#!/usr/bin/env python3
"""Generate non-board ChessRTK logo concept SVGs."""

from __future__ import annotations

from html import escape
from pathlib import Path


ROOT = Path(__file__).resolve().parent
DEEP = "#0d1c14"
GREEN = "#123b2b"
MID = "#2e7d57"
GOLD = "#d8a63a"
PAPER = "#fffefb"
SOFT = "#eef5ef"
MUTED = "#647268"


CONCEPTS = [
    ("01-cli-cursor", "CLI Cursor", "A terminal-native toolkit mark.", "cli_cursor", "dark"),
    ("02-engine-wave", "Engine Wave", "Evaluation signals without board imagery.", "engine_wave", "light"),
    ("03-hash-fingerprint", "Hash Fingerprint", "Stable identities for reproducible runs.", "hash_fingerprint", "dark"),
    ("04-search-dag", "Search DAG", "A compact graph for explored lines.", "search_dag", "light"),
    ("05-pipeline-arc", "Pipeline Arc", "Input, analysis, export, and publishing.", "pipeline_arc", "dark"),
    ("06-dataset-stack", "Dataset Stack", "Records layered into training artifacts.", "dataset_stack", "light"),
    ("07-pdf-press", "PDF Press", "Native publishing as a project identity.", "pdf_press", "dark"),
    ("08-protocol-bus", "Protocol Bus", "UCI traffic normalized into stable output.", "protocol_bus", "light"),
    ("09-repro-seal", "Repro Seal", "Deterministic command runs with a checkable result.", "repro_seal", "dark"),
    ("10-eval-contour", "Eval Contour", "Scores, principal lines, and confidence signals.", "eval_contour", "light"),
    ("11-tensor-cube", "Tensor Cube", "Model-ready data without literal chess imagery.", "tensor_cube", "dark"),
    ("12-config-switchboard", "Config Switchboard", "Engines, models, and exports routed by config.", "config_switchboard", "light"),
]


def circle(cx: int, cy: int, r: int, fill: str, stroke: str = PAPER, width: int = 4) -> str:
    """Return an outlined circle."""
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{stroke}" stroke-width="{width}"/>'


def icon_cli_cursor() -> str:
    return f"""
<rect x="40" y="54" width="200" height="156" rx="18" fill="{DEEP}"/>
<circle cx="68" cy="78" r="7" fill="#e46d59"/>
<circle cx="92" cy="78" r="7" fill="{GOLD}"/>
<circle cx="116" cy="78" r="7" fill="{MID}"/>
<path d="M68 124 L96 146 L68 168" fill="none" stroke="{GOLD}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M116 170 H198" stroke="{PAPER}" stroke-width="12" stroke-linecap="round"/>
<text x="66" y="224" fill="{GREEN}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="24" font-weight="900">crtk --safe</text>
"""


def icon_engine_wave() -> str:
    return f"""
<rect x="38" y="42" width="204" height="196" rx="22" fill="{SOFT}"/>
<path d="M56 172 C82 110 110 215 142 150 S194 82 224 124" fill="none" stroke="{GOLD}" stroke-width="12" stroke-linecap="round"/>
<path d="M56 196 C88 152 118 164 150 126 S194 72 224 84" fill="none" stroke="{MID}" stroke-width="7" stroke-linecap="round"/>
{circle(142, 150, 10, GOLD, PAPER, 3)}
{circle(224, 124, 10, MID, PAPER, 3)}
<text x="66" y="92" fill="{GREEN}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="27" font-weight="900">+0.42</text>
<text x="68" y="121" fill="{MUTED}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="18" font-weight="850">pv e4 e5</text>
"""


