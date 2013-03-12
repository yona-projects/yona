nforge.namespace("project");

nforge.project.new = function() {
  var that = {
    init: function(formName) {
      var errorMessages = {
        'name': Messages('project.name.alert'),
        'accept': Messages('project.new.agreement.alert')
      };
      new FormValidator(formName, [{
        name: 'name',
        rules: 'required|alpha_dash'
      }, {
        name: 'accept',
        rules: 'required'
      }], function(errors, event) {
        var label;
        var div = $('div.alert').empty();

        if (errors.length == 0) {
            return;
        }

        if (div.length == 0) {
          div = $('<div>');
          div.addClass('alert alert-error');
          div.append($('<a>').addClass('close').attr('data-dismiss', 'alert').text('x'));
          $('div.page').before(div);
        }

        for(var i = 0; i < errors.length; i ++) {
          label =
            $('<label>').attr('for', errors[i].name)
            .append($('<strong>').text(Messages('message.warning')))
            .append($('<span>').text(' ' + errorMessages[errors[i].name]));
          div.append(label);
        }

        event.returnValue = false;
      });
    }
  }

  return that;
}

nforge.project.nameCheck = function() {
	var that = {
		init : function() {
			$("#save").click(function() {
				var reg_name = /^[a-zA-Z0-9_][-a-zA-Z0-9_]+[^-]$/;
				if(!reg_name.test($("input#project-name").val())) {
		            $("#alert_msg").show();
		            return false;
			    } else {
			        $("#alert_msg").hide();
					return true;
			    }
			});
		}
	};
	return that;
};

nforge.project.urlCheck = function() {
	var that = {
		init : function() {
			$("#save").click(function() {
				var reg_url = /^http?:\/\//;

				if($("input#siteurl").val()!="" && !reg_url.test($("input#siteurl").val())) {
          $("#urlAlert").show();
          return false;
        }else {
        	$("#urlAlert").hide();
        	return true;
        }

			});
		}
	};
	return that;
};

nforge.project.acceptCheck = function() {
	var that = {
		init : function(id) {
			$("#"+id).click(function() {
				if($("#accept").is(":not(:checked)")) {
          $("#acceptAlert").show();
          return false;
        }else {
        	$("#acceptAlert").hide();
					return true;	
        }
			});
		}
	};
	return that;	
};

nforge.project.logoCheck = function() {
	var that = {
		init : function() {
			$("#logoPath").change(function(){
				var reg_type = /\.(gif|bmp|jpg|jpeg|png)$/i;
        if (!reg_type.test($(this).val())) {
            $("#logoTypeAlert").show(); 
            $(this).val('');
        } else { 
            return $("form#saveSetting").submit(); 
        }
      });
		}
	};
	return that;
};

nforge.project.popovers = function() {
	var that = {
		init : function() {
			$("#project_name").popover();
            $("#share_option_explanation").popover();
            $("#terms").popover();
		}
	};
	return that;
}

nforge.project.roleChange = function() {
	var that = {
		init : function() {
			$("select#role").change(function(){
        $(this).parent("form").submit();
      });
		}
	};
	return that;
};
