/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

(function(ns){

    const oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        const htVar = {};
        const htElement = {};

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
            htElement.welInputTitle.on('keydown', (e) => {
                if($yona.isEnterKey(e)) {
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
            const sTitle = (htElement.welInputTitle.val() || "").trim();
            const sContent = (htElement.welInputContent.val() || "").trim();
            const sDueDate = (htElement.welInputDueDate.val() || "").trim();

            if(sTitle.length === 0){
                $yona.showAlert(Messages("milestone.error.title"));
                return false;
            }

            if(sContent.length === 0){
                $yona.showAlert(Messages("milestone.error.content"));
                return false;
            }

            if(sDueDate.length > 0 && htVar.rxDateFormat.test(sDueDate) === false){
                $yona.showAlert(Messages("milestone.error.duedateFormat"));
                return false;
            }

            return true;
        }

        /**
         * initialize DatePicker
         * 항상 보이는 달력을 붙인다. 고른 날짜는 마감일 입력란에 채우고, 입력란에 쓴 값은 blur에서 달력에 반영한다(yona.ui.Calendar.inline).
         * @requires vanilla-calendar-pro, yona.ui.Calendar
         */
        function _initDatePicker(){
            if(typeof VanillaCalendarPro != "object" || !yona.ui.Calendar || !yona.ui.Calendar.inline){
                console.log("[Yona] vanilla-calendar-pro and yona.ui.Calendar required");
                return false;
            }

            htVar.oPicker = yona.ui.Calendar.inline(htElement.welDatePicker, { "input": htElement.welInputDueDate });

            // 입력란에 값이 있으면 달력도 그 날짜로 맞춘다(입력란은 정규화된 값으로 다시 채워진다)
            const sDueDate = htElement.welInputDueDate.val();
            if(sDueDate.length > 0){
                htVar.oPicker.setDate(sDueDate);
            }
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            const oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welInputContent);

            if(oUploader){
                (new yona.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welInputContent,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader.attr("data-namespace")
                }));
            }
        }

        _init(htOptions || {});
    };

})("yona.milestone.Write");
