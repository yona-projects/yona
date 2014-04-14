/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changsung Kim
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
(function(ns) {
    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions) {
        var htElement = {};

        /**
         * 모듈 초기화
         * initialize
         */
        function _init() {
            _initElement();
            _attachEvent();
        }

        /**
         * 엘리먼트 변수 초기화
         * initialize element variables
         */
        function _initElement() {
            htElement.welBtnEnroll  = $("#enrollBtn");
        }

        /**
         * 이벤트 핸들러 초기화
         * attach event handlers
         */
        function _attachEvent() {
            htElement.welBtnEnroll.on('click',_onClickBtnEnroll);
        }

        /**
         * Enroll 버튼 클릭시 이벤트 핸들러
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnEnroll(weEvt){
            var sURL = $(this).attr('href');
            $.ajax(sURL, {
                "method" : "post",
                "success": function(){
                    document.location.reload();
                },
                "error": function(){
                    $yobi.notify("Server Error");
                }
            })

            weEvt.preventDefault();
            return false;
        }

        _init(htOptions || {});
    };
})("yobi.organization.Global");
