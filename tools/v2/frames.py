"""Frame cadence for the V2 transitions and presses, measured on the phone.

The app logs one FieldKitFrames line per screen change and per press touch, built
from the vsync callbacks it receives. This script drives the app over adb and
reads those lines back.
"""
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "v2"))
os.environ.setdefault("FK_SERIAL", "R58M52YG20N")
import emu  # noqa: E402

PKG = emu.PKG_V2


def shell(*args):
    return subprocess.run([emu.ADB, "-s", emu.SERIAL, "shell"] + list(args),
                          capture_output=True).stdout.decode("utf-8", "replace")


def frames_since(mark):
    out = shell("logcat", "-d", "-s", "FieldKitFrames:I")
    lines = [line for line in out.splitlines() if "FieldKitFrames" in line]
    return lines[mark:]


def gfx():
    out = shell("dumpsys", "gfxinfo", PKG)
    keep = []
    for line in out.splitlines():
        if ("Total frames rendered" in line or "Janky frames" in line
                or "50th percentile" in line or "99th percentile" in line):
            keep.append(line.strip())
    return " | ".join(keep)


def center(text, prefix=False):
    for value, _, x1, y1, x2, y2 in emu.dump():
        # A ScrollView reports its off-screen children with 0x0 bounds; tapping one
        # lands in the corner, so only laid-out nodes count.
        if x2 - x1 <= 0 or y2 - y1 <= 0:
            continue
        if (value.startswith(text) if prefix else value == text):
            return (x1 + x2) // 2, (y1 + y2) // 2
    raise SystemExit("no %r on screen" % text)


def step(label, run):
    mark = len(frames_since(0))
    shell("dumpsys", "gfxinfo", PKG, "reset")
    time.sleep(0.2)
    run()
    time.sleep(1.4)
    lines = frames_since(mark)
    print("----", label)
    for line in lines:
        print("   ", line.split("FieldKitFrames: ", 1)[-1].strip())
    print("    gfxinfo:", gfx())


def tap(text, prefix=False):
    def run():
        x, y = center(text, prefix)
        shell("input", "tap", str(x), str(y))
    return run


def press(text, prefix=False):
    def run():
        x, y = center(text, prefix)
        shell("input", "motionevent", "DOWN", str(x), str(y))
        time.sleep(0.25)
        shell("input", "motionevent", "UP", str(x), str(y))
    return run


for _ in range(3):
    shell("input", "keyevent", "4")
    time.sleep(0.3)
shell("am", "force-stop", PKG)
shell("logcat", "-c")
shell("am", "start", "-n", PKG + "/" + PKG + ".V2Activity")
time.sleep(2.5)

step("home -> Settings (tile)", tap("Settings"))
step("Settings -> home (back pill)", tap("\u2039", prefix=True))
step("home -> Link (tile)", tap("Link"))
step("Link -> USB (INSPECT THE CABLE pill)", tap("INSPECT THE CABLE"))
step("USB -> USB (SCAN pill rebuild)", tap("SCAN THE PORT AGAIN"))
step("USB -> home (back pill)", tap("\u2039", prefix=True))
step("home -> Journal (tile)", tap("Journal"))
step("Journal -> home (back pill)", tap("\u2039", prefix=True))
step("home -> Settings (tile), warm", tap("Settings"))
step("Settings OWNER header press", press("OWNER"))
step("Settings BATTERY header press", press("BATTERY"))
step("Settings -> home for the pill", tap("\u2039", prefix=True))
step("home -> Link for the pill", tap("Link"))
step("Link pill INSPECT THE CABLE press", press("INSPECT THE CABLE"))
step("Link -> home after the pill", tap("\u2039", prefix=True))

# The survey screen and its save/list rebuilds go through the same async attach,
# so they are measured with the same bar: 17 frames, 16 ms, nothing late.
def bring(text, prefix=False):
    """The screen is taller than the phone: scroll until the target is there."""
    for _ in range(7):
        try:
            return center(text, prefix)
        except SystemExit:
            shell("input", "swipe", "540", "1700", "540", "900", "300")
            time.sleep(0.5)
    raise SystemExit("no %r on screen" % text)


def tap_at(text, prefix=False):
    def run():
        x, y = bring(text, prefix)
        shell("input", "tap", str(x), str(y))
    return run


def field(index):
    """The nth text field on screen, top to bottom - the site is sticky, so its
    hint is gone and only its position identifies it."""
    boxes = sorted((y1, (x1 + x2) // 2, (y1 + y2) // 2)
                   for value, cls, x1, y1, x2, y2 in emu.dump()
                   if cls == "EditText" and x2 - x1 > 0 and y2 - y1 > 0)
    return boxes[index][1], boxes[index][2]


def key(index, text_value):
    x, y = field(index)
    shell("input", "tap", str(x), str(y))
    time.sleep(0.5)
    shell("input", "text", text_value)
    time.sleep(0.4)
    # ESC closes the keyboard; back (4) would leave the survey screen.
    shell("input", "keyevent", "111")
    time.sleep(0.6)


step("home -> Link for the survey", tap("Link"))
step("Link -> SURVEY (OPEN THE SURVEY)", tap_at("OPEN THE SURVEY"))
key(0, "Ridge%s3")
key(1, "4")
step("SURVEY category chip press", press("water"))
step("SURVEY -> SURVEY (SAVE THE SURVEY)", tap_at("SAVE THE SURVEY"))
step("SURVEY -> SURVEY (OPEN a saved survey)", tap_at("OPEN"))
step("SURVEY -> SURVEY (DELETE asks first)", tap_at("DELETE"))
step("SURVEY -> SURVEY (KEEP IT)", tap_at("KEEP IT"))
step("SURVEY -> Link (back pill)", tap_at("\u2039", prefix=True))

print("focus:", emu.focus())
print("crash hits:", shell("logcat", "-b", "crash", "-d").count("fieldkit.v2"))
