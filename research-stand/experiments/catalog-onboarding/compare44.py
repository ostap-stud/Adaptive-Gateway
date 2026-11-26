"""Paired comparison of full Catalog onboarding with method-specific manual routes."""
import json
import platform
import statistics
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path

from study import (AUTH, BASE, CAT, GATE, MAPPING, REG_PORT, REFRESH_PORT, ROLE,
                   Monitor, Study, args, check, csv_rows, digest, ms, now, utc, wait)


def manual_routes():
    routes = []
    for order, (methods, protected) in enumerate([("GET", False), ("POST,PUT,DELETE", True)]):
        predicate_args = {"methods": methods}
        if protected:
            # Auth reads the CSV field. Gateway GATHER_LIST takes each map value as one method.
            predicate_args.update({f"_genkey_{i}": method for i, method in enumerate(methods.split(","))})
        routes.append(dict(route=CAT+"/**", serviceName="catalog-service", isInternal=False,
            routeDescription="Catalog "+methods, order=order,
            predicates=[dict(name="Method", args=predicate_args)],
            filters=[dict(name="Authentication", args={})] if protected else [],
            roles=[ROLE] if protected else []))
    return routes


def configure(lab, row, arm):
    def action(method, url, body, expected):
        entry = dict(method=method, url=url, body=body, start_ns=now())
        row["actions"].append(entry)
        status, _ = lab.http(url, method, body, lab.admin)
        entry.update(end_ns=now(), status=status)
        check(status==expected, f"{method} {url}: HTTP {status}")
    if arm == "MANUAL":
        action("POST", AUTH+"/service/add", [{"name":"catalog-service","description":"Catalog"}], 201)
        action("POST", AUTH+"/service/route/add", manual_routes(), 201)
        action("POST", GATE+"/actuator/gateway/refresh", None, 200)
    else:
        action("PUT", MAPPING, {"roles":[ROLE],"reason":"Catalog onboarding experiment"}, 200)


def access_matrix(lab, row):
    uri = GATE+CAT
    matrix = row["matrix"]
    users = {"guest":None, "ordinary":lab.user, "writer":lab.writer}

    def request(person, operation, method, path, allowed, body=None):
        start = now()
        code, raw = lab.http(uri+path, method, body, users[person])
        cell = dict(person=person, operation=operation, method=method, path=path,
                    start_ns=start, end_ns=now(), status=code)
        matrix.append(cell)
        check(code in allowed, f"{person} {operation}: HTTP {code}, expected {allowed}")
        cell["decision"] = "DENY" if code in (401,403) else "ALLOW"
        return json.loads(raw) if code in (200,201) else None

    cats = request("writer","categories","GET","/categories",(200,))
    cat = cats[0]["id"]
    original = dict(name=row["instance"],categoryId=cat,price=15)
    changed = dict(original,price=16)
    product = request("writer","create","POST","/products",(201,),original)
    product_id = product["id"]
    path = "/products/"+str(product_id)
    check(product["name"]==original["name"] and product["price"]==15,"Created product content")
    for person in users:
        if person!="writer":
            cats = request(person,"categories","GET","/categories",(200,))
            check(any(c["id"]==cat for c in cats),"Category list content")
        listing = request(person,"list","GET","/products?q="+row["instance"],(200,))
        check([p["id"] for p in listing["products"]]==[product_id],"List returned wrong products")
        selection = request(person,"selection","GET","/selection",(200,))
        check(isinstance(selection["products"],list),"Selection content")
        detail = request(person,"detail","GET",path,(200,))
        check(detail["id"]==product_id and detail["price"]==15,"Detail or unauthorized side effect")
        if person != "writer":
            request(person,"create","POST","/products",(401,403),original)
            request(person,"update","PUT",path,(401,403),changed)
            request(person,"delete","DELETE",path,(401,403))
    updated = request("writer","update","PUT",path,(200,),changed)
    check(updated["id"]==product_id and updated["price"]==16,"Updated product content")
    request("writer","delete","DELETE",path,(204,))
    status,_ = lab.http(uri+path,token=lab.writer)
    end = now()
    row.update(deactivated_status=status,ready_ns=end)
    check(status==404,"Deactivated product still readable")
    check(len({(c["person"],c["operation"]) for c in matrix})==len(matrix)==21,"Incomplete matrix")


