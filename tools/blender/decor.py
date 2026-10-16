from mathutils import Euler
# Control room furniture: one object per block, all built at the origin (block centre, floor), 1 block = 1 m,
# Z up, front = +Y (OBJ -Z = north after export, the blockstate turns it). The desk is 1.5 blocks tall, so seats
# sit at 0.9. Run with helpers.py before and bake.py after:
#   bake('decor', 512, 'textures/block/decor.png', 'models/obj/decor.obj', 'blender/decor.blend')
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
for m in list(bpy.data.materials):
    bpy.data.materials.remove(m)
for im in list(bpy.data.images):
    bpy.data.images.remove(im)

M_LEATHER = mat("leather", grime((96, 56, 38), scale=7.0, streak=False))
M_LEATHER_D = mat("leather_d", grime((64, 38, 28), scale=7.0, streak=False))
M_BLACK = mat("black", rgb((36, 36, 36)))
M_CHROME = mat("chrome", grime((168, 172, 170), scale=8.0, streak=False))
M_WOOD = mat("wood", grime((112, 70, 40), scale=4.0))
M_SAFE = mat("safe", grime((92, 106, 96), scale=3.0))
M_SAFE_D = mat("safe_d", grime((66, 78, 70), scale=3.0, streak=False))
M_BRASS = mat("brass", rgb((180, 150, 70)))
M_RED = mat("red", grime((172, 36, 30), scale=5.0, streak=False))
M_POT = mat("pot", grime((150, 82, 52), scale=5.0, streak=False))
M_SOIL = mat("soil", rgb((54, 40, 30)))
M_LEAF = mat("leaf", grime((60, 112, 48), scale=9.0, streak=False))
M_LEAF_D = mat("leaf_d", grime((40, 82, 36), scale=9.0, streak=False))
M_PAPER = mat("paper", grime((222, 218, 202), scale=9.0, streak=False))
M_TIN = mat("tin", grime((110, 116, 112), scale=6.0))


def bar(name, a, b, t, m):
    """Square bar of thickness t from a to b."""
    a, b = Vector(a), Vector(b)
    d = b - a
    o = cube(name, (-t / 2, -t / 2, 0), (t / 2, t / 2, d.length), m)
    o.data.transform(Matrix.Translation(a) @ d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4())
    return o


def cone(name, r0, z0, z1, m, verts, r1, at=(0, 0, 0)):
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r0, radius2=r1, depth=z1 - z0)
    o = bpy.context.active_object
    o.name = name
    o.data.transform(Matrix.Translation(Vector(at) + Vector((0, 0, (z0 + z1) / 2))))
    return put(o, m)


def star_base(tag, r, z, t, m):
    """Five-spoke swivel base with casters."""
    out = []
    for k in range(5):
        a = math.radians(90 + k * 72)
        c, s = math.cos(a), math.sin(a)
        out.append(bar(f"{tag}_spoke{k}", (0.04 * c, 0.04 * s, z), (r * c, r * s, z - 0.06), t, m))
        out.append(cyl(f"{tag}_caster{k}", 0.035, 0.0, z - 0.06, M_BLACK, 8, at=(r * c, r * s, 0)))
    return out


# ---- operator's swivel chair: no arms, brown leatherette, curved-ish back on a single stem ----
p = star_base("op", 0.3, 0.14, 0.045, M_CHROME)
p.append(cyl("op_hub", 0.05, 0.08, 0.18, M_BLACK, 8))
p.append(cyl("op_column", 0.03, 0.18, 0.78, M_CHROME, 8))
p.append(cube("op_plate", (-0.16, -0.16, 0.78), (0.16, 0.16, 0.81), M_BLACK))
p.append(cube("op_seat", (-0.25, -0.24, 0.81), (0.25, 0.25, 0.92), M_LEATHER, bevel=0.0))
p.append(cube("op_seat_rim", (-0.26, -0.25, 0.80), (0.26, 0.26, 0.83), M_LEATHER_D))
p.append(bar("op_spine", (0, -0.2, 0.84), (0, -0.27, 1.12), 0.05, M_CHROME))
p.append(cube("op_back", (-0.23, -0.31, 1.06), (0.23, -0.23, 1.52), M_LEATHER))
p.append(cube("op_back_shell", (-0.235, -0.33, 1.07), (0.235, -0.31, 1.51), M_BLACK))
join("ChairOperator", p)

