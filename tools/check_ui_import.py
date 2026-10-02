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
          r"me\.kavishdevar", r"AACPManager", r"AirPodsService", r"ServiceManager", r"XposedState", r"BillingManager",
          r"R\.drawable\.adaptive\b", r"R\.drawable\.noise_cancellation\b", r"R\.drawable\.transparency\b"]
# Literal backslash-u escapes of surrogates (U+D800-U+DBFF high halves) and private-use code points (U+E000-U+FFFF).
ESCAPED_GLYPH = [re.compile(r"\\u[dD][89aAbB][0-9a-fA-F]{2}"), re.compile(r"\\u[eEfF][0-9a-fA-F]{3}")]
PRIVATE_USE = re.compile("[-\U000f0000-\U0010ffff]")
NOTICE = "Modified for LibreBuds (2026)"
# Files that must not be reintroduced anywhere under ROOT (LibrePods media/icons/fonts).
BANNED_FILE_GLOBS = [
    "res/raw*/*.mp4",
    "res/font/*.ttf",
    "res/font/*.otf",
    "drawable*/airpods*",
    "drawable*/adaptive.png",
    "drawable*/noise_cancellation.png",
    "drawable*/transparency.png",
]
# Our own case-opening clips for the card popup (LibreBuds original artwork, see NOTICE) are the only
# videos allowed. Exact paths, relative to android/app/src.
ALLOWED_FILES = [
    "main/res/raw/popup_freebuds_4_light.mp4",
    "main/res/raw/popup_freebuds_4_dark.mp4",
    "main/res/raw/popup_freebuds_5_light.mp4",
    "main/res/raw/popup_freebuds_5_dark.mp4",
    "main/res/raw/popup_freebuds_6_light.mp4",
    "main/res/raw/popup_freebuds_6_dark.mp4",
    "main/res/raw/popup_freebuds_pro_2_light.mp4",
    "main/res/raw/popup_freebuds_pro_2_dark.mp4",
    "main/res/raw/popup_freebuds_pro_3_light.mp4",
    "main/res/raw/popup_freebuds_pro_3_dark.mp4",
    "main/res/raw/popup_freebuds_pro_4_light.mp4",
    "main/res/raw/popup_freebuds_pro_4_dark.mp4",
    "main/res/raw/popup_freebuds_pro_5_light.mp4",
    "main/res/raw/popup_freebuds_pro_5_dark.mp4",
]
# XML numeric character references pointing into the SF Symbols private-use ranges (hex or decimal).
NUMERIC_CHAR_REF = re.compile(r"&#(x[0-9a-fA-F]+|[0-9]+);")
HEADER_MARKERS = ("LibreBuds contributors", "LibrePods contributors")


def is_private_use_codepoint(value: int) -> bool:
    return 0xE000 <= value <= 0xF8FF or value >= 0xF0000


def main() -> int:
    problems = []
    for pattern in BANNED_FILE_GLOBS:
        for path in sorted(ROOT.rglob(pattern)):
            relative = path.relative_to(ROOT).as_posix()
            if path.is_file() and relative not in ALLOWED_FILES:
                problems.append(f"{path}: banned file matches {pattern!r}")
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
        if path.suffix == ".xml":
            for match in NUMERIC_CHAR_REF.finditer(text):
                ref = match.group(1)
                value = int(ref[1:], 16) if ref[0] in "xX" else int(ref)
                if is_private_use_codepoint(value):
                    line = text.count("\n", 0, match.start()) + 1
                    problems.append(f"{path}:{line}: private-use numeric character reference {match.group(0)!r}")
        if path.suffix == ".kt" and "LibrePods contributors" in text and NOTICE not in text:
            problems.append(f"{path}: imported file without modification notice")
        if path.suffix == ".kt" and "package io.github.librebuds" in text:
            head = "\n".join(text.splitlines()[:20])
            if not any(marker in head for marker in HEADER_MARKERS):
                problems.append(f"{path}: missing LibreBuds/LibrePods header in first 20 lines")
    for problem in problems:
        print(problem)
    print(f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
