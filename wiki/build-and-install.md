# Build and Install

There is no build system to learn here. One `javac` invocation compiles the whole project — no Maven, no Gradle, nothing fetched from the network at build time. From a fresh checkout you are a few commands away from a working `crtk`: install a JDK, compile, optionally package a JAR, optionally run the Linux installer, and confirm with `crtk doctor`. Native GPU backends are the one moving part that lives elsewhere; they are optional and covered in [GPU Backends](gpu.md).

## What you get

- A single shared chess core: legal move generation, make/undo, attack detection, FEN/SAN/UCI handling, Chess960, and perft validation — the same core behind the CLI, the Workbench, and every evaluator.
- A deterministic noun-verb command-line interface (for example `crtk move list`, `crtk engine bestmove`, `crtk fen tags`).
- Optional external UCI engine integration (Stockfish, LC0) and optional local model weights for the built-in evaluators.

The same sources compiled against the same `--release 17` target produce the same classes, and no command leans on hidden global state, so a run today and a run next month agree. See [Getting Started](getting-started.md) for your first commands.

## Requirements

| Requirement | Needed for | Notes |
| --- | --- | --- |
| Java 17+ JDK (with `javac`) | Building from source | The JDK is required because you compile the sources yourself. A JRE alone cannot build. |
| Java 17+ runtime | Running `crtk` | Any 17-or-newer runtime runs the built JAR. |
| A UCI engine (e.g. Stockfish) | `engine analyze`, `engine bestmove`, `engine threats`, `engine compare`, `engine uci-smoke`, `puzzle mine` | Optional. On `PATH` or pointed to via a protocol TOML (`--protocol-path`). See [Configuration](configuration.md). |
| Local model weights under `models/` | NNUE, LC0 CNN, OTIS, and T5 evaluators/text | Optional. Used by `engine eval`, `engine builtin`, `fen text`, and `puzzle text`. |
| Vendor GPU toolchain (CUDA/ROCm/oneAPI) | Native perft and OTIS acceleration | Optional, with automatic CPU fallback. See [GPU Backends](gpu.md). |

A JDK is the only hard dependency. FEN tooling, move generation, perft, tagging, dataset export, PDF publishing — all of it runs with nothing else installed. External engines and model weights buy you the workflows that need them, and nothing more.

### Honest note on neural-network fidelity

The bundled evaluators (NNUE, LC0 CNN, OTIS) are real, deterministic position evaluators, but they are not bit-exact reimplementations of the upstream engines. The BT4 path is the rough one — simplified and still experimental. Read them as research-grade evaluators rather than stand-ins for native LC0 inference; for the strongest play and analysis, point crtk at an external UCI engine. See [LC0 Integration](lc0.md).

## Debian/Ubuntu packages

Minimal install to build and run:

```bash
sudo apt-get update && sudo apt-get install -y \
  git ca-certificates \
  openjdk-17-jdk
```

Add a UCI engine only if you want external-engine analysis or puzzle mining:

```bash
sudo apt-get install -y stockfish
```

Reach for the optional tooling if you plan to run `./install.sh` or build the CUDA JNI backend:

```bash
sudo apt-get install -y \
  curl \
  build-essential \
  cmake \
  nvidia-cuda-toolkit
```

Notes:

- `git` clones the repository on a fresh machine; `ca-certificates` lets the installer fetch model weights over HTTPS.
- `nvidia-cuda-toolkit` is large, and you only need it to build the optional CUDA JNI backend under `native/cuda/`. ROCm/HIP and oneAPI builds bring their own vendor toolchains. See [GPU Backends](gpu.md).

## Build from source

Two lines from the repository root. Make a directory for the classes, then compile every source file into it:

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
```

> Keep `--release 17`. It pins both the language level and the platform API surface, so a newer JDK still emits Java 17-compatible classes that behave the same. Drop the flag and compile against a newer API and you can hit spurious errors that have nothing to do with your code.

Run straight from the compiled classes:

```bash
java -cp out application.Main help
java -cp out application.Main <area> <action> [options]
```

## Package a runnable JAR

Bundle the compiled classes into a self-contained `crtk.jar`:

```bash
jar --create --file crtk.jar --main-class application.Main -C out .
java -jar crtk.jar help
```

The rest of this site writes commands as `crtk`, which is the launcher the installer drops on your `PATH`. Wherever you see it, `java -jar crtk.jar` does exactly the same thing.

## Verify the build

Three commands tell you whether everything is wired up:

```bash
crtk version
crtk doctor
crtk help
```

`crtk version` prints version metadata; add `--json` for a machine-readable object:

```text
crtk 1.0.0
```

```bash
crtk version --json
```

```text
{"name":"ChessRTK","launcher":"crtk","version":"1.0.0","java":"21.0.11"}
```

`crtk doctor` walks the runtime, the config file, the engine protocol, the configured engine instances, the output directory, and the local model artifacts, then summarizes the result as `ok` or `ok-with-warnings`:

```text
doctor: ok-with-warnings
Java: 21.0.11
Config: /path/to/config/cli.config.toml
Protocol: config/default.engine.toml
Engine instances: 4
Output: dump/
Warnings:
  - Missing model file for t5-model-path: .../models/t5.bin
