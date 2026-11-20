#!/usr/bin/env bats
# 메인 스크립트의 처음부터 끝까지 흐름 시험(--dry-run과 가짜 릴리스 서버). 실제로 설치하지 않는다.
load test_helper

setup() {
  setup_tmp
  export HOME="$TMP_DIR/home"; mkdir -p "$HOME"
  export YI_SYSTEM_RECORD_DIR="$TMP_DIR/sysrec"
  # 가짜 릴리스: file:// 주소로 jar를 준다
  mkdir -p "$TMP_DIR/srv"
  for v in 2.0.0 2.1.0 2.2.0-rc1; do printf 'JAR-%s' "$v" > "$TMP_DIR/srv/yona-$v.jar"; done
  python3 - "$TMP_DIR" <<'PY'
import hashlib, json, sys
t = sys.argv[1]
def rel(tag, v, pre=False):
    p = f"{t}/srv/yona-{v}.jar"
    d = hashlib.sha256(open(p, 'rb').read()).hexdigest()
    return {"tag_name": tag, "name": tag, "draft": False, "prerelease": pre,
            "assets": [{"name": f"yona-{v}.jar", "size": 9, "digest": "sha256:" + d, "browser_download_url": "file://" + p}],
            "tarball_url": "x"}
json.dump([rel("v2.2.0-rc1", "2.2.0-rc1", True), rel("v2.1.0", "2.1.0"), rel("v2.0.0", "2.0.0")], open(f"{t}/releases.json", "w"))
PY
  export YONA_RELEASES_URL="file://$TMP_DIR/releases.json"
  export LANG=C LC_ALL=C
  export YI_EUID=1000
  # 이 컴퓨터의 JDK와 상관없이 21이 있는 것으로 시험한다
  mkdir -p "$TMP_DIR/bin"
  printf '#!/bin/sh\necho "openjdk version \\"21.0.2\\" 2026-01-01" >&2\n' > "$TMP_DIR/bin/java"; chmod +x "$TMP_DIR/bin/java"
  export PATH="$TMP_DIR/bin:$PATH"
  YI="$YI_DIR/yona-install.sh"
}
teardown() { teardown_tmp; }

base_h2() { echo --yes --dry-run --offline=false --version=v2.0.0 --install-dir="$TMP_DIR/home/yona" --data-dir="$TMP_DIR/home/yona-data" --service=false --db=h2 --port=58123; }

@test "H2와 서비스 없음 dry-run은 계획을 보이고 아무것도 만들지 않는다" {
  run "$YI" install $(base_h2)
  [ "$status" -eq 0 ]
  [[ "$output" == *"Plan:"* ]]
  [[ "$output" == *"nothing was installed"* ]]
  [ ! -e "$TMP_DIR/home/yona" ]
  [ ! -e "$TMP_DIR/home/yona-data" ]
}

@test "계획에서 관리자 권한이 필요한 단계에 [sudo]가 붙는다" {
  run "$YI" install --yes --dry-run --version=v2.0.0 --install-dir=/opt/yona-x --data-dir=/var/lib/yona-x --service=true --db=h2 --port=58124
  [[ "$output" == *"[sudo]"* ]]
  [[ "$output" == *"Steps that need administrator rights"* ]]
}

@test "비대화형에서 필요한 답이 모자라면 목록으로 알리고 종료 코드 2로 끝난다" {
  run "$YI" install --yes --dry-run --version=v2.0.0
  [ "$status" -eq 2 ]
  [[ "$output" == *"install_dir"* ]]
  [[ "$output" == *"service.register"* ]]
  [[ "$output" == *"db.type"* ]]
  [[ "$output" == *"Nothing was installed"* ]]
}

@test "잘못된 값은 모두 모아 알리고 종료 코드 2로 끝난다" {
  run "$YI" install --yes --dry-run --version=v2.0.0 --db=oracle --port=70000
  [ "$status" -eq 2 ]
  [[ "$output" == *"db.type"* ]]
  [[ "$output" == *"web.port"* ]]
}

@test "알 수 없는 옵션은 종료 코드 2이다" {
  run "$YI" install --no-such-thing
  [ "$status" -eq 2 ]
}

@test "한국어 시스템이면 한국어로 출력하고 --lang으로 바꿀 수 있다" {
  LC_ALL=ko_KR.UTF-8 run "$YI" install $(base_h2)
  [[ "$output" == *"실행 계획"* ]]
  LC_ALL=ko_KR.UTF-8 run "$YI" install $(base_h2) --lang=en
  [[ "$output" == *"Plan:"* ]]
}

