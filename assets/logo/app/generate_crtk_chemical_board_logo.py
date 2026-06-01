#!/usr/bin/env python3
"""Generate the active ChessRTK app logo — a macOS Big Sur style icon.

The icon is a squircle (superellipse) tile with a top-lit teal gradient over a
softened chessboard, and a glossy chemical flask floating above a soft drop
shadow. It renders SVG (source), PNG (via Inkscape), and ICO assets.
"""

from __future__ import annotations

import math
import shutil
import struct
import subprocess
from pathlib import Path
from textwrap import dedent

from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
OUTPUT_DIR = Path(__file__).resolve().parent
PNG_PATH = OUTPUT_DIR / "crtk-chemical-board.png"
ICO_PATH = OUTPUT_DIR / "crtk-chemical-board.ico"
SVG_PATH = OUTPUT_DIR / "crtk-chemical-board.svg"

SIZE = 1024

# Big Sur tile: a squircle with a small transparent margin for the soft shadow.
TILE_MARGIN = 76
TILE_A = (SIZE - 2 * TILE_MARGIN) / 2          # squircle half-extent
TILE_C = SIZE / 2                              # squircle centre
SQUIRCLE_EXPONENT = 5.0                        # ~Apple "squircle" continuity
BOARD_MID = TILE_C

# Flask hero artwork floats centred with generous padding.
SOURCE_VIEWBOX_WIDTH = 428.99
SOURCE_VIEWBOX_HEIGHT = 524.33
# Knight + flask are composed as one centered group: matched visual size,
# grounded on a shared baseline, the group's optical centre on the tile, balanced
# against the flask's heavier (saturated) weight on the right.
MARK_HEIGHT = 510
MARK_WIDTH = MARK_HEIGHT * SOURCE_VIEWBOX_WIDTH / SOURCE_VIEWBOX_HEIGHT
MARK_X = 426
MARK_Y = 240

# Board surface (softened teal so it reads as a lit chessboard, not stripes).
LIGHT_TILE_TOP = "#c4e9f3"
LIGHT_TILE_BOTTOM = "#97d2e3"
DARK_TILE_TOP = "#4fa3c0"
DARK_TILE_BOTTOM = "#2d7589"
TILE_DIVIDER = "#15536b"

# Flask glass + liquid, glossy with soft edges (no hard outline).
GLASS_TOP = "#ffffff"
GLASS_MID = "#eaf7fb"
GLASS_BOTTOM = "#cfe7ee"
RIM_TOP = "#3a90ab"
RIM_BOTTOM = "#1f5e77"
LIQUID_TOP = "#2aa7c9"
LIQUID_MID = "#1b8cad"
LIQUID_BOTTOM = "#13708f"
BUBBLE_LIGHT = "#e3f5fa"

# Big Sur depth.
SHADOW_COLOR = "#06303f"
TILE_SHADOW = "#0a2f3e"

# A large chess knight standing behind the flask, upper-left, in canvas
# coordinates. Read from the canonical piece art and recolored to teal.
KNIGHT_SVG = ROOT / "assets" / "embedded" / "pieces" / "svg" / "white-knight.svg"
KNIGHT_X = 150
KNIGHT_Y = 162
KNIGHT_SIZE = 578


def squircle_path(cx: float, cy: float, a: float, exponent: float = SQUIRCLE_EXPONENT, steps: int = 192) -> str:
    """Return an SVG path approximating a Big Sur squircle (superellipse)."""
    power = 2.0 / exponent
    points: list[str] = []
    for i in range(steps):
        theta = 2.0 * math.pi * i / steps
        ct = math.cos(theta)
        st = math.sin(theta)
        x = cx + a * math.copysign(abs(ct) ** power, ct)
        y = cy + a * math.copysign(abs(st) ** power, st)
        points.append(f"{x:.2f} {y:.2f}")
    return "M" + " L".join(points) + " Z"


