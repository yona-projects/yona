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
            
            htVar.oTagInput = new hive.ui.Typeahead(htElement.welInputAddTag, {
            	"sActionURL": htVar.sURLTags
            });
		}

        /**
		 * attach event handlers
		 */
		function _attachEvent(){
			htElement.welInputLogo.change(_onChangeLogoPath);
			htElement.welBtnDeletePrj.click(_onClickBtnDeletePrj);
			htElement.welBtnSave.click(_onClickBtnSave);
            htElement.welInputAddTag.keypress(_onKeyPressNewTag);
//                .typeahead().data('typeahead').source = _tagTypeaheadSource;
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
        * Submit new tag to add that.
        */
        function _submitTag () {
        	$hive.sendForm({
        		"sURL"   : htVar.sURLProjectTags,
        		"htData" : {"name": htElement.welInputAddTag.val()},
        		"fOnLoad": _appendTags
        	});
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
        	$hive.sendForm({
        		"sURL"     : htVar.sURLProjectTags,
        		"htOptForm": {"method":"get"},
        		"fOnLoad"  : _appendTags
        	});
        }

        /**
        * Make a tag element by given id and name.

        * @param {String} sId
        * @param {String} sName
        */
        function _createTag(sId, sName) {
            var fOnClickDelete = function() {
            	$hive.sendForm({
            		"sURL"   : htVar.sURLProjectTags + '/' + sId,
            		"htData" : {"_method":"DELETE"},
            		"fOnLoad": function(){
            			welTag.remove();
            		}
            	});            	
            };

            var welTag = $('<span class="label label-info">' + sName + " </span>")
            	.append($('<a href="javascript:void(0)">x</a>')
            	.click(fOnClickDelete));

            return welTag;
        }

        /**
        * Append the given tags on #tags div to show them.
        *
        * @param {Object} htTags
        */
        function _appendTags(htTags) {
            for(var sId in htTags) {
                htElement.welTags.append(_createTag(sId, htTags[sId]));
            }
        }

		_init(htOptions);
	};
	
})("hive.project.Setting");
