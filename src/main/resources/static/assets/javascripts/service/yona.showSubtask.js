/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function _initShowSubtasks(){
    const showSubtasksAlways = localStorage.getItem('showSubtasksAlways');
    const $toggleShowSubtasks = $("#toggle-show-subtasks");

    if( showSubtasksAlways  === 'true'){
        $toggleShowSubtasks.prop('checked', true);
        $(".child-issue-list").show();
    }
    $toggleShowSubtasks.on('click', function () {
        if(this.checked){
            localStorage.setItem('showSubtasksAlways', true);
            $(".child-issue-list").show();
        } else {
            localStorage.setItem('showSubtasksAlways', false);
            $('.post-item').removeClass('highlightBg').css("cursor", "");
            $(".child-issue-list").hide();
        }
    });
}

_initShowSubtasks();
