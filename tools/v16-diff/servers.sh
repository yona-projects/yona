#!/bin/bash
# 비교용 서버 두 대(v1.6 공식 릴리스: 19200, v2: 19300)를 다루는 스크립트이다.
#   servers.sh start|stop|restart       두 서버 시작, 종료, 재시작
#   servers.sh snapshot <이름>          두 서버의 데이터를 저장 (서버를 멈춘 뒤 저장하고 다시 시작한다)
#   servers.sh restore <이름>           저장해 둔 상태로 되돌린다 (서버를 멈추고, 지금 데이터는 *.discard.* 로 옆에 치운다)
#   servers.sh build                    v2를 다시 빌드한다
#   servers.sh start 는 SMTP 수신기(smtpsink.py, 포트 19225/19325)도 띄워 알림 메일을 yona-diff-work/mails-*.jsonl 에 쌓는다.
# 프로세스는 pid 파일로만 다룬다(pgrep -f 로 찾으면 이 스크립트를 부른 셸까지 종료될 수 있다).
set -u
R=/home/jiho/yona-convert
V16=$R/yona-v1.16-release/yona-1.16.0
V16RUN=$R/yona-v1.16-release/run
V2D=$R/yona-diff-v2
SNAP=$R/yona-diff-snapshots
JAR=$R/yona/build/libs/yona-2.0.0-SNAPSHOT.jar
J8=/usr/lib/jvm/java-8-openjdk-amd64
J21=/usr/lib/jvm/java-21-openjdk-amd64

