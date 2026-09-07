# SSH: 시스템 sshd 연동 (`git@host:owner/repo.git`, `hg@host:owner/repo`)

yona-wiki P3-03이 설계했지만 "호스트 시스템을 건드리지 않는다"는 그 세션의 제약 때문에 실제
등록은 못 하고 미뤄둔 부분이다(`docs/yona-wiki/plans/p3-03-ssh-gpg.md` 참고). 이 문서는 실제
운영 서버에서 시스템 관리자가 직접 등록할 수 있도록 그 절차를 정리한다.

## 이게 왜 필요한가

윈도우에서는 yona가 JVM 안에 자체 SSH 서버(Apache MINA SSHD, 기본 포트 2222)를 띄워
`ssh://git@host:2222/owner/repo.git`처럼 접속한다(`yona.ssh.mina.enabled=auto`가 OS를 보고
자동으로 켬 — 별도 설정 불필요, 이 문서와 무관).

리눅스/맥에서는 GitHub/GitLab과 똑같은 방식을 쓴다 — **`git@host:owner/repo.git`(포트 22,
시스템의 실제 OpenSSH sshd)**. 이건 yona가 자동으로 띄워주는 게 아니라, sshd가 접속자의
공개키를 볼 때마다 yona에게 "이 키가 누구 것인지" 물어보도록 **관리자가 한 번 등록**해야
동작한다. 그 등록 방법이 이 문서다.

## 전체 그림

```
                          ①공개키로 접속 시도
클라이언트 ssh/git  ─────────────────────────▶  시스템 sshd (포트 22)
                                                      │
                                                      │ ②AuthorizedKeysCommand로
                                                      │   ssh-auth.sh 실행 (%t %k)
                                                      ▼
                                              ssh-auth.sh ──③POST /internal/ssh/authenticate──▶ yona 서버
                                                      ▲                                    (127.0.0.1)
                                                      │  ④"이 사용자입니다" 회신받아
                                                      │    authorized_keys 형식 한 줄을 인쇄
                                                      │    (command=에 principal + socat 릴레이
                                                      │     한 줄을 그대로 심어둠)
                                                      ▼
                                              sshd가 인증 완료, 세션을 command= 로 강제 실행
                                                      │
                                                      ▼
                                              ⑤socat이 stdin/stdout을 유닉스 도메인 소켓에 그대로
                                                이어붙임(이미 떠 있는 yona 메인 JVM이 반대편에서
                                                인가 판정 + 실제 git/hg 프로토콜을 처리 — P3-18
                                                SshRelayServer, HTTPS 경로와 완전히 동일한
                                                브랜치 보호 로직 적용)
```

GitHub/GitLab이 쓰는 것과 동일한 패턴이다(`AuthorizedKeysCommand` + forced command). yona는
①의 신원 확인 API(`SshInternalController`, `/internal/ssh/authenticate`)만 제공하고, 나머지
인가 판정(이 저장소에 push/clone 가능한가, 브랜치 보호 등)은 ⑤의 `SshRelayServer`가 소켓
반대편에서 직접 처리한다 — 별도의 "인가 확인 후 git 바이너리 exec" 훅 스크립트는 필요 없다
(이전 버전 문서에 있던 `ssh-shell.sh`와 `/internal/ssh/authorize` 호출은 **삭제됐다** — 그
스크립트가 진짜 git 바이너리를 직접 exec해서 브랜치 보호 등 yona의 JGit 훅 체이닝을 우회하는
구조였기 때문. 지금은 `ssh-auth.sh` 하나와, 표준 도구 `socat`만 있으면 된다 — 별도
`yona-cli` 바이너리도, 커스텀 셸 스크립트도 필요 없다).

## 사전 준비

- yona 서버가 이미 이 호스트(같은 머신)에서 떠 있어야 한다. `SshInternalController`는 보안상
  루프백 주소(127.0.0.1)에서 온 요청만 받는다 — 훅 스크립트와 yona 서버가 **반드시 같은
  호스트**에 있어야 한다(컨테이너로 띄웠다면 훅 스크립트도 그 컨테이너 안에서 실행되거나,
  host network를 공유해야 한다).
