# shellcheck shell=bash
# 설치자의 답을 모으고 검증한다. 답은 명령줄 옵션, 답 파일(`--answers=파일`), 환경 변수(`YONA_INSTALL_<키>`)로 줄 수 있고,
# 우선순위는 옵션, 답 파일, 환경 변수 순이다. 질문에 대한 답과 비대화형 모드의 입력이 같은 키를 쓴다.
# 키와 옵션 이름은 `roadmap.md`의 "X-63 비대화형 답 항목표"와 같다.

# 답 항목표이다. 형식은 `키|옵션|종류`이고 종류는 value(값을 받음), flag(값이 없으면 true), skip(따로 처리)이다.
YI_OPTIONS='lang|--lang|value
mode|--mode|value
version|--version|value
install_dir|--install-dir|value
data_dir|--data-dir|value
db.type|--db|value
db.install|--db-install|value
db.method|--db-method|value
db.host|--db-host|value
db.port|--db-port|value
db.name|--db-name|value
db.user|--db-user|value
db.password_file|--db-password-file|value
db.admin_user|--db-admin-user|value
db.admin_password_file|--db-admin-password-file|value
site.hostname|--hostname|value
site.port|--public-port|value
site.scheme|--scheme|value
site.behind_https_proxy|--behind-https-proxy|flag
web.port|--port|value
mail.enabled|--mail-enabled|flag
mail.host|--mail-host|value
mail.port|--mail-port|value
mail.ssl|--mail-ssl|flag
mail.user|--mail-user|value
mail.password_file|--mail-password-file|value
mail.from_user|--mail-from-user|value
mail.from_domain|--mail-from-domain|value
mail.mock|--mail-mock|flag
ssh.mode|--ssh|value
ssh.port|--ssh-port|value
ssh.public_port|--ssh-public-port|value
ssh.helper|--ssh-helper|value
v16.dir|--v16-dir|value
v16.conf|--v16-conf|value
v16.db|--v16-db|value
v16.timezone|--v16-timezone|value
service.register|--service|flag
install_jdk|--install-jdk|flag
confirm.backup|--confirm-backup|flag
confirm.replace_data|--confirm-replace-data|flag
yes|--yes|flag
yes|--non-interactive|flag
dry_run|--dry-run|flag
offline|--offline|flag
download_only|--download-only|flag
keep_on_failure|--keep-on-failure|flag
allow_downgrade|--allow-downgrade|flag
purge|--purge|flag
answers|--answers|skip'

# 불리언 값을 받는 키이다.
YI_BOOL_KEYS="site.behind_https_proxy mail.enabled mail.ssl mail.mock service.register install_jdk confirm.backup confirm.replace_data yes dry_run offline download_only keep_on_failure allow_downgrade purge"

# 옵션 이름으로 표를 찾아 `_yi_found_key`, `_yi_found_kind`에 담는다. 없으면 실패한다.
_yi_opt_lookup() {
  local line
  _yi_found_key=""
  _yi_found_kind=""
  while IFS='|' read -r key opt kind; do
    if [ "$opt" = "$1" ]; then
      _yi_found_key="$key"
      _yi_found_kind="$kind"
      return 0
    fi
  done <<EOF2
$YI_OPTIONS
EOF2
  return 1
}

# 환경 변수 이름을 만든다. 예: `db.type` -> `YONA_INSTALL_DB_TYPE`
_yi_env_name() {
  printf 'YONA_INSTALL_%s' "$(yi_key "$1" | tr '[:lower:]' '[:upper:]')"
}

# 환경 변수에서 답을 읽는다(우선순위 가장 낮음).
_yi_load_env() {
  local key opt kind name
  while IFS='|' read -r key opt kind; do
    [ "$kind" = "skip" ] && continue
    name="$(_yi_env_name "$key")"
    if [ -n "${!name-}" ]; then
      ans_set "$key" "${!name}"
    fi
  done <<EOF2
$YI_OPTIONS
EOF2
}

