# FieldKit V2: where this stands, and how to pick it up

Written at the end of the session that restyled V2 against the shipped 2.8 build.
The branch is `v2`; everything described here is committed and pushed.

## What the mission is

Make V2 designwise indistinguishable from V1, prove it with paired screenshots on
one device, and make sure no screen claims anything the code does not do. Then
ship: rebuilt APK, unit tests, `apkcheck`, all twelve tiles plus SOS on the API 34
emulator **and** the SM-A505F, empty crash buffer on both.

## What is done

- **An API 34 emulator exists and works.** Android 14, full AOSP image, hardware
  accelerated. Details and commands below.
- **V1 runs beside V2 on the same device.** `app/build/pulled/FieldKit-2.8.apk` is
  the real `2.8 (19)` build pulled from the phone, not a reconstruction.
- **The design was measured, not guessed.** V1 ships no colour or dimension
  resources (its APK contains one layout file, for the widget), so every token in
  `docs/V2-SCREENS.md` was sampled from its rendered pixels and its accessibility
  bounds.
- **V2 was restyled to those tokens**: panel `#151A21`, borders `#272F3A`, fields
  `#07090C`, muted `#8493A2`, 16 dp margins, 24 dp corners, 48 dp controls, the
  outlined back pill, 30 sp amber caps titles, grey caps section labels, and the
  V1 rule that the one main action on a screen is a **filled amber** button and
  everything else is an outlined pill. The per-tile accents are V1's own values.
- **Unlabelled fields were fixed.** Profile had three bare number boxes; every
  form now has V1-style labels and cards (Profile, Settings, Journal, Calculator).
- **A duplicated paragraph was fixed** on Link: the permission explanation was
  printed by both the card and the `SecurityException` catch.
- **Earlier fixes still stand**: answering the permission dialog no longer throws
  you home, the Link card appears only while the permission is missing, the
  Android 12 splash is dark, and `app/build-v2.sh` actually produces a complete APK.
- `docs/screens/{v1,v2}/` holds the paired captures taken on that one device, each
  with a `.txt` of the same screen's geometry.

## The structure pass, and why it was needed

Matching the palette was not enough, and the owner said so: the tiles were not
"in the same place" as V1. The cause was mundane and worth writing down. The
blueprint script that was supposed to open each V1 tile tapped `x=540`, which is
the middle column, so Profile, Settings, Emergency and Calculator were all dumping
the *Settings* screen and the comparison had nothing to compare. The dumps were
redone by tapping each tile's accessibility bounds; V1's full scrollable contents
per tile are in `app/build/v1full/*.txt`.

With those in hand, the screens were rebuilt on V1's information architecture
rather than its colours:

- New shared components in `Ui`: `section(context, label, title, detail)` for
  V1's card with a grey caps label over a white line over a grey line,
  `row(context, left, right, listener)` for its `text ... › TAG` rows,
  `divider`, and `toolRow` for the numbered calculator rows. `Tools.accordion`
  and `Tools.tool` open a section in place, with V1's plus turning into a minus.
- **Profile** is V1's form again: name, body weight, age, ten condition chips,
  six allergy chips plus "other", regular medicines, three workload chips.
- **Settings** is V1's folded list of thirteen sections in its own order.
- **Emergency** is V1's `IF YOU SEE / OPEN` table with a `› TAG` on the right.
- **Calculator** is a numbered list (01-04) of named sums that open in place.
- **Link, Share, Screens, Library, Journal and Maps** follow V1's card order;
  Screens now paints exactly the lines typed into its routine box, and Library is
  Kiwix / starter shelf / on this phone / favourites.
- The SOS bar moved below the grid, because V1 has no SOS card and the top of the
  home screen should stay where V1 puts it.

Sections V2 cannot back (USB, PC link, survey, metadata wipe, APK sharing, GPX,
widget, app lock) keep V1's position and say so in one line. That is the rule this
build is held to: same place, honest words.

## What has been verified, and what has not

