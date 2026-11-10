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
        htElement.welChkAccept      = $("#accept");
        htElement.welBtnTransferPop = $("#btnTransfer");
        htElement.welBtnTransferPrj = $("#btnTransferExec");
    }

    /**
     * attach event handlers
     */
    function _attachEvent(htOptions){
        htElement.welBtnTransferPop.click(_onClickBtnTransferPop);

        htElement.welBtnTransferPrj.one("click", () =>{
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
                    bootstrap.Modal.getOrCreateInstance(document.querySelector("#alertTransfer")).hide();
                    $yona.alert(Messages("project.transfer.error"));
                    return false;
                }
            });
        });
    }
    
    function _onClickBtnTransferPop(){
        if(htElement.welChkAccept.is(":checked") === false){
            $yona.alert(Messages("project.transfer.alert"));
            return false;
        }
        return true;
    }

    _init(htOptions || {});
};

export default Transfer;

register("yona.project.Transfer", Transfer);
