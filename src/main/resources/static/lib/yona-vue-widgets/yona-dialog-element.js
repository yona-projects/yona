import { D as e, S as t, _ as n, d as r, m as i, n as a, r as o, w as s } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as c } from "./_plugin-vue_export-helper-B3ysoDQm.js";
import { t as l } from "./format-DftWF65d.js";
//#region src/dialog/YonaDialog.vue?vue&type=script&setup=true&lang.ts
var u = { class: "message" }, d = { class: "center-text" }, f = { class: "center-txt buttons" }, p = /*#__PURE__*/ c(/* @__PURE__ */ n({
	__name: "YonaDialog",
	setup(n, { expose: a }) {
		let c = o(), p = e("dialogRef"), m = e("messageRef"), h = e("descriptionRef"), g = null, _ = null;
		function v() {
			return typeof Messages == "function" ? Messages("button.confirm") : "Confirm";
		}
		function y() {
			return c ? Array.from(c.querySelectorAll("[slot=\"buttons\"]")) : [];
		}
		function b() {
			y().forEach((e) => e.remove());
		}
		function x(e, t) {
			let n = document.createElement("button");
			return n.type = "button", n.className = "ybtn " + t, n.textContent = e, n.setAttribute("slot", "buttons"), n;
		}
		function S(e) {
			if (b(), !c) return;
			let t = e.aButtonLabels;
			if (!t) {
				c.appendChild(x(v(), "ybtn-info"));
				return;
			}
			let n = e.aButtonStyles || [];
			t.forEach((e, r) => {
				let i = n[r] || (n.length === 0 && r === t.length - 1 ? "ybtn-primary" : "ybtn-default");
				c.appendChild(x(e, i));
			});
		}
		function C(e, t) {
			(typeof _ != "function" || _({
				weEvt: t,
				nButtonIndex: y().indexOf(e)
			}) !== !1) && D();
		}
		function w(e) {
			if (e.target === p.value) {
				D();
				return;
			}
			let t = e.target, n = t.closest?.("[slot=\"buttons\"]");
			if (n) {
				C(n, e);
				return;
			}
			t.closest?.("[data-dismiss=\"modal\"]") && D();
		}
		function T() {
			m.value && (m.value.innerHTML = ""), typeof g == "function" && g();
		}
		function E(e, t, n = {}) {
			g = n.fOnAfterHide ?? null, _ = n.fOnClickButton ?? null, S(n), m.value && (m.value.innerHTML = l(e)), h.value && (h.value.innerHTML = l(t || "")), p.value?.showModal(), typeof n.fOnAfterShow == "function" && n.fOnAfterShow();
			{
				let e = y(), t = e.filter((e) => e.classList.contains("ybtn-primary"));
				(t.length ? t[t.length - 1] : e[e.length - 1])?.focus();
			}
		}
		function D() {
			p.value?.close();
		}
		return a({
			show: E,
			hide: D
		}), (e, n) => (t(), i("dialog", {
			ref_key: "dialogRef",
			ref: p,
			class: "modal yonaDialog",
			onClick: w,
			onClose: T
		}, [n[0] ||= r("div", { class: "btn-dismiss" }, [r("button", {
			type: "button",
			class: "btn-transparent",
			"data-dismiss": "modal"
		}, "×")], -1), r("div", u, [r("div", d, [r("p", {
			ref_key: "messageRef",
			ref: m,
			class: "msg"
		}, null, 512), r("p", {
			ref_key: "descriptionRef",
			ref: h,
			class: "desc"
		}, null, 512)]), r("div", f, [s(e.$slots, "buttons", {}, void 0, !0)])])], 544));
	}
}), [["styles", [".modal.yonaDialog[data-v-367d2980]{z-index:1050;background-color:#fff;background-clip:padding-box;border:1px solid #0000004d;border-radius:6px;outline:none;width:500px;margin-left:-250px;padding:16px 20px;position:fixed;top:10%;left:50%;box-shadow:0 3px 7px #0000004d}.modal.yonaDialog[data-v-367d2980]::backdrop{opacity:.8;background-color:#000}.btn-dismiss[data-v-367d2980]{text-align:right;clear:both;width:100%;margin:0;padding:0;display:block}.btn-dismiss button[data-v-367d2980]{color:#898989;cursor:pointer;background:0 0;border:none;font-size:24px;font-weight:700}.message .center-text[data-v-367d2980]{text-align:center}.message .msg[data-v-367d2980]{text-align:center;margin-bottom:20px;font-size:18px;font-weight:700;line-height:1.5em}.message .desc[data-v-367d2980]{text-align:center;color:#555;margin:20px 0 25px;font-size:14px;font-weight:400;line-height:150%}.message .buttons[data-v-367d2980]{text-align:center}", "::slotted([slot=buttons]){margin:0 4px}"]], ["__scopeId", "data-v-367d2980"]]);
//#endregion
//#region src/dialog/element.ts
customElements.define("yona-dialog", a(p));
//#endregion
