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
- `nvidia-cuda-toolkit` is large and only needed to build the optional CUDA JNI backend under `native-cuda/`.

## Build (no Maven/Gradle)

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
```

## Run

```bash
java -cp out application.Main help
java -cp out application.Main <command> [options]
```

## Package a runnable JAR (optional)

```bash
jar --create --file ucicli.jar --main-class application.Main -C out .
java -jar ucicli.jar help
```

## Linux installer (Debian/Ubuntu)

`./install.sh` is a convenience installer that:
- optionally installs OpenJDK 17 and Stockfish via `apt-get`
- compiles sources and builds `ucicli.jar`
- installs a launcher at `/usr/local/bin/ucicli` that runs from this repo
- optionally builds the CUDA JNI backend under `native-cuda/` (if you have the CUDA toolkit)

```bash
./install.sh
ucicli help
```

Skip the CUDA backend build:

```bash
./install.sh --no-cuda
```

Force the CUDA backend build (installs missing CUDA build deps on Debian/Ubuntu):

```bash
./install.sh --cuda
```
