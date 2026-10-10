import bpy, bmesh, math, json

R = "E:/ModPorting/ntm-air-defense/"
A = R + "src/main/resources/assets/ntm_air_defense/"
SHARP = math.radians(46)  # 8- and 10-sided cylinders (45 and 36 deg facets) stay smooth
NO_OUTLINE = ("Tracer",)
PREVIEW_MOVED = ("BukMissile", "TorMissile", "PantsirStage", "PantsirDart")


def sharp_lines(objs):
    """UV segments of the sharp edges only (crease > 35 deg or open borders): panel lines, not facets of round parts."""
    segs = []
    for o in objs:
        if o.name in NO_OUTLINE:
            continue
        bm = bmesh.new()
        bm.from_mesh(o.data)
        uv = bm.loops.layers.uv.active
        for e in bm.edges:
            faces = e.link_faces
            if len(faces) == 2 and faces[0].normal.angle(faces[1].normal, 0) < SHARP:
                continue
            for loop in e.link_loops:
                segs.append([list(loop[uv].uv), list(loop.link_loop_next[uv].uv)])
        bm.free()
    return segs


def bake(tag, size, tex, obj, blend):
    objs = [o for o in bpy.data.objects if o.type == "MESH"]
    vl = bpy.context.view_layer
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.rotation_euler = (0, 0, 0)
        if o.name in PREVIEW_MOVED:
            o.location = (0, 0, 0)
        o.hide_set(False, view_layer=vl)
        o.select_set(True, view_layer=vl)
        for uv in list(o.data.uv_layers):
            o.data.uv_layers.remove(uv)
        o.data.uv_layers.new(name="UVMap")
    vl.objects.active = objs[0]
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(50), island_margin=0.003, scale_to_bounds=False)
    bpy.ops.object.mode_set(mode="OBJECT")
    img = bpy.data.images.new(tag + "_bake", size, size)
    for m in {s.material for o in objs for s in o.material_slots}:
        t = m.node_tree.nodes.new("ShaderNodeTexImage")
        t.image = img
        m.node_tree.nodes.active = t
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.samples = 8
    sc.cycles.device = "CPU"
    sc.render.bake.use_pass_direct = False
    sc.render.bake.use_pass_indirect = False
    sc.render.bake.margin = 2
    bpy.ops.object.bake(type="DIFFUSE", pass_filter={"COLOR"})
    img.filepath_raw = A + tex
    img.file_format = "PNG"
    img.save()
    json.dump(sharp_lines(objs), open(R + "build/" + tag + "_outlines.json", "w"))
    bpy.ops.wm.obj_export(filepath=A + obj, export_selected_objects=True, export_uv=True, export_normals=True,
                          export_materials=False, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y")
    bpy.ops.wm.save_as_mainfile(filepath=R + blend)
    print("baked", tag)
