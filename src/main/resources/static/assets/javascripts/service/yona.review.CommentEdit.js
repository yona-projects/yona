/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/

/**
 * 리뷰 댓글 고치기(추가 개발, GitHub 기준, decisions.md D-38 7번).
 *
 * 댓글 옆의 고치기 단추(`.comment-edit`, `data-url`)를 누르면 그 자리에서 본문이 입력란으로 바뀐다. 원문은 댓글 안의 숨긴 입력란(`textarea.comment-raw`)에 있다.
 * 저장하면 서버(`POST /comments/review_comment/{id}/edit`)가 마크다운으로 그린 본문(`bodyHtml`)을 돌려주고, 화면은 새로 읽지 않고 그 자리의 본문과 "수정됨" 표시만 바꾼다.
 */
(function ($) {
    "use strict";

    function close($comment) {
        $comment.find(".comment-edit-form").remove();
        $comment.find(".comment-body").show();
    }

    $(document).on("click", ".comment-edit", function () {
        var $button = $(this), $comment = $button.closest("li.comment");
        if ($comment.find(".comment-edit-form").length) {
            return;
        }
        var $body = $comment.find(".comment-body");
        var $form = $('<div class="comment-edit-form mt10"><textarea class="comment-edit-text form-control" rows="6" style="width:100%; box-sizing:border-box;"></textarea>' +
            '<div class="right-txt mt5"><button type="button" class="ybtn ybtn-small comment-edit-cancel"></button> ' +
            '<button type="button" class="ybtn ybtn-success ybtn-small comment-edit-save"></button></div></div>');
        $form.find(".comment-edit-text").val($comment.find("textarea.comment-raw").val());
        $form.find(".comment-edit-cancel").text(Messages("button.cancel"));
        $form.find(".comment-edit-save").text(Messages("button.save"));
        $body.hide().after($form);
        $form.find(".comment-edit-text").focus();

        $form.on("click", ".comment-edit-cancel", function () { close($comment); });
        $form.on("click", ".comment-edit-save", function () {
            var contents = $form.find(".comment-edit-text").val();
            if ($.trim(contents) === "") {
                $yobi.notify(Messages("post.comment.empty"), 3000);
                return;
            }
            $.ajax($button.data("url"), {method: "POST", dataType: "json", data: {contents: contents}}).done(function (response) {
                $comment.find("textarea.comment-raw").val(contents);
                $body.html(response.bodyHtml);
                if (!$comment.find(".comment-edited").length) {
                    $comment.find(".meta-info .ago").first().after($('<span class="comment-edited ago"></span>').text("(" + Messages("reviewComment.edited") + ")"));
                }
                close($comment);
            }).fail(function (xhr) {
                $yobi.notify(Messages("error.failedTo", Messages("common.comment.edit"), xhr.status, xhr.statusText));
            });
        });
    });
})(jQuery);