@test "대화형: 질문에 답하면 같은 계획이 나온다" {
  # 질문 순서: 버전(2=v2.1.0), 설치 방식(기본), 서비스(n), 설치 폴더, 데이터 폴더, DB(h2), 호스트, 포트, 메일(n), SSH(기본), 진행(n)
  printf '%s\n' 2 "" n "$TMP_DIR/home/yona" "$TMP_DIR/home/yona-data" 5 "" "" "" "" "" n > "$TMP_DIR/in"
  YI_INPUT="$TMP_DIR/in" run "$YI" install --dry-run
  [ "$status" -eq 0 ]
  [[ "$output" == *"Choose the version to install"* ]]
  [[ "$output" == *"v2.1.0"* ]]
  [[ "$output" == *"Plan:"* ]]
}

@test "버전 선택지에 사전 배포는 (rc)로 표시한다" {
  printf '%s\n' 3 > "$TMP_DIR/in"
  YI_INPUT="$TMP_DIR/in" run "$YI" install --dry-run --service=false --db=h2 --install-dir="$TMP_DIR/home/y" --data-dir="$TMP_DIR/home/d" --port=58125 --mode=install
  [[ "$output" == *"v2.2.0-rc1 (rc)"* ]]
}

@test "없는 버전을 고르면 종료 코드 2이다" {
  run "$YI" install --yes --dry-run --version=v9.9.9
  [ "$status" -eq 2 ]
  [[ "$output" == *"v9.9.9"* ]]
}

@test "같은 버전이 이미 설치되어 있으면 알리고 끝낸다" {
  mkdir -p "$YI_SYSTEM_RECORD_DIR"
  printf 'version=v2.0.0\ninstall_dir=/opt/yona\ndata_dir=/var/lib/yona\nservice=true\nos=linux\n' > "$YI_SYSTEM_RECORD_DIR/install.rec"
  run "$YI" install --yes --dry-run --version=v2.0.0
  [ "$status" -eq 0 ]
  [[ "$output" == *"already installed"* ]]
}

@test "다른(높은) 버전이면 업그레이드로 보고 백업 확인을 요구한다" {
  mkdir -p "$YI_SYSTEM_RECORD_DIR"
  printf 'version=v2.0.0\ninstall_dir=/opt/yona\ndata_dir=/var/lib/yona\nservice=true\nos=linux\n' > "$YI_SYSTEM_RECORD_DIR/install.rec"
  run "$YI" install --yes --dry-run --version=v2.1.0
  [ "$status" -eq 2 ]
  [[ "$output" == *"Upgrading the installed v2.0.0 to v2.1.0"* ]]
  [[ "$output" == *"confirm.backup"* ]]
  run "$YI" install --yes --dry-run --version=v2.1.0 --confirm-backup
  [ "$status" -eq 0 ]
  [[ "$output" == *"Replace the Yona jar"* ]]
}

@test "낮은 버전으로는 --allow-downgrade 없이 바꾸지 않는다" {
  mkdir -p "$YI_SYSTEM_RECORD_DIR"
  printf 'version=v2.1.0\ninstall_dir=/opt/yona\ndata_dir=/var/lib/yona\nservice=true\nos=linux\n' > "$YI_SYSTEM_RECORD_DIR/install.rec"
  run "$YI" install --yes --dry-run --version=v2.0.0 --confirm-backup
  [ "$status" -eq 2 ]
  [[ "$output" == *"Cannot switch to the lower version"* ]]
}

@test "정식에서 사전 배포로 올릴 때는 확인을 한 번 더 요구한다" {
  mkdir -p "$YI_SYSTEM_RECORD_DIR"
  printf 'version=v2.1.0\ninstall_dir=/opt/yona\ndata_dir=/var/lib/yona\nservice=true\nos=linux\n' > "$YI_SYSTEM_RECORD_DIR/install.rec"
  run "$YI" install --yes --dry-run --version=v2.2.0-rc1 --confirm-backup
  [ "$status" -eq 2 ]
  [[ "$output" == *"pre-release"* ]]
}

@test "설치 기록이 없으면 제거는 종료 코드 1이다" {
  run "$YI" uninstall --yes
  [ "$status" -eq 1 ]
  [[ "$output" == *"not installed"* ]]
}

