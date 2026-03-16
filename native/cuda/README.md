# `native/cuda`: optional CUDA backends (JNI)

This directory contains two optional native shared libraries built from separate CUDA sources:
- `lc0j_cuda` for LC0 evaluation (used by `src/chess/lc0/`)
- `t5_cuda` for the T5 tag-to-text pipeline (used by `src/chess/nn/t5/`)

If present at runtime, the Java code can:
- Detect whether CUDA is usable (`chess.nn.lc0.cuda.Support.isAvailable()` / `deviceCount()`, `chess.nn.t5.cuda.Support.isAvailable()` / `deviceCount()`).
- Run LC0J `.bin` policy+value inference on the GPU (`chess.nn.lc0.cuda.Backend`), auto-selected by `chess.nn.lc0.Network` when `-Dcrtk.lc0.backend=auto` (default) and CUDA is available.
- Run end-to-end T5 greedy decoding on the GPU (`chess.nn.t5.cuda.Backend`), with a CPU fallback when CUDA cannot initialize.

Both JNI libraries intentionally have **no third-party Java dependencies**; they use JNI and the CUDA runtime (`cudart`).
The T5 backend also links against cuBLAS for fast GEMMs.

## Supported platforms
This should build anywhere CMake can find:
- A C++17 toolchain
- The CUDA toolkit (`nvcc`, `cudart`)
- A JDK with JNI headers

In practice, Linux is the most tested target. Windows should work with a Visual Studio generator; macOS is typically not relevant because CUDA is not supported on modern macOS.

## Build
Prerequisites:
- CMake 3.18+ (CUDA language support)
- CUDA toolkit (provides `nvcc`, `cudart`, and `cublas`)
- Java 17+ JDK (for `jni.h`)

### Linux (single-config generators)

```bash
cmake -S native/cuda -B native/cuda/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/cuda/build -j
```

Output:
- Linux: `native/cuda/build/liblc0j_cuda.so`, `native/cuda/build/libt5_cuda.so`
- Windows: `native/cuda/build/Release/lc0j_cuda.dll`, `native/cuda/build/Release/t5_cuda.dll` (typical)

If CMake cannot find JNI, set `JAVA_HOME` to your JDK root and re-run configure.

### Windows (Visual Studio, multi-config)

```powershell
cmake -S native/cuda -B native/cuda/build -G "Visual Studio 17 2022" -A x64
cmake --build native/cuda/build --config Release
```

## Run
The libraries must be loadable via `System.loadLibrary("lc0j_cuda")` and/or `System.loadLibrary("t5_cuda")`.

Two common ways:
- Add the build directory to `java.library.path`.
- Copy the built library next to where you run Java from (there is a small fallback in `chess.nn.lc0.CudaSupport` that tries the current directory).
- Or set `CRTK_T5_CUDA_LIB` to the absolute `t5_cuda` library path (T5 only).

Example (quick backend check; opens a window):

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
java -cp out -Djava.library.path=native/cuda/build application.Main display --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --show-backend
```

Example (force CUDA backend; opens a window; errors out if CUDA cannot initialize):

```bash
java -cp out -Djava.library.path=native/cuda/build -Dcrtk.lc0.backend=cuda application.Main display --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --show-backend
```

Backend selection:
- LC0 default: `-Dcrtk.lc0.backend=auto` (use CUDA if available, else CPU)
- LC0 force CPU: `-Dcrtk.lc0.backend=cpu`
- LC0 force CUDA: `-Dcrtk.lc0.backend=cuda`
- T5 default: `-Dcrtk.t5.backend=auto` (use CUDA if available, else CPU)
- T5 force CPU: `-Dcrtk.t5.backend=cpu`
- T5 force CUDA: `-Dcrtk.t5.backend=cuda` (attempts CUDA, falls back to CPU on failure)
- T5 CUDA dtype (optional): `CRTK_T5_CUDA_DTYPE=fp16|bf16|fp32` (default `fp32`)

In code, call `chess.nn.lc0.cuda.Support.isAvailable()` / `deviceCount()` and
`chess.nn.t5.cuda.Support.isAvailable()` / `deviceCount()`.

## Notes
- This backend loads LC0J weights with magic `LC0J` (same file format as the pure-Java CPU path).
- Use `crtk.lc0.backend` / `crtk.lc0.threads` for configuration.

## Troubleshooting
- VSCode squiggles on `#include <jni.h>` / CUDA headers: run the CMake configure step once to generate `native/cuda/build/compile_commands.json` and reload VSCode (this repo sets `C_Cpp.default.compileCommands` accordingly).
- CMake cannot find CUDA: ensure `nvcc` is on `PATH`, or pass `-DCUDAToolkit_ROOT=/path/to/cuda` when configuring.
- CMake cannot find JNI: ensure you installed a full JDK (not just a JRE) and set `JAVA_HOME` to the JDK root.
- `UnsatisfiedLinkError: no lc0j_cuda in java.library.path`: pass `-Djava.library.path=...` pointing at the directory containing the built library, or copy the library into your working directory.
- `UnsatisfiedLinkError: no t5_cuda in java.library.path`: pass `-Djava.library.path=...` or set `CRTK_T5_CUDA_LIB`.
- `libcudart.so...: cannot open shared object file` (Linux): ensure the CUDA runtime is installed and discoverable (often via `LD_LIBRARY_PATH=/usr/local/cuda/lib64`).
- `libcublas.so...: cannot open shared object file` (Linux): ensure the CUDA toolkit is installed and `LD_LIBRARY_PATH` includes the CUDA lib directory.
- `cudart64_*.dll was not found` (Windows): ensure the CUDA runtime DLL directory is on `PATH` (or install the CUDA toolkit properly).
- `deviceCount=0` but you expect a GPU: check NVIDIA driver install, `nvidia-smi`, and `CUDA_VISIBLE_DEVICES`.
