"""Additional Catalog experiments. Run against the repository's isolated Lab only."""
import argparse
import csv
import json
import os
import platform
import re
import statistics
import subprocess
import sys
import tempfile
import threading
from collections import defaultdict
from datetime import datetime, timezone
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote
from uuid import uuid4

parser = argparse.ArgumentParser()
parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
parser.add_argument("--java", default="java")
parser.add_argument("--pg-bin", type=Path, required=True)
parser.add_argument("--pg-port", type=int, default=39547)
parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "results")
args = parser.parse_args()
args.repo = args.repo.resolve()
sys.path.insert(0, str(args.repo / "research-stand/experiments"))
from lab import Lab, check, wait
from observers import BASE, PORT, Instruments, DOC, PATH, digest, now
from measure import utc, ms

AUTH = BASE["auth"] + "/auth-service"
GATE = BASE["gateway"]
CAT = "/catalog-service"
ROLE = "ROLE_EXPERIMENT_WRITER"
MAPPING = AUTH + "/policy/permissions/catalog.write"
REG_PORT, REFRESH_PORT = 38763, 38089
args.delay = 1.5


class Monitor(Instruments):
    live = False

    def serve(self, kind, method, path, headers, body):
        start = now()
        if kind == "docs" and self.live:
            conn = HTTPConnection("127.0.0.1", PORT["catalog"], timeout=10)
            try:
                conn.request("GET", DOC)
                res = conn.getresponse()
                data = res.read()
                self.event("live_docs", start_ns=start, end_ns=now(), status=res.status, sha256=digest(data))
                return res.status, data
            finally:
                conn.close()
        code, data = super().serve(kind, method, path, headers, body)
        if kind == "registry" and code == 200:
            apps = json.loads(data)["applications"].get("application", [])
            if any(x["name"].upper() == "CATALOG-SERVICE" for x in apps):
                self.event("discovered", start_ns=start, end_ns=now())
        if kind == "sync" and method == "GET" and "/catalog-service/" in path:
            self.event("inspect", start_ns=start, end_ns=now(), status=code)
        return code, data

    def relay(self, port, dest, kind):
        monitor = self
        class Relay(BaseHTTPRequestHandler):
            def dispatch(self):
                start = now()
                conn = HTTPConnection("127.0.0.1", PORT[dest], timeout=12)
                try:
                    size = int(self.headers.get("Content-Length", 0))
                    headers = {k:v for k,v in self.headers.items() if k.lower() not in ("host","connection","transfer-encoding")}
                    if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
                        chunks = []
                        while True:
                            length = int(self.rfile.readline().split(b";",1)[0].strip(),16)
                            if length == 0:
                                while self.rfile.readline().strip():
                                    pass
                                break
                            chunks.append(self.rfile.read(length))
                            self.rfile.read(2)
                        body = b"".join(chunks)
                        headers["Content-Length"] = str(len(body))
                    else:
                        body = self.rfile.read(size) if size else None
                    conn.request(self.command, self.path, body, headers)
                    res = conn.getresponse()
                    data = res.read()
                    monitor.event(kind, start_ns=start, end_ns=now(), path=self.path, method=self.command, status=res.status)
                    self.send_response(res.status)
                    for k in ("Content-Type", "Content-Encoding", "Location"):
                        if res.getheader(k):
                            self.send_header(k, res.getheader(k))
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                finally:
                    conn.close()
            do_GET = do_POST = do_PUT = do_DELETE = dispatch
            def log_message(self, *_):
                pass
        server = ThreadingHTTPServer(("127.0.0.1", port), Relay)
        self.servers.append(server)
        threading.Thread(target=server.serve_forever, daemon=True).start()

    def start(self):
        super().start()
        self.relay(REG_PORT, "eureka", "registration")
        self.relay(REFRESH_PORT, "gateway", "publication")


