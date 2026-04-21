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
      err "Unknown argument: $1"
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
    err "Failed to resolve path: $target"
    return 1
  fi
  if [[ "$resolved" == "$APP_HOME" || "$resolved" == "/" ]]; then
    err "Refusing to remove repo root: $resolved"
    return 1
  fi
  if [[ "$resolved" != "$APP_HOME/"* ]]; then
    err "Refusing to remove path outside repo: $resolved"
    return 1
  fi
  rm -rf "$resolved"
}

title "ChessRTK uninstaller"
info "Repo: $APP_HOME"
info "Launcher: $LAUNCHER"

section "Build Artifacts"
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
    step "Build artifacts found"
    for t in "${build_targets[@]}"; do
      info "- $t"
    done
    if confirm "Remove these build artifacts?" "Y"; then
      for t in "${build_targets[@]}"; do
        safe_remove_in_repo "$t"
      done
      step "Build artifacts removed"
    else
      warn "Skipping build artifact removal."
    fi
  else
    step "No build artifacts found"
  fi
else
  step "Keeping build artifacts (--keep-build)"
fi

section "Data Directories"
if [[ $REMOVE_DATA -eq 1 ]]; then
  data_targets=()
  if [[ -d "$APP_HOME/dump" ]]; then
    data_targets+=("$APP_HOME/dump")
  fi
  if [[ -d "$APP_HOME/session" ]]; then
    data_targets+=("$APP_HOME/session")
  fi
  if [[ ${#data_targets[@]} -gt 0 ]]; then
    step "Data directories found"
    for t in "${data_targets[@]}"; do
      info "- $t"
    done
    if confirm "Remove these data directories?" "N"; then
      for t in "${data_targets[@]}"; do
        safe_remove_in_repo "$t"
      done
      step "Data directories removed"
    else
      warn "Skipping data directory removal."
    fi
  else
    step "No data directories found"
  fi
else
  step "Keeping data directories (use --remove-data to delete dump/ and session/)"
fi

section "Launcher"
if [[ $REMOVE_LAUNCHER -eq 1 ]]; then
  if [[ -e "$LAUNCHER" ]]; then
    if [[ "${EUID:-$(id -u)}" -ne 0 && -z "$SUDO" ]]; then
      warn "Cannot remove $LAUNCHER (need root or sudo)."
    else
      if confirm "Remove launcher at $LAUNCHER?" "Y"; then
        $SUDO rm -f "$LAUNCHER"
        step "Launcher removed"
      else
        warn "Skipping launcher removal."
      fi
    fi
  else
    step "No launcher found at $LAUNCHER"
  fi
else
  step "Keeping launcher (--keep-launcher)"
fi

section "Summary"
title "Done"
info "System packages were not removed."
info "OpenJDK, Stockfish, CUDA, ROCm, and oneAPI stay installed if present."
