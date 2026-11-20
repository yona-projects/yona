# Yona 설치 프로그램(Windows, PowerShell)의 산출물 생성: 설정 파일, 비밀 값 파일, SQL, 서비스 설정, 시작 스크립트, 결과 문서.
# 셸의 lib/render.sh와 같은 입력에서 같은 설정 파일과 SQL을 만든다(scenarios/ 골든 파일로 견준다). 줄바꿈은 항상 LF이다.
Set-StrictMode -Version 2.0

$script:YiHeapPercent = 75

function ConvertTo-YiYamlQuoted([string]$Value) { return "'" + $Value.Replace("'", "''") + "'" }
function ConvertTo-YiPropsEscape([string]$Value) {
    $v = $Value.Replace('\', '\\')
    if ($v.StartsWith(' ')) { $v = '\ ' + $v.Substring(1) }
    return $v
}
function ConvertTo-YiSqlMysql([string]$Value) { return "'" + $Value.Replace('\', '\\').Replace("'", "''") + "'" }
function ConvertTo-YiSqlStd([string]$Value) { return "'" + $Value.Replace("'", "''") + "'" }

# 비밀번호가 보이는 ASCII 문자(공백 포함)만으로 이루어졌으면 참이다.
function Test-YiPasswordOk([string]$Value) { return ($Value -ne '' -and $Value -cmatch '^[ -~]+$') }

# 비밀 값 파일을 읽는다(끝의 개행은 지운다).
function Read-YiSecretFile([string]$Path) { return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8).TrimEnd("`r", "`n") }

function Get-YiDbDefaultPort([string]$Type) {
    switch ($Type) { 'postgresql' { '5432' } 'mariadb' { '3306' } 'mssql' { '1433' } 'cubrid' { '33000' } default { '0' } }
}

# Windows 경로의 역슬래시를 슬래시로 바꾼다(스프링의 file: 주소와 YAML에서 안전하다).
function ConvertTo-YiSlashPath([string]$Path) { return $Path.Replace('\', '/') }

function Get-YiJdbcUrl([string]$Type, [string]$HostName, [string]$Port, [string]$Name, [string]$DataDir) {
    switch ($Type) {
        'postgresql' { "jdbc:postgresql://${HostName}:${Port}/$Name" }
        'mariadb' { "jdbc:mariadb://${HostName}:${Port}/$Name" }
        'mssql' { "jdbc:sqlserver://${HostName}:${Port};databaseName=$Name;lockTimeout=20000" }
        'cubrid' { "jdbc:cubrid:${HostName}:${Port}:${Name}:::?charset=utf-8" }
        'h2' { "jdbc:h2:file:$DataDir/db;DB_CLOSE_DELAY=-1" }
    }
}

function Get-YiActiveJdbcUrl {
    $type = Get-YiAnswer 'db.type'
    $port = Get-YiAnswer 'db.port' (Get-YiDbDefaultPort $type)
    if ((Get-YiAnswer 'db.install') -eq 'separate') { $dbHost = Get-YiAnswer 'db.host' } else { $dbHost = '127.0.0.1' }
    return Get-YiJdbcUrl $type $dbHost $port (Get-YiAnswer 'db.name' 'yona') (ConvertTo-YiSlashPath (Get-YiAnswer 'data_dir'))
}

function Test-YiNeedsSecrets {
    return ((Get-YiAnswer 'db.type') -ne 'h2') -or (Test-YiAnswer 'mail.password_file') -or ((Get-YiAnswer 'ssh.mode') -eq 'system')
}

# conf/application.yml의 내용이다(셸의 render_application_yml과 같다).
function New-YiApplicationYml {
    $l = New-Object System.Collections.Generic.List[string]
    $type = Get-YiAnswer 'db.type'
    $user = if ($type -eq 'h2') { 'sa' } else { Get-YiAnswer 'db.user' 'yona' }
    $install = ConvertTo-YiSlashPath (Get-YiAnswer 'install_dir')
    $l.Add('# Yona 설정: 설치 스크립트가 만들었다. 비밀 값은 secrets.properties에 있다.')
    $l.Add('spring:')
    if (Test-YiNeedsSecrets) { $l.Add('  config:'); $l.Add('    import: ' + (ConvertTo-YiYamlQuoted "optional:file:$install/conf/secrets.properties")) }
    $l.Add('  datasource:'); $l.Add('    url: ' + (ConvertTo-YiYamlQuoted (Get-YiActiveJdbcUrl))); $l.Add('    username: ' + (ConvertTo-YiYamlQuoted $user))
    if ((Get-YiAnswer 'mail.enabled') -eq 'true') {
        $l.Add('  mail:'); $l.Add('    host: ' + (ConvertTo-YiYamlQuoted (Get-YiAnswer 'mail.host'))); $l.Add('    port: ' + (Get-YiAnswer 'mail.port'))
        if (Test-YiAnswer 'mail.user') { $l.Add('    username: ' + (ConvertTo-YiYamlQuoted (Get-YiAnswer 'mail.user'))) }
        $l.Add('    properties:'); $l.Add('      mail.smtp.ssl.enable: ' + (Get-YiAnswer 'mail.ssl' 'false'))
    }
    $l.Add('server:'); $l.Add('  port: ' + (Get-YiAnswer 'web.port' '8080'))
    if ((Get-YiAnswer 'site.behind_https_proxy') -eq 'true') { $l.Add('  forward-headers-strategy: framework') }
    $l.Add('yona:'); $l.Add('  data-dir: ' + (ConvertTo-YiYamlQuoted (ConvertTo-YiSlashPath (Get-YiAnswer 'data_dir'))))
    if (Test-YiAnswer 'site.hostname') { $l.Add('  hostname: ' + (ConvertTo-YiYamlQuoted (Get-YiAnswer 'site.hostname'))) }
    if (Test-YiAnswer 'site.port') { $l.Add('  port: ' + (Get-YiAnswer 'site.port')) }
    if (Test-YiAnswer 'site.scheme') { $l.Add('  scheme: ' + (ConvertTo-YiYamlQuoted (Get-YiAnswer 'site.scheme'))) }
    if ((Get-YiAnswer 'mail.enabled') -eq 'true') {
        $l.Add('  smtp:')
        if (Test-YiAnswer 'mail.from_user') { $l.Add('    user: ' + (ConvertTo-YiYamlQuoted (Get-YiAnswer 'mail.from_user'))) }
        if (Test-YiAnswer 'mail.from_domain') { $l.Add('    domain: ' + (ConvertTo-YiYamlQuoted (Get-YiAnswer 'mail.from_domain'))) }
        $l.Add('    mock: ' + (Get-YiAnswer 'mail.mock' 'false'))
    }
    switch (Get-YiAnswer 'ssh.mode') {
        'builtin' {
            $l.Add('  ssh:'); $l.Add('    mina:'); $l.Add('      enabled: true'); $l.Add('      port: ' + (Get-YiAnswer 'ssh.port' '2222'))
            if (Test-YiAnswer 'ssh.public_port') { $l.Add('    public-port: ' + (Get-YiAnswer 'ssh.public_port')) }
        }
        'system' {
            $l.Add('  ssh:'); $l.Add('    relay:'); $l.Add('      enabled: true')
            if (Test-YiAnswer 'ssh.public_port') { $l.Add('    public-port: ' + (Get-YiAnswer 'ssh.public_port')) }
        }
    }
    return (($l -join "`n") + "`n")
}

# conf/secrets.properties의 내용이다. 소유자만 읽도록 만들어야 한다.
function New-YiSecretsProperties {
    $l = New-Object System.Collections.Generic.List[string]
    if ((Get-YiAnswer 'db.type') -ne 'h2' -and (Test-YiAnswer 'db.password_file')) { $l.Add('spring.datasource.password=' + (ConvertTo-YiPropsEscape (Read-YiSecretFile (Get-YiAnswer 'db.password_file')))) }
    if ((Get-YiAnswer 'mail.enabled') -eq 'true' -and (Test-YiAnswer 'mail.password_file')) { $l.Add('spring.mail.password=' + (ConvertTo-YiPropsEscape (Read-YiSecretFile (Get-YiAnswer 'mail.password_file')))) }
    if ((Get-YiAnswer 'ssh.mode') -eq 'system' -and (Test-YiAnswer 'ssh.relay_secret')) { $l.Add('yona.ssh.relay.internal-secret=' + (ConvertTo-YiPropsEscape (Get-YiAnswer 'ssh.relay_secret'))) }
    if ($l.Count -eq 0) { return '' }
    return (($l -join "`n") + "`n")
}

# 데이터베이스와 사용자를 만드는 SQL이다. 인자는 사용자의 접속 호스트(MariaDB만 쓴다).
function New-YiDbSql([string]$UserHost) {
    $name = Get-YiAnswer 'db.name' 'yona'; $user = Get-YiAnswer 'db.user' 'yona'
    $pw = ''; if (Test-YiAnswer 'db.password_file') { $pw = Read-YiSecretFile (Get-YiAnswer 'db.password_file') }
    $l = New-Object System.Collections.Generic.List[string]
    switch (Get-YiAnswer 'db.type') {
        'mariadb' {
            $l.Add("CREATE DATABASE ``$name`` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin;")
            $l.Add('CREATE USER ' + (ConvertTo-YiSqlMysql $user) + '@' + (ConvertTo-YiSqlMysql $UserHost) + ' IDENTIFIED BY ' + (ConvertTo-YiSqlMysql $pw) + ';')
            $l.Add("GRANT ALL PRIVILEGES ON ``$name``.* TO " + (ConvertTo-YiSqlMysql $user) + '@' + (ConvertTo-YiSqlMysql $UserHost) + ';')
            $l.Add('FLUSH PRIVILEGES;')
        }
        'postgresql' {
            $l.Add("CREATE ROLE `"$user`" LOGIN PASSWORD " + (ConvertTo-YiSqlStd $pw) + ';')
            $l.Add("CREATE DATABASE `"$name`" OWNER `"$user`" ENCODING 'UTF8' TEMPLATE template0;")
        }
        'mssql' {
            $l.Add("CREATE DATABASE [$name];"); $l.Add("ALTER DATABASE [$name] SET READ_COMMITTED_SNAPSHOT ON;")
            $l.Add("CREATE LOGIN [$user] WITH PASSWORD = " + (ConvertTo-YiSqlStd $pw) + ', CHECK_POLICY = OFF;')
            $l.Add("USE [$name];"); $l.Add("CREATE USER [$user] FOR LOGIN [$user];"); $l.Add("ALTER ROLE db_owner ADD MEMBER [$user];")
        }
        'cubrid' { $l.Add("create user $user password " + (ConvertTo-YiSqlStd $pw) + ' groups dba;') }
    }
    if ($l.Count -eq 0) { return '' }
    return (($l -join "`n") + "`n")
}

# 설치를 되돌릴 때 데이터베이스와 사용자를 지우는 SQL이다.
function New-YiDbDropSql([string]$UserHost) {
    $name = Get-YiAnswer 'db.name' 'yona'; $user = Get-YiAnswer 'db.user' 'yona'
    switch (Get-YiAnswer 'db.type') {
        'mariadb' { return "DROP DATABASE IF EXISTS ``$name``;`nDROP USER IF EXISTS " + (ConvertTo-YiSqlMysql $user) + '@' + (ConvertTo-YiSqlMysql $UserHost) + ";`n" }
        'postgresql' { return "DROP DATABASE IF EXISTS `"$name`";`nDROP ROLE IF EXISTS `"$user`";`n" }
        default { return '' }
    }
}

# 컨테이너 DB를 시작할 때 --env-file로 넘기는 환경 파일의 내용이다. 소유자만 읽도록 만들어야 한다.
function New-YiDbEnv {
    $adminUser = Get-YiAnswer 'db.admin_user'
    $pw = ''; if (Test-YiAnswer 'db.admin_password_file') { $pw = Read-YiSecretFile (Get-YiAnswer 'db.admin_password_file') }
    switch (Get-YiAnswer 'db.type') {
        'postgresql' { if (-not $adminUser) { $adminUser = 'postgres' }; return "POSTGRES_USER=$adminUser`nPOSTGRES_PASSWORD=$pw`n" }
        'mariadb' { return "MARIADB_ROOT_PASSWORD=$pw`n" }
        'mssql' { return "ACCEPT_EULA=Y`nMSSQL_SA_PASSWORD=$pw`n" }
        'cubrid' { return "CUBRID_LOCALE=en_US.utf8`nCUBRID_DB=" + (Get-YiAnswer 'db.name' 'yona') + "`n" }
        default { return '' }
    }
}

# 패키지로 설치한 DB의 관리자 비밀번호를 설정하는 SQL이다(Windows는 컨테이너만 쓰므로 거의 쓰지 않는다).
function New-YiDbAdminSql {
    if (-not (Test-YiAnswer 'db.admin_password_file')) { return '' }
    $pw = Read-YiSecretFile (Get-YiAnswer 'db.admin_password_file')
    switch (Get-YiAnswer 'db.type') {
        'postgresql' { return 'ALTER ROLE "' + (Get-YiAnswer 'db.admin_user' 'postgres') + '" PASSWORD ' + (ConvertTo-YiSqlStd $pw) + ";`n" }
        'mariadb' { return "ALTER USER 'root'@'localhost' IDENTIFIED VIA mysql_native_password USING PASSWORD(" + (ConvertTo-YiSqlMysql $pw) + ") OR unix_socket;`n" }
        default { return '' }
    }
}

# --- Windows 서비스(WinSW) ---

function ConvertTo-YiXml([string]$Value) { return [System.Security.SecurityElement]::Escape($Value) }

# WinSW의 서비스 설정(yona-service.xml)이다. 부팅 때 자동(지연된 시작)으로 시작하고 실패하면 다시 시작한다.
# 서비스 계정은 기본 LocalSystem이 아니라 제한된 계정(LocalService)으로 둔다(에이전트 판단, Windows에서 확인하지 못함).
function New-YiWinSwXml {
    $install = Get-YiAnswer 'install_dir'
    $java = Get-YiAnswer 'env.java' 'java'
    $conf = (ConvertTo-YiSlashPath $install) + '/conf/'
    $args = "-XX:MaxRAMPercentage=$script:YiHeapPercent -jar `"$install\yona.jar`" --spring.config.additional-location=file:$conf"
    $x = New-Object System.Collections.Generic.List[string]
    $x.Add('<service>'); $x.Add('  <id>yona</id>'); $x.Add('  <name>Yona</name>'); $x.Add('  <description>Yona (git and code review server)</description>')
    $x.Add('  <executable>' + (ConvertTo-YiXml $java) + '</executable>'); $x.Add('  <arguments>' + (ConvertTo-YiXml $args) + '</arguments>')
    $x.Add('  <workingdirectory>' + (ConvertTo-YiXml $install) + '</workingdirectory>')
    if (Test-YiAnswer 'env.tz') { $x.Add('  <env name="TZ" value="' + (ConvertTo-YiXml (Get-YiAnswer 'env.tz')) + '"/>') }
    $x.Add('  <startmode>Automatic</startmode>'); $x.Add('  <delayedAutoStart>true</delayedAutoStart>')
    $x.Add('  <onfailure action="restart" delay="10 sec"/>'); $x.Add('  <stoptimeout>60 sec</stoptimeout>')
    $x.Add('  <serviceaccount><username>NT AUTHORITY\LocalService</username></serviceaccount>')
    $x.Add('  <log mode="roll"/>'); $x.Add('</service>')
    return (($x -join "`n") + "`n")
}

# --- 서비스 없이 쓰는 시작, 중지, 상태 스크립트(PowerShell) ---
function New-YiRunScript([string]$Name) {
    $install = Get-YiAnswer 'install_dir'; $data = Get-YiAnswer 'data_dir'; $java = Get-YiAnswer 'env.java' 'java'; $port = Get-YiAnswer 'web.port' '8080'
    $conf = (ConvertTo-YiSlashPath $install) + '/conf/'
    switch ($Name) {
        'start' {
            return @"
# Yona를 백그라운드로 시작한다. 부팅 때 자동으로 시작하지 않는다.
`$pidFile = '$data\yona.pid'
if (Test-Path `$pidFile) { if (Get-Process -Id ([int](Get-Content `$pidFile)) -ErrorAction SilentlyContinue) { Write-Host "Yona is already running"; exit 0 } }
New-Item -ItemType Directory -Force '$data\logs' | Out-Null
`$p = Start-Process -FilePath '$java' -ArgumentList @('-XX:MaxRAMPercentage=$script:YiHeapPercent', '-jar', '$install\yona.jar', '--spring.config.additional-location=file:$conf') -WorkingDirectory '$install' -WindowStyle Hidden -RedirectStandardOutput '$data\logs\yona.out' -RedirectStandardError '$data\logs\yona.err' -PassThru
`$p.Id | Set-Content `$pidFile
Write-Host "Yona started (pid `$(`$p.Id))"

"@
        }
        'stop' {
            return @"
# Yona를 종료한다.
`$pidFile = '$data\yona.pid'
if (-not (Test-Path `$pidFile)) { Write-Host "Yona is not running"; exit 0 }
Stop-Process -Id ([int](Get-Content `$pidFile)) -ErrorAction SilentlyContinue
Remove-Item `$pidFile -ErrorAction SilentlyContinue
Write-Host "Yona stopped"

"@
        }
        'status' { return "# Yona의 상태를 확인한다.`nInvoke-RestMethod -Uri 'http://127.0.0.1:$port/-_-actuator/health'`n" }
    }
}

# 설치 결과 문서(INSTALL-REPORT.md)의 내용이다. 비밀 값은 쓰지 않는다.
function New-YiReport {
    $scheme = Get-YiAnswer 'site.scheme' 'http'; $hostName = Get-YiAnswer 'site.hostname' 'localhost'
    if (Test-YiAnswer 'site.port') { $url = "${scheme}://${hostName}:" + (Get-YiAnswer 'site.port') }
    elseif ($scheme -eq 'https') { $url = "${scheme}://$hostName" } else { $url = "${scheme}://${hostName}:" + (Get-YiAnswer 'web.port' '8080') }
    $data = Get-YiAnswer 'data_dir'; $web = Get-YiAnswer 'web.port' '8080'
    $l = New-Object System.Collections.Generic.List[string]
    $l.Add('# ' + (Get-YiMessage 'report.title')); $l.Add('')
    $l.Add((Get-YiMessage 'report.version' @((Get-YiAnswer 'version')))); $l.Add('')
    $l.Add('## ' + (Get-YiMessage 'report.access')); $l.Add('')
    $l.Add('- ' + (Get-YiMessage 'report.url' @($url))); $l.Add('- ' + (Get-YiMessage 'report.web_port' @($web)))
    if ((Get-YiAnswer 'ssh.mode') -eq 'builtin') { $l.Add('- ' + (Get-YiMessage 'report.ssh_builtin' @((Get-YiAnswer 'ssh.port' '2222')))) }
    $l.Add(''); $l.Add('## ' + (Get-YiMessage 'report.locations')); $l.Add('')
    $l.Add('- ' + (Get-YiMessage 'report.install_dir' @((Get-YiAnswer 'install_dir')))); $l.Add('- ' + (Get-YiMessage 'report.data_dir' @($data)))
    $l.Add('- ' + (Get-YiMessage 'report.database' @(((Get-YiAnswer 'db.type') + ' / ' + (Get-YiAnswer 'db.name' 'yona')))))
    $l.Add(''); $l.Add('## ' + (Get-YiMessage 'report.commands')); $l.Add('')
    if ((Get-YiAnswer 'service.register') -eq 'true') { $l.Add('- Get-Service yona / Restart-Service yona') }
    else { $l.Add('- ' + (Get-YiMessage 'report.cmd_scripts' @((Get-YiAnswer 'install_dir')))); $l.Add('- ' + (Get-YiMessage 'report.no_autostart')) }
    $l.Add('- ' + (Get-YiMessage 'report.health' @($web)))
    $l.Add(''); $l.Add('## ' + (Get-YiMessage 'report.backup')); $l.Add('')
    foreach ($k in 'report.backup_secret', 'report.backup_ssh', 'report.backup_repo') { $l.Add('- ' + (Get-YiMessage $k @($data))) }
    if ((Get-YiAnswer 'db.type') -ne 'h2') { $l.Add('- ' + (Get-YiMessage 'report.backup_db')) }
    $l.Add(''); $l.Add('## ' + (Get-YiMessage 'report.next')); $l.Add('')
    $l.Add('- ' + (Get-YiMessage 'report.next_admin')); $l.Add('- ' + (Get-YiMessage 'report.firewall' @($web)))
    return (($l -join "`n") + "`n")
}
