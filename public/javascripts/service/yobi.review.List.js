/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Deokhong Kim
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
         */
        function _init(htOptions){
            _initVar(htOptions || {})
            _initElement();
            _attachEvent();
            _initPagination();
        }

        function _initVar(htOptions) {
            htVar = htOptions;
            htVar.nTotalPages = htOptions.nTotalPages || 1;
        }

         /**
         * initialize element
         */
        function _initElement(){
            htElement.welPagination = $(htVar.elPagination || "#pagination");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){

        }

        /**
         * update Pagination
         * @requires yobi.Pagination
         */
        function _initPagination(){
            yobi.Pagination.update(htElement.welPagination, htVar.nTotalPages);
        }

        _init(htOptions);
    }
})("yobi.review.List");

