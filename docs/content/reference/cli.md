---
title: CLI
summary: The juicer command-line tool — build, serve, config.
weight: 10
---

Juicer's CLI has three subcommands: `build`, `serve`, and `config`.

## Global options

| Flag                    | What |
|-------------------------|------|
| `-b`, `--baseurl <URL>` | Override `baseURL` from the config |
| `-c`, `--config <name>` | Pick a baseline config (`simple` / `standard` / `norme`); default is `standard` |
| `-h`, `--help`          | Print usage and exit |
| `-v`, `--verbose`       | Print step-by-step build progress |
| `--version`             | Print the version and exit |

## `build`

Build the site to `<src>/public/` (or `-d <dst>` to override).

```bash
sbt 'juicerJVM/run build -s . -d public'
```

| Flag                  | What |
|-----------------------|------|
| `-s`, `--source <p>`  | Site source directory (default: `.`) |
| `-d`, `--dest <p>`    | Output directory (default: `<src>/<publicDir>`) |
| `-D`, `--drafts`      | Include `draft: true` pages |
| `-F`, `--future`      | Include pages whose parsed `date:` is past `now` (skipped by default) |

## `serve`

Build, then start a local HTTP server.

```bash
sbt 'juicerJVM/run serve -s . -p 8080'
```

| Flag                  | What |
|-----------------------|------|
| `-s`, `--source <p>`  | Site source directory |
| `-d`, `--dest <p>`    | Output directory |
| `--host <h>`          | Bind host (default: `localhost`) |
| `-p`, `--port <p>`    | Listen port (default: `8080`) |
| `-D`, `--drafts`      | Include drafts |
| `-F`, `--future`      | Include future-dated pages |
| `-L`, `--live-reload` | Rebuild on source changes and reload browser tabs |

[= note =]
`serve` is currently JVM-only — `com.sun.net.httpserver` doesn't exist on Scala.js or Native. The build itself works on all three platforms; serving is the lone JVM exception.
[= /note =]

## `config`

Print the merged site config (after baseline + overlay + CLI overrides).

```bash
sbt 'juicerJVM/run config -s .'
```

Useful for debugging "why is my `htmlDir` doing that?" — the answer is right there in the printed config.