- `curl`, `jq`, `socat`이 설치돼 있어야 한다(`apt install curl jq socat` / `yum install curl jq socat`).
  `socat`은 forced command가 stdin/stdout을 yona의 유닉스 도메인 소켓에 그대로 이어붙이는 데만
  쓰는 표준 도구다(대부분 배포판 기본 저장소에 있음).
- yona가 리슨하는 HTTP 포트를 확인한다. `application.yml`에 `server.port`를 별도로 지정하지
  않았다면 Spring Boot 기본값인 `8080`이다 — `server.port`를 직접 설정했다면 그 값을 쓴다
  ([settings-reference.md](settings-reference.md) 참고). 아래 예시는 `8080` 기준이다.
- yona의 `yona.ssh.relay.socket-path` 설정값(기본 `/tmp/yona/ssh-relay.sock` — **운영에서는
  `yona.git.base-dir`와 마찬가지로 반드시 영구 경로로 바꿀 것**, `/tmp`는 재부팅 시 사라지고
  다른 프로세스가 같은 이름으로 먼저 만들어버릴 수도 있다)을 확인한다. `yona.ssh.relay.enabled`는
  기본값 `true`라 별도 설정 없이 이미 켜져 있다.

## Step 1. 전용 시스템 계정 만들기

GitHub 방식과 동일하게, 사람이 아니라 **저장소 접근 전용 계정**으로 모든 접속을 받는다. 실제
사용자 구분은 OS 계정이 아니라 공개키로 한다.

**계정 이름은 VCS 종류와 무관하다 — `git`/`hg` 둘 다 만들어서 URL 관례를 맞춰주는 걸 권장한다.**
`ssh-auth.sh`(아래 Step 3)의 forced command는 어느 계정으로 접속했는지가 아니라
`SSH_ORIGINAL_COMMAND`의 내용(git 명령인지 hg 명령인지)만 보고 소켓 반대편(`SshRelayServer`)에
그대로 릴레이하므로, 계정 이름 자체는 아무 의미가 없다 — `hg clone ssh://git@host/...`도
기술적으로는 동작한다. 하지만 실제 Mercurial 관례(`hg-ssh` 설정)는 `hg`라는 별도 계정을
쓰므로, 사용자가 `git@host:owner/repo.git`과 `hg@host:owner/repo` 양쪽 다 자연스럽게 쓸 수
있도록 계정을 두 개 만들어서 Step 4의 `Match User` 블록 하나로 같이 묶는다(설정 내용은
완전히 동일 — 계정만 두 개).

```bash
sudo useradd --system --shell /bin/bash --home-dir /home/git --create-home git
sudo useradd --system --shell /bin/bash --home-dir /home/hg --create-home hg
```

(아래 예시는 `git` 계정 기준으로 쓰지만 `hg` 계정도 완전히 동일하게 설정한다 — 계정 이름만
다를 뿐 나머지 Step은 두 계정 모두에 그대로 적용된다.)

**`/usr/sbin/nologin`을 쓰면 안 된다 — 반드시 실제 셸(`/bin/bash`)을 지정할 것.** 처음엔
직관적으로 nologin을 썼었는데, 실제 컨테이너에 sshd를 띄워 forced command까지 관통시켜보고서야
발견한 문제다(2026-09-08): OpenSSH는 `command=`로 지정된 forced command를 그 계정의
로그인 셸로 `<셸> -c "<명령>"` 형태로 실행한다 — 셸이 nologin이면 `nologin -c "..."`이
되는데, `nologin`은 인자를 전부 무시하고 "This account is currently not available." 한 줄만
찍고 종료해버려 **`command=`가 있으나 마나 하게 무력화된다**(우리 forced command는 전혀
실행되지 않음). 대화형 로그인/비밀번호 접근을 막는 건 nologin이 아니라 **비밀번호 자체를
설정하지 않는 것**(`useradd`가 기본적으로 계정을 잠긴 상태로 만듦, `passwd -l`로 재확인
가능)과 `command=`+`no-pty`(forced command가 항상 강제되고, 원본 명령이 없는 경우
"대화형 셸 접속은 지원하지 않습니다"를 직접 응답하도록 이미 아래 스크립트에 들어있음)로
이미 충분히 달성된다 — 셸이 진짜 `/bin/bash`여도 이 계정으로는 비밀번호 로그인이 불가능하고
SSH도 항상 forced command로만 귀결되므로 보안상 구멍이 생기지 않는다.

