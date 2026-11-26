#!/usr/bin/env bash
# Verifies that every APK in a directory is properly signed with ONE expected certificate.
#
#   Usage: verify_apk_signature.sh <directory> <sha256-hex-or-colon-fingerprint>
#
# apksigner performs the full cryptographic verification of the APK Signature Scheme v2/v3
# blocks; the script then compares the signer certificate fingerprint, so an APK that was built
# with a regenerated or foreign key can never be published by mistake.
# Exit code 0 = every APK is good, 1 = at least one APK was refused.
set -euo pipefail

DIR="${1:?usage: verify_apk_signature.sh <directory> <sha256>}"
EXPECTED="$(printf '%s' "${2:?missing expected SHA-256 fingerprint}" | tr -d ':' | tr 'A-F' 'a-f')"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
if [ -z "$APKSIGNER" ]; then
  echo "::error::apksigner not found under ${SDK:-<no Android SDK>}/build-tools"
  exit 1
fi

shopt -s nullglob
APKS=( "$DIR"/*.apk )
if [ "${#APKS[@]}" -eq 0 ]; then
  echo "::error::no APKs found in $DIR"
  exit 1
fi

status=0
for APK in "${APKS[@]}"; do
  # apksigner exits non-zero when the APK is unsigned or its signature does not verify.
  if ! CERTS="$("$APKSIGNER" verify --print-certs "$APK" 2>&1)"; then
    echo "::error title=Invalid signature::$(basename "$APK") did not pass 'apksigner verify'"
    printf '%s\n' "$CERTS" | head -5
    status=1
    continue
  fi
  # `|| true`: an unexpected apksigner output format must not abort under `set -e` + `pipefail`.
  ACTUAL="$(printf '%s\n' "$CERTS" | grep -m1 -i 'certificate SHA-256 digest' | awk '{print $NF}' | tr -d ':' | tr 'A-F' 'a-f' || true)"
  SCHEMES="$(printf '%s\n' "$CERTS" | grep -i '^verif' \
    | sed -E 's/^Verified (using|with) //; s/ scheme \(.*\)//; s/ \(.*\)//' | paste -sd'; ' - || true)"
  if [ -z "$ACTUAL" ]; then
    echo "::error title=Unknown signer::could not read a certificate SHA-256 digest for $(basename "$APK")"
    status=1
  elif [ "$ACTUAL" != "$EXPECTED" ]; then
    echo "::error title=Wrong signing key::$(basename "$APK") is signed with certificate $ACTUAL but $EXPECTED was expected. Never regenerate the keystores (docs/KEYSTORE.md)."
    status=1
  else
    echo "$(basename "$APK"): OK ($SCHEMES, certificate SHA-256 $ACTUAL)"
  fi
done
exit "$status"
