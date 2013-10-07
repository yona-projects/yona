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

        function _initVar() {
            htVar.url = htOptions.url;
            htVar.oFromBranch  = new yobi.ui.Dropdown({"elContainer": htOptions.welFromBranch});
            htVar.oToBranch  = new yobi.ui.Dropdown({"elContainer": htOptions.welToBranch});
            
        }
        
        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = $("form.nm");
            htElement.welInputTitle = $('#title');
            htElement.welInputBody  = $('#body');
            
            
            htElement.welInputFromBranch = $('input[name="fromBranch"]');
            htElement.welInputToBranch = $('input[name="toBranch"]');
            
            htElement.welUploader = $("#upload");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welForm.submit(_onSubmitForm);
            
            htVar.oFromBranch.onChange(_reloadNewPullRequest);
            htVar.oToBranch.onChange(_reloadNewPullRequest);
            
            $('#helpMessage').hide();
            $('#helpBtn').click(function(e){
                e.preventDefault();
                $('#helpMessage').toggle();
            });
            
            $('body').on('click','button.more',function(){
               $(this).next('pre').toggleClass("hidden"); 
            });
            
           
        }

        function _reloadNewPullRequest() {
            var data = {};
            data.fromBranch = htVar.oFromBranch.getValue();
            data.toBranch = htVar.oToBranch.getValue();
            
            if(!(data.fromBranch && data.toBranch)) {
                return;
            }
            
            var target = document.getElementById('spin');
            spinner = new Spinner().spin(target);
            
            $.ajax(htVar.url, {
                "method" : "get",
                "data"   : data,
                "success" : function(response) {
                    spinner.stop();
                    $("div[pjax-container]").html(response);
                   _initElement();
                   _initFileUploader();
                },
                "error"  : function() {
                    spinner.stop();
                    $yobi.showAlert(Messages("error.internalServerError"));
                }
            });
        }
        
        /**
         * on submit form
         */
        function _onSubmitForm(weEvt){
            return _validateForm();
        }

        function _validateForm(){
            var sTitle = $.trim(htElement.welInputTitle.val());
            var sBody = $.trim(htElement.welInputBody.val());
            // these two fields should be loaded dynamically.
            htElement.welInputFromBranch = $('input[name="fromBranch"]');
            htElement.welInputToBranch = $('input[name="toBranch"]');
            var sFromBranch = $.trim(htElement.welInputFromBranch.val());
            var sToBranch = $.trim(htElement.welInputToBranch.val());

            if(sTitle.length === 0){
                $yobi.alert(Messages("pullRequest.title.required"));
                return false;
            }

            if(sBody.length === 0){
                $yobi.alert(Messages("pullRequest.body.required"));
                return false;
            }

            if(sFromBranch.length === 0){
                $yobi.alert(Messages("pullRequest.fromBranch.required"));
                return false;
            }

            if(sToBranch.length === 0){
                $yobi.alert(Messages("pullRequest.toBranch.required"));
                return false;
            }

            return true;
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welInputBody);
            var sUploaderId = oUploader.attr("data-namespace");
            
            (new yobi.Attachments({
                "elContainer"  : htElement.welUploader,
                "elTextarea"   : htElement.welInputBody,
                "sTplFileItem" : $('#tplAttachedFile').text(),
                "sUploaderId"  : sUploaderId
            }));
        }
        
        _init(htOptions || {});
    };

})("yobi.git.Write");