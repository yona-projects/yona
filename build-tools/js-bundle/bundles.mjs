/**
 * Yona JS 번들 3개의 입력 목록. v1.6의 `minify-js.sh`와 같은 순서이다(전역 스크립트라 순서가 곧 의존 순서이다).
 * 경로는 `src/main/resources/static/assets` 기준이다.
 * v1.6과 다른 점은 `build-tools/minify-js.sh` 머리말을 따른다(`bootstrap-typeahead.js` 추가, jQuery 3.7.1, zclip 제거, 목록 영역 교체는 htmx와 `yona.Htmx.js`).
 */
const JS = "javascripts";
const BS = "bootstrap/js";

export const bundles = {
  "yona-layout.js": [
    `${JS}/lib/nprogress/nprogress.js`,
    `${JS}/lib/jquery/jquery-3.7.1.js`,
    `${JS}/lib/jquery/jquery.browser.js`,
    `${JS}/lib/htmx.js`,
    `${JS}/common/yona.Common.js`,
    `${JS}/common/yona.Htmx.js`,
  ],
  "yona-common.js": [
    `${JS}/lib/mustache.js`,
    `${JS}/lib/jquery/jquery.form.js`,
    `${JS}/lib/jquery/jquery.requestAs.js`,
    `${JS}/lib/jquery/jquery.search.js`,
    `${BS}/bootstrap-typeahead.js`,
    `${JS}/lib/rgbcolor.js`,
    `${JS}/lib/humanize.js`,
    `${JS}/lib/validate.js`,
    `${JS}/lib/xss.js`,
    `${JS}/lib/clipboard.js`,
  ],
  "yona-lib.js": [
    `${JS}/common/yona.Attachments.js`,
    `${JS}/common/yona.Files.js`,
    `${JS}/common/yona.Mention.js`,
    `${JS}/common/yona.Markdown.js`,
    `${JS}/common/yona.Pagination.js`,
    `${JS}/common/yona.ShortcutKey.js`,
    `${JS}/common/yona.ui.Dropdown.js`,
    `${JS}/common/yona.ui.Typeahead.js`,
    `${JS}/common/yona.ui.Dialog.js`,
    `${JS}/common/yona.ui.Toast.js`,
    `${JS}/common/yona.ui.Tabs.js`,
    `${JS}/common/yona.OriginalMessage.js`,
    `${JS}/service/yona.temporarySaveHandler.js`,
  ],
};
