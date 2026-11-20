#!/usr/bin/env bash
# PowerShell 시험(Pester 5)을 Linux의 pwsh 컨테이너에서 돌린다. 이미지 `yona-pester`가 없으면 먼저 만든다(run-all.sh 참고).
# Windows 전용 동작(관리자 확인, 서비스 등록, UAC)은 이 방법으로 시험할 수 없다.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
docker run --rm -v "$ROOT:/repo:ro" -w /repo/install yona-pester pwsh -NoProfile -Command \
  "Import-Module Pester -MinimumVersion 5.5; \$c = New-PesterConfiguration; \$c.Run.Path = 'test/ps'; \$c.Run.Exit = \$true; \$c.Output.Verbosity = 'Normal'; Invoke-Pester -Configuration \$c"
