#requires -Version 5
<#
  One-shot setup for the BAIC X7 build of DiPlay.

  Installs (optionally), grants the overlay permission, writes the settings this head unit needs, and
  restarts the app. Everything is idempotent: run it again after a rebuild.

  Examples
      .\setup-x7.ps1
      .\setup-x7.ps1 -Apk ..\..\mobile\build\outputs\apk\debug\mobile-debug.apk
      .\setup-x7.ps1 -Adb "C:\platform-tools\adb.exe" -MediaChannel 4

  Notes
  - The package is the side-by-side debug build (com.shihab.diplay.hudtest), never the release app.
  - Writing preferences needs `adb shell` to be root. Without root the script only grants the overlay
    permission and prints what to set by hand.
#>
param(
    [string]$Adb = 'adb',
    [string]$Apk,
    [int]$MediaChannel = 4,
    [int]$NavigationChannel = 0,
    [switch]$SkipRestart
)

$ErrorActionPreference = 'Stop'
$Package = 'com.shihab.diplay.hudtest'
$Activity = "$Package/com.shilapi.xcertplay.DiPlayActivity"
$PrefsFile = "/data/data/$Package/shared_prefs/xcertplay_airplay.xml"

function Write-Step($text) { Write-Host "`n== $text ==" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "  ok   $text" -ForegroundColor Green }
function Write-Warn2($text) { Write-Host "  warn $text" -ForegroundColor Yellow }

# --- 0. device ---------------------------------------------------------------
Write-Step 'Device'
$devices = & $Adb devices 2>&1
if (($devices -join "`n") -notmatch '\bdevice\b') {
    throw "No authorised device. Enable USB debugging, connect the cable and accept the prompt."
}
$device = (& $Adb shell getprop ro.product.device 2>&1) -join ''
$model = (& $Adb shell getprop ro.product.model 2>&1) -join ''
Write-Ok "$($device.Trim()) / $($model.Trim())"
if ($device.Trim() -ne 'c3005h') {
    Write-Warn2 "This script was measured on ro.product.device=c3005h. Your device is '$($device.Trim())'."
    Write-Warn2 'The overlay route may still work; the panel rectangle and media channel probably need re-measuring.'
}

# --- 1. install --------------------------------------------------------------
if ($Apk) {
    Write-Step 'Install'
    if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }
    & $Adb install -r $Apk 2>&1 | ForEach-Object { "  $_" }
    Write-Ok "installed $Apk"
} else {
    Write-Step 'Install'
    $installed = & $Adb shell "pm path $Package" 2>&1
    if (($installed -join '') -notmatch 'package:') {
        throw "$Package is not installed. Pass -Apk <path-to-mobile-debug.apk>."
    }
    Write-Ok "$Package already installed"
}

# --- 2. overlay permission ---------------------------------------------------
Write-Step 'Overlay permission (SYSTEM_ALERT_WINDOW)'
& $Adb shell "appops set $Package SYSTEM_ALERT_WINDOW allow" 2>&1 | Out-Null
$appop = (& $Adb shell "appops get $Package SYSTEM_ALERT_WINDOW" 2>&1) -join ' '
if ($appop -match 'allow') { Write-Ok 'granted' } else { Write-Warn2 "unexpected state: $appop" }

