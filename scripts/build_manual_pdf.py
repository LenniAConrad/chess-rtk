#!/usr/bin/env python3
"""Build a print-friendly ChessRTK manual PDF from the wiki documentation."""

from __future__ import annotations

import argparse
import html
import re
import shutil
import subprocess
import sys
from pathlib import Path

import build_docs_site as docs


DEFAULT_HTML = docs.DOCS / "manual.html"
DEFAULT_PDF = docs.DOCS / "chessrtk-manual.pdf"
A4_WIDTH_PT = 594.96
A4_HEIGHT_PT = 841.92
PDF_SIZE_TOLERANCE_PT = 1.0


def page_anchor(page: docs.Page) -> str:
    """Return the manual chapter anchor for a generated docs page."""
    return "chapter-" + docs.slug(Path(page.source).stem)


def chapter_title(page: docs.Page) -> str:
    """Return the title used in the manual chapter heading."""
    if page.source == "Home.md":
        return "Project Overview"
    return page.title


def manual_markdown(page: docs.Page) -> str:
    """Return Markdown adjusted for a continuous manual chapter."""
    markdown = docs.markdown_for_page(page)
    return re.sub(r"^# [^\n]+\n+", "", markdown, count=1).strip()


def rewrite_manual_links(body: str, page: docs.Page, href_to_anchor: dict[str, str]) -> str:
    """Rewrite generated-page links into same-document manual links."""
    current_anchor = page_anchor(page)

    def id_repl(match: re.Match[str]) -> str:
        return f'id="{current_anchor}-{match.group(1)}"'

    def href_repl(match: re.Match[str]) -> str:
        href = html.unescape(match.group(1))
        if href.startswith(("#", "mailto:")) or "://" in href:
            if href.startswith("#"):
                return f'href="#{current_anchor}-{html.escape(href[1:])}"'
            return f'href="{html.escape(href)}"'
        file_part, anchor = (href.split("#", 1) + [""])[:2] if "#" in href else (href, "")
        if file_part in href_to_anchor:
            target = href_to_anchor[file_part]
            if anchor:
                target = f"{target}-{anchor}"
            return f'href="#{html.escape(target)}"'
        return f'href="{html.escape(href)}"'

    body = re.sub(r'id="([^"]+)"', id_repl, body)
    return re.sub(r'href="([^"]+)"', href_repl, body)


def manual_toc(pages: list[docs.Page]) -> str:
    """Render a categorized table of contents for the manual."""
    grouped: dict[str, list[docs.Page]] = {}
    for page in pages:
        grouped.setdefault(page.category, []).append(page)
    sections = []
    for category, category_pages in grouped.items():
        links = "\n".join(
            f'<li><a href="#{html.escape(page_anchor(page))}">{html.escape(chapter_title(page))}</a></li>'
            for page in category_pages
        )
        sections.append(f"<section><h2>{html.escape(category)}</h2><ol>{links}</ol></section>")
    return "\n".join(sections)


