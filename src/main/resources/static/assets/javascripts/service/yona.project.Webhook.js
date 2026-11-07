/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2015 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihwan Chun
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

(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(options){

        var elements = {};

        /**
         * Initialize
         * @param options
         * @private
         */
        function _init(options){
            _initElement(options);
            _initVar(options);
            _attachEvent();
        }

        /**
         * Initialize element variables
         * @param options
         * @private
         */
        function _initElement(options){
            elements.form = $(options.form);
            elements.payloadUrl = elements.form.find('input[name="payloadUrl"]');
        }

        /**
         * Initialize variables
         *
         * @param options
         * @private
         */
        function _initVar(options) {
            // Reserved for future development
        }

        /**
         * Attach event handlers
         * @private
         */
        function _attachEvent(){
            elements.form.on("submit", _isFormValid);
        }

        /**
         * Returns whether is form valid
         * and shows error if invalid.
         *
         * @returns {boolean}
         * @private
         */
        function _isFormValid(){
            if (elements.payloadUrl.val().length === 0) {
                $yobi.alert(Messages("project.webhook.payloadUrl.empty"));
                return false;
            }
            return true;
        }

        _init(options || {});
    };

})("yobi.project.Webhook");
