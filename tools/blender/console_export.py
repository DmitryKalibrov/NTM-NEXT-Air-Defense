# Export the control room models (run after console.py, console_tex.py and console_parts.py in the same Blender).
# control_console.obj: block parts in block space, Blender front -Y -> OBJ +Z, like the other models of the addon.
# control_parts.obj: panel elements in their own panel space, unchanged (X right, Y up the panel, Z out of it),
#   in blocks (1 grid px = 1/16); control_parts.json carries each element's footprint, mount and part pivots.
import bpy, json

R = "E:/ModPorting/ntm-air-defense/"
A = R + "common/src/main/resources/assets/ntm_air_defense/"


def export(objs, path, forward, up):
    vl = bpy.context.view_layer
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.hide_set(False, view_layer=vl)
        o.select_set(True, view_layer=vl)
    vl.objects.active = objs[0]
    bpy.ops.wm.obj_export(filepath=path, export_selected_objects=True, export_uv=True, export_normals=True,
                          export_materials=False, export_triangulated_mesh=True, forward_axis=forward, up_axis=up)


preview = {o for c in bpy.data.collections for o in c.objects}
blocks = [o for o in bpy.data.objects if o.type == "MESH" and o not in preview and not o.get("element")]
export(blocks, A + "models/obj/control_console.obj", "NEGATIVE_Z", "Y")
for o in blocks:  # the sources stay hidden: the preview shows copies of them
    o.hide_set(True)

elements = [o for o in bpy.data.objects if o.get("element") and o not in preview]
spots = {o: o.location.copy() for o in elements}
for o in elements:
    o.location = (0, 0, 0)
export(elements, A + "models/obj/control_parts.obj", "Y", "Z")
for o, at in spots.items():
    o.location = at

info = {}
for o in elements:
    e = info.setdefault(o["element"], {"size": list(o["size"]), "mount": o.get("mount", "any"), "parts": {}})
    part = o.name[len(o["element"]) + 1:] if o.name != o["element"] else ""
    if part:
        e["parts"][part] = {"pivot": [v / 16 for v in o["pivot"]]} if "pivot" in o else {}
json.dump(info, open(A + "models/control_parts.json", "w"), indent=1, sort_keys=True)
print(len(blocks), "block parts,", len(elements), "element meshes,", len(info), "elements")
