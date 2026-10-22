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
        $textarea.val($textarea.val().split(linkStr).join(""));

        $.post(url)
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

        return caretPos + txtToAdd.length;
    }
});
