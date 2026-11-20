# shellcheck shell=bash
# 설치자에게 묻는다. 입력은 터미널(`/dev/tty`)에서 읽는다(`curl | bash`로 시작하면 표준 입력이 스크립트 본문이다).
# 시험에서는 `YI_INPUT` 파일로 대신 준다. 이미 답이 있으면(옵션, 답 파일, 환경 변수) 묻지 않고,
# `--yes`(비대화형)이면 질문하지 않는다(기본값을 써도 되는 항목만 기본값을 쓴다).

# 비밀번호를 담을 임시 폴더이다. 시험에서 `YI_SECRET_DIR`로 바꾼다.
_yi_secret_dir() {
  if [ -z "${YI_SECRET_DIR:-}" ]; then
    YI_SECRET_DIR="$(mktemp -d "${TMPDIR:-/tmp}/yona-install-secrets.XXXXXX")"
    chmod 700 "$YI_SECRET_DIR"
  fi
  mkdir -p "$YI_SECRET_DIR"
  chmod 700 "$YI_SECRET_DIR"
  printf '%s' "$YI_SECRET_DIR"
}

# 입력 파일을 연다(파일 기술자 9). 열 수 없으면 실패한다.
prompt_open() {
  local input="${YI_INPUT:-/dev/tty}"
  [ "${YI_PROMPT_OPEN:-}" = "$input" ] && return 0
  exec 9<"$input" 2>/dev/null || return 1
  YI_PROMPT_OPEN="$input"
}

# 질문할 수 있으면 참이다: 비대화형(`--yes`)이 아니고 입력을 열 수 있어야 한다.
prompt_available() {
  [ "$(ans_get yes)" != "true" ] || return 1
  prompt_open
}

# 한 줄을 읽어 `YI_LINE`에 담는다. 입력이 끝났으면 오류로 끝낸다.
_yi_read_line() {
  if ! IFS= read -r -u 9 YI_LINE; then
    printf '%s\n' "$(msg err.input_closed)" >&2
    return 1
  fi
}

# 텍스트 질문. 사용법: ask_text <키> <메시지 키> [기본값] [optional]
# `optional`이면 비대화형에서 기본값을 쓴다.
ask_text() {
  local key="$1" msgkey="$2" default="${3:-}" optional="${4:-}"
  ans_has "$key" && return 0
  if ! prompt_available; then
    [ -n "$optional" ] && [ -n "$default" ] && ans_set "$key" "$default"
    return 0
  fi
  if [ -n "$default" ]; then
    printf '%s [%s] ' "$(msg "$msgkey")" "$default"
  else
    printf '%s ' "$(msg "$msgkey")"
  fi
  _yi_read_line || return 1
  if [ -n "$YI_LINE" ]; then
    ans_set "$key" "$YI_LINE"
  elif [ -n "$default" ]; then
    ans_set "$key" "$default"
  fi
  printf '\n'
}

# 선택 질문. 번호나 값을 받고, 목록에 없으면 다시 묻는다. 사용법: ask_choice <키> <메시지 키> "<선택지...>" [기본값] [optional]
# 선택지의 설명은 메시지 `choice.<키>.<값>`에서 읽는다.
ask_choice() {
  local key="$1" msgkey="$2" choices="$3" default="${4:-}" optional="${5:-}" i choice tries=0
  ans_has "$key" && return 0
  if ! prompt_available; then
    [ -n "$optional" ] && [ -n "$default" ] && ans_set "$key" "$default"
    return 0
  fi
  while [ "$tries" -lt 5 ]; do
    printf '%s\n' "$(msg "$msgkey")"
    i=1
    for choice in $choices; do
      printf '  %d) %s\n' "$i" "$(_yi_choice_label "$key" "$choice")"
      i=$((i + 1))
    done
    printf '> [%s] ' "$default"
    _yi_read_line || return 1
    [ -z "$YI_LINE" ] && YI_LINE="$default"
    if [ -n "$YI_LINE" ]; then
      i=1
      for choice in $choices; do
        if [ "$YI_LINE" = "$choice" ] || [ "$YI_LINE" = "$i" ]; then
          ans_set "$key" "$choice"
          printf '\n'
          return 0
        fi
        i=$((i + 1))
      done
    fi
    printf '%s\n' "$(msg prompt.invalid)"
    tries=$((tries + 1))
  done
  return 1
}

# 선택지 이름의 표시 문구이다. 메시지가 없으면 값을 그대로 쓴다.
_yi_choice_label() {
  local label
  label="$(msg "choice.$1.$2")"
  [ "$label" = "choice.$1.$2" ] && label="$2"
  printf '%s' "$label"
}

# 예 아니오 질문. y, yes, n, no를 받고 true, false로 담는다. 사용법: ask_yesno <키> <메시지 키> <기본값 true|false> [optional]
ask_yesno() {
  local key="$1" msgkey="$2" default="${3:-false}" optional="${4:-}" hint tries=0
  ans_has "$key" && return 0
  if ! prompt_available; then
    [ -n "$optional" ] && ans_set "$key" "$default"
    return 0
  fi
  if [ "$default" = "true" ]; then hint="Y/n"; else hint="y/N"; fi
  while [ "$tries" -lt 5 ]; do
    printf '%s [%s] ' "$(msg "$msgkey")" "$hint"
    _yi_read_line || return 1
    case "$(yi_lower "$YI_LINE")" in
      y | yes) ans_set "$key" true; printf '\n'; return 0 ;;
      n | no) ans_set "$key" false; printf '\n'; return 0 ;;
      '') ans_set "$key" "$default"; printf '\n'; return 0 ;;
    esac
    printf '%s\n' "$(msg prompt.invalid)"
    tries=$((tries + 1))
  done
  return 1
}

# 비밀번호 질문. 두 번 입력해 같아야 하고, 비어 있거나 허용되지 않는 문자가 있으면 다시 묻는다.
# 입력은 소유자만 읽는 파일(0600)로 저장하고 그 경로를 답에 담는다(명령줄이나 화면에 남기지 않는다).
# 사용법: ask_secret <파일 경로를 담을 키> <메시지 키>
ask_secret() {
  local key="$1" msgkey="$2" first second tries=0 file
  ans_has "$key" && return 0
  prompt_available || return 0
  while [ "$tries" -lt 5 ]; do
    printf '%s: ' "$(msg "$msgkey")"
    if [ -t 9 ]; then IFS= read -r -s -u 9 first || return 1; printf '\n'; else _yi_read_line || return 1; first="$YI_LINE"; fi
    if password_ok "$first"; then
      printf '%s (%s): ' "$(msg "$msgkey")" "$(msg prompt.again)"
      if [ -t 9 ]; then IFS= read -r -s -u 9 second || return 1; printf '\n'; else _yi_read_line || return 1; second="$YI_LINE"; fi
      if [ "$first" = "$second" ]; then
        file="$(_yi_secret_dir)/$(yi_key "$key")"
        ( umask 077; printf '%s\n' "$first" > "$file" )
        ans_set "$key" "$file"
        return 0
      fi
      printf '%s\n' "$(msg prompt.mismatch)"
    else
      printf '%s\n' "$(msg prompt.password_rule)"
    fi
    tries=$((tries + 1))
  done
  return 1
}
