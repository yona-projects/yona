/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

yona.Markdown = (function(htOptions){

    var htVar = {};

    // 호출부가 제각각이라(내부에서는 raw 엘리먼트, yona.project.Home.js 등 미전환
    // 파일에서는 jQuery 객체) 단일 엘리먼트/컬렉션 인자를 모두 raw DOM으로 정규화한다.
    function _toElement(el){
        if(!el){
            return null;
        }
        if(el.jquery){
            return el[0];
        }
        return el;
    }

    function _toElements(x){
        if(!x){
            return document.querySelectorAll("[markdown]");
        }
        if(typeof x === "string"){
            return document.querySelectorAll(x);
        }
        if(x.jquery){
            return x.toArray();
        }
        if(x.length !== undefined){
            return x; // NodeList or Array
        }
        return [x]; // single Element
    }

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
        var elTarget = _toElement(welTarget);
        var source = {
            "body": sContentBody,
            "breaks": (elTarget.classList.contains('readme-body') ? false : true)
        };

        fetch(htVar.sMarkdownRendererUrl, {
            "method": "post",
            "headers": {"Content-Type": "application/json; charset=utf-8"},
            "body": JSON.stringify(source)
        }).then(function(response){
            // jQuery의 success 콜백은 HTTP 에러 상태(4xx/5xx)에서는 호출되지 않았으므로
            // (원본에 error 핸들러가 없어 그런 경우 조용히 무시됐다) response.ok를 직접
            // 확인해 동일하게 동작시킨다.
            if(!response.ok){
                return Promise.reject(response);
            }
            return response.text();
        }).then(function(data){
            elTarget.innerHTML = data;
            document.querySelectorAll('pre code').forEach(function(block){
                hljs.highlightElement(block);
            });
        }).catch(function(){
            // 실패해도 아무것도 안 함(원본 jQuery 버전에도 error 핸들러 없음) - 이 요청이
            // 실패해도 교체되지 않은 원본 콘텐츠가 사용자에게 그대로 보이기 때문.
        });
    }

    /**
     * set Markdown Viewer
     *
     * @param {Element} elTarget is not <textarea> or <input>
     */
    function _setViewer(elTarget){
        var sMarkdownText = elTarget.textContent;
        var sContentBody  = (sMarkdownText) ? _renderMarkdown(sMarkdownText) : elTarget.innerHTML;
        document.querySelectorAll('.markdown-loader').forEach(function(el){ el.remove(); });
        elTarget.innerHTML = sContentBody;
        elTarget.classList.remove('markdown-before');
    }

    // Deprecated. so never call this method
    function _postMarkdownRender(){
        // Make first li font bold when multi-depth list is used
        document.querySelectorAll(".markdown-wrap > ul").forEach(function(ul){
            ul.querySelectorAll("> li > ul").forEach(function(el){ el.closest('ul').style.fontWeight = 'bold'; }); //ul > ul
            ul.querySelectorAll("> li > ol").forEach(function(el){ el.closest('ul').style.fontWeight = 'bold'; }); //ul > ol
        });

        document.querySelectorAll(".markdown-wrap > ol").forEach(function(ol){
            ol.querySelectorAll("> li > ul").forEach(function(el){ el.closest('ol').style.fontWeight = 'bold'; }); //ol > ul
            ol.querySelectorAll("> li > ol").forEach(function(el){ el.closest('ol').style.fontWeight = 'bold'; }); //ol > ol
        });
    }

    /**
     * set Markdown Editor
     *
     * @param {Element} elTextarea
     */
    function _setEditor(elTextarea){
        var elContainer = elTextarea.closest('[data-toggle="markdown-editor"]');

        if(!elContainer){
            return false;
        }

        elContainer.addEventListener("click", function(weEvt){
            var match = weEvt.target.closest('a[data-mode="preview"]');
            if(!match || !elContainer.contains(match)){
                return;
            }

            var elPreview = elContainer.querySelector("div.markdown-preview");
            var sContentBody = elTextarea.value;

            _replaceAutoLink(elPreview, sContentBody);

            elPreview.style.minHeight = elTextarea.offsetHeight + 'px';
        });

        // _tab()/_untab()은 이 파일 자신이 아니라 yona.KeyControl.js가 전역
        // window._tab/window._untab로 정의한다(site/layout.html이 모든 화면에
        // 항상 로드하므로 실제로 호출 가능하다) - 이전 주석은 "이 코드베이스 어디에도
        // 정의돼 있지 않다"고 잘못 적혀 있었으나, 실제로는 정상 동작하는 기능이다
        // (2026-09-17, widget-candidates.md 5번 항목에서 재확인).
        elTextarea.addEventListener("keydown", function(e) {
            if (e.shiftKey && e.key === 'Tab') {
                e.preventDefault();
                _untab(this);
            } else if ( e.key === 'Tab' ) {
                e.preventDefault();
                _tab(this);
            }
        });
    }

    /**
     * enableMarkdown on target elements
     *
     * @param {String} sQuery Selector string for targets
     */
    function _enableMarkdown(sQuery){
        var waTarget = _toElements(sQuery);

        waTarget.forEach(function(elTarget){
            _isEditableElement(elTarget) ? _setEditor(elTarget) : _setViewer(elTarget);
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
