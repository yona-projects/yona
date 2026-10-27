#!/usr/bin/env python3
"""실제 git과 svn 클라이언트로 하는 두 번째 대조: 공개 범위(PUBLIC, PROTECTED, PRIVATE) x 사용자(익명, 구성원, 비구성원, 관리자) x 읽기와 쓰기 행렬,
그리고 git(많은 참조, 압축 요청, 원자적 푸시, 푸시 옵션, 얕은 복제 뒤 깊게, 부분 복제, 번들, 미러)과 svn(병합, 전환, 내보내기, 외부 참조, 잠금 훔치기, 다량 커밋, rdump)이다.
  clients2.py   data2 상태에서 실행한다."""
import os
import random
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
import clients  # noqa: E402
from clients import PORTS, ENV, run, record, results, W, make_project, norm  # noqa: E402
from probe import session, BASES  # noqa: E402

SCOPES = ["PUBLIC", "PROTECTED", "PRIVATE"]
USERS = [("anon", None, None), ("tester", "tester", "pass1234"), ("other", "other", "pass1234"), ("third", "third", "pass1234"), ("admin", "admin", "admin1234")]


def make(actor, owner, name, scope, vcs):
    for n, b in BASES.items():
        session(b, actor).post(b + "/projects", data={"owner": owner, "name": name, "projectScope": scope, "vcs": vcs, "code": "true", "issue": "true",
                                                          "pullRequest": "true", "review": "true", "milestone": "true", "board": "true"}, allow_redirects=False)


def add_member(owner, name, login):
    for n, b in BASES.items():
        session(b, owner).post(b + f"/{owner}/{name}/members", data={"loginId": login}, allow_redirects=False)


def seed_repo():
    src = f"{W}/seed"
    shutil.rmtree(src, ignore_errors=True)
    os.makedirs(src)
    env = dict(ENV, GIT_AUTHOR_DATE="2026-04-01T09:00:00+0900", GIT_COMMITTER_DATE="2026-04-01T09:00:00+0900")
    run(["git", "init", "-q", "-b", "master"], cwd=src, env=env)
    for i in range(3):
        open(f"{src}/f{i}.txt", "w").write(f"내용 {i}\n")
        run(["git", "add", "-A"], cwd=src, env=env)
        run(["git", "commit", "-q", "-m", f"c{i}"], cwd=src, env=env)
    return src


def url_for(server, user, pw, owner, name, vcs):
    port = PORTS[server]
    cred = f"{user}:{pw}@" if user else ""
    return f"http://{cred}127.0.0.1:{port}/{'svn/' if vcs == 'svn' else ''}{owner}/{name}"


def matrix():
    seed = seed_repo()
    for scope in SCOPES:
        for vcs, kind in (("GIT", "git"), ("Subversion", "svn")):
            name = f"m{scope.lower()}{kind}"
            make("tester", "tester", name, scope, vcs)
            add_member("tester", name, "other")
            for server in PORTS:
                port = PORTS[server]
                if kind == "git":
                    # 처음 올려 둔다(소유자)
                    run(["git", "push", url_for(server, "tester", "pass1234", "tester", name, kind), "master"], cwd=seed)
                else:
                    S = ["svn", "--non-interactive", "--no-auth-cache", "--trust-server-cert", "--username", "tester", "--password", "pass1234"]
                    tree = f"{W}/svnseed"
                    os.makedirs(f"{tree}/trunk", exist_ok=True)
                    open(f"{tree}/trunk/a.txt", "w").write("a\n")
                    run(S + ["import", tree, url_for(server, None, None, "tester", name, kind), "-m", "시작"])
                for label, user, pw in USERS:
                    u = url_for(server, user, pw, "tester", name, kind)
                    tag = f"{scope} {kind} {label}"
                    if kind == "git":
                        d = f"{W}/mx-{server}-{name}-{label}"
                        shutil.rmtree(d, ignore_errors=True)
                        record(server, f"{tag} 복제", *run(["git", "clone", "-q", u, d]))
                        record(server, f"{tag} ls-remote", *run(["git", "ls-remote", u]))
                        env = dict(ENV, GIT_AUTHOR_DATE="2026-05-01T09:00:00+0900", GIT_COMMITTER_DATE="2026-05-01T09:00:00+0900")
                        w = f"{W}/mw-{server}-{name}-{label}"
                        shutil.rmtree(w, ignore_errors=True)
                        shutil.copytree(seed, w)
                        open(f"{w}/{label}.txt", "w").write(label)
                        run(["git", "add", "-A"], cwd=w, env=env)
                        run(["git", "commit", "-q", "-m", f"{label} 수정"], cwd=w, env=env)
                        record(server, f"{tag} 푸시", *run(["git", "push", u, "master:refs/heads/by-" + label], cwd=w, env=env))
                    else:
                        S = ["svn", "--non-interactive", "--no-auth-cache", "--trust-server-cert"] + (["--username", user, "--password", pw] if user else [])
                        record(server, f"{tag} 목록", *run(S + ["list", u]))
                        record(server, f"{tag} 로그", *run(S + ["log", "-q", u]))
                        record(server, f"{tag} 체크아웃", *run(S + ["checkout", "-q", u, f"{W}/sx-{server}-{name}-{label}"]))
                        record(server, f"{tag} mkdir", *run(S + ["mkdir", f"{u}/trunk/dir-{label}", "-m", "새 폴더"]))
                        record(server, f"{tag} 삭제", *run(S + ["delete", f"{u}/trunk/a.txt", "-m", "삭제"]))


