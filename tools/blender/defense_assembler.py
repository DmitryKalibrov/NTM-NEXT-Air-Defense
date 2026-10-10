
# Defence assembly hall, 9x9 footprint, 7 high, open at both ends (+-Y in Blender = game +-Z).
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
for m in list(bpy.data.materials):
    bpy.data.materials.remove(m)
for im in list(bpy.data.images):
    bpy.data.images.remove(im)


def ribbed(base, ribs):
    """Corrugated cladding: light and dark bands along the panel (UV based)."""
    def b(nt):
        co = nt.nodes.new("ShaderNodeTexCoord")
        wave = _n(nt, "ShaderNodeTexWave", Scale=ribs, Distortion=0.0)
        wave.wave_profile = "SAW"
        nt.links.new(co.outputs["UV"], wave.inputs["Vector"])
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.elements[0].color = lin([c * 0.75 for c in base]) + [1]
        ramp.color_ramp.elements[1].color = lin([min(255, c * 1.08) for c in base]) + [1]
        nt.links.new(wave.outputs["Fac"], ramp.inputs["Fac"])
        return ramp.outputs["Color"]
    return b


M_FLOOR = mat("floor", grime((110, 108, 102), scale=1.5))
M_CONC = mat("conc", grime((128, 125, 116)))
M_STEEL = mat("steel", grime((70, 74, 78), scale=6.0, streak=False))
M_FRAME = mat("frame", grime((84, 98, 62), scale=4.0, streak=False))
M_CLAD = mat("clad", ribbed((92, 104, 80), 40.0))
M_ROOF = mat("roof", ribbed((80, 84, 86), 50.0))
M_GLASS = mat("glass", rgb((70, 95, 110)))
M_CRANE = mat("crane", grime((214, 168, 40), scale=6.0, streak=False))
M_HAZARD = mat("hazard", hazard)
M_DARK = mat("dark", rgb((40, 43, 42)))
M_LAMP = mat("lamp", rgb((235, 228, 190)))
M_RED = mat("red", rgb((150, 32, 26)))

W, H = 4.5, 7.0          # half width, height
RAIL_Z = 5.9             # crane runway top
COLS_Y = (-4.3, 0.0, 4.3)


def beam(name, a, b, t, m):
    a, b = Vector(a), Vector(b)
    d = b - a
    o = cube(name, (-t / 2, -t / 2, 0), (t / 2, t / 2, d.length), m)
    o.data.transform(Matrix.Translation(a) @ d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4())
    return o


def pivot_origin(o, at):
    o.data.transform(Matrix.Translation(-Vector(at)))
    o.location = at
    return o


h = []
# floor: thin slab, assembly stand outline, walkway lines
h.append(cube("floor", (-W, -W, 0.0), (W, W, 0.1), M_FLOOR))
for s in (-1, 1):
    h.append(cube("line", (s * 2.6 - 0.06, -4.3, 0.1), (s * 2.6 + 0.06, 4.3, 0.12), M_HAZARD))
