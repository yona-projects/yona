import { C as e, D as t, F as n, I as r, M as i, P as a, S as o, _ as s, b as c, c as ee, d as l, h as u, m as d, n as f, r as p, u as m, x as h } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as g } from "./_plugin-vue_export-helper-B3ysoDQm.js";
//#region src/attachments/markers.ts
function _(e) {
	return e.map((e) => ({
		submitId: e.id,
		id: e.id,
		name: e.name,
		url: e.href,
		mimeType: e.mime,
		size: Number(e.size) || 0,
		progress: 100
	}));
}
//#endregion
//#region src/attachments/YonaAttachments.vue?vue&type=script&setup=true&lang.ts
var v = { class: "attach-wrap" }, y = {
	key: 0,
	class: "help help-droppable",
	style: { display: "inline" }
}, b = { class: "btn-wrap" }, x = { class: "nbtn medium white fake-file-wrap" }, S = {
	key: 1,
	class: "help help-pastable",
	style: { display: "inline" }
}, C = [
	"id",
	"data-id",
	"data-name",
	"data-href",
	"data-mime",
	"onClick"
], w = { class: "name" }, te = { class: "size" }, ne = { class: "pull-right" }, re = { class: "progress upload-progress" }, T = /*#__PURE__*/ g(/* @__PURE__ */ s({
	__name: "YonaAttachments",
	setup(s, { expose: f }) {
		let g = p(), T = t("fileInputRef"), E = i([]), D = i(!1), O = m(() => E.value.length > 0), k = null, A = "/files", j = "/files", M = [];
		function ie() {
			let e = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
			return e ? { "X-XSRF-TOKEN": decodeURIComponent(e[1] ?? "") } : {};
		}
		function N() {
			let e = /* @__PURE__ */ new Date();
			return "" + e.getSeconds() + e.getMilliseconds() + "-" + e.getFullYear() + "-" + (e.getMonth() + 1) + "-" + e.getDate() + "-" + e.getHours() + "-" + e.getMinutes();
		}
		function P(e) {
			return [
				"video/mp4",
				"video/ogg",
				"video/webm"
			].indexOf((e || "").toString().trim().toLowerCase()) >= 0;
		}
		function F(e) {
			let t = e.mimeType || "", n = `[${e.name}](${e.url}) `;
			if (t.substring(0, 5) === "image") return "!" + n;
			if (P(t)) {
				let r = document.createElement("div"), i = document.createElement("video");
				i.className = "video-js", i.setAttribute("data-setup", "{}"), i.setAttribute("controls", "controls");
				let a = document.createElement("source");
				return a.setAttribute("src", e.url ?? ""), a.setAttribute("type", t), i.appendChild(a), r.appendChild(i), r.appendChild(document.createTextNode(n)), r.innerHTML;
			}
			return n;
		}
		function I(e) {
			return `<!--_${e}_-->`;
		}
		function L(e) {
			let t = e.closest("yona-markdown-editor, yona-markdown-editor-vue");
			t && (t.value = e.value);
		}
		function R(e, t) {
			e.setSelectionRange(t, t);
		}
		function z(e) {
			if (!k) return;
			let t = k.selectionStart ?? k.value.length, n = k.value;
			k.value = n.substring(0, t) + e + n.substring(t), R(k, t + e.length), L(k);
		}
		function B(e) {
			if (!k) return;
			let t = k.value.split(e).join("");
			t = t.split(e.trim()).join(""), k.value = t, L(k);
		}
		function V(e, t) {
			if (!k) return;
			let n = k.selectionStart ?? 0, r = t.length - e.length - 1;
			k.value = k.value.split(e).join(t), r > 0 && R(k, n + r), L(k);
		}
		function H(e) {
			M.indexOf(e) === -1 && M.push(e), G();
		}
		function U(e) {
			let t = M.indexOf(e);
			t !== -1 && M.splice(t, 1), G();
		}
		let W = null;
		function G() {
			g && (W || (W = document.createElement("input"), W.type = "hidden", W.name = "temporaryUploadFiles", g.insertBefore(W, g.firstChild)), W.value = M.join(","));
		}
		function K(e) {
			return E.value.find(e);
		}
		function q(e, t) {
			let n = t ?? N(), r = {
				submitId: n,
				name: e.name === "image.png" ? `${n}.png` : e.name,
				size: e.size,
				progress: 0
			};
			E.value.unshift(r);
			let i = new FormData();
			i.append("filePath", e, r.name);
			let a = new XMLHttpRequest();
			a.open("POST", A);
			let o = ie();
			Object.keys(o).forEach((e) => a.setRequestHeader(e, o[e] ?? "")), a.upload && a.upload.addEventListener("progress", (e) => {
				if (e.lengthComputable) {
					let t = K((e) => e.submitId === n);
					t && (t.progress = Math.ceil(e.loaded / e.total * 100));
				}
			}), a.addEventListener("load", () => {
				if (a.status >= 200 && a.status < 300) {
					let e;
					try {
						e = JSON.parse(a.responseText);
					} catch {
						Y(n, "invalid response");
						return;
					}
					J(n, e);
				} else Y(n, a.statusText || String(a.status));
			}), a.addEventListener("error", () => Y(n, "network error")), a.send(i);
		}
		function J(e, t) {
			let n = K((t) => t.submitId === e);
			n && (H(t.id), n.id = t.id, n.name = t.name, n.url = t.url, n.mimeType = t.mimeType, n.size = t.size, n.progress = 100, V(I(e), F(n)));
		}
		function Y(e, t) {
			let n = E.value.findIndex((t) => t.submitId === e);
			n !== -1 && E.value.splice(n, 1), B(I(e)), console.error("파일 업로드 실패:", t);
		}
		function ae(e) {
			let t = e.target, n = t.files;
			n && Array.from(n).forEach((e) => q(e)), t.value = "";
		}
		function X(e) {
			e.preventDefault(), e.stopPropagation(), D.value = !0;
		}
		function oe(e) {
			e.preventDefault(), D.value = !1;
		}
		function Z(e) {
			e.preventDefault(), e.stopPropagation(), D.value = !1;
			let t = e.dataTransfer?.files;
			t && t.length !== 0 && Array.from(t).forEach((e) => q(e));
		}
		function Q(e) {
			let t = e.clipboardData?.items;
			if (t) for (let n = 0; n < t.length; n++) {
				let r = t[n];
				if (r && r.kind === "file" && r.type.indexOf("image") === 0) {
					let t = N(), n = r.getAsFile();
					if (!n) continue;
					let i = `${t}.png`;
					z(I(t)), q(new File([n], i, { type: n.type }), t), e.preventDefault();
				}
			}
		}
		function se(e, t) {
			e.id && (t.target.closest(".btn-delete") ? ce(e) : z(F(e)));
		}
		function ce(e) {
			e.url && fetch(e.url, {
				method: "post",
				body: new URLSearchParams({ _method: "delete" })
			}).then((t) => {
				if (!t.ok) return Promise.reject(t);
				U(e.id ?? ""), B(F(e));
				let n = E.value.indexOf(e);
				n !== -1 && E.value.splice(n, 1);
			}).catch(() => {
				console.error("첨부파일 삭제 실패");
			});
		}
		function le(e) {
			if (!e || e < 1024) return `${e || 0}B`;
			let t = [
				"KB",
				"MB",
				"GB",
				"TB"
			], n = e / 1024, r = 0;
			for (; n >= 1024 && r < t.length - 1;) n /= 1024, r++;
			return `${n.toFixed(1)}${t[r]}`;
		}
		function ue() {
			return g ? Array.from(g.querySelectorAll(":scope > .attached-file-marker")).map((e) => ({
				id: e.dataset.id ?? "",
				name: e.dataset.name ?? "",
				href: e.dataset.href ?? "",
				mime: e.dataset.mime ?? "",
				size: e.dataset.size ?? ""
			})) : [];
		}
		function de(e, t) {
			let n = new URLSearchParams({
				containerType: e || "",
				containerId: t || ""
			});
			fetch(`${j}?${n}`).then((e) => e.ok ? e.json() : Promise.reject(e)).then((e) => {
				(e.attachments || []).forEach((e) => {
					E.value.push({
						...e,
						submitId: e.id ?? N(),
						progress: 100
					});
				}), t || (e.tempFiles || []).forEach((e) => {
					E.value.push({
						...e,
						submitId: e.id ?? N(),
						progress: 100
					});
				});
			}).catch(() => {});
		}
		function fe(e) {
			e.addEventListener("paste", Q), e.addEventListener("dragover", X), e.addEventListener("drop", Z);
		}
		function $(e) {
			e.removeEventListener("paste", Q), e.removeEventListener("dragover", X), e.removeEventListener("drop", Z);
		}
		function pe(e = {}) {
			k && $(k), k = e.textarea ?? null, k && fe(k), A = e.uploadURL ?? A, j = e.listURL ?? j;
			let t = e.resourceType ?? g?.getAttribute("data-resource-type") ?? void 0, n = e.resourceId ?? g?.getAttribute("data-resource-id") ?? void 0;
			t && de(t, n);
		}
		return c(() => {
			G();
			let e = ue();
			e.length > 0 && (E.value = _(e));
		}), h(() => {
			W?.remove(), k && $(k);
		}), f({ configure: pe }), (t, i) => (o(), d("div", {
			class: "upload-wrap content-footer",
			onDragover: X,
			onDragleave: oe,
			onDrop: Z
		}, [
			l("div", v, [
				(o(), d("span", y, "첨부할 파일을 끌어다 놓거나")),
				l("div", b, [l("div", x, [
					i[0] ||= l("i", { class: "yobicon-upload" }, null, -1),
					i[1] ||= u(),
					i[2] ||= l("span", null, "업로드", -1),
					l("input", {
						ref_key: "fileInputRef",
						ref: T,
						type: "file",
						class: "file",
						name: "filePath",
						multiple: "",
						onChange: ae
					}, null, 544)
				])]),
				i[3] ||= l("span", { class: "plain" }, "버튼을 클릭해서 선택하세요", -1),
				(o(), d("span", S, "클립보드 이미지를 붙여 넣을 수도 있습니다"))
			]),
			l("ul", {
				class: "attached-files unstyled",
				style: n({ display: O.value ? "block" : "none" })
			}, [(o(!0), d(ee, null, e(E.value, (e) => (o(), d("li", {
				key: e.submitId,
				class: a(["attached-file", {
					complete: !!e.id,
					temporary: !e.id
				}]),
				id: e.id ? void 0 : e.submitId,
				"data-id": e.id,
				"data-name": e.name,
				"data-href": e.url,
				"data-mime": e.mimeType,
				onClick: (t) => se(e, t)
			}, [
				i[4] ||= l("i", { class: "yobicon-supportrequest" }, null, -1),
				l("i", {
					class: a(["mimetype", { "yobicon-video2": P(e.mimeType) }]),
					style: n({ display: P(e.mimeType) ? "" : void 0 })
				}, null, 6),
				l("strong", w, r(e.name), 1),
				l("span", te, r(le(e.size)), 1),
				l("div", ne, [l("div", re, [l("div", {
					class: "bar orange",
					style: n({ width: e.progress + "%" })
				}, null, 4)])]),
				i[5] ||= l("button", {
					type: "button",
					class: "btn-transparent btn-delete pull-right"
				}, "×", -1),
				i[6] ||= l("span", { class: "pull-right nbtn small white btn-insert" }, "클릭해서 삽입", -1)
			], 10, C))), 128))], 4),
			l("p", {
				class: "right-txt help",
				style: n({ display: O.value ? "block" : "none" })
			}, [...i[7] ||= [l("i", { class: "yobicon-supportrequest" }, null, -1), u(" 표시된 파일은 글을 저장하면 첨부됩니다. ", -1)]], 4),
			l("div", {
				class: "upload-drop-here",
				style: n({ display: D.value ? "block" : "none" })
			}, [...i[8] ||= [l("div", { class: "msg-wrap" }, [l("div", { class: "msg" }, "파일을 여기에 놓으세요")], -1)]], 4)
		], 32));
	}
}), [["styles", [":host{display:block}", ".upload-wrap[data-v-9563516a]{position:relative;padding:10px!important}.content-footer[data-v-9563516a]{background-color:#f5f5f5;border-radius:5px;padding:10px 20px}.upload-wrap .help[data-v-9563516a]{display:none}.upload-wrap .attach-wrap[data-v-9563516a]{text-align:center}.upload-wrap .attach-wrap .btn-wrap[data-v-9563516a]{vertical-align:top;margin:0 5px;display:inline-block!important}.upload-wrap .attach-wrap .plain[data-v-9563516a]{line-height:30px;display:inline-block}.upload-wrap .attached-files[data-v-9563516a]{border-top:1px solid #e0e0e0;margin-top:15px;margin-bottom:0;padding:15px 0;list-style:none}.attached-file[data-v-9563516a]{cursor:pointer;background:#fafafa;border:1px solid #ccc;height:30px;margin:5px 4px;padding:0 10px;line-height:30px;transition-duration:.5s;display:inline-block;overflow:hidden}.attached-file[data-v-9563516a]:hover{border:1px solid #f36c22}.attached-file i[data-v-9563516a]{vertical-align:middle;color:#3a7ee5;margin-right:3px;display:none}.attached-file .name[data-v-9563516a]{text-overflow:ellipsis;white-space:nowrap;vertical-align:middle;max-width:250px;transition-duration:.5s;display:inline-block;overflow:hidden}.attached-file .size[data-v-9563516a]{vertical-align:middle;font-size:11px}.attached-file .progress[data-v-9563516a]{width:100px;height:7px;margin:0;display:inline-block;overflow:hidden}.attached-file .btn-delete[data-v-9563516a]{cursor:pointer;background:0 0;border:0;width:30px;height:30px;font-size:1.5em;font-weight:700}.attached-file .btn-delete[data-v-9563516a]:hover{color:#f36c22}.attached-file .btn-insert[data-v-9563516a]{box-shadow:none;margin-top:2px;margin-right:10px;line-height:20px;display:none}.attached-file .btn-insert[data-v-9563516a]:hover{color:#f36c22;background:#fff}.attached-file.complete .progress[data-v-9563516a]{display:none}.attached-file.complete .btn-delete[data-v-9563516a]{display:inline-block}.attached-file.complete .btn-insert[data-v-9563516a]{display:block}.attached-file.temporary i[data-v-9563516a]{display:inline-block}.upload-drop-here[data-v-9563516a]{z-index:9999;pointer-events:none;background:#fffc;border:3px dashed #ffb23d;position:absolute;inset:2px}.upload-drop-here .msg-wrap[data-v-9563516a]{width:100%;height:100%;position:relative}.upload-drop-here .msg[data-v-9563516a]{text-align:center;position:absolute;top:50%;left:0;right:0;transform:translateY(-50%)}.nbtn[data-v-9563516a]{text-align:center;white-space:nowrap;color:#fff;cursor:pointer;background-color:#707070;border:0;border-radius:2px;margin-right:5px;padding:0;font-size:11px;font-weight:700;line-height:18px;display:inline-block;box-shadow:inset 0 -1px 1px #0000004d}.nbtn.white[data-v-9563516a]{color:#222;background-color:#fff}.nbtn.white[data-v-9563516a]:hover{color:#f36c22;background-color:#e6e6e6}div.nbtn.medium[data-v-9563516a]{padding:6px 20px}.nbtn.small[data-v-9563516a]{padding:3px 10px;font-size:10px}.fake-file-wrap[data-v-9563516a]{clear:both;cursor:pointer;display:block;position:relative;overflow:hidden}.fake-file-wrap[data-v-9563516a]:hover{background:#e6e6e6}.fake-file-wrap .file[data-v-9563516a]{z-index:2;cursor:pointer;opacity:0;width:100%;min-width:100px;position:absolute;top:0;left:5px}"]], ["__scopeId", "data-v-9563516a"]]);
//#endregion
//#region src/attachments/element.ts
customElements.define("yona-attachments", f(T));
//#endregion
