/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
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
         *
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initElement();
            _initFileDownloader();
        }

        /**
         * 엘리먼트 변수 초기화
         * initialize elements
         */
        function _initElement(){
            htElement.welAttachments = $(".attachments");
        }

        /**
         * initialize fileDownloader
         */
        function _initFileDownloader(){
            htElement.welAttachments.each(function(i, elContainer){
                if(!$(elContainer).data("isYobiAttachment")){
                    (new yobi.Attachments({"elContainer": elContainer}));
                }
            });
        }

        _init(htOptions || {});
    };
})("yobi.milestone.View");