h.append(cube("stand", (-2.2, -2.2, 0.1), (2.2, 2.2, 0.12), M_CONC))
# columns, wall cladding to 4.6 with a window band above, eaves
for s in (-1, 1):
    x = s * (W - 0.15)
    for y in COLS_Y:
        h.append(cube("col", (x - 0.15, y - 0.15, 0.1), (x + 0.15, y + 0.15, H - 0.4), M_FRAME))
        bx0, bx1 = sorted((x, x - s * 0.55))
        h.append(cube("bracket", (bx0, y - 0.15, RAIL_Z - 0.6), (bx1, y + 0.15, RAIL_Z - 0.3), M_FRAME))
    xo0, xo1 = sorted((s * W, s * (W - 0.08)))
    h.append(cube("clad", (xo0, -4.15, 0.1), (xo1, 4.15, 4.6), M_CLAD))
    h.append(cube("sill", (xo0 - 0.02, -4.15, 4.6), (xo1 + 0.02, 4.15, 4.7), M_FRAME))
    for k in range(6):
        y0 = -4.0 + k * 1.37
        h.append(cube("win", (xo0, y0, 4.75), (xo1, y0 + 1.2, 5.45), M_GLASS))
        h.append(cube("mull", (xo0 - 0.01, y0 + 1.2, 4.7), (xo1 + 0.01, y0 + 1.37, 5.5), M_FRAME))
    h.append(cube("clad2", (xo0, -4.15, 5.5), (xo1, 4.15, H - 0.4), M_CLAD))
    # crane runway beam on the brackets
    rx = s * (W - 0.55)
    h.append(cube("runway", (rx - 0.15, -4.4, RAIL_Z - 0.3), (rx + 0.15, 4.4, RAIL_Z), M_STEEL))
    h.append(cube("railtop", (rx - 0.04, -4.4, RAIL_Z), (rx + 0.04, 4.4, RAIL_Z + 0.06), M_DARK))
    # stripes on the column feet
    for y in COLS_Y:
        h.append(cube("footband", (s * (W - 0.15) - 0.16, y - 0.16, 0.1), (s * (W - 0.15) + 0.16, y + 0.16, 0.6), M_HAZARD))
# roof trusses over the columns, purlins, roof sheet with a ridge skylight
for y in COLS_Y:
    h.append(beam("chord", (-W, y, H - 0.4), (W, y, H - 0.4), 0.2, M_FRAME))
    h.append(beam("rafter", (-W, y, H - 0.4), (0, y, H), 0.16, M_FRAME))
    h.append(beam("rafter", (W, y, H - 0.4), (0, y, H), 0.16, M_FRAME))
    for k in range(1, 4):
        x = W * k / 4
        for s in (-1, 1):
            h.append(beam("post", (s * x, y, H - 0.4), (s * x, y, H - 0.4 * x / W), 0.07, M_FRAME))
            h.append(beam("diag", (s * x, y, H - 0.4), (s * W * (k - 1) / 4, y, H - 0.4 * W * (k - 1) / 4 / W), 0.06, M_FRAME))
for s in (-1, 1):
    for f in (0.35, 0.75):
        x = s * W * f
        h.append(beam("purlin", (x, -W, H - 0.4 * f + 0.05), (x, W, H - 0.4 * f + 0.05), 0.1, M_STEEL))
    # roof sheet halves, skylight strip near the ridge
    bm = bmesh.new()
    pts = [(s * W, -W, H - 0.4 + 0.12), (s * 0.7, -W, H - 0.4 * 0.7 / W + 0.12), (s * 0.7, W, H - 0.4 * 0.7 / W + 0.12), (s * W, W, H - 0.4 + 0.12)]
    vs = [bm.verts.new(p) for p in (pts if s < 0 else pts[::-1])]
    bm.faces.new(vs)
    me = bpy.data.meshes.new("roof")
    bm.to_mesh(me)
    bm.free()
    ro = bpy.data.objects.new("roof", me)
    bpy.context.collection.objects.link(ro)
    sol = ro.modifiers.new("t", "SOLIDIFY")
    sol.thickness = 0.06
    h.append(put(ro, M_ROOF))
h.append(cube("skylight", (-0.7, -W, H - 0.02), (0.7, W, H + 0.03), M_GLASS))
# control booth in a front corner, lamps under the trusses
h.append(cube("booth", (-4.35, 3.0, 0.1), (-3.0, 4.35, 2.3), M_FRAME))
h.append(cube("boothwin", (-3.0, 3.2, 1.2), (-2.98, 4.2, 2.0), M_GLASS))
h.append(cube("boothwin2", (-4.1, 3.0, 1.2), (-3.2, 2.98, 2.0), M_GLASS))
h.append(cube("boothroof", (-4.4, 2.95, 2.3), (-2.95, 4.4, 2.4), M_STEEL))
h.append(cube("panel", (-2.98, 3.4, 0.6), (-2.95, 4.0, 1.1), M_DARK))
for y in COLS_Y:
    for x in (-2.0, 2.0):
        h.append(cube("lampbox", (x - 0.25, y - 0.12, H - 0.75), (x + 0.25, y + 0.12, H - 0.6), M_STEEL))
        h.append(cube("lamp", (x - 0.22, y - 0.1, H - 0.77), (x + 0.22, y + 0.1, H - 0.75), M_LAMP))
