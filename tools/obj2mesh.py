"""Compile a Wavefront OBJ into NTM's .hbm_mesh (the format com.hbm.render.loader.ObjMesh reads).

usage: python obj2mesh.py model.obj [more.obj ...]   -> writes model.hbm_mesh next to each
Mirrors NTM's ObjLoader: v is flipped (1 - v), face normal from the first three corners,
triangles get their third corner repeated as the fourth.
"""
import math
import struct
import sys

MAGIC, VERSION = 1212304719, 1


def varint(n):
    z = ((n << 1) ^ (n >> 31)) & 0xFFFFFFFF
    out = bytearray()
    while True:
        b = z & 0x7F
        z >>= 7
        if z:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def face_normal(p0, p1, p2):
    ax, ay, az = (p1[i] - p0[i] for i in range(3))
    bx, by, bz = (p2[i] - p0[i] for i in range(3))
    n = (ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
    ln = math.sqrt(sum(c * c for c in n)) or 1.0
    if ln < 1e-6:
        ln = 1.0
    return tuple(c / ln for c in n)


def compile_obj(path):
    pos, uv, nrm, groups = [], [], [], []
    for line in open(path, encoding="utf-8"):
        t = line.split()
        if not t:
            continue
        if t[0] == "v":
            pos.append(tuple(map(float, t[1:4])))
        elif t[0] == "vt":
            uv.append((float(t[1]), 1.0 - float(t[2])))
        elif t[0] == "vn":
            nrm.append(tuple(map(float, t[1:4])))
        elif t[0] in ("o", "g"):
            groups.append((t[1], []))
        elif t[0] == "f":
            corners = [tuple(int(i) - 1 for i in c.split("/")) for c in t[1:]]
            if len(corners) not in (3, 4):
                raise ValueError(f"{path}: face with {len(corners)} corners, triangulate first")
            groups[-1][1].append(corners)

    normals = list(nrm)  # face normals are appended to the same table
    body = bytearray()
    body += struct.pack(">i", len(groups))
    for name, faces in groups:
        nb = name.encode("utf-8")
        body += struct.pack(">i", len(nb)) + nb
        ps = [pos[c[0]] for f in faces for c in f]
        body += struct.pack(">6f", *(min(p[i] for p in ps) for i in range(3)),
                            *(max(p[i] for p in ps) for i in range(3)))
        body += struct.pack(">i", len(faces))
        lp = lu = ln = lf = 0
        for f in faces:
            body.append(len(f))
            fi = len(normals)
            normals.append(face_normal(*(pos[c[0]] for c in f[:3])))
            body += varint(fi - lf); lf = fi
            for p, u, n in f:
                body += varint(p - lp) + varint(u - lu) + varint(n - ln)
                lp, lu, ln = p, u, n

    out = bytearray(struct.pack(">ii", MAGIC, VERSION))
    for table, width in ((pos, 3), (uv, 2), (normals, 3)):
        out += struct.pack(">i", len(table))
        for row in table:
            out += struct.pack(f">{width}f", *row)
    out += body
    dst = path[:-4] + ".hbm_mesh"
    open(dst, "wb").write(out)
    return dst, len(groups), sum(len(f) for _, f in groups)


def check(path):
    """Read back with the same rules as ObjMesh.read; fails loudly on any mismatch."""
    d = open(path, "rb").read(); at = 0

    def i32():
        nonlocal at; v = struct.unpack_from(">i", d, at)[0]; at += 4; return v

    def var():
        nonlocal at; v = s = 0
        while True:
            b = d[at]; at += 1; v |= (b & 0x7F) << s; s += 7
            if not b & 0x80:
                return (v >> 1) ^ -(v & 1)

    assert i32() == MAGIC and i32() == VERSION
    sizes = []
    for w in (3, 2, 3):
        n = i32(); sizes.append(n); at += n * w * 4
    names = []
    for _ in range(i32()):
        nl = i32(); names.append(d[at:at + nl].decode()); at += nl + 24
        p = u = n = fn = 0
        for _ in range(i32()):
            k = d[at]; at += 1; assert k in (3, 4)
            fn += var(); assert 0 <= fn < sizes[2]
            for _ in range(k):
                p += var(); u += var(); n += var()
                assert 0 <= p < sizes[0] and 0 <= u < sizes[1] and 0 <= n < sizes[2]
    assert at == len(d), "trailing data"
    return names


if __name__ == "__main__":
    for src in sys.argv[1:]:
        dst, g, f = compile_obj(src)
        print(dst, g, "groups", f, "faces", check(dst))