def measure(lab, row):
    arm = row["arm"]
    lab.reset(arm)
    # The config API returns 404 after reset. Check the published routes and permission mapping too.
    def catalog_routes():
        routes = lab.call(GATE+"/actuator/gateway/routes", token=lab.admin)
        return [r for r in routes if str(r.get("uri","")).lower()=="lb://catalog-service"]
    check(not catalog_routes(),"Old published Catalog route")
    count = int(lab.c.sql("SELECT count(*) FROM users.permission_role WHERE permission='catalog.write'"))
    check(count==0,"Old writer mapping")
    row["before"] = dict(configuration_http=404, published_routes=0, role_mappings=count)
    row["instance"], row["registration"] = lab.new_catalog()
    start = row["registration"]["end_ns"]
    configure(lab,row,arm)
    def available():
        status,raw = lab.http(GATE+CAT+"/products")
        row["probes"].append([now(),status])
        return status==200 and isinstance(json.loads(raw).get("products"),list)
    wait("Public list through Gateway",available,45)
    access_matrix(lab,row)
    cfg = lab.config()
    sources = [r["source"] for r in cfg["routes"] if r["active"]]
    ops = sum(o["active"] for o in cfg["operations"])
    published = catalog_routes()
    row.update(routes_created=len(cfg["routes"]), published_route_ids=[r["route_id"] for r in published],
               published_routes=published, sources=sources, operations=ops, mode=cfg["service"]["management_mode"],
               admin_requests=len(row["actions"]), latency_ms=ms(row["ready_ns"],start),
               first_public_read_ms=ms(row["probes"][-1][0],start),
               admin_http_ms=sum(a["end_ns"]-a["start_ns"] for a in row["actions"])/1e6)
    manual = arm=="MANUAL"
    expected = 2 if manual else 1
    check(len(set(row["published_route_ids"]))==row["routes_created"]==len(sources)==expected,
          f"Unexpected persisted or published routes: {row['published_route_ids']}, {sources}")
    check(row["mode"]==arm and sources==(["MANUAL"]*2 if manual else ["GENERATED"]) and ops==(0 if manual else 7),
          "Unexpected service configuration")


def save_tables(out,rows):
    scalar = ("pair","arm","ok","started","finished","instance","latency_ms","first_public_read_ms",
              "admin_http_ms","admin_requests","routes_created","operations","error")
    csv_rows(out/"raw.csv",[{k:r.get(k,"") for k in scalar} for r in rows])
    csv_rows(out/"access.csv",[dict(pair=r["pair"],arm=r["arm"],**c) for r in rows for c in r["matrix"]])
    metrics = ("latency_ms","first_public_read_ms","admin_http_ms","admin_requests","routes_created")
    stats, pairs = [], []
    def describe(group,metric,values):
        if values:
            stats.append(dict(group=group,metric=metric,n=len(values),mean=statistics.mean(values),
                median=statistics.median(values),minimum=min(values),maximum=max(values),
                sample_sd=statistics.stdev(values) if len(values)>1 else ""))
    for arm in ("MANUAL","AUTOMATIC"):
        for metric in metrics:
            describe(arm,metric,[r[metric] for r in rows if r["arm"]==arm and r["ok"]])
    for i in range(1,11):
        arms={r["arm"]:r for r in rows if r["pair"]==i and r["ok"]}
        if len(arms)==2:
            def decisions(r):
                return {(c["person"],c["operation"]):c["decision"] for c in r["matrix"]}
            pairs.append(dict(pair=i,equal_decisions=decisions(arms["MANUAL"])==decisions(arms["AUTOMATIC"]),
                **{m:arms["AUTOMATIC"][m]-arms["MANUAL"][m] for m in metrics}))
    for metric in metrics:
        describe("AUTOMATIC minus MANUAL",metric,[p[metric] for p in pairs])
    csv_rows(out/"statistics.csv",stats)
    csv_rows(out/"paired_differences.csv",pairs)


def main():
    out = args.output / ("4.4-methods-"+datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S_%fZ"))
    out.mkdir(parents=True)
    print(out,flush=True)
    rows=[]
    with tempfile.TemporaryDirectory(prefix="catalog-methods-") as work:
        lab=Study(args,work,out)
        lab.tap=Monitor()
        info=dict(section="4.4",start=utc(),platform=platform.platform(),python=platform.python_version(),
            java=subprocess.run([args.java,"-version"],capture_output=True,text=True).stderr.strip(),
            postgres=subprocess.check_output([args.pg_bin/"postgres","--version"],text=True).strip(),
            manager_delay_s=args.delay,probe_sleep_s=.1,registry_cache_ms=1000,gateway_registry_fetch_s=2,
            loadbalancer_cache_ttl_s=1,pg_port=args.pg_port,registration_port=REG_PORT,refresh_port=REFRESH_PORT,
            endpoints=BASE,profile="expanded",pairs=10,route_batch=manual_routes(),
            jar_sha256={k:digest(next((args.repo/v/"target").glob("*.jar")).read_bytes()) for k,v in lab.modules.items()})
        def save():
            (out/"conditions.json").write_text(json.dumps(info,indent=2),encoding="utf8")
        save()
        try:
            lab.start()
            lab.prepare()
            info["jvm_options"]=lab.c.env["JAVA_TOOL_OPTIONS"]
            info["expanded_contract_sha256"]=digest(lab.tap.documents["expanded"])
            save()
            for pair in range(1,11):
                arms=("AUTOMATIC","MANUAL") if pair%2 else ("MANUAL","AUTOMATIC")
                for arm in arms:
                    r=dict(pair=pair,arm=arm,started=utc(),actions=[],probes=[],matrix=[],ok=False)
                    try:
                        measure(lab,r)
                        r["ok"]=True
                    except Exception as exc:
                        r["error"]=f"{type(exc).__name__}: {exc}"
                        raise
                    finally:
                        r["finished"]=utc()
                        rows.append(r)
                        with (out/"raw.jsonl").open("a",encoding="utf8") as f:
                            print(json.dumps(r),file=f)
                        print(pair,arm,r["ok"],r.get("latency_ms"),r.get("error",""),flush=True)
        except Exception as exc:
            info["error"]=str(exc)
            raise
        finally:
            try:
                info.update(finished=utc(),completed=sum(r["ok"] for r in rows))
                save()
                save_tables(out,rows)
            finally:
                lab.close()


if __name__=="__main__":
    main()
