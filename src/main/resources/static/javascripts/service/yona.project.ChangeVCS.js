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
            elements.confirmProjectName = document.getElementById("confirmVcsProjectName");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(options){
            // 체크박스 게이트 + 모달 열기/닫기는 project.Delete/Transfer.js와 거의
            // 동일하게 반복되던 부분이라 공용 헬퍼로 합쳤다(yona.Common.js 참고).
            $yona.attachCheckboxGatedConfirm(elements.btnChangeVCS, elements.acceptChangeVCS,
                elements.alertChangeVCS, Messages("project.changeVCS.alert"));
            elements.btnChangeVCSExec.addEventListener("click", changeVCS);
            elements.confirmProjectName.addEventListener("input", function() {
                elements.btnChangeVCSExec.disabled =
                    elements.confirmProjectName.value !== elements.confirmProjectName.dataset.projectName;
            });
        }

        function changeVCS() {
            if (!elements.acceptChangeVCS.checked ||
                    elements.confirmProjectName.value !== elements.confirmProjectName.dataset.projectName) {
                return;
            }
            fetch(options.sTransferURL, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    projectId: Number(elements.confirmProjectName.dataset.projectId),
                    projectName: elements.confirmProjectName.value,
                    expectedVcs: elements.confirmProjectName.dataset.currentVcs,
                    accepted: elements.acceptChangeVCS.checked
                })
            })
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
