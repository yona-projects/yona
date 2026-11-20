#!/usr/bin/env bats
# 사전 점검과 설치 기록 시험. 외부 명령(java, docker, sudo, curl)은 PATH 앞의 가짜 실행 파일로 대신한다.
load test_helper

setup() {
  setup_tmp
  . "$YI_DIR/lib/common.sh"
  . "$YI_DIR/lib/answers.sh"
  . "$YI_DIR/lib/render.sh"
  . "$YI_DIR/lib/preflight.sh"
  . "$YI_DIR/lib/record.sh"
  ans_clear
  mkdir -p "$TMP_DIR/bin"
  export PATH="$TMP_DIR/bin:$PATH"
  export HOME="$TMP_DIR/home"; mkdir -p "$HOME"
}
teardown() { teardown_tmp; }

stub() { # 이름 본문
  printf '#!/bin/sh\n%s\n' "$2" > "$TMP_DIR/bin/$1"; chmod +x "$TMP_DIR/bin/$1"
}

# --- JDK ---

@test "java -version 문자열에서 주 버전을 읽는다" {
  [ "$(java_major_of 'openjdk version "21.0.11" 2026-04-21')" = "21" ]
  [ "$(java_major_of 'openjdk version "17.0.15" 2025-04-15')" = "17" ]
  [ "$(java_major_of 'java version "1.8.0_492"')" = "8" ]
  [ "$(java_major_of 'openjdk version "25.0.5" 2026-07-21')" = "25" ]
}

@test "JDK 21 이상이 있으면 경로를 env.java에 담고 통과한다" {
  stub java 'echo "openjdk version \"21.0.2\" 2026-01-01" >&2'
  run pf_java
  [ "$status" -eq 0 ]
  pf_java >/dev/null
  [[ "$(ans_get env.java)" == */java ]]
}

@test "JDK가 낮으면 실패하고 필요한 버전을 알린다" {
  stub java 'echo "openjdk version \"17.0.15\" 2025-04-15" >&2'
  run pf_java
  [ "$status" -ne 0 ]
  [[ "$output" == *"FAIL|java|"* ]]
  [[ "$output" == *"17"* ]]
}

@test "JDK가 없으면 실패한다" {
  YI_NO_PATH_JAVA=1 JAVA_HOME="" run pf_java
  [ "$status" -ne 0 ]
  [[ "$output" == *"FAIL|java|"* ]]
}

@test "SDKMAN의 JDK도 찾는다" {
  mkdir -p "$HOME/.sdkman/candidates/java/current/bin"
  printf '#!/bin/sh\necho "openjdk version \\"21.0.11\\" 2026-04-21" >&2\n' > "$HOME/.sdkman/candidates/java/current/bin/java"
  chmod +x "$HOME/.sdkman/candidates/java/current/bin/java"
  YI_NO_PATH_JAVA=1 JAVA_HOME="" run pf_java
  [ "$status" -eq 0 ]
}

# --- 포트 ---

@test "열려 있는 포트는 사용 중으로 본다" {
  python3 -c "
import socket,time
s=socket.socket(); s.bind(('127.0.0.1',0)); s.listen(1)
print(s.getsockname()[1], flush=True); time.sleep(5)" > "$TMP_DIR/port" &
  for i in 1 2 3 4 5 6 7 8 9 10; do [ -s "$TMP_DIR/port" ] && break; sleep 0.2; done
  port="$(cat "$TMP_DIR/port")"
  run port_in_use "$port"
  [ "$status" -eq 0 ]
  ans_set web.port "$port"
  run pf_ports
  [[ "$output" == *"FAIL|port|"* ]]
}

@test "비어 있는 포트는 통과한다" {
  ans_set web.port 59999
  run pf_ports
  [ -z "$output" ]
}

@test "웹 포트와 DB 포트를 모두 점검한다(함께 설치할 때만 DB 포트)" {
  ans_set web.port 59998; ans_set db.type postgresql; ans_set db.install together; ans_set db.port 59997
  run pf_ports
  [ -z "$output" ]
}

