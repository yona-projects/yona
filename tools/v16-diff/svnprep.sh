#!/bin/bash
# 같은 내용의 Subversion 이력을 두 서버(v1.6: 19200, v2: 19300)의 같은 프로젝트에 커밋한다. 리비전 번호는 서버마다 같게 나온다.
#   svnprep.sh <프로젝트 소유자> <프로젝트 이름> <올리는 사용자>
set -eu
OWNER=$1; NAME=$2; USER=$3
for P in 19200 19300; do
  W=/home/jiho/yona-convert/yona-diff-work/svn-$OWNER-$NAME-$P
  rm -rf "$W"; mkdir -p "$W"
  URL="http://127.0.0.1:$P/svn/$OWNER/$NAME"
  S="svn --non-interactive --no-auth-cache --username $USER --password pass1234 --trust-server-cert"
  $S checkout -q "$URL" "$W/wc"
  cd "$W/wc"
  mkdir -p trunk/src trunk/docs "trunk/한글 폴더" branches tags
  printf '# SVN 프로젝트\n\n소개 글입니다.\n' > trunk/README.md
  printf 'public class Main {\n  public static void main(String[] a) {}\n}\n' > trunk/src/Main.java
  printf '문서 내용\n' > "trunk/docs/설명.txt"
  printf '한글 파일 내용\n' > "trunk/한글 폴더/파일 이름.txt"
  $S add -q --force trunk branches tags
  $S commit -q -m "첫 커밋: 기본 구조" >/dev/null
  $S update -q
  printf 'public class Main {\n  public static void main(String[] a) {\n    System.out.println("hi");\n  }\n}\n' > trunk/src/Main.java
  printf 'class Util {}\n' > trunk/src/Util.java
  $S add -q trunk/src/Util.java
  $S commit -q -m "Main 수정과 Util 추가" >/dev/null
  $S update -q
  $S copy -q trunk branches/dev
  $S commit -q -m "dev 브랜치 만들기" >/dev/null
  $S update -q
  printf 'dev only\n' > branches/dev/src/Dev.java
  $S add -q branches/dev/src/Dev.java
  $S commit -q -m "dev에서 Dev.java 추가" >/dev/null
  $S update -q
  $S move -q trunk/docs/설명.txt trunk/docs/guide.txt
  $S commit -q -m "문서 이름 바꾸기" >/dev/null
  $S update -q
  $S delete -q trunk/src/Util.java
  $S propset -q svn:ignore '*.log' trunk
  $S commit -q -m "Util 삭제와 속성 변경" >/dev/null
  $S update -q
  head -c 2048 /dev/zero | tr '\0' 'x' > trunk/data.bin
  printf '\x89PNG\r\n\x1a\n\0\0\0\rIHDR' > trunk/logo.png
  $S add -q trunk/data.bin trunk/logo.png
  $S propset -q svn:mime-type image/png trunk/logo.png
  $S commit -q -m "바이너리 추가" >/dev/null
  $S update -q
  $S copy -q trunk tags/v1
  $S commit -q -m "v1 태그" >/dev/null
  $S update -q
  cd - >/dev/null
  echo "$P: $($S info --show-item last-changed-revision "$URL")"
done
