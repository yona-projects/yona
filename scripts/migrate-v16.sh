#!/usr/bin/env bash
# v1.6 데이터베이스를 이어받을 때 필요한 설정 변환과 데이터 보정을 한 번에 돌린다.
#   설정) v1.6 conf/application.conf를 v2 속성 파일로 변환(--v16-conf를 줄 때)
#   데이터) 0) 범위 문자열 이관  1) 풀 리퀘스트 번호 정리  2) 옛 사용자 토큰 이관  3) 리뷰어 설정 이관  4) 승인 리뷰 채우기
# 데이터 이관은 반드시 서버를 끈 상태에서 실행한다. 몇 번을 돌려도 결과가 같다. 서버와 같은 설정(DB 접속, yona.data-dir)을 써야 한다.
#
# 사용법: scripts/migrate-v16.sh <yona.jar 경로> [--v16-conf=<application.conf>] [--v16-conf-out=<출력 속성 파일>]
#                                [--v16-conf-force] [--v16-conf-only] [서버를 시작할 때 쓰던 --옵션 ...]
#   --v16-conf=FILE      v1.6의 application.conf를 v2 속성 파일로 바꾸고, 그 파일을 설정으로 써서 이관한다(DB 접속과 비밀 키도 그 파일에서 읽는다).
#   --v16-conf-out=FILE  변환한 파일의 위치(기본: ./yona-v16.properties). 비밀번호가 들어 있어 소유자만 읽도록 만든다.
#   --v16-conf-force     출력 파일이 이미 있어도 덮어쓴다(없으면 중단한다).
#   --v16-conf-only      설정만 변환하고 끝낸다(내용을 확인하거나 고친 뒤 --v16-conf 없이 다시 돌리려는 경우).
#   예)  scripts/migrate-v16.sh build/libs/yona-2.0.0.jar --v16-conf=/opt/yona-1.6/conf/application.conf --yona.data-dir=/var/yona
#        scripts/migrate-v16.sh build/libs/yona-2.0.0.jar --spring.config.additional-location=file:/etc/yona/   (이미 설정이 있는 경우)
# 주의: --server.port는 주지 않는다(이 스크립트가 --server.port=0을 붙인다). Java 21이 필요하다(JAVA_HOME을 쓴다).
# 종료 코드: 0 성공, 1 실패(데이터 이관은 실패하면 아무것도 바꾸지 않는다), 2 사용법 오류
set -euo pipefail

if [ "$#" -lt 1 ] || [ ! -f "$1" ]; then
  echo "사용법: $0 <yona.jar 경로> [--v16-conf=<application.conf>] [서버 옵션 ...]" >&2
  exit 2
fi

JAR="$1"
shift

CONF=""
CONF_OUT="./yona-v16.properties"
CONF_FORCE=""
CONF_ONLY=""
ARGS=()
for arg in "$@"; do
  case "$arg" in
    --v16-conf=*) CONF="${arg#--v16-conf=}" ;;
    --v16-conf-out=*) CONF_OUT="${arg#--v16-conf-out=}" ;;
    --v16-conf-force) CONF_FORCE="--force" ;;
    --v16-conf-only) CONF_ONLY="1" ;;
    *) ARGS+=("$arg") ;;
  esac
done

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if [ -n "$CONF" ]; then
  # 설정 변환은 스프링과 데이터베이스를 열지 않는다(부트 jar의 PropertiesLauncher로 변환기만 실행한다)
  "$JAVA" -Dloader.main=dev.yona.domain.upgrade.V16ConfigMain -cp "$JAR" \
    org.springframework.boot.loader.launch.PropertiesLauncher "$CONF" "$CONF_OUT" $CONF_FORCE
  if [ -n "$CONF_ONLY" ]; then exit 0; fi
  # 변환한 파일을 먼저 두어, 사용자가 직접 준 --spring.config.additional-location이 있으면 그것이 이긴다
  ARGS=("--spring.config.additional-location=optional:file:$CONF_OUT" "${ARGS[@]+"${ARGS[@]}"}")
elif [ -n "$CONF_ONLY" ]; then
  echo "--v16-conf-only에는 --v16-conf=<application.conf>가 필요합니다" >&2
  exit 2
fi

exec "$JAVA" -jar "$JAR" --yona.migrate=v16 --server.port=0 "${ARGS[@]+"${ARGS[@]}"}"
