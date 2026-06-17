#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/make_runtime_linux.sh [--version <vX.Y.Z>] [--dest <dir>]

Builds a Linux x86_64 self-contained runtime bundle:
  - compiles crtk with javac --release 17
  - packages crtk.jar
  - derives the required Java modules with jdeps
  - creates a bundled runtime with jlink
  - creates an app image with jpackage
  - adds a root ./crtk launcher that runs from the bundle directory

Outputs:
  dist/crtk-<version>-linux-x86_64-runtime/
  dist/crtk-<version>-linux-x86_64-runtime.tar.gz
  dist/crtk-<version>-linux-x86_64-runtime.sha256

Examples:
  scripts/make_runtime_linux.sh --version v1.0.0
  scripts/make_runtime_linux.sh --dest dist/runtime-smoke
EOF
}

VERSION=""
DEST_DIR="dist"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="${2:-}"
      shift 2
      ;;
    --dest)
      DEST_DIR="${2:-}"
      shift 2
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

# shellcheck source=scripts/build_lock.sh
source "$repo_root/scripts/build_lock.sh"
crtk_acquire_build_lock "$repo_root"

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool not found; install a Java 17+ JDK that includes $tool." >&2
    exit 1
  fi
}

derive_version() {
  if [[ -n "$VERSION" ]]; then
    printf '%s\n' "$VERSION"
    return
  fi
  if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git describe --tags --always
    return
  fi
  date -u +%Y%m%d
}

artifact_safe() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9._-' '-'
}

jpackage_version() {
  local raw="${1#v}"
  if [[ "$raw" =~ ^[0-9]+([.][0-9]+){0,2}$ ]]; then
    printf '%s\n' "$raw"
  else
    printf '1.0.0\n'
  fi
}

copy_runtime_resources() {
  if [[ -d schemas ]]; then
    rm -rf out/schemas
    cp -R schemas out/schemas
  fi
}

build_jar() {
  echo "== Building Java (crtk.jar) =="
  rm -rf out
  mkdir -p out
  mapfile -t sources < <(find src -name '*.java' | sort)
  if [[ "${#sources[@]}" -eq 0 ]]; then
    echo "No Java sources found under src/" >&2
    exit 1
  fi
  javac --release 17 -d out "${sources[@]}"
  copy_runtime_resources
  jar --create --file crtk.jar --main-class application.Main -C out .
}

module_deps() {
  local deps
  if deps="$(jdeps --ignore-missing-deps --multi-release 17 --print-module-deps crtk.jar)"; then
    deps="$(printf '%s' "$deps" | tr -d '[:space:]')"
  fi
  if [[ -z "${deps:-}" ]]; then
    deps="java.base,java.desktop,java.logging,java.prefs,jdk.httpserver"
  fi
  for extra in jdk.crypto.ec jdk.localedata; do
    if [[ ",$deps," != *",$extra,"* ]]; then
      deps="$deps,$extra"
    fi
  done
  printf '%s\n' "$deps"
}

write_root_launcher() {
  local stage_dir="$1"
  cat > "$stage_dir/crtk" <<'EOF'
#!/bin/sh
set -eu

case "$0" in
  */*) app_dir=${0%/*} ;;
  *) app_dir=. ;;
esac

APP_HOME="$(CDPATH= cd "$app_dir" && pwd)"
cd "$APP_HOME"
exec "$APP_HOME/bin/crtk" "$@"
EOF
  chmod +x "$stage_dir/crtk"
}

stage_docs_and_config() {
  local stage_dir="$1"
  cp -f LICENSE.txt "$stage_dir/"
  cp -f README.md "$stage_dir/"
  cp -R config "$stage_dir/"
  cp -R wiki "$stage_dir/"
  if [[ -f models/README.md ]]; then
    mkdir -p "$stage_dir/models"
    cp -f models/README.md "$stage_dir/models/"
  fi
}

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "scripts/make_runtime_linux.sh only builds Linux runtime images." >&2
  exit 2
fi
for tool in javac jar jdeps jlink jpackage sha256sum tar; do
  require_tool "$tool"
done

version="$(derive_version)"
safe_version="$(artifact_safe "$version")"
app_version="$(jpackage_version "$version")"
artifact="crtk-${safe_version}-linux-x86_64-runtime"
dest_dir="$DEST_DIR"
stage_dir="$dest_dir/$artifact"
tmp_dir="$dest_dir/.${artifact}.tmp"
runtime_dir="$tmp_dir/runtime"
input_dir="$tmp_dir/input"

mkdir -p "$dest_dir"
rm -rf "$stage_dir" "$tmp_dir"
mkdir -p "$input_dir"
trap 'rm -rf "$tmp_dir"' EXIT

build_jar
cp -f crtk.jar "$input_dir/"

modules="$(module_deps)"
echo "== Building Java runtime image =="
echo "modules: $modules"
jlink \
  --add-modules "$modules" \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=2 \
  --output "$runtime_dir"

echo "== Creating jpackage app image =="
jpackage \
  --type app-image \
  --name crtk \
  --dest "$tmp_dir" \
  --input "$input_dir" \
  --main-jar crtk.jar \
  --main-class application.Main \
  --runtime-image "$runtime_dir" \
  --app-version "$app_version" \
  --vendor "ChessRTK" \
  --description "Deterministic Java chess research toolkit"

mv "$tmp_dir/crtk" "$stage_dir"
write_root_launcher "$stage_dir"
stage_docs_and_config "$stage_dir"

echo "== Smoke test bundled launcher =="
env -i HOME="${HOME:-/tmp}" PATH="" "$stage_dir/crtk" version

echo "== Creating tarball =="
tarball="$dest_dir/${artifact}.tar.gz"
checksum="$dest_dir/${artifact}.sha256"
rm -f "$tarball" "$checksum"
tar -C "$dest_dir" -czf "$tarball" "$artifact"
(cd "$dest_dir" && sha256sum "${artifact}.tar.gz" > "${artifact}.sha256")

echo "Wrote: $stage_dir"
echo "Wrote: $tarball"
echo "Wrote: $checksum"
