import { register } from "../namespace.js";

/**
 * 그룹(조직) 삭제 화면의 확인 절차이다. 프로젝트 삭제(`project.Delete`)와 같은 순서로, 확인 창의 그룹 이름 입력란에 이름을
 * 정확히(대소문자 포함) 입력해야 "삭제" 단추가 켜지고, 누르면 삭제 요청(`DELETE`)을 보낸다. 서버 요청 계약은 v1.6 그대로이고
 * 화면에서만 한 번 더 확인한다(decisions.md A-578).
 *
 * 실패하면 확인 창을 닫고 서버가 돌려준 오류 문구(`errorMsg` 메시지 키)를 알린다. 읽을 수 없으면 기본 오류 문구를 알린다.
 *
 * @param {Object} htOptions
 * @param {string} htOptions.sDeleteURL 삭제 요청 주소(`/organizations/{이름}`)
 * @param {string} htOptions.sOrgName 확인에 입력해야 하는 그룹 이름
 * @param {string} htOptions.sHomeURL 삭제에 성공하면 이동할 주소
 */
const Delete = function(htOptions){

    const htElement = {};

    /**
     * 요소를 잡고 이벤트를 건다.
     */
    function _init(){
        htElement.welExec = $("#btnDeleteExec");
        htElement.welConfirmName = $("#deleteConfirmName");
        htElement.welModal = $("#alertDeletion");

        htElement.welConfirmName.on("input", _syncExecButton);
        // 확인 창을 열 때마다 입력을 비우고 "삭제" 단추를 끈다
        htElement.welModal.on("show.bs.modal", function(){
            htElement.welConfirmName.val("");
            _syncExecButton();
        });
        htElement.welExec.on("click", _onClickExec);
    }

    /**
     * 입력한 이름이 그룹 이름과 정확히 같을 때만 "삭제" 단추를 켠다.
     */
    function _syncExecButton(){
        htElement.welExec.prop("disabled", htElement.welConfirmName.val() !== htOptions.sOrgName);
    }

    /**
     * 이름이 맞으면 삭제 요청을 보낸다.
     * @param {Event} weEvt 클릭 이벤트
     */
    function _onClickExec(weEvt){
        weEvt.preventDefault();
        weEvt.stopPropagation();
        if (htElement.welConfirmName.val() !== htOptions.sOrgName) {
            return;
        }
        yona.http.ajax({
            type: "delete",
            url: htOptions.sDeleteURL,
            success: function(){
                location.href = htOptions.sHomeURL;
            },
            error: function(jqXHR){
                bootstrap.Modal.getOrCreateInstance(document.getElementById("alertDeletion")).hide();
                let sErrorMsg;
                try {
                    sErrorMsg = Messages(JSON.parse(jqXHR.responseText).errorMsg);
                } catch (e) {
                    sErrorMsg = Messages("organization.delete.error");
                }
                $yona.alert(sErrorMsg);
                return false;
            }
        });
    }

    _init();
};

export default Delete;

register("yona.organization.Delete", Delete);
