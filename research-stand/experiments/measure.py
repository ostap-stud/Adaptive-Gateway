import json
import time
from datetime import datetime, timezone

from lab import check, wait
from observers import BASE, PATH, now

EXPECTED = {"default":[404,404], "expanded":[200,200], "authenticated":[401,200], "permission":[403,403]}
ACCESS = {"expanded":"PUBLIC","authenticated":"AUTHENTICATED","permission":"AUTHENTICATED"}


def utc():
    return datetime.now(timezone.utc).isoformat()


def ms(t, origin):
    return round((t-origin)/1e6, 4)


def selected(state):
    return next((op for op in state["operations"] if op["gateway_path"] == PATH and op["http_method"] == "GET"), {})


class Series:
    def __init__(self, lab, output, rng):
        self.lab, self.output, self.rng = lab, output, rng
        self.rows = []
        self.first_id = None
        self.record = None

    def trial(self, section, scenario, repeat, fn, **params):
        row = dict(section=section, scenario=scenario, repeat=repeat, started_utc=utc(),
                   manager_delay_s=self.lab.delay, **params)
        self.record = row
        try:
            fn(row)
            row["ok"] = True
        except BaseException as exc:
            row.update(ok=False, error=f"{type(exc).__name__}: {exc}")
            raise
        finally:
            row["finished_utc"] = utc()
            self.rows.append(row)
            with (self.output/"raw.jsonl").open("a",encoding="utf-8") as f:
                f.write(json.dumps(row, ensure_ascii=False)+"\n")
            print(section, scenario, repeat, row["ok"], row.get("latency_ms", ""), flush=True)
            self.record = None

    def durable(self, profile):
        cfg = self.lab.config()
        op = selected(cfg)
        ev = cfg["latestEvent"] or {}
        if ev.get("status") != "APPLIED" or ev["configuration_version"] != cfg["service"]["configuration_version"]:
            return False
        if profile == "default":
            return cfg if not op.get("active") else False
        policy = next((p for p in cfg["policies"] if p["api_operation_id"] == op.get("id") and p["source"]=="OPENAPI"), {})
        if not op.get("active") or policy.get("access_type") != ACCESS[profile]:
            return False
        # Permission and authenticated normalize to the same access type; verify the permission separately.
        if profile in ("permission","authenticated"):
            operations = self.lab.call(BASE["auth"]+"/auth-service/admin/services/catalog-service/operations",token=self.lab.admin)
            actual = next(x for x in operations if x["id"]==op["id"])["permissions"]
            if actual != (["catalog.selection"] if profile=="permission" else []):
                return False
        return cfg

    def align(self, profile):
        if self.lab.tap.profile != profile:
            self.lab.publish(profile)
        wait("Baseline Gateway "+profile, lambda: self.lab.probe()[0] == EXPECTED[profile])
        return wait("Confirmed baseline "+profile, lambda: self.durable(profile))

    def poll(self, row, t0, expected):
        period = self.lab.args.probe_ms * 1_000_000
        deadline, tick = t0 + 45_000_000_000, now()
        row["probes"] = []
        while now() < deadline:
            start = now()
            codes, end = self.lab.probe()
            row["probes"].append(dict(start_ms=ms(start,t0), end_ms=ms(end,t0), codes=codes))
            if codes == expected:
                row.update(latency_ms=ms(end,t0), first_probe_correct=len(row["probes"])==1)
                return
            tick += period
            if tick < end:
                tick += ((end-tick)//period+1)*period
            time.sleep(max(0, (tick-now())/1e9))
        raise TimeoutError("Gateway did not reach " + str(expected))

    def transition(self, row, before, after, jitter=True):
        cfg = self.align(before)
        lab = self.lab
        if jitter:
            pause = self.rng.random()*lab.delay
            row["phase_pause_s"] = round(pause,5)
            time.sleep(pause)
        row.update(from_profile=before,to_profile=after,before_version=cfg["service"]["configuration_version"],
                   baseline_codes=EXPECTED[before],expected_codes=EXPECTED[after])
        published,t0 = lab.publish(after)
        row.update(published_ns=published, confirmed_ns=t0, confirmation_roundtrip_ms=ms(t0,published))
        self.poll(row,t0,EXPECTED[after])
        state = wait("Expected durable state "+after, lambda: self.durable(after))
        ev = state["latestEvent"]
        writes = wait("Correlated Manager write", lambda: [e for e in lab.tap.since(published,"sync")
                    if e["request_id"]==ev["request_id"] and e["status"]=="APPLIED"])
        reads = [e for e in lab.tap.since(published,"fetch") if e["profile"]==after and e["status"]==200]
        check(reads, "No observed Manager fetch for target contract")
        op = selected(state)
        signatures = [(o["http_method"],o["gateway_path"]) for o in state["operations"]]
        check(len(signatures)==len(set(signatures)) and len(state["routes"])==1, "Duplicate configuration")
        if op:
            if self.first_id is None:
                self.first_id = op["id"]
            check(op["id"]==self.first_id,"Operation ID changed")
        write = writes[0]
        row.update(operation_id=op.get("id"),active=op.get("active",False),route_id=state["routes"][0]["id"],
                   version=ev["configuration_version"], previous_hash=cfg["service"]["openapi_hash"],
                   semantic_hash=state["service"]["openapi_hash"],request_id=ev["request_id"], operation_count=len(signatures),
                   manager_fetch_ms=ms(reads[0]["start_ns"],t0), sync_start_ms=ms(write["start_ns"],t0),
                   sync_response_ms=ms(write["end_ns"],t0), sync_rtt_ms=ms(write["end_ns"],write["start_ns"]))
        check(row["version"]>row["before_version"], "Revision did not advance")
        return state

    def timing(self):
        for rep in range(1,self.lab.args.timing+1):
            for label,before,after in [("add","default","expanded"),("restrict","expanded","authenticated"),
                                       ("remove","authenticated","default")]:
                self.trial("4.3",label,rep,lambda row:self.transition(row,before,after))

    def intervals(self):
        # A stable precondition then one intervention inside each fixed length window.
        delays = list(self.lab.args.intervals)
        self.rng.shuffle(delays)
        for delay in delays:
            self.lab.start_manager(delay)
            for rep in range(1,self.lab.args.windows+1):
                self.align("expanded")
                def window(row):
                    begin=now()
                    stop=begin+int(self.lab.args.window_s*1e9)
                    self.transition(row,"expanded","authenticated")
                    time.sleep(max(0,(stop-now())/1e9))
                    starts=[e["start_ns"] for e in self.lab.tap.since(begin,"cycle") if e["start_ns"]<stop]
                    row.update(window_s=self.lab.args.window_s,window_start_ns=begin,window_end_ns=stop,
                               cycles=len(starts),cycle_offsets_ms=[ms(s,begin) for s in starts],
                               intervention_in_window=begin<=row["confirmed_ns"]<stop,
                               convergence_in_window=row["confirmed_ns"]+row["latency_ms"]*1e6<=stop)
                    check(row["intervention_in_window"], "Intervention outside count window")
                self.trial("4.6","fixed_window",rep,window)
