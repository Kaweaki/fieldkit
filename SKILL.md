---
name: fieldkit-offline-android
description: Build, restyle and verify a self-contained offline Android app (a "field kit": tile menu, drawn wallpapers, USB/Bluetooth tooling, survival calculators, profiles, passcode pad) without gradle, using only aapt2, javac, d8, zipalign and apksigner, then drive and check it over adb. Use when a plain-Java Android app must be built from source with no AndroidX or network, when a home/lock screen wallpaper must be drawn at runtime, when USB serial or BLE devices must be listed and identified, when an app needs a runtime-switchable palette, or when a passcode must hide stored screens without breaking the rest of the app.
---

# FieldKit: a no-gradle offline Android app

A compact recipe for a single-app offline toolkit: plain Java, framework APIs
only, every view built in code, no `R.java`, no AndroidX, no `INTERNET`
permission, no background services. Everything here is reusable for any app that
has to be assembled with raw SDK tools and proven on a real phone over a cable.

## 1. Toolchain: aapt2 + javac + d8, no gradle

```
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/pkg/base.apk -I $ANDROID_HOME/platforms/android-34/android.jar \
    --manifest AndroidManifest.xml --min-sdk-version 21 --target-sdk-version 34 \
    --version-code N --version-name X.Y build/res.zip
javac --release 8 -nowarn -classpath android.jar -d build/classes $(find src -name '*.java')
d8 --lib android.jar --min-api 21 --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -j ../pkg/unsigned.apk classes.dex)
zipalign -f -p 4 unsigned.apk aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out App.apk aligned.apk
apksigner verify App.apk
```

Pitfalls that cost real time:

- **Use build-tools 35.0.0.** 34's `d8` crashed on this source set.
- **No `R.java` unless something must be named.** Screens can be built
  entirely in Java, but `RemoteViews` needs a layout and ids: add
  `--java build/gen` to `aapt2 link`, include
  `$(find build/gen -name '*.java')` in the `javac` line, and put the layout and
  `res/xml` metadata in `res/`. `R` uses the manifest package.
- **The `zip -j` step** must run after `aapt2 link` and before `zipalign`, or
  the APK verifies but has no dex.
- **Same package + different signature** will fail to update; keep one debug
  keystore in the project and reuse it.
- **`INSTALL_FAILED_VERIFICATION_FAILURE`** on locked-down phones: temporarily
  `settings put global package_verifier_enable 0`, install, then restore the
  previous value (read it first).
- Keep the build in a `build.sh` that `set -euo pipefail` and re-runs cleanly;
  a stale `build/` produces confusing "no changes" builds.

## 2. Screens without layouts

- One static `Ui` class holds colours as mutable statics. `Ui.apply(context)`
  reads the palette and background from preferences and is the first call in
  every `onCreate`; changing the palette and recreating the activity re-skins
  the app with no resource variants.
- `Ui.card/label/field/text/chips/action/secondary/note/out` produce the whole
  visual language. Chips: `Ui.chips(host, label, items, def, int[] sel)` plus
  `Ui.select(row, idx)`, with the selected index in a one-element array.
- Button text is uppercased inside `Ui.action`/`Ui.secondary`. **Remember this
  when driving the app with uiautomator**: the dump shows the uppercase form.
- Backgrounds: a `Drawable` that paints a grid in `onDraw`, or a plain
  `ColorDrawable`, both behind a `ScrollView`.

## 3. Wallpapers without assets

`WallpaperManager` + a `Canvas` at the screen size, no image files and no helper
app:

- Draw, `setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)` for home
  and `FLAG_LOCK` for the lock screen (API 24+ for flags).
- Keep the lock art's focal point out of the bottom ~25% and away from the top
  centre so the clock and padlock do not sit on it.
- `SET_WALLPAPER` is a normal permission; no runtime prompt.
- Verify with `keyevent 26` then `keyevent 224` before `screencap`.

## 4. USB and Bluetooth identification

USB host:

- `UsbManager.getDeviceList()`; show vendor/product ids, and the manufacturer
  and product strings only when the device actually exposes them.
- Walk configuration/interface/endpoint descriptors for a truthful summary.
- Serial: claim the interface, `bulkTransfer` on the in/out endpoints. Support
  CDC-ACM and CP210x (`setControlLineState`, `setBaudRate` vendor requests); say
  so plainly when the chip is not one you can drive.
