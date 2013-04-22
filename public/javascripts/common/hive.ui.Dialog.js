/**
 * @(#)hive.ui.Dialog.js 2013.04.22
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
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
		 * 엘리먼트 변수
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
		
		function _onHiddenDialog(){
			htElement.welMessage.html("");
			
			if(typeof htVar.fCallback == "function"){
				htVar.fCallback();
				htVar.fCallback = null; 
			}
		}
		
		// 초기화
		_init(sContainer, htOptions || {});
		
		return {
			"show": showDialog,
			"hide": hideDialog
		};
	};

})("hive.ui.Dialog");