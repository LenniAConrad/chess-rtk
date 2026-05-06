#!/usr/bin/env python3
"""Generate signature-level ChessRTK logo concept SVGs."""

from __future__ import annotations

from html import escape
from pathlib import Path


ROOT = Path(__file__).resolve().parent
DEEP = "#07110c"
INK = "#0d1c14"
GREEN = "#123b2b"
MID = "#2e7d57"
MINT = "#8fd3aa"
GOLD = "#d8a63a"
PAPER = "#fffefb"
SOFT = "#eef5ef"
MUTED = "#647268"


CONCEPTS = [
    ("01-state-lattice", "State Lattice", "A reproducible position graph with one verified path.", "state_lattice", "dark"),
    ("02-fen-helix", "FEN Helix", "Encoded chess state as a living, repeatable strand.", "fen_helix", "light"),
    ("03-proof-loop", "Proof Loop", "A closed command loop with a checkable result.", "proof_loop", "dark"),
    ("04-kernel-orbit", "Kernel Orbit", "One rules core with tools orbiting around it.", "kernel_orbit", "light"),
    ("05-search-prism", "Search Prism", "A position split into candidate lines and resolved.", "search_prism", "dark"),
    ("06-command-sigil", "Command Sigil", "A terminal mark built from prompt, route, and result.", "command_sigil", "light"),
    ("07-record-weave", "Record Weave", "Analysis records woven into datasets and books.", "record_weave", "dark"),
    ("08-engine-pulse", "Engine Pulse", "Evaluation as a signal with a stable center line.", "engine_pulse", "light"),
    ("09-transposition-knot", "Transposition Knot", "Different paths meeting at the same state.", "transposition_knot", "dark"),
    ("10-rtk-crest", "RTK Crest", "A compact identity mark for releases and favicons.", "rtk_crest", "light"),
]


def node(cx: int, cy: int, fill: str = GOLD, r: int = 10, stroke: str = PAPER) -> str:
    """Return an outlined node."""
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{stroke}" stroke-width="4"/>'


def icon_state_lattice() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="28" fill="{SOFT}"/>
<g fill="none" stroke="{GREEN}" stroke-width="5" opacity="0.48">
  <path d="M78 74 L140 48 L202 74 L202 144 L140 176 L78 144 Z"/>
  <path d="M78 144 L140 112 L202 144"/>
  <path d="M140 48 V112 L140 176"/>
  <path d="M78 74 L140 112 L202 74"/>
</g>
<path d="M78 144 L140 112 L202 74" fill="none" stroke="{GOLD}" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M140 176 C116 204 94 214 66 218" fill="none" stroke="{MID}" stroke-width="7" stroke-linecap="round"/>
{node(78, 144, MID, 10)}{node(140, 112, GOLD, 12)}{node(202, 74, GOLD, 10)}{node(66, 218, MID, 8)}
"""


def icon_fen_helix() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="28" fill="{SOFT}"/>
<path d="M76 70 C128 98 154 182 210 210" fill="none" stroke="{GOLD}" stroke-width="10" stroke-linecap="round"/>
<path d="M210 70 C154 98 128 182 76 210" fill="none" stroke="{MID}" stroke-width="10" stroke-linecap="round"/>
<g stroke="{GREEN}" stroke-width="5" stroke-linecap="round" opacity="0.68">
  <path d="M94 88 H192"/><path d="M86 120 H166"/><path d="M96 158 H158"/><path d="M86 190 H188"/>
</g>
<g font-family="ui-monospace, Menlo, Consolas, monospace" font-size="18" font-weight="900" fill="{GREEN}">
  <text x="66" y="74">FEN</text>
  <text x="178" y="224">0 1</text>
</g>
"""


def icon_proof_loop() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="102" fill="{SOFT}"/>
<path d="M82 152 C58 92 116 56 164 72 C216 90 220 164 174 196 C136 222 86 206 70 170" fill="none" stroke="{MID}" stroke-width="11" stroke-linecap="round"/>
<path d="M176 68 L202 84 L172 96" fill="{MID}"/>
<path d="M96 142 L126 172 L184 108" fill="none" stroke="{GOLD}" stroke-width="15" stroke-linecap="round" stroke-linejoin="round"/>
<text x="140" y="224" text-anchor="middle" fill="{GREEN}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="18" font-weight="900">deterministic</text>
"""


def icon_kernel_orbit() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="28" fill="{SOFT}"/>
<circle cx="140" cy="140" r="44" fill="{GREEN}"/>
<text x="140" y="152" text-anchor="middle" fill="{PAPER}" font-family="Inter, Arial, sans-serif" font-size="32" font-weight="900">CR</text>
<g fill="none" stroke="{MID}" stroke-width="5" opacity="0.65">
  <circle cx="140" cy="140" r="78"/>
  <ellipse cx="140" cy="140" rx="88" ry="44" transform="rotate(-28 140 140)"/>
</g>
{node(72, 110, GOLD, 9)}{node(206, 98, MID, 9)}{node(210, 178, GOLD, 9)}{node(94, 204, MID, 9)}
"""


