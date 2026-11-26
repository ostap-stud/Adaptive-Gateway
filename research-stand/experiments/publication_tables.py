"""Assemble manuscript tables from two audited runs without pooling their 4.6 rows."""
import argparse
import json
from pathlib import Path

from audit import audit
from summarize import describe, write_csv

LABEL = {"restrict":"Обмеження доступу", "relax":"Відкриття доступу", "add":"Додавання",
         "remove":"Видалення", "permission":"Декларативний дозвіл", "replay":"Повтор PUT",
         "version_conflict":"Конфлікт версії", "override":"Політика ADMIN",
         "openapi_outage":"Недоступність OpenAPI", "registry_outage":"Зупинка Eureka",
         "gateway_outage":"Зупинка Gateway"}


def load_run(path):
    audit(path)
    cfg = json.loads((path/"conditions.json").read_text(encoding="utf-8"))
    rows = [json.loads(line) for line in (path/"raw.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    return cfg, rows


def uk(v):
    if v is None:
        return "—"
    if isinstance(v, float):
        return f"{v:.2f}".replace(".", ",")
    return str(v)


def table(columns, records):
    return ["| " + " | ".join(columns) + " |", "|" + "|".join("---" for _ in columns) + "|",
            *["| " + " | ".join(map(uk, record)) + " |" for record in records], ""]


def make(original, repeat, output):
    a, old = load_run(original)
    b, fresh = load_run(repeat)
    if original.resolve() == repeat.resolve() or b["parameters"]["sections"] != ["4.6"]:
        raise ValueError("Supply the full original run and a separate 4.6-only run")
    if b["parameters"]["windows"] != 10 or sorted(b["parameters"]["intervals"]) != [1,3,5,10]:
        raise ValueError("The selected follow-up must have ten windows at 1, 3, 5 and 10 s")
    if not {"4.2","4.3","4.4","4.5","4.6"} <= {r["section"] for r in old}:
        raise ValueError("The first directory must contain the original five sections")
    if not all(r["intervention_in_window"] for r in fresh):
        raise ValueError("Intervention outside the count window")
    rows = [dict(r,source_run=original.name) for r in old if r["section"] != "4.6"]
    rows.extend(dict(r,source_run=repeat.name) for r in fresh)
    lines = ["# Зведені таблиці для розділу 4", "",
             f"**Прогін A** · `{original.name}` · коміт `{a['commit'][:7]}`. З {len(old)} повторів у ці таблиці увійшли розділи 4.2–4.5.",
             "",f"**Прогін B** · `{repeat.name}` · коміт `{b['commit'][:7]}`. Усі {len(fresh)} повторів належать до 4.6. "
             f"Попередні {sum(r['section']=='4.6' for r in old)} вікон 4.6 збережені в архіві A.", "",
             "SD — вибіркове стандартне відхилення. P95 — порядкова статистика з рангом ⌈0,95n⌉ для n ≥ 20. "
             "Розрахунки виконано до округлення. [Значення у CSV](publication-tables.csv).",""]
    csv_rows = []
    def blocks(title):
        lines.extend(["## "+title,""])
    def outcome(section):
        groups = sorted({r["scenario"] for r in rows if r["section"]==section})
        vals=[]
        for k in groups:
            rr=[r for r in rows if r["section"]==section and r["scenario"]==k]
            passed=sum(r["ok"] for r in rr)
            vals.append([LABEL[k], len(rr), passed])
            csv_rows.append(dict(source_run=original.name,section=section,scenario=k,
                                 metric="passed_trials",n=len(rr),passed=passed))
        lines.extend(table(["Сценарій","Повторів","Пройшло"], vals))
    fields=("n","mean","median","min","max","sd","p95")
    names=["n","Середнє","Медіана","Мін.","Макс.","SD","P95"]
    def aggregate(rr, metric, selectors, source, values=None):
        s=describe(values if values is not None else [r[metric] for r in rr])
        csv_rows.append(dict(source_run=source,**selectors,metric=metric,**s))
        return [s[k] for k in fields]

    blocks("4.2. Коректність · A")
    outcome("4.2")
    blocks("4.3. Затримка адаптації, мс · A")
    vals=[]
    for scenario in ["add","restrict","remove"]:
        rr=[r for r in rows if r["section"]=="4.3" and r["scenario"]==scenario]
        vals.append([LABEL[scenario], *aggregate(rr,"latency_ms",dict(section="4.3",scenario=scenario),original.name)])
    lines.extend(table(["Зміна",*names],vals))
    blocks("4.4. Час до правильної відповіді Gateway, мс · A")
    vals, paired = [], []
    for scenario in ["restrict","relax"]:
        pairs = {}
        for arm in ["manual","adaptive"]:
            rr=[r for r in rows if r["section"]=="4.4" and r["scenario"]==scenario and r["arm"]==arm]
            pairs[arm]={r["repeat"]:r["latency_ms"] for r in rr}
            actions={r["admin_actions"] for r in rr}
            if len(actions)!=1:
                raise ValueError("Administrative action counts differ within a group")
            vals.append([LABEL[scenario],"REST" if arm=="manual" else "Manager",next(iter(actions)),
                         *aggregate(rr,"latency_ms",dict(section="4.4",scenario=scenario,arm=arm,
                             admin_writes_per_trial=next(iter(actions))),original.name)])
        if pairs["manual"].keys()!=pairs["adaptive"].keys():
            raise ValueError("Unmatched trial in 4.4")
        differences=[pairs["adaptive"][i]-v for i,v in pairs["manual"].items()]
        paired.append([LABEL[scenario], *aggregate([],"adaptive_minus_manual_ms",dict(section="4.4",scenario=scenario),
                                                   original.name, differences)])
    lines.extend(table(["Зміна","Спосіб","Записів адміністратора",*names],vals))
    lines.extend(["**Парна різниця, мс.** Для того самого сценарію та номера повтору обчислено час Manager мінус час REST.",""])
    lines.extend(table(["Зміна",*names],paired))
    blocks("4.5. Узгодженість та відмови · A")
    outcome("4.5")
    for metric,title in [("latency_ms","Затримка, мс"),("cycles","Кількість початих циклів за 24 с")]:
        blocks("4.6. "+title+" · B")
        vals=[]
        for delay in sorted({r["manager_delay_s"] for r in fresh}):
            rr=[r for r in fresh if r["manager_delay_s"]==delay]
            if {r["window_s"] for r in rr}!={24}:
                raise ValueError("24-second windows required for these publication tables")
            vals.append([delay, *aggregate(rr,metric,dict(section="4.6",scenario="fixed_window",
                                                          manager_delay_s=delay,window_s=24,convergence_in_window_n=sum(r["convergence_in_window"] for r in rr)),repeat.name)])
        lines.extend(table(["Пауза, с",*names],vals))
    within = sum(r["convergence_in_window"] for r in fresh)
    lines.extend([f"У прогоні B правильна пара відповідей зафіксована в межах 24-секундного вікна у {within} з {len(fresh)} повторів.", ""])
    lines.extend([f"[Первинні вимірювання A](results/{original.name}/raw.jsonl) · "
                  f"[Первинні вимірювання B](results/{repeat.name}/raw.jsonl) · "
                  f"[Часові позначки та всі статистики A](results/{original.name}/statistics.csv) · "
                  f"[Усі статистики B](results/{repeat.name}/statistics.csv)",""])
    output.mkdir(parents=True,exist_ok=True)
    (output/"publication-tables.uk.md").write_text("\n".join(lines),encoding="utf-8")
    write_csv(output/"publication-tables.csv",csv_rows)


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("original",type=Path)
    p.add_argument("repeat",type=Path)
    p.add_argument("--output",type=Path,default=Path(__file__).resolve().parent)
    args=p.parse_args()
    make(args.original,args.repeat,args.output)