Verified on `fk34ui` after the rebuild: every screen renders in V1's order, a
condition chip lights and persists, the OWNER section opens, and all twelve tiles
plus SOS open with an empty crash buffer and the same pid. The JVM self test is
58 passed, 0 failed; `tools/apkcheck.py` is 6 passed, 2 warned, 0 failed.

Not verified: the SM-A505F had no ADB connection during this pass, so the phone
has still only seen the pre-restyle build. Run the tour on it before calling the
two apps done — it is the one device that is not an emulator.

## What is left, in order

1. **Compare the restyled tiles against V1 pixel by pixel.** Captures exist for all
   twelve; only home, Profile, Settings, Link and SOS were reviewed. Fix what
   differs for no reason and record deliberate differences in `docs/V2-SCREENS.md`.
   *The type scale this exposed has been fixed in code (see the 2026-11-24 update
   below); the re-capture that proves it is still outstanding.*
2. **Finish the claims audit.** *Audited 2026-11-24: all four compute buttons,
   both backup paths, the share sheet and the journal/seal path now have three
   more fixes recorded below; the walk is done for this build.* Walk every
   button, tile, card and caption and
   confirm the code behind it. Known-good so far: the torch Morse (logged by the
   camera service on the phone), the tone, the lock-screen-only wallpaper
   (`FLAG_LOCK`, verified by wallpaper record), the AES-256-GCM journal round trip,
   the SAF folder picker, the `.mbtiles` inspection, the Briar detection. Still to
   check after the restyle: every compute button, both backup paths, the share
   sheet, and that no label promises more than the number it shows.
3. **Re-capture Link** now that the duplicate paragraph is fixed, and capture the
   granted state (`pm grant se.blimark.fieldkit.v2 android.permission.BLUETOOTH_SCAN`
   and `…BLUETOOTH_CONNECT`), since `pm revoke` suppresses the system dialog
   afterwards. *Done on the phone 2026-11-24: both states captured; on API 29 the
   permission is `ACCESS_FINE_LOCATION`, and granted it lists two paired devices.*
4. **Re-verify on the SM-A505F** with the restyled build: twelve tiles, SOS
   reaching SIGNALLING, empty crash buffer. The phone is API 29, so its Link screen
   must still show the pre-12 location wording. *Done 2026-11-24: twelve tiles,
   SIGNALLING with 16 torch events, empty crash buffer, pre-12 wording shown.*
5. Re-run `build-v2.sh`, the unit tests and `apkcheck`, and commit the result.

## Environment, from nothing

The machine used was Windows 10 Home; nothing below is preinstalled.

```
# SDK (put it somewhere durable; %LOCALAPPDATA%\Temp is wiped eventually)
sdkmanager "platform-tools" "emulator" "build-tools;35.0.0" "platforms;android-36" \
           "system-images;android-34;aosp_atd;x86_64" \
           "system-images;android-34;default;x86_64" \
           "extras;google;Android_Emulator_Hypervisor_Driver"
```

The emulator refuses software CPU emulation (`-accel off` answers "not supported"),
so a hypervisor is mandatory. On Windows that is AEHD, installed once with admin
rights:

```
cmd /c "...\extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat"
emulator -accel-check        # must say "AEHD (version 2.2) is installed and usable"
```

Two AVDs, both API 34:

```
avdmanager create avd -n fk34   -k "system-images;android-34;aosp_atd;x86_64"
avdmanager create avd -n fk34ui -k "system-images;android-34;default;x86_64"
```

`fk34` (ATD) boots in about 100 s and is the fastest way to run the tour, but it
has **no display pipeline and no wallpaper service** — screenshots come out black
and `dumpsys wallpaper` answers "Can't find service". `fk34ui` is the full image:
use it for anything visual.

```
emulator -avd fk34ui -no-window -no-audio -no-boot-anim -no-metrics \
         -no-snapshot -gpu swiftshader_indirect -port 5556
```

Both AVDs need `hw.ramSize=2048` and a 1080x2340/440 lcd in `config.ini`; the
default ATD profile otherwise comes with 96 MB of RAM.

