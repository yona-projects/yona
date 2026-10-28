#!/usr/bin/env python3
"""파일 종류, 문자 부호화, 줄바꿈, 크기, 심볼릭 링크, 서브모듈, 모드 변경, 이름 바꾸기, 병합, 태그, 브랜치가 다양한 시험 저장소를 결정적으로(고정 날짜) 만들고
두 서버의 같은 프로젝트(기본 tester/rich1)에 올린다. 두 서버가 같은 해시를 갖는다.
사용: mkrich.py [소유자 프로젝트 올리는사용자]"""
import os, shutil, struct, subprocess, sys, zlib, zipfile, io

OWNER, NAME, USER = (sys.argv[1:4] + ["tester", "rich1", "tester"])[:3] if len(sys.argv) >= 4 else ("tester", "rich1", "tester")
W = f"/home/jiho/yona-convert/yona-diff-work/git-{OWNER}-{NAME}-rich"
shutil.rmtree(W, ignore_errors=True)
os.makedirs(W)
os.chdir(W)
ENV = dict(os.environ, GIT_AUTHOR_NAME="Tester", GIT_AUTHOR_EMAIL="tester@example.com", GIT_COMMITTER_NAME="Tester", GIT_COMMITTER_EMAIL="tester@example.com")


def git(*args, date=None, env=None, check=True, input=None):
    e = dict(ENV, **(env or {}))
    if date:
        e["GIT_AUTHOR_DATE"] = e["GIT_COMMITTER_DATE"] = date
    return subprocess.run(["git", *args], env=e, check=check, capture_output=True, input=input).stdout.decode("utf-8", "replace")


def w(path, data, mode=None):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    mode_ = "wb" if isinstance(data, bytes) else "w"
    with open(path, mode_, **({} if isinstance(data, bytes) else {"encoding": "utf-8", "newline": ""})) as f:
        f.write(data)
    if mode:
        os.chmod(path, mode)


n = [0]


def commit(msg, date=None, **env):
    n[0] += 1
    d = date or f"2026-03-{1 + (n[0] // 24):02d}T{(n[0] % 24):02d}:{(n[0] * 7) % 60:02d}:00+0900"
    git("add", "-A")
    git("commit", "-q", "--allow-empty-message", "-m", msg, date=d, env=env)


git("init", "-q", "-b", "master", ".")
git("config", "core.autocrlf", "false")
git("config", "core.filemode", "true")


def png(wd, ht, seed=0):
    raw = b"".join(b"\x00" + b"".join(bytes([(x * 5 + seed) % 256, (y * 7) % 256, 128]) for x in range(wd)) for y in range(ht))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", wd, ht, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


# 1. 텍스트 부호화와 줄바꿈
w("README.md", "# 풍부한 저장소\n\n![로고](img/a.png) [문서](docs/index.md) [상대](../x) [절대](http://example.org)\n\n| 열 | 값 |\n|---|---|\n| a | 1 |\n\n```python\nprint('hi')\n```\n\n<b>HTML</b> &amp; 이모지 😀\n")
ko = "한글 텍스트 파일입니다.\n두 번째 줄\n"
w("text/utf8.txt", ko + "emoji 😀🎉 ñ é ü\n")
w("text/utf8bom.txt", b"\xef\xbb\xbf" + ko.encode())
w("text/euckr.txt", ko.encode("euc-kr"))
w("text/sjis.txt", "日本語のテキストです。\n二行目\n".encode("shift_jis"))
w("text/gbk.txt", "中文文本文件。\n第二行\n".encode("gbk"))
w("text/big5.txt", "繁體中文檔案。\n第二行\n".encode("big5"))
w("text/utf16le.txt", b"\xff\xfe" + ko.encode("utf-16-le"))
w("text/utf16be.txt", b"\xfe\xff" + ko.encode("utf-16-be"))
w("text/latin1.txt", "café résumé naïve\n".encode("latin-1"))
w("text/cp1251.txt", "Привет мир\nвторая строка\n".encode("cp1251"))
w("text/invalid-utf8.txt", b"valid \xff\xfe invalid \xc3\x28 bytes\n")
w("text/crlf.txt", "line1\r\nline2\r\nline3\r\n")
w("text/cr.txt", "line1\rline2\rline3\r")
w("text/mixed-eol.txt", "a\r\nb\nc\rd\r\n")
w("text/no-newline.txt", "no newline at end")
w("text/empty.txt", "")
w("text/onechar.txt", "x")
w("text/tabs.txt", "a\tb\t\tc\n\t\tindented\n")
w("text/long-line.txt", "L" * 60000 + "\n")
w("text/many-lines.txt", "".join(f"line {i}\n" for i in range(1, 12001)))
w("text/big.txt", "B" * 80 + "\n" * 1 + ("x" * 99 + "\n") * 21000)
w("text/exact1mb.txt", ("e" * 1023 + "\n") * 1024)
w("text/1mb-plus1.txt", ("e" * 1023 + "\n") * 1024 + "z")
w("text/null-in-text.txt", "text before\x00text after\n")
w("text/ansi.txt", "\x1b[31mred\x1b[0m plain\n")
w("text/html-ish.txt", "<script>alert('x')</script> <b>bold</b> &lt; &amp;\n")
# 2. 이진 파일과 그림
w("bin/random.bin", bytes((i * 131 + 7) % 256 for i in range(4096)))
w("bin/zero.bin", b"\x00" * 2048)
w("bin/fake.pdf", b"%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n")
buf = io.BytesIO()
with zipfile.ZipFile(buf, "w") as z:
    z.writestr("a.txt", "zip content")
