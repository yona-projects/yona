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
        if(charCode === 64 || charCode === 35 || charCode === 58) { // 64 = @, 35 = #, 58 = :
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
        _onLoadUserList();
    }

    function _onLoadUserList(){
        htVar.doesNotDataLoaded = false;

        var searchPending;

        var emojis = [
            { name: "+1", content: "👍" },
            { name: "heart", content: "❤️️" },
            { name: "wink", content: "😘" },
            { name: "smile", content: "🙂" },
            { name: "confused", content: "😕" },
            { name: "check", content: "✅" },
            { name: "hooray", content: "🎉" },
            { name: "sad", content: "😢" },
            { name: "-1", content: "👎" },
            { name: "tada", content: "🎉" },
            { name: "x", content: "❌" },
            { name: "o", content: "⭕" },
            { name: "face smile", content: "😄" },
            { name: "face smile kiss", content: "😙" },
            { name: "face kissing", content: "😗" },
            { name: "face astonished", content: "😲" },
            { name: "face angry", content: "😠" },
            { name: "face scream", content: "😱" },
            { name: "face cry", content: "😢" },
            { name: "face neutral", content: "😐" },
            { name: "face heart", content: "😍" },
            { name: "question?", content: "❓" },
            { name: "!", content: "❗️" },
            { name: "bangbang!", content: "‼️" },
            { name: "beer", content: "🍺" },
            { name: "icecream", content: "🍦" },
            { name: "korea", content: "🇰🇷" },
            { name: "us america", content: "🇺🇸" },
            { name: "fr", content: "🇫🇷" },
            { name: "cn china", content: "🇨🇳" },
            { name: "+100", content: "💯" },
            { name: "heavy check", content: "✔️"},
            { name: "+plus", content: "➕"},
            { name: "-minus", content: "➖️"},
            { name: "cactus", content: "🌵️"},
            { name: "animal cat", content: "🐈"},
            { name: "clover", content: "🍀"},
            { name: "v️", content: "✌️"},
            { name: "lock", content: "🔒"},
            { name: "unlock", content: "🔓"},
            { name: "idea bulb", content: "💡"},
            { name: "bomb", content: "💣"},
            { name: "calendar", content: "📆"},
            { name: "date", content: "📅"},
            { name: "chicken", content: "🐔"},
            { name: "mushroom", content: "🍄"},
            { name: "moneybag", content: "💰"},
            { name: "money dollar", content: "💵"},
            { name: "envelope", content: "✉️"},
            { name: "chart upward", content: "📈"},
            { name: "chart downward", content: "📉"},
            { name: "택배 parcel", content: "📦"},
            { name: "박수 clap", content: "👏"},
            { name: "game joker", content: "🃏"},
            { name: "game cards", content: "🎴"},
            { name: "game die", content: "🎲"},
            { name: "tea", content: "🍵"},
            { name: "coffee", content: "☕"},
            { name: "crystal", content: "🔮"},
            { name: "taxi", content: "🚕"},
            { name: "bus", content: "🚌"},
            { name: "train", content: "🚋"},
            { name: "warn", content: "⚠️"},
            { name: "star", content: "⭐"},
            { name: "phone", content: "☎️"},
        ];
        htElement.welTarget
            .atwho({
                at: "@",
                limit: 10,
                displayTpl: "<li data-value='@${loginid}'><img style='width:20px;height:20px;' src='${image}'> ${name} <small>${loginid}</small></li>",
                suspendOnComposing: false,
                searchKey: "searchText",
                insertTpl: "@${loginid}",
                callbacks: {
                    remoteFilter: function(query, callback) {
                        NProgress.start();
                        clearTimeout(searchPending);
                        searchPending = setTimeout(function () {
                            $.getJSON(htVar.url, { query: query, mentionType: "user" }, function (data) {
                                NProgress.done();
                                callback(data.result)
                            });
                        }, 300);
                    }
                }
            })
            .atwho({
                at: ":",
                limit: 10,
                displayTpl: "<li>${content} <small>${name}</small></li>",
                insertTpl: "${content}",
                data: emojis
            })
            .atwho({
                at: "#",
                limit: 10,
                displayTpl: "<li data-value='#${issueNo}'><small>#${issueNo}</small> ${title}</li>",
                suspendOnComposing: false,
                insertTpl: "#${issueNo}",
                callbacks: {
                    remoteFilter: function(query, callback) {
                        NProgress.start();
                        $.getJSON(htVar.url, {query: query, mentionType: "issue"}, function(data) {
                            NProgress.done();
                            callback(data.result)
                        });
                    },
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
