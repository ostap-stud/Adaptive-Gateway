"""Per-run chapter 4 figures, with plotted statistics derived directly from raw.jsonl."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
from statistics import mean, median, stdev

from reportlab.graphics import renderPDF, renderSVG
from reportlab.graphics.shapes import Circle, Drawing, Line, PolyLine, Rect, String
from reportlab.lib.colors import HexColor, white
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import pypdfium2 as pdfium

INK = HexColor("#202020")
GRID = HexColor("#d4d4d4")
LIGHT = HexColor("#dedede")
DARK = HexColor("#535353")
WIDTH, HEIGHT = 450, 240
LEFT, RIGHT, BOTTOM, TOP = 44, 438, 40, 205


def register_font():
    if "StudySans" in pdfmetrics.getRegisteredFontNames():
        return
    candidates = (Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts/arial.ttf",
                  Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
    for path in candidates:
        if path.is_file():
            pdfmetrics.registerFont(TTFont("StudySans", str(path)))
            return
    raise RuntimeError("Install Arial or DejaVu Sans for Ukrainian figure labels")


def quantile(values, p):
    """Linear interpolation at (n-1)*p in an ordered, nonempty sample (type 7)."""
    h = (len(values) - 1) * p
    i = math.floor(h)
    if i == len(values) - 1:
        return values[i]
    return values[i] + (h-i) * (values[i+1] - values[i])


def group(rows, metric, **selectors):
    values = sorted(row[metric] for row in rows
                    if all(row.get(k) == v for k, v in selectors.items()))
    if not values:
        return None
    q1, q3 = quantile(values, .25), quantile(values, .75)
    span = q3 - q1
    inside = [v for v in values if q1 - 1.5 * span <= v <= q3 + 1.5 * span]
    return {**selectors, "metric": metric, "n": len(values), "mean": mean(values),
            "sd": stdev(values) if len(values) > 1 else None, "min": values[0], "q1": q1,
            "median": median(values), "q3": q3, "max": values[-1],
            "whisker_low": min(inside), "whisker_high": max(inside),
            "outliers": [v for v in values if v < min(inside) or v > max(inside)]}


def label(d, x, y, text, anchor="start", size=10):
    d.add(String(x, y, text, fontName="StudySans", fontSize=size, fillColor=INK, textAnchor=anchor))


def line(d, x1, y1, x2, y2, width=.8, color=INK):
    d.add(Line(x1, y1, x2, y2, strokeColor=color, strokeWidth=width))


def frame(maximum, unit, integer=False):
    register_font()
    d = Drawing(WIDTH, HEIGHT)
    d.add(Rect(0, 0, WIDTH, HEIGHT, strokeColor=None, fillColor=white))
    rough = max(maximum / 5, .001)
    scale = 10 ** math.floor(math.log10(rough))
    step = next(s * scale for s in (1, 2, 2.5, 5, 10) if s * scale >= rough)
    if integer:
        step = max(1, math.ceil(step))
    ticks = max(1, math.ceil(maximum / step))
    upper = ticks * step

    def y(v):
        return BOTTOM + (TOP - BOTTOM) * v / upper
    for i in range(ticks+1):
        v = i * step
        line(d, LEFT, y(v), RIGHT, y(v), .42, GRID)
        label(d, LEFT-7, y(v)-3, f"{v:g}".replace(".", ","), "end", 9)
    line(d, LEFT, BOTTOM, LEFT, TOP)
    line(d, LEFT, BOTTOM, RIGHT, BOTTOM)
    label(d, LEFT, TOP+14, unit)
    return d, y


def whisker(d, x, lo, hi, cap=5):
    line(d, x, lo, x, hi)
    for yy in (lo, hi):
        line(d, x-cap, yy, x+cap, yy)


def boxplot(groups):
    d, y = frame(max(g["max"] for g in groups), "Затримка, мс")
    for i, (g, name) in enumerate(zip(groups, ("Додавання", "Обмеження доступу", "Видалення"))):
        x = LEFT + (i+.5) * (RIGHT-LEFT) / 3
        whisker(d, x, y(g["whisker_low"]), y(g["whisker_high"]), 9)
        d.add(Rect(x-19, y(g["q1"]), 38, y(g["q3"])-y(g["q1"]),
                   fillColor=LIGHT, strokeColor=INK, strokeWidth=1))
        line(d, x-19, y(g["median"]), x+19, y(g["median"]), 1.8)
        for v in g["outliers"]:
            d.add(Circle(x, y(v), 2.5, fillColor=white, strokeColor=INK))
        label(d, x, BOTTOM-17, name, "middle")
    return d


def bars(groups):
    d, y = frame(max(g["q3"] for g in groups), "Затримка, мс")
    for left, title, fill in ((208, "REST", LIGHT), (280, "Adaptive Manager", DARK)):
        d.add(Rect(left, TOP+13, 11, 7, strokeColor=INK, fillColor=fill, strokeWidth=.65))
        label(d, left+15, TOP+12, title, size=9)
    for i, g in enumerate(groups):
        arm = i % 2
        center = LEFT + (i//2 + .5) * (RIGHT-LEFT) / 2
        x = center + (-22 if arm == 0 else 22)
        d.add(Rect(x-17, BOTTOM, 34, y(g["median"])-BOTTOM, fillColor=(LIGHT,DARK)[arm],
                   strokeColor=INK, strokeWidth=.7))
        # Narrow white underlay keeps the lower cap readable within the darker bar.
        line(d, x, y(g["q1"]), x, y(g["q3"]), 2.2, white)
        for v in (g["q1"], g["q3"]):
            line(d, x-5, y(v), x+5, y(v), 2.2, white)
        whisker(d, x, y(g["q1"]), y(g["q3"]))
        if arm == 0:
            label(d, center, BOTTOM-17, ("Обмеження доступу","Відкриття доступу")[i//2], "middle")
    return d


def trend(groups, metric):
    cycles = metric == "cycles"
    low, high = ("min", "max") if cycles else ("q1", "q3")
    window = groups[0]["window_s"]
    d, y = frame(max(g[high] for g in groups),
                 f"Кількість циклів за {window:g} с" if cycles else "Затримка, мс", cycles)
    delays = [g["manager_delay_s"] for g in groups]
    def x(value):
        if delays[-1] == delays[0]:
            return (LEFT+RIGHT)/2
        return LEFT+12+(RIGHT-LEFT-24)*(value-delays[0])/(delays[-1]-delays[0])
    coords = [(x(v),y(g["median"])) for v,g in zip(delays,groups)]
    if len(coords) > 1:
        d.add(PolyLine([p for point in coords for p in point], strokeColor=DARK, strokeWidth=1, fillColor=None))
    for g, (px,py) in zip(groups,coords):
        whisker(d, px, y(g[low]), y(g[high]), 4)
        d.add(Circle(px, py, 3.2, strokeColor=INK, fillColor=white, strokeWidth=1.25))
        line(d, px, BOTTOM, px, BOTTOM-3)
        label(d, px, BOTTOM-14, f'{g["manager_delay_s"]:g}'.replace(".", ","), "middle", 9)
    label(d, (LEFT+RIGHT)/2, 7, "Пауза Adaptive Manager, с", "middle")
    return d


def save(folder, stem, drawing, groups, run_id):
    folder.mkdir(exist_ok=True, parents=True)
    svg = renderSVG.drawToString(drawing).replace("StudySans", "Arial, sans-serif")
    (folder / (stem+".svg")).write_text(svg, encoding="utf-8")
    doc = pdfium.PdfDocument(renderPDF.drawToString(drawing))
    try:
        page = doc[0]
        bitmap = page.render(scale=300/72)
        try:
            bitmap.to_pil().save(folder / (stem+".png"), dpi=(300,300))
        finally:
            bitmap.close()
            page.close()
    finally:
        doc.close()
    with (folder / (stem+".csv")).open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=["run_id", *groups[0]])
        writer.writeheader()
        for g in groups:
            writer.writerow({"run_id":run_id, **g, "outliers":json.dumps(g["outliers"])})


def render(run_id, rows, folder, sections):
    good = [r for r in rows if r["ok"]]
    if "4.3" in sections:
        gs = [group(good, "latency_ms", section="4.3", scenario=k) for k in ("add","restrict","remove")]
        if all(gs):
            save(folder,"4-3-transitions",boxplot(gs),gs,run_id)
    if "4.4" in sections:
        gs = [group(good, "latency_ms", section="4.4", scenario=k, arm=a)
              for k in ("restrict","relax") for a in ("manual","adaptive")]
        if all(gs):
            save(folder,"4-4-management",bars(gs),gs,run_id)
    if "4.6" in sections:
        eligible = [r for r in good if r["section"] == "4.6"]
        windows = {r["window_s"] for r in eligible}
        if len(windows) > 1:
            raise ValueError("Cannot combine different window lengths")
        delays = sorted({r["manager_delay_s"] for r in eligible})
        for metric, stem in (("latency_ms","4-6-delay"), ("cycles","4-6-cycles")):
            gs = [group(eligible, metric, section="4.6", manager_delay_s=v, window_s=next(iter(windows))) for v in delays]
            if gs:
                save(folder,stem,trend(gs,metric),gs,run_id)


def figures(out, rows):
    """Entry point used by summarize.py for a single run."""
    render(out.name, rows, out/"figures", {"4.3", "4.4", "4.6"})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--sections", nargs="+", choices=["4.3","4.4","4.6"], default=["4.3","4.4","4.6"])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    rows = [json.loads(line) for line in (args.directory/"raw.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    render(args.directory.name, rows, args.output or args.directory/"figures", args.sections)
