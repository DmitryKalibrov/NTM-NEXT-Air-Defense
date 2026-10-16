"""Fire atlas for the shaderpack (Iris) path of FxRender: the shapes the procedural fire shader
(shaders/core/debris.fsh) draws live, baked into four 64x64 cells of one 256x64 texture. Under a shaderpack
our own shader can't run, so NTM's additive particle pipeline draws these and the vertex colour adds heat.

cells: 0 glow (radial), 1 trail (u = x head..tail, v = y across), 2 plume (u = x nozzle..tip), 3 fireball.
Trail and glow are white (coloured by the vertex); plume and fireball carry their own hot-core colouring.

usage: python gen_fire.py <out.png>
"""
import sys
import numpy as np
from PIL import Image
from scipy.ndimage import zoom

N = 64


def noise2(rng, cells):
    return zoom(rng.random((cells, cells)), N / cells, order=3, mode="grid-wrap")[:N, :N]


def fbm2(rng):
    return sum(noise2(rng, 4 * 2 ** o) * 0.5 ** o for o in range(4)) / 1.875


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def blackbody(t):
    c = np.stack([np.full_like(t, 0.45), np.full_like(t, 0.02), np.zeros_like(t)], -1)
    for e0, e1, col in ((0.0, 0.35, (1.0, 0.32, 0.02)), (0.35, 0.7, (1.0, 0.72, 0.22)), (0.7, 1.0, (1.0, 0.97, 0.88))):
        k = smooth(e0, e1, t)[..., None]
        c = c * (1 - k) + np.array(col) * k
    return c


def main():
    rng = np.random.default_rng(7)
    y, x = np.mgrid[0:N, 0:N] / (N - 1)
    sx, sy = x * 2 - 1, y * 2 - 1
    d = np.hypot(sx, sy)
    cells = []

    # 0 glow
    g = np.clip(np.exp(-d * d * 7) + np.exp(-d * 3) * 0.3, 0, 1) * smooth(1.0, 0.85, d)
    cells.append(np.dstack([np.ones((N, N, 3)), g]))

    # 1 trail: core across, torn into blobs along
    u, v = x, sy
    n = fbm2(rng)
    width = (1.0 + (0.3 - 1.0) * u) * (0.7 + 0.6 * n)
    core = np.exp(-(v * v) / (width * width) * 3)
    blobs = smooth(0.2, 0.6, fbm2(rng) + (1 - u) * 0.7)
    across = smooth(1.0, 0.8, np.abs(v))   # fade before the cell edge: no straight ribbon borders
    cells.append(np.dstack([np.ones((N, N, 3)), np.clip(core * blobs * across, 0, 1)]))

    # 2 plume: white core, shock diamonds by the nozzle, orange towards the tip
    width = (0.35 + 0.65 * np.sqrt(u)) * (0.8 + 0.4 * n)
    core = np.exp(-(v * v) / (width * width) * 4)
    diamonds = 0.75 + 0.25 * np.cos(u * 38) * (1 - smooth(0.0, 0.45, u))
    glow = core * (1 - u) ** 1.3 * diamonds
    temp = np.clip((1 - u * 0.9) * (0.5 + 0.6 * core), 0, 1)
    cells.append(np.dstack([blackbody(temp), np.clip(glow * 1.3 * across * smooth(1.0, 0.9, u), 0, 1)]))

    # 3 fireball: boiling orange body, white-hot centre, warm halo
    boil = fbm2(rng)
    edge = 0.62 + 0.28 * (boil - 0.5)
    body = smooth(edge, edge - 0.22, d)
    temp = np.clip(0.35 + 0.65 * boil + np.exp(-d * d * 10) * 1.2 - d * 0.3, 0, 1)
    halo = np.exp(-d * 2.2) * 0.45 * smooth(1.0, 0.8, d)
    a = np.clip(body * (0.8 + 0.6 * boil) + halo, 0, 1)
    col = (blackbody(temp) * body[..., None] + np.array([1.0, 0.42, 0.08]) * halo[..., None]) / np.maximum(a, 1e-4)[..., None]
    cells.append(np.dstack([np.clip(col, 0, 1), a]))

    img = np.concatenate(cells, axis=1)
    Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(sys.argv[1])
    print("ok")


if __name__ == "__main__":
    main()
