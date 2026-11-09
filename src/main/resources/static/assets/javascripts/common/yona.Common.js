/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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
window.yona = (typeof yona == "undefined") ? {} : yona;

$yona = yona.Common = (function(){

    var htVar = {
        "sScriptPath":"",
        "rxTrim": /\s+/g
    };
    var htModuleInstance = {};

    /**
     * set JavaScript asset path for loadScript
     * @param {String} sPath
     */
    function setScriptPath(sPath){
        htVar.sScriptPath = sPath;
    }

    /**
     * Create namespace object from String
     * @param {String} sName namespace string like 'yona.module.Name'
     * @returns {Hash Table} container object and last name of argument
     * @example
     * var oNS = createNamespace("yona.module.Name");
     * oNS.container[oNS.name] = { ... };
     * // oNS.container === yona.module
     * // oNS.name === "Name"
     */
    function createNamespace(sNamespace) {
        var aSpace = sNamespace.split(".");
        var oParent = window;
        var sObjectName = null;

        for ( var i = 0, len = aSpace.length; i < len; i++) {
            sObjectName = aSpace[i];
            if (i == (len - 1)) {
                break;
            }
            if (typeof oParent[sObjectName] !== "object") {
                oParent[sObjectName] = {};
            }
            oParent = oParent[sObjectName];
        }

        return {
            "container" : oParent,
            "name" : sObjectName
        };
    }

    /**
     * load module
     * @param {String} sName
     * @param {Hash Table} htOptions
     * @param {Function} fCallback
     */
    function loadModule(sName, htOptions, fCallback){
        htOptions = htOptions || {};

        if(registerModule(sName, htOptions) === false){
            htVar.htTryLoad = htVar.htTryLoad || {};
            htVar.htTryLoad[sName] = (typeof htVar.htTryLoad[sName] == "undefined") ? 1 : (++htVar.htTryLoad[sName]);

            if(htVar.htTryLoad[sName] > 3){
                console.log("[Yobi] fail to load module " + sName);
                return false;
            }

            var sURL = htVar.sScriptPath + "service/yona." + sName + ".js";
            var fOnLoad = function(){
                loadModule(sName, htOptions, fCallback);
            };
            return loadScript(sURL, fOnLoad);
        }

        if(typeof fCallback == "function"){
            fCallback(htOptions);
        }
    }

    /**
     * register module
     * @param {String} sName
     * @param {Hash Table} htOptions
     */
    function registerModule(sName, htOptions){
        var aNames = sName.split(".");
        var sDepth = aNames.shift();
        var oModule = yona[sDepth];

        while(aNames.length && oModule){
            sDepth = aNames.shift();
            oModule = oModule[sDepth];
        }

        $("[data-bs-toggle=popover]").each(function(){ bootstrap.Popover.getOrCreateInstance(this); });

        // temporary code for compatibility with nForge
        var oInstance;
        if(typeof oModule == "undefined"){
            return false;
        } else if(typeof oModule == "function"){
            oInstance = new oModule(htOptions);
        } else if(typeof oModule == "object"){
            oInstance = oModule;
            oInstance.init();
        }
        return htModuleInstance[sName] = oInstance;
    }

    /**
     * load JavaScript
     * @param {String} sURL
     * @param {Function} fCallback callback function on load
     */
    function loadScript(sURL, fCallback){
        var elScript = document.createElement("script");
        elScript.type = "text/javascript";
        elScript.async = true;
        elScript.src = sURL;

        // run callback and free memory on load
        var fOnLoad = function(){
            if(typeof fCallback == "function"){
                fCallback();
            }
            document.body.removeChild(elScript);
            elScript = fOnLoad = null;
        };

        // attach onLoad event handler
        if(elScript.addEventListener) { // for FF
            elScript.addEventListener("load", fOnLoad, false);
        } else if(typeof elScript.onload == "undefined"){
            elScript.onreadystatechange = function(){ // for IE
                if(this.readyState === "complete" || this.readyState === "loaded"){
                    fOnLoad();
                }
            };
        } else { // and for other polite browsers
            elScript.onload = fOnLoad;
        }

        document.body.appendChild(elScript);
    }

    /**
     * stop Event
     * @param {Event} eEvt
     */
    function stopEvent(eEvt) {
        if(!eEvt){
            return;
        }
        eEvt.cancelBubble = true;
        eEvt.returnValue = false;

        if (eEvt.stopPropagation) {
            eEvt.stopPropagation();
        }
        if (eEvt.preventDefault) {
            eEvt.preventDefault();
        }
    }

    /**
     * Compute a color contrasted with the given color (lightness).
     * See http://en.wikipedia.org/wiki/Luma_(video)
     * @param {String} sColor
     * @returns {String}
     * @example dimgray if yellow is given.
     */
    function getContrastColor(sColor){
        var oRGB = new RGBColor(sColor);
        var y709 = (oRGB.r * 0.21) + (oRGB.g * 0.72) + (oRGB.b * 0.07);
        return (y709 > 192) ? 'dimgray' : 'white';
    }

    /**
     * Send a request using $.ajaxForm
     * @param {Hash Table} htOptions
     * @param {String}        htOptions.sURL <form> action
     * @param {Hash Table} htOptions.htOptForm <form> attributes
     * @param {Hash Table} htOptions.htData data to send
     * @param {Function}   htOptions.fOnLoad callback function on load
     * @param {Function}   htOptions.fOnError callback function on error
     * @param {String}       htOptions.sDataType
     */
    function sendForm(htOptions){
        var sKey = "";
        var aFields = [];
        var aFormAttr = [];

        // create form with attributes (htOptForm)
        var htOptForm = htOptions.htOptForm || {"method":"post"};
        for(sKey in htOptForm){
            aFormAttr.push(sKey + '="' + htOptForm[sKey] + '"');
        }
        var sFormAttr = aFormAttr.join(" ");
        var welForm = $('<form action="' + htOptions.sURL + '" ' + sFormAttr + '>');

        // form fields
        var htData = htOptions.htData || {};
        for(sKey in htData){
            aFields.push($('<input type="hidden" name="' + sKey + '" value="' + htData[sKey] + '">'));
        }
        welForm.append(aFields);
        welForm.appendTo(document.body);

        // send form
        welForm.ajaxForm({
            "success" : function(){
                if(typeof htOptions.fOnLoad === "function"){
                    htOptions.fOnLoad.apply(this, arguments);
                }
                welForm.remove();
            },
            "error"   : function(){
                if(typeof htOptions.fOnError === "function"){
                    htOptions.fOnError.apply(this, arguments);
                }
                welForm.remove();
            },
            "dataType": htOptions.sDataType || null
        });

        welForm.submit();

        aFields = aFormAttr = sFormAttr = null;
    }

    /**
     * Strip all whitespace in string
     * @param {String} sValue
     * @return {String}
     */
    function getTrim(sValue){
        return sValue.trim().replace(htVar.rxTrim, '');
    }

    /**
     * Show alert dialog
     * @param {String} sMessage Message string
     * @param {Function} fOnAfterHide Call this function after hidden dialog (optional)
     * @param {String} sDescription Description string (optional)
     */
    function showAlert(sMessage, fOnAfterHide, sDescription){
        if(!htVar.oAlertDialog){
            htVar.oAlertDialog = new yona.ui.Dialog("#yobiDialog");
        }

        htVar.oAlertDialog.show(sMessage, sDescription, {
            "fOnAfterHide": fOnAfterHide
        });
    }

    /**
     * Show confirm dialog
     * @param {String} sMessage Message string
     * @param {Function} fCallback Call this function after click button
     * @param {String} sDescription Description string (optional)
     * @param {Hash Table} htOptions
     * @param {Array} htOptions.aButtonLabels Specifying button labels (optional)
     * @param {Array} htOptions.aButtonStyles Specifying button CSS Class names (optional)
     */
    function showConfirm(sMessage, fCallback, sDescription, htOptions){
        if(!htVar.oConfirmDialog){
            htVar.oConfirmDialog = new yona.ui.Dialog("#yobiDialog");
        }

        htOptions = htOptions || {};
        var aButtonStyles = htOptions.aButtonStyles;
        var aButtonLabels = htOptions.aButtonLabels || [Messages("button.cancel"), Messages("button.confirm")];

        htVar.oConfirmDialog.show(sMessage, sDescription, {
           "fOnClickButton": fCallback,
           "aButtonLabels" : aButtonLabels,
           "aButtonStyles" : aButtonStyles
        });
    }

    /**
     * Show confirm before send ajax.
     *
     * @param {String} sMessage confirm message
     * @param {Hash Table} htAjaxOptions jQuery.ajax settings
     * @param {String} sDescription Description string (optional)
     * @param {Hash Table} htConfirmOptions showConfirm options (optional)
     */
    function ajaxConfirm(sMessage, htAjaxOptions, sDescription, htConfirmOptions){
        showConfirm(sMessage, function(htData){
            if(htData.nButtonIndex === 1){
                $.ajax(htAjaxOptions);
            }
        }, sDescription, htConfirmOptions);
    }

    /**
     * Show notification message using Toast PopUp
     * @param {String} sMessage
     * @param {Number} nDuration
     */
    function notify(sMessage, nDuration, sTitle){
        if(!htVar.oToast){
            htVar.oToast = new yona.ui.Toast("#yobiToasts", {
                "sTplToast": $("#tplYobiToast").text()
            });
        }

        if(sTitle){
            htVar.oToast.push("<strong>" + sTitle + "</strong>" + "<br/>" + sMessage, nDuration);
        } else {
            htVar.oToast.push(sMessage, nDuration);
        }

    }

    /**
     * Inserts HTML line breaks before all newlines in a string
     * @param {String} sText
     * @return {String}
     */
    function nl2br(sText){
        return (typeof sText === "string") ? sText.split("\n").join("<br>") : sText;
    }

    /**
     * Simple template processor
     * @param {String} sTemplate Template String
     * @param {Hash Table} htData Data Object.
     * @return {String}
     * @example
     * processTpl("My name is ${name}", {name: 'John Doe'});
     * // returns "My name is John Doe"
     *
     * processTpl("1st item of Array is '${0}'", ['a','b','c']);
     * // returns "1st item of Array is 'a'"
     */
    function processTpl(sTemplate, htData) {
        htVar.rxTemplate = htVar.rxTemplate || /\${([^{}]*)}/g;

        return sTemplate.replace(htVar.rxTemplate, function(a, b) {
            return (typeof htData[b] == "undefined") ? "" : htData[b];
        });
    }

    /**
     * HTML 이스케이프. v1.6이 쓰던 jquery.tmpl 1.0.0의 `${}`와 같게 `<`, `>`, `"`, `'`만 바꾼다(`&`는 바꾸지 않는다).
     * Mustache의 기본 이스케이프(`&`, `/`, `` ` ``, `=`까지 바꿈) 대신 쓰려고 `Mustache.escape`에 건다.
     *
     * @param {*} value
     * @return {String}
     */
    function escapeTemplateValue(value) {
        return ("" + value).split("<").join("&lt;").split(">").join("&gt;").split('"').join("&#34;").split("'").join("&#39;");
    }

    /**
     * HTML 조각을 노드 배열로 읽는다(jQuery `$.parseHTML`의 기본 동작과 같다: 스크립트는 버리고, 읽는 동안 실행하거나 이미지를 불러오지 않는다).
     * `<template>`은 `<tr>` 같은 조각도 문맥 없이 읽는다.
     *
     * @param {String} sHtml
     * @return {Node[]}
     */
    function parseHtml(sHtml) {
        var elTemplate = document.createElement("template");
        elTemplate.innerHTML = sHtml;
        elTemplate.content.querySelectorAll("script").forEach(function (elScript) { elScript.remove(); });
        return Array.from(elTemplate.content.childNodes);
    }

    /**
     * Mustache 템플릿으로 HTML 조각을 만들어 jQuery 객체로 돌려준다. v1.6의 `$.tmpl(템플릿, 자료)`(jquery.tmpl)를 대신한다(decisions.md D-94).
     *
     * `{{이름}}`은 이스케이프해서, `{{{이름}}}`은 그대로 넣는다. 없는 값은 빈 글자이고 `a.b` 같은 중첩 속성도 된다.
     * 앞뒤 공백은 버린다. 글자만 있는 템플릿은 글자 노드를, 뿌리가 둘이면 둘을 돌려준다.
     *
     * @param {String} sTemplate Mustache 템플릿 문자열
     * @param {Object} htData 자료 (생략 가능)
     * @return {jQuery} 만들어진 노드들
     * @example
     * $yona.render('<li title="{{name}}">{{name}}</li>', {name: 'a & b'});
     */
    function render(sTemplate, htData) {
        // 요소를 찾지 못하면 `$(el).html()`이 undefined이다. jquery.tmpl처럼 빈 집합을 돌려준다
        if (typeof sTemplate !== "string") {
            return $();
        }
        Mustache.escape = escapeTemplateValue;
        return $(parseHtml(Mustache.render(sTemplate, htData == null ? {} : htData).trim()));
    }

    /**
     * 요소 안에 적힌 Mustache 템플릿(`<script type="text/x-mustache" id="...">`)으로 그린다. v1.6의 `$('#id').tmpl(자료)`를 대신한다.
     *
     * @param {String|Element|jQuery} elTemplate 템플릿이 든 요소
     * @param {Object} htData 자료 (생략 가능)
     * @return {jQuery} 만들어진 노드들
     */
    function renderElement(elTemplate, htData) {
        return render($(elTemplate).html(), htData);
    }

    /**
     * Convert special characters to HTML entities
     * @param {String} sHTML
     * @return {String}
     */
    function htmlspecialchars(sHTML){
        htVar.welHSC = htVar.welHSC || $("<div>");
        return htVar.welHSC.text(sHTML).html();
    }

    /**
     * Get whether the file is image with MIME type, and filename extension.
     * returns boolean or null if unavailable to determine result
     *
     * @param {Variant} vFile File object or HTMLElement
     * @returns {Boolean|Null}
     */
    function isImageFile(vFile){
        // if vFile is File or Blob Object
        if((typeof window.File !== "undefined" && vFile instanceof window.File) ||
           (typeof window.Blob !== "undefined" && vFile instanceof window.Blob)){
            return (vFile.type.toLowerCase().indexOf("image/") === 0);
        }

        // if vFile is HTMLElement
        var welFile = $(vFile);
        var oFileList = welFile.prop("files");

        if(oFileList && oFileList.length){
            var bResult = true;

            for(var i = 0, nLength = oFileList.length; i < nLength; i++){
                bResult = bResult && isImageFile(oFileList[i]);
            }

            return bResult;
        }

        // if cannot find MIME type from File object
        // get whether filename ends with extension looks like image file
        // like as .gif, .bmp, .jpg, .jpeg, .png.
        if(typeof welFile.val() === "string"){
            htVar.rxImgExts = htVar.rxImgExts || /\.(gif|bmp|jpg|jpeg|png)$/i;
            return htVar.rxImgExts.test(welFile.val());
        }

        // Unavailable to detect mimeType
        return null;
    }

    /**
     * 요소에 팝오버를 띄운다. 이미 있던 팝오버는 없애고 새로 만든다.
     * Bootstrap 5의 팝오버 API(`new bootstrap.Popover`)를 쓰며 jQuery 플러그인 호출(팝오버 만들기와 보이기)을 대신한다(X-38e).
     *
     * @param {Wrapped Element|Element} target 팝오버를 붙일 요소
     * @param {Hash Table} htOptions 팝오버 옵션(placement, content, ...). `trigger`는 기본이 "manual"이다.
     */
    function showPopover(target, htOptions){
        $(target).each(function(){
            var oOld = bootstrap.Popover.getInstance(this);
            if(oOld){
                oOld.dispose();
            }
            new bootstrap.Popover(this, Object.assign({"trigger": "manual"}, htOptions)).show();
        });
    }

    /**
     * 요소의 팝오버를 닫고 없앤다. 팝오버가 없으면 아무것도 하지 않는다.
     *
     * @param {Wrapped Element|Element} target 팝오버가 붙은 요소
     */
    function hidePopover(target){
        $(target).each(function(){
            var oPopover = bootstrap.Popover.getInstance(this);
            if(oPopover){
                oPopover.dispose();
            }
        });
    }

    /**
     * 탭을 보인다(Bootstrap 5의 `bootstrap.Tab`). 대상이 없으면 아무것도 하지 않는다.
     *
     * @param {Wrapped Element|Element} target 탭의 링크
     */
    function showTab(target){
        var elTab = $(target).get(0);
        if(elTab){
            bootstrap.Tab.getOrCreateInstance(elTab).show();
        }
    }

    /**
     * 키보드 이벤트 본체를 돌려준다. jQuery 이벤트면 `originalEvent`(네이티브)를 쓴다.
     * jQuery 이벤트에는 `key`는 복사되지만 `isComposing`은 복사되지 않는다.
     *
     * @param {Event|jQuery.Event} eEvt
     * @return {KeyboardEvent|undefined}
     */
    function _keyboardEvent(eEvt) {
        return (eEvt && eEvt.originalEvent) || eEvt;
    }

    /**
     * 입력기(IME)로 글자를 조합하는 중에 난 키보드 이벤트인지 알려 준다.
     *
     * 조합 중의 키는 입력기의 것이다(예: 한글 조합을 확정하는 Enter). Chrome과 Firefox는 이때 `key`를 "Process",
     * `keyCode`를 229로 보내고, Safari는 `key`를 "Enter"로 보내면서 `isComposing`을 참으로 둔다. 세 가지를 모두 본다.
     *
     * @param {Event|jQuery.Event} eEvt
     * @return {Boolean}
     */
    function isComposingKey(eEvt) {
        var oEvt = _keyboardEvent(eEvt);
        return !!oEvt && (oEvt.isComposing === true || oEvt.keyCode === 229 || oEvt.key === "Process");
    }

    /**
     * 눌린 키가 [sKey](`KeyboardEvent.key` 값)이고 조합 중이 아닌지 알려 준다.
     */
    function _isKey(eEvt, sKey) {
        var oEvt = _keyboardEvent(eEvt);
        return !!oEvt && oEvt.key === sKey && !isComposingKey(oEvt);
    }

    /**
     * Enter(숫자 패드 포함)가 눌렸는지. 한글 조합을 확정하는 Enter는 제외한다.
     */
    function isEnterKey(eEvt) {
        return _isKey(eEvt, "Enter");
    }

    /**
     * Esc가 눌렸는지. 조합을 취소하는 Esc는 제외한다.
     */
    function isEscapeKey(eEvt) {
        return _isKey(eEvt, "Escape");
    }

    /**
     * Backspace가 눌렸는지.
     */
    function isBackspaceKey(eEvt) {
        return _isKey(eEvt, "Backspace");
    }

    /**
     * 정화할 때 내용과 함께 걷어내는 요소이다. 옛 필터(`lib/xss.js`)가 글자로 바꿔 보여 주던 금지 요소(Yona가 v1.6부터 막아 온 목록)에,
     * 결과를 다시 읽을 때 구조가 바뀌어 XSS가 되는 변이(mutation) XSS를 일으키는 noscript, math, svg를 더했다.
     * 진짜 `<form>` 같은 요소를 그대로 쓰면 글자로 보이지 않고 사라진다(선생님이 무시해도 된다고 하셨다). 이스케이프해서 쓴 `&lt;form&gt;`은 글자로 보인다.
     * `input`은 마크다운이 그리는 작업 목록 체크 상자이므로 뺐다(서버의 v1.6 정책도 허용한다). 대신 체크 상자만 남기고 속성을 줄인다(`xssClean`).
     * script, 이벤트 처리기, `javascript:` 주소 같은 것은 목록과 상관없이 표준 정화기가 항상 걷어낸다.
     */
    var XSS_REMOVED_ELEMENTS = [
        "applet", "audio", "basefont", "base", "bgsound", "blink", "body", "embed", "form", "frameset", "frame", "head", "html", "ilayer", "iframe",
        "isindex", "layer", "link", "meta", "object", "plaintext", "style", "script", "textarea", "title", "xml",
        "noscript",
        // math와 svg는 HTML과 다른 이름 공간의 요소라 이름만 주면 걷어내지 못한다
        { "name": "math", "namespace": "http://www.w3.org/1998/Math/MathML" },
        { "name": "svg", "namespace": "http://www.w3.org/2000/svg" }
    ];

    var xssSanitizer = null;

    /**
     * 위험한 HTML을 정화해 글자로 돌려준다(S-12, A-425).
     *
     * 브라우저가 표준 Sanitizer API(`Element.setHTML`)를 지원하면 표준으로 정화한다. 표준은 허용 목록이 아니라 위험한 것(script, 이벤트 처리기,
     * `javascript:` 주소 등)을 걷어내는 방식이라 이미지, 동영상, details, class, id, style 같은 마크다운 내용은 그대로 남는다.
     * 지원하지 않는 브라우저(Safari 등)나 표준 정화기를 만들지 못하는 경우에는 옛 정규식 필터(`Filter.defence`)를 쓴다.
     *
     * @param {String} str 정화할 HTML. 값이 없으면 빈 글자로 본다
     * @return {String} 정화한 HTML
     */
    function xssClean(str) {
        str = str == null ? "" : String(str);

        if (typeof Sanitizer === "function" && typeof Element.prototype.setHTML === "function") {
            try {
                xssSanitizer = xssSanitizer || new Sanitizer({ "removeElements": XSS_REMOVED_ELEMENTS });
                var box = document.createElement("div");
                box.setHTML(str, { "sanitizer": xssSanitizer });
                // 표준 정화기는 주소의 종류를 가리지 않으므로 링크의 data: 주소는 따로 걷어낸다(옛 필터도 막았다). 이미지의 data: 주소는 남긴다
                var links = box.querySelectorAll("a[href], area[href]");
                for (var i = 0; i < links.length; i++) {
                    if (/^[\s\u0000-\u001f]*data:/i.test(links[i].getAttribute("href"))) { links[i].removeAttribute("href"); }
                }
                // 체크 상자만 남긴다. 서버의 v1.6 정책(`MarkdownRenderer`)은 input의 type, disabled, checked를 허용한다. 가짜 입력란을 막기 위해 체크 상자가 아닌 input은 지우고
                // 허용하지 않는 속성(name, value, form 등)을 걷어낸다.
                var inputs = box.querySelectorAll("input");
                for (var j = 0; j < inputs.length; j++) {
                    var input = inputs[j];
                    if ((input.getAttribute("type") || "").toLowerCase() !== "checkbox") { input.remove(); continue; }
                    var names = input.getAttributeNames();
                    for (var k = 0; k < names.length; k++) {
                        if (["type", "checked", "disabled", "class", "id", "style", "width", "height"].indexOf(names[k]) < 0) { input.removeAttribute(names[k]); }
                    }
                }
                return box.innerHTML;
            } catch (e) {
                // 설정을 거부하거나 정화에 실패하는 브라우저는 아래의 옛 필터로 정화한다
            }
        }

        return new Filter().defence(str);
    }

    /* public Interface */
    return {
        "setScriptPath"   : setScriptPath,
        "createNamespace" : createNamespace,
        "getContrastColor": getContrastColor,
        "loadModule": loadModule,
        "loadScript": loadScript,
        "stopEvent" : stopEvent,
        "sendForm"  : sendForm,
        "getTrim"   : getTrim,
        "showAlert" : showAlert,
        "alert"     : showAlert,
        "confirm"   : showConfirm,
        "ajaxConfirm": ajaxConfirm,
        "notify"    : notify,
        "nl2br"     : nl2br,
        "tmpl"      : processTpl,
        "render"    : render,
        "renderElement": renderElement,
        "htmlspecialchars": htmlspecialchars,
        "isImageFile": isImageFile,
        "xssClean" : xssClean,
        "isComposingKey": isComposingKey,
        "isEnterKey": isEnterKey,
        "isEscapeKey": isEscapeKey,
        "isBackspaceKey": isBackspaceKey,
        "showPopover": showPopover,
        "hidePopover": hidePopover,
        "showTab": showTab
    };
})();

