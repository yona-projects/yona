/**
 * @(#)hive.{ModuleName}.js {YYYY.MM.DD}
 *
 * Copyright {AuthorName}.
 * Released under {License}
 * 
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){
		
		var htVar = {};
		var htElements = {};

		/**
		 * initialize
		 * @param {Hash Table} htOptions
		 */
		function _init(htOptions){
			_initVar(htOptions || {});
			_initElement(htOptions || {});
			_attachEvent();
		}

		/**
		 * initialize variables except element
		 */
		function _initVar(htOptions){
			htVar.sFoo = "bar";
		}
		
		/**
		 * initialize element variables
		 */
		function _initElement(htOptions){
			htElement.welDocument = $(htOptions.elDocument || document);
		}

		/**
		 * attach event handlers
		 */
        function _attachEvent() {
        	htElement.welDocument.ready(_onDocumentReady);
        }

        function _onDocumentReady(){
        	// ... 
        }
        

        /**
         * ...
         * ...
         * your implements are here
         * ...
         * ...
         */
        
        _init();
	};
	
})("hive.module.Name");