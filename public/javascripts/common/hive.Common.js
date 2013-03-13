/**
 * @(#)hive.Common.js 2013.03.11
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */
window.hive = (typeof hive == "undefined") ? {} : hive;

$hive = hive.Common = (function(){
	
	var htVar = {"sScriptPath":""};
	var htModuleInstance = {};
	
	/**
	 * set JavaScript asset path for loadScript
	 * @param {String} sPath
	 */
	function setScriptPath(sPath){
		htVar.sScriptPath = sPath;
	}

	/**
	 * Create namespace object from String
	 * @param {String} sName namespace string like 'hive.module.Name'
	 * @returns {Hash Table} container object and last name of argument
	 * @example 
	 * var oNS = createNamespace("hive.module.Name");
	 * oNS.container[oNS.name] = { ... };
	 * // oNS.container === hive.module
	 * // oNS.name === "Name"
	 */
	function createNamespace(sNamespace) {
		var aSpace = sNamespace.split(".");
		var oParent = window;
		var sObjectName = null;
		
		for ( var i = 0, len = aSpace.length; i < len; i++) {
			sObjectName = aSpace[i];
			if (i == (len - 1)) {
				break;
			}
			if (typeof oParent[sObjectName] !== "object") {
				oParent[sObjectName] = {};
			}
			oParent = oParent[sObjectName];
		}
		
		return {
			"container" : oParent,
			"name" : sObjectName
		};
	}
	
	/**
	 * load module
	 * @param {String} sName
	 * @param {Hash Table} htOptions
	 * @param {Function} fCallback
	 */
	function loadModule(sName, htOptions, fCallback){
		htOptions = htOptions || {};

		// 모듈 스크립트가 이미 로드되었으면 바로 초기화 하고
		// 그렇지 않으면 스크립트 파일 불러온 뒤 초기화 시도
		if(registerModule(sName, htOptions) === false){
			htVar.htTryLoad = htVar.htTryLoad || {};
			htVar.htTryLoad[sName] = (typeof htVar.htTryLoad[sName] == "undefined") ? 1 : (htVar.htTryLoad[sName]++);
			
			if(htVar.htTryLoad[sName] > 3){
				console.log("[HIVE] fail to load module " + sName);
				return false;
			}
			
			var sURL = htVar.sScriptPath + "service/hive." + sName + ".js";
			var fOnLoad = function(){
				loadModule(sName, htOptions, fCallback);
			};
			return loadScript(sURL, fOnLoad);
		}
		
		if(typeof fCallback == "function"){
			fCallback(htOptions);
		}
	}

	/**
	 * register module
	 * @param {String} sName
	 * @param {Hash Table} htOptions
	 */
	function registerModule(sName, htOptions){
		var aNames = sName.split(".");
		var sDepth = aNames.shift();
		var oModule = hive[sDepth];
		
		while(aNames.length && oModule){
			sDepth = aNames.shift();
			oModule = oModule[sDepth];
		}
		
		/*
		if(typeof oModule != "function"){
			console.log("[HIVE] " + sName + " is not loaded or invalid module");
			return false;
		}
		
		htModuleInstance[sName] = new oModule(htOptions);
		return htModuleInstance[sName];
		*/
		
		// temporary code for compatibility with nForge
		var oInstance;
		if(typeof oModule == "undefined"){
			return false;
		} else if(typeof oModule == "function"){
			oInstance = new oModule(htOptions);
		} else if(typeof oModule == "object"){
			oInstance = oModule;
			oInstance.init();
		}
		return htModuleInstance[sName] = oInstance;
	}
	
	/**
	 * load JavaScript
	 * @param {String} sURL
	 * @param {Function} fCallback callback function on load
	 */
	function loadScript(sURL, fCallback){
		var elScript = document.createElement("script");
		elScript.type = "text/javascript";
		elScript.async = true;
		elScript.src = sURL;

		// run callback and free memory on load
		var fOnLoad = function(){
			if(typeof fCallback == "function"){
				fCallback();
			}
			document.body.removeChild(elScript);
			elScript = fOnLoad = null;
		};
		
		// attach onLoad event handler
		if(typeof elScript.onload == "undefined"){
			elScript.onreadystatechange = function(){ // for IE
				if(this.readyState === "complete" || this.readyState === "loaded"){
					fOnLoad();
				}
			};
		} else { // and for other polite browsers
			elScript.onload = fOnLoad;
		}
	
		document.body.appendChild(elScript);
	}

	/**
	 * stop Event
	 * @param {Event} eEvt
	 */
	function stopEvent(eEvt) {
		if(!eEvt){
			return;
		}
		eEvt.cancelBubble = true;
		eEvt.returnValue = false;

		if (eEvt.stopPropagation) {
			eEvt.stopPropagation();
		}
		if (eEvt.preventDefault) {
			eEvt.preventDefault();
		}
	}
	
	/* public Interface */
	return {
		"setScriptPath": setScriptPath,
		"createNamespace": createNamespace,
		"loadModule": loadModule,
		"loadScript": loadScript,
		"stopEvent": stopEvent
	};
})();

// tooltip
$(document).ready(function(){
	$('.n-tooltip').tooltip();	
});

var nforge = {
	"namespace": function(sName){
		var oNS = $hive.createNamespace("nforge." + sName);
		oNS.container[oNS.name] = {};
	},
	
	"require": function(sModuleName, htOptions){
		$hive.loadModule(sModuleName, htOptions);
	}
};