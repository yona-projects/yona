#!/bin/bash
# 속성, 복사, 병합, 이름 바꾸기, 교체, 링크, 부호화, 큰 파일, 특수한 이름과 로그가 든 Subversion 이력을 두 서버의 같은 프로젝트에 커밋한다. 리비전 번호가 같게 나온다.
#   mksvnrich.sh <소유자> <프로젝트> <올리는 사용자>   (프로젝트는 SVN으로 이미 만들어져 있어야 한다)
set -u
OWNER=$1; NAME=$2; USER=$3
for P in 19200 19300; do
  W=/home/jiho/yona-convert/yona-diff-work/svnrich-$OWNER-$NAME-$P
  rm -rf "$W"; mkdir -p "$W"
  URL="http://127.0.0.1:$P/svn/$OWNER/$NAME"
  S="svn --non-interactive --no-auth-cache --username $USER --password pass1234 --trust-server-cert"
  SO="svn --non-interactive --no-auth-cache --username other --password pass1234 --trust-server-cert"
  $S checkout -q "$URL" "$W/wc" || { echo "checkout 실패 $P"; continue; }
  cd "$W/wc"
  c() { $S commit -q -m "$1" >/dev/null 2>&1 || echo "commit 실패: $1"; $S update -q >/dev/null 2>&1; }
  mkdir -p trunk/text trunk/img trunk/bin trunk/src branches tags
  printf '# SVN 풍부\n\n소개\n' > trunk/README.md
  python3 - <<'PY'
ko = "한글 텍스트입니다.\n둘째 줄\n"
open("trunk/text/utf8.txt","w",encoding="utf-8").write(ko)
open("trunk/text/euckr.txt","wb").write(ko.encode("euc-kr"))
open("trunk/text/sjis.txt","wb").write("日本語です。\n".encode("shift_jis"))
open("trunk/text/utf16.txt","wb").write(b"\xff\xfe"+ko.encode("utf-16-le"))
open("trunk/text/latin1.txt","wb").write("café\n".encode("latin-1"))
open("trunk/text/crlf.txt","wb").write(b"a\r\nb\r\nc\r\n")
open("trunk/text/empty.txt","w").write("")
open("trunk/text/long.txt","w").write("L"*60000+"\n")
open("trunk/bin/random.bin","wb").write(bytes((i*131+7)%256 for i in range(4096)))
import zlib, struct
def png(w,h):
    raw=b"".join(b"\x00"+b"".join(bytes([(x*5)%256,(y*7)%256,128]) for x in range(w)) for y in range(h))
    ch=lambda t,d: struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d)&0xffffffff)
    return b"\x89PNG\r\n\x1a\n"+ch(b"IHDR",struct.pack(">IIBBBBB",w,h,8,2,0,0,0))+ch(b"IDAT",zlib.compress(raw,9))+ch(b"IEND",b"")
