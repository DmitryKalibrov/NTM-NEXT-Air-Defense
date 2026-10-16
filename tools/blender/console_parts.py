# Panel elements for the control room (standalone). Each element is built in panel space, in grid px
# (1 px = 1/16 block): X right, Y up, Z out of the panel, footprint 0..w x 0..h on the surface (z = 0).
# The game places it on any face (wall panel, desk slope, tilted tier) with that face's own frame.
# Moving parts are separate objects "<element>_<part>" with a "pivot" property (px) for the renderer.
# Texture: one atlas, cell 0 is a palette of flat swatches, the other 32 px cells are drawn faces (dials, screens).
# White caps and lenses, and parts named *_tint (colour stripes, default white), are tinted in game.
import bpy, bmesh, math
import numpy as np
from mathutils import Matrix, Vector

R = "E:/ModPorting/ntm-air-defense/"
TEX = R + "common/src/main/resources/assets/ntm_air_defense/textures/block/control_parts.png"
C, N = 32, 8                      # cell size, cells per row
S = C * N
for o in [o for o in bpy.data.objects if o.get("element")]:
    bpy.data.objects.remove(o)

PAL = [(22, 22, 22), (52, 54, 54), (92, 96, 96), (150, 154, 152), (235, 235, 230), (190, 40, 32), (120, 24, 20),
       (60, 150, 70), (226, 186, 40), (220, 120, 30), (50, 90, 170), (176, 180, 182), (146, 186, 170), (88, 60, 40),
       (232, 226, 205), (180, 150, 70), (236, 240, 240, 110), (46, 74, 52), (140, 62, 18), (196, 150, 60),
       (118, 30, 26), (60, 70, 110)]
BLACK, DGREY, MGREY, LGREY, WHITE, RED, DRED, GREEN, YELLOW, ORANGE, BLUE, CHROME, PANEL, BAKELITE, CREAM, BRASS, GLASS,     ENAMEL, TEA, GILT, CRIMSON, NAVY = range(22)

