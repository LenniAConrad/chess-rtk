# Releasing ChessRTK

A release is one runnable jar, a thin launcher, and — if you want the GPU — a native library beside it. There is **no Maven or Gradle**: `javac --release 17`, `jar`, and CMake produce every artifact, full stop. That austerity is the point. The same sources through the same three tools yield the same `crtk` binary, which is what lets the packager ship checksums and mean them.

This page is for cutting a release. If you are setting up a development machine instead, start with [Build and Install](build-and-install.md); for the end-user install story, see [Getting Started](getting-started.md).

## What ships in a release

A release is a self-contained directory, tarred and gzipped. The Linux CUDA packager stages this layout:

| Item | Path in bundle | Notes |
| --- | --- | --- |
| Runnable jar | `crtk.jar` | Pure Java 17; the entire CLI and Swing Workbench |
| Launcher | `crtk` | Bash wrapper that runs `java -jar crtk.jar "$@"` |
| CUDA backend | `lib/liblc0_cuda.so` | Native GPU library; CPU fallback if absent |
| License | `LICENSE.txt` | GPL-3.0-only; always bundled |
| Readme | `README.md` | Project overview |
| Config | `config/` | Default TOML configuration |
| Docs | `wiki/` | The Markdown documentation set |
| Models (optional) | `models/` | Only with `--include-models` |

When `lib/liblc0_cuda.so` is present, the launcher adds `-Djava.library.path=lib` on its own, so an extracted release finds the GPU without any further wiring. Model weights stay out by default: they are large, and they live in a separate model repository.

> One honest note on the networks. The bundled evaluators (NNUE, LC0 CNN, OTIS, T5) are usable, not bit-exact reimplementations of their upstream engines. BT4 in particular is simplified and experimental. No release claims LC0 or BT4 parity, and you should not read one into it.

## Versioning

The `version` command is the single source of truth. Release checks and scripts read it, never a constant pasted somewhere else:

```bash
crtk version
```

```text
crtk 1.0.0
```

For CI and release tooling, ask for JSON. It also reports the runtime it is running on:

```bash
crtk version --json
```

```text
{"name":"ChessRTK","launcher":"crtk","version":"1.0.0","java":"21.0.11"}
```

`crtk version` accepts these flags:

| Flag | Effect |
| --- | --- |
| `--json` | Emit one JSON object |
| `--jsonl` | Emit one JSON object line |
| `--no-header` | Accepted for script-friendly consistency |
| `--quiet` | Suppress non-row chatter where supported |

Artifacts are tagged `vX.Y.Z`. The packager takes the version as its own argument (see below); omit it and it falls back to `git describe --tags --always`, then to a UTC date stamp outside a checkout. The one rule when cutting a release: the git tag, the bundle filename, and `crtk version` must agree. A mismatch there is the bug nobody notices until a user reports it.

The Java constant behind `crtk version`, the text/JSON command output, and the
repository metadata in `package.json` are checked together by
`CLICommandRegressionTest`, so release metadata drift fails in the normal CLI
suite instead of waiting for a packaging run.

## Build the runnable jar

Everything else hangs off the jar, so build it through the regression suite — the same path CI takes, which is the point of building it that way locally:

```bash
./scripts/run_regression_suite.sh jar
```

That compiles every source with `javac --release 17`, packages `crtk.jar` under main class `application.Main`, and runs a smoke check (`crtk --help`, plus `workbench`/`gui` help). The two underlying commands, if you ever want them by hand:

```bash
find src -name '*.java' -print0 | xargs -0 javac --release 17 -d out
rm -rf out/schemas && cp -R schemas out/schemas
jar --create --file crtk.jar --main-class application.Main -C out .
```

`--release 17` is not optional. It pins both the bytecode target and the standard-library surface the compiler will admit, which is what makes the build reproducible across JDK versions — you can compile on a newer JDK and still ship an artifact that only depends on 17.

## Build the Linux self-contained runtime bundle

For a CPU-only Linux bundle that does not require a system JDK/JRE at runtime, use the jlink/jpackage script. It still compiles with `javac --release 17` and packages `crtk.jar` first; the bundled runtime is only a release artifact layered on top of that jar.

```bash
scripts/make_runtime_linux.sh --version v1.0.0
```

