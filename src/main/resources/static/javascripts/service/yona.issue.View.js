/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 *  https://yona.io
 **/
(function(ns){

    "use strict";

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(options){

        var vars = {};
        var elements = {};

        /**
         * jQuery `.on(evt, selector, fn)` 위임 바인딩과 동일하게 재현한다:
         * addEventListener + closest(selector) + container.contains(...) 가드 +
         * fn.call(matchedEl, e) (P3-70 라운드2에서 확립한 관례).
         *
         * @private
         */
        function _delegate(container, sEventType, sSelector, fHandler){
            if(!container){
                return;
            }
            container.addEventListener(sEventType, function(weEvt){
                var matched = weEvt.target.closest(sSelector);
                if(matched && container.contains(matched)){
                    fHandler.call(matched, weEvt);
                }
            });
        }

        /**
         * issue/view.html 인라인 스크립트가 `$(document).on('submit', '#comment-form', ...)`로
         * 댓글 폼 제출을 AJAX로 가로챈다(P3-48). jQuery의 인자 없는 `.submit()`은
         * `.trigger("submit")`과 같아서 이 델리게이트를 실제로 호출하고, 아무도
         * preventDefault를 안 부르면 네이티브 elem.submit()으로 폴백한다 - 네이티브
         * form.submit()을 직접 부르면 이 AJAX 가로채기를 건너뛰게 되므로 동일한
         * "이벤트 발생 우선" 흐름을 재현한다.
         *
         * @private
         */
        function _triggerFormSubmit(elForm){
            if(!elForm){
                return;
            }
            var weEvt = new Event("submit", {"bubbles": true, "cancelable": true});
            var bNotPrevented = elForm.dispatchEvent(weEvt);
            if(bNotPrevented){
                elForm.submit();
            }
        }

        /**
         * jQuery `$(el).offset().top`(문서 기준 절대 좌표)와 동일한 값을 구한다.
         *
         * @private
         */
        function _getOffsetTop(el){
            return el.getBoundingClientRect().top + (window.pageYOffset || document.documentElement.scrollTop);
        }

        /**
         * jQuery `:visible` 셀렉터(레이아웃 유무로 판단 - 조상 요소가 숨겨진 경우까지
         * 포함)와 동일하게 재현한다.
         *
         * @private
         */
        function _isJQueryVisible(el){
            return !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
        }

        /**
         * `data-watching` 속성은 서버 렌더링 시점 값 그대로 유지되고(이 파일은 board.View.js와
         * 달리 `.attr()`이 아니라 `.data()`로 읽고 쓴다), jQuery `.data(key, value)` setter는
         * DOM 속성을 건드리지 않고 내부 캐시만 갱신한다 - 최초 1회는 속성값을 읽어 boolean으로
         * 변환(coercion)하고, 그 다음부터는 순수 JS 캐시로만 토글되는 quirk를 재현한다.
         *
         * @private
         */
        function _getWatchingState(el){
            return Object.prototype.hasOwnProperty.call(el, "__watchingCache")
                ? el.__watchingCache
                : (el.getAttribute("data-watching") === "true");
        }

        function _setWatchingState(el, value){
            el.__watchingCache = value;
        }

        /**
         * Initialize
         * @param {Hash Table} options
         */
        function _init(options){
            _initElement(options || {});
            _initVar(options || {});
            _attachEvent();

            _initFileUploader();
            _initFileDownloader();
            _initCommentAndCloseButton();

            //_setTimelineUpdateTimer();
            _affixIssueInfoWrap();
        }

        /**
         * Initialize HTML Element variables
         *
         * @private
         */
        function _initElement(options){
            elements.uploader = document.getElementById("upload");
            elements.textarea = document.querySelector('textarea[data-editor-mode="comment-body"]');

            elements.btnWatch = document.getElementById('watch-button');
            elements.issueInfoWrap = document.querySelector(".issue-info");
            elements.dueDateStatus = elements.issueInfoWrap ? elements.issueInfoWrap.querySelector(".duedate-status") : null;

            elements.timelineWrap = document.getElementById("timeline");
            elements.timelineList = elements.timelineWrap ? elements.timelineWrap.querySelector(".timeline-list") : null;

            elements.btnVoteComment = document.querySelectorAll(options.btnVoteComment || '[data-request-type="comment-vote"]');
        }

        /**
         * Initialize variables
         *
         * @param options
         * @private
         */
        function _initVar(options){
            vars.issueId = options.issueId;
            vars.urls = options.urls;
            vars.nextState = options.nextState;
            var tplFileItemEl = document.getElementById("tplAttachedFile");
            // jQuery `$('#tplAttachedFile').text()`는 매치가 없어도 빈 문자열을 반환한다.
            vars.tplFileItem = tplFileItemEl ? tplFileItemEl.textContent : "";

            // for auto-update
            vars.isTimelineUpdating = false;
            vars.isTextareaOnFocused = false;
            vars.timelineUpdateTimer = null;
            vars.timelineUpdatePeriod = options.timelineUpdatePeriod || 60000; // 60000ms = 60s = 1m
            vars.timelineHTML = elements.timelineList ? elements.timelineList.innerHTML : "";
            vars.timelineItems = _countTimelineItems();

            // for comment-and-close
            vars.nextState = options.nextState;
        }

        /**
         * Attach event handler
         */
        function _attachEvent(){
            // Watch button
            if(elements.btnWatch){
                elements.btnWatch.addEventListener("click", _onClickBtnWatch);
            }

            // Vote button on comment
            _delegate(elements.timelineWrap, "click", '[data-request-type="comment-vote"]', _onClickCommentVote);

            // Update issue info
            // data-toggle 속성값이 select2->tomselect로 바뀌었으므로(사용자 결정 2026-09-12)
            // 델리게이트 셀렉터도 함께 갱신 - 안 그러면 yona.ui.TomSelect.js의 bridgeChangeEvent가
            // 쏘는 네이티브 change 이벤트를 이 델리게이트가 못 잡아 이슈 인라인 수정(담당자/
            // 마일스톤/라벨)이 조용히 멈춘다.
            _delegate(elements.issueInfoWrap, "change", "[data-toggle=tomselect]", _onChangeIssueInfo);
            _delegate(elements.issueInfoWrap, "change", "[data-toggle=calendar]", _onChangeDueDate);
            // "select2-selecting"은 Select2 v3 전용 커스텀 이벤트라 Tom Select가 절대 발생시키지
            // 않는다(P3-70 라운드1에서 issue.Write.js의 동일 분기 발견·문서화) - 이 바인딩과 아래
            // _onSelectingAssignee는 도달 불가능한 죽은 코드다. 삭제하지 않고 문법만 그대로
            // vanilla로 옮긴다.
            _delegate(elements.issueInfoWrap, "select2-selecting", '[name="assignee.user.id"]', _onSelectingAssignee);

            // Detect textarea events for autoUpdate timeline
            if(elements.textarea){
                elements.textarea.addEventListener("focus", _onFocusCommentTextarea);
                elements.textarea.addEventListener("blur", _onBlurCommentTextarea);
            }
        }

        function _onClickCommentVote(){
            fetch(this.dataset.requestUri, {"method": "post"})
                .then(function(response){
                    if(!response.ok){
                        return response.text().then(function(text){
                            return Promise.reject(text);
                        });
                    }
                    location.reload();
                })
                .catch(function(responseText){
                    $yona.notify(Messages(responseText), 3000);
                });
        }

        /**
         * jQuery.param()(traditional:false, 기본값)과 동일한 방식으로 요청 데이터를
         * URLSearchParams로 직렬화한다 - 배열 값은 key[]=v1&key[]=v2 형태로 반복
         * append한다(_getUpdateIssueRequestData의 attachingLabelIds/detachingLabelIds
         * 대응, 서버가 기대하는 파라미터 이름 규칙을 그대로 유지하기 위함).
         */
        function _toJQueryStyleParams(data){
            var params = new URLSearchParams();
            Object.keys(data).forEach(function(key){
                var value = data[key];
                // jQuery.param()은 값이 undefined/null이어도 키 자체는 유지하고 빈 문자열로
                // 직렬화한다(예: attachingLabelIds가 없으면 "attachingLabelIds=") - 키를 통째로
                // 생략하면 서버 바인딩이 달라질 수 있어 정확히 재현한다.
                if(Array.isArray(value)){
                    value.forEach(function(item){
                        params.append(key + "[]", item == null ? "" : item);
                    });
                } else {
                    params.append(key, value == null ? "" : value);
                }
            });
            return params;
        }

        /**
         * 도달 불가능한 죽은 핸들러(위 _attachEvent 주석 참고) - Tom Select가
         * "select2-selecting"을 발생시키지 않아 실제로는 절대 호출되지 않는다. 문법만
         * vanilla로 옮기고 로직은 그대로 보존한다.
         *
         * @private
         */
        function _onSelectingAssignee(evt){
            var targetElement = this;
            var selectedElement = targetElement.querySelector("option:checked");
            var isValueNotChanged = (targetElement.value === evt.val);
            // P3-70 라운드1에서 issue.Write.js와 동일하게 확인: forceChange/nonMember는 어느
            // 템플릿/JS도 설정한 적 없는 순수 죽은 참조라 항상 undefined다 - window.jQuery.data()
            // 정적 접근자로 jQuery 내부 데이터 캐시를 그대로 읽는 관례를 유지한다.
            var isForceChange = window.jQuery.data(evt.object.element, "forceChange");
            var isNonMember = selectedElement ? window.jQuery.data(selectedElement, "nonMember") : undefined;

            if (isNonMember && !isValueNotChanged) {
                if(selectedElement){
                    selectedElement.remove();
                }
            }

            if(isForceChange && isValueNotChanged){
                targetElement.dispatchEvent(new Event("change", {"bubbles": true}));
            }
        }

        /**
         * on change dueDate input field
         *
         * @param evt
         * @private
         */
        function _onChangeDueDate(evt){
            var element = this;
            var dueDate = element.value.trim();

            // if dueDate is not empty and invalid
            if(dueDate && !moment(dueDate).isValid()){
                $yona.notify(Messages("issue.error.invalid.duedate"), 3000);
                element.focus();
                return;
            }

            // "oval"(old value) 캐시는 이 두 줄 밖 어디에서도 읽히지 않는 자기참조 값이라
            // (grep으로 확인) 사실상 관찰 가능한 효과가 없다 - 원본 그대로 순수 JS 캐시
            // (expando)로 보존한다.
            if(element.__oval !== element.value){
                element.__oval = element.value;
            }

            _requestUpdateIssue(evt, function(res){
                if(elements.dueDateStatus){
                    elements.dueDateStatus.innerHTML = "(" + res.dueDateMsg + ")";
                    if (res.isOverDue) {
                        elements.dueDateStatus.classList.add("overdue");
                    } else {
                        elements.dueDateStatus.classList.remove("overdue");
                    }
                }
            });
        }

        /**
         * "change" event handler of issue info select2 fields.
         *
         * @param evt
         * @private
         */
        function _onChangeIssueInfo(evt){
            _requestUpdateIssue(evt);
        }

        /**
         * Send request to update issue info
         * like as assignee.id, milestone.id and labelIds.
         *
         * @param evt
         * @param callback
         * @private
         */
        function _requestUpdateIssue(evt, callback){
            // P3-46 #5: Select2(v3) -> Tom Select 교체. field.data("select2")로 인스턴스를 얻던
            // 방식을 field.tomselect로 교체한다(현재 이 경로로 실제 도달하는 필드는 #milestone
            // 하나뿐 - 최종 보고 "범위 밖 발견" 참고).
            var field = evt.target;
            var fieldName = field.dataset.fieldName || field.name;
            var fieldTomSelect = field.tomselect;
            var fieldValue = fieldTomSelect ? fieldTomSelect.getValue() : field.value;

            // Send request to update issueInfo
            fetch(vars.urls.massUpdate, {
                "method": "post",
                "body": _toJQueryStyleParams(_getUpdateIssueRequestData(fieldName, fieldValue, evt))
            })
            .then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                return response.json();
            })
            .then(function(res){
                _updateTimeline();

                $yona.notify(Messages("issue.update." + fieldName), 3000);

                if(fieldTomSelect){
                    fieldTomSelect.setValue(fieldValue, true);
                }

                if(typeof callback === "function"){
                    callback(res, fieldName, fieldValue, evt);
                }
            })
            .catch(function(res){
                $yona.notify(Messages("error.failedTo",
                    Messages("issue.update." + fieldName),
                    res.status, res.statusText));
            });
        }

        /**
         * Returns request data to update issue info.
         *
         * @param fieldName
         * @param fieldValue
         * @param evt
         * @returns {Hash Table}
         * @private
         */
        function _getUpdateIssueRequestData(fieldName, fieldValue, evt){
            var requestData = {"issues[0].id": vars.issueId};

            if(fieldName === "labelIds"){
                requestData["attachingLabelIds"] = _getIdProps(evt.added);
                requestData["detachingLabelIds"] = _getIdProps(evt.removed);
            } else {
                requestData[fieldName] = fieldValue;
            }

            if(fieldName === "dueDate"){
                requestData["isDueDateChanged"] = true;
            }

            return requestData;
        }

        /**
         * Returns "id" properties of given object.
         * If {@code source} is array, extract "id" property of each object in the array.
         *
         * @param source
         * @returns {Array}
         * @private
         */
        function _getIdProps(source){
            var result = [];

            if(source instanceof Array){
                source.forEach(function(obj){
                    if(obj && obj.id){
                        result.push(obj.id);
                    }
                });
            } else if(source && source.id){
                result.push(source.id);
            }

            return (result.length > 0) ? result : undefined;
        }

        /**
         * "focus" event handler of textarea
         * _onLoadTimeline references {@code vars.isTextareaOnFocus}
         * to hold steady scroll position from textarea
         *
         * @private
         */
        function _onFocusCommentTextarea(){
            vars.isTextareaOnFocused = true;
        }

        /**
         * "blur" event handler of textarea
         *
         * @private
         */
        function _onBlurCommentTextarea(){
            vars.isTextareaOnFocused = false;
        }

        /**
         * "click" event handler of watch/unwatch button.
         * Toggles watch/unwatch issue.
         *
         * @param evt
         * @private
         */
        function _onClickBtnWatch(evt){
            var button = evt.target;
            var watching = _getWatchingState(button);
            var url = watching ? vars.urls.unwatch : vars.urls.watch;

            fetch(url, {"method": "post"}).then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                _setWatchingState(button, !watching);
                button.classList.toggle('ybtn-watching');
                button.innerHTML = Messages(!watching ? "issue.unwatch" : "issue.watch");
                button.blur();

                $yona.notify(Messages(watching ? "issue.unwatch.start" : "issue.watch.start"), 3000);
            });
        }

        /**
         * Initialize fileUploader
         *
         * @private
         */
        function _initFileUploader(){
            var oUploader = yona.Files.getUploader(elements.uploader, elements.textarea);

            if(oUploader){
                // P3-70 라운드3/4: yona.Files.getUploader()는 code.Diff.js/code.SvnDiff.js
                // (라운드5 대상)가 여전히 .attr()로 접근해야 해서 반환값을 jQuery로 감싸둔
                // 상태다 - oUploader[0]로 raw element를 꺼내 네이티브로 읽는다.
                (new yona.Attachments({
                    "elContainer"  : elements.uploader,
                    "elTextarea"   : elements.textarea,
                    "sTplFileItem" : vars.tplFileItem,
                    "sUploaderId"  : oUploader[0].getAttribute("data-namespace")
                }));
            }
        }

        /**
         * Initialize fileDownloader
         *
         * @param target
         * @private
         */
        function _initFileDownloader(target){
            var containers = target || document.querySelectorAll(".attachments");
            containers.forEach(function(container){
                // isYonaAttachment는 milestone.View.js(이미 vanilla)가 확립한 공개 계약 -
                // window.jQuery.data() 정적 접근자로 jQuery 내부 데이터 캐시를 직접 읽는다.
                if(!window.jQuery.data(container, "isYonaAttachment")){
                    (new yona.Attachments({"elContainer": container}));
                }
            });
        }

        /**
         * Update issue timeline
         *
         * @private
         */
        function _updateTimeline(){
            if(vars.isTimelineUpdating){
                return;
            }

            vars.isTimelineUpdating = true;

            fetch(vars.urls.timeline)
             .then(function(response){
                 if(!response.ok){
                     return Promise.reject(response);
                 }
                 return response.text();
             })
             .then(_onLoadTimeline)
             .catch(function(){})
             .finally(function(){
                 vars.isTimelineUpdating = false;
             });
        }

        /**
         * Render issue timeline on load HTML
         *
         * @param resultHTML
         * @private
         */
        function _onLoadTimeline(resultHTML){
            if(resultHTML === vars.timelineHTML){ // update only HTML has changed
                return;
            }

            _fixTimelineHeight();

            var timelineList = _getRenderedTimeline(resultHTML);

            setTimeout(function(){
                elements.timelineList.replaceWith(timelineList);
                elements.timelineList = timelineList;
                vars.timelineHTML = resultHTML;

                var isChanged = (vars.timelineItems !== _countTimelineItems());
                var isTimelineChangedOnTyping = vars.isTextareaOnFocused && isChanged;

                var scrollGap = isTimelineChangedOnTyping ?
                    (_getOffsetTop(elements.textarea) - (window.pageYOffset || document.documentElement.scrollTop)) : 0;

                _unfixTimelineHeight();

                if(isTimelineChangedOnTyping){
                    window.scrollTo(window.pageXOffset || document.documentElement.scrollLeft, _getOffsetTop(elements.textarea) - scrollGap);
                }
            }, 500);
        }

        /**
         * fix timeline height with current height
         *
         * @private
         */
        function _fixTimelineHeight(){
            elements.timelineWrap.style.height = getComputedStyle(elements.timelineWrap).height;
        }

        /**
         * unfix timeline height
         *
         * @private
         */
        function _unfixTimelineHeight(){
            elements.timelineWrap.style.height = "";
            vars.timelineItems = _countTimelineItems();
        }

        /**
         * Get issue timeline element which filled with specified HTML String
         *
         * @param sHTML
         * @returns {*}
         * @private
         */
        function _getRenderedTimeline(timelineHTML){
            var timelineList = elements.timelineList.cloneNode(true);
            timelineList.innerHTML = timelineHTML;

            _initFileDownloader(timelineList.querySelectorAll(".attachments"));
            yona.Markdown.enableMarkdown(timelineList.querySelectorAll("[markdown]"));
            // P3-70 라운드10: 새로 렌더링된(아직 Common.js의 전역 DOMContentLoaded auto-init을
            // 거치지 않은) 타임라인 조각 안의 [data-request-method] 엘리먼트(삭제 버튼)를 개별
            // 초기화한다 - $yona.requestAs는 idempotent라 이미 초기화된 엘리먼트를 다시 넘겨도
            // 안전하다.
            timelineList.querySelectorAll("[data-request-method]").forEach(function(el){
                $yona.requestAs(el);
            }); // delete button

            return timelineList;
        }

        /**
         * Update timeline automatically with interval timer.
         * Don't update if visible .comment-update-form exists
         * or docked inspector is opened.
         *
         * @private
         */
        function _setTimelineUpdateTimer(){
            _unsetTimelineUpdateTimer();

            vars.timelineItems = _countTimelineItems();
            vars.timelineUpdateTimer = setInterval(function(){
                var visibleUpdateForms = Array.prototype.filter.call(
                    elements.timelineWrap.querySelectorAll(".comment-update-form"),
                    _isJQueryVisible
                );
                var isEditing = (visibleUpdateForms.length > 0) || _isDockedInspectorOpened();

                if(vars.isTimelineUpdating !== true && !isEditing){
                    _updateTimeline();
                }
            }, vars.timelineUpdatePeriod);
        }

        function _isDockedInspectorOpened(){
            return (window.outerHeight - window.innerHeight > 100);
        }

        /**
         * Unset IssueTimeline update timer
         *
         * @private
         */
        function _unsetTimelineUpdateTimer(){
            if(vars.timelineUpdateTimer != null){
                clearInterval(vars.timelineUpdateTimer);
            }

            vars.timelineUpdateTimer = null;
        }

        /**
         * Count items in timeline
         * for detect timeline has updated
         *
         * @returns {*}
         * @private
         */
        function _countTimelineItems(){
            return elements.timelineList ? elements.timelineList.querySelectorAll("ul.comments > li").length : 0;
        }

        /**
         * Add "comment & close" like button at comment form
         *
         * @private
         */
        function _initCommentAndCloseButton(){
            var commentForm = document.getElementById("comment-form");
            var dynamicCommentBtn = document.getElementById("dynamic-comment-btn");
            var withStateTransitionInput = document.createElement("input");
            withStateTransitionInput.type = "hidden";
            withStateTransitionInput.name = "withStateTransition";

            if(commentForm){
                commentForm.prepend(withStateTransitionInput);
            }

            if(dynamicCommentBtn){
                dynamicCommentBtn.classList.remove("hidden");
                dynamicCommentBtn.innerHTML = Messages("button.nextState." + vars.nextState);
                dynamicCommentBtn.addEventListener("click", function(){
                    if(elements.textarea.value.length > 0){
                        withStateTransitionInput.value = "true";
                        // issue/view.html 인라인 스크립트의 $(document).on('submit',
                        // '#comment-form', ...) AJAX 핸들러(P3-48)를 그대로 타도록 진짜
                        // "submit" 이벤트를 발생시킨다(위 _triggerFormSubmit 주석 참고).
                        _triggerFormSubmit(commentForm);
                    } else {
                        withStateTransitionInput.value = "";
                        location.href = vars.urls.nextState;
                    }
                });
            }

            if(elements.textarea){
                elements.textarea.addEventListener("keyup", function(){
                    if(dynamicCommentBtn){
                        if(elements.textarea.value.length > 0){
                            dynamicCommentBtn.innerHTML = Messages("button.commentAndNextState." + vars.nextState);
                        } else {
                            dynamicCommentBtn.innerHTML = Messages("button.nextState." + vars.nextState);
                        }
                    }
                });
            }

            // if yona.ShortcutKey exists
            if(yona.ShortcutKey){
                yona.ShortcutKey.attach("CTRL+SHIFT+ENTER", function(htInfo){
                    if(dynamicCommentBtn && elements.textarea === htInfo.elTarget){
                        dynamicCommentBtn.click();
                    }
                });
            }
        }

        function _affixIssueInfoWrap(){
            if(elements.issueInfoWrap){
                elements.issueInfoWrap.classList.add("sticky-issue-info");
            }
        }

        // initialize
        _init(options || {});
    };
})("yona.issue.View");
