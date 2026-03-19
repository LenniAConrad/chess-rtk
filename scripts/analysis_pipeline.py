#!/usr/bin/env python3
import argparse
import random
import json
import os
import re
import subprocess
from pathlib import Path
from typing import List


def run_cmd(cmd: List[str]) -> str:
    return subprocess.check_output(cmd, text=True)


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
    return []


def normalize_move_quotes(tags: List[str]) -> List[str]:
    out: List[str] = []
    for t in tags:
        out.append(re.sub(r'move=\"([^\"]+)\"', r"move=\1", t))
    return out


def parse_pv_lines(output: str, limit: int | None) -> List[str]:
    pvs: dict[int, List[str]] = {}
    current = None
    in_line = False
    for line in output.splitlines():
        m = re.match(r"^PV(\d+)$", line.strip())
        if m:
            current = int(m.group(1))
            in_line = False
            continue
        if current is None:
            continue
        stripped = line.strip()
        if stripped.startswith("line:"):
            pv = stripped[len("line:") :].strip()
            tokens = pv.split()
            if tokens:
                pvs[current] = tokens
            in_line = True
            continue
        if in_line and (line.startswith(" ") or line.startswith("\\t")):
            extra = stripped.split()
            if extra:
                pvs[current].extend(extra)
            continue
        if in_line:
            current = None
            in_line = False
    if limit is not None and limit > 0:
        for k in list(pvs.keys()):
            pvs[k] = pvs[k][:limit]
    return [f"PV: {' '.join(pvs[i])}" for i in sorted(pvs.keys())]


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


def write_jsonl(path: Path, tags: List[str]) -> None:
    path.write_text(json.dumps({"tags": tags}, ensure_ascii=False) + "\n", encoding="utf-8")


