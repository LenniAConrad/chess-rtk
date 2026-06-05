#!/usr/bin/env python3
"""Generate the ChessRTK GitHub social preview banner."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "assets" / "banner" / "github" / "crtk-github-banner.png"
APP_ICON = ROOT / "assets" / "logo" / "app" / "crtk-chemical-board.png"

WIDTH = 1280
HEIGHT = 640

INK = (5, 48, 64, 255)
TEAL = (11, 92, 112, 255)
MUTED = (57, 98, 114, 255)
LINE = (182, 218, 224, 255)
LIGHT = (250, 253, 253, 255)
GOLD = (219, 167, 32, 255)


def load_font(paths: list[str], size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    """Load the first available font."""
    for path in paths:
        if Path(path).exists():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


TITLE = load_font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansDisplay-Bold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ],
    76,
)
SUBTITLE = load_font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansDisplay-SemiBold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-SemiBold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ],
    32,
)
BODY = load_font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansDisplay-Regular.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ],
    25,
)
MONO = load_font(
    [
        "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    ],
    21,
)


def draw_background(canvas: Image.Image) -> None:
    """Draw a plain light background with a very soft right-side tint."""
    pixels = canvas.load()
    for y in range(HEIGHT):
        for x in range(WIDTH):
            tint = max(0.0, 1.0 - (((x - 1040) / 560) ** 2 + ((y - 250) / 430) ** 2) ** 0.5)
            r = round(250 - tint * 8)
            g = round(253 - tint * 2)
            b = round(253 + tint * 1)
            pixels[x, y] = (r, g, min(255, b), 255)

    draw = ImageDraw.Draw(canvas, "RGBA")
    draw.line((72, 72, 1208, 72), fill=LINE, width=1)
    draw.line((72, 568, 1208, 568), fill=LINE, width=1)


def draw_text(canvas: Image.Image) -> None:
    """Draw the left-side project copy."""
    draw = ImageDraw.Draw(canvas)
    x = 86
    draw.text((x, 169), "ChessRTK", font=TITLE, fill=INK)
    draw.text((x + 2, 260), "Deterministic Chess Programming Toolkit", font=SUBTITLE, fill=TEAL)
    draw.text(
        (x + 2, 316),
        "Move generation, validation, perft, FEN/PGN/SAN, UCI,",
        font=BODY,
        fill=MUTED,
    )
    draw.text(
        (x + 2, 352),
        "Chess960, datasets, SVG boards, and PDF publishing.",
        font=BODY,
        fill=MUTED,
    )
    draw.text((x + 2, 443), "crtk <area> <action> [options]", font=MONO, fill=TEAL)


def draw_board_accent(canvas: Image.Image) -> None:
    """Draw one subtle board accent behind the app icon."""
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer, "RGBA")
    left, top, square = 838, 142, 44
    for row in range(8):
        for col in range(8):
            fill = (239, 248, 249, 124) if (row + col) % 2 == 0 else (155, 204, 212, 116)
            draw.rectangle(
                (
                    left + col * square,
                    top + row * square,
                    left + (col + 1) * square,
                    top + (row + 1) * square,
                ),
                fill=fill,
            )
    draw.rounded_rectangle(
        (left, top, left + square * 8, top + square * 8),
        radius=20,
        outline=(18, 126, 150, 96),
        width=2,
    )
    draw.line((left + 2 * square, top + 5 * square, left + 5 * square, top + 2 * square), fill=GOLD, width=6)
    draw.ellipse(
        (left + 5 * square - 8, top + 2 * square - 8, left + 5 * square + 8, top + 2 * square + 8),
        fill=GOLD,
    )
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(0.15)))


def paste_icon(canvas: Image.Image) -> None:
    """Place the app icon cleanly over the board accent."""
    if not APP_ICON.exists():
        return
    icon = Image.open(APP_ICON).convert("RGBA")
    icon.thumbnail((290, 290), Image.Resampling.LANCZOS)
    x, y = 882, 177

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow.alpha_composite(icon, (x + 6, y + 18))
    shadow = shadow.filter(ImageFilter.GaussianBlur(20))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(icon, (x, y))


def main() -> None:
    canvas = Image.new("RGBA", (WIDTH, HEIGHT), LIGHT)
    draw_background(canvas)
    draw_text(canvas)
    draw_board_accent(canvas)
    paste_icon(canvas)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUT, "PNG", optimize=True)
    print(f"wrote {OUT.relative_to(ROOT)} ({WIDTH}x{HEIGHT})")


if __name__ == "__main__":
    main()
