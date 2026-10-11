
# Pantsir-S1 combat module at full size, 1 block = 1 m. Blender: Z up, front = +Y (OBJ -Z).
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


M_CAMO = mat("camo", camo(dark=(48, 52, 50), mid=(84, 98, 62), sand=(150, 142, 118), scale=0.55))
M_OLIVE_D = mat("olive_dark", grime((61, 66, 56), scale=4.0, streak=False))
M_STEEL = mat("steel", grime((70, 74, 78), scale=6.0, streak=False))
M_DECK = mat("deck", grime((79, 82, 76), scale=5.0, streak=False))
M_DARK = mat("dark", rgb((40, 43, 42)))
M_BARREL = mat("barrel", grime((52, 54, 55), scale=8.0, streak=False))
M_HAZARD = mat("hazard", hazard)
M_TUBE = mat("tube", grime((86, 96, 70), scale=6.0, streak=False))
M_CAP = mat("cap", grime((60, 66, 52), scale=6.0, streak=False))
M_ARRAY = mat("array", emitters((79, 84, 70), 55.0))
M_GLASS = mat("glass", rgb((30, 45, 55)))
M_MISSILE = mat("missile", grime((150, 152, 140), scale=8.0, streak=False))
M_RED = mat("red", rgb((150, 32, 26)))

DECK = 0.6
PIV = (0.2, 1.65)            # side weapon blocks: each pitches on its own trunnion at x = +-1.2 through (y, z)
RPIV = (1.15, 1.45)          # tracking radar drum: its own elevation axis (y, z)
PACK_X = 1.55                # missile pack centre |x|
COLS = (-0.27, 0.0, 0.27)    # tube columns within a pack
ROWS = (1.82, 2.1)           # tube rows (z)
T_Y0, T_Y1, T_R = -1.5, 1.7, 0.125
GUN_X, GUN_Z, GUN_Y1 = 1.55, 1.3, 3.1   # twin gun under each pack: centre |x|, barrel height, muzzle y


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


def ycyl(name, r, y0, y1, x, z, m, verts=12):
    """Cylinder along +Y from y0 to y1 (helper cyl() mirrors the range on its Y axis)."""
    return cyl(name, r, -y1, -y0, m, verts=verts, axis="Y", at=(x, 0, z))


def pivot_origin(o, at):
    """Put the object's origin on its axis (world geometry unchanged) so Blender shows the real pivot."""
    o.data.transform(Matrix.Translation(-Vector(at)))
    o.location = at
    return o


# ---------------- Base: platform, ring (static) ----------------
p = [cube("deck", (-2, -2, 0), (2, 2, DECK), M_DECK)]
for sx in (-1, 1):
    p.append(cube("hz", (sx * 1.97 - 0.03, -1.6, DECK), (sx * 1.97 + 0.03, 1.6, DECK + 0.012), M_HAZARD))
p.append(cyl("ring", 1.35, DECK, DECK + 0.15, M_STEEL, verts=16))
join("Base", p)

# ---------------- Turret (yaw) ----------------
t = []
t.append(side_prism("hull", [(-3.0, 0.75), (1.15, 0.75), (1.45, 1.05), (1.45, 1.75), (0.9, 2.2), (-2.7, 2.2), (-3.0, 1.9)], -1.15, 1.15, M_CAMO))
for s in (-1, 1):  # side cheeks carrying the trunnions, with access doors
    x0, x1 = sorted((s * 1.15, s * 1.2))
    t.append(cube("cheek", (x0, -0.6, 0.85), (x1, 1.0, 2.0), M_CAMO))
    t.append(cyl("trunnion", 0.2, -0.1, 0.1, M_STEEL, verts=10, axis="X", at=(s * 1.2, PIV[0], PIV[1])))
    xd0, xd1 = sorted((s * 1.15, s * 1.17))
    for y0 in (-2.85, -1.85):
        t.append(cube("door", (xd0, y0, 0.95), (xd1, y0 + 0.9, 1.8), M_CAMO))
        t.append(cube("handle", (min(xd1, xd1 + 0.03 * s), y0 + 0.7, 1.3), (max(xd1, xd1 + 0.03 * s), y0 + 0.78, 1.45), M_STEEL))
    bx0, bx1 = sorted((s * 1.17, s * 1.35))
    t.append(cube("sidebox", (bx0, -2.9, 0.8), (bx1, -0.8, 0.95), M_OLIVE_D))
