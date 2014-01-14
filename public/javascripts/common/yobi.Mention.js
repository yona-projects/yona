/**
 * @(#)yobi.Mention.js 2013.08.12
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */
yobi.Mention = function(htOptions) {

    var htVar = {};
    var htElement = {};

    /**
     * Initialize
     *
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement();
        _attachEvent();
    }

    /**
     * Initialize Variables
     *
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions) {
        htVar = htOptions || {}; // set htVar as htOptions

        htVar.htMemberList = {
            "emptyQuery": true,
            "typeaheadOpts": {
                "items": 15
            },
            "users": [],
            "queryBy": ["username"]
        };
    }

    /**
     * Initialize Element variables
     */
    function _initElement(){
        htElement.welTarget = $('#' + htVar.target);
    }

    /**
     * attachEvent
     */
    function _attachEvent() {
        htElement.welTarget.on("keypress", _onKeyInput);
    }

    /**
     * Event handler on KeyInput event
     *
     * @param {Event} eEvt
     */
    function _onKeyInput(eEvt){
        eEvt = eEvt || window.event;

        if(eEvt.which === 64){ // 64 = at
            _findUserList();
        }
    }

    /**
     * Find Userlist
     */
    function _findUserList(){
        $.ajax({
            "url"        : htVar.url,
            "type"       : "get",
            "contentType": "application/json",
            "dataType"   : "json"
        }).done(_onLoadUserList);
    }

    function _onLoadUserList(aData){
        htVar.htMemberList.users = aData;
        htElement.welTarget.mention(htVar.htMemberList);
    }

    _init(htOptions || {});
};
