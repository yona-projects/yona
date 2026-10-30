#!/bin/bash
# JS 번들(yona-layout.js, yona-common.js, yona-lib.js)을 다시 만든다. v1.6 `minify-js.sh`를 새 저장소 구조에 맞춘 것이다.
# 옛 스크립트의 입력 목록과 순서를 그대로 쓰되, Bootstrap 3 전환(X-38b, D-53)으로 `bootstrap-typeahead.js`를 `bootstrap.js` 바로 뒤에 더했다
# (Bootstrap 3부터 typeahead가 빠졌고 이 사이트는 2.3.1의 typeahead 플러그인을 계속 쓴다, `decisions.md` A-284).
# Bootstrap 5(X-38d)의 번들은 ES2015 이상이라 이 컴파일러로 합치지 않고, 이미 압축된 `bootstrap.bundle.min.js`를 템플릿이 따로 불러온다.
# 사용: build-tools/minify-js.sh   (저장소 루트에서 실행, Java 필요)
set -euo pipefail
cd "$(dirname "$0")/.."
ASSETS="src/main/resources/static/assets"
COMPILER="$ASSETS/compiler.jar"
JS="$ASSETS/javascripts"
BS="$ASSETS/bootstrap/js"

java -jar "$COMPILER" --js $JS/lib/nprogress/nprogress.js $JS/lib/jquery/jquery-3.3.1.js $JS/lib/jquery/jquery.browser.js $JS/lib/jquery/jquery.pjax.js $JS/common/yobi.Common.js --js_output_file $JS/yona-layout.js

java -jar "$COMPILER" --js $JS/lib/jquery/jquery.tmpl.js $JS/lib/jquery/jquery.form.js $JS/lib/jquery/jquery.validate.js $JS/lib/jquery/jquery.requestAs.js $JS/lib/jquery/jquery.search.js $JS/lib/jquery/jquery.zclip.min.js $JS/lib/jquery/jquery.placeholder.min.js $BS/bootstrap-typeahead.js $JS/lib/rgbcolor.js $JS/lib/humanize.js $JS/lib/validate.js $JS/lib/xss.js $JS/lib/clipboard.js --js_output_file $JS/yona-common.js

java -jar "$COMPILER" --js $JS/common/yobi.Attachments.js $JS/common/yobi.Files.js $JS/common/yobi.Mention.js $JS/common/yobi.Markdown.js $JS/common/yobi.Pagination.js $JS/common/yobi.ShortcutKey.js $JS/common/yobi.ui.Dropdown.js $JS/common/yobi.ui.Typeahead.js $JS/common/yobi.ui.Dialog.js $JS/common/yobi.ui.Toast.js $JS/common/yobi.ui.Tabs.js $JS/common/yobi.OriginalMessage.js $JS/service/yona.temporarySaveHandler.js --js_output_file $JS/yona-lib.js
