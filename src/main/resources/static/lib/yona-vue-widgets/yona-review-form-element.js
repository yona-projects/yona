import { C as e, D as t, M as n, P as r, S as i, T as a, _ as o, b as s, c, d as l, f as u, j as d, l as f, m as p, n as m, p as h, r as g, v as ee } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
//#region src/review-form/YonaReviewForm.vue?vue&type=script&setup=true&lang.ts
var _ = ["action"], v = ["name", "value"], y = ["name", "value"], b = {
	key: 1,
	class: "author-info-wrap pull-left hide-in-mobile"
}, x = { class: "author-info" }, S = ["href"], C = ["src"], w = { class: "write-comment-box" }, T = { class: "write-comment-wrap" }, E = /* @__PURE__ */ o({
	__name: "YonaReviewForm",
	setup(o, { expose: m }) {
		let E = g(), D = n(!1), O = n("arrow-top"), k = n(null), A = d({}), j = n(0), M = n(""), N = n(void 0), P = n(void 0), F = n(void 0), I = n(void 0), L = n(void 0), R = t("wrapRef"), z = t("editorRef"), B = null;
		function V(e) {
			return !e.dataset.threadId && !e.dataset.line ? H(e) : e;
		}
		function H(e) {
			let t = e.previousElementSibling;
			for (; t && !t.matches("tr[data-line]");) t = t.previousElementSibling;
			return t || e;
		}
		function U(e, t) {
			if (e.dataset.threadId) return e.closest(".comment-thread-wrap");
			let n = document.createElement("tr");
			return n.className = "comment-form", n.innerHTML = "<td colspan=\"3\" class=\"write-comment-form\"></td>", t === "top" ? e.before(n) : e.after(n), n;
		}
		function W(e) {
			let t = {};
			if (!e) return t;
			let n = [
				"bIsReversed",
				"sStartType",
				"sEndType",
				"sPathA",
				"sPathB",
				"sPrevCommitId",
				"sCommitId",
				"sFilePath"
			];
			return Object.keys(e).forEach((r) => {
				if (n.indexOf(r) > -1) return;
				let i = r.substring(1, 2).toLowerCase() + r.substring(2);
				t[i] = String(e[r]);
			}), t;
		}
		function G(e, t) {
			return e.dataset.threadId ? { "thread.id": e.dataset.threadId ?? "" } : W(t);
		}
		function K() {
			return z.value?.querySelector("textarea") ?? null;
		}
		function q(e, t = {}) {
			let n = (t.sPlacement || "bottom").toLowerCase();
			O.value = n === "top" ? "arrow-bottom" : "arrow-top";
			let r = V(e), i = U(r, n);
			if (!i) return;
			let a = i.matches(".write-comment-form") ? i : i.querySelector(".write-comment-form");
			a && (B = i.matches("tr.comment-form") ? i : null, Object.keys(A).forEach((e) => delete A[e]), Object.assign(A, G(r, t.htBlockInfo)), k.value = a, j.value++, D.value = !0, ee(() => {
				K()?.focus();
			}), window.dispatchEvent(new CustomEvent("CodeCommentBox:aftershow")));
		}
		function J() {
			D.value = !1, B &&= (B.remove(), null), window.dispatchEvent(new CustomEvent("CodeCommentBox:afterhide"));
		}
		function Y(e, t) {
			D.value ? J() : q(e, t);
		}
		function X() {
			return D.value;
		}
		function Z() {
			return R.value?.offsetHeight ?? 0;
		}
		function Q() {
			let e = R.value?.getBoundingClientRect();
			return e ? {
				top: e.top + window.scrollY,
				left: e.left + window.scrollX
			} : {
				top: 0,
				left: 0
			};
		}
		function $(e) {
			M.value = e.actionUrl ?? M.value, N.value = e.avatarUrl ?? N.value, P.value = e.profileUrl ?? P.value, F.value = e.resourceType ?? F.value, I.value = e.csrfParam ?? I.value, L.value = e.csrfToken ?? L.value;
		}
		return s(() => {
			E && $({
				actionUrl: E.getAttribute("data-action") ?? void 0,
				avatarUrl: E.getAttribute("data-avatar-url") ?? void 0,
				profileUrl: E.getAttribute("data-profile-url") ?? void 0,
				resourceType: E.getAttribute("data-resource-type") ?? void 0,
				csrfParam: E.getAttribute("data-csrf-param") ?? void 0,
				csrfToken: E.getAttribute("data-csrf-token") ?? void 0
			});
		}), m({
			show: q,
			hide: J,
			toggle: Y,
			isVisible: X,
			height: Z,
			offset: Q,
			configure: $
		}), (t, n) => {
			let o = a("yona-markdown-editor-vue"), s = a("yona-attachments");
			return D.value && k.value ? (i(), u(f, {
				key: 0,
				to: k.value,
				disabled: !k.value
			}, [l("div", {
				id: "review-form",
				ref_key: "wrapRef",
				ref: R,
				class: r(["review-form", O.value]),
				style: { display: "block" }
			}, [l("form", {
				action: M.value,
				method: "post",
				enctype: "multipart/form-data"
			}, [
				I.value ? (i(), p("input", {
					key: 0,
					type: "hidden",
					name: I.value,
					value: L.value
				}, null, 8, v)) : h("", !0),
				(i(!0), p(c, null, e(A, (e, t) => (i(), p("input", {
					key: t,
					type: "hidden",
					name: t,
					value: e
				}, null, 8, y))), 128)),
				N.value ? (i(), p("div", b, [l("div", x, [l("a", {
					href: P.value || "#",
					target: "_blank"
				}, [l("img", {
					src: N.value,
					width: "32",
					height: "32"
				}, null, 8, C)], 8, S)])])) : h("", !0),
				l("div", w, [l("div", T, [
					l("div", { class: "pull-right" }, [l("button", {
						type: "button",
						class: "ybtn ybtn-default ybtn-small",
						"data-toggle": "close",
						onClick: J
					}, "×")]),
					(i(), u(o, {
						key: `editor-${j.value}`,
						ref_key: "editorRef",
						ref: z,
						name: "contents",
						"editor-mode": "code-review-body",
						style: { "--yona-md-min-height": "100px" }
					})),
					(i(), u(s, {
						key: `attachments-${j.value}`,
						"data-resource-type": F.value
					}, null, 8, ["data-resource-type"])),
					l("div", { class: "right-txt" }, [l("button", {
						type: "button",
						class: "ybtn ybtn-small",
						"data-toggle": "close",
						onClick: J
					}, "취소"), n[0] ||= l("button", {
						type: "submit",
						class: "ybtn ybtn-success ybtn-small"
					}, "댓글 등록", -1)])
				])])
			], 8, _)], 2)], 8, ["to", "disabled"])) : h("", !0);
		};
	}
});
//#endregion
//#region src/review-form/element.ts
customElements.define("yona-review-form", m(E));
//#endregion
