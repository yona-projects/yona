/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Changgun Kim
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
    oNS.container[oNS.name] = function(element, userOptions){

        var targetElement;

        function _init(element, userOptions){
            targetElement = $(element);

            var options = $.extend({
                "field"      : targetElement.get(0),
                "defaultDate": targetElement.val(),
                "setDefaultDate": true,
                "format": "YYYY-MM-DD"
            }, userOptions);

            if (!targetElement.data("pickaday")){
                var pikaday = new Pikaday(options);
                targetElement.data("pickaday", pikaday);
            }

            targetElement.next(".btn-calendar").on("click", function(){
                targetElement.data("pickaday").show();
            });

            if(targetElement.val().length > 0 && options.silent !== true){
                _setDate(targetElement.val());
            }
        }

        function _getDate(){
            return targetElement.data("pickaday").getDate();
        }

        function _setDate(dateStr){
            return targetElement.data("pickaday").setDate(dateStr);
        }

        _init(element, userOptions || {});

        return {
            "getDate": _getDate,
            "setDate": _setDate
        };
    };

})("yobi.ui.Calendar");

$(function(){
    $('[data-toggle="calendar"]').each(function(i, el){
        yobi.ui.Calendar(el, $(el).data());
    });
});