## Running things

```
# build (needs JDK 17 on PATH, an SDK, and a keystore; debug signing is fine)
ANDROID_HOME=<sdk> KS=<keystore> ./app/build-v2.sh        # -> app/build/FieldKit-V2.apk

# unit tests and the APK contract check
python -m unittest discover -s tests -t .
python tools/apkcheck.py app/build/FieldKit-V2.apk

# all twelve tiles + SOS on whatever device FK_SERIAL names
ADB=<sdk>/platform-tools/adb FK_SERIAL=emulator-5556 python tools/v2/tour.py \
    --apk app/build/FieldKit-V2.apk

# paired screenshots for V1 and V2
ADB=... FK_SERIAL=emulator-5556 python tools/v2/capture.py v1
ADB=... FK_SERIAL=emulator-5556 python tools/v2/capture.py v2
```

The scripts find `adb` from `$ADB`, then `PATH`, then `$ANDROID_HOME/platform-tools`,
so they work on another machine unchanged. Everything is driven through the
accessibility tree, which is why it also works on a headless image.

## Things worth knowing before changing anything

- **V2 cannot read V1's source** — the 2.8 activity is not in this repository. The
  APK is the specification; measure it, do not invent it.
- **`Ui.action` is the filled amber button, `Ui.button` is the outlined pill.** That
  single rule is most of V1's button language.
- **Screen frames come from `Tools.screen(activity, title, subtitle, [backLabel], back)`**
  which draws the back pill, the 30 sp caps title, the subtitle and the rule. A new
  screen that builds its own header will drift immediately.
- **V1 has two screens V2 has no counterpart for**: a permissions consent screen and
  a one-time "READ THIS ONCE" intro. The mission's screen list did not include
  them. Decide whether V2 needs them before claiming the two apps match screen for
  screen.
- Do not restyle V1 to match V2. V1 is the reference.

## Session update, 2026-11-24

The machine this ran on had no JDK 17, no SDK and no adb. JDK 17 and the SDK were
installed at `C:\Users\dator\android-sdk`; the build, the self test, the Python
suite and `apkcheck` all run again.

### Fixed (the claims audit, item 2 above)

- **Calculator 01 gave clear water the cloudy dose.** It passed `!dirty` where
  `Water.bleachMl` expects `cloudy`, then multiplied by two as well, so clear
  water was treated at 4 mg/L instead of 2 - a double dose of bleach. The flag
  now goes straight through, and the card prints the dose it used (2 mg/L clear,
  4 mg/L cloudy, 30 minutes), which the wiring doc already promised.
- **Link's Bluetooth caption promised a scan.** It read "Scan, read services,
  send commands" while the code lists bonded devices and sends one serial line,
  and the card underneath says it never scans. Now "Paired phones, one framed
  line each way".
- **The lock-screen claim was unconditional.** `Kit.wallpaper` can only use
  `FLAG_LOCK` from API 24; below that `setBitmap` sets both surfaces, while the
  toast said "Lock screen updated". The toast and the Screens note now say so.
- Calculator 03's row detail said "battery amp-hours" over a field asking for mAh.
- **Calculator 04 always dosed for cloudy water.** It passed `cloudy = true`
  outright, so the bleach figure was 4 mg/L even for water you can see through,
  and nothing on screen said which dose it had used. The sentence now reads
  "... to treat it all if it is cloudy", so the number matches the words.
- **Calculator 03's two assumptions were invisible.** Its answers are 85% of a
  battery's rated mAh (converter loss) and 70% of a panel's rating (real sun);
  the card printed the reduced numbers with no word about why they are below
  the arithmetic. The card now states both.
- **A lost backup was reported as written.** If the process was rebuilt while
  the file picker was open, the export text was gone and the save still wrote an
  empty file and said "Backup written". It now says the backup was lost and
  writes nothing.
- Checked and found honest, so left alone: the copy-to-clipboard and share-the-
  message buttons on SOS (the system chooser does the sending, and the screen
  says this app never goes near a network), the SAF save and the SAF restore
  (a restore really does clear and replace every stored value, which the screen
  says), the send-the-backup chooser, and all four compute buttons' arithmetic
  against `Water`/`Power`.

