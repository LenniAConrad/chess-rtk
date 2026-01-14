# Releasing `ucicli`

This repo is intentionally build-tool light: the CLI is a runnable Java 17 jar, and the optional CUDA backend is a small JNI library under `native/cuda/`.

## Linux (x86_64) + CUDA release artifact

Prerequisites:
- Java 17+ JDK (`javac`, `jar`)
- CMake 3.18+
- CUDA toolkit (`nvcc`)
- NVIDIA driver (runtime)

Build and package:

```bash
scripts/make_release_linux_cuda.sh --version vX.Y.Z
```

To include `models/` in the bundle:

```bash
scripts/make_release_linux_cuda.sh --version vX.Y.Z --include-models
```

Outputs:
- `dist/ucicli-vX.Y.Z-linux-x86_64-cuda.tar.gz`
- `dist/SHA256SUMS`

Quick smoke test (from the extracted artifact directory):

```bash
./ucicli cuda-info
./ucicli help
```

## GitHub release checklist

1. Ensure working tree is clean (`git status`).
2. Decide on version `vX.Y.Z` and tag it:
   - `git tag -a vX.Y.Z -m "vX.Y.Z"`
   - `git push origin vX.Y.Z`
3. Run the release build:
   - `scripts/make_release_linux_cuda.sh --version vX.Y.Z`
4. Create a GitHub Release for tag `vX.Y.Z` and upload:
   - `dist/ucicli-vX.Y.Z-linux-x86_64-cuda.tar.gz`
   - `dist/SHA256SUMS`
