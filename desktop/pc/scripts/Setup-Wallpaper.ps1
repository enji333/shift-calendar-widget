# Shift Calendar PC wallpaper: one-time setup. Safe to run again later to change anything
# (press Enter at any question to keep what you have).
$ErrorActionPreference = 'Stop'
$src = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $env:LOCALAPPDATA 'ShiftCalendarWallpaper'
Write-Host ''
Write-Host '  Shift Calendar - PC wallpaper setup' -ForegroundColor Green
Write-Host '  Your Google Calendar as the desktop picture, in the same look as your phone widget.'
Write-Host '  It refreshes by itself every 15 minutes and when you sign in.'
Write-Host ''

# ----- copy the files to a fixed place, so the download folder can be deleted -----
New-Item -ItemType Directory -Force -Path (Join-Path $dir 'scripts') | Out-Null
if ($src -ne $dir) {
    Copy-Item (Join-Path $src 'wallpaper.html') $dir -Force
    Copy-Item (Join-Path $src 'scripts\*') (Join-Path $dir 'scripts') -Force
    foreach ($f in 'Update now.cmd', 'Setup.cmd', 'Remove.cmd', 'README.txt') { if (Test-Path (Join-Path $src $f)) { Copy-Item (Join-Path $src $f) $dir -Force } }
}
Get-ChildItem $dir -Recurse -File | Unblock-File -ErrorAction SilentlyContinue

# ----- calendars -----
$calFile = Join-Path $dir 'calendars.txt'
$have = @(); if (Test-Path $calFile) { $have = @(Get-Content $calFile | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') }) }
Write-Host '1) Your calendars' -ForegroundColor Yellow
Write-Host '   For each Google calendar to show (your main one, plus any others):'
Write-Host '   open calendar.google.com on this PC > Settings (gear) > click the calendar on the left'
Write-Host '   > Integrate calendar > copy "Secret address in iCal format".'
Write-Host '   Keep that address private: anyone with it can read that calendar.'
if ($have.Count) { Write-Host "   You have $($have.Count) calendar(s) set up. Press Enter to keep them, or paste new ones to replace them." }
$new = @()
while ($true) {
    $u = (Read-Host ('   Paste address ' + ($new.Count + 1) + ' (or just press Enter when done)')).Trim()
    if (-not $u) { break }
    if ($u -notmatch '^(https|webcal)://') { Write-Host '   That does not look like an address (it should start with https://).' -ForegroundColor Red; continue }
    $c = (Read-Host '   Colour for its events, like #F4511E (Enter = automatic)').Trim()
    if ($c -and $c -notmatch '^#[0-9A-Fa-f]{6}$') { $c = '' }
    $new += ($u + $(if ($c) { ' ' + $c } else { '' }))
}
if ($new.Count) {
    $head = @('# One Google Calendar "secret address in iCal format" per line.', '# Optional after it: a colour like #F4511E for that calendar''s events.', '')
    Set-Content -Path $calFile -Value ($head + $new) -Encoding UTF8
} elseif (-not (Test-Path $calFile)) {
    Set-Content -Path $calFile -Value @('# One Google Calendar "secret address in iCal format" per line.', '# Optional after it: a colour like #F4511E for that calendar''s events.') -Encoding UTF8
}

# ----- look -----
Write-Host ''
Write-Host '2) Your look' -ForegroundColor Yellow
Write-Host '   To match your phone: in the Shift Calendar app tap "Receive settings from computer" >'
Write-Host '   "Copy this phone''s settings as a code" and send it to this PC (or press "Copy settings code"'
Write-Host '   in the control panel). Paste it here. Enter = keep the current look.'
$lookFile = Join-Path $dir 'my-look.txt'
$code = (Read-Host '   Settings code').Trim()
if ($code) { Set-Content -Path $lookFile -Value @('# Settings code from the Shift Calendar app or control panel (starts with SC1).', $code) -Encoding UTF8 }
elseif (-not (Test-Path $lookFile)) { Set-Content -Path $lookFile -Value '# Settings code from the Shift Calendar app or control panel (starts with SC1).' -Encoding UTF8 }

# ----- where on the screen -----
$cfgPath = Join-Path $dir 'config.json'
$cfg = [ordered]@{ pos = 'right'; size = 48; scale = 1.25; top = 24; bottom = 64; side = 24; bg = ''; buttons = $false }
if (Test-Path $cfgPath) { $old = Get-Content $cfgPath -Raw | ConvertFrom-Json; foreach ($p in $old.PSObject.Properties) { $cfg[$p.Name] = $p.Value } }
Write-Host ''
Write-Host '3) Where on the screen' -ForegroundColor Yellow
Write-Host "   R = right side (icons stay clear on the left), L = left side, C = centre, F = full screen.  Now: $($cfg.pos)"
$pos = (Read-Host '   R / L / C / F (Enter = keep)').Trim().ToUpper()
switch ($pos) { 'R' { $cfg.pos = 'right' } 'L' { $cfg.pos = 'left' } 'C' { $cfg.pos = 'center' } 'F' { $cfg.pos = 'full' } }
if ($cfg.pos -ne 'full') {
    $sz = (Read-Host "   How wide, as % of the screen (20-100). Now: $($cfg.size)").Trim()
    if ($sz -match '^\d+$') { $cfg.size = [Math]::Max(20, [Math]::Min(100, [int]$sz)) }
}
$sc = (Read-Host "   Text size: 1 = like the phone, 1.25 = a bit bigger, 1.5 = big. Now: $($cfg.scale)").Trim()
if ($sc -match '^\d+(\.\d+)?$') { $cfg.scale = [double]$sc }
$cfg | ConvertTo-Json | Set-Content -Path $cfgPath -Encoding UTF8

# ----- automatic refresh -----
$vbs = Join-Path $dir 'scripts\run-hidden.vbs'
if (Test-Path "$env:windir\System32\wscript.exe") { $act = New-ScheduledTaskAction -Execute 'wscript.exe' -Argument "`"$vbs`"" }
else { $act = New-ScheduledTaskAction -Execute 'conhost.exe' -Argument "--headless powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $dir 'scripts\Update-Wallpaper.ps1')`"" }
$t1 = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$t2 = New-ScheduledTaskTrigger -Once -At (Get-Date).Date -RepetitionInterval (New-TimeSpan -Minutes 15)
$set = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 5) -MultipleInstances IgnoreNew
$who = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName 'Shift Calendar wallpaper' -Action $act -Trigger @($t1, $t2) -Settings $set -Principal $who -Force | Out-Null

Write-Host ''
Write-Host 'Drawing your wallpaper now (takes about 10-20 seconds)...' -ForegroundColor Yellow
& (Join-Path $dir 'scripts\Update-Wallpaper.ps1') -Show
Write-Host ''
Write-Host "Done. Files are in $dir" -ForegroundColor Green
Write-Host 'Run Setup.cmd again any time to change calendars, look or position. "Update now.cmd" redraws straight away.'
Start-Process explorer.exe $dir