### Verified this session

Build OK; `SelfTest` 58 passed, 0 failed; `python -m unittest discover -s tests -t .`
30 tests OK; `apkcheck` 5 passed, 3 warned, 0 failed, 0 skipped.

### Measured but not changed (item 1), with the numbers

Comparing the committed bounds in `docs/screens/v1` against `docs/screens/v2`:

| | V1 | V2 |
|---|---|---|
| home build label height | 36 px | 48 px |
| home title height | 125 px | 161 px |
| detail title height | 81 px | 111 px |
| subtitle height | 44 px | 56 px |
| tile caption height | 36 px | 48 px |
| form field height | 96-103 px | 125 px |
| chip row pitch | 143 px | 154 px |
| Profile field labels | `Name` | `NAME` |

V2 still runs about a third larger than V1 on every text size. Profile's field
labels are caps where V1's form is sentence case; this is Profile-only, because
V1's tile-screen headers ("USB - OTG", "THIS PC") genuinely are caps. The cost
is visible in the pair: V1 fits Profile down to the allergy chips on one screen,
V2 has not reached the Allergies label by the bottom of the frame. V1's Link
header also carries a `⋮` overflow control that V2 does not draw.

None of this was changed, because the phone was unreachable and an emulator is
not set up on this machine: every one of those numbers would have been altered
with no way to re-capture and compare. They are the next thing to do with a
device attached.

### Type scale corrected (item 1)

The drift table above said V2 runs a third larger than V1 on every text size. The
cause is now known, and it was not a font scale: the tokens had been read off
accessibility *view* heights, and a TextView's height is its font padding plus a
line, not its type size. Measured in ink instead - the same string's cap height
in both captures, with V1's half-size PNG doubled so both are in 1080 px - V1's
detail title is 40 px of ink, which is 22 sp, not the 30 sp the table claimed.
The status bar's own text measures 34 px in both files, which is what rules out
different system font scales on the two captures.

`Ui` now carries one named scale - `TITLE_HOME` 33, `TITLE_DETAIL` 22, `HEAD` 12,
`BODY` 11, `NOTE` 10, `LABEL` 9, `INPUT` 13, `PILL` 10, `TILE_TITLE` 12,
`TILE_SUB` 10 - and no call site passes a bare number any more. Fixed with it: the
header rule is V1's 1 dp instead of 2; chips sit 4 dp apart instead of 8 (V1's
pitch is 143 px, ours was 154); a field is 13 sp in a 96 px box, matching V1's
96-103; and form labels are sentence case, "Name" not "NAME", because V1's forms
are - that is the one place V1 does not use caps.

**Not verified visually.** No device answered while this was written, so the new
numbers have not been re-captured against V1. Build, self test, the Python suite
and `apkcheck` are green; the pixel comparison is the first thing to run when the
phone is back.

### On the SM-A505F

The handset answered and was authorized, so items 3 and 4 were run on it. What
it is: Android 10, **API 29**, 1080x2340 at **420 dpi**, and **font_scale 1.1** -
the owner has enlarged system text, so every sp draws about 10% larger than on
the 440 dpi emulator. That alone explains most of the "V2 is 10-13% bigger"
reading against the emulator captures: V2 is sp-based on purpose and follows the
setting, and V1 does too.

The old `se.blimark.fieldkit.v2` on the phone was a **release-signed** `2.9 (21)`
from the previous session, so `adb install -r` was refused with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. `pm uninstall -k` keeps the data but still
blocks the install, and that data cannot be read from a non-debuggable build, so
the package was removed and installed fresh. **The old test build's settings and
journal are gone**; V1 (`se.blimark.fieldkit`, `2.8 (19)`) was not touched, and
its profile, journal and permissions are as they were. Every later build on this
phone must use the same keystore, or it will wipe again - see
`docs/COMPATIBILITY.md`.

Verified on the phone:

