# Support

When crtk misbehaves, the good news is that it almost always misbehaves the same way twice. Determinism means a problem reproduces from one command plus its input, so a report that hands a maintainer that command rarely needs a follow-up. The self-checks below resolve the usual suspects — runtime version, config, protocol TOML, engine path, movegen — on their own, which is why they come before the issue tracker.

## Start Here: Self-Diagnose

Start here, because most of what looks like a bug is an environment or configuration problem these three commands will name for you.

```bash
crtk doctor
crtk config validate
crtk config show
crtk doctor --json
crtk config show --json
```

`crtk doctor` inspects the runtime, configuration, engine protocol files, external engine availability, and local artifacts. By default it tolerates warnings; `--strict` turns them into a non-zero exit, which is what you want in CI:

```bash
crtk doctor --strict --verbose
```

For bug reports, automation, or the Workbench setup card, prefer `crtk doctor --json` and `crtk config show --json`. They return stable `crtk.doctor.v1` and `crtk.config.v1` objects instead of requiring text scraping.

> No `crtk` launcher on your PATH? Replace `crtk ...` with `java -jar crtk.jar ...` (or `java -cp out application.Main ...`). Always tell us which form you used.

## Run the Regression Suite

Run the recommended suite from the repository root before you report anything. A clean pass narrows the search: your build and core are sound, so whatever you hit lives somewhere more specific. A failure here is the opposite of a dead end — it is the single most useful thing you can put in a report.

```bash
./scripts/run_regression_suite.sh recommended
```

The suite compiles with `javac --release 17`, lints, and exercises the core, CLI, and engine paths. When you already know roughly where the trouble is, a narrower target is faster:

| Target | What it runs |
| --- | --- |
| `recommended` | Default suite: build, core, CLI, engine, perft smoke, jar smoke |
| `build` | Compile only |
| `core` | Core chess-engine checks |
| `cli` | CLI command checks |
| `engine` | Engine integration checks |
| `perft-smoke` | Quick perft sanity check |
| `uci` | External UCI engine checks (needs Stockfish on PATH) |
| `ci` | Full continuous-integration suite |

## Targeted Self-Checks

Match the check to the symptom. Each command is deterministic, so its output drops straight into a bug report.

### External UCI engine problems

When `engine analyze`, `engine bestmove`, `engine compare`, `engine threats`, or `puzzle mine` can't get a word out of Stockfish or LC0, the protocol TOML is the usual culprit. Smoke-test it:

```bash
crtk engine uci-smoke --protocol-path config/your-engine.toml --nodes 1 --max-duration 5s --verbose
crtk engine compare --left-protocol config/a.toml --right-protocol config/b.toml --fen "<FEN>"
```

### Move generation, notation, or perft problems

Everything else stands on movegen, so if a move looks wrong, rule out the foundation first:

```bash
crtk engine perft-suite --depth 6 --threads 4
crtk move both --fen "<FEN>"
crtk fen validate --fen "<FEN>"
```

### Native GPU backend problems

A GPU run that quietly falls back to the CPU is the common failure here. Confirm the JNI status, then compare the two paths so you can see whether the native CUDA/ROCm/oneAPI backend actually engaged:

```bash
crtk engine gpu --verbose
crtk engine perft-suite --depth 6 --gpu --split 4
```

### Neural-network and evaluator problems

For evaluator selection, weights, or model-path trouble, drive the backends directly. One expectation to set before you report a discrepancy: crtk's networks are workable evaluators, not bit-exact reproductions of LC0/BT4. The BT4 path is simplified and still experimental, so "the numbers don't match upstream LC0" is the design, not a defect.

```bash
crtk engine eval --fen "<FEN>" --evaluator lc0 --weights models/your-weights
crtk engine static --fen "<FEN>"
crtk fen text --fen "<FEN>"
```

## What to Include in a Bug Report

The fastest report is the one a maintainer can reproduce in a single command. Aim for that. Include:

- The **exact command line** you ran, verbatim (with the area and action, e.g. `crtk engine bestmove ...`).
- The **full input**: the complete FEN string, a PGN snippet, or the shape of the input file (`--input` / `--stdin`).
- The **expected behavior** versus the **actual output**, including the full stack trace (re-run with `--verbose` to capture it).
- Your **Java version** from `java -version`.
- Your **operating system** and version.
- Which **invocation form** you used: the `crtk` launcher, `java -jar crtk.jar`, or `java -cp out application.Main`.
- For UCI engine issues: the **engine path and protocol TOML** contents (`--protocol-path`), plus output from `crtk engine uci-smoke --verbose`.
- Whether **`./scripts/run_regression_suite.sh recommended` passes** on your checkout.
- For GPU issues: the output of `crtk engine gpu --verbose`.

Gathering this once beats trading questions for a week.

## Where to File It

Open issues on the project's GitHub issue tracker. Search first — perft mismatches, engine-protocol questions, and known operational quirks are often already tracked or documented, and the answer may be sitting in a closed thread. ChessRTK is GPL-3.0-only.

## Security and Privacy Notes

crtk runs locally and deterministically, so a good report rarely needs anything sensitive. Before pasting into a public issue:

- Do not share private engine binary paths, proprietary model/weights locations, private dataset paths, or unpublished book manuscripts unless they are genuinely needed and safe to share.
- Replace private paths with equivalent local examples (for example, a public Stockfish path or a sample FEN) wherever possible.

## Where to Look Next

Before filing, check whether the question already has a home:

| Symptom | Page |
| --- | --- |
| Build, compile, or `crtk` launcher setup | [Build and install](build-and-install.md) |
| First-run walkthrough | [Getting started](getting-started.md) |
| Config files and engine protocol TOML | [Configuration](configuration.md) |
| Command syntax and flags | [Command reference](command-reference.md) |
| Known operational issues and workarounds | [Troubleshooting](troubleshooting.md) |
| LC0 CNN evaluator and weights | [LC0](lc0.md) |
| Built-in MCTS and forced-mate prover | [In-house engine](in-house-engine.md) |
| Frequently asked questions | [FAQ](faq.md) |
| Model files and weights | `models/README.md` |
