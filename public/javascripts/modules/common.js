nforge.namespace('common');

nforge.Pagination = function () {
  var that,
    $pagination,
    $target,
    pageInfo,
    callbackFn;

  that = {
    init : function (paginationId) {
      $pagination = $('#' + paginationId);
      $pagination.find('a').click(that.goPage);
      pageInfo = {currentPageNum : $pagination.data('currentPageNum')};
      return that;
    },

    goPage : function (e) {
      nforge.stopEvent(e);
      var _self = $(this),
        $parent = _self.closest('li');

      if ($parent.hasClass('disabled')) {
        return false;
      }
      pageInfo.pageNum = _self.data('pageNum');
      nforge.callback.apply(that, [callbackFn]);
    },

    setCallback : function (callback) {
      callbackFn = callback;
      return that;
    },

    getPageInfo : function () {
      return pageInfo;
    },

    setUpdateTarget : function ($_target) {
      $target = $_target;
      return that;
    },

    getUpdateTarget : function () {
      return $target;
    },

    updatePaginationBar : function () {
      /* @TODO need design.. */
      return that;
    }
  };

  return that;
};