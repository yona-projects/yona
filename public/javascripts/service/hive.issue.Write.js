/**
 * @(#)hive.Issue.Write.js 2013.03.13
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){
		
		var htVar = {};
		var htElement = {};
		
		/**
		 * initialize
		 */
		function _init(htOptions){
			_initElement(htOptions || {});
			_initVar(htOptions || {});
			_attachEvent();
			
			_initFileUploader();
			
			if(typeof htOptions.htOptLabel == "object"){
				_initLabel(htOptions.htOptLabel);
			}
		}
		
		/**
		 * initialize variable
		 */
		function _initVar(htOptions){
			htVar.sMode = htOptions.sMode || "new";
      htVar.sIssueFormURL = htOptions.sIssueFormURL;
      htVar.sMilestoneRefresh = htOptions.sMilestoneRefresh;
			htVar.sUploaderAction = htOptions.sUploaderAction;
			htVar.sTplFileItem = htOptions.sTplFileItem || (htElement.welTplFileItem ? htElement.welTplFileItem.text() : "");
            htVar.htOptLabel = htOptions.htOptLabel || {};
		}
		
		/**
		 * initialize element variable
		 */
		function _initElement(htOptions){
			htElement.welUploader = $(htOptions.elUploader || "#upload");
			htElement.welTextarea = $(htOptions.elTextarea || "#body");
			htElement.welInputTitle = $(htOptions.elInputTitle || "#title");
			htElement.welBtnManageLabel = $(htOptions.welBtnManageLabel || "#manage-label-link");

			htElement.welTplFileItem = $('#tplAttachedFile');			
		}
			
		/**
		 * attach event handler
		 */
		function _attachEvent(){
			$("form").submit(_onSubmitForm);
            htElement.welBtnManageLabel.click(_clickBtnManageLabel);
      $("body").on("click", htVar.sMilestoneRefresh, _reloadMilestone);
      $(htVar.sMilestoneRefresh).click(_reloadMilestone);
		}

        function _clickBtnManageLabel() {
            htVar.htOptLabel.bEditable = !htVar.htOptLabel.bEditable;
            _initLabel(htVar.htOptLabel);
        }

        function _reloadMilestone() {
          $.get(htVar.sIssueFormURL, function(data){
            var context = data.replace("<!DOCTYPE html>", "").trim();
            var milestoneOptionDiv = $("#milestoneOption", context);
            $("#milestoneOption").html(milestoneOptionDiv.html());
            new hive.ui.Dropdown({"elContainer":"#milestoneId"});
          });
        }
		
		/**
		 * initialize fileUploader
		 */
		function _initFileUploader(){
			hive.FileUploader.init({
                "sMode"       : htVar.sMode,
                "sAction"     : htVar.sUploaderAction,
                "sTplFileItem": htVar.sTplFileItem,
			  	"elContainer" : htElement.welUploader,
			  	"elTextarea"  : htElement.welTextarea
			});
		}
		
		/**
		 * 지정한 라벨들을 활성화 상태로 표시
		 * @param {Hash Table} htActiveLabels
		 * @example
		 * htActiveLabels["labelId"] = "labelColor";
		 */
		function _initLabel(htOptions){
			htOptions.fOnLoad = function(){
				var sKey;
				for(sKey in htOptions.htActive){
					hive.Label.setActiveLabel(sKey, htOptions.htActive[sKey]);
				}
			};
			
			hive.Label.init(htOptions);
		}
		
		/**
		 * 폼 전송시 유효성 검사 함수
		 */
		function _onSubmitForm(){
			var sTitle = $hive.getTrim(htElement.welInputTitle.val());
			var sBody = $hive.getTrim(htElement.welTextarea.val());
			
			// 제목이 비어있으면
			if(sTitle.length < 1){
				$hive.showAlert(Messages("issue.error.emptyTitle"), function(){
					htElement.welInputTitle.focus();
				});
				return false;
			}
			
			// 본문이 비어있으면
			if(sBody.length < 1){
				$hive.showAlert(Messages("issue.error.emptyBody"), function(){
					htElement.welTextarea.focus();
				});
				return false;
			}
			
			return true;
		}
		
		_init(htOptions);
	};
	
})("hive.issue.Write");