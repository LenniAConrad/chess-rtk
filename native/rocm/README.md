# `native/rocm`: optional ROCm backend (JNI)

This directory contains optional native shared libraries:
- `lc0j_rocm` used by the Java LC0 evaluator under `src/chess/lc0/`.
- `t5_rocm` used by the T5 tag-to-text pipeline under `src/chess/nn/t5/`.

If present at runtime, the Java code can:
- Detect whether ROCm is usable (`chess.nn.lc0.rocm.Support.isAvailable()` / `deviceCount()`).
- Run LC0J `.bin` policy+value inference on the GPU (`chess.nn.lc0.rocm.Backend`), which is auto-selected by `chess.nn.lc0.Network` when `-Dcrtk.lc0.backend=auto` (default) and ROCm is available.
- Run end-to-end T5 greedy decoding on the GPU (`chess.nn.t5.rocm.Backend`), with a CPU fallback when ROCm cannot initialize.

This JNI library intentionally has **no third-party Java dependencies**; it uses JNI and the ROCm HIP runtime.

## Supported platforms
This should build anywhere CMake can find:
- A C++17 toolchain
- The ROCm toolkit (HIP compiler/runtime)
- A JDK with JNI headers

In practice, Linux is the most tested target. Windows and macOS are typically not used for ROCm.

## Build
Prerequisites:
- CMake 3.18+ (HIP language support)
- ROCm toolkit (provides HIP/hipcc)
- Java 17+ JDK (for `jni.h`)

### Linux (single-config generators)

```bash
cmake -S native/rocm -B native/rocm/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/rocm/build -j
```

Output:
- Linux: `native/rocm/build/liblc0j_rocm.so`, `native/rocm/build/libt5_rocm.so`
- Windows: `native/rocm/build/Release/lc0j_rocm.dll` (if supported)

If CMake cannot find JNI, set `JAVA_HOME` to your JDK root and re-run configure.

## Run
The libraries must be loadable via `System.loadLibrary("lc0j_rocm")` and/or `System.loadLibrary("t5_rocm")`.

Two common ways:
- Add the build directory to `java.library.path`.
- Copy the built library next to where you run Java from (there is a small fallback in `chess.nn.lc0.rocm.Support` that tries the current directory).
 - Or set `CRTK_T5_ROCM_LIB` to the absolute `t5_rocm` library path (T5 only).

Example (quick backend check; opens a window):

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
java -cp out -Djava.library.path=native/rocm/build application.Main display --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --show-backend
```

Example (force ROCm backend; opens a window; errors out if ROCm cannot initialize):

```bash
java -cp out -Djava.library.path=native/rocm/build -Dcrtk.lc0.backend=rocm application.Main display --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --show-backend
```

Backend selection:
- Default: `-Dcrtk.lc0.backend=auto` (use CUDA/ROCm/oneAPI if available, else CPU)
- Force CPU: `-Dcrtk.lc0.backend=cpu`
- Force ROCm: `-Dcrtk.lc0.backend=rocm` (aliases: `amd`, `hip`)

T5 selection:
- Default: `-Dcrtk.t5.backend=auto` (use CUDA/ROCm/oneAPI if available, else CPU)
- Force CPU: `-Dcrtk.t5.backend=cpu`
- Force ROCm: `-Dcrtk.t5.backend=rocm` (aliases: `amd`, `hip`)

In code, call `chess.nn.lc0.rocm.Support.isAvailable()` / `chess.nn.lc0.rocm.Support.deviceCount()` and
`chess.nn.t5.rocm.Support.isAvailable()` / `chess.nn.t5.rocm.Support.deviceCount()`.

## Notes
- This backend loads LC0J weights with magic `LC0J` (same file format as the pure-Java CPU path).
- Use `crtk.lc0.backend` / `crtk.lc0.threads` for configuration.

## Troubleshooting
- VSCode squiggles on `#include <jni.h>` / HIP headers: run the CMake configure step once to generate `native/rocm/build/compile_commands.json` and reload VSCode (this repo sets `C_Cpp.default.compileCommands` accordingly).
- CMake cannot find HIP/ROCm: ensure `hipcc` is on `PATH`, or pass `-DCMAKE_PREFIX_PATH=/opt/rocm` when configuring.
- CMake cannot find JNI: ensure you installed a full JDK (not just a JRE) and set `JAVA_HOME` to the JDK root.
- `UnsatisfiedLinkError: no lc0j_rocm in java.library.path`: pass `-Djava.library.path=...` pointing at the directory containing the built library, or copy the library into your working directory.
- `libamdhip64.so...: cannot open shared object file` (Linux): ensure the ROCm runtime is installed and discoverable (often via `LD_LIBRARY_PATH=/opt/rocm/lib`).
- `libhipblas.so...: cannot open shared object file` (Linux): ensure ROCm BLAS is installed and on `LD_LIBRARY_PATH`.
- `deviceCount=0` but you expect a GPU: check AMD driver install, `rocminfo`, and `HIP_VISIBLE_DEVICES`.
