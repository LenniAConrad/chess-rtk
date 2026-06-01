#!/usr/bin/env python3
"""Generate the deprecated ChessRTK route-knight app logo assets."""

from __future__ import annotations

from pathlib import Path
from textwrap import dedent

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[4]
OUTPUT_DIR = Path(__file__).resolve().parent
PNG_PATH = OUTPUT_DIR / "crtk-route-knight.png"
ICO_PATH = OUTPUT_DIR / "crtk-route-knight.ico"
SVG_PATH = OUTPUT_DIR / "crtk-route-knight.svg"

SIZE = 1024
SCALE = 4

DEEP = "#151d23"
NAVY = "#07384f"
BLUE = "#08799f"
INK = "#102432"
INK_SOFT = "#183a47"
CREAM = "#f7d492"
SAND = "#d7bc8c"
BROWN = "#8e4e3f"
TEAL = "#6ed3c5"
IVORY = "#f8f7ef"
AMBER = "#f5a94d"
ORANGE = "#d7793e"


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


def vertical_gradient(width_px: int, height_px: int, top: str, bottom: str) -> Image.Image:
    """Create a vertical RGB gradient image."""
    top_rgb = tuple(int(top[index:index + 2], 16) for index in (1, 3, 5))
    bottom_rgb = tuple(int(bottom[index:index + 2], 16) for index in (1, 3, 5))
    image = Image.new("RGBA", (width_px, height_px), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for y in range(height_px):
        t = y / max(1, height_px - 1)
        color = tuple(round(top_rgb[i] * (1 - t) + bottom_rgb[i] * t) for i in range(3))
        draw.line([(0, y), (width_px, y)], fill=(*color, 255))
    return image


def rounded_mask(size: tuple[int, int], radius: float) -> Image.Image:
    """Create a rounded rectangle mask."""
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=width(radius), fill=255)
    return mask


def paste_masked(canvas: Image.Image, image: Image.Image, mask: Image.Image) -> None:
    """Composite an image through an alpha mask."""
    canvas.alpha_composite(Image.composite(image, Image.new("RGBA", image.size, (0, 0, 0, 0)), mask))


def draw_board_tiles(draw: ImageDraw.ImageDraw) -> None:
    """Draw a compact 2x2 board tile field."""
    x0, y0, tile = 220, 224, 292
    radius = 40
    draw.rounded_rectangle(box(x0 - 14, y0 - 14, x0 + tile * 2 + 14, y0 + tile * 2 + 14),
                           radius=width(radius + 12), fill=(7, 17, 23, 94))
    draw.rounded_rectangle(box(x0, y0, x0 + tile * 2, y0 + tile * 2), radius=width(radius), fill=SAND)
    draw.rectangle(box(x0, y0, x0 + tile, y0 + tile), fill=CREAM)
    draw.rectangle(box(x0 + tile, y0, x0 + tile * 2, y0 + tile), fill=(32, 119, 139, 255))
    draw.rectangle(box(x0, y0 + tile, x0 + tile, y0 + tile * 2), fill=(17, 74, 87, 255))
    draw.rectangle(box(x0 + tile, y0 + tile, x0 + tile * 2, y0 + tile * 2), fill=BROWN)
    draw.line([point(x0 + tile, y0), point(x0 + tile, y0 + tile * 2)], fill=(21, 38, 43, 76),
              width=width(5))
    draw.line([point(x0, y0 + tile), point(x0 + tile * 2, y0 + tile)], fill=(21, 38, 43, 76),
              width=width(5))


def route_points() -> list[tuple[float, float]]:
    """Return the L-shaped knight-route glyph points."""
    return [(274, 680), (410, 680), (410, 478), (592, 330), (684, 330)]


def draw_round_line(
        draw: ImageDraw.ImageDraw,
        points: list[tuple[float, float]],
        fill: tuple[int, int, int, int] | str,
        stroke: float) -> None:
    """Draw a polyline with rounded visual caps."""
    draw.line(scaled(points), fill=fill, width=width(stroke), joint="curve")
    radius = stroke / 2
    for x, y in (points[0], points[-1]):
        draw.ellipse(box(x - radius, y - radius, x + radius, y + radius), fill=fill)


def draw_node(draw: ImageDraw.ImageDraw, x: float, y: float, radius: float, fill: str, inner: str | None) -> None:
    """Draw a route node with an ivory rim."""
    draw.ellipse(box(x - radius - 12, y - radius - 12, x + radius + 12, y + radius + 12),
                 fill=(4, 16, 22, 150))
    draw.ellipse(box(x - radius, y - radius, x + radius, y + radius), fill=IVORY)
    if inner is not None:
        inner_radius = radius * 0.56
        draw.ellipse(box(x - inner_radius, y - inner_radius, x + inner_radius, y + inner_radius), fill=inner)
    else:
        shine_radius = radius * 0.34
        draw.ellipse(box(x - shine_radius, y - shine_radius, x + shine_radius, y + shine_radius), fill=fill)


