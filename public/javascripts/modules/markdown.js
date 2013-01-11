nforge.namespace('markdown');

var renderMarkdown = function(text) {
  text = text
    .replace(/```(\w+)(?:\r\n|\r|\n)((\r|\n|.)*?)(\r|\n)```/gm, function(match, p1, p2) {
    try {
      return '<pre><code class="' + p1 + '">' + hljs(p2, p1).value + '</code></pre>';
    } catch (e) {
      return '<pre><code>' + hljs(p2).value + '</code></pre>';
    }
  });

  return new Showdown.converter().makeHtml(text);
};

var getFileList = function(target, urlToGetFileList, fn) {
  var form = $('<form>')
    .attr('method', 'get')
    .attr('action', urlToGetFileList);
  resourceType = target.attr('resourceType');
  resourceId = target.attr('resourceId');
  if (resourceType !== undefined) {
    form.append('<input type="hidden" name="containerType" value="' + resourceType + '">');
  }
  if (resourceId !== undefined) {
    form.append('<input type="hidden" name="containerId" value="' + resourceId + '">');
  }
  form.ajaxForm({ success: fn });
  form.submit();
};

var editor = function (textarea) {
  var previewDiv, previewSwitch;

  previewDiv = $('<div>');
  previewDiv.attr('div', 'preview');
  previewDiv.css('display', 'none');

  previewSwitch = $('<div>');
  previewSwitch.append($('<input type="radio" name="edit-mode" value="edit" checked>Edit</input>'));
  previewSwitch.append($('<input type="radio" name="edit-mode" value="preview">Preview</input>'));
  previewSwitch.change(function() {
    var val = $('input:radio[name=edit-mode]:checked').val();
    if (val == 'preview') {
      previewDiv.html(renderMarkdown(textarea.val()));
      textarea.css('display', 'none');
      previewDiv.css('display', '');
    } else {
      textarea.css('display', '');
      previewDiv.css('display', 'none');
    }
  });

  textarea.before(previewSwitch);
  textarea.before(previewDiv);
};

var viewer = function (target) {
  target.html(renderMarkdown(target.text()));
};

