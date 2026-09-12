/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * PR 생성(create.html)/수정(edit.html) 화면이 공유하던 머지 가능 여부 체크(#status 배너 +
 * #__commits 갱신) 로직이 두 템플릿에 거의 동일하게 중복돼 있던 것을 이 모듈로 추출했다.
 * legacy yona.git.Write.js가 하던 역할이지만, 그 파일과 달리 create/edit의 실제 차이(브랜치
 * select 활성화 여부, 프로젝트 변경 시 리로드, 폼 제출 대상/페이로드)를 htOptions로 명시적으로
 * 받는다 - yona.issue.Write.js/yona.milestone.Write.js와 동일하게 sMode 기반으로 동작한다.
 *
 * @example
 * // create.html
 * $yona.loadModule("pullrequest.Write", {
 *     "sMode"           : "new",
 *     "sMergeResultURL" : "...",
 *     "elFromBranch"    : "#fromBranch",
 *     "elToBranch"      : "#toBranch",
 *     "elFromProjectId" : "#fromProjectId",
 *     "elToProjectId"   : "#toProjectId",
 *     "bAutoFillTitleBody": true,
 *     "sSubmitMethod"   : "POST",
 *     "sErrorPrefix"    : "생성 실패: "
 *     // sSubmitURL은 "new" 모드에서는 제출 시점의 toProjectId로 내부에서 계산한다
 * });
 *
 * // edit.html
 * $yona.loadModule("pullrequest.Write", {
 *     "sMode"           : "edit",
 *     "sState"          : pr.state,
 *     "sMergeResultURL" : "...",
 *     "elFromBranch"    : "input[name='fromBranch']",
 *     "elToBranch"      : "input[name='toBranch']",
 *     "sSubmitMethod"   : "PUT",
 *     "sSubmitURL"      : "/api/projects/" + projectId + "/pullrequests/" + number,
 *     "sRedirectURL"    : "/" + owner + "/" + projectName + "/pull/" + number,
 *     "sErrorPrefix"    : "수정 실패: "
 * });
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
            _initElement(htOptions);

            // branches.isEmpty()일 때는 폼 자체가 렌더링되지 않는다(create.html
            // th:if="${!branches.isEmpty()}") - 이 경우 아무것도 하지 않는다.
            if(!htElement.welForm){
                return;
            }

            _initVar(htOptions);
            _attachEvent();

            if(htVar.sState === "OPEN"){
                _checkMergeResult();
            }
        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.sMode = htOptions.sMode;
            htVar.sState = htOptions.sState;
            htVar.sMergeResultURL = htOptions.sMergeResultURL;
            htVar.bAutoFillTitleBody = htOptions.bAutoFillTitleBody === true;
            htVar.sSubmitMethod = htOptions.sSubmitMethod;
            htVar.sSubmitURL = htOptions.sSubmitURL;
            htVar.sRedirectURL = htOptions.sRedirectURL;
            htVar.sErrorPrefix = htOptions.sErrorPrefix || "";
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = document.getElementById("pull-request-form");
            if(!htElement.welForm){
                return;
            }

            htElement.welFromBranch = document.querySelector(htOptions.elFromBranch);
            htElement.welToBranch = document.querySelector(htOptions.elToBranch);
            htElement.welFromProjectId = htOptions.elFromProjectId ? document.querySelector(htOptions.elFromProjectId) : null;
            htElement.welToProjectId = htOptions.elToProjectId ? document.querySelector(htOptions.elToProjectId) : null;
            htElement.welStatus = document.getElementById("status");
            htElement.welCommits = document.getElementById("__commits");
            htElement.welTitle = document.getElementById("title");
            htElement.welBody = document.querySelector('textarea[data-editor-mode="content-body"]');
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            if(htVar.sMode === "new"){
                htElement.welFromBranch.addEventListener("change", _checkMergeResult);
                htElement.welToBranch.addEventListener("change", _checkMergeResult);

                if(htElement.welFromProjectId && htElement.welToProjectId){
                    htElement.welFromProjectId.addEventListener("change", _onChangeProject);
                    htElement.welToProjectId.addEventListener("change", _onChangeProject);
                }
            }

            htElement.welForm.addEventListener("submit", _onSubmitForm);
        }

        /**
         * legacy yona.git.Write.js _onChangeProject() 대응 — from/to 프로젝트 선택이 바뀌면 새
         * fromProjectId/toProjectId 쿼리스트링으로 페이지 전체를 새로고침한다(서버가 그 프로젝트
         * 기준으로 연관 프로젝트 목록·브랜치 목록을 다시 계산).
         */
        function _onChangeProject(){
            location.search = "?fromProjectId=" + htElement.welFromProjectId.value +
                "&toProjectId=" + htElement.welToProjectId.value;
        }

        /**
         * legacy yona.git.Write.js _checkMergeResult()/_onSuccessMergeResult()/_getMergeResultData()
         * 대응. 브랜치가 바뀔 때마다(또는 편집 화면 최초 로드 시) 머지 가능 여부를 GET으로 조회해
         * #__commits에 결과 조각을 꽂고, 그 조각의 #mergeResult data-* 속성을 다시 읽어 #status
         * 배너를 갱신한다.
         */
        function _checkMergeResult(){
            var sFromBranch = htElement.welFromBranch.value;
            var sToBranch = htElement.welToBranch.value;
            if(!sFromBranch || !sToBranch){
                return;
            }

            htElement.welStatus.className = "alert alert-info";
            htElement.welStatus.textContent = Messages("pullRequest.is.merging");
            htElement.welStatus.style.display = "";

            var htParams = {"fromBranch": sFromBranch, "toBranch": sToBranch};
            if(htElement.welFromProjectId && htElement.welToProjectId){
                htParams.fromProjectId = htElement.welFromProjectId.value;
                htParams.toProjectId = htElement.welToProjectId.value;
            }

            fetch(htVar.sMergeResultURL + "?" + new URLSearchParams(htParams))
                .then(function(response){
                    if(!response.ok){
                        return Promise.reject(response);
                    }
                    return response.text();
                })
                .then(function(sResultHTML){
                    htElement.welCommits.innerHTML = sResultHTML;

                    var elResult = document.getElementById("mergeResult");
                    var nNumOfCommits = parseInt(elResult.getAttribute("data-commits"), 10) || 0;
                    var bIsConflict = elResult.getAttribute("data-conflict") === "true";

                    if(nNumOfCommits > 0 && bIsConflict){
                        htElement.welStatus.className = "alert alert-error";
                        htElement.welStatus.textContent = Messages("pullRequest.is.not.safe");
                    } else if(nNumOfCommits > 0){
                        htElement.welStatus.className = "alert alert-success";
                        htElement.welStatus.textContent = Messages("pullRequest.is.safe");
                    } else {
                        htElement.welStatus.className = "alert alert-info";
                        htElement.welStatus.textContent = Messages("pullRequest.diff.noChanges");
                    }
                    htElement.welStatus.style.display = "";

                    if(htVar.bAutoFillTitleBody){
                        var sSuggestedTitle = elResult.getAttribute("data-pullrequest-title");
                        var sSuggestedBody = elResult.getAttribute("data-pullrequest-body");
                        if(sSuggestedTitle && htElement.welTitle.value.trim() === ""){
                            htElement.welTitle.value = sSuggestedTitle;
                        }
                        if(sSuggestedBody && htElement.welBody.value.trim() === ""){
                            htElement.welBody.value = sSuggestedBody;
                        }
                    }
                })
                .catch(function(){});
        }

        /**
         * @returns {Hash Table}
         */
        function _buildRequestData(){
            var htData = {
                "title": htElement.welTitle.value,
                "body" : htElement.welBody.value,
                "fromBranch": htElement.welFromBranch.value,
                "toBranch"  : htElement.welToBranch.value
            };

            if(htVar.sMode === "new"){
                htData.fromProjectId = htElement.welFromProjectId.value;
            }

            return htData;
        }

        /**
         * @returns {String}
         */
        function _getRedirectURL(){
            if(htVar.sMode === "new"){
                var elSelected = htElement.welToProjectId.options[htElement.welToProjectId.selectedIndex];
                return "/" + elSelected.dataset.owner + "/" + elSelected.dataset.name + "/pulls";
            }

            return htVar.sRedirectURL;
        }

        /**
         * "new" 모드는 toProjectId를 제출 시점에 다시 읽는다(legacy와 동일하게 - 프로젝트를
         * 바꾸면 _onChangeProject가 즉시 페이지를 리로드하므로 항상 서버 렌더값과 일치하지만,
         * 그 가정에 기대지 않고 그대로 최신 값을 읽는다).
         *
         * @returns {String}
         */
        function _getSubmitURL(){
            if(htVar.sMode === "new"){
                return "/api/projects/" + htElement.welToProjectId.value + "/pullrequests";
            }

            return htVar.sSubmitURL;
        }

        /**
         * @param {Event} weEvt
         */
        function _onSubmitForm(weEvt){
            weEvt.preventDefault();

            fetch(_getSubmitURL(), {
                "method": htVar.sSubmitMethod,
                "headers": {"Content-Type": "application/json"},
                "body": JSON.stringify(_buildRequestData())
            }).then(function(response){
                if(!response.ok){
                    return response.text().then(function(sText){
                        return Promise.reject(sText);
                    });
                }
                window.location.href = _getRedirectURL();
            }).catch(function(sErrorText){
                htElement.welStatus.textContent = htVar.sErrorPrefix + sErrorText;
                htElement.welStatus.style.display = "";
            });
        }

        _init(htOptions || {});
    };

})("yona.pullrequest.Write");
