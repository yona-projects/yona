# shellcheck shell=bash
# Yona 설치 스크립트의 공통 도구.
#
# bash 3.2(macOS 기본 셸)에서도 돌아가도록 연관 배열, `${var^^}`, `mapfile`, `local -n`을 쓰지 않는다.
# 설치자가 준 답은 `A_<키>` 이름의 전역 변수로 보관한다(점과 하이픈은 밑줄로 바꾼다).

# 키 이름을 변수 이름에 쓸 수 있게 바꾼다. 예: `db.admin-user` -> `db_admin_user`
yi_key() {
  printf '%s' "$1" | tr '.-' '__'
}

# 답을 저장한다. 사용법: ans_set <키> <값>
ans_set() {
  printf -v "A_$(yi_key "$1")" '%s' "$2"
}

# 답을 읽는다. 값이 없으면 기본값(두 번째 인자)을 돌려준다. 사용법: ans_get <키> [기본값]
ans_get() {
  local name
  name="A_$(yi_key "$1")"
  local value="${!name-}"
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "${2-}"
  fi
}

# 답이 비어 있지 않으면 참이다.
ans_has() {
  local name
  name="A_$(yi_key "$1")"
  [ -n "${!name-}" ]
}

# 저장한 답을 모두 지운다(시험과 다시 시작할 때 쓴다).
ans_clear() {
  local name
  for name in $(compgen -A variable | grep '^A_'); do
    unset "$name"
  done
}

# 소문자로 바꾼다(bash 3.2에는 `${var,,}`가 없다).
yi_lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

# 오류 메시지를 표준 오류로 쓴다.
yi_err() {
  printf '%s\n' "$*" >&2
}
