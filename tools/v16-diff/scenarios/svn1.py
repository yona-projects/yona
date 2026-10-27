"""Subversion 프로젝트(svnprep.sh로 8개 리비전을 올린 tester/svn1)의 코드, 커밋, 비교, 저장소 프로토콜 화면을 여러 사용자가 읽는다. 읽기만 한다.
data3 상태에서 실행한다."""
import urllib.parse

from harness import Step

ACTORS = ["anon", "admin", "tester", "other", "third"]


def q(path):
    return urllib.parse.quote(path, safe="/")


def paths(o="tester", p="svn1"):
    b = f"/{o}/{p}"
    out = [
        b, f"{b}?tabId=history", f"{b}/code", f"{b}/code/HEAD", f"{b}/code/HEAD/", f"{b}/code/HEAD/trunk", f"{b}/code/HEAD/trunk/src", f"{b}/code/HEAD/trunk/src/Main.java",
        f"{b}/code/HEAD/trunk/README.md", f"{b}/code/HEAD/trunk/docs", f"{b}/code/HEAD/trunk/docs/guide.txt", q(f"{b}/code/HEAD/trunk/docs/설명.txt"),
        q(f"{b}/code/HEAD/trunk/한글 폴더"), q(f"{b}/code/HEAD/trunk/한글 폴더/파일 이름.txt"), f"{b}/code/HEAD/trunk/data.bin", f"{b}/code/HEAD/trunk/logo.png",
        f"{b}/code/HEAD/trunk/nope", f"{b}/code/HEAD/branches", f"{b}/code/HEAD/branches/dev", f"{b}/code/HEAD/branches/dev/src/Dev.java", f"{b}/code/HEAD/tags",
        f"{b}/code/HEAD/tags/v1", f"{b}/code/3/trunk", f"{b}/code/3/trunk/src/Main.java", f"{b}/code/1/trunk/src/Main.java", f"{b}/code/99/trunk", f"{b}/code/abc/trunk",
        f"{b}/code/HEAD/!", f"{b}/code/HEAD/trunk/!", f"{b}/code/HEAD/trunk/!/src",
        f"{b}/rawcode/HEAD/trunk/README.md", f"{b}/rawcode/HEAD/trunk/logo.png", q(f"{b}/rawcode/HEAD/trunk/한글 폴더/파일 이름.txt"), f"{b}/rawcode/HEAD/trunk/nope",
        f"{b}/rawcode/2/trunk/src/Main.java", f"{b}/files/HEAD/trunk/README.md", f"{b}/image/HEAD/trunk/logo.png", f"{b}/image/HEAD/trunk/README.md",
        f"{b}/commits", f"{b}/commits/HEAD/", f"{b}/commits/HEAD/trunk", f"{b}/commits/HEAD/trunk/src/Main.java", f"{b}/commits/HEAD/branches/dev", f"{b}/commits/HEAD/nope",
        f"{b}/commits/HEAD/?page=1", f"{b}/commits/5/", f"{b}/commit/99", f"{b}/commit/abc", f"{b}/commit/0", f"{b}/branches", f"{b}/compare/1..8", f"{b}/compare/3..4",
        f"{b}/compare/8..1", f"{b}/compare/x..y", f"{b}/compare/2..2", f"{b}/newPullRequestForm", f"{b}/search?keyword=Main", f"{b}/statistics",
        f"{b}/mentionListAtCommitDiff?commitId=3", f"{b}/mentionListAtCommitDiff?commitId=zzz", f"{b}/info/refs?service=git-upload-pack",
        f"/svn/{o}/{p}", f"/svn/{o}/{p}/trunk/README.md", f"/svn/{o}/{p}/nope", f"/svn/{o}/nope",
    ]
    out += [f"{b}/commit/{r}" for r in range(1, 9)]
    out += [f"{b}/commit/{r}?format=json" for r in (1, 4)]
    out += [f"{b}/commit/{r}/trunk/README.md" for r in (1,)]
    return out


def steps():
    s = []
    s.append(Step("admin", "POST", "/users/login", data={"loginIdOrEmail": "admin", "password": "admin1234"}, note="관리자 로그인"))
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    for actor in ACTORS:
        if actor != "anon":
            pw = "admin1234" if actor == "admin" else "pass1234"
            s.append(Step(actor, "POST", "/users/login", data={"loginIdOrEmail": actor, "password": pw}, note=f"{actor} 로그인"))
    for actor in ACTORS:
        for pth in paths():
            s.append(Step(actor, "GET", pth))
    return s
