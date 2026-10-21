#!/usr/bin/env python3
"""Draws the app's gear icon and launcher banner (both original artwork, MIT like the code).
Run from the repo root: python3 tools/make-icons.py"""
import math
from PIL import Image, ImageDraw, ImageFont

S = 8  # supersample, then shrink for smooth edges

def gear(size=96, teeth=8):
    n = size * S; c = n / 2
    im = Image.new("L", (n, n), 0); d = ImageDraw.Draw(im)
    r_out, r_root, r_hole = 0.47 * n, 0.36 * n, 0.15 * n
    pts = []
    for i in range(teeth * 4):
        a = 2 * math.pi * i / (teeth * 4) - math.pi / 2
        r = r_out if i % 4 in (1, 2) else r_root
        pts.append((c + r * math.cos(a), c + r * math.sin(a)))
    d.polygon(pts, fill=255)
    d.ellipse([c - r_root * 1.02, c - r_root * 1.02, c + r_root * 1.02, c + r_root * 1.02], fill=255)
    d.ellipse([c - r_hole, c - r_hole, c + r_hole, c + r_hole], fill=0)
    a = im.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0)); out.putalpha(a)
    return out

def banner(w=320, h=180):
    im = Image.new("RGB", (w * S // 2, h * S // 2), (8, 10, 16)); d = ImageDraw.Draw(im)
    W, H = im.size
    f = ImageFont.truetype("app/assets/michroma.ttf", int(H * 0.17))
    t = "LIFTOFF"; bb = d.textbbox((0, 0), t, font=f)
    d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], H * 0.52 - (bb[3] - bb[1]) / 2 - bb[1]), t, font=f, fill=(255, 255, 255))
    r = H * 0.025; d.ellipse([W / 2 - r, H * 0.28 - r, W / 2 + r, H * 0.28 + r], fill=(255, 255, 255))
    return im.resize((w, h), Image.LANCZOS)

gear().save("app/res/drawable-nodpi/gear.png")
banner().save("app/res/drawable/banner.png")
print("wrote gear.png and banner.png")
