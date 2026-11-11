

// ES 모듈은 문서 분석이 끝난 뒤 DOMContentLoaded 전에 실행되며, 아래 준비 완료 처리기는 그때 등록되어 문서가 준비되면 돈다.
$(() => {
    /* Set side navigation */
    // Also, see index.scala.html for home page menu sliding actions !!
    const $sidebar = $("#mySidenav");
    const viewSize = window.parent === window ? $(window).width() : $(window.parent).width();
    const PIXEL_CRITERIA_FOR_SMALL_DEVICE = 720;  // Criteria to distinguish small devices
    const SIDE_BAR_DEFAULT_WIDTH = "360px";

    if ($(".gnb-usermenu-dropdown").length !== 0) {
        (async () => {
            let data;
            try {
                data = await yona.http.get(UsermenuUrl);
            } catch (failure) {
                console.log("Usermenu loading failed: " + failure);
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

        $("#sidebar-open-btn").on("click.usermenu", (event) => {
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
                    $yona.alert("Update failed: " + failureReason(failure));
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
                    $yona.alert("Update failed: " + failureReason(failure));
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
                    $yona.alert("Update failed: " + failureReason(failure));
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

    function isShortcutKeyPressed(event) {
        const sKey = (event.originalEvent || event).key;
        return (!event.metaKey && (sKey === "f" || sKey === "ㄹ"))     // keypress의 key => f, ㄹ(한글 자모 U+3139)
            && $(':focus').length === 0;                        // avoid already somewhere focused state
    }

    function closeSidebar($sidebar) {
        $sidebar.width("0").css("border", "none");
        $(".main-stream").removeClass("col-lg-8").addClass("col-lg-12");
    }

    /**
     * 즐겨찾기 요청이 실패했을 때 알림창에 보일 사유를 돌려준다. 서버가 `{"reason": ...}` JSON을 주면 그 값을 쓰고, JSON이 아닌 본문
     * (로그인하지 않은 403의 HTML 등)이면 상태 문구를 쓴다. v1.6은 `JSON.parse`가 예외를 던져 알림도 없이 멈췄다(issue-F3).
     *
     * @param {{responseText?: string, statusText?: string, status?: number}} failure 실패한 요청 객체
     * @returns {string} 알림창에 보일 사유
     */
    function failureReason(failure) {
        try {
            const reason = JSON.parse(failure.responseText).reason;
            if (reason) return reason;
        } catch (ignored) {
            // JSON이 아닌 본문이다. 아래 상태 문구로 대신한다
        }
        return (failure && (failure.statusText || failure.status)) || "";
    }

    function openSidebar($sidebar) {
        // 720px is a criteria to distinguish small devices
        if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
            $sidebar.width(SIDE_BAR_DEFAULT_WIDTH).css("border", "1px solid #ccc");
            $(".search-input").focus();
        } else {
            $sidebar.width("100vw").css("border", "1px solid #ccc");
        }
        $(".main-stream").removeClass("col-lg-12").addClass("col-lg-8");
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
