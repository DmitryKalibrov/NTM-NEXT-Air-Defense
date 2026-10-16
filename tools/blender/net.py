"""Vegetation camo net thrown over a vehicle: ONE continuous sheet. A height map of the hull is taken with rays
from above, the sheet rests on its high points and falls off them with a fixed slope down to the hull's bottom,
then is relaxed so it sags between the supports. Over moving parts (launcher, radars, hatches) the sheet is held
under their lowest point, so they poke through instead of cutting it. Unwrapped in one piece, the texture tiles
every CELL metres (textures repeat). Object "Net" in the vehicle's model space -> models/obj/net_<tag>.obj; run
tools/obj2mesh.py on it afterwards."""
import bpy, bmesh, math, random
from mathutils import Vector, noise

R = "E:/ModPorting/ntm-air-defense/"
A = R + "common/src/main/resources/assets/ntm_air_defense/"
CELL = 0.5    # metres per texture tile
STEP = 0.25   # grid spacing of the sheet


def _bbox(o):
    pts = [o.matrix_world @ Vector(c) for c in o.bound_box]
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]


def build_net(cover, moving, floor, cap=99.0, slope=2.2, clear=0.07, relax=3, seed=1, margin=1.0, hang=None):
    """cover: objects the net lies on; moving: objects it must stay under; floor: where it ends (hull bottom).
    hang: (radius, length) when the hull turns over a platform: past the radius the platform may not be under the
    hem (it depends on the yaw), so the hem hangs down there as a curtain, up to length below the floor."""
    lo, hi = Vector((1e9, 1e9, 0)), Vector((-1e9, -1e9, 0))
    for o in cover:
        a, b = _bbox(o)
        lo.x, lo.y, hi.x, hi.y = min(lo.x, a[0]), min(lo.y, a[1]), max(hi.x, b[0]), max(hi.y, b[1])
    nx, ny = int((hi.x - lo.x + 2 * margin) / STEP) + 1, int((hi.y - lo.y + 2 * margin) / STEP) + 1
    x0, y0 = lo.x - margin, lo.y - margin
    xy = lambda i, j: (x0 + i * STEP, y0 + j * STEP)
    keep = []
    for o in moving:
        a, b = _bbox(o)
        keep.append((a[0] - 0.1, b[0] + 0.1, a[1] - 0.1, b[1] + 0.1, a[2] - 0.05))

    def limit(x, y):
        c = cap
        for kx0, kx1, ky0, ky1, kz in keep:
            if kx0 <= x <= kx1 and ky0 <= y <= ky1:
                c = min(c, kz)
        return c

    # height map of the hull under each grid point (several rays per cell so thin edges aren't missed)
    H = {}
    for i in range(nx):
        for j in range(ny):
            x, y = xy(i, j)
            best = None
            for ox, oy in ((0, 0), (0.1, 0.1), (-0.1, 0.1), (0.1, -0.1), (-0.1, -0.1)):
                for o in cover:
                    inv = o.matrix_world.inverted()
                    ok, loc, _, _ = o.ray_cast(inv @ Vector((x + ox, y + oy, 50)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
                    if ok:
                        z = (o.matrix_world @ loc).z
                        best = z if best is None else max(best, z)
            if best is not None:
                H[i, j] = min(best + clear, limit(x, y))
    # drape: every point hangs from the highest support reachable with the slope
    sup = list(H.items())
    Z = {}
    for i in range(nx):
        for j in range(ny):
            z = floor
            for (si, sj), h in sup:
                d = math.hypot(si - i, sj - j) * STEP
                z = max(z, h - slope * d)
            Z[i, j] = z
    # relax: the sheet sags between supports (never through the hull, never above a moving part)
    for _ in range(relax):
        N = {}
        for (i, j), z in Z.items():
            nb = [Z[k] for k in ((i - 1, j), (i + 1, j), (i, j - 1), (i, j + 1)) if k in Z]
            avg = sum(nb) / len(nb)
            # off the hull the sheet keeps falling steeply (no ledge), it only sags where it rests on something
            z2 = 0.5 * z + 0.5 * avg if (i, j) in H else min(z, 0.5 * z + 0.5 * avg)
            if (i, j) in H:
                z2 = max(z2, H[i, j] - 0.02)
            N[i, j] = min(max(z2, floor), limit(*xy(i, j)) if (i, j) in H else 99)
        Z = N
    # wrinkles
    for (i, j), z in Z.items():
        if z > floor + 0.02:
            x, y = xy(i, j)
            Z[i, j] = z + noise.noise(Vector((x * 1.7, y * 1.7, seed))) * 0.05

    bm = bmesh.new()
    V = {k: bm.verts.new((*xy(*k), max(z, floor))) for k, z in Z.items()}
    for i in range(nx - 1):
        for j in range(ny - 1):
            q = [(i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)]
            if all(Z[k] <= floor + 0.03 for k in q):
                continue  # beyond the hem
            bm.faces.new([V[k] for k in q])
    for v in [v for v in bm.verts if not v.link_faces]:
        bm.verts.remove(v)
    # keep the one piece that touches the top (drop stray fragments, if any)
    bm.faces.ensure_lookup_table()
    seen, best = set(), []
    for f in bm.faces:
        if f in seen:
            continue
        comp, stack = [], [f]
        seen.add(f)
        while stack:
            g = stack.pop()
            comp.append(g)
            for e in g.edges:
                for h in e.link_faces:
                    if h not in seen:
                        seen.add(h)
                        stack.append(h)
        if len(comp) > len(best):
            best = comp
    keepf = set(best)
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if f not in keepf], context="FACES")
    if hang:
        _curtain(bm, floor, *hang, seed)
    me = bpy.data.meshes.new("Net")
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new("Net", me)
    bpy.context.scene.collection.objects.link(o)
    _unwrap(o)
    print("net", len(me.polygons), "faces, pieces kept 1 of", "?")
    return o


