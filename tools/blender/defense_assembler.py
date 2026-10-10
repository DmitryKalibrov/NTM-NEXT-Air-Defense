
# Defence assembly hall, 9x9 footprint, 7 high: closed concrete hall with a roller gate at each end
# (+-Y in Blender = game -+Z). Moving parts are modelled at rest; pivots are listed in docs/animation.md.
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


M_FLOOR = mat("floor", grime((104, 102, 96), scale=1.5))
M_CONC = mat("conc", grime((132, 128, 118)))
M_CONC_D = mat("conc_d", grime((100, 98, 92)))
M_STEEL = mat("steel", grime((70, 74, 78), scale=6.0, streak=False))
M_CLAD = mat("clad", ribbed((84, 96, 72), 40.0))
M_SHUTTER = mat("shutter", ribbed((96, 100, 102), 60.0))
M_GLASS = mat("glass", rgb((70, 95, 110)))
M_CRANE = mat("crane", grime((214, 168, 40), scale=6.0, streak=False))
M_ARM = mat("arm", grime((196, 104, 34), scale=6.0, streak=False))
M_HAZARD = mat("hazard", hazard)
M_DARK = mat("dark", rgb((40, 43, 42)))
M_LAMP = mat("lamp", rgb((235, 228, 190)))
M_RED = mat("red", rgb((150, 32, 26)))
M_BLUE = mat("blue", rgb((40, 62, 120)))

W, H = 4.5, 7.0       # half width, height
T = 0.35              # wall thickness
GX, GZ = 3.1, 5.8     # gate half width, gate height
RAIL_Z = 5.9          # crane runway top
ARM_X, ARM_P, ARM_E, ARM_REACH = 3.5, 2.4, 4.3, 1.5   # arm column x, shoulder / elbow height, upper arm length

h = []
# ---- floor: slab, darker stand with a hazard edge, cart rails running gate to gate
h.append(cube("floor", (-W, -W, 0.0), (W, W, 0.1), M_FLOOR))
h.append(cube("stand", (-2.4, -3.9, 0.1), (2.4, 3.9, 0.12), M_CONC_D))
for s in (-1, 1):
    h.append(cube("edge", (s * 2.4 - 0.08, -3.9, 0.1), (s * 2.4 + 0.08, 3.9, 0.13), M_HAZARD))
    for x in (0.9, 1.6):
        h.append(cube("rail", (s * x - 0.05, -W, 0.1), (s * x + 0.05, W, 0.125), M_STEEL))

# ---- side walls: concrete plinth, ribbed cladding, window band, concrete cornice; pilasters inside
for s in (-1, 1):
    xo, xi = s * W, s * (W - T)
    x0, x1 = sorted((xo, xi))
    h.append(cube("plinth", (x0, -W, 0.0), (x1, W, 1.8), M_CONC))
    c0, c1 = sorted((xi, s * (W - 0.12)))  # cladding sits back from the concrete face
    h.append(cube("clad", (c0, -W + 0.4, 1.8), (c1, W - 0.4, 4.8), M_CLAD))
    h.append(cube("sill", (x0, -W + 0.4, 4.8), (x1, W - 0.4, 4.92), M_CONC))
    h.append(cube("lintelw", (x0, -W + 0.4, 5.6), (x1, W - 0.4, 5.75), M_CONC))
    for k in range(4):
        y0 = -3.75 + k * 1.95
        h.append(cube("win", (c0 + 0.05, y0, 4.92), (c1 - 0.05, y0 + 1.65, 5.6), M_GLASS))
        h.append(cube("mullc", (c0, y0 + 0.78, 4.92), (c1, y0 + 0.87, 5.6), M_STEEL))
    h.append(cube("clad2", (c0, -W + 0.4, 5.75), (c1, W - 0.4, 6.6), M_CLAD))
    h.append(cube("cornice", (x0, -W, 6.6), (x1, W, H), M_CONC))
    # concrete pilasters between the windows, plinth to cornice
    for y in (-1.95, 0.0, 1.95):
        h.append(cube("pilaster", (x0, y - 0.15, 1.8), (x1, y + 0.15, 6.6), M_CONC))
    for y in (-3.9, 3.9):
        h.append(cube("pilaster", (x0, y - 0.15 if y < 0 else y - 0.2, 1.8), (x1, y + 0.2 if y < 0 else y + 0.15, 6.6), M_CONC))
    # inner steel columns carrying the crane runway
    for y in (-3.6, 0.0, 3.6):
        cx = s * (W - T - 0.15)
        h.append(cube("col", (cx - 0.15, y - 0.18, 0.1), (cx + 0.15, y + 0.18, RAIL_Z - 0.3), M_STEEL))
        h.append(cube("footband", (cx - 0.16, y - 0.19, 0.1), (cx + 0.16, y + 0.19, 0.6), M_HAZARD))
    rx = s * (W - T - 0.3)
    h.append(cube("runway", (rx - 0.18, -W + 0.4, RAIL_Z - 0.35), (rx + 0.18, W - 0.4, RAIL_Z), M_STEEL))
    h.append(cube("railtop", (rx - 0.05, -W + 0.4, RAIL_Z), (rx + 0.05, W - 0.4, RAIL_Z + 0.06), M_DARK))

