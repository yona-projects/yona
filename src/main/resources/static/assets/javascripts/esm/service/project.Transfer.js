/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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

const Transfer = function(htOptions){
            
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
        htElement.welBtnTransferPrj = $("#btnTransferExec");
        htElement.welConfirmName    = $("#transferConfirmName");
        htElement.welModal          = $("#alertTransfer");
    }

    /**
     * 확인 창의 "예" 단추는 `owner/name`을 정확히(대소문자 포함) 입력했을 때만 켠다(A-538, GitHub 방식).
     * 서버 요청 계약은 그대로이고 화면에서만 한 번 더 확인한다.
     */
    function _syncExecButton(){
        htElement.welBtnTransferPrj.prop("disabled", htElement.welConfirmName.val() !== htOptions.sProjectName);
    }


    /**
     * attach event handlers
     */
    function _attachEvent(htOptions){
        htElement.welConfirmName.on("input", _syncExecButton);
        // 확인 창을 열 때마다 입력을 비우고 "예"를 끈다
        htElement.welModal.on("show.bs.modal", () => { htElement.welConfirmName.val(""); _syncExecButton(); });
        // v1.6은 `.one("click")`이라 한 번 실패하면 같은 화면에서 다시 "예"를 눌러도 아무 반응이 없었다(project-F19).
        // 요청 중에만 중복 전송을 막고, 실패하면 다시 보낼 수 있게 푼다.
        let bSending = false;
        htElement.welBtnTransferPrj.on("click", () =>{
            if(bSending || htElement.welConfirmName.val() !== htOptions.sProjectName){
                return;
            }
            bSending = true;
            yona.http.ajax(htOptions.sTransferURL + "?owner=" + $("#owner").val(), {
                "method" : "put",
                "success": function(oRes, sStatus, oXHR){
                    // default action below:
                    const sLocation = oXHR.getResponseHeader("Location");

                    if(oXHR.status === 204 && sLocation){
                        document.location.href = sLocation;
                    } else {
                        document.location.reload();
                    }
                },
                "error": function(){
                    bSending = false;
                    bootstrap.Modal.getOrCreateInstance(document.querySelector("#alertTransfer")).hide();
                    $yona.alert(Messages("project.transfer.error"));
                    return false;
                }
            });
        });
    }
    
    _init(htOptions || {});
};

export default Transfer;

register("yona.project.Transfer", Transfer);
