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
         * 초기화
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _initVar(htOptions || {});
            _attachEvent();
            _initFileUploader();
            
            // 제목 입력란에 포커스
            htElement.welInputTitle.focus();

            // zenForm
            _initZenForm();
        }

        /**
         * 변수 초기화
         * initialize variable
         */
        function _initVar(htOptions){
            htVar.sMode = htOptions.sMode || "new";
            htVar.sIssueId = htOptions.sIssueId || null;
            htVar.sIssueListURL = htOptions.sIssueListURL;
            htVar.sIssueFormURL = htOptions.sIssueFormURL;
            htVar.sTplFileItem = htOptions.sTplFileItem || htElement.welTplFileItem.text();
            htVar.sTplRelIssueItem = htOptions.sTplRelIssueItem || htElement.welTplRelIssueItem.text();
            htVar.nKeywordWatcher = null;
            htVar.bUnloadEvent = false;
            htVar.bOnRequestRelIssue = false;
            htVar.sLastHaystack = "";
        }
        
        /**
         * 엘리먼트 변수 초기화
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
            htElement.welTplRelIssueItem = $("#tplRelIssue");
            htElement.welRelativeIssueWrap = $("#relativeIssue");
            htElement.welRelativeIssueList = htElement.welRelativeIssueWrap.find("ul");
        }

        /**
         * 이벤트 핸들러
         * attach event handler
         */
        function _attachEvent(){
            $("form").submit(_onSubmitForm);
            htElement.welIssueOptions.on("click", htElement.welMilestoneRefresh, _onReloadMilestone);
            
            htElement.welTextarea.on({
                "focus": function(){
                    if(htVar.bUnloadEvent === false){
                        $(window).on("beforeunload", _onBeforeUnload);
                        htVar.bUnloadEvent = true;
                    }
                    _startKeywordWatcher();
                },
                "blur" : _stopKeywordWatcher
            });
            htElement.welInputTitle.on({
                "focus": _startKeywordWatcher,
                "blur" : _stopKeywordWatcher
            });
        }

        /**
         * 입력하던 도중 페이지를 벗어나려고 하면 경고 메시지를 표시하도록
         */
        function _onBeforeUnload(){
            if($yobi.getTrim(htElement.welTextarea.val()).length > 0){
                return Messages("issue.error.beforeunload");
            }
        }
        
        /**
         * 라벨 관리 버튼 클릭시
         */
        function _clickBtnManageLabel() {
            htVar.htOptLabel.bEditable = !htVar.htOptLabel.bEditable;
            _initLabel(htVar.htOptLabel);
        }

        /**
         * 마일스톤 정보 새로고침
         */
        function _onReloadMilestone() {
            $.get(htVar.sIssueFormURL, function(data){
                var context = data.replace("<!DOCTYPE html>", "").trim();
                var milestoneOptionDiv = $("#milestoneOption", context);
                $("#milestoneOption").html(milestoneOptionDiv.html());
                (new yobi.ui.Dropdown({"elContainer":"#milestoneId"}));
            });
        }
        
        /**
         * 파일 업로더 초기화
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welTextarea);
            
            if(oUploader){
                (new yobi.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader.attr("data-namespace")
                }));
            }
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
                        
            $(window).off("beforeunload", _onBeforeUnload);
            return true;
        }

        /**
         * ZenForm 초기화
         * initialize zenForm
         */
        function _initZenForm(){
            $(".zen-mode").zenForm({"theme": "light"});
            $(".s--zen").tooltip({
                "delay": {"show": 500, "hide": 100},
                "title": Messages("title.zenmode"),
                "placement": "left"
            });
        }
        
        /**
         * 키워드 추출 타이머 시작
         */
        function _startKeywordWatcher(){
            if(htVar.nKeywordWatcher != null){
                return; // 이미 시작한 경우
            }
            
            htVar.nKeywordWatcher = setInterval(function(){
                var sHaystack = htElement.welInputTitle.val() + " " + htElement.welTextarea.val();
                
                // 입력값이 변할 때에만 요청
                if(htVar.sLastHaystack != sHaystack){
                    _getRelativeIssue();
                    htVar.sLastHaystack = sHaystack;
                }
                
                // 필드가 그냥 비어있는 경우
                if(sHaystack === " "){
                    htElement.welRelativeIssueWrap.hide();
                }
            }, 1000);
        }
        
        /**
         * 키워드 추출 타이머 중단
         */
        function _stopKeywordWatcher(){
            if(htVar.nKeywordWatcher != null){
                clearInterval(htVar.nKeywordWatcher);
            }
            htVar.nKeywordWatcher = null;
        }
        
        /**
         * 지금 등록중인 이슈와 유사한 이슈를 찾아준다
         * 제목 + 본문을 기준으로 키워드를 판단하고,
         * 기존에 등록된 이슈 중에서 검색한다
         */
        function _getRelativeIssue(){
            var sHaystack = htElement.welInputTitle.val() + " " + htElement.welTextarea.val();
            if(htVar.bOnRequestRelIssue || (sHaystack.length < 2)){
                return;
            }
            
            var aKeywords = _extractKeyword(sHaystack);
            var sKeyword = aKeywords.pop();

            // 마땅한 키워드가 없는 경우
            if(!sKeyword){
                htVar.bOnRequestRelIssue = false;
                htElement.welRelativeIssueWrap.hide();
                return;
            }
            
            // 이미 요청한 상태라면 반복 요청하지 않기 위해
            htVar.bOnRequestRelIssue = true;
            $.get(htVar.sIssueListURL, {
                "state"   : "all",
                "filter"  : sKeyword,
                "exceptId": htVar.sIssueId,
                "itemsPerPage": 5
            }, _onLoadRelativeIssue);
        }
        
        function _onLoadRelativeIssue(htData){
            htVar.bOnRequestRelIssue = false;
            
            var aResult = [];
            for(var sKey in htData){
                aResult.push($yobi.tmpl(htVar.sTplRelIssueItem, htData[sKey]));
            }
            
            htElement.welRelativeIssueWrap[(aResult.length > 0) ? "show":"hide"]();
            htElement.welRelativeIssueList.html(aResult.join(""));
        }
        
        /**
         * 문자열에서 키워드 추출
         * 빈도수 낮은 순에서 높은 순으로
         * 
         * @param {String} sStr
         * @return {Array}
         */
        function _extractKeyword(sStr) {
            var aKeywords = [];
            var htKeyword = {};

            // 키워드 추출
            sStr.split(" ").forEach(function(s){
                var sWord = _trimKeyword(s).toLowerCase();

                if(sWord.length > 1) {
                    htKeyword[sWord] = htKeyword[sWord] || 0;
                    htKeyword[sWord]++;
                    
                    if(aKeywords.indexOf(sWord) < 0) {
                        aKeywords.push(sWord);
                    }
                }
            });

            // 키워드 빈도순 정렬
            aKeywords.sort(function(a, b){
                return htKeyword[a] - htKeyword[b];
            });

            return aKeywords;
        }

        /**
         * 검색하기 적당한 키워드로 만든다
         * - 한국어: 예상 가능한 접미어를 찾아 제거한 단어 반환
         * - 영  어: 유의미한 검색결과를 얻기 어려운 단어 배제
         * - 일본어: 미지원
         * 
         * @param {String} sStr
         * @return {String}
         */
        function _trimKeyword(sStr) {
            var sResult = sStr;
            htVar.rxTrimKr = htVar.rxTrimKr || /[은|는|이|가|을|를|음|수|할|하기|의|에|께|서|에서|시|도|니다|면|으면|있|었|함|듯|다|으로|로]$/;
            htVar.aTrimEn = htVar.aTrimEn || ["of", "as", "it", "a", "an", "or", "for", "while", "on", "at", "in", "this", "that", "is", "are", "were", "by", "you", "the", "and", "to"];
            
            // 한국어: 반복적 확인하며 접미어를 깎아나간다
            while(htVar.rxTrimKr.test(sResult)){
                sResult = sResult.replace(htVar.rxTrimKr, '');
            }
            
            // 영어: 유의미한 검색결과를 얻기 어려운 단어는 배제한다
            sResult = (htVar.aTrimEn.indexOf(sStr.toLowerCase()) > -1) ? '' : sResult;
            
            return sResult;
        }

        _init(htOptions);
    };
})("yobi.issue.Write");
