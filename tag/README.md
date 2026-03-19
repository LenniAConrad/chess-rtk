# Tag: LLM-First, DB-Filterable Tagging + Move Deltas

This document proposes a tag vocabulary and delta format that is:
- Easy for LLMs like FLAN to interpret.
- Filterable and indexable in a database.
- Human readable.
- Stable enough to track what changed after each move.

The goal is to emit a compact, deterministic set of tags per position and a clean diff per move.

**Goals**
- Deterministic, low-variance tags for the same position.
- Small, fixed vocabularies with explicit enums.
- Key/value structure so tags can be indexed or queried.
- Human-readable lines that can be used as prompts directly.
- Predictable ordering for stable diffs.
- A clear delta model: added, removed, changed.

**Tag Line Shape (Recommended)**
- Prefix defines the family: `META:`, `FACT:`, `MATERIAL:`, `PAWN:`, `PIECE:`, `KING:`, `TACTIC:`, `THREAT:`, `SPACE:`, `INITIATIVE:`, `DEVELOPMENT:`, `MOBILITY:`, `ENDGAME:`, `OPENING:`.
- Use `key=value` pairs with lowercase keys and values.
- Keep keys in a fixed order within each family.
- Use fixed enums rather than free text.
- Use `side=white|black` and `square=a1` consistently.
- Keep numbers as plain integers or short decimals.

Example tag line:
`PAWN: structure=isolated side=white square=c4`

**Canonicalization Rules (For Stable Diffs)**
- Sort tags by family priority (see below).
- Sort tags within a family by their identity keys (see below).
- Sort keys within each tag in the exact order listed in this README.
- Use lowercase for all values except ECO codes.
- When a value is unknown, omit the tag entirely instead of emitting `unknown`.

**Priority Order (For Tag-to-Text and Sorting)**
- `FACT:` and `META:`.
- `TACTIC:` and `THREAT:`.
- `PIECE:` extremes, then `PIECE:` tiers.
- `KING:` and `PAWN:` structure.
- `MATERIAL:`.
- `SPACE:` / `INITIATIVE:` / `DEVELOPMENT:` / `MOBILITY:`.
- `OUTPOST:`.
- `ENDGAME:` and `OPENING:`.

**Recommended Tag List**

**Core Meta**
- `META: to_move=white|black`
- `META: phase=opening|middlegame|endgame`
- `META: eval_cp=<int>`
- `META: eval_bucket=equal|slight_white|slight_black|clear_white|clear_black|winning_white|winning_black|crushing_white|crushing_black`
- `META: mate_in=<int>`
- `META: mated_in=<int>`
- `META: wdl=<w>/<d>/<l>`
- `META: difficulty=very_easy|easy|medium|hard|very_hard`
- `META: source=engine|human|puzzle|opening|analysis`

**Status / Rules**
- `FACT: status=normal|check|checkmated|stalemate|insufficient|draw_claimable`
- `FACT: in_check=white|black|none`
- `FACT: castle_rights=KQ|K|Q|kq|k|q|none`
- `FACT: en_passant=<square>`
- `FACT: center_control=white|black|balanced`
- `FACT: center_state=open|closed`
- `FACT: space_advantage=white|black`

**Material**
- `MATERIAL: balance=equal|white_up_pawn|black_up_pawn|white_up_minor|black_up_minor|white_up_exchange|black_up_exchange|white_up_queen|black_up_queen`
- `MATERIAL: imbalance=bishop_pair_white|bishop_pair_black|opposite_color_bishops|same_color_bishops|queenless|rookless`
- `MATERIAL: piece_count side=white|black piece=pawn|knight|bishop|rook|queen|king count=<int>`

**Piece Activity (Inspired by `chess.tag`)**
- `PIECE: tier=very_strong|strong|slightly_strong|neutral|slightly_weak|weak|very_weak side=white|black piece=pawn|knight|bishop|rook|queen|king square=<sq>`
- `PIECE: extreme=strongest|weakest|strongest_white|weakest_white|strongest_black|weakest_black side=white|black piece=pawn|knight|bishop|rook|queen|king square=<sq>`
- `PIECE: activity=pinned|trapped|low_mobility|high_mobility side=white|black piece=pawn|knight|bishop|rook|queen|king square=<sq>`

