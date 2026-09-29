#!/usr/bin/env python3
"""Static checks for UI code imported from LibrePods.

Fails when a banned asset reference or an SF Symbols glyph (Unicode private use areas, raw or
as a \\u escape) remains in android/app/src, or when an imported file lacks the modification notice.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "android" / "app" / "src"
BANNED = [r"R\.font\.sf_pro", r"sf_pro", r"R\.raw\.connected", r"R\.raw\.island", r"R\.drawable\.airpods",
          r"me\.kavishdevar", r"AACPManager", r"AirPodsService", r"ServiceManager", r"XposedState", r"BillingManager"]
# Literal backslash-u escapes of surrogates (U+D800-U+DBFF high halves) and private-use code points (U+E000-U+FFFF).
ESCAPED_GLYPH = [re.compile(r"\\u[dD][89aAbB][0-9a-fA-F]{2}"), re.compile(r"\\u[eEfF][0-9a-fA-F]{3}")]
PRIVATE_USE = re.compile("[-\U000f0000-\U0010ffff]")
NOTICE = "Modified for LibreBuds (2026)"


def main() -> int:
    problems = []
    for path in sorted(ROOT.rglob("*")):
        if path.suffix not in {".kt", ".xml"} or not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        for pattern in BANNED:
            for match in re.finditer(pattern, text):
                line = text.count("\n", 0, match.start()) + 1
                problems.append(f"{path}:{line}: banned reference {match.group(0)!r}")
        for match in PRIVATE_USE.finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            problems.append(f"{path}:{line}: private-use glyph U+{ord(match.group(0)):04X}")
        for pattern in ESCAPED_GLYPH:
            for match in pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                problems.append(f"{path}:{line}: escaped surrogate/private-use sequence {match.group(0)!r}")
        if path.suffix == ".kt" and "LibrePods contributors" in text and NOTICE not in text:
            problems.append(f"{path}: imported file without modification notice")
    for problem in problems:
        print(problem)
    print(f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
