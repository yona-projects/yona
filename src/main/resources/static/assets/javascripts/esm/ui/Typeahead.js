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

// 이 파일은 같은 이름에 함수와 정적 멤버를 등록하는 구조라 본문은 그대로 두고 ES 모듈 태그로 불러온다(전역 $yona가 먼저 준비되어 있다).
/**
 * yona.ui.Typeahead
 *
 * 입력란 아래에 후보 목록을 보여 주는 자동 완성이다. v1.6은 BS2 플러그인(bootstrap-typeahead 2.3.1과 better-typeahead)을 jQuery로 불렀고,
 * 지금은 플러그인 없이 같은 동작을 한다(S-10, A-426). 동작은 옛 플러그인에서 시험으로 확인한 그대로다.
 *
 * 동작(v1.6과 같다):
 * - `keyup`마다 입력란 값으로 후보를 찾는다. 값의 길이가 `minLength`(기본 0)보다 짧으면 목록을 닫는다. 초점이 들어올 때도 찾으므로 minLength가 0이면
 *   초점만 가도 전체 후보가 열린다.
 * - 후보는 검색어를 대소문자 구분 없이 포함하는 것만 남기고, 검색어로 시작하는 것, 대소문자까지 같게 포함하는 것, 나머지 순으로 정렬해 최대 10개를 보여 준다.
 *   첫 항목은 선택 표시(`active`)가 되고(검색어가 없을 때는 표시하지 않는다), 기본 표시는 검색어와 일치하는 글자를 `<strong>`으로 강조한다.
 * - 위, 아래 방향키는 선택을 옮기고(끝에서 처음으로, 처음에서 끝으로 돌아간다), Enter는 키를 뗄 때, Tab은 키를 누를 때 선택한 항목을 입력란에 채우고
 *   `change`를 낸 뒤 닫는다. Esc는 닫기만 한다. 목록이 열려 있는 동안에는 키 이벤트가 위로 퍼지지 않는다.
 * - 마우스를 올린 항목이 선택 표시되고, 누르면 채운 뒤 입력란에 초점을 돌려 준다. 입력란이 초점을 잃으면 닫히지만 마우스가 목록 위에 있는 동안은 닫히지 않는다.
 * - 목록(`ul.typeahead.dropdown-menu`)은 입력란 바로 다음에 놓이고 입력란의 왼쪽 아래에 맞춰진다.
 *
 * 한글 입력기로 조합하는 중에 누른 Enter는 조합을 마치는 키이므로 항목을 고르지 않는다(S-05와 같은 규칙, v1.6에는 없던 보완이다).
 *
 * @example
 * new yona.ui.Typeahead(htElement.welInputAddTag, {
 *      "sActionURL": htVar.sURLTags,
 *      "htData": {
 *          "context": "PROJECT_TAGGING_TYPEAHEAD",
 *          "project_id": htVar.nProjectId,
 *          "limit": 8
 *      }
 * });
 *
 * `htData`의 선택 옵션:
 * - `source`: 후보 배열(나중에 항목을 더해도 반영된다) 또는 `function(query, process)`(비동기라면 나중에 `process(후보 배열)`을 부른다). 없으면 `sActionURL`로 묻는다.
 * - `minLength`: 찾기 시작하는 입력 길이. 기본 0.
 * - `maxItems`: 한 번에 보여 주는 후보의 최대 수. 기본 10(`limit`은 서버에 보내는 값일 뿐 이 수를 바꾸지 않는다).
 * - `updater(item)`: 고른 항목을 입력란에 채울 값으로 바꾼다. 기본은 항목 그대로이다.
 * - `render(item)`: 항목 하나를 목록에 그릴 HTML 글자를 돌려준다(아바타 이미지 같은 것). 기본은 강조한 항목 글자이다.
 * - 그 밖의 값은 서버에 보내는 질의에 붙는다(`context`, `limit` 등).
 */

