
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htElement = {};
        var htOrderMap = {"asc": "desc", "desc": "asc"};

        function _initImplicitTitlePrefix() {
            document.querySelectorAll(".title-prefix").forEach(function(el){
                el.addEventListener("click", function(){
                    var text = this.textContent;
                    document.querySelectorAll("input[name*='filter']").forEach(function(filterInput){
                        filterInput.value = text;
                        var form = filterInput.closest("form");
                        if(form){
                            form.submit();
                        }
                    });
                });
            });
        }

        /**
         * initialize
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _attachEvent();
            _initPagination(htOptions);
            _initImplicitTitlePrefix();
            _listHoverEffect();
            _initTwoColumnMode();
        }

        function _listHoverEffect(){
            document.querySelectorAll(".post-list-wrap > .post-item").forEach(function(el){
                if(el.matches(".notice-wrap > .post-item")){
                    return;
                }
                el.addEventListener("mouseenter", function () {
                    this.style.backgroundColor = "#fafafa";
                });
                el.addEventListener("mouseleave", function () {
                    this.style.backgroundColor = "#fff";
                });
            });
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = document.querySelector(htOptions.sOptionForm || "#option_form");
            htElement.welInputOrderBy = htElement.welForm.querySelector("input[name=orderBy]");
            htElement.welInputOrderDir = htElement.welForm.querySelector("input[name=orderDir]");
            htElement.welInputPageNum = htElement.welForm.querySelector("input[name=pageNum]");
            // .post-list-wrap은 공지(.notice-wrap)와 일반 게시글 목록에 각각 따로 렌더링되고
            // (board/list.html), 게시글이 하나도 없으면 아예 존재하지 않을 수도 있다 - 단일
            // 엘리먼트로 취급하면 안 되므로 매칭되는 전체를 컬렉션으로 다룬다.
            htElement.welIssueWrap = document.querySelectorAll(htOptions.welIssueWrap || '.post-list-wrap');

            htElement.welPages = document.querySelectorAll(htOptions.sQueryPages || "#pagination a");
            htElement.welPagination = htOptions.elPagination || document.querySelector('#pagination');
        }

        /**
         * attach event handlers
         */
        function _attachEvent() {
            htElement.welPages.forEach(function(el){ el.addEventListener("click", _onClickPage); });
            htElement.welIssueWrap.forEach(function(wrap){
                wrap.addEventListener("click", function(e){
                    var match = e.target.closest("a[data-label-id][data-category-id]");
                    if(match && wrap.contains(match)){
                        _onClickLabelOnList.call(match, e);
                    }
                });
            });
        }

        /**
         * onClick PageNum
         */
        function _onClickPage(event){
            event.preventDefault();
            htElement.welInputPageNum.value = this.getAttribute("pageNum");
            htElement.welForm.submit();
            return false;
        }

        /**
         * "click" event handler of labels on the list.
         * Add clicked label to search form condition.
         *
         * @param event
         * @private
         */
        function _onClickLabelOnList(weEvt) {
            weEvt.preventDefault();

            var link = this;
            var targetQuery = "[data-search=labelIds]";
            var target = htElement.welForm.querySelector(targetQuery);

            var labelId = link.dataset.labelId;
            var newValue;

            if(target.multiple){
                newValue = _getSelectedValues(target) || [];
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
            console.log("labelId", labelId);
        }

        /**
         * jQuery .val()이 multiple select에서 반환하던 "선택된 option value 배열"과 동일하게 맞춘다.
         */
        function _getSelectedValues(selectEl){
            return Array.prototype.filter.call(selectEl.options, function(opt){ return opt.selected; })
                .map(function(opt){ return opt.value; });
        }

        function _initPagination(htOptions){
            yona.Pagination.update(htElement.welPagination, htOptions.nTotalPages);
        }

        _init(htOptions);
    };

})("yona.board.List");
