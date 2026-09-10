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
 * P3-46 #5: Select2(v3) -> Tom Select 교체.
 *
 * 파일명/네임스페이스(yobi.ui.Select2)는 P3-46 앞선 항목들(yobi.ui.Calendar.js 등)과 동일한 이유로
 * 그대로 유지한다 - 17개 템플릿이 이 파일 경로를 직접 참조하므로 이름을 바꾸면 위험만 커지고 얻는
 * 것이 없다.
 *
 * Tom Select는 대상 <select>/<input>의 data-* 속성을 자동으로 읽어 옵션 데이터 객체에 그대로
 * 얹어준다(dataset 기반 - 예: data-avatar-url -> avatarUrl, data-category-id -> categoryId,
 * data-category-is-exclusive -> categoryIsExclusive). jQuery의 .data()와 달리 dataset은 값을
 * "true"/"false" 같은 문자열 그대로 돌려주고 boolean으로 변환해주지 않는다 - 아래 곳곳에서
 * categoryIsExclusive를 항상 === "true" 로 비교하는 이유다(문자열 "false"도 JS에서는 truthy라
 * 그냥 if(...)로 쓰면 조용히 깨진다).
 *
 * @requires tom-select.complete.min.js (https://tom-select.js.org/)
 */

(function(ns){

    "use strict";

    var oNS = $yobi.createNamespace(ns);

    // ===== 로케일 문자열 (select2_locale_ko.js/ja.js 대응) =====
    // common/select2.html이 로케일에 따라 window.YOBI_SELECT2_I18N을 채워둔다. 없으면(영어 등)
    // select2 v3 기본 영어 문구(select2.js:3203-3208)와 동일한 값으로 폴백한다.
    var I18N = window.YOBI_SELECT2_I18N || {
        noResults: "No matches found",
        searching: "Searching...",
        tooShort: function(n){ return "Please enter " + n + " more character" + (n === 1 ? "" : "s"); }
    };

    // ===== 공통: 기본 매처(select2 v3 기본 matcher와 동일한 대소문자 무시 부분일치) =====
    // Tom Select 기본 score는 Sifter 기반 퍼지 매칭이라 select2 v3의 단순 부분일치와 결과가 달라질
    // 수 있다 - data-format이 없는 필드(및 커스텀 matcher가 없는 milestone/issues/issuelabel/branch)
    // 도 포함해 모든 인스턴스에 이 단순 부분일치를 기본으로 적용해 기존 동작과 맞춘다.
    function _defaultScore(search){
        var term = search.toLowerCase();
        return function(item){
            var text = (item.text || "").toString().toLowerCase();
            return text.indexOf(term) > -1 ? 1 : 0;
        };
    }

    // "user" 포맷 전용 matcher(로그인 id도 검색 대상에 포함) 대응
    function _userScore(search){
        var term = search.toLowerCase();
        return function(item){
            var text = (item.text || "").toString().toLowerCase();
            var loginId = (item.loginId || "").toString().toLowerCase();
            return (loginId.indexOf(term) > -1 || text.indexOf(term) > -1) ? 1 : 0;
        };
    }

    // "projects" 포맷 전용 matcher(owner도 검색 대상에 포함) 대응
    function _projectsScore(search){
        var term = search.toLowerCase();
        return function(item){
            var text = (item.text || "").toString().toLowerCase();
            var owner = (item.owner || "").toString().toLowerCase();
            return (owner.indexOf(term) > -1 || text.indexOf(term) > -1) ? 1 : 0;
        };
    }

    // ===== 포맷별 렌더러(formatResult/formatSelection 대응, option/item 공용) =====
    var renderers = {
        "projects": function(data, escape){
            var avatarURL = data.avatarUrl;
            var owner = data.owner || "";
            var name = (data.text || "").trim();

            function _doesntHaveProjectAvatar(){
                return !avatarURL || avatarURL.indexOf("project_default_logo.png") !== -1;
            }

            if(_doesntHaveProjectAvatar()){
                return '<div class="usf-group" title="' + escape(name) + '">' +
                    '<span class="width25px"></span>' +
                    '<span class="loginid">' + escape(owner) + '</span>' +
                    '<span class="name">' + escape(name) + '</span>' +
                    '</div>';
            }

            return '<div class="usf-group" title="' + escape(name) + '">' +
                '<span class="avatar-wrap smaller"><img src="' + escape(avatarURL) + '" width="16" height="16"></span>' +
                '<span class="loginid">' + escape(owner) + '</span>' +
                '<span class="name">' + escape(name) + '</span>' +
                '</div>';
        },
        "issues": function(data, escape){
            return '<div title="' + escape(data.text) + '">' + escape(data.text) + '</div>';
        },
        "user": function(data, escape){
            var avatarURL = data.avatarUrl;
            var name = (data.text || "").trim();

            if(!avatarURL){
                return "<div>" + escape(data.text) + "</div>";
            }

            var loginId = data.loginId ? "@" + data.loginId : "";

            return '<div class="usf-group" title="' + escape(name) + ' ' + escape(loginId) + '">' +
                '<span class="avatar-wrap smaller"><img src="' + escape(avatarURL) + '" width="20" height="20"></span>' +
                '<strong class="name">' + escape(name) + '</strong>' +
                '<span class="loginid">' + escape(loginId) + '</span>' +
                '</div>';
        },
        "milestone": function(data){
            var milestoneState = data.state;

            if(!milestoneState){
                return data.text;
            }

            milestoneState = milestoneState.toLowerCase();
            var milestoneStateLabel = Messages("milestone.state." + milestoneState);
            // 원본의 "${name}".replace('<', '&lt;')를 그대로 재현한다 - 정규식이 아니라 String#replace라
            // 문자열의 '첫 번째' '<' 문자만 치환하는 불완전한 이스케이프다(의도적으로 고치지 않음,
            // 최종 보고 "범위 밖 발견" 참고).
            var name = data.text.trim().replace('<', '&lt;');

            return '<div title="[' + milestoneStateLabel + '] ' + name + '">' + name + '</div>';
        },
        "issuelabel": function(data, escape){
            var labelId = data.value;
            var text = escape((data.text || "").trim());

            return '<div><a class="label issue-label active static" data-label-id="' + escape(labelId) + '">' + text + '</a></div>';
        },
        "branch": function(data){
            // 어떤 템플릿도 현재 data-format="branch"를 쓰지 않아(grep으로 재확인) 이 포맷은
            // 실행 경로상 도달 불가능한 죽은 코드다 - 원본 동작을 그대로 이식만 해둔다(최종 보고
            // "범위 밖 발견" 참고).
            var branchType = "unknown";
            var branchName = data.text.trim();
            var branchNameRegex = /refs\/(.[a-z]+)\/(.+)/i;
            var parsedBranchName = branchName.match(branchNameRegex);
            var branchTypeMapByName = { "heads": "branch", "tags": "tag" };

            if(parsedBranchName){
                branchType = branchTypeMapByName[parsedBranchName[1]] || parsedBranchName[1];
                branchName = parsedBranchName[2];
            }

            return '<div><strong class="branch-label ' + branchType + '">' + branchType + '</strong> ' + branchName + '</div>';
        }
    };

    // ===== issuelabel 전용: 카테고리(optgroup) 헤더 렌더러 =====
    // select2 v3에서는 optgroup 헤더도 formatResult로 들어왔지만(itemObject.element가 optgroup DOM일
    // 때 label.val()이 undefined인 것으로 구분), Tom Select는 optgroup 헤더 렌더링이
    // render.optgroup_header로 완전히 분리되어 있어 그쪽이 더 명확하다.
    function _issueLabelOptgroupHeader(data, escape){
        var isCategoryExclusive = data.categoryIsExclusive === "true";
        var title = Messages("label.category.option") + '<br>' +
            (isCategoryExclusive ? Messages("label.category.option.single") : Messages("label.category.option.multiple"));
        var css = isCategoryExclusive ? 'yobicon-tag  category-exclusive single' : 'yobicon-tags category-exclusive multiple';

        return '<div class="optgroup-header">' +
            '<i class="' + css + '" data-toggle="tooltip" data-html="true" data-placement="right" title="' + title + '"></i>' +
            '<span>' + escape(data.label) + '</span>' +
            '</div>';
    }

    // ===== 공통 매처 테이블 =====
    var scoreFns = {
        "user": _userScore,
        "projects": _projectsScore
    };

    // ===== select2-selecting/change 이벤트 브릿지 =====
    // Tom Select는 값이 바뀌어도 원본 <select>/<input> DOM에 native "change" 이벤트를 쏘지 않는다
    // (내부 MicroEvent 시스템으로만 'change'를 trigger한다) - 그런데 이 프로젝트의 여러 곳
    // (yobi.project.New.js의 #vcs 핸들러, yobi.issue.View.js의 이슈 인라인 수정 등)이 여전히
    // jQuery로 원본 요소의 "change"를 델리게이트로 구독하며 select2 시절처럼 evt.val을 읽는다.
    // 이 브릿지가 없으면 그 기능들이 조용히 멈춘다 - data-toggle="select2"로 자동 초기화되는
    // 인스턴스뿐 아니라(아래 자동 초기화 루프), 자동 초기화를 거치지 않고 별도 모듈에서 직접
    // TomSelect를 생성하는 #assignee(yona.issue.Assginee.js)/#issueSharer(yona.issue.Sharer.js)도
    // 반드시 이 브릿지를 걸어야 한다 - 그래서 재사용 가능하도록 외부에 노출해둔다.
    function bridgeChangeEvent(tomSelectInstance, targetElement){
        tomSelectInstance.on("change", function(value){
            var evt = $.Event("change");
            evt.val = value;
            $(targetElement).trigger(evt);
        });
    }

    // ===== 드롭다운 스크롤이 리스트 양 끝에서 페이지 전체 스크롤로 새는 것을 막는다 =====
    // (select2 v3 시절 _stopScrollOnBothEnds 대응, 셀렉터만 Tom Select의 실제 드롭다운 스크롤
    // 컨테이너(.ts-dropdown-content)로 교체)
    var mousewheelGuardInstalled = false;
    function _installMousewheelGuardOnce(){
        if(mousewheelGuardInstalled){
            return;
        }
        mousewheelGuardInstalled = true;

        $(document).on("mousewheel", ".ts-dropdown-content", function(evt){
            var element = evt.currentTarget;
            var atBottom = ($(element).scrollTop() + $(element).height() >= element.scrollHeight);
            var atTop = ($(element).scrollTop() === 0);

            if((evt.originalEvent.deltaY > 0 && atBottom) || (evt.originalEvent.deltaY < 0 && atTop)){
                evt.preventDefault();
                evt.stopPropagation();
                return false;
            }
        });
    }

    oNS.container[oNS.name] = function(element, options){
        _installMousewheelGuardOnce();

        var targetElement = $(element);
        var formatName = (targetElement.data("format") || "").toString().toLowerCase();
        var renderer = renderers[formatName];
        var scoreFn = scoreFns[formatName] || _defaultScore;

        var dropdownCssClass = targetElement.data("dropdownCssClass");
        var containerCssClass = targetElement.data("containerCssClass");

        var tsOptions = $.extend({
            // 로컬 데이터 기반 인스턴스는 select2처럼 매치되는 항목을 전부 보여줘야 한다 - Tom Select
            // 기본값(maxOptions:50)은 프로젝트/멤버/마일스톤/라벨이 50개를 넘는 순간 나머지를 조용히
            // 잘라버리므로 반드시 큰 값으로 올려둔다.
            maxOptions: 10000,
            // select2 v3는 검색어에 매치된 부분 문자열을 하이라이트하는 기능이 없었다(CSS의
            // .select2-highlighted는 검색 매치가 아니라 키보드/마우스로 "현재 활성화된 행"을
            // 표시하는 것 - Tom Select의 render 시스템이 자동으로 처리한다). Tom Select 기본값
            // (highlight:true)은 매치된 substring을 <span class="highlight">로 감싸는 새 기능이라
            // 끈다("새 라이브러리 부가 기능은 켜지 않는다" 원칙).
            highlight: false,
            score: scoreFn,
            wrapperClass: "ts-wrapper" + (containerCssClass ? " " + containerCssClass : ""),
            render: {
                // select2 v3는 no-results/searching/selection-limit 모두 같은 스타일(회색 배경 텍스트
                // 행)이었을 뿐 스피너 애니메이션 같은 건 없었다 - Tom Select 기본 "spinner" 템플릿
                // (회전 링 그래픽)을 쓰면 원래 없던 시각 요소가 새로 생기므로 쓰지 않는다.
                no_results: function(){ return '<div class="no-results">' + I18N.noResults + '</div>'; },
                loading: function(){ return '<div class="no-results">' + I18N.searching + '</div>'; }
            }
        }, options);

        if(renderer){
            tsOptions.render.option = renderer;
            tsOptions.render.item = renderer;
        }

        if(formatName === "issuelabel"){
            tsOptions.render.optgroup_header = _issueLabelOptgroupHeader;
            tsOptions.plugins = (tsOptions.plugins || []).concat(["remove_button"]);
            tsOptions.onItemAdd = function(value){
                var self = this;
                var addedOption = self.options[value];
                if(!addedOption){
                    return;
                }

                // select2 시절 _rememberLastScrollTop/_restoreLastScrollTop 대응: 배타 카테고리
                // 정리로 옵션 목록이 다시 그려지면서 스크롤 위치가 top으로 리셋되는 것을 막는다.
                var dropdownContent = self.dropdown_content;
                var savedScrollTop = dropdownContent ? dropdownContent.scrollTop : 0;

                if(addedOption.categoryIsExclusive === "true"){
                    var categoryId = addedOption.categoryId;
                    self.items.slice().forEach(function(otherValue){
                        if(otherValue === value){
                            return;
                        }
                        var other = self.options[otherValue];
                        if(other && other.categoryId === categoryId){
                            self.removeItem(otherValue, true);
                        }
                    });
                }

                if(dropdownContent){
                    setTimeout(function(){
                        dropdownContent.scrollTop = savedScrollTop;
                    }, 0);
                }
            };
        }

        // select2-without-searchbox: select2 v3에서는 드롭다운 안 검색창을 CSS로 숨겼을 뿐이지만
        // display:none이라 포커스/탭도 안 됐다 - 실질적으로 "검색 불가, 클릭으로만 선택"과 동일했다.
        // Tom Select는 검색창이 컨트롤 자체와 합쳐져 있어 "숨기기만" 하는 대응이 없다 -
        // controlInput:null(입력 자체를 없앰)이 원본의 실제 동작(타이핑 불가)과 결과적으로 동일하다.
        if(dropdownCssClass === "select2-without-searchbox"){
            tsOptions.controlInput = null;
        } else if(dropdownCssClass){
            tsOptions.dropdownClass = "ts-dropdown " + dropdownCssClass;
        }

        var instance = new TomSelect(element, tsOptions);
        bridgeChangeEvent(instance, element);

        return instance;
    };

    // 다른 모듈(yona.issue.Sharer.js/yona.issue.Assginee.js)이 재사용할 수 있도록 노출한다 -
    // 그 둘은 data-toggle="select2" 자동 초기화 루프를 타지 않고 직접 TomSelect를 생성하므로
    // change 이벤트 브릿지를 스스로 걸어야 한다.
    oNS.container[oNS.name].bridgeChangeEvent = bridgeChangeEvent;
    oNS.container[oNS.name].i18n = I18N;

    $(function(){
        $('[data-toggle="select2"]').each(function(i, el){
            yobi.ui.Select2(el);
        });
    });
})("yobi.ui.Select2");
