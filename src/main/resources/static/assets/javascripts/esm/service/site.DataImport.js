import { register } from "../namespace.js";

/**
 * 사이트 관리의 데이터 가져오기 화면에서 파일을 TUS(tus.io 이어 올리기 규약)로 올린다(X-53, `decisions.md` D-144).
 *
 * 파일을 고르고 가져오기를 누르면 교체한다는 확인을 묻고, 조각으로 올린다. 끊기면 같은 파일을 다시 올릴 때 이어서 올리며,
 * 올리기를 마치면 올린 위치를 `sURLImport`로 보내 가져오기를 시작한다(서버가 처음 화면이나 실패 안내로 이동시킨다).
 * v1.6의 한 번에 올리는 폼(`/sites/import`)은 스크립트가 없을 때를 위해 남아 있다.
 *
 * 옵션:
 * - `sURLTus`: TUS 요청을 받는 주소
 * - `sURLImport`: 올리기를 마친 뒤 가져오기를 시작하는 주소
 * - `sConfirm`: 교체를 알리는 확인 문구
 * - `nChunkSize`: 조각 크기(바이트). 기본 5MB
 * - `fSubmit`: 가져오기 요청을 보내는 함수(시험용). 기본은 숨은 폼 제출
 *
 * @param {Object} htOptions 옵션
 */
const DataImport = function (htOptions) {
    const htOpt = htOptions || {};
    const welForm = $("#importForm");
    const welFile = welForm.find("input[type=file]");
    const welButton = welForm.find("button[type=submit]");
    const welProgress = $("#import-progress");
    const welBar = $("#import-bar");
    const welStatus = $("#import-status");

    function _init() {
        welForm.on("submit", _onSubmit);
    }

    /**
     * 가져오기 단추를 누르면 확인한 뒤 올리기를 시작한다. 파일이 없으면 아무것도 하지 않는다.
     */
    function _onSubmit(oEvent) {
        const file = welFile.prop("files") && welFile.prop("files")[0];
        oEvent.preventDefault();
        if (!file || !window.tus || !window.tus.isSupported) {
            // 파일이 없으면 보낼 것이 없고, tus를 쓸 수 없는 브라우저는 v1.6처럼 한 번에 올린다
            if (file) { welForm.off("submit", _onSubmit); welForm.trigger("submit"); }
            return;
        }
        if (!window.confirm(htOpt.sConfirm)) { return; }
        _start(file);
    }

    function _start(file) {
        welButton.prop("disabled", true);
        welStatus.text("");
        welProgress.prop("hidden", false);
        const upload = new window.tus.Upload(file, {
            endpoint: htOpt.sURLTus,
            chunkSize: htOpt.nChunkSize || 5 * 1024 * 1024,
            retryDelays: [0, 1000, 3000, 5000, 10000],
            metadata: { filename: file.name, filetype: file.type || "application/json" },
            onError: function () {
                welButton.prop("disabled", false);
                welStatus.text(Messages("site.data.import.upload.failed"));
            },
            onProgress: function (nSent, nTotal) {
                const nPercent = nTotal > 0 ? Math.floor(nSent / nTotal * 100) : 0;
                welBar.css("width", nPercent + "%");
                welStatus.text(Messages("site.data.import.upload.progress") + " " + nPercent + "%");
            },
            onSuccess: function () {
                welBar.css("width", "100%");
                welStatus.text(Messages("site.data.import.upload.importing"));
                _import(upload.url);
            }
        });
        // 끊긴 올리기가 있으면 이어서 올린다
        upload.findPreviousUploads().then(function (aPrevious) {
            if (aPrevious && aPrevious.length > 0) { upload.resumeFromPreviousUpload(aPrevious[0]); }
            upload.start();
        });
    }

    /**
     * 올린 위치(맥락 경로를 뗀 `/sites/tus/<id>`)로 가져오기를 시작한다.
     */
    function _import(sUploadURL) {
        const sPath = new URL(sUploadURL, window.location.href).pathname;
        const nIndex = sPath.lastIndexOf("/sites/tus/");
        const sUri = nIndex >= 0 ? sPath.substring(nIndex) : sPath;
        const fSubmit = htOpt.fSubmit || _submitForm;
        fSubmit(htOpt.sURLImport, { uploadUri: sUri });
    }

    function _submitForm(sURL, htData) {
        const form = $("<form>").attr({ method: "post", action: sURL }).hide().appendTo(document.body);
        Object.keys(htData).forEach(function (k) { $("<input type=hidden>").attr("name", k).val(htData[k]).appendTo(form); });
        form.get(0).submit();
    }

    _init();
};

export default DataImport;

register("yona.site.DataImport", DataImport);
