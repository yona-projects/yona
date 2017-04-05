/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
var lastClicked = "";

function _initTwoColumnMode(){
    var $twoColumnMode = $("#two-column-mode");
    var useTwoColumnMode = localStorage.getItem('useTwoColumnMode');
    var $title = $('.title');

    $('#two-column-mode-checkbox').popover({trigger: "hover", placement: "top", delay: { show: 1000, hide: 100 }});

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
            $('#pageslide').html("<div>Loading...</div>");
        } else {
            localStorage.setItem('useTwoColumnMode', false);
            $('.post-item').removeClass('highlightBg').css("cursor", "");
            unbindEvents();
        }
    });

    ////////////////////////////

    function attachPageSlideEvent(twoColumnMode, title){
        twoColumnMode.prop('checked', true);

        title.pageslide({direction: "left", speed: 0});
        $('.post-item').css("cursor", "pointer");
    }

    function unbindEvents() {
        $title.unbind('click.pageslide');
        $title.unbind('click.iframeLoading');
    }

    function bindFrameLoading() {
        $title.on('click.iframeLoading', function (e) {
            $('.post-item').removeClass('highlightBg');
            $(this).closest('.post-item').addClass('highlightBg');

            if(lastClicked === this){
                if($('#pageslide').is(":visible")){
                    $(".left-menu").hide(0);
                }
                NProgress.done();
                return;
            } else {
                lastClicked = this;
            }

            if($('#pageslide').is(":visible")){
                $(".left-menu").hide(0);
            }
            setTimeout(function () {
                $('#pageslide > iframe').ready(function () {
                    NProgress.done();
                });
            }, 100);
        });
    }
}
