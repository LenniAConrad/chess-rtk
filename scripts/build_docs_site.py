#!/usr/bin/env python3
"""Build the static ChessRTK documentation site from wiki Markdown files.

The generated site mirrors a modern open-source project homepage: a sticky top
navigation bar, a marketing hero with feature cards on the landing page, a
left documentation sidebar with on-page tables of contents, and a dark footer.
It ships a light theme with a working dark-mode toggle (persisted in
``localStorage`` and seeded from the OS preference).

Run with::

    python3 scripts/build_docs_site.py
"""

from __future__ import annotations

import html
import json
import re
import shutil
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WIKI = ROOT / "wiki"
DOCS = ROOT / "docs"

REPO_URL = "https://github.com/LenniAConrad/chess-rtk"
ISSUES_URL = f"{REPO_URL}/issues"
SITE_NAME = "ChessRTK"
TAGLINE = "The deterministic chess research toolkit"


@dataclass(frozen=True)
class Page:
    """One generated documentation page."""

    source: str
    title: str
    href: str
    category: str


# Documentation information architecture. Source files that are not listed here
# are appended to a trailing "Reference" group so nothing is silently dropped.
NAVIGATION: list[tuple[str, list[tuple[str, str]]]] = [
    (
        "Start Here",
        [
            ("Home.md", "Overview"),
            ("getting-started.md", "Getting Started"),
            ("build-and-install.md", "Build & Install"),
            ("use-cases.md", "Use Cases"),
            ("faq.md", "FAQ"),
            ("troubleshooting.md", "Troubleshooting"),
        ],
    ),
    (
        "Commands",
        [
            ("command-cheatsheet.md", "Cheatsheet"),
            ("command-reference.md", "Command Reference"),
            ("cli-command-guide.md", "Command Guide"),
            ("example-commands.md", "Example Commands"),
            ("configuration.md", "Configuration"),
            ("outputs-and-logs.md", "Outputs & Logs"),
        ],
    ),
    (
        "Desktop Workbench",
        [
            ("workbench.md", "Workbench"),
            ("workbench-design-guide.md", "Design Guide"),
        ],
    ),
    (
        "Engines & Models",
        [
            ("in-house-engine.md", "Built-in Engine"),
            ("lc0.md", "LC0 Networks"),
            ("gpu.md", "GPU Acceleration"),
            ("t5.md", "T5 Text"),
        ],
    ),
    (
        "Workflows",
        [
            ("mining.md", "Puzzle Mining"),
            ("filter-dsl.md", "Filter DSL"),
            ("datasets.md", "ML Datasets"),
            ("review-to-study.md", "Review To Study"),
            ("piece-tags.md", "Tagging"),
            ("tag-reference.md", "Tag Reference"),
            ("book-publishing.md", "Book Publishing"),
            ("ai-agents.md", "AI Agents & Automation"),
        ],
    ),
    (
        "Project",
        [
            ("architecture.md", "Architecture"),
            ("quality-and-testing.md", "Quality & Testing"),
            ("development-notes.md", "Development Notes"),
            ("releasing.md", "Releasing"),
            ("glossary.md", "Glossary"),
            ("support.md", "Support"),
            ("README.md", "Wiki Index"),
        ],
    ),
]


# Marketing hero copy shown on the landing page above the rendered Home.md body.
HERO = {
    "eyebrow": "Deterministic chess tooling",
    "title": "ChessRTK",
    "subtitle": (
        "One Java 17 chess core behind a scriptable CLI, a desktop Workbench, "
        "engine analysis, puzzle mining, datasets, and native PDF publishing."
    ),
    "primary": ("Get started", "getting-started.html"),
    "secondary": ("View on GitHub", REPO_URL),
    "meta": "Java 17 / GPL-3.0 / no Maven or Gradle",
}


# Inline stroke icons (24x24, currentColor) used on the landing feature cards.
ICONS: dict[str, str] = {
    "core": '<rect x="7" y="7" width="10" height="10" rx="2"/><path d="M10 7V4M14 7V4M10 20v-3M14 20v-3M7 10H4M7 14H4M20 10h-3M20 14h-3"/>',
    "engine": '<path d="M13 2 4 14h7l-1 8 9-12h-7l1-8Z"/>',
    "nn": '<circle cx="5" cy="6" r="2"/><circle cx="5" cy="18" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="6" r="2"/><circle cx="19" cy="18" r="2"/><path d="M7 6.6 10.4 11M7 17.4 10.4 13M14 11l3.4-4.4M14 13l3.4 4.4"/>',
    "gpu": '<path d="M12 3 3 8l9 5 9-5-9-5Z"/><path d="M3 12l9 5 9-5M3 16l9 5 9-5"/>',
    "mining": '<circle cx="11" cy="11" r="6"/><path d="m20 20-3.4-3.4"/>',
    "tags": '<path d="M20.6 13.4 11 3.8a2 2 0 0 0-1.4-.6H4a1 1 0 0 0-1 1v5.6a2 2 0 0 0 .6 1.4l9.6 9.6a2 2 0 0 0 2.8 0l4.6-4.6a2 2 0 0 0 0-2.8Z"/><circle cx="7.5" cy="7.5" r="1.3"/>',
    "datasets": '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"/>',
    "book": '<path d="M4 5a2 2 0 0 1 2-2h12v16H6a2 2 0 0 0-2 2V5Z"/><path d="M4 19a2 2 0 0 1 2-2h12"/>',
    "workbench": '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M9 9v11"/>',
    "agents": '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="m7 9 3 3-3 3M13 15h4"/>',
}


# Landing-page feature cards advertising the toolkit surface.
FEATURES: list[dict[str, str]] = [
    {
        "icon": "core",
        "title": "One shared chess core",
        "body": "Legal moves, FEN, SAN, UCI, Chess960, perft, tags, diagrams, and books all use one implementation.",
        "href": "architecture.html",
        "link": "Architecture",
    },
    {
        "icon": "agents",
        "title": "Scriptable CLI",
        "body": "Use noun-then-verb commands with stable text, JSON, and JSONL output for reproducible automation.",
        "href": "command-cheatsheet.html",
        "link": "Commands",
    },
    {
        "icon": "workbench",
        "title": "Desktop Workbench",
        "body": "Open the same core in a Swing app for board work, command forms, datasets, publishing previews, and play.",
        "href": "workbench.html",
        "link": "Workbench",
    },
    {
        "icon": "book",
        "title": "Research outputs",
        "body": "Mine puzzles, export tensors, drive engines, and render PDF books without external build or typesetting stacks.",
        "href": "book-publishing.html",
        "link": "Publishing",
    },
]


