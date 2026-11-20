SHIFT CALENDAR - PC WALLPAPER (Windows 10 / 11)
================================================

Your Google Calendar as the desktop picture, drawn in the same look as the phone widget.
It redraws itself every 15 minutes and when you sign in. Nothing to install: it uses
Microsoft Edge (already on Windows) to draw the picture, hidden in the background.

SET IT UP (about 3 minutes)
1. Unzip this folder anywhere (Downloads is fine).
2. Double-click Setup.cmd. If Windows says "Windows protected your PC", click
   "More info" then "Run anyway".
3. It asks for:
   - Your calendars: on this PC open calendar.google.com > Settings (gear, top right) >
     click your calendar under "Settings for my calendars" > "Integrate calendar" >
     copy "Secret address in iCal format" and paste it. Repeat for any other calendar
     you want on there. Keep these addresses private.
   - Your look (optional): on the phone open Shift Calendar > "Receive settings from computer"
     > "Copy this phone's settings as a code", send it to yourself, paste it here.
     Or press "Copy settings code" in the control panel.
   - Where on the screen: right side (default), left, centre or full screen; width; text size.
4. The wallpaper appears. The files live in %LOCALAPPDATA%\ShiftCalendarWallpaper.

CHANGE THINGS LATER
- Run Setup.cmd again (press Enter to keep any answer).
- New look on the phone? Copy a fresh settings code and paste it in Setup.cmd (step 2).
- "Update now.cmd" redraws straight away. last-update.log says what happened last time.

GOOD TO KNOW
- Google's iCal feed can take a few minutes (sometimes longer) to show a change you just made.
- The feed doesn't include per-event colours set in Google, so the wallpaper uses your
  widget's colour codes (ACT / EPA / VW / GPH ...) and one colour per calendar.
- Remove.cmd stops the refreshing (and can delete the files).
- Live option: the free Lively Wallpaper app (Microsoft Store) can show wallpaper.html from
  the folder above directly. Add ?live=1 to the address so it reloads itself every 10 minutes.
