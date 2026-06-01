
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