- The phone is the host: USB-C needs an OTG adapter.

Bluetooth:

- Run `BluetoothLeScanner` and register the classic `ACTION_FOUND` receiver at
  the same time, dedupe by MAC into one model.
- OUI tables are a static slice; randomised addresses defeat them. Fall back to
  a transport heuristic and label the row "guess" - never present it as fact.
- "How many people" is a count of phone-like devices, phrased as an estimate.
- PING ALL: `connectGatt(..., autoConnect=false)` with a timeout, count the
  callbacks, disconnect immediately.
- Classic serial: `createRfcommSocketToServiceRecord` with the SPP UUID.

Radio: Android exposes no API to retune the phone's own transmitter. If a
feature cannot exist, build the honest version - read the real radio state and
do the frequency/channel arithmetic - and say in the UI why.

## 5. Profiles, party maths and passcode

- Store people one line each: `name|kg|flags|activity|status` under a single
  preference key. Flags as bit values; parse defensively.
- Party maths: compute per person first (water, kcal with pregnancy/diabetes/
  child adjustments), then total. Show each person's line so the arithmetic is
  auditable rather than a single unexplained number.
- Passcode: store only `SHA-256(code)` in preferences; keep an `unlocked`
  static that lives for the process, so a cold start asks again.
- Guard at two levels: the tile handler **and** each protected activity's
  `onCreate`. Activities can be launched directly by intents or by recents.
- **Do not use a dismissing `AlertDialog` for the passcode.** A wrong code
  closes it and throws the user back to the grid. Use a full-screen keypad:
  dots, digits, clear/back, an UNLOCK button, inline "wrong code - try again",
  and stay put on failure. On success, finish and let the caller open the
  pending tile (`pending` index + `onResume`).
- Anything drawn from lock state must be recomputed in `onResume`: a banner
  painted in `onCreate` goes stale the moment the code is removed. Compare the
  current state to the drawn state and `recreate()` when they differ.
- Keep an owner/label name in preferences, never hardcoded, so a copy of the
  APK handed to someone else carries no personal data.

## 6. Verifying on the phone

- Install, then `am force-stop` before `am start`; a resumed task can show a
  stale screen and make a working button look broken.
- `uiautomator dump /sdcard/u.xml` + `cat` to a local file; a tiny helper finds
  a node by text (or by class, for `EditText`) and prints its centre for
  `input tap`. Give it the exact on-screen string, uppercase included.
- Type into fields by tapping the `EditText` node's centre, then
  `input text` (spaces as `%s`).
- Wait for text, not for time: loop the dump until the expected string appears.
- Check `logcat -b crash`, and `dumpsys activity services <pkg>` to prove there
  is no background service.
- Screenshots: `adb exec-out screencap -p > shot.png`, then look at them.
- If the phone sleeps and drops off USB: `adb reconnect`, `svc power stayon
  true`, `wm dismiss-keyguard`, swipe up to unlock.

## 7. Overflow menus, advanced mode and plain-language errors

- **Overflow menu**: a `\u22EE` `TextView` in a horizontal header row, opening a
  `PopupMenu` anchored to itself. Add items with `getMenu().add(0, id, order,
  label)` and switch on `getItemId()`. Keep destructive or rare actions there,
  not on the main surface.
- **One global detail switch** (`advanced` in preferences) is better than a
  second UI: every technical screen reads it and toggles the visibility of a
  technical `TextView`, never the behaviour. Include the switch in the overflow
  menu *and* in settings, and rebuild the screen after flipping it.
- **Explain failures where they happen.** A red rounded card above the content,
  a heading like WHAT WENT WRONG, one actionable sentence, a `try again`
  button, and a monospace technical line shown only in advanced mode.
- **BLE status 133** is a generic stack error, not a diagnosis. Practical fix:
  stop scanning before connecting, `connectGatt(ctx, false, cb,
  TRANSPORT_LE)` on API 23+, close the previous client, set a ~12 s timeout,
  retry exactly once on 133, then explain the likely causes (already connected
  elsewhere, out of range, non-connectable beacon, pairing needed).
- Never leave the user with a bare number; put the number in advanced mode and
  the meaning in normal mode.
- A non-exported activity cannot be started with `adb shell am start`; drive it
  through the UI or the check silently tests nothing.

## 8. Home-screen widget and cross-ROM compatibility

