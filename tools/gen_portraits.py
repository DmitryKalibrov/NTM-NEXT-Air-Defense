# Pixel-art leader portraits for the desk portrait elements (console_parts.py imports this, numpy only): 32 x 32
# tiles, rows top-down, the frame's front face maps to x 2..30: a 2 px gilt frame around a 24 x 28 picture. Heads
# are hand-drawn character grids (14 wide) over a shared bust (tie / mao / kurta) and a flag.
#   python tools/gen_portraits.py preview.png
import sys
import numpy as np
W, H = 24, 28

BUST = {
    "tie": [
        ".......ccWWttWWcc.......",
        "....ccccccWttWcccccc....",
        "..cccccccCWttWCccccccc..",
        ".ccccccccCWttWCcccccccc.",
        "cccccccccCCttCCccccccccc",
        "ccccccccccCttCcccccccccc",
        "cccccccccccTTccccccccccc",
        "cccccccccccCCccccccccccc",
        "ccccccccccccoccccccccccc",
        "cccccccccccCCccccccccccc",
    ],
    "mao": [
        ".......cccccccccc.......",
        "....cccccccCCccccccc....",
        "..cccccccccCCccccccccc..",
        ".ccccccccccCCcccccccccc.",
        "ccccccccccccoccccccccccc",
        "cccccccccccCCccccccccccc",
        "ccccccccccccoccccccccccc",
        "cccccccccccCCccccccccccc",
        "ccccccccccccoccccccccccc",
        "cccccccccccCCccccccccccc",
    ],
    "kurta": [
        ".......WWWWWWWWWW.......",
        "....WWWccccWWccccWWW....",
        "..WWWWcccccWWcccccWWWW..",
        ".WWWWWcccccWWcccccWWWWW.",
        "WWWWWWccccCoWCccccWWWWWW",
        "WWWWWWccccccCcccccWWWWWW",
        "WWWWWWCcccccoccccCWWWWWW",
        "WWWWWWCccccccccccCWWWWWW",
        "WWWWWWCcccccoccccCWWWWWW",
        "WWWWWWCccccccccccCWWWWWW",
    ],
}

# head grids, 14 wide, drawn from y = 2 at x = 5; '.' = see-through
HEADS = {
    "putin": [  # thinning light hair, high forehead, narrow face, small pale eyes, thin lips
        "....lslsls....",
        "...lsssssssl..",
        "..lssssssssl..",
        "..hssssssssh..",
        "..hsbbssbbsh..",
        ".SswessssewsS.",
        ".SsssssssssS..",
        "..sssssSsssss.",
        "..sssSnnSsss..",
        "..ssssssssss..",
        "..sssmmmmsss..",
        "...ssssssss...",
        "...SssssssS...",
        "....SssssS....",
        ".....ssss.....",
        ".....SssS.....",
    ],
    "trump": [  # big swept golden hair, tan face, pale brows, squint, pursed mouth
        "...hhhhhhhhh..",
        "..hhhhhhhhhhhh",
        ".hhhhhhhhhhhhh",
        ".hHHhhhhhhhhhh",
        ".hsssHHhhhhhh.",
        ".hssssssssssh.",
        "..sbbbsssbbbs.",
        ".SsSesssSesss.",
        ".SssssssssssS.",
        "..ssssSSsssss.",
        "..sssSnnSssss.",
        "..ssssssssss..",
        "..sssSmmSsss..",
        "...ssmmmmsss..",
        "...SssssssS...",
        "....SssssS....",
        ".....SssS.....",
    ],
    "xi": [  # neat black side-parted hair, broad round face, calm eyes
        "....hhhhhh....",
        "..hhhhhhhhhh..",
        "..hhhhHhhhhh..",
        "..hHhssssshhh.",
        "..hssssssssh..",
        "..sbbbssbbbs..",
        ".SssssssssssS.",
        ".SswessssewsS.",
        "..ssssSSssss..",
        "..sssSnnSsss..",
        "..ssssssssss..",
        "..ssssmmssss..",
        "..SssssssssS..",
        "...SssssssS...",
        "....SssssS....",
        ".....SssS.....",
    ],
    "modi": [  # receding white hair, rimless glasses, full trimmed white beard
        "....hhhhhh....",
        "...hssssssh...",
        "..hssssssssh..",
        "..hssssssssh..",
        "..sbbbssbbbs..",
        ".SgggsssgggsS.",
        ".SgeesssseegS.",
        ".SgggsSSgggsS.",
        "..ssssSSssss..",
        "..BssSnnSssB..",
        "..BBBBBBBBBB..",
        "..BBBmmmmBBB..",
        "..BBBBBBBBBB..",
        "...BBBBBBBB...",
        "....BBBBBB....",
        ".....SssS.....",
    ],
    "kim": [  # tall flat-top black hair, shaved sides, round chubby face
        "...hhhhhhhh...",
        "..hhhhhhhhhh..",
        "..hhhhhhhhhh..",
        "..HhhhhhhhhH..",
        "..HHssssssHH..",
        ".SsbbbssbbbsS.",
        ".SssssssssssS.",
        ".SswessssewsS.",
        ".sssssSSsssss.",
        ".ssssSnnSssss.",
        ".ssssssssssss.",
        "..sssmmmmsss..",
        "..ssssssssss..",
        "..SssssssssS..",
        "...SSssssSS...",
        ".....SssS.....",
    ],
}

