"""Rebuild tables from raw repetitions. Run separately with a result directory."""
import argparse
import csv
import json
from collections import defaultdict
from math import ceil
from pathlib import Path
from statistics import mean, median, stdev

GROUP = ("section","scenario","arm","manager_delay_s")
METRICS = ("latency_ms","manager_fetch_ms","sync_start_ms","sync_response_ms","sync_rtt_ms",
           "confirmation_roundtrip_ms","admin_request_ms","admin_actions","cycles")


def write_csv(path, rows):
    if not rows:
        return
    columns=list(dict.fromkeys(k for r in rows for k in r))
    with path.open("w",encoding="utf-8-sig",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=columns)
        writer.writeheader()
        for row in rows:
            writer.writerow({k:json.dumps(v,ensure_ascii=False,separators=(",",":")) if isinstance(v,(dict,list))
                             else v for k,v in row.items()})


def describe(vals):
    values=sorted(vals)
    n=len(values)
    if not n:
        return {"n":0}
    return dict(n=n,mean=round(mean(values),4),median=round(median(values),4),min=values[0],max=values[-1],
                sd=round(stdev(values),4) if n>=2 else None,
                p95=values[ceil(.95*n)-1] if n>=20 else None)


def summarize(out):
    rows=[json.loads(line) for line in (out/"raw.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    write_csv(out/"raw.csv",rows)
    groups=defaultdict(list)
    for r in rows:
        groups[tuple(r.get(k,"") for k in GROUP)].append(r)
    counts,stats=[],[]
    for key,members in sorted(groups.items()):
        group=dict(zip(GROUP,key))
        good=[r for r in members if r["ok"]]
        counts.append(dict(**group,attempted=len(members),passed=len(good),failed=len(members)-len(good)))
        for metric in METRICS:
            vals=[r[metric] for r in good if r.get(metric) is not None]
            if vals:
                stats.append(dict(**group,metric=metric,**describe(vals)))
    write_csv(out/"checks.csv",counts)
    write_csv(out/"statistics.csv",stats)
    paired=defaultdict(dict)
    for r in rows:
        if r["section"]=="4.4" and r["ok"]:
            paired[(r["scenario"],r["repeat"],r["manager_delay_s"])][r["arm"]]=r
    diffs=[]
    for (scenario,rep,delay),arms in sorted(paired.items()):
        if set(arms)=={"adaptive","manual"}:
            a,m=arms["adaptive"],arms["manual"]
            diffs.append(dict(scenario=scenario,repeat=rep,manager_delay_s=delay,adaptive_ms=a["latency_ms"],
                              manual_ms=m["latency_ms"],adaptive_minus_manual_ms=round(a["latency_ms"]-m["latency_ms"],4),
                              adaptive_actions=a["admin_actions"],manual_actions=m["admin_actions"]))
    write_csv(out/"pairs.csv",diffs)
    write_csv(out/"paired_statistics.csv",[dict(scenario=label,metric="adaptive_minus_manual_ms",
              **describe([d["adaptive_minus_manual_ms"] for d in diffs if d["scenario"]==label]))
              for label in sorted({d["scenario"] for d in diffs})])
    from tables import tables
    tables(out,stats,counts,diffs)
    try:
        from figures import figures
        figures(out,rows)
    except ImportError as exc:
        print("Tables ready. To generate figures, install the packages in requirements.txt:",exc,flush=True)


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("directory",type=Path)
    summarize(parser.parse_args().directory)
