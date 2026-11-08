"""Square icon (512x512): screen + orange play button on top, "Orange TV" below.

Usage: python tools/make_icon.py [output]   (requires Pillow and Windows' Segoe UI font)
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = sys.argv[1] if len(sys.argv) > 1 else ROOT / "app/src/main/res/drawable-nodpi/ic_app.png"
S = 4
N = 512 * S
ORANGE = (255, 121, 0)

def rounded_triangle(d, pts, r, fill):
    """Triangle with rounded corners: polygon + thick strokes + disks at the vertices.
    (A plain closed outline leaves a spike at the starting vertex.)"""
    d.polygon(pts, fill=fill)
    for a, b in zip(pts, pts[1:] + pts[:1]):
        d.line([a, b], fill=fill, width=2 * r)
    for x, y in pts:
        d.ellipse([x - r, y - r, x + r, y + r], fill=fill)

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

bg = Image.new("RGB", (N, N))
px = bg.load()
c0, c1 = (14, 22, 48), (53, 32, 106)
for y in range(N):
    for x in range(0, N, 4):
        col = lerp(c0, c1, (x + y) / (2 * N))
        for k in range(4):
            if x + k < N:
                px[x + k, y] = col
img = bg.convert("RGBA")

halo = Image.new("RGBA", (N, N), (0, 0, 0, 0))
ImageDraw.Draw(halo).ellipse([136 * S, 40 * S, 376 * S, 280 * S], fill=ORANGE + (80,))
img = Image.alpha_composite(img, halo.filter(ImageFilter.GaussianBlur(50 * S)))

layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
d = ImageDraw.Draw(layer)
# Screen + stand
d.rounded_rectangle([146 * S, 70 * S, 366 * S, 216 * S], radius=26 * S,
                    fill=(255, 255, 255, 22), outline=(255, 255, 255, 245), width=10 * S)
d.rounded_rectangle([206 * S, 234 * S, 306 * S, 248 * S], radius=7 * S, fill=(255, 255, 255, 245))
# Orange play button
tri = [(234 * S, 108 * S), (286 * S, 143 * S), (234 * S, 178 * S)]
rounded_triangle(d, tri, 5 * S, ORANGE)

# "Orange TV" centred, on one line
font = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 84 * S)
w_o = d.textlength("Orange", font=font)
w_sp = d.textlength(" ", font=font)
w_tv = d.textlength("TV", font=font)
x0 = (N - (w_o + w_sp + w_tv)) / 2
y0 = 290 * S
d.text((x0, y0), "Orange", font=font, fill=ORANGE)
d.text((x0 + w_o + w_sp, y0), "TV", font=font, fill=(255, 255, 255))

img = Image.alpha_composite(img, layer)
img.resize((512, 512), Image.LANCZOS).convert("RGB").save(OUT, optimize=True)
print("ok", OUT)
