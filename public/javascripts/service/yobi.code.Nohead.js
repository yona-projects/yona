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
         * initialize variables
         * @param {Hash Table} htOptions
         */
        function _initVar(htOptions){
            htVar.sPath = 'code/HEAD/!/';
            htVar.nInterval = htOptions.nInterval || 5000; // ms
            htVar.nTimer = null;
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            $(window).on("focus", _onFocusWindow);
            $(window).on("blur", _onBlurWindow);
        }

        /**
         * check is repository has updated
         */
        function _checkUpdate(){
            $.ajax({
                "url": htVar.sPath,
                "success": _onLoadList
            });
        }

        /**
         * _checkUpdate 에서 사용함
         */
        function _onLoadList(){
            document.location.reload();
        }

        function _onFocusWindow(){
            _checkUpdate();

            if(!htVar.nTimer){
                htVar.nTimer = setInterval(_checkUpdate, htVar.nInterval);
            }
        }

        function _onBlurWindow(){
            clearInterval(htVar.nTimer);
            htVar.nTimer = null;
        }

        // 초기화
        _init(htOptions);
    };

})("yobi.code.Nohead");