**`git` 계정은 저장소 디렉터리(`yona.git.base-dir`/`yona.hg.base-dir`)에 대한 파일시스템
권한이 전혀 필요 없다** — P3-18 이전(소켓 릴레이 도입 전) 문서에는 `git` 계정을 yona 앱 계정과
같은 그룹에 넣고 **저장소 디렉터리 자체를 그 그룹에 rwX로 열어주는** 절차가 있었는데, 지금은
그럴 필요가 없다. forced command(아래 Step 3)가 하는 일은 stdin/stdout을 유닉스 도메인
소켓에 `socat`으로 이어붙이는 것뿐이고, 실제 git/hg 저장소 파일을 열고 읽고 쓰는 건 전부
**이미 떠 있는 yona JVM 프로세스 자신**(보통 `yona`라는 별도 OS 계정)이 한다 — `git` 계정은
그 파일들을 단 한 바이트도 직접 건드리지 않는다.

다만 **그룹 자체는 여전히 공유해야 한다** — 이유가 저장소 파일이 아니라 **소켓 파일**로
바뀌었을 뿐이다. `SshRelayServer`가 여는 유닉스 도메인 소켓(`yona.ssh.relay.socket-path`)은
`yona` 프로세스 소유로 그룹 rw(`rw-rw----`)만 열려 있어서(코드가 바인드 직후 명시적으로
고정한다), `git`/`hg` 계정이 그 소켓에 `connect()`하려면 여전히 `yona` 그룹의 멤버여야 한다:

```bash
# 예: yona 앱이 "yona" 계정으로 돈다고 가정 — 저장소 디렉터리엔 더 이상 chmod 안 해도 됨,
# 이 usermod 두 줄만 있으면 된다(소켓 연결 권한 전용).
sudo usermod -aG yona git
sudo usermod -aG yona hg
```

## Step 2. 공유 시크릿 준비

sshd가 호출하는 훅 스크립트는 yona 서버가 아무나의 요청을 받아주지 않도록 시크릿 헤더를
같이 보내야 한다(`SshInternalController`가 루프백 체크에 더해 이것까지 요구). 자동 생성되는
파일(`yona.data`/`ssh/internal-secret`)은 yona 앱 소유자만 읽을 수 있게 잠겨 있어(의도적 보안
조치) 다른 계정에서 실행되는 훅 스크립트가 그대로 읽을 수 없다 — 그래서 **값을 직접 정해서
yona 설정과 훅 스크립트 양쪽에 똑같이 넣어준다.**

```bash
# 1) 강한 무작위 값 생성
openssl rand -base64 32
# 예: <위에서-openssl로-생성한-값-그대로-붙여넣기>

# 2) yona 서버 설정에 추가 (application.yml 또는 환경변수)
```

```yaml
yona:
  ssh:
    internal-secret: "<위에서-openssl로-생성한-값-그대로-붙여넣기>"
```

환경변수로 줄 거면 `YONA_SSH_INTERNAL_SECRET`(Spring Boot의 relaxed binding — `yona.ssh.internal-secret`에
대응)를 쓴다.

```bash
# 3) 훅 스크립트용 파일로도 똑같은 값을 저장
sudo mkdir -p /etc/yona
echo -n "<위에서-openssl로-생성한-값-그대로-붙여넣기>" | sudo tee /etc/yona/ssh-internal-secret
sudo groupadd --system yona-ssh-hook
sudo chown root:yona-ssh-hook /etc/yona/ssh-internal-secret
sudo chmod 640 /etc/yona/ssh-internal-secret
```

