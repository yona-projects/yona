// 회귀 주의: `#s2id_parentId`는 Select2 v3가 만드는 래퍼 id 관례라, #parentId를
// Tom Select로 교체한 뒤로는 가리키는 요소가 없는 죽은 선택자다(Tom Select는 그런 id의
// 래퍼를 만들지 않는다). 위젯 표시/비활성화는 반드시 Tom Select 인스턴스(element.tomselect)의
// enable/disable + wrapper 엘리먼트로 제어해야 한다.
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
            $yona.notify("Issue will be moved or written to '" + targetProjectName + "'", 4000);
        }
    });
});
