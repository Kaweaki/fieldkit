# Test plan

Four layers, cheapest first. Only layer 0 can run on a machine without the Java
source; layer 1 needs the source, layers 2 and 3 need a device or an emulator.

## Layer 0 — the manifest contract (no device, no SDK)

```console
py tools/apkcheck.py dist/FieldKit-2.6.apk
```

On 2.6 it must report three failures (target API, Bluetooth permissions,
storage permissions) and two warnings (minimum API, debug signing). After a 2.7
build it must report no failures; one warning stays while the storage
permissions are capped at API 28 and the document picker is still missing.
`app/build.sh` runs it automatically before an APK is considered finished. `py -m unittest discover -s tests -t .` proves the
checker still catches each of those bugs.

This layer exists because the 2.6 build passed every manual check on a phone
running Android 10 while being unusable on Android 12+. A permission that is
missing from the manifest cannot be caught by a screen test on the wrong phone.

## Layer 1 — the logic, on the JVM

`app/test/se/blimark/fieldkit/SelfTest.java` runs on any JDK and covers the
arithmetic that 2.7 added: sunrise and sunset against known local times, bleach
and iodine dosing, the power budget, ICE line building, the backup round trip
and the message framing. It has no JUnit and no `android.jar` dependency, so the
job is one `javac` and one `java`.

```console
javac --release 8 -d build/classes $(find app/src -name '*.java')
javac --release 8 -cp build/classes -d build/test $(find app/test -name '*.java')
java -cp build/classes:build/test se.blimark.fieldkit.SelfTest
```

The classes that touch the platform (permissions, picker, insets, torch,
compass, SPP) compile against `android.jar` in CI but can only be exercised on a
device — layers 2 and 3 below. The existing logic still needs the same
treatment once the activity source is here:

| Class | What to assert |
| --- | --- |
| `ProfileStore` | water and kcal per person for pregnant / diabetic / child flags, totals equal the sum of the lines, a malformed `people_list` line is skipped rather than crashing |
| `ToolActivity` | each of the ten calculators against a hand-computed table, including unit edges |
| `Adv` | frequency ↔ channel both ways, station spacing, out-of-band input rejected |
| `Lock`, `Crypto` | PBKDF2 round trip, wrong code fails, legacy SHA-256 record is accepted once and rewritten, AES-GCM tag failure on a modified blob |
| `PeerActivity` | packet framing and sealing: wrong network code yields no output, a truncated packet is rejected |
| `Oui` | a known OUI resolves, a randomised address falls through to the heuristic and is labelled a guess |
| `MapView` | TMS row/column maths, scale bar value at a known latitude and zoom |
| `Share` | filename validation rejects `../` and absolute paths |

## Layer 2 — the UI, on emulators

Matrix: API 23, 26, 30, 34, 36, x86_64 images, one AVD each, portrait. API 23
is the oldest image worth carrying (it is the first with keystore AES-GCM);
API 36 is what Play will run.

For each image:

1. `adb install -r`, then `am force-stop`, then `am start` — a resumed task can
   show a stale screen and make a working button look broken.
2. Walk all 24 activities. For each one: `uiautomator dump`, find the expected
   string (remember `Ui.action` uppercases button text), tap it, dump again,
   assert the next screen's anchor text appears.
3. After the walk: `adb logcat -b crash -d` must contain no entry for
   `se.blimark.fieldkit`, and `adb shell dumpsys activity services
   se.blimark.fieldkit` must be empty.
4. Screenshot each screen (`adb exec-out screencap -p`) into `screenshots/`,
   which is git-ignored.
5. On API 31+ specifically: grant Nearby devices, open Radio, assert the scan
   list is populated rather than empty. This is the check the 2.6 build would
   fail.

## Layer 3 — hardware, on phones

No emulator has a Bluetooth radio, a USB host port or a GPS fix. This table is
the part of "test everything" that cannot be automated, and it belongs in the
release notes with a date.

| Path | Needs | Evidence to record |
| --- | --- | --- |
| BLE connect, services, retry on 133 | any BLE peripheral (a $5 beacon works) | status, transport, attempt count in advanced mode |
| USB serial console | OTG adapter + CDC-ACM or CP210x board | echoed bytes, VID/PID in the descriptor view |
| SPP client and PC link server | a computer with a Bluetooth serial port | a real COM port on the PC, text both ways |
| Peers, distance, bearing | two phones on one subnet, a fix on both | peer row with distance, direction blank without a fix |
| Survey | Android 13+ for the Wi-Fi path | 65+ sightings, WPA3/WPA2 label, sealed log |
| MBTiles | 16-tile test file | tiles render, waypoint named and pinned |
| Kiwix deep links | Kiwix installed, one ZIM | `zim://`, content provider and `ACTION_SEARCH` tried in order |
| Map fallback | any `geo:` app | picker lists it, crosshair arrives |
| Widget | a launcher with a widget picker | three lines render, "locked" while the passcode is set |
| Passcode | — | wrong code keeps the pad up, right code opens the tapped tile, cold start asks again |

