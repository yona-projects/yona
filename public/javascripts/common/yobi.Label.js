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
 * yobi.Label
 * 라벨 목록을 가져다 표현해주는 역할
 * (개별 삭제 링크 처리 포함)
 */
yobi.Label = (function(htOptions){

    var htVar = {};
    var htElement = {};

    /**
     * 초기화
     * initialize
     * @param {Hash Table} htOptions
     * @param {Boolean} htOptions.bEditable     라벨 편집기 활성화 여부 (default: false)
     * @param {String} htOptions.sTplLabel      라벨 카테고리 그룹 템플릿
     * @param {String} htOptions.sTplControls   라벨 그룹 템플릿
     * @param {String} htOptions.sTplBtnLabelId 라벨 항목 템플릿
     * @param {String} htOptions.sURLLabels     라벨 목록을 반환하는 AJAX URL
     * @param {String} htOptions.sURLPost       새 라벨 작성을 위한 AJAX URL
     */
    function _init(htOptions){
        htOptions = $.extend({"bEditable": false}, htOptions);
        _initVar(htOptions);
        _initElement(htOptions);
        _initLabelEditor();
        _attachEvent();
    }

    /**
     *  변수 초기화
     *  initialize variable except element
     *    htVar.sURLLabels = htOptions.sURLLabels;
     *    htVar.sURLPost = htOptions.sURLPost;
     *    htVar.bEditable = htOptions.bEditable;
     *  @param {Hash Table} htOptions 초기화 옵션
     */
    function _initVar(htOptions){
        // copy htOptions to htVar
        for(key in htOptions){
            htVar[key] = htOptions[key];
        }

        htVar.sURLLabel = htVar.sURLLabels.substr(0, htVar.sURLLabels.length-1);

        htVar.sTplLabel = htOptions.sTplLabel || '<div class="control-group"><label class="control-label" data-category="${category}">${category}</label></div>';
        htVar.sTplControls = htOptions.sTplControls || '<div class="controls label-group" data-category="${category}"></div>';
        htVar.sTplBtnLabelId = htOptions.sTplBtnLabelId || '<span class="issue-label ${labelCSS} ${activeClass}" data-labelId="${labelId}">${labelName}${deleteButton}</span>';
        htVar.sTplLabelItem = $('#labelListItem').text();
        htVar.sTplLabelCategoryItem = $('#labelCatetoryItem').text();
    }

    /**
     * 엘리먼트 초기화
     * initialize element variable
     */
    function _initElement(htOptions){
        htElement.welContainer  = $(htOptions.welContainer || "fieldset.labels");
        htElement.welForm = $(htOptions.welForm || 'form#issue-form,form.form-search,form#search');

        // add label
        htElement.welLabels = $('.labels');
        htElement.welLabelEditor = $('.label-editor');

        htElement.welBtnManageLabel = $(htOptions.welBtnManageLabel || "#manage-label-link");

        // setting label list
        htElement.welAttachLabels = $('#attach-label-list');
        htElement.welDeleteLabels = $('#delete-label-list');
    }

    /**
     * 이벤트 핸들러 설정
     * initialize event handler
     */
    function _attachEvent(){
        htElement.welForm.submit(_onSubmitForm);
        htElement.welBtnManageLabel.click(_clickBtnManageLabel);
    }

    /**
     * 폼 전송시 이벤트 핸들러
     * 라벨 선택도 반영되도록 자동으로 필드를 추가한다
     */
    function _onSubmitForm(){
        // append labelIds to searchForm
        if(htVar.bEditable === false){
            _appendSelectedLabelIdsToForm();
        }

        return true;
    }

    /**
     * 현재 선택되어 있는 라벨들의 ID를 검색폼에 추가한다
     * @private
     */
    function _appendSelectedLabelIdsToForm(){
        // clear former fields first
        _clearLabelIdsOnForm();

        var aValues = [];
        var waSelectedLabels = $("fieldset.labels div[data-category] span.active[data-labelId]");

        waSelectedLabels.each(function(i, elLabel){
            aValues.push('<input type="hidden" name="labelIds" value="'+ $(elLabel).attr('data-labelId') + '">');
        });

        htElement.welForm.append(aValues);
        waSelectedLabels = null;
    }

    /**
     * 기존에 검색폼 영역에 추가되어 있던 labelIds input을 제거한다
     * @private
     */
    function _clearLabelIdsOnForm(){
        $("input[name='labelIds']").each(function(i, elInput) {
            $(elInput).remove();
        });
    }

    /**
     * 라벨 에디터 컨트롤
     */
    function _clickBtnManageLabel() {
        // 편집모드로 진입하는 경우
        if(htVar.bEditable === false){
            _appendSelectedLabelIdsToForm();
        }

        htVar.bEditable = !htVar.bEditable;
        _initLabelEditor();
    }

    /**
     * 라벨 편집기 초기화
     * initialize Label Editor
     */
    function _initLabelEditor(){
        htElement.welContainer.empty();

        if(htVar.bEditable){
            yobi.LabelEditor.appendTo(htElement.welContainer, {
                "sURLPost" : htVar.sURLPost,
                "fOnCreate": _onCreateNewLabel
            });
        }

        _getLabels(htVar.fOnLoad);
    }

    /**
     * 라벨 에디터가 새 라벨 생성후 호출하는 함수
     * @param {Object} oLabel
     */
    function _onCreateNewLabel(oLabel){
        _addLabelIntoCategory(oLabel);
        _setActiveLabel(oLabel.id, oLabel.color);
        _addLabelForSettingGroup(oLabel);
        $('input[name="labelName"]').val("");
    }

    function _addLabelForSettingGroup(oLabel) {
        var sLabel;
        var welAttachDivider = htElement.welAttachLabels.find('li[data-category="'+oLabel.category+'"].divider');
        var welDeleteDivider = htElement.welDeleteLabels.find('li[data-category="'+oLabel.category+'"].divider');

        if(welAttachDivider.length === 0) {
            sLabel = $yobi.tmpl(htVar.sTplLabelCategoryItem, oLabel);
            htElement.welAttachLabels.prepend(sLabel);
            htElement.welDeleteLabels.prepend(sLabel);
        } else {
            sLabel = $yobi.tmpl(htVar.sTplLabelItem, oLabel);
            welAttachDivider.before(sLabel);
            welDeleteDivider.before(sLabel);
        }
    }

    /**
     * getLabels
     * 라벨 목록을 서버로부터 수신하여 목록 생성
     * 분류에 따라 라벨 목록을 만드는 것은 _addLabelIntoCategory 에서 수행
     * @param {Function} fCallback 완료 후 수행할 콜백 함수
     */
    function _getLabels(fCallback){
        // send request
        $.get(htVar.sURLLabels, function(oRes){
            if(!(oRes instanceof Object)){
                return;
            }

            // add label into category after sort
            var aLabels = oRes.sort(function(a, b) {
                return (a.category == b.category) ? (a.name > b.name) : (a.category > b.category);
            });
            $(aLabels).each(function(nIndex, oLabel){
                _addLabelIntoCategory(oLabel);
            });

            // run callback function
            if (typeof fCallback == "function") {
                fCallback(this);
            }

            aLabels = null;
        });
    }

    /**
     * add label into category
     * 새 라벨을 지정한 분류에 추가.
     * 서버 통신용 함수는 아니고 화면에 표시하기 위한 목적.
     * @param {Number} oLabel.id 라벨 id
     * @param {String} oLabel.category 라벨 분류
     * @param {String} oLabel.name 라벨 이름
     * @param {String} oLabel.color 라벨 색상
     * @return {Wrapped Element} 추가된 라벨 버튼 엘리먼트
     */
    function _addLabelIntoCategory(oLabel) {
        // set Label Color
        _setLabelColor(oLabel);

        // label Id
        var welBtnLabelId = $($yobi.tmpl(htVar.sTplBtnLabelId, {
            "labelId"     : oLabel.id,
            "labelName"   : oLabel.name,
            "labelCSS"    : 'active-' + $yobi.getContrastColor(oLabel.color),
            "activeClass" : _getActiveClass(parseInt(oLabel.id)),
            "deleteButton": htVar.bEditable ? '<span class="delete">&times;</span>' : ''
        }));

        // 편집모드: 라벨 버튼을 항상 active 상태로 유지
        if(htVar.bEditable){
            welBtnLabelId.addClass('active');
        }
        welBtnLabelId.click(_onClickLabel);

        // 이미 같은 카테고리가 있으면 거기에 넣고
        var welCategory = $('fieldset.labels div[data-category="' + oLabel.category + '"]');
        if (welCategory.length > 0) {
            welCategory.append(welBtnLabelId);
            return welBtnLabelId;
        }

        // 없으면 새 카테고리 줄을 추가한다
        var welLabel = $.tmpl(htVar.sTplLabel, {"category": oLabel.category});
        var welControls = $.tmpl(htVar.sTplControls, {"category": oLabel.category});
        welControls.append(welBtnLabelId); // Edit Button
        welLabel.append(welControls); // Controls

        if(htVar.bEditable){
            yobi.LabelEditor.addCategory(oLabel.category);
        }

        // add label into category
        if(htElement.welLabelEditor.length > 0) {
            htElement.welLabelEditor.before(welLabel);
        } else {
            htElement.welLabels.prepend(welLabel);
        }

        return welBtnLabelId;
    }

    /**
     * 라벨 엘리먼트가 활성화 되었을때 (= active 클래스가 주어졌을때)
     * 적용할 배경색/글자색 CSS Rule 추가하는 함수
     * @param {Object} oLabel
     */
    function _setLabelColor(oLabel){
        var sDefaultCSSTarget = '.issue-label[data-labelId="' + oLabel.id + '"]';
        var sActiveCSSTarget = '.issue-label.active[data-labelId="' + oLabel.id + '"]';

        var aDefaultCss = [];
        var sDefaultCssSkel = 'box-shadow: inset 3px 0 0px ' + oLabel.color;
        ["", "-moz-", "-webkit"].forEach(function(sPrefix){
            aDefaultCss.push(sPrefix + sDefaultCssSkel);
        });
        var sDefaultCss = aDefaultCss.join(";");
        var sActiveCss = 'background-color: ' + oLabel.color + '; color:'+$yobi.getContrastColor(oLabel.color);

        if(document.styleSheets[0].addRule) {
            document.styleSheets[0].addRule(sActiveCSSTarget,sActiveCss);
            document.styleSheets[0].addRule(sDefaultCSSTarget,sDefaultCss);
        } else {
            document.styleSheets[0].insertRule(sActiveCSSTarget+'{'+ sActiveCss +'}',0);
            document.styleSheets[0].insertRule(sDefaultCSSTarget+'{'+ sDefaultCss +'}',0);
        }
    }

    /**
     * 라벨 선택 여부에 따라 적절한 class 를 반환한다.
     * @param {Number} nLabelId
     */
    function _getActiveClass(nLabelId) {
        if (htVar.aSelectedLabels && htVar.aSelectedLabels.indexOf(nLabelId) != -1) {
            return 'active';
        }
        return '';
    }

    /**
     * 라벨 엘리먼트를 클릭했을때 이벤트 핸들러
     *
     * @param {Wrapped Event} weEvt
     * @return {Boolean} false
     */
    function _onClickLabel(weEvt){
        var welCurrent = $(weEvt.target); // SPAN .delete or .issue-label
        var welLabel = welCurrent.attr("data-labelId") ? welCurrent : welCurrent.parent("[data-labelId]");
        var sLabelId = welLabel.attr("data-labelId");

        // 편집모드이고, 삭제버튼을 클릭한 경우라면
        if(htVar.bEditable && welCurrent.hasClass("delete")){
            // 정말 삭제하겠냐고 물어보고
            if(confirm(Messages("label.confirm.delete")) === false){
                return false;
            }

            return _requestDeleteLabel(sLabelId);
        }

        // 편집모드가 아닐때 클릭했다면 해당 라벨을
        // 토글하고, 같은 카테고리의 다른 모든 라벨은
        // 선택상태를 해제한다.
        if(!htVar.bEditable){
            welLabel.siblings().removeClass("active");
            welLabel.toggleClass("active");
        }

        // 선택한 라벨로 이슈 검색
        // 선택한 값을 검색쿼리에 추가하는 작업은 _onSubmitForm 에서 수행된다
        if (htVar.bRefresh) {
            htElement.welForm.submit();
        }

        return false;
    }


    /**
     * 서버에 라벨 삭제 요청 전송
     * request to delete label
     * @param sLabelId
     * @private
     */
    function _requestDeleteLabel(sLabelId){
        if(!sLabelId){
            return false;
        }

        $.post(
            htVar.sURLLabel + '/' + sLabelId + '/delete',
            {"_method": "delete"}
        ).done(function(){
            _removeLabel(sLabelId);
        });

        return true;
    }

    /**
     * 라벨 목록에서 라벨 삭제
     * remove label on list
     * @param {String} sLabelId
     */
    function _removeLabel(sLabelId) {
        var welEditorLabel = $('.issue-form-labels').find('[data-labelId=' + sLabelId + ']');
        var welSettingLabel = htElement.welAttachLabels.find('li[data-value="'+sLabelId+'"]');

        if(welEditorLabel.siblings().length ===0) {
            var sCategory = welSettingLabel.data('category');
            welEditorLabel.parents('div.control-group').remove();
            htElement.welAttachLabels.find('li[data-category="'+sCategory+'"]').remove();
            yobi.LabelEditor.removeCategory(sCategory);
        } else {
            welEditorLabel.remove();
            welSettingLabel.remove();
        }
    }

    /**
     * 지정한 라벨을 선택한 상태로 만들어주는 함수
     * @param {String} sId
     */
    function _setActiveLabel(sId){
        // 색상 지정: addLabelIntoCategory 단계에서
        // 이미 .active 상태의 색상이 지정되어 있음

        // 해당되는 라벨 엘리먼트에 active 클래스 지정
        $('.labels .issue-label[data-labelId="' + sId + '"]').addClass('active');
    }

    function _resetLabel(labelId) {
        $(".labels .issue-label").removeClass("active");
        _setActiveLabel(labelId);
    }

    // 인터페이스 반환
    return {
        "init": _init,
        "setActiveLabel": _setActiveLabel,
        "resetLabel": _resetLabel
    };
})();
