# Yona 설치 프로그램(Windows, PowerShell 5.1 이상)의 공통 기능: 답 저장소, 메시지, 답 읽기와 검증, 릴리스 읽기, 버전 비교.
# 셸 구현(lib/*.sh)과 같은 동작을 한다. 같은 메시지 파일(messages/*.properties)과 같은 답 항목표를 쓰고,
# 시험(test/ps)은 셸과 같은 입력에서 같은 결과가 나오는지 견준다.
Set-StrictMode -Version 2.0

# 답 항목표이다. 형식은 `키|옵션|종류`(셸의 YI_OPTIONS와 같아야 한다. test/parity.bats가 견준다).
$script:YiOptions = @'
lang|--lang|value
mode|--mode|value
version|--version|value
install_dir|--install-dir|value
data_dir|--data-dir|value
db.type|--db|value
db.install|--db-install|value
db.method|--db-method|value
db.host|--db-host|value
db.port|--db-port|value
db.name|--db-name|value
db.user|--db-user|value
db.password_file|--db-password-file|value
db.admin_user|--db-admin-user|value
db.admin_password_file|--db-admin-password-file|value
site.hostname|--hostname|value
site.port|--public-port|value
site.scheme|--scheme|value
site.behind_https_proxy|--behind-https-proxy|flag
web.port|--port|value
mail.enabled|--mail-enabled|flag
mail.host|--mail-host|value
mail.port|--mail-port|value
mail.ssl|--mail-ssl|flag
mail.user|--mail-user|value
mail.password_file|--mail-password-file|value
mail.from_user|--mail-from-user|value
mail.from_domain|--mail-from-domain|value
mail.mock|--mail-mock|flag
ssh.mode|--ssh|value
ssh.port|--ssh-port|value
ssh.public_port|--ssh-public-port|value
ssh.helper|--ssh-helper|value
v16.dir|--v16-dir|value
v16.conf|--v16-conf|value
v16.db|--v16-db|value
v16.timezone|--v16-timezone|value
service.register|--service|flag
install_jdk|--install-jdk|flag
confirm.backup|--confirm-backup|flag
confirm.replace_data|--confirm-replace-data|flag
yes|--yes|flag
yes|--non-interactive|flag
dry_run|--dry-run|flag
offline|--offline|flag
download_only|--download-only|flag
keep_on_failure|--keep-on-failure|flag
allow_downgrade|--allow-downgrade|flag
purge|--purge|flag
answers|--answers|skip
'@

$script:YiBoolKeys = 'site.behind_https_proxy mail.enabled mail.ssl mail.mock service.register install_jdk confirm.backup confirm.replace_data yes dry_run offline download_only keep_on_failure allow_downgrade purge'.Split(' ')
$script:YiLangs = @('ko', 'en')
$script:Ans = @{}
$script:Msgs = @{}

# --- 답 저장소 ---
function Clear-YiAnswers { $script:Ans = @{} }
function Set-YiAnswer([string]$Key, [string]$Value) { $script:Ans[$Key] = $Value }
function Get-YiAnswer([string]$Key, [string]$Default = '') {
    if ($script:Ans.ContainsKey($Key) -and -not [string]::IsNullOrEmpty($script:Ans[$Key])) { return [string]$script:Ans[$Key] }
    return $Default
}
function Test-YiAnswer([string]$Key) { return -not [string]::IsNullOrEmpty((Get-YiAnswer $Key)) }

# --- 메시지 ---
function Get-YiMessagesDir { if ($env:YI_MESSAGES_DIR) { return $env:YI_MESSAGES_DIR } return (Join-Path $PSScriptRoot '..\..\messages') }

function Read-YiMessageFile([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        if ($line -eq '' -or $line.StartsWith('#')) { continue }
        $i = $line.IndexOf('=')
        if ($i -gt 0) { $script:Msgs[$line.Substring(0, $i)] = $line.Substring($i + 1) }
    }
}

# 영어를 먼저 읽고 고른 언어를 덮어써 빠진 키를 영어로 채운다.
function Import-YiMessages([string]$Lang) {
    $dir = Get-YiMessagesDir
    $script:Msgs = @{}
    Read-YiMessageFile (Join-Path $dir 'en.properties')
    if ($Lang -ne 'en') { Read-YiMessageFile (Join-Path $dir "$Lang.properties") }
}

function Set-YiMessage([string]$Key, [string]$Text) { $script:Msgs[$Key] = $Text }

# `{1}`, `{2}`를 인자로 바꾸고 `\n`을 줄바꿈으로 푼다. 없는 키는 키 이름을 돌려준다.
function Get-YiMessage([string]$Key, [string[]]$Arguments = @()) {
    if (-not $script:Msgs.ContainsKey($Key)) { return $Key }
    $text = $script:Msgs[$Key].Replace('\n', "`n")
    for ($i = 0; $i -lt $Arguments.Count; $i++) { $text = $text.Replace('{' + ($i + 1) + '}', $Arguments[$i]) }
    return $text
}

