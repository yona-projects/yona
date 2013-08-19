/**
 * @(#)yobi.Markdown 2013.03.21
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */
yobi.Markdown = function(htOptions){

    var htVar = {};
    var htElement = {};
    
    /**
     * initialize
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement(htOptions);
        
        _enableMarkdown();
    }

    /**
     * Return a regular expresion for autolink.
     */
    function _rxLink() {
        // case insensitive match
        var sUserPat = "[a-z0-9-_.]+";
        var sProjectPat = "[-a-z0-9_]+";
        var sNumberPat = "[0-9]+";
        var sShaPat = "[0-9a-f]{7,40}";

        var sProjectPathPat = sUserPat + "/" + sProjectPat;
        var sTargetPat =
            "#(" + sNumberPat + ")|(@)?(" + sShaPat + ")|@(" + sUserPat + ")";

        return new RegExp(
                "(\\S*?)(" + sProjectPathPat + ")?(?:" + sTargetPat + ")(\\S*?)", "gi");
    }

    /**
     * initialize variables
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions){
        htVar.rxCodeBlock = /```(\w+)(?:\r\n|\r|\n)((\r|\n|.)*?)(\r|\n)```/gm;
        htVar.rxLink = _rxLink();
        htVar.sTplSwitch = htOptions.sTplSwitch;
        htVar.sIssuesUrl = htOptions.sIssuesUrl;
        htVar.sProjectUrl = htOptions.sProjectUrl;
        htVar.bGfmStyle = htOptions.bGfmStyle || false;
    }
    
    /**
     * initialize element
     * @param {Hash Table} htOptions
     */
    function _initElement(htOptions){
        htElement.waTarget = $(htOptions.aTarget) || $("[markdown]");
    }
    
    /**
     * Render as Markdown document
     * @require showdown.js
     * @require hljs.js
     * @param {String} sText
     * @return {String}
     */
    function _renderMarkdown(sText) {
            
        var options = {
          gfm: true,
          tables: true,
          breaks: false,
          pedantic: false,
          sanitize: true,
          smartLists: true,
          langPrefix: '',
          highlight: function(code, lang) {
            if (lang === 'js') {
              return highlighter.javascript(code);
            }
            return code;
          }
        };
        
        var makeLink = function(sMatch, sPre, sProject, sNum, sAt, sSha, sUser, sPost) {
            var path, text;

            if (sPost) {
                return sMatch;
            }

            if (sPre.substr(0, 4).toLowerCase() == 'http') {
                return sMatch;
            }

            if (sSha && sProject && sAt) {
                // owner/sProject@2022d330c5858eae9ca9cb5acb9e6a5060563b2c
                path = '/' + sProject + '/commit/' + sSha;
                text = sProject + '/' + sSha;
            } else if (sSha && !sAt) {
                // 2022d330c5858eae9ca9cb5acb9e6a5060563b2c
                path = htVar.sProjectUrl + '/commit/' + sSha;
                text = sSha;
            } else if (sSha && sAt) {
                // @abc1234
                // This is a link for sUser even if it looks like a 160bit sSha.
                path = '/' + sSha;
                text = '@' + sSha;
            } else if (sNum && sProject) {
                // owner/sProject#1234
                path = '/' + sProject + '/issue/' + sNum;
                text = sProject + '/' + sNum;
            } else if (sNum) {
              // #1234
                path = htVar.sProjectUrl + '/issue/' + sNum;
                text ='#' + sNum;
            } else if (sUser) {
                // @foo
                if (sPre.length == 0 || !/\w/.test(sPre[sPre.length - 1])) {
                    path = '/' + sUser;
                    text = '@' + sUser;
                }
            }

            if (path && text) {
                return sPre + '<a href="' + path + '">' + text + '</a>' + sPost;
            } else {
                return sMatch;
            }
        }

        sText = sText.replace(htVar.rxLink, makeLink);

        if(htVar.bGfmStyle) sText = sText.replace(/\n/g, "  \n");
             
        var lexer = new marked.Lexer(options);
        var tokens = lexer.lex(sText);
        var sHTML = marked.parser(tokens);

        return sHTML;
    }

    /**
     * set Markdown Editor
     * @param {Wrapped Element} welTextarea
     */
    function _setEditor(welTextarea) {
        // create new preview area 
        var welPreview = $('<div class="markdown-preview markdown-wrap">');
        welPreview.css({
            "display"   : "none",
            "width"     : welTextarea.width()  + 'px',
            "min-height": welTextarea.height() + 'px',
            "padding"   : welTextarea.css("padding")
        });

        var welPreviewSwitch = $('<div id="mode-select">');
            welPreviewSwitch.html(htVar.sTplSwitch);

        var fOnChangeSwitch = function() {
            var bPreview = ($("input:radio[name=edit-mode]:checked").val() == "preview");
            welPreview.html(_renderMarkdown(welTextarea.val()));
            (bPreview ? welPreview: welTextarea).show();
            (bPreview ? welTextarea: welPreview).hide();
        };
        welPreviewSwitch.change(fOnChangeSwitch);

        welTextarea.before(welPreviewSwitch);
        welTextarea.before(welPreview);
    }

    /**
     * set Markdown Viewer
     * @param {Wrapped Element} welTarget is not <textarea> or <input>
     */
    function _setViewer(welTarget) {
        welTarget.html(_renderMarkdown(welTarget.text())).show();
    }
    
    /**
     * enableMarkdown
     * same as nforge.markdown.enable
     */
    function _enableMarkdown(){
        var sTagName;
        
        htElement.waTarget.each(function(nIndex, elTarget){
            sTagName = elTarget.tagName.toUpperCase();
            
            if(sTagName == "TEXTAREA" || sTagName == "INPUT" || elTarget.contentEditable == "true"){
                _setEditor($(elTarget));
            } else {
                _setViewer($(elTarget));
            }
        });
    }

    // initialize
    _init(htOptions || {});
};
