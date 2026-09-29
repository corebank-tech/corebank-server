"""아이콘 노드형 인프라 다이어그램 렌더러.

좌표를 데이터로 적고 SVG를 직접 만든다. 노드는 "아이콘 위 · 이름과 부제 아래"로 그리고,
계층 띠(Tier)와 AWS식 그룹 상자를 더한다.

아이콘 출처 (icons/ 아래 라이선스 파일)
  aws-icons@3.3.0 (MIT, AWS 공식 Architecture Icons 기반)
  simple-icons@13 (CC0) — 브랜드 로고, 파일명 앞에 si- 를 붙였다
"""
import html
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")
FONT = ("ui-sans-serif,-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,"
        "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif")

# simple-icons 브랜드 색
BRAND = {"cloudflare": "#F38020", "react": "#61DAFB", "spring": "#6DB33F", "redis": "#FF4438",
         "mysql": "#4479A1", "prometheus": "#E6522C", "grafana": "#F46800",
         "githubactions": "#2088FF", "docker": "#2496ED", "nginx": "#009639", "fastapi": "#009688"}

C = dict(text="#1f2328", muted="#59636e", page="#ffffff",
         aws="#232F3E", vpc="#8C4FFF", az="#007CBC", asg="#ED7100", subnet_pub="#248814",
         subnet_priv="#147EBA", vm="#6e7781",
         flow="#DD344C", data="#C925D1", ops="#0969da", neutral="#6e7781", sec="#bf3989")


def esc(s):
    return html.escape(str(s), quote=True)


def _inner(name):
    """아이콘 SVG 파일의 viewBox와 내부 마크업."""
    if name.startswith("si-"):
        raw = open(os.path.join(ICONS, name + ".svg"), encoding="utf-8").read()
        brand = name[3:]
        vb = re.search(r'viewBox="([^"]+)"', raw).group(1)
        body = re.sub(r"^.*?<svg[^>]*>|</svg>\s*$", "", raw, flags=re.S)
        body = re.sub(r"<title>.*?</title>", "", body)
        return vb, f'<g fill="{BRAND.get(brand, "#333")}">{body}</g>'
    raw = open(os.path.join(ICONS, name + ".svg"), encoding="utf-8").read()
    vb = re.search(r'viewBox="([^"]+)"', raw).group(1)
    body = re.sub(r"^.*?<svg[^>]*>|</svg>\s*$", "", raw, flags=re.S)
    body = re.sub(r"<title>.*?</title>", "", body)
    return vb, body


def icon(x, y, size, name):
    vb, body = _inner(name)
    return (f'<svg x="{x}" y="{y}" width="{size}" height="{size}" viewBox="{vb}" '
            f'overflow="visible">{body}</svg>')


def text(x, y, s, size=13, weight=400, fill=None, anchor="middle"):
    return (f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" '
            f'text-anchor="{anchor}" fill="{fill or C["text"]}">{esc(s)}</text>')


