# Optoelectronic station (OES): a tripod-mounted head with a thermal imager (big round lens), a TV camera and a
# laser rangefinder between them, like the border / air defence EO trackers. 1 block = 1 m, Z up, front = +Y
# (OBJ -Z after export); cyl(axis="Y") runs along -Y, so front faces take negative ranges. Parts: Base (tripod, mast) still, Yaw turns about Z at the origin, Pitch tilts about X at
# PITCH_Z. Run with helpers.py before and bake.py after (see README).
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
for m in list(bpy.data.materials):
    bpy.data.materials.remove(m)
for im in list(bpy.data.images):
    bpy.data.images.remove(im)

PITCH_Z = 1.55

M_OLIVE = mat("olive", grime((92, 104, 70), scale=5.0, streak=False))
M_OLIVE_D = mat("olive_d", grime((70, 80, 54), scale=5.0, streak=False))
M_DARK = mat("dark", rgb((38, 40, 39)))
M_STEEL = mat("steel", grime((70, 74, 78), scale=6.0, streak=False))
M_GLASS = mat("glass", rgb((24, 34, 44)))
M_LENS = mat("lens", rgb((60, 56, 70)))


def leg(name, a, b, t, m):
    a, b = Vector(a), Vector(b)
    d = b - a
    o = cube(name, (-t / 2, -t / 2, 0), (t / 2, t / 2, d.length), m)
    o.data.transform(Matrix.Translation(a) @ d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4())
    return o


def hooded_box(tag, x0, x1, y0, y1, z0, z1, hood, m):
    """A sensor housing with a sun hood (top and side plates) reaching `hood` past its front."""
    parts = [cube(tag, (x0, y0, z0), (x1, y1, z1), m)]
    t = 0.025
    parts.append(cube(tag + "_hood_t", (x0, y1, z1 - t), (x1, y1 + hood, z1), m))
    parts.append(cube(tag + "_hood_l", (x0, y1, z0 + 0.04), (x0 + t, y1 + hood, z1 - t), m))
    parts.append(cube(tag + "_hood_r", (x1 - t, y1, z0 + 0.04), (x1, y1 + hood, z1 - t), m))
    return parts


def handle(tag, x0, x1, y, z, m):
    h = 0.07
    return [cube(tag + "_p0", (x0, y - 0.015, z), (x0 + 0.03, y + 0.015, z + h), m),
            cube(tag + "_p1", (x1 - 0.03, y - 0.015, z), (x1, y + 0.015, z + h), m),
            cube(tag + "_bar", (x0, y - 0.015, z + h), (x1, y + 0.015, z + h + 0.03), m)]


# ---- base: tripod and mast ----
base = []
for k in range(3):
    a = math.radians(90 + k * 120)
    base.append(leg(f"leg{k}", (0.07 * math.cos(a), 0.07 * math.sin(a), 0.9), (0.44 * math.cos(a), 0.44 * math.sin(a), 0.0), 0.055, M_DARK))
    base.append(cube(f"foot{k}", (0.44 * math.cos(a) - 0.05, 0.44 * math.sin(a) - 0.05, 0), (0.44 * math.cos(a) + 0.05, 0.44 * math.sin(a) + 0.05, 0.03), M_DARK))
base.append(cyl("hub", 0.09, 0.82, 1.0, M_DARK, 8))
base.append(cube("mast", (-0.08, -0.08, 1.0), (0.08, 0.08, 1.24), M_OLIVE_D))
base.append(cyl("collar", 0.11, 1.24, 1.3, M_STEEL, 8))
join("Base", base)

# ---- yaw: turntable and the stand carrying the tilt axis ----
yaw = [cyl("turntable", 0.15, 1.3, 1.36, M_OLIVE_D, 10),
       cube("stand", (-0.07, -0.1, 1.36), (0.07, 0.1, PITCH_Z - 0.02), M_OLIVE_D)]
join("Yaw", yaw)

# ---- pitch: the sensor head ----
head = []
# rangefinder in the middle: two small apertures
head.append(cube("lrf", (-0.16, -0.2, 1.42), (0.16, 0.22, 1.74), M_OLIVE))
head.append(cube("lrf_tx", (-0.1, 0.22, 1.6), (-0.02, 0.235, 1.68), M_GLASS))
head.append(cube("lrf_rx", (0.02, 0.22, 1.58), (0.11, 0.235, 1.68), M_GLASS))
head += handle("lrf_h", -0.12, 0.12, 0.0, 1.74, M_OLIVE_D)
# thermal imager on the left: big box, big round lens in a dark rim
head += hooded_box("ir", -0.64, -0.17, -0.26, 0.24, 1.36, 1.76, 0.12, M_OLIVE)
head.append(cyl("ir_rim", 0.15, -0.27, -0.24, M_DARK, 16, axis="Y", at=(-0.405, 0, 1.56)))
head.append(cyl("ir_lens", 0.12, -0.285, -0.27, M_LENS, 16, axis="Y", at=(-0.405, 0, 1.56)))
head += handle("ir_h", -0.5, -0.3, -0.05, 1.76, M_OLIVE_D)
# TV camera on the right: smaller box, small lens low in the face
head += hooded_box("tv", 0.17, 0.6, -0.22, 0.26, 1.4, 1.74, 0.1, M_OLIVE)
head.append(cyl("tv_rim", 0.065, -0.285, -0.26, M_DARK, 12, axis="Y", at=(0.32, 0, 1.6)))
head.append(cyl("tv_lens", 0.045, -0.295, -0.285, M_LENS, 12, axis="Y", at=(0.32, 0, 1.6)))
head.append(cube("tv_panel", (0.24, 0.26, 1.44), (0.53, 0.27, 1.52), M_OLIVE_D))
head += handle("tv_h", 0.28, 0.48, -0.03, 1.74, M_OLIVE_D)
join("Pitch", head)
print("eos parts", [o.name for o in bpy.data.objects])
