# ROCm/HIP Native Backend

This directory holds the **optional ROCm/HIP backend** for ChessRTK ("crtk"): a small set of JNI shared libraries that offload work to AMD GPUs. The libraries are entirely optional — crtk runs fully on the CPU without them. When a library and a compatible AMD device are present, crtk auto-detects them and uses the GPU for two performance paths in particular: **bulk perft** (`engine perft --gpu`, `engine perft-suite --gpu`) and **OTIS** policy/WDL inference. The same source tree also builds GPU paths for the LC0 CNN and BT4 evaluators and the T5 tag-to-text decoder. Every GPU path falls back to deterministic CPU code when ROCm is unavailable, so nothing here changes results — it only changes throughput.

> The neural-network paths (LC0 CNN, BT4, OTIS, T5) are usable evaluators, not bit-exact replicas of upstream engines. The BT4 path in particular is simplified and experimental. The perft path is exact node counting and is verified against the CPU core.

## What this backend provides

This directory has **no third-party Java dependencies**. It links only against JNI and the ROCm HIP runtime. Each `.hip` file is a thin shim that maps generic GPU macros (alloc, memcpy, synchronize, device count) onto HIP calls and then includes a shared implementation from `../common/`. The actual kernels are written once and compiled for every vendor backend (CUDA, ROCm, oneAPI), which keeps the GPU math identical across vendors.

| Library (Linux) | Java backend package | Purpose | Shared kernel source |
| --- | --- | --- | --- |
| `libperft_rocm.so` | `chess.nn.perft.rocm` | Split-depth bulk perft node counting | `../common/perft_gpu_impl.inl`, `../common/perft_core.h` |
| `libotis_rocm.so` | `chess.nn.otis.rocm` | OTIS policy/WDL inference | `../common/otis_gpu_impl.inl` |
| `liblc0_rocm.so` | `chess.nn.lc0.cnn.rocm`, `chess.nn.lc0.bt4.rocm` | LC0 CNN and BT4 policy+value inference | `../common/lc0_bt4_gpu_impl.inl` |
| `libt5_rocm.so` | `chess.nn.t5.rocm` | T5 greedy decoding for natural-language summaries | (in `t5_rocm_jni.hip`) |

The brief focus of this page is the **perft** and **OTIS** paths; the LC0 and T5 libraries build from the same `CMakeLists.txt` and load the same way.

## Prerequisites

- A **C++17 toolchain**.
- The **ROCm toolkit**, which provides HIP and the `hipcc` compiler/runtime. The libraries link against `HIP::device`; the T5 library additionally links against `hipblas`.
- **CMake 3.18+** (required for first-class HIP language support).
- A full **Java 17+ JDK** (not just a JRE) so CMake can find `jni.h` via `find_package(JNI)`.

Linux is the primary and most-tested target for ROCm. Windows and macOS are not typical ROCm platforms.

## Build

From the repository root:

```bash
cmake -S native/rocm -B native/rocm/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/rocm/build -j
```

If CMake cannot locate HIP/ROCm, make sure `hipcc` is on `PATH`, or pass an explicit prefix:

```bash
cmake -S native/rocm -B native/rocm/build -DCMAKE_BUILD_TYPE=Release -DCMAKE_PREFIX_PATH=/opt/rocm
```

If CMake cannot find JNI, set `JAVA_HOME` to your JDK root and re-run the configure step.

### Build output

The build produces these shared libraries in `native/rocm/build/`:

- `libperft_rocm.so`
- `libotis_rocm.so`
- `liblc0_rocm.so`
- `libt5_rocm.so`

The configure step also writes `native/rocm/build/compile_commands.json` (handy for editor/clangd integration with the HIP and JNI headers).

## Loading the libraries from Java

Each library is loaded lazily by its `Support` class via `System.loadLibrary`. crtk finds it through one of:

- `-Djava.library.path=native/rocm/build` pointing at the directory that holds the built libraries.
- The current working directory (a small fallback in the loader checks there).
- An explicit absolute path in an environment variable: `CRTK_PERFT_ROCM_LIB` for perft, `CRTK_OTIS_ROCM_LIB` for OTIS, `CRTK_T5_ROCM_LIB` for T5.

To confirm what crtk sees at runtime, run the GPU status command:

```bash
java -jar crtk.jar -Djava.library.path=native/rocm/build engine gpu
```

