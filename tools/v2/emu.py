"""Device helpers for the FieldKit V2 checks.

    ADB=/path/to/adb FK_SERIAL=emulator-5554 py tools/v2/tour.py

ADB is taken from the environment, then PATH, then the usual SDK layout, so the
same script works on another machine, on an emulator, or on a phone over USB.
Everything speaks through the accessibility tree, which is why it works on a
headless image with no framebuffer.
"""

import os
import re
import shutil
import subprocess

PKG_V1 = "se.blimark.fieldkit"
PKG_V2 = "se.blimark.fieldkit.v2"
TILES = ["Profile", "Settings", "Medical", "Calculator", "Link", "Share",
         "Screens", "Library", "Journal", "Maps", "Comms", "Help"]


def _find_adb():
    for candidate in (os.environ.get("ADB"), shutil.which("adb")):
        if candidate and os.path.exists(candidate):
            return candidate
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if root:
            name = "adb.exe" if os.name == "nt" else "adb"
            path = os.path.join(root, "platform-tools", name)
            if os.path.exists(path):
                return path
    return "adb"


ADB = _find_adb()
SERIAL = os.environ.get("FK_SERIAL", "emulator-5554")


def sh(*args):
    """Run adb and return stdout bytes."""
    return subprocess.run([ADB, "-s", SERIAL] + list(args), capture_output=True).stdout


def dump():
    """The current screen as [(text, class, x1, y1, x2, y2)], from uiautomator."""
    sh("shell", "uiautomator", "dump", "/data/local/tmp/t.xml")
    xml = sh("exec-out", "cat", "/data/local/tmp/t.xml").decode("utf-8", "replace")
    out = []
    for m in re.finditer(r'text="([^"]*)"[^>]*?class="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        out.append((m.group(1).strip(), m.group(2).split(".")[-1],
                    int(m.group(3)), int(m.group(4)), int(m.group(5)), int(m.group(6))))
    return out


def texts():
    return [t for t, _, _, _, _, _ in dump() if t]


def tapif(predicate):
    """Tap the first node whose text satisfies predicate(text)."""
    for text, _, x1, y1, x2, y2 in dump():
        if predicate(text) and 0 < (y1 + y2) // 2 < 2300:
            sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return True
    return False


def pid(package=PKG_V2):
    return sh("shell", "pidof", package).decode("utf-8", "replace").strip()


def home(package=PKG_V2, activity=".V2Activity"):
    """Restart the app so every capture starts from the same screen."""
    import time
    sh("shell", "am", "start", "-S", "-n", package + "/" + activity)
    time.sleep(3)


def shot(path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(sh("exec-out", "screencap", "-p"))


def tree(path):
    """Write the same screen's geometry next to its screenshot."""
    with open(path.replace(".png", ".txt"), "w", encoding="utf-8", newline="\n") as handle:
        for text, kind, x1, y1, x2, y2 in dump():
            if text:
                handle.write("%-40s %-10s x=%4d-%4d y=%5d-%5d\n"
                             % (text.replace("\n", " / ")[:40], kind, x1, x2, y1, y2))


def open_tile(label):
    """Scroll the home grid until the named tile is on screen, then tap it."""
    sh("shell", "input", "swipe", "540", "700", "540", "1900", "120")
    import time
    time.sleep(0.6)
    for _ in range(4):
        if tapif(lambda t: t == label):
            return True
        sh("shell", "input", "swipe", "540", "1700", "540", "800", "200")
        time.sleep(0.8)
    return False
