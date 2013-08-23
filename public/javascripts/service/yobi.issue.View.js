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
            // 지켜보기
            htElement.welBtnWatch.click(_onClickBtnWatch);
            
            // 이슈 정보 업데이트
            htElement.welChkIssueOpen.change(_onChangeIssueOpen);
            htVar.oMilestone.onChange(_onChangeMilestone);
            htVar.oAssignee.onChange(_onChangeAssignee);
        }
        
        /**
         * 지켜보기 버튼 클릭시 이벤트 핸들러
         * 
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnWatch(weEvt){
            var welTarget = $(weEvt.target);
            var bWatched = (welTarget.attr("data-watching") == "true") ? true : false;
            
            $yobi.sendForm({
                "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                "fOnLoad": function(){
                    welTarget.attr("data-watching", !bWatched);
                    welTarget.html(Messages(!bWatched ? "project.unwatch" : "project.watch"));
                    $yobi.notify(Messages(bWatched ? "issue.unwatch.start" : "issue.watch.start"), 3000);
                }
            });
        }
        
        /**
         * 이슈 해결/미해결 스위치 변경시
         */
        function _onChangeIssueOpen(){
            var welTarget  = $(this);
            var bChecked   = welTarget.prop("checked");
            var sNextState = bChecked ? "OPEN" : "CLOSED";
            
            _requestUpdateIssue({
               "htData" : {"state": sNextState},
               "fOnLoad": function(){
                    welTarget.prop("checked", bChecked);
                },
               "fOnError": function(){
                    welTarget.prop("checked", !bChecked);
                    _onErrorRequest();
               }
            });
        }
        
        /**
         * 담당자 변경시
         * 
         * @param {String} sValue 선택된 항목의 값
         */
        function _onChangeAssignee(sValue){
            _requestUpdateIssue({
               "htData"  : {"assignee.id": sValue},
               "fOnLoad" : function(){
                   $yobi.notify(Messages("issue.update.assignee"), 3000);
                   htVar.oAssignee.selectItem("li[data-id=" + sValue + "]");
               },
               "fOnError": _onErrorRequest
            });
        }
        
        /**
         * 마일스톤 변경시
         * 
         * @param {String} sValue 선택된 항목의 값
         */
        function _onChangeMilestone(sValue){
            _requestUpdateIssue({
               "htData"  : {"milestone.id": sValue},
               "fOnLoad" : function(){
                   $yobi.notify(Messages("issue.update.milestone"), 3000);
               },
               "fOnError": _onErrorRequest
            });
        }
        
        /**
         * 이슈 정보 업데이트 AJAX 호출
         * 
         * @param {Hash Table} htOptions
         */
        function _requestUpdateIssue(htOptions){
            var htReqData = {"issues[0].id": htVar.sIssueId};
            for(var sKey in htOptions.htData){
                htReqData[sKey] = htOptions.htData[sKey];
            }
            
            $.ajax(htVar.sIssuesUrl, {
                "method" : "post",
                "data"   : htReqData,
                "success": htOptions.fOnLoad,
                "error"  : htOptions.fOnError
            });
        }
        
        /**
         * 이슈 정보 업데이트 호출 실패시
         */
        function _onErrorRequest(){
            $yobi.alert(Messages("error.internalServerError"));
        }
        
		/**
		 * initialize fileUploader
		 */
		function _initFileUploader(){
			var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welTextarea);
			var sUploaderId = oUploader.attr("data-namespace");
			
			(new yobi.Attachments({
			    "elContainer"  : htElement.welUploader,
			    "elTextarea"   : htElement.welTextarea,
			    "sTplFileItem" : htVar.sTplFileItem,
			    "sUploaderId"  : sUploaderId
			}));
		}
		
		/**
		 * initialize fileDownloader
		 */
		function _initFileDownloader(){
			htElement.welAttachments.each(function(i, elContainer){
    			(new yobi.Attachments({"elContainer": elContainer}));
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