# ---- commander's armchair: wooden frame, tall padded back, armrests ----
p = []
for sx in (-1, 1):
    for sy in (-1, 1):
        p.append(cube(f"cm_leg{sx}{sy}", (sx * 0.29 - 0.03, sy * 0.27 - 0.03, 0), (sx * 0.29 + 0.03, sy * 0.27 + 0.03, 0.78), M_WOOD))
p.append(cube("cm_apron", (-0.32, -0.3, 0.7), (0.32, 0.3, 0.8), M_WOOD))
p.append(cube("cm_seat", (-0.3, -0.26, 0.8), (0.3, 0.3, 0.94), M_LEATHER))
p.append(cube("cm_back", (-0.3, -0.34, 0.8), (0.3, -0.26, 1.82), M_LEATHER))
p.append(cube("cm_back_frame", (-0.32, -0.37, 0.8), (0.32, -0.34, 1.86), M_WOOD))
p.append(cube("cm_back_top", (-0.32, -0.37, 1.86), (0.32, -0.25, 1.9), M_WOOD))
for sx in (-1, 1):
    x0, x1 = sorted((sx * 0.3, sx * 0.37))
    p.append(cube(f"cm_arm{sx}", (x0, -0.3, 1.1), (x1, 0.3, 1.16), M_WOOD))
    p.append(cube(f"cm_arm_pad{sx}", (x0 + 0.005, -0.28, 1.16), (x1 - 0.005, 0.24, 1.19), M_LEATHER_D))
    p.append(cube(f"cm_arm_post{sx}", (x0 + 0.01, 0.22, 0.94), (x1 - 0.01, 0.28, 1.1), M_WOOD))
for k, z in enumerate((1.1, 1.35, 1.6)):  # tufting
    for x in (-0.15, 0, 0.15):
        p.append(cube(f"cm_tuft{k}{x}", (x - 0.012, -0.262, z - 0.012), (x + 0.012, -0.255, z + 0.012), M_LEATHER_D))
join("ChairCommander", p)

# ---- document safe: heavy box on a plinth, dial, handle, hinge side, brass name plate ----
p = [cube("sf_plinth", (-0.38, -0.33, 0), (0.38, 0.3, 0.06), M_SAFE_D),
     cube("sf_body", (-0.4, -0.35, 0.06), (0.4, 0.3, 1.25), M_SAFE),
     cube("sf_door", (-0.34, 0.3, 0.12), (0.34, 0.34, 1.19), M_SAFE),
     cyl("sf_dial_ring", 0.075, -0.355, -0.34, M_SAFE_D, 10, axis="Y", at=(0.08, 0, 0.82)),
     cyl("sf_dial", 0.055, -0.385, -0.355, M_CHROME, 10, axis="Y", at=(0.08, 0, 0.82)),
     cube("sf_handle_hub", (0.04, 0.34, 0.53), (0.12, 0.37, 0.61), M_CHROME),
     cube("sf_handle", (0.075, 0.37, 0.4), (0.085, 0.39, 0.74), M_CHROME),
     cube("sf_plate", (-0.2, 0.34, 1.02), (0.06, 0.345, 1.1), M_BRASS),
     cube("sf_keyhole", (-0.24, 0.34, 0.66), (-0.2, 0.345, 0.72), M_BLACK)]
for z in (0.25, 1.0):
    p.append(cyl(f"sf_hinge{z}", 0.025, z, z + 0.14, M_SAFE_D, 8, at=(-0.355, 0.33, 0)))
