#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SUITE="${1:-recommended}"
PERFT_THREADS="${CRTK_PERFT_THREADS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)}"
PERFT_DEPTH="${CRTK_PERFT_DEPTH:-4}"
PERFT_SUITE_DEPTH="${CRTK_PERFT_SUITE_DEPTH:-4}"
REQUIRE_STOCKFISH="${CRTK_REQUIRE_STOCKFISH:-0}"

compile_sources() {
  rm -rf out
  mkdir -p out
  mapfile -t sources < <(find src -name '*.java' | sort)
  if [[ "${#sources[@]}" -eq 0 ]]; then
    echo "No Java sources found under src/" >&2
    exit 1
  fi
  javac "$@" --release 17 -d out "${sources[@]}"
}

ensure_compiled() {
  if ! find out -name '*.class' -print -quit >/dev/null 2>&1; then
    run_build
    return
  fi
  if [[ -z "$(find out -name '*.class' -print -quit)" ]]; then
    run_build
  fi
}

run_java() {
  echo "==> java $*"
  java "$@"
}

run_test() {
  local main_class="$1"
  shift || true
  run_java -cp out "$main_class" "$@"
}

run_headless_test() {
  local main_class="$1"
  shift || true
  run_java -Djava.awt.headless=true -cp out "$main_class" "$@"
}

run_build() {
  echo "==> build"
  compile_sources
}

run_lint() {
  echo "==> lint"
  compile_sources -Xlint:all
  git diff --check
}

run_core() {
  ensure_compiled
  run_test testing.PositionRegressionTest
  run_test testing.CoreMoveGenerationRegressionTest
  run_test testing.SANRegressionTest
  run_test testing.JsonRegressionTest
  run_test testing.Chess960SetupRegressionTest
}

run_cli() {
  ensure_compiled
  run_test testing.CLICommandRegressionTest
  run_test testing.PGNRegressionTest
  run_test testing.ChessBookCommandRegressionTest
  run_test testing.ChessBookCoverCommandRegressionTest
  run_test testing.ChessPDFCommandRegressionTest
}

run_engine() {
  ensure_compiled
  run_test testing.BuiltInEngineRegressionTest
}

run_uci() {
  ensure_compiled
  run_test testing.UCIRegressionTest
  if command -v stockfish >/dev/null 2>&1; then
    run_java -cp out application.Main engine uci-smoke --nodes 1 --max-duration 5s
  elif [[ "$REQUIRE_STOCKFISH" == "1" ]]; then
    echo "stockfish is required for the uci suite but was not found on PATH" >&2
    exit 1
  else
    echo "==> skipping application.Main engine uci-smoke (stockfish not on PATH)"
  fi
}

run_book() {
  ensure_compiled
  run_headless_test testing.BookRegressionTest
  run_headless_test testing.ChessPDFRegressionTest
  run_test testing.PDFDocumentRegressionTest
}

run_perft_smoke() {
  ensure_compiled
  run_java -cp out application.Main engine perft --depth "$PERFT_DEPTH" --threads "$PERFT_THREADS"
  run_java -cp out application.Main engine perft-suite --depth "$PERFT_SUITE_DEPTH" --threads "$PERFT_THREADS"
}

run_jar() {
  ensure_compiled
  echo "==> jar"
  rm -f crtk.jar
  jar --create --file crtk.jar --main-class application.Main -C out .
}

run_recommended() {
  run_build
  run_lint
  run_core
  run_cli
  run_engine
  run_uci
  run_book
  run_perft_smoke
}

run_ci() {
  run_build
  run_lint
  run_core
  run_cli
  run_uci
  run_perft_smoke
}

run_release() {
  run_recommended
  run_jar
}

case "$SUITE" in
  build)
    run_build
    ;;
  lint)
    run_lint
    ;;
  core)
    run_core
    ;;
  cli)
    run_cli
    ;;
  engine)
    run_engine
    ;;
  uci)
    run_uci
    ;;
  book)
    run_book
    ;;
  perft-smoke)
    run_perft_smoke
    ;;
  jar)
    run_jar
    ;;
  recommended)
    run_recommended
    ;;
  ci)
    run_ci
    ;;
  release)
    run_release
    ;;
  *)
    echo "Unknown suite: $SUITE" >&2
    echo "Usage: $0 [build|lint|core|cli|engine|uci|book|perft-smoke|jar|recommended|ci|release]" >&2
    exit 2
    ;;
esac
