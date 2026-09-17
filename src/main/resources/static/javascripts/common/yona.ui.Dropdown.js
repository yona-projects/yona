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
 * var oSelect = new yona.Dropdown({
 *     "elContainer": ".btn-group",
 *     "fOnChange"  : function(){},
 *     "
 * });
 *
 * Bootstrap dropdown 플러그인에 의존하지 않는 순수 커스텀 구현이다 - 실제 코드에는
 * .dropdown() 호출이 전혀 없고 li 클릭/active 클래스 토글만으로 직접 동작한다.
 */
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {"sValue":""};
        var htElement = {};

        // elContainer는 호출부에 따라 raw DOM 엘리먼트(layout.html), 문자열 셀렉터
        // (yona.issue.Write.js), jQuery 객체(yona.issue.MassUpdate.js 경유)로 제각각
        // 넘어오므로 셋 다 raw DOM 엘리먼트로 정규화한다.
        function _toElement(el){
            if(!el){
                return null;
            }
            if(typeof el === "string"){
                return document.querySelector(el);
            }
            if(el.jquery){
                return el[0];
            }
            return el;
        }

         function _init(htOptions){
            _initElement(htOptions);
            _attachEvent();

            if(htOptions.fOnChange){
                _setOnChange(htOptions.fOnChange);
            }

            _selectDefault();
        }

         function _initElement(htOptions){
            htElement.welContainer = _toElement(htOptions.elContainer);
            // elContainer가 th:if 등으로 렌더링되지 않은 화면(예: 마일스톤이 없는 프로젝트의
            // #milestone 드롭다운)에서는 jQuery 셀렉터가 빈 컬렉션이 되어 welContainer가 null이
            // 된다 - 원본 jQuery는 빈 컬렉션에 대한 조작이 조용히 no-op이었으므로 동일하게 처리한다.
            if(!htElement.welContainer){
                return;
            }

            // 컨테이너가 <yona-dropdown>(태그명으로 판별)이면 그 위에 노출된
            // getValue/onChange/selectByValue/selectItem으로 전부 위임한다. 아직 마이그레이션
            // 안 된 화면(평범한 div.btn-group)은 원본 vanilla 구현이 처리하며, 두 경로 다
            // 동일한 공개 계약을 반환하므로 호출부는 코드를 바꿀 필요가 없다.
            if(htElement.welContainer.tagName.toLowerCase() === "yona-dropdown"){
                htVar.bIsVueDropdown = true;
                return;
            }

            htElement.welSelectedLabel = htElement.welContainer.querySelector(".d-label");
            htElement.welList = htElement.welContainer.querySelector(".dropdown-menu");
            htElement.waItems = htElement.welList.querySelectorAll("li");
        }

        function _attachEvent(){
            if(!htElement.welContainer || htVar.bIsVueDropdown){
                return;
            }
            htElement.welList.addEventListener("click", function(weEvt){
                var match = weEvt.target.closest("li");
                if(match && htElement.welList.contains(match)){
                    _onClickItem(weEvt, match);
                }
            });
            htElement.welList.addEventListener("mousewheel", _onScrollList);
        }

        /**
         * @param weEvt
         * @returns {boolean}
         * @private
         */
        function _onScrollList(weEvt){
            if((weEvt.deltaY > 0 && _isScrollEndOfList()) ||
               (weEvt.deltaY < 0 && _isScrollTopOfList())){
                weEvt.preventDefault();
                weEvt.stopPropagation();
                return false;
            }
        }

        /**
         * @returns {boolean}
         * @private
         */
        function _isScrollTopOfList(){
            return (htElement.welList.scrollTop === 0);
        }

        /**
         * @returns {boolean}
         * @private
         */
        function _isScrollEndOfList(){
            return (htElement.welList.scrollTop + htElement.welList.clientHeight === htElement.welList.scrollHeight);
        }

        /**
         * @param {Event} weEvt
         * @param {Element} welTarget <li> item that was clicked (or its ancestor li)
         */
        function _onClickItem(weEvt, welTarget){
            // ignore click event if item doesn't have data-value attribute
            if(!welTarget || welTarget.getAttribute("data-value") === null){
                weEvt.stopPropagation();
                weEvt.preventDefault();
                return false;
            }

            _setItemSelected(welTarget); // display
            _setFormValue(welTarget);    // set form value
            _onChange(); // fireEvent
        }

        /**
         * @param {Element} welTarget
         */
        function _setItemSelected(welTarget){
            htElement.welSelectedLabel.innerHTML = welTarget.innerHTML;
            htElement.waItems.forEach(function(item){ item.classList.remove("active"); });
            welTarget.classList.add("active");
        }

        /**
         * @param {Element} welTarget
         */
        function _setFormValue(welTarget){
            var sFieldValue = welTarget.getAttribute("data-value");
            var sFieldName  = htElement.welContainer.getAttribute("data-name");
            htVar.sName     = sFieldName;
            htVar.sValue    = sFieldValue;

            if(sFieldName === null){
                return;
            }

            var welInput = htElement.welContainer.querySelector("input[name='" + sFieldName +"']");

            if(!welInput){
                welInput = document.createElement("input");
                welInput.type = "hidden";
                welInput.name = sFieldName;
                htElement.welContainer.appendChild(welInput);
            }

            welInput.value = sFieldValue;
        }

        function _onChange(){
            if(typeof htVar.fOnChange == "function"){
                setTimeout(function(){
                    htVar.fOnChange(_getValue());
                }, 0);
            }
        }

        /**
         * @param {Function} fOnChange
         */
        function _setOnChange(fOnChange){
            if(htVar.bIsVueDropdown){
                return htElement.welContainer.onChange(fOnChange);
            }
            htVar.fOnChange = fOnChange;
            return true;
        }

        /**
         * @return {String}
         */
        function _getValue(){
            if(htVar.bIsVueDropdown){
                return htElement.welContainer.getValue();
            }
            return htVar.sValue;
        }

        function _selectDefault(){
            if(htVar.bIsVueDropdown){
                return true; // Vue 컴포넌트 자신의 onMounted가 이미 처리한다.
            }
            return _selectItem("li[data-selected=true]");
        }

        /**
         * @param {String} sValue
         */
        function _selectByValue(sValue){
            if(htVar.bIsVueDropdown){
                return htElement.welContainer.selectByValue(sValue);
            }
            return _selectItem("li[data-value='" + sValue + "']");
        }

        /**
         * @param {String} sQuery
         */
        function _selectItem(sQuery){
            if(!htElement.welContainer){
                return false;
            }
            if(htVar.bIsVueDropdown){
                return htElement.welContainer.selectItem(sQuery);
            }
            var waFind = htElement.welContainer.querySelectorAll(sQuery);
            if(waFind.length <= 0){
                return false; // no item matches
            }

            var welTarget = waFind[0];
            _setItemSelected(welTarget);
            _setFormValue(welTarget);

            return true;
        }

        _init(htOptions);

        return {
            "getValue": _getValue,
            "onChange": _setOnChange,
            "selectByValue": _selectByValue,
            "selectItem"   : _selectItem
        };
    };
})("yona.ui.Dropdown");
