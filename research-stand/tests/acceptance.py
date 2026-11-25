"""Run against isolated JVMs and two new databases; do not use python -O."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
from uuid import uuid4

from business import verify

STAND = Path(__file__).resolve().parents[1]
APPS = ("catalog", "inventory", "order")
PORTS = dict(eureka=28761, auth=28334, gateway=28088, manager=28335,
             catalog=28911, inventory=28912, order=28913)


def run(args):
    if not __debug__:
        raise RuntimeError("Assertions must be enabled")
    args.repo = args.repo.resolve()
    spec = importlib.util.spec_from_file_location("cycle", args.repo / "tools/verify-adaptive-cycle.py")
    cycle = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(cycle)
    cycle.PORTS.update(PORTS)
    for port in PORTS.values():
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
    c = cycle.Cycle(args)
    cycle.REPO = c.output  # Isolate relative log files from the working project.
    base = {name: f"http://127.0.0.1:{port}" for name, port in PORTS.items()}
    commerce = "commerce_qa_" + uuid4().hex
    created_commerce = False
    c.gateway_url = base["gateway"]
    c.report.update(tlsVerification=False, transport="loopback HTTP")
    c.env.update(ADAPTIVE_GATEWAY_BASE_URL=base["gateway"], ADAPTIVE_MANAGER_BASE_URL=base["manager"],
                 DB_PASSWORD=os.environ.get("PGPASSWORD", ""), STAND_DB=commerce, STAND_PG_HOST="127.0.0.1",
                 STAND_PG_PORT=str(args.pg_port), STAND_DB_USER=args.pg_user,
                 STAND_DB_PASSWORD=os.environ.get("PGPASSWORD", ""), STAND_INVENTORY_URL=base["inventory"],
                 STAND_CATALOG_URL=base["catalog"], JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -Xmx300m")
    def passed(label):
        print("PASS:", label, flush=True)
        c.report["checks"].append(label)

    def call(url, method="GET", body=None, expected=200, token=None):
        headers = {"Authorization": "Bearer " + token} if token else {}
        code, raw = c.request(url, method, body, headers)
        allowed = expected if isinstance(expected, tuple) else (expected,)
        assert code in allowed, f"{method} {url}: expected {allowed}, received {code}: {raw[:400]}"
        if isinstance(expected, tuple):
            return code
        try:
            return json.loads(raw)
        except ValueError:
            return raw

    def launch(name, profile="default"):
        module = name + "-service"
        c.start(name, str(STAND / module), c.common(name) + [
            "--spring.profiles.active=" + profile, "--eureka.instance.instance-id=" + module + "-acceptance"])
        path = "/products" if name == "catalog" else "/orders" if name == "order" else "/stock/1"
        c.wait(f"{module} ready ({profile})", lambda: c.request(base[name] + "/" + module + path)[0] == 200)

    def revision(profile):
        c.stop("catalog")
        launch("catalog", profile)

    gw = base["gateway"]
    auth = gw + "/auth-service"
    selection = "/catalog-service/selection"
    def snapshot():
        return c.json_get(base["manager"] + "/observations")["observations"]

    def state(name):
        return c.json_get(c.auth_url + "/auth-service/internal/adaptive/services/" + name + "-service/configuration", c.headers)

    def operation():
        return next((o for o in state("catalog")["operations"] if o["gateway_path"] == selection), {})

    def behavior(code, token=None):
        return c.request(gw + selection, headers={"Authorization": "Bearer " + token} if token else {})[0] == code

    try:
        c.configure_db()
        c.sql(f'CREATE DATABASE "{commerce}"', database="postgres")
        created_commerce = True
        c.start("eureka", str(args.repo / "discoveryserver"), ["--server.address=127.0.0.1", "--server.port=28761",
            "--eureka.server.response-cache-update-interval-ms=1000"])
        c.start("auth", str(args.repo / "auth-service"), c.common("auth"))
        c.wait("Auth initialized", lambda: c.request(c.auth_url + "/auth-service/v3/api-docs")[0] == 200)
        c.start("gateway", str(args.repo / "gatewayserver"), c.common("gateway") + [
            "--server.ssl.enabled=false", "--spring.cloud.loadbalancer.cache.ttl=1s"])
        c.start("manager", str(args.repo / "adaptive-manager"), ["--server.port=28335", "--manager.mode=OBSERVE",
            "--manager.admission-mode=DISCOVERY", "--manager.poll-delay-ms=1500",
            "--manager.timeout-ms=10000", "--manager.eureka-url=" + base["eureka"] + "/eureka/",
            "--manager.auth-base-url=" + base["auth"]])
        for name in APPS:
            launch(name)
        verify(call, base, passed)
        c.stop("inventory")
        retry = {"id": str(uuid4()), "items": [{"productId": 2, "quantity": 2}]}
        purchase_url = base["order"] + "/order-service/orders"
        call(purchase_url, "POST", retry, 503)
        assert call(purchase_url + "/" + retry["id"])["status"] == "PENDING"
        launch("inventory")
        assert call(purchase_url, "POST", retry, 201)["status"] == "RESERVED"
        passed("Inventory unavailable: durable PENDING and successful retry after restart")
        c.wait("OBSERVE has three analyzed candidates", lambda: all(any(o["name"] == n+"-service"
            and o["status"] in {"ANALYZED","ANALYZED_WITH_BLOCKED_OPERATIONS"} and o["candidate"] for o in snapshot()) for n in APPS))
        for name in APPS:
            assert c.request(c.auth_url + "/auth-service/internal/adaptive/services/" + name + "-service/configuration",
                             headers=c.headers)[0] == 404
        c.wait("Gateway routes Auth", lambda: c.request(auth + "/login", "POST",
            {"login": "cycle-admin", "password": c.password})[0] == 200)
        admin = call(auth + "/login", "POST", {"login": "cycle-admin", "password": c.password})
        c.wait("Switch to APPLY", lambda: c.request(auth + "/admin/adaptive/mode", "PUT", {"mode": "APPLY"},
            {"Authorization": "Bearer " + admin})[0] == 200)
        c.wait("APPLY exposes public catalog", lambda: c.request(gw + "/catalog-service/products")[0] == 200)
        c.wait("All three services appear in admin API", lambda: set(n+"-service" for n in APPS) <=
               {s["serviceName"] for s in call(auth + "/admin/services", token=admin)})
        c.wait("All route publications confirmed", lambda: all((state(n).get("latestEvent") or {}).get("status") == "APPLIED" for n in APPS))
        for name, count in zip(APPS, [6, 5, 5]):
            config = state(name)
            assert len(config["routes"]) == 1 and len(config["operations"]) == count
            assert config["latestEvent"]["status"] == "APPLIED"
            doc = call(gw + "/swagger/services/" + name + "-service")
            groups = [op for methods in doc["paths"].values() for op in methods.values()]
            assert len(groups) == count and all("x-access" in op for op in groups)
        call(gw + "/swagger-ui.html")
        assert {n+"-service" for n in APPS} <= {s["name"] for s in call(gw + "/swagger/config")["urls"]}
        passed("S1 automatic routes, 16 operations, centralized Swagger and admin service inventory")

        if args.panel:
            panel_env = dict(os.environ, STAND_QA_LOGIN_PASSWORD=c.password)
            subprocess.run(["node", str(STAND / "tests/panel.cjs"), str(args.repo / "admin-panel/dist"), gw],
                           env=panel_env, check=True, timeout=90)
            passed("Browser: authenticated Services page displays all three applications")

        call(auth + "/signup", "POST", {"login":"commerce-reader", "password":c.password, "contactId":900031,"roles":[]}, 201, admin)
        user = call(auth + "/login", "POST", {"login":"commerce-reader","password":c.password})
        order = {"id": str(uuid4()), "items":[{"productId":1,"quantity":1}]}
        call(gw + "/order-service/orders", "POST", order, 401)
        assert call(gw + "/order-service/orders", "POST", order, 201, user)["status"] == "RESERVED"
        call(gw + "/order-service/orders", expected=403, token=user)
        call(gw + "/inventory-service/reservations/" + str(uuid4()), "PUT", {"items":order["items"]}, 403, user)
        passed("Gateway AUTHENTICATED order, unmapped permission and INTERNAL denial")

        revision("expanded")
        c.wait("S2 added operation is public", lambda: behavior(200))
        oid = operation()["id"]
        revision("authenticated")
        c.wait("S3 anonymous request now requires JWT", lambda: behavior(401))
        assert behavior(200, user)
        revision("permission")
        c.wait("S4 unknown permission denies authenticated request", lambda: behavior(403, user))
        call(auth + "/role/add", "POST", [{"roleName":"READER","application":"COMMERCE","roleDescription":"Research"}], 201, admin)
        call(auth + "/policy/permissions/catalog.selection", "PUT", {"roles":["ROLE_COMMERCE_READER"],"reason":"Research mapping"}, token=admin)
        assert behavior(403, user)
        call(auth + "/add-roles/commerce-reader", "POST", ["ROLE_COMMERCE_READER"], token=admin)
        assert behavior(200, user)
        passed("S4 mapping separately verifies missing role and matching role")

        revision("default")
        c.wait("S5 removed operation becomes inactive", lambda: operation().get("active") is False and behavior(404))
        revision("expanded")
        c.wait("S5 returned operation reuses the ID", lambda: operation().get("id") == oid and operation()["active"] and behavior(200))
        before = state("catalog")
        c.stop("catalog")
        # Process termination on Windows skips the JVM shutdown hook; deregister our own instance.
        call(base["eureka"] + "/eureka/apps/CATALOG-SERVICE/catalog-service-acceptance", "DELETE", expected=(200,404))
        c.wait("S6 catalog absent from discovery", lambda: any(o["name"] == "catalog-service" and o["status"] in
               {"MISSING","NO_UP_INSTANCE"} for o in snapshot()))
        assert state("catalog")["operations"] == before["operations"]
        launch("catalog", "expanded")
        c.wait("S6 catalog recovered", lambda: behavior(200))
        policy = auth + f"/policy/operations/{oid}/ADMIN"
        call(policy, "PUT", {"accessType":"BLOCKED","reason":"Research override"}, token=admin)
        assert behavior(403, user)
        revision("authenticated")
        c.wait("S7 new contract applied under admin restriction", lambda:
            state("catalog")["service"]["openapi_hash"] != before["service"]["openapi_hash"] and behavior(403, user))
        call(policy, "DELETE", {"reason":"End research override"}, token=admin)
        assert behavior(200, user) and behavior(401)
        before = state("catalog")
        revision("docs-off")
        c.wait("S8 manager reports failed specification fetch", lambda: any(o["name"] == "catalog-service"
            and o["status"] == "OBSERVATION_FAILED" for o in snapshot()))
        after = state("catalog")
        assert before["operations"] == after["operations"] and before["service"]["configuration_version"] == after["service"]["configuration_version"]
        revision("authenticated")
        c.wait("S8 valid specification available again", lambda: any(o["name"] == "catalog-service"
            and o["status"] == "ANALYZED" for o in snapshot()) and behavior(200,user))
        c.report.update(status="PASSED")
    except Exception as exc:
        c.report.update(status="FAILED", error=str(exc))
        raise
    finally:
        try:
            for name in list(c.processes):
                c.stop(name)
            if created_commerce:
                c.sql(f'DROP DATABASE "{commerce}"', database="postgres")
        finally:
            c.close()
            print("Report:", c.output / "report.json", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=STAND.parent)
    parser.add_argument("--java", default=shutil.which("java"))
    parser.add_argument("--psql", default=shutil.which("psql"))
    parser.add_argument("--pg-user", default="postgres")
    parser.add_argument("--pg-port", type=int, default=5432)
    parser.add_argument("--output", default=str(STAND / "target/acceptance"))
    parser.add_argument("--panel", action="store_true", help="Check the built React panel using Node and Playwright")
    run(parser.parse_args())
