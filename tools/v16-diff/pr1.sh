#!/bin/bash
# 포크(other/proj1)를 복제해 브랜치에 커밋을 올린다. 두 서버에 같은 해시가 되도록 시각을 고정한다.
set -eu
W=/home/jiho/yona-convert/yona-diff-work/pr-clone
rm -rf "$W"; mkdir -p "$W"; cd "$W"
export GIT_AUTHOR_NAME="Other" GIT_AUTHOR_EMAIL="other@example.com" GIT_COMMITTER_NAME="Other" GIT_COMMITTER_EMAIL="other@example.com"
commit() { export GIT_AUTHOR_DATE="$1" GIT_COMMITTER_DATE="$1"; git add -A; git commit -q -m "$2"; }
# 원본과 같은 이력을 가진 로컬 저장소를 새로 만들어 두 포크에 각각 올린다(복제 주소가 서버마다 다르므로 한 저장소를 두 곳에 올린다)
SRC=/home/jiho/yona-convert/yona-diff-work/git-tester-proj1-base
git clone -q "$SRC" work; cd work
git checkout -q -b feature origin/master 2>/dev/null || git checkout -q -b feature master
printf 'public class Feature {\n    static String name() { return "feature"; }\n}\n' > src/main/Feature.java
printf '# 프로젝트 소개\n\n처음 쓰는 README 입니다.\n\n- 항목1\n- 항목2\n- 항목3\n- 포크에서 추가\n' > README.md
commit "2026-02-10T09:00:00+0900" "포크: Feature 추가와 README 수정"
printf 'more\n' >> README.md
commit "2026-02-10T10:00:00+0900" "포크: README 한 줄 더"
git checkout -q -b conflict master
printf '충돌 내용\n' > README.md
commit "2026-02-10T11:00:00+0900" "포크: 충돌하는 README"
for P in 19200 19300; do
  URL="http://other:pass1234@127.0.0.1:$P/other/proj1"
  git push -q "$URL" master feature conflict 2>&1 | tail -1 || true
done
git log --all --format='%H %s' | head -5
