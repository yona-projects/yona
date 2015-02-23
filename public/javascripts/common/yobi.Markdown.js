/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
        htVar.sMarkdownRendererUrl = htOptions.sMarkdownRendererUrl;

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
        return $yobi.xssClean(marked(sText, htVar.htMarkedOption));
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
        $.ajax(htVar.sMarkdownRendererUrl,{
            "type": "post",
            "data": {"body": sContentBody, "breaks": (welTarget.hasClass('readme-body') ? false : true)},
            "success": function(data){
                welTarget.html(data);
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
        "render" : _render
    };
})();
