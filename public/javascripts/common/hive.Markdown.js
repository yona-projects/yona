/**
 * @(#)hive.Markdown 2013.03.21
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */
hive.Markdown = function(htOptions){

	var htVar = {};
	var htElement = {};
	
	/**
	 * initialize
	 */
	function _init(htOptions){
		_initVar(htOptions);
		_initElement(htOptions);
		
		_enableMarkdown();
	}
	
	/**
	 * initialize variables
	 */
	function _initVar(htOptions){
		htVar.rxMarkdown = /```(\w+)(?:\r\n|\r|\n)((\r|\n|.)*?)(\r|\n)```/gm;
		htVar.sTplSwitch = htOptions.sTplSwitch;
	}
	
	/**
	 * initialize element
	 */
	function _initElement(htOptions){
		htElement.waTarget = $(htOptions.aTarget) || $("[markdown]");
	}
	
	/**
	 * Render as Markdown document
	 * @require showdown.js
	 * @require hljs.js
	 */
	function _renderMarkdown(sText) {
		sText = sText.replace(htVar.rxMarkdown, function(match, p1, p2) {
			try {
				return '<pre><code class="' + p1 + '">' + hljs(p2, p1).value + '</code></pre>';
			} catch (e) {
				return '<pre><code>' + hljs(p2).value + '</code></pre>';
			}
		});

		var sHTML = new Showdown.converter().makeHtml(sText); 
		return sHTML;
	}

	/**
	 * set Markdown Editor
	 * @param {Wrapped Element} welTextarea
	 */
	function _setEditor(welTextarea) {
		// create new preview area 
		var welPreview = $('<div class="markdown-preview">');
		welPreview.css({
			"display"   : "none",
			"width"     : welTextarea.width()  + 'px',
			"min-height": welTextarea.height() + 'px',
			"padding"   : welTextarea.css("padding")
		});

		var welPreviewSwitch = $('<div id="mode-select">');
			welPreviewSwitch.html(htVar.sTplSwitch);

		var fOnChangeSwitch = function() {
			var bPreview = ($("input:radio[name=edit-mode]:checked").val() == "preview");
			welPreview.html(_renderMarkdown(welTextarea.val()));
			(bPreview ? welPreview: welTextarea).show();
			(bPreview ? welTextarea: welPreview).hide();
		};
		welPreviewSwitch.change(fOnChangeSwitch);

		welTextarea.before(welPreviewSwitch);
		welTextarea.before(welPreview);
	}

	/**
	 * set Markdown Viewer
	 * @param {Wrapped Element} welTarget is not <textarea> or <input>
	 */
	function _setViewer(welTarget) {
		welTarget.html(_renderMarkdown(welTarget.text()));
	}

	/**
	 * enableMarkdown
	 * same as nforge.markdown.enable
	 */
	function _enableMarkdown(){
		var sTagName;
		
		htElement.waTarget.each(function(nIndex, elTarget){
			sTagName = elTarget.tagName.toUpperCase();
			
			if(sTagName == "TEXTAREA" || sTagName == "INPUT" || elTarget.contentEditable == "true"){
				_setEditor($(elTarget));
			} else {
				_setViewer($(elTarget));
			}
		});
	}
	
	_init(htOptions || {});
};


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