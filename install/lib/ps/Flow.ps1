# Yona 설치 프로그램(Windows, PowerShell)의 흐름: 환경 감지, 사전 점검, 계획, 질문, 적용(필요하면 UAC로 격상), 시작, 상태 확인,
# 업그레이드, 제거. 셸의 lib/flow.sh와 같은 결정을 따른다(일반 사용자로 시작하고 필요한 단계만 격상, 한 번에 격상, 되돌리기 기록).
# Windows에서는 DB를 컨테이너(Podman 권장)로만 설치하고, SSH는 내장 서버만 쓴다(번들 OpenSSH가 AuthorizedKeysCommand를 지원하지 않는다).
# 종료 코드: 0 성공, 1 실패, 2 입력 오류(아무것도 바꾸지 않음).
Set-StrictMode -Version 2.0
$ProgressPreference = 'SilentlyContinue'

$script:OnWindows = ($PSVersionTable.PSEdition -eq 'Desktop') -or ((Get-Variable IsWindows -ValueOnly -ErrorAction SilentlyContinue) -eq $true)
$script:YiMinJava = 21

# 출력은 콘솔에 쓰고 시험이 읽을 수 있게 기록 버퍼(`$script:YiLog`)에도 남긴다.
$script:YiLog = New-Object System.Collections.Generic.List[string]
function Write-YiLine([string]$Text) { $script:YiLog.Add($Text); Write-Host $Text }
function Write-YiError([string]$Text) { $script:YiLog.Add($Text); [Console]::Error.WriteLine($Text) }

# --- 환경 ---

function Initialize-YiEnvironment {
    Set-YiAnswer 'env.os' 'windows'
    Set-YiAnswer 'env.user' $(if ($env:USERNAME) { $env:USERNAME } else { [Environment]::UserName })
    try { $iana = ''; if ([TimeZoneInfo]::TryConvertWindowsIdToIanaId([TimeZoneInfo]::Local.Id, [ref]$iana)) { Set-YiAnswer 'env.tz' $iana } } catch { }
    Set-YiAnswer 'env.containers' ''
}

function Get-YiHomeDir { if ($env:USERPROFILE) { return $env:USERPROFILE } return $HOME }
function Get-YiProgramFiles { if ($env:ProgramFiles) { return $env:ProgramFiles } return 'C:\Program Files' }
function Get-YiProgramData { if ($env:ProgramData) { return $env:ProgramData } return 'C:\ProgramData' }

# 기본 설치 폴더와 데이터 폴더이다. 서비스를 등록하면 시스템 경로, 아니면 홈 폴더 아래(관리자 권한이 필요 없다).
function Get-YiDefaultInstallDir([bool]$Service) { if ($Service) { return (Join-Path (Get-YiProgramFiles) 'Yona') } return (Join-Path (Get-YiHomeDir) 'yona') }
function Get-YiDefaultDataDir([bool]$Service) { if ($Service) { return (Join-Path (Get-YiProgramData) 'Yona') } return (Join-Path (Get-YiHomeDir) 'yona-data') }

# 설치 기록 폴더이다(시스템 구성은 ProgramData, 일반 사용자 구성은 LOCALAPPDATA).
function Get-YiSystemRecordDir { if ($env:YI_SYSTEM_RECORD_DIR) { return $env:YI_SYSTEM_RECORD_DIR } return (Join-Path (Get-YiProgramData) 'Yona\install') }
function Get-YiUserRecordDir { $base = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { Join-Path (Get-YiHomeDir) '.local/state' }; return (Join-Path $base 'Yona\install') }
function Find-YiRecord {
    foreach ($d in @((Get-YiSystemRecordDir), (Get-YiUserRecordDir))) { $f = Join-Path $d 'install.rec'; if (Test-Path $f) { return $f } }
    return $null
}
function Read-YiRecordValue([string]$File, [string]$Key) {
    foreach ($l in [System.IO.File]::ReadAllLines($File)) { if ($l.StartsWith("$Key=")) { return $l.Substring($Key.Length + 1) } }
    return ''
}

# --- 사전 점검 ---

function Get-YiJavaMajor([string]$Output) {
    if ($Output -notmatch 'version "([^"]*)"') { return 0 }
    $v = $Matches[1]; $parts = $v.Split('.'); $major = $parts[0]
    if ($major -eq '1' -and $parts.Count -gt 1) { $major = $parts[1] }
    $major = ($major -replace '[^0-9].*$', '')
    if ($major) { return [int]$major } else { return 0 }
}

function Get-YiJavaMajorOf([string]$Path) {
    if (-not $Path -or -not (Test-Path $Path)) { return 0 }
    try { $out = & $Path -version 2>&1 | Select-Object -First 1; return (Get-YiJavaMajor ([string]$out)) } catch { return 0 }
}

function Test-YiJava {
    $cands = @((Get-YiAnswer 'env.java'))
    if ($env:JAVA_HOME) { $cands += (Join-Path $env:JAVA_HOME 'bin/java') }
    if ($env:YI_NO_PATH_JAVA -ne '1') { $c = Get-Command java -ErrorAction SilentlyContinue; if ($c) { $cands += $c.Source } }
    $best = 0
    foreach ($c in $cands) {
        if (-not $c) { continue }
        $m = Get-YiJavaMajorOf $c
        if ($m -ge $script:YiMinJava) { Set-YiAnswer 'env.java' $c; Set-YiAnswer 'env.need_jdk' 'false'; return @() }
        if ($m -gt $best) { $best = $m }
    }
    Set-YiAnswer 'env.need_jdk' 'true'
    if ($best -gt 0) { return @("FAIL|java|JDK $best found, but $script:YiMinJava or later is required") }
    return @("FAIL|java|JDK $script:YiMinJava or later was not found")
}

function Test-YiPortInUse([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $r = $client.BeginConnect('127.0.0.1', $Port, $null, $null); if ($r.AsyncWaitHandle.WaitOne(500) -and $client.Connected) { return $true } return $false }
    catch { return $false } finally { $client.Close() }
}

function Test-YiPorts {
    $out = @(); $ports = @([int](Get-YiAnswer 'web.port' '8080'))
    if ((Get-YiAnswer 'ssh.mode') -eq 'builtin') { $ports += [int](Get-YiAnswer 'ssh.port' '2222') }
    if ((Get-YiAnswer 'db.install') -eq 'together') { $p = [int](Get-YiAnswer 'db.port' (Get-YiDbDefaultPort (Get-YiAnswer 'db.type'))); if ($p -gt 0) { $ports += $p } }
    foreach ($p in $ports) { if (Test-YiPortInUse $p) { $out += "FAIL|port|$p" } }
    return $out
}

function Get-YiExistingAncestor([string]$Path) {
    $p = $Path
    while ($p -and -not (Test-Path $p)) { $parent = Split-Path $p -Parent; if ($parent -eq $p) { break }; $p = $parent }
    return $p
}

