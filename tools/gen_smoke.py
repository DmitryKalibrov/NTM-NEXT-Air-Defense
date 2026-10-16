"""Smoke puff sprites for ParticleSmoke, rendered as real volumes: a 3D density field (a few overlapping
lobes, eroded by 3D noise) lit from above with self-shadowing and seen through with Beer-Lambert absorption,
so the puffs have round lit tops, shaded undersides and soft see-through edges with no outline.
White, so the particle tints them; the particle must not roll or the light would turn with it.

usage: python gen_smoke.py <textures/particle dir> [count]   -> smoke_0.png .. smoke_<count-1>.png
"""
import sys
import numpy as np
from PIL import Image
from scipy.ndimage import zoom

N = 64           # sprite size; the volume is N^3
SIGMA = 5.0      # extinction per unit density over the whole depth
SHADOW = 6.0     # how dark the inside gets under thick smoke
AMBIENT = 0.55   # sky light reaching the shaded side


def noise3(rng, cells):
    return zoom(rng.random((cells, cells, cells)), N / cells, order=3, mode="grid-wrap")[:N, :N, :N]


def fbm3(rng):
    return sum(noise3(rng, 4 * 2 ** o) * 0.6 ** o for o in range(4)) / 2.18


def puff(rng):
    # axes: [y (row, 0 = top), x, z (0 = nearest the viewer)], all -1..1
    y, x, z = np.meshgrid(*(np.linspace(-1, 1, N),) * 3, indexing="ij")
    field = np.full((N, N, N), -1.0)
    stretch = rng.uniform(0.75, 1.25)       # some puffs wider, some taller
    for _ in range(rng.integers(4, 9)):
        c = rng.normal(0, 0.22, 3)
        r = rng.uniform(0.28, 0.5)
        d = np.sqrt(((x - c[0]) / stretch) ** 2 + ((y - c[1]) * stretch) ** 2 + (z - c[2]) ** 2) / r
        field = np.maximum(field, 1 - d)
    n = fbm3(rng)
    # noise erodes the lobes into ragged billows, but only near them: no faint haze filling the quad
    dens = np.clip(field * 1.6 + (n - 0.5) * 2.6, 0, None) * np.clip((field + 0.12) / 0.12, 0, 1)
    dens *= np.clip((0.95 - np.sqrt(x * x + y * y + z * z)) * 4, 0, 1)   # never reach the quad edge
    return dens


def render(dens):
    step = 2.0 / N
    # light from straight above: optical depth from the top down to each voxel
    above = np.cumsum(dens, axis=0) - dens * 0.5
    lit = np.exp(-SHADOW * above * step)
    light = AMBIENT + (1 - AMBIENT) * lit
    # look along z, front to back
    a = 1 - np.exp(-SIGMA * dens * step)
    colour = np.zeros((N, N))
    trans = np.ones((N, N))
    for k in range(N):
        colour += trans * a[:, :, k] * light[:, :, k]
        trans *= 1 - a[:, :, k]
    alpha = 1 - trans
    lum = np.where(alpha > 1e-4, colour / np.maximum(alpha, 1e-4), 1.0)
    return np.dstack([lum, lum, lum, alpha])


def main():
    out, count = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 16
    for v in range(count):
        img = render(puff(np.random.default_rng(2000 + v)))
        Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA").save(f"{out}/smoke_{v}.png")
    print(f"{count} sprites")


if __name__ == "__main__":
    main()
