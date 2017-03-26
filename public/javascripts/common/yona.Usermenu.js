$(function() {
    $.get(UsermenuUrl)
        .done(function (data) {
            $("#usermenu-tab-content-list").html(data);
            afterUsermenuLoaded();
        })
        .fail(function (data) {
            $yobi.alert("Usermenu loading failed: " + data);
        });;

    function afterUsermenuLoaded(){
        /* Set side navigation */
        // Also, see index.scala.html for home page menu sliding actions !!
        var $sidebar = $("#mySidenav");
        var viewSize = $(window).width();
        var PIXEL_CRITERIA_FOR_SMALL_DEVICE = 720;  // Criteria to distinguish small devices
        var SIDE_BAR_DEFAULT_WIDTH = "420px";

        $("#main").on("click", function(event){
            if( $sidebar.width() !== 0 && $(event.target).parents("#mySidenav").length == 0) {
                closeSidebar($sidebar);
            }
        });

        $("#sidebar-open-btn").on("click", function (event) {
            event.stopPropagation();
            if( $sidebar.width() !== 0){
                closeSidebar($sidebar);
            } else {
                openSidebar($sidebar);
                updateStar();
            }
        });

        function closeSidebar($sidebar) {
            $sidebar.width("0").css("border", "none");
            $(".main-stream").removeClass("span8").addClass("span12");
        }

        function openSidebar($sidebar){
            // 720px is a criteria to distinguish small devices
            if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
                $sidebar.width(SIDE_BAR_DEFAULT_WIDTH).css("border", "1px solid #ccc");
                $(".search-input").focus();
            } else {
                $sidebar.width("100vw").css("border", "1px solid #ccc");
            }
            $(".main-stream").removeClass("span12").addClass("span8");
        }

        // used for new project list ui
        $(".right-menu").on('click', ".myProjectList, a[href='#recentlyVisited'], a[href='#createdByMe'], a[href='#watching'], a[href='#joinmember']", function() {
            updateStar();
            setTimeout(function focusToProjectSearchInput() {
                var $projectSearch = $('.project-search');
                var $orgSearch = $('.org-search');
                if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
                    $projectSearch.focus();
                }
                if(!$projectSearch.val()){
                    $projectSearch.val($orgSearch.val());
                }
                $orgSearch.val("");
            }, 200);

        });

        $('.myOrganizationList').on('click', function focusToOrgSearchInput(){
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
        $(".search-input").on("keyup", function() {
            var value = $(this).val().toLowerCase().trim();
            $(".user-li").each(function() {
                $(this).toggle($(this).text().toLowerCase().indexOf(value) !== -1);
            });
        }).on("keydown.moveCursorFromInputform", function(e) {
            switch (e.keyCode) {
                case 27:   // ESC
                    closeSidebar($sidebar);
                    break;
                default:
                    break;
            }
        });

        $(".project-list > .star-project").on("click", function toggleProjectFavorite(e) {
            e.stopPropagation();
            var that = $(this);
            $.post(UsermenuToggleFavoriteProjectUrl + that.data("projectId"))
                .done(function (data) {
                    if(data.favored){
                        that.find('i').addClass("starred");
                    } else {
                        that.find('i').removeClass("starred");
                        that.parent(".project-list").remove();
                    }
                })
                .fail(function (data) {
                    $yobi.alert("Update failed: " + JSON.parse(data.responseText).reason);
                });
        });

        $(".org-list > .star-org").on("click", function toggleOrgFavorite(e) {
            e.stopPropagation();
            var that = $(this);
            $.post(UsermenuToggleFoveriteOrganizationUrl + that.data("organizationId"))
                .done(function (data) {
                    if(data.favored){
                        that.find('i').addClass("starred");
                    } else {
                        that.find('i').removeClass("starred");
                    }
                })
                .fail(function (data) {
                    $yobi.alert("Update failed: " + JSON.parse(data.responseText).reason);
                });
        });

        // This method intended to sync sub tab list of projects
        function updateStar(){
            $.get(UsermenuGetFoveriteProjectsUrl)
                .done(function(data){
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
    }
});
