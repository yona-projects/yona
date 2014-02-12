/**
 * @(#)yobi.issue.MassUpdate.js 2013.08.29
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */

(function(ns){
    
    var oNS = $yobi.createNamespace(ns);
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
            _setMassUpdateFormAffixed();
        }
        
        /**
         * initialize variables except element
         */
        function _initVar(htOptions){
            htVar.nTotalPages = htOptions.nTotalPages || 1;
            htVar.sIssueCheckBoxesSelector = htOptions.sIssueCheckBoxesSelector;
            htVar.sIssueCheckedBoxesSelector = htVar.sIssueCheckBoxesSelector + ':checked';
            htVar.sActionURL = htOptions.sURL;

            htVar.oState     = new yobi.ui.Dropdown({"elContainer": htOptions.welState});
            htVar.oAssignee  = new yobi.ui.Dropdown({"elContainer": htOptions.welAssignee});
            htVar.oMilestone = new yobi.ui.Dropdown({"elContainer": htOptions.welMilestone});
            htVar.oAttachingLabel = new yobi.ui.Dropdown({"elContainer": htOptions.welAttachingLabel});
            htVar.oDetachingLabel = new yobi.ui.Dropdown({"elContainer": htOptions.welDetachingLabel});
        }
        
        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.waLabels = $("a.issue-label[data-color]"); // 목록 > 라벨

            htElement.welContainer  = $(".inner");
            htElement.welBtnAdvance = $(".btn-advanced");       
            htElement.welPagination = $(htOptions.elPagination || "#pagination");

            htElement.welMassUpdateForm = htOptions.welMassUpdateForm;
            htElement.welMassUpdateButtons = htOptions.welMassUpdateButtons;
            htElement.waCheckboxes  = $(htVar.sIssueCheckBoxesSelector);
            htElement.weAllCheckbox = $('#check-all');
            
            htElement.welBtnAttachingLabel = $(htOptions.welAttachingLabel).find("button"); // 라벨 추가 버튼
            htElement.welBtnDetachingLabel = $(htOptions.welDetachingLabel).find("button"); // 라벨 제거 버튼
            htElement.welAttachLabels = $('#attach-label-list');  // 라벨 추가 <ul>
            htElement.welDetachLabels = $('#delete-label-list');  // 라벨 제거 <ul>
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            // massUpdate dropdowns 
            htVar.oState.onChange(_onChangeUpdateField);
            htVar.oMilestone.onChange(_onChangeUpdateField);
            htVar.oAssignee.onChange(_onChangeUpdateField);
            htVar.oAttachingLabel.onChange(_onChangeUpdateField);
            htVar.oDetachingLabel.onChange(_onChangeUpdateField);

            // massUpdate checkboxes
            htElement.waCheckboxes.change(_onCheckIssue);
            if($(htVar.sIssueCheckedBoxesSelector).length > 0){ // if already checked box exists
                _onCheckIssue();
            }

            // selectAll
            $(htElement.weAllCheckbox).on('click', function(){
                $(htVar.sIssueCheckBoxesSelector).prop('checked', this.checked).change();
                _onCheckIssue();
            });
            yobi.ShortcutKey.attach("CTRL+A", function(htInfo){
                if(!htInfo.bFormInput){
                    htInfo.weEvt.preventDefault();
                    $(htElement.weAllCheckbox).trigger('click');
                    return false;
                }
            });

            htElement.welMassUpdateForm.on("submit", function(weEvt){
                $.pjax.submit(weEvt, "div[pjax-container]", {
                    "fragment": "div[pjax-container]",
                    "timeout" : 3000
                });
            });
        }

        /**
         * Add a hidden input element into the given form.
         */
        function _addFormField(welForm, sName, sValue) {
            $('<input>').attr({
                'type': 'hidden',
                'name': sName,
                'value': sValue
            }).appendTo(welForm);
        }

        /**
         * When check an issue, enable Mass Update dropdowns if only one or
         * more issues are checked, otherwise disable them.
         */
        function _onCheckIssue(){
            var waChecked = $(htVar.sIssueCheckedBoxesSelector);
            var bDisabled = (waChecked.length === 0);

            htElement.welMassUpdateButtons.attr('disabled', bDisabled);
            
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
            htElement.welAttachLabels.find('li').show();
            htElement.welDetachLabels.find('li').show();            
        }
        
        /**
         * Make label list by checked issue item
         * 
         * @param {Wrapped Array} waChecked
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
            var sCategory, sLabelId;
            var bVisible = false;
            
            // 선택한 이슈에 설정된 라벨 정보로부터
            for(sCategory in htLabels){
                for(sLabelId in htLabels[sCategory]){
                    htLabel = htLabels[sCategory][sLabelId];
                    
                    // 선택된 모든 이슈에 존재하는 라벨인 경우 항목 감춤
                    if(htLabel.issues.length === nLength){
                        htElement.welAttachLabels.find('[data-value="' + sLabelId + '"]').hide();
                    }
                } // end-for-label
                
                bVisible = _getLabelCategoryVisibility(htElement.welAttachLabels, sCategory) || bVisible;
            } // end-for-category

            // 선택한 이슈에 라벨이 있으면서
            // 모든 카테고리가 보여줄 항목이 없다면 라벨 추가 버튼 자체를 비활성화
            // 아예 라벨이 없는 경우라면 라벨 추가 버튼은 당연히 활성화
            htElement.welBtnAttachingLabel.attr("disabled", htLabels.hasOwnProperty() ? !bVisible : false);
        }
        
        /**
         * Hide category itself if all items are invisible
         * 
         * @param {Wrapped Element} welList Target Label List
         * @param {String} sCategory CategoryName
         * 
         * @return {Boolean} Returns does category visible
         */
        function _getLabelCategoryVisibility(welList, sCategory){
            var welItem;
            var bHidden = true;
            var waCategoryItems = welList.find('li[data-category="' + sCategory +'"]');
            
            waCategoryItems.each(function(i, el){
                welItem = $(el);
                
                if(typeof welItem.data("value") !== "undefined"){
                    bHidden = bHidden && (welItem.css("display") === "none");
                }
            });
            
            if(bHidden){
                waCategoryItems.hide();
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
            var sTpl = $("#labelListItem").text();
            
            // Category
            for(sCategory in htLabels){
                aHTML.push('<li class="disabled" data-category="' + sCategory + '"><span>' + sCategory + '</span></li>');
                
                // Label
                for(sLabelId in htLabels[sCategory]){
                    htLabel = htLabels[sCategory][sLabelId];
                    aHTML.push($yobi.tmpl(sTpl, htLabel));
                }
                
                aHTML.push('<li class="divider"></li>');
            }
            
            // 선택한 항목에서 삭제할 라벨이 있는 경우에만 활성화
            if(aHTML.length > 0){
                htElement.welDetachLabels.html(aHTML.join("\n"));
            } else {
                htElement.welBtnDetachingLabel.attr("disabled", true);
            }
        }
        
        /**
         * Get labels by checked issue item
         * 
         * @param {Wrapped Array} waChecked
         * @return {Hash Table}
         */
        function _getLabelsByChecked(waChecked){
            var htLabels = {};
            var welCheck, sIssueLabels, aIssueLabels, aLabel;
            var sCategory, sLabelId, sLabelName;
            
            waChecked.each(function(i, el){
                welCheck = $(el);
                sIssueLabels = welCheck.data("issueLabels");
                
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
                    
                    htLabels[sCategory] = htLabels[sCategory] || {}; // category
                    htLabels[sCategory][sLabelId] = htLabels[sCategory][sLabelId] || {"id":sLabelId, "name":sLabelName, "category":sCategory}; // label
                    htLabels[sCategory][sLabelId].issues = htLabels[sCategory][sLabelId].issues || []; // issues to count
                    htLabels[sCategory][sLabelId].issues.push(welCheck.data("issue-id"));
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

            // 어떤 항목을 보고 있었다는걸 확인할 수 있다면
            // 폼 ActionURL 뒤에 Hash 를 붙여 최대한 그 위치에 가깝게 다시 돌아오게 만든다
            if(sItemId){
                welForm.attr("action", htVar.sActionURL + "#" + sItemId);
            }

            // 적용할 이슈 항목들을 폼 필드로 추가
            $(htVar.sIssueCheckedBoxesSelector).each(function(){
                _addFormField(
                    welForm,
                    'issues[' + (nCnt++) + '].id',
                    $(this).data('issue-id')
                );
            });

            welForm.submit();
        }

        /**
         * 현재 스크롤 높이를 기준으로 현재 보고 있는 이슈 항목(.post-item)의 ID를 추측하여 반환한다
         * 첫번째 항목이 화면에 보이고 있는 경우 undefined 가 반환될 수 있다
         *
         * @returns {*|jQuery}
         * @private
         */
        function _getCurrentItemIdByScrollTop(){
            var nScrollTop = $(window).scrollTop();
            var sItemId = $(".post-item").filter(function(i,el){
                return ($(el).offset().top > nScrollTop);
            }).first().prev().attr("id");

            return sItemId;
        }

        /**
         * 일괄 업데이트 폼이 스크롤해도 계속 따라다니도록 설정하는 함수
         */
        function _setMassUpdateFormAffixed(){
            htVar.nMassUpdateTop = htElement.welMassUpdateForm.offset().top + (htElement.welMassUpdateForm.height() / 2) - 20;

            _updateMassUpdateFormFixation();
            $(window).on("scroll", _updateMassUpdateFormFixation);
        }

        /**
         * 현재 스크롤 높이에 따라 일괄 업데이트 폼의 고정 여부를 업데이트 한다
         * @private
         */
        function _updateMassUpdateFormFixation(){
            if($(window).scrollTop() > htVar.nMassUpdateTop){
                htElement.welMassUpdateForm.addClass("fixed");
            } else {
                htElement.welMassUpdateForm.removeClass("fixed");
            }
        }

        _init(htOptions);
    };
    
})("yobi.issue.MassUpdate");
