<#
.SYNOPSIS
    Cuts an immutable Sower release: bumps the version, builds every language,
    tags once, and publishes the APKs to GitHub.

.DESCRIPTION
    F-Droid pins each build to a commit hash and watches tags for updates, so a
    tag must never be moved once pushed. This script refuses to reuse a tag,
    always raises versionCode, and uploads both the versioned asset names and
    the evergreen ones the in-app QR code points at.

.EXAMPLE
    pwsh tools/release.ps1 -Version 1.1 -Notes "Character-precise highlighting." -DryRun
    pwsh tools/release.ps1 -Version 1.1 -Notes "Character-precise highlighting."
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$Notes,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$languages = @('en', 'es', 'fr', 'pt', 'ru', 'ar', 'hi', 'sw', 'zh')
$tag = "v$Version"
$gradle = Join-Path $root 'app\build.gradle'

function Fail($message) {
    Write-Host "release: $message" -ForegroundColor Red
    exit 1
}

# --- guard rails -----------------------------------------------------------

if ($Version -notmatch '^[0-9]+\.[0-9]+$') {
    Fail "version must look like 1.1 (got '$Version')"
}
if ((git tag -l $tag)) {
    Fail "$tag already exists locally. Tags are immutable - pick a new version."
}
if ((git ls-remote --tags origin $tag)) {
    Fail "$tag already exists on origin. Tags are immutable - pick a new version."
}
if ((git status --porcelain)) {
    Fail 'working tree is dirty. Commit or stash first.'
}

# --- version bump ----------------------------------------------------------

$text = Get-Content $gradle -Raw
if ($text -notmatch 'versionCode\s+(\d+)') { Fail 'versionCode not found in app/build.gradle' }
$oldCode = [int]$Matches[1]
$newCode = $oldCode + 1
if ($text -notmatch 'versionName\s+"([^"]+)"') { Fail 'versionName not found in app/build.gradle' }
$oldName = $Matches[1]
if ($oldName -eq $Version) { Fail "versionName is already $Version" }

$text = $text -replace 'versionCode\s+\d+', "versionCode $newCode"
$text = $text -replace 'versionName\s+"[^"]+"', "versionName `"$Version`""
Set-Content -Path $gradle -Value $text -Encoding utf8 -NoNewline
Write-Host "release: $oldName ($oldCode) -> $Version ($newCode)" -ForegroundColor Cyan

# F-Droid reads the changelog for the versionCode it is building.
$changelog = Join-Path $root "fastlane\metadata\android\en-US\changelogs\$newCode.txt"
Set-Content -Path $changelog -Value $Notes -Encoding utf8

# --- build -----------------------------------------------------------------

Write-Host 'release: building all languages...' -ForegroundColor Cyan
$build = & "$root\gradlew.bat" assembleRelease 2>$null | Out-String
if ($build -notmatch 'BUILD SUCCESSFUL') {
    Fail "gradle build failed:`n$($build.Substring([Math]::Max(0, $build.Length - 2000)))"
}
foreach ($lang in $languages) {
    $apk = "$root\app\build\outputs\apk\$lang\release\app-$lang-release.apk"
    if (-not (Test-Path $apk)) { Fail "missing APK for $lang" }
}
Write-Host "release: built $($languages.Count) APKs" -ForegroundColor Green

if ($DryRun) {
    Write-Host 'release: dry run - reverting the version bump, nothing published.' -ForegroundColor Yellow
    git checkout -- $gradle
    Remove-Item $changelog -ErrorAction SilentlyContinue
    exit 0
}

# --- commit and tag (once, never moved) ------------------------------------

git add -A
git commit -m "Release $Version" | Out-Null
git tag -a $tag -m "Sower $Version"
git push origin main
git push origin $tag
$commit = (git rev-parse HEAD).Trim()
Write-Host "release: tagged $tag at $commit" -ForegroundColor Green

# --- publish to GitHub -----------------------------------------------------

Add-Type @"
using System; using System.Runtime.InteropServices;
public class SowerCred {
  [DllImport("advapi32.dll", EntryPoint="CredReadW", CharSet=CharSet.Unicode)] static extern bool CredReadW(string target, int type, int flags, out IntPtr cred);
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
  public static string ReadPw(string target) {
    IntPtr p; if (!CredReadW(target, 1, 0, out p)) return null;
    var c = (CREDENTIAL)Marshal.PtrToStructure(p, typeof(CREDENTIAL));
    return c.CredentialBlobSize > 0 ? Marshal.PtrToStringUni(c.CredentialBlob, c.CredentialBlobSize / 2) : "";
  }
}
"@
$token = [SowerCred]::ReadPw('git:https://github.com')
if (-not $token) { Fail 'no GitHub token in Windows Credential Manager' }
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$headers = @{ Authorization = "token $token"; 'User-Agent' = 'sower-release' }

$body = @{ tag_name = $tag; name = "Sower $Version"; body = $Notes } | ConvertTo-Json
$release = Invoke-RestMethod -Uri 'https://api.github.com/repos/Arcdub/sower/releases' `
    -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($body))

$upload = $release.upload_url -replace '\{\?name,label\}', ''
foreach ($lang in $languages) {
    $apk = "$root\app\build\outputs\apk\$lang\release\app-$lang-release.apk"
    # Versioned name for the record, evergreen name so old QR codes keep working.
    foreach ($name in @("Sower-$Version-$lang.apk", "Sower-$lang.apk")) {
        Invoke-RestMethod -Uri "$upload`?name=$name" -Method Post -Headers $headers `
            -ContentType 'application/vnd.android.package-archive' -InFile $apk | Out-Null
    }
}
Write-Host "release: uploaded $($languages.Count * 2) assets to $tag" -ForegroundColor Green
Write-Host ''
Write-Host 'Next: update the F-Droid recipe in fdroiddata with' -ForegroundColor Cyan
Write-Host "  versionName: '$Version'  versionCode: $newCode  commit: $commit"