@test "제거 --purge는 확인 없이 하지 않는다" {
  mkdir -p "$YI_SYSTEM_RECORD_DIR"
  printf 'version=v2.0.0\ninstall_dir=/opt/yona\ndata_dir=/var/lib/yona\nservice=true\nos=linux\n' > "$YI_SYSTEM_RECORD_DIR/install.rec"
  run "$YI" uninstall --purge --dry-run </dev/null
  [ "$status" -eq 2 ]
  [[ "$output" == *"--purge was not confirmed"* ]]
}

@test "--download-only는 jar와 sha256 파일을 스크립트 폴더에 받는다" {
  cp -R "$YI_DIR" "$TMP_DIR/bundle"
  rm -f "$TMP_DIR/bundle"/yona-*.jar
  run "$TMP_DIR/bundle/yona-install.sh" install --download-only --version=v2.0.0
  [ "$status" -eq 0 ]
  [ -f "$TMP_DIR/bundle/yona-2.0.0.jar" ]
  [ -f "$TMP_DIR/bundle/yona-2.0.0.jar.sha256" ]
  [ "$(cut -d' ' -f1 "$TMP_DIR/bundle/yona-2.0.0.jar.sha256")" = "$(sha256sum "$TMP_DIR/bundle/yona-2.0.0.jar" | cut -d' ' -f1)" ]
}

@test "인터넷이 안 되고 같은 폴더에 jar도 없으면 안내하고 끝난다" {
  cp -R "$YI_DIR" "$TMP_DIR/bundle2"
  rm -f "$TMP_DIR/bundle2"/yona-*.jar
  YONA_RELEASES_URL="file://$TMP_DIR/none.json" run "$TMP_DIR/bundle2/yona-install.sh" install --yes --dry-run --version=v2.0.0
  [ "$status" -eq 1 ]
  [[ "$output" == *"--download-only"* ]]
}

@test "같은 폴더에 jar가 있으면 인터넷 없이 그 jar로 계획한다(폐쇄 환경)" {
  cp -R "$YI_DIR" "$TMP_DIR/bundle3"
  printf 'LOCAL' > "$TMP_DIR/bundle3/yona-2.0.0.jar"
  YONA_RELEASES_URL="file://$TMP_DIR/none.json" run "$TMP_DIR/bundle3/yona-install.sh" install --yes --dry-run --version=v2.0.0 \
    --install-dir="$TMP_DIR/home/y" --data-dir="$TMP_DIR/home/d" --service=false --db=h2 --port=58126
  [ "$status" -eq 0 ]
  [[ "$output" == *"using only the files in this folder"* ]]
  [[ "$output" == *"Plan:"* ]]
}

# --- v1.6 이관 ---

make_bundle_with_stub_migrators() { # 가짜 이관 스크립트가 받은 인자를 기록한다
  cp -R "$YI_DIR" "$TMP_DIR/mb"
  rm -f "$TMP_DIR/mb"/yona-*.jar; printf 'JAR' > "$TMP_DIR/mb/yona-2.0.0.jar"
  for n in migrate-v16.sh migrate-v16-h2.sh; do
    printf '#!/usr/bin/env bash\necho "$0 $*" >> "%s/migrate-calls.log"\nenv | grep "^V16_TIMEZONE\\|^JAVA_HOME" >> "%s/migrate-calls.log"\nout=""\nfor a in "$@"; do case "$a" in --v16-conf-out=*) out="${a#--v16-conf-out=}";; esac; done\n[ -n "$out" ] && printf "spring.datasource.url=jdbc:h2:v16\\nyona.smtp.mock=true\\n" > "$out"\nexit ${MIGRATE_RC:-0}\n' "$TMP_DIR" "$TMP_DIR" > "$TMP_DIR/mb/$n"; chmod +x "$TMP_DIR/mb/$n"
  done
}

@test "이관(MariaDB 제자리): 이관 스크립트를 서버를 켜기 전에 --v16-conf와 데이터 폴더로 부른다" {
  make_bundle_with_stub_migrators
  YI_HEALTH_TIMEOUT=2 run "$TMP_DIR/mb/yona-install.sh" install --yes --offline --version=v2.0.0 --mode=migrate --v16-dir="$TMP_DIR/v16" --v16-db=mariadb \
    --v16-conf="$TMP_DIR/v16/conf/application.conf" --confirm-backup --install-dir="$TMP_DIR/home/y" --data-dir="$TMP_DIR/v16" --service=false --port=58331
  [ -f "$TMP_DIR/migrate-calls.log" ]
  grep -q "migrate-v16.sh .*--v16-conf=$TMP_DIR/v16/conf/application.conf" "$TMP_DIR/migrate-calls.log"
  grep -q -- "--yona.data-dir=$TMP_DIR/v16" "$TMP_DIR/migrate-calls.log"
  grep -q "^JAVA_HOME=" "$TMP_DIR/migrate-calls.log"
  [[ "$output" == *"Migrating the v1.6 settings and data"* ]]
}

