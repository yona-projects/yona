/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

yobi.Markdown = (function(htOptions){

    var htVar = {};

    /**
     * initialize
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        htOptions = htOptions || {};

        _initVar(htOptions);
        _enableMarkdown(htOptions.aTarget);
    }

    /**
     * initialize variables
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions){
        htVar.sMarkdownRendererUrl = htOptions.sMarkdownRendererUrl;

        // P3-46 #6+7: marked.js(v0.7 전후) -> v18로 버전업하며 "highlight" 옵션 콜백 방식이
        // 없어져서(marked v5+에서 제거), 커스텀 renderer.code()로 이식한다(marked.use()는 전역
        // 싱글턴에 한 번 등록하면 이후 marked.parse() 호출마다 적용된다). 원본의
        // langPrefix:''(클래스에 접두어 없음, "hljs" 클래스도 원래 안 붙였음) 동작을 그대로
        // 재현: 언어가 있으면 <code class="lang">, 없으면 <code>만.
        htVar.htMarkedOption = {
            "gfm"     : true,
            "pedantic": false
        };

        marked.use({
            "renderer": {
                "code": function(oToken){
                    var sCode = oToken.text;
                    var sLang = oToken.lang ? oToken.lang.toLowerCase() : "";

                    if(sLang && typeof hljs !== "undefined" && hljs.getLanguage(sLang)){
                        try {
                            var sHighlighted = hljs.highlight(sCode, {language: sLang}).value;
                            return '<pre><code class="' + sLang + '">' + sHighlighted + '\n</code></pre>\n';
                        } catch(oException) {
                            console.log(oException.message);
                        }
                    }

                    return '<pre><code' + (sLang ? ' class="' + sLang + '"' : '') + '>' +
                        _escapeCode(sCode) + '\n</code></pre>\n';
                }
            }
        });
    }

    /**
     * HTML-escape raw code text before inserting into markup(marked의 기본 code renderer가
     * 하던 이스케이프를 커스텀 renderer가 대체하며 직접 수행).
     *
     * @param {String} sText
     * @return {String}
     */
    function _escapeCode(sText){
        return sText.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    }

    /**
     * Render as Markdown document
     *
     * @require marked.js
     * @require hljs.js
     * @param {String} sText
     * @return {String}
     */
    function _renderMarkdown(sText) {
        return $yobi.xssClean(marked.parse(sText, htVar.htMarkedOption));
    }

    /**
     * Replace auto-link
     * @param welTarget
     * @param sContentBody
     * @private
     */
    function _replaceAutoLink(welTarget, sContentBody){
        /**
         * If this ajax request is failed, do anything.
         * Because, the content body not replaced is shown to user before this request.
         */
        if(htVar.sMarkdownRendererUrl){
            _render(welTarget, sContentBody);
        }
    }

    function _render(welTarget, sContentBody) {
        var source = {
            "body": sContentBody,
            "breaks": (welTarget.hasClass('readme-body') ? false : true)
        };

        $.ajax(htVar.sMarkdownRendererUrl,{
            "type": "post",
            "contentType":"application/json; charset=utf-8",
            "data": JSON.stringify(source),
            "success": function(data){
                welTarget.html(data);
                $('pre code').each(function(i, block) {
                    hljs.highlightElement(block);
                });
            }
        });
    }

    /**
     * set Markdown Viewer
     *
     * @param {Wrapped Element} welTarget is not <textarea> or <input>
     */
    function _setViewer(welTarget){
        var sMarkdownText = welTarget.text();
        var sContentBody  = (sMarkdownText) ? _renderMarkdown(sMarkdownText) : welTarget.html();
        $('.markdown-loader').remove();
        welTarget.html(sContentBody).removeClass('markdown-before');
    }

    // Deprecated. so never call this method
    function _postMarkdownRender(){
        // Make first li font bold when multi-depth list is used
        var ul = $(".markdown-wrap > ul");
        ul.find("> li > ul").parent().closest('ul').css('font-weight', 'bold');  //ul > ul
        ul.find("> li > ol").parent().closest('ul').css('font-weight', 'bold');  //ul > ol

        var ol = $(".markdown-wrap > ol");
        ol.find("> li > ul").parent().closest('ol').css('font-weight', 'bold');  //ol > ul
        ol.find("> li > ol").parent().closest('ol').css('font-weight', 'bold');  //ol > ol
    }

    /**
     * set Markdown Editor
     *
     * @param {Wrapped Element} welTextarea
     */
    function _setEditor(welTextarea){
        var elContainer = welTextarea.parents('[data-toggle="markdown-editor"]').get(0);

        if(!elContainer){
            return false;
        }

        $(elContainer).on("click", 'a[data-mode="preview"]', function(weEvt){
            var welPreview = $(weEvt.delegateTarget).find("div.markdown-preview");
            var sContentBody = welTextarea.val();

            _replaceAutoLink(welPreview, sContentBody);

            welPreview.css({"min-height": welTextarea.height() + 'px'});
        });

        welTextarea.on("keydown.tabkey-event-handler", function(e) {
            var $this = $(this);
            if (e.shiftKey && e.key === 'Tab') {
                e.preventDefault();
                _untab($this.get(0));
            } else if ( e.key === 'Tab' ) {
                e.preventDefault();
                _tab($this.get(0));
            }
        });
    }

    /**
     * enableMarkdown on target elements
     *
     * @param {String} sQuery Selector string for targets
     */
    function _enableMarkdown(sQuery){
        var waTarget = $(sQuery || "[markdown]"); // TODO: markdown=true

        waTarget.each(function(nIndex, elTarget){
            _isEditableElement(elTarget) ? _setEditor($(elTarget)) : _setViewer($(elTarget));
        });
    }

    /**
     * Returns that specified element is editable
     *
     * @param {HTMLElement} elTarget
     * @return {Boolean}
     */
    function _isEditableElement(elTarget){
        var sTagName = elTarget.tagName.toUpperCase();
        return (sTagName === "TEXTAREA" || sTagName === "INPUT" || elTarget.contentEditable == "true");
    }

    // public interface
    return {
        "init"  : _init,
        "enableMarkdown": _enableMarkdown,
        "render" : _render
    };
})();
