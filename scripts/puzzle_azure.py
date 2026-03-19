#!/usr/bin/env python3
import argparse
import json
import os
import re
import subprocess
from pathlib import Path
from typing import List


def run_cmd(cmd: List[str]) -> str:
    return subprocess.check_output(cmd, text=True)


def compute_word_target(tags: List[str]) -> int:
    pv_lines = [t for t in tags if t.startswith("PV: ")]
    pv_plies = 0
    variation_max_plies = 0
    for pv in pv_lines:
        tokens = pv[len("PV: ") :].strip().split()
        pv_plies = max(pv_plies, len(tokens))
        variation_max_plies = max(variation_max_plies, len(tokens))
    variation_count = len(pv_lines)
    threat_count = sum(1 for t in tags if t.startswith("THREAT: "))
    tactic_count = sum(1 for t in tags if t.startswith("TACTIC: "))

    base = 170
    pv_bonus = max(0, pv_plies - 8) * 6
    var_bonus = max(0, variation_count - 1) * 35
    var_len_bonus = max(0, variation_max_plies - 8) * 3
    tactic_bonus = tactic_count * 8 + threat_count * 10
    word_target = base + pv_bonus + var_bonus + var_len_bonus + tactic_bonus
    return max(140, min(520, word_target))


def ensure_meta(tags: List[str], key: str, value) -> None:
    prefix = f"META: {key}="
    for i, t in enumerate(tags):
        if t.startswith(prefix):
            tags[i] = f"META: {key}={value}"
            return
    tags.append(f"META: {key}={value}")


def parse_tags(raw: str) -> List[str]:
    raw = raw.strip()
    if not raw:
        return []
    if raw.startswith("[\"") and raw.endswith("\"]"):
        inner = raw[2:-2]
        if not inner:
            return []
        return inner.split("\",\"")
    if raw.startswith("["):
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            pass

    tags: List[str] = []
    in_str = False
    buf: List[str] = []
    i = 0
    while i < len(raw):
        ch = raw[i]
        if not in_str:
            if ch == '"':
                in_str = True
                buf = []
            i += 1
            continue
        # in string
        if ch == '"':
            nxt = raw[i + 1] if i + 1 < len(raw) else ""
            nxt2 = raw[i + 2] if i + 2 < len(raw) else ""
            if nxt == '"' and nxt2 in {",", "]"}:
                # Internal quote right before string end: keep it, then next quote ends the string.
                buf.append('"')
                i += 1
            elif nxt in {",", "]"}:
                tags.append("".join(buf))
                in_str = False
            else:
                buf.append('"')
            i += 1
            continue
        buf.append(ch)
        i += 1
    return tags


def parse_pv_lines(output: str, limit: int | None) -> List[str]:
    pvs: dict[int, List[str]] = {}
    current = None
    for line in output.splitlines():
        m = re.match(r"^PV(\d+)$", line.strip())
        if m:
            current = int(m.group(1))
            continue
        if current is not None:
            stripped = line.strip()
            if stripped.startswith("line:"):
                pv = stripped[len("line:") :].strip()
                tokens = pv.split()
                if limit is not None and limit > 0:
                    tokens = tokens[:limit]
                if tokens:
                    pvs[current] = tokens
                current = None
    return [f"PV: {' '.join(pvs[i])}" for i in sorted(pvs.keys())]


