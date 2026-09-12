/**
 * Yona, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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
    
    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){
                
        var htElement = {};
        
        /**
         * initialize
         */
        function _init(htOptions){
            _initElement(htOptions);
            _attachEvent(htOptions);
        }
        
        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welChkAccept      = $("#accept");
            htElement.welBtnTransferPop = $("#btnTransfer");
            htElement.welBtnTransferPrj = $("#btnTransferExec");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(htOptions){
            htElement.welBtnTransferPop.click(_onClickBtnTransferPop);

            htElement.welBtnTransferPrj.one("click", function(){
                fetch(htOptions.sTransferURL + "?owner=" + $("#owner").val(), {"method": "put"})
                    .then(function(response){
                        if(!response.ok){
                            return Promise.reject(response);
                        }
                        // default action below:
                        var sLocation = response.headers.get("Location");

                        if(response.status === 204 && sLocation){
                            document.location.href = sLocation;
                        } else {
                            document.location.reload();
                        }
                    })
                    .catch(function(){
                        $("#alertTransfer").modal("hide");
                        $yona.alert(Messages("project.transfer.error"));
                    });
            });
        }
        
        function _onClickBtnTransferPop(){
            if(htElement.welChkAccept.is(":checked") === false){
                $yona.alert(Messages("project.transfer.alert"));
                return false;
            }
            return true;
        }

        _init(htOptions || {});
    };
    
})("yona.project.Transfer");