w("bin/archive.zip", buf.getvalue())
w("img/a.png", png(16, 16))
w("img/b.jpg", bytes.fromhex("ffd8ffe000104a46494600010100000100010000ffdb004300") + b"\x08" * 64 + bytes.fromhex("ffd9"))
w("img/c.gif", b"GIF89a\x01\x00\x01\x00\x80\x00\x00\x00\x00\x00\xff\xff\xff!\xf9\x04\x01\x00\x00\x00\x00,\x00\x00\x00\x00\x01\x00\x01\x00\x00\x02\x02D\x01\x00;")
w("img/d.svg", '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"><rect width="20" height="20" fill="red"/><script>alert(1)</script></svg>\n')
w("img/e.bmp", b"BM" + struct.pack("<IHHI", 58, 0, 0, 54) + struct.pack("<IiiHHIIiiII", 40, 1, 1, 1, 24, 0, 4, 0, 0, 0, 0) + b"\x00\x00\xff\x00")
w("img/UPPER.PNG", png(8, 8, 3))
w("img/no-ext", png(8, 8, 5))
w("img/fake.png", b"this is not a png\n")
# 3. 여러 언어의 소스
SRC = {"a.py": "def f(x):\n    return x * 2\n", "b.js": "const f = (x) => x * 2;\n", "c.ts": "const f = (x: number): number => x * 2;\n", "d.go": "package main\nfunc main() {}\n",
       "e.rs": "fn main() { println!(\"hi\"); }\n", "f.c": "int main(void) { return 0; }\n", "g.cpp": "#include <iostream>\nint main() { std::cout << 1; }\n", "h.cs": "class A { static void Main() {} }\n",
       "i.rb": "puts 'hi'\n", "j.php": "<?php echo 'hi'; ?>\n", "k.kt": "fun main() { println(\"hi\") }\n", "l.scala": "object A extends App { println(1) }\n", "m.sql": "SELECT * FROM t WHERE a = 1;\n",
       "n.sh": "#!/bin/sh\necho hi\n", "o.yaml": "a:\n  - b\n  - c\n", "p.json": '{"a": [1, 2, {"b": null}]}\n', "q.xml": "<a><b c=\"d\">e</b></a>\n", "r.html": "<!doctype html><html><body><p>hi</p></body></html>\n",
       "s.css": "a { color: red; }\n", "t.md": "# 제목\n\n본문 **굵게**\n", "u.rst": "Title\n=====\n\ntext\n", "v.txt": "plain\n", "W.JAVA": "class W {}\n", "a.b.c.d.java": "class D {}\n", "weird name (1).txt": "w\n",
       "Dockerfile": "FROM alpine\nRUN echo hi\n", "Makefile": "all:\n\techo hi\n", ".gitignore": "*.log\nbuild/\n", ".hidden": "hidden\n", "LICENSE": "MIT License\n", "no_extension": "no ext\n", "file.": "dot end\n",
       "ipynb.ipynb": '{"cells": [], "metadata": {}, "nbformat": 4, "nbformat_minor": 5}\n', "data.csv": "a,b,c\n1,2,3\n한글,\"x,y\",z\n", "data.tsv": "a\tb\n1\t2\n", "x.min.js": "var a=1;\n"}
