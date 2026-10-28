#!/usr/bin/env python3
"""특수 문자가 든 가지, 태그, 폴더, 파일 이름과 커밋 메시지를 가진 작은 저장소를 두 서버의 tester/sp1에 올린다."""
import os, shutil, subprocess
W = "/home/jiho/yona-convert/yona-diff-work/git-tester-sp1-special"
shutil.rmtree(W, ignore_errors=True)
os.makedirs(W)
os.chdir(W)
ENV = dict(os.environ, GIT_AUTHOR_NAME='Au<b>t&"h\'or', GIT_AUTHOR_EMAIL="tester@example.com", GIT_COMMITTER_NAME="Tester", GIT_COMMITTER_EMAIL="tester@example.com")


def git(*a, date="2026-05-01T09:00:00+0900", check=True):
    e = dict(ENV, GIT_AUTHOR_DATE=date, GIT_COMMITTER_DATE=date)
    return subprocess.run(["git", *a], env=e, check=check, capture_output=True, text=True).stdout


git("init", "-q", "-b", "master", ".")
d = "dir <b>&'\"%+#;"
os.makedirs(d)
open(f"{d}/file <i>&'\".txt", "w").write("<b>본문</b> & \"인용\" '작은' \\역슬래시 😀\n")
open("README.md", "w").write("# 특수 <b>제목</b> & 'q'\n\n[링크](http://example.org/?a=1&b=2)\n")
git("add", "-A")
git("commit", "-q", "-m", "제목 <b>굵게</b> & \"따옴표\" 'a' 😀\n\n본문 <script>alert(1)</script> & &amp; \"q\" %s #1")
git("branch", "br<b>&\"'#%+;x")
git("tag", "tag<b>&\"'#%+;x")
git("tag", "-a", "ann<i>tag", "-m", "주석 <b>태그</b> & 메시지")
for port in (19200, 19300):
    p = subprocess.run(["git", "push", "-q", f"http://tester:pass1234@127.0.0.1:{port}/tester/sp1", "--all"], env=ENV, capture_output=True, text=True)
    p2 = subprocess.run(["git", "push", "-q", f"http://tester:pass1234@127.0.0.1:{port}/tester/sp1", "--tags"], env=ENV, capture_output=True, text=True)
    print(port, p.returncode, p2.returncode, (p.stderr + p2.stderr).strip().splitlines()[-1:] )