class Study(Lab):
    def launch(self, name, opts):
        if name == "auth":
            opts = [*opts, f"--adaptive.gateway.base-url=http://127.0.0.1:{REFRESH_PORT}"]
        super().launch(name, opts)

    def registry(self):
        return self.call(BASE["eureka"] + "/eureka/apps", headers={"Accept":"application/json"})["applications"]["application"]

    def catalog_ids(self):
        return [i["instanceId"] for a in self.registry() if a["name"]=="CATALOG-SERVICE" for i in a["instance"]]

    def prepare(self):
        self.call(AUTH+"/role/add","POST",[{"roleName":"WRITER","application":"EXPERIMENT","roleDescription":"Catalog experiment"}],self.admin,201)
        self.call(AUTH+"/signup","POST",{"login":"writer","password":self.c.password,"contactId":69112,"roles":[ROLE]},self.admin,201)
        self.writer = self.call(AUTH+"/login","POST",{"login":"writer","password":self.c.password})
        self.tap.live = True

    def reset(self, arm):
        self.mode("OBSERVE")
        ids = self.catalog_ids()
        self.c.stop("catalog")
        for instance in ids:
            code, _ = self.http(BASE["eureka"]+"/eureka/apps/CATALOG-SERVICE/"+quote(instance,safe=""), "DELETE")
            check(code in (200,404), f"deregister HTTP {code}")
        wait("Catalog left Eureka",lambda: not self.catalog_ids())
        wait("Manager observed absence", lambda: self.snapshot().get("registryStatus")=="AVAILABLE" and self.observation().get("status") in (None,"MISSING"))
        check(re.fullmatch(r"adaptive_cycle_[a-f0-9]{12}",self.c.db) and self.pg.poll() is None, "Private DB required")
        self.c.sql("BEGIN; DELETE FROM users.service WHERE service_name='catalog-service'; DELETE FROM users.permission_role WHERE permission='catalog.write'; COMMIT;")
        self.call(GATE+"/actuator/gateway/refresh","POST",token=self.admin)
        wait("No Catalog route",lambda: self.http(GATE+CAT+"/products",token=self.writer)[0]==404)
        check(self.http(self.config_url(),headers=self.c.headers)[0]==404, "Configuration still present")
        if arm != "MANUAL":
            self.mode("APPLY")

    def map_writer(self):
        self.call(MAPPING,"PUT",{"roles":[ROLE],"reason":"Catalog onboarding experiment"},self.admin)

    def new_catalog(self):
        trace_start = now()
        instance = "catalog-study-"+uuid4().hex[:12]
        opts = [o for o in self.c.common("catalog") if not o.startswith("--eureka.client.service-url.")]
        self.launch("catalog",opts+[f"--eureka.client.service-url.defaultZone=http://127.0.0.1:{REG_PORT}/eureka/",
                     "--eureka.instance.instance-id="+instance, "--spring.profiles.active=expanded"])
        def registration():
            return next((e for e in self.tap.since(trace_start,"registration") if e["method"]=="POST" and
                         e["path"].rstrip("/").upper().endswith("/APPS/CATALOG-SERVICE") and e["status"]==204),None)
        return instance, wait("Eureka registration HTTP 204", registration, 60)

    def admin_actions(self, arm, row):
        def action(name, fn):
            start = now()
            item = dict(action=name,start_ns=start)
            row["actions"].append(item)
            fn()
            item["end_ns"] = now()
        if arm == "AUTOMATIC":
            action("PUT catalog.write roles", self.map_writer)
        if arm != "MANUAL":
            return
        action("POST service",lambda:self.call(AUTH+"/service/add","POST",[{"name":"catalog-service","description":"Catalog"}],self.admin,201))
        route = dict(route=CAT+"/**",routeDescription="Catalog",serviceName="catalog-service",isInternal=False,
                     order=0,roles=[ROLE],filters=[dict(name="Authentication",args={})],predicates=[])
        action("POST route with writer role",lambda:self.call(AUTH+"/service/route/add","POST",[route],self.admin,201))
        action("POST Gateway refresh",lambda:self.call(GATE+"/actuator/gateway/refresh","POST",token=self.admin))

    def onboarding(self,row,arm):
        self.reset(arm)
        if arm=="DISCOVERY":
            self.map_writer()
        row["instance"], registration = self.new_catalog()
        t0 = registration["end_ns"]
        row.update(registration=registration, actions=[])
        self.admin_actions(arm,row)
        row["admin_actions"] = len(row["actions"])
        url = GATE+CAT
        observations = row["route_probes"] = []
        def ready():
            code, raw = self.http(url+"/products",token=self.writer)
            observations.append([now(),code])
            return code==200 and isinstance(json.loads(raw).get("products"),list)
        wait("Writer GET /products through Gateway",ready,45)
        row["route_ready_ms"] = ms(observations[-1][0],t0)
        checks = row["functional"] = {}
        def request(label, path, token, method="GET", payload=None, allowed=(200,)):
            code, raw = self.http(url+path,method,payload,token)
            checks[label]=code
            row.setdefault("functional_end_ns",{})[label]=now()
            check(code in allowed,f"{label}: HTTP {code}, expected {allowed}")
            return json.loads(raw) if raw and code in (200,201) else None
        categories = request("categories","/categories",self.writer)
        check(isinstance(request("selection","/selection",self.writer)["products"],list),"selection content")
        payload=dict(name=row["instance"],categoryId=categories[0]["id"],price=15)
        for label, token in (("guest_write",None),("unprivileged_write",self.user)):
            request(label,"/products",token,"POST",payload,(401,403))
        p = request("create","/products",self.writer,"POST",payload,(201,))
        path = "/products/"+str(p["id"])
        check(request("detail",path,self.writer)["name"]==payload["name"],"Created product")
        payload["price"]=16
        check(request("update",path,self.writer,"PUT",payload)["price"]==16,"Updated price")
        request("delete",path,self.writer,"DELETE",allowed=(204,))
        request("deactivated",path,self.writer,allowed=(404,))
        end = now()
        row.update(ready_ns=end, latency_ms=ms(end,t0), capabilities={
            "guest_read":self.http(url+"/products")[0],"unprivileged_read":self.http(url+"/products",token=self.user)[0]})
        cfg = self.config()
        row["stored"] = dict(mode=cfg["service"]["management_mode"],routes=[r["source"] for r in cfg["routes"]],
                             operations=len([o for o in cfg["operations"] if o["active"]]))
        expected = ("MANUAL","MANUAL",0) if arm=="MANUAL" else ("AUTOMATIC","GENERATED",7)
        check(row["stored"]==dict(mode=expected[0],routes=[expected[1]],operations=expected[2]),"Unexpected service model")
        check(all(c==200 for c in row["capabilities"].values()) if arm!="MANUAL" else
              all(c in (401,403) for c in row["capabilities"].values()), "Read access differs from advertised model")
        trace = row["trace"] = {k:[e for e in self.tap.since(t0,k) if e["start_ns"]<=end]
                 for k in ("discovered","live_docs","inspect","sync","publication")}
        if arm != "MANUAL":
            reg, doc, inspect = (trace[k][0] for k in ("discovered","live_docs","inspect"))
            sync = next(e for e in trace["sync"] if "/catalog-service/" in e["path"])
            refresh = next(e for e in trace["publication"] if e["method"]=="POST" and e["start_ns"]>=sync["start_ns"])
            row.update(discovery_ms=ms(reg["end_ns"],t0),docs_http_ms=ms(doc["end_ns"],doc["start_ns"]),
                       post_docs_to_inspect_ms=ms(inspect["start_ns"],doc["end_ns"]),
                       sync_including_refresh_ms=ms(sync["end_ns"],sync["start_ns"]),
                       refresh_http_ms=ms(refresh["end_ns"],refresh["start_ns"]))


    def poll(self):
        return self.call(BASE["manager"]+"/internal/admin/poll","POST",headers=self.c.headers)

    def controlled_cycles(self):
        self.c.stop("manager")
        self.tap.live = False
        self.publish("expanded")
        self.launch("manager",[f"--server.port={PORT['manager']}","--server.address=127.0.0.1","--manager.polling-enabled=false",
            "--manager.mode=APPLY","--manager.admission-mode=DISCOVERY","--manager.timeout-ms=10000",
            "--manager.eureka-url="+BASE["registry"]+"/eureka/","--manager.auth-base-url="+BASE["sync"]])
        wait("Manager HTTP",lambda:self.http(BASE["manager"]+"/observations")[0]==200)
        self.poll()
        wait("Seed public selection",lambda:self.probe()[0]==[200,200])

    def signatures(self):
        # Includes identities, policies, config versions and latest event; also count the whole event journal.
        states = {n:self.config(n) for n in ("catalog","order","inventory")}
        fingerprint = digest(json.dumps(states,sort_keys=True).encode())
        count = int(self.c.sql("SELECT count(*) FROM users.adaptation_event"))
        return fingerprint,count,states["catalog"]["service"]["configuration_version"]

    def cycle(self,row,changed):
        before = self.signatures()
        profile = self.tap.profile
        if changed:
            profile = "authenticated" if profile=="expanded" else "expanded"
        self.publish(profile)
        row.update(profile=profile,before=before)
        start = now()
        snapshot = self.poll()
        end = now()
        outcomes = {o["name"]:o.get("sync") for o in snapshot["observations"] if o["name"] in ("catalog-service","order-service","inventory-service")}
        trace = {k:[e for e in self.tap.since(start,k) if e["start_ns"]<=end] for k in ("cycle","fetch","sync","publication")}
        after = self.signatures()
        codes = self.probe()[0]
        writes = len(trace["sync"])
        publications = sum(e["method"]=="POST" for e in trace["publication"])
        row.update(latency_ms=ms(end,start),start_ns=start,end_ns=end,after=after,trace=trace,
                   writes=writes,publications=publications,outcomes=outcomes,gateway_codes=codes)
        check(snapshot["mode"]=="APPLY" and snapshot["registryStatus"]=="AVAILABLE" and len(outcomes)==3,"Incomplete observation")
        check(len(trace["cycle"])==len(trace["fetch"])==1,"Unexpected observation count")
        check(codes==([200,200] if profile=="expanded" else [401,200]),"Selection policy through Gateway")
        if changed:
            check(writes==publications==1 and after[1]==before[1]+1 and after[2]==before[2]+1 and
                  trace["sync"][0]["status"]=="APPLIED","Change did not produce one applied version")
        else:
            check(before==after and writes==publications==0 and all(x.get("action")=="SKIP" and x.get("status")=="UNCHANGED"
                  for x in outcomes.values()),"Unchanged cycle produced side effects or unexpected sync outcome")


