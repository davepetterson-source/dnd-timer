import math, re, sys

CX, CY = 195, 174          # circle centre (preview px)
RING = 132                 # outer ring radius
AVOID = 150           # tile inner edge follows this radius

# ---------------------------------------------------------------- tile paths
def qcurve(p1, c, p2, n=24):
    return [((1-t)**2*p1[0] + 2*(1-t)*t*c[0] + t*t*p2[0], (1-t)**2*p1[1] + 2*(1-t)*t*c[1] + t*t*p2[1]) for t in [i/n for i in range(n + 1)]]

def bow(p1, p2, k, away):
    mx, my = (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2
    nx, ny = -(p2[1] - p1[1]), (p2[0] - p1[0])
    ln = math.hypot(nx, ny); nx, ny = nx / ln, ny / ln
    if nx * (away[0] - mx) + ny * (away[1] - my) > 0: nx, ny = -nx, -ny
    return (mx + nx * k, my + ny * k)

def fillet(pts, vi, r):
    # replace the sharp vertex pts[vi] of a closed polyline with a circular arc of radius r
    m = len(pts); V = pts[vi]
    def pt_at(dirn, d):
        acc = 0; i = vi
        while True:
            j = (i + dirn) % m
            sl = math.dist(pts[i], pts[j])
            if acc + sl >= d:
                t = (d - acc) / sl
                return (pts[i][0] + (pts[j][0] - pts[i][0]) * t, pts[i][1] + (pts[j][1] - pts[i][1]) * t), j
            acc += sl; i = j
    a, _ = pt_at(-1, 3); c, _ = pt_at(1, 3)
    u1 = ((a[0] - V[0]) / 3, (a[1] - V[1]) / 3); u2 = ((c[0] - V[0]) / 3, (c[1] - V[1]) / 3)
    th = math.acos(max(-1, min(1, u1[0] * u2[0] + u1[1] * u2[1])))
    d = r / math.tan(th / 2)
    T1, i1 = pt_at(-1, d); T2, i2 = pt_at(1, d)
    bx, by = u1[0] + u2[0], u1[1] + u2[1]; bl = math.hypot(bx, by); bx, by = bx / bl, by / bl
    C = (V[0] + bx * r / math.sin(th / 2), V[1] + by * r / math.sin(th / 2))
    a1 = math.atan2(T1[1] - C[1], T1[0] - C[0]); a2 = math.atan2(T2[1] - C[1], T2[0] - C[0])
    da = (a2 - a1 + math.pi) % (2 * math.pi) - math.pi
    arc = [(C[0] + r * math.cos(a1 + da * k / 16), C[1] + r * math.sin(a1 + da * k / 16)) for k in range(17)]
    # rebuild: from i2 forward around to i1, then arc
    out = []
    i = i2
    while i != i1:
        out.append(pts[i]); i = (i + 1) % m
    out.append(pts[i1])
    return out + arc

def pick_shape(w, h, ya, rr=None, btop=12, bhyp=9, inset=0):
    from shapely.geometry import Polygon, box
    W, H, YA = w * 1.06, h * 1.32, ya * 1.12
    TL, R, BL = (0, 0), (W, YA), (0, H)
    top = qcurve(TL, bow(TL, R, btop, (W * .3, H * .5)), R, 40)
    hyp = qcurve(R, bow(R, BL, bhyp, (W * .3, H * .45)), BL, 60)
    tri = Polygon(top + hyp[1:])
    big = tri.buffer(-24, resolution=32).buffer(24, resolution=32)      # broad round end and top corner
    small = tri.buffer(-12, resolution=32).buffer(12, resolution=32)    # tighter point by the circle
    near_r = box(W * 0.55, -10, W + 10, YA + 0.35 * H)
    shape = big.union(small.intersection(near_r)).buffer(0.01).buffer(-0.01)
    shape = shape.simplify(0.05)
    pts = list(shape.exterior.coords)[:-1]
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    pts = [((p[0] - x0) * w / (x1 - x0), (p[1] - y0) * h / (y1 - y0)) for p in pts]
    if inset:
        pg = Polygon(pts).buffer(-inset, resolution=32)
        pts = list(pg.exterior.coords)[:-1]
    return pts

def poly_d(pts, fx):
    q = [fx(p) for p in pts]
    return 'M ' + ' L '.join('%.1f %.1f' % p for p in q) + ' Z'

def rounded(pts, corners):
    m = len(pts)
    seg = [math.dist(pts[i], pts[(i + 1) % m]) for i in range(m)]
    cum = [0]
    for i in range(m):
        cum.append(cum[-1] + seg[i])
    L = cum[-1]
    def at(s):
        s %= L
        for i in range(m):
            if cum[i + 1] >= s:
                t = (s - cum[i]) / seg[i] if seg[i] else 0
                a, b = pts[i], pts[(i + 1) % m]
                return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
    def near(i, j):
        d = abs(cum[i] - cum[j]); d = min(d, L - d)
        return d < corners[j]
    out = []
    first = True
    for i in range(m):
        if i in corners:
            r = corners[i]
            lo, hi = at(cum[i] - r), at(cum[i] + r)
            if first:
                out.append(('M', lo)); first = False
            else:
                out.append(('L', lo))
            out.append(('Q', pts[i], hi))
        elif not any(near(i, j) for j in corners):
            out.append(('M' if first else 'L', pts[i])); first = False
    return out

def to_d(cmds, fx):
    d = []
    for c in cmds:
        if c[0] == 'Q':
            a, b = fx(c[1]), fx(c[2])
            d.append('Q %.1f %.1f %.1f %.1f' % (a[0], a[1], b[0], b[1]))
        else:
            p = fx(c[1])
            d.append('%s %.1f %.1f' % (c[0], p[0], p[1]))
    return ' '.join(d) + ' Z'

def tile_paths(x0, y0, w, h, flipx, flipy, ya, xb, b=1.6):
    def fx(p):
        x, y = p
        return ((w - x) if flipx else x, (h - y) if flipy else y)
    outer = poly_d(pick_shape(w, h, ya), fx)
    inner = poly_d(pick_shape(w, h, ya, inset=b), fx)
    return outer, inner

def ring_gap(t):
    key, x0, y0, w, h, fxp, fyp = t[:7]
    ya = round((0.31 if t[11] == 'top' else 0.32) * h)
    best = 1e9
    for p in pick_shape(w, h, ya):
        x = x0 + ((w - p[0]) if fxp else p[0]); y = y0 + ((h - p[1]) if fyp else p[1])
        best = min(best, math.hypot(x - CX, y - CY) - RING)
    return best

# ---------------------------------------------------------------- icons
I_MED = '<svg aria-hidden="true" width="34" height="34" viewBox="0 0 24 24" fill="#f4c8ff"><circle cx="12" cy="4.4" r="2.5"></circle><path d="M10.3 8.2h3.4c.9 0 1.5.6 1.6 1.4l.5 5.2H8.2l.5-5.2c.1-.8.7-1.4 1.6-1.4z"></path><path d="M9.4 8.7C6.8 9.4 5.1 12 4.5 15.3M14.6 8.7c2.6.7 4.3 3.3 4.9 6.6" fill="none" stroke="#f4c8ff" stroke-width="1.8" stroke-linecap="round"></path><path fill-rule="evenodd" d="M2.4 17.6c0-1.6 1.5-2.4 3.4-2.4h12.4c1.9 0 3.4.8 3.4 2.4 0 1.7-1.6 2.6-3.6 2.6H6c-2 0-3.6-.9-3.6-2.6zM9.4 18.5l5.2-.9.3.7-5.2.9z"></path></svg>'
I_BED = '<svg aria-hidden="true" width="40" height="40" viewBox="0 0 24 24" fill="#8fd0ff"><rect x="2" y="3.5" width="2.6" height="17" rx="1.3"></rect><rect x="2" y="13.6" width="20" height="3" rx=".6"></rect><rect x="19.4" y="13.6" width="2.6" height="6.9" rx="1.2"></rect><circle cx="7.9" cy="10.3" r="2.4"></circle><path d="M11 7.6h7.2a3 3 0 0 1 3 3v2H11z"></path></svg>'
I_GEAR = '<svg aria-hidden="true" width="32" height="32" viewBox="0 0 24 24" fill="#ffffff"><path d="M19.4 13a7.6 7.6 0 0 0 0-2l2.1-1.6-2-3.5-2.5 1a7.4 7.4 0 0 0-1.7-1L15 3.3h-4l-.4 2.6a7.4 7.4 0 0 0-1.7 1l-2.5-1-2 3.5L6.6 11a7.6 7.6 0 0 0 0 2l-2.2 1.6 2 3.5 2.5-1a7.4 7.4 0 0 0 1.7 1l.4 2.6h4l.4-2.6a7.4 7.4 0 0 0 1.7-1l2.5 1 2-3.5zM13 15.2A3.2 3.2 0 1 1 13 8.8a3.2 3.2 0 0 1 0 6.4z" transform="translate(-1 0)"></path></svg>'
I_FACE = '<svg aria-hidden="true" width="34" height="34" viewBox="0 0 24 24" fill="#cdd7ff"><path d="M20.5 14.6A8.6 8.6 0 0 1 9.4 3.5a8.6 8.6 0 1 0 11.1 11.1z"></path></svg>'
I_SIL = '<svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon><line x1="23" y1="9" x2="17" y2="15"></line><line x1="17" y1="9" x2="23" y2="15"></line></svg>'
I_BELL = '<svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M10.3 20a2 2 0 0 0 3.4 0z"></path></svg>'
I_NOTE = '<svg aria-hidden="true" width="17" height="17" viewBox="0 0 24 24" fill="currentColor"><path d="M9 18V5l12-2v13" fill="none" stroke="currentColor" stroke-width="2"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>'

TILES = [
    # key, x0, y0, w, h, flipx, flipy, fill, border, glow, sub colour, where
    ('pMed', 6, 0, 106, 152, False, False,
     'linear-gradient(160deg, rgba(150,80,240,.55) 0%, rgba(90,40,190,.38) 55%, rgba(50,24,130,.30) 100%)',
     'linear-gradient(160deg, #f0a8ff, #b070ff 50%, #7d4dff)', 'rgba(190,100,255,.85)', '#cdbdff', 'top'),
    ('pKip', 278, 0, 106, 152, True, False,
     'linear-gradient(200deg, rgba(60,150,255,.50) 0%, rgba(30,90,220,.35) 55%, rgba(20,50,150,.28) 100%)',
     'linear-gradient(200deg, #9be3ff, #3fa8ff 50%, #2f6dff)', 'rgba(60,160,255,.85)', '#a9d8ff', 'top'),
    ('pSet', 6, 200, 108, 146, False, True,
     'linear-gradient(20deg, rgba(20,120,150,.45) 0%, rgba(16,80,120,.32) 55%, rgba(12,50,90,.26) 100%)',
     'linear-gradient(20deg, #7ff0ff, #2ec8e6 50%, #2b8fd8)', 'rgba(40,200,230,.75)', '#a4ecff', 'bottom'),
    ('pSleep', 276, 200, 108, 146, True, True,
     'linear-gradient(-20deg, rgba(70,90,190,.42) 0%, rgba(45,58,150,.30) 55%, rgba(30,36,110,.24) 100%)',
     'linear-gradient(-20deg, #c8d4ff, #8aa0ff 50%, #5b6fe8)', 'rgba(120,140,255,.65)', '#c2ccff', 'bottom'),
]

def edge_at(t, yd, hh):
    key, x0, y0, w, h, fxp, fyp = t[:7]
    where = t[11]
    ya, xb = (28, 43) if where == 'top' else (28, 40)
    cx = (w - (CX - x0)) if fxp else (CX - x0)
    cy = (h - (CY - y0)) if fyp else (CY - y0)
    pts, cor = poly_tl(w, h, ya, xb, cx, cy, RING + 8)
    curve = pts[2:-2]
    best = w
    for k in range(-4, 5):
        yy = yd + hh * k / 4
        yu = (h - yy) if fyp else yy
        if yu <= ya:
            e = w
        else:
            e = w
            for a, b in zip(curve, curve[1:]):
                if min(a[1], b[1]) <= yu <= max(a[1], b[1]) and a[1] != b[1]:
                    e = a[0] + (b[0] - a[0]) * (yu - a[1]) / (b[1] - a[1])
            if yu >= h: e = xb
        best = min(best, e)
    return best

def cx_at(t, yd, hh):
    w = t[3]
    u = edge_at(t, yd, hh) / 2 + 1
    return (w - u) if t[5] else u

def tile_html(t):
    key, x0, y0, w, h, fxp, fyp, fill, border, glow, subc, where = t
    ya = round((0.31 if where == 'top' else 0.32) * h)
    outer, inner = tile_paths(x0, y0, w, h, fxp, fyp, ya, 0)
    ring = 'path(evenodd, "%s %s")' % (outer, inner)
    icon = {'pMed': I_MED, 'pKip': I_BED, 'pSet': I_GEAR, 'pSleep': I_FACE}[key]
    def line(xf, yf, html, size=12, col=None, bold=False, hgt=16):
        cx = (1 - xf) * w if fxp else xf * w
        return ('<div style="position: absolute; left: %.1fpx; top: %.1fpx; width: 100px; height: %dpx; display: flex; align-items: center; justify-content: center; gap: 6px; white-space: nowrap; font-size: %spx; font-weight: %d; color: %s">%s</div>'
                % (cx - 50, yf * h - hgt / 2, hgt, size, 700 if bold else 400, col or subc, html))
    mode = '<sc-if value="{{%s_isSilent}}" hint-placeholder-val="{{ false }}"><span>Silent</span></sc-if><sc-if value="{{%s_isDnd}}" hint-placeholder-val="{{ false }}"><span>DND</span></sc-if>' % (key, key)
    note = '<sc-if value="{{%s_hasMusic}}" hint-placeholder-val="{{ false }}"><span style="display: flex; color: #ffffff">%s</span></sc-if>' % (key, I_NOTE)
    if key == 'pSet':
        rows = (line(.33, .33, icon, hgt=36) + line(.40, .53, 'Setup', 13, '#ffffff', True)
                + line(.36, .67, 'Presets') + line(.36, .78, 'Repeat &amp; info', 11))
    elif where == 'top':
        rows = (line(.43, .23, icon, hgt=40) + line(.40, .46, '{{%s_name}}' % key, 13, '#ffffff', True)
                + line(.31, .60, '{{%s_dur}}' % key) + line(.30, .70, mode) + line(.25, .84, note, hgt=18))
    else:
        rows = (line(.33, .35, icon, hgt=36) + line(.40, .55, '{{%s_name}}' % key, 13, '#ffffff', True)
                + line(.36, .69, '{{%s_dur}}' % key) + line(.42, .80, mode + note, hgt=18))
    return f'''    <button class="reset" onClick="{{{{{key}_apply}}}}" aria-label="{{{{{key}_name}}}}" style="position: absolute; left: {x0}px; top: {y0}px; width: {w}px; height: {h}px; {{{{dim}}}}">
      <span aria-hidden="true" style="position: absolute; inset: 0; filter: drop-shadow(0 0 8px {glow})">
        <span style="position: absolute; inset: 0; clip-path: path(&quot;{inner}&quot;); background: {fill}; backdrop-filter: blur(4px); -webkit-backdrop-filter: blur(4px)"></span>
        <span style="position: absolute; inset: 0; clip-path: {ring.replace(chr(34), '&quot;')}; background: {border}"></span>
      </span>
      {rows}
    </button>
'''

# ---------------------------------------------------------------- ring
def ticks():
    out = []
    c = RING
    for i in range(120):
        long = i % 5 == 0
        r1, r2 = (111, 127) if long else (114, 124)
        out.append('<line x1="%d" y1="%d" x2="%d" y2="%d" transform="rotate(%d %d %d)"></line>' % (c, c - r2, c, c - r1, i * 3, c, c))
    return ''.join(out)

D = 2 * RING
RING_HTML = f'''    <div aria-hidden="true" style="position: absolute; left: {CX - RING}px; top: {CY - RING}px; width: {D}px; height: {D}px; border-radius: 50%; box-sizing: border-box; border: 1.5px solid rgba(120,160,255,.9); background: radial-gradient(circle, #0a1032 0%, #0c1440 78%, #18236a 86%, #0f1850 100%); box-shadow: 0 0 20px rgba(80,130,255,.6), inset 0 0 14px rgba(90,130,255,.4)"></div>
    <div aria-hidden="true" style="position: absolute; left: {CX - 108}px; top: {CY - 108}px; width: 216px; height: 216px; border-radius: 50%; box-sizing: border-box; border: 1.5px solid rgba(110,145,255,.55); background: radial-gradient(circle at 50% 45%, #121c56, #080d2c 72%); box-shadow: 0 0 12px rgba(80,120,255,.35)"></div>
    <sc-if value="{{{{notActive}}}}" hint-placeholder-val="{{{{ true }}}}">
      <svg aria-hidden="true" width="{D}" height="{D}" viewBox="0 0 {D} {D}" style="position: absolute; left: {CX - RING}px; top: {CY - RING}px" stroke="rgba(150,175,255,.6)" stroke-width="1.2" stroke-linecap="round">{ticks()}</svg>
    </sc-if>
    <sc-if value="{{{{isActive}}}}" hint-placeholder-val="{{{{ false }}}}">
      <svg aria-hidden="true" width="{D}" height="{D}" viewBox="0 0 {D} {D}" style="position: absolute; left: {CX - RING}px; top: {CY - RING}px">
        <sc-for list="{{{{segs}}}}" as="sg" hint-placeholder-count="60">
          <rect x="{RING - 3}" y="3" width="6" height="19" rx="2.5" fill="{{{{sg.fill}}}}" transform="{{{{sg.tf}}}}" style="filter: {{{{sg.glow}}}}"></rect>
        </sc-for>
      </svg>
      <div aria-hidden="true" style="position: absolute; left: {CX + 40}px; top: {CY - 104}px; width: 0; height: 0">
        <span class="zz" style="left: 0; top: 40px; font-size: 14px; animation-delay: 0s">z</span>
        <span class="zz" style="left: 12px; top: 24px; font-size: 19px; animation-delay: 1.2s">Z</span>
        <span class="zz" style="left: 26px; top: 4px; font-size: 24px; animation-delay: 2.4s">Z</span>
      </div>
    </sc-if>
    <sc-if value="{{{{showDrum}}}}" hint-placeholder-val="{{{{ true }}}}">
      <div style="position: absolute; left: {CX - 80}px; top: 80px; width: 160px; text-align: center; font-size: 12px; line-height: 14px; letter-spacing: 1.2px; font-weight: 700; color: #cdd5f5">DURATION</div>
      <div aria-hidden="true" style="position: absolute; left: 106px; top: 151px; width: 178px; height: 46px; box-sizing: border-box; border-radius: 14px; border: 1px solid rgba(150,140,255,.55); background: linear-gradient(180deg, #4a3fd0, #2f289a); box-shadow: 0 0 16px rgba(110,90,255,.45)"></div>
      <div class="od" onPointerDown="{{{{dDown}}}}" onPointerMove="{{{{dMove}}}}" onPointerUp="{{{{dUp}}}}" onPointerCancel="{{{{dUp}}}}" onWheel="{{{{dWheel}}}}" style="position: absolute; left: {CX - 100}px; top: {CY - 72}px; width: 200px; height: 148px; border: 0; border-radius: 0; background: none">
        <div style="position: absolute; inset: 0">
          <sc-for list="{{{{durItems}}}}" as="s" hint-placeholder-count="15">
            <button class="odi" onClick="{{{{s.pick}}}}" aria-label="{{{{s.aria}}}}" style="height: 40px; margin-top: -20px; line-height: 40px; transform: {{{{s.tf}}}}; opacity: {{{{s.op}}}}; font-size: {{{{s.fs}}}}px; font-weight: {{{{s.fw}}}}; color: {{{{s.col}}}}">{{{{s.label}}}}</button>
          </sc-for>
        </div>
      </div>
    </sc-if>
    <div style="position: absolute; left: {CX - 100}px; top: {CY - 30}px; width: 200px; display: {{{{clockDisp}}}}; flex-direction: column; align-items: center">
      <div style="font-size: {{{{clockSize}}}}px; line-height: 40px; font-weight: 700; color: #ffffff; letter-spacing: .5px; text-align: center; white-space: pre-line">{{{{clock}}}}</div>
      <div style="font-size: 15px; line-height: 20px; color: #c4cdf0; margin-top: 2px">{{{{caption}}}}</div>
    </div>
'''

CLOCK = '<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#e6ebff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="flex: none"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>'
FLAG = '<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#e6ebff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="flex: none"><path d="M5 22V3"></path><path d="M5 4h13l-2.5 4.5L18 13H5"></path></svg>'
CARD = 'box-sizing: border-box; border-radius: 16px; border: 1px solid rgba(120,145,235,.32); background: linear-gradient(180deg, rgba(22,32,84,.72), rgba(12,18,56,.72)); box-shadow: inset 0 1px 0 rgba(255,255,255,.06)'
TITLE = 'position: absolute; left: 12px; top: 13px; display: flex; align-items: center; gap: 9px; font-size: 15px; line-height: 22px; font-weight: 500; color: #ffffff'

CARDS = f'''    <div style="position: absolute; left: 10px; top: 347px; width: 180px; height: 156px; {CARD}">
      <div style="{TITLE}">{CLOCK}<sc-if value="{{{{startedFixed}}}}" hint-placeholder-val="{{{{ false }}}}"><span>Started</span></sc-if><sc-if value="{{{{notStarted}}}}" hint-placeholder-val="{{{{ true }}}}"><span>Start at</span></sc-if></div>
      <sc-if value="{{{{startedFixed}}}}" hint-placeholder-val="{{{{ false }}}}">
        <div style="position: absolute; left: 0; right: 0; top: 60px; text-align: center; font-size: 26px; line-height: 32px; font-weight: 700; color: #ffffff">{{{{startedTime}}}}</div>
        <div style="position: absolute; left: 0; right: 0; top: 96px; text-align: center; font-size: 15px; line-height: 20px; color: #c4cdf0">{{{{startedDay}}}}</div>
      </sc-if>
      <sc-if value="{{{{showWheel}}}}" hint-placeholder-val="{{{{ true }}}}">
        <div style="position: absolute; left: 8px; right: 8px; top: 46px; height: 84px; border-radius: 12px; background: rgba(6,10,34,.55); {{{{dim}}}}">
          <div aria-hidden="true" style="position: absolute; left: 0; right: 0; top: 27px; height: 30px; border-radius: 10px; background: linear-gradient(90deg, rgba(50,70,190,.15), rgba(60,84,210,.7) 30%, rgba(60,84,210,.7) 70%, rgba(50,70,190,.15))"></div>
          <div class="od" onPointerDown="{{{{sDown}}}}" onPointerMove="{{{{sMove}}}}" onPointerUp="{{{{sUp}}}}" onPointerCancel="{{{{sUp}}}}" onWheel="{{{{sWheel}}}}" style="position: absolute; inset: 0; height: auto; border: 0; background: none; border-radius: 12px">
            <div style="position: absolute; inset: 0">
              <sc-for list="{{{{stItems}}}}" as="s" hint-placeholder-count="12">
                <button class="odi" onClick="{{{{s.pick}}}}" disabled="{{{{locked}}}}" aria-label="{{{{s.aria}}}}" style="transform: {{{{s.tf}}}}; opacity: {{{{s.op}}}}; font-size: {{{{s.fs}}}}px; font-weight: {{{{s.fw}}}}; color: {{{{s.col}}}}">{{{{s.label}}}}</button>
              </sc-for>
            </div>
          </div>
        </div>
      </sc-if>
      <div aria-label="{{{{repAria}}}}" style="position: absolute; left: 0; right: 0; top: 135px; display: flex; justify-content: center; gap: 5px">
        <sc-for list="{{{{repDots}}}}" as="r" hint-placeholder-count="7">
          <span style="width: 17px; height: 17px; border-radius: 50%; box-sizing: border-box; display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 700; border: 1px solid {{{{r.bd}}}}; background: {{{{r.bg}}}}; color: {{{{r.col}}}}">{{{{r.l}}}}</span>
        </sc-for>
      </div>
    </div>
    <div style="position: absolute; left: 200px; top: 347px; width: 180px; height: 156px; {CARD}">
      <div style="{TITLE}">{FLAG}<span>Finishes</span></div>
      <div style="position: absolute; left: 0; right: 0; top: 62px; text-align: center; font-size: 27px; line-height: 32px; font-weight: 700; color: #ffffff">{{{{endTime}}}}</div>
      <div style="position: absolute; left: 0; right: 0; top: 98px; text-align: center; font-size: 15px; line-height: 20px; color: #c4cdf0">{{{{endDay}}}}</div>
    </div>
'''

BTN = '''    <button class="go {{goCls}}" onClick="{{toggle}}" style="position: absolute; left: 10px; top: 518px; width: 370px; height: 58px; border-radius: 29px; font-size: 17px; letter-spacing: .3px">
      <sc-if value="{{isIdle}}" hint-placeholder-val="{{ true }}">
        <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="#ffffff" style="margin-right: 12px"><polygon points="6 3 20 12 6 21 6 3"></polygon></svg>
      </sc-if>
      <sc-if value="{{showStop}}" hint-placeholder-val="{{ false }}">
        <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="#ffffff" style="margin-right: 12px"><rect x="4" y="4" width="16" height="16" rx="2"></rect></svg>
      </sc-if>
      {{goLabel}}
    </button>
'''

MODES = '''    <button class="mbtn dnd {{dndCls}}" onClick="{{pickDnd}}" disabled="{{locked}}" aria-pressed="{{isDnd}}" style="position: absolute; left: 10px; top: 602px; width: 182px; height: 64px; {{dim}}">
      <span class="mic"><svg aria-hidden="true" width="30" height="30" viewBox="0 0 24 24" fill="#ffffff"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M10.3 20a2 2 0 0 0 3.4 0z"></path></svg></span>
      <span>Do Not Disturb</span>
    </button>
    <button class="mbtn silent {{silentCls}}" onClick="{{pickSilent}}" disabled="{{locked}}" aria-pressed="{{isSilent}}" style="position: absolute; left: 198px; top: 602px; width: 182px; height: 64px; {{dim}}">
      <span class="mic"><svg aria-hidden="true" width="30" height="30" viewBox="0 0 24 24" fill="#d9b8ff" stroke="#d9b8ff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon><line x1="22" y1="9" x2="16" y2="15"></line><line x1="16" y1="9" x2="22" y2="15"></line></svg></span>
      <span>Silent</span>
    </button>
'''

CSS = '''.mbtn{display:flex;align-items:center;justify-content:center;gap:6px;white-space:nowrap;box-sizing:border-box;border-radius:16px;font-family:Inter,system-ui,sans-serif;font-size:15px;font-weight:500;color:#ffffff;cursor:pointer;padding:0 10px}
.mbtn.dnd{border:1.5px solid rgba(110,150,255,.45);background:linear-gradient(180deg,rgba(40,80,200,.45),rgba(24,48,140,.45))}
.mbtn.dnd.sel{border-color:#8fb6ff;background:linear-gradient(180deg,#3f7dff,#2350d8);box-shadow:0 0 18px rgba(60,120,255,.65),inset 0 1px 0 rgba(255,255,255,.3)}
.mbtn.silent{border:1.5px solid rgba(170,120,255,.5);background:linear-gradient(180deg,rgba(96,52,180,.5),rgba(60,30,130,.5))}
.mbtn.silent.sel{border-color:#d6a8ff;background:linear-gradient(180deg,#8a4ff0,#5e2cc0);box-shadow:0 0 18px rgba(160,90,255,.6),inset 0 1px 0 rgba(255,255,255,.3)}
.mbtn:disabled{cursor:default}
.mic{flex:none;width:40px;height:40px;border-radius:50%;display:flex;align-items:center;justify-content:center}
.mbtn.dnd .mic{background:radial-gradient(circle,rgba(140,190,255,.55),rgba(80,130,255,0) 70%)}
.mbtn.silent .mic{background:radial-gradient(circle,rgba(170,110,255,.6),rgba(120,60,220,0) 70%)}
.go.start{background:linear-gradient(180deg,#43d673,#1f9a45);border:1px solid rgba(190,255,200,.55);box-shadow:0 0 16px rgba(50,210,110,.45),inset 0 1px 0 rgba(255,255,255,.35)}
.go.end{background:linear-gradient(180deg,#f0414f,#b5121f);border:1px solid rgba(255,190,190,.5);box-shadow:0 0 16px rgba(240,60,80,.45),inset 0 1px 0 rgba(255,255,255,.3)}
'''

def main_html():
    return ('  <div style="position: absolute; left: 0; top: 56px; width: 390px; height: 684px">\n'
            + ''.join(tile_html(t) for t in TILES) + RING_HTML + CARDS + BTN + MODES + '  </div>\n')
