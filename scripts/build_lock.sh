#!/usr/bin/env bash

crtk_install_build_lock_cleanup_trap() {
  local cleanup='rm -rf "$CRTK_BUILD_LOCK_DIR_TO_CLEAN"'
  local previous
  previous="$(trap -p EXIT || true)"
  if [[ -z "$previous" ]]; then
    trap "$cleanup" EXIT
    return
  fi

  local previous_command
  previous_command="${previous#trap -- }"
  previous_command="${previous_command% EXIT}"
  eval "previous_command=$previous_command"
  trap "$cleanup"$'\n'"$previous_command" EXIT
}

crtk_acquire_build_lock() {
  local root_dir="$1"
  if [[ "${CRTK_BUILD_LOCK:-1}" == "0" ]]; then
    return
  fi

  local lock_path
  if command -v git >/dev/null 2>&1 && git -C "$root_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    lock_path="$(git -C "$root_dir" rev-parse --git-path crtk-build.lock)"
  else
    lock_path="$root_dir/.crtk-build.lock"
  fi
  mkdir -p "$(dirname "$lock_path")"

  if command -v flock >/dev/null 2>&1; then
    exec 9>"$lock_path"
    if ! flock -n 9; then
      echo "==> waiting for build lock ($lock_path)"
      flock 9
    fi
    return
  fi

  local lock_dir="${lock_path}.dir"
  until mkdir "$lock_dir" 2>/dev/null; do
    echo "==> waiting for build lock ($lock_dir)"
    sleep 2
  done
  CRTK_BUILD_LOCK_DIR_TO_CLEAN="$lock_dir"
  crtk_install_build_lock_cleanup_trap
}
