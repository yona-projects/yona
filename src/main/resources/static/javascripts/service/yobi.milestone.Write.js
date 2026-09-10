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
         * @requires Flatpickr (https://flatpickr.js.org/) — Pikaday를 대체(P3-46 #1).
         *
         * 기존 Pikaday는 dueDate 입력 필드에 바인딩되지 않은("field" 옵션 없이 생성된) 독립
         * 인스턴스였다 — #datepicker 안에 항상 펼쳐진 상태로 렌더링되고, 값 동기화는 전부
         * onSelect 콜백 + dueDate blur 핸들러로 수동 처리했다. 이 아키텍처를 그대로 유지한다
         * (dueDate에 직접 바인딩하지 않는 이유: 그러면 Flatpickr 자체 blur 파싱이 잘못된 입력을
         * 만났을 때 필드를 강제로 비워버려 Pikaday의 "잘못된 값이면 조용히 무시" 동작과 달라짐).
         */
        function _initDatePicker(){
            if(typeof flatpickr != "function"){
                console.log("[Yobi] Flatpickr required (https://flatpickr.js.org/)");
                return false;
            }

            var sFlatpickrFormat = htVar.sDateFormat
                .replace("YYYY", "Y")
                .replace("MM", "m")
                .replace("DD", "d");

            // append Flatpickr calendar to DatePicker (element 자체가 field를 겸함 — 실제 input이
            // 아니므로 화면에 별도 표시되는 값은 없다. Pikaday의 "append(oPicker.el)"과 동일한
            // 위치에 인라인으로 렌더링되도록 appendTo를 같은 컨테이너로 지정한다)
            htVar.oPicker = flatpickr(htElement.welDatePicker.get(0), {
                "inline"    : true,
                "appendTo"  : htElement.welDatePicker.get(0),
                "dateFormat": sFlatpickrFormat,
                "onChange"  : function(selectedDates, dateStr) {
                    htElement.welInputDueDate.val(dateStr);
                }
            });

            // fill DatePicker date to InputDueDate if empty
            // or set DatePicker date with InputDueDate
            var sDueDate = htElement.welInputDueDate.val();
            if(sDueDate.length > 0){
                htVar.oPicker.setDate(sDueDate, true, sFlatpickrFormat);
            }

            // set relative event between dueDate input and datePicker
            htElement.welInputDueDate.blur(function() {
                // Pikaday.setDate()는 파싱 불가능한 문자열이면 조용히 무시하고 필드 값은 건드리지
                // 않았다. Flatpickr는 선택된 날짜가 없으면 자신에 바인딩된 필드 값을 비워버리므로
                // (여기서는 #datepicker 자신 — dueDate 입력이 아님 — 이라 직접적인 피해는 없지만),
                // onChange가 빈 문자열로 dueDate를 덮어쓰는 것을 막기 위해 유효한 날짜일 때만
                // setDate를 호출해 기존 동작을 재현한다.
                var sValue = this.value;
                if(sValue.length === 0 || !isNaN(Date.parse(sValue))){
                    htVar.oPicker.setDate(sValue, true, sFlatpickrFormat);
                }
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
