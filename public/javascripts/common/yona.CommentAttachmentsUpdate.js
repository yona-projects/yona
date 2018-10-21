$(function(){
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

        $attachfiles.val($attachfiles.val().split(",").filter(function(item){
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

    $(".attached-file-marker").on("click", ".btn-delete", deleteAttachment);
    $(".file-upload__input").on("change", function (e) {
        var $this = $(this);
        var files = $this[0].files;

        NProgress.start();
        var doneCount = 0;

        var caretPos = $this.parent().closest("form").find("textarea")[0].selectionStart;
        for (var i = 0; i < files.length; i++) {
            var file = files[i];
            var formData = new FormData();

            formData.append("filePath", file);

            $.ajax({
                url: '/files',
                type: 'POST',
                cache: false,
                contentType: false,
                processData: false,
                data: formData
            }).done(function (data) {
                var $parentForm = $this.parent().closest("form");
                var $attachfiles = $parentForm.find(".temporaryUploadFiles");
                var $textarea = $parentForm.find("textarea");

                if (doneCount === 0) {
                    $attachfiles.val(data.id);
                } else {
                    $attachfiles.val($attachfiles.val() + "," + data.id);
                }
                var attachment = '<div class="attached-file attached-file-marker" data-mime="' +
                    data.mimeType.trim() + '" data-name="' + data.name + '" data-href="' + data.url + '">\n' +
                    '<strong class="name">' + data.name + '</strong>\n' +
                    '<span class="size">' + humanize.filesize(data.size) + '</span>\n' +
                    '<button type="button" class="btn-transparent btn-delete" data-id="' + data.id + '">&times;</button>\n' +
                    '</div>';
                $parentForm.find(".attachment-files").append(attachment);
                $parentForm.find(".attachment-files").on("click", ".btn-delete", deleteAttachment);

                caretPos = insertLinkIntoTextarea($textarea, data, caretPos);

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
        .on("dragenter", "textarea", function(e){
            e.stopPropagation();
            e.preventDefault();
            rememberBorder = $(this).css("border");
            $(this).css("border", "1px dashed orange");
        })
        .on("dragover", "textarea", function(e){
            e.stopPropagation();
            e.preventDefault();
        })
        .on("drop", "textarea", function(e){
            e.stopPropagation();
            e.preventDefault();

            var dt = e.originalEvent.dataTransfer;
            var files = dt.files;

            console.log(files);

            $(this).css("border", rememberBorder);
            $(this).parent().closest("form").find(".file-upload__input")[0].files = files;
        })
        .on("dragleave", "textarea", function(e){
            $(this).css("border", rememberBorder);
        });
});
