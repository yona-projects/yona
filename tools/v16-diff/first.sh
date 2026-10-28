#!/bin/bash
# 최초 설치 직후 상태(`first` 저장 상태)를 만든다: v1.6는 기본 비밀 키(`application.secret`)를 쓰는 설정과 빈 DB, v2는 비밀 키 파일이 없는 빈 데이터이다.
# 두 서버 모두 첫 요청에 사이트 비밀 키 설정 화면을 보인다. 만든 뒤 `servers.sh restore first`로 언제든 되돌린다.
R=/home/jiho/yona-convert
V16=$R/yona-v1.16-release/yona-1.16.0
V2D=$R/yona-diff-v2
DEFAULT='VA2v:_I=h9>?FYOH:@ZhW]01P<mWZAKlQ>kk>Bo`mdCiA>pDw64FcBuZdDh<47Ew'
HERE=$(cd "$(dirname "$0")" && pwd)
"$HERE/servers.sh" stop
stamp=$(date +%s)
for i in yona.h2.db repo uploads; do [ -e "$V16/$i" ] && mv "$V16/$i" "$V16/$i.discard.$stamp"; done
rm -f "$V16/yona.lock.db" "$V16/yona.trace.db"
cp "$V16/conf/application.conf" "$V16/conf/application.conf.discard.$stamp"
python3 - "$V16/conf/application.conf" "$DEFAULT" <<'PY'
import re, sys
p, d = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
s = re.sub(r'^application\.secret=".*"$', 'application.secret="' + d + '"', s, flags=re.M)
open(p, 'w', encoding='utf-8').write(s)
PY
for i in db data; do [ -e "$V2D/$i" ] && mv "$V2D/$i" "$V2D/$i.discard.$stamp"; done
mkdir -p "$V2D/db" "$V2D/data"
"$HERE/servers.sh" start
"$HERE/servers.sh" snapshot first
