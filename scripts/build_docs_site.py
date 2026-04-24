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


def render_page(page: Page, pages: list[Page]) -> tuple[str, str]:
    """Render one full HTML page."""
    markdown = (WIKI / page.source).read_text(encoding="utf-8")
    body, toc, search_text = render_markdown(markdown)
    page_title = title_from_markdown(WIKI / page.source)
    hero = ""
    if page.href == "index.html":
        hero = """
<section class="hero">
  <div>
    <p class="eyebrow">ChessRTK documentation</p>
    <h1>Chess tools, engine workflows, datasets, and publishing in one CLI.</h1>
    <p>Use the sidebar to browse guides, command references, architecture notes, and workflow docs.</p>
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
    for path in (ROOT / "assets" / "diagrams").glob("*.png"):
        shutil.copy2(path, assets / "diagrams" / path.name)
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
  --bg: #f6f7f4;
  --panel: #ffffff;
  --panel-soft: #f0f4ef;
  --text: #202521;
  --muted: #667066;
  --border: #d9e0d7;
  --accent: #2d6a4f;
  --accent-strong: #1b4332;
  --accent-soft: #e5f3eb;
  --warn: #9b5c00;
  --code-bg: #17201b;
  --code-text: #edf7ef;
  --shadow: 0 18px 45px rgba(24, 37, 28, 0.10);
}

* {
  box-sizing: border-box;
}

html {
  scroll-behavior: smooth;
}

body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font: 16px/1.62 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
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
  background: #fbfcfa;
  border-right: 1px solid var(--border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text);
  text-decoration: none;
  margin-bottom: 24px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: var(--accent-strong);
  color: white;
  font-weight: 800;
  letter-spacing: 0;
}

.brand small {
  display: block;
  color: var(--muted);
  font-size: 0.78rem;
  margin-top: -2px;
}

.search-label {
  display: block;
  color: var(--muted);
  font-size: 0.78rem;
  font-weight: 700;
  margin: 0 0 6px;
  text-transform: uppercase;
}

.search {
  width: 100%;
  height: 42px;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0 12px;
  background: white;
  color: var(--text);
  font: inherit;
}

.nav-section {
  margin-top: 24px;
}

.nav-section h2 {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 0.76rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.nav-section a {
  display: block;
  padding: 8px 10px;
  border-radius: 8px;
  color: #2d352e;
  text-decoration: none;
}

.nav-section a:hover,
.nav-section a.active {
  background: var(--accent-soft);
  color: var(--accent-strong);
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
  background: rgba(246, 247, 244, 0.92);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(12px);
}

.topbar span {
  display: block;
  color: var(--muted);
  font-size: 0.78rem;
}

.topbar nav {
  display: flex;
  gap: 14px;
  align-items: center;
  white-space: nowrap;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 420px);
  align-items: center;
  gap: 28px;
  margin: 32px auto 0;
  max-width: 1160px;
  padding: 0 32px;
}

.hero div {
  padding: 34px;
  border-radius: 8px;
  background: var(--panel);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
}

.hero img {
  box-shadow: var(--shadow);
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 860px) 240px;
  gap: 34px;
  max-width: 1160px;
  margin: 32px auto;
  padding: 0 32px;
}

.content {
  min-width: 0;
  padding: 38px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow);
}

.content h1 {
  margin-top: 0;
  font-size: clamp(2rem, 4vw, 3.2rem);
  line-height: 1.08;
}

.content h2 {
  margin-top: 2.1em;
  padding-top: 0.5em;
  border-top: 1px solid var(--border);
  font-size: 1.55rem;
  line-height: 1.2;
}

.content h3 {
  margin-top: 1.7em;
  font-size: 1.18rem;
}

.content p,
.content li {
  color: #303830;
}

.content code {
  padding: 0.12em 0.34em;
  border-radius: 5px;
  background: var(--panel-soft);
  color: #16351f;
  font-size: 0.92em;
}

.content pre {
  overflow: auto;
  padding: 18px;
  border-radius: 8px;
  background: var(--code-bg);
  color: var(--code-text);
}

.content pre code {
  padding: 0;
  background: transparent;
  color: inherit;
  font-size: 0.92rem;
}

.table-wrap {
  overflow: auto;
  margin: 1rem 0;
  border: 1px solid var(--border);
  border-radius: 8px;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 620px;
}

th,
td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  vertical-align: top;
}

th {
  background: var(--panel-soft);
  color: #263327;
  font-size: 0.88rem;
}

tr:last-child td {
  border-bottom: 0;
}

.toc {
  position: sticky;
  top: 76px;
  align-self: start;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fbfcfa;
}

.toc h2 {
  margin: 0 0 12px;
  font-size: 0.8rem;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.toc a {
  display: block;
  margin: 8px 0;
  color: #344237;
  font-size: 0.92rem;
  text-decoration: none;
}

.toc .toc-h3 {
  padding-left: 12px;
  color: var(--muted);
}

.footer {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  max-width: 1160px;
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
    background: white;
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
  }

  .hero img {
    margin-top: 18px;
  }

  .content {
    padding: 24px 18px;
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
  const links = Array.from(document.querySelectorAll(".nav-section a"));

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
    });
  }
})();
"""


if __name__ == "__main__":
    main()
