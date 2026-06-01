# Tag Reference

A tag is crtk's answer to a recurring problem: how do you let a language model talk about a chess position without letting it hallucinate the moves? You don't hand it prose. You hand it facts. A tag such as `MATERIAL: balance=white_up_exchange` or `TACTIC: motif=fork side=white move=e4c5` is a short, parseable string that states only what is verifiably true of the board, derived from the same chess core that generates moves, converts FEN/SAN/UCI, and runs perft. Feed the tagger the same FEN twice and you get the same tags in the same canonical order, every time. This page is the catalogue: every family, every field, every value, so you — or a model downstream — can quote them without guessing. Tags come out of `fen tags` and `puzzle tags`, get summarized by `record tag-stats`, and feed both the classical `position describe` pipeline and the optional T5 summaries behind `fen text` / `puzzle text`.

To produce tags from a FEN, PGN, or puzzle line, see [Command Reference](command-reference.md) and [Example Commands](example-commands.md). For the design behind the shared core, see [Architecture](architecture.md).

## What tags are

A tag is a single line in one canonical format:

```text
FAMILY: key=value key=value
```

- The **family** is uppercase (`FACT`, `META`, `TACTIC`, ...) and names the category of fact.
- **Fields** are space-separated `key=value` pairs. A bare `FAMILY:` with no fields is allowed.
- Values containing spaces, quotes, or backslashes are double-quoted, with the quote or backslash escaped: `name="King's Pawn Game"`.

Nothing here reaches for an external UCI engine. Every tag is derivable from the position itself; the only "engine" inputs permitted are crtk's own bounded searchers — alpha-beta, MCTS, the mate prover — plus the static evaluator, static exchange evaluation (SEE), and the bundled ECO opening book. Those searchers run under a fixed node and time budget, which is what keeps bulk tagging both deterministic and cheap. An unbounded search would give you sharper tags and a different answer every run; that tradeoff isn't worth it here.

> No move is invented. If a downstream text generator names a move, that move already appears in a tag — `CAND: ... move=`, `PV:`, `THREAT: ... move=`, `TACTIC: ... move=`, or `MOVE: only=`. Moves in `CAND` / `THREAT` / `IDEA` are SAN; static-motif and `MOVE: only=` moves are UCI.

## Producing tags

Most paths into the tagger differ only in what they read — a single FEN, a list, a PGN, a puzzle line — and converge on the same generator.

| Command | Purpose |
| --- | --- |
| `fen tags --fen "<FEN>"` | Emit the full tag set for one position (JSON array of tag strings). |
| `fen tags --input FILE` | Tag a FEN list; supports parent/child pairs and `--sequence` for ordered lines. |
| `fen tags --pgn FILE` | Tag positions from a PGN (`--mainline` or `--sidelines`). |
| `fen tags --delta` | Emit per-move tag deltas as JSONL. |
| `fen tags --analyze` | Run bounded engine analysis to enrich tags (adds `CAND` / `PV` / eval metadata). |
| `puzzle tags --fen "<FEN>"` | Generate per-move tags across a puzzle's principal variations. |
| `record tag-stats --input FILE` | Summarize tag distributions across a `.record` file. |

Both `fen tags` and `puzzle tags` emit from `chess.tag.Generator`. `position describe` consumes the same tags to build deterministic prose; `fen text` / `puzzle text` hand them to the T5 tag-to-text model. The full flag list lives in [Command Reference](command-reference.md).

## Determinism and stability guarantees

The whole point of tags is that you can trust them across runs, so the generator is built to a few hard rules.

- **Deterministic.** The same FEN always yields the same tags. A tag asserts what is true of the position and nothing more — no mood, no hand-waving evaluation.
- **Sorted and de-duplicated.** Output is ordered by family rank, then lexicographically within a family, with exact duplicates dropped. The sort is idempotent, so re-sorting canonical output changes nothing.
- **Stable canonical family order:** `FACT` → `META` → `MOVE` → `THREAT` → `CAND` → `PV` → `IDEA` → `TACTIC` → `CHECKMATE` → `PIECE` → `KING` → `PAWN` → `MATERIAL` → `SPACE` → `INITIATIVE` → `DEVELOPMENT` → `MOBILITY` → `OUTPOST` → `ENDGAME` → `OPENING`.
- **Regression-locked.** A new `pattern=` or `motif=` value ships with a golden fixture under `testdata/tags/*.tsv` and is cross-checked against the existing detectors before it lands. That cross-check is the safeguard that stops a new detector from quietly rewriting old output.