def source_svg_mark() -> str:
    """Return the chemical flask geometry with ChessRTK glossy styling."""
    return dedent(
        """\
        <g transform="translate(-180.31 -244.48)">
          <path class="source-rim" d="m333.27 244.48c-9.0304 0-16.342 10.519-16.342 19.55v7.7384c0 6.3886 3.685 11.885 9.0111 14.56-0.0798 1.0586-0.15314 2.1275-0.15314 3.2074v153.8c-2.4178 4.5548-4.7734 9.2986-7.3312 14l-132.17 239.08c-21.284 39.127 19.219 72.395 43.071 72.395h330.72c23.851 0 64.789-32.472 43.071-72.395l-132.16-239.08c-2.5578-4.7019-4.9134-9.4456-7.3312-14v-153.8c0-1.0798-0.0733-2.1487-0.15314-3.2074 5.3262-2.6758 9.0111-8.1719 9.0111-14.56v-7.7384c0-9.0306-7.2608-19.55-16.291-19.55z"/>
          <g class="source-glass" transform="matrix(1.6291 0 0 1.6291 -1390.6 -814.74)">
            <path d="m1080.3 768.8h31.307c12.456 0 16.531 11.607 22.483 22.611l70.701 130.71c14.28 26.399-10.028 38.012-22.484 38.012h-172.71c-12.456 0-35.208-14.489-22.483-38.012l70.701-130.71c5.9522-11.004 10.028-22.611 22.483-22.611z"/>
            <rect x="1063.8" y="670.49" width="64.23" height="117.7" ry="0"/>
            <rect transform="rotate(-90)" x="-671.96" y="1058.1" width="13.263" height="75.529" ry="4.3"/>
          </g>
          <path class="source-liquid" d="m461.99 505.91 91.927 174.75c20.125 38.256-14.442 54.746-32.381 54.746h-248.74c-17.939 0-50.781-20.907-32.381-54.746l90.127-165.75c79.68 31.256 108.66-55.62 131.45-8.999z"/>
          <path class="source-sheen" d="m360 300 14 0 0 150c-2.4 4.6-4.8 9.3-7.3 14l-70 126c-9-22 2-44 22-58 22-15 38-36 41-62 3-26-1-52 0.3-78z" opacity="0.5"/>
          <circle class="source-bubble" cx="470" cy="600" r="11"/>
          <circle class="source-bubble" cx="490" cy="578" r="6"/>
          <rect class="source-neck-line" x="413.88" y="281.54" width="15.08" height="164.1" ry="7.5399"/>
        </g>
        """
    )


def knight_layer() -> str:
    """Return the large background knight rendered in the flask's glass material.

    The knight reuses the flask's white glass body gradient and soft teal rim so
    the two objects read as the same material in the same light.
    """
    knight = KNIGHT_SVG.read_text(encoding="utf-8")
    inner = knight[knight.index("<g shape-rendering"):knight.index("</svg>")]
    inner = inner.replace('fill="#000000"', 'fill="url(#rimGrad)"')
    inner = inner.replace('fill="#ffffff"', 'fill="url(#glassGrad)"')
    return (
        f'<svg x="{KNIGHT_X}" y="{KNIGHT_Y}" width="{KNIGHT_SIZE}" '
        f'height="{KNIGHT_SIZE}" viewBox="0 0 200 200">{inner}</svg>'
    )


