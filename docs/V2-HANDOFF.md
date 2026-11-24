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
   afterwards.
4. **Re-verify on the SM-A505F** with the restyled build: twelve tiles, SOS
   reaching SIGNALLING, empty crash buffer. The phone is API 29, so its Link screen
   must still show the pre-12 location wording.
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

### Blocked

The SM-A505F answered as `R58M52YG20N unauthorized`, then dropped off the bus, so
`adb install -r`, the granted-state Link capture (item 3) and the phone run
(item 4) are all still outstanding. Reconnect, authorize USB debugging, then:

```
ADB=<sdk>/platform-tools/adb FK_SERIAL=R58M52YG20N python tools/v2/tour.py \
    --apk app/build/FieldKit-V2.apk
```