def icon_hash_fingerprint() -> str:
    rings = "".join(
        f'<path d="M{58 + i * 18} 154 C{70 + i * 13} {82 - i * 2} {154 + i * 3} {70 + i * 2} {188 + i * 3} {128 + i * 6}" '
        f'fill="none" stroke="{MID if i % 2 else GOLD}" stroke-width="{9 - i}" stroke-linecap="round" opacity="{0.96 - i * 0.08}"/>'
        for i in range(5)
    )
    return f"""
<rect x="36" y="36" width="208" height="208" rx="104" fill="{SOFT}"/>
{rings}
<text x="140" y="222" text-anchor="middle" fill="{GREEN}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="22" font-weight="900">a7c913</text>
"""


def icon_search_dag() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="24" fill="#f8fbf8"/>
<g fill="none" stroke="{MID}" stroke-width="8" stroke-linecap="round">
  <path d="M74 198 C86 154 104 132 140 112"/>
  <path d="M140 112 C120 86 98 68 74 52"/>
  <path d="M140 112 C170 92 194 70 216 46"/>
  <path d="M140 112 C170 128 194 154 218 190"/>
  <path d="M74 198 C116 198 162 196 218 190"/>
</g>
{circle(74, 198, 13, GOLD)}{circle(140, 112, 13, MID)}{circle(74, 52, 10, GREEN)}{circle(216, 46, 10, GOLD)}{circle(218, 190, 10, GREEN)}
"""


def icon_pipeline_arc() -> str:
    return f"""
<rect x="36" y="44" width="208" height="192" rx="24" fill="{SOFT}"/>
<path d="M68 160 C95 76 185 76 212 160" fill="none" stroke="{GOLD}" stroke-width="12" stroke-linecap="round"/>
<path d="M200 140 L218 162 L190 168" fill="{GOLD}"/>
<g font-family="Inter, Arial, sans-serif" font-size="17" font-weight="900" text-anchor="middle">
  <circle cx="68" cy="160" r="24" fill="{GREEN}"/><text x="68" y="166" fill="{PAPER}">IN</text>
  <circle cx="140" cy="80" r="24" fill="{MID}"/><text x="140" y="86" fill="{PAPER}">RUN</text>
  <circle cx="212" cy="160" r="24" fill="{GOLD}"/><text x="212" y="166" fill="{DEEP}">OUT</text>
</g>
"""


def icon_dataset_stack() -> str:
    return f"""
<rect x="48" y="58" width="154" height="122" rx="18" fill="{MID}" opacity="0.32"/>
<rect x="66" y="76" width="154" height="122" rx="18" fill="{MID}" opacity="0.48"/>
<rect x="84" y="94" width="154" height="122" rx="18" fill="{GREEN}"/>
<g fill="{PAPER}">
  <rect x="108" y="122" width="30" height="30" rx="7"/>
  <rect x="153" y="122" width="30" height="30" rx="7"/>
  <rect x="198" y="122" width="30" height="30" rx="7"/>
</g>
<text x="161" y="187" text-anchor="middle" fill="{PAPER}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="22" font-weight="900">records</text>
"""


def icon_pdf_press() -> str:
    return f"""
<rect x="58" y="42" width="132" height="176" rx="16" fill="{PAPER}"/>
<path d="M158 42 V78 H190" fill="{SOFT}"/>
<rect x="84" y="84" width="138" height="104" rx="15" fill="{GREEN}"/>
<path d="M108 112 H190 M108 138 H190 M108 164 H164" stroke="{PAPER}" stroke-width="8" stroke-linecap="round"/>
<rect x="202" y="84" width="18" height="104" rx="5" fill="{GOLD}"/>
<text x="124" y="238" text-anchor="middle" fill="{GREEN}" font-family="Inter, Arial, sans-serif" font-size="28" font-weight="900">PDF</text>
"""


def icon_protocol_bus() -> str:
    labels = ["uci", "isready", "go", "bestmove"]
    rows = "".join(
        f'<rect x="56" y="{60 + i * 38}" width="112" height="25" rx="7" fill="{SOFT}"/>'
        f'<text x="67" y="{79 + i * 38}">{label}</text>'
        f'<path d="M172 {72 + i * 38} H218" stroke="{MID}" stroke-width="7" stroke-linecap="round"/>'
        for i, label in enumerate(labels)
    )
    return f"""