# 시스템 언어(UI 문화권 이름, 예: ko-KR)를 돌려준다.
function Get-YiSystemLocale {
    if ($env:YI_LOCALE) { return $env:YI_LOCALE }
    return [System.Globalization.CultureInfo]::CurrentUICulture.Name
}

# `--lang`, `YONA_INSTALL_LANG`, 시스템 언어(ko이면 한국어), 영어 순이다. 지원하지 않는 값이면 $null을 돌려준다.
function Resolve-YiLanguage([string]$Flag, [string]$EnvValue, [string]$System) {
    foreach ($c in @($Flag, $EnvValue)) {
        if ($c) { if ($script:YiLangs -contains $c) { return $c } else { return $null } }
    }
    if ($System -and $System.StartsWith('ko')) { return 'ko' }
    return 'en'
}

# --- 답 읽기 ---
function Get-YiOptionTable {
    $rows = @()
    foreach ($line in ($script:YiOptions -split "`r?`n")) {
        if (-not $line) { continue }
        $p = $line.Split('|')
        $rows += [pscustomobject]@{ Key = $p[0]; Option = $p[1]; Kind = $p[2] }
    }
    return $rows
}

function ConvertTo-YiEnvName([string]$Key) { return 'YONA_INSTALL_' + ($Key -replace '[.\-]', '_').ToUpperInvariant() }

# 환경 변수, 답 파일, 옵션 순으로 읽어 나중 것이 앞의 것을 덮는다. 알 수 없는 옵션이면 예외를 던진다.
function Import-YiAnswers([string[]]$Arguments = @()) {
    $table = Get-YiOptionTable
    foreach ($row in $table) {
        if ($row.Kind -eq 'skip') { continue }
        $v = [Environment]::GetEnvironmentVariable((ConvertTo-YiEnvName $row.Key))
        if ($v) { Set-YiAnswer $row.Key $v }
    }
    # 답 파일
    for ($i = 0; $i -lt $Arguments.Count; $i++) {
        $a = $Arguments[$i]; $file = $null
        if ($a -like '--answers=*') { $file = $a.Substring(10) } elseif ($a -eq '--answers' -and $i + 1 -lt $Arguments.Count) { $file = $Arguments[$i + 1] }
        if ($file) {
            if (-not (Test-Path $file)) { throw "answers file not found: $file" }
            foreach ($line in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) {
                $t = $line.Trim()
                if ($t -eq '' -or $t.StartsWith('#') -or -not $t.Contains('=')) { continue }
                $k = $t.Substring(0, $t.IndexOf('=')).Trim(); $val = $t.Substring($t.IndexOf('=') + 1).Trim()
                if ($k) { Set-YiAnswer $k $val }
            }
        }
    }
    # 옵션
    $i = 0
    while ($i -lt $Arguments.Count) {
        $a = $Arguments[$i]; $i++
        if (-not $a.StartsWith('--')) { throw "unexpected argument: $a" }
        $name = $a; $value = $null; $hasValue = $false
        $eq = $a.IndexOf('=')
        if ($eq -gt 0) { $name = $a.Substring(0, $eq); $value = $a.Substring($eq + 1); $hasValue = $true }
        $row = $table | Where-Object { $_.Option -eq $name } | Select-Object -First 1
        if (-not $row) { throw "unknown option: $name" }
        if ($row.Kind -eq 'skip') { if (-not $hasValue) { $i++ }; continue }
        if (-not $hasValue) {
            if ($row.Kind -eq 'flag') { $value = 'true' }
            else {
                if ($i -ge $Arguments.Count) { throw "option requires a value: $name" }
                $value = $Arguments[$i]; $i++
            }
        }
        Set-YiAnswer $row.Key $value
    }
}

# yes, no, true, false를 true, false로 맞춘다.
function Convert-YiBooleans {
    foreach ($key in $script:YiBoolKeys) {
        $v = (Get-YiAnswer $key).ToLowerInvariant()
        if (@('yes', 'true', 'y', '1') -contains $v) { Set-YiAnswer $key 'true' }
        elseif (@('no', 'false', 'n', '0') -contains $v) { Set-YiAnswer $key 'false' }
    }
}

function Test-YiPort([string]$Value) {
    if ($Value -notmatch '^[0-9]+$') { return $false }
    $n = [int]$Value
    return ($n -ge 1 -and $n -le 65535)
}

function Test-YiEnum([string]$Key, [string[]]$Allowed) {
    $v = Get-YiAnswer $Key
    if ($v -and ($Allowed -notcontains $v)) { return "invalid|$Key|$v" }
}

