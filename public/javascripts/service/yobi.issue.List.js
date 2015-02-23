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

    "use strict";

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htElement = {};
        var htInitialOptions = {};

        /**
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _attachEvent();
            _initPagination();
            _initPjax();

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

            target.data("select2").val(newValue, true); // triggerChange=true
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
         * @requires yobi.Pagination
         * @private
         */
        function _initPagination(){
            yobi.Pagination.update(htElement.welPagination, htElement.welPagination.data("total"));
        }

        /**
         * Initialize Pjax
         *
         * @requires jquery.pjax
         * @private
         */
        function _initPjax(){
            var isFirefox = navigator.userAgent.toLowerCase().indexOf('firefox') > -1;
            var isSafari = navigator.userAgent.indexOf('Safari') != -1 && navigator.userAgent.indexOf('Chrome') == -1;
            // Workaround for pjax bug result from bfcache
            // https://developer.mozilla.org/en-US/docs/Using_Firefox_1.5_caching
            if(isFirefox || isSafari){
                return;
            }
            var htPjaxOptions = {
                "fragment": "div[pjax-container]",
                "timeout" : 3000
            };

            if($.support.pjax) {
                $.pjax.defaults.maxCacheLength = 0;
            }

            // on click pagination
            $(document).on("click", "a[pjax-page]", function(weEvt) {
                $.pjax.click(weEvt, "div[pjax-container]", htPjaxOptions);
            });

            // on submit search form
            $(document).on("submit", "form[name='search']", function(weEvt) {
                $.pjax.submit(weEvt, "div[pjax-container]", htPjaxOptions);
            });

            // show spinners
            $(document).on({
                "pjax:send"    : _onBeforeLoadIssueList,
                "pjax:complete": _onLoadIssueList
            });
        }

        function _onBeforeLoadIssueList(){
            NProgress.start();
        }

        function _onLoadIssueList(){
            NProgress.done();

            _initElement(htInitialOptions);
            _initPagination();
            _initSelect2();
            _initCalendar();
        }

        /**
         * Initialize ui.Select2
         * This function called after redraw issue list HTML using PJAX.
         *
         * @private
         */
        function _initSelect2(){
            if(typeof yobi.ui.Select2 === "function"){
                $('[data-toggle="select2"]').each(function(i, el){
                    yobi.ui.Select2(el);
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
           if(typeof yobi.ui.Calendar === "function"){
               $('[data-toggle="calendar"]').each(function(i, el){
                   yobi.ui.Calendar(el, {
                       "silent": true
                   });
               });
           }
        }

        function _onSubmitSearchForm(evt){
            var elDueDate = htElement.welIssueWrap.find("[data-toggle='calendar']");

            if(elDueDate.length > 0) {
                var sDueDate = $yobi.getTrim($(elDueDate).val());

                if(sDueDate && !moment(sDueDate).isValid()){
                    $yobi.notify(Messages("issue.error.invalid.duedate"), 3000);
                    elDueDate.focus();
                    return false;
                }

                return true;
            }    

            return true;
        }

        _init(htOptions);
    };

})("yobi.issue.List");
