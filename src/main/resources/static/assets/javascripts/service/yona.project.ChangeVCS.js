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
    var oNS = $yona.createNamespace(ns);
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
            elements.targetVCS = $("#targetVcs");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(options){
            elements.btnChangeVCS.on('click', showConfirmPopup);
            elements.btnChangeVCSExec.on("click", changeVCS);
            elements.targetVCS.on("change", function(){ showTexts(options.oTexts || {}); });
        }

        /**
         * 선택한 종류에 맞게 안내와 확인 문구를 갈아 끼운다
         */
        function showTexts(texts){
            var target = elements.targetVCS.val();
            var text = texts[target];
            if(!text){ return; }
            $(".next-vcs").text(target);
            $(".vcs-description").text(text.description);
            $(".vcs-requestion").text(text.requestion);
        }
        
        function showConfirmPopup() {
            if(elements.acceptChangeVCS.is(":checked") === false){
                $yona.alert(Messages("project.changeVCS.alert"));
                return false;
            }
            return true;
        }

        function changeVCS() {
            var ajaxOptions = {
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
                    bootstrap.Modal.getOrCreateInstance(document.querySelector("#alertChangeVCS")).hide();
                    $yona.alert(Messages("project.changeVCS.error"));
                }
            };
            // 바꿀 종류를 고르게 한 화면이면 고른 종류를 보낸다. 없으면 v1.6처럼 번갈아 바꾼다
            if(elements.targetVCS.length){
                ajaxOptions.data = {"targetVcs": elements.targetVCS.val()};
            }
            $.ajax(options.sTransferURL, ajaxOptions);
        }

        _init(options || {});
    };
    
})("yona.project.ChangeVCS");
