import bpy, bmesh, math
from mathutils import Matrix, Vector


def _n(nt, kind, **inputs):
    node = nt.nodes.new(kind)
    for k, v in inputs.items():
        node.inputs[k].default_value = v
    return node


def mat(name, build):
    m = bpy.data.materials.get(name)
    if m:
        return m
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.links.new(build(nt), nt.nodes["Principled BSDF"].inputs["Base Color"])
    return m


def lin(c):
    """sRGB 0..255 (as sampled from the Buk/Tor textures) to the linear values shader nodes take."""
    return [((v / 255) ** 2.2) for v in c]


def camo(dark=(57, 61, 45), mid=(90, 99, 59), sand=(140, 132, 107), scale=0.45):
    """Buk/Tor style three-tone camouflage: hard-edged blobs in object space, faint grain on top."""
    def b(nt):
        co = nt.nodes.new("ShaderNodeTexCoord")
        blob = _n(nt, "ShaderNodeTexNoise", Scale=scale, Detail=1.5, Roughness=0.4)
        nt.links.new(co.outputs["Object"], blob.inputs["Vector"])
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.interpolation = "CONSTANT"
        els = ramp.color_ramp.elements
        els[0].position, els[0].color = 0.0, lin(dark) + [1]
        els[1].position, els[1].color = 0.43, lin(mid) + [1]
        e = els.new(0.6); e.color = lin(sand) + [1]
        nt.links.new(blob.outputs["Fac"], ramp.inputs["Fac"])
        grain = _n(nt, "ShaderNodeTexNoise", Scale=40.0, Detail=2.0)
        nt.links.new(co.outputs["Object"], grain.inputs["Vector"])
        g = nt.nodes.new("ShaderNodeMapRange")
        g.inputs["To Min"].default_value, g.inputs["To Max"].default_value = 0.88, 1.06
        nt.links.new(grain.outputs["Fac"], g.inputs["Value"])
        mul = nt.nodes.new("ShaderNodeMixRGB"); mul.blend_type = "MULTIPLY"; mul.inputs["Fac"].default_value = 1.0
        nt.links.new(ramp.outputs["Color"], mul.inputs["Color1"])
        cv = nt.nodes.new("ShaderNodeCombineColor")
        for k in ("Red", "Green", "Blue"):
            nt.links.new(g.outputs["Result"], cv.inputs[k])
        nt.links.new(cv.outputs["Color"], mul.inputs["Color2"])
        return mul.outputs["Color"]
    return b


def rgb(c):
    def b(nt):
        r = nt.nodes.new("ShaderNodeRGB")
        r.outputs[0].default_value = lin(c) + [1]
        return r.outputs[0]
    return b


def grime(base, scale=2.0, streak=True):
    def b(nt):
        co = nt.nodes.new("ShaderNodeTexCoord")
        blot = _n(nt, "ShaderNodeTexNoise", Scale=scale, Detail=8.0)
        nt.links.new(co.outputs["Object"], blot.inputs["Vector"])
        f = blot.outputs["Fac"]
        if streak:
            mp = nt.nodes.new("ShaderNodeMapping")
            mp.inputs["Scale"].default_value = (8, 8, 0.5)
            nt.links.new(co.outputs["Object"], mp.inputs["Vector"])
            st = _n(nt, "ShaderNodeTexNoise", Scale=3.0, Detail=2.0)
            nt.links.new(mp.outputs["Vector"], st.inputs["Vector"])
            m = nt.nodes.new("ShaderNodeMath")
            m.operation = "MULTIPLY"
            nt.links.new(f, m.inputs[0])
            nt.links.new(st.outputs["Fac"], m.inputs[1])
            f = m.outputs[0]
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.elements[0].position = 0.15
        ramp.color_ramp.elements[1].position = 0.45
        ramp.color_ramp.elements[0].color = lin([c * 0.8 for c in base]) + [1]
        ramp.color_ramp.elements[1].color = lin([min(255, c * 1.08) for c in base]) + [1]
        nt.links.new(f, ramp.inputs["Fac"])
        return ramp.outputs["Color"]
    return b


def hazard(nt):
    co = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(co.outputs["Object"], sep.inputs[0])
    add = nt.nodes.new("ShaderNodeMath")
    add.operation = "ADD"
    nt.links.new(sep.outputs["X"], add.inputs[0])
    nt.links.new(sep.outputs["Y"], add.inputs[1])
    mul = nt.nodes.new("ShaderNodeMath")
    mul.operation = "MULTIPLY"
    mul.inputs[1].default_value = 5.0
    nt.links.new(add.outputs[0], mul.inputs[0])
    fr = nt.nodes.new("ShaderNodeMath")
    fr.operation = "FRACT"
    nt.links.new(mul.outputs[0], fr.inputs[0])
    gt = nt.nodes.new("ShaderNodeMath")
    gt.operation = "GREATER_THAN"
    gt.inputs[1].default_value = 0.5
    nt.links.new(fr.outputs[0], gt.inputs[0])
    mix = nt.nodes.new("ShaderNodeMixRGB")
    mix.inputs["Color1"].default_value = lin((214, 168, 40)) + [1]
    mix.inputs["Color2"].default_value = lin((35, 35, 33)) + [1]
    nt.links.new(gt.outputs[0], mix.inputs["Fac"])
    return mix.outputs["Color"]


M_CONC = mat("concrete", grime((128, 125, 116)))
M_CONC_DARK = mat("concrete_dark", grime((96, 94, 88)))
M_STEEL = mat("steel", grime((70, 74, 78), scale=6.0, streak=False))
M_LINER = mat("liner", grime((40, 43, 42), scale=5.0))
M_ARMOR = mat("armor", camo())
M_HAZARD = mat("hazard", hazard)
M_SOOT = mat("soot", rgb((22, 22, 21)))


def put(o, m):
    o.data.materials.clear()
    o.data.materials.append(m)
    return o


def cube(name, lo, hi, m, bevel=0.0):
    bpy.ops.mesh.primitive_cube_add(size=1)
    o = bpy.context.active_object
    o.name = name
    c = [(a + b) / 2 for a, b in zip(lo, hi)]
    s = [b - a for a, b in zip(lo, hi)]
    o.data.transform(Matrix.Translation(c) @ Matrix.Diagonal(Vector(s)).to_4x4())
    if bevel >= 0.1:  # only big structural chamfers, as one flat face; small parts stay sharp boxes
        mod = o.modifiers.new("bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 1
        mod.limit_method = "ANGLE"
    return put(o, m)


def cyl(name, r, z0, z1, m, verts=32, axis="Z", at=(0, 0, 0)):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=z1 - z0)
    o = bpy.context.active_object
    o.name = name
    mtx = Matrix.Translation((0, 0, (z0 + z1) / 2))
    if axis == "Y":
        mtx = Matrix.Rotation(math.pi / 2, 4, "X") @ mtx
    if axis == "X":
        mtx = Matrix.Rotation(math.pi / 2, 4, "Y") @ mtx
    o.data.transform(Matrix.Translation(at) @ mtx)
    return put(o, m)


def bool_cut(target, cutter):
    mod = target.modifiers.new("cut", "BOOLEAN")
    mod.object = cutter
    mod.operation = "DIFFERENCE"
    mod.solver = "EXACT"
    bpy.context.view_layer.objects.active = target
    bpy.ops.object.modifier_apply(modifier="cut")
    bpy.data.objects.remove(cutter)


def join(name, objs):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.convert(target="MESH")
    bpy.ops.object.join()
    o = bpy.context.active_object
    o.name = o.data.name = name
    return o