def csv_rows(path,rows):
    if rows:
        fields=list(dict.fromkeys(k for row in rows for k in row))
        with path.open("w",encoding="utf-8-sig",newline="") as stream:
            writer=csv.DictWriter(stream,fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)


def tables(output,rows):
    csv_rows(output/"raw.csv",[{k:(json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list,tuple)) else v)
            for k,v in row.items()} for row in rows])
    groups=defaultdict(list)
    for row in rows:
        if row["ok"]:
            for metric in ("latency_ms","admin_actions","route_ready_ms","discovery_ms","docs_http_ms","post_docs_to_inspect_ms",
                           "sync_including_refresh_ms","refresh_http_ms","writes","publications"):
                if metric in row:
                    groups[row["section"],row["scenario"],metric].append(row[metric])
    stats=[]
    for (section,scenario,metric),v in groups.items():
        stats.append(dict(section=section,scenario=scenario,metric=metric,n=len(v),mean=statistics.mean(v),
                     median=statistics.median(v),minimum=min(v),maximum=max(v),sd=statistics.stdev(v) if len(v)>1 else "",
                     p95=statistics.quantiles(v,n=100,method="inclusive")[94] if len(v)>=20 else ""))
    csv_rows(output/"statistics.csv",stats)


def main():
    output = args.output / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S_%fZ")
    output.mkdir(parents=True)
    print("Results:",output,flush=True)
    rows=[]
    with tempfile.TemporaryDirectory(prefix="ag-catalog-study-") as temp:
        lab=Study(args,temp,output)
        lab.tap=Monitor()
        conditions=dict(start=utc(),python=platform.python_version(),os=platform.platform(),cpu=os.cpu_count(),
            repo=str(args.repo),pg_port=args.pg_port,java=args.java,manager_delay_s=args.delay,probe_sleep_s=.1,
            registry_cache_ms=1000,gateway_eureka_fetch_s=2,loadbalancer_cache_ttl_s=1,
            java_options="-XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -Xmx300m",
            java_version=subprocess.run([args.java,"-version"],capture_output=True,text=True).stderr.strip(),
            postgres_version=subprocess.check_output([args.pg_bin/"postgres","--version"],text=True).strip(),
            endpoints=BASE,registrar_relay_port=REG_PORT,publication_relay_port=REFRESH_PORT,
            jar_sha256={name:digest(next((args.repo/module/"target").glob("*.jar")).read_bytes()) for name,module in lab.modules.items()})
        def save():
            (output/"conditions.json").write_text(json.dumps(conditions,indent=2,ensure_ascii=False),encoding="utf-8")
        save()
        def trial(section,arm,n,fn):
            row=dict(section=section,scenario=arm,repeat=n,started=utc(),ok=False)
            try:
                fn(row)
                row["ok"]=True
            except Exception as exc:
                row["error"]=f"{type(exc).__name__}: {exc}"
                raise
            finally:
                row["finished"]=utc()
                rows.append(row)
                with (output/"raw.jsonl").open("a",encoding="utf-8") as stream:
                    print(json.dumps(row,ensure_ascii=False),file=stream)
                print(section,arm,n,row["ok"],row.get("latency_ms"),row.get("error",""),flush=True)
        try:
            lab.start()
            lab.prepare()
            conditions["contract_hashes"]={k:digest(v) for k,v in lab.tap.documents.items()}
            save()
            # Interleave modes and alternate pair order. End with automatic Catalog for cycle trials.
            for n in range(1,11):
                arms=("AUTOMATIC","MANUAL") if n%2 else ("MANUAL","AUTOMATIC")
                for arm in arms:
                    trial("4.4",arm,n,lambda r:lab.onboarding(r,arm))
                trial("4.3","DISCOVERY",n,lambda r:lab.onboarding(r,"DISCOVERY"))
            lab.controlled_cycles()
            for n in range(1,21):
                for changed in ((False,True) if n%2 else (True,False)):
                    trial("4.5","changed" if changed else "unchanged",n,lambda r:lab.cycle(r,changed))
        except Exception as exc:
            conditions["error"]=f"{type(exc).__name__}: {exc}"
            raise
        finally:
            conditions["finished"]=utc()
            conditions["completed"]=sum(r["ok"] for r in rows)
            save()
            tables(output,rows)
            lab.close()

if __name__=="__main__":
    main()
