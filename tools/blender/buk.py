
# Buk-M1 style launcher (9A310 turret) at full size, 1 block = 1 m. Blender: Z up, front = +Y (OBJ -Z).
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
for m in list(bpy.data.materials):
    bpy.data.materials.remove(m)
for im in list(bpy.data.images):
    bpy.data.images.remove(im)

M_CAMO = mat("camo", camo(dark=(48, 52, 50), mid=(84, 98, 62), sand=(150, 142, 118), scale=0.5))
M_OLIVE_D = mat("olive_dark", grime((61, 66, 56), scale=4.0, streak=False))
M_STEEL = mat("steel", grime((70, 74, 78), scale=6.0, streak=False))
M_DECK = mat("deck", grime((79, 82, 76), scale=5.0, streak=False))
M_DARK = mat("dark", rgb((40, 43, 42)))
M_HAZARD = mat("hazard", hazard)
M_GREEN = mat("missile", grime((74, 108, 66), scale=7.0, streak=False))
M_WHITE = mat("nose", grime((226, 224, 214), scale=7.0, streak=False))
M_NOZZLE = mat("nozzle", rgb((96, 40, 28)))
M_RADOME = mat("radome", grime((88, 104, 66), scale=3.0, streak=False))
M_ARRAY = mat("antenna", grime((58, 62, 58), scale=9.0, streak=False))
M_GLASS = mat("glass", rgb((30, 45, 55)))


def beam(name, a, b, t, m):
    a, b = Vector(a), Vector(b)
    d = b - a
    o = cube(name, (-t / 2, -t / 2, 0), (t / 2, t / 2, d.length), m)
    o.data.transform(Matrix.Translation(a) @ d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4())
    return o