```

A missing optional model is a warning, not an error, because the core CLI runs fine without it. In CI you usually want the opposite stance — any warning should fail the check — so pass `--strict` and `doctor` exits non-zero:

```bash
crtk doctor --strict
```

`crtk help` lists every area and action. Append a command to drill into one:

```bash
crtk help engine bestmove
crtk help fen generate
crtk help --full
```

## Linux installer (Debian/Ubuntu)

If the manual steps above feel like one too many, `./install.sh` collapses them into a single command that takes a fresh checkout to a fully wired-up install. It:

- optionally installs OpenJDK 17 and Stockfish via `apt-get`
- optionally downloads NNUE, LC0 CNN, and official LC0 BT4 model artifacts into `models/`
- compiles the sources with `javac --release 17` and builds `crtk.jar`
- installs a launcher at `/usr/local/bin/crtk` that runs from this repository
- installs a `ChessRTK Workbench` desktop entry that launches the Swing workbench (`crtk workbench`) without opening a terminal
- optionally builds the CUDA, ROCm/HIP, and oneAPI JNI backends when their vendor toolchains are present

Any `models/*.bin`, `models/*.pb.gz`, and `models/*.nnue` files it downloads are local artifacts, and git ignores them.

Run the installer, then verify:

```bash
./install.sh
crtk help
```

After install, open `ChessRTK Workbench` from your applications menu, or launch the same desktop app from a shell:

```bash
crtk workbench
```

The Workbench gives you a board, play-vs-engine, command forms, batch jobs, dataset and publishing previews, puzzles, and neural-network visualizers. See [Workbench](workbench.md).

### Installer flags

Left alone, the installer detects what toolchains are present and acts on them. These flags take that decision out of its hands.

| Flag | Effect |
| --- | --- |
| `--models` (alias `--fetch-models`) | Download optional model weights without prompting |
| `--no-models` (alias `--no-fetch-models`) | Skip model weight downloads |
| `--cuda` / `--rocm` (`--amd`) / `--oneapi` (`--intel`) | Force a native GPU backend build |
| `--require-cuda` / `--require-rocm` / `--require-oneapi` | Fail the install if that backend cannot be built |
| `--no-cuda` / `--no-rocm` / `--no-oneapi` | Skip that native GPU backend build |
| `--no-launcher` | Skip installing the `/usr/local/bin/crtk` launcher |
| `--no-desktop` (alias `--no-app-launcher`) | Skip the desktop app entry |
| `-h`, `--help` | Show installer usage |

Common combinations:

```bash
./install.sh --models
./install.sh --no-models
./install.sh --no-cuda --no-rocm --no-oneapi
./install.sh --no-desktop
./install.sh --cuda
```

None of the native GPU backends are required — build none of them and perft and OTIS quietly run on the CPU instead. See [GPU Backends](gpu.md) for `engine gpu`, `engine perft --gpu`, and the toolchain requirements.

## Update

Fast-forward an existing checkout and rerun the installer in one step:

```bash
./scripts/update.sh
```

Rebuild and reinstall the current checkout without pulling new commits:

```bash
./scripts/update.sh --no-pull
```

Notes:

- `scripts/update.sh` refuses to pull when the worktree has local changes, so an in-progress edit is never clobbered.
- Anything it doesn't recognize passes straight through to `./install.sh` — for example `./scripts/update.sh --no-cuda`.

## Uninstall

Remove the launcher, the desktop entry, and local build artifacts (`out/`, `crtk.jar`, `native/*/build/`):

```bash
./scripts/uninstall.sh
```

Useful flags:

| Flag | Effect |
| --- | --- |
| `--remove-data` (alias `--all`) | Also remove app-created data directories (`dump/`, plus legacy `session/` if present) |
| `--keep-build` | Keep build artifacts (`out/`, `crtk.jar`, native build dirs) |
| `--keep-launcher` | Keep `/usr/local/bin/crtk` |
| `--keep-desktop` | Keep the `ChessRTK Workbench` desktop entry |
| `-y`, `--yes` | Assume yes for prompts |

```bash
./scripts/uninstall.sh --keep-desktop
./scripts/uninstall.sh --remove-data
```

## Regression checks after a build

The same runner CI uses is yours to run locally, which is the point — what passes on your machine passes in the pipeline. From the repository root:

```bash
./scripts/run_regression_suite.sh recommended
```

Focused suites you can run individually:

```bash
./scripts/run_regression_suite.sh build
./scripts/run_regression_suite.sh lint
./scripts/run_regression_suite.sh core
./scripts/run_regression_suite.sh cli
./scripts/run_regression_suite.sh uci
./scripts/run_regression_suite.sh book
./scripts/run_regression_suite.sh perft-smoke
./scripts/run_regression_suite.sh release
```

The perft smoke suite checks the in-process core move generator against stored truth values; it never launches Stockfish or any other external UCI process. Tune its depth and parallelism with environment variables:

```bash
CRTK_PERFT_SUITE_DEPTH=6 CRTK_PERFT_THREADS=4 ./scripts/run_regression_suite.sh perft-smoke
```

When a change you made depends on a configured external UCI engine, make the engine suite demand one rather than skip silently:

```bash
CRTK_REQUIRE_STOCKFISH=1 ./scripts/run_regression_suite.sh uci
```

Any time you touch move generation, FEN validation, SAN, make/undo, or Chess960 setup, run the full internal perft validation — that is where subtle correctness bugs hide. You can also exercise the perft path directly through the CLI:

```bash
crtk engine perft --startpos --depth 5
crtk engine perft-suite --depth 6 --threads 4
```

Adding `--gpu` to `engine perft` / `engine perft-suite` (or the matching runner environment) routes the work to the optional native backend when one is built, and still launches no external UCI process. See [GPU Backends](gpu.md).

## Next steps

- [Getting Started](getting-started.md) — your first commands and core workflows.
- [Configuration](configuration.md) — config files, engine protocols, and model paths.
- [Command Reference](command-reference.md) — every area and action.
- [GPU Backends](gpu.md) — native CUDA/ROCm/oneAPI perft and OTIS acceleration.
- [Troubleshooting](troubleshooting.md) — diagnosing build and runtime issues.
