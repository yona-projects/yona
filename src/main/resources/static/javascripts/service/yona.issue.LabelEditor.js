/**
 * Yona, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(options){

        var vars = {};
        var elements = {};

        /**
         * jQuery `.data()` getter가 dataset 문자열 값을 "true"/"false"→boolean,
         * "null"→null, 순수 숫자 문자열→Number, "{"/"["로 시작·끝나는 값→JSON.parse로
         * 자동 변환해 돌려주는 quirk(jQuery.fn.data 내부 getData)를 그대로 재현한다.
         * 이 값이 나중에 `!=` 비교(_onClickBtnSubmitEditLabel의 isLabelNameChanged)나
         * 배열 indexOf 비교(_buildCategoryList)에 쓰이므로, 네이티브 dataset의 raw
         * 문자열을 그냥 쓰면 라벨/카테고리 이름이 우연히 "true"/"123" 등인 극단적
         * 케이스에서 결과가 달라질 수 있다.
         *
         * @private
         */
        function _coerceDataValue(value){
            if(value === undefined){
                return undefined;
            }
            if(value === "true"){
                return true;
            }
            if(value === "false"){
                return false;
            }
            if(value === "null"){
                return null;
            }
            if(value !== "" && value === String(+value)){
                return +value;
            }
            if(/^(?:\{[\s\S]*\}|\[[\s\S]*\])$/.test(value)){
                try{
                    return JSON.parse(value);
                }catch(e){
                    // JSON 파싱 실패 시 원본 문자열 유지 (jQuery도 동일하게 실패시 원본 반환)
                }
            }
            return value;
        }

        /**
         * @private
         */
        function _getData(el, key){
            return el ? _coerceDataValue(el.dataset[key]) : undefined;
        }

        /**
         * 원본 jQuery `elements.form.submit()`(인자 없음)은 사실 `.trigger("submit")`과
         * 같다 - "submit" 이벤트를 실제로 발생시켜 같은 폼에 바인딩된 _onSubmitForm을
         * 재호출하고(_submitFormAfterConfirmNewCategoryOption의 재귀 재제출 설계가 여기
         * 의존한다 - vars.isNewCategoryExclusive가 이번엔 정의돼 있어 두 번째 진입에서
         * _requestAddLabel로 빠진다), 아무도 preventDefault를 안 부르면 네이티브
         * elem.submit()으로 폴백한다. 네이티브 form.submit()을 직접 부르면 _onSubmitForm을
         * 건너뛰고 categoryIsExclusive 등 AJAX 전용 필드 없이 그대로 서버에 POST돼
         * 400(잘못된 요청)이 난다 - 동일한 "이벤트 발생 우선" 흐름을 재현해야 한다.
         *
         * @private
         */
        function _triggerFormSubmit(elForm){
            var weEvt = new Event("submit", {"bubbles": true, "cancelable": true});
            var bNotPrevented = elForm.dispatchEvent(weEvt);
            if(bNotPrevented){
                elForm.submit();
            }
        }

        /**
         * Initialize
         *
         * @param options
         * @private
         */
        function _init(options){
            _initElement(options);
            _initVar(options);
            _attachEvent();
            _buildCategoryList();
        }

        /**
         * Initialize element variables
         *
         * @param options
         * @private
         */
        function _initElement(options){

            elements.list = document.querySelector(options.list);
            elements.form = document.querySelector(options.form);
            elements.formInput = elements.form.querySelectorAll('input,button[type=submit]');
            elements.inputCategory = elements.form.querySelector('input[name="category"]');
            elements.inputName = elements.form.querySelector('input[name="name"]');
            elements.inputColor = elements.form.querySelector('input[name="color"]');
            elements.colorsWrap = elements.form.querySelector("div.label-preset-colors");

            elements.editCategoryForm = document.querySelector(options.editCategoryForm);
            elements.editCategoryName = elements.editCategoryForm.querySelector("[name=name]");
            elements.editCategoryExclusive = elements.editCategoryForm.querySelector("[name=isExclusive]");

            elements.editLabelForm = document.querySelector(options.editLabelForm);
            elements.editLabelCategory = elements.editLabelForm.querySelector('[name="category.id"]');
            elements.editLabelName = elements.editLabelForm.querySelector("[name=name]");
            elements.editLabelColor = elements.editLabelForm.querySelector("[name=color]");
            elements.editLabelColorsWrap = elements.editLabelForm.querySelector("div.label-preset-colors");
        }

        /**
         * Initialize variables
         *
         * @param options
         * @private
         */
        function _initVar(options){
            vars.listURL = options.listURL;
            vars.actionURL = elements.form.action;
            vars.categories = [];

            // vars.categories는 이후 _buildCategoryList/_addLabelIntoCategory에서
            // push로 채워지는데, source에 이 배열 레퍼런스를 그대로 넘겨두면
            // yona.ui.Typeahead가 매 입력마다 최신 상태를 다시 읽어 별도 갱신 없이
            // 반영된다. yona.ui.Typeahead.js는 이미 완전한 vanilla 구현이라
            // Element를 그대로 받는다.
            new yona.ui.Typeahead(elements.inputCategory, {
                "htData": {"source": vars.categories}
            });
        }

        /**
         * Attach event handlers
         *
         * @private
         */
        function _attachEvent(){
            // new label form
            // "submit" 핸들러가 항상 false를 반환해 제출을 막는 원본은 jQuery
            // `.on()`이 return false를 preventDefault+stopPropagation으로 바꿔주는
            // 관례에 기대고 있었다 - 네이티브 addEventListener는 핸들러의 반환값을
            // 무시하므로, evt.preventDefault()를 명시적으로 호출해야 실제 폼 전송
            // (페이지 이동)이 일어나지 않는다.
            elements.form.addEventListener("submit", _onSubmitForm);
            elements.form.addEventListener("click", function(e){
                var matched = e.target.closest(".btn-preset-color");
                if(matched && elements.form.contains(matched)){
                    _onClickBtnPresetColor(e);
                }
            });
            elements.inputCategory.addEventListener("keypress", _preventSubmitWhenEnterKeyPressed);
            elements.inputName.addEventListener("focus", _onFocusInputName);
            elements.inputColor.addEventListener("keyup", _onKeyUpInputColor);
            elements.inputColor.addEventListener("blur", _onBlurInputColor);

            // label list (위임 바인딩 - jQuery `.on(evt, selector, fn)`과 동일하게
            // closest() + contains() 가드로 재현)
            elements.list.addEventListener("click", function(e){
                var matched = e.target.closest("[data-delete-uri]");
                if(matched && elements.list.contains(matched)){
                    _onClickBtnDeleteLabel(e);
                }
            });
            elements.list.addEventListener("click", function(e){
                // _onClickBtnEditLabel은 evt.currentTarget을 읽는데, jQuery
                // 위임에서는 evt.currentTarget이 "매치된 위임 대상 요소"가 되지만
                // 네이티브 이벤트의 currentTarget은 항상 리스너를 붙인 컨테이너
                // 자신이라 값이 다르다 - matched를 this로 bind해 그 자리를
                // 대신하도록 핸들러 내부를 함께 수정했다.
                var matched = e.target.closest("[data-update-uri]");
                if(matched && elements.list.contains(matched)){
                    _onClickBtnEditLabel.call(matched, e);
                }
            });
            elements.list.addEventListener("click", function(e){
                var matched = e.target.closest("[data-category-update-uri]");
                if(matched && elements.list.contains(matched)){
                    _onClickBtnEditCategory.call(matched, e);
                }
            });

            // edit category form
            elements.editCategoryForm.addEventListener("click", function(e){
                var matched = e.target.closest(".btnSubmit");
                if(matched && elements.editCategoryForm.contains(matched)){
                    _onClickBtnSubmitEditCategory();
                }
            });
            $yona.attachDialogDismiss(elements.editCategoryForm);

            // edit label form
            elements.editLabelForm.addEventListener("click", function(e){
                var matched = e.target.closest(".btnSubmit");
                if(matched && elements.editLabelForm.contains(matched)){
                    _onClickBtnSubmitEditLabel();
                }
            });
            $yona.attachDialogDismiss(elements.editLabelForm);
            elements.editLabelForm.addEventListener("click", function(e){
                var matched = e.target.closest(".btn-preset-color");
                if(matched && elements.editLabelForm.contains(matched)){
                    _onClickBtnPresetColorOnEditForm(e);
                }
            });
            elements.editLabelColor.addEventListener("keyup", _onKeyUpEditColor);
            elements.editLabelColor.addEventListener("blur", _onBlurEditColor);
        }

        /**
         * Build category list array from HTML data.
         * for check is new category, and Typeahead source of inputCategory.
         *
         * @private
         */
        function _buildCategoryList(){
            document.querySelectorAll("div[data-category-name]").forEach(function(item){
                var categoryName = _getData(item, "categoryName");

                if(vars.categories.indexOf(categoryName) < 0){
                    vars.categories.push(categoryName);
                }
            });
        }

        /**
         * false when enter key pressed
         * @private
         */
        function _preventSubmitWhenEnterKeyPressed(evt){
            if(_isEnterKeyPressed(evt)){
                evt.preventDefault();
                return false;
            }
        }

        /**
         * Returns whether the pressed key is ENTER
         * from given Event.
         *
         * @param evt
         * @returns {boolean}
         * @private
         */
        function _isEnterKeyPressed(evt){
            return ((evt.keyCode || evt.which) === 13);
        }

        /**
         * "submit" event handler of form
         * After Validate form before submit, send request via fetch
         *
         * @returns {boolean}
         * @private
         */
        function _onSubmitForm(evt){
            if(evt){
                evt.preventDefault();
            }

            if(!_isFormValid()){
                return false;
            }

            var categoryName = elements.inputCategory.value.trim();

            // if categoryName is new, and no option determined,
            // show confirm before submit
            if(_isNewCategory(categoryName) && typeof vars.isNewCategoryExclusive === "undefined"){
                _submitFormAfterConfirmNewCategoryOption(categoryName);
                return false;
            }

            // send request to add label
            _requestAddLabel({
                "labelName"   : elements.inputName.value.trim(),
                "labelColor"  : _getRefinedHexColor(elements.inputColor.value.trim()),
                "categoryName": categoryName,
                "categoryIsExclusive": vars.isNewCategoryExclusive
            });

            // remove category option after each request
            delete vars.isNewCategoryExclusive;
            return false;
        }

        /**
         * Returns whether is form valid
         * and shows error if invalid.
         *
         * @returns {boolean}
         * @private
         */
        function _isFormValid(){
            if(elements.inputCategory.value.length === 0 ||
                elements.inputName.value.length === 0 ||
                elements.inputColor.value.length === 0){
                $yona.alert(Messages("label.failedTo", Messages("label.add")) + "\n" + Messages("label.error.empty"));
                return false;
            }

            if(_getRefinedHexColor(elements.inputColor.value) === false){
                $yona.alert(Messages("label.failedTo", Messages("label.add")) + "\n" + Messages("label.error.color", elements.inputColor.value));
                return false;
            }

            return true;
        }

        /**
         * Returns whether is specified {@code categoryName} exists
         *
         * @param categoryName
         * @returns {boolean}
         * @private
         */
        function _isNewCategory(categoryName){
            return (vars.categories.indexOf(categoryName) < 0);
        }

        /**
         * Submit the form after user choose option for create new category.
         *
         * @param categoryName
         * @private
         */
        function _submitFormAfterConfirmNewCategoryOption(categoryName){
            $yona.confirm(Messages("label.category.new.confirm", categoryName), function(evt){
                    vars.isNewCategoryExclusive = (evt.nButtonIndex === 1);
                    _triggerFormSubmit(elements.form);
                }, "", {
                    "aButtonStyles":["confirm-button-vertical", "confirm-button-vertical"],
                    "aButtonLabels":[Messages("label.category.option.multiple"), Messages("label.category.option.single")]
                }
            );
        }

        /**
         * jQuery.param()과 동일하게 값이 undefined/null이어도 키는 유지하고 빈 문자열로
         * 직렬화한다(예: categoryIsExclusive가 미확정이면 "categoryIsExclusive=") -
         * URLSearchParams에 requestData 객체를 직접 넘기면 undefined가 문자열
         * "undefined"로 잘못 직렬화되는 문제를 막는다.
         */
        function _toRequestParams(requestData){
            var params = new URLSearchParams();
            Object.keys(requestData).forEach(function(key){
                var value = requestData[key];
                params.append(key, value == null ? "" : value);
            });
            return params;
        }

        function _showError(res, messageKey){
            if(res.responseText){
                try{
                    var error = JSON.parse(res.responseText);
                    var errorText = Messages("label.failedTo", Messages(messageKey));

                    for(var key in error){
                        errorText += "\n" + error[key];
                    }

                    $yona.alert(errorText);
                }catch(e){
                    $yona.alert(Messages("error.failedTo", Messages(messageKey), res.status, res.statusText));
                }
            }else{
                $yona.alert(Messages("error.failedTo", Messages(messageKey), res.status, res.statusText));
            }
        }

        /**
         * Send request to add label with given data
         * called from _onSubmitForm.
         *
         * @param requestData
         * @private
         */
        function _requestAddLabel(requestData){
            if(_isLabelExists(requestData.categoryName, requestData.labelName)){
                $yona.alert(Messages("label.error.duplicated"));
                return false;
            }

            fetch(vars.actionURL, {
                "method": "post",
                "body"  : _toRequestParams(requestData)
            })
            .then(function(response){
                if(!response.ok){
                    return response.text().then(function(text){
                        return Promise.reject({"status": response.status, "statusText": response.statusText, "responseText": text});
                    });
                }
                return response.json().catch(function(){ return null; });
            })
            .then(function(res){
                if (res instanceof Object && res !== null){
                    _addLabelIntoCategory(res);
                    elements.inputName.value = "";
                    elements.inputName.focus();
                    return;
                }

                $yona.alert(Messages("label.error.creationFailed"));
            })
            .catch(function(res){
                _showError(res, "label.add");
            });
        }

        /**
         * Returns whether the label exists which is specified category and name.
         *
         * @param categoryName
         * @param labelName
         * @returns {boolean}
         * @private
         */
        function _isLabelExists(categoryName, labelName){
            var categoryEl = _getCategoryElement(categoryName);
            return !!(categoryEl && categoryEl.querySelector('[data-label-name="' + labelName + '"]'));
        }

        /**
         * Add specified label into category.
         * append label name to typeahead source,
         * and render list which the label has added.
         *
         * @param label
         * @private
         */
        function _addLabelIntoCategory(label){
            if(_isNewCategory(label.category)){
                vars.categories.push(label.category);
                _addCategoryIntoEditFormSelect(label.categoryId, label.category);
            }

            _reloadLabelList();
        }

        /**
         * Add new category option into select on edit label form
         *
         * @param label
         * @private
         */
        function _addCategoryIntoEditFormSelect(categoryId, categoryName){
            // 원본 jQuery `$('<option value="...">...</option>')`은 categoryName을
            // escape 없이 HTML로 파싱한다 - 이스케이프를 새로 도입하지 않고 그대로
            // 동치 재현(P3-70 라운드2 site.MassMail.js와 동일한 판단).
            var wrapper = document.createElement("div");
            wrapper.innerHTML = '<option value="' + categoryId + '">' + categoryName + '</option>';
            var option = wrapper.firstElementChild;
            elements.editLabelCategory.appendChild(option);
        }

        /**
         * Refresh element.list as PJAX style after add label.
         *
         * @private
         */
        function _reloadLabelList(){
            document.location.reload(true);
        }

        /**
         * "focus" event handler of inputName
         *
         * Shows preset color buttons, and fills inputColor with color code if it is empty.
         * Random color if entered category name is not exists before,
         * or color of first item in category if the category exists.
         *
         * @private
         */
        function _onFocusInputName(){
            // .label-preset-colors는 CSS에서 display:none이 기본값인 <div>라
            // jQuery .show()와 동치로 style.display = "block"을 직접 지정한다
            // (P3-70 라운드1/2와 동일한 관례).
            elements.colorsWrap.style.display = "block";

            var categoryName = elements.inputCategory.value.trim();
            var labelColor = _isNewCategory(categoryName) ? _getRandomColorCodeInPreset()
                                                          : _getFirstItemColorInCategory(categoryName);

            if(elements.inputColor.value.length === 0){
                elements.inputColor.value = labelColor;
                _updateInputBySelectedColor(elements.inputName, elements.inputColor, labelColor);
            }
        }

        /**
         * Returns random color code in preset colors.
         *
         * @private
         * @returns {String}
         */
        function _getRandomColorCodeInPreset(){
            var presetColors = elements.colorsWrap.querySelectorAll(".btn-preset-color");
            var randomIndex = (new Date().getTime()) % presetColors.length;
            var color = presetColors[randomIndex].style.backgroundColor;

            return _getRefinedHexColor(color);
        }

        /**
         * Returns background-color of first .issue-label in specified {@code categoryName}
         *
         * @param categoryName
         * @returns {String}
         * @private
         */
        function _getFirstItemColorInCategory(categoryName){
            var categoryEl = _getCategoryElement(categoryName);
            var targetItem = categoryEl ? categoryEl.querySelector(".issue-label") : null;
            var color = targetItem ? getComputedStyle(targetItem).backgroundColor : undefined;

            return _getRefinedHexColor(color);
        }

        /**
         * "click" event handler of preset color button
         *
         * @param evt
         * @private
         */
        function _onClickBtnPresetColor(evt){
            var targetButton = evt.target;
            var targetColor = _getRefinedHexColor(getComputedStyle(targetButton).backgroundColor);

            elements.inputColor.value = targetColor;
            elements.inputColor.focus();
            elements.colorsWrap.querySelectorAll(".btn-preset-color").forEach(function(el){
                el.classList.remove("active");
            });
            targetButton.classList.add("active");

            _updateInputBySelectedColor(elements.inputName, elements.inputColor, targetColor);
        }

        /**
         * "blur" event handler of inputColor
         *
         * @private
         */
        function _onBlurInputColor(){
            var typedColor = elements.inputColor.value;

            if(typedColor.length < 1){
                return;
            }

            if(!_isValidColorExpr(typedColor)){
                $yona.alert(Messages("label.error.color", typedColor), function(){
                    elements.inputColor.focus();
                });
                return;
            }

            _updateInputBySelectedColor(elements.inputName,
                                        elements.inputColor,
                                        _getRefinedHexColor(typedColor));
        }

        function _onKeyUpInputColor(){
            if(!_isValidColorExpr(elements.inputColor.value)){
                return;
            }

            _updateInputBySelectedColor(elements.inputName,
                                        elements.inputColor,
                                        elements.inputColor.value);
        }

        /**
         * Get color code in HEX.
         * Returns false if given color expression is cannot be covered by RGBColor.
         *
         * @require lib/rgbcolor.js
         * @param color
         * @returns {*}
         * @private
         */
        function _getRefinedHexColor(color){
            var rgb = new RGBColor(color || "");
            return rgb && rgb.ok ? rgb.toHex() : false;
        }

        /**
         * Update inputColor and inputName style with given color expression
         *
         * @require common/yona.Common.js
         * @param color
         * @private
         */
        function _updateInputBySelectedColor(inputName, inputColor, color){
            if(!color){
                return;
            }

            var boxShadowCSS = _getPrefixedCSSText("box-shadow: inset 25px 0 0 " + color + " !important");

            inputColor.style.cssText = boxShadowCSS;
            inputName.style.backgroundColor = color;
            inputName.classList.remove("dimgray", "white");
            inputName.classList.add($yona.getContrastColor(color));
        }

        /**
         * Returns CSS Text prefixed with -moz-, -webkit-.
         *
         * @param cssText
         * @returns {string}
         * @private
         * @example
         *
         * _getPrefixedCSSText('text-shadow: 1px 1px 0 #000');
         *
         * // Returns string below:
         * // text-shadow: 1px 1px 0 #000; -moz-text-shadow: 1px 1px 0 #000; -webkit-text-shadow: 1px 1px 0 #000;
         */
        function _getPrefixedCSSText(cssText){
            var result = [];
            var prefixes = ["", "-moz-", "-webkit-"];

            prefixes.forEach(function(prefix){
                result.push(prefix + cssText);
            });

            return result.join(";");
        }

        /**
         * "click" event handler of label delete button
         * Show confirm to delete and send request to remove label.
         *
         * @param evt
         * @private
         */
        function _onClickBtnDeleteLabel(evt){
            $yona.confirm(Messages("label.confirm.delete"), function(data){
                if(data.nButtonIndex === 1){
                    _requestRemoveLabel(evt.target);
                }
            });
        }

        /**
         * Send AJAX request to remove label with specified delete button
         *
         * @param target
         * @private
         */
        function _requestRemoveLabel(target){
            var deleteUri = _getData(target, "deleteUri");

            fetch(deleteUri, {
                "method": "post",
                "body"  : new URLSearchParams({"_method": "delete"})
            })
            .then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                _removeLabel(_getData(target, "categoryName"), _getData(target, "labelId"));
            });
        }

        /**
         * Remove specified label from list
         *
         * @param categoryName
         * @param labelId
         * @private
         */
        function _removeLabel(categoryName, labelId){
            var labelRow = document.querySelector('tr[data-label-id="' + labelId + '"]');
            if(labelRow){
                labelRow.remove();
            }

            if(_isEmptyCategory(categoryName)){
                _removeCategory(categoryName);
            }
        }

        /**
         * Returns whether given category is empty in list.
         *
         * @param categoryName
         * @returns {boolean}
         * @private
         */
        function _isEmptyCategory(categoryName){
            var categoryEl = _getCategoryElement(categoryName);
            return !categoryEl || categoryEl.querySelectorAll("tr[data-label-id]").length === 0;
        }

        /**
         * Remove specified category from list
         *
         * @param categoryName
         * @private
         */
        function _removeCategory(categoryName){
            var categoryEl = _getCategoryElement(categoryName);
            if(categoryEl){
                categoryEl.remove();
            }
        }

        /**
         * Returns category wrapper HTMLElement (or null if not found)
         * This function used when to remove category, determine the category is empty
         * or other DOM traversal/manipulations.
         *
         * @param categoryName
         * @returns {?HTMLElement}
         * @private
         */
        function _getCategoryElement(categoryName){
            return document.querySelector('div.category-wrap[data-category-name="' + categoryName + '"]');
        }

        /**
         * "click" event handler of edit category button.
         * Shows modal dialog element.editCategoryForm with data of clicked button.
         *
         * @param evt
         * @private
         */
        function _onClickBtnEditCategory(evt){
            // 위임 바인딩 쪽에서 .call(matched, e)로 호출되므로 this가 jQuery
            // 위임의 evt.currentTarget과 동일한 매치 대상 요소다.
            var target = this;

            // 원본 jQuery는 target.data()로 버튼의 data-* 전부를 한 번에
            // editCategoryForm의 내부 데이터 캐시로 옮겨뒀다가 나중에
            // (_onClickBtnSubmitEditCategory에서) 다시 읽는다 - 이 모듈
            // 안에서만 쓰는 사적 상태라 커스텀 expando로 대체했다
            // (project/partial_issuelabels_list.html의 실제 버튼 마크업 기준
            // 5개 키만 존재함을 확인).
            elements.editCategoryForm.__labelData = {
                projectId: _getData(target, "projectId"),
                categoryId: _getData(target, "categoryId"),
                categoryName: _getData(target, "categoryName"),
                categoryIsExclusive: _getData(target, "categoryIsExclusive"),
                categoryUpdateUri: _getData(target, "categoryUpdateUri")
            };

            elements.editCategoryName.value = elements.editCategoryForm.__labelData.categoryName;
            // P3-46 #5 후속 버그 수정(2026-09-12): Select2 v3 API(.data("select2").val(...))가 Tom
            // Select 교체 후에도 남아있어 항상 undefined였다(TypeError로 크래시). element.tomselect +
            // setValue(value)로 교체.
            elements.editCategoryExclusive.tomselect.setValue(elements.editCategoryForm.__labelData.categoryIsExclusive + "");
            elements.editCategoryForm.showModal();
        }

        /**
         * "click" event handler of submit button on edit category form.
         * Send update category request to "categoryUpdateUri".
         *
         * @private
         */
        function _onClickBtnSubmitEditCategory(){
            var cachedData = elements.editCategoryForm.__labelData || {};
            var requestData = {
                "id"  : cachedData.categoryId,
                "name": elements.editCategoryName.value.trim(),
                "isExclusive": elements.editCategoryExclusive.value,
                "project.id" : cachedData.projectId
            };

            NProgress.start();

            fetch(cachedData.categoryUpdateUri, {
                "method": "put",
                "body"  : _toRequestParams(requestData)
            }).then(function(response){
                if(!response.ok){
                    return response.text().then(function(text){
                        return Promise.reject({"status": response.status, "statusText": response.statusText, "responseText": text});
                    });
                }
                _reloadLabelList();
            }).catch(function(res){
                _showError(res, "label.category.edit");
            }).finally(function(){
                elements.editCategoryForm.close();
                NProgress.done();
            });
        }

        /**
         * "click" event handler of edit label button.
         * Shows modal dialog element.editLabelForm with data of clicked button.
         *
         * @param evt
         * @private
         */
        function _onClickBtnEditLabel(evt){
            // 위 _onClickBtnEditCategory와 동일한 이유로 this = 위임 매치 요소.
            var target = this;

            elements.editLabelForm.__labelData = {
                categoryId: _getData(target, "categoryId"),
                labelName: _getData(target, "labelName"),
                labelColor: _getData(target, "labelColor"),
                updateUri: _getData(target, "updateUri")
            };

            elements.editLabelName.value = elements.editLabelForm.__labelData.labelName;
            elements.editLabelColor.value = elements.editLabelForm.__labelData.labelColor;
            // P3-46 #5 후속 버그 수정(2026-09-12): 위 _onClickBtnEditCategory와 동일한 이유로
            // element.tomselect + setValue(value)로 교체.
            elements.editLabelCategory.tomselect.setValue(elements.editLabelForm.__labelData.categoryId);
            elements.editLabelForm.showModal();

            _updateInputBySelectedColor(elements.editLabelName,
                                        elements.editLabelColor,
                                        elements.editLabelForm.__labelData.labelColor);
        }

        /**
         * "click" event handler of submit button on edit label form.
         * Send update label request to "labelUpdateUri".
         *
         * @private
         */
        function _onClickBtnSubmitEditLabel(){
            var requestData = {
                "name" : elements.editLabelName.value.trim(),
                "color": elements.editLabelColor.value.trim(),
                "category.id": elements.editLabelCategory.value
            };

            // Check is label with same name exists on new category
            // P3-46 #5 후속 버그 수정(2026-09-12): Select2 v3의 .data("select2").data()(현재 선택된
            // 데이터 객체 반환)를 Tom Select API로 교체 - 인스턴스의 options[현재 값]이 선택된
            // 옵션 객체(포맷 없는 select라 기본 렌더러의 {value, text} 형태)다.
            var editLabelCategoryTomSelect = elements.editLabelCategory.tomselect;
            var selectedCategoryOption = editLabelCategoryTomSelect.options[editLabelCategoryTomSelect.getValue()];
            var categoryName = selectedCategoryOption ? selectedCategoryOption.text : undefined;
            var cachedData = elements.editLabelForm.__labelData || {};
            var initialLabelName = cachedData.labelName;
            var isLabelNameChanged = (requestData.name != initialLabelName);

            if(isLabelNameChanged && _isLabelExists(categoryName, requestData.name)){
                _popoverMessageOn(Messages("label.error.duplicated.in.category", categoryName), elements.editLabelName);
                return false;
            }

            // Check is entered color valid
            if(!_isValidColorExpr(requestData.color)){
                _popoverMessageOn(Messages("label.error.color", requestData.color), elements.editLabelColor);
                return false;
            }

            NProgress.start();

            fetch(cachedData.updateUri, {
                "method": "put",
                "body"  : _toRequestParams(requestData)
            }).then(function(response){
                if(!response.ok){
                    return response.text().then(function(text){
                        return Promise.reject({"status": response.status, "statusText": response.statusText, "responseText": text});
                    });
                }
                _reloadLabelList();
            }).catch(function(res){
                _showError(res, "label.edit");
            }).finally(function(){
                elements.editLabelForm.close();
                NProgress.done();
            });
        }

        /**
         * "click" event handler of preset color buttons on editLabel form.
         *
         * @param evt
         * @private
         */
        function _onClickBtnPresetColorOnEditForm(evt){
            var targetButton = evt.target;
            var targetColor = _getRefinedHexColor(getComputedStyle(targetButton).backgroundColor);

            elements.editLabelColor.focus();
            elements.editLabelColor.value = targetColor;
            elements.editLabelColorsWrap.querySelectorAll(".btn-preset-color").forEach(function(el){
                el.classList.remove("active");
            });
            targetButton.classList.add("active");

            _updateInputBySelectedColor(elements.editLabelName,
                                        elements.editLabelColor,
                                        targetColor);
        }

        /**
         * "blur" event handler of color input on editLabel form.
         *
         * @private
         */
        function _onBlurEditColor(){
            var refinedColor = _getRefinedHexColor(elements.editLabelColor.value);

            _updateInputBySelectedColor(elements.editLabelName,
                                        elements.editLabelColor,
                                        refinedColor);
        }

        function _onKeyUpEditColor(){
            if(!_isValidColorExpr(elements.editLabelColor.value)){
                return;
            }

            _updateInputBySelectedColor(elements.editLabelName,
                                        elements.editLabelColor,
                                        elements.editLabelColor.value);
        }

        /**
         * Returns whether given color expression is valid
         *
         * @param colorExpr
         * @returns {boolean}
         * @private
         */
        function _isValidColorExpr(colorExpr){
            // As RGBColor.js is too generous to validate HEX color expression,
            // Check length of colorExpr if it starts with '#' which means HEX.
            if(colorExpr.indexOf('#') === 0 &&
                !(colorExpr.length === 4 || colorExpr.length === 7)){
                return false;
            }

            var rgb = new RGBColor(colorExpr);
            return (rgb && rgb.ok);
        }

        /**
         * Show {@code message} bottom of {@code element}
         *
         * @param message
         * @param element
         * @private
         */
        function _popoverMessageOn(message, element){
            $yona.showPopoverError(element, message, "bottom");
        }

        _init(options || {});
    };

})("yona.issue.LabelEditor");
