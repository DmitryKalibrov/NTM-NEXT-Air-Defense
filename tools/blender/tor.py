
# Tor-M2KM style combat module, 1 block = 1 m. Blender: Z up, turret front = +Y (OBJ -Z after export).
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
for m in list(bpy.data.materials):
    bpy.data.materials.remove(m)
for im in list(bpy.data.images):
    bpy.data.images.remove(im)


def emitters(base, cells):
    def b(nt):
        co = nt.nodes.new("ShaderNodeTexCoord")
        br = _n(nt, "ShaderNodeTexBrick", Scale=cells, **{"Mortar Size": 0.1, "Bias": 0.0, "Brick Width": 0.25, "Row Height": 0.25})
        br.offset = 0.0
        br.inputs["Color1"].default_value = lin([c * 1.15 for c in base]) + [1]
        br.inputs["Color2"].default_value = lin(base) + [1]
        br.inputs["Mortar"].default_value = lin([c * 0.5 for c in base]) + [1]
        nt.links.new(co.outputs["UV"], br.inputs["Vector"])
        return br.outputs["Color"]
    return b


M_CAMO = mat("camo", camo(scale=0.55))
M_OLIVE_D = mat("olive_dark", grime((61, 66, 56), scale=4.0, streak=False))
M_STEEL = mat("steel", grime((70, 74, 78), scale=6.0, streak=False))
M_DECK = mat("deck", grime((79, 82, 76), scale=5.0, streak=False))
M_DARK = mat("dark", rgb((40, 43, 42)))
M_ARRAY = mat("array", emitters((79, 84, 70), 60.0))
M_GRILL = mat("grill", emitters((52, 55, 50), 40.0))
M_GLASS = mat("glass", rgb((30, 45, 55)))
M_LIGHT = mat("light", grime((187, 185, 173), scale=6.0, streak=False))
M_HAZARD = mat("hazard", hazard)
M_MISSILE = mat("missile", grime((150, 152, 140), scale=8.0, streak=False))
M_RED = mat("red", rgb((150, 32, 26)))


def beam(name, a, b, t, m):
    a, b = Vector(a), Vector(b)
    d = b - a
    o = cube(name, (-t / 2, -t / 2, 0), (t / 2, t / 2, d.length), m)
    o.data.transform(Matrix.Translation(a) @ d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4())
    return o


