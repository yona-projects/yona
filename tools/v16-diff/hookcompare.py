#!/usr/bin/env python3
"""httpsink.py가 쌓은 두 서버의 웹훅 요청을 (경로, 순서)로 짝지어 비교한다. 본문은 JSON이면 구조를 맞춰 정규화한다.
  hookcompare.py [--out 보고서.md]"""
import argparse
import difflib
import json
import os
import re
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(__file__))
import harness  # noqa: E402

WORK = "/home/jiho/yona-convert/yona-diff-work"
KEEP_HEADERS = ("authorization", "content-type", "x-yona-event", "x-yobi-event", "user-agent", "x-hub-signature", "x-github-event", "x-gitHub-event")


def load(path):
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()] if os.path.exists(path) else []


def norm_text(t):
    t = re.sub(r"127\.0\.0\.1:19[23]\d\d", "<host>", t)
    t = re.sub(r"10\.126\.201\.1", "<host>", t)
    t = harness.scrub(t)
    t = re.sub(r"\b[0-9a-f]{40}\b", "<sha1>", t)
    return t


def norm_json(v, key=""):
    if isinstance(v, dict):
        return {k: norm_json(x, k) for k, x in v.items()}
    if isinstance(v, list):
        return [norm_json(x, key) for x in v]
    if isinstance(v, str):
        return norm_text(v)
    if isinstance(v, int) and not isinstance(v, bool) and (key.lower() in ("id", "number", "timestamp", "created", "updated") or v > 10**9):
        return "<n>"
    return v


def describe(r):
    lines = [f"{r['method']} {re.sub(r'[0-9]+', 'N', r['path'])}"]
    for k in sorted(r["headers"]):
        if k.lower() in KEEP_HEADERS:
            lines.append(f"{k.lower()}: {norm_text(r['headers'][k])}")
    # 서명 헤더 값은 본문이 같아도 시크릿과 시각에 따라 다르니 있는지만 본다
    lines = [re.sub(r"(x-hub-signature\w*: ).*", r"\1<서명>", l) for l in lines]
    body = r["body"]
    try:
        data = json.loads(body)
        lines += json.dumps(norm_json(data), ensure_ascii=False, indent=1, sort_keys=True).splitlines()
    except ValueError:
        lines += [norm_text(l) for l in body.splitlines()]
    return lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out")
    args = ap.parse_args()
    a = load(os.path.join(WORK, "hooks-v16.jsonl"))
    b = load(os.path.join(WORK, "hooks-v2.jsonl"))
    ga, gb = defaultdict(list), defaultdict(list)
    for r in a:
        ga[re.sub(r"\?.*", "", r["path"])].append(r)
    for r in b:
        gb[re.sub(r"\?.*", "", r["path"])].append(r)
    report, same, diff = [], 0, 0
    for k in sorted(set(ga) | set(gb)):
        la, lb = ga.get(k, []), gb.get(k, [])
        da_all = [describe(x) for x in la]
        db_all = [describe(y) for y in lb]
        rest_b = list(db_all)
        rest_a = []
        for d in da_all:
            if d in rest_b:
                rest_b.remove(d)
                same += 1
            else:
                rest_a.append(d)
        if len(la) != len(lb):
            report.append(f"## 건수가 다르다 {k}: v1.6={len(la)} v2={len(lb)}")
        for da, db in zip(rest_a, rest_b):
            diff += 1
            report.append(f"## 다르다 {k}")
            report += ["    " + l for l in difflib.unified_diff(da, db, "v1.6", "v2", lineterm="", n=1)]
        for da in rest_a[len(rest_b):]:
            report.append(f"## v1.6에만 있다 {k}: {da[0]}")
        for db in rest_b[len(rest_a):]:
            report.append(f"## v2에만 있다 {k}: {db[0]}")
    head = f"# 웹훅: v1.6 {len(a)}건, v2 {len(b)}건, 같음 {same}, 다름 {diff}"
    text = head + "\n" + "\n".join(report) + "\n"
    if args.out:
        open(args.out, "w", encoding="utf-8").write(text)
    print(head)


if __name__ == "__main__":
    main()
