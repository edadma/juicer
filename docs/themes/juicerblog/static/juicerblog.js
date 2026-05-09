/* juicerblog — small client-side enhancements to server-rendered code blocks.
 *
 * The markdown library emits `<pre><code class="language-foo">…</code></pre>`
 * for fenced blocks. juicer's highlighter integration adds the inline
 * `<span class="hl-…">` colorization at build time, but two affordances are
 * easier to do at runtime against the live DOM:
 *
 *   1. **Language label.** Read the `language-X` class from each inner
 *      `<code>` and surface it as a `data-language="X"` attribute on the
 *      parent `<pre>` so CSS can render a small uppercase chip via ::before.
 *   2. **Copy button.** A button in the top-right corner of every `<pre>`
 *      that copies the code to the clipboard and gives a one-second visual
 *      confirmation.
 *
 * The whole file is ~50 LOC and idempotent — running it twice does no harm
 * because both passes set attributes / append children only when missing.
 *
 * Loaded via a defer'd <script> tag in partials/head.html, so the script
 * fires once after the parser is done with the body. No framework, no
 * dependencies, works without modules. */
(function () {
  if (window.__juicerblogCodeEnhanced) return;
  window.__juicerblogCodeEnhanced = true;

  function enhance() {
    var pres = document.querySelectorAll('.juicerblog-content pre');
    for (var i = 0; i < pres.length; i++) {
      var pre = pres[i];
      if (pre.dataset.juicerblogEnhanced) continue;
      pre.dataset.juicerblogEnhanced = '1';

      // (1) Language label — scan the inner <code>'s class list for a
      // `language-X` token and lift X onto the parent <pre>.
      var code = pre.querySelector('code[class*="language-"]');
      if (code) {
        var langClass = Array.prototype.find.call(
          code.classList,
          function (c) { return c.indexOf('language-') === 0; }
        );
        if (langClass) {
          var lang = langClass.slice('language-'.length);
          if (lang) pre.setAttribute('data-language', lang);
        }
      }

      // (2) Copy button — appended last so it sits at the end of the <pre>;
      // CSS positions it absolute. We grab the visible text from the inner
      // <code> (not pre.textContent) so the language label and button text
      // never accidentally land in the clipboard.
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'juicerblog-copy-btn';
      btn.setAttribute('aria-label', 'Copy code');
      btn.textContent = 'Copy';
      btn.addEventListener('click', function (e) {
        var srcCode = e.currentTarget.parentElement.querySelector('code');
        var text = srcCode ? srcCode.innerText : '';
        var done = function () {
          var b = e.currentTarget;
          b.textContent = 'Copied';
          b.classList.add('is-copied');
          setTimeout(function () {
            b.textContent = 'Copy';
            b.classList.remove('is-copied');
          }, 1200);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done, function () {
            // Clipboard API can fail (insecure context, denied permission,
            // older Safari). Silent fall-through to a flash-fail state so
            // the user doesn't sit looking at "Copy" wondering if they
            // missed.
            var b = e.currentTarget;
            b.textContent = 'Failed';
            setTimeout(function () { b.textContent = 'Copy'; }, 1500);
          });
        }
      });
      pre.appendChild(btn);
    }
  }

  /* Reading-progress bar — a 2px hairline glued to the top of the
   * viewport that fills left-to-right as the reader scrolls down a
   * single post. Only attaches on pages that contain a
   * `.juicerblog-post--dated` article (i.e., the single-post template
   * with post chrome — not section archives, not static pages, not
   * tag/author/date archives). The bar is created once on first scroll
   * and updated via rAF-throttled handler so big posts on slow devices
   * don't repaint per scroll event. */
  function attachReadingProgress() {
    var article = document.querySelector('.juicerblog-post--dated');
    if (!article) return;

    var bar = document.createElement('div');
    bar.className = 'juicerblog-reading-progress';
    bar.setAttribute('role', 'progressbar');
    bar.setAttribute('aria-hidden', 'true');
    document.body.appendChild(bar);

    var ticking = false;
    function update() {
      ticking = false;
      var rect = article.getBoundingClientRect();
      var winH = window.innerHeight || document.documentElement.clientHeight;
      // Progress is "how much of the article have we scrolled past":
      // 0% when the article top is still below the viewport top,
      // 100% when the article bottom has scrolled above the viewport top.
      // Clamped to [0, 1].
      var total = rect.height - winH;
      var passed = -rect.top;
      var pct = total > 0 ? Math.min(1, Math.max(0, passed / total)) : 0;
      bar.style.transform = 'scaleX(' + pct + ')';
    }
    function onScroll() {
      if (ticking) return;
      ticking = true;
      window.requestAnimationFrame(update);
    }
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll, { passive: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      enhance();
      attachReadingProgress();
    });
  } else {
    enhance();
    attachReadingProgress();
  }
})();
