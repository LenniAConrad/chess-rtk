
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
