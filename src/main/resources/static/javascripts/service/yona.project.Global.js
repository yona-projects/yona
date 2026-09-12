/**
 * Yona, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author JiHan Kim
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
/*
 * 프로젝트 페이지 전역에 영향을 주는 공통모듈
 * projectMenu.scala.html 에서 호출함
 */
(function(ns) {
    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions) {
        var htVar = {};
        var htElement = {};

        var clientErrorStatus = /^4[0-9][0-9]$/
        var serverErrorStatus = /^5[0-9][0-9]$/
        var networkErrorStatus = 0;

        /**
         * initialize
         */
        function _init(htOptions) {
            _initVar(htOptions);
            _initElement();
            _attachEvent();

            // htKeyMap is optional
            if(typeof htOptions.htKeyMap === "object"){
                _initShortcutKey(htOptions.htKeyMap);
            }
        }

        /**
         * initialize normal variables
         */
        function _initVar(htOptions){

        }

        /**
         * initialize element variables
         */
        function _initElement() {
            htElement.welBtnWatch   = document.querySelectorAll(".watchBtn, #btnWatch");
            htElement.welBtnEnroll  = document.querySelectorAll("#enrollBtn");

            htElement.welForkedFrom = document.querySelectorAll("#forkedFrom");
            htElement.weBtnHeaderToggle = document.querySelectorAll('.project-header-toggle-btn');
        }

        /**
         * attach event handlers
         */
        function _attachEvent() {
            htElement.welBtnWatch.forEach(function(el){ el.addEventListener('click', _onClickBtnWatch); });
            htElement.welBtnEnroll.forEach(function(el){ el.addEventListener('click', _onClickBtnEnroll); });
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickBtnWatch(weEvt){
            var sURL = this.getAttribute('href');

            fetch(sURL, {"method": "post"})
                .then(function(response){
                    if(!response.ok){
                        return Promise.reject(response);
                    }
                    document.location.reload();
                })
                .catch(function(){
                    $yona.notify("Server Error");
                });

            weEvt.preventDefault();
            return false;
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickBtnEnroll(weEvt){
            var sURL = this.getAttribute('href');

            fetch(sURL, {"method": "post"})
                .then(function(response){
                    if(!response.ok){
                        return Promise.reject(response);
                    }
                    document.location.reload();
                })
                .catch(function(oXHR){
                    // jQuery의 readyState===0(UNSET)은 요청이 서버에 도달 못한 네트워크 실패를
                    // 뜻했다 - fetch는 이 경우 프로미스 자체가 reject되어(TypeError, Response
                    // 객체가 아예 안 생김) oXHR.status가 undefined인 것으로 동일하게 감지한다.
                    if(typeof oXHR.status === "undefined"){
                        $yona.notify(Messages("user.enroll.failed.network"), 3000);
                    }else{
                        switch(true){
                            case oXHR.status == 403:
                                $yona.notify(Messages("error.forbidden"), 3000);
                                break;
                            case clientErrorStatus.test(oXHR.status):
                                $yona.notify(Messages("user.enroll.failed.client"), 3000);
                                break;
                            case serverErrorStatus.test(oXHR.status):
                                $yona.notify(Messages("user.enroll.failed.server"), 3000);
                                break;
                            default:
                                $yona.notify(Messages("user.enroll.failed"), 3000);
                                break;
                        }
                    }
                });

            weEvt.preventDefault();
            return false;
        }

        /**
         * @param {Hash Table} htKeyMap
         * @require yona.ShortcutKey
         */
        function _initShortcutKey(htKeyMap){
            yona.ShortcutKey.setKeymapLink(htKeyMap);
        }

        _init(htOptions || {});
    };
})("yona.project.Global");
