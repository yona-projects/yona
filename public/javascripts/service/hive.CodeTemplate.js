/**
 * @(#)hive.{ModuleName}.js {YYYY.MM.DD}
 *
 * Copyright {AuthorName}.
 * Released under {License}
 * 
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = (function(htOptions){
		
		var htElements = {};

		/**
		 * initialize
		 * @param {Hash Table} htOptions
		 */
		function $init(htOptions){
			_initElement(htOptions || {});
			_attachEvent();
		}

		/**
		 * initialize element variables
		 */
		function _initElement(htOptions){
		}

		/**
		 * attach event handlers
		 */
        function _attachEvent() {
        }


        /**
         * ...
         * ...
         * your implements are here
         * ...
         * ...
         */
        
        return {
        	"init": $init
        };
	})();
	
})("hive.module.Name");