/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        // 호출부(issue/create.html, issue/edit.html)가 elDueDate/elMilestoneRefresh를
        // jQuery 객체($("#issueDueDate") 등)로 넘기므로 raw DOM으로 정규화한다.
        function _toElement(el){
            if(el && el.jquery){
                return el[0];
            }
            return el;
        }

        /**
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _initVar(htOptions || {});
            _attachEvent();
            _initFileUploader();

            htElement.welInputTitle.focus();
            htElement.welInputTitle.addEventListener('keydown', function (e) {
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
            htVar.sTplFileItem = htOptions.sTplFileItem || htElement.welTplFileItem.textContent;
            htVar.bUnloadEvent = false;
        }

        /**
         * initialize element variable
         */
        function _initElement(htOptions){
            htElement.welUploader = document.querySelector(htOptions.elUploader || "#upload");
            htElement.welIssueOptions = document.querySelector(htOptions.elIssueOptions || "#options");
            htElement.welTextarea = document.querySelector(htOptions.elTextarea || "#body");
            htElement.welInputTitle = document.querySelector(htOptions.elInputTitle || "#title");
            htElement.welBtnManageLabel = document.querySelector(htOptions.welBtnManageLabel || "#manage-label-link");
            // 이 값은 아래 _attachEvent의 기존 버그(비델리게이트 바인딩) 때문에 실제로는
            // 셀렉터로 쓰이지 않는다 - 저장만 하고 값 자체는 사용하지 않는다.
            htElement.welMilestoneRefresh = htOptions.elMilestoneRefresh;
            htElement.welTplFileItem = document.getElementById('tplAttachedFile');
            htElement.welAssignee = document.getElementById("assignee");
            htElement.welDueDate = _toElement(htOptions.elDueDate) || document.getElementById("issueDueDate");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            document.querySelectorAll("form").forEach(function(form){
                form.addEventListener("submit", _onSubmitForm);
            });

            // 원본 jQuery 코드는 .on("click", htElement.welMilestoneRefresh, _onReloadMilestone)로
            // 델리게이트 셀렉터 자리에 문자열이 아닌 jQuery 객체를 넘겼다 - jQuery의 .on() 오버로드
            // 해석 규칙상 이는 (selector, data, fn) -> (undefined, ..., _onReloadMilestone)로
            // 재해석돼 #options 전체에 바로 바인딩되는 기존 버그다(안 어디를 클릭해도 마일스톤
            // 갱신이 실행됨) - 동작을 바꾸지 않기 위해 그대로 재현한다. 다만 #options 자체가
            // 현재 어느 템플릿에도 없어 jQuery에서도 이미 무동작이었다 - null 가드로 보존한다.
            if(htElement.welIssueOptions){
                htElement.welIssueOptions.addEventListener("click", _onReloadMilestone);
            }

            htElement.welTextarea.addEventListener("focus", function(){
                if(htVar.bUnloadEvent === false){
                    window.addEventListener("beforeunload", _onBeforeUnload);
                    htVar.bUnloadEvent = true;
                }
            });

            temporarySaveHandler(htElement.welTextarea);

            // 인스턴스는 htElement.welAssignee.tomselect로 접근한다(yona.issue.Assginee.js가
            // 생성). weEvt.val은 yona.ui.TomSelect.js의 bridgeChangeEvent가 원본 select2
            // "change" 이벤트와 동일한 모양으로 채워 넣어준다. setValue의 두 번째 인자
            // (silent:true)는 이 정규화 재설정이 또 다른 change로 무한루프에 빠지지 않게 막는다.
            htElement.welAssignee.addEventListener("change", function(weEvt){
                var tomSelectInstance = htElement.welAssignee && htElement.welAssignee.tomselect;
                if(tomSelectInstance){
                    tomSelectInstance.setValue(weEvt.val, true);
                }
            });

            // data("forceChange")는 어느 템플릿/JS에서도 설정된 적이 없어 원본(select2)에서도
            // 이미 도달 불가능한 죽은 코드였다. Tom Select는 애초에 "select2-selecting"
            // 이벤트를 발생시키지 않으므로 이 바인딩은 등록만 되고 결코 실행되지 않는다 -
            // 동작 변화가 없어 그대로 보존한다.
            htElement.welAssignee.addEventListener("select2-selecting", function(weEvt){
                if(weEvt.object && weEvt.object.element._forceChange){
                    htElement.welAssignee.dispatchEvent(new Event("change"));
                }
            });
        }

        function _onBeforeUnload(){
            if($yona.getTrim(htElement.welTextarea.value).length > 0){
                return Messages("issue.error.beforeunload");
            }
        }

        function _onReloadMilestone() {
            fetch(htVar.sIssueFormURL).then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                return response.text();
            }).then(function(data){
                var context = data.replace("<!DOCTYPE html>", "").trim();
                var parsedDoc = new DOMParser().parseFromString(context, "text/html");
                var milestoneOptionDiv = parsedDoc.getElementById("milestoneOption");
                // jQuery의 .html(undefined)는 setter가 아니라 getter로 동작해 아무 것도 바꾸지
                // 않았다 - milestoneOptionDiv를 못 찾은 경우 원본과 동일하게 아무 것도 하지 않는다.
                if(milestoneOptionDiv){
                    document.getElementById("milestoneOption").innerHTML = milestoneOptionDiv.innerHTML;
                }
                (new yona.ui.Dropdown({"elContainer":"#milestoneId"}));
            });
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                (new yona.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader[0].getAttribute("data-namespace")
                }));
            }
        }

        function _onSubmitForm(event){
            var sTitle = $yona.getTrim(htElement.welInputTitle.value);

            if(sTitle.length < 1){
                event.preventDefault();
                $yona.alert(Messages("issue.error.emptyTitle"), function(){
                    htElement.welInputTitle.focus();
                });
                return false;
            }

            var sDueDate = $yona.getTrim(htElement.welDueDate.value);

            if (sDueDate && !moment(sDueDate).isValid()) {
                event.preventDefault();
                $yona.notify(Messages("issue.error.invalid.duedate"), 3000);
                htElement.welDueDate.focus();
                return false;
            }

            window.removeEventListener("beforeunload", _onBeforeUnload);

            removeCurrentPageTemprarySavedContent();

            return true;
        }

        _init(htOptions);
    };
})("yona.issue.Write");
