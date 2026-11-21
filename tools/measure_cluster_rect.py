#!/usr/bin/env python3
"""Measure the instrument-panel's usable rectangle from a ``screencap -d 1`` capture.

The BAIC X7 panel shows only the centre strip of display 1 (1920x720). The stock map paints
nothing outside its own window, so the bounding box of non-black pixels *is* the panel's visible
rectangle for the layout that was active when the capture was taken.

Caveat: a rounded window measures a few pixels small (measured 579..1341 for a window at
576..1344). Round the result outward to the real window edge instead of shrinking to the lit box.

Usage
-----
    adb shell screencap -d 1 -p /sdcard/d1.png
    adb pull /sdcard/d1.png dump/d1_full_map.png
    python tools/measure_cluster_rect.py dump/d1_full_map.png --mode FULL_MAP

    # several layouts at once
    python tools/measure_cluster_rect.py dump/d1_*.png
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - the bundled runtime ships Pillow
    sys.exit("Pillow is required: run this with the DSH bundled python")


def measure(path: Path, threshold: int) -> tuple[tuple[int, int, int, int] | None, tuple[int, int]]:
    image = Image.open(path).convert("L")
    width, height = image.size
    pixels = image.load()

    min_x, min_y, max_x, max_y = width, height, -1, -1
    for y in range(height):
        for x in range(width):
            if pixels[x, y] > threshold:
                if x < min_x:
                    min_x = x
                if x > max_x:
                    max_x = x
                if y < min_y:
                    min_y = y
                if y > max_y:
                    max_y = y
    if max_x < 0:
        return None, (width, height)
    return (min_x, min_y, max_x - min_x + 1, max_y - min_y + 1), (width, height)


def column_profile(path: Path, threshold: int, buckets: int = 24) -> list[tuple[int, int]]:
    """Rough horizontal occupancy, to spot layouts with content in two separate areas."""
    image = Image.open(path).convert("L")
    width, height = image.size
    pixels = image.load()
    step = max(1, width // buckets)
    out = []
    for start in range(0, width, step):
        end = min(width, start + step)
        hit = 0
        for x in range(start, end, 3):
            for y in range(0, height, 7):
                if pixels[x, y] > threshold:
                    hit += 1
        out.append((start, hit))
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("images", nargs="+", type=Path)
    parser.add_argument("--threshold", type=int, default=16, help="luminance above this counts as content (default 16)")
    parser.add_argument("--mode", help="PateoClusterMode name to print a Kotlin snippet for")
    args = parser.parse_args()

    for path in args.images:
        if not path.exists():
            print(f"missing: {path}")
            continue
        rect, (width, height) = measure(path, args.threshold)
        print(f"\n=== {path.name}  ({width}x{height}) ===")
        if rect is None:
            print("  nothing lit -> this layout has no video area (TBT_ONLY / OFF)")
            continue
        x, y, w, h = rect
        print(f"  content box: x={x} y={y} w={w} h={h}  (right={x + w} bottom={y + h})")
        if (x, y, w, h) != (0, 0, width, height):
            print(f"  full display is {width}x{height}: the car crops the rest")
        print("  column occupancy:", " ".join(f"{start}:{hit}" for start, hit in column_profile(path, args.threshold)))
        if args.mode:
            print("\n  Kotlin:")
            print(f"    val {args.mode} = ClusterRect({x}, {y}, {w}, {h})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
