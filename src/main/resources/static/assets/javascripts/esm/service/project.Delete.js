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
        htElement.welChkAccept    = $("#acceptDelete");
        htElement.welBtnDeletePop = $("#btnDelete");
        htElement.welBtnDeletePrj = $("#btnDeleteExec");
    }

    /**
     * attach event handlers
     */
    function _attachEvent(htOptions){
        htElement.welBtnDeletePop.click(_onClickBtnDeletePrj);
        htElement.welBtnDeletePrj.on("click", (weEvt) =>{
            weEvt.preventDefault();
            weEvt.stopPropagation();
            yona.Request.send("delete", htOptions.sDeleteURL).then((result) =>{
                if(result.ok === false){
                    bootstrap.Modal.getOrCreateInstance(document.querySelector("#alertDeletion")).hide();
                    $yona.alert(Messages("project.delete.error"));
                }
            });
        });
    }

    /** 삭제 단추: 동의했을 때만 확인 창을 연다(이유는 이관 단추와 같다). */
    function _onClickBtnDeletePrj(weEvt){
        weEvt.preventDefault();
        if(htElement.welChkAccept.is(":checked") === false){
            $yona.alert(Messages("project.delete.alert"));
            return false;
        }
        bootstrap.Modal.getOrCreateInstance(document.querySelector("#alertDeletion")).show();
        return false;
    }

    _init(htOptions || {});
};

export default Delete;

register("yona.project.Delete", Delete);