# 답 파일을 읽는다. 형식은 `키=값`이고 `#`로 시작하는 줄과 빈 줄은 건너뛴다. 키와 값 앞뒤 공백은 지운다.
_yi_load_file() {
  local file="$1" line key value
  if [ ! -f "$file" ]; then
    yi_err "answers file not found: $file"
    return 1
  fi
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      '' | '#'*) continue ;;
    esac
    case "$line" in
      *=*) ;;
      *) continue ;;
    esac
    key=${line%%=*}
    value=${line#*=}
    key="$(printf '%s' "$key" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
    value="$(printf '%s' "$value" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
    [ -n "$key" ] && ans_set "$key" "$value"
  done <"$file"
}

# 옵션을 읽는다. `--키=값`, `--키 값`, 값이 없는 플래그를 받는다.
_yi_load_args() {
  local arg name value
  while [ "$#" -gt 0 ]; do
    arg="$1"
    shift
    case "$arg" in
      --*) ;;
      *) yi_err "unexpected argument: $arg"; return 1 ;;
    esac
    name=${arg%%=*}
    if ! _yi_opt_lookup "$name"; then
      yi_err "unknown option: $name"
      return 1
    fi
    if [ "$_yi_found_kind" = "skip" ]; then
      # `--answers 파일` 모양이면 다음 인자를 건너뛴다.
      case "$arg" in *=*) ;; *) shift ;; esac
      continue
    fi
    case "$arg" in
      *=*) value=${arg#*=} ;;
      *)
        if [ "$_yi_found_kind" = "flag" ]; then
          value="true"
        else
          if [ "$#" -eq 0 ]; then
            yi_err "option requires a value: $name"
            return 1
          fi
          value="$1"
          shift
        fi
        ;;
    esac
    ans_set "$_yi_found_key" "$value"
  done
}

# 옵션에서 `--answers` 파일 경로를 찾는다.
_yi_find_answers_file() {
  local prev=""
  for arg in "$@"; do
    case "$arg" in
      --answers=*) printf '%s' "${arg#--answers=}"; return 0 ;;
    esac
    if [ "$prev" = "--answers" ]; then
      printf '%s' "$arg"
      return 0
    fi
    prev="$arg"
  done
}

# 환경 변수, 답 파일, 옵션 순으로 읽어 나중 것이 앞의 것을 덮는다.
# 사용법: answers_load [옵션...]
answers_load() {
  local file
  _yi_load_env
  file="$(_yi_find_answers_file "$@")"
  if [ -n "$file" ]; then
    _yi_load_file "$file" || return 1
  fi
  _yi_load_args "$@"
}

# 불리언 답을 true, false로 맞춘다(yes, no, true, false를 대소문자 구분 없이 받는다). 알 수 없는 값은 그대로 둔다.
answers_normalize() {
  local key value
  for key in $YI_BOOL_KEYS; do
    value="$(yi_lower "$(ans_get "$key")")"
    case "$value" in
      yes | true | y | 1) ans_set "$key" true ;;
      no | false | n | 0) ans_set "$key" false ;;
    esac
  done
}

_yi_is_port() {
  case "$1" in
    '' | *[!0-9]*) return 1 ;;
  esac
  local n=$((10#$1))
  [ "$n" -ge 1 ] && [ "$n" -le 65535 ]
}

# 허용 목록과 견주어 오류를 `invalid|키|값` 한 줄씩 돌려준다.
_yi_check_enum() {
  local key="$1" allowed="$2" value
  value="$(ans_get "$key")"
  [ -z "$value" ] && return 0
  case " $allowed " in
    *" $value "*) ;;
    *) printf 'invalid|%s|%s\n' "$key" "$value" ;;
  esac
}

