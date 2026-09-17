/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

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
