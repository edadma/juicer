---
title: Theming
summary: Per-site overrides for theme colours, fonts, sizing, and layout — without forking the theme.
weight: 50
---

The bundled themes (`juicerdocs`, `juicerblog`, `juicerchurch`,
`juicercafe`, `juicerlanding`, `juicerportfolio`) are designed so a
site can rebrand and retune them without touching theme source. There
are three layers of override — use whichever fits the change you
want to make.

## Layer 1 — palette and token overrides via `[juicerXxx]`

Each theme reads a `[juicerXxx]` table from `site.toml` (where `Xxx`
is the theme's name) and emits an inline `<style>` block in
`<head>`. CSS custom properties get overridden on `:root` (light) or
`[data-theme="dark"]` (dark). The bundled CSS file is unchanged
across every site that uses the theme — only the small inline block
differs.

This is the cheapest, fastest theming layer — use it for colour
adjustments, font swaps, prose-column widening.

### Palette tokens (all four themes)

| Key            | CSS variable     | What |
|----------------|------------------|------|
| `brand`        | `--brand`        | Primary brand colour. Hero accents, link underlines, focus rings. |
| `brandStrong`  | `--brand-strong` | Darker variant for emphasised text + headings on light bg. |
| `brandSoft`    | `--brand-soft`   | Tinted background for active states, badges, pull quotes. |
| `accent`       | `--accent`       | Secondary accent colour (often warm) for CTAs and highlights. |
| `leaf`         | `--leaf`         | Tertiary "alive / fresh" colour — recurring-event chips, success states. |

For each light token there is a dark variant: `brandDark`,
`brandStrongDark`, `brandSoftDark`, `accentDark`, `leafDark`. Set them
to override the theme's hardcoded dark palette without leaking into
light mode.

### Typography tokens

| Key         | CSS variable    | What |
|-------------|-----------------|------|
| `fontSans`  | `--font-sans`   | Body / UI sans stack |
| `fontSerif` | `--font-serif`  | Serif stack (cafe / church / blog / portfolio — juicerdocs and juicerlanding are sans-only) |
| `fontMono`  | `--font-mono`   | Code / monospace stack |

Pass a full CSS font stack including fallbacks — e.g.
`fontSans = "'Inter', system-ui, sans-serif"`.

### Sizing tokens

The chrome-sizing surface differs by theme — each exposes the tokens
it actually uses.

For `juicercafe`, `juicerchurch`, `juicerblog`, `juicerlanding`,
`juicerportfolio`:

| Key        | CSS variable    | What |
|------------|-----------------|------|
| `measure`  | `--measure`     | Prose column max-width (default `38rem`; juicerlanding `65ch`; juicerportfolio `62ch`) |
| `gutter`   | `--gutter`      | Outer padding around content blocks |
| `radiusLg` | `--radius-lg`   | Large border-radius (cards, hero panels) |

For `juicerdocs`:

| Key          | CSS variable     | What |
|--------------|------------------|------|
| `contentMax` | `--content-max`  | Prose column max-width (default `48rem`) |
| `sidebarW`   | `--sidebar-w`    | Left sidebar width (default `16rem`) |
| `railW`      | `--rail-w`       | Right TOC rail width (default `18rem`) |
| `radiusLg`   | `--radius-lg`    | Large border-radius |

### Example — quietly retune juicercafe to a teal palette

```toml
[juicercafe]
brand           = "#0f766e"   # teal-700
brandStrong     = "#134e4a"   # teal-900
brandSoft       = "#ccfbf1"   # teal-100
accent          = "#7c3aed"   # violet-600
brandDark       = "#5eead4"   # teal-300 — lifted for dark bg
brandStrongDark = "#99f6e4"   # teal-200
brandSoftDark   = "rgba(94, 234, 212, 0.18)"
accentDark      = "#a78bfa"   # violet-400
fontSerif       = "'Source Serif 4', Georgia, serif"
measure         = "42rem"
```

## Layer 2 — `customCSS`: drop your own stylesheet alongside the theme

For overrides that don't fit a CSS variable (custom selectors, new
animations, third-party widgets), drop a stylesheet under
`static/site.css` (or any name) and list it in `site.toml`:

```toml
customCSS = ["site.css"]
```

The theme's `<head>` partial emits each entry as a `<link
rel="stylesheet">` **after** the bundled theme CSS, so anything in
your file overrides anything in the theme. Multiple entries load in
the order listed:

```toml
customCSS = ["fonts.css", "branding.css", "site.css"]
```

This is the right layer for:

- Loading a `@font-face` block for a custom font (then point
  `[juicerXxx] fontSerif` at it).
- Adding entire new widgets that the theme doesn't ship.
- Vendor / third-party CSS (Mermaid, KaTeX skin, etc.) that has to
  load on every page.

Files referenced by `customCSS` are still site `static/` content;
they're copied verbatim during build.

## Layer 3 — replace a theme file outright

When a site puts a file in `static/` with the same path as one the
theme ships, the **site copy wins** during the build. So a site can
ship its own `static/juicercafe.css` to fully replace the theme's
stylesheet (verified by the `should ship theme static/ files; site
static/ overwrites on path collision` build test).

Use this layer sparingly — you lose theme updates for any file you
replace. Layers 1 and 2 cover almost every customisation need; reach
for Layer 3 only when you need to fork.

## Site-wide chrome keys

Top-level `site.toml` keys, distinct from the per-theme `[juicerXxx]`
tables. Every theme reads them.

| Key                     | Type      | Default | What |
|-------------------------|-----------|---------|------|
| `favicon`               | `String`  | unset   | Path used in `<link rel="icon">`. Browser infers MIME from extension. |
| `hideJuicerCredit`      | `Boolean` | `false` | Set `true` to omit the "Built with juicer" footer line. |
| `repoURL`               | `String`  | unset   | Git host URL. juicerdocs surfaces a topbar GitHub icon and a homepage "Star on GitHub" button when set; other themes ignore. |
| `customCSS`             | `[String]`| unset   | List of stylesheet paths to load after the bundled theme CSS — see Layer 2 above. |
| `authorsPath`           | `String`  | `/authors/` | URL prefix for the team listing + per-author archives. See [Template data → authorsPath](/reference/template-data/#renaming-the-url-prefix-authorspath). |