## Family overview

Some families fire on every position; others only when the board earns them. The "Emission" column tells you which.

| Family | Emission | What it captures |
| --- | --- | --- |
| `FACT` | always | Static board facts: status, check, castling rights, en passant, center. |
| `META` | always | Position and analysis metadata: side to move, phase, eval, opening. |
| `MOVE` | always | Aggregate legal-move counts and forced/only-move facts. |
| `THREAT` | when grounded | What the side to move threatens next (promotion, mate, king attack). |
| `CAND` | with `--analyze` | Engine candidate moves with a grounded reason. |
| `PV` | with `--analyze` | Principal variation in SAN. |
| `IDEA` | when grounded | Plans citing a legal move or re-derivable board fact. |
| `TACTIC` | when present | Static (and bounded-search) tactical motifs. |
| `CHECKMATE` | mated positions | Mate identity and named mate patterns. |
| `PIECE` | always | Per-piece role, tier, and activity. |
| `KING` | always | Castling, pawn shelter, and king-safety summary. |
| `PAWN` | always | Pawn-structure features and majorities. |
| `MATERIAL` | always | Material balance, imbalances, and per-piece counts. |
| `SPACE` | always | Center control and space advantage. |
| `INITIATIVE` | always | Tempo and forcing-move signals. |
| `DEVELOPMENT` | always | Development lead and undeveloped pieces. |
| `MOBILITY` | always | Mobility comparison and most-mobile piece. |
| `OUTPOST` | when present | Knight/bishop outpost squares. |
| `ENDGAME` | when applicable | Material endgame class. |
| `OPENING` | when ECO matches | ECO code and opening name from the bundled book. |

## FACT — static board observations

The things you could read off the board without thinking: status, check, rights, en passant, who holds the center.

| Field | Values |
| --- | --- |
| `status` | `normal`, `check`, `checkmated`, `stalemate`, `insufficient` |
| `in_check` | `white`, `black`, `none` |
| `castle_rights` | `KQkq` and any subset, or `none` |
| `en_passant` | a target square, or `-` |
| `center_state` | `open`, `closed`, and related states |
| `center_control` | `white`, `black`, `balanced` |
| `puzzle` | `winning`, `draw` (when tagging puzzle context) |

## META — metadata and evaluation

Side to move, phase, opening, and whatever the bounded evaluator could establish under its budget.

| Field | Values |
| --- | --- |
| `to_move` | `white`, `black` |
| `phase` | `opening`, `middlegame`, `endgame` |
| `source` | `engine` (when analysis enriched the tags) |
| `eval_cp` | integer centipawns, White-positive |
| `eval_bucket` | `equal`, `slight_white`, `clear_white`, `winning_white`, `crushing_white`, and the `*_black` mirror |
| `mate_in` | mating distance in moves |
| `mated_in` | `0` for a side already mated |
| `wdl` | `<w>/<d>/<l>` permille triple |
| `difficulty` | difficulty label (e.g. `very_easy`) |
| `eco` | ECO code |
| `opening` | quoted opening name |

## MOVE — legal-move facts

Generate every legal move, then count them by kind. The counts say a lot about the position before any evaluation runs.

| Field | Meaning |
| --- | --- |
| `legal` | total legal moves |
| `captures` | capturing moves |
| `checks` | checking moves |
| `mates` | moves giving checkmate |
| `promotions` | promoting moves |
| `underpromotions` | non-queen promotions |
| `castles` | castling moves |
| `en_passant` | legal en-passant captures |
| `quiet` | quiet (non-capturing, non-checking) moves |
| `evasions` | check evasions when in check |
| `only` | the single legal move in UCI, when exactly one exists |
| `forced` | `true` when the side to move has only one legal move |

Each count field is `=<n>`.

## THREAT — grounded next-move threats

What the side to move is threatening — but only when a concrete legal move backs the claim.

| `type` | Fields |
| --- | --- |
| `promote` | `side=` `severity=immediate` `square=` |
| `mate` | `side=` `severity=immediate\|soon` `move="<SAN>"` `[target="mate_in_N"]` |
| `king_attack` | `side=` `severity=soon` `move="<SAN>"` `target=open_X_file\|half_open_X_file\|exposed_king_ring_N` |

