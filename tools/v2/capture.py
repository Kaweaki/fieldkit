"""Capture every user-visible screen of V1 and V2 on one device, for pairing.

    ADB=... FK_SERIAL=emulator-5556 py tools/v2/capture.py v1
    ADB=... FK_SERIAL=emulator-5556 py tools/v2/capture.py v2

Both apps are installed on the same API 34 AVD, so the screenshots share a screen
size, density and system font. V1 is the shipped 2.8 APK built from the repo's
own `dist/`/pulled copy, never a reconstruction: see docs/V2-HANDOFF.md.

Writes <outdir>/NN_name.png plus a .txt of the same screen's geometry, so a
deviation can be proved with numbers as well as pixels.
"""

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emu import PKG_V1, PKG_V2, TILES, home, open_tile, pid, shot, tree  # noqa: E402

OUT = os.environ.get("OUT", "docs/screens")


def capture(package, activity, outdir):
    home(package, activity)
    shot(outdir + "/00_home.png")
    tree(outdir + "/00_home.png")
    print("home captured, pid", pid(package))
    for index, name in enumerate(TILES):
        home(package, activity)
        opened = open_tile(name)
        time.sleep(2.0)
        slug = "%02d_%s" % (index + 1, name.lower())
        shot(outdir + "/" + slug + ".png")
        tree(outdir + "/" + slug + ".png")
        print("%-11s opened=%s" % (name, opened))


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "v2"
    if which == "v1":
        capture(PKG_V1, ".MainActivity", OUT + "/v1")
    else:
        capture(PKG_V2, ".V2Activity", OUT + "/v2")
