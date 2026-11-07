/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         */
        function _init(htOptions){
            _initVar(htOptions);
            _initElement(htOptions);
            _initDatePicker();
            _attachEvent();
            _initFileUploader();

            htElement.welInputTitle.focus();
            htElement.welInputTitle.on('keydown', function (e) {
                if((e.keyCode || e.which) === 13) {
                    e.preventDefault();
                    htElement.welInputContent.focus();
                }
            });

        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.sDateFormat  = htOptions.sDateFormat  || "YYYY-MM-DD";
            htVar.rxDateFormat = htOptions.rxDateFormat || /\d{4}-\d{2}-\d{2}$/;
            htVar.sTplFileItem = $('#tplAttachedFile').text();
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = $("#milestone-form");
            htElement.welDatePicker   = $(htOptions.elDatePicker);
            htElement.welInputDueDate = $(htOptions.elDueDate);
            htElement.welInputTitle   = $('#title');
            htElement.welInputContent = $('textarea[data-editor-mode="content-body"]');
            htElement.welUploader = $(htOptions.elUploader || "#upload");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            temporarySaveHandler(htElement.welInputContent);
            htElement.welForm.submit(_onSubmitForm);
        }

        /**
         * on submit form
         */
        function _onSubmitForm(weEvt){
            removeCurrentPageTemprarySavedContent();
            return _validateForm();
        }

        function _validateForm(){
            var sTitle = $.trim(htElement.welInputTitle.val());
            var sContent = $.trim(htElement.welInputContent.val());
            var sDueDate = $.trim(htElement.welInputDueDate.val());

            if(sTitle.length === 0){
                $yobi.showAlert(Messages("milestone.error.title"));
                return false;
            }

            if(sContent.length === 0){
                $yobi.showAlert(Messages("milestone.error.content"));
                return false;
            }

            if(sDueDate.length > 0 && htVar.rxDateFormat.test(sDueDate) === false){
                $yobi.showAlert(Messages("milestone.error.duedateFormat"));
                return false;
            }

            return true;
        }

        /**
         * initialize DatePicker
         * @requires Pikaday
         */
        function _initDatePicker(){
            if(typeof Pikaday != "function"){
                console.log("[Yobi] Pikaday required (https://github.com/dbushell/Pikaday)");
                return false;
            }

            // append Pikaday element to DatePicker
            htVar.oPicker = new Pikaday({
                "format" : htVar.sDateFormat,
                "onSelect" : function(oDate) {
                    htElement.welInputDueDate.val(this.toString());
                }
            });
            htElement.welDatePicker.append(htVar.oPicker.el);

            // fill DatePicker date to InputDueDate if empty
            // or set DatePicker date with InputDueDate
            var sDueDate = htElement.welInputDueDate.val();
            if(sDueDate.length > 0){
                htVar.oPicker.setDate(sDueDate);
            }

            // set relative event between dueDate input and datePicker
            htElement.welInputDueDate.blur(function() {
                htVar.oPicker.setDate(this.value);
            });
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welInputContent);

            if(oUploader){
                (new yobi.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welInputContent,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader.attr("data-namespace")
                }));
            }
        }

        _init(htOptions || {});
    };

})("yobi.milestone.Write");
