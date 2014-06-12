/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jihan Kim
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
 * yobi.LabelEditor
 * 새 라벨 추가를 위한 에디터 인터페이스
 */
yobi.LabelEditor = (function(welContainer, htOptions){

    var htVar = {};
    var htElement = {};

    /**
     * initialize
     * @param {Wrapped Element} welContainer Container element to append label editor
     * @param {Hash Table} htOptions
     */
    function _init(welContainer, htOptions){
        _initVar(htOptions);
        _initElement(welContainer);
        _attachEvent();
    }

    /**
     * initialize variables
     * @param {Hash Table} htOptions
     * @param {Function} htOptions.fOnCreate
     * @param {String}   htOptions.sURLPost
     * @param {String}   htOptions.sTplEditor
     * @param {String}   htOptions.sTplBtnColor
     * @param {Array}    htOptions.aColors
     */
    function _initVar(htOptions){
        htVar.sURLPost = htOptions.sURLPost;
        htVar.fOnCreate = htOptions.fOnCreate;

        htVar.aColors = htOptions.aColors || ['#da5454','#f86ca0','#ff9e9d','#ffcc33','#f8c86c','#ff9933','#99ca3c','#3fb8af','#22b4b9','#6ca6f8','#4d68b1','#9966cc'];
        htVar.sTplEditor = htOptions.sTplEditor || $("#tplYobiLabelEditor").text();
        htVar.sTplBtnColor = htOptions.sTplBtnColor || '<button type="button" class="issue-label issueColor nbtn square" style="background-color:${color}">';
    }

    /**
     * initialize elements
     * @param {Wrapped Element} welContainer
     */
    function _initElement(welContainer){
        htElement.welContainer = $(welContainer);
        htElement.welEditor = _getLabelEditor();
        htElement.welContainer.append(htElement.welEditor);

        htElement.welWrap = $("#custom-label");
        htElement.welColors = htElement.welWrap.find("div.colors");
        _makeColorTable(); // 색상표 생성

        htElement.waBtnCustomColor = htElement.welWrap.find("button.issueColor");
        htElement.welCustomLabelColor = htElement.welWrap.find("input[name=labelColor]"); // $('#custom-label-color');
        htElement.welCustomLabelName =  htElement.welWrap.find("input[name=labelName]");  // $('#custom-label-name');
        htElement.welCustomLabelCategory = htElement.welWrap.find("input[name=labelCategory]"); // $('#custom-label-category');
        htElement.welCustomLabelCategory.typeahead();
        htElement.welBtnCustomLabelSubmit  = htElement.welWrap.find("button.labelSubmit"); //$('#custom-label-submit');

        // Focus to the category input area.
        htElement.welCustomLabelCategory.focus();
    }

    /**
     * @returns {Boolean} false on enter key has pressed
     */
    function _preventDefaultWhenEnterPressed(weEvt) {
        return !((weEvt.keyCode || weEvt.which) === 13);
    }

    /**
     * @private false when enter key pressed
     */
    function _preventSubmitAndMoveWhenEnterPressed(eEvt, welTarget){
        var code = eEvt.keyCode || eEvt.which;
        if(code === 13){
            welTarget.focus();
            eEvt.preventDefault();
        }
    }

    /**
     * attach events
     */
    function _attachEvent(){
        htElement.waBtnCustomColor.click(_onClickBtnCustomColor);
        htElement.welBtnCustomLabelSubmit.click(_onClickBtnSubmitCustom);

        htElement.welCustomLabelCategory
            .keypress(_preventDefaultWhenEnterPressed);
        htElement.welCustomLabelName
            .focus(_onFocusLabelName)
            .keypress(_preventDefaultWhenEnterPressed)
            .keyup(function(weEvt) {
                if((weEvt.keyCode || weEvt.which) === 13){
                    htElement.welCustomLabelColor.focus();
                    weEvt.preventDefault();
                    return false;
                }
            });
        htElement.welCustomLabelColor
            .keypress(function(weEvt){
                if((weEvt.keyCode || weEvt.which) === 13){
                    _addCustomLabel();
                    weEvt.preventDefault();
                    return false;
                }
            })
            .keyup(function(weEvt){
                _preventSubmitAndMoveWhenEnterPressed(weEvt, htElement.welCustomLabelName);
            });
        htElement.welCustomLabelColor.keyup(_onKeyupInputColorCustom);
    }

    function _onFocusLabelName(){
        var sCategory = htElement.welCustomLabelCategory.val();
        var sColor = htElement.welCustomLabelColor.val();

        if(!sCategory.trim()){
            return;
        }

        var welFirstItemInCategory = $('.label-group[data-category="' + sCategory + '"] > .issue-label:first');

        if(welFirstItemInCategory.length > 0 && !sColor.trim()){
            var sColor = new RGBColor(welFirstItemInCategory.css("background-color")).toHex();
            _updateSelectedColor(sColor);
            htElement.welCustomLabelColor.val(sColor);
        }
    }

    /**
     * Get label Editor
     * @return {Wrapped Element}
     */
    function _getLabelEditor(){
        // label editor HTML
        var welEditor = $yobi.tmpl(htVar.sTplEditor, {
            "labelAdd"        : Messages("label.add"),
            "labelNew"        : Messages("label.new"),
            "labelName"       : Messages("label.name"),
            "labelCategory"   : Messages('label.category'),
            "labelCustomColor": Messages("label.customColor")
        });

        return welEditor;
    }

    function _makeColorTable(){
        var aColorBtns = [];
        htVar.aColors.forEach(function(sColor){
            aColorBtns.push($yobi.tmpl(htVar.sTplBtnColor, {"color": sColor}));
        });
        htElement.welColors.prepend(aColorBtns);
        aColorBtns = null;
    }

    function _onClickBtnSubmitCustom(){
        _addCustomLabel();
        htElement.welCustomLabelName.focus();
    }

    /**
     * add custom label
     */
    function _addCustomLabel(){
        var htData = {
            "name"    : htElement.welCustomLabelName.val(),
            "color"   : htElement.welCustomLabelColor.val(),
            "category": htElement.welCustomLabelCategory.val()
        };

        if(htData.name.length === 0 || htData.color.length === 0 || htData.category.length === 0){
            $yobi.alert(Messages("label.error.empty"));
            return false;
        }

        var sColor = htElement.welCustomLabelColor.val();
        var oColor = new RGBColor(sColor);

        if(!oColor.ok){
            $yobi.alert(Messages("label.error.color", sColor));
            return false;
        }

        htElement.welCustomLabelColor.val(oColor.toHex());

        // send request
        $yobi.sendForm({
            "sURL"     : htVar.sURLPost,
            "htData"   : htData,
            "htOptForm": {"enctype": "multipart/form-data"},
            "fOnLoad"  : function(oRes){
                // label.id, label.category, label.name, label.color
                if (!(oRes instanceof Object)) {
                    var sMessage = Messages("label.error.creationFailed");

                    if($(".labels .issue-label:contains('" + htData.name + "')").length > 0){
                        sMessage = Messages("label.error.duplicated");
                    }

                    $yobi.alert(sMessage);
                    return;
                }

                htElement.welCustomLabelName.val("");
                _addCategoryTypeahead(oRes.category);

                if(typeof htVar.fOnCreate === "function"){
                    htVar.fOnCreate(oRes);
                }
            }
        });
    }

    /**
     * @param {Event} eEvt
     */
    function _onClickBtnCustomColor(eEvt){
        var welTarget = $(eEvt.target || eEvt.srcElement || eEvt.originalTarget);

        // Set clicked button active.
        htElement.waBtnCustomColor.removeClass("active");
        welTarget.addClass("active");

        // Get the selected color.
        var sColor = new RGBColor(welTarget.css('background-color')).toHex();

        // Fill the color input area with the hexadecimal value of
        // the selected color.
        _updateSelectedColor(sColor);

        //move caret to custom label color input
        htElement.welCustomLabelColor.val(sColor);
        htElement.welCustomLabelColor.focus();
    }

    function _onKeyupInputColorCustom(){
        var sColor = htElement.welCustomLabelColor.val();
        var oColor = new RGBColor(sColor);

        if(oColor.ok){
            _updateSelectedColor(oColor.toHex());
        }

        oColor = null;
    }

    /**
     * updateSelectedLabel Color
     *
     * @param {String} sBgColor
     */
    function _updateSelectedColor(sBgColor){
        // Change the name input area's color to the selected color.
        var sFgColor = $yobi.getContrastColor(sBgColor);
        htElement.welCustomLabelName.css({
            "color": sFgColor,
            "background-color": sBgColor,
            "border-color": sBgColor
        });

        // Change also place holder's
        var aSelectors = ['#custom-label input[name=labelName]:-moz-placeholder',
                         '#custom-label input[name=labelName]:-ms-input-placeholder',
                         '#custom-label input[name=labelName]::-webkit-input-placeholder'];

        var elStyle = document.styleSheets[0];
        var sStyleColor = 'color:' + sFgColor + ' !important';
        var sStyleOpacity = 'opacity: 0.8';

        aSelectors.forEach(function(sSelector){
            try {
                elStyle.addRule(sSelector, sStyleColor);
                elStyle.addRule(sSelector, sStyleOpacity);
            } catch (e){ }
        });

        htElement.welCustomLabelColor.css("border-color", sBgColor);
        aSelectors = elStyle = null;
    }

    /**
     * @param {String} sCategory
     */
    function _removeCategoryTypeahead(sCategory){
        var aSource = htElement.welCustomLabelCategory.typeahead().data('typeahead').source;
        aSource.pop(aSource.indexOf(sCategory));
    }

    /**
     * @param {String} sCategory
     */
    function _addCategoryTypeahead(sCategory) {
        var aSource = htElement.welCustomLabelCategory.typeahead().data('typeahead').source;

        if(aSource.indexOf(sCategory) === -1){
            aSource.push(sCategory);
        }
    }

    return {
        "appendTo": _init,
        "removeCategory": _removeCategoryTypeahead,
        "addCategory": _addCategoryTypeahead
    }
})();
