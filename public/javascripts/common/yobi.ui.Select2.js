/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jihan Kim
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
 * yobi.ui.Select2
 *
 * data-format 속성으로 해당 select 항목의 포맷을 지정할 수 있다
 *
 * @requires select2.js (http://ivaynberg.github.io/select2/)
 */

(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(elSelect, htOptions){
        var welSelect = $(elSelect);

        // Select2.js default options
        var htOpt = $.extend({
            "width": "resolve",
            "dropdownCssClass" : welSelect.data("dropdownCssClass"),
            "containerCssClass": welSelect.data("containerCssClass")
        }, htOptions);

        // Customized formats
        var htFormat = {
            "user": function(oItem){
                var welItem = $(oItem.element);
                var sAvatarURL = welItem.data("avatarUrl");

                if(!sAvatarURL){
                    return '<div>' + oItem.text + '</div>';
                }

                // Template text
                var sTplUserItem = $("#tplSelect2FormatUser").text() || '<div class="usf-group" title="${name} ${loginId}">' +
                    '<span class="avatar-wrap smaller"><img src="${avatarURL}" width="20" height="20"></span>' +
                    '<strong class="name">${name}</strong>' +
                    '<span class="loginid">${loginId}</span></div>';

                var sText = $yobi.tmpl(sTplUserItem, {
                    "avatarURL": sAvatarURL,
                    "name"     : oItem.text.trim(),
                    "loginId"  : "@" + welItem.data("loginId")
                });

                return sText;
            },
            "milestone": function(oItem){
                var welItem = $(oItem.element);
                var sMilestoneState = welItem.data("state");

                if(!sMilestoneState){
                    return oItem.text;
                }

                sMilestoneState = sMilestoneState.toLowerCase();
                var sMilestoneStateLabel = Messages("milestone.state." + sMilestoneState);
                var sTplMilestoneItem = $("#tplSElect2FormatMilestone").text()
                                    || '<div title="[${stateLabel}] ${name}">${name}</div>';

                var sText = $yobi.tmpl(sTplMilestoneItem, {
                    "name" : oItem.text.trim(),
                    "state": sMilestoneState,
                    "stateLabel": sMilestoneStateLabel
                });

                return sText;
            },
            "issuelabel": function(oItem){
                var welItem = $(oItem.element);
                var sLabelId = welItem.val();

                if(!sLabelId){
                    return '<span>' + oItem.text.trim() + '</span>';
                }

                return '<a class="label issue-label active static" data-labelid="' + sLabelId + '">' + oItem.text.trim() + '</a>';
            }
        };

        // Custom matchers
        var htMatchers = {
            "user": function(sTerm, sText, welItem){
                sTerm = sTerm.toLowerCase();
                sText = sText.toLowerCase();

                var sLoginId = welItem.data("loginId");
                sLoginId = (typeof sLoginId !== "undefined") ? sLoginId.toLowerCase() : "";

                return (sLoginId.indexOf(sTerm) > -1) || (sText.indexOf(sTerm) > -1);
            }
        };

        // Use customized format if specified format exists
        var sFormatName = welSelect.data("format");
        var fFormat = sFormatName ? htFormat[sFormatName.toLowerCase()] : null;
        var fMatcher = sFormatName ? htMatchers[sFormatName.toLowerCase()] : null;

        if(typeof fFormat === "function"){
            htOpt = $.extend(htOpt, {
                "formatResult"   : fFormat,
                "formatSelection": fFormat
            });
        }

        if(typeof fMatcher === "function"){
            htOpt.matcher = fMatcher;
        }

        if(typeof fSorter === "function"){
            htOpt.sortResults = fSorter;
        }

        return welSelect.select2(htOpt);
    };

})("yobi.ui.Select2");

/**
 * data-toggle="select2" 로 지정한 select 엘리먼트에 select2.js 를 적용한다
 */
$(function(){
    $('[data-toggle="select2"]').each(function(i, el){
        yobi.ui.Select2(el);
    });
});
