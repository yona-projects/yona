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

var markdownEditor = editor;
var markdownViewer = viewer;
