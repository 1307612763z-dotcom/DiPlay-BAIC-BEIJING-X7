#requires -Version 5
<#
  Push the current branch to both repositories.

  - origin : the GitHub fork of shihabal3amri/DiPlay (keeps the upstream link, can open PRs)
  - mirror : a plain repository with the same content, so GitHub's default search finds it
             (repository search excludes forks unless fork:true is given)

  Usage
      .\sync-mirror.ps1
      .\sync-mirror.ps1 -Message "fix: something"   # commit staged changes first
      .\sync-mirror.ps1 -Branch baic-x7
#>
param(
    [string]$Branch = 'baic-x7',
    [string]$Message
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$git = 'git'

# GitHub Desktop ships git; fall back to it when git is not on PATH.
if (-not (Get-Command $git -ErrorAction SilentlyContinue)) {
    $desktop = Get-ChildItem "$env:LOCALAPPDATA\GitHubDesktop" -Directory -Filter 'app-*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($desktop) {
        $candidate = Join-Path $desktop.FullName 'resources\app\git\cmd\git.exe'
        if (Test-Path $candidate) { $git = $candidate }
    }
}
if (-not (Get-Command $git -ErrorAction SilentlyContinue) -and -not (Test-Path $git)) {
    throw 'git not found. install Git or GitHub Desktop.'
}

Push-Location $repo
try {
    if ($Message) {
        & $git add -A
        & $git commit -m $Message
    }

    $dirty = (& $git status --porcelain) -join ''
    if ($dirty) {
        Write-Warning 'Working tree has uncommitted changes; they will not be pushed.'
    }

    Write-Host "pushing $Branch -> origin/$Branch"
    & $git push origin $Branch
    if ($LASTEXITCODE -ne 0) { throw 'origin push failed' }

    Write-Host "pushing $Branch -> mirror/main (discovery mirror)"
    & $git push mirror "${Branch}:main"
    if ($LASTEXITCODE -ne 0) { throw 'mirror push failed (is the mirror remote configured?)' }

    Write-Host ''
    Write-Host 'done:'
    & $git log --oneline -1
    & $git remote -v
} finally {
    Pop-Location
}
