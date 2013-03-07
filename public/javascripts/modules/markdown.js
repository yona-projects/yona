nforge.namespace('markdown');

var _PreventXSS = function(text) {
    // Remove all attributes in every tags
	var regex = /<[a-z\/!$]("[^"]*"|'[^']*'|[^'">])*>/gi;

	text = text.replace(regex, function(wholeMatch) {
        var elem = $(wholeMatch);
        var tagName;
        var match = wholeMatch.match(/<\s*(\/?)\s*([^\s>]*)/i);
        if (match) {
            if (match[2].toLowerCase() == 'script') {
                // Remove script tag
                return "";
            } else {
                // Remove attributes
                return "<" + match[1] + match[2] + ">";
            }
        }
	});

    return text;
};

var renderMarkdown = function(text) {
  text = text
    .replace(/```(\w+)(?:\r\n|\r|\n)((\r|\n|.)*?)(\r|\n)```/gm, function(match, p1, p2) {
    try {
      return '<pre><code class="' + p1 + '">' + hljs(p2, p1).value + '</code></pre>';
    } catch (e) {
      return '<pre><code>' + hljs(p2).value + '</code></pre>';
    }
  });

  return Markdown.getSanitizingConverter().makeHtml(text);
};

var editor = function (textarea) {
  var previewDiv, previewSwitch, commentBtn;

  previewDiv = $('<div>');
  commentBtn = $('.comment-btn');
  previewDiv.attr('div', 'preview');
  previewDiv.css('display', 'none');

  previewSwitch = $('<div id="mode-select">');
  previewSwitch.append($('<input type="radio" name="edit-mode" id="edit-mode" value="edit" checked>Edit</input>'));
  previewSwitch.append($('<input type="radio" name="edit-mode" id="preview-mode" value="preview">Preview</input>'));
  previewSwitch.change(function() {
    var val = $('input:radio[name=edit-mode]:checked').val();
    if (val == 'preview') {
      previewDiv.html(renderMarkdown(textarea.val()));
      textarea.css('display', 'none');
      previewDiv.css('display', '');
      commentBtn.css('display', 'none');
    } else {
      textarea.css('display', '');
      previewDiv.css('display', 'none');
      commentBtn.css('display', '');
    }
  });

  textarea.before(previewSwitch);
  textarea.before(previewDiv);
};

var viewer = function (target) {
  console.log(target.text());
  target.html(renderMarkdown(target.text()));
};

nforge.markdown.enable = function() {
  var that = {
    init: function(targets) {
      for(var i = 0; i < targets.length; i++) {
        var target = targets[i];
        var tagname = target.tagName.toLowerCase();
        if (tagname == 'textarea' || tagname == 'input'
                || target.contentEditable == 'true') {
          editor($(target));
        } else {
          viewer($(target));
        }
      }
    }
  };

  return that;
}
