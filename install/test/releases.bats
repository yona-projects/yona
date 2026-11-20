#!/usr/bin/env bats
# GitHub 릴리스 목록을 읽어 v2 이상 최근 4건을 고르는 일의 시험.
load test_helper

setup() { setup_tmp; . "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/releases.sh"; }
teardown() { teardown_tmp; }

each_fixture() { # 줄바꿈이 있는 JSON과 한 줄 JSON 모두로 같은 결과여야 한다
  for f in releases-pretty releases-minified; do "$@" "$BATS_TEST_DIRNAME/fixtures/$f.json" || return 1; done
}

tags_of() { releases_list_v2 < "$1" | cut -f1 | tr '\n' ' ' | sed 's/ $//'; }

@test "v2 이상 정식 릴리스를 높은 버전부터 최근 4건 고른다" {
  check() { [ "$(tags_of "$1")" = "v2.1.0 v2.0.1 v2.0.0 v2.0.0-rc2" ]; }
  each_fixture check
}

@test "초안(draft)과 v1 릴리스와 jar가 없는 릴리스는 뺀다" {
  check() { out="$(tags_of "$1")"; [[ "$out" != *"v2.2.0"* ]] && [[ "$out" != *"v1.16.0"* ]] && [[ "$out" != *"nojar"* ]]; }
  each_fixture check
}

@test "개수는 인자로 바꿀 수 있다" {
  run bash -c ". '$YI_DIR/lib/common.sh'; . '$YI_DIR/lib/releases.sh'; releases_list_v2 6 < '$BATS_TEST_DIRNAME/fixtures/releases-pretty.json' | cut -f1 | tr '\n' ' '"
  [ "$output" = "v2.1.0 v2.0.1 v2.0.0 v2.0.0-rc2 v2.0.0-rc1 " ]
}

@test "각 줄은 태그, 표시 이름, jar 주소, 크기, digest를 탭으로 나눈다" {
  line="$(releases_list_v2 < "$BATS_TEST_DIRNAME/fixtures/releases-pretty.json" | head -1)"
  [ "$(printf '%s' "$line" | cut -f1)" = "v2.1.0" ]
  [ "$(printf '%s' "$line" | cut -f3)" = "https://github.com/yona-projects/yona/releases/download/v2.1.0/yona-2.1.0.jar" ]
  [ "$(printf '%s' "$line" | cut -f4)" = "210" ]
  [ "$(printf '%s' "$line" | cut -f5)" = "sha256:$(printf '%064x' 210)" ]
}

@test "-plain.jar가 아니라 실행 jar를 고른다" {
  line="$(releases_list_v2 < "$BATS_TEST_DIRNAME/fixtures/releases-minified.json" | head -1)"
  [[ "$(printf '%s' "$line" | cut -f3)" != *"-plain.jar" ]]
}

@test "사전 배포 이름과 표시는 (rc) 같은 꼬리표를 붙인다" {
  rc1="$(releases_list_v2 6 < "$BATS_TEST_DIRNAME/fixtures/releases-pretty.json" | grep '^v2.0.0-rc1')"
  rc2="$(releases_list_v2 6 < "$BATS_TEST_DIRNAME/fixtures/releases-pretty.json" | grep '^v2.0.0-rc2')"
  stable="$(releases_list_v2 6 < "$BATS_TEST_DIRNAME/fixtures/releases-pretty.json" | grep '^v2.0.0	')"
  [ "$(printf '%s' "$rc1" | cut -f2)" = "v2.0.0-rc1 (rc)" ]
  [ "$(printf '%s' "$rc2" | cut -f2)" = "v2.0.0-rc2 (rc)" ]
  [ "$(printf '%s' "$stable" | cut -f2)" = "v2.0.0" ]
}

@test "본문에 따옴표와 JSON 조각이 있어도 잘못 읽지 않는다" {
  check() { [ "$(tags_of "$1")" = "v2.1.0 v2.0.1 v2.0.0 v2.0.0-rc2" ]; }
  each_fixture check
}