open("trunk/img/a.png","wb").write(png(16,16))
open("trunk/img/b.svg","w").write('<svg xmlns="http://www.w3.org/2000/svg"><rect width="5" height="5"/></svg>\n')
open("trunk/src/Main.java","w").write("class Main {}\n")
PY
  $S add -q --force trunk branches tags; c "r1 구조와 파일 종류"
  mkdir -p trunk/many; for i in $(seq 1 60); do echo "file $i" > trunk/many/f$(printf %03d $i).txt; done
  $S add -q trunk/many; c "r2 파일 60개"
  $S propset -q svn:eol-style native trunk/text/crlf.txt
  printf '#!/bin/sh\necho run\n' > trunk/src/run.sh; $S add -q trunk/src/run.sh; $S propset -q svn:executable '*' trunk/src/run.sh
  $S propset -q svn:keywords 'Id Rev' trunk/src/Main.java
  $S propset -q svn:ignore $'*.log\n*.tmp\nbuild' trunk
  $S propset -q svn:externals 'ext http://example.org/ext' trunk/src
  $S propset -q "속성:이름" '한글 값 <b>&' trunk/README.md
  $S propset -q svn:mime-type text/plain trunk/bin/random.bin
  c "r3 속성 여러 가지"
  $S propset -q svn:needs-lock '*' trunk/text/utf8.txt; c "r4 속성만 바꾼 커밋"
  $S copy -q trunk branches/b1; c "r5 b1 가지"
  echo "b1 only" > branches/b1/src/B1.java; $S add -q branches/b1/src/B1.java; echo "changed in b1" >> branches/b1/README.md; c "r6 b1 작업"
  echo "changed in trunk" >> trunk/README.md; c "r7 trunk 작업"
  (cd trunk && $S merge -q ../branches/b1 . >/dev/null 2>&1; true); c "r8 b1을 trunk에 병합"
  $S move -q trunk/many trunk/numbers; c "r9 폴더 이름 바꾸기"
  $S delete -q trunk/numbers/f001.txt; c "r10 삭제"
  $S delete -q trunk/numbers/f002.txt; echo "replaced content" > trunk/numbers/f002.txt; $S add -q trunk/numbers/f002.txt 2>/dev/null; c "r11 교체"
  $S copy -q -r 2 "$URL/trunk/many/f003.txt" trunk/numbers/f003-from-r2.txt 2>/dev/null; c "r12 옛 리비전에서 복사"
  ln -s README.md trunk/link-to-readme; $S add -q trunk/link-to-readme; c "r13 심볼릭 링크"
  echo "other author" > trunk/by-other.txt; $S add -q trunk/by-other.txt; $SO commit -q -m "r14 다른 작성자" trunk/by-other.txt >/dev/null 2>&1; $S update -q
  echo "x" > trunk/empty-log.txt; $S add -q trunk/empty-log.txt; $S commit -q -m "" >/dev/null 2>&1; $S update -q
  echo "x" > trunk/ml-log.txt; $S add -q trunk/ml-log.txt
  $S commit -q -m $'r16 여러 줄 로그\n\n- 항목 `코드` **굵게**\n- <script>alert(1)</script> & &amp;\n\n한글 본문 #1 참조' >/dev/null 2>&1; $S update -q
  echo "x" > trunk/huge-log.txt; $S add -q trunk/huge-log.txt; $S commit -q -m "r17 $(python3 -c 'print("긴 로그 줄 "*600)')" >/dev/null 2>&1; $S update -q
  mkdir -p "trunk/special"; cd trunk/special
  for n in "with space.txt" "hash#name.txt" "percent%name.txt" "amp&name.txt" "q?name.txt" "plus+name.txt" "한글 이름.txt" "emoji😀.txt" "paren(1).txt" "at@name.txt" "semi;name.txt"; do echo "$n" > "$n"; $S add -q --force "./$n@" 2>/dev/null || $S add -q --force "$n@" 2>/dev/null; done
  python3 - <<'PY'
import unicodedata, os
open(unicodedata.normalize("NFD","한글분해.txt"),"w").write("nfd\n")
PY
  $S add -q --force . 2>/dev/null; cd ../..
  c "r18 특수한 이름"
  python3 -c "open('trunk/text/big.txt','w').write(('x'*99+'\n')*30000)"; $S add -q trunk/text/big.txt; c "r19 큰 텍스트 3MB"
  $S lock -q -m "작업 중" trunk/src/Main.java >/dev/null 2>&1
  for i in $(seq 1 45); do echo "revision $i" >> trunk/seq.txt; [ $i -eq 1 ] && $S add -q trunk/seq.txt; c "s$i 순서 커밋"; done
  $S copy -q trunk tags/v1; c "태그 v1"
  $S copy -q branches/b1 tags/v2; c "태그 v2"
  cd - >/dev/null
  echo "$P: $($S info --show-item last-changed-revision "$URL")"
done
