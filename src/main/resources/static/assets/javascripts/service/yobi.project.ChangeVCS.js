/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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
(function(ns){

    var NO_CONTENT = 204;
    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(options){
                
        var elements = {};
        
        /**
         * initialize
         */
        function _init(options){
            _initElement(options);
            _attachEvent(options);
        }
        
        /**
         * initialize element variables
         */
        function _initElement(optinos){
            elements.acceptChangeVCS = $("#acceptChangeVCS");
            elements.btnChangeVCS = $("#btnChangeVCS");
            elements.btnChangeVCSExec = $("#btnChangeVCSExec");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(options){
            elements.btnChangeVCS.on('click', showConfirmPopup);
            elements.btnChangeVCSExec.on("click", changeVCS);
        }
        
        function showConfirmPopup() {
            if(elements.acceptChangeVCS.is(":checked") === false){
                $yobi.alert(Messages("project.changeVCS.alert"));
                return false;
            }
            return true;
        }

        function changeVCS() {
            $.ajax(options.sTransferURL, {
                "method" : "post",
                "success": function(res, status, xhr){
                    // default action below:
                    var location = xhr.getResponseHeader("Location");

                    if(xhr.status === NO_CONTENT && location){
                        document.location.href = location;
                    } else {
                        document.location.reload();
                    }
                },
                "error": function(){
                    $("#alertChangeVCS").modal("hide");
                    $yobi.alert(Messages("project.changeVCS.error"));
                }
            });
        }

        _init(options || {});
    };
    
})("yobi.project.ChangeVCS");
