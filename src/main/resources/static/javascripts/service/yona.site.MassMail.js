/**
 * Yona, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
            var htOpt = htOptions || {};
            _initVar(htOpt);
            _initElement(htOpt);
            _attachEvent();
        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.sURLProjects = htOptions.sURLProjects;
            htVar.sURLMailList = htOptions.sURLMailList;
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            // projects
            htElement.welInputProject = document.getElementById('input-project');
            htElement.welSelectedProjects = document.getElementById('selected-projects');
            htElement.welBtnSelectProject = document.getElementById('select-project');
            htElement.welBtnWriteEmail = document.getElementById('write-email');
            htElement.welProjectList = document.getElementById('project-list-wrap');
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welInputProject.addEventListener('keypress', _onKeyPressInputProject);
            htElement.welBtnSelectProject.addEventListener('click', _onClickSelectProject);
            htElement.welBtnWriteEmail.addEventListener('click', _onClickWriteEmail);
            new yona.ui.Typeahead(htElement.welInputProject, {
                "sActionURL": htVar.sURLProjects
            });

            var elMailWrap = document.querySelector('.mess-mail-wrap');
            elMailWrap.addEventListener('click', function(weEvt){
                var elLabel = weEvt.target.closest('[data-toggle="mail-type"]');
                if(elLabel && elMailWrap.contains(elLabel)){
                    _clickMailTypeLabel.call(elLabel);
                }
            });
        }

        /**
         * jQuery의 show()/hide()를 대체 - #project-list-wrap은 <div>라서 show 시
         * 기본 표시값인 "block"으로 되돌린다(원본 jQuery .show()와 동일 결과).
         *
         * @param {String} sAction "show" 또는 "hide"
         */
        function _setProjectListVisibility(sAction){
            htElement.welProjectList.style.display = (sAction === "show") ? "block" : "none";
        }

        function _clickMailTypeLabel() {
            var sAction = this.getAttribute('data-action');
            htElement.welSelectedProjects.innerHTML = "";
            _setProjectListVisibility(sAction);

        }

        /**
         * Bootstrap 2 $.fn.button('loading'/'reset')를 대체하는 최소 재현.
         * 원본도 setTimeout(fn, 0)으로 상태 전환을 미뤘으므로 동일하게 유지한다.
         *
         * @param {Element} el
         * @param {String} sState "loading" 또는 "reset"
         */
        function _setButtonState(el, sState){
            if(el._yonaResetText === undefined){
                el._yonaResetText = el.innerHTML;
            }

            el.innerHTML = (sState === "loading") ?
                (el.getAttribute("data-loading-text") || "loading...") : el._yonaResetText;

            setTimeout(function(){
                if(sState === "loading"){
                    el.classList.add("disabled");
                    el.setAttribute("disabled", "disabled");
                } else {
                    el.classList.remove("disabled");
                    el.removeAttribute("disabled");
                }
            }, 0);
        }

        /**
         * Launch a mail client to write an email.
         */
        function _onClickWriteEmail() {
            // Get project names from labels in #selected-projects div.
            var sMailingType = document.querySelector('[name=mailingType]:checked').value;
            var waProjectSpan, aProjects;
            if (sMailingType == 'all') {
                aProjects = {'all': 'true'}
            } else {
                waProjectSpan = document.querySelectorAll('#selected-projects > .label');
                aProjects = [];
                for (var i = 0; i < waProjectSpan.length; i++) {
                    aProjects.push(waProjectSpan[i].childNodes[0].nodeValue.trim());
                }
            }

            // Send a request contains project names to get email addresses and
            // launch user's mail client with them using mailto scheme.
            _setButtonState(htElement.welBtnWriteEmail, "loading");

            $yona.sendForm({
                "sURL"      : htVar.sURLMailList,
                "htOptForm": {"method":"POST"},
                "htData"    : aProjects,
                "sDataType" : "json",
                "fOnLoad"   : function(data) {
                    var form = document.createElement('form');
                    var mailto = 'mailto:';
                    for (var i = 0; i < data.length; i++) {
                        mailto += data[i] + ',';
                    }
                    console.log(mailto);
                    form.setAttribute('method', 'POST');
                    form.setAttribute('action', mailto);
                    form.setAttribute('enctype', 'text/plain');
                    form.submit();
                    _setButtonState(htElement.welBtnWriteEmail, "reset");
                }
            });
        }

        /**
         * Add a project, which user types in #input-project element, into
         * #selected-projects div.
         */
        function _onClickSelectProject() {
            _appendProjectLabel(htElement.welInputProject.value);
            htElement.welInputProject.value = "";
            return false;
        }

        /**
         * Same as _onClickSelectProject but triggered by pressing enter.
         *
         * @param {Object} oEvent
         */
        function _onKeyPressInputProject(oEvent) {
            if (oEvent.keyCode == 13) {
                _appendProjectLabel(htElement.welInputProject.value);
                htElement.welInputProject.value = "";
                return false;
            }
        }

        /**
         * Make a project label by given name.
         *
         * sName은 원본 jQuery 버전(`$('<span>'+sName+'</span>')`)도 HTML로 그대로 파싱해
         * 넣었으므로 동일하게 innerHTML로 삽입한다(동치 유지 목적, 신규 이스케이프 도입 안 함).
         *
         * @param {String} sName
         */
        function _createProjectLabel(sName) {
            var welProject = document.createElement('span');
            welProject.className = 'label label-info';
            welProject.innerHTML = sName + " ";
            welProject.style.marginRight = '5px';

            var elUnselect = document.createElement('a');
            elUnselect.setAttribute('href', 'javascript:void(0)');
            elUnselect.textContent = 'x';
            elUnselect.addEventListener('click', function() {
                welProject.remove();
            });
            welProject.appendChild(elUnselect);

            return welProject;
        }

        /**
         * Append the given projects on #selected-projects div to show them.
         *
         * @param {Object} htProjects
         */
        function _appendProjectLabel(sTags) {
            htElement.welSelectedProjects.appendChild(_createProjectLabel(sTags));
        }

        _init(htOptions);
    };

})("yona.site.MassMail");
