#!/usr/bin/env bats
# 설치 환경(운영체제, 배포본, 패키지 관리자, sshd 서비스 이름)을 읽는 부분의 시험.
load test_helper

setup() { setup_tmp; . "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/answers.sh"; . "$YI_DIR/lib/env.sh"; ans_clear; mkdir -p "$TMP_DIR/bin"; }
teardown() { teardown_tmp; }

detect_with() { YI_UNAME=Linux YI_OS_RELEASE="$BATS_TEST_DIRNAME/fixtures/os/$1" detect_env; }

@test "Ubuntu는 apt, 서비스 이름은 ssh" {
  detect_with ubuntu
  [ "$(ans_get env.os)" = "linux" ]
  [ "$(ans_get env.distro)" = "ubuntu" ]
  [ "$(ans_get env.pkg)" = "apt" ]
  [ "$(ans_get env.sshd_unit)" = "ssh" ]
}

@test "Debian은 apt, 서비스 이름은 ssh" {
  detect_with debian
  [ "$(ans_get env.pkg)" = "apt" ]
  [ "$(ans_get env.sshd_unit)" = "ssh" ]
}

@test "Rocky Linux와 Fedora는 dnf, 서비스 이름은 sshd" {
  detect_with rocky
  [ "$(ans_get env.distro)" = "rocky" ]
  [ "$(ans_get env.pkg)" = "dnf" ]
  [ "$(ans_get env.sshd_unit)" = "sshd" ]
  ans_clear; detect_with fedora
  [ "$(ans_get env.pkg)" = "dnf" ]
  [ "$(ans_get env.sshd_unit)" = "sshd" ]
}

@test "파생 배포본은 ID_LIKE로 판단한다(Linux Mint는 apt)" {
  detect_with linuxmint
  [ "$(ans_get env.pkg)" = "apt" ]
}

@test "지원 목록 밖의 배포본은 패키지 관리자를 none으로 둔다" {
  detect_with arch
  [ "$(ans_get env.pkg)" = "none" ]
}

@test "macOS는 mac, 패키지 관리자는 brew, 서비스는 launchd" {
  YI_UNAME=Darwin detect_env
  [ "$(ans_get env.os)" = "mac" ]
  [ "$(ans_get env.pkg)" = "brew" ]
}

@test "sudo로 실행했으면 원래 사용자를 env.user로 쓴다" {
  SUDO_USER=alice detect_with ubuntu
  [ "$(ans_get env.user)" = "alice" ]
}

@test "systemctl이 있으면 실제 유닛 이름을 확인한다(ssh.service가 있으면 ssh)" {
  printf '#!/bin/sh\nif [ "$1" = "cat" ] && [ "$2" = "sshd" ]; then exit 0; fi\nexit 1\n' > "$TMP_DIR/bin/systemctl"; chmod +x "$TMP_DIR/bin/systemctl"
  PATH="$TMP_DIR/bin:$PATH" detect_with ubuntu
  [ "$(ans_get env.sshd_unit)" = "sshd" ]
}

@test "시간대는 TZ 환경 변수를 우선하고 없으면 /etc/localtime에서 읽는다" {
  TZ=Asia/Seoul detect_with ubuntu
  [ "$(ans_get env.tz)" = "Asia/Seoul" ]
}

@test "기본 폴더는 서비스 등록 여부와 운영체제에 따라 다르다" {
  [ "$(default_install_dir linux true)" = "/opt/yona" ]
  [ "$(default_data_dir linux true)" = "/var/lib/yona" ]
  [ "$(default_install_dir mac true)" = "/usr/local/yona" ]
  [ "$(default_data_dir mac true)" = "/usr/local/var/yona" ]
  HOME=/home/alice
  [ "$(default_install_dir linux false)" = "/home/alice/yona" ]
  [ "$(default_data_dir linux false)" = "/home/alice/yona-data" ]
}

@test "시스템 패키지로 설치할 수 있는 DB는 배포본과 종류에 따라 거른다" {
  [ "$(db_methods linux apt postgresql)" = "container package" ]
  [ "$(db_methods linux dnf mariadb)" = "container package" ]
  [ "$(db_methods linux apt mssql)" = "container" ]
  [ "$(db_methods linux apt cubrid)" = "container" ]
  [ "$(db_methods linux none postgresql)" = "container" ]
  [ "$(db_methods mac brew postgresql)" = "container" ]
  [ "$(db_methods linux apt h2)" = "" ]
}
