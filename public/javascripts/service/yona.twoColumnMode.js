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
    var $title = $('.title');

    var $projectPageWrap = $('.project-page-wrap');
    if (mainWidth === "") {
        mainWidth = $projectPageWrap.css("width");
    }

    if( isLeftMenuHide ) {
        $(".left-menu").hide(0);
    }

    $('#two-column-mode-checkbox').popover({trigger: "hover", placement: "top", delay: { show: 100, hide: 100 }});

    // when to check box click
    $('.mass-update-check').on('click', function (e) {
        e.stopPropagation();
    });

    if( useTwoColumnMode  === 'true'){
        attachPageSlideEvent($twoColumnMode, $title);
        bindFrameLoading();
        $(".two-column-icon-border").addClass("two-column-icon-selected");
    } else {
        $twoColumnMode.prop('checked', false);
        $('.post-item').css("cursor", "");
        unbindEvents();
        $(".two-column-icon-border").removeClass("two-column-icon-selected");
    }

    $twoColumnMode.on('click', function () {
        if(this.checked){
            localStorage.setItem('useTwoColumnMode', true);
            attachPageSlideEvent($twoColumnMode, $title);
            bindFrameLoading();
            $(".two-column-icon-border").addClass("two-column-icon-selected");
        } else {
            localStorage.setItem('useTwoColumnMode', false);
            $('.post-item').removeClass('highlightBg').css("cursor", "");
            $(".two-column-icon-border").removeClass("two-column-icon-selected");
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
        revokeMarginOfMainPage();
        $.pageslide.close();
    }

    function bindFrameLoading() {
        $title.on('click.iframeLoading', function (e) {
            $('.post-item').removeClass('highlightBg');
            $(this).closest('.post-item').addClass('highlightBg');

            if($('#pageslide').is(":visible")){
                $(".left-menu").hide(0);
                isLeftMenuHide = true;
                reduceMarginOfMainPage();
            } else {
                revokeMarginOfMainPage();
            }
            setTimeout(function () {
                $('#pageslide > iframe').ready(function () {
                    NProgress.done();
                });
            }, 100);
        });
    }

    function reduceMarginOfMainPage() {
        var $projectPageWrap = $('.project-page-wrap');
        $projectPageWrap.css("margin", "20px 10px 0").css("width", "55%");

        $(".project-header-wrap").css("margin", "0 10px");
        $(".project-menu-inner").css("margin", "0 10px")
    }

    function revokeMarginOfMainPage() {
        var $projectPageWrap = $('.project-page-wrap');

        $projectPageWrap.css("margin", "20px auto 0").css("width", mainWidth);
        $(".project-header-wrap").css("margin", "0 auto");
        $(".project-menu-inner").css("margin", "0 auto");
    }
}
