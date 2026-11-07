/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihan Kim
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
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initVar(htOptions || {});
            _initElement(htOptions || {});
            _attachEvent();
        }

        /**
         * initialize variables except element
         */
        function _initVar(htOptions){
            htVar.sFoo = "bar";
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welDocument = $(htOptions.elDocument || document);
        }

        /**
         * attach event handlers
         */
        function _attachEvent() {
            htElement.welDocument.ready(_onDocumentReady);
        }

        function _onDocumentReady(){
            // ...
        }


        /**
         * ...
         * ...
         * your implements are here
         * ...
         * ...
         */

        /**
         * destroy this module
         */
        function destroy(){
            // detachEvent() if available

            // free memory
            htVar = htElement = null;
        }

        _init();
    };

})("yobi.module.Name");
