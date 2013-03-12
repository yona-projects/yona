/**
 * @(#)hive.board.List.js 2013.03.11
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){
		
		var htElements = {};
    	var htOrderMap = {"asc": "desc", "desc": "asc"};

		/**
		 * initialize
		 * @param {Hash Table} htOptions
		 */
		function $init(htOptions){
			_initElement(htOptions || {});
			_attachEvent();
			
			console.log("board.List initialized");
		}

		/**
		 * initialize element variables
		 */
		function _initElement(htOptions){
			htElements.welForm = $(htOptions.sOptionForm || "#option_form");
			htElements.welInputKey = htElements.welForm.find("input[name=key]");
			htElements.welInputOrder = htElements.welForm.find("input[name=order]");
			htElements.welInputPageNum = htElements.welForm.find("input[name=pageNum]");
			
			htElements.welFilter = $(htOptions.sQueryFilter || "#order a");
			htElements.welPages = $(htOptions.sQueryPages || "#pagination a"); 
		}

		/**
		 * attach event handlers
		 */
        function _attachEvent() {
            htElements.welFilter.click(_onClickFilter);
            htElements.welPages.click(_onClickPage);
        }

        /**
         * onClick filter
         */
        function _onClickFilter(){
            var sKey = $(this).attr("key");

        	// Key
            if (sKey !== htElements.welInputKey.val()) {
            	htElements.welInputKey.val(sKey)
            } else { // Order
            	var sCurrentVal = htElements.welInputOrder.val();
            	htElements.welInputOrder.val(htOrderMap[sCurrentVal]);
            }
            htElements.welForm.submit();
            return false;
        }

        /**
         * onClick PageNum
         */
        function _onClickPage(){
        	htElements.welInputPageNum.val($(this).attr("pageNum"));
        	htElements.welForm.submit();
            return false;
        }
        
        $init();
	};
	
})("hive.board.List");

/*
$hive.load("Board", function(){
	hive.board.List();
});

$hive.load("board.List");
*/

nforge.namespace("board");
/**
 * PostList
 */

nforge.board.list = function() {
    var that = {
        "init" : function() {
            that.setUpEventListener();
        },

        "setUpEventListener" : function() {
            var $headers = $("#order a");
            $headers.click(that.onHeader);
            
            var $pagination = $("#pagination a");
            $pagination.click(that.onPager);
        },

        "onHeader" : function() {
            var key = $(this).attr("key");
            var $input = $("#option_form input[name=key]");
            if (key !== $input.val()) {
                $input.val(key)
            } else {
                $input = $("#option_form input[name=order]");
                if ($input.val() === "desc"){
                	$input.val("asc");
                } else if ($input.val() === "asc") {
                	$input.val("desc");
                }
            }
            $("#option_form").submit();
            return false;
        },

        "onPager" : function() {
            var $input = $("#option_form input[name=pageNum]");
            $input.val($(this).attr("pageNum"));
            $("#option_form").submit();
            return false;
        }
    };
    
    return that;
};

nforge.board.vaildate = function() {
    var that = {
        "init" : function() {
            $("form").submit(function() {
                if ($("input#title").val() === "" || $("textarea#contents").val() === "") {
                    $("#warning button").click(function(){
                        $('#warning').hide();
                    });
                    $('#warning').show();
                    return false;
                }
                return true;
            });
        }
    };
    return that;
};

/**
 * PostView
 */
nforge.board.view = function() {
    var that = {
        "init" : function(filesUrl) {
            var attachments;

            fileUploader.init({
            	"elTarget"    : $('#upload'),   // upload area
            	"elTextarea"  : $('#contents'), // textarea
            	"sTplFileItem": $('#tplAttachedFile').text(),
            	"sAction"     : filesUrl
            });
            
            attachments = $('.attachments');
            for (var i = 0, nLength = attachments.length; i < nLength; i++) {
                fileDownloader($(attachments[i]), filesUrl);
            }
        }
    }

    return that;
};

/**
 * newPost
 */
nforge.board.new = function() {
	var that = {
		"init": function(filesUrl) {
            fileUploader.init({
            	"elTarget"    : $('#upload'),   // upload area
            	"elTextarea"  : $('#contents'), // textarea
            	"sTplFileItem": $('#tplAttachedFile').text(),
            	"sAction"     : filesUrl
            });
		}
	};

	return that;
};


// Alias
nforge.board.edit = nforge.issue.new;
