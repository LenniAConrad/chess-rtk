# CUDA Native Backend

This directory builds the optional CUDA (NVIDIA GPU) implementation of ChessRTK's native acceleration layer. It is a set of JNI shared libraries that the Java core loads at runtime to offload two workloads onto the GPU: **bulk perft node counting** and **OTIS neural-network policy/WDL inference**. (The same `CMakeLists.txt` also builds the experimental LC0 CNN/BT4 and T5 libraries; this page focuses on the perft and OTIS paths described in the brief, with the others summarized at the end.)

Everything here is **optional**. ChessRTK runs entirely on the CPU with no native libraries present; the GPU path is a drop-in accelerator that is auto-detected when available and silently falls back to the deterministic CPU implementation when it is not. The native sources are deliberately free of third-party Java dependencies — they use only JNI plus the CUDA runtime (`cudart`).

For the wider native picture and the AMD/Intel equivalents, see the [ROCm backend](../rocm/README.md) and the [oneAPI backend](../oneapi/README.md). For how GPU support fits into ChessRTK as a whole, see the [LC0 guide](../../docs/lc0.md) and [Build and Install](../../docs/build-and-install.md).

## What this backend accelerates

| Library (Linux name) | Workload | Java package | Driven by |
| --- | --- | --- | --- |
| `libperft_cuda.so` | Split-depth bulk perft (legal-move tree node counting) | `chess.nn.perft.cuda` | `engine perft --gpu`, `engine perft-suite --gpu` |
| `libotis_cuda.so` | OTIS `.bin` policy/WDL inference | `chess.nn.otis.cuda` | `engine eval --otis`, `engine builtin --otis` |
| `liblc0_cuda.so` | LC0 CNN + compact BT4 inference (experimental) | `chess.nn.lc0.cnn.cuda`, `chess.nn.lc0.bt4.cuda` | `engine builtin --lc0`, `engine eval --lc0` |
| `libt5_cuda.so` | T5 tag-to-text decoding | `chess.nn.t5.cuda` | `fen text`, `puzzle text` |

The perft and OTIS backends share their kernels with the other vendors. The perft kernel lives in `../common/perft_gpu_impl.inl` (with the position core in `../common/perft_core.h`); the OTIS kernel lives in `../common/otis_gpu_impl.inl`. Each `*.cu` file in this directory is a thin shim that defines a set of portability macros (`PERFT_GPU_MALLOC`, `BT4_GPU_MEMCPY`, ...) mapping onto `cudaMalloc`/`cudaMemcpy`/etc., then `#include`s the shared `.inl`. The same `.inl` files compile unchanged against ROCm (`hip*`) and a CPU host build, which keeps GPU and CPU results identical by construction.

## How perft offload works

Perft on the GPU is a node-counting accelerator, not an evaluator. The Java driver expands the legal-move tree on the CPU down to a *split depth*, packs every frontier position into fixed-width little-endian `long` words, and hands the batch to `nativeBulkPerft`. Each GPU thread unpacks one frontier position and computes `perft(remainingDepth)` for it; the host sums the per-position counts. A detailed variant (`nativeBulkPerftDetailed`) returns the seven standard perft counters per position (nodes, captures, en passant, castles, promotions, checks, checkmates).

- `--split N` controls how deep the CPU expands before the GPU takes over. A larger split produces more, shallower frontier positions (more parallelism, lower per-thread depth); a smaller split produces fewer, deeper subtrees.
- Raise `--split` when the remaining device depth is too high for a single kernel launch to finish comfortably.

## Prerequisites

- **CMake 3.18 or newer** (required for first-class CUDA language support).
- **CUDA toolkit** providing `nvcc`, `cudart` (and `cublas`, used only by the T5 library).
- **A JDK 17+** so CMake can locate `jni.h` via `find_package(JNI)`.
- **A C++17 toolchain** compatible with your CUDA version.

Linux is the most-tested target. Windows builds work with a Visual Studio generator. macOS is not applicable — modern macOS has no CUDA support.

If CMake cannot find the JNI headers, set `JAVA_HOME` to your JDK root and re-run the configure step. If it cannot find CUDA, ensure `nvcc` is on `PATH` or pass `-DCUDAToolkit_ROOT=/path/to/cuda`.

## Build

### Linux

```bash
cmake -S native/cuda -B native/cuda/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/cuda/build -j
```

Produces (in `native/cuda/build/`):

```text
libperft_cuda.so
libotis_cuda.so
liblc0_cuda.so
libt5_cuda.so
```

### Windows (Visual Studio, multi-config)

```powershell
cmake -S native/cuda -B native/cuda/build -G "Visual Studio 17 2022" -A x64
cmake --build native/cuda/build --config Release
```

Produces `perft_cuda.dll`, `otis_cuda.dll`, `lc0_cuda.dll`, and `t5_cuda.dll` under `native/cuda/build/Release/`.

You only need to build and ship the libraries for the workloads you intend to accelerate; missing libraries simply leave their Java packages on the CPU path.

## How Java loads the libraries

At runtime the Java core resolves each library through `System.loadLibrary(...)` — for example `perft_cuda` and `otis_cuda`. There are two standard ways to make them loadable:

- Point the JVM at the build directory with `-Djava.library.path=native/cuda/build`.
- Copy the built library next to your working directory (each `Support` class also probes the current directory as a fallback).

