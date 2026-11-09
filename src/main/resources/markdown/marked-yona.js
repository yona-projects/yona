/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/

/**
 * marked(공식 배포 파일 `marked.js`) 위에 얹는 Yona의 사용자 지정이다. v1.6은 marked.js 파일을 직접 고쳐 썼고(5곳),
 * 공식 확장 지점(`marked.use`와 `Slugger`)으로 옮겨 공식 파일을 그대로 둔다. 서버(Nashorn)와 브라우저가 같은 파일을 쓰므로
 * ES5 문법만 쓴다. 불러오는 순서는 `marked.js` 다음이다.
 *
 * 1. 제목 `id`의 접두어(`headerPrefix`)는 `yb-header-`이다.
 * 2. 제목은 `headerIds` 옵션과 상관없이 항상 `id`를 달고 끝에 `<a href="#id" class="head-anchor">#</a>`를 붙인다.
 * 3. 작업 목록 항목(`<li>` 안에 `<input`이 있는 것)에 `task-list-item`, 그 체크박스에 `task-list-item-checkbox` 클래스를 붙인다.
 * 4. 작업 목록 체크박스는 `disabled`가 없다(`yona.Tasklist.js`가 눌러서 바꾼다).
 * 5. 제목 슬러그는 구두점을 지운 뒤 영문, 숫자, 밑줄, `|`, 한글 외의 문자를 `-`로 바꾼다.
 */
(function (marked) {
    'use strict';

    marked.setOptions({headerPrefix: 'yb-header-'});

    marked.use({
        renderer: {
            heading: function (text, level, raw, slugger) {
                var id = this.options.headerPrefix + slugger.slug(raw);
                return '<h' + level + ' id="' + id + '">' + text + '<a href="#' + id + '" class="head-anchor">#</a>' + '</h' + level + '>\n';
            },
            listitem: function (text) {
                var html = '<li>' + text + '</li>\n';

                if (html.indexOf("<input") !== -1 && html.indexOf("task-list-item") === -1) {
                    html = html.replace("<input ", "<input class='task-list-item-checkbox' ");
                    html = html.replace("<li>", "<li class='task-list-item'>");
                }

                return html;
            },
            checkbox: function (checked) {
                return '<input ' + (checked ? 'checked="" ' : '') + 'type="checkbox"' + (this.options.xhtml ? ' /' : '') + '> ';
            }
        }
    });

    marked.Slugger.prototype.serialize = function (value) {
        return value.toLowerCase().trim()
            // remove html tags
            .replace(/<[!\/a-z].*?>/ig, '')
            // remove unwanted chars
            .replace(/[ -⁯⸀-⹿\\'!"#$%&()*+,./:;<=>?@[\]^`{|}~]/g, '').replace(/[^\w|ㄱ-ㅎ|ㅏ-ㅣ|가-힣]+/g, '-')
            .replace(/\s/g, '-');
    };
})(marked);
