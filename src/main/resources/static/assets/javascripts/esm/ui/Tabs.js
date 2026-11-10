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

// ES 모듈은 문서 분석이 끝난 뒤 DOMContentLoaded 전에 실행되며, 아래 준비 완료 처리기는 그때 등록되어 문서가 준비되면 돈다.
/**
 * yona.ui.Tabs
 *
 * `id`가 있는 탭 목록(`.nav-tabs[id]`)에서 고른 탭의 순서를 localStorage(`yobitab-<id>`)에 저장하고, 다음에 화면을 열 때 그 탭을 보여 준다.
 *
 * v1.6은 복원 조건이 `welLink.data("toggle" == "tab")`(`"toggle" == "tab"`이 거짓이라 `data(false)`)여서 저장만 하고 복원하지 않았다(F-02, R-139).
 * 또 탭 목록이 둘 이상이면 모든 클릭이 마지막 목록의 `id`로 저장되었다(목록마다 다른 변수를 쓰지 않았다). 둘 다 고쳤다(D-111).
 *
 * - 복원할 탭은 Bootstrap 5의 탭 요소(`data-bs-toggle="tab"`인 `a`나 `button`)만 해당한다. v1.6의 `data-toggle="tab"`도 받는다.
 * - 서버가 탭을 정해 준 화면(주소에 `tab=`, 예: 설정을 저장하고 돌아오는 화면)에서는 저장된 값으로 덮어쓰지 않는다.
 * - localStorage를 쓸 수 없으면(사이트 데이터 차단) 조용히 넘어간다.
 */
$(document).ready(() =>{

    $(".nav-tabs[id]").each((i, elContainer) =>{
        const welContainer = $(elContainer);
        const sContainerId = welContainer.attr("id");

        welContainer.find("li").click(function(){
            _store(sContainerId, $(this).index());
        });
        _restoreTab(sContainerId);
    });

    /**
     * @param {String} sContainerId
     * @param {Number} nIndex
     */
    function _store(sContainerId, nIndex){
        try {
            localStorage.setItem("yobitab-" + sContainerId, nIndex);
        } catch (err) {
            // 저장할 수 없으면 기억하지 않는다
        }
    }

    /**
     * 저장된 순서의 탭을 보여 준다.
     *
     * @param {String} sContainerId
     */
    function _restoreTab(sContainerId){
        if(/[?&]tab=/.test(window.location.search)){
            return;
        }

        let nIndex;
        try {
            nIndex = localStorage.getItem("yobitab-" + sContainerId);
        } catch (err) {
            return;
        }

        const waItems = $("#" + sContainerId).find("li > a, li > button");
        if(nIndex === null || !waItems[nIndex]){
            return;
        }

        const welLink = $(waItems[nIndex]);
        if(welLink.data("toggle") === "tab" || welLink.data("bsToggle") === "tab"){
            $yona.showTab(welLink);
        }
    }

});
