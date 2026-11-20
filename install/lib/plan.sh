# shellcheck shell=bash
# 실행 계획: 선택한 답으로 어떤 단계를 어떤 순서로, 어떤 권한으로 하는지 정한다. `--dry-run`과 격상 동의 화면이 이 계획을 보인다.
# 계획의 각 줄은 `단계 id|관리자 권한 필요 여부(yes/no)`이다.

# 설치 폴더나 데이터 폴더에 쓰려면 관리자 권한이 필요한가.
_yi_files_priv() {
  if [ "$(ans_get service.register)" = "true" ]; then
    printf 'yes'
  elif ! path_writable "$(ans_get install_dir)" || ! path_writable "$(ans_get data_dir)"; then
    printf 'yes'
  else
    printf 'no'
  fi
}

# DB를 컨테이너로 설치할 때 관리자 권한이 필요한가(root이거나 컨테이너 도구를 쓸 수 있으면 필요 없다).
_yi_container_priv() {
  local tool
  tool="$(ans_get env.containers docker)"
  if [ "${YI_EUID:-$(id -u)}" = "0" ]; then
    printf 'no'
  elif command -v "$tool" >/dev/null 2>&1 && "$tool" info >/dev/null 2>&1; then
    printf 'no'
  else
    printf 'yes'
  fi
}

# 계획의 단계를 `id|priv` 줄로 출력한다. 모드가 upgrade이면 업그레이드 단계이다.
plan_steps() {
  if [ "$(ans_get env.mode)" = "upgrade" ]; then
    printf 'preflight|no\nfetch|no\nbackup-confirm|no\nreplace|%s\nrestart|%s\nhealth|no\nreport|no\n' \
      "$(_yi_files_priv)" "$([ "$(ans_get service.register)" = "true" ] && echo yes || echo no)"
    return 0
  fi
  local files_priv service
  files_priv="$(_yi_files_priv)"
  service="$(ans_get service.register)"
  printf 'preflight|no\nfetch|no\nstage|no\n'
  if [ "$(ans_get install_jdk)" = "true" ] && [ "$(ans_get env.need_jdk)" = "true" ]; then
    printf 'jdk|yes\n'
  fi
  if [ "$(ans_get db.install)" = "together" ]; then
    case "$(ans_get db.method)" in
      package) printf 'db|yes\n' ;;
      container) printf 'db|%s\n' "$(_yi_container_priv)" ;;
    esac
  fi
  [ "$service" = "true" ] && [ "$(ans_get env.os linux)" != "mac" ] && printf 'user|yes\n'
  printf 'files|%s\n' "$files_priv"
  [ "$service" = "true" ] && [ "$(ans_get ssh.mode)" = "system" ] && printf 'sshd|yes\n'
  [ "$service" = "true" ] && printf 'service|yes\n'
  printf 'start|no\nhealth|no\nreport|no\n'
}

# 관리자 권한이 필요한 단계의 id를 한 줄에 모아 돌려준다(없으면 빈 값).
plan_privileged_list() {
  plan_steps | awk -F'|' '$2 == "yes" { printf "%s ", $1 }' | sed 's/ $//'
}

# 격상이 필요한 단계와 이유를 보인다(동의를 받기 전에 보여 주는 화면).
plan_privileged_summary() {
  local id
  for id in $(plan_privileged_list); do
    printf '  - %s\n' "$(msg "step.$id") ($id)"
  done
}

# 계획 전체를 사람이 읽는 모양으로 출력한다. 권한이 필요한 단계에는 `[sudo]`를 붙인다.
plan_print() {
  local id priv n=1
  plan_steps | while IFS='|' read -r id priv; do
    if [ "$priv" = "yes" ]; then
      printf '%2d. [sudo] %s\n' "$n" "$(msg "step.$id")"
    else
      printf '%2d.        %s\n' "$n" "$(msg "step.$id")"
    fi
    n=$((n + 1))
  done
}