# Top navigation links (label, href). External links open in a new tab.
NAV_LINKS: list[tuple[str, str]] = [
    ("Get started", "getting-started.html"),
    ("Commands", "command-reference.html"),
    ("Workbench", "workbench.html"),
    ("Docs", "wiki-index.html"),
    ("Manual", "chessrtk-manual.pdf"),
]


# Dark footer columns (heading, [(label, href), ...]).
FOOTER_COLUMNS: list[tuple[str, list[tuple[str, str]]]] = [
    (
        "Documentation",
        [
            ("Getting Started", "getting-started.html"),
            ("Command Reference", "command-reference.html"),
            ("Configuration", "configuration.html"),
            ("Troubleshooting", "troubleshooting.html"),
            ("Manual (PDF)", "chessrtk-manual.pdf"),
        ],
    ),
    (
        "Workflows",
        [
            ("Puzzle Mining", "mining.html"),
            ("ML Datasets", "datasets.html"),
            ("Tagging", "piece-tags.html"),
            ("Book Publishing", "book-publishing.html"),
            ("AI Agents", "ai-agents.html"),
        ],
    ),
    (
        "Project",
        [
            ("Architecture", "architecture.html"),
            ("Quality & Testing", "quality-and-testing.html"),
            ("GitHub", REPO_URL),
            ("Issues", ISSUES_URL),
        ],
    ),
]


def slug(text: str) -> str:
    """Return a stable HTML id slug."""
    value = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return value or "section"


def page_href(source_name: str) -> str:
    """Return the generated HTML filename for a wiki source."""
    if source_name == "Home.md":
        return "index.html"
    if source_name == "README.md":
        return "wiki-index.html"
    return f"{Path(source_name).stem}.html"


def all_pages() -> list[Page]:
    """Return pages in navigation order followed by uncategorized pages."""
    seen: set[str] = set()
    pages: list[Page] = []
    for category, entries in NAVIGATION:
        for source, title in entries:
            if (WIKI / source).exists():
                pages.append(Page(source, title, page_href(source), category))
                seen.add(source)
    for path in sorted(WIKI.glob("*.md")):
        if path.name not in seen:
            title = title_from_markdown(path)
            pages.append(Page(path.name, title, page_href(path.name), "Reference"))
    return pages


def title_from_markdown(path: Path) -> str:
    """Extract a Markdown H1 title from a file."""
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return path.stem.replace("-", " ").title()


def convert_href(target: str) -> str:
    """Convert wiki-relative links to generated site links."""
    if "://" in target or target.startswith("#") or target.startswith("mailto:"):
        return target
    file_part, anchor = (target.split("#", 1) + [""])[:2] if "#" in target else (target, "")
    suffix = f"#{anchor}" if anchor else ""
    if file_part.endswith(".md"):
        return page_href(Path(file_part).name) + suffix
    if "/" not in file_part and (WIKI / f"{file_part}.md").exists():
        return page_href(f"{file_part}.md") + suffix
    if file_part.startswith("../assets/"):
        return "assets/" + file_part[len("../assets/") :] + suffix
    if file_part.startswith("assets/"):
        return file_part + suffix
    if file_part.startswith("../"):
        return file_part[3:] + suffix
    return file_part + suffix


def inline_markdown(text: str) -> str:
    """Render the inline Markdown subset used by the wiki."""
    escaped = html.escape(text)
    code_spans: list[str] = []

    def code_repl(match: re.Match[str]) -> str:
        code_spans.append(f"<code>{match.group(1)}</code>")
        return f"\u0000CODE{len(code_spans) - 1}\u0000"

    def image_repl(match: re.Match[str]) -> str:
        alt = match.group(1)
        href = convert_href(html.unescape(match.group(2)))
        return f'<img src="{html.escape(href)}" alt="{alt}" loading="lazy">'

    def link_repl(match: re.Match[str]) -> str:
        label = match.group(1)
        href = convert_href(html.unescape(match.group(2)))
        return f'<a href="{html.escape(href)}">{label}</a>'

    escaped = re.sub(r"`([^`]+)`", code_repl, escaped)
    escaped = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", image_repl, escaped)
    escaped = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", link_repl, escaped)
    escaped = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", escaped)
    escaped = re.sub(r"\*([^*]+)\*", r"<em>\1</em>", escaped)
    for index, code in enumerate(code_spans):
        escaped = escaped.replace(f"\u0000CODE{index}\u0000", code)
    return escaped


def render_table(lines: list[str]) -> str:
    """Render a simple GitHub-flavored Markdown table."""
    rows = []
    for line in lines:
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        rows.append(cells)
    header = rows[0]
    body = rows[2:]
    out = ["<div class=\"table-wrap\"><table>", "<thead><tr>"]
    for cell in header:
        out.append(f"<th>{inline_markdown(cell)}</th>")
    out.append("</tr></thead><tbody>")
    for row in body:
        out.append("<tr>")
        for cell in row:
            cls = ' class="numeric"' if cell.startswith(":") or re.match(r"^[0-9., +-]+$", cell) else ""
            out.append(f"<td{cls}>{inline_markdown(cell)}</td>")
        out.append("</tr>")
    out.append("</tbody></table></div>")
    return "\n".join(out)


