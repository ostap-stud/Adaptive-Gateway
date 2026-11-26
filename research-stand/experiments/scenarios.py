"""Correctness, paired policy-management comparison, and controlled isolated failures."""
from concurrent.futures import ThreadPoolExecutor
from uuid import UUID

from lab import check, wait
from measure import EXPECTED, ms, selected
from observers import BASE, DOC, PATH, PORT, now

AUTH = BASE["auth"] + "/auth-service"


def invariant_ids(s, initial, cfg):
    op = selected(cfg)
    original = {(v["gateway_path"],v["http_method"]):v["id"] for v in initial["operations"]}
    current = {(v["gateway_path"],v["http_method"]):v["id"] for v in cfg["operations"]}
    check(all(current.get(key)==value for key,value in original.items()),"Existing operation changed ID")
    check(len(current)==len(cfg["operations"]), "Duplicate operation")
    check([r["id"] for r in cfg["routes"]]==[r["id"] for r in initial["routes"]], "Ingress changed ID")
    keys=[(p["api_operation_id"],p["source"]) for p in cfg["policies"]]
    check(len(keys)==len(set(keys)), "Duplicate policy layer")
    return op


def correctness(s):
    lab=s.lab
    initial=s.align("default")
    lab.call(AUTH+"/role/add","POST",[{"roleName":"READER","application":"EXPERIMENT","roleDescription":"Chapter 4"}],lab.admin,201)
    lab.call(AUTH+"/add-roles/observer","POST",["ROLE_EXPERIMENT_READER"],lab.admin)
    mapping=AUTH+"/policy/permissions/catalog.selection"
    for rep in range(1,lab.args.checks+1):
        for label,before,after in (("add","default","expanded"),("restrict","expanded","authenticated"),
                                  ("permission","authenticated","permission"),("remove","permission","default")):
            def verify(row):
                cfg=s.transition(row,before,after,jitter=False)
                op=invariant_ids(s,initial,cfg)
                check(len([x for x in cfg["operations"] if x["active"]])==(6 if after=="default" else 7),"Active count")
                if after=="permission":
                    lab.call(mapping,"PUT",{"roles":["ROLE_EXPERIMENT_READER"],"reason":"Controlled mapping"},lab.admin)
                    row["mapped_codes"]=lab.probe()[0]
                    check(row["mapped_codes"]==[401,200],"Mapped user access")
                    lab.call(mapping,"PUT",{"roles":[],"reason":"Reset controlled mapping"},lab.admin)
                    check(lab.probe()[0]==EXPECTED[after],"Mapping removal")
                row["ids_verified"]=True
                check(op["id"]==s.first_id,"Selection ID")
            s.trial("4.2",label,rep,verify)


def policy_url(cfg):
    return AUTH + f'/policy/operations/{selected(cfg)["id"]}/ADMIN'


def compare(s):
    lab=s.lab
    for rep in range(1,lab.args.pairs+1):
        arms=("manual","adaptive") if rep%2 else ("adaptive","manual")
        for arm in arms:
            start=s.align("expanded")
            url=policy_url(start)
            if arm=="manual":
                lab.mode("OBSERVE")
            try:
                for label,before,after in (("restrict","expanded","authenticated"),("relax","authenticated","expanded")):
                    def execute(row):
                        row.update(arm=arm,admin_actions=0)
                        if arm=="adaptive":
                            s.transition(row,before,after)
                            return
                        check(lab.probe()[0]==EXPECTED[before],"Manual baseline behavior")
                        row.update(from_profile=before,to_profile=after,baseline_codes=EXPECTED[before],expected_codes=EXPECTED[after])
                        published,t0=lab.publish(after)
                        row.update(published_ns=published,confirmed_ns=t0,confirmation_roundtrip_ms=ms(t0,published))
                        method="PUT" if label=="restrict" else "DELETE"
                        body={"reason":"Paired management trial"}
                        if method=="PUT":
                            body["accessType"]="AUTHENTICATED"
                        row.update(admin_actions=1,admin_method=method,operation_id=selected(start)["id"])
                        def apply_policy():
                            begin=now()
                            lab.call(url,method,body,lab.admin)
                            return begin, now()
                        with ThreadPoolExecutor(max_workers=1) as pool:
                            action=pool.submit(apply_policy)
                            s.poll(row,t0,EXPECTED[after])
                            begin,end=action.result()
                        row["admin_request_ms"]=ms(end,begin)
                        row["admin_dispatch_ms"]=ms(begin,t0)
                        row["version"]=lab.config()["service"]["configuration_version"]
                    s.trial("4.4",label,rep,execute,arm=arm)
            finally:
                if arm=="manual":
                    lab.mode("APPLY")
            s.align("expanded")


def preflight(lab, profile, version):
    lab.publish(profile)
    def proposal():
        o=lab.observation()
        req=o.get("proposedRequest") or {}
        selected_op=next((op for op in req.get("operations",[]) if op["gatewayPath"]==PATH),{})
        if req.get("expectedVersion")==version and selected_op.get("access")=="AUTHENTICATED" and not selected_op["permissions"]:
            return req
    return wait("Fresh read-only proposal",proposal)


def version(cfg):
    return cfg["service"]["configuration_version"]


def same_config(left,right):
    return (version(left)==version(right) and left["operations"]==right["operations"]
            and left["routes"]==right["routes"] and left["policies"]==right["policies"])


