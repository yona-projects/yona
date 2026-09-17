import { A as e, D as t, F as n, I as r, M as i, N as a, P as o, S as s, T as c, _ as l, a as u, b as d, c as f, d as p, f as m, g as h, k as g, l as _, m as ee, n as v, r as y, s as b, u as x, w as S } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { n as C, r as w, t as T } from "./YonaColorPicker-Bl3ChY9B.js";
import { n as E, t as D } from "./request-6sbQvC45.js";
//#region src/label-editor/YonaNewLabelForm.vue?vue&type=script&setup=true&lang.ts
var O = { class: "form-legend" }, k = { class: "form-wrap" }, A = ["placeholder"], j = ["placeholder"], te = {
	type: "submit",
	class: "ybtn ybtn-primary btn-submit"
}, M = /* @__PURE__ */ l({
	__name: "YonaNewLabelForm",
	setup(l, { expose: v }) {
		let M = [
			"#f44336",
			"#e91e63",
			"#9c27b0",
			"#3f51b5",
			"#2196f3",
			"#03a9f4",
			"#00bcd4",
			"#009688",
			"#4caf50",
			"#8bc34a",
			"#cddc39",
			"#ffeb3b",
			"#ffc107",
			"#ff9800",
			"#ff5722",
			"#795548",
			"#9e9e9e"
		], N = y(), P = (e) => new RGBColor(e), F = i(""), I = i(""), L = i(""), R = i(!1), z = x(() => ({ display: R.value ? "block" : "none" })), B = i({}), V = i(""), H = t("nameInputRef"), U = t("categoryTypeaheadRef"), W = "", G;
		function K() {
			return document.querySelector("yona-dialog");
		}
		function q(e) {
			K()?.show(e);
		}
		function J() {
			let e = [];
			return document.querySelectorAll("div[data-category-name]").forEach((t) => {
				let n = t.dataset.categoryName;
				n && e.indexOf(n) < 0 && e.push(n);
			}), e;
		}
		function Y(e) {
			return J().indexOf(e) < 0;
		}
		function X(e) {
			return document.querySelector(`div.category-wrap[data-category-name="${CSS.escape(e)}"]`);
		}
		function Z(e, t) {
			let n = X(e);
			return !!(n && n.querySelector(`[data-label-name="${CSS.escape(t)}"]`));
		}
		function Q(e) {
			e && (B.value = { backgroundColor: e }, V.value = C(e));
		}
		function ne() {
			let e = Date.now() % M.length;
			return w(M[e] ?? "", P);
		}
		function re(e) {
			let t = X(e), n = t ? t.querySelector(".issue-label") : null, r = n ? getComputedStyle(n).backgroundColor : void 0;
			return w(r || "", P);
		}
		function ie() {
			R.value = !0;
			let e = F.value.trim(), t = Y(e) ? ne() : re(e);
			L.value.length === 0 && t && (L.value = t, Q(t));
		}
		function ae(e) {
			Q(e);
		}
		function oe(e) {
			q(E("label.error.color", e));
		}
		function se(e) {
			e.key === "Enter" && e.preventDefault();
		}
		function ce() {
			return F.value.length === 0 || I.value.length === 0 || L.value.length === 0 ? (q(E("label.failedTo", E("label.add")) + "\n" + E("label.error.empty")), !1) : w(L.value, P) !== !1 || (q(E("label.failedTo", E("label.add")) + "\n" + E("label.error.color", L.value)), !1);
		}
		function le(e, t, n, r) {
			if (n) try {
				let e = JSON.parse(n), t = E("label.failedTo", E(r));
				Object.keys(e).forEach((n) => {
					t += "\n" + e[n];
				}), q(t);
				return;
			} catch {}
			q(E("error.failedTo", E(r), String(e), t));
		}
		async function ue(e) {
			if (Z(e.categoryName, e.labelName)) {
				q(E("label.error.duplicated"));
				return;
			}
			let t;
			try {
				t = await fetch(W, {
					method: "post",
					body: D(e)
				});
			} catch {
				q(E("label.failedTo", E("label.add")));
				return;
			}
			if (!t.ok) {
				let e = await t.text();
				le(t.status, t.statusText, e, "label.add");
				return;
			}
			let n = await t.json().catch(() => null);
			if (n && typeof n == "object") {
				document.location.reload();
				return;
			}
			q(E("label.error.creationFailed"));
		}
		function $() {
			if (!ce()) return;
			let e = F.value.trim();
			if (Y(e) && G === void 0) {
				K()?.show(E("label.category.new.confirm", e), "", {
					aButtonLabels: [E("label.category.option.multiple"), E("label.category.option.single")],
					aButtonStyles: ["confirm-button-vertical", "confirm-button-vertical"],
					fOnClickButton: ({ nButtonIndex: e }) => {
						G = e === 1, $();
					}
				});
				return;
			}
			ue({
				labelName: I.value.trim(),
				labelColor: w(L.value.trim(), P),
				categoryName: e,
				categoryIsExclusive: G
			}), G = void 0;
		}
		return d(() => {
			N && (W = N.getAttribute("data-action") ?? ""), U.value?.configure({ source: J() });
		}), v({}), (t, i) => {
			let l = c("yona-typeahead");
			return s(), ee(f, null, [S(t.$slots, "default"), (s(), m(_, {
				to: a(N),
				disabled: !a(N)
			}, [p("form", {
				class: "new-label-wrap",
				onSubmit: b($, ["prevent"])
			}, [
				p("strong", O, r(a(E)("label.new")), 1),
				p("div", k, [p("div", null, [h(l, {
					ref_key: "categoryTypeaheadRef",
					ref: U
				}, {
					default: g(() => [e(p("input", {
						"onUpdate:modelValue": i[0] ||= (e) => F.value = e,
						type: "text",
						name: "category",
						class: "input-label mr5",
						maxlength: "250",
						autocomplete: "off",
						placeholder: a(E)("label.category"),
						onKeypress: se
					}, null, 40, A), [[u, F.value]])]),
					_: 1
				}, 512), e(p("input", {
					ref_key: "nameInputRef",
					ref: H,
					"onUpdate:modelValue": i[1] ||= (e) => I.value = e,
					type: "text",
					name: "name",
					class: o(["input-label", V.value]),
					maxlength: "250",
					autocomplete: "off",
					placeholder: a(E)("label.name"),
					style: n(B.value),
					onFocus: ie
				}, null, 46, j), [[u, I.value]])]), h(T, {
					modelValue: L.value,
					"onUpdate:modelValue": i[2] ||= (e) => L.value = e,
					style: n(z.value),
					"preset-colors": M,
					onPreview: ae,
					onInvalid: oe
				}, null, 8, ["modelValue", "style"])]),
				p("button", te, r(a(E)("label.add")), 1)
			], 32)], 8, ["to", "disabled"]))], 64);
		};
	}
});
//#endregion
//#region src/label-editor/new-label-form-element.ts
customElements.define("yona-new-label-form", v(M));
//#endregion
