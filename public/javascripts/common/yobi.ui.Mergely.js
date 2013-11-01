/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
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
         * 초기화
         */
        function _init(){
            _initElement();
            _initMergely();
            _attachEvent();
        }

        /**
         * 엘리먼트 변수 초기화
         */        
        function _initElement(htOptions){
            htElement.welMergelyWrap = $("#compare");
            htElement.welMergely = $("#mergely");
            htElement.welMergelyPathTitle = htElement.welMergelyWrap.find(".path > span");
            htElement.welMergelyCommitA = htElement.welMergelyWrap.find(".commitA");
            htElement.welMergelyCommitB = htElement.welMergelyWrap.find(".commitB");
        }
        
        /**
         * Mergely 초기화
         */
        function _initMergely(){
            var htWrapSize = _getMergelyWrapSize();
    
            htElement.welMergely.mergely({
                "width" : "auto",
                // "height": "auto",
                "height": (htWrapSize.nWrapHeight - 100) + "px",
                "editor_width": ((htWrapSize.nWrapWidth - 92) / 2) + "px",
                "editor_height": (htWrapSize.nWrapHeight - 100) + "px",
                "cmsettings":{"readOnly": true, "lineNumbers": true}
            });
        }
        
        /**
         * 이벤트 핸들러 초기화
         */
        function _attachEvent(){
            $(window).on("resize", _resizeMergely);
        }
        
        /**
         * fullDiff 버튼의 셀렉터를 제공하면
         * _onClickBtnFullDiff 함수를 click 이벤트 핸들러로 지정한다
         */
        function _setButtons(sQuery){
            $(sQuery).on("click", _onClickBtnFullDiff);
        }

        /**
         * fullDiff 버튼 클릭시 이벤트 핸들러
         * 
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnFullDiff(weEvt){
            var welTarget = $(weEvt.target);
            var sCommitA = welTarget.attr("data-commitA");
            var sCommitB = welTarget.attr("data-commitB");
            var sPathA   = welTarget.attr("data-pathA");
            var sPathB   = welTarget.attr("data-pathB");
            var sRawA    = welTarget.attr("data-rawA");
            var sRawB    = welTarget.attr("data-rawB");
            
            // UpdateText
            htElement.welMergelyPathTitle.text((sPathA != sPathB) ? (sPathA + " -> " + sPathB) : sPathB);
            htElement.welMergelyCommitA.text(sCommitA);
            htElement.welMergelyCommitB.text(sCommitB);
            htElement.welMergelyWrap.modal();

            _resizeMergely();
            _updateMergely(sRawA, sRawB);
        }

        /**
         * Mergely wrapper 크기 반환
         */
        function _getMergelyWrapSize(){
            return {
                "nWrapWidth" : window.innerWidth - 100,
                "nWrapHeight": window.innerHeight - (window.innerHeight * 0.2)
            };
        }

        /**
         * 두 코드를 가져다 fullDiff 에 표시하는 함수
         * 
         * @param {String} sRawURLFrom
         * @param {String} sRawURLTo
         */
        function _updateMergely(sRawURLFrom, sRawURLTo){
            // lhs = from
            $.get(sRawURLFrom).done(function(sData){
                htElement.welMergely.mergely("lhs", sData);
                htElement.welMergely.mergely("resize");
                htElement.welMergely.mergely("update");
            });
            
            // rhs = to
            $.get(sRawURLTo).done(function(sData){
                htElement.welMergely.mergely("rhs", sData);
                htElement.welMergely.mergely("resize");
                htElement.welMergely.mergely("update");
            });
        }
        
        /**
         * Mergely 영역 크기 조절
         */
        function _resizeMergely(){
            var htWrapSize = _getMergelyWrapSize();
            var nWidth = ((htWrapSize.nWrapWidth - 92) / 2);
            var nHeight = (htWrapSize.nWrapHeight - 100);
            
            htElement.welMergelyWrap.css({
                "width" : htWrapSize.nWrapWidth + "px",
                "height": htWrapSize.nWrapHeight + "px",
                "margin-left": -(htWrapSize.nWrapWidth / 2) + "px"
            });
            htElement.welMergely.mergely("cm", "rhs").setSize(nWidth + "px", nHeight + "px");
            htElement.welMergely.mergely("cm", "lhs").setSize(nWidth + "px", nHeight + "px");
            
            $(".mergely-column").width(nWidth).height(nHeight);
            $(".CodeMirror").height(nHeight);
        }
        
        _init(htOptions || {});
        
        return {
            "setButtons": _setButtons
        };
    };
})("yobi.ui.Mergely");
