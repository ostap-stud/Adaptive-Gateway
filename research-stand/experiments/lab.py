"""Start only newly created databases and owned processes, using the repo's acceptance launcher."""
import importlib.util
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time
from types import SimpleNamespace
from urllib.error import URLError
from urllib.request import Request, urlopen

from observers import BASE, DOC, PATH, PORT, Instruments, digest, now


def check(ok, message):
    if not ok:
        raise RuntimeError(message)


def wait(label, predicate, timeout=75):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        try:
            value = predicate()
            if value:
                return value
        except (URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(.10)
    raise TimeoutError(label)


class Lab:
    def __init__(self, args, work, output):
        self.args, self.work, self.output = args, Path(work), output
        self.repo = args.repo.resolve()
        self.tap = Instruments()
        self.pg = self.c = self.pg_log = None
        self.delay = None
        self.pg_bin = args.pg_bin.resolve()
        self.pg_secret = secrets.token_urlsafe(24)
        self.modules = dict(eureka="discoveryserver", gateway="gatewayserver", auth="auth-service", manager="adaptive-manager")
        self.modules.update({k: "research-stand/" + k + "-service" for k in ("catalog", "order", "inventory")})

    def start(self):
        for port in [*PORT.values(), self.args.pg_port]:
            with socket.socket() as sock:
                sock.bind(("127.0.0.1", port))
        data = self.work / "pgdata"
        password_file = self.work / "pg-password"
        password_file.write_text(self.pg_secret, encoding="ascii")
        try:
            subprocess.run([self.pg_bin / "initdb", "-D", data, "-U", "research", "-A", "scram-sha-256",
                            "--pwfile", password_file, "--locale=C", "-E", "UTF8"],
                           check=True, capture_output=True)
        finally:
            password_file.unlink(missing_ok=True)
        self.pg_log = (self.work / "postgres.log").open("wb")
        self.pg = subprocess.Popen([self.pg_bin / "postgres", "-D", data, "-h", "127.0.0.1", "-p", str(self.args.pg_port)],
                                   stdout=self.pg_log, stderr=subprocess.STDOUT)
        wait("Private PostgreSQL failed to start", lambda: subprocess.run(
            [self.pg_bin / "pg_isready", "-h", "127.0.0.1", "-p", str(self.args.pg_port), "-U", "research", "-d", "postgres"],
            capture_output=True).returncode == 0)
        spec = importlib.util.spec_from_file_location("cycle", self.repo / "tools/verify-adaptive-cycle.py")
        helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(helper)
        helper.PORTS.update(PORT)
        cfg = SimpleNamespace(java=self.args.java, psql=str(self.pg_bin / "psql"), pg_port=self.args.pg_port,
                              pg_user="research", output=str(self.work))
        c = self.c = helper.Cycle(cfg)
        helper.REPO = c.output
        c.env.update(PGPASSWORD=self.pg_secret, DB_PASSWORD=self.pg_secret, STAND_DB_PASSWORD=self.pg_secret,
                     STAND_DB_USER="research", STAND_DB="commerce_experiment", STAND_PG_HOST="127.0.0.1",
                     STAND_PG_PORT=str(cfg.pg_port), STAND_INVENTORY_URL=BASE["inventory"], STAND_CATALOG_URL=BASE["catalog"],
                     ADAPTIVE_GATEWAY_BASE_URL=BASE["gateway"], ADAPTIVE_MANAGER_BASE_URL=BASE["manager"],
                     JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -Xmx300m")
        c.configure_db()
        c.sql("CREATE DATABASE commerce_experiment", database="postgres")
        self.tap.start()
        self.launch("eureka", ["--server.address=127.0.0.1", f"--server.port={PORT['eureka']}",
                               "--eureka.server.response-cache-update-interval-ms=1000"])
        self.launch("auth", c.common("auth"))
        wait("Auth bootstrap", lambda: c.request(BASE["auth"]+"/auth-service/v3/api-docs")[0] == 200)
        self.start_gateway()
        for name in ("order", "inventory"):
            self.launch(name, c.common(name))
        # Capture real contracts once. Only the final expanded backend registers with Eureka.
        for profile in ("default", "authenticated", "permission", "expanded"):
            opts = c.common("catalog") + ["--spring.profiles.active="+profile]
            if profile != "expanded":
                opts.append("--eureka.client.enabled=false")
            self.launch("catalog", opts)
            def document():
                with urlopen(BASE["catalog"] + DOC, timeout=3) as res:
                    return res.read()
            raw = wait("Catalog OpenAPI " + profile, document)
            rule = json.loads(raw)["paths"].get(PATH, {}).get("get", {}).get("x-access")
            expected = {"default":None, "expanded":{"type":"PUBLIC"}, "authenticated":{"type":"AUTHENTICATED"},
                        "permission":{"type":"PERMISSION","permissions":["catalog.selection"]}}
            check(rule == expected[profile], "Unexpected real contract: " + profile)
            self.tap.documents[profile] = raw
            if profile != "expanded":
                c.stop("catalog")
        check(self.http(BASE["catalog"] + PATH)[0] == 200, "Expanded backend is required")
        self.start_manager(self.args.delay)
        wait("APPLY and Eureka warm-up", lambda: all(self.config(n, optional=True).get("latestEvent",{}).get("status") == "APPLIED"
                                                     for n in ("catalog","inventory","order")), 120)
        auth = BASE["auth"] + "/auth-service"
        self.admin = self.call(auth+"/login", "POST", {"login":"cycle-admin","password":c.password})
        self.call(auth+"/signup","POST",{"login":"observer","password":c.password,"contactId":60014,"roles":[]},
                  self.admin,201)
        self.user = self.call(auth+"/login","POST",{"login":"observer","password":c.password})
        wait("Ready Gateway", lambda: self.http(BASE["gateway"]+"/catalog-service/products")[0] == 200)
        check(self.probe()[0] == [404,404], "Initial Gateway state differs")
        print("Isolated infrastructure ready", flush=True)

    def launch(self, name, opts):
        self.c.start(name, str(self.repo / self.modules[name]), opts)

    def start_gateway(self):
        self.launch("gateway", self.c.common("gateway") + ["--server.ssl.enabled=false", "--spring.cloud.loadbalancer.cache.ttl=1s"])

    def start_manager(self, delay):
        if self.delay is not None:
            self.c.stop("manager")
        self.delay = delay
        self.launch("manager", [f"--server.port={PORT['manager']}", "--manager.mode=APPLY", "--manager.admission-mode=DISCOVERY",
                    f"--manager.poll-delay-ms={int(delay*1000)}", "--manager.timeout-ms=10000",
                    "--manager.eureka-url="+BASE["registry"]+"/eureka/", "--manager.auth-base-url="+BASE["sync"]])
        wait("Manager registry ready", lambda: self.snapshot().get("registryStatus") == "AVAILABLE")

    def snapshot(self):
        return self.c.json_get(BASE["manager"]+"/observations")

    def observation(self):
        return next((o for o in self.snapshot()["observations"] if o["name"]=="catalog-service"), {})

    def http(self, url, method="GET", body=None, token=None, headers=None):
        hs = dict(headers or {})
        if token:
            hs["Authorization"] = "Bearer "+token
        try:
            return self.c.request(url, method, body, hs)
        except (URLError, TimeoutError, ConnectionError) as exc:
            return 0, type(exc).__name__

    def call(self, url, method="GET", body=None, token=None, expected=200, headers=None):
        code, raw = self.http(url, method, body, token, headers)
        check(code == expected, f"{method} {url}: HTTP {code}, expected {expected}")
        try:
            return json.loads(raw)
        except ValueError:
            return raw

    def config_url(self, name="catalog"):
        return BASE["auth"] + "/auth-service/internal/adaptive/services/" + name + "-service/configuration"

    def config(self, name="catalog", optional=False):
        code, raw = self.http(self.config_url(name), headers=self.c.headers)
        if optional and code != 200:
            return {}
        check(code == 200, f"Configuration returned {code}")
        return json.loads(raw)

    def mode(self, mode):
        url = BASE["auth"] + "/auth-service/admin/adaptive/mode"
        wait("Manager mode "+mode, lambda: self.http(url, "PUT", {"mode":mode}, self.admin)[0] == 200)

    def publish(self, profile):
        published = self.tap.publish(profile)
        req = Request(BASE["docs"] + DOC, headers={"X-Experiment-Confirmation":"1"})
        with urlopen(req, timeout=3) as response:
            raw = response.read()
        confirmed = now()
        check(digest(raw) == digest(self.tap.documents[profile]), "Wrong contract served")
        return published, confirmed

    def probe(self):
        codes = []
        for token in (None, self.user):
            code, body = self.http(BASE["gateway"]+PATH, token=token)
            if code == 200:
                check(isinstance(json.loads(body).get("products"), list), "Unexpected selection response")
            codes.append(code)
        return codes, now()

    def close(self):
        try:
            if self.c:
                for name in list(self.c.processes):
                    self.c.stop(name)
                for stream in self.c.logs:
                    stream.close()
        finally:
            self.tap.close()
            if self.pg:
                subprocess.run([self.pg_bin / "pg_ctl","-D",self.work/"pgdata","-m","fast","-w","stop"],capture_output=True)
                if self.pg.poll() is None:
                    self.pg.terminate()
                self.pg.wait(timeout=15)
            if self.pg_log:
                self.pg_log.close()
