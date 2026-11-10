/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
var lastClicked = "";
var mainWidth = "";
var isLeftMenuHide = false;

/**
 * 오른쪽 패널(`#pageslide`, Bootstrap 오프캔버스). v1.6의 jquery.pageslide가 하던 일을 한다(시험용 구현, 브랜치 trial/pageslide-offcanvas):
 * 제목 링크를 누르면 그 주소를 iframe으로 열고, 같은 링크를 다시 누르면 닫는다. 다른 링크를 누르면 iframe을 지우고(로딩 gif가 보인다)
 * 300ms 뒤 새 주소를 읽는다. 바깥 클릭이나 Esc로는 닫히지 않고(`keyboard: false`, 배경 없음), 페이지 스크롤도 잠그지 않는다(`scroll: true`).
 */
var twoColumnPanel = (function () {
    let element = null, offcanvas = null, open = false, lastCaller = null, loadTimer = null;

    function ensure() {
        if (!element) {
            element = $('<div id="pageslide" class="offcanvas offcanvas-end" tabindex="-1"></div>').appendTo("body")[0];
            offcanvas = new bootstrap.Offcanvas(element, {"backdrop": false, "scroll": true, "keyboard": false});
        }
    }

    function load(href) {
        window.clearTimeout(loadTimer);
        $(element).find("iframe").remove();
        loadTimer = window.setTimeout(() => {
            $(element).append($("<iframe allowtransparency='true' frameborder='0'></iframe>").attr("src", href));
        }, 300);
    }

    function close() {
        if (!open) { return; }
        open = false;
        lastCaller = null;
        window.clearTimeout(loadTimer);
        offcanvas.hide();
        $(".left-menu").show(200);
    }

    return {
        /** 링크를 눌렀을 때: 같은 링크를 다시 눌렀으면 닫고, 아니면 열거나 내용을 바꾼다. */
        toggle: function (href, caller) {
            ensure();
            if (open && caller === lastCaller) {
                close();
                return;
            }
            load(href);
            if (!open) {
                open = true;
                offcanvas.show();
            }
            lastCaller = caller;
        },
        close: function () { if (element) { close(); } },
        isOpen: function () { return open; }
    };
})();

function _initTwoColumnMode(){
    const $twoColumnMode = $("#two-column-mode");
    const useTwoColumnMode = localStorage.getItem('useTwoColumnMode');
    const $title = $('.title, .twoColumeModeTarget');

    const $projectPageWrap = $('.project-page-wrap');
    if (mainWidth === "") {
        mainWidth = $projectPageWrap.css("width") || $(".page-wrap").css("width");
    }

    if( isLeftMenuHide ) {
        $(".left-menu").hide(0);
        $(".user-info-box").hide(0);
    }

    $('#two-column-mode-checkbox').each(function(){ new bootstrap.Popover(this, {trigger: "hover", placement: "top", delay: { show: 100, hide: 100 }}); });

    // when to check box click
    $('.mass-update-check').on('click', (e) => {
        e.stopPropagation();
    });

    if( useTwoColumnMode  === 'true'){
        attachPageSlideEvent($twoColumnMode, $title);
        bindFrameLoading();
    } else {
        $twoColumnMode.prop('checked', false);
        $('.post-item').css("cursor", "");
        unbindEvents();
    }

    $twoColumnMode.on('click', function () {
        if(this.checked){
            localStorage.setItem('useTwoColumnMode', true);
            attachPageSlideEvent($twoColumnMode, $title);
            bindFrameLoading();
        } else {
            localStorage.setItem('useTwoColumnMode', false);
            $('.post-item').removeClass('highlightBg').css("cursor", "");
            unbindEvents();
        }
    });

    ////////////////////////////

    function attachPageSlideEvent(twoColumnMode, title){
        twoColumnMode.prop('checked', true);

        title.on('click.twoColumnPanel', function(e){
            e.preventDefault();
            e.stopPropagation();
            twoColumnPanel.toggle($(this).attr("href"), this);
        });
        $('.post-item').css("cursor", "pointer");

        title.on('click.changeUrlWhenClick', function(e){
            const $this = $(this);
            if (!history.state) {
                window.history.pushState({ startPath: location.pathname }, $this.text(), $this.attr("href"));
            } else {
                window.history.replaceState(history.state, $this.text(), $this.attr("href"));
            }
        })
    }

    function unbindEvents() {
        $title.unbind('click.twoColumnPanel');
        $title.unbind('click.iframeLoading');
        $title.unbind('click.changeUrlWhenClick');
        twoColumnPanel.close();
        $(".user-info-box").show(0);
    }

    function bindFrameLoading() {
        $title.on('click.iframeLoading', function (e) {
            $('.post-item').removeClass('highlightBg');
            $(this).closest('.post-item').addClass('highlightBg');

            if(twoColumnPanel.isOpen()){
                $(".left-menu").hide(0);
                $(".user-info-box").hide(0);
                isLeftMenuHide = true;
            } else {
                $(".user-info-box").show(0);
            }
            setTimeout(() => {
                $('#pageslide > iframe').ready(() => {
                    NProgress.done();
                });
            }, 100);
        });
    }
}