- `tools/v2/tour.py`: all twelve tiles open onto their own screen, SOS reaches
  SIGNALLING with **16 torch events** logged by the camera service against the
  app's pid, the crash buffer is **empty**, and the pid is stable across the SOS
  start and stop (20679 before and after). The pid does change from tile to
  tile because `home()` force-stops the app between screens; that is the tour's
  design, not a restart of the UI.
- **Link, both states.** With the permission missing it shows the API 29 wording
  ("Android 11 and earlier need Location for a Bluetooth scan"), which is the
  pre-12 branch working. Granted, it lists the phone's **two real paired
  devices** - `ROUGHBEAT FC:58:FA:8E:FB:15` and
  `ACTON BLUETOOTH 04:FE:A1:61:3D:...` - each with a send button, under the
  message field. So the radio path end to end is live on the handset, not just
  on the emulator.

### The type scale, measured on the phone

With V1 and V2 captured on the **same phone at the same font scale**, the deficit
could finally be measured instead of inferred. (3.9 px per sp on this device):

| Element | V1 | V2 now |
|---|---|---|
| detail title `PROFILE` / `LINK` | 86 px | 86 px |
| home title `FIELDKIT` | 131 px | 127 px |
| subtitle | 47 px | 49 px |
| form label `Name` | 43 px | 43 px |
| field box | 96 px | 93 px |
| chip box / row pitch | 126 / 137 px | 126 / 137 px |
| Allergies label | 1853 px | 1863 px |
| last chip on screen | nuts, latex | nuts, latex |

Two structural findings came out of the phone pair that no emulator capture had
shown:

- **V1 draws Profile as one card.** The gap from the age field to the Conditions
  label measures 18 dp in V1; three stacked cards produce 44 dp, and that alone
  cost V2 a screen's worth of room. Profile is now one card with 16 dp and 10 dp
  spacers, and it lands within 11 px of V1 all the way down.
- **V1 puts the owner's name in the home header**, left of the build label
  (`HARALD BLIMARK` ... `FIELDKIT 2.8`). V2 now does the same, from the same
  stored value the Profile form and the ICE card use.

### What the pixels showed

Comparing the two Profile captures by eye - both on the phone, same font scale -
found the things bounds cannot express:

- **V1's controls are rounded rectangles, not lozenges.** On its top row a chip
  insets 23 px, the back pill 24 px and a field 22 px at 420 dpi, so chips and
  pills are about 10 dp and fields 8 dp. V2 drew 24 dp on a 48 dp chip, which is
  a stadium - the single most obvious "this is a different app" cue in the pair.
  `Ui.RADIUS_CONTROL` (10) and `RADIUS_FIELD` (8) now carry the measured values,
  including the re-draw when a chip is tapped.
- **The header rule is 2 dp.** V1's rule is five pixels tall on the phone; the
  1 dp value in the previous session came from a thresholded reading of the
  emulator capture, not from V1.
- **The home header carries the owner's name**, left of the build label, from
  the same default the Profile form fills in.
- **The SOS panel's one main action is filled amber** - V1's button language -
  instead of a muted red fill with dark text that read as a disabled control.

`tools/v2/emu.py` also learned to fall back to a launcher start: on this phone
`am start` answers "intent has been delivered to currently running top-most
instance" and leaves Settings in front, so the documented capture command was
capturing the wrong screen for V1.

### The app's own paths, exercised on the phone

- **Calculator 01, clear water:** `0,84 mL of 5% bleach, about 17 drops, wait 30
  minutes` - the 2 mg/L dose. With `Cloudy water? 1`: `1,68 mL ... 34 drops` -
  the 4 mg/L dose. So the double-dose bug that opened this session is fixed in
  the UI, not only in `Water`.
- **Journal:** sealing a 19-character entry reports "Sealed. 19 characters
  written.", reopening with the same passphrase reports "Opened. 19
  characters.", a wrong passphrase reports "That passphrase does not open this
  journal.", and clearing reports "Deleted." The test entry was cleared again;
  the phone keeps no test data.
