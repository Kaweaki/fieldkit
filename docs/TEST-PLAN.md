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
