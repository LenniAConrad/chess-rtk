#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

WIDTH="${CRTK_WORKBENCH_PREVIEW_WIDTH:-1500}"
HEIGHT="${CRTK_WORKBENCH_PREVIEW_HEIGHT:-950}"
FEN="${CRTK_WORKBENCH_PREVIEW_FEN:-}"
PANEL="${CRTK_WORKBENCH_PREVIEW_PANEL:-}"
BLACK_DOWN=0
BUILD=1
TIMEOUT_SECONDS="${CRTK_WORKBENCH_PREVIEW_TIMEOUT:-10}"
STATE_DIR="${CRTK_WORKBENCH_PREVIEW_STATE_DIR:-artifacts/workbench-live}"
LOG_FILE=""
DUMP_COMPONENTS=0
COMPONENTS_FILE="${CRTK_WORKBENCH_COMPONENTS_FILE:-}"
KNOWN_LIVE_PANELS="dashboard,board,analyze,play,solve,puzzle,relations,draw,run,commands,datasets,publish,engine,evaluator,network,search,mcts,tree,gauntlet,console,logs,board:draw,engine:tree"

usage() {
  cat <<'USAGE'
Usage: scripts/launch_workbench_preview.sh [options]

Build and launch the real Swing Workbench with isolated preview preferences.
The script leaves the app running and prints PID/window information for wmctrl
or xdotool automation.

Options:
  --width PX       Requested window width (default: 1500)
  --height PX      Requested window height (default: 950)
  --fen FEN        Initial FEN
  --panel TARGET   Open a specific live Workbench target after launch
  --black-down     Start with Black at the bottom
  --state-dir DIR  Preview state/log directory (default: artifacts/workbench-live)
  --log FILE       Launcher stdout/stderr log path
  --dump-components [FILE]
                  Also write a detailed component dump
                  (default FILE: STATE_DIR/workbench.components.txt)
  --timeout SEC    Seconds to wait for a wmctrl window id (default: 10)
  --no-build       Reuse existing out/ classes
  --list-panels    Print known live panel targets
  -h, --help       Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
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
    --black-down)
      BLACK_DOWN=1
      shift
      ;;
    --state-dir)
      STATE_DIR="${2:?missing value for --state-dir}"
      shift 2
      ;;
    --log)
      LOG_FILE="${2:?missing value for --log}"
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
    --timeout)
      TIMEOUT_SECONDS="${2:?missing value for --timeout}"
      shift 2
      ;;
    --no-build)
      BUILD=0
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

if [[ -z "${DISPLAY:-}" ]]; then
  echo "DISPLAY is not set; use scripts/capture_workbench_preview.sh or run under xvfb-run/Xephyr." >&2
  exit 1
fi

if [[ "$BUILD" == "1" ]]; then
  ./scripts/run_regression_suite.sh build
fi

mkdir -p "$STATE_DIR/home" "$STATE_DIR/java-prefs"
LOG_FILE="${LOG_FILE:-$STATE_DIR/workbench.log}"
PID_FILE="$STATE_DIR/workbench.pid"
if [[ "$DUMP_COMPONENTS" == "1" && -z "$COMPONENTS_FILE" ]]; then
  COMPONENTS_FILE="$STATE_DIR/workbench.components.txt"
fi
if [[ "$DUMP_COMPONENTS" != "1" ]]; then
  COMPONENTS_FILE=""
fi
mkdir -p "$(dirname "$LOG_FILE")"
if [[ -n "$COMPONENTS_FILE" ]]; then
  mkdir -p "$(dirname "$COMPONENTS_FILE")"
fi

JAVA_PROPS=(
  "-Duser.home=$STATE_DIR/home"
  "-Djava.util.prefs.userRoot=$STATE_DIR/java-prefs"
)
WHITE_DOWN=true
if [[ "$BLACK_DOWN" == "1" ]]; then
  WHITE_DOWN=false
fi

java "${JAVA_PROPS[@]}" -cp out testing.WorkbenchPreviewLauncher \
  "$FEN" "$WHITE_DOWN" "$PANEL" "$COMPONENTS_FILE" "$WIDTH" "$HEIGHT" >"$LOG_FILE" 2>&1 &
PID="$!"
echo "$PID" >"$PID_FILE"

WINDOW_ID=""
if command -v wmctrl >/dev/null 2>&1; then
  deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [[ "$SECONDS" -le "$deadline" ]]; do
    WINDOW_ID="$(wmctrl -lp | awk -v pid="$PID" '$3 == pid {print $1; exit}')"
    if [[ -n "$WINDOW_ID" ]]; then
      wmctrl -ir "$WINDOW_ID" -b remove,maximized_horz,maximized_vert || true
      wmctrl -ir "$WINDOW_ID" -e "0,80,80,$WIDTH,$HEIGHT" || true
      wmctrl -ia "$WINDOW_ID" || true
      break
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
      echo "Workbench exited early; see $LOG_FILE" >&2
      exit 1
    fi
    sleep 0.2
  done
fi

echo "Workbench PID: $PID"
echo "PID file: $PID_FILE"
echo "Log: $LOG_FILE"
if [[ "$DUMP_COMPONENTS" == "1" ]]; then
  echo "Component dump: $COMPONENTS_FILE"
fi
if [[ -n "$WINDOW_ID" ]]; then
  echo "Window id: $WINDOW_ID"
  echo "Stop: kill $PID"
  if command -v xdotool >/dev/null 2>&1; then
    echo "Activate: xdotool windowactivate $WINDOW_ID"
  else
    echo "Activate: wmctrl -ia $WINDOW_ID"
  fi
else
  echo "Window id: unavailable (install wmctrl for automatic discovery/resize)"
fi