# --- 디스크, 메모리 ---

@test "디스크 여유가 모자라면 실패한다" {
  stub df 'printf "Filesystem 1024-blocks Used Available Capacity Mounted\n/dev/x 1000 900 100 90%% /\n"'
  ans_set install_dir "$TMP_DIR/inst"; ans_set data_dir "$TMP_DIR/data"
  run pf_disk
  [[ "$output" == *"FAIL|disk|"* ]]
}

@test "디스크 여유가 충분하면 통과한다" {
  stub df 'printf "Filesystem 1024-blocks Used Available Capacity Mounted\n/dev/x 99999999 1 99999998 1%% /\n"'
  ans_set install_dir "$TMP_DIR/inst"; ans_set data_dir "$TMP_DIR/data"
  run pf_disk
  [ -z "$output" ]
}

@test "메모리가 모자라면 경고만 한다(막지 않는다)" {
  run pf_memory_check 512
  [[ "$output" == *"WARN|memory|"* ]]
  run pf_memory_check 4096
  [ -z "$output" ]
}

# --- 권한 ---

@test "격상이 필요 없으면 권한을 묻지 않는다" {
  ans_set install_dir "$TMP_DIR/home/yona"; ans_set data_dir "$TMP_DIR/home/yona-data"; ans_set service.register false
  run needs_privilege
  [ "$status" -ne 0 ]
}

@test "서비스를 등록하면 격상이 필요하다" {
  ans_set install_dir "$TMP_DIR/home/yona"; ans_set data_dir "$TMP_DIR/home/d"; ans_set service.register true
  run needs_privilege
  [ "$status" -eq 0 ]
}

@test "쓸 수 없는 경로에 설치하면 격상이 필요하다" {
  [ "$(id -u)" -ne 0 ] || skip "root는 어디든 쓸 수 있다"
  ans_set install_dir /proc/yona-nope; ans_set data_dir "$TMP_DIR/home/d"; ans_set service.register false
  run needs_privilege
  [ "$status" -eq 0 ]
}

@test "시스템 패키지로 DB를 설치하면 격상이 필요하다" {
  ans_set install_dir "$TMP_DIR/home/yona"; ans_set data_dir "$TMP_DIR/home/d"; ans_set service.register false
  ans_set db.install together; ans_set db.method package
  run needs_privilege
  [ "$status" -eq 0 ]
}

@test "격상이 필요하고 root도 sudo도 없으면 실패한다" {
  ans_set install_dir /opt/yona; ans_set data_dir /var/yona; ans_set service.register true
  YI_EUID=1000 PATH="$TMP_DIR/bin:/usr/bin:/bin" run pf_privilege
  # sudo가 이 컴퓨터에 있을 수 있어 가짜로 없다고 만든다
  stub sudo 'exit 127'
  YI_EUID=1000 YI_NO_SUDO=1 run pf_privilege
  [[ "$output" == *"FAIL|privilege|"* ]]
}

@test "root이면 격상 없이 통과한다" {
  ans_set install_dir /opt/yona; ans_set data_dir /var/yona; ans_set service.register true
  YI_EUID=0 run pf_privilege
  [ -z "$output" ]
}

# --- 비밀 값 파일 ---

@test "비밀번호 파일이 없으면 실패한다" {
  ans_set db.type postgresql; ans_set db.install separate; ans_set db.password_file "$TMP_DIR/none"
  run pf_secret_files
  [[ "$output" == *"FAIL|secret-file|"* ]]
}

@test "비밀번호 파일의 권한이 넓으면 경고한다" {
  printf 'pw\n' > "$TMP_DIR/pw"; chmod 644 "$TMP_DIR/pw"
  ans_set db.type postgresql; ans_set db.install separate; ans_set db.password_file "$TMP_DIR/pw"
  run pf_secret_files
  [[ "$output" == *"WARN|secret-file-mode|"* ]]
}

