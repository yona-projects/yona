import { _ as e, b as t, n, r, w as i, x as a } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
//#endregion
//#region src/dropdown/YonaDropdown.vue
var o = /* @__PURE__ */ e({
	__name: "YonaDropdown",
	setup(e, { expose: n }) {
		let o = r(), s = "", c = null;
		function l() {
			return o?.querySelector(":scope > .dropdown-menu") ?? o?.querySelector(".dropdown-menu") ?? null;
		}
		function u() {
			return o?.querySelector(".d-label") ?? null;
		}
		function d() {
			return Array.from(l()?.querySelectorAll("li") ?? []);
		}
		function f(e) {
			let t = u();
			t && (t.innerHTML = e.innerHTML), d().forEach((e) => e.classList.remove("active")), e.classList.add("active");
		}
		function p(e) {
			let t = e.getAttribute("data-value") ?? "", n = o?.getAttribute("data-name");
			if (s = t, !n || !o) return;
			let r = o.querySelector(`input[name="${CSS.escape(n)}"]`);
			r || (r = document.createElement("input"), r.type = "hidden", r.name = n, o.appendChild(r)), r.value = s;
		}
		function m() {
			if (typeof c == "function") {
				let e = c;
				setTimeout(() => e(s), 0);
			}
		}
		function h(e) {
			let t = e.target.closest("li"), n = l();
			if (!t || !n || !n.contains(t) || t.getAttribute("data-value") === null) {
				e.stopPropagation(), e.preventDefault();
				return;
			}
			f(t), p(t), m();
		}
		function g(e) {
			let t = l();
			if (!t) return;
			let n = t.scrollTop === 0, r = t.scrollTop + t.clientHeight === t.scrollHeight;
			(e.deltaY > 0 && r || e.deltaY < 0 && n) && (e.preventDefault(), e.stopPropagation());
		}
		function _(e) {
			let t = l();
			if (!t) return !1;
			let n = t.querySelector(e);
			return n ? (f(n), p(n), !0) : !1;
		}
		return t(() => {
			let e = l();
			e && (e.addEventListener("click", h), e.addEventListener("mousewheel", g)), _("li[data-selected=true]");
		}), a(() => {
			let e = l();
			e && (e.removeEventListener("click", h), e.removeEventListener("mousewheel", g));
		}), n({
			getValue: () => s,
			onChange: (e) => (c = e, !0),
			selectByValue: (e) => _(`li[data-value='${CSS.escape(e)}']`),
			selectItem: _
		}), (e, t) => i(e.$slots, "default");
	}
});
//#endregion
//#region src/dropdown/element.ts
customElements.define("yona-dropdown", n(o));
//#endregion
