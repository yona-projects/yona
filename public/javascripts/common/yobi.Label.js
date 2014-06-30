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
 * yobi.Label
 * 라벨 목록을 가져다 표현해주는 역할
 * (개별 삭제 링크 처리 포함)
 */
yobi.Label = (function(htOptions){

    var htVar = {};
    var htElement = {};

    /**
     * initialize
     * @param {Hash Table} htOptions
     * @param {Boolean} htOptions.bEditable
     * @param {String} htOptions.sTplLabel
     * @param {String} htOptions.sTplControls
     * @param {String} htOptions.sTplBtnLabelId
     * @param {String} htOptions.sURLLabels
     * @param {String} htOptions.sURLPost
     */
    function _init(htOptions){
        htOptions = $.extend({"bEditable": false}, htOptions);
        _initVar(htOptions);
        _initElement(htOptions);
        _initLabelEditor();
        _attachEvent();
    }

    /**
     *  initialize variable except element
     *    htVar.sURLLabels = htOptions.sURLLabels;
     *    htVar.sURLPost = htOptions.sURLPost;
     *    htVar.bEditable = htOptions.bEditable;
     *  @param {Hash Table} htOptions
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
     * initialize event handler
     */
    function _attachEvent(){
        htElement.welForm.submit(_onSubmitForm);
        htElement.welBtnManageLabel.click(_clickBtnManageLabel);
    }

    function _onSubmitForm(){
        // append labelIds to searchForm
        if(htVar.bEditable === false){
            _appendSelectedLabelIdsToForm();
        }

        return true;
    }

    /**
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
     * @private
     */
    function _clearLabelIdsOnForm(){
        $("input[name='labelIds']").each(function(i, elInput) {
            $(elInput).remove();
        });
    }

    function _clickBtnManageLabel() {
        if(htVar.bEditable === false){
            _appendSelectedLabelIdsToForm();
        }

        htVar.bEditable = !htVar.bEditable;
        _initLabelEditor();
    }

    /**
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
     * @param {Function} fCallback
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
     * @param {Number} oLabel.id
     * @param {String} oLabel.category
     * @param {String} oLabel.name
     * @param {String} oLabel.color
     * @return {Wrapped Element}
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

        if(htVar.bEditable){
            welBtnLabelId.addClass('active');
        }
        welBtnLabelId.click(_onClickLabel);

        var welCategory = $('fieldset.labels div[data-category="' + oLabel.category + '"]');
        if (welCategory.length > 0) {
            welCategory.append(welBtnLabelId);
            return welBtnLabelId;
        }

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
     * @param {Object} oLabel
     */
    function _setLabelColor(oLabel){
        var sDefaultCSSTarget = '.issue-label[data-labelId="' + oLabel.id + '"]';
        var sActiveCSSTarget = '.issue-label.active[data-labelId="' + oLabel.id + '"]';

        var aDefaultCss = [];
        var sDefaultCssSkel = 'box-shadow: inset 2px 0 0px ' + oLabel.color;
        ["", "-moz-", "-webkit-"].forEach(function(sPrefix){
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
     * @param {Number} nLabelId
     */
    function _getActiveClass(nLabelId) {
        if (htVar.aSelectedLabels && htVar.aSelectedLabels.indexOf(nLabelId) != -1) {
            return 'active';
        }
        return '';
    }

    /**
     * @param {Wrapped Event} weEvt
     * @return {Boolean} false
     */
    function _onClickLabel(weEvt){
        var welCurrent = $(weEvt.target); // SPAN .delete or .issue-label
        var welLabel = welCurrent.attr("data-labelId") ? welCurrent : welCurrent.parent("[data-labelId]");
        var sLabelId = welLabel.attr("data-labelId");

        if(htVar.bEditable && welCurrent.hasClass("delete")){
            if(confirm(Messages("label.confirm.delete")) === false){
                return false;
            }

            return _requestDeleteLabel(sLabelId);
        }

        if(!htVar.bEditable){
            welLabel.siblings().removeClass("active");
            welLabel.toggleClass("active");
        }

        if (htVar.bRefresh) {
            htElement.welForm.submit();
        }

        return false;
    }


    /**
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
     * @param {String} sId
     */
    function _setActiveLabel(sId){
        $('.labels .issue-label[data-labelId="' + sId + '"]').addClass('active');
    }

    function _resetLabel(labelId) {
        $(".labels .issue-label").removeClass("active");
        _setActiveLabel(labelId);
    }

    return {
        "init": _init,
        "setActiveLabel": _setActiveLabel,
        "resetLabel": _resetLabel
    };
})();
