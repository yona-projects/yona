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
    oNS.container[oNS.name] = function(options){

        var vars = {};
        var elements = {};

        /**
         * initialize
         */
        function _init(options){
            _initVar(options);
            _initElement(options);
            _attachEvent();
            _initFileUploader();
        }

        /**
         * initialize variables
         */
        function _initVar(options) {
            vars.mergeResult = {};
            vars.mergeResultURL = options.mergeResultURL;
            vars.tplFileItem = $('#tplAttachedFile').text();
            vars.uploaderId = null;
        }

        /**
         * initialize element variables
         */
        function _initElement(options){
            elements.form  = $("form.nm");
            elements.title = $('#title');
            elements.body  = $('textarea[data-editor-mode="content-body"]');

            elements.fromProject = options.fromProject;
            elements.fromBranch  = options.fromBranch;
            elements.toProject   = options.toProject;
            elements.toBranch    = options.toBranch;
            elements.state       = options.state;

            elements.uploader = $("#upload");
            elements.numOfCommits = $("#numOfCommits");
            elements.commits = $("#__commits");
            elements.status = $("#status");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            elements.form.on("submit", _onSubmitForm);
            elements.title.on("keyup", _onKeyUpInput);
            elements.body.on("keyup", _onKeyUpInput);

            // onChangeProject
            elements.fromProject.on("change", _onChangeProject);
            elements.toProject.on("change", _onChangeProject);

            // onChangeBranch
            elements.fromBranch.on("change", _checkMergeResult);
            elements.toBranch.on("change", _checkMergeResult);

            $(document.body).on("click", "button.moreBtn", function(){
                $(this).next("pre.commitMsg.desc").toggleClass("hidden");
            });

            if(elements.state === "OPEN") {
                _checkMergeResult();
            }
        }

        /**
         * "keyup" event handler of inputTitle, inputBody.
         * Mark as user has typed on this input, and detach this event handler.
         * Ignore if the pressed key is ENTER.
         *
         * @param {Wrapped Event} evt
         */
        function _onKeyUpInput(evt){
            var keyCode = (evt.keyCode || evt.which);

            if(keyCode !== 13){
                $(evt.target).data("isUserHasTyped", true)
                             .off("keyup", _onKeyUpInput);
            }
        }

        /**
         * Reload page with changed fromProjectId, toProjectId query string.
         *
         * @private
         */
        function _onChangeProject(){
            var data = _getFormValue();

            location.search = "?fromProjectId=" + data.fromProjectId + "&toProjectId=" + data.toProjectId;
        }

        /**
         * Request merge result with changed branch.
         *
         * @private
         */
        function _checkMergeResult(){
            var data = _getFormValue();

            if(!data.fromBranch && !data.toBranch){
                return;
            }

            _showMergeResult({"message" : Messages("pullRequest.is.merging")});

            NProgress.start();

            $.ajax(vars.mergeResultURL, {
                "data": data
            })
            .done(_onSuccessMergeResult)
            .fail(_onErrorMergeResult)
            .always(function(){
                NProgress.done();
            });
        }

        /**
         * On success to load mergeResult
         * Fill element.commits and form field title/body.
         * and show result with parsing responded HTML.
         *
         * @param resultHTML
         * @private
         */
        function _onSuccessMergeResult(resultHTML){
            elements.commits.html(resultHTML);
            vars.mergeResult = _getMergeResultData();
            _showMergeResult(vars.mergeResult);
            _fillFormTitleBody(vars.mergeResult);
        }

        /**
         * Returns merge result data to show.
         * {@code container} relative data relies on views/git/partial_merge_result.scala.html
         *
         * @returns {{cssClass: string, message: *, isConflict: boolean, numOfCommits: Number, title: *, body: *}}
         * @private
         */
        function _getMergeResultData(){
            var container = $("#mergeResult");
            var data = {
                "cssClass"    : "alert-info",
                "message"     : Messages("pullRequest.diff.noChanges"),
                "isConflict"  : (container.attr("data-conflict") === "true"),
                "numOfCommits": parseInt(container.attr("data-commits"), 10),
                "title"       : container.attr("data-pullrequest-title"),
                "body"        : container.attr("data-pullrequest-body")
            };

            if(data.numOfCommits > 0){
                data.message  = data.isConflict ? Messages("pullRequest.is.not.safe") : Messages("pullRequest.is.safe");
                data.cssClass = data.isConflict ? "alert-error" : "alert-success";
            }

            return data;
        }

        function _showMergeResult(mergeResult){
            elements.status.removeClass("alert-success alert-error alert-info")
                               .addClass(mergeResult.cssClass)
                               .html(mergeResult.message);

            elements.numOfCommits.html(mergeResult.numOfCommits || "");
        }

        /**
         * Fill form input title and body with merge result data
         * if user doesn't have typed.
         *
         * @param data
         * @private
         */
        function _fillFormTitleBody(data){
            var isUserHasTyped = elements.title.data("isUserHasTyped") ||
                                 elements.body.data("isUserHasTyped");

            if(!isUserHasTyped){
                elements.title.val(data.title);
                elements.body.val(data.body);
            }
        }

        /**
         * On error occurs to get merge result.
         * Show error message and response status.
         *
         * @param res
         * @private
         */
        function _onErrorMergeResult(res){
            _showMergeResult({
                "message" : Messages("pullRequest.error.newPullRequestForm", res.status, res.statusText),
                "cssClass": "alert-error"
            });
            _fillFormTitleBody({"title":"", "body":""});
        }

        /**
         * "submit" event handler of the form.
         * Returns false if validate fails.
         *
         * @returns {Boolean}
         * @private
         */
        function _onSubmitForm(){
            return _validateForm();
        }

        /**
         * Validate form before submit
         *
         * @returns {boolean}
         * @private
         */
        function _validateForm(){
            // Check whether is commit exists to send
            if(!vars.mergeResult.numOfCommits){
                $yobi.alert(Messages("pullRequest.diff.noChanges"));
                return false;
            }

            // Show confirm dialog in case of conflict
            if(vars.mergeResult.isConflict && !vars.mergeResult.forceSubmit){
                $yobi.confirm(Messages("pullRequest.ignore.conflict"), function(data){
                    if(data.nButtonIndex === 1){
                        vars.mergeResult.forceSubmit = true;
                        elements.form.submit();
                    }
                });
                return false;
            }

            // Check whether required field is empty
            var requiredField = _getFormValue();

            for(var fieldName in requiredField){
                if(requiredField[fieldName].length === 0){
                    $yobi.alert(Messages("pullRequest." + fieldName + ".required"));
                    return false;
                }
            }

            return true;
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            if(vars.uploaderId){
                vars.attachments.destroy();
                yobi.Files.destroyUploader(vars.uploaderId);
                vars.uploaderId = null;
            }

            var oUploader = yobi.Files.getUploader(elements.uploader, elements.body);

            if(oUploader){
                vars.uploaderId = oUploader.attr("data-namespace");
                vars.attachments = new yobi.Attachments({
                    "elContainer"  : elements.uploader,
                    "elTextarea"   : elements.body,
                    "sTplFileItem" : vars.tplFileItem,
                    "sUploaderId"  : vars.uploaderId
                });
            }
        }

        function _getFormValue(){
            return {
                "title"      : $.trim(elements.title.val()),
                "fromProjectId": $.trim(elements.fromProject.val()),
                "toProjectId"  : $.trim(elements.toProject.val()),
                "fromBranch" : $.trim(elements.fromBranch.val()),
                "toBranch"   : $.trim(elements.toBranch.val())
            };
        }

        _init(options || {});
    };
})("yobi.git.Write");