def main() -> None:
    parser = argparse.ArgumentParser(description="Mine puzzle tags + deltas and generate Azure text.")
    parser.add_argument("--fen", required=True, help="Root FEN")
    parser.add_argument("--out-dir", default="/tmp", help="Output directory")
    parser.add_argument("--multipv", type=int, default=3, help="Analysis multipv")
    parser.add_argument("--pv-plies", type=int, default=40, help="Max PV plies for puzzle-tags")
    parser.add_argument("--max-nodes", type=int, default=10000000, help="Max nodes per analysis")
    parser.add_argument("--max-duration", default="10s", help="Max duration per analysis")
    parser.add_argument("--crtk", default="crtk", help="crtk executable")
    parser.add_argument("--azure-script", default="scripts/azure_tag_text.py", help="Azure tag-to-text script")
    parser.add_argument("--prompt", default="tag/README-chatgpt.md", help="System prompt file")
    parser.add_argument("--temperature", type=float, default=0.3, help="Azure temperature")
    parser.add_argument("--max-output-tokens", type=int, default=1400, help="Azure max output tokens")
    args = parser.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    delta_path = out_dir / "puzzle.tags.delta.jsonl"
    root_tags_path = out_dir / "puzzle.root.tags.jsonl"
    enriched_path = out_dir / "puzzle.root.enriched.jsonl"
    azure_out_path = out_dir / "puzzle.azure.jsonl"

    # 1) Mine puzzle line with deltas (for T5 training)
    cmd = [
        args.crtk,
        "puzzle-tags",
        "--fen",
        args.fen,
        "--multipv",
        str(args.multipv),
        "--pv-plies",
        str(args.pv_plies),
        "--max-nodes",
        str(args.max_nodes),
        "--max-duration",
        args.max_duration,
    ]
    with delta_path.open("w", encoding="utf-8") as f:
        subprocess.check_call(cmd, stdout=f)

    # 2) Root tags with PVs for overall puzzle summary
    root_tags = run_cmd(
        [
            args.crtk,
            "tags",
            "--fen",
            args.fen,
            "--analyze",
            "--multipv",
            str(args.multipv),
            "--max-nodes",
            str(args.max_nodes),
            "--max-duration",
            args.max_duration,
        ]
    ).strip()
    root_tags_path.write_text(root_tags + "\n", encoding="utf-8")

    tags_list = parse_tags(root_tags)

    # Replace PV tags with full PVs from analysis (for richer summaries).
    analysis_output = run_cmd(
        [
            args.crtk,
            "analyze",
            "--fen",
            args.fen,
            "--multipv",
            str(args.multipv),
            "--max-nodes",
            str(args.max_nodes),
            "--max-duration",
            args.max_duration,
        ]
    )
    pv_tags = parse_pv_lines(analysis_output, args.pv_plies)
    tags_list = [t for t in tags_list if not t.startswith("PV: ")]
    tags_list.extend(pv_tags)
    word_target = compute_word_target(tags_list)
    ensure_meta(tags_list, "pv_plies", max(0, max(len(t[len("PV: ") :].split()) for t in tags_list if t.startswith("PV: ")) if any(t.startswith("PV: ") for t in tags_list) else 0))
    ensure_meta(tags_list, "variation_count", sum(1 for t in tags_list if t.startswith("PV: ")))
    ensure_meta(tags_list, "variation_max_plies", max(0, max(len(t[len("PV: ") :].split()) for t in tags_list if t.startswith("PV: ")) if any(t.startswith("PV: ") for t in tags_list) else 0))
    ensure_meta(tags_list, "threat_count", sum(1 for t in tags_list if t.startswith("THREAT: ")))
    ensure_meta(tags_list, "tactic_count", sum(1 for t in tags_list if t.startswith("TACTIC: ")))
    ensure_meta(tags_list, "word_target", word_target)
    ensure_meta(tags_list, "length_hint", "long")

    enriched_path.write_text(json.dumps({"tags": tags_list}, ensure_ascii=False) + "\n", encoding="utf-8")

    # 3) Azure tag-to-text for the root position summary
    azure_env = os.environ.copy()
    if not azure_env.get("AZURE_OPENAI_ENDPOINT") or not azure_env.get("AZURE_OPENAI_API_KEY"):
        raise SystemExit("AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_API_KEY must be set.")
    cmd = [
        "python3",
        args.azure_script,
        "--input",
        str(enriched_path),
        "--output",
        str(azure_out_path),
        "--temperature",
        str(args.temperature),
        "--max-output-tokens",
        str(args.max_output_tokens),
        "--prompt",
        args.prompt,
    ]
    subprocess.check_call(cmd, env=azure_env)

    print(azure_out_path.read_text(encoding="utf-8").strip())


if __name__ == "__main__":
    main()
