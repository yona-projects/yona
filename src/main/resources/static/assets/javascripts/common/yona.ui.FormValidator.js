/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 양식을 제출할 때 입력값을 규칙으로 검사해 오류 목록을 처리기에 넘기는 작은 검사기이다(가입, 비밀번호 변경과 재설정 화면).
 * v1.6이 쓰던 외부 라이브러리 `validate.js`(FormValidator 1.1, Yona가 패치한 판)에서 이 화면들이 쓰는 규칙만 다시 썼다(D-122 3단계).
 * 생성자와 처리기 규약은 옛 라이브러리와 같다.
 *
 * 사용
 * <pre>
 * var oValidator = new yona.ui.FormValidator("signup", [
 *     {"name": "loginId",  "rules": "required|callback_check_loginId"},
 *     {"name": "password", "rules": "required|min_length[4]"},
 *     {"name": "retyped",  "rules": "required|matches[password]", "display": "비밀번호 확인"}
 * ], function (aErrors, eEvent) { ... });
 * oValidator.registerCallback("check_loginId", function (sValue) { return /^[a-z0-9]+$/.test(sValue); });
 * oValidator.setMessage("required", "%s 항목은 필수입니다.");
 * </pre>
 *
 * 규칙(`|`으로 이음): `required`, `min_length[n]`, `max_length[n]`, `matches[다른 필드]`, `valid_email`, `callback_이름`(`registerCallback`으로 등록한 함수가 false를 돌려주면 실패).
 * 값이 비어 있고 `required`가 없으면 나머지 규칙은 건너뛴다. 한 필드에서는 처음 실패한 규칙 하나만 오류로 낸다.
 * 메시지의 첫 `%s`는 필드의 표시 이름(`display`, 없으면 필드 이름)으로, 규칙에 인자가 있으면 두 번째 `%s`는 그 인자(다른 필드면 그 표시 이름)로 바뀐다.
 *
 * 양식의 `onsubmit`에 검사를 연결한다. 처리기가 true나 false를 돌려주면 그 값이 `onsubmit`의 반환값이 되고, 아니면 오류가 있을 때 제출을 막는다.
 * `validate()`로 제출 없이 직접 검사할 수도 있다.
 */
