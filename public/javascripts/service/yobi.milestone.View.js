/**
 * Yobi, Project Hosting SW
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
            _initElement(htOptions || {});
            _initFileDownloader();
            _attachEvent();
        }

        /**
         * initialize elements
         */
        function _initElement(htOptions){
            htElement.welAttachments = $(".attachments")
            htElement.waLabels = $("a.issue-label[data-color]")
            htElement.sMilestoneId = htOptions.sMilestoneId;
            htElement.sURLLabels = htOptions.sURLLabels;
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.waLabels.on("click", function(weEvt){
                weEvt.preventDefault();
                location.href = htElement.sURLLabels + "?milestoneId=" + htElement.sMilestoneId + "&labelIds=" + $(this).attr('data-labelId');
            });
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
