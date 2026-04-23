# Releasing ChessRTK (`crtk`)

This repo is intentionally build-tool light: the CLI is a runnable Java 17 jar.
Optional native GPU backends live under `native/cuda/`, `native/rocm/`, and
`native/oneapi/`. The release script below packages the CUDA Linux artifact.

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

To include local, gitignored model files in the bundle:

```bash
scripts/make_release_linux_cuda.sh --version vX.Y.Z --include-models
```

Outputs:
- `dist/crtk-vX.Y.Z-linux-x86_64-cuda.tar.gz`
- `dist/SHA256SUMS`

Quick smoke test (from the extracted artifact directory):

```bash
./crtk engine gpu
./crtk help
```

## GitHub release checklist

## Release readiness checklist

1. Ensure the working tree is clean:
   `git status --short`
2. Run a fresh compile and lint pass:
   `./scripts/run_regression_suite.sh build`
   `./scripts/run_regression_suite.sh lint`
3. Run the recommended local regression suite:
   `./scripts/run_regression_suite.sh recommended`
4. Run a deeper perft suite after move-generation or notation changes:
   `CRTK_PERFT_SUITE_DEPTH=6 CRTK_PERFT_THREADS=4 ./scripts/run_regression_suite.sh perft-smoke`
5. Update docs that changed with the release:
   `README.md`, `wiki/command-reference.md`, `wiki/build-and-install.md`, and any new feature notes
6. Build a fresh runnable jar:
   `./scripts/run_regression_suite.sh jar`
7. Confirm license intent before publishing:
   current `LICENSE.txt` is source-available and restrictive (non-commercial / no-derivatives), not a standard open-source license
   if broader adoption matters, replace it intentionally before release with a standard license such as `MIT`, `Apache-2.0`, or `BSD-2-Clause`

## GitHub release workflow

1. Ensure the release-readiness checklist above is complete.
2. Decide on version `vX.Y.Z` and tag it:
   - `git tag -a vX.Y.Z -m "vX.Y.Z"`
   - `git push origin vX.Y.Z`
3. Run the release build:
   - `scripts/make_release_linux_cuda.sh --version vX.Y.Z`
   - or at minimum `./scripts/run_regression_suite.sh release`
4. Create a GitHub Release for tag `vX.Y.Z` and upload:
   - `dist/crtk-vX.Y.Z-linux-x86_64-cuda.tar.gz`
   - `dist/SHA256SUMS`
