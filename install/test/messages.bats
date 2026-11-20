#!/usr/bin/env bats
# 메시지 파일과 언어 감지 시험.
load test_helper

setup() { setup_tmp; . "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/messages.sh"; }
teardown() { teardown_tmp; }

@test "한국어와 영어 메시지 파일의 키 집합이 같다" {
  ko=$(grep -v '^#' "$YI_DIR/messages/ko.properties" | grep -v '^$' | cut -d= -f1 | sort)
  en=$(grep -v '^#' "$YI_DIR/messages/en.properties" | grep -v '^$' | cut -d= -f1 | sort)
  [ "$ko" = "$en" ]
}

@test "msg는 {1}, {2} 자리를 인자로 바꾼다" {
  msg_load en
  run msg "port.in_use" 8080
  [ "$status" -eq 0 ]
  [[ "$output" == *"8080"* ]]
}

@test "한국어를 불러오면 한국어 문구가 나온다" {
  msg_load ko
  run msg "port.in_use" 8080
  [[ "$output" == *"사용 중"* ]]
}

@test "없는 키는 키 이름을 그대로 돌려준다" {
  msg_load en
  run msg "no.such.key"
  [ "$output" = "no.such.key" ]
}

@test "한국어에 없는 키는 영어 문구로 대신한다" {
  printf 'only.en=hello {1}\n' > "$TMP_DIR/en.properties"
  printf 'other=x\n' > "$TMP_DIR/ko.properties"
  YI_MESSAGES_DIR="$TMP_DIR" msg_load ko
  run msg only.en world
  [ "$output" = "hello world" ]
}

@test "\\n은 줄바꿈으로 풀린다" {
  printf 'multi=a\\nb\n' > "$TMP_DIR/en.properties"
  YI_MESSAGES_DIR="$TMP_DIR" msg_load en
  run msg multi
  [ "${lines[0]}" = "a" ]
  [ "${lines[1]}" = "b" ]
}

# --- 언어 감지: --lang > YONA_INSTALL_LANG > 시스템 언어 > 영어 ---

@test "--lang이 가장 우선한다" {
  run detect_lang "en" "ko" "ko_KR.UTF-8"
  [ "$output" = "en" ]
}

@test "YONA_INSTALL_LANG가 시스템 언어보다 우선한다" {
  run detect_lang "" "en" "ko_KR.UTF-8"
  [ "$output" = "en" ]
}

@test "시스템 언어가 ko로 시작하면 한국어다" {
  run detect_lang "" "" "ko_KR.UTF-8"
  [ "$output" = "ko" ]
}

@test "시스템 언어가 영어이거나 모르면 영어다" {
  run detect_lang "" "" "en_US.UTF-8"
  [ "$output" = "en" ]
  run detect_lang "" "" ""
  [ "$output" = "en" ]
  run detect_lang "" "" "C"
  [ "$output" = "en" ]
}

@test "지원하지 않는 --lang 값은 오류이다" {
  run detect_lang "fr" "" ""
  [ "$status" -ne 0 ]
}

@test "시스템 언어를 읽을 때 LC_ALL, LC_MESSAGES, LANG 순으로 본다" {
  LC_ALL="" LC_MESSAGES="ko_KR.UTF-8" LANG="en_US.UTF-8" run system_locale
  [ "$output" = "ko_KR.UTF-8" ]
  LC_ALL="en_US.UTF-8" LC_MESSAGES="ko_KR.UTF-8" LANG="ko_KR.UTF-8" run system_locale
  [ "$output" = "en_US.UTF-8" ]
}

@test "환경 변수가 모두 비어 있으면 Mac의 AppleLocale을 본다" {
  mkdir -p "$TMP_DIR/bin"
  printf '#!/bin/sh\necho ko_KR\n' > "$TMP_DIR/bin/defaults"; chmod +x "$TMP_DIR/bin/defaults"
  PATH="$TMP_DIR/bin:$PATH" LC_ALL="" LC_MESSAGES="" LANG="" run system_locale
  [ "$output" = "ko_KR" ]
}

@test "인자에 &나 역슬래시가 있어도 그대로 들어간다(bash 5.2의 치환 규칙에 속지 않는다)" {
  msg_load en
  run msg "port.in_use" 'a&b\\c'
  [[ "$output" == *'a&b\\c'* ]]
  [[ "$output" != *"{1}"* ]]
}

@test "msg_set으로 실행 중에 문구를 정할 수 있다" {
  msg_load en
  msg_set "choice.version.v2.0.0-rc1" "v2.0.0-rc1 (rc)"
  [ "$(msg choice.version.v2.0.0-rc1)" = "v2.0.0-rc1 (rc)" ]
}
