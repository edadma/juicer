# Design: third-party themes

Status: **proposal** (not yet implemented). Written 2026-05-26.

How juicer goes from "themes are directories vendored in a site" to "themes
are installable artifacts published by third parties, building on a shared
base." The goal is an ecosystem where someone can publish a theme, a user
can install it with one command, and it works — without forking juicer or
hand-copying a base theme.

This respects [ROADMAP](../ROADMAP.md) principle #2 (*no scope creep into
module registries*): the design leans on **git + a bundled base + explicit
sources**, not a hosted package index. There is no server to run.

---

## 1. Where we are today

The plumbing for "a theme is a git repo you install" already exists:

- **`juicer theme add <git-url>`** (`App.scala:themeAdd`) clones a repo — or a
  `--subdir` of a multi-theme repo, pinned to `--ref` — into
  `<src>/<themeDir>/<name>`, and records **`.juicer-theme.toml`** (url, ref,
  subdir) inside the installed theme.
- **`juicer theme upgrade [<name>]`** (`App.scala:themeUpgrade`) re-fetches one
  or all installed themes from their recorded `.juicer-theme.toml`.
- **`theme.toml inherits = [...]`** lets a theme depend on other themes;
  `resolveThemeChain` (`Process.scala`) walks the site's `theme` list plus each
  theme's `inherits` into a single depth-first, dedup-keep-first lookup chain,
  with cycle detection.
- **Precedence is already ecosystem-correct:** `site files > first theme > … >
  last theme > inherited bases`. A user overrides any partial / shortcode /
  layout / static asset by shipping a same-named file higher in the chain —
  no fork required.

The git clone itself is platform-specific (`jvm/.../git_clone.scala`; JS/Native
stub with a clear message), and the cross-platform driver only resolves paths.

### What's missing

1. **Nothing ships with the binary.** There are no bundled resources and no
   `new`/scaffold command. The "bundled" themes (`juicerdocs`, `juicercommon`,
   …) exist only as part of *this repo's docs site* (`docs/themes/`). A fresh
   install of juicer has **zero themes**.

2. **`theme add` fetches one repo, not its dependencies.** If an installed
   theme's `theme.toml` declares `inherits = ["juicercommon"]`, `juicercommon`
   is not fetched, and `resolveThemeChain` fails the build (it validates that
   inherited themes exist).

3. **A name in `inherits` has no source.** `inherits = ["juicercommon"]` is a
   name, not a URL. juicer has no way to turn that name into something to fetch.

4. **`theme.toml` carries only `inherits`.** No identity, version, license, or
   engine-compatibility info for discovery or safety.

### The linchpin: `juicercommon`

The namespace-standardization work made **every** bundled theme
`inherits = ["juicercommon"]`, and third-party themes will want the same (it's
where the shared `seo`, dark-mode scripts, `hreflang`, and the
note/tip/tabs/github/… shortcodes live). So `juicercommon` is now a hard
dependency of the whole theme ecosystem — and it currently ships nowhere a
user can reach. **Solving its distribution is the one change that unblocks
everything else.**

---

## 2. Proposed architecture

### 2.1 Layered theme search path

Today a theme name resolves only against the site's `themeDir`. Generalize
`resolveThemeChain` to search an ordered list of **roots**, first match wins:

1. **Site `themeDir/`** — project-local; highest priority. Lets a site vendor
   or override any theme by dropping it here.
2. **Global cache** `~/.juicer/themes/<name>/` — installed once, reused across
   every site on the machine. (Override with `JUICER_HOME`.)
3. **Builtins** — themes bundled into the juicer binary (§2.2). At minimum
   `juicercommon`; ideally the whole official set.

This makes `inherits = ["juicercommon"]` resolve for *any* theme, third-party
included, with no install step — `juicercommon` is found as a builtin.

`resolveThemeChain` already takes the site theme names and computes roots via
`themeRootPath`. The change: `themeRootPath` consults the search path in order
instead of only `<src>/<themeDir>/<name>`. Precedence within a resolved chain
is unchanged; the search path only governs *where a given theme name is found*,
and earlier roots win so a site can shadow a builtin by vendoring it.

Resolution rule (precise): for a theme **name**, pick the first root that
contains a directory of that name. A site copy shadows a global-cache copy,
which shadows a builtin. The *contents* of that one directory are then used;
roots are not merged per-theme (no half-site/half-builtin theme).

### 2.2 Builtin themes bundled in the binary

