/**
 * @(#)yobi.Pagination 2013.03.21
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */
// Render pagination in the given target HTML element.
// Usage: Pagiation.updatePagination(target, totalPages);
// For more details, see docs/technical/pagination.md
/**
 * TODO: 무한 스크롤 구현을 할 게 아니라면
 * 굳이 페이징 링크를 굳이 동적으로 만들어야 할까? 개선 검토 필요
 */
yobi.Pagination = (function(window, document) {
	var htRegEx = {};
	var rxDigit = /^.[0-9]*$/;
	
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
			parser.search = '?' + query;
			
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
	 * window.updatePagination
	 */
	window.updatePagination = function(target, totalPages, options) {
		if (totalPages <= 0){
			return;
		}

		var target = $(target);
		var linkToPrev, linkToNext, urlToPrevPage, urlToNextPage;
		var options = options || {};
		
		options.url = options.url || document.URL;
		options.firstPage = options.firstPage || 1;

		var pageNumFromUrl;
		var paramNameForPage = options.paramNameForPage || 'pageNum';

		if (!$.isNumeric(options.current)) {
			query = getQuery(options.url);
			pageNumFromUrl  = parseInt(valueFromQuery(paramNameForPage, query));
			options.current = pageNumFromUrl || options.firstPage;
		}

		options.hasPrev = (typeof options.hasPrev == "undefined") ? options.current > options.firstPage : options.hasPrev;
		options.hasNext = (typeof options.hasNext == "undefined") ? options.current < totalPages : options.hasNext;

		validateOptions(options);

		target.html('');
		target.addClass('page-navigation-wrap');

		// previous page exists
		var welPagePrev;
		if (options.hasPrev) {
			linkToPrev = $('<a pjax-page>').append($('<i class="ico btn-pg-prev">')).append($('<span>').text('PREV'));

			if (typeof (options.submit) == 'function') {
				linkToPrev.attr('href', 'javascript: void(0);').click(function(e) {
						options.submit(options.current - 1);
				});
			} else {
				urlToPrevPage = urlWithPageNum(options.url, options.current - 1, paramNameForPage);
				linkToPrev.attr('href', urlToPrevPage);
			}

			welPagePrev = $('<li class="page-num ikon">').append(linkToPrev);
		} else {
			welPagePrev = $('<li class="page-num ikon">').append($('<i class="ico btn-pg-prev off">')).append($('<span class="off">').text('PREV'));
		}

		// on submit event handler
		if (typeof (options.submit) == 'function') {
			var keydownOnInput = function(e) {
			    if(validateInputNum($(target), options.current)){
				    options.submit($(target).val());
                }
			};
		} else {
			var keydownOnInput = function(e) {
				var welTarget = $(e.target || e.srcElement);
				if (e.which == 13 && validateInputNum(welTarget, options.current)) {
					document.location.href = urlWithPageNum(options.url, welTarget.val(), paramNameForPage);
				}
			}
		}

		// page input box
		var elInput = $('<input name="pageNum" type="number" pattern="[0-9]*" min="1" max="' + totalPages + '" class="input-mini" value="' + options.current + '">').keydown(keydownOnInput);
		var welPageInputContainer = $('<li class="page-num">').append(elInput);
		var welDelimiter = $('<li class="page-num delimiter">').text('/');
		var welTotalPages = $('<li class="page-num">').text(totalPages);

		// next page exists
		var welPageNext;
		if (options.hasNext) {
			linkToNext = $('<a pjax-page>').append($('<span>').text('NEXT')).append($('<i class="ico btn-pg-next">'));

			if (typeof (options.submit) == 'function') {
				linkToNext.attr('href', 'javascript: void(0);').click(function(e) { options.submit(options.current + 1);});
			} else {
				urlToNextPage = urlWithPageNum(options.url, options.current + 1, paramNameForPage);
				linkToNext.attr('href', urlToNextPage);
			}

			welPageNext = $('<li class="page-num ikon">').append(linkToNext);
		} else {
			welPageNext = $('<li class="page-num ikon">').append($('<span class="off">').text('NEXT').append('<i class="ico btn-pg-next off">'));
		}

		// fill #pagination
		var welPageList = $('<ul class="page-nums">').append([welPagePrev, welPageInputContainer, welDelimiter, welTotalPages, welPageNext]);
		target.append(welPageList);
	};

    // validate number range
    function validateInputNum(welTarget, nCurrentPageNum){
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
