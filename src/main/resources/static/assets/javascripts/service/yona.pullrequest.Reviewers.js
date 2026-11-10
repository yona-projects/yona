/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/

/**
 * 풀 리퀘스트 보기의 리뷰어 칸(추가 항목 X-27, decisions.md D-37).
 *
 * 수정 권한이 있으면 리뷰를 요청할 사람을 원격 검색 선택 상자(`#review-request`)로 고르는 즉시 Ajax로 요청하고, 대기 중인 사람 옆의 취소 단추(`.review-cancel`)와
 * 리뷰한 사람 옆의 다시 요청 단추(`.review-rerequest`)도 Ajax로 보낸다. 성공하면 알림창(toast)으로 알리고 화면을 다시 불러오지 않고
 * 리뷰 영역(`#review-area`)과 타임라인만 새로 읽어 바꾼다.
 *
 * 주소는 `#review-request`의 데이터 속성(`data-request-url`, `data-candidates-url`, `data-timeline-url`)과 단추의 `data-url`, `data-reviewer`에 있다.
 */
(function ($) {
    "use strict";

    function formatter(result) {
        if (!result.avatarUrl) {
            return '<div>' + result.name + '</div>';
        }
        return '<div class="usf-group" title="' + result.name + ' ' + result.loginId + '">' +
            '<span class="avatar-wrap smaller"><img src="' + result.avatarUrl + '" width="20" height="20"></span>' +
            '<strong class="name">' + result.name + '</strong>' +
            '<span class="loginid">' + result.loginId + '</span></div>';
    }

    function failed(xhr) {
        $yona.notify(Messages("error.failedTo", Messages("pullRequest.review.area.title"), xhr.status, xhr.statusText));
    }

    /** 변경 뒤 리뷰 영역과 타임라인만 다시 읽는다. */
    function refresh(timelineUrl) {
        yona.http.get(window.location.href, (html) => {
            // 읽어 온 문서는 DOMParser로 읽어 스크립트를 실행하지 않는다. `#review-area`가 없으면(jQuery `.html(undefined)`처럼) 바꾸지 않는다
            const fresh = new DOMParser().parseFromString(html, "text/html").querySelector("#review-area");
            if (fresh) {
                fresh.querySelectorAll("script").forEach((script) => { script.remove(); });
                $("#review-area").html(fresh.innerHTML);
            }
            bindRequestBox();
        });
        if (timelineUrl) {
            yona.http.get(timelineUrl, (html) => {
                const $comments = $("#comments");
                if ($comments.length) {
                    $comments.html(html);
                }
            });
        }
    }

    function send(url, data, done) {
        yona.http.ajax(url, {method: "POST", dataType: "json", contentType: "application/json", data: JSON.stringify(data || {})})
            .done(done).fail(failed);
    }

    function timelineUrl() {
        return $("#review-request").data("timeline-url");
    }

    function bindRequestBox() {
        const $box = $("#review-request");
        if (!$box.length) {
            return;
        }
        $box.select2({
            minimumInputLength: 1,
            allowClear: true,
            ajax: yona.ui.Select2.userSearch($box.data("candidates-url"), false),
            templateResult: yona.ui.Select2.userTemplate(formatter),
            templateSelection: yona.ui.Select2.userTemplate(formatter),
            escapeMarkup: function (m) { return m; }
        });
        $box.on("change", (evt) => {
            const loginId = $box.val();
            if (!loginId) {
                return;
            }
            send($box.data("request-url"), {reviewer: loginId}, () => {
                $yona.notify(Messages("pullRequest.review.request.done"), 3000);
                refresh(timelineUrl());
            });
        });
    }

    $(() => {
        if (!$("#review-area").length) {
            return;
        }
        bindRequestBox();

        // 취소와 다시 요청 단추는 영역을 다시 읽어도 동작하도록 문서에 위임한다
        $(document).on("click", "#review-area .review-cancel", function () {
            send($(this).data("url"), {}, () => {
                $yona.notify(Messages("pullRequest.review.request.cancelled"), 3000);
                refresh(timelineUrl());
            });
        });
        $(document).on("click", "#review-area .review-rerequest", function () {
            send($(this).data("url"), {reviewer: $(this).data("reviewer")}, () => {
                $yona.notify(Messages("pullRequest.review.request.done"), 3000);
                refresh(timelineUrl());
            });
        });
    });
})(jQuery);