Prerequisites are the stock JDK packaging tools: `javac`, `jar`, `jdeps`, `jlink`, and `jpackage`. The script derives the Java module set from `crtk.jar`, adds TLS/locale support, writes a jpackage app image, and adds a root `./crtk` launcher that changes into the bundle directory before dispatching. That keeps the relative `config/`, `wiki/`, and `models/README.md` paths consistent with the normal checkout launcher.

Outputs land under `dist/`:

| Output | Description |
| --- | --- |
| `dist/crtk-<version>-linux-x86_64-runtime/` | Staged self-contained app image |
| `dist/crtk-<version>-linux-x86_64-runtime.tar.gz` | Portable runtime tarball |
| `dist/crtk-<version>-linux-x86_64-runtime.sha256` | SHA-256 checksum file for the tarball |

The script smoke-tests the produced root launcher with an empty `PATH`:

```bash
env -i HOME="$HOME" PATH="" dist/crtk-v1.0.0-linux-x86_64-runtime/crtk version
```

If that prints the expected version, the app is using the bundled runtime rather than a `java` binary from the host. This is still Linux-only groundwork; Windows/macOS launchers and installers remain separate release work.

## Build the Linux (x86_64) + CUDA release artifact

One script does the whole pass: `scripts/make_release_linux_cuda.sh` builds the jar, builds the CUDA JNI library, stages the bundle, and writes the tarball and checksums.

Prerequisites:

- A Java 17+ JDK on `PATH` (`javac`, `jar`)
- CMake 3.18+ (for CUDA language support)
- CUDA toolkit (`nvcc`)
- An NVIDIA driver at runtime to actually use the GPU

Build and package a tagged release:

```bash
scripts/make_release_linux_cuda.sh --version v1.0.0
```

To fold local model weights into the bundle (gitignored, and absent from a normal release):

```bash
scripts/make_release_linux_cuda.sh --version v1.0.0 --include-models
```

Drop `--version` and it derives one from `git describe --tags --always`. Miss any of `javac`, `jar`, `cmake`, or `nvcc` and it fails early and says which — the packager will not quietly produce a CPU-only bundle. A release labeled CUDA should contain CUDA. The graceful CPU fallback belongs to `install.sh`, not here.

Outputs land under `dist/`:

| Output | Description |
| --- | --- |
| `dist/crtk-<version>-linux-x86_64-cuda/` | Staged bundle directory |
| `dist/crtk-<version>-linux-x86_64-cuda.tar.gz` | The release tarball |
| `dist/SHA256SUMS` | SHA-256 checksum of the tarball |

### Smoke-test the artifact

From the extracted directory, three commands tell you the launcher resolved and the GPU path is live:

```bash
./crtk version
./crtk help
./crtk engine gpu
```

`crtk engine gpu` lists the native backends it found and says whether the CUDA library actually loaded; if it did not, crtk falls back to the CPU path without complaint. For what the accelerated commands look like (`engine perft --gpu`, OTIS evaluation), see [GPU Backends](gpu.md) and the [Command Reference](command-reference.md).

## Other native backends

The packager only ships CUDA, but two more backends exist: ROCm for AMD and oneAPI for Intel, under `native/rocm/` and `native/oneapi/`. Those are `install.sh`'s job (see below), not the packager's. All three are optional, and the design assumes none of them. With no native library loaded, every command still runs on the CPU. What the backends buy you is speed on `engine perft` / `engine perft-suite` and OTIS evaluation — and they all defer to the same in-process chess core, so they accelerate the work without ever disagreeing about the answer.

## Pre-release checklist

Before you tag, run the full suite once. It builds, lints, and exercises core movegen, the CLI, engines, UCI, book/PDF publishing, perft, and a jar smoke test:

```bash
./scripts/run_regression_suite.sh recommended
```

If the release touched move generation or notation, go deeper on perft — that is where off-by-one bugs hide:

```bash
CRTK_PERFT_SUITE_DEPTH=6 CRTK_PERFT_THREADS=4 ./scripts/run_regression_suite.sh perft-smoke
```

You can also run the suites one at a time: `build`, `lint`, `core`, `cli`, `engine`, `uci`, `book`, `docs`, `perft-smoke`, and `jar`. The `ci` suite adds a full docs-site rebuild on top. Then walk the checklist:

1. Confirm a clean working tree: `git status --short`.
2. Run `./scripts/run_regression_suite.sh recommended` and ensure it passes.
3. Update docs that changed with the release — `README.md`, [Command Reference](command-reference.md), [Build and Install](build-and-install.md), and any new-feature pages.
4. Confirm `crtk version` reports the version you intend to tag.
5. Verify the release will bundle `LICENSE.txt` (the packager always copies it).

