#!/bin/bash
# 모든 비교 시나리오를 알맞은 저장 상태에서 다시 돌려 이전 보고서의 "다름" 건수와 견준다.
#   regress.sh [시나리오 이름 ...]   이름이 없으면 전부 돈다. 보고서는 yona-diff-v2/report-<이름>.md 에 덮어쓴다.
# 서버가 떠 있어야 하며(servers.sh start), 시나리오마다 servers.sh restore 로 상태를 되돌린다.
cd "$(dirname "$0")" || exit 1
OUT=/home/jiho/yona-convert/yona-diff-v2
# 이름:저장상태:먼저 할 일
PLAN=(
  "anon_basic:base:"
  "pages1:data1:"
  "counts:data1:"
  "repo1:data2:"
  "md1:data2:"
  "forms1:data2:"
  "api1:data2:"
  "site1:data2:"
  "misc1:data2:"
  "bulk1:data2:"
  "hook1:data2:"
  "att1:data2:"
  "org2:data2:"
  "methods1:data2:"
  "review1:data2:pr"
  "pr1:data2:pr"
  "svn1:data3:"
  "life1:data3:"
  "proto1:data3:"
  "mail1:data3:"
)
want=" $* "
for item in "${PLAN[@]}"; do
  IFS=: read -r name state pre <<<"$item"
  [ "$#" -gt 0 ] && [[ "$want" != *" $name "* ]] && continue
  before=$(head -1 "$OUT/report-$name.md" 2>/dev/null | sed -n 's/.*다름 \([0-9]*\).*/\1/p')
  ./servers.sh restore "$state" >/dev/null 2>&1
  if [ "$pre" = "pr" ]; then
    PYTHONPATH=scenarios python3 harness.py run pr1a --out /dev/null >/dev/null 2>&1
    ./pr1.sh >/dev/null 2>&1
  fi
  PYTHONPATH=scenarios python3 harness.py run "$name" --out "$OUT/report-$name.md" --top 30 >/dev/null 2>&1
  after=$(head -1 "$OUT/report-$name.md" | sed -n 's/.*다름 \([0-9]*\).*/\1/p')
  flag=""; [ "$before" != "$after" ] && flag="  <-- 달라짐"
  echo "$name: 다름 ${before:-?} -> ${after:-?}$flag"
done
