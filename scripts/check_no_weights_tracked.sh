#!/usr/bin/env bash
set -euo pipefail

pattern='^(nets/|.*\.(pb\.gz|pb|onnx|nnue)$|.*weights.*)'

if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git is required." >&2
  exit 2
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not inside a git work tree." >&2
  exit 2
fi

tracked="$(git ls-files || true)"
if [[ -z "${tracked}" ]]; then
  exit 0
fi

if command -v rg >/dev/null 2>&1; then
  if echo "${tracked}" | rg -n "${pattern}" >/dev/null; then
    echo "ERROR: tracked weight files detected (should be gitignored):" >&2
    echo "${tracked}" | rg -n "${pattern}" >&2
    exit 1
  fi
  exit 0
fi

if echo "${tracked}" | grep -En "${pattern}" >/dev/null; then
  echo "ERROR: tracked weight files detected (should be gitignored):" >&2
  echo "${tracked}" | grep -En "${pattern}" >&2
  exit 1
fi

exit 0
