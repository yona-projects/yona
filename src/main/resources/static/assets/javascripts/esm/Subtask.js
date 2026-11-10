

// ES 모듈은 문서 분석이 끝난 뒤 DOMContentLoaded 전에 실행되며, 아래 준비 완료 처리기는 그때 등록되어 문서가 준비되면 돈다.
$(() => {
    $(".subtask-message").on("click", function () {
        const subtaskWrap = $(".subtask-wrap");
        subtaskWrap.toggle();
        if(subtaskWrap.is( ":visible" )){
            $(this).addClass("option-on");
        } else {
            $(this).removeClass("option-on");
        }
        const subtaskInputFields = subtaskWrap.find("select");
        subtaskInputFields.each(function(){
            this.disabled = !this.disabled;
        });
    });

    const initialProject = $("#targetProjectId");
    const initialProjectId = initialProject.val();
    initialProject.on("change", (selected) =>{
        const parentId = $("#parentId");
        const targetProjectName = selected.target.selectedOptions[0].innerText;
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
