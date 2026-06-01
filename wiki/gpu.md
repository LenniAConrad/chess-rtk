# GPU Acceleration

Everything in crtk runs on the CPU, and most of it stays there. GPU acceleration is an **optional** add-on aimed at the two workloads that actually saturate a device: **perft** move-enumeration and **OTIS / LC0 neural-network inference**. It ships as native JNI shared libraries — CUDA for NVIDIA, ROCm for AMD, oneAPI for Intel — loaded at runtime if they happen to be on the path. When a backend is missing or refuses to initialize, crtk **falls back to the CPU** without comment and returns the same answer. You opt in deliberately: `--gpu` for perft, a backend property for the evaluators. The device only ever sees the heavy batches.

What follows: reading backend status with `engine gpu`, running `engine perft --gpu` and `engine perft-suite --gpu`, building the native libraries under `native/cuda`, `native/rocm`, and `native/oneapi`, and how the runtime finds and links them.

> All of this is optional. If you only need the CPU toolkit, skip the page — nothing here is load-bearing for the rest of crtk.

## What GPU acceleration does (and does not) do

The backends move arithmetic, not logic. Results are identical either way: perft node counts match to the digit, and the evaluators run the same model weights regardless of where the matrix multiplies land.

**Accelerated on the GPU when a backend is present:**

- **Perft enumeration** — `engine perft --gpu` and `engine perft-suite --gpu` hand packed subtree batches to the device through the `perft_<vendor>` library.
- **OTIS inference** — `.bin` policy/WDL evaluation on the GPU, auto-selected by the OTIS model.
- **LC0 CNN inference** — `.bin` policy + value inference on the GPU.
- **LC0 BT4 inference** — compact BT4 `.bin` (magic `BT4J`) policy + value inference on the GPU. The BT4 path is simplified and experimental; treat it as a usable evaluator, not a bit-exact LC0 reimplementation.
- **T5 text generation** — the `fen text` / `puzzle text` summary pipeline can offload matrix multiplies (CUDA/ROCm run greedy decoding end-to-end on the device; oneAPI offloads GEMMs).

**Always on the CPU (no GPU path):** legal move generation, make/undo, FEN/SAN/UCI parsing, the built-in MCTS engine (`engine builtin` / `engine java`), the forced-mate prover (`engine mate`), and the classical/NNUE evaluators. External UCI engines such as Stockfish or LC0 manage their own hardware independently of crtk's native backends.

This is what makes the GPU safe to leave on in a test harness. A GPU run of `engine perft` returns the same totals as the CPU run, and `engine perft-suite` checks against the same expected counts whichever path it took. See [Quality and Testing](quality-and-testing.md) for how the perft suite serves as a regression gate.

![ChessRTK position toolbox: one shared chess core feeding evaluation and analysis](../assets/diagrams/crtk-position-toolbox.png)

## Check backend status: `engine gpu`

Run this before anything else. It prints one line per backend across all three vendors, so a single glance tells you what is loaded and what can actually reach a device on this machine.

```bash
crtk engine gpu
```

Each line reports three fields:

- `loaded=yes|no` — whether the native library was found and linked into the JVM.
- `available=yes|no` — whether the runtime could initialize a usable device.
- `deviceCount=N` — number of devices the backend sees.

Example output on a machine with one NVIDIA GPU and CUDA libraries on the path:

```text
LC0 CNN CUDA JNI backend: loaded=yes, available=yes (deviceCount=1)
LC0 BT4 CUDA JNI backend: loaded=yes, available=yes (deviceCount=1)
LC0 CNN ROCm JNI backend: loaded=no, available=no (deviceCount=0)
LC0 BT4 ROCm JNI backend: loaded=no, available=no (deviceCount=0)
LC0 CNN oneAPI JNI backend: loaded=no, available=no (deviceCount=0)
LC0 BT4 oneAPI JNI backend: loaded=no, available=no (deviceCount=0)
OTIS CUDA JNI backend: loaded=yes, available=yes (deviceCount=1)
OTIS ROCm JNI backend: loaded=no, available=no (deviceCount=0)
OTIS oneAPI JNI backend: loaded=no, available=no (deviceCount=0)
PERFT CUDA JNI backend: loaded=yes, available=yes (deviceCount=1)
PERFT ROCm JNI backend: loaded=no, available=no (deviceCount=0)
PERFT oneAPI JNI backend: loaded=no, available=no (deviceCount=0)
```

`loaded=no` means the library never made it onto `java.library.path` (or the vendor-specific path env var was unset) — a placement problem, not a hardware one. `loaded=yes, available=no` is the more interesting failure: the library linked, but it found no usable device. That points at drivers or `deviceCount`, not the build. Add `--verbose` for more detail.

```bash
crtk engine gpu --verbose
```

## GPU perft: `engine perft --gpu` and `engine perft-suite --gpu`

Perft counts the leaf nodes in the legal move tree to a fixed depth — the standard way to check that a move generator is both correct and fast. The GPU path changes only how the work is divided; the count is the same.

