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

            elements.list = $(options.list);
            elements.form = $(options.form);
            elements.formInput = elements.form.find('input,button[type=submit]');
            elements.inputCategory = elements.form.find('input[name="category"]');
            elements.inputName = elements.form.find('input[name="name"]');
            elements.inputColor = elements.form.find('input[name="color"]');
            elements.colorsWrap = elements.form.find("div.label-preset-colors");

            elements.editCategoryForm = $(options.editCategoryForm);
            elements.editCategoryName = elements.editCategoryForm.find("[name=name]");
            elements.editCategoryExclusive = elements.editCategoryForm.find("[name=isExclusive]");

            elements.editLabelForm = $(options.editLabelForm);
            elements.editLabelCategory = elements.editLabelForm.find('[name="category.id"]');
            elements.editLabelName = elements.editLabelForm.find("[name=name]");
            elements.editLabelColor = elements.editLabelForm.find("[name=color]");
            elements.editLabelColorsWrap = elements.editLabelForm.find("div.label-preset-colors");
        }

        /**
         * Initialize variables
         *
         * @param options
         * @private
         */
        function _initVar(options){
            vars.listURL = options.listURL;
            vars.actionURL = elements.form.prop("action");
            vars.categories = [];

            elements.inputCategory.typeahead();
            elements.inputCategory.data("typeahead").source = vars.categories;
        }

        /**
         * Attach event handlers
         *
         * @private
         */
        function _attachEvent(){
            // new label form
            elements.form.on("submit", _onSubmitForm);
            elements.form.on("click", ".btn-preset-color", _onClickBtnPresetColor);
            elements.inputCategory.on("keypress", _preventSubmitWhenEnterKeyPressed);
            elements.inputName.on("focus", _onFocusInputName);
            elements.inputColor.on({
                "keyup": _onKeyUpInputColor,
                "blur" : _onBlurInputColor
            });

            // label list
            elements.list.on("click", "[data-delete-uri]", _onClickBtnDeleteLabel);
            elements.list.on("click", "[data-update-uri]", _onClickBtnEditLabel);
            elements.list.on("click", "[data-category-update-uri]", _onClickBtnEditCategory);

            // edit category form
            elements.editCategoryForm.on("click", ".btnSubmit", _onClickBtnSubmitEditCategory);
            $yona.attachDialogDismiss(elements.editCategoryForm.get(0));

            // edit label form
            elements.editLabelForm.on("click", ".btnSubmit", _onClickBtnSubmitEditLabel);
            $yona.attachDialogDismiss(elements.editLabelForm.get(0));
            elements.editLabelForm.on("click", ".btn-preset-color", _onClickBtnPresetColorOnEditForm);
            elements.editLabelColor.on({
                "keyup": _onKeyUpEditColor,
                "blur" : _onBlurEditColor
            });
        }

        /**
         * Build category list array from HTML data.
         * for check is new category, and Typeahead source of inputCategory.
         *
         * @private
         */
        function _buildCategoryList(){
            $("div[data-category-name]").each(function(i, item){
                var categoryName = $(item).data("categoryName");

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
         * After Validate form before submit, send request via $.ajax
         *
         * @returns {boolean}
         * @private
         */
        function _onSubmitForm(){
            if(!_isFormValid()){
                return false;
            }

            var categoryName = $.trim(elements.inputCategory.val());

            // if categoryName is new, and no option determined,
            // show confirm before submit
            if(_isNewCategory(categoryName) && typeof vars.isNewCategoryExclusive === "undefined"){
                _submitFormAfterConfirmNewCategoryOption(categoryName);
                return false;
            }

            // send request to add label
            _requestAddLabel({
                "labelName"   : $.trim(elements.inputName.val()),
                "labelColor"  : _getRefinedHexColor($.trim(elements.inputColor.val())),
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
            if(elements.inputCategory.val().length === 0 ||
                elements.inputName.val().length === 0 ||
                elements.inputColor.val().length === 0){
                $yona.alert(Messages("label.failedTo", Messages("label.add")) + "\n" + Messages("label.error.empty"));
                return false;
            }

            if(_getRefinedHexColor(elements.inputColor.val()) === false){
                $yona.alert(Messages("label.failedTo", Messages("label.add")) + "\n" + Messages("label.error.color", elements.inputColor.val()));
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
                    elements.form.submit();
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
                    elements.inputName.val("").focus();
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
            return (_getCategoryElement(categoryName).find('[data-label-name="' + labelName + '"]').length > 0);
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
            var option = $('<option value="' + categoryId + '">' + categoryName + '</option>');
            elements.editLabelCategory.append(option);
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
            elements.colorsWrap.show();

            var categoryName = elements.inputCategory.val().trim();
            var labelColor = _isNewCategory(categoryName) ? _getRandomColorCodeInPreset()
                                                          : _getFirstItemColorInCategory(categoryName);

            if(elements.inputColor.val().length === 0){
                elements.inputColor.val(labelColor);
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
            var presetColors = elements.colorsWrap.find(".btn-preset-color");
            var randomIndex = (new Date().getTime()) % presetColors.length;
            var color = presetColors.get(randomIndex).style.backgroundColor;

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
            var targetItem = _getCategoryElement(categoryName).find(".issue-label:first");
            var color = targetItem.css("background-color");

            return _getRefinedHexColor(color);
        }

        /**
         * "click" event handler of preset color button
         *
         * @param evt
         * @private
         */
        function _onClickBtnPresetColor(evt){
            var targetButton = $(evt.target);
            var targetColor = _getRefinedHexColor(targetButton.css('background-color'));

            elements.inputColor.val(targetColor);
            elements.inputColor.focus();
            elements.colorsWrap.find(".btn-preset-color").removeClass("active");
            targetButton.addClass("active");

            _updateInputBySelectedColor(elements.inputName, elements.inputColor, targetColor);
        }

        /**
         * "blur" event handler of inputColor
         *
         * @private
         */
        function _onBlurInputColor(){
            var typedColor = elements.inputColor.val();

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
            if(!_isValidColorExpr(elements.inputColor.val())){
                return;
            }

            _updateInputBySelectedColor(elements.inputName,
                                        elements.inputColor,
                                        elements.inputColor.val());
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

            inputColor.css("cssText", boxShadowCSS);
            inputName.css("background-color", color);
            inputName.removeClass("dimgray white").addClass($yona.getContrastColor(color));
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
            var targetButton = $(target);

            fetch(targetButton.data("deleteUri"), {
                "method": "post",
                "body"  : new URLSearchParams({"_method": "delete"})
            })
            .then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                _removeLabel(targetButton.data("categoryName"), targetButton.data("labelId"));
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
            $('tr[data-label-id="' + labelId + '"]').remove();

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
            return (_getCategoryElement(categoryName).find("tr[data-label-id]").length === 0);
        }

        /**
         * Remove specified category from list
         *
         * @param categoryName
         * @private
         */
        function _removeCategory(categoryName){
            _getCategoryElement(categoryName).remove();
        }

        /**
         * Returns category wrapper jQuery HTMLElement
         * This function used when to remove category, determine the category is empty
         * or other DOM traversal/manipulations.
         *
         * @param categoryName
         * @returns {*|jQuery|HTMLElement}
         * @private
         */
        function _getCategoryElement(categoryName){
            return $('div.category-wrap[data-category-name="' + categoryName + '"]');
        }

        /**
         * "click" event handler of edit category button.
         * Shows modal dialog element.editCategoryForm with .data() of clicked button.
         *
         * @param evt
         * @private
         */
        function _onClickBtnEditCategory(evt){
            var target = $(evt.currentTarget);

            elements.editCategoryForm.data(target.data());
            elements.editCategoryName.val(target.data("categoryName"));
            // P3-46 #5 후속 버그 수정(2026-09-12): Select2 v3 API(.data("select2").val(...))가 Tom
            // Select 교체 후에도 남아있어 항상 undefined였다(TypeError로 크래시). element.tomselect +
            // setValue(value)로 교체.
            elements.editCategoryForm.find("[name=isExclusive]")[0].tomselect.setValue(target.data("categoryIsExclusive") + "");
            elements.editCategoryForm.get(0).showModal();
        }

        /**
         * "click" event handler of submit button on edit category form.
         * Send update category request to "categoryUpdateUri".
         *
         * @private
         */
        function _onClickBtnSubmitEditCategory(){
            var requestData = {
                "id"  : elements.editCategoryForm.data("categoryId"),
                "name": $.trim(elements.editCategoryName.val()),
                "isExclusive": elements.editCategoryExclusive.val(),
                "project.id" : elements.editCategoryForm.data("projectId")
            };

            NProgress.start();

            fetch(elements.editCategoryForm.data("categoryUpdateUri"), {
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
                elements.editCategoryForm.get(0).close();
                NProgress.done();
            });
        }

        /**
         * "click" event handler of edit label button.
         * Shows modal dialog element.editLabelForm with .data() of clicked button.
         *
         * @param evt
         * @private
         */
        function _onClickBtnEditLabel(evt){
            var target = $(evt.currentTarget);

            elements.editLabelForm.data(target.data());
            elements.editLabelName.val(target.data("labelName"));
            elements.editLabelColor.val(target.data("labelColor"));
            // P3-46 #5 후속 버그 수정(2026-09-12): 위 _onClickBtnEditCategory와 동일한 이유로
            // element.tomselect + setValue(value)로 교체.
            elements.editLabelCategory[0].tomselect.setValue(target.data("categoryId"));
            elements.editLabelForm.get(0).showModal();

            _updateInputBySelectedColor(elements.editLabelName,
                                        elements.editLabelColor,
                                        target.data("labelColor"));
        }

        /**
         * "click" event handler of submit button on edit label form.
         * Send update label request to "labelUpdateUri".
         *
         * @private
         */
        function _onClickBtnSubmitEditLabel(){
            var requestData = {
                "name" : $.trim(elements.editLabelName.val()),
                "color": $.trim(elements.editLabelColor.val()),
                "category.id": elements.editLabelCategory.val()
            };

            // Check is label with same name exists on new category
            // P3-46 #5 후속 버그 수정(2026-09-12): Select2 v3의 .data("select2").data()(현재 선택된
            // 데이터 객체 반환)를 Tom Select API로 교체 - 인스턴스의 options[현재 값]이 선택된
            // 옵션 객체(포맷 없는 select라 기본 렌더러의 {value, text} 형태)다.
            var editLabelCategoryTomSelect = elements.editLabelCategory[0].tomselect;
            var selectedCategoryOption = editLabelCategoryTomSelect.options[editLabelCategoryTomSelect.getValue()];
            var categoryName = selectedCategoryOption ? selectedCategoryOption.text : undefined;
            var initialLabelName = elements.editLabelForm.data("labelName");
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

            fetch(elements.editLabelForm.data("updateUri"), {
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
                elements.editLabelForm.get(0).close();
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
            var targetButton = $(evt.target);
            var targetColor = _getRefinedHexColor(targetButton.css('background-color'));

            elements.editLabelColor.focus();
            elements.editLabelColor.val(targetColor);
            elements.editLabelColorsWrap.find(".btn-preset-color").removeClass("active");
            targetButton.addClass("active");

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
            var refinedColor = _getRefinedHexColor(elements.editLabelColor.val());

            _updateInputBySelectedColor(elements.editLabelName,
                                        elements.editLabelColor,
                                        refinedColor);
        }

        function _onKeyUpEditColor(){
            if(!_isValidColorExpr(elements.editLabelColor.val())){
                return;
            }

            _updateInputBySelectedColor(elements.editLabelName,
                                        elements.editLabelColor,
                                        elements.editLabelColor.val());
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
            element.popover("destroy");
            element.popover({
                "placement": "bottom",
                "content"  : message
            }).popover("show");
        }

        _init(options || {});
    };

})("yona.issue.LabelEditor");
