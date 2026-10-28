#!/bin/bash
# 조직 소유 한글 이름 프로젝트의 풀 리퀘스트 준비: 조직과 프로젝트를 만들고 기본 이력을 올린 뒤 포크하고, 포크에 브랜치를 올린다.
#   pr2.sh   data2 상태에서 실행한다. 끝나면 `PYTHONPATH=scenarios python3 harness.py run pr2`를 실행한다.
set -eu
cd "$(dirname "$0")"
python3 - <<'PYEOF'
import sys
sys.path.insert(0, ".")
from probe import session, BASES
for n, b in BASES.items():
    t = session(b, "tester")
    t.post(b + "/organizations/new", data={"name": "corp1", "descr": "회사 조직"}, allow_redirects=False)
    t.post(b + "/organizations/corp1/members", data={"loginId": "other"}, allow_redirects=False)
    t.post(b + "/projects", data={"owner": "corp1", "name": "한글저장소", "projectScope": "PUBLIC", "vcs": "GIT", "code": "true", "issue": "true", "pullRequest": "true", "review": "true", "milestone": "true", "board": "true"}, allow_redirects=False)
PYEOF
SRC=/home/jiho/yona-convert/yona-diff-work/git-tester-proj1-base
for P in 19200 19300; do git -C "$SRC" push -q "http://tester:pass1234@127.0.0.1:$P/corp1/%ED%95%9C%EA%B8%80%EC%A0%80%EC%9E%A5%EC%86%8C" master 2>&1 | tail -1 || true; done
PYTHONPATH=scenarios python3 harness.py run pr2a --out /dev/null >/dev/null 2>&1
W=/home/jiho/yona-convert/yona-diff-work/pr2-clone
rm -rf "$W"; mkdir -p "$W"; cd "$W"
export GIT_AUTHOR_NAME="Other" GIT_AUTHOR_EMAIL="other@example.com" GIT_COMMITTER_NAME="Other" GIT_COMMITTER_EMAIL="other@example.com"
commit() { export GIT_AUTHOR_DATE="$1" GIT_COMMITTER_DATE="$1"; git add -A; git commit -q -m "$2"; }
git clone -q "$SRC" work; cd work
git checkout -q -b feature master
printf 'public class Feature {\n    static String name() { return "한글"; }\n}\n' > src/main/Feature.java
mkdir -p "src/한글 폴더"; printf '한글 파일\n' > "src/한글 폴더/파일 이름.txt"
commit "2026-02-10T09:00:00+0900" "포크: 한글 파일 추가"
printf 'more\n' >> README.md
commit "2026-02-10T10:00:00+0900" "포크: README 한 줄 더"
git checkout -q -b conflict master
printf '충돌 내용\n' > README.md
commit "2026-02-10T11:00:00+0900" "포크: 충돌하는 README"
for P in 19200 19300; do
  git push -q "http://other:pass1234@127.0.0.1:$P/other/%ED%95%9C%EA%B8%80%ED%8F%AC%ED%81%AC" master feature conflict 2>&1 | tail -1 || true
done
git log --all --format='%H %s' | head -3
