"""git 저장소(gitprep.sh로 올린 tester/proj1)가 있는 상태에서 여러 사용자가 코드, 커밋, 비교, 브랜치 화면을 읽는다. 읽기만 한다."""
import urllib.parse

from harness import Step

ACTORS = ["anon", "admin", "tester", "other", "third"]
def _hashes():
    """gitprep.sh가 만든 저장소의 커밋 해시를 오래된 순으로 읽는다."""
    import subprocess
    out = subprocess.run(["git", "-C", "/home/jiho/yona-convert/yona-diff-work/git-tester-proj1-base", "log", "--reverse", "--format=%H"], capture_output=True, text=True, check=True).stdout.split()
    return out


H = _hashes()


def q(path):
    return urllib.parse.quote(path, safe="/")


def paths(o="tester", p="proj1"):
    b = f"/{o}/{p}"
    out = [
        f"{b}/code", f"{b}/code/master", f"{b}/code/dev", f"{b}/code/master/src", f"{b}/code/master/src/main", f"{b}/code/master/src/main/Main.java",
        f"{b}/code/master/README.md", q(f"{b}/code/master/docs/설명.txt"), q(f"{b}/code/master/한글 폴더"), f"{b}/code/master/data.csv",
        f"{b}/code/master/logo.png", f"{b}/code/master/nope", f"{b}/code/nobranch", f"{b}/code/v1.0", f"{b}/code/light-tag", f"{b}/code/{H[1]}",
        f"{b}/code/{H[1]}/README.md", f"{b}/code/dev/docs/dev.txt", f"{b}/code/master/src/main/Util.java",
        f"{b}/code/!", f"{b}/code/master/!", f"{b}/code/master/!/src", f"{b}/code/master/!/src/main", f"{b}/code/!/src",
        f"{b}/rawcode/master/README.md", f"{b}/rawcode/master/logo.png", q(f"{b}/rawcode/master/docs/설명.txt"), f"{b}/rawcode/master/nope",
        f"{b}/rawcode/dev/docs/dev.txt", f"{b}/files/master/README.md", f"{b}/files/master/logo.png", f"{b}/image/master/logo.png",
        f"{b}/image/master/README.md", f"{b}/code/master/download", f"{b}/code/dev/download", f"{b}/code/v1.0/download",
        f"{b}/commits", f"{b}/commits/master/", f"{b}/commits/dev/", f"{b}/commits/master/README.md", f"{b}/commits/master/src/main/Main.java",
        f"{b}/commits/nobranch/", f"{b}/commits/master/nope", f"{b}/commits/master/?page=1", f"{b}/commits/dev/src",
        f"{b}/commit/deadbeef", f"{b}/branches", f"{b}/compare/{H[0]}..{H[3]}", f"{b}/compare/master..dev", f"{b}/compare/dev..master",
        f"{b}/compare/aaaa..bbbb", f"{b}/compare/{H[1]}..{H[1]}", f"{b}/compare/v1.0..master", f"{b}/info/refs?service=git-upload-pack",
        f"{b}/info/refs?service=git-receive-pack", f"{b}/info/refs", f"{b}/newPullRequestForm", f"{b}/newPullRequest/mergeResult",
        f"{b}/newPullRequest/mergeResult?fromBranch=dev&toBranch=master", f"{b}/search?keyword=Main", f"{b}/statistics", f"{b}/statistics?branch=master",
        f"{b}/mentionListAtCommitDiff?commitId={H[2]}", f"{b}/mentionListAtCommitDiff?commitId=zzz", f"{b}/issue/1/timeline",
    ]
    out += [f"{b}/commit/{h}" for h in H]
    out += [f"{b}/commit/{h}?format=json" for h in H[:2]]
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
