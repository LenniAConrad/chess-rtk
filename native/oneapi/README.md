# oneAPI / SYCL Native Backend (perft + OTIS)

This directory holds the optional **oneAPI / SYCL** JNI backend for ChessRTK ("crtk"). It compiles to native shared libraries that crtk loads at runtime to offload two workloads onto an Intel GPU: bulk **perft** (split-depth node counting) and **OTIS** neural-network policy/WDL inference. Everything here is strictly optional — crtk ships a pure-Java implementation of both paths and falls back to the CPU automatically when the native library, the SYCL runtime, or an Intel GPU is missing. There are no third-party Java dependencies: the bridge is plain JNI plus the oneAPI SYCL runtime, and the perft device kernel reuses the same portable C++ move generator the CUDA and ROCm backends use, so a position counted on the GPU is counted by the same rules as crtk's [one shared chess core](../../docs/architecture.md).

This README is technical and build-focused. For the user-facing commands that consume this backend, see [Native GPU backends](../../docs/lc0.md), [Command Reference](../../docs/command-reference.md), and the sibling [CUDA](../cuda/README.md) and [ROCm](../rocm/README.md) backends.

## What this backend accelerates

| Workload | crtk feature | Java entry point | Native source |
| --- | --- | --- | --- |
| Split-depth bulk perft | `engine perft --gpu`, `engine perft-suite --gpu` | `chess.nn.perft.oneapi.Backend` / `Support` | `perft_oneapi_jni.cpp` |
| OTIS policy/WDL inference | `engine eval --otis`, `engine builtin --otis` | `chess.nn.otis.oneapi.Backend` / `Support` | `otis_oneapi_jni.cpp` |

> The same `CMakeLists.txt` also builds `lc0_oneapi` (LC0 CNN + BT4) and `t5_oneapi` (T5 summaries). Those are out of scope for this README — see the in-tree comments in `lc0_cnn_oneapi_jni.cpp`, `lc0_bt4_oneapi_jni.cpp`, and `t5_oneapi_jni.cpp`. The build steps below produce all four libraries in one pass.

### Honest fidelity note

The OTIS native path reconstructs the board from the encoded `simple_18` planes, rebuilds the typed tactical relation masks on the host, and runs the OTIS forward pass (square tokens → typed sheaf trunk → readout → policy/WDL heads) in SYCL kernels. It deliberately does **not** reproduce the per-legal-move policy refinement that the pure-Java path layers on top, because the device kernel has no legal-move generator. The values it returns are the **raw policy-head logits**, not the refined per-move policy. OTIS is a usable research evaluator, not a bit-exact reproduction of any external engine. Perft, by contrast, returns exact node counts identical to the CPU path.

## Prerequisites

- **CMake 3.18+**
- A **SYCL-capable C++17 compiler** — Intel oneAPI DPC++ (`icpx`) or upstream LLVM with `-fsycl`. The build passes `-fsycl` as both a compile and link option.
- The **oneAPI / SYCL runtime** (provides `libsycl.so` and Level Zero / OpenCL device backends).
- A full **JDK 17+** so CMake's `find_package(JNI)` can locate `jni.h` (a JRE is not enough).
- An **Intel GPU** with working drivers at runtime. The backend explicitly filters for GPU devices whose vendor string contains `intel`; if none are found, `deviceCount()` returns 0 and crtk falls back to the CPU.

Linux is the most tested target. Windows may work with Intel oneAPI; macOS is not a supported target.

## Build

From the repository root:

```bash
cmake -S native/oneapi -B native/oneapi/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/oneapi/build -j
```

If your default compiler does not understand `-fsycl`, point CMake at the SYCL compiler explicitly:

```bash
cmake -S native/oneapi -B native/oneapi/build -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_COMPILER=icpx
cmake --build native/oneapi/build -j
```

If CMake cannot find JNI, set `JAVA_HOME` to your JDK root and re-run the configure step.

### Library output

| Platform | perft | OTIS |
| --- | --- | --- |
| Linux | `native/oneapi/build/libperft_oneapi.so` | `native/oneapi/build/libotis_oneapi.so` |
| Windows | `native/oneapi/build/Release/perft_oneapi.dll` | `native/oneapi/build/Release/otis_oneapi.dll` |

The configure step also writes `native/oneapi/build/compile_commands.json` (this repo points `C_Cpp.default.compileCommands` at it for editor IntelliSense over the SYCL and JNI headers).