이 그룹(`yona-ssh-hook`)에는 Step 4에서 만들 훅 실행 계정(`yona-ssh-hook`)만 넣으면 된다 —
이 시크릿 파일을 읽는 건 `AuthorizedKeysCommand`(`ssh-auth.sh`, `/internal/ssh/authenticate`
호출)뿐이고, `git` 계정이 실행하는 forced command(위 Step 1 참고 — 소켓에 바이트만 릴레이)는
이 파일을 전혀 읽지 않는다. **`git` 계정을 이 그룹에 넣을 필요는 없다** — Step 1에서 이미
설명한 `yona` 그룹(소켓 연결용)과 헷갈리지 말 것, 서로 다른 그룹·다른 목적이다.

## Step 3. 훅 스크립트 설치

`/usr/local/lib/yona/ssh-auth.sh` 하나만 있으면 된다(①→④, `AuthorizedKeysCommand`가 실행) —
인가 판정과 git/hg 프로토콜 처리는 전부 `SshRelayServer`(이미 떠 있는 yona 메인 JVM)가
소켓 반대편에서 직접 하므로, 예전처럼 "인가 확인 API를 또 호출하고 그 결과로 git 바이너리를
exec하는" 두 번째 스크립트가 필요 없다.

```bash
#!/usr/bin/env bash
# yona AuthorizedKeysCommand 훅 — sshd가 접속자의 공개키(%t=타입, %k=base64)를 넘겨주면
# yona 서버에 "이 키가 등록된 사용자/Deploy Key인가" 물어보고, 맞으면 authorized_keys
# 형식 한 줄을 표준출력에 인쇄한다(OpenSSH가 이 출력을 authorized_keys 파일처럼 취급).
# 모르는 키거나 오류가 나면 아무것도 출력하지 않고 exit 0 — sshd가 다음 방법(다른
# AuthorizedKeysFile 등)으로 넘어가거나 최종적으로 인증을 거부한다. **틀렸을 때 조용히
# 실패하는 쪽으로 설계할 것** — 여기서 접근을 허용하는 실수를 하면 안 된다.
set -euo pipefail

YONA_URL="${YONA_INTERNAL_URL:-http://127.0.0.1:8080}"
SECRET_FILE="${YONA_SSH_SECRET_FILE:-/etc/yona/ssh-internal-secret}"
RELAY_SOCKET="${YONA_SSH_RELAY_SOCKET:-/tmp/yona/ssh-relay.sock}"

key_type="$1"
key_blob="$2"

[ -r "$SECRET_FILE" ] || exit 0
secret="$(cat "$SECRET_FILE")"

response="$(curl -s -m 5 -X POST "$YONA_URL/internal/ssh/authenticate" \
  -H "Content-Type: application/json" \
  -H "X-Yona-Internal-Secret: $secret" \
  -d "{\"publicKey\": \"$key_type $key_blob\"}")" || exit 0

principal="$(echo "$response" | jq -r '.principal // empty')"
[ -n "$principal" ] || exit 0

# forced command 본체 — SshRelayServer(P3-18)의 핸드셰이크 프로토콜 그대로: 첫 줄은 이
# principal, 둘째 줄은 클라이언트가 실제 요청한 명령(SSH_ORIGINAL_COMMAND, sshd가 세션
# 시작 시 자동으로 채워준다). 그 두 줄을 보낸 뒤부터는 순수 바이트 릴레이이므로, 이후 클라이언트가
# 보내는 나머지 stdin도 그대로 이어붙여야 한다(cat) — printf와 cat의 출력을 한 파이프로 묶어
# socat의 표준입력("-")에 넣고, socat의 표준출력은 건드리지 않아 그대로 ssh 세션에 돌아간다.
# $SSH_ORIGINAL_COMMAND는 지금(ssh-auth.sh 실행 시점)이 아니라 나중에(forced command가 실제
# 세션에서 실행될 때) 평가돼야 하므로 여기서는 반드시 이스케이프(\$)해서 리터럴로 심어야 한다.
# 대화형 로그인(원본 명령이 없는 ssh -T 접속)은 GitHub과 동일하게 신원 확인 용도로만 허용하고
# 셸은 주지 않는다.
forced_command="if [ -z \"\$SSH_ORIGINAL_COMMAND\" ]; then echo 'Hi! yona SSH 인증에 성공했습니다. 다만 대화형 셸 접속은 지원하지 않습니다.' >&2; exit 1; fi; { printf '%s\\n%s\\n' '$principal' \"\$SSH_ORIGINAL_COMMAND\"; cat; } | socat - UNIX-CONNECT:$RELAY_SOCKET"

# authorized_keys의 command="..." 값 안에 있는 리터럴 큰따옴표는 반드시 \" 로 이스케이프해야
# 한다 — 안 그러면 sshd가 이스케이프 안 된 첫 번째 큰따옴표에서 값을 끊어버려 뒷부분이
# authorized_keys의 별도 필드로 잘못 해석된다(man sshd(8)의 AUTHORIZED_KEYS FILE FORMAT 참고,
# 위 forced_command에 "$SSH_ORIGINAL_COMMAND" 리터럴이 들어있어 실제로 해당됨).
#
# **역슬래시는 절대 이스케이프하면 안 된다** — 실제 컨테이너에 sshd를 띄워 소켓 릴레이까지
# 관통시켜 검증하는 과정에서(2026-09-08) 처음엔 "\\"도 "\\\\"로 이스케이프했었는데, 그러면
# `printf '%s\n%s\n' ...`의 `\n`이 실제 개행이 아니라 리터럴 백슬래시+n 두 글자로 클라이언트에
# 전달돼(`socat -x`로 실제 바이트를 떠서 확인) 핸드셰이크 자체가 깨졌다 — OpenSSH의
# authorized_keys 파서는 값을 끝내는 큰따옴표를 찾을 때 "\"" 만 특별 취급(이스케이프된
# 따옴표로 인식해 값을 안 끝냄)할 뿐, 그 외의 백슬래시는 전혀 손대지 않고 그대로 통과시킨다
# (man 페이지엔 "큰따옴표를 escape할 수 있다"고만 적혀 있고 일반적인 백슬래시 언이스케이프는
# 명시돼 있지 않은데, 실제로도 그렇게 동작함을 `socat -x`로 직접 확인). 그래서 역슬래시는
# 원본 그대로 둬야 forced_command 안의 `\n`이 살아남는다.
escaped_command="${forced_command//\"/\\\"}"

# no-pty 등으로 대화형 셸/포트포워딩을 원천 차단하고, command=로 이 세션을 무조건
# 위 forced_command로 강제한다.
echo "command=\"$escaped_command\",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty $key_type $key_blob"
```

