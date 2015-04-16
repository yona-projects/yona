/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Suwon Chae
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
yobi.Mention = function(htOptions) {

    var htVar = {};
    var htElement = {};

    /**
     * Initialize
     *
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement();
        _attachEvent();
    }

    /**
     * Initialize Variables
     *
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions) {
        htVar = htOptions || {}; // set htVar as htOptions
        htVar.doesNotDataLoaded = true;
        htVar.nKeyupEventGenerator = null;
        htVar.sMentionText = null;
    }

    /**
     * Initialize Element variables
     */
    function _initElement() {
        if (!htVar.target) {
            if (window.console) {
                console.error("mention form element targeting doesn't exist!")
            }
            return;
        }
        htElement.welTarget = $(htVar.target);
    }
    /**
     * attachEvent
     */
    function _attachEvent() {
        htElement.welTarget.on("keypress", _onKeyInput);
        if (jQuery.browser.mozilla){
            htElement.welTarget.on("focus", _startKeyupEventGenerator);
            htElement.welTarget.on("blur", _stopKeyupEventGenerator);
        }
    }

    /**
     * Event handler on KeyInput event
     *
     * @param {Event} eEvt
     */
    function _onKeyInput(eEvt){
        eEvt = eEvt || window.event;

        var charCode = eEvt.which || eEvt.keyCode;
        if(charCode === 64 || charCode === 35) { // 64 = @, 35 = #
            if(htVar.doesNotDataLoaded) {
                _findMentionList();
            }
        }
    }

    function _startKeyupEventGenerator(){
        if (htVar.nKeyupEventGenerator){
            clearInterval(htVar.nKeyupEventGenerator);
        }

        htVar.nKeyupEventGenerator = setInterval(
            function(){
                if (htVar.sMentionText != htElement.welTarget.val()){
                    htElement.welTarget.trigger("keyup");
                    htVar.sMentionText = htElement.welTarget.val();
                }
            }
            ,100);
    }

    function _stopKeyupEventGenerator(){
        if (htVar.nKeyupEventGenerator){
            clearInterval(htVar.nKeyupEventGenerator);
            htVar.nKeyupEventGenerator = null;
        }
    }

    /**
     * Find Userlist
     */
    function _findMentionList(){
        $.ajax({
            "url"        : htVar.url,
            "type"       : "get",
            "contentType": "application/json",
            "dataType"   : "json",
            "beforeSend" : function() {
                NProgress.start();
            }
        }).done(function(data){
            NProgress.done();
            _onLoadUserList(data);
        });
    }

    function _onLoadUserList(aData){
        htVar.doesNotDataLoaded = false;

        htElement.welTarget
            .atwho({
                at: "@",
                limit: 10,
                data: aData.result,
                tpl: "<li data-value='@${loginid}'><img style='width:20px;height:20px;' src='${image}'> ${username} <small>${loginid}</small></li>",
                show_the_at: true
            })
            .atwho({
                at: "#",
                limit: 10,
                tpl: '<li data-value="#${issueNo}"><small>#${issueNo}</small> ${title}</li>',
                data: aData.issues,
                callbacks: {
                    sorter: function(query, items, searchKey) {
                        var item, i, len, results;
                        if (!query) {
                            return items;
                        }
                        results = [];
                        for (i = 0, len = items.length; i < len; i++) {
                            item = items[i];

                            if (item.issueNo === query) {
                                item.atwhoOrder = 0;
                            } else {
                                var issueNoIndexOf = item.issueNo.toLowerCase().indexOf(query.toLowerCase());
                                item.atwhoOrder = i + 1
                                    + Math.pow(10, issueNoIndexOf)
                                    + ((issueNoIndexOf > -1) ? 0 : Math.pow(100, item.title.toLowerCase().indexOf(query.toLowerCase())));
                            }
                            results.push(item);
                        }
                        return results.sort(function(a, b) {
                            return a.atwhoOrder - b.atwhoOrder;
                        });
                    }
                }
            })
            .atwho("run");
    }

    _init(htOptions || {});
};
