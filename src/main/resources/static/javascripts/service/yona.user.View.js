/**
 * Yona, Project Hosting SW
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
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

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
            document.querySelectorAll(".post-item").forEach(function(el){
                el.addEventListener("click", function(e){
                    // .child-issue-list는 Bootstrap .hide 클래스(display:none)가 붙어 있어
                    // 인라인 스타일을 비우는 것만으로는 다시 보이지 않는다 - block을 강제한다.
                    this.querySelector(".child-issue-list").style.display = "block";
                });
            });

            document.querySelectorAll(".title-wrap > .title").forEach(function(el){
                el.addEventListener("click", function(e){
                    e.stopPropagation();
                });
            });
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
            htElement.welDaysAgo = document.getElementById('daysAgoBtn');
            htElement.waTabs = document.querySelectorAll('a[data-toggle="tab"]');
            htElement.waLeaveProject = document.querySelectorAll("a.leaveProject");
            htElement.waBtnWatch   = document.querySelectorAll(".watchBtn");
        }

        /**
         * attach event
         */
        function _attachEvent(){
            // daysAgoBtn은 게스트 사용자에게는 렌더링되지 않는다
            // (user/view.html의 th:if="${currentUser == null || !currentUser.isGuest}").
            if(htElement.welDaysAgo){
                htElement.welDaysAgo.addEventListener("keypress", _onKeypressDaysAgo);
            }
            htElement.waLeaveProject.forEach(function(el){ el.addEventListener("click", _onClickBtnLeaveProject); });
            htElement.waBtnWatch.forEach(function(el){ el.addEventListener("click", _onClickBtnWatch); });
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickBtnWatch(weEvt){
            var welTarget = this;
            var sURL = welTarget.getAttribute("href");

            fetch(sURL, {"method": "post"})
                .then(function(response){
                    if(!response.ok){
                        return Promise.reject(response);
                    }
                    document.location.reload();
                })
                .catch(function(oRes){
                    var bOnWatching = welTarget.classList.contains("blue");
                    var sActionMsg = Messages(bOnWatching ? "project.unwatch" : "project.watch");

                    $yona.notify(Messages("error.failedTo", sActionMsg, oRes.status, oRes.statusText));
                });

            weEvt.preventDefault();
            return false;
        }

        /**
         * @param {Event}
         */
        function _onClickBtnLeaveProject(weEvt){
            var sProjectName = this.getAttribute("data-projectName");

            if(confirm(Messages("userinfo.leaveProject.confirm", sProjectName)) === false){
                weEvt.preventDefault();
                weEvt.stopPropagation();
                return false;
            }
        }

        /**
         * @param {Wrapped Event}
         */
        function _onKeypressDaysAgo(weEvt){
            if(weEvt.keyCode === 13){ // Enter 키에 대해서만
                weEvt.preventDefault();
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
            var oURI = parseUri(document.location.href);
            var sDaysAgo = htElement.welDaysAgo.value;
            var sOptGroups = (oURI.queryKey.groups) ? "&groups=" + oURI.queryKey.groups : "";

            return 'daysAgo=' + sDaysAgo + '&selected=' + htVar.sTabSelected + sOptGroups;
        }

        _init(htOptions || {});
    };
})("yona.user.View");
