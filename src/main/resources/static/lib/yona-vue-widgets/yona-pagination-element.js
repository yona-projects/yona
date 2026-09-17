import { I as e, M as t, N as n, S as r, _ as i, c as a, d as o, j as s, m as c, n as l, p as u } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as d } from "./_plugin-vue_export-helper-B3ysoDQm.js";
//#region src/pagination/pagination.ts
function f(e) {
	let t = e == null ? e : String(e);
	return !Array.isArray(e) && Number(t) - parseFloat(String(t)) + 1 >= 0;
}
var p = /^.[0-9]*$/;
function m(e) {
	return e != null && p.test(String(e));
}
function h(e, t) {
	return new URL(e.replace("&amp;", "&"), t);
}
function g(e, t, n, r) {
	let i = h(e, r).searchParams.get(t), a = i == null ? NaN : parseInt(i, 10);
	return Number.isNaN(a) || a === 0 ? n : a;
}
function _(e, t, n, r) {
	let i = h(e, r);
	return i.searchParams.set(n, String(t)), i.toString();
}
function v(e) {
	if (!f(e)) throw Error("options.current is not valid: " + e);
}
function y(e, t, n) {
	let r = t.url ?? n, i = t.firstPage ?? 1, a = t.paramNameForPage ?? "pageNum", o = m(t.current) ? Number(t.current) : g(r, a, i, n);
	return v(o), {
		url: r,
		current: o,
		firstPage: i,
		totalPages: e,
		paramNameForPage: a,
		hasPrev: t.hasPrev ?? o > i,
		hasNext: t.hasNext ?? o < e,
		submit: t.submit
	};
}
function b(e, t, n, r) {
	if (!p.test(e)) return {
		valid: !1,
		value: String(r)
	};
	let i = parseInt(e, 10);
	return i < t ? {
		valid: !0,
		value: String(t)
	} : i > n ? {
		valid: !0,
		value: String(n)
	} : {
		valid: !0,
		value: e
	};
}
//#endregion
//#region src/pagination/YonaPagination.vue?vue&type=script&setup=true&lang.ts
var x = {
	key: 0,
	class: "page-navigation-wrap"
}, S = { class: "page-nums" }, C = { class: "page-num ikon" }, w = ["href"], T = { class: "off" }, E = { class: "page-num" }, D = [
	"name",
	"max",
	"value"
], O = { class: "page-num" }, k = { class: "page-num ikon" }, A = ["href"], j = { class: "off" }, M = /*#__PURE__*/ d(/* @__PURE__ */ i({
	inheritAttrs: !1,
	__name: "YonaPagination",
	setup(i, { expose: l }) {
		let d = t(!1), f = s({
			current: 1,
			firstPage: 1,
			totalPages: 1,
			paramNameForPage: "pageNum",
			hasPrev: !1,
			hasNext: !1,
			url: ""
		}), p, m = t("1");
		function h() {
			return (typeof Messages == "function" ? Messages("button.prevPage") : "") || "PREV";
		}
		function g() {
			return (typeof Messages == "function" ? Messages("button.nextPage") : "") || "NEXT";
		}
		function v() {
			return _(f.url, f.current - 1, f.paramNameForPage, window.location.href);
		}
		function M() {
			return _(f.url, f.current + 1, f.paramNameForPage, window.location.href);
		}
		function N(e, t) {
			typeof yona < "u" && yona?.ShortcutKey && yona.ShortcutKey.setKeymapLink({ [e]: t });
		}
		function P(e, t = {}) {
			if (!(e > 0)) return;
			let n = window.location.href, r = y(e, t, n);
			f.current = r.current, f.firstPage = r.firstPage, f.totalPages = r.totalPages, f.paramNameForPage = r.paramNameForPage, f.hasPrev = r.hasPrev, f.hasNext = r.hasNext, f.url = r.url, p = r.submit, m.value = String(r.current), d.value = !0, N("LEFT", f.hasPrev ? v() : ""), N("RIGHT", f.hasNext ? M() : "");
		}
		function F() {
			p && p(f.current - 1);
		}
		function I() {
			p && p(f.current + 1);
		}
		function L(e) {
			let t = e.target, n = b(t.value, 1, f.totalPages, f.current);
			m.value = n.value, t.value !== n.value && (t.value = n.value), n.valid && p && p(Number(n.value));
		}
		function R(e) {
			e.key !== "Enter" || p || (e.preventDefault(), window.location.href = _(f.url, m.value, f.paramNameForPage, window.location.href));
		}
		function z(e) {
			e.target.select();
		}
		return l({ update: P }), (t, i) => d.value ? (r(), c("div", x, [o("ul", S, [
			o("li", C, [f.hasPrev ? (r(), c("a", {
				key: 0,
				"pjax-page": "",
				href: n(p) ? "javascript: void(0);" : v(),
				onClick: F
			}, [i[0] ||= o("i", { class: "ico btn-pg-prev" }, null, -1), o("span", null, e(h()), 1)], 8, w)) : (r(), c(a, { key: 1 }, [i[1] ||= o("i", { class: "ico btn-pg-prev off" }, null, -1), o("span", T, e(h()), 1)], 64))]),
			o("li", E, [o("input", {
				type: "number",
				pattern: "[0-9]*",
				class: "input-mini nospinner",
				name: f.paramNameForPage,
				max: f.totalPages,
				min: "1",
				value: m.value,
				onInput: L,
				onKeydown: R,
				onClick: z
			}, null, 40, D)]),
			i[4] ||= o("li", { class: "page-num delimiter" }, "/", -1),
			o("li", O, e(f.totalPages), 1),
			o("li", k, [f.hasNext ? (r(), c("a", {
				key: 0,
				"pjax-page": "",
				href: n(p) ? "javascript: void(0);" : M(),
				onClick: I
			}, [o("span", null, e(g()), 1), i[2] ||= o("i", { class: "ico btn-pg-next" }, null, -1)], 8, A)) : (r(), c(a, { key: 1 }, [o("span", j, e(g()), 1), i[3] ||= o("i", { class: "ico btn-pg-next off" }, null, -1)], 64))])
		])])) : u("", !0);
	}
}), [["styles", [":host{display:block}", ".page-navigation-wrap[data-v-77d0c571]{text-align:center;clear:both;width:100%;margin:20px 0}.page-navigation-wrap .page-nums[data-v-77d0c571]{margin:0 0 0 -120px;padding:0;font-size:0;list-style:none;display:inline-block}@media (width<=720px){.page-navigation-wrap .page-nums[data-v-77d0c571]{margin-left:0}}.page-navigation-wrap .page-nums .page-num[data-v-77d0c571]{color:#8e9094;padding:0 10px;font-size:12px;display:inline-block}.page-navigation-wrap .page-nums .page-num .input-mini[data-v-77d0c571]{text-align:center;border:1px solid #eee;width:30px;margin:0;font-weight:700}.page-navigation-wrap .page-nums .page-num .input-mini[data-v-77d0c571]:hover,.page-navigation-wrap .page-nums .page-num .input-mini[data-v-77d0c571]:focus{color:#f36c22;border-color:#f36c22;box-shadow:inset -1px -1px 2px #0000001a}.page-navigation-wrap .page-nums .page-num.ikon[data-v-77d0c571]{padding:0 5px}.page-navigation-wrap .page-nums .page-num.ikon[data-v-77d0c571]:nth-child(4n-2){padding-right:10px}.page-navigation-wrap .page-nums .page-num.ikon[data-v-77d0c571]:nth-child(5n-2){padding-left:10px}.page-navigation-wrap .page-nums .page-num.ikon span[data-v-77d0c571]{color:#f36c22;font-size:11px}.page-navigation-wrap .page-nums .page-num.ikon span.off[data-v-77d0c571]{color:#8e9094}.page-navigation-wrap .page-nums .page-num.delimiter[data-v-77d0c571]{color:#ddd;padding:0 5px}.page-navigation-wrap .page-nums .page-num .nospinner[data-v-77d0c571]{-moz-appearance:textfield}.ico[data-v-77d0c571]{vertical-align:middle;background-image:url(/images/sprite.png);background-repeat:no-repeat;display:inline-block}.btn-pg-next[data-v-77d0c571]{background-position:-146px -139px;width:6px;height:9px;margin-left:10px}.btn-pg-next.off[data-v-77d0c571]{background-position:-23px -13px}.btn-pg-prev[data-v-77d0c571]{background-position:-136px -139px;width:6px;height:9px;margin-right:10px}.btn-pg-prev.off[data-v-77d0c571]{background-position:-164px -2px}"]], ["__scopeId", "data-v-77d0c571"]]);
//#endregion
//#region src/pagination/element.ts
customElements.define("yona-pagination", l(M));
//#endregion