The division is a tradeoff you control. crtk expands the tree on the CPU down to a **split depth**, packs the resulting legal positions, and ships them to the device, which counts the remaining subtrees in parallel. `--split N` sets that CPU depth. Each subtree the device receives is one large, independent unit of work — good for the GPU, until a subtree is deep enough to exhaust device memory or stall. When that happens, raise `--split`: more of the tree expands on the CPU, the per-device subtrees shrink, and the batch fits.

| Flag | Meaning |
| --- | --- |
| `--gpu` | Use the native GPU perft backend when available; otherwise fall back to CPU |
| `--split N` | CPU expansion depth before the subtree batch is sent to the device |
| `--depth`, `-d N` | Perft depth |
| `--threads N` | CPU worker threads for root moves (also used during expansion) |
| `--divide`, `--per-move` | Print a per-root-move node table |
| `--format FMT` | Output format: `detail`, `table`, or `stockfish` |

Run perft from the start position to depth 6, expanding three plies on the CPU before the GPU handles the rest:

```bash
crtk engine perft --startpos --depth 6 --gpu --split 3
```

Run perft on a custom position with a per-root-move breakdown:

```bash
crtk engine perft --fen "<FEN>" --depth 6 --gpu --split 3 --divide
```

With no native perft backend loaded, `--gpu` does nothing visible: the same perft runs on the CPU and returns the same totals. That interchangeability is the whole point — it lets the regression suite treat GPU and CPU runs as equivalent.

The suite accepts the same two GPU flags, so you can validate a whole battery of known positions on the device:

```bash
crtk engine perft-suite --depth 6 --gpu --split 3
```

| `engine perft-suite` flag | Meaning |
| --- | --- |
| `--depth`, `-d N` | Depth to validate, 1..6 (default: 6) |
| `--gpu` | Use the native GPU perft backend when available |
| `--split N` | CPU expansion depth for GPU perft |
| `--threads N` | Worker threads across positions |
| `--suite PATH` | Custom tab-delimited suite file |

See [Quality and Testing](quality-and-testing.md) for the suite file format and how perft is used as a determinism gate, and [Command Reference](command-reference.md) for the full option list.

## GPU evaluators: OTIS and LC0

The neural-network backends are chosen by system property rather than CLI flag — the commands stay the same whether the work runs on a GPU or not. The default is `auto`: take a GPU if one is there, otherwise the CPU. So `engine eval --otis` and `engine eval --lc0` are unchanged, as are the OTIS/LC0 paths inside `puzzle mine`, `fen tags`, and `fen text` / `puzzle text`. You change where the math happens without touching how you ask for it.

| Property | Default | Forces | Notes |
| --- | --- | --- | --- |
| `-Dcrtk.lc0.backend` | `auto` | `cpu`, `cuda`, `rocm`, `oneapi` | Shared LC0 CNN backend selector; ROCm aliases `amd`/`hip`, oneAPI alias `intel` |
| `-Dcrtk.lc0.bt4.backend` | inherits LC0 | `auto`, `cpu`, `cuda`, `rocm`, `oneapi` | Optional BT4-specific override |
| `-Dcrtk.otis.backend` | `auto` | `cpu`, `cuda`, `rocm`, `oneapi` | OTIS policy/WDL backend selector |
| `-Dcrtk.t5.backend` | `auto` | `cpu`, `cuda`, `rocm`, `oneapi` | T5 summary pipeline backend selector |

Force OTIS onto a CUDA device for an evaluation:

```bash
java -Dcrtk.otis.backend=cuda -jar crtk.jar engine eval --otis --fen "<FEN>"
```

Pin LC0 CNN inference to the CPU even when a GPU is present:

```bash
java -Dcrtk.lc0.backend=cpu -jar crtk.jar engine eval --lc0 --fen "<FEN>"
```

One expectation to set straight: crtk's neural networks are usable evaluators, not bit-exact reimplementations of upstream LC0/BT4. The GPU and CPU paths agree because they run the same crtk weights — but those weights will not track a stock LC0 binary move-for-move, and the BT4 backend is simplified and experimental on top of that. See [LC0 CNN evaluator](lc0.md) and [T5 summaries](t5.md) for what these models actually deliver.

## Building the native libraries

Each vendor backend lives in its own directory and builds with CMake. There are **no third-party Java dependencies** — JNI plus the vendor runtime, nothing else. Build the one that matches your hardware:

| Vendor | Directory | Runtime needed | Library names (Linux) |
| --- | --- | --- | --- |
| NVIDIA | `native/cuda` | CUDA toolkit (`nvcc`, `cudart`, `cublas`) | `liblc0_cuda.so`, `libotis_cuda.so`, `libt5_cuda.so`, `libperft_cuda.so` |
| AMD | `native/rocm` | ROCm toolkit (HIP / `hipcc`) | `liblc0_rocm.so`, `libotis_rocm.so`, `libt5_rocm.so`, `libperft_rocm.so` |
| Intel | `native/oneapi` | SYCL toolchain (Intel oneAPI `icpx`/`dpcpp` or LLVM `-fsycl`) | `liblc0_oneapi.so`, `libotis_oneapi.so`, `libt5_oneapi.so`, `libperft_oneapi.so` |