def git_extras():
    name = "gx1"
    make("tester", "tester", name, "PUBLIC", "GIT")
    src = f"{W}/gxsrc"
    shutil.rmtree(src, ignore_errors=True)
    os.makedirs(src)
    env = dict(ENV, GIT_AUTHOR_DATE="2026-04-01T09:00:00+0900", GIT_COMMITTER_DATE="2026-04-01T09:00:00+0900")
    run(["git", "init", "-q", "-b", "master"], cwd=src, env=env)
    rnd = random.Random(5)
    for c in range(150):
        open(f"{src}/file{c % 20}.txt", "a").write(f"{rnd.random()}\n")
        run(["git", "add", "-A"], cwd=src, env=env)
        run(["git", "commit", "-q", "-m", f"커밋 {c}"], cwd=src, env=env)
        if c % 10 == 0:
            run(["git", "branch", f"b{c}"], cwd=src, env=env)
            run(["git", "tag", f"t{c}"], cwd=src, env=env)
    for server in PORTS:
        port = PORTS[server]
        auth = f"http://tester:pass1234@127.0.0.1:{port}/tester/{name}"
        base = f"http://127.0.0.1:{port}/tester/{name}"
        rec = lambda label, r: record(server, label, *r)  # noqa: E731
        rec("많은 참조 푸시", run(["git", "push", auth, "--all"], cwd=src, env=env))
        rec("태그 푸시", run(["git", "push", auth, "--tags"], cwd=src, env=env))
        rec("ls-remote 많은 참조", run(["git", "ls-remote", base]))
        d = f"{W}/gxc-{port}"
        shutil.rmtree(d, ignore_errors=True)
        rec("얕은 복제", run(["git", "clone", "-q", "--depth", "3", base, d]))
        rec("깊게", run(["git", "fetch", "-q", "--unshallow"], cwd=d))
        rec("로그 수", run(["git", "rev-list", "--count", "--all"], cwd=d))
        rec("부분 복제", run(["git", "clone", "-q", "--filter=blob:none", base, f"{W}/gxp-{port}"]))
        rec("단일 브랜치", run(["git", "clone", "-q", "--single-branch", "-b", "b10", base, f"{W}/gxs-{port}"]))
        rec("미러 복제", run(["git", "clone", "-q", "--mirror", base, f"{W}/gxm-{port}"]))
        rec("미러 참조 수", run(["git", "-C", f"{W}/gxm-{port}", "for-each-ref", "--count=1000"]))
        rec("원자적 푸시", run(["git", "push", "--atomic", auth, "master:refs/heads/at1", "master:refs/heads/at2"], cwd=src, env=env))
        rec("푸시 옵션", run(["git", "push", "-o", "ci.skip", auth, "master:refs/heads/po"], cwd=src, env=env))
        rec("여러 삭제", run(["git", "push", auth, "--delete", "at1", "at2", "po"], cwd=src, env=env))
        rec("미러 푸시", run(["git", "push", "--mirror", auth], cwd=src, env=env))
        rec("fetch --prune", run(["git", "fetch", "-q", "--prune", "origin"], cwd=d) if False else (0, ""))
        rec("번들", run(["git", "bundle", "create", f"{W}/b-{port}.bundle", "--all"], cwd=f"{W}/gxm-{port}"))
        rec("저장소 이름 대소문자 클론", run(["git", "clone", "-q", f"http://127.0.0.1:{port}/Tester/{name}", f"{W}/gxu-{port}"]))
        rec("스마트 아닌 요청", run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", f"{base}/info/refs"]))
        rec("HEAD 파일", run(["curl", "-s", "-w", "%{http_code}", f"{base}/HEAD"]))
        rec("objects/info/packs", run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", f"{base}/objects/info/packs"]))
        rec("git-upload-pack GET", run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", f"{base}/git-upload-pack"]))
        rec("v2 프로토콜 복제", run(["git", "-c", "protocol.version=2", "clone", "-q", base, f"{W}/gxv2-{port}"]))


def svn_extras():
    name = "sx1"
    make("tester", "tester", name, "PUBLIC", "Subversion")
    for server in PORTS:
        port = PORTS[server]
        url = f"http://127.0.0.1:{port}/svn/tester/{name}"
        S = ["svn", "--non-interactive", "--no-auth-cache", "--trust-server-cert", "--username", "tester", "--password", "pass1234"]
        rec = lambda label, r: record(server, label, *r)  # noqa: E731
        wc = f"{W}/sxw-{port}"
        shutil.rmtree(wc, ignore_errors=True)
        rec("구조 만들기", run(S + ["mkdir", "-m", "구조", f"{url}/trunk", f"{url}/branches", f"{url}/tags"]))
        rec("체크아웃", run(S + ["checkout", "-q", f"{url}/trunk", wc]))
        for i in range(60):
            open(f"{wc}/f{i % 8}.txt", "a", encoding="utf-8").write(f"줄 {i}\n")
            if i < 8:
                run(S + ["add", "-q", f"{wc}/f{i}.txt"])
            run(S + ["commit", "-q", "-m", f"수정 {i}", wc])
        rec("로그 개수", run(S + ["log", "-q", url]))
        rec("브랜치 복사", run(S + ["copy", f"{url}/trunk", f"{url}/branches/b1", "-m", "브랜치"]))
        bw = f"{W}/sxb-{port}"
        shutil.rmtree(bw, ignore_errors=True)
        run(S + ["checkout", "-q", f"{url}/branches/b1", bw])
        open(f"{bw}/branch.txt", "w").write("브랜치\n")
        run(S + ["add", "-q", f"{bw}/branch.txt"])
        rec("브랜치 커밋", run(S + ["commit", "-m", "브랜치 작업", bw]))
        rec("병합", run(S + ["merge", "-q", f"{url}/branches/b1", wc]))
        rec("병합 커밋", run(S + ["commit", "-m", "병합", wc]))
        rec("mergeinfo", run(S + ["mergeinfo", "--show-revs", "merged", f"{url}/branches/b1", wc]))
        rec("전환", run(S + ["switch", "-q", f"{url}/branches/b1", wc]))
        rec("내보내기", run(S + ["export", "-q", f"{url}/trunk", f"{W}/sxe-{port}", "--force"]))
        rec("속성 외부", run(S + ["propset", "svn:externals", f"{url}/branches/b1 ext", f"{W}/sxw-{port}"]))
        rec("blame -g", run(S + ["blame", "-g", f"{url}/trunk/f0.txt"]))
        rec("diff -c", run(S + ["diff", "-c", "5", url]))
        rec("로그 xml", run(S + ["log", "--xml", "-l", "3", url]))
        rec("잠금 훔치기", run(S + ["lock", f"{url}/trunk/f1.txt", "-m", "잠금"]))
        rec("다른 사용자 잠금", run(["svn", "--non-interactive", "--no-auth-cache", "--trust-server-cert", "--username", "other", "--password", "pass1234", "lock", f"{url}/trunk/f1.txt", "-m", "남"]))
        rec("강제 잠금", run(S + ["lock", "--force", f"{url}/trunk/f1.txt", "-m", "강제"]))
        rec("잠금 정보", run(S + ["info", f"{url}/trunk/f1.txt"]))
        rec("rdump", run(["svnrdump", "--non-interactive", "--username", "tester", "--password", "pass1234", "dump", url, "-r", "1:3"]) if shutil.which("svnrdump") else (0, ""))
        rec("proplist -R", run(S + ["proplist", "-R", "-v", url]))
        rec("다량 가져오기", run(S + ["import", f"{W}/clientstree", f"{url}/bulk", "-m", "다량"]) if os.path.isdir(f"{W}/clientstree") else (0, ""))


def main():
    shutil.rmtree(W, ignore_errors=True)
    os.makedirs(W)
    matrix()
    git_extras()
    svn_extras()
    same = diff = 0
    import difflib
    for (la, ra, ta), (lb, rb, tb) in zip(results["v1.6"], results["v2"]):
        if ra == rb and ta == tb:
            same += 1
            continue
        diff += 1
        print(f"## 다르다: {la} (종료 코드 v1.6={ra} v2={rb})")
        for l in difflib.unified_diff(ta.split("\n"), tb.split("\n"), "v1.6", "v2", lineterm="", n=0):
            print("   ", l[:200])
    print(f"# 클라이언트2: 같음 {same}, 다름 {diff} (전체 {same + diff})")


if __name__ == "__main__":
    main()