## Status

| Layer | Runs on the audit machine | Result |
| --- | --- | --- |
| 0 | yes | 2.6 reports 3 failures, 2 warnings, as designed |
| 1 | no | blocked: the Java source is not in this repository |
| 2 | no | blocked: no emulator, no SDK, no `adb` |
| 3 | no | blocked: no phone |

Until the source is committed, "test all functions" is a plan, not a claim.

## Device run: 2026-11-23

Executed on the one Android device available, through `app/probe/` — a separate
package that calls the 2.7 classes and writes each result to logcat.

Device: Samsung SM-A505F, Android 10 (API 29), 1080x2340.
Nothing here exercises API 31+ behaviour; the phone cannot reach it.

| Probe key | Result | Meaning |
|---|---|---|
| `torch_supported` | `true` | CameraManager reports a flash unit |
| `torch_on` / `torch_off` | `true` / `true` | The LED lit and went out, no CAMERA permission requested |
| `compass_started` | `true` | Rotation vector sensor registered |
| `heading` | `NE 61` | Live bearing with cardinal point |
| `radio_ready` | `true` (after grant) | `Perms.needed()` returned ACCESS_FINE_LOCATION on API 29 |
| `radio_ready_before_prompt` | `false` | With the permission revoked the app asks instead of crashing |
| `radio_ready_after_prompt` | `true` | The grant callback re-enables the radio path |
| `sun_london_0621` | `03:43 / 20:21` | Matches the JVM vector exactly |
| `water_5pct_10l` | `1,68 mL ... 34 drops` | Matches, with the device's decimal comma |
| `power` | `10000 mAh at 500 mA: about 17,0 h` | Matches |
| `ice` | all five fields present | ICE line building works |
| `backup_roundtrip` | `true` | Vault payload survived encode/decode |
| `textlink` | `id1/3/hello there` | Chunked message framing works |
| `insets_top_padding` | `0` | Inconclusive, see below |

`GrantPermissionsActivity` in the system log confirmed the request came from the
probe for `ACCESS_FINE_LOCATION`. The deny branch was not exercised: the tap
that presses the probe button releases into the dialog that button opens, and
One UI's allow button sits under that point.

The insets result proves nothing either way. This activity is not edge-to-edge
and targets 34, so the decor consumes the system bars before the content view
sees them. Padding only matters with `SYSTEM_UI_FLAG_LAYOUT_*` under target 35,
which needs Android 15.

Storage was full (32 MB free of 52 GB) and blocked installation, so two ZIMs of
37 GB of Kiwix content were removed first: `wikipedia_en_top1m_nopic` (17.1 GB)
and `wikipedia_sv_all_mini` (4.0 GB, a subset of the `sv_all_nopic` still on the
phone). The medical, water, food, knots, post-disaster, Appropedia, energypedia,
low-tech and Wikivoyage ZIMs are untouched.

Still untested on hardware: the Bluetooth split itself (needs API 31+), SPP over
a real cable, MBTiles import through SAF, Kiwix intents, the widget, and
edge-to-edge insets.

## Kiwix library: 2026-11-23

The phone held 15 ZIM files in `/storage/emulated/0/Kiwix`, but Kiwix's own
library was empty ("Inga filer här") because the app had never scanned them.
Two facts matter for anyone repeating this:

- The library is filled by a **pull-to-scan** gesture inside the Bibliotek tab.
  A `VIEW` intent on a `.zim` file opens that book, but only the one issued to a
  cold-started app is remembered; the rest are displayed and forgotten.
- The scan indexes ZIMs where they already are, recording a relative path such
  as `../../../../../Kiwix/zimgit-water_en_2024-08.zim`. Nothing is copied into
  `Android/data/.../files/ZIMFiles`, so FieldKit can still see the same files.

After the scan, all seven requested books appear with correct sizes and article
counts, and 14 of the 15 files are indexed. The exception is
`wikipedia_sv_all_nopic_2026-11.zim`: its ZIM header places the trailing MD5 at
7,612,178,401 but the file ends at 5,967,631,525, so 1.6 GB never arrived and
Kiwix leaves it out. The other 14 satisfy `checksumPos == size - 16` exactly.

