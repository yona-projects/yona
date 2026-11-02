# 시스템 sshd로 Git SSH 접근 열기 (`git@호스트:소유자/저장소.git`)

Yona는 Git SSH 접근을 두 방식으로 제공한다. 둘 중 필요한 것을 켜면 되고, 둘 다 꺼 두면 SSH 포트를 열지 않는다.

| 방식 | 접속 주소 | 특징 |
|---|---|---|
| 내장 SSH 서버(`yona.ssh.enabled`) | `ssh://git@호스트:2222/소유자/저장소.git` | Yona가 포트를 직접 연다. 설치할 것이 없다. 22번 포트는 root 권한이나 포트 전달이 필요하다 |
| **시스템 sshd 연동**(`yona.ssh.relay.enabled`, 이 문서) | `git@호스트:소유자/저장소.git` | 이미 22번 포트에서 도는 OpenSSH sshd가 접속을 받는다. GitHub와 같은 주소 모양이다 |

두 방식 모두 사용자와 배포 키는 Yona의 **SSH 키** 설정(내 정보, 프로젝트 설정의 배포 키)에서 등록한 공개키를 쓴다. 접근 권한 판정, 브랜치 보호 규칙, 서명 커밋 필수 규칙, 감사 기록은 HTTP와 똑같이 적용된다.

## 두 가지 도우미: Go 실행 파일(권장)과 셸 훅

sshd 쪽에서 쓰는 도우미는 두 가지이고 서버 쪽 소켓 프로토콜은 같다. 하나만 고르면 된다.

| | Go 실행 파일 `yona-ssh`(권장) | 셸 훅 `yona-ssh-auth.sh` |
|---|---|---|
| 필요한 것 | 빌드한 파일 하나 | `bash`, `curl`, `socat` |
| 강제 명령 | 셸 없이 `yona-ssh relay ...` | 셸 한 줄(FIFO, `socat`) |
| 빌드 | `cd tools/ssh-helper && go build -o yona-ssh .`(Go 1.22 이상) | 없음 |
| 운영체제 | Linux, macOS, Windows에서 빌드된다. 단 **Windows의 번들 OpenSSH 서버는 `AuthorizedKeysCommand`를 지원하지 않는다.** Windows 서버는 내장 SSH 서버(`yona.ssh.enabled`)를 쓴다 | Linux, macOS |

이 문서의 단계는 같다. **Go 실행 파일을 쓰려면** 3절의 훅 설치와 4절의 sshd 설정만 아래처럼 바뀐다.

```bash
cd tools/ssh-helper && go build -o yona-ssh .
sudo install -o root -g root -m 755 yona-ssh /usr/local/bin/yona-ssh
```

```
Match User git
    AuthorizedKeysCommand /usr/local/bin/yona-ssh auth %t %k
    AuthorizedKeysCommandUser yona-ssh-hook
```

설정 파일(`/etc/yona/ssh-auth.conf`)과 공유 비밀 값 파일은 셸 훅과 같다. 실행 파일은 `git` 계정과 훅 계정이 모두 실행할 수 있어야 하고(`755`), 비밀 값 파일은 훅 계정만 읽는다(`relay`는 비밀 값을 읽지 않는다).

## 동작 방식

```
클라이언트 ssh/git ──①공개키로 접속──▶ 시스템 sshd (22번 포트)
                                         │ ②AuthorizedKeysCommand로 훅 실행(%t %k)
                                         ▼
                                  yona-ssh-auth.sh ──③POST /internal/ssh/authenticate──▶ Yona 서버 (127.0.0.1)
                                         ▲                                              (루프백 + 공유 비밀 값)
                                         │ ④"등록된 키입니다": 서명된 인증 표를 받아
                                         │   authorized_keys 형식 한 줄을 인쇄(강제 명령에 표를 심는다)
                                         ▼
                                  sshd가 인증을 끝내고 세션을 강제 명령으로 실행
                                         │ ⑤socat(또는 yona-ssh relay)이 입출력을 유닉스 소켓에 이어 붙임
                                         ▼
                                  Yona의 중계 소켓 ── 인증 표 확인 → 인가 → git 프로토콜과 푸시 훅 실행
```

- sshd를 실행하는 계정(`git`)은 **저장소 파일을 읽거나 쓸 필요가 없다.** 저장소는 이미 떠 있는 Yona 프로세스가 열고, `git` 계정은 소켓에 바이트를 전달할 뿐이다.
- 중계 소켓에는 `yona` 그룹의 계정이면 누구나 연결할 수 있으므로, 소켓에 적히는 사용자 번호를 그대로 믿지 않는다. 훅이 키를 인증할 때 Yona가 **공유 비밀 값으로 서명한 인증 표(5분 유효)** 를 돌려주고, 중계 서버는 표의 서명을 확인한다.
- 훅은 모르는 키나 오류에서는 아무것도 인쇄하지 않고 0으로 끝난다(sshd가 인증을 거부한다).

