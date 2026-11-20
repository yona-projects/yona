# shellcheck shell=bash
# 사전 점검: 설치나 내려받기를 시작하기 전에 모든 점검을 모아서 한 번에 보인다(하나씩 실패하며 일부만 설치된 상태를 막는다).
# 점검 함수는 문제를 `FAIL|코드|상세` 또는 `WARN|코드|상세` 줄로 출력하고, FAIL이 있으면 실패한다.
# 외부 명령은 시험에서 PATH 앞의 가짜 실행 파일로 대신할 수 있다.

# 필요한 JDK의 주 버전이다.
YI_MIN_JAVA=21

# `java -version`의 첫 출력에서 주 버전을 읽는다(`1.8.0`은 8). 사용법: java_major_of <출력 문자열>
java_major_of() {
  local v major minor
  v="$(printf '%s' "$1" | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -1)"
  major=${v%%.*}
  if [ "$major" = "1" ]; then
    minor=${v#*.}
    major=${minor%%.*}
  fi
  major=${major%%[!0-9]*}
  printf '%s' "$major"
}

# 경로의 java 실행 파일이 내는 주 버전을 돌려준다. 실행할 수 없으면 빈 값이다.
_yi_java_major() {
  [ -x "$1" ] || return 0
  java_major_of "$("$1" -version 2>&1 | head -1)"
}

# JDK 21 이상을 찾는다. 후보는 `env.java`, `JAVA_HOME`, PATH의 java, SDKMAN의 현재 JDK이다.
# 찾으면 경로를 `env.java`에 담고 `env.need_jdk`를 false로 둔다. 없으면 FAIL을 출력한다.
pf_java() {
  local cand path major best=""
  for cand in "$(ans_get env.java)" "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$([ "${YI_NO_PATH_JAVA:-}" = "1" ] || command -v java 2>/dev/null)" "${HOME:-}/.sdkman/candidates/java/current/bin/java"; do
    [ -n "$cand" ] || continue
    path="$cand"
    major="$(_yi_java_major "$path")"
    [ -n "$major" ] || continue
    if [ "$major" -ge "$YI_MIN_JAVA" ]; then
      ans_set env.java "$path"
      ans_set env.need_jdk false
      return 0
    fi
    best="$major"
  done
  ans_set env.need_jdk true
  if [ -n "$best" ]; then
    printf 'FAIL|java|JDK %s found, but %s or later is required\n' "$best" "$YI_MIN_JAVA"
  else
    printf 'FAIL|java|JDK %s or later was not found\n' "$YI_MIN_JAVA"
  fi
  return 1
}

# 포트가 이미 쓰이고 있으면 참이다(127.0.0.1에 연결되는지로 본다). 사용법: port_in_use <포트>
port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

# 웹 포트, 내장 SSH 포트, 함께 설치하는 DB 포트가 비어 있는지 점검한다.
pf_ports() {
  local rc=0 port
  port="$(ans_get web.port 8080)"
  if port_in_use "$port"; then printf 'FAIL|port|%s\n' "$port"; rc=1; fi
  if [ "$(ans_get ssh.mode)" = "builtin" ]; then
    port="$(ans_get ssh.port 2222)"
    if port_in_use "$port"; then printf 'FAIL|port|%s\n' "$port"; rc=1; fi
  fi
  if [ "$(ans_get db.install)" = "together" ]; then
    port="$(ans_get db.port "$(db_default_port "$(ans_get db.type)")")"
    if [ "$port" != "0" ] && port_in_use "$port"; then printf 'FAIL|port|%s\n' "$port"; rc=1; fi
  fi
  return "$rc"
}

# 경로에서 가장 가까운 기존 상위 폴더를 돌려준다.
_yi_existing_ancestor() {
  local p="$1"
  while [ -n "$p" ] && [ ! -e "$p" ]; do
    p="$(dirname "$p")"
    [ "$p" = "." ] && p="/"
  done
  printf '%s' "${p:-/}"
}

# 경로를 지금 사용자가 만들거나 쓸 수 있으면 참이다.
path_writable() {
  [ -w "$(_yi_existing_ancestor "$1")" ]
}

# 설치 폴더와 데이터 폴더의 여유 공간을 점검한다(1GiB 미만이면 실패).
pf_disk() {
  local rc=0 p avail
  for p in "$(ans_get install_dir)" "$(ans_get data_dir)"; do
    [ -n "$p" ] || continue
    avail="$(df -Pk "$(_yi_existing_ancestor "$p")" 2>/dev/null | awk 'NR == 2 { print $4 }')"
    if [ -n "$avail" ] && [ "$avail" -lt 1048576 ]; then
      printf 'FAIL|disk|%s %sKB\n' "$p" "$avail"
      rc=1
    fi
  done
  return "$rc"
}

# 메모리(MB)가 1GiB 미만이면 경고한다(막지 않는다). 사용법: pf_memory_check <MB>
pf_memory_check() {
  if [ "$1" -lt 1024 ]; then
    printf 'WARN|memory|%sMB\n' "$1"
  fi
}

# 이 컴퓨터의 메모리를 점검한다.
pf_memory() {
  local mb=""
  if [ -r /proc/meminfo ]; then
    mb="$(awk '/MemTotal/ { print int($2 / 1024) }' /proc/meminfo)"
  elif command -v sysctl >/dev/null 2>&1; then
    mb="$(( $(sysctl -n hw.memsize 2>/dev/null || echo 0) / 1048576 ))"
  fi
  [ -n "$mb" ] && [ "$mb" -gt 0 ] && pf_memory_check "$mb"
  return 0
}

# 선택한 작업에 관리자 권한이 필요하면 참이다(서비스 등록, 시스템 패키지, sshd 연동, 쓸 수 없는 경로, 1024 미만 포트).
needs_privilege() {
  [ "$(ans_get service.register)" = "true" ] && return 0
  [ "$(ans_get ssh.mode)" = "system" ] && return 0
  [ "$(ans_get db.install)" = "together" ] && [ "$(ans_get db.method)" = "package" ] && return 0
  [ "$(ans_get install_jdk)" = "true" ] && [ "$(ans_get env.need_jdk)" = "true" ] && return 0
  path_writable "$(ans_get install_dir)" || return 0
  path_writable "$(ans_get data_dir)" || return 0
  [ "$(ans_get web.port 8080)" -lt 1024 ] && return 0
  [ "$(ans_get ssh.mode)" = "builtin" ] && [ "$(ans_get ssh.port 2222)" -lt 1024 ] && return 0
  return 1
}

# 격상이 필요한데 root도 sudo도 없으면 실패한다. 비대화형이면 비밀번호 없는 sudo(`sudo -n`)가 되어야 한다.
pf_privilege() {
  needs_privilege || return 0
  [ "${YI_EUID:-$(id -u)}" = "0" ] && return 0
  if [ "${YI_NO_SUDO:-}" = "1" ] || ! command -v sudo >/dev/null 2>&1; then
    printf 'FAIL|privilege|root or sudo is required\n'
    return 1
  fi
  if [ "$(ans_get yes)" = "true" ] && ! sudo -n true 2>/dev/null; then
    printf 'FAIL|privilege|sudo needs a password but the run is non-interactive\n'
    return 1
  fi
  return 0
}

# 파일의 권한(8진수)을 돌려준다(GNU와 BSD의 `stat` 모두).
file_mode() {
  stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1" 2>/dev/null
}

# 비밀번호 파일을 점검한다: 있어야 하고, 소유자만 읽도록 하고(넓으면 경고), 내용이 올바른 문자여야 한다.
pf_secret_files() {
  local rc=0 key file mode check_content
  for key in db.password_file db.admin_password_file mail.password_file; do
    ans_has "$key" || continue
    case "$key" in
      mail.password_file) [ "$(ans_get mail.enabled)" = "true" ] || continue ;;
      *) [ "$(ans_get db.type)" = "h2" ] && continue ;;
    esac
    file="$(ans_get "$key")"
    if [ ! -f "$file" ] || [ ! -r "$file" ]; then
      printf 'FAIL|secret-file|%s\n' "$key"
      rc=1
      continue
    fi
    mode="$(file_mode "$file")"
    case "$mode" in
      *[0-7][1-7][0-7] | *[0-7][0-7][1-7]) printf 'WARN|secret-file-mode|%s %s\n' "$key" "$mode" ;;
    esac
    check_content="$(read_secret_file "$file")"
    if ! password_ok "$check_content"; then
      printf 'FAIL|secret-file-content|%s\n' "$key"
      rc=1
    fi
  done
  return "$rc"
}