# 경로에 지금 사용자가 쓸 수 있으면 참이다(임시 파일을 만들어 확인한다).
function Test-YiPathWritable([string]$Path) {
    $a = Get-YiExistingAncestor $Path
    if (-not $a) { return $false }
    $probe = Join-Path $a ('.yona-probe-' + [guid]::NewGuid().ToString('N'))
    try { New-Item -ItemType File -Path $probe -ErrorAction Stop | Out-Null; Remove-Item $probe -Force; return $true } catch { return $false }
}

function Test-YiAdministrator {
    if (-not $script:OnWindows) { return $false }
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return ([Security.Principal.WindowsPrincipal]$id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# 선택한 작업에 관리자 권한이 필요한가(서비스 등록, 쓸 수 없는 경로, 1024 미만 포트, Docker, JDK 설치).
function Test-YiNeedsPrivilege {
    if ((Get-YiAnswer 'service.register') -eq 'true') { return $true }
    if (-not (Test-YiPathWritable (Get-YiAnswer 'install_dir'))) { return $true }
    if (-not (Test-YiPathWritable (Get-YiAnswer 'data_dir'))) { return $true }
    if ([int](Get-YiAnswer 'web.port' '8080') -lt 1024) { return $true }
    if ((Get-YiAnswer 'install_jdk') -eq 'true' -and (Get-YiAnswer 'env.need_jdk') -eq 'true') { return $true }
    return $false
}

function Test-YiPrivilege {
    if (-not (Test-YiNeedsPrivilege)) { return @() }
    if ((Test-YiAdministrator) -or $env:YI_ADMIN -eq '1') { return @() }
    if ((Get-YiAnswer 'yes') -eq 'true') { return @('FAIL|privilege|administrator rights are required but the run is non-interactive (UAC cannot be answered)') }
    return @()
}

function Test-YiSecretFiles {
    $out = @()
    foreach ($key in 'db.password_file', 'db.admin_password_file', 'mail.password_file') {
        if (-not (Test-YiAnswer $key)) { continue }
        if ($key -eq 'mail.password_file' -and (Get-YiAnswer 'mail.enabled') -ne 'true') { continue }
        if ($key -ne 'mail.password_file' -and (Get-YiAnswer 'db.type') -eq 'h2') { continue }
        $f = Get-YiAnswer $key
        if (-not (Test-Path $f -PathType Leaf)) { $out += "FAIL|secret-file|$key"; continue }
        if (-not (Test-YiPasswordOk (Read-YiSecretFile $f))) { $out += "FAIL|secret-file-content|$key" }
    }
    return $out
}

function Test-YiContainerTool {
    if ((Get-YiAnswer 'db.install') -ne 'together') { return @() }
    foreach ($t in 'podman', 'docker') {
        if ($env:YI_NO_CONTAINER -eq '1') { break }
        if (Get-Command $t -ErrorAction SilentlyContinue) { Set-YiAnswer 'env.containers' $t; return @() }
    }
    return @('FAIL|container|podman or docker is not available (Podman is recommended on Windows)')
}

function Test-YiDisk {
    $out = @()
    foreach ($p in @((Get-YiAnswer 'install_dir'), (Get-YiAnswer 'data_dir'))) {
        if (-not $p) { continue }
        try { $root = [System.IO.Path]::GetPathRoot((Resolve-Path (Get-YiExistingAncestor $p)).Path); $free = ([System.IO.DriveInfo]$root).AvailableFreeSpace
            if ($free -lt 1GB) { $out += "FAIL|disk|$p $([int]($free / 1KB))KB" } } catch { }
    }
    return $out
}

# 모든 점검을 모아 `FAIL|코드|상세`, `WARN|...` 줄로 돌려준다.
function Invoke-YiPreflight {
    $all = @()
    $all += Test-YiJava; $all += Test-YiPorts; $all += Test-YiDisk; $all += Test-YiPrivilege; $all += Test-YiSecretFiles; $all += Test-YiContainerTool
    return @($all | Where-Object { $_ })
}

# --- 계획 ---

# 계획의 단계를 `id|priv` 줄로 돌려준다(셸의 plan_steps와 같다).
function Get-YiPlanSteps {
    if ((Get-YiAnswer 'env.mode') -eq 'upgrade') {
        $svc = if ((Get-YiAnswer 'service.register') -eq 'true') { 'yes' } else { 'no' }
        return @('preflight|no', 'fetch|no', 'backup-confirm|no', "replace|$svc", "restart|$svc", 'health|no', 'report|no')
    }
    $svc = (Get-YiAnswer 'service.register') -eq 'true'
    $filesPriv = if ($svc -or -not (Test-YiPathWritable (Get-YiAnswer 'install_dir')) -or -not (Test-YiPathWritable (Get-YiAnswer 'data_dir'))) { 'yes' } else { 'no' }
    $s = @('preflight|no', 'fetch|no', 'stage|no')
    if ((Get-YiAnswer 'install_jdk') -eq 'true' -and (Get-YiAnswer 'env.need_jdk') -eq 'true') { $s += 'jdk|yes' }
    if ((Get-YiAnswer 'db.install') -eq 'together') { $s += 'db|' + $(if ($env:YI_ADMIN -eq '1' -or (Test-YiAdministrator)) { 'no' } else { 'no' }) }
    $s += "files|$filesPriv"
    if ($svc) { $s += 'service|yes' }
    $s += 'start|no', 'health|no', 'report|no'
    return $s
}

function Get-YiPrivilegedSteps { return @(Get-YiPlanSteps | Where-Object { $_.EndsWith('|yes') } | ForEach-Object { $_.Split('|')[0] }) }

function Format-YiPlan {
    $n = 1; $out = @()
    foreach ($line in (Get-YiPlanSteps)) {
        $id, $priv = $line.Split('|')
        $mark = if ($priv -eq 'yes') { '[admin]' } else { '       ' }
        $out += ('{0,2}. {1} {2}' -f $n, $mark, (Get-YiMessage "step.$id")); $n++
    }
    return $out
}

# --- 질문 ---

$script:YiInput = $null
$script:YiInputLines = $null
$script:YiSecretDir = $null
function Test-YiInteractive { return ((Get-YiAnswer 'yes') -ne 'true') -and ($script:YiInput -ne 'none') }

function Read-YiLine {
    if ($env:YI_INPUT) {
        if ($null -eq $script:YiInputLines) { $script:YiInputLines = [System.Collections.Generic.Queue[string]]::new([string[]][System.IO.File]::ReadAllLines($env:YI_INPUT)) }
        if ($script:YiInputLines.Count -eq 0) { throw (Get-YiMessage 'err.input_closed') }
        return $script:YiInputLines.Dequeue()
    }
    return (Read-Host)
}

function Ask-YiText([string]$Key, [string]$MsgKey, [string]$Default = '', [bool]$Optional = $false) {
    if (Test-YiAnswer $Key) { return }
    if (-not (Test-YiInteractive)) { if ($Optional -and $Default) { Set-YiAnswer $Key $Default }; return }
    $prompt = Get-YiMessage $MsgKey; if ($Default) { $prompt += " [$Default]" }
    Write-Host -NoNewline "$prompt "
    $line = Read-YiLine
    if ($line) { Set-YiAnswer $Key $line } elseif ($Default) { Set-YiAnswer $Key $Default }
}

function Ask-YiChoice([string]$Key, [string]$MsgKey, [string[]]$Choices, [string]$Default = '', [bool]$Optional = $false) {
    if (Test-YiAnswer $Key) { return }
    if (-not (Test-YiInteractive)) { if ($Optional -and $Default) { Set-YiAnswer $Key $Default }; return }
    for ($t = 0; $t -lt 5; $t++) {
        Write-YiLine (Get-YiMessage $MsgKey)
        for ($i = 0; $i -lt $Choices.Count; $i++) {
            $label = Get-YiMessage "choice.$Key.$($Choices[$i])"; if ($label -eq "choice.$Key.$($Choices[$i])") { $label = $Choices[$i] }
            Write-YiLine ('  {0}) {1}' -f ($i + 1), $label)
        }
        Write-Host -NoNewline "> [$Default] "
        $line = Read-YiLine; if (-not $line) { $line = $Default }
        for ($i = 0; $i -lt $Choices.Count; $i++) { if ($line -eq $Choices[$i] -or $line -eq [string]($i + 1)) { Set-YiAnswer $Key $Choices[$i]; return } }
        Write-YiLine (Get-YiMessage 'prompt.invalid')
    }
    throw (Get-YiMessage 'prompt.invalid')
}

function Ask-YiYesNo([string]$Key, [string]$MsgKey, [bool]$Default = $false, [bool]$Optional = $false) {
    if (Test-YiAnswer $Key) { return }
    if (-not (Test-YiInteractive)) { if ($Optional) { Set-YiAnswer $Key $(if ($Default) { 'true' } else { 'false' }) }; return }
    $hint = if ($Default) { 'Y/n' } else { 'y/N' }
    for ($t = 0; $t -lt 5; $t++) {
        Write-Host -NoNewline ((Get-YiMessage $MsgKey) + " [$hint] ")
        switch ((Read-YiLine).ToLowerInvariant()) { { $_ -in 'y', 'yes' } { Set-YiAnswer $Key 'true'; return } { $_ -in 'n', 'no' } { Set-YiAnswer $Key 'false'; return } '' { Set-YiAnswer $Key $(if ($Default) { 'true' } else { 'false' }); return } }
        Write-YiLine (Get-YiMessage 'prompt.invalid')
    }
    throw (Get-YiMessage 'prompt.invalid')
}

# 비밀번호 질문: 두 번 입력해 같으면 현재 사용자만 읽는 임시 파일에 저장하고 경로를 답에 담는다.
function Ask-YiSecret([string]$Key, [string]$MsgKey) {
    if (Test-YiAnswer $Key) { return }
    if (-not (Test-YiInteractive)) { return }
    $dir = Get-YiSecretDir
    for ($t = 0; $t -lt 5; $t++) {
        if ($env:YI_INPUT) { Write-Host -NoNewline ((Get-YiMessage $MsgKey) + ': '); $a = Read-YiLine; $b = Read-YiLine }
        else {
            $a = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR((Read-Host (Get-YiMessage $MsgKey) -AsSecureString)))
            $b = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR((Read-Host ((Get-YiMessage $MsgKey) + ' (' + (Get-YiMessage 'prompt.again') + ')') -AsSecureString)))
        }
        if (-not (Test-YiPasswordOk $a)) { Write-YiLine (Get-YiMessage 'prompt.password_rule'); continue }
        if ($a -ne $b) { Write-YiLine (Get-YiMessage 'prompt.mismatch'); continue }
        $f = Join-Path $dir (($Key -replace '[.\-]', '_'))
        [System.IO.File]::WriteAllText($f, $a + "`n", (New-Object System.Text.UTF8Encoding($false)))
        if ($script:OnWindows) { try { icacls $f /inheritance:r /grant:r "$($env:USERNAME):F" | Out-Null } catch { } }
        Set-YiAnswer $Key $f; return
    }
    throw (Get-YiMessage 'prompt.password_rule')
}

