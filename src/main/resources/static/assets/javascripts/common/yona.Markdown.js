/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

yona.Markdown = (function(htOptions){

    const htVar = {};

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

        // highlight.js 11에서 없어진 옛 PHP 별칭(v1.6의 9.11 팩은 php3부터 php6까지 알았다)을 다시 등록한다
        if(window.hljs && hljs.registerAliases && !hljs.getLanguage("php5")){
            hljs.registerAliases(["php3", "php4", "php5", "php6"], {"languageName": "php"});
        }

        htVar.htMarkedOption = {
            "gfm"       : true,
            "tables"    : true,
            "pedantic"  : false,
            "sanitize"  : false,
            "smartLists": true,
            "langPrefix": '',
            "highlight" : function(sCode, sLang) {
                if(sLang) {
                    try {
                        return hljs.highlight(sCode, {"language": sLang.toLowerCase()}).value;
                    } catch(oException) {
                        console.log(oException.message);
                    }
                }
            }
        };
    }

    /**
     * Render as Markdown document
     *
     * @require showdown.js
     * @require hljs.js
     * @param {String} sText
     * @return {String}
     */
    function _renderMarkdown(sText) {
        return $yona.xssClean(marked.parse(sText, htVar.htMarkedOption));
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
        const source = {
            "body": sContentBody,
            "breaks": (welTarget.hasClass('readme-body') ? false : true)
        };

        $.ajax(htVar.sMarkdownRendererUrl,{
            "type": "post",
            "contentType":"application/json; charset=utf-8",
            "data": JSON.stringify(source),
            "success": function(data){
                welTarget.html(data);
                $('pre code').each((i, block) => {
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
        const sMarkdownText = welTarget.text();
        const sContentBody  = (sMarkdownText) ? _renderMarkdown(sMarkdownText) : welTarget.html();
        $('.markdown-loader').remove();
        welTarget.html(sContentBody).removeClass('markdown-before');
    }

    // Deprecated. so never call this method
    function _postMarkdownRender(){
        // Make first li font bold when multi-depth list is used
        const ul = $(".markdown-wrap > ul");
        ul.find("> li > ul").parent().closest('ul').css('font-weight', 'bold');  //ul > ul
        ul.find("> li > ol").parent().closest('ul').css('font-weight', 'bold');  //ul > ol

        const ol = $(".markdown-wrap > ol");
        ol.find("> li > ul").parent().closest('ol').css('font-weight', 'bold');  //ol > ul
        ol.find("> li > ol").parent().closest('ol').css('font-weight', 'bold');  //ol > ol
    }

    /**
     * set Markdown Editor
     *
     * @param {Wrapped Element} welTextarea
     */
    function _setEditor(welTextarea){
        const elContainer = welTextarea.parents('[data-toggle="markdown-editor"]').get(0);

        if(!elContainer){
            return false;
        }

        $(elContainer).on("click", 'a[data-mode="preview"]', (weEvt) =>{
            const welPreview = $(weEvt.delegateTarget).find("div.markdown-preview");
            const sContentBody = welTextarea.val();

            _replaceAutoLink(welPreview, sContentBody);

            welPreview.css({"min-height": welTextarea.height() + 'px'});
        });

        welTextarea.on("keydown.tabkey-event-handler", function(e) {
            const $this = $(this);
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
        const waTarget = $(sQuery || "[markdown]"); // TODO: markdown=true

        waTarget.each((nIndex, elTarget) =>{
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
        const sTagName = elTarget.tagName.toUpperCase();
        return (sTagName === "TEXTAREA" || sTagName === "INPUT" || elTarget.contentEditable == "true");
    }

    // public interface
    return {
        "init"  : _init,
        "enableMarkdown": _enableMarkdown,
        "render" : _render
    };
})();
