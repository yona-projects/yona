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
        htElement.welChkAccept      = $("#acceptTransfer");
        htElement.welBtnTransferPop = $("#btnTransfer");
        htElement.welBtnTransferPrj = $("#btnTransferExec");
    }

    /**
     * attach event handlers
     */
    function _attachEvent(htOptions){
        htElement.welBtnTransferPop.click(_onClickBtnTransferPop);

        // v1.6은 `.one("click")`이라 한 번 실패하면 같은 화면에서 다시 "예"를 눌러도 아무 반응이 없었다(project-F19).
        // 요청 중에만 중복 전송을 막고, 실패하면 다시 보낼 수 있게 푼다.
        let bSending = false;
        htElement.welBtnTransferPrj.on("click", () =>{
            if(bSending){
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
    
    /**
     * 이관 단추: 동의 체크박스에 동의했을 때만 확인 창을 연다. 단추가 `data-bs-toggle="modal"`이면 BS5가 `return false`로 막히지 않고 열어 버리므로,
     * 확인 창은 여기서 직접 연다(동의하지 않으면 경고만 한다).
     */
    function _onClickBtnTransferPop(weEvt){
        weEvt.preventDefault();
        if(htElement.welChkAccept.is(":checked") === false){
            $yona.alert(Messages("project.transfer.alert"));
            return false;
        }
        bootstrap.Modal.getOrCreateInstance(document.querySelector("#alertTransfer")).show();
        return false;
    }

    _init(htOptions || {});
};

export default Transfer;

register("yona.project.Transfer", Transfer);
