/**
 * @(#)yobi.Markdown 2013.03.21
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */
yobi.Markdown = (function(htOptions){

    var htVar = {};
    var htElement = {};

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
        htVar.sTplSwitch = htOptions.sTplSwitch;
        htVar.sIssuesUrl = htOptions.sIssuesUrl;
        htVar.sProjectUrl = htOptions.sProjectUrl;
        htVar.bBreaks = htOptions.bBreaks;
        htVar.sUserRules = '[a-z0-9_\\-\\.]';
        htVar.sProjecRules = '[a-z0-9_\\-\\.]';
        htVar.sIssueRules = '\\d';
        htVar.sSha1Rules = '[a-f0-9]{7,40}';
        htVar.htFilter = new Filter();
        htVar.htMarkedOption = {
            "gfm"       : true,
            "tables"    : true,
            "pedantic"  : false,
            "sanitize"  : false,
            "smartLists": true,
            "langPrefix": '',
            "breaks"    : htVar.bBreaks,
            "hook"      : _markedHooks,
            "highlight" : function(sCode, sLang){
                return (!sLang) ? sCode : hljs(sCode, sLang).value;
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
        return htVar.htFilter.sanitize(marked(sText, htVar.htMarkedOption)).xss();
    }

    /**
     * marked.js hooks function
     * 
     * @require marked.js
     * @param {String} sSrc
     * @param {String} sType
     * @return {String}
     */
    function _markedHooks(sSrc, sType){
        if(sType === 'code'){
            return sSrc;
        }

        // define rxGfmLinkRules once
        htVar = htVar || {};
        
        if(typeof htVar.rxGfmLinkRules === "undefined"){
            htVar.rxWord = /\w/;
            var rxUser = /\[user\]/g;
            var rxProject = /\[project\]/g;
            var rxShar1 = /\[shar1\]/g;
            var rxIssue = /\[issue\]/g;
            var sGfmLinkRules = '(([user]+\\/[project]+)|([user]+))?(#([issue]+)|(@)?([shar1]))|@([user]+)';

            sGfmLinkRules = sGfmLinkRules.replace(rxUser, htVar.sUserRules)
                .replace(rxUser,    htVar.sUserRules)
                .replace(rxProject, htVar.sProjecRules)
                .replace(rxShar1,   htVar.sSha1Rules)
                .replace(rxIssue,   htVar.sIssueRules);

            htVar.rxGfmLinkRules = new RegExp(sGfmLinkRules,'gm');
        }
        
        sSrc = sSrc.replace(htVar.rxGfmLinkRules, function(sMatch, sProjectGroup, sProjectPath, sUserName, sTargetGoup, sIssue, sAt, sShar1, sMention, nMatchIndex){
            var aIgnores,
                rxIgnoreRules = /<(?:a|code)(?:\s+[^>]*)*\s*>[\s\S]*<\/(?:a|code)>|(?:\S+)\s*=\s*["'][^"']*["']/igm;
                
            while(aIgnores = rxIgnoreRules.exec(sSrc)){
                if(nMatchIndex > aIgnores.index && nMatchIndex < aIgnores.index + aIgnores[0].length){
                    return sMatch;
                }   
            }

            if(htVar.rxWord.test(sSrc[nMatchIndex-1]) || htVar.rxWord.test(sSrc[nMatchIndex+sMatch.length])){
                return sMatch;
            }
            return _makeLink(sMatch, sProjectGroup, sProjectPath, sUserName, sTargetGoup, sIssue, sAt, sShar1, sMention);
        });
        
        return sSrc;
    }
    
    /**
     * make hyperlink automatically with patterns.
     * 
     * @param {String} sMatch
     * @param {String} sProjectGroup
     * @param {String} sProjectPath
     * @param {String} sUserName
     * @param {String} sTargetGoup
     * @param {String} sIssue
     * @param {String} sAt
     * @param {String} sShar1
     * @param {String} sMention
     * @return {String}
     */
    function _makeLink(sMatch, sProjectGroup, sProjectPath, sUserName, sTargetGoup, sIssue, sAt, sShar1, sMention){
        var sRef,
            sTitle,
            sOwner = htVar.sProjectUrl.split('/')[1],
            sProject = htVar.sProjectUrl.split('/')[2];
        var sClass = "";

        if(sProjectGroup && sUserName && sIssue && !sProjectPath) {
            // User/#Num nforge#12345
            sRef = [sUserName, sProject, 'issue', sIssue].join("/");
            sTitle = sMatch;
        } else if(sProjectGroup && sProjectPath && sIssue && !sUserName) {
            // User/Project#Num nforge/yobi#12345
            sRef = [sProjectGroup, 'issue', sIssue].join("/");
            sTitle = sMatch;
        } else if(sIssue && !sProjectGroup && !sProjectPath && !sUserName) {
            // #Num #123
            sRef = [sOwner, sProject, 'issue', sIssue].join("/");
            sTitle = sMatch;
            sClass="issueLink";
        } else if(sShar1 && !sAt && !/[^0-9a-f]/.test(sMatch)) {
            // SHA1 be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [sOwner, sProject, 'commit' , sMatch].join("/");
            sTitle = sMatch.slice(0,7);
        } else if(sShar1 && sAt) {
            // SHA1 @be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [sOwner, sProject, 'commit' , sMatch.slice(1)].join("/");
            sTitle = sMatch.slice(1,7);    
        } else if(sProjectGroup && sUserName && sShar1 && sAt && !sProjectPath ) {
            // User@SHA1 nforge@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [sUserName, sProject, 'commit' , sShar1].join("/");
            sTitle = [sUserName, '@', sShar1.slice(0,7)].join("");
        } else if(sProjectGroup && sShar1 && sProjectPath && sAt && !sUserName) {
            // User/Project@SHA1 nforge/yobi@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [sProjectGroup, 'commit' , sShar1].join("/");
            sTitle = [sProjectGroup, '@', sShar1.slice(0,7)].join("");
        } else if (sMention) {
            sRef = sMention;
            sTitle = sMatch;
        } else {
            return sMatch;
        }

        return '<a href="/'+sRef+'" class="'+sClass+'">'+sTitle+'</a>';
    }

    /**
     * set Markdown Editor
     * 
     * @param {Wrapped Element} welTextarea
     */
    function _setEditor(welTextarea) {
        // create new preview area
        var welPreview = $('<div class="markdown-preview markdown-wrap">');
        var welTextareaBox = $('.textarea-box');
        
        welPreview.css({
            "display"   : "none",
            "min-height": welTextarea.height() + 'px'
        });

        var welPreviewSwitch = $('input[name="edit-mode"]');

        var fOnChangeSwitch = function() {
            var bPreview = ($("input:radio[name=edit-mode]:checked").val() == "preview");
            welPreview.html(_renderMarkdown(welTextarea.val()));
            (bPreview ? welPreview: welTextareaBox).show();
            (bPreview ? welTextareaBox: welPreview).hide();
        };

        welPreviewSwitch.change(fOnChangeSwitch);
        welTextareaBox.before(welPreview);
    }

    /**
     * set Markdown Viewer
     * 
     * @param {Wrapped Element} welTarget is not <textarea> or <input>
     */
    function _setViewer(welTarget) {
        var sMarkdownText = welTarget.text();
        var sContentBody  = (sMarkdownText) ? _renderMarkdown(sMarkdownText) : welTarget.html();
        welTarget.html(sContentBody).removeClass('markdown-before');
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
     * Returns that specifieid element is editable
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
        "renderMarkdown": _renderMarkdown
    };
})();
