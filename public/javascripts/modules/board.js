nforge.namespace("board");
nforge.board.list = function() {
    var that = {
        init : function() {
            that.setUpEventListener();
        },

        setUpEventListener : function() {
            var $headers = $("th a");
            $headers.click(that.onHeader);
            var $pagination = $("pagination a");
            $pagination.click(that.onPager);
        },

        onHeader : function() {
            var key = $(this).attr("key");
            var $input = $("#search-form input[name=key]");
            if (key !== $input.val()) {
                $input.val(key)
            } else {
                $input = $("#search-form input[name=order]");
                if ($input.val() === "desc"){
                  $input.val("asc");
                }
                else if ($input.val() === "asc") {
                  $input.val("desc");
                }
            }
            $("#search-form").submit();
            return false;
        },

        onPager : function() {
            var $input = $("#search-form input[name=pageNum]");
            $input.val($(this).attr("pageNum"));
            $("#search-form").submit();
            return false;
        }
    };
    return that;
};
nforge.board.vaildate = function() {
    var that = {
        init : function() {
            $("form").submit(function() {
                if ($("input#title").val() === "" || $("textarea#contents").val() === "") {
                    $("#warning button").click(function(){
                        $('#warning').hide();
                    });
                    $('#warning').show();
                    return false;
                } else {
                    return true;
                }
            });
        }
    };
    return that;
};