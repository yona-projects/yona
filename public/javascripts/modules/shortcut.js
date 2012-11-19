nforge.namespace('shortcut');

// ctrl + enter to submit a form
nforge.shortcut.submit = function () {
  var that;

  that = {
    init : function () {
      var eventHandler = function (event) {
        if (event.ctrlKey && event.which == 13) {
          $($(event.target).parents('form').get(0)).submit();
        }
      }

      $('textarea').keydown(eventHandler);
      $('input').keydown(eventHandler);
    }
  };

  return that;
};
