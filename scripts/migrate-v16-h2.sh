#!/usr/bin/env bash
# v1.6가 H2(`yona.h2.db`)를 쓰던 설치를 v2로 옮긴다. v2의 H2 2.x는 v1.6의 H2 1.3 파일을 읽지 못하므로 DB를 그대로 이어받을 수 없다. 이 스크립트가 아래를 한 번에 한다.
#   1) v1.6의 H2 파일을 옛 H2 jar(v1.6 설치의 lib/com.h2database.h2-1.3.176.jar)로 읽어 데이터 가져오기 형식의 JSON으로 바꾼다(tools/h2-export). v1.6을 켤 필요도 관리자 로그인도 필요 없다.
#   2) scripts/migrate-v16.sh로 그 파일을 v2의 DB에 넣고(사이트 관리의 데이터 가져오기와 같은 코드) 이어서 데이터 보정을 돌린다. 설정 변환(--v16-conf)도 같이 쓸 수 있다.
# v1.6 서버는 꺼져 있어야 하고 원본 H2 파일은 바뀌지 않는다(복사본을 읽는다). 대상 v2 DB의 테이블은 비우고 교체하므로 새 DB에서 쓴다. 몇 번을 돌려도 결과가 같다.
#
# 사용법: scripts/migrate-v16-h2.sh <yona.jar 경로> --v16-dir=<v1.6 설치 폴더> --yes [--v16-conf=<application.conf>] [서버를 시작할 때 쓰던 --옵션 ...]
#   --v16-dir=DIR       v1.6 설치 폴더. DIR/yona.h2.db와 DIR/lib/com.h2database.h2-1.3.176.jar를 쓴다.
#   --h2-file=FILE      H2 파일을 따로 지정한다(yona.h2.db 또는 확장자를 뺀 경로). 주면 --v16-dir 없이도 된다.
#   --h2-jar=FILE       옛 H2 jar를 따로 지정한다(H2 1.3.x).
#   --keep-export=FILE  변환한 JSON을 이 위치에 남긴다(기본은 임시 파일이고 끝나면 지운다). 비밀번호 해시가 들어 있어 소유자만 읽도록 만든다.
#   --yes               대상 DB의 테이블을 비우고 교체하는 것을 확인한다(없으면 시작하지 않는다).
#   그 밖의 옵션(--v16-conf=..., --yona.data-dir=..., --spring.datasource.url=... 등)은 scripts/migrate-v16.sh로 그대로 넘긴다.
# 환경 변수: V16_TIMEZONE  옛 시각을 읽는 시간대(예: Asia/Seoul). v1.6 서버가 돌던 시간대와 같아야 한다. 비우면 이 컴퓨터의 시간대이다.
#            TOOL_SRC      변환기 소스(H2Export.java)의 위치. 기본은 이 저장소의 tools/h2-export/H2Export.java.
# 주의: 변환기를 소스로 바로 실행하므로 JDK 21(JAVA_HOME)이 필요하다(JRE만으로는 안 된다).
# 종료 코드: 0 성공, 1 실패, 2 사용법이나 입력 오류(아무것도 바꾸지 않음)
set -euo pipefail

die() { echo "$1" >&2; exit 2; }

if [ "$#" -lt 1 ] || [ ! -f "$1" ]; then
  die "사용법: $0 <yona.jar 경로> --v16-dir=<v1.6 설치 폴더> --yes [--v16-conf=<application.conf>] [서버 옵션 ...]"
fi
JAR="$1"
shift

V16_DIR=""; H2_FILE=""; H2_JAR=""; KEEP=""; YES=""
ARGS=()
for arg in "$@"; do
  case "$arg" in
    --v16-dir=*) V16_DIR="${arg#--v16-dir=}" ;;
    --h2-file=*) H2_FILE="${arg#--h2-file=}" ;;
    --h2-jar=*) H2_JAR="${arg#--h2-jar=}" ;;
    --keep-export=*) KEEP="${arg#--keep-export=}" ;;
    --yes) YES="1" ;;
    *) ARGS+=("$arg") ;;
  esac
done

if [ -z "$H2_FILE" ]; then
  [ -n "$V16_DIR" ] || die "--v16-dir=<v1.6 설치 폴더> 또는 --h2-file=<yona.h2.db>가 필요합니다"
  H2_FILE="$V16_DIR/yona.h2.db"