def render_markdown(text: str) -> tuple[str, list[tuple[int, str, str]], str]:
    """Render Markdown into HTML, TOC entries, and plain search text."""
    lines = text.splitlines()
    output: list[str] = []
    toc: list[tuple[int, str, str]] = []
    search_text: list[str] = []
    paragraph: list[str] = []
    list_type: str | None = None
    in_code = False
    code_lang = ""
    code_lines: list[str] = []
    i = 0

    def flush_paragraph() -> None:
        if paragraph:
            output.append(f"<p>{inline_markdown(' '.join(paragraph))}</p>")
            paragraph.clear()

    def close_list() -> None:
        nonlocal list_type
        if list_type:
            output.append(f"</{list_type}>")
            list_type = None

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if in_code:
            if stripped.startswith("```"):
                language = f" language-{html.escape(code_lang)}" if code_lang else ""
                code = html.escape("\n".join(code_lines))
                output.append(f'<pre><code class="{language.strip()}">{code}</code></pre>')
                code_lines.clear()
                in_code = False
                code_lang = ""
            else:
                code_lines.append(line)
            i += 1
            continue

        if stripped.startswith("```"):
            flush_paragraph()
            close_list()
            in_code = True
            code_lang = stripped[3:].strip()
            i += 1
            continue

        if not stripped:
            flush_paragraph()
            close_list()
            i += 1
            continue

        if "|" in stripped and i + 1 < len(lines) and re.match(r"^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$", lines[i + 1]):
            flush_paragraph()
            close_list()
            table_lines = [line, lines[i + 1]]
            i += 2
            while i < len(lines) and "|" in lines[i] and lines[i].strip():
                table_lines.append(lines[i])
                i += 1
            output.append(render_table(table_lines))
            continue

        if stripped.startswith(">"):
            flush_paragraph()
            close_list()
            quote_lines: list[str] = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                quote = re.sub(r"^>\s?", "", lines[i].strip())
                if quote:
                    quote_lines.append(quote)
                    search_text.append(quote)
                i += 1
            output.append(f"<blockquote><p>{inline_markdown(' '.join(quote_lines))}</p></blockquote>")
            continue

        heading = re.match(r"^(#{1,6})\s+(.+)$", stripped)
        if heading:
            flush_paragraph()
            close_list()
            level = len(heading.group(1))
            title = heading.group(2).strip()
            ident = slug(re.sub(r"`([^`]+)`", r"\1", title))
            toc.append((level, title, ident))
            search_text.append(title)
            output.append(f'<h{level} id="{ident}">{inline_markdown(title)}</h{level}>')
            i += 1
            continue

        bullet = re.match(r"^[-*]\s+(.+)$", stripped)
        if bullet:
            flush_paragraph()
            if list_type != "ul":
                close_list()
                output.append("<ul>")
                list_type = "ul"
            item = bullet.group(1)
            search_text.append(item)
            output.append(f"<li>{inline_markdown(item)}</li>")
            i += 1
            continue

        ordered = re.match(r"^\d+\.\s+(.+)$", stripped)
        if ordered:
            flush_paragraph()
            if list_type != "ol":
                close_list()
                output.append("<ol>")
                list_type = "ol"
            item = ordered.group(1)
            search_text.append(item)
            output.append(f"<li>{inline_markdown(item)}</li>")
            i += 1
            continue

        close_list()
        paragraph.append(stripped)
        search_text.append(stripped)
        i += 1

    flush_paragraph()
    close_list()
    if in_code:
        code = html.escape("\n".join(code_lines))
        output.append(f"<pre><code>{code}</code></pre>")
    return "\n".join(output), toc, " ".join(search_text)


def sidebar_html(pages: list[Page], active: str) -> str:
    """Render the documentation sidebar navigation."""
    by_source = {page.source: page for page in pages}
    out = []
    for category, entries in NAVIGATION:
        links = [by_source[source] for source, _ in entries if source in by_source]
        if not links:
            continue
        out.append(f'<section class="nav-section"><h2>{html.escape(category)}</h2>')
        for page in links:
            cls = "active" if page.href == active else ""
            out.append(
                f'<a class="{cls}" href="{html.escape(page.href)}" '
                f'data-title="{html.escape(page.title.lower())}">{html.escape(page.title)}</a>'
            )
        out.append("</section>")
    return "\n".join(out)


def toc_html(toc: list[tuple[int, str, str]]) -> str:
    """Render an on-page table of contents."""
    links = [(level, title, ident) for level, title, ident in toc if level in (2, 3)]
    if not links:
        return '<p class="toc-empty">No sections on this page.</p>'
    out = ['<h2>On this page</h2>']
    for level, title, ident in links[:20]:
        cls = "toc-h3" if level == 3 else "toc-h2"
        out.append(f'<a class="{cls}" href="#{html.escape(ident)}">{html.escape(strip_markup(title))}</a>')
    return "\n".join(out)


def strip_markup(value: str) -> str:
    """Remove small Markdown markers from labels."""
    value = re.sub(r"`([^`]+)`", r"\1", value)
    value = value.replace("*", "")
    return value


def markdown_for_page(page: Page) -> str:
    """Return Markdown prepared for the generated website."""
    markdown = (WIKI / page.source).read_text(encoding="utf-8")
    if page.source == "Home.md":
        # The marketing hero replaces the leading H1 + banner image.
        markdown = re.sub(r"^# [^\n]+\n+", "", markdown, count=1)
        markdown = re.sub(r"^!\[[^\]]*\]\([^)]+\)\n+", "", markdown, count=1)
    return markdown


def icon_svg(name: str) -> str:
    """Return a feature-card SVG icon by name."""
    body = ICONS.get(name, ICONS["core"])
    return (
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" '
        'stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" '
        f'aria-hidden="true">{body}</svg>'
    )


def hero_html() -> str:
    """Render the landing-page hero and feature cards."""
    primary_label, primary_href = HERO["primary"]
    secondary_label, secondary_href = HERO["secondary"]
    cards = []
    for feature in FEATURES:
        cards.append(
            f"""<a class="feature-card" href="{html.escape(feature['href'])}">
        <span class="feature-icon">{icon_svg(feature['icon'])}</span>
        <h3>{html.escape(feature['title'])}</h3>
        <p>{html.escape(feature['body'])}</p>
        <span class="feature-link">{html.escape(feature['link'])} <span aria-hidden="true">&rarr;</span></span>
      </a>"""
        )
    cards_html = "\n      ".join(cards)
    return f"""
<section class="hero">
  <div class="hero-inner">
    <div class="hero-text">
      <p class="eyebrow">{html.escape(HERO['eyebrow'])}</p>
      <h1>{html.escape(HERO['title'])}</h1>
      <p class="hero-sub">{html.escape(HERO['subtitle'])}</p>
      <div class="hero-cta">
        <a class="btn btn-primary" href="{html.escape(primary_href)}">{html.escape(primary_label)}</a>
        <a class="btn btn-ghost" href="{html.escape(secondary_href)}">{html.escape(secondary_label)}</a>
      </div>
      <p class="hero-meta">{html.escape(HERO['meta'])}</p>
    </div>
    <div class="hero-art">
      <img src="assets/logo/crtk-logo.svg" alt="ChessRTK logo" width="320" height="320">
    </div>
  </div>
</section>
<section class="features">
  <div class="features-inner">
    <p class="section-kicker">Start with the surface you need</p>
    <div class="feature-grid">
      {cards_html}
    </div>
  </div>
</section>
"""