# 검증 오류를 `invalid|키|값` 줄로 출력한다. `answers_validate`가 모아 쓴다.
# bash 3.2는 `$( )` 안의 `case` 패턴 괄호를 잘못 읽으므로 본문을 함수로 분리했다.
_yi_validate_collect() {
  local key value
  _yi_check_enum lang "ko en"
  _yi_check_enum mode "install migrate"
  _yi_check_enum db.type "postgresql mariadb cubrid mssql h2"
  _yi_check_enum db.install "together separate"
  _yi_check_enum db.method "container package"
  _yi_check_enum site.scheme "http https"
  _yi_check_enum ssh.mode "none builtin system"
  _yi_check_enum v16.db "mariadb mysql h2"
  for key in web.port site.port ssh.port ssh.public_port db.port mail.port; do
    value="$(ans_get "$key")"
    if [ -n "$value" ] && ! _yi_is_port "$value"; then
      printf 'invalid|%s|%s\n' "$key" "$value"
    fi
  done
  for key in db.name db.user; do
    value="$(ans_get "$key")"
    if [ -n "$value" ] && ! printf '%s' "$value" | grep -Eq '^[a-z][a-z0-9_]{0,62}$'; then
      printf 'invalid|%s|%s\n' "$key" "$value"
    fi
  done
  value="$(ans_get db.admin_user)"
  if [ -n "$value" ] && ! printf '%s' "$value" | grep -Eq '^[A-Za-z][A-Za-z0-9_]{0,62}$'; then
    printf 'invalid|db.admin_user|%s\n' "$value"
  fi
  value="$(ans_get version)"
  if [ -n "$value" ] && ! printf '%s' "$value" | grep -Eq '^v?[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$'; then
    printf 'invalid|version|%s\n' "$value"
  fi
  for key in $YI_BOOL_KEYS; do
    value="$(ans_get "$key")"
    if [ -n "$value" ] && [ "$value" != "true" ] && [ "$value" != "false" ]; then
      printf 'invalid|%s|%s\n' "$key" "$value"
    fi
  done
  if [ "$(ans_get ssh.mode)" = "builtin" ] && [ -n "$(ans_get ssh.port)" ] && [ "$(ans_get ssh.port)" = "$(ans_get web.port 8080)" ]; then
    printf 'conflict|ssh.port|%s\n' "$(ans_get ssh.port)"
  fi
}

# 답을 검증한다. 오류를 모두 모아 `invalid|키|값` 줄로 출력하고, 하나라도 있으면 실패한다.
answers_validate() {
  answers_normalize
  local out
  out="$(_yi_validate_collect)"
  if [ -n "$out" ]; then
    printf '%s\n' "$out"
    return 1
  fi
  return 0
}

# DB 종류와 설치 방법에 따라 필요한 답을 돌려준다.
_yi_required_db() {
  local type install
  type="$(ans_get db.type)"
  [ -z "$type" ] && { printf 'db.type\n'; return; }
  [ "$type" = "h2" ] && return
  install="$(ans_get db.install)"
  [ -z "$install" ] && { printf 'db.install\n'; return; }
  if [ "$install" = "together" ]; then
    for key in db.method db.name db.user db.password_file db.admin_password_file; do
      ans_has "$key" || printf '%s\n' "$key"
    done
  else
    for key in db.host db.port db.name db.user db.password_file; do
      ans_has "$key" || printf '%s\n' "$key"
    done
  fi
}

# 비대화형에서 모자란 필수 답의 키를 한 줄씩 돌려준다. 모드는 install, migrate, upgrade이다.
# 사용법: answers_required <모드>
answers_required() {
  local mode="$1" key
  case "$mode" in
    upgrade)
      for key in version confirm.backup; do
        ans_has "$key" || printf '%s\n' "$key"
      done
      return 0
      ;;
  esac
  for key in version install_dir data_dir service.register; do
    ans_has "$key" || printf '%s\n' "$key"
  done
  if [ "$mode" = "migrate" ]; then
    for key in v16.dir v16.db confirm.backup; do
      ans_has "$key" || printf '%s\n' "$key"
    done
    if [ "$(ans_get v16.db)" = "h2" ]; then
      ans_has v16.timezone || printf 'v16.timezone\n'
      _yi_required_db
    fi
  else
    _yi_required_db
  fi
  if [ "$(ans_get mail.enabled)" = "true" ] || [ "$(ans_get mail.enabled)" = "yes" ]; then
    for key in mail.host mail.port mail.mock; do
      ans_has "$key" || printf '%s\n' "$key"
    done
  fi
}
