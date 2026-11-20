#!/usr/bin/env bash
# 공통 시나리오(scenarios/<이름>/answers.env)의 산출물을 만들어 골든 파일(expected/)과 견준다.
# 셸과 PowerShell 구현이 같은 입력에서 같은 설정 파일과 SQL을 만들어야 하므로 같은 골든 파일을 둘 다 쓴다.
# 사용법: . test/scenarios.sh; scenarios_check_all   (골든 파일 갱신: scenarios_update, 의도했을 때만)

# 시나리오 하나의 산출물을 폴더에 만든다. 사용법: scenario_render <시나리오 폴더> <출력 폴더>
scenario_render() {
  local dir="$1" out="$2" host
  mkdir -p "$out"
  ans_clear
  # 답 파일의 %SCENARIO%를 시나리오 폴더로 바꾼 사본을 읽는다
  sed "s#%SCENARIO%#$dir#g" "$dir/answers.env" > "$out/.answers.env"
  answers_load --answers="$out/.answers.env" || return 1
  answers_normalize
  render_application_yml > "$out/application.yml"
  render_secrets_properties > "$out/secrets.properties"
  if [ "$(ans_get db.install)" = "together" ]; then
    if [ "$(ans_get db.method)" = "package" ]; then host="localhost"; else host="%"; fi
    render_db_sql "$host" > "$out/db.sql"
    render_db_env > "$out/db.env"
  fi
  rm -f "$out/.answers.env"
}

# 모든 시나리오를 골든 파일과 견준다. 다르면 실패한다.
scenarios_check_all() {
  local root name tmp rc=0 f
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scenarios" && pwd)"
  for name in $(ls "$root"); do
    tmp="$(mktemp -d)"
    scenario_render "$root/$name" "$tmp" || { echo "  FAIL $name: 렌더링 실패"; rc=1; continue; }
    for f in "$tmp"/*; do
      if [ -f "$root/$name/expected/$(basename "$f")" ] && diff -q "$f" "$root/$name/expected/$(basename "$f")" >/dev/null; then
        :
      else
        echo "  FAIL 시나리오 $name: $(basename "$f")가 골든 파일과 다릅니다"; rc=1
      fi
    done
    [ "$rc" -eq 0 ] && echo "  ok   시나리오 $name"
    rm -rf "$tmp"
  done
  return "$rc"
}

# 골든 파일을 현재 출력으로 갱신한다(의도한 변경일 때만 쓰고, 바뀐 내용을 눈으로 검토한다).
scenarios_update() {
  local root name
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scenarios" && pwd)"
  for name in $(ls "$root"); do
    rm -rf "$root/$name/expected"
    scenario_render "$root/$name" "$root/$name/expected" || return 1
  done
}
