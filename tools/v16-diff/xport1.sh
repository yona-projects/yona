#!/bin/bash
# 이전 경로 시험: v1.6의 내보내기 파일을 v2로 가져와, 가져온 v2의 화면이 원래 v1.6의 화면과 같은지 본다(v1.6 -> v2 이전).
cd "$(dirname "$0")" || exit 1
R=/home/jiho/yona-convert; V2D=$R/yona-diff-v2; SNAP=$R/yona-diff-snapshots; OUT=$R/yona-diff-v2
./servers.sh restore data1 >/dev/null 2>&1
python3 - <<'PY'
import requests
b="http://127.0.0.1:19200"
s=requests.Session(); s.post(b+"/users/login",data={"loginIdOrEmail":"admin","password":"admin1234"})
open("/home/jiho/yona-convert/yona-diff-work/export-v1.6.json","wb").write(s.get(b+"/sites/export").content)
PY
./servers.sh stop >/dev/null 2>&1
stamp=$(date +%s)
for i in db data; do [ -e "$V2D/$i" ] && mv "$V2D/$i" "$V2D/$i.discard.$stamp"; done
tar -xzf "$SNAP/base/v2.tgz" -C "$V2D"
./servers.sh start >/dev/null 2>&1
python3 - <<'PY'
import requests
b="http://127.0.0.1:19300"
s=requests.Session(); r0=s.post(b+"/users/login",data={"loginIdOrEmail":"admin","password":"admin1234"},allow_redirects=False)
data=open("/home/jiho/yona-convert/yona-diff-work/export-v1.6.json","rb").read()
r=s.post(b+"/sites/import",files={"data":("d.json",data,"application/json")},allow_redirects=False)
print("v1.6의 파일을 v2에 가져오기:", r.status_code, r.headers.get("Location"))
PY
sleep 3
XPORT_SAME=1 PYTHONPATH=scenarios python3 harness.py run pages1 --out "$OUT/report-xport1-pages1.md" --top 30 2>&1 | head -1
head -1 "$OUT/report-pages1.md"
./servers.sh restore data2 >/dev/null 2>&1