- Widget = `AppWidgetProvider` subclass + `res/layout/widget.xml` +
  `res/xml/widget_info.xml` + a `<receiver>` with `APPWIDGET_UPDATE` and the
  `android.appwidget.provider` meta-data. Build the `RemoteViews` in one static
  method and call `AppWidgetManager.updateAppWidget` for every id.
- Keep widget content choices in the same preferences file, and hide personal
  values when the passcode is set and the process is locked.
- `setInt(viewId, "setBackgroundColor", colour)` works from `RemoteViews`;
  colours from a runtime palette apply without new resources.
- Ship one universal APK: `minSdkVersion 21`, no AndroidX, no Play services, no
  `INTERNET`, and guard post-21 APIs with `Build.VERSION.SDK_INT`. That covers
  AOSP, GrapheneOS, LineageOS, OxygenOS, One UI and the rest.
- Non-Android Linux phones (Ubuntu Touch) cannot install an APK; say so plainly
  instead of implying the APK is universal, and treat a native port as a
  separate app that would share the same data model.

## 9. Honesty rules that make the app trustworthy

- Never present an OUI guess, a phone count or a radio capability as certain.
- When a platform API does not exist, show the real state and the arithmetic,
  and state the limit in the screen itself.
- No accounts, no cloud, no services. If you add a local socket, declare
  `INTERNET` for it but keep it opt-in, bind it to the subnet and say so; do not
  keep claiming "no internet permission" in help text.

## 10. AES vaults, passcode records and encrypted beacons

- Data at rest: AES-256-GCM with a key in the Android keystore (API 23+). Put
  no key material in preferences. On API 21-22 derive from the passcode with
  PBKDF2 and label the UI honestly when there is nothing to derive from.
- Passcodes: salted PBKDF2 (120 000 rounds) and `MessageDigest.isEqual` for the
  compare. Wrap the stored record with AES-GCM as well, so a copied preference
  file does not even expose the hash. Accept a legacy unsalted SHA-256 record
  once and rewrite it on the next good unlock.
- Peer packets: derive a shared key from a group code (not the passcode) with
  PBKDF2, then AES-GCM per packet with a random IV. A wrong code fails the tag,
  which is the behaviour you want.
- Never call a transport encrypted unless you control it; Bluetooth and Wi-Fi
  Direct have their own encryption.

## 11. Local peer presence, distance and bearing

- UDP broadcast on one port: `FK1|id|name|lat|lon|time` every few seconds, GCM
  sealed. Ignore your own id. Send only while the screen is open.
- Distance and bearing from `Location.distanceBetween`; print a compass point
  plus degrees and blank it when either side has no fix. RSSI alone cannot give
  direction - reject that shortcut.
- LAN sockets still need the `INTERNET` permission. Keep the feature off by
  default, explain the one permission, and do not hardcode a remote address.

## 12. Offline MBTiles rendering

- Open the SQLite file read-only, read `metadata` (name, minzoom, maxzoom), and
  query `tiles` with TMS rows: `row = (1 << z) - 1 - y`.
- Draw 256 px tiles at integer zoom; pan by moving the centre in world pixels,
  double-tap and pinch step the zoom. Cache 128 tiles and clear on zoom.
- Decode tiles synchronously on the first draw. A thread pool plus
  `postInvalidate` looked correct but never repainted inside a ScrollView;
  256 px PNG decode is cheap enough to block for.
- Add a crosshair, scale bar (`156543.03392 * cos(lat) / 2^z`), GPS dot and
  labelled waypoint pins. Public Download may be permission-restricted under a
  modern targetSdk - also offer an app-folder import path that needs no grant.

## 13. Additive health data and advanced protocol sets

- Store conditions and allergies as comma lists; derive the old bit flags only
  for the maths. Never reduce a person to one binary attribute.
- Build multi-select chips from a `boolean[]` (two per row), then join at save
  time; keep free-text medicines and status beside them.
- Advanced-only content: separate arrays, rendered behind the switch, with a
  line telling the user how many entries are hidden and how to turn them on.

## 14. Sharing hygiene

- Images: decode and re-encode at JPEG 90 to drop EXIF, including GPS. Serve
  the cleaned file through a `/cache/` branch of the existing provider, validate
  the filename against traversal, and grant read permission on the intent.
- PDF, DOCX, ZIP and APKs keep internal properties; say so instead of implying
  every format is scrubbed.
