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
        var htElement = {};

        function _init(){
            _initElement();
            _initMergely();
            _attachEvent();
        }

        function _initElement(htOptions){
            htElement.welMergelyWrap = $("#compare");
            htElement.welMergely = $("#mergely");
            htElement.welMergelyPathTitle = htElement.welMergelyWrap.find(".path > span");
            htElement.welMergelyCommitA = htElement.welMergelyWrap.find(".commitA");
            htElement.welMergelyCommitB = htElement.welMergelyWrap.find(".commitB");
        }

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

        function _attachEvent(){
            $(window).on("resize", _resizeMergely);
        }

        function _setButtons(sQuery){
            $(sQuery).on("click", _onClickBtnFullDiff);
        }

        /**
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

        function _getMergelyWrapSize(){
            return {
                "nWrapWidth" : window.innerWidth - 100,
                "nWrapHeight": window.innerHeight - (window.innerHeight * 0.2)
            };
        }

        /**
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
