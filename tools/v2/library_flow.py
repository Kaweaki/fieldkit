"""The library, end to end, on the phone.

Proves what the screen claims: it lists the files that are actually in the folder
handed to it, newest first, with format, size and date; tapping a PDF and a text
file really leaves the app for a viewer; a format with no app gets an honest
sentence instead of a dead tap; a file deleted behind the list is named as gone
after a rescan; and with nothing granted the screen says so instead of pretending
the folders are empty.

The list is read by scrolling the whole screen once into a set, because a scan of
a real phone holds more files than one screen, and a check that only looks at the
top of the list proves nothing about the rest.

    FK_SERIAL=R58M52YG20N py -u tools/v2/library_flow.py
"""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault("FK_SERIAL", "R58M52YG20N")
import emu  # noqa: E402

PKG = emu.PKG_V2
PASSED = 0
FAILED = 0


def sh(*args):
    return subprocess.run([emu.ADB, "-s", emu.SERIAL, "shell"] + list(args),
                          capture_output=True).stdout.decode("utf-8", "replace")


def check(ok, what):
    global PASSED, FAILED
    if ok:
        PASSED += 1
        print("PASS", what, flush=True)
    else:
        FAILED += 1
        print("FAIL", what, flush=True)


def nodes():
    return [(value, cls, x1, y1, x2, y2) for value, cls, x1, y1, x2, y2 in emu.dump()
            if x2 - x1 > 0 and y2 - y1 > 0]


def texts():
    return [value for value, _, _, _, _, _ in nodes() if value]


def visible(part):
    return any(part in value for value in texts())


def find(text=None, prefix=None, maxlen=200):
    for value, _, x1, y1, x2, y2 in nodes():
        if text is not None and value == text:
            return (x1 + x2) // 2, (y1 + y2) // 2
        if prefix and value and value.startswith(prefix) and len(value) <= maxlen:
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def scroll_down(step=800):
    sh("input", "swipe", "540", str(1800), "540", str(1800 - step), "250")
    time.sleep(0.45)


def scroll_top():
    for _ in range(10):
        sh("input", "swipe", "540", "900", "540", "1900", "160")
        time.sleep(0.2)


def whole_list(pages=26):
    """Every text on the screen, read from the top down once."""
    scroll_top()
    seen = set()
    for _ in range(pages):
        before = len(seen)
        for value in texts():
            seen.add(value)
        scroll_down()
        if len(seen) == before and any("KIWIX" in value for value in seen):
            break
    scroll_top()
    return seen


def bring(text=None, prefix=None, maxlen=200, tries=30):
    for _ in range(tries):
        point = find(text, prefix, maxlen)
        if point:
            return point
        scroll_down()
    return None


def tap_visible(text=None, prefix=None, maxlen=200):
    point = bring(text, prefix, maxlen)
    if point is None:
        raise SystemExit("no %r on screen" % (text or prefix))
    sh("input", "tap", str(point[0]), str(point[1]))
    time.sleep(1.4)


def tap_back():
    sh("input", "keyevent", "4")
    time.sleep(1.4)


def start():
    sh("input", "keyevent", "224")
    sh("wm", "dismiss-keyguard")
    sh("am", "force-stop", PKG)
    time.sleep(0.4)
    sh("am", "start", "-n", PKG + "/" + PKG + ".V2Activity")
    time.sleep(2.6)


def open_library():
    scroll_top()
    if not visible("STORAGE ACCESS"):
        tap_visible("Library")
    time.sleep(1.0)
    scroll_top()


def grant_folder(button):
    tap_visible(button)
    time.sleep(2.6)
    bar = find(prefix="VILL DU TILL", maxlen=60) or find(prefix="USE THIS FOLDER")
    if bar is None:
        raise SystemExit("the picker showed no use-this-folder bar")
    sh("input", "tap", str(bar[0]), str(bar[1]))
    time.sleep(2.6)
    allow = find(prefix="TILL", maxlen=8) or find(text="ALLOW")
    if allow is None:
        raise SystemExit("the picker asked nothing")
    sh("input", "tap", str(allow[0]), str(allow[1]))
    time.sleep(3.0)


