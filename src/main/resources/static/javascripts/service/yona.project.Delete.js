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
            // 체크박스 게이트 + 모달 열기/닫기는 project.Transfer/ChangeVCS.js와 거의
            // 동일하게 반복되던 부분이라 공용 헬퍼로 합쳤다(yona.Common.js 참고,
            // widget-candidates.md 4번 항목).
            $yona.attachCheckboxGatedConfirm(htElement.elBtnDeletePop, htElement.elChkAccept,
                htElement.elAlertDeletion, Messages("project.delete.alert"));

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

        _init(htOptions || {});
    };

})("yona.project.Delete");