<rect x="36" y="38" width="208" height="204" rx="24" fill="#f8fbf8"/>
<g font-family="ui-monospace, Menlo, Consolas, monospace" font-size="17" font-weight="850" fill="{GREEN}">{rows}</g>
<rect x="205" y="52" width="24" height="156" rx="8" fill="{GREEN}"/>
<path d="M208 204 L217 222 L226 204" fill="{GOLD}"/>
"""


def icon_repro_seal() -> str:
    return f"""
<circle cx="140" cy="140" r="104" fill="{SOFT}"/>
<circle cx="140" cy="140" r="78" fill="{GREEN}"/>
<path d="M98 142 L127 171 L185 108" fill="none" stroke="{GOLD}" stroke-width="16" stroke-linecap="round" stroke-linejoin="round"/>
<text x="140" y="224" text-anchor="middle" fill="{GREEN}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="18" font-weight="900">same input same output</text>
"""


def icon_eval_contour() -> str:
    return f"""
<rect x="38" y="38" width="204" height="204" rx="24" fill="{SOFT}"/>
<path d="M62 174 C96 126 118 186 146 134 S194 82 224 116" fill="none" stroke="{GOLD}" stroke-width="11" stroke-linecap="round"/>
<path d="M62 198 C100 160 132 164 162 128 S194 94 224 88" fill="none" stroke="{MID}" stroke-width="7" stroke-linecap="round"/>
<path d="M62 136 C98 106 126 116 154 90 S194 60 224 64" fill="none" stroke="{GREEN}" stroke-width="5" stroke-linecap="round" opacity="0.5"/>
<text x="70" y="77" fill="{GREEN}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="21" font-weight="900">score map</text>
"""


def icon_tensor_cube() -> str:
    return f"""
<path d="M80 88 L146 56 L214 88 L214 166 L146 204 L80 166 Z" fill="{SOFT}" stroke="{PAPER}" stroke-width="4"/>
<path d="M80 88 L146 124 L214 88 M146 124 V204 M80 166 L146 124 L214 166" fill="none" stroke="{MID}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M104 102 L146 82 L190 102 M104 138 L146 160 L190 138" fill="none" stroke="{GOLD}" stroke-width="6" stroke-linecap="round"/>
<text x="146" y="235" text-anchor="middle" fill="{PAPER}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="21" font-weight="900">planes</text>
"""


def icon_config_switchboard() -> str:
    return f"""
<rect x="42" y="44" width="196" height="190" rx="22" fill="{SOFT}"/>
<g stroke="{MID}" stroke-width="7" stroke-linecap="round">
  <path d="M84 84 H198"/><path d="M84 132 H198"/><path d="M84 180 H198"/>
</g>
<g>
  {circle(112, 84, 12, GOLD, PAPER, 3)}
  {circle(166, 132, 12, MID, PAPER, 3)}
  {circle(132, 180, 12, GREEN, PAPER, 3)}
