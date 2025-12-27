#!/usr/bin/env bash
# Universal Chess CLI installer (Ubuntu)
# Repo: https://github.com/LenniAConrad/universal-chess-cli
# Installs a launcher at /usr/local/bin/ucicli that runs from this repo.
set -euo pipefail

APP_NAME="ucicli"
REPO_OWNER="LenniAConrad"
REPO_NAME="universal-chess-cli"

# Resolve repo root (assume script is placed in repo root)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd -- "$SCRIPT_DIR" && pwd)"
OUT_DIR="$APP_HOME/out"
JAR_PATH="$APP_HOME/ucicli.jar"
LAUNCHER="/usr/local/bin/$APP_NAME"

CUDA_MODE="auto"      # auto|yes|no
REQUIRE_CUDA=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --cuda)
      CUDA_MODE="yes"
      shift
      ;;
    --require-cuda)
      CUDA_MODE="yes"
      REQUIRE_CUDA=1
      shift
      ;;
    --no-cuda)
      CUDA_MODE="no"
      shift
      ;;
    -h|--help)
      echo "Usage: ./install.sh [--cuda|--require-cuda|--no-cuda]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: ./install.sh [--cuda|--require-cuda|--no-cuda]" >&2
      exit 2
      ;;
  esac
done

SUDO=''
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO='sudo'
  else
    echo "This script needs root privileges for package installs and installing $LAUNCHER."
    echo "Please run as root or install sudo."
    exit 1
  fi
fi

confirm() {
  # confirm "Prompt" [default:Y|N]
  local prompt="${1:-Proceed?}"
  local def="${2:-Y}"
  local hint="[Y/n]"
  [[ "$def" == "N" ]] && hint="[y/N]"
  while true; do
    read -rp "$prompt $hint " ans || exit 1
    ans="${ans:-$def}"
    case "$ans" in
      [Yy]*) return 0 ;;
      [Nn]*) return 1 ;;
      *) echo "Please answer y or n." ;;
    esac
  done
}

need_apt_update=0
apt_install() {
  local pkg="$1"
  if ! dpkg -s "$pkg" >/dev/null 2>&1; then
    if [[ $need_apt_update -eq 0 ]]; then
      $SUDO apt-get update -y
      need_apt_update=1
    fi
    $SUDO apt-get install -y "$pkg"
  fi
}

cmake_version_ok() {
  # Requires CMake 3.18+ for proper CUDA language support.
  command -v cmake >/dev/null 2>&1 || return 1
  local v major minor
  v="$(cmake --version 2>/dev/null | head -n1 | awk '{print $3}')"
  major="${v%%.*}"
  minor="${v#*.}"
  minor="${minor%%.*}"
  [[ "$major" =~ ^[0-9]+$ && "$minor" =~ ^[0-9]+$ ]] || return 1
  (( major > 3 || (major == 3 && minor >= 18) ))
}

maybe_export_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    return 0
  fi
  if ! command -v javac >/dev/null 2>&1; then
    return 0
  fi
  local javac_path
  javac_path="$(readlink -f "$(command -v javac)")"
  export JAVA_HOME
  JAVA_HOME="$(dirname "$(dirname "$javac_path")")"
}

JOBS=4
if command -v nproc >/dev/null 2>&1; then
  JOBS="$(nproc)"
fi

CUDA_RESULT="skipped" # built|skipped|failed
CUDA_BUILD_DIR="$APP_HOME/native-cuda/build"
CUDA_LIB_SO="$CUDA_BUILD_DIR/liblc0j_cuda.so"

manual_build_cuda_backend() {
  if ! command -v nvcc >/dev/null 2>&1; then
    echo "nvcc not found."
    return 1
  fi

  maybe_export_java_home
  if [[ -z "${JAVA_HOME:-}" || ! -d "${JAVA_HOME}/include" ]]; then
    echo "JAVA_HOME is not set to a valid JDK (needed for JNI headers)."
    return 1
  fi
  if [[ ! -d "${JAVA_HOME}/include/linux" ]]; then
    echo "Missing JNI platform headers at: ${JAVA_HOME}/include/linux"
    return 1
  fi

  mkdir -p "$CUDA_BUILD_DIR"
  echo
  echo "Building CUDA backend (native-cuda) via nvcc (CMake fallback)..."
  if nvcc -shared -Xcompiler=-fPIC -O3 --std=c++17 \
      -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" \
      -o "$CUDA_LIB_SO" "$APP_HOME/native-cuda/lc0j_cuda_jni.cu" -lcudart; then
    return 0
  fi
  return 1
}

