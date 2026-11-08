/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 제목 머리말(`[`) 자동완성을 `<title-input>`(components 저장소의 `title-input`)에 연결한다.
 *
 * v1.6은 `<input id="title">`에 At.js로 머리말을 붙였다. 지금은 `<title-input>`이 `[` 질의, 서버 조회, 목록, 키 처리를 맡고,
 * 이 모듈은 yona의 일만 한다: 라벨을 고르면 `<select id="labelIds">`를 바꾸고(같은 분류에서 하나만 고르는 분류는 교체) select2를 갱신하고
 * 알림을 띄우며, 조회의 시작과 끝에 NProgress를 보인다. 라벨이 아니거나 `#labelIds`가 없는 화면은 `[이름]` 글자를 그대로 넣는다.
 *
 * @param {{target: string, url?: string}} htOptions `target`은 `<title-input>`의 선택자, `url`은 머리말 후보를 묻는 주소(`src`가 없을 때만 쓴다)
 */
function yonaTitleHeadModule(htOptions){
    var htVar = {};
    var welTarget = null;

    /**
     * Initialize
     *
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        htVar = htOptions || {};
        if (!htVar.target) {
            if (window.console) {
                console.error("title head form element targeting doesn't exist!");
            }
            return;
        }
        welTarget = $(htVar.target);
        if (welTarget.length === 0) {
            return;
        }
        if (htVar.url && !welTarget.attr("src")) {
            welTarget.attr("src", htVar.url);
        }
        _attachEvent();
    }

    /**
     * attachEvent
     */
    function _attachEvent() {
        welTarget.on("title-input-select", _onSelect);
        welTarget.on("title-input-loadstart", function() { NProgress.start(); });
        welTarget.on("title-input-loadend", function() { NProgress.done(); });
    }

    /**
     * 후보를 골랐을 때. 라벨이면 select에서 고르고 글자는 남기지 않는다(이벤트를 취소하면 컴포넌트는 `[질의`만 지운다).
     * 라벨이 아니거나 라벨 select가 없는 화면이면 취소하지 않아 `[이름]`이 들어간다.
     *
     * @param {Event} eEvt `title-input-select`. `detail.item`은 서버가 준 후보
     */
    function _onSelect(eEvt) {
        var item = eEvt.originalEvent.detail.item;
        var $labelField = $("#labelIds");

        if (!item.category || $labelField.length === 0) {
            return;
        }

        eEvt.originalEvent.preventDefault();

        var issueLabels = $labelField.val() || [];
        var $selectedLabel = $labelField.find("option[value=" + item.id + "]");
        $selectedLabel.prop('selected', true);

        if (item.isExclusive) {
            var labelsOfTheCategory = getProjectLabels()[item.categoryId] || [];
            issueLabels = issueLabels.filter(function(label){
                return labelsOfTheCategory.indexOf(label) === -1;
            });
        }

        issueLabels.push($selectedLabel.val());
        $labelField.val(issueLabels).trigger("change.select2");

        $yona.notify('Label: ' + item.name, 3000);
    }

    /**
     *  It gather all project labels.
     *  Category id is used for the key.
     *  Label ids are used for the value.
     *  {
     *     31: [130, 120, ...],
     *     70: [200, 201, 320, ...]
     *  }
     */
    function getProjectLabels(){
        var allLabels = {};
        $("#labelIds > optgroup").each(function(){
            var allLabelsOfTheCategory = [];
            var categoryId;
            $(this).children().each(function(){
                var $this = $(this);
                allLabelsOfTheCategory.push($this.val());
                categoryId = $this.data("categoryId");
            });

            allLabels[categoryId] = allLabelsOfTheCategory;
        });
        return allLabels;
    }

    _init(htOptions || {});
}
