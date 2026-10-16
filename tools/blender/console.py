# Control room modules (Soviet plant control room style), front = -Y, 1/16 block pixel grid.
# Every part is a profile extruded along X: no overlapping boxes. Blocks are cut into slices instead of overlays:
# an end slice is either plain body (EdgeL/R, EdgeB/T) or its decorated replacement (Cheek, Plinth, Cornice),
# the renderer picks one per merged run. Where two slices or two blocks touch, exactly ONE face is built
# (profile sides marked None and the X caps are left out), so no coplanar face pairs flicker on the seams.
# The X ends of a run are closed by the separate <part>CapL / <part>CapR faces.
# Corner parts (In = inner/concave, Out = outer/convex, 90 deg) are the same profile mitred at 45 deg.
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
for c in list(bpy.data.collections):
    bpy.data.collections.remove(c)

# flat paint: wear, panel lines and rivets go into the pixel texture, not into noise
MATS = [
    mat("cr_green", rgb((146, 186, 170))),
    mat("cr_green_dark", rgb((112, 150, 136))),
    mat("cr_green_light", rgb((170, 204, 190))),
    mat("cr_blue", rgb((38, 78, 128))),
    mat("cr_black", rgb((40, 42, 41))),
]
G, GD, GL, BL, BK = range(5)


def P(v):
    return v / 16 - 0.5


def prism_bm(poly, x0, x1, cap=G, caps=(False, False)):
    """poly: [(y, z, mat of the side running to the next point, None = no face)] in px; extruded from x0 to x1.
    Faces are wound explicitly (outward), so open slices keep correct normals."""
    if sum(poly[i - 1][0] * p[1] - p[0] * poly[i - 1][1] for i, p in enumerate(poly)) < 0:  # make it CCW in (y, z)
        poly = [(y, z, poly[i - 1][2]) for i, (y, z, _) in enumerate(poly)][::-1]
    bm = bmesh.new()
    a = [bm.verts.new((P(x0), P(y), z / 16)) for y, z, _ in poly]
    b = [bm.verts.new((P(x1), P(y), z / 16)) for y, z, _ in poly]
    n = len(poly)
    for i in range(n):
        if poly[i][2] is not None:
            j = (i + 1) % n
            bm.faces.new((a[i], a[j], b[j], b[i])).material_index = poly[i][2]
    if caps[0]:
        bm.faces.new(a[::-1]).material_index = cap
    if caps[1]:
        bm.faces.new(b).material_index = cap
    for v in [v for v in bm.verts if not v.link_faces]:
        bm.verts.remove(v)
    bm.normal_update()
    return bm


def to_obj(name, bm):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for m in MATS:
        me.materials.append(m)
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    return o


def part(name, poly, x0=0, x1=16, cap=G, caps=(False, False)):
    return to_obj(name, prism_bm(poly, x0, x1, cap, caps))


def end_caps(name, poly, cap=G):
    """Profile faces closing the west (CapL) and east (CapR) end of a run."""
    flat = [(y, z, None) for y, z, _ in poly]
    to_obj(name + "CapL", prism_bm(flat, 0, 16, cap, (True, False)))
    to_obj(name + "CapR", prism_bm(flat, 0, 16, cap, (False, True)))


def corner(name, poly, outer, cap=G):
    """Run A enters through the west face (front -Y). Inner: run B leaves through the south face, front -X.
    Outer: run B leaves through the north face, front +X. Mitre on the block diagonal."""
    halves = []
    for side in (0, 1):
        bm = prism_bm(poly, 0, 16, cap)
        if side:
            bmesh.ops.rotate(bm, verts=bm.verts[:], cent=(0, 0, 0),
                             matrix=Matrix.Rotation(math.pi / 2 if outer else -math.pi / 2, 3, "Z"))
        no = (1, 1, 0) if outer else (1, -1, 0)
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=(0, 0, 0), plane_no=no,
                               clear_outer=not side, clear_inner=bool(side))
        halves.append(bm)
    bm = halves[0]
    me = bpy.data.meshes.new("tmp")
    halves[1].to_mesh(me)
    halves[1].free()
    bm.from_mesh(me)
    bpy.data.meshes.remove(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=1e-5)
    return to_obj(name, bm)


def family(prefix, poly, cap=G):
    part(prefix, poly, cap=cap)
    end_caps(prefix, poly, cap)
    corner(prefix + "In", poly, False, cap)
    corner(prefix + "Out", poly, True, cap)


# ---------------- Desk ----------------
# 1.5 blocks tall (a working height for a 1.8 block player), the part above the block is drawn and collides
# like a fence. (y, z) from the plinth round: recessed plinth, cabinet, apron, ledge at z 15, slope to z 19.5
# (16 x 9 grid), back strip (16 x 4 grid), top at z 24, back (split at z 16 so its texels stay block-sized), bottom
DESK = [(3, 0, BK), (3, 1, G), (2, 1, G), (2, 13, GD), (0, 13, GD), (0, 15, GL), (3, 15, GL), (11, 19.5, GL),
        (11, 24, G), (16, 24, G), (16, 16, G), (16, 0, BK)]
