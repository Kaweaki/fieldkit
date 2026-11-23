# Wiring the 2.7 classes into FieldKit

The runnable reference for everything below is `app/v2/`: it calls each class
from a real screen, and `app/build-v2.sh` builds it. Read that first when a
wiring question comes up, because it is the version that has been run on a
phone rather than the version that was planned.

Nine small classes in `app/src/se/blimark/fieldkit/` carry the fixes and the new
tools. They are deliberately self-contained — no `Ui`, no activity, no field of
theirs — because the activity source is not in this repository. Wiring each one
is a handful of lines, listed here so nothing has to be guessed.

`app/build.sh` compiles everything under `app/src`, so a class is in the build as
soon as it lands. It is only *reachable* once an activity calls it.

| Class | Reached from | What it needs |
| --- | --- | --- |
| `Perms` | Link, Radio, BLE, Serial, PC link | a call before any scan or connect |
| `Saf` | Map (import), Settings (backup export) | `startActivityForResult` + `onActivityResult` |
| `Insets` | every activity's `onCreate` | one line after `setContentView` |
| `Torch` | a new tile or the Link menu | nothing |
| `Heading` | Map, or the same place as the torch | nothing |
| `Water` | Calculator: a new "water treatment" tool | nothing |
| `Power` | Calculator: a new "power budget" tool | nothing |
| `Ice` | Screens: the lock wallpaper draw | the four preference strings |
| `Backup` | Settings: export / restore | `SharedPreferences.getAll()` |
| `TextLink`, `SppText` | Comms: a FieldKit-to-FieldKit text card | a paired MAC |

## Bluetooth: the release blocker

Every screen that touches the radio needs this before it scans. `ensureRadio`
puts up the system dialog and returns false; run the scan from the callback.

```java
// BtActivity / GattActivity / SppActivity / LinkActivity.onCreate
if (!Perms.ensureRadio(this)) {
    Ui.note(this, "Allow Nearby devices to scan.", false);
    return;
}
```

```java
// same activity
@Override
public void onRequestPermissionsResult(int request, String[] granted, int[] result) {
    super.onRequestPermissionsResult(request, granted, result);
    if (request == Perms.REQ_RADIO) {
        if (Perms.hasRadio(this)) {
            startScan();                       // whatever the screen already calls
        } else {
            showErrorCard("WHAT WENT WRONG", Perms.explain(this));
        }
    }
}
```

`PcLinkActivity` additionally needs `BLUETOOTH_ADVERTISE` for
`ACTION_REQUEST_DISCOVERABLE`; it is declared in the manifest, so the dialog
covers it on Android 12+.

## Edge-to-edge: required above target 34

Android draws edge to edge whether the app asks or not from target 35. Every
screen is a full-bleed `ScrollView`, so without this the header sits under the
clock.

```java
// every onCreate, after setContentView(root)
Insets.apply(root);
```

`Insets.applyBottom(root)` exists for screens that already own their top padding.

## Map import: replaces the storage permission

```java
// the import button
Saf.pickFile(this, Saf.REQ_OPEN_MBTILES);
```

```java
@Override
protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == Saf.REQ_OPEN_MBTILES && result == RESULT_OK && data != null) {
        try {
            File file = Saf.copyInto(this, data.getData(), "fieldkit.mbtiles");
            openMap(file);                       // the existing MBTiles path
        } catch (IOException e) {
            showErrorCard("WHAT WENT WRONG", "That file could not be read. Pick it from "
                    + "the phone again, keeping the screen open until it finishes.");
        }
    }
}
```

The app-folder route stays as the fallback it already is, and the storage
permissions stay capped at API 28 for the phones where a path still works.

## Torch and SOS

```java
if (!Torch.supported(this)) {
    Ui.note(this, Torch.unavailable(this), false);   // says why, never fakes it
}
Torch.toggle(this);          // the light button
Torch.sos(this);             // the SOS button; Torch.stop(this) in onPause
```

No permission is asked: `setTorchMode` needs none from Android 6, and below that
the button explains itself rather than requesting the camera.

## Compass

```java
heading = Heading.start(this, new Heading.Listener() {
    @Override
    public void onHeading(float degrees, String point, float accuracy) {
        bearing.setText(Heading.line(degrees));      // "NW 312 deg"
    }
});
// onPause: if (heading != null) heading.stop();
```

`Heading.start` returns null on a phone without a rotation vector; say so in the
row instead of leaving it blank forever.

## Calculator: two more tools

Add to `ToolsActivity.TITLES`/`SUBS` and to `ToolActivity`:

```java
// water treatment
double ml = Water.bleachMl(percent, litres, cloudy);
int drops = Water.bleachDrops(percent, litres, cloudy);
String line = Water.doseLine(percent, litres, cloudy);
// boiling: Water.boilMinutes(altitudeMetres), settle: Water.settleMinutes()

// power budget
Power.hours(mAh, drawMa);
Power.solarWhPerDay(watts, peakSunHours);
Power.charges(bankWh, phoneWh);
```

Both carry their assumptions on screen: 2 mg/L clear, 4 mg/L cloudy, 30 minutes,
and the 0.85 conversion allowance.

## ICE on the lock wallpaper

The lock art is drawn, not an image, so this is text on an existing canvas.

```java
String[] lines = Ice.lines(owner, blood, allergies, medicines, contact);
// draw each with the same paint the routine lines use, or Ice.summary(lines)
```

Keep it to what the owner chose to publish: this is the one surface a stranger
can read without the passcode. Clear the fields when the passcode is removed.

## Backup export and restore

```java
// export
String text = Backup.encode(prefs.getAll());
Saf.createFile(this, Backup.fileName("2026-11-23"), "text/plain", Saf.REQ_CREATE_BACKUP);
// ... onActivityResult, then:
Saf.writeText(this, data.getData(), text);
```

```java
// restore
Map<String, String> values = Backup.decode(readTheFile());
SharedPreferences.Editor editor = prefs.edit();
for (Map.Entry<String, String> e : values.entrySet()) editor.putString(e.getKey(), e.getValue());
editor.apply();
```

Restoring replaces the listed keys and leaves the rest alone; say that on the
button, because a stale `people_list` overwritten by accident is the one mistake
this feature can cause.

## FieldKit to FieldKit text

```java
String frame = TextLink.frame(myId, nextSequence(), text);
String error = SppText.send(this, pairedMac, frame, 12000);   // "" means sent
if (error.length() > 0) showErrorCard("WHAT WENT WRONG", error);
```

Incoming side: read lines from the existing SPP server, `TextLink.parse(line)`,
ignore null, and show `frame.id` short so two phones are distinguishable. The id
is the app's own random id, never the MAC.

## Copy that still has to change

The Consent screen lists grants in its own words and Help explains what the app
may use. After this release both must say:

- **Nearby devices** (Android 12+): "finds and connects to Bluetooth devices on
  this screen only".
- **Files**: no longer a grant. The picker gives one file at a time, chosen by
  the user; the old switch gates nothing and should go, or be relabelled to what
  it actually controls.
- **Storage on Android 9 and older**: still a real grant, still asked for, and
  the Help text should say it disappears on newer phones.
