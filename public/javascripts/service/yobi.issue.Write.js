/**
 * @(#)yobi.Issue.Write.js 2013.03.13
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */

(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * 초기화
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _initVar(htOptions || {});
            _attachEvent();
            _initFileUploader();

            // 제목 입력란에 포커스
            htElement.welInputTitle.focus();

            // zenForm
            _initZenForm();
        }

        /**
         * 변수 초기화
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
         * 엘리먼트 변수 초기화
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
        }

        /**
         * 이벤트 핸들러
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
        }

        /**
         * 입력하던 도중 페이지를 벗어나려고 하면 경고 메시지를 표시하도록
         */
        function _onBeforeUnload(){
            if($yobi.getTrim(htElement.welTextarea.val()).length > 0){
                return Messages("issue.error.beforeunload");
            }
        }

        /**
         * 마일스톤 정보 새로고침
         */
        function _onReloadMilestone() {
            $.get(htVar.sIssueFormURL, function(data){
                var context = data.replace("<!DOCTYPE html>", "").trim();
                var milestoneOptionDiv = $("#milestoneOption", context);
                $("#milestoneOption").html(milestoneOptionDiv.html());
                (new yobi.ui.Dropdown({"elContainer":"#milestoneId"}));
            });
        }

        /**
         * 파일 업로더 초기화
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

        /**
         * 폼 전송시 유효성 검사 함수
         */
        function _onSubmitForm(){
            var sTitle = $yobi.getTrim(htElement.welInputTitle.val());

            // 제목이 비어있으면
            if(sTitle.length < 1){
                $yobi.alert(Messages("issue.error.emptyTitle"), function(){
                    htElement.welInputTitle.focus();
                });
                return false;
            }

            $(window).off("beforeunload", _onBeforeUnload);
            return true;
        }

        /**
         * ZenForm 초기화
         * initialize zenForm
         */
        function _initZenForm(){
            $(".zen-mode").zenForm({"theme": "light"});
            $(".s--zen").tooltip({
                "delay": {"show": 500, "hide": 100},
                "title": Messages("title.zenmode"),
                "placement": "left"
            });
        }

        _init(htOptions);
    };
})("yobi.issue.Write");
