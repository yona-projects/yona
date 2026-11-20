#!/usr/bin/env bats
# 답(옵션, 답 파일, 환경 변수)의 우선순위, 검증, 필수 항목 시험.
load test_helper

setup() {
  setup_tmp
  . "$YI_DIR/lib/common.sh"
  . "$YI_DIR/lib/answers.sh"
  ans_clear
}
teardown() { teardown_tmp; }

@test "옵션은 --키=값과 --키 값 두 모양을 모두 받는다" {
  answers_load --db=postgresql --port 9090
  [ "$(ans_get db.type)" = "postgresql" ]
  [ "$(ans_get web.port)" = "9090" ]
}

@test "값이 없는 옵션(플래그)은 true가 된다" {
  answers_load --yes --dry-run
  [ "$(ans_get yes)" = "true" ]
  [ "$(ans_get dry_run)" = "true" ]
}

@test "--non-interactive는 --yes와 같다" {
  answers_load --non-interactive
  [ "$(ans_get yes)" = "true" ]
}

@test "알 수 없는 옵션은 실패하고 이름을 알린다" {
  run answers_load --no-such-option=1
  [ "$status" -ne 0 ]
  [[ "$output" == *"--no-such-option"* ]]
}

@test "환경 변수 YONA_INSTALL_키를 읽는다" {
  YONA_INSTALL_DB_TYPE=mariadb YONA_INSTALL_WEB_PORT=8081 answers_load
  [ "$(ans_get db.type)" = "mariadb" ]
  [ "$(ans_get web.port)" = "8081" ]
}

@test "답 파일을 읽고 주석과 빈 줄을 건너뛴다" {
  cat > "$TMP_DIR/a.env" <<'EOT'
# 주석
db.type = postgresql

web.port=9000
EOT
  answers_load --answers="$TMP_DIR/a.env"
  [ "$(ans_get db.type)" = "postgresql" ]
  [ "$(ans_get web.port)" = "9000" ]
}

@test "우선순위는 옵션, 답 파일, 환경 변수 순이다" {
  printf 'web.port=2000\ndb.type=mariadb\n' > "$TMP_DIR/a.env"
  YONA_INSTALL_WEB_PORT=1000 YONA_INSTALL_DB_TYPE=h2 YONA_INSTALL_LANG=en \
    answers_load --answers="$TMP_DIR/a.env" --port=3000
  [ "$(ans_get web.port)" = "3000" ]      # 옵션
  [ "$(ans_get db.type)" = "mariadb" ]    # 답 파일이 환경 변수보다 우선
  [ "$(ans_get lang)" = "en" ]            # 환경 변수만 있는 것
}

@test "없는 답 파일은 실패한다" {
  run answers_load --answers="$TMP_DIR/없음.env"
  [ "$status" -ne 0 ]
}

# --- 검증 ---

