# FieldKit

> **Branch `v2`.** Prepares the next release (app 2.7, versionCode 18): the
> permission and target-SDK fixes, a licence, a manifest contract check and a
> test plan. It is additive and reversible — see
> [`docs/RESTORE.md`](docs/RESTORE.md). `main` still holds the released 2.6 tree.

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
| [`LICENSE`](LICENSE) | MIT |
| [`app/AndroidManifest.xml`](app/AndroidManifest.xml) | The manifest as 2.7 should read, with every change marked |
| [`app/build.sh`](app/build.sh) | The build in one command, transcribed from the process document |
| [`app/src/`](app/src) | The 2.7 classes: Bluetooth permission request, document picker, insets, torch and SOS, compass, water dosing, power budget, ICE card, backup, FieldKit-to-FieldKit text |
| [`app/test/`](app/test) | `SelfTest`: plain-JVM checks for every calculation, no device needed |
| [`docs/V2-WIRING.md`](docs/V2-WIRING.md) | Where each new class is called from, with the exact lines |
| [`tools/apkcheck.py`](tools/apkcheck.py) | Parses an APK's binary manifest and checks the compatibility contract |
| [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) | What runs where, measured from the APK |
| [`docs/TEST-PLAN.md`](docs/TEST-PLAN.md) | The four layers that would prove a release works |
| [`docs/RESTORE.md`](docs/RESTORE.md) | How to go back |

## Download and install

Grab the APK from the [latest release](../../releases/latest), then either open it
on the phone or sideload it:

```
adb install -r FieldKit-2.6.apk
```

On a phone with Play Protect a sideloaded APK usually gets a scan prompt, not a
refusal; install after checking the SHA-256 above.
`INSTALL_FAILED_VERIFICATION_FAILURE` is a different, rarer case where the
package verifier rejects the signature. The workaround weakens the whole device,
so use it only on a test phone, note the old value first and put it back:

```
adb shell settings get global package_verifier_enable      # note the value
adb shell settings put global package_verifier_enable 0
adb install -r FieldKit-2.6.apk
adb shell settings put global package_verifier_enable <old value>
```

The build is signed with a debug keystore, so updating over another signature
fails; uninstall the old copy first in that case. Installing from a computer
with `adb install` normally needs no setting changed.

The 2.6 build cannot use its radio screens on Android 12 or newer; the cause and
the fix are in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

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
| Native libraries | none: one APK for ARM, ARM64, x86 and x86_64, Chromebooks included |
| Exported components | `MainActivity` and `FieldKitWidget` only |
| Signing certificate | `CN=Android Debug` (development only) |

Verify a download with:

```
sha256sum FieldKit-2.6.apk
```

## Checking a build

```console
$ py tools/apkcheck.py dist/FieldKit-2.6.apk
```

It parses the APK's binary manifest, without an SDK or a device, and reports
what the build gets wrong. On 2.6 that is three failures — target API level,
Bluetooth permissions, storage permissions — and two warnings. After 2.7 it must
report no failures; one warning stays while the storage permissions are capped
at API 28 and the document picker is still missing. `app/build.sh` runs it
before an APK counts as finished. The
reasoning behind each check is in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

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

One APK, plain framework Java, no AndroidX, no Play services and no native
code, so it runs on ARM, ARM64, x86 and x86_64: phones, tablets, Android Go
devices and Chromebooks. `minSdkVersion 21` reaches 99.8% of active Android
devices; the practical floor is Android 7.1, because Library and Maps hand off to
Kiwix and a map app that require it. Ubuntu Touch and other Linux phones cannot
install an APK — Waydroid on Halium 9+ runs this one. iOS, KaiOS and HarmonyOS
NEXT cannot run an APK at all, and Windows 11's Android subsystem was retired in
March 2025.

The matrix, the numbers and their sources are in
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## Known limits

Stated in the app itself, not hidden here: there is no API to retune the phone's
own FM transmitter (the radio screen shows real radio state and channel
arithmetic instead), randomised Bluetooth addresses defeat OUI lookup (those
  rows are marked as guesses), USB serial needs an OTG adapter and a CDC-ACM or
  CP210x bridge, and the app-level passcode is not disk encryption.
- Radio, BLE, Serial and PC link fail on Android 12+ in 2.6: the manifest
declares no `BLUETOOTH_SCAN` or `BLUETOOTH_CONNECT`, and nothing in the app can
request a permission it never declared. Fixed in `app/AndroidManifest.xml`.
- `READ`/`WRITE_EXTERNAL_STORAGE` grant nothing under scoped storage, which is
why the Download import was recorded as refused. It is an app bug, not a
platform limit; the fix is a document picker.

## Licence

MIT — see [`LICENSE`](LICENSE). Chosen so the source can be rebuilt, forked,
translated and ported, and so F-Droid and IzzyOnDroid can carry it; swap in
GPL-3.0 if derivatives must stay open.

## FieldKit V2 (this branch)

`app/v2/` is a complete, runnable app in the V1 palette: package
`se.blimark.fieldkit.v2`, built by `app/build-v2.sh`. It exists because the
shipped app's activity source is not in this repository, so the 2.7 classes had
nowhere real to run. Every tile is backed by code that runs on the phone; nothing
is a placeholder:

| Tile | What it actually does |
|---|---|
| Profile | water for your people and days, plus the bleach or boil dose for all of it |
| Settings | stores name, blood type, allergies, medicines, contact, home position; export and restore a plain-text backup through the system picker |
| Medical | nine field protocols and your ICE card |
| Calculator | bleach and iodine dosing, ORS recipe, battery hours, solar yield |
| Link | Bluetooth serial text to a paired phone, framed by `TextLink` |
| Share | writes the backup through `SAF`, or hands it to any app |
| Screens | paints the ICE card onto the lock screen only (`FLAG_LOCK`) |
| Library | counts the ZIM files it can see and opens Kiwix |
| Journal | AES-256-GCM notes, key from PBKDF2 over your passphrase |
| Maps | opens a picked `.mbtiles`, reads its metadata and counts tiles |
| Comms | opens Briar if it is installed, says so if it is not |
| Help | this phone's real capabilities, and what the build does not do |

The SOS bar above the grid runs the torch in Morse, the same pattern in tone,
a flashing panel, and a preset message built from the ICE fields that can be
copied or shared. It needs no permission and no network.

The manifest has **no INTERNET permission** and no storage permissions; the only
runtime permission is the Bluetooth pair (scan/connect on Android 12+, location
below it), asked from the Link tile and the home banner.

