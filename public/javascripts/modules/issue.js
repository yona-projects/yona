nforge.namespace('issue');
nforge.issue.list = function () {
  var that,
    $checkboxForm,
    $searchForm,
    $pagination;
  that = {
    init : function () {
      $checkboxForm = $("#checkboxForm");
      $searchForm = $("#searchForm");
      $pagination = $("#pagination");
      that.setEvent();
    },

    setEvent : function () {
      $checkboxForm.find('.filters').click(that.filter);
      $('.th-sort').click(that.search);

      $("#milestone").change(function () {
        $searchForm.submit();
      });

      /* TODO */
      $pagination.click(function () {
        console.log('TBD..');
      });
    },

    filter : function (e) {
      setTimeout(function () {
        $checkboxForm.submit();
      }, 1);
    },

    search : function (e) {
      nforge.stopEvent(e);
      var _self = $(this),
        sortBy = _self.data('sortBy'),
        $hiddenInputs = $searchForm.find('h-value'),
        $sortInput = $hiddenInputs.filter('.sort'),
        $orderInput = $hiddenInputs.filter('.order'),
        orderVal = $orderInput.val();

      if ($sortInput.val() !== sortBy) {
        $sortInput.val(sortBy);
      } else {
        $orderInput.val(orderVal === 'asc' ? 'desc' : orderVal);
      }
      $searchForm.submit();
    }
  };
  return that;
};