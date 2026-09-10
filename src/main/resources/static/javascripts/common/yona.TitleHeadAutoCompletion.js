/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
// P3-46 #3: atjs(jquery.atwho.js) -> Tribute.js 교체.
//
// yobi.Mention.js와 동일한 이유로 즉시(eager) 등록 방식으로 단순화했다 — atjs 시절엔 "[" 키가
// 처음 눌렸을 때만 지연 등록했지만, 실제 드롭다운은 여전히 "[" 를 입력했을 때만 나타나므로
// 사용자 입장에서 차이가 없다. 단, _initTribute()를 _attachEvent()(파이어폭스 IME 폴리필,
// jQuery.browser 미로드로 인해 항상 TypeError가 발생하는 기존 버그 — 최종 보고 참고)보다 반드시
// 먼저 호출해 자동완성 등록 자체는 그 크래시의 영향을 받지 않도록 한다.
function yonaTitleHeadModule(htOptions){
    var htVar = {};
    var htElement = {};
    var issueLabels = [];
    var projectLabels = getProjectLabels();
    var tribute;
    var searchPending; // atjs 시절과 동일하게 300ms debounce

    var MAX_QUERY_LEN = 20; // atjs DEFAULT_CALLBACKS.matcher의 maxLen(기본값 20) 이식

    /**
     * atjs DEFAULT_CALLBACKS.highlighter 이식(yobi.Mention.js 주석 참고) — Tribute의
     * menuItemTemplate은 <li> 내부 콘텐츠만 돌려주므로, 원본과 동일한 경계 조건으로 검색어를
     * <strong>으로 감싸기 위해 임시로 <li>...</li> 로 감쌌다가 다시 벗겨낸다.
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

        if($("#labelIds").length > 0 ) {
            issueLabels = $("#labelIds").select2("val");
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
        htElement.welTarget = $(htVar.target);
    }

    /**
     * "[" 트리거(이슈 라벨 접두어) 원격 검색. atjs 시절과 동일하게 300ms debounce 후 조회하며,
     * 매 호출마다(디바운스 전) select2에서 현재 선택된 라벨 목록을 다시 읽어 issueLabels를
     * 최신화한다(원본 remoteFilter의 순서를 그대로 유지).
     */
    function _fetchTitleHeads(query, callback) {
        if (query.length > MAX_QUERY_LEN) {
            callback([]);
            return;
        }
        NProgress.start();
        issueLabels = $("#labelIds").length > 0 && $("#labelIds").select2("val") || [];
        clearTimeout(searchPending);

        searchPending = setTimeout(function () {
            $.getJSON(htVar.url, { query: query }, function (data) {
                NProgress.done();
                callback(_sortLabels(query, data.result || []));
            });
        }, 300);
    }

    /**
     * yona.TitleHeadAutoCompletion.js(atjs 버전)의 커스텀 sorter를 그대로 이식한 것 —
     * searchKey(searchText)로 부분일치 필터링 후 frequency 내림차순 -> category 오름차순 ->
     * name 오름차순으로 정렬한다. 다른 트리거와 달리 질의가 비어 있어도(빈 문자열이면 모든
     * 항목이 부분일치하므로) 이 정렬을 그대로 적용한다 — 원본에 `if(!query)` 단락 처리가 없다.
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
            // atjs suffix:"" (자동 공백 삽입 없음)를 그대로 재현 — selectTemplate이 최종 삽입
            // 텍스트를 직접 반환하므로 Tribute의 전역 접미사는 비워둔다.
            replaceTextSuffix: "",
            collection: [
                {
                    trigger: "[",
                    // atjs startWithSpace:false 이식 — 다른 3개 트리거와 달리 공백 없이도
                    // 바로 뒤에서 매칭을 시작한다.
                    requireLeadingSpace: false,
                    allowSpaces: false,
                    menuShowMinLength: 0,
                    searchOpts: { skip: true },
                    values: _fetchTitleHeads,
                    // atjs beforeInsert 이식: #labelIds가 있는 화면(issue create/edit)에서는
                    // 라벨을 select2 쪽에 바로 선택 처리하고 텍스트는 삽입하지 않는다(""를 반환).
                    // #labelIds가 없는 화면(board create)에서는 "[name]" 텍스트를 그대로 삽입한다
                    // (suffix가 ""라 뒤에 공백이 붙지 않는다).
                    selectTemplate: function(item) {
                        var original = item.original;
                        var category = original.category || "";
                        var $labelField = $("#labelIds");
                        var value = "[" + original.name + "]";

                        if (category && $labelField.length > 0) {
                            var $selectedLabel = $labelField.find("option[value=" + original.id + "]");
                            $selectedLabel.prop('selected', true);

                            if (original.isExclusive) {
                                issueLabels = issueLabels.filter(function(label) {
                                    return projectLabels[original.categoryId].indexOf(label) === -1;
                                });
                            }

                            issueLabels.push($selectedLabel.val());
                            $labelField.select2("val", issueLabels);

                            $yobi.notify('Label: ' + original.name, 3000);
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

        htElement.welTarget.each(function() {
            var el = this;
            tribute.attach(el);
            // atjs TextareaController.insert()가 삽입 후 항상 $inputor.change()를 호출하던 것과
            // 동일하게 유지한다.
            el.addEventListener("tribute-replaced", function() {
                $(el).trigger("change");
            });
        });
    }

    /**
     * attachEvent
     */
    function _attachEvent() {
        // 파이어폭스 조합입력(IME) 대응 폴리필 — atjs 시절부터 있던 코드를 그대로 유지한다
        // (결정 필요 사항: 최종 보고 참고). yobi.Mention.js와 동일하게 jQuery.browser가 이
        // 페이지들에 로드되어 있지 않아(범위 밖 발견, 최종 보고 참고) 항상 TypeError가 발생하지만,
        // _initTribute()를 먼저 호출해 두었으므로 라벨 자동완성 자체는 영향을 받지 않는다.
        if (jQuery.browser.mozilla){
            htElement.welTarget.on("focus", _startKeyupEventGenerator);
            htElement.welTarget.on("blur", _stopKeyupEventGenerator);
        }
    }

    function _startKeyupEventGenerator(){
        if (htVar.nKeyupEventGenerator){
            clearInterval(htVar.nKeyupEventGenerator);
        }

        htVar.nKeyupEventGenerator = setInterval(
            function(){
                if (htVar.sMentionText != htElement.welTarget.val()){
                    htElement.welTarget.trigger("keyup");
                    htVar.sMentionText = htElement.welTarget.val();
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
        $("#labelIds > optgroup").each(function(){
            var allLabelsOfTheCategory = [];
            var categoryId;
            $(this).children().each(function(){
                $this = $(this);
                allLabelsOfTheCategory.push($this.val());
                categoryId = $this.data("categoryId")
            });

            allLabels[categoryId] = allLabelsOfTheCategory;
        });
        return allLabels;
    }

    _init(htOptions || {});
}
