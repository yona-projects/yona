#!/usr/bin/env python3
"""댓글 수 규칙을 두 서버에서 비교한다: 연산마다 목록에 보이는 숫자와 실제 댓글 수를 표로 보여 준다."""
import re
import sys
import requests

BASES = {"v1.6": "http://127.0.0.1:19200", "v2": "http://127.0.0.1:19300"}


def sess(base, u):
    s = requests.Session()
    s.post(base + "/users/login", data={"loginIdOrEmail": u, "password": "pass1234"}, allow_redirects=False)
    return s


def mp(**f):
    return {k: (None, str(v)) for k, v in f.items()}


def shown(s, base, kind, number):
    h = s.get(base + ("/tester/proj1/issues" if kind == "issue" else "/tester/proj1/posts")).text
    i = h.find(f'href="/tester/proj1/{kind if kind == "issue" else "post"}/{number}"')
    m = re.search(r'item-count">\s*(\d+)', h[i:i + 3500]) if i >= 0 else None
    return m.group(1) if m else "-"


def actual(s, base, kind, number):
    h = s.get(base + f"/tester/proj1/{kind if kind == 'issue' else 'post'}/{number}").text
    return len(set(re.findall(r'id="comment-(\d+)"', h)))


def run(name, base):
    t, o = sess(base, "tester"), sess(base, "other")
    t.post(base + "/tester/proj1/issues/latest", files=mp(title="수 시험", body="본문", isDraft="false"), allow_redirects=False)
    t.post(base + "/tester/proj1/posts", files=mp(title="수 시험 글", body="본문", issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), allow_redirects=False)
    inum = int(re.findall(r'/issue/(\d+)"', t.get(base + "/tester/proj1/issues").text)[0])
    pnum = int(re.findall(r'/post/(\d+)"', t.get(base + "/tester/proj1/posts").text)[0])
    rows = []

    def row(op):
        rows.append((op, shown(t, base, "issue", inum), actual(t, base, "issue", inum), shown(t, base, "post", pnum), actual(t, base, "post", pnum)))

    row("생성")
    ids = {"issue": [], "post": []}
    for n, (who, text) in enumerate([(t, "1"), (o, "2"), (t, "3"), (o, "4")], 1):
        who.post(base + f"/tester/proj1/issue/{inum}/comments", files=mp(contents=text), allow_redirects=False)
        who.post(base + f"/tester/proj1/post/{pnum}/comment", files=mp(contents=text), allow_redirects=False)
        row(f"댓글 {n}")
    ih = t.get(base + f"/tester/proj1/issue/{inum}").text
    ph = t.get(base + f"/tester/proj1/post/{pnum}").text
    icid = re.findall(r'id="comment-(\d+)"', ih)
    pcid = re.findall(r'id="comment-(\d+)"', ph)
    t.post(base + f"/tester/proj1/issue/{inum}/comments/{icid[0]}", files=mp(contents="고침"), allow_redirects=False)
    row("이슈 댓글 고침")
    t.delete(base + f"/tester/proj1/issue/{inum}/comment/{icid[0]}/delete", allow_redirects=False)
    t.delete(base + f"/tester/proj1/post/{pnum}/comment/{pcid[0]}/delete", allow_redirects=False)
    row("댓글 하나씩 삭제")
    o.post(base + f"/tester/proj1/issue/{inum}/vote", allow_redirects=False)
    row("이슈 추천")
    t.post(base + f"/tester/proj1/issue/{inum}/comments", files=mp(contents="5"), allow_redirects=False)
    t.post(base + f"/tester/proj1/post/{pnum}/comment", files=mp(contents="5"), allow_redirects=False)
    row("댓글 5")
    t.post(base + f"/tester/proj1/issue/{inum}/edit", files=mp(title="수 시험(수정)", body="본문2", isDraft="false"), allow_redirects=False)
    t.post(base + f"/tester/proj1/post/{pnum}/edit", files=mp(title="수 시험 글(수정)", body="본문2", issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), allow_redirects=False)
    row("본문 수정")
    t.post(base + f"/tester/proj1/issue/{inum}/comments", files=mp(contents="6", withStateTransition="close"), allow_redirects=False)
    row("댓글 6(상태 전환)")
    return rows


res = {n: run(n, b) for n, b in BASES.items()}
print(f"{'연산':<20} | 이슈 v1.6 (표시/실제) | 이슈 v2 | 글 v1.6 | 글 v2")
for a, b in zip(res["v1.6"], res["v2"]):
    print(f"{a[0]:<20} | {a[1]:>3}/{a[2]:<3}              | {b[1]:>3}/{b[2]:<3} | {a[3]:>3}/{a[4]:<3} | {b[3]:>3}/{b[4]:<3}")
