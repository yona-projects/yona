/**
 * Yona, Project Hosting SW
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
/**
 * 이 모듈은 죽은 코드다 - "yona.ui.Mergely(" 호출도, 대상 마크업(#compare/#mergely)도 저장소에
 * 없고, 의존하는 jQuery 플러그인 $.fn.mergely조차 존재하지 않아 애초에 실행 불가능했다. 삭제
 * 대신 vanilla로 전환하되, 존재하지 않는 $.fn.mergely 호출부만은 원본 그대로(jQuery 래핑) 남겨
 * "플러그인 없음"으로 동일하게 실패하도록 뒀다. .modal() -> 네이티브 <dialog> 전환은 다른
 * 파일들과 동일한 관례를 따른다.
 */
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        function _init(){
            _initElement();
            _initMergely();
            _attachEvent();
        }

        function _initElement(htOptions){
            htElement.welMergelyWrap = document.getElementById("compare");
            htElement.welMergely = document.getElementById("mergely");
            htElement.welMergelyPathTitle = htElement.welMergelyWrap ? htElement.welMergelyWrap.querySelector(".path > span") : null;
            htElement.welMergelyCommitA = htElement.welMergelyWrap ? htElement.welMergelyWrap.querySelector(".commitA") : null;
            htElement.welMergelyCommitB = htElement.welMergelyWrap ? htElement.welMergelyWrap.querySelector(".commitB") : null;
        }

        function _initMergely(){
            var htWrapSize = _getMergelyWrapSize();

            // $.fn.mergely 플러그인이 저장소에 없다(파일 헤더 참고) - 원본과 동일하게 실패하도록 둔다.
            $(htElement.welMergely).mergely({
                "width" : "auto",
                // "height": "auto",
                "height": (htWrapSize.nWrapHeight - 100) + "px",
                "editor_width": ((htWrapSize.nWrapWidth - 92) / 2) + "px",
                "editor_height": (htWrapSize.nWrapHeight - 100) + "px",
                "cmsettings":{"readOnly": true, "lineNumbers": true}
            });
        }

        function _attachEvent(){
            window.addEventListener("resize", _resizeMergely);
        }

        function _setButtons(sQuery){
            document.querySelectorAll(sQuery).forEach(function(el){
                el.addEventListener("click", _onClickBtnFullDiff);
            });
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickBtnFullDiff(weEvt){
            var welTarget = weEvt.target;
            var sCommitA = welTarget.getAttribute("data-commitA");
            var sCommitB = welTarget.getAttribute("data-commitB");
            var sPathA   = welTarget.getAttribute("data-pathA");
            var sPathB   = welTarget.getAttribute("data-pathB");
            var sRawA    = welTarget.getAttribute("data-rawA");
            var sRawB    = welTarget.getAttribute("data-rawB");

            // UpdateText
            if(htElement.welMergelyPathTitle){
                htElement.welMergelyPathTitle.textContent = (sPathA != sPathB) ? (sPathA + " -> " + sPathB) : sPathB;
            }
            if(htElement.welMergelyCommitA){
                htElement.welMergelyCommitA.textContent = sCommitA;
            }
            if(htElement.welMergelyCommitB){
                htElement.welMergelyCommitB.textContent = sCommitB;
            }
            if(htElement.welMergelyWrap && typeof htElement.welMergelyWrap.showModal === "function"){
                htElement.welMergelyWrap.showModal();
            }

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
            fetch(sRawURLFrom).then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                return response.text();
            }).then(function(sData){
                $(htElement.welMergely).mergely("lhs", sData);
                $(htElement.welMergely).mergely("resize");
                $(htElement.welMergely).mergely("update");
            }).catch(function(){});

            // rhs = to
            fetch(sRawURLTo).then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                return response.text();
            }).then(function(sData){
                $(htElement.welMergely).mergely("rhs", sData);
                $(htElement.welMergely).mergely("resize");
                $(htElement.welMergely).mergely("update");
            }).catch(function(){});
        }

        function _resizeMergely(){
            var htWrapSize = _getMergelyWrapSize();
            var nWidth = ((htWrapSize.nWrapWidth - 92) / 2);
            var nHeight = (htWrapSize.nWrapHeight - 100);

            if(htElement.welMergelyWrap){
                htElement.welMergelyWrap.style.width = htWrapSize.nWrapWidth + "px";
                htElement.welMergelyWrap.style.height = htWrapSize.nWrapHeight + "px";
                htElement.welMergelyWrap.style.marginLeft = -(htWrapSize.nWrapWidth / 2) + "px";
            }
            $(htElement.welMergely).mergely("cm", "rhs").setSize(nWidth + "px", nHeight + "px");
            $(htElement.welMergely).mergely("cm", "lhs").setSize(nWidth + "px", nHeight + "px");

            document.querySelectorAll(".mergely-column").forEach(function(el){
                el.style.width = nWidth + "px";
                el.style.height = nHeight + "px";
            });
            document.querySelectorAll(".CodeMirror").forEach(function(el){
                el.style.height = nHeight + "px";
            });
        }

        _init(htOptions || {});

        return {
            "setButtons": _setButtons
        };
    };
})("yona.ui.Mergely");