SINK=$R/yona/tools/v16-diff/smtpsink.py
WORK=$R/yona-diff-work
start_sinks() {
  mkdir -p "$WORK"
  for pair in "19225:mails-v16.jsonl" "19325:mails-v2.jsonl"; do
    port=${pair%%:*}; file=${pair#*:}
    if [ ! -f "$WORK/sink-$port.pid" ] || ! kill -0 "$(cat "$WORK/sink-$port.pid")" 2>/dev/null; then
      nohup python3 "$SINK" "$port" "$WORK/$file" > "$WORK/sink-$port.log" 2>&1 &
      echo $! > "$WORK/sink-$port.pid"
    fi
  done
  # 웹훅 수신기(HTTP)
  for pair in "19226:hooks-v16.jsonl" "19326:hooks-v2.jsonl"; do
    port=${pair%%:*}; file=${pair#*:}
    if [ ! -f "$WORK/sink-$port.pid" ] || ! kill -0 "$(cat "$WORK/sink-$port.pid")" 2>/dev/null; then
      nohup python3 "$R/yona/tools/v16-diff/httpsink.py" "$port" "$WORK/$file" > "$WORK/sink-$port.log" 2>&1 &
      echo $! > "$WORK/sink-$port.pid"
    fi
  done
}
stop_sinks() { for port in 19225 19325 19226 19326; do [ -f "$WORK/sink-$port.pid" ] && kill "$(cat "$WORK/sink-$port.pid")" 2>/dev/null; rm -f "$WORK/sink-$port.pid"; done; }

wait_http() { # 주소 이름
  for i in $(seq 1 80); do
    sleep 3
    c=$(curl -s -o /dev/null -w "%{http_code}" --max-time 4 "$1" 2>/dev/null || true)
    if [ "$c" != "000" ] && [ -n "$c" ]; then echo "$2 up ($c)"; return 0; fi
  done
  echo "$2 did not start"; return 1
}

stop_v16() {
  if [ -f "$V16/RUNNING_PID" ]; then
    P=$(cat "$V16/RUNNING_PID"); kill "$P" 2>/dev/null || true
    for i in 1 2 3 4 5 6 7 8; do sleep 1; kill -0 "$P" 2>/dev/null || break; done
    rm -f "$V16/RUNNING_PID"
  fi
}
stop_v2() {
  if [ -f "$V2D/v2.pid" ]; then
    P=$(cat "$V2D/v2.pid"); kill "$P" 2>/dev/null || true
    for i in 1 2 3 4 5 6 7 8; do sleep 1; kill -0 "$P" 2>/dev/null || break; done
    rm -f "$V2D/v2.pid"
  fi
}
start_v16() {
  mkdir -p "$V16RUN"; cd "$V16RUN"
  JAVA_HOME=$J8 PATH=$J8/bin:$PATH nohup ../yona-1.16.0/bin/yona -Dhttp.port=19200 -Dhttp.address=127.0.0.1 -Dapplication.update.check.use=false -J-Xmx768m \
    -Dsmtp.host=127.0.0.1 -Dsmtp.port=19225 -Dsmtp.ssl=false -Dsmtp.tls=false -Dsmtp.mock=false -Dsmtp.user=yona.mail -Dsmtp.password=pw -Dsmtp.domain=example.com \
    -Dapplication.notification.bymail.initdelay=1s -Dapplication.notification.bymail.interval=2s -Dapplication.notification.bymail.delay=3s > "$V16RUN/run.log" 2>&1 &
  wait_http http://127.0.0.1:19200/ v1.6
}
start_v2() {
  mkdir -p "$V2D/data" "$V2D/db"; cd "$V2D"
  nohup $J21/bin/java -jar "$JAR" --server.port=19300 --yona.data-dir="$V2D/data" \
    --spring.datasource.url="jdbc:h2:file:$V2D/db/yona;DB_CLOSE_DELAY=-1" \
    --yona.social.login-support="github, google" --yona.social.github.client-id=id --yona.social.github.client-secret=secret \
    --yona.social.google.client-id=id --yona.social.google.client-secret=secret \
    --yona.update.check=false --spring.mail.host=127.0.0.1 --spring.mail.port=19325 --yona.smtp.user=yona.mail --yona.smtp.domain=example.com --yona.imap.use=false --yona.imap.address=your-yona-email-address@gmail.com --yona.imap.user=your-yona-email-address@gmail.com \
    --yona.notification-mail-init-delay=1000 --yona.notification-mail-interval=2000 --yona.notification-mail-delay=3000 > "$V2D/boot.log" 2>&1 &
  echo $! > "$V2D/v2.pid"
  wait_http http://127.0.0.1:19300/ v2
}

state_items_v16() { echo "yona.h2.db conf/application.conf repo uploads"; }
state_items_v2() { echo "db data"; }

save_state() { # 이름
  mkdir -p "$SNAP/$1"
  (cd "$V16" && tar -czf "$SNAP/$1/v16.tgz" $(for i in $(state_items_v16); do [ -e "$i" ] && echo "$i"; done))
  (cd "$V2D" && tar -czf "$SNAP/$1/v2.tgz" $(state_items_v2))
}
restore_state() { # 이름
  local stamp; stamp=$(date +%s)
  for i in $(state_items_v16); do [ -e "$V16/$i" ] && mv "$V16/$i" "$V16/$i.discard.$stamp"; done
  rm -f "$V16/yona.lock.db" "$V16/yona.trace.db"
  tar -xzf "$SNAP/$1/v16.tgz" -C "$V16"
  for i in $(state_items_v2); do [ -e "$V2D/$i" ] && mv "$V2D/$i" "$V2D/$i.discard.$stamp"; done
  tar -xzf "$SNAP/$1/v2.tgz" -C "$V2D"
}

case "${1:-}" in
  start) start_sinks; start_v16; start_v2 ;;
  stop) stop_v16; stop_v2; stop_sinks ;;
  restart) stop_v16; stop_v2; start_sinks; start_v16; start_v2 ;;
  build) cd $R/yona && ./gradlew bootJar -q 2>&1 | grep -E "FAIL|error:" || true ;;
  snapshot) stop_v16; stop_v2; save_state "$2"; start_sinks; start_v16; start_v2 ;;
  restore) stop_v16; stop_v2; restore_state "$2"; start_sinks; : > "$WORK/mails-v16.jsonl"; : > "$WORK/mails-v2.jsonl"; : > "$WORK/hooks-v16.jsonl"; : > "$WORK/hooks-v2.jsonl"; start_v16; start_v2 ;;
  *) echo "사용법: $0 start|stop|restart|build|snapshot <이름>|restore <이름>"; exit 1 ;;
esac
