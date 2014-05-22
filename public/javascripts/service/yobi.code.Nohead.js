/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author JiHan Kim
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

        /**
         * Initialize
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initVar(htOptions);
            _attachEvent();
        }

        /**
         * 변수 초기화 함수
         * initialize variables
         * @param {Hash Table} htOptions
         */
        function _initVar(htOptions){
            htVar.sPath = 'code/HEAD/!/';
            htVar.nInterval = htOptions.nInterval || 5000; // ms
            htVar.nTimer = null;
        }

        /**
         * 이벤트 초기화 함수
         * attach event handlers
         */
        function _attachEvent(){
            $(window).on("focus", _onFocusWindow);
            $(window).on("blur", _onBlurWindow);
        }

        /**
         * 저장소의 파일 목록을 확인하는 함수
         * check is repository has updated
         */
        function _checkUpdate(){
            $.ajax({
                "url": htVar.sPath,
                "success": _onLoadList
            });
        }

        /**
         * 파일 목록 응답이 정상적으로 올 때 이벤트 핸들러
         * _checkUpdate 에서 사용함
         */
        function _onLoadList(){
            document.location.reload();
        }

        /**
         * window 객체의 focus 이벤트 핸들러
         * 포커스가 돌아오면 업데이트 여부 판단하고 polling 시작
         */
        function _onFocusWindow(){
            _checkUpdate();

            if(!htVar.nTimer){
                htVar.nTimer = setInterval(_checkUpdate, htVar.nInterval);
            }
        }

        /**
         * window 객체의 blur 이벤트 핸들러
         * 포커스를 잃으면 polling 중단
         */
        function _onBlurWindow(){
            clearInterval(htVar.nTimer);
            htVar.nTimer = null;
        }

        // 초기화
        _init(htOptions);
    };

})("yobi.code.Nohead");