- **Link:** both states, listing the two paired devices above.

Not exercised, and why: the lock-screen wallpaper (it replaces the owner's own
lock screen, and the earlier session already verified `FLAG_LOCK` through the
wallpaper record), and a Bluetooth send (neither paired device is a serial port,
so a send would only exercise the error path). Those two are the next real
tests.

### Backup and restore through the system picker, on the phone

The session before this one wrote that the document picker "needs a person to
choose a folder". **That was wrong**, and this run disproved it: the picker is
driven end to end with `uiautomator dump` and taps, no human needed.

- **Save.** `SAVE AS A FILE` opens `ACTION_CREATE_DOCUMENT` straight into
  *Nedladdningar* - the folder the picker was last used in, so the destination
  is remembered - with the name already filled in
  (`fieldkit-20261124-1255.txt`) and a `SPARA` button at the bottom. Tapping
  that button is the whole interaction. The app answered **`Backup written.`**
  and `/sdcard/Download/fieldkit-20261124-1255.txt` existed at **97 bytes**, byte
  for byte the 97 characters the Share screen had just printed:
  `owner RESTORE TEST`, `weight 99`, `meds test meds`, `conditions asthma`.
- **Restore.** The stored values were then changed inside the app (weight
  99 -> 55, condition asthma -> diabetes) so that a restore had something to
  prove. `RESTORE FROM A FILE` opens `ACTION_OPEN_DOCUMENT` in the same folder;
  the file is the first card. Tapping it produced **`Restored 6 values.`**, the
  home screen came back with `RESTORE TEST` in the header, and Share printed the
  97-character text again - weight 99, asthma back, diabetes gone. So the round
  trip closes on the app's own screens, not only in the file system.
