#!/usr/bin/env python3
"""요청 하나를 두 서버에 보내 응답을 나란히 보여 준다.
사용: probe.py <행위자> <메서드> <경로> [이름=값 ...] [--mp] [--json '{...}'] [--body 글자수]
행위자: anon, admin(admin1234), 그 밖은 비밀번호 pass1234 로 로그인한다.
"""
import argparse
import json
import re
import sys

import requests

BASES = {"v1.6": "http://127.0.0.1:19200", "v2": "http://127.0.0.1:19300"}


def session(base, actor):
    s = requests.Session()
    s.headers["Accept-Language"] = "en-US,en;q=0.9"
    if actor != "anon":
        pw = "admin1234" if actor == "admin" else "pass1234"
        s.post(base + "/users/login", data={"loginIdOrEmail": actor, "password": pw}, allow_redirects=False)
    return s


def main():
    p = argparse.ArgumentParser()
    p.add_argument("actor")
    p.add_argument("method")
    p.add_argument("path")
    p.add_argument("fields", nargs="*")
    p.add_argument("--mp", action="store_true", help="멀티파트로 보낸다")
    p.add_argument("--json")
    p.add_argument("--body", type=int, default=600)
    p.add_argument("--header", action="append", default=[])
    a = p.parse_args()
    data = dict(f.split("=", 1) for f in a.fields)
    headers = dict(h.split(":", 1) for h in a.header)
    for name, base in BASES.items():
        s = session(base, a.actor)
        kw = dict(allow_redirects=False, headers=headers, timeout=60)
        if a.json:
            kw["json"] = json.loads(a.json)
        elif a.mp:
            kw["files"] = {k: (None, v) for k, v in data.items()}
        elif data:
            kw["data"] = data
        r = s.request(a.method, base + a.path, **kw)
        print(f"===== {name}: {r.status_code}")
        for k, v in r.headers.items():
            if k.lower() in ("content-type", "location", "cache-control", "content-disposition", "www-authenticate", "set-cookie", "content-length", "etag", "last-modified", "expires", "pragma"):
                print(f"  {k}: {v[:160]}")
        text = r.text if "text" in r.headers.get("content-type", "") or "json" in r.headers.get("content-type", "") or "javascript" in r.headers.get("content-type", "") else f"<{len(r.content)} bytes>"
        text = re.sub(r"\s+\n", "\n", text)
        print("  body:", text[: a.body].replace("\n", "\n        "))


if __name__ == "__main__":
    main()
