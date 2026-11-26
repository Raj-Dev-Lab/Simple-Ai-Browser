#!/usr/bin/env bash
# Collects the distributable APKs into one folder, writes SHA256SUMS.txt and a Markdown table.
#
#   Usage: collect_apks.sh <outputs-dir e.g. app/build/outputs/sab> <out-dir e.g. dist> [summary-file]
#
# Debug APKs are mandatory; release APKs are only present when the signing secrets were configured.
# When the GitHub Actions env file is available, RELEASE_READY=true|false is exported for later
# steps (release note).
set -euo pipefail

SRC="${1:?usage: collect_apks.sh <outputs-dir> <out-dir> [summary-file]}"
OUT="${2:?missing out-dir}"
SUMMARY="${3:-}"
mkdir -p "$OUT"

shopt -s nullglob
DEBUG_APKS=( "$SRC"/debug/sab-debug-*.apk )
RELEASE_APKS=( "$SRC"/release/sab-release-*.apk )
if [ "${#DEBUG_APKS[@]}" -eq 0 ]; then
  echo "::error::no debug APKs found in $SRC/debug"
  exit 1
fi

cp "${DEBUG_APKS[@]}" "$OUT/"
if [ "${#RELEASE_APKS[@]}" -gt 0 ]; then
  cp "${RELEASE_APKS[@]}" "$OUT/"
  RELEASE_READY=true
else
  RELEASE_READY=false
fi
if [ -n "${GITHUB_ENV:-}" ]; then
  echo "RELEASE_READY=$RELEASE_READY" >> "$GITHUB_ENV"
fi

(cd "$OUT" && sha256sum ./*.apk > SHA256SUMS.txt)

if [ -n "$SUMMARY" ]; then
  {
    echo "## APKs"
    echo ""
    echo "| File | Size | SHA-256 (first 16) |"
    echo "|---|---|---|"
    for f in "$OUT"/*.apk; do
      printf '| `%s` | %s | `%s…` |\n' \
        "$(basename "$f")" \
        "$(numfmt --to=iec --format='%.1f' "$(stat -c%s "$f")")" \
        "$(sha256sum "$f" | cut -c1-16)"
    done
    echo ""
  } >> "$SUMMARY"
fi
echo "collected $(find "$OUT" -maxdepth 1 -name '*.apk' | wc -l) APKs in $OUT (release ready: $RELEASE_READY)"
