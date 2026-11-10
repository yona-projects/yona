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
 * @example
 * var oSelect = new yona.Dropdown({
 *     "elContainer": ".btn-group",
 *     "fOnChange"  : function(){},
 *     "
 * });
 *
 * @require bootstrap-dropdown.js
 */
(function(ns){

    const oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        const htVar = {"sValue":""};
        const htElement = {};

         function _init(htOptions){
            _initElement(htOptions);
            _attachEvent();

            htVar.fOnChange = htOptions.fOnChange;

            _selectDefault();
        }

         function _initElement(htOptions){
            htElement.welContainer = $(htOptions.elContainer);
            htElement.welSelectedLabel = htElement.welContainer.find(".d-label");
            htElement.welList = htElement.welContainer.find(".dropdown-menu");
            htElement.waItems = htElement.welList.find("li");
        }

        function _attachEvent(){
            htElement.welList.on("click", "li", _onClickItem);
        }


        /**
         * @param {Wrapped Event} weEvt
         */
        function _onClickItem(weEvt){
            // set welTarget to <li> item
            const welCurrent = $(weEvt.target);
            const welTarget = (weEvt.target.tagName === "LI") ? welCurrent : $(welCurrent.parents("li")[0]);

            // ignore click event if item doesn't have data-value attribute
            if(welTarget.length === 0 || typeof welTarget.attr("data-value") === "undefined"){
                weEvt.stopPropagation();
                weEvt.preventDefault();
                return false;
            }

            _setItemSelected(welTarget); // display
            _setFormValue(welTarget);    // set form value
            _onChange(); // fireEvent
        }

        /**
         * @param {Wrapped Element} welTarget
         */
        function _setItemSelected(welTarget){
            htElement.welSelectedLabel.html(welTarget.html());
            // 메뉴 항목의 `dropdown-item`(Bootstrap 4)은 메뉴 안의 모양이므로 단추 라벨에는 남기지 않는다.
            htElement.welSelectedLabel.find(".dropdown-item").removeClass("dropdown-item");
            htElement.waItems.removeClass("active");
            welTarget.addClass("active");
        }

        /**
         * @param {Wrapped Element} welTarget
         */
        function _setFormValue(welTarget){
            const sFieldValue = welTarget.attr("data-value");
            const sFieldName  = htElement.welContainer.attr("data-name");
            htVar.sName     = sFieldName;
            htVar.sValue    = sFieldValue;

            if(typeof sFieldName === "undefined"){
                return;
            }

            let welInput = htElement.welContainer.find("input[name='" + sFieldName +"']");

            if(welInput.length === 0){
                welInput = $('<input type="hidden" name="' + sFieldName + '">');
                htElement.welContainer.append(welInput);
            }

            welInput.val(sFieldValue);
        }

        function _onChange(){
            if(typeof htVar.fOnChange == "function"){
                setTimeout(() =>{
                    htVar.fOnChange(_getValue());
                }, 0);
            }
        }

        /**
         * @param {Function} fOnChange
         */
        function _setOnChange(fOnChange){
            htVar.fOnChange = fOnChange;
            return true;
        }

        /**
         * @return {String}
         */
        function _getValue(){
            return htVar.sValue;
        }

        function _selectDefault(){
            return _selectItem("li[data-selected=true]");
        }

        /**
         * @param {String} sValue
         */
        function _selectByValue(sValue){
            return _selectItem("li[data-value='" + sValue + "']");
        }

        /**
         * @param {String} sQuery
         */
        function _selectItem(sQuery){
            const waFind = htElement.welContainer.find(sQuery);
            if(waFind.length <= 0){
                return false; // no item matches
            }

            const welTarget = $(waFind[0]);
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
