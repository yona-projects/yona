#!/usr/bin/env bash
# 컨테이너 이미지 시험: 비루트, 읽기 전용 루트, 새 볼륨의 소유권, 상태 확인, 정상 종료(143), 재시작 뒤 데이터 유지.
# 사용법: test-image.sh <이미지>
set -u
IMG="${1:?이미지}"
fail=0
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1: 기대 [$2] 실제 [$3]"; fail=1; fi; }
wait_up() { for i in $(seq 1 90); do [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$1/-_-actuator/health")" = "200" ] && return 0; sleep 2; done; return 1; }
docker rm -fv yona-img-test >/dev/null 2>&1; docker volume rm -f yona-img-test-data >/dev/null 2>&1
echo "== 비루트 + 읽기 전용 루트 + 새 볼륨(소유권은 이미지에서 이어받는다)"
docker run -d --name yona-img-test --read-only --tmpfs /tmp -v yona-img-test-data:/data -p 18095:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:h2:file:/data/db;DB_CLOSE_DELAY=-1' "$IMG" >/dev/null
check "상태 확인 통과" "yes" "$(wait_up 18095 && echo yes || echo no)"
check "실행 사용자" "10001" "$(docker exec yona-img-test id -u)"
check "루트 파일 시스템은 읽기 전용" "ro" "$(docker exec yona-img-test sh -c 'touch /x 2>/dev/null && echo rw || echo ro')"
check "데이터 폴더에 쓴다" "yes" "$(docker exec yona-img-test sh -c '[ -f /data/db.mv.db ] && echo yes || echo no')"
check "HEALTHCHECK가 healthy" "healthy" "$(for i in $(seq 1 40); do s=$(docker inspect yona-img-test --format '{{.State.Health.Status}}'); [ "$s" = healthy ] && break; sleep 5; done; echo "$s")"
echo "== 정상 종료"
s=$(date +%s); docker stop yona-img-test >/dev/null
check "종료 코드 143" "143" "$(docker inspect yona-img-test --format '{{.State.ExitCode}}')"
check "10초 안에 종료" "yes" "$([ $(( $(date +%s) - s )) -lt 10 ] && echo yes || echo no)"
echo "== 컨테이너를 지우고 다시 만들어도(볼륨 유지) 데이터가 남는다"
docker rm yona-img-test >/dev/null
docker run -d --name yona-img-test --read-only --tmpfs /tmp -v yona-img-test-data:/data -p 18095:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:h2:file:/data/db;DB_CLOSE_DELAY=-1' "$IMG" >/dev/null
check "다시 시작해도 통과" "yes" "$(wait_up 18095 && echo yes || echo no)"
check "데이터 파일 유지" "yes" "$(docker exec yona-img-test sh -c '[ -f /data/db.mv.db ] && echo yes || echo no')"
docker rm -fv yona-img-test >/dev/null; docker volume rm -f yona-img-test-data >/dev/null
[ "$fail" -eq 0 ] && echo "이미지 시험 통과" || echo "이미지 시험 실패"
exit "$fail"
