#!/usr/bin/env python3
"""Draws the four WA outback header scenes once, and writes them as
Android vector drawables (android/res/drawable/scene_*.xml) and as SVG
(tools/out/scene_*.svg plus a JS block for the control panel).

Flat shapes in the WA bush and outback palette with night-indigo outlines.
Run:  python3 tools/scenes.py
"""
import math, os, random, json

W, H = 600, 200
C = dict(pindan="#B5452C", iron="#7A2E22", salmon="#E3A587", spinifex="#D9B45A", saltbush="#9DAA94",
         mulga="#6E7352", bone="#EDE3D0", pink="#F0C6C0", lilac="#B9A3C9", sky="#6FC3C4", indigo="#2B2F55")
INK = C["indigo"]
FLAT = dict(C)
# Hand-inked variant: soft pastel colours, thin near-black outlines that wobble, hatching and stipple
PASTEL = dict(pindan="#E58E6F", iron="#A9665C", salmon="#F1C3A6", spinifex="#EBCB86", saltbush="#B4CFC4",
              mulga="#7E9C92", bone="#F6F1E6", pink="#F3D2D6", lilac="#C3B5DD", sky="#7FCFD6", indigo="#4F5C8F")
PEN = "#1D1A1F"


def mix(a, b, t):
    a = [int(a[i:i + 2], 16) for i in (1, 3, 5)]; b = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join("%02X" % round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def f(v):
    s = ("%.1f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


class Scene:
    def __init__(self, name, ink=False):
        self.name, self.items, self.ink = name, [], ink
        self.rw = random.Random(sum(map(ord, name)) * 7 + (1 if ink else 0))

    def path(self, d, fill=None, stroke=None, sw=1.2, alpha=1.0, cap="round", clip=None):
        if self.ink and stroke == INK: stroke, sw = PEN, max(0.35, sw * 0.6)
        if self.ink and fill == INK: fill = PEN
        self.items.append(dict(d=d, fill=fill, stroke=stroke, sw=sw, alpha=alpha, cap=cap, clip=clip))

    def wob(self, pts, closed=False, amp=0.7, step=7.0):
        """Breaks long edges into short pieces and nudges each, so lines look drawn by hand."""
        if not self.ink or len(pts) < 2: return pts
        out = []; seq = list(pts) + ([pts[0]] if closed else [])
        for i in range(len(seq) - 1):
            (x0, y0), (x1, y1) = seq[i], seq[i + 1]
            n = max(1, int(math.hypot(x1 - x0, y1 - y0) / step))
            for k in range(n):
                t = k / n; j = amp if (k > 0 or i > 0) else 0
                out.append((x0 + (x1 - x0) * t + self.rw.uniform(-j, j), y0 + (y1 - y0) * t + self.rw.uniform(-j, j)))
        out.append(seq[-1] if not closed else out[0])
        return out[:-1] if closed else out

    @staticmethod
    def d_of(pts, closed):
        return "M" + " L".join("%s,%s" % (f(x), f(y)) for x, y in pts) + ("Z" if closed else "")

    def hatch(self, clip_pts, angle=80, gap=2.4, sw=0.45, alpha=0.75, broken=0.25):
        """Fine parallel pen strokes, kept inside a shape."""
        if not self.ink: return
        xs = [p[0] for p in clip_pts]; ys = [p[1] for p in clip_pts]
        cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        a = math.radians(angle); dx, dy = math.cos(a), math.sin(a); nx, ny = -dy, dx
        corners = [(x - cx, y - cy) for x in (min(xs), max(xs)) for y in (min(ys), max(ys))]
        O = max(abs(px * nx + py * ny) for px, py in corners) + 1
        T = max(abs(px * dx + py * dy) for px, py in corners) + 1
        segs = []; o = -O
        while o < O:
            t = -T
            while t < T:
                ln = self.rw.uniform(3, 12)
                if self.rw.random() > broken:
                    x0, y0 = cx + nx * o + dx * t, cy + ny * o + dy * t
                    x1, y1 = x0 + dx * ln, y0 + dy * ln
                    segs.append("M%s,%s L%s,%s" % (f(x0 + self.rw.uniform(-.3, .3)), f(y0), f(x1), f(y1 + self.rw.uniform(-.3, .3))))
                t += ln + self.rw.uniform(0.5, 3)
            o += gap * self.rw.uniform(0.8, 1.2)
        # (offsets and lengths cover just the shape's box)
        if segs: self.path(" ".join(segs), None, PEN, sw, alpha, clip=self.d_of(clip_pts, True))

    def stipple(self, clip_pts, n, sw=0.9, alpha=0.6):
        """Pen dots scattered inside a shape."""
        if not self.ink: return
        xs = [p[0] for p in clip_pts]; ys = [p[1] for p in clip_pts]
        segs = []
        for _ in range(n):
            x, y = self.rw.uniform(min(xs), max(xs)), self.rw.uniform(min(ys), max(ys))
            segs.append("M%s,%s l0.2,0" % (f(x), f(y)))
        self.path(" ".join(segs), None, PEN, sw, alpha, clip=self.d_of(clip_pts, True))

    def dashes(self, y0, y1, n, wmin=4, wmax=14, sw=0.5, alpha=0.6, x0=0, x1=W):
        """Short wavy strokes: water ripples, ground streaks, wind."""
        if not self.ink: return
        segs = []
        for _ in range(n):
            x, y = self.rw.uniform(x0, x1), self.rw.uniform(y0, y1); w = self.rw.uniform(wmin, wmax)
            segs.append("M%s,%s q%s,%s %s,0" % (f(x), f(y), f(w / 2), f(self.rw.uniform(-1.2, 1.2)), f(w)))
        self.path(" ".join(segs), None, PEN, sw, alpha)

    # ----- shapes -----
    def rect(self, x, y, w, h, fill, stroke=None, sw=1.2):
        self.path("M%s,%sh%sv%sh%sZ" % (f(x), f(y), f(w), f(h), f(-w)), fill, stroke, sw)

    def poly(self, pts, fill=None, stroke=INK, sw=1.2, closed=True, alpha=1.0):
        if stroke: pts = self.wob(pts, closed)
        self.path(self.d_of(pts, closed), fill, stroke, sw, alpha)

    def ellipse(self, cx, cy, rx, ry, fill, stroke=INK, sw=1.0, rot=0.0):
        if self.ink and stroke and max(rx, ry) > 2:
            a = math.radians(rot); n = 18 if max(rx, ry) < 10 else 40; pts = []
            for k in range(n):
                t = 2 * math.pi * k / n; j = 1 + self.rw.uniform(-0.05, 0.05) * (1 if n == 18 else 0.3)
                ex, ey = rx * math.cos(t) * j, ry * math.sin(t) * j
                pts.append((cx + ex * math.cos(a) - ey * math.sin(a), cy + ex * math.sin(a) + ey * math.cos(a)))
            self.path(self.d_of(pts, True), fill, stroke, sw); return
        a = math.radians(rot); dx, dy = rx * math.cos(a), rx * math.sin(a)
        d = "M%s,%s A%s,%s %s 1,0 %s,%s A%s,%s %s 1,0 %s,%sZ" % (
            f(cx - dx), f(cy - dy), f(rx), f(ry), f(rot), f(cx + dx), f(cy + dy),
            f(rx), f(ry), f(rot), f(cx - dx), f(cy - dy))
        self.path(d, fill, stroke, sw)

    def circle(self, cx, cy, r, fill, stroke=INK, sw=1.2):
        self.ellipse(cx, cy, r, r, fill, stroke, sw)

    def line(self, pts, colour, sw, alpha=1.0):
        if colour == INK: pts = self.wob(pts, False, 0.45)
        self.path(self.d_of(pts, False), None, colour, sw, alpha)

    def outlined_line(self, pts, colour, sw):
        """A thick line with an indigo edge: trunks, branches, clouds."""
        pts = self.wob(pts, False, 0.4)
        self.path(self.d_of(pts, False), None, PEN if self.ink else INK, sw + (1.0 if self.ink else 1.8))
        self.path(self.d_of(pts, False), None, colour, sw)

    def bands(self, cols, y0, y1):
        n = len(cols); h = (y1 - y0) / n
        for i, c in enumerate(cols):
            self.rect(0, y0 + i * h - 0.3, W, h + 0.6, c)

    def grad(self, a, b, n):
        return [mix(a, b, i / (n - 1)) for i in range(n)]

    def ridge(self, y_base, pts, fill, sw=1.2):
        self.poly([(0, y_base)] + pts + [(W, y_base)], fill, INK, sw)

    def cloud(self, x, y, w, colour):
        self.outlined_line([(x, y), (x + w, y)], colour, 3.2)

    def dome(self, x, y, r, fill, spikes=4, rnd=None):
        self.path("M%s,%s A%s,%s 0 0,1 %s,%sZ" % (f(x - r), f(y), f(r), f(r), f(x + r), f(y)), fill, INK, 0.9)
        rnd = rnd or random
        for k in range(spikes):
            a = math.radians(30 + 120 * (k + 0.5) / spikes + rnd.uniform(-8, 8))
            self.line([(x + math.cos(a) * r * 0.5, y - math.sin(a) * r * 0.5), (x + math.cos(a) * r * 1.35, y - math.sin(a) * r * 1.35)], INK, 0.6)

    # ----- output -----
    def svg(self):
        out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" preserveAspectRatio="xMidYMid slice">' % (W, H)]
        cid = 0
        for it in self.items:
            a = ['d="%s"' % it["d"], 'fill="%s"' % (it["fill"] or "none")]
            if it["stroke"]:
                a += ['stroke="%s"' % it["stroke"], 'stroke-width="%s"' % f(it["sw"]), 'stroke-linecap="%s"' % it["cap"], 'stroke-linejoin="round"']
            if it["alpha"] < 1:
                a.append('opacity="%s"' % it["alpha"])
            if it["clip"]:
                cid += 1; k = "%s%s%d" % (self.name, "i" if self.ink else "f", cid)
                out.append('<clipPath id="%s"><path d="%s"/></clipPath><g clip-path="url(#%s)"><path %s/></g>' % (k, it["clip"], k, " ".join(a)))
            else:
                out.append("<path %s/>" % " ".join(a))
        out.append("</svg>")
        return "".join(out)

    def vector(self):
        out = ['<?xml version="1.0" encoding="utf-8"?>',
               '<!-- generated by tools/scenes.py; edit that file, not this one -->',
               '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
               '    android:width="%ddp" android:height="%ddp" android:viewportWidth="%d" android:viewportHeight="%d">' % (W, H, W, H)]
        for it in self.items:
            a = ['android:pathData="%s"' % it["d"]]
            if it["fill"]:
                a.append('android:fillColor="%s"' % it["fill"])
                if it["alpha"] < 1: a.append('android:fillAlpha="%s"' % it["alpha"])
            if it["stroke"]:
                a += ['android:strokeColor="%s"' % it["stroke"], 'android:strokeWidth="%s"' % f(it["sw"]),
                      'android:strokeLineCap="%s"' % it["cap"], 'android:strokeLineJoin="round"']
                if it["alpha"] < 1: a.append('android:strokeAlpha="%s"' % it["alpha"])
            if it["clip"]:
                out.append('    <group><clip-path android:pathData="%s" />' % it["clip"])
                out.append("        <path %s /></group>" % " ".join(a))
            else:
                out.append("    <path %s />" % " ".join(a))
        out.append("</vector>")
        return "\n".join(out) + "\n"


# ---------------------------------------------------------------- pieces
def gum_tree(s, x, base, h, rnd, trunk=C["bone"], scale=1.0):
    top = base - h * 0.45
    s.outlined_line([(x, base), (x, top)], trunk, 5 * scale)
    tips = []
    for side in (-1, 1):
        bx, by = x + side * h * 0.22, base - h * 0.75
        s.outlined_line([(x, top), (bx, by)], trunk, 3.2 * scale)
        for k in (-1, 1):
            tx, ty = bx + k * h * 0.13 + side * h * 0.05, by - h * 0.2
            s.outlined_line([(bx, by), (tx, ty)], trunk, 2 * scale)
            tips.append((tx, ty))
    s.outlined_line([(x, top), (x + rnd.uniform(-4, 4), base - h * 0.95)], trunk, 2.4 * scale)
    tips.append((x, base - h * 0.95))
    for tx, ty in tips:
        for _ in range(4):
            s.ellipse(tx + rnd.uniform(-h * 0.1, h * 0.1), ty + rnd.uniform(-h * 0.05, h * 0.04), h * rnd.uniform(0.07, 0.11), h * rnd.uniform(0.035, 0.05),
                      rnd.choice([C["saltbush"], C["mulga"], C["saltbush"]]), INK, 0.8)


def grass_tree(s, x, base, h, rnd):
    w = h * 0.16
    s.poly([(x - w * 0.6, base), (x - w * 0.45, base - h * 0.5), (x + w * 0.45, base - h * 0.5), (x + w * 0.6, base)], INK, INK, 1)
    s.line([(x - w * 0.2, base - h * 0.15), (x + w * 0.2, base - h * 0.2)], C["iron"], 0.7)
    s.line([(x - w * 0.2, base - h * 0.32), (x + w * 0.2, base - h * 0.37)], C["iron"], 0.7)
    s.poly([(x, base - h * 0.75), (x + 0.25 * h, base - h * 0.42), (x + w * 0.5, base - h * 0.5), (x, base - h * 0.4),
            (x - w * 0.5, base - h * 0.5), (x - 0.25 * h, base - h * 0.42)], C["spinifex"], INK, 0.9)
    s.poly([(x - 1.2, base - h * 0.7), (x - 1, base - h * 1.45), (x, base - h * 1.5), (x + 1, base - h * 1.45), (x + 1.2, base - h * 0.7)], C["iron"], INK, 0.7)
    cx, cy = x, base - h * 0.62
    for k in range(24):
        a = math.radians(rnd.uniform(-10, 190)); r = h * rnd.uniform(0.38, 0.55)
        ex, ey = cx + math.cos(a) * r, cy - math.sin(a) * r * 0.8
        if a > math.radians(160) or a < math.radians(20): ey += h * 0.12
        s.line([(cx, cy), ((cx + ex) / 2, min(cy, ey) - h * 0.05), (ex, ey)], rnd.choice([C["saltbush"], C["mulga"], C["saltbush"], INK]), 0.9)
    s.ellipse(cx, cy, h * 0.12, h * 0.06, C["mulga"], INK, 0.8)


def pinnacle(s, x, base, h, rnd):
    w = h * rnd.uniform(0.26, 0.34)
    tip = (x + rnd.uniform(-2, 2), base - h)
    s.poly([(x - w * 0.5, base), (x + w * 2.2, base + 4), (x + w * 2.4, base + 7), (x + w * 0.2, base + 3)], C["lilac"], None, 0)
    left = [(x - w * 0.5, base), (x - w * 0.42, base - h * 0.4), (x - w * 0.18, base - h * 0.82), tip]
    right = [tip, (x + w * 0.22, base - h * 0.75), (x + w * 0.42, base - h * 0.35), (x + w * 0.5, base)]
    s.poly(left + right, C["bone"], INK, 1.1)
    shade = [tip, (x + w * 0.22, base - h * 0.75), (x + w * 0.42, base - h * 0.35), (x + w * 0.5, base), (x + w * 0.08, base), (x + w * 0.05, base - h * 0.5)]
    s.poly(shade, C["salmon"], None, 0)
    s.hatch(shade, 84, 1.6 if h > 30 else 1.3, 0.35, 0.7)
    s.poly(left + right, None, INK, 1.1)
    for k in range(3):
        yy = base - h * rnd.uniform(0.1, 0.6)
        s.line([(x - w * 0.25, yy), (x - w * 0.05, yy - 0.5)], INK, 0.5)


def mesa(s, x0, x1, top, base, left_c, right_c, rnd):
    cap = (base - top) * 0.22; inset = (x1 - x0) * 0.12; mid = x0 + (x1 - x0) * 0.6
    body = [(x0, base), (x0 + inset, top + cap), (x1 - inset, top + cap), (x1, base)]
    s.poly(body, left_c, INK, 1.1)
    face = [(mid, top + cap), (x1 - inset, top + cap), (x1, base), (mid + (x1 - x0) * 0.08, base)]
    s.poly(face, right_c, None, 0)
    s.hatch(face, 95, 2.0, 0.4, 0.8)
    s.hatch([(x0 + inset, top + cap), (mid, top + cap), (mid + (x1 - x0) * 0.08, base), (x0, base)], 92, 5.5, 0.35, 0.45, 0.5)
    s.poly([(x0 + inset, top + cap), (x0 + inset + 1, top), (x1 - inset - 1, top - 1), (x1 - inset, top + cap)], left_c, INK, 1.1)
    s.poly([(mid, top + cap), (mid, top - 0.5), (x1 - inset - 1, top - 1), (x1 - inset, top + cap)], right_c, None, 0)
    s.poly(body, None, INK, 1.1)
    s.line([(mid, top), (mid, top + cap), (mid + (x1 - x0) * 0.08, base)], INK, 0.8)
    for k in range(int((x1 - x0) / 9)):
        xx = rnd.uniform(x0 + inset + 2, x1 - inset - 2); yy = rnd.uniform(top + 2, top + cap - 2)
        s.line([(xx, yy), (xx, yy + rnd.uniform(2, cap * 0.6))], INK, 0.45)
        xx = rnd.uniform(x0 + inset + 4, x1 - inset - 4); yy = rnd.uniform(top + cap + 4, base - 8)
        s.line([(xx, yy), (xx - 2, yy + rnd.uniform(5, 16))], INK, 0.45)


def mulga_bush(s, x, base, h, rnd):
    s.line([(x, base), (x - 1, base - h * 0.5)], INK, 1.2)
    for _ in range(5):
        s.ellipse(x + rnd.uniform(-h * 0.35, h * 0.35), base - h * rnd.uniform(0.55, 0.85), h * rnd.uniform(0.18, 0.26), h * rnd.uniform(0.12, 0.16), C["mulga"], INK, 0.8)


# ---------------------------------------------------------------- scenes
def scene_grass(ink=False):
    s = Scene("grass", ink); r = random.Random(4)
    s.bands(s.grad(C["sky"], C["bone"], 7), 0, 122)
    s.circle(175, 116, 38, mix(C["bone"], "#FFFFFF", 0.25), INK, 1.1)
    for x, y, w in ((-10, 30, 90), (60, 48, 110), (330, 26, 150), (470, 52, 100)): s.cloud(x, y, w, C["bone"])
    s.ridge(132, [(0, 116), (40, 112), (90, 117), (150, 108), (220, 113), (300, 106), (380, 112), (450, 105), (520, 111), (600, 108)], C["lilac"])
    s.ridge(134, [(0, 124), (60, 119), (130, 124), (200, 118), (280, 123), (360, 117), (440, 122), (520, 117), (600, 121)], C["saltbush"])
    s.rect(0, 128, W, 72, C["salmon"]); s.line([(0, 128), (W, 128)], INK, 1.1)
    for k in range(9):
        y = r.uniform(140, 196); x = r.uniform(-40, 520); w = r.uniform(60, 160)
        s.poly([(x, y), (x + w, y - 2), (x + w + 10, y + 1.5), (x + 6, y + 2.5)], C["pindan"], None, 0)
    for x, h in ((70, 70), (300, 92), (520, 80), (400, 50)): gum_tree(s, x, 130, h, r)
    for x, h in ((140, 40), (250, 30), (460, 34), (35, 26), (560, 28), (350, 22)):
        s.poly([(x - 2, 133), (x + h * 2.2, 129.5), (x + h * 2.2, 133.5)], C["pindan"], None, 0)
    for x, b, h in ((25, 160, 34), (115, 190, 52), (230, 172, 42), (345, 196, 60), (480, 180, 50), (575, 165, 38), (180, 145, 22), (420, 142, 20), (300, 140, 16)):
        s.poly([(x, b), (x + h * 1.6, b - 2), (x + h * 1.6, b + 2)], C["pindan"], None, 0)
        grass_tree(s, x, b, h, r)
    for _ in range(14): s.dome(r.uniform(0, W), r.uniform(150, 199), r.uniform(4, 7), r.choice([C["spinifex"], C["saltbush"]]), 4, r)
    s.stipple([(0, 129), (W, 129), (W, 200), (0, 200)], 900, 0.8, 0.45)
    s.dashes(130, 199, 60, 4, 16, 0.45, 0.55)
    s.dashes(6, 100, 14, 10, 40, 0.4, 0.35)
    return s


def scene_pinnacles(ink=False):
    s = Scene("pinnacles", ink); r = random.Random(7)
    s.bands(s.grad(C["indigo"], "#5E5486", 4) + s.grad("#7A6C9C", C["lilac"], 3) + s.grad(mix(C["lilac"], C["pink"], .5), C["pink"], 2), 0, 128)
    for _ in range(70):
        x, y = r.uniform(0, W), r.uniform(2, 80)
        s.circle(x, y, r.uniform(0.5, 1.1), C["bone"], None, 0)
    s.circle(130, 74, 24, C["bone"], INK, 1.2)
    for dx, dy, rr in ((-9, -7, 5), (7, -12, 2.6), (9, 4, 4.3), (-5, 10, 3.4), (-14, 5, 2.2)): s.circle(130 + dx, 74 + dy, rr, C["pink"], INK, 0.7)
    for x, y, w in ((220, 70, 110), (160, 98, 100), (330, 92, 80), (440, 82, 130)): s.cloud(x, y, w, C["lilac"])
    s.ridge(132, [(0, 122), (40, 119), (110, 124), (190, 121), (260, 118), (330, 123), (410, 120), (480, 117), (540, 122), (600, 119)], C["mulga"])
    s.rect(0, 127, W, 73, C["spinifex"]); s.line([(0, 127), (W, 127)], INK, 1.1)
    for _ in range(10):
        y = r.uniform(140, 198); x = r.uniform(-60, 540)
        s.line([(x, y), (x + 50, y - 1.5), (x + 120, y + 1)], C["pindan"], 0.6, 0.7)
    spots = []
    for _ in range(26):
        y = r.uniform(130, 200); h = 10 + (y - 128) * 0.9 + r.uniform(-4, 8)
        spots.append((y, r.uniform(-10, W + 10), h))
    s.stipple([(0, 128), (W, 128), (W, 200), (0, 200)], 700, 0.8, 0.5)
    for y, x, h in sorted(spots): pinnacle(s, x, y, h, r)
    s.hatch([(0, 118), (W, 118), (W, 128), (0, 128)], 88, 2.2, 0.35, 0.5)
    return s


def scene_saltlake(ink=False):
    s = Scene("saltlake", ink); r = random.Random(11)
    s.bands(s.grad(C["lilac"], C["pink"], 4) + s.grad(mix(C["pink"], C["bone"], .4), C["bone"], 3), 0, 118)
    for x, y, w in ((210, 20, 120), (330, 30, 140), (460, 46, 130), (520, 24, 90), (300, 62, 90)): s.cloud(x, y, w, C["pink"])
    s.circle(430, 104, 22, C["spinifex"], INK, 1.1)
    s.poly([(0, 118), (20, 113), (45, 112), (58, 104), (68, 113), (95, 110), (115, 101), (128, 112), (165, 114), (180, 118)], C["mulga"], INK, 1)
    s.poly([(250, 118), (262, 114), (280, 113), (300, 116), (330, 115), (345, 118)], C["saltbush"], INK, 1)
    s.poly([(500, 118), (512, 107), (530, 106), (548, 99), (560, 108), (580, 112), (590, 104), (600, 108), (600, 118)], C["mulga"], INK, 1)
    s.bands(s.grad(C["bone"], C["pink"], 3) + s.grad(mix(C["pink"], C["lilac"], .3), mix(C["pink"], C["lilac"], .6), 3), 118, 200)
    s.poly([(0, 118), (20, 123), (45, 124), (58, 131), (68, 123), (95, 126), (115, 134), (128, 124), (165, 122), (180, 118)], C["saltbush"], None, 0, alpha=0.55)
    s.poly([(500, 118), (512, 128), (530, 129), (548, 136), (560, 127), (580, 124), (590, 131), (600, 128), (600, 118)], C["saltbush"], None, 0, alpha=0.55)
    s.line([(0, 118), (W, 118)], INK, 1)
    for k, y in enumerate((123, 129, 137, 147, 160)): s.line([(0, y), (W, y + (k % 2) * 1.5)], INK, 0.5, 0.55)
    for k in range(-14, 15):
        s.line([(300 + k * 22, 118), (300 + k * 80, 200)], INK, 0.5, 0.55)
    for k in range(8):
        w = 44 - k * 5; y = 122 + k * 5.5
        s.rect(430 - w / 2, y, w, 2.6, C["spinifex"], INK, 0.5)
    s.poly([(0, 172), (60, 176), (120, 175), (200, 182), (300, 184), (400, 190), (520, 192), (600, 195), (600, 200), (0, 200)], C["pindan"], INK, 1)
    s.poly([(0, 186), (90, 188), (200, 193), (320, 194), (420, 199), (430, 200), (0, 200)], C["iron"], None, 0)
    # salmon gum
    x, b = 85, 186
    s.outlined_line([(x, b), (x + 6, 150), (x + 2, 120)], C["salmon"], 4.4)
    for (a, bb) in (((x + 2, 120), (x - 40, 80)), ((x + 2, 120), (x + 40, 84)), ((x + 4, 135), (x + 70, 112)), ((x - 30, 92), (x - 52, 60)), ((x + 30, 92), (x + 36, 58)), ((x + 2, 120), (x + 8, 66))):
        s.outlined_line([a, bb], C["salmon"], 2.2)
    for cx, cy in ((x - 52, 60), (x - 40, 80), (x + 36, 58), (x + 40, 84), (x + 70, 112), (x + 8, 66), (x - 62, 74), (x + 62, 95)):
        for _ in range(6):
            s.ellipse(cx + r.uniform(-14, 14), cy + r.uniform(-7, 6), r.uniform(7, 11), r.uniform(4, 5.5), r.choice([C["saltbush"], C["mulga"]]), INK, 0.8)
    for _ in range(16): s.dome(r.uniform(0, W), r.uniform(184, 200), r.uniform(4, 8), r.choice([C["saltbush"], C["saltbush"], C["spinifex"]]), 4, r)
    s.dashes(120, 172, 90, 3, 12, 0.4, 0.5)
    s.stipple([(0, 172), (W, 192), (W, 200), (0, 200)], 300, 0.8, 0.5)
    s.dashes(8, 70, 10, 15, 45, 0.4, 0.3)
    return s


def scene_mesas(ink=False):
    s = Scene("mesas", ink); r = random.Random(3)
    s.bands(s.grad(C["sky"], C["bone"], 7), 0, 126)
    s.circle(470, 70, 22, C["bone"], INK, 1.2)
    for x, y, w in ((20, 50, 140), (5, 72, 70), (90, 82, 110), (260, 38, 70), (300, 60, 110), (360, 86, 160), (330, 98, 100), (520, 80, 90)): s.cloud(x, y, w, C["bone"])
    mesa(s, 330, 470, 86, 126, C["pink"], C["lilac"], r)
    mesa(s, -10, 230, 70, 126, C["salmon"], C["pindan"], r)
    mesa(s, 200, 330, 92, 126, C["salmon"], C["pindan"], r)
    mesa(s, 450, 620, 80, 126, C["salmon"], C["pindan"], r)
    s.line([(375, 126), (375, 108)], INK, 0.7); s.circle(375, 106, 3.5, C["bone"], INK, 0.6)
    s.rect(0, 125, W, 75, C["pindan"]); s.line([(0, 125), (W, 125)], INK, 1.1)
    for _ in range(9):
        y = r.uniform(132, 196); x = r.uniform(-30, 560); w = r.uniform(70, 170)
        s.poly([(x, y), (x + w / 2, y - 1.6), (x + w, y), (x + w / 2, y + 1.6)], C["salmon"], INK, 0.5)
    s.poly([(388, 125), (392, 125), (330, 200), (250, 200)], C["salmon"], INK, 1)
    s.line([(390, 125), (290, 200)], INK, 0.5)
    for _ in range(40):
        y = r.uniform(130, 200); sz = 1.5 + (y - 125) * 0.09
        x = r.uniform(0, W)
        if 240 < x < 340 and y > 150: continue
        if r.random() < 0.35: mulga_bush(s, x, y, sz * 3.2, r)
        else: s.dome(x, y, sz * 1.3, r.choice([C["spinifex"], C["saltbush"]]), 4, r)
    s.stipple([(0, 126), (W, 126), (W, 200), (0, 200)], 900, 0.8, 0.45)
    s.dashes(8, 110, 12, 12, 40, 0.4, 0.3)
    return s


SCENES = [("grass", "Grass trees and gums", scene_grass), ("pinnacles", "Pinnacles at night", scene_pinnacles),
          ("saltlake", "Salt lake sunset", scene_saltlake), ("mesas", "Pilbara mesas", scene_mesas)]

if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.makedirs(os.path.join(root, "tools", "out"), exist_ok=True)
    js = {}
    for ink in (False, True):
        C.clear(); C.update(PASTEL if ink else FLAT)
        for key, label, fn in SCENES:
            s = fn(ink); name = key + ("_ink" if ink else "")
            with open(os.path.join(root, "android", "res", "drawable", "scene_%s.xml" % name), "w") as fh: fh.write(s.vector())
            svg = s.svg()
            with open(os.path.join(root, "tools", "out", "scene_%s.svg" % name), "w") as fh: fh.write(svg)
            js[name] = svg
            print(name, len(s.items), "shapes", len(svg) // 1024, "KB")
    with open(os.path.join(root, "tools", "out", "scenes.js"), "w") as fh:
        fh.write("const SCENE_SVG = " + json.dumps(js) + ";\n")
