yobi.Mention = function(htOptions) {

    var htVar = {};
    var htElement = {};

    function _init(htOptions){
        _initVar(htOptions);
        _attachEvent();
    }


    function _initVar(htOptions) {
        htVar = htOptions || {};

        htVar.memberList = {
            emptyQuery: true,
            typeaheadOpts: {
                items: 15
            },
            users: []
        };

    }

    function _attachEvent() {
        $('#'+htVar.target).keypress(function(event){
            var event = event || window.event;

            if( event.which == 64 ){ // 64 = at
                _findUserList(htVar.url, htVar.target);
            }
        });
    }

    function _findUserList(url, target){
        $.ajax({
            type: "GET",
            contentType: 'application/json',
            url: url,
            dataType: "json"
        }).done(function( data ) {
                htVar.memberList.users = data;
                $("#"+target).mention(htVar.memberList);
            });
    }

    _init(htOptions || {});
};
