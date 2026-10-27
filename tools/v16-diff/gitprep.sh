#!/bin/bash
# 같은 내용·같은 시각의 저장소를 만들어 두 서버(v1.6: 19200, v2: 19300)의 같은 프로젝트에 올린다.
# 커밋 해시가 두 서버에서 같으므로 코드·커밋·비교 화면을 그대로 대조할 수 있다.
#   gitprep.sh <프로젝트 소유자> <프로젝트 이름> <올리는 사용자> [변형]
set -eu
OWNER=$1; NAME=$2; USER=$3; VARIANT=${4:-base}
W=/home/jiho/yona-convert/yona-diff-work/git-$OWNER-$NAME-$VARIANT
rm -rf "$W"; mkdir -p "$W"; cd "$W"
export GIT_AUTHOR_NAME="Tester" GIT_AUTHOR_EMAIL="tester@example.com" GIT_COMMITTER_NAME="Tester" GIT_COMMITTER_EMAIL="tester@example.com"
commit() { # 날짜 메시지
  export GIT_AUTHOR_DATE="$1" GIT_COMMITTER_DATE="$1"; git add -A; git commit -q -m "$2"
}
git init -q -b master .
git config core.autocrlf false
if [ "$VARIANT" = "names" ]; then
  # 이름이 까다로운 파일들: 공백, 특수문자, 한글, 퍼센트, 더하기, 해시, 물음표(리눅스에서 가능한 것)
  mkdir -p "dir with space" "한글 디렉터리/하위" "a+b" "100%" "x#y" "q?r" "amp&dir" "quote'dir" 'dq"dir' "semi;colon" "brace{a}" "paren(1)" "bracket[1]" "at@sign" "eq=sign" "tilde~" "comma,dir" "pipe|dir" "caret^dir" "back\`tick"
  for d in "dir with space" "한글 디렉터리/하위" "a+b" "100%" "x#y" "q?r" "amp&dir" "quote'dir" 'dq"dir' "semi;colon" "brace{a}" "paren(1)" "bracket[1]" "at@sign" "eq=sign" "tilde~" "comma,dir" "pipe|dir" "caret^dir"; do
    printf 'content of %s\n' "$d" > "$d/file name.txt"
    printf 'second\n' > "$d/한글 파일.md"
  done
  printf 'root\n' > "공백 있는 파일.txt"
  printf '# readme\n' > README.md
  commit "2026-02-01T09:00:00+0900" "이름이 까다로운 파일들"
  git tag -a "v 1" -m "tag with space" 2>/dev/null || true
  git branch "feature/한글-브랜치"
  git branch "with space" 2>/dev/null || true
  git branch "plus+branch"
  for P in "19200" "19300"; do
    URL="http://$USER:pass1234@127.0.0.1:$P/$OWNER/$NAME"
    git push -q "$URL" --all 2>&1 | tail -1 || true
    git push -q "$URL" --tags 2>&1 | tail -1 || true
  done
  git log --format='%H %s' | head -3
  exit 0
fi
printf '# 프로젝트 소개\n\n처음 쓰는 README 입니다.\n\n- 항목1\n- 항목2\n' > README.md
mkdir -p src/main docs "한글 폴더"
printf 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("hello");\n    }\n}\n' > src/main/Main.java
printf '문서 내용\n두번째 줄\n' > "docs/설명.txt"
printf '한글 폴더 안의 파일\n' > "한글 폴더/파일.txt"
head -c 2048 /dev/urandom | od -An -tx1 > /dev/null
python3 - <<'PY'
import struct,zlib
def png(w,h):
    raw=b''.join(b'\x00'+bytes([(x*5)%256,(y*7)%256,128])*1 for y in range(h) for x in [0])  # 의미 없는 작은 데이터
    raw=b''.join(b'\x00'+b''.join(bytes([(x*5)%256,(y*7)%256,128]) for x in range(w)) for y in range(h))
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
open('logo.png','wb').write(png(16,16))
PY
commit "2026-01-01T09:00:00+0900" "첫 커밋: 기본 파일"
printf '# 프로젝트 소개\n\n처음 쓰는 README 입니다.\n\n- 항목1\n- 항목2\n- 항목3\n' > README.md
printf 'public class Util {\n    static int add(int a, int b) { return a + b; }\n}\n' > src/main/Util.java
commit "2026-01-02T10:30:00+0900" "README 항목 추가, Util 추가"
git checkout -q -b dev
printf 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("hello dev");\n        System.out.println(Util.add(1, 2));\n    }\n}\n' > src/main/Main.java
printf '개발 브랜치 문서\n' > docs/dev.txt
commit "2026-01-03T11:00:00+0900" "dev: Main 수정 (이슈 #1 참조)"
git checkout -q master
git rm -q -r "한글 폴더"
printf 'a,b,c\n1,2,3\n' > data.csv
commit "2026-01-04T12:00:00+0900" "한글 폴더 삭제, data.csv 추가"
git merge -q --no-ff -m "dev 병합" dev 2>/dev/null || true
export GIT_AUTHOR_DATE="2026-01-05T13:00:00+0900" GIT_COMMITTER_DATE="2026-01-05T13:00:00+0900"
git tag -a v1.0 -m "첫 릴리스" 2>/dev/null || true
git tag light-tag
for P in "19200" "19300"; do
  URL="http://$USER:pass1234@127.0.0.1:$P/$OWNER/$NAME"
  git push -q "$URL" master dev --tags 2>&1 | tail -2 || true
done
git log --format='%H %s' | head -8
