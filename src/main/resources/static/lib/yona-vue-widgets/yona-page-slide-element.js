import { F as e, M as t, S as n, _ as r, m as i, n as a, p as o } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
//#region src/page-slide/YonaPageSlide.vue?vue&type=script&setup=true&lang.ts
var s = ["src"], c = /* @__PURE__ */ r({
	__name: "YonaPageSlide",
	setup(r, { expose: a }) {
		let c = t(!1), l = t("left"), u = t(""), d = t(0), f;
		function p(e, t = "left") {
			f && clearTimeout(f), u.value = "", d.value++, l.value = t, c.value = !0, f = setTimeout(() => {
				u.value = e;
			}, 300);
		}
		function m() {
			f &&= (clearTimeout(f), void 0), c.value = !1;
		}
		function h() {
			return c.value;
		}
		return a({
			show: p,
			hide: m,
			isVisible: h
		}), (t, r) => c.value ? (n(), i("div", {
			key: 0,
			style: e({
				display: "block",
				position: "fixed",
				top: "0",
				height: "100%",
				zIndex: 999999,
				width: "50%",
				padding: "0",
				boxShadow: "2px 2px 8px #000",
				background: "#FFF url('/images/loading-gif-2.gif') no-repeat center",
				left: l.value === "left" ? "auto" : "0px",
				right: l.value === "left" ? "0px" : "auto"
			})
		}, [u.value ? (n(), i("iframe", {
			key: d.value,
			allowtransparency: "true",
			frameborder: "0",
			hspace: "0",
			style: {
				width: "100%",
				height: "100%"
			},
			src: u.value
		}, null, 8, s)) : o("", !0)], 4)) : o("", !0);
	}
});
//#endregion
//#region src/page-slide/element.ts
customElements.define("yona-page-slide", a(c));
//#endregion