# --- 3. preferences ----------------------------------------------------------
Write-Step 'Settings (cluster map, audio channels)'
$isRoot = ((& $Adb shell id 2>&1) -join '') -match 'uid=0'
if (-not $isRoot) {
    Write-Warn2 'adb shell is not root; cannot write preferences.'
    Write-Warn2 'Open DiPlay -> Settings and set: CarPlay map on instrument cluster = on,'
    Write-Warn2 "media audio channel = $MediaChannel, navigation audio channel = $NavigationChannel."
} else {
    $uidLine = (& $Adb shell "dumpsys package $Package | grep -m1 userId=" 2>&1) -join ''
    $uid = ($uidLine -replace '.*userId=', '' -replace '[^0-9]', '')
    if (-not $uid) { throw "Could not read the app's uid from: $uidLine" }

    # Reading app-private storage needs SELinux permissive for a moment (adb shell is root, but the
    # su domain cannot enter app data while enforcing).
    & $Adb shell 'setenforce 0' 2>&1 | Out-Null
    try {
        $existing = (& $Adb shell "cat $PrefsFile" 2>&1) -join "`n"
    } finally {
        & $Adb shell 'setenforce 1' 2>&1 | Out-Null
    }

    function Set-Bool([string]$xml, [string]$name, [bool]$value) {
        $entry = "    <boolean name=`"$name`" value=`"$(if ($value) {'true'} else {'false'})`" />"
        if ($xml -match "name=`"$name`"") {
            return [regex]::Replace($xml, "<boolean name=`"$name`"[^/]*/>", $entry.Trim())
        }
        return $xml -replace '</map>', "$entry`n</map>"
    }
    function Set-Int([string]$xml, [string]$name, [int]$value) {
        $entry = "    <int name=`"$name`" value=`"$value`" />"
        if ($xml -match "name=`"$name`"") {
            return [regex]::Replace($xml, "<int name=`"$name`"[^/]*/>", $entry.Trim())
        }
        return $xml -replace '</map>', "$entry`n</map>"
    }

    if ($existing -notmatch '<map>') {
        $xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>"
    } else {
        $xml = $existing
    }
    $xml = Set-Bool $xml 'cluster_map_enabled' $true
    $xml = Set-Int $xml 'media_audio_channel' $MediaChannel
    $xml = Set-Int $xml 'navigation_audio_channel' $NavigationChannel

    $temp = Join-Path ([System.IO.Path]::GetTempPath()) 'diplay-prefs.xml'
    [System.IO.File]::WriteAllText($temp, $xml, (New-Object System.Text.UTF8Encoding($false)))
    & $Adb push $temp /data/local/tmp/diplay-prefs.xml 2>&1 | Out-Null

    & $Adb shell 'setenforce 0' 2>&1 | Out-Null
    try {
        & $Adb shell "mkdir -p /data/data/$Package/shared_prefs" 2>&1 | Out-Null
        & $Adb shell "cp /data/local/tmp/diplay-prefs.xml $PrefsFile" 2>&1 | Out-Null
        & $Adb shell "chown $uid`:$uid $PrefsFile; chmod 660 $PrefsFile" 2>&1 | Out-Null
        & $Adb shell "restorecon $PrefsFile" 2>&1 | Out-Null
        $written = (& $Adb shell "cat $PrefsFile" 2>&1) -join "`n"
    } finally {
        & $Adb shell 'setenforce 1' 2>&1 | Out-Null
    }
    if ($written -match 'cluster_map_enabled' -and $written -match 'media_audio_channel') {
        Write-Ok "cluster_map_enabled=true, media=$MediaChannel, navigation=$NavigationChannel"
    } else {
        Write-Warn2 'Preferences may not have been written; check the app settings manually.'
    }
}

# --- 4. restart --------------------------------------------------------------
if (-not $SkipRestart) {
    Write-Step 'Restart DiPlay'
    & $Adb shell "am force-stop $Package" 2>&1 | Out-Null
    Start-Sleep -Milliseconds 500
    & $Adb shell "am start -n $Activity" 2>&1 | Select-Object -Last 1
    Write-Ok 'started'
}

# --- 5. what to expect -------------------------------------------------------
Write-Step 'Next'
Write-Host @"
  1. Turn on the car hotspot, open DiPlay, connect the iPhone.
  2. The instrument panel should show the CarPlay map.
  3. Check with:
       $Adb logcat -d -s DiPlay-Cluster
       $Adb shell dumpsys window windows | grep -A3 DiPlay-cluster
     Expected: "cluster: overlay 1920x720+0+0 on display 1"
  4. If the panel stays on the stock map, close and reopen DiPlay (re-raises the window).
  5. Full troubleshooting: docs/INSTALL-X7.md
"@
