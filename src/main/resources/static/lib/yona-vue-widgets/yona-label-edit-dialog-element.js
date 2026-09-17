import { C as e, D as t, F as n, I as r, M as i, N as a, P as o, S as s, _ as c, b as l, c as u, d, f, g as p, l as m, m as h, n as g } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { i as _, n as v, t as y } from "./YonaColorPicker-Bl3ChY9B.js";
import { n as b, t as x } from "./request-6sbQvC45.js";
//#region src/label-editor/YonaLabelEditDialog.vue?vue&type=script&setup=true&lang.ts
var S = { class: "message edit-label-form" }, C = { class: "center-txt" }, w = ["value"], T = ["placeholder"], E = { class: "center-txt buttons mt20 mb20" }, D = {
	type: "button",
	class: "ybtn ybtn-default",
	"data-dismiss": "modal"
}, O = /* @__PURE__ */ c({
	__name: "YonaLabelEditDialog",
	setup(c, { expose: g }) {
		function O() {
			return document.querySelector("yona-dialog");
		}
		function k(e) {
			O()?.show(e);
		}
		function A(e, t) {
			e && document.querySelector("yona-popover")?.showPopoverError(e, t, "bottom");
		}
		let j = (e) => new RGBColor(e), M = t("dialogRef"), N = t("nameInputRef"), P = t("categorySelectRef"), F = i({}), I = i(""), L = i(""), R = i([]), z = null;
		function B() {
			let e = [];
			return document.querySelectorAll("div[data-category-name]").forEach((t) => {
				let n = t.dataset.category, r = t.dataset.categoryName;
				n && r && e.push({
					id: n,
					name: r
				});
			}), e;
		}
		function V(e) {
			return e ? document.querySelector(`div.category-wrap[data-category-name="${CSS.escape(e)}"]`) : null;
		}
		function H(e, t) {
			let n = V(e);
			return !!(n && n.querySelector(`[data-label-name="${CSS.escape(t)}"]`));
		}
		function U(e) {
			e && (F.value = { backgroundColor: e }, I.value = v(e));
		}
		l(() => {
			R.value = B();
		});
		function W(e) {
			z = e, L.value = e.labelColor, N.value && (N.value.value = e.labelName);
			let t = P.value;
			t?.tomselect ? t.tomselect.setValue(e.categoryId) : t && (t.value = e.categoryId), M.value?.showModal(), U(e.labelColor);
		}
		function G() {
			M.value?.close();
		}
		function K(e) {
			if (e.target === M.value) {
				G();
				return;
			}
			e.target.closest?.("[data-dismiss=\"modal\"]") && G();
		}
		function q(e) {
			U(e);
		}
		function J(e) {}
		function Y(e, t, n, r) {
			if (n) try {
				let e = JSON.parse(n), t = b("label.failedTo", b(r));
				Object.keys(e).forEach((n) => {
					t += "\n" + e[n];
				}), k(t);
				return;
			} catch {}
			k(b("error.failedTo", b(r), String(e), t));
		}
		async function X() {
			if (!z || !N.value || !P.value) return;
			let e = L.value.trim(), t = N.value.value.trim(), n = P.value.value, r = P.value.tomselect, i = r ? r.options[r.getValue()] : void 0, a = i ? i.text : void 0;
			if (t !== z.labelName && H(a, t)) {
				A(N.value, b("label.error.duplicated.in.category", a || ""));
				return;
			}
			if (!_(e, j)) {
				A(document.querySelector(".edit-label-form .input-label-color"), b("label.error.color", e));
				return;
			}
			let o = {
				name: t,
				color: e,
				"category.id": n
			};
			try {
				let e = await fetch(z.updateUri, {
					method: "put",
					body: x(o)
				});
				if (!e.ok) {
					let t = await e.text();
					Y(e.status, e.statusText, t, "label.edit");
					return;
				}
				document.location.reload();
			} catch {
				k(b("label.failedTo", b("label.edit")));
			} finally {
				G();
			}
		}
		return g({
			show: W,
			hide: G
		}), (t, i) => (s(), f(m, { to: "body" }, [d("dialog", {
			ref_key: "dialogRef",
			ref: M,
			id: "editLabel",
			class: "modal yonaDialog",
			onClick: K
		}, [i[1] ||= d("div", { class: "btn-dismiss" }, [d("button", {
			type: "button",
			class: "btn-transparent",
			"data-dismiss": "modal"
		}, "×")], -1), d("div", S, [d("div", C, [
			d("select", {
				ref_key: "categorySelectRef",
				ref: P,
				name: "category.id",
				"data-toggle": "tomselect"
			}, [(s(!0), h(u, null, e(R.value, (e) => (s(), h("option", {
				key: e.id,
				value: e.id
			}, r(e.name), 9, w))), 128))], 512),
			d("input", {
				ref_key: "nameInputRef",
				ref: N,
				type: "text",
				name: "name",
				class: o(["text input-label-name", I.value]),
				maxlength: "250",
				placeholder: a(b)("label.name"),
				style: n(F.value)
			}, null, 14, T),
			p(y, {
				modelValue: L.value,
				"onUpdate:modelValue": i[0] ||= (e) => L.value = e,
				"edit-variant": "",
				"preset-colors": [
					"#FF7770",
					"#F18CA7",
					"#FFB399",
					"#F1D55C",
					"#A5D870",
					"#32CDA1",
					"#9985D8",
					"#40A0EB",
					"#6BC4E9",
					"#DCBD98",
					"#8C8C9C",
					"#7A9CB4"
				],
				onPreview: q,
				onInvalid: J
			}, null, 8, ["modelValue"])
		]), d("div", E, [d("button", {
			type: "button",
			class: "ybtn ybtn-info btnSubmit",
			onClick: X
		}, r(a(b)("button.save")), 1), d("button", D, r(a(b)("button.cancel")), 1)])])], 512)]));
	}
});
//#endregion
//#region src/label-editor/label-edit-dialog-element.ts
customElements.define("yona-label-edit-dialog", g(O));
//#endregion
