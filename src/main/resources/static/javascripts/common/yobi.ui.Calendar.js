/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Changgun Kim
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
 * yobi.ui.Calendar
 *
 * @requires Flatpickr (https://flatpickr.js.org/) — Pikaday를 대체(P3-46 #1).
 *           공개 API(getDate/setDate)와 마크업 계약(data-toggle="calendar", 다음 형제
 *           .btn-calendar 클릭 시 열림, 입력 필드 값 "YYYY-MM-DD" 포맷 동기화)은 기존과
 *           동일하게 유지한다.
 */

(function(ns){
    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(element, userOptions){

        var targetElement;

        function _init(element, userOptions){
            targetElement = $(element);
            userOptions = userOptions || {};

            var options = {
                "dateFormat": "Y-m-d",
                // Pikaday는 필드에 자유롭게 타이핑할 수 있었다(bound 필드의 'change' 이벤트로
                // 재파싱). Flatpickr는 기본값(allowInput:false)이면 키보드 입력을 막으므로
                // 동일 동작을 위해 명시적으로 켠다.
                "allowInput": true
            };

            if (!targetElement.data("flatpickr")){
                var picker = flatpickr(targetElement.get(0), options);
                targetElement.data("flatpickr", picker);
            }

            targetElement.next(".btn-calendar").on("click", function(){
                targetElement.data("flatpickr").open();
            });

            if(targetElement.val().length > 0 && userOptions.silent !== true){
                // 페이지 로드 시 이미 채워진 필드 값을 캘린더 내부 상태에 맞춰주는 것뿐이라
                // 실제 값 변경이 아니다 — change 이벤트를 발생시키면 issue/view.html의
                // 마감일 자동저장 위젯이 페이지를 열 때마다 조용히 재저장을 시도하게 된다
                // (P3-46 #1 범위 밖 발견, 사용자 확인 후 이 초기화 경로에서만 수정).
                // 사용자/다른 코드가 명시적으로 부르는 공개 setDate()는 기존처럼 change를 발생시킨다.
                targetElement.data("flatpickr").setDate(targetElement.val(), false, "Y-m-d");
            }
        }

        function _getDate(){
            var picker = targetElement.data("flatpickr");
            return (picker.selectedDates && picker.selectedDates.length > 0) ? picker.selectedDates[0] : null;
        }

        function _setDate(dateStr){
            // Pikaday.setDate()는 필드 값 갱신과 함께 항상 필드에 네이티브 'change' 이벤트를
            // 발생시켰다(issue/view.html의 마감일 자동저장 위젯이 이 이벤트에 의존한다) — 두
            // 번째 인자 true로 Flatpickr에서도 동일하게 onChange(=change/input 이벤트 디스패치)가
            // 발생하도록 한다.
            return targetElement.data("flatpickr").setDate(dateStr, true, "Y-m-d");
        }

        _init(element, userOptions || {});

        return {
            "getDate": _getDate,
            "setDate": _setDate
        };
    };

})("yobi.ui.Calendar");

$(function(){
    $('[data-toggle="calendar"]').each(function(i, el){
        yobi.ui.Calendar(el, $(el).data());
    });
});
