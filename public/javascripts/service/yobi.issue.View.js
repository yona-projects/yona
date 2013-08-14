/**
 * @(#)yobi.issue.View.js 2013.03.13
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
		 * @param {Hash Table} htOptions
		 */
		function _init(htOptions){
			_initVar(htOptions || {});
			_initElement(htOptions || {});
            _attachEvent();

			_initFileUploader();
			_initFileDownloader();
			_setLabelTextColor();
		}

		/**
		 * initialize variables except HTML Element
		 */
		function _initVar(htOptions){
            htVar.sTplFileItem = $('#tplAttachedFile').text();

		    htVar.sIssueId = htOptions.sIssueId;
		    htVar.sIssuesUrl = htOptions.sIssuesUrl;
		    
			htVar.sUploadUrl  = htOptions.sUploadUrl;
			htVar.sFilesUrl   = htOptions.sFilesUrl;
            htVar.sWatchUrl   = htOptions.sWatchUrl;
            htVar.sUnwatchUrl = htOptions.sUnwatchUrl;
            
            htVar.oAssignee  = new yobi.ui.Dropdown({"elContainer": htOptions.welAssignee});
            htVar.oMilestone = new yobi.ui.Dropdown({"elContainer": htOptions.welMilestone});
		}
		
		/**
		 * initialize HTML Element variables
		 */
		function _initElement(htOptions){
			htElement.welUploader = $("#upload");
			htElement.welTextarea = $("#comment-editor");
			
			htElement.welAttachments = $(".attachments");
			htElement.welLabels = $('.issue-label');
            htElement.welBtnWatch = $('#watch-button');
            
            htElement.welIssueUpdateForm = htOptions.welIssueUpdateForm;
            htElement.sIssueCheckBoxesSelector = htOptions.sIssueCheckBoxesSelector;
            
            htElement.welChkIssueOpen = $("#issueOpen");
		}

        /**
         * attach event handler
         */
        function _attachEvent(){
            htElement.welBtnWatch.click(function(weEvt) {
                var welTarget = $(weEvt.target);
                var bWatched = welTarget.hasClass("watching");

                $yobi.sendForm({
                    "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                    "fOnLoad": function(){
                        welTarget.toggleClass("watching");
                        welTarget.html(Messages(welTarget.hasClass("watching") ? "project.unwatch" : "project.watch"));
                    }
                });
            });

            htVar.oMilestone.onChange(_onChangeUpdateField);
            htVar.oAssignee.onChange(_onChangeUpdateField);
            
            htElement.welChkIssueOpen.change(_onChangeIssueOpen);
        }
        
        /**
         * 이슈 해결/미해결 스위치 변경시
         */
        function _onChangeIssueOpen(){
            var welTarget  = $(this);
            var bChecked   = welTarget.prop("checked");
            var sNextState = bChecked ? "OPEN" : "CLOSED";
            
            $.ajax(htVar.sIssuesUrl, {
                "method": "post",
                "data": {
                    "issues[0].id": htVar.sIssueId,
                    "state": sNextState
                },
                "success": function(){
                    welTarget.prop("checked", bChecked);
                },
                "error" : function(){
                    welTarget.prop("checked", !bChecked);
                    $yobi.notify(Messages("error.internalServerError"));
                }
            });
        }
        
        /**
         * 이슈 즉시 수정 폼 변경시 이벤트 핸들러
         */
        function _onChangeUpdateField() {
            htElement.welIssueUpdateForm.submit();
        }
        
		/**
		 * initialize fileUploader
		 */
		function _initFileUploader(){
		    yobi.FileUploader.init({
				"elContainer" : htElement.welUploader,
				"elTextarea"  : htElement.welTextarea,
				"sTplFileItem": htVar.sTplFileItem,
				"sAction"     : htVar.sUploadUrl
			});
		}
		
		/**
		 * initialize fileDownloader
		 */
		function _initFileDownloader(){
			htElement.welAttachments.each(function(n, el){
				fileDownloader($(el), htVar.sFilesUrl);
			});
		}
        
		/**
		 * set Labels foreground color as contrast to background color 
		 */
		function _setLabelTextColor(){
			var welLabel;
			var sBgColor, sColor;
			
			htElement.welLabels.each(function(nIndex, elLabel){
				welLabel = $(elLabel);
				sBgColor = welLabel.css("background-color");
				sColor = $yobi.getContrastColor(sBgColor); 
				welLabel.css("color", sColor);
			});
			
			welLabel = null;
		}
		
        _init(htOptions);
	};
	
})("yobi.issue.View");