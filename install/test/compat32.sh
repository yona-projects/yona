#!/usr/bin/env bash
# bash 3.2(macOS 기본 셸)에서 라이브러리가 같은 결과를 내는지 확인하는 호환 시험이다.
# `run-all.sh`가 bash 3.2 컨테이너 안에서 실행한다. 이 컴퓨터의 bash에서도 돌려 결과가 같음을 확인한다.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
YI_DIR="$(cd "$HERE/.." && pwd)"
export YI_DIR
fail=0
check() { # 설명 기대값 실제값
  if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1: 기대 [$2] 실제 [$3]"; fail=1; fi
}

echo "bash ${BASH_VERSION}"
for f in "$YI_DIR"/lib/*.sh; do
  # shellcheck disable=SC1090
  . "$f" || { echo "  FAIL 불러오기: $f"; fail=1; }
done

# 공통
ans_set db.admin-user root
check "ans_get" "root" "$(ans_get db.admin_user)"
check "ans_get 기본값" "x" "$(ans_get none x)"

# 메시지와 언어
msg_load ko
check "msg ko" "8080번 포트는 이미 사용 중입니다." "$(msg port.in_use 8080)"
check "detect_lang ko" "ko" "$(detect_lang '' '' ko_KR.UTF-8)"
check "detect_lang en" "en" "$(detect_lang '' '' C)"

# 답
ans_clear
answers_load --db=postgresql --port 9090 --yes
check "옵션 읽기" "postgresql:9090:true" "$(ans_get db.type):$(ans_get web.port):$(ans_get yes)"
check "필수 항목" "version install_dir data_dir service.register db.install" "$(answers_required install | tr '\n' ' ' | sed 's/ $//')"
ans_clear
ans_set web.port 70000
answers_validate >/dev/null
check "검증 실패" "1" "$?"

# 렌더링(있는 경우): 이 컴퓨터의 bash가 만든 골든 파일과 같아야 한다.
if [ -f "$YI_DIR/lib/render.sh" ] && [ -d "$YI_DIR/scenarios" ] && [ -f "$YI_DIR/test/scenarios.sh" ]; then
  # shellcheck disable=SC1091
  . "$YI_DIR/test/scenarios.sh"
  scenarios_check_all || fail=1
fi

[ "$fail" -eq 0 ] && echo "호환 시험 통과" || echo "호환 시험 실패"
exit "$fail"
