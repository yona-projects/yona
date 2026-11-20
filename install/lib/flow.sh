# shellcheck shell=bash
# 설치 흐름: 답 읽기, 릴리스 고르기, 질문, 사전 점검, 계획, 적용, 시작, 상태 확인, 결과 문서.
# 신규 설치, 업그레이드(이미 설치된 다른 버전), 이관(v1.6), 제거를 이 파일이 잇는다.
# 종료 코드: 0 성공, 1 실패, 2 입력 오류(아무것도 바꾸지 않음).

# 오류 메시지를 표준 오류로 쓰고 종료 코드를 돌려준다. 사용법: flow_fail <코드> <메시지 키> [인자...]
flow_fail() {
  local code="$1"
  shift
  printf '%s\n' "$(msg "$@")" >&2
  return "$code"
}

# 관리자 권한이 필요하면 `sudo`로, 아니면 그대로 스크립트를 실행한다. 사용법: flow_run_script <스크립트> [인자...]
flow_run_script() {
  local script="$1"
  shift
  if needs_privilege && [ "${YI_EUID:-$(id -u)}" != "0" ]; then
    ${YI_SUDO:-sudo} env YI_KEEP_ON_FAILURE="$(ans_get keep_on_failure false | sed 's/true/1/; s/false/0/')" bash "$script" "$@"
  else
    YI_KEEP_ON_FAILURE="$(ans_get keep_on_failure false | sed 's/true/1/; s/false/0/')" bash "$script" "$@"
  fi
}

# 설치 기록을 쓰는 폴더이다(관리자 권한이 필요한 구성은 시스템, 아니면 사용자 폴더).
flow_record_dir() {
  if [ "$(ans_get service.register)" = "true" ] || needs_privilege; then
    record_system_dir
  else
    record_user_dir
  fi
}

# --- 릴리스 고르기 ---

# 설치할 수 있는 릴리스(로컬 jar와 GitHub 릴리스)의 목록을 `YI_CAND`(탭으로 나눈 줄)에 모은다. 로컬 jar가 같은 버전이면 로컬을 쓴다.
# 인터넷이 안 되면 같은 폴더의 jar를 쓰라고 안내한다. 사용법: flow_load_candidates
flow_load_candidates() {
  local local_list online="" json
  YI_ALL=""
  local_list="$(releases_local_list "$YI_DIR")"
  if [ "$(ans_get offline)" != "true" ]; then
    if json="$(releases_fetch 2>/dev/null)"; then
      online="$(printf '%s' "$json" | releases_list_v2 1000)"
    else
      if [ -z "$local_list" ]; then
        flow_fail 1 err.network
        return 1
      fi
      printf '%s\n' "$(msg info.offline_auto)"
    fi
  fi
  # 같은 태그는 로컬 줄을 먼저 둔다(첫 줄만 남긴다). 높은 버전부터 정렬한다.
  YI_ALL="$(printf '%s\n%s\n' "$local_list" "$online" | awk -F'\t' 'NF >= 3 && !seen[$1]++')"
  YI_ALL="$(printf '%s\n' "$YI_ALL" | while IFS= read -r line; do
    [ -n "$line" ] && printf '%s\t%s\n' "$(version_key "$(printf '%s' "$line" | cut -f1)")" "$line"
  done | LC_ALL=C sort -r -t "$(printf '\t')" -k1,1 | cut -f2-)"
  if [ -z "$YI_ALL" ]; then
    flow_fail 1 err.no_release
    return 1
  fi
  YI_CAND="$(printf '%s\n' "$YI_ALL" | head -n 4)"
}

# 릴리스 줄에서 jar 정보를 `env.*` 답으로 담는다. 사용법: _yi_set_release <줄>
_yi_set_release() {
  ans_set version "$(printf '%s' "$1" | cut -f1)"
  ans_set env.jar_url "$(printf '%s' "$1" | cut -f3)"
  ans_set env.jar_size "$(printf '%s' "$1" | cut -f4)"
  ans_set env.jar_digest "$(printf '%s' "$1" | cut -f5)"
}

