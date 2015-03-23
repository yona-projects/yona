/**
 * Yobi, Project Hosting SW
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

    var oNS = $yobi.createNamespace(ns);
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

            // edit label form
            elements.editLabelForm.on("click", ".btnSubmit", _onClickBtnSubmitEditLabel);
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
                $yobi.alert(Messages("label.failed") + "\n" + Messages("label.error.empty"));
                return false;
            }

            if(_getRefinedHexColor(elements.inputColor.val()) === false){
                $yobi.alert(Messages("label.failed") + "\n" + Messages("label.error.color", elements.inputColor.val()));
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
            $yobi.confirm(Messages("label.category.new.confirm", categoryName), function(evt){
                    vars.isNewCategoryExclusive = (evt.nButtonIndex === 1);
                    elements.form.submit();
                }, "", {
                    "aButtonStyles":["confirm-button-vertical", "confirm-button-vertical"],
                    "aButtonLabels":[Messages("label.category.option.multiple"), Messages("label.category.option.single")]
                }
            );
        }

        function _showError(res, messageKey){
            if(res.responseText){
                try{
                    var error = JSON.parse(res.responseText);
                    var errorText = Messages("label.failedTo", Messages(messageKey));

                    for(var key in error){
                        errorText += "\n" + error[key];
                    }

                    $yobi.alert(errorText);
                }catch(e){
                    $yobi.alert(Messages("error.failedTo", Messages(messageKey), res.status, res.statusText));
                }
            }else{
                $yobi.alert(Messages("error.failedTo", Messages(messageKey), res.status, res.statusText));
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
                $yobi.alert(Messages("label.error.duplicated"));
                return false;
            }

            $.ajax(vars.actionURL, {
                "method": "post",
                "data"  : requestData
            })
            .done(function(res){
                if (res instanceof Object){
                    _addLabelIntoCategory(res);
                    elements.inputName.val("").focus();
                    return;
                }

                $yobi.alert(Messages("label.error.creationFailed"));
            })
            .fail(function(res){
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
                $yobi.alert(Messages("label.error.color", typedColor), function(){
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
         * @require common/yobi.Common.js
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
            inputName.removeClass("dimgray white").addClass($yobi.getContrastColor(color));
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
            $yobi.confirm(Messages("label.confirm.delete"), function(data){
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

            $.ajax(targetButton.data("deleteUri"), {
                "method": "post",
                "data"  : {"_method": "delete"}
            })
            .done(function(){
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
            elements.editCategoryForm.find("[name=isExclusive]").data("select2").val(target.data("categoryIsExclusive") + "");
            elements.editCategoryForm.modal("show");
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

            $.ajax(elements.editCategoryForm.data("categoryUpdateUri"), {
                "method": "put",
                "data"  : requestData
            }).done(function(){
                _reloadLabelList();
            }).fail(function(res){
                _showError(res, "label.category.edit");
            }).always(function(){
                elements.editCategoryForm.modal("hide");
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
            elements.editLabelCategory.data("select2").val(target.data("categoryId"));
            elements.editLabelForm.modal("show");

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
            var categoryName = elements.editLabelCategory.data("select2").data().text;
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

            $.ajax(elements.editLabelForm.data("updateUri"), {
                "method": "put",
                "data"  : requestData
            }).done(function(){
                _reloadLabelList();
            }).fail(function(res){
                _showError(res, "label.edit");
            }).always(function(){
                elements.editLabelForm.modal("hide");
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

})("yobi.issue.LabelEditor");
