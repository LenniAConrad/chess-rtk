#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/build_lock.sh
source "$ROOT_DIR/scripts/build_lock.sh"
crtk_acquire_build_lock "$ROOT_DIR"

SUITE="${1:-recommended}"
BUILD_STAMP="out/.crtk-build-complete"
ONLINE_CPUS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)"
if ! [[ "$ONLINE_CPUS" =~ ^[0-9]+$ ]] || [[ "$ONLINE_CPUS" -lt 1 ]]; then
  ONLINE_CPUS=2
fi
# Java regression classes and high-level phases share the compiled tree and
# out/tmp. Keep both serial by default so the standard recommended gate is
# stable; CRTK_TEST_JOBS/CRTK_SUITE_JOBS remain explicit opt-ins.
DEFAULT_TEST_JOBS=1
DEFAULT_SUITE_JOBS=1
PERFT_THREADS="${CRTK_PERFT_THREADS:-$ONLINE_CPUS}"
PERFT_DEPTH="${CRTK_PERFT_DEPTH:-4}"
PERFT_SUITE_DEPTH="${CRTK_PERFT_SUITE_DEPTH:-4}"
REQUIRE_STOCKFISH="${CRTK_REQUIRE_STOCKFISH:-0}"
TEST_TIMEOUT="${CRTK_TEST_TIMEOUT:-300}"
TEST_JOBS="${CRTK_TEST_JOBS:-$DEFAULT_TEST_JOBS}"
SUITE_JOBS="${CRTK_SUITE_JOBS:-$DEFAULT_SUITE_JOBS}"

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 1 ]]; then
    echo "$name must be a positive integer (got '$value')" >&2
    exit 2
  fi
}

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "$value" =~ ^[0-9]+$ ]]; then
    echo "$name must be a non-negative integer (got '$value')" >&2
    exit 2
  fi
}

require_positive_integer CRTK_PERFT_THREADS "$PERFT_THREADS"
require_positive_integer CRTK_TEST_JOBS "$TEST_JOBS"
require_positive_integer CRTK_SUITE_JOBS "$SUITE_JOBS"
require_non_negative_integer CRTK_TEST_TIMEOUT "$TEST_TIMEOUT"

compile_sources() {
  clean_out
  mkdir -p out
  mapfile -t sources < <(find src -name '*.java' | sort)
  if [[ "${#sources[@]}" -eq 0 ]]; then
    echo "No Java sources found under src/" >&2
    exit 1
  fi
  javac "$@" --release 17 -d out "${sources[@]}"
  copy_runtime_resources
  touch "$BUILD_STAMP"
}

clean_out() {
  local attempt
  for attempt in 1 2 3; do
    if rm -rf out; then
      return
    fi
    sleep "0.$attempt"
  done
  rm -rf out
}

ensure_compiled() {
  if [[ ! -f "$BUILD_STAMP" ]]; then
    run_build
    return
  fi
  if [[ ! -f out/application/Main.class || ! -f out/testing/CLICommandRegressionTest.class ]]; then
    run_build
  fi
}

run_java() {
  echo "==> java $*"
  java "$@"
}

# Wraps a java invocation in a per-step timeout when GNU `timeout` (or macOS
# `gtimeout`) is available, so a hung UCI/Robot/engine path cannot block the
# whole CI gate. Override via CRTK_TEST_TIMEOUT=seconds; set to 0 to disable.
run_test_with_timeout() {
  local cmd="$1"
  shift || true
  if [[ "$TEST_TIMEOUT" == "0" || -z "$TEST_TIMEOUT" ]]; then
    "$cmd" "$@"
    return
  fi
  local timeout_bin=""
  if command -v timeout >/dev/null 2>&1; then
    timeout_bin="timeout"
  elif command -v gtimeout >/dev/null 2>&1; then
    timeout_bin="gtimeout"
  fi
  if [[ -z "$timeout_bin" ]]; then
    "$cmd" "$@"
    return
  fi
  echo "==> [timeout=${TEST_TIMEOUT}s] $cmd $*"
  # Capture the wrapped command's exit BEFORE entering an if-block. Inside
  # `if ! cmd; then ... fi` bash does not preserve the failing command's $?
  # for the body, so `local rc=$?` always saw 0 and `exit $rc` silently
  # turned every test failure into a clean exit.
  local rc=0
  "$timeout_bin" --kill-after=10 "$TEST_TIMEOUT" "$cmd" "$@" || rc=$?
  if [[ $rc -ne 0 ]]; then
    if [[ $rc -eq 124 || $rc -eq 137 ]]; then
      echo "==> TEST TIMED OUT after ${TEST_TIMEOUT}s: $cmd $*" >&2
    fi
    exit $rc
  fi
}

