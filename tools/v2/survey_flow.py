"""The survey, end to end, on the phone.

Drives the shipped APK over adb: opens the survey from Link, proves that the
form refuses a bad name and a bad count, saves one, kills the process, starts it
again and reads the saved survey back, edits it, leaves a half-typed draft in the
form across another kill, then deletes it after the confirmation step. Every
claim is a line of text read off the accessibility tree, not an assumption.

Three things the harness has to do for this screen, and does:
  * scroll - the survey is taller than the phone, so a target is brought into
    view before it is tapped;
  * ignore zero-area nodes - a ScrollView reports its off-screen children with
    bounds [0,0][0,0], and tapping one lands in the corner doing nothing;
  * close the keyboard with ESC (111) - back (4) leaves the screen.

    FK_SERIAL=R58M52YG20N py -u tools/v2/survey_flow.py
"""
import os
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
    """The laid-out nodes only: a 0x0 node cannot be tapped or read."""
    return [(value, cls, x1, y1, x2, y2) for value, cls, x1, y1, x2, y2 in emu.dump()
            if x2 - x1 > 0 and y2 - y1 > 0]


def texts():
    return [value for value, _, _, _, _, _ in nodes() if value]


def visible(part):
    return any(part in value for value in texts())


def wait_for(part, seconds=5.0):
    """A rebuild is a worker-thread build plus a fade; give it a moment."""
    deadline = time.time() + seconds
    while time.time() < deadline:
        if visible(part):
            return True
        time.sleep(0.3)
    return visible(part)


def find(text=None, prefix=None):
    for value, _, x1, y1, x2, y2 in nodes():
        if (text is not None and value == text) or (prefix and value.startswith(prefix)):
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def scroll_down():
    sh("input", "swipe", "540", "1700", "540", "900", "300")
    time.sleep(0.5)


def scroll_top():
    for _ in range(6):
        sh("input", "swipe", "540", "900", "540", "1900", "180")
        time.sleep(0.25)


def bring(text=None, prefix=None):
    for _ in range(7):
        point = find(text, prefix)
        if point:
            return point
        scroll_down()
    raise SystemExit("no %r on screen, have %r" % (text or prefix, texts()))


def tap(text):
    point = bring(text)
    sh("input", "tap", str(point[0]), str(point[1]))
    time.sleep(1.0)


def tap_back():
    scroll_top()
    point = bring(prefix="\u2039")
    sh("input", "tap", str(point[0]), str(point[1]))
    time.sleep(1.2)


def wait_scroll(part, seconds=8.0):
    """Waits for a rebuild, scrolling as it goes: the card may land below the fold."""
    deadline = time.time() + seconds
    while time.time() < deadline:
        if visible(part):
            return True
        scroll_down()
        time.sleep(0.3)
    return visible(part)


def scroll_to(part):
    """Scrolls until a piece of text is on screen; False when it never is."""
    for _ in range(7):
        if visible(part):
            return True
        scroll_down()
    return False


def fields():
    """The three form fields, top to bottom, while the form is at the top."""
    boxes = []
    for _ in range(4):
        boxes = [(x1, y1, x2, y2) for value, cls, x1, y1, x2, y2 in nodes()
                 if cls == "EditText"]
        boxes.sort(key=lambda box: box[1])
        if len(boxes) >= 3:
            return boxes
        scroll_top()
        time.sleep(0.4)
    return boxes


def field_values():
    boxes = [(y1, value) for value, cls, _, y1, _, _ in nodes() if cls == "EditText"]
    boxes.sort()
    return [value for _, value in boxes]


