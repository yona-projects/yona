/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
function yonaTitleHeadModule(htOptions){
    var htVar = {};
    var htElement = {};
    var issueLabels = [];
    var projectLabels = getProjectLabels();

    /**
     * Initialize
     *
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement();
        _attachEvent();

        if($("#labelIds").length > 0 ) {
            issueLabels = $("#labelIds").select2("val");
        }
    }

    /**
     * Initialize Variables
     *
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions) {
        htVar = htOptions || {}; // set htVar as htOptions
        htVar.doesNotDataLoaded = true;
        htVar.nKeyupEventGenerator = null;
        htVar.sMentionText = null;
    }

    /**
     * Initialize Element variables
     */
    function _initElement() {
        if (!htVar.target) {
            if (window.console) {
                console.error("mention form element targeting doesn't exist!")
            }
            return;
        }
        htElement.welTarget = $(htVar.target);
    }
    /**
     * attachEvent
     */
    function _attachEvent() {
        htElement.welTarget.on("keypress", _onKeyInput);
        if (jQuery.browser.mozilla){
            htElement.welTarget.on("focus", _startKeyupEventGenerator);
            htElement.welTarget.on("blur", _stopKeyupEventGenerator);
        }
    }

    /**
     * Event handler on KeyInput event
     *
     * @param {Event} eEvt
     */
    function _onKeyInput(eEvt){
        eEvt = eEvt || window.event;

        var charCode = eEvt.which || eEvt.keyCode;
        if(charCode === 91) { // 91 = [
            if(htVar.doesNotDataLoaded) {
                _findTitleHead(eEvt);
            }
        }
    }

    function _startKeyupEventGenerator(){
        if (htVar.nKeyupEventGenerator){
            clearInterval(htVar.nKeyupEventGenerator);
        }

        htVar.nKeyupEventGenerator = setInterval(
            function(){
                if (htVar.sMentionText != htElement.welTarget.val()){
                    htElement.welTarget.trigger("keyup");
                    htVar.sMentionText = htElement.welTarget.val();
                }
            }
            ,100);
    }

    function _stopKeyupEventGenerator(){
        if (htVar.nKeyupEventGenerator){
            clearInterval(htVar.nKeyupEventGenerator);
            htVar.nKeyupEventGenerator = null;
        }
    }

    function _findTitleHead(){
        htVar.doesNotDataLoaded = false;

        var searchPending;

        htElement.welTarget
            .atwho({
                at: "[",
                limit: 10,
                displayTpl: "<li data-value='@${name}' data-category-id='${categoryId}' data-is-exclusive='${isExclusive}' data-id='${id}'><small style='color: #${labelColor}'>${category}</small> ${name}</li>",
                suspendOnComposing: false,
                searchKey: "searchText",
                suffix: "",
                insertTpl: "[${name}]",
                startWithSpace: false,
                callbacks: {
                    remoteFilter: function(query, callback) {
                        NProgress.start();
                        issueLabels = $("#labelIds").length > 0 && $("#labelIds").select2("val") || [];
                        clearTimeout(searchPending);

                        searchPending = setTimeout(function () {
                            $.getJSON(htVar.url, { query: query }, function (data) {
                                NProgress.done();
                                callback(data.result);
                            });
                        }, 300);
                    },
                    sorter: function(query, items, searchKey) {
                        var results = [];

                        items.forEach(function (item) {
                            if (item[searchKey].toLowerCase().indexOf(query.toLowerCase()) !== -1) {
                                results.push(item);
                            }
                        });

                        return results.sort(function(a, b) {
                            if(b.frequency === a.frequency) {
                                if(b.category.toLowerCase() === a.category.toLowerCase()) {
                                    return a.name.toLowerCase() >= b.name.toLowerCase() ? 1 : -1;
                                } else {
                                    return a.category.toLowerCase() > b.category.toLowerCase() ? 1 : -1;
                                }
                            }
                            return b.frequency - a.frequency;
                        });
                    },
                    beforeInsert: function(value, $li, e) {
                        var category = $li.find("small").text();
                        var selectedValueOnly = value.substring(1, value.length - 1);
                        var $labelField = $("#labelIds");

                        if (category && $labelField.length > 0) {
                            var $selectedLabel = $labelField.find("option[value=" + $li.data("id")  + "]");
                            $selectedLabel.prop('selected', true);

                            if($li.data("isExclusive")){
                                issueLabels = issueLabels.filter(function(label){
                                    return projectLabels[$li.data("categoryId")].indexOf(label) === -1
                                });
                            }

                            issueLabels.push($selectedLabel.val());
                            $("#labelIds").select2("val", issueLabels);

                            $yobi.notify('Label: ' + selectedValueOnly, 3000);
                            return "";
                        } else {
                            return value;
                        }
                    }
                }
            });
    }

    /**
     *  It gather all project labels.
     *  Category id is used for the key.
     *  Label ids are used for the value.
     *  {
     *     31: [130, 120, ...],
     *     70: [200, 201, 320, ...]
     *  }
     */
    function getProjectLabels(){
        var allLabels = {};
        $("#labelIds > optgroup").each(function(){
            var allLabelsOfTheCategory = [];
            var categoryId;
            $(this).children().each(function(){
                $this = $(this);
                allLabelsOfTheCategory.push($this.val());
                categoryId = $this.data("categoryId")
            });

            allLabels[categoryId] = allLabelsOfTheCategory;
        });
        return allLabels;
    }

    _init(htOptions || {});
}
