/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Changsung Kim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
                    "minLength" : 1,
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
        function _updater(item) {
            return htVar.htUserData[item].loginId;
        }

        /**
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
         * @param {Wrapped Event} weEvt
         */
        function _onClickEnrollAcceptBtns(weEvt){
            weEvt.preventDefault();
            var loginId = $(this).attr('data-loginId');
            $('#loginId').val(loginId);
            $('#addNewMember').submit();
        }

        /**
         * @param {Wrapped Element} weltArget
         */
        function _onClickDelete(){
            var sURL = $(this).attr("data-href");

            // DELETE 메소드로 AJAX 호출
            // v1.6은 삭제 링크를 누를 때마다 핸들러를 더해, 취소한 뒤 다른 구성원을 지우면 이전 구성원의 주소로도 DELETE가 나갔다(C024, C174, C113-3).
            // 같은 이름 공간의 핸들러를 떼고 다시 달아 마지막에 고른 구성원 하나만 지운다
            $("#deleteBtn").off("click.deleteMember").on("click.deleteMember", function(){
                $.ajax(sURL, {
                    "method"  : "delete",
                    "dataType": "html",
                    "success" : _onSuccessDeleteMember,
                    "error"   : _onErrorDeleteMember
                });
            });

            _showConfirmDeleteMember(sURL);
        }

        function _onSuccessDeleteMember(sResult){
            var htData = $.parseJSON(sResult);
            document.location.replace(htData.location);
        }

        /**
         * @param {Object} oXHR
         */
        function _onErrorDeleteMember(oXHR){
            var sErrorMsg;

            switch(oXHR.status){
                case 403:
                    var sNeedle = Messages("project.member.ownerCannotLeave");
                    sErrorMsg = (oXHR.responseText.indexOf(sNeedle) > -1) ? sNeedle : Messages("error.forbidden");
                    break;

                case 404:
                    sErrorMsg = Messages("organization.member.unknownOrganization");
                    break;

                default:
                    sErrorMsg = Messages("error.badrequest");
                    break;
            }

            $yobi.alert(sErrorMsg);
            bootstrap.Modal.getOrCreateInstance(htElement.welAlertDelete[0]).hide();
        }

        /**
         * @param {String} sURL
         */
        function _showConfirmDeleteMember(sURL){
            bootstrap.Modal.getOrCreateInstance(htElement.welAlertDelete[0]).show();
        }

        /**
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
                "htData" : {"id": sRoleId},
                "fOnLoad" : function(oData, oStatus, oXHR) {
                    console.log("oXHR.responseText:" + oXHR.responseText);
                    if (oXHR.responseText != "") {
                        var htData = $.parseJSON(oXHR.responseText);
                        document.location.replace(htData.location);
                    }
                }
            });
        }

        _init(htOptions);
    };

})("yobi.organization.Member");
