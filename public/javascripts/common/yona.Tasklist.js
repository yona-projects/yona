/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

$(function () {
    var $markdownWrap = $(".markdown-wrap");
    var inputCheckBox = "input[type='checkbox']";

    checkTasklistDoneCount($markdownWrap);
    disableCheckboxIfNeeds($markdownWrap);

    $markdownWrap.find(inputCheckBox).each(function () {
        var $this = $(this);
        var $parent = $this.closest();

        $parent
            .click(function () {
                $this.trigger('click');
            })
            .hover(function(){
                $(this).css({ cursor: 'pointer' });
            });
    });

    $markdownWrap.find(inputCheckBox).on("click", function () {
        var $this = $(this);
        var $form = $this.closest("div[id]").prev().find("form");
        var url = $form.attr("action");
        var originalText = $form.find("textarea").val();
        checkTask($this);

        var text = $form.find("textarea").val();

        $.ajax({
            method: "PATCH",
            url: url,
            contentType: "application/json",
            data: JSON.stringify({ content: text, original: originalText }),
            beforeSend: function() {
                NProgress.start();
            }
        })
        .done(function (msg) {
            NProgress.done();
            checkTasklistDoneCount($markdownWrap);
        })
        .fail(function(jqXHR, textStatus){
            var response = JSON.parse(jqXHR.responseText);
            var message = '[' + jqXHR.statusText + '] ' + response.message + '\n\nRefresh the page!';
            $yobi.showAlert(message);
            NProgress.done();
        });

    });

    function checkTask(that, checked) {
        var $this = that;
        var isChecked;
        if(checked === undefined) {
            isChecked = $this.prop("checked");
        } else {
            isChecked = checked;
        }

        $this.prop('checked', isChecked);

        var $parent = $this.closest(".markdown-wrap");
        var index = $parent.find(inputCheckBox).index($this);
        var $form = $this.closest("div[id]").prev().find("form");
        var $textarea = $form.find("textarea");
        var text = $textarea.val();

        var counter = 0;
        // See: https://regex101.com/r/uIC2RM/2
        text = text.replace(/^([ ]*[-+*] \[[ xX]?])([ ]?.+)/gm, function replacer(match, checkbox, text){
            var composedText = checkbox + text;
            if(index === counter) {
                if(isChecked) {
                    composedText = checkbox.replace(/\[[ ]?]/, "[x]") + text
                } else {
                    composedText = checkbox.replace(/\[[xX]?]/, "[ ]") + text
                }
            }
            counter++;
            return composedText;
        });

        $textarea.val(text);
        $this.next().find(inputCheckBox).each(function () {
            checkTask($(this), isChecked);
        });
    }

    function checkTasklistDoneCount($target) {
        $target.each(function( index ) {
            var $this = $(this);
            var total = 0;
            var checked = 0;
            $this.find(inputCheckBox).each(function () {
                total++;
                if($(this).prop("checked")) {
                    checked++;
                }
            });
            var $tasklist = $this.prev();
            var percentage = checked / total * 100;
            $tasklist.find(".done-counter").html("(" + checked + "/" + total + ")");
            $tasklist.find(".bar").width(percentage + "%");
            $tasklist.find(".task-title").width(percentage + "%");
            if(total > 0) {
                $tasklist.addClass("task-show");
            }
            if(percentage === 100) {
                $tasklist.find(".bar").removeClass("red").addClass("green");
            } else {
                $tasklist.find(".bar").removeClass("green").addClass("red");
            }
        });
    }

    function disableCheckboxIfNeeds($target){
        $target.each(function() {
            var $this = $(this);
            if($this.data("allowedUpdate") !== true) {
                $this.find(inputCheckBox).each(function () {
                    $(this).prop("disabled", true);
                });
            }
        });
    }

    // See: addTaskListButtonListener() at views/common/scripts.scala.html
});
