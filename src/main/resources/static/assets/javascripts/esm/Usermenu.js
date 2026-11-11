

// ES 모듈은 문서 분석이 끝난 뒤 DOMContentLoaded 전에 실행되며, 아래 준비 완료 처리기는 그때 등록되어 문서가 준비되면 돈다.
$(() => {
    /* Set side navigation */
    // Also, see index.scala.html for home page menu sliding actions !!
    const $sidebar = $("#mySidenav");
    const viewSize = window.parent === window ? $(window).width() : $(window.parent).width();
    const PIXEL_CRITERIA_FOR_SMALL_DEVICE = 720;  // Criteria to distinguish small devices
    const SIDE_BAR_DEFAULT_WIDTH = "360px";
    const OPEN_STATE_KEY = "shallWeOpenLeftNavigation"; // v1.6와 같은 키이다(열림 "true", 닫힘 "false")
    const ACTIVE_TAB_KEY = "sidebarActiveMenu";         // v1.6와 같은 키이다(마지막에 고른 탭의 li 클래스)

    if ($(".gnb-usermenu-dropdown").length !== 0) {
        (async () => {
            let data;
            try {
                data = await yona.http.get(UsermenuUrl);
            } catch (failure) {
                // 목록을 읽지 못해도 아바타 단추와 바깥 클릭 닫기와 F 키는 걸어 둔다. 예전에는 여기서 끝나 단추가 아무 일도 하지 않았다(shell-F7)
                console.log("Usermenu loading failed: " + failure);
                iniNaviUserMenu();
                return;
            }
            $("#usermenu-tab-content-list").html(data);
            iniNaviUserMenu();
            afterUsermenuLoaded();
        })();
    }

    afterUsermenuLoaded();

    function iniNaviUserMenu() {
        $(document).on("keypress.openFavorite", function openFavoriteMenuWithShortcutKey(event) {
            if (isShortcutKeyPressed(event)) {
                event.preventDefault();
                openSidebar($sidebar);
                updateStar();
            }
        });

        $("#main").on("click.main", (event) => {
            if ($sidebar.width() !== 0 && $(event.target).parents("#mySidenav").length == 0) {
                closeSidebar($sidebar);
            }
        });

        // 지난 화면에서 열어 둔 채로 넘어왔다면 이어서 연다(작은 화면은 화면 전체를 덮으므로 이어 열지 않는다)
        if (recall(OPEN_STATE_KEY) === "true" && viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
            openSidebar($sidebar, true);
            updateStar();
        }

        $("[data-shell-switcher]").on("click.usermenu", (event) => {
            event.stopPropagation();
            if ($sidebar.width() !== 0) {
                closeSidebar($sidebar);
            } else {
                openSidebar($sidebar);
                updateStar();
            }
        });
    }

    function afterUsermenuLoaded() {
        restoreActiveTab();

        // 탭을 고르면 기억하고, 새로고침 아이콘은 화면을 다시 읽는다(v1.6 sidebar.scala.html)
        $(".nav-tabs .myOrganizationList, .nav-tabs .myProjectList, .nav-tabs .myRecentIssueList").off("click.rememberTab").on("click.rememberTab", function () {
            const name = ["myOrganizationList", "myProjectList", "myRecentIssueList"].find((n) => this.classList.contains(n));
            if (name) remember(ACTIVE_TAB_KEY, name);
        });
        $(".refresh-button").off("click.refresh").on("click.refresh", () => window.location.reload());

        // used for new project list ui
        $(".right-menu").off("click.tab").on("click.tab", ".myProjectList, a[href='#recentlyVisited'], a[href='#createdByMe'], a[href='#watching'], a[href='#joinmember']", () => {
            updateStar();
            setTimeout(function focusToProjectSearchInput() {
                const $projectSearch = $('.project-search');
                const $orgSearch = $('.org-search');
                if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
                    $projectSearch.focus();
                }
                if (!$projectSearch.val()) {
                    $projectSearch.val($orgSearch.val());
                }
                $orgSearch.val("");
            }, 200);

        });

        $('.myOrganizationList').off("click.orgList").on("click.orgList", function focusToOrgSearchInput() {
            setTimeout(() => {
                const $projectSearch = $('.project-search');
                const $orgSearch = $('.org-search');
                if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
                    $orgSearch.focus();
                }
                $orgSearch.val($projectSearch.val());
                $projectSearch.val("");
            }, 200);
        });

        // search by keyword
        $(".search-input")
            .off("keyup.search keydown.moveCursorFromInputform")
            .on("keyup.search", function (event) {
                const value = $(this).val().toLowerCase().trim();

                if (value !== "" || $yona.isBackspaceKey(event)) {
                    $(".user-li").each(function () {
                        $(this).toggle($(this).text().toLowerCase().indexOf(value) !== -1);
                    });
                    $(".org-li").each(function () {
                        $(this).toggle($(this).text().toLowerCase().indexOf(value) !== -1);
                    });
                }
            })
            .on("keydown.moveCursorFromInputform", (e) => {
                if ($yona.isEscapeKey(e)) {
                    $('.project-search').blur();
                    closeSidebar($sidebar);
                }
            });

        $(".project-list > .star-project, .project-breadcrumb > .user-project-list")
            .off("click.toggleProjectFavorite")
            .on("click.toggleProjectFavorite", async function toggleProjectFavorite(e) {
                e.stopPropagation();
                const that = $(this);
                let data;
                try {
                    data = await yona.http.post(UsermenuToggleFavoriteProjectUrl + that.data("projectId"));
                } catch (failure) {
                    $yona.alert(Messages("common.updateFailed", $yona.escapeHtml(failureReason(failure))));
                    return;
                }
                that.find('i').toggleClass("starred", !!data.favored);
            });

        $(".favorite-issue")
            .off("click.toggleProjectFavorite")
            .on("click.toggleProjectFavorite", async function toggleProjectFavorite(e) {
                e.stopPropagation();
                const that = $(this);
                let data;
                try {
                    data = await yona.http.post(UsermenuToggleFavoriteIssueUrl + that.data("issueId"));
                } catch (failure) {
                    $yona.alert(Messages("common.updateFailed", $yona.escapeHtml(failureReason(failure))));
                    return;
                }
                that.find('i').toggleClass("starred", !!data.favored);
                $yona.notify(Messages(data.message), 3000);

            });

        $(".user-ul > .user-li, .project-ul > .user-li")
            .off("click.project")
            .on("click.project", function (e) {
                e.preventDefault();
                e.stopPropagation();

                const location = $(this).data('location');
                if (e.metaKey || e.ctrlKey || e.shiftKey) {
                   return window.location = location;
                }

                if (window.self.name !== 'mainFrame') {
                    if ($("#mainFrame").length > 0) {
                        window.open(location, 'mainFrame');
                    } else {
                        window.open(location, '_blank');
                    }
                } else {
                    window.open(location, 'mainFrame');
                }

                $(".user-ul > .user-li, .project-ul > .user-li").removeClass("selected");
                $(this).addClass("selected");
            });

        $(".org-list > .star-org")
            .off("click.org")
            .on("click.org", async function toggleOrgFavorite(e) {
                e.stopPropagation();
                const that = $(this);
                let data;
                try {
                    data = await yona.http.post(UsermenuToggleFoveriteOrganizationUrl + that.data("organizationId"));
                } catch (failure) {
                    $yona.alert(Messages("common.updateFailed", $yona.escapeHtml(failureReason(failure))));
                    return;
                }
                that.find('i').toggleClass("starred", !!data.favored);
            });


        $(".all-orgs")
            .off("click.allOrgs")
            .on("click.allOrgs", function () {
                const $li = $(this).closest("li").find(".hide").toggle("fast");
            });

        $(".sub-project-counter").each(function (item) {
            const $this = $(this);
            const counter = $this.closest(".org-li").find(".project-ul > .user-li").length || "";

            $this.text(counter);
        });
    }

    /**
     * 즐겨찾기 요청이 실패했을 때 알림창에 보일 이유를 돌려준다. 서버 응답이 JSON(`{"reason": ...}`)이면 `reason`이고, JSON이 아닌
     * 짧은 글(400의 `Wrong project id` 등)이면 그 글을 쓴다. HTML이나 아주 긴 본문(로그인하지 않은 403, 500 화면)이면 본문 대신 상태 문구를 쓴다.
     * v1.6은 `JSON.parse`를 바로 불러 JSON이 아닌 응답이면 예외가 나서 알림창도 뜨지 않고 아무 반응이 없었다(shell-F20, issue-F3).
     *
     * @param {{responseText?: string, statusText?: string, status?: number}} failure 실패한 요청
     * @returns {string} 알림창에 보일 이유
     */
    function failureReason(failure) {
        const text = (failure && failure.responseText) || "";
        try {
            const reason = JSON.parse(text).reason;
            if (reason !== undefined && reason !== "") return reason;
        } catch (e) { /* JSON이 아니다. 아래에서 본문이나 상태 문구를 쓴다 */ }
        if (text && text.length <= 200 && text.indexOf("<") < 0) return text;
        return String((failure && (failure.statusText || failure.status)) || "");
    }

    function isShortcutKeyPressed(event) {
        const sKey = (event.originalEvent || event).key;
        return (!event.metaKey && (sKey === "f" || sKey === "ㄹ"))     // keypress의 key => f, ㄹ(한글 자모 U+3139)
            && $(':focus').length === 0;                        // avoid already somewhere focused state
    }

    /** 저장소에 쓰고 읽는다. 저장소를 쓸 수 없는 환경(비공개 창 등)에서도 화면은 동작해야 하므로 실패는 무시한다. */
    function remember(key, value) {
        try { localStorage.setItem(key, value); } catch (ignored) { /* 기억만 못 할 뿐이다 */ }
    }

    function recall(key) {
        try { return localStorage.getItem(key); } catch (ignored) { return null; }
    }

    function closeSidebar($sidebar) {
        $sidebar.width("0").css("border", "none");
        remember(OPEN_STATE_KEY, "false"); // A-491: v1.6의 `shallWeOpenLeftNavigation`처럼 열림 상태를 기억한다
    }

    function openSidebar($sidebar, restoring = false) {
        // 720px is a criteria to distinguish small devices
        if (!restoring) remember(OPEN_STATE_KEY, "true");
        if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
            $sidebar.width(SIDE_BAR_DEFAULT_WIDTH).css("border", "1px solid #ccc");
            if (!restoring) $(".search-input").focus(); // 화면을 열 때 이어서 열 때는 입력란이 포커스를 빼앗지 않게 한다
        } else {
            $sidebar.width("100vw").css("border", "1px solid #ccc");
        }
        // A-490: 패널은 본문 위에 덮이므로 본문 열 너비(.main-stream)를 줄이지 않는다.
    }

    /** 마지막에 고른 탭을 활성으로 만든다. 기억한 값이 없으면 서버가 그린 처음 상태(즐겨찾기)를 그대로 둔다. */
    function restoreActiveTab() {
        const name = recall(ACTIVE_TAB_KEY);
        if (!["myOrganizationList", "myProjectList", "myRecentIssueList"].includes(name)) return;
        $(".nav-tabs li > a.active").removeClass("active");
        $(".nav-tabs ." + name + " > a").addClass("active");
        $("#usermenu-tab-content-list .tab-pane.active").removeClass("active");
        $("#" + name).addClass("active");
    }

    // This method intended to sync sub tab list of projects
    async function updateStar() {
        let data;
        try {
            data = await yona.http.get(UsermenuGetFoveriteProjectsUrl);
        } catch (failure) {
            return; // 실패하면 별 표시를 그대로 둔다(예전에도 .fail 처리기가 없었다)
        }
        $(".star-project").each(function () {
            const $this = $(this);
            $this.find("i").toggleClass("starred", data.projectIds.includes($this.data("projectId")));
        });
    }

});
