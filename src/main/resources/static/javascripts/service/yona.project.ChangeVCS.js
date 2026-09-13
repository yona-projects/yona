/**
 * Yona, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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

    var NO_CONTENT = 204;
    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(options){

        var elements = {};

        /**
         * initialize
         */
        function _init(options){
            _initElement(options);
            _attachEvent(options);
        }

        /**
         * initialize element variables
         */
        function _initElement(optinos){
            elements.acceptChangeVCS = document.getElementById("acceptChangeVCS");
            elements.btnChangeVCS = document.getElementById("btnChangeVCS");
            elements.btnChangeVCSExec = document.getElementById("btnChangeVCSExec");
            elements.alertChangeVCS = document.getElementById("alertChangeVCS");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(options){
            // 원본은 #btnChangeVCS의 data-toggle="modal"(Bootstrap 전역 델리게이트)이 모달을
            // 열고, showConfirmPopup은 체크 안 됐을 때 false를 반환해(stopPropagation) 그
            // 델리게이트 핸들러까지 이벤트가 안 번지게 막는 방식이었다 - 델리게이트를 없앴으니
            // 여기서 직접 체크 후 열도록 동일한 게이트를 재현한다.
            elements.btnChangeVCS.addEventListener('click', function(weEvt){
                weEvt.preventDefault();
                if(showConfirmPopup()){
                    elements.alertChangeVCS.showModal();
                }
            });
            elements.btnChangeVCSExec.addEventListener("click", changeVCS);

            $yona.attachDialogDismiss(elements.alertChangeVCS);
        }

        function showConfirmPopup() {
            if(elements.acceptChangeVCS.checked === false){
                $yona.alert(Messages("project.changeVCS.alert"));
                return false;
            }
            return true;
        }

        function changeVCS() {
            fetch(options.sTransferURL, {"method": "post"})
                .then(function(response){
                    // jQuery의 error 콜백은 HTTP 에러 상태(4xx/5xx)에서도 호출됐지만, fetch는
                    // 네트워크 레벨 실패만 reject하므로 response.ok를 직접 확인해야 동일하게
                    // 동작한다.
                    if(!response.ok){
                        return Promise.reject(response);
                    }
                    // default action below:
                    var location = response.headers.get("Location");

                    if(response.status === NO_CONTENT && location){
                        document.location.href = location;
                    } else {
                        document.location.reload();
                    }
                })
                .catch(function(){
                    elements.alertChangeVCS.close();
                    $yona.alert(Messages("project.changeVCS.error"));
                });
        }

        _init(options || {});
    };

})("yona.project.ChangeVCS");
