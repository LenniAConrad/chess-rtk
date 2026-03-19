#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
from pathlib import Path
from typing import Iterable, List

# Local imports from analysis_pipeline
from analysis_pipeline import build_sequence_prompt, build_deterministic_story


def iter_fens_from_jsonl(path: Path) -> Iterable[str]:
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            raw = line.strip()
            if not raw:
                continue
            try:
                obj = json.loads(raw)
            except json.JSONDecodeError:
                continue
            fen = obj.get("fen") or obj.get("FEN") or obj.get("position")
            if fen:
                yield fen


def iter_fens_from_text(path: Path) -> Iterable[str]:
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            fen = line.strip()
            if fen:
                yield fen


def run_puzzle_tags(crtk: str, fen: str, multipv: int, pv_plies: int, max_nodes: int, max_duration: str) -> str:
    cmd = [
        crtk,
        "puzzle-tags",
        "--fen",
        fen,
        "--multipv",
        str(multipv),
        "--pv-plies",
        str(pv_plies),
        "--max-nodes",
        str(max_nodes),
        "--max-duration",
        max_duration,
    ]
    return subprocess.check_output(cmd, text=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build T5 dataset from puzzle FENs using deterministic NLG.")
    parser.add_argument("--input", required=True, help="Input file (JSONL puzzle dump or plain FEN list)")
    parser.add_argument("--output", required=True, help="Output JSONL dataset path")
    parser.add_argument("--format", choices=["jsonl", "fen"], default="jsonl", help="Input format")
    parser.add_argument("--limit", type=int, default=0, help="Process only first N fens")
    parser.add_argument("--variants", type=int, default=3, help="Variants per puzzle")
    parser.add_argument("--seed", type=int, default=1, help="Base seed")
    parser.add_argument("--style", choices=["gm", "classic", "energetic"], default="gm")
    parser.add_argument("--multipv", type=int, default=3)
    parser.add_argument("--pv-plies", type=int, default=24)
    parser.add_argument("--max-nodes", type=int, default=1000000)
    parser.add_argument("--max-duration", default="2s")
    parser.add_argument("--crtk", default="crtk")
    parser.add_argument("--append", action="store_true")
    args = parser.parse_args()

    input_path = Path(args.input)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    if args.format == "jsonl":
        fen_iter = iter_fens_from_jsonl(input_path)
    else:
        fen_iter = iter_fens_from_text(input_path)

    mode = "a" if args.append else "w"
    count = 0
    with output_path.open(mode, encoding="utf-8") as out:
        for fen in fen_iter:
            if args.limit and count >= args.limit:
                break
            try:
                delta_text = run_puzzle_tags(
                    args.crtk, fen, args.multipv, args.pv_plies, args.max_nodes, args.max_duration
                )
            except subprocess.CalledProcessError as exc:
                print(f"skip fen (engine error): {fen[:30]}... -> {exc}")
                continue

            # write delta temp file
            tmp_delta = Path("/tmp/puzzle_delta.jsonl")
            tmp_delta.write_text(delta_text, encoding="utf-8")

            # build input prompt and deterministic output
            start_side = fen.split()[1] if len(fen.split()) > 1 else "w"
            start_side = "white" if start_side.lower().startswith("w") else "black"
            prompt = build_sequence_prompt(tmp_delta, start_side)

            for i in range(args.variants):
                seed = args.seed + i
                story = build_deterministic_story(tmp_delta, start_side, seed=seed, style=args.style)
                record = {
                    "fen": fen,
                    "input": prompt,
                    "output": story,
                    "variant": i + 1,
                    "seed": seed,
                    "style": args.style,
                }
                out.write(json.dumps(record, ensure_ascii=False) + "\n")

            count += 1


if __name__ == "__main__":
    main()
