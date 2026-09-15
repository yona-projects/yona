document.addEventListener("DOMContentLoaded", function () {
    function delegate(container, eventType, selector, handler) {
        container.addEventListener(eventType, function (e) {
            var match = e.target.closest(selector);
            if (match && container.contains(match)) {
                handler.call(match, e);
            }
        });
    }

    function deleteAttachment(event) {
        var target = event.currentTarget;
        var parent = target.closest(".attached-file-marker");
        var id = target.dataset.id;
        var filename = parent.dataset.name;
        var url = parent.dataset.href;
        var mimeType = parent.dataset.mime;
        var linkStr = "[" + filename + "](" + url + ")";

        if (mimeType.startsWith("image")) {
            linkStr = "!" + linkStr;
        }

        var form = target.closest("form");
        var textarea = form.querySelector("textarea");
        var attachfiles = form.querySelector(".temporaryUploadFiles");

        attachfiles.value = attachfiles.value.split(",").filter(function (item) {
            return item != id;
        }).join(",");
        removeLinkFromTextarea(textarea, linkStr);

        // AttachmentController.deleteFile()은 POST + _method=delete 파라미터 계약이다
        // (yona.Files.js._deleteFile()이 이미 쓰는 것과 동일한 계약 — P3-50에서 실제 클릭으로
        // 검증하며 발견: 이 파라미터 없이 순수 $.post(url)만 보내면 400 Bad Request).
        fetch(url, { "method": "post", "body": new URLSearchParams({ "_method": "delete" }) })
            .then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                parent.remove();
            })
            .catch(function (data) {
                console.log(data);
            });
    }

    document.querySelectorAll(".attached-file-marker").forEach(function (marker) {
        delegate(marker, "click", ".btn-delete", deleteAttachment);
    });

    document.querySelectorAll(".file-upload__input").forEach(function (attachmentInput) {
        attachmentInput.addEventListener("change", function (e) {
            NProgress.start();

            var files = attachmentInput.files;
            var caretPos = getCaretPos(attachmentInput);
            var doneCount = 0;

            for (var i = 0; i < files.length; i++) {
                var formData = new FormData();
                formData.append("filePath", files[i]);

                fetch('/files', {
                    method: 'POST',
                    body: formData
                }).then(function(response){
                    if(!response.ok){
                        return response.text().then(function(text){
                            return Promise.reject(text);
                        });
                    }
                    return response.json();
                }).then(function (data) {
                    var parentForm = attachmentInput.closest("form");

                    buildTemporaryUploadedFileCards(parentForm, data);
                    caretPos = insertLinkIntoTextarea(parentForm.querySelector("textarea"), data, caretPos);
                    doneCount++;
                    if (doneCount === files.length) {
                        NProgress.done();
                    }
                }).catch(function (data) {
                    $yona.notify(data);
                });
            }
        });
    });

    var rememberBorder = "";
    document.querySelectorAll(".textarea-box").forEach(function (textareaBox) {
        delegate(textareaBox, "dragenter", "textarea", function (e) {
            e.stopPropagation();
            e.preventDefault();
            rememberBorder = this.style.border;
            this.style.border = "1px dashed orange";
        });
        delegate(textareaBox, "dragover", "textarea", function (e) {
            e.stopPropagation();
            e.preventDefault();
        });
        delegate(textareaBox, "drop", "textarea", function (e) {
            e.stopPropagation();
            e.preventDefault();

            var dt = e.dataTransfer;
            var files = dt.files;

            this.style.border = rememberBorder;

            var attachmentInput = this.closest("form").querySelector(".file-upload__input");
            attachmentInput.files = files;
            attachmentInput.dispatchEvent(new Event("change"));
        });
        delegate(textareaBox, "dragleave", "textarea", function (e) {
            this.style.border = rememberBorder;
        });
        delegate(textareaBox, "paste", "textarea", function (event) {
            var items = event.clipboardData.items;
            var attachmentInput = this.closest("form").querySelector(".file-upload__input");
            var caretPos = getCaretPos(attachmentInput);

            for (var index in items) {
                var item = items[index];

                if (item.kind === 'file' && item.type.indexOf("image") === 0) {
                    NProgress.start();
                    var formData = new FormData();
                    formData.append('filePath', item.getAsFile(), generateFileName());

                    fetch('/files', {
                        method: 'POST',
                        body: formData
                    }).then(function(response){
                        if(!response.ok){
                            return response.text().then(function(text){
                                return Promise.reject(text);
                            });
                        }
                        return response.json();
                    }).then(function (data) {
                        var parentForm = attachmentInput.closest("form");

                        buildTemporaryUploadedFileCards(parentForm, data);
                        caretPos = insertLinkIntoTextarea(parentForm.querySelector("textarea"), data, caretPos);
                        NProgress.done();
                    }).catch(function (data) {
                        $yona.notify(data);
                    });

                }
            }
        });
    });

    function getCaretPos(attachmentInput) {
        return attachmentInput.closest("form").querySelector("textarea").selectionStart;
    }

    function buildTemporaryUploadedFileCards(parentForm, data) {
        var attachmentFileListArea = parentForm.querySelector(".attachment-files");

        setTemporaryUploadFileIds(parentForm.querySelector(".temporaryUploadFiles"), data.id);
        attachmentFileListArea.insertAdjacentHTML("beforeend", getAttachmentCard(data));
        delegate(attachmentFileListArea, "click", ".btn-delete", deleteAttachment);
    }

    function getAttachmentCard(data) {
        return '<div class="attached-file attached-file-marker" data-mime="' +
            data.mimeType.trim() + '" data-name="' + data.name + '" data-href="' + data.url + '">\n' +
            '<strong class="name">' + data.name + '</strong>\n' +
            '<span class="size">' + humanize.filesize(data.size) + '</span>\n' +
            '<button type="button" class="btn-transparent btn-delete" data-id="' + data.id + '">&times;</button>\n' +
            '</div>';
    }

    function setTemporaryUploadFileIds(attachmentFiles, fileId) {
        if (attachmentFiles.value === "") {
            attachmentFiles.value = fileId;
        } else {
            var splitIds = attachmentFiles.value.split(",");
            if (!splitIds.includes(fileId)) {
                attachmentFiles.value = splitIds.concat(fileId).join(",");
            }
        }
    }

    function generateFileName() {
        var now = new Date();
        return  now.getSeconds() + "" + now.getMilliseconds() + '-' + now.getFullYear() + '-' + (now.getMonth() + 1)
            + '-' + now.getDate() + '-' + now.getHours() + '-' + now.getMinutes() + ".png";
    }

    // P3-50: 이 댓글 수정 폼의 textarea는 <yona-markdown-editor>(CodeMirror)가 감싸둔
    // 상태다 — CodeMirror -> textarea 단방향 동기화만 있어 textarea.value = ...로 직접
    // 쓰는 값은 CodeMirror가 인지하지 못해 실제 제출 내용에는 반영되지 않는다(Playwright로
    // 실제 재현). raw textarea 조작 결과를 계산한 뒤 그 최종 문자열을 CodeMirror 쪽에도
    // 강제로 반영한다 — 클릭/제출 등 호출 시점에 따라 CodeMirror API 분기 자체가 항상
    // 타지 못하는 경우가 있고(Playwright로 실제 재현: 카드 클릭 직후엔 raw textarea에 정상
    // 반영되지만 이후 포커스가 빠지며 CodeMirror가 자신의 변경 없는 내부 버퍼로 되돌려써
    // 사라짐), raw textarea를 항상 최종 소스오브트루스로 강제 동기화하면 호출 경로와
    // 무관하게 결과가 일관된다(새 댓글 폼의 동일 문제 - yona.Attachments.js - 와 같은 방식).
    //
    // 6단계(jQuery 완전 제거): lib/yona-markdown-editor(수정 금지 대상)가 예전엔
    // `window.jQuery(textarea).data(...)`로 노출하던 것을, 이제 커스텀 엘리먼트
    // 자신의 네이티브 `value` getter/setter로 노출한다 - `textarea.closest(
    // 'yona-markdown-editor')`로 직접 찾아 jQuery 없이 바로 접근한다. yona.Attachments.js와
    // 동일한 이유로 <yona-markdown-editor-vue>(components/vue-widgets)도 셀렉터에 추가 -
    // 원본만 쓰는 화면에서는 동작이 전혀 바뀌지 않는다.
    function syncMarkdownEditor(textarea) {
        var elEditor = textarea ? textarea.closest("yona-markdown-editor, yona-markdown-editor-vue") : null;
        if (elEditor) {
            elEditor.value = textarea.value;
        }
    }

    function insertLinkIntoTextarea(textarea, data, caretPos) {
        var textAreaTxt = textarea.value;
        var txtToAdd = "[" + data.name + "](" + data.url + ")";
        if (data.mimeType.startsWith("image")) {
            txtToAdd = "!" + txtToAdd;
        }

        txtToAdd = " " + txtToAdd;

        if (textAreaTxt.length > 0 && caretPos === 0) {
            caretPos = textAreaTxt.length;
        }

        textarea.value = textAreaTxt.substring(0, caretPos) + txtToAdd + textAreaTxt.substring(caretPos);
        syncMarkdownEditor(textarea);

        return caretPos + txtToAdd.length;
    }

    function removeLinkFromTextarea(textarea, linkStr) {
        textarea.value = textarea.value.split(linkStr).join("");
        syncMarkdownEditor(textarea);
    }
});