## Loading the library from Java

The libraries are resolved through `System.loadLibrary("perft_oneapi")` and `System.loadLibrary("otis_oneapi")`. Make them discoverable in one of these ways:

- Add the build directory to the JVM library path: `-Djava.library.path=native/oneapi/build`.
- Set an explicit absolute path to a single library file: `CRTK_PERFT_ONEAPI_LIB=/abs/path/to/libperft_oneapi.so` or `CRTK_OTIS_ONEAPI_LIB=/abs/path/to/libotis_oneapi.so`.
- Ensure the oneAPI runtime is on the loader path, e.g. `LD_LIBRARY_PATH=/opt/intel/oneapi/compiler/latest/linux/lib` on Linux, so `libsycl.so` resolves.

A clean compile of crtk, for reference (no Maven or Gradle — crtk builds with `javac --release 17`):

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
```

## Using the backend

### Check what the JVM sees

```bash
java -cp out -Djava.library.path=native/oneapi/build crtk engine gpu --verbose
```

`engine gpu` reports the GPU JNI backend status for each workload. In code, the same information is available via `chess.nn.perft.oneapi.Support.isAvailable()` / `deviceCount()` and `chess.nn.otis.oneapi.Support.isAvailable()` / `deviceCount()`.

### GPU perft

Java expands the search tree on the CPU down to `--split` plies, packs each frontier position into 13 little-endian words, and hands the batch to the device. Each SYCL work-item unpacks one position and counts `perft(remainingDepth)` with the shared recursion-free move generator; the host sums the results. Raise `--split` when the remaining device-side depth is too high for a single kernel pass.

```bash
java -cp out -Djava.library.path=native/oneapi/build crtk engine perft --startpos --depth 6 --gpu --split 3
java -cp out -Djava.library.path=native/oneapi/build crtk engine perft-suite --depth 6 --gpu --split 2
```

If no native perft backend is available, `--gpu` silently falls back to the deterministic CPU perft and produces identical counts.

### OTIS evaluation

```bash
java -cp out -Djava.library.path=native/oneapi/build crtk engine eval --otis --weights path/to/otis.bin --startpos
```

## Backend selection

The native code only activates when an Intel GPU is present and the selected mode permits it. Both selectors accept `intel` as an alias for `oneapi`.

| Property | Default | Values |
| --- | --- | --- |
| `-Dcrtk.perft.backend` | `auto` (try CUDA, then ROCm, then oneAPI, else CPU) | `auto`, `oneapi`, `intel` |
| `-Dcrtk.otis.backend` | `auto` (CUDA / ROCm / oneAPI if available, else CPU) | `auto`, `cpu`, `oneapi`, `intel` |

Force the oneAPI path to confirm it engages (this errors out if oneAPI cannot initialize, rather than silently falling back):

```bash
java -cp out -Djava.library.path=native/oneapi/build -Dcrtk.otis.backend=oneapi crtk engine eval --otis --weights path/to/otis.bin --startpos
```

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `-fsycl: unknown argument` | Use a SYCL-capable compiler; add `-DCMAKE_CXX_COMPILER=icpx`. |
| CMake cannot find JNI | Install a full JDK (not a JRE) and set `JAVA_HOME` to its root. |
| `UnsatisfiedLinkError: no perft_oneapi in java.library.path` | Pass `-Djava.library.path=native/oneapi/build` or set `CRTK_PERFT_ONEAPI_LIB`. |
| `UnsatisfiedLinkError: no otis_oneapi in java.library.path` | Pass `-Djava.library.path=native/oneapi/build` or set `CRTK_OTIS_ONEAPI_LIB`. |
| `libsycl.so: cannot open shared object file` | Add the oneAPI runtime to the loader path, e.g. `LD_LIBRARY_PATH=/opt/intel/oneapi/compiler/latest/linux/lib`. |
| `deviceCount=0` but a GPU is installed | Check Intel GPU drivers, run `sycl-ls`, and review `ONEAPI_DEVICE_SELECTOR`. |
| Editor squiggles on `#include <jni.h>` / SYCL headers | Run the CMake configure step once to generate `compile_commands.json`, then reload the editor. |

## See also

- [Native GPU backends overview](../../docs/lc0.md)
- [Command Reference](../../docs/command-reference.md)
- [Build & Install](../../docs/build-and-install.md)
- [CUDA backend](../cuda/README.md) · [ROCm backend](../rocm/README.md)
