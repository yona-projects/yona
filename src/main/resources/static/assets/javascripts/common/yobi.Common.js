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
window.yobi = (typeof yobi == "undefined") ? {} : yobi;

$yobi = yobi.Common = (function(){

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
     * @param {String} sName namespace string like 'yobi.module.Name'
     * @returns {Hash Table} container object and last name of argument
     * @example
     * var oNS = createNamespace("yobi.module.Name");
     * oNS.container[oNS.name] = { ... };
     * // oNS.container === yobi.module
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

            var sURL = htVar.sScriptPath + "service/yobi." + sName + ".js";
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
        var oModule = yobi[sDepth];

        while(aNames.length && oModule){
            sDepth = aNames.shift();
            oModule = oModule[sDepth];
        }

        $("[data-toggle=popover]").popover();

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
            htVar.oAlertDialog = new yobi.ui.Dialog("#yobiDialog");
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
            htVar.oConfirmDialog = new yobi.ui.Dialog("#yobiDialog");
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
            htVar.oToast = new yobi.ui.Toast("#yobiToasts", {
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
        "xssClean" : xssClean
    };
})();

