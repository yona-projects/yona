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

import { register } from "../namespace.js";

const View = function(htOptions){

    const htVar = {};
    const htElement = {};

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
        // 열기, 닫기 요청이 실패하면(이미 지워진 마일스톤은 404) 알린다. 공통 요청 도우미(Request.js)는 실패를 알리지 않아
        // v1.6은 눌러도 아무 반응이 없었다(milestone-F7). `request:error`는 버블링하지 않으므로 단추마다 듣는다
        $("[data-request-method]").each(function(){
            const elButton = this;
            elButton.addEventListener("request:error", function(weEvt){
                $yona.alert(Messages("error.failedTo", $(elButton).text().trim(), weEvt.detail.status, ""));
            });
        });
        htElement.waLabels.on("click", function(weEvt){
            weEvt.preventDefault();
            location.href = htElement.sURLLabels + "?milestoneId=" + htElement.sMilestoneId + "&labelIds=" + $(this).attr('data-labelId');
        });
    }
    /**
     * initialize fileDownloader
     */
    function _initFileDownloader(){
        htElement.welAttachments.each((i, elContainer) =>{
            if(!$(elContainer).data("isYobiAttachment")){
                (new yona.Attachments({"elContainer": elContainer}));
            }
        });
    }

    _init(htOptions || {});
};

export default View;

register("yona.milestone.View", View);
