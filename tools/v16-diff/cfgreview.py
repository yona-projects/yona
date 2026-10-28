#!/usr/bin/env python3
"""cfg.sh가 만든 보고서에서 알림(flash) 표시 순서 차이(v1.6 자체가 실행마다 달라지는 것, G-21)만 있는 항목을 빼고 남은 차이를 보여 준다.
사용: cfgreview.py [이름 ...]   이름이 없으면 report-cfg-*.md 전부"""
import glob, os, re, sys
OUT = "/home/jiho/yona-convert/yona-diff-v2"
NOTIFY = re.compile(r'\$yobi\.notify\("((?:[^"\\]|\\.)*)"')


def blocks(path):
    t = open(path, encoding="utf-8").read()
    return re.split(r"\n(?=## \d+\. )", t)[1:]


def is_flash_order_only(block):
    lines = block.split("\n")
    minus = [l for l in lines if l.startswith("    -") and not l.startswith("    ---")]
    plus = [l for l in lines if l.startswith("    +") and not l.startswith("    +++")]
    if len(minus) != 1 or len(plus) != 1 or "상태 코드" in block:
        return False
    a, b = NOTIFY.findall(minus[0]), NOTIFY.findall(plus[0])
    if not a or sorted(a) != sorted(b):
        return False
    strip = lambda s: NOTIFY.sub("N", s[5:])  # noqa: E731
    return strip(minus[0]) == strip(plus[0])


names = sys.argv[1:] or sorted(os.path.basename(p)[len("report-cfg-"):-3] for p in glob.glob(f"{OUT}/report-cfg-*.md"))
base = {re.sub(r"^## \d+\. ", "", b.split("\n")[0]).strip() for b in blocks(f"{OUT}/report-cfg-base.md") if not is_flash_order_only(b)}
for n in names:
    rest = []
    for b in blocks(f"{OUT}/report-cfg-{n}.md"):
        title = re.sub(r"^## \d+\. ", "", b.split("\n")[0]).strip()
        if is_flash_order_only(b) or (n != "base" and title in base):
            continue
        rest.append(b)
    print(f"{n}: 순서 차이를 뺀 새 차이 {len(rest)}건")
    for b in rest:
        st = re.search(r"상태 코드: v1\.6=(\d+) v2=(\d+)", b)
        print("   ", b.split("\n")[0][:110], st.groups() if st else "")