**Pawn Structure**
- `PAWN: structure=isolated|passed|backward side=white|black square=<sq>`
- `PAWN: structure=doubled side=white|black file=<file>`
- `PAWN: structure=connected_passed side=white|black squares=<sq,sq>`
- `PAWN: islands side=white|black count=<int>`
- `PAWN: majority=queenside|kingside|center side=white|black`

**King Safety**
- `KING: safety=very_safe|safe|unsafe|very_unsafe side=white|black`
- `KING: castled=yes|no side=white|black`
- `KING: shelter=pawns_intact|weakened|open side=white|black`

**Tactics and Threats**
- `TACTIC: motif=fork|pin|skewer|discovered_attack|double_attack|deflection|decoy|overload|interference|back_rank|clearance|zwischenzug|perpetual_check|mate_attack|hanging side=white|black detail="<text>"`
- `THREAT: type=mate|material|tactic|promote side=white|black severity=immediate|soon|latent move="<SAN>"`

**Strategic Themes**
- `SPACE: side=white|black|equal`
- `INITIATIVE: side=white|black|equal`
- `DEVELOPMENT: side=white|black|equal`
- `MOBILITY: side=white|black|equal`
- `OUTPOST: side=white|black square=<sq> piece=knight|bishop`

**Endgame / Opening**
- `ENDGAME: type=queenless|rook|minor|opposite_bishops`
- `OPENING: eco=<code>`
- `OPENING: name="<name>"`

**Identity Keys (For Delta Tracking)**
These keys define “the same tag” across positions. If the identity matches but a value changes, it is a `changed` tag.

- `META:` identity is the tag key itself, like `META: eval_cp`.
- `FACT:` identity is the tag key itself, like `FACT: status`.
- `MATERIAL:` identity is the tag key itself, like `MATERIAL: balance`, or `side + piece` for `piece_count`.
- `PIECE: tier` identity is `side + piece + square`.
- `PIECE: extreme` identity is `extreme`.
- `PIECE: activity` identity is `activity + side + piece + square`.
- `PAWN: structure` identity is `structure + side + square`.
- `PAWN: connected_passed` identity is `side + squares`.
- `PAWN: islands` identity is `side`.
- `PAWN: majority` identity is `side`.
- `KING:` identity is `key + side` (safety/shelter/castled).
- `TACTIC:` identity is `motif (+ detail when present)`.
- `THREAT:` identity is `type + side`.
- `SPACE:` identity is the tag key itself.
- `INITIATIVE:` identity is the tag key itself.
- `DEVELOPMENT:` identity is the tag key itself.
- `MOBILITY:` identity is the tag key itself.
- `OUTPOST:` identity is `side + square + piece`.
- `ENDGAME:` identity is the tag key itself.
- `OPENING:` identity is the tag key itself.

**Delta Format (Per Move)**
For each move, compute the new tag set for the resulting position, then compare to the previous set using the identities above.

Recommended JSON shape per ply:
```json
{
  "ply": 12,
  "move": {"san": "Nf7+", "uci": "g5f7"},
  "tags": ["META: to_move=black", "FACT: status=check", "PIECE: tier=strong side=white piece=knight square=f7"],
  "delta": {
    "added": ["FACT: status=check", "PIECE: tier=strong side=white piece=knight square=f7"],
    "removed": ["PIECE: tier=neutral side=white piece=knight square=g5"],
    "changed": [
      {
        "key": "KING: side=black",
        "from": "KING: safety=safe side=black",
        "to": "KING: safety=unsafe side=black"
      },
      {
        "key": "META: eval_cp",
        "from": "META: eval_cp=35",
        "to": "META: eval_cp=160"
      }
    ]
  }
}
```

**Why This Works For LLMs**
- Tag lines are short and consistent with fixed tokens.
- Value words are repeated across positions, which helps model anchoring.
- The delta describes the effect of a move without re-reading the full position.

**Feeding FLAN (Input Packaging)**
The model must see one position (one record) at a time. Do not feed a full file of many records.

**Single position summary**
Provide only the tags for one position (optionally include `move_san` if you want the move name in text).
```
position:
  fen: <fen>
  move_san: <san or null>
  tags:
    - <tag>
    - <tag>
```

**Move effect summary**
Provide only the delta for one move (optionally include the current position tags for context).
```
move:
  san: <san>
delta:
  added: [<tag>, ...]
  removed: [<tag>, ...]
  changed: [{key, from, to}, ...]
tags: [<tag>, ...]   # optional
```