var fileUploader = function (textarea, action) {
  var setProgressBar = function(value) {
    progressbar.css("width", value + "%");
    progressbar.text(value + "%");
  };

  var createFileItem = function(file, link) {
    var fileitem, filelink, filesize, insertButton, deleteButton;

    fileitem = $('<li>');
    fileitem.attr('tabindex', 0);

    filelink = $('<a>');
    filelink.attr('href', file.url);
    filelink.text(file.name);
    filesize = $('<span>').text('(' + humanize.filesize(file.size) + ')');

    insertButton = $('<input type="button">');
    insertButton.attr('id', file.name);
    insertButton.attr('value', '본문에 삽입');
    insertButton.addClass('insertInto label label-info');
    insertButton.click(function() { insertLinkInto(textarea, link); });

    deleteButton = $('<a>');
    deleteButton.attr('name', 'submit');
    deleteButton.addClass('fileDeleteBtn close');
    deleteButton.text('x');
    deleteButton.click(function() {
      var form = $('<form>')
        .attr('method', 'post')
        .attr('enctype', 'multipart/form-data')
        .attr('action', file.url)
        .css('display', 'none');
      form.append('<input type="hidden" name="_method" value="delete">');
      $('body').append(form);
      form.ajaxForm({
        success: function() {
          fileitem.remove();
          textarea.val(textarea.val().split(link).join(''));
          setProgressBar(0);
          notification.text(file.name + ' is deleted successfully.');
        }
      });
      form.submit();
    });

    fileitem.append(filelink);
    fileitem.append(filesize);
    fileitem.append(insertButton);
    fileitem.append(deleteButton);

    return fileitem;
  };

  var createFileList = function(title) {
    var filelist, div

    filelist = $('<ul>');
    filelist.attr('id', 'filelist');
    filelist.addClass('files');

    div = $('<div>');
    div.addClass('attachment-list');
    div.append($('<strong>' + title + '</strong>'));
    div.append(filelist);

    return div;
  };

  var _getFileNameOnly = function(filename) {
    var fakepath = 'fakepath';
    var fakepathPostion = filename.indexOf(fakepath);
    if (fakepathPostion > -1) {
      filename = filename.substring(fakepath.length + fakepathPostion + 1);
    }
    return filename;
  };

  var _replaceFileInputControl = function() {
    progress.before(createAttachment());
  };

  var insertLinkInto = function(textarea, link) {
    var pos = textarea.prop('selectionStart');
    var text = textarea.val();
    textarea.val(text.substring(0, pos) + link + text.substring(pos));
  };

  var isImageType = function(mimeType) {
    if (mimeType && mimeType.substr(0, mimeType.indexOf('/')) == 'image') {
      return true;
    } else {
      return false;
    }
  };

  var createFileLink = function(name, url, mimeType) {
    if (isImageType(mimeType)) {
      return '<img src="' + url + '">';
    } else {
      return '[' + name + '](' + url + ')';
    }
  };

  var fileUploadOptions = {
    beforeSubmit: function() {
      var filename = _getFileNameOnly(attachment.val());

      // show message box
      if (filename === "") {
        notification.text('Choose a file to be attached.');
        return false;
      }

      return true;
    },

    success: function(responseBody, statusText, xhr) {
      var file, link;
      file = responseBody;

      if (!(file instanceof Object) || !file.name || !file.url) {
        notification.text('Failed to upload - Server error.');
        _replaceFileInputControl();
        setProgressBar(0);
        return;
      }

      _replaceFileInputControl();

      link = createFileLink(file.name, file.url, file.mimeType)
      if (isImageType(file.mimeType)) {
        insertLinkInto(textarea, link);
      }

      tempFileList.css('display', '');
      tempFileList.append(createFileItem(file, link));

      setProgressBar(100);
    },

    error: function(responseBody, statusText, xhr) {
      notification.text('Failed to upload.');
      _replaceFileInputControl();
      setProgressBar(0);
    },

    uploadProgress: function(event, position, total, percentComplete) {
      setProgressBar(percentComplete);
    }
  };

  var createAttachment = function() {
    var attachment = $('<input type="file" name="filePath">');

    attachment.click(function(event) {
      setProgressBar(0);
    });

    attachment.change(function(event) {
      if (attachment.val() !== "") {
        var filename = _getFileNameOnly(attachment.val());
        var form = $('<form>')
          .attr('method', 'post')
          .attr('enctype', 'multipart/form-data')
          .attr('action', action)
          .css('display', 'none');
        form.append(attachment);
        $('body').append(form);
        form.ajaxForm(fileUploadOptions);
        form.submit();
      }
    });

    return attachment;
  }

  var fileAttachment, attachment, progressbar, progress, attachmentList,
      tempFileList, notification;

  if (!textarea || !action) {
    throw new Error('textarea and action is required.');
  }

  attachment = createAttachment();
  progressbar = $('<div class="bar">');
  progress = $('<div class="progress progress-warning">').append(progressbar);
  attachmentList = createFileList('Attachments');
  tempFileList = createFileList('Temporary files (attached if you save)');
  notification = $('<div>');

  attachmentList.css('display', 'none');
  tempFileList.css('display', 'none');

  getFileList(textarea, action, function(responseBody, statusText, xhr) {
    var addFiles = function(files, targetList) {
      if (files) {
        for (var i = 0; i < files.length; i++) {
          var file = files[i];
          var link = createFileLink(file.name, file.url, file.mimeType);
          targetList.css('display', '');
          targetList.append(createFileItem(file, link));
        }
      }
    };

    addFiles(responseBody.attachments, attachmentList);
    addFiles(responseBody.tempFiles, tempFileList);
  });

  fileAttachment = textarea.siblings('.file-attachment');
  if (fileAttachment.length <= 0) {
    fileAttachment = textarea.parent().siblings('.file-attachment');
  }
  if (fileAttachment.length <= 0) {
    fileAttachment = $('<div>').addClass('file-attachment');
    textarea.after(fileAttachment);
  }

  fileAttachment
    .append(notification)
    .append(attachment)
    .append(progress)
    .append(attachmentList)
    .append(tempFileList);
}

var fileDownloader = function (div, urlToGetFileList) {
  var createFileItem = function(file) {
    var link = $('<a>')
        .prop('href', file.url)
        .append($('<i>').addClass('icon-download'))
        .append($('<div>').text(file.name).html());

    return $('<li>').append(link);
  }

  var filelist = $('<ul>');
  var addFiles = function(responseBody, statusText, xhr) {
    var files = responseBody.attachments;
    for (var i = 0; i < files.length; i++) {
      filelist.css('display', '');
      filelist.append(createFileItem(files[i]));
    }
  }

  getFileList(div, urlToGetFileList, addFiles);

  div.after(filelist);
}

nforge.markdown.enable = function() {
  var that = {
    init: function(targets, action) {
      for(var i = 0; i < targets.length; i++) {
        var target = targets[i];
        var tagname = target.tagName.toLowerCase();
        if (tagname == 'textarea' || tagname == 'input'
                || target.contentEditable == 'true') {
          editor($(target));
          fileUploader($(target), action);
        } else {
          viewer($(target));
          fileDownloader($(target), action);
        }
      }
    }
  };

  return that;
}