function Get-YiSecretDir {
    if (-not $script:YiSecretDir) { $script:YiSecretDir = Join-Path ([System.IO.Path]::GetTempPath()) ('yona-install-secrets-' + [guid]::NewGuid().ToString('N')); New-Item -ItemType Directory $script:YiSecretDir | Out-Null }
    return $script:YiSecretDir
}

function Get-YiAdminDefault([string]$Type) { switch ($Type) { 'postgresql' { 'postgres' } 'mariadb' { 'root' } 'mssql' { 'sa' } 'cubrid' { 'dba' } } }

function Invoke-YiQuestions {
    $svc = $false
    Ask-YiYesNo 'service.register' 'prompt.service' $true
    $svc = (Get-YiAnswer 'service.register') -eq 'true'
    Ask-YiText 'install_dir' 'prompt.install_dir' (Get-YiDefaultInstallDir $svc)
    Ask-YiText 'data_dir' 'prompt.data_dir' (Get-YiDefaultDataDir $svc)
    if ((Get-YiAnswer 'env.mode') -eq 'migrate') {
        Ask-YiText 'v16.dir' 'prompt.v16_dir' ''; Ask-YiChoice 'v16.db' 'prompt.v16_db' @('mariadb', 'mysql', 'h2') 'mariadb'
        Ask-YiYesNo 'confirm.backup' 'prompt.backup' $false
        if ((Get-YiAnswer 'v16.db') -eq 'h2') { Ask-YiText 'v16.timezone' 'prompt.v16_timezone' (Get-YiAnswer 'env.tz') }
    }
    if ((Get-YiAnswer 'env.mode') -ne 'migrate' -or (Get-YiAnswer 'v16.db') -eq 'h2') {
        Ask-YiChoice 'db.type' 'prompt.db_type' @('postgresql', 'mariadb', 'cubrid', 'mssql', 'h2') 'postgresql'
        $type = Get-YiAnswer 'db.type'
        if ($type -and $type -ne 'h2') {
            Ask-YiChoice 'db.install' 'prompt.db_install' @('together', 'separate') 'together'
            if ((Get-YiAnswer 'db.install') -eq 'together') {
                if (-not (Test-YiAnswer 'db.method')) { Set-YiAnswer 'db.method' 'container' }
                Ask-YiText 'db.name' 'prompt.db_name' 'yona' $true; Ask-YiText 'db.user' 'prompt.db_user' 'yona' $true; Ask-YiSecret 'db.password_file' 'prompt.db_password'
                Ask-YiText 'db.admin_user' 'prompt.db_admin_user' (Get-YiAdminDefault $type) $true; Ask-YiSecret 'db.admin_password_file' 'prompt.db_admin_password'
            } else {
                Ask-YiText 'db.host' 'prompt.db_host' ''; Ask-YiText 'db.port' 'prompt.db_port' (Get-YiDbDefaultPort $type) $true
                Ask-YiText 'db.name' 'prompt.db_name' 'yona' $true; Ask-YiText 'db.user' 'prompt.db_user' 'yona' $true; Ask-YiSecret 'db.password_file' 'prompt.db_password'
            }
        }
    }
    Ask-YiText 'site.hostname' 'prompt.hostname' ''
    Ask-YiChoice 'site.scheme' 'prompt.scheme' @('http', 'https') 'http' $true
    if ((Get-YiAnswer 'site.scheme') -eq 'https') { Ask-YiYesNo 'site.behind_https_proxy' 'prompt.https_proxy' $false $true }
    Ask-YiText 'web.port' 'prompt.web_port' '8080' $true
    Ask-YiYesNo 'mail.enabled' 'prompt.mail' $false $true
    if ((Get-YiAnswer 'mail.enabled') -eq 'true') {
        Ask-YiText 'mail.host' 'prompt.mail_host' ''; Ask-YiText 'mail.port' 'prompt.mail_port' '587'; Ask-YiYesNo 'mail.ssl' 'prompt.mail_ssl' $false $true
        Ask-YiText 'mail.user' 'prompt.mail_user' ''; Ask-YiSecret 'mail.password_file' 'prompt.mail_password'
        Ask-YiText 'mail.from_user' 'prompt.mail_from_user' ''; Ask-YiText 'mail.from_domain' 'prompt.mail_from_domain' ''; Ask-YiYesNo 'mail.mock' 'prompt.mail_mock' $false
    }
    Ask-YiChoice 'ssh.mode' 'prompt.ssh' @('none', 'builtin') 'none' $true
    if ((Get-YiAnswer 'ssh.mode') -eq 'builtin') { Ask-YiText 'ssh.port' 'prompt.ssh_port' '2222' $true; Ask-YiText 'ssh.public_port' 'prompt.ssh_public_port' '' }
}

