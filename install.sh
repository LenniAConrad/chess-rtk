#!/usr/bin/env bash
# crtk installer (Ubuntu)
# Repo: https://github.com/LenniAConrad/chess-rtk
# Installs a launcher at /usr/local/bin/crtk that runs from this repo.
set -euo pipefail

APP_NAME="crtk"
REPO_OWNER="LenniAConrad"
REPO_NAME="ChessRTK"

# Resolve repo root (assume script is placed in repo root)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd -- "$SCRIPT_DIR" && pwd)"
OUT_DIR="$APP_HOME/out"
JAR_PATH="$APP_HOME/crtk.jar"
LAUNCHER="/usr/local/bin/$APP_NAME"
MODEL_DIR="$APP_HOME/models"
MODEL_BASE_URL="https://media.githubusercontent.com/media/LenniAConrad/chess-models/main/models"
MODEL_REMOTE_FILES=("nn-f68ec79f0fe3.nnue" "lc0_610153.bin" "lc0_744706.bin")
MODEL_LOCAL_FILES=(
  "crtk-halfkp.nnue"
  "leela_112planes-30blocksx384-policyhead80-valuehead32-policy4672-wdl3.bin"
  "leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin"
)

CUDA_MODE="auto"      # auto|yes|no
REQUIRE_CUDA=0
ROCM_MODE="auto"      # auto|yes|no
REQUIRE_ROCM=0
ONEAPI_MODE="auto"    # auto|yes|no
REQUIRE_ONEAPI=0
MODEL_MODE="auto"     # auto|yes|no
INSTALL_LAUNCHER=1

# Colors (opt-out with NO_COLOR or non-TTY)
if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  C_RESET=$'\033[0m'
  C_BOLD=$'\033[1m'
  C_GREEN_BOLD=$'\033[1;92m'
  C_YELLOW=$'\033[33m'
  C_RED=$'\033[31m'
else
  C_RESET=""
  C_BOLD=""
  C_GREEN_BOLD=""
  C_YELLOW=""
  C_RED=""
fi

title() {
  printf '%b\n' "${C_GREEN_BOLD}$*${C_RESET}"
}
section() {
  printf '\n%b\n' "${C_BOLD}$*${C_RESET}"
}
step() {
  printf '%b\n' "${C_GREEN_BOLD}  >${C_RESET} $*"
}
info() {
  printf '    %s\n' "$*"
}
warn() {
  printf '%b\n' "${C_YELLOW}warning:${C_RESET} $*"
}
err() {
  printf '%b\n' "${C_RED}error:${C_RESET} $*" >&2
}

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
    --rocm|--amd)
      ROCM_MODE="yes"
      shift
      ;;
    --require-rocm|--require-amd)
      ROCM_MODE="yes"
      REQUIRE_ROCM=1
      shift
      ;;
    --no-rocm|--no-amd)
      ROCM_MODE="no"
      shift
      ;;
    --oneapi|--intel)
      ONEAPI_MODE="yes"
      shift
      ;;
    --require-oneapi|--require-intel)
      ONEAPI_MODE="yes"
      REQUIRE_ONEAPI=1
      shift
      ;;
    --no-oneapi|--no-intel)
      ONEAPI_MODE="no"
      shift
      ;;
    --no-launcher)
      INSTALL_LAUNCHER=0
      shift
      ;;
    --models|--fetch-models)
      MODEL_MODE="yes"
      shift
      ;;
    --no-models|--no-fetch-models)
      MODEL_MODE="no"
      shift
      ;;
    -h|--help)
      echo "Usage: ./install.sh [--cuda|--require-cuda|--no-cuda] [--rocm|--require-rocm|--no-rocm] [--oneapi|--require-oneapi|--no-oneapi] [--models|--no-models] [--no-launcher]"
      exit 0
      ;;
    *)
      err "Unknown argument: $1"
      echo "Usage: ./install.sh [--cuda|--require-cuda|--no-cuda] [--rocm|--require-rocm|--no-rocm] [--oneapi|--require-oneapi|--no-oneapi] [--models|--no-models] [--no-launcher]" >&2
      exit 2
      ;;
  esac
done

SUDO=''
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO='sudo'
  else
    err "This script needs root privileges for package installs and installing $LAUNCHER."
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
apt_update_once() {
  if [[ $need_apt_update -eq 0 ]]; then
    if ! $SUDO apt-get update -y; then
      return 1
    fi
    need_apt_update=1
  fi
}
apt_install() {
  local pkg="$1"
  if ! dpkg -s "$pkg" >/dev/null 2>&1; then
    if ! apt_update_once; then
      return 1
    fi
    if ! $SUDO apt-get install -y "$pkg"; then
      return 1
    fi
  fi
}

