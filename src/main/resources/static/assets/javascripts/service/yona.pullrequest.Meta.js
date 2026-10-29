/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/

/**
 * 풀 리퀘스트 보기의 담당자와 라벨 선택(추가 항목 X-14, decisions.md D-34).
 *
 * 이슈 화면처럼 선택하는 즉시 Ajax로 보내고 화면을 다시 불러오지 않는다. 알림창(toast)으로 알리고 타임라인만 다시 불러온다.
 * - 담당자: `yona.issue.Assginee.js`와 같은 원격 검색 선택 상자(후보는 프로젝트 단위 API). 지우면 담당자를 해제한다.
 * - 라벨: `yobi.issue.View.js`의 일괄 변경처럼 붙인 라벨과 뗀 라벨의 번호만 보낸다.
 *
 * 주소는 `#pr-meta`의 데이터 속성(`data-assignees-url`, `data-labels-url`, `data-timeline-url`, `data-candidates-url`)에 있다.
 */
(function ($) {
    "use strict";

    function formatter(result) {
        if (!result.avatarUrl) {
            return '<div>' + result.name + '</div>';
        }
        var tplUserItem = $("#tplSelect2FormatUser").text() || '<div class="usf-group" title="${name} ${loginId}">' +
            '<span class="avatar-wrap smaller"><img src="${avatarURL}" width="20" height="20"></span>' +
            '<strong class="name">${name}</strong>' +
            '<span class="loginid">${loginId}</span></div>';
        return $.tmpl(tplUserItem, {"avatarURL": result.avatarUrl, "name": result.name, "loginId": result.loginId});
    }

    function matcher(term, text, result) {
        term = term.toLowerCase();
        var loginId = (typeof result.loginId !== "undefined") ? result.loginId.toLowerCase() : "";
        return (loginId.indexOf(term) > -1) || (text.toLowerCase().indexOf(term) > -1);
    }

    /** select2의 change 이벤트가 알려 주는 선택 항목들의 `id`만 모은다. 없으면 `undefined`이다. */
    function idsOf(source) {
        var ids = [];
        if (source instanceof Array) {
            source.forEach(function (obj) { if (obj && obj.id) { ids.push(obj.id); } });
        } else if (source && source.id) {
            ids.push(source.id);
        }
        return ids.length > 0 ? ids : undefined;
    }

    $(function () {
        var $meta = $("#pr-meta");
        if (!$meta.length) {
            return;
        }
        var urls = {
            assignees: $meta.data("assignees-url"),
            labels: $meta.data("labels-url"),
            timeline: $meta.data("timeline-url"),
            candidates: $meta.data("candidates-url")
        };

        /** 변경 뒤 타임라인 줄만 다시 불러오고 라벨 상자의 글자색을 다시 맞춘다. */
        function refreshTimeline() {
            $.get(urls.timeline, function (html) {
                var $comments = $("#comments");
                if (!$comments.length) {
                    $comments = $('<ul class="comments" id="comments"></ul>').appendTo(".board-comment-wrap");
                }
                $comments.html(html);
                $comments.find(".event > .label").each(function () {
                    var $label = $(this);
                    $label.removeClass("dimgray white").addClass($yobi.getContrastColor($label.css("background-color")));
                });
            });
        }

        function failed(title, xhr) {
            $yobi.notify(Messages("error.failedTo", title, xhr.status, xhr.statusText));
        }

        // 담당자
        var $assignee = $("#assignee");
        if ($assignee.length) {
            $assignee.select2({
                minimumInputLength: 0,
                allowClear: true,
                id: function (obj) { return obj.loginId; },
                ajax: {
                    url: urls.candidates,
                    dataType: "json",
                    quietMillis: 300,
                    data: function (term) { return {query: term}; },
                    results: function (data) { return {results: data}; },
                    cache: true
                },
                initSelection: function (element, callback) {
                    var loginId = $(element).val();
                    if (loginId !== "") {
                        callback({loginId: loginId, name: $(element).data("name"), avatarUrl: $(element).data("avatar-url")});
                    }
                },
                formatResult: formatter,
                formatSelection: formatter,
                matcher: matcher,
                escapeMarkup: function (m) { return m; }
            });

            $assignee.on("change", function () {
                var loginId = $assignee.val();
                $.ajax(urls.assignees, {
                    method: "POST",
                    dataType: "json",
                    contentType: "application/json",
                    data: JSON.stringify({assignees: loginId ? [loginId] : []})
                }).done(function (response) {
                    $yobi.notify(Messages("issue.assignee") + ": " + (response.assignee ? response.assignee.name : Messages("issue.noAssignee")), 3000);
                    refreshTimeline();
                }).fail(function (xhr) {
                    failed(Messages("issue.assignee"), xhr);
                });
            });
        }

        // 라벨
        $("#labelIds").on("change", function (evt) {
            var data = {};
            var added = idsOf(evt.added);
            var removed = idsOf(evt.removed);
            if (added) { data.attachingLabelIds = added; }
            if (removed) { data.detachingLabelIds = removed; }
            $.ajax(urls.labels, {method: "POST", dataType: "json", traditional: true, data: data}).done(function () {
                $yobi.notify(Messages("issue.update.labelIds"), 3000);
                refreshTimeline();
            }).fail(function (xhr) {
                failed(Messages("issue.update.labelIds"), xhr);
            });
        });
    });
})(jQuery);
