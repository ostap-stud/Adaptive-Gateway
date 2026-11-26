"""Render compact Ukrainian tables from the same computed statistics as the CSV."""
from statistics import median

NAMES={"add":"Додавання", "restrict":"Обмеження доступу", "relax":"Відкриття доступу",
       "remove":"Видалення","permission":"Декларативний дозвіл","replay":"Повтор того самого PUT",
       "version_conflict":"Застаріла версія","override":"Політика ADMIN","openapi_outage":"Недоступність OpenAPI",
       "registry_outage":"Недоступність Eureka для Manager","gateway_outage":"Зупинка й відновлення Gateway"}


def number(value):
    if value is None:
        return "—"
    return f"{value:.2f}".replace(".",",") if isinstance(value,float) else str(value)


def table(columns,body):
    result=["| "+" | ".join(columns)+" |","|"+"|".join("---" for _ in columns)+"|"]
    result.extend("| "+" | ".join(number(v) for v in row)+" |" for row in body)
    return result


def tables(out,stats,counts,diffs):
    rows=["# Результати прогону "+out.name,"",
          "Тривалості подано в мілісекундах. SD — вибіркове стандартне відхилення. P95 подано для груп із щонайменше 20 успішних повторів.",
          "","[Сирі повтори](raw.csv) · [Умови запуску](conditions.json) · [Статистики всіх метрик](statistics.csv)",""]
    def add(title,cols,data):
        if data:
            rows.extend(["## "+title,"",*table(cols,data),""])
    fields=["n","mean","median","min","max","sd","p95"]
    cols=["n","Середнє","Медіана","Мін.","Макс.","SD","P95"]
    add("4.2. Коректність",["Зміна","Виконано","Пройшло","Помилки"],
        [[NAMES[r["scenario"]],r["attempted"],r["passed"],r["failed"]] for r in counts if r["section"]=="4.2"])
    add("4.3. Час до правильної відповіді Gateway",["Зміна",*cols],
        [[NAMES[r["scenario"]],*[r[k] for k in fields]] for r in stats if r["section"]=="4.3" and r["metric"]=="latency_ms"])
    marks={"manager_fetch_ms":"Перший запит документа","sync_start_ms":"Початок PUT",
           "sync_response_ms":"Відповідь Auth про публікацію","sync_rtt_ms":"Тривалість PUT"}
    add("4.3. Часові позначки та тривалість HTTP-синхронізації",["Зміна","Подія або інтервал","n","Медіана, мс"],
        [[NAMES[r["scenario"]],marks[r["metric"]],r["n"],r["median"]] for r in stats if r["section"]=="4.3" and r["metric"] in marks])
    actions={(r["scenario"],r["arm"]):r["mean"] for r in stats if r["section"]=="4.4" and r["metric"]=="admin_actions"}
    add("4.4. Однаковий результат керування доступом",["Зміна","Спосіб","Записів REST адміністратора на повтор",*cols],
        [[NAMES[r["scenario"]],"REST" if r["arm"]=="manual" else "Manager",actions[(r["scenario"],r["arm"])],
          *[r[k] for k in fields]] for r in stats if r["section"]=="4.4" and r["metric"]=="latency_ms"])
    add("4.4. Парні різниці часу",["Зміна","Пар","Медіана (Manager − REST), мс"],
        [[NAMES[key],len(vals),median(vals)] for key in sorted({r["scenario"] for r in diffs})
         if (vals:=[r["adaptive_minus_manual_ms"] for r in diffs if r["scenario"]==key])])
    add("4.5. Узгодженість і відновлення",["Перевірка","Виконано","Пройшло","Помилки"],
        [[NAMES[r["scenario"]],r["attempted"],r["passed"],r["failed"]] for r in counts if r["section"]=="4.5"])
    for metric,title in [("latency_ms","Затримка, мс"),("cycles","Кількість циклів за однакове вікно")]:
        add("4.6. "+title,["Пауза Manager, с",*cols],
            [[r["manager_delay_s"],*[r[k] for k in fields]] for r in stats if r["section"]=="4.6" and r["metric"]==metric])
    (out/"tables.uk.md").write_text("\n".join(rows)+"\n",encoding="utf-8")
