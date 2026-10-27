#!/usr/bin/env python3
"""요청 하나의 정규화한 HTML 차이를 문맥과 함께 전부 보여 준다. 사용: show.py <행위자> <경로> [문맥줄수]"""
import difflib
import sys

import requests

from harness import html_lines
from probe import session, BASES

actor, path = sys.argv[1], sys.argv[2]
n = int(sys.argv[3]) if len(sys.argv) > 3 else 4
outs = {}
for name, base in BASES.items():
    r = session(base, actor).get(base + path, allow_redirects=False)
    outs[name] = (r.status_code, html_lines(r.text, name == "v1.6") if "html" in r.headers.get("content-type", "") else [r.text[:300]])
print({k: v[0] for k, v in outs.items()})
for line in difflib.unified_diff(outs["v1.6"][1], outs["v2"][1], "v1.6", "v2", lineterm="", n=n):
    print(line[:230])
