# Book publishing

ChessRTK can produce native vector PDFs for five publishing jobs:

- `book collection`: build a dense puzzle-collection TOML manifest from record JSON/JSONL, with optional interior and cover PDF output.
- `book study`: render richer annotated puzzle studies with hints, analysis text, comments, and figure lists.
- `book pdf`: quick diagram sheets from FEN lists or PGN mainlines.
- `book render`: full puzzle books from JSON/TOML manifests.
- `book cover`: matching paperback, hardcover, or ebook covers from the same manifest.

No LaTeX step is required for these commands.
Legacy aliases `book ilovechess` and `book artofchess` still resolve to
`book collection` and `book study`.

The native book renderer keeps publishing-specific text helpers in
`chess.book.render`. Solution SAN in captions and tables is converted to
figurine algebraic notation, so moves such as `Qxe8+` render with chess
figurines and a multiplication sign for captures.

## Minimal workflow

```bash
mkdir -p dist
crtk book render -i books/puzzles.toml --check
crtk book render -i books/puzzles.toml -o dist/puzzles.pdf
crtk book cover -i books/puzzles.toml --check \
  --pdf dist/puzzles.pdf --binding paperback --interior white-bw
crtk book cover -i books/puzzles.toml -o dist/puzzles-cover.pdf \
  --pdf dist/puzzles.pdf --binding paperback --interior white-bw
```

For reviewer or free-edition copies, generate a traceable watermarked PDF:

```bash
crtk book render -i books/puzzles.toml -o dist/puzzles-review.pdf \
  --watermark-id "ARC reviewer@example.com 2026-04-22"
```

`--watermark-id` implies `--watermark`; it adds page-specific overlay text,
corner marks, and matching PDF metadata.

Pass the rendered interior PDF to `book cover --pdf <path>` so the cover
command can read the actual trim width, trim height, and page count from the
interior file. If `--pdf` is omitted, the cover command falls back to manifest
`paperwidth` / `paperheight`, then manifest `pages`, then an estimate from the
puzzle grid and solution-table cadence. `--pages` still overrides the inferred
page count when you need a manual spine width.

The `--check` flag is a dry run. For `book render`, it validates page geometry,
FEN parsing, and solution moves. For `book cover`, it validates the same
manifest and prints the calculated trim, spine, and full-cover dimensions.

## Puzzle collection workflow

Use `book collection` when your source material is a record JSON/JSONL dump
with a starting `position` and a PV in `analysis`. The command converts the
first PV into move-numbered SAN, writes a TOML manifest, and can optionally
render the interior and cover in one pass.

```bash
crtk book collection -i dump/mate1.json -o books/mate1.book.toml \
  --title "Chess Puzzle Collection" \
  --subtitle "4,000 Mate in 1 Puzzles" \
  --author "Lennart A. Conrad" \
  --time "2026" \
  --location "Shanghai" \
  --pdf-output dist/mate1.pdf \
  --cover-output dist/mate1-cover.pdf \
  --binding paperback \
  --interior white-bw
```

If you omit `--subtitle`, the command falls back to a generic
`"<count> Chess Puzzles"` subtitle. If you omit front-matter paragraphs, the
builder supplies a compact default introduction and leaves the native
how-to-read and afterword fallbacks in place.

## Puzzle study workflow

Use `book study` when you already have richer annotated content and want
to keep per-entry description text, comments, hints, and figure lists.

```bash
crtk book study -i books/puzzle-studies.json \
  --manifest-output books/puzzle-studies.toml \
  -o dist/puzzle-studies.pdf \
  --cover-output dist/puzzle-studies-cover.pdf \
  --title "Chess Puzzle Studies" \
  --subtitle "400 Annotated Studies" \
  --author "Lennart A. Conrad" \
  --binding paperback \
  --interior white-bw
```

This command accepts JSON or TOML input, can write a normalized TOML manifest,
renders the composition-style interior PDF, and can build a matching cover in
one pass. The manifest stays separate from the lean `book render` model on
purpose: `book study` keeps the richer `Composition` fields instead of
collapsing everything into `position + moves`.

Example TOML:

