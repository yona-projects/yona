/**
 * Yona, Project Hosting SW
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
            htVar.rxPrjName = /^[0-9A-Za-z-_\.가-힣]+$/;
            htVar.aReservedWords = [".", "..", ".git"];
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            // 프로젝트 설정 관련
            htElement.welForm = document.querySelector("form#saveSetting");
            htElement.welInputLogo = document.getElementById("logoPath");
            htElement.welInputName = document.querySelector("input#project-name");
            htElement.welBtnSave   = document.getElementById("save");
            htElement.welReviewerCount = document.getElementById("welReviewerCount");
            htElement.welMenuSettingCode = document.getElementById("menuSettingCode");
            htElement.welMenuSettingPullRequest = document.getElementById("menuSettingPullRequest");
            htElement.welReviewerCountDisable = document.getElementById('reviewerCountDisable');
            htElement.welMenuSettingReview = document.getElementById("menuSettingReview");
            htElement.welReviewerCountSettingPanel = document.getElementById("reviewerCountSettingPanel");
            htElement.welDefaultBranceSettingPanel = document.getElementById("defaultBranceSettingPanel");
            htElement.welSubMenuProjectChangeVCS = document.getElementById("subMenuProjectChangeVCS");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welInputLogo.addEventListener("change", _onChangeLogoPath);

            htElement.welBtnSave.addEventListener("click", _onClickBtnSave);

            htElement.welMenuSettingCode.addEventListener('click', _onClickMenuSettingCode);
            htElement.welMenuSettingPullRequest.addEventListener('click', _onClickMenuSettingPullRequest);
            htElement.welMenuSettingReview.addEventListener('click', _onClickMenuSettingReview);

            // welReviewerCount 등은 project.vcs가 GIT일 때만 렌더링되는 패널
            // (project/setting.html의 th:if="...toUpperCase() == 'GIT'") 안에 있어
            // SVN 프로젝트에서는 존재하지 않는다.
            if(htElement.welReviewerCount && htElement.welReviewerCount.dataset.value === "true") {
                htElement.welReviewerCount.style.display = "";
            }

            document.querySelectorAll(".reviewer-count-wrap").forEach(function(wrap){
                wrap.addEventListener("click", function(e){
                    var match = e.target.closest('[data-toggle="reviewer-count"]');
                    if(match && wrap.contains(match)){
                        _toggleReviewerCount.call(match);
                    }
                });
            });
        }

        function _toggleReviewerCount(){
            var sAction = this.dataset.action;
            htElement.welReviewerCount.style.display = (sAction === "show") ? "" : "none";
        }

        function _onChangeLogoPath(){
            var welTarget = this;

            if($yona.isImageFile(welTarget) === false){
                $yona.showAlert(Messages("project.logo.alert"));
                welTarget.value = '';
                return;
            }

            htElement.welForm.submit();
        }

        function _onClickBtnSave(event){
            var sPrjName = htElement.welInputName.value;
            if(!htVar.rxPrjName.test(sPrjName)){
                event.preventDefault();
                $yona.showAlert(Messages("project.name.alert"));
                return false;
            }
            if(htVar.aReservedWords.indexOf(sPrjName) >= 0){
                event.preventDefault();
                $yona.showAlert(Messages("project.name.reserved.alert"));
                return false;
            }

            return true;
        }

        function _onClickMenuSettingCode() {
            var isChecked = this.checked;

            if (!isChecked) {
                htElement.welMenuSettingCode.checked = false;
                htElement.welMenuSettingPullRequest.checked = false;
                htElement.welMenuSettingReview.checked = false;
                if(htElement.welReviewerCountDisable){
                    htElement.welReviewerCountDisable.click();
                }

                if(htElement.welReviewerCountSettingPanel){
                    htElement.welReviewerCountSettingPanel.style.display = "none";
                }
                if(htElement.welDefaultBranceSettingPanel){
                    htElement.welDefaultBranceSettingPanel.style.display = "none";
                }
                htElement.welSubMenuProjectChangeVCS.style.display = "none";
            }
        }

        function _onClickMenuSettingPullRequest() {
            var isChecked = this.checked;

            if(isChecked) {
                htElement.welMenuSettingCode.checked = true;
                if(htElement.welReviewerCountSettingPanel){
                    htElement.welReviewerCountSettingPanel.style.display = "";
                }
            } else {
                if(htElement.welReviewerCountSettingPanel){
                    htElement.welReviewerCountSettingPanel.style.display = "none";
                }
                if(htElement.welReviewerCountDisable){
                    htElement.welReviewerCountDisable.click();
                }
            }
        }

        function _onClickMenuSettingReview() {
            var isChecked = this.checked;

            if(isChecked) {
                htElement.welMenuSettingCode.checked = true;
            }
        }

        _init(htOptions);
    };

})("yona.project.Setting");
