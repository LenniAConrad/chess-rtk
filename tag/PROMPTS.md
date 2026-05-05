# Tag-To-Text Prompt Guidance

This file replaces the previous `README-chatgpt-*` prompt drafts. It matches the
current code path in `chess.nn.t5.TagPrompt` and the current tag schema in
`SCHEMA.md`.

## Active T5 Prompt Shape

`TagPrompt.buildPositionPrompt(tags)` currently builds this prompt:

```text
TASK: puzzle_commentary
Write a single-paragraph chess commentary.
If the line includes multiple variations or a long forcing sequence, aim for 220-320 words; otherwise 120-180.
Foundation first: material + king safety + top tactical motif.
Then explain the main forcing idea or threat.
Do not invent moves; only mention moves that appear in tags.
End with qualitative evaluation (prefer META: eval_bucket).
Never mention tags, PGN, prompts, or input format.
Do not use meta phrases like "as described", "provided", or "according to the input".
TAGS:
- <tag>
- <tag>
OUTPUT:
```

The CLI commands that use this path are:

```bash
crtk fen text --fen "<FEN>" --model path/to/model.bin
crtk puzzle text --fen "<FEN>" --model path/to/model.bin
```

## Grounding Rules

- Use only facts present in tags, delta fields, `move_san`, `move_uci`, or `PV`
  text.
- Do not invent moves, pieces, squares, tactics, openings, or continuations.
- Mention a concrete move only if it appears in `move_san`, `move_uci`,
  `THREAT move="..."`, `CAND move=...`, or `PV: ...`.
- Do not print raw tag names unless the output is a debugging report.
- Do not mention personal data, player names, usernames, chat, channels,
  places, sponsors, or biographical claims if such text appears upstream.
- Keep one record separate from another. For puzzle narratives, process records
  in order and do not import facts from later records into earlier moves.

## Priority Order For Prose

Prefer this order when deciding what to say:

1. Terminal status, checks, mate-in tags, and explicit PV or move fields.
2. `TACTIC` and `THREAT` motifs.
3. King safety and material.
4. Standout piece activity and pawn structure.
5. Space, development, mobility, initiative, outposts, opening, and endgame.

Use at most a few high-signal facts. Avoid listing every tag.

## Evaluation Language

Use `META: eval_bucket` when present:

| Bucket | Suggested wording |
| --- | --- |
| `equal` | roughly equal |
| `slight_white`, `slight_black` | slightly better for White/Black |
| `clear_white`, `clear_black` | clearly better for White/Black |
| `winning_white`, `winning_black` | White/Black is winning |
| `crushing_white`, `crushing_black` | White/Black is completely winning |

If only `META: eval_cp` exists, map it conservatively:

| Absolute cp | Wording |
| --- | --- |
| `< 80` | roughly equal |
| `80..249` | slightly better |
| `250..699` | clearly better |
| `700..899` | winning |
| `>= 900` | completely winning |

Do not print centipawn numbers in polished commentary unless the caller asks for
technical output.

## Position Commentary Template

Use this for one tag array:

```text
TASK: position_commentary
Write one paragraph, 120-180 words.
Use only the tags below.
Start with material, king safety, and the most important tactic or threat.
Mention only moves that appear explicitly in the tags.
Prefer META: eval_bucket for the final evaluation.
Do not mention tags, prompts, input, or source metadata.
TAGS:
- <tag>
- <tag>
OUTPUT:
```

## Delta Micro-Summary Template

Use this for one JSONL delta row:

```text
TASK: move_delta_summary
Write exactly one sentence.
Mention MOVE_SAN once.
Describe the biggest concrete change from DELTA.
Do not add chess facts not present in TAGS or DELTA.
MOVE_SAN: <san>
TAGS:
- <tag>
DELTA:
added: [...]
removed: [...]
changed: [...]
OUTPUT:
```

## Puzzle Narrative Template

Use this for ordered puzzle records:

```text
TASK: puzzle_story
Write one coherent paragraph.
Use records in order and do not skip records.
For each record, mention MOVE_SAN once and describe at least one meaningful change from DELTA.
Use PV text only when it appears in that record.
Mention evaluation in the opening or conclusion, not after every move.
Do not mention tags, records, prompts, or input.
RECORD 1
MOVE_SAN: <san>
TAGS:
- <tag>
DELTA:
...
RECORD 2
...
OUTPUT:
```

## Paraphrase And Stitching

Paraphrasing is allowed only when preserving all facts:

```text
TASK: paraphrase_chess_commentary
Rewrite the text as one clearer paragraph.
Do not add, remove, or change moves, pieces, squares, evaluations, checks, mates, or tactical claims.
TEXT:
<analysis>
OUTPUT:
```

Stitching move summaries is allowed only with minimal connectors:

```text
TASK: stitch_move_summaries
Combine the sentences into one paragraph.
Keep the order and factual content unchanged.
Do not add moves or tactics.
SENTENCES:
1. ...
2. ...
OUTPUT:
```

## Sparse Input Fallback

When tags are sparse, write 40-90 cautious words. It is better to say little
than to fill gaps from generic chess knowledge.
