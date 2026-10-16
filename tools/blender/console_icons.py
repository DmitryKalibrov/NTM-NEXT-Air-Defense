# Icons for the element catalogue screen (run after console_parts.py): every element rendered on its own,
# textured, from the front and a little above, transparent background, packed 8 per row into
# textures/gui/console_icons.png (64 px cells, in the order of control_parts.json).
import bpy, json, math
from mathutils import Vector

R = "E:/ModPorting/ntm-air-defense/"
A = R + "common/src/main/resources/assets/ntm_air_defense/"
CELL, COLS = 64, 8
TMP = "C:/Users/calib/AppData/Local/Temp/claude/E--ModPorting/7b591362-784e-40f1-89f2-72147c70212b/scratchpad/icon.png"

names = list(json.load(open(A + "models/control_parts.json")).keys())
preview = {o for c in bpy.data.collections for o in c.objects}
sources = [o for o in bpy.data.objects if o.get("element") and o not in preview]
sc = bpy.context.scene
saved = {o.name: o.hide_render for o in bpy.data.objects}
old = (sc.camera, sc.render.resolution_x, sc.render.resolution_y, sc.render.film_transparent, sc.render.engine)

cam_data = bpy.data.cameras.new("icon_cam")
cam_data.type = "ORTHO"
cam = bpy.data.objects.new("icon_cam", cam_data)
sc.collection.objects.link(cam)
sun_data = bpy.data.lights.new("icon_sun", "SUN")
sun_data.energy = 3.0
sun = bpy.data.objects.new("icon_sun", sun_data)
sc.collection.objects.link(sun)
sun.rotation_euler = (math.radians(35), math.radians(-25), 0)
sc.camera = cam
sc.render.resolution_x = sc.render.resolution_y = CELL
sc.render.film_transparent = True
try:
    sc.render.engine = "BLENDER_EEVEE_NEXT"
except TypeError:
    sc.render.engine = "BLENDER_EEVEE"
sc.render.image_settings.file_format = "PNG"
sc.render.image_settings.color_mode = "RGBA"

rows = (len(names) + COLS - 1) // COLS
atlas = [0.0] * (COLS * CELL * rows * CELL * 4)
panel_view = Vector((0.45, 0.6, 1.0)).normalized()  # from the front of an upright panel, a little above and right
desk_view = Vector((0.45, -1.0, 0.8)).normalized()  # desk things (mount "top", Z up) from the operator's side
for i, name in enumerate(names):
    objs = [o for o in sources if o["element"] == name and not (o.name.startswith(name + "_pic") and o.name != name + "_pic0")]
    view = desk_view if objs[0].get("mount") == "top" else panel_view
    for o in bpy.data.objects:
        o.hide_render = o not in objs and o not in (cam, sun)
    pts = [o.matrix_world @ Vector(c) for o in objs for c in o.bound_box]
    lo = Vector([min(p[k] for p in pts) for k in range(3)])
    hi = Vector([max(p[k] for p in pts) for k in range(3)])
    centre, size = (lo + hi) / 2, (hi - lo).length
    cam.location = centre + view * (size * 3 + 0.5)
    cam.rotation_euler = (-view).to_track_quat("-Z", "Y").to_euler()
    cam_data.ortho_scale = size * 1.08
    sc.render.filepath = TMP
    bpy.ops.render.render(write_still=True)
    img = bpy.data.images.load(TMP, check_existing=False)
    px = list(img.pixels)
    bpy.data.images.remove(img)
    cx, cy = (i % COLS) * CELL, (rows - 1 - i // COLS) * CELL  # Blender pixels run bottom-up
    for y in range(CELL):
        row = ((cy + y) * COLS * CELL + cx) * 4
        atlas[row:row + CELL * 4] = px[y * CELL * 4:(y + 1) * CELL * 4]

out = bpy.data.images.get("console_icons") or bpy.data.images.new("console_icons", COLS * CELL, rows * CELL, alpha=True)
out.scale(COLS * CELL, rows * CELL)
out.pixels.foreach_set(atlas)
out.filepath_raw = A + "textures/gui/console_icons.png"
out.file_format = "PNG"
out.save()

for n, h in saved.items():
    if n in bpy.data.objects:
        bpy.data.objects[n].hide_render = h
bpy.data.objects.remove(cam)
bpy.data.objects.remove(sun)
sc.camera, sc.render.resolution_x, sc.render.resolution_y, sc.render.film_transparent = old[:4]
try:
    sc.render.engine = old[4]
except TypeError:
    pass
print("icons", len(names), "->", COLS * CELL, "x", rows * CELL)
