/**
 * @(#)hive.project.Member.js 2013.03.18
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
		function _init(){
			_initVar();
			_initElement();
			_attachEvent();
		}
		
		/**
		 * initialize variables
		 */
		function _initVar(){
			htVar.oTypeahead = new hive.ui.Typeahead("#loginId", {
				"sActionURL": "/users"
			});
		}
		
		/**
		 * initialize element variables
		 */
		function _initElement(){
			htElement.waBtns = $(".btns");
			
			// 멤버 삭제 확인 대화창
			htElement.welAlertDelete = $("#alertDeletion");
			htElement.welBtnConfirmDelete = htElement.welAlertDelete.find(".btnDelete");
		}
		
		/**
		 * attach event handlers
		 */
		function _attachEvent(){
			htElement.waBtns.click(_onClickBtns);
		}

		/**
		 * 각 멤버별 버튼 클릭시 이벤트 핸들러
		 * data-action 속성을 바탕으로 분기
		 * @param {Wrapped Event} weEvt
		 */
		function _onClickBtns(weEvt){
			var welTarget = $(weEvt.target);
			if(!welTarget.attr("data-action")){ // in case of <i class="ico">
				welTarget = $(welTarget.parent("[data-action]"));
			}
			
			var sAction = welTarget.attr("data-action").toLowerCase();

			switch(sAction){
				case "delete":
					_onClickDelete(welTarget);
					break;
				case "apply":
					_onClickApply(welTarget);
					break;
			}
			return;
		}
		
		/**
		 * 멤버 삭제 버튼 클릭시
		 * @param {Wrapped Element} weltArget
		 */
		function _onClickDelete(welTarget){
			var sURL = welTarget.attr("data-href");
			_showConfirmDeleteMember(sURL);
		}
		
		function _showConfirmDeleteMember(sURL){
			htElement.welBtnConfirmDelete.attr("href", sURL);
			htElement.welAlertDelete.modal();
		}
		
		/**
		 * 멤버 정보 변경 버튼 클릭시
		 * @param {Wrapped Element} welTarget
		 */
		function _onClickApply(welTarget){
			var sURL = welTarget.attr("data-href");
			var sLoginId = welTarget.attr("data-loginId");
			var sRoleId = $('input[name="roleof-' + sLoginId + '"]').val();
			
			if(typeof sRoleId == "undefined"){
				console.log("cannot find Role Id");
				return false;
			}
			
			// send request
			$hive.sendForm({
				"sURL"   : sURL,
				"htData" : {"id": sRoleId},
				"fOnLoad": function(){
					document.location.reload();
				}
			});
		}

		_init(htOptions);
	};
	
})("hive.project.Member");
