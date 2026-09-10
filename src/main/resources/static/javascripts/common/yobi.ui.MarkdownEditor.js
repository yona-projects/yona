/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
/**
 * yobi.ui.MarkdownEditor
 *
 * P3-46 #8-1(셸 교체): textarea + 수제 Edit/Preview 탭 UI(site/layout.html::markdownEditor의
 * ul.nav-tabs) -> EasyMDE 인스턴스로 교체. EasyMDE는 대상 <textarea>를 CodeMirror 기반 에디터로
 * 감싸면서 원본 textarea를 display:none으로 숨기고 자기 툴바/에디터 UI를 그 자리에 직접 삽입한다.
 *
 * 이번 단계 범위: 셸 교체 + yona 스타일 재스킨 + name/value/폼 제출 동기화 보장까지만.
 * - 미리보기 렌더링을 yobi.Markdown(marked/highlight.js) 재사용으로 연결하는 것(previewRender
 *   옵션)은 2단계 범위 — 지금은 EasyMDE 내장 렌더러가 미리보기를 그린다.
 * - @ 멘션(Tribute) 연동은 3단계 범위 — 지금은 Tribute가 이 CodeMirror 인스턴스에 붙지 않는다.
 * - 체크리스트/임시저장 지우기 버튼/알림수신자 목록 재구현은 4단계 범위.
 *
 * @requires easymde.min.js
 */
