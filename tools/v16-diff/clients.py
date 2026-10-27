#!/usr/bin/env python3
"""실제 git과 svn 클라이언트로 두 서버(v1.6: 19200, v2: 19300)에 같은 일을 시켜 결과를 대조한다.
한글 경로, 큰 저장소(수백 개 파일, 수십 MB), 인증 흐름(틀린 비밀번호, 익명, 권한 없는 사용자), 태그, 브랜치 삭제, 얕은 복제, svn 가져오기와 이력을 다룬다.
  clients.py            두 서버에 프로젝트를 만들고 전부 돈다(data2 상태에서 실행)
출력은 서버 주소와 시각을 가린 뒤 줄 단위로 견준다. 같으면 `같음`, 다르면 차이를 보인다."""
import os
import random
import re
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
from probe import session, BASES  # noqa: E402

W = "/home/jiho/yona-convert/yona-diff-work/clients"
PORTS = {"v1.6": 19200, "v2": 19300}
ENV = dict(os.environ, GIT_AUTHOR_NAME="Tester", GIT_AUTHOR_EMAIL="tester@example.com", GIT_COMMITTER_NAME="Tester", GIT_COMMITTER_EMAIL="tester@example.com",
           GIT_TERMINAL_PROMPT="0", LC_ALL="C.UTF-8")
results = {"v1.6": [], "v2": []}


def run(cmd, cwd=None, env=None, inp=None, timeout=600):
    p = subprocess.run(cmd, cwd=cwd, env=env or ENV, input=inp, capture_output=True, text=True, timeout=timeout, errors="replace")
    return p.returncode, (p.stdout + p.stderr)


def norm(text, port):
    text = text.replace(str(port), "<port>")
    text = re.sub(r"\d{4}-\d\d-\d\d[T ]\d\d:\d\d:\d\d[^\s]*", "<시각>", text)
    text = re.sub(r"!svn/act/[0-9a-f-]{36}", "!svn/act/<id>", text)
    text = re.sub(r"(Receiving|Resolving|remote: Counting|remote: Compressing|Counting|Compressing|Writing|Delta compression|Enumerating)[^\n]*\n?", "", text)
    text = re.sub(r"\d+(\.\d+)? (KiB|MiB|bytes)[^\n]*", "<크기>", text)
    text = re.sub(r"/home/jiho/yona-convert/yona-diff-work/clients/[^/\s]+", "<작업>", text)
    return "\n".join(l.rstrip() for l in text.split("\n") if l.strip())


def record(server, label, rc, out):
    results[server].append((label, rc, norm(out, PORTS[server])))


def build_source():
    """결정적인 내용과 시각의 큰 저장소를 만든다."""
    src = f"{W}/src"
    shutil.rmtree(W, ignore_errors=True)
    os.makedirs(src)
    env = dict(ENV, GIT_AUTHOR_DATE="2026-04-01T09:00:00+0900", GIT_COMMITTER_DATE="2026-04-01T09:00:00+0900")
    run(["git", "init", "-q", "-b", "master"], cwd=src, env=env)
    rnd = random.Random(7)
    for c in range(1, 61):
        env = dict(env, GIT_AUTHOR_DATE=f"2026-04-{(c % 28) + 1:02d}T09:{c % 60:02d}:00+0900", GIT_COMMITTER_DATE=f"2026-04-{(c % 28) + 1:02d}T09:{c % 60:02d}:00+0900")
        for f in range(8):
            d = f"{src}/디렉터리{(c + f) % 12}/하위 폴더{(c * f) % 5}"
            os.makedirs(d, exist_ok=True)
            with open(f"{d}/파일 {c}-{f}.txt", "w", encoding="utf-8") as fh:
                fh.write("".join(f"줄 {i} {rnd.random()}\n" for i in range(rnd.randint(5, 400))))
        if c == 10:
            with open(f"{src}/big.bin", "wb") as fh:
                fh.write(rnd.randbytes(30 * 1024 * 1024))
        if c == 30:
            with open(f"{src}/big.bin", "ab") as fh:
                fh.write(rnd.randbytes(5 * 1024 * 1024))
        run(["git", "add", "-A"], cwd=src, env=env)
        run(["git", "commit", "-q", "-m", f"커밋 {c}: 파일 추가와 수정"], cwd=src, env=env)
    run(["git", "tag", "v1"], cwd=src, env=env)
    run(["git", "tag", "-a", "v2", "-m", "주석 태그"], cwd=src, env=env)
    run(["git", "checkout", "-q", "-b", "feature/한글-브랜치"], cwd=src, env=env)
    with open(f"{src}/feature.txt", "w", encoding="utf-8") as fh:
        fh.write("기능\n")
    run(["git", "add", "-A"], cwd=src, env=env)
    run(["git", "commit", "-q", "-m", "기능 브랜치"], cwd=src, env=env)
    run(["git", "checkout", "-q", "master"], cwd=src, env=env)
    return src


