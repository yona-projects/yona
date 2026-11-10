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

// 이 파일은 같은 이름에 함수와 정적 멤버를 등록하는 구조라 본문은 그대로 두고 ES 모듈 태그로 불러온다(전역 $yona가 먼저 준비되어 있다).
/**
 * yona.ui.Select2
 *
 * `data-toggle="select2"` 선택 상자를 select2 4.x로 꾸미고, `data-format` 값(projects, issues, user, milestone,
 * issuelabel, branch)에 따라 항목 모양, 검색 방식, 선택 동작을 더한다.
 *
 * select2 3.4.5에서 4.1.0으로 올리며(decisions.md D-95) 옵션 이름을 4.x에 맞췄다.
 * <ul>
 *   <li>`formatResult`, `formatSelection` → `templateResult`, `templateSelection`</li>
 *   <li>`matcher(term, text, element)` → `matcher(params, data)`</li>
 *   <li>`select2-selecting` → `select2:selecting`</li>
 *   <li>`containerCssClass`는 4.x에 없으므로 `data-container-css-class` 값을 초기화 뒤에 컨테이너에 직접 건다</li>
 * </ul>
 *
 * @requires select2.js 4.1.0 (https://select2.org/)
 */

(function(ns){

    "use strict";

    const oNS = $yona.createNamespace(ns);

    // 이 파일을 거치지 않고 `.select2()`를 직접 부르는 화면(담당자, 공유자, 리뷰어)도 같은 테마를 쓴다
    $.fn.select2.defaults.set("theme", "bootstrap-5");

    /**
     * 원본 `<select>`의 클래스를 select2 컨테이너에 복사하지 않을 목록.
     * 3.x는 원본 클래스를 컨테이너에 그대로 복사했고, 레이아웃 클래스(`float-start`, `mb10` 등)가 컨테이너에 걸리는 것에 화면 CSS가 기대고 있다.
     * 4.x는 복사하지 않으므로 초기화 뒤에 같은 규칙으로 복사한다. `form-select`와 `form-control`은 컨테이너에 화살표 배경이
     * 겹쳐 그려지므로(X-38i, D-56), `hide`는 컨테이너가 보여야 하므로, `select2-hidden-accessible`은 select2가 붙인 것이므로 뺀다.
     */
    const NOT_COPIED_CLASSES = {"form-select": true, "form-control": true, "hide": true, "select2-hidden-accessible": true};

    /**
     * 3.x 방식으로 쓰인 항목 모양 함수를 4.x 템플릿 함수로 바꾼다.
     * 3.x의 formatResult는 문자열이나 jQuery 객체를 돌려주며, 문자열은 HTML로 해석되었다. 4.x는 문자열을 글자로 이스케이프하므로
     * 문자열은 HTML 조각으로 바꿔 돌려주어 같은 모양을 지킨다. 결과를 아직 읽어 오는 줄(`loading`)과 원본 option이 없는 항목(자리 표시 글자)은
     * 3.x가 모양 함수를 거치지 않았으므로 글자 그대로 둔다.
     *
     * @param {Function} formatter 3.x 방식의 항목 모양 함수 (itemObject → 문자열 또는 jQuery)
     * @return {Function} 4.x templateResult, templateSelection 함수
     */
    function _toTemplate(formatter){
        return function(itemObject){
            if(itemObject.loading || !itemObject.element){
                return itemObject.text;
            }
            const result = formatter(itemObject);
            return (typeof result === "string") ? $("<span/>").html(result) : result;
        };
    }

    /**
     * 3.x 방식의 검색 함수 `(검색어, 항목 글자, option 요소)`를 4.x matcher로 바꾼다.
     * 구역(optgroup)은 자식 중 맞는 것만 남겨 돌려준다.
     *
     * @param {Function} test 맞으면 true를 돌려주는 함수
     * @return {Function} 4.x matcher 함수 `(params, data)`
     */
    function _toMatcher(test){
        function match(term, data){
            if(data.children){
                const children = [];
                data.children.forEach((child) =>{
                    const matched = match(term, child);
                    if(matched){
                        children.push(matched);
                    }
                });
                return children.length > 0 ? Object.assign({}, data, {"children": children}) : null;
            }
            return test(term, data.text, $(data.element)) ? data : null;
        }
        return function(params, data){
            const term = (params.term || "").trim();
            return term === "" ? data : match(term, data);
        };
    }


    /**
     * 선택된 option들을 `{id, text, element}` 모양의 자료 객체로 모은다. 키는 option 값이다.
     *
     * @param {jQuery} select 원본 `<select>`
     * @return {Object} option 값 → 자료 객체
     */
    function _selectedData(select){
        const selected = {};
        if(!select[0]){
            return selected;
        }
        Array.from(select[0].options).forEach((option) =>{
            if(option.selected){
                selected[option.value] = {"id": option.value, "text": $(option).text(), "element": option};
            }
        });
        return selected;
    }

    /**
     * 선택 상자의 선택이 마지막으로 확인한 때와 어떻게 달라졌는지 돌려주고, 확인 기준을 지금 선택으로 바꾼다.
     * select2 3.x의 change 이벤트가 알려 주던 `added`와 `removed`에 해당한다(4.x의 change 이벤트에는 없다).
     * 기준은 초기화할 때 처음 잡으므로 change 처리기를 붙인 순서와 무관하다. 한 번의 change 처리에서 한 번만 부른다.
     *
     * @param {Element|jQuery|String} element 원본 `<select>`
     * @return {{added: Array, removed: Array}} 더해진 선택과 빠진 선택(각 항목은 `{id, text, element}`)
     */
    function selectionChanges(element){
        const select = $(element);
        const before = select.data("yonaSelectionBase") || {};
        const current = _selectedData(select);
        const changes = {"added": [], "removed": []};

        Object.entries(current).forEach((entry) =>{
            const id = entry[0], data = entry[1];
            if(!(id in before)){
                changes.added.push(data);
            }
        });
        Object.entries(before).forEach((entry) =>{
            const id = entry[0], data = entry[1];
            if(!(id in current)){
                changes.removed.push(data);
            }
        });

        select.data("yonaSelectionBase", current);
        return changes;
    }


    /**
     * 원격 검색(사용자) 선택 상자의 한 항목에서 사용자 정보를 읽는다. 검색 결과 항목은 응답 객체 필드를 그대로 갖고,
     * 미리 선택된 항목은 option의 `data-*`(`data-name`, `data-login-id`, `data-avatar-url`, `data-type`)에서 읽는다.
     *
     * @param {Object} state select2 항목 객체
     * @return {{name: String, loginId: String, avatarUrl: String, type: String}}
     */
    function userOf(state){
        const optionElement = $(state.element);
        return {
            "name"     : state.name      || optionElement.data("name")      || state.text,
            "loginId"  : state.loginId   || optionElement.data("loginId")   || state.id,
            "avatarUrl": state.avatarUrl || optionElement.data("avatarUrl"),
            "type"     : state.type      || optionElement.data("type")
        };
    }

    /**
     * 사용자 항목 모양 함수를 4.x 템플릿 함수로 바꾼다(원격 검색 선택 상자용).
     * 검색 중 줄(`loading`)과 자리 표시 글자(원본 option도 사용자 정보도 없는 항목)는 글자 그대로 둔다.
     *
     * @param {Function} formatter 사용자 정보 `{name, loginId, avatarUrl, type}` → 문자열(HTML) 또는 jQuery 객체
     * @return {Function} templateResult, templateSelection 함수
     */
    function userTemplate(formatter){
        return function(state){
            if(state.loading || (!state.element && !state.loginId)){
                return state.text;
            }
            const result = formatter(userOf(state));
            return (typeof result === "string") ? $("<span/>").html(result) : result;
        };
    }

    /**
     * 사용자를 원격 검색하는 select2 `ajax` 설정을 만든다. 응답은 사용자 객체 배열(`loginId`, `name`, `avatarUrl`, `type`)이며
     * 항목의 `id`는 `loginId`이다. 요청 인자는 `query`(검색어)이다.
     *
     * @param {String} url 검색 API 주소
     * @param {Boolean} cache 같은 검색어의 응답을 다시 쓸지 여부 (기본 true)
     * @return {Object} select2 ajax 옵션
     */
    function userSearch(url, cache){
        return {
            "url"     : url,
            "dataType": "json",
            "delay"   : 300,
            "cache"   : cache !== false,
            "data"    : function(params){
                return {"query": params.term};
            },
            "processResults": function(users){
                return {"results": users.map((user) =>{
                    return Object.assign({"id": user.loginId, "text": user.name}, user);
                })};
            }
        };
    }

    /**
     * 사용자들을 선택된 option으로 채운다. 이미 같은 값의 option이 있으면 바꾼다.
     * 3.x의 `initSelection`(미리 정해진 값을 서버에서 읽어 와 항목 객체로 채우던 일)에 해당한다.
     *
     * @param {jQuery|Element|String} select 원본 `<select>`
     * @param {Array} users 사용자 객체 배열 (`loginId`, `name`, `avatarUrl`, `type`)
     */
    function selectUsers(select, users){
        const selectElement = $(select);
        users.forEach((user) =>{
            selectElement.find("option").filter(function(){
                return this.value === user.loginId;
            }).remove();

            const option = $(new Option(user.name, user.loginId, true, true));
            option.attr({"data-name": user.name, "data-login-id": user.loginId, "data-avatar-url": user.avatarUrl, "data-type": user.type});
            selectElement.append(option);
        });
        selectElement.trigger("change.select2");
    }

    oNS.container[oNS.name] = function(element, options){
        const targetElement = $(element);

        let select2Option = Object.assign({
            "width"            : "resolve",
            "allowClear"       : targetElement.data("allowClear"),
            "closeOnSelect"    : targetElement.data("closeOnSelect"),
            "dropdownCssClass" : targetElement.data("dropdownCssClass")
        }, options);

        // Customized formats
        const formatters = {
            "projects": function(itemObject){
                const itemElement = $(itemObject.element);
                const avatarURL = itemElement.data("avatarUrl");

                function _doesntHaveProjectAvatar() {
                    return !avatarURL || avatarURL.indexOf("project_default_logo.png") !== -1;
                }

                const owner = itemElement.data("owner") ? itemElement.data("owner") : "";
                if(_doesntHaveProjectAvatar()){
                    return $yona.render($("#tplSelect2ProjectsWithoutAvatar").text(), {
                        "name"     : itemObject.text,
                        "owner"    : owner
                    });
                } else {
                    return $yona.render($("#tplSelect2Projects").text(), {
                        "avatarURL": avatarURL,
                        "name"     : itemObject.text.trim(),
                        "owner"    : owner
                    });
                }
            },
            "issues": function(itemObject){
                return $yona.render($("#tplSelect2FormatIssues").text(), {
                    "name"     : itemObject.text
                });
            },
            "user": function(itemObject){
                const itemElement = $(itemObject.element);
                const avatarURL = itemElement.data("avatarUrl");

                if(!avatarURL){
                    return '<div>' + itemObject.text + '</div>';
                }

                // Template text
                const tplUserItem = $("#tplSelect2FormatUser").text() || '<div class="usf-group" title="{{name}} {{loginId}}">' +
                    '<span class="avatar-wrap smaller"><img src="{{avatarURL}}" width="20" height="20"></span>' +
                    '<strong class="name">{{name}}</strong>' +
                    '<span class="loginid">{{loginId}}</span></div>';

                const loginId = itemElement.data("loginId") ? "@" + itemElement.data("loginId") : "";

                const formattedResult = $yona.render(tplUserItem, {
                    "avatarURL": avatarURL,
                    "name"     : itemObject.text.trim(),
                    "loginId"  : loginId
                });

                return formattedResult;
            },
            "milestone": function(itemObject){
                const itemElement = $(itemObject.element);
                let milestoneState = itemElement.data("state");

                if(!milestoneState){
                    return itemObject.text;
                }

                milestoneState = milestoneState.toLowerCase();
                const milestoneStateLabel = Messages("milestone.state." + milestoneState);
                const tplMilestoneItem = $("#tplSElect2FormatMilestone").text()
                                    || '<div title="[{{stateLabel}}] {{name}}">{{name}}</div>';

                const formattedResult = $yona.render(tplMilestoneItem, {
                    "name" : itemObject.text.trim().replace('<', '&lt;'),
                    "state": milestoneState,
                    "stateLabel": milestoneStateLabel
                });

                return formattedResult;
            },
            "issuelabel": function(itemObject){
                const element = $(itemObject.element);
                const labelId = element.val();
                const text = $("<div/>").text(itemObject.text).html().trim();

                if(!labelId){ // = optgroup
                    const isCategoryExclusive = element.data("categoryIsExclusive");
                    const data = {
                        "text" : text,
                        "title": Messages("label.category.option") + '<br>' +
                                (isCategoryExclusive ? Messages("label.category.option.single")
                                                     : Messages("label.category.option.multiple")),
                        "css"  : isCategoryExclusive ? 'yobicon-tag  category-exclusive single'
                                                     : 'yobicon-tags category-exclusive multiple'
                    };
                    const tpl = '<i class="{{css}}" data-bs-toggle="tooltip" data-bs-html="true" data-bs-placement="right" title="{{title}}"></i><span>{{text}}</span>';

                    return $yona.render(tpl, data);
                }

                return '<a class="badge issue-label active static" data-label-id="' + labelId + '">' + text + '</a>';
            },
            "branch": function(itemObject){
                // 구역 제목(optgroup, 브랜치/태그)은 종류 배지 없이 글자만 보인다 (decisions.md D-30)
                if(itemObject.children){
                    return $('<div/>').text(itemObject.text.trim()).html();
                }

                let branchType = "unknown";
                let branchName = itemObject.text.trim();
                const branchNameRegex = /refs\/(.[a-z]+)\/(.+)/i;

                // parse branchName with regular expression branchNameRegex
                // e.g.'refs/heads/feature/review-10'
                // -> ["refs/heads/feature/review-10", "heads", "feature/review-10"]
                const parsedBranchName = branchName.match(branchNameRegex);
                const branchTypeMapByName = {
                    "heads": "branch",
                    "tags" : "tag"
                };

                if(parsedBranchName){
                    branchType = branchTypeMapByName[parsedBranchName[1]] || parsedBranchName[1];
                    branchName = parsedBranchName[2];
                }

                const tplBranchItem = $("#tplSelect2FormatBranch").text()
                                  || '<strong class="branch-label {{branchType}}">{{branchType}}</strong> {{branchName}}';

                // branchType will be "unknown"
                // if selected branch name doesn't starts with /refs
                const formattedResult = $yona.render(tplBranchItem, {
                    "branchType": branchType,
                    "branchName": branchName
                });

                return formattedResult;
            }
        };

        // Custom matchers
        const matchers = {
            "user": function(term, formattedResult, itemElement){
                term = term.toLowerCase();
                formattedResult = formattedResult.toLowerCase();
                let loginId = itemElement.data("loginId") + "";
                loginId = (typeof loginId !== "undefined") ? loginId.toLowerCase() : "";

                return (loginId.indexOf(term) > -1) || (formattedResult.indexOf(term) > -1);
            },
            "projects": function(term, formattedResult, itemElement){
              term = term.toLowerCase();
              formattedResult = formattedResult.toLowerCase();
              let owner = itemElement.data("owner") + "";
              owner = (typeof owner !== "undefined") ? owner.toLowerCase() : "";

              return (owner.indexOf(term) > -1) || (formattedResult.indexOf(term) > -1);
            }
        };

        // Custom behaviors
        const behaviors = {
            "issuelabel": function(select2Element){
                // 배타 분류의 라벨을 고르면 같은 분류에서 이미 고른 라벨을 먼저 해제한다.
                // 해제는 select2의 선택 처리 전에 option의 selected만 바꾸고, 뒤이은 선택 처리가 change를 한 번 내보내며 화면을 갱신한다.
                select2Element.on("select2:selecting", (evt) =>{
                    const selected = $(evt.params.args.data.element);

                    if(!selected.data("categoryIsExclusive")){
                        return;
                    }

                    // 선택된 option이 하나도 없는 순간이 생기면 브라우저가 맨 앞의 빈 option을 선택해 폼에 빈 값이 실린다.
                    // 그래서 새 라벨을 먼저 선택 상태로 만든 뒤 같은 분류의 앞선 선택을 해제한다(select2가 이어서 change를 한 번 내보낸다)
                    const categoryId = selected.data("categoryId");
                    selected[0].selected = true;
                    Array.from(select2Element[0].options).forEach((option) =>{
                        if(option.selected && option !== selected[0] && $(option).data("categoryId") === categoryId){
                            option.selected = false;
                        }
                    });
                });
            }
        };

        // Use customized format if specified format exists
        const formatName = targetElement.data("format");
        const formatter = formatName ? formatters[formatName.toLowerCase()] : null;
        const matcher   = formatName ? matchers[formatName.toLowerCase()]   : null;
        const behavior  = formatName ? behaviors[formatName.toLowerCase()]  : null;

        if(typeof formatter === "function"){
            const template = _toTemplate(formatter);
            select2Option = Object.assign(select2Option, {
                "templateResult"   : template,
                "templateSelection": template
            });
        }

        if(typeof matcher === "function"){
            select2Option.matcher = _toMatcher(matcher);
        }

        if(typeof behavior === "function"){
            behavior(targetElement);
        }

        // 목록 맨 위나 맨 아래에서 휠을 더 돌려도 페이지가 따라 스크롤되지 않게 한다
        $(document).on("mousewheel", ".select2-results__options", _stopScrollOnBothEnds);

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

        targetElement.select2(select2Option);
        targetElement.data("yonaSelectionBase", _selectedData(targetElement));

        // 3.x처럼 `data-container-css-class`와 원본의 레이아웃 클래스를 컨테이너에 건다
        const container = targetElement.next(".select2-container");
        container.addClass(targetElement.data("containerCssClass") || "");
        (targetElement.attr("class") || "").split(/\s+/).forEach((className) =>{
            if(className && !NOT_COPIED_CLASSES[className]){
                container.addClass(className);
            }
        });

        return targetElement;
    };

    oNS.container[oNS.name].selectionChanges = selectionChanges;
    oNS.container[oNS.name].userOf = userOf;
    oNS.container[oNS.name].userTemplate = userTemplate;
    oNS.container[oNS.name].userSearch = userSearch;
    oNS.container[oNS.name].selectUsers = selectUsers;
})("yona.ui.Select2");

$(() =>{
    $('[data-toggle="select2"]').each((i, el) =>{
        yona.ui.Select2(el);
    });
});
