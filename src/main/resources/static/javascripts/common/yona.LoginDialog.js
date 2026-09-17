/**
 * Yona, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihan Kim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
document.addEventListener("DOMContentLoaded", function(){
    "use strict";

    var htElement = {};
    var clientErrorStatus = /^4[0-9][0-9]$/
    var serverErrorStatus = /^5[0-9][0-9]$/
    var networkErrorStatus = 0;

    function _init(){
        // do this except in loginForm, signUpForm
        if(location.pathname.substr(1).split('/')[0] === "users"){
            return;
        }

        // #loginDialog는 익명 사용자에게만 렌더링된다(site/layout.html의
        // sec:authorize="isAnonymous()") - 로그인된 사용자의 페이지에는 아예 없다.
        var elDialog = document.getElementById("loginDialog");
        if(!elDialog){
            return;
        }

        // 하이브리드 어댑터(2026-09-17, components/vue-widgets login-dialog 위젯 적용) -
        // #loginDialog 자리가 <yona-login-dialog>(태그명으로 판별)면 실제 표시/폼 제출/
        // 에러 처리를 전부 그 커스텀 엘리먼트 자신이 담당한다(show(trigger)/hide()만
        // 공개 계약) - 이 페이지 쪽 코드는 전역 트리거 델리게이트만 그대로 소유한다
        // (Dropdown 이후 확립된 "트리거는 원래 살던 곳에 남는다" 경계와 동일).
        if(elDialog.tagName.toLowerCase() === "yona-login-dialog"){
            document.body.addEventListener('click', function(weEvt){
                var elTrigger = weEvt.target.closest('[data-login="required"]');
                if(elTrigger){
                    weEvt.preventDefault();
                    weEvt.stopPropagation();
                    elDialog.show(weEvt.target);
                }
            });
            return;
        }

        _initElement();
        _attachEvent();
    }

    function _initElement(){
        htElement.elDialog = document.getElementById("loginDialog");
        htElement.elForm = htElement.elDialog.querySelector("form");
        htElement.elInputId = htElement.elDialog.querySelector("input[name='loginIdOrEmail']");
        htElement.elInputPw = htElement.elDialog.querySelector("input[name='password']");
        htElement.elInputRememberMe = htElement.elDialog.querySelector("input[name='rememberMe']");
        htElement.elLoginError = htElement.elDialog.querySelector(".error");
        htElement.elLoginErrorMsg = htElement.elLoginError.querySelector(".error-message");
    }

    function _attachEvent(){
        document.body.addEventListener('click', function(weEvt){
            var elTrigger = weEvt.target.closest('[data-login="required"]');
            if(elTrigger){
                _showDialog(weEvt);
            }
        });
        htElement.elForm.addEventListener('submit', _onSubmitForm);
        // 배경 클릭/X 닫기 버튼(data-dismiss="modal") 처리 - ESC는 <dialog> 네이티브 동작으로 충분.
        $yona.attachDialogDismiss(htElement.elDialog);
    }

    function _showDialog(weEvt){
        if(_isInputElement(weEvt.target)){
            weEvt.target.blur();
        }

        htElement.elLoginError.style.display = "none";
        htElement.elInputPw.value = "";
        htElement.elInputId.value = "";

        htElement.elDialog.showModal();
        htElement.elInputId.focus();

        weEvt.preventDefault();
        weEvt.stopPropagation();
        return false;
    }

    function _isInputElement(el){
        return (["INPUT", "TEXTAREA"].indexOf(el.tagName.toUpperCase()) > -1);
    }

    function _onSubmitForm(weEvt){
        fetch(htElement.elForm.getAttribute("action"), {
            "method": "post",
            // jQuery $.ajax/$.post는 동일 출처 요청에 X-Requested-With: XMLHttpRequest를
            // 자동으로 붙였는데 fetch는 그렇지 않다 - 이 헤더가 없으면 서버
            // (YonaAuthenticationFailureHandler)가 AJAX 요청인지 못 알아채고 302 리다이렉트를
            // 내려주고, fetch가 그 리다이렉트를 그대로 따라가 200을 받아버려 로그인 실패 시
            // 에러 메시지가 전혀 뜨지 않고 조용히 페이지만 새로고침되는 회귀가 있었다(Playwright로
            // 실제 재현) - 명시적으로 헤더를 붙여 서버가 AJAX 경로(JSON 에러 응답)를 타게 한다.
            "headers": {"X-Requested-With": "XMLHttpRequest"},
            "body": new URLSearchParams({
                "loginIdOrEmail" : htElement.elInputId.value,
                "password": htElement.elInputPw.value,
                "rememberMe": htElement.elInputRememberMe.checked
            })
        }).then(function(response){
            if(response.ok){
                document.location.reload();
                return;
            }
            return response.text().then(function(responseText){
                return Promise.reject({"status": response.status, "responseText": responseText});
            });
        }).catch(function(htResult){
            // jQuery의 readyState===0(UNSET)은 요청이 서버에 도달하지 못한 네트워크 실패를
            // 뜻했다 - fetch는 이런 경우 프로미스 자체가 reject되며 TypeError를 던지므로
            // (Response 객체가 아예 안 생김) htResult.status가 undefined인 것으로 동일하게
            // 감지한다.
            if(typeof htResult.status === "undefined"){
                _showDialogError(Messages("user.login.failed.network"));
            }else if(htResult.responseText && htResult.responseText.length > 0){
                try{
                    var responseObject = JSON.parse(htResult.responseText);
                    _showDialogError(Messages(responseObject.message));
                }catch (err){
                    _getErrorMessageByStatus(htResult.status);
                }
            }else{
                _getErrorMessageByStatus(htResult.status);
            }
        });

        weEvt.preventDefault();
        weEvt.stopPropagation();
        return false;
    }

    function _getErrorMessageByStatus(status) {
        switch(true){
            case clientErrorStatus.test(status):
                _showDialogError(Messages("user.login.failed.client"));
                break;
            case serverErrorStatus.test(status):
                _showDialogError(Messages("user.login.failed.server"));
                break;
            default:
                _showDialogError(Messages("user.login.failed"));
                break;
        }
    }

    function _showDialogError(sMessage){
        htElement.elLoginErrorMsg.innerHTML = sMessage;
        // .loginDialog .error는 CSS에서 기본 display:none이라(yona.css) 인라인 스타일을
        // 단순히 비우는 것만으로는 다시 나타나지 않는다 - jQuery .show()가 하던 것처럼
        // 명시적으로 보이는 display 값을 강제한다.
        htElement.elLoginError.style.display = "block";

        // jQuery UI .effect("shake")를 CSS 애니메이션으로 대체(yona.css의 .yona-shake 참고).
        // 클래스를 뗐다 다시 붙이기 전에 강제로 리플로우시켜야 연속 실패 시에도 애니메이션이 재생된다.
        htElement.elDialog.classList.remove("yona-shake");
        void htElement.elDialog.offsetWidth;
        htElement.elDialog.classList.add("yona-shake");

        htElement.elInputId.focus();
    }

    _init();
});
