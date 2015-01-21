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
/**
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
         * Initialize component
         * @param {String} sQuery
         * @param {Hash Table} htOptions
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

        function data(key, value) {
            if (value !== undefined) {
                htVar.htData[key] = value;
            } else {
                return htVar.htData[key];
            }
        }

        /**
         * Initialize element
         * @param {String} sQuery
         */
        function _initElement(sQuery){
            try {
                htElement.welInput = $(sQuery);
                htElement.welInput.typeahead({ minLength: htVar.htData.minLength || 0 });
                htData = htElement.welInput.data('typeahead');
                htData.items = htVar.htData.limit || 8;
                htData.source = htVar.htData.source || _onTypeAhead;

                if(typeof htVar.htData.updater === "function"){
                    htData.updater = htVar.htData.updater;
                }

                if(typeof htVar.htData.render === "function"){
                    htData.render = htVar.htData.render;
                }

                htData.minLength = htVar.htData.minLength || 0;
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
        * @param {Function} fProcess
        */
        function _onTypeAhead(sQuery, fProcess) {
            if (sQuery.match(htVar.sLastQuery) && htVar.bIsLastRangeEntire) {
                fProcess(htVar.htCachedUsers);
            } else {
                htVar.htData.query = sQuery;
                $yobi.sendForm({
                    "sURL"        : htVar.sActionURL,
                    "htOptForm"    : {"method":"get"},
                    "htData"    : htVar.htData,
                    "sDataType" : "json",
                    "fOnLoad"    : function(oData, oStatus, oXHR){
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
