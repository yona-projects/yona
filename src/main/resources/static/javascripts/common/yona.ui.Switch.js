/**
 * Yona, 21st Century Project Hosting SW
 *
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 *
 * P3-70 라운드12: static/bootstrap/js/bootstrap-switch.js(v1.3, 251줄, lib 성격의 벤더 파일,
 * 절대 수정하지 않음)의 `$.fn.bootstrapSwitch` 자동 초기화(`$(function(){
 * $('.switch').bootstrapSwitch(); })`)를 대체하는 vanilla 구현이다. 이 파일이 곧
 * jQuery 코어 제거의 마지막 블로커였다(라운드10~11 완료 로그 참고).
 *
 * 유일한 실사용처는 templates/user/edit_notifications.html의 알림 on/off 토글:
 *   <div class="switch" data-on-label="On" data-off-label="Off">
 *     <input class="notiUpdate" type="checkbox" data-toggle="switch" ...>
 *   </div>
 * data-on/data-off/data-icon/data-animated는 이 템플릿에서 전혀 안 쓰여(grep 재확인)
 * 색상 변형·아이콘·애니메이션-끄기 옵션은 이식하지 않았다 - data-on-label/data-off-label
 * 만 지원한다. service/yona.user.Setting.js(76~94행)가 이미 .notiUpdate 체크박스에
 * 네이티브 change 리스너를 걸어 서버로 토글 요청을 보내므로, 이 파일이 새로 만들어야
 * 하는 건 "시각적 스위치 위젯 껍데기 + 클릭/키보드로 체크박스를 토글하는 상호작용"뿐이다.
 *
 * 드래그 슬라이드 애니메이션(벤더 원본의 mousemove/touchmove로 실시간 위치를 따라가는
 * 부분)은 의도적으로 재현하지 않았다 - 코디네이터 지시(P3-70 라운드12 프롬프트)에 따라,
 * 이 앱의 유일한 사용처가 좁은 설정 페이지의 체크박스 하나뿐이라 클릭/키보드만으로 충분히
 * 대체 가능하다고 판단했고, CSS(stylesheets/yona.css:11093-11098)의 `.switch-animate`가
 * 이미 `transition: left 0.25s ease-out`을 정의하고 있어 클래스 전환만으로도 부드러운
 * 시각 전환이 유지된다. Playwright로 클릭 기반 토글이 실제 서버 왕복(.notiUpdate의 change
 * 리스너 → PATCH/POST → 새로고침 없이 실제로 알림 설정이 바뀜)까지 정상 동작함을 확인했다.
 */
