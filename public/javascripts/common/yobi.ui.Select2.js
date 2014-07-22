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
 * @requires select2.js (http://ivaynberg.github.io/select2/)
 */

(function(ns){

    "use strict";

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(elSelect, htOptions){
        var welSelect = $(elSelect);

        // Select2.js default options
        var htOpt = $.extend({
            "width": "resolve",
            "allowClear": welSelect.data("allowClear"),
            "closeOnSelect": welSelect.data("closeOnSelect"),
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

                var sLoginId = welItem.data("loginId") ? "@" + welItem.data("loginId") : "";

                var sText = $yobi.tmpl(sTplUserItem, {
                    "avatarURL": sAvatarURL,
                    "name"     : oItem.text.trim(),
                    "loginId"  : sLoginId
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
            "issuelabel": function(item){
                var element = $(item.element);
                var labelId = element.val();

                if(!labelId){ // = optgroup
                    var isCategoryExclusive = element.data("categoryIsExclusive");
                    var data = {
                        "text" : item.text.trim(),
                        "title": Messages("label.category.option") + '<br>' +
                                (isCategoryExclusive ? Messages("label.category.option.single")
                                                     : Messages("label.category.option.multiple")),
                        "css"  : isCategoryExclusive ? 'yobicon-tag  category-exclusive single'
                                                     : 'yobicon-tags category-exclusive multiple'
                    };
                    var tpl = '<i class="${css}" data-toggle="tooltip" data-html="true" data-placement="right" title="${title}"></i><span>${text}</span>';

                    return $yobi.tmpl(tpl, data);
                }

                return '<a class="label issue-label active static" data-label-id="' + labelId + '">' + item.text.trim() + '</a>';
            },
            "branch": function(oItem){
                var sBranchType = "";
                var sBranchName = oItem.text.trim();
                var rxBranchName = /refs\/(.[a-z]+)\/(.+)/i;

                // parse sBranchName with regular expression rxBranchName
                // e.g.'refs/heads/feature/review-10'
                // -> ["refs/heads/feature/review-10", "heads", "feature/review-10"]
                var aParsedBranchName = sBranchName.match(rxBranchName);
                var htBranchTypeMapByName = {
                    "heads": "branch",
                    "tags" : "tag"
                };

                if(aParsedBranchName){
                    sBranchType = htBranchTypeMapByName[aParsedBranchName[1]] || aParsedBranchName[1];
                    sBranchName = aParsedBranchName[2];
                }

                var sTplBranchItem = $("#tplSelect2FormatBranch").text()
                                  || '<strong class="branch-label ${branchType}">${branchType}</strong> ${branchName}';

                var sBranchNameHTMLWithLabel = $yobi.tmpl(sTplBranchItem, {
                    "branchType": sBranchType,
                    "branchName": sBranchName
                });

                return sBranchNameHTMLWithLabel;
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

        // Custom behaviors
        var htBehaviors = {
            "issuelabel": function(select2Element){
                select2Element.on({
                    "select2-selecting": _onSelectingIssueLabel,
                    "select2-open"     : _onOpenIssueLabel
                });

                function _onSelectingIssueLabel(evt){
                    var data = [evt.object];
                    var element = $(evt.object.element);
                    var select2Object = $(evt.target).data("select2");

                    if(element.data("categoryIsExclusive")){
                        var filtered = _filterLabelInSameCategory(evt.object, select2Object.data());
                        data = data.concat(filtered);
                    } else {
                        data = data.concat(select2Object.data());
                    }

                    _rememberLastScrollTop();

                    select2Object.data(data, true); // trigger "change" event

                    _refreshDropdown(select2Object);

                    evt.preventDefault();
                    return false;
                }

                function _filterLabelInSameCategory(label, currentData){
                    var categoryId = $(label.element).data("categoryId");

                    return currentData.filter(function(data){
                        return (categoryId !== $(data.element).data("categoryId"));
                    });
                }

                function _rememberLastScrollTop(){
                    var lastScrollTop = $("#select2-drop").find(".select2-results").scrollTop();
                    select2Element.data("lastScrollTop", lastScrollTop);
                }

                function _restoreLastScrollTop(){
                    var lastScrollTop = select2Element.data("lastScrollTop");

                    if(lastScrollTop){
                        $("#select2-drop").find(".select2-results").scrollTop(lastScrollTop);
                        select2Element.data("lastScrollTop", null);
                    }
                }

                function _refreshDropdown(select2Object){
                    select2Object.close();
                    select2Object.open();
                }

                function _onOpenIssueLabel(){
                    _restoreLastScrollTop();
                }
            }
        };

        // Use customized format if specified format exists
        var sFormatName = welSelect.data("format");
        var fFormat = sFormatName ? htFormat[sFormatName.toLowerCase()] : null;
        var fMatcher = sFormatName ? htMatchers[sFormatName.toLowerCase()] : null;
        var fBehavior = sFormatName ? htBehaviors[sFormatName.toLowerCase()] : null;

        if(typeof fFormat === "function"){
            htOpt = $.extend(htOpt, {
                "formatResult"   : fFormat,
                "formatSelection": fFormat
            });
        }

        if(typeof fMatcher === "function"){
            htOpt.matcher = fMatcher;
        }

        if(typeof fBehavior === "function"){
            fBehavior(welSelect);
        }

        return welSelect.select2(htOpt);
    };
})("yobi.ui.Select2");

$(function(){
    $('[data-toggle="select2"]').each(function(i, el){
        yobi.ui.Select2(el);
    });
});
