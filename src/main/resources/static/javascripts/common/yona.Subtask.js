// 점진적 jQuery 제거 - jQuery($) 없이 순수 DOM API로 재작성.
//
// 이 과정에서 legacy 대비 회귀를 하나 발견해 함께 고쳤다: legacy(v1.6.1~최신 HEAD까지 이
// 파일은 단 한 줄도 바뀐 적이 없다 - 지금도 진짜 Select2 v3를 씀)는 `#s2id_parentId`
// (Select2 v3가 원본 <select> 옆에 만드는 위젯 래퍼의 id 관례)를 보이기/숨기기 하고
// `change.select2` 커스텀 이벤트를 발생시켜 Select2 쪽 표시를 갱신시켰다. yona는 별도 작업
// (P3-46, Select2 -> Tom Select 교체)에서 #parentId/#targetProjectId만 Tom Select로 바꾸고
// 이 파일은 갱신하지 않아, `$('#s2id_parentId')`가 가리키는 요소 자체가 더 이상 존재하지 않는
// 죽은 코드가 됐다(Tom Select는 그런 id의 래퍼를 만들지 않는다) - 다른 프로젝트로 전환해도
// #parentId 위젯이 실제로 숨겨지지 않던 회귀. Tom Select 인스턴스(`element.tomselect`)의
// 실제 API(enable/disable + wrapper 엘리먼트 표시 전환)로 교체해 legacy와 동일한 결과(다른
// 프로젝트로 전환하면 부모 이슈 선택 위젯이 비활성화되고 숨겨짐)를 내도록 했다.
function _onReady(fn){
    if (document.readyState !== 'loading') {
        fn();
    } else {
        document.addEventListener('DOMContentLoaded', fn);
    }
}

_onReady(function(){
    document.querySelectorAll(".subtask-message").forEach(function(messageEl){
        messageEl.addEventListener("click", function(){
            document.querySelectorAll(".subtask-wrap").forEach(function(subtaskWrap){
                var willShow = getComputedStyle(subtaskWrap).display === "none";
                subtaskWrap.style.display = willShow ? "" : "none";

                if (willShow) {
                    messageEl.classList.add("option-on");
                } else {
                    messageEl.classList.remove("option-on");
                }

                subtaskWrap.querySelectorAll("select").forEach(function(select){
                    select.disabled = !select.disabled;
                });
            });
        });
    });

    var initialProject = document.getElementById("targetProjectId");
    if (!initialProject) {
        return;
    }
    var initialProjectId = initialProject.value;

    initialProject.addEventListener("change", function(){
        var parentId = document.getElementById("parentId");
        var parentTomSelect = parentId ? parentId.tomselect : null;
        var selectedOption = initialProject.selectedOptions[0];
        var targetProjectName = selectedOption ? selectedOption.textContent : "";

        if (initialProject.value === initialProjectId) {
            if (parentTomSelect) {
                parentTomSelect.enable();
                parentTomSelect.wrapper.style.display = "";
            } else if (parentId) {
                parentId.disabled = false;
            }
        } else {
            if (parentId) {
                var firstOption = parentId.querySelector("option");
                if (parentTomSelect) {
                    parentTomSelect.setValue(firstOption ? firstOption.value : "");
                    parentTomSelect.disable();
                    parentTomSelect.wrapper.style.display = "none";
                } else {
                    parentId.value = firstOption ? firstOption.value : "";
                    parentId.disabled = true;
                }
            }
            $yobi.notify("Issue will be moved or written to '" + targetProjectName + "'", 4000);
        }
    });
});
