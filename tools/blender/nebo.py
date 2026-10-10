
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
for m in list(bpy.data.materials):
    bpy.data.materials.remove(m)

M_PAD = mat("pad", grime((116, 114, 107)))
M_OLIVE = mat("olive", camo())
M_OLIVE_D = mat("olive_dark", grime((61, 66, 56), scale=4.0, streak=False))
M_STEEL = mat("steel", grime((70, 74, 78), scale=6.0, streak=False))
M_DARK = mat("dark", rgb((40, 43, 42)))
M_HAZARD = mat("hazard", hazard)


def beam(name, a, b, t, m):
    """Square bar of side t from point a to point b."""
    a, b = Vector(a), Vector(b)
    d = b - a
    o = cube(name, (-t / 2, -t / 2, 0), (t / 2, t / 2, d.length), m)
    rot = d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4()
    o.data.transform(Matrix.Translation(a) @ rot)
    return o


def lattice(name, x0, x1, y0, y1, z0, z1, bays, leg, brace, m):
    """Square lattice tower: corner legs, a ring at each bay, X braces on all four sides."""
    ps = []
    cs = [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
    for x, y in cs:
        ps.append(beam(name, (x, y, z0), (x, y, z1), leg, m))
    for i in range(bays + 1):
        z = z0 + (z1 - z0) * i / bays
        for k in range(4):
            ps.append(beam(name, (*cs[k], z), (*cs[(k + 1) % 4], z), brace, m))
    for i in range(bays):
        za, zb = z0 + (z1 - z0) * i / bays, z0 + (z1 - z0) * (i + 1) / bays
        for k in range(4):
            (ax, ay), (bx, by) = cs[k], cs[(k + 1) % 4]
            ps.append(beam(name, (ax, ay, za), (bx, by, zb), brace, m))
            ps.append(beam(name, (bx, by, za), (ax, ay, zb), brace, m))
    return ps


# ---------------- Base (static) ----------------
base = []
base.append(cube("pad", (-2.5, -2.5, 0), (2.5, 2.5, 0.3), M_PAD))
base.append(cube("plinth", (-0.75, -0.75, 0.3), (0.75, 0.75, 0.6), M_PAD))
# equipment shelter behind the mast
base.append(cube("shelter", (-2.2, 0.9, 0.3), (1.6, 2.35, 2.2), M_OLIVE))
base.append(cube("shroof", (-2.3, 0.8, 2.2), (1.7, 2.45, 2.3), M_OLIVE_D))
for x0 in (-1.9, -0.9):
    base.append(cube("door", (x0, 0.86, 0.45), (x0 + 0.75, 0.9, 1.95), M_OLIVE_D))
    base.append(cube("handle", (x0 + 0.6, 0.82, 1.1), (x0 + 0.66, 0.86, 1.3), M_STEEL))
base.append(cube("vent", (0.2, 0.86, 1.4), (1.3, 0.9, 1.9), M_DARK))
for x in (0.35, 0.6, 0.85, 1.1):
    base.append(cube("louver", (x, 0.83, 1.42), (x + 0.12, 0.87, 1.88), M_OLIVE_D))
base.append(cube("aircon", (1.6, 1.1, 0.6), (2.15, 2.1, 1.5), M_OLIVE_D))
base.append(cube("cable", (-0.2, 0.3, 0.3), (0.2, 0.9, 0.42), M_DARK))
# generator cabinet and cable drum at the front corner
base.append(cube("genset", (-2.3, -2.3, 0.3), (-1.0, -1.4, 1.3), M_OLIVE))
base.append(cube("genvent", (-2.2, -2.34, 0.8), (-1.1, -2.3, 1.2), M_DARK))
base.append(cyl("drum", 0.35, -0.25, 0.25, M_STEEL, verts=10, axis="X", at=(1.7, -1.8, 0.65)))
# guard rail posts along the front edge
for x in (-0.5, 0.5, 1.5):
    base.append(cube("post", (x - 0.03, -2.42, 0.3), (x + 0.03, -2.36, 1.1), M_STEEL))
base.append(cube("rail", (-0.53, -2.42, 1.05), (1.53, -2.36, 1.1), M_HAZARD))
# lattice mast and the fixed half of the turntable
base += lattice("mast", -0.4, 0.4, -0.4, 0.4, 0.6, 4.4, 6, 0.1, 0.045, M_OLIVE)
base.append(cube("mastcap", (-0.5, -0.5, 4.4), (0.5, 0.5, 4.55), M_OLIVE_D))
base.append(cyl("ring", 0.45, 4.55, 4.75, M_STEEL, verts=12))
join("Base", base)

# ---------------- Antenna (turns about the Z axis) ----------------
ant = []
ant.append(cyl("rotor", 0.42, 4.75, 4.95, M_OLIVE_D, verts=12))
ant.append(cube("head", (-0.45, -0.35, 4.95), (0.45, 0.55, 5.6), M_OLIVE))
ant.append(cube("drive", (-0.3, 0.55, 5.0), (0.3, 0.85, 5.45), M_OLIVE_D))
# central lattice column carrying the array
ant += lattice("col", -0.22, 0.22, -0.55, -0.15, 5.6, 8.6, 5, 0.08, 0.04, M_OLIVE)
# array: vertical booms in front, two horizontal truss girders holding them
AY = -0.65                     # boom plane (rods point to -Y from here)
W, ZB, ZT = 4.1, 5.15, 8.5     # half width, bottom and top of the booms
for z in (5.7, 7.9):
    ant.append(beam("girder", (-W, AY + 0.15, z), (W, AY + 0.15, z), 0.12, M_OLIVE))
    ant.append(beam("girder", (-W, AY + 0.45, z + 0.25), (W, AY + 0.45, z + 0.25), 0.08, M_OLIVE))
    for i in range(8):  # zig-zag web between the two chords
        xa, xb = -W + 2 * W * i / 8, -W + 2 * W * (i + 1) / 8
        ant.append(beam("web", (xa, AY + 0.15, z), (xb, AY + 0.45, z + 0.25), 0.04, M_OLIVE))
# struts from the head out to the girders
for s in (-1, 1):
    ant.append(beam("strut", (s * 0.4, 0.3, 5.5), (s * 2.6, AY + 0.15, 5.7), 0.08, M_OLIVE))
    ant.append(beam("strut", (s * 0.2, -0.3, 8.4), (s * 2.6, AY + 0.15, 7.9), 0.06, M_OLIVE))
N_BOOMS, N_YAGI = 14, 6
for i in range(N_BOOMS):
    x = -W + 0.1 + (2 * W - 0.2) * i / (N_BOOMS - 1)
    ant.append(cube("boom", (x - 0.05, AY - 0.05, ZB), (x + 0.05, AY + 0.05, ZT), M_OLIVE))
    for j in range(N_YAGI):
        z = ZB + 0.3 + (ZT - ZB - 0.6) * j / (N_YAGI - 1)
        # wave channel: a rod forward with a reflector and directors across it
        ant.append(cube("yagi", (x - 0.02, AY - 0.7, z - 0.02), (x + 0.02, AY, z + 0.02), M_STEEL))
        for k, (yy, half) in enumerate(((AY - 0.08, 0.22), (AY - 0.3, 0.18), (AY - 0.5, 0.16), (AY - 0.68, 0.14))):
            ant.append(cube("el", (x - half, yy - 0.015, z - 0.015), (x + half, yy + 0.015, z + 0.015), M_STEEL))
# lightning rod and the IFF stub on top
ant.append(cube("rod", (-0.03, -0.38, 8.6), (0.03, -0.32, 9.6), M_STEEL))
ant.append(cube("iff", (-0.6, -0.5, 8.6), (0.6, -0.2, 8.75), M_OLIVE_D))
a = join("Antenna", ant)
print("ok", len(bpy.data.objects["Base"].data.polygons), len(a.data.polygons))