```toml
title = "Chess Puzzle Studies"
subtitle = "Annotated Sample"
author = "ChessRTK"
time = "2026"
location = "Shanghai"
pageSize = "a5"
margin = 42.0
diagramsPerRow = 1
boardPixels = 720
whiteSideDown = false
showFen = false
blurb = ["Annotated positions with guided hints and commentary."]
link = ["example.com/art"]

[[compositions]]
title = "Mate in One"
description = "White to move and mate in one."
analysis = "1. Qh8#."
hintLevel1 = "Look at the back rank."
figureMovesAlgebraic = ["Start", "1. Qh8#"]
figureMovesDetail = ["Initial position", "Mate"]
figureFens = [
  "6k1/5ppp/8/8/8/8/5PPP/6KQ w - - 0 1",
  "7Q/5pkp/8/8/8/8/5PPP/6K1 b - - 0 1",
]
figureArrows = ["h1h8", ""]

[[compositions]]
title = "Punished Weakness"
comment = "Black exploits the loosened kingside dark squares."
analysis = "2... Qh4#"
id = "A2"
figureMovesAlgebraic = ["Final"]
figureFens = ["rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"]
```

Important puzzle-study fields:

- `[[compositions]]` is the repeated annotated entry list.
- `description`, `comment`, `analysis`, and `hintLevel1..4` map directly to the rendered section blocks.
- `figureFens` is the diagram backbone; `figureMovesAlgebraic`, `figureMovesDetail`, and `figureArrows` stay aligned with it.
- `pageSize`, `margin`, `diagramsPerRow`, `boardPixels`, `whiteSideDown`, and `showFen` control the composition-PDF layout.

## Manifest shape

Both JSON and TOML are accepted. TOML is usually easier to maintain:

```toml
title = "Tactical Positions"
subtitle = "100 exercises"
author = "ChessRTK"
time = "2026"
location = "Hangzhou"
language = "English"
pages = 120

paperwidth = 15.24
paperheight = 22.86
innermargin = 2.0
outermargin = 1.5
topmargin = 2.0
bottommargin = 2.0

tablefrequency = 6
puzzlerows = 2
puzzlecolumns = 2

imprint = ["Generated with ChessRTK"]
dedication = ["For careful solvers."]
introduction = ["Solve each position before checking the answer."]
howToRead = ["White is shown at the bottom unless the position requires otherwise."]
blurb = ["A compact set of tactical exercises with full solutions."]
afterword = ["Thanks for reading."]

[[elements]]
position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
moves = "1. e4"
```

Important fields:

- `paperwidth` / `paperheight` are trim dimensions in centimeters.
- `pages` is used by cover generation for spine width.
- `puzzlerows` and `puzzlecolumns` control how many diagrams appear on each puzzle page.
- Each `[[elements]]` entry needs a starting FEN in `position` and a SAN solution line in `moves`.
- `blurb` is reused on the back cover.

## Cover dimensions

`book cover` calculates dimensions from trim size, binding type, interior
paper, and printed page count.

Bindings:

- `paperback`: wraparound back + spine + front cover with 0.125 inch bleed on each outside edge.
- `hardcover`: case-laminate cover with 1.5 cm wrap and 1.0 cm hinge allowance.
- `ebook`: front-cover-only PDF; spine and back cover are omitted.

Interior tokens:

- `white-bw`: white paper, black-and-white printing.
- `cream-bw`: cream paper, black-and-white printing.
- `white-standard-color`: white paper, standard color.
- `white-premium-color`: white paper, premium color.

Spine width is `pages * paper-thickness`. The current constants are:

| Interior | Inches per page |
| --- | ---: |
| `white-bw` | `0.002252` |
| `cream-bw` | `0.0025` |
| `white-standard-color` | `0.002252` |
| `white-premium-color` | `0.002347` |

## Diagram sheets

Use `book pdf` when you want a smaller document instead of a full puzzle book:

```bash
crtk book pdf --fen "<FEN>" -o dist/position.pdf
crtk book pdf -i seeds.txt -o dist/sheet.pdf --title "Training Sheet"
crtk book pdf --pgn games.pgn -o dist/games.pdf --page-size a5 --diagrams-per-row 1
```

Input choices are exclusive: use exactly one of `--fen`, `--input`, or `--pgn`.

## Checks

After editing publishing code or manifests, these checks cover the native PDF
paths:

```bash
java -Djava.awt.headless=true -cp out testing.BookRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessBookCommandRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessBookCoverCommandRegressionTest
java -Djava.awt.headless=true -cp out testing.PuzzleStudyCommandRegressionTest
java -Djava.awt.headless=true -cp out testing.PuzzleCollectionCommandRegressionTest
```

Use `-Djava.awt.headless=true` on machines without a reachable display server,
including CI and many remote shells.

For print-on-demand dimensions, compare the final values printed by
`book cover --check` with the publishing service's own cover calculator
before uploading.

References:

- KDP print cover calculator: https://kdp.amazon.com/en_US/help/topic/G201953020
- KDP paperback submission guidelines: https://kdp.amazon.com/en_US/help/topic/GDTKFJPNQCBTMRV6
