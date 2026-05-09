---
title: juicerlanding
summary: The bundled single-page product / SaaS landing theme — frontmatter-driven section stack, slate / indigo / amber palette, no JavaScript framework.
weight: 70
---

`juicerlanding` is the landing-page sibling to [juicerdocs](/juicerdocs/) and [juicerblog](/juicerblog/). It's bundled with the juicer repo at `docs/themes/juicerlanding/`, so a clone of juicer comes with it out of the box. To use it on your own site, copy the directory or run:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerlanding
```

…and set `theme = "juicerlanding"` in your `site.toml`. A live demo lives at `examples/landing-site/` in the repo — run it with `sbt 'juicerJVM/run serve -s examples/landing-site -L'` to see every section on this page rendered against fictional product copy.

## What's in the box

A **single-page** product theme: one `_index.md` with `layout: home`, and the entire visible page is assembled from frontmatter blocks in `site.toml`. Each section is its own partial, gated on its own data block, so a site that doesn't ship (say) testimonials simply doesn't render that section — no skeletons, no empty headings.

- **Slate / indigo / amber palette** signalling privacy-tech / developer-tools, distinct from juicerdocs's teal-and-sage docs warmth.
- **Light + dark mode** driven by `[data-theme="dark"]` overrides on CSS custom properties; pre-paint apply script in `<head>` prevents the white flash on dark.
- **Theme-toggle button** in the topbar (and a GitHub icon link if you set `repoURL`).
- **Sticky topbar** with backdrop blur, optional secondary nav, optional always-visible CTA mirroring your hero call-to-action.
- **Conditional section stack** — nine sections, fixed order, each silently omitted if its frontmatter block is absent. (The order is the theme's; site authors don't need to know it.)
- **Native `<details>` accordion** for the FAQ — no JS, browser handles open/close.
- **Pricing tier highlight** — set `featured = true` on one row of `[[pricing]]` and that column gets the badge + ring + scale-up treatment.
- **Code-snippet frame** with the three-traffic-light header and an optional language chip — used by the hero (install one-liner) and the standalone code section.
- **Four-column footer** driven by `[[footerColumns]]` — brand blurb auto-generated from `title` + `tagline`, and you supply the link columns.
- **Generic `file.html` and `folder.html`** for secondary pages (changelog, privacy, about, /blog/) — same prose treatment as the rest of the bundled themes.
- **404 page** that doesn't depend on the rest of the chrome (so it works even if the topbar partial errors out).

## When to use it (and when not to)

Use juicerlanding if you're shipping an indie SaaS, an open-source library with a marketing front, a CLI / dev-tool that needs a homepage, or anything where the front page is doing most of the conversion work and the rest of the site (`/docs/`, `/changelog/`, `/blog/`) is supporting content rather than the main attraction.

Pick something else if you want:

- **Multi-page reference.** That's [juicerdocs](/juicerdocs/) — left rail, right TOC rail, prev/next nav, the whole docs apparatus.
- **A blog or essay site.** That's [juicerblog](/juicerblog/) — long-form prose treatment, author bylines, dated post archives.
- **A community / location site** (church, cafe, group). That's [juicerchurch](/juicerchurch/) or [juicercafe](/juicercafe/) — recurring events, hours widgets, places-to-find-us cards.

## What this section covers

The page below documents every section block, every frontmatter knob, and every `[juicerlanding]` palette token the theme reads from `site.toml`. The shared theming model (palette / customCSS / file-replacement layers) is in [Theming](/reference/theming/) and applies to juicerlanding identically to the other themes.
