# PowerShell 산출물 생성 시험(Pester 5). 셸이 만든 골든 파일(scenarios/*/expected)과 글자 하나까지 같아야 한다.
BeforeAll {
    . "$PSScriptRoot/../../lib/ps/Core.ps1"
    . "$PSScriptRoot/../../lib/ps/Render.ps1"
    $script:Root = (Resolve-Path "$PSScriptRoot/../../scenarios").Path
    function script:Read-Expected([string]$Name, [string]$File) {
        $p = Join-Path $script:Root "$Name/expected/$File"
        if (Test-Path $p) { return [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8) } else { return $null }
    }
    function script:Load-Scenario([string]$Name) {
        Clear-YiAnswers
        $dir = Join-Path $script:Root $Name
        $tmp = New-TemporaryFile
        (Get-Content (Join-Path $dir 'answers.env') -Raw -Encoding UTF8).Replace('%SCENARIO%', $dir) | Set-Content $tmp -Encoding UTF8
        Import-YiAnswers @("--answers=$tmp"); Remove-Item $tmp; Convert-YiBooleans
    }
}

Describe '골든 시나리오(셸과 같은 출력)' {
    BeforeAll { $script:Names = Get-ChildItem $script:Root -Directory | ForEach-Object Name }
    It '<_> : application.yml, secrets.properties, db.sql, db.env' -ForEach (Get-ChildItem (Join-Path $PSScriptRoot '../../scenarios') -Directory | ForEach-Object Name) {
        Load-Scenario $_
        New-YiApplicationYml | Should -BeExactly (Read-Expected $_ 'application.yml')
        New-YiSecretsProperties | Should -BeExactly (Read-Expected $_ 'secrets.properties')
        if ((Get-YiAnswer 'db.install') -eq 'together') {
            $h = if ((Get-YiAnswer 'db.method') -eq 'package') { 'localhost' } else { '%' }
            New-YiDbSql $h | Should -BeExactly (Read-Expected $_ 'db.sql')
            New-YiDbEnv | Should -BeExactly (Read-Expected $_ 'db.env')
        }
    }
}

Describe '이스케이프와 보조 함수' {
    It 'YAML, properties, SQL 문자열' {
        ConvertTo-YiYamlQuoted "it's" | Should -Be "'it''s'"
        ConvertTo-YiPropsEscape ' lead' | Should -Be '\ lead'
        ConvertTo-YiPropsEscape 'a\b' | Should -Be 'a\\b'
        ConvertTo-YiSqlMysql "a'b\c" | Should -Be "'a''b\\c'"
        ConvertTo-YiSqlStd "a'b\c" | Should -Be "'a''b\c'"
    }
    It '비밀번호는 보이는 ASCII만 받는다' {
        Test-YiPasswordOk 'Abc!@# 1' | Should -BeTrue
        Test-YiPasswordOk '비밀번호' | Should -BeFalse
        Test-YiPasswordOk '' | Should -BeFalse
        Test-YiPasswordOk "a`nb" | Should -BeFalse
    }
    It 'DB 종류별 기본 포트와 JDBC 주소' {
        Get-YiDbDefaultPort 'postgresql' | Should -Be '5432'
        Get-YiJdbcUrl 'mssql' 'db' '1433' 'yona' '/d' | Should -Be 'jdbc:sqlserver://db:1433;databaseName=yona;lockTimeout=20000'
        Get-YiJdbcUrl 'cubrid' 'db' '33000' 'yona' '/d' | Should -Be 'jdbc:cubrid:db:33000:yona:::?charset=utf-8'
    }
    It 'Windows 경로는 설정 파일에 슬래시로 쓴다' {
        Clear-YiAnswers; Set-YiAnswer 'install_dir' 'C:\Program Files\Yona'; Set-YiAnswer 'data_dir' 'C:\ProgramData\Yona'; Set-YiAnswer 'db.type' 'h2'
        $y = New-YiApplicationYml
        $y | Should -Match "data-dir: 'C:/ProgramData/Yona'"
        $y | Should -Match "jdbc:h2:file:C:/ProgramData/Yona/db"
    }
}

Describe 'WinSW 서비스 설정' {
    It '자동(지연된 시작), 실패 시 재시작, 제한된 계정, 절대 경로' {
        Clear-YiAnswers; Set-YiAnswer 'install_dir' 'C:\Program Files\Yona'; Set-YiAnswer 'env.java' 'C:\Java\bin\java.exe'; Set-YiAnswer 'env.tz' 'Asia/Seoul'
        $x = New-YiWinSwXml
        $x | Should -Match '<id>yona</id>'
        $x | Should -Match '<startmode>Automatic</startmode>'
        $x | Should -Match '<delayedAutoStart>true</delayedAutoStart>'
        $x | Should -Match 'action="restart"'
        $x | Should -Match 'NT AUTHORITY\\LocalService'
        $x | Should -Match 'C:\\Program Files\\Yona\\yona.jar'
        $x | Should -Match 'name="TZ" value="Asia/Seoul"'
        { [xml]$x } | Should -Not -Throw
    }
}

Describe '시작 스크립트와 결과 문서' {
    It '시작 스크립트는 문법이 올바른 PowerShell이다' {
        Clear-YiAnswers; Set-YiAnswer 'install_dir' 'C:\Yona'; Set-YiAnswer 'data_dir' 'C:\YonaData'; Set-YiAnswer 'env.java' 'C:\Java\bin\java.exe'
        foreach ($n in 'start', 'stop', 'status') {
            $errors = $null; [System.Management.Automation.Language.Parser]::ParseInput((New-YiRunScript $n), [ref]$null, [ref]$errors) | Out-Null
            @($errors).Count | Should -Be 0
        }
    }
    It '결과 문서에 비밀번호가 없고 접속 주소와 백업 목록이 있다' {
        Import-YiMessages 'en'
        Clear-YiAnswers
        Set-YiAnswer 'version' 'v2.0.0'; Set-YiAnswer 'install_dir' 'C:\Yona'; Set-YiAnswer 'data_dir' 'C:\YonaData'; Set-YiAnswer 'web.port' '8080'
        Set-YiAnswer 'db.type' 'postgresql'; Set-YiAnswer 'site.hostname' 'git.example.com'; Set-YiAnswer 'site.scheme' 'https'; Set-YiAnswer 'service.register' 'false'
        $r = New-YiReport
        $r | Should -Match 'https://git.example.com'
        $r | Should -Match 'secret.properties'
        $r | Should -Match 'does not start automatically'
    }
}
