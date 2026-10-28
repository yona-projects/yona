#!/bin/bash
# 예약 작업 대조: (1) 오래된 알림 정리(`application.notification.keep-time`), (2) 임시 첨부 정리(`application.temporaryfiles.keep-up.time`).
# 데이터를 `data2`로 되돌리고, 서버를 멈춘 채 DB에서 `NEW_ISSUE` 알림의 날짜를 90일 앞으로 당긴 뒤 짧은 주기로 서버를 띄워 기다린다.
# 끝나면 DB의 알림 수와 첨부 수를 두 DB에서 읽어 견준다.
cd "$(dirname "$0")" || exit 1
R=/home/jiho/yona-convert
V16=$R/yona-v1.16-release/yona-1.16.0
V2D=$R/yona-diff-v2
WORK=$R/yona-diff-work
H1=$V16/lib/com.h2database.h2-1.3.176.jar
H2=$(find ~/.gradle/caches -name "h2-2.3.232.jar" | head -1)
J21=/usr/lib/jvm/java-21-openjdk-amd64
J8=/usr/lib/jvm/java-8-openjdk-amd64
sql16() { (cd "$V16" && $J8/bin/java -cp "$H1" org.h2.tools.Shell -url "jdbc:h2:file:$V16/yona;MODE=PostgreSQL;MV_STORE=FALSE;MVCC=FALSE" -user "" -password "" -sql "$1" 2>&1 | tail -n +1); }
sql2() { $J21/bin/java -cp "$H2" org.h2.tools.Shell -url "jdbc:h2:file:$V2D/db/yona" -user sa -password "" -sql "$1" 2>&1; }
./servers.sh restore data2 >/dev/null 2>&1
./servers.sh stop >/dev/null 2>&1
AGE="UPDATE notification_event SET created = DATEADD('DAY', -90, created) WHERE event_type = 'NEW_ISSUE'"
echo "-- 옛 알림으로 바꾼 행(v1.6 / v2)"; sql16 "$AGE" | tail -2; sql2 "$AGE" | tail -2
echo "-- 정리 전 알림 수(v1.6 / v2)"; sql16 "SELECT COUNT(*), SUM(CASE WHEN event_type='NEW_ISSUE' THEN 1 ELSE 0 END) FROM notification_event" | tail -3; sql2 "SELECT COUNT(*), SUM(CASE WHEN event_type='NEW_ISSUE' THEN 1 ELSE 0 END) FROM notification_event" | tail -3
printf '%s' "-Dapplication.notification.keep-time=30 -Dapplication.temporaryfiles.keep-up.time=20s" > "$WORK/cfg.v16"
printf '%s' "--yona.notification-keep-time=30 --yona.temporary-files-keep-up-time=20s" > "$WORK/cfg.v2"
./servers.sh start >/dev/null 2>&1
sleep 5
PYTHONPATH=scenarios python3 harness.py run sched1b --out /home/jiho/yona-convert/yona-diff-v2/report-sched1b.md --top 30 2>&1 | head -1   # 업로드 뒤 대기
sleep 70                                                       # 알림 정리는 시작 1분 뒤
PYTHONPATH=scenarios python3 harness.py run sched1a --out /home/jiho/yona-convert/yona-diff-v2/report-sched1a.md --top 30 2>&1 | head -1
./servers.sh stop >/dev/null 2>&1
echo "-- 정리 뒤 알림 수(v1.6 / v2)"; sql16 "SELECT COUNT(*), SUM(CASE WHEN event_type='NEW_ISSUE' THEN 1 ELSE 0 END) FROM notification_event" | tail -3; sql2 "SELECT COUNT(*), SUM(CASE WHEN event_type='NEW_ISSUE' THEN 1 ELSE 0 END) FROM notification_event" | tail -3
echo "-- 정리 뒤 사용자 임시 첨부 수(v1.6 / v2)"; sql16 "SELECT COUNT(*) FROM attachment WHERE container_type='USER'" | tail -3; sql2 "SELECT COUNT(*) FROM attachment WHERE container_type='USER'" | tail -3
: > "$WORK/cfg.v16"; : > "$WORK/cfg.v2"
./servers.sh restore data2 >/dev/null 2>&1
