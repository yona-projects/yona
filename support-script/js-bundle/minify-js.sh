#!/bin/bash
# yona-common.js / yona-layout.js / yona-lib.js 재빌드 스크립트.
#
# legacy `minify-js.sh`(Play Framework 시절, 저장소 루트에 있었음)를 yona(Spring Boot,
# src/main/resources/static/ 레이아웃)에 맞게 옮긴 것이다. 같은 Closure Compiler(v20160208,
# legacy public/compiler.jar를 그대로 복사)로 같은 SIMPLE 최적화를 적용해 legacy와 동일한
# 압축 방식을 유지한다.
#
# legacy 원본 재료 목록 대비 하나만 뺐다(이미 삭제된 소스 파일이라 재료로 넣을 수 없음 -
# 죽은 코드를 다시 얼려넣지 않기 위해 의도적으로 제외):
#   - yona-lib.js에서 common/yobi.Mention.js: P3-46 5단계에서 CM6 기반 mention.ts로
#     완전히 대체되며 소스 자체가 삭제됨.
#
# lib/xss.js는 legacy 재료 목록 그대로 유지한다 - 처음엔 "서버사이드 Markdown.java 전용이라
# 클라이언트 번들엔 필요 없다"고 오판해 뺐었는데, 실제로는 yobi.Common.js의 xssClean()이
# `new Filter()`(xss.js가 노출하는 전역 클래스)를 직접 호출하고 yobi.Markdown.js(클라이언트
# 사이드 마크다운 미리보기)가 그 xssClean()을 쓴다 - 뺐더니 Playwright로 실제
# "ReferenceError: Filter is not defined"가 재현돼 원복했다. lib/xss.js 소스 파일 자체는
# (어느 템플릿도 <script src>로 개별 로드하지 않으므로) 서 있기만 하고 이 번들 재료로만
# 쓰인다.
#
# 사용법(저장소 루트에서, Java 필요):
#   ./support-script/js-bundle/minify-js.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATIC="$REPO_ROOT/src/main/resources/static"
JS="$STATIC/javascripts"
COMPILER="$SCRIPT_DIR/compiler.jar"

echo "== yona-layout.js =="
java -jar "$COMPILER" \
  --js "$JS/lib/nprogress/nprogress.js" \
  --js "$JS/lib/jquery/jquery-3.3.1.js" \
  --js "$JS/lib/jquery/jquery.browser.js" \
  --js "$JS/lib/jquery/jquery.pjax.js" \
  --js "$JS/common/yobi.Common.js" \
  --js_output_file "$JS/yona-layout.js"

echo "== yona-common.js =="
java -jar "$COMPILER" \
  --js "$JS/lib/jquery/jquery.tmpl.js" \
  --js "$JS/lib/jquery/jquery.form.js" \
  --js "$JS/lib/jquery/jquery.validate.js" \
  --js "$JS/lib/jquery/jquery.requestAs.js" \
  --js "$JS/lib/jquery/jquery.search.js" \
  --js "$JS/lib/jquery/jquery.zclip.min.js" \
  --js "$JS/lib/jquery/jquery.placeholder.min.js" \
  --js "$STATIC/bootstrap/js/bootstrap.js" \
  --js "$JS/lib/rgbcolor.js" \
  --js "$JS/lib/humanize.js" \
  --js "$JS/lib/validate.js" \
  --js "$JS/lib/xss.js" \
  --js "$JS/lib/clipboard.js" \
  --js_output_file "$JS/yona-common.js"

echo "== yona-lib.js =="
java -jar "$COMPILER" \
  --js "$JS/common/yobi.Attachments.js" \
  --js "$JS/common/yobi.Files.js" \
  --js "$JS/common/yobi.Markdown.js" \
  --js "$JS/common/yobi.Pagination.js" \
  --js "$JS/common/yobi.ShortcutKey.js" \
  --js "$JS/common/yobi.ui.Dropdown.js" \
  --js "$JS/common/yobi.ui.Typeahead.js" \
  --js "$JS/common/yobi.ui.Dialog.js" \
  --js "$JS/common/yobi.ui.Toast.js" \
  --js "$JS/common/yobi.ui.Tabs.js" \
  --js "$JS/common/yobi.OriginalMessage.js" \
  --js "$JS/service/yona.temporarySaveHandler.js" \
  --js_output_file "$JS/yona-lib.js"

echo "완료: yona-layout.js / yona-common.js / yona-lib.js 재생성됨."
