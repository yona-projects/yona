/**
 * Yona, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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
// Render pagination in the given target HTML element.
// Usage: Pagiation.updatePagination(target, totalPages);
// For more details, see docs/technical/pagination.md

yona.Pagination = (function(window, document) {
    var htRegEx = {};
    var rxDigit = /^.[0-9]*$/;
    // isNumeric determines hex, point or negative numbers as numeric.
    // but, rxDigit finds only positive decimal integer numbers

    // elTarget는 수많은 템플릿 인라인 스크립트/미전환 JS 파일에서 여전히 jQuery
    // 객체($("#pagination"))로 넘어오므로, 그 호출부들을 건드리지 않기 위해
    // raw DOM 엘리먼트로 정규화한다.
    function _toElement(el){
        if(el && el.jquery){
            return el[0];
        }
        return el;
    }

    // jQuery $.isNumeric()과 동일한 트릭(문자열 - 숫자 파싱값이 0이 아니면 NaN이
    // 되는 성질 이용). 16진수/부호/소수점 문자열도 숫자로 판정하는 동작까지 동일하게 유지.
    function isNumeric(obj){
        var realStringObj = obj && obj.toString();
        return !Array.isArray(obj) && (realStringObj - parseFloat(realStringObj) + 1) >= 0;
    }

    /**
     * getQuery
     * @param {String} url
     */
    function getQuery(url){
        var parser = document.createElement('a');
            parser.href = url.replace('&amp;', '&');

        return parser.search;
    }

    /**
     * valueFromQuery
     * @param {String} key
     * @param {String} query
     */
    function valueFromQuery(key, query) {
        htRegEx[key] = htRegEx[key] || new RegExp('(^|&|\\?)' + key + '=([^&]+)');
        var result = htRegEx[key].exec(query);

        return (result) ? result[2]: null;
    }

    /**
     * urlWithQuery
     * @param {String} url
     * @param {String} query
     */
    function urlWithQuery(url, query) {
        var parser = document.createElement('a');
            parser.href = url;
            parser.search = (query[0]=='?') ? query : '?' + query;

        return parser.href;
    }

    /**
     * urlWithPageNum
     *
     * Create a url whose query has a paramNameForPage parameter whose value is
     * pageNum.
     *
     * @param {String} url
     * @param {Number} pageNum
     * @param {String} paramNameForPage
     */
    function urlWithPageNum(url, pageNum, paramNameForPage) {
        var query = getQuery(url);
        var regex = new RegExp('(^|&|\\?)' + paramNameForPage + '=[^&]+');
        var result = regex.exec(query);
        if (result) {
            // if paramNameForPage parameter already exists, update it.
            query = query.replace(regex, result[1] + paramNameForPage + '=' + pageNum);
        } else {
            // if not add new one.
            query = query + '&' + paramNameForPage + '=' + pageNum;
        }

        return urlWithQuery(url, query);
    }

    /**
     * validateOptions
     */
    function validateOptions(options) {
        if (!isNumeric(options.current)) {
            throw new Error("options.current is not valid: " + options.current);
        }
    }

    /**
     * Update pagination
     *
     * @param {HTMLElement} elTarget
     * @param {Number} nTotalPages
     * @param {Hash Table} htOpt
     */
    function updatePagination(elTarget, nTotalPages, htOptions) {
        // `nTotalPages <= 0`만으로는 undefined/NaN을 걸러내지 못한다(둘 다 모든 비교
        // 연산자에서 false를 내 이 가드를 그냥 통과한다) - 결과가 0건이라 #pagination
        // 자체가 서버 렌더링에서 빠지는 화면에서 elTarget이 없거나 총 페이지 수가
        // undefined인 채로 넘어와 "Cannot set properties of undefined"로 죽던 것을
        // 발견했다 - 둘 다 명시적으로 걸러낸다.
        if(!elTarget || !(nTotalPages > 0)){
            return;
        }

        var welTarget = _toElement(elTarget);
        if(!welTarget){
            return;
        }

        // 대상 컨테이너 자체는 절대 교체하지 않는다 - issue.List.js 등 일부 호출부가
        // #pagination 참조를 캐싱해두고 update()를 여러 번 재호출하므로, 컨테이너를
        // 교체하면 두 번째 호출부터 이미 분리된 엘리먼트를 다시 건드리는 셈이라
        // 아무 효과가 없다. 안에 <yona-pagination> 자식을 한 번만 만들고 이후로는
        // 재사용해 update()로 위임한다.
        if(typeof customElements !== "undefined" && customElements.get("yona-pagination")){
            var elVue = (welTarget.tagName.toLowerCase() === "yona-pagination")
                ? welTarget
                : welTarget.querySelector(":scope > yona-pagination");
            if(!elVue){
                elVue = document.createElement("yona-pagination");
                welTarget.innerHTML = "";
                welTarget.appendChild(elVue);
            }
            elVue.update(nTotalPages, htOptions || {});
            return;
        }

        var htData = htOptions || {};

        htData.url = htData.url || document.URL;
        htData.firstPage = htData.firstPage || 1;
        htData.totalPages = nTotalPages;
        htData.paramNameForPage = htData.paramNameForPage || 'pageNum';
        htData.current = !rxDigit.test(htData.current) ? _getPageNumFromUrl(htData) : htData.current;
        htData.hasPrev = (typeof htData.hasPrev === "undefined") ? htData.current > htData.firstPage : htData.hasPrev;
        htData.hasNext = (typeof htData.hasNext === "undefined") ? htData.current < htData.totalPages : htData.hasNext;

        validateOptions(htData);

        welTarget.innerHTML = '';
        welTarget.classList.add('page-navigation-wrap');

        // prev/next link
        var welPagePrev = _getPrevPageLink(htData);
        var welPageNext = _getNextPageLink(htData);

        // page input box
        var welPageInput = _getPageInputBox(htData);
        var welPageInputWrap = document.createElement('li');
        welPageInputWrap.className = 'page-num';
        welPageInputWrap.appendChild(welPageInput);

        var welDelimiter = document.createElement('li');
        welDelimiter.className = 'page-num delimiter';
        welDelimiter.textContent = '/';

        var welTotalPages = document.createElement('li');
        welTotalPages.className = 'page-num';
        welTotalPages.textContent = nTotalPages;

        // fill #pagination
        var welPageList = document.createElement('ul');
        welPageList.className = 'page-nums';
        welPageList.append(welPagePrev, welPageInputWrap, welDelimiter, welTotalPages, welPageNext);
        welTarget.appendChild(welPageList);
    }

    /**
     * Get current page number from QueryString
     *
     * @param htData
     * @returns {Number}
     * @private
     */
    function _getPageNumFromUrl(htData){
        var sQuery = getQuery(htData.url);
        var nPageNumFromUrl  = parseInt(valueFromQuery(htData.paramNameForPage, sQuery), 10);
        return nPageNumFromUrl || htData.firstPage;
    }

    /**
     * Get PageNum INPUT element
     *
     * @param htData
     * @returns {Element}
     * @private
     */
    function _getPageInputBox(htData){
        var welPageInput = document.createElement('input');
        welPageInput.type = 'number';
        welPageInput.setAttribute('pattern', '[0-9]*');
        welPageInput.className = 'input-mini nospinner';

        welPageInput.name = htData.paramNameForPage;
        welPageInput.max  = htData.totalPages;
        welPageInput.min  = 1;

        welPageInput.value = htData.current;

        welPageInput.addEventListener("keydown", function(weEvt){
            if(!isValidInputNum(welPageInput, htData.current)){
                return;
            }

            var nCurrentValue = welPageInput.value;

            if(typeof htData.submit === "function"){
                htData.submit(nCurrentValue);
            } else if(weEvt.which === 13){
                document.location.href = urlWithPageNum(htData.url, nCurrentValue, htData.paramNameForPage);
            }
        });

        return welPageInput;
    }

    /**
     * Get previous page link
     *
     * @param htData
     * @returns {Element}
     * @private
     */
    function _getPrevPageLink(htData){
        var sLinkText = Messages("button.prevPage") || 'PREV';
        var sLinkHTMLOn = '<i class="ico btn-pg-prev"></i><span>' + sLinkText + '</span>';
        var sLinkHTMLOff = '<i class="ico btn-pg-prev off"></i><span class="off">' + sLinkText + '</span>';

        var htOptions = Object.assign(htData, {
            "bActive"  : htData.hasPrev,
            "sLinkHref": htData.hasPrev ? urlWithPageNum(htData.url, htData.current - 1, htData.paramNameForPage) : "",
            "sLinkHTMLOn"   : sLinkHTMLOn,
            "sLinkHTMLOff"  : sLinkHTMLOff,
            "sShortcutKey"  : "LEFT",
            "nSubmitPageNum": htData.current - 1
        });

        var welPagePrev = _buildPageLink(htOptions);

        return welPagePrev;
    }

    /**
     * Get next page link
     *
     * @param htData
     * @returns {Element}
     * @private
     */
    function _getNextPageLink(htData){
        var sLinkText = Messages("button.nextPage") || 'NEXT';
        var sLinkHTMLOn = '<span>' + sLinkText + '</span><i class="ico btn-pg-next"></i>';
        var sLinkHTMLOff = '<span class="off">' + sLinkText + '</span><i class="ico btn-pg-next off"></i>';

        var htOptions = Object.assign(htData, {
            "bActive"  : htData.hasNext,
            "sLinkHref": htData.hasNext ? urlWithPageNum(htData.url, htData.current + 1, htData.paramNameForPage) : "",
            "sLinkHTMLOn"   : sLinkHTMLOn,
            "sLinkHTMLOff"  : sLinkHTMLOff,
            "sShortcutKey"  : "RIGHT",
            "nSubmitPageNum": htData.current + 1
        });

        var welPageNext = _buildPageLink(htOptions);

        return welPageNext;
    }

    /**
     * Build prev/next page link
     *
     * @param htData
     * @returns {Element}
     * @private
     */
    function _buildPageLink(htData){
        var welPageLink = document.createElement('li');
        welPageLink.className = 'page-num ikon';

        if(htData.bActive){
            var welLink = document.createElement('a');
            welLink.setAttribute('pjax-page', '');
            welLink.innerHTML = htData.sLinkHTMLOn;

            if(typeof htData.submit === 'function'){
                welLink.setAttribute("href", "javascript: void(0);");
                welLink.addEventListener("click", function(){
                    htData.submit(htData.nSubmitPageNum);
                });
            } else {
                welLink.setAttribute("href", htData.sLinkHref);
            }

            welPageLink.appendChild(welLink);
        } else {
            welPageLink.innerHTML = htData.sLinkHTMLOff;
        }

        // if yona.ShortcutKey exists
        if(yona.ShortcutKey){
            var htKeyOpt = {};
            htKeyOpt[htData.sShortcutKey] = htData.sLinkHref;
            yona.ShortcutKey.setKeymapLink(htKeyOpt);
        }

        return welPageLink;
    }

    // validate number range
    function isValidInputNum(welTarget, nCurrentPageNum){
        if(rxDigit.test(welTarget.value) === false){
            welTarget.value = nCurrentPageNum;
            return false;
        }

        var nVal = parseInt(welTarget.value, 10);
        var nMin = parseInt(welTarget.min, 10);
        var nMax = parseInt(welTarget.max, 10);

        if(nVal < nMin){
            welTarget.value = nMin;
        } else if(nVal > nMax){
            welTarget.value = nMax;
        }
        return true;
    }

    return {
        "update" : updatePagination
    };
})(window, document);

document.addEventListener('click', function(e){
    var match = e.target.closest('input[name="pageNum"][type="number"]');
    if(match){
        match.select();
    }
});