## 설치

이 절차는 AlmaLinux 8 컨테이너의 OpenSSH 8.0 sshd로 셸 훅과 Go 실행 파일 두 가지 모두 실제로 확인했다. 아래 예시는 Yona가 `yona` 계정으로 같은 호스트에서 돌고 HTTP 포트가 9000이라고 가정한다.

### 1. Yona 설정

`application.yml`(또는 환경 변수)에 다음을 둔다. 설정의 전체 목록과 설명은 저장소의 `application.yml`에 있다.

```yaml
yona:
  ssh:
    # user는 sshd의 전용 계정 이름과 같아야 한다(복제 주소의 `git@`)
    user: git
    relay:
      enabled: true
      # 비우면 <데이터 디렉터리>/ssh/relay.sock. 경로가 108바이트쯤을 넘으면 연결하지 못하므로 데이터 디렉터리가 깊으면 짧은 경로를 직접 정한다
      socket-path: /var/lib/yona/ssh/relay.sock
      # 강한 무작위 값. 아래 훅 쪽 파일에도 똑같이 둔다. 비워 두면 <데이터 디렉터리>/ssh/internal-secret 파일을 만들어 쓰지만
      # 그 파일은 Yona 계정만 읽을 수 있어 훅이 읽지 못하므로 운영에서는 직접 정한다
      internal-secret: "<openssl rand -base64 32 의 결과>"
```

Yona를 재시작하면 `SSH relay listening on /var/lib/yona/ssh/relay.sock` 로그가 보이고 소켓 파일이 `rw-rw----`(Yona 계정과 그 그룹)로 생긴다.

### 2. 전용 계정과 그룹

```bash
# 저장소 접근 전용 계정. 사람이 로그인하는 계정이 아니다. 비밀번호를 설정하지 않아 잠긴 상태이고 접속은 항상 강제 명령으로만 끝난다
sudo useradd --system --shell /bin/bash --home-dir /home/git --create-home git
# 중계 소켓은 Yona 계정의 그룹만 연결할 수 있다
sudo usermod -aG yona git
```

**셸을 `/usr/sbin/nologin`으로 하면 안 된다.** sshd는 강제 명령을 `셸 -c "<명령>"`으로 실행하는데 `nologin`은 인자를 무시하고 "계정을 쓸 수 없다"는 문구만 찍고 끝나, 강제 명령이 실행되지 않는다. 비밀번호가 잠겨 있고 강제 명령이 항상 걸려 있으므로 `/bin/bash`여도 안전하다.

### 3. 공유 비밀 값과 훅 스크립트

훅은 sshd가 `AuthorizedKeysCommandUser`(root도 접속 계정도 아닌 저권한 전용 계정)로 실행한다.

```bash
sudo groupadd --system yona-ssh-hook
sudo useradd --system --no-create-home --shell /sbin/nologin --gid yona-ssh-hook yona-ssh-hook

# 공유 비밀 값(Yona의 yona.ssh.relay.internal-secret과 같은 값. echo -n으로 끝에 개행이 붙지 않게 한다)
sudo mkdir -p /etc/yona /usr/local/lib/yona
echo -n '<위와 같은 값>' | sudo tee /etc/yona/ssh-internal-secret >/dev/null
sudo chown root:yona-ssh-hook /etc/yona/ssh-internal-secret && sudo chmod 640 /etc/yona/ssh-internal-secret

# 훅 스크립트(이 저장소의 tools/ssh/yona-ssh-auth.sh). 필요한 도구: bash, curl, socat
sudo install -o root -g yona-ssh-hook -m 750 tools/ssh/yona-ssh-auth.sh /usr/local/lib/yona/yona-ssh-auth.sh
```

sshd는 훅을 최소한의 환경으로 실행해 환경 변수를 줄 수 없으므로 **설정은 파일**에 둔다(`/etc/yona/ssh-auth.conf`, 생략하면 기본값):

```bash
sudo tee /etc/yona/ssh-auth.conf >/dev/null <<'CONF'
YONA_INTERNAL_URL=http://127.0.0.1:9000
YONA_SSH_RELAY_SOCKET=/var/lib/yona/ssh/relay.sock
CONF
```

