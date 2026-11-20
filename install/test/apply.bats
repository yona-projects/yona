#!/usr/bin/env bats
# 설치 적용 스크립트(관리자 권한 단계를 모아 한 번에 실행하는 스크립트)를 만드는 부분의 시험.
load test_helper

setup() {
  setup_tmp
  . "$YI_DIR/lib/common.sh"
  . "$YI_DIR/lib/answers.sh"
  . "$YI_DIR/lib/render.sh"
  . "$YI_DIR/lib/apply.sh"
  ans_clear
  ans_set env.os linux; ans_set env.stage "$TMP_DIR/stage"; ans_set env.record_dir "$TMP_DIR/rec"
  ans_set install_dir "$TMP_DIR/opt/yona"; ans_set data_dir "$TMP_DIR/var/yona"; ans_set version v2.0.0
  ans_set db.type h2; ans_set web.port 8080
}
teardown() { teardown_tmp; }

@test "생성한 스크립트는 bash 문법이 올바르다" {
  ans_set service.register true
  render_apply_script > "$TMP_DIR/apply.sh"
  run bash -n "$TMP_DIR/apply.sh"
  [ "$status" -eq 0 ]
}

@test "서비스를 등록하지 않으면 계정, 서비스, sshd 단계가 없다" {
  ans_set service.register false
  out="$(render_apply_script)"
  [[ "$out" != *"useradd"* ]]
  [[ "$out" != *"systemctl"* ]]
  [[ "$out" != *"sshd"* ]]
}

@test "서비스를 등록하면 전용 계정, 소유권, 유닛 등록, 부팅 자동 시작 단계가 있다" {
  ans_set service.register true
  out="$(render_apply_script)"
  [[ "$out" == *"useradd --system"* ]]
  [[ "$out" == *"chown"* ]]
  [[ "$out" == *"/etc/systemd/system/yona.service"* ]]
  [[ "$out" == *"systemctl enable --now yona"* ]]
}

@test "각 단계는 되돌릴 방법을 기록(journal)에 남긴다" {
  ans_set service.register true
  out="$(render_apply_script)"
  [[ "$out" == *"j_add"* ]]
  [[ "$out" == *"rollback()"* ]]
  [[ "$out" == *"systemctl disable --now yona"* ]]
  [[ "$out" == *"userdel yona"* ]]
}

@test "이미 있던 계정과 폴더는 되돌릴 때 지우지 않는다(만든 것만 기록한다)" {
  ans_set service.register true
  out="$(render_apply_script)"
  # 계정은 없을 때만 만들고 그때만 기록한다
  [[ "$out" == *"if ! id -u yona"* ]]
  [[ "$out" == *"if [ ! -d "* ]]
}

@test "시스템 sshd 연동이면 드롭인 검사 뒤 reload하고 실패하면 드롭인을 지운다" {
  ans_set service.register true; ans_set ssh.mode system; ans_set env.sshd_unit ssh
  out="$(render_apply_script)"
  [[ "$out" == *"/etc/ssh/sshd_config.d/50-yona.conf"* ]]
  [[ "$out" == *"sshd -t"* ]]
  [[ "$out" == *"systemctl reload ssh"* ]]
  [[ "$out" == *"useradd"*"git"* ]]
}

@test "패키지로 설치하는 PostgreSQL은 배포본의 패키지 관리자로 설치하고 서비스를 켠다" {
  ans_set service.register true; ans_set db.type postgresql; ans_set db.install together; ans_set db.method package; ans_set env.pkg apt
  out="$(render_apply_script)"
  [[ "$out" == *"apt-get install -y postgresql"* ]]
  [[ "$out" == *"systemctl enable --now postgresql"* ]]
  [[ "$out" == *"db.sql"* ]]
}

@test "dnf 배포본은 dnf로 설치한다" {
  ans_set service.register true; ans_set db.type mariadb; ans_set db.install together; ans_set db.method package; ans_set env.pkg dnf
  out="$(render_apply_script)"
  [[ "$out" == *"dnf install -y mariadb-server"* ]]
}

