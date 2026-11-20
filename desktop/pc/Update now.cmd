@echo off
set "D=%LOCALAPPDATA%\ShiftCalendarWallpaper"
if not exist "%D%\scripts\Update-Wallpaper.ps1" set "D=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%D%\scripts\Update-Wallpaper.ps1" -Show
pause
