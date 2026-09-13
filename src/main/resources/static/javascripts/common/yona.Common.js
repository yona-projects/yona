/**
 * Yona, Project Hosting SW
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
                console.log("[Yona] fail to load module " + sName);
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

        initHoverPopovers("[data-toggle=popover]");

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
            htVar.oAlertDialog = new yona.ui.Dialog("#yonaDialog");
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
            htVar.oConfirmDialog = new yona.ui.Dialog("#yonaDialog");
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
     * @param {Hash Table} htAjaxOptions fetch 기반 설정 - {url, method, dataType, success, error}만
     *        지원한다(원래 jQuery.ajax settings를 그대로 넘기던 것을 실제 호출부 1곳(현재
     *        yona.project.Member.js)의 사용 범위에 맞춰 좁혔다 - 2026-09-12).
     * @param {String} sDescription Description string (optional)
     * @param {Hash Table} htConfirmOptions showConfirm options (optional)
     */
    function ajaxConfirm(sMessage, htAjaxOptions, sDescription, htConfirmOptions){
        showConfirm(sMessage, function(htData){
            if(htData.nButtonIndex === 1){
                fetch(htAjaxOptions.url, {"method": htAjaxOptions.method || "get"})
                    .then(function(response){
                        return response.text().then(function(text){
                            if(!response.ok){
                                return Promise.reject({"status": response.status, "responseText": text});
                            }
                            return htAjaxOptions.dataType === "json" ? JSON.parse(text) : text;
                        });
                    })
                    .then(function(data){
                        if(typeof htAjaxOptions.success === "function"){
                            htAjaxOptions.success(data);
                        }
                    })
                    .catch(function(err){
                        if(typeof htAjaxOptions.error === "function"){
                            htAjaxOptions.error(err);
                        }
                    });
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
            htVar.oToast = new yona.ui.Toast("#yonaToasts", {
                "sTplToast": $("#tplYonaToast").text()
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

    function xssClean(str) {
      var filter = new Filter();

      return filter.defence(str);
    }

    /**
     * 네이티브 <dialog> 엘리먼트에 "배경(backdrop) 클릭으로 닫기"와
     * "[data-dismiss=modal] 클릭으로 닫기"를 붙여준다. Bootstrap .modal()이 전역으로 제공하던
     * 것을 각 다이얼로그마다 대체하기 위한 공용 헬퍼(ESC 닫기는 <dialog> 자체가 네이티브로
     * 처리하므로 별도 구현이 필요 없다).
     *
     * @param {Element} elDialog
     * @param {Boolean} bStaticBackdrop true면 배경 클릭으로 닫지 않는다(Bootstrap의
     *        data-backdrop="static"과 동일 - 예: 아바타 자르기처럼 실수로 닫히면 곤란한 모달).
     */
    function attachDialogDismiss(elDialog, bStaticBackdrop){
        elDialog.addEventListener("click", function(weEvt){
            if(!bStaticBackdrop && weEvt.target === elDialog){
                elDialog.close();
                return;
            }

            var elDismiss = weEvt.target.closest('[data-dismiss="modal"]');
            if(elDismiss && elDialog.contains(elDismiss)){
                elDialog.close();
            }
        });
    }

    /**
     * elTarget이 호출부에 따라 raw DOM 엘리먼트, jQuery 객체로 제각각 넘어오므로
     * 둘 다 raw DOM 엘리먼트로 정규화한다(yona.ui.Dropdown.js의 _toElement와 동일 패턴).
     */
    function _toElement(el){
        if(!el){
            return null;
        }
        if(el.jquery){
            return el[0] || null;
        }
        return el;
    }

    /**
     * Bootstrap .popover() 마크업(popover/arrow/popover-title/popover-content)과
     * 배치별 CSS(.popover.top/.right/.bottom/.left)는 yona.css/bootstrap.css에 이미
     * 갖춰져 있으므로, JS 쪽 위치 계산 로직만 재현한다.
     */
    function _createPopoverElement(sContent, sTitle, sPlacement){
        var elPopover = document.createElement("div");
        elPopover.className = "popover " + (sPlacement || "top");
        // .popover의 기본 display는 none이라(bootstrap.css) show() 시 명시적으로
        // block을 강제해야 한다(Bootstrap 원본의 $tip.css({..., display:'block'})과 동일).
        elPopover.style.display = "block";

        var elArrow = document.createElement("div");
        elArrow.className = "arrow";
        elPopover.appendChild(elArrow);

        if(sTitle){
            var elTitle = document.createElement("h3");
            elTitle.className = "popover-title";
            elTitle.textContent = sTitle;
            elPopover.appendChild(elTitle);
        }

        var elContent = document.createElement("div");
        elContent.className = "popover-content";
        elContent.textContent = sContent;
        elPopover.appendChild(elContent);

        return elPopover;
    }

    /**
     * <dialog>는 브라우저의 top layer에서 렌더링되므로, document.body에 붙인
     * popover는 z-index를 아무리 높여도 열려있는 dialog 뒤에 깔린다(top layer는
     * 일반 stacking context와 완전히 분리됨). 트리거가 열린 dialog 안에 있으면
     * 그 dialog를 popover의 부모로 써서 같은 레이어에서 그려지게 한다.
     */
    function _getPopoverContainer(elTrigger){
        var elDialog = elTrigger.closest("dialog[open]");
        return elDialog || document.body;
    }

    function _positionPopoverElement(elTrigger, elPopover, sPlacement, elContainer){
        var htRect = elTrigger.getBoundingClientRect();
        var nOffsetTop, nOffsetLeft;

        if(elContainer !== document.body){
            // dialog 컨테이너 기준 상대 좌표(둘 다 뷰포트 기준 rect라 스크롤 오프셋 불필요).
            var htContainerRect = elContainer.getBoundingClientRect();
            nOffsetTop = htRect.top - htContainerRect.top;
            nOffsetLeft = htRect.left - htContainerRect.left;
        } else {
            nOffsetTop = htRect.top + window.pageYOffset;
            nOffsetLeft = htRect.left + window.pageXOffset;
        }

        var nPopWidth = elPopover.offsetWidth;
        var nPopHeight = elPopover.offsetHeight;
        var nTop, nLeft;

        switch(sPlacement){
            case "bottom":
                nTop = nOffsetTop + htRect.height;
                nLeft = nOffsetLeft + htRect.width / 2 - nPopWidth / 2;
                break;
            case "left":
                nTop = nOffsetTop + htRect.height / 2 - nPopHeight / 2;
                nLeft = nOffsetLeft - nPopWidth;
                break;
            case "right":
                nTop = nOffsetTop + htRect.height / 2 - nPopHeight / 2;
                nLeft = nOffsetLeft + htRect.width;
                break;
            default: // top
                nTop = nOffsetTop - nPopHeight;
                nLeft = nOffsetLeft + htRect.width / 2 - nPopWidth / 2;
        }

        elPopover.style.top = nTop + "px";
        elPopover.style.left = nLeft + "px";
    }

    /**
     * popover 생성 + (dialog-aware) 컨테이너에 삽입 + 위치 계산 + 표시를 한번에 처리.
     */
    function _showPopoverOn(elTrigger, sContent, sTitle, sPlacement){
        var elContainer = _getPopoverContainer(elTrigger);
        var elPopover = _createPopoverElement(sContent, sTitle, sPlacement);
        elContainer.appendChild(elPopover);
        _positionPopoverElement(elTrigger, elPopover, sPlacement, elContainer);
        elPopover.classList.add("in");
        return elPopover;
    }

    /**
     * data-toggle="popover"(+ data-trigger="hover") 마크업에 대해, hover 시
     * data-content/data-original-title을 popover로 보여준다. Bootstrap 원본은
     * $this.data("popover")로 이미 초기화된 요소를 걸러 재초기화를 막았는데,
     * registerModule()이 loadModule마다 이 셀렉터로 재호출되므로 동일하게
     * elTrigger 자체에 바인딩 여부를 표시해 중복 바인딩을 막는다.
     *
     * @param {String} sSelector
     */
    function initHoverPopovers(sSelector){
        document.querySelectorAll(sSelector).forEach(function(elTrigger){
            if(elTrigger._yonaHoverPopoverBound){
                return;
            }
            elTrigger._yonaHoverPopoverBound = true;

            var nShowTimer, nHideTimer;

            function _remove(){
                if(elTrigger._yonaPopoverEl){
                    elTrigger._yonaPopoverEl.remove();
                    elTrigger._yonaPopoverEl = null;
                }
            }

            elTrigger.addEventListener("mouseenter", function(){
                clearTimeout(nHideTimer);
                nShowTimer = setTimeout(function(){
                    var sContent = elTrigger.getAttribute("data-content");
                    if(!sContent){
                        return;
                    }
                    var sTitle = elTrigger.getAttribute("data-original-title") || "";
                    var sPlacement = elTrigger.getAttribute("data-placement") || "top";

                    _remove();
                    elTrigger._yonaPopoverEl = _showPopoverOn(elTrigger, sContent, sTitle, sPlacement);
                }, 100);
            });

            elTrigger.addEventListener("mouseleave", function(){
                clearTimeout(nShowTimer);
                nHideTimer = setTimeout(_remove, 100);
            });
        });
    }

    /**
     * 입력 필드 등 특정 엘리먼트 옆에 검증 에러 메시지를 즉시(manual trigger)
     * 표시한다 - issue.LabelEditor/user.SignUp/resetPassword/project.New/
     * user.Setting이 공통으로 쓰던 "$el.popover({trigger:manual, content:...}).
     * popover('show')" 패턴을 대체.
     *
     * @param {Element|jQuery} elTarget
     * @param {String} sMessage
     * @param {String} [sPlacement="left"]
     */
    function showPopoverError(elTarget, sMessage, sPlacement){
        var el = _toElement(elTarget);
        if(!el){
            return;
        }

        hidePopoverError(el);

        el._yonaPopoverEl = _showPopoverOn(el, sMessage, "", sPlacement || "left");
    }

    /**
     * @param {Element|jQuery} elTarget
     */
    function hidePopoverError(elTarget){
        var el = _toElement(elTarget);
        if(el && el._yonaPopoverEl){
            el._yonaPopoverEl.remove();
            el._yonaPopoverEl = null;
        }
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
        "htmlspecialchars": htmlspecialchars,
        "isImageFile": isImageFile,
        "xssClean" : xssClean,
        "attachDialogDismiss": attachDialogDismiss,
        "initHoverPopovers": initHoverPopovers,
        "showPopoverError": showPopoverError,
        "hidePopoverError": hidePopoverError
    };
})();

