"""Verify saved 4.4 files without starting the stand."""
import csv
import json
import statistics
import sys
from collections import Counter
from math import isclose
from pathlib import Path


def verify(directory):
    def table(name):
        with (directory/name).open(encoding="utf-8-sig", newline="") as stream:
            return list(csv.DictReader(stream))
    rows=[json.loads(line) for line in (directory/"raw.jsonl").read_text(encoding="utf8").splitlines()]
    assert len(rows)==20 and all(r["ok"] for r in rows)
    assert Counter((r["pair"],r["arm"]) for r in rows)==Counter((n,a) for n in range(1,11) for a in ("MANUAL","AUTOMATIC"))
    assert len({r["instance"] for r in rows})==20
    flat=table("raw.csv")
    cells=table("access.csv")
    pairs=table("paired_differences.csv")
    assert len(flat)==20 and len(cells)==420 and len(pairs)==10
    ix=0
    expected_ops={"list":200,"detail":200,"selection":200,"categories":200,"create":201,"update":200,"delete":204}
    compared={}
    for r,summary in zip(rows,flat,strict=True):
        arm,pair=r["arm"],r["pair"]
        manual=arm=="MANUAL"
        assert r["registration"]["method"]=="POST" and r["registration"]["status"]==204
        assert r["before"]==dict(configuration_http=404,published_routes=0,role_mappings=0)
        assert r["latency_ms"]==round((r["ready_ns"]-r["registration"]["end_ns"])/1e6,4)==float(summary["latency_ms"])
        assert r["admin_requests"]==len(r["actions"])==(3 if manual else 1)
        assert r["routes_created"]==len(set(r["published_route_ids"]))==(2 if manual else 1)
        assert r["mode"]==arm and r["operations"]==(0 if manual else 7)
        if manual:
            assert [a["status"] for a in r["actions"]]==[201,201,200]
            routes=sorted(r["published_routes"],key=lambda route:route["order"])
            assert "Methods: [GET]" in routes[0]["predicate"] and not routes[0]["filters"]
            assert all(method in routes[1]["predicate"] for method in ("POST","PUT","DELETE"))
            assert any("AuthenticationGatewayFilterFactory" in item for item in routes[1]["filters"])
            public,protected=r["actions"][1]["body"]
            assert public["filters"]==public["roles"]==[] and public["predicates"]==[{"name":"Method","args":{"methods":"GET"}}]
            assert protected["filters"]==[{"name":"Authentication","args":{}}] and protected["roles"]==["ROLE_EXPERIMENT_WRITER"]
            pred=protected["predicates"][0]
            assert pred["name"]=="Method" and pred["args"]["methods"]=="POST,PUT,DELETE"
            assert set(v for k,v in pred["args"].items() if k!="methods")=={"POST","PUT","DELETE"}
        else:
            assert r["actions"][0]["status"]==200 and r["actions"][0]["method"]=="PUT"
        assert r["probes"][-1][1]==200 and r["deactivated_status"]==404
        decisions={}
        for c in r["matrix"]:
            actor,op,code=c["person"],c["operation"],c["status"]
            assert actor in ("guest","ordinary","writer") and op in expected_ops
            allowed=op in ("list","detail","selection","categories") or actor=="writer"
            assert (code==expected_ops[op]) if allowed else (code in (401,403))
            assert c["decision"]==("ALLOW" if allowed else "DENY")
            assert c["end_ns"]<=r["ready_ns"]
            assert (actor,op) not in decisions
            decisions[actor,op]=c["decision"]
            flat_cell=cells[ix]
            assert (int(flat_cell["pair"]),flat_cell["arm"],flat_cell["person"],flat_cell["operation"],int(flat_cell["status"]))==(pair,arm,actor,op,code)
            ix+=1
        assert len(decisions)==21
        compared[pair,arm]=decisions
    for p in pairs:
        i=int(p["pair"])
        assert p["equal_decisions"]=="True" and compared[i,"MANUAL"]==compared[i,"AUTOMATIC"]
        for k in ("latency_ms","admin_http_ms","first_public_read_ms","admin_requests","routes_created"):
            values={r["arm"]:r[k] for r in rows if r["pair"]==i}
            assert isclose(float(p[k]),values["AUTOMATIC"]-values["MANUAL"],abs_tol=1e-8)
    stats=table("statistics.csv")
    assert len(stats)==15
    for stat in stats:
        group,k=stat["group"],stat["metric"]
        v=[r[k] for r in rows if r["arm"]==group] if group!="AUTOMATIC minus MANUAL" else [float(p[k]) for p in pairs]
        assert len(v)==int(stat["n"])==10
        for field,fn in (("mean",statistics.mean),("median",statistics.median),("minimum",min),("maximum",max),("sample_sd",statistics.stdev)):
            assert isclose(float(stat[field]),fn(v),abs_tol=1e-8)
    print("Verified 10 pairs, 420 Gateway decisions, administrative requests, route counts and statistics.")


if __name__=="__main__":
    verify(Path(sys.argv[1]))
