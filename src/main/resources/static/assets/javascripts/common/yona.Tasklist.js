/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

$(() => {
    const $markdownWrap = $(".markdown-wrap");
    const inputCheckBox = "input[type='checkbox']";

    checkTasklistDoneCount($markdownWrap);
    disableCheckboxIfNeeds($markdownWrap);

    $markdownWrap.find(inputCheckBox).each(function () {
        const $this = $(this);
        const $parent = $this.closest();

        $parent
            .click(() => {
                $this.trigger('click');
            })
            .hover(function(){
                $(this).css({ cursor: 'pointer' });
            });
    });

    $markdownWrap.find(inputCheckBox).on("click", function () {
        const $this = $(this);
        const $form = $this.closest("div[id]").prev().find("form");
        const url = $form.attr("action");
        const originalText = $form.find("textarea").val();
        checkTask($this);

        const text = $form.find("textarea").val();

        $.ajax({
            method: "PATCH",
            url: url,
            contentType: "application/json",
            data: JSON.stringify({ content: text, original: originalText }),
            beforeSend: function() {
                NProgress.start();
            }
        })
        .done((msg) => {
            NProgress.done();
            checkTasklistDoneCount($markdownWrap);
        })
        .fail((jqXHR, textStatus) =>{
            const response = JSON.parse(jqXHR.responseText);
            const message = '[' + jqXHR.statusText + '] ' + response.message + '\n\nRefresh the page!';
            $yona.showAlert(message);
            NProgress.done();
        });

    });

    function checkTask(that, checked) {
        const $this = that;
        let isChecked;
        if(checked === undefined) {
            isChecked = $this.prop("checked");
        } else {
            isChecked = checked;
        }

        $this.prop('checked', isChecked);

        const $parent = $this.closest(".markdown-wrap");
        const index = $parent.find(inputCheckBox).index($this);
        const $form = $this.closest("div[id]").prev().find("form");
        const $textarea = $form.find("textarea");
        let text = $textarea.val();

        let counter = 0;
        // See: https://regex101.com/r/uIC2RM/2
        text = text.replace(/^([ ]*[-+*] \[[ xX]?])([ ]?.+)/gm, function replacer(match, checkbox, text){
            let composedText = checkbox + text;
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
            const $this = $(this);
            let total = 0;
            let checked = 0;
            $this.find(inputCheckBox).each(function () {
                total++;
                if($(this).prop("checked")) {
                    checked++;
                }
            });
            const $tasklist = $this.prev();
            const percentage = checked / total * 100;
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
            const $this = $(this);
            if($this.data("allowedUpdate") !== true) {
                $this.find(inputCheckBox).each(function () {
                    $(this).prop("disabled", true);
                });
            }
        });
    }

    // See: addTaskListButtonListener() at views/common/scripts.scala.html
});
