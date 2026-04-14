#!/usr/bin/env python3
"""Generate WhiteSur-inspired piece logos (PNG + ICO)."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter


def vertical_gradient(size: int, top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    grad = Image.new("RGBA", (size, size))
    px = grad.load()
    den = max(1, size - 1)
    for y in range(size):
        t = y / den
        r = int(round(top[0] * (1.0 - t) + bottom[0] * t))
        g = int(round(top[1] * (1.0 - t) + bottom[1] * t))
        b = int(round(top[2] * (1.0 - t) + bottom[2] * t))
        for x in range(size):
            px[x, y] = (r, g, b, 255)
    return grad


def rounded_rect_mask(size: int, radius: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask


def trim_alpha(img: Image.Image) -> Image.Image:
    alpha = img.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        return img
    return img.crop(bbox)


def desaturate_rgba(src: Image.Image) -> Image.Image:
    src = src.convert("RGBA")
    alpha = src.getchannel("A")
    rgb = src.convert("RGB")
    rgb = ImageEnhance.Color(rgb).enhance(0.0)
    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    return out


def style_piece_texture(src: Image.Image, piece_is_white: bool) -> Image.Image:
    src = src.convert("RGBA")
    alpha = src.getchannel("A")
    rgb = desaturate_rgba(src).convert("RGB")
    if piece_is_white:
        rgb = ImageEnhance.Contrast(rgb).enhance(1.10)
        rgb = ImageEnhance.Brightness(rgb).enhance(0.96)
    else:
        rgb = ImageEnhance.Contrast(rgb).enhance(1.02)
        rgb = ImageEnhance.Brightness(rgb).enhance(0.94)
    piece = rgb.convert("RGBA")
    piece.putalpha(alpha)
    return piece


def is_white_piece(piece_path: Path) -> bool:
    return piece_path.stem.startswith("white-")


def load_piece_art(piece_path: Path, render_size: int) -> Image.Image:
    if piece_path.suffix.lower() != ".svg":
        return Image.open(piece_path).convert("RGBA")

    inkscape = shutil.which("inkscape")
    if inkscape is None:
        raise RuntimeError("SVG piece logo generation requires inkscape on PATH")

    with tempfile.TemporaryDirectory() as tmp:
        rendered = Path(tmp) / "piece.png"
        subprocess.run(
            [
                inkscape,
                str(piece_path),
                "--export-type=png",
                f"--export-filename={rendered}",
                "--export-width",
                str(render_size),
                "--export-height",
                str(render_size),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        with Image.open(rendered) as image:
            return image.convert("RGBA")


def draw_logo(piece_path: Path, size: int, aa_scale: int) -> Image.Image:
    work_size = size * aa_scale
    canvas = Image.new("RGBA", (work_size, work_size), (0, 0, 0, 0))
    piece_is_white = is_white_piece(piece_path)

    tile_size = int(round(work_size * 0.86))
    tile_x = (work_size - tile_size) // 2
    tile_y = (work_size - tile_size) // 2 - int(round(work_size * 0.01))
    radius = int(round(tile_size * 0.23))

    if piece_is_white:
        tile_top = (52, 52, 52)
        tile_bottom = (10, 10, 10)
        border_color = (168, 168, 168, 90)
        highlight_alpha_peak = 10
        shadow_fill = 190
        symbol_shadow_strength = 0.52
    else:
        tile_top = (252, 252, 252)
        tile_bottom = (196, 196, 196)
        border_color = (120, 120, 120, 110)
        highlight_alpha_peak = 72
        shadow_fill = 150
        symbol_shadow_strength = 0.25

    tile_mask = rounded_rect_mask(tile_size, radius)
    tile = vertical_gradient(tile_size, tile_top, tile_bottom)
    tile.putalpha(tile_mask)

    shadow = Image.new("RGBA", (work_size, work_size), (0, 0, 0, 0))
    shadow_mask = Image.new("L", (work_size, work_size), 0)
    draw_shadow = ImageDraw.Draw(shadow_mask)
    shadow_offset_y = int(round(work_size * 0.02))
    draw_shadow.rounded_rectangle(
        (tile_x, tile_y + shadow_offset_y, tile_x + tile_size - 1, tile_y + tile_size - 1 + shadow_offset_y),
        radius=radius,
        fill=shadow_fill,
    )
    shadow_alpha = shadow_mask.filter(ImageFilter.GaussianBlur(radius=max(1, int(round(work_size * 0.03)))))
    shadow.putalpha(shadow_alpha)
    canvas.alpha_composite(shadow)

    canvas.alpha_composite(tile, (tile_x, tile_y))

    highlight = Image.new("RGBA", (tile_size, tile_size), (255, 255, 255, 0))
    hpx = highlight.load()
    den = max(1, tile_size - 1)
    for y in range(tile_size):
        t = y / den
        a = int(round(highlight_alpha_peak * (1.0 - min(1.0, t * 1.6))))
        for x in range(tile_size):
            hpx[x, y] = (255, 255, 255, a)
    highlight.putalpha(ImageChops.multiply(highlight.getchannel("A"), tile_mask))
    canvas.alpha_composite(highlight, (tile_x, tile_y))

    border = Image.new("RGBA", (tile_size, tile_size), (0, 0, 0, 0))
    db = ImageDraw.Draw(border)
    border_w = max(1, int(round(work_size * 0.0035)))
    db.rounded_rectangle(
        (border_w // 2, border_w // 2, tile_size - 1 - border_w // 2, tile_size - 1 - border_w // 2),
        radius=max(1, radius - border_w // 2),
        outline=border_color,
        width=border_w,
    )
    border.putalpha(ImageChops.multiply(border.getchannel("A"), tile_mask))
    canvas.alpha_composite(border, (tile_x, tile_y))

    piece = load_piece_art(piece_path, work_size)
    piece = trim_alpha(piece)
    piece = style_piece_texture(piece, piece_is_white)

    symbol_h = int(round(tile_size * 0.60))
    ratio = piece.width / piece.height
    symbol_w = int(round(symbol_h * ratio))
    piece = piece.resize((symbol_w, symbol_h), Image.Resampling.LANCZOS)

    sym_x = tile_x + (tile_size - symbol_w) // 2
    sym_y = tile_y + (tile_size - symbol_h) // 2 + int(round(tile_size * 0.01))

    sym_shadow = Image.new("RGBA", (work_size, work_size), (0, 0, 0, 0))
    sym_shadow_layer = Image.new("RGBA", piece.size, (0, 0, 0, 0))
    sym_shadow_layer.putalpha(piece.getchannel("A"))
    sym_shadow_layer = sym_shadow_layer.filter(ImageFilter.GaussianBlur(radius=max(1, int(round(work_size * 0.006)))))
    sym_shadow.alpha_composite(sym_shadow_layer, (sym_x, sym_y + int(round(work_size * 0.007))))
    sym_shadow = Image.blend(Image.new("RGBA", (work_size, work_size), (0, 0, 0, 0)), sym_shadow, symbol_shadow_strength)
    canvas.alpha_composite(sym_shadow)

    canvas.alpha_composite(piece, (sym_x, sym_y))
    if aa_scale > 1:
        canvas = canvas.resize((size, size), Image.Resampling.LANCZOS)
    canvas = desaturate_rgba(canvas)
    return canvas


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate WhiteSur-inspired CRTK piece logos (PNG + ICO).")
    parser.add_argument(
        "--piece",
        type=Path,
        default=Path("assets/embedded/pieces/black-rook.svg"),
        help="Source piece SVG or PNG for single render mode.",
    )
    parser.add_argument(
        "--pieces-dir",
        type=Path,
        default=Path("assets/embedded/pieces"),
        help="Directory containing black-* and white-* SVG or PNG piece files.",
    )
    parser.add_argument(
        "--all-pieces",
        action="store_true",
        help="Render all piece files from --pieces-dir.",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("assets/logo/pieces"),
        help="Output directory.",
    )
    parser.add_argument(
        "--name",
        default="crtk-rook",
        help="Base filename for single render mode (without extension).",
    )
    parser.add_argument(
        "--prefix",
        default="crtk",
        help="Filename prefix in --all-pieces mode. Final name: <prefix>-<piece-stem>.",
    )
    parser.add_argument(
        "--size",
        type=int,
        default=512,
        help="Output PNG size (square).",
    )
    parser.add_argument(
        "--aa-scale",
        type=int,
        default=4,
        help="Supersampling factor for antialiasing (1 disables supersampling).",
    )
    return parser.parse_args()


def write_outputs(image: Image.Image, out_dir: Path, base_name: str) -> None:
    png_path = out_dir / f"{base_name}.png"
    ico_path = out_dir / f"{base_name}.ico"
    image.save(png_path, format="PNG")
    ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    image.save(ico_path, format="ICO", sizes=ico_sizes)
    print(f"wrote {png_path}")
    print(f"wrote {ico_path}")


def main() -> int:
    args = parse_args()
    if args.size < 128:
        raise ValueError("--size must be >= 128")
    if args.aa_scale < 1:
        raise ValueError("--aa-scale must be >= 1")

    args.out_dir.mkdir(parents=True, exist_ok=True)

    if args.all_pieces:
        pieces = sorted(args.pieces_dir.glob("*.svg"))
        if not pieces:
            pieces = sorted(args.pieces_dir.glob("*.png"))
        pieces = [p for p in pieces if p.stem.startswith("black-") or p.stem.startswith("white-")]
        if not pieces:
            raise FileNotFoundError(f"No piece SVGs or PNGs found in: {args.pieces_dir}")
        for piece in pieces:
            logo = draw_logo(piece, args.size, args.aa_scale)
            write_outputs(logo, args.out_dir, f"{args.prefix}-{piece.stem}")
    else:
        logo = draw_logo(args.piece, args.size, args.aa_scale)
        write_outputs(logo, args.out_dir, args.name)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
