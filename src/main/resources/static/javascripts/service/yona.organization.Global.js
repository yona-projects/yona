/**
 * Yona, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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
(function(ns) {
    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions) {
        var htElement = {};

        /**
         * initialize
         */
        function _init() {
            _initElement();
            _attachEvent();
        }

        /**
         * initialize element variables
         */
        function _initElement() {
            htElement.elBtnEnroll  = document.getElementById("enrollBtn");
        }

        /**
         * attach event handlers
         */
        function _attachEvent() {
            htElement.elBtnEnroll.addEventListener('click', _onClickBtnEnroll);
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickBtnEnroll(weEvt){
            var sURL = this.getAttribute('href');
            fetch(sURL, { "method": "post" })
                .then(function(response){
                    if (!response.ok) {
                        throw new Error("Server Error");
                    }
                    document.location.reload();
                })
                .catch(function(){
                    $yona.notify("Server Error");
                });

            weEvt.preventDefault();
            return false;
        }

        _init(htOptions || {});
    };
})("yona.organization.Global");
