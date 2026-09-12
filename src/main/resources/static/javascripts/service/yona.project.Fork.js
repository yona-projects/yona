/**
 * Yona, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Jungkook Kim
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
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _attachEvent();
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.elInputProjectOwner = document.getElementById("project-owner");
        }

        /**
         * attach event handlers
         */
        function _attachEvent() {
            htElement.elInputProjectOwner.addEventListener("change", _onChangeProjectOwner);
        }

        function _onChangeProjectOwner() {
            document.location.href = this.selectedOptions[0].dataset.url;
        }

        /**
         * destroy this module
         */
        function destroy(){
            htElement = null;
        }

        _init();
    };
})("yona.project.Fork");
