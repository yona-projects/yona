$(function () {
    /* Set side navigation */
    // Also, see index.scala.html for home page menu sliding actions !!
    var $sidebar = $("#mySidenav");
    var viewSize = window.parent === window ? $(window).width() : $(window.parent).width();
    var PIXEL_CRITERIA_FOR_SMALL_DEVICE = 720;  // Criteria to distinguish small devices
    var SIDE_BAR_DEFAULT_WIDTH = "360px";

    if ($(".gnb-usermenu-dropdown").length !== 0) {
        $.get(UsermenuUrl)
            .done(function (data) {
                $("#usermenu-tab-content-list").html(data);
                iniNaviUserMenu();
                afterUsermenuLoaded();
            })
            .fail(function (data) {
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

        $("#main").on("click.main", function (event) {
            if ($sidebar.width() !== 0 && $(event.target).parents("#mySidenav").length == 0) {
                closeSidebar($sidebar);
            }
        });

        $("#sidebar-open-btn").on("click.usermenu", function (event) {
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
        $(".right-menu").on("click.tab", ".myProjectList, a[href='#recentlyVisited'], a[href='#createdByMe'], a[href='#watching'], a[href='#joinmember']", function () {
            updateStar();
            setTimeout(function focusToProjectSearchInput() {
                var $projectSearch = $('.project-search');
                var $orgSearch = $('.org-search');
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
            setTimeout(function () {
                var $projectSearch = $('.project-search');
                var $orgSearch = $('.org-search');
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
                var value = $(this).val().toLowerCase().trim();

                if (value !== "" || event.which === 8) {  // 8: backspace
                    $(".user-li").each(function () {
                        $(this).toggle($(this).text().toLowerCase().indexOf(value) !== -1);
                    });
                    $(".org-li").each(function () {
                        $(this).toggle($(this).text().toLowerCase().indexOf(value) !== -1);
                    });
                }
            })
            .on("keydown.moveCursorFromInputform", function (e) {
                switch (e.keyCode) {
                    case 27:   // ESC
                        $('.project-search').blur();
                        closeSidebar($sidebar);
                        break;
                    default:
                        break;
                }
            });

        $(".project-list > .star-project, .project-breadcrumb > .user-project-list")
            .on("click.toggleProjectFavorite", function toggleProjectFavorite(e) {
                e.stopPropagation();
                var that = $(this);
                $.post(UsermenuToggleFavoriteProjectUrl + that.data("projectId"))
                    .done(function (data) {
                        if (data.favored) {
                            that.find('i').addClass("starred");
                        } else {
                            that.find('i').removeClass("starred");
                        }
                    })
                    .fail(function (data) {
                        $yobi.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });
            });

        $(".favorite-issue")
            .on("click.toggleProjectFavorite", function toggleProjectFavorite(e) {
                e.stopPropagation();
                var that = $(this);
                $.post(UsermenuToggleFavoriteIssueUrl + that.data("issueId"))
                    .done(function (data) {
                        if (data.favored) {
                            that.find('i').addClass("starred");
                        } else {
                            that.find('i').removeClass("starred");
                        }
                        $yobi.notify(Messages(data.message), 3000);
                    })
                    .fail(function (data) {
                        $yobi.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });

            });

        $(".user-ul > .user-li, .project-ul > .user-li")
            .on("click.project", function (e) {
                e.preventDefault();
                e.stopPropagation();

                var location = $(this).data('location');
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
                var that = $(this);
                $.post(UsermenuToggleFoveriteOrganizationUrl + that.data("organizationId"))
                    .done(function (data) {
                        if (data.favored) {
                            that.find('i').addClass("starred");
                        } else {
                            that.find('i').removeClass("starred");
                        }
                    })
                    .fail(function (data) {
                        $yobi.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });
            });


        $(".all-orgs")
            .on("click.allOrgs", function () {
                var $li = $(this).closest("li").find(".hide").toggle("fast");
            });

        $(".sub-project-counter").each(function (item) {
            var $this = $(this);
            var counter = $this.closest(".org-li").find(".project-ul > .user-li").length || "";

            $this.text(counter);
        });
    }

    function isShortcutKeyPressed(event) {
        return (!event.metaKey && (event.which === 102 || event.which === 12601))     // keycode => 102: f, 12623: ㄹ
            && $(':focus').length === 0;                        // avoid already somewhere focused state
    }

    function closeSidebar($sidebar) {
        $sidebar.width("0").css("border", "none");
        $(".main-stream").removeClass("span8").addClass("span12");
    }

    function openSidebar($sidebar) {
        // 720px is a criteria to distinguish small devices
        if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
            $sidebar.width(SIDE_BAR_DEFAULT_WIDTH).css("border", "1px solid #ccc");
            $(".search-input").focus();
        } else {
            $sidebar.width("100vw").css("border", "1px solid #ccc");
        }
        $(".main-stream").removeClass("span12").addClass("span8");
    }

    // This method intended to sync sub tab list of projects
    function updateStar() {
        $.get(UsermenuGetFoveriteProjectsUrl)
            .done(function (data) {
                $(".star-project").each(function () {
                    var $this = $(this);
                    if (data.projectIds.indexOf($this.data("projectId")) !== -1) {
                        $this.find("i").addClass("starred");
                    } else {
                        $this.find("i").removeClass("starred");
                    }
                });
            });
    }

});