## GitHub release workflow

1. Complete the pre-release checklist above.
2. Tag and push the version: `git tag -a v1.0.0 -m "v1.0.0"` then `git push origin v1.0.0`.
3. Build the artifact: `scripts/make_release_linux_cuda.sh --version v1.0.0`.
4. Create a GitHub Release for tag `v1.0.0` and attach `dist/crtk-v1.0.0-linux-x86_64-cuda.tar.gz` and `dist/SHA256SUMS`.

## Install, update, and uninstall scripts

Most users run crtk straight from a checkout via the repo's helper scripts. [Build and Install](build-and-install.md) documents them in full; what follows is the maintainer's view — exactly what each script does, and what it refuses to do.

### `install.sh`

`./install.sh` builds crtk from the current checkout and wires it into the system. It:

- Checks for Java 17+ and `javac`, offering to install OpenJDK 17 if missing.
- Optionally installs Stockfish for UCI-backed analysis.
- Optionally downloads model weights into `models/` (prompted, or forced with `--models` / skipped with `--no-models`).
- Compiles sources with `javac --release 17` and packages `crtk.jar`.
- Builds optional native backends and installs a launcher at `/usr/local/bin/crtk` plus a "ChessRTK Workbench" desktop entry.

Backend selection flags:

| Flag | Effect |
| --- | --- |
| `--cuda` / `--no-cuda` / `--require-cuda` | Force, skip, or require the CUDA backend |
| `--rocm` / `--no-rocm` / `--require-rocm` | Force, skip, or require the ROCm (AMD) backend |
| `--oneapi` / `--no-oneapi` / `--require-oneapi` | Force, skip, or require the oneAPI (Intel) backend |
| `--models` / `--no-models` | Force or skip model-weight download |
| `--no-launcher` | Do not install `/usr/local/bin/crtk` |
| `--no-desktop` | Do not install the Workbench desktop entry |

`--amd` and `--intel` are aliases for `--rocm` and `--oneapi`. The `--require-*` flags are the only ones that turn a failed backend into a non-zero exit; everywhere else a backend that won't build just drops you to a CPU-only install. The installed launcher also turns on Java2D OpenGL acceleration for the Workbench and points `java.library.path` at whatever backends did build.

### `update.sh`

`./scripts/update.sh` fast-forwards the checkout and reruns the installer:

- Refuses to pull on a dirty worktree or a detached HEAD — commit or stash first.
- Runs `git pull --ff-only` on the current branch, then re-executes `./install.sh`.
- `--no-pull` skips the pull and just rebuilds the current checkout.
- Any other arguments are passed straight through to `install.sh`, e.g. `./scripts/update.sh --no-cuda` or `./scripts/update.sh --rocm --no-launcher`.

### `uninstall.sh`

`./scripts/uninstall.sh` undoes what the installer created, and its guardrails refuse to touch anything outside the repo:

- Removes build artifacts by default: `out/`, `crtk.jar`, and `native/*/build/`.
- Removes the `/usr/local/bin/crtk` launcher and the Workbench desktop entry.
- `--all` / `--remove-data` additionally removes the `dump/` and `session/` data directories (off by default).
- `--keep-build`, `--keep-launcher`, and `--keep-desktop` preserve those pieces.
- `-y` / `--yes` assumes yes for all prompts.

It never removes system packages. OpenJDK, Stockfish, CUDA, ROCm, and oneAPI stay exactly where they were — uninstalling crtk should not uninstall your toolchain.

## Reproducibility notes

- One toolchain — `javac --release 17` and `jar`. No build framework, no plugin graph, no lockfile to drift out from under you.
- One chess core. The same legal move generation, make/undo, FEN/SAN/UCI, and Chess960 logic backs the CLI, the Workbench, and the native perft backends, so the three surfaces cannot quietly disagree.
- Deterministic output. The noun-verb commands return stable results, which is the precondition for everything else: it is why the packager can ship checksums and why `run_regression_suite.sh` can assert exact answers rather than approximate ones.
- Check a download with `sha256sum -c SHA256SUMS` from inside the extracted `dist/` directory.

See also: [Build and Install](build-and-install.md), [GPU Backends](gpu.md), [Quality and Testing](quality-and-testing.md), and the [Command Reference](command-reference.md).
