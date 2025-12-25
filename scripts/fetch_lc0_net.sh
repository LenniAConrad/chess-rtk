#!/usr/bin/env bash
#
# Fetch Leela Chess Zero (Lc0) network weights into a local directory (default: ./nets).
#
# Usage examples:
#   ./scripts/fetch_lc0_net.sh --url <URL> [--sha256 <HASH>] [--out nets]
#
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/fetch_lc0_net.sh --url <URL> [--sha256 <HASH>] [--out nets]

Options:
  --url <URL>         Required. Download URL.
  --sha256 <HASH>     Optional. Expected SHA-256 (hex).
  --out <DIR>         Optional. Output directory (default: nets).
  -h, --help          Show help.
EOF
}

url=""
sha256=""
out_dir="nets"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url)
      url="${2:-}"; shift 2 ;;
    --sha256)
      sha256="${2:-}"; shift 2 ;;
    --out)
      out_dir="${2:-}"; shift 2 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2 ;;
  esac
done

if [[ -z "${url}" ]]; then
  echo "Missing required: --url <URL>" >&2
  usage >&2
  exit 2
fi

mkdir -p "${out_dir}"

derive_name() {
  local u="$1"
  local path_part base sha
  path_part="${u%%\?*}"
  base="$(basename -- "${path_part}")"

  if [[ -n "${base}" && "${base}" != "/" && "${base}" != "." && "${base}" != "get_network" ]]; then
    echo "${base}"
    return 0
  fi

  sha="$(printf '%s' "${u}" | sed -n 's/.*[?&]sha=\\([^&]*\\).*/\\1/p')"
  if [[ -n "${sha}" ]]; then
    echo "lc0_${sha}.pb.gz"
    return 0
  fi

  echo "lc0_net_$(date +%Y%m%d_%H%M%S)"
}

name="$(derive_name "${url}")"
final_path="${out_dir%/}/${name}"
tmp_path="${final_path}.part"

cleanup() {
  rm -f "${tmp_path}"
}
trap cleanup EXIT

download() {
  local u="$1"
  local out="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --retry-delay 1 -o "${out}" "${u}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${out}" "${u}"
  else
    echo "Neither curl nor wget is available for downloading." >&2
    exit 1
  fi
}

verify_sha256() {
  local expected="$1"
  local file="$2"
  local actual=""

  if command -v sha256sum >/dev/null 2>&1; then
    actual="$(sha256sum "${file}" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then
    actual="$(shasum -a 256 "${file}" | awk '{print $1}')"
  else
    echo "No SHA-256 tool found (need sha256sum or shasum -a 256)." >&2
    exit 1
  fi

  if [[ "${actual,,}" != "${expected,,}" ]]; then
    echo "SHA-256 mismatch for ${file}" >&2
    echo "  expected: ${expected}" >&2
    echo "  actual:   ${actual}" >&2
    exit 1
  fi
}

echo "Downloading: ${url}" >&2
download "${url}" "${tmp_path}"

if [[ -n "${sha256}" ]]; then
  echo "Verifying SHA-256..." >&2
  verify_sha256 "${sha256}" "${tmp_path}"
fi

mv -f "${tmp_path}" "${final_path}"
trap - EXIT

if command -v realpath >/dev/null 2>&1; then
  realpath -- "${final_path}"
else
  echo "${final_path}"
fi

