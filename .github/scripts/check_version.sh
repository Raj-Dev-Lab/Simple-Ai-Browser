#!/usr/bin/env bash
# Version-control guard for releases (runs on v* tags, before anything is built).
# Fails the run when
#   - the tag does not equal versionName in app/build.gradle.kts,
#   - CHANGELOG.md has no section for that version, or
#   - versionCode was not raised compared with the previous v* tag
#     (Android refuses to install a build whose versionCode is lower than the installed one).
set -euo pipefail

GRADLE_FILE="app/build.gradle.kts"
VERSION_NAME="$(grep -Eo 'versionName = "[^"]+"' "$GRADLE_FILE" | head -1 | cut -d'"' -f2)"
VERSION_CODE="$(grep -Eo 'versionCode = [0-9]+' "$GRADLE_FILE" | head -1 | grep -Eo '[0-9]+')"
TAG="${GITHUB_REF_NAME#v}"

fail() { echo "::error::$*"; exit 1; }

[ -n "$VERSION_NAME" ] || fail "versionName not found in $GRADLE_FILE"
[ -n "$VERSION_CODE" ] || fail "versionCode not found in $GRADLE_FILE"
[ "$TAG" = "$VERSION_NAME" ] || fail "tag v$TAG does not match versionName \"$VERSION_NAME\" in $GRADLE_FILE"
grep -Eq "^## \[$TAG\]" CHANGELOG.md || fail "CHANGELOG.md has no \"## [$TAG]\" section"

PREV_TAG="$(git tag --list 'v*' --sort=-v:refname | grep -vFx "$GITHUB_REF_NAME" | head -1 || true)"
if [ -n "$PREV_TAG" ]; then
  PREV_CODE="$(git show "$PREV_TAG:$GRADLE_FILE" 2>/dev/null | grep -Eo 'versionCode = [0-9]+' | head -1 | grep -Eo '[0-9]+' || echo 0)"
  [ "$VERSION_CODE" -gt "$PREV_CODE" ] || fail "versionCode $VERSION_CODE is not greater than in $PREV_TAG ($PREV_CODE)"
fi

echo "version OK: tag $GITHUB_REF_NAME = versionName $VERSION_NAME, versionCode $VERSION_CODE (previous tag: ${PREV_TAG:-none})"
