/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author JiHan Kim
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
         */
        function _init(htOptions){
            _initVar(htOptions || {});
            _initElement(htOptions || {});
            _attachEvent();

            _initFileUploader();
        }

        /**
         * initialize variables
         */
        function _initVar() {
            htVar.sFormURL = htOptions.sFormURL;
            htVar.oFromProject = new yobi.ui.Dropdown({"elContainer": htOptions.welFromProject});
            htVar.oToProject = new yobi.ui.Dropdown({"elContainer": htOptions.welToProject});
            htVar.oFromBranch  = new yobi.ui.Dropdown({"elContainer": htOptions.welFromBranch});
            htVar.oToBranch  = new yobi.ui.Dropdown({"elContainer": htOptions.welToBranch});
            htVar.sUploaderId = null;
            htVar.oSpinner = null;

            htVar.htUserInput = {};
            htVar.sTplFileItem = $('#tplAttachedFile').text();
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = $("form.nm");
            htElement.welInputTitle = $('#title');
            htElement.welInputBody  = $('#body');

            htElement.welInputFromProject = $('input[name="fromProjectId"]');
            htElement.welInputToProject = $('input[name="toProjectId"]');
            htElement.welInputFromBranch = $('input[name="fromBranch"]');
            htElement.welInputToBranch = $('input[name="toBranch"]');

            htElement.welUploader = $("#upload");
            htElement.welContainer = $("#frmWrap");

            htElement.welAbleToMerge = $("#ableToMerge");
            htElement.welCommitCount = $("#commitCount");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welForm.submit(_onSubmitForm);
            htElement.welInputTitle.on("keyup", _onKeyupInput);
            htElement.welInputBody.on("keyup", _onKeyupInput);

            htVar.oFromProject.onChange(_refreshNewPullRequestForm);
            htVar.oToProject.onChange(_refreshNewPullRequestForm);
            htVar.oFromBranch.onChange(_reloadNewPullRequestForm);
            htVar.oToBranch.onChange(_reloadNewPullRequestForm);

            $(document.body).on("click", "button.moreBtn", function(){
                $(this).next("pre.commitMsg.desc").toggleClass("hidden");
            });

            $('body').on('click','button.more',function(){
               $(this).next('pre').toggleClass("hidden");
            });

            _reloadNewPullRequestForm();
        }

        /**
         * @param {Wrapped Event} weEvt
         */
        function _onKeyupInput(weEvt){
            var welTarget = $(weEvt.target);
            var sInputId = welTarget.attr("id");
            htVar.htUserInput = htVar.htUserInput || {};
            htVar.htUserInput[sInputId] = true;
        }

        /**
         * @private
         */
        function _refreshNewPullRequestForm(){
            var htData = {};

            htData.fromProjectId = htVar.oFromProject.getValue();
            htData.toProjectId = htVar.oToProject.getValue();

            document.location.href = htVar.sFormURL + "?fromProjectId=" + htData.fromProjectId + "&toProjectId=" + htData.toProjectId;
        }

        /**
         * request to reload pullRequestForm
         */
        function _reloadNewPullRequestForm(){
            var htData = {};
            htData.fromBranch = htVar.oFromBranch.getValue();
            htData.toBranch = htVar.oToBranch.getValue();
            htData.fromProjectId = htVar.oFromProject.getValue();
            htData.toProjectId = htVar.oToProject.getValue();

            if(!(htData.fromBranch && htData.toBranch)) {
                return;
            }

            _startSpinner();

            $.ajax(htVar.sFormURL, {
                "method" : "get",
                "data"   : htData,
                "success": _onSuccessReloadForm,
                "error"  : _onErrorReloadForm
            });
        }

        /**
         * onSuccess to reloadForm
         */
        function _onSuccessReloadForm(sRes){
            var sTitle = htElement.welInputTitle.val();
            var sBody = htElement.welInputBody.val();

            htElement.welContainer.html(sRes);
            _reloadElement();

            // 만약 사용자가 입력한 제목이나 본문이 있으면 내용을 유지한다
            if(sTitle.length > 0 && htVar.htUserInput.title){
                htElement.welInputTitle.val(sTitle);
            }
            if(sBody.length > 0 && htVar.htUserInput.body){
                htElement.welInputBody.val(sBody);
            }

            _initFileUploader();
            _stopSpinner();
            _updateSummary();
        }

        function _updateSummary() {
            var success = $(".alert-success");
            if(success) {
                htElement.welAbleToMerge.text(Messages("pullRequest.is.safe.to.merge"));
            } else {
                htElement.welAbleToMerge.text(Messages("pullRequest.is.not.safe.to.merge"));
            }

            var commits = $(".commits tr");
            if(commits) {
                htElement.welCommitCount.text($(".commits tr").length - 1);
            } else {
                htElement.welCommitCount.text(Messages("is.empty"))
            }

        }

        function _reloadElement(){
            htElement.welInputTitle = $('#title');
            htElement.welInputBody  = $('#body');
            htElement.welUploader = $("#upload");

            htElement.welInputTitle.on("keyup", _onKeyupInput);
            htElement.welInputBody.on("keyup", _onKeyupInput);
        }

        /**
         * onFailed to reloadForm
         */
        function _onErrorReloadForm(oRes){
            _stopSpinner();
            $yobi.alert(Messages("pullRequest.error.newPullRequestForm", oRes.status, oRes.statusText));
        }

        function _startSpinner(){
            htVar.oSpinner = htVar.oSpinner || new Spinner();
            htVar.oSpinner.spin(document.getElementById('spin'));
        }

        function _stopSpinner(){
            if(htVar.oSpinner){
                htVar.oSpinner.stop();
            }
            htVar.oSpinner = null;
        }

        /**
         * Event handler on submit form
         */
        function _onSubmitForm(weEvt){
            return _validateForm();
        }

        /**
         * Validate form before submit
         */
        function _validateForm(){
            // these two fields should be loaded dynamically.
            htElement.welInputFromBranch = $('input[name="fromBranch"]');
            htElement.welInputToBranch = $('input[name="toBranch"]');
            htElement.welInputFromProject = $('input[name="fromProjectId"]');
            htElement.welInputToProject = $('input[name="toProjectId"]');

            // check whether required field is empty
            var htRequired = {
                "title"     : $.trim(htElement.welInputTitle.val()),
                "fromProject": $.trim(htElement.welInputFromProject.val()),
                "toProject" : $.trim(htElement.welInputToProject.val()),
                "fromBranch": $.trim(htElement.welInputFromBranch.val()),
                "toBranch"  : $.trim(htElement.welInputToBranch.val())
            };

            for(var sMessageKey in htRequired){
                if(htRequired[sMessageKey].length === 0){
                    $yobi.alert(Messages("pullRequest." + sMessageKey + ".required"));
                    return false;
                }
            }

            if(!htVar.sFormURL) {
                return true;
            }

            var bCommitNotChanged = $.trim($("#commitChanged").val()) != "true";

            if(bCommitNotChanged) {
                $yobi.alert(Messages("pullRequest.diff.noChanges"));
                return false;
            }
            return true;
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            if(htVar.sUploaderId){
                htVar.oAttachments.destroy();
                yobi.Files.destroyUploader(htVar.sUploaderId);
                htVar.sUploaderId = null;
            }

            var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welInputBody);
            if(oUploader){
                htVar.sUploaderId = oUploader.attr("data-namespace");
                htVar.oAttachments = new yobi.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welInputBody,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : htVar.sUploaderId
                });
            }
        }

        _init(htOptions || {});
    };
})("yobi.git.Write");
