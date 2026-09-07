# SSH: 시스템 sshd 연동 (`git@host:owner/repo.git`)

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
                                                      │    (command="ssh-shell.sh <principal>" 포함)
                                                      ▼
                                              sshd가 인증 완료, 세션을 command= 로 강제 실행
                                                      │
                                                      ▼
                                              ssh-shell.sh ──⑤POST /internal/ssh/authorize──▶ yona 서버
                                                      │        (이 저장소에 이 사용자가 push/clone 가능한가?)
                                                      ▼
                                              ⑥허용되면 실제 git-upload-pack/git-receive-pack 실행
```

GitHub/GitLab이 쓰는 것과 동일한 패턴이다(`AuthorizedKeysCommand` + forced command). yona는
이 흐름의 ③⑤에 해당하는 판정 API(`SshInternalController`, `/internal/ssh/authenticate`,
`/internal/ssh/authorize`)만 제공하고, ②④⑥에 해당하는 훅 스크립트 두 개는 관리자가 이
문서대로 서버에 직접 설치한다(별도 `yona-cli` 바이너리는 필요 없다 — 표준 도구인 `curl`/`jq`
+ 짧은 셸 스크립트로 충분하다).

## 사전 준비

- yona 서버가 이미 이 호스트(같은 머신)에서 떠 있어야 한다. `SshInternalController`는 보안상
  루프백 주소(127.0.0.1)에서 온 요청만 받는다 — 훅 스크립트와 yona 서버가 **반드시 같은
  호스트**에 있어야 한다(컨테이너로 띄웠다면 훅 스크립트도 그 컨테이너 안에서 실행되거나,
  host network를 공유해야 한다).
- `curl`, `jq`가 설치돼 있어야 한다(`apt install curl jq` / `yum install curl jq`).
- yona가 리슨하는 HTTP 포트를 확인한다. `application.yml`에 `server.port`를 별도로 지정하지
  않았다면 Spring Boot 기본값인 `8080`이다 — `server.port`를 직접 설정했다면 그 값을 쓴다
  ([settings-reference.md](settings-reference.md) 참고). 아래 예시는 `8080` 기준이다.

## Step 1. 전용 시스템 계정 만들기

GitHub 방식과 동일하게, 사람이 아니라 **저장소 접근 전용 계정 하나**(관례상 `git`)로 모든
접속을 받는다. 실제 사용자 구분은 OS 계정이 아니라 공개키로 한다.

```bash
sudo useradd --system --shell /usr/sbin/nologin --home-dir /home/git --create-home git
```

`--shell /usr/sbin/nologin`으로 비밀번호/대화형 로그인 자체를 막는다 — 이후 접속은 전부
`AuthorizedKeysCommand`가 만들어주는 forced command로만 이뤄진다.

yona가 만든 bare 저장소 디렉터리(`yona.git.base-dir`, 기본 `/tmp/yona/git` — 운영에서는 반드시
영구 경로로 바꿔야 함, [settings-reference.md](settings-reference.md) 참고)에 `git` 계정이
읽고 쓸 수 있어야 한다. yona 앱을 실행하는 OS 계정과 그룹을 공유하는 게 가장 간단하다.

```bash
# 예: yona 앱이 "yona" 계정으로 돈다고 가정
sudo usermod -aG yona git
sudo chmod -R g+rwX /srv/yona/git   # yona.git.base-dir 실제 경로로 교체
sudo chmod g+s /srv/yona/git        # 이후 새로 생기는 저장소도 그룹 상속되도록
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

이 그룹(`yona-ssh-hook`)에는 Step 3의 훅 실행 계정과 `git` 계정 둘 다 넣는다(둘 다 이 파일을
읽어야 한다).

## Step 3. 훅 스크립트 두 개 설치

`/usr/local/lib/yona/ssh-auth.sh` (①→④, `AuthorizedKeysCommand`가 실행):

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
SHELL_SCRIPT="/usr/local/lib/yona/ssh-shell.sh"

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

# no-pty 등으로 대화형 셸/포트포워딩을 원천 차단하고, command=로 이 세션을 무조건
# ssh-shell.sh로 강제한다(클라이언트가 요청한 명령은 SSH_ORIGINAL_COMMAND로 전달됨).
echo "command=\"$SHELL_SCRIPT $principal\",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty $key_type $key_blob"
```

`/usr/local/lib/yona/ssh-shell.sh` (⑤→⑥, 세션이 실제로 실행하는 forced command):

```bash
#!/usr/bin/env bash
# ssh-auth.sh가 만든 authorized_keys의 command=로 강제 실행된다. $1=principal(위 스크립트가
# 심어둠), $SSH_ORIGINAL_COMMAND=클라이언트가 실제 요청한 git 명령(sshd가 자동으로 채워줌).
set -euo pipefail

YONA_URL="${YONA_INTERNAL_URL:-http://127.0.0.1:8080}"
SECRET_FILE="${YONA_SSH_SECRET_FILE:-/etc/yona/ssh-internal-secret}"

principal="$1"
original_command="${SSH_ORIGINAL_COMMAND:-}"

