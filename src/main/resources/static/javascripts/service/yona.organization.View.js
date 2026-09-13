/**
 * Yona, Project Hosting SW
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

    var oNS = $yona.createNamespace(ns);
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
            htElement.elAlertLeave = document.querySelector(htOptions.welAlertLeave || "#alertLeave");
            htElement.waGroupLeaveBtn = document.querySelectorAll(htOptions.welGroupLeaveBtn || "#groupLeaveBtn");
            htElement.waLeaveBtn = document.querySelectorAll(htOptions.welLeaveBtn || "#leaveBtn");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.waGroupLeaveBtn.forEach(function(el){ el.addEventListener("click", _onGroupClickLeaveBtn); });
            htElement.waLeaveBtn.forEach(function(el){ el.addEventListener("click", _onClickLeaveBtn); });

            // 배경 클릭/X버튼(data-dismiss=modal) 닫기 - ESC는 <dialog> 네이티브 동작.
            $yona.attachDialogDismiss(htElement.elAlertLeave);
        }

        function _onGroupClickLeaveBtn(){
            htElement.elAlertLeave.showModal();
        }

        function _onClickLeaveBtn(){
            // 클릭된 확인 버튼(this) 자신이 아니라, 모달을 열었던 groupLeaveBtn의
            // data-href를 읽는다(원본 jQuery 코드와 동일한 계약).
            var sURL = htElement.waGroupLeaveBtn[0].getAttribute("data-href");

            fetch(sURL, {"method": "delete"})
                .then(function(response){
                    if(!response.ok){
                        return response.text().then(function(text){
                            return Promise.reject({"responseText": text});
                        });
                    }
                    return response.text();
                })
                .then(_onSuccessLeaveMember)
                .catch(_onErrorLeaveMember);
        }

        function _onSuccessLeaveMember(oXHR){
            try{
                var htData = JSON.parse(oXHR);
                document.location.replace(htData.location);
            }catch(e){
                document.location.reload();
            }
        }

        function _onErrorLeaveMember(oXHR){
            var sErrorMsg;

            try{
                sErrorMsg = Messages(JSON.parse(oXHR.responseText).errorMsg);
            }catch(e){
                sErrorMsg = Messages("organization.member.leave.unknownerror");
            }

            htElement.elAlertLeave.close();
            $yona.notify(sErrorMsg, 3000);
        }

        _init();
    };
})("yona.organization.View");
