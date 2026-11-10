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
/*
 * 프로젝트 페이지 전역에 영향을 주는 공통모듈
 * projectMenu.scala.html 에서 호출함
 */

import { register } from "../namespace.js";

const Global = function(htOptions){
    const htVar = {};
    const htElement = {};

    const clientErrorStatus = /^4[0-9][0-9]$/
    const serverErrorStatus = /^5[0-9][0-9]$/
    const networkErrorStatus = 0;

    /**
     * initialize
     */
    function _init(htOptions) {
        _initVar(htOptions);
        _initElement();
        _attachEvent();

        // htKeyMap is optional
        if(typeof htOptions.htKeyMap === "object"){
            _initShortcutKey(htOptions.htKeyMap);
        }
    }

    /**
     * initialize normal variables
     */
    function _initVar(htOptions){

    }

    /**
     * initialize element variables
     */
    function _initElement() {
        htElement.welBtnWatch   = $(".watchBtn, #btnWatch");
        htElement.welBtnEnroll  = $("#enrollBtn");

        htElement.welForkedFrom = $("#forkedFrom");
        htElement.weBtnHeaderToggle = $('.project-header-toggle-btn');
    }

    /**
     * attach event handlers
     */
    function _attachEvent() {
        htElement.welBtnWatch.on('click',_onClickBtnWatch);
        htElement.welBtnEnroll.on('click',_onClickBtnEnroll);
    }

    /**
     * @param {Wrapped Event} weEvt
     */
    function _onClickBtnWatch(weEvt){
        const sURL = $(this).attr('href');

        yona.http.ajax(sURL, {
            "method" : "post",
            "success": function(){
                document.location.reload();
            },
            "error": function(){
                $yona.notify("Server Error");
            }
        })

        weEvt.preventDefault();
        return false;
    }

    /**
     * @param {Wrapped Event} weEvt
     */
    function _onClickBtnEnroll(weEvt){
        const sURL = $(this).attr('href');

        yona.http.ajax(sURL, {
            "method" : "post",
            "success": function(){
                document.location.reload();
            },
            "error": function(oXHR){
                if(oXHR.readyState == networkErrorStatus){
                    $yona.notify(Messages("user.enroll.failed.network"), 3000);
                }else{
                    switch(true){
                        case oXHR.status == 403:
                            $yona.notify(Messages("error.forbidden"), 3000);
                            break;
                        case clientErrorStatus.test(oXHR.status):
                            $yona.notify(Messages("user.enroll.failed.client"), 3000);
                            break;
                        case serverErrorStatus.test(oXHR.status):
                            $yona.notify(Messages("user.enroll.failed.server"), 3000);
                            break;
                        default:
                            $yona.notify(Messages("user.enroll.failed"), 3000);
                            break;
                    }
                }
            }
        })

        weEvt.preventDefault();
        return false;
    }

    /**
     * @param {Hash Table} htKeyMap
     * @require yona.ShortcutKey
     */
    function _initShortcutKey(htKeyMap){
        yona.ShortcutKey.setKeymapLink(htKeyMap);
    }

    _init(htOptions || {});
};

export default Global;

register("yona.project.Global", Global);
