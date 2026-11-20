# Yona 설치 프로그램 (X-63)

Yona v2를 Linux, macOS(셸)와 Windows(PowerShell)에 설치, 업그레이드, 제거하고 v1.6에서 이관한다. 설계와 결정은 위키의
[`roadmap.md`](../yona-v2-wiki/roadmap.md) X-63 구간과 [`architecture/install-scripts.md`](../yona-v2-wiki/architecture/install-scripts.md)에 있다.

## 한 줄 설치

```bash
# Linux, macOS: 일반 사용자로 시작하고 관리자 권한이 필요한 단계에서만 sudo로 올린다
curl -fsSL https://github.com/yona-projects/yona/releases/latest/download/bootstrap.sh | bash
# 받아서 읽어 보고 실행하려면
curl -fsSLO https://github.com/yona-projects/yona/releases/latest/download/bootstrap.sh && less bootstrap.sh && bash bootstrap.sh
```

```powershell
# Windows(PowerShell): 관리자 권한이 필요한 단계에서만 UAC로 올린다
irm https://github.com/yona-projects/yona/releases/latest/download/bootstrap.ps1 | iex
```

## 직접 실행

```bash
./yona-install.sh help
./yona-install.sh install --dry-run        # 계획만 보인다(아무것도 설치하지 않는다)
./yona-install.sh install --yes --version=v2.0.0 --service=true --db=postgresql --db-install=together \
  --db-method=container --db-password-file=/root/db.pw --db-admin-password-file=/root/admin.pw \
  --install-dir=/opt/yona --data-dir=/var/lib/yona
./yona-install.sh uninstall [--purge]
```

모든 질문의 답은 옵션, 답 파일(`--answers=파일`), 환경 변수(`YONA_INSTALL_<키>`)로 미리 줄 수 있다(비대화형: `--yes`).
비밀번호는 명령줄이 아니라 소유자만 읽는 파일로 준다. 필요한 답이 모자라면 아무것도 설치하지 않고 목록을 보인다.

## 폐쇄 환경

인터넷이 되는 곳에서 `yona-install.sh install --download-only --version=v2.0.0`으로 jar와 `.sha256`을 이 폴더에 받는다.
이 폴더째 서버로 옮겨 같은 명령(`--offline`)을 실행한다. JDK와 DB 설치 파일은 받지 않는다.

## 구성

| 경로 | 내용 |
|---|---|
| `yona-install.sh`, `lib/*.sh` | 셸 구현(bash 3.2 호환) |
| `yona-install.ps1`, `lib/ps/*.ps1` | PowerShell 구현(5.1 이상) |
| `messages/{ko,en}.properties` | 두 구현이 함께 읽는 메시지 |
| `templates/` | 설치 폴더에 복사하는 도우미(`wait-for-port.sh`) |
| `bootstrap-*.sh`, `bootstrap.ps1`, `build-release.sh` | 한 줄 설치와 릴리스 묶음 만들기 |
| `scenarios/` | 셸과 PowerShell이 같은 산출물을 만드는지 견주는 공통 골든 파일 |
| `test/` | 시험(bats, Pester, 통합 시험) |

## 시험

```bash
# 한 번에: shellcheck, bats, bash 3.2 호환, Pester. YONA_JAR를 주면 컨테이너 통합 시험도 돈다.
BATS_BIN=/path/to/bats YONA_JAR=/path/to/yona.jar install/test/run-all.sh
install/test/integration/db-container.sh     # 컨테이너 DB(PostgreSQL, MariaDB 12.3.3) 실제 설치와 되돌리기
```

PowerShell 시험은 `yona-pester` 이미지(Pester 5 이상이 든 `mcr.microsoft.com/powershell`)가 필요하다
(`docker commit`으로 만든다. `test/run-all.sh`가 없으면 건너뛴다). Windows 전용 동작(관리자 확인, UAC, WinSW 서비스)은
Linux의 pwsh로 시험할 수 없다.
