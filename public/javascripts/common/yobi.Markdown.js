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
     * initialize variables
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions){
        htVar.sTplSwitch = htOptions.sTplSwitch;
        htVar.sIssuesUrl = htOptions.sIssuesUrl;
        htVar.sProjectUrl = htOptions.sProjectUrl;
        htVar.bWysiwyg = htOptions.bWysiwyg || false;
        htVar.sUserRules = '[a-z0-9_\\-\\.]';
        htVar.sProjecRules = '[a-z0-9_\\-]';
        htVar.sIssueRules = '\\d';
        htVar.sSha1Rules = '[a-f0-9]{7,40}';
        htVar.htFilter = new Filter();

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
            
        var htMarkedOption = {
          gfm: true,
          wysiwyg: htVar.bWysiwyg,
          tables: true,
          breaks: false,
          pedantic: false,
          sanitize: false,
          smartLists: true,
          langPrefix: '',
          highlight: function(code, lang) {
            if(!lang) return code;
            return hljs(code,lang).value;
          }
        };
        
        var hooks = function(sSrc,sType) {

            var sGfmLinkRules =  '(([user]+\\/[project]+)|([user]+))?(#([issue]+)|(@)?([shar1]))|@([user]+)';
            
            if(sType=='code') return sSrc;

            sGfmLinkRules = sGfmLinkRules.replace(/\[user\]/g,htVar.sUserRules)
                .replace(/\[user\]/g,htVar.sUserRules)
                .replace(/\[project\]/g,htVar.sProjecRules)
                .replace(/\[shar1\]/g,htVar.sSha1Rules)
                .replace(/\[issue\]/g,htVar.sIssueRules);             

            sSrc = sSrc.replace(new RegExp(sGfmLinkRules,'gm'), function(sMatch,sProjectGroup,sProjectPath,sUserName,sTargetGoup,sIssue,sAt ,sShar1,sMention,nMatchIndex) { 
                var rIgnoreRules = /<(?:a|code)(?:\s+[^>]*)*\s*>[^\n]*<\/(?:a|code)>|(?:\S+)\s*=\s*["'][^"']*["']/igm,
                    aIgnores;

                while(aIgnores = rIgnoreRules.exec(sSrc)) {
                  if(nMatchIndex > aIgnores.index && nMatchIndex < aIgnores.index + aIgnores[0].length) return sMatch;
                }    

                if(/\w/.test(sSrc[nMatchIndex-1]) || /\w/.test(sSrc[nMatchIndex+sMatch.length])) return sMatch;

                return _makeLink(sMatch,sProjectGroup,sProjectPath,sUserName,sTargetGoup,sIssue, sAt, sShar1,sMention);
            });    
            
            return  sSrc;
        };

        htMarkedOption.hook = hooks;
      
        return htVar.htFilter.sanitize(marked(sText,htMarkedOption)).xss();
    }

    function _makeLink(sMatch,sProjectGroup,sProjectPath,sUserName,sTargetGoup,sIssue,sAt,sShar1,sMention) {
        var sRef,
            sTitle,
            sOwner = htVar.sProjectUrl.split('/')[1],
            sProject = htVar.sProjectUrl.split('/')[2]; 

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
        } else if(sShar1 && !sAt) {
            // SHA1 be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [sOwner, sProject, 'commit' , sMatch].join("/");
            sTitle = sMatch.slice(0,7);
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

        return '<a href="/'+sRef+'">'+sTitle+'</a>';
        
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
        welTarget.html(_renderMarkdown(welTarget.text())).removeClass('markdown-before');
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
