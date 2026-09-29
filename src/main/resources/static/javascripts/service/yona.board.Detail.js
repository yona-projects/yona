(function () {
    'use strict';

    // 게시글 상세(#post-detail-content)의 마운트/해제. 일반 상세 페이지에서는 DOMContentLoaded에,
    // 2단 보기(Turbo Frame)에서는 상세 프레임이 바뀔 때마다 yona.board.Turbo.js가 호출한다.
    // 이슈 상세(yona.issue.Detail.js)와 같은 lifecycle 계약: 모든 리스너는 AbortController로,
    // 나머지는 cleanups로 묶어 dispose 한 번에 되돌린다.
    window.yona = window.yona || {};
    yona.mountBoardDetail = function (root) {
        if (!root) { return function () {}; }
        if (root._disposeBoardDetail) { return root._disposeBoardDetail; }
        var lifecycle = new AbortController();
        var cleanups = [];
        var data = root.dataset;
        var form = root.querySelector('#comment-form');
        var textarea = form && form.querySelector('textarea[name="contents"]');
        var submitting = false;

        function own(cleanup) {
            if (typeof cleanup === 'function') { cleanups.push(cleanup); }
        }
        function on(target, type, listener) {
            if (target) { target.addEventListener(type, listener, {signal: lifecycle.signal}); }
        }
        function errorMessage(text) {
            try {
                var parsed = JSON.parse(text);
                if (parsed && parsed.message) { return parsed.message; }
            } catch (ignore) { /* not JSON */ }
            return text || '';
        }
        function request(url, options, message, onSuccess) {
            fetch(url, Object.assign({}, options, {signal: lifecycle.signal})).then(function (response) {
                if (lifecycle.signal.aborted) { return; }
                if (response.ok) {
                    if (onSuccess) { onSuccess(); }
                    window.location.reload();
                    return;
                }
                return response.text().then(function (text) { throw new Error(errorMessage(text)); });
            }).catch(function (error) {
                if (lifecycle.signal.aborted) { return; }
                submitting = false;
                alert(message + ' failed: ' + error.message);
            });
        }
        function commentEmpty() { return !(textarea && textarea.value || '').trim(); }

        function disposeContent(container) {
            container.querySelectorAll('.markdown-wrap').forEach(function (element) {
                if (element.viewer) { element.viewer.destroy(); }
            });
            container.querySelectorAll('.issueLink').forEach(function (element) {
                if (element._tippy) { element._tippy.destroy(); }
            });
            autosize.destroy(container.querySelectorAll('textarea'));
        }

        function initContent(container) {
            yona.initTasklist(container);
            yona.initSubComments(container);
            container.querySelectorAll('[data-request-method]').forEach(function (element) {
                $yona.requestAs(element);
            });
            container.querySelectorAll('.markdown-wrap').forEach(function (element) {
                if (!element.viewer) { new Viewer(element, {transition: false}); }
                element.querySelectorAll('img').forEach(function (image) { image.style.cursor = 'pointer'; });
                element.querySelectorAll('a[href]').forEach(function (link) {
                    var href = link.getAttribute('href');
                    if (/^[^./#]/.test(href) && href.indexOf(location.protocol + '//' + location.host) !== 0) {
                        link.target = '_blank';
                    }
                });
            });
            container.querySelectorAll('.issueLink').forEach(function (element) {
                if (!element._tippy) {
                    tippy(element, {content: decodeURI(element.getAttribute('href')), delay: [1000, 100],
                        placement: 'top-start', theme: 'light-border', maxWidth: 'none', arrow: false});
                }
            });
            autosize(container.querySelectorAll('textarea'));
        }

        initContent(root);
        // Tom Select 라이브러리를 로드하는 페이지(2단 보기 목록)에서만 초기화한다. 단독 상세 페이지는 원래
        // 라이브러리를 로드하지 않아 일반 <select>로 동작하며, 그 동작을 그대로 유지한다.
        root.querySelectorAll('[data-toggle="tomselect"]').forEach(function (element) {
            if (!element.tomselect && yona.ui && yona.ui.TomSelect) { yona.ui.TomSelect(element); }
        });

        own(yona.board.View({
            root: root,
            postId: data.postId,
            urls: {watch: '/watch', unwatch: '/unwatch', labels: data.labelsUrl}
        }));

        // 삭제 확인 성공 시 글 목록으로 이동. "load" 핸들러가 false를 반환하면 requestAs의 기본
        // 동작(reload)을 건너뛴다. requestAs는 엘리먼트당 한 번만 클릭 리스너를 붙이므로(위
        // initContent) 여기서 다시 호출해도 리스너가 중복되지 않는다.
        var deleteButton = root.querySelector('dialog#deleteConfirm [data-request-method="delete"]');
        if (deleteButton) {
            $yona.requestAs(deleteButton).on('load', function () {
                window.location = data.listUrl;
                return false;
            });
        }

        on(root, 'click', function (event) {
            var trigger = event.target.closest('a[href^="#"]');
            if (trigger) {
                var id = trigger.getAttribute('href').slice(1);
                var dialog = id && root.querySelector('#' + CSS.escape(id));
                if (dialog && dialog.tagName === 'DIALOG') {
                    event.preventDefault();
                    dialog.showModal();
                    return;
                }
            }
            var dismiss = event.target.closest('[data-dismiss="modal"]');
            if (dismiss) {
                var dismissDialog = dismiss.closest('dialog');
                if (dismissDialog) { event.preventDefault(); dismissDialog.close(); }
            }
            if (event.target.tagName === 'DIALOG') {
                var bounds = event.target.getBoundingClientRect();
                if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) {
                    event.target.close();
                }
            }
            // legacy yobi.Comment.js의 _toggleEditForm이 열기/닫기 양쪽에서 하던 부수 동작
            // (.add-a-comment 숨김 + autosize 재계산)을 재현한다.
            var edit = event.target.closest('[data-toggle="comment-edit"], .ybtn-cancel');
            if (edit) {
                var editing = edit.matches('[data-toggle="comment-edit"]');
                var commentId = edit.dataset.commentId;
                var body = root.querySelector('#comment-body-' + commentId);
                var editForm = root.querySelector('#comment-editform-' + commentId);
                if (body) { body.style.display = editing ? 'none' : 'block'; }
                if (editForm) {
                    editForm.style.display = editing ? 'block' : 'none';
                    // <yona-attachments>는 실제 textarea 참조를 명령형으로 주입받아야 붙여넣기/드래그/
                    // 링크 삽입이 그 textarea를 대상으로 동작한다 - 폼이 열리는 시점에 배선(멱등).
                    var attachments = editForm.querySelector('yona-attachments');
                    var editTextarea = editForm.querySelector('textarea[data-editor-mode="update-comment-body"]');
                    if (editing && attachments && editTextarea) { attachments.configure({textarea: editTextarea}); }
                }
                root.querySelectorAll('.add-a-comment').forEach(function (element) { element.style.display = 'none'; });
                autosize.update(root.querySelectorAll('textarea'));
            }
        });

        // 새 댓글/댓글 수정/대댓글은 모두 REST(POST/PUT .../comments)로 제출한다.
        on(root, 'submit', function (event) {
            var target = event.target;
            if (target === form) {
                event.preventDefault();
                if (commentEmpty()) {
                    $yona.notify(Messages('post.comment.empty'), 3000);
                    if (textarea) { textarea.focus(); }
                    return;
                }
                submitting = true;
                request(form.dataset.apiBase, {
                    method: 'POST', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({contents: textarea.value})
                }, Messages('button.comment.new'), function () { removeCurrentPageTemprarySavedContent(textarea); });
            } else if (target.matches('.comment-update-form form')) {
                event.preventDefault();
                var editTextarea = target.querySelector('textarea[data-editor-mode="update-comment-body"]');
                var notification = target.querySelector('.send-notification-checkbox');
                request(target.dataset.apiBase + '/' + target.dataset.commentId, {
                    method: 'PUT', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({contents: editTextarea ? editTextarea.value : undefined,
                        sendNotificationMail: !!(notification && notification.checked)})
                }, Messages('common.comment.edit'));
            } else if (target.matches('.child-comment-form')) {
                event.preventDefault();
                var childTextarea = target.querySelector('textarea[name="contents"]');
                request(target.dataset.apiBase, {
                    method: 'POST', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({contents: childTextarea ? childTextarea.value : undefined,
                        parentCommentId: target.dataset.parentCommentId})
                }, Messages('button.comment.new'));
            }
        });

        // yona.CommentForm.js의 기능(빈 값 제출 방지/Ctrl+Shift+Enter 단축 제출/beforeunload 이탈 경고/
        // localStorage 임시저장)을 이 파일이 직접 재구현한다.
        if (textarea) {
            own(temporarySaveHandler(textarea));
            on(document, 'keydown', function (event) {
                var isSubmitCombo = (event.ctrlKey || event.metaKey) && event.shiftKey && event.key === 'Enter';
                if (isSubmitCombo && document.activeElement !== form && form.contains(document.activeElement)) {
                    event.preventDefault();
                    // 아무도 preventDefault 하지 않으면 네이티브 제출로 폴백한다(submit 핸들러가 먼저 실행).
                    form.requestSubmit();
                }
            });
            on(window, 'beforeunload', function (event) {
                if (!commentEmpty() && !submitting) {
                    event.preventDefault();
                    event.returnValue = Messages('common.comment.beforeunload.confirm');
                }
            });
        }

        var elevator = yona.createScrollElevator({shape: 'rounded', glass: true});
        own(function () { if (elevator && elevator.destroy) { elevator.destroy(); } });

        root._disposeBoardDetail = function () {
            if (lifecycle.signal.aborted) { return; }
            lifecycle.abort();
            cleanups.reverse().forEach(function (cleanup) { cleanup(); });
            root.querySelectorAll('dialog[open]').forEach(function (dialog) { dialog.close(); });
            root.querySelectorAll('input, select').forEach(function (element) {
                if (element.tomselect) { element.tomselect.destroy(); }
            });
            disposeContent(root);
            delete root._disposeBoardDetail;
        };
        return root._disposeBoardDetail;
    };

    document.addEventListener('DOMContentLoaded', function () {
        yona.mountBoardDetail(document.getElementById('post-detail-content'));
    });
}());
