"""Recomposes Bob's clay face from bob-clay-face-source.png and writes every icon asset.
Run with any Python that has Pillow:  python make_face.py
Tweak EYE_GAP, MOUTH_X/MOUTH_Y (centre, source pixels) and MOUTH_SCALE, then rerun."""
from PIL import Image, ImageDraw, ImageFilter
import os
EYE_GAP = 400          # centre-to-centre, px of the 1024 source
MOUTH_X, MOUTH_Y = 557, 695
MOUTH_SCALE = 0.70
BG = (253, 210, 2)
here = os.path.dirname(os.path.abspath(__file__))
im = Image.open(os.path.join(here, 'bob-clay-face-source.png')).convert('RGB')
out = Image.new('RGB', im.size, BG)
def piece(box, pad):
    x0, y0, x1, y1 = box; b = (x0-pad[0], y0-pad[1], x1+pad[2], y1+pad[3])
    c = im.crop(b); m = Image.new('L', c.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([10, 10, c.width-11, c.height-11], radius=40, fill=255)
    return c, m.filter(ImageFilter.GaussianBlur(8)), b
def paste(c, m, cx, cy, scale=1.0):
    if scale != 1.0:
        c = c.resize((round(c.width*scale), round(c.height*scale)), Image.LANCZOS); m = m.resize(c.size, Image.LANCZOS)
    out.paste(c, (round(cx-c.width/2), round(cy-c.height/2)), m)
for box, cx in [((279, 276, 447, 444), 512-EYE_GAP/2), ((567, 276, 732, 444), 512+EYE_GAP/2)]:
    c, m, b = piece(box, (40, 40, 40, 70)); paste(c, m, cx, (b[1]+b[3])/2)
c, m, b = piece((312, 519, 741, 789), (45, 12, 45, 75)); paste(c, m, MOUTH_X, MOUTH_Y, MOUTH_SCALE)
out.save(os.path.join(here, 'bob-clay-face.png'))
# Face bounds, then the icon layers: launcher foreground (face in the adaptive safe zone) and the in-app mark.
px = out.load(); xs = []; ys = []
for y in range(0, 1024, 2):
    for x in range(0, 1024, 2):
        r, g, bb = px[x, y]
        if r+g+bb < 300: xs.append(x); ys.append(y)
x0, y0, x1, y1 = min(xs)-30, min(ys)-30, max(xs)+30, max(ys)+55
face = out.crop((x0, y0, x1, y1))
def compose(size, frac):
    c = Image.new('RGB', (size, size), BG)
    s = frac*size/max(face.width, face.height)
    f = face.resize((round(face.width*s), round(face.height*s)), Image.LANCZOS)
    c.paste(f, ((size-f.width)//2, (size-f.height)//2)); return c
res = os.path.join(here, '../android/app/src/main/res')
for d, p in {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}.items():
    compose(p*4, 0.56).resize((p, p), Image.LANCZOS).save(f'{res}/mipmap-{d}/ic_launcher_foreground.png', optimize=True)
compose(1024, 0.86).resize((384, 384), Image.LANCZOS).save(f'{res}/drawable-nodpi/bob_mark.png', optimize=True)
compose(1024, 0.56).save(os.path.join(here, 'bob-icon-1024.png'))
compose(1024, 0.86).resize((384, 384), Image.LANCZOS).save(os.path.join(here, '../assets/plates/mark.png'))
print('face bounds', x0, y0, x1, y1)
