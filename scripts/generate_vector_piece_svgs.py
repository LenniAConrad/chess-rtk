#!/usr/bin/env python3
"""Trace embedded chess piece PNGs into flat SVGs using Potrace."""

from __future__ import annotations

import argparse
import math
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image


PIECE_NAMES = (
    "black-bishop",
    "black-king",
    "black-knight",
    "black-pawn",
    "black-queen",
    "black-rook",
    "white-bishop",
    "white-king",
    "white-knight",
    "white-pawn",
    "white-queen",
    "white-rook",
)

SVG_NS = {"svg": "http://www.w3.org/2000/svg"}


@dataclass(frozen=True)
class TraceResult:
    """Measured result for one traced piece."""

    source: Path
    output: Path
    similarity: float
    alpha_iou: float
    opttolerance: float
    path_count: int
    bytes_written: int


def write_pgm_mask(mask: np.ndarray, path: Path) -> None:
    """Write a binary mask as a PGM file with black foreground for Potrace."""

    pixels = np.where(mask, 0, 255).astype(np.uint8)
    Image.fromarray(pixels, mode="L").save(path)


def collapse_ws(value: str) -> str:
    """Collapse whitespace in generated SVG path data."""

    return " ".join(value.split())


def potrace_paths(
    potrace: str,
    mask: np.ndarray,
    temp_dir: Path,
    stem: str,
    opttolerance: float,
    turdsize: int,
    alphamax: float,
    unit: int,
) -> tuple[str, list[str]]:
    """Trace a binary mask with Potrace and return its transform plus paths."""

    if not np.any(mask):
        return "", []

    mask_path = temp_dir / f"{stem}.pgm"
    svg_path = temp_dir / f"{stem}.svg"
    write_pgm_mask(mask, mask_path)
    subprocess.run(
        [
            potrace,
            str(mask_path),
            "--svg",
            "--flat",
            "--width",
            "200pt",
            "--height",
            "200pt",
            "--margin",
            "0",
            "--turdsize",
            str(turdsize),
            "--alphamax",
            str(alphamax),
            "--opttolerance",
            str(opttolerance),
            "--unit",
            str(unit),
            "--output",
            str(svg_path),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=True,
    )

    tree = ET.parse(svg_path)
    group = tree.find(".//svg:g", SVG_NS)
    if group is None:
        return "", []

    transform = collapse_ws(group.attrib.get("transform", ""))
    paths = [
        collapse_ws(path.attrib["d"])
        for path in group.findall("svg:path", SVG_NS)
        if path.attrib.get("d")
    ]
    return transform, paths


def symmetrize_mask(mask: np.ndarray) -> np.ndarray:
    """Return a horizontally symmetric mask with averaged left/right runs."""

    height, width = mask.shape
    result = np.zeros_like(mask)
    for y in range(height):
        runs = row_runs(mask[y])
        for index in range((len(runs) + 1) // 2):
            left_start, left_end = runs[index]
            right_start, right_end = runs[-index - 1]
            mirrored_right_start = width - right_end
            mirrored_right_end = width - right_start
            average_start = (left_start + mirrored_right_start) / 2.0
            average_end = (left_end + mirrored_right_end) / 2.0
            paint_interval(result[y], average_start, average_end)
            paint_interval(result[y], width - average_end, width - average_start)

    return np.logical_or(result, np.fliplr(result))


def row_runs(row: np.ndarray) -> list[tuple[int, int]]:
    """Return start/end-exclusive runs of true values in one mask row."""

    if not np.any(row):
        return []

    padded = np.concatenate(([False], row, [False]))
    changes = np.diff(padded.astype(np.int8))
    starts = np.flatnonzero(changes == 1)
    ends = np.flatnonzero(changes == -1)
    return list(zip(starts.tolist(), ends.tolist()))


def paint_interval(row: np.ndarray, start: float, end: float) -> None:
    """Paint a continuous horizontal interval into a binary row."""

    width = row.shape[0]
    start_i = max(0, min(width, int(math.floor(start + 0.5))))
    end_i = max(0, min(width, int(math.floor(end + 0.5))))
    if end_i <= start_i and end > start:
        midpoint = max(0, min(width - 1, int(math.floor((start + end) / 2.0))))
        row[midpoint] = True
        return

    row[start_i:end_i] = True


def traced_group(transform: str, paths: list[str], fill: str) -> tuple[list[str], int]:
    """Build SVG group markup for traced paths."""

    if not paths:
        return [], 0

    parts = [f'    <g transform="{transform}" fill="{fill}" stroke="none">']
    parts.extend(f'      <path d="{path}"/>' for path in paths)
    parts.append("    </g>")
    return parts, len(paths)


def traced_svg(
    source_png: Path,
    temp_dir: Path,
    potrace: str,
    opttolerance: float,
    alpha_threshold: int,
    frame_threshold: int,
    black_fill: str,
    white_fill: str,
    frame_fill: str,
    turdsize: int,
    alphamax: float,
    unit: int,
) -> tuple[str, int]:
    """Build a flat two-tone SVG: pure frame plus flat infill."""

    color, piece = source_png.stem.split("-", 1)
    geometry_png = source_png.with_name(f"white-{piece}.png")
    image = np.array(Image.open(geometry_png).convert("RGBA"))
    alpha = image[..., 3]
    gray = image[..., 0]
    silhouette = alpha >= alpha_threshold
    if not np.any(silhouette):
        raise ValueError(f"No visible pixels found in {source_png}")

    infill = silhouette & (gray > frame_threshold)
    symmetrize_piece = piece != "knight"
    if symmetrize_piece:
        silhouette = symmetrize_mask(silhouette)
        infill = symmetrize_mask(infill) & silhouette

    infill_color = white_fill if color == "white" else black_fill
    title = source_png.stem.replace("-", " ").title()

    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" '
        'role="img" aria-labelledby="title">',
        f'  <title id="title">{title}</title>',
        '  <g shape-rendering="geometricPrecision">',
    ]

    path_count = 0
    transform, paths = potrace_paths(
        potrace,
        silhouette,
        temp_dir,
        f"{source_png.stem}-frame",
        opttolerance,
        turdsize,
        alphamax,
        unit,
    )
    elements, count = traced_group(transform, paths, frame_fill)
    parts.extend(elements)
    path_count += count

    transform, paths = potrace_paths(
        potrace,
        infill,
        temp_dir,
        f"{source_png.stem}-infill",
        opttolerance,
        turdsize,
        alphamax,
        unit,
    )
    elements, count = traced_group(transform, paths, infill_color)
    parts.extend(elements)
    path_count += count

    parts.extend(["  </g>", "</svg>", ""])
    return "\n".join(parts), path_count


def render_svg(inkscape: str, svg_path: Path, png_path: Path, size: int) -> None:
    """Render an SVG to a PNG using Inkscape."""

    subprocess.run(
        [
            inkscape,
            str(svg_path),
            "--export-type=png",
            f"--export-filename={png_path}",
            "--export-width",
            str(size),
            "--export-height",
            str(size),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=True,
    )


def premultiply_rgba(image: np.ndarray) -> np.ndarray:
    """Return a floating-point premultiplied RGBA array."""

    data = image.astype(float)
    data[..., :3] *= data[..., 3:4] / 255.0
    return data


def similarity_score(source_png: Path, rendered_png: Path) -> tuple[float, float]:
    """Score rendered output against source inside the source foreground bbox."""

    source_image = Image.open(source_png).convert("RGBA")
    rendered_image = Image.open(rendered_png).convert("RGBA")
    bbox = source_image.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError(f"No alpha bounding box found in {source_png}")

    source = premultiply_rgba(np.array(source_image))
    rendered = premultiply_rgba(np.array(rendered_image))
    x0, y0, x1, y1 = bbox
    source_crop = source[y0:y1, x0:x1]
    rendered_crop = rendered[y0:y1, x0:x1]
    mae = np.mean(np.abs(source_crop - rendered_crop))
    similarity = 1.0 - (mae / 255.0)

    source_alpha = source_crop[..., 3] > 32
    rendered_alpha = rendered_crop[..., 3] > 32
    union = np.logical_or(source_alpha, rendered_alpha)
    if not np.any(union):
        alpha_iou = 1.0
    else:
        alpha_iou = float(np.logical_and(source_alpha, rendered_alpha).sum() / union.sum())

    return float(similarity), alpha_iou


def trace_and_score(
    source_png: Path,
    output_svg: Path,
    temp_dir: Path,
    inkscape: str,
    potrace: str,
    opttolerance: float,
    alpha_threshold: int,
    frame_threshold: int,
    black_fill: str,
    white_fill: str,
    frame_fill: str,
    turdsize: int,
    alphamax: float,
    unit: int,
    size: int,
) -> tuple[TraceResult, str]:
    """Trace one source PNG to SVG, render it, and return measured similarity."""

    svg_text, path_count = traced_svg(
        source_png,
        temp_dir,
        potrace,
        opttolerance,
        alpha_threshold,
        frame_threshold,
        black_fill,
        white_fill,
        frame_fill,
        turdsize,
        alphamax,
        unit,
    )
    candidate_svg = temp_dir / f"{source_png.stem}.svg"
    candidate_png = temp_dir / f"{source_png.stem}.png"
    candidate_svg.write_text(svg_text, encoding="utf-8")
    render_svg(inkscape, candidate_svg, candidate_png, size)
    similarity, alpha_iou = similarity_score(source_png, candidate_png)
    result = TraceResult(
        source=source_png,
        output=output_svg,
        similarity=similarity,
        alpha_iou=alpha_iou,
        opttolerance=opttolerance,
        path_count=path_count,
        bytes_written=len(svg_text.encode("utf-8")),
    )
    return result, svg_text


def source_files(source_dir: Path) -> list[Path]:
    """Return the expected source PNG files in stable order."""

    files = [source_dir / f"{name}.png" for name in PIECE_NAMES]
    missing = [str(path) for path in files if not path.is_file()]
    if missing:
        raise FileNotFoundError("Missing source PNGs: " + ", ".join(missing))
    return files


def validate_shared_alpha_shapes(source_dir: Path) -> None:
    """Confirm black and white piece PNGs share the same source silhouette."""

    for piece in ("bishop", "king", "knight", "pawn", "queen", "rook"):
        black_alpha = np.array(Image.open(source_dir / f"black-{piece}.png").convert("RGBA"))[..., 3]
        white_alpha = np.array(Image.open(source_dir / f"white-{piece}.png").convert("RGBA"))[..., 3]
        if not np.array_equal(black_alpha, white_alpha):
            raise ValueError(f"black-{piece}.png and white-{piece}.png do not share the same alpha shape")


def parse_float_list(raw: str) -> list[float]:
    """Parse comma-separated float values."""

    values = [float(item.strip()) for item in raw.split(",") if item.strip()]
    if not values:
        raise ValueError("At least one value must be provided")
    return values


def generate(args: argparse.Namespace) -> list[TraceResult]:
    """Trace all piece PNGs, tightening Potrace optimization until the target passes."""

    inkscape = shutil.which("inkscape")
    if inkscape is None:
        raise RuntimeError("Inkscape is required to render and score traced SVGs")
    potrace = shutil.which("potrace")
    if potrace is None:
        raise RuntimeError("Potrace is required to trace piece masks")

    sources = source_files(args.source_dir)
    validate_shared_alpha_shapes(args.source_dir)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    opttolerances = parse_float_list(args.opttolerances)

    with tempfile.TemporaryDirectory() as raw_temp_dir:
        temp_dir = Path(raw_temp_dir)
        for opttolerance in opttolerances:
            candidates = [
                trace_and_score(
                    source_png=source,
                    output_svg=args.out_dir / f"{source.stem}.svg",
                    temp_dir=temp_dir,
                    inkscape=inkscape,
                    potrace=potrace,
                    opttolerance=opttolerance,
                    alpha_threshold=args.alpha_threshold,
                    frame_threshold=args.frame_threshold,
                    black_fill=args.black_fill,
                    white_fill=args.white_fill,
                    frame_fill=args.frame_fill,
                    turdsize=args.turdsize,
                    alphamax=args.alphamax,
                    unit=args.unit,
                    size=args.size,
                )
                for source in sources
            ]
            results = [result for result, _svg_text in candidates]
            minimum = min(result.similarity for result in results)
            print(f"opttolerance {opttolerance:g}: min similarity {minimum * 100:.2f}%")
            if minimum >= args.target:
                for result, svg_text in candidates:
                    result.output.write_text(svg_text, encoding="utf-8")
                return results

    raise RuntimeError(
        f"No opttolerance reached {args.target * 100:.2f}% minimum similarity; "
        f"last minimum was {minimum * 100:.2f}%"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=Path("assets/embedded/pieces/png"),
        help="Directory containing the original black-*.png and white-*.png files.",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("assets/embedded/pieces/svg"),
        help="Destination directory for traced SVG files.",
    )
    parser.add_argument(
        "--target",
        type=float,
        default=0.967,
        help="Minimum required similarity score, expressed from 0.0 to 1.0.",
    )
    parser.add_argument(
        "--alpha-threshold",
        type=int,
        default=160,
        help="Alpha threshold used for tracing visible source pixels.",
    )
    parser.add_argument(
        "--opttolerances",
        default="1.5,1,0.5,0.2,0.1,0.05",
        help="Comma-separated Potrace --opttolerance values to try from smoothest to strictest.",
    )
    parser.add_argument(
        "--turdsize",
        type=int,
        default=0,
        help="Potrace --turdsize value. Zero preserves the small source details.",
    )
    parser.add_argument(
        "--alphamax",
        type=float,
        default=1.0,
        help="Potrace --alphamax curve/corner threshold.",
    )
    parser.add_argument(
        "--unit",
        type=int,
        default=10,
        help="Potrace --unit quantization value.",
    )
    parser.add_argument(
        "--frame-threshold",
        type=int,
        default=240,
        help="White-source gray threshold separating pure black frame from flat infill.",
    )
    parser.add_argument(
        "--frame-fill",
        default="#000000",
        help="Frame/detail fill color.",
    )
    parser.add_argument(
        "--white-fill",
        default="#ffffff",
        help="Flat white-piece infill color.",
    )
    parser.add_argument(
        "--black-fill",
        default="#4c4c4c",
        help="Flat black-piece infill color.",
    )
    parser.add_argument(
        "--size",
        type=int,
        default=200,
        help="Pixel size used only for scoring rendered SVGs against source PNGs.",
    )
    args = parser.parse_args()
    if args.unit < 1:
        parser.error("--unit must be at least 1")
    return args


def main() -> int:
    results = generate(parse_args())
    print("piece,similarity,alpha_iou,opttolerance,paths,svg_bytes")
    for result in results:
        print(
            f"{result.source.stem},"
            f"{result.similarity * 100:.2f}%,"
            f"{result.alpha_iou * 100:.2f}%,"
            f"{result.opttolerance:g},"
            f"{result.path_count},"
            f"{result.bytes_written}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
