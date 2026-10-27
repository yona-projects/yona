#!/bin/bash
# gitprep.sh가 만든 저장소에 같은 시각의 커밋을 하나 더해 두 서버(19200, 19300)에 올린다. 푸시 알림 메일을 시험할 때 쓴다.
#   gitpush.sh <프로젝트 소유자> <프로젝트 이름> <올리는 사용자>
set -eu
OWNER=$1; NAME=$2; USER=$3
W=/home/jiho/yona-convert/yona-diff-work/git-$OWNER-$NAME-base
cd "$W"
export GIT_AUTHOR_NAME="Tester" GIT_AUTHOR_EMAIL="tester@example.com" GIT_COMMITTER_NAME="Tester" GIT_COMMITTER_EMAIL="tester@example.com"
export GIT_AUTHOR_DATE="2026-03-01T09:00:00+0900" GIT_COMMITTER_DATE="2026-03-01T09:00:00+0900"
printf '푸시 시험 %s\n' "$(date +%s)" > "push-test.txt"
git add -A; git commit -q -m "푸시 시험 커밋"
for P in 19200 19300; do
  git push -q "http://$USER:pass1234@127.0.0.1:$P/$OWNER/$NAME" master 2>&1 | tail -1 || true
done
