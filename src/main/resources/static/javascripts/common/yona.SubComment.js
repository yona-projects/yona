document.addEventListener("DOMContentLoaded", function(){
    // jQuery의 :visible 판정과 동일한 공식(jQuery 소스 그대로).
    function isVisible(el){
        return !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
    }

    function fadeIn(el, duration){
        el.style.transition = "opacity " + duration + "ms";
        el.style.display = "";
        el.style.opacity = "0";
        requestAnimationFrame(function(){
            el.style.opacity = "1";
        });
    }

    function fadeOut(el, duration){
        el.style.transition = "opacity " + duration + "ms";
        el.style.opacity = "0";
        setTimeout(function(){
            el.style.display = "none";
        }, duration);
    }

    // timeline label text color adjusting
    document.querySelectorAll(".event > .label").forEach(function(el){
        el.classList.remove("dimgray", "white");
        el.classList.add($yona.getContrastColor(getComputedStyle(el).backgroundColor));
    });

    // Releated with one line sub-comment feature
    document.querySelectorAll(".add-a-comment").forEach(function(el){
        el.addEventListener("click", function(e){
            var parent = this.closest(".comment");

            // Show input form
            var inputForm = parent.querySelector(".child-comment-input-form");
            if(inputForm){
                inputForm.style.display = (getComputedStyle(inputForm).display === "none") ? "" : "none";
            }

            parent.querySelectorAll("textarea").forEach(function(textarea){
                textarea.addEventListener('keypress', function(e) {
                    // Enter to submit
                    if ((e.metaKey || e.Control) && (e.keyCode || e.which) === 13) {
                        var form = textarea.closest('form');
                        if(form){
                            form.submit();
                        }
                        return false;
                    }
                });
                textarea.addEventListener('keyup', function(e) {
                    // Cancel input
                    if ((e.keyCode || e.which) === 27) {
                        document.querySelectorAll(".child-comment-input-form").forEach(function(el){
                            el.style.display = "none";
                            el.style.visibility = "hidden";
                        });
                        document.querySelectorAll(".add-a-comment").forEach(function(el){
                            el.style.display = "";
                        });
                    }
                });
                textarea.focus();
            });
        });
    });

    document.querySelectorAll(".comment").forEach(function(comment){
        ["mouseenter", "tab"].forEach(function(eventType){
            comment.addEventListener(eventType, function () {
                var textareaBox = comment.querySelector(".textarea-box > textarea");
                if(!textareaBox || !isVisible(textareaBox)) {
                    var addAComment = comment.querySelector(".add-a-comment");
                    var newIssueBy = comment.querySelector(".new-issue-by");
                    var shareLink = comment.querySelector(".share-link");
                    if(addAComment){ fadeIn(addAComment, 300); }
                    if(newIssueBy){ fadeIn(newIssueBy, 300); }
                    if(shareLink){ fadeIn(shareLink, 300); }
                }
            });
        });
        comment.addEventListener("mouseleave", function () {
            var addAComment = comment.querySelector(".add-a-comment");
            var newIssueBy = comment.querySelector(".new-issue-by");
            var shareLink = comment.querySelector(".share-link");
            if(addAComment){ fadeOut(addAComment, 300); }
            if(newIssueBy){ fadeOut(newIssueBy, 300); }
            if(shareLink){ fadeOut(shareLink, 300); }
        });
    });

    // Releated with one line sub-comment feature
    document.querySelectorAll(".subcomment-author").forEach(function addAuthorToLastParagraphOfOnelineComment(el){
        // append Author and addtionals to mardkown rendered contents
        // Remove spaces
        var trimmed = el.innerHTML.replace(/\s\s+/g, ' ');
        // Find parent element
        var closest = el.closest('.contents');
        var paragraphs = closest.querySelectorAll('p');
        var normalTextRenderedParagraph = paragraphs.length ? paragraphs[paragraphs.length - 1] : null;

        // Remove unused author and addtional text
        el.remove();
        if(!normalTextRenderedParagraph){
            closest.insertAdjacentHTML('beforeend', trimmed);
        } else {
            normalTextRenderedParagraph.insertAdjacentHTML('beforeend', trimmed);
        }
    });
});