if [ -z "$original_command" ]; then
  # GitHub과 동일 — 대화형 로그인(ssh -T git@host)은 신원 확인 용도로만 허용하고 셸은 안 준다.
  echo "Hi! yona SSH 인증에 성공했습니다. 다만 대화형 셸 접속은 지원하지 않습니다." >&2
  exit 1
fi

secret="$(cat "$SECRET_FILE")"
payload="$(jq -n --arg p "$principal" --arg c "$original_command" '{principal: $p, command: $c}')"

response="$(curl -s -m 5 -X POST "$YONA_URL/internal/ssh/authorize" \
  -H "Content-Type: application/json" \
  -H "X-Yona-Internal-Secret: $secret" \
  -d "$payload")"

allowed="$(echo "$response" | jq -r '.allowed')"
if [ "$allowed" != "true" ]; then
  echo "$(echo "$response" | jq -r '.reason // "접근이 거부되었습니다."')" >&2
  exit 1
fi

service="$(echo "$response" | jq -r '.service')"     # git-upload-pack / git-receive-pack / git-upload-archive
repo_dir="$(echo "$response" | jq -r '.repoDir')"     # yona가 계산해준 실제 물리 경로(신뢰 가능)

# "git-" 접두어를 뗀 서브커맨드 형태로 실행 — /usr/lib/git-core가 PATH에 없어도 동작한다.
exec git "${service#git-}" "$repo_dir"
```

권한 설정:

```bash
sudo chmod 750 /usr/local/lib/yona/ssh-auth.sh /usr/local/lib/yona/ssh-shell.sh
sudo chown root:yona-ssh-hook /usr/local/lib/yona/ssh-auth.sh
sudo chown root:git /usr/local/lib/yona/ssh-shell.sh
```

## Step 4. sshd_config에 등록

`/etc/ssh/sshd_config`에 아래를 추가한다(파일 맨 끝, `Match` 블록은 항상 끝에 와야 함).
`git` 계정으로 접속할 때만 이 훅이 개입하도록 `Match User git`으로 범위를 좁힌다 — 다른
시스템 계정의 SSH 로그인 방식에는 영향이 없다.

```sshd_config
Match User git
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

1. yona 웹 UI에서 SSH 공개키를 하나 등록한다(`내 정보 > SSH 키`).
2. 대화형 접속 테스트 — "셸은 안 준다"는 메시지가 떠야 정상:
   ```bash
   ssh -T -i ~/.ssh/id_ed25519 git@yona.example.com
   # Hi! yona SSH 인증에 성공했습니다. 다만 대화형 셸 접속은 지원하지 않습니다.
   ```
3. 실제 clone/push 테스트:
   ```bash
   git clone git@yona.example.com:owner/repo.git
   cd repo && touch test.txt && git add test.txt
   git commit -m "ssh push 테스트" && git push
   ```
4. 등록되지 않은 키로 접속하면 sshd 표준 방식대로 `Permission denied (publickey)`가 떠야 한다.

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
- **clone은 되는데 push가 막힌다** — `AuthorizeResponse.reason`이 stderr로 그대로 전달된다
  (`git push` 클라이언트가 이 메시지를 보여줌) — 브랜치 보호 정책, PRIVATE 프로젝트 멤버십,
  읽기전용 Deploy Key 여부를 먼저 의심한다([troubleshooting.md](troubleshooting.md) 및
  브랜치 보호 문서 참고).
- **저장소 파일에 대한 Permission denied (repository 안에서)** — `git` OS 계정이 yona 앱
  계정과 같은 그룹에 속해 있고, `yona.git.base-dir` 디렉터리가 그 그룹에 rwX로 열려 있는지
  Step 1을 다시 확인.

## 참고

- 판정 로직 자체(공개키→사용자, 명령→권한)는 `SshAuthServiceImpl`
  (`src/main/kotlin/com/github/yonaprojects/yona/domain/sshkey/SshAuthServiceImpl.kt`)에 있고,
  이 문서의 훅 스크립트가 호출하는 API 계약은 `SshInternalController`
  (`src/main/kotlin/com/github/yonaprojects/yona/config/ssh/SshInternalController.kt`)가 정의한다.
- 윈도우 전용 임베디드 SSH 서버(`YonaMinaSshServer`, `yona.ssh.mina.*` 설정)는 이 문서와 별개
  경로다 — 리눅스/맥 운영 서버라면 이 문서만 따르면 된다.
- 설계 배경 전체는 `docs/yona-wiki/plans/p3-03-ssh-gpg.md` 참고.
- **지금은 Git 전용이다.** Mercurial도 공식 `hg-ssh`가 이 문서와 똑같은 아키텍처(공유 계정 +
  `authorized_keys` 강제 명령)를 쓰므로, P3-12 2라운드에서 `ssh-shell.sh`에 `hg -R '<repo>' serve
  --stdio` 패턴을 인식하는 분기를 추가해 이 가이드를 그대로 확장할 예정이다(`docs/yona-wiki/plans/p3-12-mercurial-hg4j.md`
  참고). SVN은 관례상 이 공유-계정 패턴을 쓰지 않아(실제 시스템 계정 단위 접속이 표준) 이 문서의
  대상이 아니며, 지금처럼 HTTP(WebDAV, `SvnController`)로만 서빙한다.