</g>
<text x="140" y="224" text-anchor="middle" fill="{GREEN}" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="19" font-weight="900">toml routes</text>
"""


ICONS = {
    "cli_cursor": icon_cli_cursor,
    "engine_wave": icon_engine_wave,
    "hash_fingerprint": icon_hash_fingerprint,
    "search_dag": icon_search_dag,
    "pipeline_arc": icon_pipeline_arc,
    "dataset_stack": icon_dataset_stack,
    "pdf_press": icon_pdf_press,
    "protocol_bus": icon_protocol_bus,
    "repro_seal": icon_repro_seal,
    "eval_contour": icon_eval_contour,
    "tensor_cube": icon_tensor_cube,
    "config_switchboard": icon_config_switchboard,
}


def svg_for(index: int, slug: str, title: str, tagline: str, icon: str, theme: str) -> str:
    """Return one complete logo concept."""
    dark = theme == "dark"
    bg = (
        f'<rect width="1200" height="420" rx="48" fill="{DEEP}"/>'
        f'<rect x="32" y="32" width="1136" height="356" rx="38" fill="{GREEN}" opacity="0.42"/>'
        if dark
        else f'<rect width="1200" height="420" rx="48" fill="#f4f7f3"/>'
        f'<rect x="42" y="42" width="1116" height="336" rx="38" fill="{PAPER}" stroke="#d6dfd7" stroke-width="2"/>'
    )
    word = PAPER if dark else GREEN
    body = "#dce9df" if dark else "#3e4b42"
    title_color = GOLD if dark else MID
    shell = "#fffefb" if dark else DEEP
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 420" role="img" aria-labelledby="title desc">
  <title id="title">ChessRTK {escape(title)} logo concept</title>
  <desc id="desc">{escape(tagline)}</desc>
  <defs>
    <filter id="shadow" x="-12%" y="-12%" width="124%" height="124%">
      <feDropShadow dx="0" dy="16" stdDeviation="18" flood-color="#07100c" flood-opacity="0.22"/>
    </filter>
  </defs>
  {bg}
  <g transform="translate(82 70)" filter="url(#shadow)">
    <rect width="280" height="280" rx="34" fill="{shell}"/>
    {ICONS[icon]()}
  </g>
  <g transform="translate(424 112)">
    <text x="0" y="76" fill="{word}" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="86" font-weight="850" letter-spacing="0">ChessRTK</text>
    <text x="3" y="123" fill="{title_color}" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="26" font-weight="850" letter-spacing="0">{escape(title).upper()}</text>
    <text x="3" y="172" fill="{body}" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="30" font-weight="500" letter-spacing="0">{escape(tagline)}</text>
    <text x="3" y="224" fill="{title_color}" opacity="0.76" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="22" font-weight="850">concept {index:02d} / non-board mark</text>
  </g>
</svg>
"""


def write_preview() -> None:
    """Write a preview page for the concept set."""
    cards = "\n".join(
        f"""<article>
  <h2>{escape(title)}</h2>
  <img src="{escape(slug)}.svg" alt="ChessRTK {escape(title)} logo concept">
</article>"""
        for slug, title, _, _, _ in CONCEPTS
    )
    html = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ChessRTK Non-Board Logo Concepts</title>
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
      <h1>ChessRTK Non-Board Logo Concepts</h1>
      <p>Twelve directions that avoid the chessboard motif and focus on CLI, engine, data, and reproducibility ideas.</p>
    </header>
    {cards}
  </main>
</body>
</html>
"""
    (ROOT / "preview.html").write_text(html, encoding="utf-8")


def write_readme() -> None:
    """Write the concept index."""
    rows = "\n".join(f"| `{slug}.svg` | {title} | {tagline} |" for slug, title, tagline, _, _ in CONCEPTS)
    readme = f"""# Non-Board ChessRTK Logo Concepts

These concepts avoid the chessboard motif. They focus on ChessRTK as a command
line, engine, data, and publishing toolkit.

| File | Concept | Core idea |
| --- | --- | --- |
{rows}

Open `preview.html` in a browser to compare the full set.
"""
    (ROOT / "README.md").write_text(readme, encoding="utf-8")


def main() -> int:
    """Generate all SVGs and support files."""
    ROOT.mkdir(parents=True, exist_ok=True)
    for index, (slug, title, tagline, icon, theme) in enumerate(CONCEPTS, start=1):
        (ROOT / f"{slug}.svg").write_text(svg_for(index, slug, title, tagline, icon, theme), encoding="utf-8")
    write_preview()
    write_readme()
    print(f"generated {len(CONCEPTS)} non-board logo concepts in {ROOT.relative_to(Path.cwd())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
