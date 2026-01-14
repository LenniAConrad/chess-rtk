#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/make_release_linux_cuda.sh [--version <vX.Y.Z>] [--include-models]

Builds:
  - ucicli.jar (Java 17, pure javac)
  - native/cuda/build/liblc0j_cuda.so (CMake + NVCC)

Packages a Linux x86_64 CUDA-enabled release under:
  dist/ucicli-<version>-linux-x86_64-cuda/
  dist/ucicli-<version>-linux-x86_64-cuda.tar.gz

Examples:
  scripts/make_release_linux_cuda.sh --version v0.1.0
  scripts/make_release_linux_cuda.sh --include-models
EOF
}

VERSION=""
INCLUDE_MODELS=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="${2:-}"
      shift 2
      ;;
    --include-models)
      INCLUDE_MODELS=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ -z "$VERSION" ]]; then
  if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    VERSION="$(git describe --tags --always)"
  else
    VERSION="$(date -u +%Y%m%d)"
  fi
fi

if ! command -v javac >/dev/null 2>&1; then
  echo "javac not found; install a Java 17+ JDK." >&2
  exit 1
fi
if ! command -v jar >/dev/null 2>&1; then
  echo "jar tool not found; install a Java 17+ JDK." >&2
  exit 1
fi
if ! command -v cmake >/dev/null 2>&1; then
  echo "cmake not found; install CMake 3.18+." >&2
  exit 1
fi
if ! command -v nvcc >/dev/null 2>&1; then
  echo "nvcc not found; install the CUDA toolkit (required to build native/cuda)." >&2
  exit 1
fi

echo "== Building Java (ucicli.jar) =="
rm -rf out
mkdir -p out
find src -name "*.java" -print0 | xargs -0 javac --release 17 -d out
jar --create --file ucicli.jar --main-class application.Main -C out .

echo "== Building CUDA JNI (liblc0j_cuda.so) =="
cmake -S native/cuda -B native/cuda/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/cuda/build -j

cuda_lib="native/cuda/build/liblc0j_cuda.so"
if [[ ! -f "$cuda_lib" ]]; then
  echo "Expected CUDA library not found at: $cuda_lib" >&2
  exit 1
fi

artifact="ucicli-${VERSION}-linux-x86_64-cuda"
stage_dir="dist/${artifact}"
rm -rf "$stage_dir"
mkdir -p "$stage_dir/lib"

echo "== Staging files: $stage_dir =="
cp -f ucicli.jar "$stage_dir/"
cp -f "$cuda_lib" "$stage_dir/lib/"
cp -f LICENSE.txt "$stage_dir/"
cp -f README.md "$stage_dir/"
cp -r config "$stage_dir/"
cp -r wiki "$stage_dir/"

if [[ $INCLUDE_MODELS -eq 1 ]]; then
  cp -r models "$stage_dir/"
fi

cat > "$stage_dir/ucicli" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"

CUDA_LIB_DIR="$APP_HOME/lib"
CUDA_OPT=""
if [[ -f "$CUDA_LIB_DIR/liblc0j_cuda.so" && "$JAVA_OPTS" != *"-Djava.library.path="* ]]; then
  CUDA_OPT="-Djava.library.path=$CUDA_LIB_DIR"
fi

# shellcheck disable=SC2086
exec "$JAVA_BIN" $JAVA_OPTS $CUDA_OPT -jar "$APP_HOME/ucicli.jar" "$@"
EOF
chmod +x "$stage_dir/ucicli"

echo "== Creating tarball =="
tarball="dist/${artifact}.tar.gz"
rm -f "$tarball"
tar -C dist -czf "$tarball" "$artifact"

echo "== Checksums =="
(cd dist && sha256sum "${artifact}.tar.gz" > SHA256SUMS)
echo "Wrote: $tarball"
echo "Wrote: dist/SHA256SUMS"
