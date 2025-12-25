# Piece Tags (`chess.tag`)

This project can emit **piece tags** (short lines like `weak white f4 pawn`). The goal is to provide compact signals that an LLM (or a human) can turn into a coherent, natural-language description of a position.

## Tag Format

Each non-empty square produces one line:

`<tier> <side> <square> <piece>`

Examples:

- `weak white f4 pawn`
- `very strong black f5 queen`
- `neutral black e8 king`

Two extra summary lines are appended:

- `strongest: <side> <piece> <square>`
- `weakest: <side> <piece> <square>`

Example:

- `strongest: black queen f5`
- `weakest: black king g5`

## Field Meanings

### `tier`
One of:

- `very strong`
- `strong`
- `slightly strong`
- `neutral`
- `slightly weak`
- `weak`
- `very weak`

Interpretation: how good/bad the piece is **on its current square**.

### `side`
`white` or `black` — the owner of the piece being described.

### `square`
Algebraic square in lowercase (e.g., `f4`, `e8`).

### `piece`
Lowercase: `pawn`, `knight`, `bishop`, `rook`, `queen`, `king`.

## How To Use These Tags In A Description

The tags are best treated as **hints** and **priorities**, not as strict tactics.

### What Matters Most (Importance Ranking)

Use this ordering when deciding what to mention:

1. `strongest:` and `weakest:` lines (global highlights; mention these first).
2. Any `very strong` / `very weak` pieces (big features).
3. Any `strong` / `weak` pieces (notable features).
4. `slightly strong` / `slightly weak` pieces (supporting details; mention only if needed).
5. `neutral` pieces (usually omit; include only for completeness or if the piece is critical context).

### What Each Tier Suggests (Natural Language)

When converting tags into prose, these mappings are usually reasonable:

- `very strong`: “excellent piece”, “dominant”, “key attacker/defender”, “very well placed”
- `strong`: “well placed”, “active”, “useful”
- `slightly strong`: “somewhat active”, “slightly improved placement”
- `neutral`: “normal placement”, “no clear issue”
- `slightly weak`: “a bit awkward”, “slightly misplaced”
- `weak`: “poorly placed”, “target”, “awkward”, “likely needs improvement/defense”
- `very weak`: “major problem”, “severely misplaced/exposed”, “urgent weakness”

### How To Turn Tags Into A Coherent Summary

Good summaries usually follow this structure:

1. One sentence for each side’s most important feature (often starts from the `strongest:` line).
2. One sentence mentioning the biggest weakness (often starts from the `weakest:` line).
3. Optional: 1–3 supporting details from `strong`/`weak` tiers.

Example template:

- “`<side>`’s key piece is the `<piece>` on `<square>`.”
- “The main problem is the `<side>` `<piece>` on `<square>`.”
- “Supporting details: `<tier> <side> <square> <piece>`, …”

### The `strongest` / `weakest` Lines (Explicit Meaning)

- `strongest: black queen f5` means: the single most important *positive* piece placement on the board is the black queen on f5.
- `weakest: black king g5` means: the single most important *negative* piece placement on the board is the black king on g5.

These lines are often the best anchors for an LLM to build a narrative around.
