# The radar interface's instrument face (textures/gui/radar_console.png): a 16 x 9 unit panel rendered straight
# on, so the game maps its regions linearly. The live picture (scope, character screen) and the labels are drawn
# over it by ScreenRadar; keep the region constants there in step with these.
#   scope   centre (4.6, 4.7), screen radius 3.6
#   chars   screen (9.8, 4.0) - (15.0, 8.0)
#   range knob (10.4, 1.55), brightness knob (12.0, 1.55), buttons at x 13.3 / 14.2 / 15.1, y 1.55 (0.7 square)
#   plates  under each control (y 0.45 - 0.85) and over the character screen (9.4, 8.45) - (15.4, 8.85)
#   hood    octagon around the scope, outer radius 4.45
import bpy, bmesh, math
from mathutils import Matrix, Vector

R = "E:/ModPorting/ntm-air-defense/common/src/main/resources/assets/ntm_air_defense/textures/gui/radar_console.png"
old = bpy.context.window.scene
sc = bpy.data.scenes.new("radar_gui")
bpy.context.window.scene = sc


def lin(c):
    return [(v / 255) ** 2.2 for v in c] + [1]


def material(name, rgb, rough=0.6, grain=0.0, metal=0.0, spec=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if spec is not None:
        b.inputs["Specular IOR Level"].default_value = spec
    if grain:  # hammer paint: a fine mottle
        tex = nt.nodes.new("ShaderNodeTexNoise")
        tex.inputs["Scale"].default_value = 60
        tex.inputs["Detail"].default_value = 6
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.elements[0].color = lin([c * (1 - grain) for c in rgb])
        ramp.color_ramp.elements[1].color = lin([min(255, c * (1 + grain)) for c in rgb])
        nt.links.new(tex.outputs["Fac"], ramp.inputs["Fac"])
        nt.links.new(ramp.outputs["Color"], b.inputs["Base Color"])
        bump = nt.nodes.new("ShaderNodeBump")
        bump.inputs["Strength"].default_value = 0.08
        nt.links.new(tex.outputs["Fac"], bump.inputs["Height"])
        nt.links.new(bump.outputs["Normal"], b.inputs["Normal"])
    else:
        b.inputs["Base Color"].default_value = lin(rgb)
    return m


PAINT = material("rg_paint", (150, 156, 150), 0.7, 0.07)
PAINT_D = material("rg_paint_d", (112, 118, 112), 0.7, 0.07)
DARK = material("rg_dark", (38, 40, 38), 0.5)
BLACK = material("rg_black", (14, 15, 14), 0.4)
TUBE = material("rg_tube", (6, 18, 9), 0.35, spec=0.15)
CHROME = material("rg_chrome", (190, 192, 190), 0.25, metal=0.8)
WHITE = material("rg_white", (225, 225, 215), 0.5)
GREEN = material("rg_green", (60, 150, 70), 0.4)
YELLOW = material("rg_yellow", (210, 170, 40), 0.4)
RED = material("rg_red", (180, 40, 30), 0.4)


def obj(name, bm, m):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    o.data.materials.append(m)
    sc.collection.objects.link(o)
    return o


def box(name, x0, y0, z0, x1, y1, z1, m):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1)
    bmesh.ops.scale(bm, vec=(x1 - x0, y1 - y0, z1 - z0), verts=bm.verts[:])
    bmesh.ops.translate(bm, vec=((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2), verts=bm.verts[:])
    return obj(name, bm, m)


def cyl(name, cx, cy, r, z0, z1, m, n=8, r1=None):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=n, radius1=r, radius2=r if r1 is None else r1, depth=z1 - z0)
    bmesh.ops.rotate(bm, verts=bm.verts[:], cent=(0, 0, 0), matrix=Matrix.Rotation(math.pi / n, 3, "Z"))
    bmesh.ops.translate(bm, vec=(cx, cy, (z0 + z1) / 2), verts=bm.verts[:])
    return obj(name, bm, m)


def ring(name, cx, cy, r0, r1, z0, z1, m, n=8):
    bm = bmesh.new()
    a0 = math.pi / n
    for k in range(n):
        pts = []
        for rr, kk in ((r0, k), (r1, k), (r1, k + 1), (r0, k + 1)):
            a = a0 + 2 * math.pi * kk / n
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        vs = [bm.verts.new((x, y, z0)) for x, y in pts] + [bm.verts.new((x, y, z1)) for x, y in pts]
        for f in ((0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)):
            bm.faces.new([vs[i] for i in f])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    return obj(name, bm, m)


def funnel(name, cx, cy, r0, r1, z0, z1, m, n):
    """A sloped inner wall: radius r0 at the bottom, r1 at the top."""
    bm = bmesh.new()
    lo = [bm.verts.new((cx + r0 * math.cos(2 * math.pi * k / n), cy + r0 * math.sin(2 * math.pi * k / n), z0)) for k in range(n)]
    hi = [bm.verts.new((cx + r1 * math.cos(2 * math.pi * k / n), cy + r1 * math.sin(2 * math.pi * k / n), z1)) for k in range(n)]
    for k in range(n):
        bm.faces.new((lo[k], lo[(k + 1) % n], hi[(k + 1) % n], hi[k]))
    return obj(name, bm, m)


