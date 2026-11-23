"""Isolated real-process acceptance test. Uses only Python standard library, JDK and psql.
Creates and drops its own random database, ephemeral TLS material and five owned JVMs.
Build module jars before running; never point this at a production PostgreSQL instance.
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import ssl
import subprocess
import time
import urllib.error
import urllib.request
import uuid

REPO = Path(__file__).resolve().parents[1]
NO_WINDOW = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
PORTS = {"eureka": 18761, "auth": 18334, "gateway": 18443, "demo": 18996, "manager": 18335}


def require(value, message):
    if not value:
        raise RuntimeError(message)


def java_tools(java):
    settings = subprocess.run([java, "-XshowSettings:properties", "-version"],
                              capture_output=True, text=True, creationflags=NO_WINDOW)
    match = re.search(r"^\s*java.home\s*=\s*(.+)$", settings.stderr, re.M)
    require(match, "Cannot locate the real JDK home")
    suffix = ".exe" if os.name == "nt" else ""
    root = Path(match.group(1).strip()) / "bin"
    return str(root / ("java" + suffix)), str(root / ("keytool" + suffix))


class Cycle:
    def __init__(self, args):
        self.args = args
        self.java, self.keytool = java_tools(args.java)
        self.db = "adaptive_cycle_" + uuid.uuid4().hex[:12]
        self.output = Path(args.output).resolve() / self.db
        self.output.mkdir(parents=True, exist_ok=False)
        self.processes = {}
        self.logs = []
        self.created = False
        self.secret = secrets.token_bytes(32)
        self.password = secrets.token_urlsafe(24)
        self.key = secrets.token_urlsafe(40)
        self.gateway_key = secrets.token_urlsafe(40)
        self.store_password = secrets.token_urlsafe(24)
        self.env = os.environ.copy()
        self.env.update(DB_SCHEMA_URL=f"jdbc:postgresql://127.0.0.1:{args.pg_port}/{self.db}?currentSchema=users",
                        DB_USERNAME=args.pg_user, DB_PASSWORD="test",
                        JWT_SECRET=base64.b64encode(self.secret).decode(), JWT_EXPIRE="7200000",
                        ADAPTIVE_SYNC_KEY=self.key, MANAGER_AUTH_KEY=self.key,
                        GATEWAY_REFRESH_KEY=self.gateway_key, ADAPTIVE_GATEWAY_KEY=self.gateway_key,
                        ADAPTIVE_BOOTSTRAP_ENABLED="true", ADAPTIVE_BOOTSTRAP_LOGIN="cycle-admin",
                        ADAPTIVE_BOOTSTRAP_PASSWORD=self.password,
                        SMOKE_STORE_PASSWORD=self.store_password, KEY_STORE_PASSWORD_CHANGEIT=self.store_password)
        self.report = {"database": self.db, "checks": [], "tlsVerification": True, "status": "RUNNING"}
        self.headers = {"X-Adaptive-Key": self.key}
        self.auth_url = f"http://127.0.0.1:{PORTS['auth']}"
        self.gateway_url = f"https://localhost:{PORTS['gateway']}"
        self.manager_url = f"http://127.0.0.1:{PORTS['manager']}"
        self.config_url = self.auth_url + "/auth-service/internal/adaptive/services/adaptive-demo/configuration"

    def command(self, args, label, stdin=None, env=None):
        result = subprocess.run(args, input=stdin, capture_output=True, text=True,
                                encoding="utf-8", errors="replace", env=env or self.env,
                                creationflags=NO_WINDOW)
        # SQL output may contain fixture details; retain locally, do not include in public summary.
        (self.output / (label + ".log")).write_text(result.stdout + result.stderr, encoding="utf-8")
        require(result.returncode == 0, f"{label} failed; inspect its local log")
        return result.stdout.strip()

    def sql(self, statement=None, file=None, database=None):
        args = [self.args.psql, "-h", "127.0.0.1", "-p", str(self.args.pg_port), "-U", self.args.pg_user,
                "-d", database or self.db, "-v", "ON_ERROR_STOP=1", "-t", "-A"]
        if file:
            args += ["-f", str(file)]
        return self.command(args, "database-last-command", statement)

    def request(self, url, method="GET", payload=None, headers=None):
        body = None if payload is None else json.dumps(payload).encode()
        combined = dict(headers or {})
        if body is not None:
            combined["Content-Type"] = "application/json"
        req = urllib.request.Request(url, body, combined, method=method)
        try:
            with urllib.request.urlopen(req, timeout=12, context=getattr(self, "tls", None)) as response:
                data = response.read().decode()
                return response.status, data
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode()

    def json_get(self, url, headers=None):
        status, text = self.request(url, headers=headers)
        require(status == 200, f"Expected HTTP 200 from {url.split('?')[0]}, got {status}")
        return json.loads(text)

    def wait(self, label, condition, seconds=120):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            for name, process in self.processes.items():
                require(process.poll() is None, f"{name} exited unexpectedly; inspect its log")
            try:
                if condition():
                    print("PASS: " + label, flush=True)
                    self.report["checks"].append(label)
                    return
            except (urllib.error.URLError, TimeoutError, ConnectionError, json.JSONDecodeError):
                pass
            time.sleep(1)
        raise RuntimeError("Timed out: " + label)

    def start(self, name, module, options, java_options=()):
        require(name not in self.processes, "Process already owned: " + name)
        jars = list((REPO / module / "target").glob("*.jar"))
        require(len(jars) == 1, f"Build one executable jar for {module} first")
        log = open(self.output / (name + "-" + str(time.time_ns()) + ".log"), "w", encoding="utf-8")
        self.logs.append(log)
        process = subprocess.Popen([self.java, *java_options, "-jar", str(jars[0]), *options],
                                   cwd=REPO, env=self.env, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=NO_WINDOW)
        self.processes[name] = process

    def stop(self, name):
        process = self.processes.pop(name, None)
        if process and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)

    def common(self, name):
        return ["--server.address=127.0.0.1", f"--server.port={PORTS[name]}",
                "--eureka.instance.hostname=127.0.0.1", "--eureka.instance.prefer-ip-address=false",
                f"--eureka.client.service-url.defaultZone=http://127.0.0.1:{PORTS['eureka']}/eureka/",
                "--eureka.client.registry-fetch-interval-seconds=2",
                "--eureka.client.initial-instance-info-replication-interval-seconds=1"]

    def start_gateway(self):
        self.start("gateway", "gatewayserver", self.common("gateway") + [
            f"--server.ssl.key-store={(self.output / 'gateway.p12').as_uri()}",
            "--server.ssl.key-alias=adaptive-demo", "--spring.cloud.loadbalancer.cache.ttl=1s"])

    def start_demo(self, public_access):
        self.start("demo", "test-service", self.common("demo") + [
            "--spring.application.name=adaptive-demo", "--spring.profiles.active=adaptive-demo",
            "--eureka.instance.instance-id=adaptive-demo-cycle", f"--demo.public-access={public_access}"])

    def start_manager(self):
        self.start("manager", "adaptive-manager", [
            f"--server.port={PORTS['manager']}", "--manager.mode=APPLY",
            "--manager.admission-mode=DISCOVERY", "--manager.poll-delay-ms=1000",
            f"--manager.eureka-url=http://127.0.0.1:{PORTS['eureka']}/eureka/",
            f"--manager.auth-base-url={self.auth_url}", "--manager.timeout-ms=10000"])

    def create_tls(self):
        self.command([self.keytool, "-genkeypair", "-alias", "adaptive-demo", "-keyalg", "RSA",
                      "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost",
                      "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
                      "-keystore", str(self.output / "gateway.p12"), "-storepass:env", "SMOKE_STORE_PASSWORD",
                      "-keypass:env", "SMOKE_STORE_PASSWORD", "-noprompt"], "tls-generate")
        self.command([self.keytool, "-exportcert", "-rfc", "-alias", "adaptive-demo",
                      "-keystore", str(self.output / "gateway.p12"), "-storepass:env", "SMOKE_STORE_PASSWORD",
                      "-file", str(self.output / "gateway.pem")], "tls-export")
        self.command([self.keytool, "-importcert", "-alias", "adaptive-demo", "-noprompt",
                      "-file", str(self.output / "gateway.pem"), "-keystore", str(self.output / "trust.p12"),
                      "-storetype", "PKCS12", "-storepass:env", "SMOKE_STORE_PASSWORD"], "tls-trust")
        self.tls = ssl.create_default_context(cafile=str(self.output / "gateway.pem"))

    def bootstrap_token(self):
        def encode(value):
            return base64.urlsafe_b64encode(value).decode().rstrip("=")
        header = encode(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
        body = encode(json.dumps({"sub": "cycle-admin", "exp": int(time.time()) + 7200,
                                  "roles": ["ROLE_GATEWAY_ADMIN"]}).encode())
        signing = header + "." + body
        return signing + "." + encode(hmac.new(self.secret, signing.encode(), hashlib.sha256).digest())

    def configure_db(self):
        self.sql(f'CREATE DATABASE "{self.db}";', database="postgres")
        self.created = True
        # Auth initializes the empty database and bootstrap admin exactly as an IDEA launch does.

    def state(self):
        return self.json_get(self.config_url, self.headers)

    def applied(self, after_version=-1):
        state = self.state()
        event = state.get("latestEvent") or {}
        return (state["service"]["configuration_version"] > after_version and event.get("status") == "APPLIED"
                and event.get("configuration_version") == state["service"]["configuration_version"])

    def event_count(self):
        return int(self.sql("SELECT count(*) FROM users.adaptation_event;"))

    def check_access(self, path, expected, token=None):
        status, _ = self.request(self.gateway_url + "/adaptive-demo" + path,
                                 headers={} if token is None else {"Authorization": "Bearer " + token})
        require(status == expected, f"{path}: expected {expected}, got {status}")
        self.report["checks"].append(f"HTTPS {path}: {expected}" + (" with JWT" if token else " without JWT"))

    def run(self):
        for port in PORTS.values():
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", port))
        self.configure_db()
        self.create_tls()
        self.env.update(ADAPTIVE_GATEWAY_BASE_URL=self.gateway_url,
                        ADAPTIVE_GATEWAY_TIMEOUT_MS="2500")
        self.start("eureka", "discoveryserver", [
            "--server.address=127.0.0.1", f"--server.port={PORTS['eureka']}",
            "--eureka.server.response-cache-update-interval-ms=1000"])
        self.start("auth", "auth-service", self.common("auth") + [
            "--logging.level.org.springframework.jdbc.core=INFO"], java_options=[
                f"-Djavax.net.ssl.trustStore={self.output / 'trust.p12'}",
                f"-Djavax.net.ssl.trustStorePassword={self.store_password}",
                "-Djavax.net.ssl.trustStoreType=PKCS12"])
        self.wait("Auth startup", lambda: self.request(self.auth_url + "/auth-service/v3/api-docs")[0] == 200)
        status, admin = self.request(self.auth_url + "/auth-service/login", "POST",
                                     {"login": "cycle-admin", "password": self.password})
        require(status == 200, "Admin login failed")
        admin_headers = {"Authorization": "Bearer " + admin}
        self.legacy_rows = self.sql("SELECT json_agg(r ORDER BY id)::text FROM users.route r WHERE source='MANUAL';")
        status, _ = self.request(self.auth_url + "/auth-service/signup", "POST",
                                {"login": "cycle-user", "password": self.password, "contactId": 900001, "roles": []}, admin_headers)
        require(status == 201, "User creation via admin API failed")
        status, user = self.request(self.auth_url + "/auth-service/login", "POST",
                                    {"login": "cycle-user", "password": self.password})
        require(status == 200, "User login failed")
        require(self.request(self.config_url, headers=self.headers)[0] == 404, "Demo must not be preregistered")
        self.start_gateway()
        self.start_demo("PUBLIC")
        self.start_manager()
        self.wait("Automatic registration from empty database reaches APPLIED",
                  lambda: self.request(self.config_url, headers=self.headers)[0] == 200 and self.applied())
        self.wait("Eureka load-balancer can reach demo backend",
                  lambda: self.request(self.gateway_url + "/adaptive-demo/demo/public")[0] == 200)
        first = self.state()
        rid = next(r["id"] for r in first["routes"] if r["managed_key"] == "ingress")
        operation_ids = {o["gateway_path"] + ":" + o["http_method"]: o["id"] for o in first["operations"]}
        require(len(first["routes"]) == 1 and len(operation_ids) == 6, "Unexpected generated configuration")
        self.check_access("/demo/public", 200)
        self.check_access("/demo/private", 401)
        self.check_access("/demo/private", 200, user)
        self.check_access("/demo/blocked", 403, user)
        self.check_access("/unknown", 404)
        self.check_access("/demo/restricted", 403, user)
        policy_base = self.auth_url + "/auth-service/policy"
        status, _ = self.request(self.auth_url + "/auth-service/role/add", "POST",
            [{"roleName": "READER", "application": "DEMO", "roleDescription": "Permission test"}], admin_headers)
        require(status == 201, "Role creation failed")
        status, _ = self.request(policy_base + "/permissions/demo.read", "PUT",
            {"roles": ["ROLE_DEMO_READER"], "reason": "Acceptance mapping"}, admin_headers)
        require(status == 200, "Permission mapping failed")
        self.check_access("/demo/restricted", 401)
        self.check_access("/demo/restricted", 403, user)
        status, _ = self.request(self.auth_url + "/auth-service/add-roles/cycle-user", "POST", ["ROLE_DEMO_READER"], admin_headers)
        require(status == 200, "User role assignment failed")
        self.check_access("/demo/restricted", 200, user)
        oid = operation_ids["/adaptive-demo/demo/restricted:GET"]
        policy_url = policy_base + f"/operations/{oid}/ADMIN"
        require(self.request(policy_url, "PUT", {"accessType": "PUBLIC", "reason": "Normal override"}, admin_headers)[0] == 200,
                "Admin policy change failed")
        self.check_access("/demo/restricted", 401)
        require(self.request(policy_url, "PUT", {"accessType": "PUBLIC", "forceOverride": True, "reason": "Explicit demo exception"}, admin_headers)[0] == 200,
                "Force override failed")
        self.wait("Admin force override survives the next synchronization", self.applied)
        self.check_access("/demo/restricted", 200)
        require(self.request(policy_url, "DELETE", {"reason": "Restore declaration"}, admin_headers)[0] == 200,
                "Policy removal failed")
        self.check_access("/demo/restricted", 401)
        audit = self.json_get(policy_base + "/audit", admin_headers)
        require(any(row["action"] == "POLICY_PUT" and row["actor"] == "cycle-admin" for row in audit), "Missing policy audit")
        require(self.request(policy_base + "/permissions/demo.read", "PUT", {"roles": [], "reason": "Unauthorized attempt"},
                             {"Authorization": "Bearer " + user})[0] in (401,403), "Normal user changed central policy")
        self.wait("Policy updates are published", self.applied)
        docs_config = self.json_get(self.gateway_url + "/swagger/config")
        require(any(item["name"] == "adaptive-demo" for item in docs_config["urls"]), "Service absent from central Swagger")
        docs = self.json_get(self.gateway_url + "/swagger/services/adaptive-demo")
        require("/adaptive-demo/demo/restricted" in docs["paths"] and docs["servers"] == [{"url": "/"}], "Invalid centralized documentation")
        require(self.request(self.gateway_url + "/swagger-ui.html")[0] == 200, "Swagger UI unavailable")
        require(self.request(self.gateway_url + "/actuator/gateway/routes", headers={"X-Gateway-Key": self.key})[0] == 401,
                "Manager key must not authorize Gateway management")
        self.report["checks"].append("Swagger UI and proxied service OpenAPI available; business policies remain enforced")
        initial_events = self.event_count()
        time.sleep(4)
        require(self.event_count() == initial_events, "Unchanged polls produced duplicate events")
        self.stop("manager")
        self.start_manager()
        self.wait("Manager restart sees UNCHANGED durable state",
                  lambda: any((o.get("sync") or {}).get("status") == "UNCHANGED"
                              for o in self.json_get(self.manager_url + "/observations")["observations"] if o["name"] == "adaptive-demo"))
        require(self.event_count() == initial_events, "Restart duplicated synchronization")
        before = first["service"]["configuration_version"]
        self.request(self.auth_url + f"/auth-service/service/route/{rid}/state", "PUT", {"active": False}, admin_headers)
        self.wait("Manual inactive override is published and preserved", lambda: self.applied(before))
        require(not self.state()["routes"][0]["active"], "Manager revived the manual override")
        self.check_access("/demo/public", 404)
        before = self.state()["service"]["configuration_version"]
        self.request(self.auth_url + f"/auth-service/service/route/{rid}/state", "PUT", {"active": True}, admin_headers)
        self.wait("Explicit admin reactivation is published", lambda: self.applied(before))
        self.check_access("/demo/public", 200)
        before = self.state()["service"]["configuration_version"]
        # New OpenAPI revision while Gateway is offline tests persistence and durable retry.
        self.stop("gateway")
        self.stop("demo")
        self.start_demo("AUTHENTICATED")
        self.wait("Changed OpenAPI persists as FAILED_REFRESH while Gateway is offline",
                  lambda: self.state()["service"]["configuration_version"] > before and
                          (self.state().get("latestEvent") or {}).get("status") == "FAILED_REFRESH")
        failed_event = self.state()["latestEvent"]["request_id"]
        failed_count = self.event_count()
        self.stop("manager")
        self.start_gateway()
        self.start_manager()
        self.wait("Restarted Manager retries the existing event and reaches APPLIED", self.applied)
        require(self.state()["latestEvent"]["request_id"] == failed_event, "Retry replaced the persisted event")
        require(self.event_count() == failed_count, "Retry duplicated configuration")
        self.check_access("/demo/public", 401)
        self.check_access("/demo/public", 200, user)
        self.check_access("/demo/blocked", 403, user)
        final = self.state()
        require(final["routes"][0]["id"] == rid, "Route ID changed")
        require(operation_ids == {o["gateway_path"] + ":" + o["http_method"]: o["id"] for o in final["operations"]},
                "Operation IDs changed")
        require(self.legacy_rows == self.sql("SELECT json_agg(r ORDER BY id)::text FROM users.route r WHERE source='MANUAL';"),
                "Legacy routes changed")
        self.report.update(status="PASSED", routeId=rid, operationCount=len(operation_ids),
                           finalVersion=final["service"]["configuration_version"], eventCount=self.event_count(),
                           recoveryEvent=failed_event, legacyRoutesPreserved=True)
        (self.output / "final-configuration.json").write_text(json.dumps(final, indent=2), encoding="utf-8")
        print("PASS: stable IDs, no duplicate events, manual override and legacy routes preserved", flush=True)

    def close(self):
        for name in list(self.processes):
            self.stop(name)
        for log in self.logs:
            log.close()
        if self.created:
            # Only the fresh database created by this exact run can be removed.
            require(re.fullmatch(r"adaptive_cycle_[0-9a-f]{12}", self.db), "Refusing unexpected cleanup target")
            self.sql(f'DROP DATABASE "{self.db}" WITH (FORCE);', database="postgres")
            self.report["databaseCleanedUp"] = True
        # Remove generated private key/truststore; retain only public certificate and diagnostic logs.
        for filename in ("gateway.p12", "trust.p12"):
            path = self.output / filename
            if path.exists():
                path.unlink()
        (self.output / "report.json").write_text(json.dumps(self.report, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", default=shutil.which("java"))
    parser.add_argument("--psql", default=shutil.which("psql"))
    parser.add_argument("--pg-port", type=int, default=55439)
    parser.add_argument("--pg-user", default="postgres")
    parser.add_argument("--output", default=str(REPO / "adaptive-manager/target/cycle"))
    args = parser.parse_args()
    require(args.java and args.psql, "Supply --java and --psql")
    cycle = Cycle(args)
    try:
        cycle.run()
    except Exception as error:
        cycle.report.update(status="FAILED", error=str(error))
        raise
    finally:
        cycle.close()
        print("Report: " + str(cycle.output / "report.json"), flush=True)


if __name__ == "__main__":
    main()