def side_prism(name, profile, x0, x1, m):
    bm = bmesh.new()
    l = [bm.verts.new((x0, y, z)) for y, z in profile]
    r = [bm.verts.new((x1, y, z)) for y, z in profile]
    bm.faces.new(l[::-1])
    bm.faces.new(r)
    for i in range(len(profile)):
        j = (i + 1) % len(profile)
        bm.faces.new((l[i], l[j], r[j], r[i]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    return put(o, m)


def rail_tube(a, b, r=0.025):
    return beam("rail", a, b, r * 2, M_STEEL)


DECK, HULL_TOP = 0.6, 1.55
PIV_Y, PIV_Z = -2.6, 2.05           # launcher hinge in the rear head
RAIL_X = (-1.125, -0.375, 0.375, 1.125)
M_LEN, M_R = 5.55, 0.2
M_TAIL = -2.5                        # missile tail y on the launcher at rest
RAIL_TOP = 2.85
M_AXIS_Z = RAIL_TOP + 0.1 + M_R      # missile axis height at rest

# ---------------- Base: platform, ring, power plug (static) ----------------
p = [cube("deck", (-2, -2, 0), (2, 2, DECK), M_DECK)]
for sx in (-1, 1):
    p.append(cube("hz", (sx * 1.97 - 0.03, -1.6, DECK), (sx * 1.97 + 0.03, 1.6, DECK + 0.012), M_HAZARD))
p.append(cyl("ring", 1.55, DECK, DECK + 0.15, M_STEEL, verts=16))
PX0, PX1 = -1.0, 0.0
p.append(cube("plugframe", (PX0, -2.06, 0.0), (PX1, -2.0, DECK), M_HAZARD))
p.append(cube("plugpanel", (PX0 + 0.08, -2.08, 0.06), (PX1 - 0.08, -2.06, DECK - 0.06), M_OLIVE_D))
p.append(cyl("socket", 0.17, 2.08, 2.22, M_STEEL, verts=12, axis="Y", at=((PX0 + PX1) / 2, 0, 0.32)))
p.append(cyl("pin", 0.09, 2.22, 2.25, M_DARK, verts=10, axis="Y", at=((PX0 + PX1) / 2, 0, 0.32)))
for x in (PX0 + 0.14, PX1 - 0.14):
    for z in (0.12, DECK - 0.12):
        p.append(cyl("bolt", 0.025, 2.08, 2.1, M_STEEL, verts=6, axis="Y", at=(x, 0, z)))
join("Base", p)

# ---------------- Carriage (yaw): long low hull, front instrument cabin, radome ----------------
c = []
c.append(side_prism("hull", [(-3.1, 0.75), (2.4, 0.75), (2.4, HULL_TOP), (-2.95, HULL_TOP), (-3.1, 1.4)], -1.5, 1.5, M_CAMO))
for s_ in (-1, 1):
    xd0, xd1 = sorted((s_ * 1.5, s_ * 1.52))
    for y0 in (-2.95, -2.15, -1.35):  # rear row of side doors
        c.append(cube("door", (xd0, y0, 0.85), (xd1, y0 + 0.7, 1.45), M_CAMO))
        c.append(cube("handle", (min(xd1, xd1 + 0.03 * s_), y0 + 0.55, 1.1), (max(xd1, xd1 + 0.03 * s_), y0 + 0.62, 1.22), M_STEEL))
    c.append(cube("bigdoor", (xd0, -0.5, 0.85), (xd1, 0.35, 1.45), M_CAMO))
    xs0, xs1 = sorted((s_ * 1.5, s_ * 1.6))
    c.append(cube("skirt", (xs0, -3.0, 0.75), (xs1, 2.35, 0.82), M_STEEL))
# front instrument cabin, roof box, electro-optical sight, cabin door and window
c.append(cube("cabin", (-1.4, 0.4, HULL_TOP), (1.4, 2.4, 2.5), M_CAMO))
c.append(cube("roofbox", (-1.1, 0.8, 2.5), (1.1, 2.2, 2.72), M_CAMO))
c.append(cube("eobase", (0.05, 1.7, 2.72), (0.35, 2.0, 2.78), M_STEEL))
c.append(cube("eo", (0.0, 1.65, 2.78), (0.4, 2.05, 2.92), M_CAMO))
c.append(cube("eoglass", (0.06, 2.05, 2.8), (0.34, 2.07, 2.9), M_GLASS))
for s_ in (-1, 1):
    xd0, xd1 = sorted((s_ * 1.4, s_ * 1.42))
    c.append(cube("cabdoor", (xd0, 0.55, 1.65), (xd1, 1.35, 2.4), M_CAMO))
    c.append(cube("window", (xd0 - 0.005 * s_ if s_ < 0 else xd0, 1.6, 1.95), (xd1 + 0.005 if s_ > 0 else xd1, 2.25, 2.3), M_GLASS))
# launcher seat in the rear and ram anchors
c.append(cube("seat", (-1.0, -3.0, HULL_TOP), (1.0, -2.2, 1.75), M_OLIVE_D))
for s_ in (-1, 1):
    c.append(cube("ramlug", (s_ * 1.25 - 0.1, -0.75, HULL_TOP), (s_ * 1.25 + 0.1, -0.45, HULL_TOP + 0.15), M_STEEL))
# whip at the rear corner
c.append(cyl("mastbase", 0.08, HULL_TOP, HULL_TOP + 0.25, M_STEEL, verts=8, at=(-1.35, -3.0, 0)))
c.append(cube("mast", (-1.38, -3.03, HULL_TOP + 0.25), (-1.32, -2.97, HULL_TOP + 3.6), M_OLIVE_D))
join("Carriage", c)

# ---------------- Radar: 9S35 under the long rounded radome in the nose (fixed to the carriage) ----------------
r = []
r.append(side_prism("radome", [(2.4, 0.8), (3.25, 0.8), (3.5, 0.88), (3.66, 1.12), (3.72, 1.5), (3.66, 1.95), (3.5, 2.25), (3.25, 2.4), (2.4, 2.4)], -1.25, 1.25, M_RADOME))
r.append(cube("seam", (-1.27, 2.4, 0.8), (1.27, 2.47, 2.4), M_OLIVE_D))
join("Radar", r)

# ---------------- Launcher (pitch about x through (PIV_Y, PIV_Z)): rear head + rails ----------------
L = []
L.append(side_prism("head", [(-3.2, 1.6), (-1.7, 1.6), (-1.7, 2.5), (-1.95, 2.75), (-2.9, 2.75), (-3.2, 2.45)], -1.35, 1.35, M_CAMO))
L.append(cyl("axle", 0.14, -1.45, 1.45, M_STEEL, verts=10, axis="X", at=(0, PIV_Y, PIV_Z)))
for x in (-0.75, 0.0, 0.75):
    L.append(cube("girder", (x - 0.09, -1.75, RAIL_TOP - 0.3), (x + 0.09, 0.4, RAIL_TOP - 0.1), M_CAMO))
L.append(cube("cross", (-1.3, -0.1, RAIL_TOP - 0.3), (1.3, 0.15, RAIL_TOP - 0.1), M_CAMO))
for x in RAIL_X:
    L.append(cube("rail", (x - 0.07, -2.7, RAIL_TOP - 0.1), (x + 0.07, 0.45, RAIL_TOP), M_STEEL))
    L.append(cube("shoe", (x - 0.04, -1.4, RAIL_TOP), (x + 0.04, -1.1, M_AXIS_Z - M_R + 0.01), M_STEEL))
    L.append(cube("shoe", (x - 0.04, 0.1, RAIL_TOP), (x + 0.04, 0.4, M_AXIS_Z - M_R + 0.01), M_STEEL))
for s_ in (-1, 1):  # ram lugs under the girders
    L.append(cube("lug", (s_ * 1.25 - 0.1, -1.3, RAIL_TOP - 0.45), (s_ * 1.25 + 0.1, -1.1, RAIL_TOP - 0.1), M_STEEL))
    L.append(cube("lugarm", (min(s_ * 0.84, s_ * 1.25), -1.3, RAIL_TOP - 0.3), (max(s_ * 0.84, s_ * 1.25), -1.1, RAIL_TOP - 0.15), M_CAMO))
join("Launcher", L)


def missile9m38():
    """9M38 along +Y, tail at y 0: nozzle, body, long-chord wings, tail fins, white ogive."""
    q = []
    q.append(cyl("nozzle", M_R * 0.8, 0.0, 0.08, M_NOZZLE, verts=12))
    q.append(cyl("body", M_R, 0.08, M_LEN - 0.95, M_GREEN, verts=12))
    q.append(cyl("band", M_R + 0.015, M_LEN - 2.0, M_LEN - 1.92, M_DARK, verts=12))
    bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=M_R, radius2=0.015, depth=0.95)
    nose = bpy.context.active_object
    nose.data.transform(Matrix.Translation((0, 0, M_LEN - 0.475)))
    q.append(put(nose, M_WHITE))
    for k in range(4):
        rot = Matrix.Rotation(k * math.pi / 2 + math.pi / 4, 4, "Z")
        # long low wing: a trapezoid slab from the body out to 0.43
        bm = bmesh.new()
        t = 0.012
        prof = [(M_R, 1.0), (M_R, 3.6), (0.43, 3.25), (0.43, 1.35)]
        a = [bm.verts.new((rr, -t, z)) for rr, z in prof]
        b = [bm.verts.new((rr, t, z)) for rr, z in prof]
        bm.faces.new(a)
        bm.faces.new(b[::-1])
        for i in range(4):
            j = (i + 1) % 4
            bm.faces.new((a[j], a[i], b[i], b[j]))
        me = bpy.data.meshes.new("wing")
        bm.to_mesh(me)
        bm.free()
        w = bpy.data.objects.new("wing", me)
        bpy.context.collection.objects.link(w)
        w.data.transform(rot)
        q.append(put(w, M_GREEN))
        fin = cube("fin", (M_R - 0.01, -0.012, 0.1), (0.48, 0.012, 0.55), M_WHITE)
        fin.data.transform(rot)
        q.append(fin)
    for o in q:
        o.data.transform(Matrix.Rotation(-math.pi / 2, 4, "X"))  # +Z -> +Y
    return q


for i, x in enumerate(RAIL_X):
    ms = missile9m38()
    for o in ms:
        o.data.transform(Matrix.Translation((x, M_TAIL, M_AXIS_Z)))
    join(f"Missile{i + 1}", ms)
ms = missile9m38()
for o in ms:
    o.data.transform(Matrix.Translation((0, -M_LEN / 2, 0)))
join("BukMissile", ms)

# ---------------- hydraulic rams (two, beside the radar) ----------------
A = (-0.6, HULL_TOP + 0.08)      # (y, z) anchor on the hull
B = (-1.2, RAIL_TOP - 0.4)       # lug on the launcher at rest
cy = []
rd = []
for s in (-1, 1):
    x = s * 1.25
    d = Vector((0, B[0] - A[0], B[1] - A[1]))
    cy.append(beam("cyl", (x, A[0], A[1]), (x, A[0] + d.y * 0.97, A[1] + d.z * 0.97), 0.16, M_CAMO))
    rd.append(beam("rod", (x, A[0], A[1]), (x, B[0], B[1]), 0.08, M_STEEL))
join("RamCylinder", cy)
join("RamRod", rd)
print("ok", [o.name for o in bpy.data.objects])