for k, v in SRC.items():
    w(f"src/{k}", v)
w("src/run.sh", "#!/bin/sh\necho run\n", 0o755)
# 4. 읽을 파일(README) 변형
w("docs/index.md", "# 문서 색인\n\n[상위](../README.md) [형제](sub/page.md) ![그림](../img/a.png)\n")
w("docs/README.markdown", "# markdown 확장자\n")
w("docs/sub/page.md", "# 하위 쪽\n\n[루트](/README.md)\n")
w("docs/sub/README", "확장자 없는 README\n")
w("docs/readme.txt", "소문자 readme.txt\n")
w("deep/a/b/c/d/e/f/g/h/i/j/k.txt", "deep\n")
for i in range(1, 161):
    w(f"many/f{i:03d}.txt", f"file {i}\n")
commit("초기 커밋: 다양한 파일", date="2026-03-01T09:00:00+0900")

# 5. 심볼릭 링크, 서브모듈, 모드
os.symlink("text/utf8.txt", "link-to-file")
os.symlink("text", "link-to-dir")
os.symlink("does/not/exist", "dangling-link")
os.symlink("loop-b", "loop-a")
os.symlink("loop-a", "loop-b")
os.symlink("../../etc/passwd", "escape-link")
w(".gitmodules", '[submodule "sub"]\n\tpath = sub\n\turl = https://example.org/sub.git\n')
git("update-index", "--add", "--cacheinfo", "160000,1234567890123456789012345678901234567890,sub")
commit("링크와 서브모듈", date="2026-03-02T09:00:00+0900")
os.chmod("src/a.py", 0o755)
commit("모드만 바꿈", date="2026-03-02T10:00:00+0900")

# 6. 수정, 이름 바꾸기, 복사, 삭제, 되살리기, 공백만 바꿈, 줄바꿈만 바꿈
w("text/utf8.txt", ko + "emoji 😀🎉 ñ é ü\n세 번째 줄\n")
commit("텍스트 수정", date="2026-03-03T09:00:00+0900")
git("mv", "src/a.py", "src/renamed.py")
commit("이름 바꾸기", date="2026-03-03T10:00:00+0900")
shutil.copy("src/renamed.py", "src/copied.py")
commit("복사", date="2026-03-03T11:00:00+0900")
git("rm", "-q", "src/copied.py")
commit("삭제", date="2026-03-03T12:00:00+0900")
w("src/copied.py", "def f(x):\n    return x * 2\n")
commit("되살림", date="2026-03-03T13:00:00+0900")
w("text/tabs.txt", "a  b  c\n  indented\n")
commit("공백만 바꿈 (탭을 공백으로)", date="2026-03-03T14:00:00+0900")
w("text/crlf.txt", "line1\nline2\nline3\n")
commit("줄바꿈만 바꿈 (CRLF -> LF)", date="2026-03-03T15:00:00+0900")
w("bin/random.bin", bytes((i * 17 + 3) % 256 for i in range(4096)))
w("img/a.png", png(16, 16, 9))
commit("이진 파일 바꿈", date="2026-03-03T16:00:00+0900")
w("text/many-lines.txt", "".join(f"line {i}\n" if i % 100 else f"changed {i}\n" for i in range(1, 12001)))
commit("많은 줄 중 일부 바꿈", date="2026-03-03T17:00:00+0900")
for i in range(1, 161):
    w(f"many/f{i:03d}.txt", f"file {i} edited\n")
commit("파일 160개 한꺼번에 수정", date="2026-03-03T18:00:00+0900")

# 7. 이력: 작성자, 시각대, 메시지 변형
CASES = [("빈 작성자 정보 아님", "Alice Kim", "alice@example.org", "2026-03-04T09:00:00+0530"), ("한글 작성자", "김철수", "chulsoo@example.org", "2026-03-04T10:00:00-0930"),
         ("등록된 사용자 이메일", "Other", "other@example.com", "2026-03-04T11:00:00+1245"), ("이메일 형식 이상", "Weird", "weird", "2026-03-04T12:00:00+0000"),
         ("아주 옛날", "Old", "old@example.org", "1970-01-02T00:00:00+0000"), ("먼 미래", "Future", "future@example.org", "2099-12-31T23:59:59+0900")]
