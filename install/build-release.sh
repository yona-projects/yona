#!/usr/bin/env bash
# 릴리스에 올릴 설치 묶음을 만든다: yona-install.tar.gz(셸), yona-install.zip(PowerShell 포함 전체), bootstrap.sh, bootstrap.ps1, SHA256SUMS.
# 설치 묶음에는 시험 자료(test, scenarios)를 넣지 않는다. 부트스트랩은 단독 파일이라 `curl | bash`로 받아 실행할 수 있다.
# 사용법: build-release.sh [출력 폴더, 기본 install/dist]
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/dist}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/yona-install"
for f in yona-install.sh yona-install.ps1 lib messages templates; do
  cp -R "$HERE/$f" "$STAGE/yona-install/$f"
done
chmod +x "$STAGE/yona-install/yona-install.sh" "$STAGE/yona-install/templates/"*.sh
# 묶음 안의 압축 파일 이름은 풀었을 때 폴더 하나가 생기게 yona-install/로 시작한다.
rm -f "$OUT/yona-install.tar.gz" "$OUT/yona-install.zip"
( cd "$STAGE" && tar -czf "$OUT/yona-install.tar.gz" yona-install )
( cd "$STAGE" && python3 - "$OUT/yona-install.zip" <<'PY'
import os, sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk("yona-install"):
        for f in sorted(files):
            p = os.path.join(root, f)
            info = zipfile.ZipInfo.from_file(p, p.replace(os.sep, "/"))
            info.external_attr = (os.stat(p).st_mode & 0xFFFF) << 16
            z.writestr(info, open(p, "rb").read(), zipfile.ZIP_DEFLATED)
PY
)
# 단독 부트스트랩(셸): 공통 도구와 릴리스 읽기를 이어 붙인다.
{
  cat "$HERE/bootstrap-head.sh"
  sed '/^# shellcheck/d' "$HERE/lib/common.sh"
  sed '/^# shellcheck/d' "$HERE/lib/releases.sh"
  cat "$HERE/bootstrap-body.sh"
} > "$OUT/bootstrap.sh"
chmod +x "$OUT/bootstrap.sh"
cp "$HERE/bootstrap.ps1" "$OUT/bootstrap.ps1"
( cd "$OUT" && sha256sum yona-install.tar.gz yona-install.zip bootstrap.sh bootstrap.ps1 > SHA256SUMS )
echo "릴리스 묶음: $OUT"
