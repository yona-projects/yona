import { I as e, M as t, P as n, S as r, _ as i, d as a, m as o, n as s, w as c } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as l } from "./_plugin-vue_export-helper-B3ysoDQm.js";
//#region src/switch/YonaSwitch.vue?vue&type=script&setup=true&lang.ts
var u = ["aria-checked"], d = { class: "switch-left" }, f = { class: "switch-right" }, p = /*#__PURE__*/ l(/* @__PURE__ */ i({
	__name: "YonaSwitch",
	props: {
		onLabel: {
			default: "ON",
			type: String
		},
		offLabel: {
			default: "OFF",
			type: String
		}
	},
	setup(i) {
		let s = t(null), l = t(!1), p = t(!1);
		function m() {
			return (s.value?.assignedElements?.() ?? []).find((e) => e.tagName === "INPUT" && e.type === "checkbox") ?? null;
		}
		function h() {
			let e = m();
			e && (l.value = e.checked, p.value = e.disabled);
		}
		function g() {
			let e = m();
			e && (e.addEventListener("change", h), h());
		}
		function _() {
			if (p.value) return;
			let e = m();
			e && (e.checked = !e.checked, l.value = e.checked, e.dispatchEvent(new Event("change", { bubbles: !0 })));
		}
		function v(e) {
			e.target.closest(".switch-left, .switch-right, label") && (e.preventDefault(), _());
		}
		function y(e) {
			e.keyCode === 32 && (e.preventDefault(), _());
		}
		return (t, m) => (r(), o("div", {
			class: n(["switch has-switch", { deactivate: p.value }]),
			tabindex: "0",
			role: "checkbox",
			"aria-checked": l.value ? "true" : "false",
			onClick: v,
			onKeydown: y
		}, [a("div", { class: n(["switch-animate", l.value ? "switch-on" : "switch-off"]) }, [
			c(t.$slots, "default", {
				ref_key: "slotRef",
				ref: s,
				onSlotchange: g
			}, void 0, !0),
			a("span", d, e(i.onLabel), 1),
			m[0] ||= a("label", null, "\xA0", -1),
			a("span", f, e(i.offLabel), 1)
		], 2)], 42, u));
	}
}), [["styles", [":host{display:inline-block}.switch.has-switch ::slotted(input[type=checkbox]){display:none}", ".switch.has-switch[data-v-f233afbb]{cursor:pointer;text-align:left;-webkit-user-select:none;user-select:none;-o-user-select:none;border-radius:30px;width:80px;line-height:1.231;display:inline-block;position:relative;overflow:hidden;-webkit-mask:url(/images/switch-mask.png) 0 0 no-repeat;mask:url(/images/switch-mask.png) 0 0 no-repeat}.switch.has-switch.deactivate[data-v-f233afbb]{opacity:.5;cursor:default!important}.switch.has-switch.deactivate label[data-v-f233afbb],.switch.has-switch.deactivate span[data-v-f233afbb]{cursor:default!important}.switch.has-switch>div[data-v-f233afbb]{width:162%;position:relative;top:0}.switch.has-switch>div.switch-animate[data-v-f233afbb]{-o-transition:left .25s ease-out;-webkit-backface-visibility:hidden;transition:left .25s ease-out}.switch.has-switch>div.switch-off[data-v-f233afbb]{left:-63%}.switch.has-switch>div.switch-off label[data-v-f233afbb]{background-color:#fff;border-color:#fd6956;box-shadow:-1px 0 #ffffff80}.switch.has-switch>div.switch-on[data-v-f233afbb]{left:0%}.switch.has-switch>div.switch-on label[data-v-f233afbb]{background-color:#fff}.switch.has-switch span[data-v-f233afbb]{cursor:pointer;float:left;text-align:center;z-index:1;box-sizing:border-box;-o-transition:.25s ease-out;-webkit-backface-visibility:hidden;width:50%;height:29px;margin:0;padding-top:5px;padding-bottom:6px;font-size:13px;font-weight:700;line-height:19px;transition:all .25s ease-out;position:relative}.switch.has-switch span.switch-left[data-v-f233afbb]{color:#fff;background-color:#b6da54;border-left:1px solid #0000;border-radius:30px 0 0 30px}.switch.has-switch span.switch-left[data-v-f233afbb]:hover{background-color:#a3ce2d}.switch.has-switch span.switch-right[data-v-f233afbb]{color:#fff;text-indent:5px;background-color:#fd6956;border-radius:0 30px 30px 0}.switch.has-switch span.switch-right[data-v-f233afbb]:hover{background-color:#fc3c24}.switch.has-switch label[data-v-f233afbb]{float:left;vertical-align:middle;z-index:100;-o-transition:.25s ease-out;-webkit-backface-visibility:hidden;border:4px solid #b6da54;border-radius:50%;width:21px;height:21px;margin:0 -15px 0 -14px;padding:0;transition:all .25s ease-out;position:relative}"]], ["__scopeId", "data-v-f233afbb"]]);
//#endregion
//#region src/switch/element.ts
customElements.define("yona-switch", s(p));
//#endregion
