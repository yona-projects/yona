/**
 * Yona, Project Hosting SW
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

    var oNS = $yona.createNamespace(ns);
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
            htElement.elChkAccept    = document.getElementById("accept");
            htElement.elBtnDeletePop = document.getElementById("btnDelete");
            htElement.elBtnDeletePrj = document.getElementById("btnDeleteExec");
            htElement.elAlertDeletion = document.getElementById("alertDeletion");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(htOptions){
            // 원본은 #btnDelete의 data-toggle="modal"(Bootstrap 전역 델리게이트)이 모달을 열고,
            // _onClickBtnDeletePrj가 체크 안 됐을 때 false를 반환해(stopPropagation) 그
            // 델리게이트까지 이벤트가 안 번지게 막는 방식이었다 - 델리게이트를 없앴으니 여기서
            // 직접 체크 후 열도록 동일한 게이트를 재현한다.
            htElement.elBtnDeletePop.addEventListener('click', function(weEvt){
                weEvt.preventDefault();
                if(_onClickBtnDeletePrj()){
                    htElement.elAlertDeletion.showModal();
                }
            });

            $yona.attachDialogDismiss(htElement.elAlertDeletion);

            // P3-70 라운드10: jquery.requestAs.js 플러그인 호출부를 $yona.requestAs(코어
            // 라이브러리 제거를 위해 이번 라운드에서 신설된 네이티브 대체)로 전환.
            $yona.requestAs(htElement.elBtnDeletePrj, {
                "sMethod" : "delete",
                "sHref"   : htOptions.sDeleteURL,
                "fOnError": function(){
                    htElement.elAlertDeletion.close();
                    $yona.alert(Messages("project.delete.error"));
                    return false;
                }
            });
        }

        function _onClickBtnDeletePrj(){
            if(htElement.elChkAccept.checked === false){
                $yona.alert(Messages("project.delete.alert"));
                return false;
            }
            return true;
        }

        _init(htOptions || {});
    };

})("yona.project.Delete");