Mate-in-1 falls straight out of direct play; anything longer has to be proven by the bounded mate prover before it earns the tag. A `king_attack` needs a legal check landing into a shelter that is demonstrably weak, and it stands down when a mate threat already covers the position. Material threats that would require search to confirm are left out on purpose — they can't be grounded, so they don't get a tag.

## CAND — candidate moves

Only with `--analyze`. One tag per move the bounded search puts forward.

- Fields: `role=best|alt move=<SAN> eval_cp=<int> note="<short reason>"`.
- `note` carries a grounded reason rather than a verdict: `checkmate`, `wins material` (a SEE-positive capture), `promotes`, `check`, or empty when none applies.

## PV — principal variation

Emitted only with `--analyze`.

```text
PV: <SAN> <SAN> ...
```

## IDEA — grounded plans

Plans, but kept honest: each cites a legal move or a board fact you can re-derive. No speculation about what "should" happen.

- Fields: `side=<color> type=<type> move="<SAN>" detail="<short grounded reason>"`.

| `type` | Meaning |
| --- | --- |
| `win_material` | highest-SEE legal capture (with `move=`) |
| `promote` | legal promotion or passed-pawn advance (with `move=`) |
| `king_safety` | king on an open/half-open file with at least one ring attacker (no move) |
| `space` | a countable piece-advance majority past the middle (no move) |

## TACTIC — tactical motifs

The motifs a coach would point to. Each carries `motif=<id> side=white|black`, then whatever fields the motif needs to be specific: `move=<uci>`, `piece=`, `square=`, `detail="..."`, and motif-specific extras like `targets=<piece@sq,...>` for a fork, `front=`/`behind=` for a skewer, `slider=`/`target=` for a discovered attack, `count=`/`squares=` for rooks on the seventh, or `see=<cp>` for material loss.

| `motif` | Description |
| --- | --- |
| `pin` | a piece pinned against a more valuable piece or the king |
| `skewer` | a more valuable piece attacked in front of a lesser one |
| `fork` | one piece attacking two or more targets |
| `discovered_attack` | a moving piece unmasks a slider's attack |
| `hanging` | an undefended attacked piece |
| `overload` | a defender with too many duties |
| `trapped` | a piece with no safe squares |
| `mate_in_1` | a legal move delivering immediate mate |
| `mate_in_<n>` | a forced mate in `n>=2` proven by the bounded mate prover |
| `promotion` | a legal promotion |
| `underpromotion` | a legal non-queen promotion |
| `rooks_on_7th` | one or two rooks on the enemy second-from-back rank |
| `loses_material` | SEE-backed: the side to move can win material via a legal capture (side = the losing side) |
| `double_check` | a legal move giving check with two pieces at once |

## CHECKMATE — mate identity

This family fires once the game is already over — the board is mated, and these tags describe how.

| Field | Values |
| --- | --- |
| `winner` | `white`, `black` |
| `defender` | `white`, `black` |
| `delivery` | `pawn`, `knight`, `bishop`, `rook`, `queen`, `king`, `multiple` |
| `pattern` | a named mate net (see below) |

**Named patterns currently emitted:** `back_rank_mate`, `smothered_mate`, `corner_mate`, `support_mate`, `double_check`, `arabian_mate`, `epaulette_mate`, `anastasia_mate`, `david_and_goliath_mate`, `damiano_mate`, `scholars_mate`, `swallows_tail_mate`, `dovetail_mate`, `hook_mate`, `opera_mate`, `lawnmower_mate`, `blackburne_mate`, `greco_mate`, `kill_box_mate`, `reti_mate`, `anderssen_mate`, `mayet_mate`.

A named pattern is a refinement, not a replacement: it sits alongside the generic geometry it specializes, so `anastasia_mate` shows up next to `support_mate` rather than instead of it. Each detector runs only on an already-mated board, ships its own golden fixture, and is cross-collision-probed against the rest — the same board fed to every detector — so two patterns never claim the same mate by accident.

## PIECE — piece roles and activity

How each piece is doing — where it sits, how much it can do, and where it ranks against its peers.