# ---- end walls: full height concrete pylons either side of the gate, a heavy lintel, roller shutter box
for e in (-1, 1):
    yo, yi = e * W, e * (W - 0.4)
    y0, y1 = sorted((yo, yi))
    for s in (-1, 1):
        x0, x1 = sorted((s * GX, s * W))
        h.append(cube("pylon", (x0, y0, 0.0), (x1, y1, H), M_CONC))
        h.append(cube("pylonband", (x0, y0 - 0.01, 6.3), (x1, y1 + 0.01, 6.45), M_CONC_D))
        # steel gate jamb with hazard stripes
        j0, j1 = sorted((s * GX, s * (GX - 0.2)))
        h.append(cube("jamb", (j0, y0 - 0.05, 0.1), (j1, y1 + 0.05, GZ), M_STEEL))
        h.append(cube("jambhz", (j0 - 0.01, y0 - 0.06, 0.1), (j1 + 0.01, y1 + 0.06, 1.6), M_HAZARD))
        # red warning lamp on the pylon face
        ly = yo + e * 0.06
        h.append(cube("warn", (s * 3.8 - 0.15, min(yo, ly), 4.9), (s * 3.8 + 0.15, max(yo, ly), 5.15), M_RED))
    h.append(cube("lintel", (-GX, y0, GZ), (GX, y1, H), M_CONC))
    # rolled up shutter: drum housing on the outer face, bottom slat showing under the lintel
    hy0, hy1 = sorted((yi, yi - e * 0.45))
    h.append(cube("shutterbox", (-GX - 0.1, hy0, 5.9), (GX + 0.1, hy1, 6.5), M_STEEL))
    h.append(cube("shutter", (-GX + 0.2, y0 + 0.1, GZ - 0.35), (GX - 0.2, y1 - 0.1, GZ), M_SHUTTER))
    h.append(cube("slat", (-GX + 0.2, y0 + 0.08, GZ - 0.42), (GX - 0.2, y1 - 0.08, GZ - 0.35), M_HAZARD))
    # number board over the gate
    h.append(cube("board", (-1.0, min(yo, yo + e * 0.03), 6.55), (1.0, max(yo, yo + e * 0.03), 6.9), M_BLUE))

# ---- roof: ceiling beams along X, slab, parapet, skylights and vents
for y in (-2.7, -0.9, 0.9, 2.7):
    h.append(cube("ibeam", (-W + T, y - 0.1, 6.7), (W - T, y + 0.1, 6.85), M_STEEL))
    for x in (-1.8, 1.8):
        h.append(cube("lampbox", (x - 0.3, y - 0.12, 6.5), (x + 0.3, y + 0.12, 6.7), M_STEEL))
        h.append(cube("lamp", (x - 0.27, y - 0.1, 6.48), (x + 0.27, y + 0.1, 6.5), M_LAMP))
