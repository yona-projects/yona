// Render pagination in the given target HTML element.
//
// Usage: Pagiation.updatePagination(target, totalPages);
//
// For more details, see docs/technical/pagination.md

(function(window, document) {
  var getQuery = function(url) {
     var parser = document.createElement('a');
     parser.href = url;
     return parser.search;
  };

  var valueFromQuery = function(key, query) {
    var regex = new RegExp('(^|&|\\?)' + key + '=([^&]+)');
    var result = regex.exec(query);

    if (result) {
        return result[2];
    } else {
        return null;
    }
  };

  var urlWithQuery = function(url, query) {
     var parser = document.createElement('a');
     parser.href = url;
     parser.search = '?' + query;
     return parser.href;
  };

  var urlWithPageNum = function(url, pageNum, paramNameForPage) {
    var query = getQuery(url);
    var regex = new RegExp('(^|&|\\?)' + paramNameForPage + '=[^&]+');
    var result = regex.exec(query);
    query = query.replace(regex, result[1] + paramNameForPage + '=' + pageNum);

    return urlWithQuery(url, query);
  };

  var validateOptions = function(options) {
    if (!Number.isFinite(options.current)) {
      throw new Error("options.current is not valid: " + options.current);
    }
  };

  window.updatePagination = function(target, totalPages, options) {
    var target = $(target);
    var linkToPrev, linkToNext, urlToPrevPage, urlToNextPage;
    var ul, prev, inputBox, delimiter, total, next, query;
    var keydownOnInput;
    var paramNameForPage;
    var pageNumFromUrl;

    if (totalPages <= 0) return;

    if (options == undefined) options = {};
    if (options.url == undefined) options.url = document.URL;
    if (options.firstPage == undefined) options.firstPage = 1;

    paramNameForPage = options.paramNameForPage || 'pageNum';

    if (!Number.isFinite(options.current)) {
        query = getQuery(options.url);
        pageNumFromUrl = parseInt(valueFromQuery(paramNameForPage, query));
        options.current = pageNumFromUrl || options.firstPage;
    }

    if (options.hasPrev == undefined) {
        options.hasPrev = options.current > options.firstPage;
    }

    if (options.hasNext == undefined) {
        options.hasNext = options.current < totalPages;
    }

    validateOptions(options);

    target.html('');

    target.addClass('page-navigation-wrap');

    if (options.hasPrev) {
      linkToPrev = $('<a>')
          .append($('<i>').addClass('ico').addClass('btn-pg-prev'))
          .append($('<span>').text('PREV'));

      if (typeof(options.submit) == 'function') {
        linkToPrev
            .attr('href', 'javascript: void(0);')
            .click(function(e) { options.submit(options.current - 1); });
      } else {
        urlToPrevPage = urlWithPageNum(options.url, options.current - 1, paramNameForPage);
        linkToPrev.attr('href', urlToPrevPage);
      }

      prev = $('<li>')
          .addClass('page-num')
          .addClass('ikon')
          .append(linkToPrev);
    } else {
      prev = $('<li>')
          .addClass('page-num')
          .addClass('ikon')
          .append($('<i>').addClass('ico').addClass('btn-pg-prev').addClass('off'))
          .append($('<span>').text('PREV').addClass('off'));
    }

    if (typeof(options.submit) == 'function') {
      keydownOnInput = function(e) { options.submit($(target).val()); };
    } else {
      keydownOnInput = function(e) {
        var target = e.target || e.srcElement;
        if (e.which == 13) {
          location.href = urlWithPageNum(options.url, $(target).val(), paramNameForPage);
        }
      }
    }

    inputBox = $('<li>').addClass('page-num')
        .append($('<input>')
                .attr('name', 'pageNum')
                .attr('type', 'number')
                .attr('min', '1')
                .attr('max', totalPages)
                .addClass('input-mini')
                .val(options.current)
                .keydown(keydownOnInput));
    delimiter = $('<li>').addClass('page-num').text('/');
    total = $('<li>').addClass('page-num').text(totalPages);

    if (options.hasNext) {
      linkToNext = $('<a>')
          .append($('<span>').text('NEXT'))
          .append($('<i>').addClass('ico').addClass('btn-pg-next'));

      if (typeof(options.submit) == 'function') {
        linkToNext
            .attr('href', 'javascript: void(0);')
            .click(function(e) { options.submit(options.current + 1); });
      } else {
        urlToNextPage = urlWithPageNum(options.url, options.current + 1, paramNameForPage);
        linkToNext.attr('href', urlToNextPage);
      }

      next = $('<li>')
          .addClass('page-num')
          .addClass('ikon')
          .append(linkToNext);
    } else {
      next = $('<li>')
          .addClass('page-num')
          .addClass('ikon')
          .append($('<i>').addClass('ico').addClass('btn-pg-next').addClass('off'))
          .append($('<span>').text('NEXT').addClass('off'));
    }

    ul = $('<ul>')
        .addClass('page-nums')
        .append(prev)
        .append(inputBox)
        .append(delimiter)
        .append(total)
        .append(next);

    target.append(ul);
  };

  window.Pagination = {
    update: updatePagination
  }
})(window, document);
