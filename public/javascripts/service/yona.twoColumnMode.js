/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
function _initTwoColumnMode(){
    var $twoColumnMode = $("#two-column-mode");
    var useTwoColumnMode = localStorage.getItem('useTwoColumnMode');
    var $title = $('.title');

    $('#two-column-mode-checkbox').popover({trigger: "hover", placement: "top", delay: { show: 1000, hide: 100 }});

    if( useTwoColumnMode  === 'true'){
        attachPageSlideEvent($twoColumnMode, $title);
        bindFrameLoading();
    } else {
        $twoColumnMode.prop('checked', false);
        $('.post-item').css("cursor", "");
        unbindEvents();
    }

    $twoColumnMode.on('click', function () {
        console.log('this.checked', this.checked);
        if(this.checked){
            localStorage.setItem('useTwoColumnMode', true);
            attachPageSlideEvent($twoColumnMode, $title);
            bindFrameLoading();
        } else {
            localStorage.setItem('useTwoColumnMode', false);
            $('.post-item').css("cursor", "");
            unbindEvents();
        }
    });

    ////////////////////////////

    function attachPageSlideEvent(twoColumnMode, title){
        twoColumnMode.prop('checked', true);
        title.pageslide({direction: "left"});
        $('.post-item').css("cursor", "pointer");
    }

    function unbindEvents() {
        $title.unbind('click.pageslide');
        $title.unbind('click.iframeLoading');
    }

    function bindFrameLoading() {
        $title.on('click.iframeLoading', function () {
            $(".left-menu").hide(200);
            setTimeout(function () {
                $('#pageslide > iframe').ready(function () {
                    NProgress.done();
                });
            }, 100);
        });
    }
}