- Default the switch on, allow a per-send override, and keep the global setting
  in Settings.

## 15. Security audit pass

- Manifest: `allowBackup=false`, `usesCleartextTraffic=false`, no exported
  components except the launcher and widget, no WebView or JavaScript bridge.
- Grep for `Runtime.exec`, raw SQL concatenation, path traversal in providers.
- Gate sensitive screens in both the tile handler and the activity itself; a
  USB descriptor view counts as sensitive when a passcode is set.
- Write the findings and the residual risk (app-level lock is not full-disk
  encryption) into the build document rather than implying perfect safety.

## 16. Launcher icon as a single vector

- One 108 dp `res/drawable/ic_launcher.xml` beats a pile of PNG densities. Draw a
  rounded plate with an arc path, then the mark with fill and stroke paths.
- Near-background rather than pure background (`#10151C` against `#0B0E12`) so
  the icon still reads as an object on a dark launcher.
- Verify by opening the system App info and looking at it; the launcher masks
  the corners itself on API 26+.

## 17. First-run permission review

- Put a `Consent` preference layer beside the Android grants: `consent_ok`, an
  optional prompt-every-start, and one switch per capability.
- The review lists real state (`granted` / `not granted` / `granted at install`)
  and a separate "never requested" section naming contacts, camera, microphone,
  SMS, call log, calendar, body sensors. If it is not in the manifest, say so
  rather than showing a fake toggle.
- App-level switches must actually gate features (survey, peers, location) or
  they are decoration. Give a button to the system app settings for the real
  grant, and say that the app cannot grant itself anything.
- Open the review from the main activity only when it is needed, and expose the
  same toggle plus a review button in Settings.

## 18. Stars and Kiwix deep links

- Calculator favourites: a star `TextView` inside the row (it consumes its own
  click so the row still opens the tool), ids in one comma preference, starred
  tools rendered first.
- Library favourites: `title|book|page` lines. Try the exact `content://`,
  `kiwix://` or `http(s)://` link first, then Kiwix's content and scheme forms,
  then open Kiwix and say the version did not take the link. Never claim a deep
  link works without testing the installed app.
- Keep an explicit empty state and an outline star to remove, so the list is
  recoverable without a settings trip.
- Newer Kiwix uses `zim://<book id>/<page>`; keep the content-provider and
  `kiwix://` forms as fallbacks, and preserve `/` in the page path.

## 19. Scan results that stay useful

- Give every result row a click that opens an analysis dialog, not just a log
  line. Include address, radio detail, RSSI, rough distance, OUI guess, time and
  a share action.
- Keep pins in a separate sealed preference (`kind:id`), and also mirror them
  into the roster. Never rely on the scan log to remember a pin.
- Sort pinned items first in both live and stored rendering, give them a distinct
  border and label, and re-render on every new sighting so the pin jumps to the
  top immediately.
- On resume, compare the stored on/off setting with what the screen was built
  with and recreate if it changed. Auto-start one scan when the live screen
  opens; stop and store on pause. This removes stale “off” screens without a
  background service.
- A MAC is not an identity. Label signal distance and vendor as estimates, and
  do not call a device “the same phone” when the address may be randomised.

## 20. Folding a long settings page

- Build the cards normally, then post-process the top-level card list: use the
  first small title as the header, move the remaining children into a hidden
  body, and toggle it with `+` / `−`. No per-section rewrite is needed.
- Keep all folds closed on entry and add one line telling the user they are
  folds. This turns a 600-line scroll into a compact index.
- For a start-screen grid, give every tile its own muted edge colour from one
  array; for the one tile that must be stronger, use a second pixel of stroke
  and a slightly warmer title, not a saturated alarm colour.

## 21. First-run orientation

- A consent screen answers “what may this app use”. Add a separate intro for
  “what this app is”: an index and tools, with the articles, books, maps and
  media downloaded separately into Kiwix / MBTiles.
- Mark the intro seen on both the primary button and Back, or Main will reopen it
  every launch. Start it after approval; do not stack it over the consent gate.
- Repeat the same truth in Help, and list the first-hour setup steps there.

## 22. Map fallback and map tools

- Do not hard-code a map app. Store a package preference with an empty default,
  query `geo:` activities, offer `none` first, and send a normal
  `geo:0,0?q=lat,lon(label)` intent so the two apps exchange a point without
  reading each other's data.
