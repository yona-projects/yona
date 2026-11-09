$(function(){
    // timeline label text color adjusting
    $(".event > .badge").each(function() {
        var $this = $(this);
        $this.removeClass("dimgray white")
            .addClass($yona.getContrastColor($this.css('background-color')))
    });

    // Releated with one line sub-comment feature
    $(".add-a-comment").on("click", function(e){
        var parent = $(this).parents(".comment");

        // Show input form
        parent.find(".child-comment-input-form").toggle();

        // 누를 때마다 처리기가 쌓이지 않도록 이름 공간으로 먼저 떼고 붙인다(R-143)
        parent.find("textarea").off(".subcomment").on('keypress.subcomment', function(e) {
            // Enter to submit
            if ((e.metaKey || e.Control) && $yona.isEnterKey(e)) {
                $(this).parents('form').submit();
                return false;
            }
        }).on('keyup.subcomment', function(e) {
            // Cancel input. `visibility`는 건드리지 않는다: 다시 "대댓글 추가"를 눌러 `toggle()`이 `display`만 되돌려도 입력란이 보이게 한다(R-143)
            if ($yona.isEscapeKey(e)) {
                $(".child-comment-input-form").css("display", "none");
                $(".add-a-comment").show();
            }
        }).focus();
    });

    $(".comment").on("mouseenter tab", function () {
        var $this = $(this);
        if(!$this.find(".textarea-box > textarea").is(":visible")) {
            $this.find(".add-a-comment").fadeIn(300);
            $this.find(".new-issue-by").fadeIn(300);
            $this.find(".share-link").fadeIn(300);
        }
    }).on("mouseleave", function () {
        $(this).find(".add-a-comment").fadeOut(300);
        $(this).find(".new-issue-by").fadeOut(300);
        $(this).find(".share-link").fadeOut(300);
    });

    // Releated with one line sub-comment feature
    $(".subcomment-author").each(function addAuthorToLastParagraphOfOnelineComment(index, el){
        // append Author and addtionals to mardkown rendered contents
        var $el = $(el);
        // Remove spaces
        var trimmed = $el.html().replace(/\s\s+/g, ' ');
        // Find parent element
        var $closest = $el.closest('.contents');
        var normalTextRenderedParagraph = $closest.find('p').last();

        // Remove unused author and addtional text
        $el.remove();
        if(normalTextRenderedParagraph.length === 0){
            $closest.append(trimmed);
        } else {
            normalTextRenderedParagraph.append(trimmed);
        }
    });
});
