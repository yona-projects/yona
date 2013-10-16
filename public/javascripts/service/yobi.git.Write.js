/**
 * @(#)yobi.git.Write.js 2013.08.18
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
            
            _initFileUploader();
        }

        /**
         * 변수 초기화
         * initialize variables
         */
        function _initVar() {
            htVar.sFormURL = htOptions.sFormURL;
            htVar.oFromBranch  = new yobi.ui.Dropdown({"elContainer": htOptions.welFromBranch});
            htVar.oToBranch  = new yobi.ui.Dropdown({"elContainer": htOptions.welToBranch});
            htVar.sUploaderId = null;
            htVar.oSpinner = null;
            htVar.htUserInput = {};
            htVar.sTplFileItem = $('#tplAttachedFile').text();
        }
        
        /**
         * 엘리먼트 변수 초기화
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = $("form.nm");
            htElement.welInputTitle = $('#title');
            htElement.welInputBody  = $('#body');
            
            htElement.welInputFromBranch = $('input[name="fromBranch"]');
            htElement.welInputToBranch = $('input[name="toBranch"]');
            
            htElement.welUploader = $("#upload");
            htElement.welContainer = $("#frmWrap");
        }

        /**
         * 이벤트 핸들러 설정
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welForm.submit(_onSubmitForm);
            htElement.welInputTitle.on("keyup", _onKeyupInput);
            htElement.welInputBody.on("keyup", _onKeyupInput);
            
            htVar.oFromBranch.onChange(_reloadNewPullRequestForm);
            htVar.oToBranch.onChange(_reloadNewPullRequestForm);
            
            $('#helpMessage').hide();
            $('#helpBtn').click(function(e){
                e.preventDefault();
                $('#helpMessage').toggle();
            });
            
            $('body').on('click','button.more',function(){
               $(this).next('pre').toggleClass("hidden"); 
            });
        }

        /**
         * 제목/내용에 키 입력이 발생할 때의 이벤트 핸들러
         * 
         * @param {Wrapped Event} weEvt
         */
        function _onKeyupInput(weEvt){
            var welTarget = $(weEvt.target);
            var sInputId = welTarget.attr("id");
            htVar.htUserInput = htVar.htUserInput || {};
            htVar.htUserInput[sInputId] = true;
        }
        
        /**
         * 브랜치 선택이 바뀌면 폼 내용을 변경한다
         * request to reload pullRequestForm
         */
        function _reloadNewPullRequestForm(){
            var htData = {};
            htData.fromBranch = htVar.oFromBranch.getValue();
            htData.toBranch = htVar.oToBranch.getValue();
            
            if(!(htData.fromBranch && htData.toBranch)) {
                return;
            }
            
            _startSpinner();
            
            $.ajax(htVar.sFormURL, {
                "method" : "get",
                "data"   : htData,
                "success": _onSuccessReloadForm,
                "error"  : _onErrorReloadForm
            });
        }
        
        /**
         * onSuccess to reloadForm
         */
        function _onSuccessReloadForm(sRes){
            var sTitle = htElement.welInputTitle.val();
            var sBody = htElement.welInputBody.val();
            
            htElement.welContainer.html(sRes);
            _reloadElement();

            // 만약 사용자가 입력한 제목이나 본문이 있으면 내용을 유지한다
            if(sTitle.length > 0 && htVar.htUserInput.title){
                htElement.welInputTitle.val(sTitle);
            }
            if(sBody.length > 0 && htVar.htUserInput.body){
                htElement.welInputBody.val(sBody);
            }
            
            _initFileUploader();
            _stopSpinner();
        }
        
        /**
         * pjax 영역 변화에 의해 다시 찾아야 하는 엘리먼트 레퍼런스
         * _onSuccessReloadForm 에서 호출
         */
        function _reloadElement(){
            htElement.welInputTitle = $('#title');
            htElement.welInputBody  = $('#body');
            htElement.welUploader = $("#upload");
            
            htElement.welInputTitle.on("keyup", _onKeyupInput);
            htElement.welInputBody.on("keyup", _onKeyupInput);
        }

        /**
         * onFailed to reloadForm
         */
        function _onErrorReloadForm(){
            _stopSpinner();
            $yobi.showAlert(Messages("error.internalServerError"));
        }
        
        /**
         * Spinner 시작
         */
        function _startSpinner(){
            htVar.oSpinner = htVar.oSpinner || new Spinner();
            htVar.oSpinner.spin(document.getElementById('spin'));
        }
        
        /**
         * Spinner 종료
         */
        function _stopSpinner(){
            if(htVar.oSpinner){
                htVar.oSpinner.stop();
            }
            htVar.oSpinner = null;
        }
        
        /**
         * Event handler on submit form
         */
        function _onSubmitForm(weEvt){
            return _validateForm();
        }

        /**
         * 폼 유효성 검사
         * Validate form before submit
         */
        function _validateForm(){
            // these two fields should be loaded dynamically.
            htElement.welInputFromBranch = $('input[name="fromBranch"]');
            htElement.welInputToBranch = $('input[name="toBranch"]');
            
            // check whether required field is empty
            var htRequired = {
                "title"     : $.trim(htElement.welInputTitle.val()),
                "body"      : $.trim(htElement.welInputBody.val()),
                "fromBranch": $.trim(htElement.welInputFromBranch.val()),
                "toBranch"  : $.trim(htElement.welInputToBranch.val())
            };
            
            for(var sMessageKey in htRequired){
                if(htRequired[sMessageKey].length === 0){
                    $yobi.alert(Messages("pullRequest." + sMessageKey + ".required"));
                    return false;
                }
            }

            return true;
        }

        /**
         * 파일업로더 초기화
         * initialize fileUploader
         */
        function _initFileUploader(){
            // 이미 설정된 업로더가 있으면 제거하고 재설정
            // reloadNewPullRequest 에서 브랜치 선택할 때 마다 입력 영역이 변하기 때문
            if(htVar.sUploaderId){
                htVar.oAttachments.destroy();
                yobi.Files.destroyUploader(htVar.sUploaderId);
                htVar.sUploaderId = null;
            }
            
            // 업로더 초기화
            var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welInputBody);
            if(oUploader){
                htVar.sUploaderId = oUploader.attr("data-namespace");
                htVar.oAttachments = new yobi.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welInputBody,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : htVar.sUploaderId
                });
            }
        }
        
        _init(htOptions || {});
    };
})("yobi.git.Write");