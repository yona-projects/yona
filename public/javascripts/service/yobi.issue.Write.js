/**
 * @(#)yobi.Issue.Write.js 2013.03.13
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
			_initElement(htOptions || {});
			_initVar(htOptions || {});
			_attachEvent();
			
			_initFileUploader();
			
            htElement.welInputTitle.focus();

            // zenForm
            $(".zen-mode").zenForm({"theme": "light"});
            $(".s--zen").tooltip({
                "delay": {"show": 500, "hide": 100},
                "title": Messages("title.zenmode"),
                "placement": "left"
            });
		}

		/**
		 * initialize variable
		 */
		function _initVar(htOptions){
			htVar.sMode = htOptions.sMode || "new";
            htVar.sIssueFormURL = htOptions.sIssueFormURL;
			htVar.sUploaderAction = htOptions.sUploaderAction;
			htVar.sTplFileItem = htOptions.sTplFileItem || (htElement.welTplFileItem ? htElement.welTplFileItem.text() : "");
		}
		
		/**
		 * initialize element variable
		 */
		function _initElement(htOptions){
			htElement.welUploader = $(htOptions.elUploader || "#upload");
            htElement.welIssueOptions = $(htOptions.elIssueOptions || "#options");
			htElement.welTextarea = $(htOptions.elTextarea || "#body");
			htElement.welInputTitle = $(htOptions.elInputTitle || "#title");
			htElement.welBtnManageLabel = $(htOptions.welBtnManageLabel || "#manage-label-link");
            htElement.welMilestoneRefresh = $(htOptions.elMilestoneRefresh || ".icon-refresh");
			htElement.welTplFileItem = $('#tplAttachedFile');
		}
			
		/**
		 * attach event handler
		 */
		function _attachEvent(){
			$("form").submit(_onSubmitForm);
            htElement.welIssueOptions.on("click", htElement.welMilestoneRefresh, _onReloadMilestone);
            
            htElement.welTextarea.on("focus", function(){
                $(window).on("beforeunload", _onBeforeUnload);
            });
		}

        function _onBeforeUnload(){
            if($yobi.getTrim(htElement.welTextarea.val()).length > 0){
                return Messages("issue.error.beforeunload");
            }
        }
        
        function _clickBtnManageLabel() {
            htVar.htOptLabel.bEditable = !htVar.htOptLabel.bEditable;
            _initLabel(htVar.htOptLabel);
        }

        function _onReloadMilestone() {
          $.get(htVar.sIssueFormURL, function(data){
            var context = data.replace("<!DOCTYPE html>", "").trim();
            var milestoneOptionDiv = $("#milestoneOption", context);
            $("#milestoneOption").html(milestoneOptionDiv.html());
            new yobi.ui.Dropdown({"elContainer":"#milestoneId"});
          });
        }
		
		/**
		 * initialize fileUploader
		 */
		function _initFileUploader(){
		    yobi.FileUploader.init({
                "sMode"       : htVar.sMode,
                "sAction"     : htVar.sUploaderAction,
                "sTplFileItem": htVar.sTplFileItem,
			  	"elContainer" : htElement.welUploader,
			  	"elTextarea"  : htElement.welTextarea
			});
		}
		
		/**
		 * 폼 전송시 유효성 검사 함수
		 */
		function _onSubmitForm(){
			var sTitle = $yobi.getTrim(htElement.welInputTitle.val());
			var sBody = $yobi.getTrim(htElement.welTextarea.val());
			
			// 제목이 비어있으면
			if(sTitle.length < 1){
				$yobi.showAlert(Messages("issue.error.emptyTitle"), function(){
					htElement.welInputTitle.focus();
				});
				return false;
			}
			
			// 본문이 비어있으면
			if(sBody.length < 1){
				$yobi.showAlert(Messages("issue.error.emptyBody"), function(){
					htElement.welTextarea.focus();
				});
				return false;
			}
			
			$(window).off("beforeunload", _onBeforeUnload);
			return true;
		}
		
		_init(htOptions);
	};
	
})("yobi.issue.Write");