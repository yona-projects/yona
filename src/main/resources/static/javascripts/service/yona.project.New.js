/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp.
 * https://yona.io
 **/
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * jQuery UI `.toggle("slide")`(가로 슬라이드 show/hide)의 최소 vanilla 재현.
         * `#repoAuth`(repo-auth-wrap)에만 쓰이는 단일 용도 헬퍼라 이 파일 안에 둔다.
         */
        function _toggleSlide(el){
            if(!el){
                return;
            }

            var bHidden = window.getComputedStyle(el).display === "none";
            var nDuration = 400;

            if(bHidden){
                // `.form-wrap.new-project .repo-auth-wrap { display: none; }`(yona.css)처럼
                // 클래스 기반 display:none 규칙이 있으면 인라인 스타일을 빈 문자열로만
                // 비워서는 그 규칙이 그대로 이겨 패널이 절대 열리지 않는다(Playwright 실측
                // 회귀 발견) - jQuery `.show()`가 태그 기본 표시값을 명시적으로 세팅해 CSS
                // 규칙을 오버라이드하던 것과 동일하게, 명시적 값("block")을 넣어야 한다.
                el.style.display = "block";
                var nWidth = el.offsetWidth;
                el.style.overflow = "hidden";
                el.style.marginLeft = (-nWidth) + "px";
                el.style.transition = "margin-left " + nDuration + "ms ease";
                void el.offsetWidth; // 강제 리플로우 - transition이 실제 적용된 뒤 목표값으로 옮겨야 애니메이션이 보인다.
                el.style.marginLeft = "0px";
                setTimeout(function(){
                    el.style.transition = "";
                    el.style.overflow = "";
                    el.style.marginLeft = "";
                }, nDuration);
            } else {
                var nWidth2 = el.offsetWidth;
                el.style.overflow = "hidden";
                el.style.marginLeft = "0px";
                el.style.transition = "margin-left " + nDuration + "ms ease";
                void el.offsetWidth;
                el.style.marginLeft = (-nWidth2) + "px";
                setTimeout(function(){
                    el.style.display = "none";
                    el.style.transition = "";
                    el.style.overflow = "";
                    el.style.marginLeft = "";
                }, nDuration);
            }
        }

        /**
         * jQuery `.parent(selector)`(즉시 부모가 selector에 매칭될 때만 반환, closest 아님)와 동일.
         */
        function _parentIfMatches(el, sTag){
            var elParent = el ? el.parentElement : null;
            return (elParent && elParent.tagName.toLowerCase() === sTag.toLowerCase()) ? elParent : null;
        }

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
            htVar.rxPrjName = /^[0-9A-Za-z-_\.가-힣]+$/;
            htVar.aReservedWords = [".", "..", ".git"];
        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.welForm = document.querySelector(htOptions.sFormId);
            htElement.welInputProjectName = document.getElementById("project-name");
            htElement.welInputProjectOwner = document.getElementById("project-owner");
            htElement.welInputGitRepoURL = document.getElementById("url");

            htElement.vcsSelect = document.getElementById("vcs");
            htElement.svnWarning = document.getElementById("svn");
            htElement.welProtected = document.getElementById("opt-protected");

            htElement.welRepoAuthCheck = document.getElementById("useRepoAuth");
            htElement.welRepoAuthWrap = document.getElementById("repoAuth");
            htElement.waRepoAuthInput = htElement.welRepoAuthWrap ? htElement.welRepoAuthWrap.querySelectorAll("input") : [];

            htElement.welMenuSettingCode = document.getElementById("menuSettingCode");
            htElement.welMenuSettingPullRequest = document.getElementById("menuSettingPullRequest");
            htElement.welReviewerCountDisable = document.getElementById('reviewerCountDisable');
            htElement.welMenuSettingReview = document.getElementById("menuSettingReview");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            if(htElement.vcsSelect){
                htElement.vcsSelect.addEventListener("change", _onChangeVCSItem);
            }
            if(htElement.welInputProjectOwner){
                htElement.welInputProjectOwner.addEventListener("change", _onChangeProjectOwner);
            }
            if(htElement.welForm){
                htElement.welForm.addEventListener("submit", _validateForm);
            }
            if(htElement.welRepoAuthCheck){
                htElement.welRepoAuthCheck.addEventListener("change", _onChangeRepoAuthCheck);
            }

            if(htElement.welMenuSettingCode){
                htElement.welMenuSettingCode.addEventListener('click', _onClickMenuSettingCode);
            }
            if(htElement.welMenuSettingPullRequest){
                htElement.welMenuSettingPullRequest.addEventListener('click', _onClickMenuSettingPullRequest);
            }
            if(htElement.welMenuSettingReview){
                htElement.welMenuSettingReview.addEventListener('click', _onClickMenuSettingReview);
            }
            if(htElement.welInputProjectName){
                htElement.welInputProjectName.addEventListener('focusout', _onFocusoutProjectName);
            }
        }

        function _onFocusoutProjectName(){
            htElement.welInputProjectName.value = htElement.welInputProjectName.value.trim().replace(/ /g, '-');
        }

        function _onChangeRepoAuthCheck(){
            document.querySelectorAll("input").forEach($yona.hidePopoverError);
            _toggleSlide(htElement.welRepoAuthWrap);
            htElement.waRepoAuthInput.forEach(function(el){
                el.disabled = !(htElement.welRepoAuthCheck && htElement.welRepoAuthCheck.checked);
            });
        }

        function _onChangeVCSItem(evt){
            // select2(evt.val 커스텀 필드) -> TomSelect 교체 후 change 이벤트가 표준 DOM
            // 이벤트로 바뀌어 evt.val이 항상 undefined였다(issue.LabelEditor.js의 Select2 v3
            // -> Tom Select 이관 누락과 같은 계열의 버그, Playwright로 실제 재현).
            if(evt.target.value.toUpperCase() === "SUBVERSION"){
                if(htElement.svnWarning){ htElement.svnWarning.style.display = "inline"; }
                /**
                 * We don't know whether the user want to check this or not
                 * because this checkbox will be hidden. So we let it be true
                 * forcely. It may be misguessing of the user's choice but it
                 * prevents the possibiltiy that the user never knows the
                 * existence of PullRequest feature when the user changes the
                 * project setting to use Git.
                 */
                var elMenuSettingPullRequest = document.getElementById('menuSettingPullRequest');
                if(elMenuSettingPullRequest){
                    elMenuSettingPullRequest.checked = true;
                    var elLabel = _parentIfMatches(elMenuSettingPullRequest, 'label');
                    if(elLabel){ elLabel.style.display = "none"; }
                }
            } else {
                if(htElement.svnWarning){ htElement.svnWarning.style.display = "none"; }
                var elMenuSettingPullRequest2 = document.getElementById('menuSettingPullRequest');
                if(elMenuSettingPullRequest2){
                    elMenuSettingPullRequest2.checked = true;
                    var elLabel2 = _parentIfMatches(elMenuSettingPullRequest2, 'label');
                    if(elLabel2){ elLabel2.style.display = ""; }
                }
            }
        }

        function _onChangeProjectOwner() {
            // jQuery Sizzle 전용 `:selected` 의사클래스는 네이티브 querySelector에서 유효하지
            // 않은 셀렉터라 SyntaxError를 던진다(Playwright 실측으로 발견한 실제 회귀) -
            // HTML5 표준 `:checked`(option 요소의 선택 상태에도 적용됨)로 대체.
            var elSelectedOption = document.querySelector("#project-owner option:checked");
            var sType = elSelectedOption ? elSelectedOption.dataset.type : undefined;

            if (sType == "user") {
                var elProtectedCheckbox = document.getElementById("protected");
                if (elProtectedCheckbox && elProtectedCheckbox.checked) {
                    var elPublicCheckbox = document.getElementById("public");
                    if(elPublicCheckbox){ elPublicCheckbox.checked = true; }
                }
                if(htElement.welProtected){ htElement.welProtected.style.display = "none"; }
            } else {
                if(htElement.welProtected){ htElement.welProtected.style.display = ""; }
            }
        }

        function _focusOnFirstField(){
            if(htElement.welRepoAuthCheck && htElement.welRepoAuthCheck.checked){
                htElement.waRepoAuthInput[0].focus();
            } else if(htElement.welInputGitRepoURL){
                htElement.welInputGitRepoURL.focus();
            } else if(htElement.welInputProjectName){
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
            var projectName = htElement.welInputProjectName ? htElement.welInputProjectName.value : "";

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

            if(htElement.welInputGitRepoURL &&
               htElement.welInputGitRepoURL.value.trim().length === 0 ){
                error.url = error.url || [];
                error.url.push(Messages("project.import.error.empty.url"));
            }

            if(Object.keys(error).length > 0){
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
                targetElement = htElement.welForm ? htElement.welForm.querySelector("[name=" + target + "]") : null;

                if(targetElement) {
                    $yona.showPopoverError(targetElement, error[target].shift(), "left");
                }
            }
        }

        function _onClickMenuSettingCode() {
            var isChecked = this.checked;

            if (!isChecked) {
                if(htElement.welMenuSettingCode){ htElement.welMenuSettingCode.checked = false; }
                if(htElement.welMenuSettingPullRequest){ htElement.welMenuSettingPullRequest.checked = false; }
                if(htElement.welMenuSettingReview){ htElement.welMenuSettingReview.checked = false; }

                // 원본 그대로 보존: welReviewerCountSettingPanel/welDefaultBranceSettingPanel/
                // welSubMenuProjectChangeVCS는 _initElement에서 단 한 번도 할당되지 않는
                // pre-existing 미완성 상태다(전수 조사 완료) - "코드" 체크박스를 실제로
                // 해제하면 원본도 이 지점에서 TypeError를 던지며 멈췄을 것이다(Playwright로
                // 실측 예정). jQuery 전환과 무관한 기존 버그라 고치지 않고 그대로 보존한다.
                htElement.welReviewerCountSettingPanel.style.display = "none";
                htElement.welDefaultBranceSettingPanel.style.display = "none";
                htElement.welSubMenuProjectChangeVCS.style.display = "none";
            }
        }

        function _onClickMenuSettingPullRequest() {
            var isChecked = this.checked;

            if(isChecked) {
                if(htElement.welMenuSettingCode){ htElement.welMenuSettingCode.checked = true; }
            } else {
                // 위와 동일한 pre-existing 미할당 버그 - 그대로 보존.
                htElement.welReviewerCountSettingPanel.style.display = "none";
            }
        }

        function _onClickMenuSettingReview() {
            var isChecked = this.checked;

            if(isChecked) {
                if(htElement.welMenuSettingCode){ htElement.welMenuSettingCode.checked = true; }
            }
        }

        _init(htOptions || {});
    };

})("yona.project.New");
