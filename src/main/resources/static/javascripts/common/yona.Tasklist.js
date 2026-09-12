/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

document.addEventListener("DOMContentLoaded", function () {
    var markdownWraps = document.querySelectorAll(".markdown-wrap");
    var inputCheckBox = "input[type='checkbox']";

    checkTasklistDoneCount(markdownWraps);
    disableCheckboxIfNeeds(markdownWraps);

    // 레거시 버그 보존: 원본은 $this.closest()를 인자 없이 호출해 항상 빈 jQuery
    // 컬렉션이 되므로("부모를 클릭/호버하면 체크박스가 토글된다"는 의도와 달리)
    // 이 블록은 처음부터 아무 동작도 하지 않는 죽은 코드였다. 동작을 바꾸지 않기
    // 위해 그대로 아무것도 바인딩하지 않는다.

    markdownWraps.forEach(function (wrap) {
        wrap.querySelectorAll(inputCheckBox).forEach(function (checkbox) {
            checkbox.addEventListener("click", function () {
                var form = checkbox.closest("div[id]").previousElementSibling.querySelector("form");
                var url = form.getAttribute("action");
                var textarea = form.querySelector("textarea");
                var originalText = textarea.value;
                checkTask(checkbox);

                var text = textarea.value;

                NProgress.start();
                fetch(url, {
                    method: "PATCH",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({ content: text, original: originalText })
                })
                .then(function(response){
                    if(!response.ok){
                        return response.text().then(function(text){
                            return Promise.reject({statusText: response.statusText, responseText: text});
                        });
                    }
                    return response.text();
                })
                .then(function (msg) {
                    NProgress.done();
                    checkTasklistDoneCount(markdownWraps);
                })
                .catch(function(err){
                    var response = JSON.parse(err.responseText);
                    var message = '[' + err.statusText + '] ' + response.message + '\n\nRefresh the page!';
                    $yona.showAlert(message);
                    NProgress.done();
                });
            });
        });
    });

    function checkTask(that, checked) {
        var isChecked;
        if(checked === undefined) {
            isChecked = that.checked;
        } else {
            isChecked = checked;
        }

        that.checked = isChecked;

        var parent = that.closest(".markdown-wrap");
        var index = Array.prototype.indexOf.call(parent.querySelectorAll(inputCheckBox), that);
        var form = that.closest("div[id]").previousElementSibling.querySelector("form");
        var textarea = form.querySelector("textarea");
        var text = textarea.value;

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

        textarea.value = text;
        if(that.nextElementSibling){
            that.nextElementSibling.querySelectorAll(inputCheckBox).forEach(function (checkbox) {
                checkTask(checkbox, isChecked);
            });
        }
    }

    function checkTasklistDoneCount(targets) {
        targets.forEach(function (target) {
            var total = 0;
            var checked = 0;
            target.querySelectorAll(inputCheckBox).forEach(function (checkbox) {
                total++;
                if(checkbox.checked) {
                    checked++;
                }
            });
            var tasklist = target.previousElementSibling;
            var percentage = checked / total * 100;
            tasklist.querySelector(".done-counter").innerHTML = "(" + checked + "/" + total + ")";
            tasklist.querySelector(".bar").style.width = percentage + "%";
            tasklist.querySelector(".task-title").style.width = percentage + "%";
            if(total > 0) {
                tasklist.classList.add("task-show");
            }
            if(percentage === 100) {
                tasklist.querySelector(".bar").classList.remove("red");
                tasklist.querySelector(".bar").classList.add("green");
            } else {
                tasklist.querySelector(".bar").classList.remove("green");
                tasklist.querySelector(".bar").classList.add("red");
            }
        });
    }

    function disableCheckboxIfNeeds(targets){
        targets.forEach(function (target) {
            if(target.dataset.allowedUpdate !== "true") {
                target.querySelectorAll(inputCheckBox).forEach(function (checkbox) {
                    checkbox.disabled = true;
                });
            }
        });
    }

    // See: addTaskListButtonListener() at views/common/scripts.scala.html
});