def side_prism(name, profile, x0, x1, m):
    """Solid from a YZ side profile (CCW seen from +X), extruded across X."""
    bm = bmesh.new()
    l = [bm.verts.new((x0, y, z)) for y, z in profile]
    r = [bm.verts.new((x1, y, z)) for y, z in profile]
    bm.faces.new(l[::-1])
    bm.faces.new(r)
    n = len(profile)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((l[i], l[j], r[j], r[i]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    return put(o, m)


# ---------------- TBase: platform, turret ring, the power plug cell (static) ----------------
DECK = 0.6
p = []
p.append(cube("deck", (-2, -2, 0), (2, 2, DECK), M_DECK))
for sx in (-1, 1):
    p.append(cube("hz", (sx * 1.97 - 0.03, -1.6, DECK), (sx * 1.97 + 0.03, 1.6, DECK + 0.012), M_HAZARD))
p.append(cyl("ring", 1.45, DECK, DECK + 0.15, M_STEEL, verts=16))
for k in range(4):  # cable/coolant boxes on the deck
    a = k * math.pi / 2 + math.pi / 4
    c = Vector((math.cos(a), math.sin(a), 0)) * 1.75
    p.append(cube("box", (c.x - 0.18, c.y - 0.18, DECK), (c.x + 0.18, c.y + 0.18, DECK + 0.15), M_OLIVE_D))
# power plug: exactly the one block x -1..0 on the rear face (y = -2); cables meet it at mid height
PX0, PX1 = -1.0, 0.0
p.append(cube("plugframe", (PX0, -2.06, 0.0), (PX1, -2.0, DECK), M_HAZARD))
p.append(cube("plugpanel", (PX0 + 0.08, -2.08, 0.06), (PX1 - 0.08, -2.06, DECK - 0.06), M_OLIVE_D))
p.append(cyl("socket", 0.17, 2.08, 2.22, M_STEEL, verts=12, axis="Y", at=((PX0 + PX1) / 2, 0, 0.32)))
p.append(cyl("pin", 0.09, 2.22, 2.25, M_DARK, verts=10, axis="Y", at=((PX0 + PX1) / 2, 0, 0.32)))
for x in (PX0 + 0.14, PX1 - 0.14):
    for z in (0.12, DECK - 0.12):
        p.append(cyl("bolt", 0.025, 2.08, 2.1, M_STEEL, verts=6, axis="Y", at=(x, 0, z)))
join("TBase", p)

# ---------------- Turret (yaw) ----------------
t = []
# hull side profile: low nose ahead of the glacis, sloped rear upper edge
t.append(side_prism("hull", [(-2.9, 0.45), (2.95, 0.45), (2.95, 0.85), (2.5, 1.3), (2.5, 2.1), (-2.55, 2.1), (-2.9, 1.7)], -1.45, 1.45, M_CAMO))
# side equipment sponsons with doors and handles
for s in (-1, 1):
    x0, x1 = sorted((s * 1.45, s * 1.62))
    t.append(cube("sponson", (x0, -2.5, 0.75), (x1, 2.0, 1.95), M_CAMO))
    xd0, xd1 = sorted((s * 1.62, s * 1.645))
    for y0 in (-2.4, -1.55, -0.7, 0.15, 1.0):
        t.append(cube("door", (xd0, y0, 0.85), (xd1, y0 + 0.75, 1.85), M_CAMO))
        t.append(cube("handle", (min(xd1, xd1 + 0.03 * s), y0 + 0.55, 1.3), (max(xd1, xd1 + 0.03 * s) + 0.0, y0 + 0.62, 1.45), M_STEEL))
    t.append(cube("skirt", (min(s * 1.45, s * 1.66), -2.55, 0.7), (max(s * 1.45, s * 1.66), 2.05, 0.75), M_STEEL))
# rear: cooling grilles and an exhaust box
t.append(cube("grille", (-1.1, -2.92, 0.7), (1.1, -2.9, 1.45), M_GRILL))
t.append(cube("exhaust", (0.85, -3.05, 1.5), (1.25, -2.8, 1.65), M_DARK))
# roof equipment fore and aft of the missile module
t.append(cube("cooler", (-1.2, 1.0, 2.1), (0.6, 1.9, 2.4), M_CAMO))
t.append(cube("coolgrill", (-1.1, 1.1, 2.4), (0.5, 1.8, 2.42), M_GRILL))
t.append(cube("aftbox", (-1.2, -1.85, 2.1), (1.2, -1.0, 2.45), M_CAMO))
for x in (-0.8, 0.0, 0.8):
    t.append(cube("lid", (x - 0.3, -1.75, 2.45), (x + 0.3, -1.1, 2.48), M_CAMO))
# raised missile module with 8 cells, ribbed sides
t.append(cube("module", (-1.05, -0.85, 2.1), (1.05, 0.85, 2.95), M_CAMO))
t.append(cube("deckplate", (-1.0, -0.8, 2.95), (1.0, 0.8, 3.0), M_STEEL))
for x in (-0.9, -0.3, 0.3, 0.9):
    for s in (-1, 1):
        y0, y1 = sorted((s * 0.85, s * 0.9))
        t.append(cube("rib", (x - 0.04, y0, 2.15), (x + 0.04, y1, 2.9), M_CAMO))
# rear pedestal of the search radar and its mast
t.append(cube("pedestal", (-0.55, -2.6, 2.1), (0.55, -1.9, 2.75), M_CAMO))
t.append(cyl("mast", 0.17, 2.75, 3.15, M_STEEL, verts=10, at=(0, -2.25, 0)))
# electro-optical sight on the front right, cable conduit, whip antennas at the rear corners
t.append(cube("eo", (0.85, 1.9, 2.1), (1.35, 2.45, 2.55), M_CAMO))
t.append(cube("eoglass", (0.92, 2.45, 2.2), (1.28, 2.47, 2.45), M_GLASS))
t.append(cube("conduit", (-1.38, -2.5, 2.1), (-1.25, 2.3, 2.2), M_OLIVE_D))
for s in (-1, 1):
    t.append(cube("whipbase", (s * 1.3 - 0.07, -2.45, 2.1), (s * 1.3 + 0.07, -2.31, 2.25), M_DARK))
    t.append(cube("whip", (s * 1.3 - 0.015, -2.395, 2.25), (s * 1.3 + 0.015, -2.365, 4.2), M_DARK))
# trunnion brackets for the tracking radar on the glacis
for s in (-1, 1):
    t.append(cube("bracket", (s * 0.95 - 0.1, 2.25, 1.25), (s * 0.95 + 0.1, 2.7, 1.85), M_CAMO))
join("Turret", t)

# ---------------- Hatches 1-4 back row, 5-8 front row ----------------
CX = (-0.72, -0.24, 0.24, 0.72)
for c in range(8):
    back = c < 4
    y0, y1 = (-0.78, -0.05) if back else (0.05, 0.78)
    h = [cube("hatch", (CX[c % 4] - 0.22, y0, 3.0), (CX[c % 4] + 0.22, y1, 3.06), M_CAMO)]
    hy = y0 if back else y1  # hinge edge
    for x in (CX[c % 4] - 0.12, CX[c % 4] + 0.12):
        h.append(cyl("knuckle", 0.03, -0.04, 0.04, M_STEEL, verts=6, axis="X", at=(x, hy, 3.03)))
    join(f"Hatch{c + 1}", h)

# ---------------- SearchRadar: spins about the mast (x 0, y -2.25) ----------------
r = []
r.append(cyl("rotor", 0.25, 3.15, 3.35, M_OLIVE_D, verts=10, at=(0, -2.25, 0)))
r.append(cube("yoke", (-0.15, -2.4, 3.35), (0.15, -2.1, 3.6), M_STEEL))
# lattice antenna 2.0 x 1.2, leaning back 20 deg, face towards +Y
tilt = Matrix.Translation((0, -2.2, 3.55)) @ Matrix.Rotation(math.radians(-20), 4, "X")
ant = []
W2, H = 1.0, 1.25
ant.append(beam("f", (-W2, 0, 0), (W2, 0, 0), 0.07, M_CAMO))
ant.append(beam("f", (-W2, 0, H), (W2, 0, H), 0.07, M_CAMO))
ant.append(beam("f", (-W2, 0, 0), (-W2, 0, H), 0.07, M_CAMO))
ant.append(beam("f", (W2, 0, 0), (W2, 0, H), 0.07, M_CAMO))
for i in range(1, 10):
    x = -W2 + 2 * W2 * i / 10
    ant.append(beam("v", (x, 0, 0), (x, 0, H), 0.03, M_STEEL))
for j in range(1, 6):
    z = H * j / 6
    ant.append(beam("h", (-W2, 0, z), (W2, 0, z), 0.025, M_STEEL))
for s in (-1, 1):  # back truss
    ant.append(beam("truss", (0, -0.05, 0.05), (s * 0.8, -0.3, H * 0.5), 0.05, M_CAMO))
    ant.append(beam("truss", (0, -0.05, H - 0.05), (s * 0.8, -0.3, H * 0.5), 0.05, M_CAMO))
ant.append(beam("spine", (0, -0.3, 0), (0, -0.3, H), 0.06, M_CAMO))
ant.append(cube("feed", (-0.2, 0.0, -0.05), (0.2, 0.25, 0.12), M_OLIVE_D))
ant.append(cube("iff", (-0.7, -0.05, H + 0.03), (0.7, 0.05, H + 0.12), M_OLIVE_D))
for o in ant:
    o.data.transform(tilt)
r += ant
join("SearchRadar", r)

# ---------------- TrackRadar: pitches about x through (y 2.5, z 1.55) ----------------
k = []
k.append(cube("box", (-0.85, 2.5, 1.0), (0.85, 3.0, 2.2), M_CAMO))
k.append(cube("array", (-0.72, 3.0, 1.1), (0.72, 3.03, 2.1), M_ARRAY))
for s in (-1, 1):
    k.append(cyl("trunnion", 0.16, -0.08, 0.08, M_STEEL, verts=10, axis="X", at=(s * 0.93, 2.5, 1.55)))
    hx0, hx1 = sorted((s * 0.85, s * 1.05))
    k.append(cube("horn", (hx0, 2.7, 1.75), (hx1, 3.05, 2.05), M_OLIVE_D))
k.append(cube("top", (-0.6, 2.55, 2.2), (0.6, 2.9, 2.35), M_OLIVE_D))
join("TrackRadar", k)

# ---------------- TorMissile: 9M331, 2.9 m, nose +Y (game -Z), centred ----------------
mm = []
L, R = 2.9, 0.12
body = cyl("body", R, -L / 2, L / 2 - 0.45, M_MISSILE, verts=12)
mm.append(body)
bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=R, radius2=0.01, depth=0.45)
nose = bpy.context.active_object
nose.data.transform(Matrix.Translation((0, 0, L / 2 - 0.225)))
mm.append(put(nose, M_MISSILE))
mm.append(cyl("band", R + 0.015, L / 2 - 0.75, L / 2 - 0.6, M_RED, verts=12))
mm.append(cyl("nozzle", R * 0.8, -L / 2 - 0.05, -L / 2, M_DARK, verts=12))
for a in range(4):
    rot = Matrix.Rotation(a * math.pi / 2 + math.pi / 4, 4, "Z")
    fin = cube("wing", (R - 0.01, -0.01, -L / 2 + 0.05), (R + 0.22, 0.01, -L / 2 + 0.5), M_MISSILE)
    fin.data.transform(rot)
    can = cube("canard", (R - 0.01, -0.01, L / 2 - 0.95), (R + 0.12, 0.01, L / 2 - 0.75), M_MISSILE)
    can.data.transform(rot)
    mm += [fin, can]
for o in mm:
    o.data.transform(Matrix.Rotation(-math.pi / 2, 4, "X"))  # +Z -> +Y
join("TorMissile", mm)
for o in bpy.data.objects:
    if o.name not in ("TBase", "TorMissile"):
        o.data.transform(Matrix.Translation((0, 0, DECK - 0.3)))
print("ok", [o.name for o in bpy.data.objects])
