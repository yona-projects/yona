/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Suwon Chae
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
// P3-46 #3: atjs(jquery.atwho.js) -> Tribute.js 교체.
//
// atjs 시절엔 "@"/":"/"#" 중 아무 키나 처음 눌렸을 때(_onKeyInput/_findMentionList)만 지연
// 등록(.atwho(...))했지만, Tribute는 attach() 한 번으로 3개 트리거를 동시에 등록해도 실제
// 드롭다운은 여전히 해당 트리거 문자를 타이핑했을 때만 나타나므로 사용자 입장에서 동작 차이가
// 없다 — 그래서 이번엔 _init() 시점에 즉시 등록(eager)하도록 단순화했다. 단, 이 즉시 등록을
// _attachEvent()의 파이어폭스 IME 폴리필 분기보다 반드시 먼저 실행해야 한다: 이 페이지들에는
// jQuery.browser가 전혀 로드되지 않아(atjs/jquery.browser.js도, lib/jquery/jquery.browser.js도
// 로드하지 않음 — atjs->Tribute 교체와 무관한 기존 버그, 최종 보고의 "범위 밖 발견" 참고)
// `jQuery.browser.mozilla`를 읽는 순간 항상 TypeError가 발생한다. atjs 시절엔 이 TypeError가
// "keypress 리스너 등록 이후, 실제 .atwho() 등록은 그보다 한참 뒤(사용자가 실제로 키를 누른
// 시점)"에 터졌기 때문에 멘션 기능 자체는 영향을 받지 않고, 같은 <script> 블록 안에서 이 호출
// *뒤에* 오는 다른 코드만 실행되지 않았다. 이 순서(먼저 자동완성을 완전히 붙이고, 그 다음에
// 크래시가 나는 지점)를 그대로 재현해야 기존 버그의 "영향 범위"까지 동일하게 유지된다.
yobi.Mention = function(htOptions) {

    var htVar = {};
    var htElement = {};
    var tribute;
    var searchPending; // "@" 트리거 전용 300ms debounce 타이머(atjs 시절과 동일하게 "#"는 debounce 없음)

    var emojis = [
        { name: "+1", content: "👍" },
        { name: "heart", content: "❤️️" },
        { name: "wink", content: "😘" },
        { name: "smile", content: "🙂" },
        { name: "confused", content: "😕" },
        { name: "check", content: "✅" },
        { name: "hooray", content: "🎉" },
        { name: "sad", content: "😢" },
        { name: "-1", content: "👎" },
        { name: "tada", content: "🎉" },
        { name: "x", content: "❌" },
        { name: "o", content: "⭕" },
        { name: "face smile", content: "😄" },
        { name: "face smile kiss", content: "😙" },
        { name: "face kissing", content: "😗" },
        { name: "face astonished", content: "😲" },
        { name: "face angry", content: "😠" },
        { name: "face scream", content: "😱" },
        { name: "face cry", content: "😢" },
        { name: "face neutral", content: "😐" },
        { name: "face heart", content: "😍" },
        { name: "question?", content: "❓" },
        { name: "!", content: "❗️" },
        { name: "bangbang!", content: "‼️" },
        { name: "beer", content: "🍺" },
        { name: "icecream", content: "🍦" },
        { name: "korea", content: "🇰🇷" },
        { name: "us america", content: "🇺🇸" },
        { name: "fr", content: "🇫🇷" },
        { name: "cn china", content: "🇨🇳" },
        { name: "+100", content: "💯" },
        { name: "heavy check", content: "✔️"},
        { name: "+plus", content: "➕"},
        { name: "-minus", content: "➖️"},
        { name: "cactus", content: "🌵️"},
        { name: "animal cat", content: "🐈"},
        { name: "clover", content: "🍀"},
        { name: "v️", content: "✌️"},
        { name: "lock", content: "🔒"},
        { name: "unlock", content: "🔓"},
        { name: "idea bulb", content: "💡"},
        { name: "bomb", content: "💣"},
        { name: "calendar", content: "📆"},
        { name: "date", content: "📅"},
        { name: "chicken", content: "🐔"},
        { name: "mushroom", content: "🍄"},
        { name: "moneybag", content: "💰"},
        { name: "money dollar", content: "💵"},
        { name: "envelope", content: "✉️"},
        { name: "chart upward", content: "📈"},
        { name: "chart downward", content: "📉"},
        { name: "택배 parcel", content: "📦"},
        { name: "박수 clap", content: "👏"},
        { name: "game joker", content: "🃏"},
        { name: "game cards", content: "🎴"},
        { name: "game die", content: "🎲"},
        { name: "tea", content: "🍵"},
        { name: "coffee", content: "☕"},
        { name: "crystal", content: "🔮"},
        { name: "taxi", content: "🚕"},
        { name: "bus", content: "🚌"},
        { name: "train", content: "🚋"},
        { name: "warn", content: "⚠️"},
        { name: "star", content: "⭐"},
        { name: "phone", content: "☎️"},
    ];

    // atjs DEFAULT_CALLBACKS.matcher의 maxLen(기본값 20) 이식 — 트리거 문자 뒤로 공백 없이 20자를
    // 넘는 문자열이 이어지면 atwho는 더 이상 매칭 후보로 보지 않고(쿼리 자체를 만들지 않음)
    // 드롭다운을 닫았다. Tribute엔 이에 대응하는 옵션이 없어 각 트리거의 검색 함수 초입에서
    // 직접 재현한다(빈 결과를 돌려주면 Tribute도 동일하게 메뉴를 숨긴다).
    var MAX_QUERY_LEN = 20;

    /**
     * atjs DEFAULT_CALLBACKS.highlighter 이식. atwho는 렌더링된 <li> 마크업 전체(여는/닫는
     * <li> 태그 포함) 문자열에서 검색어와 일치하는 첫 구간을 <strong>으로 감쌌다. Tribute의
     * menuItemTemplate은 <li>의 내부 콘텐츠만 돌려주는 구조라 경계 태그가 없으면 마지막 필드가
     * 하이라이트되지 않는(뒤에 닫는 '<' 가 없는) 차이가 생긴다 — 원본과 동일한 경계 조건을
     * 재현하기 위해 임시로 <li>...</li>로 감쌌다가 다시 벗겨낸다.
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
     * "@" 트리거(사용자 멘션) 원격 검색. atjs 시절과 동일하게 300ms debounce 후 조회한다.
     */
    function _fetchUserMentions(query, callback) {
        if (query.length > MAX_QUERY_LEN) {
            callback([]);
            return;
        }
        NProgress.start();
        clearTimeout(searchPending);
        searchPending = setTimeout(function () {
            $.getJSON(htVar.url, { query: query, mentionType: "user" }, function (data) {
                NProgress.done();
                callback(_sortBySearchText(query, data.result || []));
            });
        }, 300);
    }

    /**
     * atjs DEFAULT_CALLBACKS.sorter 이식(searchKey="searchText"). "@" 트리거는 atjs 시절
     * 커스텀 sorter를 넘기지 않아 기본 정렬(검색어가 searchText 내에서 나타나는 위치가 이를수록
     * 우선)이 그대로 적용됐다. 질의가 비어 있으면(트리거 문자만 입력) 서버가 돌려준 순서를
     * 그대로 유지한다.
     */
    function _sortBySearchText(query, items) {
        if (!query) {
            return items.slice(0, 10);
        }
        var scored = [];
        for (var i = 0; i < items.length; i++) {
            var item = items[i];
            var order = String(item.searchText).toLowerCase().indexOf(query.toLowerCase());
            if (order > -1) {
                item.atwho_order = order;
                scored.push(item);
            }
        }
        scored.sort(function(a, b) { return a.atwho_order - b.atwho_order; });
        return scored.slice(0, 10);
    }

    /**
     * ":" 트리거(이모지). 원격 검색 없이 하드코딩된 배열에서 로컬로 필터링/정렬한다.
     * atjs 기본 filter(이름 부분일치) + 기본 sorter(일치 위치 기준 정렬)를 그대로 이식.
     */
    function _fetchEmojis(query, callback) {
        if (query.length > MAX_QUERY_LEN) {
            callback([]);
            return;
        }
        if (!query) {
            callback(emojis.slice(0, 10));
            return;
        }
        var q = query.toLowerCase();
        var scored = [];
        for (var i = 0; i < emojis.length; i++) {
            var order = emojis[i].name.toLowerCase().indexOf(q);
            if (order > -1) {
                scored.push({ item: emojis[i], order: order });
            }
        }
        scored.sort(function(a, b) { return a.order - b.order; });
        callback(scored.slice(0, 10).map(function(s) { return s.item; }));
    }

    /**
     * "#" 트리거(이슈 참조) 원격 검색. atjs 시절과 동일하게 debounce 없이 매 키 입력마다 조회한다
     * ("@"/"[" 트리거와 다른 점 — 원본 코드 그대로 유지).
     */
    function _fetchIssueMentions(query, callback) {
        if (query.length > MAX_QUERY_LEN) {
            callback([]);
            return;
        }
        NProgress.start();
        $.getJSON(htVar.url, { query: query, mentionType: "issue" }, function (data) {
            NProgress.done();
            callback(_sortIssues(query, data.result || []).slice(0, 10));
        });
    }

    /**
     * yobi.Mention.js(atjs 버전)의 "#" 트리거 커스텀 sorter를 그대로 이식한 것 — 정확히 일치하는
     * issueNo를 최우선으로, 그 외엔 issueNo/title 내 인덱스 위치 기반 가중치로 정렬한다.
     */
    function _sortIssues(query, items) {
        if (!query) {
            return items;
        }
        var results = [];
        for (var i = 0, len = items.length; i < len; i++) {
            var item = items[i];
            if (item.issueNo === query) {
                item.atwhoOrder = 0;
            } else {
                var issueNoIndexOf = item.issueNo.toLowerCase().indexOf(query.toLowerCase());
                item.atwhoOrder = i + 1
                    + Math.pow(10, issueNoIndexOf)
                    + ((issueNoIndexOf > -1) ? 0 : Math.pow(100, item.title.toLowerCase().indexOf(query.toLowerCase())));
            }
            results.push(item);
        }
        return results.sort(function(a, b) {
            return a.atwhoOrder - b.atwhoOrder;
        });
    }

    function _initTribute() {
        tribute = new Tribute({
            // atjs suffix 기본값(공백 자동 삽입)을 그대로 재현하기 위해 Tribute의 전역
            // replaceTextSuffix는 비워두고, 트리거별 selectTemplate이 직접 접미사를 반환한다.
            replaceTextSuffix: "",
            collection: [
                {
                    trigger: "@",
                    requireLeadingSpace: true,
                    allowSpaces: false,
                    menuShowMinLength: 0,
                    searchOpts: { skip: true },
                    values: _fetchUserMentions,
                    selectTemplate: function(item) {
                        return "@" + item.original.loginid + " ";
                    },
                    menuItemTemplate: function(item) {
                        var html = "<img style='width:20px;height:20px;' src='" + item.original.image + "'> "
                            + item.original.name + " <small>" + item.original.loginid + "</small>";
                        return _highlight(html, tribute.current.mentionText);
                    }
                },
                {
                    trigger: ":",
                    requireLeadingSpace: true,
                    allowSpaces: false,
                    menuShowMinLength: 0,
                    searchOpts: { skip: true },
                    values: _fetchEmojis,
                    selectTemplate: function(item) {
                        return item.original.content + " ";
                    },
                    menuItemTemplate: function(item) {
                        var html = item.original.content + " <small>" + item.original.name + "</small>";
                        return _highlight(html, tribute.current.mentionText);
                    }
                },
                {
                    trigger: "#",
                    requireLeadingSpace: true,
                    allowSpaces: false,
                    menuShowMinLength: 0,
                    searchOpts: { skip: true },
                    values: _fetchIssueMentions,
                    selectTemplate: function(item) {
                        return "#" + item.original.issueNo + " ";
                    },
                    menuItemTemplate: function(item) {
                        var html = "<small>#" + item.original.issueNo + "</small> " + item.original.title;
                        return _highlight(html, tribute.current.mentionText);
                    }
                }
            ]
        });

        htElement.welTarget.each(function() {
            var el = this;
            tribute.attach(el);
            // atjs TextareaController.insert()가 삽입 후 항상 $inputor.change()를 호출하던 것과
            // 동일하게, 다른 코드가 이 textarea의 change 이벤트에 의존할 가능성을 고려해 유지한다.
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
        // (결정 필요 사항: 최종 보고 참고). 단, 이 페이지들에는 jQuery.browser 자체가 전혀
        // 로드되지 않아(atjs->Tribute 교체와 무관한 기존 버그, 최종 보고의 "범위 밖 발견" 참고)
        // 아래 줄이 항상 TypeError를 던진다 — _initTribute()를 이 함수보다 먼저 호출해 두었으므로
        // 멘션 자동완성 자체는 이 크래시의 영향을 받지 않고, atjs 시절과 마찬가지로 이 호출 뒤에
        // 이어지는 다른 코드만 실행되지 않는 기존 상태를 그대로 보존한다.
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

    _init(htOptions || {});
};
