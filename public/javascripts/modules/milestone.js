nforge.namespace('milestone');

nforge.milestone.manage = function () {
  return {
    init : function () {
      $('.save').click(this.save);
    },

    save : function (e) {
      var errors = {},
        $title = $('#title'),
        $contents = $('#contents'),
        $dueDate = $('#dueDate'),
        dueDateValue = $.trim($dueDate.val()),
        isDateMatch = dueDateValue.match(/\d\d\d\d-\d\d-\d\d/);

      if (!$.trim($title.val())) {
        errors['title'] = 'error.required';
      }

      if (!$.trim($contents.val())) {
        errors['contents'] = 'error.required';
      }

      if (!dueDateValue || !isDateMatch) {
        errors['dueDate'] = !dueDateValue ? 'error.required' : 'error.wrong.format';
      }

      $title.next().html(errors['title'] || '');
      $contents.next().html(errors['contents'] || '');
      $dueDate.next().html(errors['dueDate'] || '');

      return $.isEmptyObject(errors);
    }
  };
};

