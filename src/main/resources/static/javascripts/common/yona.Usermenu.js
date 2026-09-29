document.addEventListener("DOMContentLoaded", function () {
    var sidebarState = window.yonaSidebarState;
    var sidebar = document.getElementById("mySidenav");
    var viewSize = document.documentElement.clientWidth;
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

    if (sidebar) {
        // iniNaviUserMenu()는 #sidebar-open-btn 클릭/바깥클릭/단축키 리스너만 등록하며, 전부
        // 초기 HTML에 이미 있는 #mySidenav/#sidebar-open-btn/#main만 참조한다(그 안의 updateStar()도
        // .star-project가 아직 없으면 조용히 no-op) -- 사이드바 AJAX 파셜 로드를 기다릴 이유가 없다.
        // 예전에는 fetch().then() 안에서만 등록돼서, 그 fetch가 끝나기 전에 사용자가 사이드바 열기
        // 버튼을 누르면 리스너가 아직 안 걸려있어 클릭이 그대로 무시되고 사이드바가 영원히 안
        // 열렸다(느린 네트워크에서 실사용자도 겪을 수 있는 버그). 즉시 등록하도록 앞으로 뺌.
        iniNaviUserMenu();
        fetch(UsermenuUrl)
            .then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                return response.text();
            })
            .then(function (data) {
                document.getElementById("usermenu-tab-content-list").innerHTML = data;
                afterUsermenuLoaded(sidebar);
            })
            .catch(function (data) {
                console.log("Usermenu loading failed: " + data);
            });
    }

    if (sidebar) afterUsermenuLoaded(sidebar);
    var leftSidebar = document.getElementById("sidebar");
    if (leftSidebar) afterUsermenuLoaded(leftSidebar);
    bindProjectFavorites(document);
    document.addEventListener("turbo:frame-load", function (event) {
        if (event.target.id === "sidebar") afterUsermenuLoaded(event.target);
    });
    document.addEventListener("click", function(e) {
                var that = e.target.closest(".favorite-issue[data-issue-id]");
                if (!that) return;
                e.stopPropagation();
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
                        document.querySelectorAll('.favorite-issue[data-issue-id="' + that.dataset.issueId + '"] i').forEach(function (icon) {
                            icon.classList.toggle("starred", data.favored);
                        });
                        $yona.notify(Messages(data.message), 3000);
                    })
                    .catch(function (data) {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });

        }, true);

    function iniNaviUserMenu() {
        document.addEventListener("keypress", function openFavoriteMenuWithShortcutKey(event) {
            if (isShortcutKeyPressed(event)) {
                event.preventDefault();
                openSidebar(sidebar);
                updateStar(sidebar);
            }
        });

        document.addEventListener("click", function (event) {
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
                updateStar(sidebar);
            }
        });
    }

    // The right menu loads once; Turbo replaces left-menu contents on refresh.
    // Keep per-element bindings idempotent when either root is initialized again.
    function _bindOnce(el, sType, fHandler) {
        var sMarker = "usermenuBound_" + sType;
        if (el.dataset[sMarker]) {
            return;
        }
        el.dataset[sMarker] = "1";
        el.addEventListener(sType, fHandler);
    }

    function afterUsermenuLoaded(root) {
        _bindOnce(root, "click", function (event) {
            var tab = event.target.closest('.nav-tabs [data-toggle="tab"], .nav-subtab [data-toggle="tab"]');
            if (!tab || !root.contains(tab)) return;

            var isOrganization = tab.parentElement.classList.contains("myOrganizationList");
            var isProject = tab.parentElement.classList.contains("myProjectList");
            if (!isOrganization && !isProject && !tab.closest(".nav-subtab")) return;

            var projectSearch = root.querySelector(".project-search");
            var orgSearch = root.querySelector(".org-search");
            if (!projectSearch || !orgSearch) return;
            var searchInput = isOrganization ? orgSearch : projectSearch;
            var previousSearch = isOrganization ? projectSearch : orgSearch;
            if (isOrganization || !searchInput.value) searchInput.value = previousSearch.value;
            previousSearch.value = "";
            sidebarState.rememberSearch(root, searchInput);
            sidebarState.rememberSearch(root, previousSearch);
            sidebarState.filter(searchInput);
            if (!isOrganization) updateStar(root);
            sidebarState.rememberTab(root, tab);
            setTimeout(function () {
                if (viewSize > PIXEL_CRITERIA_FOR_SMALL_DEVICE && searchInput.isConnected) searchInput.focus();
            }, 0);
        });

        root.querySelectorAll(".search-input").forEach(function (searchInput) {
            _bindOnce(searchInput, "input", function () {
                sidebarState.filter(this);
                sidebarState.rememberSearch(root, this);
            });
        });
        _bindOnce(root, "keydown", function (event) {
            if (event.key !== "Escape") return;
            event.preventDefault();
            event.stopPropagation();
            if (document.activeElement && root.contains(document.activeElement)) document.activeElement.blur();
            if (root.id === "sidebar") {
                var close = root.querySelector("[data-sidebar-close]");
                if (close) close.click();
            } else {
                closeSidebar(root);
            }
        });

        bindProjectFavorites(root);

        root.querySelectorAll(".user-ul > .user-li[data-location], .project-ul > .user-li[data-location]").forEach(function (el) {
            _bindOnce(el, "click", function (event) {
                // Real links keep their href, target, and native modifier behavior.
                if (event.defaultPrevented || event.button !== 0 || event.target.closest("a, button, input, select, textarea")) return;
                event.preventDefault();
                event.stopPropagation();
                var modified = event.metaKey || event.ctrlKey || event.shiftKey;
                if (root.id === "sidebar" ? !modified : modified) {
                    window.location.assign(this.dataset.location);
                } else {
                    window.open(this.dataset.location, "_blank", "noopener");
                }
                root.querySelectorAll(".user-li.selected").forEach(function (li) {
                    li.classList.remove("selected");
                });
                this.classList.add("selected");
            });
        });

        root.querySelectorAll(".org-list > .star-org[data-organization-id]").forEach(function (el) {
            _bindOnce(el, "click", function toggleOrgFavorite(e) {
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
                        document.querySelectorAll('.star-org[data-organization-id="' + that.dataset.organizationId + '"] i').forEach(function (icon) {
                            icon.classList.toggle("starred", data.favored);
                        });
                    })
                    .catch(function (data) {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });
            });
        });

        root.querySelectorAll(".all-orgs").forEach(function (el) {
            _bindOnce(el, "click", function () {
                this.closest("li").querySelectorAll(".hide").forEach(function (hiddenEl) {
                    toggleFast(hiddenEl);
                });
            });
        });

        root.querySelectorAll(".sub-project-counter").forEach(function (el) {
            el.textContent = el.closest(".org-li").querySelectorAll(".project-ul > .user-li").length || "";
        });

        if (root.id === "sidebar") sidebarState.restore(root);
        $yona.initHoverPopovers("#" + root.id + " [data-toggle=popover]");
    }

    function bindProjectFavorites(root) {

        root.querySelectorAll(".project-list > .star-project[data-project-id], .project-breadcrumb > .user-project-list[data-project-id]").forEach(function (el) {
            _bindOnce(el, "click", function toggleProjectFavorite(e) {
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
                        document.querySelectorAll('.star-project[data-project-id="' + that.dataset.projectId + '"] i, .project-breadcrumb > .user-project-list[data-project-id="' + that.dataset.projectId + '"] i').forEach(function (icon) {
                            icon.classList.toggle("starred", data.favored);
                        });
                    })
                    .catch(function (data) {
                        $yona.alert("Update failed: " + JSON.parse(data.responseText).reason);
                    });
            });
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
            // .search-input lives inside the usermenu AJAX partial (common/usermenu_tab_content_list.html),
            // fetched asynchronously on page load -- if the sidebar is opened before that fetch
            // resolves, this element doesn't exist yet. Calling .focus() on null used to throw here,
            // aborting the function before the .main-stream span12->span8 reflow below ever ran,
            // leaving the sidebar visually widened but the main content not shrunk to make room for it.
            var searchInput = sidebar.querySelector(".tab-pane.active .search-input");
            if (searchInput) {
                searchInput.focus();
            }
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
    function updateStar(root) {
        fetch(UsermenuGetFoveriteProjectsUrl)
            .then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                return response.json();
            })
            .then(function (data) {
                root.querySelectorAll(".star-project").forEach(function (el) {
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
