#!/usr/bin/env python3
"""Generate the ChessRTK GitHub social preview banner.

The output is intentionally deterministic: it uses the checked-in app icon,
system fonts when available, and direct Pillow drawing. GitHub recommends a
1280x640 social preview, so this script writes that exact size.
"""

from __future__ import annotations

import math
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "assets" / "banner" / "github" / "crtk-github-banner.png"
APP_ICON = ROOT / "assets" / "logo" / "app" / "crtk-chemical-board.png"

WIDTH = 1280
HEIGHT = 640

INK = (5, 48, 64, 255)
MUTED = (57, 99, 115, 255)
TEAL = (19, 132, 156, 255)
TEAL_DARK = (10, 91, 111, 255)
TEAL_SOFT = (209, 238, 242, 255)
GOLD = (218, 166, 32, 255)
CARD = (255, 255, 255, 232)
BORDER = (186, 219, 224, 255)


def font(paths: list[str], size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    """Load the first available font from a list of absolute paths."""
    for path in paths:
        if Path(path).exists():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


DISPLAY_BOLD = font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansDisplay-Bold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ],
    78,
)
DISPLAY_SEMIBOLD = font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansDisplay-SemiBold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-SemiBold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ],
    33,
)
BODY_FONT = font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansDisplay-Regular.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ],
    25,
)
LABEL_FONT = font(
    [
        "/usr/share/fonts/truetype/noto/NotoSans-SemiBold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ],
    21,
)
MONO_FONT = font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    ],
    19,
)
MONO_SMALL = font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    ],
    15,
)


def lerp(a: int, b: int, t: float) -> int:
    return round(a + (b - a) * t)


def rounded_shadow(
    canvas: Image.Image,
    box: tuple[int, int, int, int],
    radius: int,
    blur: int = 22,
    offset: tuple[int, int] = (0, 10),
    alpha: int = 42,
) -> None:
    """Paint a soft shadow for a rounded rectangle."""
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    shifted = (
        box[0] + offset[0],
        box[1] + offset[1],
        box[2] + offset[0],
        box[3] + offset[1],
    )
    draw.rounded_rectangle(shifted, radius=radius, fill=(5, 48, 64, alpha))
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))


def rounded_card(
    canvas: Image.Image,
    box: tuple[int, int, int, int],
    radius: int = 26,
    fill: tuple[int, int, int, int] = CARD,
    outline: tuple[int, int, int, int] = BORDER,
) -> ImageDraw.ImageDraw:
    """Draw a soft card and return a fresh drawing context."""
    rounded_shadow(canvas, box, radius)
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=1)
    return draw


def draw_background(canvas: Image.Image) -> None:
    """Draw a quiet light background with a subtle teal emphasis."""
    pixels = canvas.load()
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        base = (
            lerp(250, 229, t),
            lerp(253, 246, t),
            lerp(253, 247, t),
            255,
        )
        for x in range(WIDTH):
            dx = (x - 1010) / 520
            dy = (y - 205) / 420
            glow = max(0.0, 1.0 - math.sqrt(dx * dx + dy * dy))
            warm = max(0.0, 1.0 - math.sqrt(((x - 160) / 430) ** 2 + ((y - 545) / 320) ** 2))
            r = min(255, round(base[0] + glow * 12 + warm * 8))
            g = min(255, round(base[1] + glow * 10 + warm * 5))
            b = min(255, round(base[2] + glow * 4 - warm * 12))
            pixels[x, y] = (r, g, b, 255)

    draw = ImageDraw.Draw(canvas, "RGBA")
    draw.rounded_rectangle((1120, -80, 1350, 190), radius=76, fill=(216, 239, 243, 130))
    draw.line((64, 56, 1216, 56), fill=(172, 211, 218, 120), width=1)
    draw.line((64, 584, 1216, 584), fill=(172, 211, 218, 95), width=1)


def draw_text_block(canvas: Image.Image) -> None:
    """Draw the left-side product copy."""
    draw = ImageDraw.Draw(canvas)
    x = 74
    draw.text((x, 134), "ChessRTK", font=DISPLAY_BOLD, fill=INK)
    draw.text((x + 3, 226), "Chess programming toolkit", font=DISPLAY_SEMIBOLD, fill=TEAL_DARK)

    body = (
        "Deterministic legal move generation, validation, perft testing, "
        "FEN/PGN/SAN workflows, UCI engine analysis, Chess960, datasets, "
        "SVG boards, and PDF publishing."
    )
    y = 282
    for line in textwrap.wrap(body, width=61):
        draw.text((x + 3, y), line, font=BODY_FONT, fill=MUTED)
        y += 36

    pills = ["movegen", "perft", "uci", "fen / pgn / san", "chess960"]
    px, py = x + 2, 442
    for label in pills:
        bbox = draw.textbbox((0, 0), label, font=LABEL_FONT)
        width = bbox[2] - bbox[0] + 32
        if px + width > 600:
            px = x + 2
            py += 54
        draw.rounded_rectangle((px, py, px + width, py + 38), radius=19, fill=(255, 255, 255, 216), outline=(192, 222, 226, 255), width=1)
        draw.text((px + 16, py + 6), label, font=LABEL_FONT, fill=TEAL_DARK)
        px += width + 13


