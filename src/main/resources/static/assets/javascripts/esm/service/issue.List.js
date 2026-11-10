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

import { register } from "../namespace.js";
import { isValidDate } from "../dates.js";

const List = function(htOptions){

    const htElement = {};
    let htInitialOptions = {};

    function _initImplicitTitlePrefix() {
        $(".title-prefix").on("click", function(){
            const filterInput = $("input[name*='filter']");
            filterInput.val($(this).text());
            yona.Htmx.submit(filterInput.closest("form"));
        });
    }

    /**
     * initialize
     */
    function _init(htOptions){
        _initElement(htOptions || {});
        _attachEvent();
        _initPagination();
        _initHtmx();
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
        htElement.welIssueWrap.on("click", "a[list-filter]", _onClickSearchFilter);
        htElement.welIssueWrap.on("click", "a[orderBy]", _onClickListOrder);
        htElement.welIssueWrap.on("click", "a[state]", _onClickStateTab);
        htElement.welIssueWrap.on("click", "[data-submit]", _onChangeSearchField);

        htElement.welIssueWrap.on("change", '[data-toggle="issue-checkbox"]', _onChangeIssueCheckBox);
        htElement.welIssueWrap.on("change", "[data-search]", _onChangeSearchField);
        htElement.welIssueWrap.on("change", '[data-toggle="calendar"]', _onChangeSearchField);
        htElement.welIssueWrap.on("htmx:beforeRequest", "form[name='search']", _onSubmitSearchForm);
    }

    /**
     * "change" event of issue-checkbox.
     * Gets issueId from changed checkbox and
     * set highlight the issue item has same issue Id.
     *
     * @private
     */
    function _onChangeIssueCheckBox() {
        const welCheckBox = $(this);
        const welItemWrap = $('#issue-item-' + welCheckBox.data('issueId'));

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

        const link = $(this);

        htElement.welSearchForm.find("input[name=orderBy]").val(link.attr("orderBy"));
        htElement.welSearchForm.find("input[name=orderDir]").val(link.attr("orderDir"));
        yona.Htmx.submit(htElement.welSearchForm);
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
        yona.Htmx.submit(htElement.welSearchForm);
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

        const link = $(this);
        const targetQuery = "[data-search=labelIds]";
        const target = htElement.welSearchForm.find(targetQuery);

        const labelId = link.data("labelId");
        let newValue;

        if(target.prop("multiple")){
            newValue = (target.val() || []);
            newValue.push(labelId);
        } else {
            newValue = labelId;
        }

        target.val(newValue).trigger("change");
    }

    /**
     * "change" event handler of search fields.
     * Submit the form on change event has triggered.
     *
     * @private
     */
    function _onChangeSearchField() {
        yona.Htmx.submit(htElement.welSearchForm);
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

        const data = $(this).data();

        for(const key in data){
            htElement.welSearchForm.find('[data-search="' + key + '"]').val(data[key]);
        }

        yona.Htmx.submit(htElement.welSearchForm);
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
     * 목록 영역(`div[list-container]`)을 htmx로 바꾸는 화면의 후처리를 단다.
     * 어떤 폼과 링크가 영역을 바꾸는지는 템플릿의 `hx-*` 속성이 정하고(`decisions.md` D-96),
     * 공통 설정(헤더, 진행 막대, 오류 대체)은 `yona.Htmx.js`가 한다.
     * htmx는 영역 안쪽만 바꾸므로 영역 안쪽에 직접 단 이벤트와 스크립트가 만든 요소는 바꾼 뒤에 다시 초기화한다.
     *
     * @requires htmx
     * @private
     */
    function _initHtmx(){
        $(document).on("htmx:afterSwap", (weEvt) =>{
            if($(weEvt.target).is("[list-container]")){
                _onLoadIssueList();
            }
        });
    }

    function _initShowChildList() {
        $(".post-item").on("click", function(e){
            $(this).find(".child-issue-list").show();
        });
        
        $(".title-wrap > .title").on("click", (e) =>{
            e.stopPropagation();
        })
    }

    function _onLoadIssueList(){
        _initElement(htInitialOptions);
        _initPagination();
        _initSelect2();
        _initCalendar();
        _initImplicitTitlePrefix();
        _addEventAtOrganizationIssueSearchPage();
        _listHoverEffect();
        _initTwoColumnMode();  // yona.twoColumnMode.js
        _initShowChildList();
        // v1.6은 이 스크립트(yona.showSubtask.js)를 읽지 않는 화면에서 ReferenceError가 났다(R-69). 있을 때만 부른다
        if (typeof _initShowSubtasks === "function") {
            _initShowSubtasks(); // yona.showSubtask.js
        }
    }

    function _listHoverEffect(){
        $(".post-item").hover(function () {
            $(this).css("background-color", "#fafafa");
        }, function () {
            $(this).css("background-color", "#fff");
        });
    }

    function _addEventAtOrganizationIssueSearchPage() {
        // htmx replaces the contents of the list wrap, so events bound to its descendants are lost and required adding again.
        $("#projects" ).on("change", () =>{
            yona.Htmx.submit($("#search"));
        });
    }

    /**
     * Initialize ui.Select2
     * This function called after redraw issue list HTML using htmx.
     *
     * @private
     */
    function _initSelect2(){
        if(typeof yona.ui.Select2 === "function"){
            $('[data-toggle="select2"]').each((i, el) =>{
                yona.ui.Select2(el);
            });
        }
    }

    /**
     * Initialize ui.Calendar
     * This function called after redraw issue list HTML using htmx.
     *
     * @private
     */
    function _initCalendar(){
       if(typeof yona.ui.Calendar === "function"){
           $('[data-toggle="calendar"]').each((i, el) =>{
               yona.ui.Calendar(el, {
                   "silent": true
               });
           });
       }
    }

    /**
     * 검색 폼의 요청을 보내기 직전에 목표 완료일 형식을 검사한다. 잘못되면 알리고 요청을 막는다(`false`).
     * htmx는 폼 자체에 단 제출 이벤트로 요청을 시작하므로 바깥 요소의 `submit` 처리보다 먼저 실행된다.
     * 그래서 요청을 취소할 수 있는 `htmx:beforeRequest`에서 검사한다.
     */
    function _onSubmitSearchForm(evt){
        const elDueDate = htElement.welIssueWrap.find("[data-toggle='calendar']");

        if(elDueDate.length > 0) {
            const sDueDate = $yona.getTrim($(elDueDate).val());

            if(sDueDate && !isValidDate(sDueDate)){
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

export default List;

register("yona.issue.List", List);
