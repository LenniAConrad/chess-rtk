# `native-cuda`: optional CUDA backend (JNI)

This directory contains an optional native shared library (`lc0j_cuda`) used by the Java LC0 evaluator under `src/chess/lc0/`.

If present at runtime, the Java code can:
- Detect whether CUDA is usable (`chess.lc0.cuda.Support.isAvailable()` / `deviceCount()`).
- Run LC0J `.bin` policy+value inference on the GPU (`chess.lc0.cuda.Backend`), which is auto-selected by `chess.lc0.Network` when `-Ducicli.lc0.backend=auto` (default) and CUDA is available.

This JNI library intentionally has **no third-party Java dependencies**; it uses JNI and the CUDA runtime (`cudart`).

## Supported platforms
This should build anywhere CMake can find:
- A C++17 toolchain
- The CUDA toolkit (`nvcc`, `cudart`)
- A JDK with JNI headers

In practice, Linux is the most tested target. Windows should work with a Visual Studio generator; macOS is typically not relevant because CUDA is not supported on modern macOS.

## Build
Prerequisites:
- CMake 3.18+ (CUDA language support)
- CUDA toolkit (provides `nvcc` and `cudart`)
- Java 17+ JDK (for `jni.h`)

### Linux (single-config generators)

```bash
cmake -S native-cuda -B native-cuda/build -DCMAKE_BUILD_TYPE=Release
cmake --build native-cuda/build -j
```

Output:
- Linux: `native-cuda/build/liblc0j_cuda.so`
- Windows: `native-cuda/build/Release/lc0j_cuda.dll` (typical)

If CMake cannot find JNI, set `JAVA_HOME` to your JDK root and re-run configure.

### Windows (Visual Studio, multi-config)

```powershell
cmake -S native-cuda -B native-cuda/build -G "Visual Studio 17 2022" -A x64
cmake --build native-cuda/build --config Release
```

## Run
The library must be loadable via `System.loadLibrary("lc0j_cuda")`.

Two common ways:
- Add the build directory to `java.library.path`.
- Copy the built library next to where you run Java from (there is a small fallback in `chess.lc0.CudaSupport` that tries the current directory).

Example (quick backend check; opens a window):

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
java -cp out -Djava.library.path=native-cuda/build application.Main display --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --show-backend
```

Example (force CUDA backend; opens a window; errors out if CUDA cannot initialize):

```bash
java -cp out -Djava.library.path=native-cuda/build -Ducicli.lc0.backend=cuda application.Main display --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --show-backend
```

Backend selection:
- Default: `-Ducicli.lc0.backend=auto` (use CUDA if available, else CPU)
- Force CPU: `-Ducicli.lc0.backend=cpu`
- Force CUDA: `-Ducicli.lc0.backend=cuda`

In code, call `chess.lc0.cuda.Support.isAvailable()` / `chess.lc0.cuda.Support.deviceCount()`.

## Notes
- This backend loads LC0J weights with magic `LC0J` (same file format as the pure-Java CPU path).
- `lc0j.backend` / `lc0j.threads` are still accepted as legacy aliases, but prefer `ucicli.lc0.backend` / `ucicli.lc0.threads`.

## Troubleshooting
- VSCode squiggles on `#include <jni.h>` / CUDA headers: run the CMake configure step once to generate `native-cuda/build/compile_commands.json` and reload VSCode (this repo sets `C_Cpp.default.compileCommands` accordingly).
- CMake cannot find CUDA: ensure `nvcc` is on `PATH`, or pass `-DCUDAToolkit_ROOT=/path/to/cuda` when configuring.
- CMake cannot find JNI: ensure you installed a full JDK (not just a JRE) and set `JAVA_HOME` to the JDK root.
- `UnsatisfiedLinkError: no lc0j_cuda in java.library.path`: pass `-Djava.library.path=...` pointing at the directory containing the built library, or copy the library into your working directory.
- `libcudart.so...: cannot open shared object file` (Linux): ensure the CUDA runtime is installed and discoverable (often via `LD_LIBRARY_PATH=/usr/local/cuda/lib64`).
- `cudart64_*.dll was not found` (Windows): ensure the CUDA runtime DLL directory is on `PATH` (or install the CUDA toolkit properly).
- `deviceCount=0` but you expect a GPU: check NVIDIA driver install, `nvidia-smi`, and `CUDA_VISIBLE_DEVICES`.
