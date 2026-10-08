#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
COMPILE_SDK="${ANDROID_COMPILE_SDK:-34}"
OUTPUT="${1:-$ROOT/build/PalpiGO.apk}"

: "${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_SDK_ROOT}"
: "${PALPIGO_LEGACY_KEYSTORE:?Set PALPIGO_LEGACY_KEYSTORE for Android versions below API 28}"
: "${PALPIGO_LEGACY_KEY_ALIAS:?Set PALPIGO_LEGACY_KEY_ALIAS}"
: "${PALPIGO_LEGACY_KEYSTORE_PASSWORD:?Set PALPIGO_LEGACY_KEYSTORE_PASSWORD}"
: "${PALPIGO_LEGACY_KEY_PASSWORD:?Set PALPIGO_LEGACY_KEY_PASSWORD}"
: "${PALPIGO_KEYSTORE:?Set PALPIGO_KEYSTORE to the new release keystore outside the repository}"
: "${PALPIGO_KEY_ALIAS:?Set PALPIGO_KEY_ALIAS}"
: "${PALPIGO_KEYSTORE_PASSWORD:?Set PALPIGO_KEYSTORE_PASSWORD}"
: "${PALPIGO_KEY_PASSWORD:?Set PALPIGO_KEY_PASSWORD}"
: "${PALPIGO_SIGNING_LINEAGE:?Set PALPIGO_SIGNING_LINEAGE to the verified signer lineage}"

TOOLS="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
PLATFORM="$ANDROID_HOME/platforms/android-$COMPILE_SDK/android.jar"
for tool in aapt2 d8 zipalign apksigner; do
    test -x "$TOOLS/$tool" || { echo "Missing Android tool: $TOOLS/$tool" >&2; exit 1; }
done
test -f "$PLATFORM" || { echo "Missing Android platform: $PLATFORM" >&2; exit 1; }
test -f "$PALPIGO_KEYSTORE" || { echo "Signing keystore not found" >&2; exit 1; }
test -f "$PALPIGO_LEGACY_KEYSTORE" || { echo "Legacy signing keystore not found" >&2; exit 1; }
test -f "$PALPIGO_SIGNING_LINEAGE" || { echo "Signing lineage not found" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$WORK/dex"

"$TOOLS/aapt2" compile --dir "$ROOT/android-apk-src/res" -o "$WORK/resources.zip"
"$TOOLS/aapt2" link \
    -o "$WORK/base.apk" \
    -I "$PLATFORM" \
    --manifest "$ROOT/android-apk-src/AndroidManifest.xml" \
    --java "$WORK/generated" \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    "$WORK/resources.zip"

mapfile -t JAVA_SOURCES < <(find "$ROOT/android-apk-src/src" "$WORK/generated" -name '*.java' -type f)
javac -source 8 -target 8 -cp "$PLATFORM" -d "$WORK/classes" "${JAVA_SOURCES[@]}"
mapfile -t CLASS_FILES < <(find "$WORK/classes" -name '*.class' -type f)
"$TOOLS/d8" --min-api 26 --lib "$PLATFORM" --output "$WORK/dex" "${CLASS_FILES[@]}"

cp "$WORK/base.apk" "$WORK/unsigned.apk"
(cd "$WORK" && zip -q -j unsigned.apk dex/classes.dex)
"$TOOLS/zipalign" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

mkdir -p "$(dirname "$OUTPUT")"
"$TOOLS/apksigner" sign \
    --lineage "$PALPIGO_SIGNING_LINEAGE" \
    --ks "$PALPIGO_LEGACY_KEYSTORE" \
    --ks-key-alias "$PALPIGO_LEGACY_KEY_ALIAS" \
    --ks-pass env:PALPIGO_LEGACY_KEYSTORE_PASSWORD \
    --key-pass env:PALPIGO_LEGACY_KEY_PASSWORD \
    --v1-signing-enabled false \
    --next-signer \
    --ks "$PALPIGO_KEYSTORE" \
    --ks-key-alias "$PALPIGO_KEY_ALIAS" \
    --ks-pass env:PALPIGO_KEYSTORE_PASSWORD \
    --key-pass env:PALPIGO_KEY_PASSWORD \
    --signer-for-min-sdk-version 28 \
    --signer-lineage "$PALPIGO_SIGNING_LINEAGE" \
    --rotation-min-sdk-version 28 \
    --out "$OUTPUT" \
    "$WORK/aligned.apk"
"$TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT"
echo "Built signed APK: $OUTPUT"
