/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Ahn Hyeok Jun
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

        var htElement = {};
        var htOrderMap = {"asc": "desc", "desc": "asc"};

        /**
         * initialize
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _attachEvent();

            _initPagination(htOptions);
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = $(htOptions.sOptionForm || "#option_form");
            htElement.welInputOrderBy = htElement.welForm.find("input[name=orderBy]");
            htElement.welInputOrderDir = htElement.welForm.find("input[name=orderDir]");
            htElement.welInputPageNum = htElement.welForm.find("input[name=pageNum]");

            htElement.welPages = $(htOptions.sQueryPages || "#pagination a");
            htElement.welPagination = $(htOptions.elPagination || '#pagination');
        }

        /**
         * attach event handlers
         */
        function _attachEvent() {
            htElement.welPages.click(_onClickPage);
        }

        /**
         * onClick PageNum
         */
        function _onClickPage(){
            htElement.welInputPageNum.val($(this).attr("pageNum"));
            htElement.welForm.submit();
            return false;
        }


        function _initPagination(htOptions){
            yobi.Pagination.update(htElement.welPagination, htOptions.nTotalPages);
        }

        _init(htOptions);
    };

})("yobi.board.List");
