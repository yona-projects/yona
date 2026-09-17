import { D as e, I as t, N as n, S as r, _ as i, d as a, f as o, l as s, n as c } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { n as l, t as u } from "./request-6sbQvC45.js";
//#region src/label-editor/YonaCategoryEditDialog.vue?vue&type=script&setup=true&lang.ts
var d = { class: "message edit-label-category-form" }, f = { class: "center-txt" }, p = ["placeholder"], m = { class: "desc" }, h = { value: "false" }, g = { value: "true" }, _ = { class: "center-txt buttons mt20 mb20" }, v = {
	type: "button",
	class: "ybtn ybtn-default",
	"data-dismiss": "modal"
}, y = /* @__PURE__ */ i({
	__name: "YonaCategoryEditDialog",
	setup(i, { expose: c }) {
		function y() {
			return document.querySelector("yona-dialog");
		}
		function b(e) {
			y()?.show(e);
		}
		let x = e("dialogRef"), S = e("nameInputRef"), C = e("exclusiveSelectRef"), w = null;
		function T(e) {
			w = e, S.value && (S.value.value = e.categoryName);
			let t = C.value;
			t?.tomselect ? t.tomselect.setValue(String(e.categoryIsExclusive)) : t && (t.value = String(e.categoryIsExclusive)), x.value?.showModal();
		}
		function E() {
			x.value?.close();
		}
		function D(e) {
			if (e.target === x.value) {
				E();
				return;
			}
			e.target.closest?.("[data-dismiss=\"modal\"]") && E();
		}
		function O(e, t, n, r) {
			if (n) try {
				let e = JSON.parse(n), t = l("label.failedTo", l(r));
				Object.keys(e).forEach((n) => {
					t += "\n" + e[n];
				}), b(t);
				return;
			} catch {}
			b(l("error.failedTo", l(r), String(e), t));
		}
		async function k() {
			if (!w || !S.value || !C.value) return;
			let e = {
				id: w.categoryId,
				name: S.value.value.trim(),
				isExclusive: C.value.value,
				"project.id": w.projectId
			};
			try {
				let t = await fetch(w.categoryUpdateUri, {
					method: "put",
					body: u(e)
				});
				if (!t.ok) {
					let e = await t.text();
					O(t.status, t.statusText, e, "label.category.edit");
					return;
				}
				document.location.reload();
			} catch {
				b(l("label.failedTo", l("label.category.edit")));
			} finally {
				E();
			}
		}
		return c({
			show: T,
			hide: E
		}), (e, i) => (r(), o(s, { to: "body" }, [a("dialog", {
			ref_key: "dialogRef",
			ref: x,
			id: "editCategory",
			class: "modal yonaDialog",
			onClick: D
		}, [i[0] ||= a("div", { class: "btn-dismiss" }, [a("button", {
			type: "button",
			class: "btn-transparent",
			"data-dismiss": "modal"
		}, "×")], -1), a("div", d, [a("div", f, [a("input", {
			ref_key: "nameInputRef",
			ref: S,
			type: "text",
			name: "name",
			class: "text category-name",
			placeholder: n(l)("label.category")
		}, null, 8, p), a("div", m, [a("span", null, t(n(l)("label.category.option")), 1), a("select", {
			ref_key: "exclusiveSelectRef",
			ref: C,
			name: "isExclusive",
			"data-toggle": "tomselect",
			"data-dropdown-css-class": "tomselect-without-searchbox"
		}, [a("option", h, t(n(l)("label.category.option.multiple")), 1), a("option", g, t(n(l)("label.category.option.single")), 1)], 512)])]), a("div", _, [a("button", {
			type: "button",
			class: "ybtn ybtn-info btnSubmit",
			onClick: k
		}, t(n(l)("button.save")), 1), a("button", v, t(n(l)("button.cancel")), 1)])])], 512)]));
	}
});
//#endregion
//#region src/label-editor/category-edit-dialog-element.ts
customElements.define("yona-category-edit-dialog", c(y));
//#endregion
