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
(function(ns){

    var oNS = $yobi.createNamespace(ns);
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

            // in loading, click previously selected tab
            if(htOptions.sTabSelected){
                $('a[href=#' + htOptions.sTabSelected + '][data-toggle=tab]').trigger('click');
            }
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
            htElement.waTabs = $('a[data-toggle="tab"]');
            htElement.waLeaveProject = $("a.leaveProject");
            htElement.waBtnWatch   = $(".watchBtn");
        }

        /**
         * attach event
         */
        function _attachEvent(){
            htElement.welDaysAgo.on("keypress", _onKeypressDaysAgo);
            htElement.waTabs.on("click", _onClickTabs);
            htElement.waLeaveProject.on("click", _onClickBtnLeaveProject);
            htElement.waBtnWatch.on("click",_onClickBtnWatch);
        }

        /**
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnWatch(weEvt){
            var welTarget = $(this);
            var sURL = welTarget.attr("href");

            $.ajax(sURL, {
                "method" : "post",
                "success": function(){
                    document.location.reload();
                },
                "error"  : function(oRes){
                    var bOnWatching = welTarget.hasClass("blue");
                    var sActionMsg = Messages(bOnWatching ? "project.unwatch" : "project.watch");

                    $yobi.notify(Messages("error.failedTo", sActionMsg, oRes.status, oRes.statusText));
                }
            });

            weEvt.preventDefault();
            return false;
        }

        /**
         * @param {Wrapped Event}
         */
        function _onClickBtnLeaveProject(weEvt){
            var sProjectName = $(this).attr("data-projectName");

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
                _rememberCurrentTab();
                document.location.href = '?' + _getTabQueryString();
                return false;
            }
        }

        /**
         * @param {Wrapped Event}
         */
        function _onClickTabs(weEvt){
            // get href link and remove '#' from #link from 'href' value
            var sHref = $(this).attr('href');
            _rememberCurrentTab(sHref.substr(sHref.indexOf("#") + 1));

            ($.inArray($(this).attr('href'), htVar.aDaysAgoTargets) === -1) ? _disableDaysAgo() : _enableDaysAgo();
        }

        function _enableDaysAgo(){
            htElement.welDaysAgo.prop('disabled', false);
            htElement.welDaysAgo.css('color','');
            htElement.welDaysAgo.blur();
        }

        function _disableDaysAgo(){
            htElement.welDaysAgo.prop('disabled', true);
            htElement.welDaysAgo.css('color','#eee');
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
            var sDaysAgo = htElement.welDaysAgo.val();
            var sOptGroups = (oURI.queryKey.groups) ? "&groups=" + oURI.queryKey.groups : "";

            return 'daysAgo=' + sDaysAgo + '&selected=' + htVar.sTabSelected + sOptGroups;
        }

        _init(htOptions || {});
    };
})("yobi.user.View");
