# Development notes

## Project shape

- `src/application/` — CLI entry point, config loading, session/log plumbing
  - `application.Main`: subcommand parsing + dispatch
  - `application.Config`: loads `config/cli.config.toml` (auto-seeded if missing)
- `src/chess/` — chess core, UCI protocol, mining pipeline, render/display helpers
  - `chess.uci`: engine protocol + analysis parsing + Filter DSL
  - `chess.io`: converters, readers/writers, dataset exporters
  - `chess.eval`: evaluation backend used by `display` (LC0 when available; fallback to classical)
  - `chess.lc0`: pure-Java LC0 forward pass (+ optional `native-cuda/` backend)

## Adding/changing CLI commands

1. Add a new `case` in `application.Main.main(...)`.
2. Implement a `runXxx(Argv a)` handler (use `utility.Argv` for parsing).
3. Update the help text in `application.Main.help()`.
4. Update docs:
   - `wiki/command-reference.md`
   - `wiki/example-commands.md`

## Docs policy

The root `README.md` should stay as a quickstart. Longer explanations live under `wiki/`.

