# shellcheck shell=bash
# 설치 기록(`install.rec`)을 찾고 읽는다. 시스템 구성은 `/var/log/yona-install/`, 일반 사용자 구성은
# `~/.local/state/yona-install/`에 기록이 있다. "이미 설치됨" 판단, 업그레이드, 제거가 이 기록을 본다.

# 시스템 구성의 기록 폴더이다. 시험에서 `YI_SYSTEM_RECORD_DIR`로 바꾼다.
record_system_dir() {
  printf '%s' "${YI_SYSTEM_RECORD_DIR:-/var/log/yona-install}"
}

# 일반 사용자 구성의 기록 폴더이다.
record_user_dir() {
  printf '%s' "${HOME:-}/.local/state/yona-install"
}

# 설치 기록 파일의 경로를 돌려준다(시스템, 사용자 순). 없으면 실패한다.
record_find() {
  local dir
  for dir in "$(record_system_dir)" "$(record_user_dir)"; do
    if [ -f "$dir/install.rec" ]; then
      printf '%s' "$dir/install.rec"
      return 0
    fi
  done
  return 1
}

# 기록에서 값을 읽는다. 사용법: record_get <기록 파일> <키>
record_get() {
  sed -n "s/^$2=//p" "$1" | head -1
}

# 설치된 버전과 고른 버전을 견주어 상태를 돌려준다: new(설치된 것 없음), same, upgrade, downgrade.
# 사용법: install_state <설치된 버전(없으면 빈 값)> <고른 버전>
install_state() {
  if [ -z "$1" ]; then
    printf 'new'
    return 0
  fi
  case "$(version_cmp "$2" "$1")" in
    0) printf 'same' ;;
    1) printf 'upgrade' ;;
    *) printf 'downgrade' ;;
  esac
}

# 정식 릴리스에서 사전 배포(rc 등)로 올리는 업그레이드이면 yes이다(확인을 한 번 더 받는다).
# 사용법: upgrade_needs_confirm <설치된 버전> <고른 버전>
upgrade_needs_confirm() {
  case "$2" in
    *-*)
      case "$1" in
        *-*) printf 'no' ;;
        *) printf 'yes' ;;
      esac
      ;;
    *) printf 'no' ;;
  esac
}
