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

/**
 * 이전엔 `window.jQuery`를 WeakMap으로 흉내내는 shim이 있었다 - yona-markdown-editor의
 * 구버전 `.data(...)` 요구와, yona.Attachments.js가 쓰던 `window.jQuery.data(el,
 * "isYonaAttachment", ...)` 정적 접근자를 만족시키기 위한 것이었다. 이제 두 계약 모두
 * jQuery 없는 형태(`.value` getter/setter, `_isYonaAttachment` expando 프로퍼티)로
 * 바뀌어 shim이 필요 없어졌다 - 저장소 전체에 jQuery는 벤더 파일에만 남아 있다.
 */
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
     * REST 메서드는 htData._method 히든 필드로 오버라이드하는 관례를 유지한다 - 실제
     * 와이어 상 HTTP 메서드는 htOptForm.method(get/post) 그대로 둬야 한다(PUT/DELETE를
     * fetch에 직접 지정하면 안 됨). method가 get이면 쿼리스트링으로, 아니면
     * URLSearchParams(기본) 또는 FormData(enctype에 "multipart" 포함 시)로 바디를 구성한다.
     *
     * fOnLoad(responseText, statusText, xhr) 시그니처는 그대로 유지한다 - yona-lib.js의
     * site.search 캐시 로직이 세 번째 인자 xhr.getResponseHeader("Content-Range")를 실제로
     * 읽으므로, fetch Response를 감싸 getResponseHeader/status/responseText를 제공하는
     * 최소 XHR 호환 shim을 만들어 넘긴다.
     *
     * @param {Hash Table} htOptions
     * @param {String}        htOptions.sURL 요청 URL
     * @param {Hash Table} htOptions.htOptForm {method, enctype} 등 <form> 속성 - method
     *        기본값은 "post"(원본과 동일)
     * @param {Hash Table} htOptions.htData 전송할 데이터(객체 또는 배열 - 원본이 for-in으로
     *        키를 순회해 히든 필드를 만들던 것과 동일하게 배열도 인덱스 키로 전송된다)
     * @param {Function}   htOptions.fOnLoad callback function on load
     * @param {Function}   htOptions.fOnError callback function on error
     * @param {String}       htOptions.sDataType "json"이면 응답을 JSON.parse해 fOnLoad에 넘긴다
     */
    function sendForm(htOptions){
        var sKey = "";
        var htOptForm = htOptions.htOptForm || {"method":"post"};
        var sMethod = (htOptForm.method || "post").toLowerCase();
        var htData = htOptions.htData || {};
        var sURL = htOptions.sURL;
        var oInit = {"method": sMethod, "credentials": "same-origin"};

        if(sMethod === "get"){
            var oParams = new URLSearchParams();
            for(sKey in htData){
                oParams.append(sKey, htData[sKey]);
            }
            var sQuery = oParams.toString();
            if(sQuery){
                sURL += (sURL.indexOf("?") === -1 ? "?" : "&") + sQuery;
            }
        } else if((htOptForm.enctype || "").indexOf("multipart") !== -1){
            var oFormData = new FormData();
            for(sKey in htData){
                oFormData.append(sKey, htData[sKey]);
            }
            oInit.body = oFormData;
        } else {
            var oBody = new URLSearchParams();
            for(sKey in htData){
                oBody.append(sKey, htData[sKey]);
            }
            oInit.body = oBody;
        }

        fetch(sURL, oInit).then(function(oResponse){
            var fReadBody = (htOptions.sDataType === "json") ? oResponse.json() : oResponse.text();
            return fReadBody.then(function(vBody){
                return {"response": oResponse, "body": vBody};
            }, function(){
                // JSON 파싱 실패 시 원본 jQuery.form과 동일하게 실패를 전파하지 않고 빈 값으로 폴백.
                return {"response": oResponse, "body": (htOptions.sDataType === "json") ? null : ""};
            });
        }).then(function(oResult){
            var oResponse = oResult.response;
            var sResponseText = (typeof oResult.body === "string") ? oResult.body : JSON.stringify(oResult.body);
            var oXHRShim = {
                "status"    : oResponse.status,
                "statusText": oResponse.statusText,
                "responseText": sResponseText,
                "getResponseHeader": function(sName){ return oResponse.headers.get(sName); }
            };

            if(oResponse.ok){
                if(typeof htOptions.fOnLoad === "function"){
                    htOptions.fOnLoad(oResult.body, "success", oXHRShim);
                }
            } else if(typeof htOptions.fOnError === "function"){
                htOptions.fOnError(oXHRShim, "error", oResponse.statusText);
            }
        }).catch(function(oErr){
            if(typeof htOptions.fOnError === "function"){
                htOptions.fOnError({
                    "status": 0,
                    "statusText": "error",
                    "responseText": "",
                    "getResponseHeader": function(){ return null; }
                }, "error", String(oErr));
            }
        });
    }

    /**
     * jquery.requestAs.js 플러그인의 네이티브 대체.
     *
     * 원본 플러그인 계약을 그대로 재현한다 - 엘리먼트당 한 번만 초기화되고(idempotent,
     * el._yonaRequestAs로 캐시 - jQuery `.data("requestAs")`와 동일한 "최초 1회만 생성" 동작,
     * 두 번째 호출부터는 새 htOptions를 무시하고 기존 인스턴스를 그대로 반환한다), click/keydown
     * (Enter)에서 요청을 보내고, 커스텀 이벤트(beforeRequest/load/error)를 등록·발생시킬 수
     * 있는 {options, on, off} 인터페이스를 반환한다. 성공 시 204+Location이면 그 위치로 이동,
     * 아니면 페이지를 새로고침 - 원본 _onSuccessRequest/_onErrorRequest와 동일한 기본 동작이다.
     *
     * @param {Element|jQuery} elArg
     * @param {Hash Table} [htOptions]
     * @param {String} [htOptions.sMethod] 없으면 data-request-method 속성, 없으면 "get"
     * @param {String} [htOptions.sHref] 없으면 data-request-uri 속성, 없으면 href 속성
     * @param {Function} [htOptions.fOnLoad]
     * @param {Function} [htOptions.fOnError]
     * @returns {Object|null} {options, on, off}
     */
    function requestAs(elArg, htOptions){
        var el = _toElement(elArg);
        if(!el){
            return null;
        }
        if(el._yonaRequestAs){
            return el._yonaRequestAs;
        }

        htOptions = htOptions || {};
        var htHandlers = {};
        var htData = {
            "sMethod": (htOptions.sMethod || el.getAttribute("data-request-method") || "get").toLowerCase(),
            "sHref"  : htOptions.sHref || el.getAttribute("data-request-uri") || el.getAttribute("href")
        };

        function _fireEvent(sName, oData){
            var aHandlers = htHandlers[sName];
            if(!(aHandlers instanceof Array)){
                return undefined;
            }
            var bResult;
            aHandlers.forEach(function(fHandler){ bResult = bResult || fHandler(oData); });
            return bResult;
        }

        function _on(sEventName, fHandler){
            if(typeof sEventName === "object"){
                for(var sKey in sEventName){
                    htHandlers[sKey] = htHandlers[sKey] || [];
                    htHandlers[sKey].push(sEventName[sKey]);
                }
            } else {
                htHandlers[sEventName] = htHandlers[sEventName] || [];
                htHandlers[sEventName].push(fHandler);
            }
        }

        function _off(sEventName, fHandler){
            if(!fHandler){
                htHandlers[sEventName] = [];
                return;
            }
            var aHandlers = htHandlers[sEventName];
            var nIndex = aHandlers ? aHandlers.indexOf(fHandler) : -1;
            if(nIndex > -1){
                aHandlers.splice(nIndex, 1);
            }
        }

        // legacy compatibility - htOptions.fOnLoad/fOnError는 "load"/"error" 커스텀 이벤트
        // 핸들러로 등록된다(원본 RequestAs.init()과 동일).
        if(typeof htOptions.fOnLoad === "function"){
            _on("load", htOptions.fOnLoad);
        }
        if(typeof htOptions.fOnError === "function"){
            _on("error", htOptions.fOnError);
        }

        function _onErrorResponse(oResponse, oXHRShim){
            var bResult = _fireEvent("error", {"oXHR": oXHRShim});
            if(bResult === false){
                return;
            }
            var nStatus = oResponse ? oResponse.status : 0;
            if(nStatus === 200){
                document.location.reload();
            } else if(nStatus === 204){
                document.location.href = oResponse.headers.get("Location");
            }
        }

        function _sendRequest(){
            var htReqOpt = {"method": htData.sMethod};
            if(_fireEvent("beforeRequest", htReqOpt) === false){
                return;
            }

            fetch(htData.sHref, {"method": htReqOpt.method, "credentials": "same-origin"})
                .then(function(oResponse){
                    return oResponse.text().then(function(sText){
                        var oXHRShim = {
                            "status": oResponse.status,
                            "responseText": sText,
                            "getResponseHeader": function(sName){ return oResponse.headers.get(sName); }
                        };
                        if(oResponse.ok){
                            var bResult = _fireEvent("load", {"oRes": sText, "oXHR": oXHRShim, "sStatus": "success"});
                            if(bResult === false){
                                return;
                            }
                            var sLocation = oResponse.headers.get("Location");
                            if(oResponse.status === 204 && sLocation){
                                document.location.href = sLocation;
                            } else {
                                document.location.reload();
                            }
                        } else {
                            _onErrorResponse(oResponse, oXHRShim);
                        }
                    });
                })
                .catch(function(){
                    _onErrorResponse(null, {"status": 0, "responseText": "", "getResponseHeader": function(){ return null; }});
                });
        }

        function _onClickOrKeydown(weEvt){
            if(weEvt.type === "keydown" && weEvt.keyCode !== 13){
                return;
            }
            _sendRequest();
            weEvt.preventDefault();
            weEvt.stopPropagation();
        }

        // GET 메서드의 <a> 태그는 원본 플러그인도 클릭 핸들러를 붙이지 않는다(네이티브 앵커
        // 이동만으로 충분하다는 의도) - 이 앱에는 data-request-method="get"이 전혀 없어(전수
        // grep 확인) 실제로는 항상 핸들러가 붙지만, 원본 조건을 그대로 재현해 둔다.
        if(!(htData.sMethod === "get" && el.tagName.toLowerCase() === "a")){
            el.style.cursor = "pointer";
            el.addEventListener("click", _onClickOrKeydown);
            el.addEventListener("keydown", _onClickOrKeydown);
        }

        el._yonaRequestAs = {
            "options": htData,
            "on"     : _on,
            "off"    : _off
        };
        return el._yonaRequestAs;
    }

    /**
     * jquery.requestAs.js의 DATA-API(`$(document).ready(function(){ $("[data-request-
     * method]").requestAs(); })`)와 동일 - 페이지에 정적으로 존재하는 [data-request-method]
     * 엘리먼트를 전부 초기화한다. issue.View.js 등 나중에 동적으로 삽입된 영역은 각자
     * $yona.requestAs(el)을 개별 호출해 추가로 초기화한다(idempotent라 중복 호출해도 안전).
     */
    document.addEventListener("DOMContentLoaded", function(){
        document.querySelectorAll("[data-request-method]").forEach(function(el){
            requestAs(el);
        });
    });

    /**
     * jquery.search.js의 네이티브 대체 - data-toggle="item-search" 입력창에 타이핑하면
     * data-items로 지정된 [data-item="..."] 목록을 data-value 부분일치로 실시간 필터링한다.
     */
    function _initItemSearch(el){
        if(el._yonaSearchBound){
            return;
        }
        el._yonaSearchBound = true;

        var sItem = el.getAttribute("data-items") || "";
        var aItems = document.querySelectorAll('[data-item="' + sItem + '"]');
        var nSearchTimer;

        function _search(sFilter){
            // jQuery `$.trim(filter)`은 앞뒤 공백만 제거한다(내부 공백은 유지) - 빈 입력
            // 여부 판정에만 쓰이고, 실제 부분일치 비교는 트리밍 전(내부 공백 포함) 값을
            // 그대로 쓴다는 점에 주의 - $yona.getTrim()(내부 공백까지 축약)을 쓰면 안 된다.
            if(!sFilter.trim()){
                aItems.forEach(function(elItem){ elItem.style.display = ""; });
                return;
            }
            aItems.forEach(function(elItem){
                var sValue = (elItem.getAttribute("data-value") || "").toLowerCase();
                elItem.style.display = (sValue.indexOf(sFilter) !== -1) ? "" : "none";
            });
        }

        el.addEventListener("keyup", function(){
            clearTimeout(nSearchTimer);
            var sFilter = el.value.toLowerCase();
            nSearchTimer = setTimeout(function(){ _search(sFilter); }, 200);
        });
        el.addEventListener("keydown", function(){
            clearTimeout(nSearchTimer);
        });
    }

    // focus는 버블링하지 않으므로 캡처 단계에서 위임한다.
    document.addEventListener("focus", function(weEvt){
        var matched = weEvt.target.closest && weEvt.target.closest('[data-toggle="item-search"]');
        if(matched && document.contains(matched)){
            _initItemSearch(matched);
        }
    }, true);

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
     *        지원한다(원래 jQuery.ajax settings 전체를 넘기던 것을 실제 호출부의 사용 범위에
     *        맞춰 좁혔다).
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
            // jQuery $("#tplYonaToast").text()는 매치가 없으면 ""를 반환한다(예외 아님) -
            // yona.ui.Toast._initVar()가 htOptions.sTplToast에 .replace()를 바로 호출해
            // undefined면 예외를 던지므로, 같은 no-op 동치를 위해 null 가드 후 ""로 폴백한다.
            var elTplToast = document.getElementById("tplYonaToast");
            htVar.oToast = new yona.ui.Toast("#yonaToasts", {
                "sTplToast": elTplToast ? elTplToast.textContent : ""
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
        // jQuery $("<div>").text(sHTML).html()과 동일한 트릭 - textContent에 넣었다가
        // innerHTML로 다시 꺼내면 <,>,&,",' 등이 브라우저 파서에 의해 그대로 이스케이프된다.
        // jQuery .text(v) setter가 내부적으로 el.textContent = v를 호출하는 것과 동일해
        // 완전히 동치다.
        htVar.elHSC = htVar.elHSC || document.createElement("div");
        htVar.elHSC.textContent = sHTML;
        return htVar.elHSC.innerHTML;
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

        // if vFile is HTMLElement(raw 또는 jQuery 객체 둘 다 호출부 계약대로 허용 -
        // _toElement로 raw element 정규화, yona.ui.Dropdown._toElement와 동일 패턴)
        var elFile = _toElement(vFile);
        var oFileList = elFile ? elFile.files : undefined;

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
        if(elFile && typeof elFile.value === "string"){
            htVar.rxImgExts = htVar.rxImgExts || /\.(gif|bmp|jpg|jpeg|png)$/i;
            return htVar.rxImgExts.test(elFile.value);
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
     * "동의합니다" 체크박스로 게이트된 위험 작업 확인 다이얼로그 공용 헬퍼 -
     * project.Delete/Transfer/ChangeVCS.js 세 파일이 반복 구현하던 "체크 안 됐으면
     * 경고 얼럿, 체크됐으면 모달 열기 + 배경/X 닫기" 부분만 하나로 합쳤다. 다이얼로그를
     * 연 뒤 실제 실행과 실패 처리는 호출부마다 API/메시지가 달라 각 파일에 남겨둔다 -
     * 이 헬퍼는 게이트 체크와 모달 열기/닫기만 담당한다.
     *
     * @param {Element} elOpenButton 클릭 시 게이트를 통과하면 모달을 여는 트리거
     * @param {Element} elCheckbox "동의합니다" 체크박스
     * @param {Element} elDialog 네이티브 <dialog>
     * @param {String} sAlertMessage 체크 안 됐을 때 보여줄 경고 메시지(이미 Messages()로
     *        번역된 문자열 - 호출부가 넘긴다)
     */
    function attachCheckboxGatedConfirm(elOpenButton, elCheckbox, elDialog, sAlertMessage){
        elOpenButton.addEventListener("click", function(weEvt){
            weEvt.preventDefault();
            if(elCheckbox.checked === false){
                showAlert(sAlertMessage);
                return;
            }
            elDialog.showModal();
        });

        attachDialogDismiss(elDialog);
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
     * 페이지에 <yona-popover> 싱글턴(site/layout.html 참고)이 있으면 그 커스텀
     * 엘리먼트에 위임하고, 없으면(격리된 스모크 테스트 등) null을 반환해 아래 각
     * 함수가 원본 vanilla 구현으로 폴백하게 한다.
     */
    function _getVuePopover(){
        return document.querySelector("yona-popover");
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
        var elVuePopover = _getVuePopover();
        if(elVuePopover){
            elVuePopover.initHoverPopovers(sSelector);
            return;
        }
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
     * Bootstrap Tooltip.prototype.fixTitle()과 동일 - title 속성을 최초 1회만
     * data-original-title로 옮겨서, 네이티브 브라우저 툴팁(title이 그대로 있으면 OS가
     * 띄우는 기본 툴팁)과 커스텀 tooltip이 중복 표시되는 것을 막는다.
     *
     * @param {Element} elTrigger
     */
    function _fixTooltipTitle(elTrigger){
        if(elTrigger.getAttribute("data-original-title") === null){
            elTrigger.setAttribute("data-original-title", elTrigger.getAttribute("title") || "");
            elTrigger.setAttribute("title", "");
        }
    }

    /**
     * Bootstrap .tooltip() 마크업(tooltip/tooltip-arrow/tooltip-inner)과 배치별
     * CSS(.tooltip.top/.right/.bottom/.left, .fade.in)는 yona.css/bootstrap.css에
     * 이미 갖춰져 있다 - 위치 계산 공식은 popover와 완전히 동일해 _positionPopoverElement/
     * _getPopoverContainer를 그대로 재사용하고, 마크업 생성만 별도로 둔다.
     *
     * @param {Boolean} bHtml data-html="true" 마크업(issue/view.html의 댓글 추천인 목록 등)
     *        대응 - Bootstrap setContent()의 "html이면 .html(), 아니면 .text()" 분기와 동일.
     */
    function _createTooltipElement(sContent, sPlacement, bHtml){
        var elTooltip = document.createElement("div");
        elTooltip.className = "tooltip fade " + (sPlacement || "top");
        elTooltip.setAttribute("role", "tooltip");

        var elArrow = document.createElement("div");
        elArrow.className = "tooltip-arrow";
        elTooltip.appendChild(elArrow);

        var elInner = document.createElement("div");
        elInner.className = "tooltip-inner";
        if(bHtml){
            elInner.innerHTML = sContent;
        } else {
            elInner.textContent = sContent;
        }
        elTooltip.appendChild(elInner);

        return elTooltip;
    }

    /**
     * data-toggle="tooltip" 마크업(title 또는 이미 옮겨진 data-original-title)에 대해
     * hover/focus 시 즉시 tooltip을 표시한다(Bootstrap Tooltip 기본 옵션 delay:0과 동일).
     * mouseenter/mouseleave/focus/blur는 버블링하지 않으므로 캡처 단계에서 위임한다.
     *
     * @param {Element} elTrigger
     */
    function showTooltip(elTrigger){
        var elVuePopover = _getVuePopover();
        if(elVuePopover){
            elVuePopover.showTooltip(elTrigger);
            return;
        }

        if(!elTrigger || elTrigger._yonaTooltipEl){
            return;
        }
        _fixTooltipTitle(elTrigger);
        var sTitle = elTrigger.getAttribute("data-original-title") || "";
        if(!sTitle){
            return; // Bootstrap hasContent()과 동일 - 표시할 내용이 없으면 아무것도 하지 않는다.
        }
        var sPlacement = elTrigger.getAttribute("data-placement") || "top";
        // jQuery .data("html")은 문자열 "true"를 boolean으로 자동 변환하지만 네이티브
        // getAttribute는 항상 원본 문자열이라 명시 비교로 재현한다.
        var bHtml = elTrigger.getAttribute("data-html") === "true";
        var elContainer = _getPopoverContainer(elTrigger);
        var elTooltip = _createTooltipElement(sTitle, sPlacement, bHtml);
        elContainer.appendChild(elTooltip);
        _positionPopoverElement(elTrigger, elTooltip, sPlacement, elContainer);
        elTooltip.classList.add("in");
        elTrigger._yonaTooltipEl = elTooltip;
    }

    /**
     * @param {Element} elTrigger
     */
    function hideTooltip(elTrigger){
        var elVuePopover = _getVuePopover();
        if(elVuePopover){
            elVuePopover.hideTooltip(elTrigger);
            return;
        }

        var elTooltip = elTrigger && elTrigger._yonaTooltipEl;
        if(!elTooltip){
            return;
        }
        elTrigger._yonaTooltipEl = null;
        elTooltip.classList.remove("in");

        // Bootstrap Tooltip.prototype.hide()의 "트랜지션 종료 후 제거(폴백 500ms)"와 동일.
        var nRemoveTimeout = setTimeout(function(){ elTooltip.remove(); }, 500);
        elTooltip.addEventListener("transitionend", function onTransitionEnd(){
            clearTimeout(nRemoveTimeout);
            elTooltip.removeEventListener("transitionend", onTransitionEnd);
            elTooltip.remove();
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

        var elVuePopover = _getVuePopover();
        if(elVuePopover){
            elVuePopover.showPopoverError(el, sMessage, sPlacement);
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
        if(!el){
            return;
        }

        var elVuePopover = _getVuePopover();
        if(elVuePopover){
            elVuePopover.hidePopoverError(el);
            return;
        }

        if(el._yonaPopoverEl){
            el._yonaPopoverEl.remove();
            el._yonaPopoverEl = null;
        }
    }

    /**
     * Bootstrap .tab() 플러그인의 Tab.prototype.show/activate를 재현한다 - 클릭된
     * 탭 링크가 속한 <li> 목록에서 active를 옮기고, href(또는 data-target)가
     * 가리키는 패널 쪽 형제 목록에서도 active를 옮긴다. fade 트랜지션은 이 앱의
     * 마크업에 쓰이지 않아(직접 대조 확인) 생략했다.
     *
     * @param {Element|jQuery} elTabLink
     */
    function tabShow(elTabLink){
        var el = _toElement(elTabLink);
        if(!el){
            return;
        }

        var elLi = el.closest("li");
        if(elLi && elLi.classList.contains("active")){
            return;
        }

        var sSelector = el.getAttribute("data-target") || el.getAttribute("href");
        // href="#23"처럼 id가 숫자로 시작하면 document.querySelector("#23")은 유효하지
        // 않은 CSS 셀렉터라 예외를 던진다(user/edit_notifications.html의 project.id 앵커로
        // 실제 재현) - getElementById는 순수 문자열 비교라 이 문제가 없다.
        var elTarget = (sSelector && sSelector.charAt(0) === "#") ? document.getElementById(sSelector.slice(1)) : null;

        var elUl = el.closest("ul");
        if(elUl){
            var elActiveLi = elUl.querySelector(":scope > li.active");
            if(elActiveLi){
                elActiveLi.classList.remove("active");
            }
            if(elLi){
                elLi.classList.add("active");
            }
        }

        if(elTarget && elTarget.parentElement){
            var elActivePane = elTarget.parentElement.querySelector(":scope > .active");
            if(elActivePane){
                elActivePane.classList.remove("active");
            }
            elTarget.classList.add("active");
        }
    }

    /**
     * Bootstrap의 $(document).on('click.tab.data-api', '[data-toggle="tab"], ...')
     * 전역 위임과 동일 - 이 문서 전체에 한 번만 등록한다(yona.Common.js는 IIFE로
     * 페이지당 한 번만 평가되므로 중복 바인딩 걱정이 없다).
     */
    document.addEventListener("click", function(weEvt){
        var elTabLink = weEvt.target.closest('[data-toggle="tab"], [data-toggle="pill"]');
        if(elTabLink){
            weEvt.preventDefault();
            tabShow(elTabLink);
        }
    });

    /**
     * Bootstrap 2 bootstrap-dropdown.js DATA-API의 vanilla 재구현.
     *
     * 원본은 document에 핸들러 여러 개를 등록하는데, 그중 `.on('click.dropdown-menu',
     * function(e){ e.stopPropagation() })`은 셀렉터 없이 document에 직접 바인딩돼(원래는
     * `.dropdown-menu`로 위임됐어야 하는 Bootstrap 2.3.1의 알려진 결함) document가
     * 버블링의 끝이라 stopPropagation을 호출해도 관찰 가능한 효과가 없다 - 이 무의미한
     * 줄은 이식하지 않았다. toggle은 clearMenus()로 모든 메뉴를 먼저 닫은 뒤 자신이
     * 닫혀 있었으면 다시 열고, `return false`로 버블링을 끊어 같은 document의 clearMenus가
     * 뒤이어 실행돼 방금 연 메뉴를 즉시 닫아버리는 것을 막는다. keydown(화살표로
     * `[role=menu] li a` 탐색)은 이 앱 어디에도 `.dropdown-menu`에 `role="menu"`가 없어
     * 원본에서도 죽은 경로였다 - 이식하지 않았다.
     */
    function _dropdownGetParent(el){
        var sSelector = el.getAttribute("data-target");
        if(!sSelector){
            sSelector = el.getAttribute("href");
            sSelector = (sSelector && sSelector.indexOf("#") !== -1) ? sSelector.replace(/.*(?=#[^\s]*$)/, "") : null;
        }
        var elParent = (sSelector && sSelector.charAt(0) === "#") ? document.getElementById(sSelector.slice(1)) : null;
        return elParent || el.parentElement;
    }

    function _dropdownClearMenus(){
        document.querySelectorAll('[data-toggle="dropdown"]').forEach(function(el){
            var elParent = _dropdownGetParent(el);
            if(elParent){
                elParent.classList.remove("open");
            }
        });
    }

    /**
     * bootstrap-switch.js(벤더, 미수정)는 파일 맨 끝의
     * `$(function(){ $('.switch')['bootstrapSwitch'](); })`로 호출부 없이 스스로
     * DOMContentLoaded에 초기화하며, `$.fn.bootstrapSwitch` 등록 자체가 진짜 jQuery를
     * 요구한다. 관련 시각 효과는 이 플러그인이 만드는 마크업에 전적으로 의존하는 CSS라
     * jQuery 코어를 완전히 제거하면 이 화면(user/edit_notifications.html)의 토글
     * 스위치가 깨진다 - 그래서 jQuery 코어(및 bootstrap.js)는 아직 제거하지 못했다.
     *
     * 아래 dropdown/button/alert DATA-API와 yona.ui.Switch.js는 모두 이 함수로 "진짜
     * jQuery가 이미 로드돼 있으면 스스로 비활성화"하는 가드를 공유한다 - bootstrap.js의
     * 동일한 전역 DATA-API와 중복 실행(더블 토글 등)되는 것을 막기 위해서다. jQuery
     * 코어가 실제로 제거되면 이 함수가 항상 false를 반환해 가드는 무해하게 남는다.
     */
    function _isRealJQueryStillLoaded(){
        return !!(window.jQuery && window.jQuery.fn);
    }

    document.addEventListener("click", function(weEvt){
        if(_isRealJQueryStillLoaded()){
            return;
        }
        var elToggle = weEvt.target.closest('[data-toggle="dropdown"]');
        if(elToggle){
            // jQuery `$this.is('.disabled, :disabled')` - 엘리먼트 자기 자신만 검사한다
            // (조상까지 검사하지 않음).
            if(elToggle.classList.contains("disabled") || elToggle.disabled){
                return;
            }

            var elParent = _dropdownGetParent(elToggle);
            var bWasOpen = !!(elParent && elParent.classList.contains("open"));

            _dropdownClearMenus();

            if(!bWasOpen && elParent){
                elParent.classList.add("open");
            }

            elToggle.focus();
            weEvt.preventDefault();
            weEvt.stopPropagation();
            return;
        }

        if(weEvt.target.closest(".dropdown form")){
            return;
        }

        _dropdownClearMenus();
    });

    /**
     * Bootstrap 2 bootstrap-button.js `[data-toggle^=button]` DATA-API의 vanilla 재구현.
     * 원본은 클릭된 엘리먼트에서 가장 가까운 `.btn`을 찾아, `[data-toggle="buttons-radio"]`
     * 조상이 있으면 그 안의 `.active`를 전부 제거한 뒤 자신에 `.active`를 토글한다.
     *
     * 이 앱의 실사용처(watch-button 등)는 전부 `class="ybtn"`(커스텀 클래스)이라 `.btn`에
     * 매치되지 않아 이 DATA-API 자체는 사실상 no-op이다 - watch 토글의 실제 클래스 전환은
     * 각 페이지의 별도 클릭 핸들러가 전담한다. 원본 알고리즘은 향후 실제 `.btn` 클래스
     * 엘리먼트가 추가될 경우에 대비해 그대로 이식해 둔다.
     */
    document.addEventListener("click", function(weEvt){
        if(_isRealJQueryStillLoaded()){
            return;
        }
        if(!weEvt.target.closest('[data-toggle^="button"]')){
            return;
        }

        var elBtn = weEvt.target;
        if(!elBtn.classList || !elBtn.classList.contains("btn")){
            elBtn = elBtn.closest(".btn");
        }
        if(!elBtn){
            return;
        }

        var elRadioParent = elBtn.closest('[data-toggle="buttons-radio"]');
        if(elRadioParent){
            elRadioParent.querySelectorAll(".active").forEach(function(elActive){
                elActive.classList.remove("active");
            });
        }

        elBtn.classList.toggle("active");
    });

    /**
     * Bootstrap 2 bootstrap-alert.js `[data-dismiss="alert"]` DATA-API의 vanilla 재구현.
     * carousel/collapse/typeahead는 이 앱에 실사용처가 없어 이식하지 않았지만, 이
     * DATA-API는 `templates/user/lostPassword.html`(비밀번호 재설정 성공/실패 알림
     * 박스)에서 실제로 쓰인다.
     *
     * (다른 두 곳의 `data-dismiss="alert"` 마크업은 실사용이 아니다: userList.html의
     * 2곳은 `<script type="text/x-jquery-tmpl">` 안에 있어 이미 죽은 코드고,
     * partial_recently_pushed_branches.html의 "×" 링크는 `data-request-method="delete"`도
     * 함께 달려 있어 $yona.requestAs의 클릭 리스너가 먼저 stopPropagation()을 호출하는
     * 바람에 이 DATA-API에 애초에 도달하지 못한다.)
     *
     * 원본 알고리즘: data-target 속성이 있으면 그 셀렉터, 없으면 href의 "#..." 부분을
     * 셀렉터로 쓴다 - lostPassword.html은 둘 다 없는 <button>이라 `$this.hasClass('alert')
     * ? $this : $this.parent()`로 폴백한다. 취소 가능한 'close' 이벤트를 쏘고, 기본 동작이
     * 막히지 않았으면 'in' 클래스 제거 후 'closed' 이벤트를 쏘고 DOM에서 제거한다.
     *
     * href="#"인 pullrequest 링크의 셀렉터 폴백은 jQuery라면 예외를 던질 수 있는 엣지케이스지만,
     * 그 링크는 위에서 설명한 대로 stopPropagation() 때문에 이 핸들러에 도달하지 않으므로
     * 예외를 재현하는 대신 안전하게 무시한다.
     */
    function _alertGetTargetSelector(el){
        var sSelector = el.getAttribute("data-target");
        if(!sSelector){
            var sHref = el.getAttribute("href");
            sSelector = (sHref && sHref.indexOf("#") !== -1) ? sHref.replace(/.*(?=#[^\s]*$)/, "") : null;
        }
        return sSelector;
    }

    document.addEventListener("click", function(weEvt){
        if(_isRealJQueryStillLoaded()){
            return;
        }
        var elTrigger = weEvt.target.closest('[data-dismiss="alert"]');
        if(!elTrigger){
            return;
        }
        weEvt.preventDefault();

        var sSelector = _alertGetTargetSelector(elTrigger);
        var elParent = null;
        if(sSelector){
            try {
                elParent = document.querySelector(sSelector);
            } catch(oParseError){
                elParent = null; // 위 주석 참고 - jQuery는 던질 수 있는 경우지만 안전하게 무시
            }
        }
        if(!elParent){
            elParent = elTrigger.classList.contains("alert") ? elTrigger : elTrigger.parentElement;
        }
        if(!elParent){
            return;
        }

        var closeEvent = new Event("close", {"bubbles": true, "cancelable": true});
        elParent.dispatchEvent(closeEvent);
        if(closeEvent.defaultPrevented){
            return;
        }

        elParent.classList.remove("in");
        elParent.dispatchEvent(new Event("closed", {"bubbles": true}));
        if(elParent.parentNode){
            elParent.parentNode.removeChild(elParent);
        }
    });

    /* public Interface */
    return {
        "setScriptPath"   : setScriptPath,
        "createNamespace" : createNamespace,
        "getContrastColor": getContrastColor,
        "loadModule": loadModule,
        "loadScript": loadScript,
        "stopEvent" : stopEvent,
        "sendForm"  : sendForm,
        "requestAs" : requestAs,
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
        "attachCheckboxGatedConfirm": attachCheckboxGatedConfirm,
        "initHoverPopovers": initHoverPopovers,
        "showPopoverError": showPopoverError,
        "hidePopoverError": hidePopoverError,
        "tabShow": tabShow,
        "showTooltip": showTooltip,
        "hideTooltip": hideTooltip
    };
})();

