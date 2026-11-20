# PowerShell 흐름 시험(Pester 5). Windows 전용 동작(관리자 확인, UAC, WinSW)은 시험하지 않고, 어디서나 같은 부분(질문, 검증, 계획, 점검, dry-run)을 본다.
BeforeAll {
    $script:Here = (Resolve-Path "$PSScriptRoot/../..").Path
    $env:YI_MESSAGES_DIR = Join-Path $script:Here 'messages'
    . "$script:Here/lib/ps/Core.ps1"; . "$script:Here/lib/ps/Render.ps1"; . "$script:Here/lib/ps/Flow.ps1"
}

Describe 'PowerShell 흐름' {
    BeforeEach {
        Clear-YiAnswers; Import-YiMessages 'en'
        $script:Tmp = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString('N')); New-Item -ItemType Directory $script:Tmp | Out-Null
        $env:YI_INPUT = $null; $script:YiInputLines = $null
    }
    AfterEach { Remove-Item $script:Tmp -Recurse -Force -ErrorAction SilentlyContinue; $env:YI_INPUT = $null }

    Context '계획' {
        It 'H2와 서비스 없음이면 관리자 단계가 없다' {
            Set-YiAnswer 'service.register' 'false'; Set-YiAnswer 'install_dir' "$script:Tmp/i"; Set-YiAnswer 'data_dir' "$script:Tmp/d"; Set-YiAnswer 'db.type' 'h2'
            ((Get-YiPlanSteps) | ForEach-Object { $_.Split('|')[0] }) -join ' ' | Should -Be 'preflight fetch stage files start health report'
            @(Get-YiPrivilegedSteps).Count | Should -Be 0
        }
        It '서비스를 등록하면 files와 service가 관리자 단계이다' {
            Set-YiAnswer 'service.register' 'true'; Set-YiAnswer 'install_dir' "$script:Tmp/i"; Set-YiAnswer 'data_dir' "$script:Tmp/d"
            (Get-YiPrivilegedSteps) -join ' ' | Should -Be 'files service'
        }
        It '업그레이드 계획' {
            Set-YiAnswer 'env.mode' 'upgrade'; Set-YiAnswer 'service.register' 'true'
            ((Get-YiPlanSteps) | ForEach-Object { $_.Split('|')[0] }) -join ' ' | Should -Be 'preflight fetch backup-confirm replace restart health report'
        }
        It '관리자 단계에 [admin] 표시' { Set-YiAnswer 'service.register' 'true'; Set-YiAnswer 'install_dir' "$script:Tmp/i"; Set-YiAnswer 'data_dir' "$script:Tmp/d"; ((Format-YiPlan) -join "`n") | Should -Match '\[admin\]' }
    }

    Context '점검' {
        It 'java -version 문자열에서 주 버전을 읽는다' {
            Get-YiJavaMajor 'openjdk version "21.0.11" 2026-04-21' | Should -Be 21
            Get-YiJavaMajor 'openjdk version "17.0.15" 2025-04-15' | Should -Be 17
            Get-YiJavaMajor 'java version "1.8.0_492"' | Should -Be 8
        }
        It '비밀번호 파일이 없거나 내용이 올바르지 않으면 실패한다' {
            Set-YiAnswer 'db.type' 'postgresql'; Set-YiAnswer 'db.password_file' "$script:Tmp/none"
            (Test-YiSecretFiles) -join ' ' | Should -Match 'FAIL\|secret-file\|db.password_file'
            [System.IO.File]::WriteAllText("$script:Tmp/pw", "비밀번호`n"); Set-YiAnswer 'db.password_file' "$script:Tmp/pw"
            (Test-YiSecretFiles) -join ' ' | Should -Match 'secret-file-content'
            [System.IO.File]::WriteAllText("$script:Tmp/pw", "good-pw`n")
            @(Test-YiSecretFiles).Count | Should -Be 0
        }
        It '열려 있는 포트는 사용 중으로 본다' {
            $l = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0); $l.Start()
            try { Test-YiPortInUse $l.LocalEndpoint.Port | Should -BeTrue } finally { $l.Stop() }
        }
        It '컨테이너 DB인데 도구가 없으면 실패한다' {
            $env:YI_NO_CONTAINER = '1'; Set-YiAnswer 'db.install' 'together'
            try { (Test-YiContainerTool) -join ' ' | Should -Match 'FAIL\|container' } finally { $env:YI_NO_CONTAINER = $null }
        }
    }

    Context '질문' {
        It '선택은 번호나 값을 받고 텍스트는 비우면 기본값을 쓴다' {
            "2`n`ntyped" | Set-Content "$script:Tmp/in" -Encoding UTF8; $env:YI_INPUT = "$script:Tmp/in"
            Ask-YiChoice 'db.install' 'prompt.db_install' @('together', 'separate') 'together'
            Ask-YiText 'a.key' 'prompt.db_name' 'def'
            Ask-YiText 'b.key' 'prompt.db_name' 'def'
            Get-YiAnswer 'db.install' | Should -Be 'separate'; Get-YiAnswer 'a.key' | Should -Be 'def'; Get-YiAnswer 'b.key' | Should -Be 'typed'
        }
        It '비대화형이면 질문하지 않고 optional만 기본값을 쓴다' {
            Set-YiAnswer 'yes' 'true'
            Ask-YiText 'opt' 'prompt.db_name' 'def' $true; Ask-YiText 'req' 'prompt.db_name' 'def'
            Get-YiAnswer 'opt' | Should -Be 'def'; Get-YiAnswer 'req' | Should -Be ''
        }
        It '비밀번호는 두 번 같게 입력하면 파일로 저장한다' {
            "S3cret!`nS3cret!" | Set-Content "$script:Tmp/in" -Encoding UTF8; $env:YI_INPUT = "$script:Tmp/in"
            Ask-YiSecret 'db.password_file' 'prompt.db_password'
            (Get-Content (Get-YiAnswer 'db.password_file') -Raw).Trim() | Should -Be 'S3cret!'
            Remove-Item (Split-Path (Get-YiAnswer 'db.password_file')) -Recurse -Force
        }
    }

    Context '적용 스크립트' {
        It '생성한 스크립트는 올바른 PowerShell이고 단계와 되돌리기 기록이 있다' {
            Set-YiAnswer 'env.stage' 'C:\stage'; Set-YiAnswer 'env.record_dir' 'C:\rec'; Set-YiAnswer 'install_dir' 'C:\Yona'; Set-YiAnswer 'data_dir' 'C:\YonaData'
            Set-YiAnswer 'version' 'v2.0.0'; Set-YiAnswer 'service.register' 'true'; Set-YiAnswer 'db.type' 'postgresql'; Set-YiAnswer 'db.install' 'together'; Set-YiAnswer 'db.method' 'container'; Set-YiAnswer 'env.containers' 'podman'
            $s = New-YiApplyScript
            $errors = $null; [System.Management.Automation.Language.Parser]::ParseInput($s, [ref]$null, [ref]$errors) | Out-Null
            @($errors).Count | Should -Be 0
            $s | Should -Match 'podman run -d --name yona-db --restart unless-stopped -p 127.0.0.1:5432:5432 --env-file'
            $s | Should -Match "yona-service.exe'\) install"
            $s | Should -Match 'Add-Undo'
            $s | Should -Match 'version=v2.0.0'
        }
    }

    Context '진입점(--dry-run)' {
        BeforeEach {
            [System.IO.File]::WriteAllText("$script:Tmp/yona-2.0.0.jar", 'JAR')
            $env:YI_SYSTEM_RECORD_DIR = "$script:Tmp/sysrec"
            # 이 컴퓨터의 JDK와 상관없이 21이 있는 것으로 시험한다(리눅스의 pwsh에서만 쓸 수 있는 가짜 실행 파일)
            New-Item -ItemType Directory "$script:Tmp/jdk/bin" -Force | Out-Null
            [System.IO.File]::WriteAllText("$script:Tmp/jdk/bin/java", "#!/bin/sh`necho 'openjdk version `"21.0.2`" 2026-01-01' >&2`n")
            chmod +x "$script:Tmp/jdk/bin/java"
            $env:JAVA_HOME = "$script:Tmp/jdk"; $env:YI_NO_PATH_JAVA = '1'
            $script:YiLog.Clear()
        }
        AfterEach { $env:YI_SYSTEM_RECORD_DIR = $null; $env:JAVA_HOME = $null; $env:YI_NO_PATH_JAVA = $null }
        It 'H2, 서비스 없음 dry-run은 계획을 보이고 0으로 끝난다' {
            $code = Invoke-YiMain @('install', '--yes', '--dry-run', '--offline', '--version=v2.0.0', "--install-dir=$script:Tmp/y", "--data-dir=$script:Tmp/yd", '--service=false', '--db=h2', '--port=58991', '--lang=en') $script:Tmp 6>$null
            @($code)[-1] | Should -Be 0
            ($script:YiLog -join "`n") | Should -Match 'Plan:'
            ($script:YiLog -join "`n") | Should -Match 'nothing was installed'
            Test-Path "$script:Tmp/y" | Should -BeFalse
        }
        It '필요한 답이 모자라면 2로 끝난다' {
            $code = Invoke-YiMain @('install', '--yes', '--dry-run', '--offline', '--version=v2.0.0', '--lang=en') $script:Tmp 2>$null 6>$null
            @($code)[-1] | Should -Be 2
        }
        It '잘못된 값과 알 수 없는 옵션은 2로 끝난다' {
            @(Invoke-YiMain @('install', '--yes', '--db=oracle', '--lang=en') $script:Tmp 2>$null 6>$null)[-1] | Should -Be 2
            @(Invoke-YiMain @('install', '--nope') $script:Tmp 2>$null 6>$null)[-1] | Should -Be 2
        }
        It '같은 버전이 이미 설치되어 있으면 알리고 끝낸다' {
            New-Item -ItemType Directory $env:YI_SYSTEM_RECORD_DIR | Out-Null
            "version=v2.0.0`ninstall_dir=C:\Yona`ndata_dir=C:\D`nservice=true`nos=windows" | Set-Content "$env:YI_SYSTEM_RECORD_DIR/install.rec"
            Invoke-YiMain @('install', '--yes', '--offline', '--version=v2.0.0', '--lang=en') $script:Tmp 6>$null | Out-Null
            ($script:YiLog -join "`n") | Should -Match 'already installed'
        }
        It '다른 높은 버전이면 업그레이드이고 백업 확인이 없으면 2로 끝난다' {
            [System.IO.File]::WriteAllText("$script:Tmp/yona-2.1.0.jar", 'JAR2')
            New-Item -ItemType Directory $env:YI_SYSTEM_RECORD_DIR | Out-Null
            "version=v2.0.0`ninstall_dir=C:\Yona`ndata_dir=C:\D`nservice=true`nos=windows" | Set-Content "$env:YI_SYSTEM_RECORD_DIR/install.rec"
            $code = @(Invoke-YiMain @('install', '--yes', '--offline', '--version=v2.1.0', '--lang=en') $script:Tmp 6>$null 2>$null)
            ($script:YiLog -join "`n") | Should -Match 'Upgrading the installed v2.0.0 to v2.1.0'
            $code[-1] | Should -Be 2
        }
        It 'v1.6 이관은 Windows에서 지원하지 않는다고 안내하고 2로 끝난다' {
            $code = @(Invoke-YiMain @('install', '--yes', '--offline', '--version=v2.0.0', '--mode=migrate', '--lang=en') $script:Tmp 6>$null 2>$null)
            ($script:YiLog -join "`n") | Should -Match 'cannot run directly on Windows'
            $code[-1] | Should -Be 2
        }
        It '설치 기록이 없으면 제거는 1로 끝난다' { @(Invoke-YiMain @('uninstall', '--yes', '--lang=en') $script:Tmp 2>$null 6>$null)[-1] | Should -Be 1 }
        It '한국어 시스템이면 한국어로 출력한다' {
            $env:YI_LOCALE = 'ko-KR'
            try { Invoke-YiMain @('install', '--yes', '--dry-run', '--offline', '--version=v2.0.0', "--install-dir=$script:Tmp/y", "--data-dir=$script:Tmp/yd", '--service=false', '--db=h2', '--port=58992') $script:Tmp 6>$null | Out-Null; ($script:YiLog -join "`n") | Should -Match '실행 계획' } finally { $env:YI_LOCALE = $null }
        }
        It '--download-only는 jar와 sha256 파일을 만든다' {
            $json = '[{"tag_name":"v2.3.0","name":"v2.3.0","draft":false,"prerelease":false,"assets":[{"name":"yona-2.3.0.jar","size":3,"digest":"sha256:' + ((Get-FileHash "$script:Tmp/yona-2.0.0.jar" -Algorithm SHA256).Hash.ToLower()) + '","browser_download_url":"file://' + "$script:Tmp/yona-2.0.0.jar" + '"}],"tarball_url":"x"}]'
            [System.IO.File]::WriteAllText("$script:Tmp/rel.json", $json); $env:YONA_RELEASES_URL = "file://$script:Tmp/rel.json"
            $b = Join-Path $script:Tmp 'bundle'; New-Item -ItemType Directory $b | Out-Null
            try { $c = Invoke-YiMain @('install', '--download-only', '--version=v2.3.0', '--lang=en') $b 6>&1; Test-Path "$b/yona-2.3.0.jar" | Should -BeTrue; Test-Path "$b/yona-2.3.0.jar.sha256" | Should -BeTrue } finally { $env:YONA_RELEASES_URL = $null }
        }
    }
}
