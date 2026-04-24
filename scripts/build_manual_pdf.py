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
  <style>{MANUAL_STYLES}</style>
</head>
<body>
  <section class="cover">
    <p class="eyebrow">ChessRTK Manual</p>
    <h1>ChessRTK</h1>
    <p class="tagline">A practical manual for deterministic chess tooling, engine workflows, datasets, and publishing.</p>
    <img src="assets/banner/github/crtk-github-banner.png" alt="ChessRTK banner">
    <dl>
      <div><dt>Runtime</dt><dd>Java 17 command-line toolkit</dd></div>
      <div><dt>Source</dt><dd>Generated from the repository wiki</dd></div>
      <div><dt>Build</dt><dd><code>python3 scripts/build_manual_pdf.py</code></dd></div>
    </dl>
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
            "--no-pdf-header-footer",
            "--print-to-pdf-no-header",
            f"--print-to-pdf={pdf_path}",
            html_path.resolve().as_uri(),
        ],
        check=True,
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    """Parse command-line options."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--html", type=Path, default=DEFAULT_HTML, help="manual HTML output path")
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF, help="manual PDF output path")
    parser.add_argument("--html-only", action="store_true", help="write HTML without rendering the PDF")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    """Build the manual HTML and PDF."""
    args = parse_args(sys.argv[1:] if argv is None else argv)
    docs.main()
    pages = docs.all_pages()
    args.html.parent.mkdir(parents=True, exist_ok=True)
    args.html.write_text(render_manual_html(pages), encoding="utf-8")
    if not args.html_only:
        render_pdf(args.html, args.pdf)
        print(f"generated manual PDF at {args.pdf.relative_to(docs.ROOT)}")
    print(f"generated manual HTML at {args.html.relative_to(docs.ROOT)}")
    return 0


MANUAL_STYLES = r"""
@page {
  size: A4;
  margin: 17mm 15mm 19mm;
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  color-scheme: light;
}

body {
  margin: 0;
  background: #f4f7f3;
  color: #17221b;
  font: 10.5pt/1.55 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  print-color-adjust: exact;
  -webkit-print-color-adjust: exact;
}

a {
  color: #226b4a;
  text-decoration-thickness: 0.06em;
  text-underline-offset: 0.15em;
}

img {
  max-width: 100%;
  border-radius: 8px;
}

code {
  padding: 0.08em 0.28em;
  border: 1px solid #d9e5d9;
  border-radius: 5px;
  background: #eef5ef;
  color: #143923;
  font-size: 0.92em;
}

pre {
  overflow-wrap: break-word;
  white-space: pre-wrap;
  margin: 4mm 0;
  padding: 4mm;
  border-radius: 8px;
  background: #14221a;
  color: #edf7ef;
}

pre code {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
}

.cover,
.contents,
.chapter {
  page-break-after: always;
}

.cover {
  min-height: 247mm;
  padding: 23mm 18mm;
  border-radius: 8px;
  background:
    linear-gradient(125deg, rgba(216, 166, 58, 0.20), transparent 42%),
    linear-gradient(135deg, #0d1c14 0%, #153723 58%, #102018 100%);
  color: #ffffff;
}

.cover h1 {
  margin: 7mm 0 4mm;
  font-size: 46pt;
  line-height: 0.95;
  letter-spacing: 0;
}

.cover .tagline {
  max-width: 128mm;
  margin: 0 0 14mm;
  color: #dbe8de;
  font-size: 15pt;
}

.cover img {
  display: block;
  width: 132mm;
  margin: 0 0 14mm;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: #ffffff;
}

.cover dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4mm;
  margin: 0;
}

.cover dl div {
  min-height: 22mm;
  padding: 4mm;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
}

.cover dt {
  color: #d8a63a;
  font-weight: 800;
}

.cover dd {
  margin: 1mm 0 0;
  color: #eaf2ec;
}

.eyebrow,
.chapter-kicker {
  margin: 0;
  color: #b88412;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.contents {
  padding: 6mm 0 0;
}

.contents h1 {
  margin: 2mm 0 8mm;
  color: #123b2b;
  font-size: 27pt;
  letter-spacing: 0;
}

.contents section {
  break-inside: avoid;
  margin: 0 0 7mm;
  padding: 5mm;
  border: 1px solid #d5dfd6;
  border-radius: 8px;
  background: #fffefb;
}

.contents h2 {
  margin: 0 0 2mm;
  color: #123b2b;
  font-size: 13pt;
}

.contents ol {
  columns: 2;
  margin: 0;
  padding-left: 5mm;
}

.contents li {
  margin: 0 0 1.8mm;
}

.chapter {
  padding-top: 3mm;
}

.chapter h1 {
  margin: 2mm 0 7mm;
  padding-bottom: 4mm;
  border-bottom: 2px solid #d8a63a;
  color: #123b2b;
  font-size: 25pt;
  line-height: 1.1;
  letter-spacing: 0;
}

.chapter h2 {
  break-after: avoid;
  margin: 8mm 0 2.5mm;
  color: #123b2b;
  font-size: 16pt;
  line-height: 1.2;
}

.chapter h3 {
  break-after: avoid;
  margin: 6mm 0 2mm;
  color: #263c2b;
  font-size: 12.5pt;
}

.chapter p,
.chapter li {
  color: #303830;
}

.chapter ul,
.chapter ol {
  padding-left: 7mm;
}

.table-wrap {
  overflow: visible;
  margin: 4mm 0;
  border: 1px solid #d5dfd6;
  border-radius: 8px;
  background: #fffefb;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 8.7pt;
}

th,
td {
  padding: 2.5mm;
  border-bottom: 1px solid #d5dfd6;
  text-align: left;
  vertical-align: top;
}

th {
  background: #edf5ef;
  color: #123b2b;
}

tr:last-child td {
  border-bottom: 0;
}

blockquote {
  margin: 4mm 0;
  padding: 3mm 4mm;
  border-left: 4px solid #2e7d57;
  border-radius: 0 8px 8px 0;
  background: #e4f4ea;
}

@media screen {
  body {
    max-width: 210mm;
    margin: 0 auto;
    padding: 14mm;
  }

  .cover,
  .contents,
  .chapter {
    margin-bottom: 10mm;
    padding: 12mm;
    border: 1px solid #d5dfd6;
    border-radius: 8px;
    background-color: #fffefb;
    box-shadow: 0 18px 45px rgba(24, 37, 28, 0.10);
  }

  .cover {
    background:
      linear-gradient(125deg, rgba(216, 166, 58, 0.20), transparent 42%),
      linear-gradient(135deg, #0d1c14 0%, #153723 58%, #102018 100%);
  }
}
"""


if __name__ == "__main__":
    raise SystemExit(main())
