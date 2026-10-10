"""Generate the 53T6 interceptor model (flight parts: motor stage and kill vehicle) and its texture.

usage: python gen_53t6.py   -> models/obj/abm_53t6.obj + textures/item/abm_53t6.png, then run obj2mesh.py on it.
Model space as for Buk/Tor: Y up, nose of the flying missile towards -Z, origin at the footprint centre.
Faces are flat-coloured from a swatch atlas: each face's UVs stretch over one swatch's inner square.
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "ntm_air_defense")

# Swatch atlas: 8x8 cells of 16 px on 128 px.
SW = {
    "concrete": ((143, 141, 134), 10), "concrete_dark": ((104, 103, 97), 10), "olive": ((79, 90, 58), 6),
    "white": ((214, 214, 206), 4), "nozzle": ((48, 48, 48), 4), "black": ((20, 20, 20), 2),
    "array": ((58, 61, 64), 0), "disc": ((150, 156, 160), 0), "wall": ((168, 163, 149), 8),
    "stripe": ((214, 170, 40), 0), "steel": ((110, 114, 118), 5), "red": ((150, 40, 30), 4),
}
NAMES = list(SW)


def texture(path):
    img = Image.new("RGB", (128, 128))
    rnd = random.Random(135)
    for i, name in enumerate(NAMES):
        (r, g, b), noise = SW[name]
        ox, oy = i % 8 * 16, i // 8 * 16
        for y in range(16):
            for x in range(16):
                n = rnd.randint(-noise, noise)
                c = [r + n, g + n, b + n]
                if name == "array" and (x % 4 == 0 or y % 4 == 0):  # phased-array grid
                    c = [v + 22 for v in c]
                if name == "disc" and (x + y) % 4 == 0:
                    c = [v - 25 for v in c]
                if name == "stripe" and (x + y) // 4 % 2:
                    c = [30, 30, 30]
                img.putpixel((ox + x, oy + y), tuple(max(0, min(255, v)) for v in c))
    img.save(path)


class Obj:
    def __init__(self, title):
        self.lines = [f"# NTM Air Defense - {title}"]
        self.nv = self.nt = 0
        self.lines.append("vn 0 1 0")  # normals are recomputed per face by obj2mesh

    def part(self, name):
        self.lines.append(f"o {name}")

    def face(self, pts, swatch):
        """Convex polygon (CCW seen from outside), fan-split into quads/triangles."""
        i = NAMES.index(swatch)
        u0, v0 = (i % 8 * 16 + 2) / 128, (i // 8 * 16 + 2) / 128
        span = 12 / 128
        # Planar UVs over the face's own extent, on the two axes it is widest along.
        ext = [max(p[k] for p in pts) - min(p[k] for p in pts) for k in range(3)]
        a, b = sorted(range(3), key=lambda k: -ext[k])[:2]
        lo = [min(p[k] for p in pts) for k in range(3)]
        base = self.nv
        for p in pts:
            self.lines.append("v %.5f %.5f %.5f" % p)
            s = (p[a] - lo[a]) / (ext[a] or 1)
            t = (p[b] - lo[b]) / (ext[b] or 1)
            self.lines.append("vt %.5f %.5f" % (u0 + s * span, 1 - (v0 + t * span)))
        self.nv += len(pts)
        idx = [base + k + 1 for k in range(len(pts))]
        polys = [idx] if len(idx) <= 4 else [[idx[0], idx[k], idx[k + 1]] for k in range(1, len(idx) - 1)]
        for poly in polys:
            self.lines.append("f " + " ".join(f"{k}/{k}/1" for k in poly))

    def box(self, x0, y0, z0, x1, y1, z1, sw, skip=()):
        c = lambda x, y, z: (x, y, z)
        faces = {
            "top": [c(x0, y1, z0), c(x0, y1, z1), c(x1, y1, z1), c(x1, y1, z0)],
            "bottom": [c(x0, y0, z0), c(x1, y0, z0), c(x1, y0, z1), c(x0, y0, z1)],
            "north": [c(x0, y0, z0), c(x0, y1, z0), c(x1, y1, z0), c(x1, y0, z0)],
            "south": [c(x0, y0, z1), c(x1, y0, z1), c(x1, y1, z1), c(x0, y1, z1)],
            "west": [c(x0, y0, z0), c(x0, y0, z1), c(x0, y1, z1), c(x0, y1, z0)],
            "east": [c(x1, y0, z0), c(x1, y1, z0), c(x1, y1, z1), c(x1, y0, z1)],
        }
        for k, f in faces.items():
            if k not in skip:
                self.face(f, sw)

    def lathe(self, profile, sw, n=16, xf=lambda p: p, cap_bottom=True, cap_top=False):
        """Surface of revolution about +Y. profile: [(y, r, swatch|None)] bottom to top."""
        ring = lambda y, r: [(r * math.cos(2 * math.pi * k / n), y, r * math.sin(2 * math.pi * k / n)) for k in range(n)]
        for (y0, r0, s), (y1, r1, _) in zip(profile, profile[1:]):
            a, b = ring(y0, r0), ring(y1, r1)
            for k in range(n):
                j = (k + 1) % n
                if r1 < 1e-6:
                    self.face([xf(a[k]), xf(b[k]), xf(a[j])], s or sw)
                else:
                    self.face([xf(a[k]), xf(b[k]), xf(b[j]), xf(a[j])], s or sw)
        if cap_bottom:
            y, r, _ = profile[0]
            self.face([xf(p) for p in ring(y, r)], "nozzle")
        if cap_top:
            y, r, _ = profile[-1]
            self.face([xf(p) for p in ring(y, r)][::-1], "nozzle")

    def save(self, path):
        open(path, "w", encoding="utf-8").write("\n".join(self.lines) + "\n")


# ---- 53T6 interceptor, 6.6 blocks: nozzle, solid-fuel motor section, joint ring, conical forebody, warhead tip ----

MISSILE_LEN = 6.6
PROFILE = [
    (0.0, 0.32, "nozzle"), (0.3, 0.20, "nozzle"),   # nozzle bell, exit at the bottom
    (0.32, 0.46, "olive"), (2.6, 0.46, "nozzle"),   # aft plate, then the motor case (the fuel)
    (2.75, 0.46, "white"), (2.8, 0.44, "white"),    # joint ring
    (5.9, 0.12, "red"), (MISSILE_LEN, 0.0, "red"),  # forebody cone, warhead nose
]


STAGE_TOP = 2.75  # the motor section separates at the joint ring


def missile(o, xf, lo=0.0, hi=MISSILE_LEN):
    """The part of the missile between heights lo and hi; fins go with the motor section."""
    prof = [p for p in PROFILE if lo <= p[0] <= hi]
    o.lathe(prof, "white", xf=xf, cap_top=prof[-1][1] > 0)
    if lo > 0:
        return
    for k in range(4):  # small tail fins
        a = math.pi / 4 + k * math.pi / 2
        cx, cz, nx, nz = math.cos(a), math.sin(a), -math.sin(a), math.cos(a)
        t = 0.03
        pts = [(0.45, 0.35), (0.8, 0.35), (0.8, 0.7), (0.45, 1.3)]  # (radius, height)
        for side in (t, -t):
            f = [(cx * r + nx * side, y, cz * r + nz * side) for r, y in pts]
            o.face([xf(p) for p in (f if side > 0 else f[::-1])], "olive")


def abm_53t6():
    o = Obj("53T6")
    # In flight, centred on the whole missile: motor stage and the kill vehicle that flies on after staging.
    c = MISSILE_LEN / 2
    flight = lambda p: (p[0], p[2], -(p[1] - c))
    o.part("A135Stage")
    missile(o, flight, 0, STAGE_TOP)
    o.part("A135Warhead")
    missile(o, flight, STAGE_TOP, MISSILE_LEN)
    return o


if __name__ == "__main__":
    abm_53t6().save(os.path.join(ROOT, "models", "obj", "abm_53t6.obj"))
    os.makedirs(os.path.join(ROOT, "textures", "item"), exist_ok=True)
    texture(os.path.join(ROOT, "textures", "item", "abm_53t6.png"))
    print("ok")