# 컨테이너로 DB를 설치할 때 docker나 podman이 있고 응답하는지 점검한다. 쓸 도구를 `env.containers`에 담는다.
pf_container() {
  [ "$(ans_get db.install)" = "together" ] && [ "$(ans_get db.method)" = "container" ] || return 0
  if [ "${YI_NO_DOCKER:-}" != "1" ] && command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    ans_set env.containers docker
    return 0
  fi
  if [ "${YI_NO_PODMAN:-}" != "1" ] && command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; then
    ans_set env.containers podman
    return 0
  fi
  printf 'FAIL|container|docker or podman is not available\n'
  return 1
}

# 외부 접속(GitHub)이 되는지 점검한다. 폐쇄 환경이면 같은 폴더의 파일을 쓰라고 안내한다.
pf_network() {
  [ "$(ans_get offline)" = "true" ] && return 0
  if ! curl -fsSI --max-time 8 https://api.github.com >/dev/null 2>&1; then
    printf 'WARN|network|github is not reachable; use --offline with the files in this directory\n'
    return 1
  fi
  return 0
}

# 모든 점검을 돌려 출력을 모으고 FAIL이 있으면 실패한다. 사용법: preflight_run
preflight_run() {
  local rc=0 out
  for check in pf_java pf_ports pf_disk pf_memory pf_privilege pf_secret_files pf_container; do
    out="$($check)" || true
    [ -n "$out" ] && printf '%s\n' "$out"
    case "$out" in *FAIL\|*) rc=1 ;; esac
  done
  # pf_java와 pf_container는 값을 현재 셸에 남겨야 하므로 한 번 더 부른다(출력은 버린다).
  pf_java >/dev/null 2>&1 || true
  pf_container >/dev/null 2>&1 || true
  return "$rc"
}