@test "컨테이너 DB는 포트를 127.0.0.1에만 공개하고 재시작 정책을 둔다" {
  ans_set service.register true; ans_set db.type postgresql; ans_set db.install together; ans_set db.method container; ans_set env.containers docker
  ans_set db.name yona; ans_set db.user yona
  out="$(render_apply_script)"
  [[ "$out" == *"docker run -d --name yona-db"* ]]
  [[ "$out" == *"--restart unless-stopped"* ]]
  [[ "$out" == *"127.0.0.1:5432:5432"* ]]
  [[ "$out" == *"docker rm -fv yona-db"* ]]
}

@test "컨테이너 DB의 비밀번호는 명령줄이 아니라 파일로 넘긴다" {
  printf 'adminpw\n' > "$TMP_DIR/admin"
  ans_set service.register true; ans_set db.type postgresql; ans_set db.install together; ans_set db.method container; ans_set env.containers docker
  ans_set db.admin_password_file "$TMP_DIR/admin"
  out="$(render_apply_script)"
  [[ "$out" != *"adminpw"* ]]
  [[ "$out" == *"--env-file"* ]]
}

@test "Mac은 LaunchDaemon으로 등록한다" {
  ans_set env.os mac; ans_set service.register true; ans_set env.user tester
  out="$(render_apply_script)"
  [[ "$out" == *"/Library/LaunchDaemons/dev.yona.server.plist"* ]]
  [[ "$out" == *"launchctl bootstrap system"* ]]
  [[ "$out" != *"useradd"* ]]
}

@test "설치 기록 파일에 버전과 경로와 설치 방식을 남긴다" {
  ans_set service.register false
  out="$(render_apply_script)"
  [[ "$out" == *"install.rec"* ]]
  [[ "$out" == *"version=v2.0.0"* ]]
}

@test "rollback 인자로 부르면 기록을 거꾸로 되돌리는 모드가 있다" {
  ans_set service.register false
  out="$(render_apply_script)"
  [[ "$out" == *'"${1:-apply}" = "rollback"'* ]]
}

@test "패키지 DB를 되돌릴 때 쓸 삭제 SQL을 기록 폴더에 보관한다" {
  ans_set service.register true; ans_set db.type postgresql; ans_set db.install together; ans_set db.method package; ans_set env.pkg apt
  out="$(render_apply_script)"
  [[ "$out" == *'db-drop.sql'* ]]
  [[ "$out" == *'RECORD_DIR/db-drop.sql'* ]]
}

@test "DB 포트를 바꿔도 컨테이너 안의 포트는 DB의 기본 포트이다(바깥 포트만 바뀐다)" {
  ans_set service.register true; ans_set db.type postgresql; ans_set db.install together; ans_set db.method container; ans_set env.containers docker
  ans_set db.port 55432
  out="$(render_apply_script)"
  [[ "$out" == *"127.0.0.1:55432:5432"* ]]
  [[ "$out" != *"55432:55432"* ]]
}

@test "MariaDB 컨테이너는 초기화 중의 임시 서버를 피하려고 TCP로 기다리고 SQL을 실행한다" {
  ans_set service.register true; ans_set db.type mariadb; ans_set db.install together; ans_set db.method container; ans_set env.containers docker
  out="$(render_apply_script)"
  [[ "$out" == *"mariadb-admin ping -h 127.0.0.1"* ]]
  [[ "$out" == *"mariadb -h 127.0.0.1 -uroot"* ]]
}

@test "conf 아래 모든 파일(이관 변환기의 application.properties 포함)을 설치한다" {
  ans_set service.register false
  out="$(render_apply_script)"
  [[ "$out" == *'cp "$STAGE"/conf/* "$INSTALL_DIR/conf/"'* ]]
  [[ "$out" == *"conf/application.properties"* ]]
}
