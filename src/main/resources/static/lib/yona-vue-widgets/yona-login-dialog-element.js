import { A as e, D as t, F as n, I as r, M as i, S as a, _ as o, a as s, b as c, c as l, d as u, f as d, h as f, i as p, l as m, m as h, n as g, p as _, r as v } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
//#region src/login-dialog/YonaLoginDialog.vue?vue&type=script&setup=true&lang.ts
var y = { class: "modal-body" }, b = {
	key: 0,
	class: "btns-row nm"
}, x = ["placeholder"], S = ["placeholder"], C = { class: "error-message" }, w = { class: "btns-row nm" }, T = {
	type: "submit",
	class: "ybtn ybtn-primary fullsize"
}, E = { class: "btns-row nm" }, D = {
	key: 0,
	class: "social-login-title-line"
}, O = {
	key: 2,
	class: "act-row right-txt mt20"
}, k = { class: "pull-left" }, A = {
	for: "remember-meD",
	class: "bg-checkbox"
}, j = { href: "/lostPassword" }, M = { href: "/signup" }, N = /* @__PURE__ */ o({
	__name: "YonaLoginDialog",
	setup(o, { expose: g }) {
		let N = v(), P = i("/users/login"), F = i(!1), I = i(""), L = i(""), R = i(!0), z = i(!1), B = i(""), V = t("dialogRef"), H = t("inputIdRef");
		function U(e, t) {
			return (typeof Messages == "function" ? Messages(e) : "") || t;
		}
		function W(e) {
			let t = e?.tagName?.toUpperCase();
			return t === "INPUT" || t === "TEXTAREA";
		}
		function G(e) {
			W(e ?? null) && e.blur(), z.value = !1, L.value = "", I.value = "", V.value?.showModal(), H.value?.focus();
		}
		function K() {
			V.value?.close();
		}
		function q(e) {
			if (e.target === V.value) {
				K();
				return;
			}
			e.target.closest?.("[data-dismiss=\"modal\"]") && K();
		}
		function J(e) {
			/^4[0-9][0-9]$/.test(String(e)) ? Y(U("user.login.failed.client", "Failed to log in. The request is invalid.")) : /^5[0-9][0-9]$/.test(String(e)) ? Y(U("user.login.failed.server", "Failed to log in because a server error has occurred.")) : Y(U("user.login.failed", "Failed to log in."));
		}
		function Y(e) {
			B.value = e, z.value = !0;
			let t = V.value;
			t && (t.classList.remove("yona-shake"), t.offsetWidth, t.classList.add("yona-shake")), H.value?.focus();
		}
		async function X(e) {
			e.preventDefault();
			let t;
			try {
				t = await fetch(P.value, {
					method: "post",
					headers: { "X-Requested-With": "XMLHttpRequest" },
					body: new URLSearchParams({
						loginIdOrEmail: I.value,
						password: L.value,
						rememberMe: String(R.value)
					})
				});
			} catch {
				Y(U("user.login.failed.network", "Failed to log in because of network trouble."));
				return;
			}
			if (t.ok) {
				document.location.reload();
				return;
			}
			let n = await t.text();
			if (n && n.length > 0) try {
				let e = JSON.parse(n);
				Y(U(e.message, e.message));
				return;
			} catch {}
			J(t.status);
		}
		return c(() => {
			N && (P.value = N.getAttribute("data-action") ?? P.value, F.value = N.getAttribute("data-use-social-login-only") === "true");
		}), g({
			show: G,
			hide: K
		}), (t, i) => (a(), d(m, { to: "body" }, [u("dialog", {
			ref_key: "dialogRef",
			ref: V,
			class: "modal loginDialog",
			onClick: q
		}, [u("div", y, [i[7] ||= u("div", { class: "pull-right" }, [u("button", {
			type: "button",
			class: "close mr10",
			"data-dismiss": "modal",
			"aria-hidden": "true"
		}, "×")], -1), u("form", {
			class: "frm-wrap login-form-wrap",
			onSubmit: X
		}, [
			F.value ? (a(), h("div", b, r(U("app.warn.support.social.login.only", "Only allow sign-in via social login")), 1)) : _("", !0),
			F.value ? _("", !0) : (a(), h(l, { key: 1 }, [
				u("dl", null, [u("dd", null, [e(u("input", {
					ref_key: "inputIdRef",
					ref: H,
					"onUpdate:modelValue": i[0] ||= (e) => I.value = e,
					name: "loginIdOrEmail",
					type: "text",
					class: "text email",
					autocomplete: "off",
					placeholder: U("user.login.key", "Login ID or E-mail")
				}, null, 8, x), [[s, I.value]])]), u("dd", null, [e(u("input", {
					"onUpdate:modelValue": i[1] ||= (e) => L.value = e,
					name: "password",
					type: "password",
					class: "text password",
					autocomplete: "off",
					placeholder: U("user.password", "Password")
				}, null, 8, S), [[s, L.value]])])]),
				u("div", {
					class: "error",
					style: n({ display: z.value ? "block" : "none" })
				}, [i[3] ||= u("i", { class: "yobicon-error" }, null, -1), u("span", C, r(B.value), 1)], 4),
				u("div", w, [u("button", T, r(U("button.login", "Log in")), 1)])
			], 64)),
			u("div", E, [
				F.value ? _("", !0) : (a(), h("div", D, " or ")),
				i[4] ||= u("a", {
					href: "/authenticate/github",
					class: "ybtn oauth-login-btn"
				}, [u("span", { class: "auth-provider-logo" }, [u("span", { class: "github" }, [u("svg", {
					"aria-hidden": "true",
					height: "24",
					version: "1.1",
					viewBox: "0 0 16 16",
					width: "19"
				}, [u("path", { d: "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59 0.4 0.07 0.55-0.17 0.55-0.38 0-0.19-0.01-0.82-0.01-1.49-2.01 0.37-2.53-0.49-2.69-0.94-0.09-0.23-0.48-0.94-0.82-1.13-0.28-0.15-0.68-0.52-0.01-0.53 0.63-0.01 1.08 0.58 1.23 0.82 0.72 1.21 1.87 0.87 2.33 0.66 0.07-0.52 0.28-0.87 0.51-1.07-1.78-0.2-3.64-0.89-3.64-3.95 0-0.87 0.31-1.59 0.82-2.15-0.08-0.2-0.36-1.02 0.08-2.12 0 0 0.67-0.21 2.2 0.82 0.64-0.18 1.32-0.27 2-0.27 0.68 0 1.36 0.09 2 0.27 1.53-1.04 2.2-0.82 2.2-0.82 0.44 1.1 0.16 1.92 0.08 2.12 0.51 0.56 0.82 1.27 0.82 2.15 0 3.07-1.87 3.75-3.65 3.95 0.29 0.25 0.54 0.73 0.54 1.48 0 1.07-0.01 1.93-0.01 2.2 0 0.21 0.15 0.46 0.55 0.38C13.71 14.53 16 11.53 16 8 16 3.58 12.42 0 8 0z" })])]), u("span", { class: "provider-name" }, "Sign in with github")])], -1),
				i[5] ||= u("a", {
					href: "/authenticate/google",
					class: "ybtn oauth-login-btn"
				}, [u("span", { class: "auth-provider-logo" }, [u("img", {
					src: "/images/provider-logo/btn_google_light_normal_ios.svg",
					alt: "login with Google"
				}), f(" Sign in with Google ")])], -1)
			]),
			F.value ? _("", !0) : (a(), h("div", O, [
				u("div", k, [e(u("input", {
					id: "remember-meD",
					"onUpdate:modelValue": i[2] ||= (e) => R.value = e,
					type: "checkbox",
					name: "rememberMe",
					class: "checkbox"
				}, null, 512), [[p, R.value]]), u("label", A, r(U("title.rememberMe", "Stay logged in")), 1)]),
				u("a", j, r(U("title.resetPassword", "Reset password")), 1),
				i[6] ||= u("span", { class: "gray-txt ml10 mr10" }, "|", -1),
				u("a", M, r(U("title.signup", "Sign up")), 1)
			]))
		], 32)])], 512)]));
	}
});
//#endregion
//#region src/login-dialog/element.ts
customElements.define("yona-login-dialog", g(N));
//#endregion
