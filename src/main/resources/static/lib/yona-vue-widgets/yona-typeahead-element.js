import { C as e, D as t, F as n, M as r, P as i, S as a, _ as o, c as s, d as c, m as l, n as u, w as d } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as f } from "./_plugin-vue_export-helper-B3ysoDQm.js";
//#region src/typeahead/YonaTypeahead.vue?vue&type=script&setup=true&lang.ts
var p = { class: "typeahead-wrap" }, m = ["innerHTML"], h = /*#__PURE__*/ f(/* @__PURE__ */ o({
	__name: "YonaTypeahead",
	setup(o, { expose: u }) {
		let f = t("slotRef"), h = r(!1), g = r([]), _ = r(0), v = () => {}, y = 0, b = 10, x = "";
		function S() {
			return (f.value?.assignedElements?.() ?? []).find((e) => e.tagName === "INPUT") ?? null;
		}
		function C(e) {
			v = e.source, y = e.minLength ?? 0, b = e.limit ?? 10;
		}
		function w(e) {
			let t = x.toLowerCase(), n = [], r = [], i = [];
			return e.forEach((e) => {
				e.toLowerCase().indexOf(t) === 0 ? n.push(e) : e.indexOf(x) > -1 ? r.push(e) : i.push(e);
			}), n.concat(r, i);
		}
		function T(e) {
			let t = x.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&");
			return e.replace(RegExp(`(${t})`, "ig"), "<strong>$1</strong>");
		}
		function E(e) {
			g.value = e.slice(0, b).map((e) => ({
				value: e,
				highlightedHtml: T(e)
			})), _.value = 0;
		}
		function D(e) {
			let t = x.toLowerCase(), n = w(e.filter((e) => String(e).toLowerCase().indexOf(t) > -1));
			if (n.length === 0) {
				h.value = !1;
				return;
			}
			E(n), h.value = !0;
		}
		function O() {
			let e = S();
			if (e) {
				if (x = e.value, !x || x.length < y) {
					h.value = !1;
					return;
				}
				typeof v == "function" ? v(x, D) : Array.isArray(v) && D(v.slice());
			}
		}
		function k() {
			let e = S(), t = g.value[_.value];
			if (!e || !t) {
				h.value = !1;
				return;
			}
			e.value = t.value, e.dispatchEvent(new Event("change", { bubbles: !0 })), h.value = !1;
		}
		function A() {
			O();
		}
		function j(e) {
			if (h.value) switch (e.keyCode) {
				case 9:
				case 13:
				case 27:
					e.preventDefault();
					break;
				case 38:
					e.preventDefault(), _.value = g.value.length ? (_.value - 1 + g.value.length) % g.value.length : 0;
					break;
				case 40: e.preventDefault(), _.value = g.value.length ? (_.value + 1) % g.value.length : 0;
			}
		}
		function M(e) {
			switch (e.keyCode) {
				case 9:
				case 13:
					h.value && k();
					return;
				case 27:
					h.value &&= !1;
					return;
			}
		}
		function N() {
			h.value = !1;
		}
		function P(e) {
			e.preventDefault();
		}
		function F(e) {
			let t = e.target.closest("li");
			if (!t) return;
			e.preventDefault();
			let n = Array.from(t.parentElement?.children ?? []).indexOf(t);
			n >= 0 && (_.value = n, k()), S()?.focus();
		}
		function I(e) {
			let t = e.target.closest("li");
			if (!t) return;
			let n = Array.from(t.parentElement?.children ?? []).indexOf(t);
			n >= 0 && (_.value = n);
		}
		function L() {
			let e = S();
			e && (e.addEventListener("keydown", j), e.addEventListener("keyup", M), e.addEventListener("input", A), e.addEventListener("blur", N));
		}
		return u({ configure: C }), (t, r) => (a(), l("div", p, [d(t.$slots, "default", {
			ref_key: "slotRef",
			ref: f,
			onSlotchange: L
		}, void 0, !0), c("ul", {
			class: "typeahead dropdown-menu",
			style: n({ display: h.value ? "block" : "none" }),
			onMousedown: P,
			onClick: F,
			onMouseover: I
		}, [(a(!0), l(s, null, e(g.value, (e, t) => (a(), l("li", {
			key: e.value,
			class: i({ active: t === _.value })
		}, [c("a", {
			href: "#",
			innerHTML: e.highlightedHtml
		}, null, 8, m)], 2))), 128))], 36)]));
	}
}), [["styles", [":host{display:inline-block;position:relative}", ".typeahead-wrap[data-v-b8f532c5]{display:contents}.typeahead.dropdown-menu[data-v-b8f532c5]{z-index:1000;float:left;background-color:#fff;background-clip:padding-box;border:1px solid #0003;border-radius:6px;min-width:160px;margin:2px 0 0;padding:5px 0;list-style:none;position:absolute;top:100%;left:0;box-shadow:0 5px 10px #0003}.typeahead.dropdown-menu>li>a[data-v-b8f532c5]{clear:both;color:#333;white-space:nowrap;cursor:pointer;padding:3px 20px;font-weight:400;line-height:20px;text-decoration:none;display:block}.typeahead.dropdown-menu>li>a[data-v-b8f532c5]:hover,.typeahead.dropdown-menu>li.active>a[data-v-b8f532c5]{color:#fff;background-color:#0081c2;background-image:linear-gradient(#08c,#0077b3);background-repeat:repeat-x;outline:0}"]], ["__scopeId", "data-v-b8f532c5"]]);
//#endregion
//#region src/typeahead/element.ts
customElements.define("yona-typeahead", u(h));
//#endregion
