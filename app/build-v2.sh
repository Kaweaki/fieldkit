#!/usr/bin/env bash
# FieldKit V2 build: the shared helper classes plus the v2 UI, in one command.
#
#   ANDROID_HOME=~/Android/Sdk KS=~/dev.keystore ./build-v2.sh
#
# The app that shipped as 2.8 has no source in this repository, so this builds
# the V2 package (se.blimark.fieldkit.v2) next to it: same palette, same tile
# grid, and every tile backed by a class that is exercised by the JVM self test.
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$APP_DIR"

: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK root}"

BUILD_TOOLS="${BUILD_TOOLS:-35.0.0}"
PLATFORM="${PLATFORM:-android-36}"
MIN_SDK="${MIN_SDK:-21}"
TARGET_SDK="${TARGET_SDK:-36}"
VERSION_CODE="${VERSION_CODE:-21}"
VERSION_NAME="${VERSION_NAME:-2.9}"
OUT="${OUT:-build/FieldKit-V2.apk}"
KS="${KS:-}"

TOOLS="$ANDROID_HOME/build-tools/$BUILD_TOOLS"
ANDROID_JAR="$ANDROID_HOME/platforms/$PLATFORM/android.jar"

[ -x "$TOOLS/aapt2" ] || [ -x "$TOOLS/aapt2.exe" ] || { echo "no aapt2 in $TOOLS" >&2; exit 2; }
[ -f "$ANDROID_JAR" ] || { echo "no $ANDROID_JAR" >&2; exit 2; }
[ -d v2/src ] || { echo "no v2/src next to build-v2.sh" >&2; exit 2; }

# Git Bash on Windows needs the .bat shims for the Java tools.
shim() { if [ -x "$TOOLS/$1" ]; then echo "$TOOLS/$1"; else echo "$TOOLS/$1.bat"; fi; }
AAPT2="$(shim aapt2)"; D8="$(shim d8)"; ZIPALIGN="$(shim zipalign)"; APKSIGNER="$(shim apksigner)"

rm -rf build/v2
mkdir -p build/v2/classes build/v2/dex build/v2/pkg

"$AAPT2" compile --dir v2/res -o build/v2/res.zip

"$AAPT2" link -o build/v2/pkg/base.apk -I "$ANDROID_JAR" \
    --manifest v2/AndroidManifest.xml \
    --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    build/v2/res.zip

javac --release 8 -Xlint:-options -classpath "$ANDROID_JAR" -d build/v2/classes \
    $(find src v2/src -name '*.java')

"$D8" --lib "$ANDROID_JAR" --min-api "$MIN_SDK" --output build/v2/dex \
    $(find build/v2/classes -name '*.class')

# classes.dex enters the APK after aapt2 link and before zipalign. It has to go
# into a copy of what aapt2 produced: building a fresh archive here would drop
# the manifest and the resources.
cp build/v2/pkg/base.apk build/v2/pkg/unsigned.apk
PYTHON="${PYTHON:-}"
if [ -z "$PYTHON" ]; then
    for candidate in python3 python py; do
        if command -v "$candidate" >/dev/null 2>&1; then PYTHON="$candidate"; break; fi
    done
fi
[ -n "$PYTHON" ] || { echo "no python found to add classes.dex to the apk" >&2; exit 2; }
"$PYTHON" - <<'PY'
import zipfile
z = zipfile.ZipFile('build/v2/pkg/unsigned.apk', 'a')
z.write('build/v2/dex/classes.dex', 'classes.dex')
z.close()
PY

"$ZIPALIGN" -f -p 4 build/v2/pkg/unsigned.apk build/v2/pkg/aligned.apk

if [ -n "$KS" ]; then
    "$APKSIGNER" sign --ks "$KS" --ks-pass "pass:android" --key-pass "pass:android" \
        --out "$OUT" build/v2/pkg/aligned.apk
else
    cp build/v2/pkg/aligned.apk "$OUT"
    echo "built unsigned: $OUT"
fi

echo "built $OUT"
