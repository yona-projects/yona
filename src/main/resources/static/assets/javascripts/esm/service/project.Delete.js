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

const Delete = function(htOptions){

    const htVar = {};
    const htElement = {};

    /**
     * initialize
     */
    function _init(htOptions){
        _initElement(htOptions);
        _attachEvent(htOptions);
    }

    /**
     * initialize element variables
     */
    function _initElement(htOptions){
        htElement.welBtnDeletePrj = $("#btnDeleteExec");
        htElement.welConfirmName  = $("#deleteConfirmName");
        htElement.welModal        = $("#alertDeletion");
    }

    /**
     * 확인 창의 "예" 단추는 `owner/name`을 정확히(대소문자 포함) 입력했을 때만 켠다(A-536, GitHub 방식).
     * 서버 요청 계약은 그대로이고 화면에서만 한 번 더 확인한다.
     */
    function _syncExecButton(){
        const matched = htElement.welConfirmName.val() === htOptions.sProjectName;
        htElement.welBtnDeletePrj.prop("disabled", !matched);
    }

    /**
     * attach event handlers
     */
    function _attachEvent(htOptions){
        htElement.welConfirmName.on("input", _syncExecButton);
        // 확인 창을 열 때마다 입력을 비우고 "예"를 끈다
        htElement.welModal.on("show.bs.modal", () => { htElement.welConfirmName.val(""); _syncExecButton(); });
        htElement.welBtnDeletePrj.on("click", (weEvt) =>{
            weEvt.preventDefault();
            weEvt.stopPropagation();
            if(htElement.welConfirmName.val() !== htOptions.sProjectName){ return; }
            yona.Request.send("delete", htOptions.sDeleteURL).then((result) =>{
                if(result.ok === false){
                    bootstrap.Modal.getOrCreateInstance(document.querySelector("#alertDeletion")).hide();
                    $yona.alert(Messages("project.delete.error"));
                }
            });
        });
    }

    _init(htOptions || {});
};

export default Delete;

register("yona.project.Delete", Delete);