- Add `<queries>` for the `geo` scheme and any known reader packages when
  targeting Android 11+.
- Useful map tools are cheap: go to validated coordinates, copy the crosshair,
  share it as text plus a geo link, and send the crosshair or a waypoint to the
  fallback app.

## 23. Kiwix starter shelf

- A ZIM book id contains a download date, so a hard-coded article link is almost
  always wrong. Provide a small curated shelf that opens Kiwix search, then let
  the user star the real page with the book id Kiwix shows.
- Store shelf entries as `title||search:term`, mark them as searches in the list,
  and handle that prefix before the normal deep-link ladder.
- Try `ACTION_SEARCH` first, then a plain Kiwix launch with an honest toast.
  Never claim a deep link or search works on a build you have not tested.

## 24. Missing companion apps

- Check both the normal and standalone package names. If neither resolves, show
  one clear dialog: this app is an index, the companion holds the content, and
  the user can download it now or decline. A declined dialog must leave a
  visible no-content state and a download/check action, not a silent failure.
- `Download` can open the official download page; returning to the screen must
  re-check the package. If the user used that download action, opening the
  companion once on return is a reasonable connection, but keep a manual
  `CHECK AGAIN` path for installs done elsewhere.
- Never render fake articles or placeholder content when the companion is
  absent. Say plainly that there are no articles yet.

## 25. GPX and waypoint relativity

- Store waypoints as `name|lat|lon`; compute distance and bearing with
  `Location.distanceBetween` from the latest fix, falling back to the map
  crosshair. Label which origin was used; a crosshair is not a GPS fix.
- GPX import: cap the input and point count, reject `DOCTYPE`/`ENTITY`, validate
  lat/lon, sanitise names and deduplicate. Export with XML escaping and a normal
  GPX 1.1 header. Use fixed app-private filenames plus the system file picker;
  share through a provider and validate the cache filename against traversal.
- Rebuild waypoint metrics after a map move with a debounce, not on every touch
  event, or a small list makes panning stutter.

## 26. Adding serial bridges without a HAL

- Detect the chip by VID/PID and print which driver will be attempted before
  opening. CP210x, CH340/CH341, FTDI and CDC-ACM need different vendor requests;
  never send CDC requests to an unknown vendor interface. Refuse unknown
  vendor-specific interfaces explicitly.
- Vendor init sequences are best-effort and need a real board to verify. Keep the
  claim honest in the UI and build notes.
- For both USB and Bluetooth consoles, add line-ending choice, hex send/display,
  timestamps, buffer copy and share. One shared text helper avoids drift between
  the two screens.

## 27. A sealed journal and peer text

- Store journal entries as `timestamp|base64(text)` lines, seal the whole blob
  with the same AES-GCM vault as the survey, and gate the tile and the activity.
  State plainly when Android 5 has no passcode to derive a key from.
- Keep peer text inside the existing AES-GCM packet, cap and sanitise it, and
  never create a separate plaintext message store. Show last-seen/distance/
  bearing alongside it, blanking them when either side has no fix.

## 28. Permission drift and reference inspection

- On resume, compare enabled app-level switches to real runtime grants and show
  a banner with an Android app-settings shortcut. A self-imposed switch is not a
  grant.
- Reference apps: inspect manifests/workflows and cleanly re-implement patterns;
  never paste proprietary or copyleft code into a differently licensed app.
  Record exactly what was installed, what was only source-inspected, and what
  could not be obtained. A missing reference app is a limitation, not a pass.

## 29. USB device role and a PC that answers

- `UsbManager.getDeviceList()` is a host-mode API. When the phone is plugged into
  a PC, the phone is the device and the PC is the host, so the PC is absent by
  design. Do not spend time trying to make it appear in that list.
- Read the sticky `android.hardware.usb.action.USB_STATE` broadcast plus
  `BatteryManager.EXTRA_PLUGGED` to distinguish no cable, charge-only and a data
  link (`ADB`/`MTP`). Android does not expose the upstream host's name; say so.
- For a PC response over USB, run a TCP server on `127.0.0.1` and document the
  PC-side `adb forward tcp:PORT tcp:PORT` command, then any TCP client. This is
  an ADB transport, not raw USB serial. Require USB debugging and a data cable.
- Keep Bluetooth SPP as the no-ADB route. Never call the ADB workaround a "USB
  serial port" or imply that Android apps can enumerate the host PC.
