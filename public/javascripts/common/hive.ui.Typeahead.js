/**
 * @(#)hive.ui.Typeahead.js 2013.04.15
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(sQuery, htOptions){

		var htVar = {};
		var htElement = {};
	
		/**
		 * Initialize component
		 */
		function _init(sQuery, htOptions){
			_initVar(htOptions);
			_initElement(sQuery);
		}
		
		/**
		 * Initialize variables
		 * @param {Hash Table} htOptions
		 */
		function _initVar(htOptions){
			htVar.sActionURL = htOptions.sActionURL || "/users";
			htVar.rxContentRange = /items\s+([0-9]+)\/([0-9]+)/;
            htVar.htData = htOptions.htData || {};
		}
		
		/**
		 * Initialize element
		 * @requires bootstrap.js
		 * @param {String} sQuery
		 */
		function _initElement(sQuery){
			htElement.welInput = $(sQuery);
			htElement.welInput.typeahead({
                source: _onTypeAhead,
                minLength: 0,
                items: (parseInt(htVar.htData.limit) !== NaN) ? htVar.htData.limit : 8
            });
		}
		
        /**
        * Data source for loginId typeahead while adding new member.
        *
        * For more information, See "source" option at
        * http://twitter.github.io/bootstrap/javascript.html#typeahead
        *
        * @param {String} sQuery
        * @param {Function} fProcess
        */
        function _onTypeAhead(sQuery, fProcess) {
            if (sQuery.match(htVar.sLastQuery) && htVar.bIsLastRangeEntire) {
            	fProcess(htVar.htCachedUsers);
            } else {
                htVar.htData.query = sQuery;
            	$hive.sendForm({
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
	
})("hive.ui.Typeahead");
