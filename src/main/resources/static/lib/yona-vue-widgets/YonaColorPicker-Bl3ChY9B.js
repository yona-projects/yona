import { C as e, D as t, F as n, M as r, N as i, O as a, P as o, S as s, _ as c, c as l, d as u, m as d } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { n as f } from "./request-6sbQvC45.js";
//#region src/label-editor/color.ts
function p(e, t) {
	let n = t(e || "");
	return n && n.ok ? n.toHex() : !1;
}
function m(e, t) {
	if (e.indexOf("#") === 0 && e.length !== 4 && e.length !== 7) return !1;
	let n = t(e);
	return !!(n && n.ok);
}
function h(e) {
	return [
		"",
		"-moz-",
		"-webkit-"
	].map((t) => t + e).join(";");
}
function g(e) {
	let t = e.replace("#", ""), n = parseInt(t.substring(0, 2), 16), r = parseInt(t.substring(2, 4), 16), i = parseInt(t.substring(4, 6), 16);
	return n * .21 + r * .72 + i * .07 > 192 ? "dimgray" : "white";
}
//#endregion
//#region src/label-editor/YonaColorPicker.vue?vue&type=script&setup=true&lang.ts
var _ = ["onClick"], v = ["value", "placeholder"], y = /* @__PURE__ */ c({
	__name: "YonaColorPicker",
	props: {
		presetColors: {},
		modelValue: {},
		editVariant: { type: Boolean }
	},
	emits: [
		"update:modelValue",
		"preview",
		"invalid"
	],
	setup(c, { expose: g, emit: y }) {
		let b = c, x = y, S = (e) => new RGBColor(e), C = r(null), w = t("colorInputRef");
		function T(e) {
			w.value && (w.value.style.cssText = h(`box-shadow: inset 25px 0 0 ${e} !important`));
		}
		function E(e, t) {
			let n = p(e, S);
			n && (C.value = t, x("update:modelValue", n), T(n), x("preview", n));
		}
		function D(e) {
			let t = e.target.value;
			x("update:modelValue", t), m(t, S) && (T(t), x("preview", t));
		}
		function O(e) {
			let t = e.target.value;
			if (t.length < 1) return;
			if (!m(t, S)) {
				x("invalid", t);
				return;
			}
			let n = p(t, S);
			x("update:modelValue", n), T(n), x("preview", n);
		}
		return a(() => b.modelValue, (e) => {
			e && T(e);
		}, { immediate: !0 }), g({ focus: () => w.value?.focus() }), (t, r) => (s(), d("div", { class: o(["label-preset-colors", { edit: c.editVariant }]) }, [(s(!0), d(l, null, e(c.presetColors, (e, t) => (s(), d("button", {
			key: t,
			type: "button",
			class: o(["issue-label btn-preset-color", { active: C.value === t }]),
			style: n({ backgroundColor: e }),
			onClick: (n) => E(e, t)
		}, null, 14, _))), 128)), u("input", {
			ref_key: "colorInputRef",
			ref: w,
			type: "text",
			class: "input-small input-label-color",
			value: c.modelValue,
			placeholder: i(f)("label.customColor"),
			onKeyup: D,
			onBlur: O
		}, null, 40, v)], 2));
	}
});
//#endregion
export { m as i, g as n, p as r, y as t };