# rear: cooling grille and stowage box
t.append(cube("grille", (-0.8, -3.02, 0.95), (0.8, -3.0, 1.7), M_ARRAY))
t.append(cube("rearbox", (-0.9, -3.3, 0.85), (0.9, -3.0, 1.2), M_OLIVE_D))
t.append(cube("roofbox", (-0.95, -2.9, 2.2), (0.95, -2.45, 2.45), M_CAMO))
for x in (-0.6, 0.0, 0.6):
    t.append(cube("roofvent", (x - 0.22, -2.85, 2.45), (x + 0.22, -2.5, 2.47), M_ARRAY))
# roof: search radar column at the rear, electro-optical turret, whips
t.append(cube("column", (-0.35, -2.25, 2.2), (0.35, -1.55, 2.7), M_CAMO))
t.append(cyl("eobase", 0.22, 2.2, 2.35, M_STEEL, verts=10, at=(0.45, 0.35, 0)))
t.append(cube("eo", (0.25, 0.15, 2.35), (0.65, 0.6, 2.7), M_CAMO))
t.append(cube("eoglass", (0.3, 0.6, 2.42), (0.6, 0.62, 2.62), M_GLASS))
t.append(cube("whip", (-0.95, -2.35, 2.2), (-0.91, -2.31, 3.6), M_DARK))
t.append(cube("whip", (0.91, -2.35, 2.2), (0.95, -2.31, 3.4), M_DARK))
t.append(cube("vent", (-0.85, -1.2, 2.2), (-0.25, -0.2, 2.32), M_ARRAY))
t.append(cube("hatch", (0.2, -1.3, 2.2), (0.85, -0.6, 2.26), M_CAMO))
join("Turret", t)

# ---------------- SearchRadar: flat array on the column, spins about (x 0, y -1.1) ----------------
r = []
r.append(cyl("rotor", 0.25, 2.7, 2.85, M_OLIVE_D, verts=10, at=(0, -1.9, 0)))
r.append(cube("yoke", (-0.12, -2.02, 2.85), (0.12, -1.78, 3.1), M_STEEL))
ant = []
ant.append(cube("panel", (-0.85, -0.1, 0.0), (0.85, 0.08, 1.15), M_CAMO))
ant.append(cube("face", (-0.78, 0.08, 0.07), (0.78, 0.1, 1.08), M_ARRAY))
for s in (-1, 1):
    ant.append(cube("rib", (s * 0.5 - 0.04, -0.2, 0.05), (s * 0.5 + 0.04, -0.1, 1.1), M_OLIVE_D))
ant.append(cube("iff", (-0.8, -0.06, 1.15), (0.8, 0.06, 1.3), M_OLIVE_D))
for x in (-0.6, -0.2, 0.2, 0.6):
    ant.append(cube("iffel", (x - 0.03, 0.06, 1.17), (x + 0.03, 0.14, 1.28), M_STEEL))
tilt = Matrix.Translation((0, -1.9, 3.05)) @ Matrix.Rotation(math.radians(-12), 4, "X")
for o in ant:
    o.data.transform(tilt)
r += ant
pivot_origin(join("SearchRadar", r), (0, -1.9, 2.7))

# ---------------- WeaponL / WeaponR: each side block pitches on its own trunnion ----------------
for s_, name in ((-1, "WeaponL"), (1, "WeaponR")):
    w = []
    px = s_ * PACK_X
    # trunnion hub on the cheek and the arm out to the pack
    w.append(cyl("hub", 0.24, -0.12, 0.12, M_CAMO, verts=10, axis="X", at=(s_ * 1.32, PIV[0], PIV[1])))
    w.append(cube("arm", (min(s_ * 1.2, s_ * (PACK_X - 0.45)), PIV[0] - 0.3, 1.45), (max(s_ * 1.2, s_ * (PACK_X - 0.45)), PIV[0] + 0.3, 2.25), M_CAMO))
    for y in (-1.0, 0.0, 1.0):
        w.append(cube("strap", (px - 0.45, y - 0.06, ROWS[0] - 0.16), (px + 0.45, y + 0.06, ROWS[1] + 0.16), M_OLIVE_D))
    for z in ROWS:
        for dx in COLS:
            w.append(ycyl("tube", T_R, T_Y0, T_Y1, px + dx, z, M_TUBE, verts=10))
            w.append(ycyl("rim", T_R + 0.015, T_Y1 - 0.06, T_Y1 + 0.015, px + dx, z, M_STEEL, verts=10))  # face clear of the tube end
            w.append(ycyl("back", T_R * 0.8, T_Y0 - 0.03, T_Y0 + 0.01, px + dx, z, M_DARK, verts=10))
    # 2A38M style twin gun: receiver box, feed, cooling jackets, two barrels, muzzle brakes
    w.append(cube("receiver", (px - 0.28, -0.7, GUN_Z - 0.2), (px + 0.28, 0.9, GUN_Z + 0.16), M_CAMO))
    w.append(cube("feed", (px - 0.2, -0.4, GUN_Z - 0.38), (px + 0.2, 0.4, GUN_Z - 0.2), M_OLIVE_D))
    for dx in (-0.1, 0.1):
        w.append(ycyl("jacket", 0.07, 0.9, 1.7, px + dx, GUN_Z, M_STEEL, verts=8))
        w.append(ycyl("barrel", 0.035, 1.7, GUN_Y1 - 0.18, px + dx, GUN_Z, M_BARREL, verts=8))
        w.append(ycyl("brake", 0.055, GUN_Y1 - 0.18, GUN_Y1, px + dx, GUN_Z, M_DARK, verts=8))
    pivot_origin(join(name, w), (0, PIV[0], PIV[1]))

