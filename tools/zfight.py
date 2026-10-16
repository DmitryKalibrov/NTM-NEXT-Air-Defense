"""Find and fix z-fighting in an exported OBJ: faces of two separate pieces (primitives joined into one object,
or different objects) lying in the same plane, facing the same way and overlapping - the GPU can't tell
which is in front, so the surface flickers. The smaller piece of each pair is pushed out along the face
normal by EPS (a sixth of a texture pixel: invisible, but enough depth for a camera tens of blocks away),
and the check repeats until nothing overlaps.

usage: python zfight.py model.obj [--fix] [--cross]    (then recompile with obj2mesh.py)
--cross also checks faces of different groups against each other: only for models whose groups are drawn
together (a vehicle's hull and turret), not for catalogues of alternative parts (the console variants).
Only vertex positions change; UVs, normals, groups and face order stay as they are.
"""
import math
import sys
from collections import defaultdict

EPS = 0.01          # push-out distance, blocks
PLANE_TOL = 0.002   # faces closer than this to one plane are coplanar
MIN_AREA = 1e-4     # overlaps smaller than this are just shared edges


def sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def signed2(poly):
    return sum(poly[i][0] * poly[i - 1][1] - poly[i - 1][0] * poly[i][1] for i in range(len(poly)))


def ccw(poly):
    return poly if signed2(poly) < 0 else poly[::-1]


def cut(p, q, a, b):
    dx, dy = q[0] - p[0], q[1] - p[1]
    ex, ey = b[0] - a[0], b[1] - a[1]
    den = dx * ey - dy * ex
    t = 0 if abs(den) < 1e-12 else ((a[0] - p[0]) * ey - (a[1] - p[1]) * ex) / den
    return (p[0] + dx * t, p[1] + dy * t)


def clip(subject, clipper):
    """Sutherland-Hodgman: subject polygon clipped by a convex clipper, both counter-clockwise."""
    out = subject
    for i in range(len(clipper)):
        a, b = clipper[i - 1], clipper[i]
        inside = lambda p: (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= -1e-9
        src, out = out, []
        for j in range(len(src)):
            p, q = src[j - 1], src[j]
            if inside(q):
                if not inside(p): out.append(cut(p, q, a, b))
                out.append(q)
            elif inside(p):
                out.append(cut(p, q, a, b))
        if not out: break
    return out


def load(path):
    return load_lines(open(path, encoding="utf-8").read().split("\n"))


def load_lines(lines):
    verts, faces, group = [], [], None   # faces: (vertex indices, group)
    for line in lines:
        t = line.split()
        if not t: continue
        if t[0] in ("o", "g"): group = t[1]
        elif t[0] == "v": verts.append(tuple(map(float, t[1:4])))
        elif t[0] == "f": faces.append(([int(c.split("/")[0]) - 1 for c in t[1:]], group))
    return lines, verts, faces


def components(n, faces):
    parent = list(range(n))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for idx, _ in faces:
        for i in idx[1:]: parent[find(i)] = find(idx[0])
    return [find(i) for i in range(n)]


def overlap(verts, ia, ib, n):
    ax = max(range(3), key=lambda i: abs(n[i]))   # project onto the other two axes
    u, v = [i for i in range(3) if i != ax]
    pa = ccw([(verts[i][u], verts[i][v]) for i in ia])
    pb = ccw([(verts[i][u], verts[i][v]) for i in ib])
    poly = clip(pa, pb)
    return abs(signed2(poly)) / 2 if len(poly) >= 3 else 0.0


def analyse(verts, faces, cross_groups):
    """Overlapping pairs as (face a, face b, the face that moves); a face is (no, normal, d, area, component)."""
    comp = components(len(verts), faces)
    tris = []
    for fi, (idx, _) in enumerate(faces):
        p = [verts[i] for i in idx]
        n = cross(sub(p[1], p[0]), sub(p[2], p[0]))
        ln = math.sqrt(dot(n, n))
        if ln < 1e-9: continue
        n = (n[0] / ln, n[1] / ln, n[2] / ln)
        tris.append((fi, n, dot(n, p[0]), ln / 2, comp[idx[0]]))
    comp_area = defaultdict(float)
    for t in tris: comp_area[t[4]] += t[3]
    buckets = defaultdict(list)
    for t in tris:
        buckets[(round(t[1][0], 2), round(t[1][1], 2), round(t[1][2], 2), round(t[2] / PLANE_TOL))].append(t)
    pairs = []
    for key, group in buckets.items():
        near = group + buckets.get(key[:3] + (key[3] + 1,), [])
        for a in group:
            for b in near:
                if b[0] <= a[0] or a[4] == b[4]: continue
                if not cross_groups and faces[a[0]][1] != faces[b[0]][1]: continue
                if dot(a[1], b[1]) < 0.999 or abs(a[2] - b[2]) > PLANE_TOL: continue
                if overlap(verts, faces[a[0]][0], faces[b[0]][0], a[1]) > MIN_AREA:
                    # the smaller piece gives way (ties: by component id, so always the same one)
                    pairs.append((a, b, a if (comp_area[a[4]], a[4]) < (comp_area[b[4]], b[4]) else b))
    return pairs


def fix(lines, faces, pairs):
    push = {}
    for _, _, f in pairs:
        for i in faces[f[0]][0]:
            push[i] = f[1]
    vi = 0
    for li, line in enumerate(lines):
        if line.startswith("v "):
            if vi in push:
                p = list(map(float, line.split()[1:4]))
                n = push[vi]
                lines[li] = "v %.6f %.6f %.6f" % (p[0] + n[0] * EPS, p[1] + n[1] * EPS, p[2] + n[2] * EPS)
            vi += 1
    return len(push)


def report(path, faces, pairs):
    by_group = defaultdict(int)
    for a, b, _ in pairs:
        ga, gb = faces[a[0]][1], faces[b[0]][1]
        by_group[ga if ga == gb else f"{ga} / {gb}"] += 1
    print(f"{path}: {len(pairs)} overlapping coplanar face pairs")
    for g, c in sorted(by_group.items(), key=lambda x: -x[1])[:12]:
        print(f"  {c:5d}  {g}")


def selftest():
    sq = lambda x: [(x, 0), (x + 1, 0), (x + 1, 1), (x, 1)]
    assert abs(abs(signed2(clip(ccw(sq(0)), ccw(sq(0.5))))) / 2 - 0.5) < 1e-9
    assert len(clip(ccw(sq(0)), ccw(sq(3)))) == 0
    print("ok")


def main():
    if sys.argv[1] == "--selftest": return selftest()
    path, do_fix, cross_groups = sys.argv[1], "--fix" in sys.argv, "--cross" in sys.argv
    lines, verts, faces = load(path)
    pairs = analyse(verts, faces, cross_groups)
    report(path, faces, pairs)
    if not do_fix: return
    passes = 0
    while pairs and passes < 6:
        moved = fix(lines, faces, pairs)
        passes += 1
        _, verts, faces = load_lines(lines)
        pairs = analyse(verts, faces, cross_groups)
        print(f"  pass {passes}: moved {moved} vertices by {EPS}, {len(pairs)} pairs left")
    open(path, "w", encoding="utf-8").write("\n".join(lines))


if __name__ == "__main__":
    main()
