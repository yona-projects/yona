#!/usr/bin/env bats
# 한 줄 설치 부트스트랩과 릴리스 묶음 만들기 시험(가짜 릴리스 서버는 file:// 주소).
load test_helper

setup() {
  setup_tmp
  export HOME="$TMP_DIR/home"; mkdir -p "$HOME"
  "$YI_DIR/build-release.sh" "$TMP_DIR/dist" >/dev/null
  export YI_EUID=1000
  mkdir -p "$TMP_DIR/bin"
  printf '#!/bin/sh\necho "openjdk version \\"21.0.2\\" 2026-01-01" >&2\n' > "$TMP_DIR/bin/java"; chmod +x "$TMP_DIR/bin/java"
  export PATH="$TMP_DIR/bin:$PATH" LANG=C LC_ALL=C YI_SYSTEM_RECORD_DIR="$TMP_DIR/sysrec"
  # 가짜 릴리스: 설치 묶음, jar, 둘의 digest
  printf 'JAR' > "$TMP_DIR/yona-2.0.0.jar"
  python3 - "$TMP_DIR" <<'PY'
import hashlib, json, sys
t = sys.argv[1]
def d(p): return "sha256:" + hashlib.sha256(open(p, 'rb').read()).hexdigest()
def asset(name, p): return {"name": name, "size": 1, "digest": d(p), "browser_download_url": "file://" + p}
json.dump([{"tag_name": "v2.0.0", "name": "v2.0.0", "draft": False, "prerelease": False, "tarball_url": "x",
  "assets": [asset("yona-2.0.0.jar", f"{t}/yona-2.0.0.jar"), asset("yona-install.tar.gz", f"{t}/dist/yona-install.tar.gz")]}], open(f"{t}/releases.json", "w"))
PY
  export YONA_RELEASES_URL="file://$TMP_DIR/releases.json"
}
teardown() { teardown_tmp; }

@test "릴리스 묶음을 만든다: 설치 묶음, 부트스트랩, 체크섬" {
  [ -f "$TMP_DIR/dist/yona-install.tar.gz" ]
  [ -f "$TMP_DIR/dist/yona-install.zip" ]
  [ -f "$TMP_DIR/dist/bootstrap.sh" ]
  [ -f "$TMP_DIR/dist/bootstrap.ps1" ]
  [ -f "$TMP_DIR/dist/SHA256SUMS" ]
}

@test "설치 묶음에는 시험 자료가 없고 실행에 필요한 파일이 있다" {
  tar -tzf "$TMP_DIR/dist/yona-install.tar.gz" > "$TMP_DIR/list"
  grep -q "yona-install.sh" "$TMP_DIR/list"
  grep -q "lib/flow.sh" "$TMP_DIR/list"
  grep -q "messages/ko.properties" "$TMP_DIR/list"
  grep -q "templates/wait-for-port.sh" "$TMP_DIR/list"
  grep -q "yona-install.ps1" "$TMP_DIR/list"
  grep -q "lib/ps/Flow.ps1" "$TMP_DIR/list"
  ! grep -q "test/" "$TMP_DIR/list"
  ! grep -q "scenarios/" "$TMP_DIR/list"
}

@test "부트스트랩은 단독 파일이고 문법이 올바르다" {
  run bash -n "$TMP_DIR/dist/bootstrap.sh"
  [ "$status" -eq 0 ]
  ! grep -q '^\. ' "$TMP_DIR/dist/bootstrap.sh"      # 다른 파일을 source하지 않는다
}

@test "부트스트랩이 설치 묶음을 받아 검증하고 풀어서 설치 프로그램에 인자를 넘긴다" {
  run bash "$TMP_DIR/dist/bootstrap.sh" --version=v2.0.0 --yes --dry-run --service=false --db=h2 \
    --install-dir="$TMP_DIR/home/y" --data-dir="$TMP_DIR/home/d" --port=58321
  [ "$status" -eq 0 ]
  [[ "$output" == *"sha256 check passed"* ]]
  [[ "$output" == *"Plan:"* ]]
}

@test "받은 묶음의 digest가 다르면 실행하지 않고 중단한다" {
  printf 'tampered' >> "$TMP_DIR/dist/yona-install.tar.gz"
  run bash "$TMP_DIR/dist/bootstrap.sh" --version=v2.0.0 --yes --dry-run
  [ "$status" -ne 0 ]
  [[ "$output" == *"does not match"* ]]
  [[ "$output" != *"Plan:"* ]]
}

@test "설치 묶음이 없는 릴리스를 고르면 안내하고 중단한다" {
  python3 - "$TMP_DIR" <<'PY'
import json, sys
t = sys.argv[1]
r = json.load(open(f"{t}/releases.json")); r[0]["assets"] = [a for a in r[0]["assets"] if a["name"].endswith(".jar")]
json.dump(r, open(f"{t}/releases.json", "w"))
PY
  run bash "$TMP_DIR/dist/bootstrap.sh" --version=v2.0.0 --yes --dry-run
  [ "$status" -ne 0 ]
  [[ "$output" == *"yona-install.tar.gz"* ]]
}
