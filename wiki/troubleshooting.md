# Troubleshooting

Most things that go wrong with crtk go wrong in one of seven places: the launcher or `PATH`, the runtime version, an external UCI engine that was never configured, a perft mismatch, a build error, missing model files, or a native GPU backend that failed to load. This page walks each one by symptom and gives the fix. Two commands diagnose nearly all of them in seconds, so start there.

## Start here: two diagnostic commands

Both are read-only and deterministic. They report what crtk actually resolved on this machine, not what you assume it did.

- `crtk doctor` — checks the Java version, the resolved config and engine protocol files, the engine instance count, the output directory, and whether local model artifacts exist. It prints `ok`, `ok-with-warnings`, or an error, then lists each warning.
- `crtk config validate` — validates `config/cli.config.toml` and the referenced engine protocol TOML and reports the first problem it finds.
- `crtk doctor --json` and `crtk config show --json` — emit stable machine-readable objects for scripts, issue reports, and the Workbench setup/health surface.

```bash
crtk doctor
crtk config validate
crtk config show
crtk doctor --json
```

A healthy machine prints something like this. Warnings — a missing optional T5 model, say — are non-fatal: `doctor` reports `ok-with-warnings` and everything else keeps working.

```text
doctor: ok-with-warnings
Java: 21.0.11
Config: /path/to/config/cli.config.toml
Protocol: config/default.engine.toml
Engine instances: 4
Output: dump/
Warnings:
  - Missing model file for t5-model-path: /path/to/models/t5.bin
```

In CI or scripts, `crtk doctor --strict` turns warnings into failures — it exits non-zero the moment one is present. Add `--json` when the caller needs the `crtk.doctor.v1` status, warning/error arrays, and native backend matrix without scraping text. See [Configuration](configuration.md) for what each value means and [Build and Install](build-and-install.md) for installation details.

## Launcher or `PATH` issues

**Symptom:** `crtk: command not found`, or the `crtk` launcher runs an old build.

- Start with `which crtk`. If nothing prints, the wrapper is not on your `PATH`.
- The installer places the launcher for you. Re-run `./install.sh` from the repository root (add `--no-desktop` to skip the desktop entry), then open a new shell so the updated `PATH` takes effect.
- Installing with `./install.sh --no-launcher` skips the wrapper deliberately — invoke the toolkit directly, e.g. `java -jar crtk.jar doctor` from the repo root. Every `crtk <area> <action>` in these docs maps one-to-one to `java -jar crtk.jar <area> <action>`.
- If `crtk` runs but acts stale, check `crtk version`. Rebuild (see "Build errors" below), then verify again.

## Java version errors

**Symptom:** `UnsupportedClassVersionError`, `class file has wrong version`, or crtk refuses to start.

- crtk targets Java 17 and is compiled with `javac --release 17`, so it runs on that release or anything newer — 21 is fine, as the `doctor` output above shows.
- See what you actually have with `java -version`. If it is older than 17, install a 17+ JDK/JRE and make it the default, or point `JAVA_HOME` at it.
- The `Java:` line from `crtk doctor` reports the version crtk resolved. When the launcher and your shell disagree on which runtime they pick, that line is where the disagreement shows.

> A stock JDK is all you need to build and run. No Maven, no Gradle, no third-party dependency to resolve.

## UCI engine not configured

**Symptom:** an `engine` command that needs an external engine fails to start a process, hangs, or reports a missing binary. This affects `engine analyze`, `engine bestmove` (and `bestmove-uci` / `bestmove-san` / `bestmove-both`), `engine threats`, `engine compare`, `engine analyze-batch`, `engine bestmove-batch`, and `engine uci-smoke`, plus any tagging command run with `--analyze`.

- `crtk config validate` reads the engine protocol TOML named by your config and flags a missing or non-executable binary — usually the whole story.
- That protocol TOML names the engine and its `path = ...`. Confirm the path exists and is executable. If the engine lives on your `PATH`, `which stockfish` or `which lc0` tells you where.
- To debug against a specific protocol without touching your default, point one command at it with `--protocol-path /path/to/engine.toml` (short form `-P`).
- Isolate the wiring with the smallest possible search:

```bash
crtk engine uci-smoke -P config/default.engine.toml --max-nodes 1
```

- To take the external engine out of the picture, use commands that never spawn a UCI process: `engine static` and `engine eval` (local evaluators), `engine builtin` / `engine java` (built-in searcher), `engine mate` (forced-mate prover), `move list`, `engine perft`, and `engine perft-suite`. If those run and `engine analyze` does not, the fault is in the external-engine configuration, not the chess core. See [Configuration](configuration.md) and [LC0](lc0.md) for protocol setup.

## Perft mismatches

**Symptom:** `engine perft-suite` reports a failing row, or `engine perft` returns a node count that disagrees with a known reference.

- `engine perft-suite` checks stored truth values against crtk's own move generator — or the native GPU backend when `--gpu` is set. No Stockfish, no external UCI process. A mismatch therefore implicates the core move generation, or on the GPU path, the device backend.
- Re-run the failing position on its own and break it down per root move:

