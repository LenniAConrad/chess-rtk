#!/usr/bin/env python3
"""Generate the primary ChessRTK application icon assets."""

from __future__ import annotations

from pathlib import Path
from textwrap import dedent

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[4]
OUTPUT_DIR = Path(__file__).resolve().parent
PNG_PATH = OUTPUT_DIR / "crtk-otis-lattice.png"
ICO_PATH = OUTPUT_DIR / "crtk-otis-lattice.ico"
SVG_PATH = OUTPUT_DIR / "crtk-otis-lattice.svg"
BOARD_PATH = ROOT / "assets" / "embedded" / "board" / "png" / "board.png"
KING_PATH = ROOT / "assets" / "embedded" / "pieces" / "png" / "white-king.png"

SIZE = 1024
SCALE = 4
BOARD_CROP_SQUARES = 2

DEEP = "#121212"
PANEL = "#202020"
GRID = "#74d8c7"
GRID_SOFT = "#2d8f7e"
IVORY = "#fff8e7"
GOLD = "#f2bf4f"
PRESSURE = "#e55d4a"
SHADOW = "#050505"


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


def interp(a: tuple[float, float], b: tuple[float, float], t: float) -> tuple[float, float]:
    """Linearly interpolate between two points."""
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def draw_polyline(draw: ImageDraw.ImageDraw, points: list[tuple[float, float]], fill: str, stroke: float) -> None:
    """Draw a scaled polyline with rounded joins where Pillow supports it."""
    draw.line(scaled(points), fill=fill, width=width(stroke), joint="curve")


def resample_filter() -> int:
    """Return the best available Pillow resampling filter."""
    return getattr(Image, "Resampling", Image).LANCZOS


def rotate_filter() -> int:
    """Return the best Pillow filter supported by affine rotation."""
    return getattr(Image, "Resampling", Image).BICUBIC


def load_rgba(path: Path) -> Image.Image:
    """Load an image asset as RGBA."""
    return Image.open(path).convert("RGBA")


def paste_center(canvas: Image.Image, image: Image.Image, center: tuple[float, float]) -> None:
    """Paste an RGBA image centered at an icon-space coordinate."""
    x = round(center[0] * SCALE - image.width / 2)
    y = round(center[1] * SCALE - image.height / 2)
    canvas.alpha_composite(image, (x, y))


def alpha_shadow(source: Image.Image, blur: float, opacity: float) -> Image.Image:
    """Build a soft black shadow from an image alpha channel."""
    alpha = source.getchannel("A").filter(ImageFilter.GaussianBlur(width(blur)))
    shadow = Image.new("RGBA", source.size, (0, 0, 0, 0))
    shadow.putalpha(alpha.point(lambda value: round(value * opacity)))
    return shadow


