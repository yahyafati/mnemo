"""Generates the launcher, monochrome, splash and logo vector drawables from the logo geometry (logo.svg).

Needs shapely (`pip install shapely`) for the monochrome layer. Run from anywhere; it writes into the repo.
"""
import math
from shapely.geometry import Polygon, LineString, box
from shapely import affinity
from shapely.ops import unary_union

import os
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
BACK = dict(x=24, y=24, w=40, h=52, r=8, angle=-6, pivot=(24, 24))
FRONT = dict(x=36, y=22, w=40, h=52, r=8, angle=6, pivot=(36, 22))
CHECK = [(48, 38), (56, 50), (68, 34)]
DARK, CREAM, CREAM_EDGE, INDIGO = "#1C1A17", "#F4F1EA", "#E7E3D8", "#4F46E5"


def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def rot(p, angle, pivot):
    a = math.radians(angle)
    dx, dy = p[0] - pivot[0], p[1] - pivot[1]
    return (pivot[0] + dx * math.cos(a) - dy * math.sin(a), pivot[1] + dx * math.sin(a) + dy * math.cos(a))


def xf(p, s, tx, ty):
    return (p[0] * s + tx, p[1] * s + ty)


def rrect_path(c, s=1.0, tx=0.0, ty=0.0):
    """Rounded rect rotated about its pivot, as SVG path data with arcs (exact, no polygon)."""
    x, y, w, h, r = c["x"], c["y"], c["w"], c["h"], c["r"]
    pts = [(x + r, y), (x + w - r, y), (x + w, y + r), (x + w, y + h - r),
           (x + w - r, y + h), (x + r, y + h), (x, y + h - r), (x, y + r)]
    q = [xf(rot(p, c["angle"], c["pivot"]), s, tx, ty) for p in pts]
    rr = fmt(r * s)
    P = lambda p: f"{fmt(p[0])},{fmt(p[1])}"
    return (f"M{P(q[0])}L{P(q[1])}A{rr},{rr},0,0,1,{P(q[2])}L{P(q[3])}A{rr},{rr},0,0,1,{P(q[4])}"
            f"L{P(q[5])}A{rr},{rr},0,0,1,{P(q[6])}L{P(q[7])}A{rr},{rr},0,0,1,{P(q[0])}Z")


def rrect_poly(c):
    b = box(c["x"] + c["r"], c["y"] + c["r"], c["x"] + c["w"] - c["r"], c["y"] + c["h"] - c["r"]).buffer(c["r"], quad_segs=12)
    return affinity.rotate(b, c["angle"], origin=c["pivot"])  # same matrix as SVG rotate()


def poly_path(geom, s=1.0, tx=0.0, ty=0.0):
    polys = [geom] if geom.geom_type == "Polygon" else list(geom.geoms)
    out = []
    for p in polys:
        for ring in [p.exterior, *p.interiors]:
            pts = [xf(c, s, tx, ty) for c in list(ring.coords)[:-1]]
            out.append("M" + "L".join(f"{fmt(a)},{fmt(b)}" for a, b in pts) + "Z")
    return "".join(out)


def bounds():
    g = unary_union([rrect_poly(BACK), rrect_poly(FRONT)])
    return g.bounds


def fit(size, canvas):
    """Scale and offset that centre the card pair on `canvas` with its longest side = `size`."""
    x0, y0, x1, y1 = bounds()
    s = size / max(x1 - x0, y1 - y0)
    return s, canvas / 2 - s * (x0 + x1) / 2, canvas / 2 - s * (y0 + y1) / 2