try_build_cuda_backend() {
  if [[ "$CUDA_MODE" == "no" ]]; then
    CUDA_RESULT="skipped"
    return 0
  fi
  if [[ ! -d "$APP_HOME/native-cuda" ]]; then
    CUDA_RESULT="skipped"
    return 0
  fi

  local want_build=0
  if [[ "$CUDA_MODE" == "yes" ]]; then
    want_build=1
  elif [[ "$CUDA_MODE" == "auto" ]]; then
    if confirm "Build optional CUDA backend (native-cuda JNI)? (auto-uses GPU if available)" "Y"; then
      want_build=1
    else
      CUDA_RESULT="skipped"
      return 0
    fi
  fi

  if [[ $want_build -eq 0 ]]; then
    CUDA_RESULT="skipped"
    return 0
  fi

  if ! command -v g++ >/dev/null 2>&1 || ! command -v make >/dev/null 2>&1; then
    echo "Compiler toolchain not found; installing build-essential..."
    if ! apt_install build-essential; then
      echo "Failed to install build-essential."
      CUDA_RESULT="failed"
      return 0
    fi
  fi

  if ! command -v nvcc >/dev/null 2>&1; then
    echo "CUDA toolkit (nvcc) not found; installing nvidia-cuda-toolkit (large)..."
    if ! apt_install nvidia-cuda-toolkit; then
      echo "Failed to install nvidia-cuda-toolkit."
      CUDA_RESULT="failed"
      return 0
    fi
  fi

  if ! command -v cmake >/dev/null 2>&1; then
    echo "CMake not found; installing (preferred for CUDA backend builds)..."
    if ! apt_install cmake; then
      echo "Failed to install CMake."
      echo "Trying a manual nvcc build as a fallback..."
      if manual_build_cuda_backend; then
        echo "CUDA backend built: $CUDA_LIB_SO"
        CUDA_RESULT="built"
        return 0
      fi
      CUDA_RESULT="failed"
      return 0
    fi
  fi
  if ! cmake_version_ok; then
    echo "CMake 3.18+ is required for the CUDA backend."
    echo "Found: $(cmake --version 2>/dev/null | head -n1 || true)"
    echo "Trying a manual nvcc build as a fallback..."
    if manual_build_cuda_backend; then
      echo "CUDA backend built: $CUDA_LIB_SO"
      CUDA_RESULT="built"
      return 0
    fi
    CUDA_RESULT="failed"
    return 0
  fi

  echo
  echo "Building CUDA backend (native-cuda)..."
  maybe_export_java_home

  if cmake -S "$APP_HOME/native-cuda" -B "$CUDA_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release && \
     cmake --build "$CUDA_BUILD_DIR" -j "$JOBS"; then
    if [[ -f "$CUDA_LIB_SO" ]]; then
      echo "CUDA backend built: $CUDA_LIB_SO"
    else
      echo "CUDA backend build succeeded, but expected library not found at: $CUDA_LIB_SO"
    fi
    CUDA_RESULT="built"
    return 0
  fi

  echo
  echo "WARNING: CUDA backend build failed. Continuing with CPU-only install."
  echo "To retry later:"
  echo "  cmake -S native-cuda -B native-cuda/build -DCMAKE_BUILD_TYPE=Release"
  echo "  cmake --build native-cuda/build -j"
  echo "Troubleshooting: native-cuda/README.md"
  CUDA_RESULT="failed"
  return 0
}

echo "== Universal Chess CLI installer =="
echo "Repo path: $APP_HOME"
echo

echo "Checking prerequisites..."
JAVA_OK=0
JAVAC_OK=0

if command -v java >/dev/null 2>&1; then
  # Try to detect major version (works for modern OpenJDK)
  ver="$(java -version 2>&1 | head -n1 | sed -E 's/.*version "?([0-9]+).*/\1/')"
  if [[ "$ver" =~ ^[0-9]+$ && "$ver" -ge 17 ]]; then
    JAVA_OK=1
  else
    echo "  -> Found Java, but version <$ver> (<17)."
  fi
else
  echo "  -> Java not found."
fi

if command -v javac >/dev/null 2>&1; then
  JAVAC_OK=1
