document.addEventListener("DOMContentLoaded", function () {
    /* Set side navigation */
    // Also, see index.scala.html for home page menu sliding actions !!
    var sidebar = document.getElementById("mySidenav");
    var viewSize = window.parent === window ? document.documentElement.clientWidth : window.parent.document.documentElement.clientWidth;
    var PIXEL_CRITERIA_FOR_SMALL_DEVICE = 720;  // Criteria to distinguish small devices
    var SIDE_BAR_DEFAULT_WIDTH = "360px";

    // #mySidenav는 로그인 사용자에게만 렌더링된다(site/layout.html의
    // sec:authorize="isAuthenticated()") - 비로그인 사용자는 sidebar가 null.
    if (sidebar) {
        var gnbOuter = document.querySelector(".gnb-outer");
        var gnbPosition = gnbOuter ? getComputedStyle(gnbOuter).position : undefined;

        if (gnbPosition === "absolute" || gnbPosition === "fixed") {
            sidebar.style.top = "40px";
        } else if (document.querySelectorAll(".admin-logged-in-affix").length === 1) {
            sidebar.style.top = "84px";
        } else {
            sidebar.style.top = "40px";
        }
    }

    if (document.querySelectorAll(".gnb-usermenu-dropdown").length !== 0) {
        fetch(UsermenuUrl)
            .then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                return response.text();
            })
            .then(function (data) {
                document.getElementById("usermenu-tab-content-list").innerHTML = data;
                iniNaviUserMenu();
                afterUsermenuLoaded();
            })
            .catch(function (data) {
                console.log("Usermenu loading failed: " + data);
            });
    }

    afterUsermenuLoaded();

    function iniNaviUserMenu() {
        document.addEventListener("keypress", function openFavoriteMenuWithShortcutKey(event) {
            if (isShortcutKeyPressed(event)) {
                event.preventDefault();
                openSidebar(sidebar);
                updateStar();
            }
        });

        document.getElementById("main").addEventListener("click", function (event) {
            if (sidebar.offsetWidth !== 0 && !(sidebar.contains(event.target) && event.target !== sidebar)) {
                closeSidebar(sidebar);
            }
        });

        document.getElementById("sidebar-open-btn").addEventListener("click", function (event) {
            event.stopPropagation();
            if (sidebar.offsetWidth !== 0) {
                closeSidebar(sidebar);
            } else {
                openSidebar(sidebar);
                updateStar();
            }
        });
    }

    function afterUsermenuLoaded() {
        // used for new project list ui
        var rightMenu = document.querySelector(".right-menu");
        if (rightMenu) {
            rightMenu.addEventListener("click", function (e) {
                var match = e.target.closest(".myProjectList, a[href='#recentlyVisited'], a[href='#createdByMe'], a[href='#watching'], a[href='#joinmember']");
                if (!match || !rightMenu.contains(match)) {
                    return;
                }
                updateStar();
                setTimeout(function focusToProjectSearchInput() {
                    var projectSearch = document.querySelector('.project-search');
                    var orgSearch = document.querySelector('.org-search');
                    if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
                        projectSearch.focus();
                    }
                    if (!projectSearch.value) {
                        projectSearch.value = orgSearch.value;
                    }
                    orgSearch.value = "";
                }, 200);
            });
        }

        document.querySelectorAll('.myOrganizationList').forEach(function (el) {
            el.addEventListener("click", function focusToOrgSearchInput() {
                setTimeout(function () {
                    var projectSearch = document.querySelector('.project-search');
                    var orgSearch = document.querySelector('.org-search');
                    if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
                        orgSearch.focus();
                    }
                    orgSearch.value = projectSearch.value;
                    projectSearch.value = "";
                }, 200);
            });
        });

        // search by keyword
        document.querySelectorAll(".search-input").forEach(function (searchInput) {
            searchInput.addEventListener("keyup", function (event) {
                var value = this.value.toLowerCase().trim();

                if (value !== "" || event.which === 8) {  // 8: backspace
                    document.querySelectorAll(".user-li").forEach(function (el) {
                        el.style.display = el.textContent.toLowerCase().indexOf(value) !== -1 ? "" : "none";
                    });
                    document.querySelectorAll(".org-li").forEach(function (el) {
                        el.style.display = el.textContent.toLowerCase().indexOf(value) !== -1 ? "" : "none";
                    });
                }
            });
            searchInput.addEventListener("keydown", function (e) {
                switch (e.keyCode) {
                    case 27:   // ESC
                        document.querySelector('.project-search').blur();
                        closeSidebar(sidebar);
                        break;
                    default:
                        break;
                }
            });
        });

        document.querySelectorAll(".project-list > .star-project, .project-breadcrumb > .user-project-list").forEach(function (el) {
            el.addEventListener("click", function toggleProjectFavorite(e) {
                e.stopPropagation();
                var that = this;
                fetch(UsermenuToggleFavoriteProjectUrl + that.dataset.projectId, {"method": "post"})
                    .then(function(response){
                        return response.text().then(function(text){
                            if(!response.ok){
                                return Promise.reject({"responseText": text});
                            }
                            return JSON.parse(text);
                        });
                    })
                    .then(function (data) {
                        if (data.favored) {
                            that.querySelector('i').classList.add("starred");
                        } else {
                            that.querySelector('i').classList.remove("starred");
                        }
                    })
                    .catch(function (data) {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });
            });
        });

        document.querySelectorAll(".favorite-issue").forEach(function (el) {
            el.addEventListener("click", function toggleProjectFavorite(e) {
                e.stopPropagation();
                var that = this;
                fetch(UsermenuToggleFavoriteIssueUrl + that.dataset.issueId, {"method": "post"})
                    .then(function(response){
                        return response.text().then(function(text){
                            if(!response.ok){
                                return Promise.reject({"responseText": text});
                            }
                            return JSON.parse(text);
                        });
                    })
                    .then(function (data) {
                        if (data.favored) {
                            that.querySelector('i').classList.add("starred");
                        } else {
                            that.querySelector('i').classList.remove("starred");
                        }
                        $yona.notify(Messages(data.message), 3000);
                    })
                    .catch(function (data) {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });

            });
        });

        document.querySelectorAll(".user-ul > .user-li, .project-ul > .user-li").forEach(function (el) {
            el.addEventListener("click", function (e) {
                e.preventDefault();
                e.stopPropagation();

                var location = this.dataset.location;
                if (e.metaKey || e.ctrlKey || e.shiftKey) {
                   return window.location = location;
                }

                if (window.self.name !== 'mainFrame') {
                    if (document.getElementById("mainFrame")) {
                        window.open(location, 'mainFrame');
                    } else {
                        window.open(location, '_blank');
                    }
                } else {
                    window.open(location, 'mainFrame');
                }

                document.querySelectorAll(".user-ul > .user-li, .project-ul > .user-li").forEach(function (li) {
                    li.classList.remove("selected");
                });
                this.classList.add("selected");
            });
        });

        document.querySelectorAll(".org-list > .star-org").forEach(function (el) {
            el.addEventListener("click", function toggleOrgFavorite(e) {
                e.stopPropagation();
                var that = this;
                fetch(UsermenuToggleFoveriteOrganizationUrl + that.dataset.organizationId, {"method": "post"})
                    .then(function(response){
                        return response.text().then(function(text){
                            if(!response.ok){
                                return Promise.reject({"responseText": text});
                            }
                            return JSON.parse(text);
                        });
                    })
                    .then(function (data) {
                        if (data.favored) {
                            that.querySelector('i').classList.add("starred");
                        } else {
                            that.querySelector('i').classList.remove("starred");
                        }
                    })
                    .catch(function (data) {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });
            });
        });


        document.querySelectorAll(".all-orgs").forEach(function (el) {
            el.addEventListener("click", function () {
                var hidden = this.closest("li").querySelectorAll(".hide");
                hidden.forEach(function (hiddenEl) {
                    toggleFast(hiddenEl);
                });
            });
        });

        document.querySelectorAll(".sub-project-counter").forEach(function (el) {
            var counter = el.closest(".org-li").querySelectorAll(".project-ul > .user-li").length || "";

            el.textContent = counter;
        });
    }

    function isShortcutKeyPressed(event) {
        return (!event.metaKey && (event.which === 102 || event.which === 12601))     // keycode => 102: f, 12623: ㄹ
            && (document.activeElement === null || document.activeElement === document.body);   // avoid already somewhere focused state
    }

    function closeSidebar(sidebar) {
        sidebar.style.width = "0";
        sidebar.style.border = "none";
        document.querySelectorAll(".main-stream").forEach(function (el) {
            el.classList.remove("span8");
            el.classList.add("span12");
        });
    }

    function openSidebar(sidebar) {
        // 720px is a criteria to distinguish small devices
        if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE) {
            sidebar.style.width = SIDE_BAR_DEFAULT_WIDTH;
            sidebar.style.border = "1px solid #ccc";
            document.querySelector(".search-input").focus();
        } else {
            sidebar.style.width = "100vw";
            sidebar.style.border = "1px solid #ccc";
        }
        document.querySelectorAll(".main-stream").forEach(function (el) {
            el.classList.remove("span12");
            el.classList.add("span8");
        });
    }

    // jQuery .toggle("fast")를 대체하는 CSS 트랜지션 기반 구현.
    function toggleFast(el) {
        var isHidden = getComputedStyle(el).display === "none";
        el.style.transition = "none";
        if (isHidden) {
            // 대상은 <li class="user-li hide"> - Bootstrap의 .hide{display:none}가 우선
            // 적용되므로 빈 문자열로는 다시 안 보인다. list-item을 명시해야 한다.
            el.style.display = "list-item";
            el.style.overflow = "hidden";
            el.style.maxHeight = "0px";
            el.style.opacity = "0";
            var targetHeight = el.scrollHeight;
            requestAnimationFrame(function () {
                el.style.transition = "max-height 200ms ease, opacity 200ms ease";
                el.style.maxHeight = targetHeight + "px";
                el.style.opacity = "1";
            });
            el.addEventListener("transitionend", function handler() {
                el.style.maxHeight = "";
                el.style.overflow = "";
                el.style.transition = "";
                el.removeEventListener("transitionend", handler);
            });
        } else {
            el.style.overflow = "hidden";
            el.style.maxHeight = el.scrollHeight + "px";
            requestAnimationFrame(function () {
                el.style.transition = "max-height 200ms ease, opacity 200ms ease";
                el.style.maxHeight = "0px";
                el.style.opacity = "0";
            });
            el.addEventListener("transitionend", function handler() {
                el.style.display = "none";
                el.style.transition = "";
                el.removeEventListener("transitionend", handler);
            });
        }
    }

    // This method intended to sync sub tab list of projects
    function updateStar() {
        fetch(UsermenuGetFoveriteProjectsUrl)
            .then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                return response.json();
            })
            .then(function (data) {
                document.querySelectorAll(".star-project").forEach(function (el) {
                    if (data.projectIds.indexOf(Number(el.dataset.projectId)) !== -1) {
                        el.querySelector("i").classList.add("starred");
                    } else {
                        el.querySelector("i").classList.remove("starred");
                    }
                });
            })
            .catch(function(){});
    }

});
