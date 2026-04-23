# Build & install

## Requirements

- Java 17+ JDK (needs `javac` to build)
- Optional: a UCI chess engine either on `PATH` or configured via
  `config/default.engine.toml` for `engine analyze`, `engine bestmove`,
  `engine threats`, `engine uci-smoke`, and puzzle mining
- Optional: local model weights under `models/` for LC0, NNUE, and T5-backed
  evaluator/text workflows

## Debian/Ubuntu packages

Minimal install (build + run):

```bash
sudo apt-get update && sudo apt-get install -y \
  git ca-certificates \
  openjdk-17-jdk
```

Install a UCI engine only if you want external-engine analysis or mining:

```bash
sudo apt-get install -y stockfish
```

Optional (recommended if you use `./install.sh` and/or build the CUDA JNI backend):

```bash
sudo apt-get install -y \
  curl \
  build-essential \
  cmake \
  nvidia-cuda-toolkit
```

Notes:
- `git` is included so a fresh machine can `git clone` this repo (and `ca-certificates` enables HTTPS downloads).
- `nvidia-cuda-toolkit` is large and only needed to build the optional CUDA JNI backend under `native/cuda/`.

## Build (no Maven/Gradle)

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
```

## Focused regression check

Run the zero-dependency `Position` regression harness after a build:

```bash
java -cp out testing.PositionRegressionTest
```

## Quality checks

The VS Code workspace enables SonarLint automatic analysis and excludes local
artifact directories such as `data/`, `dump/`, `models/`, `out/`, and
`session/`. There is no required SonarQube server for local development.

Use these local checks before committing Java changes:

```bash
javac -Xlint:all --release 17 -d out $(find src -name "*.java")
java -cp out testing.CliCommandRegressionTest
java -cp out testing.PositionRegressionTest
java -cp out testing.CoreMoveGenerationRegressionTest
java -cp out testing.BuiltInEngineRegressionTest
java -cp out application.Main doctor
git diff --check
```

If the workflow depends on a configured external UCI engine, also run:

```bash
java -cp out application.Main engine uci-smoke --nodes 1 --max-duration 5s
```

For rendering and PDF checks on a headless machine, pass the AWT headless flag:

```bash
java -Djava.awt.headless=true -cp out testing.BookRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessPdfRegressionTest
```

Run the full internal perft validation when changing move generation, FEN
validation, SAN, make/undo, or Chess960 setup:

```bash
java -cp out application.Main engine perft-suite
java -cp out application.Main engine perft-suite --depth 6 --threads 4
```

The perft suite uses stored truth values and the Java core move generator. It
does not launch Stockfish or any other external process.

## Run

```bash
java -cp out application.Main help
java -cp out application.Main <command> [options]
```

## Package a runnable JAR (optional)

```bash
jar --create --file crtk.jar --main-class application.Main -C out .
java -jar crtk.jar help
```

## Linux installer (Debian/Ubuntu)

`./install.sh` is a convenience installer that:
- optionally installs OpenJDK 17 and Stockfish via `apt-get`
- optionally downloads LC0 model weights into `models/`
- compiles sources and builds `crtk.jar`
- installs a launcher at `/usr/local/bin/crtk` that runs from this repo
- optionally builds the CUDA JNI backend under `native/cuda/` (if you have the CUDA toolkit)

Downloaded `models/*.bin` files are local artifacts and are ignored by git.

```bash
./install.sh
crtk help
```

Fetch optional model weights without prompting:

```bash
./install.sh --models
```

Skip model weight downloads:

```bash
./install.sh --no-models
```

Skip the CUDA backend build:

```bash
./install.sh --no-cuda
```

Force the CUDA backend build (installs missing CUDA build deps on Debian/Ubuntu):

```bash
./install.sh --cuda
```

## Update

Update the current git checkout with a fast-forward pull and rerun the installer:

```bash
./scripts/update.sh
```

Rebuild and reinstall the current checkout without pulling new commits:

```bash
./scripts/update.sh --no-pull
```

Notes:
- `scripts/update.sh` refuses to pull if the git worktree has local changes.
- Any remaining flags are passed through to `./install.sh`, for example `./scripts/update.sh --no-cuda`.

## Uninstall

Remove the launcher and local build artifacts:

```bash
./scripts/uninstall.sh
```

Also remove data directories created by the app (`dump/`, `session/`):

```bash
./scripts/uninstall.sh --remove-data
```