for i, (msg, name, email, date) in enumerate(CASES):
    w(f"hist/h{i}.txt", f"{msg}\n")
    n[0] += 1
    git("add", "-A")
    git("commit", "-q", "-m", msg + (" #1 참조" if i == 0 else ""), env={"GIT_AUTHOR_NAME": name, "GIT_AUTHOR_EMAIL": email, "GIT_AUTHOR_DATE": date, "GIT_COMMITTER_DATE": "2026-03-04T20:00:00+0900"})
w("hist/long-msg.txt", "x\n")
git("add", "-A")
git("commit", "-q", "-m", "아주 긴 첫 줄 " + "가나다라마바사" * 30 + "\n\n본문 첫 단락\n\n- 항목 `코드` **굵게**\n- <script>alert(1)</script> & &amp;\n\nSigned-off-by: Tester <tester@example.com>\nCo-authored-by: Other <other@example.com>\n" + "긴 본문 줄 " * 200, date="2026-03-05T09:00:00+0900")
w("hist/empty-msg.txt", "x\n")
git("add", "-A")
git("commit", "-q", "--allow-empty-message", "-m", "", date="2026-03-05T10:00:00+0900")
for i in range(1, 121):
    w(f"hist/seq/s{i:03d}.txt", f"{i}\n")
    commit(f"순서 커밋 {i}", date=f"2026-03-{6 + i // 24:02d}T{i % 24:02d}:00:00+0900")

# 8. 가지와 병합
git("checkout", "-q", "-b", "feature/한글-가지")
w("branch/feature.txt", "feature\n")
commit("기능 가지 작업 1", date="2026-04-01T09:00:00+0900")
w("branch/feature2.txt", "feature2\n")
commit("기능 가지 작업 2", date="2026-04-01T10:00:00+0900")
git("checkout", "-q", "master")
w("branch/master-only.txt", "master\n")
commit("master 작업", date="2026-04-01T11:00:00+0900")
git("merge", "-q", "--no-ff", "-m", "기능 병합", "feature/한글-가지", date="2026-04-01T12:00:00+0900")
git("checkout", "-q", "-b", "conflict-a")
w("branch/conflict.txt", "a\n")
commit("충돌 a", date="2026-04-02T09:00:00+0900")
git("checkout", "-q", "master")
git("checkout", "-q", "-b", "conflict-b")
w("branch/conflict.txt", "b\n")
commit("충돌 b", date="2026-04-02T10:00:00+0900")
git("checkout", "-q", "master")
for i in range(1, 41):
    git("branch", f"bulk/b{i:02d}")
git("branch", "with space") if False else None
git("branch", "plus+branch")
git("branch", "release/1.0")
git("checkout", "-q", "-b", "octo1")
w("octo/1.txt", "1\n"); commit("octo1", date="2026-04-03T09:00:00+0900")
git("checkout", "-q", "master")
git("checkout", "-q", "-b", "octo2")
w("octo/2.txt", "2\n"); commit("octo2", date="2026-04-03T10:00:00+0900")
git("checkout", "-q", "master")
git("merge", "-q", "--no-ff", "-m", "문어 병합", "octo1", "octo2", date="2026-04-03T11:00:00+0900", check=False)
git("branch", "-D", "octo1", "octo2") if False else None
# 9. 태그
git("tag", "light1")
git("tag", "-a", "v1.0", "-m", "첫 릴리스\n\n" + "자세한 설명 " * 50, date="2026-04-04T09:00:00+0900")
git("tag", "-a", "v2.0-한글", "-m", "한글 태그", date="2026-04-04T10:00:00+0900")
git("tag", "-a", "nested-tag", "-m", "태그의 태그", "v1.0", date="2026-04-04T11:00:00+0900", check=False)
tree = git("rev-parse", "HEAD^{tree}").strip()
git("tag", "tree-tag", tree)
blob = git("rev-parse", "HEAD:README.md").strip()
git("tag", "blob-tag", blob)
for i in range(1, 31):
    git("tag", f"bulk-tag-{i:02d}")
print(git("log", "--oneline").count("\n"), "커밋", git("branch", "-a").count("\n"), "가지", git("tag").count("\n"), "태그", git("rev-parse", "HEAD").strip())

# 올리기
for port in (19200, 19300):
    url = f"http://{USER}:pass1234@127.0.0.1:{port}/{OWNER}/{NAME}"
    for refs in (["--all"], ["--tags"]):
        p = subprocess.run(["git", "push", "-q", url, *refs], env=ENV, capture_output=True, text=True)
        print(port, refs, p.returncode, p.stderr.strip().splitlines()[-1] if p.stderr.strip() else "")
