import { A as e, C as t, I as n, M as r, O as i, P as a, S as o, _ as s, a as c, b as ee, c as l, d as u, h as d, m as f, n as p, o as m, p as te, r as ne, u as h, w as re } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
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
function ie(e, t) {
	return t.trim() === "" ? e : e.flatMap((e) => {
		if (T(e.name, t)) return [e];
		let n = E(e.projects, t);
		return n.length > 0 ? [{
			...e,
			projects: n
		}] : [];
	});
}
function ae(e, t, n = (e) => e.favorite) {
	return t ? e.projects : e.projects.filter(n);
}
function oe(e, t, n) {
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
function se(e, t, n) {
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
var D = "yonaLeftSidebarOpen", O = "shallWeOpenLeftNavigation", k = "sidebarActiveMenu", A = ["myOrganizationList", "myProjectList"], j = [
	"recentlyVisited",
	"createdByMe",
	"watching",
	"joinmember"
];
function M(e, t) {
	try {
		return e();
	} catch {
		return t;
	}
}
function ce(e) {
	return M(() => {
		let t = e.getItem(D);
		if (t !== null) return t === "true";
		let n = e.getItem(O);
		if (n === null) return !1;
		let r = n === "true";
		return e.setItem(D, String(r)), e.removeItem(O), r;
	}, !1);
}
function le(e, t) {
	M(() => e.setItem(D, String(t)), void 0);
}
function ue(e) {
	let t = M(() => e.getItem(k), null);
	return A.find((e) => e === t) ?? "myOrganizationList";
}
function N(e, t) {
	A.includes(t) && M(() => e.setItem(k, t), void 0);
}
var P = {
	projectTab: "recentlyVisited",
	orgQuery: "",
	projectQuery: ""
}, F = (e) => `yona.sidebar.${e}`;
function I(e, t) {
	return t ? M(() => {
		let n = e.getItem(F(t));
		if (n === null) return { ...P };
		let r = JSON.parse(n), i = typeof r == "object" && r ? r : {};
		return {
			projectTab: j.find((e) => e === i.projectTab) ?? P.projectTab,
			orgQuery: typeof i.orgQuery == "string" ? i.orgQuery : "",
			projectQuery: typeof i.projectQuery == "string" ? i.projectQuery : ""
		};
	}, { ...P }) : { ...P };
}
function L(e, t, n) {
	t && M(() => e.setItem(F(t), JSON.stringify(n)), void 0);
}
//#endregion
//#region src/usermenu/YonaSidebar.vue?vue&type=script&setup=true&lang.ts
var R = ["hidden", "aria-label"], z = {
	class: "tabs",
	role: "tablist"
}, B = [
	"data-tab",
	"aria-selected",
	"onClick"
], V = {
	key: 0,
	class: "error",
	role: "alert"
}, H = {
	key: 1,
	class: "status",
	role: "status"
}, U = {
	class: "pane",
	id: "myOrganizationList"
}, de = ["placeholder"], fe = {
	key: 0,
	class: "no-result"
}, pe = {
	key: 1,
	class: "user-ul orgs"
}, me = { class: "org-row" }, he = ["aria-expanded", "onClick"], ge = { class: "org-name" }, _e = { class: "sub-project-counter" }, ve = [
	"aria-pressed",
	"aria-label",
	"onClick"
], ye = { class: "project-ul" }, be = ["href", "title"], xe = { class: "project-name" }, Se = [
	"aria-pressed",
	"aria-label",
	"onClick"
], Ce = ["href", "title"], we = { class: "project-name" }, W = { class: "project-owner" }, Te = [
	"aria-pressed",
	"aria-label",
	"onClick"
], Ee = {
	class: "pane",
	id: "myProjectList"
}, De = ["placeholder"], Oe = {
	class: "subtabs",
	role: "tablist"
}, ke = [
	"data-subtab",
	"aria-selected",
	"onClick"
], Ae = {
	key: 0,
	class: "no-result"
}, je = {
	key: 1,
	class: "user-ul"
}, Me = ["href", "title"], Ne = { class: "project-name" }, Pe = { class: "project-owner" }, Fe = [
	"aria-pressed",
	"aria-label",
	"onClick"
], G = "personal", K = /*#__PURE__*/ g(/* @__PURE__ */ s({
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
	setup(s, { expose: p }) {
		let g = s, _ = ne();
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
		let x = r(null), S = r(!1), C = r(!1), T = r(_?.hasAttribute("open") || ce(b())), D = r(ue(b())), O = r("recentlyVisited"), k = r(""), A = r(""), j = r(/* @__PURE__ */ new Set()), M = r(/* @__PURE__ */ new Set()), P = h(() => _?.getAttribute("login-id") || x.value?.loginId || "");
		i(T, (e) => {
			_?.toggleAttribute("open", e), e && !x.value && !S.value && q();
		}, { immediate: !0 });
		function F(e) {
			T.value !== e && (T.value = e, le(b(), e), _?.dispatchEvent(new CustomEvent("yona-sidebar-toggle", {
				detail: { open: e },
				bubbles: !0,
				composed: !0
			})));
		}
		function K() {
			F(!T.value);
		}
		async function q() {
			S.value = !0, C.value = !1;
			try {
				let e = await fetch(g.apiUrl, {
					headers: { Accept: "application/json" },
					credentials: "same-origin"
				});
				if (!e.ok) throw Error(String(e.status));
				let t = w(await e.json());
				M.value = new Set([
					...t.personal,
					...t.favoriteOrganizations.flatMap((e) => e.projects),
					...t.organizations.flatMap((e) => e.projects)
				].filter((e) => e.favorite).map((e) => e.id)), x.value = t;
			} catch {
				C.value = !0;
			} finally {
				S.value = !1;
			}
		}
		i(P, (e) => {
			if (!e) return;
			let t = I(b(), e);
			O.value = t.projectTab, k.value = t.orgQuery, A.value = t.projectQuery;
		}, { immediate: !0 });
		function J() {
			L(b(), P.value, {
				projectTab: O.value,
				orgQuery: k.value,
				projectQuery: A.value
			});
		}
		function Ie(e) {
			D.value = e, N(b(), e);
		}
		function Le(e) {
			O.value = e, J();
		}
		function Re(e) {
			let t = new Set(j.value);
			t.has(e) ? t.delete(e) : t.add(e), j.value = t;
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
			t && x.value && (x.value = oe(x.value, e.id, t.favored));
		}
		async function ze(e) {
			let t = await Y(g.favoriteOrganizationUrl + e.id);
			t && x.value && (x.value = se(x.value, e.id, t.favored));
		}
		let Be = h(() => [{
			id: "myOrganizationList",
			label: v("title.favorite", "즐겨찾기")
		}, {
			id: "myProjectList",
			label: v("title.project", "프로젝트")
		}]), Ve = h(() => [
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
		]), Z = h(() => {
			let e = x.value;
			return e ? ie([
				{
					id: -1,
					name: e.loginId,
					favorite: !1,
					projects: e.personal
				},
				...e.favoriteOrganizations,
				...e.organizations
			], k.value).map((e) => ({
				org: e,
				key: e.id === -1 ? G : `org-${e.id}`,
				personal: e.id === -1,
				projects: ae(e, j.value.has(e.id === -1 ? G : `org-${e.id}`) || k.value.trim() !== "", (e) => M.value.has(e.id))
			})) : [];
		}), Q = h(() => E(x.value?.favoriteProjects ?? [], k.value)), $ = h(() => E(x.value?.[O.value] ?? [], A.value)), He = h(() => Z.value.length === 0 && Q.value.length === 0);
		return p({
			toggle: K,
			setOpen: F,
			reload: q
		}), ee(() => {
			T.value && !x.value && !S.value && q();
		}), (r, i) => (o(), f("aside", {
			class: "sidebar",
			part: "sidebar",
			hidden: !T.value,
			role: "complementary",
			"aria-label": v("sidebar.label", "Sidebar")
		}, [
			re(r.$slots, "header", {}, void 0, !0),
			u("div", z, [(o(!0), f(l, null, t(Be.value, (e) => (o(), f("button", {
				key: e.id,
				class: a(["tab", { active: D.value === e.id }]),
				type: "button",
				role: "tab",
				"data-tab": e.id,
				"aria-selected": D.value === e.id,
				onClick: (t) => Ie(e.id)
			}, n(e.label), 11, B))), 128))]),
			C.value ? (o(), f("div", V, [d(n(v("sidebar.loadFailed", "목록을 불러오지 못했습니다.")) + " ", 1), u("button", {
				class: "retry",
				type: "button",
				onClick: q
			}, n(v("sidebar.retry", "다시 시도")), 1)])) : x.value ? (o(), f(l, { key: 2 }, [e(u("section", U, [e(u("input", {
				"onUpdate:modelValue": i[0] ||= (e) => k.value = e,
				class: "search-input org-search",
				type: "text",
				autocomplete: "off",
				placeholder: v("sidebar.searchPlaceholder", "검색할 이름"),
				onInput: J
			}, null, 40, de), [[c, k.value]]), He.value ? (o(), f("div", fe, n(v("title.no.results", "결과 없음")), 1)) : (o(), f("ul", pe, [(o(!0), f(l, null, t(Z.value, (e) => (o(), f("li", {
				key: e.key,
				class: a(["org-li", { personal: e.personal }])
			}, [u("div", me, [u("button", {
				class: "org-list",
				type: "button",
				"aria-expanded": j.value.has(e.key),
				onClick: (t) => Re(e.key)
			}, [u("span", ge, n(e.org.name), 1), u("span", _e, n(e.org.projects.length || ""), 1)], 8, he), e.personal ? te("", !0) : (o(), f("button", {
				key: 0,
				class: "star-org",
				type: "button",
				"aria-pressed": e.org.favorite,
				"aria-label": v("sidebar.favoriteOrganization", "조직 즐겨찾기"),
				onClick: (t) => ze(e.org)
			}, [u("span", { class: a(["star", { starred: e.org.favorite }]) }, "★", 2)], 8, ve))]), u("ul", ye, [(o(!0), f(l, null, t(e.projects, (e) => (o(), f("li", {
				key: e.id,
				class: "user-li"
			}, [u("a", {
				class: "project-list",
				href: e.href,
				title: e.overview ?? void 0
			}, [u("span", xe, n(e.name), 1)], 8, be), u("button", {
				class: "star-project",
				type: "button",
				"aria-pressed": e.favorite,
				"aria-label": v("sidebar.favoriteProject", "즐겨찾기"),
				onClick: (t) => X(e)
			}, [u("span", { class: a(["star", { starred: e.favorite }]) }, "★", 2)], 8, Se)]))), 128))])], 2))), 128)), (o(!0), f(l, null, t(Q.value, (e) => (o(), f("li", {
				key: `etc-${e.id}`,
				class: "user-li etc-favorites"
			}, [u("a", {
				class: "project-list",
				href: e.href,
				title: e.overview ?? void 0
			}, [u("span", we, n(e.name), 1), u("span", W, n(e.owner), 1)], 8, Ce), u("button", {
				class: "star-project",
				type: "button",
				"aria-pressed": e.favorite,
				"aria-label": v("sidebar.favoriteProject", "즐겨찾기"),
				onClick: (t) => X(e)
			}, [u("span", { class: a(["star", { starred: e.favorite }]) }, "★", 2)], 8, Te)]))), 128))]))], 512), [[m, D.value === "myOrganizationList"]]), e(u("section", Ee, [
				e(u("input", {
					"onUpdate:modelValue": i[1] ||= (e) => A.value = e,
					class: "search-input project-search",
					type: "text",
					autocomplete: "off",
					placeholder: v("sidebar.searchPlaceholder", "검색할 이름"),
					onInput: J
				}, null, 40, De), [[c, A.value]]),
				u("div", Oe, [(o(!0), f(l, null, t(Ve.value, (e) => (o(), f("button", {
					key: e.id,
					class: a(["subtab", { active: O.value === e.id }]),
					type: "button",
					role: "tab",
					"data-subtab": e.id,
					"aria-selected": O.value === e.id,
					onClick: (t) => Le(e.id)
				}, n(e.label), 11, ke))), 128))]),
				$.value.length === 0 ? (o(), f("div", Ae, n(v("title.no.results", "결과 없음")), 1)) : (o(), f("ul", je, [(o(!0), f(l, null, t($.value, (e) => (o(), f("li", {
					key: e.id,
					class: "user-li"
				}, [u("a", {
					class: "project-list",
					href: e.href,
					title: e.overview ?? void 0
				}, [u("span", Ne, n(e.name), 1), u("span", Pe, n(e.owner), 1)], 8, Me), u("button", {
					class: "star-project",
					type: "button",
					"aria-pressed": e.favorite,
					"aria-label": v("sidebar.favoriteProject", "즐겨찾기"),
					onClick: (t) => X(e)
				}, [u("span", { class: a(["star", { starred: e.favorite }]) }, "★", 2)], 8, Fe)]))), 128))]))
			], 512), [[m, D.value === "myProjectList"]])], 64)) : (o(), f("div", H, n(v("sidebar.loading", "불러오는 중…")), 1))
		], 8, R));
	}
}), [["styles", [":host{display:block}", ".sidebar[data-v-7a4b455a]{box-sizing:border-box;width:var(--yona-sidebar-width,270px);background-color:var(--yona-sidebar-bg,#333);color:var(--yona-sidebar-fg,white);border-right:1px solid var(--yona-sidebar-border,black);min-height:100%;font-size:14px}.sidebar[hidden][data-v-7a4b455a]{display:none}.tabs[data-v-7a4b455a]{display:flex}.tab[data-v-7a4b455a]{color:var(--yona-sidebar-tab-fg,lightgray);cursor:pointer;font:inherit;background:0 0;border:none;padding:8px 10px}.tab[data-v-7a4b455a]:hover,.tab.active[data-v-7a4b455a]{color:var(--yona-sidebar-accent,#f36c22);background-color:var(--yona-sidebar-tab-active-bg,black)}.pane[data-v-7a4b455a]{padding:0 8px}.search-input[data-v-7a4b455a]{box-sizing:border-box;background-color:var(--yona-sidebar-input-bg,black);width:100%;height:34px;color:var(--yona-sidebar-fg,white);border:none;margin:8px 0 0;padding:0 6px;font-size:14px;display:block}.search-input[data-v-7a4b455a]:focus{outline:1px solid var(--yona-sidebar-search-accent,#e91e63)}.subtabs[data-v-7a4b455a]{flex-wrap:wrap;padding:10px 0 5px;display:flex}.subtab[data-v-7a4b455a]{color:var(--yona-sidebar-tab-fg,lightgray);cursor:pointer;font:inherit;background:0 0;border:none;padding:2px 8px}.subtab.active[data-v-7a4b455a]{color:var(--yona-sidebar-accent,#f36c22)}.user-ul[data-v-7a4b455a]{max-height:80vh;margin:0 0 10px;padding:0;list-style:none;overflow-y:auto}.project-ul[data-v-7a4b455a]{margin:0;padding:0;list-style:none}.org-li[data-v-7a4b455a]{margin:3px 0 8px}.org-row[data-v-7a4b455a],.user-li[data-v-7a4b455a]{align-items:center;display:flex}.org-list[data-v-7a4b455a]{min-width:0;color:var(--yona-sidebar-fg,white);font:inherit;cursor:pointer;text-align:left;background:0 0;border:none;flex:auto;justify-content:space-between;align-items:center;padding:1px 4px;font-weight:700;display:flex}.org-name[data-v-7a4b455a]{color:var(--yona-sidebar-org,#00bcd4);text-overflow:ellipsis;white-space:nowrap;max-width:140px;overflow:hidden}.sub-project-counter[data-v-7a4b455a]{color:var(--yona-sidebar-muted,grey);font-size:12px;font-weight:400}.user-li[data-v-7a4b455a]{line-height:normal}.project-list[data-v-7a4b455a]{min-width:0;color:var(--yona-sidebar-fg,white);flex:auto;justify-content:space-between;align-items:center;padding:4px 0 4px 22px;text-decoration:none;display:flex}.project-list[data-v-7a4b455a]:hover,.org-list[data-v-7a4b455a]:hover{background-color:var(--yona-sidebar-hover,#ffffff26);text-decoration:none}.project-name[data-v-7a4b455a]{text-overflow:ellipsis;white-space:nowrap;min-width:50px;max-width:150px;overflow:hidden}.project-owner[data-v-7a4b455a]{color:var(--yona-sidebar-muted,grey);text-overflow:ellipsis;white-space:nowrap;text-align:right;min-width:40px;max-width:50px;padding:0 10px;font-size:12px;overflow:hidden}.etc-favorites[data-v-7a4b455a]{border-top:1px dashed gray}.star-project[data-v-7a4b455a],.star-org[data-v-7a4b455a]{cursor:pointer;width:29px;color:var(--yona-sidebar-star-off,#eee);background:0 0;border:none;flex-shrink:0;padding:0}.star[data-v-7a4b455a]{font-size:16px}.star.starred[data-v-7a4b455a],.star-project:hover .star[data-v-7a4b455a],.star-org:hover .star[data-v-7a4b455a]{color:var(--yona-sidebar-star-on,#e91e63)}.no-result[data-v-7a4b455a],.status[data-v-7a4b455a]{color:var(--yona-sidebar-empty,mediumvioletred);text-align:center;margin:10px 0 25px}.error[data-v-7a4b455a]{color:var(--yona-sidebar-empty,mediumvioletred);text-align:center;padding:12px}.retry[data-v-7a4b455a]{cursor:pointer;margin-left:6px}a[data-v-7a4b455a]:focus-visible,button[data-v-7a4b455a]:focus-visible{outline:2px solid var(--yona-sidebar-accent,#f36c22);outline-offset:-2px}"]], ["__scopeId", "data-v-7a4b455a"]]);
//#endregion
//#region src/usermenu/element.ts
customElements.define("yona-sidebar", p(K));
//#endregion
