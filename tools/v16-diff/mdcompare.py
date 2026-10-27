#!/usr/bin/env python3
"""마크다운 미리보기(POST /markdown/:user/:project)를 두 서버에 보내 변환 결과를 직접 비교한다. 사용: mdcompare.py [파일...]"""
import difflib
import re
import sys

from probe import session, BASES
sys.path.insert(0, "scenarios")
from md1 import SNIPPETS


def norm(html):
    return [l.strip() for l in re.sub(r">\s*<", ">\n<", html).split("\n") if l.strip()]


def render(base, s, text):
    r = s.post(base + "/markdown/tester/proj1", json={"body": text, "breaks": True})
    return r.status_code, r.text


def main():
    cases = list(SNIPPETS)
    for f in sys.argv[1:]:
        cases.append((f, open(f, encoding="utf-8").read()))
    s16, s2 = session(BASES["v1.6"], "tester"), session(BASES["v2"], "tester")
    same = 0
    for name, text in cases:
        a, b = render(BASES["v1.6"], s16, text), render(BASES["v2"], s2, text)
        if a == b:
            same += 1
            continue
        print(f"=== {name}: 상태 {a[0]} / {b[0]}")
        for l in difflib.unified_diff(norm(a[1]), norm(b[1]), "v1.6", "v2", lineterm="", n=0):
            print("   ", l[:300])
    print(f"완전히 같음 {same} / {len(cases)}")


main()
