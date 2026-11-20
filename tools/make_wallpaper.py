#!/usr/bin/env python3
"""Builds desktop/pc/wallpaper.html: the control panel's own calendar drawing, set up as a PC wallpaper.
Run after any change to panel/control-panel.html so the PC wallpaper keeps matching the phone."""
import pathlib
root = pathlib.Path(__file__).resolve().parent.parent
panel = (root / "panel" / "control-panel.html").read_text(encoding="utf-8")
ics = (root / "desktop" / "src" / "ics.js").read_text(encoding="utf-8")
boot = (root / "desktop" / "src" / "wall-boot.js").read_text(encoding="utf-8")
assert panel.count("</body>") == 1
panel = panel.replace("<title>Shift Calendar Control Panel</title>", "<title>Shift Calendar wallpaper</title>")
inject = ('<script src="wallpaper-data.js"></script>\n<script>\n' + ics + '\n</script>\n<script>\n' + boot + '\n</script>\n</body>')
out = root / "desktop" / "pc" / "wallpaper.html"
out.write_text(panel.replace("</body>", inject), encoding="utf-8")
print("wrote", out, out.stat().st_size, "bytes")
