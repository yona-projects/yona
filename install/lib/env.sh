# shellcheck shell=bash
# 설치 환경의 사실을 읽는다: 운영체제, 배포본, 패키지 관리자, 설치한 사용자, 시간대, sshd 서비스 이름.
# 결과는 `env.*` 답으로 담고 산출물 생성과 계획이 읽는다. 시험에서는 `YI_UNAME`과 `YI_OS_RELEASE`로 바꾼다.

# `/etc/os-release`에서 값을 읽는다(따옴표는 지운다). 사용법: _yi_os_release_get <키> <파일>
_yi_os_release_get() {
  sed -n "s/^$1=//p" "$2" | head -1 | tr -d '"'
}

# 환경을 읽어 `env.*` 답으로 담는다.
detect_env() {
  local uname_s osrel id like tz link
  uname_s="$(${YI_UNAME:+echo $YI_UNAME} 2>/dev/null)"
  [ -n "$uname_s" ] || uname_s="$(uname -s)"
  osrel="${YI_OS_RELEASE:-/etc/os-release}"
  case "$uname_s" in
    Linux)
      ans_set env.os linux
      id=""; like=""
      if [ -f "$osrel" ]; then
        id="$(_yi_os_release_get ID "$osrel")"
        like="$(_yi_os_release_get ID_LIKE "$osrel")"
      fi
      ans_set env.distro "$id"
      case " $id $like " in
        *" debian "* | *" ubuntu "*) ans_set env.pkg apt; ans_set env.sshd_unit ssh ;;
        *" rhel "* | *" fedora "* | *" centos "* | *" rocky "* | *" almalinux "*) ans_set env.pkg dnf; ans_set env.sshd_unit sshd ;;
        *) ans_set env.pkg none; ans_set env.sshd_unit sshd ;;
      esac
      # systemd가 있으면 실제 유닛 이름을 확인한다(배포본 기본과 다를 수 있다).
      if command -v systemctl >/dev/null 2>&1; then
        if systemctl cat sshd >/dev/null 2>&1; then
          ans_set env.sshd_unit sshd
        elif systemctl cat ssh >/dev/null 2>&1; then
          ans_set env.sshd_unit ssh
        fi
      fi
      ;;
    Darwin)
      ans_set env.os mac
      ans_set env.distro macos
      ans_set env.pkg brew
      ;;
    *)
      ans_set env.os other
      ;;
  esac
  ans_set env.arch "$(uname -m)"
  ans_set env.user "${SUDO_USER:-$(id -un)}"
  tz="${TZ:-}"
  if [ -z "$tz" ] && [ -L /etc/localtime ]; then
    link="$(readlink /etc/localtime)"
    tz="${link#*zoneinfo/}"
  fi
  [ -n "$tz" ] && ans_set env.tz "$tz"
  return 0
}

# 기본 설치 폴더이다. 서비스를 등록하면 운영체제 관례의 시스템 경로, 등록하지 않으면 홈 폴더 아래(관리자 권한이 필요 없다).
# 사용법: default_install_dir <linux|mac|windows> <서비스 등록 여부 true/false>
default_install_dir() {
  if [ "$2" = "true" ]; then
    case "$1" in
      mac) printf '/usr/local/yona' ;;
      *) printf '/opt/yona' ;;
    esac
  else
    printf '%s/yona' "${HOME:-}"
  fi
}

# 기본 데이터 폴더이다. 사용법: default_data_dir <운영체제> <서비스 등록 여부>
default_data_dir() {
  if [ "$2" = "true" ]; then
    case "$1" in
      mac) printf '/usr/local/var/yona' ;;
      *) printf '/var/lib/yona' ;;
    esac
  else
    printf '%s/yona-data' "${HOME:-}"
  fi
}

# DB 종류별로 이 환경에서 쓸 수 있는 설치 방법을 돌려준다. 시스템 패키지는 Linux의 apt, dnf에서 PostgreSQL과 MariaDB만 지원한다
# (SQL Server와 Cubrid는 배포본 저장소에 없다). Mac은 컨테이너만 쓴다. H2는 설치할 서버가 없다.
# 사용법: db_methods <운영체제> <패키지 관리자> <DB 종류>
db_methods() {
  case "$3" in
    h2) printf '' ;;
    postgresql | mariadb)
      if [ "$1" = "linux" ] && { [ "$2" = "apt" ] || [ "$2" = "dnf" ]; }; then
        printf 'container package'
      else
        printf 'container'
      fi
      ;;
    *) printf 'container' ;;
  esac
}
