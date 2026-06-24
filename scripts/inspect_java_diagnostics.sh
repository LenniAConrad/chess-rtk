#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_NAME="$(basename "$ROOT_DIR")"
CODE_HOME="${CODE_HOME:-$HOME/.config/Code}"
WORKSPACE_STORAGE="${WORKSPACE_STORAGE:-$CODE_HOME/User/workspaceStorage}"
LOGS_DIR="${LOGS_DIR:-$CODE_HOME/logs}"
KNOWN_STALE_MARKERS="$ROOT_DIR/reports/java-diagnostics-known-stale.txt"
MCP_CONFIG="${MCP_CONFIG:-$CODE_HOME/User/mcp.json}"
SONARLINT_DB="${SONARLINT_DB:-$HOME/.sonarlint/storage/h2/sq-ide}"

usage() {
  cat <<'EOF'
Usage: scripts/inspect_java_diagnostics.sh [options]

Run javac warnings plus local VS Code JDT and SonarQube for IDE diagnostic checks.

Options:
  -h, --help  Show this help.
EOF
}

case "${1:-}" in
  "")
    ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    echo "Unknown option: $1" >&2
    echo "Try --help." >&2
    exit 2
    ;;
esac

echo "==> javac -Xlint:all"
mapfile -t sources < <(find "$ROOT_DIR/src" -name '*.java' | sort)
if [[ ${#sources[@]} -eq 0 ]]; then
  echo "No Java sources found under $ROOT_DIR/src" >&2
  exit 1
fi

tmp_out="$(mktemp -d)"
tmp_markers="$(mktemp)"
tmp_active_markers="$(mktemp)"
tmp_stale_markers="$(mktemp)"
trap 'rm -rf "$tmp_out" "$tmp_markers" "$tmp_active_markers" "$tmp_stale_markers"' EXIT

javac -Xlint:all --release 17 -d "$tmp_out" "${sources[@]}"
echo "javac reported no warnings."

echo
echo "==> Java suppression markers"
if command -v rg >/dev/null 2>&1; then
  if ! (cd "$ROOT_DIR" && rg -n '@SuppressWarnings|NOSONAR' src); then
    echo "No Java suppression markers found."
  fi
else
  if ! (cd "$ROOT_DIR" && grep -RInE '@SuppressWarnings|NOSONAR' src); then
    echo "No Java suppression markers found."
  fi
fi

echo
echo "==> VS Code JDT marker messages"
if ! command -v strings >/dev/null 2>&1; then
  echo "Cannot inspect JDT marker binaries because 'strings' is unavailable."
elif [[ ! -d "$WORKSPACE_STORAGE" ]]; then
  echo "No VS Code workspace storage found at $WORKSPACE_STORAGE."
else
  while IFS= read -r -d '' marker_file; do
    strings -a "$marker_file" | awk '
      /\/src\/.*\.java/ {
        file = $0
        sub(/^.*_\/src\//, "src/", file)
        sub(/[[:space:]].*$/, "", file)
      }
      /^message$/ {
        if ((getline msg) > 0) {
          sub(/^[^A-Z]*/, "", msg)
          if (file != "" && msg != "") {
            print file ": " msg
          }
        }
      }
    ' >>"$tmp_markers"
  done < <(
    find "$WORKSPACE_STORAGE" \
      \( -path "*/redhat.java/*_ws/.metadata/.plugins/org.eclipse.core.resources/.projects/${REPO_NAME}/.markers" \
      -o -path "*/redhat.java/*_ws/.metadata/.plugins/org.eclipse.core.resources/.projects/${REPO_NAME}_*/.markers" \
      -o -path "*/redhat.java/*_ws/.metadata/.plugins/org.eclipse.core.resources/.projects/${REPO_NAME}/.markers.snap" \
      -o -path "*/redhat.java/*_ws/.metadata/.plugins/org.eclipse.core.resources/.projects/${REPO_NAME}_*/.markers.snap" \) \
      -type f -print0 2>/dev/null
  )

  if [[ -s "$tmp_markers" ]]; then
    while IFS= read -r marker; do
      if [[ -f "$KNOWN_STALE_MARKERS" ]] && grep -Fxq "$marker" "$KNOWN_STALE_MARKERS"; then
        echo "$marker" >>"$tmp_stale_markers"
      else
        echo "$marker" >>"$tmp_active_markers"
      fi
    done < <(sort -u "$tmp_markers")

    if [[ -s "$tmp_active_markers" ]]; then
      echo "Active or review-needed markers:"
      sort -u "$tmp_active_markers"
    else
      echo "No active JDT marker messages found."
    fi

    if [[ -s "$tmp_stale_markers" ]]; then
      echo
      echo "Documented stale marker candidates:"
      sort -u "$tmp_stale_markers"
    fi
  else
    echo "No JDT marker messages found."
  fi
fi

echo
echo "==> SonarLint local issue database"
mapfile -t sonarlint_jars < <(
  find "$HOME/.vscode/extensions" -path '*/server/sonarlint-ls.jar' -type f -printf '%T@ %p\n' 2>/dev/null \
    | sort -nr \
    | sed 's/^[^ ]* //'
)
if [[ ${#sonarlint_jars[@]} -eq 0 ]]; then
  echo "No SonarQube for IDE language-server jar found."
elif [[ ! -f "${SONARLINT_DB}.mv.db" ]]; then
  echo "No SonarLint H2 database found at ${SONARLINT_DB}.mv.db."
else
  scope_uri="file://${ROOT_DIR}"
  java -cp "${sonarlint_jars[0]}" org.h2.tools.Shell \
    -url "jdbc:h2:file:${SONARLINT_DB};AUTO_SERVER=TRUE" \
    -user sa \
    -password '' \
    -sql "SELECT 'KNOWN_FINDINGS' AS TABLE_NAME, COUNT(*) AS ROWS FROM KNOWN_FINDINGS WHERE CONFIGURATION_SCOPE_ID = '${scope_uri}';
SELECT 'LOCAL_ONLY_ISSUES' AS TABLE_NAME, COUNT(*) AS ROWS FROM LOCAL_ONLY_ISSUES WHERE CONFIGURATION_SCOPE_ID = '${scope_uri}';
SELECT 'SERVER_FINDINGS' AS TABLE_NAME, COUNT(*) AS ROWS FROM SERVER_FINDINGS WHERE SONAR_PROJECT_KEY LIKE '%${REPO_NAME}%' OR FILE_PATH LIKE '%${REPO_NAME}%';" \
    || echo "Could not query SonarLint local issue database."
fi

echo
echo "==> SonarQube for IDE logs"
if [[ ! -d "$LOGS_DIR" ]]; then
  echo "No VS Code logs directory found at $LOGS_DIR."
else
  mcp_config_valid=false
  if [[ -f "$MCP_CONFIG" ]] && python -m json.tool "$MCP_CONFIG" >/dev/null 2>&1; then
    mcp_config_valid=true
  fi

  mapfile -t sonar_logs < <(
    find "$LOGS_DIR" -path '*/SonarSource.sonarlint-vscode/SonarQube for IDE.log' -type f -printf '%T@ %p\n' 2>/dev/null \
      | sort -nr \
      | head -5 \
      | sed 's/^[^ ]* //'
  )

  if [[ ${#sonar_logs[@]} -eq 0 ]]; then
    echo "No SonarQube for IDE logs found."
  else
    for log_file in "${sonar_logs[@]}"; do
      echo "$log_file"
      messages="$(grep -Ei 'error|warn|issue|problem' "$log_file" || true)"
      messages="$(
        while IFS= read -r line; do
          if [[ "$line" == *"Using H2 "*" which is newer than the version Flyway has been verified with."* ]]; then
            continue
          fi
          if [[ "$line" == *"Error reading MCP config: Unexpected end of JSON input"* && "$mcp_config_valid" == true ]]; then
            continue
          fi
          printf '%s\n' "$line"
        done <<<"$messages"
      )"
      if [[ -n "$messages" ]]; then
        printf '%s\n' "$messages" | tail -20
      else
        echo "No review-needed SonarQube for IDE log messages found."
      fi
      echo
    done
  fi
fi
