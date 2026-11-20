# PowerShell 공통 기능 시험(Pester 5). 셸 시험(answers, releases, messages)과 같은 입력에서 같은 결과가 나와야 한다.
BeforeAll {
    . "$PSScriptRoot/../../lib/ps/Core.ps1"
    $script:Fixtures = Join-Path $PSScriptRoot '../fixtures'
}
Context 'Yona 설치 공통 기능' {
BeforeEach { Clear-YiAnswers; $env:YI_MESSAGES_DIR = $null }

Context '답 읽기' {
    It '옵션은 --키=값과 --키 값 두 모양을 받는다' {
        Import-YiAnswers @('--db=postgresql', '--port', '9090')
        Get-YiAnswer 'db.type' | Should -Be 'postgresql'
        Get-YiAnswer 'web.port' | Should -Be '9090'
    }
    It '값이 없는 옵션은 true가 된다' {
        Import-YiAnswers @('--yes', '--dry-run', '--non-interactive')
        Get-YiAnswer 'yes' | Should -Be 'true'
        Get-YiAnswer 'dry_run' | Should -Be 'true'
    }
    It '알 수 없는 옵션은 예외이다' { { Import-YiAnswers @('--nope=1') } | Should -Throw '*--nope*' }
    It '환경 변수, 답 파일, 옵션 순으로 우선한다' {
        $f = New-TemporaryFile
        "web.port=2000`ndb.type=mariadb" | Set-Content $f -Encoding UTF8
        $env:YONA_INSTALL_WEB_PORT = '1000'; $env:YONA_INSTALL_DB_TYPE = 'h2'; $env:YONA_INSTALL_LANG = 'en'
        try {
            Import-YiAnswers @("--answers=$f", '--port=3000')
            Get-YiAnswer 'web.port' | Should -Be '3000'
            Get-YiAnswer 'db.type' | Should -Be 'mariadb'
            Get-YiAnswer 'lang' | Should -Be 'en'
        } finally { Remove-Item Env:YONA_INSTALL_WEB_PORT, Env:YONA_INSTALL_DB_TYPE, Env:YONA_INSTALL_LANG -ErrorAction SilentlyContinue; Remove-Item $f }
    }
}

Context '검증' {
    It '올바른 답은 통과한다' {
        Import-YiAnswers @('--db=postgresql', '--db-install=separate', '--db-port=5432', '--db-name=yona', '--port=8080', '--scheme=https', '--ssh=builtin', '--ssh-port=2222', '--version=v2.0.0')
        Test-YiAnswers | Should -BeNullOrEmpty
    }
    It '오류를 모두 모아 알린다' {
        Import-YiAnswers @('--db=oracle', '--port=70000', '--scheme=ftp', '--db-name=Bad-Name')
        $e = (Test-YiAnswers) -join "`n"
        $e | Should -Match 'db.type'; $e | Should -Match 'web.port'; $e | Should -Match 'site.scheme'; $e | Should -Match 'db.name'
    }
    It '포트는 1부터 65535까지의 숫자만 받는다' {
        foreach ($bad in '0', '65536', 'abc', '-1', '8.5') { Clear-YiAnswers; Set-YiAnswer 'web.port' $bad; @(Test-YiAnswers).Count | Should -BeGreaterThan 0 }
        foreach ($good in '1', '80', '8080', '65535') { Clear-YiAnswers; Set-YiAnswer 'web.port' $good; Test-YiAnswers | Should -BeNullOrEmpty }
    }
    It '웹 포트와 내장 SSH 포트가 같으면 충돌이다' {
        Import-YiAnswers @('--port=2222', '--ssh=builtin', '--ssh-port=2222')
        ((Test-YiAnswers) -join "`n") | Should -Match 'ssh.port'
    }
    It '불리언을 true/false로 맞춘다' {
        Set-YiAnswer 'service.register' 'yes'; Convert-YiBooleans; Get-YiAnswer 'service.register' | Should -Be 'true'
        Set-YiAnswer 'service.register' 'NO'; Convert-YiBooleans; Get-YiAnswer 'service.register' | Should -Be 'false'
    }
}

Context '필수 항목' {
    It 'H2는 버전, 폴더, 서비스 여부, DB 종류만 필요하다' {
        Import-YiAnswers @('--db=h2', '--version=v2.0.0', '--install-dir=C:/y', '--data-dir=C:/d', '--service=false')
        Get-YiRequired 'install' | Should -BeNullOrEmpty
    }
    It '아무 답이 없으면 모두 알린다' {
        $m = (Get-YiRequired 'install') -join ' '
        foreach ($k in 'version', 'install_dir', 'data_dir', 'service.register', 'db.type') { $m | Should -Match ([regex]::Escape($k)) }
    }
    It '함께 설치하는 DB는 이름, 사용자, 비밀번호, 관리자 비밀번호를 요구한다' {
        Import-YiAnswers @('--db=postgresql', '--db-install=together', '--db-method=container')
        $m = (Get-YiRequired 'install') -join ' '
        foreach ($k in 'db.name', 'db.user', 'db.password_file', 'db.admin_password_file') { $m | Should -Match ([regex]::Escape($k)) }
        $m | Should -Not -Match 'db.host'
    }
    It '업그레이드는 버전과 백업 확인만 요구한다' { ((Get-YiRequired 'upgrade') -join ' ') | Should -Be 'version confirm.backup' }
}

Context '언어와 메시지' {
    It '--lang, 환경 변수, 시스템 언어, 영어 순이다' {
        Resolve-YiLanguage 'en' 'ko' 'ko-KR' | Should -Be 'en'
        Resolve-YiLanguage '' 'en' 'ko-KR' | Should -Be 'en'
        Resolve-YiLanguage '' '' 'ko-KR' | Should -Be 'ko'
        Resolve-YiLanguage '' '' 'en-US' | Should -Be 'en'
        Resolve-YiLanguage '' '' '' | Should -Be 'en'
        Resolve-YiLanguage 'fr' '' '' | Should -BeNullOrEmpty
    }
    It '실제 메시지 파일에서 한국어와 영어 문구를 읽고 {1}을 바꾼다' {
        Import-YiMessages 'ko'; Get-YiMessage 'port.in_use' @('8080') | Should -Be '8080번 포트는 이미 사용 중입니다.'
        Import-YiMessages 'en'; Get-YiMessage 'port.in_use' @('8080') | Should -Be 'Port 8080 is already in use.'
    }
    It '인자에 &나 역슬래시가 있어도 그대로 들어간다' {
        Import-YiMessages 'en'; Get-YiMessage 'port.in_use' @('a&b\c') | Should -Be 'Port a&b\c is already in use.'
    }
    It '없는 키는 키 이름을 돌려준다' { Import-YiMessages 'en'; Get-YiMessage 'no.such' | Should -Be 'no.such' }
    It '한국어와 영어 메시지 파일의 키 집합이 같다' {
        $dir = Get-YiMessagesDir
        $keys = { param($f) [System.IO.File]::ReadAllLines((Join-Path $dir $f), [System.Text.Encoding]::UTF8) | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object { $_.Split('=')[0] } | Sort-Object }
        (& $keys 'ko.properties') -join ',' | Should -Be ((& $keys 'en.properties') -join ',')
    }
}

Context '버전 비교' {
    It '큰 쪽이 이기고 사전 배포는 정식보다 낮다' {
        Compare-YiVersion '2.0.1' '2.0.0' | Should -Be 1
        Compare-YiVersion 'v2.0.0' '2.0.1' | Should -Be -1
        Compare-YiVersion 'v2.0.0' '2.0.0' | Should -Be 0
        Compare-YiVersion '2.10.0' '2.9.0' | Should -Be 1
        Compare-YiVersion '2.0.0-rc1' '2.0.0' | Should -Be -1
        Compare-YiVersion '2.0.0-rc2' '2.0.0-rc10' | Should -Be -1
        Compare-YiVersion '2.0.1-rc1' '2.0.0' | Should -Be 1
    }
    It '설치 상태와 사전 배포 확인' {
        Get-YiInstallState '' 'v2.0.0' | Should -Be 'new'
        Get-YiInstallState 'v2.0.0' 'v2.0.0' | Should -Be 'same'
        Get-YiInstallState 'v2.0.0' 'v2.1.0' | Should -Be 'upgrade'
        Get-YiInstallState 'v2.1.0' 'v2.0.0' | Should -Be 'downgrade'
        Get-YiUpgradeNeedsConfirm 'v2.0.0' 'v2.1.0-rc1' | Should -Be 'yes'
        Get-YiUpgradeNeedsConfirm 'v2.0.0-rc1' 'v2.0.0' | Should -Be 'no'
    }
}

Context '릴리스 목록' {
    It '줄바꿈이 있는 JSON과 한 줄 JSON 모두 v2 이상 4건을 높은 버전부터 고른다' {
        foreach ($name in 'releases-pretty.json', 'releases-minified.json') {
            $tags = (Get-YiReleases (Get-Content (Join-Path $script:Fixtures $name) -Raw -Encoding UTF8)).Tag -join ' '
            $tags | Should -Be 'v2.1.0 v2.0.1 v2.0.0 v2.0.0-rc2'
        }
    }
    It '표시 이름과 jar 정보가 셸과 같다' {
        $r = Get-YiReleases (Get-Content (Join-Path $script:Fixtures 'releases-pretty.json') -Raw -Encoding UTF8) 6
        ($r | Where-Object Tag -eq 'v2.0.0-rc1').Label | Should -Be 'v2.0.0-rc1 (rc)'
        ($r | Where-Object Tag -eq 'v2.0.0').Label | Should -Be 'v2.0.0'
        $first = $r[0]
        $first.Url | Should -Be 'https://github.com/yona-projects/yona/releases/download/v2.1.0/yona-2.1.0.jar'
        $first.Size | Should -Be '210'
        $first.Digest | Should -Be ('sha256:' + ('{0:x64}' -f 210))
    }
    It '실제 GitHub 응답을 읽는다' {
        $r = Get-YiReleases (Get-Content (Join-Path $script:Fixtures 'releases-real.json') -Raw -Encoding UTF8)
        $r[0].Tag | Should -Be 'v2.0.0-rc1'
        $r[0].Digest | Should -Match '^sha256:'
    }
    It 'sha256 digest를 검증한다' {
        $f = New-TemporaryFile; [System.IO.File]::WriteAllText($f.FullName, 'hello')
        Get-YiSha256 $f.FullName | Should -Be '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824'
        Test-YiDigest $f.FullName ('sha256:' + (Get-YiSha256 $f.FullName)) | Should -Be 0
        Test-YiDigest $f.FullName ('sha256:' + ('0' * 64)) | Should -Be 1
        Test-YiDigest $f.FullName '' | Should -Be 2
        Remove-Item $f
    }
    It '같은 폴더의 jar를 후보로 읽는다' {
        $d = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid()); New-Item -ItemType Directory $d | Out-Null
        [System.IO.File]::WriteAllText("$d/yona-2.0.0-rc1.jar", 'jar')
        $l = Get-YiLocalReleases $d
        $l[0].Tag | Should -Be 'v2.0.0-rc1'; $l[0].Label | Should -Be 'v2.0.0-rc1 (rc)'; $l[0].Digest | Should -Be ''
        Remove-Item $d -Recurse
    }
}
}