| Field | Values |
| --- | --- |
| `activity` | `high_mobility`, `low_mobility`, `pin`, `restricted`, `trapped`, and related |
| `tier` | a strength tier such as `very_strong`, `neutral`, `very_weak` (with `side=` `piece=` `square=`) |
| `extreme` | `strongest`, `weakest`, and `*_white` / `*_black` variants (with `side=` `piece=` `square=`) |

## KING — king safety

| Field | Values |
| --- | --- |
| `castled` | `yes`, `no` (with `side=`) |
| `shelter` | `pawns_intact`, `weakened`, `open` (with `side=`) |
| `safety` | `very_safe`, `safe`, `unsafe`, `very_unsafe` (with `side=`) |

## PAWN — pawn structure

| Field | Values |
| --- | --- |
| `structure` | `doubled`, `isolated`, `backward`, `passed`, `connected_passed` (with `side=` and `square=`, `file=`, or `squares=`) |
| `majority` | `kingside`, `queenside`, `center` (with `side=`) |
| `islands` | island `count=` (with `side=`) |

## MATERIAL — material balance

| Field | Values |
| --- | --- |
| `balance` | `equal`, `white_up_<unit>`, `black_up_<unit>` |
| `imbalance` | `bishop_pair_white`, the `*_black` mirror, `rookless`, `queenless`, `opposite_color_bishops`, and related |
| `piece_count` | per-piece count (with `side=` `piece=` `count=`) |

## SPACE, INITIATIVE, DEVELOPMENT, MOBILITY, OUTPOST

The slower-burning positional signals, each reduced to a side and a count where one exists.

| Family | Forms |
| --- | --- |
| `SPACE` | `side=`; `center_control side= count=`; space-advantage signal |
| `INITIATIVE` | `side=`; `forcing_moves side= count=` |
| `DEVELOPMENT` | `side=` or `equal`, plus undeveloped-piece detail |
| `MOBILITY` | `side=`; `piece= side= square= moves=` for the most mobile piece |
| `OUTPOST` | `side= square= piece=` |

## ENDGAME and OPENING

| Family | Forms |
| --- | --- |
| `ENDGAME` | `type=rook\|minor\|queenless\|...` material endgame class |
| `OPENING` | `eco=A00`; `name="Amar Opening"` style fields (inherited from the parent line when the child has no exact ECO match) |

`OPENING` tags appear only when the bundled ECO book matches the position — out of book, there is no honest code to emit.

## Tag deltas and identity

Walking a game move by move, you care less about the full tag set than about what just changed. `fen tags --delta` gives you that. The trick is comparing tags by **semantic identity** instead of raw text, so editing a value reads as a single change rather than one remove paired with one add:

```text
META: to_move=white         -> META:to_move
FACT: status=normal         -> FACT:status
MOVE: legal=20              -> MOVE:legal
CHECKMATE: delivery=queen   -> CHECKMATE:delivery
CHECKMATE: pattern=smothered_mate -> CHECKMATE:pattern:smothered_mate
```

Count and single-attribute tags take their identity from the field key, so changing the value is reported as a change. Multi-valued concepts like mate patterns fold the value into the identity instead — which is exactly why several patterns can sit on one board without colliding.

## Example

```bash
java -jar crtk.jar fen tags --fen "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1"
```

A trimmed slice of the output, a JSON array of tag strings:

```text
"FACT: status=normal"
"META: eval_bucket=clear_white"
"META: to_move=white"
"MOVE: legal=20"
"MOVE: mates=1"
"TACTIC: motif=mate_in_1 side=white move=a1a8"
"KING: shelter=pawns_intact side=white"
"MATERIAL: balance=white_up_exchange"
"MATERIAL: imbalance=queenless"
"ENDGAME: type=queenless"
```

## Validating tags

Every detector is pinned by a fixture so its output can't drift unnoticed. The static fixtures live under `testdata/tags/` as TSV rows:

```text
id<TAB>fen<TAB>must_contain_tags<TAB>must_not_contain_tags
```

The two tag columns hold exact tag strings, separated by semicolons — one asserting what must appear, the other what must not. Run the suite with:

```bash
./scripts/run_regression_suite.sh core
```

## Related pages

- [Command Reference](command-reference.md) — full flag list for `fen tags`, `puzzle tags`, and `record tag-stats`.
- [Example Commands](example-commands.md) — copy-pasteable tagging recipes.
- [Architecture](architecture.md) — the one shared chess core behind the tagger.
- [Datasets](datasets.md) — turning tagged records into ML training data.
