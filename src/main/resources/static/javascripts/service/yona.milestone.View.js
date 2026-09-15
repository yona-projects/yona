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
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         *
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _initFileDownloader();
            _attachEvent();
        }

        /**
         * initialize elements
         */
        function _initElement(htOptions){
            htElement.welAttachments = document.querySelectorAll(".attachments");
            htElement.waLabels = document.querySelectorAll("a.issue-label[data-color]");
            htElement.sMilestoneId = htOptions.sMilestoneId;
            htElement.sURLLabels = htOptions.sURLLabels;
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.waLabels.forEach(function(elLabel){
                elLabel.addEventListener("click", function(weEvt){
                    weEvt.preventDefault();
                    location.href = htElement.sURLLabels + "?milestoneId=" + htElement.sMilestoneId + "&labelIds=" + elLabel.getAttribute('data-labelId');
                });
            });
        }
        /**
         * initialize fileDownloader
         */
        function _initFileDownloader(){
            htElement.welAttachments.forEach(function(elContainer){
                // 6단계(jQuery 완전 제거): isYonaAttachment는 yona.Attachments.js가 붙이는
                // 순수 expando 프로퍼티다(공개 계약, 중복 초기화 가드).
                if(!elContainer._isYonaAttachment){
                    (new yona.Attachments({"elContainer": elContainer}));
                }
            });
        }

        _init(htOptions || {});
    };
})("yona.milestone.View");
