#!/usr/bin/env python3
# Copyright (c) 2026 Littleton Robotics
# http://github.com/Mechanical-Advantage
#
# Use of this source code is governed by a BSD
# license that can be found in the LICENSE file
# at the root directory of this project.

"""Generates the Android launcher icon from the layers of the iOS app icon.

The iOS icon (AdvantageTune/AppIcon.icon) is the source of truth for the artwork. Android masks
launcher icons to a shape chosen by the device, so the frame around the iOS icon is left out and
the artwork is scaled to fit the area that every mask keeps visible. Requires Pillow:

    python3 android/tools/generate_icons.py
"""

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ICON = ROOT / "AdvantageTune" / "AppIcon.icon"
RES = ROOT / "android" / "app" / "src" / "main" / "res"

# Launcher icon layers are 108dp wide, of which the middle 72dp is visible
LAYER_SIZE = 108
VISIBLE_SIZE = 72
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# Part of the overlay layer that holds the knobs (the rest is the frame)
KNOBS_BOX = (150, 150, 874, 874)


def srgb_from_linear(value: float) -> float:
    value = min(max(value, 0.0), 1.0)
    return 12.92 * value if value <= 0.0031308 else 1.055 * value ** (1 / 2.4) - 0.055


def linear_from_srgb(value: float) -> float:
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def parse_color(spec: str) -> tuple[int, int, int]:
    """Converts an Icon Composer color ("srgb:r,g,b,a" or "display-p3:r,g,b,a") to sRGB."""
    space, components = spec.split(":")
    r, g, b = (float(c) for c in components.split(",")[:3])
    if space == "display-p3":
        r, g, b = (linear_from_srgb(c) for c in (r, g, b))
        r, g, b = (
            1.2249401 * r - 0.2249404 * g + 0.0000000 * b,
            -0.0420569 * r + 1.0420571 * g + 0.0000000 * b,
            -0.0196376 * r - 0.0786361 * g + 1.0982735 * b,
        )
        r, g, b = (srgb_from_linear(c) for c in (r, g, b))
    return tuple(round(min(max(c, 0.0), 1.0) * 255) for c in (r, g, b))


def default_fill(specializations: list[dict]) -> dict:
    """Returns the fill used by the default (light) appearance."""
    return next(s["value"] for s in specializations if "appearance" not in s)


def tinted(alpha: Image.Image, color: tuple[int, int, int], opacity: float = 1.0) -> Image.Image:
    layer = Image.new("RGBA", alpha.size, color + (0,))
    layer.putalpha(alpha.point(lambda a: round(a * opacity)))
    return layer


def place(artwork: Image.Image, size: int) -> Image.Image:
    """Scales the artwork to the visible part of a launcher icon layer."""
    visible = round(size * VISIBLE_SIZE / LAYER_SIZE)
    scaled = artwork.resize((visible, visible), Image.LANCZOS)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.alpha_composite(scaled, ((size - visible) // 2, (size - visible) // 2))
    return layer


def main() -> None:
    icon = json.loads((ICON / "icon.json").read_text())
    layers = {}
    for group in icon["groups"]:
        for layer in group["layers"]:
            color = parse_color(default_fill(layer["fill-specializations"])["solid"])
            alpha = Image.open(ICON / "Assets" / layer["image-name"]).convert("RGBA").getchannel("A")
            layers[layer["name"]] = (alpha, color)

    lines_alpha, lines_color = layers["Lines"]
    overlay_alpha, knobs_color = layers["Overlay"]
    knobs_alpha = Image.new("L", overlay_alpha.size, 0)
    knobs_alpha.paste(overlay_alpha.crop(KNOBS_BOX), KNOBS_BOX[:2])

    foreground = tinted(lines_alpha, lines_color)
    foreground.alpha_composite(tinted(knobs_alpha, knobs_color))

    monochrome = tinted(lines_alpha, (255, 255, 255), opacity=0.5)
    monochrome.alpha_composite(tinted(knobs_alpha, (255, 255, 255)))

    for density, scale in DENSITIES.items():
        directory = RES / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        size = round(LAYER_SIZE * scale)
        place(foreground, size).save(directory / "ic_launcher_foreground.png", optimize=True)
        place(monochrome, size).save(directory / "ic_launcher_monochrome.png", optimize=True)

    # Background gradient that ends in the icon's fill color
    fill = parse_color(default_fill(icon["fill-specializations"])["automatic-gradient"])
    top = tuple(round(c + (255 - c) * 0.6) for c in fill)
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "drawable" / "ic_launcher_background.xml").write_text(
        f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from AdvantageTune/AppIcon.icon by android/tools/generate_icons.py. Do not edit. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
  <gradient
      android:angle="270"
      android:endColor="#{'%02X%02X%02X' % fill}"
      android:startColor="#{'%02X%02X%02X' % top}" />
</shape>
"""
    )

    print(f"lines #{'%02X%02X%02X' % lines_color}, knobs #{'%02X%02X%02X' % knobs_color}, "
          f"background #{'%02X%02X%02X' % fill}")


if __name__ == "__main__":
    main()