def coat_board(board: Image.Image) -> Image.Image:
    """Apply a subtle OTIS color coat to the 2x2 board crop."""
    coated = board.copy()
    overlay = Image.new("RGBA", coated.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    half = coated.width // 2
    coats = [
        ((0, 0, half, half), (116, 216, 199, 64)),
        ((half, 0, coated.width, half), (242, 191, 79, 58)),
        ((0, half, half, coated.height), (116, 216, 199, 42)),
        ((half, half, coated.width, coated.height), (229, 93, 74, 58)),
    ]
    for region, color in coats:
        draw.rectangle(region, fill=color)
    return Image.alpha_composite(coated, overlay)


def board_diamond() -> Image.Image:
    """Return a 2x2 crop of the shared board asset rotated into a diamond."""
    board_size = 470 * SCALE
    board_source = load_rgba(BOARD_PATH)
    square_size = board_source.width // 8
    crop_size = square_size * BOARD_CROP_SQUARES
    board = board_source.crop((0, 0, crop_size, crop_size)).resize((board_size, board_size), resample_filter())
    board = coat_board(board)
    rotated = board.rotate(45, resample=rotate_filter(), expand=True, fillcolor=(0, 0, 0, 0))
    rotated.putalpha(rotated.getchannel("A").point(lambda value: round(value * 0.96)))
    return rotated


def king_piece() -> Image.Image:
    """Return the shared white king asset scaled for the icon center."""
    piece_size = 455 * SCALE
    return load_rgba(KING_PATH).resize((piece_size, piece_size), resample_filter())


def draw_relation_line(
        draw: ImageDraw.ImageDraw,
        points: list[tuple[float, float]],
        fill: str,
        stroke: float) -> None:
    """Draw a tactical relation line with a dark contrast underlay."""
    draw_polyline(draw, points, (0, 0, 0, 165), stroke + 8)
    draw_polyline(draw, points, fill, stroke)


def draw_icon() -> Image.Image:
    """Render the OTIS icon using the shared board and white king assets."""
    canvas = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    draw.rounded_rectangle(box(34, 34, 990, 990), radius=width(190), fill=DEEP)
    draw.rounded_rectangle(box(72, 72, 952, 952), radius=width(154), fill=PANEL)
    draw.rounded_rectangle(box(72, 72, 952, 952), radius=width(154), outline="#3b3b3b", width=width(8))

    top = (512, 164)
    right = (860, 512)
    bottom = (512, 860)
    left = (164, 512)
    center = (512, 512)
    diamond = [top, right, bottom, left]
    draw.polygon(scaled([(512, 124), (900, 512), (512, 900), (124, 512)]), fill=(0, 0, 0, 120))
    paste_center(canvas, board_diamond(), center)
    draw_polyline(draw, [top, right, bottom, left, top], "#4f4f4f", 14)
    draw_polyline(draw, [top, right, bottom, left, top], GRID_SOFT, 7)

    for t in (0.25, 0.5, 0.75):
        draw_polyline(draw, [interp(left, top, t), interp(bottom, right, t)], (0, 0, 0, 110), 8)
        draw_polyline(draw, [interp(top, right, t), interp(left, bottom, t)], (0, 0, 0, 110), 8)

    relation_nodes = [
        (top, GOLD, 34),
        (right, PRESSURE, 38),
        (bottom, GOLD, 30),
        (left, GRID, 34),
        ((332, 332), GRID, 28),
        ((692, 332), GOLD, 28),
        ((700, 700), PRESSURE, 30),
        ((324, 700), GRID, 28),
    ]
    for node, color, radius in relation_nodes:
        draw_relation_line(draw, [center, node], color, 11 if color != PRESSURE else 13)
    draw_relation_line(
        draw,
        [(332, 332), (692, 332), right, (700, 700), bottom, (324, 700), left, (332, 332)],
        GRID,
        7,
    )

    piece = king_piece()
    shadow = alpha_shadow(piece, 7, 0.38)
    paste_center(canvas, shadow, (518, 528))
    paste_center(canvas, piece, (512, 520))

    for node, color, radius in relation_nodes:
        draw.ellipse(
            box(node[0] - radius - 8, node[1] - radius - 8, node[0] + radius + 8, node[1] + radius + 8),
            fill=(0, 0, 0, 125),
        )
        draw.ellipse(
            box(node[0] - radius, node[1] - radius, node[0] + radius, node[1] + radius),
            fill=color,
            outline=IVORY,
            width=width(7),
        )

    draw.ellipse(box(472, 472, 552, 552), fill=DEEP, outline=IVORY, width=width(8))
    draw.ellipse(box(494, 494, 530, 530), fill=GOLD)

    return canvas.resize((SIZE, SIZE), resample_filter())


def write_svg() -> None:
    """Write a vector source that references the shared board and king assets."""
    svg = dedent(
        f"""\
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" role="img" aria-label="ChessRTK OTIS tactical lattice icon">
          <rect x="34" y="34" width="956" height="956" rx="190" fill="{DEEP}"/>
          <rect x="72" y="72" width="880" height="880" rx="154" fill="{PANEL}" stroke="#3b3b3b" stroke-width="8"/>
          <path d="M512 124 L900 512 L512 900 L124 512 Z" fill="#000" opacity="0.47"/>
          <g transform="rotate(45 512 512)" opacity="0.96">
            <svg x="277" y="277" width="470" height="470" viewBox="0 0 400 400" preserveAspectRatio="none">
              <image href="../../../embedded/board/png/board.png" x="0" y="0" width="1600" height="1600"/>
              <rect x="0" y="0" width="200" height="200" fill="{GRID}" opacity="0.25"/>
              <rect x="200" y="0" width="200" height="200" fill="{GOLD}" opacity="0.23"/>
              <rect x="0" y="200" width="200" height="200" fill="{GRID}" opacity="0.16"/>
              <rect x="200" y="200" width="200" height="200" fill="{PRESSURE}" opacity="0.23"/>
            </svg>
          </g>
          <path d="M512 164 L860 512 L512 860 L164 512 Z" fill="none" stroke="#4f4f4f" stroke-width="14" stroke-linejoin="round"/>
          <path d="M512 164 L860 512 L512 860 L164 512 Z" fill="none" stroke="{GRID_SOFT}" stroke-width="7" stroke-linejoin="round"/>
          <g fill="none" stroke="#000" stroke-width="8" stroke-linecap="round" opacity="0.43">
            <path d="M251 425 L599 773"/><path d="M338 338 L686 686"/><path d="M425 251 L773 599"/>
            <path d="M599 251 L251 599"/><path d="M686 338 L338 686"/><path d="M773 425 L425 773"/>
          </g>
          <g fill="none" stroke-linecap="round" stroke-linejoin="round">
            <path d="M512 512 L512 164" stroke="#000" stroke-width="19" opacity="0.65"/>
            <path d="M512 512 L512 164" stroke="{GOLD}" stroke-width="11"/>
            <path d="M512 512 L860 512" stroke="#000" stroke-width="21" opacity="0.65"/>
            <path d="M512 512 L860 512" stroke="{PRESSURE}" stroke-width="13"/>
            <path d="M512 512 L512 860" stroke="#000" stroke-width="19" opacity="0.65"/>
            <path d="M512 512 L512 860" stroke="{GOLD}" stroke-width="11"/>
            <path d="M512 512 L164 512" stroke="#000" stroke-width="19" opacity="0.65"/>
            <path d="M512 512 L164 512" stroke="{GRID}" stroke-width="11"/>
            <path d="M512 512 L332 332" stroke="#000" stroke-width="19" opacity="0.65"/>
            <path d="M512 512 L332 332" stroke="{GRID}" stroke-width="11"/>
            <path d="M512 512 L692 332" stroke="#000" stroke-width="19" opacity="0.65"/>
            <path d="M512 512 L692 332" stroke="{GOLD}" stroke-width="11"/>
            <path d="M512 512 L700 700" stroke="#000" stroke-width="21" opacity="0.65"/>
            <path d="M512 512 L700 700" stroke="{PRESSURE}" stroke-width="13"/>
            <path d="M512 512 L324 700" stroke="#000" stroke-width="19" opacity="0.65"/>
            <path d="M512 512 L324 700" stroke="{GRID}" stroke-width="11"/>
            <path d="M332 332 L692 332 L860 512 L700 700 L512 860 L324 700 L164 512 L332 332" stroke="#000" stroke-width="15" opacity="0.65"/>
            <path d="M332 332 L692 332 L860 512 L700 700 L512 860 L324 700 L164 512 L332 332" stroke="{GRID}" stroke-width="7"/>
          </g>
          <image href="../../../embedded/pieces/png/white-king.png" x="284.5" y="292.5" width="455" height="455"/>
          <g stroke="{IVORY}" stroke-width="7">
            <circle cx="512" cy="164" r="42" fill="#000" opacity="0.49" stroke="none"/>
            <circle cx="512" cy="164" r="34" fill="{GOLD}"/>
            <circle cx="860" cy="512" r="46" fill="#000" opacity="0.49" stroke="none"/>
            <circle cx="860" cy="512" r="38" fill="{PRESSURE}"/>
            <circle cx="512" cy="860" r="38" fill="#000" opacity="0.49" stroke="none"/>
            <circle cx="512" cy="860" r="30" fill="{GOLD}"/>
            <circle cx="164" cy="512" r="42" fill="#000" opacity="0.49" stroke="none"/>
            <circle cx="164" cy="512" r="34" fill="{GRID}"/>
            <circle cx="332" cy="332" r="36" fill="#000" opacity="0.49" stroke="none"/>
            <circle cx="332" cy="332" r="28" fill="{GRID}"/>
            <circle cx="692" cy="332" r="36" fill="#000" opacity="0.49" stroke="none"/>
            <circle cx="692" cy="332" r="28" fill="{GOLD}"/>
            <circle cx="700" cy="700" r="38" fill="#000" opacity="0.49" stroke="none"/>
            <circle cx="700" cy="700" r="30" fill="{PRESSURE}"/>
            <circle cx="324" cy="700" r="36" fill="#000" opacity="0.49" stroke="none"/>
            <circle cx="324" cy="700" r="28" fill="{GRID}"/>
          </g>
          <circle cx="512" cy="512" r="40" fill="{DEEP}" stroke="{IVORY}" stroke-width="8"/>
          <circle cx="512" cy="512" r="18" fill="{GOLD}"/>
        </svg>
        """
    )
    SVG_PATH.write_text(svg, encoding="utf-8")


def main() -> None:
    """Generate PNG, ICO, and SVG application icon assets."""
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