권한 설정:

```bash
sudo chmod 750 /usr/local/lib/yona/ssh-auth.sh
sudo chown root:yona-ssh-hook /usr/local/lib/yona/ssh-auth.sh
```

## Step 4. sshd_config에 등록

`/etc/ssh/sshd_config`에 아래를 추가한다(파일 맨 끝, `Match` 블록은 항상 끝에 와야 함).
`git`/`hg` 두 계정으로 접속할 때만 이 훅이 개입하도록 `Match User git,hg`로 범위를 좁힌다
(OpenSSH는 쉼표로 여러 계정을 한 Match 블록에 묶을 수 있다) — 다른 시스템 계정의 SSH 로그인
방식에는 영향이 없다. 두 계정 다 완전히 같은 설정을 쓴다(Step 1 참고 — 계정 이름 자체는
`ssh-auth.sh`의 동작에 아무 영향이 없다).

```sshd_config
Match User git,hg
    AuthorizedKeysCommand /usr/local/lib/yona/ssh-auth.sh %t %k
    AuthorizedKeysCommandUser yona-ssh-hook
```

`%t %k`는 반드시 명령줄에 직접 적어야 한다 — OpenSSH는 `AuthorizedKeysCommand`를 인자 없이
실행하는 게 기본값이고, `%` 토큰은 이 줄에 명시한 것만 치환돼 인자로 전달된다(`man
sshd_config`의 `AuthorizedKeysCommand` 항목 참고). `%t`=공개키 타입, `%k`=base64 키 데이터.