def color_layers(s, tx, ty, indent="    "):
    P = lambda p: f"{fmt(p[0])},{fmt(p[1])}"
    chk = [xf(p, s, tx, ty) for p in CHECK]
    return f"""{indent}<path
{indent}    android:fillColor="{CREAM}"
{indent}    android:pathData="{rrect_path(BACK, s, tx, ty)}"
{indent}    android:strokeColor="{CREAM_EDGE}"
{indent}    android:strokeWidth="{fmt(2 * s)}" />
{indent}<path
{indent}    android:fillColor="{INDIGO}"
{indent}    android:pathData="{rrect_path(FRONT, s, tx, ty)}"
{indent}    android:strokeAlpha="0.2"
{indent}    android:strokeColor="#FFFFFF"
{indent}    android:strokeWidth="{fmt(2 * s)}" />
{indent}<path
{indent}    android:pathData="M{P(chk[0])}L{P(chk[1])}L{P(chk[2])}"
{indent}    android:strokeColor="#FFFFFF"
{indent}    android:strokeLineCap="round"
{indent}    android:strokeLineJoin="round"
{indent}    android:strokeWidth="{fmt(4 * s)}" />
"""


def vector(name, size_dp, viewport, body, comment, ignore=None):
    attrs = f'\n    xmlns:tools="http://schemas.android.com/tools"\n    tools:ignore="{ignore}"' if ignore else ""
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated from the logo geometry; see docs/release/assets/README.md. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"{attrs}
    android:width="{size_dp}dp"
    android:height="{size_dp}dp"
    android:viewportWidth="{viewport}"
    android:viewportHeight="{viewport}">
{body}</vector>
"""


def write(path, text):
    with open(f"{REPO}/{path}", "w") as f:
        f.write(text)


# Adaptive icon foreground: cards + check inside the 66 dp safe zone of the 108 dp canvas.
s, tx, ty = fit(60, 108)
write("app/src/main/res/drawable/ic_launcher_foreground.xml",
      vector("fg", 108, 108, color_layers(s, tx, ty), "Adaptive icon foreground: the two cards and the check, inside the 66 dp safe zone."))

# Monochrome (themed icons): one shape, the check cut out, a gap between the cards.
front = rrect_poly(FRONT)
check = LineString(CHECK).buffer(2, cap_style="round", join_style="round", quad_segs=8)
back_visible = rrect_poly(BACK).difference(front.buffer(2.2, quad_segs=8))
# Polygons, so simplify: 0.03 of 100 units is well below a pixel of the 108 dp canvas.
mono = unary_union([front.difference(check), back_visible]).simplify(0.03)
mono_body = f'    <path\n        android:fillColor="#FFFFFF"\n        android:fillType="evenOdd"\n        android:pathData="{poly_path(mono, s, tx, ty)}" />\n'
write("app/src/main/res/drawable/ic_launcher_monochrome.xml",
      vector("mono", 108, 108, mono_body, "Themed (Android 13+) icon: one shape, the check cut out.", ignore="VectorPath"))

# Splash icon: the dark disc of the Android 12 splash mask (192 dp of 288), with the cards inside it.
ss, stx, sty = fit(96, 288)
splash = (f'    <path\n        android:fillColor="{DARK}"\n        android:pathData="M144,48a96,96 0,1 1 0,192a96,96 0,1 1 0,-192z" />\n'
          + color_layers(ss, stx, sty))
write("app/src/main/res/drawable/ic_splash.xml",
      vector("splash", 288, 288, splash, "Splash icon: the logo on a dark disc, which the Android 12 splash mask shows whole.", ignore="VectorRaster"))

# The full logo tile (Settings > About, onboarding), exactly the SVG on a 100 x 100 viewport.
tile = (f'    <path\n        android:fillColor="{DARK}"\n        android:pathData="M24,0L76,0A24,24 0,0 1,100,24L100,76A24,24 0,0 1,76,100L24,100A24,24 0,0 1,0,76L0,24A24,24 0,0 1,24,0Z" />\n'
        + color_layers(1.0, 0, 0))
write("core/designsystem/src/main/res/drawable/mnemo_logo.xml",
      vector("logo", 100, 100, tile, "The Mnemo logo tile, from the logo SVG."))
print("scale", s, tx, ty, "bounds", bounds())
