#!/usr/bin/env bats
# 공통 시나리오의 골든 파일 시험. PowerShell(test/ps/Render.Tests.ps1)도 같은 골든 파일과 견준다.
load test_helper

setup() {
  . "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/answers.sh"; . "$YI_DIR/lib/render.sh"; . "$YI_DIR/test/scenarios.sh"
}

@test "모든 시나리오의 산출물이 골든 파일과 같다" {
  run scenarios_check_all
  [ "$status" -eq 0 ]
}

@test "시나리오가 최소 네 개 있고 각각 골든 파일이 있다" {
  [ "$(ls "$YI_DIR/scenarios" | wc -l | tr -d ' ')" -ge 4 ]
  for d in "$YI_DIR"/scenarios/*/; do [ -f "$d/expected/application.yml" ]; done
}

@test "셸과 PowerShell의 답 항목표가 같다" {
  sh_table="$(sed -n "/^YI_OPTIONS='/,/'\$/p" "$YI_DIR/lib/answers.sh" | sed "s/^YI_OPTIONS='//; s/'\$//")"
  ps_table="$(sed -n "/^\$script:YiOptions = @'/,/^'@/p" "$YI_DIR/lib/ps/Core.ps1" | sed '1d;$d' | tr -d '\r')"
  [ "$sh_table" = "$ps_table" ]
}

@test "셸과 PowerShell의 불리언 키 목록이 같다" {
  sh_keys="$(sed -n 's/^YI_BOOL_KEYS="\(.*\)"/\1/p' "$YI_DIR/lib/answers.sh")"
  ps_keys="$(sed -n "s/^\$script:YiBoolKeys = '\(.*\)'.Split.*/\1/p" "$YI_DIR/lib/ps/Core.ps1" | tr -d '\r')"
  [ "$sh_keys" = "$ps_keys" ]
}