All three want CMake 3.18+, a C++17 toolchain, and a full JDK 17+ — a JRE will not do, because the build needs `jni.h`. Linux is the most-tested target. Each directory keeps its own `README.md` with the full build, run, and troubleshooting matrix.

### CUDA (NVIDIA)

```bash
cmake -S native/cuda -B native/cuda/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/cuda/build -j
```

### ROCm (AMD)

```bash
cmake -S native/rocm -B native/rocm/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/rocm/build -j
```

### oneAPI (Intel)

```bash
cmake -S native/oneapi -B native/oneapi/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/oneapi/build -j
```

If your default compiler does not understand `-fsycl`, point CMake at one that does:

```bash
cmake -S native/oneapi -B native/oneapi/build -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_COMPILER=icpx
```

If CMake cannot find JNI, set `JAVA_HOME` to your JDK root and re-run the configure step. On Windows, reach for a multi-config generator (for example `-G "Visual Studio 17 2022" -A x64`) and `--config Release`; outputs land under `build/Release/` with `.dll` extensions.

## How Java loads the native libraries

Each backend loads through `System.loadLibrary`, which means the built libraries have to sit somewhere on the JVM's library path. The usual move is to point `java.library.path` straight at the vendor build directory:

```bash
java -Djava.library.path=native/cuda/build -jar crtk.jar engine gpu
```

Discovery order, from most to least common:

- Add the vendor `build/` directory to `-Djava.library.path`.
- Copy the built library next to where you launch crtk (there is a small fallback that probes the current directory).
- Set the per-backend, per-vendor path env var for an explicit absolute path, for example `CRTK_OTIS_CUDA_LIB`, `CRTK_PERFT_ROCM_LIB`, or `CRTK_T5_ONEAPI_LIB`.

Backend selection at runtime:

- LC0 CNN / BT4, OTIS, and T5 each read their `-Dcrtk.<area>.backend` property (`auto` by default) and auto-select a loaded GPU backend when `auto`.
- Perft uses `-Dcrtk.perft.backend` — `auto` tries CUDA, then ROCm, then oneAPI; you can force a vendor with `cuda`, `rocm`, or `oneapi`.

Once it builds, confirm the whole chain resolves with `engine gpu` — you want `loaded=yes, available=yes`:

```bash
java -Djava.library.path=native/cuda/build -jar crtk.jar engine gpu --verbose
```

## Troubleshooting

| Symptom | Likely cause and fix |
| --- | --- |
| `engine gpu` shows `loaded=no` | Library not on the path. Pass `-Djava.library.path=native/<vendor>/build` or set the per-backend `CRTK_*_LIB` env var |
| `UnsatisfiedLinkError: no <lib> in java.library.path` | Same as above — the named library was not found |
| `loaded=yes, available=no`, `deviceCount=0` | Library linked but no usable device. Check drivers: `nvidia-smi` / `CUDA_VISIBLE_DEVICES` (CUDA), `rocminfo` / `HIP_VISIBLE_DEVICES` (ROCm), `sycl-ls` / `ONEAPI_DEVICE_SELECTOR` (oneAPI) |
| `libcudart.so` / `libcublas.so` not found (Linux) | Add the CUDA lib dir to `LD_LIBRARY_PATH` (often `/usr/local/cuda/lib64`) |
| `libamdhip64.so` / `libhipblas.so` not found (Linux) | Add the ROCm lib dir to `LD_LIBRARY_PATH` (often `/opt/rocm/lib`) |
| `libsycl.so` not found (Linux) | Add the oneAPI runtime lib dir to `LD_LIBRARY_PATH` (often `/opt/intel/oneapi/compiler/latest/linux/lib`) |
| GPU perft slow or out of memory | Raise `--split N` so more of the tree is expanded on the CPU before the device batch |
| CMake cannot find JNI | Install a full JDK (not a JRE) and set `JAVA_HOME` to its root |

A backend that refuses to load is never fatal here — crtk drops to the CPU and carries on. That is why `--gpu` and the `auto` backend setting are safe to leave on by default; the worst case is that they do nothing. For broader environment checks run [`doctor`](troubleshooting.md), and reach for each vendor's `native/<vendor>/README.md` when you need the complete platform notes.

## Related pages

- [Build and Install](build-and-install.md) — building crtk itself with `javac --release 17`
- [Quality and Testing](quality-and-testing.md) — perft and the regression suite
- [LC0 CNN evaluator](lc0.md) — the LC0/BT4 evaluation path and its fidelity caveats
- [T5 summaries](t5.md) — the natural-language pipeline that can use the GPU
- [Command Reference](command-reference.md) — full `engine perft`, `engine perft-suite`, and `engine gpu` options
- [Configuration](configuration.md) — system properties and config values
