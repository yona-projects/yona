#!/bin/bash
# yona-common.js / yona-layout.js / yona-lib.js 재빌드 스크립트.
#
# legacy `minify-js.sh`를 yona(Spring Boot, src/main/resources/static/ 레이아웃)에 맞게
# 옮긴 것 - 동일한 Closure Compiler(v20160208, legacy public/compiler.jar)로 동일한
# SIMPLE 최적화를 적용해 legacy와 압축 방식을 맞춘다.
#
# legacy 재료 목록에서 아래 벤더 파일들은 뺐다 - 전부 실사용처가 $yona.* 네이티브 구현이나
# fetch/History API 기반 자체 구현으로 대체됐거나, 애초에 아무 데서도 호출되지 않는 죽은
# 코드였음을 확인했다. 다시 추가하기 전에 대체 구현이 여전히 그 역할을 하고 있는지 먼저
# 확인할 것 - 죽은 코드를 다시 번들에 넣지 않기 위한 의도적 제외다:
#   - common/yona.Mention.js (CM6 기반 mention.ts로 대체, 소스 자체가 삭제됨)
#   - lib/jquery/jquery.tmpl.js, jquery.pjax.js, jquery.form.js, jquery.validate.js,
#     jquery.requestAs.js, jquery.search.js, jquery.zclip.min.js,
#     jquery.placeholder.min.js
#   - jquery-3.3.1.js, jquery.browser.js, bootstrap.js (dropdown/button/alert 등의
#     DATA-API 델리게이트를 common/yona.Common.js에 vanilla로 재구현해 대체)
#
# lib/xss.js는 유지한다 - 한 번 "서버사이드 전용이라 불필요"로 오판해 뺐다가, yona.Common.js의
# xssClean()이 xss.js가 노출하는 전역 Filter 클래스를 직접 호출하고 yona.Markdown.js가 그걸
# 쓴다는 걸 놓쳐 "ReferenceError: Filter is not defined"가 실제로 재현돼 원복했다. 이 소스
# 파일은 <script src>로 개별 로드되지 않고 이 번들 재료로만 쓰인다.
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
  --js "$JS/common/yona.Common.js" \
  --js_output_file "$JS/yona-layout.js"

echo "== yona-common.js =="
java -jar "$COMPILER" \
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
  --js "$JS/common/yona.ui.Switch.js" \
  --js "$JS/common/yona.OriginalMessage.js" \
  --js "$JS/service/yona.temporarySaveHandler.js" \
  --js_output_file "$JS/yona-lib.js"

echo "완료: yona-layout.js / yona-common.js / yona-lib.js 재생성됨."