def write_svg() -> None:
    """Write the vector source for the Big Sur style chemical-board logo."""
    tile = squircle_path(TILE_C, TILE_C, TILE_A)
    tile_inner = squircle_path(TILE_C, TILE_C, TILE_A - 6)
    half = TILE_A  # used to place tiles relative to centre
    lo = TILE_C - half
    hi = TILE_C + half
    svg = dedent(
        f"""\
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {SIZE} {SIZE}" role="img" aria-label="ChessRTK chemical-board flask logo">
          <defs>
            <clipPath id="tileClip"><path d="{tile}"/></clipPath>
            <linearGradient id="lightTile" x1="0" x2="0" y1="{lo}" y2="{hi}" gradientUnits="userSpaceOnUse">
              <stop offset="0" stop-color="{LIGHT_TILE_TOP}"/>
              <stop offset="1" stop-color="{LIGHT_TILE_BOTTOM}"/>
            </linearGradient>
            <linearGradient id="darkTile" x1="0" x2="0" y1="{lo}" y2="{hi}" gradientUnits="userSpaceOnUse">
              <stop offset="0" stop-color="{DARK_TILE_TOP}"/>
              <stop offset="1" stop-color="{DARK_TILE_BOTTOM}"/>
            </linearGradient>
            <linearGradient id="gloss" x1="0" x2="0" y1="{lo}" y2="{hi}" gradientUnits="userSpaceOnUse">
              <stop offset="0" stop-color="#ffffff" stop-opacity="0.30"/>
              <stop offset="0.28" stop-color="#ffffff" stop-opacity="0.05"/>
              <stop offset="0.58" stop-color="#ffffff" stop-opacity="0"/>
              <stop offset="1" stop-color="{TILE_SHADOW}" stop-opacity="0.20"/>
            </linearGradient>
            <radialGradient id="topSheen" cx="0.5" cy="0.06" r="0.75">
              <stop offset="0" stop-color="#ffffff" stop-opacity="0.34"/>
              <stop offset="0.45" stop-color="#ffffff" stop-opacity="0"/>
            </radialGradient>
            <linearGradient id="glassGrad" x1="0.12" y1="0.05" x2="0.9" y2="1">
              <stop offset="0" stop-color="{GLASS_TOP}"/>
              <stop offset="0.5" stop-color="{GLASS_MID}"/>
              <stop offset="1" stop-color="{GLASS_BOTTOM}"/>
            </linearGradient>
            <linearGradient id="rimGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stop-color="{RIM_TOP}"/>
              <stop offset="1" stop-color="{RIM_BOTTOM}"/>
            </linearGradient>
            <linearGradient id="liquidGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stop-color="{LIQUID_TOP}"/>
              <stop offset="0.55" stop-color="{LIQUID_MID}"/>
              <stop offset="1" stop-color="{LIQUID_BOTTOM}"/>
            </linearGradient>
            <linearGradient id="knightGrad" x1="0.15" y1="0.05" x2="0.85" y2="1">
              <stop offset="0" stop-color="#dcf3f9"/>
              <stop offset="0.5" stop-color="#85cadc"/>
              <stop offset="1" stop-color="#46a1bd"/>
            </linearGradient>
            <filter id="tileShadow" x="-12%" y="-10%" width="124%" height="128%" color-interpolation-filters="sRGB">
              <feGaussianBlur in="SourceAlpha" stdDeviation="16" result="b"/>
              <feOffset in="b" dx="0" dy="14" result="o"/>
              <feFlood flood-color="{TILE_SHADOW}" flood-opacity="0.26" result="c"/>
              <feComposite in="c" in2="o" operator="in" result="s"/>
              <feMerge><feMergeNode in="s"/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
            <filter id="flaskShadow" x="-35%" y="-20%" width="170%" height="165%" color-interpolation-filters="sRGB">
              <feGaussianBlur in="SourceAlpha" stdDeviation="17" result="b"/>
              <feOffset in="b" dx="0" dy="18" result="o"/>
              <feFlood flood-color="{SHADOW_COLOR}" flood-opacity="0.34" result="c"/>
              <feComposite in="c" in2="o" operator="in" result="s"/>
              <feMerge><feMergeNode in="s"/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
            <filter id="knightShadow" x="-30%" y="-15%" width="155%" height="150%" color-interpolation-filters="sRGB">
              <feGaussianBlur in="SourceAlpha" stdDeviation="13" result="b"/>
              <feOffset in="b" dx="2" dy="12" result="o"/>
              <feFlood flood-color="{SHADOW_COLOR}" flood-opacity="0.24" result="c"/>
              <feComposite in="c" in2="o" operator="in" result="s"/>
              <feMerge><feMergeNode in="s"/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
            <style>
              .source-rim {{ fill: url(#rimGrad); }}
              .source-glass {{ fill: url(#glassGrad); }}
              .source-liquid {{ fill: url(#liquidGrad); }}
              .source-sheen {{ fill: #ffffff; }}
              .source-bubble {{ fill: {BUBBLE_LIGHT}; opacity: 0.9; }}
              .source-neck-line {{ fill: #ffffff; opacity: 0.5; }}
            </style>
          </defs>

          <g filter="url(#tileShadow)">
            <path d="{tile}" fill="url(#lightTile)"/>
            <g clip-path="url(#tileClip)">
              <rect x="{lo}" y="{lo}" width="{half}" height="{half}" fill="url(#darkTile)"/>
              <rect x="{BOARD_MID}" y="{BOARD_MID}" width="{half}" height="{half}" fill="url(#darkTile)"/>
              <path d="M{BOARD_MID} {lo} V{hi} M{lo} {BOARD_MID} H{hi}" stroke="{TILE_DIVIDER}" stroke-opacity="0.16" stroke-width="4"/>
              <rect x="{lo}" y="{lo}" width="{2 * half}" height="{2 * half}" fill="url(#gloss)"/>
              <rect x="{lo}" y="{lo}" width="{2 * half}" height="{2 * half}" fill="url(#topSheen)"/>
            </g>
            <path d="{tile_inner}" fill="none" stroke="#ffffff" stroke-opacity="0.40" stroke-width="3"/>
            <path d="{tile}" fill="none" stroke="{TILE_SHADOW}" stroke-opacity="0.10" stroke-width="3"/>
          </g>

          <g>
            {knight_layer()}
          </g>

          <g>
            <svg x="{MARK_X:.3f}" y="{MARK_Y:.3f}" width="{MARK_WIDTH:.3f}" height="{MARK_HEIGHT}" viewBox="0 0 {SOURCE_VIEWBOX_WIDTH} {SOURCE_VIEWBOX_HEIGHT}" overflow="visible">
        {source_svg_mark()}
            </svg>
          </g>
        </svg>
        """
    )
    SVG_PATH.write_text(svg, encoding="utf-8")


