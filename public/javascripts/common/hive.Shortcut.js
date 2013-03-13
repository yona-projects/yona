nforge.namespace('shortcut');

// ctrl + enter to submit a form
nforge.shortcut.submit = function () {
  var that;

  that = {
    init : function () {
      var eventHandler = function (event) {
        if (event.ctrlKey && event.which == 13) {
          $($(event.target).parents('form').get(0)).submit();
        }
      }

      $('textarea').keydown(eventHandler);
      $('input').keydown(eventHandler);
    }
  };

  return that;
};


nforge.namespace('code');

nforge.code.copy = function() {
    return {
        init: function() {
            $('#copy-url').zclip({
                path: '/assets/javascripts/ZeroClipboard.swf',
                copy: function() {
                    return $("#repo-url").attr('value');
                }
            });
        }
    }
}

/*
(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){

		_init(htOptions);
	};
	
})("hive.Shortcut");
*/

/*
function shortcutKeys() {
	this.keyNames={'32':'space', '8':'backspace', '9':'tab', '46':'delete', 
			'65':'A','66':'B','67':'C','68':'D','69':'E','70':'F','71':'G','72':'H','73':'I','74':'J','75':'K','76':'L','77':'M',
			'78':'N','79':'O','80':'P','81':'Q','82':'R','83':'S','84':'T','85':'U','86':'V','87':'W','88':'X','89':'Y','90':'Z',
			'48':'0','49':'1','50':'2','51':'3','52':'4','53':'5','54':'6','55':'7','56':'8','57':'9',
			'96':'0p','97':'1p','98':'2p','99':'3p','100':'4p','101':'5p','102':'6p','103':'7p','104':'8p','105':'9p',
			'191':'/'
	};
	this.actions={};
	this.set=function(key, action) {
		this.actions[key] = action;
	};
	this.handler=function(event) {
		if (event.altKey || event.ctrlKey)
			return;
		keyCode = event.keyCode || event.charCode;
		target = event.target || event.srcElement;
		switch (target.tagName) {
		case "INPUT":
		case "SELECT":
		case "TEXTAREA":
			return;
		}
		if (this.actions[this.keyNames[keyCode]] !== undefined)
			eval(this.actions[this.keyNames[keyCode]]);
	};
};
*/