/**
 * Yona, 21st Century Project Hosting SW
 *
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 *
 * static/bootstrap/js/bootstrap-switch.js(벤더, 수정 금지)의 $.fn.bootstrapSwitch 자동
 * 초기화를 대체하는 vanilla 구현이다.
 *
 * 유일한 실사용처는 templates/user/edit_notifications.html의 알림 on/off 토글이며,
 * 그 화면이 안 쓰는 색상 변형·아이콘·애니메이션-끄기 옵션은 이식하지 않았다 -
 * data-on-label/data-off-label만 지원한다. service/yona.user.Setting.js가 이미
 * .notiUpdate 체크박스에 change 리스너를 걸어 서버 토글 요청을 보내므로, 이 파일은
 * 스위치 위젯 껍데기와 클릭/키보드 토글 상호작용만 담당한다.
 *
 * 드래그 슬라이드 애니메이션(벤더의 mousemove/touchmove 실시간 추적)은 재현하지 않았다 -
 * 유일한 사용처가 체크박스 하나뿐이라 클릭/키보드로 충분하고, CSS `.switch-animate`의
 * transition으로 시각 전환은 그대로 유지된다.
 */
(function(){
    "use strict";

    /**
     * 진짜 jQuery + bootstrap-switch.js가 아직 로드된 전환 기간에는 벤더의 자동 초기화와
     * 중복 실행되면 같은 체크박스가 두 번 감싸지는 회귀가 생기므로, 진짜 jQuery가 있으면
     * 스스로 비활성화한다.
     */
    function _isRealJQueryStillLoaded(){
        return !!(window.jQuery && window.jQuery.fn);
    }

    /**
     * bootstrap-switch.js의 init 메서드를 vanilla로 재구현.
     *
     * @param {Element} elCheckbox [data-toggle="switch"] 체크박스 자신(별도 트리거
     *   엘리먼트가 아니다 - bootstrap.js의 [data-toggle="dropdown"]과 달리 이 속성은
     *   토글 대상 input 자체에 붙는다).
     */
    function _wrapOne(elCheckbox){
        if(elCheckbox.tagName !== "INPUT" || elCheckbox.type !== "checkbox"){
            return;
        }
        if(elCheckbox._yonaSwitch){
            return; // idempotent - 동적 재삽입 대비 가드(el._yonaRequestAs와 동일 관례)
        }

        var elOuter = elCheckbox.closest(".switch");
        if(!elOuter){
            return;
        }
        elOuter.classList.add("has-switch");

        // 매치되는 마지막 클래스가 이긴다(원본이 순회하며 매번 덮어쓰므로) - 이 앱은
        // 셋 다 안 쓰지만 알고리즘은 그대로 이식한다.
        var sSizeClass = "";
        ["switch-mini", "switch-small", "switch-large"].forEach(function(sClass){
            if(elOuter.classList.contains(sClass)){
                sSizeClass = sClass;
            }
        });

        // data-icon은 이 템플릿에서 안 쓰여 이식하지 않았다.
        var sOnLabel  = elOuter.hasAttribute("data-on-label")  ? elOuter.getAttribute("data-on-label")  : "ON";
        var sOffLabel = elOuter.hasAttribute("data-off-label") ? elOuter.getAttribute("data-off-label") : "OFF";

        var elSwitchLeft = document.createElement("span");
        elSwitchLeft.classList.add("switch-left");
        if(sSizeClass){ elSwitchLeft.classList.add(sSizeClass); }
        elSwitchLeft.innerHTML = sOnLabel;

        var elSwitchRight = document.createElement("span");
        elSwitchRight.classList.add("switch-right");
        if(sSizeClass){ elSwitchRight.classList.add(sSizeClass); }
        elSwitchRight.innerHTML = sOffLabel;

        // for 속성은 체크박스에 id가 있을 때만 단다 - 원본의 .attr('for', undefined)는
        // jQuery에서 getter로 동작해 아무 것도 설정하지 않으므로(id 없는 체크박스라 원본에서도
        // for가 전혀 안 붙었다), 그 동작을 그대로 재현한다.
        var elLabel = document.createElement("label");
        elLabel.innerHTML = "&nbsp;";
        if(sSizeClass){ elLabel.classList.add(sSizeClass); }
        if(elCheckbox.id){
            elLabel.setAttribute("for", elCheckbox.id);
        }

        var elInner = document.createElement("div");
        elCheckbox.parentNode.insertBefore(elInner, elCheckbox);
        elInner.appendChild(elCheckbox);

        // data-animated가 명시적으로 "false"가 아니면 switch-animate를 붙인다 - CSS의
        // transition: left 0.25s ease-out이 여기 걸린다.
        if(elOuter.getAttribute("data-animated") !== "false"){
            elInner.classList.add("switch-animate");
        }

        // 자식 순서는 [checkbox, switchLeft, label, switchRight]다 - 벤더 소스와 정확히
        // 일치시켰다(다른 순서로 짜기 쉬우니 주의).
        elInner.appendChild(elSwitchLeft);
        elInner.appendChild(elLabel);
        elInner.appendChild(elSwitchRight);

        elInner.classList.add(elCheckbox.checked ? "switch-on" : "switch-off");

        if(elCheckbox.disabled){
            elOuter.classList.add("deactivate");
        }

        // 벤더 원본은 스페이스바 keydown 리스너를 걸지만 tabindex를 준 적이 없어 실제로는
        // 키보드로 도달 불가능한 죽은 코드였다(마우스/터치 전용). 접근성을 위해 tabindex="0"을
        // 추가해 키보드 포커스가 가능하도록 했고, role="checkbox"/aria-checked도 함께 부여했다.
        if(!elOuter.hasAttribute("tabindex")){
            elOuter.setAttribute("tabindex", "0");
        }
        elOuter.setAttribute("role", "checkbox");
        elOuter.setAttribute("aria-checked", elCheckbox.checked ? "true" : "false");

        elCheckbox._yonaSwitch = true;
    }

    /**
     * 클릭/스페이스바 공통 토글 로직. 벤더 원본의 드래그 관련 좌표 계산을 걷어내고
     * "checked 반전 → on/off 클래스 전환 → change 이벤트 dispatch"만 남긴 의도적 단순화다.
     *
     * 참고(기존 동작 그대로 보존, 새 버그 아님): service/yona.user.Setting.js의
     * _onChangeNotiSwitch가 서버 요청 실패 시 체크박스를 일반 프로퍼티 대입으로 되돌리는데,
     * 이는 change 이벤트를 발생시키지 않는다 - 원본도 change 이벤트에만 반응해 on/off
     * 클래스를 갱신했으므로, 이 에러 복구 경로는 "checked 값은 되돌아가지만 스위치 시각
     * 상태는 안 바뀌는" 낙후된 동작이 원본과 동일하게 남아있다.
     */
    function _toggle(elOuter){
        if(elOuter.classList.contains("deactivate")){
            return;
        }
        var elCheckbox = elOuter.querySelector('input[type="checkbox"]');
        if(!elCheckbox){
            return;
        }

        elCheckbox.checked = !elCheckbox.checked;

        var elInner = elCheckbox.parentElement;
        if(elInner){
            elInner.classList.remove(elCheckbox.checked ? "switch-off" : "switch-on");
            elInner.classList.add(elCheckbox.checked ? "switch-on" : "switch-off");
        }
        elOuter.setAttribute("aria-checked", elCheckbox.checked ? "true" : "false");

        elCheckbox.dispatchEvent(new Event("change", {"bubbles": true}));
    }

    function _initAll(){
        if(_isRealJQueryStillLoaded()){
            return;
        }
        document.querySelectorAll('[data-toggle="switch"]').forEach(_wrapOne);
    }

    document.addEventListener("DOMContentLoaded", _initAll);

    // switch-left/switch-right/label 클릭이 모두 같은 토글로 귀결되므로 하나의
    // 델리게이트로 합쳤다.
    document.addEventListener("click", function(weEvt){
        if(_isRealJQueryStillLoaded()){
            return;
        }
        var elClickable = weEvt.target.closest(".has-switch .switch-left, .has-switch .switch-right, .has-switch label");
        if(!elClickable){
            return;
        }
        var elOuter = elClickable.closest(".has-switch");
        if(!elOuter){
            return;
        }
        weEvt.preventDefault();
        _toggle(elOuter);
    });

    // 스페이스바(keyCode 32) 토글 - 위 접근성 보완(tabindex 추가)으로 실제 도달 가능해졌다.
    document.addEventListener("keydown", function(weEvt){
        if(_isRealJQueryStillLoaded()){
            return;
        }
        if(weEvt.keyCode !== 32){
            return;
        }
        var elOuter = weEvt.target.closest(".has-switch");
        if(!elOuter){
            return;
        }
        weEvt.preventDefault();
        _toggle(elOuter);
    });
})();
