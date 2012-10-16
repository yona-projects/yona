nforge.namespace('markdown');

var markdownRender = function(text) {
  text = text.
    replace(/```(\w+)(?:\r\n|\r|\n)((\r|\n|.)*?)(\r|\n)```/gm, function(match, p1, p2) {
    try {
      return '<pre><code class="' + p1 + '">' + hljs(p2, p1).value + '</code></pre>';
    } catch (e) {
      return '<pre><code>' + hljs(p2).value + '</code></pre>';
    }
  });

  return new Showdown.converter().makeHtml(text);
};

nforge.markdown.edit = function () {
  var that;

  that = {
    init : function (selector) {
      var previewDiv, previewSwitch;

      if (!selector) selector = '#body';

      previewDiv = $('<div>');
      previewDiv.attr('div', 'preview');
      previewDiv.css('display', 'none');

      previewSwitch = $('<div>');
      previewSwitch.append($('<input type="radio" name="edit-mode" value="edit" checked>Edit</input>'));
      previewSwitch.append($('<input type="radio" name="edit-mode" value="preview">Preview</input>'));
      previewSwitch.change(function() {
        var val = $('input:radio[name=edit-mode]:checked').val();
        if (val == 'preview') {
          previewDiv.html(markdownRender($(selector).val()));
          $(selector).css('display', 'none');
          previewDiv.css('display', '');
        } else {
          $(selector).css('display', '');
          previewDiv.css('display', 'none');
        }
      });

      $(selector).before(previewSwitch);
      $(selector).before(previewDiv);
    }
  };

  return that;
};

nforge.markdown.render = function (selector) {
  var that;

  that = {
    init : function (selector) {
      if (!selector) selector = '#body';
      $(selector).html(markdownRender($(selector).text()));
    }
  };

  return that;
};
