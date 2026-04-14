#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/out_svg_test"

mkdir -p "$OUT_DIR"

JAVA_SOURCES=()
JAVA_SOURCES+=("${ROOT}/src/utility/Svg.java")

javac -d "$OUT_DIR" "${JAVA_SOURCES[@]}"
java -cp "$OUT_DIR" utility.Svg\$SelfTest "$@"
