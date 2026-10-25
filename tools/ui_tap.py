#!/usr/bin/env python3
"""Taps an element of the app on a connected device by its text or content description (uiautomator).
Usage: ui_tap.py SERIAL TEXT [--desc] [--wait SECONDS] [--exists]   (case-insensitive, substring)"""
import re, subprocess, sys, time, xml.etree.ElementTree as ET

def dump(serial):
    subprocess.run(["adb", "-s", serial, "shell", "uiautomator", "dump", "--compressed", "/sdcard/mb_ui.xml"],
                   capture_output=True, text=True)
    out = subprocess.run(["adb", "-s", serial, "exec-out", "cat", "/sdcard/mb_ui.xml"], capture_output=True, text=True).stdout
    return ET.fromstring(out[out.index("<"):]) if "<" in out else None

def find(root, needle, by_desc):
    needle = needle.lower()
    best = None
    for node in root.iter("node"):
        if node.get("package") != "com.mouthband.app":
            continue
        value = (node.get("content-desc") if by_desc else node.get("text")) or ""
        if needle in value.lower():
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                if best is None or node.get("clickable") == "true":
                    best = ((x1 + x2) // 2, (y1 + y2) // 2)
    return best

def main():
    serial, needle = sys.argv[1], sys.argv[2]
    by_desc = "--desc" in sys.argv
    wait = float(sys.argv[sys.argv.index("--wait") + 1]) if "--wait" in sys.argv else 10.0
    deadline = time.time() + wait
    while True:
        root = dump(serial)
        point = find(root, needle, by_desc) if root is not None else None
        if point or time.time() > deadline:
            break
        time.sleep(0.8)
    if not point:
        print(f"not found: {needle}", file=sys.stderr)
        sys.exit(1)
    if "--exists" in sys.argv:
        sys.exit(0)
    subprocess.run(["adb", "-s", serial, "shell", "input", "tap", str(point[0]), str(point[1])])
    print(f"tapped {needle} at {point}")

main()
