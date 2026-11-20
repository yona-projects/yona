#!/usr/bin/env bats
# 질문(프롬프트) 시험. 입력은 YI_INPUT 파일로 대신 준다.
load test_helper

setup() {
  setup_tmp
  . "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/answers.sh"; . "$YI_DIR/lib/messages.sh"; . "$YI_DIR/lib/render.sh"; . "$YI_DIR/lib/prompt.sh"
  ans_clear
  printf 'prompt.test=Question?\nprompt.pick=Pick one\nchoice.pick.a=Alpha\nchoice.pick.b=Beta\nprompt.secret=Password\nprompt.yn=Yes or no?\nerr.input_closed=input closed\nprompt.invalid=Invalid input\nprompt.mismatch=mismatch\n' > "$TMP_DIR/en.properties"
  YI_MESSAGES_DIR="$TMP_DIR" msg_load en
  export YI_SECRET_DIR="$TMP_DIR/secrets"
}
teardown() { teardown_tmp; }

feed() { printf '%s\n' "$@" > "$TMP_DIR/input"; export YI_INPUT="$TMP_DIR/input"; prompt_open; }

@test "이미 답이 있으면 묻지 않는다" {
  ans_set my.key preset
  feed "typed"
  ask_text my.key prompt.test "def" >/dev/null
  [ "$(ans_get my.key)" = "preset" ]
}

@test "텍스트 질문은 입력을 담고 비우면 기본값을 쓴다" {
  feed "typed" ""
  ask_text a.key prompt.test "def" >/dev/null
  ask_text b.key prompt.test "def" >/dev/null
  [ "$(ans_get a.key)" = "typed" ]
  [ "$(ans_get b.key)" = "def" ]
}

@test "선택 질문은 번호나 값을 받는다" {
  feed "2" "a" ""
  ask_choice c1 prompt.pick "a b" "a" >/dev/null
  ask_choice c2 prompt.pick "a b" "b" >/dev/null
  ask_choice c3 prompt.pick "a b" "b" >/dev/null
  [ "$(ans_get c1)" = "b" ]
  [ "$(ans_get c2)" = "a" ]
  [ "$(ans_get c3)" = "b" ]    # 비우면 기본값
}

@test "선택 질문은 목록에 없는 입력이면 다시 묻는다" {
  feed "9" "zzz" "1"
  out="$(ask_choice c1 prompt.pick "a b" "a")"
  ask_choice c4 prompt.pick "a b" "a" >/dev/null 2>&1 || true
  [[ "$out" == *"Invalid input"* ]]
}

@test "예 아니오 질문은 y, n, yes, no를 받고 true false로 담는다" {
  feed "y" "n" "" "YES"
  ask_yesno q1 prompt.yn false >/dev/null
  ask_yesno q2 prompt.yn true >/dev/null
  ask_yesno q3 prompt.yn true >/dev/null
  ask_yesno q4 prompt.yn false >/dev/null
  [ "$(ans_get q1)" = "true" ]
  [ "$(ans_get q2)" = "false" ]
  [ "$(ans_get q3)" = "true" ]
  [ "$(ans_get q4)" = "true" ]
}

@test "비밀번호는 두 번 입력해 같으면 소유자만 읽는 파일로 저장하고 파일 경로를 답에 담는다" {
  feed "S3cret!" "S3cret!"
  ask_secret db.password_file prompt.secret >/dev/null
  f="$(ans_get db.password_file)"
  [ -f "$f" ]
  [ "$(cat "$f")" = "S3cret!" ]
  [ "$(stat -c %a "$f" 2>/dev/null || stat -f %Lp "$f")" = "600" ]
}

@test "비밀번호가 서로 다르거나 비었거나 허용되지 않는 문자면 다시 묻는다" {
  feed "a" "b" "" "good" "good"
  ask_secret db.password_file prompt.secret >/dev/null
  [ "$(cat "$(ans_get db.password_file)")" = "good" ]
}

@test "비대화형(yes)이면 질문하지 않고 기본값이 허용된 항목만 기본값을 쓴다" {
  ans_set yes true
  ask_text opt.key prompt.test "def" optional >/dev/null
  ask_text req.key prompt.test "def" >/dev/null
  [ "$(ans_get opt.key)" = "def" ]
  [ -z "$(ans_get req.key)" ]
}

@test "입력이 끝났는데 답이 필요하면 오류로 끝낸다" {
  feed "only-one"
  ask_text a.key prompt.test "" >/dev/null
  run ask_text b.key prompt.test ""
  [ "$status" -ne 0 ]
}
