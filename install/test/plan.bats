#!/usr/bin/env bats
# 실행 계획(어떤 단계를 어떤 권한으로 하는가) 시험.
load test_helper

setup() {
  setup_tmp
  . "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/answers.sh"; . "$YI_DIR/lib/render.sh"; . "$YI_DIR/lib/preflight.sh"; . "$YI_DIR/lib/plan.sh"
  . "$YI_DIR/lib/messages.sh"
  ans_clear
  ans_set env.os linux
  ans_set install_dir "$TMP_DIR/inst"; ans_set data_dir "$TMP_DIR/data"; ans_set db.type h2; ans_set version v2.0.0
}
teardown() { teardown_tmp; }

ids() { plan_steps | cut -d'|' -f1 | tr '\n' ' ' | sed 's/ $//'; }
priv_of() { plan_steps | awk -F'|' -v id="$1" '$1 == id { print $2 }'; }

@test "H2와 서비스 없음이면 격상이 필요한 단계가 없다" {
  ans_set service.register false
  [ "$(ids)" = "preflight fetch stage files start health report" ]
  [ "$(plan_steps | grep -c '|yes')" = "0" ]
}

@test "서비스를 등록하면 계정, 서비스 단계가 관리자 권한이다" {
  ans_set service.register true
  [ "$(priv_of user)" = "yes" ]
  [ "$(priv_of service)" = "yes" ]
  [ "$(priv_of files)" = "yes" ]
  [ "$(priv_of preflight)" = "no" ]
  [ "$(priv_of fetch)" = "no" ]
}

@test "함께 설치하는 패키지 DB는 관리자 권한 단계가 있다" {
  ans_set service.register false; ans_set db.type postgresql; ans_set db.install together; ans_set db.method package
  [ "$(priv_of db)" = "yes" ]
}

@test "따로 설치된 DB는 DB 단계가 없다" {
  ans_set service.register false; ans_set db.type postgresql; ans_set db.install separate
  [ -z "$(priv_of db)" ]
}

@test "시스템 sshd 연동은 sshd 단계가 있다" {
  ans_set service.register true; ans_set ssh.mode system
  [[ "$(ids)" == *"sshd"* ]]
  [ "$(priv_of sshd)" = "yes" ]
}

@test "JDK를 설치하기로 했으면 jdk 단계가 맨 앞쪽에 있다" {
  ans_set service.register true; ans_set install_jdk true; ans_set env.need_jdk true
  [ "$(ids | cut -d' ' -f1-3)" = "preflight fetch stage" ]
  [[ "$(ids)" == *"jdk"* ]]
  [ "$(priv_of jdk)" = "yes" ]
}

@test "업그레이드 계획은 jar 교체와 재시작뿐이다" {
  ans_set env.mode upgrade; ans_set service.register true
  [ "$(ids)" = "preflight fetch backup-confirm replace restart health report" ]
}

@test "격상이 필요한 단계 목록을 모아 알려 준다" {
  msg_load en
  ans_set service.register true
  out="$(plan_privileged_summary)"
  [[ "$out" == *"service"* ]]
}

@test "계획 출력에 권한이 필요한 단계가 표시된다" {
  msg_load en
  ans_set service.register true
  out="$(plan_print)"
  [[ "$out" == *"[sudo]"* ]]
}
