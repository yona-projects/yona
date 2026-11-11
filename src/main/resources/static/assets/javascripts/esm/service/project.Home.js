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
        // v1.6은 폼 제출을 막기만 해서 입력란에서 Enter를 눌러도 저장되지 않았다(project-F14). Enter를 저장 단추 클릭으로 처리한다.
        $('#project-description-input').on('keydown', (weEvt) =>{
            if(weEvt.key === 'Enter' && !weEvt.originalEvent?.isComposing){
                weEvt.preventDefault();
                $('#descriptionSaveBtn').trigger('click');
            }
        });
        $('#descriptionSaveBtn').on('click', async () =>{
            const overview = {"overview" : $("#project-description-input").val() };
            let data;
            try {
                data = await yona.http.ajax({
                    "url": htVar.sURLProject,
                    "method": "put",
                    "data": JSON.stringify(overview),
                    "contentType":"application/json"
                });
            } catch (err) {
                // v1.6은 콘솔에만 남겨 저장이 실패해도 아무 반응이 없었다(project-F14). 오류를 알리고 편집 상태를 유지한다.
                const nStatus = err && err.status;
                $yona.alert(Messages(nStatus === 403 ? "error.forbidden" : (nStatus === 404 ? "error.notfound" : "error.internalServerError")));
                return;
            }
            const sDescription = (data.overview)
                                ? data.overview
                                : $("#project-description-input").attr('placeholder');

            yona.Markdown.render($("#project-description"), sDescription);

            $('[data-toggle="project-description-tab"]').toggleClass('hidden');
        });
        $('#projectLeaveBtn').on('click',function(){
            bootstrap.Modal.getOrCreateInstance(htElement.welAlertLeave[0]).show();

            const sURL = $(this).attr("data-href");

            // 탈퇴 단추를 누를 때마다 처리기가 겹쳐 쌓여 "아니오" 뒤 다시 "예"를 누르면 요청이 여러 번 나갔다(v1.6, project-F10). 이전 처리기를 떼고 단다.
            $("#leaveBtn").off("click").on("click", async () =>{
                let sResult;
                try {
                    sResult = await yona.http.ajax(sURL, {
                        "method": "delete",
                        "dataType": "html"
                    });
                } catch (oXHR) {
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
                    return;
                }
                const htData = JSON.parse(sResult);
                document.location.replace(htData.location);
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