# --- 적용 스크립트(PowerShell) ---

# 적용 스크립트의 내용이다. 단계마다 되돌리는 명령을 기록(journal)에 남기고, 실패하면 거꾸로 실행해 이번 실행이 만든 것만 지운다.
function New-YiApplyScript {
    $stage = Get-YiAnswer 'env.stage'; $install = Get-YiAnswer 'install_dir'; $data = Get-YiAnswer 'data_dir'; $rec = Get-YiAnswer 'env.record_dir'
    $svc = (Get-YiAnswer 'service.register') -eq 'true'
    $tool = Get-YiAnswer 'env.containers' 'podman'
    $s = New-Object System.Collections.Generic.List[string]
    $s.Add('# Yona 설치 적용 스크립트: yona-install이 만들었다. 관리자 권한이 필요한 단계를 모아 한 번에 실행한다.')
    $s.Add("param([string]`$Mode = 'apply')")
    $s.Add("`$ErrorActionPreference = 'Stop'")
    foreach ($kv in @(@('Stage', $stage), @('InstallDir', $install), @('DataDir', $data), @('RecordDir', $rec))) { $s.Add("`$$($kv[0]) = '" + $kv[1].Replace("'", "''") + "'") }
    $s.Add("`$Journal = Join-Path `$RecordDir 'journal.log'")
    $s.Add('function Add-Undo([string]$Cmd) { Add-Content -Path $Journal -Value $Cmd -Encoding UTF8 }')
    $s.Add('function Invoke-Rollback { if (-not (Test-Path $Journal)) { return }; $lines = Get-Content $Journal -Encoding UTF8; [array]::Reverse($lines); foreach ($c in $lines) { try { Invoke-Expression $c } catch { } }; Move-Item $Journal "$Journal.rolled-back" -Force }')
    $s.Add('if ($Mode -eq ''rollback'') { Invoke-Rollback; exit 0 }')
    $s.Add('New-Item -ItemType Directory -Force $RecordDir | Out-Null; Set-Content -Path $Journal -Value @() -Encoding UTF8')
    $s.Add('try {')
    if ((Get-YiAnswer 'db.install') -eq 'together' -and (Get-YiAnswer 'db.method') -eq 'container') {
        $type = Get-YiAnswer 'db.type'; $port = Get-YiAnswer 'db.port' (Get-YiDbDefaultPort $type)
        $image = switch ($type) { 'postgresql' { 'postgres:16' } 'mariadb' { 'mariadb:12.3.3' } 'mssql' { 'mcr.microsoft.com/mssql/server:2022-latest' } 'cubrid' { 'cubrid/cubrid:11.4' } }
        $dataPath = switch ($type) { 'postgresql' { '/var/lib/postgresql/data' } 'mariadb' { '/var/lib/mysql' } 'mssql' { '/var/opt/mssql' } 'cubrid' { '/home/cubrid/CUBRID/databases' } }
        $s.Add("  Write-Host '[yona-install] db-container'")
        $s.Add("  & $tool run -d --name yona-db --restart unless-stopped -p 127.0.0.1:${port}:${port} --env-file `"`$Stage\db.env`" -v yona-db-data:$dataPath $image | Out-Null; if (`$LASTEXITCODE -ne 0) { throw 'db-container' }")
        $s.Add("  Add-Undo '& $tool rm -fv yona-db; & $tool volume rm yona-db-data'")
        $s.Add("  `$ready = `$false; for (`$i = 0; `$i -lt 90; `$i++) { & $tool exec yona-db sh -c 'true' 2>`$null; if (`$LASTEXITCODE -eq 0) { Start-Sleep -Seconds 5; `$ready = `$true; break }; Start-Sleep -Seconds 2 }")
        $exec = switch ($type) {
            'postgresql' { "psql -h 127.0.0.1 -U `"`$POSTGRES_USER`" -v ON_ERROR_STOP=1" }
            'mariadb' { "mariadb -uroot -p`"`$MARIADB_ROOT_PASSWORD`"" }
            'mssql' { "/opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P `"`$MSSQL_SA_PASSWORD`" -b" }
            'cubrid' { 'csql -u dba ' + (Get-YiAnswer 'db.name' 'yona') }
        }
        $s.Add("  Write-Host '[yona-install] db-sql'")
        $s.Add("  Get-Content `"`$Stage\db.sql`" -Raw | & $tool exec -i yona-db sh -c '$($exec.Replace("'", "''"))'; if (`$LASTEXITCODE -ne 0) { throw 'db-sql' }")
    }
    $s.Add("  Write-Host '[yona-install] files'")
    $s.Add('  if (-not (Test-Path $InstallDir)) { New-Item -ItemType Directory -Force $InstallDir | Out-Null; Add-Undo "Remove-Item -Recurse -Force ''$InstallDir''" }')
    $s.Add('  if (-not (Test-Path $DataDir)) { New-Item -ItemType Directory -Force $DataDir | Out-Null; Add-Undo "Remove-Item -Recurse -Force ''$DataDir''" }')
    $s.Add('  New-Item -ItemType Directory -Force (Join-Path $InstallDir ''conf''), (Join-Path $InstallDir ''bin'') | Out-Null')
    $s.Add('  Copy-Item (Join-Path $Stage ''yona.jar'') (Join-Path $InstallDir ''yona.jar'') -Force')
    $s.Add('  Copy-Item (Join-Path $Stage ''conf\*'') (Join-Path $InstallDir ''conf'') -Force -Recurse')
    $s.Add('  Copy-Item (Join-Path $Stage ''bin\*'') (Join-Path $InstallDir ''bin'') -Force')
    $s.Add('  foreach ($f in ''INSTALL-REPORT.md'', ''VERSION'') { if (Test-Path (Join-Path $Stage $f)) { Copy-Item (Join-Path $Stage $f) (Join-Path $InstallDir $f) -Force } }')
    $s.Add('  $sec = Join-Path $InstallDir ''conf\secrets.properties''; if (Test-Path $sec) { icacls $sec /inheritance:r /grant:r "SYSTEM:F" "Administrators:F" "NT AUTHORITY\LocalService:R" | Out-Null }')
    if ($svc) {
        $s.Add("  Write-Host '[yona-install] service'")
        $s.Add('  Copy-Item (Join-Path $Stage ''yona-service.exe'') (Join-Path $InstallDir ''yona-service.exe'') -Force')
        $s.Add('  Copy-Item (Join-Path $Stage ''yona-service.xml'') (Join-Path $InstallDir ''yona-service.xml'') -Force')
        $s.Add('  & (Join-Path $InstallDir ''yona-service.exe'') install; if ($LASTEXITCODE -ne 0) { throw ''service'' }')
        $s.Add('  Add-Undo "& ''$InstallDir\yona-service.exe'' uninstall"')
        $s.Add('  & (Join-Path $InstallDir ''yona-service.exe'') start; if ($LASTEXITCODE -ne 0) { throw ''service-start'' }')
        $s.Add('  Add-Undo "& ''$InstallDir\yona-service.exe'' stop"')
    }
    $s.Add("  Write-Host '[yona-install] record'")
    $recLines = @("version=$(Get-YiAnswer 'version')", "install_dir=$install", "data_dir=$data", "service=$(Get-YiAnswer 'service.register' 'false')", 'os=windows', "db_type=$(Get-YiAnswer 'db.type')", "db_install=$(Get-YiAnswer 'db.install')", "db_method=$(Get-YiAnswer 'db.method')", "ssh_mode=$(Get-YiAnswer 'ssh.mode' 'none')")
    $s.Add('  Set-Content -Path (Join-Path $RecordDir ''install.rec'') -Encoding UTF8 -Value @(' + (($recLines | ForEach-Object { "'" + $_.Replace("'", "''") + "'" }) -join ', ') + ')')
    $s.Add("  Write-Host '[yona-install] done'")
    $s.Add('} catch {')
    $s.Add('  Write-Error "[yona-install] FAILED: $_"')
    $s.Add('  if ($env:YI_KEEP_ON_FAILURE -ne ''1'') { Invoke-Rollback }')
    $s.Add('  exit 1')
    $s.Add('}')
    return (($s -join "`n") + "`n")
}

# --- 흐름 ---

function Get-YiRemoteText([string]$Url) {
    if ($Url -like 'file://*') { return [System.IO.File]::ReadAllText(([uri]$Url).LocalPath, [System.Text.Encoding]::UTF8) }
    $h = @{ Accept = 'application/vnd.github+json' }
    return (Invoke-WebRequest -Uri $Url -Headers $h -UseBasicParsing).Content
}

function Save-YiRemoteFile([string]$Url, [string]$Path) {
    if ($Url -like 'file://*') { Copy-Item ([uri]$Url).LocalPath $Path -Force; return }
    if (Test-Path $Url) { Copy-Item $Url $Path -Force; return }
    Invoke-WebRequest -Uri $Url -OutFile $Path -UseBasicParsing
}

function Get-YiReleasesUrl { if ($env:YONA_RELEASES_URL) { return $env:YONA_RELEASES_URL } return 'https://api.github.com/repos/yona-projects/yona/releases?per_page=30' }

function Get-YiCandidates([string]$ScriptDir) {
    $local = @(Get-YiLocalReleases $ScriptDir); $online = @()
    if ((Get-YiAnswer 'offline') -ne 'true') {
        try { $online = @(Get-YiReleases (Get-YiRemoteText (Get-YiReleasesUrl)) 1000) }
        catch { if ($local.Count -eq 0) { throw (Get-YiMessage 'err.network') }; Write-YiLine (Get-YiMessage 'info.offline_auto') }
    }
    $seen = @{}; $all = @()
    foreach ($c in ($local + $online)) { if (-not $seen.ContainsKey($c.Tag)) { $seen[$c.Tag] = $true; $all += $c } }
    $all = @($all | Sort-Object -Property Key -Descending -CaseSensitive)
    if ($all.Count -eq 0) { throw (Get-YiMessage 'err.no_release') }
    return $all
}

function Select-YiRelease($Candidates) {
    if (Test-YiAnswer 'version') {
        $want = Get-YiAnswer 'version'; if (-not $want.StartsWith('v')) { $want = "v$want"; Set-YiAnswer 'version' $want }
        $hit = $Candidates | Where-Object { $_.Tag -eq $want } | Select-Object -First 1
        if (-not $hit) { throw "NOTFOUND:$want" }
    } else {
        $top = @($Candidates | Select-Object -First 4)
        foreach ($c in $top) { Set-YiMessage "choice.version.$($c.Tag)" $c.Label }
        Ask-YiChoice 'version' 'prompt.version' @($top | ForEach-Object Tag) $top[0].Tag
        if (-not (Test-YiAnswer 'version')) { return $null }
        $hit = $Candidates | Where-Object { $_.Tag -eq (Get-YiAnswer 'version') } | Select-Object -First 1
    }
    Set-YiAnswer 'env.jar_url' $hit.Url; Set-YiAnswer 'env.jar_size' $hit.Size; Set-YiAnswer 'env.jar_digest' $hit.Digest
    return $hit
}

function Save-YiJar {
    $dest = Join-Path (Get-YiAnswer 'env.stage') 'yona.jar'
    Write-YiLine (Get-YiMessage 'info.downloading' @((Get-YiAnswer 'env.jar_url')))
    Save-YiRemoteFile (Get-YiAnswer 'env.jar_url') $dest
    switch (Test-YiDigest $dest (Get-YiAnswer 'env.jar_digest')) {
        0 { Write-YiLine (Get-YiMessage 'info.verified') }
        2 { Write-YiLine (Get-YiMessage 'warn.not_verified') }
        default { throw (Get-YiMessage 'err.digest_mismatch') }
    }
}

# 준비 폴더에 설정, 서비스 설정, SQL, 스크립트를 만든다(비밀 값이 있어 끝나면 지운다).
function New-YiStage([string]$ScriptDir) {
    $stage = Join-Path ([System.IO.Path]::GetTempPath()) ('yona-install-stage-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory $stage, (Join-Path $stage 'conf'), (Join-Path $stage 'bin') | Out-Null
    Set-YiAnswer 'env.stage' $stage
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $stage 'conf/application.yml'), (New-YiApplicationYml), $utf8)
    $sec = New-YiSecretsProperties; if ($sec) { [System.IO.File]::WriteAllText((Join-Path $stage 'conf/secrets.properties'), $sec, $utf8) }
    foreach ($n in 'start', 'stop', 'status') { [System.IO.File]::WriteAllText((Join-Path $stage "bin/yona-$n.ps1"), (New-YiRunScript $n), $utf8) }
    if ((Get-YiAnswer 'service.register') -eq 'true') {
        $winsw = @((Join-Path $ScriptDir 'yona-service.exe'), (Join-Path $ScriptDir 'WinSW-x64.exe')) | Where-Object { Test-Path $_ } | Select-Object -First 1
        if (-not $winsw) { throw (Get-YiMessage 'err.winsw' @((Join-Path $ScriptDir 'WinSW-x64.exe'))) }
        Copy-Item $winsw (Join-Path $stage 'yona-service.exe'); [System.IO.File]::WriteAllText((Join-Path $stage 'yona-service.xml'), (New-YiWinSwXml), $utf8)
    }
    if ((Get-YiAnswer 'db.install') -eq 'together') {
        [System.IO.File]::WriteAllText((Join-Path $stage 'db.sql'), (New-YiDbSql '%'), $utf8)
        [System.IO.File]::WriteAllText((Join-Path $stage 'db.env'), (New-YiDbEnv), $utf8)
    }
    [System.IO.File]::WriteAllText((Join-Path $stage 'INSTALL-REPORT.md'), (New-YiReport), $utf8)
    [System.IO.File]::WriteAllText((Join-Path $stage 'VERSION'), (Get-YiAnswer 'version') + "`n", $utf8)
}

function Remove-YiStage {
    foreach ($d in @((Get-YiAnswer 'env.stage'), $script:YiSecretDir)) { if ($d -and (Test-Path $d)) { Remove-Item $d -Recurse -Force -ErrorAction SilentlyContinue } }
}

# 스크립트를 실행한다. 관리자 권한이 필요한데 관리자가 아니면 UAC로 올려 한 번에 실행한다.
function Invoke-YiScript([string]$Script, [string[]]$ScriptArgs = @()) {
    $keep = if ((Get-YiAnswer 'keep_on_failure') -eq 'true') { '1' } else { '0' }
    $env:YI_KEEP_ON_FAILURE = $keep
    $psExe = if ($PSVersionTable.PSEdition -eq 'Core') { (Get-Process -Id $PID).Path } else { 'powershell' }
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Script) + $ScriptArgs
    if ((Test-YiNeedsPrivilege) -and -not (Test-YiAdministrator) -and $script:OnWindows) {
        $p = Start-Process -FilePath $psExe -ArgumentList $argList -Verb RunAs -Wait -PassThru
        return $p.ExitCode
    }
    & $psExe @argList
    return $LASTEXITCODE
}

function Wait-YiHealth([int]$Limit = 300) {
    $port = Get-YiAnswer 'web.port' '8080'; $waited = 0
    while ($waited -lt $Limit) {
        try { $r = Invoke-RestMethod -Uri "http://127.0.0.1:$port/-_-actuator/health" -TimeoutSec 3; if ($r.status -eq 'UP') { return $true } } catch { }
        Start-Sleep -Seconds 2; $waited += 2
    }
    return $false
}

function Write-YiValidation([string[]]$Lines) {
    foreach ($l in $Lines) { $p = $l.Split('|'); Write-YiError (Get-YiMessage "err.$($p[0])" @($p[1], $p[2])) }
}

function Write-YiPreflight([string[]]$Lines) {
    foreach ($l in $Lines) { $p = $l.Split('|', 3); if ($p[0] -eq 'FAIL') { Write-YiError (Get-YiMessage "err.pf.$($p[1])" @($p[2])) } else { Write-YiLine (Get-YiMessage "warn.pf.$($p[1])" @($p[2])) } }
}

# 신규 설치 흐름이다.
function Invoke-YiInstall([string]$ScriptDir) {
    Invoke-YiQuestions
    Convert-YiBooleans
    $v = @(Test-YiAnswers); if ($v.Count -gt 0) { Write-YiValidation $v; return 2 }
    $missing = @(Get-YiRequired (Get-YiAnswer 'env.mode' 'install'))
    if ($missing.Count -gt 0) { Write-YiError (Get-YiMessage 'err.missing_header'); foreach ($m in $missing) { Write-YiError "  - $m" }; return 2 }
    $pf = @(Invoke-YiPreflight)
    if ($pf.Count -gt 0) { Write-YiPreflight $pf }
    if (@($pf | Where-Object { $_.StartsWith('FAIL|') }).Count -gt 0) { Write-YiError (Get-YiMessage 'err.preflight_failed'); return 1 }
    $recDir = if ((Get-YiAnswer 'service.register') -eq 'true' -or (Test-YiNeedsPrivilege)) { Get-YiSystemRecordDir } else { Get-YiUserRecordDir }
    Set-YiAnswer 'env.record_dir' $recDir
    Write-YiLine ''; Write-YiLine (Get-YiMessage 'plan.header'); foreach ($l in (Format-YiPlan)) { Write-YiLine $l }
    $priv = @(Get-YiPrivilegedSteps)
    if ($priv.Count -gt 0) { Write-YiLine ''; Write-YiLine (Get-YiMessage 'plan.sudo_header'); foreach ($id in $priv) { Write-YiLine ("  - $(Get-YiMessage "step.$id") ($id)") } }
    if ((Get-YiAnswer 'dry_run') -eq 'true') { Write-YiLine ''; Write-YiLine (Get-YiMessage 'plan.dry_run_done'); return 0 }
    if (Test-YiInteractive) { Ask-YiYesNo 'confirm.proceed' 'prompt.proceed' $true; if ((Get-YiAnswer 'confirm.proceed') -ne 'true') { Write-YiLine (Get-YiMessage 'info.cancelled'); return 0 } }
    try {
        New-YiStage $ScriptDir; Save-YiJar
        $apply = Join-Path (Get-YiAnswer 'env.stage') 'apply.ps1'
        [System.IO.File]::WriteAllText($apply, (New-YiApplyScript), (New-Object System.Text.UTF8Encoding($true)))
        Write-YiLine ''; Write-YiLine (Get-YiMessage 'info.applying')
        if ((Invoke-YiScript $apply) -ne 0) { Write-YiError (Get-YiMessage 'err.apply_failed'); return 1 }
        if ((Get-YiAnswer 'service.register') -ne 'true') { & (Join-Path (Get-YiAnswer 'install_dir') 'bin/yona-start.ps1') }
        Write-YiLine (Get-YiMessage 'info.waiting_health')
        $limit = if ($env:YI_HEALTH_TIMEOUT) { [int]$env:YI_HEALTH_TIMEOUT } else { 300 }
        if (-not (Wait-YiHealth $limit)) {
            Write-YiError (Get-YiMessage 'err.health_failed')
            if ((Get-YiAnswer 'keep_on_failure') -ne 'true') { Invoke-YiScript $apply @('-Mode', 'rollback') | Out-Null }
            return 1
        }
        Write-YiLine ''; Write-YiLine (Get-YiMessage 'info.done'); Write-YiLine (Get-YiMessage 'info.report_at' @((Join-Path (Get-YiAnswer 'install_dir') 'INSTALL-REPORT.md')))
        return 0
    } catch { Write-YiError "$_"; return 1 } finally { Remove-YiStage }
}

# 진입점이다. 사용법: Invoke-YiMain <인자 배열> <스크립트 폴더>
function Invoke-YiMain([string[]]$Arguments, [string]$ScriptDir) {
    $cmd = 'install'
    if ($Arguments.Count -gt 0 -and $Arguments[0] -in 'install', 'uninstall', 'help') { $cmd = $Arguments[0]; $Arguments = @($Arguments | Select-Object -Skip 1) }
    if ($cmd -eq 'help') { Import-YiMessages 'en'; Write-YiLine (Get-YiMessage 'usage.text'); return 0 }
    try { Import-YiAnswers $Arguments } catch { Write-YiError "$_"; return 2 }
    $lang = Resolve-YiLanguage (Get-YiAnswer 'lang') '' (Get-YiSystemLocale)
    if (-not $lang) { Write-YiError 'unsupported language'; return 2 }
    Import-YiMessages $lang; Write-YiLine (Get-YiMessage 'lang.using' @($lang))
    Convert-YiBooleans
    $v = @(Test-YiAnswers); if ($v.Count -gt 0) { Write-YiValidation $v; return 2 }
    Initialize-YiEnvironment
    $installed = ''; $rec = Find-YiRecord; if ($rec) { $installed = Read-YiRecordValue $rec 'version' }
    if ($cmd -eq 'uninstall') { return (Invoke-YiUninstall $rec) }
    try { $cands = Get-YiCandidates $ScriptDir } catch { Write-YiError "$_"; return 1 }
    try { $hit = Select-YiRelease $cands } catch { if ("$_" -like 'NOTFOUND:*') { Write-YiError (Get-YiMessage 'err.version_not_found' @(("$_").Substring(9))); return 2 }; throw }
    if (-not $hit) { Write-YiError (Get-YiMessage 'err.missing_header'); Write-YiError '  - version'; return 2 }
    if ((Get-YiAnswer 'download_only') -eq 'true') { return (Invoke-YiDownloadOnly $ScriptDir) }
    switch (Get-YiInstallState $installed (Get-YiAnswer 'version')) {
        'same' { Write-YiLine (Get-YiMessage 'info.already_installed' @($installed)); return 0 }
        'upgrade' { return (Invoke-YiUpgrade $rec $installed) }
        'downgrade' { if ((Get-YiAnswer 'allow_downgrade') -ne 'true') { Write-YiError (Get-YiMessage 'err.downgrade' @($installed, (Get-YiAnswer 'version'))); return 2 }; return (Invoke-YiUpgrade $rec $installed) }
    }
    Ask-YiChoice 'mode' 'prompt.mode' @('install', 'migrate') 'install' $true
    Set-YiAnswer 'env.mode' (Get-YiAnswer 'mode' 'install')
    # 이관 스크립트(migrate-v16*.sh)는 셸 스크립트라 Windows에서는 직접 실행할 수 없다.
    if ((Get-YiAnswer 'env.mode') -eq 'migrate') { Write-YiError (Get-YiMessage 'err.migrate_windows'); return 2 }
    return (Invoke-YiInstall $ScriptDir)
}

function Invoke-YiDownloadOnly([string]$ScriptDir) {
    $jar = Join-Path $ScriptDir ('yona-' + (Get-YiAnswer 'version').TrimStart('v') + '.jar')
    Write-YiLine (Get-YiMessage 'info.downloading' @((Get-YiAnswer 'env.jar_url')))
    Save-YiRemoteFile (Get-YiAnswer 'env.jar_url') $jar
    $r = Test-YiDigest $jar (Get-YiAnswer 'env.jar_digest')
    if ($r -eq 1) { Remove-Item $jar -Force; Write-YiError (Get-YiMessage 'err.digest_mismatch'); return 1 }
    if ($r -eq 0) { Write-YiLine (Get-YiMessage 'info.verified') } else { Write-YiLine (Get-YiMessage 'warn.not_verified') }
    "$(Get-YiSha256 $jar)  $(Split-Path $jar -Leaf)" | Set-Content "$jar.sha256" -Encoding ASCII
    Write-YiLine (Get-YiMessage 'info.bundle_ready' @($ScriptDir)); return 0
}

# 업그레이드: 설치된 다른 버전을 새 jar로 바꾼다. 설정과 데이터는 건드리지 않는다.
function Invoke-YiUpgrade([string]$Rec, [string]$Installed) {
    Set-YiAnswer 'env.mode' 'upgrade'
    foreach ($k in @(@('install_dir', 'install_dir'), @('data_dir', 'data_dir'), @('service.register', 'service'), @('db.type', 'db_type'))) { Set-YiAnswer $k[0] (Read-YiRecordValue $Rec $k[1]) }
    Write-YiLine (Get-YiMessage 'upgrade.found' @($Installed, (Get-YiAnswer 'version')))
    if ((Get-YiUpgradeNeedsConfirm $Installed (Get-YiAnswer 'version')) -eq 'yes') {
        Ask-YiYesNo 'confirm.prerelease' 'prompt.prerelease' $false
        if ((Get-YiAnswer 'confirm.prerelease') -ne 'true') { Write-YiError (Get-YiMessage 'err.prerelease_unconfirmed'); return 2 }
    }
    Ask-YiYesNo 'confirm.backup' 'prompt.backup' $false
    $m = @(Get-YiRequired 'upgrade'); if ($m.Count -gt 0) { Write-YiError (Get-YiMessage 'err.missing_header'); foreach ($x in $m) { Write-YiError "  - $x" }; return 2 }
    if ((Get-YiAnswer 'confirm.backup') -ne 'true') { Write-YiError (Get-YiMessage 'err.backup_unconfirmed'); return 2 }
    Write-YiLine ''; Write-YiLine (Get-YiMessage 'plan.header'); foreach ($l in (Format-YiPlan)) { Write-YiLine $l }
    if ((Get-YiAnswer 'dry_run') -eq 'true') { Write-YiLine ''; Write-YiLine (Get-YiMessage 'plan.dry_run_done'); return 0 }
    # 실제 교체는 서비스를 멈추고 jar를 바꾸고 다시 시작한 뒤 상태를 확인한다(실패하면 옛 jar로 복구).
    try {
        $stage = Join-Path ([System.IO.Path]::GetTempPath()) ('yona-install-stage-' + [guid]::NewGuid().ToString('N')); New-Item -ItemType Directory $stage | Out-Null; Set-YiAnswer 'env.stage' $stage
        Save-YiJar
        $install = Get-YiAnswer 'install_dir'; $old = $Installed; $svc = (Get-YiAnswer 'service.register') -eq 'true'
        $stop = if ($svc) { 'Stop-Service yona' } else { "& '$install\bin\yona-stop.ps1'" }; $start = if ($svc) { 'Start-Service yona' } else { "& '$install\bin\yona-start.ps1'" }
        $script = @"
`$ErrorActionPreference = 'Stop'
$stop
Copy-Item '$install\yona.jar' '$install\yona-$old.jar.bak' -Force
Copy-Item '$stage\yona.jar' '$install\yona.jar' -Force
$start
"@
        $f = Join-Path $stage 'upgrade.ps1'; [System.IO.File]::WriteAllText($f, $script, (New-Object System.Text.UTF8Encoding($true)))
        if ((Invoke-YiScript $f) -ne 0) { Write-YiError (Get-YiMessage 'err.upgrade_failed'); return 1 }
        if (-not (Wait-YiHealth 300)) {
            Write-YiError (Get-YiMessage 'err.upgrade_failed')
            $restore = @"
$stop
Copy-Item '$install\yona-$old.jar.bak' '$install\yona.jar' -Force
$start
"@
            $rf = Join-Path $stage 'restore.ps1'; [System.IO.File]::WriteAllText($rf, $restore, (New-Object System.Text.UTF8Encoding($true))); Invoke-YiScript $rf | Out-Null
            return 1
        }
        (Get-YiAnswer 'version') | Set-Content (Join-Path $install 'VERSION') -Encoding ASCII
        $lines = Get-Content $Rec | ForEach-Object { if ($_.StartsWith('version=')) { 'version=' + (Get-YiAnswer 'version') } else { $_ } }; Set-Content $Rec $lines -Encoding UTF8
        Write-YiLine (Get-YiMessage 'info.upgraded' @((Get-YiAnswer 'version'))); return 0
    } catch { Write-YiError "$_"; return 1 } finally { Remove-YiStage }
}

# 제거: 서비스를 끄고 설치한 파일을 지운다. --purge이면 데이터 폴더와 컨테이너 DB도 지운다.
function Invoke-YiUninstall([string]$Rec) {
    if (-not $Rec) { Write-YiError (Get-YiMessage 'err.not_installed'); return 1 }
    $install = Read-YiRecordValue $Rec 'install_dir'; $data = Read-YiRecordValue $Rec 'data_dir'; $svc = (Read-YiRecordValue $Rec 'service') -eq 'true'
    Write-YiLine (Get-YiMessage 'uninstall.target' @($install))
    if ((Get-YiAnswer 'purge') -eq 'true') {
        Write-YiLine (Get-YiMessage 'uninstall.purge_warning' @($data))
        if (Test-YiInteractive) { Ask-YiText 'confirm.purge_path' 'prompt.purge_confirm' ''; if ((Get-YiAnswer 'confirm.purge_path') -ne $data) { Write-YiError (Get-YiMessage 'err.purge_unconfirmed'); return 2 } }
        elseif ((Get-YiAnswer 'yes') -ne 'true') { Write-YiError (Get-YiMessage 'err.purge_unconfirmed'); return 2 }
    }
    if ((Get-YiAnswer 'dry_run') -eq 'true') { Write-YiLine (Get-YiMessage 'plan.dry_run_done'); return 0 }
    $s = New-Object System.Collections.Generic.List[string]
    if ($svc) { $s.Add("& '$install\yona-service.exe' stop; & '$install\yona-service.exe' uninstall") } else { $s.Add("& '$install\bin\yona-stop.ps1'") }
    foreach ($f in 'yona.jar', 'VERSION', 'INSTALL-REPORT.md', 'yona-service.exe', 'yona-service.xml') { $s.Add("Remove-Item -Force -ErrorAction SilentlyContinue '$install\$f'") }
    $s.Add("Remove-Item -Recurse -Force -ErrorAction SilentlyContinue '$install\conf', '$install\bin'")
    if ((Get-YiAnswer 'purge') -eq 'true') {
        $s.Add("Remove-Item -Recurse -Force -ErrorAction SilentlyContinue '$data'")
        if ((Read-YiRecordValue $Rec 'db_method') -eq 'container') { foreach ($t in 'podman', 'docker') { $s.Add("if (Get-Command $t -ErrorAction SilentlyContinue) { & $t rm -fv yona-db; & $t volume rm yona-db-data }") } }
    }
    $s.Add("Remove-Item -Force -ErrorAction SilentlyContinue '$Rec'")
    $f = Join-Path ([System.IO.Path]::GetTempPath()) ('yona-uninstall-' + [guid]::NewGuid().ToString('N') + '.ps1')
    [System.IO.File]::WriteAllText($f, ($s -join "`n"), (New-Object System.Text.UTF8Encoding($true)))
    Set-YiAnswer 'install_dir' $install; Set-YiAnswer 'data_dir' $data; Set-YiAnswer 'service.register' $(if ($svc) { 'true' } else { 'false' })
    $rc = Invoke-YiScript $f; Remove-Item $f -ErrorAction SilentlyContinue
    if ($rc -ne 0) { return 1 }
    Write-YiLine (Get-YiMessage 'uninstall.done'); return 0
}