C = lambda *v: tuple(v)
PAL = {
    "putin": dict(s=C(236, 196, 170), S=C(204, 160, 136), l=C(170, 140, 104), h=C(150, 120, 88), b=C(150, 120, 88),
                  w=C(240, 240, 240), e=C(70, 100, 140), n=C(170, 120, 100), m=C(176, 110, 104),
                  c=C(40, 44, 56), C=C(26, 28, 36), W=C(236, 236, 236), t=C(110, 40, 60), T=C(80, 26, 44), o=C(26, 28, 36)),
    "trump": dict(s=C(234, 162, 108), S=C(204, 130, 84), h=C(236, 196, 112), H=C(204, 160, 76), b=C(240, 214, 160),
                  w=C(240, 240, 240), e=C(60, 60, 70), n=C(180, 110, 72), m=C(200, 108, 92),
                  c=C(28, 34, 60), C=C(18, 22, 40), W=C(240, 240, 240), t=C(204, 30, 36), T=C(150, 20, 26), o=C(18, 22, 40)),
    "xi": dict(s=C(232, 196, 160), S=C(204, 166, 132), h=C(28, 28, 32), H=C(70, 70, 76), b=C(40, 36, 36),
               w=C(240, 240, 240), e=C(36, 30, 30), n=C(176, 132, 104), m=C(180, 110, 100),
               c=C(30, 32, 40), C=C(18, 20, 26), W=C(236, 236, 236), t=C(170, 30, 36), T=C(120, 20, 26), o=C(18, 20, 26)),
    "modi": dict(s=C(184, 128, 92), S=C(150, 100, 70), h=C(232, 232, 228), b=C(220, 220, 216), B=C(238, 238, 234),
                 g=C(160, 150, 130), w=C(240, 240, 240), e=C(36, 26, 22), n=C(130, 84, 60), m=C(150, 90, 80),
                 c=C(70, 86, 110), C=C(50, 62, 82), W=C(236, 226, 196), o=C(200, 180, 120)),
    "kim": dict(s=C(238, 204, 176), S=C(208, 170, 144), h=C(22, 22, 26), H=C(120, 110, 104), b=C(36, 32, 32),
                w=C(240, 240, 240), e=C(36, 30, 30), n=C(180, 136, 112), m=C(184, 116, 108),
                c=C(30, 30, 32), C=C(16, 16, 18), o=C(60, 60, 64)),
}
BODY = {"putin": "tie", "trump": "tie", "xi": "tie", "modi": "kurta", "kim": "mao"}


def flag(name):
    a = np.zeros((H, W, 3))
    y, x = np.mgrid[0:H, 0:W]
    if name == "putin":
        a[:], a[y >= 9], a[y >= 18] = (236, 236, 236), (30, 70, 160), (200, 40, 40)
    elif name == "trump":
        a[:] = (236, 236, 236)
        a[(y // 2) % 2 == 0] = (190, 40, 50)
        a[(y < 12) & (x < 11)] = (40, 50, 110)
        a[(y < 12) & (x < 11) & ((x + y) % 3 == 0) & (y % 2 == 1)] = (236, 236, 236)
    elif name == "xi":
        a[:] = (210, 30, 30)
        for sx, sy in ((3, 3), (7, 1), (9, 3), (9, 6), (7, 8)):
            a[sy, sx] = (250, 220, 0)
        a[2:5, 2:5] = (250, 220, 0)
        a[[2, 2, 4, 4], [2, 4, 2, 4]] = (210, 30, 30)
    elif name == "modi":
        a[:], a[y >= 9], a[y >= 18] = (240, 150, 50), (240, 240, 240), (20, 130, 60)
        a[(np.hypot(x - 11.5, y - 13.5) < 3) & (np.hypot(x - 11.5, y - 13.5) > 2)] = (20, 40, 140)
    elif name == "kim":
        a[:] = (30, 70, 160)
        a[(y >= 4) & (y < 24)] = (236, 236, 236)
        a[(y >= 5) & (y < 23)] = (200, 40, 40)
        a[(np.hypot(x - 2.5, y - 14) < 2.8)] = (236, 236, 236)
        a[(np.hypot(x - 2.5, y - 14) < 1.5)] = (200, 40, 40)
    return a * 0.8 + 20  # faded print behind the sitter


def paint(img, grid, x0, y0, pal):
    for j, row in enumerate(grid):
        assert len(row) == len(grid[0]), (row, len(row))
        for i, ch in enumerate(row):
            if ch != ".":
                img[y0 + j, x0 + i] = pal[ch]


def portrait(name):
    pic = flag(name)
    paint(pic, BUST[BODY[name]], 0, H - 10, PAL[name])
    paint(pic, HEADS[name], 5, 2, PAL[name])
    tex = np.zeros((32, 32, 3))
    tex[:] = (150, 110, 40)  # sides of the frame sample this
    gold = np.zeros((32, 28, 3))
    gold[:] = (196, 152, 60)
    gold[0, :], gold[:, 0] = (236, 200, 110), (236, 200, 110)
    gold[-1, :], gold[:, -1] = (120, 84, 30), (120, 84, 30)
    gold[1, 1:-1], gold[1:-1, 1] = (150, 110, 40), (150, 110, 40)
    gold[-2, 1:-1], gold[1:-1, -2] = (226, 186, 96), (226, 186, 96)
    gold[2:30, 2:26] = pic
    tex[:, 2:30] = gold
    return np.clip(tex, 0, 255).astype(np.uint8)


if __name__ == "__main__":
    from PIL import Image
    tiles = [portrait(n) for n in HEADS]
    Image.fromarray(np.hstack(tiles)).resize((32 * len(tiles) * 8, 256), Image.NEAREST).save(sys.argv[1])
