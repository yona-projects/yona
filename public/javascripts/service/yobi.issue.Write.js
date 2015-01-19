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
(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _initVar(htOptions || {});
            _attachEvent();
            _initFileUploader();

            htElement.welInputTitle.focus();
        }

        /**
         * initialize variable
         */
        function _initVar(htOptions){
            htVar.sMode = htOptions.sMode || "new";
            htVar.sIssueId = htOptions.sIssueId || null;
            htVar.sIssueListURL = htOptions.sIssueListURL;
            htVar.sIssueFormURL = htOptions.sIssueFormURL;
            htVar.sTplFileItem = htOptions.sTplFileItem || htElement.welTplFileItem.text();
            htVar.bUnloadEvent = false;
        }

        /**
         * initialize element variable
         */
        function _initElement(htOptions){
            htElement.welUploader = $(htOptions.elUploader || "#upload");
            htElement.welIssueOptions = $(htOptions.elIssueOptions || "#options");
            htElement.welTextarea = $(htOptions.elTextarea || "#body");
            htElement.welInputTitle = $(htOptions.elInputTitle || "#title");
            htElement.welBtnManageLabel = $(htOptions.welBtnManageLabel || "#manage-label-link");
            htElement.welMilestoneRefresh = $(htOptions.elMilestoneRefresh || ".icon-refresh");
            htElement.welTplFileItem = $('#tplAttachedFile');
            htElement.welAssignee = $("#assignee");
            htElement.welDueDate = $(htOptions.elDueDate || "#issueDueDate");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            $("form").submit(_onSubmitForm);
            htElement.welIssueOptions.on("click", htElement.welMilestoneRefresh, _onReloadMilestone);

            htElement.welTextarea.on({
                "focus": function(){
                    if(htVar.bUnloadEvent === false){
                        $(window).on("beforeunload", _onBeforeUnload);
                        htVar.bUnloadEvent = true;
                    }
                }
            });

            htElement.welAssignee.on("change", function(weEvt){
                htElement.welAssignee.select2("val", weEvt.val);
            });

            htElement.welAssignee.on("select2-selecting", function(weEvt){
                if($(weEvt.object.element).data("forceChange")){
                    htElement.welAssignee.trigger("change");
                }
            });
        }

        function _onBeforeUnload(){
            if($yobi.getTrim(htElement.welTextarea.val()).length > 0){
                return Messages("issue.error.beforeunload");
            }
        }

        function _onReloadMilestone() {
            $.get(htVar.sIssueFormURL, function(data){
                var context = data.replace("<!DOCTYPE html>", "").trim();
                var milestoneOptionDiv = $("#milestoneOption", context);
                $("#milestoneOption").html(milestoneOptionDiv.html());
                (new yobi.ui.Dropdown({"elContainer":"#milestoneId"}));
            });
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                (new yobi.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader.attr("data-namespace")
                }));
            }
        }

        function _onSubmitForm(){
            var sTitle = $yobi.getTrim(htElement.welInputTitle.val());

            if(sTitle.length < 1){
                $yobi.alert(Messages("issue.error.emptyTitle"), function(){
                    htElement.welInputTitle.focus();
                });
                return false;
            }

            var sDueDate = $yobi.getTrim(htElement.welDueDate.val());

            if (sDueDate && !moment(sDueDate).isValid()) {
                $yobi.notify(Messages("issue.error.invalid.duedate"), 3000);
                htElement.welDueDate.focus();
                return false;
            }

            $(window).off("beforeunload", _onBeforeUnload);
            return true;
        }

        _init(htOptions);
    };
})("yobi.issue.Write");
