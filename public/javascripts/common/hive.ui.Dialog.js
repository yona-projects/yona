/**
 * @(#)hive.ui.Dialog.js 2013.04.22
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

/**
 * bootstrap-modal.js 에서 제공하는 수동 호출 기능을
 * 사용하기 간편하게 하기 위해 작성한 공통 인터페이스
 * 대화창 레이어 내부에 존재하는 .msg 엘리먼트에
 * 지정한 메시지를 표시하는 기능이 추가되어 있음
 *   
 * @example 
 * var oDialog = new hive.ui.Dialog("#hiveDialog")
 * oDialog.show("메시지");
 * 
 * @require bootstrap-modal.js
 */
(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(sContainer, htOptions){

		var htVar = {};
		var htElement = {};
		
		/**
		 * 초기화
		 * @param {String} sContainer
		 * @param {Hash Table} htOptions
		 */
		function _init(sContainer, htOptions){
			_initElement(sContainer);
			_attachEvent();
		}
		
		/**
		 * 엘리먼트 초기화
		 * @param {String} sContainer
		 */
		function _initElement(sContainer){
			htElement.welContainer = $(sContainer);
			htElement.welMessage = htElement.welContainer.find(".msg");
			htElement.welContainer.modal({
				"show": false
			});
		}
		
		/**
		 * 이벤트 설정
		 */
		function _attachEvent(){
			htElement.welContainer.on("hidden", _onHiddenDialog);
		}
		
		/**
		 * 메시지 출력
		 * @param {String} sMessage
		 */
		function showDialog(sMessage, fCallback){
			sMessage = sMessage.split("\n").join("<br>"); // nl2br
			htElement.welMessage.html(sMessage);
			htElement.welContainer.modal("show");
			htVar.fCallback = fCallback;
		}

		/**
		 * 대화창 닫음
		 */
		function hideDialog(){
			htElement.welContainer.modal("hide");
		}
		
		/**
		 * 대화창 닫고 난 뒤 이벤트 핸들러
		 * 콜백함수 지정되어 있으면 실행
		 */
		function _onHiddenDialog(){
			htElement.welMessage.html("");
			
			if(typeof htVar.fCallback == "function"){
				htVar.fCallback();
				htVar.fCallback = null; 
			}
		}
		
		// 초기화
		_init(sContainer, htOptions || {});
		
		// 인터페이스
		return {
			"show": showDialog,
			"hide": hideDialog
		};
	};

})("hive.ui.Dialog");