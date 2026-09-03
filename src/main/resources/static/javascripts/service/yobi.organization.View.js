/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Changgun Kim
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
    oNS.container[oNS.name] = function(){

        var htElement = {};

        /**
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _attachEvent(htOptions || {});
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welAlertLeave = $(htOptions.welAlertLeave || "#alertLeave");
            htElement.welGroupLeaveBtn = $(htOptions.welGroupLeaveBtn || "#groupLeaveBtn");
            htElement.welLeaveBtn = $(htOptions.welLeaveBtn || "#leaveBtn");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welGroupLeaveBtn.on("click", _onGroupClickLeaveBtn);
            htElement.welLeaveBtn.on("click", _onClickLeaveBtn);
        }

        function _onGroupClickLeaveBtn(){
            htElement.welAlertLeave.modal("show");
        }

        function _onClickLeaveBtn(){
            var sURL = htElement.welGroupLeaveBtn.attr("data-href");

            $.ajax(sURL, {
                "method"  : "delete",
                "dataType": "html",
                "success" : _onSuccessLeaveMember,
                "error"   : _onErrorLeaveMember
            });
        }

        function _onSuccessLeaveMember(oXHR){
            try{
                var htData = $.parseJSON(oXHR);
                document.location.replace(htData.location);
            }catch(e){
                document.location.reload();
            }
        }

        function _onErrorLeaveMember(oXHR){
            var sErrorMsg;

            try{
                sErrorMsg = Messages($.parseJSON(oXHR.responseText).errorMsg);
            }catch(e){
                sErrorMsg = Messages("organization.member.leave.unknownerror");
            }

            htElement.welAlertLeave.modal("hide");
            $yobi.notify(sErrorMsg, 3000);
        }

        _init();
    };
})("yobi.organization.View");