run_test() {
  local main_class="$1"
  shift || true
  run_test_with_timeout java -cp out "$main_class" "$@"
}

run_headless_test() {
  local main_class="$1"
  shift || true
  run_test_with_timeout java -Djava.awt.headless=true -cp out "$main_class" "$@"
}

run_test_spec() {
  local spec="$1"
  local mode="${spec%%:*}"
  local main_class="${spec#*:}"
  case "$mode" in
    java)
      run_test "$main_class"
      ;;
    headless)
      run_headless_test "$main_class"
      ;;
    *)
      echo "unknown test spec: $spec" >&2
      exit 2
      ;;
  esac
}

run_test_batch() {
  local label="$1"
  shift || true
  local -a specs=("$@")
  local count="${#specs[@]}"
  if [[ "$count" -eq 0 ]]; then
    return
  fi
  if [[ "$TEST_JOBS" -eq 1 || "$count" -eq 1 ]]; then
    local spec
    for spec in "${specs[@]}"; do
      run_test_spec "$spec"
    done
    return
  fi

  local jobs="$TEST_JOBS"
  if [[ "$jobs" -gt "$count" ]]; then
    jobs="$count"
  fi
  echo "==> $label tests (${count} classes, ${jobs} jobs)"

  local tmpdir
  tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/crtk-regression-${label}.XXXXXX")"
  local failed=0
  local start=0
  local index end pid log rc spec
  local -a pids=()
  local -a logs=()

  while [[ "$start" -lt "$count" ]]; do
    end=$((start + jobs))
    if [[ "$end" -gt "$count" ]]; then
      end="$count"
    fi
    pids=()
    logs=()

    for ((index = start; index < end; index++)); do
      spec="${specs[$index]}"
      log="$tmpdir/$(printf "%03d" "$index").log"
      logs[$index]="$log"
      ( run_test_spec "$spec" ) >"$log" 2>&1 &
      pids[$index]=$!
    done

    for ((index = start; index < end; index++)); do
      pid="${pids[$index]}"
      log="${logs[$index]}"
      rc=0
      wait "$pid" || rc=$?
      cat "$log"
      if [[ "$rc" -ne 0 ]]; then
        failed="$rc"
      fi
    done

    start="$end"
    if [[ "$failed" -ne 0 ]]; then
      rm -rf "$tmpdir"
      exit "$failed"
    fi
  done

  rm -rf "$tmpdir"
  if [[ "$failed" -ne 0 ]]; then
    exit "$failed"
  fi
}

run_test_batch_serial() {
  local saved_jobs="$TEST_JOBS"
  TEST_JOBS=1
  run_test_batch "$@"
  TEST_JOBS="$saved_jobs"
}

run_suite_phase() {
  local phase="$1"
  case "$phase" in
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
    docs)
      run_docs
      ;;
    perft-smoke)
      run_perft_smoke
      ;;
    *)
      echo "unknown suite phase: $phase" >&2
      exit 2
      ;;
  esac
}

