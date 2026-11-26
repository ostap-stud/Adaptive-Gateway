"""Publication figures. Points show individual trials; horizontal lines show group medians."""
from math import ceil, floor, log10
from pathlib import Path
from statistics import median
import os

from reportlab.graphics import renderPDF, renderSVG
from reportlab.graphics.shapes import Circle, Drawing, Line, Rect, String
from reportlab.lib.colors import Color, HexColor, white
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import pypdfium2 as pdfium

INK=HexColor("#152b40")
BLUE=HexColor("#21618a")
ORANGE=HexColor("#a1441d")
GRID=HexColor("#d6dfe5")


def font():
    for p in [Path(os.environ.get("WINDIR","C:/Windows"))/"Fonts/arial.ttf",
              Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")]:
        if p.is_file():
            pdfmetrics.registerFont(TTFont("StudySans",str(p)))
            return "StudySans"
    raise RuntimeError("Install Arial or DejaVu Sans for Ukrainian figure labels")


def point_chart(out,filename,title,units,groups,labels,colors,subtitle):
    if not all(groups):
        return
    family=font()
    w,h=680,318
    d=Drawing(w,h)
    d.add(Rect(0,0,w,h,fillColor=white,strokeColor=None))
    def txt(x,y,value,size=10,anchor="start",color=INK):
        d.add(String(x,y,value,fontName=family,fontSize=size,fillColor=color,textAnchor=anchor))
    txt(20,h-26,title,16)
    txt(20,h-46,subtitle,10)
    left,right,bottom,top=69,w-22,52,h-85
    maximum=max(max(vs) for vs in groups)
    rough=max(maximum/5,0.01)
    scale=10**floor(log10(rough))
    step=next(v*scale for v in (1,2,2.5,5,10) if v*scale>=rough)
    upper=ceil(max(maximum,.01)/step)*step
    if units=="Циклів" and step<1:
        step=1
        upper=max(1,ceil(maximum))
    def y(v):
        return bottom+(top-bottom)*v/upper
    txt(left,top+12,units,10)
    for i in range(round(upper/step)+1):
        val=i*step
        d.add(Line(left,y(val),right,y(val),strokeColor=GRID,strokeWidth=.6))
        txt(left-8,y(val)-3,f"{val:g}".replace(".",","),9,"end")
    slot=(right-left)/len(groups)
    for i,values in enumerate(groups):
        cx=left+(i+.5)*slot
        color=colors[i]
        for j,v in enumerate(values):
            # Deterministic horizontal separation; x offsets have no numerical meaning.
            delta=0 if len(values)==1 else ((j*0.61803398875)%1-.5)*min(55,slot*.45)
            d.add(Circle(cx+delta,y(v),2.6,fillColor=white,strokeColor=color,strokeWidth=1))
        yy=y(median(values))
        d.add(Line(cx-slot*.23,yy,cx+slot*.23,yy,strokeColor=color,strokeWidth=2))
        txt(cx,bottom-18,labels[i],9,"middle")
        txt(cx,bottom-33,f"n = {len(values)}",8,"middle")
    # Render SVG with a system font name so the figure is portable between applications.
    svg=renderSVG.drawToString(d).replace("StudySans","Arial, sans-serif")
    (out/(filename+".svg")).write_text(svg,encoding="utf-8")
    pdf=pdfium.PdfDocument(renderPDF.drawToString(d))
    try:
        page=pdf[0]
        bitmap=page.render(scale=3)
        bitmap.to_pil().save(out/(filename+".png"),dpi=(216,216))
        bitmap.close()
        page.close()
    finally:
        pdf.close()


def figures(out,rows):
    out=out/"figures"
    out.mkdir(exist_ok=True)
    rows=[r for r in rows if r["ok"]]
    def vals(section,metric,**filters):
        return [r[metric] for r in rows if r["section"]==section and metric in r and
                all(r.get(k)==v for k,v in filters.items())]
    keys=["add","restrict","remove"]
    point_chart(out,"4-3-transitions","Час адаптації за типом зміни контракту","мс",
                [vals("4.3","latency_ms",scenario=k) for k in keys],["Додавання","Обмеження доступу","Видалення"],
                [BLUE]*3,"Від підтвердження OpenAPI до правильної пари відповідей Gateway. Риска — медіана.")
    delays=sorted({r["manager_delay_s"] for r in rows if r["section"]=="4.6"})
    if delays:
        for metric,units,name,title in [("latency_ms","мс","delay","Затримка зміни доступу"),
                                       ("cycles","Циклів","cycles","Кількість циклів спостереження")]:
            window=next(r["window_s"] for r in rows if r["section"]=="4.6")
            point_chart(out,"4-6-"+name,title+" за різних інтервалів Manager",units,
                        [vals("4.6",metric,manager_delay_s=v) for v in delays],
                        [f"{v:g} с" for v in delays],[BLUE]*len(delays),
                        f"Налаштована пауза між циклами. Вікно {window:g} с, одна зміна PUBLIC → AUTHENTICATED. Риска — медіана.")
    point_chart(out,"4-4-management","Час від публікації контракту до зміни доступу","мс",
                [vals("4.4","latency_ms",scenario=k,arm=a) for k in ["restrict","relax"] for a in ["manual","adaptive"]],
                ["Обмеження · REST","Обмеження · Manager","Відкриття · REST","Відкриття · Manager"],
                [ORANGE,BLUE,ORANGE,BLUE],"REST — підготовлений адміністративний запит. Риска — медіана.")