def make_project(actor, owner, name, vcs):
    for n, b in BASES.items():
        s = session(b, actor)
        s.post(b + "/projects", data={"owner": owner, "name": name, "projectScope": "PUBLIC", "vcs": vcs, "code": "true", "issue": "true", "pullRequest": "true",
                                      "review": "true", "milestone": "true", "board": "true"}, allow_redirects=False)


def git_flow(server, src):
    port = PORTS[server]
    base = f"http://127.0.0.1:{port}/tester/bigg1"
    auth = f"http://tester:pass1234@127.0.0.1:{port}/tester/bigg1"
    rec = lambda label, rc_out: record(server, label, *rc_out)  # noqa: E731
    rec("잘못된 비밀번호 푸시", run(["git", "push", f"http://tester:wrong@127.0.0.1:{port}/tester/bigg1", "master"], cwd=src))
    rec("익명 푸시", run(["git", "push", base, "master"], cwd=src))
    rec("권한 없는 사용자 푸시", run(["git", "push", f"http://other:pass1234@127.0.0.1:{port}/tester/bigg1", "master"], cwd=src))
    rec("푸시(큰 저장소)", run(["git", "push", auth, "master", "feature/한글-브랜치", "--tags"], cwd=src))
    rec("ls-remote", run(["git", "ls-remote", base]))
    dest = f"{W}/clone-{port}"
    rec("익명 복제", run(["git", "clone", "-q", base, dest]))
    rec("복제 HEAD", run(["git", "rev-parse", "HEAD"], cwd=dest))
    rec("복제 트리", (0, subprocess.run("git ls-tree -r HEAD | sha1sum", shell=True, cwd=dest, capture_output=True, text=True).stdout))
    rec("fsck", run(["git", "fsck", "--full"], cwd=dest))
    rec("로그 개수", run(["git", "rev-list", "--count", "--all"], cwd=dest))
    rec("브랜치", run(["git", "branch", "-a"], cwd=dest))
    rec("태그", run(["git", "tag", "-l"], cwd=dest))
    rec("한글 파일 보기", run(["git", "show", "HEAD:디렉터리1/하위 폴더0/파일 1-0.txt"], cwd=dest))
    rec("얕은 복제", run(["git", "clone", "-q", "--depth", "1", base, f"{W}/shallow-{port}"]))
    rec("얕은 복제 개수", run(["git", "rev-list", "--count", "HEAD"], cwd=f"{W}/shallow-{port}"))
    rec("프로토콜 v2 ls-remote", run(["git", "-c", "protocol.version=2", "ls-remote", base]))
    rec("프로토콜 v0 ls-remote", run(["git", "-c", "protocol.version=0", "ls-remote", base]))
    # 변경 푸시와 가져오기
    env = dict(ENV, GIT_AUTHOR_DATE="2026-05-01T09:00:00+0900", GIT_COMMITTER_DATE="2026-05-01T09:00:00+0900")
    with open(f"{dest}/새 파일.txt", "w", encoding="utf-8") as fh:
        fh.write("새 내용\n")
    run(["git", "add", "-A"], cwd=dest, env=env)
    run(["git", "commit", "-q", "-m", "복제본에서 수정"], cwd=dest, env=env)
    rec("복제본 푸시", run(["git", "push", auth, "master"], cwd=dest, env=env))
    rec("태그 푸시", run(["git", "push", auth, "v1", "--force"], cwd=dest, env=env))
    rec("강제 푸시 거절(비빨리감기)", run(["git", "push", auth, "HEAD~1:master"], cwd=dest, env=env))
    rec("강제 푸시", run(["git", "push", "-f", auth, "HEAD~1:master"], cwd=dest, env=env))
    rec("브랜치 삭제", run(["git", "push", auth, "--delete", "feature/한글-브랜치"], cwd=dest, env=env))
    rec("없는 저장소", run(["git", "ls-remote", f"http://127.0.0.1:{port}/tester/nothing"]))
    rec("다른 이름 대소문자", run(["git", "ls-remote", f"http://127.0.0.1:{port}/tester/BIGG1"]))
    rec(".git 접미사", run(["git", "ls-remote", f"{base}.git"]))
    rec("마지막 브랜치", run(["git", "ls-remote", base]))


