"""풍부한 Subversion 저장소(`mksvnrich.sh`가 만든 tester/svnrich): 파일 종류, 속성, 이름 바꾸기, 병합, 특수한 이름, 긴 로그, 많은 리비전의 코드와 커밋 화면을 읽는다."""
import subprocess
import urllib.parse

from harness import Step
from data1 import login

W = "/home/jiho/yona-convert/yona-diff-work/svnrich-tester-svnrich-19200/wc"
B = "/tester/svnrich"


def q(p):
    return urllib.parse.quote(p, safe="/")


def sh(*a):
    return subprocess.run(a, capture_output=True, text=True, cwd=W).stdout


def steps():
    s = [login("tester")]
    head = int(sh("svn", "info", "--show-item", "last-changed-revision", "/home/jiho/yona-convert/yona-diff-work/svnrich-tester-svnrich-19200/wc").strip() or "1")
    listing = [l for l in sh("svn", "ls", "-R", "--depth", "infinity", "trunk").splitlines() if l and not l.endswith("/")]
    dirs = sorted({d for l in listing for d in ["/".join(l.split("/")[:i]) for i in range(1, len(l.split("/")))]})
    for a in ("anon", "tester"):
        for p in ("", "/code", "/code/HEAD", "/code/HEAD/", "/commits", "/commits/HEAD/", "/commits?page=2", "/commits?page=3", "/commits?page=99", "/branches", "/statistics"):
            s.append(Step(a, "GET", B + p, note=f"{p or '홈'} [{a}]"))
    for d in ["trunk"] + [f"trunk/{x}" for x in dirs[:30]] + ["branches", "branches/b1", "tags", "tags/v1", "tags/v2"]:
        s.append(Step("anon", "GET", f"{B}/code/HEAD/{q(d)}", note=f"폴더 {d}"))
    for f in listing[:200]:
        p = f"trunk/{f}"
        s.append(Step("anon", "GET", f"{B}/code/HEAD/{q(p)}", note=f"파일 {p}"))
        s.append(Step("anon", "GET", f"{B}/rawcode/HEAD/{q(p)}", note=f"원본 {p}"))
        if p.endswith((".png", ".svg")):
            s.append(Step("anon", "GET", f"{B}/image/HEAD/{q(p)}", note=f"그림 {p}"))
        if "/special/" in p or "/text/" in p or p.endswith("README.md"):
            s.append(Step("anon", "GET", f"{B}/commits/HEAD/{q(p)}", note=f"파일 이력 {p}"))
    for r in range(1, head + 1):
        s.append(Step("anon", "GET", f"{B}/commit/{r}", note=f"커밋 r{r}"))
        if r in (1, 3, 8, 9, 11, 12, 16, 17, 18, 19):
            s.append(Step("anon", "GET", f"{B}/code/{r}/trunk", note=f"r{r}의 trunk"))
            s.append(Step("anon", "GET", f"{B}/code/{r}/trunk/README.md", note=f"r{r}의 README"))
    for pair in ("1..5", "5..8", "8..9", "1..19", "19..1", "2..2", f"1..{head}", "tags/v1..tags/v2"):
        s.append(Step("anon", "GET", f"{B}/compare/{q(pair)}", note=f"비교 {pair}"))
    return s
