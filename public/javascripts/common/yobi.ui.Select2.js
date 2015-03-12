/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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
 * yobi.ui.Select2
 *
 * @requires select2.js (http://ivaynberg.github.io/select2/)
 */

(function(ns){

    "use strict";

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(element, options){
        var targetElement = $(element);

        var select2Option = $.extend({
            "width"            : "resolve",
            "allowClear"       : targetElement.data("allowClear"),
            "closeOnSelect"    : targetElement.data("closeOnSelect"),
            "dropdownCssClass" : targetElement.data("dropdownCssClass"),
            "containerCssClass": targetElement.data("containerCssClass")
        }, options);

        // Customized formats
        var formatters = {
            "user": function(itemObject){
                var itemElement = $(itemObject.element);
                var avatarURL = itemElement.data("avatarUrl");

                if(!avatarURL){
                    return '<div>' + itemObject.text + '</div>';
                }

                // Template text
                var tplUserItem = $("#tplSelect2FormatUser").text() || '<div class="usf-group" title="${name} ${loginId}">' +
                    '<span class="avatar-wrap smaller"><img src="${avatarURL}" width="20" height="20"></span>' +
                    '<strong class="name">${name}</strong>' +
                    '<span class="loginid">${loginId}</span></div>';

                var loginId = itemElement.data("loginId") ? "@" + itemElement.data("loginId") : "";

                var formattedResult = $yobi.tmpl(tplUserItem, {
                    "avatarURL": avatarURL,
                    "name"     : itemObject.text.trim(),
                    "loginId"  : loginId
                });

                return formattedResult;
            },
            "milestone": function(itemObject){
                var itemElement = $(itemObject.element);
                var milestoneState = itemElement.data("state");

                if(!milestoneState){
                    return itemObject.text;
                }

                milestoneState = milestoneState.toLowerCase();
                var milestoneStateLabel = Messages("milestone.state." + milestoneState);
                var tplMilestoneItem = $("#tplSElect2FormatMilestone").text()
                                    || '<div title="[${stateLabel}] ${name}">${name}</div>';

                var formattedResult = $yobi.tmpl(tplMilestoneItem, {
                    "name" : itemObject.text.trim().replace('<', '&lt;'),
                    "state": milestoneState,
                    "stateLabel": milestoneStateLabel
                });

                return formattedResult;
            },
            "issuelabel": function(itemObject){
                var element = $(itemObject.element);
                var labelId = element.val();
                var text = $.trim($('<div/>').text(itemObject.text).html());

                if(!labelId){ // = optgroup
                    var isCategoryExclusive = element.data("categoryIsExclusive");
                    var data = {
                        "text" : text,
                        "title": Messages("label.category.option") + '<br>' +
                                (isCategoryExclusive ? Messages("label.category.option.single")
                                                     : Messages("label.category.option.multiple")),
                        "css"  : isCategoryExclusive ? 'yobicon-tag  category-exclusive single'
                                                     : 'yobicon-tags category-exclusive multiple'
                    };
                    var tpl = '<i class="${css}" data-toggle="tooltip" data-html="true" data-placement="right" title="${title}"></i><span>${text}</span>';

                    return $yobi.tmpl(tpl, data);
                }

                return '<a class="label issue-label active static" data-label-id="' + labelId + '">' + text + '</a>';
            },
            "branch": function(itemObject){
                var branchType = "unknown";
                var branchName = itemObject.text.trim();
                var branchNameRegex = /refs\/(.[a-z]+)\/(.+)/i;

                // parse branchName with regular expression branchNameRegex
                // e.g.'refs/heads/feature/review-10'
                // -> ["refs/heads/feature/review-10", "heads", "feature/review-10"]
                var parsedBranchName = branchName.match(branchNameRegex);
                var branchTypeMapByName = {
                    "heads": "branch",
                    "tags" : "tag"
                };

                if(parsedBranchName){
                    branchType = branchTypeMapByName[parsedBranchName[1]] || parsedBranchName[1];
                    branchName = parsedBranchName[2];
                }

                var tplBranchItem = $("#tplSelect2FormatBranch").text()
                                  || '<strong class="branch-label ${branchType}">${branchType}</strong> ${branchName}';

                // branchType will be "unknown"
                // if selected branch name doesn't starts with /refs
                var formattedResult = $yobi.tmpl(tplBranchItem, {
                    "branchType": branchType,
                    "branchName": branchName
                });

                return formattedResult;
            }
        };

        // Custom matchers
        var matchers = {
            "user": function(term, formattedResult, itemElement){
                term = term.toLowerCase();
                formattedResult = formattedResult.toLowerCase();

                var loginId = itemElement.data("loginId") + "";
                loginId = (typeof loginId !== "undefined") ? loginId.toLowerCase() : "";

                return (loginId.indexOf(term) > -1) || (formattedResult.indexOf(term) > -1);
            }
        };

        // Custom behaviors
        var behaviors = {
            "issuelabel": function(select2Element){
                select2Element.on({
                    "select2-selecting": _onSelectingIssueLabel,
                    "select2-open"     : _onOpenIssueLabel
                });

                function _onSelectingIssueLabel(evt){
                    var data = [evt.object];
                    var element = $(evt.object.element);
                    var select2Object = $(evt.target).data("select2");

                    // Remove label which category is same with selected label from current data
                    // if selected label belonged exclusive category
                    if(element.data("categoryIsExclusive")){
                        var filtered = _filterLabelInSameCategory(evt.object, select2Object.data());
                        data = data.concat(filtered);
                    } else {
                        data = data.concat(select2Object.data());
                    }

                    _rememberLastScrollTop();

                    // Set data as filtered
                    // and trigger "change" event
                    select2Object.data(data, true);

                    if(select2Object.opts.closeOnSelect !== false){
                        select2Object.close();
                    }

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

                function _onOpenIssueLabel(){
                    _restoreLastScrollTop();
                }
            }
        };

        // Use customized format if specified format exists
        var formatName = targetElement.data("format");
        var formatter = formatName ? formatters[formatName.toLowerCase()] : null;
        var matcher   = formatName ? matchers[formatName.toLowerCase()]   : null;
        var behavior  = formatName ? behaviors[formatName.toLowerCase()]  : null;

        if(typeof formatter === "function"){
            select2Option = $.extend(select2Option, {
                "formatResult"   : formatter,
                "formatSelection": formatter
            });
        }

        if(typeof matcher === "function"){
            select2Option.matcher = matcher;
        }

        if(typeof behavior === "function"){
            behavior(targetElement);
        }

        $(document).on("mousewheel", ".select2-results", _stopScrollOnBothEnds);

        function _stopScrollOnBothEnds(evt){
            if((evt.originalEvent.deltaY > 0 && _isScrollEndOfList(evt.currentTarget)) ||
               (evt.originalEvent.deltaY < 0 && _isScrollTopOfList(evt.currentTarget))){
                evt.preventDefault();
                evt.stopPropagation();
                return false;
            }
        }

        function _isScrollTopOfList(element){
            return ($(element).scrollTop() === 0);
        }

        function _isScrollEndOfList(element){
            return ($(element).scrollTop() + $(element).height() === element.scrollHeight);
        }

        return targetElement.select2(select2Option);
    };
})("yobi.ui.Select2");

$(function(){
    $('[data-toggle="select2"]').each(function(i, el){
        yobi.ui.Select2(el);
    });
});
