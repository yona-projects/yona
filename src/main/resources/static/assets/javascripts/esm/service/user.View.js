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

import { register } from "../namespace.js";

const View = function(htOptions){

    const htVar = {};
    const htElement = {};

    /**
     * initialize
     *
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement();
        _attachEvent();
        _initShowChildList();
        _initTwoColumnMode();
    }

    function _initShowChildList() {
        $(".post-item").on("click", function(e){
            $(this).find(".child-issue-list").show();
        });

        $(".title-wrap > .title").on("click", (e) =>{
            e.stopPropagation();
        })
    }

    /**
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions){
        htVar.oState = {};
        htVar.sTabSelected = htOptions.sTabSelected;
        htVar.aDaysAgoTargets = ['#postings','#pullRequests','#issues'];
    }

    /**
     * initialize elements
     */
    function _initElement(){
        htElement.welDaysAgo = $('#daysAgoBtn');
        htElement.waTabs = $('a[data-bs-toggle="tab"]');
        htElement.waLeaveProject = $("a.leaveProject");
        htElement.waBtnWatch   = $(".watchBtn");
    }

    /**
     * attach event
     */
    function _attachEvent(){
        htElement.welDaysAgo.on("keypress", _onKeypressDaysAgo);
        htElement.waLeaveProject.on("click", _onClickBtnLeaveProject);
        htElement.waBtnWatch.on("click",_onClickBtnWatch);
    }

    /**
     * @param {Wrapped Event} weEvt
     */
    function _onClickBtnWatch(weEvt){
        const welTarget = $(this);
        const sURL = welTarget.attr("href");

        yona.http.ajax(sURL, {
            "method" : "post",
            "success": function(){
                document.location.reload();
            },
            "error"  : function(oRes){
                const bOnWatching = welTarget.hasClass("blue");
                const sActionMsg = Messages(bOnWatching ? "project.unwatch" : "project.watch");

                $yona.notify(Messages("error.failedTo", sActionMsg, oRes.status, oRes.statusText));
            }
        });

        weEvt.preventDefault();
        return false;
    }

    /**
     * @param {Wrapped Event}
     */
    function _onClickBtnLeaveProject(weEvt){
        const sProjectName = $(this).attr("data-projectName");

        weEvt.preventDefault();
        weEvt.stopPropagation();

        if(confirm(Messages("userinfo.leaveProject.confirm", sProjectName)) === false){
            return false;
        }

        // 탈퇴는 상태를 바꾸므로 링크 이동(GET)이 아니라 폼 제출(POST)로 보낸다(R-48-2)
        const welForm = $('<form method="post" style="display:none"></form>').attr("action", $(this).attr("data-leave-url"));
        welForm.appendTo(document.body).submit();
        return false;
    }

    /**
     * @param {Wrapped Event}
     */
    function _onKeypressDaysAgo(weEvt){
        if($yona.isEnterKey(weEvt)){ // Enter 키에 대해서만
            _rememberCurrentTab();
            document.location.href = '?' + _getTabQueryString();
            return false;
        }
    }

    /**
     * @param {String} sTabSelected
     */
    function _rememberCurrentTab(sTabSelected){
        if(sTabSelected){
            htVar.sTabSelected = sTabSelected;
        }
        history.replaceState(htVar.oState, '', '?' + _getTabQueryString());
    }

    /**
     * @return {String}
     */
    function _getTabQueryString(){
        // 주소의 groups 값은 인코딩된 그대로 다시 붙인다
        const aGroups = /[?&]groups=([^&#]*)/.exec(document.location.search);
        const sDaysAgo = htElement.welDaysAgo.val();
        const sOptGroups = (aGroups && aGroups[1]) ? "&groups=" + aGroups[1] : "";

        return 'daysAgo=' + sDaysAgo + '&selected=' + htVar.sTabSelected + sOptGroups;
    }

    _init(htOptions || {});
};

export default View;

register("yona.user.View", View);
