# V2 against V1, screen by screen

Both builds were captured on the **same** device: the `fk34ui` AVD (Android 14 /
API 34, full AOSP image, 1080x2340 at 440 dpi). V1 is the shipped `2.8 (19)` APK
(`app/build/pulled/FieldKit-2.8.apk`, installed from the phone's own package);
V2 is built by `app/build-v2.sh`. Nothing here was reconstructed by hand: V1's
activity has no source in this repository, so its APK is the reference.

Regenerate the set with

```
ADB=<path to adb> FK_SERIAL=emulator-5556 py tools/v2/capture.py v1
ADB=<path to adb> FK_SERIAL=emulator-5556 py tools/v2/capture.py v2
```

The PNGs in `docs/screens/<build>/` are half size; each has a `.txt` beside it
with the same screen's accessibility bounds, so geometry differences can be
proved with numbers rather than by eye.

## The design tokens, measured from the 2.8 build

Everything below was sampled from V1's own pixels at 440 dpi, not chosen.

| Token | V1 value | Where it shows |
|---|---|---|
| page | `#0B0E12` | behind everything |
| panel | `#151A21` | every card and tile |
| field | `#07090C` | text inputs |
| chip | `#1A2028` | toggle buttons |
| border | `#272F3A` | card, field and secondary-button outlines |
| text | `#E7EEF5` | names, values |
| muted | `#8493A2` | subtitles, section labels, tile captions |
| dim | `#6C7A88` | the smallest print |
| accent | `#FFB000` | titles, primary buttons, the rule |
| margins | 16 dp | screen edge to content |
| card corners | 24 dp | cards, tiles, buttons |
| control height | 48 dp | buttons, chips |
| tile | 316 x 264 px, 8 dp gaps | the home grid |
| header | grey caps build label, 44 sp title, 15 sp subtitle, amber rule fading right | home |
| detail header | outlined back pill, 30 sp amber caps title, 15 sp subtitle, same rule | every tile |

Tile accents, one per tile, as V1 draws them: Profile `#6E93B8`, Settings
`#8A96A3`, Medical `#C76B63` on a `#1B1313` fill, Calculator `#C79A4E`, Link
`#8A7FB8`, Share `#5A9E94`, Screens `#B78862`, Library `#78A67F`, Journal
`#B39B6E`, Maps `#6C8FC4`, Comms `#A984A9`, Help `#8296A8`.

Primary and secondary controls are a fixed pair in V1: the one main action on a
screen is **filled amber with near-black caps**, everything else is a **thin
outlined pill with amber caps**. V2 now follows that rule (`Ui.action` versus
`Ui.button`).

## Per-screen verdict

| Screen | Files | Verdict | Reason |
|---|---|---|---|
| Home grid | `00_home` | identical frame, deliberate content | same header block, rule, 3x4 grid, tile geometry, fills and accents; V2 adds the SOS card and shows its own RADIO LOCKED card, and its tile captions name what V2 actually does (9 protocols, not 40) |
| Profile | `01_profile` | identical frame, deliberate content | V1's back pill, title, subtitle and rule; fields are labelled and grouped in a card like V1's, but the fields are V2's (weight, people, days) and the result block is V2's |
| Settings | `02_settings` | identical frame, different content | V1 folds sections, V2 shows one card per group; every box is labelled |
| Medical | `03_medical` | V1 vocabulary, V2 content | titled EMERGENCY with back "ALL TOOLS" as V1 does; nine protocols against V1's forty, so the subtitle is V1's and the count is not claimed |
| Calculator | `04_calculator` | identical frame, V2 content | grey caps section labels, labelled inputs; four sums instead of V1's ten |
| Link | `05_link`, `14_link_noperm` | deliberately different | V1 is a USB/OTG/PC/BLE survey screen; V2 is Bluetooth text only, so its subtitle describes that and the permission card appears only while the permission is actually missing |
| Share | `06_share` | identical frame, V2 content | V1 "send the whole app"; V2 exports the app's own data, and says so |
| Screens | `07_screens` | identical frame, V2 content | all-wallpaper tooling in V1, ICE card on the lock screen in V2 |
| Library | `08_library` | deliberately different | V1 opens a Kiwix-missing dialog; V2 counts ZIMs and explains the scoped-storage limit in the screen itself |
| Journal | `09_journal` | identical frame, V2 content | V1 title FIELD JOURNAL, V2 uses the same title and a labelled passphrase/note card |
| Maps | `10_maps` | deliberately different | titled MAP as V1; V2 inspects an .mbtiles file and does not draw tiles, which the subtitle states |
| Comms | `11_comms` | identical frame, V2 content | Briar detection plus a list of what this app carries itself |
| Help | `12_help` | identical frame, V2 content | live device facts and an explicit list of what the build does not do |
| SOS panel | `13_sos` | V2 only, deliberate | V1 has no distress screen; V2 uses the shared frame and the panel says plainly when the phone has no flash |

Status of the review: home, profile, settings, link and the SOS panel were
compared against V1 and restyled in this pass. The remaining tiles were captured
after the restyle but have **not yet been compared pixel by pixel** — that is the
first job listed in `docs/V2-HANDOFF.md`.
