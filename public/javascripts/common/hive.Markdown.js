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
	 * @param {Hash Table} htOptions
	 */
	function _init(htOptions){
		_initVar(htOptions);
		_initElement(htOptions);
		
		_enableMarkdown();
	}
	
	/**
	 * initialize variables
	 * @param {Hash Table} htOptions
	 */
	function _initVar(htOptions){
		htVar.rxCodeBlock = /```(\w+)(?:\r\n|\r|\n)((\r|\n|.)*?)(\r|\n)```/gm;
		htVar.sTplSwitch = htOptions.sTplSwitch;
	}
	
	/**
	 * initialize element
     * @param {Hash Table} htOptions
	 */
	function _initElement(htOptions){
		htElement.waTarget = $(htOptions.aTarget) || $("[markdown]");
	}
	
	/**
	 * Render as Markdown document
     * @require showdown.js
     * @require hljs.js
	 * @param {String} sText
	 * @return {String}
	 */
	function _renderMarkdown(sText) {
		sText = sText.replace(htVar.rxCodeBlock, function(match, p1, p2) {
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

	// initialize
	_init(htOptions || {});
};
