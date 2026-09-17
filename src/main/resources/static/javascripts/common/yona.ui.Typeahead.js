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
/**
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
 * Bootstrap bootstrap-typeahead.js에 의존하지 않는 순수 커스텀 구현이다 - source가
 * 함수면 비동기 서버 조회(기본값 _onTypeAhead, $yona.sendForm으로 XHR의
 * Content-Range 헤더까지 확인), 배열이면 동기 필터링(issue.LabelEditor.js의
 * 카테고리 자동완성처럼)을 그대로 지원한다. 마크업/스타일(.typeahead.dropdown-menu,
 * <li><a>)은 bootstrap.css에 이미 있는 것을 그대로 재사용한다.
 */
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(vTarget, htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * @param {String|Element|jQuery} vTarget
         * @param {Hash Table} htOptions
         */
        function _init(vTarget, htOptions){
            _initVar(htOptions);
            _initElement(vTarget);
        }

        function _toElement(v){
            if(!v){
                return null;
            }
            if(typeof v === "string"){
                return document.querySelector(v);
            }
            if(v.jquery){
                return v[0] || null;
            }
            return v;
        }

        function _initVar(htOptions){
            htVar.sActionURL = htOptions.sActionURL || "/users";
            htVar.rxContentRange = /items\s+([0-9]+)\/([0-9]+)/;
            htVar.htData = htOptions.htData || {};
            htVar.nMinLength = (typeof htVar.htData.minLength === "number") ? htVar.htData.minLength : 0;
            htVar.nItems = htVar.htData.limit || 10;
            htVar.sQuery = "";
            htVar.bShown = false;

            // htVar.htData는 _onTypeAhead가 $yona.sendForm으로 서버에 그대로
            // 전송하는 쿼리 파라미터 묶음이다(context/project_id/limit 등) - source가
            // 함수/배열이면 여기 남겨둘 경우 그대로 직렬화되어 서버에 이상한
            // 파라미터로 전송된다. vSource로 분리해 htData를 오염시키지 않는다.
            htVar.vSource = htVar.htData.source || _onTypeAhead;
            delete htVar.htData.source;
        }

        function _initElement(vTarget){
            htElement.elInput = _toElement(vTarget);
            if(!htElement.elInput){
                return;
            }

            // 하이브리드 어댑터(2026-09-17, components/vue-widgets typeahead 위젯 적용) -
            // <yona-typeahead>가 로드돼 있으면 생성자 자신이 그 자리에서 기존 input을
            // 감싸고(idempotent - 이미 감싸져 있으면 재사용) configure()로 위임한다.
            // 호출부 5곳(project.Home/site.MassMail/project.Member/issue.LabelEditor/
            // organization.Member) 코드는 전혀 안 바뀐다.
            if(typeof customElements !== "undefined" && customElements.get("yona-typeahead")){
                var elWrapper = htElement.elInput.closest("yona-typeahead");
                if(!elWrapper){
                    elWrapper = document.createElement("yona-typeahead");
                    htElement.elInput.insertAdjacentElement("beforebegin", elWrapper);
                    elWrapper.appendChild(htElement.elInput);
                }
                htVar.bIsVueTypeahead = true;
                elWrapper.configure({
                    "source": htVar.vSource,
                    "minLength": htVar.nMinLength,
                    "limit": htVar.nItems
                });
                return;
            }

            htElement.elMenu = document.createElement("ul");
            htElement.elMenu.className = "typeahead dropdown-menu";
            htElement.elMenu.style.display = "none";
            htElement.elInput.insertAdjacentElement("afterend", htElement.elMenu);

            _attachEvent();
        }

        function _attachEvent(){
            htElement.elInput.addEventListener("keydown", _onKeydown);
            htElement.elInput.addEventListener("keyup", _onKeyup);
            // 한글 등 IME 조합 입력은 브라우저에 따라 keyup이 조합 문자마다
            // 안정적으로 발생하지 않을 수 있어 input 이벤트로 조회를 트리거한다
            // (input은 네비게이션 키로는 발생하지 않으니 keyup의 화살표/enter/
            // tab/esc 처리와 겹치지 않는다).
            htElement.elInput.addEventListener("input", _lookup);
            htElement.elInput.addEventListener("blur", _onBlur);
            // mousedown이 input의 blur보다 먼저 발생하므로, 메뉴 클릭 시
            // blur가 먼저 메뉴를 숨겨버리지 않도록 기본 동작(포커스 이동)을 막는다.
            htElement.elMenu.addEventListener("mousedown", function(weEvt){
                weEvt.preventDefault();
            });
            htElement.elMenu.addEventListener("click", _onClickMenu);
            htElement.elMenu.addEventListener("mouseover", _onMouseOverMenu);
        }

        function _onKeydown(weEvt){
            if(!htVar.bShown){
                return;
            }
            switch(weEvt.keyCode){
                case 9:  // tab
                case 13: // enter
                case 27: // escape
                    weEvt.preventDefault();
                    break;
                case 38: // up
                    weEvt.preventDefault();
                    _prev();
                    break;
                case 40: // down
                    weEvt.preventDefault();
                    _next();
                    break;
            }
        }

        function _onKeyup(weEvt){
            switch(weEvt.keyCode){
                case 9: case 13:
                    if(htVar.bShown){
                        _select();
                    }
                    return;
                case 27:
                    if(htVar.bShown){
                        _hide();
                    }
                    return;
            }
        }

        function _onBlur(){
            // 메뉴 클릭 시 elMenu의 mousedown 핸들러가 preventDefault()로 포커스 이동
            // 자체를 막으므로, blur는 메뉴 밖을 클릭/탭 이동했을 때만 발생한다.
            _hide();
        }

        function _onClickMenu(weEvt){
            var elItem = weEvt.target.closest("li");
            if(!elItem){
                return;
            }
            weEvt.preventDefault();
            _setActive(elItem);
            _select();
            htElement.elInput.focus();
        }

        function _onMouseOverMenu(weEvt){
            var elItem = weEvt.target.closest("li");
            if(elItem){
                _setActive(elItem);
            }
        }

        function _lookup(){
            htVar.sQuery = htElement.elInput.value;

            if(!htVar.sQuery || htVar.sQuery.length < htVar.nMinLength){
                if(htVar.bShown){
                    _hide();
                }
                return;
            }

            var vSource = htVar.vSource;
            if(typeof vSource === "function"){
                vSource(htVar.sQuery, _process);
            } else if(Array.isArray(vSource)){
                _process(vSource.slice());
            }
        }

        function _process(aItems){
            var sQueryLower = htVar.sQuery.toLowerCase();

            aItems = aItems.filter(function(vItem){
                return String(vItem).toLowerCase().indexOf(sQueryLower) > -1;
            });
            aItems = _sort(aItems);

            if(aItems.length === 0){
                if(htVar.bShown){
                    _hide();
                }
                return;
            }

            _render(aItems.slice(0, htVar.nItems));
            _show();
        }

        /**
         * 일치 항목을 "쿼리로 시작 > 대소문자 일치 포함 > 그 외" 순으로 정렬한다
         * (Bootstrap 원본 Typeahead.prototype.sorter와 동일한 규칙).
         */
        function _sort(aItems){
            var sQueryLower = htVar.sQuery.toLowerCase();
            var aBeginsWith = [], aCaseSensitive = [], aCaseInsensitive = [];

            aItems.forEach(function(vItem){
                var sItem = String(vItem);
                if(sItem.toLowerCase().indexOf(sQueryLower) === 0){
                    aBeginsWith.push(sItem);
                } else if(sItem.indexOf(htVar.sQuery) > -1){
                    aCaseSensitive.push(sItem);
                } else {
                    aCaseInsensitive.push(sItem);
                }
            });

            return aBeginsWith.concat(aCaseSensitive, aCaseInsensitive);
        }

        function _highlight(sItem){
            var sEscapedQuery = htVar.sQuery.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&");
            return sItem.replace(new RegExp("(" + sEscapedQuery + ")", "ig"), "<strong>$1</strong>");
        }

        function _render(aItems){
            htElement.elMenu.innerHTML = "";

            aItems.forEach(function(sItem, nIndex){
                var elLi = document.createElement("li");
                elLi.setAttribute("data-value", sItem);
                if(nIndex === 0){
                    elLi.classList.add("active");
                }

                var elLink = document.createElement("a");
                elLink.href = "#";
                elLink.innerHTML = _highlight(sItem);
                elLi.appendChild(elLink);

                htElement.elMenu.appendChild(elLi);
            });
        }

        function _next(){
            var elActive = htElement.elMenu.querySelector("li.active");
            var elNext = elActive ? elActive.nextElementSibling : null;

            if(elActive){
                elActive.classList.remove("active");
            }
            if(!elNext){
                elNext = htElement.elMenu.querySelector("li");
            }
            if(elNext){
                elNext.classList.add("active");
            }
        }

        function _prev(){
            var elActive = htElement.elMenu.querySelector("li.active");
            var elPrev = elActive ? elActive.previousElementSibling : null;

            if(elActive){
                elActive.classList.remove("active");
            }
            if(!elPrev){
                var aItems = htElement.elMenu.querySelectorAll("li");
                elPrev = aItems[aItems.length - 1];
            }
            if(elPrev){
                elPrev.classList.add("active");
            }
        }

        function _setActive(elItem){
            var elActive = htElement.elMenu.querySelector("li.active");
            if(elActive){
                elActive.classList.remove("active");
            }
            elItem.classList.add("active");
        }

        function _select(){
            var elActive = htElement.elMenu.querySelector("li.active");
            if(!elActive){
                _hide();
                return;
            }

            htElement.elInput.value = elActive.getAttribute("data-value");
            htElement.elInput.dispatchEvent(new Event("change", {"bubbles": true}));
            _hide();
        }

        function _show(){
            htElement.elMenu.style.top = (htElement.elInput.offsetTop + htElement.elInput.offsetHeight) + "px";
            htElement.elMenu.style.left = htElement.elInput.offsetLeft + "px";
            htElement.elMenu.style.display = "block";
            htVar.bShown = true;
        }

        function _hide(){
            htElement.elMenu.style.display = "none";
            htVar.bShown = false;
        }

        /**
        * Data source for loginId typeahead while adding new member.
        *
        * @param {String} sQuery
        * @param {Function} fProcess
        */
        function _onTypeAhead(sQuery, fProcess) {
            if (htVar.sLastQuery && sQuery.indexOf(htVar.sLastQuery) === 0 && htVar.bIsLastRangeEntire) {
                fProcess(htVar.htCachedUsers);
            } else {
                htVar.htData.query = sQuery;
                $yona.sendForm({
                    "sURL"        : htVar.sActionURL,
                    "htOptForm"    : {"method":"get"},
                    "htData"    : htVar.htData,
                    "sDataType" : "json",
                    "fOnLoad"    : function(oData, oStatus, oXHR){
                        var sContentRange = oXHR.getResponseHeader('Content-Range');

                        htVar.bIsLastRangeEntire = _isEntireRange(sContentRange);
                        htVar.sLastQuery = sQuery;
                        htVar.htCachedUsers = oData;

                        fProcess(oData);
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
             var aMatch = htVar.rxContentRange.exec(sContentRange || ""); // [1]=total, [2]=items
             return (aMatch) ? !(parseInt(aMatch[1], 10) < parseInt(aMatch[2], 10)) : true;
         }

        _init(vTarget, htOptions || {});
    };

})("yona.ui.Typeahead");
