#!/usr/bin/env bash
# crtk updater (Debian/Ubuntu-friendly)
# Repo: https://github.com/LenniAConrad/chess-rtk
# Fast-forwards the current checkout and reruns install.sh.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd -- "$SCRIPT_DIR/.." && pwd)"
INSTALL_SCRIPT="$APP_HOME/install.sh"

PULL_CHANGES=1
INSTALL_ARGS=()

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
Usage: ./update.sh [--no-pull] [install.sh options...]

Run from repo root as:
  ./scripts/update.sh [--no-pull] [install.sh options...]

Updates the current checkout (git pull --ff-only) and reruns ./install.sh.

Options:
  --no-pull   Skip git pull and just rerun ./install.sh
  -h, --help  Show this help

Any remaining arguments are passed through to ./install.sh, for example:
  ./scripts/update.sh --no-cuda
  ./scripts/update.sh --rocm --no-launcher
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
    --no-pull)
      PULL_CHANGES=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      INSTALL_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ ! -x "$INSTALL_SCRIPT" ]]; then
  err "Missing installer script: $INSTALL_SCRIPT"
  exit 1
fi

ensure_clean_worktree() {
  if ! git diff --quiet --ignore-submodules -- || ! git diff --cached --quiet --ignore-submodules --; then
    err "Refusing to pull with local git changes in the worktree."
    echo "Commit/stash your changes first, or rerun with --no-pull to rebuild the current checkout." >&2
    exit 1
  fi
}

update_checkout() {
  if ! command -v git >/dev/null 2>&1; then
    err "git is required to update the checkout."
    exit 1
  fi

  if ! git -C "$APP_HOME" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    err "This directory is not a git checkout: $APP_HOME"
    echo "Use --no-pull if you only want to rerun ./install.sh." >&2
    exit 1
  fi

  local branch
  branch="$(git -C "$APP_HOME" symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
  if [[ -z "$branch" ]]; then
    err "Detached HEAD detected; refusing to pull automatically."
    echo "Check out a branch first, or rerun with --no-pull." >&2
    exit 1
  fi

  ensure_clean_worktree

  step "Updating git checkout on branch: $branch"
  git -C "$APP_HOME" pull --ff-only
}

title "ChessRTK updater"
info "Repo: $APP_HOME"

if [[ $PULL_CHANGES -eq 1 ]]; then
  section "Checkout"
  update_checkout
else
  section "Checkout"
  step "Skipping git pull (--no-pull)"
fi

section "Install"
step "Running installer"
exec "$INSTALL_SCRIPT" "${INSTALL_ARGS[@]}"
