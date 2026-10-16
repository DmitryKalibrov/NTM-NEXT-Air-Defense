# Pixel texture for the control room parts (run after console.py).
# 32 px per block. Every face is projected onto its own plane in block coordinates, so a face's position
# in the block picks the same texels wherever it is: the seam border always lands on the block edge.
# Each material index of console.py is one 32 x 32 tile of the atlas; the blue atlas swaps the face tile.
import numpy as np

R = "E:/ModPorting/ntm-air-defense/"
TEX = R + "common/src/main/resources/assets/ntm_air_defense/textures/block/"
T = 32
COLS, ROWS = 4, 2
W, H = COLS * T, ROWS * T
rng = np.random.default_rng(7)


def tile(base, seam=0.78, rivets=False, grain=4, lines=()):
    c = np.ones((T, T, 3)) * np.array(base, float)
    c += rng.integers(-grain, grain + 1, (T, T, 1))
    c[0, :] *= seam; c[-1, :] *= seam; c[:, 0] *= seam; c[:, -1] *= seam
    for v in lines:  # horizontal panel joints, v in px from the bottom
        c[v, 1:-1] *= 0.84
        c[v + 1, 1:-1] *= 1.06
    if rivets:
        for u, v in ((3, 3), (T - 4, 3), (3, T - 4), (T - 4, T - 4)):
            c[v, u] *= 0.6
            c[v + 1, u] *= 1.15
    return np.clip(c, 0, 255)


def atlas(face):
    img = np.zeros((H, W, 4))
    img[..., 3] = 255
    tiles = [face,                                            # G   faces of panels and cabinets
             tile((112, 150, 136), lines=(16,)),              # GD  sides, backs, aprons
             tile((170, 204, 190), seam=0.86),                # GL  desk writing surfaces
             tile((38, 78, 128), rivets=True),                # BL  mimic / tilted tier
             tile((40, 42, 41), seam=0.9, grain=3)]           # BK  plinths
    for i, t in enumerate(tiles):
        col, row = i % COLS, i // COLS
        img[row * T:(row + 1) * T, col * T:(col + 1) * T, :3] = t
    return img


def save(name, img):
    im = bpy.data.images.get(name) or bpy.data.images.new(name, W, H)
    im.scale(W, H)
    im.pixels.foreach_set((img / 255).ravel().astype(np.float32))
    im.filepath_raw = TEX + name + ".png"
    im.file_format = "PNG"
    im.save()
    return im


green = save("control_console", atlas(tile((146, 186, 170), rivets=True)))
save("control_console_blue", atlas(tile((38, 78, 128), rivets=True)))


def anchor(vals, aligned):
    lo = min(vals)
    if aligned:
        b = math.floor(lo / T + 1e-4) * T
        if max(vals) - b <= T + 1e-3:
            return b, 1.0
    return lo, min(1.0, T / max(1e-6, max(vals) - lo))


def unwrap(o):
    me = o.data
    bm = bmesh.new()
    bm.from_mesh(me)
    uv = bm.loops.layers.uv.verify()
    off = Vector((0.5, 0.5, 0))  # block corner -> 0
    for f in bm.faces:
        n = f.normal
        if abs(n.z) > 0.99:
            ua, va = Vector((1, 0, 0)), Vector((0, 1, 0))
        else:
            ua = n.cross(Vector((0, 0, 1))).normalized()
            va = ua.cross(n).normalized() if abs(n.z) > 0.01 else Vector((0, 0, 1))
        pts = [((l.vert.co + off).dot(ua) * T, (l.vert.co + off).dot(va) * T) for l in f.loops]
        # axis-aligned faces keep block-anchored texels; diagonal/sloped ones start at 0 and squeeze into the tile
        u0, su = anchor([p[0] for p in pts], abs(ua.x) > 0.999 or abs(ua.y) > 0.999)
        v0, sv = anchor([p[1] for p in pts], abs(n.z) < 0.01 or abs(n.z) > 0.99)
        col, row = f.material_index % COLS, f.material_index // COLS
        for l, (u, v) in zip(f.loops, pts):
            l[uv].uv = ((col * T + (u - u0) * su) / W, (row * T + (v - v0) * sv) / H)
    bm.to_mesh(me)
    bm.free()


tex_mat = bpy.data.materials.get("console_tex") or bpy.data.materials.new("console_tex")
tex_mat.use_nodes = True
nt = tex_mat.node_tree
node = next((n for n in nt.nodes if n.type == "TEX_IMAGE"), None) or nt.nodes.new("ShaderNodeTexImage")
node.image = green
node.interpolation = "Closest"
bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
nt.links.new(node.outputs["Color"], bsdf.inputs["Base Color"])
bsdf.inputs["Roughness"].default_value = 0.8

done = set()
for o in bpy.data.objects:
    if o.type == "MESH" and o.data.name not in done:
        done.add(o.data.name)
        unwrap(o)
        for i in range(len(o.data.materials)):
            o.data.materials[i] = tex_mat
print("textured", len(done))


# Grow the welded variants into their neighbours (after unwrapping, so the texels stay put): an overlap of
# 0.002 blocks under seam-coloured texels closes the hairlines between separately drawn blocks.
for o in bpy.data.objects:
    g = o.get("grow")
    if not g:
        continue
    for v in o.data.vertices:
        c = v.co
        if "l" in g and c.x < -0.5 + 1e-4: c.x -= 0.002
        if "r" in g and c.x > 0.5 - 1e-4: c.x += 0.002
        if "b" in g and c.z < 1e-4: c.z -= 0.002
        if "t" in g and abs(c.z - 1) < 1e-4: c.z += 0.002
print("grown")