(function (window, document) {
    "use strict";

    const yona = window.yona = window.yona || {};
    yona.ui = yona.ui || {};

    const NUMERIC = /^[0-9]+$/;
    const EMAIL = /[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?/i;
    const RULE_WITH_PARAM = /^(.+?)\[(.+)\]$/;

    const DEFAULT_MESSAGES = {
        "required": "The %s field is required.",
        "matches": "The %s field does not match the %s field.",
        "valid_email": "The %s field must contain a valid email address.",
        "min_length": "The %s field must be at least %s characters in length.",
        "max_length": "The %s field must not exceed %s characters in length."
    };

    /** 규칙 이름별 검사 함수. 통과하면 true를 돌려준다. */
    const HOOKS = {
        "required": function (htField) {
            if (htField.type === "checkbox") {
                return htField.checked === true;
            }
            return htField.value !== null && htField.value !== "";
        },
        "matches": function (htField, sOtherName) {
            const elOther = this.form[sOtherName];
            return elOther ? htField.value === elOther.value : false;
        },
        "valid_email": function (htField) {
            return EMAIL.test(htField.value);
        },
        "min_length": function (htField, sLength) {
            return NUMERIC.test(sLength) && htField.value.length >= parseInt(sLength, 10);
        },
        "max_length": function (htField, sLength) {
            return NUMERIC.test(sLength) && htField.value.length <= parseInt(sLength, 10);
        }
    };

    /**
     * @param {String} sFormName 양식의 name이나 id
     * @param {Array} aFields `{name, rules, display}` 목록
     * @param {Function} [fCallback] `(오류 목록, 이벤트)`를 받는 처리기. 오류는 `{id, name, message}`
     * @constructor
     */
    function FormValidator(sFormName, aFields, fCallback) {
        const oSelf = this;

        this.callback = fCallback || function () {};
        this.errors = [];
        this.fields = {};
        this.form = document.forms[sFormName] || {};
        this.messages = {};
        this.handlers = {};

        aFields.forEach((htField) => {
            // 이름이나 규칙이 없으면 건너뛴다
            if (!htField.name || !htField.rules) {
                return;
            }
            oSelf.fields[htField.name] = {
                "name": htField.name,
                "display": htField.display || htField.name,
                "rules": htField.rules,
                "id": null,
                "type": null,
                "value": null,
                "checked": null
            };
        });

        this.form.onsubmit = function (eEvent) {
            try {
                return oSelf.validate(eEvent);
            } catch (oError) {
                console.error(oError);
                return false;
            }
        };
    }

    /** 규칙의 오류 메시지를 정한다. @return {FormValidator} 이어 부를 수 있게 자신을 돌려준다 */
    FormValidator.prototype.setMessage = function (sRule, sMessage) {
        this.messages[sRule] = sMessage;
        return this;
    };

    /** `callback_이름` 규칙이 부를 함수를 등록한다. 함수는 값을 받아 false를 돌려주면 실패이다. */
    FormValidator.prototype.registerCallback = function (sName, fHandler) {
        if (sName && typeof sName === "string" && typeof fHandler === "function") {
            this.handlers[sName] = fHandler;
        }
        return this;
    };

    /**
     * 모든 필드를 검사하고 처리기를 부른다.
     *
     * @param {Event} [eEvent] 제출 이벤트
     * @return {Boolean} 제출해도 되는지(처리기가 true나 false를 돌려주면 그 값)
     */
    FormValidator.prototype.validate = function (eEvent) {
        const oSelf = this;
        this.errors = [];

        Object.keys(this.fields).forEach((sKey) => {
            const htField = oSelf.fields[sKey];
            const elInput = oSelf.form[htField.name];
            if (elInput) {
                htField.id = elInput.id;
                htField.type = elInput.type;
                htField.value = elInput.value;
                htField.checked = elInput.checked;
            }
            oSelf.validateField(htField);
        });

        if (typeof this.callback === "function") {
            const vReturn = this.callback(this.errors, eEvent);
            if (typeof vReturn === "boolean") {
                return vReturn;
            }
        }

        if (this.errors.length > 0) {
            if (eEvent && eEvent.preventDefault) {
                eEvent.preventDefault();
            } else {
                return false;
            }
        }
        return true;
    };

    /** 한 필드를 검사해 처음 실패한 규칙 하나를 `errors`에 더한다. */
    FormValidator.prototype.validateField = function (htField) {
        const aRules = htField.rules.split("|");

        // 필수가 아니면 빈 값은 검사하지 않는다
        if (htField.rules.indexOf("required") === -1 && (!htField.value || htField.value === "")) {
            return;
        }

        for (let i = 0; i < aRules.length; i++) {
            let sMethod = aRules[i];
            let sParam = null;
            let bFailed = false;
            const aParts = RULE_WITH_PARAM.exec(sMethod);

            if (aParts) {
                sMethod = aParts[1];
                sParam = aParts[2];
            }

            if (typeof HOOKS[sMethod] === "function") {
                bFailed = !HOOKS[sMethod].call(this, htField, sParam);
            } else if (sMethod.substring(0, 9) === "callback_") {
                sMethod = sMethod.substring(9);
                if (typeof this.handlers[sMethod] === "function") {
                    bFailed = this.handlers[sMethod].call(this, htField.value) === false;
                }
            }

            if (bFailed) {
                const sSource = this.messages[sMethod] || DEFAULT_MESSAGES[sMethod];
                let sMessage = "An error has occurred with the " + htField.display + " field.";
                if (sSource) {
                    sMessage = sSource.replace("%s", htField.display);
                    if (sParam) {
                        sMessage = sMessage.replace("%s", this.fields[sParam] ? this.fields[sParam].display : sParam);
                    }
                }
                this.errors.push({"id": htField.id, "name": htField.name, "message": sMessage});
                // 같은 필드에서 오류를 겹쳐 쌓지 않는다(필수와 형식 등)
                break;
            }
        }
    };

    yona.ui.FormValidator = FormValidator;
})(window, document);
