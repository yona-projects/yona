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

        var vars = {};
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
            elements.webhookListWrap = $(".webhook-list-wrap");
            elements.payloadUrl = elements.form.find('input[name="payloadUrl"]');
            elements.secret = elements.form.find('input[name="secret"]');
        }

        /**
         * Initialize variables
         *
         * @param options
         * @private
         */
        function _initVar(options) {
            vars.actionURL = elements.form.prop("action");
        }

        /**
         * Attach event handlers
         * @private
         */
        function _attachEvent(){
            elements.form.on("submit", _onSubmitForm);
            elements.webhookListWrap.on("click", "[data-delete-uri]", _onClickBtnDeleteWebhook);
        }

        /**
         * "submit" event handler of form
         * After Validate form before submit, send request via $.ajax
         *
         * @returns {boolean}
         * @private
         */
        function _onSubmitForm() {
            if (!_isFormValid()) {
                return false;
            }

            // Send request to add webhook.
            _requestAddWebhook({
                "payloadUrl" : elements.payloadUrl.val(),
                "secret"     : elements.secret.val()
            });

            return false;
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

        /**
         * Send request to add webhook with given data
         * called from _onSubmitForm.
         *
         * @param requestData
         * @private
         */
        function _requestAddWebhook(requestData){
            $.ajax(vars.actionURL, {
                "method": "post",
                "data"  : requestData
            })
            .done(function(res){
                if (res instanceof Object){
                    document.location.reload(true);
                    return;
                }

                $yobi.alert(Messages("project.webhook.error.creationFailed"));
            })
            .fail(function(res) {
                try {
                    var error = JSON.parse(res.responseText);
                    var errorText = Messages("project.webhook.failedTo", Messages("project.webhook.new"));

                    for (var key in error) {
                        errorText += "\n" + error[key];
                    }

                    $yobi.alert(errorText);
                } catch(e) {
                    $yobi.alert(Messages("error.failedTo", Messages("project.webhook.new"), res.status, res.statusText));
                }
            });
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
