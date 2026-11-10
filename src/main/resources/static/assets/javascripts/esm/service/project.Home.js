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

const Home = function(htOptions){

    const htVar = {};
    const htElement = {};

    function _init(htOptions){
        const htOpt = htOptions || {};
        _initVar(htOpt);
        _initElement(htOptions);
        _attachEvent();

//            _resizeProjectInfo();
    }

    function _initVar(htOptions){
        htVar.sRepoURL = htOptions.sRepoURL;
        htVar.sURLProject = htOptions.sURLProject;
    }

    /**
     * initialize element
     */
    function _initElement(htOptions) {
        // clone url
        htElement.welBtnClone   = $('[data-toggle="cloneURL"]');

        htElement.welInputCloneURL =$('#cloneURL');
        htElement.welBtnCopy   = $('#cloneURLBtn');

        htElement.welAlertLeave = $("#alertLeave");
    }

    /**
     * attach event handler
     */
    function _attachEvent(){
        // 저장소 주소 복사(Flash 대체 경로는 없앴다, yona.Clipboard)
        htElement.welBtnCopy.on("click", () => {
            yona.Clipboard.copy(htElement.welInputCloneURL.val()).then((copied) => {
                if (copied) {
                    yona.Common.notify(Messages("code.copyUrl.copied"), 1000);
                }
            });
        });

        htElement.welInputCloneURL.on('click',function(){
            $(this).select();
        });

        $('.project-page-wrap')
            .on('click', '[data-toggle="description-edit"]', () =>{
                $('[data-toggle="project-description-tab"]').toggleClass('hidden');
                $('.project-description-edit input').focus();
            }).on('click', '[data-toggle="description-cancel"]', () =>{
                $('[data-toggle="project-description-tab"]').toggleClass('hidden');
            });
        $('#descriptionSaveBtn').on('click',() =>{
            const overview = {"overview" : $("#project-description-input").val() };
            yona.http.ajax({
                "url": htVar.sURLProject,
                "method": "put",
                "data": JSON.stringify(overview),
                "contentType":"application/json"
            }).done((data) =>{
                    const sDescription = (data.overview)
                                        ? data.overview
                                        : $("#project-description-input").attr('placeholder');

                    yona.Markdown.render($("#project-description"), sDescription);

                    $('[data-toggle="project-description-tab"]').toggleClass('hidden');


            }).fail((err) =>{
                    console.log("err>> ", err);
            });
        });
        $('#projectLeaveBtn').on('click',function(){
            bootstrap.Modal.getOrCreateInstance(htElement.welAlertLeave[0]).show();

            const sURL = $(this).attr("data-href");

            $("#leaveBtn").click(() =>{

                yona.http.ajax(sURL, {
                    "method": "delete",
                    "dataType": "html"
                }).done((sResult) =>{
                    const htData = JSON.parse(sResult);
                    document.location.replace(htData.location);
                }).fail((oXHR) =>{
                    let sErrorMsg;

                    switch(oXHR.status){
                        case 403:
                            sErrorMsg = Messages("project.member.notExist");
                            break;

                        case 404:
                            sErrorMsg = Messages("project.is.empty");
                            break;

                        default:
                            sErrorMsg = Messages("error.badrequest");
                            break;
                    }

                    $yona.alert(sErrorMsg);
                });
            });
        });
    }

    /*
    function _resizeProjectInfo(){
        htElement.welHomeInfo.width(htElement.welHome.width() - (htElement.welHomeLogo.width() + htElement.welHomeMember.width() + 21));
    }
    */

    _init(htOptions || {});
};

export default Home;

register("yona.project.Home", Home);
