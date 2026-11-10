

// ES 모듈은 문서 분석이 끝난 뒤 DOMContentLoaded 전에 실행되며, 아래 준비 완료 처리기는 그때 등록되어 문서가 준비되면 돈다.
$(() => {
    async function deleteAttachment() {
        const $this = $(this);
        const $parent = $this.parent(".attached-file-marker");
        const id = $this.data("id");
        const filename = $parent.data("name");
        const url = $parent.data("href");
        const mimeType = $parent.data("mime");
        let linkStr = "[" + filename + "](" + url + ")";

        if (mimeType.startsWith("image")) {
            linkStr = "!" + linkStr;
        }

        const $form = $this.parent().closest("form");
        const $textarea = $form.find("textarea");
        const $attachfiles = $form.find(".temporaryUploadFiles");

        $attachfiles.val($attachfiles.val().split(",").filter((item) => {
            return item != id;
        }).join(","));
        $textarea.val($textarea.val().split(linkStr).join(""));

        try {
            await yona.http.post(url);
        } catch (data) {
            console.log(data);
            return;
        }
        $parent.remove();
    }

    $(".attached-file-marker").on("click", ".btn-delete", deleteAttachment);
    $(".file-upload__input").on("change", function (e) {
        NProgress.start();

        const $attachmentInput = $(this);
        const files = $attachmentInput[0].files;
        let caretPos = getCaretPos($attachmentInput);
        let doneCount = 0;

        for (let i = 0; i < files.length; i++) {
            const formData = new FormData();
            formData.append("filePath", files[i]);

            // 파일마다 요청이 동시에 나가고 끝난 순서대로 처리되던 동작을 지키려고, 반복문 안에서 await하지 않고 파일마다 따로 실행한다
            (async () => {
                let data;
                try {
                    data = await yona.http.ajax({
                        url: '/files',
                        type: 'POST',
                        cache: false,
                        contentType: false,
                        processData: false,
                        data: formData
                    });
                } catch (failure) {
                    $yona.notify(failure);
                    return;
                }
                const $parentForm = $attachmentInput.parent().closest("form");

                buildTemporaryUploadedFileCards($parentForm, data);
                caretPos = insertLinkIntoTextarea($parentForm.find("textarea"), data, caretPos);
                doneCount++;
                if (doneCount === files.length) {
                    NProgress.done();
                }
            })();
        }
    });

    let rememberBorder = "";
    $(".textarea-box")
        .on("dragenter", "textarea", function (e) {
            e.stopPropagation();
            e.preventDefault();
            rememberBorder = $(this).css("border");
            $(this).css("border", "1px dashed orange");
        })
        .on("dragover", "textarea", (e) => {
            e.stopPropagation();
            e.preventDefault();
        })
        .on("drop", "textarea", function (e) {
            e.stopPropagation();
            e.preventDefault();

            const dt = e.originalEvent.dataTransfer;
            const files = dt.files;

            $(this).css("border", rememberBorder);

            const attachmentInput = $(this).parent().closest("form").find(".file-upload__input");
            attachmentInput[0].files = files;
            attachmentInput.trigger("change");
        })
        .on("dragleave", "textarea", function (e) {
            $(this).css("border", rememberBorder);
        })
        .on("paste", "textarea", function (event) {
            const items = (event.clipboardData || event.originalEvent.clipboardData).items;
            const $attachmentInput = $(this).parent().closest("form").find(".file-upload__input");
            let caretPos = getCaretPos($attachmentInput);

            for (const index in items) {
                const item = items[index];

                if (item.kind === 'file' && item.type.indexOf("image") === 0) {
                    NProgress.start();
                    const formData = new FormData();
                    formData.append('filePath', item.getAsFile(), generateFileName());

                    // 붙여넣은 이미지마다 요청이 동시에 나가던 동작을 지키려고 반복문 안에서 await하지 않는다
                    (async () => {
                        let data;
                        try {
                            data = await yona.http.ajax('/files', {
                                type: 'POST',
                                contentType: false,
                                processData: false,
                                data: formData
                            });
                        } catch (failure) {
                            $yona.notify(failure);
                            return;
                        }
                        const $parentForm = $attachmentInput.parent().closest("form");

                        buildTemporaryUploadedFileCards($parentForm, data);
                        caretPos = insertLinkIntoTextarea($parentForm.find("textarea"), data, caretPos);
                        NProgress.done();
                    })();

                }
            }
        });

    function getCaretPos($attachmentInput) {
        return $attachmentInput.parent().closest("form").find("textarea")[0].selectionStart;
    }

    function buildTemporaryUploadedFileCards($parentForm, data) {
        const attachmentFileListArea = $parentForm.find(".attachment-files");

        setTemporaryUploadFileIds($parentForm.find(".temporaryUploadFiles"), data.id);
        // v1.6은 업로드마다 삭제 핸들러를 더해 삭제 요청이 거듭 나갔다(C007). 떼고 다시 단다
        attachmentFileListArea.append(getAttachmentCard(data))
            .off("click", ".btn-delete", deleteAttachment)
            .on("click", ".btn-delete", deleteAttachment);
    }

    function getAttachmentCard(data) {
        return '<div class="attached-file attached-file-marker" data-mime="' +
            data.mimeType.trim() + '" data-name="' + data.name + '" data-href="' + data.url + '">\n' +
            '<strong class="name">' + data.name + '</strong>\n' +
            '<span class="size">' + $yona.formatFileSize(data.size) + '</span>\n' +
            '<button type="button" class="btn-transparent btn-delete" data-id="' + data.id + '">&times;</button>\n' +
            '</div>';
    }

    function setTemporaryUploadFileIds($attachmentFiles, fileId) {
        if ($attachmentFiles.val() === "") {
            $attachmentFiles.val(fileId);
        } else {
            const splitIds = $attachmentFiles.val().split(",");
            // v1.6은 문자열 배열에 숫자 아이디를 찾아 항상 거짓이었다(C008)
            if (!splitIds.includes(String(fileId))) {
                $attachmentFiles.val(splitIds.concat(fileId).join(","));
            }
        }
    }

    function generateFileName() {
        const now = new Date();
        return  now.getSeconds() + "" + now.getMilliseconds() + '-' + now.getFullYear() + '-' + (now.getMonth() + 1)
            + '-' + now.getDate() + '-' + now.getHours() + '-' + now.getMinutes() + ".png";
    }

    function insertLinkIntoTextarea($textarea, data, caretPos) {
        const textAreaTxt = $textarea.val();
        let txtToAdd = "[" + data.name + "](" + data.url + ")";
        if (data.mimeType.startsWith("image")) {
            txtToAdd = "!" + txtToAdd;
        }

        txtToAdd = " " + txtToAdd;

        if (textAreaTxt.length > 0 && caretPos === 0) {
            caretPos = textAreaTxt.length;
        }

        $textarea.val(textAreaTxt.substring(0, caretPos) + txtToAdd + textAreaTxt.substring(caretPos));

        return caretPos + txtToAdd.length;
    }
});