download_file() {
  # download_file URL DEST
  local url="$1"
  local dest="$2"
  local tmp="${dest}.tmp"

  rm -f "$tmp"
  if command -v curl >/dev/null 2>&1; then
    if ! curl -fL --progress-bar -o "$tmp" "$url"; then
      rm -f "$tmp"
      return 1
    fi
  elif command -v wget >/dev/null 2>&1; then
    if ! wget -O "$tmp" "$url"; then
      rm -f "$tmp"
      return 1
    fi
  else
    echo "Neither curl nor wget is installed." >&2
    if confirm "Install curl now?" "Y"; then
      apt_install curl || return 1
      if ! curl -fL --progress-bar -o "$tmp" "$url"; then
        rm -f "$tmp"
        return 1
      fi
    else
      return 1
    fi
  fi
  mv "$tmp" "$dest"
}

fetch_model_weights() {
  if [[ "$MODEL_MODE" == "no" ]]; then
    MODEL_RESULT="skipped"
    return 0
  fi

  local missing=()
  local index
  local remote
  local file
  for index in "${!MODEL_LOCAL_FILES[@]}"; do
    remote="${MODEL_REMOTE_FILES[$index]}"
    file="${MODEL_LOCAL_FILES[$index]}"
    if [[ ! -s "$MODEL_DIR/$file" && -s "$MODEL_DIR/$remote" ]]; then
      mv "$MODEL_DIR/$remote" "$MODEL_DIR/$file"
    fi
    if [[ ! -s "$MODEL_DIR/$file" ]]; then
      missing+=("$index")
    fi
  done

  if [[ ${#missing[@]} -eq 0 ]]; then
    MODEL_RESULT="present"
    step "Model weights already present in $MODEL_DIR"
    return 0
  fi

  if [[ "$MODEL_MODE" == "auto" ]]; then
    echo
    echo "Missing optional model weights:"
    for index in "${missing[@]}"; do
      printf '  %s\n' "${MODEL_LOCAL_FILES[$index]}"
    done
    if ! confirm "Download model weights into models/ now?" "Y"; then
      MODEL_RESULT="skipped"
      warn "Skipping model weights. engine builtin --nnue will require models/crtk-halfkp.nnue or --weights, and LC0-backed evaluation will use fallback behavior until weights are added."
      return 0
    fi
  fi

  mkdir -p "$MODEL_DIR"
  local failed=0
  for index in "${missing[@]}"; do
    remote="${MODEL_REMOTE_FILES[$index]}"
    file="${MODEL_LOCAL_FILES[$index]}"
    echo
    step "Downloading model weight: $file"
    if download_file "$MODEL_BASE_URL/$remote" "$MODEL_DIR/$file"; then
      step "Saved: $MODEL_DIR/$file"
    else
      failed=1
      rm -f "$MODEL_DIR/$file.tmp"
      warn "Failed to download $file"
    fi
  done

  if [[ $failed -eq 0 ]]; then
    MODEL_RESULT="downloaded"
  else
    MODEL_RESULT="failed"
  fi
  return 0
}

ubuntu_codename() {
  if command -v lsb_release >/dev/null 2>&1; then
    lsb_release -cs
    return 0
  fi
  if [[ -r /etc/os-release ]]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    if [[ -n "${VERSION_CODENAME:-}" ]]; then
      echo "$VERSION_CODENAME"
      return 0
    fi
  fi
  echo "jammy"
}

canonical_path() {
  local p="$1"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$p" <<'PY'
import os, sys
print(os.path.realpath(sys.argv[1]))
PY
    return 0
  fi
  if command -v realpath >/dev/null 2>&1; then
    realpath -m "$p" 2>/dev/null || realpath "$p" 2>/dev/null || echo "$p"
    return 0
  fi
  readlink -f "$p" 2>/dev/null || echo "$p"
}

cmake_cache_get() {
  # cmake_cache_get KEY CMakeCache.txt
  awk -F= -v k="$1" '$1==k{print $2; exit}' "$2" 2>/dev/null || true
}

detect_hipcc() {
  if command -v hipcc >/dev/null 2>&1; then
    command -v hipcc
    return 0
  fi
  if [[ -x /opt/rocm/bin/hipcc ]]; then
    echo "/opt/rocm/bin/hipcc"
    return 0
  fi
  return 1
}

detect_oneapi_cxx() {
  if command -v dpcpp >/dev/null 2>&1; then
    command -v dpcpp
    return 0
  fi
  if command -v icpx >/dev/null 2>&1; then
    command -v icpx
    return 0
  fi
  local p
  p="/opt/intel/oneapi/compiler/latest/bin/dpcpp"
  if [[ -x "$p" ]]; then
    echo "$p"
    return 0
  fi
  p="/opt/intel/oneapi/compiler/latest/bin/icpx"
  if [[ -x "$p" ]]; then
    echo "$p"
    return 0
  fi
  # Fallback for older layouts
  p="$(ls -1d /opt/intel/oneapi/compiler/*/bin/icpx 2>/dev/null | head -n1 || true)"
  if [[ -n "$p" && -x "$p" ]]; then
    echo "$p"
    return 0
  fi
  p="$(ls -1d /opt/intel/oneapi/compiler/*/bin/dpcpp 2>/dev/null | head -n1 || true)"
  if [[ -n "$p" && -x "$p" ]]; then
    echo "$p"
    return 0
  fi
  return 1
}

install_rocm_hip_toolchain() {
  # Best-effort ROCm HIP toolchain install. May require adding AMD's apt repo.
  if detect_hipcc >/dev/null 2>&1; then
    return 0
  fi

  echo "Attempting to install ROCm HIP toolchain (hipcc)..."
  if apt_install rocm-hip-sdk; then
    :
  else
    echo "ROCm packages not available in current apt sources."
    if ! confirm "Add AMD ROCm apt repo and install rocm-hip-sdk? (large)" "Y"; then
      return 1
    fi

    apt_install ca-certificates || return 1
    apt_install wget || return 1
    apt_install gpg || return 1

    local codename version
    codename="$(ubuntu_codename)"
    version="${ROCM_APT_VERSION:-6.0}"

    $SUDO mkdir -p /etc/apt/keyrings
    if ! wget -qO- "https://repo.radeon.com/rocm/rocm.gpg.key" | $SUDO gpg --dearmor -o /etc/apt/keyrings/rocm.gpg; then
      echo "Failed to install ROCm apt key." >&2
      return 1
    fi
    echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/rocm.gpg] https://repo.radeon.com/rocm/apt/${version} ${codename} main" | \
      $SUDO tee /etc/apt/sources.list.d/rocm.list >/dev/null

    need_apt_update=0
    apt_install rocm-hip-sdk || return 1
  fi

  local hipcc_bin=""
  hipcc_bin="$(detect_hipcc 2>/dev/null || true)"
  if [[ -z "$hipcc_bin" ]]; then
    echo "hipcc still not found after installation. You may need to reboot or fix ROCm install." >&2
    return 1
  fi
  return 0
}

install_oneapi_compiler() {
  # Best-effort oneAPI DPC++ compiler install. May require adding Intel's apt repo.
  if detect_oneapi_cxx >/dev/null 2>&1; then
    return 0
  fi

  echo "Attempting to install Intel oneAPI DPC++ compiler (dpcpp/icpx)..."

  if apt_install intel-oneapi-dpcpp-cpp-compiler || apt_install intel-oneapi-compiler-dpcpp-cpp; then
    :
  else
    echo "Intel oneAPI packages not available in current apt sources."
    if ! confirm "Add Intel oneAPI apt repo and install the compiler? (large)" "Y"; then
      return 1
    fi

    apt_install ca-certificates || return 1
    apt_install wget || return 1
    apt_install gpg || return 1

    $SUDO mkdir -p /usr/share/keyrings
    if ! wget -qO- "https://apt.repos.intel.com/oneapi/gpgkey" | $SUDO gpg --dearmor -o /usr/share/keyrings/oneapi-archive-keyring.gpg; then
      echo "Failed to install Intel oneAPI apt key." >&2
      return 1
    fi
    echo "deb [signed-by=/usr/share/keyrings/oneapi-archive-keyring.gpg] https://apt.repos.intel.com/oneapi all main" | \
      $SUDO tee /etc/apt/sources.list.d/oneapi.list >/dev/null

    need_apt_update=0
    if ! (apt_install intel-oneapi-dpcpp-cpp-compiler || apt_install intel-oneapi-compiler-dpcpp-cpp); then
      return 1
    fi
  fi

  local cxx=""
  cxx="$(detect_oneapi_cxx 2>/dev/null || true)"
  if [[ -z "$cxx" ]]; then
    echo "dpcpp/icpx still not found after installation. You may need to source oneAPI env:" >&2
    echo "  source /opt/intel/oneapi/setvars.sh" >&2
    return 1
  fi
  return 0
}

prepare_cmake_build_dir() {
  # prepare_cmake_build_dir SOURCE_DIR BUILD_DIR
  # If BUILD_DIR contains a stale cache from a different source/build path, wipe it to avoid:
  # "CMakeCache.txt directory ... is different..." / "source ... does not match ..."
  local source_dir="$1"
  local build_dir="$2"

  mkdir -p "$build_dir"
  local cache_file="$build_dir/CMakeCache.txt"
  if [[ ! -f "$cache_file" ]]; then
    return 0
  fi

  local cache_home cache_build expected_home expected_build
  cache_home="$(cmake_cache_get "CMAKE_HOME_DIRECTORY:INTERNAL" "$cache_file")"
  cache_build="$(cmake_cache_get "CMAKE_CACHEFILE_DIR:INTERNAL" "$cache_file")"
  expected_home="$(canonical_path "$source_dir")"
  expected_build="$(canonical_path "$build_dir")"

  if [[ -n "$cache_home" && "$(canonical_path "$cache_home")" != "$expected_home" ]] || \
     [[ -n "$cache_build" && "$(canonical_path "$cache_build")" != "$expected_build" ]]; then
    if [[ "$build_dir" != "$APP_HOME/"* || "$build_dir" == "$APP_HOME" || "$build_dir" == "/" ]]; then
      echo "Refusing to clean suspicious CMake build dir: $build_dir" >&2
      return 1
    fi
    echo "Detected stale CMake cache in: $build_dir"
    echo "  cache source: ${cache_home:-<unknown>}"
    echo "  cache build:  ${cache_build:-<unknown>}"
    echo "Cleaning build directory to reconfigure..."
    rm -rf "$build_dir"
    mkdir -p "$build_dir"
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

MODEL_RESULT="skipped" # downloaded|present|skipped|failed

CUDA_RESULT="skipped" # built|skipped|failed
CUDA_BUILD_DIR="$APP_HOME/native/cuda/build"
CUDA_LIB_SO="$CUDA_BUILD_DIR/liblc0j_cuda.so"
CUDA_T5_LIB_SO="$CUDA_BUILD_DIR/libt5_cuda.so"

ROCM_RESULT="skipped" # built|skipped|failed
ROCM_BUILD_DIR="$APP_HOME/native/rocm/build"
ROCM_LIB_SO="$ROCM_BUILD_DIR/liblc0j_rocm.so"
ROCM_T5_LIB_SO="$ROCM_BUILD_DIR/libt5_rocm.so"

ONEAPI_RESULT="skipped" # built|skipped|failed
ONEAPI_BUILD_DIR="$APP_HOME/native/oneapi/build"
ONEAPI_LIB_SO="$ONEAPI_BUILD_DIR/liblc0j_oneapi.so"
ONEAPI_T5_LIB_SO="$ONEAPI_BUILD_DIR/libt5_oneapi.so"

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
  step "Building CUDA backends (native/cuda) via nvcc (CMake fallback)..."
  if ! nvcc -shared -Xcompiler=-fPIC -O3 --std=c++17 \
      -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" \
      -o "$CUDA_LIB_SO" "$APP_HOME/native/cuda/lc0j_cuda_jni.cu" -lcudart; then
    return 1
  fi
  if ! nvcc -shared -Xcompiler=-fPIC -O3 --std=c++17 \
      -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" \
      -o "$CUDA_T5_LIB_SO" "$APP_HOME/native/cuda/t5_cuda_jni.cu" -lcudart -lcublas; then
    return 1
  fi
  return 0
}

try_build_cuda_backend() {
  if [[ "$CUDA_MODE" == "no" ]]; then
    CUDA_RESULT="skipped"
    return 0
  fi
  local has_cuda=0
  if [[ -d "$APP_HOME/native/cuda" ]]; then
    has_cuda=1
  fi
  if [[ $has_cuda -eq 0 ]]; then
    CUDA_RESULT="skipped"
    return 0
  fi

  local want_build=0
  if [[ "$CUDA_MODE" == "yes" ]]; then
    want_build=1
  elif [[ "$CUDA_MODE" == "auto" ]]; then
    if confirm "Build optional CUDA backend (native/cuda JNI)? (auto-uses GPU if available)" "Y"; then
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
      if [[ $has_cuda -eq 1 ]]; then
        echo "Trying a manual nvcc build as a fallback..."
        if manual_build_cuda_backend; then
          step "CUDA backends built: $CUDA_LIB_SO, $CUDA_T5_LIB_SO"
          CUDA_RESULT="built"
        else
          CUDA_RESULT="failed"
        fi
      fi
      return 0
    fi
  fi
  if ! cmake_version_ok; then
    echo "CMake 3.18+ is required for the CUDA backend."
    echo "Found: $(cmake --version 2>/dev/null | head -n1 || true)"
    if [[ $has_cuda -eq 1 ]]; then
      echo "Trying a manual nvcc build as a fallback..."
      if manual_build_cuda_backend; then
        step "CUDA backends built: $CUDA_LIB_SO, $CUDA_T5_LIB_SO"
        CUDA_RESULT="built"
      else
        CUDA_RESULT="failed"
      fi
    fi
    return 0
  fi

  echo
  step "Building CUDA backends (native/cuda)..."
  maybe_export_java_home

  prepare_cmake_build_dir "$APP_HOME/native/cuda" "$CUDA_BUILD_DIR"
  if cmake -S "$APP_HOME/native/cuda" -B "$CUDA_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release && \
     cmake --build "$CUDA_BUILD_DIR" -j "$JOBS"; then
    if [[ -f "$CUDA_LIB_SO" ]]; then
      step "CUDA backend built: $CUDA_LIB_SO"
    else
      warn "CUDA backend build succeeded, but expected library not found at: $CUDA_LIB_SO"
    fi
    if [[ -f "$CUDA_T5_LIB_SO" ]]; then
      step "T5 CUDA backend built: $CUDA_T5_LIB_SO"
    else
      warn "T5 CUDA backend build succeeded, but expected library not found at: $CUDA_T5_LIB_SO"
    fi
    CUDA_RESULT="built"
  else
    echo
    warn "CUDA backend build failed. Continuing with CPU-only install."
    echo "To retry later:"
    echo "  cmake -S native/cuda -B native/cuda/build -DCMAKE_BUILD_TYPE=Release"
    echo "  cmake --build native/cuda/build -j"
    echo "Troubleshooting: native/cuda/README.md"
    CUDA_RESULT="failed"
  fi
  return 0
}

manual_build_rocm_backend() {
  local hipcc_bin=""
  hipcc_bin="$(detect_hipcc 2>/dev/null || true)"
  if [[ -z "$hipcc_bin" ]]; then
    echo "hipcc not found."
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

  mkdir -p "$ROCM_BUILD_DIR"
  echo
  step "Building ROCm backends (native/rocm) via hipcc (CMake fallback)..."
  if "$hipcc_bin" -shared -fPIC -O3 --std=c++17 \
      -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" \
      -o "$ROCM_LIB_SO" "$APP_HOME/native/rocm/lc0j_rocm_jni.hip"; then
    if "$hipcc_bin" -shared -fPIC -O3 --std=c++17 \
        -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" \
        -o "$ROCM_T5_LIB_SO" "$APP_HOME/native/rocm/t5_rocm_jni.hip" -lhipblas; then
      return 0
    fi
  fi
  return 1
}

try_build_rocm_backend() {
  if [[ "$ROCM_MODE" == "no" ]]; then
    ROCM_RESULT="skipped"
    return 0
  fi
  if [[ ! -d "$APP_HOME/native/rocm" ]]; then
    ROCM_RESULT="skipped"
    return 0
  fi

  local want_build=0
  if [[ "$ROCM_MODE" == "yes" ]]; then
    want_build=1
  elif [[ "$ROCM_MODE" == "auto" ]]; then
    if confirm "Build optional ROCm backend (native/rocm JNI)? (auto-uses GPU if available)" "Y"; then
      want_build=1
    else
      ROCM_RESULT="skipped"
      return 0
    fi
  fi

  if [[ $want_build -eq 0 ]]; then
    ROCM_RESULT="skipped"
    return 0
  fi

  if ! command -v g++ >/dev/null 2>&1 || ! command -v make >/dev/null 2>&1; then
    echo "Compiler toolchain not found; installing build-essential..."
    if ! apt_install build-essential; then
      echo "Failed to install build-essential."
      ROCM_RESULT="failed"
      return 0
    fi
  fi

  local hipcc_bin=""
  hipcc_bin="$(detect_hipcc 2>/dev/null || true)"
  if [[ -z "$hipcc_bin" ]]; then
    echo "ROCm toolchain (hipcc) not found."
    if confirm "Install ROCm HIP toolchain now? (may add AMD apt repo; large)" "Y"; then
      if ! install_rocm_hip_toolchain; then
        echo "Failed to install ROCm HIP toolchain."
        ROCM_RESULT="failed"
        return 0
      fi
      hipcc_bin="$(detect_hipcc 2>/dev/null || true)"
    else
      ROCM_RESULT="failed"
      return 0
    fi
  fi

  if ! command -v cmake >/dev/null 2>&1; then
    echo "CMake not found; trying a manual hipcc build as a fallback..."
    if manual_build_rocm_backend; then
      step "ROCm backends built: $ROCM_LIB_SO, $ROCM_T5_LIB_SO"
      ROCM_RESULT="built"
      return 0
    fi
    ROCM_RESULT="failed"
    return 0
  fi

  echo
  step "Building ROCm backends (native/rocm)..."
  maybe_export_java_home

  prepare_cmake_build_dir "$APP_HOME/native/rocm" "$ROCM_BUILD_DIR"
  if cmake -S "$APP_HOME/native/rocm" -B "$ROCM_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release -DCMAKE_HIP_COMPILER="$hipcc_bin" && \
     cmake --build "$ROCM_BUILD_DIR" -j "$JOBS"; then
    if [[ -f "$ROCM_LIB_SO" ]]; then
      step "ROCm backend built: $ROCM_LIB_SO"
    else
      warn "ROCm backend build succeeded, but expected library not found at: $ROCM_LIB_SO"
    fi
    if [[ -f "$ROCM_T5_LIB_SO" ]]; then
      step "T5 ROCm backend built: $ROCM_T5_LIB_SO"
    else
      warn "T5 ROCm backend build succeeded, but expected library not found at: $ROCM_T5_LIB_SO"
    fi
    ROCM_RESULT="built"
    return 0
  fi

  echo
  warn "ROCm backend build failed. Continuing with CPU-only install."
  echo "To retry later:"
  echo "  cmake -S native/rocm -B native/rocm/build -DCMAKE_BUILD_TYPE=Release -DCMAKE_HIP_COMPILER=hipcc"
  echo "  cmake --build native/rocm/build -j"
  ROCM_RESULT="failed"
  return 0
}

manual_build_oneapi_backend() {
  local cxx=""
  cxx="$(detect_oneapi_cxx 2>/dev/null || true)"
  if [[ -z "$cxx" ]]; then
    echo "dpcpp/icpx not found."
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

  mkdir -p "$ONEAPI_BUILD_DIR"
  echo
  step "Building oneAPI backends (native/oneapi) via $cxx (CMake fallback)..."
  if "$cxx" -fsycl -shared -fPIC -O3 --std=c++17 \
      -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" \
      -o "$ONEAPI_LIB_SO" "$APP_HOME/native/oneapi/lc0j_oneapi_jni.cpp"; then
    if "$cxx" -fsycl -shared -fPIC -O3 --std=c++17 \
        -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" \
        -o "$ONEAPI_T5_LIB_SO" "$APP_HOME/native/oneapi/t5_oneapi_jni.cpp"; then
      return 0
    fi
  fi
  return 1
}

try_build_oneapi_backend() {
  if [[ "$ONEAPI_MODE" == "no" ]]; then
    ONEAPI_RESULT="skipped"
    return 0
  fi
  if [[ ! -d "$APP_HOME/native/oneapi" ]]; then
    ONEAPI_RESULT="skipped"
    return 0
  fi

  local want_build=0
  if [[ "$ONEAPI_MODE" == "yes" ]]; then
    want_build=1
  elif [[ "$ONEAPI_MODE" == "auto" ]]; then
    if confirm "Build optional oneAPI backend (native/oneapi JNI)? (auto-uses GPU if available)" "Y"; then
      want_build=1
    else
      ONEAPI_RESULT="skipped"
      return 0
    fi
  fi

  if [[ $want_build -eq 0 ]]; then
    ONEAPI_RESULT="skipped"
    return 0
  fi

  if ! command -v g++ >/dev/null 2>&1 || ! command -v make >/dev/null 2>&1; then
    echo "Compiler toolchain not found; installing build-essential..."
    if ! apt_install build-essential; then
      echo "Failed to install build-essential."
      ONEAPI_RESULT="failed"
      return 0
    fi
  fi

  local oneapi_cxx=""
  oneapi_cxx="$(detect_oneapi_cxx 2>/dev/null || true)"
  if [[ -z "$oneapi_cxx" ]]; then
    echo "oneAPI compiler (dpcpp/icpx) not found."
    if confirm "Install Intel oneAPI DPC++ compiler now? (may add Intel apt repo; large)" "Y"; then
      if ! install_oneapi_compiler; then
        echo "Failed to install Intel oneAPI compiler."
        ONEAPI_RESULT="failed"
        return 0
      fi
      oneapi_cxx="$(detect_oneapi_cxx 2>/dev/null || true)"
    else
      ONEAPI_RESULT="failed"
      return 0
    fi
  fi

  if ! command -v cmake >/dev/null 2>&1; then
    echo "CMake not found; trying a manual oneAPI build as a fallback..."
    if manual_build_oneapi_backend; then
      step "oneAPI backends built: $ONEAPI_LIB_SO, $ONEAPI_T5_LIB_SO"
      ONEAPI_RESULT="built"
      return 0
    fi
    ONEAPI_RESULT="failed"
    return 0
  fi

  echo
  step "Building oneAPI backends (native/oneapi)..."
  maybe_export_java_home

  prepare_cmake_build_dir "$APP_HOME/native/oneapi" "$ONEAPI_BUILD_DIR"
  if cmake -S "$APP_HOME/native/oneapi" -B "$ONEAPI_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_COMPILER="$oneapi_cxx" && \
     cmake --build "$ONEAPI_BUILD_DIR" -j "$JOBS"; then
    if [[ -f "$ONEAPI_LIB_SO" ]]; then
      step "oneAPI backend built: $ONEAPI_LIB_SO"
    else
      warn "oneAPI backend build succeeded, but expected library not found at: $ONEAPI_LIB_SO"
    fi
    if [[ -f "$ONEAPI_T5_LIB_SO" ]]; then
      step "T5 oneAPI backend built: $ONEAPI_T5_LIB_SO"
    else
      warn "T5 oneAPI backend build succeeded, but expected library not found at: $ONEAPI_T5_LIB_SO"
    fi
    ONEAPI_RESULT="built"
    return 0
  fi

  echo
  warn "oneAPI backend build failed. Continuing with CPU-only install."
  echo "To retry later:"
  echo "  cmake -S native/oneapi -B native/oneapi/build -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_COMPILER=$oneapi_cxx"
  echo "  cmake --build native/oneapi/build -j"
  ONEAPI_RESULT="failed"
  return 0
}

title "ChessRTK installer"
info "Repo: $APP_HOME"
info "Launcher: $LAUNCHER"

section "Prerequisites"
JAVA_OK=0
JAVAC_OK=0

step "Checking Java runtime"
if command -v java >/dev/null 2>&1; then
  # Try to detect major version (works for modern OpenJDK)
  ver="$(java -version 2>&1 | head -n1 | sed -E 's/.*version "?([0-9]+).*/\1/')"
  if [[ "$ver" =~ ^[0-9]+$ && "$ver" -ge 17 ]]; then
    JAVA_OK=1
  else
    warn "Found Java, but version <$ver> (<17)."
  fi
else
  warn "Java not found."
fi

step "Checking Java compiler"
if command -v javac >/dev/null 2>&1; then
  JAVAC_OK=1
else
  warn "javac not found (needed to build)."
fi

if [[ $JAVA_OK -eq 0 || $JAVAC_OK -eq 0 ]]; then
  if confirm "Install OpenJDK 17 (JRE+JDK) now?" "Y"; then
    apt_install openjdk-17-jdk
    JAVA_OK=1
    JAVAC_OK=1
  else
    warn "Skipping Java installation. Ensure Java 17 (and javac) is available."
  fi
fi

step "Checking optional Stockfish engine"
if command -v stockfish >/dev/null 2>&1; then
  step "Stockfish found at: $(command -v stockfish)"
else
  if confirm "Install Stockfish (optional, recommended)?" "Y"; then
    apt_install stockfish
  else
    warn "Skipping Stockfish installation. You can set your engine path in config/default.engine.toml."
  fi
fi

section "Workspace"
step "Creating output and log directories"
mkdir -p "$OUT_DIR" "$APP_HOME/dump" "$APP_HOME/session"

section "Model Weights"
fetch_model_weights

section "Java Build"
step "Compiling sources with javac"
find "$APP_HOME/src" -name "*.java" -print0 | xargs -0 javac --release 17 -d "$OUT_DIR"

step "Packaging runnable jar"
jar --create --file "$JAR_PATH" --main-class application.Main -C "$OUT_DIR" .

section "Native Backends"
try_build_cuda_backend
if [[ $REQUIRE_CUDA -eq 1 && "$CUDA_RESULT" != "built" ]]; then
  err "CUDA backend was required (--require-cuda) but was not built."
  exit 1
fi

try_build_rocm_backend
if [[ $REQUIRE_ROCM -eq 1 && "$ROCM_RESULT" != "built" ]]; then
  err "ROCm backend was required (--require-rocm/--require-amd) but was not built."
  exit 1
fi

try_build_oneapi_backend
if [[ $REQUIRE_ONEAPI -eq 1 && "$ONEAPI_RESULT" != "built" ]]; then
  err "oneAPI backend was required (--require-oneapi/--require-intel) but was not built."
  exit 1
fi

section "Launcher"
if [[ $INSTALL_LAUNCHER -eq 1 ]]; then
  step "Installing launcher to $LAUNCHER"
  LAUNCHER_TMP="$(mktemp)"
  cat > "$LAUNCHER_TMP" <<EOF
#!/usr/bin/env bash
set -euo pipefail
APP_HOME="$APP_HOME"
JAVA_BIN="\${JAVA_BIN:-java}"
JAVA_OPTS="\${JAVA_OPTS:-}"
cd "\$APP_HOME"
LIB_DIRS=()
if [[ -f "\$APP_HOME/native/cuda/build/liblc0j_cuda.so" || -f "\$APP_HOME/native/cuda/build/libt5_cuda.so" ]]; then
  LIB_DIRS+=("\$APP_HOME/native/cuda/build")
fi
if [[ -f "\$APP_HOME/native/rocm/build/liblc0j_rocm.so" || -f "\$APP_HOME/native/rocm/build/libt5_rocm.so" ]]; then
  LIB_DIRS+=("\$APP_HOME/native/rocm/build")
fi
if [[ -f "\$APP_HOME/native/oneapi/build/liblc0j_oneapi.so" || -f "\$APP_HOME/native/oneapi/build/libt5_oneapi.so" ]]; then
  LIB_DIRS+=("\$APP_HOME/native/oneapi/build")
fi
LIB_OPT=""
if [[ \${#LIB_DIRS[@]} -gt 0 && "\$JAVA_OPTS" != *"-Djava.library.path="* ]]; then
  LIB_PATH="\$(IFS=:; echo "\${LIB_DIRS[*]}")"
  LIB_OPT="-Djava.library.path=\$LIB_PATH"
fi
# shellcheck disable=SC2086
exec "\$JAVA_BIN" \$JAVA_OPTS \$LIB_OPT -jar "\$APP_HOME/crtk.jar" "\$@"
EOF

  $SUDO mv "$LAUNCHER_TMP" "$LAUNCHER"
  $SUDO chmod +x "$LAUNCHER"
else
  warn "Skipping launcher install (--no-launcher)."
fi

section "Summary"
title "Done"
if [[ $INSTALL_LAUNCHER -eq 1 ]]; then
  step "Launcher installed: $LAUNCHER"
fi
if [[ "$CUDA_RESULT" == "built" ]]; then
  step "CUDA backend: built ($CUDA_LIB_SO)"
elif [[ "$CUDA_RESULT" == "failed" ]]; then
  warn "CUDA backend: not built (CPU fallback)"
fi
if [[ "$ROCM_RESULT" == "built" ]]; then
  step "ROCm backends: built ($ROCM_LIB_SO, $ROCM_T5_LIB_SO)"
elif [[ "$ROCM_RESULT" == "failed" ]]; then
  warn "ROCm backend: not built (CPU fallback)"
fi
if [[ "$ONEAPI_RESULT" == "built" ]]; then
  step "oneAPI backends: built ($ONEAPI_LIB_SO, $ONEAPI_T5_LIB_SO)"
elif [[ "$ONEAPI_RESULT" == "failed" ]]; then
  warn "oneAPI backend: not built (CPU fallback)"
fi
if [[ "$MODEL_RESULT" == "downloaded" ]]; then
  step "Model weights: downloaded into $MODEL_DIR"
elif [[ "$MODEL_RESULT" == "present" ]]; then
  step "Model weights: already present in $MODEL_DIR"
elif [[ "$MODEL_RESULT" == "failed" ]]; then
  warn "Model weights: download failed (see models/README.md)"
fi

section "Next Steps"
info "$APP_NAME help"
info "Run from anywhere with '$APP_NAME'."
