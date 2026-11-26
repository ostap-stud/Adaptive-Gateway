"""Independent cross-check of raw timestamps, Gateway responses and published tables."""
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

p=Path(sys.argv[1])
rows=[json.loads(s) for s in (p/"raw.jsonl").read_text(encoding="utf8").splitlines()]
def read_csv(name):
    with (p/name).open(encoding="utf-8-sig",newline="") as stream:
        return list(csv.DictReader(stream))
flat=read_csv("raw.csv")
assert len(rows)==len(flat)==70
assert all(r["ok"] for r in rows)
conds=json.loads((p/"conditions.json").read_text(encoding="utf8"))
assert conds["completed"]==70 and len(conds["jar_sha256"])==7
seen=set()
observations=defaultdict(list)
for r,f in zip(rows,flat,strict=True):
    key=(r["section"],r["scenario"],r["repeat"])
    assert key not in seen and f["section"]==key[0] and f["scenario"]==key[1] and int(f["repeat"])==key[2]
    seen.add(key)
    observations[key[:2]].append(r)
    assert float(f["latency_ms"])==r["latency_ms"]
    if "registration" in r:
        reg=r["registration"]
        assert reg["status"]==204 and reg["method"]=="POST"
        assert r["latency_ms"]==round((r["ready_ns"]-reg["end_ns"])/1e6,4)
        assert r["route_ready_ms"]<=r["latency_ms"] and r["route_probes"][-1][1]==200
        assert len(r["functional"])==9 and r["functional"]["deactivated"]==404
        auto=r["scenario"]!="MANUAL"
        assert r["stored"]=={"mode":"AUTOMATIC" if auto else "MANUAL","routes":["GENERATED" if auto else "MANUAL"],"operations":7 if auto else 0}
        assert r["functional"]["guest_write"]==401 and r["functional"]["unprivileged_write"]==(403 if auto else 401)
        assert set(r["capabilities"].values())==({200} if auto else {401})
        assert [a["end_ns"]>=a["start_ns"] for a in r["actions"]]==[True]*r["admin_actions"]
        assert r["admin_actions"]=={"MANUAL":3,"AUTOMATIC":1,"DISCOVERY":0}[r["scenario"]]
        if auto:
            assert r["trace"]["live_docs"][0]["status"]==200
            assert r["trace"]["sync"][0]["status"]=="APPLIED"
    else:
        changed=r["scenario"]=="changed"
        assert r["latency_ms"]==round((r["end_ns"]-r["start_ns"])/1e6,4)
        assert len(r["trace"]["cycle"])==len(r["trace"]["fetch"])==1
        assert r["writes"]==r["publications"]==int(changed)
        assert r["after"][1]-r["before"][1]==r["after"][2]-r["before"][2]==int(changed)
        assert (r["after"][0]!=r["before"][0])==changed
        assert r["gateway_codes"]==([200,200] if r["profile"]=="expanded" else [401,200])
ids=[r["instance"] for r in rows if "instance" in r]
assert len(set(ids))==len(ids)==30
assert sorted(map(len,observations.values()))==[10,10,10,20,20]
for stat in read_csv("statistics.csv"):
    items=observations[stat["section"],stat["scenario"]]
    v=[r[stat["metric"]] for r in items]
    assert int(stat["n"])==len(v)
    for k,fn in [("mean",statistics.mean),("median",statistics.median),("minimum",min),("maximum",max),("sd",statistics.stdev)]:
        assert abs(float(stat[k])-fn(v))<1e-6
    if len(v)>=20:
        ordered=sorted(v)
        pos=(len(v)-1)*.95
        lo=int(pos)
        assert abs(float(stat["p95"])-(ordered[lo]+(pos-lo)*(ordered[lo+1]-ordered[lo])))<1e-6
    else:
        assert stat["p95"]==""
print("Verified 70 raw observations, 30 unique registrations, Gateway criteria and summary statistics.")
