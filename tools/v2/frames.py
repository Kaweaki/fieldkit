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

print("focus:", emu.focus())
print("crash hits:", shell("logcat", "-b", "crash", "-d").count("fieldkit.v2"))
