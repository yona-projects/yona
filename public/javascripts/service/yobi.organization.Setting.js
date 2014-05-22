/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Deokhong Kim
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
    oNS.container[oNS.name] = function(){

        var htElement = {};

        /**
         * initialize
         */
        function _init(){
            _initElement();
            _attachEvent();
        }

        /**
         * initialize element variables
         */
        function _initElement(){
            // 프로젝트 설정 관련
            htElement.welForm = $("form#saveSetting");
            htElement.welInputLogo = $("#logoPath");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welInputLogo.change(_onChangeLogoPath);
        }

        /**
         * 프로젝트 로고 변경시 이벤트 핸들러
         */
        function _onChangeLogoPath(){
            var welTarget = $(this);

            if($yobi.isImageFile(welTarget) === false){
                $yobi.showAlert(Messages("project.logo.alert"));
                welTarget.val('');
                return;
            }

            htElement.welForm.submit();
        }

        _init();
    };

})("yobi.organization.Setting");