@test "올바른 답은 검증을 통과한다" {
  answers_load --db=postgresql --db-install=separate --db-port=5432 --db-name=yona --db-user=yona \
    --port=8080 --scheme=https --ssh=builtin --ssh-port=2222 --version=v2.0.0
  run answers_validate
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "검증은 오류를 모두 모아 한 번에 알린다" {
  answers_load --db=oracle --port=70000 --scheme=ftp --db-name=Bad-Name
  run answers_validate
  [ "$status" -ne 0 ]
  [[ "$output" == *"db.type"* ]]
  [[ "$output" == *"web.port"* ]]
  [[ "$output" == *"site.scheme"* ]]
  [[ "$output" == *"db.name"* ]]
}

@test "포트는 1부터 65535까지의 숫자만 받는다" {
  for bad in 0 65536 abc -1 8.5; do
    ans_clear; ans_set web.port "$bad"
    run answers_validate
    [ "$status" -ne 0 ] || { echo "통과하면 안 됨: $bad"; return 1; }
  done
  for good in 1 80 8080 65535; do
    ans_clear; ans_set web.port "$good"
    run answers_validate
    [ "$status" -eq 0 ] || { echo "통과해야 함: $good"; return 1; }
  done
}

@test "DB 이름과 사용자는 영문 소문자로 시작하고 소문자 숫자 밑줄만 받는다" {
  for bad in 1abc Yona "yo-na" "yo na" "yona;drop" ""; do
    ans_clear; ans_set db.name "$bad"
    if [ -z "$bad" ]; then continue; fi
    run answers_validate
    [ "$status" -ne 0 ] || { echo "통과하면 안 됨: [$bad]"; return 1; }
  done
  ans_clear; ans_set db.name yona_v2; ans_set db.user app1
  run answers_validate
  [ "$status" -eq 0 ]
}

@test "웹 포트와 내장 SSH 포트가 같으면 오류이다" {
  answers_load --port=2222 --ssh=builtin --ssh-port=2222
  run answers_validate
  [ "$status" -ne 0 ]
  [[ "$output" == *"ssh.port"* ]]
}

@test "버전은 v2.0.0, 2.0.0, v2.0.0-rc1 모양을 받는다" {
  for good in v2.0.0 2.0.0 v2.0.0-rc1 v2.1.3; do
    ans_clear; ans_set version "$good"
    run answers_validate
    [ "$status" -eq 0 ] || { echo "통과해야 함: $good"; return 1; }
  done
  ans_clear; ans_set version "latest"
  run answers_validate
  [ "$status" -ne 0 ]
}

@test "불리언 답은 yes no true false를 받고 true false로 맞춘다" {
  ans_set service.register yes
  answers_normalize
  [ "$(ans_get service.register)" = "true" ]
  ans_set service.register NO
  answers_normalize
  [ "$(ans_get service.register)" = "false" ]
}

# --- 필수 항목(비대화형에서 모자라면 중단) ---

@test "신규 설치 H2는 버전, 폴더, 서비스 여부, DB 종류만 필요하다" {
  answers_load --db=h2 --version=v2.0.0 --install-dir=/opt/yona --data-dir=/var/lib/yona --service=false
  run answers_required install
  [ -z "$output" ]
}

@test "신규 설치에서 아무 답이 없으면 필수 항목을 모두 알린다" {
  run answers_required install
  for k in version install_dir data_dir service.register db.type; do
    [[ "$output" == *"$k"* ]] || { echo "빠짐: $k"; return 1; }
  done
}

@test "함께 설치하는 DB는 이름, 사용자, 비밀번호, 관리자 비밀번호를 요구한다" {
  answers_load --db=postgresql --db-install=together --db-method=container
  run answers_required install
  for k in db.name db.user db.password_file db.admin_password_file; do
    [[ "$output" == *"$k"* ]] || { echo "빠짐: $k"; return 1; }
  done
  [[ "$output" != *"db.host"* ]]
}

@test "따로 설치된 DB는 접속 정보를 요구하고 관리자 계정은 요구하지 않는다" {
  answers_load --db=mariadb --db-install=separate
  run answers_required install
  for k in db.host db.port db.name db.user db.password_file; do
    [[ "$output" == *"$k"* ]] || { echo "빠짐: $k"; return 1; }
  done
  [[ "$output" != *"admin"* ]]
}

@test "메일을 쓰면 서버, 포트, 모의 발송 여부를 요구한다" {
  answers_load --mail-enabled=true
  run answers_required install
  for k in mail.host mail.port mail.mock; do
    [[ "$output" == *"$k"* ]] || { echo "빠짐: $k"; return 1; }
  done
}

@test "이관은 v1.6 폴더와 DB 종류와 백업 확인을 요구한다" {
  run answers_required migrate
  for k in v16.dir v16.db confirm.backup; do
    [[ "$output" == *"$k"* ]] || { echo "빠짐: $k"; return 1; }
  done
}

@test "v1.6이 H2이면 시간대와 대상 DB를 요구한다" {
  answers_load --v16-db=h2
  run answers_required migrate
  [[ "$output" == *"v16.timezone"* ]]
  [[ "$output" == *"db.type"* ]]
}

@test "업그레이드는 버전과 백업 확인만 요구한다" {
  run answers_required upgrade
  [[ "$output" == *"version"* ]]
  [[ "$output" == *"confirm.backup"* ]]
  [[ "$output" != *"db."* ]]
}