def hood_top(name, cx, cy, r, ro, z, m, n=48):
    """The hood's face: from a round hole of radius r out to the octagon of vertex radius ro."""
    bm = bmesh.new()
    ap = ro * math.cos(math.pi / 8)
    ins, outs = [], []
    for k in range(n):
        a = 2 * math.pi * k / n
        # distance to the octagon (vertices at pi/8 + k pi/4) along this angle
        rel = (a - math.pi / 8) % (math.pi / 4) - math.pi / 8
        d = ap / math.cos(rel)
        ins.append(bm.verts.new((cx + r * math.cos(a), cy + r * math.sin(a), z)))
        outs.append(bm.verts.new((cx + d * math.cos(a), cy + d * math.sin(a), z)))
    for k in range(n):
        j = (k + 1) % n
        bm.faces.new((ins[k], outs[k], outs[j], ins[j]))
    return obj(name, bm, m)


# panel with a raised frame and corner screws
box("panel", 0, 0, -0.3, 16, 9, 0, PAINT)
for b in ((0, 0, 16, 0.25), (0, 8.75, 16, 9), (0, 0.25, 0.25, 8.75), (15.75, 0.25, 16, 8.75)):
    box("frame", b[0], b[1], 0, b[2], b[3], 0.12, PAINT_D)
for x, y in ((0.5, 0.5), (15.5, 0.5), (0.5, 8.5), (15.5, 8.5)):
    cyl("screw", x, y, 0.13, 0.12, 0.2, CHROME, 6)

# radar scope: octagonal hood, inner collar, the tube face, four bolts
CX, CY = 4.6, 4.7
ring("hood", CX, CY, 4.1, 4.45, 0, 0.9, PAINT_D)
hood_top("hood_top", CX, CY, 3.78, 4.1, 0.9, PAINT_D)
funnel("collar", CX, CY, 3.6, 3.78, 0.02, 0.9, DARK, 48)
cyl("tube", CX, CY, 3.62, -0.1, 0.02, TUBE, 48)
for dx, dy in ((-3.55, -3.55), (3.55, -3.55), (-3.55, 3.55), (3.55, 3.55)):
    cyl("bolt", CX + dx, CY + dy, 0.17, 0, 0.18, CHROME, 6)

# character screen in a chamfered bezel, a plate above it
for b in ((9.4, 3.6, 15.4, 4.0), (9.4, 8.0, 15.4, 8.4), (9.4, 4.0, 9.8, 8.0), (15.0, 4.0, 15.4, 8.0)):
    box("bezel", b[0], b[1], 0, b[2], b[3], 0.45, PAINT_D)
box("chars", 9.8, 4.0, -0.05, 15.0, 8.0, 0.02, TUBE)
box("plate", 9.4, 8.45, 0, 15.4, 8.85, 0.06, BLACK)

# controls row: range selector with a tick dial, brightness knob, three push buttons; a plate under each
cyl("dial", 10.4, 1.55, 0.72, 0, 0.04, BLACK, 24)
for k in range(4):
    a = math.radians(150 - k * 40)
    x, y = 10.4 + 0.62 * math.cos(a), 1.55 + 0.62 * math.sin(a)
    box("tick", x - 0.05, y - 0.05, 0.04, x + 0.05, y + 0.05, 0.07, WHITE)
cyl("range_skirt", 10.4, 1.55, 0.48, 0, 0.18, DARK, 12)
cyl("range_knob", 10.4, 1.55, 0.38, 0.18, 0.6, BLACK, 12, 0.34)
cyl("bright_skirt", 12.0, 1.55, 0.4, 0, 0.16, DARK, 12)
cyl("bright_knob", 12.0, 1.55, 0.3, 0.16, 0.5, BLACK, 12, 0.27)
for x, cap in ((13.3, GREEN), (14.2, YELLOW), (15.1, RED)):
    box("btn_bezel", x - 0.35, 1.2, 0, x + 0.35, 1.9, 0.14, BLACK)
    box("btn_cap", x - 0.26, 1.29, 0.14, x + 0.26, 1.81, 0.34, cap)
for x0, x1 in ((9.65, 11.15), (11.35, 12.65), (12.9, 15.5)):
    box("plate", x0, 0.45, 0, x1, 0.85, 0.06, BLACK)

# straight-on orthographic camera, soft key light from the upper left
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
cam.data.type = "ORTHO"
cam.data.ortho_scale = 16
cam.location = (8, 4.5, 20)
sc.collection.objects.link(cam)
sc.camera = cam
light = bpy.data.objects.new("key", bpy.data.lights.new("key", "AREA"))
light.data.energy = 3500
light.data.size = 12
light.location = (2, 12, 14)
light.rotation_euler = (math.radians(-35), math.radians(-10), 0)
sc.collection.objects.link(light)
fill = bpy.data.objects.new("fill", bpy.data.lights.new("fill", "SUN"))
fill.data.energy = 1.2
fill.rotation_euler = (math.radians(20), math.radians(15), 0)
sc.collection.objects.link(fill)
sc.world = bpy.data.worlds.new("rg_world")
sc.world.color = (0.02, 0.02, 0.02)
try:
    sc.render.engine = "BLENDER_EEVEE_NEXT"
except TypeError:
    sc.render.engine = "BLENDER_EEVEE"
sc.render.resolution_x, sc.render.resolution_y = 1280, 720
sc.render.film_transparent = False
sc.render.image_settings.file_format = "PNG"
sc.render.image_settings.color_mode = "RGB"
sc.render.filepath = R
bpy.ops.render.render(write_still=True)

# leave nothing behind in the file
for o in list(sc.collection.objects):
    bpy.data.objects.remove(o)
bpy.context.window.scene = old
bpy.data.scenes.remove(sc)
print("rendered", R)
