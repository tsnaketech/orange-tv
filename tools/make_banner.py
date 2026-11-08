"""Generates the Fire TV banner (640x360): screen + play emblem, "Orange TV" text.

Usage: python tools/make_banner.py [output]   (requires Pillow and Windows' Segoe UI font)
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = sys.argv[1] if len(sys.argv) > 1 else ROOT / "app/src/main/res/drawable/banner.png"
S = 4                      # supersampling for a smooth result
W, H = 640 * S, 360 * S
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

# Background: diagonal gradient night -> purple
bg = Image.new("RGB", (W, H))
px = bg.load()
c0, c1 = (14, 22, 48), (53, 32, 106)
for y in range(H):
    for x in range(0, W, 4):
        col = lerp(c0, c1, (x / W + y / H) / 2)
        for k in range(4):
            if x + k < W:
                px[x + k, y] = col
img = bg.convert("RGBA")

# Subtle orange halo behind the emblem
halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(halo).ellipse([40 * S, 40 * S, 300 * S, 320 * S], fill=ORANGE + (70,))
img = Image.alpha_composite(img, halo.filter(ImageFilter.GaussianBlur(60 * S)))

# Draw on a transparent layer then composite: ImageDraw does not blend alpha in place.
layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(layer)
# Rounded screen
d.rounded_rectangle([70 * S, 110 * S, 250 * S, 230 * S], radius=22 * S,
                    fill=(255, 255, 255, 22), outline=(255, 255, 255, 245), width=7 * S)
# Stand
d.rounded_rectangle([125 * S, 246 * S, 195 * S, 256 * S], radius=5 * S, fill=(255, 255, 255, 245))
# Orange play button (corners softened by a round stroke)
tri = [(144 * S, 141 * S), (186 * S, 170 * S), (144 * S, 199 * S)]
rounded_triangle(d, tri, 4 * S, ORANGE)

# Text
bold = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 62 * S)
semi = ImageFont.truetype(r"C:\Windows\Fonts\seguisb.ttf", 26 * S)
x0 = 285 * S
d.text((x0, 118 * S), "Orange", font=bold, fill=ORANGE)
d.text((x0, 182 * S), "TV", font=bold, fill=(255, 255, 255))
w_tv = d.textlength("TV", font=bold)
d.text((x0 + w_tv + 16 * S, 214 * S), "sur Fire TV", font=semi, fill=(255, 255, 255, 170))

img = Image.alpha_composite(img, layer)
img.resize((640, 360), Image.LANCZOS).convert("RGB").save(OUT, optimize=True)
print("ok", OUT)
