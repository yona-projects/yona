/**
 * jQuery.requestAs
 * https://github.com/nforge/jquery-plugin-requestAs
 *
 * Copyright (c) 2013 JiHan Kim
 * Date: 2013/08/30
 *
 * @author JiHan Kim <laziel@naver.com>
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
!function($){

    "use strict";
  
    var RequestAs = function(elContainer, htOptions){
        this.init(elContainer, htOptions);

        // jQuery style interfaces
        this.options = this._htData;
        this.on = this._attachCustomEvent;
        this.off = this._detachCustomEvent;
    };
    
    RequestAs.prototype = {
        "constructor": RequestAs,
        
        /**
         * Attach event handler on available target elements
         */
        "init": function(elContainer, htOptions){
            var welTarget = $(elContainer);

            this._htData = this._getRequestOptions(welTarget, htOptions || {});
            this._htHandlers = {};

            // if target element is anchor and request method is GET
            // plain HTML will works enough.
            if(this._htData.sMethod === "get" && el.tagName.toLowerCase() === "a"){
                return;
            }

            // legacy compatibility
            if(typeof this._htData.fOnLoad === "function"){
                this._attachCustomEvent("load", htOptions.fOnLoad);
            }

            if(typeof this._htData.fOnError === "function"){
                this._attachCustomEvent("error", htOptions.fOnError);
            }

            // Set cursor style on target
            welTarget.css("cursor", "pointer");
            
            // Send request on click or keydown enterkey
            welTarget.on("click keydown", $.proxy(this._onClickTarget, this));

            // public interfaces
            return {
                "options": this._htData,
                "on"     : this._attachCustomEvent,
                "off"    : this._detachCustomEvent
            };
        },
        
        /**
         * Event Handler on target element
         * 
         * @param {Wrapped Event} weEvt
         */
        "_onClickTarget": function(weEvt){
            if((weEvt.type === "keydown" && weEvt.keyCode !== 13) || !this._htData){
                return;
            }

            this._sendRequest(this._htData);
            weEvt.preventDefault();
            weEvt.stopPropagation();
            
            return false;
        },
        
        /**
         * Send request
         * 
         * @param {Hash Table} htData
         */
        "_sendRequest": function(htData){
            var htReqOpt = {
                "method"  : htData.sMethod,
                "success" : $.proxy(this._onSuccessRequest, this),
                "error"   : $.proxy(this._onErrorRequest, this),
                "cache"   : false,
                "dataType": "text"
            };
            
            // if htAjaxOpt exists, it overrides current Ajax Options
            // $.ajax "data" option could be used with this
            if(htData.htAjaxOpt instanceof Object){
                htReqOpt = $.extend(htReqOpt, htData.htAjaxOpt);
            }

            // fire custom event "beforeRequest"
            // if any event handler returns explicitly false(Boolean),
            // no request will be sent.
            var bCustomEventResult = this._fireEvent("beforeRequest", htReqOpt);
            if(bCustomEventResult === false){
                return false;
            }

            $.ajax(htData.sHref, htReqOpt);
        },
        
        /**
         * Get request options from target element
         * 
         * @param {HTMLElement} elTarget
         * @param {Hash Table} htOptions
         */
        "_getRequestOptions": function(elTarget, htOptions){
            var welTarget = $(elTarget);

            return {
                "sMethod" : htOptions.sMethod  || welTarget.data("request-method") || "get",
                "sHref"   : htOptions.sHref    || welTarget.data("request-uri") || welTarget.attr("href"),
                "fOnLoad" : htOptions.fOnLoad  || undefined,
                "fOnError": htOptions.fOnError || undefined
            };
        },

        /**
         * Default callback for handle request success event
         * redirects to URL if server responses JSON data
         * (e.g. {"url":"http://www.foobar.com/"})
         * or just reload current page
         * 
         * @param {Object} oRes
         * @param {String} sStatus
         * @param {Object} oXHR
         */
        "_onSuccessRequest": function(oRes, sStatus, oXHR){
            // fire custom event "load"
            // if any event handler returns explicitly false(Boolean),
            // default action will be prevented.
            var bCustomEventResult = this._fireEvent("load", {
                "oRes"   : oRes,
                "oXHR"   : oXHR,
                "sStatus": sStatus
            });
            if(bCustomEventResult === false){
                return;
            }

            // default action below:
            var sLocation = oXHR.getResponseHeader("Location");
            
            if(oXHR.status === 204 && sLocation){
                document.location.href = sLocation;
            } else {
                document.location.reload();
            }
        },
        
        /**
         * Default callback for handle request error event
         * 
         * @param {Object} oXHR
         */
        "_onErrorRequest": function(oXHR){
            // fire custom event "load"
            // if any event handler returns explicitly false(Boolean),
            // default action will be prevented.
            this._fireEvent("error", {
                "oXHR": oXHR
            });

            // default action below:
            switch(oXHR.status){
                case 200:
                    // if server responses ok(200) but cannot determine redirect URL,
                    // reload current page.
                    document.location.reload();
                    break;
                    
                case 204:
                    // if server responses 204, it is client error.
                    // in this case, check AJAX dataType option on _sendRequest
                    document.location.href = oXHR.getResponseHeader("Location");
                    break;
            }

            // need to do something else?
        },

        /**
         * Attach custom event handler
         *
         * @param {String} sEventName
         * @param {Function} fHandler
         * @param {String} sNamespace
         * @example
         * $("#button").data("requestAs").on("eventName", function(){});
         * // or
         * $("#button").data("requestAs").on({
        *    "event1st": function(){},
        *    "event2nd": function(){}
        * });
         */
        "_attachCustomEvent": function(sEventName, fHandler){
            if(typeof sEventName === "object"){
                for(var sKey in sEventName){
                    this._htHandlers[sKey] = this._htHandlers[sKey] || [];
                    this._htHandlers[sKey].push(sEventName[sKey]);
                }
            } else {
                this._htHandlers[sEventName] = this._htHandlers[sEventName] || [];
                this._htHandlers[sEventName].push(fHandler);
            }
        },

        /**
         * Detach custom event handler
         * clears all handler of sEventName when fHandler is empty
         *
         * @param {String} sEventName
         * @param {Function} fHandler
         */
        "_detachCustomEvent": function(sEventName, fHandler){
            if(!fHandler){
                this._htHandlers[sEventName] = [];
                return;
            }

            var aHandlers = this._htHandlers[sEventName];
            var nIndex = aHandlers ? aHandlers.indexOf(fHandler) : -1;

            if(nIndex > -1){
                this._htHandlers[sEventName].splice(nIndex, 1);
            }
        },

        /**
         * Run specified custom event handlers
         *
         * @param {String} sEventName
         * @param {Object} oData
         */
        "_fireEvent": function(sEventName, oData){
            var aHandlers = this._htHandlers[sEventName];

            if ((aHandlers instanceof Array) === false) {
                return;
            }

            var bResult = undefined;

            aHandlers.forEach(function(fHandler){
                bResult = bResult || fHandler(oData);
            });

            return bResult;
        }
    };
    
    // RequestAs Plugin Definition
    var old = $.fn.requestAs;
    
    $.fn.requestAs = function(htOptions){
        return this.each(function(){
            var $this = $(this);
            var data = $this.data("requestAs");
            
            if(!data){
                $this.data("requestAs", data = new RequestAs(this, htOptions));
            }
            if(typeof htOptions == "string") {
                data[htOptions]();
            }
        });
    };

    // RequestAs No Conflict
    $.fn.requestAs.noConflict = function(){
        $.fn.requestAs = old;
        return this;
    };
    
    // RequestAs DATA-API
    $(document).ready(function(){
        $("[data-request-method]").requestAs();
    });
}(window.jQuery);
