/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changgun Kim
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
 * yobi.ui.Calendar
 *
 * @requires Pikaday.js (https://github.com/dbushell/Pikaday/)
 */

(function(ns){
    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(elInput, htOptions){
        var welInput = $(elInput);

        var htVar = {
            "sDateFormat": "YYYY-MM-DD",
            "rxDateFormat": /\d{4}-\d{2}-\d{2}/
        };

        var htElement = {};

        function _init(htOptions){
            _initElement(htOptions);
        }

        function _initElement(htOptions) {
            if (!welInput.data("pickaday")) {
                htVar.oPicker = new Pikaday({
                    "format": htVar.sDateFormat,
                    "field": welInput.get(0),
                    "defaultDate": welInput.val(),
                    "setDefaultDate": true
                });
                welInput.data("pickaday", true);

                htElement.welBtn = welInput.next();

                htElement.welBtn.on("click", function() {
                    htVar.oPicker.show();
                });

                if(welInput.val().length > 0 && htOptions.silent !== true){
                    htVar.oPicker.setDate(welInput.val());
                }
            }
        }

        function _getDate() {
            return htVar.oPicker.getDate();
        }

        function _setDate(dateStr) {
            return htVar.oPicker.setDate(dateStr);
        }

        _init(htOptions || {});

        return {
            "getDate": _getDate,
            "setDate": _setDate
        };
    };

})("yobi.ui.Calendar");

$(function(){
    $('[data-toggle="calendar"]').each(function(i, el){
        yobi.ui.Calendar(el);
    });
});