fi
BASE="${H2_FILE%.h2.db}"
if [ ! -f "$BASE.h2.db" ]; then
  if [ -f "$BASE.mv.db" ]; then
    die "$BASE.mv.db는 이미 H2 2.x 형식입니다. 변환이 필요 없습니다(v2가 그대로 읽습니다). scripts/migrate-v16.sh를 쓰십시오."
  fi
  die "H2 파일이 없습니다: $BASE.h2.db"
fi

if [ -z "$H2_JAR" ]; then
  [ -n "$V16_DIR" ] || die "--v16-dir 없이는 옛 H2 jar를 찾을 수 없습니다. --h2-jar=<com.h2database.h2-1.3.176.jar>를 주십시오"
  for candidate in "$V16_DIR"/lib/com.h2database.h2-1.3*.jar; do [ -f "$candidate" ] && H2_JAR="$candidate" && break; done
fi
[ -n "$H2_JAR" ] && [ -f "$H2_JAR" ] || die "옛 H2 jar(H2 1.3.x)를 찾지 못했습니다. v1.6 설치 폴더의 lib/com.h2database.h2-1.3.176.jar를 --h2-jar로 주십시오"

TOOL="${TOOL_SRC:-$(cd "$(dirname "$0")/.." && pwd)/tools/h2-export/H2Export.java}"
[ -f "$TOOL" ] || die "변환기 소스를 찾지 못했습니다: $TOOL (TOOL_SRC로 지정할 수 있습니다)"

if [ -z "$YES" ]; then
  # 가져오기는 대상 DB의 테이블을 비우고 파일의 행으로 교체한다. 실수로 운영 DB를 비우지 않도록 확인을 받는다
  if [ -t 0 ]; then
    read -r -p "대상 v2 DB의 테이블을 비우고 v1.6 데이터로 교체합니다. 계속할까요? [y/N] " answer
    case "$answer" in y|Y|yes|YES) ;; *) die "취소했습니다." ;; esac
  else
    die "대상 v2 DB의 테이블을 비우고 교체합니다. 확인하려면 --yes를 주십시오."
  fi
fi

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if [ -n "$KEEP" ]; then EXPORT="$KEEP"; else EXPORT="$(mktemp "${TMPDIR:-/tmp}/yona-v16-h2-export.XXXXXX.json")"; fi
cleanup() { [ -n "$KEEP" ] || rm -f "$EXPORT"; }
trap cleanup EXIT
umask 077   # 내보낸 파일에 비밀번호 해시가 들어 있다

TZ_OPTION=()
if [ -n "${V16_TIMEZONE:-}" ]; then TZ_OPTION=("-Duser.timezone=$V16_TIMEZONE"); fi
echo "[v1.6 H2 이관] 1/2 옛 H2 파일을 읽어 JSON으로 바꿉니다(시간대: ${V16_TIMEZONE:-이 컴퓨터의 시간대}, 원본은 바뀌지 않습니다)"
set +e
"$JAVA" "${TZ_OPTION[@]+"${TZ_OPTION[@]}"}" -cp "$H2_JAR" "$TOOL" "$BASE.h2.db" "$EXPORT" > "$EXPORT.log" 2>&1
STATUS=$?
set -e
if [ "$STATUS" -ne 0 ]; then
  cat "$EXPORT.log" >&2; rm -f "$EXPORT.log"
  echo "옛 H2 파일을 읽지 못했습니다(종료 코드 $STATUS). v1.6 서버가 켜져 있거나 파일이 손상되었는지 확인하십시오." >&2
  exit 1
fi
echo "[v1.6 H2 이관] 변환한 테이블: $(grep -c ' ' "$EXPORT.log")개, 파일 크기 $(wc -c < "$EXPORT") 바이트"
rm -f "$EXPORT.log"

echo "[v1.6 H2 이관] 2/2 v2 DB에 넣고 데이터 보정을 돌립니다"
"$(cd "$(dirname "$0")" && pwd)/migrate-v16.sh" "$JAR" "--yona.migrate.import-file=$EXPORT" "${ARGS[@]+"${ARGS[@]}"}"
