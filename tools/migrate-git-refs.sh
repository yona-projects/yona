#!/bin/bash
# Git 저장소의 PR 숨은 참조 이름을 `refs/yobi/...`에서 `refs/yona/...`로 옮긴다(D-93).
#
# v1.6 저장소에는 풀 리퀘스트가 `refs/yobi/pull/<번호>/head`, `.../merged`를 만들어 두었다. Yona는 `refs/yona/`를 쓰므로
# v1.6에서 이어받은 데이터 디렉터리는 서버를 새 버전으로 띄우기 전에 이 스크립트를 한 번 돌려야 한다.
# 가리키는 커밋은 그대로이고 이름만 바뀐다. 이미 옮겼거나 옛 참조가 없는 저장소는 건드리지 않으므로 몇 번을 돌려도 같은 결과이다.
#
# 사용: tools/migrate-git-refs.sh [--dry-run] <데이터 디렉터리>
#   --dry-run  옮길 참조를 보여 주기만 하고 바꾸지 않는다.
# 서버를 멈춘 상태에서 돌리기를 권한다. git 명령이 필요하다. 한 저장소가 실패해도 나머지는 계속하고, 하나라도 실패하면 종료 코드 1이다.
set -u

dry=0
if [ "${1:-}" = "--dry-run" ]; then dry=1; shift; fi
data="${1:-}"
if [ -z "$data" ]; then
  echo "사용: $0 [--dry-run] <데이터 디렉터리>" >&2
  exit 2
fi
root="$data/repo/git"
if [ ! -d "$root" ]; then
  echo "저장소 디렉터리가 없다: $root" >&2
  exit 2
fi

total=0
failed=0
for repo in "$root"/*/*.git; do
  [ -d "$repo" ] || continue
  name="${repo#"$root"/}"
  # 이름은 refname에 공백이 없으므로 "<objectname> <refname>" 줄로 읽는다.
  mapfile -t refs < <(git --git-dir="$repo" for-each-ref --format='%(objectname) %(refname)' refs/yobi/ 2>/dev/null)
  [ "${#refs[@]}" -gt 0 ] || continue
  if [ "$dry" -eq 1 ]; then
    for line in "${refs[@]}"; do echo "[dry-run] $name: ${line#* } -> refs/yona/${line#* refs/yobi/}"; done
    total=$((total + ${#refs[@]}))
    continue
  fi
  # 한 저장소의 참조 이동은 하나의 트랜잭션으로 한다(모두 옮기거나 모두 그대로 둔다).
  {
    echo start
    for line in "${refs[@]}"; do
      oid="${line%% *}"; ref="${line#* }"; new="refs/yona/${ref#refs/yobi/}"
      echo "update $new $oid"
      echo "delete $ref $oid"
    done
    echo prepare
    echo commit
  } | git --git-dir="$repo" update-ref --stdin
  if [ $? -eq 0 ]; then
    echo "$name: 참조 ${#refs[@]}개를 옮겼다"
    total=$((total + ${#refs[@]}))
  else
    echo "$name: 옮기지 못했다" >&2
    failed=$((failed + 1))
  fi
done

echo "합계: 참조 ${total}개$([ "$dry" -eq 1 ] && echo ' (dry-run, 바뀐 것 없음)'), 실패한 저장소 ${failed}곳"
[ "$failed" -eq 0 ]