CHEEK = [(-0.25, 0, GD), (-0.25, 15.5, GD), (3, 15.5, GD), (10.25, 19.6, GD), (10.25, 24.5, GD), (16, 24.5, GD),
         (16, 16, GD), (16, 0, GD)]
part("DeskCore", DESK, 1, 15)
part("DeskEdgeL", DESK, 0, 1)
part("DeskEdgeR", DESK, 15, 16)
# closed on both sides: where the cheek stands proud of the desk its inner side shows
part("DeskCheekL", CHEEK, 0, 1, GD, (True, True))
part("DeskCheekR", CHEEK, 15, 16, GD, (True, True))
corner("DeskIn", DESK, False)
corner("DeskOut", DESK, True)


# ---------------- Wall panel (flat) ----------------
# face at y = 1 (16 x 16 grid); slices: bottom px (EdgeB | Plinth), middle, top px (EdgeT | Cornice).
# Between slices the lower one keeps its top face and the upper one has no bottom face.
def rect(y0, z0, z1, face, bottom=True):
    return [(y0, z0, face), (y0, z1, G), (16, z1, GD), (16, z0, GD if bottom else None)]


family("PanelEdgeB", rect(1, 0, 1, G, False))
family("PanelPlinth", rect(2, 0, 1, BK), BK)
family("PanelCore", rect(1, 1, 15, G, False))
family("PanelEdgeT", rect(1, 15, 16, G, False))
family("PanelCornice", [(0, 15, GD), (0, 16, G), (16, 16, GD), (16, 15, None), (1, 15, GD)], GD)

# ---------------- Wall panel (tilted top tier) ----------------
# sits on a flat panel (whose top face shows as the shelf in front); face leans towards the operator
# from y = 5 at the bottom to y = 1.25 at z 15
family("TiltCore", [(5, 0, BL), (1.25, 15, G), (16, 15, GD), (16, 0, GD)])  # own bottom: it may stand on anything
family("TiltEdgeT", [(1.25, 15, BL), (1, 16, G), (16, 16, GD), (16, 15, None)])
family("TiltCornice", [(0, 15, GD), (0, 16, G), (16, 16, GD), (16, 15, None), (1.25, 15, GD)], GD)


# ---------------- Welded block variants ----------------
# What the game draws: per block, one mesh for its neighbour situation (slices joined, shared vertices merged),
# so no hairline opens between slices. "grow" marks the sides that meet a neighbour: console_tex.py pushes
# those vertices 0.002 into it after unwrapping, so seams between blocks (drawn separately) can't crack either.
def variant(name, parts, grow):
    bm = bmesh.new()
    for n in parts:
        bm.from_mesh(bpy.data.objects[n].data)
    bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=1e-5)
    o = to_obj(name, bm)
    o["grow"] = grow
    return o


for l in (0, 1):
    for r in (0, 1):
        variant(f"Desk_{l}{r}", ["DeskCore", "DeskEdgeL" if l else "DeskCheekL", "DeskEdgeR" if r else "DeskCheekR"],
                "l" * l + "r" * r)
        for t in (0, 1):
            slices = ["TiltCore", "TiltEdgeT" if t else "TiltCornice"]
            variant(f"Tilt_{t}{l}{r}", slices + [x + "CapL" for x in slices if not l] + [x + "CapR" for x in slices if not r],
                    "l" * l + "r" * r + "t" * t)
            for b in (0, 1):
                slices = ["PanelEdgeB" if b else "PanelPlinth", "PanelCore", "PanelEdgeT" if t else "PanelCornice"]
                variant(f"Panel_{b}{t}{l}{r}", slices + [x + "CapL" for x in slices if not l] + [x + "CapR" for x in slices if not r],
                        "l" * l + "r" * r + "b" * b + "t" * t)
# corners (no neighbour test across the mitre: both arms always continue into a run)
for k in ("In", "Out"):
    variant(f"DeskCorner{k}", [f"Desk{k}"], "")
    for t in (0, 1):
        variant(f"TiltCorner{k}_{t}", [f"TiltCore{k}", f"TiltEdgeT{k}" if t else f"TiltCornice{k}"], "t" * t)
        for b in (0, 1):
            variant(f"PanelCorner{k}_{b}{t}", [f"PanelEdgeB{k}" if b else f"PanelPlinth{k}", f"PanelCore{k}",
                                               f"PanelEdgeT{k}" if t else f"PanelCornice{k}"], "b" * b + "t" * t)
print(len(bpy.data.objects), "parts")