# lettering-ish warning boards on the end columns
for y in (-4.3, 4.3):
    for s in (-1, 1):
        h.append(cube("board", (s * (W - 0.15) - 0.17, y - 0.17 if y < 0 else y + 0.15, 2.6), (s * (W - 0.15) + 0.17, y - 0.15 if y < 0 else y + 0.17, 3.2), M_RED))
join("Hall", h)

# ---------------- crane: Bridge (moves along Y), Trolley (along X on the bridge), Hook (up and down) ----------------
b = []
BX = W - 0.55
b.append(cube("girder", (-BX - 0.2, -0.35, RAIL_Z + 0.06), (BX + 0.2, -0.05, RAIL_Z + 0.6), M_CRANE))
b.append(cube("girder", (-BX - 0.2, 0.05, RAIL_Z + 0.06), (BX + 0.2, 0.35, RAIL_Z + 0.6), M_CRANE))
for s in (-1, 1):
    b.append(cube("endtruck", (s * BX - 0.3, -0.7, RAIL_Z + 0.06), (s * BX + 0.3, 0.7, RAIL_Z + 0.45), M_CRANE))
    for y in (-0.5, 0.5):
        b.append(cyl("wheel", 0.12, -0.08, 0.08, M_DARK, verts=8, axis="X", at=(s * BX, y, RAIL_Z + 0.12)))
    b.append(cube("stripe", (s * BX - 0.31, -0.72, RAIL_Z + 0.2), (s * BX + 0.31, -0.7, RAIL_Z + 0.35), M_HAZARD))
b.append(cube("cab", (BX - 1.1, 0.35, RAIL_Z - 0.35), (BX - 0.3, 1.0, RAIL_Z + 0.4), M_CRANE))
b.append(cube("cabwin", (BX - 1.0, 1.0, RAIL_Z - 0.2), (BX - 0.4, 1.02, RAIL_Z + 0.2), M_GLASS))
join("Bridge", b)

t = []
t.append(cube("frame", (-0.45, -0.45, RAIL_Z + 0.6), (0.45, 0.45, RAIL_Z + 1.0), M_CRANE))
t.append(cyl("drum", 0.2, -0.35, 0.35, M_STEEL, verts=10, axis="X", at=(0, 0, RAIL_Z + 0.85)))
t.append(cube("motor", (0.25, -0.35, RAIL_Z + 1.0), (0.45, 0.0, RAIL_Z + 1.25), M_DARK))
join("Trolley", t)

k = []
for x in (-0.1, 0.1):  # cables reach up into the trolley, so lowering the block only shows more of them
    k.append(cube("cable", (x - 0.015, -0.015, 2.6), (x + 0.015, 0.015, RAIL_Z + 0.85), M_DARK))
k.append(cube("block", (-0.25, -0.12, 2.25), (0.25, 0.12, 2.65), M_CRANE))
k.append(cyl("sheave", 0.12, -0.14, 0.14, M_STEEL, verts=8, axis="Y", at=(0, 0, 2.5)))
k.append(cube("shank", (-0.04, -0.04, 1.95), (0.04, 0.04, 2.25), M_STEEL))
k.append(cube("hook", (-0.18, -0.04, 1.8), (0.06, 0.04, 1.9), M_STEEL))
k.append(cube("hooktip", (-0.18, -0.04, 1.9), (-0.1, 0.04, 2.05), M_STEEL))
join("Hook", k)
print("ok", [o.name for o in bpy.data.objects])
