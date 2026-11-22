# FieldKit

A single, self-contained Android app for a phone that works without a network: an
eleven-tile menu, wallpapers drawn on the device, USB and Bluetooth tooling,
survival calculators, a party-aware profile store, a passcode lock and a share
screen.

This repository holds the **build and code process documentation**, the
**build skill guide** and the **signed APK** for FieldKit 2.6.

## What's in here

| Path | What it is |
| --- | --- |
| [`FIELDKIT-BUILD-PROCESS.txt`](FIELDKIT-BUILD-PROCESS.txt) | How the app is put together, environment, source layout, every screen's behaviour, the security review, the checks run for each version (2.1 to 2.6) and the known limits |
| [`SKILL.md`](SKILL.md) | The reusable build skill: `aapt2` + `javac` + `d8` with no gradle, screens without layouts, runtime-drawn wallpapers, USB/BLE identification, passcode handling, adb verification, AES vaults, MBTiles rendering, Kiwix deep links |
| [`dist/FieldKit-2.6.apk`](dist/FieldKit-2.6.apk) | The signed 2.6 build (versionCode 17) |

## Download and install

Grab the APK from the [latest release](../../releases/latest), then either open it
on the phone or sideload it:

```
adb install -r FieldKit-2.6.apk
```

On a locked-down device a fresh package can be refused with
`INSTALL_FAILED_VERIFICATION_FAILURE`. Read the current value first, turn the
verifier off, install, then restore it:

```
adb shell settings get global package_verifier_enable      # note the value
adb shell settings put global package_verifier_enable 0
adb install -r FieldKit-2.6.apk
adb shell settings put global package_verifier_enable <old value>
```

The build is signed with a debug keystore, so updating over another signature
fails; uninstall the old copy first in that case.

## Build facts

Verified from the published binary:

| | |
| --- | --- |
| Package | `se.blimark.fieldkit` |
| Version | 2.6 (versionCode 17) |
| SDK | `minSdkVersion 21`, `targetSdkVersion 34` |
| Toolchain | build-tools 35.0.0 (`aapt2`, `d8`, `zipalign`, `apksigner`), platform `android-34`, JDK 17 with `javac --release 8`; no gradle, no AndroidX, no network at build time |
| Classes in `classes.dex` | 43 (`MainActivity` ... `Lock`) |
| APK size | 156,285 bytes |
| SHA-256 | `d93c20c6e133485b609b853ae5d2df3beda2bbd77e370fd0bdfcd17edf560835` |
| MD5 | `77be033952895e42b42ce6e98e77d09e` |

Verify a download with:

```
sha256sum FieldKit-2.6.apk
```

## Build from source

The Java source is not part of this repository; the build process document
describes it file by file. The pipeline it documents is:

```
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/pkg/base.apk -I android.jar --manifest AndroidManifest.xml \
    --min-sdk-version 21 --target-sdk-version 34 --java build/gen \
    --version-code 17 --version-name 2.6 build/res.zip
javac --release 8 -nowarn -classpath android.jar -d build/classes \
    $(find src -name '*.java') $(find build/gen -name '*.java')
d8 --lib android.jar --min-api 21 --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -j ../pkg/unsigned.apk classes.dex)
zipalign -f -p 4 unsigned.apk aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out FieldKit.apk aligned.apk
apksigner verify FieldKit.apk
```

Use build-tools 35.0.0: 34's `d8` crashes on this source set.

## Compatibility

One APK, plain framework Java, no AndroidX, no Google Play services. It installs
on AOSP, GrapheneOS, LineageOS, OxygenOS, One UI and the rest of Android 5.0+.
Calls that need a newer API are behind `Build.VERSION.SDK_INT` checks. Ubuntu
Touch and other non-Android Linux phones cannot install an APK; Waydroid can run
this one.

## Known limits

Stated in the app itself, not hidden here: there is no API to retune the phone's
own FM transmitter (the radio screen shows real radio state and channel
arithmetic instead), randomised Bluetooth addresses defeat OUI lookup (those
rows are marked as guesses), USB serial needs an OTG adapter and a CDC-ACM or
CP210x bridge, and the app-level passcode is not disk encryption.

## Licence

No licence has been chosen for this repository yet, so the default applies: all
rights reserved.
