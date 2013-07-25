/**
 * @(#)yobi.ui.Tabs.js 2013.06.25
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */

$(document).ready(function(){

    /**
     * 탭 클릭시 선택했던 탭 번호 저장하도록 이벤트 설정
     */
    var waItems;
    var welContainer;
    var sContainerId;
    
    $(".nav-tabs[id]").each(function(i, elContainer){
        welContainer = $(elContainer);
        sContainerId = welContainer.attr("id");

        if(typeof sContainerId != "undefined"){
            waItems = welContainer.find("li");
            waItems.click(function(){
                localStorage.setItem("yobitab-" + sContainerId, $(this).index());
            });
            _restoreTab(sContainerId, waItems);
        }
    });

    /**
     * 선택했던 탭 복원
     * @param {String} sContainerId
     * @param {Wrapped Array} waItems
     */
    function _restoreTab(sContainerId, waItems){
        var welLink;
        var waItems = $("#" + sContainerId).find("li > a");
        var nIndex = localStorage.getItem("yobitab-" + sContainerId);

        if(nIndex && waItems[nIndex]){
            welLink = $(waItems[nIndex]);

            if(welLink && welLink.data("toggle" == "tab")){
                welLink.tab("show");
            }
        }

        welLink = waItems = nIndex = null;
    }

});