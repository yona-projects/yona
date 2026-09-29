(function () {
    document.documentElement.classList.add("sidebar-js");

    function readStored(storage, key) {
        try {
            return window[storage].getItem(key);
        } catch (_) {
            return null;
        }
    }

    function writeStored(storage, key, value) {
        try {
            window[storage].setItem(key, value);
        } catch (_) {
            // Storage may be disabled; the current menu must still work.
        }
    }

    function sessionKey(root) {
        return root.id === "sidebar" && root.dataset.sidebarUser
            ? "yona.sidebar." + root.dataset.sidebarUser + "." : null;
    }

    function filter(searchInput) {
        var value = searchInput.value.toLowerCase().trim();
        searchInput.closest(".user-project-list").querySelectorAll(".user-li, .org-li").forEach(function (el) {
            el.style.display = !value ? "" : el.textContent.toLowerCase().indexOf(value) !== -1 ? "list-item" : "none";
        });
    }

    function restore(root) {
        if (!root || root.id !== "sidebar") return;
        var activeMenu = readStored("localStorage", "sidebarActiveMenu");
        if (activeMenu === "myProjectList" || activeMenu === "myOrganizationList") {
            root.querySelectorAll(".nav-tabs > li, .tab-pane.user-project-list").forEach(function (el) {
                el.classList.toggle("active", el.classList.contains(activeMenu));
            });
        }
        var key = sessionKey(root);
        if (key) {
            var projectTab = readStored("sessionStorage", key + "projectTab");
            var link = Array.from(root.querySelectorAll(".nav-subtab a[data-toggle=tab]")).find(function (tab) {
                return tab.getAttribute("href") === projectTab;
            });
            var pane = link && root.querySelector(link.getAttribute("href"));
            if (pane) {
                link.closest(".nav-subtab").querySelectorAll("li").forEach(function (item) {
                    item.classList.toggle("active", item.contains(link));
                });
                pane.parentElement.querySelectorAll(":scope > .tab-pane").forEach(function (item) {
                    item.classList.toggle("active", item === pane);
                });
            }
            root.querySelectorAll(".search-input").forEach(function (input) {
                var query = readStored("sessionStorage", key + "query." + input.closest(".user-project-list").id);
                if (query !== null) {
                    input.value = query;
                    filter(input);
                }
            });
        }
        root.removeAttribute("data-sidebar-pending");
    }

    window.yonaSidebarState = {
        restore: restore,
        filter: filter,
        rememberSearch: function (root, input) {
            var key = sessionKey(root);
            if (key) writeStored("sessionStorage", key + "query." + input.closest(".user-project-list").id, input.value);
        },
        rememberTab: function (root, tab) {
            if (root.id !== "sidebar") return;
            var activeMenu = tab.getAttribute("data-sidebar-tab");
            if (activeMenu === "myProjectList" || activeMenu === "myOrganizationList") {
                writeStored("localStorage", "sidebarActiveMenu", activeMenu);
            }
            var key = sessionKey(root);
            if (key && tab.closest(".nav-subtab")) {
                writeStored("sessionStorage", key + "projectTab", tab.getAttribute("href"));
            }
        },
        isOpen: function () {
            return document.cookie.split(";").some(function (cookie) {
                return cookie.trim() === "yona.sidebar.open=true";
            });
        },
        setOpen: function (open) {
            document.cookie = "yona.sidebar.open=" + String(open) + "; Path=/; SameSite=Lax; Max-Age=31536000"
                + (window.location.protocol === "https:" ? "; Secure" : "");
        }
    };
}());
