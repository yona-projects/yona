nforge.namespace('milestone');

nforge.milestone.manage = function () {
  return {
    init : function () {
      $('.save').click(this.save);
    },

    save : function (e) {
      var errors = {count : 0},
        $title = $('#title'),
        $contents = $('#contents'),
        $dueDate = $('#dueDate'),
        dueDateValue = $.trim($dueDate.val()),
        isDateMatch = dueDateValue.match(/\d\d\d\d-\d\d-\d\d/);

      if (!$.trim($title.val())) {
        errors['title'] = 'error.required';
        errors['count']++;
      }

      if (!$.trim($contents.val())) {
        errors['contents'] = 'error.required';
        errors['count']++;
      }

      if (!dueDateValue || !isDateMatch) {
        errors['dueDate'] = !dueDateValue ? 'error.required' : 'error.wrong.format';
        errors['count']++;
      }

      $title.next().html(errors['title'] || '');
      $contents.next().html(errors['contents'] || '');
      $dueDate.next().html(errors['dueDate'] || '');

      return (errors['count'] === 0);
    }
  };
};

