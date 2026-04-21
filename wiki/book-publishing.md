# Book publishing

ChessRTK can produce native vector PDFs for three publishing jobs:

- `book pdf`: quick diagram sheets from FEN lists or PGN mainlines.
- `book render`: full puzzle books from JSON/TOML manifests.
- `book cover`: matching paperback, hardcover, or ebook covers from the same manifest.

No LaTeX step is required for these commands.

## Minimal workflow

```bash
mkdir -p dist
crtk book render -i books/puzzles.toml --check
crtk book render -i books/puzzles.toml -o dist/puzzles.pdf
crtk book cover -i books/puzzles.toml --check \
  --binding paperback --interior white-bw --pages 120
crtk book cover -i books/puzzles.toml -o dist/puzzles-cover.pdf \
  --binding paperback --interior white-bw --pages 120
```

Use `--pages` for the final printed page count after the interior PDF has been
rendered. If omitted, the cover command uses `pages` from the manifest; if that
is absent, it estimates from the puzzle grid and solution-table cadence.

The `--check` flag is a dry run. For `book render`, it validates page geometry,
FEN parsing, and solution moves. For `book cover`, it validates the same
manifest and prints the calculated trim, spine, and full-cover dimensions.

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
java -cp out testing.BookRegressionTest
java -cp out testing.ChessBookCommandRegressionTest
java -cp out testing.ChessBookCoverCommandRegressionTest
```

For print-on-demand dimensions, compare the final values printed by
`book cover --check` with the publishing service's own cover calculator
before uploading.

References:

- KDP print cover calculator: https://kdp.amazon.com/en_US/help/topic/G201953020
- KDP paperback submission guidelines: https://kdp.amazon.com/en_US/help/topic/GDTKFJPNQCBTMRV6
