/*
 * juicerdocs — small client-side helpers.
 *
 *   - Theme toggle (writes localStorage; the <head> snippet applies the
 *     class before paint so there's no white flash on dark-mode reload).
 *   - Mobile sidebar toggle.
 *   - "Copy" buttons on every <pre> code block.
 *   - Mark active sidebar link based on current URL.
 *   - Tag <pre> elements with `data-language` so the CSS badge can render.
 *   - Client-side search via /search.json.
 *
 * Self-contained — no external dependencies.
 */

(function () {
  "use strict";

  // ===== Theme toggle =====
  const themeBtn = document.getElementById("juicerdocs-theme-toggle");
  if (themeBtn) {
    themeBtn.addEventListener("click", () => {
      const dark = document.documentElement.classList.toggle("dark");
      try {
        localStorage.setItem("juicerdocs-theme", dark ? "dark" : "light");
      } catch (e) { /* private mode */ }
    });
  }

  // ===== Mobile sidebar toggle =====
  const sidebarBtn = document.getElementById("juicerdocs-sidebar-toggle");
  const sidebar = document.getElementById("juicerdocs-sidebar");
  if (sidebarBtn && sidebar) {
    sidebarBtn.addEventListener("click", () => {
      const parent = sidebar.closest("aside");
      if (parent) parent.classList.toggle("hidden");
    });
  }

  // ===== Code block copy buttons + language badge =====
  document.querySelectorAll("pre > code").forEach((code) => {
    const pre = code.parentElement;
    if (!pre || pre.dataset.juicerdocsCopyDone) return;
    pre.dataset.juicerdocsCopyDone = "1";

    // Extract language from the className (format: language-foo) and tag
    // <pre> with data-language so the CSS ::before can show it.
    const cls = code.className || "";
    const match = cls.match(/language-(\S+)/);
    if (match) pre.dataset.language = match[1];

    const btn = document.createElement("button");
    btn.className = "juicerdocs-copy";
    btn.type = "button";
    btn.setAttribute("aria-label", "Copy code");
    btn.textContent = "Copy";
    btn.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(code.innerText);
        btn.textContent = "Copied!";
        btn.classList.add("copied");
        setTimeout(() => {
          btn.textContent = "Copy";
          btn.classList.remove("copied");
        }, 1500);
      } catch (e) {
        btn.textContent = "Error";
      }
    });
    pre.appendChild(btn);
  });

  // ===== Sidebar active-link highlight =====
  // Matches by URL path (ignoring query/fragment) and adds a class.
  const here = location.pathname.replace(/\/+$/, "/") || "/";
  document.querySelectorAll("[data-juicerdocs-nav-link]").forEach((a) => {
    const href = a.getAttribute("href");
    if (!href) return;
    const norm = href.replace(/\/+$/, "/") || "/";
    if (norm === here) a.classList.add("juicerdocs-nav-active");
  });

  // ===== Search =====
  const searchInput = document.getElementById("juicerdocs-search");
  const searchResults = document.getElementById("juicerdocs-search-results");
  if (searchInput && searchResults) {
    let index = null;
    let activeIndex = -1;

    async function ensureIndex() {
      if (index) return index;
      try {
        // Find the search.json relative to this page's site root by walking
        // up from the page URL to the host root. Simpler: assume it's at
        // /search.json relative to the site origin.
        const res = await fetch("/search.json");
        index = await res.json();
      } catch (e) {
        index = [];
      }
      return index;
    }

    function snippet(content, q) {
      const lc = content.toLowerCase();
      const idx = lc.indexOf(q.toLowerCase());
      if (idx < 0) return content.slice(0, 80) + (content.length > 80 ? "…" : "");
      const start = Math.max(0, idx - 30);
      const end = Math.min(content.length, idx + q.length + 50);
      return (start > 0 ? "…" : "") + content.slice(start, end) + (end < content.length ? "…" : "");
    }

    function renderResults(matches, q) {
      searchResults.innerHTML = "";
      if (matches.length === 0) {
        searchResults.innerHTML = '<div class="juicerdocs-result text-zinc-500 dark:text-zinc-500">No matches.</div>';
      } else {
        for (let i = 0; i < matches.length; i++) {
          const r = matches[i];
          const a = document.createElement("a");
          a.className = "juicerdocs-result";
          a.href = r.url;
          a.dataset.idx = i;
          a.innerHTML =
            '<span class="juicerdocs-result-title"></span>' +
            '<span class="juicerdocs-result-snippet"></span>';
          a.querySelector(".juicerdocs-result-title").textContent = r.title || r.url;
          a.querySelector(".juicerdocs-result-snippet").textContent = snippet(r.content || r.summary || "", q);
          searchResults.appendChild(a);
        }
      }
      searchResults.classList.remove("hidden");
      activeIndex = -1;
    }

    async function doSearch(q) {
      if (!q) {
        searchResults.classList.add("hidden");
        return;
      }
      const data = await ensureIndex();
      const lc = q.toLowerCase();
      const matches = data
        .filter((r) =>
          (r.title && r.title.toLowerCase().includes(lc)) ||
          (r.summary && r.summary.toLowerCase().includes(lc)) ||
          (r.content && r.content.toLowerCase().includes(lc))
        )
        .slice(0, 10);
      renderResults(matches, q);
    }

    searchInput.addEventListener("input", (e) => doSearch(e.target.value.trim()));
    searchInput.addEventListener("focus", (e) => {
      if (e.target.value.trim()) doSearch(e.target.value.trim());
    });
    searchInput.addEventListener("keydown", (e) => {
      const items = searchResults.querySelectorAll(".juicerdocs-result");
      if (e.key === "ArrowDown") {
        e.preventDefault();
        activeIndex = Math.min(items.length - 1, activeIndex + 1);
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        activeIndex = Math.max(0, activeIndex - 1);
      } else if (e.key === "Enter" && activeIndex >= 0) {
        e.preventDefault();
        items[activeIndex].click();
      } else if (e.key === "Escape") {
        searchResults.classList.add("hidden");
        searchInput.blur();
      }
      items.forEach((it, i) => it.classList.toggle("active", i === activeIndex));
    });
    document.addEventListener("click", (e) => {
      if (!searchInput.contains(e.target) && !searchResults.contains(e.target)) {
        searchResults.classList.add("hidden");
      }
    });
  }
})();
