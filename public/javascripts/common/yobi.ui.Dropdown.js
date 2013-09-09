/**
 * @(#)yobi.ui.Dropdown.js 2013.04.11
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */

/**
 * bootstrap-dropdown.js 에서는 단순히 목록 토글 기능만 제공하므로
 * yobi.Dropdown 로 해당 영역을 지정하면 <select> 의 기능을 하도록 만든다
 * 
 * @example 
 * var oSelect = new yobi.Dropdown({
 *     "elContainer": ".btn-group",
 *     "fOnChange"  : function(){},
 *     "
 * });
 * 
 * @require bootstrap-dropdown.js
 */
(function(ns){
	
	var oNS = $yobi.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){

		var htVar = {"sValue":""};
		var htElement = {};
		
		/**
		 * 초기화
		 */
		function _init(htOptions){
			_initElement(htOptions);
			_attachEvent();
	
			htVar.fOnChange = htOptions.fOnChange;
			
			_selectDefault();
		}
		
		/**
		 * 엘리먼트 변수
		 */
		function _initElement(htOptions){
			htElement.welContainer = $(htOptions.elContainer);
			htElement.welSelectedLabel = htElement.welContainer.find(".d-label");
			htElement.waItems = htElement.welContainer.find(".dropdown-menu li");
		}
		
		/**
		 * 이벤트 처리
		 */
		function _attachEvent(){
			// 동적 list 추가 삭제 처리를 위한 event delegation
			// 각 <li> 항목에 이벤트 핸들러를 설정하는 것 보다 
			// ul.dropdown-menu 전체에 설정하는 것이 메모리 절약
			
			htElement.welContainer.on('click', '.dropdown-menu', _onClickItem);
		}
	
		/**
		 * 항목 선택시 이벤트 핸들러
		 * 
		 * @param {Wrapped Event} weEvt
		 */
		function _onClickItem(weEvt){
		    // set welTarget to <li> item
		    var welCurrent = $(weEvt.target);
			var welTarget = (weEvt.target.tagName === "LI") ? welCurrent : $(welCurrent.parents("li")[0]);

			// if click disabled item
			if(welTarget.hasClass("disabled")){
			    weEvt.stopPropagation();
			    weEvt.preventDefault();
			    return false;
			}

			_setItemSelected(welTarget); // display
			_setFormValue(welTarget);    // set form value
			_onChange(); // fireEvent
		}
		
		/**
		 * 선택한 항목을 선택된 상태로 보이게 만드는 함수
		 * @param {Wrapped Element} welTarget
		 */
		function _setItemSelected(welTarget){
			htElement.welSelectedLabel.html(welTarget.html());
			htElement.waItems.removeClass("active");
			welTarget.addClass("active");		
		}
		
		/**
		 * 선택한 항목의 값을 input (type=hidden) 형태로 실제 폼 엘리먼트 값으로 반영
		 * @param {Wrapped Element} welTarget
		 */
		function _setFormValue(welTarget){
			var sFieldValue = welTarget.attr("data-value");
			var sFieldName	= htElement.welContainer.attr("data-name");
			var welInput	= htElement.welContainer.find("input[name='" + sFieldName +"']");
			htVar.sValue = sFieldValue;  
			
			if(typeof sFieldName == "undefined"){
				return;
			}
	
			if(welInput.length === 0){
				welInput = $('<input type="hidden" name="' + sFieldName + '">');
				htElement.welContainer.append(welInput);
			}
			
			welInput.val(sFieldValue);
		}
		
		/**
		 * 항목 값이 변경되면 실행될 함수 
		 */
		function _onChange(){				
			if(typeof htVar.fOnChange == "function"){
				setTimeout(function(){
					htVar.fOnChange(_getValue());
				}, 0);
			}
		}
	
		/**
		 * 항목 값이 변경되면 실행할 함수 지정
		 * @param {Function} fOnChange
		 */
		function _setOnChange(fOnChange){
			htVar.fOnChange = fOnChange;
			return true;
		}

		/**
		 * 현재 선택된 값을 반환
		 * @return {String}
		 */
		function _getValue(){
			return htVar.sValue;
		}

		/**
		 * 기본값 지정이 있으면 선택된 상태로 만들기
		 */
		function _selectDefault(){
			return _selectItem("li[data-selected=true]");
		}

		/**
		 * 지정한 값을 data-value 로 가진 항목을 선택 상태로 만듬
		 * @param {String} sValue
		 */
		function _selectByValue(sValue){
			return _selectItem("li[data-value='" + sValue + "']");
		}

		/**
		 * 지정한 항목을 선택 상태로 만듬
		 * @param {String} sQuery 항목 선택 셀렉터 구문
		 */
		function _selectItem(sQuery){
			var waFind = htElement.welContainer.find(sQuery);
			if(waFind.length <= 0){
				return false; // no item matches
			}
			
			var welTarget = $(waFind[0]);
			_setItemSelected(welTarget);
			_setFormValue(welTarget);

			return true;
		}
		
		_init(htOptions);

		return {
			"getValue": _getValue,
			"onChange": _setOnChange,
			"selectByValue": _selectByValue,
			"selectItem"   : _selectItem
		};
	};

})("yobi.ui.Dropdown");