def render_manual_html(pages: list[docs.Page]) -> str:
    """Render the full manual as one print-oriented HTML document."""
    href_to_anchor = {page.href: page_anchor(page) for page in pages}
    chapters = []
    for number, page in enumerate(pages, start=1):
        body, _, _ = docs.render_markdown(manual_markdown(page))
        body = rewrite_manual_links(body, page, href_to_anchor)
        body = body.replace(' loading="lazy"', ' loading="eager" decoding="sync"')
        chapters.append(
            f"""
<section class="chapter">
  <p class="chapter-kicker">{html.escape(page.category)} · Chapter {number}</p>
  <h1 id="{html.escape(page_anchor(page))}">{html.escape(chapter_title(page))}</h1>
  <div class="chapter-body">
    {body}
  </div>
</section>
"""
        )
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>ChessRTK Manual</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>{MANUAL_STYLES}</style>
</head>
<body>
  <section class="cover">
    <div class="cover-grid">
      <div class="cover-copy">
        <p class="eyebrow">ChessRTK Manual</p>
        <h1>ChessRTK</h1>
        <p class="tagline">Reference manual for deterministic chess commands, engine workflows, datasets, desktop analysis, and publishing.</p>
      </div>
      <img class="cover-logo" src="assets/logo/crtk-logo.png" alt="ChessRTK chemical-board logo">
    </div>
    <dl class="cover-meta">
      <div><dt>Runtime</dt><dd>Java 17 toolkit</dd></div>
      <div><dt>Source</dt><dd><code>wiki/*.md</code></dd></div>
    </dl>
    <p class="cover-note">One shared chess core for CLI commands, the Swing Workbench, engines, datasets, diagrams, and book publishing.</p>
  </section>
  <section class="contents">
    <p class="eyebrow">Contents</p>
    <h1>Manual Contents</h1>
    {manual_toc(pages)}
  </section>
  {"".join(chapters)}
</body>
</html>
"""


def chromium_binary() -> str:
    """Return a Chromium-compatible browser executable."""
    for name in ("chromium", "chromium-browser", "google-chrome", "google-chrome-stable", "chrome"):
        path = shutil.which(name)
        if path:
            return path
    raise RuntimeError("Chromium or Google Chrome is required to render the manual PDF")


def render_pdf(html_path: Path, pdf_path: Path) -> None:
    """Render the manual HTML to PDF with a headless browser."""
    pdf_path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            chromium_binary(),
            "--headless",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--run-all-compositor-stages-before-draw",
            "--virtual-time-budget=1000",
            "--no-pdf-header-footer",
            "--print-to-pdf-no-header",
            f"--print-to-pdf={pdf_path}",
            html_path.resolve().as_uri(),
        ],
        check=True,
    )
    verify_pdf_is_a4(pdf_path)


def verify_pdf_is_a4(pdf_path: Path) -> None:
    """Fail if Chromium renders a non-A4 manual PDF."""
    data = pdf_path.read_bytes().decode("latin-1", errors="ignore")
    media_boxes = re.findall(
        r"/MediaBox\s*\[\s*([+-]?\d+(?:\.\d+)?)\s+([+-]?\d+(?:\.\d+)?)\s+"
        r"([+-]?\d+(?:\.\d+)?)\s+([+-]?\d+(?:\.\d+)?)\s*\]",
        data,
    )
    if not media_boxes:
        raise RuntimeError(f"Unable to verify PDF page size for {pdf_path}")
    for x0_text, y0_text, x1_text, y1_text in media_boxes:
        width = abs(float(x1_text) - float(x0_text))
        height = abs(float(y1_text) - float(y0_text))
        if (
                abs(width - A4_WIDTH_PT) > PDF_SIZE_TOLERANCE_PT
                or abs(height - A4_HEIGHT_PT) > PDF_SIZE_TOLERANCE_PT):
            raise RuntimeError(
                f"Manual PDF must be A4 ({A4_WIDTH_PT:.2f} x {A4_HEIGHT_PT:.2f} pt), "
                f"got {width:.2f} x {height:.2f} pt"
            )


def parse_args(argv: list[str]) -> argparse.Namespace:
    """Parse command-line options."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--html", type=Path, default=DEFAULT_HTML, help="manual HTML output path")
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF, help="manual PDF output path")
    parser.add_argument("--html-only", action="store_true", help="write HTML without rendering the PDF")
    return parser.parse_args(argv)


def display_path(path: Path) -> str:
    """Return a user-facing output path without requiring it to live under the repo."""
    try:
        return str(path.resolve().relative_to(docs.ROOT))
    except ValueError:
        return str(path)


def main(argv: list[str] | None = None) -> int:
    """Build the manual HTML and PDF."""
    args = parse_args(sys.argv[1:] if argv is None else argv)
    docs.main()
    pages = docs.all_pages()
    args.html.parent.mkdir(parents=True, exist_ok=True)
    args.html.write_text(render_manual_html(pages), encoding="utf-8")
    if not args.html_only:
        render_pdf(args.html, args.pdf)
        print(f"generated manual PDF at {display_path(args.pdf)}")
    print(f"generated manual HTML at {display_path(args.html)}")
    return 0


MANUAL_STYLES = r"""
:root {
  color-scheme: light;
  --page: #fbfcfb;
  --surface: #ffffff;
  --surface-muted: #f3f7f6;
  --ink: #172126;
  --ink-soft: #2f3f46;
  --muted: #59686d;
  --line: #dce5e3;
  --line-strong: #c5d3d0;
  --accent: #157f89;
  --accent-strong: #0d5962;
  --accent-soft: #e8f4f3;
  --gold: #d7a11f;
  --gold-soft: #fbf4df;
  --code-bg: #102126;
  --code-line: #243a40;
  --code-text: #d8e7e8;
  --shadow: 0 18px 45px rgba(18, 33, 38, 0.10);
}

@page {
  size: A4;
  margin: 16mm 15mm 18mm;
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  background: var(--page);
}

body {
  margin: 0;
  background: var(--surface);
  color: var(--ink);
  font: 10.2pt/1.52 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  print-color-adjust: exact;
  -webkit-print-color-adjust: exact;
}

a {
  color: var(--accent-strong);
  text-decoration-color: rgba(21, 127, 137, 0.42);
  text-decoration-thickness: 0.06em;
  text-underline-offset: 0.18em;
}

p {
  margin: 0 0 3.2mm;
}

strong {
  color: var(--ink);
}

img {
  display: block;
  max-width: min(100%, 168mm);
  height: auto;
  margin: 5mm auto;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--surface);
}

code {
  padding: 0.08em 0.28em;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--surface-muted);
  color: var(--accent-strong);
  font: 0.92em/1.35 "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  overflow-wrap: anywhere;
}

pre {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  margin: 4mm 0;
  padding: 3.6mm 4mm;
  border: 1px solid var(--code-line);
  border-left: 3px solid var(--accent);
  border-radius: 7px;
  background: var(--code-bg);
  color: var(--code-text);
  font: 8.6pt/1.55 "SFMono-Regular", Consolas, "Liberation Mono", monospace;
}

pre code {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
}

.cover,
.contents,
.chapter {
  break-after: page;
}

.chapter:last-child {
  break-after: auto;
}

.cover {
  position: relative;
  min-height: 257mm;
  overflow: hidden;
  padding: 18mm;
  border: 1px solid var(--line);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(21, 127, 137, 0.08), transparent 42%),
    linear-gradient(180deg, var(--surface), var(--surface-muted));
}

.cover-grid {
  display: grid;
  grid-template-columns: 1fr 56mm;
  gap: 16mm;
  align-items: start;
  margin: 18mm 0 18mm;
}

.cover-logo {
  width: 56mm;
  max-width: 56mm;
  margin: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.cover h1 {
  margin: 6mm 0 4mm;
  color: var(--ink);
  font-size: 45pt;
  line-height: 0.96;
  letter-spacing: 0;
}

.cover .tagline {
  max-width: 118mm;
  margin: 0;
  color: var(--ink-soft);
  font-size: 14.6pt;
  line-height: 1.45;
}

.cover-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4mm;
  max-width: 88mm;
  margin: 0 0 12mm;
}

.cover-meta div {
  min-height: 22mm;
  padding: 4mm;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.cover dt {
  color: var(--accent-strong);
  font-weight: 800;
}

.cover dd {
  margin: 1.2mm 0 0;
  color: var(--ink-soft);
}

.cover dd code {
  white-space: nowrap;
  font-size: 0.86em;
}

.cover-note {
  width: 134mm;
  margin: 22mm 0 0;
  padding-top: 5mm;
  border-top: 1px solid var(--line-strong);
  color: var(--muted);
  font-size: 11.2pt;
}

.eyebrow,
.chapter-kicker {
  margin: 0;
  color: var(--accent-strong);
  font-size: 8.7pt;
  font-weight: 850;
  letter-spacing: 0;
  text-transform: uppercase;
}

.contents {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 5mm;
  align-content: start;
  padding: 4mm 0 0;
}

.contents .eyebrow,
.contents h1 {
  grid-column: 1 / -1;
}

.contents h1 {
  margin: 0 0 1mm;
  color: var(--ink);
  font-size: 27pt;
  line-height: 1.05;
  letter-spacing: 0;
}

.contents section {
  break-inside: avoid;
  margin: 0;
  padding: 4mm;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
}

.contents section:nth-of-type(2n) {
  background: var(--surface-muted);
}

.contents h2 {
  margin: 0 0 2.5mm;
  color: var(--ink);
  font-size: 12.8pt;
  line-height: 1.2;
}

.contents ol {
  margin: 0;
  padding-left: 4.5mm;
}

.contents li {
  break-inside: avoid;
  margin: 0 0 1.6mm;
}

.chapter {
  padding-top: 2mm;
}

.chapter h1 {
  margin: 2mm 0 6mm;
  padding-bottom: 4mm;
  border-bottom: 2px solid var(--accent);
  color: var(--ink);
  font-size: 24pt;
  line-height: 1.12;
  letter-spacing: 0;
}

.chapter h2 {
  break-after: avoid;
  margin: 7mm 0 2.5mm;
  color: var(--ink);
  font-size: 15.2pt;
  line-height: 1.25;
  letter-spacing: 0;
}

.chapter h3 {
  break-after: avoid;
  margin: 5.5mm 0 2mm;
  color: var(--accent-strong);
  font-size: 12pt;
  line-height: 1.28;
  letter-spacing: 0;
}

.chapter p,
.chapter li {
  color: var(--ink-soft);
}

.chapter ul,
.chapter ol {
  margin: 0 0 3.5mm;
  padding-left: 7mm;
}

.chapter li {
  margin: 0 0 1.5mm;
}

.chapter-body > p:has(img) {
  text-align: center;
}

.table-wrap {
  overflow: visible;
  width: 100%;
  margin: 4mm 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
}

table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 8.15pt;
  line-height: 1.38;
}

thead {
  display: table-header-group;
}

tr {
  break-inside: avoid;
}

th,
td {
  padding: 2.2mm;
  border-bottom: 1px solid var(--line);
  text-align: left;
  vertical-align: top;
  overflow-wrap: anywhere;
}

th {
  background: var(--accent-soft);
  color: var(--ink);
  font-weight: 800;
}

td code {
  white-space: normal;
}

tbody tr:nth-child(even) td {
  background: var(--surface-muted);
}

tr:last-child td {
  border-bottom: 0;
}

blockquote {
  margin: 4mm 0;
  padding: 3.2mm 4mm;
  border-left: 3px solid var(--gold);
  border-radius: 0 7px 7px 0;
  background: var(--gold-soft);
  color: var(--ink-soft);
}

blockquote p:last-child {
  margin-bottom: 0;
}

@media screen {
  body {
    max-width: 210mm;
    margin: 0 auto;
    padding: 14mm;
    background: var(--page);
  }

  .cover,
  .contents,
  .chapter {
    margin-bottom: 10mm;
    padding: 12mm;
    border: 1px solid var(--line);
    border-radius: 8px;
    background-color: var(--surface);
    box-shadow: var(--shadow);
  }
}
"""


if __name__ == "__main__":
    raise SystemExit(main())
