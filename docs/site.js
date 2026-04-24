
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
