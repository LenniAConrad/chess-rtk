# Universal Chess CLI (ucicli)

A lightweight, dependency-free **Java CLI** to work with chess engines and data — convert, mine, and inspect positions with ease.

---

## Features
- Convert `.record` → compact `.plain`
- Mine **single-solution puzzles** (from PGN, FEN list, or random — Chess960 supported)
- Pretty-print a FEN as an ASCII board

---

## Requirements
- **Java 17+**
- A **UCI-compatible engine** (e.g., Stockfish) on your system or configured manually
- (Optional) `config/book.eco.toml` for opening names

---

## Build
```bash
mkdir -p out
javac -d out $(find src -name "*.java")
````

---

## Usage

```bash
# show help
java -cp out application.Main help

# pretty-print a board
java -cp out application.Main print --fen "<FEN>"

# convert records
java -cp out application.Main convert -i input.record -o output.plain --sidelines

# mine puzzles
java -cp out application.Main mine -i seeds.pgn -o dump/ --engine-instances 4 --max-duration 60s
```

---

## Config, Defaults & Files

By default, the CLI reads configs from the `config/` folder:

* `cli.config.toml` – general settings
* `default.engine.toml` – UCI protocol commands
* `book.eco.toml` – optional opening names (ECO codes)

Outputs go to `dump/`, logs to `session/`. Source code is in `src/`.

---

## License

See `LICENSE.txt`.