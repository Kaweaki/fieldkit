# FieldKit

A single, self-contained Android app for a phone that works without a network: a
twelve-tile menu, wallpapers drawn on the device, USB and Bluetooth tooling,
survival calculators, a party-aware profile store, an encrypted journal, a
passcode lock and a share screen.

This repository holds the **build and code process documentation**, the
**build skill guide** and the **signed APK** for FieldKit 2.8.

## What's in here

| Path | What it is |
| --- | --- |
| [`FIELDKIT-BUILD-PROCESS.txt`](FIELDKIT-BUILD-PROCESS.txt) | How the app is put together, environment, source layout, every screen's behaviour, the security review, the checks run for each version (2.1 to 2.8) and the known limits |
| [`SKILL.md`](SKILL.md) | The reusable build skill: `aapt2` + `javac` + `d8` with no gradle, screens without layouts, runtime-drawn wallpapers, USB/BLE identification, passcode handling, adb verification, AES vaults, MBTiles rendering, Kiwix deep links, and the USB-device/ADB PC link |
| [`dist/FieldKit-2.8.apk`](dist/FieldKit-2.8.apk) | The signed 2.8 build (versionCode 19) |

## Download and install

Grab the APK from the [latest release](../../releases/latest), then either open it
on the phone or sideload it:

```
adb install -r FieldKit-2.8.apk
```

On a locked-down device a fresh package can be refused with
`INSTALL_FAILED_VERIFICATION_FAILURE`. Read the current value first, turn the
verifier off, install, then restore it:

```
adb shell settings get global package_verifier_enable      # note the value
adb shell settings put global package_verifier_enable 0
adb install -r FieldKit-2.8.apk
adb shell settings put global package_verifier_enable <old value>
```

The build is signed with a debug keystore, so updating over another signature
fails; uninstall the old copy first in that case.

## Build facts

Verified from the published binary:

| | |
| --- | --- |
| Package | `se.blimark.fieldkit` |
| Version | 2.8 (versionCode 19) |
| SDK | `minSdkVersion 21`, `targetSdkVersion 34` |
| Toolchain | build-tools 35.0.0 (`aapt2`, `d8`, `zipalign`, `apksigner`), platform `android-34`, JDK 17 with `javac --release 8`; no gradle, no AndroidX, no network at build time |
| Classes in `classes.dex` | 279 |
| APK size | 168,573 bytes |
| SHA-256 | `2a7120dce249698ecfaa5ba92c402cfa5d9e06cb2e262cb9ce4a60acb039f922` |
| MD5 | `214ea917ed6ddc68531916248e423fca` |

Verify a download with:

```
sha256sum FieldKit-2.8.apk
```

## Build from source

The Java source is not part of this repository; the build process document
describes it file by file. The pipeline it documents is:

```
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/pkg/base.apk -I android.jar --manifest AndroidManifest.xml \
    --min-sdk-version 21 --target-sdk-version 34 --java build/gen \
    --version-code 19 --version-name 2.8 build/res.zip
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

## What's new in 2.8

- USB cable reporting is honest about Android's roles: when the phone is plugged
  into a PC it is the USB device, so the PC cannot be listed by
  `UsbManager.getDeviceList()`. Link now reports the cable and data state
  (`USB data link active (ADB)`, `USB cable connected`, or `No USB cable`) and
  states that the upstream PC's name is not exposed.
- The PC Link screen can answer a PC over the cable through an ADB-forwarded
  loopback socket on port 47778, with a banner and echo. Bluetooth SPP remains
  the no-ADB route.
- 2.7 added the encrypted journal, GPX import/export with distance and bearing,
  richer BLE and USB bridge reporting, and peer short messages.
- 2.6 added the Kiwix library prompt, twelve-tile menu and the current security
  and installation flow.

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
CP210x bridge, the cable PC link needs USB debugging and `adb`, and the app-level
passcode is not disk encryption.

## Licence

No licence has been chosen for this repository yet, so the default applies: all
rights reserved.
