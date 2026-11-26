"""Independently reconcile a finished run's counts, probe timestamps, window counts and exported means."""
import argparse
import csv
import json
from collections import Counter
from math import isclose
from pathlib import Path
from statistics import mean


def require(value,message):
    if not value:
        raise ValueError(message)


def audit(out):
    conditions=json.loads((out/"conditions.json").read_text(encoding="utf-8"))
    p=conditions["parameters"]
    rows=[json.loads(line) for line in (out/"raw.jsonl").read_text(encoding="utf-8").splitlines()]
    events=json.loads((out/"control-events.json").read_text(encoding="utf-8"))
    schedule={"4.2":(p["checks"],["add","restrict","permission","remove"]),"4.3":(p["timing"],["add","restrict","remove"]),
              "4.4":(p["pairs"],["restrict","relax"]),"4.5":(p["faults"],["replay","version_conflict","override",
              "openapi_outage","registry_outage","gateway_outage"]),"4.6":(p["windows"],["fixed_window"])}
    expected=[]
    for section in set(p["sections"]):
        count,scenarios=schedule[section]
        expected.extend((section,scenario,rep,arm,delay) for scenario in scenarios for rep in range(1,count+1)
                        for arm in (["adaptive","manual"] if section=="4.4" else [""])
                        for delay in (p["intervals"] if section=="4.6" else [p["delay"]]))
    actual=[(r["section"],r["scenario"],r["repeat"],r.get("arm",""),r["manager_delay_s"]) for r in rows]
    require(Counter(actual)==Counter(expected),"Missing or duplicated planned repetitions")
    require(conditions["status"]=="complete" and all(r["ok"] for r in rows),"Run includes a failure")
    for r in rows:
        if "probes" in r:
            probes=r["probes"]
            require(probes[-1]["codes"]==r["expected_codes"] and all(v["codes"]!=r["expected_codes"] for v in probes[:-1]),
                    "Gateway outcome is not the first matching pair")
            require(r["latency_ms"]==probes[-1]["end_ms"] and r["confirmed_ns"]>=r["published_ns"],"Wrong timing origin")
            require(all(a["end_ms"]<=b["start_ms"] for a,b in zip(probes,probes[1:])),"Overlapping probe pairs")
        if "manager_fetch_ms" in r:
            writes=[e for e in events if e["kind"]=="sync" and e["start_ns"]>=r["published_ns"]
                    and e["request_id"]==r["request_id"] and e["status"]=="APPLIED"]
            require(writes and writes[0]["version"]==r["version"],"No matching durable publication")
            reads=[e for e in events if e["kind"]=="fetch" and e["start_ns"]>=r["published_ns"] and e["profile"]==r["to_profile"]]
            require(reads and abs((reads[0]["start_ns"]-r["confirmed_ns"])/1e6-r["manager_fetch_ms"])<=.0001,
                    "Incorrect first read timestamp")
        if r["section"]=="4.6":
            start,end=r["window_start_ns"],r["window_end_ns"]
            require(end-start==round(p["window_s"]*1e9),"Unequal window duration")
            require(r["cycles"]==sum(e["kind"]=="cycle" and start<=e["start_ns"]<end for e in events),"Window count mismatch")
    with (out/"raw.csv").open(encoding="utf-8-sig",newline="") as f:
        require(len(list(csv.DictReader(f)))==len(rows),"Raw CSV row count differs")
    with (out/"statistics.csv").open(encoding="utf-8-sig",newline="") as f:
        for entry in csv.DictReader(f):
            vals=[r[entry["metric"]] for r in rows if r["section"]==entry["section"] and r["scenario"]==entry["scenario"]
                  and r.get("arm","")==entry["arm"] and r["manager_delay_s"]==float(entry["manager_delay_s"]) and entry["metric"] in r]
            require(len(vals)==int(entry["n"]) and isclose(mean(vals),float(entry["mean"]),abs_tol=.000051),"Statistics mismatch")
    print("Verified:",len(rows),"completed repetitions, matching control events, CSV and summary means")
    return Counter(r["section"] for r in rows)


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("directory",type=Path)
    audit(parser.parse_args().directory)
