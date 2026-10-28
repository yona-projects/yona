"""풍부한 저장소(`mkrich.py`가 만든 tester/rich1): 모든 종류의 파일 보기(문자 부호화, 줄바꿈, 크기, 이진, 그림, 링크, 서브모듈), 원본 보기, 그림 보기, 이력, 커밋별 비교
(이름 바꾸기, 복사, 모드, 이진, 공백, 병합), 가지와 태그, 비교, 내려받기를 익명과 소유자로 읽는다. data2 상태에서 `mkrich.py`로 올린 뒤 실행한다."""
import subprocess
import urllib.parse

from harness import Step
from data1 import login

W = "/home/jiho/yona-convert/yona-diff-work/git-tester-rich1-rich"
B = "/tester/rich1"


def g(*a):
    return subprocess.run(["git", "-C", W, *a], capture_output=True, text=True, check=True).stdout


def q(path):
    return urllib.parse.quote(path, safe="/")


def steps():
    s = [login("tester"), login("third")]
    files = [p for p in g("ls-tree", "-r", "-z", "--name-only", "master").split("\0") if p]
    dirs = sorted({"/".join(p.split("/")[:i]) for p in files for i in range(1, len(p.split("/")))})
    many = [p for p in files if p.startswith("many/")]
    sample_files = [p for p in files if not p.startswith("many/")] + many[:4] + many[-2:]
    shas = g("log", "--format=%H", "master").split()
    special = {}
    for sha in shas:
        msg = g("log", "-1", "--format=%s", sha).strip()
        special[msg] = sha
    pick = [special[k] for k in ("초기 커밋: 다양한 파일", "링크와 서브모듈", "모드만 바꿈", "텍스트 수정", "이름 바꾸기", "복사", "삭제", "되살림", "공백만 바꿈 (탭을 공백으로)", "줄바꿈만 바꿈 (CRLF -> LF)", "이진 파일 바꿈",
                                                       "많은 줄 중 일부 바꿈", "파일 160개 한꺼번에 수정", "기능 병합", "문어 병합", "순서 커밋 1", "순서 커밋 120") if k in special]
    pick += [sha for sha in shas if g("log", "-1", "--format=%s", sha).strip() == "" or g("log", "-1", "--format=%s", sha).startswith("아주 긴")]
    pick += shas[:6] + shas[-3:]
    pick = list(dict.fromkeys(pick))
    tags = g("tag").split()
    branches = [b.strip() for b in g("branch", "--format=%(refname:short)").split("\n") if b.strip()]
    for actor in ("anon", "tester"):
        a = actor
        s += [Step(a, "GET", B, note=f"홈 [{a}]"), Step(a, "GET", B + "/code", note=f"코드 [{a}]"), Step(a, "GET", B + "/code/master", note=f"코드 master [{a}]"),
              Step(a, "GET", B + "/branches", note=f"가지 [{a}]"), Step(a, "GET", B + "/commits", note=f"이력 [{a}]"), Step(a, "GET", B + "/commits?page=2", note=f"이력 2쪽 [{a}]"),
              Step(a, "GET", B + "/commits?page=6", note=f"이력 6쪽 [{a}]"), Step(a, "GET", B + "/commits?page=99", note=f"이력 99쪽 [{a}]"), Step(a, "GET", B + "/commits/master/", note=f"이력 master [{a}]"),
              Step(a, "GET", B + "/code/master/download", note=f"내려받기 master [{a}]")]
    for d in dirs[:60]:
        s.append(Step("anon", "GET", f"{B}/code/master/{q(d)}", note=f"폴더 {d}"))
        s.append(Step("anon", "GET", f"{B}/code/!/{q(d)}", note=f"메타 폴더 {d}"))
    for f in sample_files:
        s.append(Step("anon", "GET", f"{B}/code/master/{q(f)}", note=f"파일 {f}"))
        s.append(Step("anon", "GET", f"{B}/rawcode/master/{q(f)}", note=f"원본 {f}"))
        s.append(Step("anon", "GET", f"{B}/code/master/!/{q(f)}", note=f"메타 {f}"))
        if f.startswith("img/"):
            s.append(Step("anon", "GET", f"{B}/image/master/{q(f)}", note=f"그림 {f}"))
        if f.startswith(("src/", "docs/", "text/utf8", "bin/random")):
            s.append(Step("anon", "GET", f"{B}/commits/master/{q(f)}", note=f"파일 이력 {f}"))
    s += [Step("anon", "GET", f"{B}/code/master/nope/none.txt", note="없는 파일"), Step("anon", "GET", f"{B}/rawcode/master/nope", note="없는 원본"), Step("anon", "GET", f"{B}/image/master/text/utf8.txt", note="그림 아닌 파일의 그림 보기"),
          Step("anon", "GET", f"{B}/rawcode/master/dangling-link", note="끊어진 링크 원본"), Step("anon", "GET", f"{B}/code/master/loop-a", note="순환 링크")]
    for sha in pick:
        s.append(Step("anon", "GET", f"{B}/commit/{sha}", note=f"커밋 {sha[:8]}"))
        s.append(Step("anon", "GET", f"{B}/commit/{sha[:10]}", note=f"커밋 짧은 {sha[:10]}"))
        s.append(Step("anon", "GET", f"{B}/code/{sha}", note=f"해시로 코드 {sha[:8]}"))
    for t in tags[:12] + ["tree-tag", "blob-tag", "nested-tag"]:
        s.append(Step("anon", "GET", f"{B}/code/{q(t)}", note=f"태그 코드 {t}"))
        s.append(Step("anon", "GET", f"{B}/commits/{q(t)}/", note=f"태그 이력 {t}"))
    for b in branches[:10] + ["feature/한글-가지"]:
        s.append(Step("anon", "GET", f"{B}/code/{q(b)}", note=f"가지 코드 {b}"))
        s.append(Step("anon", "GET", f"{B}/commits/{q(b)}/", note=f"가지 이력 {b}"))
    for pair in ("master..feature/한글-가지", "conflict-a..conflict-b", "v1.0..master", "master..v1.0", f"{shas[-1][:10]}..{shas[0][:10]}", "master..master", "nope..master", "light1..bulk-tag-30"):
        s.append(Step("anon", "GET", f"{B}/compare/{q(pair)}", note=f"비교 {pair}"))
    return s