**Variations**
Feed each branch separately. Do not mix branches in one prompt.
```
branch_id: main
move_san: <san>
tags: [...]
delta: {...}
```

**Move naming rule**
Only mention a move if it appears explicitly in the input (`move_san`, `THREAT move="..."`, `PV:`/`line:` fields).  
If you don’t want the model to name moves, omit those fields.

**Puzzle-focused minimal tag set (recommended)**
For tactical puzzles, reduce noise by using:
`THREAT`, `TACTIC`, `FACT: in_check`, `FACT: status`, `META: to_move/eval_bucket/eval_cp/wdl/mated_in`, `MATERIAL: balance`, `KING: safety`.

**Prompt Templates (FLAN-Friendly)**

Position summary prompt:
```
You are a chess annotator.
Summarize the position in 2-3 sentences.
Use only the information in the tags, do not invent tactics.
Tags:
<list of tags in priority order>
```

Move effect prompt:
```
You are a chess annotator.
Explain the effect of the move in 1-2 sentences.
Use the tag changes only.
Added tags:
<added>
Removed tags:
<removed>
Changed tags:
<changed>
```

Full game analysis prompt (per ply):
```
You are a chess annotator.
Given the previous summary and the tag delta, write 1-2 sentences about the move's impact.
Previous summary:
<prev_summary>
Delta:
<delta>
```

**Fine‑Tuned FLAN Prompt (Recommended)**
Use this exact input format during training and inference. Keep one record per sample.
```
TASK: puzzle_commentary
MOVE_SAN: <san or null>
TAGS:
- <tag>
- <tag>
```
Output should be a single paragraph (140–220 words) that:
- Starts with a foundation (material + king safety + top tactical motif).
- Explains the main forcing idea or threat.
- Notes what changed if a move is provided.
- Ends with a qualitative evaluation (prefer `META: eval_bucket`).
- Never mentions tags/PGN/input or meta phrases like “as described” or “according to the input”.
- Optional length control: add `META: word_target=<int>` or `META: length_hint=long` when you want longer outputs for complex lines.
- The generator may also inject a control line `WORD_TARGET: <int>`; treat it the same as `META: word_target`.
- For data generation, the pipeline can auto-add complexity tags and `META: word_target` based on PV/variation length and tactical density:
  `META: pv_plies`, `META: variation_count`, `META: variation_max_plies`, `META: threat_count`, `META: tactic_count`,
  `META: mate_threat_count`, `META: word_target`.

**Generating Training Data With ChatGPT**
1) Sample positions or games: use `crtk tags --sequence --delta --analyze`.
2) Emit one record per sample (never the whole file).
3) Feed each record to ChatGPT using `tag/README-chatgpt.md` as the system prompt.
4) Store pairs as JSONL:
```
{"input":"TASK: puzzle_commentary\\nMOVE_SAN: Qh8\\nTAGS:\\n- ...","output":"<model answer>"}
```
5) Keep tag order stable and avoid mixing branches in one sample.
6) Use the same prompt format for both training and inference.

**Azure Tag‑Text (Responses API)**
For batch tag‑to‑text generation with Azure OpenAI, use the built‑in script:
```
export AZURE_OPENAI_ENDPOINT="https://<resource>.openai.azure.com/openai/responses?api-version=2025-04-01-preview"
export AZURE_OPENAI_API_KEY="<key>"
export AZURE_OPENAI_MODEL="gpt-5-mini"

python3 scripts/azure_tag_text.py \
  --input "/path/to/tags.jsonl" \
  --output "/path/to/tag_text.jsonl" \
  --temperature 0.3 \
  --max-output-tokens 800
```
The script reads each JSONL line, formats it as:
`TASK: puzzle_commentary`, `MOVE_SAN: <san|null>`, `TAGS: - <tag>` and writes JSONL:
`{"input":"...","output":"..."}`.
It uses `tag/README-chatgpt.md` as the system prompt by default.

**Implementation Notes**
- Emit only Tag format. No parallel schemas.
- Canonicalize Tag before diffing and before storing in the DB.
- Store Tag as JSON array of strings, and also parse into DB columns for filtering.
- For tag-to-text, keep the top N tags per family to reduce noise.
- Avoid tags that require deep search or subjective judgment unless they are optional enrichments.

No converter is used; Tag is produced directly.
