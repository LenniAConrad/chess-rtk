#!/usr/bin/env bash
# Universal Chess CLI installer (Ubuntu)
# Repo: https://github.com/LenniAConrad/universal-chess-cli
# Installs a launcher at /usr/local/bin/ucicli that runs from this repo.
set -euo pipefail

APP_NAME="ucicli"
REPO_OWNER="LenniAConrad"
REPO_NAME="universal-chess-cli"

# Resolve repo root (assume script is placed in repo root)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd -- "$SCRIPT_DIR" && pwd)"
OUT_DIR="$APP_HOME/out"
LAUNCHER="/usr/local/bin/$APP_NAME"

SUDO=''
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO='sudo'
  else
    echo "This script needs root privileges for package installs and installing $LAUNCHER."
    echo "Please run as root or install sudo."
    exit 1
  fi
fi

confirm() {
  # confirm "Prompt" [default:Y|N]
  local prompt="${1:-Proceed?}"
  local def="${2:-Y}"
  local hint="[Y/n]"
  [[ "$def" == "N" ]] && hint="[y/N]"
  while true; do
    read -rp "$prompt $hint " ans || exit 1
    ans="${ans:-$def}"
    case "$ans" in
      [Yy]*) return 0 ;;
      [Nn]*) return 1 ;;
      *) echo "Please answer y or n." ;;
    esac
  done
}

need_apt_update=0
apt_install() {
  local pkg="$1"
  if ! dpkg -s "$pkg" >/dev/null 2>&1; then
    if [[ $need_apt_update -eq 0 ]]; then
      $SUDO apt-get update -y
      need_apt_update=1
    fi
    $SUDO apt-get install -y "$pkg"
  fi
}

echo "== Universal Chess CLI installer =="
echo "Repo path: $APP_HOME"
echo

echo "Checking prerequisites..."
JAVA_OK=0
JAVAC_OK=0

if command -v java >/dev/null 2>&1; then
  # Try to detect major version (works for modern OpenJDK)
  ver="$(java -version 2>&1 | head -n1 | sed -E 's/.*version "?([0-9]+).*/\1/')"
  if [[ "$ver" =~ ^[0-9]+$ && "$ver" -ge 17 ]]; then
    JAVA_OK=1
  else
    echo "  -> Found Java, but version <$ver> (<17)."
  fi
else
  echo "  -> Java not found."
fi

if command -v javac >/dev/null 2>&1; then
  JAVAC_OK=1
else
  echo "  -> javac not found (needed to build)."
fi

if [[ $JAVA_OK -eq 0 || $JAVAC_OK -eq 0 ]]; then
  if confirm "Install OpenJDK 17 (JRE+JDK) now?" "Y"; then
    apt_install openjdk-17-jdk
    JAVA_OK=1
    JAVAC_OK=1
  else
    echo "Skipping Java installation. Ensure Java 17 (and javac) is available."
  fi
fi

if command -v stockfish >/dev/null 2>&1; then
  echo "Stockfish found at: $(command -v stockfish)"
else
  if confirm "Install Stockfish (optional, recommended)?" "Y"; then
    apt_install stockfish
  else
    echo "Skipping Stockfish installation. You can set your engine path in config/default.engine.toml."
  fi
fi

echo
echo "Creating output & log directories..."
mkdir -p "$OUT_DIR" "$APP_HOME/dump" "$APP_HOME/session"

echo "Compiling sources (pure javac)..."
find "$APP_HOME/src" -name "*.java" -print0 | xargs -0 javac -d "$OUT_DIR"

echo "Installing launcher to $LAUNCHER ..."
LAUNCHER_TMP="$(mktemp)"
cat > "$LAUNCHER_TMP" <<EOF
#!/usr/bin/env bash
set -euo pipefail
APP_HOME="$APP_HOME"
JAVA_BIN="\${JAVA_BIN:-java}"
cd "\$APP_HOME"
exec "\$JAVA_BIN" -cp "\$APP_HOME/out" application.Main "\$@"
EOF

$SUDO mv "$LAUNCHER_TMP" "$LAUNCHER"
$SUDO chmod +x "$LAUNCHER"

echo
echo "== Done =="
echo "Launcher installed: $LAUNCHER"
echo
echo "Next steps (please read):"
echo "  1) Open README.MD for quick usage."
echo "  2) Review and tweak config files under ./config/:"
echo "       - cli.config.toml         (CLI defaults, output dir, gates)"
echo "       - default.engine.toml     (UCI engine protocol; set your engine path)"
echo "       - book.eco.toml           (optional ECO names)"
echo "  3) Verify LICENSE.txt fits your intentions."
echo
echo "Examples:"
echo "  $APP_NAME help"
echo "  $APP_NAME print --fen \"startpos\""
echo "  $APP_NAME convert -i data/input.record -o dump/output.plain --sidelines"
echo "  $APP_NAME mine -i data/seeds.pgn -o dump/ --engine-instances 4 --max-duration 60s"
echo
echo "Tip: You can run from anywhere using '$APP_NAME'."
