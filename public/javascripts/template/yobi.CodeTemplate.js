/**
 * @(#)yobi.{ModuleName}.js {YYYY.MM.DD}
 *
 * Copyright {AuthorName}.
 * Released under {License}
 * 
 */

(function(ns){
	
	var oNS = $yobi.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){
		
		var htVar = {};
		var htElement = {};

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

        /**
         * destroy this module
         */
        function destroy(){
        	// detachEvent() if available
        	
        	// free memory
        	htVar = htElement = null;
        }
        
        _init();
	};
	
})("yobi.module.Name");
