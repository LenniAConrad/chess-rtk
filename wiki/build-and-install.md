# Build & install

## Requirements

- Java 17+ JDK (needs `javac` to build)
- A UCI chess engine (e.g. Stockfish) either on `PATH` or configured via `config/default.engine.toml`

## Debian/Ubuntu packages

Minimal install (build + run):

```bash
sudo apt-get update && sudo apt-get install -y \
  git ca-certificates \
  openjdk-17-jdk \
  stockfish
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
javac -Xlint:all -d out $(find src -name "*.java")
java -cp out testing.CliCommandRegressionTest
java -cp out testing.PositionRegressionTest
java -cp out application.Main doctor
java -cp out application.Main engine uci-smoke --nodes 1 --max-duration 5s
git diff --check
```

Run the full perft validation when changing move generation or Chess960 setup:

```bash
java -cp out application.Main engine perft-suite
```

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