@test "비밀번호에 한글이 있으면 실패한다" {
  printf '비밀번호\n' > "$TMP_DIR/pw"; chmod 600 "$TMP_DIR/pw"
  ans_set db.type postgresql; ans_set db.install separate; ans_set db.password_file "$TMP_DIR/pw"
  run pf_secret_files
  [[ "$output" == *"FAIL|secret-file-content|"* ]]
}

@test "권한이 좁고 내용이 올바르면 통과한다" {
  printf 'good-pw\n' > "$TMP_DIR/pw"; chmod 600 "$TMP_DIR/pw"
  ans_set db.type postgresql; ans_set db.install separate; ans_set db.password_file "$TMP_DIR/pw"
  run pf_secret_files
  [ -z "$output" ]
}

# --- 컨테이너 도구 ---

@test "컨테이너 DB인데 docker도 podman도 없으면 실패한다" {
  ans_set db.install together; ans_set db.method container
  YI_NO_DOCKER=1 YI_NO_PODMAN=1 run pf_container
  [[ "$output" == *"FAIL|container|"* ]]
}

@test "docker가 있고 응답하면 env.containers에 담는다" {
  stub docker 'exit 0'
  ans_set db.install together; ans_set db.method container
  pf_container >/dev/null
  [ "$(ans_get env.containers)" = "docker" ]
}

@test "docker가 없고 podman이 있으면 podman을 쓴다" {
  stub podman 'exit 0'
  ans_set db.install together; ans_set db.method container
  YI_NO_DOCKER=1 pf_container >/dev/null
  [ "$(ans_get env.containers)" = "podman" ]
}

# --- 설치 기록과 이미 설치됨 ---

@test "시스템 기록이 있으면 읽는다" {
  mkdir -p "$TMP_DIR/sys"
  printf 'version=v2.0.0\ninstall_dir=/opt/yona\nservice=true\n' > "$TMP_DIR/sys/install.rec"
  export YI_SYSTEM_RECORD_DIR="$TMP_DIR/sys"
  [ "$(record_find)" = "$TMP_DIR/sys/install.rec" ]
  [ "$(record_get "$TMP_DIR/sys/install.rec" version)" = "v2.0.0" ]
  [ "$(record_get "$TMP_DIR/sys/install.rec" install_dir)" = "/opt/yona" ]
}

@test "일반 사용자 구성의 기록은 홈의 상태 폴더에 있다" {
  export YI_SYSTEM_RECORD_DIR="$TMP_DIR/nosys"
  mkdir -p "$HOME/.local/state/yona-install"
  printf 'version=v2.0.0\n' > "$HOME/.local/state/yona-install/install.rec"
  [ "$(record_find)" = "$HOME/.local/state/yona-install/install.rec" ]
}

@test "기록이 없으면 설치되지 않은 것이다" {
  export YI_SYSTEM_RECORD_DIR="$TMP_DIR/nosys"
  run record_find
  [ "$status" -ne 0 ]
}

@test "같은 버전이면 same, 높은 버전을 고르면 upgrade, 낮으면 downgrade" {
  . "$YI_DIR/lib/releases.sh"
  [ "$(install_state v2.0.0 v2.0.0)" = "same" ]
  [ "$(install_state v2.0.0 v2.1.0)" = "upgrade" ]
  [ "$(install_state v2.1.0 v2.0.0)" = "downgrade" ]
  [ "$(install_state v2.0.0-rc1 v2.0.0)" = "upgrade" ]
  [ "$(install_state "" v2.0.0)" = "new" ]
}

@test "사전 배포로 올리는 업그레이드는 확인이 필요하다" {
  . "$YI_DIR/lib/releases.sh"
  [ "$(upgrade_needs_confirm v2.0.0 v2.1.0-rc1)" = "yes" ]
  [ "$(upgrade_needs_confirm v2.0.0 v2.1.0)" = "no" ]
  [ "$(upgrade_needs_confirm v2.0.0-rc1 v2.0.0)" = "no" ]
}
