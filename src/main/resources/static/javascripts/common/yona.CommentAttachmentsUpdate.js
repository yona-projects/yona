$(function () {
    function deleteAttachment() {
        var $this = $(this);
        var $parent = $this.parent(".attached-file-marker");
        var id = $this.data("id");
        var filename = $parent.data("name");
        var url = $parent.data("href");
        var mimeType = $parent.data("mime");
        var linkStr = "[" + filename + "](" + url + ")";

        if (mimeType.startsWith("image")) {
            linkStr = "!" + linkStr;
        }

        var $form = $this.parent().closest("form");
        var $textarea = $form.find("textarea");
        var $attachfiles = $form.find(".temporaryUploadFiles");

        $attachfiles.val($attachfiles.val().split(",").filter(function (item) {
            return item != id;
        }).join(","));
        removeLinkFromTextarea($textarea, linkStr);

        // AttachmentController.deleteFile()은 POST + _method=delete 파라미터 계약이다
        // (yobi.Files.js._deleteFile()이 이미 쓰는 것과 동일한 계약 — P3-50에서 실제 클릭으로
        // 검증하며 발견: 이 파라미터 없이 순수 $.post(url)만 보내면 400 Bad Request).
        $.post(url, { "_method": "delete" })
            .done(function (data) {
                $parent.remove();
            })
            .fail(function (data) {
                console.log(data);
            });
    }

    $(".attached-file-marker").on("click", ".btn-delete", deleteAttachment);
    $(".file-upload__input").on("change", function (e) {
        NProgress.start();

        var $attachmentInput = $(this);
        var files = $attachmentInput[0].files;
        var caretPos = getCaretPos($attachmentInput);
        var doneCount = 0;

        for (var i = 0; i < files.length; i++) {
            var formData = new FormData();
            formData.append("filePath", files[i]);

            $.ajax({
                url: '/files',
                type: 'POST',
                cache: false,
                contentType: false,
                processData: false,
                data: formData
            }).done(function (data) {
                var $parentForm = $attachmentInput.parent().closest("form");

                buildTemporaryUploadedFileCards($parentForm, data);
                caretPos = insertLinkIntoTextarea($parentForm.find("textarea"), data, caretPos);
                doneCount++;
                if (doneCount === files.length) {
                    NProgress.done();
                }
            }).fail(function (data) {
                $yobi.notify(data);
            });
        }
    });

    var rememberBorder = "";
    $(".textarea-box")
        .on("dragenter", "textarea", function (e) {
            e.stopPropagation();
            e.preventDefault();
            rememberBorder = $(this).css("border");
            $(this).css("border", "1px dashed orange");
        })
        .on("dragover", "textarea", function (e) {
            e.stopPropagation();
            e.preventDefault();
        })
        .on("drop", "textarea", function (e) {
            e.stopPropagation();
            e.preventDefault();

            var dt = e.originalEvent.dataTransfer;
            var files = dt.files;

            $(this).css("border", rememberBorder);

            var attachmentInput = $(this).parent().closest("form").find(".file-upload__input");
            attachmentInput[0].files = files;
            attachmentInput.trigger("change");
        })
        .on("dragleave", "textarea", function (e) {
            $(this).css("border", rememberBorder);
        })
        .on("paste", "textarea", function (event) {
            var items = (event.clipboardData || event.originalEvent.clipboardData).items;
            var $attachmentInput = $(this).parent().closest("form").find(".file-upload__input");
            var caretPos = getCaretPos($attachmentInput);

            for (var index in items) {
                var item = items[index];

                if (item.kind === 'file' && item.type.indexOf("image") === 0) {
                    NProgress.start();
                    var formData = new FormData();
                    formData.append('filePath', item.getAsFile(), generateFileName());

                    $.ajax('/files', {
                        type: 'POST',
                        contentType: false,
                        processData: false,
                        data: formData
                    }).done(function (data) {
                        var $parentForm = $attachmentInput.parent().closest("form");

                        buildTemporaryUploadedFileCards($parentForm, data);
                        caretPos = insertLinkIntoTextarea($parentForm.find("textarea"), data, caretPos);
                        NProgress.done();
                    }).fail(function (data) {
                        $yobi.notify(data);
                    });

                }
            }
        });

    function getCaretPos($attachmentInput) {
        return $attachmentInput.parent().closest("form").find("textarea")[0].selectionStart;
    }

    function buildTemporaryUploadedFileCards($parentForm, data) {
        var attachmentFileListArea = $parentForm.find(".attachment-files");

        setTemporaryUploadFileIds($parentForm.find(".temporaryUploadFiles"), data.id);
        attachmentFileListArea.append(getAttachmentCard(data))
            .on("click", ".btn-delete", deleteAttachment);
    }

    function getAttachmentCard(data) {
        return '<div class="attached-file attached-file-marker" data-mime="' +
            data.mimeType.trim() + '" data-name="' + data.name + '" data-href="' + data.url + '">\n' +
            '<strong class="name">' + data.name + '</strong>\n' +
            '<span class="size">' + humanize.filesize(data.size) + '</span>\n' +
            '<button type="button" class="btn-transparent btn-delete" data-id="' + data.id + '">&times;</button>\n' +
            '</div>';
    }

    function setTemporaryUploadFileIds($attachmentFiles, fileId) {
        if ($attachmentFiles.val() === "") {
            $attachmentFiles.val(fileId);
        } else {
            var splitIds = $attachmentFiles.val().split(",");
            if (!splitIds.includes(fileId)) {
                $attachmentFiles.val(splitIds.concat(fileId).join(","));
            }
        }
    }

    function generateFileName() {
        var now = new Date();
        return  now.getSeconds() + "" + now.getMilliseconds() + '-' + now.getFullYear() + '-' + (now.getMonth() + 1)
            + '-' + now.getDate() + '-' + now.getHours() + '-' + now.getMinutes() + ".png";
    }

    // P3-50: 이 댓글 수정 폼의 textarea는 site/layout.html::markdownEditor가 EasyMDE(CodeMirror)로
    // 감싸둔 상태다 — CodeMirror -> textarea 단방향 동기화만 있어(yobi.ui.MarkdownEditor.js의
    // codemirror.on("change", ...) 참고) $textarea.val(...)로 직접 쓰는 값은 CodeMirror가
    // 인지하지 못해 실제 제출 내용에는 반영되지 않는다(Playwright로 실제 재현). 새 댓글 폼의
    // 동일 문제(yobi.Attachments.js)와 같은 방식으로, EasyMDE 인스턴스가 있으면 CodeMirror
    // 공식 API로 삽입/삭제하고 없으면 기존 raw textarea 조작으로 폴백한다.
    // P3-50: raw textarea 조작 결과를 EasyMDE에도 강제로 반영한다. CodeMirror API
    // (replaceRange/setValue)로 곧장 분기하는 대신 "먼저 raw 로직으로 최종 문자열을 계산 ->
    // 그 문자열을 easyMDE.value()로 밀어넣기" 방식을 쓴다 — 클릭/제출 등 호출 시점에 따라
    // CodeMirror API 분기 자체가 항상 타지 못하는 경우가 있고(Playwright로 실제 재현: 카드
    // 클릭 직후엔 raw textarea에 정상 반영되지만 이후 포커스가 빠지며 CodeMirror가 자신의
    // 변경 없는 내부 버퍼로 되돌려써 사라짐), raw textarea를 항상 최종 소스오브트루스로 강제
    // 동기화하면 호출 경로와 무관하게 결과가 일관된다.
    function syncEasyMDE($textarea) {
        var easyMDE = $textarea.length ? $textarea.data("easymde") : null;
        if (easyMDE) {
            easyMDE.value($textarea.val());
        }
    }

    function insertLinkIntoTextarea($textarea, data, caretPos) {
        var textAreaTxt = $textarea.val();
        var txtToAdd = "[" + data.name + "](" + data.url + ")";
        if (data.mimeType.startsWith("image")) {
            txtToAdd = "!" + txtToAdd;
        }

        txtToAdd = " " + txtToAdd;

        if (textAreaTxt.length > 0 && caretPos === 0) {
            caretPos = textAreaTxt.length;
        }

        $textarea.val(textAreaTxt.substring(0, caretPos) + txtToAdd + textAreaTxt.substring(caretPos));
        syncEasyMDE($textarea);

        return caretPos + txtToAdd.length;
    }

    function removeLinkFromTextarea($textarea, linkStr) {
        $textarea.val($textarea.val().split(linkStr).join(""));
        syncEasyMDE($textarea);
    }
});