# 답을 검증해 `invalid|키|값` 줄을 모두 돌려준다(없으면 빈 배열).
function Test-YiAnswers {
    Convert-YiBooleans
    $errors = @()
    $errors += Test-YiEnum 'lang' @('ko', 'en')
    $errors += Test-YiEnum 'mode' @('install', 'migrate')
    $errors += Test-YiEnum 'db.type' @('postgresql', 'mariadb', 'cubrid', 'mssql', 'h2')
    $errors += Test-YiEnum 'db.install' @('together', 'separate')
    $errors += Test-YiEnum 'db.method' @('container', 'package')
    $errors += Test-YiEnum 'site.scheme' @('http', 'https')
    $errors += Test-YiEnum 'ssh.mode' @('none', 'builtin', 'system')
    $errors += Test-YiEnum 'v16.db' @('mariadb', 'mysql', 'h2')
    foreach ($k in @('web.port', 'site.port', 'ssh.port', 'ssh.public_port', 'db.port', 'mail.port')) {
        $v = Get-YiAnswer $k
        if ($v -and -not (Test-YiPort $v)) { $errors += "invalid|$k|$v" }
    }
    foreach ($k in @('db.name', 'db.user')) {
        $v = Get-YiAnswer $k
        if ($v -and $v -cnotmatch '^[a-z][a-z0-9_]{0,62}$') { $errors += "invalid|$k|$v" }
    }
    $v = Get-YiAnswer 'db.admin_user'
    if ($v -and $v -notmatch '^[A-Za-z][A-Za-z0-9_]{0,62}$') { $errors += "invalid|db.admin_user|$v" }
    $v = Get-YiAnswer 'version'
    if ($v -and $v -notmatch '^v?[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$') { $errors += "invalid|version|$v" }
    foreach ($k in $script:YiBoolKeys) {
        $v = Get-YiAnswer $k
        if ($v -and $v -ne 'true' -and $v -ne 'false') { $errors += "invalid|$k|$v" }
    }
    if ((Get-YiAnswer 'ssh.mode') -eq 'builtin' -and (Get-YiAnswer 'ssh.port') -and (Get-YiAnswer 'ssh.port') -eq (Get-YiAnswer 'web.port' '8080')) {
        $errors += 'conflict|ssh.port|' + (Get-YiAnswer 'ssh.port')
    }
    return @($errors | Where-Object { $_ })
}

function Get-YiRequiredDb {
    $type = Get-YiAnswer 'db.type'
    if (-not $type) { return @('db.type') }
    if ($type -eq 'h2') { return @() }
    $install = Get-YiAnswer 'db.install'
    if (-not $install) { return @('db.install') }
    if ($install -eq 'together') { $keys = @('db.method', 'db.name', 'db.user', 'db.password_file', 'db.admin_password_file') }
    else { $keys = @('db.host', 'db.port', 'db.name', 'db.user', 'db.password_file') }
    return @($keys | Where-Object { -not (Test-YiAnswer $_) })
}

# 비대화형에서 모자란 필수 답의 키를 돌려준다. 모드는 install, migrate, upgrade이다.
function Get-YiRequired([string]$Mode) {
    if ($Mode -eq 'upgrade') { return @(@('version', 'confirm.backup') | Where-Object { -not (Test-YiAnswer $_) }) }
    $missing = @(@('version', 'install_dir', 'data_dir', 'service.register') | Where-Object { -not (Test-YiAnswer $_) })
    if ($Mode -eq 'migrate') {
        $missing += @(@('v16.dir', 'v16.db', 'confirm.backup') | Where-Object { -not (Test-YiAnswer $_) })
        if ((Get-YiAnswer 'v16.db') -eq 'h2') {
            if (-not (Test-YiAnswer 'v16.timezone')) { $missing += 'v16.timezone' }
            $missing += Get-YiRequiredDb
        }
    } else { $missing += Get-YiRequiredDb }
    if ((Get-YiAnswer 'mail.enabled') -eq 'true') { $missing += @(@('mail.host', 'mail.port', 'mail.mock') | Where-Object { -not (Test-YiAnswer $_) }) }
    return @($missing | Where-Object { $_ })
}

# --- 버전과 릴리스 ---

