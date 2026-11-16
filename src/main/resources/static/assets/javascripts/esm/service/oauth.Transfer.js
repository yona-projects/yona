import { register } from "../namespace.js";

/**
 * OAuth 앱 소유자 이전의 확인 절차이다. 프로젝트 삭제(`project.Delete`)와 같은 순서로, 확인 창의 입력란에 앱 이름을 정확히
 * (대소문자와 공백 포함) 입력해야 "앱 이전" 단추가 켜지고, 누르면 이전할 곳을 고른 양식을 확인 값(앱 이름)과 함께 보낸다.
 * 서버 요청 계약은 그대로이다(`POST .../transfer`의 `target`, `confirm`). 화면에서 한 번 더 확인할 뿐이다(decisions.md A-583).
 *
 * @param {Object} htOptions
 * @param {string} htOptions.sAppName 확인에 입력해야 하는 앱 이름
 */
const Transfer = function(htOptions){

    const htElement = {};

    /**
     * 요소를 잡고 이벤트를 건다.
     */
    function _init(){
        htElement.welForm = $("#oauth-transfer-form");
        htElement.welExec = $("#btnTransferExec");
        htElement.welConfirmName = $("#transferConfirmName");
        htElement.welModal = $("#alertTransfer");

        htElement.welConfirmName.on("input", _syncExecButton);
        // 확인 창을 열 때마다 입력을 비우고 실행 단추를 끈다
        htElement.welModal.on("show.bs.modal", function(){
            htElement.welConfirmName.val("");
            _syncExecButton();
        });
        htElement.welExec.on("click", _onClickExec);
    }

    /**
     * 입력한 이름이 앱 이름과 정확히 같을 때만 실행 단추를 켠다.
     */
    function _syncExecButton(){
        htElement.welExec.prop("disabled", htElement.welConfirmName.val() !== htOptions.sAppName);
    }

    /**
     * 이름이 맞으면 양식을 보낸다.
     * @param {Event} weEvt 클릭 이벤트
     */
    function _onClickExec(weEvt){
        weEvt.preventDefault();
        if (htElement.welConfirmName.val() !== htOptions.sAppName) {
            return;
        }
        htElement.welForm.find("[name=confirm]").val(htOptions.sAppName);
        htElement.welForm[0].submit();
    }

    _init();
};

export default Transfer;

register("yona.oauth.Transfer", Transfer);
