/**
 * @(#)yobi.project.Delete.js 2013.04.24
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
        
        /**
         * 프로젝트 삭제 버튼 클릭시 이벤트 핸들러
         * 데이터 영구 삭제 동의에 체크했는지 확인하고
         * 체크되지 않았으면 경고
         */
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