/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
// atjs(jquery.atwho.js)를 Tribute.js로 교체. 즉시(eager) 등록 방식으로 단순화했지만, 드롭다운은
// 여전히 "[" 입력 시에만 나타나므로 사용자 관점에서 동작 차이는 없다.
function yonaTitleHeadModule(htOptions){
    var htVar = {};
    var htElement = {};
    var issueLabels = [];
    var projectLabels = getProjectLabels();
    var tribute;
    var searchPending; // atjs 시절과 동일하게 300ms debounce

    var MAX_QUERY_LEN = 20; // atjs DEFAULT_CALLBACKS.matcher의 maxLen(기본값 20) 이식

    /**
     * Tribute의 menuItemTemplate은 <li> 내부 콘텐츠만 돌려주므로, 검색어를 <strong>으로
     * 감싸기 위해 임시로 <li>...</li>로 감쌌다가 다시 벗겨낸다.
     */
    function _highlight(innerHtml, query) {
        if (!query) {
            return innerHtml;
        }
        var wrapped = "<li>" + innerHtml + "</li>";
        var regexp = new RegExp(">\\s*([^<]*?)(" + query.replace("+", "\\+") + ")([^<]*)\\s*<", "ig");
        var replaced = wrapped.replace(regexp, function(str, p1, p2, p3) {
            return "> " + p1 + "<strong>" + p2 + "</strong>" + p3 + " <";
        });
        return replaced.slice("<li>".length, replaced.length - "</li>".length);
    }

    /**
     * Initialize
     *
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement();
        _initTribute();
        _attachEvent();

        // #labelIds는 yona.ui.TomSelect.js가 자동 초기화(data-toggle="tomselect")하므로
        // 인스턴스는 .tomselect 프로퍼티로 접근한다.
        var labelIdsEl = document.getElementById("labelIds");
        if (labelIdsEl && labelIdsEl.tomselect) {
            issueLabels = labelIdsEl.tomselect.getValue();
        }
    }

    /**
     * Initialize Variables
     *
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions) {
        htVar = htOptions || {}; // set htVar as htOptions
        htVar.nKeyupEventGenerator = null;
        htVar.sMentionText = null;
    }

    /**
     * Initialize Element variables
     */
    function _initElement() {
        if (!htVar.target) {
            if (window.console) {
                console.error("mention form element targeting doesn't exist!")
            }
            return;
        }
        htElement.elTarget = document.querySelector(htVar.target);
    }

    /**
     * "[" 트리거 원격 검색. atjs와 동일하게 300ms debounce 후 조회하며, 호출마다 TomSelect에서
     * 현재 선택된 라벨 목록을 다시 읽어 issueLabels를 최신화한다.
     */
    function _fetchTitleHeads(query, callback) {
        if (query.length > MAX_QUERY_LEN) {
            callback([]);
            return;
        }
        NProgress.start();
        var labelIdsEl = document.getElementById("labelIds");
        issueLabels = (labelIdsEl && labelIdsEl.tomselect && labelIdsEl.tomselect.getValue()) || [];
        clearTimeout(searchPending);

        searchPending = setTimeout(function () {
            fetch(htVar.url + "?" + new URLSearchParams({ query: query }))
                .then(function(response){ return response.json(); })
                .then(function(data){
                    NProgress.done();
                    callback(_sortLabels(query, data.result || []));
                });
        }, 300);
    }

    /**
     * frequency 내림차순 -> category 오름차순 -> name 오름차순으로 정렬한다. 질의가 비어
     * 있어도 빈 문자열은 모든 항목에 부분일치하므로 별도 처리 없이 그대로 정렬한다(원본 동작 유지).
     */
    function _sortLabels(query, items) {
        var results = [];
        items.forEach(function (item) {
            if (String(item.searchText).toLowerCase().indexOf(query.toLowerCase()) !== -1) {
                results.push(item);
            }
        });

        return results.sort(function(a, b) {
            if (b.frequency === a.frequency) {
                if (b.category.toLowerCase() === a.category.toLowerCase()) {
                    return a.name.toLowerCase() >= b.name.toLowerCase() ? 1 : -1;
                } else {
                    return a.category.toLowerCase() > b.category.toLowerCase() ? 1 : -1;
                }
            }
            return b.frequency - a.frequency;
        });
    }

    function _initTribute() {
        tribute = new Tribute({
            // selectTemplate이 최종 삽입 텍스트를 직접 반환하므로 전역 접미사는 비워둔다.
            replaceTextSuffix: "",
            collection: [
                {
                    trigger: "[",
                    // 다른 트리거와 달리 공백 없이 바로 뒤에서 매칭을 시작한다.
                    requireLeadingSpace: false,
                    allowSpaces: false,
                    menuShowMinLength: 0,
                    searchOpts: { skip: true },
                    values: _fetchTitleHeads,
                    // #labelIds가 있는 화면(issue create/edit)에서는 라벨을 select 필드에
                    // 선택 처리하고 텍스트는 삽입하지 않는다(""를 반환). 없는 화면(board create)
                    // 에서는 "[name]"을 그대로 삽입한다.
                    selectTemplate: function(item) {
                        var original = item.original;
                        var category = original.category || "";
                        var labelField = document.getElementById("labelIds");
                        var value = "[" + original.name + "]";

                        if (category && labelField) {
                            var selectedLabel = labelField.querySelector("option[value=" + original.id + "]");
                            selectedLabel.selected = true;

                            if (original.isExclusive) {
                                issueLabels = issueLabels.filter(function(label) {
                                    return projectLabels[original.categoryId].indexOf(label) === -1;
                                });
                            }

                            issueLabels.push(selectedLabel.value);
                            if(labelField.tomselect){
                                labelField.tomselect.setValue(issueLabels);
                            }

                            $yona.notify('Label: ' + original.name, 3000);
                            return "";
                        }
                        return value;
                    },
                    menuItemTemplate: function(item) {
                        var original = item.original;
                        var html = "<small style='color: #" + original.labelColor + "'>" + original.category + "</small> " + original.name;
                        return _highlight(html, tribute.current.mentionText);
                    }
                }
            ]
        });

        if (htElement.elTarget) {
            var el = htElement.elTarget;
            tribute.attach(el);
            // 원본이 삽입 후 항상 change 이벤트를 발생시키던 동작을 유지한다.
            el.addEventListener("tribute-replaced", function() {
                el.dispatchEvent(new Event("change", { bubbles: true }));
            });
        }
    }

    /**
     * attachEvent
     */
    function _attachEvent() {
        // 파이어폭스 IME 조합 입력 폴리필. 원래 jQuery.browser 미로드로 항상 TypeError가 나서
        // 이 분기가 실행된 적이 없었는데, UA 감지로 바꾸며 그 버그를 고쳤다 — 파이어폭스에서
        // 폴리필이 실제로 동작하는지는 아직 검증되지 않았다.
        if (/firefox/i.test(navigator.userAgent) && htElement.elTarget){
            htElement.elTarget.addEventListener("focus", _startKeyupEventGenerator);
            htElement.elTarget.addEventListener("blur", _stopKeyupEventGenerator);
        }
    }

    function _startKeyupEventGenerator(){
        if (htVar.nKeyupEventGenerator){
            clearInterval(htVar.nKeyupEventGenerator);
        }

        htVar.nKeyupEventGenerator = setInterval(
            function(){
                if (htVar.sMentionText != htElement.elTarget.value){
                    htElement.elTarget.dispatchEvent(new Event("keyup", { bubbles: true }));
                    htVar.sMentionText = htElement.elTarget.value;
                }
            }
            ,100);
    }

    function _stopKeyupEventGenerator(){
        if (htVar.nKeyupEventGenerator){
            clearInterval(htVar.nKeyupEventGenerator);
            htVar.nKeyupEventGenerator = null;
        }
    }

    /**
     *  It gather all project labels.
     *  Category id is used for the key.
     *  Label ids are used for the value.
     *  {
     *     31: [130, 120, ...],
     *     70: [200, 201, 320, ...]
     *  }
     */
    function getProjectLabels(){
        var allLabels = {};
        var labelIdsEl = document.getElementById("labelIds");
        if (labelIdsEl) {
            Array.prototype.forEach.call(labelIdsEl.querySelectorAll("optgroup"), function(optgroup){
                var allLabelsOfTheCategory = [];
                var categoryId;
                Array.prototype.forEach.call(optgroup.children, function(option){
                    allLabelsOfTheCategory.push(option.value);
                    categoryId = option.dataset.categoryId;
                });
                allLabels[categoryId] = allLabelsOfTheCategory;
            });
        }
        return allLabels;
    }

    _init(htOptions || {});
}
