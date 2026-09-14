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

        /**
         * jQuery `.on(evt, selector, fn)` 위임 바인딩과 동일하게 재현한다:
         * addEventListener + closest(selector) + container.contains(...) 가드 +
         * fn.call(matchedEl, e) (P3-70 라운드2에서 확립한 관례).
         *
         * @private
         */
        function _delegate(container, sEventType, sSelector, fHandler){
            if(!container){
                return;
            }
            container.addEventListener(sEventType, function(weEvt){
                var matched = weEvt.target.closest(sSelector);
                if(matched && container.contains(matched)){
                    fHandler.call(matched, weEvt);
                }
            });
        }

        /**
         * jQuery의 인자 없는 `.submit()`은 사실 `.trigger("submit")`과 같다 - "submit"
         * 이벤트를 실제로 발생시켜(버블링 포함) 바인딩된 핸들러를 전부 호출하고, 그 중
         * 누구도 preventDefault를 부르지 않았으면 네이티브 elem.submit()으로 폴백한다.
         * 이 파일의 pjax 가로채기(_initPjax)와 due-date 검증(_onSubmitSearchForm)이 모두
         * 이 "이벤트 발생 우선, 아무도 안 막으면 진짜 제출" 흐름에 의존하므로, 네이티브
         * form.submit()을 직접 호출하면 두 로직을 건너뛰게 된다 - 그래서 이 헬퍼로
         * 똑같은 흐름을 재현한다.
         *
         * @private
         */
        function _triggerSubmit(elForm){
            if(!elForm){
                return;
            }
            var weEvt = new Event("submit", {"bubbles": true, "cancelable": true});
            var bNotPrevented = elForm.dispatchEvent(weEvt);
            if(bNotPrevented){
                elForm.submit();
            }
        }

        /**
         * jQuery `.val()` getter가 multiple-select에서 선택된 option들의 value 배열을
         * 돌려주는 것과 동일하게 재현한다.
         *
         * @private
         */
        function _getMultiSelectValues(elSelect){
            return Array.prototype.map.call(elSelect.selectedOptions, function(option){
                return option.value;
            });
        }

        function _initImplicitTitlePrefix() {
            document.querySelectorAll(".title-prefix").forEach(function(elPrefix){
                elPrefix.addEventListener("click", function(){
                    var filterInputs = document.querySelectorAll("input[name*='filter']");
                    var aForms = [];

                    filterInputs.forEach(function(elInput){
                        elInput.value = elPrefix.textContent;
                        var elForm = elInput.closest("form");
                        if(elForm && aForms.indexOf(elForm) < 0){
                            aForms.push(elForm);
                        }
                    });

                    aForms.forEach(function(elForm){
                        _triggerSubmit(elForm);
                    });
                });
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
            htElement.welIssueWrap = document.querySelector(htOptions.welIssueWrap || '.issue-list-wrap');
            htElement.welSearchForm = document.querySelector(htOptions.welSearchForm || "form[name='search']");
            htElement.welPagination = document.querySelector(htOptions.elPagination || "#pagination");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            _delegate(htElement.welIssueWrap, "click", "a[data-label-id][data-category-id]", _onClickLabelOnList);
            _delegate(htElement.welIssueWrap, "click", "a[pjax-filter]", _onClickSearchFilter);
            _delegate(htElement.welIssueWrap, "click", "a[orderBy]", _onClickListOrder);
            _delegate(htElement.welIssueWrap, "click", "a[state]", _onClickStateTab);
            _delegate(htElement.welIssueWrap, "click", "[data-submit]", _onChangeSearchField);

            _delegate(htElement.welIssueWrap, "change", '[data-toggle="issue-checkbox"]', _onChangeIssueCheckBox);
            _delegate(htElement.welIssueWrap, "change", "[data-search]", _onChangeSearchField);
            _delegate(htElement.welIssueWrap, "change", '[data-toggle="calendar"]', _onChangeSearchField);
            _delegate(htElement.welIssueWrap, "submit", "form[name='search']", _onSubmitSearchForm);
        }

        /**
         * "change" event of issue-checkbox.
         * Gets issueId from changed checkbox and
         * set highlight the issue item has same issue Id.
         *
         * @private
         */
        function _onChangeIssueCheckBox() {
            var welCheckBox = this;
            var welItemWrap = document.getElementById('issue-item-' + welCheckBox.dataset.issueId);

            if(!welItemWrap){
                return;
            }

            if(welCheckBox.checked){
                welItemWrap.classList.add('active');
            } else {
                welItemWrap.classList.remove('active');
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

            var link = this;

            htElement.welSearchForm.querySelectorAll("input[name=orderBy]").forEach(function(el){
                el.value = link.getAttribute("orderBy");
            });
            htElement.welSearchForm.querySelectorAll("input[name=orderDir]").forEach(function(el){
                el.value = link.getAttribute("orderDir");
            });
            _triggerSubmit(htElement.welSearchForm);
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

            var link = this;
            htElement.welSearchForm.querySelectorAll("input[name=state]").forEach(function(el){
                el.value = link.getAttribute("state");
            });
            _triggerSubmit(htElement.welSearchForm);
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

            var link = this;
            var targetQuery = "[data-search=labelIds]";
            var target = htElement.welSearchForm.querySelector(targetQuery);

            var labelId = link.dataset.labelId;
            var newValue;

            if(target.multiple){
                newValue = _getMultiSelectValues(target);
                newValue.push(labelId);
            } else {
                newValue = labelId;
            }

            // P3-46 #5 후속 버그 수정(2026-09-12): Select2 v3 시절 API(.data("select2").val(...))가
            // Tom Select 교체 후에도 그대로 남아있어 target.data("select2")가 항상 undefined를
            // 반환해 TypeError로 죽어있었다(실사용 경로 - 라벨 클릭 시 크래시). Tom Select 인스턴스는
            // element.tomselect로 접근하고 값 반영은 setValue(value)로 한다(두 번째 인자 silent를
            // 생략하면 change 이벤트가 발생해 Select2의 triggerChange=true와 동등하다).
            target.tomselect.setValue(newValue);
        }

        /**
         * "change" event handler of search fields.
         * Submit the form on change event has triggered.
         *
         * @private
         */
        function _onChangeSearchField() {
            _triggerSubmit(htElement.welSearchForm);
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

            var data = this.dataset;

            for(var key in data){
                htElement.welSearchForm.querySelectorAll('[data-search="' + key + '"]').forEach(function(el){
                    el.value = data[key];
                });
            }

            _triggerSubmit(htElement.welSearchForm);
        }

        /**
         * update Pagination
         *
         * @requires yona.Pagination
         * @private
         */
        function _initPagination(){
            var nTotal = htElement.welPagination ? Number(htElement.welPagination.dataset.total) : undefined;
            yona.Pagination.update(htElement.welPagination, nTotal);
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
         * P3-70 라운드4: 이 파일의 나머지 `.submit()` 호출을 전부 _triggerSubmit()(진짜
         * "submit" 이벤트를 bubbles:true로 발생시킴)으로 바꿨으므로, 여기서도 jQuery
         * 위임 없이 순수 네이티브 위임(_delegate)만으로 동일하게 가로챌 수 있다.
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

            _delegate(document, "submit", "form[name='search']", function(weEvt){
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
            document.querySelectorAll(".post-item").forEach(function(elPostItem){
                elPostItem.addEventListener("click", function(e){
                    var elChildList = elPostItem.querySelector(".child-issue-list");
                    if(elChildList){
                        // .child-issue-list는 <div class="child-issue-list hide">라 CSS
                        // .hide가 display:none을 강제한다 - P3-70 라운드1/2 관례대로
                        // style.display = "block"으로 override한다.
                        elChildList.style.display = "block";
                    }
                });
            });

            document.querySelectorAll(".title-wrap > .title").forEach(function(el){
                el.addEventListener("click", function(e){
                    e.stopPropagation();
                });
            });
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
            document.querySelectorAll(".post-item").forEach(function(el){
                el.addEventListener("mouseenter", function(){
                    el.style.backgroundColor = "#fafafa";
                });
                el.addEventListener("mouseleave", function(){
                    el.style.backgroundColor = "#fff";
                });
            });
        }

        function _addEventAtOrganizationIssueSearchPage() {
            // pjax reset previous events, so it is required adding event again.
            var elProjects = document.getElementById("projects");
            if(elProjects){
                elProjects.addEventListener("change", function(){
                    _triggerSubmit(document.getElementById("search"));
                });
            }
        }

        /**
         * Initialize ui.Select2
         * This function called after redraw issue list HTML using PJAX.
         *
         * @private
         */
        function _initSelect2(){
            if(typeof yona.ui.TomSelect === "function"){
                document.querySelectorAll('[data-toggle="tomselect"]').forEach(function(el){
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
               document.querySelectorAll('[data-toggle="calendar"]').forEach(function(el){
                   yona.ui.Calendar(el, {
                       "silent": true
                   });
               });
           }
        }

        function _onSubmitSearchForm(evt){
            var elDueDate = htElement.welIssueWrap.querySelector("[data-toggle='calendar']");

            if(elDueDate) {
                var sDueDate = $yona.getTrim(elDueDate.value);

                if(sDueDate && !moment(sDueDate).isValid()){
                    $yona.notify(Messages("issue.error.invalid.duedate"), 3000);
                    elDueDate.focus();
                    // 원본 jQuery 핸들러의 "return false"는 preventDefault +
                    // stopPropagation을 동시에 의미한다 - 네이티브 addEventListener는
                    // 반환값을 무시하므로 명시적으로 호출해야 pjax 제출까지 막힌다
                    // (stopPropagation 없이는 document의 pjax 핸들러까지 계속 버블링돼
                    // 검증에 실패해도 검색이 진행돼버린다).
                    evt.preventDefault();
                    evt.stopPropagation();
                    return false;
                }

                return true;
            }

            return true;
        }

        _init(htOptions);
    };

})("yona.issue.List");