h.append(cube("roof", (-W + 0.1, -W + 0.1, 6.85), (W - 0.1, W - 0.1, 6.95), M_CONC_D))
for s in (-1, 1):
    h.append(cube("skylight", (s * 2.2 - 0.6, -3.4, 6.95), (s * 2.2 + 0.6, 3.4, 7.05), M_GLASS))
    h.append(cube("vent", (s * 0.6 - 0.3, -0.3, 6.95), (s * 0.6 + 0.3, 0.3, 7.25), M_STEEL))
    h.append(cube("ventcap", (s * 0.6 - 0.38, -0.38, 7.25), (s * 0.6 + 0.38, 0.38, 7.32), M_DARK))

# ---- arm columns: arm A on +X at game z -0.9 (Blender y +0.9), arm B mirrored; gas bottles, cabinets
for s in (-1, 1):
    ay = s * 0.9
    h.append(cube("armcol", (s * ARM_X - 0.3, ay - 0.3, 0.1), (s * ARM_X + 0.3, ay + 0.3, ARM_P - 0.2), M_STEEL))
    h.append(cube("armcolhz", (s * ARM_X - 0.31, ay - 0.31, 0.1), (s * ARM_X + 0.31, ay + 0.31, 0.5), M_HAZARD))
    h.append(cube("armplate", (s * ARM_X - 0.4, ay - 0.4, ARM_P - 0.2), (s * ARM_X + 0.4, ay + 0.4, ARM_P - 0.1), M_ARM))
    h.append(cube("cabinet", (s * 3.75 - 0.3, -ay - 0.5, 0.1), (s * 3.75 + 0.3, -ay + 0.5, 1.8), M_DARK))
    h.append(cube("cabpanel", (s * 3.45 - 0.01, -ay - 0.3, 1.0), (s * 3.45 + 0.01, -ay + 0.3, 1.5), M_LAMP))
    for k, m in enumerate((M_RED, M_BLUE, M_RED)):
        h.append(cyl("bottle", 0.13, 0.1, 1.5, m, verts=8, at=(s * 3.85, ay + s * (0.6 + k * 0.3), 0)))
# control booth in a corner by the rear gate
h.append(cube("booth", (-4.15, 2.6, 0.1), (-3.0, 4.1, 2.4), M_CLAD))
h.append(cube("boothwin", (-3.0, 2.8, 1.2), (-2.97, 3.9, 2.0), M_GLASS))
h.append(cube("boothroof", (-4.15, 2.55, 2.4), (-2.95, 4.1, 2.5), M_STEEL))
join("Hall", h)

# ---------------- crane: Bridge (along game Z), Trolley (along X), Hook (tip at origin), Cable (unit length) --------------
b = []
BX = W - T - 0.3
for y in (-0.35, 0.35):
    b.append(cube("girder", (-BX - 0.2, y - 0.17, RAIL_Z + 0.06), (BX + 0.2, y + 0.17, RAIL_Z + 0.46), M_CRANE))
for s in (-1, 1):
    b.append(cube("endtruck", (s * BX - 0.3, -0.75, RAIL_Z + 0.06), (s * BX + 0.3, 0.75, RAIL_Z + 0.4), M_CRANE))
    for y in (-0.55, 0.55):
        b.append(cyl("wheel", 0.12, -0.08, 0.08, M_DARK, verts=8, axis="X", at=(s * BX, y, RAIL_Z + 0.12)))
    b.append(cube("stripe", (s * BX - 0.31, -0.77, RAIL_Z + 0.15), (s * BX + 0.31, -0.75, RAIL_Z + 0.3), M_HAZARD))
    b.append(cube("stripe", (s * BX - 0.31, 0.75, RAIL_Z + 0.15), (s * BX + 0.31, 0.77, RAIL_Z + 0.3), M_HAZARD))
b.append(cube("cab", (BX - 1.0, -1.1, RAIL_Z - 0.55), (BX - 0.3, -0.52, RAIL_Z + 0.06), M_CRANE))
b.append(cube("cabwin", (BX - 0.9, -1.12, RAIL_Z - 0.4), (BX - 0.4, -1.1, RAIL_Z - 0.05), M_GLASS))
join("Bridge", b)

