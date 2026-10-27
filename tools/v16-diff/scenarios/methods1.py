"""모든 v1.6 라우트에 지원하지 않는 요청 방식(GET, POST, PUT, DELETE, PATCH)과 HEAD, OPTIONS를 보내 상태와 머리글을 대조한다.
라우트가 받지 않는 방식은 부작용이 없어야 하므로 data2 상태에서 안전하게 실행한다. HEAD는 GET 라우트에만 보내되 부작용이 있는 주소는 뺀다.
같은 라우트를 익명과 `tester`로 보낸다."""
import os
import re

from harness import Step
from data1 import login

ROUTES = os.path.join(os.path.dirname(__file__), "..", "..", "..", "src", "test", "resources", "v16", "routes.tsv")
METHODS = ["GET", "POST", "PUT", "DELETE", "PATCH"]
SAMPLE = {
    "user": "tester", "project": "proj1", "projectName": "proj1", "owner": "tester", "ownerName": "tester", "organizationName": "org1", "id": "1", "number": "1",
    "branch": "master", "commentId": "1", "emailId": "1", "userId": "1", "rev": "master", "projectId": "1", "commitId": "0" * 40, "tabId": "projects",
    "provider": "github", "loginId": "tester", "verificationCode": "abc", "type": "issue", "token": "abc", "revA": "master", "revB": "master", "pullRequestId": "1",
    "organizationId": "1", "notiType": "x", "key": "abc", "issueId": "1", "path": "README.md", "file": "README.md",
}
SIDE_EFFECT = re.compile(r"logout|delete|accept|verify|confirm|authenticate|_init|resetVisitedList|nextstate|vote|enroll|leave|watch|setAs|close|reopen|toggle|reset|import|export|token")
SKIP = re.compile(r"^/svn/|^/\*paths$|^/!svn")


def concrete(pattern):
    path = re.sub(r"[:*](\w+)", lambda m: SAMPLE.get(m.group(1), "x"), pattern)
    return path


def steps():
    s = [login("tester")]
    rows = [l.split("\t") for l in open(ROUTES, encoding="utf-8").read().splitlines() if l.strip()]
    by_pattern = {}
    for method, pattern, _ in rows:
        by_pattern.setdefault(pattern, set()).add(method)
    for pattern, methods in by_pattern.items():
        if SKIP.search(pattern):
            continue
        path = concrete(pattern)
        for actor in ("anon", "tester"):
            for m in METHODS:
                if m not in methods:
                    s.append(Step(actor, m, path, note=f"{m} {pattern} [{actor}]"))
            if "GET" in methods and not SIDE_EFFECT.search(pattern):
                s.append(Step(actor, "HEAD", path, note=f"HEAD {pattern} [{actor}]"))
            s.append(Step(actor, "OPTIONS", path, note=f"OPTIONS {pattern} [{actor}]"))
    return s
