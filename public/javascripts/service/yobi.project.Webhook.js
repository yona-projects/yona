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
            _initElement();
            _attachEvent();
        }

        /**
         * Initialize element variables
         * @param options
         * @private
         */
        function _initElement(){
            elements.webhookListWrap = $(".webhook-list-wrap");
        }

        /**
         * Attach event handlers
         * @private
         */
        function _attachEvent(){
            elements.webhookListWrap.on("click", "[data-delete-uri]", _onClickBtnDeleteWebhook);
        }

        /**
         * "Click" event handler of webhook row delete button
         * Send request to remove webhook.
         *
         * @param event
         * @private
         */
        function _onClickBtnDeleteWebhook(evt){
            // TODO: Decide whether to show confirm modal or not
            _requestRemoveWebhook(evt.target);
        }

        /**
         * Send AJAX request to remove webhook with specified delete button
         *
         * @param target
         * @private
         */
        function _requestRemoveWebhook(target){
            var targetButton = $(target);

            $.ajax(targetButton.data("deleteUri"), {
                "method": "post",
                "data"  : {"_method": "delete"}
            })
            .done(function(){
                _removeWebhookFromView(targetButton.data("webhookId"));
            });
        }

        /**
         * Remove specified webhook from webhook view
         *
         * @param webhookId
         * @private
         */
        function _removeWebhookFromView(webhookId){
            $('div[data-webhook-id="' + webhookId + '"]').remove();
        }

        _init(options || {});
    };

})("yobi.project.Webhook");
