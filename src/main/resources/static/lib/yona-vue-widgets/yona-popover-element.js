import { C as e, F as t, I as n, P as r, S as i, _ as a, c as o, d as s, f as c, j as l, l as u, m as d, n as f, p, v as m } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
//#region src/popover/popover.ts
function h(e, t, n) {
	return t === "body" ? {
		top: e.top + n.y,
		left: e.left + n.x
	} : {
		top: e.top - t.top,
		left: e.left - t.left
	};
}
function g(e, t, n, r) {
	switch (r) {
		case "bottom": return {
			top: e.top + t.height,
			left: e.left + t.width / 2 - n.width / 2
		};
		case "left": return {
			top: e.top + t.height / 2 - n.height / 2,
			left: e.left - n.width
		};
		case "right": return {
			top: e.top + t.height / 2 - n.height / 2,
			left: e.left + t.width
		};
		default: return {
			top: e.top - n.height,
			left: e.left + t.width / 2 - n.width / 2
		};
	}
}
//#endregion
//#region src/popover/YonaPopover.vue?vue&type=script&setup=true&lang.ts
var _ = ["data-yona-popover-id"], v = ["innerHTML"], y = {
	key: 1,
	class: "tooltip-inner"
}, b = ["data-yona-popover-id"], x = {
	key: 0,
	class: "popover-title"
}, S = { class: "popover-content" }, C = /* @__PURE__ */ a({
	__name: "YonaPopover",
	setup(a, { expose: f }) {
		let C = l([]), w = 1, T = /* @__PURE__ */ new WeakMap(), E = /* @__PURE__ */ new WeakMap(), D = /* @__PURE__ */ new WeakMap();
		function O(e) {
			return e.closest("dialog[open]") || document.body;
		}
		function k(e) {
			return document.querySelector(`[data-yona-popover-id="${e}"]`);
		}
		async function A(e, t, n, r, i, a) {
			let o = O(e), s = l({
				id: w++,
				skin: t,
				content: n,
				title: r,
				html: a,
				placement: i,
				container: o,
				top: 0,
				left: 0,
				visible: !1
			});
			C.push(s), await m();
			let c = k(s.id);
			if (c) {
				let t = e.getBoundingClientRect(), n = g(h(t, o === document.body ? "body" : o.getBoundingClientRect(), {
					x: window.pageXOffset,
					y: window.pageYOffset
				}), {
					width: t.width,
					height: t.height
				}, {
					width: c.offsetWidth,
					height: c.offsetHeight
				}, i);
				s.top = n.top, s.left = n.left;
			}
			return s.visible = !0, s;
		}
		function j(e) {
			let t = C.indexOf(e);
			t > -1 && C.splice(t, 1);
		}
		function M(e) {
			e.visible = !1, m(() => {
				let t = k(e.id);
				if (!t) {
					j(e);
					return;
				}
				let n = setTimeout(() => j(e), 500);
				t.addEventListener("transitionend", () => {
					clearTimeout(n), j(e);
				}, { once: !0 });
			});
		}
		function N(e) {
			e.getAttribute("data-original-title") === null && (e.setAttribute("data-original-title", e.getAttribute("title") || ""), e.setAttribute("title", ""));
		}
		function P(e) {
			if (!e || T.has(e)) return;
			N(e);
			let t = e.getAttribute("data-original-title") || "";
			t && A(e, "tooltip", t, "", e.getAttribute("data-placement") || "top", e.getAttribute("data-html") === "true").then((t) => {
				T.set(e, t);
			});
		}
		function F(e) {
			let t = e && T.get(e);
			t && (T.delete(e), M(t));
		}
		function I(e, t, n) {
			L(e), A(e, "popover", t, "", n || "left", !1).then((t) => {
				E.set(e, t);
			});
		}
		function L(e) {
			let t = e && E.get(e);
			t && (E.delete(e), j(t));
		}
		function R(e) {
			document.querySelectorAll(e).forEach((e) => {
				let t = e;
				if (t._yonaHoverPopoverBound) return;
				t._yonaHoverPopoverBound = !0;
				let n, r;
				function i() {
					let e = D.get(t);
					e && (D.delete(t), j(e));
				}
				t.addEventListener("mouseenter", () => {
					clearTimeout(r), n = setTimeout(() => {
						let e = t.getAttribute("data-content");
						if (!e) return;
						let n = t.getAttribute("data-original-title") || "", r = t.getAttribute("data-placement") || "top";
						i(), A(t, "popover", e, n, r, !1).then((e) => {
							D.set(t, e);
						});
					}, 100);
				}), t.addEventListener("mouseleave", () => {
					clearTimeout(n), r = setTimeout(i, 100);
				});
			});
		}
		return f({
			showTooltip: P,
			hideTooltip: F,
			showPopoverError: I,
			hidePopoverError: L,
			initHoverPopovers: R
		}), (a, l) => (i(!0), d(o, null, e(C, (e) => (i(), c(u, {
			key: e.id,
			to: e.container
		}, [e.skin === "tooltip" ? (i(), d("div", {
			key: 0,
			"data-yona-popover-id": e.id,
			class: r([
				"tooltip",
				"fade",
				e.placement,
				{ in: e.visible }
			]),
			role: "tooltip",
			style: t({
				top: e.top + "px",
				left: e.left + "px",
				display: "block"
			})
		}, [l[0] ||= s("div", { class: "tooltip-arrow" }, null, -1), e.html ? (i(), d("div", {
			key: 0,
			class: "tooltip-inner",
			innerHTML: e.content
		}, null, 8, v)) : (i(), d("div", y, n(e.content), 1))], 14, _)) : (i(), d("div", {
			key: 1,
			"data-yona-popover-id": e.id,
			class: r([
				"popover",
				e.placement,
				{ in: e.visible }
			]),
			style: t({
				top: e.top + "px",
				left: e.left + "px",
				display: "block"
			})
		}, [
			l[1] ||= s("div", { class: "arrow" }, null, -1),
			e.title ? (i(), d("h3", x, n(e.title), 1)) : p("", !0),
			s("div", S, n(e.content), 1)
		], 14, b))], 8, ["to"]))), 128));
	}
});
//#endregion
//#region src/popover/element.ts
customElements.define("yona-popover", f(C));
//#endregion
