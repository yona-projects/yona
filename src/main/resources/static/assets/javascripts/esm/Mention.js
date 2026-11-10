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

// 이 파일은 같은 이름에 함수와 정적 멤버를 등록하는 구조라 본문은 그대로 두고 ES 모듈 태그로 불러온다(전역 $yona가 먼저 준비되어 있다).
/**
 * 본문 입력란의 멘션 자동완성이다. `@`는 사용자, `#`는 이슈, `:`는 이모지를 고른다.
 *
 * 엔진은 GitHub의 text-expander-element(2.9.4)이다(D-106). v1.6의 At.js를 대체하며 호출 모양
 * `yona.Mention({target, url})`, 서버 호출(`url?query=&mentionType=user|issue`), 정렬 규칙, 후보 10개,
 * 이모지 목록, 시작 규칙(공백 뒤에서 시작, 질의 20자까지)은 v1.6과 같다.
 *
 * `target`에 걸리는 모든 `textarea`와 `input`을 `<text-expander keys="@ : #" data-mention-url="...">`로 감싸고,
 * 후보 목록은 `<ul class="mention-menu">`로 만들어 요소에 넘긴다. At.js와 같이 그 밖의 요소(`<review-editor>` 등)는 건드리지 않는다.
 * 요소 정의는 `common/scripts.html`이 모듈로 불러온다. 모듈이 늦게 정의되어도 이미 감싼 요소는 정의되는 때에 붙는다.
 *
 * 이벤트는 문서 전체에서 캡처 단계로 한 번만 받는다. 요소가 보내는 이벤트(`text-expander-change` 등)는 버블링하지 않지만
 * 캡처 단계는 지나가고, 감싼 요소의 속성(`data-mention-url`)은 복제(`cloneNode`, jQuery `clone`)되어도 남는다.
 * 그래서 코드 diff처럼 입력란을 복제해 만드는 화면에서도 따로 호출하지 않고 후보가 열린다.
 *
 * @param {Object} htOptions
 * @param {String} htOptions.target 입력란 선택자(`textarea`)
 * @param {String} htOptions.url 사용자와 이슈 후보를 돌려주는 주소
 */