def icon_search_prism() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="28" fill="{SOFT}"/>
<path d="M72 190 L140 64 L218 190 Z" fill="{GREEN}" opacity="0.18" stroke="{GREEN}" stroke-width="6" stroke-linejoin="round"/>
<path d="M140 64 V190" stroke="{MID}" stroke-width="7" stroke-linecap="round"/>
<path d="M72 190 H218" stroke="{GREEN}" stroke-width="7" stroke-linecap="round"/>
<path d="M92 178 L140 112 L196 78" stroke="{GOLD}" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M140 112 L204 178" stroke="{MID}" stroke-width="7" stroke-linecap="round"/>
{node(140, 112, GOLD, 11)}{node(196, 78, GOLD, 9)}{node(204, 178, MID, 9)}
"""


def icon_command_sigil() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="28" fill="{INK}"/>
<path d="M76 92 L116 122 L76 154" fill="none" stroke="{GOLD}" stroke-width="15" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M126 154 H202" stroke="{PAPER}" stroke-width="12" stroke-linecap="round"/>
<path d="M92 192 C130 166 160 128 202 74" fill="none" stroke="{MID}" stroke-width="9" stroke-linecap="round"/>
{node(92, 192, GOLD, 9, INK)}{node(202, 74, MID, 9, INK)}
<text x="86" y="214" fill="{MINT}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="18" font-weight="900">crtk</text>
"""


def icon_record_weave() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="28" fill="{SOFT}"/>
<path d="M62 88 C96 58 124 120 154 88 S198 58 222 90" fill="none" stroke="{GOLD}" stroke-width="16" stroke-linecap="round"/>
<path d="M62 138 C96 108 124 170 154 138 S198 108 222 140" fill="none" stroke="{MID}" stroke-width="16" stroke-linecap="round"/>
<path d="M62 188 C96 158 124 220 154 188 S198 158 222 190" fill="none" stroke="{GREEN}" stroke-width="16" stroke-linecap="round"/>
<g stroke="{PAPER}" stroke-width="6" opacity="0.8">
  <path d="M88 72 V204"/><path d="M140 72 V204"/><path d="M192 72 V204"/>
</g>
"""


def icon_engine_pulse() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="28" fill="{SOFT}"/>
<path d="M62 154 C92 96 116 204 146 142 S192 82 222 118" fill="none" stroke="{GOLD}" stroke-width="12" stroke-linecap="round"/>
<path d="M62 184 C96 150 124 154 154 122 S196 86 222 88" fill="none" stroke="{MID}" stroke-width="7" stroke-linecap="round"/>
<path d="M64 88 H204" stroke="{GREEN}" stroke-width="6" stroke-linecap="round" opacity="0.35"/>
<text x="72" y="78" fill="{GREEN}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="20" font-weight="900">pv</text>
{node(146, 142, GOLD, 10)}{node(222, 118, MID, 9)}
"""


def icon_transposition_knot() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="28" fill="{SOFT}"/>
<path d="M72 104 C112 54 168 54 208 104 C168 154 112 154 72 104 Z" fill="none" stroke="{MID}" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M72 176 C112 126 168 126 208 176 C168 226 112 226 72 176 Z" fill="none" stroke="{GOLD}" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M96 124 C120 146 160 146 184 124" fill="none" stroke="{GREEN}" stroke-width="8" stroke-linecap="round"/>
{node(140, 140, GREEN, 12)}{node(72, 104, MID, 8)}{node(208, 176, GOLD, 8)}
"""


def icon_rtk_crest() -> str:
    return f"""
