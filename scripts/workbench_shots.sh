#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="${CRTK_WORKBENCH_SHOTS_DIR:-artifacts/workbench-shots}"
WIDTH="${CRTK_WORKBENCH_SHOTS_WIDTH:-1600}"
KNOWN_PANELS="${CRTK_WORKBENCH_KNOWN_PANELS:-dashboard,datasets,datasets-loaded,play,draw,analyze,puzzle,commands,console,network,mcts,tree,logs,publish}"
PANELS="${CRTK_WORKBENCH_SHOTS_PANELS:-$KNOWN_PANELS}"
THEMES="${CRTK_WORKBENCH_SHOTS_THEMES:-LIGHT,DARK}"
TIMEOUT_SECONDS="${CRTK_WORKBENCH_SHOTS_TIMEOUT:-45}"
BUILD=1
ALLOW_SKIP=0
DUMP_COMPONENTS=0
DUMP_DIR="${CRTK_WORKBENCH_COMPONENTS_DIR:-}"

usage() {
  cat <<'USAGE'
Usage: scripts/workbench_shots.sh [options]

Render deterministic offscreen Workbench panel screenshots via testing.WorkbenchShots.

Options:
  --out DIR        Output directory (default: artifacts/workbench-shots)
  --width PX       Render width for wide panels (default: 1600)
  --panel NAME     Render one panel target
  --panels CSV     Panel list or all (default: all known panels)
  --themes CSV     LIGHT,DARK, or one theme (default: LIGHT,DARK)
  --dump-components [DIR]
                  Also write detailed component dumps
                  (default DIR: OUT/components)
  --timeout SEC    Per-panel timeout when coreutils timeout exists (default: 45)
  --no-build       Reuse existing out/ classes
  --allow-skip     Do not fail if WorkbenchShots skips a panel
  --list-panels    Print known offscreen panel targets
  -h, --help       Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)
      OUT_DIR="${2:?missing value for --out}"
      shift 2
      ;;
    --width)
      WIDTH="${2:?missing value for --width}"
      shift 2
      ;;
    --panel)
      PANELS="${2:?missing value for --panel}"
      shift 2
      ;;
    --panels)
      PANELS="${2:?missing value for --panels}"
      shift 2
      ;;
    --themes)
      THEMES="${2:?missing value for --themes}"
      shift 2
      ;;
    --dump-components)
      DUMP_COMPONENTS=1
      if [[ $# -gt 1 && "${2:0:1}" != "-" ]]; then
        DUMP_DIR="$2"
        shift 2
      else
        shift
      fi
      ;;
    --timeout)
      TIMEOUT_SECONDS="${2:?missing value for --timeout}"
      shift 2
      ;;
    --no-build)
      BUILD=0
      shift
      ;;
    --allow-skip)
      ALLOW_SKIP=1
      shift
      ;;
    --list-panels)
      tr ',' '\n' <<< "$KNOWN_PANELS"
      exit 0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -n "$DUMP_DIR" ]]; then
  DUMP_COMPONENTS=1
fi

if [[ "$BUILD" == "1" ]]; then
  ./scripts/run_regression_suite.sh build
fi

mkdir -p "$OUT_DIR"
if [[ "$DUMP_COMPONENTS" == "1" && -z "$DUMP_DIR" ]]; then
  DUMP_DIR="$OUT_DIR/components"
fi
if [[ "$DUMP_COMPONENTS" == "1" ]]; then
  mkdir -p "$DUMP_DIR"
fi
LOG="$OUT_DIR/workbench-shots.log"
: > "$LOG"
FAILED=0
if [[ "${PANELS,,}" == "all" ]]; then
  PANELS="$KNOWN_PANELS"
fi
IFS=',' read -r -a PANEL_LIST <<< "$PANELS"
for panel in "${PANEL_LIST[@]}"; do
  panel="${panel//[[:space:]]/}"
  if [[ -z "$panel" ]]; then
    continue
  fi
  echo "==> WorkbenchShots panel=$panel themes=$THEMES" | tee -a "$LOG"
  JAVA_ARGS=(testing.WorkbenchShots "$OUT_DIR" "$WIDTH" "$panel" "$THEMES")
  if [[ "$DUMP_COMPONENTS" == "1" ]]; then
    JAVA_ARGS+=("$DUMP_DIR")
  fi
  if command -v timeout >/dev/null 2>&1; then
    if ! timeout "${TIMEOUT_SECONDS}s" java -Djava.awt.headless=true -cp out \
        "${JAVA_ARGS[@]}" | tee -a "$LOG"; then
      FAILED=1
    fi
  elif ! java -Djava.awt.headless=true -cp out \
      "${JAVA_ARGS[@]}" | tee -a "$LOG"; then
    FAILED=1
  fi
done

if [[ "$FAILED" == "1" ]]; then
  echo "One or more WorkbenchShots panel runs failed or timed out; see $LOG" >&2
  exit 1
fi

if [[ "$ALLOW_SKIP" != "1" ]] && grep -q '^SKIP ' "$LOG"; then
  echo "WorkbenchShots skipped at least one panel; see $LOG" >&2
  exit 1
fi

COUNT="$(awk '/^OK / { print substr($0, 4) }' "$LOG" | while IFS= read -r file; do
  if [[ -f "$file" && "$(wc -c < "$file")" -gt 1024 ]]; then
    echo "$file"
  fi
done | wc -l | tr -d ' ')"
if [[ "$COUNT" -eq 0 ]]; then
  echo "No non-empty PNG screenshots were produced by this run in $OUT_DIR" >&2
  exit 1
fi

echo "Workbench screenshots: $COUNT PNG files in $OUT_DIR"
if [[ "$DUMP_COMPONENTS" == "1" ]]; then
  DUMP_COUNT="$(awk '/^DUMP / { print substr($0, 6) }' "$LOG" | while IFS= read -r file; do
    if [[ -f "$file" && "$(wc -c < "$file")" -gt 128 ]]; then
      echo "$file"
    fi
  done | wc -l | tr -d ' ')"
  echo "Workbench component dumps: $DUMP_COUNT files in $DUMP_DIR"
fi
