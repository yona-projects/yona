/**
 * @(#)hive.user.SignUp.js 2013.04.02
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(){
		
		var htVar = {};
		var htElement = {};
		
		/**
		 * initialize
		 */
		function _init(){
			_initElement();
			_initFormValidator();
			_attachEvent();
		}
		
		/**
		 * initialize elements
		 */
		function _initElement(){
			htElement.welInputPassword  = $('#password');
			htElement.welInputPassword2 = $('#retypedPassword');

			htElement.welForm = $("form[name=passwordReset]");
		}
		
		/**
		 * attach event
		 */
		function _attachEvent(){
			htElement.welInputPassword.focusout(_onBlurInputPassword);
            htElement.welInputPassword2.focusout(_onBlurInputPassword);
		}
		

		/**
		 * 비밀번호 확인 입력란 벗어날 때 이벤트 핸들러
		 * 마지막 입력란이므로 전체 폼 유효성 검사
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
	  			{"name": 'password',		"rules": 'required|min_length[4]'},
	  			{"name": 'retypedPassword', "rules": 'required|matches[password]'}
	  		];

			htVar.oValidator = new FormValidator('passwordReset', aRules, _onFormValidate);

            // set error message
            htVar.oValidator.setMessage('required',		 Messages("validation.required"));
            htVar.oValidator.setMessage('min_length',	 Messages("validation.tooShortPassword"));
            htVar.oValidator.setMessage('matches',		 Messages("validation.passwordMismatch"));
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
	        welInput.tooltip({"trigger": "manual", "placement": "left"});

	        var oToolTip = welInput.data('tooltip');
	        oToolTip.options.placement = 'left';
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
		
		_init();
	};
})("hive.resetPassword");