else
  echo "  -> javac not found (needed to build)."
fi

if [[ $JAVA_OK -eq 0 || $JAVAC_OK -eq 0 ]]; then
  if confirm "Install OpenJDK 17 (JRE+JDK) now?" "Y"; then
    apt_install openjdk-17-jdk
    JAVA_OK=1
    JAVAC_OK=1
  else
    echo "Skipping Java installation. Ensure Java 17 (and javac) is available."
  fi
fi

if command -v stockfish >/dev/null 2>&1; then
  echo "Stockfish found at: $(command -v stockfish)"
else
  if confirm "Install Stockfish (optional, recommended)?" "Y"; then
    apt_install stockfish
  else
    echo "Skipping Stockfish installation. You can set your engine path in config/default.engine.toml."
  fi
fi

echo
echo "Creating output & log directories..."
mkdir -p "$OUT_DIR" "$APP_HOME/dump" "$APP_HOME/session"

echo "Compiling sources (pure javac)..."
find "$APP_HOME/src" -name "*.java" -print0 | xargs -0 javac --release 17 -d "$OUT_DIR"

echo "Packaging runnable jar..."
jar --create --file "$JAR_PATH" --main-class application.Main -C "$OUT_DIR" .

try_build_cuda_backend
if [[ $REQUIRE_CUDA -eq 1 && "$CUDA_RESULT" != "built" ]]; then
  echo
  echo "ERROR: CUDA backend was required (--require-cuda) but was not built."
  exit 1
fi

echo "Installing launcher to $LAUNCHER ..."
LAUNCHER_TMP="$(mktemp)"
cat > "$LAUNCHER_TMP" <<EOF
#!/usr/bin/env bash
set -euo pipefail
APP_HOME="$APP_HOME"
JAVA_BIN="\${JAVA_BIN:-java}"
JAVA_OPTS="\${JAVA_OPTS:-}"
cd "\$APP_HOME"
CUDA_LIB_DIR="\$APP_HOME/native-cuda/build"
CUDA_OPT=""
if [[ -f "\$CUDA_LIB_DIR/liblc0j_cuda.so" && "\$JAVA_OPTS" != *"-Djava.library.path="* ]]; then
  CUDA_OPT="-Djava.library.path=\$CUDA_LIB_DIR"
fi
# shellcheck disable=SC2086
exec "\$JAVA_BIN" \$JAVA_OPTS \$CUDA_OPT -jar "\$APP_HOME/ucicli.jar" "\$@"
EOF

$SUDO mv "$LAUNCHER_TMP" "$LAUNCHER"
$SUDO chmod +x "$LAUNCHER"

echo
echo "== Done =="
echo "Launcher installed: $LAUNCHER"
if [[ "$CUDA_RESULT" == "built" ]]; then
  echo "CUDA backend: built ($CUDA_LIB_SO)"
elif [[ "$CUDA_RESULT" == "failed" ]]; then
  echo "CUDA backend: not built (CPU fallback)"
fi
echo
echo "Next steps (please read):"
echo "  1) Open README.md for quick usage (and wiki/README.md for full docs)."
echo "  2) Review and tweak config files under ./config/:"
echo "       - cli.config.toml         (CLI defaults, output dir, gates)"
echo "       - default.engine.toml     (UCI engine protocol; set your engine path)"
echo "       - book.eco.toml           (optional ECO names)"
echo "  3) Verify LICENSE.txt fits your intentions."
echo
echo "Examples:"
echo "  $APP_NAME help"
echo "  $APP_NAME print --fen \"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\""
echo "  $APP_NAME record-to-plain -i data/input.record -o dump/output.plain --sidelines"
echo "  $APP_NAME record-to-csv -i data/input.record -o dump/output.csv"
echo "  $APP_NAME mine -i data/seeds.pgn -o dump/ --engine-instances 4 --max-duration 60s"
echo
echo "Optional (LC0 CUDA JNI):"
echo "  cmake -S native-cuda -B native-cuda/build -DCMAKE_BUILD_TYPE=Release"
echo "  cmake --build native-cuda/build -j"
echo "  java -cp out -Djava.library.path=native-cuda/build -Ducicli.lc0.backend=cuda application.Main display --fen \"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\" --show-backend"
echo "  (See native-cuda/README.md for more details.)"
echo
echo "Tip: You can run from anywhere using '$APP_NAME'."
