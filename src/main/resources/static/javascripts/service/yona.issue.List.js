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

    "use strict";

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htElement = {};
        var htInitialOptions = {};

        function _initImplicitTitlePrefix() {
            $(".title-prefix").on("click", function(){
                var filterInput = $("input[name*='filter']");
                filterInput.val($(this).text());
                filterInput.closest("form").submit();
            });
        }

        /**
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _attachEvent();
            _initPagination();
            _initPjax();
            _initImplicitTitlePrefix();
            _listHoverEffect();
            _initTwoColumnMode();
            _initShowChildList();

            htInitialOptions = htOptions || {};
        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.welIssueWrap = $(htOptions.welIssueWrap || '.issue-list-wrap');
            htElement.welSearchForm = $(htOptions.welSearchForm || "form[name='search']");
            htElement.welPagination = $(htOptions.elPagination || "#pagination");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welIssueWrap.on("click", "a[data-label-id][data-category-id]", _onClickLabelOnList);
            htElement.welIssueWrap.on("click", "a[pjax-filter]", _onClickSearchFilter);
            htElement.welIssueWrap.on("click", "a[orderBy]", _onClickListOrder);
            htElement.welIssueWrap.on("click", "a[state]", _onClickStateTab);
            htElement.welIssueWrap.on("click", "[data-submit]", _onChangeSearchField);

            htElement.welIssueWrap.on("change", '[data-toggle="issue-checkbox"]', _onChangeIssueCheckBox);
            htElement.welIssueWrap.on("change", "[data-search]", _onChangeSearchField);
            htElement.welIssueWrap.on("change", '[data-toggle="calendar"]', _onChangeSearchField);
            htElement.welIssueWrap.on("submit", "form[name='search']", _onSubmitSearchForm);
        }

        /**
         * "change" event of issue-checkbox.
         * Gets issueId from changed checkbox and
         * set highlight the issue item has same issue Id.
         *
         * @private
         */
        function _onChangeIssueCheckBox() {
            var welCheckBox = $(this);
            var welItemWrap = $('#issue-item-' + welCheckBox.data('issueId'));

            if(welCheckBox.is(':checked')){
                welItemWrap.addClass('active');
            } else {
                welItemWrap.removeClass('active');
            }
        }

        /**
         * "click" event handler of list order link
         * Fill orderBy and orderDir field value using data attribute,
         * and submit the search form.
         *
         * @param weEvt
         * @private
         */
        function _onClickListOrder(weEvt) {
            weEvt.preventDefault();

            var link = $(this);

            htElement.welSearchForm.find("input[name=orderBy]").val(link.attr("orderBy"));
            htElement.welSearchForm.find("input[name=orderDir]").val(link.attr("orderDir"));
            htElement.welSearchForm.submit();
        }

        /**
         * "click" event handler of list state tab
         * Fill state field value using data attribute and submit the search form.
         *
         * @param weEvt
         * @private
         */
        function _onClickStateTab(weEvt) {
            weEvt.preventDefault();

            htElement.welSearchForm.find("input[name=state]").val($(this).attr("state"));
            htElement.welSearchForm.submit();
        }

        /**
         * "click" event handler of labels on issue list.
         * Add clicked label to search form condition.
         *
         * @param event
         * @private
         */
        function _onClickLabelOnList(weEvt) {
            weEvt.preventDefault();

            var link = $(this);
            var targetQuery = "[data-search=labelIds]";
            var target = htElement.welSearchForm.find(targetQuery);

            var labelId = link.data("labelId");
            var newValue;

            if(target.prop("multiple")){
                newValue = (target.val() || []);
                newValue.push(labelId);
            } else {
                newValue = labelId;
            }

            // P3-46 #5 후속 버그 수정(2026-09-12): Select2 v3 시절 API(.data("select2").val(...))가
            // Tom Select 교체 후에도 그대로 남아있어 target.data("select2")가 항상 undefined를
            // 반환해 TypeError로 죽어있었다(실사용 경로 - 라벨 클릭 시 크래시). Tom Select 인스턴스는
            // element.tomselect로 접근하고 값 반영은 setValue(value)로 한다(두 번째 인자 silent를
            // 생략하면 change 이벤트가 발생해 Select2의 triggerChange=true와 동등하다).
            target[0].tomselect.setValue(newValue);
        }

        /**
         * "change" event handler of search fields.
         * Submit the form on change event has triggered.
         *
         * @private
         */
        function _onChangeSearchField() {
            htElement.welSearchForm.submit();
        }

        /**
         * "click" event handler of quick search links
         * Find filter from data attribute and fill search form field with its value.
         * Submits form after fill values.
         *
         * Relative pages:
         * - views/issue/partial_list_quicksearch.scala.html
         * - views/issue/my_partial_search.scala.html
         *
         * @param weEvt
         * @private
         */
        function _onClickSearchFilter(weEvt) {
            weEvt.preventDefault();

            var data = $(this).data();

            for(var key in data){
                htElement.welSearchForm.find('[data-search="' + key + '"]').val(data[key]);
            }

            htElement.welSearchForm.submit();
        }

        /**
         * update Pagination
         *
         * @requires yona.Pagination
         * @private
         */
        function _initPagination(){
            yona.Pagination.update(htElement.welPagination, htElement.welPagination.data("total"));
        }

        /**
         * Initialize Pjax
         *
         * jquery.pjax 플러그인을 걷어내고 fetch + DOMParser + history.pushState로 직접
         * 구현한다. 서버는 X-PJAX류 헤더를 전혀 보지 않고 항상 전체 페이지를 그대로
         * 렌더링하므로(컨트롤러 쪽에 그런 분기가 없음을 확인함), 클라이언트가 응답
         * 전체에서 div[pjax-container] 부분만 잘라 교체하면 기존과 동일하게 동작한다.
         * 기존 jquery.pjax가 걸어두던 "Firefox/Safari의 bfcache 버그 우회(2013년대
         * 그 라이브러리 특유의 이슈)"는 그 라이브러리 자체를 걷어내므로 더 이상 적용
         * 대상이 아니다.
         *
         * pjax-container(.issue-list-wrap)는 매번 innerHTML만 교체하고 그 컨테이너
         * 엘리먼트 자체는 그대로 두므로, _attachEvent()가 이 컨테이너에 걸어둔 위임형
         * 이벤트 리스너들은 재바인딩 없이 계속 유효하다(레거시 pjax도 같은 이유로
         * 컨테이너 자체는 남기고 내용만 바꿨다).
         *
         * @private
         */
        function _initPjax(){
            var elContainer = document.querySelector('div[pjax-container]');
            if(!elContainer){
                return;
            }

            document.addEventListener("click", function(weEvt){
                var elLink = weEvt.target.closest("a[pjax-page]");
                if(!elLink){
                    return;
                }
                weEvt.preventDefault();
                _pjaxNavigate(elLink.href, false);
            });

            // _onClickListOrder/_onClickStateTab/_onClickSearchFilter/_onChangeSearchField가
            // 여전히 jQuery의 인자 없는 .submit()(내부적으로 .trigger("submit")과 동일)으로
            // 검색 폼을 제출한다 - 이건 jQuery가 등록한 핸들러만 실행하고, 아무도
            // preventDefault를 안 부르면 폴백으로 네이티브 elem.submit()을 직접 호출해버린다.
            // 네이티브 elem.submit()은 스펙상 "submit" 이벤트 자체를 발생시키지 않으므로,
            // document.addEventListener("submit", ...)로는 이 경로를 절대 가로챌 수 없다
            // (실제로 시도했다가 전체 페이지 이동으로 새는 것을 Playwright로 재현했다).
            // jQuery의 이벤트 위임($(document).on)만 이 트리거 경로에 반응하므로 그대로 쓴다.
            $(document).on("submit", "form[name='search']", function(weEvt){
                weEvt.preventDefault();
                var elForm = this;
                var sQuery = new URLSearchParams(new FormData(elForm)).toString();
                var sBaseUrl = elForm.action.split("?")[0];
                _pjaxNavigate(sBaseUrl + (sQuery ? "?" + sQuery : ""), false);
            });

            window.addEventListener("popstate", function(){
                _pjaxNavigate(document.location.href, true);
            });
        }

        /**
         * @param {String} sUrl
         * @param {Boolean} bIsPopState history.pushState를 다시 쌓지 않아야 하는 뒤로/앞으로
         *   가기 탐색인 경우 true.
         * @private
         */
        function _pjaxNavigate(sUrl, bIsPopState){
            _onBeforeLoadIssueList();

            fetch(sUrl, { "headers": { "X-Requested-With": "XMLHttpRequest" } })
                .then(function(oResp){
                    if(!oResp.ok){
                        throw new Error("pjax fetch failed: " + oResp.status);
                    }
                    return oResp.text();
                })
                .then(function(sHtml){
                    var oDoc = new DOMParser().parseFromString(sHtml, "text/html");
                    var elNewContainer = oDoc.querySelector("div[pjax-container]");
                    var elCurrContainer = document.querySelector("div[pjax-container]");

                    if(elNewContainer && elCurrContainer){
                        elCurrContainer.innerHTML = elNewContainer.innerHTML;
                    }
                    if(oDoc.title){
                        document.title = oDoc.title;
                    }
                    if(!bIsPopState){
                        window.history.pushState({}, "", sUrl);
                    }

                    _onLoadIssueList();
                })
                .catch(function(){
                    // pjax fetch/파싱 실패 시 일반 페이지 이동으로 폴백한다(레거시
                    // jquery.pjax도 오류 시 동일하게 전체 페이지 이동으로 떨어졌다).
                    NProgress.done();
                    document.location.href = sUrl;
                });
        }

        function _onBeforeLoadIssueList(){
            NProgress.start();
        }

        function _initShowChildList() {
            $(".post-item").on("click", function(e){
                $(this).find(".child-issue-list").show();
            });
            
            $(".title-wrap > .title").on("click", function(e){
                e.stopPropagation();
            })
        }

        function _onLoadIssueList(){
            NProgress.done();

            _initElement(htInitialOptions);
            _initPagination();
            _initSelect2();
            _initCalendar();
            _initImplicitTitlePrefix();
            _addEventAtOrganizationIssueSearchPage();
            _listHoverEffect();
            _initTwoColumnMode();  // yona.twoColumnMode.js
            _initShowChildList();
            _initShowSubtasks(); // yona.showSubtask.js
        }

        function _listHoverEffect(){
            $(".post-item").hover(function () {
                $(this).css("background-color", "#fafafa");
            }, function () {
                $(this).css("background-color", "#fff");
            });
        }

        function _addEventAtOrganizationIssueSearchPage() {
            // pjax reset previous events, so it is required adding event again.
            $("#projects" ).on("change", function(){
                $("#search" ).submit();
            });
        }

        /**
         * Initialize ui.Select2
         * This function called after redraw issue list HTML using PJAX.
         *
         * @private
         */
        function _initSelect2(){
            if(typeof yona.ui.TomSelect === "function"){
                $('[data-toggle="tomselect"]').each(function(i, el){
                    yona.ui.TomSelect(el);
                });
            }
        }

        /**
         * Initialize ui.Calendar
         * This function called after redraw issue list HTML using PJAX.
         *
         * @private
         */
        function _initCalendar(){
           if(typeof yona.ui.Calendar === "function"){
               $('[data-toggle="calendar"]').each(function(i, el){
                   yona.ui.Calendar(el, {
                       "silent": true
                   });
               });
           }
        }

        function _onSubmitSearchForm(evt){
            var elDueDate = htElement.welIssueWrap.find("[data-toggle='calendar']");

            if(elDueDate.length > 0) {
                var sDueDate = $yona.getTrim($(elDueDate).val());

                if(sDueDate && !moment(sDueDate).isValid()){
                    $yona.notify(Messages("issue.error.invalid.duedate"), 3000);
                    elDueDate.focus();
                    return false;
                }

                return true;
            }    

            return true;
        }

        _init(htOptions);
    };

})("yona.issue.List");
