/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Hwi Ahn
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

        function _init(htOptions){
            _initVar();
            _initElement(htOptions);
            _attachEvent();
            _focusOnFirstField();

            _showErrors(htOptions.htError);
        }

        /**
         * initialize variables
         */
        function _initVar(){
            htVar.rxPrjName = /^[0-9A-Za-z-_\.]+$/;
            htVar.aReservedWords = [".", "..", ".git"];
        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.welForm = $(htOptions.sFormId);
            htElement.welInputProjectName = $("#project-name");
            htElement.welInputProjectOwner = $("#project-owner");
            htElement.welInputGitRepoURL = $("#url");

            htElement.vcsSelect = $("#vcs");
            htElement.svnWarning = $("#svn");
            htElement.welProtected = $("#opt-protected");

            htElement.welRepoAuthCheck = $("#useRepoAuth");
            htElement.welRepoAuthWrap = $("#repoAuth");
            htElement.waRepoAuthInput = htElement.welRepoAuthWrap.find("input");

            htElement.welMenuSettingCode = $("#menuSettingCode");
            htElement.welMenuSettingPullRequest = $("#menuSettingPullRequest");
            htElement.welReviewerCountDisable = $('#reviewerCountDisable')
            htElement.welMenuSettingReview = $("#menuSettingReview");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            htElement.vcsSelect.on("change", _onChangeVCSItem);
            htElement.welInputProjectOwner.on("change", _onChangeProjectOwner);
            htElement.welForm.on("submit", _validateForm);
            htElement.welRepoAuthCheck.on("change", _onChangeRepoAuthCheck);

            htElement.welMenuSettingCode.on('click', _onClickMenuSettingCode);
            htElement.welMenuSettingPullRequest.on('click', _onClickMenuSettingPullRequest);
            htElement.welMenuSettingReview.on('click', _onClickMenuSettingReview);
        }

        function _onChangeRepoAuthCheck(){
            $("input").popover("destroy");
            htElement.welRepoAuthWrap.toggle("slide");
            htElement.waRepoAuthInput.attr("disabled", !htElement.welRepoAuthCheck.is(":checked"));
        }

        function _onChangeVCSItem(evt){
            if(evt.val.toUpperCase() === "SUBVERSION"){
                htElement.svnWarning.show();
                /**
                 * We don't know whether the user want to check this or not
                 * because this checkbox will be hidden. So we let it be true
                 * forcely. It may be misguessing of the user's choice but it
                 * prevents the possibiltiy that the user never knows the
                 * existence of PullRequest feature when the user changes the 
                 * project setting to use Git.
                 */   
                $('#menuSettingPullRequest')
                    .prop('checked', true)
                    .parent('label').hide();
            } else {
                htElement.svnWarning.hide();
                $('#menuSettingPullRequest')
                    .prop('checked', true)
                    .parent('label').show();
            }
        }

        function _onChangeProjectOwner() {
            var sType = $("#project-owner option:selected").data("type");

            if (sType == "user") {
                if ($("#protected").is(":checked")) {
                    $("#public").prop("checked", true);
                }
                htElement.welProtected.hide();
            } else {
                htElement.welProtected.show();
            }
        }

        function _focusOnFirstField(){
            if(htElement.welRepoAuthCheck.is(":checked")){
                htElement.waRepoAuthInput.get(0).focus();
            } else if(htElement.welInputGitRepoURL.length > 0){
                htElement.welInputGitRepoURL.focus();
            } else {
                htElement.welInputProjectName.focus();
            }
        }

        /**
         * Validate form on submit
         *
         * @private
         */
        function _validateForm(evt){
            var error = {};
            var projectName = htElement.welInputProjectName.val();

            if(projectName.length === 0){
                error.name = error.name || [];
                error.name.push(Messages("project.name.alert"));
            }

            if(!htVar.rxPrjName.test(projectName)){
                error.name = error.name || [];
                error.name.push(Messages("project.name.alert"));
            }

            if(htVar.aReservedWords.indexOf(projectName) > -1){
                error.name = error.name || [];
                error.name.push(Messages("project.name.reserved.alert"));
            }

            if(htElement.welInputGitRepoURL.length > 0 &&
               htElement.welInputGitRepoURL.val().trim().length === 0 ){
                error.url = error.url || [];
                error.url.push(Messages("project.import.error.empty.url"));
            }

            if(!$.isEmptyObject(error)){
                _showErrors(error);
                evt.preventDefault();
                return false;
            }

            NProgress.start();
        }

        /**
         * Show error message on target element with $.popover
         *
         * @param error
         * @private
         */
        function _showErrors(error){
            if(!error){
                return;
            }

            var targetElement;

            for(var target in error){
                targetElement = htElement.welForm.find("[name=" + target + "]");

                if(targetElement.length > 0) {
                    targetElement.popover({
                        "trigger"  : "manual",
                        "placement": "left",
                        "content"  : error[target].shift()
                    }).popover("show");
                }
            }
        }

        function _onClickMenuSettingCode() {
            var isChecked = $(this).prop("checked");
            
            if (!isChecked) {
                htElement.welMenuSettingCode.prop("checked", false);
                htElement.welMenuSettingPullRequest.prop("checked", false);
                htElement.welMenuSettingReview.prop("checked", false);
                
                htElement.welReviewerCountSettingPanel.hide();
                htElement.welDefaultBranceSettingPanel.hide();
                htElement.welSubMenuProjectChangeVCS.hide();
            }
        }

        function _onClickMenuSettingPullRequest() {
            var isChecked = $(this).prop("checked");
            
            if(isChecked) {
                htElement.welMenuSettingCode.prop("checked", true);
            } else {
                htElement.welReviewerCountSettingPanel.hide();    
            }   
        }

        function _onClickMenuSettingReview() {
            var isChecked = $(this).prop("checked");
            
            if(isChecked) {
                htElement.welMenuSettingCode.prop("checked", true);
            }     
        }

        _init(htOptions || {});
    };

})("yobi.project.New");
