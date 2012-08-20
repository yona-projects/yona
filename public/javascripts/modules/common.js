nforge.namespace('common');

nforge.Pagination = function () {
  var that,
    $pagination,
    pageInfo,
    callbackFn;

  that = {
    init : function (paginationId, aaa) {
      $pagination = $('#'+paginationId);
      $pagination.find('a').click(that.goPage);
      pageInfo = {currentPageNum : $pagination.data('currentPageNum')};
      return that;
    },

    goPage : function(e) {
      nforge.stopEvent(e);
      var _self = $(this),
        $parent = _self.closest('li');

      if($parent.hasClass('disabled')){
        return false;
      }
      pageInfo.pageNum = _self.data('pageNum');
      nforge.callback(callbackFn, pageInfo);
    },

    setCallback : function(callback) {
      callbackFn = callback;
    }
  };

  return that;
};