(function(){
    "use strict";

    /**
     * 라운드11 common/yona.Common.js의 동일 이름 가드와 완전히 같은 이유 - jQuery 코어를
     * 실제로 제거하기 전 검증 단계(진짜 jQuery + bootstrap-switch.js가 계속 로드된 채
     * 남아있는 동안)에는 이 신규 구현이 벤더 플러그인의 자동 초기화와 중복 실행되면 같은
     * 체크박스를 두 번 감싸는 이중 마크업 회귀가 생기므로, 진짜 jQuery가 있으면 스스로
     * 완전히 비활성화한다.
     */
    function _isRealJQueryStillLoaded(){
        return !!(window.jQuery && window.jQuery.fn);
    }

    /**
     * bootstrap-switch.js 9-108행(init 메서드)의 vanilla 재구현.
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

        // 34행: $element.addClass('has-switch') - $element는 .switch 컨테이너 자신이다.
        var elOuter = elCheckbox.closest(".switch");
        if(!elOuter){
            return;
        }
        elOuter.classList.add("has-switch");

        // 29-32행: switch-mini/switch-small/switch-large 중 매치되는 마지막 것이 이긴다
        // ($.each가 끝까지 순회하며 매번 덮어쓰기 때문 - break 없음). 이 앱은 셋 다 안 쓰지만
        // (grep 재확인 0건) 알고리즘은 그대로 이식한다.
        var sSizeClass = "";
        ["switch-mini", "switch-small", "switch-large"].forEach(function(sClass){
            if(elOuter.classList.contains(sClass)){
                sSizeClass = sClass;
            }
        });

        // 39-43행: data-on-label/data-off-label (data-on/data-off 색상, data-icon은 이
        // 템플릿에서 전혀 안 쓰여 이식하지 않았다).
        var sOnLabel  = elOuter.hasAttribute("data-on-label")  ? elOuter.getAttribute("data-on-label")  : "ON";
        var sOffLabel = elOuter.hasAttribute("data-off-label") ? elOuter.getAttribute("data-off-label") : "OFF";

        // 48-52행
        var elSwitchLeft = document.createElement("span");
        elSwitchLeft.classList.add("switch-left");
        if(sSizeClass){ elSwitchLeft.classList.add(sSizeClass); }
        elSwitchLeft.innerHTML = sOnLabel;

        // 58-62행
        var elSwitchRight = document.createElement("span");
        elSwitchRight.classList.add("switch-right");
        if(sSizeClass){ elSwitchRight.classList.add(sSizeClass); }
        elSwitchRight.innerHTML = sOffLabel;

        // 64-71행: icon은 이 템플릿에서 안 쓰이므로 항상 "&nbsp;" 분기만 이식.
        // for 속성은 체크박스에 id가 있을 때만 단다 - 원본 `.attr('for', $element.find('input')
        // .attr('id'))`는 id가 없으면 undefined를 두 번째 인자로 넘기는 셈인데, jQuery의
        // .attr(name, value)는 value가 undefined면 setter가 아니라 getter로 동작해(내부
        // access() 헬퍼가 value == null을 setter 분기 조건에서 제외) 아무 것도 설정하지
        // 않는다 - 이 템플릿의 체크박스는 id가 없으므로(grep 재확인) 원본에서도 for가 전혀
        // 안 붙었다.
        var elLabel = document.createElement("label");
        elLabel.innerHTML = "&nbsp;";
        if(sSizeClass){ elLabel.classList.add(sSizeClass); }
        if(elCheckbox.id){
            elLabel.setAttribute("for", elCheckbox.id);
        }

        // 73행: $element.find(':checkbox').wrap($('<div>')).parent() - 체크박스가 있던
        // 자리에 새 div를 끼워 넣고, 체크박스를 그 안으로 옮긴다.
        var elInner = document.createElement("div");
        elCheckbox.parentNode.insertBefore(elInner, elCheckbox);
        elInner.appendChild(elCheckbox);

        // 75-76행: data-animated가 명시적으로 "false"가 아니면 switch-animate(이 앱은
        // data-animated를 전혀 안 써 항상 참 - CSS의 transition: left 0.25s ease-out이
        // 여기 걸린다).
        if(elOuter.getAttribute("data-animated") !== "false"){
            elInner.classList.add("switch-animate");
        }

        // 78-81행: append 호출 순서 그대로 - .wrap() 직후 elInner의 유일한 자식은 체크박스뿐이므로
        // 최종 자식 순서는 [checkbox, switchLeft, label, switchRight]다(코디네이터가 준 근사
        // 스니펫의 [switchLeft, checkbox, label, switchRight] 순서가 아니다 - 벤더 소스와
        // 라인 단위로 대조해 정확한 순서로 이식했다).
        elInner.appendChild(elSwitchLeft);
        elInner.appendChild(elLabel);
        elInner.appendChild(elSwitchRight);

        // 83-85행: 초기 on/off 클래스.
        elInner.classList.add(elCheckbox.checked ? "switch-on" : "switch-off");

        // 87-88행: disabled면 deactivate(이 앱의 유일한 사용처는 disabled 체크박스가 없어
        // grep 재확인 결과 항상 도달하지 않는 분기지만, 향후 대비해 그대로 이식).
        if(elCheckbox.disabled){
            elOuter.classList.add("deactivate");
        }

        // 접근성(코디네이터 지시, P3-70 라운드12): 벤더 원본은 94-100행에서 $element(.switch
        // 컨테이너)에 스페이스바 keydown 리스너를 걸지만, 정작 그 어디에도 tabindex를 준 적이
        // 없다 - 체크박스 자체는 CSS로 display:none이라 원래도 tab 순서에서 빠지고, 컨테이너
        // div/span/label도 네이티브로는 포커스를 받을 수 없어 이 keydown은 실제로는 키보드로
        // 절대 도달할 수 없는 죽은 코드였다(마우스/터치로만 상호작용 가능했던 셈). "완전 동치"
        // 원칙이라면 이 죽은 상태까지 재현해야 하지만, 이번 라운드 지시는 명시적으로 "접근성을
        // 위해 재현"하라고 요구해 tabindex="0"을 추가해 실제로 키보드 포커스가 가능하도록
        // 했다 - 자체 판단에 의한 기능 추가가 아니라 이번 라운드 범위에 포함된 지시 사항이다.
        // 포커스 가능해졌으니 role="checkbox"/aria-checked도 함께 부여해 스크린 리더에도
        // 올바른 위젯으로 인식되게 했다(이 두 속성은 지시에 명시되진 않았지만 tabindex 추가와
        // 분리할 수 없는 최소 부수 조치로 판단).
        if(!elOuter.hasAttribute("tabindex")){
            elOuter.setAttribute("tabindex", "0");
        }
        elOuter.setAttribute("role", "checkbox");
        elOuter.setAttribute("aria-checked", elCheckbox.checked ? "true" : "false");

        elCheckbox._yonaSwitch = true;
    }

    /**
     * 클릭/스페이스바 공통 토글 로직. 벤더 원본(90-92행 changeStatus, 110-131행 체크박스
     * change 리스너)의 드래그 관련 좌표 계산을 걷어내고 "체크박스 checked 반전 → on/off
     * 클래스 전환 → 체크박스에 네이티브 change 이벤트 dispatch"만 남긴 것 - 코디네이터
     * 지시(위 파일 헤더 주석 참고)에 따른 의도적 단순화다.
     *
     * 참고(벤더 원본과 동일한 잠재적 낙후 동작 재현): service/yona.user.Setting.js의
     * _onChangeNotiSwitch가 서버 요청 실패 시 `welTarget.checked = !bChecked`로 체크박스를
     * "일반 프로퍼티 대입"으로 되돌리는데, 이는 네이티브 change 이벤트를 발생시키지 않는다.
     * 원본 bootstrap-switch.js도 체크박스의 'change' 이벤트에만 반응해 on/off 클래스를
     * 갱신했으므로(제거된 코드지만 로직상 동일), 이 에러 복구 경로는 원본에서도 지금
     * 구현에서도 똑같이 "내부 checked 값은 되돌아가지만 스위치 시각 상태는 안 바뀌는" 낙후된
     * 동작이었다 - 새 버그가 아니라 기존 동작을 그대로 보존한 것이다.
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

    // 102-108행: $switchLeft/$switchRight 클릭, 133행 이하: label 클릭 - 셋 다 같은 토글로
    // 귀결되므로(코디네이터 지시에 따른 단순화) 하나의 델리게이트로 합쳤다.
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

    // 94-100행: 스페이스바(keyCode 32) 토글 - 위 접근성 보완(tabindex 추가)으로 실제 도달
    // 가능해졌다.
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
