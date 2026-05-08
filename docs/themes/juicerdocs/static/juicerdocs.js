/*
 * juicerdocs — small client-side helpers.
 *
 *   - Theme toggle (data-theme attribute on <html>; persisted in
 *     localStorage; the <head> snippet applies it before paint so there's
 *     no white flash on dark-mode reload).
 *   - Mobile sidebar toggle.
 *   - "Copy" buttons + language badges on every <pre> code block.
 *   - Mark active sidebar link based on current URL.
 *   - Tabs widget (synthesizes button bar from panels).
 *   - Client-side search via /search.json.
 *   - "On this page" right-rail active-heading highlight via
 *     IntersectionObserver.
 *
 * Self-contained — no external dependencies.
 */

(function () {
  "use strict";

  // ===== Theme toggle (data-theme attribute) =====
  const themeBtn = document.getElementById("juicerdocs-theme-toggle");
  if (themeBtn) {
    themeBtn.addEventListener("click", () => {
      const cur  = document.documentElement.getAttribute("data-theme");
      const next = cur === "dark" ? "light" : "dark";
      document.documentElement.setAttribute("data-theme", next);
      try { localStorage.setItem("juicerdocs-theme", next); } catch (e) { /* private mode */ }
    });
  }

  // ===== Mobile sidebar overlay =====
  // Toggles `body[data-jd-sidebar="open"]`, which the CSS uses to slide
  // the sidebar in from the left as a fixed overlay. Uses event delegation
  // so a missing element on this page doesn't matter; clicks elsewhere
  // (backdrop, nav links, Esc key) all close the sidebar.
  function setSidebar(open) {
    if (open) document.body.setAttribute("data-jd-sidebar", "open");
    else      document.body.removeAttribute("data-jd-sidebar");
  }
  document.addEventListener("click", (e) => {
    if (e.target.closest("#juicerdocs-sidebar-toggle")) {
      e.preventDefault();
      e.stopPropagation();
      setSidebar(document.body.getAttribute("data-jd-sidebar") !== "open");
      return;
    }
    if (e.target.closest(".jd-sidebar-backdrop")) {
      setSidebar(false);
      return;
    }
    // Close when a sidebar nav link is tapped (mobile UX).
    const link = e.target.closest(".jd-sidebar-aside a");
    if (link) setSidebar(false);
  });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") setSidebar(false); });

  // ===== Code block copy buttons + language badge =====
  // Add `group` to each <pre> so the button's `group-hover:opacity-100`
  // works without a hand-rolled `.prose pre:hover .juicerdocs-copy` rule.
  const COPY_BTN_CLASS = "absolute top-1.5 right-2 px-2 py-0.5 text-xs rounded text-muted bg-fg/10 border border-fg/15 cursor-pointer opacity-0 group-hover:opacity-100 hover:!bg-fg/20 hover:!text-fg transition";
  const COPY_DONE      = ["!text-leaf", "!opacity-100"];
  document.querySelectorAll("pre > code").forEach((code) => {
    const pre = code.parentElement;
    if (!pre || pre.dataset.juicerdocsCopyDone) return;
    pre.dataset.juicerdocsCopyDone = "1";
    pre.classList.add("group");

    // Tag <pre> with data-language so the CSS ::before can show it.
    const cls   = code.className || "";
    const match = cls.match(/language-(\S+)/);
    if (match) pre.dataset.language = match[1];

    const btn = document.createElement("button");
    btn.className = COPY_BTN_CLASS;
    btn.type = "button";
    btn.setAttribute("aria-label", "Copy code");
    btn.textContent = "Copy";
    btn.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(code.innerText);
        btn.textContent = "Copied!";
        btn.classList.add(...COPY_DONE);
        setTimeout(() => {
          btn.textContent = "Copy";
          btn.classList.remove(...COPY_DONE);
        }, 1500);
      } catch (e) {
        btn.textContent = "Error";
      }
    });
    pre.appendChild(btn);
  });

  // ===== Tabs widget — see {= tabs / tab =} shortcodes =====
  // Each .juicerdocs-tab-panel ships with utility classes for layout
  // (`p-4 px-5`); we add `block` here when active, `hidden` otherwise.
  // Buttons are JS-created so we set utility classes inline. The
  // `!`-prefixed active classes outweigh the inactive state without
  // having to remove/add a base color each toggle.
  const TABS_BAR_CLASS    = "flex gap-1 px-2 pt-2 bg-surface border-b border-border";
  const TABS_BTN_CLASS    = "px-3.5 py-2 text-sm font-medium text-muted bg-transparent border-0 border-b-2 border-transparent -mb-px cursor-pointer hover:text-fg";
  const TABS_BTN_ACTIVE   = ["!text-brand", "!border-b-brand"];
  document.querySelectorAll(".juicerdocs-tabs[data-juicerdocs-tabs]").forEach((root) => {
    const panels = Array.from(root.querySelectorAll(":scope > .juicerdocs-tab-panel"));
    if (panels.length === 0) return;
    const bar = document.createElement("div");
    bar.className = TABS_BAR_CLASS;
    panels.forEach((panel, i) => {
      const label = panel.dataset.tabLabel || `Tab ${i + 1}`;
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = TABS_BTN_CLASS;
      if (i === 0) btn.classList.add(...TABS_BTN_ACTIVE);
      btn.textContent = label;
      btn.addEventListener("click", () => {
        bar.querySelectorAll("button").forEach((b) => b.classList.remove(...TABS_BTN_ACTIVE));
        panels.forEach((p) => p.classList.add("hidden"));
        btn.classList.add(...TABS_BTN_ACTIVE);
        panel.classList.remove("hidden");
      });
      bar.appendChild(btn);
      if (i !== 0) panel.classList.add("hidden");
    });
    root.insertBefore(bar, root.firstChild);
    root.removeAttribute("data-juicerdocs-tabs");
  });

  // ===== Sidebar active-link highlight =====
  // Tailwind utilities (text-brand, bg-brand-soft, etc.) are listed in
  // tailwind.css's @source so the build picks them up from this file.
  const NAV_ACTIVE = ["!text-brand", "bg-brand-soft", "!border-l-brand", "font-medium"];
  const here = location.pathname.replace(/\/+$/, "/") || "/";
  document.querySelectorAll("[data-juicerdocs-nav-link]").forEach((a) => {
    const href = a.getAttribute("href");
    if (!href) return;
    const norm = href.replace(/\/+$/, "/") || "/";
    if (norm === here) a.classList.add(...NAV_ACTIVE);
  });

  // ===== "On this page" — active-heading highlight =====
  //
  // We use IntersectionObserver to track which heading is currently
  // closest to the top of the viewport. Whichever heading just crossed
  // a 0–25% horizontal band gets the "active" style on its TOC link.
  // Falls back to highlighting nothing when no headings exist or
  // IntersectionObserver isn't available.
  (function highlightActiveHeading() {
    const tocLinks = document.querySelectorAll("[data-juicerdocs-toc-link]");
    if (tocLinks.length === 0 || !("IntersectionObserver" in window)) return;

    const linkById = new Map();
    tocLinks.forEach((a) => linkById.set(a.dataset.jdTocId, a));

    const headings = Array.from(document.querySelectorAll("article h2[id], article h3[id], article h4[id]"))
      .filter((h) => linkById.has(h.id));

    const TOC_ACTIVE = ["!text-brand", "!border-brand", "font-medium"];
    let active = null;
    function setActive(id) {
      if (active === id) return;
      if (active) {
        const prev = linkById.get(active);
        if (prev) prev.classList.remove(...TOC_ACTIVE);
      }
      active = id;
      if (id) {
        const next = linkById.get(id);
        if (next) next.classList.add(...TOC_ACTIVE);
      }
    }

    // Observe each heading. The `rootMargin` shrinks the viewport from
    // the top and bottom so we get a stable "above the fold" band that
    // tracks reading position rather than the whole page.
    const visible = new Set();
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) visible.add(e.target.id);
          else visible.delete(e.target.id);
        });
        // Pick whichever visible heading appears earliest in document order.
        if (visible.size > 0) {
          const earliest = headings.find((h) => visible.has(h.id));
          if (earliest) setActive(earliest.id);
        }
      },
      { rootMargin: "-80px 0px -75% 0px", threshold: 0 },
    );
    headings.forEach((h) => observer.observe(h));

    // Initial state — whatever heading is highest above the fold.
    const initial = headings.find((h) => h.getBoundingClientRect().top >= 80) || headings[0];
    if (initial) setActive(initial.id);
  })();

  // ===== Search =====
  const searchInput = document.getElementById("juicerdocs-search");
  const searchResults = document.getElementById("juicerdocs-search-results");
  if (searchInput && searchResults) {
    let index = null;
    let activeIndex = -1;

    async function ensureIndex() {
      if (index) return index;
      try {
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

    // Result item Tailwind classes — `last:border-b-0` removes the
    // bottom border from the final item; the !-prefixed active set
    // overrides the base color/bg cleanly via JS toggle.
    const RESULT_CLASS  = "block px-3.5 py-2 border-b border-border-soft last:border-b-0 no-underline text-fg hover:bg-brand-soft hover:text-brand";
    const RESULT_ACTIVE = ["!bg-brand-soft", "!text-brand"];
    function renderResults(matches, q) {
      searchResults.innerHTML = "";
      if (matches.length === 0) {
        searchResults.innerHTML = '<div class="px-3.5 py-2 text-sm text-muted">No matches.</div>';
      } else {
        for (let i = 0; i < matches.length; i++) {
          const r = matches[i];
          const a = document.createElement("a");
          a.className = RESULT_CLASS;
          a.href = r.url;
          a.dataset.idx = i;
          const title = document.createElement("span");
          title.className = "block font-medium";
          title.textContent = r.title || r.url;
          const snip = document.createElement("span");
          snip.className = "block text-xs opacity-70 mt-0.5 truncate";
          snip.textContent = snippet(r.content || r.summary || "", q);
          a.appendChild(title);
          a.appendChild(snip);
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
      const items = searchResults.querySelectorAll("a");
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
      items.forEach((it, i) => {
        if (i === activeIndex) it.classList.add(...RESULT_ACTIVE);
        else                   it.classList.remove(...RESULT_ACTIVE);
      });
    });
    document.addEventListener("click", (e) => {
      if (!searchInput.contains(e.target) && !searchResults.contains(e.target)) {
        searchResults.classList.add("hidden");
      }
    });
  }
})();
