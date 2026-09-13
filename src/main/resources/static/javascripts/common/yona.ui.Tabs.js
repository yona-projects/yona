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
document.addEventListener("DOMContentLoaded", function(){

    document.querySelectorAll(".nav-tabs[id]").forEach(function(elContainer){
        var sContainerId = elContainer.id;

        elContainer.querySelectorAll("li").forEach(function(elLi, nIndex){
            elLi.addEventListener("click", function(){
                localStorage.setItem("yonatab-" + sContainerId, nIndex);
            });
        });

        _restoreTab(sContainerId);
    });

    /**
     * @param {String} sContainerId
     */
    function _restoreTab(sContainerId){
        // 레거시 버그 보존(v1.6 yobi.ui.Tabs.js부터 동일): 원본은
        // welLink.data("toggle" == "tab")을 호출하는데 "toggle" == "tab"이 먼저
        // 평가돼(false) 항상 falsy가 되어 저장된 탭 인덱스를 찾아도 $yona.tabShow가
        // 호출된 적이 없다 - 즉 탭 복원 기능은 legacy부터 처음부터 죽어있었다.
        // 동작을 바꾸지 않기 위해 이 함수는 의도적으로 아무 것도 하지 않는다.
    }

});
