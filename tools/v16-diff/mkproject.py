#!/usr/bin/env python3
"""두 서버에 같은 프로젝트를 만든다. 사용: mkproject.py <행위자> <소유자> <이름> [공개범위] [vcs]"""
import sys
from probe import session, BASES

actor, owner, name = sys.argv[1:4]
scope = sys.argv[4] if len(sys.argv) > 4 else "PUBLIC"
vcs = sys.argv[5] if len(sys.argv) > 5 else "GIT"
for n, b in BASES.items():
    s = session(b, actor)
    r = s.post(b + "/projects", data={"owner": owner, "name": name, "projectScope": scope, "vcs": vcs, "code": "true", "issue": "true", "pullRequest": "true",
                                      "review": "true", "milestone": "true", "board": "true"}, allow_redirects=False)
    print(n, r.status_code, r.headers.get("Location"))
