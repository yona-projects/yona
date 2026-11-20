# shellcheck shell=bash
# 사용자에게 보이는 문구를 메시지 파일(`messages/ko.properties`, `messages/en.properties`)에서 읽는다.
# 같은 메시지 파일을 PowerShell 스크립트도 읽는다. 한국어에 없는 키는 영어 문구로 대신한다.

# 지원하는 언어이다.
YI_LANGS="ko en"

# 메시지가 있는 폴더이다. 시험에서 `YI_MESSAGES_DIR`로 바꾼다.
_yi_messages_dir() {
  printf '%s' "${YI_MESSAGES_DIR:-$YI_DIR/messages}"
}

# 파일 하나를 읽어 `M_<키>` 변수로 보관한다.
_yi_msg_read() {
  local file="$1" line key value
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      '' | '#'*) continue ;;
    esac
    key=${line%%=*}
    value=${line#*=}
    printf -v "M_$(yi_key "$key")" '%s' "$value"
  done <"$file"
}

# 언어의 메시지를 불러온다. 영어를 먼저 읽고 그 위에 고른 언어를 덮어써 빠진 키를 영어로 채운다.
# 사용법: msg_load <ko|en>
msg_load() {
  local dir
  dir="$(_yi_messages_dir)"
  _yi_msg_read "$dir/en.properties"
  if [ "$1" != "en" ]; then
    _yi_msg_read "$dir/$1.properties"
  fi
}

# 문구를 돌려준다. `{1}`, `{2}`는 인자로 바꾸고 `\n`은 줄바꿈으로 푼다. 없는 키는 키 이름을 돌려준다.
# 사용법: msg <키> [인자...]
msg() {
  local name
  name="M_$(yi_key "$1")"
  local text="${!name-}"
  if [ -z "$text" ]; then
    printf '%s' "$1"
    return 0
  fi
  shift
  # 인자에 역슬래시가 있어도 풀리지 않도록 줄바꿈 해석을 먼저 한다.
  text="$(printf '%b' "$text")"
  local i=1 arg
  for arg in "$@"; do
    # bash 5.2부터 치환 문자열의 `&`가 특별해지므로(일치한 부분으로 바뀐다) 인자를 따옴표로 감싸 막는다.
    text=${text//\{$i\}/"$arg"}
    i=$((i + 1))
  done
  printf '%s' "$text"
}

# 한 줄로 출력한다(끝에 줄바꿈). 사용법: say <키> [인자...]
say() {
  msg "$@"
  printf '\n'
}

# 시스템 언어를 읽는다. 환경 변수 `LC_ALL`, `LC_MESSAGES`, `LANG` 순으로 보고,
# 모두 비어 있으면 Mac의 `defaults read -g AppleLocale`을 본다.
system_locale() {
  local value="${LC_ALL:-}"
  [ -z "$value" ] && value="${LC_MESSAGES:-}"
  [ -z "$value" ] && value="${LANG:-}"
  if [ -z "$value" ] && command -v defaults >/dev/null 2>&1; then
    value="$(defaults read -g AppleLocale 2>/dev/null || true)"
  fi
  printf '%s' "$value"
}

# 사용할 언어를 정한다. 순서는 `--lang`, `YONA_INSTALL_LANG`, 시스템 언어(한국어이면 한국어), 영어이다.
# 지원하지 않는 값을 `--lang`이나 환경 변수로 주면 실패한다.
# 사용법: detect_lang <--lang 값> <환경 변수 값> <시스템 언어>
detect_lang() {
  local flag="$1" env="$2" sys="$3" choice
  for choice in "$flag" "$env"; do
    if [ -n "$choice" ]; then
      case " $YI_LANGS " in
        *" $choice "*) printf '%s' "$choice"; return 0 ;;
        *) return 1 ;;
      esac
    fi
  done
  case "$sys" in
    ko*) printf 'ko' ;;
    *) printf 'en' ;;
  esac
}

# 실행 중에 메시지 하나를 정한다(릴리스 이름처럼 파일에 없는 문구를 선택지 표시에 쓴다). 사용법: msg_set <키> <문구>
msg_set() {
  printf -v "M_$(yi_key "$1")" '%s' "$2"
}
