import { M as e, P as t, S as n, _ as r, b as i, d as a, f as o, h as s, j as c, l, m as u, n as d, p as f, r as p, x as m } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
//#region src/scroll-elevator/YonaScrollElevator.vue?vue&type=script&setup=true&lang.ts
var h = ["title"], g = {
	key: 0,
	class: "jq-title"
}, _ = ["title"], v = {
	key: 0,
	class: "jq-title"
}, y = /* @__PURE__ */ r({
	__name: "YonaScrollElevator",
	setup(r, { expose: d }) {
		let y = p(), b = e("bottom right"), x = e("rounded"), S = e(!1), C = e(!1), w = e(!1), T = 100, E = c({
			topSize: "jq-mid",
			bottomSize: "jq-mid"
		});
		function D() {
			return b.value.split(" ").filter(Boolean).map((e) => `align-${e}`);
		}
		function O() {
			return window.scrollY <= T;
		}
		function k() {
			return window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - T;
		}
		function A() {
			O() ? (E.topSize = "jq-sml", E.bottomSize = "jq-big") : k() ? (E.topSize = "jq-big", E.bottomSize = "jq-sml") : (E.topSize = "jq-mid", E.bottomSize = "jq-mid");
		}
		function j(e) {
			e.preventDefault(), window.scrollTo({
				top: 0,
				behavior: "smooth"
			});
		}
		function M(e) {
			e.preventDefault(), window.scrollTo({
				top: document.documentElement.scrollHeight,
				behavior: "smooth"
			});
		}
		function N() {
			document.removeEventListener("scroll", A);
		}
		return i(() => {
			if (y) {
				b.value = y.getAttribute("data-align") ?? b.value, x.value = y.getAttribute("data-shape") ?? x.value, S.value = y.getAttribute("data-glass") === "true", C.value = y.getAttribute("data-tooltips") === "true";
				let e = y.getAttribute("data-margin");
				e && (T = Number(e));
			}
			w.value = "ontouchstart" in window || !!navigator.msMaxTouchPoints, document.addEventListener("scroll", A), A();
		}), m(N), d({ destroy: N }), (e, r) => (n(), o(l, { to: "body" }, [a("div", { class: t(["jq-elevator", [
			...D(),
			x.value,
			{
				glass: S.value,
				touch: w.value
			}
		]]) }, [a("a", {
			href: "#",
			class: t(["jq-top", E.topSize]),
			title: C.value ? void 0 : "Move to Top",
			onClick: j
		}, [r[0] ||= s(" ▲", -1), C.value ? (n(), u("span", g, "Move to Top")) : f("", !0)], 10, h), a("a", {
			href: "#",
			class: t(["jq-bottom", E.bottomSize]),
			title: C.value ? void 0 : "Move to Bottom",
			onClick: M
		}, [r[1] ||= s(" ▼", -1), C.value ? (n(), u("span", v, "Move to Bottom")) : f("", !0)], 10, _)], 2)]));
	}
});
//#endregion
//#region src/scroll-elevator/element.ts
customElements.define("yona-scroll-elevator", d(y));
//#endregion
