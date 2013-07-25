/**
 * @(#)yobi.ShortcutKey 2013.03.21
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */
yobi.ShortcutKey = (function(htOptions){
	
	var htVar = {};
	var htHandlers = {};

	/**
	 * initialize
	 */
	function _init(htOptions){
		_initVar();
		_attachEvent();
	}
	
	/**
	 * initialize variables
	 */
	function _initVar(){
		htVar.rxTrim = /\s+/g;
		htVar.aCombinationKeys = ["CTRL", "ALT", "SHIFT"];
		htVar.htKeycodeMap = {
			'13':'ENTER', '38':'UP', '40':'DOWN', '37':'LEFT', '39':'RIGHT', '13':'ENTER', '27':'ESC',
			'32':'SPACE', '8':'BACKSPACE', '9':'TAB', '46':'DELETE', '33':'PAGEUP', '34':'PAGEDOWN', '36':'HOME', '35':'END',
			'65':'A', '66':'B', '67':'C', '68':'D', '69':'E', '70':'F', '71':'G', '72':'H', '73':'I', '74':'J', '75':'K', '76':'L', 
			'77':'M', '78':'N', '79':'O', '80':'P', '81':'Q', '82':'R', '83':'S', '84':'T', '85':'U', '86':'V', '87':'W', '88':'X', 
			'89':'Y', '90':'Z',	'48':'0', '49':'1', '50':'2', '51':'3', '52':'4', '53':'5', '54':'6', '55':'7', '56':'8', '57':'9',
			'219':'[', '221':']', '186':';', '222':'\'', '188':',', '190':'.', '191':'/', '189':'-', '187':'=', '220':'\\', '192':'`',
			'112':'F1', '113':'F2', '114':'F3', '115':'F4', '116':'F5', '117':'F6', '118':'F7', '119':'F8', '120':'F9', '121':'F10', '122':'F11', '123':'F12'
		};
	}
	
	/**
	 * add event listener
	 */
	function _attachEvent(){
		$(window).bind("keydown", _onKeyDown);
		$(window).bind("beforeunload", destroy); // free memory
	}
	
	function _detachEvent(){
		$(window).unbind("keydown", _onKeyDown);
		$(window).unbind("beforeunload", destroy);
	}
	
	/**
	 * global keyDown event handler
	 */
	function _onKeyDown(weEvt){
		var sKeyInput = _getKeyString(weEvt);
		var aHandlers = htHandlers[sKeyInput] || [];
		
		_runEventHandler(aHandlers, weEvt, sKeyInput);
	}
	
	function _runEventHandler(aHandlers, weEvt, sKeyInput){
		var htInfo = {
			"weEvt"     : weEvt,
			"welTarget" : $(weEvt.target),
			"sTagName"  : weEvt.target.tagName,
			"sKeyInput" : sKeyInput,
			"bFormInput": (weEvt.target.tagName == "INPUT" || weEvt.target.tagName == "TEXTAREA")
		};
		
		try {
			aHandlers.forEach(function(fHandler){
				fHandler(htInfo);
			});
		}catch(e){} finally {
			htInfo = null;
		}
	}
	
	/**
	 * attach Shortcut Key Handler
	 * @param {String} vKey keyCombiation String e.g. CTRL+ENTER
	 * @param {String} fHandler handler function
	 * or
	 * @param {Hash Table} vKey {"keyCombination:function(){}, "key":function(){}} 
	 */
	function attachHandler(vKey, fHandler){
		if(typeof vKey == "string"){
			return _addHandler(vKey, fHandler);
		}
		
		var fHandler;
		for(var sKey in vKey){
			fHandler = vKey[sKey];
			_addHandler(sKey, fHandler);
		}
	}
	
	function _addHandler(sKey, fHandler){
		sKey = _normalizeKeyString(sKey);
		
		if(!(htHandlers[sKey] instanceof Array)){
			htHandlers[sKey] = [];
		}
		
		htHandlers[sKey].push(fHandler);
	}

	/**
	 * detach Shortcut Key Handler
	 * @param {String} sKey
	 * @param {String} fHandler
	 */
	function detachHandler(sKeyInput, fHandler){
		var sKey = _normalizeKeyString(sKeyInput);
		var aHandlers = htHandlers[sKey];
		
		if(aHandlers instanceof Array){
			aHandlers.splice(aHandlers.indexOf(fHandler), 1);
			htHandlers[sKey] = aHandlers;
		}		
	}

	/**
	 * generate key string from keyDown event
	 * @param {Wrapped Event} weEvt
	 */
	function _getKeyString(weEvt){
		var sMainKey = htVar.htKeycodeMap[weEvt.keyCode];
		if(typeof sMainKey == "undefined"){ // ignore event if not on keyMap
			return;
		}
		
		// generate key combination
		var aKeys = [];
		var sKeyString = "";
		
		if(weEvt.altKey){
			aKeys.push("ALT");
		}

		if(weEvt.ctrlKey || weEvt.metaKey){
			aKeys.push("CTRL");
		}
		
		if(weEvt.shiftKey){
			aKeys.push("SHIFT");
		}
		
		aKeys.push(sMainKey);
		sKeyString = aKeys.join("+").toUpperCase();
		
		return sKeyString;
	}
	
	/**
	 * normalize Key String
	 * @param {String} sKey
	 */
	function _normalizeKeyString(sKey){
		sKey = sKey.toUpperCase() || "";
		sKey = sKey.replace(htVar.rxTrim, '');
		sKey = sKey.split("+").sort(function(v){
			return -1 * (htVar.aCombinationKeys.indexOf(v));
		}).join("+");
		
		return sKey;
	}
	
	/**
	 * Get Key Handlers. for debug.
	 * @return {Hash Table}
	 */
	function getHandlers(){
		return htHandlers;
	}
	
	/**
	 * set keyMap link
	 * @param {Hash Table} htKeyMap
	 * @example 
	 * setKeymapLink({
	 *    "N": "http://www.naver.com"
	 * });
	 */
	function setKeymapLink(htKeyMap){
	    for(var sKey in htKeyMap){
	        attachHandler(sKey, function(htInfo){
	            if(!htInfo.bFormInput){
	                document.location.href = htKeyMap[htInfo.sKeyInput];
	            }
	        });
	    }
	}
	
	/**
	 * destroy this
	 */
	function destroy(){
		_detachEvent();
		htHandlers = htVar = null;
	}
	
	_init(htOptions);

	// public interface
	return {
		"attach": attachHandler,
		"detach": detachHandler,
		"getHandlers": getHandlers,
		"setKeymapLink": setKeymapLink
	};
})();
