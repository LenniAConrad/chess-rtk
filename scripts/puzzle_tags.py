#!/usr/bin/env python3
import argparse
import re
import subprocess
from pathlib import Path
from typing import Dict, List


def run_analyze(crtk: str, fen: str, multipv: int, max_nodes: int, max_duration: str, protocol: str | None,
                threads: int | None, hash_mb: int | None, wdl: bool | None) -> str:
    cmd = [crtk, "analyze", "--fen", fen, "--multipv", str(multipv)]
    if max_nodes:
        cmd += ["--max-nodes", str(max_nodes)]
    if max_duration:
        cmd += ["--max-duration", max_duration]
    if protocol:
        cmd += ["--protocol", protocol]
    if threads:
        cmd += ["--threads", str(threads)]
    if hash_mb:
        cmd += ["--hash", str(hash_mb)]
    if wdl is True:
        cmd += ["--wdl"]
    elif wdl is False:
        cmd += ["--no-wdl"]
    return subprocess.check_output(cmd, text=True)


def parse_pv_lines(output: str, limit: int | None) -> Dict[int, List[str]]:
    pvs: Dict[int, List[str]] = {}
    current = None
    for line in output.splitlines():
        m = re.match(r"^PV(\d+)$", line.strip())
        if m:
            current = int(m.group(1))
            continue
        if current is not None:
            stripped = line.strip()
            if stripped.startswith("line:"):
                pv = stripped[len("line:"):].strip()
                tokens = pv.split()
                if limit is not None:
                    tokens = tokens[:limit]
                if tokens:
                    pvs[current] = tokens
                current = None
    return pvs


def parse_fen_meta(fen: str) -> tuple[bool, int]:
    parts = fen.split()
    if len(parts) < 6:
        raise ValueError("Invalid FEN")
    white_to_move = parts[1].lower() == "w"
    fullmove = int(parts[5])
    return white_to_move, fullmove


def format_pgn(tokens: List[str], white_to_move: bool, fullmove: int) -> str:
    if not tokens:
        return ""
    move_no = fullmove
    side = white_to_move
    out: List[str] = []
    for san in tokens:
        if side:
            out.append(f"{move_no}. {san}")
        else:
            if not out:
                out.append(f"{move_no}... {san}")
            else:
                out.append(san)
            move_no += 1
        side = not side
    return " ".join(out)


def build_pgn(fen: str, pvs: Dict[int, List[str]]) -> str:
    if 1 not in pvs:
        raise ValueError("PV1 missing from analysis output")
    white_to_move, fullmove = parse_fen_meta(fen)
    main = format_pgn(pvs[1], white_to_move, fullmove)
    variations = []
    for pv_idx in sorted(k for k in pvs.keys() if k != 1):
        line = format_pgn(pvs[pv_idx], white_to_move, fullmove)
        if line:
            variations.append(f"({line})")
    header = [
        '[Event "Puzzle PVs"]',
        '[Site "?"]',
        '[Date "????.??.??"]',
        '[Round "?"]',
        '[White "?"]',
        '[Black "?"]',
        '[SetUp "1"]',
        f'[FEN "{fen}"]',
        '',
    ]
    body = main
    if variations:
        body += " " + " ".join(variations)
    body += " *"
    return "\n".join(header) + body + "\n"


def run_tags(crtk: str, pgn_path: Path, output_path: Path, analyze: bool, max_nodes: int,
             max_duration: str, protocol: str | None, threads: int | None, hash_mb: int | None, wdl: bool | None,
             multipv: int | None) -> None:
    cmd = [crtk, "tags", "--pgn", str(pgn_path), "--sidelines", "--delta"]
    if analyze:
        cmd.append("--analyze")
    if max_nodes:
        cmd += ["--max-nodes", str(max_nodes)]
    if max_duration:
        cmd += ["--max-duration", max_duration]
    if protocol:
        cmd += ["--protocol", protocol]
    if threads:
        cmd += ["--threads", str(threads)]
    if hash_mb:
        cmd += ["--hash", str(hash_mb)]
    if wdl is True:
        cmd += ["--wdl"]
    elif wdl is False:
        cmd += ["--no-wdl"]
    if multipv:
        cmd += ["--multipv", str(multipv)]
    with output_path.open("w", encoding="utf-8") as f:
        subprocess.check_call(cmd, stdout=f)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate per-move tags for a puzzle with PV variations.")
    parser.add_argument("--fen", required=True, help="Root FEN")
    parser.add_argument("--output", required=True, help="Output JSONL path")
    parser.add_argument("--multipv", type=int, default=3, help="Number of PVs to extract")
    parser.add_argument("--pv-plies", type=int, default=12, help="Max PV plies to include")
    parser.add_argument("--max-nodes", type=int, default=2500000, help="Max nodes per analysis")
    parser.add_argument("--max-duration", default="2s", help="Max duration per analysis (e.g. 1s, 2s)")
    parser.add_argument("--protocol", default=None, help="Engine protocol TOML path")
    parser.add_argument("--threads", type=int, default=None, help="Engine threads")
    parser.add_argument("--hash", dest="hash_mb", type=int, default=None, help="Engine hash (MB)")
    parser.add_argument("--wdl", action="store_true", help="Enable WDL if supported")
    parser.add_argument("--no-wdl", action="store_true", help="Disable WDL if supported")
    parser.add_argument("--no-analyze", action="store_true", help="Skip analysis during tag generation")
    parser.add_argument("--tag-multipv", type=int, default=1, help="Multipv for tag analysis")
    parser.add_argument("--keep-pgn", action="store_true", help="Keep generated PGN next to output")
    parser.add_argument("--crtk", default="crtk", help="crtk executable (default: crtk)")
    args = parser.parse_args()

    if args.wdl and args.no_wdl:
        parser.error("Use only one of --wdl or --no-wdl")

    analysis_output = run_analyze(
        args.crtk,
        args.fen,
        args.multipv,
        args.max_nodes,
        args.max_duration,
        args.protocol,
        args.threads,
        args.hash_mb,
        True if args.wdl else False if args.no_wdl else None,
    )

    pvs = parse_pv_lines(analysis_output, args.pv_plies)
    if 1 not in pvs:
        raise SystemExit("PV1 missing; analysis did not return a main line")

    pgn_text = build_pgn(args.fen, pvs)
    output_path = Path(args.output)
    pgn_path = output_path.with_suffix(".pgn")
    pgn_path.write_text(pgn_text, encoding="utf-8")

    run_tags(
        args.crtk,
        pgn_path,
        output_path,
        analyze=not args.no_analyze,
        max_nodes=args.max_nodes,
        max_duration=args.max_duration,
        protocol=args.protocol,
        threads=args.threads,
        hash_mb=args.hash_mb,
        wdl=True if args.wdl else False if args.no_wdl else None,
        multipv=args.tag_multipv,
    )

    if not args.keep_pgn:
        try:
            pgn_path.unlink()
        except OSError:
            pass


if __name__ == "__main__":
    main()
