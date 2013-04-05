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
			_initVar();
			
			_initFormValidator();
		}
		
		/**
		 * initialize elements
		 */
		function _initElement(){
			htElement.welInputPassword = $('#password');
			htElement.welInputEmail    = $('#email');
			htElement.welInputLoginId  = $('#loginId');			
		}
		
		/**
		 * initialize variables
		 */
		function _initVar(){
			htVar.rxTrim = /\s+/g;

			// error definition
		    htVar.htErrors = {
		    	"retypedPassword": {
				"elTarget": htElement.welInputPassword
		    	},
		    	"password": {
				"elTarget": htElement.welInputPassword
		    	},
		    	"email":{
				"elTarget": htElement.welInputEmail
		    	},
		    	"loginId":{
				"elTarget": htElement.welInputLoginId
		    	}
		    };
		}
		
		/**
		 * Bootstrap toolTip function has some limitation.
		 * In this case, toolTip doesn't provide easy way to change title and contents.
		 * So, unfortunately I had to change data value in directly.
		 * @param {Wrapped Element} welInput
		 * @param {Hash Table} htMessage
		 */
		function showErrorMessage(welInput, htMessage){
	        welInput.tooltip({trigger:'manual', placement: 'left'});
	        
	        var oToolTip = welInput.data('tooltip');
	        oToolTip.options.title     = htMessage.title;
	        oToolTip.options.content   = htMessage.content;
	        oToolTip.options.placement = 'left';
	        oToolTip.options.trigger   = 'manual';

	        welInput.tooltip('show');
		}
		
		/**
		 * @param {Wrapped Element} welCheckId
		 * @param {String} sURL
		 */
		function doesExists(welCheckId, sURL){
		    var checkPosition = welCheckId.next(".isValid");
		    if(sURL.substr(-1) != "/"){
		    	sURL += "/";
		    }
		    
		    $.ajax(
		    	{"url": sURL + welCheckId.val()}
		    ).done(function(data){
		        if(data.doesExists === true){
		            showErrorMessage(welCheckId, Messages("validation.duplicated"));
		            welCheckId.tooltip("show");
		        } else {
		            welCheckId.tooltip("hide");
		            try{
		                welCheckId.tooltip("destory");
		            } catch(err){} // to avoid boostrap bug
		        }
		    });
		}

		/**
		 * attach event
		 */
		function _attachEvent(){
			$("#loginId").focusout(function(){
				// 양쪽 공백을 없애고 소문자로 변경 후 중간 공백 없앰
				$(this).val($(this).val().trim().toLowerCase().replace(htVar.rxTrim, ''));
				
				if ($(this).val() !== "") {
					doesExists($(this), "/user/doesExists/");
				}
			});
			
			$("#email").focusout(function(){
			    if ($(this).val() !== "") {
			    	doesExists($(this), "/user/isEmailExist/");
			    }
			});
			
			$('#retypedPassword').focusout(function(){
			    htVar.oValidator._validateForm();
			});
		}

		/**
		 * initialize FormValidator
		 * @require validate.js
		 */
		function _initFormValidator(){
			var aRules = [
				{"name": 'loginId',			"rules": 'required|callback_check_loginId'},
	  			{"name": 'email',			"rules": 'required|valid_email'},
	  			{"name": 'password',		"rules": 'required|min_length[4]'},
	  			{"name": 'retypedPassword', "rules": 'required|matches[password]'}
	  		];

			htVar.oValidator = new FormValidator('signup', aRules, _onFormValidate);
            htVar.oValidator.registerCallback('check_loginId', function(value) {
                return /^[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*$/.test(value);
            }).setMessage('check_loginId', Messages("validation.allowedCharsForLoginId"))
            .setMessage('required', Messages("validation.required"))
            .setMessage('min_length', Messages("validation.tooShortPassword"))
            .setMessage('matches', Messages("validation.passwordMismatch"))
            .setMessage('valid_email', Messages("validation.invalidEmail"));
		}
		
		/**
		 * on validate form
		 */
		function _onFormValidate(aErrors, event){
			if (aErrors.length > 0) {
                for(var i = 0; i < aErrors.length; i++) {
                    var htError = htVar.htErrors[aErrors[i].id];
                    if (htError) {
                        showErrorMessage(htError.elTarget, {title: aErrors[i].message});
                    }
                }
			} else {
				// to avoid bootstrap bug
				try {
					htElement.welInputPassword.tooltip('destroy');
					htElement.welInputEmail.tooltip('destroy');
					htElement.welInputLoginId.tooltip('destroy');
				} catch (err) {
//					console.log(err);
				} 
			}
		}

		
		_init();
	};
})("hive.user.SignUp");