```bash
crtk engine perft --fen "<FEN>" --depth <n> --divide
```

- When a GPU row fails, separate the device backend from the core logic: run the position once with `--gpu --divide --split <n>`, then again without `--gpu`. A correct CPU run alongside a wrong GPU run pins the bug to the native backend.
- After code changes, mismatches usually trace to FEN parsing, Chess960 castling metadata, en-passant handling, SAN/UCI move application, make/undo, or attack detection. One chess core backs every command, so a single regression in any of those surfaces in many places at once. Rule out a malformed input first with `crtk fen validate --fen "<FEN>"`.
- For a fast end-to-end check of the generator's throughput and correctness on the start position, use `crtk engine benchmark --startpos --depth 5`.

## Build errors

**Symptom:** `javac` errors, or the build produces a jar that does not run.

- Build against the Java 17 release target. A bare newer JDK without `--release 17` can throw spurious errors; the supported path is `javac --release 17`. The installer does this for you — re-run `./install.sh` from the repository root to compile and assemble `crtk.jar`.
- There is no `pom.xml` and no `build.gradle`. If you are looking for one, you are on the wrong path: the build is plain `javac` driven by `install.sh`.
- Once it builds, confirm the artifact runs. `java -jar crtk.jar version` prints the version; `crtk doctor` reports `ok` or `ok-with-warnings`.
- The native GPU libraries (CUDA / ROCm / oneAPI) are a separate, optional compile step behind the installer flags below. A failure there leaves the pure-Java build intact, and crtk falls back to CPU on its own. See [Build and Install](build-and-install.md) for the full procedure.

## Model files missing

**Symptom:** a command that needs local neural-network weights warns that default weights are missing and falls back to a classical heuristic, or a T5 command cannot find a model.

Model binaries stay out of git and out of a fresh clone, so a new checkout reports them missing until you fetch them. This is expected, not a fault. `crtk doctor` names each expected-but-missing model in its warnings — the `t5-model-path` line above is one.

- Fetch the default bundled weights in one step:

```bash
./install.sh --models
```

- That pulls the NNUE weights (`models/crtk-halfkp.nnue`), the ChessRTK LC0 CNN `.bin` weights, and the LC0 BT4 `.pb.gz` network into `models/`.
- The LC0 CNN and OTIS evaluators read their weights from `models/` and fall back to the classical heuristic when the files are gone. So on a fresh clone, `engine eval`, `fen display --ablation`, and `fen render --ablation` quietly use the classical backend until you fetch the weights.
- Override any default by naming weights explicitly: `engine builtin --nnue --weights /path/to/file.nnue`, `engine eval --lc0 --weights /path/to/file.bin`, or `engine eval --otis --weights /path/to/file`. Stockfish-format NNUE files work with the NNUE evaluator.
- For T5 natural-language summaries (`fen text`, `puzzle text`), pass `--model /path/to/t5.bin` or set `t5-model-path` in `config/cli.config.toml`.

> crtk's networks are usable evaluators, but the BT4 path is simplified and experimental — not a bit-exact reproduction of upstream LC0/BT4 inference. Read its output as an approximation, not a reference. See [LC0](lc0.md) for details on the network paths.

## GPU backend not found

**Symptom:** `engine perft --gpu`, `engine perft-suite --gpu`, or a GPU-accelerated OTIS evaluation runs on the CPU, or you want to confirm whether the native backend even loaded.

- `crtk engine gpu` prints a per-backend status report. Each line says whether the JNI library loaded and whether a device is available:

```text
LC0 CNN CUDA JNI backend: loaded=yes, available=yes (deviceCount=1)
OTIS CUDA JNI backend: loaded=yes, available=yes (deviceCount=1)
PERFT CUDA JNI backend: loaded=yes, available=yes (deviceCount=1)
PERFT ROCm JNI backend: loaded=no, available=no (deviceCount=0)
```

- `loaded=no` means the native shared library was never found, or could not be linked — you have to build it for your platform. The installer compiles the matching backend: `./install.sh --cuda` (NVIDIA), `./install.sh --rocm` (AMD), or `./install.sh --oneapi` (Intel). Sources live under `native/cuda/`, `native/rocm/`, and `native/oneapi/`.
- `loaded=yes, available=no` means the library linked but found no usable device. That points at the GPU driver and runtime (CUDA / ROCm / oneAPI), not at crtk.
- Built the libraries by hand rather than through the installer? Make them visible at runtime with `-Djava.library.path=...`.
- A missing or broken GPU backend is never fatal — GPU perft and OTIS fall back to the CPU on their own. `crtk engine gpu --verbose` gives more detail. See [Build and Install](build-and-install.md) for native backend build instructions.

## Still stuck?

- `--verbose` (or `-v`) on almost any command swaps the one-line error for a full stack trace.
- `crtk clean` clears the session cache and logs when stale state is in the way; re-run `crtk doctor` afterward.
- The [FAQ](faq.md) answers the common questions, the [Command Reference](command-reference.md) has the exact flags, and [Support](support.md) explains how to report a bug.
