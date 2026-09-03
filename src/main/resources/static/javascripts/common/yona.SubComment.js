$(function(){
    // timeline label text color adjusting
    $(".event > .label").each(function() {
        var $this = $(this);
        $this.removeClass("dimgray white")
            .addClass($yobi.getContrastColor($this.css('background-color')))
    });

    // Releated with one line sub-comment feature
    $(".add-a-comment").on("click", function(e){
        var parent = $(this).parents(".comment");

        // Show input form
        parent.find(".child-comment-input-form").toggle();

        parent.find("textarea").on('keypress', function(e) {
            // Enter to submit
            if ((e.metaKey || e.Control) && (e.keyCode || e.which) === 13) {
                $(this).parents('form').submit();
                return false;
            }
        }).on('keyup', function(e) {
            // Cancel input
            if ((e.keyCode || e.which) === 27) {
                $(".child-comment-input-form").css("display", "none").css("visibility", "hidden");
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