t = []
t.append(cube("frame", (-0.5, -0.6, RAIL_Z + 0.46), (0.5, 0.6, RAIL_Z + 0.62), M_CRANE))
t.append(cyl("drum", 0.16, -0.4, 0.4, M_STEEL, verts=8, axis="X", at=(0, 0, RAIL_Z + 0.75)))
t.append(cube("motor", (0.2, 0.25, RAIL_Z + 0.62), (0.45, 0.55, RAIL_Z + 0.85), M_DARK))
t.append(cube("box", (-0.45, -0.55, RAIL_Z + 0.62), (-0.15, -0.2, RAIL_Z + 0.82), M_DARK))
join("Trolley", t)

k = []
k.append(cube("block", (-0.28, -0.14, 0.32), (0.28, 0.14, 0.62), M_CRANE))
k.append(cube("blockhz", (-0.29, -0.15, 0.32), (0.29, 0.15, 0.4), M_HAZARD))
k.append(cyl("sheave", 0.12, -0.16, 0.16, M_STEEL, verts=8, axis="Y", at=(0, 0, 0.5)))
k.append(cube("shank", (-0.04, -0.04, 0.12), (0.04, 0.04, 0.32), M_STEEL))
k.append(cube("bar", (-0.45, -0.04, 0.04), (0.45, 0.04, 0.12), M_STEEL))      # spreader bar the part hangs from
for x in (-0.42, 0.42):
    k.append(cube("lug", (x - 0.03, -0.03, 0.0), (x + 0.03, 0.03, 0.04), M_DARK))
join("Hook", k)
c = [cube("cable", (x - 0.015, -0.015, 0.0), (x + 0.015, 0.015, 1.0), M_DARK) for x in (-0.1, 0.1)]
join("Cable", c)

# ---------------- welding arm (arm A, on +X reaching to -X; arm B is the same parts turned 180 deg) ----------------
a = []
a.append(cyl("shoulder", 0.26, -0.3, 0.3, M_STEEL, verts=8, axis="Y", at=(ARM_X, 0, ARM_P)))
a.append(cube("lower", (ARM_X - 0.18, -0.16, ARM_P), (ARM_X + 0.18, 0.16, ARM_E), M_ARM))
a.append(cube("lowerhz", (ARM_X - 0.19, -0.17, ARM_P + 0.25), (ARM_X + 0.19, 0.17, ARM_P + 0.45), M_HAZARD))
a.append(cube("piston", (ARM_X + 0.18, -0.06, ARM_P + 0.2), (ARM_X + 0.3, 0.06, ARM_E - 0.5), M_STEEL))
join("ArmLower", a)
a = []
a.append(cyl("elbow", 0.2, -0.24, 0.24, M_STEEL, verts=8, axis="Y", at=(ARM_X, 0, ARM_E)))
a.append(cube("upper", (ARM_X - ARM_REACH, -0.13, ARM_E - 0.14), (ARM_X + 0.35, 0.13, ARM_E + 0.14), M_ARM))
a.append(cube("motor", (ARM_X + 0.1, -0.2, ARM_E - 0.2), (ARM_X + 0.45, 0.2, ARM_E + 0.2), M_DARK))
a.append(cube("hose", (ARM_X - ARM_REACH + 0.2, 0.13, ARM_E - 0.05), (ARM_X, 0.17, ARM_E + 0.05), M_DARK))
join("ArmUpper", a)
HX = ARM_X - ARM_REACH
a = []
a.append(cyl("wrist", 0.14, -0.17, 0.17, M_STEEL, verts=8, axis="Y", at=(HX, 0, ARM_E)))
a.append(cube("head", (HX - 0.15, -0.15, ARM_E - 0.55), (HX + 0.15, 0.15, ARM_E), M_ARM))
a.append(cube("headhz", (HX - 0.16, -0.16, ARM_E - 0.55), (HX + 0.16, 0.16, ARM_E - 0.45), M_HAZARD))
join("ArmHead", a)
a = []
a.append(cube("torch", (HX - 0.05, -0.05, ARM_E - 0.95), (HX + 0.05, 0.05, ARM_E - 0.5), M_STEEL))
a.append(cube("nozzle", (HX - 0.07, -0.07, ARM_E - 1.05), (HX + 0.07, 0.07, ARM_E - 0.95), M_DARK))
join("ArmSpike", a)
print("ok", [o.name for o in bpy.data.objects])
