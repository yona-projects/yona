/**
 * @(#)yobi.project.New.js 2013.03.18
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */

(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        function _init(htOptions){
            _initVar(htOptions);
            _initElement(htOptions);
            _attachEvent();

            _initFormValidator();
        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.sFormName = htOptions.sFormName || "newproject";

        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.welInputVCS = $("#vcs"); // input type="hidden"
            htElement.welBtnVCSSelected = $("#vcs_msg"); // <button data-toggle="dropdown">
            htElement.aVCSItems = $("#vcs_dropdown li a");
            htElement.svnWarning = $("#svn");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            htElement.aVCSItems.click(_onSelectVCSItem);
        }

        function _onSelectVCSItem(){
            var sText = $(this).text();
            var sValue = $(this).attr("data-value");

            htElement.welInputVCS.val(sValue);
            htElement.welBtnVCSSelected.text(sText);

            if(sText == "Subversion") {
                htElement.svnWarning.show();
            } else {
                htElement.svnWarning.hide();
            }
        }

        /**
         * initialize formValidator
         * @require validate.js
         */
        function _initFormValidator(){
            // name : name of input element
            // rules: rules to apply to the input element.
            var htRuleName   = {"name":"name",   "rules":"required"}; // project name
            var aRules = [htRuleName];

            htVar.oValidator = new FormValidator(htVar.sFormName, aRules, _onFormValidate);
        }

        /**
         * handler for validation errors.
         */
        function _onFormValidate(aErrors){
            if(aErrors.length > 0){
                $('span.warning').hide();
                $('span.msg').show();
            } else {
                new Spinner({
                    lines: 13, // The number of lines to draw
                    length: 10, // The length of each line
                    width: 5, // The line thickness
                    radius: 10, // The radius of the inner circle
                    corners: 1, // Corner roundness (0..1)
                    rotate: 0, // The rotation offset
                    direction: 1, // 1: clockwise, -1: counterclockwise
                    color: '#000', // #rgb or #rrggbb
                    speed: 1, // Rounds per second
                    trail: 60, // Afterglow percentage
                    shadow: false, // Whether to render a shadow
                    hwaccel: false, // Whether to use hardware acceleration
                    className: 'spinner', // The CSS class to assign to the spinner
                    zIndex: 2e9, // The z-index (defaults to 2000000000)
                    top: 'auto', // Top position relative to parent in px
                    left: 'auto' // Left position relative to parent in px
                }).spin(document.forms[htVar.sFormName]);
            }
        }

        _init(htOptions || {});
    };

})("yobi.project.New");
