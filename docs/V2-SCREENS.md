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

The rebuild below reproduces V1's *information architecture*: the same section
labels, the same order, the same controls in the same place. Where V2 cannot do
what a section did, the section keeps V1's position and says so in one line,
rather than disappearing or pretending to a switch it does not have.

| Screen | Files | V1 structure reproduced | What differs, said on the screen |
|---|---|---|---|
| Home | `00_home` | header block, rule, 3x4 grid at V1's geometry | the grid is first, as in V1; the SOS card and the radio card sit *below* it because V1 has neither |
| Profile | `01_profile` | name, body weight, age, ten condition chips, six allergy chips, other allergies, medicines, three workload chips | V1 saved as you typed; this build has one SAVE, because its store writes on demand |
| Settings | `02_settings` | thirteen folded sections in V1's order, a plus opening to a minus | PASSCODE, APPEARANCE, ADVANCED MODE, ACTIVE SURVEY, FIELDKIT PEERS, SAVED ENTITIES, LOW BATTERY MODE and the WIDGET each state in one line that this build does not back them |
| Medical | `03_medical` | title EMERGENCY, back ALL TOOLS, IF YOU SEE / OPEN table, › TAG on the right, tap to read | nine protocols against V1's forty; the count is printed, not implied |
| Calculator | `04_calculator` | numbered rows 01..10 with a name, a one-line detail and › | four sums, and the footnote says so; the shipped ten needed a map, a radio or a library |
| Link | `05_link` | USB-OTG, THIS PC, BLUETOOTH, SURVEY - ACTIVE SCAN, in that order | only BLUETOOTH works (classic serial send); the other three say they are not in this build |
| Share | `06_share` | the whole app, your data, metadata before it leaves, any file | only the data export/restore is implemented; the other three say why not |
| Screens | `07_screens` | NIGHTLY ROUTINE - ONE THING PER LINE, SET AS LOCK SCREEN, the home-screen card, the size footnote | the box paints exactly the lines typed into it; there is no built-in home background set |
| Library | `08_library` | KIWIX card, STARTER SHELF, ON THIS PHONE, FAVOURITES | the shelf and favourites are named but not implemented: no deep link into Kiwix; the list and counts are real |
| Journal | `09_journal` | NEW ENTRY card, text area, SEAL AND ADD, what is stored, CLEAR THE WHOLE JOURNAL | V1 used the app passcode; each entry here is sealed on its own, so the passphrase has its own field |
| Maps | `10_maps` | MAP FILE, THE MAP, MAP TOOLS, WAYPOINTS | only the file check exists; no renderer, so no crosshair, zoom, position or GPX |
| Comms | `11_comms` | V2's own structure, restyled | V1's comms screen could not be dumped from the shipped APK, so this is the one screen whose order is not copied |
| Help | `12_help` | THIS DEVICE, WHAT THIS IS, WHAT THIS BUILD DOES NOT DO | live facts plus an explicit list of absences |
| SOS panel | `13_sos` | V2 only | V1 has no distress panel; it uses the same frame and says plainly when the phone has no flash |

## What was checked, and how

- Structure: V1's full scrollable contents were dumped per tile (`app/build/v1full/*.txt`)
  by tapping each tile by its accessibility bounds, then compared against the
  rebuilt screens. Earlier dumps had tapped the wrong coordinate and produced the
  Settings screen for every tile; that is why this pass started from a layout
  dump rather than from remembered screenshots.
- Interaction: on `fk34ui`, a condition chip was tapped (it lit and persisted)
  and the OWNER section was opened (the \+\ became –\ and the fields appeared).
- Whole app: all twelve tiles plus SOS opened with an empty crash buffer, same pid
  before and after.
- Not checked: the SM-A505F phone had no ADB connection during this pass, so the
  restyle has still only run on the API 34 emulator.

