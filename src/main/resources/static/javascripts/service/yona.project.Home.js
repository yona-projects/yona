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

        function _delegate(container, sEventType, sSelector, fHandler){
            if(!container){
                return;
            }
            container.addEventListener(sEventType, function(weEvt){
                var matched = weEvt.target.closest(sSelector);
                if(matched && container.contains(matched)){
                    fHandler.call(matched, weEvt);
                }
            });
        }

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
            // #plus-button-template 마크업이 legacy부터 없어 이 라벨 보드 위젯(#label-board)
            // 전체가 project/home.html에 이식되지 않은 도달 불가능 코드다 - welBtnPlus도
            // 원래부터 할당만 되고 쓰인 적이 없다.
            var welBtnPlus = null;

            htElement.welRepoURL = document.getElementById("repositoryURL");

            // clone url - welBtnClone은 원본에서도 할당만 되고 어디서도 읽지 않는 죽은 코드다.
            htElement.welBtnClone = document.querySelector('[data-toggle="cloneURL"]');

            htElement.welInputCloneURL = document.getElementById("cloneURL");
            htElement.welBtnCopy = document.getElementById("cloneURLBtn");

            htElement.elAlertLeave = document.getElementById("alertLeave");
            $yona.attachDialogDismiss(htElement.elAlertLeave);
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            if(htElement.welRepoURL){
                htElement.welRepoURL.addEventListener("click", _onClickRepoURL);
            }

            if (ClipboardJS && ClipboardJS.isSupported() && htElement.welBtnCopy) {
                // Using clipboard.min.js if supports clipboard api.
                new ClipboardJS(htElement.welBtnCopy, {
                    target: function() {
                        return document.getElementById('cloneURL');
                    }
                }).on('success', function(e) {
                    yona.Common.notify(Messages("code.copyUrl.copied"), 1000);
                    e.clearSelection();
                });
            } else {
                // jquery.zclip.js(lib/, 수정 금지) 플러그인이라 이 지점만 jQuery로 감싼다.
                // Flash 폴백이라 ClipboardJS 지원 브라우저에서는 도달하지 않는다 -
                // welBtnCopy가 null이어도 $(null)은 빈 컬렉션이라 원본의 no-op과 동치.
                $(htElement.welBtnCopy).zclip({
                    "path": htVar.sURLZeroClipboard,
                    "copy": htElement.welInputCloneURL ? htElement.welInputCloneURL.value : undefined,
                    "afterCopy": function () {
                        yona.Common.notify(Messages("code.copyUrl.copied"), 1000);
                    }
                });
            }

            if(htElement.welInputCloneURL){
                htElement.welInputCloneURL.addEventListener('click', function(){
                    this.select();
                });
            }

            document.querySelectorAll('.project-page-wrap').forEach(function(elWrap){
                _delegate(elWrap, 'click', '[data-toggle="description-edit"]', function(){
                    document.querySelectorAll('[data-toggle="project-description-tab"]').forEach(function(el){
                        el.classList.toggle('hidden');
                    });
                    var elDescInput = document.querySelector('.project-description-edit input');
                    if(elDescInput){
                        elDescInput.focus();
                    }
                });
                _delegate(elWrap, 'click', '[data-toggle="description-cancel"]', function(){
                    document.querySelectorAll('[data-toggle="project-description-tab"]').forEach(function(el){
                        el.classList.toggle('hidden');
                    });
                });
            });

            var elDescSaveBtn = document.getElementById('descriptionSaveBtn');
            if(elDescSaveBtn){
                elDescSaveBtn.addEventListener('click', function(){
                    var elDescInput = document.getElementById("project-description-input");
                    var overview = {"overview": elDescInput ? elDescInput.value : ""};

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
                                                : (elDescInput ? elDescInput.getAttribute('placeholder') : "");

                            yona.Markdown.render(document.getElementById("project-description"), sDescription);

                            document.querySelectorAll('[data-toggle="project-description-tab"]').forEach(function(el){
                                el.classList.toggle('hidden');
                            });


                    }).catch(function(err){
                            console.log("err>> ", err);
                    });
                });
            }

            var elProjectLeaveBtn = document.getElementById('projectLeaveBtn');
            if(elProjectLeaveBtn){
                elProjectLeaveBtn.addEventListener('click', function(){
                    htElement.elAlertLeave.showModal();

                    var sURL = this.getAttribute("data-href");

                    var elLeaveBtn = document.getElementById("leaveBtn");
                    if(elLeaveBtn){
                        // 원본과 동일하게 이 바깥쪽 핸들러가 호출될 때마다(=다이얼로그를 열 때마다)
                        // #leaveBtn에 리스너가 하나씩 추가로 누적된다(원본 jQuery `.click()`도
                        // 매번 새 리스너를 추가만 할 뿐 이전 것을 제거하지 않는 동일한 pre-existing
                        // 동작 - jQuery 제거와 무관해 고치지 않고 그대로 보존).
                        elLeaveBtn.addEventListener("click", function(){

                            fetch(sURL, {"method": "delete"})
                            .then(function(response){
                                if(!response.ok){
                                    return Promise.reject(response);
                                }
                                return response.text();
                            }).then(function(sResult){
                                var htData = JSON.parse(sResult);
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
                    }
                });
            }
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
         * 이하 라벨 보드(#label-board) 위젯 전체는 project/home.html에 실제로 이식되지 않은
         * 도달 불가능 코드다(_init()이 이 함수들을 호출하지 않고, 관련 htElement 필드도
         * _initElement에서 할당되지 않는다). 마크업도 legacy부터 없었다 - 삭제하지 않고
         * 동치 보장 없이 vanilla로만 전환한다.
         */

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
                "category": htElement.welInputLabel.dataset.category,
                "name": htElement.welInputLabel.value
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

            htElement.welInputLabel.value = "";

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
                var welCategory = welLabel.parentElement ? welLabel.parentElement.parentElement : null;
                var sCategory = welCategory ? welCategory.dataset.category : undefined;
                welLabel.remove();
                var elLabelList = welCategory ? welCategory.querySelector('.label-list') : null;
                if ((!elLabelList || elLabelList.children.length == 0)
                    && isRequired(sCategory)
                    && htElement.welInputLabel.dataset.category != sCategory) {
                    delete htElement.htCategory[sCategory];
                    if(welCategory){
                        welCategory.remove();
                    }
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
            // 없었다(위 주석 참고) - 사용 패턴(welLabel.addClass('label')/
            // .append(welDeleteButton), welDeleteButton.show()/.hide())에서 합리적으로
            // 추정한 최소 마크업으로 대체한다. 실제로 실행될 일이 없는 코드라 원본과
            // 100% 동일할 필요는 없다.
            var welDeleteButton = document.createElement('button');
            welDeleteButton.type = 'button';
            welDeleteButton.className = 'btn-delete-label';
            welDeleteButton.innerHTML = '&times;';
            welDeleteButton.addEventListener('click', fOnClickDelete);

            var welLabel = document.createElement('span');
            welLabel.className = 'issue-label';
            welLabel.textContent = sName;
            welLabel.appendChild(welDeleteButton);

            welLabel.setRemovability = function(bFlag) {
                if (bFlag === true) {
                    welLabel.classList.add('label');
                    welDeleteButton.style.display = "";
                } else {
                    welLabel.classList.remove('label');
                    welDeleteButton.style.display = "none";
                }
            };

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

                waCategory = htElement.welLabelBoard ?
                    Array.prototype.filter.call(htElement.welLabelBoard.children, function(el){
                        return el.getAttribute("data-category") === String(htLabel.category);
                    }) : [];

                if (waCategory.length > 0) {
                    var elLabelList = waCategory[0].querySelector(".label-list");
                    if(elLabelList){
                        elLabelList.appendChild(_createLabel(sInstanceId, htLabel.name));
                    }
                } else {
                    var elNewLabelList = __addCategory(htLabel.category).querySelector(".label-list");
                    if(elNewLabelList){
                        elNewLabelList.appendChild(_createLabel(sInstanceId, htLabel.name));
                    }
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
            var welBtnPlusLabel = htElement.welBtnPlusLabel ? htElement.welBtnPlusLabel.cloneNode(true) : null;
            if(welBtnPlusLabel){
                welBtnPlusLabel.dataset.category = sCategory;
                welBtnPlusLabel.addEventListener('click', _onClickPlusLabel);
            }

            // #category-template도 legacy부터 마크업이 없었다(위 주석 참고) -
            // _appendLabels()가 [data-category=...]로 찾고 .children('.label-list')에
            // 라벨을 추가하는 사용 패턴에서 합리적으로 추정한 최소 마크업으로 대체한다.
            var welCategory = document.createElement('div');
            welCategory.setAttribute('data-category', sCategory);
            var welCategoryName = document.createElement('span');
            welCategoryName.className = 'category-name';
            welCategoryName.textContent = sCategory;
            var welLabelList = document.createElement('div');
            welLabelList.className = 'label-list';
            welCategory.appendChild(welCategoryName);
            welCategory.appendChild(welLabelList);
            if(welBtnPlusLabel){
                welCategory.appendChild(welBtnPlusLabel);
            }

            welCategory.welBtnPlusLabel = welBtnPlusLabel;
            htElement.aBtnPlusLabel.push(welBtnPlusLabel);

            return welCategory;
        }

        function __addCategory(sCategory) {
            // 원본 그대로 보존: `welCategory =`에 `var`가 없어 암묵적 전역이 되는 pre-existing
            // 버그다(이 파일에 "use strict" 선언이 없어 예외 없이 그대로 동작) - 도달 불가능
            // 코드라 무해하며, jQuery 전환과 무관해 고치지 않는다.
            welCategory = _createCategory(sCategory);
            htElement.htCategory[sCategory] = welCategory;
            if(htElement.welNewCategory){
                htElement.welNewCategory.before(welCategory);
            }

            return welCategory;
        }

        /**
         * Add a category just before `htElement.welNewCategory`.
         */
        function _onClickNewCategory() {
            var sCategory = htElement.welInputCategory.value;
            var welCategory = htElement.htCategory[sCategory];

            if (!welCategory) {
                welCategory = __addCategory(sCategory);
            }

            htElement.welInputCategory.value = "";
            if(welCategory.welBtnPlusLabel){
                welCategory.welBtnPlusLabel.click();
            }
            if (document.activeElement !== htElement.welInputCategory) {
                htElement.welInputCategory.focus();
            }
        }

        /**
         * When a plus button in the end of the Label Board is clicked..
         */
        function _onClickPlusCategory() {
            htElement.welInputLabelBox.style.display = "none";
            htElement.welInputCategoryBox.style.display = "";
            this.before(htElement.welInputCategoryBox);
            htElement.aBtnPlusLabel.forEach(function(btn) { if(btn){ btn.style.display = ""; } });
            htElement.welBtnPlusCategory.style.display = "none";

            if (document.activeElement !== htElement.welInputCategory) {
                htElement.welInputCategory.focus();
            }
        }

        /**
         * When a plus button in each category is clicked..
         */
        function _onClickPlusLabel() {
            var sCategory, welCategory, nLabel;

            for (sCategory in htElement.htCategory) {
                if (this.dataset.category == sCategory) {
                    continue;
                }

                welCategory = htElement.htCategory[sCategory];

                var elLabelList = welCategory.querySelector('.label-list');
                nLabel = elLabelList ? elLabelList.children.length : 0;

                if (nLabel == 0 && isRequired(sCategory)) {
                    delete htElement.htCategory[sCategory];
                    welCategory.remove();
                }
            }

            htElement.welInputLabel.dataset.category = this.dataset.category;

            new yona.ui.Typeahead(htElement.welInputLabel, {
                "sActionURL": htVar.sURLLabels,
                "htData": {
                    "category":  this.dataset.category,
                    "project_id": htVar.nProjectId,
                    "limit": 8
                }
            });

            htElement.welInputCategoryBox.style.display = "none";
            htElement.welInputLabelBox.style.display = "";
            this.after(htElement.welInputLabelBox);
            htElement.aBtnPlusLabel.forEach(function(btn) { if(btn){ btn.style.display = ""; } });
            this.style.display = "none";

            if (document.activeElement !== htElement.welInputLabel) {
                htElement.welInputLabel.focus();
            }
        }

        /**
        * Show all delete buttons for all labels if bFlag is true, and hide if
        * bFlag is false.
        *
        * @param {Boolean} bFlag
        */
        function _setLabelsRemovability(bFlag) {
            htElement.aLabel.forEach(function(label) { label.setRemovability(bFlag); });
        }

        /**
        * Make .project-info div editable.
        *
        * @param {Boolean} bFlag
        */
        function _hideLabelEditor() {
            _setLabelsRemovability(false);

            htElement.aBtnPlusLabel.forEach(function(btn) { if(btn){ btn.style.display = "none"; } });
            htElement.welBtnPlusCategory.style.display = "none";

            htElement.welInputCategoryBox.style.display = "none";
            htElement.welInputLabelBox.style.display = "none";

            htElement.welLabelBoard.style.height = htVar.nLabelBoardHeight;
            // 원본 그대로 보존: 여기서 읽는 키(`labelBoardParentHeight`, n 없음)는
            // _showLabelEditor가 실제로 쓰는 키(`nLabelBoardParentHeight`, n 있음)와
            // 이름이 달라 항상 undefined인 pre-existing 오타 버그다 - 도달 불가능 코드라
            // 무해하며 jQuery 전환과 무관해 고치지 않는다.
            if(htElement.welLabelBoard.parentElement){
                htElement.welLabelBoard.parentElement.style.height = htVar.labelBoardParentHeight;
            }
        }

        /**
        * Make .project-info div uneditable.
        *
        * @param {Boolean} bFlag
        */
        function _showLabelEditor() {
            _setLabelsRemovability(true);

            htElement.aBtnPlusLabel.forEach(function(btn) { if(btn){ btn.style.display = ""; } });
            htElement.welBtnPlusCategory.style.display = "";

            htVar.nLabelBoardHeight = window.getComputedStyle(htElement.welLabelBoard).height;
            htVar.nLabelBoardParentHeight = htElement.welLabelBoard.parentElement ?
                window.getComputedStyle(htElement.welLabelBoard.parentElement).height : undefined;

            htElement.welLabelBoard.style.height = 'auto';
            if(htElement.welLabelBoard.parentElement){
                htElement.welLabelBoard.parentElement.style.height = 'auto';
            }
        }

        _init(htOptions || {});
    };

})("yona.project.Home");
