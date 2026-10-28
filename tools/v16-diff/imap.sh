#!/bin/bash
# 메일 수신(IMAP) 대조용 가짜 IMAP 서버를 띄운다. 서버마다 따로 쓴다(받은 편지함이 섞이지 않게).
#   imap.sh start   v1.6용 19143(→ 143번 포트로 전달, v1.6는 포트를 정할 수 없다), v2용 19243을 띄우고 $WORK/imap.on을 만든다.
#                    그다음 `servers.sh restore <상태>`로 서버를 다시 띄우면 두 서버가 IMAP에 연결한다.
#   imap.sh stop    끄고 imap.on을 지운다.
# 143번 포트는 일반 사용자가 열 수 없어서 도커(root)가 `--network host`로 전달한다.
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=/home/jiho/yona-convert/yona-diff-work
J21=/usr/lib/jvm/java-21-openjdk-amd64
case "${1:-}" in
  start)
    "$0" stop >/dev/null 2>&1
    CP=$(cat "$HERE/imap/classpath.txt")
    for P in 19143 19243; do
      nohup $J21/bin/java -cp "$CP" "$HERE/imap/Imapd.java" $P yona-in@example.com pw > "$WORK/imapd-$P.log" 2>&1 &
      echo $! > "$WORK/imapd-$P.pid"
    done
    docker rm -f yona-imap143 >/dev/null 2>&1
    docker run -d --name yona-imap143 --network host alpine sh -c 'nc -lk -p 143 -e nc 127.0.0.1 19143' >/dev/null
    touch "$WORK/imap.on"
    sleep 4
    echo "IMAP 서버 시작: v1.6 143(→19143), v2 19243"
    ;;
  stop)
    for P in 19143 19243; do [ -f "$WORK/imapd-$P.pid" ] && kill "$(cat "$WORK/imapd-$P.pid")" 2>/dev/null; rm -f "$WORK/imapd-$P.pid"; done
    docker rm -f yona-imap143 >/dev/null 2>&1
    rm -f "$WORK/imap.on"
    echo "IMAP 서버 중지"
    ;;
  *) echo "사용법: $0 start|stop"; exit 1 ;;
esac