def render_png() -> None:
    """Render the SVG logo to PNG with Inkscape."""
    inkscape = shutil.which("inkscape")
    if inkscape is None:
        raise RuntimeError("Inkscape is required to render the SVG app icon to PNG")
    subprocess.run(
        [
            inkscape,
            "--export-type=png",
            f"--export-filename={PNG_PATH}",
            f"--export-width={SIZE}",
            f"--export-height={SIZE}",
            str(SVG_PATH),
        ],
        check=True,
    )
    with Image.open(PNG_PATH) as image:
        image.convert("RGBA").save(PNG_PATH)


def write_ico() -> None:
    """Write a multi-size ICO, rendering each size natively for clean edges.

    Rendering each frame straight from the SVG at its target size (rather than
    downscaling one large raster) keeps the small 16/24/32 px frames crisp and
    anti-aliased instead of muddy.
    """
    inkscape = shutil.which("inkscape")
    if inkscape is None:
        raise RuntimeError("Inkscape is required to render the ICO frames")
    sizes = [16, 24, 32, 48, 64, 128, 256]
    blobs: list[tuple[int, bytes]] = []
    for size in sizes:
        tmp = OUTPUT_DIR / f".ico-frame-{size}.png"
        subprocess.run(
            [
                inkscape,
                "--export-type=png",
                f"--export-filename={tmp}",
                f"--export-width={size}",
                f"--export-height={size}",
                str(SVG_PATH),
            ],
            check=True,
        )
        blobs.append((size, tmp.read_bytes()))
        tmp.unlink()
    # Assemble the ICO by hand so every frame keeps its own natively rendered,
    # anti-aliased PNG (Pillow's ICO save would downscale a single base image).
    header = struct.pack("<HHH", 0, 1, len(blobs))
    offset = 6 + 16 * len(blobs)
    entries = b""
    body = b""
    for size, data in blobs:
        dim = 0 if size >= 256 else size  # 0 means 256 in the ICO directory
        entries += struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
        body += data
    ICO_PATH.write_bytes(header + entries + body)


def main() -> None:
    """Generate PNG, ICO, and SVG chemical-board logo assets."""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    write_svg()
    render_png()
    write_ico()
    print(f"wrote {PNG_PATH.relative_to(ROOT)}")
    print(f"wrote {ICO_PATH.relative_to(ROOT)}")
    print(f"wrote {SVG_PATH.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
