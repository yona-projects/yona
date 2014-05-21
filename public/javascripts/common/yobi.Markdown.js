/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        htVar.sIssuesUrl = htOptions.sIssuesUrl;
        htVar.sProjectUrl = htOptions.sProjectUrl;
        htVar.bBreaks = htOptions.bBreaks;

        htVar.sUserRules = '[a-zA-Z0-9_\\-\\.\\/]';
        htVar.sProjecRules = '[a-zA-Z0-9_\\-\\.]';
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
            "highlight" : function(sCode, sLang) {
                if(sLang) {
                    try {
                        return hljs.highlight(sLang.toLowerCase(), sCode).value;
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
            return _makeLink({
                "match" : sMatch,
                "projectGroup": sProjectGroup,
                "projectPath": sProjectPath,
                "userName": sUserName,
                "targetGroup": sTargetGoup,
                "issue": sIssue,
                "at": sAt,
                "sha1": sShar1,
                "mention": sMention
            });
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
    function _makeLink(target){
        var sRef,
            sTitle,
            sOwner = htVar.sProjectUrl.split('/')[1],
            sProject = htVar.sProjectUrl.split('/')[2];
        var sClass = "";

        if(target.projectGroup && target.userName && target.issue && !target.projectPath) {
            // User/#Num nforge#12345
            sRef = [target.userName, target.project, 'issue', target.issue].join("/");
            sTitle = target.match;
        } else if(target.projectGroup && target.projectPath && target.issue && !target.userName) {
            // User/Project#Num nforge/yobi#12345
            sRef = [target.projectGroup, 'issue', target.issue].join("/");
            sTitle = target.match;
        } else if(target.issue && !target.projectGroup && !target.projectPath && !target.userName) {
            // #Num #123
            sRef = [sOwner, sProject, 'issue', target.issue].join("/");
            sTitle = target.match;
            sClass="issueLink";
        } else if(target.sha1 && !target.at && !/[^0-9a-f]/.test(target.match)) {
            // SHA1 be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [sOwner, sProject, 'commit' , target.match].join("/");
            sTitle = target.match.slice(0,7);
        } else if(target.sha1 && target.at) {
            // SHA1 @be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [sOwner, sProject, 'commit' , target.match.slice(1)].join("/");
            sTitle = target.match.slice(1,7);
        } else if(target.projectGroup && target.userName && target.sha1 && target.at && !target.projectPath ) {
            // User@SHA1 nforge@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [target.userName, target.project, 'commit' , target.sha1].join("/");
            sTitle = [target.userName, '@', target.sha1.slice(0,7)].join("");
        } else if(target.projectGroup && target.sha1 && target.projectPath && target.at && !target.userName) {
            // User/Project@SHA1 nforge/yobi@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
            sRef = [target.projectGroup, 'commit' , target.sha1].join("/");
            sTitle = [target.projectGroup, '@', target.sha1.slice(0,7)].join("");
        } else if (target.mention) {
            sRef = target.mention;
            sTitle = target.match;
        } else {
            return target.match;
        }

        return '<a href="/'+sRef+'" class="'+sClass+'">'+sTitle+'</a>';
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
            welPreview.html(_renderMarkdown(welTextarea.val()));
            welPreview.css({"min-height": welTextarea.height() + 'px'});
        });

        welTextarea.on("keydown.tabkey-event-handler", function(e) {
            if(e.keyCode === 9){ //tab
                e.preventDefault();
                var start = this.selectionStart;
                var end = this.selectionEnd;
                this.value = this.value.substring(0, start) + "\t" + this.value.substring(end);
                this.selectionEnd = start + 1;
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
        "renderMarkdown": _renderMarkdown
    };
})();
