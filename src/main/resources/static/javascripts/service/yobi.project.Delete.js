/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihan Kim
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

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
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
            htElement.welChkAccept    = $("#accept");
            htElement.welBtnDeletePop = $("#btnDelete");
            htElement.welBtnDeletePrj = $("#btnDeleteExec");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(htOptions){
            htElement.welBtnDeletePop.click(_onClickBtnDeletePrj);
            htElement.welBtnDeletePrj.requestAs({
                "sMethod" : "delete",
                "sHref"   : htOptions.sDeleteURL,
                "fOnError": function(){
                    $("#alertDeletion").modal("hide");
                    $yobi.alert(Messages("project.delete.error"));
                    return false;
                }
            });
        }

        function _onClickBtnDeletePrj(){
            if(htElement.welChkAccept.is(":checked") === false){
                $yobi.alert(Messages("project.delete.alert"));
                return false;
            }
            return true;
        }

        _init(htOptions || {});
    };

})("yobi.project.Delete");
