#!/usr/bin/env python3
import argparse
import json
import os
import sys
import time
from pathlib import Path
from typing import Any, Iterable, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def load_system_prompt(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    stripped = text.lstrip()
    if stripped.startswith("SYSTEM:"):
        return stripped[len("SYSTEM:") :].lstrip()
    return text


def extract_tags(record: Any) -> Tuple[list[str] | None, dict[str, Any] | None]:
    if isinstance(record, list):
        return [str(x) for x in record], None
    if isinstance(record, dict):
        if isinstance(record.get("tags"), list):
            return [str(x) for x in record["tags"]], record
        if isinstance(record.get("tag_lines"), list):
            return [str(x) for x in record["tag_lines"]], record
    return None, record if isinstance(record, dict) else None


def format_input(tags: list[str] | None, record: dict[str, Any] | None, raw: str) -> str:
    if tags is None:
        return raw
    move_san = None
    if record:
        move_san = record.get("move_san") or record.get("moveSAN") or record.get("move")
    lines = [
        "TASK: puzzle_commentary",
        f"MOVE_SAN: {move_san if move_san else 'null'}",
        "TAGS:",
    ]
    for tag in tags:
        lines.append(f"- {tag}")
    delta = record.get("delta") if record else None
    if isinstance(delta, dict):
        added = delta.get("added")
        removed = delta.get("removed")
        changed = delta.get("changed")
        if added:
            lines.append(f"added: {json.dumps(added, ensure_ascii=False)}")
        if removed:
            lines.append(f"removed: {json.dumps(removed, ensure_ascii=False)}")
        if changed:
            lines.append(f"changed: {json.dumps(changed, ensure_ascii=False)}")
    return "\n".join(lines)


def iter_inputs(path: Path) -> Iterable[Tuple[int, str, str]]:
    with path.open("r", encoding="utf-8") as f:
        for idx, line in enumerate(f, start=1):
            raw = line.strip()
            if not raw:
                continue
            try:
                record = json.loads(raw)
            except json.JSONDecodeError:
                record = None
            tags, meta = extract_tags(record) if record is not None else (None, None)
            yield idx, raw, format_input(tags, meta, raw)


def post_request(endpoint: str, api_key: str, payload: dict[str, Any]) -> dict[str, Any]:
    data = json.dumps(payload).encode("utf-8")
    req = Request(endpoint, data=data, headers={"api-key": api_key, "Content-Type": "application/json"})
    with urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def extract_output(response: dict[str, Any]) -> str:
    parts: list[str] = []
    for item in response.get("output", []):
        for content in item.get("content", []):
            if content.get("type") == "output_text":
                parts.append(content.get("text", ""))
    return "".join(parts).strip()


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate tag-to-text commentary via Azure OpenAI Responses API.")
    parser.add_argument("--input", required=True, help="Input JSONL (tags) or plain text lines")
    parser.add_argument("--output", required=True, help="Output JSONL path")
    parser.add_argument("--prompt", default="tag/README-chatgpt.md", help="System prompt file")
    parser.add_argument("--raw", action="store_true", help="Treat input file as a single raw prompt")
    parser.add_argument("--endpoint", default=os.getenv("AZURE_OPENAI_ENDPOINT"), help="Azure Responses endpoint URL")
    parser.add_argument("--api-key", default=os.getenv("AZURE_OPENAI_API_KEY"), help="Azure API key")
    parser.add_argument("--model", default=os.getenv("AZURE_OPENAI_MODEL", "gpt-5-mini"), help="Azure model name")
    parser.add_argument("--temperature", type=float, default=0.3, help="Sampling temperature")
    parser.add_argument("--max-output-tokens", type=int, default=800, help="Max output tokens")
    parser.add_argument("--sleep-ms", type=int, default=0, help="Sleep between requests (ms)")
    parser.add_argument("--limit", type=int, default=0, help="Process only the first N records (0 = no limit)")
    args = parser.parse_args()

    if not args.endpoint or not args.api_key:
        raise SystemExit("AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_API_KEY must be set (or passed via flags).")

    prompt = load_system_prompt(Path(args.prompt))
    input_path = Path(args.input)
    output_path = Path(args.output)

    processed = 0
    with output_path.open("w", encoding="utf-8") as out:
        if args.raw:
            raw_text = input_path.read_text(encoding="utf-8")
            payload = {
                "model": args.model,
                "temperature": args.temperature,
                "max_output_tokens": args.max_output_tokens,
                "input": [
                    {"role": "system", "content": [{"type": "input_text", "text": prompt}]},
                    {"role": "user", "content": [{"type": "input_text", "text": raw_text}]},
                ],
            }
            try:
                response = post_request(args.endpoint, args.api_key, payload)
            except (HTTPError, URLError) as exc:
                raise SystemExit(f"Request failed: {exc}") from exc
            output_text = extract_output(response)
            out.write(json.dumps({"input": raw_text, "output": output_text}, ensure_ascii=False) + "\n")
            return
        for idx, raw, formatted in iter_inputs(input_path):
            if args.limit and processed >= args.limit:
                break
            payload = {
                "model": args.model,
                "temperature": args.temperature,
                "max_output_tokens": args.max_output_tokens,
                "input": [
                    {"role": "system", "content": [{"type": "input_text", "text": prompt}]},
                    {"role": "user", "content": [{"type": "input_text", "text": formatted}]},
                ],
            }
            try:
                response = post_request(args.endpoint, args.api_key, payload)
            except (HTTPError, URLError) as exc:
                raise SystemExit(f"Request failed at line {idx}: {exc}") from exc
            output_text = extract_output(response)
            out.write(json.dumps({"input": formatted, "output": output_text}, ensure_ascii=False) + "\n")
            processed += 1
            if args.sleep_ms:
                time.sleep(args.sleep_ms / 1000.0)


if __name__ == "__main__":
    main()