(function() {

    /** 후보를 보여 줄 최대 개수(At.js `limit`) */
    const LIMIT = 10;
    /** 질의 최대 길이(At.js `maxLen`). 넘으면 후보를 열지 않는다 */
    const MAX_QUERY_LENGTH = 20;
    /** 사용자 검색의 입력 지연(밀리초). v1.6은 마지막 입력 300ms 뒤에 서버에 묻는다 */
    const USER_SEARCH_DELAY = 300;
    /** 감싼 요소에 서버 주소를 남기는 속성 */
    const URL_ATTRIBUTE = "data-mention-url";

    const EMOJIS = [
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

    /** 감싼 요소마다의 상태(검색 순서와 입력 지연). 요소가 복제되어도 처음 이벤트에서 새로 만든다 */
    const oContexts = new WeakMap();
    let bInstalled = false;

    /**
     * 입력란을 `<text-expander>`로 감싼다. 이미 감싸져 있으면 주소만 갱신한다.
     *
     * 요소는 연결될 때 안쪽의 입력란을 찾으므로 입력란을 담은 뒤에 문서에 넣는다.
     * 입력란 자체는 옮겨질 뿐 같은 요소이므로 위에 걸린 핸들러는 유지된다. 포커스는 다시 돌려 둔다.
     *
     * @param {HTMLElement} elTarget
     * @param {String} sUrl
     */
    function _wrap(elTarget, sUrl) {
        const elParent = elTarget.parentNode;
        if(elParent && elParent.tagName === "TEXT-EXPANDER"){
            elParent.setAttribute(URL_ATTRIBUTE, sUrl);
            return;
        }
        const bFocused = (document.activeElement === elTarget);
        const elExpander = document.createElement("text-expander");
        elExpander.setAttribute("keys", "@ : #");
        elExpander.setAttribute(URL_ATTRIBUTE, sUrl);
        const elNext = elTarget.nextSibling;
        elExpander.appendChild(elTarget);
        elParent.insertBefore(elExpander, elNext);
        if(bFocused){
            elTarget.focus();
        }
    }

    /**
     * 문서 전체에서 한 번만 이벤트를 듣는다(캡처 단계).
     */
    function _install() {
        if(bInstalled){
            return;
        }
        bInstalled = true;
        document.addEventListener("text-expander-change", _onChange, true);
        document.addEventListener("text-expander-value", _onValue, true);
        document.addEventListener("keydown", _onKeydownCapture, true);
    }

    /**
     * 이벤트가 멘션을 붙인 요소에서 왔는지 찾는다.
     *
     * @param {Node} elNode
     * @return {HTMLElement|null} `data-mention-url`이 있는 `<text-expander>`
     */
    function _expanderOf(elNode) {
        if(!elNode || !elNode.closest){
            return null;
        }
        const elExpander = elNode.closest("text-expander");
        return (elExpander && elExpander.hasAttribute(URL_ATTRIBUTE)) ? elExpander : null;
    }

    /**
     * 요소의 상태를 가져온다. 없으면 만든다.
     */
    function _contextOf(elExpander) {
        let htContext = oContexts.get(elExpander);
        if(!htContext){
            htContext = {
                "url": elExpander.getAttribute(URL_ATTRIBUTE),
                "timer": null,
                "waiting": []
            };
            htContext.userSearch = _newSource((sQuery) =>{ return _searchUsers(htContext, sQuery); });
            htContext.issueSearch = _newSource((sQuery) =>{ return _searchIssues(htContext, sQuery); });
            oContexts.set(elExpander, htContext);
        }
        return htContext;
    }

    /**
     * 후보 목록이 열려 있는지
     */
    function _isMenuOpen(elExpander) {
        return !!elExpander.querySelector(".mention-menu");
    }

    /**
     * 후보가 열려 있을 때의 키 처리(캡처 단계, 입력란보다 먼저 받는다).
     *
     * - 조합 중(isComposing, keyCode 229)의 키는 입력기의 것이다. Safari는 조합 중에도 key를 "Enter"로 보내므로
     *   그대로 두면 후보가 골라진다. 입력란에 닿기 전에 막는다.
     * - Alt+Enter는 At.js에서 Enter와 같이 후보를 골랐다. 요소는 Alt가 눌린 키를 무시하므로 Alt 없는 Enter로 바꿔 전달한다.
     *
     * @param {KeyboardEvent} eEvt
     */
    function _onKeydownCapture(eEvt) {
        const elExpander = _expanderOf(eEvt.target);
        if(!elExpander || !_isMenuOpen(elExpander)){
            return;
        }
        if(eEvt.isComposing || eEvt.keyCode === 229){
            eEvt.stopPropagation();
            return;
        }
        if(eEvt.altKey && eEvt.key === "Enter"){
            eEvt.preventDefault();
            eEvt.stopPropagation();
            eEvt.target.dispatchEvent(new KeyboardEvent("keydown", {"key": "Enter", "bubbles": true, "cancelable": true}));
        }
    }

    /**
     * 질의가 바뀌었을 때 후보를 만들어 요소에 넘긴다.
     *
     * At.js와 같이 `@`, `#`, `:` 앞이 공백이나 줄 처음일 때만, 질의가 20자 이하일 때만 연다.
     * (요소는 `(`와 `[` 뒤에서도 열지만 At.js는 열지 않았다.)
     *
     * @param {CustomEvent} eEvt text-expander-change
     */
    function _onChange(eEvt) {
        const elExpander = _expanderOf(eEvt.target);
        if(!elExpander){
            return;
        }
        const htDetail = eEvt.detail;
        const sKey = htDetail.key;
        const sQuery = htDetail.text;
        const elTarget = elExpander.querySelector("textarea, input");

        const nKeyPos = elTarget.selectionEnd - sQuery.length - sKey.length;
        const sBefore = nKeyPos > 0 ? elTarget.value.charAt(nKeyPos - 1) : "";
        if((sBefore && !/\s/.test(sBefore)) || sQuery.length > MAX_QUERY_LENGTH){
            return;
        }

        const htContext = _contextOf(elExpander);
        let pSource;
        if(sKey === "@"){
            pSource = htContext.userSearch(sQuery);
        } else if(sKey === "#"){
            pSource = htContext.issueSearch(sQuery);
        } else {
            pSource = Promise.resolve({"query": sQuery, "items": _filterEmojis(sQuery)});
        }
        htDetail.provide(pSource.then((htResult) =>{
            // 강조는 후보를 만든 질의로 한다(앞선 호출이 늦게 끝나도 가장 나중 질의의 후보를 가장 나중 질의로 그린다)
            return _toMenu(htResult.items, htResult.query, sKey);
        }));
    }

    /**
     * 후보를 골랐을 때 넣을 글자를 정한다. 뒤에 공백이 붙는다(요소 기본값).
     *
     * @param {CustomEvent} eEvt text-expander-value
     */
    function _onValue(eEvt) {
        if(_expanderOf(eEvt.target)){
            eEvt.detail.value = eEvt.detail.item.getAttribute("data-value");
        }
    }

    /**
     * 요청 하나를 순서가 어긋나지 않게 감싼다. 먼저 보낸 요청의 응답이 늦게 와도 가장 나중 요청의 결과를 쓴다.
     *
     * @param {Function} fSearch 질의를 받아 `{query, items}` Promise를 돌려주는 함수
     * @return {Function} 같은 모양의 함수
     */
    function _newSource(fSearch) {
        let nSeq = 0;
        let pLatest = null;
        return function(sQuery){
            const nMine = ++nSeq;
            const pMine = fSearch(sQuery);
            pLatest = pMine;
            return pMine.then((htResult) =>{
                return nMine === nSeq ? htResult : pLatest;
            });
        };
    }

    /**
     * 사용자 후보. 마지막 입력 후 300ms 뒤에 서버에 한 번만 묻는다.
     * 그 사이에 들어온 앞선 호출도 같은 결과를 받아 끝난다(끝나지 않은 채로 남지 않는다).
     */
    function _searchUsers(htContext, sQuery) {
        return new Promise((resolve) =>{
            htContext.waiting.push(resolve);
            clearTimeout(htContext.timer);
            htContext.timer = setTimeout(() =>{
                const aResolvers = htContext.waiting;
                htContext.waiting = [];
                _fetch(htContext.url, "user", sQuery).then((aResult) =>{
                    const htResult = {"query": sQuery, "items": _sortUsers(sQuery, aResult)};
                    aResolvers.forEach((fResolve) =>{ fResolve(htResult); });
                });
            }, USER_SEARCH_DELAY);
        });
    }

    /**
     * 이슈 후보. 입력 지연 없이 바로 묻는다.
     */
    function _searchIssues(htContext, sQuery) {
        return _fetch(htContext.url, "issue", sQuery).then((aResult) =>{
            return {"query": sQuery, "items": _sortIssues(sQuery, aResult).slice(0, LIMIT)};
        });
    }

    /**
     * 서버에서 후보를 받아 온다. 진행 표시(NProgress)를 켜고 응답이나 실패에서 끈다.
     *
     * @param {String} sUrl
     * @param {String} sType user 또는 issue
     * @param {String} sQuery
     * @return {Promise<Array>}
     */
    async function _fetch(sUrl, sType, sQuery) {
        NProgress.start();
        try {
            const data = await yona.http.getJSON(sUrl, {"query": sQuery, "mentionType": sType});
            return (data && data.result) || [];
        } catch (failure) {
            return [];
        } finally {
            NProgress.done();
        }
    }

    /**
     * At.js 기본 정렬: 검색 문구(`searchText`)에 질의가 들어 있는 사용자만 남기고 일치 위치가 앞선 순서로 10개
     */
    function _sortUsers(sQuery, aItems) {
        if(!sQuery){
            return aItems.slice(0, LIMIT);
        }
        const sLower = sQuery.toLowerCase();
        const aScored = [];
        aItems.forEach((htItem) =>{
            const nOrder = String(htItem.searchText).toLowerCase().indexOf(sLower);
            if(nOrder > -1){
                aScored.push({"item": htItem, "order": nOrder});
            }
        });
        aScored.sort((a, b) =>{ return a.order - b.order; });
        return aScored.slice(0, LIMIT).map((htScored) =>{ return htScored.item; });
    }

    /**
     * v1.6 이슈 정렬: 번호가 질의와 같으면 맨 앞, 번호에 질의가 들어 있으면 그다음, 제목에만 있으면 뒤로 보낸다
     */
    function _sortIssues(sQuery, aItems) {
        if(!sQuery){
            return aItems;
        }
        const sLower = sQuery.toLowerCase();
        const aScored = aItems.map((htItem, i) =>{
            let nOrder;
            if(htItem.issueNo === sQuery){
                nOrder = 0;
            } else {
                const nIssueNoIndex = htItem.issueNo.toLowerCase().indexOf(sLower);
                nOrder = i + 1 + Math.pow(10, nIssueNoIndex) + ((nIssueNoIndex > -1) ? 0 : Math.pow(100, htItem.title.toLowerCase().indexOf(sLower)));
            }
            return {"item": htItem, "order": nOrder};
        });
        aScored.sort((a, b) =>{ return a.order - b.order; });
        return aScored.map((htScored) =>{ return htScored.item; });
    }

    /**
     * 이모지 후보. 이름에 질의가 든 것만 일치 위치가 앞선 순서로 10개
     */
    function _filterEmojis(sQuery) {
        if(!sQuery){
            return EMOJIS.slice(0, LIMIT);
        }
        const sLower = sQuery.toLowerCase();
        const aScored = [];
        EMOJIS.forEach((htEmoji) =>{
            const nOrder = htEmoji.name.toLowerCase().indexOf(sLower);
            if(nOrder > -1){
                aScored.push({"item": htEmoji, "order": nOrder});
            }
        });
        aScored.sort((a, b) =>{ return a.order - b.order; });
        return aScored.slice(0, LIMIT).map((htScored) =>{ return htScored.item; });
    }

    function _escape(sText) {
        return String(sText).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    /**
     * 글자에서 질의와 일치하는 부분을 굵게 보이도록 감싼다(At.js의 강조와 같다). 값은 모두 이스케이프한다.
     */
    function _highlight(sText, sQuery) {
        const sEscaped = _escape(sText);
        if(!sQuery){
            return sEscaped;
        }
        const oRegExp = new RegExp("(" + _escape(sQuery).replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + ")", "ig");
        return sEscaped.replace(oRegExp, "<strong>$1</strong>");
    }

    /**
     * 후보 목록을 요소에 넘길 형태로 만든다. 후보가 없으면 열지 않는다(matched: false).
     *
     * @param {Array} aItems
     * @param {String} sQuery
     * @param {String} sKey @, # 또는 :
     * @return {{matched: Boolean, fragment: HTMLElement}}
     */
    function _toMenu(aItems, sQuery, sKey) {
        const elMenu = document.createElement("ul");
        elMenu.className = "mention-menu";
        aItems.forEach((htItem) =>{
            const elItem = document.createElement("li");
            elItem.setAttribute("role", "option");
            if(sKey === "@"){
                elItem.setAttribute("data-value", "@" + htItem.loginid);
                elItem.innerHTML = (htItem.image ? "<img style='width:20px;height:20px;' src='" + _escape(htItem.image) + "'> " : "")
                    + _highlight(htItem.name, sQuery) + " <small>" + _highlight(htItem.loginid, sQuery) + "</small>";
            } else if(sKey === "#"){
                elItem.setAttribute("data-value", "#" + htItem.issueNo);
                elItem.innerHTML = "<small>#" + _highlight(htItem.issueNo, sQuery) + "</small> " + _highlight(htItem.title, sQuery);
            } else {
                elItem.setAttribute("data-value", htItem.content);
                elItem.innerHTML = _escape(htItem.content) + " <small>" + _highlight(htItem.name, sQuery) + "</small>";
            }
            elMenu.appendChild(elItem);
        });
        return {"matched": aItems.length > 0, "fragment": elMenu};
    }

    /**
     * @param {Object} htOptions
     */
    yona.Mention = function(htOptions) {
        htOptions = htOptions || {};
        if (!htOptions.target) {
            if (window.console) {
                console.error("mention form element targeting doesn't exist!");
            }
            return;
        }
        const aTargets = $(htOptions.target).filter("textarea, input").get();
        aTargets.forEach((elTarget) =>{
            _wrap(elTarget, htOptions.url);
        });
        _install();
    };

})();
