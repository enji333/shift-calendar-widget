# Shift Calendar PC wallpaper: stops the automatic refresh. Your current desktop picture stays until you change it.
$dir = Join-Path $env:LOCALAPPDATA 'ShiftCalendarWallpaper'
Unregister-ScheduledTask -TaskName 'Shift Calendar wallpaper' -Confirm:$false -ErrorAction SilentlyContinue
Write-Host 'The calendar wallpaper will no longer refresh. Pick another picture in Settings > Personalisation > Background.'
$ans = Read-Host "Also delete its files (your saved calendar addresses and look) in $dir ? Y/N"
if ($ans -match '^[Yy]') { Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue; Write-Host 'Deleted.' }
