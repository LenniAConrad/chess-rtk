#!/usr/bin/env python3
"""Build the static ChessRTK documentation site from wiki Markdown files."""

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


@dataclass(frozen=True)
class Page:
    """One generated documentation page."""

    source: str
    title: str
    href: str
    category: str


NAVIGATION: list[tuple[str, list[tuple[str, str]]]] = [
    (
        "Start Here",
        [
            ("Home.md", "Home"),
            ("getting-started.md", "Getting Started"),
            ("use-cases.md", "Use Cases"),
            ("command-cheatsheet.md", "Command Cheatsheet"),
            ("faq.md", "FAQ"),
            ("command-reference.md", "Command Reference"),
            ("example-commands.md", "Example Commands"),
            ("troubleshooting.md", "Troubleshooting"),
        ],
    ),
    (
        "User Guides",
        [
            ("build-and-install.md", "Build & Install"),
            ("workbench.md", "Desktop Workbench"),
            ("configuration.md", "Configuration"),
            ("in-house-engine.md", "In-House Engine"),
            ("lc0.md", "LC0"),
            ("outputs-and-logs.md", "Outputs & Logs"),
            ("support.md", "Support"),
        ],
    ),
    (
        "Workflows",
        [
            ("mining.md", "Mining Puzzles"),
            ("filter-dsl.md", "Filter DSL"),
            ("datasets.md", "Datasets"),
            ("book-publishing.md", "Book Publishing"),
            ("piece-tags.md", "Tags"),
            ("tag-reference.md", "Tag Reference"),
            ("t5.md", "T5 Text"),
            ("ai-agents.md", "AI Agents"),
        ],
    ),
    (
        "Development",
        [
            ("architecture.md", "Architecture"),
            ("quality-and-testing.md", "Quality & Testing"),
            ("development-notes.md", "Development Notes"),
            ("releasing.md", "Releasing"),
            ("roadmap.md", "Roadmap"),
            ("glossary.md", "Glossary"),
            ("tagging-implementation-plan.md", "Tagging Plan"),
            ("README.md", "Wiki Index"),
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

    def image_repl(match: re.Match[str]) -> str:
        alt = match.group(1)
        href = convert_href(html.unescape(match.group(2)))
        return f'<img src="{html.escape(href)}" alt="{alt}" loading="lazy">'

    def link_repl(match: re.Match[str]) -> str:
        label = match.group(1)
        href = convert_href(html.unescape(match.group(2)))
        return f'<a href="{html.escape(href)}">{label}</a>'

    escaped = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", image_repl, escaped)
    escaped = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", link_repl, escaped)
    escaped = re.sub(r"`([^`]+)`", r"<code>\1</code>", escaped)
    escaped = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", escaped)
    escaped = re.sub(r"\*([^*]+)\*", r"<em>\1</em>", escaped)
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


def nav_html(pages: list[Page], active: str) -> str:
    """Render the sidebar navigation."""
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
        return '<p class="toc-empty">No sections</p>'
    out = ['<h2>On This Page</h2>']
    for level, title, ident in links[:18]:
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
        markdown = re.sub(
            r"^# [^\n]+\n\n!\[[^\]]*banner[^\]]*\]\([^)]+\)\n\n",
            "",
            markdown,
            count=1,
            flags=re.IGNORECASE,
        )
    return markdown


def render_page(page: Page, pages: list[Page]) -> tuple[str, str]:
    """Render one full HTML page."""
    markdown = markdown_for_page(page)
    body, toc, search_text = render_markdown(markdown)
    page_title = title_from_markdown(WIKI / page.source)
    hero = ""
    if page.href == "index.html":
        hero = """
<section class="hero">
  <div>
    <p class="eyebrow">ChessRTK documentation</p>
    <h1>ChessRTK is a Java toolkit for deterministic chess workflows.</h1>
    <p>Use these docs for setup, CLI commands, engine workflows, tagging, datasets, Workbench usage, and publishing.</p>
    <p class="hero-actions">
      <a href="getting-started.html">Setup</a>
      <a href="command-cheatsheet.html">Command cheatsheet</a>
      <a href="architecture.html">Architecture</a>
    </p>
  </div>
  <img src="assets/banner/github/crtk-github-banner.png" alt="ChessRTK banner">
</section>
"""
    html_page = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{html.escape(page_title)} | ChessRTK Wiki</title>
  <link rel="stylesheet" href="styles.css">
  <script defer src="search-index.js"></script>
  <script defer src="site.js"></script>
</head>
<body>
  <button class="menu-toggle" type="button" aria-label="Toggle navigation">Menu</button>
  <div class="site-shell">
    <aside class="sidebar">
      <a class="brand" href="index.html">
        <span class="brand-mark">CR</span>
        <span><strong>ChessRTK</strong><small>Wiki</small></span>
      </a>
      <label class="search-label" for="doc-search">Search pages</label>
      <input id="doc-search" class="search" type="search" placeholder="Search docs...">
      <div id="search-results" class="search-results" hidden></div>
      <nav class="nav">{nav_html(pages, page.href)}</nav>
    </aside>
    <div class="page">
      <header class="topbar">
        <div>
          <span>{html.escape(page.category)}</span>
          <strong>{html.escape(page_title)}</strong>
        </div>
        <nav>
          <a href="index.html">Home</a>
          <a href="wiki-index.html">Wiki Index</a>
          <a href="chessrtk-manual.pdf">Manual PDF</a>
          <a href="command-reference.html">Commands</a>
        </nav>
      </header>
      {hero}
      <main class="content-layout">
        <article class="content">
          {body}
        </article>
        <aside class="toc">{toc_html(toc)}</aside>
      </main>
      <footer class="footer">
        <span>Generated from <code>wiki/{html.escape(page.source)}</code>.</span>
        <span>Build with <code>python3 scripts/build_docs_site.py</code>.</span>
      </footer>
    </div>
  </div>
</body>
</html>
"""
    return html_page, search_text


def copy_assets() -> None:
    """Copy documentation assets into the generated site."""
    assets = DOCS / "assets"
    if assets.exists():
        shutil.rmtree(assets)
    (assets / "diagrams").mkdir(parents=True, exist_ok=True)
    (assets / "banner" / "github").mkdir(parents=True, exist_ok=True)
    (assets / "screenshots").mkdir(parents=True, exist_ok=True)
    for path in (ROOT / "assets" / "diagrams").glob("*.png"):
        shutil.copy2(path, assets / "diagrams" / path.name)
    for path in (ROOT / "assets" / "screenshots").glob("*.png"):
        shutil.copy2(path, assets / "screenshots" / path.name)
    banner = ROOT / "assets" / "banner" / "github" / "crtk-github-banner.png"
    if banner.exists():
        shutil.copy2(banner, assets / "banner" / "github" / banner.name)


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
:root {
  color-scheme: light;
  --bg: #f6f6f3;
  --surface: #ffffff;
  --surface-muted: #f0f1ed;
  --text: #202420;
  --muted: #666d67;
  --border: #d9ddd7;
  --accent: #2f6f5e;
  --accent-strong: #245447;
  --accent-soft: #e7f0ec;
  --code-bg: #111827;
  --code-text: #e7ece9;
  --shadow: 0 18px 45px rgba(17, 24, 39, 0.08);
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  scroll-behavior: smooth;
}

body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font: 16px/1.64 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

a {
  color: var(--accent);
  text-decoration-thickness: 0.08em;
  text-underline-offset: 0.18em;
}

img {
  max-width: 100%;
  border-radius: 8px;
}

.site-shell {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr);
  min-height: 100vh;
}

.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: auto;
  padding: 22px 18px;
  background: #fbfbf9;
  border-right: 1px solid var(--border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  color: var(--text);
  text-decoration: none;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: var(--accent);
  color: #ffffff;
  font-weight: 800;
  letter-spacing: 0;
}

.brand small {
  display: block;
  margin-top: -2px;
  color: var(--muted);
  font-size: 0.78rem;
}

.search-label {
  display: block;
  margin: 0 0 6px;
  color: var(--muted);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.search {
  width: 100%;
  height: 42px;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0 12px;
  background: var(--surface);
  color: var(--text);
  font: inherit;
}

.search:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-soft);
  outline-offset: 1px;
}

.search-results {
  margin-top: 8px;
  padding: 6px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.search-results a {
  display: block;
  padding: 8px 9px;
  border-radius: 7px;
  color: var(--text);
  text-decoration: none;
}

.search-results a:hover {
  background: var(--surface-muted);
}

.search-results small {
  display: block;
  color: var(--muted);
  font-size: 0.75rem;
}

.nav-section {
  margin-top: 24px;
}

.nav-section h2 {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 0.76rem;
  letter-spacing: 0;
  text-transform: uppercase;
}

.nav-section a {
  display: block;
  padding: 8px 10px;
  border-radius: 8px;
  color: #303630;
  text-decoration: none;
}

.nav-section a:hover,
.nav-section a.active {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.nav-section a.active {
  box-shadow: inset 3px 0 0 var(--accent);
}

.page {
  min-width: 0;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 32px;
  background: rgba(246, 246, 243, 0.92);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(12px);
}

.topbar span {
  display: block;
  color: var(--muted);
  font-size: 0.78rem;
}

.topbar strong {
  color: var(--text);
}

.topbar nav {
  display: flex;
  gap: 14px;
  align-items: center;
  white-space: nowrap;
}

.topbar nav a {
  color: var(--accent-strong);
  font-weight: 700;
  text-decoration: none;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(250px, 400px);
  align-items: center;
  gap: 30px;
  max-width: 1180px;
  margin: 34px auto 0;
  padding: 0 32px 34px;
  border-bottom: 1px solid var(--border);
}

.hero div {
  min-width: 0;
}

.hero h1 {
  max-width: 760px;
  margin: 0;
  color: var(--text);
  font-size: 3.25rem;
  line-height: 1.05;
  letter-spacing: 0;
  overflow-wrap: break-word;
}

.hero p {
  max-width: 680px;
  color: #3f4740;
}

.hero img {
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: var(--shadow);
}

.eyebrow {
  margin: 0 0 10px;
  color: var(--accent);
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 24px 0 0;
}

.hero-actions a {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0 14px;
  background: var(--surface);
  color: var(--accent-strong);
  font-weight: 800;
  text-decoration: none;
}

.hero-actions a:first-child {
  border-color: var(--accent);
  background: var(--accent);
  color: #ffffff;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 900px) 250px;
  gap: 34px;
  max-width: 1180px;
  margin: 32px auto;
  padding: 0 32px;
}

.content {
  min-width: 0;
  padding: 38px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.content h1 {
  margin-top: 0;
  color: var(--text);
  font-size: 2.5rem;
  line-height: 1.1;
  letter-spacing: 0;
}

.content h2 {
  margin-top: 2.1em;
  padding-top: 1em;
  border-top: 1px solid var(--border);
  color: var(--text);
  font-size: 1.48rem;
  line-height: 1.25;
  letter-spacing: 0;
}

.content h3 {
  margin-top: 1.7em;
  color: #2c332e;
  font-size: 1.16rem;
  letter-spacing: 0;
}

.content p,
.content li {
  color: #303630;
}

.content p {
  margin: 0.9rem 0;
}

.content ul,
.content ol {
  padding-left: 1.35rem;
}

.content li + li {
  margin-top: 0.25rem;
}

.content code {
  padding: 0.12em 0.34em;
  border: 1px solid #dde2dd;
  border-radius: 5px;
  background: var(--surface-muted);
  color: #1e3f35;
  font-size: 0.92em;
}

.content pre {
  overflow: auto;
  margin: 1.1rem 0;
  padding: 18px;
  border-radius: 8px;
  background: var(--code-bg);
  color: var(--code-text);
}

.content pre code {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 0.92rem;
}

.content blockquote {
  margin: 1.35rem 0;
  padding: 0.85rem 1rem;
  border-left: 4px solid var(--accent);
  border-radius: 0 8px 8px 0;
  background: var(--accent-soft);
}

.table-wrap {
  overflow: auto;
  margin: 1.15rem 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
}

table {
  width: 100%;
  min-width: 620px;
  border-collapse: collapse;
}

th,
td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  vertical-align: top;
}

th {
  background: var(--surface-muted);
  color: #2c332e;
  font-size: 0.88rem;
}

tbody tr:nth-child(even) td {
  background: #fbfbf9;
}

tr:last-child td {
  border-bottom: 0;
}

.numeric {
  text-align: right;
}

.toc {
  position: sticky;
  top: 76px;
  align-self: start;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fbfbf9;
}

.toc h2 {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 0.8rem;
  letter-spacing: 0;
  text-transform: uppercase;
}

.toc a {
  display: block;
  margin: 8px 0;
  color: #39413b;
  font-size: 0.92rem;
  text-decoration: none;
}

.toc a:hover {
  color: var(--accent);
}

.toc .toc-h3 {
  padding-left: 12px;
  color: var(--muted);
}

.toc-empty {
  color: var(--muted);
}

.footer {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  max-width: 1180px;
  margin: 0 auto 38px;
  padding: 0 32px;
  color: var(--muted);
  font-size: 0.86rem;
}

.menu-toggle {
  display: none;
}

@media (max-width: 980px) {
  .site-shell {
    display: block;
  }

  .menu-toggle {
    position: fixed;
    top: 12px;
    left: 12px;
    z-index: 5;
    display: inline-flex;
    height: 38px;
    align-items: center;
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0 12px;
    background: var(--surface);
    color: var(--text);
    font: inherit;
  }

  .sidebar {
    position: fixed;
    z-index: 4;
    width: min(86vw, 320px);
    transform: translateX(-105%);
    transition: transform 0.18s ease;
    box-shadow: var(--shadow);
  }

  body.nav-open .sidebar {
    transform: translateX(0);
  }

  .topbar {
    padding-left: 88px;
  }

  .topbar nav {
    display: none;
  }

  .hero,
  .content-layout {
    grid-template-columns: 1fr;
  }

  .toc {
    display: none;
  }
}

@media (max-width: 720px) {
  body {
    overflow-x: hidden;
    font-size: 15px;
  }

  .hero,
  .content-layout,
  .footer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .hero {
    display: block;
    margin-top: 24px;
  }

  .hero h1 {
    font-size: 2.05rem;
    line-height: 1.12;
  }

  .hero img {
    display: block;
    margin-top: 18px;
  }

  .hero-actions a {
    width: 100%;
    justify-content: center;
  }

  .content {
    padding: 24px 18px;
  }

  .content h1 {
    font-size: 2rem;
  }

  .footer {
    display: block;
  }
}
"""


SCRIPT = r"""
(function () {
  const toggle = document.querySelector(".menu-toggle");
  const search = document.querySelector("#doc-search");
  const results = document.querySelector("#search-results");
  const links = Array.from(document.querySelectorAll(".nav-section a"));
  const index = window.CRTK_SEARCH_INDEX || [];
  const escapeHtml = (value) => String(value).replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;",
  }[char]));

  if (toggle) {
    toggle.addEventListener("click", () => {
      document.body.classList.toggle("nav-open");
    });
  }

  links.forEach((link) => {
    link.addEventListener("click", () => {
      document.body.classList.remove("nav-open");
    });
  });

  if (search) {
    search.addEventListener("input", () => {
      const query = search.value.trim().toLowerCase();
      links.forEach((link) => {
        const title = link.dataset.title || "";
        link.hidden = Boolean(query) && !title.includes(query);
      });
      if (!results) {
        return;
      }
      if (!query) {
        results.hidden = true;
        results.innerHTML = "";
        return;
      }
      const matches = index
        .filter((page) => `${page.title} ${page.category} ${page.text}`.toLowerCase().includes(query))
        .slice(0, 6);
      if (!matches.length) {
        results.hidden = false;
        results.innerHTML = "<small>No page matches</small>";
        return;
      }
      results.hidden = false;
      results.innerHTML = matches
        .map((page) => {
          const href = escapeHtml(encodeURI(page.href));
          return `<a href="${href}">${escapeHtml(page.title)}<small>${escapeHtml(page.category)}</small></a>`;
        })
        .join("");
    });
  }
})();
"""


if __name__ == "__main__":
    main()
