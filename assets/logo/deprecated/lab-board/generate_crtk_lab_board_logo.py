#!/usr/bin/env python3
"""Generate the deprecated ChessRTK lab-board app logo assets."""

from __future__ import annotations

from pathlib import Path
from textwrap import dedent

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[4]
OUTPUT_DIR = Path(__file__).resolve().parent
PNG_PATH = OUTPUT_DIR / "crtk-lab-board.png"
ICO_PATH = OUTPUT_DIR / "crtk-lab-board.ico"
SVG_PATH = OUTPUT_DIR / "crtk-lab-board.svg"

SIZE = 1024
SCALE = 4

LIGHT_BLUE = "#acd9e2"
PALE_BLUE = "#d5edf0"
MID_BLUE = "#4d9eb7"
DARK_BLUE = "#256579"
INK_BLUE = "#183847"
WARM = "#f2a04c"
WARM_DARK = "#c96d35"
WARM_LIGHT = "#ffc06d"
CREAM = "#fff1cf"


def point(x: float, y: float) -> tuple[int, int]:
    """Return a scaled integer drawing point."""
    return (round(x * SCALE), round(y * SCALE))


def box(x0: float, y0: float, x1: float, y1: float) -> tuple[int, int, int, int]:
    """Return a scaled integer drawing box."""
    return (*point(x0, y0), *point(x1, y1))


def width(value: float) -> int:
    """Return a scaled stroke width."""
    return max(1, round(value * SCALE))


def scaled(points: list[tuple[float, float]]) -> list[tuple[int, int]]:
    """Return scaled integer drawing points."""
    return [point(x, y) for x, y in points]


def resample_filter() -> int:
    """Return the best available Pillow resampling filter."""
    return getattr(Image, "Resampling", Image).LANCZOS


def rotate_filter() -> int:
    """Return the best Pillow filter supported by affine rotation."""
    return getattr(Image, "Resampling", Image).BICUBIC


def draw_round_line(
        draw: ImageDraw.ImageDraw,
        points: list[tuple[float, float]],
        fill: tuple[int, int, int, int] | str,
        stroke: float) -> None:
    """Draw a polyline with round caps."""
    draw.line(scaled(points), fill=fill, width=width(stroke), joint="curve")
    radius = stroke / 2
    for x, y in (points[0], points[-1]):
        draw.ellipse(box(x - radius, y - radius, x + radius, y + radius), fill=fill)


def rotate_layer(layer: Image.Image, angle: float, center: tuple[float, float]) -> tuple[Image.Image, tuple[int, int]]:
    """Rotate a layer around its center and return the paste location."""
    rotated = layer.rotate(angle, resample=rotate_filter(), expand=True)
    x = round(center[0] * SCALE - rotated.width / 2)
    y = round(center[1] * SCALE - rotated.height / 2)
    return rotated, (x, y)


