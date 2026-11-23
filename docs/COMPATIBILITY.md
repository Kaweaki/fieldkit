# Compatibility

Measured from the signed 2.6 APK (`dist/FieldKit-2.6.apk`, versionCode 17) and
Google's published pages on the dates cited, not from the build document. Re-run
`py tools/apkcheck.py <apk>` to reproduce the manifest half of this file.

## What the shipped APK actually declares

| | |
| --- | --- |
| package | `se.blimark.fieldkit` |
| versionCode / versionName | 17 / 2.6 |
| minSdkVersion | 21 (Android 5.0) |
| targetSdkVersion | 34 (Android 14) |
| compileSdkVersion | 34 |
| APK | 156,285 bytes, SHA-256 `d93c20c6…0835` |
| entries | 9 |
| dex | one file, 299,276 bytes |
| native libraries | **none** — no `lib/`, no `.so` |
| activities / exported | 24 / `MainActivity` and `FieldKitWidget` only |
| permissions | 13, listed below |
| signing certificate | `CN=Android Debug` |

Permissions: `SET_WALLPAPER`, `BLUETOOTH`, `BLUETOOTH_ADMIN`,
`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_WIFI_STATE`,
`CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_NETWORK_STATE`,
`INTERNET`, `NEARBY_WIFI_DEVICES`, `READ_EXTERNAL_STORAGE`,
`WRITE_EXTERNAL_STORAGE`.

No native code is the most valuable compatibility property in the build: one
APK covers ARM, ARM64, x86 and x86_64, runs on Chromebooks, and is unaffected
by Android 15's 16 KB page-size requirement that breaks native apps.

## Android reach

Cumulative share of active devices by minimum API level (Google's October 2026
distribution feed):

| minSdk | Android | Reaches |
| --- | --- | --- |
| 21 | 5.0 | 99.8% |
| 23 | 6.0 | 99.8% |
| 24 | 7.0 | 99.5% |
| 26 | 8.0 | 99.1% |
| 31 | 12 | 77.8% |

Two conclusions. First, `minSdkVersion 21` buys 0.3 points over 24 and costs a
whole pre-keystore crypto path (`Crypto` derives a key from the passcode with
PBKDF2 on API 21–22); if that branch is not tested on a real Android 5 phone,
raise the floor and delete it. Second, the 2.6 build has no `BLUETOOTH_SCAN` or
`BLUETOOTH_CONNECT`, so on the 77.8% of devices running Android 12 or newer the
radio screens fail with a `SecurityException` the UI does not explain.

## What does not work in 2.6

| Symptom | Cause | Fixed by |
| --- | --- | --- |
| Radio, BLE, Serial, PC link do nothing on Android 12+ | `targetSdk 34` with no `BLUETOOTH_SCAN`/`_CONNECT`; the dex never references them, so nothing can request them | `app/AndroidManifest.xml` V2 block + a runtime `requestPermissions` call |
| MBTiles file from Download is "refused" | `READ`/`WRITE_EXTERNAL_STORAGE` are no-ops under scoped storage; the app uses a file path, the dex has no `ACTION_OPEN_DOCUMENT` | document picker, then copy into app storage |
| Cannot be published to Google Play | Play requires API 36 for new apps and updates since 31 Aug 2026; an app targeting 34 is shown only to devices at or below API 34 | target API 36 (and retest edge-to-edge, which is enforced from 35) |
| Updating another install fails | debug certificate | a real release keystore |

Everything above is a build problem, not a platform limit. Sideloading,
F-Droid, IzzyOnDroid and Obtainium are unaffected by the Play rule.

## Other systems

| System | Runs FieldKit? | Why |
| --- | --- | --- |
| AOSP, GrapheneOS, LineageOS, One UI, OxygenOS, Android Go | yes, one APK | framework Java only, no Play services |
| Android 5.0–11 | yes | that is what 2.6 was tested on |
| Android 12–17 | app installs; radio broken (see above) | permission split |
| Chromebooks | yes | no ABI constraint |
| Ubuntu Touch, postmarketOS | no direct install; Waydroid on Halium 9+ runs it | Waydroid is an Android container |
| iOS, iPadOS | no | no APK path; only a separate web/PWA artifact could reach these |
| KaiOS feature phones | no | web apps only |
| HarmonyOS NEXT | no | dropped Android APK support |
| Windows 11 | no | Windows Subsystem for Android was retired on 5 March 2025 |

The practical floor for the *experience*, not the APK, is Android 7.1: Library
hands pages to Kiwix, whose Android build supports 7.1–15, and Maps hands a
`geo:` intent to an OSM app. Advertising "Android 5.0+" is true about install
and misleading about use.

## Sources

- Play target API levels: <https://support.google.com/googleplay/android-developer/answer/11926878>
- 16 KB page sizes: <https://developer.android.com/guide/practices/page-sizes>
- Bluetooth permissions on Android 12+: <https://developer.android.com/develop/connectivity/bluetooth/bt-permissions>
- Android version distribution, October 2026: <https://www.heise.de/en/news/Android-version-distribution-in-October-2026-Android-16-leads-11504842.html>
- Kiwix Android supported versions: <https://github.com/kiwix/kiwix-android>
- Waydroid on Ubuntu Touch: <https://docs.ubports.com/en/latest/userguide/dailyuse/waydroid.html>
- WSA retirement: <https://learn.microsoft.com/windows/android/wsa>
- F-Droid inclusion policy: <https://f-droid.org/en/docs/Inclusion_Policy/>
- IzzyOnDroid inclusion policy: <https://izzyondroid.org/docs/general/AppInclusionPolicy>
