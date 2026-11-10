/**
 * Yobi, Project Hosting SW
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
    const htRegEx = {};
    const rxDigit = /^.[0-9]*$/;
    // $.isNumeric determines hex, point or negative numbers as numeric.
    // but, rxDigit finds only positive decimal integer numbers

    /**
     * getQuery
     * @param {String} url
     */
    function getQuery(url){
        const parser = document.createElement('a');
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
        const result = htRegEx[key].exec(query);

        return (result) ? result[2]: null;
    }

    /**
     * urlWithQuery
     * @param {String} url
     * @param {String} query
     */
    function urlWithQuery(url, query) {
        const parser = document.createElement('a');
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
        let query = getQuery(url);
        const regex = new RegExp('(^|&|\\?)' + paramNameForPage + '=[^&]+');
        const result = regex.exec(query);
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
     * 숫자이거나 숫자로 읽히는 글자인지 본다. jQuery 4에서 지워진 `$.isNumeric`과 같은 식이다
     * (`Number.isFinite(Number(값))`은 빈 글자, 공백, `true`에서 값이 달라 쓰지 않는다. `jquery4.test.mjs`가 두 식을 비교한다).
     *
     * @param {*} value
     * @returns {Boolean}
     */
    function _isNumeric(value) {
        return (typeof value === "number" || typeof value === "string") && !isNaN(value - parseFloat(value));
    }

    /**
     * validateOptions
     */
    function validateOptions(options) {
        if (!_isNumeric(options.current)) {
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
        if (nTotalPages <= 0){
            return;
        }

        const welTarget = $(elTarget);
        const htData = htOptions || {};

        htData.url = htData.url || document.URL;
        htData.firstPage = htData.firstPage || 1;
        htData.totalPages = nTotalPages;
        htData.paramNameForPage = htData.paramNameForPage || 'pageNum';
        htData.current = !rxDigit.test(htData.current) ? _getPageNumFromUrl(htData) : htData.current;
        htData.hasPrev = (typeof htData.hasPrev === "undefined") ? htData.current > htData.firstPage : htData.hasPrev;
        htData.hasNext = (typeof htData.hasNext === "undefined") ? htData.current < htData.totalPages : htData.hasNext;

        validateOptions(htData);

        welTarget.html('');
        welTarget.addClass('page-navigation-wrap');

        // prev/next link
        const welPagePrev = _getPrevPageLink(htData);
        const welPageNext = _getNextPageLink(htData);

        // page input box
        const welPageInput = _getPageInputBox(htData);
        const welPageInputWrap = $('<li class="page-num">').append(welPageInput);
        const welDelimiter = $('<li class="page-num delimiter">').text('/');
        const welTotalPages = $('<li class="page-num">').text(nTotalPages);

        // fill #pagination
        const welPageList = $('<ul class="page-nums">');
        welPageList.append([welPagePrev, welPageInputWrap, welDelimiter, welTotalPages, welPageNext]);
        welTarget.append(welPageList);

        _enableHtmxLinks(welTarget);
    }

    /**
     * `div[list-container]` 안에 그린 페이지 링크를 htmx로 이동하게 한다(D-96).
     * 링크는 스크립트가 만들기 때문에 htmx가 처음 문서를 훑을 때는 없다. `hx-boost`를 붙이고 `htmx.process`로 처리하게 한다.
     * 영역을 바꾸는 방식(`hx-target` 등)은 `list-container`의 속성에서 이어받는다.
     * `hx-boost` 링크는 Ctrl이나 Command를 누르고 누르면 htmx가 가로채지 않아 새 탭으로 열린다.
     * 영역 밖의 페이지 막대와 `submit` 함수를 쓰는 링크(`href`가 `javascript:`)는 건드리지 않는다.
     *
     * @param {jQuery} welTarget 페이지 막대를 그린 요소
     * @private
     */
    function _enableHtmxLinks(welTarget){
        if(typeof window.htmx === "undefined" || welTarget.closest("[list-container]").length === 0){
            return;
        }

        welTarget.find("a[list-page]").filter(function(){
            return (this.getAttribute("href") || "").indexOf("javascript:") !== 0;
        }).attr("hx-boost", "true");

        window.htmx.process(welTarget[0]);
    }

    /**
     * Get current page number from QueryString
     *
     * @param htData
     * @returns {Number}
     * @private
     */
    function _getPageNumFromUrl(htData){
        const sQuery = getQuery(htData.url);
        const nPageNumFromUrl  = parseInt(valueFromQuery(htData.paramNameForPage, sQuery), 10);
        return nPageNumFromUrl || htData.firstPage;
    }

    /**
     * Get PageNum INPUT element
     *
     * @param htData
     * @returns {Wrapped Element}
     * @private
     */
    function _getPageInputBox(htData){
        const welPageInput = $('<input type="number" pattern="[0-9]*" class="form-control input-mini nospinner">');

        welPageInput.prop({
            "name" : htData.paramNameForPage,
            "max"  : htData.totalPages,
            "min"  : 1
        });

        welPageInput.val(htData.current);

        welPageInput.on("keydown", (weEvt) =>{
            if(!isValidInputNum(welPageInput, htData.current)){
                return;
            }

            const nCurrentValue = welPageInput.val();

            if(typeof htData.submit === "function"){
                htData.submit(nCurrentValue);
            } else if($yona.isEnterKey(weEvt)){
                document.location.href = urlWithPageNum(htData.url, nCurrentValue, htData.paramNameForPage);
            }
        });

        return welPageInput;
    }

    /**
     * Get previous page link
     *
     * @param htData
     * @returns {Wrapped Element}
     * @private
     */
    function _getPrevPageLink(htData){
        const sLinkText = Messages("button.prevPage") || 'PREV';
        const sLinkHTMLOn = '<i class="ico btn-pg-prev"></i><span>' + sLinkText + '</span>';
        const sLinkHTMLOff = '<i class="ico btn-pg-prev off"></i><span class="off">' + sLinkText + '</span>';

        const htOptions = Object.assign(htData, {
            "bActive"  : htData.hasPrev,
            "sLinkHref": htData.hasPrev ? urlWithPageNum(htData.url, htData.current - 1, htData.paramNameForPage) : "",
            "sLinkHTMLOn"   : sLinkHTMLOn,
            "sLinkHTMLOff"  : sLinkHTMLOff,
            "sShortcutKey"  : "LEFT",
            "nSubmitPageNum": htData.current - 1
        });

        const welPagePrev = _buildPageLink(htOptions);

        return welPagePrev;
    }

    /**
     * Get next page link
     *
     * @param htData
     * @returns {Wrapped Element}
     * @private
     */
    function _getNextPageLink(htData){
        const sLinkText = Messages("button.nextPage") || 'NEXT';
        const sLinkHTMLOn = '<span>' + sLinkText + '</span><i class="ico btn-pg-next"></i>';
        const sLinkHTMLOff = '<span class="off">' + sLinkText + '</span><i class="ico btn-pg-next off"></i>';

        const htOptions = Object.assign(htData, {
            "bActive"  : htData.hasNext,
            "sLinkHref": htData.hasNext ? urlWithPageNum(htData.url, htData.current + 1, htData.paramNameForPage) : "",
            "sLinkHTMLOn"   : sLinkHTMLOn,
            "sLinkHTMLOff"  : sLinkHTMLOff,
            "sShortcutKey"  : "RIGHT",
            "nSubmitPageNum": htData.current + 1
        });

        const welPageNext = _buildPageLink(htOptions);

        return welPageNext;
    }

    /**
     * Build prev/next page link
     *
     * @param htData
     * @returns {Wrapped Element}
     * @private
     */
    function _buildPageLink(htData){
        const welPageLink = $('<li class="page-num ikon">');

        if(htData.bActive){
            const welLink = $('<a list-page>');
            welLink.html(htData.sLinkHTMLOn);

            if(typeof htData.submit === 'function'){
                welLink.attr("href", "javascript: void(0);");
                welLink.on("click", () =>{
                    htData.submit(htData.nSubmitPageNum);
                });
            } else {
                welLink.attr("href", htData.sLinkHref);
            }

            welPageLink.append(welLink);
        } else {
            welPageLink.html(htData.sLinkHTMLOff);
        }

        // if yona.ShortcutKey exists
        if(yona.ShortcutKey){
            const htKeyOpt = {};
            htKeyOpt[htData.sShortcutKey] = htData.sLinkHref;
            yona.ShortcutKey.setKeymapLink(htKeyOpt);
        }

        return welPageLink;
    }

    // validate number range
    function isValidInputNum(welTarget, nCurrentPageNum){
        if(rxDigit.test(welTarget.val()) === false){
            welTarget.val(nCurrentPageNum);
            return false;
        }

        const nVal = parseInt(welTarget.val(), 10);
        const nMin = parseInt(welTarget.attr("min"), 10);
        const nMax = parseInt(welTarget.attr("max"), 10);

        if(nVal < nMin){
            welTarget.val(nMin);
        } else if(nVal > nMax){
            welTarget.val(nMax);
        }
        return true;
    }

    return {
        "update" : updatePagination
    };
})(window, document);

$(document).on('click.pagination.number-api','input[name="pageNum"][type="number"]',function() {
    $(this).select();
});
