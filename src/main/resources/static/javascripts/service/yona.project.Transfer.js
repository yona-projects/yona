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
            // 체크박스 게이트 + 모달 열기/닫기는 project.Delete/ChangeVCS.js와 동일하게
            // 반복되던 부분이라 공용 헬퍼로 합쳤다(yona.Common.js).
            $yona.attachCheckboxGatedConfirm(htElement.elBtnTransferPop, htElement.elChkAccept,
                htElement.elAlertTransfer, Messages("project.transfer.alert"));

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

        _init(htOptions || {});
    };
    
})("yona.project.Transfer");
