#!/usr/bin/env bash
# highlight.js 11 코드 강조 팩을 공식 배포 파일(@highlightjs/cdn-assets)에서 만든다(D-119).
# 핵심 파일 `highlight.min.js`(공통 언어 포함)에, v1.6의 9.11 팩이 지원하던 언어 중 공통 언어에 없는 것의 공식 언어 파일을 이어 붙인다.
# 사용: build-tools/highlight-pack/build.sh  → src/main/resources/static/assets/javascripts/lib/highlight/highlight.pack.js 와 styles/default.css 를 쓴다.
set -euo pipefail
VERSION=11.12.0
# 공통 언어에 없어 따로 이어 붙이는 언어(9.11 팩의 언어 중 공통 11.12 팩에 없는 것. htmlbars는 handlebars, tex는 latex, cs는 csharp의 별칭이다)
EXTRA="haskell gradle basic arduino excel nginx accesslog llvm clojure haml matlab awk handlebars elm django apache clojure-repl latex http scala groovy dockerfile erlang dos powershell coffeescript dart elixir cmake asciidoc"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="$ROOT/src/main/resources/static/assets/javascripts/lib/highlight"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cd "$WORK"
npm pack "@highlightjs/cdn-assets@$VERSION" --silent >/dev/null
tar xzf highlightjs-cdn-assets-$VERSION.tgz
P=package
{
  cat "$P/highlight.min.js"
  for l in $EXTRA; do
    [ -f "$P/languages/$l.min.js" ] || { echo "언어 파일이 없다: $l" >&2; exit 1; }
    printf '\n'; cat "$P/languages/$l.min.js"
  done
  printf '\n'
} > "$OUT/highlight.pack.js"
cp "$P/styles/default.min.css" "$OUT/styles/default.css"
cp "$P/LICENSE" "$OUT/LICENSE"
echo "highlight.pack.js $(wc -c < "$OUT/highlight.pack.js")바이트"
