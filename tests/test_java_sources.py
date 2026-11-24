"""Structural checks on the Java sources, for machines without a JDK.

This is not a compiler. It catches the failures that a compiler would report
first and that are invisible in review: unbalanced braces, a class named
differently from its file, a package that does not match the directory, and a
"pure" class that quietly started importing Android (which would break the
plain-JVM self test). CI runs the real `javac`.

    py -m unittest discover -s tests -t .
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app" / "src"
TEST = ROOT / "app" / "test"

# These run on a plain JVM in SelfTest, so they must not touch the platform.
PURE = {"Sun", "Water", "Power", "Ice", "Backup", "TextLink", "Jpeg", "Geo",
        "UsbReport", "Survey", "SelfTest"}


def java_files():
    files = sorted(SRC.rglob("*.java")) + sorted(TEST.rglob("*.java"))
    return files


def strip_literals(text):
    """Remove comments, string and char literals so brace counting is honest."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
        elif c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
        elif c == '"':
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                i += 1
        elif c == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def expected_package(path):
    base = SRC if SRC in path.parents else TEST
    return ".".join(path.relative_to(base).parent.parts)


class JavaSources(unittest.TestCase):
    def setUp(self):
        self.files = java_files()

    def test_sources_exist(self):
        self.assertGreaterEqual(len(self.files), 12, "the 2.7 classes should all be present")

    def test_balanced_delimiters(self):
        for path in self.files:
            code = strip_literals(path.read_text(encoding="utf-8"))
            for opener, closer in (("{", "}"), ("(", ")"), ("[", "]")):
                depth = 0
                for index, char in enumerate(code):
                    if char == opener:
                        depth += 1
                    elif char == closer:
                        depth -= 1
                        if depth < 0:
                            self.fail(f"{path.name}: '{closer}' closes nothing at {index}")
                self.assertEqual(depth, 0, f"{path.name}: {opener}{closer} are unbalanced")

    def test_package_matches_directory(self):
        for path in self.files:
            code = strip_literals(path.read_text(encoding="utf-8"))
            match = re.search(r"^\s*package\s+([\w.]+)\s*;", code, re.MULTILINE)
            self.assertIsNotNone(match, f"{path.name}: no package declaration")
            self.assertEqual(match.group(1), expected_package(path), f"{path.name}: wrong package")

    def test_file_declares_its_own_type(self):
        for path in self.files:
            code = strip_literals(path.read_text(encoding="utf-8"))
            match = re.search(
                r"(?:public\s+)?(?:final\s+)?(?:class|interface)\s+(\w+)", code)
            self.assertIsNotNone(match, f"{path.name}: no type declaration")
            self.assertEqual(match.group(1), path.stem, f"{path.name}: declares {match.group(1)}")

    def test_pure_classes_do_not_import_android(self):
        for path in self.files:
            if path.stem not in PURE:
                continue
            code = path.read_text(encoding="utf-8")
            self.assertNotIn("import android.", code, f"{path.stem} must run on a plain JVM")

    def test_platform_classes_do_import_android(self):
        for path in self.files:
            if path.stem in PURE:
                continue
            code = path.read_text(encoding="utf-8")
            self.assertIn("import android.", code, f"{path.stem} claims to be platform code")

    def test_no_stray_control_characters(self):
        for path in self.files:
            text = path.read_text(encoding="utf-8")
            for char in text:
                if ord(char) < 32 and char not in "\n\t":
                    self.fail(f"{path.name}: raw control character U+{ord(char):04X}")

    def test_files_end_with_a_newline(self):
        for path in self.files:
            self.assertTrue(path.read_text(encoding="utf-8").endswith("\n"), f"{path.name}")


if __name__ == "__main__":
    unittest.main()
