/**
 * @(#)yobi.ui.Toast.js 2013.05.14
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */

(function(ns){
	
	var oNS = $yobi.createNamespace(ns);
	oNS.container[oNS.name] = function(sContainer, htOptions){

		var htVar = {};
		var htElement = {};
		
		/**
		 * 초기화
		 * @param {String} sContainer
		 * @param {Hash Table} htOptions
		 */
		function _init(sContainer, htOptions){
			_initVar(htOptions);
			_initElement(sContainer);
		}
		
		/**
		 * 변수 초기화
		 * @param {Hash Table} htOptions
		 */
		function _initVar(htOptions){
			htOptions.sTplToast = htOptions.sTplToast.replace("\n", "");
			htVar.sTplToast = htOptions.sTplToast || '<div class="toast" tabindex="-1">\
			<div class="btn-dismiss"><button type="button" class="btn-transparent">&times;</button></div>\
			<div class="center-text msg"></div></div>';
		}
		
		/**
		 * 엘리먼트 변수
		 * @param {String} sContainer
		 */
		function _initElement(sContainer){
			htElement.welContainer = $(sContainer);
			htElement.welToast = $(htVar.sTplToast);
		}
		
		/**
		 * 토스트 메시지 추가
		 * @param {String} sMessage
		 * @param {Number} nDuration
		 */
		function pushToast(sMessage, nDuration){
			var welToast = _getToast(sMessage);
			htElement.welContainer.prepend(welToast);
			welToast.css("opacity", "1");
			
			if(nDuration && nDuration > 0){
				_fadeOutTimer(welToast, nDuration);
			}
		}
		
		/**
		 * 토스트 메시지 엘리먼트 반환하는 함수
		 * @param {String} sMessage
		 * @return {Wrapped Element}
		 */
		function _getToast(sMessage){
			var welToast = htElement.welToast.clone();
			var welMessage = welToast.find(".msg");
			
			welToast.css("opacity", "0");
            welToast.click(_onClickClose);
			welMessage.html($yobi.nl2br(sMessage));
			
			return welToast;
		}

		/**
		 * 토스트 메시지 클릭시 이벤트 핸들러
		 * transition 사용하지 않고 즉각 삭제
		 */
		function _onClickClose(weEvt){
		    $(this).remove();
		}
		
		/**
		 * 토스트 메시지를 지정한 시간 뒤에 사라지게 만드는 함수
		 * transition 사용하여 서서히 흐려지는 효과
		 * @param {Wrapped Element} welToast 토스트 엘리먼트
		 * @param {Number} nDuration 메시지를 표시할 시간 (ms)
		 */
		function _fadeOutTimer(welToast, nDuration){
			welToast.bind("webkitTransitionEnd", function(){
				welToast.remove();
			});
			setTimeout(function(){
				welToast.css("opacity", 0);
			}, nDuration);
		}
		
		/**
		 * 토스트 메시지 모두 제거
		 */
		function clearToasts(){
			htElement.welContainer.empty();
		} 
		
		// 초기화
		_init(sContainer, htOptions || {});
		
		return {
			"push" : pushToast,
			"clear": clearToasts
		};
	};

})("yobi.ui.Toast");