def protocol(s,row,conflict):
    lab=s.lab
    before=s.align("expanded")
    lab.mode("OBSERVE")
    try:
        req=preflight(lab,"authenticated",version(before))
        url=lab.config_url()
        row.update(expected_version=req["expectedVersion"], request_id=req["requestId"])
        if conflict:
            policy=policy_url(before)
            lab.call(policy,"PUT",{"accessType":"BLOCKED","reason":"Advance version"},lab.admin)
            current=lab.config()
            check(version(current)>version(before),"No competing version")
            code,_=lab.http(url,"PUT",req,headers=lab.c.headers)
            after=lab.config()
            check(code==409 and same_config(current,after) and current["latestEvent"]==after["latestEvent"],"Stale write was not rejected atomically")
            row.update(http=code,version=version(after))
            lab.call(policy,"DELETE",{"reason":"Finish conflict trial"},lab.admin)
        else:
            first=lab.call(url,"PUT",req,headers=lab.c.headers)
            one=lab.config()
            second=lab.call(url,"PUT",req,headers=lab.c.headers)
            two=lab.config()
            check(first==second and first["status"]=="APPLIED" and same_config(one,two),"Replay changed committed result")
            check(one["latestEvent"]==two["latestEvent"],"Duplicate event")
            count=int(lab.c.sql("SELECT count(*) FROM users.adaptation_event WHERE request_id='"
                                 +str(UUID(req["requestId"]))+"'::uuid"))
            check(count==1,"Duplicate request ID")
            row.update(version=version(two),event_rows=count,status=first["status"])
    finally:
        lab.mode("APPLY")
    s.align("authenticated")


def override(s,row):
    lab=s.lab
    baseline=s.align("expanded")
    url=policy_url(baseline)
    lab.call(url,"PUT",{"accessType":"BLOCKED","reason":"Keep local restriction"},lab.admin)
    check(lab.probe()[0]==[403,403],"Override was not effective")
    lab.publish("authenticated")
    after=wait("Updated OPENAPI",lambda:s.durable("authenticated"))
    p=next(p for p in after["policies"] if p["api_operation_id"]==selected(after)["id"] and p["source"]=="ADMIN")
    codes=lab.probe()[0]
    check(p["access_type"]=="BLOCKED" and codes==[403,403] and selected(after)["id"]==selected(baseline)["id"],"Override lost")
    row.update(operation_id=selected(after)["id"],version=version(after),codes_under_override=codes)
    lab.call(url,"DELETE",{"reason":"End override"},lab.admin)
    check(lab.probe()[0]==[401,200],"Override cleanup")
    row["recovery_codes"]=[401,200]


def unavailable(s,row,kind):
    lab=s.lab
    initial=s.align("expanded")
    if kind=="docs":
        lab.tap.publish("unavailable")
        check(lab.http(BASE["docs"]+DOC,headers={"X-Experiment-Confirmation":"1"})[0]==503,"OpenAPI outage missing")
        failure=wait("Manager failed OpenAPI",lambda: _failed_observation(lab))
        row["diagnostic"]=failure["message"]
    else:
        lab.c.stop("eureka")
        row["direct_registry_http"]=lab.http(BASE["eureka"]+"/eureka/apps")[0]
        check(row["direct_registry_http"]==0,"Owned Eureka still listening")
        wait("Registry unavailable",lambda:lab.snapshot()["registryStatus"]=="UNAVAILABLE")
        # Change a real contract while the Manager cannot list the registry.
        lab.publish("authenticated")
        start=now()
        wait("Two failed registry reads",lambda:len(lab.tap.since(start,"cycle"))>=2)
        row["diagnostic"]=lab.snapshot()["registryStatus"]
    after=lab.config()
    check(same_config(initial,after),"Last working configuration lost during outage")
    row.update(version_before=version(initial),version_during=version(after),codes_during=lab.probe()[0])
    if kind=="registry":
        lab.launch("eureka",["--server.address=127.0.0.1",f"--server.port={PORT['eureka']}","--eureka.server.response-cache-update-interval-ms=1000"])
        def registered():
            live={o["name"] for o in lab.snapshot()["observations"] if o["status"].startswith("ANALYZED")}
            return {"catalog-service","order-service","inventory-service"}<=live
        wait("Services re-register after Eureka restart",registered,150)
        row["restored_registry_services"]=3
    final=s.align("authenticated")
    check(version(final)>version(initial),"Recovery did not apply pending change")
    row.update(version=version(final),recovery_codes=lab.probe()[0])


def _failed_observation(lab):
    obs=lab.observation()
    return obs if obs.get("status")=="OBSERVATION_FAILED" else None


def gateway_failure(s,row):
    lab=s.lab
    initial=s.align("expanded")
    lab.c.stop("gateway")
    check(lab.http(BASE["gateway"]+PATH)[0]==0,"Gateway still listening")
    lab.publish("authenticated")
    def failed():
        ev=lab.config()["latestEvent"]
        return ev if ev["status"]=="FAILED_REFRESH" and ev["configuration_version"]>version(initial) else None
    failure=wait("Durable failed refresh",failed)
    row.update(request_id=failure["request_id"],during_status=failure["status"],version=failure["configuration_version"])
    lab.start_gateway()
    final=s.align("authenticated")
    check(final["latestEvent"]["request_id"]==failure["request_id"] and version(final)==row["version"],"Retry did not reuse event and version")
    row.update(recovery_status=final["latestEvent"]["status"],recovery_codes=lab.probe()[0])


def run_section(section,s):
    if section=="4.2":
        return correctness(s)
    if section=="4.4":
        return compare(s)
    for rep in range(1,s.lab.args.faults+1):
        for scenario,action in [("replay",lambda r:protocol(s,r,False)),("version_conflict",lambda r:protocol(s,r,True)),
                                ("override",lambda r:override(s,r)),("openapi_outage",lambda r:unavailable(s,r,"docs")),
                                ("registry_outage",lambda r:unavailable(s,r,"registry")),("gateway_outage",lambda r:gateway_failure(s,r))]:
            s.trial(section,scenario,rep,action)