@test "실제 GitHub 응답(v2.0.0-rc1)을 읽는다" {
  line="$(releases_list_v2 < "$BATS_TEST_DIRNAME/fixtures/releases-real.json" | head -1)"
  [ "$(printf '%s' "$line" | cut -f1)" = "v2.0.0-rc1" ]
  [[ "$(printf '%s' "$line" | cut -f3)" == *"/yona-2.0.0-rc1.jar" ]]
  [[ "$(printf '%s' "$line" | cut -f5)" == sha256:* ]]
}

@test "릴리스 하나를 태그로 고른다(v가 있든 없든)" {
  out="$(releases_pick v2.0.1 < "$BATS_TEST_DIRNAME/fixtures/releases-pretty.json")"
  [ "$(printf '%s' "$out" | cut -f1)" = "v2.0.1" ]
  out="$(releases_pick 2.0.1 < "$BATS_TEST_DIRNAME/fixtures/releases-pretty.json")"
  [ "$(printf '%s' "$out" | cut -f1)" = "v2.0.1" ]
}

@test "없는 태그를 고르면 실패한다" {
  run bash -c ". '$YI_DIR/lib/common.sh'; . '$YI_DIR/lib/releases.sh'; releases_pick v9.9.9 < '$BATS_TEST_DIRNAME/fixtures/releases-pretty.json'"
  [ "$status" -ne 0 ]
}

# --- 버전 비교 ---

@test "버전 비교: 큰 쪽이 이긴다" {
  [ "$(version_cmp 2.0.1 2.0.0)" = "1" ]
  [ "$(version_cmp v2.0.0 2.0.1)" = "-1" ]
  [ "$(version_cmp v2.0.0 2.0.0)" = "0" ]
  [ "$(version_cmp 2.10.0 2.9.0)" = "1" ]
  [ "$(version_cmp 3.0.0 2.99.99)" = "1" ]
}

@test "버전 비교: 사전 배포는 같은 숫자의 정식보다 낮다" {
  [ "$(version_cmp 2.0.0-rc1 2.0.0)" = "-1" ]
  [ "$(version_cmp 2.0.0 2.0.0-rc2)" = "1" ]
  [ "$(version_cmp 2.0.0-rc2 2.0.0-rc10)" = "-1" ]
  [ "$(version_cmp 2.0.1-rc1 2.0.0)" = "1" ]
}

# --- digest 검증 ---

@test "sha256 digest가 맞으면 통과하고 틀리면 실패한다" {
  printf 'hello' > "$TMP_DIR/f"
  good="sha256:$(sha256_of "$TMP_DIR/f")"
  [ "$(sha256_of "$TMP_DIR/f")" = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" ]
  run verify_digest "$TMP_DIR/f" "$good"
  [ "$status" -eq 0 ]
  run verify_digest "$TMP_DIR/f" "sha256:$(printf '%064x' 1)"
  [ "$status" -ne 0 ]
}

@test "digest가 비어 있거나 sha256이 아니면 검증하지 못했다고 실패한다" {
  printf 'x' > "$TMP_DIR/f"
  run verify_digest "$TMP_DIR/f" ""
  [ "$status" -eq 2 ]
  run verify_digest "$TMP_DIR/f" "md5:abc"
  [ "$status" -eq 2 ]
}

# --- 폐쇄 환경: 같은 폴더의 jar ---

@test "같은 폴더의 yona-버전.jar에서 버전을 읽고 체크섬 파일로 검증한다" {
  printf 'jar' > "$TMP_DIR/yona-2.0.0.jar"
  printf '%s  yona-2.0.0.jar\n' "$(sha256_of "$TMP_DIR/yona-2.0.0.jar")" > "$TMP_DIR/yona-2.0.0.jar.sha256"
  run releases_local_list "$TMP_DIR"
  [ "$output" = "$(printf 'v2.0.0\tv2.0.0\t%s/yona-2.0.0.jar\t3\tsha256:%s' "$TMP_DIR" "$(sha256_of "$TMP_DIR/yona-2.0.0.jar")")" ]
}

@test "체크섬 파일이 없으면 digest 칸이 비어 있다" {
  printf 'jar' > "$TMP_DIR/yona-2.0.0-rc1.jar"
  run releases_local_list "$TMP_DIR"
  [ "$(printf '%s' "$output" | cut -f2)" = "v2.0.0-rc1 (rc)" ]
  [ -z "$(printf '%s' "$output" | cut -f5)" ]
}