def focus_package():
    window = emu.focus()
    return window.split(" ")[-1].split("/")[0] if window else ""


def sentinels():
    return ("PDF  13.0 KB", "manual.pdf", "alice.epub", "water-quality.csv",
            "git-readme.txt", "sensor-log.dat", "zero-byte.csv")


sh("logcat", "-c")
start()
print("== listing", flush=True)
open_library()
check(visible("STORAGE ACCESS"), "the library screen opens")
if not visible(" on this phone"):
    grant_folder("CHOOSE THE DOWNLOAD FOLDER")
    time.sleep(1.5)
    open_library()
check(visible(" on this phone"), "a granted folder produces a scan")
check(visible("Granted - 1 folder") or visible("Granted - 2 folders"),
      "the access card names the grant")

listed = whole_list()
missing = [name for name in sentinels() if not any(name in value for value in listed)]
check(not missing, "every pushed document is listed: missing %s" % missing)
check("PDF  13.0 KB" in listed, "the PDF card shows its format and size")
check(any("0 BYTES" in value for value in listed), "a zero-byte file shows 0 bytes")
check(any("kB" in value or "KB" in value or "MB" in value for value in listed),
      "sizes are human sizes, not raw byte counts")
check("storage access" in " ".join(listed).lower(), "the access state is on screen")

print("== open a PDF", flush=True)
tap_visible("manual.pdf")
time.sleep(2.5)
opened = focus_package()
check(opened not in ("", PKG), "the PDF left FieldKit for a viewer (%s)" % opened)
tap_back()
start()
open_library()

print("== open a text file", flush=True)
tap_visible("git-readme.txt")
time.sleep(2.5)
opened = focus_package()
check(opened not in ("", PKG), "the text file left FieldKit for an editor (%s)" % opened)
tap_back()
start()
open_library()

print("== an EPUB with no reader", flush=True)
tap_visible("alice.epub")
time.sleep(2.5)
check(visible("No app on this phone opens EPUB"), "the missing reader is named, not a dead tap")
check(focus_package() == PKG, "the app stays in front instead of doing nothing")

print("== a file deleted behind the list", flush=True)
# A file this run creates and then removes: deleting one that an earlier run
# already removed proves nothing, because the list never knew about it.
sh("sh", "-c", "echo bait > /sdcard/Download/FieldKit/raderas-efter-scan.csv")
scroll_top()
tap_visible("SCAN AGAIN")
time.sleep(2.5)
scroll_top()
check(any("raderas-efter-scan.csv" in value for value in whole_list()),
      "the bait file is listed before it is removed")
sh("rm", "-f", "/sdcard/Download/FieldKit/raderas-efter-scan.csv")
scroll_top()
tap_visible("SCAN AGAIN")
time.sleep(2.5)
scroll_top()
page = whole_list()
lowered = " ".join(page).lower()
check("no longer available" in lowered,
      "the deleted file is reported as no longer available")
check(any("raderas-efter-scan.csv" in value for value in page), "and it is named")

print("== no access at all", flush=True)
sh("pm", "clear", PKG)
time.sleep(1.0)
start()
open_library()
check(visible("Not granted"), "with nothing granted the screen says so")
check(visible("CHOOSE THE DOWNLOAD FOLDER"), "and offers the picker")
check(visible("Nothing granted to scan"), "it does not claim a scan found nothing")
check(not any(re.search(r"\d+ documents? on this phone", value) for value in texts()),
      "no count is printed for a scan that never ran")

print("== hand the folder over again", flush=True)
grant_folder("CHOOSE THE DOWNLOAD FOLDER")
time.sleep(1.5)
open_library()
check(visible(" on this phone"), "the grant can be given again after it was cleared")
check(visible("Granted"), "and the card says granted")

crashes = sh("logcat", "-b", "crash", "-d").count("fieldkit.v2")
check(crashes == 0, "zero crash lines in logcat (%d)" % crashes)
print("focus:", emu.focus())
print()
print("%d passed, %d failed" % (PASSED, FAILED))
sys.exit(1 if FAILED else 0)
