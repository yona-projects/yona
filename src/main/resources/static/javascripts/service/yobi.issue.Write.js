/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

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
            htElement.welInputTitle.on('keydown', function (e) {
                if((e.keyCode || e.which) === 13) {
                    e.preventDefault();
                    htElement.welTextarea.focus();
                }
            });
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

            temporarySaveHandler(htElement.welTextarea);

            // P3-46 #5: Select2(v3) -> Tom Select 교체. 인스턴스는 htElement.welAssignee[0].tomselect로
            // 접근한다(yona.issue.Assginee.js가 생성). weEvt.val은 yobi.ui.Select2.js의
            // bridgeChangeEvent가 원본 select2 "change" 이벤트와 동일한 모양으로 채워 넣어준다.
            // setValue의 두 번째 인자(silent:true)는 이 정규화 재설정이 또 다른 change를 유발해
            // 무한루프로 이어지지 않도록 막는다.
            htElement.welAssignee.on("change", function(weEvt){
                var tomSelectInstance = htElement.welAssignee[0] && htElement.welAssignee[0].tomselect;
                if(tomSelectInstance){
                    tomSelectInstance.setValue(weEvt.val, true);
                }
            });

            // 범위 밖 발견(최종 보고 참고): data("forceChange")는 어느 템플릿/JS에서도 설정된 적이
            // 없어 이 분기는 원본(select2)에서도 이미 도달 불가능한 죽은 코드였다. Tom Select는
            // 애초에 "select2-selecting" 이벤트를 발생시키지 않으므로 이 바인딩은 등록은 되지만
            // 결코 실행되지 않는다 - 동작 변화가 없어 그대로 보존한다.
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

            removeCurrentPageTemprarySavedContent();

            return true;
        }

        _init(htOptions);
    };
})("yobi.issue.Write");
