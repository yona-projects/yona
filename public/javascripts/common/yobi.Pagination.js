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

yobi.Pagination = (function(window, document) {
    var htRegEx = {};
    var rxDigit = /^.[0-9]*$/;
    // $.isNumeric determines hex, point or negative numbers as numeric.
    // but, rxDigit finds only positive decimal integer numbers

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
        if (!$.isNumeric(options.current)) {
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

        var welTarget = $(elTarget);
        var htData = htOptions || {};

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
        var welPagePrev = _getPrevPageLink(htData);
        var welPageNext = _getNextPageLink(htData);

        // page input box
        var welPageInput = _getPageInputBox(htData);
        var welPageInputWrap = $('<li class="page-num">').append(welPageInput);
        var welDelimiter = $('<li class="page-num delimiter">').text('/');
        var welTotalPages = $('<li class="page-num">').text(nTotalPages);

        // fill #pagination
        var welPageList = $('<ul class="page-nums">');
        welPageList.append([welPagePrev, welPageInputWrap, welDelimiter, welTotalPages, welPageNext]);
        welTarget.append(welPageList);
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
     * @returns {Wrapped Element}
     * @private
     */
    function _getPageInputBox(htData){
        var welPageInput = $('<input type="number" pattern="[0-9]*" class="input-mini nospinner">');

        welPageInput.prop({
            "name" : htData.paramNameForPage,
            "max"  : htData.totalPages,
            "min"  : 1
        });

        welPageInput.val(htData.current);

        welPageInput.on("keydown", function(weEvt){
            if(!isValidInputNum(welPageInput, htData.current)){
                return;
            }

            var nCurrentValue = welPageInput.val();

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
     * @returns {Wrapped Element}
     * @private
     */
    function _getPrevPageLink(htData){
        var sLinkText = Messages("button.prevPage") || 'PREV';
        var sLinkHTMLOn = '<i class="ico btn-pg-prev"></i><span>' + sLinkText + '</span>';
        var sLinkHTMLOff = '<i class="ico btn-pg-prev off"></i><span class="off">' + sLinkText + '</span>';

        var htOptions = $.extend(htData, {
            "bActive"  : htData.hasPrev,
            "sLinkHref": htData.hasPrev ? urlWithPageNum(htData.url, htData.current - 1, htData.paramNameForPage) : "",
            "sLinkHTMLOn"   : sLinkHTMLOn,
            "sLinkHTMLOff"  : sLinkHTMLOff,
            "sShortcutKey"  : "A",
            "nSubmitPageNum": htData.current - 1
        });

        var welPagePrev = _buildPageLink(htOptions);

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
        var sLinkText = Messages("button.nextPage") || 'NEXT';
        var sLinkHTMLOn = '<span>' + sLinkText + '</span><i class="ico btn-pg-next"></i>';
        var sLinkHTMLOff = '<span class="off">' + sLinkText + '</span><i class="ico btn-pg-next off"></i>';

        var htOptions = $.extend(htData, {
            "bActive"  : htData.hasNext,
            "sLinkHref": htData.hasNext ? urlWithPageNum(htData.url, htData.current + 1, htData.paramNameForPage) : "",
            "sLinkHTMLOn"   : sLinkHTMLOn,
            "sLinkHTMLOff"  : sLinkHTMLOff,
            "sShortcutKey"  : "S",
            "nSubmitPageNum": htData.current + 1
        });

        var welPageNext = _buildPageLink(htOptions);

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
        var welPageLink = $('<li class="page-num ikon">');

        if(htData.bActive){
            var welLink = $('<a pjax-page>');
            welLink.html(htData.sLinkHTMLOn);

            if(typeof htData.submit === 'function'){
                welLink.attr("href", "javascript: void(0);");
                welLink.on("click", function(){
                    htData.submit(htData.nSubmitPageNum);
                });
            } else {
                welLink.attr("href", htData.sLinkHref);
            }

            welPageLink.append(welLink);
        } else {
            welPageLink.html(htData.sLinkHTMLOff);
        }

        // if yobi.ShortcutKey exists
        if(yobi.ShortcutKey){
            var htKeyOpt = {};
            htKeyOpt[htData.sShortcutKey] = htData.sLinkHref;
            yobi.ShortcutKey.setKeymapLink(htKeyOpt);
        }

        return welPageLink;
    }

    // validate number range
    function isValidInputNum(welTarget, nCurrentPageNum){
        if(rxDigit.test(welTarget.val()) === false){
            welTarget.val(nCurrentPageNum);
            return false;
        }

        var nVal = parseInt(welTarget.val(), 10);
        var nMin = parseInt(welTarget.attr("min"), 10);
        var nMax = parseInt(welTarget.attr("max"), 10);

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
