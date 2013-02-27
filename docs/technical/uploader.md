This document describes how to use Uploader and Downloader.

Uploader
--------

This allows users to upload files and attach them to a specific resource(issue,
post, comment, ...).

#### Usage

First of all, uploader module should be loaded.

    <script src="@getJSLink("uploader")" type="text/javascript"></script>

Call fileUploader javascript function to activate Uploader. The function
requires two three arguments -- a HTML element object wrapped by jQuery in
which upload form will be rendered, textarea in which a link to a uploaded file
will be added, and the url to files.

You can get the url using "@routes.AttachmentApp.newFile" or
"@routes.AttachmentApp.getFileList".

You have to specify `resourceType` and `resourceId` as attributes of the HTML
element, because they are required to upload files to the specific resource.

e.g.

    <textarea id="comment-editor" name="contents" class="span8 textbody .inputxx-large" rows="5" markdown></textarea>
    <div id="upload" resourceType=@Resource.ISSUE_POST resourceId=@issue.id></div>
    <script>fileUploader($('#upload'), $('#comment-editor'), filesUrl);</script>

However, you don't need to specify `resourceId` if the resource is not created yet,
for instance, while you try to post new issue.

e.g.

    <textarea id="comment-editor" name="contents" class="span8 textbody .inputxx-large" rows="5" markdown></textarea>
    <div id="upload" resourceType=@Resource.ISSUE_POST></div>
    <script>fileUploader($('#upload'), $('#comment-editor'), filesUrl);</script>

#### Server-side implementation

You need some server-side implementation to attach uploaded files to a specific resource or get files attached to a specific resource.

If a user upload files, they are stored in the user's temporary area. `Attachment.attachFiles` attaches them to a specific resource. The files which have been attached are removed in the temporary area.

e.g. You should put this code on an event handler called update or create a issue to attach uploaded files to the issue.

    Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.ISSUE_POST, issueId);

You can delete all files attached to a specific resource using `Attachment.attachFiles` method.

e.g. You may put this code on an event handler called after deleting a comment to delete all files attached to the comment.

    Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.ISSUE_COMMENT, commentId);

Downloader
----------

This list files attached to specific resource(issue, post, comment, ...), and
allow users to download them.

#### Usage

First of all, uploader module should be loaded.

    <script src="@getJSLink("uploader")" type="text/javascript"></script>

Call fileDownloader javascript function to activate Downloader. The function
requires two two arguments -- a HTML element object wrapped by jQuery in which
a list of files will be rendered, and the url to files.

You can get the url using "@routes.AttachmentApp.newFile" or
"@routes.AttachmentApp.getFileList".

You have to specify `resourceType` and `resourceId` as attributes of the HTML
element, because they are required to get the files only attached to the
specific resource.

e.g.

    <div class="attachments" resourceType=@Resource.BOARD_POST resourceId=@post.id></div>
    <script>
    attachments = $('.attachments');
    for (var i = 0; i < attachments.length; i++) {
      fileDownloader($(attachments[i]), "@routes.AttachmentApp.newFile");
    }
    </script>
