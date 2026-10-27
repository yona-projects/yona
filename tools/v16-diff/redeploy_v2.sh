#!/bin/bash
# v2를 다시 빌드해 포트 19300에서 재기동한다. 데이터 폴더는 그대로 둔다(yona-diff-v2/data).
set -e
cd "$(dirname "$0")/../.."
./gradlew bootJar -q 2>&1 | grep -E "FAIL|error:" || true
OLD=$(pgrep -f "server\.port=1930[0]" || true)
if [ -n "$OLD" ]; then kill $OLD; sleep 4; fi
cd /home/jiho/yona-convert/yona-diff-v2
nohup /usr/lib/jvm/java-21-openjdk-amd64/bin/java -jar /home/jiho/yona-convert/yona/build/libs/yona-2.0.0-SNAPSHOT.jar \
  --server.port=19300 --yona.data-dir=/home/jiho/yona-convert/yona-diff-v2/data --yona.hostname=localhost:19300 --yona.social.login-support="github, google" > boot.log 2>&1 &
for i in $(seq 1 60); do
  sleep 3
  c=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://127.0.0.1:19300/ 2>/dev/null || true)
  [ "$c" = "200" ] && echo "v2 up" && exit 0
done
echo "v2 did not start"; exit 1
