#!/usr/bin/env bash
# FieldKit build, in one command.
#
# Mirrors FIELDKIT-BUILD-PROCESS.txt section 1. It was not executed on the
# machine that wrote this branch (no JDK and no Android SDK there), so treat it
# as the script that document describes, committed so the build is repeatable
# rather than retyped.
#
#   ANDROID_HOME=~/Android/Sdk VERSION_NAME=2.7 VERSION_CODE=18 ./build.sh
#
# Requires: JDK 17, build-tools 35.0.0 (34's d8 crashes on this source set),
# a platform android.jar, and the app source in src/ next to this file.
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$APP_DIR"

: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK root}"

BUILD_TOOLS="${BUILD_TOOLS:-35.0.0}"
PLATFORM="${PLATFORM:-android-36}"      # the jar to compile against; 34 built 2.6
MIN_SDK="${MIN_SDK:-21}"                # see docs/COMPATIBILITY.md before raising
TARGET_SDK="${TARGET_SDK:-36}"          # Play requires 36 since 2026-08-31
VERSION_CODE="${VERSION_CODE:-18}"
VERSION_NAME="${VERSION_NAME:-2.7}"
PYTHON="${PYTHON:-python3}"
OUT="${OUT:-build/FieldKit-$VERSION_NAME.apk}"
KS="${KS:-$APP_DIR/debug.keystore}"     # dev only; releases need a real keystore

TOOLS="$ANDROID_HOME/build-tools/$BUILD_TOOLS"
ANDROID_JAR="$ANDROID_HOME/platforms/$PLATFORM/android.jar"

[ -x "$TOOLS/aapt2" ] || { echo "no aapt2 in $TOOLS" >&2; exit 2; }
[ -f "$ANDROID_JAR" ] || { echo "no $ANDROID_JAR" >&2; exit 2; }
[ -d src ] || { echo "no src/ next to build.sh" >&2; exit 2; }

rm -rf build
mkdir -p build/gen build/classes build/dex build/pkg

"$TOOLS/aapt2" compile --dir res -o build/res.zip

# --java is required even though every screen is built in code: the widget
# names R.layout.widget and its ids.
"$TOOLS/aapt2" link -o build/pkg/base.apk -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
    --java build/gen \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    build/res.zip

javac --release 8 -nowarn -classpath "$ANDROID_JAR" -d build/classes \
    $(find src build/gen -name '*.java')

"$TOOLS/d8" --lib "$ANDROID_JAR" --min-api "$MIN_SDK" --output build/dex \
    $(find build/classes -name '*.class')

# classes.dex has to enter the APK after aapt2 link and before zipalign.
(cd build/dex && zip -q -j ../pkg/unsigned.apk classes.dex)

"$TOOLS/zipalign" -f -p 4 build/pkg/unsigned.apk build/pkg/aligned.apk
"$TOOLS/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT" build/pkg/aligned.apk
"$TOOLS/apksigner" verify --print-certs "$OUT"

# The contract check is part of the build, not a review step: it is the only
# thing that notices a missing BLUETOOTH_SCAN before a phone does.
"$PYTHON" "$APP_DIR/../tools/apkcheck.py" "$OUT"