def draw_route_glyph(canvas: Image.Image) -> None:
    """Draw the distinctive knight-move route glyph."""
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_points = [(x + 18, y + 22) for x, y in route_points()]
    draw_round_line(shadow_draw, shadow_points, (0, 0, 0, 130), 98)
    shadow = shadow.filter(ImageFilter.GaussianBlur(width(6)))
    canvas.alpha_composite(shadow)

    draw = ImageDraw.Draw(canvas)
    mane = [(504, 348), (636, 254), (737, 346), (668, 421), (582, 396)]
    draw.polygon(scaled([(x + 12, y + 14) for x, y in mane]), fill=(0, 0, 0, 90))
    draw.polygon(scaled(mane), fill=AMBER, outline=ORANGE)

    points = route_points()
    draw_round_line(draw, points, (13, 35, 48, 255), 100)
    draw_round_line(draw, points, IVORY, 70)
    draw_round_line(draw, [(292, 680), (410, 680), (410, 492), (592, 346), (662, 346)],
                    (215, 247, 249, 148), 18)

    draw_node(draw, 274, 680, 67, TEAL, "#12495b")
    draw_node(draw, 410, 478, 48, AMBER, AMBER)
    draw_node(draw, 684, 330, 72, BLUE, "#145a75")


def draw_icon() -> Image.Image:
    """Render the deprecated route-knight logo."""
    full_size = SIZE * SCALE
    canvas = Image.new("RGBA", (full_size, full_size), (0, 0, 0, 0))
    background = vertical_gradient(full_size, full_size, BLUE, DEEP)
    mask = rounded_mask((full_size, full_size), 190)
    paste_masked(canvas, background, mask)

    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(box(36, 36, 988, 988), radius=width(190), outline=(255, 255, 255, 22),
                           width=width(3))
    draw.rounded_rectangle(box(92, 92, 932, 932), radius=width(142), fill=(0, 0, 0, 18))
    draw_board_tiles(draw)
    draw_route_glyph(canvas)
    draw.arc(box(154, 148, 866, 860), start=210, end=313, fill=(255, 255, 255, 18), width=width(5))
    return canvas.resize((SIZE, SIZE), resample_filter())


def write_svg() -> None:
    """Write the vector source for the deprecated route-knight logo."""
    svg = dedent(
        f"""\
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" role="img" aria-label="ChessRTK route-knight logo">
          <defs>
            <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stop-color="{BLUE}"/>
              <stop offset="1" stop-color="{DEEP}"/>
            </linearGradient>
            <filter id="softShadow" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="18" dy="22" stdDeviation="10" flood-color="#000" flood-opacity="0.42"/>
            </filter>
          </defs>
          <rect x="34" y="34" width="956" height="956" rx="190" fill="url(#bg)"/>
          <rect x="36" y="36" width="952" height="952" rx="190" fill="none" stroke="#fff" stroke-opacity="0.09" stroke-width="3"/>
          <rect x="92" y="92" width="840" height="840" rx="142" fill="#000" opacity="0.07"/>
          <rect x="206" y="210" width="612" height="612" rx="52" fill="#071117" opacity="0.37"/>
          <clipPath id="boardClip"><rect x="220" y="224" width="584" height="584" rx="40"/></clipPath>
          <g clip-path="url(#boardClip)">
            <rect x="220" y="224" width="292" height="292" fill="{CREAM}"/>
            <rect x="512" y="224" width="292" height="292" fill="#20778b"/>
            <rect x="220" y="516" width="292" height="292" fill="#114a57"/>
            <rect x="512" y="516" width="292" height="292" fill="{BROWN}"/>
            <path d="M512 224 V808 M220 516 H804" stroke="#15262b" stroke-opacity="0.3" stroke-width="5"/>
          </g>
          <polygon points="504,348 636,254 737,346 668,421 582,396" fill="{AMBER}" stroke="{ORANGE}" stroke-width="5"/>
          <g filter="url(#softShadow)" fill="none" stroke-linecap="round" stroke-linejoin="round">
            <path d="M274 680 H410 V478 L592 330 H684" stroke="#0d2330" stroke-width="100"/>
            <path d="M274 680 H410 V478 L592 330 H684" stroke="{IVORY}" stroke-width="70"/>
            <path d="M292 680 H410 V492 L592 346 H662" stroke="#d7f7f9" stroke-opacity="0.58" stroke-width="18"/>
          </g>
          <g>
            <circle cx="274" cy="680" r="79" fill="#041016" opacity="0.59"/>
            <circle cx="274" cy="680" r="67" fill="{IVORY}"/>
            <circle cx="274" cy="680" r="38" fill="#12495b"/>
            <circle cx="410" cy="478" r="60" fill="#041016" opacity="0.59"/>
            <circle cx="410" cy="478" r="48" fill="{IVORY}"/>
            <circle cx="410" cy="478" r="27" fill="{AMBER}"/>
            <circle cx="684" cy="330" r="84" fill="#041016" opacity="0.59"/>
            <circle cx="684" cy="330" r="72" fill="{IVORY}"/>
            <circle cx="684" cy="330" r="40" fill="#145a75"/>
          </g>
          <path d="M226 736 A356 356 0 0 1 784 296" fill="none" stroke="#fff" stroke-opacity="0.07" stroke-width="5" stroke-linecap="round"/>
        </svg>
        """
    )
    SVG_PATH.write_text(svg, encoding="utf-8")


def main() -> None:
    """Generate PNG, ICO, and SVG route-knight logo assets."""
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
