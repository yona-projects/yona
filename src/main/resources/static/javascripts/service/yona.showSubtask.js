/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

// 점진적 jQuery 제거 - jQuery($) 없이 순수 DOM API로 재작성. 동작(로컬스토리지 기억,
// 하위 이슈 목록 표시/숨김, 숨길 때 하이라이트/커서 스타일 초기화)은 원본과 동일하다.
function _initShowSubtasks(){
    var showSubtasksAlways = localStorage.getItem('showSubtasksAlways');
    var toggleShowSubtasks = document.getElementById('toggle-show-subtasks');

    function setChildIssueListVisible(visible){
        document.querySelectorAll('.child-issue-list').forEach(function(el){
            el.style.display = visible ? '' : 'none';
        });
    }

    if(showSubtasksAlways === 'true'){
        toggleShowSubtasks.checked = true;
        setChildIssueListVisible(true);
    }

    toggleShowSubtasks.addEventListener('click', function(){
        if(this.checked){
            localStorage.setItem('showSubtasksAlways', true);
            setChildIssueListVisible(true);
        } else {
            localStorage.setItem('showSubtasksAlways', false);
            document.querySelectorAll('.post-item').forEach(function(el){
                el.classList.remove('highlightBg');
                el.style.cursor = '';
            });
            setChildIssueListVisible(false);
        }
    });
}

_initShowSubtasks();