def topnav_html(active: str) -> str:
    """Render the sticky top navigation bar."""
    links = []
    for label, href in NAV_LINKS:
        cls = "active" if href == active else ""
        links.append(f'<a class="{cls}" href="{html.escape(href)}">{html.escape(label)}</a>')
    links_html = "\n        ".join(links)
    return f"""
  <header class="topnav">
    <div class="topnav-inner">
      <a class="topnav-brand" href="index.html">
        <img src="assets/logo/crtk-logo.svg" alt="" width="32" height="32">
        <span>ChessRTK</span>
      </a>
      <button class="nav-toggle" type="button" aria-label="Toggle navigation" aria-expanded="false">
        <span></span><span></span><span></span>
      </button>
      <nav class="topnav-links">
        {links_html}
        <a class="topnav-gh" href="{html.escape(REPO_URL)}" target="_blank" rel="noopener">GitHub</a>
        <button class="theme-toggle" type="button" aria-label="Toggle dark mode" title="Toggle dark mode">
          <svg class="icon-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4.2"/><path d="M12 2v2.4M12 19.6V22M2 12h2.4M19.6 12H22M4.9 4.9l1.7 1.7M17.4 17.4l1.7 1.7M19.1 4.9l-1.7 1.7M6.6 17.4l-1.7 1.7"/></svg>
          <svg class="icon-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4 7 7 0 1 0 20 14.5Z"/></svg>
        </button>
      </nav>
    </div>
  </header>
"""


def footer_html() -> str:
    """Render the dark site footer."""
    columns = []
    for heading, entries in FOOTER_COLUMNS:
        items = []
        for label, href in entries:
            external = ' target="_blank" rel="noopener"' if "://" in href else ""
            items.append(f'<li><a href="{html.escape(href)}"{external}>{html.escape(label)}</a></li>')
        items_html = "\n          ".join(items)
        columns.append(
            f'<div class="footer-col"><h3>{html.escape(heading)}</h3><ul>\n          {items_html}\n        </ul></div>'
        )
    columns_html = "\n        ".join(columns)
    return f"""
  <footer class="site-footer">
    <div class="footer-inner">
      <div class="footer-brand">
        <a class="footer-logo" href="index.html">
          <img src="assets/logo/crtk-logo.svg" alt="" width="40" height="40">
          <span>ChessRTK</span>
        </a>
        <p>{html.escape(TAGLINE)}. Free, open-source, and built on one shared Java chess core.</p>
        <p class="footer-license">Licensed under GPL-3.0-only.</p>
      </div>
      <div class="footer-cols">
        {columns_html}
      </div>
    </div>
    <div class="footer-bottom">
      <span>&copy; ChessRTK contributors &middot; GPL-3.0-only</span>
      <span>Documentation generated from the repository <a href="wiki-index.html">wiki</a>.</span>
    </div>
  </footer>
"""


def render_page(page: Page, pages: list[Page]) -> tuple[str, str]:
    """Render one full HTML page."""
    markdown = markdown_for_page(page)
    body, toc, search_text = render_markdown(markdown)
    page_title = title_from_markdown(WIKI / page.source)
    is_home = page.href == "index.html"

    head = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{html.escape(page_title)} | {SITE_NAME}</title>
  <meta name="description" content="{html.escape(TAGLINE)} — {html.escape(page_title)}.">
  <link rel="icon" href="assets/logo/crtk-logo.svg" type="image/svg+xml">
  <link rel="stylesheet" href="styles.css">
  <script>
    (function () {{
      var key = "crtk-theme";
      var isTheme = function (value) {{ return value === "light" || value === "dark"; }};
      var urlTheme = "";
      var stored = "";
      try {{ urlTheme = new URLSearchParams(window.location.search).get("theme"); }} catch (e) {{}}
      try {{ stored = localStorage.getItem(key); }} catch (e) {{}}
      var theme = isTheme(urlTheme) ? urlTheme : (isTheme(stored) ? stored : "");
      if (!theme) {{
        theme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
      }}
      if (isTheme(urlTheme)) {{
        try {{ localStorage.setItem(key, urlTheme); }} catch (e) {{}}
      }}
      document.documentElement.setAttribute("data-theme", theme);
    }})();
  </script>
  <script defer src="search-index.js"></script>
  <script defer src="site.js"></script>
</head>
<body class="{'is-landing' if is_home else 'is-doc'}">
{topnav_html(page.href)}"""

    if is_home:
        main = f"""{hero_html()}
  <div class="landing-body">
    <article class="content">
      {body}
    </article>
  </div>
"""
    else:
        main = f"""
  <div class="doc-shell">
    <aside class="sidebar" id="sidebar">
      <label class="search-label" for="doc-search">Search the docs</label>
      <input id="doc-search" class="search" type="search" placeholder="Search pages..." autocomplete="off">
      <div id="search-results" class="search-results" hidden></div>
      <nav class="nav">{sidebar_html(pages, page.href)}</nav>
    </aside>
    <main class="doc-main">
      <article class="content">
        <p class="content-kicker">{html.escape(page.category)}</p>
        {body}
      </article>
      <aside class="toc">{toc_html(toc)}</aside>
    </main>
  </div>
