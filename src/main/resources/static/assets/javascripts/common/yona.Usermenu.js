$(() => {
    /* Set side navigation */
    // Also, see index.scala.html for home page menu sliding actions !!
    const $sidebar = $("#mySidenav");
    const viewSize = window.parent === window ? $(window).width() : $(window.parent).width();
    const PIXEL_CRITERIA_FOR_SMALL_DEVICE = 720;  // Criteria to distinguish small devices
    const SIDE_BAR_DEFAULT_WIDTH = "360px";

    if ($(".gnb-usermenu-dropdown").length !== 0) {
        yona.http.get(UsermenuUrl)
            .done((data) => {
                $("#usermenu-tab-content-list").html(data);
                iniNaviUserMenu();
                afterUsermenuLoaded();
            })
            .fail((data) => {
                console.log("Usermenu loading failed: " + data);
            });
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
        $(".right-menu").on("click.tab", ".myProjectList, a[href='#recentlyVisited'], a[href='#createdByMe'], a[href='#watching'], a[href='#joinmember']", () => {
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

        $('.myOrganizationList').on("click.orgList", function focusToOrgSearchInput() {
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
            .on("click.toggleProjectFavorite", function toggleProjectFavorite(e) {
                e.stopPropagation();
                const that = $(this);
                yona.http.post(UsermenuToggleFavoriteProjectUrl + that.data("projectId"))
                    .done((data) => {
                        if (data.favored) {
                            that.find('i').addClass("starred");
                        } else {
                            that.find('i').removeClass("starred");
                        }
                    })
                    .fail((data) => {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });
            });

        $(".favorite-issue")
            .on("click.toggleProjectFavorite", function toggleProjectFavorite(e) {
                e.stopPropagation();
                const that = $(this);
                yona.http.post(UsermenuToggleFavoriteIssueUrl + that.data("issueId"))
                    .done((data) => {
                        if (data.favored) {
                            that.find('i').addClass("starred");
                        } else {
                            that.find('i').removeClass("starred");
                        }
                        $yona.notify(Messages(data.message), 3000);
                    })
                    .fail((data) => {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });

            });

        $(".user-ul > .user-li, .project-ul > .user-li")
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
            .on("click.org", function toggleOrgFavorite(e) {
                e.stopPropagation();
                const that = $(this);
                yona.http.post(UsermenuToggleFoveriteOrganizationUrl + that.data("organizationId"))
                    .done((data) => {
                        if (data.favored) {
                            that.find('i').addClass("starred");
                        } else {
                            that.find('i').removeClass("starred");
                        }
                    })
                    .fail((data) => {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });
            });


        $(".all-orgs")
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
    function updateStar() {
        yona.http.get(UsermenuGetFoveriteProjectsUrl)
            .done((data) => {
                $(".star-project").each(function () {
                    const $this = $(this);
                    if (data.projectIds.indexOf($this.data("projectId")) !== -1) {
                        $this.find("i").addClass("starred");
                    } else {
                        $this.find("i").removeClass("starred");
                    }
                });
            });
    }

});