# ---------------- TrackRadar: front drum, its own elevation axis ----------------
k = []
k.append(cube("mount", (-0.3, 0.75, 1.15), (0.3, 1.2, 1.75), M_CAMO))
k.append(ycyl("drum", 0.62, 1.15, 1.75, 0.0, RPIV[1], M_CAMO, verts=16))
k.append(ycyl("drumface", 0.55, 1.75, 1.78, 0.0, RPIV[1], M_ARRAY, verts=16))
for s_ in (-1, 1):
    k.append(cyl("rhinge", 0.1, -0.08, 0.08, M_STEEL, verts=8, axis="X", at=(s_ * 0.64, RPIV[0], RPIV[1])))
pivot_origin(join("TrackRadar", k), (0, RPIV[0], RPIV[1]))

# ---------------- Cap1..12: tube covers, shown while that tube is loaded ----------------
n = 0
for s in (-1, 1):
    for z in ROWS:
        for dx in COLS:
            n += 1
            pivot_origin(join(f"Cap{n}", [ycyl("cap", T_R - 0.01, T_Y1 + 0.015, T_Y1 + 0.045, s * PACK_X + dx, z, M_CAP, verts=10)]), (0, PIV[0], PIV[1]))

# ---------------- 57E6 in flight: booster + dart, nose +Y, centred ----------------
def missile_parts():
    st, dt = [], []
    L = 3.2
    st.append(cyl("bnozzle", 0.07, 0.0, 0.05, M_DARK, verts=10))
    st.append(cyl("booster", 0.085, 0.05, 1.4, M_MISSILE, verts=10))
    for a in range(4):
        rot = Matrix.Rotation(a * math.pi / 2 + math.pi / 4, 4, "Z")
        f = cube("bfin", (0.08, -0.008, 0.05), (0.26, 0.008, 0.4), M_MISSILE)
        f.data.transform(rot)
        st.append(f)
    dt.append(cyl("dart", 0.045, 1.4, L - 0.35, M_MISSILE, verts=8))
    dt.append(cyl("dband", 0.048, L - 0.75, L - 0.68, M_RED, verts=8))
    bpy.ops.mesh.primitive_cone_add(vertices=8, radius1=0.045, radius2=0.005, depth=0.35)
    nose = bpy.context.active_object
    nose.data.transform(Matrix.Translation((0, 0, L - 0.175)))
    dt.append(put(nose, M_MISSILE))
    for a in range(4):
        rot = Matrix.Rotation(a * math.pi / 2 + math.pi / 4, 4, "Z")
        f = cube("dfin", (0.04, -0.006, 1.45), (0.13, 0.006, 1.7), M_MISSILE)
        f.data.transform(rot)
        c = cube("canard", (0.04, -0.006, L - 0.75), (0.09, 0.006, L - 0.6), M_MISSILE)
        c.data.transform(rot)
        dt += [f, c]
    for o in st + dt:
        o.data.transform(Matrix.Translation((0, 0, -L / 2)))
        o.data.transform(Matrix.Rotation(-math.pi / 2, 4, "X"))  # +Z -> +Y
    return st, dt


# tracer streak for the guns: 2.5 long along +Y (game -Z), centred; drawn full-bright
M_TRACER = mat("tracer", rgb((255, 196, 70)))
join("Tracer", [cube("tracer", (-0.03, -1.25, -0.03), (0.03, 1.25, 0.03), M_TRACER)])

st, dt = missile_parts()
join("PantsirStage", st)
join("PantsirDart", dt)
print("ok", [o.name for o in bpy.data.objects])
