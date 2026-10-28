#!/usr/bin/env python3
"""harness 보고서에서 내부 번호(숫자와 <이름> 표지)만 다른 차이를 빼고 남은 차이만 보여 준다.
사용: reportfilter.py <보고서.md> [--show N]"""
import re, sys

path = sys.argv[1]
show = int(sys.argv[sys.argv.index("--show") + 1]) if "--show" in sys.argv else 40
t = open(path, encoding="utf-8").read()


def norm(line):
    line = re.sub(r"<[a-z_]{1,8}>", "N", line)
    line = re.sub(r"\d+", "N", line)
    return line.strip()


kept = []
for block in re.split(r"\n(?=## \d+\. )", t)[1:]:
    lines = block.split("\n")
    if "상태 코드" in block:
        kept.append(block)
        continue
    minus = [norm(l[5:]) for l in lines if l.startswith("    -") and not l.startswith("    ---")]
    plus = [norm(l[5:]) for l in lines if l.startswith("    +") and not l.startswith("    +++")]
    if sorted(minus) != sorted(plus):
        kept.append(block)
print(f"{path}: 번호만 다른 차이를 뺀 {len(kept)}건")
for b in kept[:show]:
    print(b[:900])
    print("----")
