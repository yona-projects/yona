/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
var lastClicked = "";
var mainWidth = "";
var isLeftMenuHide = false;

function _initTwoColumnMode(){
    var $twoColumnMode = $("#two-column-mode");
    var useTwoColumnMode = localStorage.getItem('useTwoColumnMode');
    var $title = $('.title, .twoColumeModeTarget');

    var $projectPageWrap = $('.project-page-wrap');
    if (mainWidth === "") {
        mainWidth = $projectPageWrap.css("width") || $(".page-wrap").css("width");
    }

    if( isLeftMenuHide ) {
        $(".left-menu").hide(0);
        $(".user-info-box").hide(0);
    }

    $('#two-column-mode-checkbox').popover({trigger: "hover", placement: "top", delay: { show: 100, hide: 100 }});

    // when to check box click
    $('.mass-update-check').on('click', function (e) {
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

        title.pageslide({direction: "left", speed: 0, modal: true});
        $('.post-item').css("cursor", "pointer");

        title.on('click.changeUrlWhenClick', function(e){
            var $this = $(this);
            if (!history.state) {
                window.history.pushState({ startPath: location.pathname }, $this.text(), $this.attr("href"));
            } else {
                window.history.replaceState(history.state, $this.text(), $this.attr("href"));
            }
        })
    }

    function unbindEvents() {
        $title.unbind('click.pageslide');
        $title.unbind('click.iframeLoading');
        $title.unbind('click.changeUrlWhenClick');
        $.pageslide.close();
        $(".user-info-box").show(0);
    }

    function bindFrameLoading() {
        $title.on('click.iframeLoading', function (e) {
            $('.post-item').removeClass('highlightBg');
            $(this).closest('.post-item').addClass('highlightBg');

            if($('#pageslide').is(":visible")){
                $(".left-menu").hide(0);
                $(".user-info-box").hide(0);
                isLeftMenuHide = true;
            } else {
                $(".user-info-box").show(0);
            }
            setTimeout(function () {
                $('#pageslide > iframe').ready(function () {
                    NProgress.done();
                });
            }, 100);
        });
    }
}
