#!/usr/bin/env bash
# 적용 스크립트의 컨테이너 DB 단계를 실제 docker로 확인한다(PostgreSQL 16, MariaDB 12.3.3): 컨테이너를 127.0.0.1에만 공개해 띄우고,
# 관리자 비밀번호로 데이터베이스와 사용자를 만들고, 만든 사용자로 접속되는지, 실패하면 컨테이너와 볼륨이 지워지는지 본다.
# 사용법: db-container.sh   (docker 필요. 일반 사용자로 돌린다)
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
YI_DIR="$(cd "$HERE/../.." && pwd)"
export YI_DIR
# shellcheck disable=SC1091
. "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/answers.sh"; . "$YI_DIR/lib/render.sh"; . "$YI_DIR/lib/apply.sh"
fail=0
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1: 기대 [$2] 실제 [$3]"; fail=1; fi; }
W="$(mktemp -d)"; trap 'docker rm -fv yona-db >/dev/null 2>&1; docker volume rm yona-db-data >/dev/null 2>&1; rm -rf "$W"' EXIT
printf 'User-Pw-1!\n' > "$W/db"; printf 'Admin-Pw-2!\n' > "$W/admin"; chmod 600 "$W"/db "$W"/admin

run_db() { # 종류 호스트포트 사용자접속명령
  local type="$1" hostport="$2"
  ans_clear
  ans_set env.os linux; ans_set service.register false; ans_set install_dir "$W/inst"; ans_set data_dir "$W/data"; ans_set version v2.0.0
  ans_set db.type "$type"; ans_set db.install together; ans_set db.method container; ans_set env.containers docker; ans_set db.port "$hostport"
  ans_set db.name yona; ans_set db.user yona; ans_set db.password_file "$W/db"; ans_set db.admin_password_file "$W/admin"
  ans_set env.stage "$W/stage"; ans_set env.record_dir "$W/rec"
  rm -rf "$W/stage" "$W/inst" "$W/data" "$W/rec"; mkdir -p "$W/stage/conf" "$W/stage/bin"
  printf 'jar' > "$W/stage/yona.jar"; printf 'a: 1\n' > "$W/stage/conf/application.yml"; printf '#!/bin/sh\n' > "$W/stage/bin/yona-start.sh"
  render_db_sql '%' > "$W/stage/db.sql"; render_db_drop_sql '%' > "$W/stage/db-drop.sql"
  ( umask 077; render_db_env > "$W/stage/db.env" )
  render_apply_script > "$W/apply.sh"
}

echo "== PostgreSQL 16"
run_db postgresql 55432
bash "$W/apply.sh" > "$W/out.log" 2>&1; check "적용 종료 코드" "0" "$?"
check "바깥 포트는 55432, 안쪽은 5432" "55432" "$(docker inspect yona-db --format '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}')"
check "공개 주소가 127.0.0.1이다" "127.0.0.1" "$(docker inspect yona-db --format '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostIp}}')"
check "만든 사용자로 접속한다" "1" "$(docker exec -e PGPASSWORD='User-Pw-1!' yona-db psql -h 127.0.0.1 -U yona -d yona -tAc 'select 1' 2>/dev/null)"
check "데이터베이스 인코딩 UTF8" "UTF8" "$(docker exec yona-db psql -U postgres -tAc "select pg_encoding_to_char(encoding) from pg_database where datname='yona'" 2>/dev/null)"
check "명령줄(docker inspect)에 비밀번호가 없다" "0" "$(docker inspect yona-db --format '{{.Args}} {{.Config.Cmd}}' | grep -c 'Admin-Pw-2\|User-Pw-1')"
bash "$W/apply.sh" rollback >/dev/null 2>&1
check "되돌리면 컨테이너가 없다" "0" "$(docker ps -a --format '{{.Names}}' | grep -c '^yona-db$')"
check "되돌리면 볼륨도 없다" "0" "$(docker volume ls --format '{{.Name}}' | grep -c '^yona-db-data$')"

echo "== MariaDB 12.3.3 (컨테이너 안의 접속은 호스트 %)"
run_db mariadb 53306
bash "$W/apply.sh" > "$W/out2.log" 2>&1; check "적용 종료 코드" "0" "$?"
check "만든 사용자로 접속한다" "1" "$(docker exec yona-db mariadb -h 127.0.0.1 -uyona -p'User-Pw-1!' -N -e 'select 1' 2>/dev/null)"
check "utf8mb4_bin 데이터베이스" "utf8mb4 utf8mb4_bin" "$(docker exec yona-db mariadb -uroot -p'Admin-Pw-2!' -N -e "select default_character_set_name, default_collation_name from information_schema.schemata where schema_name='yona'" 2>/dev/null | tr '\t' ' ')"
check "관리자(root) 비밀번호로 접속한다" "1" "$(docker exec yona-db mariadb -uroot -p'Admin-Pw-2!' -N -e 'select 1' 2>/dev/null)"
check "잘못된 비밀번호는 거절된다" "denied" "$(docker exec yona-db mariadb -uyona -p'wrong' -e 'select 1' >/dev/null 2>&1 && echo ok || echo denied)"
bash "$W/apply.sh" rollback >/dev/null 2>&1
check "되돌리면 컨테이너가 없다" "0" "$(docker ps -a --format '{{.Names}}' | grep -c '^yona-db$')"
[ "$fail" -eq 0 ] && echo "컨테이너 DB 통합 시험 통과" || echo "컨테이너 DB 통합 시험 실패"
exit "$fail"