def build_sequence_prompt(delta_path: Path, start_side: str) -> str:
    def find_balanced(text: str, start: int, open_ch: str, close_ch: str) -> int:
        depth = 0
        for i in range(start, len(text)):
            ch = text[i]
            if ch == open_ch:
                depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0:
                    return i
        return -1

    def extract_array(text: str, key: str) -> str:
        idx = text.find(f"\"{key}\":")
        if idx < 0:
            return "[]"
        start = text.find("[", idx)
        if start < 0:
            return "[]"
        end = find_balanced(text, start, "[", "]")
        if end < 0:
            return "[]"
        return text[start : end + 1]

    def extract_tags(text: str) -> List[str]:
        idx = text.find("\"tags\":")
        if idx < 0:
            return []
        start = text.find("[", idx)
        end = find_balanced(text, start, "[", "]")
        if start < 0 or end < 0:
            return []
        raw = text[start : end + 1]
        if raw.startswith("[\"") and raw.endswith("\"]"):
            inner = raw[2:-2]
            if not inner:
                return []
            return inner.split("\",\"")
        return []

    def extract_move_san(text: str) -> str:
        m = re.search(r"\"move_san\":\"([^\"]*)\"", text)
        return m.group(1) if m else "null"

    def tag_key(tag: str) -> str | None:
        m = re.match(r"^KING: safety=(\\w+) side=(white|black)$", tag)
        if m:
            return f"KING:safety:{m.group(2)}"
        m = re.match(r"^KING: shelter=(\\w+) side=(white|black)$", tag)
        if m:
            return f"KING:shelter:{m.group(2)}"
        m = re.match(r"^MATERIAL: balance=(\\S+)$", tag)
        if m:
            return "MATERIAL:balance"
        m = re.match(r"^INITIATIVE: side=(\\w+)$", tag)
        if m:
            return "INITIATIVE"
        m = re.match(r"^MOBILITY: side=(\\w+)$", tag)
        if m:
            return "MOBILITY"
        m = re.match(r"^SPACE: side=(\\w+)$", tag)
        if m:
            return "SPACE"
        m = re.match(r"^FACT: status=(\\w+)$", tag)
        if m:
            return "FACT:status"
        m = re.match(r"^FACT: in_check=(\\w+)$", tag)
        if m:
            return "FACT:in_check"
        return None

    def sanitize(tag: str) -> str:
        return re.sub(r'move=\"([^\"]+)\"', r"move=\1", tag)

    def trim_pv(tag: str, max_plies: int = 6) -> str:
        if not tag.startswith("PV: "):
            return tag
        moves = tag[len("PV: ") :].split()
        if len(moves) <= max_plies:
            return tag
        return "PV: " + " ".join(moves[:max_plies])

    def pretty_material(balance: str) -> str:
        mapping = {
            "equal": "Material is equal.",
            "white_up_minor": "White is up a minor piece.",
            "black_up_minor": "Black is up a minor piece.",
            "white_up_exchange": "White is up the exchange.",
            "black_up_exchange": "Black is up the exchange.",
            "white_up_queen": "White is up a queen.",
            "black_up_queen": "Black is up a queen.",
        }
        if balance in mapping:
            return mapping[balance]
        return f"Material balance favors {balance.replace('_', ' ')}."

    def pretty_eval(bucket: str) -> str:
        mapping = {
            "equal": "The position is roughly equal.",
            "slight_white": "White is slightly better.",
            "clear_white": "White is clearly better.",
            "winning_white": "White is winning.",
            "crushing_white": "White is winning decisively.",
            "slight_black": "Black is slightly better.",
            "clear_black": "Black is clearly better.",
            "winning_black": "Black is winning.",
            "crushing_black": "Black is winning decisively.",
        }
        return mapping.get(bucket, f"Evaluation: {bucket.replace('_', ' ')}.")

    def pretty_piece(tag: str) -> str:
        m = re.match(r"^PIECE: activity=(pinned|trapped) side=(white|black) piece=(\\w+) square=([a-h][1-8])$", tag)
        if m:
            activity = m.group(1)
            side = m.group(2)
            piece = m.group(3)
            square = m.group(4)
            return f"PIECE: {side.capitalize()} {piece} on {square} is {activity}."
        return tag

    def pretty_threat(tag: str) -> str:
        m = re.match(
            r"^THREAT: side=(white|black) severity=(\\w+) type=(\\w+)(?: move=([^ ]+))?(?: square=([a-h][1-8]))?$",
            tag,
        )
        if not m:
            return tag
        side = m.group(1)
        severity = m.group(2).replace("_", " ")
        ttype = m.group(3)
        move = m.group(4)
        square = m.group(5)
        if ttype == "mate":
            core = f"{side.capitalize()} threatens mate"
        elif ttype == "promote":
            core = f"{side.capitalize()} threatens promotion"
        elif ttype == "material":
            core = f"{side.capitalize()} threatens to win material"
        else:
            core = f"{side.capitalize()} threatens"
        if move:
            core += f" {move}"
        elif square:
            core += f" on {square}"
        return f"THREAT: {core}."

    def pretty_tag(tag: str) -> str:
        if tag.startswith("META: eval_bucket="):
            bucket = tag.split("=", 1)[1]
            return f"EVAL: {pretty_eval(bucket)}"
        if tag.startswith("MATERIAL: balance="):
            bal = tag.split("=", 1)[1]
            return f"MATERIAL: {pretty_material(bal)}"
        if tag.startswith("MATERIAL: imbalance="):
            imb = tag.split("=", 1)[1].replace("_", " ")
            return f"MATERIAL: Imbalance: {imb}."
        if tag.startswith("KING: safety="):
            m = re.match(r"^KING: safety=(\\w+) side=(white|black)$", tag)
            if m:
                return f"KING: {m.group(2).capitalize()} king is {m.group(1).replace('_', ' ')}."
        if tag.startswith("KING: shelter="):
            m = re.match(r"^KING: shelter=(\\w+) side=(white|black)$", tag)
            if m:
                return f"KING: {m.group(2).capitalize()} king shelter is {m.group(1).replace('_', ' ')}."
        if tag.startswith("FACT: in_check="):
            val = tag.split("=", 1)[1]
            if val == "black":
                return "FACT: Black is in check."
            if val == "white":
                return "FACT: White is in check."
            return "FACT: No check."
        if tag.startswith("FACT: status=check"):
            return "FACT: Check."
        if tag.startswith("THREAT:"):
            return pretty_threat(tag)
        if tag.startswith("PIECE:"):
            return pretty_piece(tag)
        if tag.startswith("PV: "):
            return trim_pv(tag)
        return tag

    def extract_changes(prev: List[str], curr: List[str]) -> tuple[List[str], List[str], List[str]]:
        prev_set = set(prev)
        curr_set = set(curr)
        added = [t for t in curr if t not in prev_set]
        removed = [t for t in prev if t not in curr_set]

        # Keyed changes
        prev_map = {}
        curr_map = {}
        for t in prev:
            k = tag_key(t)
            if k:
                prev_map[k] = t
        for t in curr:
            k = tag_key(t)
            if k:
                curr_map[k] = t
        changed = []
        for k, v in curr_map.items():
            if k in prev_map and prev_map[k] != v:
                changed.append(f"{k}: {prev_map[k]} -> {v}")

        def keep(tag: str) -> bool:
            if tag.startswith("MATERIAL:"):
                return tag.startswith("MATERIAL: balance=") or tag.startswith("MATERIAL: imbalance=")
            return tag.startswith(
                (
                    "THREAT:",
                    "TACTIC:",
                    "KING:",
                    "IDEA:",
                    "FACT: in_check=",
                    "PIECE: activity=pinned",
                    "PIECE: activity=trapped",
                )
            )

        def drop_no_check(tag: str) -> bool:
            return tag == "FACT: No check."

        def drop_castle(tag: str) -> bool:
            return "castled=yes" in tag or "castled=no" in tag

        added = [pretty_tag(trim_pv(sanitize(t))) for t in added if keep(t)]
        removed = [pretty_tag(trim_pv(sanitize(t))) for t in removed if keep(t)]
        changed = [pretty_tag(trim_pv(t)) for t in changed if "META:" not in t]
        added = [t for t in added if not drop_no_check(t) and not drop_castle(t)]
        removed = [t for t in removed if not drop_no_check(t) and not drop_castle(t)]
        changed = [t for t in changed if not drop_no_check(t) and not drop_castle(t)]

        return added[:4], removed[:3], changed[:3]

    records = []
    with delta_path.open("r", encoding="utf-8") as f:
        for line in f:
            raw = line.strip()
            if not raw:
                continue
            records.append(
                {
                    "move_san": extract_move_san(raw),
                    "tags": extract_tags(raw),
                }
            )
    move_count = len(records)
    side = start_side
    other = "black" if start_side == "white" else "white"
    pv_total = 0
    threat_total = 0
    for rec in records:
        for t in rec.get("tags") or []:
            if t.startswith("PV: "):
                pv_total += 1
            elif t.startswith("THREAT: "):
                threat_total += 1
    word_target = 200 + move_count * 18 + min(120, threat_total * 4)
    word_target = max(240, min(820, word_target))
    out: List[str] = [
        "TASK: puzzle_story",
        f"WORD_TARGET: {word_target}",
    ]
    keep_prefixes = (
        "THREAT:",
        "TACTIC:",
        "KING:",
        "MATERIAL:",
        "IDEA:",
        "FACT: in_check=",
        "META: eval_bucket",
        "PIECE: activity=pinned",
        "PIECE: activity=trapped",
    )
    prev_tags: List[str] = []
    for idx, rec in enumerate(records, start=1):
        out.append(f"RECORD {idx}")
        out.append(f"SIDE: {side}")
        move_san = rec.get("move_san") or "null"
        out.append(f"MOVE_SAN: {move_san}")
        tags = [sanitize(t) for t in (rec.get("tags") or [])]
        out.append("TAGS:")
        for t in tags:
            if not t.startswith(keep_prefixes):
                continue
            if t.startswith("META: eval_bucket") and idx not in (1, move_count):
                continue
            if t.startswith("MATERIAL:") and not (
                t.startswith("MATERIAL: balance=") or t.startswith("MATERIAL: imbalance=")
            ):
                continue
            if t.startswith("KING:") and not (
                t.startswith("KING: safety=") or t.startswith("KING: shelter=")
            ):
                continue
            if t.startswith("FACT: in_check=none"):
                continue
            out.append(f"- {pretty_tag(trim_pv(t))}")
        out.append("DELTA:")
        added, removed, changed = extract_changes(prev_tags, tags)
        out.append(f"added: {json.dumps(added, ensure_ascii=False)}")
        out.append(f"removed: {json.dumps(removed, ensure_ascii=False)}")
        out.append(f"changed: {json.dumps(changed, ensure_ascii=False)}")
        prev_tags = tags
        side = other if side == start_side else start_side
    return "\n".join(out)

