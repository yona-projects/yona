#!/usr/bin/env bash
# compose 파일이 의존하는 동작을 같은 구성의 docker run으로 확인한다(docker compose가 없을 때의 대안):
#   비밀번호를 파일(/run/secrets/spring.datasource.password)로 주고 configtree로 읽는지, 실제 PostgreSQL과 MariaDB에 연결해 시작하는지,
#   환경 변수로 내장 SSH를 켜는지. compose 파일 자체를 실행하는 시험은 아니다.
# 사용법: test-compose-equivalent.sh <yona 이미지>
set -u
IMG="${1:?이미지}"
W="$(mktemp -d)"; chmod 755 "$W"
trap 'docker rm -fv yct-pg yct-maria yct-yona >/dev/null 2>&1; docker network rm yct-net >/dev/null 2>&1; rm -rf "$W"' EXIT
fail=0
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1: 기대 [$2] 실제 [$3]"; fail=1; fi; }
wait_up() { for i in $(seq 1 90); do [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$1/-_-actuator/health")" = "200" ] && return 0; sleep 2; done; return 1; }
printf 'Db-Pass-1!\n' > "$W/db_password"; printf 'Adm-Pass-2!\n' > "$W/db_admin_password"; chmod 644 "$W"/*   # 컨테이너 안의 비루트가 읽어야 한다(compose의 secrets도 읽기 가능으로 마운트한다)
docker network create yct-net >/dev/null

echo "== PostgreSQL 16 (비밀번호는 POSTGRES_PASSWORD_FILE, Yona는 configtree)"
docker run -d --name yct-pg --network yct-net --network-alias postgres -e POSTGRES_DB=yona -e POSTGRES_USER=yona \
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db_password -v "$W/db_password:/run/secrets/db_password:ro" postgres:16 >/dev/null
for i in $(seq 1 60); do docker exec yct-pg pg_isready -h 127.0.0.1 -U yona -d yona >/dev/null 2>&1 && break; sleep 2; done
docker run -d --name yct-yona --network yct-net -p 18096:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/yona -e SPRING_DATASOURCE_USERNAME=yona \
  -e SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/ -e YONA_DATA_DIR=/data \
  -e YONA_SSH_MINA_ENABLED=true -e YONA_SSH_MINA_PORT=2222 \
  -v "$W/db_password:/run/secrets/spring.datasource.password:ro" "$IMG" >/dev/null
check "PostgreSQL로 시작하고 상태 확인 통과" "yes" "$(wait_up 18096 && echo yes || echo no)"
check "스키마가 만들어졌다(테이블 수 > 50)" "yes" "$(n=$(docker exec yct-pg psql -h 127.0.0.1 -U yona -d yona -tAc "select count(*) from information_schema.tables where table_schema='public'" 2>/dev/null); [ "${n:-0}" -gt 50 ] && echo yes || echo no)"
check "내장 SSH 포트가 열렸다(환경 변수로 켬)" "yes" "$(docker exec yct-yona bash -c '(exec 3<>/dev/tcp/127.0.0.1/2222) 2>/dev/null && echo yes || echo no')"
check "틀린 비밀번호 파일이면 시작하지 못한다(파일이 실제로 쓰인다)" "no" "$(docker rm -fv yct-yona >/dev/null; printf 'wrong\n' > "$W/wrong"; chmod 644 "$W/wrong"; docker run -d --name yct-yona --network yct-net -p 18096:8080 -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/yona -e SPRING_DATASOURCE_USERNAME=yona -e SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/ -v "$W/wrong:/run/secrets/spring.datasource.password:ro" "$IMG" >/dev/null; for i in $(seq 1 25); do [ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18096/-_-actuator/health)" = "200" ] && { echo yes; exit; }; sleep 2; done; echo no)"
docker rm -fv yct-yona yct-pg >/dev/null 2>&1

echo "== MariaDB 12.3.3 (utf8mb4_bin)"
docker run -d --name yct-maria --network yct-net --network-alias mariadb -e MARIADB_DATABASE=yona -e MARIADB_USER=yona \
  -e MARIADB_PASSWORD_FILE=/run/secrets/db_password -e MARIADB_ROOT_PASSWORD_FILE=/run/secrets/db_admin_password \
  -v "$W/db_password:/run/secrets/db_password:ro" -v "$W/db_admin_password:/run/secrets/db_admin_password:ro" \
  mariadb:12.3.3 --character-set-server=utf8mb4 --collation-server=utf8mb4_bin >/dev/null
for i in $(seq 1 60); do docker exec yct-maria healthcheck.sh --connect --innodb_initialized >/dev/null 2>&1 && break; sleep 2; done
docker run -d --name yct-yona --network yct-net -p 18097:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mariadb://mariadb:3306/yona -e SPRING_DATASOURCE_USERNAME=yona \
  -e SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/ -e YONA_DATA_DIR=/data \
  -v "$W/db_password:/run/secrets/spring.datasource.password:ro" "$IMG" >/dev/null
check "MariaDB로 시작하고 상태 확인 통과" "yes" "$(wait_up 18097 && echo yes || echo no)"
check "데이터베이스 문자 집합과 정렬 규칙" "utf8mb4 utf8mb4_bin" "$(docker exec yct-maria mariadb -uroot -p'Adm-Pass-2!' -N -e "select default_character_set_name, default_collation_name from information_schema.schemata where schema_name='yona'" 2>/dev/null | tr '\t' ' ')"
check "스키마가 만들어졌다(테이블 수 > 50)" "yes" "$(n=$(docker exec yct-maria mariadb -uroot -p'Adm-Pass-2!' -N -e "select count(*) from information_schema.tables where table_schema='yona'" 2>/dev/null); [ "${n:-0}" -gt 50 ] && echo yes || echo no)"
[ "$fail" -eq 0 ] && echo "compose 동등 구성 시험 통과" || echo "compose 동등 구성 시험 실패"
exit "$fail"
