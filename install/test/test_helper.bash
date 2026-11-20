# bats 공통 도우미: 설치 스크립트의 위치를 알려 주고 도서관 파일을 불러온다.
YI_DIR="$(cd "$BATS_TEST_DIRNAME/.." && pwd)"
export YI_DIR

# 시험마다 임시 폴더를 만든다.
setup_tmp() {
  TMP_DIR="$(mktemp -d)"
  export TMP_DIR
}

teardown_tmp() {
  [ -n "${TMP_DIR:-}" ] && rm -rf "$TMP_DIR"
}