`AuthorizedKeysCommandUser`는 root도 아니고 접속하려는 계정(`git`)도 아닌 **전용 저권한
계정**이어야 한다(OpenSSH가 강제하는 요구사항). Step 2에서 만든 `yona-ssh-hook` 그룹을 쓰는
계정을 하나 만든다:

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin --gid yona-ssh-hook yona-ssh-hook
```

설정을 검증하고 적용한다(**절대 `restart` 말고 `reload`나 `sshd -t` 검증 후 재시작** — 문법
오류가 있는 채로 재시작하면 SSH 접속 자체가 끊길 수 있다):

```bash
sudo sshd -t                     # 문법 오류 확인, 아무 출력 없으면 정상
sudo systemctl reload sshd       # 기존 세션 끊지 않고 설정만 다시 읽음
# reload를 지원하지 않는 배포판이면: sudo systemctl restart sshd
```

## Step 5. 실제로 확인해보기

**이 문서 전체(Step 1~4 + 아래 시나리오)를 실제 컨테이너(sshd + yona 앱)에 그대로 적용해
2026-09-08에 end-to-end로 검증했다** — 아래 절차 자체가 이론이 아니라 실측 결과다. 이 과정에서
실제로 발견해 위 Step 1/Step 3에 이미 반영한 버그 2건:
- **Step 1의 `nologin` 셸 버그**: forced command가 `<셸> -c "<명령>"`으로 실행되는데
  nologin은 인자를 무시하고 자기 메시지만 찍어 `command=`를 완전히 무력화시켰다 — `/bin/bash`로
  바꿔서 해소.
- **Step 3의 역슬래시 이중 이스케이프 버그**: authorized_keys의 `command="..."` 값을 이스케이프할
  때 큰따옴표뿐 아니라 역슬래시까지 이스케이프했었는데, OpenSSH가 역슬래시는 언이스케이프하지
  않고 그대로 통과시켜(`\"`만 특별 취급) `printf`의 `\n`이 실제 개행이 아니라 리터럴 문자 두
  개로 전달되는 바람에 핸드셰이크 자체가 깨졌다 — `socat -x`로 실제 바이트를 떠서 확인 후
  역슬래시 이스케이프를 제거해 해소.

1. yona 웹 UI에서 SSH 공개키를 하나 등록한다(`내 정보 > SSH 키`).
2. 대화형 접속 테스트 — "셸은 안 준다"는 메시지가 떠야 정상:
   ```bash
   ssh -T -i ~/.ssh/id_ed25519 git@yona.example.com
   # Hi! yona SSH 인증에 성공했습니다. 다만 대화형 셸 접속은 지원하지 않습니다.
   ```
3. 실제 clone/push 테스트(실측 완료 — 정상 동작 확인):
   ```bash
   git clone git@yona.example.com:owner/repo.git
   cd repo && touch test.txt && git add test.txt
   git commit -m "ssh push 테스트" && git push
   ```
   Mercurial 저장소는 `hg` 계정으로 접속한다(계정만 다를 뿐 나머지는 동일):
   ```bash
   hg clone ssh://hg@yona.example.com/owner/repo
   ```
4. 등록되지 않은 키로 접속하면 sshd 표준 방식대로 `Permission denied (publickey)`가 떠야 한다.
5. **브랜치 보호 확인**(실측 완료): 보호된 브랜치에 직접 push하면 `git push`가
   `! [remote rejected] ... (branch 'main' protected: ... require_pull_request)`처럼 사유를
   그대로 보여주며 거부돼야 한다 — HTTPS 경로와 동일한 정책이 SSH에도 적용되는지 확인하는
   가장 중요한 시나리오다(이 문서/`SshRelayServer`가 만들어진 핵심 이유).
