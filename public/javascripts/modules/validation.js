nforge.namespace("validation");

// Create a message box in which validation message are shown.
var createMessageBox = function() {
  var div = $('div.alert').empty();

  if (div.length > 0) {
      return div;
  }

  div = $('<div>').addClass('alert alert-error').append($('<a>').addClass('close')
          .attr('data-dismiss', 'alert').text('x'));

  $('.page').before(div);

  return div;
}

/**
 * Create an error handler which shows validation messages for each error using
 * given `getMessage` function.
 *
 * @param {Function} getMessage (error)
 */
var createErrorHandler = function(getMessage) {
  return function(errors, event) {
    var errorMessage, box;

    if (errors.length == 0) {
        return;
    }

    box = createMessageBox();

    for(var i = 0; i < errors.length; i ++) {
      if (getMessage) {
        errorMessage = getMessage(errors[i]);
      } else {
        errorMessage = errors[i].message;
      }

      box.append(
        $('<p>')
          .append($('<strong>').text(Messages('message.warning')))
          .append($('<span>').text(' ' + errorMessage))
      );
    }

    event.returnValue = false;
  };
}

nforge.validation.newProject = function() {
  var that = {
    init: function(formName) {
      var errorMessages = {
        'name': Messages('project.name.alert'),
        'accept': Messages('project.new.agreement.alert')
      };

      new FormValidator(formName, [{
        name: 'name',                // name of input element
        rules: 'required|alpha_dash' // rules to apply to the input element.
      }, {
        name: 'accept',
        rules: 'required'
      }], createErrorHandler(function(e) { // handler for validation errors.
        // callback should return an appropriate message for the given error.
        return errorMessages[e.name];
      }));
    }
  }

  return that;
}
