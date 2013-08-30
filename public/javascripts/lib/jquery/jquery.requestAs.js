/**
 * @(#)jquery.requestAs.js 2013.08.30
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */
!function ($) {

    "use strict"; // jshint ;_;
  
    var RequestAs = function(elContainer, htOptions){
        this.htData = null;
        this.init(elContainer, htOptions);
    };
    
    RequestAs.prototype = {
        "constructor": RequestAs,
        
        /**
         * Attach event handler on available target elements
         */
        "init": function(elContainer, htOptions){
            var welTarget = $(elContainer);
            this.htData = this._getRequestOptions(welTarget, htOptions || {});
            
            // if target element is anchor and request method is GET
            // plain HTML will works enough.
            if(this.htData.sMethod === "get" && el.tagName.toLowerCase() === "a"){
                return;
            }
            
            // Set cursor style on target
            welTarget.css("cursor", "pointer");
            
            // Send request on click or keydown enterkey
            welTarget.on("click keydown", $.proxy(this._onClickTarget, this));
        },
        
        /**
         * Event Handler on target element
         * 
         * @param {Wrapped Event} weEvt
         */
        "_onClickTarget": function(weEvt){
            if((weEvt.type === "keydown" && weEvt.keyCode !== 13) || !this.htData){
                return;
            }

            this._sendRequest(this.htData);
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
                "success" : htData.fOnLoad,
                "error"   : htData.fOnError,
                "cache"   : false,
                "dataType": "text"
            };
            
            // if htAjaxOpt exists, it overrides current Ajax Options
            // $.ajax "data" parameter can be used with this
            if(htData.htAjaxOpt instanceof Object){
                htReqOpt = $.extend(htReqOpt, htData.htAjaxOpt);
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
                "fOnLoad" : htOptions.fOnLoad  || this._onSuccessRequest,
                "fOnError": htOptions.fOnError || this._onErrorRequest
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