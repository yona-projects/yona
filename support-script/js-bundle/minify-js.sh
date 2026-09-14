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
# P3-70 라운드10에서 yona-common.js 재료 목록에서 6개를 추가로 뺐다(전부 실제 호출부를
# 네이티브로 대체하거나 완전한 죽은 코드임을 확인 - 아래 각 항목 참고):
#   - jquery.form.js: $yona.sendForm()을 fetch 기반으로 재작성(common/yona.Common.js) -
#     유일한 잔여 .ajaxForm() 호출부(common/yona.Files.js의 _uploadFileForm, XHR2 미지원
#     구형 브라우저 전용 legacy 폴백)는 htVar.bXHR2가 모든 현대 브라우저에서 항상 true라
#     실행되지 않는 죽은 분기로 확인됨(코드 대조 - 그대로 두어도 무해, 도달 불가).
#   - jquery.validate.js: 저장소 전체에서 실제 .validate( 호출부가 이 벤더 파일 자신 외에는
#     0건(grep 재확인) - 원래부터 죽은 로드였다.
#   - jquery.requestAs.js: $yona.requestAs()(common/yona.Common.js, DOMContentLoaded
#     전역 auto-init 포함)로 전면 대체 - 명시적 호출부(project.Delete.js/Comment.js/
#     issue.View.js/site/userList.html/site/projectList.html)와 DATA-API(전역
#     [data-request-method] 자동 초기화) 양쪽 모두 이식.
#   - jquery.search.js: $yona 내부의 네이티브 item-search 구현(focusin 캡처 위임)으로 대체 -
#     유일한 사용처인 organization/view.html의 "내 프로젝트만 보기" 검색창으로 실측 확인.
#   - jquery.zclip.min.js: service/yona.project.Home.js의 유일한 호출부가 "ClipboardJS
#     미지원 시 Flash 폴백" 분기라 모든 현대 브라우저(Chromium/Firefox/Safari 포함)에서
#     도달 불가능함을 코드 추적으로 확인(그대로 두어도 무해).
#   - jquery.placeholder.min.js: 플러그인 자체가 'placeholder' 네이티브 지원 감지 시
#     자기 자신을 no-op으로 만드는 코드임을 소스로 직접 확인(모든 현대 브라우저에서 항상
#     참) - site/layout.html의 호출 자체를 제거.
#
# jquery-3.3.1.js(및 jquery.browser.js)/bootstrap.js는 이번 라운드에서 의도적으로 남겨둔다 -
# 무리하게 제거하지 않는다는 원칙에 따라, 아래 세 가지 실사용 의존을 확인했다(전수 조사,
# 2026-09-14):
#   1. Bootstrap 2 자체의 전역 DATA-API 델리게이트 중 [data-toggle="dropdown"]/
#      [data-toggle="button"]가 여전히 수십 개 템플릿(project/header.html, organization/
#      project members.html, issue/list.html·partial_massupdate.html의 mass-update
#      드롭다운, code/svnDiff.html·code/diff.html·pullrequest/view.html의 watch 버튼,
#      site/layout.html의 GNB 검색 범위 선택 등)에서 실제로 열림/닫힘/토글에 쓰인다 -
#      라운드9는 .modal()/.tooltip()/.popover()/.affix() 4개만 명시적으로 다뤘고
#      dropdown/button DATA-API는 범위 밖이었다(대체 구현이 아직 없음).
#   2. site/layout.html의 로그아웃 링크 핸들러가 여전히 jQuery.post("/users/logout")를
#      직접 호출한다.
#   3. lib/yona-markdown-editor/yona-markdown-editor.min.js(수정 금지)의
#      exposeLegacyEasyMdeShim()이 window.jQuery(textarea).data("easymde", ...)로
#      EasyMDE 호환 shim을 노출하고, common/yona.Attachments.js·yona.
#      CommentAttachmentsUpdate.js가 window.jQuery.data(textarea, "easymde")로 그 값을
#      읽어 CodeMirror 공식 API로 파일 첨부 마크다운 링크를 삽입한다(라운드1이 확립한
#      "jQuery.data() 정적 접근자" 관례) - 양쪽 다 `if(window.jQuery)` 가드가 있어 jQuery가
#      없으면 예외 없이 조용히 스킵되지만, 그러면 CodeMirror 편집 중인 라이브 값이 아니라
#      비어있는 원본 textarea만 읽게 되는 실제 기능 회귀가 생긴다 - 쓰는 쪽 파일이 lib/라
#      수정 금지 대상이라 이 라운드에서 대체 메커니즘을 새로 설계하지 않는 한 고칠 수 없다.
# 위 세 가지 모두 해소하려면 (a) Bootstrap dropdown/button 플러그인의 vanilla 재구현
# (별도 라운드급 작업), (b) 로그아웃 핸들러의 fetch 전환, (c) easymde 브릿지를 jQuery
# 데이터 캐시가 아닌 다른 공유 채널(예: WeakMap)로 옮기는 lib 자산 재빌드가 각각 필요하다.
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
