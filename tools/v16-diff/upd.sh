#!/bin/bash
# 업데이트 확인 시험용 로컬 Git 저장소를 만든다(태그만 쓰므로 커밋은 하나면 된다).
#   upd.sh init    update-none.git(태그 없음)과 update-tags.git(여러 태그)을 만든다
#   upd.sh on      태그 있는 저장소를 보게 하고 확인을 켠다(servers.sh restore 로 서버를 다시 띄워야 적용된다)
#   upd.sh off     기본(태그 없는 저장소, 확인 끔)으로 되돌린다
#   upd.sh tag <이름>  update-tags.git에 태그를 하나 더 만든다
WORK=/home/jiho/yona-convert/yona-diff-work
case "${1:-}" in
  init)
    rm -rf "$WORK/update-src" "$WORK/update-none.git" "$WORK/update-tags.git"
    git init -q "$WORK/update-src" && cd "$WORK/update-src" && git -c user.name=t -c user.email=t@example.com commit -q --allow-empty -m init
    git clone -q --bare "$WORK/update-src" "$WORK/update-none.git"
    git clone -q --bare "$WORK/update-src" "$WORK/update-tags.git"
    for t in v1.15.0 v1.16.0 v1.16.1 v1.17.0 v2.0.0-rc1 vfoo 1.99.0 v1.9.9; do git -C "$WORK/update-tags.git" tag "$t"; done
    echo "만듦: $(git -C "$WORK/update-tags.git" tag | tr '\n' ' ')" ;;
  on) touch "$WORK/upd.on" ;;
  off) rm -f "$WORK/upd.on" ;;
  tag) git -C "$WORK/update-tags.git" tag "$2" ;;
  *) echo "사용법: $0 init|on|off|tag <이름>"; exit 1 ;;
esac