<path d="M140 42 L222 80 V150 C222 196 190 226 140 242 C90 226 58 196 58 150 V80 Z" fill="{GREEN}"/>
<path d="M140 70 L196 96 V148 C196 180 176 202 140 216 C104 202 84 180 84 148 V96 Z" fill="{DEEP}" opacity="0.55"/>
<text x="140" y="151" text-anchor="middle" fill="{PAPER}" font-family="Inter, Arial, sans-serif" font-size="58" font-weight="900">RTK</text>
<path d="M92 184 C126 158 158 120 196 82" fill="none" stroke="{GOLD}" stroke-width="10" stroke-linecap="round"/>
<circle cx="92" cy="184" r="10" fill="{GOLD}"/>
<circle cx="196" cy="82" r="10" fill="{GOLD}"/>
"""


ICONS = {
    "state_lattice": icon_state_lattice,
    "fen_helix": icon_fen_helix,
    "proof_loop": icon_proof_loop,
    "kernel_orbit": icon_kernel_orbit,
    "search_prism": icon_search_prism,
    "command_sigil": icon_command_sigil,
    "record_weave": icon_record_weave,
    "engine_pulse": icon_engine_pulse,
    "transposition_knot": icon_transposition_knot,
    "rtk_crest": icon_rtk_crest,
}


def svg_for(index: int, slug: str, title: str, tagline: str, icon: str, theme: str) -> str:
    """Return one complete logo SVG."""
    dark = theme == "dark"
    bg = (
        f'<rect width="1200" height="420" rx="48" fill="{DEEP}"/>'
        f'<rect x="34" y="34" width="1132" height="352" rx="40" fill="{GREEN}" opacity="0.48"/>'
        if dark
        else f'<rect width="1200" height="420" rx="48" fill="#f4f7f3"/>'
        f'<rect x="42" y="42" width="1116" height="336" rx="38" fill="{PAPER}" stroke="#d6dfd7" stroke-width="2"/>'
    )
    word = PAPER if dark else GREEN
    body = "#dce9df" if dark else "#3e4b42"
    title_color = GOLD if dark else MID
    shell = "#fffefb" if dark else DEEP
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 420" role="img" aria-labelledby="title desc">
  <title id="title">ChessRTK {escape(title)} signature logo concept</title>
  <desc id="desc">{escape(tagline)}</desc>
  <defs>
    <filter id="shadow" x="-12%" y="-12%" width="124%" height="124%">
      <feDropShadow dx="0" dy="16" stdDeviation="18" flood-color="#07100c" flood-opacity="0.24"/>
    </filter>
  </defs>
  {bg}
  <g transform="translate(82 70)" filter="url(#shadow)">
    <rect width="280" height="280" rx="36" fill="{shell}"/>
    {ICONS[icon]()}
  </g>
  <g transform="translate(424 112)">
    <text x="0" y="76" fill="{word}" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="86" font-weight="850" letter-spacing="0">ChessRTK</text>
    <text x="3" y="123" fill="{title_color}" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="26" font-weight="850" letter-spacing="0">{escape(title).upper()}</text>
    <text x="3" y="172" fill="{body}" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="30" font-weight="500" letter-spacing="0">{escape(tagline)}</text>
    <text x="3" y="224" fill="{title_color}" opacity="0.76" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="22" font-weight="850">concept {index:02d} / signature candidate</text>
  </g>
</svg>
"""


def write_preview() -> None:
    """Write the preview page."""
    cards = "\n".join(
        f"""<article>
  <h2>{escape(title)}</h2>
  <img src="{escape(slug)}.svg" alt="ChessRTK {escape(title)} signature logo concept">
</article>"""
        for slug, title, _, _, _ in CONCEPTS
    )
    html = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ChessRTK Signature Logo Concepts</title>
  <style>
    *, *::before, *::after {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      padding: 32px;
      background: #edf2ee;
      color: #17221b;
      font: 16px/1.55 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }}
    main {{ display: grid; gap: 22px; max-width: 1180px; margin: 0 auto; }}
    h1 {{ margin: 0 0 8px; color: #123b2b; font-size: 2rem; letter-spacing: 0; }}
    p {{ margin: 0 0 28px; color: #647268; }}
    article {{
      padding: 18px;
      border: 1px solid #d6dfd7;
      border-radius: 8px;
      background: #fffefb;
      box-shadow: 0 16px 42px rgba(20, 37, 27, 0.08);
    }}
    h2 {{ margin: 0 0 12px; color: #123b2b; font-size: 1rem; }}
    img {{ display: block; width: 100%; border-radius: 8px; }}
  </style>
</head>
<body>
  <main>
    <header>
      <h1>ChessRTK Signature Logo Concepts</h1>
      <p>Ten more deliberate marks aimed at a unique project identity, not just another chess logo.</p>
    </header>
    {cards}
  </main>
</body>
</html>
"""
    (ROOT / "preview.html").write_text(html, encoding="utf-8")


def write_readme() -> None:
    """Write the signature concept index."""
    rows = "\n".join(f"| `{slug}.svg` | {title} | {tagline} |" for slug, title, tagline, _, _ in CONCEPTS)
    readme = f"""# Signature ChessRTK Logo Concepts

This round is intentionally less literal. The goal is to find a distinctive
identity that can become recognizable on its own: state lattices, encoded
strands, proof loops, command sigils, record weaves, and transposition knots.

| File | Concept | Core idea |
| --- | --- | --- |
{rows}

Open `preview.html` in a browser to compare the full set.
"""
    (ROOT / "README.md").write_text(readme, encoding="utf-8")


def main() -> int:
    """Generate all signature SVGs."""
    ROOT.mkdir(parents=True, exist_ok=True)
    for index, (slug, title, tagline, icon, theme) in enumerate(CONCEPTS, start=1):
        (ROOT / f"{slug}.svg").write_text(svg_for(index, slug, title, tagline, icon, theme), encoding="utf-8")
    write_preview()
    write_readme()
    print(f"generated {len(CONCEPTS)} signature logo concepts in {ROOT.relative_to(Path.cwd())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
