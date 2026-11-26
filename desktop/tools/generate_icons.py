#!/usr/bin/env python3
# Copyright (c) 2026 Littleton Robotics
# http://github.com/Mechanical-Advantage
#
# Use of this source code is governed by a BSD
# license that can be found in the LICENSE file
# at the root directory of this project.

"""Generates the desktop app icon from the layers of the iOS app icon.

The iOS icon (AdvantageTune/AppIcon.icon) is the source of truth for the artwork. Windows shows
icons as they are, so the icon is drawn the way iOS shows it: the full artwork on its background,
in a rounded square. Requires Pillow:

    python3 desktop/tools/generate_icons.py
"""

import importlib.util
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ICON = ROOT / "AdvantageTune" / "AppIcon.icon"
DESKTOP = ROOT / "desktop"

# The color conversions are shared with the Android icon generator
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location(
    "android_icons", ROOT / "android" / "tools" / "generate_icons.py"
)
android_icons = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(android_icons)

# Corner radius of an iOS app icon, as a fraction of its width
CORNER_RADIUS = 0.2237
# The mask is drawn larger and scaled down to smooth its edge
SUPERSAMPLING = 4
ICO_SIZES = [16, 20, 24, 32, 40, 48, 64, 128, 256]


def main() -> None:
    icon = json.loads((ICON / "icon.json").read_text())
    fill = android_icons.parse_color(
        android_icons.default_fill(icon["fill-specializations"])["automatic-gradient"]
    )
    top = tuple(round(c + (255 - c) * 0.6) for c in fill)

    # Layers are listed from front to back
    layers = []
    for group in icon["groups"]:
        for layer in group["layers"]:
            color = android_icons.parse_color(
                android_icons.default_fill(layer["fill-specializations"])["solid"]
            )
            image = Image.open(ICON / "Assets" / layer["image-name"]).convert("RGBA")
            layers.append(android_icons.tinted(image.getchannel("A"), color))
    size = layers[0].width

    # Background gradient that ends in the icon's fill color
    artwork = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(artwork)
    for y in range(size):
        t = y / (size - 1)
        draw.line(
            [(0, y), (size, y)],
            fill=tuple(round(a + (b - a) * t) for a, b in zip(top, fill)) + (255,),
        )
    for layer in reversed(layers):
        artwork.alpha_composite(layer)

    mask = Image.new("L", (size * SUPERSAMPLING, size * SUPERSAMPLING), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, mask.width - 1, mask.height - 1],
        radius=round(mask.width * CORNER_RADIUS),
        fill=255,
    )
    artwork.putalpha(mask.resize((size, size), Image.LANCZOS))

    # Shown in the title bar and the taskbar while the app runs
    resources = DESKTOP / "src" / "main" / "resources"
    resources.mkdir(parents=True, exist_ok=True)
    artwork.resize((256, 256), Image.LANCZOS).save(resources / "icon.png", optimize=True)

    # Used by the installer and the shortcuts it creates
    (DESKTOP / "icons").mkdir(parents=True, exist_ok=True)
    artwork.save(DESKTOP / "icons" / "AdvantageTune.ico", sizes=[(s, s) for s in ICO_SIZES])

    print(f"background #{'%02X%02X%02X' % top} to #{'%02X%02X%02X' % fill}")


if __name__ == "__main__":
    main()
