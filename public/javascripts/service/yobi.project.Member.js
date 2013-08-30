/**
 * @(#)yobi.project.Member.js 2013.03.18
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */

(function(ns){

    var oNS = $yobi.createNamespace(ns);
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
            htVar.oTypeahead = new yobi.ui.Typeahead("#loginId", {
                "sActionURL": "/users"
            });
        }

        /**
         * initialize element variables
         */
        function _initElement(){
            htElement.waBtns = $(".btns");
            htElement.enrollAcceptBtns = $(".enrollAcceptBtn");

            // 멤버 삭제 확인 대화창
            htElement.welAlertDelete = $("#alertDeletion");
            htElement.welBtnConfirmDelete = htElement.welAlertDelete.find(".btnDelete");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.waBtns.click(_onClickBtns);
            htElement.enrollAcceptBtns.click(_onClickEnrollAcceptBtns);
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
         * 멤버 요청 승인 버튼 클릭시 이벤트 핸들러
         * 멤버 추가 폼 서브밋하기
         * @param {Wrapped Event} weEvt
         */
        function _onClickEnrollAcceptBtns(weEvt){
            weEvt.preventDefault();
            var loginId = $(this).attr('data-loginId');
            $('#loginId').val(loginId);
            $('#addNewMember').submit();
        }

        /**
         * 멤버 삭제 버튼 클릭시
         * @param {Wrapped Element} weltArget
         */
        function _onClickDelete(welTarget){
            var sURL = welTarget.attr("data-href");
            
            // DELETE 메소드로 AJAX 호출
            $("#deleteBtn").click(function(){
                $.ajax(sURL, {
                    "method"  : "delete",
                    "dataType": "html",
                    "success" : _onSuccessDeleteMember,
                    "error"   : _onErrorDeleteMember
                });
            });
            
            _showConfirmDeleteMember(sURL);
        }
        
        /**
         * 멤버 삭제 요청이 성공했을때
         */
        function _onSuccessDeleteMemeber(){
            document.location.reload();
        }
        
        /**
         * 멤버 삭제 요청이 실패했을때
         * @param {Object} oXHR
         */
        function _onErrorDeleteMember(oXHR){
            var sErrorMsg;
            
            switch(oXHR.status){
                case 403: // 삭제하려는 멤버가 관리자이거나 권한없음
                    var sNeedle = Messages("project.member.ownerCannotLeave");
                    sErrorMsg = (oXHR.responseText.indexOf(sNeedle) > -1) ? sNeedle : Messages("error.forbidden");
                    break;
                    
                case 404: // 프로젝트 찾을 수 없음
                    sErrorMsg = Messages("project.is.empty");
                    break;
                    
                default:  // 그 이외의 기본 오류
                    sErrorMsg = Messages("error.badrequest");
                    break;
            }
            
            $yobi.alert(sErrorMsg);
        }

        /**
         * 멤버 삭제 확인 표시
         * @param {String} sURL
         */
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
                //console.log("cannot find Role Id");
                return false;
            }

            // send request
            $yobi.sendForm({
                "sURL"   : sURL,
                "htData" : {"id": sRoleId},
                "fOnLoad": function(){
                    document.location.reload();
                }
            });
        }

        _init(htOptions);
    };

})("yobi.project.Member");
