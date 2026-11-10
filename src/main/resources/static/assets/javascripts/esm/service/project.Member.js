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

const Member = function(htOptions){

    const htVar = {};
    const htElement = {};

    /**
     * initialize
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement();
        _attachEvent();

        htElement.inputAddNewMember.focus();
    }

    /**
     * initialize variables
     */
    function _initVar(){
        htVar.sActionURL = htOptions.sActionURL;
        htVar.htData = htOptions.htData || {};
        htVar.rxContentRange = /items\s+([0-9]+)\/([0-9]+)/;

        htVar.oTypeahead = new yona.ui.Typeahead("#loginId", {
            "htData" : {
                "minLength" : 1,
                "updater" : _updater,
                "source" : _source,
                "render" : _render
            }
        });
    }

    /**
     * 목록에 그릴 항목 하나의 HTML이다. 서버가 준 `info`(아바타 이미지와 이름)를 그대로 보여 준다.
     */
    function _render(item) {
        return item;
    }

    function _updater(item) {
        return htVar.htUserData[item].loginId;
    }

    /**
     * For more information, See "source" option at
     * http://twitter.github.io/bootstrap/javascript.html#typeahead
     *
     * @param {Function} fProcess
     */
    function _source(sQuery, fProcess) {
        if (sQuery.match(htVar.sLastQuery) && htVar.bIsLastRangeEntire) {
            fProcess(htVar.htCachedUsers);
        } else {
            htVar.htData.query = sQuery;
            $yona.sendForm({
                "sURL"      : htVar.sActionURL,
                "htOptForm" : {"method":"get"},
                "htData"    : htVar.htData,
                "sDataType" : "json",
                "fOnLoad"   : function(oData, oStatus, oXHR){
                    let sContentRange = oXHR.getResponseHeader('Content-Range');

                    htVar.bIsLastRangeEntire = _isEntireRange(sContentRange);
                    htVar.sLastQuery = sQuery;

                    const userData = {};
                    const userInfos = [];
                    oData.forEach((user) => {
                        userData[user.info] = user;
                        userInfos.push(user.info);
                    });

                    htVar.htUserData = userData;
                    htVar.htCachedUsers = userInfos;
                    fProcess(userInfos);
                    sContentRange = null;
                }
            });
        }
    }

    /**
     * Return whether the given content range is an entire range for items.
     * e.g) "items 10/10"
     *
     * @param {String} sContentRange the value of Content-Range header from response
     * @return {Boolean}
     */
     function _isEntireRange(sContentRange){
         const aMatch = htVar.rxContentRange.exec(sContentRange || ""); // [1]=total, [2]=items
         return (aMatch) ? !(parseInt(aMatch[1], 10) < parseInt(aMatch[2], 10)) : true;
     }

    /**
     * initialize element variables
     */
    function _initElement(){
        htElement.waBtns = $(".btns");
        htElement.enrollAcceptBtns = $(".enrollAcceptBtn");
        htElement.memberListWrap = $('.members');
        htElement.formAddNewMember = $("#addNewMember");
        htElement.inputAddNewMember = $("#loginId");
    }

    /**
     * attach event handlers
     */
    function _attachEvent(){
        htElement.memberListWrap.on('click','[data-action="apply"]',_onClickApply);
        htElement.memberListWrap.on('click','[data-action="delete"]',_onClickDelete);
        htElement.enrollAcceptBtns.on("click", _onClickEnrollAcceptBtns);
        htElement.formAddNewMember.on("submit", _onSubmitAddNewMember);
    }

    /**
     * Submit event handler of addNewMember form.
     * Requests add new member with XHR instead of submitting form.
     *
     * @param evt
     * @returns {boolean}
     * @private
     */
    function _onSubmitAddNewMember(evt){
        yona.http.ajax(htElement.formAddNewMember.attr("action"), {
            "method"  : "post",
            "dataType": "json",
            "data"    : {
                "loginId": htElement.inputAddNewMember.val()
            }
        }).done(() =>{
            location.reload();
        }).fail((res) =>{
            const error = JSON.parse(res.responseText);
            $yona.notify(error.loginId.pop(), 3000);
        });

        evt.preventDefault();
        return false;
    }

    /**
     * @param {Wrapped Event} weEvt
     */
    function _onClickEnrollAcceptBtns(weEvt){
        weEvt.preventDefault();
        const loginId = $(this).attr('data-loginId');
        $('#loginId').val(loginId);
        $('#addNewMember').submit();
    }

    /**
     * @param {Wrapped Element} weltArget
     */
    function _onClickDelete(){
        const sURL = $(this).attr("data-href");

        $yona.ajaxConfirm(Messages("project.member.deleteConfirm"),
            {
                "url"     : sURL,
                "method"  : "delete",
                "dataType": "html",
                "success" : _onSuccessDeleteMember,
                "error"   : _onErrorDeleteMember
            },
            "",
            {
                "aButtonLabels": [Messages("button.no"), Messages("button.yes")],
                "aButtonStyles": ["ybtn-default", "ybtn-danger"]
            });
    }

    function _onSuccessDeleteMember(sResult){
        const htData = JSON.parse(sResult);
        document.location.replace(htData.location);
    }

    /**
     * @param {Object} oXHR
     */
    function _onErrorDeleteMember(oXHR){
        let sErrorMsg;

        switch(oXHR.status){
            case 403:
                var sNeedle = Messages("project.member.ownerCannotLeave");
                sErrorMsg = (oXHR.responseText.indexOf(sNeedle) > -1) ? sNeedle : Messages("error.forbidden");
                break;

            case 404:
                sErrorMsg = Messages("project.is.empty");
                break;

            default:
                sErrorMsg = Messages("error.badrequest");
                break;
        }

        $yona.alert(sErrorMsg);
    }

    /**
     * @param {Wrapped Element} welTarget
     */
    function _onClickApply(){
        const sURL = $(this).attr("data-href");
        const sLoginId = $(this).attr("data-loginId");
        const sRoleId = $('input[name="roleof-' + sLoginId + '"]').val();

        if(typeof sRoleId == "undefined"){
            //console.log("cannot find Role Id");
            return false;
        }

        // send request
        $yona.sendForm({
            "sURL"   : sURL,
            "htData" : {"id": sRoleId}
        });
    }

    _init(htOptions);
};

export default Member;

register("yona.project.Member", Member);
