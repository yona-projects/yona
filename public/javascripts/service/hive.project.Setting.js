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
			
			htVar.waPopOvers.popover();
		}
		
		/**
		 * initialize variables
		 * 정규식 변수는 한번만 선언하는게 성능 향상에 도움이 됩니다
		 */
		function _initVar(htOptions){
			htVar.rxLogoExt = /\.(gif|bmp|jpg|jpeg|png)$/i;
			htVar.rxPrjName = /^[a-zA-Z0-9_][-a-zA-Z0-9_]+[^-]$/;
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
		}

		/**
		 * attach event handlers
		 */
		function _attachEvent(){
			htElement.welInputLogo.change(_onChangeLogoPath);
			htElement.welBtnDeletePrj.click(_onClickBtnDeletePrj);
			htElement.welBtnSave.click(_onClickBtnSave);
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

		_init(htOptions);
	};
	
})("hive.project.Setting");
