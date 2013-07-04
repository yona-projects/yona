/**
 * @(#)hive.user.Setting.js 2013.05.16
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
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initElement();
            _initVar(htOptions);
            _initFormValidator();
            _attachEvent();
        }

        /**
         * 엘리먼트 변수 초기화
         * initialize elements
         * @private
         */
        function _initElement(){
            htElement.welInputOldPassword  = $('#oldPassword');
            htElement.welInputPassword  = $('#password');
            htElement.welInputRetypedPassword = $('#retypedPassword');

            htElement.welFormBasic = $("#frmBasic");
            htElement.welFormPswd = $("#frmPassword");

            htElement.welAvatarWrap = $("#avatarWrap");
            htElement.welAvatarImage = htElement.welAvatarWrap.find("img");
            htElement.welAvatarProgress = htElement.welAvatarWrap.find(".progress");
            htElement.welAvatarFile = $("#avatarFile");
            htElement.welAvatarUploaded = $("#avatarUploaded");
        }

        /**
         * 변수 초기화
         * initialize variables
         * @private
         */
        function _initVar(htOptions){
            htVar.sURLUpload = htOptions.sURLUpload;
            htVar.sURLFiles = htOptions.sURLFiles;
            htVar.nProgressHeight = htElement.welAvatarWrap.height();
            htVar.htUploadOpts = {
                "error"         : _onErrorAvatarUpload,
                "success"       : _onAvatarUploaded,
                "beforeSubmit"  : _onBeforeAvatarUpload,
                "uploadProgress": _onAvatarUploading
            };

            /*
            $.ajax(htVar.sURLFiles, {
                "success": function(data){
                    console.log(data);
                }
            });
            */
        }

        /**
         * 이벤트 초기화
         * attach event
         * @private
         */
        function _attachEvent(){
            htElement.welInputOldPassword.focusout(_onBlurInputPassword);
            htElement.welInputPassword.focusout(_onBlurInputPassword);
            htElement.welInputRetypedPassword.focusout(_onBlurInputPassword);

            htElement.welAvatarFile.change(_onChangeAvatarFile);
        }

        /**
         * 아바타 변경 버튼을 눌러 파일을 선택한 경우
         * #avatarFile 의 change 이벤트 핸들러
         */
        function _onChangeAvatarFile(weEvt){
            var welForm = $('<form method="post" enctype="multipart/form-data" style="display:none">');
            var welFile = htElement.welAvatarFile.clone();

            welFile[0].files = htElement.welAvatarFile[0].files;
            welForm.attr('action', htVar.sURLUpload);
            welForm.append(welFile).appendTo(document.body);
            welForm.ajaxForm(htVar.htUploadOpts);

            try {
                welForm.submit();
            } finally {
                welFile.remove();
                welForm.remove();
                welForm = welFile = null;
            }
        }

        /**
         * 아바타 이미지 업로드하기 전에 점검하는 함수
         * ajaxForm 의 beforeSubmit 이벤트 핸들러
         * @return {Boolean}
         */
        function _onBeforeAvatarUpload(){
            // 선택된 파일명이 없으면 false 반환
            return !(htElement.welAvatarFile.val().length === 0);
        }

        /**
         * 아바타 이미지 업로드에 실패한 경우.
         * 일반적으로 서버 연결에 실패했을 때 이 상황이 발생한다.
         * ajaxForm 의 error 이벤트 핸들러.
         */
        function _onErrorAvatarUpload(sMessage){
            if (sMessage) {
                $hive.showAlert(sMessage);
            } else {
                $hive.showAlert("아바타 이미지를 업로드 할 수 없었습니다.\n관리자에게 문의해주세요");
            }
            _setAvatarProgressBar(0);
        }

        /**
         * 아바타 이미지 업로드 진행 상태
         * ajaxForm 의 uploadProgress 이벤트 핸들러
         * @param {Wrapped Event} weEvt
         * @param {Number} nPosition
         * @param {Number}
         */
        function _onAvatarUploading(weEvt, nPosition, nTotal, nPercent){
            _setAvatarProgressBar(nPercent);
            htElement.welAvatarProgress.css("opacity", 1);
        }

        /**
         * 아바타 이미지 업로드가 완료된 후
         * ajaxForm 의 success 이벤트 핸들러
         * @param {Hash Table} htData 업로드 된 파일의 정보
         */
        function _onAvatarUploaded(htData){
            if(typeof htData != "object" || !htData.url){
                _onErrorAvatarUpload();
                return false;
            }

            if(htData.mimeType.split("/")[0].toLowerCase() != 'image') {
                _onErrorAvatarUpload(Messages("user.avatar.onlyImage"));
                return false;
            }

            htElement.welAvatarImage.attr("src", htData.url);
            htElement.welFormBasic.append($("<input>").attr({
                "type": "hidden",
                "name": "avatarId",
                "value": htData.id}));
            _setAvatarProgressBar(100);
        }

        /**
         * 아바타 이미지 업로드 진행 상태 표시 함수
         * @param {Number} nPercent
         */
        function _setAvatarProgressBar(nPercent){
            nPercent = parseInt(nPercent, 10);
            var nHeight = parseInt(htVar.nProgressHeight * (nPercent / 100), 10);
            htElement.welAvatarProgress.height(nHeight);

            // 꽉 차면 보이지 않게 하고 다시 0으로 되돌림
            if(nPercent === 100){
//                htElement.welAvatarUploaded.show();
                htElement.welAvatarProgress.css("opacity", 0);
                setTimeout(function(){
                    htElement.welFormBasic.submit();
                    _setAvatarProgressBar(0);
                }, 1000);
            }
        }

        /**
         * 비밀번호 확인 입력란 벗어날 때 이벤트 핸들러
         * 마지막 입력란이므로 전체 폼 유효성 검사
         * @private
         */
        function _onBlurInputPassword(){
            htVar.oValidator._validateForm();
        }

        /**
         * initialize FormValidator
         * @require validate.js
         */
        function _initFormValidator(){
            var aRules = [
                {"name": 'oldPassword',     "rules": 'required'},
                {"name": 'password',        "rules": 'required|min_length[4]'},
                {"name": 'retypedPassword', "rules": 'required|matches[password]'}
            ];

            htVar.oValidator = new FormValidator('passwordReset', aRules, _onFormValidate);

            // set error message
            htVar.oValidator.setMessage('required',      Messages("validation.required"));
            htVar.oValidator.setMessage('min_length',    Messages("validation.tooShortPassword"));
            htVar.oValidator.setMessage('matches',       Messages("validation.passwordMismatch"));
        }

        /**
         * on validate form
         * @param {Array} aErrors
         */
        function _onFormValidate(aErrors){
            _clearTooltips();
            // to avoid bootstrap bug
            if (aErrors.length <= 0) {
                return _clearTooltips();
            }

            var welTarget;
            aErrors.forEach(function(htError){
                welTarget = htElement.welFormPswd.find("input[name=" + htError.name + "]");
                if(welTarget){
                    showErrorMessage(welTarget, htError.message);
                }
            });
        }

        /**
         * 폼 영역에 있는 jquery.tooltip 모두 제거하는 함수
         * @private
         */
        function _clearTooltips(){
            try {
                htElement.welFormPswd.find("input").each(function(i, v){
                    $(v).tooltip("destroy");
                });
            } catch(e){}
        }

        /**
         * Bootstrap toolTip function has some limitation.
         * In this case, toolTip doesn't provide easy way to change title and contents.
         * So, unfortunately I had to change data value in directly.
         * @param {Wrapped Element} welInput
         * @param {String} sMessage
         */
        function showErrorMessage(welInput, sMessage){
            welInput.tooltip({"trigger": "manual", "placement": "right"});

            var oToolTip = welInput.data('tooltip');
            oToolTip.options.placement = 'right';
            oToolTip.options.trigger   = 'manual';
            oToolTip.options.title     = sMessage;
            oToolTip.options.content   = sMessage;

            welInput.tooltip('show');
        }

        function hideErrorMessage(welInput){
            welInput.tooltip("hide");

            try{
                welInput.tooltip("destroy");
            } catch(e){} // to avoid bootstrap bug
        }

        _init(htOptions || {});
    };
})("hive.user.Setting");