join("Safe", p)

# ---- waste bin: tin bucket with crumpled paper ----
p = [cone("bn_body", 0.17, 0.0, 0.5, M_TIN, 10, 0.2),
     cyl("bn_rim", 0.21, 0.48, 0.52, M_TIN, 10),
     cyl("bn_fill", 0.18, 0.4, 0.46, M_PAPER, 10)]
for k, (x, y, s) in enumerate(((-0.06, 0.04, 0.07), (0.07, -0.03, 0.06), (0.0, 0.08, 0.05), (0.04, -0.1, 0.05))):
    ball = cube(f"bn_ball{k}", (x - s, y - s, 0.44), (x + s, y + s, 0.44 + 2 * s), M_PAPER)
    c = Vector((x, y, 0.44 + s))
    ball.data.transform(Matrix.Translation(c) @ Euler((0.4 * k, 0.6, 0.9 * k)).to_matrix().to_4x4() @ Matrix.Translation(-c))
    p.append(ball)
join("Bin", p)

# ---- CO2 fire extinguisher: red bottle, valve, handle, hose to a black horn ----
p = [cyl("ex_bottle", 0.11, 0.0, 0.7, M_RED, 10),
     cone("ex_shoulder", 0.11, 0.7, 0.78, M_RED, 10, 0.05),
     cyl("ex_neck", 0.035, 0.78, 0.86, M_CHROME, 8),
     cube("ex_valve", (-0.04, -0.04, 0.86), (0.04, 0.05, 0.93), M_CHROME),
     cube("ex_lever", (-0.015, 0.0, 0.93), (0.015, 0.18, 0.95), M_BLACK),
     cube("ex_grip", (-0.015, -0.02, 0.88), (0.015, 0.16, 0.9), M_BLACK),
     cube("ex_label", (-0.07, 0.105, 0.25), (0.07, 0.112, 0.5), M_PAPER),
     bar("ex_hose", (0.03, 0.02, 0.88), (0.13, 0.04, 0.55), 0.03, M_BLACK),
     cone("ex_horn", 0.035, 0.25, 0.55, M_BLACK, 8, 0.016, at=(0.13, 0.04, 0))]
join("Extinguisher", p)

# ---- ficus in a pot: the office tree ----
p = [cone("fc_pot", 0.17, 0.0, 0.4, M_POT, 8, 0.22),
     cyl("fc_rim", 0.235, 0.38, 0.44, M_POT, 8),
     cyl("fc_soil", 0.2, 0.4, 0.42, M_SOIL, 8),
     bar("fc_trunk", (0, 0, 0.42), (0.03, -0.02, 1.3), 0.05, M_WOOD),
     bar("fc_branch0", (0.02, -0.01, 0.95), (0.2, 0.05, 1.25), 0.03, M_WOOD),
     bar("fc_branch1", (0.02, -0.01, 1.05), (-0.18, 0.06, 1.35), 0.03, M_WOOD)]
rng = __import__("random").Random(7)
for k in range(90):
    a = rng.uniform(0, 2 * math.pi)
    r = rng.uniform(0.0, 0.3)
    z = rng.uniform(0.95, 1.75) - r * 0.4
    s = rng.uniform(0.06, 0.09)
    x, y = r * math.cos(a), r * math.sin(a)
    leaf = cube(f"fc_leaf{k}", (x - s, y - s * 0.4, z - 0.012), (x + s, y + s * 0.4, z + 0.012), M_LEAF if k % 3 else M_LEAF_D)
    leaf.data.transform(Matrix.Translation((x, y, z)) @ Matrix.Rotation(a, 4, "Z") @ Matrix.Rotation(rng.uniform(-0.5, 0.5), 4, "X") @ Matrix.Translation((-x, -y, -z)))
    p.append(leaf)
join("Ficus", p)

for o in bpy.data.objects:
    o.data.name = o.name
print([o.name for o in bpy.data.objects])