# 정렬용 버전 키(셸의 version_key와 같은 모양). 숫자는 5자리로 채우고 정식은 `1`, 사전 배포는 `0-꼬리표`이다.
function Get-YiVersionKey([string]$Version) {
    $v = $Version.ToLowerInvariant(); if ($v.StartsWith('v')) { $v = $v.Substring(1) }
    $pre = ''
    $i = $v.IndexOf('-')
    if ($i -ge 0) { $pre = $v.Substring($i + 1); $v = $v.Substring(0, $i) }
    $p = $v.Split('.')
    $pad = { param($s) $s.PadLeft(5, '0') }
    $key = (& $pad $p[0]) + '.' + $(if ($p.Count -ge 2) { & $pad $p[1] } else { '00000' }) + '.' + $(if ($p.Count -ge 3) { & $pad $p[2] } else { '00000' }) + '.'
    if ($pre -eq '') { return $key + '1' }
    $padded = [regex]::Replace($pre, '[0-9]+', { param($m) $m.Value.PadLeft(5, '0') })
    return $key + '0-' + $padded
}

# 두 버전을 견주어 1, 0, -1을 돌려준다.
function Compare-YiVersion([string]$A, [string]$B) {
    $ka = Get-YiVersionKey $A; $kb = Get-YiVersionKey $B
    $c = [string]::CompareOrdinal($ka, $kb)
    if ($c -gt 0) { return 1 } elseif ($c -lt 0) { return -1 } else { return 0 }
}

function Get-YiReleaseLabel([string]$Tag, [bool]$Prerelease) {
    $lower = $Tag.ToLowerInvariant()
    if ($lower -like '*-rc*') { return "$Tag (rc)" }
    if ($lower.Contains('-') -or $Prerelease) { return "$Tag (pre-release)" }
    return $Tag
}

# 릴리스 JSON에서 v2 이상 릴리스를 높은 버전부터 최대 N건 고른다(초안, jar 없는 릴리스, -plain.jar 제외).
# 각 항목은 Tag, Label, Url, Size, Digest이다.
function Get-YiReleases([string]$Json, [int]$Limit = 4) {
    $items = @()
    foreach ($r in @($Json | ConvertFrom-Json)) {
        if ($r.draft) { continue }
        if ($r.tag_name -notmatch '^v?([2-9]|[1-9][0-9])') { continue }
        $jar = @($r.assets) | Where-Object { $_.name -like 'yona-*.jar' -and $_.name -notlike '*-plain.jar' } | Select-Object -First 1
        if (-not $jar) { continue }
        $digest = ''; if ($jar.PSObject.Properties['digest'] -and $jar.digest) { $digest = [string]$jar.digest }
        $items += [pscustomobject]@{ Tag = $r.tag_name; Label = (Get-YiReleaseLabel $r.tag_name ([bool]$r.prerelease)); Url = $jar.browser_download_url; Size = [string]$jar.size; Digest = $digest; Key = (Get-YiVersionKey $r.tag_name) }
    }
    return @($items | Sort-Object -Property Key -Descending -CaseSensitive | Select-Object -First $Limit)
}

# 같은 폴더의 yona-<버전>.jar를 폐쇄 환경용 후보로 읽는다(.sha256 파일이 있으면 digest로 쓴다).
function Get-YiLocalReleases([string]$Dir) {
    $items = @()
    foreach ($f in (Get-ChildItem -Path $Dir -Filter 'yona-*.jar' -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike '*-plain.jar' })) {
        $ver = $f.BaseName.Substring(5); $tag = "v$ver"
        $digest = ''
        $sha = "$($f.FullName).sha256"
        if (Test-Path $sha) { $digest = 'sha256:' + ((Get-Content $sha -TotalCount 1) -split '\s+')[0] }
        $items += [pscustomobject]@{ Tag = $tag; Label = (Get-YiReleaseLabel $tag $false); Url = $f.FullName; Size = [string]$f.Length; Digest = $digest; Key = (Get-YiVersionKey $tag) }
    }
    return @($items | Sort-Object -Property Key -Descending -CaseSensitive)
}

function Get-YiSha256([string]$Path) { return (Get-FileHash -Algorithm SHA256 -Path $Path).Hash.ToLowerInvariant() }

# 파일을 digest와 견준다. 0 일치, 1 불일치, 2 검증할 수 없음.
function Test-YiDigest([string]$Path, [string]$Digest) {
    if (-not $Digest -or -not $Digest.StartsWith('sha256:') -or $Digest.Length -le 7) { return 2 }
    if ((Get-YiSha256 $Path) -eq $Digest.Substring(7)) { return 0 } else { return 1 }
}

# 설치된 버전과 고른 버전을 견주어 new, same, upgrade, downgrade를 돌려준다.
function Get-YiInstallState([string]$Installed, [string]$Target) {
    if (-not $Installed) { return 'new' }
    switch (Compare-YiVersion $Target $Installed) { 0 { 'same' } 1 { 'upgrade' } default { 'downgrade' } }
}

# 정식 릴리스에서 사전 배포로 올리면 yes이다.
function Get-YiUpgradeNeedsConfirm([string]$Installed, [string]$Target) {
    if ($Target.Contains('-') -and -not $Installed.Contains('-')) { return 'yes' } else { return 'no' }
}
