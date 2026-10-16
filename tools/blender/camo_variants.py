"""Paint-scheme variants of a baked model: recolours the paint materials and re-bakes onto the UVs the .blend
already has (no new unwrap, so the OBJ stays as it is). Writes <tex>_desert.png, <tex>_winter.png; draw the
outlines on them afterwards with tools/outline.py like on the base texture."""
import bpy

R = "E:/ModPorting/ntm-air-defense/"
A = R + "common/src/main/resources/assets/ntm_air_defense/"

# three-tone camo ramps (dark, mid, light) and plain paint (light / dark panels), sRGB
SCHEMES = {
    "desert": {"camo": [(96, 78, 52), (168, 146, 104), (206, 188, 148)], "paint": (152, 132, 96), "paint_dark": (118, 100, 72)},
    "winter": {"camo": [(78, 90, 60), (214, 218, 214), (234, 236, 232)], "paint": (206, 210, 206), "paint_dark": (172, 178, 174)},
}
CAMO = ("camo",)                                  # three-element ramps (the Nebo's mast is "olive" with three)
PAINT = ("radome", "tube", "olive")
PAINT_DARK = ("olive_dark", "olive_d", "cap")


def lin(c):
    return [((v / 255) / 12.92 if v / 255 <= 0.04045 else ((v / 255 + 0.055) / 1.055) ** 2.4) for v in c]


def recolour(m, s):
    """Returns the old ramp colours so they can be put back."""
    saved = []
    for n in m.node_tree.nodes:
        if n.type != "VALTORGB":
            continue
        els = n.color_ramp.elements
        saved.append((n, [tuple(e.color) for e in els]))
        if len(els) == 3 and (m.name in CAMO or m.name == "olive"):
            for e, c in zip(els, s["camo"]):
                e.color = lin(c) + [1]
        elif len(els) == 2 and (m.name in PAINT or m.name in PAINT_DARK):
            top = lin(s["paint_dark" if m.name in PAINT_DARK else "paint"])
            old = list(els[1].color)
            for e in els:  # keep the grime contrast of the ramp
                e.color = [t * (e.color[i] / max(old[i], 1e-4)) for i, t in enumerate(top)] + [1]
    return saved


def variants(tag, size, tex):
    objs = [o for o in bpy.data.objects if o.type == "MESH" and o.data.uv_layers]
    vl = bpy.context.view_layer
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.hide_set(False, view_layer=vl)
        o.hide_render = False
        o.select_set(True, view_layer=vl)
    vl.objects.active = objs[0]
    mats = {s.material for o in objs for s in o.material_slots if s.material}
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.samples = 8
    sc.cycles.device = "CPU"
    sc.render.bake.use_pass_direct = False
    sc.render.bake.use_pass_indirect = False
    sc.render.bake.margin = 2
    for name, s in SCHEMES.items():
        img = bpy.data.images.new(tag + "_" + name, size, size)
        saved, nodes = [], []
        for m in mats:
            saved += recolour(m, s)
            t = m.node_tree.nodes.new("ShaderNodeTexImage")
            t.image = img
            m.node_tree.nodes.active = t
            nodes.append((m, t))
        bpy.ops.object.bake(type="DIFFUSE", pass_filter={"COLOR"})
        img.filepath_raw = A + tex.replace(".png", "_" + name + ".png")
        img.file_format = "PNG"
        img.save()
        for n, cols in saved:
            for e, c in zip(n.color_ramp.elements, cols):
                e.color = c
        for m, t in nodes:
            m.node_tree.nodes.remove(t)
        print("baked", tag, name)