def type_in(index, value, clear=True):
    scroll_top()
    boxes = fields()
    if index >= len(boxes):
        raise SystemExit("only %d fields on screen, wanted #%d: %r"
                         % (len(boxes), index, field_values()))
    box = boxes[index]
    sh("input", "tap", str((box[0] + box[2]) // 2), str((box[1] + box[3]) // 2))
    time.sleep(0.6)
    if clear:
        sh("input", "keyevent", "123")
        sh("input", "keyevent", "67 67 67 67 67 67 67 67 67 67 67 67 67 67")
        time.sleep(0.2)
    if value:
        sh("input", "text", value.replace(" ", "%s"))
    time.sleep(0.5)
    sh("input", "keyevent", "111")
    time.sleep(0.6)


def cards():
    """How many saved surveys are on screen: one 'counted' line per card."""
    return sum(1 for value in texts() if " counted" in value)


def all_cards():
    scroll_top()
    for _ in range(4):
        scroll_down()
    return cards()


def start():
    sh("am", "force-stop", PKG)
    time.sleep(0.5)
    sh("am", "start", "-n", PKG + "/" + PKG + ".V2Activity")
    time.sleep(2.4)


def open_survey():
    scroll_top()
    if not visible("OPEN THE SURVEY"):
        tap("Link")
    tap("OPEN THE SURVEY")
    if not scroll_to("SAVE THE SURVEY"):
        raise SystemExit("the survey did not open; have %r" % texts())
    scroll_top()


sh("logcat", "-c")
# A known starting point: the empty-state check is only honest on a phone with
# nothing saved. This wipes the app's own preferences, nothing else.
sh("pm", "clear", PKG)
time.sleep(1.0)
start()

print("== open the survey", flush=True)
scroll_top()
tap("Link")
check(scroll_to("OPEN THE SURVEY"), "the Link card carries the survey entry")
tap("OPEN THE SURVEY")
check(scroll_to("SAVE THE SURVEY"), "the survey screen opens")
check(visible("Nothing saved yet"), "on an empty list")

print("== validation blocks bad input", flush=True)
scroll_top()
tap("SAVE THE SURVEY")
check(visible("Name the site - at least two characters"), "an empty name is refused")
check(all_cards() == 0, "nothing was written")
type_in(0, "Ridge 3")
tap("SAVE THE SURVEY")
check(visible("Enter a whole number, zero or more"), "an empty count is refused")
type_in(1, "abc")
check(field_values()[1] == "count", "the number field does not take letters: %r"
      % (field_values()[1],))
tap("SAVE THE SURVEY")
check(visible("Enter a whole number, zero or more"), "and the save is still refused")
type_in(1, "999999")
tap("SAVE THE SURVEY")
check(visible("between 0 and 100000"), "a count past the limit is refused")
check(all_cards() == 0, "still nothing written")

print("== save", flush=True)
type_in(1, "12")
scroll_top()
tap("water")
type_in(2, "Clear water near the ridge")
scroll_top()
tap("SAVE THE SURVEY")
check(visible("Saved Ridge 3"), "the save reports itself")
check(all_cards() == 1, "one survey is listed")
check(scroll_to("12 counted"), "the list shows the count")
check(scroll_to("water"), "the list shows the category the chip chose")
scroll_top()
values = field_values()
check(values[0] == "Ridge 3", "the site stays in the form for the next entry: %r"
      % (values[0],))
check(values[1] == "count" and values[2] == "what you saw",
      "count and note start empty again: %r" % (values,))

print("== kill the process, start it cold", flush=True)
start()
open_survey()
check(all_cards() == 1, "the saved survey is still there after a force-stop")
check(scroll_to("Ridge 3"), "with its name")
check(scroll_to("12 counted"), "and its count")

print("== reopen and edit", flush=True)
scroll_top()
scroll_to("Ridge 3")
tap("OPEN")
scroll_top()
values = field_values()
check(values[0] == "Ridge 3", "the name is restored: %r" % (values[0],))
check(values[1] == "12", "the count is restored: %r" % (values[1],))
check(values[2] == "Clear water near the ridge", "the note is restored: %r" % (values[2],))
type_in(1, "13")
scroll_top()
tap("SAVE THE SURVEY")
check(all_cards() == 1, "editing replaced the survey instead of adding one")
check(scroll_to("13 counted"), "the edit replaced the count")
check(not scroll_to("12 counted"), "the old count is gone")

print("== a draft survives a force-stop", flush=True)
# Saving ended the edit, so this is a new form: three fields typed, none saved.
type_in(0, "Half typ")
type_in(1, "7")
type_in(2, "two of")
start()
open_survey()
values = field_values()
check(values[0] == "Half typ", "the half-typed name came back exactly: %r" % (values[0],))
check(values[1] == "7", "and the half-typed count: %r" % (values[1],))
check(values[2] == "two of", "and the half-typed note: %r" % (values[2],))
check(visible("Fill once, save once"), "the form is a new survey, not an edit")

print("== delete asks first", flush=True)
scroll_top()
scroll_to("Ridge 3")
tap("DELETE")
check(wait_scroll("cannot be undone"), "delete shows the confirmation")
check(visible("CONFIRM DELETE"), "with a confirm button")
check(wait_for("Tap CONFIRM DELETE below"), "and says so at the top of the screen")
tap("KEEP IT")
check(all_cards() == 1, "KEEP IT leaves the survey alone")
tap("DELETE")
wait_scroll("CONFIRM DELETE")
tap("CONFIRM DELETE")
check(wait_for("Deleted Ridge 3"), "the delete reports itself")
check(all_cards() == 0, "the list is empty")
check(scroll_to("Nothing saved yet"), "the empty state is back")

print("== back out", flush=True)
tap_back()
check(visible("Calculator") and visible("Medical"),
      "the back pill returns home, the way every tile does")

start()
open_survey()
check(all_cards() == 0, "the delete survived the restart too")
check(scroll_to("Nothing saved yet"), "with the empty state")
tap_back()

crashes = sh("logcat", "-b", "crash", "-d").count("fieldkit.v2")
check(crashes == 0, "zero crash lines in logcat (%d)" % crashes)
print("focus:", emu.focus())
print()
print("%d passed, %d failed" % (PASSED, FAILED))
sys.exit(1 if FAILED else 0)