(function(ns){

    "use strict";

    var oNS = $yobi.createNamespace(ns);

    /**
     * yona 스타일 재스킨 툴바 구성.
     *
     * EasyMDE 내장 버튼 이름(문자열)을 그대로 쓰면 기본 FontAwesome 클래스가 적용되므로, yona의
     * .ybtn/yobicon-* 체계로 보이도록 모든 버튼을 커스텀 객체로 직접 정의한다(action은 EasyMDE가
     * 정적으로 노출하는 동일한 내장 함수를 그대로 재사용 - 동작 자체는 EasyMDE 기본값과 동일하고
     * 겉모양만 바꾼다).
     *
     * 구성: Bold/Italic | Heading/Quote | Checklist/List/Numbered list | Link/Image | Preview
     * - "?"(가이드) 버튼은 제외 — help/markdown 프래그먼트(마크다운 도움말)와 중복.
     * - side-by-side/fullscreen은 기존에 없던 기능이라 포함하지 않는다.
     * - Heading/Numbered list는 대응하는 yobicon 아이콘이 없어 짧은 텍스트 라벨로 대신한다.
     * - Checklist는 yobicon-list를 쓴다 - 기존(제거된) "체크리스트 추가" 버튼이 쓰던 아이콘과
     *   동일하다(help/markdown.html의 체크리스트 예시와도 맥락이 이어짐). 일반 목록(Unordered
     *   list)은 대신 yobicon-list-alt를 쓴다.
     */
    function _toolbar(){
        return [
            {
                name: "bold",
                action: EasyMDE.toggleBold,
                className: "ybtn ybtn-small yobicon-bold",
                title: "Bold"
            },
            {
                name: "italic",
                action: EasyMDE.toggleItalic,
                className: "ybtn ybtn-small yobicon-italic",
                title: "Italic"
            },
            "|",
            {
                name: "heading",
                action: EasyMDE.toggleHeadingSmaller,
                className: "ybtn ybtn-small markdown-editor-text-icon",
                text: "H",
                title: "Heading"
            },
            {
                name: "quote",
                action: EasyMDE.toggleBlockquote,
                className: "ybtn ybtn-small yobicon-quote",
                title: "Quote"
            },
            "|",
            {
                name: "check-list",
                action: EasyMDE.toggleCheckList,
                className: "ybtn ybtn-small yobicon-list",
                title: "Checklist"
            },
            {
                name: "unordered-list",
                action: EasyMDE.toggleUnorderedList,
                className: "ybtn ybtn-small yobicon-list-alt",
                title: "Generic list"
            },
            {
                name: "ordered-list",
                action: EasyMDE.toggleOrderedList,
                className: "ybtn ybtn-small markdown-editor-text-icon",
                text: "1.",
                title: "Numbered list"
            },
            "|",
            {
                name: "link",
                action: EasyMDE.drawLink,
                className: "ybtn ybtn-small yobicon-link",
                title: "Create link"
            },
            {
                name: "image",
                action: EasyMDE.drawImage,
                className: "ybtn ybtn-small yobicon-image",
                title: "Insert image"
            },
            "|",
            {
                name: "preview",
                action: EasyMDE.togglePreview,
                className: "ybtn ybtn-small yobicon-preview",
                title: "Toggle preview",
                noDisable: true
            }
        ];
    }

    /**
     * 대상 textarea 하나에 EasyMDE 인스턴스를 붙인다. 이미 붙어있으면 기존 인스턴스를 그대로
     * 돌려준다(중복 초기화 방지 - 예: 같은 영역이 다른 초기화 루프에 의해 두 번 순회되는 경우).
     *
     * @param {HTMLElement} elTextarea
     * @return {EasyMDE}
     */
    oNS.container[oNS.name] = function(elTextarea){
        var welTextarea = $(elTextarea);

        var existing = welTextarea.data("easymde");
        if(existing){
            return existing;
        }

        var easyMDE = new EasyMDE({
            "element": elTextarea,
            // 자체 호스팅 원칙 - 외부 CDN에서 아이콘 폰트/맞춤법 사전을 조용히 내려받는 EasyMDE
            // 기본 동작을 모두 끈다(우리는 어차피 yobicon으로 전부 재스킨하므로 FontAwesome 자체가
            // 불필요하고, spellChecker는 영어 전용 사전을 jsdelivr CDN에서 받아오는 기능이라
            // 다국어(ko/ja/en) 프로젝트 성격과도 맞지 않는다 - 원래 textarea도 이런 기능이 없었다).
            "autoDownloadFontAwesome": false,
            "spellChecker": false,
            // 기존에 없던 하단 상태바(글자수/커서위치 등)도 새 부가기능이라 끈다.
            "status": false,
            "toolbarTips": true,
            "minHeight": "300px",
            "toolbar": _toolbar(),
            // side-by-side/fullscreen 토글 버튼은 툴바에서 뺐지만 기본 단축키(Cmd/Ctrl+Alt+P,
            // F11)는 여전히 살아있어 우리가 보여주지 않기로 한 기능에 몰래 도달할 수 있다 - 명시적으로
            // 비활성화.
            "shortcuts": {
                "toggleSideBySide": null,
                "toggleFullScreen": null
            }
            // TODO(2단계): previewRender 옵션을 yobi.Markdown 재사용 렌더러로 연결 — 지금은
            // EasyMDE 내장 렌더러가 미리보기를 그린다(P3-46 8번 작업 단계 참고).
        });

        // 원본(숨겨진) textarea.value를 계속 동기화한다 - 이 프로젝트의 폼 제출(jQuery Form
        // Plugin ajaxSubmit/raw $.ajax 핸들러들)은 전부 textarea의 실제 DOM value를 읽으므로
        // 이게 없으면 사용자가 입력한 내용이 서버로 전달되지 않는다(CodeMirror.fromTextArea는
        // EasyMDE의 forceSync 옵션 없이는 폼 제출 시점에도 자동으로 원본 textarea에 값을 되쓰지
        // 않는다 - 직접 확인).
        //
        // 추가로 원본 textarea에 네이티브 input/keyup 이벤트를 쏴서 이 textarea를 직접 구독하는
        // 기존 jQuery 핸들러에도 값이 바뀌었다는 신호를 보낸다. 이 중 임시저장(초안 자동저장 -
        // yona.temporarySaveHandler.js의 $textarea.on('keyup', ...))은 이 화면들에서 실제로
        // 살아있는 기능이라(P3-46 8번 항목 0단계 조사 결과) keyup을 재발행해 계속 동작하게
        // 유지한다. 다만 이걸로 전부 되살아나는 건 아니다 - 체크리스트/알림수신자(4단계)나
        // 멘션(Tribute, 3단계)처럼 CodeMirror 레벨에서 직접 재배선해야 하는 것들은 이 신호만으로
        // 해결되지 않는다 - 최종 보고 "범위 밖 발견" 참고.
        easyMDE.codemirror.on("change", function(){
            easyMDE.element.value = easyMDE.value();
            easyMDE.element.dispatchEvent(new Event("input", {"bubbles": true}));
            easyMDE.element.dispatchEvent(new Event("keyup", {"bubbles": true}));
        });

        welTextarea.data("easymde", easyMDE);

        return easyMDE;
    };

    $(function(){
        $('[data-toggle="markdown-editor"] textarea').each(function(nIndex, elTextarea){
            yobi.ui.MarkdownEditor(elTextarea);
        });
    });
})("yobi.ui.MarkdownEditor");
