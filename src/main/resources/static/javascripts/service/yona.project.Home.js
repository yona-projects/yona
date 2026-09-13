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
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        function _init(htOptions){
            var htOpt = htOptions || {};
            _initVar(htOpt);
            _initElement(htOptions);
            _attachEvent();

//            _resizeProjectInfo();
        }

        function _initVar(htOptions){
            htVar.sURLProjectLabels = htOptions.sURLProjectLabels;
            htVar.sURLLabels = htOptions.sURLLabels;
            htVar.sURLLabelCategories = htOptions.sURLLabelCategories;
            htVar.nProjectId = htOptions.nProjectId;
            htVar.sRepoURL = htOptions.sRepoURL;
            htVar.sURLProject = htOptions.sURLProject;
            htVar.sURLZeroClipboard = htOptions.sURLZeroClipboard;
        }

        /**
         * initialize element
         */
        function _initElement(htOptions) {
            // #plus-button-template 마크업 자체가 legacy(v1.6 yobi.project.Home.js)부터
            // 존재하지 않았다(git grep으로 대조 확인) - 이 라벨 보드 위젯(#label-board) 전체가
            // project/home.html에 이식되지 않은 도달 불가능 코드라, welBtnPlus도 원래부터
            // 할당만 되고 쓰인 적이 없다. .tmpl() 의존성만 제거한다.
            var welBtnPlus = $();

            htElement.welRepoURL = $("#repositoryURL");

            // clone url
            htElement.welBtnClone   = $('[data-toggle="cloneURL"]');

            htElement.welInputCloneURL =$('#cloneURL');
            htElement.welBtnCopy   = $('#cloneURLBtn');

            htElement.elAlertLeave = document.getElementById("alertLeave");
            $yona.attachDialogDismiss(htElement.elAlertLeave);
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            htElement.welRepoURL.click(_onClickRepoURL);

            if (ClipboardJS && ClipboardJS.isSupported() && htElement.welBtnCopy.length > 0) {
                // Using clipboard.min.js if supports clipboard api.
                new ClipboardJS(htElement.welBtnCopy[0], {
                    target: function() {
                        return document.getElementById('cloneURL');
                    }
                }).on('success', function(e) {
                    yona.Common.notify(Messages("code.copyUrl.copied"), 1000);
                    e.clearSelection();
                });
            } else {
                // Use zclipboard(Flash based) if not support clipboard api.
                htElement.welBtnCopy.zclip({
                    "path": htVar.sURLZeroClipboard,
                    "copy": htElement.welInputCloneURL.val(),
                    "afterCopy": function () {
                        yona.Common.notify(Messages("code.copyUrl.copied"), 1000);
                    }
                });
            }

            htElement.welInputCloneURL.on('click',function(){
                $(this).select();
            });

            $('.project-page-wrap')
                .on('click', '[data-toggle="description-edit"]', function(){
                    $('[data-toggle="project-description-tab"]').toggleClass('hidden');
                    $('.project-description-edit input').focus();
                }).on('click', '[data-toggle="description-cancel"]', function(){
                    $('[data-toggle="project-description-tab"]').toggleClass('hidden');
                });
            $('#descriptionSaveBtn').on('click',function(){
                var overview = {"overview" : $("#project-description-input").val() };
                fetch(htVar.sURLProject, {
                    "method": "put",
                    "headers": {"Content-Type": "application/json"},
                    "body": JSON.stringify(overview)
                }).then(function(response){
                    if(!response.ok){
                        return Promise.reject(response);
                    }
                    return response.json();
                }).then(function(data){
                        var sDescription = (data.overview)
                                            ? data.overview
                                            : $("#project-description-input").attr('placeholder');

                        yona.Markdown.render($("#project-description"), sDescription);

                        $('[data-toggle="project-description-tab"]').toggleClass('hidden');


                }).catch(function(err){
                        console.log("err>> ", err);
                });
            });
            $('#projectLeaveBtn').on('click',function(){
                htElement.elAlertLeave.showModal();

                var sURL = $(this).attr("data-href");

                $("#leaveBtn").click(function(){

                    fetch(sURL, {"method": "delete"})
                    .then(function(response){
                        if(!response.ok){
                            return Promise.reject(response);
                        }
                        return response.text();
                    }).then(function(sResult){
                        var htData = $.parseJSON(sResult);
                        document.location.replace(htData.location);
                    }).catch(function(oXHR){
                        var sErrorMsg;

                        switch(oXHR.status){
                            case 403:
                                sErrorMsg = Messages("project.member.notExist");
                                break;

                            case 404:
                                sErrorMsg = Messages("project.is.empty");
                                break;

                            default:
                                sErrorMsg = Messages("error.badrequest");
                                break;
                        }

                        $yona.alert(sErrorMsg);
                    });
                });
            });
        }

        /*
        function _resizeProjectInfo(){
            htElement.welHomeInfo.width(htElement.welHome.width() - (htElement.welHomeLogo.width() + htElement.welHomeMember.width() + 21));
        }
        */

        function _onClickRepoURL(){
            htElement.welRepoURL.select();
        }

        /**
        * When any key is pressed on input box in any Category line.
        *
        * @param {Object} oEvent
        */
        function _onKeyPressNewLabel(oEvent) {
            if (oEvent.keyCode == 13) {
                _submitLabel();
                return false;
            }
        }

        /**
         * When any key is pressed on input box in New Category line.
         *
         * @param {Object} oEvent
         */
        function _onKeyPressNewCategory(oEvent) {
            if (oEvent.keyCode == 13) {
                _onClickNewCategory();
                return false;
            }
        }

        /**
         * Read data to create a label from input box.
         */
        function _labelFromInput() {
            return {
                "category": htElement.welInputLabel.data('category'),
                "name": htElement.welInputLabel.val()
            };
        }

        /**
        * Submit new tag to add that.
        */
        function _submitLabel() {
            var htLabel = _labelFromInput();

            if (htLabel == null) {
                return;
            }

            htElement.welInputLabel.val("");

            $yona.sendForm({
                "sURL"   : htVar.sURLProjectLabels,
                "htData" : htLabel,
                "fOnLoad": _appendLabels
            });
        }

        var aRequired = ["Language", "License"];

        function isRequired(sCategory) {
            return aRequired.indexOf(sCategory) < 0;
        }

        /**
        * Get list of tags from the server and show them in #tags div.
        */
        function _initLabels() {
            $yona.sendForm({
                "sURL"     : htVar.sURLProjectLabels,
                "htOptForm": {"method":"get"},
                "fOnLoad"  : function(data) {
                        _appendLabels(data);

                        for (var i = 0; i < aRequired.length; i++) {
                            var sCategory = aRequired[i];
                            if (!htElement.htCategory.hasOwnProperty(sCategory)) {
                                __addCategory(sCategory);
                            }
                        }

                        _hideLabelEditor();
                    }
            });
        }

        /**
        * Make a tag element by given instance id and name.)
        *
        * @param {String} sInstanceId
        * @param {String} sName
        */
        function _createLabel(sInstanceId, sName) {
            // If someone clicks a delete button, remove the tag which contains
            // the button, and also hide its category in .project-info div if
            // the category becomes to have no tag.
            var fOnLoadAfterDeleteLabel = function() {
                var welCategory = welLabel.parent().parent();
                var sCategory = welCategory.data('category');
                welLabel.remove();
                if (welCategory.children('.label-list').children().length == 0
                    && isRequired(sCategory)
                    && htElement.welInputLabel.data('category') != sCategory) {
                    delete htElement.htCategory[sCategory];
                    welCategory.remove();
                }
            };

            var fOnClickDelete = function() {
                $yona.sendForm({
                    "sURL"   : htVar.sURLProjectLabels + '/' + sInstanceId,
                    "htData" : {"_method":"DELETE"},
                    "fOnLoad": fOnLoadAfterDeleteLabel
                });
            };

            // #label-delete-button-template/#label-template도 legacy부터 마크업이
            // 없었다(위 _initElement 주석 참고) - 사용 패턴(welLabel.addClass('label')/
            // .append(welDeleteButton), welDeleteButton.show()/.hide())에서 합리적으로
            // 추정한 최소 마크업으로 대체한다. 실제로 실행될 일이 없는 코드라 원본과
            // 100% 동일할 필요는 없다.
            var welDeleteButton = $('<button type="button" class="btn-delete-label">&times;</button>')
                .click(fOnClickDelete);

            var welLabel = $($yona.tmpl('<span class="issue-label">${name}</span>', {'name': sName}))
                .append(welDeleteButton);

            welLabel.setRemovability = function(bFlag) {
                if (bFlag === true) {
                    welLabel.addClass('label');
                    welDeleteButton.show();
                } else {
                    welLabel.removeClass('label');
                    welDeleteButton.hide();
                }
            }

            htElement.aLabel.push(welLabel);

            return welLabel;
        }

        /**
        * Append the given tags on #tags div to show them.
        *
        * @param {Object} htLabels
        */
        function _appendLabels(htLabels) {
            for(var sInstanceId in htLabels) {
                var waCategory, welCategory;
                var htLabel = htLabels[sInstanceId];

                waCategory = htElement.welLabelBoard
                    .children("[data-category=" + htLabel.category + "]");

                if (waCategory.length > 0) {
                    waCategory
                        .children(".label-list")
                        .append(_createLabel(sInstanceId, htLabel.name));
                } else {
                    __addCategory(htLabel.category)
                        .children(".label-list")
                        .append(_createLabel(sInstanceId, htLabel.name));
                }
            }
        }

        /**
         * Create a category consists with category name, labels belong
         * to this and plus button to add a label.
         *
         * @param {String} sCategory
         * @return {Object} The created category
         */
        function _createCategory(sCategory) {
            var welBtnPlusLabel = htElement.welBtnPlusLabel
                .clone()
                .data('category', sCategory)
                .click(_onClickPlusLabel);

            // #category-template도 legacy부터 마크업이 없었다(_initElement 주석 참고) -
            // _appendLabels()가 [data-category=...]로 찾고 .children('.label-list')에
            // 라벨을 추가하는 사용 패턴에서 합리적으로 추정한 최소 마크업으로 대체한다.
            var welCategory = $($yona.tmpl(
                '<div data-category="${category}"><span class="category-name">${category}</span><div class="label-list"></div></div>',
                {'category': sCategory}
            )).append(welBtnPlusLabel);

            welCategory.welBtnPlusLabel = welBtnPlusLabel;
            htElement.aBtnPlusLabel.push(welBtnPlusLabel);

            return welCategory;
        }

        function __addCategory(sCategory) {
            welCategory = _createCategory(sCategory);
            htElement.htCategory[sCategory] = welCategory;
            htElement.welNewCategory.before(welCategory);

            return welCategory;
        }

        /**
         * Add a category just before `htElement.welNewCategory`.
         */
        function _onClickNewCategory() {
            var sCategory = htElement.welInputCategory.val();
            var welCategory = htElement.htCategory[sCategory];

            if (!welCategory) {
                welCategory = __addCategory(sCategory);
            }

            htElement.welInputCategory.val("");
            welCategory.welBtnPlusLabel.trigger('click');
            if (!htElement.welInputCategory.is(":focus")) {
                htElement.welInputCategory.trigger('focus');
            }
        }

        /**
         * When a plus button in the end of the Label Board is clicked..
         */
        function _onClickPlusCategory() {
            htElement.welInputLabelBox.hide();
            htElement.welInputCategoryBox.show();
            $(this).before(htElement.welInputCategoryBox);
            jQuery.map(htElement.aBtnPlusLabel, function(btn) { btn.show(); });
            htElement.welBtnPlusCategory.hide();

            if (!htElement.welInputCategory.is(":focus")) {
                htElement.welInputCategory.trigger('focus');
            }
        }

        /**
         * When a plus button in each category is clicked..
         */
        function _onClickPlusLabel() {
            var sCategory, welCategory, nLabel;

            for (sCategory in htElement.htCategory) {
                if ($(this).data('category') == sCategory) {
                    continue;
                }

                welCategory = htElement.htCategory[sCategory];

                nLabel = welCategory.children('.label-list').children().length;

                if (nLabel == 0 && isRequired(sCategory)) {
                    delete htElement.htCategory[sCategory];
                    welCategory.remove();
                }
            }

            htElement.welInputLabel.data('category', $(this).data('category'));

            new yona.ui.Typeahead(htElement.welInputLabel, {
                "sActionURL": htVar.sURLLabels,
                "htData": {
                    "category":  $(this).data('category'),
                    "project_id": htVar.nProjectId,
                    "limit": 8
                }
            });

            htElement.welInputCategoryBox.hide();
            htElement.welInputLabelBox.show();
            $(this).after(htElement.welInputLabelBox);
            jQuery.map(htElement.aBtnPlusLabel, function(btn) { btn.show(); });
            $(this).hide();

            if (!htElement.welInputLabel.is(":focus")) {
                htElement.welInputLabel.trigger('focus');
            }
        }

        /**
        * Show all delete buttons for all labels if bFlag is true, and hide if
        * bFlag is false.
        *
        * @param {Boolean} bFlag
        */
        function _setLabelsRemovability(bFlag) {
            jQuery.map(htElement.aLabel,
                    function(label) { label.setRemovability(bFlag); });
        }

        /**
        * Make .project-info div editable.
        *
        * @param {Boolean} bFlag
        */
        function _hideLabelEditor() {
            _setLabelsRemovability(false);

            jQuery.map(htElement.aBtnPlusLabel, function(btn) { btn.hide(); });
            htElement.welBtnPlusCategory.hide();

            htElement.welInputCategoryBox.hide();
            htElement.welInputLabelBox.hide();

            htElement.welLabelBoard
                .css('height', htVar.nLabelBoardHeight);
            htElement.welLabelBoard.parent()
                .css('height', htVar.labelBoardParentHeight);
        }

        /**
        * Make .project-info div uneditable.
        *
        * @param {Boolean} bFlag
        */
        function _showLabelEditor() {
            _setLabelsRemovability(true);

            jQuery.map(htElement.aBtnPlusLabel, function(btn) { btn.show(); });
            htElement.welBtnPlusCategory.show();

            htVar.nLabelBoardHeight =
                htElement.welLabelBoard.css('height');
            htVar.nLabelBoardParentHeight =
                htElement.welLabelBoard.parent().css('height');

            htElement.welLabelBoard.css('height', 'auto');
            htElement.welLabelBoard.parent().css('height', 'auto');
        }

        _init(htOptions || {});
    };

})("yona.project.Home");
