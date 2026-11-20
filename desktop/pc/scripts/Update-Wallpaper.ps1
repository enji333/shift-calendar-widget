# Shift Calendar PC wallpaper: downloads your Google calendars, draws the calendar in your widget's
# look (with Microsoft Edge, hidden) and sets it as the desktop picture. Runs every 15 minutes.
param([switch]$Show)
$ErrorActionPreference = 'Stop'
$dir = Split-Path -Parent $PSScriptRoot
$log = Join-Path $dir 'last-update.log'
function Say($m) { $line = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  ' + $m; Add-Content -Path $log -Value $line; if ($Show) { Write-Host $m } }
if ((Test-Path $log) -and (Get-Item $log).Length -gt 200KB) { Remove-Item $log }

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $cfgPath = Join-Path $dir 'config.json'
    $cfg = if (Test-Path $cfgPath) { Get-Content $cfgPath -Raw | ConvertFrom-Json } else { [pscustomobject]@{ pos = 'right'; size = 48; scale = 1.25 } }

    # ----- your calendars (one secret iCal address per line, optional colour and name after it) -----
    $colours = @('#039BE5', '#33B679', '#F4511E', '#8E24AA', '#F6BF26', '#E67C73', '#0B8043', '#3F51B5')
    $cache = Join-Path $dir 'cache'; New-Item -ItemType Directory -Force -Path $cache | Out-Null
    $cals = @(); $i = 0
    $calFile = Join-Path $dir 'calendars.txt'
    $lines = if (Test-Path $calFile) { Get-Content $calFile } else { @() }
    foreach ($raw in $lines) {
        $l = $raw.Trim()
        if ($l -eq '' -or $l.StartsWith('#')) { continue }
        $parts = $l -split '\s+', 3
        $url = $parts[0] -replace '^webcal://', 'https://'
        $col = $colours[$i % $colours.Count]; $name = ''
        foreach ($p in $parts[1..2]) { if ($p -match '^#[0-9A-Fa-f]{6}$') { $col = $p } elseif ($p) { $name = $p } }
        $cf = Join-Path $cache ("cal$i.ics")
        $entry = [ordered]@{ name = $name; color = $col; ics = ''; error = $null }
        try {
            $wc = New-Object Net.WebClient; $wc.Encoding = [Text.Encoding]::UTF8
            $text = $wc.DownloadString($url)
            if ($text -notmatch 'BEGIN:VCALENDAR') { throw 'that address did not return a calendar (check it is the SECRET address in iCal format)' }
            [IO.File]::WriteAllText($cf, $text, (New-Object Text.UTF8Encoding $false))
        } catch {
            $msg = $_.Exception.Message -replace '^Exception calling "\w+" with "\d+" argument\(s\): ', ''
            if (Test-Path $cf) { $text = [IO.File]::ReadAllText($cf); Say "Calendar $($i+1): offline, using the last copy ($msg)" }
            else { $text = $null; $entry.error = $msg; Say "Calendar $($i+1) failed: $msg" }
        }
        if ($text) { $entry.ics = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($text)) }
        $cals += $entry; $i++
    }

    # ----- your look: a settings code from the phone app or the control panel -----
    $code = ''
    $lookFile = Join-Path $dir 'my-look.txt'
    if (Test-Path $lookFile) { $code = ((Get-Content $lookFile | Where-Object { -not $_.Trim().StartsWith('#') }) -join '').Trim() }

    $data = [ordered]@{ config = $cfg; code = $code; calendars = $cals }
    $json = $data | ConvertTo-Json -Depth 6 -Compress
    [IO.File]::WriteAllText((Join-Path $dir 'wallpaper-data.js'), "window.WALL_DATA=$json;", (New-Object Text.UTF8Encoding $false))

    # ----- screen size (real pixels) and Windows scaling -----
    Add-Type -Namespace SC -Name W -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
[DllImport("user32.dll")] public static extern int GetSystemMetrics(int i);
[DllImport("user32.dll")] public static extern uint GetDpiForSystem();
[DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern bool SystemParametersInfo(int a, int b, string c, int d);
'@ -ErrorAction SilentlyContinue
    [SC.W]::SetProcessDPIAware() | Out-Null
    $pw = [SC.W]::GetSystemMetrics(0); $ph = [SC.W]::GetSystemMetrics(1)
    $scale = 1.0; try { $scale = [SC.W]::GetDpiForSystem() / 96.0 } catch { }
    if ($scale -lt 1) { $scale = 1.0 }
    $lw = [int][Math]::Round($pw / $scale); $lh = [int][Math]::Round($ph / $scale)

    # ----- draw it with Edge (or Chrome), hidden -----
    $browsers = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
                  "$env:ProgramFiles\Google\Chrome\Application\chrome.exe", "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe", "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe")
    $browser = $browsers | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $browser) { throw 'Microsoft Edge was not found.' }
    $a = Join-Path $dir 'wallpaper-a.png'; $b = Join-Path $dir 'wallpaper-b.png'
    $out = if (Test-Path $a) { $b } else { $a }; $old = if ($out -eq $a) { $b } else { $a }
    if (Test-Path $out) { Remove-Item $out -Force }
    $page = ([Uri](Join-Path $dir 'wallpaper.html')).AbsoluteUri + "?w=$lw&h=$lh"
    $scaleTxt = $scale.ToString([Globalization.CultureInfo]::InvariantCulture)
    $bargs = @('--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run', '--no-default-browser-check', '--disable-extensions',
              "--user-data-dir=`"$(Join-Path $dir 'browser-profile')`"", "--force-device-scale-factor=$scaleTxt", "--window-size=$lw,$lh",
              '--virtual-time-budget=15000', "--screenshot=`"$out`"", "`"$page`"")
    $proc = Start-Process -FilePath $browser -ArgumentList $bargs -WindowStyle Hidden -PassThru
    if (-not $proc.WaitForExit(90000)) { try { $proc.Kill() } catch { }; throw 'Edge took too long to draw the calendar.' }
    if (-not (Test-Path $out) -or (Get-Item $out).Length -lt 1000) { throw 'Edge did not save a picture.' }

    # ----- make it the desktop picture -----
    Set-ItemProperty -Path 'HKCU:\Control Panel\Desktop' -Name WallpaperStyle -Value '10'
    Set-ItemProperty -Path 'HKCU:\Control Panel\Desktop' -Name TileWallpaper -Value '0'
    [SC.W]::SystemParametersInfo(0x0014, 0, $out, 3) | Out-Null
    if (Test-Path $old) { Remove-Item $old -Force -ErrorAction SilentlyContinue }
    Say "Updated: $($cals.Count) calendar(s), $pw x $ph screen."
} catch {
    Say ('Problem: ' + $_.Exception.Message)
    if ($Show) { exit 1 }
}
