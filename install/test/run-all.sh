#!/usr/bin/env bash
# 설치 스크립트 시험을 한 번에 돌린다: shellcheck, bats(이 컴퓨터의 bash), bats(bash 3.2).
# 필요한 것: bats(환경 변수 BATS_BIN으로 위치를 준다), docker(shellcheck와 bash 3.2 이미지).
# 사용법: install/test/run-all.sh [bats 파일 ...]
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
BATS_BIN="${BATS_BIN:-bats}"
BATS_HOME="$(cd "$(dirname "$(readlink -f "$(command -v "$BATS_BIN")")")/.." && pwd)"
FILES=("$@")
[ "${#FILES[@]}" -eq 0 ] && FILES=("$HERE")
status=0

echo "== shellcheck"
docker run --rm -v "$ROOT:/mnt:ro" koalaman/shellcheck:stable --shell=bash -x \
  $(cd "$ROOT" && ls install/lib/*.sh install/*.sh 2>/dev/null | sed 's#^#/mnt/#') || status=1

echo "== bats (이 컴퓨터의 bash)"
"$BATS_BIN" "${FILES[@]}" || status=1

echo "== 호환 시험 (bash 3.2, macOS 기본 셸 버전)"
# bats는 한국어 시험 이름을 함수 이름으로 만드는데 bash 3.2는 이를 정의하지 못한다. 그래서 같은 라이브러리를
# bash 3.2에서 직접 돌려 이 컴퓨터의 bash가 만든 결과와 견준다.
docker run --rm -v "$ROOT:/repo:ro" -w /repo bash:3.2 bash install/test/compat32.sh || status=1

echo "== PowerShell (Pester, pwsh 컨테이너)"
if docker image inspect yona-pester >/dev/null 2>&1; then
  "$HERE/run-pester.sh" || status=1
else
  echo "  건너뜀: 이미지 yona-pester가 없습니다(README의 방법으로 만든다)"
fi

# 통합 시험(컨테이너에서 실제 설치)은 느리고 jar가 필요해 YONA_JAR를 줄 때만 돌린다.
if [ -n "${YONA_JAR:-}" ]; then
  echo "== 통합 시험: 적용 스크립트(root, 되돌리기)"
  "$HERE/integration/apply-in-container.sh" || status=1
  echo "== 통합 시험: 컨테이너 DB(PostgreSQL, MariaDB 12.3.3)"
  "$HERE/integration/db-container.sh" || status=1
  echo "== 통합 시험: 일반 사용자 설치(진짜 jar)"
  "$HERE/integration/user-mode-e2e.sh" "$YONA_JAR" || status=1
fi

exit "$status"
