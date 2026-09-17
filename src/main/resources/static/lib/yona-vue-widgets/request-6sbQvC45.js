//#region src/label-editor/messages.ts
var e = {
	"label.new": "Add new label",
	"label.category": "Category",
	"label.category.option": "In this category, you can choose",
	"label.category.option.multiple": "multiple labels",
	"label.category.option.single": "only a single label",
	"label.category.new.confirm": "{0} is a new category.<br>In this category, you can choose",
	"label.category.edit": "Edit category",
	"label.name": "Name",
	"label.customColor": "Label Color",
	"label.add": "Add label",
	"label.edit": "Edit label",
	"label.error.empty": "Category, Color, and Name are required fields.",
	"label.error.color": "Please define the label color using HEX or RGB values.",
	"label.error.duplicated": "Failed to create a new label. The label may already exist.",
	"label.error.duplicated.in.category": "A label with the same name already exists in the category {0}.",
	"label.error.creationFailed": "Failed to create a new label. A server error may have occurred or the request may be invalid.",
	"label.failedTo": "Failed to {0}.",
	"error.failedTo": "Failed to {0}<br>({1} {2})",
	"button.save": "Save",
	"button.cancel": "Cancel",
	"button.confirm": "Confirm",
	"label.confirm.delete": "Once you delete this label, instances of this label attached to issues will also be removed. Do you still want to delete this label?"
};
function t(e, t) {
	return e.replace(/\{(\d+)\}/g, (e, n) => t[Number(n)] ?? "");
}
function n(n, ...r) {
	let i = globalThis.Messages;
	if (typeof i == "function") return i(n, ...r);
	let a = e[n];
	return a ? t(a, r) : n;
}
//#endregion
//#region src/label-editor/request.ts
function r(e) {
	let t = new URLSearchParams();
	return Object.keys(e).forEach((n) => {
		let r = e[n];
		t.append(n, r == null ? "" : String(r));
	}), t;
}
//#endregion
export { n, r as t };