def _curtain(bm, floor, radius, length, seed):
    """The hem past the radius hangs down in three ragged rows; it fades in over 0.8 m so nothing hangs into the
    platform where it is always under the net."""
    rnd = random.Random(seed)
    reach = lambda v: max(0.0, min(1.0, (math.hypot(v.co.x, v.co.y) - radius) / 0.8))
    edges = [e for e in bm.edges if e.is_boundary and all(reach(v) > 0.05 and v.co.z < floor + 0.15 for v in e.verts)]
    for _ in range(3):
        if not edges:
            break
        geom = bmesh.ops.extrude_edge_only(bm, edges=edges)["geom"]
        new = {g for g in geom if isinstance(g, bmesh.types.BMVert)}
        for v in new:
            v.co.z -= length * reach(v) / 3 * rnd.uniform(0.7, 1.3)
        edges = [g for g in geom if isinstance(g, bmesh.types.BMEdge) and all(x in new for x in g.verts)]


def _unwrap(o):
    vl = bpy.context.view_layer
    bpy.ops.object.select_all(action="DESELECT")
    o.select_set(True, view_layer=vl)
    vl.objects.active = o
    o.data.uv_layers.new(name="UVMap")
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.unwrap(method="CONFORMAL", margin=0.0)
    bpy.ops.object.mode_set(mode="OBJECT")
    # scale the single island so a tile covers CELL metres
    bm = bmesh.new()
    bm.from_mesh(o.data)
    uv = bm.loops.layers.uv.active
    a3 = sum(f.calc_area() for f in bm.faces)
    a2 = 0.0
    for f in bm.faces:
        p = [l[uv].uv for l in f.loops]
        a2 += abs(sum(p[k].x * p[k - 1].y - p[k - 1].x * p[k].y for k in range(len(p)))) / 2
    s = math.sqrt(a3 / a2) / CELL
    for f in bm.faces:
        for l in f.loops:
            l[uv].uv *= s
    bm.to_mesh(o.data)
    bm.free()


def export_net(o, tag):
    vl = bpy.context.view_layer
    bpy.ops.object.select_all(action="DESELECT")
    o.select_set(True, view_layer=vl)
    vl.objects.active = o
    bpy.ops.wm.obj_export(filepath=A + "models/obj/net_" + tag + ".obj", export_selected_objects=True, export_uv=True,
                          export_normals=True, export_materials=False, export_triangulated_mesh=True,
                          forward_axis="NEGATIVE_Z", up_axis="Y")
    print("exported net", tag, len(o.data.polygons), "faces")


def preview(o, scheme="green"):
    img = bpy.data.images.load(A + "textures/block/camo_net_%s.png" % scheme, check_existing=True)
    m = bpy.data.materials.new("net_prev")
    m.use_nodes = True
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = img
    t.interpolation = "Closest"
    nt.links.new(t.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(t.outputs["Alpha"], bsdf.inputs["Alpha"])
    o.data.materials.append(m)