6. **PRIVATE 프로젝트 비멤버 거부 확인**(실측 완료): 그 프로젝트 멤버가 아닌 사용자의 키로
   clone을 시도하면 `fatal: protocol error: bad line length character: ERR ` 같은 메시지와
   함께 실패해야 한다(트러블슈팅 절의 "clone/fetch 자체가 거부" 항목 참고 — 이 지저분한
   메시지 자체가 정상 동작이다, 거부는 됐다는 뜻).

## 트러블슈팅

- **`Permission denied (publickey)`만 뜨고 원인을 모르겠다** — sshd 로그를 verbose로 보면서
  재현한다: `sudo sshd -d -p 2200`(다른 포트로 임시 sshd 하나 더 띄워 디버깅) 또는
  `journalctl -u sshd -f`를 보며 접속 시도. `ssh-auth.sh`가 던지는 curl 오류(`set -e` 때문에
  스크립트가 중간에 죽으면 sshd 로그에 "AuthorizedKeysCommand ... failed"가 남는다)가 흔한
  원인 — 스크립트를 직접 실행해서 확인: `sudo -u yona-ssh-hook /usr/local/lib/yona/ssh-auth.sh ssh-ed25519 AAAA...`
- **`curl: (7) Failed to connect`** — yona 서버가 sshd와 같은 호스트/네트워크 네임스페이스에
  없다(컨테이너로 yona를 띄웠다면 `network_mode: host`가 필요하거나, 훅 스크립트를 yona
  컨테이너 안에서 실행하도록 재구성해야 한다). `SshInternalController`는 루프백 요청만
  받으므로 이 부분은 타협의 여지가 없다.
- **403이 온다** — `/etc/yona/ssh-internal-secret` 파일 내용과 yona의
  `yona.ssh.internal-secret` 설정값이 정확히 일치하는지(공백/개행 문자 포함) 확인한다.
  `echo -n`으로 파일을 썼는지(트레일링 개행이 섞이면 시크릿이 달라진다) 다시 확인.
- **push가 브랜치 보호로 막히면 메시지가 깔끔하게 뜨지만, clone/fetch 자체가 거부되면 메시지가
  지저분하다** — 실제 컨테이너로 두 경우 다 확인했다(2026-09-08). **push 거부**(브랜치 보호
  등, `git-receive-pack`이 이미 시작된 뒤의 거부)는 JGit `ReceivePack`의 표준 `report-status`를
  타므로 `git push`가 `! [remote rejected] ... (branch 'main' protected: ... require_pull_request)`
  처럼 사유를 그대로 깔끔하게 보여준다. 반면 **clone/fetch 자체가 거부**(PRIVATE 프로젝트
  비멤버, 존재하지 않는 저장소 등 — `SshRelayServer`가 `git-upload-pack`을 시작하기도 전에
  `ERR <사유>` 한 줄만 쓰고 연결을 끊는 경우)는 이게 git의 정식 프로토콜 응답이 아니라서
  클라이언트 쪽엔 `fatal: protocol error: bad line length character: ERR ` 같은 지저분한
  메시지로 뜬다(사유 텍스트 자체는 그 뒤에 섞여 나온다). 원인 자체는 브랜치 보호 정책, PRIVATE
  프로젝트 멤버십, 읽기전용 Deploy Key 여부를 먼저 의심한다([troubleshooting.md](troubleshooting.md)
  및 브랜치 보호 문서 참고) — clone 거부 쪽 메시지가 지저분한 건 실제 동작에 영향 없는 사소한
  UX 한계로 남겨둔다.
- **저장소 파일 자체에 대한 Permission denied가 보인다면** — 이건 이 SSH 문서가 다루는
  범위 밖이다(`git` 계정은 저장소 파일을 직접 건드리지 않는다, 위 Step 1 참고). yona 앱을
  실행하는 OS 계정이 `yona.git.base-dir`/`yona.hg.base-dir`에 rwX 권한이 있는지 확인할 것 —
  이건 SSH가 아니라 yona 앱 자체의 설치/기동 전제조건이다.
