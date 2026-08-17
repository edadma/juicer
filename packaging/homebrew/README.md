# Homebrew packaging

`juicer.rb` is the formula, with the version and the three checksums left as `REPLACE_*` markers so
that this copy is not a place a release has to remember to bump.

**A release includes a formula update.** juicer is a tool, not a library — nobody depends on it from
a build file, they install it and run it — so `brew install` is how it actually reaches anyone, and
a release whose formula still names the previous version leaves every user on the old one.

## Cutting a formula for a release

```sh
tools/brew-formula.sh 0.3.0 -o /tmp/juicer.rb
```

That fetches the release's three binaries, hashes them, and fills in the template. Homebrew pins each
download by SHA256 and GitHub does not publish those, so they have to come from the assets
themselves. The release must already carry them — the "Release binaries" workflow attaches them when
a release is published.

The release workflow runs the script itself and pushes the result, so running it by hand is for a
release made outside that path, or for checking what the workflow will produce.

## The tap

`edadma/homebrew-tap` — the same tap that carries texish and the Roamer, Caldera and Asteroids casks.
The formula goes at `Formula/juicer.rb`; that is the whole of it, Homebrew needs nothing else.

```sh
brew tap edadma/tap
brew trust edadma/tap
brew install edadma/tap/juicer
brew test juicer
```

`brew trust` is needed since Homebrew began refusing to load anything from an untrusted third-party
tap.

**Homebrew on the development machine belongs to the `work` account**, so all of the above is run
from a `work` shell. Running it as another user fails partway and leaves files that break the *next*
brew command, with an error naming something unrelated. Pushing the formula needs none of that — a
tap is an ordinary git repository.

## Dependencies

- **libuv** is linked into the binary — Scala Native's runtime binds it. `otool -L` on the built
  binary shows it as the only non-system library.
- **sass** and **esbuild** are shelled out to by the asset pipeline, and are deliberately not
  vendored: `AssetBuilderBackend.scala` explains that an author should get the current versions of
  both rather than whatever juicer was built against.

  **Neither is a dependency of this formula, and adding one would be a mistake.** What the formula
  ships is the Scala Native binary, where `newAssetBuilderBackend()` is
  `AssetBuilderBackend.Unavailable` — the pipeline never invokes either tool and SCSS and JS entries
  degrade to verbatim copies. Depending on them installs two tools the shipped binary cannot use.

  It is also actively harmful for `sass`: there is no `sass` in homebrew-core, so `depends_on "sass"`
  resolves to `sass/sass/sass`, which depends on `dart-lang/dart/dart`. Homebrew trusts only
  homebrew-core and homebrew-cask by default, so an install of juicer failed outright with *"Refusing
  to load formula dart-lang/dart/dart from untrusted tap"* — and trusting the taps then builds a Dart
  SDK. The core formula that provides the `sass` executable is **`dart-sass`** (bottled, no Dart SDK),
  and that is the one to name here if the pipeline is ever promoted to Native.

## The test block

`brew test juicer` builds the smallest thing that is still a site — a `site.toml`, one markdown page
with frontmatter, and the one layout that renders a section index — and checks the prose came through
the template. That covers the whole path the tool exists for: walk the tree, parse the frontmatter,
render the markdown, apply a squiggly template, write the file.

It deliberately does **not** use a theme, so the test stays independent of any theme repository.
