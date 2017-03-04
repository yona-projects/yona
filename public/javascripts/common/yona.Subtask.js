$(function () {
    $(".subtask-message").on("click", function () {
        var subtaskWrap = $(".subtask-wrap");
        subtaskWrap.toggle();
        var subtaskInputFields = subtaskWrap.find("select");
        subtaskInputFields.each(function(){
            this.disabled = !this.disabled;
        });
    });
});
