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
        function _init(htOptions){
            _initVar(htOptions);
            _initElement();
            _attachEvent();
        }

        /**
         * initialize variables
         */
        function _initVar(){
            htVar.sActionURL = htOptions.sActionURL;
            htVar.htData = htOptions.htData || {};
            htVar.rxContentRange = /items\s+([0-9]+)\/([0-9]+)/;

            htVar.oTypeahead = new yobi.ui.Typeahead("#loginId", {
                "htData" : {
                    "updater" : _updater,
                    "source" : _source,
                    "render" : _render
                }
            });
        }

        function _render(items) {
            var that = this;

            items = $(items).map(function(i, item) {
                i = $(that.options.item).attr('data-value', item);
                var _linkHtml = $('<div />');
                _linkHtml.append(item);
                i.find('a').html(_linkHtml.html());
                return i[0];
            });

            items.first().addClass('active');
            this.$menu.html(items);
            return this;
        }
        /**
         * typeahead의 updater를 재정의
         * 
         * 사용자목록 선택시 loginId가 반환된다.
         */
        function _updater(item) {
            return htVar.htUserData[item].loginId;
        }

        /**
         * typeahead의 source option을 재정의
         *
         * For more information, See "source" option at
         * http://twitter.github.io/bootstrap/javascript.html#typeahead
         *
         * @param {Function} fProcess
         */
        function _source(sQuery, fProcess) {
            if (sQuery.match(htVar.sLastQuery) && htVar.bIsLastRangeEntire) {
                fProcess(htVar.htCachedUsers);
            } else {
                htVar.htData.query = sQuery;
                $yobi.sendForm({
                    "sURL"        : htVar.sActionURL,
                    "htOptForm"    : {"method":"get"},
                    "htData"    : htVar.htData,
                    "sDataType" : "json",
                    "fOnLoad"    : function(oData, oStatus, oXHR){
                        var sContentRange = oXHR.getResponseHeader('Content-Range');

                        htVar.bIsLastRangeEntire = _isEntireRange(sContentRange);
                        htVar.sLastQuery = sQuery;

                        var userData = {};
                        var userInfos = [];
                        $.each(oData, function (i, user) {
                            userData[user.info] = user;
                            userInfos.push(user.info);
                        });

                        htVar.htUserData = userData;
                        htVar.htCachedUsers = userInfos;
                        fProcess(userInfos);
                        sContentRange = null;
                    }
                });
            }
        }

        /**
         * Return whether the given content range is an entire range for items.
         * e.g) "items 10/10"
         *
         * @param {String} sContentRange the value of Content-Range header from response
         * @return {Boolean}
         */
         function _isEntireRange(sContentRange){
             var aMatch = htVar.rxContentRange.exec(sContentRange || ""); // [1]=total, [2]=items
             return (aMatch) ? !(parseInt(aMatch[1], 10) < parseInt(aMatch[2], 10)) : true;
         }

        /**
         * initialize element variables
         */
        function _initElement(){
            htElement.waBtns = $(".btns");
            htElement.enrollAcceptBtns = $(".enrollAcceptBtn");
            htElement.memberListWrap = $('.members');

            // 멤버 삭제 확인 대화창
            htElement.welAlertDelete = $("#alertDeletion");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.memberListWrap.on('click','[data-action="apply"]',_onClickApply);
            htElement.memberListWrap.on('click','[data-action="delete"]',_onClickDelete);
            
            htElement.enrollAcceptBtns.click(_onClickEnrollAcceptBtns);

            $('#loginId').focus();
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
        function _onClickDelete(){
            var sURL = $(this).attr("data-href");

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
        function _onSuccessDeleteMember(sResult){
            var htData = $.parseJSON(sResult);
            document.location.replace(htData.location);
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
            htElement.welAlertDelete.modal();
        }

        /**
         * 멤버 정보 변경 버튼 클릭시
         * @param {Wrapped Element} welTarget
         */
        function _onClickApply(){
            var sURL = $(this).attr("data-href");
            var sLoginId = $(this).attr("data-loginId");
            var sRoleId = $('input[name="roleof-' + sLoginId + '"]').val();

            if(typeof sRoleId == "undefined"){
                //console.log("cannot find Role Id");
                return false;
            }

            // send request
            $yobi.sendForm({
                "sURL"   : sURL,
                "htData" : {"id": sRoleId}
            });
        }

        _init(htOptions);
    };

})("yobi.project.Member");
