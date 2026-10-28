#!/usr/bin/env python3
"""bulk2로 데이터를 많이 만든 서버에서 무거운 화면의 응답 시간을 두 서버에서 재 견준다(각 3번, 가운데 값). v2가 v1.6보다 3배 넘게 느리거나 1초 넘게 더 걸리는 화면을 적는다."""
import statistics, sys, time
import requests
from probe import BASES, session

B = "/tester/proj1"
PAGES = [("tester", p) for p in [
    f"{B}/issues", f"{B}/issues?state=all", f"{B}/issues?state=all&pageNum=8", f"{B}/issues?filter=버그&state=all", f"{B}/issues?orderBy=numOfComments&orderDir=desc&state=all", f"{B}/issues?format=json&state=all",
    f"{B}/issues?format=xls&state=all", f"{B}/issues/excel?state=all", f"{B}/posts", f"{B}/posts?pageNum=5", f"{B}/posts?filter=글", f"{B}/milestones", f"{B}/milestone/1", f"{B}/statistics", f"{B}", "/", "/tester",
    "/tester?daysAgo=30", "/user/issues?state=all", "/search?searchType=issue&keyword=버그", "/search?searchType=issue_comment&keyword=댓글", "/search?keyword=글", "/notifications", "/projects", f"{B}/issue/100", f"{B}/issue/250",
    f"{B}/post/100", f"{B}/issue/labels", f"{B}/settingform", f"{B}/members", "/user/usermenuTabContentList", "/-_-api/v1/user/issues", f"{B}/mentionList?query=t"]]
rows = []
for actor, path in PAGES:
    t = {}
    for name, base in BASES.items():
        s = session(base, actor)
        ts = []
        for _ in range(3):
            t0 = time.perf_counter()
            r = s.get(base + path, allow_redirects=False, timeout=120)
            ts.append(time.perf_counter() - t0)
        t[name] = (statistics.median(ts), r.status_code, len(r.content))
    rows.append((path, t))
slow = 0
for path, t in rows:
    a, b = t["v1.6"], t["v2"]
    flag = ""
    if b[0] > a[0] * 3 and b[0] - a[0] > 0.15 or b[0] - a[0] > 1.0:
        flag = "  <-- v2가 느림"; slow += 1
    print(f"{path[:70]:70} v1.6 {a[0]*1000:7.0f}ms ({a[1]}, {a[2]}B)  v2 {b[0]*1000:7.0f}ms ({b[1]}, {b[2]}B){flag}")
print(f"느린 화면 {slow}건 / 전체 {len(rows)}건")