(function(ns){

    const oNS = $yona.createNamespace(ns);

    /** 한 번에 보여 주는 후보의 최대 수. `limit` 옵션은 서버에 보내는 값일 뿐 이 수를 바꾸지 않는다(v1.6과 같다). */
    const MAX_ITEMS = 10;

    /** 입력란마다 하나의 자동 완성만 둔다. 같은 입력란에 다시 만들면 옵션만 새 값으로 바뀐다. */
    const instances = new WeakMap();

    function _escape(sText){
        return String(sText).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    /** 후보 하나를 검색어 강조와 함께 HTML로 만든다. 글자는 이스케이프하고 일치하는 부분만 `<strong>`으로 감싼다. */
    function _highlight(sItem, sQuery){
        const rx = new RegExp("(" + sQuery.replace(/[\-\[\]{}()*+?.,\\\^$|#\s]/g, "\\$&") + ")", "ig");
        return sItem.split(rx).map((sPart, i) =>{
            return (i % 2) ? "<strong>" + _escape(sPart) + "</strong>" : _escape(sPart);
        }).join("");
    }

    /** 자동 완성 하나가 입력란 하나를 맡는다. */
    function Typeahead(elInput, htOptions){
        this.input = elInput;
        this.menu = null;
        this.shown = false;
        this.focused = false;
        this.mousedover = false;
        this.query = "";
        this.composingKeydown = false;
        this.suppressKeyPressRepeat = false;
        this.configure(htOptions);
        this._listen();
    }

    Typeahead.prototype = {

        /** 옵션을 바꾼다. 같은 입력란에 다시 만들 때도 쓴다. */
        configure: function(htOptions){
            this.source = htOptions.source;
            this.minLength = htOptions.minLength || 0;
            this.maxItems = htOptions.maxItems || MAX_ITEMS;
            this.updater = typeof htOptions.updater === "function" ? htOptions.updater : function(item){ return item; };
            this.renderItem = typeof htOptions.render === "function" ? htOptions.render : null;
        },

        _listen: function(){
            const that = this;
            this.input.addEventListener("focus", () =>{ that._onFocus(); });
            this.input.addEventListener("blur", () =>{ that._onBlur(); });
            this.input.addEventListener("keydown", (e) =>{ that._onKeyDown(e); });
            this.input.addEventListener("keypress", (e) =>{ that._onKeyPress(e); });
            this.input.addEventListener("keyup", (e) =>{ that._onKeyUp(e); });
        },

        _createMenu: function(){
            const that = this;
            const menu = document.createElement("ul");
            menu.className = "typeahead dropdown-menu";
            menu.setAttribute("role", "listbox");
            menu.addEventListener("click", (e) =>{
                e.stopPropagation();
                e.preventDefault();
                that.select();
                that.input.focus();
            });
            return menu;
        },

        /** 입력란 값으로 후보를 찾는다. */
        lookup: function(){
            this.query = this.input.value || "";
            if (this.query.length < this.minLength) {
                return this.shown ? this.hide() : this;
            }
            const items = typeof this.source === "function" ? this.source(this.query, this.process.bind(this)) : this.source;
            return items ? this.process(items) : this;
        },

        /** 찾은 후보를 거르고 정렬해 보여 준다. */
        process: function(aItems){
            const sQuery = this.query.toLowerCase();
            let matched = aItems.filter((item) =>{ return ~item.toLowerCase().indexOf(sQuery); });
            const beginsWith = [], caseSensitive = [], caseInsensitive = [];
            for (let i = 0; i < matched.length; i++) {
                const item = matched[i];
                if (!item.toLowerCase().indexOf(sQuery)) { beginsWith.push(item); }
                else if (~item.indexOf(this.query)) { caseSensitive.push(item); }
                else { caseInsensitive.push(item); }
            }
            matched = beginsWith.concat(caseSensitive, caseInsensitive);
            if (!matched.length) {
                return this.shown ? this.hide() : this;
            }
            return this.render(matched.slice(0, this.maxItems)).show();
        },

        render: function(aItems){
            const that = this;
            if (!this.menu) { this.menu = this._createMenu(); }
            this.menu.textContent = "";
            aItems.forEach((sItem) =>{
                const li = document.createElement("li");
                li.setAttribute("data-value", sItem);
                li.setAttribute("role", "presentation");
                const a = document.createElement("a");
                a.className = "dropdown-item";
                a.setAttribute("href", "#");
                a.setAttribute("role", "option");
                a.innerHTML = that.renderItem ? that.renderItem(sItem) : _highlight(sItem, that.query);
                li.appendChild(a);
                li.addEventListener("mouseenter", () =>{
                    that.mousedover = true;
                    that._setActive(li);
                });
                li.addEventListener("mouseleave", () =>{
                    that.mousedover = false;
                    if (!that.focused && that.shown) { that.hide(); }
                });
                that.menu.appendChild(li);
            });
            if (this.query.length > 0) { this.menu.firstElementChild.classList.add("active"); }
            return this;
        },

        _setActive: function(li){
            const active = this.menu.querySelector(".active");
            if (active) { active.classList.remove("active"); }
            if (li) { li.classList.add("active"); }
        },

        /** 목록을 입력란 바로 다음에 놓고 입력란의 왼쪽 아래에 맞춘다(jQuery `position()`처럼 입력란의 바깥 여백은 뺀다). */
        show: function(){
            const input = this.input, style = window.getComputedStyle(input);
            this.menu.style.top = (input.offsetTop - parseFloat(style.marginTop) + input.offsetHeight) + "px";
            this.menu.style.left = (input.offsetLeft - parseFloat(style.marginLeft)) + "px";
            input.parentNode.insertBefore(this.menu, input.nextSibling);
            this.menu.style.display = "block";
            this.shown = true;
            input.setAttribute("aria-expanded", "true");
            return this;
        },

        hide: function(){
            if (this.menu) { this.menu.style.display = "none"; }
            this.shown = false;
            this.input.setAttribute("aria-expanded", "false");
            return this;
        },

        /** 선택 표시된 항목을 입력란에 채우고 `change`를 낸 뒤 닫는다. 선택 항목이 없으면 updater에 undefined가 가고 입력란이 비워진다(v1.6과 같다). */
        select: function(){
            const active = this.menu && this.menu.querySelector(".active");
            const value = this.updater(active ? active.getAttribute("data-value") : undefined);
            this.input.value = value == null ? "" : value;
            this.input.dispatchEvent(new Event("change", { "bubbles": true }));
            return this.hide();
        },

        next: function(){
            const active = this.menu.querySelector(".active");
            const next = active && active.nextElementSibling;
            if (active) { active.classList.remove("active"); }
            (next || this.menu.firstElementChild).classList.add("active");
        },

        prev: function(){
            const active = this.menu.querySelector(".active");
            const prev = active && active.previousElementSibling;
            if (active) { active.classList.remove("active"); }
            (prev || this.menu.lastElementChild).classList.add("active");
        },

        /** 목록이 열려 있을 때 방향키, Enter, Esc를 처리하고 키 이벤트가 위로 퍼지지 않게 한다. */
        _move: function(e){
            if (!this.shown) { return; }
            switch (e.key) {
                case "Enter":
                case "Escape":
                    e.preventDefault();
                    break;
                case "ArrowUp":
                    e.preventDefault();
                    this.prev();
                    break;
                case "ArrowDown":
                    e.preventDefault();
                    this.next();
                    break;
            }
            e.stopPropagation();
        },

        _onKeyDown: function(e){
            this.suppressKeyPressRepeat = ["ArrowDown", "ArrowUp", "Tab", "Enter", "Escape"].indexOf(e.key) >= 0;
            this.composingKeydown = $yona.isComposingKey(e);
            if (e.key === "Tab") {
                // 목록을 열어 둔 채 Tab으로 나가면 고르고 나간다(초점 이동은 막지 않는다)
                if (!this.shown) { return; }
                this.select();
            } else {
                this._move(e);
            }
        },

        _onKeyPress: function(e){
            if (this.suppressKeyPressRepeat) { return; }
            this._move(e);
        },

        _onKeyUp: function(e){
            switch (e.key) {
                case "ArrowDown":
                case "ArrowUp":
                case "Shift":
                case "Control":
                case "Alt":
                    break;
                case "Enter":
                    if (!this.shown) { return; }
                    if (this.composingKeydown) { this.composingKeydown = false; return; }
                    this.select();
                    break;
                case "Escape":
                    if (!this.shown) { return; }
                    this.hide();
                    break;
                default:
                    this.lookup();
            }
            e.stopPropagation();
            e.preventDefault();
        },

        _onFocus: function(){
            this.focused = true;
            if (!this.mousedover) { this.lookup(); }
        },

        _onBlur: function(){
            this.focused = false;
            if (!this.mousedover && this.shown) { this.hide(); }
        }
    };

    oNS.container[oNS.name] = function(sQuery, htOptions){

        const htVar = {};
        const htElement = {};

        /**
         * Initialize component
         * @param {String} sQuery
         * @param {Hash Table} htOptions
         */
        function _init(sQuery, htOptions){
            _initVar(htOptions);
            _initElement(sQuery);
        }

        /**
         * Initialize variables
         * @param {Hash Table} htOptions
         */
        function _initVar(htOptions){
            htVar.sActionURL = htOptions.sActionURL || ((window.yonaContextPath || "") + "/users");
            htVar.rxContentRange = /items\s+([0-9]+)\/([0-9]+)/;
            htVar.htData = htOptions.htData || {};
        }

        /**
         * Initialize element
         * @param {String} sQuery
         */
        function _initElement(sQuery){
            try {
                htElement.welInput = $(sQuery);
                const elInput = htElement.welInput.get(0);
                if (!elInput) { return; }

                const htOptions = {
                    "source"   : htVar.htData.source || _onTypeAhead,
                    "minLength": htVar.htData.minLength || 0,
                    "maxItems" : htVar.htData.maxItems,
                    "updater"  : htVar.htData.updater,
                    "render"   : htVar.htData.render
                };

                const oTypeahead = instances.get(elInput);
                if (oTypeahead) {
                    oTypeahead.configure(htOptions);
                } else {
                    instances.set(elInput, new Typeahead(elInput, htOptions));
                }
            } catch (err){
                if(typeof console == "object") {
                    console.log(err);
                }
            }
        }

        /**
        * Data source for loginId typeahead while adding new member.
        *
        * @param {String} sQuery
        * @param {Function} fProcess
        */
        function _onTypeAhead(sQuery, fProcess) {
            if (sQuery.match(htVar.sLastQuery) && htVar.bIsLastRangeEntire) {
                fProcess(htVar.htCachedUsers);
            } else {
                htVar.htData.query = sQuery;
                $yona.sendForm({
                    "sURL"        : htVar.sActionURL,
                    "htOptForm"    : {"method":"get"},
                    "htData"    : htVar.htData,
                    "sDataType" : "json",
                    "fOnLoad"    : function(oData, oStatus, oXHR){
                        let sContentRange = oXHR.getResponseHeader('Content-Range');

                        htVar.bIsLastRangeEntire = _isEntireRange(sContentRange);
                        htVar.sLastQuery = sQuery;
                        htVar.htCachedUsers = oData;

                        fProcess(oData);
                        sContentRange = null;
                    }
                });
            }
        }

        /**
         * Return whether the given content range is an entire range for items.
         * e.g) "items 10/10"
         *
         * @param {String} sContentRange the value of Content-Range header from response
         * @return {Boolean}
         */
         function _isEntireRange(sContentRange){
             const aMatch = htVar.rxContentRange.exec(sContentRange || ""); // [1]=total, [2]=items
             return (aMatch) ? !(parseInt(aMatch[1], 10) < parseInt(aMatch[2], 10)) : true;
         }

        _init(sQuery, htOptions || {});
    };

})("yona.ui.Typeahead");