- **What the file does and does not carry.** `Store.all` reads SharedPreferences
  only, so a backup holds the profile and the stored defaults and nothing else.
  The sealed journal is `files/journal.fk` and stays on the phone; the Share
  screen says exactly that ("The sealed journal is a separate file and stays
  where it is"). There is no journal entry in a backup to reopen, which is why
  the right/wrong-passphrase check belongs to the journal screen, where it was
  run.
- **The phone was left clean.** App data went back to its pre-test state through
  the app's own path: a header-only backup restored through the same picker,
  after which Share reads `1 lines, 18 characters`, Profile shows the defaults
  (`HARALD BLIMARK`, 75 kg) and the journal screen says `No journal yet.` Both
  files were deleted from `Downloads` (33 entries before, 33 after, no
  `fieldkit-*` left).

Tooling notes: `build-v2.sh` signs with a debug keystore but the manifest is not
`debuggable`, so `run-as` refuses to look inside the app - every read above went
through the app's own screens, not its files. And on Git Bash `adb push` rewrites
`/sdcard/...` into a Windows path, so putting a file on the phone from this
machine needs `MSYS_NO_PATHCONV=1`.


### The buttons, measured on the phone (2026-11-24, second pass)

The owner's note was that the buttons should feel smooth and that Link's design
was wrong. Both were real, and both were measured rather than judged.

- **Link drew each send button around its label.** `SEND TO ROUGHBEAT
  FC:58:FA:8E:FB:15` came out 748 px wide and `SEND TO ACTON BLUETOOTH
  04:FE:A1:61:3D:82` 867 px, in the tile accent colour, one under the other, so
  the column had no edge and the address was repeated in every label. It is now
  one row per device - the name in white, the address in grey caps under it, a
  divider between - with a `SEND` pill at 242 px on the right. `Ui.deviceRow` and
  `Ui.smallButton` are the components; V1's own list rows are the model
  (`Ui.row`: a line on the left, a tag on the right).
- **Nothing in the UI moved when it was pressed.** Every control is a
  `GradientDrawable`, which draws no ripple, so a tap was a silent state change.
  `Ui.pressable` now settles the view under the finger (scale 0.96, alpha 0.80,
  80 ms, `DecelerateInterpolator`) and releases it a little slower (180 ms,
  `DecelerateInterpolator(1.6)`). Only scale and alpha, so the frames are
  composited; the touch listener returns false, so the click still fires.
  Measured on the SM-A505F by holding a SEND pill for 900 ms and taking a frame
  mid-press: the amber box goes 241x104 to 231x100 px (0.96), the amber pixel
  count 2808 to 2271 and the crop mean 33.0 to 31.4, and after release all three
  are byte-identical to the idle frame. The click that followed reached the
  handler - the screen answered `Type something to send.`

### Two stub sections are now real

- **`THE WHOLE APP` - pass FieldKit on as a file.** Reads the installed package
  from `getPackageCodePath()`, writes it through `ACTION_CREATE_DOCUMENT`. Saved
  `/sdcard/Download/fieldkit-20261124-1321.apk`, 123357 bytes, **SHA-256
  `49a4d12c...`**, identical to `app/build/FieldKit-V2.apk`. The app said
  `APK written, 120 kB, byte for byte.`
- **`METADATA` - strip what a photo says about you.** `se.blimark.fieldkit.Jpeg`
  walks the marker chain, drops APP1, APP13, every other vendor APPn block and
  the comments, keeps APP0 and APP2, and copies the entropy-coded scan data
  verbatim. Verified through the picker on `image0-3.jpg`: source 96542 bytes,
  output 96448, exactly the APP1 (36 bytes) and APP13 (58 bytes) segments gone,
  the 95837 bytes of scan data identical, the decoded pixels identical, and the
  EXIF tag count 1 -> 0. A byte-level reconstruction of "source minus the dropped
  segments" equals the file the app wrote. SelfTest gained seven cases for it
  (65 passed, 0 failed) and `tests/test_java_sources.py` now lists `Jpeg` in
  `PURE`, because it is plain Java on purpose.

### Still stubs, with what each one is actually blocked on

| Section | What it needs |
|---|---|
| `USB - OTG` | USB host code and a board on the cable; nothing on this phone to talk to, so it cannot be verified here |
| `THIS PC` (ADB loopback) | `android.permission.INTERNET`, which this build deliberately does not have; adding it contradicts a promise the Help screen makes - the owner has to decide |
| `SURVEY - ACTIVE SCAN` | a wifi scan and an encrypted log; the same INTERNET story plus a permission prompt on every use |
| `LIBRARY` starter shelf | a shelf of articles, and rule 4 in `AGENTS.md` forbids downloading one |
| `MAPS` crosshair / position / GPX | a map view, a location fix and a waypoint list - a screen's worth of work, not a control |
| `WIDGET`, `APP LOCK`, any-file send | each needs its own screen and its own verification; none of them is a one-liner |

### MAPS is a real screen now (2026-11-24, third pass)

The tile used to open a card saying "not in this build". It now does the three
things the section promised, with no invented data and no new permission.

- **Position, with its source on screen.** `Kit.readFix` asks every enabled
  provider for its last known fix through `LocationManager` and keeps the newest,
  then stores the coordinates together with a sentence that says where they came
  from. On the SM-A505F that produced `56.666975 N, 12.874832 E` under
  `Android last known fix (passive), 90 min old, +/- 15 m`. A typed coordinate is
  accepted as well and is labelled `typed in by hand`. Nothing is polled and no
  request is made: this reads the fix Android already has. `ACCESS_FINE_LOCATION`
  was already declared (capped at API 30, for the Bluetooth scan), so the
  manifest is unchanged.
- **Crosshair.** `Kit.CrosshairView` draws a ring, four ticks and a centre mark on
  the phone - a coordinate readout, not a map. The card says so in one line: "No
  tiles: this draws a crosshair, not a map". No tile is fetched and there is no
  INTERNET permission in the build, so nothing can be.
- **Waypoints and GPX.** A point is stored as `lat, lon, name, source, time` and
  listed on the card; the list is what the export writes. `Geo.gpx` emits GPX 1.1
  and `Geo` is plain Java, so eighteen new self-test cases cover the formatting,
  the range check, the escaping and the document shape.
- **Proven through SAF, byte for byte.** `SAVE THE WAYPOINTS AS GPX` opened the
  same picker as the APK export and wrote `/sdcard/Download/fieldkit-20261124-1339.gpx`:
  353 bytes, parsed on the PC with `xml.etree.ElementTree` - `gpx version="1.1"`
  in the topografix namespace, one `<wpt lat="56.666975" lon="12.874832">`, the
  values character for character what the position card printed, and `<desc>`
  carrying the source and the timestamp.
- **Press response, measured.** A 3 s hold on `READ THE LAST KNOWN FIX`: the amber
  box goes 539x125 to 517x119 px (0.96), the amber pixel count 64044 to 58528 and
  the crop mean 117.04 to 91.80; after release the crop is byte-identical to the
  idle frame. The picker opening after a hold on `SAVE THE WAYPOINTS AS GPX` shows
  the click still fires.

### Every control responds now

`Ui.row`, `Ui.toolRow`, the twelve home tiles, the accordion heads, the emergency
rows and the calculator rows all call `Ui.pressable` as well. That covers all ten
`setOnClickListener` sites in the v2 sources: the four in `Tools` are the
emergency rows, the accordion head, the tool card and the chip grid - and the chip
grid's chip comes from `Ui.chip`, which was already covered.

What MAPS still cannot do, and why: draw map tiles. That needs a renderer and tile
data, and the build has no INTERNET permission by design; the `.mbtiles` card
inspects a file you carry instead of rendering it.

## The cable, the battery, and a screen that arrives (2.9)

**USB inspector.** Link's USB card now reads the phone's own state and opens a
real inspector (`UsbInspect`) instead of promising a host that was not there.
The card shows power source (BatteryManager), host-port device count
(UsbManager.getDeviceList) and whether USB tethering is up, which the kernel
answers through `/sys/class/net` names - `usb0`, `rndis0`, `eth0`. No permission
is declared for any of it. On the SM-A505F with the phone on the PC cable that
reads USB / EMPTY / OFF, and the tether interface list is empty, which is the
truth at that moment.

`INSPECT THE CABLE` opens the inspector. It reports host support
(FEATURE_USB_HOST), the AOA accessory list, and every attached device with its
vendor and product ids resolved against a bundled table. Reading a device needs
Android's own per-device grant: the app registers a receiver for its permission
action, asks with `requestPermission`, and reads the device the moment the answer
arrives. The read opens a `UsbDeviceConnection`, takes `getRawDescriptors()`
(falling back to a `GET_DESCRIPTOR` control transfer), parses it through
`UsbReport`, asks for the language ids (`0x0300`), claims HID interfaces only for
the length of a `0x22` read and releases them, then closes the connection in a
`finally` block. The report is shown as one folded card per section and saved as
JSON through SAF.

`getPorts()` is not reachable from a normal app on this build: the phone answers
the call with a SecurityException, and the screen says exactly that rather than
pretending the port is unknown hardware.

**Battery.** Settings has a BATTERY section: level, charge state, power source
and temperature from the sticky battery intent, the system saver state from
`PowerManager.isPowerSaveMode()`, a DIM THIS APP control that sets the window's
`screenBrightness` to 12 % and reads it back from the window (so the line proves
what the window manager holds), and a button that opens the system battery saver.
On the phone the line read `100%, full, power in from USB, 24.4 C` and
`System battery saver: on`, both matching `dumpsys battery` and
`settings get global low_power`.

**Motion.** Every screen now fades and lifts into place over 190 ms
(`alpha` 0→1, `translationY` 14 dp→0, decelerate) instead of snapping, and the
pills fill their card, as V1's do. The back pill is the one control that keeps
hugging its label.

**Tests.** `UsbReport` is pure Java and carries 34 new self-test cases: a
hand-built descriptor blob (CP2102 ids, one configuration, a bulk and an
interrupt endpoint), the 2 mA and 8 mA power conversions, truncated and empty
blobs, the hex dump, JSON escaping and HID usage pages. 117 tests, 0 failed.