def draw_rotated_round_rect(
        canvas: Image.Image,
        center: tuple[float, float],
        size: tuple[float, float],
        angle: float,
        radius: float,
        fill: str,
        outline: str,
        stroke: float) -> None:
    """Draw a rotated rounded rectangle."""
    pad = width(stroke + radius + 8)
    layer = Image.new("RGBA", (width(size[0]) + pad * 2, width(size[1]) + pad * 2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(
        (pad, pad, pad + width(size[0]), pad + width(size[1])),
        radius=width(radius),
        fill=fill,
        outline=outline,
        width=width(stroke),
    )
    rotated, location = rotate_layer(layer, angle, center)
    canvas.alpha_composite(rotated, location)


def draw_board(canvas: Image.Image) -> None:
    """Draw the blue 2x2 app-icon board."""
    draw = ImageDraw.Draw(canvas)
    board = box(152, 152, 872, 872)
    mask = Image.new("L", canvas.size, 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle(board, radius=width(58), fill=255)

    board_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    board_draw = ImageDraw.Draw(board_layer)
    board_draw.rectangle(box(152, 152, 512, 512), fill=LIGHT_BLUE)
    board_draw.rectangle(box(512, 152, 872, 512), fill=PALE_BLUE)
    board_draw.rectangle(box(152, 512, 512, 872), fill=MID_BLUE)
    board_draw.rectangle(box(512, 512, 872, 872), fill=DARK_BLUE)
    board_draw.line([point(512, 152), point(512, 872)], fill=(21, 58, 72, 48), width=width(5))
    board_draw.line([point(152, 512), point(872, 512)], fill=(21, 58, 72, 48), width=width(5))
    board_layer.putalpha(mask)

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(box(152, 160, 872, 880), radius=width(58), fill=(0, 0, 0, 55))
    shadow = shadow.filter(ImageFilter.GaussianBlur(width(5)))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(board_layer)
    draw.rounded_rectangle(board, radius=width(58), outline=(255, 255, 255, 30), width=width(3))


def draw_microscope(canvas: Image.Image) -> None:
    """Draw the warm microscope glyph."""
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    draw_round_line(shadow_draw, [(440, 710), (548, 696), (634, 548), (606, 396)], (0, 0, 0, 120), 116)
    shadow_draw.rounded_rectangle(box(252, 774, 748, 876), radius=width(44), fill=(0, 0, 0, 105))
    shadow = shadow.filter(ImageFilter.GaussianBlur(width(7)))
    canvas.alpha_composite(shadow)

    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(box(266, 760, 738, 846), radius=width(42), fill=WARM_DARK)
    draw.rounded_rectangle(box(326, 690, 574, 776), radius=width(30), fill=WARM)
    draw_round_line(draw, [(430, 690), (548, 664), (620, 536), (596, 394)], WARM_DARK, 104)
    draw_round_line(draw, [(456, 684), (570, 640), (620, 526), (592, 408)], WARM, 70)

    draw.rounded_rectangle(box(206, 552, 602, 610), radius=width(24), fill=INK_BLUE)
    draw.rounded_rectangle(box(214, 562, 590, 596), radius=width(14), fill=CREAM)

    draw_rotated_round_rect(canvas, (474, 336), (166, 344), -28, 28, WARM, WARM_DARK, 8)
    draw_rotated_round_rect(canvas, (438, 150), (142, 86), -28, 24, WARM_LIGHT, WARM_DARK, 7)
    draw_rotated_round_rect(canvas, (338, 516), (118, 74), -28, 20, WARM_DARK, WARM_DARK, 6)
    draw_rotated_round_rect(canvas, (326, 578), (170, 38), -28, 18, INK_BLUE, INK_BLUE, 4)

    draw.ellipse(box(480, 370, 616, 506), fill=INK_BLUE)
    draw.ellipse(box(500, 390, 596, 486), fill=CREAM)
    draw.ellipse(box(524, 414, 572, 462), fill=WARM_DARK)
    draw.ellipse(box(414, 684, 552, 822), fill=INK_BLUE)
    draw.ellipse(box(434, 704, 532, 802), fill=CREAM)
    draw.ellipse(box(458, 728, 508, 778), fill=WARM_DARK)

    draw.polygon(scaled([(374, 244), (448, 220), (438, 258), (392, 290)]), fill=WARM_LIGHT)
    draw.polygon(scaled([(388, 452), (454, 426), (438, 466), (410, 488)]), fill=WARM_LIGHT)


def draw_icon() -> Image.Image:
    """Render the deprecated lab-board logo."""
    full_size = SIZE * SCALE
    canvas = Image.new("RGBA", (full_size, full_size), (0, 0, 0, 0))
    draw_board(canvas)
    draw_microscope(canvas)
    return canvas.resize((SIZE, SIZE), resample_filter())


def write_svg() -> None:
    """Write the vector source for the deprecated lab-board logo."""
    svg = dedent(
        f"""\
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" role="img" aria-label="ChessRTK lab-board microscope logo">
          <defs>
            <clipPath id="boardClip"><rect x="152" y="152" width="720" height="720" rx="58"/></clipPath>
            <filter id="softShadow" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="14" dy="20" stdDeviation="8" flood-color="#000" flood-opacity="0.34"/>
            </filter>
          </defs>
          <g clip-path="url(#boardClip)">
            <rect x="152" y="152" width="360" height="360" fill="{LIGHT_BLUE}"/>
            <rect x="512" y="152" width="360" height="360" fill="{PALE_BLUE}"/>
            <rect x="152" y="512" width="360" height="360" fill="{MID_BLUE}"/>
            <rect x="512" y="512" width="360" height="360" fill="{DARK_BLUE}"/>
            <path d="M512 152 V872 M152 512 H872" stroke="#153a48" stroke-opacity="0.2" stroke-width="5"/>
          </g>
          <rect x="152" y="152" width="720" height="720" rx="58" fill="none" stroke="#fff" stroke-opacity="0.12" stroke-width="3"/>
          <g filter="url(#softShadow)">
            <rect x="266" y="760" width="472" height="86" rx="42" fill="{WARM_DARK}"/>
            <rect x="326" y="690" width="248" height="86" rx="30" fill="{WARM}"/>
            <path d="M430 690 C548 664 620 536 596 394" fill="none" stroke="{WARM_DARK}" stroke-width="104" stroke-linecap="round"/>
            <path d="M456 684 C570 640 620 526 592 408" fill="none" stroke="{WARM}" stroke-width="70" stroke-linecap="round"/>
            <rect x="206" y="552" width="396" height="58" rx="24" fill="{INK_BLUE}"/>
            <rect x="214" y="562" width="376" height="34" rx="14" fill="{CREAM}"/>
            <g transform="rotate(-28 474 336)">
              <rect x="391" y="164" width="166" height="344" rx="28" fill="{WARM}" stroke="{WARM_DARK}" stroke-width="8"/>
            </g>
            <g transform="rotate(-28 438 150)">
              <rect x="367" y="107" width="142" height="86" rx="24" fill="{WARM_LIGHT}" stroke="{WARM_DARK}" stroke-width="7"/>
            </g>
            <g transform="rotate(-28 338 516)">
              <rect x="279" y="479" width="118" height="74" rx="20" fill="{WARM_DARK}" stroke="{WARM_DARK}" stroke-width="6"/>
            </g>
            <g transform="rotate(-28 326 578)">
              <rect x="241" y="559" width="170" height="38" rx="18" fill="{INK_BLUE}" stroke="{INK_BLUE}" stroke-width="4"/>
            </g>
            <circle cx="548" cy="438" r="68" fill="{INK_BLUE}"/>
            <circle cx="548" cy="438" r="48" fill="{CREAM}"/>
            <circle cx="548" cy="438" r="24" fill="{WARM_DARK}"/>
            <circle cx="483" cy="753" r="69" fill="{INK_BLUE}"/>
            <circle cx="483" cy="753" r="49" fill="{CREAM}"/>
            <circle cx="483" cy="753" r="25" fill="{WARM_DARK}"/>
            <path d="M374 244 L448 220 L438 258 L392 290 Z" fill="{WARM_LIGHT}"/>
            <path d="M388 452 L454 426 L438 466 L410 488 Z" fill="{WARM_LIGHT}"/>
          </g>
        </svg>
        """
    )
    SVG_PATH.write_text(svg, encoding="utf-8")


def main() -> None:
    """Generate PNG, ICO, and SVG lab-board logo assets."""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    icon = draw_icon()
    icon.save(PNG_PATH)
    icon.save(ICO_PATH, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    write_svg()
    print(f"wrote {PNG_PATH.relative_to(ROOT)}")
    print(f"wrote {ICO_PATH.relative_to(ROOT)}")
    print(f"wrote {SVG_PATH.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
