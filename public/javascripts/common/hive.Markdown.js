/**
 * nforge.markdown.js
 */

nforge.namespace('markdown');

(function(){
	var rxMarkdown = /```(\w+)(?:\r\n|\r|\n)((\r|\n|.)*?)(\r|\n)```/gm;

	var renderMarkdown = function(text) {
	  text = text.replace(rxMarkdown, function(match, p1, p2) {
	    try {
	      return '<pre><code class="' + p1 + '">' + hljs(p2, p1).value + '</code></pre>';
	    } catch (e) {
	      return '<pre><code>' + hljs(p2).value + '</code></pre>';
	    }
	  });

	  return new Showdown.converter().makeHtml(text);
	};

	var sPreviewSwitchHTML = '\
<input type="radio" name="edit-mode" id="edit-mode" value="edit" checked="checked" class="radio-btn" />\
<label for="edit-mode" style="margin-right:3px;">Edit</label>\
<input type="radio" name="edit-mode" id="preview-mode" value="preview" class="radio-btn" />\
<label for="preview-mode">Preview</label>';

	var editor = function (textarea) {
		var previewDiv, previewSwitch;

		previewDiv = $('<div class="markdown-preview">');
		previewDiv.css('display', 'none');
		previewDiv.css('width', (textarea.width()) + 'px');
		previewDiv.css('min-height', textarea.height() + 'px');
		previewDiv.css('padding', textarea.css("padding"));

		previewSwitch = $('<div id="mode-select">');
		previewSwitch.append($(sPreviewSwitchHTML));

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

	nforge.markdown.enable = function() {
		var that = {
			"init" : function(targets) {
				var nLength = targets.length;
				for ( var i = 0; i < nLength; i++) {
					var target = targets[i];
					var tagname = target.tagName.toLowerCase();
					if (tagname == 'textarea' || tagname == 'input' || target.contentEditable == 'true') {
						editor($(target));
					} else {
						viewer($(target));
					}
				}
			}
		};

		return that;
	};	
})();



/** lib/markdown.js **/
/*
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
*/