"""

    html_page = head + main + footer_html() + "\n</body>\n</html>\n"
    return html_page, search_text


def copy_assets() -> None:
    """Copy documentation assets into the generated site."""
    assets = DOCS / "assets"
    if assets.exists():
        shutil.rmtree(assets)
    (assets / "diagrams").mkdir(parents=True, exist_ok=True)
    (assets / "banner" / "github").mkdir(parents=True, exist_ok=True)
    (assets / "screenshots").mkdir(parents=True, exist_ok=True)
    (assets / "boards").mkdir(parents=True, exist_ok=True)
    (assets / "logo").mkdir(parents=True, exist_ok=True)
    (assets / "logo" / "otis").mkdir(parents=True, exist_ok=True)
    for path in (ROOT / "assets" / "diagrams").glob("*.png"):
        shutil.copy2(path, assets / "diagrams" / path.name)
    for path in (ROOT / "assets" / "screenshots").glob("*.png"):
        shutil.copy2(path, assets / "screenshots" / path.name)
    for path in (ROOT / "assets" / "boards").glob("*.svg"):
        shutil.copy2(path, assets / "boards" / path.name)
    banner = ROOT / "assets" / "banner" / "github" / "crtk-github-banner.png"
    if banner.exists():
        shutil.copy2(banner, assets / "banner" / "github" / banner.name)
    logo_src = ROOT / "assets" / "logo" / "app"
    for name, dest in (
        ("crtk-chemical-board.svg", "crtk-logo.svg"),
        ("crtk-chemical-board.png", "crtk-logo.png"),
        ("crtk-chemical-board.ico", "crtk-logo.ico"),
    ):
        src = logo_src / name
        if src.exists():
            shutil.copy2(src, assets / "logo" / dest)
    otis_src = ROOT / "assets" / "logo" / "otis"
    for path in otis_src.glob("*.svg"):
        shutil.copy2(path, assets / "logo" / "otis" / path.name)
    for name, dest in (
        ("crtk-otis-lattice.svg", "crtk-otis.svg"),
        ("crtk-otis-lattice.png", "crtk-otis.png"),
    ):
        src = otis_src / name
        if src.exists():
            shutil.copy2(src, assets / "logo" / dest)


def remove_stale_pages(pages: list[Page]) -> None:
    """Delete generated HTML pages that no longer have a wiki source."""
    keep = {page.href for page in pages}
    keep |= {"manual.html"}  # produced by build_manual_pdf.py
    for path in DOCS.glob("*.html"):
        if path.name not in keep:
            path.unlink()


def write_static_files() -> None:
    """Write CSS and JavaScript assets."""
    (DOCS / "styles.css").write_text(STYLES, encoding="utf-8")
    (DOCS / "site.js").write_text(SCRIPT, encoding="utf-8")


def main() -> None:
    """Build all documentation pages."""
    DOCS.mkdir(exist_ok=True)
    copy_assets()
    write_static_files()
    pages = all_pages()
    remove_stale_pages(pages)
    search_index = []
    for page in pages:
        rendered, search_text = render_page(page, pages)
        (DOCS / page.href).write_text(rendered, encoding="utf-8")
        search_index.append(
            {
                "title": page.title,
                "href": page.href,
                "category": page.category,
                "text": re.sub(r"\s+", " ", search_text).strip()[:8000],
            }
        )
    (DOCS / "search-index.js").write_text(
        "window.CRTK_SEARCH_INDEX = " + json.dumps(search_index, indent=2) + ";\n",
        encoding="utf-8",
    )
    print(f"generated {len(pages)} pages in {DOCS.relative_to(ROOT)}")


STYLES = r"""
/* ChessRTK documentation site — teal brand, light theme with dark-mode toggle. */
:root {
  --bg: #fbfdfd;
  --bg-alt: #f4f7f8;
  --bg-soft: #edf3f5;
  --surface: #ffffff;
  --surface-2: #f0f5f6;
  --text: #17242b;
  --heading: #102f3a;
  --muted: #5c7078;
  --border: #dbe7eb;
  --border-soft: #e9f0f2;
  --accent: #1286a0;
  --accent-strong: #0d697d;
  --accent-soft: #e5f4f7;
  --accent-warm: #a87512;
  --accent-warm-soft: #fff3d8;
  --deep: #102a34;
  --deep-2: #0c2028;
  --code-bg: #102832;
  --code-text: #d6eef4;
  --shadow-sm: 0 1px 2px rgba(16, 47, 58, 0.04), 0 6px 18px rgba(16, 47, 58, 0.055);
  --shadow: 0 16px 44px rgba(16, 47, 58, 0.10);
  --radius: 8px;
  --radius-sm: 6px;
  --maxw: 1180px;
  color-scheme: light;
}

html[data-theme="dark"] {
  --bg: #0b1115;
  --bg-alt: #101a20;
  --bg-soft: #142229;
  --surface: #132228;
  --surface-2: #182b33;
  --text: #dfecef;
  --heading: #edf7f9;
  --muted: #a2b4bb;
  --border: #263941;
  --border-soft: #1b2b32;
  --accent: #4dbbd8;
  --accent-strong: #83d8ed;
  --accent-soft: #112f38;
  --accent-warm: #e6bd62;
  --accent-warm-soft: #302919;
  --deep: #091116;
  --deep-2: #070d11;
  --code-bg: #081318;
  --code-text: #d6eef4;
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.30), 0 8px 22px rgba(0, 0, 0, 0.18);
  --shadow: 0 18px 50px rgba(0, 0, 0, 0.36);
  color-scheme: dark;
}

*, *::before, *::after { box-sizing: border-box; }

html { scroll-behavior: smooth; scroll-padding-top: 84px; }

body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font: 16px/1.65 "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
}

a { color: var(--accent-strong); text-decoration: none; }
a:hover { text-decoration: underline; text-underline-offset: 0.18em; }

img { max-width: 100%; }

h1, h2, h3, h4 { color: var(--heading); }

/* ---------- Top navigation ---------- */
.topnav {
  position: sticky;
  top: 0;
  z-index: 50;
  background: color-mix(in srgb, var(--bg) 88%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border);
}

.topnav-inner {
  display: flex;
  align-items: center;
  gap: 18px;
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 12px 24px;
}

.topnav-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-right: auto;
  color: var(--heading);
  font-weight: 800;
  font-size: 1.15rem;
  letter-spacing: 0;
}
.topnav-brand:hover { text-decoration: none; }
.topnav-brand img { display: block; border-radius: 7px; }

.topnav-links {
  display: flex;
  align-items: center;
  gap: 6px;
}

.topnav-links a {
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  color: var(--text);
  font-weight: 600;
  font-size: 0.95rem;
}
.topnav-links a:hover { background: var(--surface-2); text-decoration: none; }
.topnav-links a.active { color: var(--accent-strong); }

.topnav-gh {
  border: 1px solid var(--border);
}

.theme-toggle {
  display: inline-grid;
  place-items: center;
  width: 38px;
  height: 38px;
  margin-left: 4px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  cursor: pointer;
}
.theme-toggle:hover { border-color: var(--accent); color: var(--accent-strong); }
.theme-toggle svg { width: 18px; height: 18px; }
.theme-toggle .icon-moon { display: none; }
html[data-theme="dark"] .theme-toggle .icon-sun { display: none; }
html[data-theme="dark"] .theme-toggle .icon-moon { display: block; }

.nav-toggle {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 40px;
  height: 38px;
  padding: 0 9px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  cursor: pointer;
}
.nav-toggle span { display: block; height: 2px; background: var(--text); border-radius: 2px; }

