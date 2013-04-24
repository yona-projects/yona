/**
 * @(#)hive.project.Delete.js 2013.04.24
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
			_initElement(htOptions);
			_attachEvent();
		}
		
		/**
		 * initialize element variables
		 */
		function _initElement(htOptions){
			htElement.welChkAccept    = $("#accept");			
			htElement.welBtnDeletePrj = $("#deletion");			
		}

        /**
		 * attach event handlers
		 */
		function _attachEvent(){
			htElement.welBtnDeletePrj.click(_onClickBtnDeletePrj);
		}
		
		/**
		 * 프로젝트 삭제 버튼 클릭시 이벤트 핸들러
		 * 데이터 영구 삭제 동의에 체크했는지 확인하고
		 * 체크되지 않았으면 경고
		 */
		function _onClickBtnDeletePrj(){
			var bChecked = htElement.welChkAccept.is(":checked");
			
			if(bChecked === false){
				$hive.showAlert(Messages("project.delete.alert"));
				return false;
			}
			return true;
		}

		_init(htOptions || {});
	};
	
})("hive.project.Delete");
