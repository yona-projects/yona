/**
 * Yona, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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
    oNS.container[oNS.name] = function(htOptions){
                
        var htElement = {};
        
        /**
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions);
            _attachEvent(htOptions);
        }
        
        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.elChkAccept      = document.getElementById("accept");
            htElement.elBtnTransferPop = document.getElementById("btnTransfer");
            htElement.elBtnTransferPrj = document.getElementById("btnTransferExec");
            htElement.elAlertTransfer  = document.getElementById("alertTransfer");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(htOptions){
            // 원본은 #btnTransfer의 data-toggle="modal"(Bootstrap 전역 델리게이트)이 모달을 열고,
            // _onClickBtnTransferPop이 체크 안 됐을 때 false를 반환해(stopPropagation) 그
            // 델리게이트까지 이벤트가 안 번지게 막는 방식이었다 - 델리게이트를 없앴으니 여기서
            // 직접 체크 후 열도록 동일한 게이트를 재현한다.
            htElement.elBtnTransferPop.addEventListener('click', function(weEvt){
                weEvt.preventDefault();
                if(_onClickBtnTransferPop()){
                    htElement.elAlertTransfer.showModal();
                }
            });

            $yona.attachDialogDismiss(htElement.elAlertTransfer);

            htElement.elBtnTransferPrj.addEventListener("click", function(){
                fetch(htOptions.sTransferURL + "?owner=" + document.getElementById("owner").value, {"method": "put"})
                    .then(function(response){
                        if(!response.ok){
                            return Promise.reject(response);
                        }
                        // default action below:
                        var sLocation = response.headers.get("Location");

                        if(response.status === 204 && sLocation){
                            document.location.href = sLocation;
                        } else {
                            document.location.reload();
                        }
                    })
                    .catch(function(){
                        htElement.elAlertTransfer.close();
                        $yona.alert(Messages("project.transfer.error"));
                    });
            }, {"once": true});
        }

        function _onClickBtnTransferPop(){
            if(htElement.elChkAccept.checked === false){
                $yona.alert(Messages("project.transfer.alert"));
                return false;
            }
            return true;
        }

        _init(htOptions || {});
    };
    
})("yona.project.Transfer");
