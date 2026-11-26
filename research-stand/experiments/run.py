"""Run chapter 4 experiments on dedicated loopback ports; no working database credentials required."""
import argparse
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import json
import os
import platform
import random
import shutil
import subprocess
import sys
import tempfile
import traceback

sys.dont_write_bytecode = True
from lab import Lab, check
from measure import Series
from observers import BASE, digest


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo",type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output",type=Path,default=Path(__file__).resolve().parent/"results")
    parser.add_argument("--java",default=shutil.which("java"))
    parser.add_argument("--pg-bin",type=Path,default=Path(r"C:\Program Files\PostgreSQL\18\bin"))
    parser.add_argument("--pg-port",type=int,default=39546)
    parser.add_argument("--sections",nargs="+",choices=["4.2","4.3","4.4","4.5","4.6"],default=["4.3","4.6","4.2","4.4","4.5"])
    parser.add_argument("--delay",type=float,default=1.5)
    parser.add_argument("--probe-ms",type=int,default=100)
    parser.add_argument("--timing",type=int,default=20)
    parser.add_argument("--checks",type=int,default=5)
    parser.add_argument("--pairs",type=int,default=10)
    parser.add_argument("--faults",type=int,default=5)
    parser.add_argument("--windows",type=int,default=3)
    parser.add_argument("--intervals",type=float,nargs="+",default=[1,3,5,10])
    parser.add_argument("--window-s",type=float,default=24)
    parser.add_argument("--seed",type=int,default=413)
    args=parser.parse_args()
    check(args.java and min(args.timing,args.checks,args.pairs,args.faults,args.windows,args.probe_ms)>0,"Invalid counts or missing Java")
    check(min(args.delay,*args.intervals)>=0.1 and args.window_s>2*max(args.intervals),"Invalid interval or short window")
    out=args.output.resolve()/datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S_%fZ")
    out.mkdir(parents=True,exist_ok=False)
    print("Results:",out,flush=True)
    def save(name,obj):
        (out/name).write_text(json.dumps(obj,indent=2,ensure_ascii=False,default=str),encoding="utf-8")
    args.repo=args.repo.resolve()
    metadata=dict(parameters=vars(args).copy(), platform=platform.platform(),logical_cpu=os.cpu_count(),
                  python=platform.python_version(),jvm_flags="-XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -Xmx300m",
                  endpoints=BASE, registry_cache_ms=1000,registry_fetch_s=2,gateway_cache_ttl_s=1,manager_timeout_ms=10000,
                  clock="time.perf_counter_ns",status="starting")
    save("conditions.json",metadata)
    with tempfile.TemporaryDirectory(prefix="adaptive-exp-",ignore_cleanup_errors=True) as work:
        lab=Lab(args,work,out)
        series=Series(lab,out,random.Random(args.seed))
        try:
            metadata["jars_sha256"]={}
            for module in lab.modules.values():
                jars=list((args.repo/module/"target").glob("*.jar"))
                check(len(jars)==1,"Build exactly one jar: "+module)
                metadata["jars_sha256"][jars[0].name]=hashlib.sha256(jars[0].read_bytes()).hexdigest()
            for key,cmd in {"java":[args.java,"-version"],"postgres":[args.pg_bin/"postgres","--version"],
                            "commit":["git","-c","safe.directory="+args.repo.as_posix(),"-C",args.repo,"rev-parse","HEAD"]}.items():
                p=subprocess.run(cmd,capture_output=True,text=True,errors="replace")
                metadata[key]=(p.stdout+p.stderr).strip()
            lab.start()
            metadata.update(status="running",docs_sha256={k:digest(v) for k,v in lab.tap.documents.items()})
            save("conditions.json",metadata)
            for section in dict.fromkeys(args.sections):
                if section!="4.6" and lab.delay!=args.delay:
                    lab.start_manager(args.delay)
                if section=="4.3":
                    series.timing()
                elif section=="4.6":
                    series.intervals()
                else:
                    from scenarios import run_section
                    run_section(section,series)
            metadata["status"]="complete"
        except BaseException as exc:
            metadata.update(status="failed",error=repr(exc))
            (out/"error.txt").write_text(traceback.format_exc(),encoding="utf-8")
            raise
        finally:
            try:
                lab.close()
            finally:
                save("control-events.json",list(lab.tap.events))
                save("conditions.json",metadata)
                if (out/"raw.jsonl").exists():
                    from summarize import summarize
                    summarize(out)


if __name__=="__main__":
    main()
