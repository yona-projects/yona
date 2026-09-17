import { n as e, t } from "./request-6sbQvC45.js";
//#region src/label-editor/data.ts
function n(e) {
	if (e !== void 0) {
		if (e === "true") return !0;
		if (e === "false") return !1;
		if (e === "null") return null;
		if (e !== "" && e === String(Number(e))) return Number(e);
		if (/^(?:\{[\s\S]*\}|\[[\s\S]*\])$/.test(e)) try {
			return JSON.parse(e);
		} catch {}
		return e;
	}
}
function r(e, t) {
	return e ? n(e.dataset[t]) : void 0;
}
//#endregion
//#region src/label-editor/list-adapter.ts
function i() {
	return document.querySelector("yona-dialog");
}
function a(e) {
	return typeof e != "string" && typeof e != "number" ? null : document.querySelector(`div.category-wrap[data-category-name="${CSS.escape(String(e))}"]`);
}
function o(e) {
	let t = a(e);
	return !t || t.querySelectorAll("tr[data-label-id]").length === 0;
}
function s(e, t) {
	document.querySelector(`tr[data-label-id="${CSS.escape(String(t))}"]`)?.remove(), o(e) && a(e)?.remove();
}
async function c(e) {
	let n = r(e, "deleteUri");
	(await fetch(n, {
		method: "post",
		body: t({ _method: "delete" })
	})).ok && s(r(e, "categoryName"), r(e, "labelId"));
}
function l(t) {
	i()?.show(e("label.confirm.delete"), "", {
		aButtonLabels: [e("button.cancel"), e("button.confirm")],
		fOnClickButton: ({ nButtonIndex: e }) => {
			e === 1 && c(t);
		}
	});
}
function u(e) {
	document.querySelector("yona-label-edit-dialog")?.show({
		categoryId: String(r(e, "categoryId") ?? ""),
		labelName: String(r(e, "labelName") ?? ""),
		labelColor: String(r(e, "labelColor") ?? ""),
		updateUri: String(r(e, "updateUri") ?? "")
	});
}
function d(e) {
	document.querySelector("yona-category-edit-dialog")?.show({
		projectId: String(r(e, "projectId") ?? ""),
		categoryId: String(r(e, "categoryId") ?? ""),
		categoryName: String(r(e, "categoryName") ?? ""),
		categoryIsExclusive: r(e, "categoryIsExclusive") === !0,
		categoryUpdateUri: String(r(e, "categoryUpdateUri") ?? "")
	});
}
function f(e) {
	e.addEventListener("click", (t) => {
		let n = t.target.closest("[data-delete-uri]");
		n && e.contains(n) && l(n);
	}), e.addEventListener("click", (t) => {
		let n = t.target.closest("[data-update-uri]");
		n && e.contains(n) && u(n);
	}), e.addEventListener("click", (t) => {
		let n = t.target.closest("[data-category-update-uri]");
		n && e.contains(n) && d(n);
	});
}
//#endregion
export { f as attachLabelListAdapter };