You can also pin a library by absolute path with an environment variable, which bypasses `java.library.path` entirely:

- `CRTK_PERFT_CUDA_LIB=/abs/path/to/libperft_cuda.so`
- `CRTK_OTIS_CUDA_LIB=/abs/path/to/libotis_cuda.so`

In code, availability is exposed per package via `Support.isAvailable()` and `Support.deviceCount()` (for example `chess.nn.perft.cuda.Support` and `chess.nn.otis.cuda.Support`). A `deviceCount` of `0` means no usable CUDA device was found and the CPU path will be used.

## Using it from the CLI

First confirm the backend is visible. `engine gpu` prints the GPU JNI backend status across all vendors and workloads:

```bash
java -jar crtk.jar engine gpu
java -jar crtk.jar engine gpu --verbose
```

If you built into `native/cuda/build`, make the libraries loadable when launching crtk, e.g. `java -Djava.library.path=native/cuda/build -jar crtk.jar ...`.

### Perft on the GPU

`engine perft --gpu` uses the native perft backend when one is loadable, otherwise it runs the identical CPU implementation. `--split` sets the CPU expansion depth.

```bash
java -Djava.library.path=native/cuda/build -jar crtk.jar engine perft --startpos --depth 6 --gpu --split 3
```

`engine perft-suite --gpu` runs the bundled perft regression suite on the GPU and verifies the counts:

```bash
java -Djava.library.path=native/cuda/build -jar crtk.jar engine perft-suite --depth 6 --gpu --split 3
```

### OTIS inference on the GPU

OTIS evaluation auto-selects CUDA when the library is loadable and a device is present (default `-Dcrtk.otis.backend=auto`):

```bash
java -Djava.library.path=native/cuda/build -jar crtk.jar engine eval --otis --weights path/to/model.bin --startpos
java -Djava.library.path=native/cuda/build -jar crtk.jar engine builtin --otis --weights path/to/model.bin --startpos
```

The native OTIS backend returns the raw policy-head logits; the Java CPU path additionally applies per-legal-move policy bonuses, so GPU and CPU policy outputs are close but not byte-identical for that reason.

## Backend selection properties

| Property / variable | Effect |
| --- | --- |
| `-Dcrtk.perft.backend=auto` | Try CUDA, then any other loaded perft vendor (default) |
| `-Dcrtk.perft.backend=cuda` | Force the CUDA perft backend |
| `CRTK_PERFT_CUDA_LIB=<path>` | Load `perft_cuda` from an explicit path |
| `-Dcrtk.otis.backend=auto` | Use CUDA if available, else CPU (default) |
| `-Dcrtk.otis.backend=cpu` | Force the CPU OTIS path |
| `-Dcrtk.otis.backend=cuda` | Force CUDA; errors out if CUDA cannot initialize |
| `CRTK_OTIS_CUDA_LIB=<path>` | Load `otis_cuda` from an explicit path |

For the experimental LC0/BT4 and T5 libraries the analogous switches are `-Dcrtk.lc0.backend=auto|cpu|cuda` (with `-Dcrtk.lc0.bt4.backend=...` overriding for the BT4 path) and `-Dcrtk.t5.backend=auto|cpu|cuda` (plus `CRTK_T5_CUDA_LIB` and an optional `CRTK_T5_CUDA_DTYPE=fp16|bf16|fp32`).

## Determinism and fidelity

The perft path is exact: GPU and CPU compute the same node counts from the same shared kernel, so `engine perft --gpu` is a pure speedup with no change in results.

The neural backends are usable evaluators but are **not** bit-exact reproductions of upstream networks. The OTIS GPU path differs from the OTIS CPU path in the policy-bonus detail noted above. The LC0 CNN and especially the compact BT4 path are simplified and experimental — do not treat them as bit-equivalent to LC0/BT4 reference outputs. The CPU path is the determinism baseline; treat GPU output as a faster, near-equivalent approximation for the neural workloads.

## Troubleshooting

- **`UnsatisfiedLinkError: no perft_cuda in java.library.path`** (or `otis_cuda`): pass `-Djava.library.path=...` pointing at the directory holding the built library, copy the library into your working directory, or set the matching `CRTK_*_CUDA_LIB` variable.
- **`libcudart.so...: cannot open shared object file`** (Linux): install the CUDA runtime and make it discoverable, often via `LD_LIBRARY_PATH=/usr/local/cuda/lib64`. For the T5 library, the same applies to `libcublas.so`.
- **`cudart64_*.dll was not found`** (Windows): ensure the CUDA runtime DLL directory is on `PATH`.
- **`deviceCount=0` but you expect a GPU**: check the NVIDIA driver and `nvidia-smi`, and confirm `CUDA_VISIBLE_DEVICES` is not masking the device.
- **CMake cannot find CUDA**: put `nvcc` on `PATH` or pass `-DCUDAToolkit_ROOT=/path/to/cuda`.
- **CMake cannot find JNI**: install a full JDK (not just a JRE) and set `JAVA_HOME` to its root.
- **VSCode squiggles on `#include <jni.h>` / CUDA headers**: run the configure step once to generate `native/cuda/build/compile_commands.json`, then reload the editor (this repo points `C_Cpp.default.compileCommands` at it).