def paste_app_icon(canvas: Image.Image) -> None:
    """Place the current app icon without a separate platform card."""
    if not APP_ICON.exists():
        return
    icon = Image.open(APP_ICON).convert("RGBA")
    icon.thumbnail((292, 292), Image.Resampling.LANCZOS)
    x, y = 816, 91

    shadow = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    shadow.alpha_composite(icon)
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    tinted = Image.new("RGBA", shadow.size, (5, 48, 64, 54))
    tinted.putalpha(shadow.getchannel("A").point(lambda a: min(54, a // 4)))
    canvas.alpha_composite(tinted, (x + 4, y + 15))
    canvas.alpha_composite(icon, (x, y))


def draw_metric_card(canvas: Image.Image) -> None:
    """Draw a compact perft/readout card."""
    box = (704, 360, 912, 500)
    draw = rounded_card(canvas, box, radius=24)
    draw.text((730, 390), "perft depth 6", font=LABEL_FONT, fill=TEAL_DARK)
    draw.text((730, 425), "119,060,324", font=font([
        "/usr/share/fonts/truetype/noto/NotoSansDisplay-SemiBold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-SemiBold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ], 29), fill=INK)
    draw.text((732, 465), "deterministic nodes", font=MONO_SMALL, fill=MUTED)
    draw.rounded_rectangle((862, 389, 893, 420), radius=10, fill=TEAL_SOFT, outline=(151, 206, 214, 255))
    draw.line((869, 405, 876, 412, 887, 396), fill=TEAL_DARK, width=4, joint="curve")


def draw_cli_card(canvas: Image.Image) -> None:
    """Draw a dark CLI card to signal programming and automation."""
    box = (920, 404, 1192, 536)
    rounded_shadow(canvas, box, radius=22, blur=24, offset=(0, 12), alpha=50)
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(box, radius=22, fill=(7, 62, 78, 244), outline=(38, 139, 160, 255), width=1)
    draw.ellipse((948, 430, 958, 440), fill=(235, 179, 39, 255))
    draw.ellipse((968, 430, 978, 440), fill=(93, 182, 197, 255))
    draw.ellipse((988, 430, 998, 440), fill=(173, 219, 225, 255))
    lines = [
        "$ crtk move list",
        "e2e4 g1f3 d2d4 c2c4",
        "$ crtk engine perft",
    ]
    y = 459
    for i, line in enumerate(lines):
        color = (230, 246, 248, 255) if i != 1 else (160, 219, 227, 255)
        draw.text((950, y), line, font=MONO_FONT, fill=color)
        y += 25


def draw_board_card(canvas: Image.Image) -> None:
    """Draw one small board card with the same teal/gold accent language."""
    box = (1016, 226, 1198, 360)
    draw = rounded_card(canvas, box, radius=22, fill=(255, 255, 255, 226))
    left, top, size = 1042, 247, 86
    square = size // 8
    for row in range(8):
        for col in range(8):
            fill = (239, 248, 249, 255) if (row + col) % 2 == 0 else (157, 203, 211, 255)
            draw.rectangle((left + col * square, top + row * square, left + (col + 1) * square, top + (row + 1) * square), fill=fill)

    highlights = [(4, 4), (3, 4), (6, 6), (5, 5)]
    for row, col in highlights:
        draw.rectangle((left + col * square, top + row * square, left + (col + 1) * square, top + (row + 1) * square), fill=(229, 180, 47, 130))
    draw.line((left + 6 * square + 5, top + 6 * square + 5, left + 4 * square + 5, top + 4 * square + 5), fill=TEAL_DARK, width=4)
    draw.ellipse((left + 6 * square + 1, top + 6 * square + 1, left + 6 * square + 10, top + 6 * square + 10), fill=TEAL_DARK)
    draw.polygon(
        [
            (left + 4 * square + 2, top + 4 * square + 3),
            (left + 4 * square + 13, top + 4 * square + 7),
            (left + 4 * square + 6, top + 4 * square + 15),
        ],
        fill=TEAL_DARK,
    )
    draw.rounded_rectangle((left - 1, top - 1, left + size + 1, top + size + 1), radius=7, outline=(73, 129, 140, 255), width=2)
    draw.text((1138, 254), "FEN", font=LABEL_FONT, fill=TEAL_DARK)
    draw.text((1138, 286), "SAN", font=LABEL_FONT, fill=TEAL_DARK)
    draw.text((1138, 318), "UCI", font=LABEL_FONT, fill=TEAL_DARK)


def main() -> None:
    canvas = Image.new("RGBA", (WIDTH, HEIGHT), (255, 255, 255, 255))
    draw_background(canvas)
    draw_text_block(canvas)
    paste_app_icon(canvas)
    draw_board_card(canvas)
    draw_metric_card(canvas)
    draw_cli_card(canvas)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUT, "PNG", optimize=True)
    print(f"wrote {OUT.relative_to(ROOT)} ({WIDTH}x{HEIGHT})")


if __name__ == "__main__":
    main()