/* ---------- Hero ---------- */
.hero {
  position: relative;
  overflow: hidden;
  background: var(--bg);
  border-bottom: 1px solid var(--border-soft);
}

.hero-inner {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.85fr);
  align-items: center;
  gap: 40px;
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 64px 24px 58px;
}

.eyebrow {
  margin: 0 0 14px;
  color: var(--accent-strong);
  font-weight: 700;
  font-size: 0.85rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.hero-text h1 {
  margin: 0 0 16px;
  font-size: 4rem;
  line-height: 1.04;
  letter-spacing: 0;
  color: var(--heading);
}

.hero-sub {
  max-width: 34em;
  margin: 0 0 26px;
  color: var(--muted);
  font-size: 1.1rem;
  line-height: 1.6;
}

.hero-cta { display: flex; flex-wrap: wrap; gap: 14px; margin-bottom: 22px; }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  padding: 0 24px;
  border-radius: var(--radius-sm);
  font-weight: 700;
  font-size: 1rem;
  border: 1px solid transparent;
  transition: transform 0.12s ease, box-shadow 0.12s ease, background 0.12s ease;
}
.btn:hover { text-decoration: none; transform: translateY(-1px); }

.btn-primary {
  background: linear-gradient(180deg, color-mix(in srgb, var(--accent) 90%, #fff), var(--accent-strong));
  color: #fff;
  box-shadow: 0 8px 18px color-mix(in srgb, var(--accent) 24%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.22);
}
.btn-primary:hover {
  background: linear-gradient(180deg, var(--accent), var(--accent-strong));
  box-shadow: 0 10px 22px color-mix(in srgb, var(--accent) 30%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.22);
}

.btn-ghost { background: var(--surface); color: var(--accent-strong); border-color: var(--border); box-shadow: var(--shadow-sm); }
.btn-ghost:hover { border-color: var(--accent); background: var(--accent-soft); }

.hero-meta { margin: 0; color: var(--muted); font-size: 0.9rem; }

.hero-art { display: grid; place-items: center; }
.hero-art img {
  width: min(280px, 72%);
  height: auto;
  filter: drop-shadow(0 16px 28px rgba(12, 58, 76, 0.12));
}

/* ---------- Feature section ---------- */
.features {
  background: var(--bg-alt);
  border-bottom: 1px solid var(--border-soft);
}
.features-inner { max-width: var(--maxw); margin: 0 auto; padding: 34px 24px 44px; }

.section-kicker {
  margin: 0 0 8px;
  color: var(--accent-strong);
  font-weight: 700;
  font-size: 0.85rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  text-align: center;
}
.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  display: block;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
  box-shadow: none;
  transition: border-color 0.14s ease, background 0.14s ease;
}
.feature-card:hover {
  text-decoration: none;
  border-color: color-mix(in srgb, var(--accent) 38%, var(--border));
  background: color-mix(in srgb, var(--surface) 86%, var(--accent-soft));
}

.feature-icon {
  display: inline-grid;
  place-items: center;
  width: 38px;
  height: 38px;
  margin-bottom: 14px;
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--accent-strong);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 14%, transparent);
}
.feature-card:nth-child(3n+2) .feature-icon {
  background: var(--accent-warm-soft);
  color: var(--accent-warm);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent-warm) 18%, transparent);
}
.feature-icon svg { width: 21px; height: 21px; }

.feature-card h3 { margin: 0 0 7px; font-size: 1.05rem; letter-spacing: 0; }
.feature-card p { margin: 0 0 14px; color: var(--muted); font-size: 0.92rem; line-height: 1.55; }
.feature-link { color: var(--accent-strong); font-weight: 700; font-size: 0.92rem; }

/* ---------- Landing body ---------- */
.landing-body { background: var(--bg); }
.landing-body .content {
  max-width: 860px;
  margin: 0 auto;
  padding: 54px 24px 72px;
  background: transparent;
  border: 0;
  box-shadow: none;
}

/* ---------- Documentation layout ---------- */
.doc-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  max-width: 1320px;
  margin: 0 auto;
}

.sidebar {
  position: sticky;
  top: 65px;
  align-self: start;
  height: calc(100vh - 65px);
  overflow-y: auto;
  padding: 26px 18px 40px;
  border-right: 1px solid var(--border);
  background: var(--bg-alt);
}