def render(out, *, w, h, title, aria, bands=(), groups=(), nodes=(), edges=(), notes=(), heading=None, tubes=(), legend=None, fs=1.0, isize=48):
    """fs: 글자 배율(기본 1). isize: 기본 아이콘 크기."""
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" '
         f'role="img" aria-label="{esc(aria)}">', f'<title>{esc(title)}</title>', '<defs>']
    for key in ("flow", "data", "ops", "neutral", "sec"):
        o.append(f'<marker id="m-{key}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
                 f'markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" '
                 f'fill="{C[key]}"/></marker>')
    o.append('</defs>')
    o.append(f'<rect width="{w}" height="{h}" fill="{C["page"]}"/>')
    if heading:
        o.append(text(30, 46 * fs, heading[0], 30 * fs, 800, anchor="start"))
        if len(heading) > 1:
            o.append(text(30, 60, heading[1], 13, 400, C["muted"], "start"))

    # 계층 띠: (y, h, 라벨, 부제, 색) — 부제는 \n으로 줄바꿈
    for by, bh, label, sub, fill in bands:
        o.append(f'<rect x="16" y="{by}" width="{w-32}" height="{bh}" rx="10" fill="{fill}"/>')
        o.append(text(32, by + 26 * fs, label, 13.5 * fs, 700, C["text"], "start"))
        for i, line in enumerate((sub or "").split("\n") if sub else []):
            o.append(text(32, by + (46 + i * 18) * fs, line, 11.5 * fs, 400, C["muted"], "start"))

    # 그룹 상자: (x, y, w, h, 라벨, 색키, 점선, 아이콘[, 라벨 위치 tl·tr·bl])
    for g in groups:
        gx, gy, gw, gh, label, key, dashed, gicon = g[:8]
        pos = g[8] if len(g) > 8 else "tl"
        col = C[key]
        dash = ' stroke-dasharray="6 4"' if dashed else ''
        o.append(f'<rect x="{gx}" y="{gy}" width="{gw}" height="{gh}" rx="4" fill="none" '
                 f'stroke="{col}" stroke-width="1.6"{dash}/>')
        if pos == "bl":
            tx = gx + 10
            if gicon:
                o.append(icon(gx, gy + gh - 26 * fs, 26 * fs, gicon))
                tx = gx + 32 * fs
            o.append(text(tx, gy + gh - 9 * fs, label, 12.5 * fs, 600, col, "start"))
            continue
        if pos == "tr":
            o.append(text(gx + gw - 10, gy + 18 * fs, label, 12.5 * fs, 600, col, "end"))
            continue
        tx = gx + 10
        if gicon:
            o.append(icon(gx, gy, 26 * fs, gicon))
            tx = gx + 32 * fs
        o.append(text(tx, gy + 18 * fs, label, 12.5 * fs, 600, col, "start"))

    # 터널(관): (x1, x2, cy, 라벨) — 두 게이트웨이 사이를 잇는 IPsec 터널
    for x1, x2, cy, label in tubes:
        th = 26 * min(fs, 1.3)
        o.append(f'<rect x="{x1}" y="{cy-th/2}" width="{x2-x1}" height="{th}" rx="{th/2}" fill="#f3eefe" '
                 f'stroke="{C["vpc"]}" stroke-width="2"/>')
        o.append(f'<line x1="{x1+10}" y1="{cy}" x2="{x2-10}" y2="{cy}" stroke="{C["vpc"]}" '
                 f'stroke-width="1.2" stroke-dasharray="4 4"/>')
        lines = label.split("\n") if label else []
        for i, line in enumerate(lines):
            o.append(text((x1 + x2) / 2, cy - th / 2 - 8 * fs - (len(lines) - 1 - i) * 15 * fs, line, 12 * fs, 700, C["vpc"]))

    # 연결선: (경로, 색키, 라벨, 라벨좌표, 정렬, 화살표 여부, 점선)
    for d, key, label, lxy, anchor, arrow, dashed in edges:
        col = C[key]
        mk = f' marker-end="url(#m-{key})"' if arrow else ''
        dash = ' stroke-dasharray="6 4"' if dashed else ''
        if d:  # d가 None이면 라벨만 둔다(한 선에 라벨 두 줄)
            o.append(f'<path d="{d}" fill="none" stroke="{col}" stroke-width="2"{mk}{dash}/>')
        if label:
            lx, ly = lxy
            tw = (sum(12 if ord(ch) > 0x2E80 else 6.6 for ch in label) + 10) * fs
            bx = lx - tw / 2 if anchor == "middle" else (lx - 4 if anchor == "start" else lx - tw + 4)
            o.append(f'<rect x="{bx}" y="{ly-13*fs}" width="{tw}" height="{18*fs}" rx="3" fill="#ffffff" opacity="0.92"/>')
            o.append(text(lx, ly, label, 12 * fs, 600, col, anchor))

    # 노드: (cx, cy, 아이콘, 이름, 부제, 배지 아이콘, 크기) — @dns·@ext·@vm은 글자 상자
    for n in nodes:
        cx, cy, ic, label, sub = n[:5]
        badge = n[5] if len(n) > 5 else None
        size = n[6] if len(n) > 6 else isize
        half = size / 2
        if ic == "@dns":
            o.append(f'<rect x="{cx-half}" y="{cy-half}" width="{size}" height="{size}" rx="8" fill="#57606a"/>')
            o.append(text(cx, cy + 5 * fs, "DNS", 14 * fs, 700, "#ffffff"))
        elif ic == "@ext":
            o.append(f'<rect x="{cx-half}" y="{cy-half}" width="{size}" height="{size}" rx="8" fill="#8250df"/>')
            o.append(text(cx, cy + 5 * fs, "대외", 13 * fs, 700, "#ffffff"))
        elif ic == "@vm":
            o.append(f'<rect x="{cx-half}" y="{cy-half}" width="{size}" height="{size}" rx="8" fill="#6e7781"/>')
            o.append(text(cx, cy + 5 * fs, "VM", 15 * fs, 700, "#ffffff"))
        else:
            o.append(icon(cx - half, cy - half, size, ic))
        if badge:
            r = 13 * size / 48
            o.append(f'<circle cx="{cx+half}" cy="{cy-half}" r="{r}" fill="#ffffff" stroke="#d1d9e0"/>')
            o.append(icon(cx + half - r * 0.7, cy - half - r * 0.7, r * 1.4, badge))
        o.append(text(cx, cy + half + 18 * fs, label, 13.5 * fs, 700))
        if sub:
            for i, line in enumerate(sub.split("\n")):
                o.append(text(cx, cy + half + 36 * fs + i * 15 * fs, line, 11.5 * fs, 400, C["muted"]))

    # 범례: (x, y, [(색키, 점선, 설명), ...])
    if legend:
        lx, ly, items = legend
        bw = 360 * fs
        o.append(f'<rect x="{lx}" y="{ly}" width="{bw}" height="{(30 + 24 * len(items)) * fs}" rx="8" '
                 f'fill="#ffffff" stroke="#d1d9e0"/>')
        o.append(text(lx + 12, ly + 20 * fs, "범례", 12.5 * fs, 700, C["text"], "start"))
        for i, (key, dashed, desc) in enumerate(items):
            yy = ly + (42 + i * 24) * fs
            dash = ' stroke-dasharray="6 4"' if dashed else ''
            o.append(f'<path d="M{lx+14} {yy-4*fs} H {lx+64*fs}" stroke="{C[key]}" stroke-width="2" '
                     f'marker-end="url(#m-{key})"{dash}/>')
            o.append(text(lx + 76 * fs, yy, desc, 12 * fs, 400, C["text"], "start"))

    for nx, ny, s in notes:
        o.append(text(nx, ny, s, 12, 400, C["muted"], "start"))
    o.append('</svg>')
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(o))
    return out
