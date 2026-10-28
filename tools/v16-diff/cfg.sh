#!/bin/bash
# 설정 값을 바꿔 띄운 두 서버에 `cfg1`을 돌려 설정에 따라 달라지는 동작을 대조한다. 표는 cfgs.tsv(이름, v1.6 옵션, v2 옵션; 탭으로 나눈다).
#   cfg.sh                전체 (기준 `base`를 먼저 돌려 설정과 상관없는 차이를 빼는 기준으로 쓴다)
#   cfg.sh 이름 [이름...]  일부
# 결과: yona-diff-v2/report-cfg-<이름>.md 와 요약 한 줄(기준에 없던 차이 건수)
cd "$(dirname "$0")" || exit 1
OUT=/home/jiho/yona-convert/yona-diff-v2
WORK=/home/jiho/yona-convert/yona-diff-work
want=" $* "
titles() { python3 - "$1" <<'PY'
import re, sys
t = open(sys.argv[1], encoding='utf-8').read()
for s in re.split(r'\n(?=## \d+\. )', t)[1:]:
    print(re.sub(r'^## \d+\. ', '', s.split('\n')[0]).strip())
PY
}
run() { # 이름 v16옵션 v2옵션
  printf '%s' "$2" > "$WORK/cfg.v16"; printf '%s' "$3" > "$WORK/cfg.v2"
  ./servers.sh restore data2 >/dev/null 2>&1; sleep 3
  PYTHONPATH=scenarios python3 harness.py run cfg1 --out "$OUT/report-cfg-$1.md" --top 30 >/dev/null 2>&1
}
base_done=0
while IFS=$'\t' read -r name f16 f2; do
  [ -z "$name" ] && continue
  if [ "$name" = base ] || [ "$#" -eq 0 ] || [[ "$want" == *" $name "* ]]; then
    if [ "$name" = base ]; then run base "" ""; titles "$OUT/report-cfg-base.md" | LC_ALL=C sort -u > "$WORK/cfg-base.titles"; echo "base: $(head -1 "$OUT/report-cfg-base.md")"; continue; fi
    run "$name" "$f16" "$f2"
    eff=$(python3 cfgprobe.py "$name" 2>&1 | tail -1)
    titles "$OUT/report-cfg-$name.md" | LC_ALL=C sort -u > "$WORK/cfg-$name.titles"
    extra=$(LC_ALL=C comm -13 "$WORK/cfg-base.titles" "$WORK/cfg-$name.titles" | wc -l)
    echo "$name: $(head -1 "$OUT/report-cfg-$name.md" | sed 's/.*: //') / 기준에 없던 차이 $extra / 설정 효과: $eff"
  fi
done < cfgs.tsv
: > "$WORK/cfg.v16"; : > "$WORK/cfg.v2"
./servers.sh restore data2 >/dev/null 2>&1
