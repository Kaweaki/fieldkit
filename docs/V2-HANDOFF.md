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

Verified on the emulator after the restyle: home, Profile, Settings, Link, SOS.
Verified on the SM-A505F: the tour and SOS, **but before the restyle** — the phone
has not seen this design yet.

## What is left, in order

1. **Compare the restyled tiles against V1 pixel by pixel.** Captures exist for all
   twelve; only home, Profile, Settings, Link and SOS were reviewed. Fix what
   differs for no reason and record deliberate differences in `docs/V2-SCREENS.md`.
2. **Finish the claims audit.** Walk every button, tile, card and caption and
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