- **`socat: E connect(...): Permission denied` 또는 `No such file or directory`** — 전자는
  `git` 계정이 `yona.ssh.relay.socket-path`(기본 `/tmp/yona/ssh-relay.sock`) 소켓 파일에 쓰기
  권한이 없는 경우다(`SshRelayServer`가 바인드 직후 그룹 rw로 고정하고 `git` 계정이 yona 앱과
  같은 그룹이어야 함 — Step 1과 위 사전 준비 항목 재확인, `ls -l`로 소켓 파일의 소유자/그룹/권한을
  직접 확인). 후자는 yona 앱이 아예 안 떠 있거나 `yona.ssh.relay.enabled=false`로 꺼져 있는
  경우다(기본값은 `true`) — 애플리케이션 로그에서 "SshRelayServer listening at ..." 줄을 확인.

## 참고

- 공개키→사용자 판정 로직은 `SshAuthServiceImpl`
  (`src/main/kotlin/com/github/yonaprojects/yona/domain/sshkey/SshAuthServiceImpl.kt`)에 있고,
  `ssh-auth.sh`가 호출하는 API 계약은 `SshInternalController`
  (`src/main/kotlin/com/github/yonaprojects/yona/config/ssh/SshInternalController.kt`)가 정의한다.
  실제 명령(git-upload-pack/git-receive-pack/hg serve 등) 인가 판정과 프로토콜 처리는
  `SshRelayServer`/`GitSshProtocolHandler`/`HgSshProtocolHandler`
  (`src/main/kotlin/com/github/yonaprojects/yona/config/ssh/`)가 소켓 반대편에서 담당한다 —
  HTTPS 경로(`GitServletConfig`)와 완전히 동일한 JGit `PreReceiveHookChain`(브랜치 보호 포함)을
  그대로 타므로, 이 문서의 forced command 자체는 판정 로직을 전혀 갖지 않는 순수 바이트
  릴레이다.
- 윈도우 전용 임베디드 SSH 서버(`YonaMinaSshServer`, `yona.ssh.mina.*` 설정)는 이 문서와 별개
  경로다 — 리눅스/맥 운영 서버라면 이 문서만 따르면 된다.
- 설계 배경 전체는 `docs/yona-wiki/plans/p3-03-ssh-gpg.md`(SSH/GPG 설계), `docs/parity/tickets/p3-18.md`
  (forced command를 소켓 릴레이로 바꾼 이유와 핸드셰이크 프로토콜 상세) 참고.
- **Git과 Mercurial 둘 다 이미 지원한다** — `ssh-auth.sh`가 만드는 forced command는 원본 명령이
  git이든(`git-upload-pack '<repo>.git'` 등) Hg든(`hg -R '<repo>' serve --stdio`, 공식 `hg-ssh`가
  이 문서와 완전히 같은 아키텍처를 쓰므로 real `hg` 클라이언트가 실제로 보내는 형식 그대로) 그냥
  소켓에 그대로 릴레이한다 — 어느 쪽인지 구분해 처리하는 건 `SshRelayServer`가 소켓 반대편에서
  하므로, 이 스크립트 자체는 VCS 종류와 무관하다. **다만 계정 이름(`git`/`hg`)은 사용자에게
  보이는 URL 관례를 맞추기 위한 것일 뿐**이다(Step 1 참고) — `ssh-auth.sh`는 어느 계정으로
  왔는지 신경 쓰지 않는다. Mercurial 쪽 브랜치 보호(require_pull_request 등)는 아직 없다
  (`P3-12` 2라운드 과제 — `HgSshProtocolHandler`에 훅을 걸 자리만 마련돼 있음,
  `docs/parity/tickets/p3-18.md` 참고). SVN은 관례상 이 공유-계정 패턴을 쓰지 않아(실제 시스템
  계정 단위 접속이 표준) 이 문서의 대상이 아니며, 지금처럼 HTTP(WebDAV, `SvnController`)로만
  서빙한다.