| 항목 | 기본값 | 설명 |
|---|---|---|
| `YONA_INTERNAL_URL` | `http://127.0.0.1:9000` | Yona 서버의 주소. **같은 호스트**여야 한다(내부 인증 주소는 루프백 요청만 받는다) |
| `YONA_SSH_SECRET_FILE` | `/etc/yona/ssh-internal-secret` | 공유 비밀 값 파일 |
| `YONA_SSH_RELAY_SOCKET` | `/var/lib/yona/ssh/relay.sock` | Yona의 `yona.ssh.relay.socket-path`와 같은 경로 |

### 4. sshd 설정

`/etc/ssh/sshd_config`의 **맨 끝**(`Match` 블록은 항상 끝에 온다)에 추가한다. `Match User`로 범위를 좁혀 다른 계정의 로그인에는 영향이 없다.

```
Match User git
    AuthorizedKeysCommand /usr/local/lib/yona/yona-ssh-auth.sh %t %k
    AuthorizedKeysCommandUser yona-ssh-hook
```

`%t %k`는 반드시 명령줄에 적는다(sshd는 적은 토큰만 인자로 넘긴다). 적용은 문법을 검사한 뒤에 한다. 문법 오류가 있는 채 재시작하면 SSH가 끊길 수 있다.

```bash
sudo sshd -t                    # 아무 출력이 없어야 한다
sudo systemctl reload sshd
```

## 확인

1. Yona 웹에서 SSH 공개키를 등록한다(내 정보의 SSH 키).
2. 셸은 주지 않는다는 문구가 나오면 인증 경로가 이어진 것이다.
   ```bash
   ssh -T git@yona.example.com
   # Hi! Yona SSH authentication succeeded. Interactive shell access is not provided.
   ```
3. 복제와 푸시:
   ```bash
   git clone git@yona.example.com:소유자/저장소.git
   git push
   ```
4. 등록하지 않은 키는 `Permission denied (publickey)`이다. 보호된 브랜치에 직접 푸시하면 HTTP와 같은 사유(`protected branch: ...`)로 거부된다. 읽을 수 없는 저장소는 `fatal: remote error: Repository not found.`이다.

## 문제 해결

- **`Permission denied (publickey)`만 나온다**: 훅이 아무것도 인쇄하지 않은 것이다. 훅 계정으로 직접 실행해 본다.
  ```bash
  sudo runuser -u yona-ssh-hook -- bash -x /usr/local/lib/yona/yona-ssh-auth.sh ssh-ed25519 AAAA...(공개키 본문)
  ```
  원인은 보통 (a) `YONA_INTERNAL_URL`이 틀렸거나 Yona가 다른 호스트에 있다, (b) 공유 비밀 값이 서로 다르다(끝의 개행, 공백), (c) 키가 등록되지 않았거나 사용자가 잠겼다, (d) 응답이 JSON이 아니라 HTML이다(Yona 버전에 연동 기능이 없거나 `yona.ssh.relay.enabled`가 꺼져 있다)이다. sshd를 `LogLevel VERBOSE`로 두면 `Accepted key ... found at ...yona-ssh-auth.sh` 줄이 보여 훅이 키를 받아들였는지 알 수 있다.
- **403이 온다**: 공유 비밀 값이 다르거나 요청이 루프백이 아니다.
- **`socat: E connect(...): Permission denied`**: `git` 계정이 중계 소켓에 연결할 권한이 없다. `ls -l`로 소켓의 소유 그룹과 `git` 계정의 그룹 소속을 확인한다. **`No such file or directory`** 는 Yona가 안 떠 있거나 `yona.ssh.relay.enabled`가 꺼져 있거나 소켓 경로가 훅 설정과 다른 것이다.
- **Yona를 컨테이너로 띄웠다**: 훅은 같은 호스트(같은 네트워크)의 루프백으로 Yona에 접속해야 한다. `network_mode: host`를 쓰거나 sshd와 같은 컨테이너에서 훅을 실행한다. 소켓 파일은 sshd가 볼 수 있는 폴더에 두고 마운트한다.
- **읽을 수 없는 저장소의 메시지**가 `fatal: remote error: ...`로 보이는 것은 정상이다(git의 정식 오류 줄이다). 원본 명령이 git이 아닌 접속(`ssh git@호스트 ls`)은 `ERR Invalid command.` 줄을 받는다.
- 이 연동은 Yona 감사 기록에 접속 종류 `ssh`와 클라이언트 주소(`SSH_CLIENT`)를 남긴다. sshd 쪽의 접속 시도 제한(`MaxAuthTries`, fail2ban)은 sshd에서 따로 설정한다. 내부 인증 주소는 키를 하나씩 물으므로 주소 단위 실패 한도(내장 서버와 HTTP의 인증 실패 차단)를 적용하지 않는다.
