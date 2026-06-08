#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_FILE="${CRTK_WORKBENCH_CAPTURE_OUT:-artifacts/workbench-live/workbench-preview.png}"
WIDTH="${CRTK_WORKBENCH_PREVIEW_WIDTH:-1500}"
HEIGHT="${CRTK_WORKBENCH_PREVIEW_HEIGHT:-950}"
FEN="${CRTK_WORKBENCH_PREVIEW_FEN:-}"
PANEL="${CRTK_WORKBENCH_PREVIEW_PANEL:-}"
BUILD=1
USE_XVFB=1
STATE_DIR="${CRTK_WORKBENCH_PREVIEW_STATE_DIR:-artifacts/workbench-live}"
DUMP_COMPONENTS=0
COMPONENTS_FILE="${CRTK_WORKBENCH_COMPONENTS_FILE:-}"
KNOWN_LIVE_PANELS="dashboard,board,analyze,play,solve,puzzle,relations,draw,run,commands,datasets,publish,engine,evaluator,network,search,mcts,tree,gauntlet,console,logs,board:draw,engine:tree"

usage() {
  cat <<'USAGE'
Usage: scripts/capture_workbench_preview.sh [options]

Launch the real Swing Workbench, capture its window with java.awt.Robot, then
dispose it. If DISPLAY is missing and xvfb-run is available, the script runs
itself inside an isolated Xvfb display.

Options:
  --out FILE       Output PNG (default: artifacts/workbench-live/workbench-preview.png)
  --width PX       Window width (default: 1500)
  --height PX      Window height (default: 950)
  --fen FEN        Initial FEN
  --panel TARGET   Open a specific live Workbench target before capture
  --state-dir DIR  Isolated preview state directory (default: artifacts/workbench-live)
  --dump-components [FILE]
                  Also write a detailed component dump
                  (default FILE: OUT.components.txt)
  --no-build       Reuse existing out/ classes
  --no-xvfb        Do not auto-reexec under xvfb-run when DISPLAY is missing
  --list-panels    Print known live panel targets
  -h, --help       Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)
      OUT_FILE="${2:?missing value for --out}"
      shift 2
      ;;
    --width)
      WIDTH="${2:?missing value for --width}"
      shift 2
      ;;
    --height)
      HEIGHT="${2:?missing value for --height}"
      shift 2
      ;;
    --fen)
      FEN="${2:?missing value for --fen}"
      shift 2
      ;;
    --panel)
      PANEL="${2:?missing value for --panel}"
      shift 2
      ;;
    --state-dir)
      STATE_DIR="${2:?missing value for --state-dir}"
      shift 2
      ;;
    --dump-components)
      DUMP_COMPONENTS=1
      if [[ $# -gt 1 && "${2:0:1}" != "-" ]]; then
        COMPONENTS_FILE="$2"
        shift 2
      else
        shift
      fi
      ;;
    --no-build)
      BUILD=0
      shift
      ;;
    --no-xvfb)
      USE_XVFB=0
      shift
      ;;
    --list-panels)
      tr ',' '\n' <<< "$KNOWN_LIVE_PANELS"
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

if [[ -n "$COMPONENTS_FILE" ]]; then
  DUMP_COMPONENTS=1
fi
if [[ "$DUMP_COMPONENTS" == "1" && -z "$COMPONENTS_FILE" ]]; then
  COMPONENTS_FILE="${OUT_FILE%.*}.components.txt"
fi
if [[ "$DUMP_COMPONENTS" != "1" ]]; then
  COMPONENTS_FILE=""
fi

if [[ -z "${DISPLAY:-}" && "$USE_XVFB" == "1" ]] && command -v xvfb-run >/dev/null 2>&1; then
  REEXEC_ARGS=(--no-xvfb --out "$OUT_FILE" --width "$WIDTH" --height "$HEIGHT" --state-dir "$STATE_DIR")
  if [[ -n "$FEN" ]]; then
    REEXEC_ARGS+=(--fen "$FEN")
  fi
  if [[ -n "$PANEL" ]]; then
    REEXEC_ARGS+=(--panel "$PANEL")
  fi
  if [[ "$DUMP_COMPONENTS" == "1" ]]; then
    REEXEC_ARGS+=(--dump-components "$COMPONENTS_FILE")
  fi
  if [[ "$BUILD" != "1" ]]; then
    REEXEC_ARGS+=(--no-build)
  fi
  exec xvfb-run -a "$0" "${REEXEC_ARGS[@]}"
fi

if [[ -z "${DISPLAY:-}" ]]; then
  echo "DISPLAY is not set and xvfb-run is unavailable or disabled." >&2
  exit 1
fi

if [[ "$BUILD" == "1" ]]; then
  ./scripts/run_regression_suite.sh build
fi

mkdir -p "$(dirname "$OUT_FILE")" "$STATE_DIR/home" "$STATE_DIR/java-prefs"
JAVA_PROPS=(
  "-Duser.home=$STATE_DIR/home"
  "-Djava.util.prefs.userRoot=$STATE_DIR/java-prefs"
)

java "${JAVA_PROPS[@]}" -cp out testing.WorkbenchRobotCapture \
  "$OUT_FILE" "$WIDTH" "$HEIGHT" "$FEN" "$PANEL" "$COMPONENTS_FILE"
