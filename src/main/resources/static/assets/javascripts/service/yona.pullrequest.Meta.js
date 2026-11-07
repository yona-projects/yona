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
 * - 라벨: `yona.issue.View.js`의 일괄 변경처럼 붙인 라벨과 뗀 라벨의 번호만 보낸다.
 *
 * 생성 폼(`data-mode="form"`)에서는 아직 풀 리퀘스트가 없으므로 선택 상자만 꾸미고 고른 값은 폼과 함께 보낸다.
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

    function matcher(params, data) {
        var term = (params.term || "").trim().toLowerCase();
        if (term === "") {
            return data;
        }
        var loginId = (typeof data.loginId !== "undefined") ? data.loginId.toLowerCase() : "";
        return ((loginId.indexOf(term) > -1) || ((data.text || "").toLowerCase().indexOf(term) > -1)) ? data : null;
    }

    /** 바뀐 선택 항목들의 `id`만 모은다. 없으면 `undefined`이다. */
    function idsOf(items) {
        var ids = items.map(function (item) { return item.id; }).filter(Boolean);
        return ids.length > 0 ? ids : undefined;
    }

    $(function () {
        var $meta = $("#pr-meta");
        if (!$meta.length) {
            return;
        }
        var formMode = $meta.data("mode") === "form";
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
                $comments.find(".event > .badge").each(function () {
                    var $label = $(this);
                    $label.removeClass("dimgray white").addClass($yona.getContrastColor($label.css("background-color")));
                });
            });
        }

        function failed(title, xhr) {
            $yona.notify(Messages("error.failedTo", title, xhr.status, xhr.statusText));
        }

        // 담당자
        var $assignee = $("#assignee");
        if ($assignee.length) {
            $assignee.select2({
                minimumInputLength: 0,
                allowClear: true,
                ajax: yona.ui.Select2.userSearch(urls.candidates),
                templateResult: yona.ui.Select2.userTemplate(formatter),
                templateSelection: yona.ui.Select2.userTemplate(formatter),
                matcher: matcher,
                escapeMarkup: function (m) { return m; }
            });

            $assignee.on("change", function () {
                if (formMode) { return; }
                var loginId = $assignee.val();
                $.ajax(urls.assignees, {
                    method: "POST",
                    dataType: "json",
                    contentType: "application/json",
                    data: JSON.stringify({assignees: loginId ? [loginId] : []})
                }).done(function (response) {
                    $yona.notify(Messages("issue.assignee") + ": " + (response.assignee ? response.assignee.name : Messages("issue.noAssignee")), 3000);
                    refreshTimeline();
                }).fail(function (xhr) {
                    failed(Messages("issue.assignee"), xhr);
                });
            });
        }

        // 라벨
        $("#labelIds").on("change", function (evt) {
            if (formMode) { return; }
            var data = {};
            var changes = yona.ui.Select2.selectionChanges(evt.target);
            var added = idsOf(changes.added);
            var removed = idsOf(changes.removed);
            if (added) { data.attachingLabelIds = added; }
            if (removed) { data.detachingLabelIds = removed; }
            $.ajax(urls.labels, {method: "POST", dataType: "json", traditional: true, data: data}).done(function () {
                $yona.notify(Messages("issue.update.labelIds"), 3000);
                refreshTimeline();
            }).fail(function (xhr) {
                failed(Messages("issue.update.labelIds"), xhr);
            });
        });
    });
})(jQuery);
