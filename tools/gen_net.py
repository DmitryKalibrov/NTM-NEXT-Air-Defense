"""Vegetation net tiles (64 px = half a block, tileable, cutout alpha): a cord mesh with leaves / dry grass /
snowy spruce woven in. Writes textures/block/camo_net_{green,desert,winter}.png."""
import math
import random
from PIL import Image, ImageDraw

OUT = "common/src/main/resources/assets/ntm_air_defense/textures/block/camo_net_%s.png"
S = 64
SCHEMES = {
    # leaf tones dark..light, twig colour, share of "light" leaves
    "green": ([(40, 58, 28), (54, 76, 34), (70, 96, 42), (90, 116, 52)], (74, 60, 40)),
    "desert": ([(112, 92, 60), (140, 118, 78), (168, 146, 100), (194, 174, 128)], (98, 78, 52)),
    "winter": ([(44, 58, 40), (62, 78, 54), (214, 220, 222), (238, 240, 242)], (70, 60, 48)),
}


def wrap(draw_fn):
    for dx in (-S, 0, S):
        for dy in (-S, 0, S):
            draw_fn(dx, dy)


def leaf(d, x, y, ang, ln, wd, col, edge):
    pts = []
    for i in range(10):
        t = i / 9 * math.pi * 2
        px, py = math.cos(t) * ln, math.sin(t) * wd * (1 - 0.35 * math.cos(t))
        pts.append((x + px * math.cos(ang) - py * math.sin(ang), y + px * math.sin(ang) + py * math.cos(ang)))
    wrap(lambda dx, dy: d.polygon([(a + dx, b + dy) for a, b in pts], fill=col + (255,), outline=edge + (255,)))


def tile(name, seed):
    tones, twig = SCHEMES[name]
    rnd = random.Random(seed)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cord = (52, 48, 38, 255)
    for k in range(0, S, 16):  # diamond cord mesh
        wrap(lambda dx, dy: d.line([(k + dx, dy), (k + S + dx, S + dy)], fill=cord))
        wrap(lambda dx, dy: d.line([(k + dx, S + dy), (k + S + dx, dy)], fill=cord))
    for _ in range(14):  # twigs under the leaves
        x, y, a = rnd.uniform(0, S), rnd.uniform(0, S), rnd.uniform(0, math.pi)
        l = rnd.uniform(8, 16)
        wrap(lambda dx, dy: d.line([(x + dx, y + dy), (x + math.cos(a) * l + dx, y + math.sin(a) * l + dy)], fill=twig + (255,), width=1))
    for i in range(170):  # leaves, darker first so the light ones sit on top
        t = min(3, int(i / 170 * 4 + rnd.uniform(-0.6, 0.6)))
        t = max(0, t)
        col = tones[t]
        edge = tuple(max(0, c - 22) for c in col)
        x, y = rnd.uniform(0, S), rnd.uniform(0, S)
        if name == "desert":  # dry grass: long thin blades
            leaf(d, x, y, rnd.uniform(-0.5, 0.5) + math.pi / 2, rnd.uniform(4, 7), 0.9, col, edge)
        elif name == "winter" and t < 2:  # spruce needles under the snow
            leaf(d, x, y, rnd.uniform(0, math.pi), rnd.uniform(3, 5), 1.2, col, edge)
        else:
            leaf(d, x, y, rnd.uniform(0, math.pi), rnd.uniform(2.5, 4.5), rnd.uniform(1.6, 2.6), col, edge)
    # cut a few holes so it reads as a net, not a sheet
    px = img.load()
    for _ in range(7):
        cx, cy, r = rnd.uniform(0, S), rnd.uniform(0, S), rnd.uniform(3, 5.5)
        for yy in range(S):
            for xx in range(S):
                ddx = min(abs(xx - cx), S - abs(xx - cx))
                ddy = min(abs(yy - cy), S - abs(yy - cy))
                if ddx * ddx + ddy * ddy < r * r and px[xx, yy][:3] != cord[:3]:
                    px[xx, yy] = (0, 0, 0, 0)
    img.save(OUT % name)
    cov = sum(1 for p in img.getdata() if p[3]) / (S * S)
    print(name, "coverage %.2f" % cov)


for i, n in enumerate(SCHEMES):
    tile(n, 7 + i)
