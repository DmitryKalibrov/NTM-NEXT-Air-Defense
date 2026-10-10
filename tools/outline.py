"""Panel lines like NTM's hand-painted models: darken the sharp edges of a baked atlas.

usage: python outline.py texture.png lines.json
lines.json: list of UV segments [[u0, v0], [u1, v1]] in Blender UV space - only the sharp (> 46 deg)
and open edges, exported by the bake script, so round parts (cylinders, cones) stay smooth.
"""
import json
import sys

from PIL import Image, ImageChops, ImageDraw

tex, segs = Image.open(sys.argv[1]).convert("RGB"), json.load(open(sys.argv[2]))
w, h = tex.size
mask = Image.new("L", tex.size, 255)
draw = ImageDraw.Draw(mask)
for (u0, v0), (u1, v1) in segs:
    draw.line([(u0 * w, (1 - v0) * h), (u1 * w, (1 - v1) * h)], fill=120, width=1)
ImageChops.multiply(tex, Image.merge("RGB", [mask] * 3)).save(sys.argv[1])
print(len(segs), "edges outlined")
