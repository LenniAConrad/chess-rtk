#!/usr/bin/env bash
# crtk uninstaller (Ubuntu)
# Repo: https://github.com/LenniAConrad/chess-rtk
# Removes the launcher and local build artifacts from this repo.
set -euo pipefail

APP_NAME="crtk"
LAUNCHER="/usr/local/bin/$APP_NAME"

# Resolve repo root (assume script is placed in scripts/)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd -- "$SCRIPT_DIR/.." && pwd)"

REMOVE_LAUNCHER=1
REMOVE_BUILD=1
REMOVE_DATA=0
ASSUME_YES=0

usage() {
  cat <<EOF
Usage: ./uninstall.sh [--all|--remove-data] [--keep-build] [--keep-launcher] [-y|--yes]

Options:
  --all, --remove-data   Remove data dirs created by the app (dump/, session/)
  --keep-build           Keep build artifacts (out/, crtk.jar, native/*/build/)
  --keep-launcher        Keep /usr/local/bin/crtk
  -y, --yes              Assume yes for prompts
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all|--remove-data)
      REMOVE_DATA=1
      shift
      ;;
    --keep-build)
      REMOVE_BUILD=0
      shift
      ;;
    --keep-launcher)
      REMOVE_LAUNCHER=0
      shift
      ;;
    -y|--yes|--force)
      ASSUME_YES=1
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

SUDO=''
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO='sudo'
  fi
fi

confirm() {
  # confirm "Prompt" [default:Y|N]
  local prompt="${1:-Proceed?}"
  local def="${2:-Y}"
  local hint="[Y/n]"
  if [[ $ASSUME_YES -eq 1 ]]; then
    return 0
  fi
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

safe_remove_in_repo() {
  local target="$1"
  if [[ ! -e "$target" ]]; then
    return 0
  fi
  local resolved
  resolved="$(canonical_path "$target")"
  if [[ -z "$resolved" ]]; then
    echo "Failed to resolve path: $target" >&2
    return 1
  fi
  if [[ "$resolved" == "$APP_HOME" || "$resolved" == "/" ]]; then
    echo "Refusing to remove repo root: $resolved" >&2
    return 1
  fi
  if [[ "$resolved" != "$APP_HOME/"* ]]; then
    echo "Refusing to remove path outside repo: $resolved" >&2
    return 1
  fi
  rm -rf "$resolved"
}

echo "crtk uninstaller ~"
echo "Repo path: $APP_HOME"
echo

if [[ $REMOVE_BUILD -eq 1 ]]; then
  build_targets=()
  if [[ -d "$APP_HOME/out" ]]; then
    build_targets+=("$APP_HOME/out")
  fi
  if [[ -f "$APP_HOME/crtk.jar" ]]; then
    build_targets+=("$APP_HOME/crtk.jar")
  fi
  if [[ -d "$APP_HOME/native/cuda/build" ]]; then
    build_targets+=("$APP_HOME/native/cuda/build")
  fi
  if [[ -d "$APP_HOME/native/rocm/build" ]]; then
    build_targets+=("$APP_HOME/native/rocm/build")
  fi
  if [[ -d "$APP_HOME/native/oneapi/build" ]]; then
    build_targets+=("$APP_HOME/native/oneapi/build")
  fi

  if [[ ${#build_targets[@]} -gt 0 ]]; then
    echo "Build artifacts found:"
    for t in "${build_targets[@]}"; do
      echo "  - $t"
    done
    if confirm "Remove these build artifacts?" "Y"; then
      for t in "${build_targets[@]}"; do
        safe_remove_in_repo "$t"
      done
    else
      echo "Skipping build artifact removal."
    fi
  fi
fi

if [[ $REMOVE_DATA -eq 1 ]]; then
  data_targets=()
  if [[ -d "$APP_HOME/dump" ]]; then
    data_targets+=("$APP_HOME/dump")
  fi
  if [[ -d "$APP_HOME/session" ]]; then
    data_targets+=("$APP_HOME/session")
  fi
  if [[ ${#data_targets[@]} -gt 0 ]]; then
    echo "Data directories found:"
    for t in "${data_targets[@]}"; do
      echo "  - $t"
    done
    if confirm "Remove these data directories?" "N"; then
      for t in "${data_targets[@]}"; do
        safe_remove_in_repo "$t"
      done
    else
      echo "Skipping data directory removal."
    fi
  fi
fi

if [[ $REMOVE_LAUNCHER -eq 1 ]]; then
  if [[ -e "$LAUNCHER" ]]; then
    if [[ "${EUID:-$(id -u)}" -ne 0 && -z "$SUDO" ]]; then
      echo "Cannot remove $LAUNCHER (need root or sudo). Skipping." >&2
    else
      if confirm "Remove launcher at $LAUNCHER?" "Y"; then
        $SUDO rm -f "$LAUNCHER"
      else
        echo "Skipping launcher removal."
      fi
    fi
  fi
fi

echo
echo "Done."
echo
echo " * Note: This does not uninstall system packages (e.g., OpenJDK, Stockfish, CUDA, ROCm, oneAPI)."
