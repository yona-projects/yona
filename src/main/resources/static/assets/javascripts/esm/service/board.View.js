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

import { register } from "../namespace.js";

const View = function(htOptions){

    const htVar = {};
    const htElement = {};

    /**
     * initialize
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initVar(htOptions || {});
        _initElement(htOptions || {});
        _attachEvent();

        _initFileUploader();
        _initFileDownloader();
    }

    /**
     * initialize variables except HTML Element
     */
    function _initVar(htOptions){
        htVar.sTplFileItem = $('#tplAttachedFile').text();
        htVar.sAction = htOptions.sAction;
        htVar.sWatchUrl = htOptions.sWatchUrl;
        htVar.sUnwatchUrl = htOptions.sUnwatchUrl;
    }

    /**
     * initialize HTML Element variables
     */
    function _initElement(htOptions){
        htElement.welUploader = $("#upload");
        htElement.welTextarea = $('textarea[data-editor-mode="comment-body"]');

        htElement.welAttachments = $(".attachments");
        htElement.welBtnWatch = $('#watch-button');
        htElement.issueInfoWrap = $(".issue-info");
    }

    /**
     * attach event handler
     */
    function _attachEvent(){
        htElement.welBtnWatch.click((weEvt) => {
            const welTarget = $(weEvt.target);
            const bWatched = (welTarget.attr("data-watching") === "true");
            
            $yona.sendForm({
                "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                "fOnLoad": function(){
                    welTarget
                        .attr("data-watching", !bWatched)
                        .toggleClass('ybtn-watching')
                        .html(Messages(!bWatched ? "post.unwatch" : "post.watch")).blur();
                    
                    $yona.notify(Messages(bWatched ? "post.unwatch.start" : "post.watch.start"), 3000);
                }
            });
        });
    }

    /**
     * initialize fileUploader
     */
    function _initFileUploader(){
        const oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

        if(oUploader){
            (new yona.Attachments({
                "elContainer"  : htElement.welUploader,
                "elTextarea"   : htElement.welTextarea,
                "sTplFileItem" : htVar.sTplFileItem,
                "sUploaderId"  : oUploader.attr("data-namespace")
            }));
        }
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


    _init(htOptions);
};

export default View;

register("yona.board.View", View);