It prints one line per vendor backend. A working ROCm install looks like:

```text
PERFT ROCm JNI backend: loaded=yes, available=yes (deviceCount=1)
OTIS ROCm JNI backend: loaded=yes, available=yes (deviceCount=1)
```

When the library is missing or no AMD device is visible, the same command reports `loaded=no, available=no (deviceCount=0)` and crtk silently uses the CPU.

## Using the GPU paths

### Bulk perft

`engine perft` and `engine perft-suite` accept `--gpu` to request the native backend, plus `--split` to control how deep the CPU expands the move tree before handing batched leaf positions to the device. Raise `--split` if the remaining device depth is too high for one launch.

```bash
java -jar crtk.jar -Djava.library.path=native/rocm/build engine perft --startpos --depth 6 --gpu --split 3
```

```bash
java -jar crtk.jar -Djava.library.path=native/rocm/build engine perft-suite --depth 6 --gpu --split 3
```

If `--gpu` is requested but no native perft backend is available, crtk falls back to the deterministic CPU perft. Node counts are identical either way.

### OTIS inference

OTIS evaluation is available through `engine eval` and `engine analyze` with the `otis` evaluator. The backend is selected automatically when a device is present:

```bash
java -jar crtk.jar -Djava.library.path=native/rocm/build engine eval --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --otis
```

## Backend selection (system properties)

Each GPU path reads a `-D` system property to choose its backend. The default is `auto`, which tries available vendor backends (CUDA, then ROCm, then oneAPI) and otherwise uses the CPU.

| Path | Property | Values |
| --- | --- | --- |
| Perft | `crtk.perft.backend` | `auto`, `rocm` (aliases `amd`, `hip`), and other vendors |
| OTIS | `crtk.otis.backend` | `auto`, `cpu`, `rocm` (aliases `amd`, `hip`) |
| LC0 CNN | `crtk.lc0.backend` | `auto`, `cpu`, `rocm` (aliases `amd`, `hip`) |
| LC0 BT4 | `crtk.lc0.bt4.backend` | `auto`, `cpu`, `rocm` (aliases `amd`, `hip`) |
| T5 | `crtk.t5.backend` | `auto`, `cpu`, `rocm` (aliases `amd`, `hip`) |

Force the ROCm OTIS backend (and fail loudly if it cannot initialize) like this:

```bash
java -jar crtk.jar -Djava.library.path=native/rocm/build -Dcrtk.otis.backend=rocm engine eval --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --otis
```

In code, the capability checks are `chess.nn.perft.rocm.Support.isAvailable()` / `.deviceCount()` and the matching `Support` classes under `chess.nn.otis.rocm`, `chess.nn.lc0.cnn.rocm`, `chess.nn.lc0.bt4.rocm`, and `chess.nn.t5.rocm`. `isAvailable()` returns `true` only when the library loaded *and* a device is visible.

## Troubleshooting

- **`UnsatisfiedLinkError: no perft_rocm in java.library.path`** — pass `-Djava.library.path=native/rocm/build`, copy the library into your working directory, or set `CRTK_PERFT_ROCM_LIB` to the absolute `.so` path. The same applies to `otis_rocm` (`CRTK_OTIS_ROCM_LIB`) and `t5_rocm` (`CRTK_T5_ROCM_LIB`).
- **CMake cannot find HIP/ROCm** — ensure `hipcc` is on `PATH`, or pass `-DCMAKE_PREFIX_PATH=/opt/rocm`.
- **CMake cannot find JNI** — install a full JDK (not a JRE) and set `JAVA_HOME` to the JDK root.
- **`libamdhip64.so ...: cannot open shared object file`** — the ROCm runtime is not discoverable; add it to the loader path, e.g. `LD_LIBRARY_PATH=/opt/rocm/lib`.
- **`libhipblas.so ...: cannot open shared object file`** — ROCm BLAS (needed by the T5 library) is missing or off `LD_LIBRARY_PATH`.
- **`deviceCount=0` but you expect a GPU** — check the AMD driver install, run `rocminfo`, and review `HIP_VISIBLE_DEVICES`.

## Related pages

- [Native In-House Engine and GPU Backends](in-house-engine.md)
- [LC0 Integration](lc0.md)
- [Build and Install](build-and-install.md)
- [Command Reference](command-reference.md)
- [Troubleshooting](troubleshooting.md)
