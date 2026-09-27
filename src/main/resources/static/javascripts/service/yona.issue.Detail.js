(function () {
    'use strict';

    window.yona = window.yona || {};
    yona.mountIssueDetail = function (root) {
        if (!root) { return function () {}; }
        if (root._disposeIssueDetail) { return root._disposeIssueDetail; }
        var lifecycle = new AbortController();
        var cleanups = [];
        var data = root.dataset;
        var form = root.querySelector('#comment-form');
        var textarea = form && form.querySelector('textarea[name="contents"]');
        var submitting = false;
        var pendingDelete;

        function own(cleanup) {
            if (typeof cleanup === 'function') { cleanups.push(cleanup); }
        }
        function on(target, type, listener) {
            if (target) { target.addEventListener(type, listener, {signal: lifecycle.signal}); }
        }
        function errorMessage(text) {
            try { return JSON.parse(text).message || text; } catch (ignore) { return text; }
        }
        function request(url, options, success, message) {
            fetch(url, Object.assign({}, options, {signal: lifecycle.signal})).then(function (response) {
                if (lifecycle.signal.aborted) { return; }
                if (response.ok) { success(response); return; }
                return response.text().then(function (text) { throw new Error(errorMessage(text)); });
            }).catch(function (error) {
                if (lifecycle.signal.aborted) { return; }
                submitting = false;
                alert(message + ' failed: ' + error.message);
            });
        }
        function postComment(target, contents, parentCommentId) {
            request(target.dataset.apiBase, {
                method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({contents: contents, parentCommentId: parentCommentId})
            }, function () {
                if (target === form) { removeCurrentPageTemprarySavedContent(textarea); }
                window.location.reload();
            }, Messages('button.comment.new'));
        }
        function emptyComment() { return !(textarea && textarea.value || '').trim(); }
        function submitComment() {
            if (form) { form.requestSubmit(); }
        }

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
            $yona.initHoverPopovers('#issue-detail-content [data-toggle="popover"]');
        }

        initContent(root);
        root.querySelectorAll('[data-toggle="tomselect"]').forEach(function (element) {
            if (!element.tomselect) { yona.ui.TomSelect(element); }
        });
        root.querySelectorAll('[data-toggle="calendar"]').forEach(function (element) {
            if (element._flatpickr) { element._detailCalendarLegacyBound = true; }
            if (!element._flatpickr) {
                flatpickr(element, {dateFormat: 'Y-m-d', allowInput: true});
            }
            if (!element._detailCalendarLegacyBound) {
                on(element.nextElementSibling, 'click', function () { element._flatpickr.open(); });
            }
        });
        own(yona.issue.View({root: root, issueId: data.issueId, nextState: data.nextState,
            urls: {
                watch: '/watch?' + new URLSearchParams({'resource.type': 'ISSUE_POST', 'resource.id': data.issueId}),
                unwatch: '/unwatch?' + new URLSearchParams({'resource.type': 'ISSUE_POST', 'resource.id': data.issueId}),
                timeline: data.timelineUrl, massUpdate: data.massUpdateUrl
            }, onTimelineLoad: initContent, beforeTimelineReplace: disposeContent}));
        if (root.querySelector('#assignee')) {
            own(yonaAssgineeModule(data.assignableUrl, data.assigneesUrl, Messages('issue.assignee'), root));
        }
        if (root.querySelector('#issueSharer')) {
            own(yonaIssueSharerModule(data.findSharerUrl, data.sharableUrl, data.shareUrl, Messages('issue.sharer'), root));
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
            var deleting = event.target.closest('[data-toggle="comment-delete"]');
            if (deleting) {
                event.preventDefault();
                pendingDelete = deleting;
                root.querySelector('#comment-delete-modal').showModal();
            }
            if (event.target.closest('#comment-delete-confirm') && pendingDelete) {
                var comment = pendingDelete.closest('li.comment, div.comment, [id^="comment-"]');
                request(pendingDelete.dataset.requestUri, {method: 'DELETE'}, function () {
                    if (comment) { comment.remove(); }
                    root.querySelector('#comment-delete-modal').close();
                }, Messages('common.comment.delete'));
            }
            var deleteIssue = event.target.closest('[data-issue-delete]');
            if (deleteIssue) {
                request(deleteIssue.dataset.requestUri, {method: 'DELETE'}, function () {
                    window.location.href = data.listUrl;
                }, Messages('issue.delete'));
            }
            var edit = event.target.closest('[data-toggle="comment-edit"], .ybtn-cancel');
            if (edit) {
                var editing = edit.matches('[data-toggle="comment-edit"]');
                var commentId = edit.dataset.commentId;
                var body = root.querySelector('#comment-body-' + commentId);
                var editForm = root.querySelector('#comment-editform-' + commentId);
                if (body) { body.style.display = editing ? 'none' : 'block'; }
                if (editForm) {
                    editForm.style.display = editing ? 'block' : 'none';
                    var attachments = editForm.querySelector('yona-attachments');
                    var editTextarea = editForm.querySelector('textarea[data-editor-mode="update-comment-body"]');
                    if (editing && attachments && editTextarea) { attachments.configure({textarea: editTextarea}); }
                }
                root.querySelectorAll('.add-a-comment').forEach(function (element) { element.style.display = 'none'; });
                autosize.update(root.querySelectorAll('textarea'));
            }
            if (event.target.closest('#issue-share-button')) {
                root.querySelectorAll('.sharer-list, #sharer-list').forEach(function (element) { element.classList.remove('hide'); });
                var sharer = root.querySelector('#issueSharer');
                if (sharer && sharer.tomselect) { sharer.tomselect.focus(); }
            }
        });

        on(root, 'submit', function (event) {
            var target = event.target;
            if (target === form) {
                event.preventDefault();
                if (emptyComment()) {
                    $yona.notify(Messages('post.comment.empty'), 3000);
                    if (textarea) { textarea.focus(); }
                    return;
                }
                submitting = true;
                postComment(form, textarea.value);
            } else if (target.matches('.comment-update-form form')) {
                event.preventDefault();
                var editTextarea = target.querySelector('textarea[data-editor-mode="update-comment-body"]');
                var notification = target.querySelector('.send-notification-checkbox');
                request(target.dataset.apiBase + '/' + target.dataset.commentId, {
                    method: 'PUT', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({contents: editTextarea ? editTextarea.value : undefined,
                        sendNotificationMail: !!(notification && notification.checked)})
                }, function () { window.location.reload(); }, Messages('common.comment.edit'));
            } else if (target.matches('.child-comment-form')) {
                event.preventDefault();
                var childTextarea = target.querySelector('textarea[name="contents"]');
                postComment(target, childTextarea ? childTextarea.value : undefined, target.dataset.parentCommentId);
            }
        });
        if (textarea) {
            if (localStorage.getItem(yonaDraftKey(textarea)) !== null) {
                var clearDraft = form.querySelector('.editor-clear-temporary');
                if (clearDraft) { clearDraft.style.display = 'block'; }
            }
            own(temporarySaveHandler(textarea));
            if (form.dataset.notiReceiversUrl) {
                own(findNotiReceiversHandler(textarea, form.dataset.notiReceiversUrl));
            }
            on(document, 'keydown', function (event) {
                if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === 'Enter' && form.contains(document.activeElement)) {
                    event.preventDefault();
                    submitComment();
                }
            });
            on(window, 'beforeunload', function (event) {
                if (!emptyComment() && !submitting) {
                    event.preventDefault();
                    event.returnValue = Messages('common.comment.beforeunload.confirm');
                }
            });
        }
        own(detectPageChange(data.detectChangeUrl));
        var elevator = yona.createScrollElevator({shape: 'rounded', tooltips: true});
        own(function () { elevator.destroy(); });

        root._disposeIssueDetail = function () {
            if (lifecycle.signal.aborted) { return; }
            lifecycle.abort();
            cleanups.reverse().forEach(function (cleanup) { cleanup(); });
            root.querySelectorAll('dialog[open]').forEach(function (dialog) { dialog.close(); });
            root.querySelectorAll('input, select').forEach(function (element) {
                if (element.tomselect) { element.tomselect.destroy(); }
                if (element._flatpickr) { element._flatpickr.destroy(); }
            });
            disposeContent(root);
            delete root._disposeIssueDetail;
        };
        return root._disposeIssueDetail;
    };

    document.addEventListener('DOMContentLoaded', function () {
        yona.mountIssueDetail(document.getElementById('issue-detail-content'));
    });
}());
