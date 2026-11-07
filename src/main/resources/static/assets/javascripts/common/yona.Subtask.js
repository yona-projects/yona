$(function () {
    $(".subtask-message").on("click", function () {
        var subtaskWrap = $(".subtask-wrap");
        subtaskWrap.toggle();
        if(subtaskWrap.is( ":visible" )){
            $(this).addClass("option-on");
        } else {
            $(this).removeClass("option-on");
        }
        var subtaskInputFields = subtaskWrap.find("select");
        subtaskInputFields.each(function(){
            this.disabled = !this.disabled;
        });
    });

    var initialProject = $("#targetProjectId");
    var initialProjectId = initialProject.val();
    initialProject.on("change", function(selected){
        var parentId = $("#parentId");
        var targetProjectName = selected.target.selectedOptions[0].innerText;
        if($(selected.target).val() === initialProjectId){
            parentId.prop("disabled", false);
            parentId.next(".select2-container").show();
            parentId.trigger('change.select2');
        } else {
            parentId.val(parentId.find("option:first").val());
            parentId.prop("disabled", true);
            parentId.next(".select2-container").hide();
            parentId.trigger('change.select2');
            $yona.notify("Issue will be moved or written to '" + targetProjectName + "'", 4000);
        }
    });
});
