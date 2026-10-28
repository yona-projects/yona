#!/bin/bash
# 내보내기/가져오기 상호 시험을 처음부터 끝까지 돌린다.
cd "$(dirname "$0")" || exit 1
OUT=/home/jiho/yona-convert/yona-diff-v2
./servers.sh restore data1 >/dev/null 2>&1
python3 xport.py export
./servers.sh restore base >/dev/null 2>&1
python3 xport.py cross
sleep 5
XPORT_SWAP=1 PYTHONPATH=scenarios python3 harness.py run pages1 --out "$OUT/report-xport-pages1.md" --top 30 2>&1 | head -1
echo "(기준: 가져오지 않은 data1에서의 pages1 다름 건수는 report-pages1.md 첫 줄)"; head -1 "$OUT/report-pages1.md"
./servers.sh restore data2 >/dev/null 2>&1
