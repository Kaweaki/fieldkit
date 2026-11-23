"""Open every tile and the SOS panel on one device, and prove nothing crashed.

    ADB=... FK_SERIAL=emulator-5556 py tools/v2/tour.py --apk app/build/FieldKit-V2.apk
    ADB=... FK_SERIAL=R58M52YG20N  py tools/v2/tour.py --apk app/build/FieldKit-V2.apk

Prints one line per tile (opened, live pid, first text on the screen), then the
SOS state and the number of torch events the camera service logged for this
app's pid, then the crash buffer. A tile that opens onto a blank screen shows up
as an empty first line.
"""

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emu import PKG_V2, TILES, dump, home, open_tile, pid, sh, tapif, texts  # noqa: E402


def main(apk):
    if apk:
        print("install:", sh("install", "-r", apk).decode("utf-8", "replace").strip().splitlines()[-1])
    sh("logcat", "-c")
    for name in TILES:
        home()
        opened = open_tile(name)
        time.sleep(1.8)
        alive = pid()
        lines = [t for t in texts() if t.strip()]
        print("%-11s opened=%-5s pid=%-6s %s" % (name, opened, alive, lines[0][:60] if lines else "(blank)"))
        sh("shell", "input", "keyevent", "4")
        time.sleep(0.8)

    home()
    tapif(lambda t: "DISTRESS" in t)
    time.sleep(2)
    tapif(lambda t: t.startswith("START"))
    time.sleep(2.5)
    state = [t for t in texts() if t.strip() in ("SIGNALLING", "IDLE")]
    running = pid()
    camera = sh("shell", "dumpsys", "media.camera").decode("utf-8", "replace")
    torch = len([line for line in camera.splitlines() if "client PID " + running in line])
    print("SOS state:", state[:1], "torch events for pid", running, ":", torch)
    tapif(lambda t: t == "STOP")
    time.sleep(1)
    crash = sh("logcat", "-d", "-b", "crash").decode("utf-8", "replace")
    print("crash lines:", len([line for line in crash.splitlines() if line.strip()]))
    print(crash[-800:] if crash.strip() else "(crash buffer empty)")
    print("pid at end:", pid())


if __name__ == "__main__":
    apk = None
    if "--apk" in sys.argv:
        apk = sys.argv[sys.argv.index("--apk") + 1]
    main(apk)
