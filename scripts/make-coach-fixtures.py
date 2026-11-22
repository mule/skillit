#!/usr/bin/env python3
"""Writes the synthetic handwriting fixtures for the #28 legibility metric tests.

These are NOT the child's handwriting: every stroke is generated here from simple shapes plus seeded
jitter, so the fixtures are reproducible and contain nothing personal. Shapes are drawn a little
differently from the app's own guides (app/.../coach/DigitGuides.kt), so the tests don't just check the
guides against themselves.

Output: app/src/test/resources/coach/digit-fixtures.v1.tsv
Line format: name<TAB>digit<TAB>boxWidth<TAB>boxHeight<TAB>strokes
strokes as InkSampleCodec.encodeStrokes: strokes joined by ';', points "x y t pressure" joined by ','.

Run: python3 scripts/make-coach-fixtures.py
"""
import math
import pathlib
import random

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/test/resources/coach/digit-fixtures.v1.tsv"
BOX = 300.0


def arc(cx, cy, rx, ry, a0, a1, n=30):
    """Angles in degrees, 90 = top; increasing = counter-clockwise on screen (y down)."""
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy - ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def line(*pts, n=10):
    out = [pts[0]]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        out += [(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n) for i in range(1, n + 1)]
    return out


# Child-like digits in unit box coordinates (top line ~0.17, baseline ~0.84).
NEAT = {
    0: [arc(.5, .5, .2, .33, 95, 455, 40)],
    1: [line((.4, .3), (.54, .17), (.53, .84))],
    2: [arc(.49, .34, .19, .17, 165, -25) + line((.66, .41), (.29, .84), (.73, .83))],
    3: [arc(.48, .33, .19, .16, 155, -90) + arc(.48, .67, .21, .17, 90, -155)[1:]],
    4: [line((.44, .17), (.28, .61), (.75, .6)), line((.62, .37), (.61, .84))],
    5: [line((.33, .18), (.31, .46)) + arc(.49, .65, .21, .19, 140, -150), line((.33, .17), (.71, .18))],
    6: [arc(.64, .64, .38, .47, 88, 180, 16) + arc(.47, .65, .19, .18, 180, 540, 32)],
    7: [line((.28, .18), (.73, .17), (.43, .84))],
    8: [arc(.5, .33, .16, .16, 95, 270, 16) + arc(.5, .66, .2, .17, 90, -270, 32)[1:] + arc(.5, .33, .16, .16, 270, 455, 16)[1:]],
    9: [arc(.5, .34, .19, .17, 5, 365, 32) + line((.69, .33), (.65, .84))],
}

VARIANTS = {
    # Ink beyond the right edge.
    "spill-3": (3, lambda s: shift(s, .33, 0)),
    "tiny-2": (2, lambda s: scale(s, .35, (.5, .84))),
    "huge-7": (7, lambda s: scale(s, 1.4, (.5, .84))),
    # Floats well above the baseline.
    "float-5": (5, lambda s: shift(s, 0, -.27)),
    "open-0": (0, lambda s: [arc(.5, .5, .2, .33, 95, 380, 36)]),
    "open-6": (6, lambda s: [arc(.64, .64, .38, .47, 88, 180, 16) + arc(.47, .65, .19, .18, 180, 430, 26)]),
    "open-8-top": (8, lambda s: [arc(.5, .33, .16, .16, 160, 270, 12) + arc(.5, .66, .2, .17, 90, -270, 32)[1:] + arc(.5, .33, .16, .16, 270, 400, 14)[1:]]),
    "open-9": (9, lambda s: [arc(.5, .34, .19, .17, 60, 340, 28) + line((.68, .40), (.65, .84))[1:]]),
    # A 4 whose last stroke closes the top: reads as 9.
    "closed-4": (4, lambda s: [line((.6, .17), (.28, .6), (.76, .6)), line((.6, .17), (.6, .84))]),
    # A 1 with a long roof: reads as 7.
    "roof-1": (1, lambda s: [line((.24, .18), (.62, .17), (.6, .84))]),
    # A 7 with almost no roof: reads as 1.
    "short-7": (7, lambda s: [line((.5, .18), (.58, .17), (.52, .84))]),
    # A 3 closed on the left: reads as 8.
    "closed-3": (3, lambda s: [arc(.49, .33, .17, .16, 90, 450, 30), arc(.49, .67, .2, .17, 90, 450, 30)]),
    # A 5 whose belly closes back on its stem: reads as 6.
    "closed-5": (5, lambda s: [line((.33, .18), (.31, .5)) + arc(.5, .66, .19, .17, 150, -200, 34), line((.33, .17), (.71, .18))]),
    # A correction written over an old digit (seen in the workbook): an 8 over a 3.
    "over-written-8": (8, lambda s: NEAT[3] + NEAT[8]),
}


def shift(strokes, dx, dy):
    return [[(x + dx, y + dy) for x, y in s] for s in strokes]


def scale(strokes, k, anchor):
    ax, ay = anchor
    return [[(ax + (x - ax) * k, ay + (y - ay) * k) for x, y in s] for s in strokes]


def densify(stroke, step=3.0 / BOX):
    """Pen samples about every 3 px, like a 120 Hz S Pen at writing speed."""
    out = [stroke[0]]
    for (x0, y0), (x1, y1) in zip(stroke, stroke[1:]):
        n = max(1, int(math.hypot(x1 - x0, y1 - y0) / step))
        out += [(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n) for i in range(1, n + 1)]
    return out


def encode(strokes, rng):
    t = 1000
    parts = []
    for s in strokes:
        pts = []
        wobble_x, wobble_y = rng.uniform(-.004, .004), rng.uniform(-.004, .004)
        for i, (x, y) in enumerate(densify(s)):
            px = (x + wobble_x + rng.gauss(0, .0025)) * BOX
            py = (y + wobble_y + rng.gauss(0, .0025)) * BOX
            p = 0.35 + 0.3 * math.sin(math.pi * i / max(1, len(s)))
            pts.append(f"{px:.1f} {py:.1f} {t} {p:.2f}")
            t += 8
        parts.append(",".join(pts))
        t += 180  # pen lift
    return ";".join(parts)


def main():
    rng = random.Random(28)
    lines = [f"neat-{d}\t{d}\t{BOX}\t{BOX}\t{encode(s, rng)}" for d, s in NEAT.items()]
    for name, (d, make) in VARIANTS.items():
        lines.append(f"{name}\t{d}\t{BOX}\t{BOX}\t{encode(make(NEAT[d]), rng)}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("# Synthetic, generated by scripts/make-coach-fixtures.py. Not real handwriting.\n" + "\n".join(lines) + "\n")
    print(f"wrote {len(lines)} fixtures to {OUT}")


if __name__ == "__main__":
    main()
