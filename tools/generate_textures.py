#!/usr/bin/env python3
"""Generates all textures for the Explorer Maze resource pack (needs Pillow).
Run from the repo root:  python3 tools/generate_textures.py
"""
import random, os
from PIL import Image, ImageDraw

RP = "resourcepack/assets"

def clamp(v): return max(0, min(255, int(v)))
def shade(c, k): return tuple(clamp(x * k) for x in c)

def veins(img, color, count, length, rnd, size=16):
    px = img.load()
    for _ in range(count):
        x, y = rnd.randrange(size), rnd.randrange(size)
        dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
        for _ in range(length):
            px[x % size, y % size] = color
            if rnd.random() < 0.4:
                dx, dy = rnd.choice([(1, 0), (0, 1), (-1, 0), (0, -1), (1, 1)])
            x += dx; y += dy

def bedrock():
    """Corrupted / withered / skulk-infested bedrock."""
    rnd = random.Random(1337)
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = shade((30, 27, 36), rnd.uniform(0.75, 1.25))
    for _ in range(9):  # withered ash patches
        cx, cy = rnd.randrange(16), rnd.randrange(16)
        for dx in range(-2, 3):
            for dy in range(-1, 2):
                if rnd.random() < 0.6:
                    px[(cx + dx) % 16, (cy + dy) % 16] = shade((88, 84, 90), rnd.uniform(0.7, 1.1))
    veins(img, (112, 28, 150), 5, 9, rnd)     # corruption (purple)
    veins(img, (120, 18, 30), 3, 6, rnd)      # blood cracks
    for _ in range(7):                        # skulk glow dots
        px[rnd.randrange(16), rnd.randrange(16)] = (40, 225, 205)
    img.save(f"{RP}/minecraft/textures/block/bedrock.png")

def sculk():
    rnd = random.Random(4242)
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = shade((8, 24, 32), rnd.uniform(0.7, 1.3))
    for _ in range(10):
        cx, cy = rnd.randrange(16), rnd.randrange(16)
        r = rnd.choice([1, 1, 2])
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r + 1 and rnd.random() < 0.8:
                    px[(cx + dx) % 16, (cy + dy) % 16] = shade((0, 175, 165), rnd.uniform(0.55, 1.1))
    veins(img, (95, 20, 130), 4, 8, rnd)
    for _ in range(6):
        px[rnd.randrange(16), rnd.randrange(16)] = (150, 255, 240)
    img.save(f"{RP}/minecraft/textures/block/sculk.png")

def explorer_skin():
    """64x32 wither-skeleton UV layout, repainted as 'The Explorer'."""
    rnd = random.Random(99)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    px = img.load()

    def noisy(box, base, spread=0.25):
        x0, y0, x1, y1 = box
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = shade(base, rnd.uniform(1 - spread, 1 + spread)) + (255,)

    cloak, dark, mask = (14, 12, 18), (24, 20, 30), (205, 200, 185)
    noisy((8, 0, 16, 8), dark); noisy((16, 0, 24, 8), dark)             # head top/bottom
    noisy((0, 8, 8, 16), cloak); noisy((16, 8, 24, 16), cloak); noisy((24, 8, 32, 16), cloak)
    noisy((8, 8, 16, 16), mask, 0.12)                                   # face = cracked bone mask
    d.rectangle((9, 10, 11, 12), fill=(255, 30, 30, 255))               # glowing eyes
    d.rectangle((13, 10, 15, 12), fill=(255, 30, 30, 255))
    px[10, 11] = (255, 230, 120, 255); px[14, 11] = (255, 230, 120, 255)
    for yy in range(13, 16):                                            # jagged mouth
        px[9 + (yy - 13) * 2, yy] = (20, 10, 10, 255)
        px[10 + (yy - 13) * 2, yy] = (20, 10, 10, 255)
    px[12, 8] = (60, 50, 55, 255); px[12, 9] = (60, 50, 55, 255)        # crack
    # body
    for box in [(20, 16, 28, 20), (28, 16, 36, 20), (16, 20, 20, 32), (20, 20, 28, 32),
                (28, 20, 32, 32), (32, 20, 40, 32)]:
        noisy(box, cloak)
    for _ in range(40):                                                 # corrupted veins
        px[rnd.randrange(20, 28), rnd.randrange(20, 32)] = (110, 25, 150, 255)
    for x in range(20, 28):                                             # tattered hem
        if rnd.random() < 0.5: px[x, 31] = (0, 0, 0, 0)
    for _ in range(8):
        px[rnd.randrange(20, 28), rnd.randrange(21, 31)] = (40, 225, 205, 255)
    # arms (origin 40,16) and legs (origin 0,16): 2x12x2 boxes
    for ox in (40, 0):
        for box in [(ox + 4, 16, ox + 6, 18), (ox + 6, 16, ox + 8, 18), (ox, 18, ox + 4, 30),
                    (ox + 4, 18, ox + 8, 30), (ox + 8, 18, ox + 12, 30), (ox + 12, 18, ox + 16, 30)]:
            noisy(box, (18, 15, 24), 0.3)
    for _ in range(25):
        px[rnd.randrange(40, 56), rnd.randrange(20, 30)] = (112, 28, 150, 255)
    img.save(f"{RP}/minecraft/textures/entity/skeleton/wither_skeleton.png")

def eye():
    """HUD eye shown as a 'title' glyph when the Explorer is near."""
    S = 32
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((1, 7, 30, 24), fill=(235, 225, 215, 255), outline=(20, 0, 0, 255))
    d.ellipse((9, 7, 22, 24), fill=(200, 10, 10, 255))
    d.ellipse((13, 9, 18, 22), fill=(0, 0, 0, 255))
    for x in (4, 8, 13, 18, 23, 27):
        d.line((x, 8, x + 1, 11), fill=(150, 20, 20, 255))
        d.line((x, 23, x - 1, 20), fill=(150, 20, 20, 255))
    img.save(f"{RP}/explorermaze/textures/font/eye.png")
    img.resize((64, 64), Image.NEAREST).save("resourcepack/pack.png")

if __name__ == "__main__":
    bedrock(); sculk(); explorer_skin(); eye()
    print("textures written")
