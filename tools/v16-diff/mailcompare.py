#!/usr/bin/env python3
"""smtpsink.py가 쌓은 두 서버의 알림 메일을 짝지어 비교한다.
  mailcompare.py [--out 보고서.md]
메일은 (받는 사람, 제목)으로 짝짓고 같은 키 안에서는 도착 순서로 맞춘다. 날짜, 메시지 ID, 경계 문자열은 가리고 본문은 harness와 같은 규칙으로 정규화한다."""
import argparse
import difflib
import email
import email.policy
import json
import os
import re
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(__file__))
import harness  # noqa: E402

WORK = "/home/jiho/yona-convert/yona-diff-work"


def load(path):
    out = []
    if not os.path.exists(path):
        return out
    for line in open(path, encoding="utf-8"):
        if line.strip():
            out.append(json.loads(line))
    return out


POSTID = re.compile(r"/(issue_post|board_post|issue_comment|nonissue_comment|commit|review_comment)/\d+@")
TOKEN = re.compile(r"(confirm/\d+/|resetPassword\?s=)\w{16,}")


def mask(text):
    return POSTID.sub(r"/\1/<n>@", TOKEN.sub(r"\1<토큰>", text))


def part_lines(part):
    ctype = part.get_content_type()
    payload = part.get_content()
    if ctype == "text/html":
        return ["[html]"] + [mask(l) for l in harness.html_lines(payload)]
    text = mask(harness.scrub(payload if isinstance(payload, str) else repr(payload)))
    return [f"[{ctype}]"] + [l.rstrip() for l in text.splitlines()]


def describe(raw):
    msg = email.message_from_string(raw["data"], policy=email.policy.default)
    lines = []
    for h in ("From", "Reply-To", "Subject", "MIME-Version", "Content-Language", "List-Id", "In-Reply-To", "References", "Precedence", "X-Mailer"):
        v = msg.get(h)
        if v is not None:
            lines.append(f"{h}: {mask(harness.scrub(str(v)))}")
    to = sorted(raw["rcpt_to"])
    lines.append("RCPT: " + ", ".join(to))
    lines.append("To: " + harness.scrub(str(msg.get("To", ""))))
    lines.append("Cc: " + harness.scrub(str(msg.get("Cc", ""))))
    for part in msg.walk():
        lines.append(f"-- {part.get_content_type()} cte={part.get('Content-Transfer-Encoding')} cs={part.get_content_charset()} disp={part.get_content_disposition()}")
        if not part.is_multipart() and (part.get_content_maintype() == "text"):
            lines += part_lines(part)
    return lines


def key(raw):
    msg = email.message_from_string(raw["data"], policy=email.policy.default)
    return (tuple(sorted(raw["rcpt_to"])), str(msg.get("Subject", "")))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out")
    ap.add_argument("--v16", default=os.path.join(WORK, "mails-v16.jsonl"))
    ap.add_argument("--v2", default=os.path.join(WORK, "mails-v2.jsonl"))
    args = ap.parse_args()
    a, b = load(args.v16), load(args.v2)
    ga, gb = defaultdict(list), defaultdict(list)
    for m in a:
        ga[key(m)].append(m)
    for m in b:
        gb[key(m)].append(m)
    report, same, diff = [], 0, 0
    only_a = [k for k in ga if k not in gb]
    only_b = [k for k in gb if k not in ga]
    for k in sorted(set(ga) & set(gb), key=str):
        la, lb = ga[k], gb[k]
        if len(la) != len(lb):
            report.append(f"## 통 수가 다르다 {k}: v1.6={len(la)} v2={len(lb)}")
        # 같은 키의 메일은 먼저 내용이 같은 것끼리 짝지어 도착 순서가 어긋난 것을 가리고, 남은 것을 도착 순서로 비교한다
        da_all = [describe(x) for x in la]
        db_all = [describe(y) for y in lb]
        rest_a, rest_b = [], list(db_all)
        for d in da_all:
            if d in rest_b:
                rest_b.remove(d)
                same += 1
            else:
                rest_a.append(d)
        for da, db in zip(rest_a, rest_b):
            diff += 1
            report.append(f"## 다르다 {k}")
            report += ["    " + l for l in difflib.unified_diff(da, db, "v1.6", "v2", lineterm="", n=1)]
    for k in only_a:
        report.append(f"## v1.6에만 있다 ({len(ga[k])}통) {k}")
    for k in only_b:
        report.append(f"## v2에만 있다 ({len(gb[k])}통) {k}")
    head = f"# 메일: v1.6 {len(a)}통, v2 {len(b)}통, 같음 {same}, 다름 {diff}, v1.6에만 {len(only_a)}종, v2에만 {len(only_b)}종"
    text = head + "\n" + "\n".join(report) + "\n"
    if args.out:
        open(args.out, "w", encoding="utf-8").write(text)
    print(head)


if __name__ == "__main__":
    main()
