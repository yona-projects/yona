#!/bin/bash
# yona-common.js / yona-layout.js / yona-lib.js 재빌드 스크립트.
#
# legacy `minify-js.sh`(Play Framework 시절, 저장소 루트에 있었음)를 yona(Spring Boot,
# src/main/resources/static/ 레이아웃)에 맞게 옮긴 것이다. 같은 Closure Compiler(v20160208,
# legacy public/compiler.jar를 그대로 복사)로 같은 SIMPLE 최적화를 적용해 legacy와 동일한
# 압축 방식을 유지한다.
#
# legacy 원본 재료 목록 대비 세 개를 뺐다(전부 이제 아무 소비자가 없음을 grep으로 확인 -
# 죽은 코드를 다시 얼려넣지 않기 위해 의도적으로 제외):
#   - yona-lib.js에서 common/yona.Mention.js: P3-46 5단계에서 CM6 기반 mention.ts로
#     완전히 대체되며 소스 자체가 삭제됨.
#   - yona-common.js에서 lib/jquery/jquery.tmpl.js: tmpl 카테고리 vanilla 전환으로
#     $(selector).tmpl(...)/$.tmpl(...) 실사용이 전부 $yona.tmpl()(순수 ${key} 치환,
#     yona.Common.js)로 대체됨 - 유일하게 남은 $.tmpl 참조는 yona.code.Diff.js인데
#     이 파일 자체가 어느 템플릿에서도 로드되지 않는 완전한 죽은 코드로 이미 확인됨.
#   - yona-layout.js에서 lib/jquery/jquery.pjax.js: pjax 카테고리 vanilla 전환으로
#     yona.issue.List.js의 $.pjax.click/submit 호출이 전부 fetch+DOMParser+
#     history.pushState 기반 자체 구현으로 대체됨.
#
# lib/xss.js는 legacy 재료 목록 그대로 유지한다 - 처음엔 "서버사이드 Markdown.java 전용이라
# 클라이언트 번들엔 필요 없다"고 오판해 뺐었는데, 실제로는 yona.Common.js의 xssClean()이
# `new Filter()`(xss.js가 노출하는 전역 클래스)를 직접 호출하고 yona.Markdown.js(클라이언트
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
  --js "$JS/common/yona.Common.js" \
  --js_output_file "$JS/yona-layout.js"

echo "== yona-common.js =="
java -jar "$COMPILER" \
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
  --js "$JS/common/yona.Attachments.js" \
  --js "$JS/common/yona.Files.js" \
  --js "$JS/common/yona.Markdown.js" \
  --js "$JS/common/yona.Pagination.js" \
  --js "$JS/common/yona.ShortcutKey.js" \
  --js "$JS/common/yona.ui.Dropdown.js" \
  --js "$JS/common/yona.ui.Typeahead.js" \
  --js "$JS/common/yona.ui.Dialog.js" \
  --js "$JS/common/yona.ui.Toast.js" \
  --js "$JS/common/yona.ui.Tabs.js" \
  --js "$JS/common/yona.OriginalMessage.js" \
  --js "$JS/service/yona.temporarySaveHandler.js" \
  --js_output_file "$JS/yona-lib.js"

echo "완료: yona-layout.js / yona-common.js / yona-lib.js 재생성됨."