# 버전을 고른다. `--version`이 있으면 그 릴리스를, 없으면 최근 4건에서 고르게 한다. 사용법: flow_select_version
flow_select_version() {
  local line tags="" tag label
  if ans_has version; then
    case "$(ans_get version)" in v*) ;; *) ans_set version "v$(ans_get version)" ;; esac
    line="$(printf '%s\n' "$YI_ALL" | awk -F'\t' -v want="$(ans_get version)" '$1 == want { print; exit }')"
    if [ -z "$line" ]; then
      flow_fail 2 err.version_not_found "$(ans_get version)"
      return 2
    fi
    _yi_set_release "$line"
    return 0
  fi
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    tag="$(printf '%s' "$line" | cut -f1)"
    label="$(printf '%s' "$line" | cut -f2)"
    msg_set "choice.version.$tag" "$label"
    tags="$tags $tag"
  done <<EOF2
$YI_CAND
EOF2
  tags="${tags# }"
  ask_choice version prompt.version "$tags" "${tags%% *}" || return 2
  if ! ans_has version; then
    return 0
  fi
  line="$(printf '%s\n' "$YI_ALL" | awk -F'\t' -v want="$(ans_get version)" '$1 == want { print; exit }')"
  _yi_set_release "$line"
}

# jar를 준비 폴더에 둔다(로컬 파일이면 복사, 주소이면 내려받는다). digest가 있으면 검증하고 틀리면 실패한다.
flow_fetch_jar() {
  local url dest rc
  url="$(ans_get env.jar_url)"
  dest="$(ans_get env.stage)/yona.jar"
  case "$url" in
    /*) cp "$url" "$dest" || { flow_fail 1 err.download "$url"; return 1; } ;;
    *)
      printf '%s\n' "$(msg info.downloading "$url")"
      curl -fL --retry 2 -o "$dest" "$url" || { flow_fail 1 err.download "$url"; return 1; }
      ;;
  esac
  verify_digest "$dest" "$(ans_get env.jar_digest)"
  rc=$?
  case "$rc" in
    0) printf '%s\n' "$(msg info.verified)" ;;
    2) printf '%s\n' "$(msg warn.not_verified)" ;;
    *) flow_fail 1 err.digest_mismatch; return 1 ;;
  esac
}

# --- 질문 ---

# DB 종류별 시스템 관리자 ID의 기본값이다.
_yi_default_admin() {
  case "$1" in
    postgresql) printf 'postgres' ;;
    mariadb) printf 'root' ;;
    mssql) printf 'sa' ;;
    cubrid) printf 'dba' ;;
  esac
}

# 신규 설치와 이관의 공통 질문이다(폴더, DB, 사이트 주소, 메일, SSH, 서비스).
flow_ask_install() {
  local os type methods pkg svc
  os="$(ans_get env.os linux)"
  pkg="$(ans_get env.pkg none)"
  ask_yesno service.register prompt.service true || return 2
  svc="$(ans_get service.register false)"
  ask_text install_dir prompt.install_dir "$(default_install_dir "$os" "$svc")" || return 2
  if [ "$(ans_get env.mode)" = "migrate" ]; then
    ask_text v16.dir prompt.v16_dir "" || return 2
    # 이관에서 데이터 폴더의 기본은 v1.6 폴더이다(저장소 repo/와 첨부 uploads/의 배치가 같다)
    ask_text data_dir prompt.data_dir "$(ans_get v16.dir)" || return 2
    ask_choice v16.db prompt.v16_db "mariadb mysql h2" mariadb || return 2
    ask_text v16.conf prompt.v16_conf "$(ans_get v16.dir)/conf/application.conf" || return 2
    ans_has v16.conf || ans_set v16.conf "$(ans_get v16.dir)/conf/application.conf"
    ask_yesno confirm.backup prompt.backup false || return 2
    if [ "$(ans_get v16.db)" = "h2" ]; then
      ask_text v16.timezone prompt.v16_timezone "$(ans_get env.tz)" || return 2
    fi
  fi
  # 신규 설치의 데이터 폴더(이관은 위에서 이미 정했다)
  ask_text data_dir prompt.data_dir "$(default_data_dir "$os" "$svc")" || return 2
  if [ "$(ans_get env.mode)" != "migrate" ] || [ "$(ans_get v16.db)" = "h2" ]; then
    ask_choice db.type prompt.db_type "postgresql mariadb cubrid mssql h2" postgresql || return 2
    type="$(ans_get db.type)"
    if [ -n "$type" ] && [ "$type" != "h2" ]; then
      ask_choice db.install prompt.db_install "together separate" together || return 2
      if [ "$(ans_get db.install)" = "together" ]; then
        methods="$(db_methods "$os" "$pkg" "$type")"
        case "$methods" in
          container) ans_has db.method || ans_set db.method container ;;
          *) ask_choice db.method prompt.db_method "$methods" container || return 2 ;;
        esac
        ask_text db.name prompt.db_name yona optional || return 2
        ask_text db.user prompt.db_user yona optional || return 2
        ask_secret db.password_file prompt.db_password || return 2
        ask_text db.admin_user prompt.db_admin_user "$(_yi_default_admin "$type")" optional || return 2
        ask_secret db.admin_password_file prompt.db_admin_password || return 2
      else
        ask_text db.host prompt.db_host "" || return 2
        ask_text db.port prompt.db_port "$(db_default_port "$type")" optional || return 2
        ask_text db.name prompt.db_name yona optional || return 2
        ask_text db.user prompt.db_user yona optional || return 2
        ask_secret db.password_file prompt.db_password || return 2
      fi
    fi
  fi
  ask_text site.hostname prompt.hostname "" || return 2
  ask_choice site.scheme prompt.scheme "http https" http optional || return 2
  if [ "$(ans_get site.scheme)" = "https" ]; then
    ask_yesno site.behind_https_proxy prompt.https_proxy false optional || return 2
  fi
  ask_text web.port prompt.web_port 8080 optional || return 2
  ask_yesno mail.enabled prompt.mail false optional || return 2
  if [ "$(ans_get mail.enabled)" = "true" ]; then
    ask_text mail.host prompt.mail_host "" || return 2
    ask_text mail.port prompt.mail_port 587 || return 2
    ask_yesno mail.ssl prompt.mail_ssl false optional || return 2
    ask_text mail.user prompt.mail_user "" || return 2
    ask_secret mail.password_file prompt.mail_password || return 2
    ask_text mail.from_user prompt.mail_from_user "" || return 2
    ask_text mail.from_domain prompt.mail_from_domain "" || return 2
    ask_yesno mail.mock prompt.mail_mock false || return 2
  fi
  if [ "$svc" = "true" ] && [ "$os" != "windows" ]; then
    ask_choice ssh.mode prompt.ssh "none builtin system" none optional || return 2
  else
    ask_choice ssh.mode prompt.ssh "none builtin" none optional || return 2
  fi
  if [ "$(ans_get ssh.mode)" = "builtin" ]; then
    ask_text ssh.port prompt.ssh_port 2222 optional || return 2
    ask_text ssh.public_port prompt.ssh_public_port "" || return 2
  fi
}

# --- 준비 폴더 ---

# 준비 폴더(`env.stage`)에 설정, 서비스 파일, SQL, 스크립트를 만든다. 이 폴더에는 비밀 값이 있어 끝나면 지운다.
flow_stage_build() {
  local stage secrets host
  stage="$(mktemp -d "${TMPDIR:-/tmp}/yona-install-stage.XXXXXX")"
  chmod 700 "$stage"
  ans_set env.stage "$stage"
  mkdir -p "$stage/conf" "$stage/bin"
  render_application_yml > "$stage/conf/application.yml"
  secrets="$(render_secrets_properties)"
  if [ -n "$secrets" ]; then
    ( umask 077; printf '%s\n' "$secrets" > "$stage/conf/secrets.properties" )
  fi
  render_run_script start > "$stage/bin/yona-start.sh"
  render_run_script stop > "$stage/bin/yona-stop.sh"
  render_run_script status > "$stage/bin/yona-status.sh"
  cp "$YI_DIR/templates/wait-for-port.sh" "$stage/bin/wait-for-port.sh"
  if [ "$(ans_get service.register)" = "true" ]; then
    if [ "$(ans_get env.os linux)" = "mac" ]; then
      render_launchd_plist > "$stage/dev.yona.server.plist"
    else
      render_systemd_unit > "$stage/yona.service"
    fi
  fi
  if [ "$(ans_get db.install)" = "together" ]; then
    if [ "$(ans_get db.method)" = "package" ]; then host="localhost"; else host="%"; fi
    {
      render_db_sql "$host"
      [ "$(ans_get db.method)" = "package" ] && render_db_admin_sql
    } > "$stage/db.sql"
    render_db_drop_sql "$host" > "$stage/db-drop.sql"
    if [ "$(ans_get db.method)" = "container" ]; then
      ( umask 077; render_db_env > "$stage/db.env" )
    fi
  fi
  if [ "$(ans_get ssh.mode)" = "system" ]; then
    flow_stage_ssh_helper "$stage" || return 1
  fi
  render_report > "$stage/INSTALL-REPORT.md"
  printf '%s\n' "$(ans_get version)" > "$stage/VERSION"
}

# 시스템 sshd 연동에 필요한 파일(도우미 실행 파일, 공유 비밀 값, 설정, 드롭인)을 준비한다.
# 도우미(`yona-ssh`)는 `--ssh-helper`로 준 파일이나 같은 폴더의 `yona-ssh-<운영체제>-<아키텍처>`를 쓴다.
flow_stage_ssh_helper() {
  local stage="$1" helper arch secret
  helper="$(ans_get ssh.helper)"
  arch="$(ans_get env.arch)"
  [ -n "$helper" ] || helper="$YI_DIR/yona-ssh-$(ans_get env.os)-$arch"
  if [ ! -f "$helper" ]; then
    flow_fail 1 err.ssh_helper "$helper"
    return 1
  fi
  cp "$helper" "$stage/yona-ssh"
  secret="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 48)"
  ans_set ssh.relay_secret "$secret"
  ( umask 077; printf '%s' "$secret" > "$stage/ssh-internal-secret" )
  printf 'YONA_INTERNAL_URL=http://127.0.0.1:%s\nYONA_SSH_RELAY_SOCKET=%s/ssh/relay.sock\n' "$(ans_get web.port 8080)" "$(ans_get data_dir)" > "$stage/ssh-auth.conf"
  render_sshd_dropin > "$stage/sshd-50-yona.conf"
  # 비밀 값 파일을 다시 만든다(공유 비밀 값이 들어가야 한다)
  ( umask 077; render_secrets_properties > "$stage/conf/secrets.properties" )
}

# 준비 폴더를 지운다(비밀 값이 있다).
flow_stage_clean() {
  [ -n "$(ans_get env.stage)" ] && rm -rf "$(ans_get env.stage)"
  [ -n "${YI_SECRET_DIR:-}" ] && rm -rf "$YI_SECRET_DIR"
  return 0
}

# --- 상태 확인 ---

# 상태 확인 주소가 UP을 돌려줄 때까지 기다린다(첫 시작은 스키마를 만드느라 오래 걸릴 수 있다). 사용법: flow_wait_health [최대 초]
flow_wait_health() {
  local limit="${1:-300}" waited=0 port
  port="$(ans_get web.port 8080)"
  while [ "$waited" -lt "$limit" ]; do
    if curl -fsS "http://127.0.0.1:$port/-_-actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

# --- 오류 출력 ---

# 검증 오류 줄(`invalid|키|값`)을 사람이 읽는 문장으로 출력한다.
flow_print_validation() {
  local kind key value
  while IFS='|' read -r kind key value; do
    [ -n "$kind" ] || continue
    printf '%s\n' "$(msg "err.$kind" "$key" "$value")" >&2
  done
}

# 사전 점검 줄(`FAIL|코드|상세`, `WARN|...`)을 출력한다.
flow_print_preflight() {
  local level code detail
  while IFS='|' read -r level code detail; do
    [ -n "$level" ] || continue
    case "$level" in
      FAIL) printf '%s\n' "$(msg "err.pf.$code" "$detail")" >&2 ;;
      WARN) printf '%s\n' "$(msg "warn.pf.$code" "$detail")" ;;
    esac
  done
}

# --- 흐름 ---

# 설치(신규, 이관) 흐름이다.
flow_install_new() {
  local missing out rc plan_priv
  flow_ask_install || return 2
  answers_normalize
  out="$(answers_validate)" || { printf '%s\n' "$out" | flow_print_validation; return 2; }
  missing="$(answers_required "$(ans_get env.mode install)")"
  if [ -n "$missing" ]; then
    printf '%s\n' "$(msg err.missing_header)" >&2
    # shellcheck disable=SC2086  # 키 목록을 단어로 나눠 한 줄씩 보인다
    printf '  - %s\n' $missing >&2
    return 2
  fi
  ans_set service.register "$(ans_get service.register false)"
  # 사전 점검: 모든 점검을 한 번에 돌려 실패 항목을 모두 보인다.
  out="$(preflight_run)"; rc=$?
  pf_java >/dev/null 2>&1 || true
  pf_container >/dev/null 2>&1 || true
  [ -n "$out" ] && printf '%s\n' "$out" | flow_print_preflight
  [ "$rc" -ne 0 ] && { printf '%s\n' "$(msg err.preflight_failed)" >&2; return 1; }
  ans_set env.record_dir "$(flow_record_dir)"
  printf '\n%s\n' "$(msg plan.header)"
  plan_print
  plan_priv="$(plan_privileged_summary)"
  if [ -n "$plan_priv" ]; then
    printf '\n%s\n%s\n' "$(msg plan.sudo_header)" "$plan_priv"
  fi
  if [ "$(ans_get dry_run)" = "true" ]; then
    printf '\n%s\n' "$(msg plan.dry_run_done)"
    return 0
  fi
  if prompt_available; then
    ask_yesno confirm.proceed prompt.proceed true || return 2
    if [ "$(ans_get confirm.proceed)" != "true" ]; then
      printf '%s\n' "$(msg info.cancelled)"
      return 0
    fi
  fi
  flow_do_install
}

# v1.6 이관을 실행한다. 릴리스 자산인 이관 스크립트(`migrate-v16.sh`, H2는 `migrate-v16-h2.sh`)를 이 폴더에서 찾아 부른다.
# 서버를 켜기 전에(적용 스크립트보다 먼저) 준비한 jar로 설정 변환과 데이터 이관을 한다. 이 스크립트들은 실패하면 아무것도 바꾸지 않는다.
# - MariaDB/MySQL: 같은 DB를 제자리에서 이어받는다. 변환기가 만든 속성 파일을 conf/application.properties로 둔다.
# - H2: 옛 파일을 직접 읽어 새 DB(이 설치의 DB 설정)에 넣는다. 변환된 속성의 DB 접속 키(옛 H2)는 지운다.
flow_run_migration() {
  local stage jar script conf_out java_home rc
  stage="$(ans_get env.stage)"
  jar="$stage/yona.jar"
  conf_out="$stage/conf/application.properties"
  if [ "$(ans_get v16.db)" = "h2" ]; then script="$YI_DIR/migrate-v16-h2.sh"; else script="$YI_DIR/migrate-v16.sh"; fi
  if [ ! -f "$script" ]; then
    flow_fail 1 err.migrate_script "$script"
    return 1
  fi
  java_home="$(dirname "$(dirname "$(ans_get env.java)")")"
  printf '%s\n' "$(msg info.migrating)"
  if [ "$(ans_get v16.db)" = "h2" ]; then
    JAVA_HOME="$java_home" V16_TIMEZONE="$(ans_get v16.timezone)" bash "$script" "$jar" \
      --v16-dir="$(ans_get v16.dir)" --yes --v16-conf="$(ans_get v16.conf)" --v16-conf-out="$conf_out" --v16-conf-force \
      --yona.data-dir="$(ans_get data_dir)" --spring.config.additional-location="file:$stage/conf/"
    rc=$?
    [ -f "$conf_out" ] && sed -i.bak '/^spring\.datasource\./d' "$conf_out" && rm -f "$conf_out.bak"
  else
    JAVA_HOME="$java_home" bash "$script" "$jar" \
      --v16-conf="$(ans_get v16.conf)" --v16-conf-out="$conf_out" --v16-conf-force --yona.data-dir="$(ans_get data_dir)"
    rc=$?
  fi
  if [ "$rc" -ne 0 ]; then
    flow_fail 1 err.migrate_failed
    return 1
  fi
  [ -f "$conf_out" ] && chmod 600 "$conf_out"
  return 0
}

# 계획을 실제로 실행한다: 준비, 내려받기, (이관), 적용(필요하면 격상), 시작, 상태 확인, 결과 문서.
flow_do_install() {
  local apply
  flow_stage_build || { flow_stage_clean; return 1; }
  flow_fetch_jar || { flow_stage_clean; return 1; }
  if [ "$(ans_get env.mode)" = "migrate" ]; then
    flow_run_migration || { flow_stage_clean; return 1; }
  fi
  apply="$(ans_get env.stage)/apply.sh"
  render_apply_script > "$apply"
  printf '\n%s\n' "$(msg info.applying)"
  if ! flow_run_script "$apply"; then
    flow_stage_clean
    flow_fail 1 err.apply_failed
    return 1
  fi
  if [ "$(ans_get service.register)" != "true" ]; then
    "$(ans_get install_dir)/bin/yona-start.sh" || true
  fi
  printf '%s\n' "$(msg info.waiting_health)"
  if ! flow_wait_health "${YI_HEALTH_TIMEOUT:-300}"; then
    printf '%s\n' "$(msg err.health_failed)" >&2
    if [ "$(ans_get keep_on_failure)" != "true" ]; then
      [ "$(ans_get service.register)" != "true" ] && "$(ans_get install_dir)/bin/yona-stop.sh" 2>/dev/null
      flow_run_script "$apply" rollback
    fi
    flow_stage_clean
    return 1
  fi
  printf '\n%s\n' "$(msg info.done)"
  printf '%s\n' "$(msg info.report_at "$(ans_get install_dir)/INSTALL-REPORT.md")"
  flow_stage_clean
  return 0
}

# 업그레이드 흐름이다: 이미 설치된 다른 버전을 새 jar로 바꾼다. 설정과 데이터는 건드리지 않는다.
flow_upgrade() {
  local rec="$1" installed="$2" confirm script out
  ans_set env.mode upgrade
  ans_set env.old_version "$installed"
  ans_set install_dir "$(record_get "$rec" install_dir)"
  ans_set data_dir "$(record_get "$rec" data_dir)"
  ans_set service.register "$(record_get "$rec" service)"
  ans_set env.os "$(record_get "$rec" os)"
  ans_set db.type "$(record_get "$rec" db_type)"
  ans_set env.record_dir "$(dirname "$rec")"
  confirm="$(upgrade_needs_confirm "$installed" "$(ans_get version)")"
  printf '%s\n' "$(msg upgrade.found "$installed" "$(ans_get version)")"
  if [ "$confirm" = "yes" ]; then
    ask_yesno confirm.prerelease prompt.prerelease false || return 2
    if [ "$(ans_get confirm.prerelease)" != "true" ]; then
      flow_fail 2 err.prerelease_unconfirmed
      return 2
    fi
  fi
  ask_yesno confirm.backup prompt.backup false || return 2
  out="$(answers_required upgrade)"
  if [ -n "$out" ]; then
    printf '%s\n' "$(msg err.missing_header)" >&2
    # shellcheck disable=SC2086
    printf '  - %s\n' $out >&2
    return 2
  fi
  if [ "$(ans_get confirm.backup)" != "true" ]; then
    flow_fail 2 err.backup_unconfirmed
    return 2
  fi
  printf '\n%s\n' "$(msg plan.header)"
  plan_print
  if [ "$(ans_get dry_run)" = "true" ]; then
    printf '\n%s\n' "$(msg plan.dry_run_done)"
    return 0
  fi
  local stage
  stage="$(mktemp -d "${TMPDIR:-/tmp}/yona-install-stage.XXXXXX")"
  chmod 700 "$stage"
  ans_set env.stage "$stage"
  flow_fetch_jar || { flow_stage_clean; return 1; }
  script="$stage/upgrade.sh"
  render_upgrade_script > "$script"
  if ! flow_run_script "$script"; then
    flow_stage_clean
    flow_fail 1 err.upgrade_failed
    return 1
  fi
  printf '\n%s\n' "$(msg info.upgraded "$(ans_get version)")"
  flow_stage_clean
}

# 제거 흐름이다: 설치 기록을 읽어 서비스를 끄고 설치한 파일을 지운다. `--purge`이면 데이터와 컨테이너 DB도 지운다.
flow_uninstall() {
  local rec script stage
  rec="$(record_find)" || { flow_fail 1 err.not_installed; return 1; }
  ans_set install_dir "$(record_get "$rec" install_dir)"
  ans_set data_dir "$(record_get "$rec" data_dir)"
  ans_set service.register "$(record_get "$rec" service)"
  ans_set env.os "$(record_get "$rec" os)"
  ans_set db.install "$(record_get "$rec" db_install)"
  ans_set db.method "$(record_get "$rec" db_method)"
  ans_set db.type "$(record_get "$rec" db_type)"
  ans_set env.record_dir "$(dirname "$rec")"
  ans_set env.containers "$(ans_get env.containers docker)"
  printf '%s\n' "$(msg uninstall.target "$(ans_get install_dir)")"
  if [ "$(ans_get purge)" = "true" ]; then
    printf '%s\n' "$(msg uninstall.purge_warning "$(ans_get data_dir)")"
    if prompt_available; then
      ask_text confirm.purge_path prompt.purge_confirm "" || return 2
      if [ "$(ans_get confirm.purge_path)" != "$(ans_get data_dir)" ]; then
        flow_fail 2 err.purge_unconfirmed
        return 2
      fi
    elif [ "$(ans_get yes)" != "true" ]; then
      flow_fail 2 err.purge_unconfirmed
      return 2
    fi
  fi
  if [ "$(ans_get dry_run)" = "true" ]; then
    printf '%s\n' "$(msg plan.dry_run_done)"
    return 0
  fi
  stage="$(mktemp -d "${TMPDIR:-/tmp}/yona-install-stage.XXXXXX")"
  chmod 700 "$stage"
  ans_set env.stage "$stage"
  script="$stage/uninstall.sh"
  render_uninstall_script > "$script"
  if ! flow_run_script "$script"; then
    flow_stage_clean
    return 1
  fi
  printf '%s\n' "$(msg uninstall.done)"
  flow_stage_clean
}

# 진입점이다. 사용법: flow_main [install|uninstall|help] [옵션...]
flow_main() {
  local cmd="install" out lang rec installed state
  case "${1:-}" in
    install | uninstall) cmd="$1"; shift ;;
    help | --help | -h) msg_load en; printf '%s\n' "$(msg usage.text)"; return 0 ;;
  esac
  answers_load "$@" || return 2
  lang="$(detect_lang "$(ans_get lang)" "" "$(system_locale)")" || { printf 'unsupported language\n' >&2; return 2; }
  msg_load "$lang"
  printf '%s\n' "$(msg lang.using "$lang")"
  answers_normalize
  out="$(answers_validate)" || { printf '%s\n' "$out" | flow_print_validation; return 2; }
  detect_env
  case "$cmd" in
    uninstall) flow_uninstall; return $? ;;
  esac
  # 이미 설치된 것이 있는가
  installed=""
  if rec="$(record_find)"; then
    installed="$(record_get "$rec" version)"
  fi
  flow_load_candidates || return 1
  flow_select_version || return $?
  ans_has version || { printf '%s\n' "$(msg err.missing_header)" >&2; printf '  - version\n' >&2; return 2; }
  if [ "$(ans_get download_only)" = "true" ]; then
    flow_download_only
    return $?
  fi
  state="$(install_state "$installed" "$(ans_get version)")"
  case "$state" in
    same) printf '%s\n' "$(msg info.already_installed "$installed")"; return 0 ;;
    upgrade) flow_upgrade "$rec" "$installed"; return $? ;;
    downgrade)
      if [ "$(ans_get allow_downgrade)" != "true" ]; then
        flow_fail 2 err.downgrade "$installed" "$(ans_get version)"
        return 2
      fi
      flow_upgrade "$rec" "$installed"
      return $?
      ;;
  esac
  ask_choice mode prompt.mode "install migrate" install optional || return 2
  ans_set env.mode "$(ans_get mode install)"
  flow_install_new
}

# 폐쇄 환경용 묶음을 만든다: 고른 릴리스의 jar와 sha256 파일을 이 스크립트가 있는 폴더에 받는다. 이 폴더째 옮겨 실행하면 된다.
# JDK와 DB 설치 파일은 받지 않는다(서버에 미리 있어야 한다).
flow_download_only() {
  local url jar sum
  url="$(ans_get env.jar_url)"
  jar="$YI_DIR/yona-$(ans_get version | sed 's/^v//').jar"
  printf '%s\n' "$(msg info.downloading "$url")"
  case "$url" in
    /*) cp "$url" "$jar" || return 1 ;;
    *) curl -fL --retry 2 -o "$jar" "$url" || { flow_fail 1 err.download "$url"; return 1; } ;;
  esac
  if verify_digest "$jar" "$(ans_get env.jar_digest)"; then
    printf '%s\n' "$(msg info.verified)"
  elif [ $? -eq 1 ]; then
    rm -f "$jar"
    flow_fail 1 err.digest_mismatch
    return 1
  else
    printf '%s\n' "$(msg warn.not_verified)"
  fi
  sum="$(sha256_of "$jar")"
  printf '%s  %s\n' "$sum" "$(basename "$jar")" > "$jar.sha256"
  printf '%s\n' "$(msg info.bundle_ready "$YI_DIR")"
}
