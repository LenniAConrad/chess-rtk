#!/usr/bin/env python3
"""Export embedded chess piece PNG byte arrays from Java source files."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

BYTE_ARRAY_PATTERN = re.compile(
    r"protected\s+static\s+final\s+byte\[\]\s+Bytes\s*=\s*\{(.*?)\};",
    re.DOTALL,
)
INT_PATTERN = re.compile(r"-?\d+")
CLASS_NAME_PATTERN = re.compile(r"^(White|Black)(Bishop|King|Knight|Pawn|Queen|Rook)$")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def parse_java_byte_array(java_file: Path) -> bytes:
    text = java_file.read_text(encoding="utf-8")
    match = BYTE_ARRAY_PATTERN.search(text)
    if match is None:
        raise ValueError(f"No embedded byte array found in {java_file}")

    ints = [int(token) for token in INT_PATTERN.findall(match.group(1))]
    if not ints:
        raise ValueError(f"Embedded byte array in {java_file} is empty")

    return bytes(value & 0xFF for value in ints)


def class_file_to_png_name(class_file: Path) -> str:
    stem = class_file.stem
    if not stem.startswith("Byte"):
        raise ValueError(f"Unexpected source file name: {class_file.name}")

    raw = stem[4:]
    match = CLASS_NAME_PATTERN.fullmatch(raw)
    if match is None:
        # Fall back to a stable lowercase name if naming diverges in the future.
        return f"{raw.lower()}.png"

    color, piece = match.groups()
    return f"{color.lower()}-{piece.lower()}.png"


def export_piece_pngs(source_dir: Path, output_dir: Path) -> int:
    sources = sorted(source_dir.glob("Byte*.java"))
    if not sources:
        print(f"No piece byte source files found in: {source_dir}", file=sys.stderr)
        return 1

    output_dir.mkdir(parents=True, exist_ok=True)

    errors = 0
    for source in sources:
        try:
            blob = parse_java_byte_array(source)
            out_name = class_file_to_png_name(source)
            out_path = output_dir / out_name
            out_path.write_bytes(blob)

            if not blob.startswith(PNG_SIGNATURE):
                print(f"warning: {source.name} did not decode to a PNG signature", file=sys.stderr)

            print(f"wrote {out_path}")
        except Exception as exc:  # noqa: BLE001
            errors += 1
            print(f"error: failed to export {source}: {exc}", file=sys.stderr)

    return 1 if errors else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export embedded chess piece image bytes into PNG files."
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=Path("src/chess/images/assets/piece"),
        help="Directory containing Byte*.java piece source files.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("assets/embedded/pieces"),
        help="Destination directory for exported PNG files.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    return export_piece_pngs(args.source_dir, args.output_dir)


if __name__ == "__main__":
    raise SystemExit(main())