.search-label {
  display: block;
  margin: 0 0 7px;
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.search {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  font: inherit;
}
.search:focus { outline: 2px solid var(--accent-soft); border-color: var(--accent); }

.search-results {
  margin-top: 8px;
  padding: 6px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  box-shadow: var(--shadow);
}
.search-results a { display: block; padding: 8px 9px; border-radius: 7px; color: var(--text); }
.search-results a:hover { background: var(--surface-2); text-decoration: none; }
.search-results small { display: block; color: var(--muted); font-size: 0.74rem; }

.nav-section { margin-top: 22px; }
.nav-section h2 {
  margin: 0 0 6px;
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.nav-section a {
  display: block;
  padding: 7px 11px;
  border-radius: var(--radius-sm);
  color: var(--text);
  font-size: 0.94rem;
}
.nav-section a:hover { background: var(--surface-2); text-decoration: none; }
.nav-section a.active {
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-weight: 700;
  box-shadow: inset 3px 0 0 var(--accent);
}

.doc-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 230px;
  gap: 40px;
  padding: 40px 40px 72px;
}

.content { min-width: 0; }
.content-kicker {
  margin: 0 0 4px;
  color: var(--accent-strong);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.content h1 {
  margin: 0 0 0.6em;
  font-size: 2.55rem;
  line-height: 1.1;
  letter-spacing: 0;
}
.content h2 {
  margin: 2em 0 0.7em;
  padding-top: 0.7em;
  border-top: 1px solid var(--border-soft);
  font-size: 1.5rem;
  letter-spacing: 0;
}
.content h3 { margin: 1.6em 0 0.5em; font-size: 1.18rem; }
.content p, .content li { color: var(--text); }
.content p { margin: 0.9rem 0; }
.content ul, .content ol { padding-left: 1.35rem; }
.content li + li { margin-top: 0.3rem; }
.content a { font-weight: 500; }

.content code {
  padding: 0.14em 0.4em;
  border-radius: 5px;
  background: var(--surface-2);
  border: 1px solid var(--border-soft);
  color: var(--accent-strong);
  font-size: 0.9em;
  font-family: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.content pre {
  overflow: auto;
  margin: 1.15rem 0;
  padding: 18px 20px;
  border-radius: var(--radius-sm);
  background: var(--code-bg);
  color: var(--code-text);
  box-shadow: var(--shadow-sm);
}
.content .code-block {
  position: relative;
  margin: 1.15rem 0;
}
.content .code-block pre {
  margin: 0;
  padding-top: 48px;
}
.code-copy {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid color-mix(in srgb, var(--code-text) 18%, transparent);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--code-bg) 80%, var(--surface));
  color: var(--code-text);
  font: 700 0.76rem/1 "Inter", ui-sans-serif, system-ui, sans-serif;
  cursor: pointer;
  opacity: 0.94;
}
.code-copy:hover {
  border-color: color-mix(in srgb, var(--accent) 58%, var(--code-text));
  color: #fff;
}
.code-copy:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.code-copy svg {
  width: 14px;
  height: 14px;
}
.code-copy.is-copied {
  border-color: color-mix(in srgb, var(--accent) 72%, #fff);
  background: var(--accent-strong);
  color: #fff;
}
.code-copy.is-failed {
  border-color: color-mix(in srgb, var(--accent-warm) 70%, #fff);
  color: #fff;
}
.content pre code {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 0.88rem;
  line-height: 1.6;
}

.content blockquote {
  margin: 1.3rem 0;
  padding: 0.85rem 1.1rem;
  border-left: 4px solid var(--accent);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--accent-soft);
  color: var(--text);
}

.content img {
  display: block;
  margin: 1.2rem auto;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
}

/* Brand/evaluator marks embedded in body content render at icon size. */
.content img[src$=".svg"] {
  width: 200px;
  height: auto;
  border: 0;
  box-shadow: none;
}

.table-wrap {
  overflow: auto;
  margin: 1.2rem 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}
table { width: 100%; min-width: 560px; border-collapse: collapse; }
th, td { padding: 10px 13px; border-bottom: 1px solid var(--border-soft); text-align: left; vertical-align: top; }
th { background: var(--surface-2); color: var(--heading); font-size: 0.86rem; }
tbody tr:nth-child(even) td { background: var(--bg-alt); }
tr:last-child td { border-bottom: 0; }
.numeric { text-align: right; font-variant-numeric: tabular-nums; }

.toc {
  position: sticky;
  top: 90px;
  align-self: start;
  max-height: calc(100vh - 110px);
  overflow-y: auto;
}
.toc h2 {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.toc a { display: block; margin: 7px 0; color: var(--muted); font-size: 0.88rem; }
.toc a:hover { color: var(--accent-strong); text-decoration: none; }
.toc .toc-h3 { padding-left: 12px; font-size: 0.84rem; }
.toc-empty { color: var(--muted); font-size: 0.88rem; }

/* ---------- Footer ---------- */
.site-footer { background: linear-gradient(180deg, var(--deep), var(--deep-2)); color: #cfe6ee; }
html[data-theme="dark"] .site-footer { background: linear-gradient(180deg, var(--deep), var(--deep-2)); border-top: 1px solid var(--border); }

.footer-inner {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 2fr);
  gap: 48px;
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 56px 24px 40px;
}

.footer-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-weight: 800;
  font-size: 1.2rem;
}
.footer-logo:hover { text-decoration: none; }
.footer-logo img { border-radius: 8px; }
.footer-brand p { margin: 14px 0 0; color: #a9cad6; font-size: 0.94rem; max-width: 32ch; }
.footer-license { font-size: 0.85rem !important; }

.footer-cols { display: grid; grid-template-columns: repeat(3, 1fr); gap: 28px; }
.footer-col h3 { margin: 0 0 12px; color: #fff; font-size: 0.78rem; letter-spacing: 0.05em; text-transform: uppercase; }
.footer-col ul { margin: 0; padding: 0; list-style: none; }
.footer-col li { margin: 0 0 9px; }
.footer-col a { color: #a9cad6; font-size: 0.94rem; }
.footer-col a:hover { color: #fff; }

.footer-bottom {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 18px 24px 36px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  color: #87afbd;
  font-size: 0.84rem;
}
.footer-bottom a { color: #a9cad6; }
.footer-bottom code { color: #cfe6ee; font-size: 0.92em; }

/* ---------- Responsive ---------- */
@media (max-width: 1040px) {
  .doc-main { grid-template-columns: minmax(0, 1fr); padding: 32px 28px 60px; }
  .toc { display: none; }
  .feature-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 860px) {
  .nav-toggle { display: flex; }
  .topnav-links {
    position: fixed;
    top: 63px;
    left: 0;
    right: 0;
    flex-direction: column;
    align-items: stretch;
    gap: 4px;
    padding: 16px 24px 24px;
    background: var(--bg);
    border-bottom: 1px solid var(--border);
    box-shadow: var(--shadow);
    transform: translateY(-130%);
    transition: transform 0.2s ease;
  }
  body.nav-open .topnav-links { transform: translateY(0); }
  .theme-toggle { margin: 8px 0 0; }

  .hero-inner { grid-template-columns: 1fr; gap: 24px; padding: 44px 24px 42px; }
  .hero-art { order: -1; }
  .hero-art img { width: min(190px, 54%); }
  .hero-text h1 { font-size: 3.25rem; }

  .doc-shell { grid-template-columns: 1fr; }
  .sidebar {
    position: fixed;
    z-index: 40;
    top: 63px;
    bottom: 0;
    left: 0;
    width: min(86vw, 320px);
    height: auto;
    transform: translateX(-104%);
    transition: transform 0.2s ease;
    box-shadow: var(--shadow);
  }
  body.sidebar-open .sidebar { transform: translateX(0); }

  .footer-inner { grid-template-columns: 1fr; gap: 32px; }
}

@media (max-width: 560px) {
  .footer-cols { grid-template-columns: 1fr 1fr; }
  .doc-main { padding: 28px 18px 52px; }
  .hero-text h1 { font-size: 2.55rem; }
  .feature-grid { grid-template-columns: 1fr; }
  .content h1 { font-size: 2.1rem; }
}

@media print {
  .code-copy { display: none; }
  .content .code-block pre { padding-top: 18px; }
}
"""


SCRIPT = r"""
(function () {
  var root = document.documentElement;
  var body = document.body;
  var themeKey = "crtk-theme";
  var themeParam = "theme";

  var isTheme = function (value) {
    return value === "light" || value === "dark";
  };

  var currentTheme = function () {
    var theme = root.getAttribute("data-theme");
    return isTheme(theme) ? theme : "light";
  };

  var setStoredTheme = function (theme) {
    if (!isTheme(theme)) { return; }
    try { localStorage.setItem(themeKey, theme); } catch (e) {}
  };

  var setUrlTheme = function (url, theme) {
    if (!isTheme(theme)) { return url; }
    url.searchParams.set(themeParam, theme);
    return url;
  };

  var isInternalHtmlLink = function (link) {
    var raw = link.getAttribute("href") || "";
    if (!raw || raw.charAt(0) === "#") {
      return false;
    }
    try {
      var url = new URL(raw, window.location.href);
      var here = new URL(window.location.href);
      if (url.protocol !== here.protocol) { return false; }
      if (url.protocol !== "file:" && url.origin !== here.origin) { return false; }
      return url.pathname.endsWith(".html") || url.pathname.endsWith("/");
    } catch (e) {
      return false;
    }
  };

  var syncThemeLinks = function (scope) {
    var theme = currentTheme();
    Array.prototype.slice.call((scope || document).querySelectorAll("a[href]")).forEach(function (link) {
      if (!isInternalHtmlLink(link)) { return; }
      try {
        link.href = setUrlTheme(new URL(link.getAttribute("href"), window.location.href), theme).href;
      } catch (e) {}
    });
  };

  var syncThemeQuery = function (theme) {
    if (!isTheme(theme) || !history.replaceState) { return; }
    try {
      var url = setUrlTheme(new URL(window.location.href), theme);
      history.replaceState(null, "", url.href);
    } catch (e) {}
  };

  var updateThemeToggle = function () {
    var theme = currentTheme();
    var label = theme === "dark" ? "Switch to light mode" : "Switch to dark mode";
    var toggle = document.querySelector(".theme-toggle");
    if (!toggle) { return; }
    toggle.setAttribute("aria-label", label);
    toggle.title = label;
  };

  var applyTheme = function (theme, options) {
    options = options || {};
    if (!isTheme(theme)) { return; }
    root.setAttribute("data-theme", theme);
    if (options.persist) { setStoredTheme(theme); }
    if (options.updateUrl) { syncThemeQuery(theme); }
    syncThemeLinks(document);
    updateThemeToggle();
  };

  applyTheme(currentTheme());

  // Theme toggle (persisted and propagated across generated pages).
  var toggle = document.querySelector(".theme-toggle");
  if (toggle) {
    toggle.addEventListener("click", function () {
      var next = currentTheme() === "dark" ? "light" : "dark";
      applyTheme(next, { persist: true, updateUrl: true });
    });
  }

  // Mobile top-nav menu.
  var navToggle = document.querySelector(".nav-toggle");
  if (navToggle) {
    navToggle.addEventListener("click", function () {
      var open = body.classList.toggle("nav-open");
      navToggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
  }

  // Add copy buttons to rendered code blocks.
  var copyIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
    'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<rect x="9" y="9" width="10" height="10" rx="2"/><path d="M5 15V7a2 2 0 0 1 2-2h8"/></svg>';
  var fallbackCopyText = function (text) {
    return new Promise(function (resolve, reject) {
      var textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.setAttribute("readonly", "");
      textarea.style.position = "fixed";
      textarea.style.top = "-1000px";
      textarea.style.left = "-1000px";
      document.body.appendChild(textarea);
      textarea.select();
      try {
        if (document.execCommand("copy")) {
          resolve();
        } else {
          reject(new Error("copy command failed"));
        }
      } catch (error) {
        reject(error);
      } finally {
        document.body.removeChild(textarea);
      }
    });
  };
  var copyText = function (text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text).catch(function () {
        return fallbackCopyText(text);
      });
    }
    return fallbackCopyText(text);
  };
  var setCopyState = function (button, label, className) {
    button.classList.remove("is-copied", "is-failed");
    if (className) { button.classList.add(className); }
    button.querySelector("span").textContent = label;
  };
  Array.prototype.slice.call(document.querySelectorAll(".content pre > code")).forEach(function (code) {
    var pre = code.parentElement;
    if (!pre || pre.dataset.copyReady === "true") { return; }
    pre.dataset.copyReady = "true";
    var wrapper = document.createElement("div");
    wrapper.className = "code-block";
    pre.parentNode.insertBefore(wrapper, pre);
    wrapper.appendChild(pre);

    var button = document.createElement("button");
    button.type = "button";
    button.className = "code-copy";
    button.setAttribute("aria-label", "Copy code");
    button.title = "Copy code";
    button.innerHTML = copyIcon + "<span>Copy</span>";
    wrapper.appendChild(button);

    button.addEventListener("click", function () {
      copyText(code.textContent.replace(/\n$/, "")).then(function () {
        setCopyState(button, "Copied", "is-copied");
        window.setTimeout(function () { setCopyState(button, "Copy", ""); }, 1400);
      }).catch(function () {
        setCopyState(button, "Select", "is-failed");
        window.setTimeout(function () { setCopyState(button, "Copy", ""); }, 1800);
      });
    });
  });

  // Documentation search filters the sidebar and shows a results dropdown.
  var search = document.querySelector("#doc-search");
  var results = document.querySelector("#search-results");
  var links = Array.prototype.slice.call(document.querySelectorAll(".nav-section a"));
  var index = window.CRTK_SEARCH_INDEX || [];
  var escapeHtml = function (value) {
    return String(value).replace(/[&<>"']/g, function (char) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char];
    });
  };

  if (search) {
    search.addEventListener("input", function () {
      var query = search.value.trim().toLowerCase();
      links.forEach(function (link) {
        var title = link.dataset.title || "";
        link.hidden = Boolean(query) && title.indexOf(query) === -1;
      });
      if (!results) { return; }
      if (!query) { results.hidden = true; results.innerHTML = ""; return; }
      var matches = index.filter(function (page) {
        return (page.title + " " + page.category + " " + page.text).toLowerCase().indexOf(query) !== -1;
      }).slice(0, 7);
      if (!matches.length) {
        results.hidden = false;
        results.innerHTML = "<small>No page matches.</small>";
        return;
      }
      results.hidden = false;
      results.innerHTML = matches.map(function (page) {
        return '<a href="' + escapeHtml(encodeURI(page.href)) + '">' +
          escapeHtml(page.title) + "<small>" + escapeHtml(page.category) + "</small></a>";
      }).join("");
      syncThemeLinks(results);
    });
  }
})();
"""


if __name__ == "__main__":
    main()
