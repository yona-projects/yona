/**
 * @(#)hive.project.Setting.js 2013.03.18
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){
		
		var htVar = {};
		var htElement = {};
		
		/**
		 * initialize
		 */
		function _init(htOptions){
			var htOpt = htOptions || {};
			_initVar(htOpt);
			_initElement(htOpt);
			_attachEvent();
            _updateTags();
			
			htVar.waPopOvers.popover();
		}
		
		/**
		 * initialize variables
		 * 정규식 변수는 한번만 선언하는게 성능 향상에 도움이 됩니다
		 */
		function _initVar(htOptions){
			htVar.rxLogoExt = /\.(gif|bmp|jpg|jpeg|png)$/i;
			htVar.rxPrjName = /^[a-zA-Z0-9_][-a-zA-Z0-9_]+[^-]$/;
            htVar.sURLProjectTags = htOptions.sURLProjectTags;
            htVar.sURLTags = htOptions.sURLTags;
		}

		/**
		 * initialize element variables
		 */
		function _initElement(htOptions){
			// 프로젝트 설정 관련
			htElement.welForm = $("form#saveSetting");
			htElement.welInputLogo = $("#logoPath");
			htElement.welAlertLogo = $("#logoTypeAlert");
			htElement.welInputName = $("input#project-name")
			htElement.welAlertName = $("#alert_msg");		

			htElement.welBtnSave   = $("#save");
			
			// 프로젝트 삭제 관련
			// TODO: 삭제는 별도 페이지로 이동 예정 hive.project.Delete.js
			htElement.welAlertAccept  = $("#acceptAlert");
			htElement.welChkAccept    = $("#accept");			
			htElement.welBtnDeletePrj = $("#deletion");
			
			// popovers
			htVar.waPopOvers = $([$("#project_name"), $("#share_option_explanation"), $("#terms")]);

            // tags
            htElement.welInputAddTag = $('input[name="newTag"]');
            htElement.welTags = $('#tags');
            htElement.welBtnAddTag = $('#addTag');
		}

        /**
		 * attach event handlers
		 */
		function _attachEvent(){
			htElement.welInputLogo.change(_onChangeLogoPath);
			htElement.welBtnDeletePrj.click(_onClickBtnDeletePrj);
			htElement.welBtnSave.click(_onClickBtnSave);
            htElement.welInputAddTag
                .keypress(_onKeyPressNewTag)
                .typeahead().data('typeahead').source = _tagTypeaheadSource;
            htElement.welBtnAddTag.click(_submitTag);
		}
		
		/**
		 * 프로젝트 로고 변경시 이벤트 핸들러
		 */
		function _onChangeLogoPath(){
			var welTarget = $(this);
			
			// 확장자 규칙 검사
			if(!htVar.rxLogoExt.text(welTarget.val())){
				htElement.welAlertLogo.show();
				welTarget.val('');
				return;
			}
			htElement.welAlertLogo.hide();
			
			return htElement.welForm.submit();
		}
		
		/**
		 * 프로젝트 삭제 버튼 클릭시 이벤트 핸들러
		 * 데이터 영구 삭제 동의에 체크했는지 확인하고
		 * 체크되지 않았으면 경고 레이어 표시
		 */
		function _onClickBtnDeletePrj(){
			var bChecked = htElement.welChkAccept.is(":checked");
			var sMethod = bChecked ? "hide" : "show";
			htElement.welAlertAccept[sMethod]();
			return bChecked;
		}
		
		/**
		 * 프로젝트 설정 저장 버튼 클릭시
		 */
		function _onClickBtnSave(){
			if(!htVar.rxPrjName.test(htElement.welInputLogo.val())){
				htElement.welAlertName.show();
				return false;
			}
			
			htElement.welAlertName.hide();
			return true;
		}

        /**
        * Data source for tag typeahead while adding new tag.
        *
        * For more information, See "source" option at
        * http://twitter.github.io/bootstrap/javascript.html#typeahead
        *
        * @param {String} query
        * @param {Function} process
        */
        function _tagTypeaheadSource(query, process) {
            if (query.match(htVar.lastQuery) && htVar.isLastRangeEntire) {
                process(htVar.cachedTags);
            } else {
                $('<form method="GET">')
                    .attr('action', htVar.sURLTags)
                    .append($('<input type="hidden" name="query">').val(query))
                    .ajaxForm({
                        "dataType": "json",
                        "success": function(tags, status, xhr) {
                            var tagNames = [];
                            for(var id in tags) {
                                tagNames.push(tags[id]);
                            }
                            htVar.isLastRangeEntire = $hive.isEntireRange(
                                xhr.getResponseHeader('Content-Range'));
                            htVar.lastQuery = query;
                            htVar.cachedTags = tagNames;
                            process(tagNames);
                        }
                    }).submit();
            }
        };

        /**
        * Submit new tag to add that.
        */
        function _submitTag () {
            $('<form method="POST">')
                .attr('action', htVar.sURLProjectTags)
                .append($('<input type="hidden" name="name">')
                        .val(htElement.welInputAddTag.val()))
                .ajaxForm({ "success": _appendTags })
                .submit();
        }

        /**
        * If user presses enter at newtag element, get list of tags from the
        * server and show them in #tags div.
        *
        * @param {Object} oEvent
        */
        function _onKeyPressNewTag(oEvent) {
            if (oEvent.keyCode == 13) {
                _submitTag();
                htElement.welInputAddTag.val("");
                return false;
            }
        }

        /**
        * Get list of tags from the server and show them in #tags div.
        */
        function _updateTags() {
            $('<form method="GET">')
                .attr('action', htVar.sURLProjectTags)
                .ajaxForm({
                    "dataType": "json",
                    "success": _appendTags
                }).submit();
        }

        /**
        * Make a tag element by given id and name.

        * @param {String} sId
        * @param {String} sName
        */
        function _createTag(sId, sName) {
            var fDelTag = function(ev) {
                $('<form method="POST">')
                    .attr('action', htVar.sURLProjectTags + '/' + sId)
                    .append($('<input type="hidden" name="_method" value="DELETE">'))
                    .ajaxForm({
                        "success": function(data, status, xhr) {
                            welTag.remove();
                        }
                    }).submit();
            };

            var welTag = $("<span class='label label-info'>")
                .text(sName + " ")
                .append($("<a href='javascript:void(0)'>").text("x").click(fDelTag));

            return welTag;
        };

        /**
        * Append the given tags on #tags div to show them.
        *
        * @param {Object} htTags
        */
        function _appendTags(htTags) {
            for(var sId in htTags) {
                htElement.welTags.append(_createTag(sId, htTags[sId]));
            }
        };

		_init(htOptions);
	};
	
})("hive.project.Setting");

/*
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
*/