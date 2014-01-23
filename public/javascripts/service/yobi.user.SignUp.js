/**
 * @(#)yobi.user.SignUp.js 2013.04.02
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */

(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         */
        function _init(){
            _initElement();
            _initVar();

            _initFormValidator();
            _attachEvent();
        }

        /**
         * initialize elements
         */
        function _initElement(){
            htElement.welInputPassword  = $('#password');
            htElement.welInputPassword2 = $('#retypedPassword');
            htElement.welInputEmail     = $('#email');
            htElement.welInputLoginId   = $('#loginId');
            htElement.welInputLoginId.focus();

            htElement.welForm = $("form[name=signup]");

        }

        /**
         * initialize variables
         */
        function _initVar(){
            htVar.rxLoginId = /^[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*$/;
        }

        /**
         * attach event
         */
        function _attachEvent(){
            htElement.welInputLoginId.focusout(_onBlurInputLoginId);
            htElement.welInputEmail.focusout(_onBlurInputEmail);
            htElement.welInputPassword.focusout(_onBlurInputPassword);
            htElement.welInputPassword2.focusout(_onBlurInputPassword);
        }

        /**
         * 아이디 입력란 벗어날 때 이벤트 핸들러
         * 중복여부 즉시 확인
         */
        function _onBlurInputLoginId(){
            var welInput = $(this);
            var sLoginId = $yobi.getTrim(welInput.val()).toLowerCase();
            welInput.val(sLoginId);

            if(_onValidateLoginId(sLoginId) === false){
                showErrorMessage(welInput, Messages("validation.allowedCharsForLoginId"));
                return false;
            }

            if(sLoginId != ""){
                doesExists($(this), "/user/isExist/");
            }
        }

        /**
         * 이메일 입력란 벗어날 때 이벤트 핸들러
         * 중복여부 즉시 확인
         */
        function _onBlurInputEmail(){
            var welInput = $(this);

            if(welInput.val() !== ""){
                doesExists(welInput, "/user/isEmailExist/");
            }
        }

        /**
         * 비밀번호 확인 입력란 벗어날 때 이벤트 핸들러
         * 마지막 입력란이므로 전체 폼 유효성 검사
         */
        function _onBlurInputPassword(){
            htVar.oValidator._validateForm();
        }

        /**
         * @param {Wrapped Element} welInput
         * @param {String} sURL
         */
        function doesExists(welInput, sURL){
            if(sURL.substr(-1) != "/"){
                sURL += "/";
            }

            $.ajax({
                "url": sURL + welInput.val()
            }).done(function(htData){
                if(htData.isExist === true){
                    showErrorMessage(welInput, Messages("validation.duplicated"));
                } else if (htData.isReserved == true) {
                    showErrorMessage(welInput, Messages("validation.reservedWord"));
                } else {
                    hideErrorMessage(welInput);
                }
            });
        }

        /**
         * initialize FormValidator
         * @require validate.js
         */
        function _initFormValidator(){
            var aRules = [
                {"name": 'loginId',         "rules": 'required|callback_check_loginId'},
                {"name": 'email',           "rules": 'required|valid_email'},
                {"name": 'password',        "rules": 'required|min_length[4]'},
                {"name": 'retypedPassword', "rules": 'required|matches[password]'}
            ];

            htVar.oValidator = new FormValidator('signup', aRules, _onFormValidate);
            htVar.oValidator.registerCallback('check_loginId', _onValidateLoginId)

            // set error message
            htVar.oValidator.setMessage('check_loginId', Messages("validation.allowedCharsForLoginId"));
            htVar.oValidator.setMessage('required',      Messages("validation.required"));
            htVar.oValidator.setMessage('min_length',    Messages("validation.tooShortPassword"));
            htVar.oValidator.setMessage('matches',       Messages("validation.passwordMismatch"));
            htVar.oValidator.setMessage('valid_email',   Messages("validation.invalidEmail"));
        }

        /**
         * login id validation
         * @param {String} sLoginId
         * @return {Boolean}
         */
        function _onValidateLoginId(sLoginId){
            return htVar.rxLoginId.test(sLoginId);
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
                welTarget = htElement.welForm.find("input[name=" + htError.name + "]");
                if(welTarget){
                    showErrorMessage(welTarget, htError.message);
                }
            });
        }

        /**
         * 폼 영역에 있는 jquery.tooltip 모두 제거하는 함수
         */
        function _clearTooltips(){
            try {
                htElement.welForm.find("input").each(function(i, v){
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
            welInput.popover({"trigger": "manual", "placement": "left"});

            var oToolTip = welInput.data('popover');
            oToolTip.options.placement = 'left';
            oToolTip.options.trigger   = 'manual';
            oToolTip.options.content   = sMessage;

            welInput.popover('show');
        }

        function hideErrorMessage(welInput){
            welInput.popover("hide");

            try{
                welInput.popover("destroy");
            } catch(e){} // to avoid bootstrap bug
        }

        _init();
    };
})("yobi.user.SignUp");
