#!/bin/bash
# JS 번들(yona-layout.js, yona-common.js, yona-lib.js)을 다시 만든다. v1.6 `minify-js.sh`(Closure Compiler)를 대신한다(D-92).
# 입력 목록과 순서는 `build-tools/js-bundle/bundles.mjs`에 있고 v1.6과 같다. 합치는 방식과 설명은 `vite.config.mjs`를 본다.
# 사용: build-tools/minify-js.sh   (Node 20 이상 필요, 처음에는 npm ci로 도구를 받는다)
set -euo pipefail
cd "$(dirname "$0")/js-bundle"
npm ci --no-audit --no-fund
npm run build
