/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author JiHan Kim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
$(document).ready(function(){

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
