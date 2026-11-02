#!/usr/bin/env bash
# Yona 시스템 sshd 연동 훅(추가 개발 항목 X-3b, decisions.md D-67)이다. sshd의 `AuthorizedKeysCommand`로 등록한다.
#
#   AuthorizedKeysCommand /usr/local/lib/yona/yona-ssh-auth.sh %t %k
#
# sshd가 접속자의 공개키(%t 종류, %k base64 본문)를 넘기면 Yona 서버의 내부 인증 주소에 등록된 사용자 키나 배포 키인지 묻고, 맞으면 `authorized_keys` 형식 한 줄을
# 표준출력에 인쇄한다(sshd가 이 출력을 authorized_keys 파일처럼 다룬다). 그 줄의 강제 명령(`command=`)은 세션이 시작될 때 Yona의 중계 소켓에 연결해 클라이언트의
# 명령(git-upload-pack, git-receive-pack)을 그대로 넘긴다. 실제 저장소 접근과 인가, 푸시 훅은 모두 이미 떠 있는 Yona 서버가 소켓 반대편에서 처리하므로
# 이 스크립트가 쓰는 계정은 저장소 파일을 읽고 쓸 필요가 없다.
#
# 모르는 키이거나 오류가 나면 아무것도 인쇄하지 않고 0으로 끝난다(sshd가 인증을 거부한다). 접근을 잘못 허용하는 쪽이 아니라 조용히 실패하는 쪽으로 만들었다.
#
# 설정(모두 선택): /etc/yona/ssh-auth.conf(YONA_SSH_CONFIG로 경로를 바꾼다)에 `이름=값` 줄로 두거나 환경 변수로 준다. sshd가 훅을 부를 때는 환경 변수가 전달되지 않으므로 운영에서는 파일을 쓴다.
#   YONA_INTERNAL_URL        Yona 서버의 주소. 기본 http://127.0.0.1:9000 (같은 호스트여야 한다. 내부 주소는 루프백 요청만 받는다)
#   YONA_SSH_SECRET_FILE     공유 비밀 값을 담은 파일. 기본 /etc/yona/ssh-internal-secret (Yona의 yona.ssh.relay.internal-secret과 같은 값)
#   YONA_SSH_RELAY_SOCKET    중계 소켓 경로. 기본 /var/lib/yona/ssh/relay.sock (Yona의 yona.ssh.relay.socket-path와 같은 경로)
#
# 필요한 도구: bash, curl, socat
set -euo pipefail

# sshd는 AuthorizedKeysCommand를 최소한의 환경으로 실행하므로 환경 변수를 줄 수 없다. 설정은 파일에 둔다(없으면 기본값을 쓴다).
CONFIG_FILE="${YONA_SSH_CONFIG:-/etc/yona/ssh-auth.conf}"
# shellcheck disable=SC1090
[ -r "$CONFIG_FILE" ] && . "$CONFIG_FILE"

YONA_URL="${YONA_INTERNAL_URL:-http://127.0.0.1:9000}"
SECRET_FILE="${YONA_SSH_SECRET_FILE:-/etc/yona/ssh-internal-secret}"
RELAY_SOCKET="${YONA_SSH_RELAY_SOCKET:-/var/lib/yona/ssh/relay.sock}"

key_type="${1:-}"
key_blob="${2:-}"
[ -n "$key_type" ] && [ -n "$key_blob" ] || exit 0
[ -r "$SECRET_FILE" ] || exit 0
secret="$(cat "$SECRET_FILE")"

response="$(curl -s -m 5 -X POST "$YONA_URL/internal/ssh/authenticate" \
  -H "Content-Type: application/json" \
  -H "X-Yona-Internal-Secret: $secret" \
  -d "{\"publicKey\": \"$key_type $key_blob\"}")" || exit 0

# 응답은 {"principal":"<표>"} 이다. 표는 영문자, 숫자, 점, 밑줄, 하이픈만 쓰므로 jq 없이 뽑는다.
principal="$(printf '%s' "$response" | sed -n 's/.*"principal" *: *"\([A-Za-z0-9._-]*\)".*/\1/p')"
[ -n "$principal" ] || exit 0

# 강제 명령: 세션이 시작될 때 sshd가 계정의 셸로 `셸 -c "<이 값>"`을 실행한다. 중계 소켓에는 먼저 세 줄(인증 표, 클라이언트 주소, 클라이언트가 요청한 명령)을 보내고, 그 뒤는
# 클라이언트의 입력을 그대로 이어 붙이는 바이트 중계이다. 원본 명령이 없는 접속(`ssh -T`)은 셸을 주지 않고 신원 확인 문구만 보인다(GitHub와 같다).
#
# 소켓 쪽이 먼저 끝나도(거부 등) 클라이언트 입력을 기다리는 `cat`이 남아 sshd가 채널을 닫지 못하는 것을 막으려고, 입력 전달을 이름 있는 파이프(FIFO)의 별도 프로세스로 분리하고
# socat이 끝나면 그 프로세스를 끝낸다. `set -m`은 필수이다: 잡 컨트롤이 꺼진 비대화형 셸은 백그라운드 명령의 표준입력을 /dev/null로 바꿔 버려 클라이언트 입력이 전달되지 않는다.
# $SSH_ORIGINAL_COMMAND와 $SSH_CLIENT는 지금이 아니라 세션이 시작될 때 평가되어야 하므로 \$로 이스케이프해 그대로 심는다.
forced_command="set -m; if [ -z \"\$SSH_ORIGINAL_COMMAND\" ]; then echo 'Hi! Yona SSH authentication succeeded. Interactive shell access is not provided.' >&2; exit 1; fi; FIFO=\"\$(mktemp -u)\"; mkfifo \"\$FIFO\"; ( printf '%s\\n%s\\n%s\\n' '$principal' \"\${SSH_CLIENT%% *}\" \"\$SSH_ORIGINAL_COMMAND\"; cat ) > \"\$FIFO\" & FEEDER_PID=\$!; disown \"\$FEEDER_PID\"; socat - UNIX-CONNECT:$RELAY_SOCKET < \"\$FIFO\"; kill \"\$FEEDER_PID\" 2>/dev/null; rm -f \"\$FIFO\""

# authorized_keys의 command="..." 값 안의 큰따옴표는 \" 로 이스케이프한다. 역슬래시는 이스케이프하면 안 된다(sshd는 \" 만 특별 취급하고 나머지는 그대로 통과시키므로
# printf의 \n이 그대로 남아야 한다).
escaped_command="${forced_command//\"/\\\"}"

echo "command=\"$escaped_command\",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty $key_type $key_blob"