FieldKit's Library tile does not read ZIMs. It reports "Kiwix is installed. ZIM
articles stay inside Kiwix; this list only keeps links." and offers OPEN KIWIX,
so its compatibility depends entirely on Kiwix having the books indexed.

## V2 app on the same phone: 2026-11-23

`se.blimark.fieldkit.v2` 2.9 (21), built by `app/build-v2.sh`, installed beside
the shipped 2.8 on the SM-A505F (Android 10, API 29). Every screen was opened
through adb and read back from the accessibility tree.

| Check | Result |
|---|---|
| Home grid, tiles, palette | renders with the V1 colours from the 2.8 APK (`#FFB000` on `#0B0E12`) |
| Profile "work it out" | 1 person, 14 days: 42 L drinking, 105 L with washing, 18 mL of 5% bleach, 353 drops |
| Calculator "bleach dose" | 10 L clear at 5%: 0.84 mL, 17 drops, iodine 50 drops, boil 1 minute |
| Medical | nine protocols open; ICE card built from Settings |
| Settings save | "Saved to this phone", eight fields |
| Journal | sealed 11 characters, reopened with the passphrase, text returned intact |
| Library | cannot list `/Kiwix` under scoped storage even with the legacy flag (it only applies at target 29); the folder picker opens and the screen explains the step |
| Maps | screen opens; the picker is reachable (selection needs a human tap) |
| Comms | found `org.briarproject.briar.android` and offers to open it |
| Help | API 29, SM-A505F, torch available, compass present, 20147 MB free |
| Screens + wallpaper | lock wallpaper id moved 2196 -> 2197 with `FLAG_LOCK` |
| SOS panel | state reached SIGNALLING; camera service logged the torch turning on and off for the app's own PID in Morse timing |
| Matched tap through the permission dialog | not exercised: One UI answers the dialog from the same tap that opens it |

Not tested here: a second phone for the Link tile, the API 31+ Bluetooth split,
the SAF folder selection (needs a human tap in DocumentsUI), and map rendering
(which this build does not claim to do).

## Android 14 / API 34 in an emulator: 2026-11-23

The phone in the table above is Android 10, so the Bluetooth permission split and
the Android 12 splash screen had never actually run. This pass put the same APK
on a newer system: Windows 10 host, Android Emulator 37.3.10 with the AEHD
hypervisor driver (the emulator refuses software emulation, so the driver is not
optional), two AVDs at API 34 - `aosp_atd` (fast, no display pipeline, so no
screenshots) and `default` (full AOSP, screenshots and wallpaper service).

| Check | Result |
|---|---|
| Installs and launches | `Success`, first frame drawn in 5.3 s cold (dexopt) and 2.0-2.3 s warm |
| All twelve tiles | opened one by one, stable PID, crash buffer empty |
| Link, permission missing | shows the Android 12 wording and an ASK FOR IT button |
| Link, permission denied | stays on the Link screen with the same card, toast "Radio still refused.", no exception |
| Link, permission granted | permission card gone, PAIRED PHONES list, "Nothing is paired yet" |
| Permission requested | dialog reads "find, connect to, and determine the relative position of nearby devices" - the modern scan/connect pair, not legacy Bluetooth |
| SOS panel | IDLE -> SIGNALLING -> IDLE, and the panel says "This phone reports no camera flash, so there is no torch to drive" |
| Help | "ANDROID 14 (API 34)", "TORCH unavailable" - the emulator's own hardware, reported as it is |
| Lock-screen wallpaper | system id=0 -> id=2 while a separate lock record id=3 with mWhich=2 appeared: lock screen only |
| One ANR | on the very first launch, with two emulators competing for the CPU; not reproducible once the second one was stopped |

Four defects came out of this and are fixed in the source:

1. `onRequestPermissionsResult` always jumped home, so answering the dialog from
   the Link screen threw the owner back to the grid. It now rebuilds the screen
   the request came from.
2. `Kit.link` drew the "Radio permission" card unconditionally, so a phone that
   had already granted it was told to ask again. The card now only exists while
   the permission is actually missing.
3. On Android 12+ the system draws a splash screen from the theme and it came out
   white in front of a black app. `values-v31/styles.xml` now sets the same
   `#0B0E12` background and the amber mark.
4. `app/build-v2.sh` appended `classes.dex` to a *new* `unsigned.apk` instead of a
   copy of what aapt2 produced, so the script emitted an APK with no manifest and
   no resources. It copies `base.apk` first now, and finds `python3`, `python` or
   `py` on its own.

Not covered by the emulator: Bluetooth actually carrying text (the emulator has
no radio, so pairing and SPP are still unproven), the torch (no flash unit), and
the magnetometer. The phone covers the torch; a second real phone is still the only
way to prove the Link tile end to end.
