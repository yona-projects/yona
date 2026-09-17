import { C as e, M as t, S as n, _ as r, c as i, d as a, g as o, k as s, m as c, n as l, t as u } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as d } from "./_plugin-vue_export-helper-B3ysoDQm.js";
import { n as f } from "./format-DftWF65d.js";
//#region src/toast/Toast.vue?vue&type=script&setup=true&lang.ts
var p = { class: "yona-toasts" }, m = ["onClick"], h = { class: "center-text" }, g = ["innerHTML"], _ = /*#__PURE__*/ d(/* @__PURE__ */ r({
	__name: "Toast",
	setup(r, { expose: l }) {
		let d = t([]), _ = 0;
		function v(e, t, n) {
			let r = _++;
			d.value.unshift({
				id: r,
				html: f(e, n)
			}), t && t > 0 && setTimeout(() => y(r), t);
		}
		function y(e) {
			d.value = d.value.filter((t) => t.id !== e);
		}
		function b() {
			d.value = [];
		}
		return l({
			push: v,
			clear: b
		}), (t, r) => (n(), c("div", p, [o(u, {
			name: "yona-toast",
			tag: "div"
		}, {
			default: s(() => [(n(!0), c(i, null, e(d.value, (e) => (n(), c("div", {
				key: e.id,
				class: "toast",
				tabindex: "-1",
				onClick: (t) => y(e.id)
			}, [r[1] ||= a("div", { class: "btn-dismiss" }, [a("button", {
				type: "button",
				class: "btn-transparent"
			}, "×")], -1), a("div", h, [r[0] ||= a("span", { class: "v" }, null, -1), a("div", {
				class: "msg",
				innerHTML: e.html
			}, null, 8, g)])], 8, m))), 128))]),
			_: 1
		})]));
	}
}), [["styles", [".yona-toasts[data-v-f29f56fa]{z-index:9999;margin:10px;position:fixed;overflow:hidden;bottom:25px!important;right:20px!important}.toast[data-v-f29f56fa]{word-break:keep-all;word-wrap:break-word;box-sizing:border-box;color:#000;cursor:pointer;background-color:#cddc39;border-radius:2px;outline:none;width:450px;margin:10px;padding:10px 20px;font-weight:700;position:relative;box-shadow:1px 1px 3px #000}.btn-dismiss[data-v-f29f56fa]{position:absolute;top:5px;left:420px}.btn-dismiss button[data-v-f29f56fa]{color:#000;cursor:pointer;background:0 0;border:0;outline:none;font-size:25px;font-weight:700}.v[data-v-f29f56fa]{vertical-align:middle;width:0;height:50px;display:inline-block}.msg[data-v-f29f56fa]{word-wrap:break-word;word-break:break-all;vertical-align:middle;width:90%;margin:0;font-size:15px;display:inline-block}.yona-toast-enter-active[data-v-f29f56fa],.yona-toast-leave-active[data-v-f29f56fa]{transition:opacity .3s}.yona-toast-enter-from[data-v-f29f56fa],.yona-toast-leave-to[data-v-f29f56fa]{opacity:0}"]], ["__scopeId", "data-v-f29f56fa"]]);
//#endregion
//#region src/toast/element.ts
customElements.define("yona-toast", l(_));
//#endregion
