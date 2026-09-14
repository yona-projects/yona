/**
 * Yona, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Changsung Kim
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
         */
        function _init(htOptions){
            _initVar(htOptions);
            _initElement();
            _attachEvent();
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

        function _render(items) {
            var that = this;

            items = items.map(function(item) {
                var elItem = _htmlToElement(that.options.item);
                elItem.setAttribute('data-value', item);

                var elLinkHtml = document.createElement('div');
                elLinkHtml.appendChild(document.createTextNode(item));
                var elAnchor = elItem.querySelector('a');
                if(elAnchor){
                    elAnchor.innerHTML = elLinkHtml.innerHTML;
                }
                return elItem;
            });

            if(items[0]){
                items[0].classList.add('active');
            }
            this.$menu.innerHTML = "";
            items.forEach(function(el){ that.$menu.appendChild(el); });
            return this;
        }

        /**
         * @param {String} sHtml
         * @return {Element}
         */
        function _htmlToElement(sHtml){
            var elWrap = document.createElement('div');
            elWrap.innerHTML = sHtml.trim();
            return elWrap.firstElementChild;
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
                    "sURL"        : htVar.sActionURL,
                    "htOptForm"    : {"method":"get"},
                    "htData"    : htVar.htData,
                    "sDataType" : "json",
                    "fOnLoad"    : function(oData, oStatus, oXHR){
                        var sContentRange = oXHR.getResponseHeader('Content-Range');

                        htVar.bIsLastRangeEntire = _isEntireRange(sContentRange);
                        htVar.sLastQuery = sQuery;

                        var userData = {};
                        var userInfos = [];
                        (oData || []).forEach(function (user) {
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
            var aMatch = htVar.rxContentRange.exec(sContentRange || ""); // [1]=total, [2]=items
            return (aMatch) ? !(parseInt(aMatch[1], 10) < parseInt(aMatch[2], 10)) : true;
        }

        /**
         * initialize element variables
         */
        function _initElement(){
            htElement.waBtns = document.querySelectorAll(".btns");
            htElement.enrollAcceptBtns = document.querySelectorAll(".enrollAcceptBtn");
            htElement.memberListWrap = document.querySelector('.members');

            htElement.welAlertDelete = document.getElementById("alertDeletion");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.memberListWrap.addEventListener('click', function(weEvt){
                var elApply = weEvt.target.closest('[data-action="apply"]');
                if(elApply && htElement.memberListWrap.contains(elApply)){
                    _onClickApply.call(elApply, weEvt);
                    return;
                }
                var elDelete = weEvt.target.closest('[data-action="delete"]');
                if(elDelete && htElement.memberListWrap.contains(elDelete)){
                    _onClickDelete.call(elDelete, weEvt);
                }
            });

            htElement.enrollAcceptBtns.forEach(function(el){
                el.addEventListener('click', _onClickEnrollAcceptBtns);
            });

            document.getElementById('loginId').focus();
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickEnrollAcceptBtns(weEvt){
            weEvt.preventDefault();
            var loginId = this.getAttribute('data-loginId');
            document.getElementById('loginId').value = loginId;
            document.getElementById('addNewMember').submit();
        }

        /**
         * @param {Element} this 클릭된 delete 액션 엘리먼트
         */
        function _onClickDelete(){
            var sURL = this.getAttribute("data-href");

            // DELETE 메소드로 AJAX 호출
            document.getElementById("deleteBtn").addEventListener('click', function(){
                fetch(sURL, {"method": "delete"})
                    .then(function(response){
                        if(!response.ok){
                            return response.text().then(function(text){
                                return Promise.reject({"status": response.status, "responseText": text});
                            });
                        }
                        return response.text();
                    })
                    .then(_onSuccessDeleteMember)
                    .catch(_onErrorDeleteMember);
            });

            _showConfirmDeleteMember(sURL);
        }

        function _onSuccessDeleteMember(sResult){
            var htData = JSON.parse(sResult);
            document.location.replace(htData.location);
        }

        /**
         * @param {Object} oXHR
         */
        function _onErrorDeleteMember(oXHR){
            var sErrorMsg;

            switch(oXHR.status){
                case 403:
                    var sNeedle = Messages("project.member.ownerCannotLeave");
                    sErrorMsg = (oXHR.responseText.indexOf(sNeedle) > -1) ? sNeedle : Messages("error.forbidden");
                    break;

                case 404:
                    sErrorMsg = Messages("organization.member.unknownOrganization");
                    break;

                default:
                    sErrorMsg = Messages("error.badrequest");
                    break;
            }

            $yona.alert(sErrorMsg);
            htElement.welAlertDelete.close();
        }

        /**
         * @param {String} sURL
         */
        function _showConfirmDeleteMember(sURL){
            htElement.welAlertDelete.showModal();
        }

        /**
         * @param {Element} this 클릭된 apply 액션 엘리먼트
         */
        function _onClickApply(){
            var sURL = this.getAttribute("data-href");
            var sLoginId = this.getAttribute("data-loginId");
            var elRole = document.querySelector('input[name="roleof-' + sLoginId + '"]');
            var sRoleId = elRole ? elRole.value : undefined;

            if(typeof sRoleId == "undefined"){
                //console.log("cannot find Role Id");
                return false;
            }

            // send request
            $yona.sendForm({
                "sURL"   : sURL,
                "htData" : {"id": sRoleId},
                "fOnLoad" : function(oData, oStatus, oXHR) {
                    console.log("oXHR.responseText:" + oXHR.responseText);
                    if (oXHR.responseText != "") {
                        var htData = JSON.parse(oXHR.responseText);
                        document.location.replace(htData.location);
                    }
                }
            });
        }

        _init(htOptions);
    };

})("yona.organization.Member");
