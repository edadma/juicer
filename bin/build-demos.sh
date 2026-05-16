#!/usr/bin/env bash
#
# Build each per-theme demo site into <out>/themes/<theme>/demo/, where
# <out> is the docs site's output directory. Run this AFTER the main
# docs build so each demo's rendered output overwrites the placeholder
# `demo.md` page the docs build wrote to the same path.
#
# Usage:
#   bin/build-demos.sh [OUT_DIR] [SITE_BASE_URL]
#
# Defaults: OUT_DIR=_site, SITE_BASE_URL=/
#
# SITE_BASE_URL is the docs site's base URL. Each demo is rebased onto
# "${SITE_BASE_URL%/}/themes/<theme>/demo/" so its internal links resolve
# correctly when served from that sub-path.
#
# The docs site itself uses the juicerdocs theme, so its "Demo site"
# leaf for juicerdocs points at the docs site root (`/`) — there is no
# docs/demos/juicerdocs/. The hand-rolled docs example (the old
# examples/docs-site) lives at docs/demos/handrolled-docs/ and isn't
# wired in as any theme's demo.

set -euo pipefail

OUT="${1:-_site}"
SITE_BASE_URL="${2:-/}"

# Make OUT absolute so we can cd around freely.
case "$OUT" in
  /*) ABS_OUT="$OUT" ;;
  *)  ABS_OUT="$PWD/$OUT" ;;
esac

# Strip any trailing slash off SITE_BASE_URL so we can append cleanly.
BASE_TRIMMED="${SITE_BASE_URL%/}"

# Themes that have a docs/demos/<theme>/ source dir. juicerdocs is
# absent on purpose (the docs site itself is the demo).
THEMES=(juicerblog juicerstudy juicercafe juicerchurch juicerlanding juicerportfolio juicergallery juicerwiki)

for theme in "${THEMES[@]}"; do
  SRC="docs/demos/$theme"
  DST="$ABS_OUT/themes/$theme/demo"
  DEMO_BASE="$BASE_TRIMMED/themes/$theme/demo/"

  if [[ ! -d "$SRC" ]]; then
    echo "build-demos: skipping $theme (no $SRC)" >&2
    continue
  fi

  echo "==> Building $theme demo: $SRC -> $DST (baseURL=$DEMO_BASE)"
  sbt --error "juicerJVM/run build -s $SRC -d $DST -b $DEMO_BASE"
done

echo "==> All demos built."
