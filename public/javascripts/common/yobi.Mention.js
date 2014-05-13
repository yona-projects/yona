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
        htVar.atConfig = {
            at: "@",
            limit: 10,
            data: [],
            tpl: "<li data-value='@${loginid}'><img style='width:20px;height:20px;' src='${image}'> ${username} <small>${loginid}</small></li>",
            show_the_at: true
        }
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

        var charCode = eEvt.which || eEvt.keyCode;
        if(charCode === 64 || charCode === 35) { // 64 = @, 35 = #
            if(htVar.atConfig.data.length == 0) {
                _findMentionList();
            }
        }
    }

    /**
     * Find Userlist
     */
    function _findMentionList(){
        $.ajax({
            "url"        : htVar.url,
            "type"       : "get",
            "contentType": "application/json",
            "dataType"   : "json"
        }).done(_onLoadUserList);
    }

    function _onLoadUserList(aData){
        htVar.atConfig.data = aData.result;

        // on-key event fix for FF on Korean input
        var keyFix = new beta.fix(htVar.target);

        $inputor = htElement.welTarget
            .atwho(htVar.atConfig)
            .atwho({
                at: "#",
                limit: 10,
                tpl: '<li data-value="#${issueNo}"><small>#${issueNo}</small> ${title}</li>',
                data: aData.issues
            });
        $inputor.caret("pos", 47);
        $inputor.focus().atwho("run");
    }

    _init(htOptions || {});
};