# ---------------- atlas ----------------
img = np.zeros((S, S, 4))
img[..., 3] = 255
yy, xx = np.mgrid[0:C, 0:C] + 0.5
rng = np.random.default_rng(3)
for i, c in enumerate(PAL):  # cell 0: 8 x 8 swatches of 4 px
    x, y = (i % 8) * 4, (i // 8) * 4
    img[y:y + 4, x:x + 4, :len(c)] = c  # GLASS carries alpha: parts using it render translucent


def disk(cx, cy, r):
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def ticks(t, cx, cy, r0, r1, a0, a1, n, col, w=0.8):
    for k in range(n):
        a = math.radians(a0 + (a1 - a0) * k / (n - 1))
        for s in np.linspace(r0, r1, 24):
            px_, py_ = cx + math.cos(a) * s, cy + math.sin(a) * s
            t[(abs(xx - px_) <= w) & (abs(yy - py_) <= w)] = col


def frame(t, col, w=1):
    t[:w], t[-w:], t[:, :w], t[:, -w:] = col, col, col, col


def bg(col, grain=3):
    return np.ones((C, C, 3)) * col + rng.integers(-grain, grain + 1, (C, C, 1))


TILES = {}


RECT = {}  # tile id -> (x, y, size) in the atlas


def tile(fn):
    i = TILES[fn.__name__] = len(TILES) + 1
    x, y = (i % N) * C, (i // N) * C
    RECT[i] = (x, y, C)
    img[y:y + C, x:x + C, :3] = np.clip(fn(), 0, 255)


def big_tile(fn):
    """2 x 2 cells, filled from the top-right corner of the atlas down."""
    i = TILES[fn.__name__] = 100 + len(TILES)
    k = sum(1 for v in TILES.values() if v >= 100) - 1
    x, y = S - 2 * C * (k + 1), S - 2 * C
    RECT[i] = (x, y, 2 * C)
    img[y:y + 2 * C, x:x + 2 * C, :3] = np.clip(fn(), 0, 255)


@tile
def dial_sq():  # square meter: pivot low centre, 90 deg arc, red end zone
    t = bg(CREAM_RGB := (232, 226, 205), 2)
    ticks(t, 16, 6, 16, 21, 135, 45, 11, (30, 30, 30))
    ticks(t, 16, 6, 16, 21, 54, 45, 2, (190, 40, 32))
    frame(t, (30, 30, 30))
    return t


@tile
def dial_round():
    t = bg((232, 226, 205), 2)
    ticks(t, 16, 16, 10, 14, 225, -45, 13, (30, 30, 30))
    ticks(t, 16, 16, 10, 14, 0, -45, 3, (190, 40, 32))
    t[~disk(16, 16, 15.5)] = (22, 22, 22)
    return t


@tile
def edge():  # vertical edgewise meter scale
    t = bg((232, 226, 205), 2)
    for k in range(11):
        y = 3 + k * 2.6
        t[(yy >= y) & (yy < y + 1) & (xx < (14 if k % 5 == 0 else 9))] = (30, 30, 30)
    t[(yy > 25) & (xx < 4)] = (190, 40, 32)
    frame(t, (30, 30, 30))
    return t


@tile
def knob_scale():
    t = bg((28, 28, 28), 2)
    ticks(t, 16, 16, 11, 15, 225, -45, 7, (220, 220, 210), 1.1)
    return t


@tile
def galette():
    t = bg((28, 28, 28), 2)
    ticks(t, 16, 16, 11, 15, 210, -30, 6, (220, 220, 210), 0.9)
    return t


@tile
def digits():  # dim 7-segment "888" behind red glass, lit digits are drawn by the game
    t = bg((26, 8, 6), 1)
    seg = (70, 22, 14)
    for d in range(3):
        x0 = 4 + d * 9
        for (a, b, c_, e) in ((x0 + 1, 24, x0 + 6, 25), (x0 + 1, 15, x0 + 6, 16), (x0 + 1, 6, x0 + 6, 7)):
            t[(xx >= a) & (xx < c_) & (yy >= b) & (yy < e)] = seg
        for (a, b, e) in ((x0, 16, 24), (x0 + 6, 16, 24), (x0, 7, 15), (x0 + 6, 7, 15)):
            t[(xx >= a) & (xx < a + 1) & (yy >= b) & (yy < e)] = seg
        t[(xx >= x0 + 7.5) & (xx < x0 + 8.5) & (yy >= 5) & (yy < 6)] = seg
    return t


@tile
def annun():  # frosted window of an annunciator tile
    t = bg((206, 206, 196), 2)
    frame(t, (150, 150, 140), 2)
    return t


@tile
def lens():
    t = bg((200, 200, 200), 1)
    r = np.hypot(xx - 13, yy - 19)
    t *= np.clip(1.15 - r / 40, 0.6, 1.15)[..., None]
    return t


@tile
def crt():
    t = bg((12, 28, 16), 2)
    t[(np.mod(xx, 8) < 1) | (np.mod(yy, 8) < 1)] = (22, 58, 30)
    v = np.hypot(xx - 16, yy - 16)
    t *= np.clip(1.25 - v / 26, 0.5, 1)[..., None]
    return t


@big_tile
def ppi():  # radar scope face: dark phosphor and a vignette; rings, bearings and the sweep are drawn live by the game
    yb, xb = np.mgrid[0:2 * C, 0:2 * C] + 0.5
    t = np.ones((2 * C, 2 * C, 3)) * (9, 22, 12) + rng.integers(-2, 3, (2 * C, 2 * C, 1))
    rr = np.hypot(xb - 32, yb - 32)
    t *= np.clip(1.15 - rr / 40, 0.55, 1)[..., None]
    t[rr > 31] = (14, 14, 14)
    return t


@big_tile
def crt_unit():  # per-unit monitor: header line, elevation sector with a target, readiness bars, text lines
    n = 2 * C
    yb, xb = np.mgrid[0:n, 0:n] + 0.5
    t = np.ones((n, n, 3)) * (10, 26, 14) + rng.integers(-2, 3, (n, n, 1))
    ph, dim = (120, 240, 140), (40, 100, 55)
    t[(yb >= 56) & (yb < 59) & (xb >= 4) & (xb < 30)] = ph  # unit name line
    rr = np.hypot(xb - 6, yb - 18)
    ang = np.degrees(np.arctan2(yb - 18, xb - 6))
    sector = (ang >= 0) & (ang <= 80)
    for r in (12, 24, 36):
        t[sector & (abs(rr - r) < 0.5)] = dim
    for a_ in (0, 20, 40, 60, 80):
        t[(abs(ang - a_) * rr * math.pi / 180 < 0.5) & (rr < 36)] = dim
    t[(abs(xb - 30) < 1.3) & (abs(yb - 34) < 1.3)] = (220, 255, 200)  # target
    for k, h in enumerate((30, 18, 26, 10)):  # readiness / ammo bars
        x = 46 + k * 4
        t[(xb >= x) & (xb < x + 2) & (yb >= 14) & (yb < 14 + h)] = ph
        t[(xb >= x) & (xb < x + 2) & (yb >= 12) & (yb < 13)] = dim
    for row in range(2):
        t[(yb >= 4 + row * 4) & (yb < 6 + row * 4) & (xb >= 4) & (xb < 20 + row * 14)] = dim
    v = np.hypot((xb - 32) / 1.1, yb - 32)
    t *= np.clip(1.25 - v / 60, 0.55, 1)[..., None]  # tube vignette
    return t


@tile
def crt_text():  # amber character screen
    t = bg((26, 14, 6), 2)
    for row in range(6):
        y = 25 - row * 4
        for col in range(9):
            if rng.random() < 0.7:
                t[(yy >= y) & (yy < y + 2) & (xx >= 3 + col * 3) & (xx < 5 + col * 3)] = (240, 150, 50)
    v = np.hypot((xx - 16) / 1.2, yy - 16)
    t *= np.clip(1.2 - v / 30, 0.55, 1)[..., None]
    return t


@tile
def key_lit():  # blank milky key cap: the game writes the key's name on it
    t = bg((215, 210, 192), 2)
    frame(t, (160, 155, 140), 2)
    return t


@tile
def tv():
    return np.ones((C, C, 3)) * rng.integers(28, 60, (C, C, 1))


@tile
def recorder():
    t = bg((236, 232, 214), 2)
    t[np.mod(xx, 4) < 1] = (170, 190, 215)
    t[np.mod(yy, 6) < 1] = (215, 170, 170)
    trace = 16 + 6 * np.sin(yy / 4) + 3 * np.sin(yy / 1.7)
    t[abs(xx - trace) < 0.8] = (180, 30, 30)
    return t


@big_tile
def seis_paper():  # seismograph paper (64 px): faint time ticks, darker every 4th, a baseline; the game draws the trace
    yb, xb = np.mgrid[0:2 * C, 0:2 * C] + 0.5
    t = np.ones((2 * C, 2 * C, 3)) * (240, 237, 224) + rng.integers(-2, 3, (2 * C, 2 * C, 1))
    t[np.mod(xb, 8) < 1] = (222, 226, 232)
    t[np.mod(yb, 8) < 1] = (228, 230, 234)
    t[np.mod(xb, 32) < 1] = (196, 206, 220)
    t[abs(yb - 32) < 0.6] = (196, 206, 220)
    return t


@tile
def speaker():
    t = bg((52, 54, 54), 2)
    t[(np.mod(xx, 4) < 2) & (np.mod(yy, 4) < 2) & disk(16, 16, 14)] = (16, 16, 16)
    return t


@tile
def jack():
    t = bg((22, 22, 22), 1)
    for cx in (5, 12, 20, 27):
        t[disk(cx, 16, 3)] = (150, 154, 152)
        t[disk(cx, 16, 1.5)] = (5, 5, 5)
    return t


@tile
def clock():
    t = bg((236, 236, 230), 1)
    ticks(t, 16, 16, 12, 15, 90, -240, 12, (25, 25, 25))
    t[~disk(16, 16, 15.5)] = (22, 22, 22)
    return t


@tile
def label():  # engraved plate, the text itself is drawn by the game
    t = bg((26, 26, 26), 1)
    frame(t, (120, 120, 116))
    return t


@tile
def phone_dial():  # rotary dial: cream number ring with ten finger holes, white centre label
    t = bg((26, 26, 26), 1)
    t[disk(16, 16, 15)] = (40, 40, 40)
    for k in range(10):
        a = math.radians(60 + 28 * k)
        t[disk(16 + 10.5 * math.cos(a), 16 + 10.5 * math.sin(a), 2.6)] = (232, 226, 205)
    t[disk(16, 16, 5.5)] = (236, 236, 230)
    t[disk(16, 16, 1.2)] = (190, 40, 32)
    return t


@tile
def calendar():  # tear-off desk calendar page: red month bar, big black day number
    t = bg((238, 236, 226), 1)
    t[yy > 25] = (190, 40, 32)
    for x0, rows in ((7, ("###", "..#", "###", "#..", "###")), (18, ("###", "#..", "###", "#.#", "###"))):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    t[(xx >= x0 + i * 2.4) & (xx < x0 + i * 2.4 + 2.4) & (yy >= 20 - j * 3.2) & (yy < 20 - j * 3.2 + 3.2)] = (25, 25, 25)
    t[(yy > 2) & (yy < 3.5) & (xx > 6) & (xx < 26)] = (150, 150, 140)
    return t


import sys
sys.path.insert(0, R + "tools")
import gen_portraits
for _n in gen_portraits.HEADS:
    def _pic(n=_n):
        return np.flipud(gen_portraits.portrait(n)).astype(float)  # atlas rows run bottom-up
    _pic.__name__ = "portrait_" + _n
    tile(_pic)


bi = bpy.data.images.get("control_parts") or bpy.data.images.new("control_parts", S, S)
bi.scale(S, S)
bi.pixels.foreach_set((img / 255).ravel().astype(np.float32))
bi.filepath_raw = TEX
bi.file_format = "PNG"
bi.save()

pm = bpy.data.materials.get("parts_tex") or bpy.data.materials.new("parts_tex")
pm.use_nodes = True
tn = next((n for n in pm.node_tree.nodes if n.type == "TEX_IMAGE"), None) or pm.node_tree.nodes.new("ShaderNodeTexImage")
tn.image, tn.interpolation = bi, "Closest"
pb = next(n for n in pm.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
pm.node_tree.links.new(tn.outputs["Color"], pb.inputs["Base Color"])
pb.inputs["Roughness"].default_value = 0.6
pm.node_tree.links.new(tn.outputs["Alpha"], pb.inputs["Alpha"])


# ---------------- geometry ----------------
class El:
    """Collects primitives per part; material index < 64 = palette swatch, >= 64 = drawn tile on the +Z face."""

    def __init__(self, name, w, h, mount="any"):
        self.name, self.w, self.h, self.mount = name, w, h, mount
        self.parts, self.pivots = {}, {}
        self.rects = [None]  # tile rectangles shared by several faces (curved screens), index in face layer "rect"
        self.post = {}  # part -> Matrix (px) applied after the UVs: tiles go on +Z faces, this stands them up

    def bm(self, part):
        return self.parts.setdefault(part, bmesh.new())

    def _add(self, part, verts, faces, col, front, tf):
        bm = self.bm(part)
        vs = [bm.verts.new(tf @ Vector(v) if tf else Vector(v)) for v in verts]
        for f in faces:
            face = bm.faces.new([vs[i] for i in f])
            face.material_index = col
        bm.normal_update()
        if front is not None:  # the face pointing most along +Z gets the tile
            new = [f for f in bm.faces if all(v in vs for v in f.verts)]
            max(new, key=lambda f: f.normal.z).material_index = 64 + TILES[front]

    def box(self, x0, y0, z0, x1, y1, z1, col, part="", front=None, tf=None):
        v = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
        f = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
        self._add(part, v, f, col, front, tf)

    def cyl(self, cx, cy, r, z0, z1, col, n=8, part="", front=None, tf=None, r1=None):
        """Prism of n flat sides (r1: a different radius at z1, a cone frustum)."""
        a0 = math.pi / n  # flat side at the top/bottom, like NTM octagons
        ring = lambda rr: [(cx + rr * math.cos(a0 + 2 * math.pi * k / n), cy + rr * math.sin(a0 + 2 * math.pi * k / n)) for k in range(n)]
        v = [(x, y, z0) for x, y in ring(r)] + [(x, y, z1) for x, y in ring(r if r1 is None else r1)]
        f = [tuple(range(n - 1, -1, -1)), tuple(range(n, 2 * n))] + [(k, (k + 1) % n, n + (k + 1) % n, n + k) for k in range(n)]
        self._add(part, v, f, col, front, tf)

    def screen(self, x0, y0, x1, y1, z, bulge, front, part="screen", nx=6, ny=5):
        """Convex CRT glass: a grid bulging out by `bulge` at the centre, one tile stretched over all of it."""
        bm = self.bm(part)
        rl = bm.faces.layers.int.get("rect") or bm.faces.layers.int.new("rect")
        self.rects.append((x0, y0, x1, y1))
        vs = {}
        for j in range(ny + 1):
            for i in range(nx + 1):
                u, v = 2 * i / nx - 1, 2 * j / ny - 1
                vs[i, j] = bm.verts.new((x0 + (x1 - x0) * i / nx, y0 + (y1 - y0) * j / ny,
                                         z + bulge * (1 - u * u) * (1 - v * v)))
        for j in range(ny):
            for i in range(nx):
                f = bm.faces.new((vs[i, j], vs[i + 1, j], vs[i + 1, j + 1], vs[i, j + 1]))
                f.material_index = 64 + TILES[front]
                f[rl] = len(self.rects) - 1
        bm.normal_update()

    def frame(self, x0, y0, x1, y1, b, c, z0, z1, col, part=""):
        """Bezel around an opening inset by b with chamfered corners c (the rounded CRT mask, kept faceted)."""
        ix0, iy0, ix1, iy1 = x0 + b, y0 + b, x1 - b, y1 - b
        inner = [(ix0 + c, iy0), (ix1 - c, iy0), (ix1, iy0 + c), (ix1, iy1 - c), (ix1 - c, iy1), (ix0 + c, iy1),
                 (ix0, iy1 - c), (ix0, iy0 + c)]
        outer = [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c),
                 (x0, y0 + c)]
        for k in range(8):
            q = [inner[k], outer[k], outer[(k + 1) % 8], inner[(k + 1) % 8]]
            if k % 2 == 1:  # corner segment: fill the square corner of the outer rectangle as well
                q = [inner[k], outer[k], (x1 if k in (1, 3) else x0, y0 if k in (1, 7) else y1), outer[(k + 1) % 8],
                     inner[(k + 1) % 8]]
            self.prism(q, z0, z1, col, part)

    def photo(self, x0, y0, x1, y1, z0, z1, side, front, part=""):
        """Framed picture lying face up: box with the tile on top, its x 2..30 of 32 stretched over the face."""
        self.box(x0, y0, z0, x1, y1, z1, side, part)
        bm = self.bm(part)
        rl = bm.faces.layers.int.get("rect") or bm.faces.layers.int.new("rect")
        m = (x1 - x0) / 14
        self.rects.append((x0 - m, y0, x1 + m, y1))
        top = max((f for f in bm.faces if f.calc_center_median().z > z1 - 1e-4), key=lambda f: f.normal.z)
        top.material_index = 64 + TILES[front]
        top[rl] = len(self.rects) - 1

    def ring(self, cx, cy, r0, r1, z0, z1, col, n=8, part=""):
        """Bezel: n flat segments between inner radius r0 and outer r1 (octagon for n = 8)."""
        a0 = math.pi / n
        pt = lambda r, k: (cx + r * math.cos(a0 + 2 * math.pi * k / n), cy + r * math.sin(a0 + 2 * math.pi * k / n))
        for k in range(n):
            self.prism([pt(r0, k), pt(r1, k), pt(r1, k + 1), pt(r0, k + 1)], z0, z1, col, part)

    def prism(self, pts, z0, z1, col, part=""):
        n = len(pts)
        v = [(x, y, z0) for x, y in pts] + [(x, y, z1) for x, y in pts]
        f = [tuple(range(n - 1, -1, -1)), tuple(range(n, 2 * n))] + [(k, (k + 1) % n, n + (k + 1) % n, n + k) for k in range(n)]
        self._add(part, v, f, col, None, None)

    def build(self, at):
        objs = []
        for part, bm in self.parts.items():
            bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
            uv = bm.loops.layers.uv.verify()
            for f in bm.faces:
                mi = f.material_index
                if mi < 64:
                    u, v = (mi % 8) * 4 + 2, (mi // 8) * 4 + 2
                    for l in f.loops:
                        l[uv].uv = (u / S, v / S)
                else:
                    cx, cy, size = RECT[mi - 64]
                    xs = [l.vert.co.x for l in f.loops]
                    ys = [l.vert.co.y for l in f.loops]
                    rl = bm.faces.layers.int.get("rect")
                    if rl and f[rl]:
                        x0, y0, x1, y1 = self.rects[f[rl]]
                        xs, ys = [x0, x1], [y0, y1]
                    for l in f.loops:
                        fu = (l.vert.co.x - min(xs)) / max(1e-6, max(xs) - min(xs))
                        fv = (l.vert.co.y - min(ys)) / max(1e-6, max(ys) - min(ys))
                        l[uv].uv = ((cx + 0.25 + fu * (size - 0.5)) / S, (cy + 0.25 + fv * (size - 0.5)) / S)
                f.material_index = 0
            if part in self.post:
                bmesh.ops.transform(bm, matrix=self.post[part], verts=bm.verts[:])
            bmesh.ops.scale(bm, vec=(1 / 16, 1 / 16, 1 / 16), verts=bm.verts[:])
            nm = self.name + ("_" + part if part else "")
            me = bpy.data.meshes.new(nm)
            bm.to_mesh(me)
            bm.free()
            me.materials.append(pm)
            o = bpy.data.objects.new(nm, me)
            o["element"] = self.name
            o["size"] = (self.w, self.h)
            o["mount"] = self.mount  # "top": only on level surfaces (desk tops), Z = world up
            if part in self.pivots:
                o["pivot"] = self.pivots[part]
            o.location = at
            bpy.context.collection.objects.link(o)
            objs.append(o)
        return objs


def rot(axis, deg, pivot):
    p = Vector(pivot)
    return Matrix.Translation(p) @ Matrix.Rotation(math.radians(deg), 4, axis) @ Matrix.Translation(-p)


ELEMENTS = []


def element(w, h, mount="any"):
    def deco(fn):
        e = El(fn.__name__, w, h, mount)
        fn(e)
        ELEMENTS.append(e)
        return fn
    return deco


# ---- buttons ----
@element(2, 2)
def btn_square(e):
    e.box(0, 0, 0, 2, 2, 0.4, DGREY)
    e.box(0.25, 0.25, 0.4, 1.75, 1.75, 1.2, WHITE, "cap")


@element(2, 2)
def btn_round(e):
    e.cyl(1, 1, 1, 0, 0.4, DGREY)
    e.cyl(1, 1, 0.7, 0.4, 1.2, WHITE, part="cap")


@element(3, 3)
def btn_mushroom(e):  # emergency stop: thin yellow plate, black guard collar, low red mushroom head
    e.box(0, 0, 0, 3, 3, 0.12, YELLOW)
    e.ring(1.5, 1.5, 0.8, 0.95, 0.12, 0.6, BLACK, 12)
    e.cyl(1.5, 1.5, 0.8, 0.12, 0.2, BLACK, 12)
    e.cyl(1.5, 1.5, 0.35, 0.2, 0.7, DGREY, 8, part="cap")
    e.cyl(1.5, 1.5, 0.8, 0.7, 0.95, DRED, 12, part="cap")
    e.cyl(1.5, 1.5, 0.65, 0.95, 1.02, RED, 12, part="cap")


@element(2, 2)
def btn_lit(e):
    e.box(0, 0, 0, 2, 2, 0.5, BLACK)
    e.box(0.3, 0.3, 0.5, 1.7, 1.7, 1.1, WHITE, "cap", "lens")


@element(3, 3)
def btn_guarded(e):
    e.box(0, 0, 0, 3, 3, 0.3, DGREY)
    for b in ((0, 0, 3, 0.4), (0, 2.6, 3, 3), (0, 0.4, 0.4, 2.6), (2.6, 0.4, 3, 2.6)):
        e.box(b[0], b[1], 0.3, b[2], b[3], 1.6, DGREY)
    e.box(0.9, 0.9, 0.3, 2.1, 2.1, 1.0, WHITE, "cap")


@element(2, 4)
def btn_pair(e):
    e.box(0, 0, 0, 2, 4, 0.3, BLACK)
    e.box(0.4, 2.4, 0.3, 1.6, 3.6, 1.0, GREEN, "cap_a")
    e.box(0.4, 0.4, 0.3, 1.6, 1.6, 1.0, RED, "cap_b")


@element(4, 5)
def keypad(e):
    e.box(0, 0, 0, 4, 5, 0.4, DGREY)
    for j in range(4):
        for i in range(3):
            x, y = 0.35 + i * 1.2, 0.3 + j * 1.15
            e.box(x, y, 0.4, x + 0.9, y + 0.9, 0.9, LGREY, f"key{j * 3 + i}")


# ---- switches ----
@element(2, 3)
def toggle(e):
    e.box(0, 0, 0, 2, 3, 0.2, DGREY)
    e.cyl(1, 1.5, 0.6, 0.2, 0.7, CHROME, 6)
    e.box(0.85, 1.35, 0.7, 1.15, 1.65, 2.4, CHROME, "lever", tf=rot("X", -20, (1, 1.5, 0.7)))
    e.box(0.81, 1.31, 1.75, 1.19, 1.69, 2.15, WHITE, "lever_tint", tf=rot("X", -20, (1, 1.5, 0.7)))
    e.pivots["lever"] = e.pivots["lever_tint"] = (1, 1.5, 0.7)


@element(3, 4)
def toggle_covered(e):  # launch toggle under a small square flip guard of milky clear plastic
    e.box(0, 0, 0, 3, 4, 0.2, DGREY)
    e.cyl(1.5, 1.7, 0.45, 0.2, 0.55, CHROME, 6)
    e.box(1.39, 1.59, 0.55, 1.61, 1.81, 1.35, CHROME, "lever", tf=rot("X", -20, (1.5, 1.7, 0.55)))
    e.box(1.35, 1.55, 0.95, 1.65, 1.85, 1.2, WHITE, "lever_tint", tf=rot("X", -20, (1.5, 1.7, 0.55)))
    e.pivots["lever"] = e.pivots["lever_tint"] = (1.5, 1.7, 0.55)
    e.box(0.6, 3.0, 0.2, 2.4, 3.4, 0.55, CHROME)  # hinge
    for b in ((0.4, 0.8, 1.45, 2.6, 3.0, 1.55),   # square lid, 2.2 x 2.2 px
              (0.4, 0.8, 0.2, 0.5, 3.0, 1.45), (2.5, 0.8, 0.2, 2.6, 3.0, 1.45),
              (0.5, 0.8, 0.2, 2.5, 0.9, 1.45)):
        e.box(*b, GLASS, "cover")
    e.pivots["cover"] = (1.5, 3.2, 0.55)


@element(2, 3)
def rocker(e):
    e.box(0, 0, 0, 2, 3, 0.4, BLACK)
    e.box(0.3, 0.3, 0.4, 1.7, 2.7, 0.9, DGREY, "rocker", tf=rot("X", 8, (1, 1.5, 0.4)))
    e.box(0.5, 2.0, 0.9, 1.5, 2.4, 0.95, WHITE, "rocker_tint", tf=rot("X", 8, (1, 1.5, 0.4)))
    e.pivots["rocker"] = e.pivots["rocker_tint"] = (1, 1.5, 0.4)


@element(3, 3)
def control_key(e):  # NPP-style control key: a bar handle turned left/right
    e.box(0, 0, 0, 3, 3, 0.1, BLACK, front="knob_scale")
    e.cyl(1.5, 1.5, 0.9, 0.1, 0.5, BLACK, part="handle")
    e.box(0.5, 1.15, 0.5, 2.5, 1.85, 1.5, BLACK, "handle")
    e.box(1.6, 1.35, 1.5, 2.4, 1.65, 1.57, WHITE, "handle_tint")
    e.pivots["handle"] = e.pivots["handle_tint"] = (1.5, 1.5, 0)


@element(3, 3)
def knob_pointer(e):
    e.box(0, 0, 0, 3, 3, 0.1, BLACK, front="knob_scale")
    e.cyl(1.5, 1.5, 1.0, 0.1, 1.0, BLACK, part="knob")
    e.box(1.3, 1.4, 1.0, 1.7, 2.5, 1.25, BLACK, "knob")
    e.box(1.4, 1.6, 1.25, 1.6, 2.45, 1.32, WHITE, "knob_tint")
    e.pivots["knob"] = e.pivots["knob_tint"] = (1.5, 1.5, 0)


@element(2, 2)
def knob_small(e):
    e.cyl(1, 1, 0.75, 0, 0.9, BLACK, part="knob")
    e.box(0.9, 1.1, 0.9, 1.1, 1.7, 0.97, WHITE, "knob_tint")
    e.pivots["knob"] = e.pivots["knob_tint"] = (1, 1, 0)


@element(4, 4)
def galette_switch(e):
    e.box(0, 0, 0, 4, 4, 0.1, BLACK, front="galette")
    e.cyl(2, 2, 1.1, 0.1, 0.6, BLACK, part="knob")
    e.box(1.6, 0.6, 0.6, 2.4, 3.4, 1.6, BLACK, "knob")
    e.box(1.8, 1.0, 1.6, 2.2, 3.3, 1.68, WHITE, "knob_tint")  # colour stripe along the bar
    e.pivots["knob"] = e.pivots["knob_tint"] = (2, 2, 0)


@element(3, 3)
def key_switch(e):
    e.box(0, 0, 0, 3, 3, 0.2, CHROME)
    e.cyl(1.5, 1.5, 1.0, 0.2, 0.8, CHROME)
    e.box(1.4, 1.0, 0.8, 1.6, 2.0, 1.6, BRASS, "key")
    e.box(0.8, 1.3, 1.6, 2.2, 1.7, 2.8, BRASS, "key")
    e.pivots["key"] = (1.5, 1.5, 0)


@element(2, 5)
def slider(e):
    e.box(0, 0, 0, 2, 5, 0.2, BLACK)
    e.box(0.85, 0.4, 0.2, 1.15, 4.6, 0.25, DGREY)
    e.box(0.4, 2.2, 0.25, 1.6, 2.8, 1.0, MGREY, "knob")


# ---- indicators ----
@element(2, 2)
def lamp_round(e):
    e.cyl(1, 1, 1, 0, 0.5, CHROME)
    e.cyl(1, 1, 0.7, 0.5, 1.0, WHITE, part="lens", front="lens")


@element(2, 2)
def lamp_square(e):
    e.box(0, 0, 0, 2, 2, 0.4, BLACK)
    e.box(0.3, 0.3, 0.4, 1.7, 1.7, 0.8, WHITE, "lens", "lens")


@element(4, 2)
def annunciator(e):
    e.box(0, 0, 0, 4, 2, 0.6, BLACK)
    e.box(0.2, 0.2, 0.6, 3.8, 1.8, 0.7, WHITE, "lens", "annun")


@element(8, 4)
def annunciator_bank(e):
    e.box(0, 0, 0, 8, 4, 0.6, BLACK)
    for j in range(3):
        for i in range(4):
            x, y = 0.2 + i * 1.95, 0.2 + j * 1.25
            e.box(x, y, 0.6, x + 1.75, y + 1.05, 0.7, WHITE, f"lens{j * 4 + i}", "annun")


@element(6, 2)
def display_digits(e):
    e.box(0, 0, 0, 6, 2, 0.5, BLACK)
    e.box(0.3, 0.3, 0.5, 5.7, 1.7, 0.55, BLACK, front="digits")


@element(4, 2)
def counter(e):
    e.box(0, 0, 0, 4, 2, 0.8, DGREY)
    e.box(0.4, 0.4, 0.8, 3.6, 1.6, 0.85, BLACK, front="digits")


def bezel(e, w, h, z0, z1, b, col):
    for q in ((0, 0, w, b), (0, h - b, w, h), (0, b, b, h - b), (w - b, b, w, h - b)):
        e.box(q[0], q[1], z0, q[2], q[3], z1, col)


@element(4, 4)
def gauge_square(e):
    e.box(0, 0, 0, 4, 4, 0.6, BLACK)
    e.box(0.4, 0.4, 0.6, 3.6, 3.6, 0.65, CREAM, front="dial_sq")
    bezel(e, 4, 4, 0.6, 0.9, 0.4, BLACK)
    e.box(1.95, 1.0, 0.66, 2.05, 3.2, 0.7, BLACK, "needle")
    e.pivots["needle"] = (2, 1.0, 0.66)


@element(4, 4)
def gauge_round(e):
    e.cyl(2, 2, 2, 0, 0.8, BLACK, 12)
    e.cyl(2, 2, 1.65, 0.8, 0.85, CREAM, 12, front="dial_round")
    e.box(1.95, 2, 0.86, 2.05, 3.5, 0.9, BLACK, "needle")
    e.pivots["needle"] = (2, 2, 0.86)


@element(2, 6)
def gauge_edgewise(e):
    e.box(0, 0, 0, 2, 6, 0.8, BLACK)
    e.box(0.3, 0.3, 0.8, 1.7, 5.7, 0.85, CREAM, front="edge")
    e.box(0.3, 2.9, 0.86, 1.2, 3.1, 0.9, RED, "pointer")


@element(5, 5)
def recorder(e):
    e.box(0, 0, 0, 5, 5, 1.2, LGREY)
    e.box(0.5, 1.2, 1.2, 4.5, 4.5, 1.25, CREAM, front="recorder")
    e.box(0.5, 0.3, 1.2, 4.5, 0.9, 1.4, BLACK)
    e.box(2.0, 2.8, 1.26, 2.2, 3.0, 1.35, RED, "pen")


def monitor(e, x, y, w, h, z, part="screen", front="crt_unit"):
    """CRT set into the panel: hood, faceted mask, convex glass sunk behind the mask."""
    e.box(x, y, 0, x + w, y + h, z, LGREY)
    e.frame(x, y, x + w, y + h, 0.55, 0.6, z, z + 0.7, LGREY)
    e.screen(x + 0.45, y + 0.45, x + w - 0.45, y + h - 0.45, z + 0.05, 0.55, front, part)


@element(4, 10)
def readiness(e):  # one air defence unit's readiness column: name plate, five annunciators, missiles left
    e.box(0, 0, 0, 4, 10, 0.5, DGREY)
    e.box(0.3, 8.7, 0.5, 3.7, 9.7, 0.6, BLACK, front="label")
    for k in range(5):
        y = 7.3 - k * 1.3
        e.box(0.5, y, 0.5, 3.5, y + 1.0, 0.8, WHITE, f"lens{k}", "annun")
    e.box(0.7, 0.4, 0.5, 3.3, 1.5, 0.55, BLACK, front="digits")


@element(12, 10)
def crt_bank(e):  # 2 x 2 monitors, one per air defence unit
    for j in range(2):
        for i in range(2):
            monitor(e, i * 6, j * 5, 6, 5, 0.8, f"screen{j * 2 + i}")


@element(6, 5)
def crt_small(e):
    e.box(0, 0, 0, 6, 1.0, 1.5, LGREY)
    monitor(e, 0, 1.0, 6, 4, 1.5, front="crt")
    for x in (1.0, 1.8, 4.6):
        e.cyl(x, 0.45, 0.25, 1.5, 1.8, BLACK, 6)


@element(12, 12)
def crt_ppi(e):  # round radar indicator in a deep octagonal hood
    e.box(0, 0, 0, 12, 12, 0.6, LGREY)
    e.ring(6, 6, 5.2, 6.4, 0.6, 2.4, LGREY)
    e.ring(6, 6, 5.0, 5.3, 0.6, 2.0, DGREY, 16)
    e.cyl(6, 6, 5.0, 0.6, 0.7, BLACK, 16, front="ppi")
    for x, y in ((0.7, 0.7), (11.3, 0.7), (0.7, 11.3), (11.3, 11.3)):
        e.cyl(x, y, 0.3, 0.6, 0.8, CHROME, 6)


@element(8, 8)
def crt_ppi_desk(e):  # radar indicator sized for the desk slope: shallower octagonal hood
    e.box(0, 0, 0, 8, 8, 0.4, LGREY)
    e.ring(4, 4, 3.45, 4.25, 0.4, 1.5, LGREY)
    e.ring(4, 4, 3.3, 3.5, 0.4, 1.2, DGREY, 16)
    e.cyl(4, 4, 3.3, 0.4, 0.5, BLACK, 16, front="ppi")
    for x, y in ((0.55, 0.55), (7.45, 0.55), (0.55, 7.45), (7.45, 7.45)):
        e.cyl(x, y, 0.25, 0.4, 0.55, CHROME, 6)


@element(5, 7)
def crt_tall(e):  # portrait character screen
    monitor(e, 0, 0, 5, 7, 0.4, front="crt_text")


@element(8, 2)
def key_strip(e):  # row of lit keys
    e.box(0, 0, 0, 8, 2, 0.3, DGREY)
    for i in range(6):
        x = 0.3 + i * 1.25
        e.box(x, 0.4, 0.3, x + 1.1, 1.6, 0.8, CREAM, f"key{i}", "key_lit")


@element(6, 6)
def lit_matrix(e):  # 4 x 4 field of lit push-buttons
    e.box(0, 0, 0, 6, 6, 0.4, DGREY)
    for j in range(4):
        for i in range(4):
            x, y = 0.4 + i * 1.35, 0.4 + j * 1.35
            e.box(x, y, 0.4, x + 1.15, y + 1.15, 0.9, CREAM, f"key{j * 4 + i}", "key_lit")


@element(4, 4)
def joystick(e):  # optoelectronic station control stick: base, rubber boot, grip with a tinted thumb cap
    e.box(0, 0, 0, 4, 4, 0.4, DGREY)
    e.cyl(2, 2, 1.1, 0.4, 0.9, BLACK, 8)
    e.box(1.7, 1.7, 0.9, 2.3, 2.3, 3.0, BLACK, "stick")
    e.box(1.5, 1.5, 3.0, 2.5, 2.5, 4.2, DGREY, "stick")
    e.box(1.6, 1.6, 4.2, 2.4, 2.4, 4.45, WHITE, "stick_tint")
    e.pivots["stick"] = e.pivots["stick_tint"] = (2, 2, 0.9)


@element(8, 6)
def tv_screen(e):  # TV channel: the seeker / gun camera picture of the bound launcher, a channel switch below
    e.box(0, 0, 0, 8, 1.0, 1.2, DGREY)
    monitor(e, 0, 1.0, 8, 5, 1.2, front="tv")
    for x in (1.0, 6.8):
        e.cyl(x, 0.5, 0.28, 1.2, 1.6, BLACK, 6)


# ---- plates and fittings ----
@element(4, 1)
def label_small(e):
    e.box(0, 0, 0, 4, 1, 0.15, BLACK, front="label")


@element(8, 2)
def label_large(e):
    e.box(0, 0, 0, 8, 2, 0.15, BLACK, front="label")


@element(4, 4)
def trackball(e):  # ball in a round well, tinted (yellow / red)
    e.box(0, 0, 0, 4, 4, 0.3, DGREY)
    e.ring(2, 2, 1.25, 1.9, 0.3, 0.9, BLACK, 12)
    e.cyl(2, 2, 1.25, 0.3, 0.4, BLACK, 12)
    e.cyl(2, 2, 1.1, 0.45, 0.9, WHITE, 12, part="ball")
    e.cyl(2, 2, 0.75, 0.9, 1.2, WHITE, 12, part="ball")


@element(5, 3)
def speaker(e):  # grille on the left, volume knob (like knob_small) on the right
    e.box(0, 0, 0, 3, 3, 0.3, DGREY, front="speaker")
    e.box(3, 0, 0, 5, 3, 0.3, DGREY)
    e.cyl(4, 1.5, 0.75, 0.3, 1.2, BLACK, part="knob")
    e.box(3.9, 1.6, 1.2, 4.1, 2.4, 1.27, WHITE, "knob_tint")
    e.pivots["knob"] = e.pivots["knob_tint"] = (4, 1.5, 0)


@element(4, 2)
def jack_panel(e):
    e.box(0, 0, 0, 4, 2, 0.3, BLACK, front="jack")


@element(1, 2)
def fuse_holder(e):
    e.box(0.15, 0, 0, 0.85, 2, 0.4, BLACK)
    e.box(0.3, 0.9, 0.4, 0.7, 1.9, 0.8, MGREY)


@element(4, 4)
def clock(e):
    e.box(0, 0, 0, 4, 4, 0.6, BLACK)
    e.box(0.3, 0.3, 0.6, 3.7, 3.7, 0.65, WHITE, front="clock")
    e.box(1.95, 2, 0.66, 2.05, 2.9, 0.7, BLACK, "hour")
    e.box(1.97, 2, 0.7, 2.03, 3.4, 0.74, BLACK, "minute")
    e.pivots["hour"] = e.pivots["minute"] = (2, 2, 0.66)


@element(3, 8)
def handset(e):
    e.box(0, 0, 0, 3, 8, 0.6, DGREY)
    e.box(0.3, 6.0, 0.6, 2.7, 7.7, 1.6, BLACK, "handset")
    e.box(0.9, 2.0, 0.9, 2.1, 6.0, 1.6, BLACK, "handset")
    e.box(0.3, 0.3, 0.6, 2.7, 2.0, 1.6, BLACK, "handset")


@element(2, 2)
def microphone(e):
    e.cyl(1, 1, 0.9, 0, 0.4, BLACK)
    e.box(0.85, 0.85, 0.4, 1.15, 1.15, 3.0, MGREY)
    e.box(0.85, 0.85, 3.0, 1.15, 1.15, 5.0, MGREY, tf=rot("X", -35, (1, 1, 3.0)))
    e.box(0.7, 2.0, 4.4, 1.3, 2.6, 5.6, BLACK, tf=rot("X", -35, (1, 1, 3.0)))


@element(4, 5, mount="top")
def desk_lamp(e):  # old Soviet desk lamp: heavy base with a push switch, stem, arm, green cone shade, lit bulb
    e.cyl(2, 3.4, 1.5, 0, 0.5, DGREY)
    e.cyl(2, 3.4, 1.05, 0.5, 0.8, DGREY)
    e.box(1.75, 2.45, 0.8, 2.25, 2.85, 1.05, BLACK, "switch")
    e.box(1.85, 3.6, 0.8, 2.15, 3.9, 7.0, CHROME)
    e.box(1.7, 3.5, 6.8, 2.3, 4.0, 7.3, DGREY)  # hinge
    e.box(1.88, 1.6, 6.9, 2.12, 3.75, 7.1, CHROME, tf=rot("X", 13, (2, 3.75, 7.0)))
    shade = rot("X", -30, (2, 1.6, 6.5))
    e.cyl(2, 1.6, 1.3, 5.0, 6.5, ENAMEL, tf=shade, r1=0.45)
    e.cyl(2, 1.6, 0.7, 4.85, 5.0, WHITE, part="bulb", tf=shade)


@element(8, 8)
def seismograph(e):  # paper under glass scrolling left, pen on a swinging arm, amplitude meter, magnitude display, alarm lamp
    e.box(0, 0, 0, 8, 8, 1.4, LGREY)
    e.box(0.6, 7.0, 1.4, 5.6, 7.7, 1.5, BLACK, front="label")
    for q in ((0.6, 1.6, 5.6, 1.9), (0.6, 6.5, 5.6, 6.8), (0.6, 1.9, 0.9, 6.5), (5.3, 1.9, 5.6, 6.5)):
        e.box(q[0], q[1], 1.4, q[2], q[3], 1.75, DGREY)
    e.box(0.9, 1.9, 1.4, 5.3, 6.5, 1.45, CREAM, "paper", "seis_paper")
    e.cyl(6.6, 4.2, 0.4, 1.4, 1.8, DGREY)
    e.box(5.1, 4.13, 1.52, 6.7, 4.27, 1.6, CHROME, "pen")
    e.box(5.0, 4.05, 1.46, 5.18, 4.35, 1.6, RED, "pen")
    e.pivots["pen"] = (6.6, 4.2, 1.55)
    e.box(6.1, 0.8, 1.4, 7.5, 3.5, 1.6, BLACK)
    e.box(6.3, 1.0, 1.6, 7.3, 3.3, 1.65, CREAM, front="edge")
    e.box(6.3, 2.05, 1.66, 7.0, 2.25, 1.7, RED, "pointer")
    e.cyl(6.85, 6.0, 0.55, 1.4, 1.7, CHROME)
    e.cyl(6.85, 6.0, 0.4, 1.7, 1.95, WHITE, part="lens", front="lens")
    e.box(0.6, 0.35, 1.4, 4.4, 1.3, 1.5, BLACK)
    e.box(0.8, 0.5, 1.5, 4.2, 1.15, 1.53, BLACK, front="digits")


# ---- desk things (level desk tops only; low Y faces the operator) ----
@element(5, 3, mount="top")
def portrait(e):  # framed leader leaning back 12 degrees on a strut; a click puts up the next one (pic0..pic4)
    stand_up = Matrix.Translation((0, 0.6, 0)) @ Matrix.Rotation(math.radians(78), 4, "X")
    e.box(0.75, 0, 0, 4.25, 4, 0.35, GILT)
    e.post[""] = stand_up
    for k, name in enumerate(gen_portraits.HEADS):  # same order as ConsoleCatalog.LEADERS
        e.photo(0.75, 0, 4.25, 4, 0.35, 0.36, GILT, "portrait_" + name, part=f"pic{k}")
        e.post[f"pic{k}"] = stand_up
    e.box(2.2, 2.3, 0, 2.8, 2.9, 0.2, GILT, "stand")
    e.box(2.35, 2.45, 0.2, 2.65, 2.75, 3.1, GILT, "stand", tf=rot("X", 25, (2.5, 2.6, 0.2)))


@element(6, 6, mount="top")
def desk_phone(e):  # Soviet rotary desk phone: sloped body, dial facing the operator, handset on the cradle
    e.box(0.3, 0.3, 0, 5.7, 5.4, 1.4, BLACK)
    e.box(0.8, 2.9, 1.4, 5.2, 5.0, 2.2, BLACK)
    e.box(1.9, 0.6, 1.4, 4.1, 2.6, 1.42, BLACK, "dial", "phone_dial", tf=rot("X", 28, (3, 0.6, 1.4)))
    e.box(0.4, 3.0, 2.2, 1.1, 4.6, 2.9, DGREY)
    e.box(4.9, 3.0, 2.2, 5.6, 4.6, 2.9, DGREY)
    e.box(0.2, 3.3, 2.9, 5.8, 4.3, 3.5, BLACK, "handset")
    e.box(0.0, 3.1, 2.5, 1.3, 4.5, 3.2, BLACK, "handset")
    e.box(4.7, 3.1, 2.5, 6.0, 4.5, 3.2, BLACK, "handset")


@element(2, 2, mount="top")
def tea_glass(e):  # glass of tea in a metal holder with a handle
    e.cyl(1, 1, 0.8, 0, 0.3, CHROME)
    e.cyl(1, 1, 0.75, 0.3, 2.6, TEA)
    e.cyl(1, 1, 0.82, 0.3, 1.6, CHROME, n=8, r1=0.86)
    e.box(1.75, 0.85, 0.6, 2.05, 1.15, 2.0, CHROME)
    e.cyl(1, 1, 0.6, 2.6, 2.62, BRASS)  # tea surface
    e.box(0.9, 0.95, 2.62, 1.0, 1.05, 3.8, CHROME)  # spoon


@element(3, 3, mount="top")
def ashtray(e):  # heavy glass ashtray with a couple of butts
    e.cyl(1.5, 1.5, 1.4, 0, 0.6, GLASS)
    e.cyl(1.5, 1.5, 1.0, 0.3, 0.31, DGREY)
    e.box(0.7, 1.2, 0.31, 1.8, 1.5, 0.6, CREAM)
    e.box(1.8, 1.2, 0.31, 2.1, 1.5, 0.6, ORANGE)
    e.box(1.5, 1.9, 0.31, 1.8, 2.7, 0.6, CREAM, tf=rot("Z", 30, (1.6, 2.3, 0)))


@element(7, 8, mount="top")
def folders(e):  # stack of document folders, the top one tied with string
    for k, (dx, dy, col) in enumerate(((0.3, 0.2, BAKELITE), (0.0, 0.5, CREAM), (0.5, 0.1, NAVY), (0.1, 0.4, CRIMSON))):
        z = k * 0.45
        e.box(dx, dy, z, dx + 6.4, dy + 7.4, z + 0.3, col)
        e.box(dx + 0.2, dy + 0.2, z + 0.3, dx + 6.2, dy + 7.2, z + 0.45, CREAM)
    e.box(0.1, 4.0, 1.8, 6.5, 4.15, 1.9, WHITE)
    e.box(3.2, 0.4, 1.8, 3.35, 7.8, 1.9, WHITE)


@element(4, 3, mount="top")
def carafe(e):  # water carafe with a glass, on a small tray
    e.box(0, 0, 0, 4, 3, 0.2, CHROME)
    e.cyl(1.4, 1.5, 1.1, 0.2, 2.6, GLASS, n=8)
    e.cyl(1.4, 1.5, 1.1, 2.6, 3.4, GLASS, n=8, r1=0.4)
    e.cyl(1.4, 1.5, 0.4, 3.4, 4.6, GLASS, n=8)
    e.cyl(1.4, 1.5, 0.5, 4.6, 5.0, GLASS, n=8)
    e.cyl(3.2, 1.5, 0.55, 0.2, 2.0, GLASS, n=8)


@element(3, 3, mount="top")
def calendar(e):  # tear-off desk calendar on a slanted wooden stand
    e.box(0, 0, 0, 3, 3, 0.4, BAKELITE)
    e.box(0.3, 0, 0, 2.7, 2.5, 0.25, CREAM, "page", "calendar")
    e.post["page"] = Matrix.Translation((0, 0.8, 0.4)) @ Matrix.Rotation(math.radians(65), 4, "X")


# ---------------- layout for review ----------------
cols = 8
for i, e in enumerate(ELEMENTS):
    e.build((20 + (i % cols) * 0.9, -(i // cols) * 0.9, 0))
print(len(ELEMENTS), "elements")
