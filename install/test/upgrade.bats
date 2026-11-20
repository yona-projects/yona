#!/usr/bin/env bats
# 업그레이드와 제거 스크립트를 만드는 부분의 시험.
load test_helper

setup() {
  setup_tmp
  . "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/answers.sh"; . "$YI_DIR/lib/render.sh"; . "$YI_DIR/lib/upgrade.sh"
  ans_clear
  ans_set env.os linux; ans_set env.stage "$TMP_DIR/stage"; ans_set env.record_dir "$TMP_DIR/rec"
  ans_set install_dir /opt/yona; ans_set data_dir /var/lib/yona; ans_set version v2.1.0; ans_set env.old_version v2.0.0; ans_set web.port 8080
}
teardown() { teardown_tmp; }

@test "업그레이드 스크립트는 bash 문법이 올바르다" {
  for svc in true false; do
    ans_set service.register "$svc"
    render_upgrade_script > "$TMP_DIR/u.sh"
    run bash -n "$TMP_DIR/u.sh"
    [ "$status" -eq 0 ]
  done
}

@test "서비스가 있으면 systemctl로 멈추고 시작한다" {
  ans_set service.register true
  out="$(render_upgrade_script)"
  [[ "$out" == *"systemctl stop yona"* ]]
  [[ "$out" == *"systemctl start yona"* ]]
}

@test "Mac 서비스는 launchctl로 멈추고 시작한다" {
  ans_set service.register true; ans_set env.os mac
  out="$(render_upgrade_script)"
  [[ "$out" == *"launchctl bootout system/dev.yona.server"* ]]
  [[ "$out" == *"launchctl bootstrap system"* ]]
}

@test "서비스가 없으면 시작과 중지 스크립트를 쓴다" {
  ans_set service.register false
  out="$(render_upgrade_script)"
  [[ "$out" == *"bin/yona-stop.sh"* ]]
  [[ "$out" == *"bin/yona-start.sh"* ]]
}

@test "옛 jar를 버전 이름으로 보관하고 새 jar로 바꾼다" {
  ans_set service.register true
  out="$(render_upgrade_script)"
  [[ "$out" == *"OLD=v2.0.0"* ]]
  [[ "$out" == *'yona-$OLD.jar.bak'* ]]
  [[ "$out" == *'cp "$STAGE/yona.jar" "$INSTALL_DIR/yona.jar"'* ]]
}

@test "상태 확인에 실패하면 옛 jar로 되돌려 다시 시작한다" {
  ans_set service.register true
  out="$(render_upgrade_script)"
  [[ "$out" == *"actuator/health"* ]]
  [[ "$out" == *"restore"* ]]
}

@test "성공하면 설치 기록의 버전을 새 버전으로 바꾼다" {
  ans_set service.register true
  [[ "$(render_upgrade_script)" == *"version=v2.1.0"* ]]
}

@test "제거 스크립트는 서비스를 끄고 설치한 파일을 지우되 기본으로 데이터를 남긴다" {
  ans_set service.register true
  out="$(render_uninstall_script)"
  [[ "$out" == *"systemctl disable --now yona"* ]]
  [[ "$out" == *'rm -f "$INSTALL_DIR/yona.jar"'* ]]
  [[ "$out" != *'rm -rf "$DATA_DIR"'* ]]
}

@test "--purge이면 데이터 폴더와 컨테이너 DB와 계정도 지운다" {
  ans_set service.register true; ans_set purge true; ans_set db.install together; ans_set db.method container; ans_set env.containers docker
  out="$(render_uninstall_script)"
  [[ "$out" == *'rm -rf "$DATA_DIR"'* ]]
  [[ "$out" == *"docker rm -fv yona-db"* ]]
  [[ "$out" == *"userdel yona"* ]]
}

@test "제거 스크립트도 bash 문법이 올바르다" {
  for p in true false; do
    ans_set service.register true; ans_set purge "$p"
    render_uninstall_script > "$TMP_DIR/x.sh"
    run bash -n "$TMP_DIR/x.sh"
    [ "$status" -eq 0 ]
  done
}
