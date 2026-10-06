import { A as e, C as t, I as n, M as r, O as i, P as a, S as o, _ as s, a as c, b as ee, c as l, d as u, h as te, m as d, n as f, o as p, p as ne, r as re, u as m, w as h } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as g } from "./_plugin-vue_export-helper-B3ysoDQm.js";
//#region src/usermenu/usermenu.ts
var _ = [
	"personal",
	"favoriteProjects",
	"recentlyVisited",
	"createdByMe",
	"watching",
	"joinmember"
], v = ["favoriteOrganizations", "organizations"];
function y(e) {
	return typeof e == "object" && !!e && !Array.isArray(e);
}
function b(e) {
	return !y(e) || typeof e.id != "number" || typeof e.name != "string" ? null : {
		id: e.id,
		name: e.name,
		owner: typeof e.owner == "string" ? e.owner : "",
		overview: typeof e.overview == "string" ? e.overview : null,
		href: typeof e.href == "string" ? e.href : "#",
		favorite: e.favorite === !0
	};
}
function x(e) {
	return Array.isArray(e) ? e.map(b).filter((e) => e !== null) : [];
}
function S(e) {
	return Array.isArray(e) ? e.flatMap((e) => y(e) && typeof e.id == "number" && typeof e.name == "string" ? [{
		id: e.id,
		name: e.name,
		favorite: e.favorite === !0,
		projects: x(e.projects)
	}] : []) : [];
}
function C(e) {
	return Array.isArray(e) ? e.flatMap((e) => y(e) && typeof e.title == "string" && typeof e.href == "string" ? [{
		title: e.title,
		href: e.href
	}] : []) : [];
}
function w(e) {
	let t = y(e) ? e : {};
	return {
		loginId: typeof t.loginId == "string" ? t.loginId : "",
		personal: x(t.personal),
		favoriteOrganizations: S(t.favoriteOrganizations),
		organizations: S(t.organizations),
		favoriteProjects: x(t.favoriteProjects),
		recentlyVisited: x(t.recentlyVisited),
		createdByMe: x(t.createdByMe),
		watching: x(t.watching),
		joinmember: x(t.joinmember),
		visitedIssues: C(t.visitedIssues)
	};
}
function T(e, t) {
	let n = t.toLowerCase().trim();
	return n === "" || e.toLowerCase().includes(n);
}
function E(e, t) {
	return e.filter((e) => T(`${e.name} ${e.owner}`, t));
}
function D(e, t) {
	return t.trim() === "" ? e : e.flatMap((e) => {
		if (T(e.name, t)) return [e];
		let n = E(e.projects, t);
		return n.length > 0 ? [{
			...e,
			projects: n
		}] : [];
	});
}
function ie(e, t, n = (e) => e.favorite) {
	return t ? e.projects : e.projects.filter(n);
}
function ae(e, t, n) {
	let r = (e) => e.id === t ? {
		...e,
		favorite: n
	} : e, i = { ...e };
	for (let t of _) i[t] = e[t].map(r);
	for (let t of v) i[t] = e[t].map((e) => ({
		...e,
		projects: e.projects.map(r)
	}));
	return i;
}
function oe(e, t, n) {
	let r = (e) => e.id === t ? {
		...e,
		favorite: n
	} : e;
	return {
		...e,
		favoriteOrganizations: e.favoriteOrganizations.map(r),
		organizations: e.organizations.map(r)
	};
}
//#endregion
//#region src/usermenu/sidebar-state.ts
var O = "yonaLeftSidebarOpen", k = "shallWeOpenLeftNavigation", A = "sidebarActiveMenu", j = ["myOrganizationList", "myProjectList"], M = [
	"recentlyVisited",
	"createdByMe",
	"watching",
	"joinmember"
];
function N(e, t) {
	try {
		return e();
	} catch {
		return t;
	}
}
function se(e) {
	return N(() => {
		let t = e.getItem(O);
		if (t !== null) return t === "true";
		let n = e.getItem(k);
		if (n === null) return !1;
		let r = n === "true";
		return e.setItem(O, String(r)), e.removeItem(k), r;
	}, !1);
}
function ce(e, t) {
	N(() => e.setItem(O, String(t)), void 0);
}
function le(e) {
	let t = N(() => e.getItem(A), null);
	return j.find((e) => e === t) ?? "myOrganizationList";
}
function ue(e, t) {
	j.includes(t) && N(() => e.setItem(A, t), void 0);
}
var P = {
	projectTab: "recentlyVisited",
	orgQuery: "",
	projectQuery: ""
}, F = (e) => `yona.sidebar.${e}`;
function de(e, t) {
	return t ? N(() => {
		let n = e.getItem(F(t));
		if (n === null) return { ...P };
		let r = JSON.parse(n), i = typeof r == "object" && r ? r : {};
		return {
			projectTab: M.find((e) => e === i.projectTab) ?? P.projectTab,
			orgQuery: typeof i.orgQuery == "string" ? i.orgQuery : "",
			projectQuery: typeof i.projectQuery == "string" ? i.projectQuery : ""
		};
	}, { ...P }) : { ...P };
}
function fe(e, t, n) {
	t && N(() => e.setItem(F(t), JSON.stringify(n)), void 0);
}
var I = (e) => `yona.sidebar.cache.${e}`;
function L(e, t, n) {
	if (!t) return null;
	try {
		let r = e.getItem(I(t));
		if (r === null) return null;
		let i = JSON.parse(r);
		if (typeof i != "object" || !i) return null;
		let { savedAt: a, menu: o } = i;
		if (typeof a != "number" || a > n || n - a > 18e5) return null;
		let s = w(o);
		return s.loginId === t ? {
			menu: s,
			savedAt: a
		} : null;
	} catch {
		return null;
	}
}
function R(e, t, n, r) {
	if (t) try {
		e.setItem(I(t), JSON.stringify({
			savedAt: r,
			menu: n
		}));
	} catch {}
}
function z(e, t) {
	return e !== null && t - e.savedAt <= 15e3;
}
//#endregion
//#region src/usermenu/YonaSidebar.vue?vue&type=script&setup=true&lang.ts
var B = ["hidden", "aria-label"], V = {
	class: "tabs",
	role: "tablist"
}, pe = [
	"data-tab",
	"aria-selected",
	"onClick"
], me = {
	key: 0,
	class: "error",
	role: "alert"
}, he = {
	key: 1,
	class: "status",
	role: "status"
}, ge = {
	class: "pane",
	id: "myOrganizationList"
}, _e = ["placeholder"], ve = {
	key: 0,
	class: "no-result"
}, ye = {
	key: 1,
	class: "user-ul orgs"
}, be = { class: "org-row" }, xe = ["aria-expanded", "onClick"], Se = { class: "org-name" }, Ce = { class: "sub-project-counter" }, we = [
	"aria-pressed",
	"aria-label",
	"onClick"
], Te = { class: "project-ul" }, Ee = ["href", "title"], De = { class: "project-name" }, Oe = [
	"aria-pressed",
	"aria-label",
	"onClick"
], ke = ["href", "title"], Ae = { class: "project-name" }, H = { class: "project-owner" }, je = [
	"aria-pressed",
	"aria-label",
	"onClick"
], Me = {
	class: "pane",
	id: "myProjectList"
}, Ne = ["placeholder"], Pe = {
	class: "subtabs",
	role: "tablist"
}, Fe = [
	"data-subtab",
	"aria-selected",
	"onClick"
], Ie = {
	key: 0,
	class: "no-result"
}, Le = {
	key: 1,
	class: "user-ul"
}, Re = ["href", "title"], ze = { class: "project-name" }, Be = { class: "project-owner" }, Ve = [
	"aria-pressed",
	"aria-label",
	"onClick"
], U = "personal", W = /*#__PURE__*/ g(/* @__PURE__ */ s({
	__name: "YonaSidebar",
	props: {
		apiUrl: {
			default: "/-_-api/v1/usermenu",
			type: String
		},
		favoriteProjectUrl: {
			default: "/-_-api/v1/favoriteProjects/",
			type: String
		},
		favoriteOrganizationUrl: {
			default: "/-_-api/v1/favoriteOrganizations/",
			type: String
		}
	},
	setup(s, { expose: f }) {
		let g = s, _ = re();
		function v(e, t) {
			let n = typeof Messages == "function" ? Messages(e) : "";
			return n && n !== e ? n : t;
		}
		let y = {
			getItem: () => null,
			setItem: () => {},
			removeItem: () => {}
		};
		function b() {
			try {
				return window.localStorage;
			} catch {
				return y;
			}
		}
		function x() {
			try {
				return window.sessionStorage;
			} catch {
				return y;
			}
		}
		let S = r(null), C = r(!1), T = r(!1), O = r(_?.hasAttribute("open") || se(b())), k = r(le(b())), A = r("recentlyVisited"), j = r(""), M = r(""), N = r(/* @__PURE__ */ new Set()), P = r(/* @__PURE__ */ new Set()), F = m(() => _?.getAttribute("login-id") || S.value?.loginId || ""), I = () => _?.getAttribute("login-id") ?? "", W = 0;
		i(O, (e) => {
			_?.toggleAttribute("open", e), e && !S.value && !C.value && q();
		}, { immediate: !0 });
		function G(e) {
			O.value !== e && (O.value = e, ce(b(), e), _?.dispatchEvent(new CustomEvent("yona-sidebar-toggle", {
				detail: { open: e },
				bubbles: !0,
				composed: !0
			})));
		}
		function He() {
			G(!O.value);
		}
		function K(e) {
			return new Set([
				...e.personal,
				...e.favoriteOrganizations.flatMap((e) => e.projects),
				...e.organizations.flatMap((e) => e.projects)
			].filter((e) => e.favorite).map((e) => e.id));
		}
		async function q(e = !1) {
			let t = Date.now(), n = L(x(), I(), t);
			if (n && !S.value && (S.value = n.menu, P.value = K(n.menu), W = n.savedAt), e || !z(n, t)) {
				C.value = !0, T.value = !1;
				try {
					let e = await fetch(g.apiUrl, {
						headers: { Accept: "application/json" },
						credentials: "same-origin"
					});
					if (!e.ok) throw Error(String(e.status));
					let t = w(await e.json());
					P.value = K(t), S.value = t, W = Date.now(), R(x(), I(), t, W);
				} catch {
					S.value || (T.value = !0);
				} finally {
					C.value = !1;
				}
			}
		}
		i(F, (e) => {
			if (!e) return;
			let t = de(b(), e);
			A.value = t.projectTab, j.value = t.orgQuery, M.value = t.projectQuery;
		}, { immediate: !0 });
		function J() {
			fe(b(), F.value, {
				projectTab: A.value,
				orgQuery: j.value,
				projectQuery: M.value
			});
		}
		function Ue(e) {
			k.value = e, ue(b(), e);
		}
		function We(e) {
			A.value = e, J();
		}
		function Ge(e) {
			let t = new Set(N.value);
			t.has(e) ? t.delete(e) : t.add(e), N.value = t;
		}
		async function Y(e) {
			try {
				let t = await fetch(e, {
					method: "POST",
					credentials: "same-origin"
				});
				return t.ok ? { favored: (await t.json()).favored === !0 } : null;
			} catch {
				return null;
			}
		}
		async function X(e) {
			let t = await Y(g.favoriteProjectUrl + e.id);
			t && S.value && (S.value = ae(S.value, e.id, t.favored), R(x(), I(), S.value, W || Date.now()));
		}
		async function Ke(e) {
			let t = await Y(g.favoriteOrganizationUrl + e.id);
			t && S.value && (S.value = oe(S.value, e.id, t.favored), R(x(), I(), S.value, W || Date.now()));
		}
		let qe = m(() => [{
			id: "myOrganizationList",
			label: v("title.favorite", "즐겨찾기")
		}, {
			id: "myProjectList",
			label: v("title.project", "프로젝트")
		}]), Je = m(() => [
			{
				id: "recentlyVisited",
				label: v("common.order.recentlyVisited", "최근 방문")
			},
			{
				id: "createdByMe",
				label: v("common.order.createdByMe", "내가 만든")
			},
			{
				id: "watching",
				label: v("common.order.watching", "지켜보는")
			},
			{
				id: "joinmember",
				label: v("common.order.joinmember", "참여 중인")
			}
		]), Z = m(() => {
			let e = S.value;
			return e ? D([
				{
					id: -1,
					name: e.loginId,
					favorite: !1,
					projects: e.personal
				},
				...e.favoriteOrganizations,
				...e.organizations
			], j.value).map((e) => ({
				org: e,
				key: e.id === -1 ? U : `org-${e.id}`,
				personal: e.id === -1,
				projects: ie(e, N.value.has(e.id === -1 ? U : `org-${e.id}`) || j.value.trim() !== "", (e) => P.value.has(e.id))
			})) : [];
		}), Q = m(() => E(S.value?.favoriteProjects ?? [], j.value)), $ = m(() => E(S.value?.[A.value] ?? [], M.value)), Ye = m(() => Z.value.length === 0 && Q.value.length === 0);
		return f({
			toggle: He,
			setOpen: G,
			reload: () => q(!0)
		}), ee(() => {
			O.value && !S.value && !C.value && q();
		}), (r, i) => (o(), d("aside", {
			class: "sidebar",
			part: "sidebar",
			hidden: !O.value,
			role: "complementary",
			"aria-label": v("sidebar.label", "Sidebar")
		}, [
			h(r.$slots, "header", {}, void 0, !0),
			u("div", V, [(o(!0), d(l, null, t(qe.value, (e) => (o(), d("button", {
				key: e.id,
				class: a(["tab", { active: k.value === e.id }]),
				type: "button",
				role: "tab",
				"data-tab": e.id,
				"aria-selected": k.value === e.id,
				onClick: (t) => Ue(e.id)
			}, n(e.label), 11, pe))), 128))]),
			T.value ? (o(), d("div", me, [te(n(v("sidebar.loadFailed", "목록을 불러오지 못했습니다.")) + " ", 1), u("button", {
				class: "retry",
				type: "button",
				onClick: i[0] ||= (e) => q(!0)
			}, n(v("sidebar.retry", "다시 시도")), 1)])) : S.value ? (o(), d(l, { key: 2 }, [e(u("section", ge, [e(u("input", {
				"onUpdate:modelValue": i[1] ||= (e) => j.value = e,
				class: "search-input org-search",
				type: "text",
				autocomplete: "off",
				placeholder: v("sidebar.searchPlaceholder", "검색할 이름"),
				onInput: J
			}, null, 40, _e), [[c, j.value]]), Ye.value ? (o(), d("div", ve, n(v("title.no.results", "결과 없음")), 1)) : (o(), d("ul", ye, [(o(!0), d(l, null, t(Z.value, (e) => (o(), d("li", {
				key: e.key,
				class: a(["org-li", { personal: e.personal }])
			}, [u("div", be, [u("button", {
				class: "org-list",
				type: "button",
				"aria-expanded": N.value.has(e.key),
				onClick: (t) => Ge(e.key)
			}, [u("span", Se, n(e.org.name), 1), u("span", Ce, n(e.org.projects.length || ""), 1)], 8, xe), e.personal ? ne("", !0) : (o(), d("button", {
				key: 0,
				class: "star-org",
				type: "button",
				"aria-pressed": e.org.favorite,
				"aria-label": v("sidebar.favoriteOrganization", "조직 즐겨찾기"),
				onClick: (t) => Ke(e.org)
			}, [u("span", { class: a(["star", { starred: e.org.favorite }]) }, "★", 2)], 8, we))]), u("ul", Te, [(o(!0), d(l, null, t(e.projects, (e) => (o(), d("li", {
				key: e.id,
				class: "user-li"
			}, [u("a", {
				class: "project-list",
				href: e.href,
				title: e.overview ?? void 0
			}, [u("span", De, n(e.name), 1)], 8, Ee), u("button", {
				class: "star-project",
				type: "button",
				"aria-pressed": e.favorite,
				"aria-label": v("sidebar.favoriteProject", "즐겨찾기"),
				onClick: (t) => X(e)
			}, [u("span", { class: a(["star", { starred: e.favorite }]) }, "★", 2)], 8, Oe)]))), 128))])], 2))), 128)), (o(!0), d(l, null, t(Q.value, (e) => (o(), d("li", {
				key: `etc-${e.id}`,
				class: "user-li etc-favorites"
			}, [u("a", {
				class: "project-list",
				href: e.href,
				title: e.overview ?? void 0
			}, [u("span", Ae, n(e.name), 1), u("span", H, n(e.owner), 1)], 8, ke), u("button", {
				class: "star-project",
				type: "button",
				"aria-pressed": e.favorite,
				"aria-label": v("sidebar.favoriteProject", "즐겨찾기"),
				onClick: (t) => X(e)
			}, [u("span", { class: a(["star", { starred: e.favorite }]) }, "★", 2)], 8, je)]))), 128))]))], 512), [[p, k.value === "myOrganizationList"]]), e(u("section", Me, [
				e(u("input", {
					"onUpdate:modelValue": i[2] ||= (e) => M.value = e,
					class: "search-input project-search",
					type: "text",
					autocomplete: "off",
					placeholder: v("sidebar.searchPlaceholder", "검색할 이름"),
					onInput: J
				}, null, 40, Ne), [[c, M.value]]),
				u("div", Pe, [(o(!0), d(l, null, t(Je.value, (e) => (o(), d("button", {
					key: e.id,
					class: a(["subtab", { active: A.value === e.id }]),
					type: "button",
					role: "tab",
					"data-subtab": e.id,
					"aria-selected": A.value === e.id,
					onClick: (t) => We(e.id)
				}, n(e.label), 11, Fe))), 128))]),
				$.value.length === 0 ? (o(), d("div", Ie, n(v("title.no.results", "결과 없음")), 1)) : (o(), d("ul", Le, [(o(!0), d(l, null, t($.value, (e) => (o(), d("li", {
					key: e.id,
					class: "user-li"
				}, [u("a", {
					class: "project-list",
					href: e.href,
					title: e.overview ?? void 0
				}, [u("span", ze, n(e.name), 1), u("span", Be, n(e.owner), 1)], 8, Re), u("button", {
					class: "star-project",
					type: "button",
					"aria-pressed": e.favorite,
					"aria-label": v("sidebar.favoriteProject", "즐겨찾기"),
					onClick: (t) => X(e)
				}, [u("span", { class: a(["star", { starred: e.favorite }]) }, "★", 2)], 8, Ve)]))), 128))]))
			], 512), [[p, k.value === "myProjectList"]])], 64)) : (o(), d("div", he, n(v("sidebar.loading", "불러오는 중…")), 1))
		], 8, B));
	}
}), [["styles", [":host{display:block}", ".sidebar[data-v-0bbf3ae8]{box-sizing:border-box;width:var(--yona-sidebar-width,270px);background-color:var(--yona-sidebar-bg,#333);color:var(--yona-sidebar-fg,white);border-right:1px solid var(--yona-sidebar-border,black);min-height:100%;font-size:14px}.sidebar[hidden][data-v-0bbf3ae8]{display:none}.tabs[data-v-0bbf3ae8]{display:flex}.tab[data-v-0bbf3ae8]{color:var(--yona-sidebar-tab-fg,lightgray);cursor:pointer;font:inherit;background:0 0;border:none;padding:8px 10px}.tab[data-v-0bbf3ae8]:hover,.tab.active[data-v-0bbf3ae8]{color:var(--yona-sidebar-accent,#f36c22);background-color:var(--yona-sidebar-tab-active-bg,black)}.pane[data-v-0bbf3ae8]{padding:0 8px}.search-input[data-v-0bbf3ae8]{box-sizing:border-box;background-color:var(--yona-sidebar-input-bg,black);width:100%;height:34px;color:var(--yona-sidebar-fg,white);border:none;margin:8px 0 0;padding:0 6px;font-size:14px;display:block}.search-input[data-v-0bbf3ae8]:focus{outline:1px solid var(--yona-sidebar-search-accent,#e91e63)}.subtabs[data-v-0bbf3ae8]{flex-wrap:wrap;padding:10px 0 5px;display:flex}.subtab[data-v-0bbf3ae8]{color:var(--yona-sidebar-tab-fg,lightgray);cursor:pointer;font:inherit;background:0 0;border:none;padding:2px 8px}.subtab.active[data-v-0bbf3ae8]{color:var(--yona-sidebar-accent,#f36c22)}.user-ul[data-v-0bbf3ae8]{max-height:80vh;margin:0 0 10px;padding:0;list-style:none;overflow-y:auto}.project-ul[data-v-0bbf3ae8]{margin:0;padding:0;list-style:none}.org-li[data-v-0bbf3ae8]{margin:3px 0 8px}.org-row[data-v-0bbf3ae8],.user-li[data-v-0bbf3ae8]{align-items:center;display:flex}.org-list[data-v-0bbf3ae8]{min-width:0;color:var(--yona-sidebar-fg,white);font:inherit;cursor:pointer;text-align:left;background:0 0;border:none;flex:auto;justify-content:space-between;align-items:center;padding:1px 4px;font-weight:700;display:flex}.org-name[data-v-0bbf3ae8]{color:var(--yona-sidebar-org,#00bcd4);text-overflow:ellipsis;white-space:nowrap;max-width:140px;overflow:hidden}.sub-project-counter[data-v-0bbf3ae8]{color:var(--yona-sidebar-muted,grey);font-size:12px;font-weight:400}.user-li[data-v-0bbf3ae8]{line-height:normal}.project-list[data-v-0bbf3ae8]{min-width:0;color:var(--yona-sidebar-fg,white);flex:auto;justify-content:space-between;align-items:center;padding:4px 0 4px 22px;text-decoration:none;display:flex}.project-list[data-v-0bbf3ae8]:hover,.org-list[data-v-0bbf3ae8]:hover{background-color:var(--yona-sidebar-hover,#ffffff26);text-decoration:none}.project-name[data-v-0bbf3ae8]{text-overflow:ellipsis;white-space:nowrap;min-width:50px;max-width:150px;overflow:hidden}.project-owner[data-v-0bbf3ae8]{color:var(--yona-sidebar-muted,grey);text-overflow:ellipsis;white-space:nowrap;text-align:right;min-width:40px;max-width:50px;padding:0 10px;font-size:12px;overflow:hidden}.etc-favorites[data-v-0bbf3ae8]{border-top:1px dashed gray}.star-project[data-v-0bbf3ae8],.star-org[data-v-0bbf3ae8]{cursor:pointer;width:29px;color:var(--yona-sidebar-star-off,#eee);background:0 0;border:none;flex-shrink:0;padding:0}.star[data-v-0bbf3ae8]{font-size:16px}.star.starred[data-v-0bbf3ae8],.star-project:hover .star[data-v-0bbf3ae8],.star-org:hover .star[data-v-0bbf3ae8]{color:var(--yona-sidebar-star-on,#e91e63)}.no-result[data-v-0bbf3ae8],.status[data-v-0bbf3ae8]{color:var(--yona-sidebar-empty,mediumvioletred);text-align:center;margin:10px 0 25px}.error[data-v-0bbf3ae8]{color:var(--yona-sidebar-empty,mediumvioletred);text-align:center;padding:12px}.retry[data-v-0bbf3ae8]{cursor:pointer;margin-left:6px}a[data-v-0bbf3ae8]:focus-visible,button[data-v-0bbf3ae8]:focus-visible{outline:2px solid var(--yona-sidebar-accent,#f36c22);outline-offset:-2px}"]], ["__scopeId", "data-v-0bbf3ae8"]]);
//#endregion
//#region src/usermenu/element.ts
customElements.define("yona-sidebar", f(W));
//#endregion
