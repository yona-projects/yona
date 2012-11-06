nforge.namespace("project");

nforge.project.nameCheck = function() {
	var that = {
		init : function() {
			$("#save").click(function() {
				var reg_name = /^[a-zA-Z0-9_]+$/;
				if(!reg_name.test($("input#name").val())) {
			        $("#nameAlert").show();
			        return false;
			    }else {
			    	$("#nameAlert").hide();
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