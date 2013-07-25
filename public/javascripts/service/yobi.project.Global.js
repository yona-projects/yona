/**
 * @(#)yobi.project.Global.js 2013.06.21
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */
/*
 * 프로젝트 페이지 전역에 영향을 주는 공통모듈
 * prjmenu.scala.html 에서 호출함
 */
(function(ns) {
    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions) {
        var htVar = {};
        var htElement = {};

        /**
         * 모듈 초기화
         * initialize
         */
        function _init(htOptions) {
            _initVar(htOptions);
            _initElement();
            _attachEvent();

            _initShortcutKey(htOptions.htKeyMap);
            _initAffix();
        }

        /**
         * 변수 초기화
         * initialize normal variables
         */
        function _initVar(htOptions){
            htVar.sRepoURL = htOptions.sRepoURL;
        }

        /**
         * 엘리먼트 변수 초기화
         * initialize element variables
         */
        function _initElement() {
            htElement.welProjectMenu = $(".project-menu");
            htElement.welProjectMenuWrap = $(".project-menu-wrap");

            htElement.welBtnWatch =  $(".watchBtn, #btnWatch");
            htElement.welBtnEnroll =  $(".enrollBtn");
            htElement.welBtnClone = $("#btnClone");
            htElement.welForkedFrom = $("#forkedFrom");
        }

        /**
         * 이벤트 핸들러 초기화
         * attach event handlers
         */
        function _attachEvent() {
            htElement.welBtnWatch.click(_onClickBtnWatch);
            htElement.welBtnEnroll.click(_onClickBtnEnroll);

            // 내용은 data-content 속성으로 scala 파일 내에 있음.
            htElement.welForkedFrom.popover({
                "html"   : true
            });

            htElement.welBtnClone.popover({
                "html"     : true,
                "placement": "bottom",
                "trigger"  : "manual",
                "content"  : $("#tplRepoURL").html()
            });
            htElement.welBtnClone.click(_onClickBtnClone);
        }

        /**
         * Watch 버튼 클릭시 이벤트 핸들러
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnWatch(weEvt){
            weEvt.preventDefault();
            $('<form action="' + $(this).attr('href') + '" method="post"></form>').submit();
        }

        /**
         * Enroll 버튼 클릭시 이벤트 핸들러
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnEnroll(weEvt){
            weEvt.preventDefault();
            $('<form action="' + $(this).attr('href') + '" method="post"></form>').submit();
        }

        /**
         * 프로젝트 전역 공통 단축키
         * @param {Hash Table} htKeyMap
         * @require yobi.ShortcutKey
         */
        function _initShortcutKey(htKeyMap){
            yobi.ShortcutKey.setKeymapLink(htKeyMap);
        }

        /**
         * Clone 버튼 클릭시 이벤트 핸들러
         * $.popover 를 수동으로 제어하도록 되어 있음
         */
        function _onClickBtnClone(){
            htElement.welBtnClone.popover("toggle");

            // popover 영역은 표시할 때마다 새로 생성하므로
            // 이벤트 처리를 다시 해야 함
            var welPopover = htElement.welProjectMenu.find(".popover");

            if(welPopover.length > 0){
                // 주소 복사 버튼
                welPopover.find(".copy-url").zclip({
                    "path": "/assets/javascripts/lib/jquery/ZeroClipboard.swf",
                    "copy": function() {
                        return htVar.sRepoURL;
                    }
                });

                // 주소 표시 영역
                welPopover.find(".repo-url").click(function(){
                    $(this).select();
                });
            }
        }

        /**
         * 프로젝트 메뉴 영역에 bootstrap-affix 적용
         */
        function _initAffix(){
            htElement.welProjectMenu.height(htElement.welProjectMenuWrap.height());

            htElement.welProjectMenuWrap.affix({
                "offset": htElement.welProjectMenuWrap.offset()
            });
        }

        _init(htOptions || {});
    };
})("yobi.project.Global");