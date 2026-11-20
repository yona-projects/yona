# Yona 설치 부트스트랩(Windows): 고른 릴리스의 설치 묶음(yona-install.zip)을 받아 sha256을 검증한 뒤 풀어서 실행한다.
# 사용법: irm <릴리스 자산 주소>/bootstrap.ps1 | iex   (받아서 읽어 보고 실행하려면 파일로 받아 .\bootstrap.ps1 [옵션...])
# 옵션은 yona-install.ps1과 같다. 스크립트가 파일로 저장되어 있어야 UAC로 다시 실행할 수 있어 묶음을 임시 폴더에 풀어 실행한다.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
function Get-BsText([string]$Url) {
    if ($Url -like 'file://*') { return [System.IO.File]::ReadAllText(([uri]$Url).LocalPath) }
    return (Invoke-WebRequest -Uri $Url -Headers @{ Accept = 'application/vnd.github+json' } -UseBasicParsing).Content
}
$url = if ($env:YONA_RELEASES_URL) { $env:YONA_RELEASES_URL } else { 'https://api.github.com/repos/yona-projects/yona/releases?per_page=30' }
$version = $null
foreach ($a in @($Rest)) { if ($a -like '--version=*') { $version = $a.Substring(10) } }
$releases = @((Get-BsText $url) | ConvertFrom-Json)
if ($version) { $tag = if ($version.StartsWith('v')) { $version } else { "v$version" }; $rel = $releases | Where-Object { $_.tag_name -eq $tag } | Select-Object -First 1 }
else { $rel = $releases | Where-Object { -not $_.draft } | Select-Object -First 1 }
if (-not $rel) { Write-Error 'No release was found.'; exit 1 }
$asset = @($rel.assets) | Where-Object { $_.name -eq 'yona-install.zip' } | Select-Object -First 1
if (-not $asset) { Write-Error "Release $($rel.tag_name) has no yona-install.zip asset. Download the installer files of that release manually."; exit 1 }
$work = Join-Path ([System.IO.Path]::GetTempPath()) ('yona-bootstrap-' + [guid]::NewGuid().ToString('N')); New-Item -ItemType Directory $work | Out-Null
$zip = Join-Path $work 'yona-install.zip'
Write-Host "Downloading the installer of $($rel.tag_name): $($asset.browser_download_url)"
if ($asset.browser_download_url -like 'file://*') { Copy-Item ([uri]$asset.browser_download_url).LocalPath $zip } else { Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $zip -UseBasicParsing }
$digest = if ($asset.PSObject.Properties['digest']) { [string]$asset.digest } else { '' }
if ($digest.StartsWith('sha256:')) {
    if ((Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower() -ne $digest.Substring(7)) { Remove-Item $work -Recurse -Force; Write-Error 'The sha256 of the downloaded installer does not match the release. Aborting.'; exit 1 }
    Write-Host 'The sha256 check passed.'
} else { Write-Host 'Warning: no digest is available, so the installer could not be verified.' }
Expand-Archive -Path $zip -DestinationPath $work
# 고른 릴리스의 설치 프로그램에 같은 인자를 넘긴다(버전이 어긋나지 않는다).
& (Join-Path $work 'yona-install\yona-install.ps1') @Rest
$rc = $LASTEXITCODE
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
exit $rc
