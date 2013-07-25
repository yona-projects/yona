/**
 * @(#)yobi.ui.Typeahead.js 2013.04.15
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */

/**
 * bootstrap-typeahead.js 사용을 위한 공통 인터페이스
 *   
 * @example 
 * new yobi.ui.Typeahead(htElement.welInputAddTag, {
 *      "sActionURL": htVar.sURLTags,
 *      "htData": {
 *          "context": "PROJECT_TAGGING_TYPEAHEAD",
 *          "project_id": htVar.nProjectId,
 *          "limit": 8
 *      }
 * });
 * 
 * @require bootstrap-typeahead.js
 */

(function(ns){
	
	var oNS = $yobi.createNamespace(ns);
	oNS.container[oNS.name] = function(sQuery, htOptions){

		var htVar = {};
		var htElement = {};
	
		/**
		 * 초기화
		 * Initialize component
		 * @param {String} sQuery ui.Typeahead 를 적용할 대상
		 * @param {Hash Table} htOptions
		 */
		function _init(sQuery, htOptions){
			_initVar(htOptions);
			_initElement(sQuery);
		}
		
		/**
		 * 변수 초기화
		 * Initialize variables
		 * @param {Hash Table} htOptions
		 */
		function _initVar(htOptions){
			htVar.sActionURL = htOptions.sActionURL || "/users";
			htVar.rxContentRange = /items\s+([0-9]+)\/([0-9]+)/;
            htVar.htData = htOptions.htData || {};
		}

        function data(key, value) {
            if (value !== undefined) {
                htVar.htData[key] = value;
            } else {
                return htVar.htData[key];
            }
        }
		
        /**
         * 엘리먼트 초기화
         * Initialize element
         * @param {String} sQuery
         */
        function _initElement(sQuery){
            try {
                htElement.welInput = $(sQuery);
                htElement.welInput.typeahead({ minLength: 0 });
                htData = htElement.welInput.data('typeahead');
                htData.items = htVar.htData.limit || 8;
                htData.source = _onTypeAhead;
                htData.minLength = 0;
            } catch (err){
                if(typeof console == "object") {
                    console.log(err);        	                        
        	}
            }
        }

        /**
        * Data source for loginId typeahead while adding new member.
        *
        * For more information, See "source" option at
        * http://twitter.github.io/bootstrap/javascript.html#typeahead
        *

        * @param {Function} frocess
        */
        function _onTypeAhead(sQuery, fProcess) {
            if (sQuery.match(htVar.sLastQuery) && htVar.bIsLastRangeEntire) {
            	fProcess(htVar.htCachedUsers);
            } else {
                htVar.htData.query = sQuery;
            	$yobi.sendForm({
            		"sURL"		: htVar.sActionURL,
            		"htOptForm"	: {"method":"get"},
            		"htData"	: htVar.htData,
                    "sDataType" : "json",
            		"fOnLoad"	: function(oData, oStatus, oXHR){
            			var sContentRange = oXHR.getResponseHeader('Content-Range');
            			
            			htVar.bIsLastRangeEntire = _isEntireRange(sContentRange);
            			htVar.sLastQuery = sQuery;
            			htVar.htCachedUsers = oData;
            			
            			fProcess(oData);
            			sContentRange = null;
            		}
            	});
            }
        }
		
        /**
         * Return whether the given content range is an entire range for items.
         * e.g) "items 10/10"
         *
         * @param {String} sContentRange the value of Content-Range header from response
         * @return {Boolean}
         */
         function _isEntireRange(sContentRange){
             var aMatch = htVar.rxContentRange.exec(sContentRange || ""); // [1]=total, [2]=items
             return (aMatch) ? !(parseInt(aMatch[1], 10) < parseInt(aMatch[2], 10)) : true;
         }

		_init(sQuery, htOptions || {});
	};
	
})("yobi.ui.Typeahead");
