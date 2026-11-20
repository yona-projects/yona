# Yona 설치 프로그램(Windows): 신규 설치, 업그레이드, v1.6 이관, 제거.
# 사용법: .\yona-install.ps1 [install|uninstall|help] [옵션...]  (옵션은 셸 버전과 같다: --db=postgresql --yes --dry-run ...)
# 일반 사용자로 시작하고, 관리자 권한이 필요한 단계가 있을 때만 UAC로 올린다.
# 같은 폴더의 lib\ps, messages, jar를 쓰므로 폐쇄 환경에서는 이 폴더째 옮겨 실행한다.
# 실행 정책 때문에 막히면 이 창에서만 `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`로 풀고 다시 실행한다(스크립트가 정책을 바꾸지는 않는다).
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:YI_MESSAGES_DIR = Join-Path $here 'messages'
. (Join-Path $here 'lib\ps\Core.ps1')
. (Join-Path $here 'lib\ps\Render.ps1')
. (Join-Path $here 'lib\ps\Flow.ps1')
exit (Invoke-YiMain @($Rest) $here)
