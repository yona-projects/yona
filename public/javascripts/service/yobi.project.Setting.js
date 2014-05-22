/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jihan Kim
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
            var htOpt = htOptions || {};
            _initVar(htOpt);
            _initElement(htOpt);
            _attachEvent();

            htVar.waPopOvers.popover();
        }

        /**
         * initialize variables
         * 정규식 변수는 한번만 선언하는게 성능 향상에 도움이 됩니다
         */
        function _initVar(htOptions){
            htVar.rxPrjName = /^[0-9A-Za-z-_\.]+$/;
            htVar.aReservedWords = [".", "..", ".git"];
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            // 프로젝트 설정 관련
            htElement.welForm = $("form#saveSetting");
            htElement.welInputLogo = $("#logoPath");
            htElement.welInputName = $("input#project-name");
            htElement.welBtnSave   = $("#save");
            htElement.welReviewerCount = $("#welReviewerCount");

            // popovers
            htVar.waPopOvers = $([$("#project_name"), $("#share_option_explanation"), $("#terms")]);
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welInputLogo.change(_onChangeLogoPath);
            htElement.welBtnSave.click(_onClickBtnSave);

            if(htElement.welReviewerCount.data("value") === true) {
                htElement.welReviewerCount.show();
            }

            $(".reviewer-count-wrap").on("click", '[data-toggle="reviewer-count"]', _toggleReviewerCount);
        }

        /**
         * 리뷰어 기능 사용 여부를 토글 한다.
         */
        function _toggleReviewerCount(){
            var sAction = $(this).data("action");
            htElement.welReviewerCount[sAction]();
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

        /**
         * 프로젝트 설정 저장 버튼 클릭시
         */
        function _onClickBtnSave(){
            var sPrjName = htElement.welInputName.val();
            if(!htVar.rxPrjName.test(sPrjName)){
                $yobi.showAlert(Messages("project.name.alert"));
                return false;
            }
            if(htVar.aReservedWords.indexOf(sPrjName) >= 0){
                $yobi.showAlert(Messages("project.name.reserved.alert"));
                return false;
            }

            return true;
        }

        _init(htOptions);
    };

})("yobi.project.Setting");