def svn_flow(server):
    port = PORTS[server]
    url = f"http://127.0.0.1:{port}/svn/tester/bigs1"
    S = ["svn", "--non-interactive", "--no-auth-cache", "--trust-server-cert"]
    A = S + ["--username", "tester", "--password", "pass1234"]
    rec = lambda label, rc_out: record(server, label, *rc_out)  # noqa: E731
    wc = f"{W}/svnwc-{port}"
    tree = f"{W}/svntree"
    if not os.path.exists(tree):
        rnd = random.Random(11)
        for i in range(120):
            d = f"{tree}/trunk/폴더 {i % 9}/하위{i % 4}"
            os.makedirs(d, exist_ok=True)
            with open(f"{d}/문서 {i}.txt", "w", encoding="utf-8") as fh:
                fh.write("".join(f"{rnd.random()}\n" for _ in range(rnd.randint(1, 200))))
        with open(f"{tree}/trunk/큰 파일.bin", "wb") as fh:
            fh.write(rnd.randbytes(8 * 1024 * 1024))
        os.makedirs(f"{tree}/branches", exist_ok=True)
        os.makedirs(f"{tree}/tags", exist_ok=True)
    rec("틀린 비밀번호 가져오기", run(S + ["--username", "tester", "--password", "wrong", "import", tree, url, "-m", "x"]))
    rec("익명 가져오기", run(S + ["import", tree, url, "-m", "x"]))
    rec("권한 없는 사용자 가져오기", run(S + ["--username", "other", "--password", "pass1234", "import", tree, url, "-m", "x"]))
    rec("가져오기(큰 트리)", run(A + ["import", tree, url, "-m", "큰 트리 가져오기"]))
    rec("익명 복사", run(S + ["checkout", "-q", url, f"{W}/anon-{port}"]))
    rec("체크아웃", run(A + ["checkout", "-q", url, wc]))
    rec("목록", run(A + ["list", "-R", url]))
    rec("정보", run(A + ["info", "--show-item", "revision", url]))
    rec("로그", run(A + ["log", "-v", url]))
    with open(f"{wc}/trunk/폴더 0/하위0/문서 0.txt", "a", encoding="utf-8") as fh:
        fh.write("수정\n")
    with open(f"{wc}/trunk/새 파일 한글.txt", "w", encoding="utf-8") as fh:
        fh.write("새 파일\n")
    run(A + ["add", "-q", f"{wc}/trunk/새 파일 한글.txt"])
    rec("상태", run(A + ["status", wc]))
    rec("차이", run(A + ["diff", wc]))
    rec("커밋", run(A + ["commit", "-m", "수정과 추가", wc]))
    rec("업데이트", run(A + ["update", wc]))
    rec("잠금", run(A + ["lock", f"{wc}/trunk/새 파일 한글.txt", "-m", "잠금"]))
    rec("잠금 해제", run(A + ["unlock", f"{wc}/trunk/새 파일 한글.txt"]))
    rec("속성", run(A + ["propset", "svn:keywords", "Id", f"{wc}/trunk/새 파일 한글.txt"]))
    rec("속성 커밋", run(A + ["commit", "-m", "속성", wc]))
    rec("복사", run(A + ["copy", f"{url}/trunk", f"{url}/branches/한글 브랜치", "-m", "브랜치"]))
    rec("이동", run(A + ["move", f"{url}/trunk/새 파일 한글.txt", f"{url}/trunk/이동됨.txt", "-m", "이동"]))
    rec("삭제", run(A + ["delete", f"{url}/branches/한글 브랜치", "-m", "삭제"]))
    rec("blame", run(A + ["blame", f"{url}/trunk/폴더 0/하위0/문서 0.txt"]))
    rec("cat", run(A + ["cat", f"{url}/trunk/이동됨.txt"]))
    rec("없는 경로", run(A + ["list", f"{url}/trunk/없음"]))
    rec("없는 저장소", run(A + ["list", f"http://127.0.0.1:{port}/svn/tester/nothing"]))
    rec("로그 한 줄", run(A + ["log", "-q", url, "-r", "HEAD:1"]))
    rec("속성 읽기", run(A + ["proplist", "-v", f"{url}/trunk/이동됨.txt"]))
    rec("충돌 업데이트", run(A + ["update", wc, "-r", "1"]))


def main():
    build_source()
    make_project("tester", "tester", "bigg1", "GIT")
    make_project("tester", "tester", "bigs1", "Subversion")
    for server in PORTS:
        git_flow(server, f"{W}/src")
        svn_flow(server)
    same = diff = 0
    for (la, ra, ta), (lb, rb, tb) in zip(results["v1.6"], results["v2"]):
        if ra == rb and ta == tb:
            same += 1
            continue
        diff += 1
        print(f"## 다르다: {la} (종료 코드 v1.6={ra} v2={rb})")
        import difflib
        for l in difflib.unified_diff(ta.split("\n"), tb.split("\n"), "v1.6", "v2", lineterm="", n=0):
            print("   ", l[:200])
    print(f"# 클라이언트: 같음 {same}, 다름 {diff} (전체 {same + diff})")


if __name__ == "__main__":
    main()
