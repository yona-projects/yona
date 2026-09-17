//#region src/toast/format.ts
function e(e) {
	return e.split("\n").join("<br>");
}
function t(t, n) {
	let r = e(t);
	return n ? `<strong>${n}</strong><br/>${r}` : r;
}
//#endregion
export { t as n, e as t };