The base theme(s) must be available on all three targets (JVM, JS, Native)
**without** a runtime filesystem or classpath dependency — Native and JS have
no classpath. The cross-platform-safe approach:

> **Generate a Scala source file** (`BuiltinThemes.scala`, in `shared/`) from
> the theme files at build time. Each builtin theme becomes a
> `Map[String /*relative path*/, String /*file contents*/]`, compiled into the
> binary on every target.

- An sbt `sourceGenerators` task reads `modules/builtin-themes/<theme>/**` and
  emits the map. (Text files inline as string literals; any binary asset —
  logos — base64-encodes, or builtins stay text-only and ship binaries via the
  git-installed path.)
- At build time, a builtin theme is either materialized into a temp dir or read
  directly through the same `Path`-like accessor the rest of the resolver uses.
  Simplest first cut: when a builtin is needed, write it into the global cache
  (`~/.juicer/themes/<name>/`) once, then resolve normally.

Mind the JVM 64 KB method/constant limits (we hit this in nex's preamble) — a
large theme map should be split across several `private def`s or objects.

`juicercommon` is the must-have builtin. Bundling `juicerdocs` et al. too gives
every user a working start and enables a `juicer new <theme>` scaffold later.

### 2.3 Transitive install

`theme add` should fetch a theme **and** the `inherits` it can't already
resolve. After cloning, read the theme's `theme.toml`; for each `inherits`
entry not satisfiable from the search path (§2.1), fetch it too, recursively.
Detect cycles (reuse the gray-set logic from `resolveThemeChain`).

`theme upgrade` re-resolves the dependency set the same way, so a base bump
propagates.

### 2.4 Naming a dependency's source

To fetch a dep by name, juicer needs name → source. Avoid a hosted registry
(principle #2). Resolution order for a name in `inherits`:

1. **Already resolvable** from the search path (builtin / global cache / site)
   → use it; fetch nothing. (`juicercommon` always hits here.)
2. **Declared source** in the depending theme's `theme.toml`:

   ```toml
   inherits = ["juicercommon", "fancybase"]

   [[require]]
   name   = "fancybase"
   git    = "https://github.com/someone/fancybase"
   ref    = "v2.1.0"     # optional; default repo HEAD
   subdir = ""           # optional; for multi-theme repos
   ```

3. **Otherwise** → fail with a clear message: *"theme 'x' inherits 'fancybase'
   but no source is known; add a `[[require]]` block or install it manually."*

Builtin/official names need no `[[require]]`. Only out-of-tree deps do. This
keeps the common case (`inherits = ["juicercommon"]`) zero-config while still
supporting arbitrary third-party dependency graphs.

### 2.5 `theme.toml` metadata (expansion)

v1 ships only `inherits`. For an ecosystem, add optional identity + a
compatibility gate. All fields optional except where a feature needs them:

```toml
name        = "fancydocs"
version     = "1.4.0"
description = "A docs theme with fancy callouts."
author      = "someone"
license     = "MIT"
homepage    = "https://github.com/someone/fancydocs"

inherits = ["juicercommon"]

# Engine-compatibility gate. Build fails fast with a clear message if the
# running juicer is too old, instead of mis-rendering (see §4).
requires = { juicer = ">=0.2" }

# Optional: capabilities the theme assumes (see §4).
capabilities = ["images", "deferred-shortcodes"]

[[require]]            # sources for non-builtin inherited themes (§2.4)
name = "fancybase"
git  = "https://github.com/someone/fancybase"
```

`name`/`version` are informational today (the install directory name is still
authoritative for resolution) but feed `theme upgrade` output, a future
`theme list`, and the lockfile.

### 2.6 Lockfile

For reproducible builds, `juicer theme add`/`upgrade` write a top-level
**`juicer.lock`** (TOML) pinning the exact resolved commit SHA of every
installed theme and its transitive deps:

```toml
[[theme]]
name   = "fancydocs"
git    = "https://github.com/someone/fancydocs"
ref    = "v1.4.0"
commit = "9f3a…"        # exact SHA actually fetched
subdir = ""

[[theme]]
name   = "fancybase"
git    = "https://github.com/someone/fancybase"
commit = "c1d2…"
```

A plain `juicer build` does **not** fetch; it uses what's installed. `theme
add`/`upgrade` are the only commands that touch the network, and they update
the lock. This is a strict superset of today's per-theme `.juicer-theme.toml`
(which can stay as the in-theme record, or be subsumed by the lock).

### 2.7 Distributing the official themes

`theme add --subdir` already supports the natural layout: a single
`edadma/juicer-themes` repo with `juicercommon/`, `juicerdocs/`,
`juicerblog/`, … as subdirectories.

```bash
juicer theme add --subdir juicerdocs https://github.com/edadma/juicer-themes
```

The builtins (§2.2) are generated from a checkout of that same tree, so
"builtin" and "installable" are the *same* theme sources — no drift. This
repo's own `docs/` site can keep vendoring under `docs/themes/` for
development, or consume the published themes like any other user.

### 2.8 Author conventions (documented, not enforced)

The namespace-standardization work surfaced rules every theme author must
follow:

- **Namespace your CSS classes** with your theme's name: `fancydocs-sidebar`,
  not `sidebar`. The shared shortcodes inherited from `juicercommon` already
  emit the reserved **`juicer-`** prefix (`juicer-callout`, `juicer-btn`, …);
  your theme's CSS must style those classes if you use those shortcodes.
- **Never write `:not([class*="prefix-"])` guards that assume a class prefix.**
  This is exactly what broke when shortcode classes were renamed
  `juicerdocs-` → `juicer-`: a content-link rule
  `.x-content a:not([class*="x-"])` stopped excluding the renamed buttons and
  painted them `var(--brand)` (invisible on the pink button). If you must
  exclude component anchors inside content, match the stable substring
  (`[class*="juicer"]` covers both your own `x-` classes and the shared
  `juicer-` ones), or better, scope the positive rule instead of excluding.
- **Declare `requires`/`capabilities`** for any engine feature you depend on
  (image variants, deferred `[~ ~]` shortcodes, etc.) so the gate in §4 can
  protect users.

---

## 3. Suggested order

1. **Builtin `juicercommon` + layered search path** (§2.1–2.2). Unblocks the
   ecosystem on its own: third-party themes can `inherits = ["juicercommon"]`
   and build. *Highest leverage; everything else depends on it.*
2. **Transitive install + `[[require]]` sources** (§2.3–2.4). One-command
   install of a theme with out-of-tree bases.
3. **`theme.toml` metadata + `requires` gate** (§2.5, §4). Safety + discovery.
4. **Lockfile** (§2.6). Reproducibility.
5. **Publish `edadma/juicer-themes` + author/conventions docs** (§2.7–2.8).
   Optional `juicer new <theme>` scaffold and `juicer theme list`.

Step 1 alone is shippable and valuable.

---

## 4. Capability / version gating (why)

Themes depend on engine features that aren't visible in their file list:
the `imageVariants` / `srcset` builtins, the immediate `[= =]` vs deferred
`[~ ~]` shortcode passes, the `asset` builtin, `markdownify`, the
partial/shortcode lookup contract. The `figure` shortcode that broke during
the juicercommon merge is the canonical example — it silently assumed the
image-variant builtins existed.

`requires = { juicer = ">=0.2" }` and an optional `capabilities = [...]` list
let `build` (and `theme add`) **refuse** an incompatible theme with a precise
message instead of producing a broken site. The engine advertises a version
and a capability set; the resolver checks each theme in the chain before
rendering. Keep the capability vocabulary small and concrete (named after real
engine features), not a free-for-all.

---

## 5. Cross-platform notes

- `theme add`/`upgrade` shell out to `git`, so they're JVM-only; JS/Native
  already stub `installFromGit` with a clear message. That's fine — installing
  is a dev-machine action. **Building** with already-installed/builtin themes
  must work on all three targets.
- Builtins therefore can't rely on JVM classpath resources. Use the generated-
  Scala-source approach (§2.2) so theme content is compiled into every target.
- `~/.juicer/` home resolution needs a cross-platform accessor (env `HOME` /
  `USERPROFILE`, overridable via `JUICER_HOME`); keep it in `shared/` with thin
  platform shims if needed.

---

## 6. Open questions

- **Bundle just `juicercommon`, or the whole official set?** Just the base is
  the minimum; the full set enables `juicer new` and offline starts at the cost
  of binary size.
- **Keep `.juicer-theme.toml` per theme, or fold it entirely into
  `juicer.lock`?** The lock is the superset; the per-theme file is handy when a
  theme dir is copied around without the lock.
- **Global cache keying** — `<name>/` (simple, one version per machine) vs
  `<name>@<ref>/` (multiple versions, needed if two sites pin different refs).
  Start simple; revisit if it bites.
- **Should `build` ever auto-install a missing builtin into the cache, or only
  read it in-memory?** In-memory is cleaner but means materializing builtin
  static assets to the output some other way.
