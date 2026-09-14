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

        /**
         * initialize
         */
        function _init(htOptions){
            _initVar(htOptions || {});
            _initElement(htOptions || {});
            _attachEvent();
        }

        /**
         * initialize variables except element
         */
        function _initVar(htOptions){
            htVar.nTotalPages = htOptions.nTotalPages || 1;
            htVar.sIssueCheckBoxesSelector = htOptions.sIssueCheckBoxesSelector;
            htVar.sIssueCheckedBoxesSelector = htVar.sIssueCheckBoxesSelector + ':checked';
            htVar.sActionURL = htOptions.sURL;
            htVar.htExclusiveLabels = {};
            // #attaching-label/#detaching-label은 프로젝트에 라벨이 없으면 애초에 렌더링되지
            // 않는다(th:if) - 원본 jQuery `$(...).data('name')`는 그 경우 매치 없는 빈 집합에서
            // undefined를 반환해 "undefined[]"가 됐다(런타임 에러는 아님). 네이티브
            // getElementById는 그 경우 null을 주므로, null 가드로 동일한 "undefined[]" 결과를
            // 재현한다(이 값 자체가 이후 실제로 쓰이는지 여부와 무관하게 원본과 동일하게).
            htVar.detachingLabelName = (htOptions.welDetachingLabel ? htOptions.welDetachingLabel.dataset.name : undefined) + '[]';

            htVar.oState     = new yona.ui.Dropdown({"elContainer": htOptions.welState});
            htVar.oAssignee  = new yona.ui.Dropdown({"elContainer": htOptions.welAssignee});
            htVar.oMilestone = new yona.ui.Dropdown({"elContainer": htOptions.welMilestone});
            htVar.oAttachingLabel = new yona.ui.Dropdown({"elContainer": htOptions.welAttachingLabel});
            htVar.oDetachingLabel = new yona.ui.Dropdown({"elContainer": htOptions.welDetachingLabel});
        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.waLabels = document.querySelectorAll("a.issue-label[data-color]");

            htElement.welContainer  = document.querySelector(".inner");
            htElement.welBtnAdvance = document.querySelector(".btn-advanced");
            htElement.welPagination = htOptions.elPagination || document.getElementById("pagination");

            htElement.welMassUpdateForm = htOptions.welMassUpdateForm;
            htElement.welMassUpdateButtons = htOptions.welMassUpdateButtons;
            htElement.waCheckboxes  = document.querySelectorAll(htOptions.sIssueCheckBoxesSelector);
            htElement.weAllCheckbox = document.getElementById('check-all');

            // welAttachingLabel/welDetachingLabel 자체가 null일 수 있다(위 참고) - 그 경우
            // 원본 jQuery `$(null).find("button")`는 빈 집합을 안전하게 반환하지만 네이티브
            // querySelector는 null에서 호출하면 예외이므로 가드한다.
            htElement.welBtnAttachingLabel = htOptions.welAttachingLabel ? htOptions.welAttachingLabel.querySelector("button") : null;
            htElement.welBtnDetachingLabel = htOptions.welDetachingLabel ? htOptions.welDetachingLabel.querySelector("button") : null;
            htElement.welAttachLabels = document.getElementById('attach-label-list');
            htElement.welDetachLabels = document.getElementById('delete-label-list');
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            // massUpdate dropdowns
            htVar.oState.onChange(_onChangeUpdateField);
            htVar.oMilestone.onChange(_onChangeUpdateField);
            htVar.oAssignee.onChange(_onChangeUpdateField);
            htVar.oAttachingLabel.onChange(_onChangeAttachingLabelField);
            htVar.oDetachingLabel.onChange(_onChangeUpdateField);

            // massUpdate checkboxes
            htElement.waCheckboxes.forEach(function(el){
                el.addEventListener("change", _onCheckIssue);
            });
            if(document.querySelectorAll(htVar.sIssueCheckedBoxesSelector).length > 0){ // if already checked box exists
                _onCheckIssue();
            }

            // selectAll
            if(htElement.weAllCheckbox){
                htElement.weAllCheckbox.addEventListener('click', function(){
                    var checked = htElement.weAllCheckbox.checked;
                    htElement.waCheckboxes.forEach(function(el){
                        el.checked = checked;
                        // 원본 jQuery `.change()`는 각 체크박스에 바인딩된 "change" 핸들러
                        // (_onCheckIssue)를 그대로 호출한다 - 네이티브 change 이벤트를 직접
                        // 발생시켜 동일하게 재현.
                        el.dispatchEvent(new Event('change'));
                    });
                    _onCheckIssue();
                });
            }
            yona.ShortcutKey.attach("CTRL+A", function(htInfo){
                if(!htInfo.bFormInput){
                    htInfo.weEvt.preventDefault();
                    if(htElement.weAllCheckbox){
                        // 원본 jQuery `.trigger('click')`는 체크박스처럼 네이티브 메서드가
                        // 있는 이벤트는 실제 네이티브 click()을 호출한다 - 동일하게 재현.
                        htElement.weAllCheckbox.click();
                    }
                    return false;
                }
            });
        }

        /**
         * Add a hidden input element into the given form.
         */
        function _addFormField(welForm, sName, sValue) {
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = sName;
            input.value = sValue;
            welForm.appendChild(input);
        }

        /**
         * When check an issue, enable Mass Update dropdowns if only one or
         * more issues are checked, otherwise disable them.
         */
        function _onCheckIssue(){
            var waChecked = document.querySelectorAll(htVar.sIssueCheckedBoxesSelector);
            var bDisabled = (waChecked.length === 0);

            htElement.welMassUpdateButtons.forEach(function(btn){
                btn.disabled = bDisabled;
            });

            if(bDisabled){
                _restoreLabelList();
            } else {
                _makeLabelListByChecked(waChecked);
            }
        }

        /**
         * Restore labels list
         */
        function _restoreLabelList(){
            if(htElement.welAttachLabels){
                htElement.welAttachLabels.querySelectorAll('li').forEach(function(el){
                    el.style.display = "";
                });
            }
            if(htElement.welDetachLabels){
                htElement.welDetachLabels.querySelectorAll('li').forEach(function(el){
                    el.style.display = "";
                });
            }
        }

        /**
         * Make label list by checked issue item
         *
         * @param {NodeList} waChecked
         */
        function _makeLabelListByChecked(waChecked){
            var htLabels = _getLabelsByChecked(waChecked);

            _restoreLabelList();
            _setAttachLabelList(htLabels, waChecked.length);
            _setDetachLabelList(htLabels);
        }

        /**
         * set AttachLabels list
         * make list without labels on checked issue
         *
         * @param {Hash Table} htLabels
         * @param {Number} nLength Numbers of checked issues
         */
        function _setAttachLabelList(htLabels, nLength){
            var sCategory, sLabelId, aCategoryIds;
            var bVisible = false;

            // Reset
            htVar.htExclusiveLabels = {};

            for(sCategory in htLabels){
                for(sLabelId in htLabels[sCategory]){
                    htLabel = htLabels[sCategory][sLabelId];

                    if(htLabel.issues.length === nLength){
                        var labelEl = htElement.welAttachLabels ? htElement.welAttachLabels.querySelector('[data-value="' + sLabelId + '"]') : null;
                        if(labelEl){
                            labelEl.style.display = "none";
                        }
                    }

                    if(htLabels[sCategory][sLabelId].exclusive){
                        aCategoryIds = htVar.htExclusiveLabels[htLabels[sCategory][sLabelId].categoryId] || [];
                        aCategoryIds.push(sLabelId);
                        htVar.htExclusiveLabels[htLabels[sCategory][sLabelId].categoryId] = aCategoryIds;
                    }
                } // end-for-label

                bVisible = _getLabelCategoryVisibility(htElement.welAttachLabels, sCategory) || bVisible;
            } // end-for-category

            if(htElement.welBtnAttachingLabel){
                htElement.welBtnAttachingLabel.disabled = htLabels.hasOwnProperty() ? !bVisible : false;
            }
        }

        /**
         * Hide category itself if all items are invisible
         *
         * @param {?HTMLElement} welList Target Label List (라벨이 없는 프로젝트면 null)
         * @param {String} sCategory CategoryName
         *
         * @return {Boolean} Returns does category visible
         */
        function _getLabelCategoryVisibility(welList, sCategory){
            var bHidden = true;

            if(!welList){
                return false;
            }

            var waCategoryItems = welList.querySelectorAll('li[data-category="' + sCategory + '"]');

            waCategoryItems.forEach(function(el){
                if(typeof el.dataset.value !== "undefined"){
                    bHidden = bHidden && (getComputedStyle(el).display === "none");
                }
            });

            if(bHidden){
                waCategoryItems.forEach(function(el){
                    el.style.display = "none";
                });
            }

            return !bHidden;
        }

        /**
         * set DetachLabels list
         * make list with labels on checked issue
         * make detaching button disabled if no labels on checked issue
         *
         * @param {Hash Table} htLabels
         */
        function _setDetachLabelList(htLabels){
            var aHTML = [];
            var sCategory, sLabelId, htLabel;
            var tplEl = document.getElementById("labelListItem");
            // jQuery `$('#labelListItem').text()`는 매치가 없어도 빈 문자열을 반환한다.
            var sTpl = tplEl ? tplEl.textContent : "";

            // Category
            for(sCategory in htLabels){
                aHTML.push('<li class="disabled" data-category="' + sCategory + '"><span>' + sCategory + '</span></li>');

                // Label
                for(sLabelId in htLabels[sCategory]){
                    htLabel = htLabels[sCategory][sLabelId];
                    aHTML.push($yona.tmpl(sTpl, htLabel));
                }

                aHTML.push('<li class="divider"></li>');
            }

            if(aHTML.length > 0){
                if(htElement.welDetachLabels){
                    htElement.welDetachLabels.innerHTML = aHTML.join("\n");
                }
            } else if(htElement.welBtnDetachingLabel){
                htElement.welBtnDetachingLabel.disabled = true;
            }
        }

        /**
         * Get labels by checked issue item
         *
         * @param {NodeList} waChecked
         * @return {Hash Table}
         */
        function _getLabelsByChecked(waChecked){
            var htLabels = {};
            var sIssueLabels, aLabel;
            var sCategory, sLabelId, sLabelName, sCategoryId, bExclusiveCategory;

            waChecked.forEach(function(el){
                sIssueLabels = el.dataset.issueLabels;

                if(!sIssueLabels){
                    return;
                }

                sIssueLabels.split("|").forEach(function(sLabel){
                    if(sLabel === ""){
                        return;
                    }

                    aLabel = sLabel.split(",");
                    sCategory  = aLabel[0];
                    sLabelId   = aLabel[1];
                    sLabelName = aLabel[2];
                    sCategoryId = aLabel[3];
                    bExclusiveCategory = (aLabel[4] === 'true');

                    htLabels[sCategory] = htLabels[sCategory] || {}; // category
                    htLabels[sCategory][sLabelId] = htLabels[sCategory][sLabelId] || {"id":sLabelId, "name":sLabelName, "category":sCategory, "categoryId":sCategoryId, "exclusive":bExclusiveCategory}; // label
                    htLabels[sCategory][sLabelId].issues = htLabels[sCategory][sLabelId].issues || []; // issues to count
                    htLabels[sCategory][sLabelId].issues.push(el.dataset.issueId);
                });
            });

            return htLabels;
        }

        /**
         * When change the value of any field in the Mass Update form, submit
         * the form and request to update issues.
         */
        function _onChangeUpdateField(){
            var nCnt = 0;
            var welForm = htElement.welMassUpdateForm;
            var sItemId = _getCurrentItemIdByScrollTop();

            if(sItemId){
                welForm.setAttribute("action", htVar.sActionURL + "#" + sItemId);
            }

            document.querySelectorAll(htVar.sIssueCheckedBoxesSelector).forEach(function(el){
                _addFormField(
                    welForm,
                    'issues[' + (nCnt++) + '].id',
                    el.dataset.issueId
                );
            });

            welForm.submit();
        }

        /**
         * jQuery `$(el).offset().top`(문서 기준 절대 좌표)와 `$(window).scrollTop()`의 차는
         * 대수적으로 `el.getBoundingClientRect().top`(뷰포트 기준)과 항상 같다
         * (offset().top = rect.top + scrollY, scrollTop = scrollY이므로
         * offset().top > scrollTop ⇔ rect.top > 0) - scrollY를 따로 구하지 않고
         * rect.top > 0만 비교해도 완전히 동치다.
         */
        function _getCurrentItemIdByScrollTop(){
            var postItems = document.querySelectorAll(".post-item");
            var target = null;

            for(var i = 0; i < postItems.length; i++){
                if(postItems[i].getBoundingClientRect().top > 0){
                    target = postItems[i];
                    break;
                }
            }

            if(!target){
                return null;
            }

            var prev = target.previousElementSibling;
            return prev ? prev.getAttribute("id") : null;
        }

function _onChangeAttachingLabelField(sLabelId){
            var labelEl = htElement.welAttachLabels ? htElement.welAttachLabels.querySelector('[data-value="' + sLabelId + '"]') : null;
            var categoryId = labelEl ? labelEl.dataset.category : undefined;
            var aDetachLabels = htVar.htExclusiveLabels[categoryId] || [];
            for(var i = 0; i < aDetachLabels.length; i++) {
                if(sLabelId !== aDetachLabels[i]){
                    _addFormField(
                        htElement.welMassUpdateForm,
                        htVar.detachingLabelName,
                        aDetachLabels[i]
                    );
                }
            }

            _onChangeUpdateField.apply(this, arguments);
        }

        _init(htOptions);
    };

})("yona.issue.MassUpdate");