def build_deterministic_story(delta_path: Path, start_side: str, seed: int = 1, style: str = "gm") -> str:
    rng = random.Random(seed)

    style = style.lower()
    if style not in {"gm", "classic", "energetic"}:
        style = "gm"

    verbs_white = {
        "gm": ["plays", "chooses", "continues with", "opts for", "goes for"],
        "classic": ["plays", "chooses", "continues with", "selects", "opts for"],
        "energetic": ["plays", "fires", "goes for", "jumps to", "pushes"],
    }[style]
    verbs_black = {
        "gm": ["responds with", "replies with", "answers with", "chooses", "plays"],
        "classic": ["responds with", "replies with", "answers with", "chooses", "plays"],
        "energetic": ["hits back with", "responds with", "answers with", "chooses", "plays"],
    }[style]
    check_added_phrases = {
        "gm": ["giving check", "with check", "checking the king"],
        "classic": ["giving check", "with check"],
        "energetic": ["with check", "checking the king"],
    }[style]
    check_removed_phrases = {
        "gm": ["stepping out of check", "escaping the check"],
        "classic": ["stepping out of check", "escaping the check"],
        "energetic": ["slipping out of check", "escaping the check"],
    }[style]
    def clean_clause(text: str) -> str:
        for prefix in ("THREAT: ", "KING: ", "MATERIAL: ", "FACT: ", "PIECE: ", "EVAL: ", "TACTIC: "):
            if text.startswith(prefix):
                text = text[len(prefix) :]
                break
        text = text.rstrip(".")
        # Tactic detail
        m = re.search(r'detail=\"([^\"]+)\"', text)
        if m:
            detail = m.group(1).strip()
            if detail:
                lower = detail.lower()
                if lower.startswith("hanging "):
                    parts = detail.split()
                    if len(parts) >= 4:
                        side = parts[1].capitalize()
                        piece = parts[2]
                        square = parts[3]
                        return f"{side} {piece} on {square} is hanging"
                if lower.startswith("pin:") or lower.startswith("skewer:") or lower.startswith("discovered attack:"):
                    return detail[0].upper() + detail[1:]
                return detail[0].upper() + detail[1:]
        # Fallback parsing for raw threat lines
        m = re.match(
            r"^(?:side=)?(white|black)\s+severity=(\w+)\s+type=(\w+)(?:\s+move=([^ ]+))?(?:\s+square=([a-h][1-8]))?$",
            text,
        )
        if m:
            side = m.group(1)
            ttype = m.group(3)
            move = m.group(4)
            square = m.group(5)
            if ttype == "mate":
                core = f"{side.capitalize()} threatens mate"
            elif ttype == "promote":
                core = f"{side.capitalize()} threatens promotion"
            elif ttype == "material":
                core = f"{side.capitalize()} threatens to win material"
            else:
                core = f"{side.capitalize()} threatens"
            if move:
                core += f" with {move}"
            elif square:
                core += f" on {square}"
            return core
        # Fallback parsing for raw king lines
        m = re.match(r"^(safety|shelter)=(\w+)\s+side=(white|black)$", text)
        if m:
            kind = m.group(1)
            val = m.group(2).replace("_", " ")
            side = m.group(3).capitalize()
            if kind == "safety":
                return f"{side}'s king is {val}"
            return f"{side}'s king shelter is {val}"
        # Fallback parsing for raw piece activity lines
        m = re.match(r"^activity=(pinned|trapped)\s+side=(white|black)\s+piece=(\w+)\s+square=([a-h][1-8])$", text)
        if m:
            activity = m.group(1)
            side = m.group(2).capitalize()
            piece = m.group(3)
            square = m.group(4)
            return f"{side} {piece} on {square} is {activity}"
        # Fallback parsing for raw material balance
        m = re.match(r"^balance=(\w+)$", text)
        if m:
            return pretty_material(m.group(1)).rstrip(".")
        # Tidy generic phrasing
        if "threatens with " in text and "threatens mate with" not in text:
            text = text.replace("threatens with ", "threatens ")
        return text

    def verb_for(side: str) -> str:
        verbs = verbs_white if side == "white" else verbs_black
        return rng.choice(verbs)

    def pick_clauses(added: list[str], removed: list[str], changed: list[str]) -> tuple[list[str], bool, bool]:
        clauses: list[str] = []

        def find(prefix: str, source: list[str]) -> list[str]:
            return [t for t in source if t.startswith(prefix)]

        check_added = False
        check_removed = False
        # Check (only when added)
        for t in added:
            if t in ("FACT: Black is in check.", "FACT: White is in check."):
                check_added = True
                break
        # Check removed
        for t in removed:
            if t in ("FACT: Black is in check.", "FACT: White is in check."):
                check_removed = True
                break

        # Threats (added then removed)
        threats_added = find("THREAT:", added)
        if threats_added:
            clauses.append(clean_clause(threats_added[0]))
        threats_removed = find("THREAT:", removed)
        if threats_removed and not threats_added:
            raw = threats_removed[0]
            if ("severity=immediate" in raw) or ("type=mate" in raw):
                tr = clean_clause(raw)
                if tr.startswith("White threatens"):
                    clauses.append("White's threat is neutralized")
                elif tr.startswith("Black threatens"):
                    clauses.append("Black's threat is neutralized")
                else:
                    clauses.append(f"{tr} is neutralized")

        # King safety changes
        king_added = find("KING:", added)
        if king_added:
            clauses.append(clean_clause(king_added[0]))

        # Key tactics
        tactic_added = find("TACTIC:", added)
        if tactic_added:
            clauses.append(clean_clause(tactic_added[0]))

        # Piece state (pinned/trapped)
        piece_added = find("PIECE:", added)
        if piece_added:
            clauses.append(clean_clause(piece_added[0]))

        # Fallback: changed info
        if not clauses and changed:
            clauses.append(clean_clause(changed[0]))

        return clauses, check_added, check_removed

    def normalize_clause(c: str) -> str:
        return re.sub(r"[\\s\\.,;:]+", " ", c.strip().lower())

    # Reuse parsing from build_sequence_prompt
    story_prompt = build_sequence_prompt(delta_path, start_side)
    lines = story_prompt.splitlines()
    records: list[dict[str, str | list[str]]] = []
    current: dict[str, str | list[str]] | None = None
    for line in lines:
        if line.startswith("RECORD "):
            if current:
                records.append(current)
            current = {"tags": [], "added": [], "removed": [], "changed": []}
        elif current is None:
            continue
        elif line.startswith("SIDE: "):
            current["side"] = line.split(": ", 1)[1]
        elif line.startswith("MOVE_SAN: "):
            current["move"] = line.split(": ", 1)[1]
        elif line.startswith("- "):
            current["tags"].append(line[2:])
        elif line.startswith("added: "):
            try:
                current["added"] = json.loads(line[len("added: ") :])
            except json.JSONDecodeError:
                current["added"] = []
        elif line.startswith("removed: "):
            try:
                current["removed"] = json.loads(line[len("removed: ") :])
            except json.JSONDecodeError:
                current["removed"] = []
        elif line.startswith("changed: "):
            try:
                current["changed"] = json.loads(line[len("changed: ") :])
            except json.JSONDecodeError:
                current["changed"] = []
    if current:
        records.append(current)

    if not records:
        return ""

    # Foundation from first record tags
    first = records[0]
    tags = list(first.get("tags", []))
    foundation_bits: list[tuple[str, str]] = []
    for t in tags:
        if t.startswith("EVAL:") or t.startswith("MATERIAL:") or t.startswith("KING:") or t.startswith("THREAT:"):
            kind = "EVAL" if t.startswith("EVAL:") else "MATERIAL" if t.startswith("MATERIAL:") else "OTHER"
            foundation_bits.append((kind, clean_clause(t)))
    foundation_sentence = ""
    if foundation_bits:
        eval_text = next((t for k, t in foundation_bits if k == "EVAL"), "")
        material_text = next((t for k, t in foundation_bits if k == "MATERIAL"), "")
        secondary = [t for k, t in foundation_bits if k == "OTHER"]

        parts = []
        if eval_text and material_text:
            mat_phrase = material_text
            mat_side = ""
            if mat_phrase.startswith("Black is "):
                mat_side = "black"
            elif mat_phrase.startswith("White is "):
                mat_side = "white"

            eval_clause = eval_text
            eval_side = ""
            if eval_clause.lower().startswith("white "):
                eval_side = "white"
            elif eval_clause.lower().startswith("black "):
                eval_side = "black"

            if mat_side and eval_side and mat_side == eval_side:
                parts.append(f"{eval_clause} and {mat_phrase.lower()}.")
            else:
                if " is " in mat_phrase:
                    mat_phrase = mat_phrase.replace(" is ", " being ", 1)
                parts.append(
                    rng.choice(
                        [
                            f"Despite {mat_phrase}, {eval_clause}.",
                            f"{eval_clause}, even with {mat_phrase}.",
                            f"{eval_clause} despite {mat_phrase}.",
                        ]
                    )
                )
        elif eval_text:
            parts.append(f"{eval_text}.")
        elif material_text:
            parts.append(f"{material_text}.")

        if secondary:
            if len(secondary) >= 2:
                sec = f"{secondary[0]} and {secondary[1]}"
            else:
                sec = secondary[0]
            parts.append(sec + ".")

        foundation_sentence = " ".join(parts).strip()

    # Per-move sentences
    sentences: list[str] = []
    recent: list[str] = []
    if foundation_sentence:
        sentences.append(foundation_sentence)
        # Seed recent with foundation clauses to reduce repetition
        for kind, txt in foundation_bits:
            recent.append(normalize_clause(txt))
    for idx, rec in enumerate(records):
        side = rec.get("side", "white")
        move = rec.get("move", "null")
        added = list(rec.get("added", []))
        removed = list(rec.get("removed", []))
        changed = list(rec.get("changed", []))
        raw_clauses, check_added, check_removed = pick_clauses(added, removed, changed)
        side_name = "White" if side == "white" else "Black"
        verb = verb_for(side)
        lead = f"{side_name} {verb} {move}"
        if check_added:
            lead += ", " + rng.choice(check_added_phrases)
        elif check_removed:
            lead += ", " + rng.choice(check_removed_phrases)
        def format_clause(c: str) -> str:
            lower = c.lower()
            if lower.startswith("discovered attack:"):
                return "a discovered attack appears: " + c[len("Discovered attack: ") :]
            if lower.startswith("skewer:"):
                return "a skewer appears: " + c[len("Skewer: ") :]
            if lower.startswith("pin:"):
                return "a pin appears: " + c[len("Pin: ") :]
            return c

        # Filter repeated clauses
        filtered: list[str] = []
        for c in raw_clauses:
            key = normalize_clause(c)
            if key in recent:
                continue
            filtered.append(c)
            if len(filtered) >= 2:
                break
        if filtered:
            clauses = filtered
        else:
            clauses = [] if idx == 0 else raw_clauses[:1]

        if clauses:
            formatted = [format_clause(c) for c in clauses]
            if len(formatted) == 1:
                tail = formatted[0]
            else:
                first = formatted[0]
                second = formatted[1]
                # Merge duplicated "X is Y" clauses for same subject
                if " is " in first and " is " in second:
                    subj1, pred1 = first.split(" is ", 1)
                    subj2, pred2 = second.split(" is ", 1)
                    if subj1 == subj2:
                        tail = f"{subj1} is {pred1} and {pred2}"
                    else:
                        tail = f"{first}, while {second}"
                else:
                    # Use contrast when a threat is neutralized
                    if "neutralized" in second.lower():
                        tail = f"{first}, but {second}"
                    else:
                        tail = f"{first}, while {second}"
            joiner = rng.choice(["and", "with", "as"])
            # Avoid awkward "with Black threatens..." constructions
            if joiner == "with" and tail.lower().startswith(("white ", "black ", "a ")):
                joiner = "and"
            if joiner == "and":
                sentence = lead + ", and " + tail + "."
            elif joiner == "with":
                sentence = lead + ", with " + tail + "."
            else:
                sentence = lead + ", as " + tail + "."
        else:
            sentence = lead + "."
        sentences.append(sentence)
        for c in clauses:
            recent.append(normalize_clause(c))
        if len(recent) > 6:
            recent = recent[-6:]

    # Conclusion from last record
    last = records[-1]
    last_tags = list(last.get("tags", []))
    conclusion_bits = []
    for t in last_tags:
        if t.startswith("EVAL:") or t.startswith("MATERIAL:") or t.startswith("KING:"):
            conclusion_bits.append(clean_clause(t))
    if conclusion_bits:
        def soften(clause: str) -> str:
            if clause.startswith("Black's king is "):
                return "Black's king " + clause[len("Black's king is ") :]
            if clause.startswith("White's king is "):
                return "White's king " + clause[len("White's king is ") :]
            if clause.startswith("Black king is "):
                return "the Black king " + clause[len("Black king is ") :]
            if clause.startswith("White king is "):
                return "the White king " + clause[len("White king is ") :]
            return clause

        if len(conclusion_bits) >= 2:
            conclusion = f"{conclusion_bits[0]} and {conclusion_bits[1]}"
            if len(conclusion_bits) >= 3:
                conclusion += f", with {soften(conclusion_bits[2])}"
        else:
            conclusion = conclusion_bits[0]
        if not conclusion.endswith("."):
            conclusion += "."
        sentences.append(conclusion)

    story = " ".join(sentences).strip()
    story = re.sub(r"(?<!to win material )threatens with ", "threatens ", story)
    return story


