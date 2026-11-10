/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

import { register } from "../namespace.js";

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

// 템플릿의 인라인 스크립트와 다른 모듈이 전역 이름으로 부른다
register("_initShowSubtasks", _initShowSubtasks);