run_suite_phase_batch() {
  local label="$1"
  shift || true
  local -a phases=("$@")
  local count="${#phases[@]}"
  if [[ "$count" -eq 0 ]]; then
    return
  fi
  if [[ "$SUITE_JOBS" -eq 1 || "$count" -eq 1 ]]; then
    local phase
    for phase in "${phases[@]}"; do
      run_suite_phase "$phase"
    done
    return
  fi

  local jobs="$SUITE_JOBS"
  if [[ "$jobs" -gt "$count" ]]; then
    jobs="$count"
  fi
  echo "==> $label phases (${count} targets, ${jobs} jobs)"

  local tmpdir
  tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/crtk-regression-${label}-phases.XXXXXX")"
  local failed=0
  local start=0
  local index end pid log rc phase
  local -a pids=()
  local -a logs=()

  while [[ "$start" -lt "$count" ]]; do
    end=$((start + jobs))
    if [[ "$end" -gt "$count" ]]; then
      end="$count"
    fi
    pids=()
    logs=()

    for ((index = start; index < end; index++)); do
      phase="${phases[$index]}"
      log="$tmpdir/$(printf "%03d" "$index")-$phase.log"
      logs[$index]="$log"
      ( run_suite_phase "$phase" ) >"$log" 2>&1 &
      pids[$index]=$!
    done

    for ((index = start; index < end; index++)); do
      pid="${pids[$index]}"
      log="${logs[$index]}"
      rc=0
      wait "$pid" || rc=$?
      cat "$log"
      if [[ "$rc" -ne 0 ]]; then
        failed="$rc"
      fi
    done

    start="$end"
    if [[ "$failed" -ne 0 ]]; then
      rm -rf "$tmpdir"
      exit "$failed"
    fi
  done

  rm -rf "$tmpdir"
  if [[ "$failed" -ne 0 ]]; then
    exit "$failed"
  fi
}

run_build() {
  echo "==> build"
  compile_sources
}

run_lint() {
  echo "==> lint"
  compile_sources -Xlint:all
  run_scripts_lint
  git diff --check
}

run_build_and_lint() {
  echo "==> build + lint"
  compile_sources -Xlint:all
  run_scripts_lint
  git diff --check
}

run_scripts_lint() {
  echo "==> scripts"
  local -a shell_scripts=()
  if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    mapfile -t shell_scripts < <(git ls-files '*.sh' | sort)
  else
    mapfile -t shell_scripts < <(find . -name '*.sh' -print | sort)
  fi
  if [[ "${#shell_scripts[@]}" -eq 0 ]]; then
    echo "No shell scripts found"
    return
  fi
  local script
  for script in "${shell_scripts[@]}"; do
    bash -n "$script"
  done
  if command -v shellcheck >/dev/null 2>&1; then
    shellcheck -S error "${shell_scripts[@]}"
  else
    echo "==> skipping shellcheck (not on PATH)"
  fi
}

copy_runtime_resources() {
  if [[ -d schemas ]]; then
    mkdir -p out/schemas
    find schemas -type f -name '*.schema.json' -print0 \
      | while IFS= read -r -d '' schema_file; do
          mkdir -p "out/$(dirname "$schema_file")"
          cp "$schema_file" "out/$schema_file"
        done
  fi
}

run_core() {
  ensure_compiled
  run_test_batch core \
    java:testing.PositionRegressionTest \
    java:testing.CoreMoveGenerationRegressionTest \
    java:testing.SplitPerftRegressionTest \
    java:testing.SANRegressionTest \
    java:testing.ClassicalEvaluationBreakdownTest \
    java:testing.JsonRegressionTest \
    java:testing.RecordSchemaVersionRegressionTest \
    java:testing.XmlSecurityRegressionTest \
    java:testing.InstallScriptRegressionTest \
    java:testing.SourceHeaderRegressionTest \
    java:testing.DescribeVerifierRegressionTest \
    java:testing.Chess960SetupRegressionTest \
    java:testing.ParserRegressionTest \
    java:testing.TaggingRegressionTest \
    java:testing.TagFixtureRegressionTest \
    java:testing.SortFamilyOrderRegressionTest \
    java:testing.WorkbenchStructureRegressionTest \
    java:testing.BooleansRegressionTest \
    headless:testing.WorkbenchRegressionTest
}