def main() -> None:
    parser = argparse.ArgumentParser(description="End-to-end analysis pipeline to Azure text.")
    parser.add_argument("--fen", required=True, help="Root FEN")
    parser.add_argument("--mode", choices=["position", "puzzle"], default="puzzle", help="Analysis mode")
    parser.add_argument("--out-dir", default="/tmp", help="Output directory")
    parser.add_argument("--multipv", type=int, default=3, help="Analysis multipv")
    parser.add_argument("--pv-plies", type=int, default=40, help="Max PV plies")
    parser.add_argument("--max-nodes", type=int, default=10000000, help="Max nodes per analysis")
    parser.add_argument("--max-duration", default="10s", help="Max duration per analysis")
    parser.add_argument("--crtk", default="crtk", help="crtk executable")
    parser.add_argument("--azure-script", default="scripts/azure_tag_text.py", help="Azure tag-to-text script")
    parser.add_argument("--prompt", default="tag/README-chatgpt.md", help="System prompt file")
    parser.add_argument("--temperature", type=float, default=0.3, help="Azure temperature")
    parser.add_argument("--max-output-tokens", type=int, default=1600, help="Azure max output tokens")
    parser.add_argument("--per-move", action="store_true", help="Also generate per-move Azure output for deltas")
    parser.add_argument("--story", action="store_true", help="Generate a single move-by-move puzzle story from deltas")
    parser.add_argument(
        "--story-two-pass",
        action="store_true",
        help="Generate per-move micro summaries then stitch into a story (best quality, extra calls).",
    )
    parser.add_argument(
        "--story-deterministic",
        action="store_true",
        help="Generate a fully deterministic story without any Azure calls (no hallucinations).",
    )
    parser.add_argument("--nlg-seed", type=int, default=1, help="Seed for deterministic NLG variation.")
    parser.add_argument(
        "--nlg-style",
        choices=["gm", "classic", "energetic"],
        default="gm",
        help="Deterministic NLG style preset.",
    )
    parser.add_argument(
        "--nlg-variants",
        type=int,
        default=1,
        help="Generate N deterministic variants by incrementing the seed.",
    )
    parser.add_argument(
        "--story-paraphrase",
        action="store_true",
        help="Generate deterministic story then paraphrase via Azure (no new facts).",
    )
    args = parser.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    delta_path = out_dir / "puzzle.tags.delta.jsonl"
    root_tags_path = out_dir / "analysis.root.tags.jsonl"
    enriched_path = out_dir / "analysis.root.enriched.jsonl"
    azure_out_path = out_dir / "analysis.azure.jsonl"

    if args.mode == "puzzle":
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

    tags_list = normalize_move_quotes(parse_tags(root_tags))

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
    ensure_meta(tags_list, "pv_plies", max((len(t[len("PV: ") :].split()) for t in tags_list if t.startswith("PV: ")), default=0))
    ensure_meta(tags_list, "variation_count", sum(1 for t in tags_list if t.startswith("PV: ")))
    ensure_meta(tags_list, "variation_max_plies", max((len(t[len("PV: ") :].split()) for t in tags_list if t.startswith("PV: ")), default=0))
    ensure_meta(tags_list, "threat_count", sum(1 for t in tags_list if t.startswith("THREAT: ")))
    ensure_meta(tags_list, "tactic_count", sum(1 for t in tags_list if t.startswith("TACTIC: ")))
    ensure_meta(tags_list, "word_target", word_target)
    if word_target >= 220:
        ensure_meta(tags_list, "length_hint", "long")

    write_jsonl(enriched_path, tags_list)

    azure_env = os.environ.copy()
    if not (args.mode == "puzzle" and (args.story or args.story_two_pass or args.story_deterministic or args.story_paraphrase)):
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

    if args.mode == "puzzle" and args.per_move:
        if not azure_env.get("AZURE_OPENAI_ENDPOINT") or not azure_env.get("AZURE_OPENAI_API_KEY"):
            raise SystemExit("AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_API_KEY must be set.")
        per_move_out = out_dir / "analysis.azure.moves.jsonl"
        cmd = [
            "python3",
            args.azure_script,
            "--input",
            str(delta_path),
            "--output",
            str(per_move_out),
            "--temperature",
            str(args.temperature),
            "--max-output-tokens",
            str(args.max_output_tokens),
            "--prompt",
            args.prompt,
        ]
        subprocess.check_call(cmd, env=azure_env)

    if args.mode == "puzzle" and args.story_deterministic:
        fen_parts = args.fen.split()
        start_side = "white"
        if len(fen_parts) > 1 and fen_parts[1].lower() in ("w", "b"):
            start_side = "white" if fen_parts[1].lower() == "w" else "black"
        if args.nlg_variants <= 1:
            story = build_deterministic_story(delta_path, start_side, seed=args.nlg_seed, style=args.nlg_style)
            print(story)
        else:
            for i in range(args.nlg_variants):
                seed = args.nlg_seed + i
                story = build_deterministic_story(delta_path, start_side, seed=seed, style=args.nlg_style)
                print(json.dumps({"variant": i + 1, "seed": seed, "text": story}, ensure_ascii=False))
        return

    if args.mode == "puzzle" and args.story_paraphrase:
        if not azure_env.get("AZURE_OPENAI_ENDPOINT") or not azure_env.get("AZURE_OPENAI_API_KEY"):
            raise SystemExit("AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_API_KEY must be set.")
        fen_parts = args.fen.split()
        start_side = "white"
        if len(fen_parts) > 1 and fen_parts[1].lower() in ("w", "b"):
            start_side = "white" if fen_parts[1].lower() == "w" else "black"
        story = build_deterministic_story(delta_path, start_side, seed=args.nlg_seed, style=args.nlg_style)
        story_path = out_dir / "analysis.story.deterministic.txt"
        story_path.write_text(story + "\n", encoding="utf-8")
        story_out = out_dir / "analysis.azure.story.jsonl"
        cmd = [
            "python3",
            args.azure_script,
            "--input",
            str(story_path),
            "--output",
            str(story_out),
            "--temperature",
            str(args.temperature),
            "--max-output-tokens",
            str(args.max_output_tokens),
            "--prompt",
            "tag/README-chatgpt-paraphrase.md",
            "--raw",
        ]
        subprocess.check_call(cmd, env=azure_env)
        print(story_out.read_text(encoding="utf-8").strip())
        return

    if args.mode == "puzzle" and args.story_two_pass:
        if not azure_env.get("AZURE_OPENAI_ENDPOINT") or not azure_env.get("AZURE_OPENAI_API_KEY"):
            raise SystemExit("AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_API_KEY must be set.")
        fen_parts = args.fen.split()
        start_side = "white"
        if len(fen_parts) > 1 and fen_parts[1].lower() in ("w", "b"):
            start_side = "white" if fen_parts[1].lower() == "w" else "black"
        story_prompt = out_dir / "analysis.story.txt"
        story_prompt.write_text(build_sequence_prompt(delta_path, start_side), encoding="utf-8")

        # Per-move micro summaries
        micro_out = out_dir / "analysis.micro.jsonl"
        sentences: list[str] = []
        raw_lines = story_prompt.read_text(encoding="utf-8").splitlines()
        # Split into record blocks
        blocks: list[list[str]] = []
        current: list[str] = []
        for line in raw_lines:
            if line.startswith("RECORD "):
                if current:
                    blocks.append(current)
                current = [line]
            else:
                if current:
                    current.append(line)
        if current:
            blocks.append(current)
        # Build per-record prompts
        with micro_out.open("w", encoding="utf-8") as out:
            for idx, block in enumerate(blocks, start=1):
                payload_lines = ["TASK: move_micro"]
                for ln in block:
                    if ln.startswith("RECORD "):
                        continue
                    payload_lines.append(ln)
                temp_in = out_dir / f"micro_{idx}.txt"
                temp_in.write_text("\n".join(payload_lines) + "\n", encoding="utf-8")
                temp_out = out_dir / f"micro_{idx}.jsonl"
                cmd = [
                    "python3",
                    args.azure_script,
                    "--input",
                    str(temp_in),
                    "--output",
                    str(temp_out),
                    "--temperature",
                    str(args.temperature),
                    "--max-output-tokens",
                    "200",
                    "--prompt",
                    "tag/README-chatgpt-move.md",
                    "--raw",
                ]
                subprocess.check_call(cmd, env=azure_env)
                raw = temp_out.read_text(encoding="utf-8").strip()
                if raw:
                    try:
                        obj = json.loads(raw)
                        sent = str(obj.get("output", "")).strip()
                    except json.JSONDecodeError:
                        sent = ""
                    if sent:
                        sentences.append(sent)
                        out.write(json.dumps({"i": idx, "s": sent}, ensure_ascii=False) + "\n")

        # Stitch
        stitch_prompt = out_dir / "analysis.stitch.txt"
        word_target = 260 + len(sentences) * 14
        word_target = max(240, min(820, word_target))
        stitch_lines = ["TASK: stitch_story", f"WORD_TARGET: {word_target}", "SENTENCES:"]
        for i, s in enumerate(sentences, start=1):
            stitch_lines.append(f"{i}. {s}")
        stitch_prompt.write_text("\n".join(stitch_lines) + "\n", encoding="utf-8")

        story_out = out_dir / "analysis.azure.story.jsonl"
        cmd = [
            "python3",
            args.azure_script,
            "--input",
            str(stitch_prompt),
            "--output",
            str(story_out),
            "--temperature",
            str(args.temperature),
            "--max-output-tokens",
            str(args.max_output_tokens),
            "--prompt",
            "tag/README-chatgpt-stitch.md",
            "--raw",
        ]
        subprocess.check_call(cmd, env=azure_env)
        print(story_out.read_text(encoding="utf-8").strip())
        return

    if args.mode == "puzzle" and args.story:
        if not azure_env.get("AZURE_OPENAI_ENDPOINT") or not azure_env.get("AZURE_OPENAI_API_KEY"):
            raise SystemExit("AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_API_KEY must be set.")
        fen_parts = args.fen.split()
        start_side = "white"
        if len(fen_parts) > 1 and fen_parts[1].lower() in ("w", "b"):
            start_side = "white" if fen_parts[1].lower() == "w" else "black"
        story_prompt = out_dir / "analysis.story.txt"
        story_prompt.write_text(build_sequence_prompt(delta_path, start_side), encoding="utf-8")
        story_out = out_dir / "analysis.azure.story.jsonl"
        cmd = [
            "python3",
            args.azure_script,
            "--input",
            str(story_prompt),
            "--output",
            str(story_out),
            "--temperature",
            str(args.temperature),
            "--max-output-tokens",
            str(args.max_output_tokens),
            "--prompt",
            "tag/README-chatgpt-sequence.md",
            "--raw",
        ]
        subprocess.check_call(cmd, env=azure_env)
        print(story_out.read_text(encoding="utf-8").strip())
        return

    print(azure_out_path.read_text(encoding="utf-8").strip())


if __name__ == "__main__":
    main()
