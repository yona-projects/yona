/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
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
     * 초기화
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
     * 변수 초기화
     * initialize variables
     * @param {Hash Table} htOptions
     * @param {Function} htOptions.fOnCreate    라벨 추가 후 실행할 콜백 함수
     * @param {String}   htOptions.sURLPost     라벨 추가 AJAX URL
     * @param {String}   htOptions.sTplEditor   라벨 에디터 템플릿
     * @param {String}   htOptions.sTplBtnColor 라벨 기본 색상 버튼 템플릿
     * @param {Array}    htOptions.aColors      라벨 기본 색상코드 배열
     */
    function _initVar(htOptions){
        htVar.sURLPost = htOptions.sURLPost;
        htVar.fOnCreate = htOptions.fOnCreate || function(){};

        htVar.aColors = htOptions.aColors || ['#999999','#da5454','#f86ca0','#ff9e9d','#ff9933','#ffcc33','#f8c86c','#99ca3c','#22b4b9','#4d68b1','#6ca6f8','#3fb8af','#9966cc','#ffffff'];
        htVar.sTplEditor = htOptions.sTplEditor || '<div class="control-group label-editor">\
        <strong class="control-label">${labelNew}</strong>\
        <div id="custom-label" class="controls">\
            <div class="row-fluid">\
                <div>\
                    <input type="text" name="labelCategory" class="input-small labelInput" data-provider="typeahead" autocomplete="off" placeholder="${labelCategory}">\
                </div>\
                <div>\
                    <input type="text" name="labelName" class="input-small labelInput" placeholder="${labelName}" autocomplete="off">\
                </div>\
            </div>\
            <div class="colors"><input type="text" name="labelColor" class="input-small labelColor" placeholder="${labelCustomColor}"></div>\
            <div class="row-fluid">\
                <div class="span12"><button type="button" class="nbtn medium black labelSubmit">${labelAdd}</button></div>\
            </div>\
        </div></div>';
        htVar.sTplBtnColor = htOptions.sTplBtnColor || '<button type="button" class="issue-label issueColor nbtn small" style="background-color:${color}">';
    }

    /**
     * 엘리먼트 초기화
     * initialize elements
     * @param {Wrapped Element} welContainer 컨테이너 엘리먼트
     */
    function _initElement(welContainer){
        // htVar.sTplEditor 를 이용해 만들어진 라벨 에디터를 대상 영역에 붙이고
        htElement.welContainer = $(welContainer);
        htElement.welEditor = _getLabelEditor();
        htElement.welContainer.append(htElement.welEditor);

        // 세부 항목의 엘리먼트 레퍼런스 변수 설정
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
     * Enter 키가 눌렸을 경우 기본 이벤트 무시
     * @returns false when enter key pressed
     */
    function _preventDefaultWhenEnterPressed(eEvt) {
        return !(eEvt.keyCode === 13);
    }

    /**
     * Enter 키가 눌렸을 경우 기본 이벤트 무시하고 특정 엘리먼트로 포커스 이동
     * @private false when enter key pressed
     */
    function _preventSubmitAndMoveWhenEnterPressed(eEvt, welTarget){
        var code = eEvt.keyCode || eEvt.which;
        if (code === 13) {
            welTarget.focus();
            eEvt.preventDefault();
        }
    }

    /**
     * 이벤트 초기화
     * attach events
     */
    function _attachEvent(){
        htElement.waBtnCustomColor.click(_onClickBtnCustomColor);
        htElement.welBtnCustomLabelSubmit.click(_onClickBtnSubmitCustom);

        htElement.welCustomLabelCategory
            .keypress(_preventDefaultWhenEnterPressed);
        htElement.welCustomLabelName
            .keypress(_preventDefaultWhenEnterPressed)
            .keyup(function(e) {
                if ( e.keyCode === 13){
                    htElement.welCustomLabelColor.focus();
                    e.preventDefault();
                    return false;
                }
            });
        htElement.welCustomLabelColor
            .keypress(function(e) {
                if ( e.keyCode === 13 ){
                    _addCustomLabel();
                    e.preventDefault();
                    return false;
                }
            })
            .keyup(function(e){
                _preventSubmitAndMoveWhenEnterPressed(e, htElement.welCustomLabelName);
            });
        htElement.welCustomLabelColor.keyup(_onKeyupInputColorCustom);
    }

    /**
     * Get label Editor
     * 새 라벨 편집기 영역 엘리먼트를 생성해서 반환하는 함수
     * @return {Wrapped Element}
     */
    function _getLabelEditor(){
        // label editor HTML
        var welEditor = $.tmpl(htVar.sTplEditor, {
            "labelAdd"        : Messages("label.add"),
            "labelNew"        : Messages("label.new"),
            "labelName"        : Messages("label.name"),
            "labelCategory"    : Messages('label.category'),
            "labelCustomColor": Messages("label.customColor")
        });

        return welEditor;
    }

    function _makeColorTable(){
        var aColorBtns = [];
        htVar.aColors.forEach(function(sColor){
            aColorBtns.push($.tmpl(htVar.sTplBtnColor, {"color": sColor}));
        });
        htElement.welColors.prepend(aColorBtns);
        aColorBtns = null;
    }

    /**
     * 새 라벨 추가 버튼 클릭시 이벤트 핸들러
     */
    function _onClickBtnSubmitCustom(){
        _addCustomLabel();
        htElement.welCustomLabelName.focus();
    }

    /**
     * 새 라벨 추가
     * add custom label
     */
    function _addCustomLabel(){
        var htData = {
            "name"    : htElement.welCustomLabelName.val(),
            "color"   : htElement.welCustomLabelColor.val(),
            "category": htElement.welCustomLabelCategory.val()
        };

        // 하나라도 입력안된 것이 있으면 서버 요청 하지 않음
        if(htData.name.length === 0 || htData.color.length === 0 || htData.category.length === 0){
            $yobi.alert(Messages("label.error.empty"));
            return false;
        }

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

                htElement.welCustomLabelCategory.data("typeahead").source.push(oRes.category);
                htVar.fOnCreate(oRes);
            }
        });
    }

    /**
     * 새 라벨 색상 버튼 클릭시 이벤트 핸들러
     * @param {Event} eEvt
     */
    function _onClickBtnCustomColor(eEvt){
        var welTarget = $(eEvt.target || eEvt.srcElement || eEvt.originalTarget);

        // Set clicked button active.
        htElement.waBtnCustomColor.removeClass("active");
        welTarget.addClass("active");

        // Get the selected color.
        var sColor = welTarget.css('background-color');

        // Fill the color input area with the hexadecimal value of
        // the selected color.
        htElement.welCustomLabelColor.val(new RGBColor(sColor).toHex());
        htElement.welCustomLabelColor.css("border-color", sColor);
        _updateSelectedColor(sColor);
        //move caret to custom lable color input
        htElement.welCustomLabelColor.focus();
    }

    /**
     * 새 라벨 색상 코드 입력 <input>이 업데이트 될 때
     * 이름 입력 <input> 영역 배경색/글씨색 업데이트 하도록
     */
    function _onKeyupInputColorCustom(){
        var sColor = htElement.welCustomLabelColor.val();
        var oColor = new RGBColor(sColor);

        if (oColor.ok) {
            _updateSelectedColor(sColor);
            htElement.welCustomLabelColor.css("border-color", sColor);
        }

        oColor = null;
    }

    /**
     * updateSelectedLabel Color
     * 지정한 색으로 새 라벨 이름 영역의 배경색을 설정하고
     * 글자색을 배경색에 맞추어 업데이트 해주는 함수
     * @param {String} sBgColor 배경색
     */
    function _updateSelectedColor(sBgColor){
        // Change the name input area's color to the selected color.
        var sFgColor = $yobi.getContrastColor(sBgColor);
        htElement.welCustomLabelName.css({
            "color": sFgColor,
            "background-color": sBgColor
        });

        // Change also place holder's
        // TODO: 이 부분도 나중에 정리할 것. #custom-label-name 고정되어 있음
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

        aSelectors = elStyle = null;
    }

    /**
     * 카테고리 자동완성(typeahead) 소스에서 지정한 값을 제거함
     * @param {String} sCategory 제거할 카테고리 이름
     */
    function _removeCategoryTypeahead(sCategory){
        var aSource = htElement.welCustomLabelCategory.typeahead().data('typeahead').source;
        aSource.pop(aSource.indexOf(sCategory));
    }

    /**
     * 카테고리 자동완성(typeahead) 소스에서 지정한 값을 추가함
     * @param {String} sCategory 추가할 카테고리 이름
     */
    function _addCategoryTypeahead(sCategory) {
        var aSource = htElement.welCustomLabelCategory.typeahead().data('typeahead').source;
        aSource.push(sCategory);
    }

    // 인터페이스 반환
    return {
        "appendTo": _init,
        "removeCategory": _removeCategoryTypeahead,
        "addCategory": _addCategoryTypeahead
    }
})();
