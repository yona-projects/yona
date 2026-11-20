#!/usr/bin/env bash
# 진짜 Yona jar로 일반 사용자(서비스 없음, H2, 같은 폴더의 jar = 폐쇄 환경 방식) 설치를 끝까지 확인한다:
# 설치, 시작, 상태 확인(UP), 결과 문서, 시작 스크립트의 중지, 제거.
# 사용법: user-mode-e2e.sh <yona jar 경로> [이미지, 기본 eclipse-temurin:21-jre]
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
YI_DIR="$(cd "$HERE/../.." && pwd)"
JAR="${1:?yona jar 경로}"
IMAGE="${2:-eclipse-temurin:21-jre}"
VERSION_NAME="$(basename "$JAR" .jar)"; VERSION_NAME="${VERSION_NAME#yona-}"
docker run --rm --user 1000:1000 -e HOME=/tmp/home \
  -v "$YI_DIR:/src:ro" -v "$JAR:/jar/yona-$VERSION_NAME.jar:ro" "$IMAGE" bash -c '
set -u
mkdir -p /tmp/home /tmp/b
cp -R /src/. /tmp/b/
rm -f /tmp/b/yona-*.jar
ln -s /jar/yona-'"$VERSION_NAME"'.jar /tmp/b/yona-'"$VERSION_NAME"'.jar
sha256sum /jar/yona-'"$VERSION_NAME"'.jar | sed "s#/jar/##" > /tmp/b/yona-'"$VERSION_NAME"'.jar.sha256
fail=0
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1: 기대 [$2] 실제 [$3]"; fail=1; fi; }

export LANG=C LC_ALL=C YI_HEALTH_TIMEOUT=240
echo "== 설치"
/tmp/b/yona-install.sh install --yes --offline --version=v'"$VERSION_NAME"' --install-dir=/tmp/home/yona --data-dir=/tmp/home/yona-data \
  --service=false --db=h2 --port=18090 > /tmp/install.log 2>&1
rc=$?
tail -12 /tmp/install.log | sed "s/^/    /"
check "설치 종료 코드" "0" "$rc"
check "jar 설치" "yes" "$([ -f /tmp/home/yona/yona.jar ] && echo yes || echo no)"
check "설정 파일" "yes" "$([ -f /tmp/home/yona/conf/application.yml ] && echo yes || echo no)"
check "결과 문서" "yes" "$([ -f /tmp/home/yona/INSTALL-REPORT.md ] && echo yes || echo no)"
check "설치 기록(사용자 영역)" "yes" "$([ -f /tmp/home/.local/state/yona-install/install.rec ] && echo yes || echo no)"
echo "== 실행 중인지"
check "상태 확인" "UP" "$(curl -fsS http://127.0.0.1:18090/-_-actuator/health | sed -n "s/.*\"status\":\"\\([A-Z]*\\)\".*/\\1/p" | head -1)"
check "pid 파일" "yes" "$([ -f /tmp/home/yona-data/yona.pid ] && echo yes || echo no)"
echo "== 같은 버전을 다시 설치하면 알리고 끝낸다"
out="$(/tmp/b/yona-install.sh install --yes --offline --version=v'"$VERSION_NAME"' 2>&1)"
check "이미 설치됨" "yes" "$(echo "$out" | grep -q "already installed" && echo yes || echo no)"
echo "== 중지"
/tmp/home/yona/bin/yona-stop.sh | sed "s/^/    /"
sleep 1
check "중지 뒤 상태 확인 실패" "down" "$(curl -fsS --max-time 3 http://127.0.0.1:18090/-_-actuator/health >/dev/null 2>&1 && echo up || echo down)"
echo "== 제거"
/tmp/b/yona-install.sh uninstall --yes 2>&1 | tail -3 | sed "s/^/    /"
check "설치 폴더 제거" "no" "$([ -e /tmp/home/yona/yona.jar ] && echo yes || echo no)"
check "데이터 폴더 보존" "yes" "$([ -d /tmp/home/yona-data ] && echo yes || echo no)"
check "기록 제거" "no" "$([ -f /tmp/home/.local/state/yona-install/install.rec ] && echo yes || echo no)"
[ "$fail" -eq 0 ] && echo "일반 사용자 설치 통합 시험 통과" || echo "일반 사용자 설치 통합 시험 실패"
exit "$fail"
'
