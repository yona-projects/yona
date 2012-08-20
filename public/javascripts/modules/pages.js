nforge.namespace('pages');

nforge.pages.contentSearch = function () {
  var filter,
    issuePagination = nforge.require('Pagination','issue-pagination'),
    postPagination = nforge.require('Pagination','post-pagination');
  return {
    init : function () {
      filter = $('.filter').val();

      issuePagination.setCallback(function(pageInfo){
        console.log(pageInfo);
      });

      postPagination.setCallback(function(pageInfo){
        console.log(pageInfo);
      });
    }
  };
};