@test "이관(MariaDB 제자리): 설정 파일에 datasource 블록이 없고 변환된 속성 파일이 conf에 놓인다" {
  make_bundle_with_stub_migrators
  # 가짜 jar라 서버가 뜨지 않아 상태 확인에서 되돌려지므로, 준비 폴더의 내용은 이관 스크립트가 받은 경로에서 본다
  YI_HEALTH_TIMEOUT=2 YI_KEEP_STAGE=1 run "$TMP_DIR/mb/yona-install.sh" install --yes --offline --version=v2.0.0 --mode=migrate --v16-dir="$TMP_DIR/v16" --v16-db=mariadb \
    --v16-conf="$TMP_DIR/v16/conf/application.conf" --confirm-backup --install-dir="$TMP_DIR/home/y" --data-dir="$TMP_DIR/v16" --service=false --port=58332 --keep-on-failure
  [ -f "$TMP_DIR/home/y/conf/application.properties" ]
  ! grep -q "datasource:" "$TMP_DIR/home/y/conf/application.yml"
  [ ! -e "$TMP_DIR/home/y/conf/secrets.properties" ]
  [ "$(stat -c %a "$TMP_DIR/home/y/conf/application.properties" 2>/dev/null || stat -f %Lp "$TMP_DIR/home/y/conf/application.properties")" = "600" ]
}

@test "이관(H2): 옛 H2 파일 변환 스크립트를 시간대와 함께 부르고 변환된 속성의 옛 DB 접속 키를 지운다" {
  make_bundle_with_stub_migrators
  YI_HEALTH_TIMEOUT=2 run "$TMP_DIR/mb/yona-install.sh" install --yes --offline --version=v2.0.0 --mode=migrate --v16-dir="$TMP_DIR/v16" --v16-db=h2 --v16-timezone=Asia/Seoul \
    --v16-conf="$TMP_DIR/v16/conf/application.conf" --confirm-backup --confirm-replace-data --db=h2 --install-dir="$TMP_DIR/home/y" --data-dir="$TMP_DIR/home/d" --service=false --port=58333 --keep-on-failure
  grep -q "migrate-v16-h2.sh .*--v16-dir=$TMP_DIR/v16 --yes" "$TMP_DIR/migrate-calls.log"
  grep -q "^V16_TIMEZONE=Asia/Seoul" "$TMP_DIR/migrate-calls.log"
  [ -f "$TMP_DIR/home/y/conf/application.properties" ]
  ! grep -q "spring.datasource" "$TMP_DIR/home/y/conf/application.properties"
  grep -q "yona.smtp.mock=true" "$TMP_DIR/home/y/conf/application.properties"
}

@test "이관 스크립트가 실패하면 아무것도 설치하지 않고 종료 코드 1이다" {
  make_bundle_with_stub_migrators
  MIGRATE_RC=3 run "$TMP_DIR/mb/yona-install.sh" install --yes --offline --version=v2.0.0 --mode=migrate --v16-dir="$TMP_DIR/v16" --v16-db=mariadb \
    --v16-conf="$TMP_DIR/v16/conf/application.conf" --confirm-backup --install-dir="$TMP_DIR/home/y" --data-dir="$TMP_DIR/v16" --service=false --port=58334
  [ "$status" -eq 1 ]
  [[ "$output" == *"The migration failed"* ]]
  [ ! -e "$TMP_DIR/home/y/yona.jar" ]
}

@test "이관 스크립트가 같은 폴더에 없으면 안내하고 종료 코드 1이다" {
  make_bundle_with_stub_migrators; rm "$TMP_DIR/mb/migrate-v16.sh"
  run "$TMP_DIR/mb/yona-install.sh" install --yes --offline --version=v2.0.0 --mode=migrate --v16-dir="$TMP_DIR/v16" --v16-db=mariadb \
    --v16-conf="$TMP_DIR/v16/conf/application.conf" --confirm-backup --install-dir="$TMP_DIR/home/y" --data-dir="$TMP_DIR/v16" --service=false --port=58335
  [ "$status" -eq 1 ]
  [[ "$output" == *"migration script was not found"* ]]
}
