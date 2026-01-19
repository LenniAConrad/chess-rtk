#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

java -jar crtk.jar render --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --output assets/banner-pos1.png --size 300 --no-border
java -jar crtk.jar render --fen "r1bq1rk1/ppp2ppp/2n2n2/3pp3/1bP1P3/2N2N2/PP1P1PPP/R1BQKB1R w KQ - 2 6" --output assets/banner-pos2.png --size 240 --no-border
java -jar crtk.jar render --fen "8/8/8/2k5/8/4K3/3P4/8 w - - 0 1" --output assets/banner-pos3.png --size 315 --no-border
java -jar crtk.jar render --fen "r3k2r/pppq1ppp/2npbn2/4p3/2B1P3/2N1BN2/PPPP1PPP/R2QK2R w KQkq - 4 7" --output assets/banner-pos4.png --size 270 --no-border
java -jar crtk.jar render --fen "r1bqkb1r/pppp1ppp/2n2n2/4p3/4P3/2N2N2/PPPP1PPP/R1BQKB1R w KQkq - 2 3" --output assets/banner-pos5.png --size 290 --no-border
java -jar crtk.jar render --fen "2r3k1/1p3ppp/p1n1pn2/3q4/3P4/2PB1N2/PP3PPP/R2Q1RK1 w - - 0 16" --output assets/banner-pos6.png --size 255 --no-border
java -jar crtk.jar render --fen "8/2p5/3k4/2p1p3/2P1P3/3K4/8/8 w - - 0 1" --output assets/banner-pos7.png --size 280 --no-border
java -jar crtk.jar render --fen "r2q1rk1/pp1n1ppp/2p1pn2/2bp4/2B1P3/2N2N2/PPP2PPP/R1BQ1RK1 w - - 4 8" --output assets/banner-pos8.png --size 230 --no-border

python3 - <<'PY'
from PIL import Image

bg = (0xFB, 0xFB, 0xFB, 255)
pad = 20
paths = [
    "assets/banner-pos1.png",
    "assets/banner-pos2.png",
    "assets/banner-pos3.png",
    "assets/banner-pos4.png",
    "assets/banner-pos5.png",
    "assets/banner-pos6.png",
    "assets/banner-pos7.png",
    "assets/banner-pos8.png",
]
for path in paths:
    img = Image.open(path).convert("RGBA")
    out = Image.new("RGBA", (img.width + pad * 2, img.height + pad * 2), bg)
    out.alpha_composite(img, (pad, pad))
    out.save(path)
PY

neato -Tpng assets/crtk-github-banner.dot -o assets/crtk-github-banner.png
python3 - <<'PY'
from PIL import Image
bg = (0xF8,0xFA,0xFC,255)
raw = Image.open('assets/crtk-github-banner.png').convert('RGBA')
W,H = 1280,640
out = Image.new('RGBA',(W,H),bg)
x = (W-raw.size[0])//2
y = (H-raw.size[1])//2
out.alpha_composite(raw,(x,y))
out.save('assets/crtk-github-banner.png')
print("Wrote banner: assets/crtk-github-banner.png")
PY