run_cli() {
  ensure_compiled
  run_test_batch cli \
    java:testing.CLICommandRegressionTest \
    java:testing.CommandCatalogRegressionTest \
    java:testing.SchemaAgreementRegressionTest \
    java:testing.RecordValidateRegressionTest \
    java:testing.RecordDedupeRegressionTest \
    java:testing.RecordSplitRegressionTest \
    java:testing.RecordAuditSplitRegressionTest \
    java:testing.RecordTrainingJSONLRegressionTest \
    java:testing.RecordLC0ExporterRegressionTest \
    java:testing.DatasetDeterminismRegressionTest \
    java:testing.DatasetManifestRegressionTest \
    java:testing.DatasetVerifyRegressionTest \
    java:testing.DatasetAuditRegressionTest \
    java:testing.DatasetDiffRegressionTest \
    java:testing.PgnStoreRegressionTest \
    java:testing.PgnStoreCompactRegressionTest \
    java:testing.PgnStoreScaleRegressionTest \
    java:testing.PGNRegressionTest \
    java:testing.ReviewClassifierRegressionTest \
    java:testing.ReviewRowSchemaRegressionTest \
    java:testing.ReviewCommandRegressionTest \
    java:testing.ChessBookCommandRegressionTest \
    java:testing.ChessBookCoverCommandRegressionTest \
    java:testing.ChessPDFCommandRegressionTest \
    java:testing.PuzzleCollectionCommandRegressionTest \
    java:testing.PuzzleStudyCommandRegressionTest
  # These tests both spin up the localhost daemon and exercise process-wide
  # command capture. Keep them serial so the CLI phase remains deterministic
  # while the broader command suite still runs in parallel.
  run_test_batch_serial cli-daemon \
    java:testing.ServeRegressionTest \
    java:testing.PythonSdkRegressionTest
}

run_engine() {
  ensure_compiled
  # Engine tests touch shared optional backend/model state. Keep this phase
  # serial so the recommended suite remains a reliable gate.
  run_test_batch_serial engine \
    java:testing.BuiltInEngineRegressionTest \
    java:testing.SprtRegressionTest \
    java:testing.GameClockRegressionTest \
    java:testing.StagedPickerRegressionTest \
    java:testing.PuzzleDifficultyRegressionTest \
    java:testing.BT4RegressionTest \
    java:testing.ClassifierModelRegressionTest \
    java:testing.T5RegressionTest \
    java:testing.OtisBackendRegressionTest \
    java:testing.GpuPerftRegressionTest \
    java:testing.NNUERegressionTest \
    java:testing.UpstreamRegressionTest \
    headless:testing.PlayModeRegressionTest
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
  run_test_batch book \
    headless:testing.BookRegressionTest \
    headless:testing.ChessPDFRegressionTest \
    java:testing.PDFDocumentRegressionTest
}

run_docs() {
  echo "==> docs"
  python3 scripts/build_docs_site.py
  python3 scripts/build_manual_pdf.py --html-only
}

run_perft_smoke() {
  ensure_compiled
  run_java -cp out application.Main engine perft --depth "$PERFT_DEPTH" --threads "$PERFT_THREADS"
  run_java -cp out application.Main engine perft-suite --depth "$PERFT_SUITE_DEPTH" --threads "$PERFT_THREADS"
}

run_jar() {
  ensure_compiled
  echo "==> jar"
  copy_runtime_resources
  rm -rf out/tmp
  rm -f crtk.jar
  jar --create --file crtk.jar --main-class application.Main -C out .
  echo "==> jar smoke"
  local jar_path="$ROOT_DIR/crtk.jar"
  ( cd "${TMPDIR:-/tmp}" \
    && java -jar "$jar_path" --help >/dev/null \
    && java -jar "$jar_path" workbench --help | grep -q "workbench options:" \
    && java -jar "$jar_path" gui --help | grep -q "crtk workbench" \
    && java -jar "$jar_path" schema show crtk.record.v2 | grep -q '"$id": "crtk.record.v2"' )
}

run_recommended() {
  run_build_and_lint
  run_suite_phase_batch recommended core cli engine uci book perft-smoke
  run_jar
}

run_ci() {
  run_build_and_lint
  run_suite_phase_batch ci core cli engine book docs uci perft-smoke
  run_jar
}

run_release() {
  run_recommended
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
  scripts)
    run_scripts_lint
    ;;
  uci)
    run_uci
    ;;
  book)
    run_book
    ;;
  docs)
    run_docs
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
    echo "Usage: $0 [build|lint|core|cli|engine|scripts|uci|book|docs|perft-smoke|jar|recommended|ci|release]" >&2
    exit 2
    ;;
esac
