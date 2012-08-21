nforge.namespace('pages');

nforge.pages.contentSearch = function () {
  var filter,
    $searchForm,
    issuePagination = nforge.require('Pagination', 'issue-pagination'),
    postPagination = nforge.require('Pagination', 'post-pagination'),
    paginationCallback = function () {
      var _self = this,
        $target = _self.getUpdateTarget(),
        params = {
          filter : filter,
          page   : _self.getPageInfo().pageNum,
          type   : $target.data('type')
        };

      $.get($searchForm.attr('action'), params, function (res) {
        _self.updatePaginationBar();
        $target.html(res);
      });
    };

  return {
    init : function () {
      filter = $('.filter').val();
      $searchForm = $('#contentsSearchForm');

      issuePagination.setCallback(paginationCallback)
        .setUpdateTarget($('.issue-tbody').data('type', 'issue'));

      postPagination.setCallback(paginationCallback)
        .setUpdateTarget($('.post-tbody').data('type', 'post'));
    }
  };
};