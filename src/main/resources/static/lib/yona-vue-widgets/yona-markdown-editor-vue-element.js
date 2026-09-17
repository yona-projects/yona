import { A as e, C as t, E as n, I as r, M as i, N as a, O as o, P as s, S as c, _ as l, b as u, c as d, d as f, m as p, n as m, o as h, y as g } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as _ } from "./_plugin-vue_export-helper-B3ysoDQm.js";
//#region node_modules/@marijn/find-cluster-break/src/index.js
var v = [], y = [];
(() => {
	let e = "lc,34,7n,7,7b,19,,,,2,,2,,,20,b,1c,l,g,,2t,7,2,6,2,2,,4,z,,u,r,2j,b,1m,9,9,,o,4,,9,,3,,5,17,3,1n,9,16,o,,x,1i,3,,i,,7,a,2,t,3,1k,,,7,2,2,2,3,9,,a,2,q,,2,3,1k,,,5,4,2,2,3,3,,u,2,3,,b,3,1k,,,8,,3,,3,k,2,m,6,,3,1k,,,7,2,2,2,3,7,3,a,2,u,,1n,5,3,3,,4,9,,14,5,1j,,,7,,3,,4,7,2,b,2,t,3,1k,,,7,,3,,4,7,2,b,2,f,,c,4,1j,2,,7,,3,,4,9,,a,2,t,3,1y,,4,6,,,,8,i,2,1p,,,8,c,8,2q,,,a,b,7,21,2,r,,,,,,4,2,1d,k,,2,5,b,,10,9,,2u,b,,6,n,4,4,3,g,4,d,,,3,6,,f,,jj,3,qa,4,s,3,t,2,u,2,1s,w,9,,19,3,,,39,2,y,,3a,c,4,c,63,5,1l,a,,,,,2,o,2,,1c,1a,2,c,k,5,1b,h,12,9,c,3,u,d,1k,e,1c,k,48,3,,l,4,,6,,2,3,5i,1s,ek,,5f,x,2da,3,3x,,2o,w,fe,6,2x,2,n9w,4,,a,w,2,28,2,7k,,3,,4,,n,5,4,,2b,2,1e,i,q,i,d,,12,8,p,d,18,4,1b,e,10,,1v,e,c,,8,2,1a,,1f,,,3,2,2,5,2,,,15,5,5,2,6k,8,,2,fn4,,kh,g,g,g,a6,2,gt,,6a,,45,5,1ae,3,,2,5,4,14,3,4,,4l,2,fx,4,1t,5,8t,2,25,6,1y,b,1d,4,3e,3,1h,f,15,,2,2,a,4,19,b,7,,1p,3,10,e,g,2,18,,c,3,1c,e,8,4,,2,2k,c,6,,2,,4d,c,l,4,1j,2,,7,2,2,2,3,9,,a,2,2,7,3,5,1v,9,,,2,,,4,,5,,,e,2,2a,i,n,,29,k,6j,7,2,9,r,2,2a,h,2y,d,2t,3,2,a,74,f,6t,6,,2,2,4,,,,2,3x,7,2,7,3,,s,a,14,7,,4,8,,9,b,1a,g,5i,8,5j,8,,8,2a,m,,e,3e,6,3,,,2,,7,,,1u,5,,2,,5,9n,4,9,2,,,1c,7,3,5,n,,44l,,6,f,8ug,i,1xc,5,1n,7,t4,,,1j,7,4,29,,b,2,f57,2,3mp,1a,2,n,f2,5,3,6,8,8,2,7,u,4,44,3,1iz,1j,4,1e,8,,e,,m,5,,f,11s,7,,h,2,7,,2,,5,2s,,4g,7,af,,1p,4,e4,4,72,2,6r,,2,,7,2,5,,d6,7,31,7,240,5".split(",").map((e) => e ? parseInt(e, 36) : 1);
	for (let t = 0, n = 0; t < e.length; t++) (t % 2 ? y : v).push(n += e[t]);
})();
function ee(e) {
	if (e < 768) return !1;
	for (let t = 0, n = v.length;;) {
		let r = t + n >> 1;
		if (e < v[r]) n = r;
		else if (e >= y[r]) t = r + 1;
		else return !0;
		if (t == n) return !1;
	}
}
function b(e) {
	return e >= 127462 && e <= 127487;
}
var x = 8205;
function te(e, t, n = !0, r = !0) {
	return (n ? ne : re)(e, t, r);
}
function ne(e, t, n) {
	if (t == e.length) return t;
	t && C(e.charCodeAt(t)) && ie(e.charCodeAt(t - 1)) && t--;
	let r = S(e, t);
	for (t += w(r); t < e.length;) {
		let i = S(e, t);
		if (r == x || i == x || n && ee(i)) t += w(i), r = i;
		else if (b(i)) {
			let n = 0, r = t - 2;
			for (; r >= 0 && b(S(e, r));) n++, r -= 2;
			if (n % 2 == 0) break;
			t += 2;
		} else break;
	}
	return t;
}
function re(e, t, n) {
	for (; t > 1;) {
		let r = ne(e, t - 2, n);
		if (r < t) return r;
		t--;
	}
	return 0;
}
function S(e, t) {
	let n = e.charCodeAt(t);
	if (!ie(n) || t + 1 == e.length) return n;
	let r = e.charCodeAt(t + 1);
	return C(r) ? (n - 55296 << 10) + (r - 56320) + 65536 : n;
}
function C(e) {
	return e >= 56320 && e < 57344;
}
function ie(e) {
	return e >= 55296 && e < 56320;
}
function w(e) {
	return e < 65536 ? 1 : 2;
}
//#endregion
//#region node_modules/@codemirror/state/dist/index.js
var T = class e {
	lineAt(e) {
		if (e < 0 || e > this.length) throw RangeError(`Invalid position ${e} in document of length ${this.length}`);
		return this.lineInner(e, !1, 1, 0);
	}
	line(e) {
		if (e < 1 || e > this.lines) throw RangeError(`Invalid line number ${e} in ${this.lines}-line document`);
		return this.lineInner(e, !0, 1, 0);
	}
	replace(e, t, n) {
		[e, t] = pe(this, e, t);
		let r = [];
		return this.decompose(0, e, r, 2), n.length && n.decompose(0, n.length, r, 3), this.decompose(t, this.length, r, 1), ae.from(r, this.length - (t - e) + n.length);
	}
	append(e) {
		return this.replace(this.length, this.length, e);
	}
	slice(e, t = this.length) {
		[e, t] = pe(this, e, t);
		let n = [];
		return this.decompose(e, t, n, 0), ae.from(n, t - e);
	}
	eq(e) {
		if (e == this) return !0;
		if (e.length != this.length || e.lines != this.lines) return !1;
		let t = this.scanIdentical(e, 1), n = this.length - this.scanIdentical(e, -1), r = new le(this), i = new le(e);
		for (let e = t, a = t;;) {
			if (r.next(e), i.next(e), e = 0, r.lineBreak != i.lineBreak || r.done != i.done || r.value != i.value) return !1;
			if (a += r.value.length, r.done || a >= n) return !0;
		}
	}
	iter(e = 1) {
		return new le(this, e);
	}
	iterRange(e, t = this.length) {
		return new ue(this, e, t);
	}
	iterLines(e, t) {
		let n;
		if (e == null) n = this.iter();
		else {
			t ??= this.lines + 1;
			let r = this.line(e).from;
			n = this.iterRange(r, Math.max(r, t == this.lines + 1 ? this.length : t <= 1 ? 0 : this.line(t - 1).to));
		}
		return new de(n);
	}
	toString() {
		return this.sliceString(0);
	}
	toJSON() {
		let e = [];
		return this.flatten(e), e;
	}
	constructor() {}
	static of(t) {
		if (t.length == 0) throw RangeError("A document must have at least one line");
		return t.length == 1 && !t[0] ? e.empty : t.length <= 32 ? new E(t) : ae.from(E.split(t, []));
	}
}, E = class e extends T {
	constructor(e, t = oe(e)) {
		super(), this.text = e, this.length = t;
	}
	get lines() {
		return this.text.length;
	}
	get children() {
		return null;
	}
	lineInner(e, t, n, r) {
		for (let i = 0;; i++) {
			let a = this.text[i], o = r + a.length;
			if ((t ? n : o) >= e) return new fe(r, o, n, a);
			r = o + 1, n++;
		}
	}
	decompose(t, n, r, i) {
		let a = t <= 0 && n >= this.length ? this : new e(ce(this.text, t, n), Math.min(n, this.length) - Math.max(0, t));
		if (i & 1) {
			let t = r.pop(), n = se(a.text, t.text.slice(), 0, a.length);
			if (n.length <= 32) r.push(new e(n, t.length + a.length));
			else {
				let t = n.length >> 1;
				r.push(new e(n.slice(0, t)), new e(n.slice(t)));
			}
		} else r.push(a);
	}
	replace(t, n, r) {
		if (!(r instanceof e)) return super.replace(t, n, r);
		[t, n] = pe(this, t, n);
		let i = se(this.text, se(r.text, ce(this.text, 0, t)), n), a = this.length + r.length - (n - t);
		return i.length <= 32 ? new e(i, a) : ae.from(e.split(i, []), a);
	}
	sliceString(e, t = this.length, n = "\n") {
		[e, t] = pe(this, e, t);
		let r = "";
		for (let i = 0, a = 0; i <= t && a < this.text.length; a++) {
			let o = this.text[a], s = i + o.length;
			i > e && a && (r += n), e < s && t > i && (r += o.slice(Math.max(0, e - i), t - i)), i = s + 1;
		}
		return r;
	}
	flatten(e) {
		for (let t of this.text) e.push(t);
	}
	scanIdentical() {
		return 0;
	}
	static split(t, n) {
		let r = [], i = -1;
		for (let a of t) r.push(a), i += a.length + 1, r.length == 32 && (n.push(new e(r, i)), r = [], i = -1);
		return i > -1 && n.push(new e(r, i)), n;
	}
}, ae = class e extends T {
	constructor(e, t) {
		super(), this.children = e, this.length = t, this.lines = 0;
		for (let t of e) this.lines += t.lines;
	}
	lineInner(e, t, n, r) {
		for (let i = 0;; i++) {
			let a = this.children[i], o = r + a.length, s = n + a.lines - 1;
			if ((t ? s : o) >= e) return a.lineInner(e, t, n, r);
			r = o + 1, n = s + 1;
		}
	}
	decompose(e, t, n, r) {
		for (let i = 0, a = 0; a <= t && i < this.children.length; i++) {
			let o = this.children[i], s = a + o.length;
			if (e <= s && t >= a) {
				let i = r & (a <= e | (s >= t ? 2 : 0));
				a >= e && s <= t && !i ? n.push(o) : o.decompose(e - a, t - a, n, i);
			}
			a = s + 1;
		}
	}
	replace(t, n, r) {
		if ([t, n] = pe(this, t, n), r.lines < this.lines) for (let i = 0, a = 0; i < this.children.length; i++) {
			let o = this.children[i], s = a + o.length;
			if (t >= a && n <= s) {
				let c = o.replace(t - a, n - a, r), l = this.lines - o.lines + c.lines;
				if (c.lines < l >> 4 && c.lines > l >> 6) {
					let a = this.children.slice();
					return a[i] = c, new e(a, this.length - (n - t) + r.length);
				}
				return super.replace(a, s, c);
			}
			a = s + 1;
		}
		return super.replace(t, n, r);
	}
	sliceString(e, t = this.length, n = "\n") {
		[e, t] = pe(this, e, t);
		let r = "";
		for (let i = 0, a = 0; i < this.children.length && a <= t; i++) {
			let o = this.children[i], s = a + o.length;
			a > e && i && (r += n), e < s && t > a && (r += o.sliceString(e - a, t - a, n)), a = s + 1;
		}
		return r;
	}
	flatten(e) {
		for (let t of this.children) t.flatten(e);
	}
	scanIdentical(t, n) {
		if (!(t instanceof e)) return 0;
		let r = 0, [i, a, o, s] = n > 0 ? [
			0,
			0,
			this.children.length,
			t.children.length
		] : [
			this.children.length - 1,
			t.children.length - 1,
			-1,
			-1
		];
		for (;; i += n, a += n) {
			if (i == o || a == s) return r;
			let e = this.children[i], c = t.children[a];
			if (e != c) return r + e.scanIdentical(c, n);
			r += e.length + 1;
		}
	}
	static from(t, n = t.reduce((e, t) => e + t.length + 1, -1)) {
		let r = 0;
		for (let e of t) r += e.lines;
		if (r < 32) {
			let e = [];
			for (let n of t) n.flatten(e);
			return new E(e, n);
		}
		let i = Math.max(32, r >> 5), a = i << 1, o = i >> 1, s = [], c = 0, l = -1, u = [];
		function d(t) {
			let n;
			if (t.lines > a && t instanceof e) for (let e of t.children) d(e);
			else t.lines > o && (c > o || !c) ? (f(), s.push(t)) : t instanceof E && c && (n = u[u.length - 1]) instanceof E && t.lines + n.lines <= 32 ? (c += t.lines, l += t.length + 1, u[u.length - 1] = new E(n.text.concat(t.text), n.length + 1 + t.length)) : (c + t.lines > i && f(), c += t.lines, l += t.length + 1, u.push(t));
		}
		function f() {
			c != 0 && (s.push(u.length == 1 ? u[0] : e.from(u, l)), l = -1, c = u.length = 0);
		}
		for (let e of t) d(e);
		return f(), s.length == 1 ? s[0] : new e(s, n);
	}
};
T.empty = /*@__PURE__*/ new E([""], 0);
function oe(e) {
	let t = -1;
	for (let n of e) t += n.length + 1;
	return t;
}
function se(e, t, n = 0, r = 1e9) {
	for (let i = 0, a = 0, o = !0; a < e.length && i <= r; a++) {
		let s = e[a], c = i + s.length;
		c >= n && (c > r && (s = s.slice(0, r - i)), i < n && (s = s.slice(n - i)), o ? (t[t.length - 1] += s, o = !1) : t.push(s)), i = c + 1;
	}
	return t;
}
function ce(e, t, n) {
	return se(e, [""], t, n);
}
var le = class {
	constructor(e, t = 1) {
		this.dir = t, this.done = !1, this.lineBreak = !1, this.value = "", this.nodes = [e], this.offsets = [t > 0 ? 1 : (e instanceof E ? e.text.length : e.children.length) << 1];
	}
	nextInner(e, t) {
		for (this.done = this.lineBreak = !1;;) {
			let n = this.nodes.length - 1, r = this.nodes[n], i = this.offsets[n], a = i >> 1, o = r instanceof E ? r.text.length : r.children.length;
			if (a == (t > 0 ? o : 0)) {
				if (n == 0) return this.done = !0, this.value = "", this;
				t > 0 && this.offsets[n - 1]++, this.nodes.pop(), this.offsets.pop();
			} else if ((i & 1) == (t > 0 ? 0 : 1)) {
				if (this.offsets[n] += t, e == 0) return this.lineBreak = !0, this.value = "\n", this;
				e--;
			} else if (r instanceof E) {
				let i = r.text[a + (t < 0 ? -1 : 0)];
				if (this.offsets[n] += t, i.length > Math.max(0, e)) return this.value = e == 0 ? i : t > 0 ? i.slice(e) : i.slice(0, i.length - e), this;
				e -= i.length;
			} else {
				let i = r.children[a + (t < 0 ? -1 : 0)];
				e > i.length ? (e -= i.length, this.offsets[n] += t) : (t < 0 && this.offsets[n]--, this.nodes.push(i), this.offsets.push(t > 0 ? 1 : (i instanceof E ? i.text.length : i.children.length) << 1));
			}
		}
	}
	next(e = 0) {
		return e < 0 && (this.nextInner(-e, -this.dir), e = this.value.length), this.nextInner(e, this.dir);
	}
}, ue = class {
	constructor(e, t, n) {
		this.value = "", this.done = !1, this.cursor = new le(e, t > n ? -1 : 1), this.pos = t > n ? e.length : 0, this.from = Math.min(t, n), this.to = Math.max(t, n);
	}
	nextInner(e, t) {
		if (t < 0 ? this.pos <= this.from : this.pos >= this.to) return this.value = "", this.done = !0, this;
		e += Math.max(0, t < 0 ? this.pos - this.to : this.from - this.pos);
		let n = t < 0 ? this.pos - this.from : this.to - this.pos;
		e > n && (e = n), n -= e;
		let { value: r } = this.cursor.next(e);
		return this.pos += (r.length + e) * t, this.value = r.length <= n ? r : t < 0 ? r.slice(r.length - n) : r.slice(0, n), this.done = !this.value, this;
	}
	next(e = 0) {
		return e < 0 ? e = Math.max(e, this.from - this.pos) : e > 0 && (e = Math.min(e, this.to - this.pos)), this.nextInner(e, this.cursor.dir);
	}
	get lineBreak() {
		return this.cursor.lineBreak && this.value != "";
	}
}, de = class {
	constructor(e) {
		this.inner = e, this.afterBreak = !0, this.value = "", this.done = !1;
	}
	next(e = 0) {
		let { done: t, lineBreak: n, value: r } = this.inner.next(e);
		return t && this.afterBreak ? (this.value = "", this.afterBreak = !1) : t ? (this.done = !0, this.value = "") : n ? this.afterBreak ? this.value = "" : (this.afterBreak = !0, this.next()) : (this.value = r, this.afterBreak = !1), this;
	}
	get lineBreak() {
		return !1;
	}
};
typeof Symbol < "u" && (T.prototype[Symbol.iterator] = function() {
	return this.iter();
}, le.prototype[Symbol.iterator] = ue.prototype[Symbol.iterator] = de.prototype[Symbol.iterator] = function() {
	return this;
});
var fe = class {
	constructor(e, t, n, r) {
		this.from = e, this.to = t, this.number = n, this.text = r;
	}
	get length() {
		return this.to - this.from;
	}
};
function pe(e, t, n) {
	return t = Math.max(0, Math.min(e.length, t)), [t, Math.max(t, Math.min(e.length, n))];
}
function D(e, t, n = !0, r = !0) {
	return te(e, t, n, r);
}
function me(e) {
	return e >= 56320 && e < 57344;
}
function he(e) {
	return e >= 55296 && e < 56320;
}
function ge(e, t) {
	let n = e.charCodeAt(t);
	if (!he(n) || t + 1 == e.length) return n;
	let r = e.charCodeAt(t + 1);
	return me(r) ? (n - 55296 << 10) + (r - 56320) + 65536 : n;
}
function _e(e) {
	return e <= 65535 ? String.fromCharCode(e) : (e -= 65536, String.fromCharCode((e >> 10) + 55296, (e & 1023) + 56320));
}
function ve(e) {
	return e < 65536 ? 1 : 2;
}
var ye = /\r\n?|\n/, be = /*@__PURE__*/ (function(e) {
	return e[e.Simple = 0] = "Simple", e[e.TrackDel = 1] = "TrackDel", e[e.TrackBefore = 2] = "TrackBefore", e[e.TrackAfter = 3] = "TrackAfter", e;
})(be ||= {}), xe = class e {
	constructor(e) {
		this.sections = e;
	}
	get length() {
		let e = 0;
		for (let t = 0; t < this.sections.length; t += 2) e += this.sections[t];
		return e;
	}
	get newLength() {
		let e = 0;
		for (let t = 0; t < this.sections.length; t += 2) {
			let n = this.sections[t + 1];
			e += n < 0 ? this.sections[t] : n;
		}
		return e;
	}
	get empty() {
		return this.sections.length == 0 || this.sections.length == 2 && this.sections[1] < 0;
	}
	iterGaps(e) {
		for (let t = 0, n = 0, r = 0; t < this.sections.length;) {
			let i = this.sections[t++], a = this.sections[t++];
			a < 0 ? (e(n, r, i), r += i) : r += a, n += i;
		}
	}
	iterChangedRanges(e, t = !1) {
		we(this, e, t);
	}
	get invertedDesc() {
		let t = [];
		for (let e = 0; e < this.sections.length;) {
			let n = this.sections[e++], r = this.sections[e++];
			r < 0 ? t.push(n, r) : t.push(r, n);
		}
		return new e(t);
	}
	composeDesc(e) {
		return this.empty ? e : e.empty ? this : Ee(this, e);
	}
	mapDesc(e, t = !1) {
		return e.empty ? this : Te(this, e, t);
	}
	mapPos(e, t = -1, n = be.Simple) {
		let r = 0, i = 0;
		for (let a = 0; a < this.sections.length;) {
			let o = this.sections[a++], s = this.sections[a++], c = r + o;
			if (s < 0) {
				if (c > e) return i + (e - r);
				i += o;
			} else {
				if (n != be.Simple && c >= e && (n == be.TrackDel && r < e && c > e || n == be.TrackBefore && r < e || n == be.TrackAfter && c > e)) return null;
				if (c > e || c == e && t < 0 && !o) return e == r || t < 0 ? i : i + s;
				i += s;
			}
			r = c;
		}
		if (e > r) throw RangeError(`Position ${e} is out of range for changeset of length ${r}`);
		return i;
	}
	touchesRange(e, t = e) {
		for (let n = 0, r = 0; n < this.sections.length && r <= t;) {
			let i = this.sections[n++], a = this.sections[n++], o = r + i;
			if (a >= 0 && r <= t && o >= e) return r < e && o > t ? "cover" : !0;
			r = o;
		}
		return !1;
	}
	toString() {
		let e = "";
		for (let t = 0; t < this.sections.length;) {
			let n = this.sections[t++], r = this.sections[t++];
			e += (e ? " " : "") + n + (r >= 0 ? ":" + r : "");
		}
		return e;
	}
	toJSON() {
		return this.sections;
	}
	static fromJSON(t) {
		if (!Array.isArray(t) || t.length % 2 || t.some((e) => typeof e != "number")) throw RangeError("Invalid JSON representation of ChangeDesc");
		return new e(t);
	}
	static create(t) {
		return new e(t);
	}
}, Se = class e extends xe {
	constructor(e, t) {
		super(e), this.inserted = t;
	}
	apply(e) {
		if (this.length != e.length) throw RangeError("Applying change set to a document with the wrong length");
		return we(this, (t, n, r, i, a) => e = e.replace(r, r + (n - t), a), !1), e;
	}
	mapDesc(e, t = !1) {
		return Te(this, e, t, !0);
	}
	invert(t) {
		let n = this.sections.slice(), r = [];
		for (let e = 0, i = 0; e < n.length; e += 2) {
			let a = n[e], o = n[e + 1];
			if (o >= 0) {
				n[e] = o, n[e + 1] = a;
				let s = e >> 1;
				for (; r.length < s;) r.push(T.empty);
				r.push(a ? t.slice(i, i + a) : T.empty);
			}
			i += a;
		}
		return new e(n, r);
	}
	compose(e) {
		return this.empty ? e : e.empty ? this : Ee(this, e, !0);
	}
	map(e, t = !1) {
		return e.empty ? this : Te(this, e, t, !0);
	}
	iterChanges(e, t = !1) {
		we(this, e, t);
	}
	get desc() {
		return xe.create(this.sections);
	}
	filter(t) {
		let n = [], r = [], i = [], a = new De(this);
		done: for (let e = 0, o = 0;;) {
			let s = e == t.length ? 1e9 : t[e++];
			for (; o < s || o == s && a.len == 0;) {
				if (a.done) break done;
				let e = Math.min(a.len, s - o);
				O(i, e, -1);
				let t = a.ins == -1 ? -1 : a.off == 0 ? a.ins : 0;
				O(n, e, t), t > 0 && Ce(r, n, a.text), a.forward(e), o += e;
			}
			let c = t[e++];
			for (; o < c;) {
				if (a.done) break done;
				let e = Math.min(a.len, c - o);
				O(n, e, -1), O(i, e, a.ins == -1 ? -1 : a.off == 0 ? a.ins : 0), a.forward(e), o += e;
			}
		}
		return {
			changes: new e(n, r),
			filtered: xe.create(i)
		};
	}
	toJSON() {
		let e = [];
		for (let t = 0; t < this.sections.length; t += 2) {
			let n = this.sections[t], r = this.sections[t + 1];
			r < 0 ? e.push(n) : r == 0 ? e.push([n]) : e.push([n].concat(this.inserted[t >> 1].toJSON()));
		}
		return e;
	}
	static of(t, n, r) {
		let i = [], a = [], o = 0, s = null;
		function c(t = !1) {
			if (!t && !i.length) return;
			o < n && O(i, n - o, -1);
			let r = new e(i, a);
			s = s ? s.compose(r.map(s)) : r, i = [], a = [], o = 0;
		}
		function l(t) {
			if (Array.isArray(t)) for (let e of t) l(e);
			else if (t instanceof e) {
				if (t.length != n) throw RangeError(`Mismatched change set length (got ${t.length}, expected ${n})`);
				c(), s = s ? s.compose(t.map(s)) : t;
			} else {
				let { from: e, to: s = e, insert: l } = t;
				if (e > s || e < 0 || s > n) throw RangeError(`Invalid change range ${e} to ${s} (in doc of length ${n})`);
				let u = l ? typeof l == "string" ? T.of(l.split(r || ye)) : l : T.empty, d = u.length;
				if (e == s && d == 0) return;
				e < o && c(), e > o && O(i, e - o, -1), O(i, s - e, d), Ce(a, i, u), o = s;
			}
		}
		return l(t), c(!s), s;
	}
	static empty(t) {
		return new e(t ? [t, -1] : [], []);
	}
	static fromJSON(t) {
		if (!Array.isArray(t)) throw RangeError("Invalid JSON representation of ChangeSet");
		let n = [], r = [];
		for (let e = 0; e < t.length; e++) {
			let i = t[e];
			if (typeof i == "number") n.push(i, -1);
			else if (!Array.isArray(i) || typeof i[0] != "number" || i.some((e, t) => t && typeof e != "string")) throw RangeError("Invalid JSON representation of ChangeSet");
			else if (i.length == 1) n.push(i[0], 0);
			else {
				for (; r.length < e;) r.push(T.empty);
				r[e] = T.of(i.slice(1)), n.push(i[0], r[e].length);
			}
		}
		return new e(n, r);
	}
	static createSet(t, n) {
		return new e(t, n);
	}
};
function O(e, t, n, r = !1) {
	if (t == 0 && n <= 0) return;
	let i = e.length - 2;
	i >= 0 && n <= 0 && n == e[i + 1] ? e[i] += t : i >= 0 && t == 0 && e[i] == 0 ? e[i + 1] += n : r ? (e[i] += t, e[i + 1] += n) : e.push(t, n);
}
function Ce(e, t, n) {
	if (n.length == 0) return;
	let r = t.length - 2 >> 1;
	if (r < e.length) e[e.length - 1] = e[e.length - 1].append(n);
	else {
		for (; e.length < r;) e.push(T.empty);
		e.push(n);
	}
}
function we(e, t, n) {
	let r = e.inserted;
	for (let i = 0, a = 0, o = 0; o < e.sections.length;) {
		let s = e.sections[o++], c = e.sections[o++];
		if (c < 0) i += s, a += s;
		else {
			let l = i, u = a, d = T.empty;
			for (; l += s, u += c, c && r && (d = d.append(r[o - 2 >> 1])), !(n || o == e.sections.length || e.sections[o + 1] < 0);) s = e.sections[o++], c = e.sections[o++];
			t(i, l, a, u, d), i = l, a = u;
		}
	}
}
function Te(e, t, n, r = !1) {
	let i = [], a = r ? [] : null, o = new De(e), s = new De(t);
	for (let e = -1;;) if (o.done && s.len || s.done && o.len) throw Error("Mismatched change set lengths");
	else if (o.ins == -1 && s.ins == -1) {
		let e = Math.min(o.len, s.len);
		O(i, e, -1), o.forward(e), s.forward(e);
	} else if (s.ins >= 0 && (o.ins < 0 || e == o.i || o.off == 0 && (s.len < o.len || s.len == o.len && !n))) {
		let t = s.len;
		for (O(i, s.ins, -1); t;) {
			let n = Math.min(o.len, t);
			o.ins >= 0 && e < o.i && o.len <= n && (O(i, 0, o.ins), a && Ce(a, i, o.text), e = o.i), o.forward(n), t -= n;
		}
		s.next();
	} else if (o.ins >= 0) {
		let t = 0, n = o.len;
		for (; n;) if (s.ins == -1) {
			let e = Math.min(n, s.len);
			t += e, n -= e, s.forward(e);
		} else if (s.ins == 0 && s.len < n) n -= s.len, s.next();
		else break;
		O(i, t, e < o.i ? o.ins : 0), a && e < o.i && Ce(a, i, o.text), e = o.i, o.forward(o.len - n);
	} else if (o.done && s.done) return a ? Se.createSet(i, a) : xe.create(i);
	else throw Error("Mismatched change set lengths");
}
function Ee(e, t, n = !1) {
	let r = [], i = n ? [] : null, a = new De(e), o = new De(t);
	for (let e = !1;;) if (a.done && o.done) return i ? Se.createSet(r, i) : xe.create(r);
	else if (a.ins == 0) O(r, a.len, 0, e), a.next();
	else if (o.len == 0 && !o.done) O(r, 0, o.ins, e), i && Ce(i, r, o.text), o.next();
	else if (a.done || o.done) throw Error("Mismatched change set lengths");
	else {
		let t = Math.min(a.len2, o.len), n = r.length;
		if (a.ins == -1) {
			let n = o.ins == -1 ? -1 : o.off ? 0 : o.ins;
			O(r, t, n, e), i && n && Ce(i, r, o.text);
		} else o.ins == -1 ? (O(r, a.off ? 0 : a.len, t, e), i && Ce(i, r, a.textBit(t))) : (O(r, a.off ? 0 : a.len, o.off ? 0 : o.ins, e), i && !o.off && Ce(i, r, o.text));
		e = (a.ins > t || o.ins >= 0 && o.len > t) && (e || r.length > n), a.forward2(t), o.forward(t);
	}
}
var De = class {
	constructor(e) {
		this.set = e, this.i = 0, this.next();
	}
	next() {
		let { sections: e } = this.set;
		this.i < e.length ? (this.len = e[this.i++], this.ins = e[this.i++]) : (this.len = 0, this.ins = -2), this.off = 0;
	}
	get done() {
		return this.ins == -2;
	}
	get len2() {
		return this.ins < 0 ? this.len : this.ins;
	}
	get text() {
		let { inserted: e } = this.set, t = this.i - 2 >> 1;
		return t >= e.length ? T.empty : e[t];
	}
	textBit(e) {
		let { inserted: t } = this.set, n = this.i - 2 >> 1;
		return n >= t.length && !e ? T.empty : t[n].slice(this.off, e == null ? void 0 : this.off + e);
	}
	forward(e) {
		e == this.len ? this.next() : (this.len -= e, this.off += e);
	}
	forward2(e) {
		this.ins == -1 ? this.forward(e) : e == this.ins ? this.next() : (this.ins -= e, this.off += e);
	}
}, Oe = class e {
	constructor(e, t, n, r) {
		this.from = e, this.to = t, this.flags = n, this.goalColumn = r;
	}
	get anchor() {
		return this.flags & 32 ? this.to : this.from;
	}
	get head() {
		return this.flags & 32 ? this.from : this.to;
	}
	get empty() {
		return this.from == this.to;
	}
	get assoc() {
		return this.flags & 8 ? -1 : this.flags & 16 ? 1 : 0;
	}
	get undirectional() {
		return (this.flags & 64) > 0;
	}
	get bidiLevel() {
		let e = this.flags & 7;
		return e == 7 ? null : e;
	}
	map(t, n = -1) {
		let r, i;
		return this.empty ? r = i = t.mapPos(this.from, n) : (r = t.mapPos(this.from, 1), i = t.mapPos(this.to, -1)), r == this.from && i == this.to ? this : new e(r, i, this.flags, this.goalColumn);
	}
	extend(e, t = e, n = 0) {
		if (e <= this.anchor && t >= this.anchor) return k.range(e, t, void 0, void 0, n);
		let r = Math.abs(e - this.anchor) > Math.abs(t - this.anchor) ? e : t;
		return k.range(this.anchor, r, void 0, void 0, n);
	}
	eq(e, t = !1) {
		return this.anchor == e.anchor && this.head == e.head && this.goalColumn == e.goalColumn && (!t || !this.empty || this.assoc == e.assoc);
	}
	toJSON() {
		return {
			anchor: this.anchor,
			head: this.head
		};
	}
	static fromJSON(e) {
		if (!e || typeof e.anchor != "number" || typeof e.head != "number") throw RangeError("Invalid JSON representation for SelectionRange");
		return k.range(e.anchor, e.head);
	}
	static create(t, n, r, i) {
		return new e(t, n, r, i);
	}
}, k = class e {
	constructor(e, t) {
		this.ranges = e, this.mainIndex = t;
	}
	map(t, n = -1) {
		return t.empty ? this : e.create(this.ranges.map((e) => e.map(t, n)), this.mainIndex);
	}
	eq(e, t = !1) {
		if (this.ranges.length != e.ranges.length || this.mainIndex != e.mainIndex) return !1;
		for (let n = 0; n < this.ranges.length; n++) if (!this.ranges[n].eq(e.ranges[n], t)) return !1;
		return !0;
	}
	get main() {
		return this.ranges[this.mainIndex];
	}
	asSingle() {
		return this.ranges.length == 1 ? this : new e([this.main], 0);
	}
	addRange(t, n = !0) {
		return e.create([t].concat(this.ranges), n ? 0 : this.mainIndex + 1);
	}
	replaceRange(t, n = this.mainIndex) {
		let r = this.ranges.slice();
		return r[n] = t, e.create(r, this.mainIndex);
	}
	toJSON() {
		return {
			ranges: this.ranges.map((e) => e.toJSON()),
			main: this.mainIndex
		};
	}
	static fromJSON(t) {
		if (!t || !Array.isArray(t.ranges) || typeof t.main != "number" || t.main >= t.ranges.length) throw RangeError("Invalid JSON representation for EditorSelection");
		return new e(t.ranges.map((e) => Oe.fromJSON(e)), t.main);
	}
	static single(t, n = t) {
		return new e([e.range(t, n)], 0);
	}
	static create(t, n = 0) {
		if (t.length == 0) throw RangeError("A selection needs at least one range");
		for (let r = 0, i = 0; i < t.length; i++) {
			let a = t[i];
			if (a.empty ? a.from <= r : a.from < r) return e.normalized(t.slice(), n);
			r = a.to;
		}
		return new e(t, n);
	}
	static cursor(e, t = 0, n, r) {
		return Oe.create(e, e, (t == 0 ? 0 : t < 0 ? 8 : 16) | (n == null ? 7 : Math.min(6, n)), r);
	}
	static range(e, t, n, r, i) {
		let a = r == null ? 7 : Math.min(6, r);
		return !i && e != t && (i = t < e ? 1 : -1), i && (a |= i < 0 ? 8 : 16), t < e ? Oe.create(t, e, a | 32, n) : Oe.create(e, t, a, n);
	}
	static undirectionalRange(e, t) {
		return Oe.create(e, t, 64, void 0);
	}
	static normalized(t, n = 0) {
		let r = t[n];
		t.sort((e, t) => e.from - t.from), n = t.indexOf(r);
		for (let r = 1; r < t.length; r++) {
			let i = t[r], a = t[r - 1];
			if (i.empty ? i.from <= a.to : i.from < a.to) {
				let o = a.from, s = Math.max(i.to, a.to);
				r <= n && n--, t.splice(--r, 2, i.anchor > i.head ? e.range(s, o) : e.range(o, s));
			}
		}
		return new e(t, n);
	}
};
function ke(e, t) {
	for (let n of e.ranges) if (n.to > t) throw RangeError("Selection points outside of document");
}
var Ae = 0, A = class e {
	constructor(e, t, n, r, i) {
		this.combine = e, this.compareInput = t, this.compare = n, this.isStatic = r, this.id = Ae++, this.default = e([]), this.extensions = typeof i == "function" ? i(this) : i;
	}
	get reader() {
		return this;
	}
	static define(t = {}) {
		return new e(t.combine || ((e) => e), t.compareInput || ((e, t) => e === t), t.compare || (t.combine ? (e, t) => e === t : je), !!t.static, t.enables);
	}
	of(e) {
		return new Me([], this, 0, e);
	}
	compute(e, t) {
		if (this.isStatic) throw Error("Can't compute a static facet");
		return new Me(e, this, 1, t);
	}
	computeN(e, t) {
		if (this.isStatic) throw Error("Can't compute a static facet");
		return new Me(e, this, 2, t);
	}
	from(e, t) {
		return t ||= (e) => e, this.compute([e], (n) => t(n.field(e)));
	}
};
function je(e, t) {
	return e == t || e.length == t.length && e.every((e, n) => e === t[n]);
}
var Me = class {
	constructor(e, t, n, r) {
		this.dependencies = e, this.facet = t, this.type = n, this.value = r, this.id = Ae++;
	}
	dynamicSlot(e) {
		let t = this.value, n = this.facet.compareInput, r = this.id, i = e[r] >> 1, a = this.type == 2, o = !1, s = !1, c = [];
		for (let t of this.dependencies) t == "doc" ? o = !0 : t == "selection" ? s = !0 : (e[t.id] ?? 1) & 1 || c.push(e[t.id]);
		return {
			create(e) {
				return e.values[i] = t(e), 1;
			},
			update(e, r) {
				if (o && r.docChanged || s && (r.docChanged || r.selection) || Pe(e, c)) {
					let r = t(e);
					if (a ? !Ne(r, e.values[i], n) : !n(r, e.values[i])) return e.values[i] = r, 1;
				}
				return 0;
			},
			reconfigure: (e, o) => {
				let s, c = o.config.address[r];
				if (c != null) {
					let r = qe(o, c);
					if (this.dependencies.every((t) => t instanceof A ? o.facet(t) === e.facet(t) : t instanceof Le ? o.field(t, !1) == e.field(t, !1) : !0) || (a ? Ne(s = t(e), r, n) : n(s = t(e), r))) return e.values[i] = r, 0;
				} else s = t(e);
				return e.values[i] = s, 1;
			}
		};
	}
	get extension() {
		return this;
	}
};
function Ne(e, t, n) {
	if (e.length != t.length) return !1;
	for (let r = 0; r < e.length; r++) if (!n(e[r], t[r])) return !1;
	return !0;
}
function Pe(e, t) {
	let n = !1;
	for (let r of t) Ke(e, r) & 1 && (n = !0);
	return n;
}
function Fe(e, t, n) {
	let r = n.map((t) => e[t.id]), i = n.map((e) => e.type), a = r.filter((e) => !(e & 1)), o = e[t.id] >> 1;
	function s(e) {
		let n = [];
		for (let t = 0; t < r.length; t++) {
			let a = qe(e, r[t]);
			if (i[t] == 2) for (let e of a) n.push(e);
			else n.push(a);
		}
		return t.combine(n);
	}
	return {
		create(e) {
			for (let t of r) Ke(e, t);
			return e.values[o] = s(e), 1;
		},
		update(e, n) {
			if (!Pe(e, a)) return 0;
			let r = s(e);
			return t.compare(r, e.values[o]) ? 0 : (e.values[o] = r, 1);
		},
		reconfigure(e, i) {
			let a = Pe(e, r), c = i.config.facets[t.id], l = i.facet(t);
			if (c && !a && je(n, c)) return e.values[o] = l, 0;
			let u = s(e);
			return t.compare(u, l) ? (e.values[o] = l, 0) : (e.values[o] = u, 1);
		}
	};
}
var Ie = /*@__PURE__*/ A.define({ static: !0 }), Le = class e {
	constructor(e, t, n, r, i) {
		this.id = e, this.createF = t, this.updateF = n, this.compareF = r, this.spec = i, this.provides = void 0;
	}
	static define(t) {
		let n = new e(Ae++, t.create, t.update, t.compare || ((e, t) => e === t), t);
		return t.provide && (n.provides = t.provide(n)), n;
	}
	create(e) {
		return (e.facet(Ie).find((e) => e.field == this)?.create || this.createF)(e);
	}
	slot(e) {
		let t = e[this.id] >> 1;
		return {
			create: (e) => (e.values[t] = this.create(e), 1),
			update: (e, n) => {
				let r = e.values[t], i = this.updateF(r, n);
				return this.compareF(r, i) ? 0 : (e.values[t] = i, 1);
			},
			reconfigure: (e, n) => {
				let r = e.facet(Ie), i = n.facet(Ie), a;
				return (a = r.find((e) => e.field == this)) && a != i.find((e) => e.field == this) ? (e.values[t] = a.create(e), 1) : n.config.address[this.id] == null ? (e.values[t] = this.create(e), 1) : (e.values[t] = n.field(this), 0);
			}
		};
	}
	init(e) {
		return [this, Ie.of({
			field: this,
			create: e
		})];
	}
	get extension() {
		return this;
	}
}, Re = {
	lowest: 4,
	low: 3,
	default: 2,
	high: 1,
	highest: 0
};
function ze(e) {
	return (t) => new Ve(t, e);
}
var Be = {
	highest: /*@__PURE__*/ ze(Re.highest),
	high: /*@__PURE__*/ ze(Re.high),
	default: /*@__PURE__*/ ze(Re.default),
	low: /*@__PURE__*/ ze(Re.low),
	lowest: /*@__PURE__*/ ze(Re.lowest)
}, Ve = class {
	constructor(e, t) {
		this.inner = e, this.prec = t;
	}
	get extension() {
		return this;
	}
}, He = class e {
	of(e) {
		return new Ue(this, e);
	}
	reconfigure(t) {
		return e.reconfigure.of({
			compartment: this,
			extension: t
		});
	}
	get(e) {
		return e.config.compartments.get(this);
	}
}, Ue = class {
	constructor(e, t) {
		this.compartment = e, this.inner = t;
	}
	get extension() {
		return this;
	}
}, We = class e {
	constructor(e, t, n, r, i, a) {
		for (this.base = e, this.compartments = t, this.dynamicSlots = n, this.address = r, this.staticValues = i, this.facets = a, this.statusTemplate = []; this.statusTemplate.length < n.length;) this.statusTemplate.push(0);
	}
	staticFacet(e) {
		let t = this.address[e.id];
		return t == null ? e.default : this.staticValues[t >> 1];
	}
	static resolve(t, n, r) {
		let i = [], a = Object.create(null), o = /* @__PURE__ */ new Map();
		for (let e of Ge(t, n, o)) e instanceof Le ? i.push(e) : (a[e.facet.id] || (a[e.facet.id] = [])).push(e);
		let s = Object.create(null), c = [], l = [];
		for (let e of i) s[e.id] = l.length << 1, l.push((t) => e.slot(t));
		let u = r?.config.facets;
		for (let e in a) {
			let t = a[e], n = t[0].facet, i = u && u[e] || [];
			if (t.every((e) => e.type == 0)) {
				if (s[n.id] = c.length << 1 | 1, je(i, t)) c.push(r.facet(n));
				else {
					let e = n.combine(t.map((e) => e.value));
					c.push(r && n.compare(e, r.facet(n)) ? r.facet(n) : e);
				}
			} else {
				for (let e of t) e.type == 0 ? (s[e.id] = c.length << 1 | 1, c.push(e.value)) : (s[e.id] = l.length << 1, l.push((t) => e.dynamicSlot(t)));
				s[n.id] = l.length << 1, l.push((e) => Fe(e, n, t));
			}
		}
		let d = l.map((e) => e(s));
		return new e(t, o, d, s, c, a);
	}
};
function Ge(e, t, n) {
	let r = [
		[],
		[],
		[],
		[],
		[]
	], i = /* @__PURE__ */ new Map();
	function a(e, o) {
		let s = i.get(e);
		if (s != null) {
			if (s <= o) return;
			let t = r[s].indexOf(e);
			t > -1 && r[s].splice(t, 1), e instanceof Ue && n.delete(e.compartment);
		}
		if (i.set(e, o), Array.isArray(e)) for (let t of e) a(t, o);
		else if (e instanceof Ue) {
			if (n.has(e.compartment)) throw RangeError("Duplicate use of compartment in extensions");
			let r = t.get(e.compartment) || e.inner;
			n.set(e.compartment, r), a(r, o);
		} else if (e instanceof Ve) a(e.inner, e.prec);
		else if (e instanceof Le) r[o].push(e), e.provides && a(e.provides, o);
		else if (e instanceof Me) r[o].push(e), e.facet.extensions && a(e.facet.extensions, Re.default);
		else {
			let t = e.extension;
			if (!t) throw Error(`Unrecognized extension value in extension set (${e}).`);
			if (t == e) throw Error(`Unrecognized extension value in extension set (${e}). This sometimes happens because multiple instances of @codemirror/state are loaded, breaking instanceof checks.`);
			a(t, o);
		}
	}
	return a(e, Re.default), r.reduce((e, t) => e.concat(t));
}
function Ke(e, t) {
	if (t & 1) return 2;
	let n = t >> 1, r = e.status[n];
	if (r == 4) throw Error("Cyclic dependency between fields and/or facets");
	if (r & 2) return r;
	e.status[n] = 4;
	let i = e.computeSlot(e, e.config.dynamicSlots[n]);
	return e.status[n] = 2 | i;
}
function qe(e, t) {
	return t & 1 ? e.config.staticValues[t >> 1] : e.values[t >> 1];
}
var Je = /*@__PURE__*/ A.define(), Ye = /*@__PURE__*/ A.define({
	combine: (e) => e.some((e) => e),
	static: !0
}), Xe = /*@__PURE__*/ A.define({
	combine: (e) => e.length ? e[0] : void 0,
	static: !0
}), Ze = /*@__PURE__*/ A.define(), Qe = /*@__PURE__*/ A.define(), $e = /*@__PURE__*/ A.define(), et = /*@__PURE__*/ A.define({ combine: (e) => e.length ? e[0] : !1 }), tt = class {
	constructor(e, t) {
		this.type = e, this.value = t;
	}
	static define() {
		return new nt();
	}
}, nt = class {
	of(e) {
		return new tt(this, e);
	}
}, rt = class {
	constructor(e) {
		this.map = e;
	}
	of(e) {
		return new j(this, e);
	}
}, j = class e {
	constructor(e, t) {
		this.type = e, this.value = t;
	}
	map(t) {
		let n = this.type.map(this.value, t);
		return n === void 0 ? void 0 : n == this.value ? this : new e(this.type, n);
	}
	is(e) {
		return this.type == e;
	}
	static define(e = {}) {
		return new rt(e.map || ((e) => e));
	}
	static mapEffects(e, t) {
		if (!e.length) return e;
		let n = [];
		for (let r of e) {
			let e = r.map(t);
			e && n.push(e);
		}
		return n;
	}
};
j.reconfigure = /*@__PURE__*/ j.define(), j.appendConfig = /*@__PURE__*/ j.define();
var it = class e {
	constructor(t, n, r, i, a, o) {
		this.startState = t, this.changes = n, this.selection = r, this.effects = i, this.annotations = a, this.scrollIntoView = o, this._doc = null, this._state = null, r && ke(r, n.newLength), a.some((t) => t.type == e.time) || (this.annotations = a.concat(e.time.of(Date.now())));
	}
	static create(t, n, r, i, a, o) {
		return new e(t, n, r, i, a, o);
	}
	get newDoc() {
		return this._doc ||= this.changes.apply(this.startState.doc);
	}
	get newSelection() {
		return this.selection || this.startState.selection.map(this.changes);
	}
	get state() {
		return this._state || this.startState.applyTransaction(this), this._state;
	}
	annotation(e) {
		for (let t of this.annotations) if (t.type == e) return t.value;
	}
	get docChanged() {
		return !this.changes.empty;
	}
	get reconfigured() {
		return this.startState.config != this.state.config;
	}
	isUserEvent(t) {
		let n = this.annotation(e.userEvent);
		return !!(n && (n == t || n.length > t.length && n.slice(0, t.length) == t && n[t.length] == "."));
	}
};
it.time = /*@__PURE__*/ tt.define(), it.userEvent = /*@__PURE__*/ tt.define(), it.addToHistory = /*@__PURE__*/ tt.define(), it.remote = /*@__PURE__*/ tt.define();
function at(e, t) {
	let n = [];
	for (let r = 0, i = 0;;) {
		let a, o;
		if (r < e.length && (i == t.length || t[i] >= e[r])) a = e[r++], o = e[r++];
		else if (i < t.length) a = t[i++], o = t[i++];
		else return n;
		!n.length || n[n.length - 1] < a ? n.push(a, o) : n[n.length - 1] < o && (n[n.length - 1] = o);
	}
}
function ot(e, t, n) {
	let r, i, a;
	return n ? (r = t.changes, i = Se.empty(t.changes.length), a = e.changes.compose(t.changes)) : (r = t.changes.map(e.changes), i = e.changes.mapDesc(t.changes, !0), a = e.changes.compose(r)), {
		changes: a,
		selection: t.selection ? t.selection.map(i) : e.selection?.map(r),
		effects: j.mapEffects(e.effects, r).concat(j.mapEffects(t.effects, i)),
		annotations: e.annotations.length ? e.annotations.concat(t.annotations) : t.annotations,
		scrollIntoView: e.scrollIntoView || t.scrollIntoView
	};
}
function st(e, t, n) {
	let r = t.selection, i = ft(t.annotations);
	return t.userEvent && (i = i.concat(it.userEvent.of(t.userEvent))), {
		changes: t.changes instanceof Se ? t.changes : Se.of(t.changes || [], n, e.facet(Xe)),
		selection: r && (r instanceof k ? r : k.single(r.anchor, r.head)),
		effects: ft(t.effects),
		annotations: i,
		scrollIntoView: !!t.scrollIntoView
	};
}
function ct(e, t, n) {
	let r = st(e, t.length ? t[0] : {}, e.doc.length);
	t.length && t[0].filter === !1 && (n = !1);
	for (let i = 1; i < t.length; i++) {
		t[i].filter === !1 && (n = !1);
		let a = !!t[i].sequential;
		r = ot(r, st(e, t[i], a ? r.changes.newLength : e.doc.length), a);
	}
	let i = it.create(e, r.changes, r.selection, r.effects, r.annotations, r.scrollIntoView);
	return ut(n ? lt(i) : i);
}
function lt(e) {
	let t = e.startState, n = !0;
	for (let r of t.facet(Ze)) {
		let t = r(e);
		if (t === !1) {
			n = !1;
			break;
		}
		Array.isArray(t) && (n = n === !0 ? t : at(n, t));
	}
	if (n !== !0) {
		let r, i;
		if (n === !1) i = e.changes.invertedDesc, r = Se.empty(t.doc.length);
		else {
			let t = e.changes.filter(n);
			r = t.changes, i = t.filtered.mapDesc(t.changes).invertedDesc;
		}
		e = it.create(t, r, e.selection && e.selection.map(i), j.mapEffects(e.effects, i), e.annotations, e.scrollIntoView);
	}
	let r = t.facet(Qe);
	for (let n = r.length - 1; n >= 0; n--) {
		let i = r[n](e);
		e = i instanceof it ? i : Array.isArray(i) && i.length == 1 && i[0] instanceof it ? i[0] : ct(t, ft(i), !1);
	}
	return e;
}
function ut(e) {
	let t = e.startState, n = t.facet($e), r = e;
	for (let i = n.length - 1; i >= 0; i--) {
		let a = n[i](e);
		a && Object.keys(a).length && (r = ot(r, st(t, a, e.changes.newLength), !0));
	}
	return r == e ? e : it.create(t, e.changes, e.selection, r.effects, r.annotations, r.scrollIntoView);
}
var dt = [];
function ft(e) {
	return e == null ? dt : Array.isArray(e) ? e : [e];
}
var pt = /*@__PURE__*/ (function(e) {
	return e[e.Word = 0] = "Word", e[e.Space = 1] = "Space", e[e.Other = 2] = "Other", e;
})(pt ||= {}), mt = /[\u00df\u0587\u0590-\u05f4\u0600-\u06ff\u3040-\u309f\u30a0-\u30ff\u3400-\u4db5\u4e00-\u9fcc\uac00-\ud7af]/, ht;
try {
	ht = /*@__PURE__*/ RegExp("[\\p{Alphabetic}\\p{Number}_]", "u");
} catch {}
function gt(e) {
	if (ht) return ht.test(e);
	for (let t = 0; t < e.length; t++) {
		let n = e[t];
		if (/\w/.test(n) || n > "" && (n.toUpperCase() != n.toLowerCase() || mt.test(n))) return !0;
	}
	return !1;
}
function _t(e) {
	return (t) => {
		if (!/\S/.test(t)) return pt.Space;
		if (gt(t)) return pt.Word;
		for (let n = 0; n < e.length; n++) if (t.indexOf(e[n]) > -1) return pt.Word;
		return pt.Other;
	};
}
var M = class e {
	constructor(e, t, n, r, i, a) {
		this.config = e, this.doc = t, this.selection = n, this.values = r, this.status = e.statusTemplate.slice(), this.computeSlot = i, a && (a._state = this);
		for (let e = 0; e < this.config.dynamicSlots.length; e++) Ke(this, e << 1);
		this.computeSlot = null;
	}
	field(e, t = !0) {
		let n = this.config.address[e.id];
		if (n == null) {
			if (t) throw RangeError("Field is not present in this state");
			return;
		}
		return Ke(this, n), qe(this, n);
	}
	update(...e) {
		return ct(this, e, !0);
	}
	applyTransaction(t) {
		let n = this.config, { base: r, compartments: i } = n;
		for (let e of t.effects) e.is(He.reconfigure) ? (n &&= (i = /* @__PURE__ */ new Map(), n.compartments.forEach((e, t) => i.set(t, e)), null), i.set(e.value.compartment, e.value.extension)) : e.is(j.reconfigure) ? (n = null, r = e.value) : e.is(j.appendConfig) && (n = null, r = ft(r).concat(e.value));
		let a;
		n ? a = t.startState.values.slice() : (n = We.resolve(r, i, this), a = new e(n, this.doc, this.selection, n.dynamicSlots.map(() => null), (e, t) => t.reconfigure(e, this), null).values);
		let o = t.startState.facet(Ye) ? t.newSelection : t.newSelection.asSingle();
		new e(n, t.newDoc, o, a, (e, n) => n.update(e, t), t);
	}
	replaceSelection(e) {
		return typeof e == "string" && (e = this.toText(e)), this.changeByRange((t) => ({
			changes: {
				from: t.from,
				to: t.to,
				insert: e
			},
			range: k.cursor(t.from + e.length)
		}));
	}
	changeByRange(e) {
		let t = this.selection, n = e(t.ranges[0]), r = this.changes(n.changes), i = [n.range], a = ft(n.effects);
		for (let n = 1; n < t.ranges.length; n++) {
			let o = e(t.ranges[n]), s = this.changes(o.changes), c = s.map(r);
			for (let e = 0; e < n; e++) i[e] = i[e].map(c);
			let l = r.mapDesc(s, !0);
			i.push(o.range.map(l)), r = r.compose(c), a = j.mapEffects(a, c).concat(j.mapEffects(ft(o.effects), l));
		}
		return {
			changes: r,
			selection: k.create(i, t.mainIndex),
			effects: a
		};
	}
	changes(t = []) {
		return t instanceof Se ? t : Se.of(t, this.doc.length, this.facet(e.lineSeparator));
	}
	toText(t) {
		return T.of(t.split(this.facet(e.lineSeparator) || ye));
	}
	sliceDoc(e = 0, t = this.doc.length) {
		return this.doc.sliceString(e, t, this.lineBreak);
	}
	facet(e) {
		let t = this.config.address[e.id];
		return t == null ? e.default : (Ke(this, t), qe(this, t));
	}
	toJSON(e) {
		let t = {
			doc: this.sliceDoc(),
			selection: this.selection.toJSON()
		};
		if (e) for (let n in e) {
			let r = e[n];
			r instanceof Le && this.config.address[r.id] != null && (t[n] = r.spec.toJSON(this.field(e[n]), this));
		}
		return t;
	}
	static fromJSON(t, n = {}, r) {
		if (!t || typeof t.doc != "string") throw RangeError("Invalid JSON representation for EditorState");
		let i = [];
		if (r) {
			for (let e in r) if (Object.prototype.hasOwnProperty.call(t, e)) {
				let n = r[e], a = t[e];
				i.push(n.init((e) => n.spec.fromJSON(a, e)));
			}
		}
		return e.create({
			doc: t.doc,
			selection: k.fromJSON(t.selection),
			extensions: n.extensions ? i.concat([n.extensions]) : i
		});
	}
	static create(t = {}) {
		let n = We.resolve(t.extensions || [], /* @__PURE__ */ new Map()), r = t.doc instanceof T ? t.doc : T.of((t.doc || "").split(n.staticFacet(e.lineSeparator) || ye)), i = t.selection ? t.selection instanceof k ? t.selection : k.single(t.selection.anchor, t.selection.head) : k.single(0);
		return ke(i, r.length), n.staticFacet(Ye) || (i = i.asSingle()), new e(n, r, i, n.dynamicSlots.map(() => null), (e, t) => t.create(e), null);
	}
	get tabSize() {
		return this.facet(e.tabSize);
	}
	get lineBreak() {
		return this.facet(e.lineSeparator) || "\n";
	}
	get readOnly() {
		return this.facet(et);
	}
	phrase(t, ...n) {
		for (let n of this.facet(e.phrases)) if (Object.prototype.hasOwnProperty.call(n, t)) {
			t = n[t];
			break;
		}
		return n.length && (t = t.replace(/\$(\$|\d*)/g, (e, t) => {
			if (t == "$") return "$";
			let r = +(t || 1);
			return !r || r > n.length ? e : n[r - 1];
		})), t;
	}
	languageDataAt(e, t, n = -1) {
		let r = [];
		for (let i of this.facet(Je)) for (let a of i(this, t, n)) Object.prototype.hasOwnProperty.call(a, e) && r.push(a[e]);
		return r;
	}
	charCategorizer(e) {
		let t = this.languageDataAt("wordChars", e);
		return _t(t.length ? t[0] : "");
	}
	wordAt(e) {
		let { text: t, from: n, length: r } = this.doc.lineAt(e), i = this.charCategorizer(e), a = e - n, o = e - n;
		for (; a > 0;) {
			let e = D(t, a, !1);
			if (i(t.slice(e, a)) != pt.Word) break;
			a = e;
		}
		for (; o < r;) {
			let e = D(t, o);
			if (i(t.slice(o, e)) != pt.Word) break;
			o = e;
		}
		return a == o ? null : k.range(a + n, o + n);
	}
};
M.allowMultipleSelections = Ye, M.tabSize = /*@__PURE__*/ A.define({ combine: (e) => e.length ? e[0] : 4 }), M.lineSeparator = Xe, M.readOnly = et, M.phrases = /*@__PURE__*/ A.define({ compare(e, t) {
	let n = Object.keys(e), r = Object.keys(t);
	return n.length == r.length && n.every((n) => e[n] == t[n]);
} }), M.languageData = Je, M.changeFilter = Ze, M.transactionFilter = Qe, M.transactionExtender = $e, He.reconfigure = /*@__PURE__*/ j.define();
function vt(e, t, n = {}) {
	let r = {};
	for (let t of e) for (let e of Object.keys(t)) {
		let i = t[e], a = r[e];
		if (a === void 0) r[e] = i;
		else if (a !== i && i !== void 0) {
			if (Object.hasOwnProperty.call(n, e)) r[e] = n[e](a, i);
			else throw Error("Config merge conflict for field " + e);
		}
	}
	for (let e in t) r[e] === void 0 && (r[e] = t[e]);
	return r;
}
var yt = class {
	eq(e) {
		return this == e;
	}
	range(e, t = e) {
		return xt.create(e, t, this);
	}
};
yt.prototype.startSide = yt.prototype.endSide = 0, yt.prototype.point = !1, yt.prototype.mapMode = be.TrackDel;
function bt(e, t) {
	return e == t || e.constructor == t.constructor && e.eq(t);
}
var xt = class e {
	constructor(e, t, n) {
		this.from = e, this.to = t, this.value = n;
	}
	static create(t, n, r) {
		return new e(t, n, r);
	}
};
function St(e, t) {
	return e.from - t.from || e.value.startSide - t.value.startSide;
}
var Ct = class e {
	constructor(e, t, n, r) {
		this.from = e, this.to = t, this.value = n, this.maxPoint = r;
	}
	get length() {
		return wt(this.to);
	}
	findIndex(e, t, n, r = 0) {
		let i = n ? this.to : this.from;
		for (let a = r, o = i.length;;) {
			if (a == o) return a;
			let r = a + o >> 1, s = i[r] - e || (n ? this.value[r].endSide : this.value[r].startSide) - t;
			if (r == a) return s >= 0 ? a : o;
			s >= 0 ? o = r : a = r + 1;
		}
	}
	between(e, t, n, r) {
		for (let i = this.findIndex(t, -1e9, !0), a = this.findIndex(n, 1e9, !1, i); i < a; i++) if (r(this.from[i] + e, this.to[i] + e, this.value[i]) === !1) return !1;
	}
	map(t, n, r, i, a) {
		let o = [], s = [], c = [], l = -1, u = -1;
		iter: for (let e = 0; e < this.value.length; e++) {
			let d = this.value[e], f = this.from[e] + t, p = this.to[e] + t, m, h;
			if (f == p) {
				let e = n.mapPos(f, d.startSide, d.mapMode);
				if (e == null || (m = h = e, d.startSide != d.endSide && (h = n.mapPos(f, d.endSide), h < m))) continue;
			} else if (m = n.mapPos(f, d.startSide), h = n.mapPos(p, d.endSide), m > h || m == h && d.startSide > 0 && d.endSide <= 0) continue;
			if (!((h - m || d.endSide - d.startSide) < 0)) {
				if (l < 0 && (l = m), d.point && (u = Math.max(u, h - m)), (m - r || d.startSide - i) >= 0) o.push(d), s.push(m - l), c.push(h - l), r = h, i = d.endSide;
				else {
					if (m == h) for (let e = o.length; e > 0; e--) {
						if ((m - (c[e - 1] + l) || d.startSide - o[e - 1].endSide) >= 0) {
							o.splice(e, 0, d), s.splice(e, 0, m - l), c.splice(e, 0, h - l);
							continue iter;
						}
						if ((m - (s[e - 1] + l) || d.endSide - o[e - 1].startSide) > 0) break;
					}
					a(m, h, d);
				}
			}
		}
		return {
			mapped: o.length ? new e(s, c, o, u) : null,
			pos: l
		};
	}
}, N = class e {
	constructor(e, t, n, r) {
		this.chunkPos = e, this.chunk = t, this.nextLayer = n, this.maxPoint = r;
	}
	static create(t, n, r, i) {
		return new e(t, n, r, i);
	}
	get length() {
		let e = this.chunk.length - 1;
		return e < 0 ? 0 : Math.max(this.chunkEnd(e), this.nextLayer.length);
	}
	get size() {
		if (this.isEmpty) return 0;
		let e = this.nextLayer.size;
		for (let t of this.chunk) e += t.value.length;
		return e;
	}
	chunkEnd(e) {
		return this.chunkPos[e] + this.chunk[e].length;
	}
	update(t) {
		let { add: n = [], sort: r = !1, filterFrom: i = 0, filterTo: a = this.length } = t, o = t.filter;
		if (n.length == 0 && !o) return this;
		if (r && (n = n.slice().sort(St)), this.isEmpty) return n.length ? e.of(n) : this;
		let s = new Ot(this, null, -1).goto(0), c = 0, l = [], u = new Et();
		for (; s.value || c < n.length;) if (c < n.length && (s.from - n[c].from || s.startSide - n[c].value.startSide) >= 0) {
			let e = n[c++];
			u.addInner(e.from, e.to, e.value, !1) || l.push(e);
		} else s.rangeIndex == 1 && s.chunkIndex < this.chunk.length && (c == n.length || this.chunkEnd(s.chunkIndex) < n[c].from) && (!o || i > this.chunkEnd(s.chunkIndex) || a < this.chunkPos[s.chunkIndex]) && u.addChunk(this.chunkPos[s.chunkIndex], this.chunk[s.chunkIndex]) ? s.nextChunk() : ((!o || i > s.to || a < s.from || o(s.from, s.to, s.value)) && (u.addInner(s.from, s.to, s.value, !1) || l.push(xt.create(s.from, s.to, s.value))), s.next());
		return u.finishInner(this.nextLayer.isEmpty && !l.length ? e.empty : this.nextLayer.update({
			add: l,
			filter: o,
			filterFrom: i,
			filterTo: a
		}));
	}
	map(t) {
		if (t.empty || this.isEmpty) return this;
		let n = [], r = [], i = -1, a, o = (e, t, n) => {
			a ||= new Et(), a.addRange(e, t, n, !1);
		};
		for (let e = 0; e < this.chunk.length; e++) {
			let a = this.chunkPos[e], s = this.chunk[e], c = t.touchesRange(a, a + s.length);
			if (c === !1) i = Math.max(i, s.maxPoint), n.push(s), r.push(t.mapPos(a));
			else if (c === !0) {
				let [e, c] = n.length ? [wt(r) + wt(n).length, wt(wt(n).value).endSide] : [-1, -1], { mapped: l, pos: u } = s.map(a, t, e, c, o);
				l && (i = Math.max(i, l.maxPoint), n.push(l), r.push(u));
			}
		}
		let s = this.nextLayer.map(t);
		return a && (s = a.finishInner(s)), n.length == 0 ? s : new e(r, n, s || e.empty, i);
	}
	between(e, t, n) {
		if (!this.isEmpty) {
			for (let r = 0; r < this.chunk.length; r++) {
				let i = this.chunkPos[r], a = this.chunk[r];
				if (t >= i && e <= i + a.length && a.between(i, e - i, t - i, n) === !1) return;
			}
			this.nextLayer.between(e, t, n);
		}
	}
	iter(e = 0) {
		return kt.from([this]).goto(e);
	}
	get isEmpty() {
		return this.nextLayer == this;
	}
	static iter(e, t = 0) {
		return kt.from(e).goto(t);
	}
	static compare(e, t, n, r, i = -1) {
		let a = e.filter((e) => e.maxPoint > 0 || !e.isEmpty && e.maxPoint >= i), o = t.filter((e) => e.maxPoint > 0 || !e.isEmpty && e.maxPoint >= i), s = Dt(a, o, n), c = new jt(a, s, i), l = new jt(o, s, i);
		n.iterGaps((e, t, n) => Mt(c, e, l, t, n, r)), n.empty && n.length == 0 && Mt(c, 0, l, 0, 0, r);
	}
	static eq(e, t, n = 0, r) {
		r ??= 1e9 - 1;
		let i = e.filter((e) => !e.isEmpty && t.indexOf(e) < 0), a = t.filter((t) => !t.isEmpty && e.indexOf(t) < 0);
		if (i.length != a.length) return !1;
		if (!i.length) return !0;
		let o = Dt(i, a), s = new jt(i, o, 0).goto(n), c = new jt(a, o, 0).goto(n);
		for (;;) {
			if (s.to != c.to || !Nt(s.active, c.active) || s.point && (!c.point || !bt(s.point, c.point))) return !1;
			if (s.to > r) return !0;
			s.next(), c.next();
		}
	}
	static spans(e, t, n, r, i = -1) {
		let a = new jt(e, null, i).goto(t), o = t, s = a.openStart;
		for (;;) {
			let e = Math.min(a.to, n);
			if (a.point) {
				let n = a.activeForPoint(a.to), i = a.pointFrom < t ? n.length + 1 : a.point.startSide < 0 ? n.length : Math.min(n.length, s);
				r.point(o, e, a.point, n, i, a.pointRank), s = Math.min(a.openEnd(e), n.length);
			} else e > o && (r.span(o, e, a.active, s), s = a.openEnd(e));
			if (a.to > n) return s + (a.point && a.to > n ? 1 : 0);
			o = a.to, a.next();
		}
	}
	static of(e, t = !1) {
		let n = new Et();
		for (let r of e instanceof xt ? [e] : t ? Tt(e) : e) n.add(r.from, r.to, r.value);
		return n.finish();
	}
	static join(t) {
		if (!t.length) return e.empty;
		let n = wt(t);
		for (let r = t.length - 2; r >= 0; r--) for (let i = t[r]; i != e.empty; i = i.nextLayer) n = new e(i.chunkPos, i.chunk, n, Math.max(i.maxPoint, n.maxPoint));
		return n;
	}
};
N.empty = /*@__PURE__*/ new N([], [], null, -1);
function wt(e) {
	return e[e.length - 1];
}
function Tt(e) {
	if (e.length > 1) for (let t = e[0], n = 1; n < e.length; n++) {
		let r = e[n];
		if (St(t, r) > 0) return e.slice().sort(St);
		t = r;
	}
	return e;
}
N.empty.nextLayer = N.empty;
var Et = class e {
	finishChunk(e) {
		this.chunks.push(new Ct(this.from, this.to, this.value, this.maxPoint)), this.chunkPos.push(this.chunkStart), this.chunkStart = -1, this.setMaxPoint = Math.max(this.setMaxPoint, this.maxPoint), this.maxPoint = -1, e && (this.from = [], this.to = [], this.value = []);
	}
	constructor() {
		this.chunks = [], this.chunkPos = [], this.chunkStart = -1, this.last = null, this.lastFrom = -1e9, this.lastTo = -1e9, this.from = [], this.to = [], this.value = [], this.maxPoint = -1, this.setMaxPoint = -1, this.nextLayer = null;
	}
	add(e, t, n) {
		this.addRange(e, t, n, !0);
	}
	addRange(t, n, r, i) {
		this.addInner(t, n, r, i) || (this.nextLayer ||= new e()).addRange(t, n, r, i);
	}
	addInner(e, t, n, r) {
		let i = e - this.lastTo || n.startSide - this.last.endSide;
		if (r && i <= 0 && (e - this.lastFrom || n.startSide - this.last.startSide) < 0) throw Error("Ranges must be added sorted by `from` position and `startSide`");
		return i < 0 ? !1 : (this.from.length == 250 && this.finishChunk(!0), this.chunkStart < 0 && (this.chunkStart = e), this.from.push(e - this.chunkStart), this.to.push(t - this.chunkStart), this.last = n, this.lastFrom = e, this.lastTo = t, this.value.push(n), n.point && (this.maxPoint = Math.max(this.maxPoint, t - e)), !0);
	}
	addChunk(e, t) {
		if ((e - this.lastTo || t.value[0].startSide - this.last.endSide) < 0) return !1;
		this.from.length && this.finishChunk(!0), this.setMaxPoint = Math.max(this.setMaxPoint, t.maxPoint), this.chunks.push(t), this.chunkPos.push(e);
		let n = t.value.length - 1;
		return this.last = t.value[n], this.lastFrom = t.from[n] + e, this.lastTo = t.to[n] + e, !0;
	}
	finish() {
		return this.finishInner(N.empty);
	}
	finishInner(e) {
		if (this.from.length && this.finishChunk(!1), this.chunks.length == 0) return e;
		let t = N.create(this.chunkPos, this.chunks, this.nextLayer ? this.nextLayer.finishInner(e) : e, this.setMaxPoint);
		return this.from = null, t;
	}
};
function Dt(e, t, n) {
	let r = /* @__PURE__ */ new Map();
	for (let t of e) for (let e = 0; e < t.chunk.length; e++) t.chunk[e].maxPoint <= 0 && r.set(t.chunk[e], t.chunkPos[e]);
	let i = /* @__PURE__ */ new Set();
	for (let e of t) for (let t = 0; t < e.chunk.length; t++) {
		let a = r.get(e.chunk[t]);
		a != null && (n ? n.mapPos(a) : a) == e.chunkPos[t] && !n?.touchesRange(a, a + e.chunk[t].length) && i.add(e.chunk[t]);
	}
	return i;
}
var Ot = class {
	constructor(e, t, n, r = 0) {
		this.layer = e, this.skip = t, this.minPoint = n, this.rank = r;
	}
	get startSide() {
		return this.value ? this.value.startSide : 0;
	}
	get endSide() {
		return this.value ? this.value.endSide : 0;
	}
	goto(e, t = -1e9) {
		return this.chunkIndex = this.rangeIndex = 0, this.gotoInner(e, t, !1), this;
	}
	gotoInner(e, t, n) {
		for (; this.chunkIndex < this.layer.chunk.length;) {
			let t = this.layer.chunk[this.chunkIndex];
			if (!(this.skip && this.skip.has(t) || this.layer.chunkEnd(this.chunkIndex) < e || t.maxPoint < this.minPoint)) break;
			this.chunkIndex++, n = !1;
		}
		if (this.chunkIndex < this.layer.chunk.length) {
			let r = this.layer.chunk[this.chunkIndex].findIndex(e - this.layer.chunkPos[this.chunkIndex], t, !0);
			(!n || this.rangeIndex < r) && this.setRangeIndex(r);
		}
		this.next();
	}
	forward(e, t) {
		(this.to - e || this.endSide - t) < 0 && this.gotoInner(e, t, !0);
	}
	next() {
		for (;;) if (this.chunkIndex == this.layer.chunk.length) {
			this.from = this.to = 1e9, this.value = null;
			break;
		} else {
			let e = this.layer.chunkPos[this.chunkIndex], t = this.layer.chunk[this.chunkIndex], n = e + t.from[this.rangeIndex];
			if (this.from = n, this.to = e + t.to[this.rangeIndex], this.value = t.value[this.rangeIndex], this.setRangeIndex(this.rangeIndex + 1), this.minPoint < 0 || this.value.point && this.to - this.from >= this.minPoint) break;
		}
	}
	setRangeIndex(e) {
		if (e == this.layer.chunk[this.chunkIndex].value.length) {
			if (this.chunkIndex++, this.skip) for (; this.chunkIndex < this.layer.chunk.length && this.skip.has(this.layer.chunk[this.chunkIndex]);) this.chunkIndex++;
			this.rangeIndex = 0;
		} else this.rangeIndex = e;
	}
	nextChunk() {
		this.chunkIndex++, this.rangeIndex = 0, this.next();
	}
	compare(e) {
		return this.from - e.from || this.startSide - e.startSide || this.rank - e.rank || this.to - e.to || this.endSide - e.endSide;
	}
}, kt = class e {
	constructor(e) {
		this.heap = e;
	}
	static from(t, n = null, r = -1) {
		let i = [];
		for (let e = 0; e < t.length; e++) for (let a = t[e]; !a.isEmpty; a = a.nextLayer) a.maxPoint >= r && i.push(new Ot(a, n, r, e));
		return i.length == 1 ? i[0] : new e(i);
	}
	get startSide() {
		return this.value ? this.value.startSide : 0;
	}
	goto(e, t = -1e9) {
		for (let n of this.heap) n.goto(e, t);
		for (let e = this.heap.length >> 1; e >= 0; e--) At(this.heap, e);
		return this.next(), this;
	}
	forward(e, t) {
		for (let n of this.heap) n.forward(e, t);
		for (let e = this.heap.length >> 1; e >= 0; e--) At(this.heap, e);
		(this.to - e || this.value.endSide - t) < 0 && this.next();
	}
	next() {
		if (this.heap.length == 0) this.from = this.to = 1e9, this.value = null, this.rank = -1;
		else {
			let e = this.heap[0];
			this.from = e.from, this.to = e.to, this.value = e.value, this.rank = e.rank, e.value && e.next(), At(this.heap, 0);
		}
	}
};
function At(e, t) {
	for (let n = e[t];;) {
		let r = (t << 1) + 1;
		if (r >= e.length) break;
		let i = e[r];
		if (r + 1 < e.length && i.compare(e[r + 1]) >= 0 && (i = e[r + 1], r++), n.compare(i) < 0) break;
		e[r] = n, e[t] = i, t = r;
	}
}
var jt = class {
	constructor(e, t, n) {
		this.minPoint = n, this.active = [], this.activeTo = [], this.activeRank = [], this.minActive = -1, this.point = null, this.pointFrom = 0, this.pointRank = 0, this.to = -1e9, this.endSide = 0, this.openStart = -1, this.cursor = kt.from(e, t, n);
	}
	goto(e, t = -1e9) {
		return this.cursor.goto(e, t), this.active.length = this.activeTo.length = this.activeRank.length = 0, this.minActive = -1, this.to = e, this.endSide = t, this.openStart = -1, this.next(), this;
	}
	forward(e, t) {
		for (; this.minActive > -1 && (this.activeTo[this.minActive] - e || this.active[this.minActive].endSide - t) < 0;) this.removeActive(this.minActive);
		this.cursor.forward(e, t);
	}
	removeActive(e) {
		Pt(this.active, e), Pt(this.activeTo, e), Pt(this.activeRank, e), this.minActive = It(this.active, this.activeTo);
	}
	addActive(e) {
		let t = 0, { value: n, to: r, rank: i } = this.cursor;
		for (; t < this.activeRank.length && (i - this.activeRank[t] || r - this.activeTo[t]) > 0;) t++;
		Ft(this.active, t, n), Ft(this.activeTo, t, r), Ft(this.activeRank, t, i), e && Ft(e, t, this.cursor.from), this.minActive = It(this.active, this.activeTo);
	}
	next() {
		let e = this.to, t = this.point;
		this.point = null;
		let n = this.openStart < 0 ? [] : null;
		for (;;) {
			let r = this.minActive;
			if (r > -1 && (this.activeTo[r] - this.cursor.from || this.active[r].endSide - this.cursor.startSide) < 0) {
				if (this.activeTo[r] > e) {
					this.to = this.activeTo[r], this.endSide = this.active[r].endSide;
					break;
				}
				this.removeActive(r), n && Pt(n, r);
			} else if (!this.cursor.value) {
				this.to = this.endSide = 1e9;
				break;
			} else if (this.cursor.from > e) {
				this.to = this.cursor.from, this.endSide = this.cursor.startSide;
				break;
			} else {
				let e = this.cursor.value;
				if (!e.point) this.addActive(n), this.cursor.next();
				else if (t && this.cursor.to == this.to && this.cursor.from < this.cursor.to) this.cursor.next();
				else {
					this.point = e, this.pointFrom = this.cursor.from, this.pointRank = this.cursor.rank, this.to = this.cursor.to, this.endSide = e.endSide, this.cursor.next(), this.forward(this.to, this.endSide);
					break;
				}
			}
		}
		if (n) {
			this.openStart = 0;
			for (let t = n.length - 1; t >= 0 && n[t] < e; t--) this.openStart++;
		}
	}
	activeForPoint(e) {
		if (!this.active.length) return this.active;
		let t = [];
		for (let n = this.active.length - 1; n >= 0 && !(this.activeRank[n] < this.pointRank); n--) (this.activeTo[n] > e || this.activeTo[n] == e && this.active[n].endSide >= this.point.endSide) && t.push(this.active[n]);
		return t.reverse();
	}
	openEnd(e) {
		let t = 0;
		for (let n = this.activeTo.length - 1; n >= 0 && this.activeTo[n] > e; n--) t++;
		return t;
	}
};
function Mt(e, t, n, r, i, a) {
	e.goto(t), n.goto(r);
	let o = r + i, s = r, c = r - t, l = !!a.boundChange;
	for (let t = !1;;) {
		let r = e.to + c - n.to, i = r || e.endSide - n.endSide, u = i < 0 ? e.to + c : n.to, d = Math.min(u, o);
		if (e.point || n.point ? (e.point && n.point && bt(e.point, n.point) && Nt(e.activeForPoint(e.to), n.activeForPoint(n.to)) || a.comparePoint(s, d, e.point, n.point), t = !1) : (t && a.boundChange(s), d > s && !Nt(e.active, n.active) && a.compareRange(s, d, e.active, n.active), l && d < o && (r || e.openEnd(u) != n.openEnd(u)) && (t = !0)), u > o) break;
		s = u, i <= 0 && e.next(), i >= 0 && n.next();
	}
}
function Nt(e, t) {
	if (e.length != t.length) return !1;
	for (let n = 0; n < e.length; n++) if (e[n] != t[n] && !bt(e[n], t[n])) return !1;
	return !0;
}
function Pt(e, t) {
	for (let n = t, r = e.length - 1; n < r; n++) e[n] = e[n + 1];
	e.pop();
}
function Ft(e, t, n) {
	for (let n = e.length - 1; n >= t; n--) e[n + 1] = e[n];
	e[t] = n;
}
function It(e, t) {
	let n = -1, r = 1e9;
	for (let i = 0; i < t.length; i++) (t[i] - r || e[i].endSide - e[n].endSide) < 0 && (n = i, r = t[i]);
	return n;
}
function Lt(e, t, n = e.length) {
	let r = 0;
	for (let i = 0; i < n && i < e.length;) e.charCodeAt(i) == 9 ? (r += t - r % t, i++) : (r++, i = D(e, i));
	return r;
}
function Rt(e, t, n, r) {
	for (let r = 0, i = 0;;) {
		if (i >= t) return r;
		if (r == e.length) break;
		i += e.charCodeAt(r) == 9 ? n - i % n : 1, r = D(e, r);
	}
	return r === !0 ? -1 : e.length;
}
for (var zt = "ͼ", Bt = typeof Symbol > "u" ? "__ͼ" : Symbol.for(zt), Vt = typeof Symbol > "u" ? "__styleSet" + Math.floor(Math.random() * 1e8) : Symbol("styleSet"), Ht = typeof globalThis < "u" ? globalThis : typeof window < "u" ? window : {}, Ut = class {
	constructor(e, t) {
		this.rules = [];
		let { finish: n } = t || {};
		function r(e) {
			return /^@/.test(e) ? [e] : e.split(/,\s*/);
		}
		function i(e, t, a, o) {
			let s = [], c = /^@(\w+)\b/.exec(e[0]), l = c && c[1] == "keyframes";
			if (c && t == null) return a.push(e[0] + ";");
			for (let n in t) {
				let o = t[n];
				if (/&/.test(n)) i(n.split(/,\s*/).map((t) => e.map((e) => t.replace(/&/, e))).reduce((e, t) => e.concat(t)), o, a);
				else if (o && typeof o == "object") {
					if (!c) throw RangeError("The value of a property (" + n + ") should be a primitive value.");
					i(r(n), o, s, l);
				} else o != null && s.push(n.replace(/_.*/, "").replace(/[A-Z]/g, (e) => "-" + e.toLowerCase()) + ": " + o + ";");
			}
			(s.length || l) && a.push((n && !c && !o ? e.map(n) : e).join(", ") + " {" + s.join(" ") + "}");
		}
		for (let t in e) i(r(t), e[t], this.rules);
	}
	getRules() {
		return this.rules.join("\n");
	}
	static newName() {
		let e = Ht[Bt] || 1;
		return Ht[Bt] = e + 1, zt + e.toString(36);
	}
	static mount(e, t, n) {
		let r = e[Vt], i = n && n.nonce;
		r ? i && r.setNonce(i) : r = new Gt(e, i), r.mount(Array.isArray(t) ? t : [t], e);
	}
}, Wt = /* @__PURE__ */ new Map(), Gt = class {
	constructor(e, t) {
		let n = e.ownerDocument || e, r = n.defaultView;
		if (!e.head && e.adoptedStyleSheets && r.CSSStyleSheet) {
			let t = Wt.get(n);
			if (t) return e[Vt] = t;
			this.sheet = new r.CSSStyleSheet(), Wt.set(n, this);
		} else this.styleTag = n.createElement("style"), t && this.styleTag.setAttribute("nonce", t);
		this.modules = [], e[Vt] = this;
	}
	mount(e, t) {
		let n = this.sheet, r = 0, i = 0;
		for (let t = 0; t < e.length; t++) {
			let a = e[t], o = this.modules.indexOf(a);
			if (o < i && o > -1 && (this.modules.splice(o, 1), i--, o = -1), o == -1) {
				if (this.modules.splice(i++, 0, a), n) for (let e = 0; e < a.rules.length; e++) n.insertRule(a.rules[e], r++);
			} else {
				for (; i < o;) r += this.modules[i++].rules.length;
				r += a.rules.length, i++;
			}
		}
		if (n) t.adoptedStyleSheets.indexOf(this.sheet) < 0 && (t.adoptedStyleSheets = [this.sheet, ...t.adoptedStyleSheets]);
		else {
			let e = "";
			for (let t = 0; t < this.modules.length; t++) e += this.modules[t].getRules() + "\n";
			this.styleTag.textContent = e;
			let n = t.head || t;
			this.styleTag.parentNode != n && n.insertBefore(this.styleTag, n.firstChild);
		}
	}
	setNonce(e) {
		this.styleTag && this.styleTag.getAttribute("nonce") != e && this.styleTag.setAttribute("nonce", e);
	}
}, Kt = {
	8: "Backspace",
	9: "Tab",
	10: "Enter",
	12: "NumLock",
	13: "Enter",
	16: "Shift",
	17: "Control",
	18: "Alt",
	20: "CapsLock",
	27: "Escape",
	32: " ",
	33: "PageUp",
	34: "PageDown",
	35: "End",
	36: "Home",
	37: "ArrowLeft",
	38: "ArrowUp",
	39: "ArrowRight",
	40: "ArrowDown",
	44: "PrintScreen",
	45: "Insert",
	46: "Delete",
	59: ";",
	61: "=",
	91: "Meta",
	92: "Meta",
	106: "*",
	107: "+",
	108: ",",
	109: "-",
	110: ".",
	111: "/",
	144: "NumLock",
	145: "ScrollLock",
	160: "Shift",
	161: "Shift",
	162: "Control",
	163: "Control",
	164: "Alt",
	165: "Alt",
	173: "-",
	186: ";",
	187: "=",
	188: ",",
	189: "-",
	190: ".",
	191: "/",
	192: "`",
	219: "[",
	220: "\\",
	221: "]",
	222: "'"
}, qt = {
	48: ")",
	49: "!",
	50: "@",
	51: "#",
	52: "$",
	53: "%",
	54: "^",
	55: "&",
	56: "*",
	57: "(",
	59: ":",
	61: "+",
	173: "_",
	186: ":",
	187: "+",
	188: "<",
	189: "_",
	190: ">",
	191: "?",
	192: "~",
	219: "{",
	220: "|",
	221: "}",
	222: "\""
}, Jt = typeof navigator < "u" && /Mac/.test(navigator.platform), Yt = typeof navigator < "u" && /MSIE \d|Trident\/(?:[7-9]|\d{2,})\..*rv:(\d+)/.exec(navigator.userAgent), Xt = 0; Xt < 10; Xt++) Kt[48 + Xt] = Kt[96 + Xt] = String(Xt);
for (var Xt = 1; Xt <= 24; Xt++) Kt[Xt + 111] = "F" + Xt;
for (var Xt = 65; Xt <= 90; Xt++) Kt[Xt] = String.fromCharCode(Xt + 32), qt[Xt] = String.fromCharCode(Xt);
for (var Zt in Kt) qt.hasOwnProperty(Zt) || (qt[Zt] = Kt[Zt]);
function Qt(e) {
	var t = !(Jt && e.metaKey && e.shiftKey && !e.ctrlKey && !e.altKey || Yt && e.shiftKey && e.key && e.key.length == 1 || e.key == "Unidentified") && e.key || (e.shiftKey ? qt : Kt)[e.keyCode] || e.key || "Unidentified";
	return t == "Esc" && (t = "Escape"), t == "Del" && (t = "Delete"), t == "Left" && (t = "ArrowLeft"), t == "Up" && (t = "ArrowUp"), t == "Right" && (t = "ArrowRight"), t == "Down" && (t = "ArrowDown"), t;
}
//#endregion
//#region node_modules/@codemirror/view/dist/index.js
var $t = typeof navigator < "u" ? navigator : {
	userAgent: "",
	vendor: "",
	platform: ""
}, en = typeof document < "u" ? document : { documentElement: { style: {} } }, tn = /*@__PURE__*/ /Edge\/(\d+)/.exec($t.userAgent), nn = /*@__PURE__*/ /MSIE \d/.test($t.userAgent), rn = /*@__PURE__*/ /Trident\/(?:[7-9]|\d{2,})\..*rv:(\d+)/.exec($t.userAgent), an = !!(nn || rn || tn), on = !an && /*@__PURE__*/ /gecko\/(\d+)/i.test($t.userAgent), sn = !an && /*@__PURE__*/ /Chrome\/(\d+)/.exec($t.userAgent), cn = "webkitFontSmoothing" in en.documentElement.style, ln = !an && /*@__PURE__*/ /Apple Computer/.test($t.vendor), un = ln && (/*@__PURE__*/ /Mobile\/\w+/.test($t.userAgent) || $t.maxTouchPoints > 2), P = {
	mac: un || /*@__PURE__*/ /Mac/.test($t.platform),
	windows: /*@__PURE__*/ /Win/.test($t.platform),
	linux: /*@__PURE__*/ /Linux|X11/.test($t.platform),
	ie: an,
	ie_version: nn ? en.documentMode || 6 : rn ? +rn[1] : tn ? +tn[1] : 0,
	gecko: on,
	gecko_version: on ? +(/*@__PURE__*/ /Firefox\/(\d+)/.exec($t.userAgent) || [0, 0])[1] : 0,
	chrome: !!sn,
	chrome_version: sn ? +sn[1] : 0,
	ios: un,
	android: /*@__PURE__*/ /Android\b/.test($t.userAgent),
	webkit: cn,
	webkit_version: cn ? +(/*@__PURE__*/ /\bAppleWebKit\/(\d+)/.exec($t.userAgent) || [0, 0])[1] : 0,
	safari: ln,
	safari_version: ln ? +(/*@__PURE__*/ /\bVersion\/(\d+(\.\d+)?)/.exec($t.userAgent) || [0, 0])[1] : 0,
	tabSize: en.documentElement.style.tabSize == null ? "-moz-tab-size" : "tab-size"
};
function dn(e, t) {
	for (let n in e) n == "class" && t.class ? t.class += " " + e.class : n == "style" && t.style ? t.style += ";" + e.style : t[n] = e[n];
	return t;
}
var fn = /*@__PURE__*/ Object.create(null);
function pn(e, t, n) {
	if (e == t) return !0;
	e ||= fn, t ||= fn;
	let r = Object.keys(e), i = Object.keys(t);
	if (r.length - (n && r.indexOf(n) > -1 ? 1 : 0) != i.length - (n && i.indexOf(n) > -1 ? 1 : 0)) return !1;
	for (let a of r) if (a != n && (i.indexOf(a) == -1 || e[a] !== t[a])) return !1;
	return !0;
}
function mn(e, t) {
	for (let n = e.attributes.length - 1; n >= 0; n--) {
		let r = e.attributes[n].name;
		t[r] ?? e.removeAttribute(r);
	}
	for (let n in t) {
		let r = t[n];
		n == "style" ? e.style.cssText = r : e.getAttribute(n) != r && e.setAttribute(n, r);
	}
}
function hn(e, t, n) {
	let r = !1;
	if (t) for (let i in t) n && i in n || (r = !0, i == "style" ? e.style.cssText = "" : e.removeAttribute(i));
	if (n) for (let i in n) t && t[i] == n[i] || (r = !0, i == "style" ? e.style.cssText = n[i] : e.setAttribute(i, n[i]));
	return r;
}
function gn(e) {
	let t = Object.create(null);
	for (let n = 0; n < e.attributes.length; n++) {
		let r = e.attributes[n];
		t[r.name] = r.value;
	}
	return t;
}
var _n = class {
	eq(e) {
		return !1;
	}
	updateDOM(e, t, n) {
		return !1;
	}
	compare(e) {
		return this == e || this.constructor == e.constructor && this.eq(e);
	}
	get estimatedHeight() {
		return -1;
	}
	get lineBreaks() {
		return 0;
	}
	ignoreEvent(e) {
		return !0;
	}
	coordsAt(e, t, n) {
		return null;
	}
	get isHidden() {
		return !1;
	}
	get editable() {
		return !1;
	}
	destroy(e) {}
}, vn = /*@__PURE__*/ (function(e) {
	return e[e.Text = 0] = "Text", e[e.WidgetBefore = 1] = "WidgetBefore", e[e.WidgetAfter = 2] = "WidgetAfter", e[e.WidgetRange = 3] = "WidgetRange", e;
})(vn ||= {}), F = class extends yt {
	constructor(e, t, n, r) {
		super(), this.startSide = e, this.endSide = t, this.widget = n, this.spec = r;
	}
	get heightRelevant() {
		return !1;
	}
	static mark(e) {
		return new yn(e);
	}
	static widget(e) {
		let t = Math.max(-1e4, Math.min(1e4, e.side || 0)), n = !!e.block;
		return t += n && !e.inlineOrder ? t > 0 ? 3e8 : -4e8 : t > 0 ? 1e8 : -1e8, new xn(e, t, t, n, e.widget || null, !1);
	}
	static replace(e) {
		let t = !!e.block, n, r;
		if (e.isBlockGap) n = -5e8, r = 4e8;
		else {
			let { start: i, end: a } = Sn(e, t);
			n = (i ? t ? -3e8 : -1 : 5e8) - 1, r = (a ? t ? 2e8 : 1 : -6e8) + 1;
		}
		return new xn(e, n, r, t, e.widget || null, !0);
	}
	static line(e) {
		return new bn(e);
	}
	static set(e, t = !1) {
		return N.of(e, t);
	}
	hasHeight() {
		return this.widget ? this.widget.estimatedHeight > -1 : !1;
	}
};
F.none = N.empty;
var yn = class e extends F {
	constructor(e) {
		let { start: t, end: n } = Sn(e);
		super(t ? -1 : 5e8, n ? 1 : -6e8, null, e), this.tagName = e.tagName || "span", this.attrs = e.class && e.attributes ? dn(e.attributes, { class: e.class }) : e.class ? { class: e.class } : e.attributes || fn;
	}
	eq(t) {
		return this == t || t instanceof e && this.tagName == t.tagName && pn(this.attrs, t.attrs);
	}
	range(e, t = e) {
		if (e >= t) throw RangeError("Mark decorations may not be empty");
		return super.range(e, t);
	}
};
yn.prototype.point = !1;
var bn = class e extends F {
	constructor(e) {
		super(-2e8, -2e8, null, e);
	}
	eq(t) {
		return t instanceof e && this.spec.class == t.spec.class && pn(this.spec.attributes, t.spec.attributes);
	}
	range(e, t = e) {
		if (t != e) throw RangeError("Line decoration ranges must be zero-length");
		return super.range(e, t);
	}
};
bn.prototype.mapMode = be.TrackBefore, bn.prototype.point = !0;
var xn = class e extends F {
	constructor(e, t, n, r, i, a) {
		super(t, n, i, e), this.block = r, this.isReplace = a, this.mapMode = r ? t <= 0 ? be.TrackBefore : be.TrackAfter : be.TrackDel;
	}
	get type() {
		return this.startSide == this.endSide ? this.startSide <= 0 ? vn.WidgetBefore : vn.WidgetAfter : vn.WidgetRange;
	}
	get heightRelevant() {
		return this.block || !!this.widget && (this.widget.estimatedHeight >= 5 || this.widget.lineBreaks > 0);
	}
	eq(t) {
		return t instanceof e && Cn(this.widget, t.widget) && this.block == t.block && this.startSide == t.startSide && this.endSide == t.endSide;
	}
	range(e, t = e) {
		if (this.isReplace && (e > t || e == t && this.startSide > 0 && this.endSide <= 0)) throw RangeError("Invalid range for replacement decoration");
		if (!this.isReplace && t != e) throw RangeError("Widget decorations can only have zero-length ranges");
		return super.range(e, t);
	}
};
xn.prototype.point = !0;
function Sn(e, t = !1) {
	let { inclusiveStart: n, inclusiveEnd: r } = e;
	return n ??= e.inclusive, r ??= e.inclusive, {
		start: n ?? t,
		end: r ?? t
	};
}
function Cn(e, t) {
	return e == t || !!(e && t && e.compare(t));
}
function wn(e, t, n, r = 0) {
	let i = n.length - 1;
	i >= 0 && n[i] + r >= e ? n[i] = Math.max(n[i], t) : n.push(e, t);
}
var Tn = class e extends yt {
	constructor(e, t, n) {
		super(), this.tagName = e, this.attributes = t, this.rank = n;
	}
	eq(t) {
		return t == this || t instanceof e && this.tagName == t.tagName && pn(this.attributes, t.attributes);
	}
	static create(t) {
		return new e(t.tagName, t.attributes || fn, t.rank == null ? 50 : Math.max(0, Math.min(t.rank, 100)));
	}
	static set(e, t = !1) {
		return N.of(e, t);
	}
};
Tn.prototype.startSide = Tn.prototype.endSide = -1;
function En(e) {
	let t;
	return t = e.nodeType == 11 ? e.getSelection ? e : e.ownerDocument : e, t.getSelection();
}
function Dn(e, t) {
	return t ? e == t || e.contains(t.nodeType == 1 ? t : t.parentNode) : !1;
}
function On(e, t) {
	if (!t.anchorNode) return !1;
	try {
		return Dn(e, t.anchorNode);
	} catch {
		return !1;
	}
}
function kn(e) {
	return e.nodeType == 3 ? Kn(e, 0, e.nodeValue.length).getClientRects() : e.nodeType == 1 ? e.getClientRects() : [];
}
function An(e, t, n, r) {
	return n ? Nn(e, t, n, r, -1) || Nn(e, t, n, r, 1) : !1;
}
function jn(e) {
	for (var t = 0;; t++) if (e = e.previousSibling, !e) return t;
}
function Mn(e) {
	return e.nodeType == 1 && /^(DIV|P|LI|UL|OL|BLOCKQUOTE|DD|DT|H\d|SECTION|PRE)$/.test(e.nodeName);
}
function Nn(e, t, n, r, i) {
	for (;;) {
		if (e == n && t == r) return !0;
		if (t == (i < 0 ? 0 : Pn(e))) {
			if (e.nodeName == "DIV") return !1;
			let n = e.parentNode;
			if (!n || n.nodeType != 1) return !1;
			t = jn(e) + (i < 0 ? 0 : 1), e = n;
		} else if (e.nodeType == 1) {
			if (e = e.childNodes[t + (i < 0 ? -1 : 0)], e.nodeType == 1 && e.contentEditable == "false") return !1;
			t = i < 0 ? Pn(e) : 0;
		} else return !1;
	}
}
function Pn(e) {
	return e.nodeType == 3 ? e.nodeValue.length : e.childNodes.length;
}
function Fn(e, t) {
	let { left: n, right: r } = e;
	if (n == r) return e;
	let i = t ? n : r;
	return {
		left: i,
		right: i,
		top: e.top,
		bottom: e.bottom
	};
}
function In(e) {
	let t = e.visualViewport;
	return t ? {
		left: 0,
		right: t.width,
		top: 0,
		bottom: t.height
	} : {
		left: 0,
		right: e.innerWidth,
		top: 0,
		bottom: e.innerHeight
	};
}
function Ln(e, t) {
	let n = t.width / e.offsetWidth, r = t.height / e.offsetHeight;
	return (n > .995 && n < 1.005 || !isFinite(n) || Math.abs(t.width - e.offsetWidth) < 1) && (n = 1), (r > .995 && r < 1.005 || !isFinite(r) || Math.abs(t.height - e.offsetHeight) < 1) && (r = 1), {
		scaleX: n,
		scaleY: r
	};
}
function Rn(e, t, n, r, i, a, o, s) {
	let c = e.ownerDocument, l = c.defaultView || window;
	for (let u = e, d = !1; u && !d;) if (u.nodeType == 1) {
		let e, f = u == c.body, p = 1, m = 1;
		if (f) e = In(l);
		else {
			if (/^(fixed|sticky)$/.test(getComputedStyle(u).position) && (d = !0), u.scrollHeight <= u.clientHeight && u.scrollWidth <= u.clientWidth) {
				u = u.assignedSlot || u.parentNode;
				continue;
			}
			let t = u.getBoundingClientRect();
			({scaleX: p, scaleY: m} = Ln(u, t)), e = {
				left: t.left,
				right: t.left + u.clientWidth * p,
				top: t.top,
				bottom: t.top + u.clientHeight * m
			};
		}
		let h = 0, g = 0;
		if (i == "nearest") t.top < e.top + o ? (g = t.top - (e.top + o), n > 0 && t.bottom > e.bottom + g && (g = t.bottom - e.bottom + o)) : t.bottom > e.bottom - o && (g = t.bottom - e.bottom + o, n < 0 && t.top - g < e.top && (g = t.top - (e.top + o)));
		else {
			let r = t.bottom - t.top, a = e.bottom - e.top;
			g = (i == "center" && r <= a ? t.top + r / 2 - a / 2 : i == "start" || i == "center" && n < 0 ? t.top - o : t.bottom - a + o) - e.top;
		}
		if (r == "nearest" ? t.left < e.left + a ? (h = t.left - (e.left + a), n > 0 && t.right > e.right + h && (h = t.right - e.right + a)) : t.right > e.right - a && (h = t.right - e.right + a, n < 0 && t.left < e.left + h && (h = t.left - (e.left + a))) : h = (r == "center" ? t.left + (t.right - t.left) / 2 - (e.right - e.left) / 2 : r == "start" == s ? t.left - a : t.right - (e.right - e.left) + a) - e.left, h || g) {
			if (f) l.scrollBy(h, g);
			else {
				let e = 0, n = 0;
				if (g) {
					let e = u.scrollTop;
					u.scrollTop += g / m, n = (u.scrollTop - e) * m;
				}
				if (h) {
					let t = u.scrollLeft;
					u.scrollLeft += h / p, e = (u.scrollLeft - t) * p;
				}
				t = {
					left: t.left - e,
					top: t.top - n,
					right: t.right - e,
					bottom: t.bottom - n
				}, e && Math.abs(e - h) < 1 && (r = "nearest"), n && Math.abs(n - g) < 1 && (i = "nearest");
			}
		}
		if (f) break;
		(t.top < e.top || t.bottom > e.bottom || t.left < e.left || t.right > e.right) && (t = {
			left: Math.max(t.left, e.left),
			right: Math.min(t.right, e.right),
			top: Math.max(t.top, e.top),
			bottom: Math.min(t.bottom, e.bottom)
		}), u = u.assignedSlot || u.parentNode;
	} else if (u.nodeType == 11) u = u.host;
	else break;
}
function zn(e, t = !0) {
	let n = e.ownerDocument, r = null, i = null;
	for (let a = e.parentNode; a && !(a == n.body || (!t || r) && i);) if (a.nodeType == 1) !i && a.scrollHeight > a.clientHeight && (i = a), t && !r && a.scrollWidth > a.clientWidth && (r = a), a = a.assignedSlot || a.parentNode;
	else if (a.nodeType == 11) a = a.host;
	else break;
	return {
		x: r,
		y: i
	};
}
var Bn = class {
	constructor() {
		this.anchorNode = null, this.anchorOffset = 0, this.focusNode = null, this.focusOffset = 0;
	}
	eq(e) {
		return this.anchorNode == e.anchorNode && this.anchorOffset == e.anchorOffset && this.focusNode == e.focusNode && this.focusOffset == e.focusOffset;
	}
	setRange(e) {
		let { anchorNode: t, focusNode: n } = e;
		this.set(t, Math.min(e.anchorOffset, t ? Pn(t) : 0), n, Math.min(e.focusOffset, n ? Pn(n) : 0));
	}
	set(e, t, n, r) {
		this.anchorNode = e, this.anchorOffset = t, this.focusNode = n, this.focusOffset = r;
	}
};
function Vn(e) {
	let t = [];
	for (let n = e; n; n = n.nodeType == 11 ? n.host : n.parentNode) n.nodeType == 1 && t.push({
		node: n,
		left: n.scrollLeft,
		top: n.scrollTop
	});
	return t;
}
function Hn(e, t = !0) {
	for (let { node: n, left: r, top: i } of e) t && n.scrollTop != i && (n.scrollTop = i), n.scrollLeft != r && (n.scrollLeft = r);
}
var Un = null;
P.safari && P.safari_version >= 26 && (Un = !1);
function Wn(e) {
	if (e.setActive) return e.setActive();
	if (Un) return e.focus(Un);
	let t = Vn(e);
	e.focus(Un == null ? { get preventScroll() {
		return Un = { preventScroll: !0 }, !0;
	} } : void 0), Un || (Un = !1, Hn(t));
}
var Gn;
function Kn(e, t, n = t) {
	let r = Gn ||= document.createRange();
	return r.setEnd(e, n), r.setStart(e, t), r;
}
function qn(e, t, n, r) {
	let i = {
		key: t,
		code: t,
		keyCode: n,
		which: n,
		cancelable: !0
	};
	r && ({altKey: i.altKey, ctrlKey: i.ctrlKey, shiftKey: i.shiftKey, metaKey: i.metaKey} = r);
	let a = new KeyboardEvent("keydown", i);
	a.synthetic = !0, e.dispatchEvent(a);
	let o = new KeyboardEvent("keyup", i);
	return o.synthetic = !0, e.dispatchEvent(o), a.defaultPrevented || o.defaultPrevented;
}
function Jn(e) {
	for (; e;) {
		if (e && (e.nodeType == 9 || e.nodeType == 11 && e.host)) return e;
		e = e.assignedSlot || e.parentNode;
	}
	return null;
}
function Yn(e, t) {
	let n = t.focusNode, r = t.focusOffset;
	if (!n || t.anchorNode != n || t.anchorOffset != r) return !1;
	for (r = Math.min(r, Pn(n));;) if (r) {
		if (n.nodeType != 1) return !1;
		let e = n.childNodes[r - 1];
		e.contentEditable == "false" ? r-- : (n = e, r = Pn(n));
	} else if (n == e) return !0;
	else r = jn(n), n = n.parentNode;
}
function Xn(e) {
	return e instanceof Window ? e.pageYOffset > Math.max(0, e.document.documentElement.scrollHeight - e.innerHeight - 4) : e.scrollTop > Math.max(1, e.scrollHeight - e.clientHeight - 4);
}
function Zn(e, t) {
	for (let n = e, r = t;;) if (n.nodeType == 3 && r > 0) return {
		node: n,
		offset: r
	};
	else if (n.nodeType == 1 && r > 0) {
		if (n.contentEditable == "false") return null;
		n = n.childNodes[r - 1], r = Pn(n);
	} else if (n.parentNode && !Mn(n)) r = jn(n), n = n.parentNode;
	else return null;
}
function Qn(e, t) {
	for (let n = e, r = t;;) if (n.nodeType == 3 && r < n.nodeValue.length) return {
		node: n,
		offset: r
	};
	else if (n.nodeType == 1 && r < n.childNodes.length) {
		if (n.contentEditable == "false") return null;
		n = n.childNodes[r], r = 0;
	} else if (n.parentNode && !Mn(n)) r = jn(n) + 1, n = n.parentNode;
	else return null;
}
var $n = class e {
	constructor(e, t, n = !0) {
		this.node = e, this.offset = t, this.precise = n;
	}
	static before(t, n) {
		return new e(t.parentNode, jn(t), n);
	}
	static after(t, n) {
		return new e(t.parentNode, jn(t) + 1, n);
	}
}, I = /*@__PURE__*/ (function(e) {
	return e[e.LTR = 0] = "LTR", e[e.RTL = 1] = "RTL", e;
})(I ||= {}), er = I.LTR, tr = I.RTL;
function nr(e) {
	let t = [];
	for (let n = 0; n < e.length; n++) t.push(1 << e[n]);
	return t;
}
var rr = /*@__PURE__*/ nr("88888888888888888888888888888888888666888888787833333333337888888000000000000000000000000008888880000000000000000000000000088888888888888888888888888888888888887866668888088888663380888308888800000000000000000000000800000000000000000000000000000008"), ir = /*@__PURE__*/ nr("4444448826627288999999999992222222222222222222222222222222222222222222222229999999999999999999994444444444644222822222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222999999949999999229989999223333333333"), ar = /*@__PURE__*/ Object.create(null), or = [];
for (let e of [
	"()",
	"[]",
	"{}"
]) {
	let t = /*@__PURE__*/ e.charCodeAt(0), n = /*@__PURE__*/ e.charCodeAt(1);
	ar[t] = n, ar[n] = -t;
}
function sr(e) {
	return e <= 247 ? rr[e] : 1424 <= e && e <= 1524 ? 2 : 1536 <= e && e <= 1785 ? ir[e - 1536] : 1774 <= e && e <= 2220 ? 4 : 8192 <= e && e <= 8204 ? 256 : 64336 <= e && e <= 65023 ? 4 : 1;
}
var cr = /[\u0590-\u05f4\u0600-\u06ff\u0700-\u08ac\ufb50-\ufdff]/, lr = class {
	get dir() {
		return this.level % 2 ? tr : er;
	}
	constructor(e, t, n) {
		this.from = e, this.to = t, this.level = n;
	}
	side(e, t) {
		return this.dir == t == e ? this.to : this.from;
	}
	forward(e, t) {
		return e == (this.dir == t);
	}
	static find(e, t, n, r) {
		let i = -1;
		for (let a = 0; a < e.length; a++) {
			let o = e[a];
			if (o.from <= t && o.to >= t) {
				if (o.level == n) return a;
				(i < 0 || (r == 0 ? e[i].level > o.level : r < 0 ? o.from < t : o.to > t)) && (i = a);
			}
		}
		if (i < 0) throw RangeError("Index out of range");
		return i;
	}
};
function ur(e, t) {
	if (e.length != t.length) return !1;
	for (let n = 0; n < e.length; n++) {
		let r = e[n], i = t[n];
		if (r.from != i.from || r.to != i.to || r.direction != i.direction || !ur(r.inner, i.inner)) return !1;
	}
	return !0;
}
var L = [];
function dr(e, t, n, r, i) {
	for (let a = 0; a <= r.length; a++) {
		let o = a ? r[a - 1].to : t, s = a < r.length ? r[a].from : n, c = a ? 256 : i;
		for (let t = o, n = c, r = c; t < s; t++) {
			let i = sr(e.charCodeAt(t));
			i == 512 ? i = n : i == 8 && r == 4 && (i = 16), L[t] = i == 4 ? 2 : i, i & 7 && (r = i), n = i;
		}
		for (let e = o, t = c, r = c; e < s; e++) {
			let i = L[e];
			if (i == 128) e < s - 1 && t == L[e + 1] && t & 24 ? i = L[e] = t : L[e] = 256;
			else if (i == 64) {
				let i = e + 1;
				for (; i < s && L[i] == 64;) i++;
				let a = e && t == 8 || i < n && L[i] == 8 ? r == 1 ? 1 : 8 : 256;
				for (let t = e; t < i; t++) L[t] = a;
				e = i - 1;
			} else i == 8 && r == 1 && (L[e] = 1);
			t = i, i & 7 && (r = i);
		}
	}
}
function fr(e, t, n, r, i) {
	let a = i == 1 ? 2 : 1;
	for (let o = 0, s = 0, c = 0; o <= r.length; o++) {
		let l = o ? r[o - 1].to : t, u = o < r.length ? r[o].from : n;
		for (let t = l, n, r, o; t < u; t++) if (r = ar[n = e.charCodeAt(t)]) {
			if (r < 0) {
				for (let e = s - 3; e >= 0; e -= 3) if (or[e + 1] == -r) {
					let n = or[e + 2], r = n & 2 ? i : n & 4 ? n & 1 ? a : i : 0;
					r && (L[t] = L[or[e]] = r), s = e;
					break;
				}
			} else if (or.length == 189) break;
			else or[s++] = t, or[s++] = n, or[s++] = c;
		} else if ((o = L[t]) == 2 || o == 1) {
			let e = o == i;
			c = +!e;
			for (let t = s - 3; t >= 0; t -= 3) {
				let n = or[t + 2];
				if (n & 2) break;
				if (e) or[t + 2] |= 2;
				else {
					if (n & 4) break;
					or[t + 2] |= 4;
				}
			}
		}
	}
}
function pr(e, t, n, r) {
	for (let i = 0, a = r; i <= n.length; i++) {
		let o = i ? n[i - 1].to : e, s = i < n.length ? n[i].from : t;
		for (let c = o; c < s;) {
			let o = L[c];
			if (o == 256) {
				let o = c + 1;
				for (;;) if (o == s) {
					if (i == n.length) break;
					o = n[i++].to, s = i < n.length ? n[i].from : t;
				} else if (L[o] == 256) o++;
				else break;
				let l = a == 1, u = l == ((o < t ? L[o] : r) == 1) ? l ? 1 : 2 : r;
				for (let t = o, r = i, a = r ? n[r - 1].to : e; t > c;) t == a && (t = n[--r].from, a = r ? n[r - 1].to : e), L[--t] = u;
				c = o;
			} else a = o, c++;
		}
	}
}
function mr(e, t, n, r, i, a, o) {
	let s = r % 2 ? 2 : 1;
	if (r % 2 == i % 2) for (let c = t, l = 0; c < n;) {
		let t = !0, u = !1;
		if (l == a.length || c < a[l].from) {
			let e = L[c];
			e != s && (t = !1, u = e == 16);
		}
		let d = !t && s == 1 ? [] : null, f = t ? r : r + 1, p = c;
		run: for (;;) if (l < a.length && p == a[l].from) {
			if (u) break run;
			let m = a[l];
			if (!t) for (let e = m.to, t = l + 1;;) {
				if (e == n) break run;
				if (t < a.length && a[t].from == e) e = a[t++].to;
				else if (L[e] == s) break run;
				else break;
			}
			l++, d ? d.push(m) : (m.from > c && o.push(new lr(c, m.from, f)), hr(e, m.direction == er == !(f % 2) ? r : r + 1, i, m.inner, m.from, m.to, o), c = m.to), p = m.to;
		} else if (p == n || (t ? L[p] != s : L[p] == s)) break;
		else p++;
		d ? mr(e, c, p, r + 1, i, d, o) : c < p && o.push(new lr(c, p, f)), c = p;
	}
	else for (let c = n, l = a.length; c > t;) {
		let n = !0, u = !1;
		if (!l || c > a[l - 1].to) {
			let e = L[c - 1];
			e != s && (n = !1, u = e == 16);
		}
		let d = !n && s == 1 ? [] : null, f = n ? r : r + 1, p = c;
		run: for (;;) if (l && p == a[l - 1].to) {
			if (u) break run;
			let m = a[--l];
			if (!n) for (let e = m.from, n = l;;) {
				if (e == t) break run;
				if (n && a[n - 1].to == e) e = a[--n].from;
				else if (L[e - 1] == s) break run;
				else break;
			}
			d ? d.push(m) : (m.to < c && o.push(new lr(m.to, c, f)), hr(e, m.direction == er == !(f % 2) ? r : r + 1, i, m.inner, m.from, m.to, o), c = m.from), p = m.from;
		} else if (p == t || (n ? L[p - 1] != s : L[p - 1] == s)) break;
		else p--;
		d ? mr(e, p, c, r + 1, i, d, o) : p < c && o.push(new lr(p, c, f)), c = p;
	}
}
function hr(e, t, n, r, i, a, o) {
	let s = t % 2 ? 2 : 1;
	dr(e, i, a, r, s), fr(e, i, a, r, s), pr(i, a, r, s), mr(e, i, a, t, n, r, o);
}
function gr(e, t, n) {
	if (!e) return [new lr(0, 0, +(t == tr))];
	if (t == er && !n.length && !cr.test(e)) return _r(e.length);
	if (n.length) for (; e.length > L.length;) L[L.length] = 256;
	let r = [], i = t == er ? 0 : 1;
	return hr(e, i, i, n, 0, e.length, r), r;
}
function _r(e) {
	return [new lr(0, e, 0)];
}
var vr = "";
function yr(e, t, n, r, i) {
	let a = r.head - e.from, o = lr.find(t, a, r.bidiLevel ?? -1, r.assoc), s = t[o], c = s.side(i, n);
	if (a == c) {
		let e = o += i ? 1 : -1;
		if (e < 0 || e >= t.length) return null;
		s = t[o = e], a = s.side(!i, n), c = s.side(i, n);
	}
	let l = D(e.text, a, s.forward(i, n));
	(l < s.from || l > s.to) && (l = c), vr = e.text.slice(Math.min(a, l), Math.max(a, l));
	let u = o == (i ? t.length - 1 : 0) ? null : t[o + (i ? 1 : -1)];
	return u && l == c && u.level + +!i < s.level ? k.cursor(u.side(!i, n) + e.from, u.forward(i, n) ? 1 : -1, u.level) : k.cursor(l + e.from, s.forward(i, n) ? -1 : 1, s.level);
}
function br(e, t, n) {
	for (let r = t; r < n; r++) {
		let t = sr(e.charCodeAt(r));
		if (t == 1) return er;
		if (t == 2 || t == 4) return tr;
	}
	return er;
}
var xr = /*@__PURE__*/ A.define(), Sr = /*@__PURE__*/ A.define(), Cr = /*@__PURE__*/ A.define(), wr = /*@__PURE__*/ A.define(), Tr = /*@__PURE__*/ A.define(), Er = /*@__PURE__*/ A.define(), Dr = /*@__PURE__*/ A.define(), Or = /*@__PURE__*/ A.define(), kr = /*@__PURE__*/ A.define(), Ar = /*@__PURE__*/ A.define({ combine: (e) => e.some((e) => e) }), jr = /*@__PURE__*/ A.define({ combine: (e) => e.some((e) => e) }), Mr = /*@__PURE__*/ A.define(), Nr = class e {
	constructor(e, t, n, r, i, a = !1) {
		this.range = e, this.y = t, this.x = n, this.yMargin = r, this.xMargin = i, this.isSnapshot = a;
	}
	map(t) {
		return t.empty ? this : new e(this.range.map(t), this.y, this.x, this.yMargin, this.xMargin, this.isSnapshot);
	}
	clip(t) {
		return this.range.to <= t.doc.length ? this : new e(k.cursor(t.doc.length), this.y, this.x, this.yMargin, this.xMargin, this.isSnapshot);
	}
}, Pr = /*@__PURE__*/ j.define({ map: (e, t) => e.map(t) }), Fr = /*@__PURE__*/ j.define();
function Ir(e, t, n) {
	let r = e.facet(wr);
	r.length ? r[0](t) : window.onerror && window.onerror(String(t), n, void 0, void 0, t) || (n ? console.error(n + ":", t) : console.error(t));
}
var Lr = /*@__PURE__*/ A.define({ combine: (e) => !e.length || e[0] }), Rr = 0, zr = /*@__PURE__*/ A.define({ combine(e) {
	return e.filter((t, n) => {
		for (let r = 0; r < n; r++) if (e[r].plugin == t.plugin) return !1;
		return !0;
	});
} }), Br = class e {
	constructor(e, t, n, r, i) {
		this.id = e, this.create = t, this.domEventHandlers = n, this.domEventObservers = r, this.baseExtensions = i(this), this.extension = this.baseExtensions.concat(zr.of({
			plugin: this,
			arg: void 0
		}));
	}
	of(e) {
		return this.baseExtensions.concat(zr.of({
			plugin: this,
			arg: e
		}));
	}
	static define(t, n) {
		let { eventHandlers: r, eventObservers: i, provide: a, decorations: o } = n || {};
		return new e(Rr++, t, r, i, (e) => {
			let t = [];
			return o && t.push(Wr.of((t) => {
				let n = t.plugin(e);
				return n ? o(n) : F.none;
			})), a && t.push(a(e)), t;
		});
	}
	static fromClass(t, n) {
		return e.define((e, n) => new t(e, n), n);
	}
}, Vr = class {
	constructor(e) {
		this.spec = e, this.mustUpdate = null, this.value = null;
	}
	get plugin() {
		return this.spec && this.spec.plugin;
	}
	update(e) {
		if (!this.value) {
			if (this.spec) try {
				this.value = this.spec.plugin.create(e, this.spec.arg);
			} catch (t) {
				Ir(e.state, t, "CodeMirror plugin crashed"), this.deactivate();
			}
		} else if (this.mustUpdate) {
			let e = this.mustUpdate;
			if (this.mustUpdate = null, this.value.update) try {
				this.value.update(e);
			} catch (t) {
				if (Ir(e.state, t, "CodeMirror plugin crashed"), this.value.destroy) try {
					this.value.destroy();
				} catch {}
				this.deactivate();
			}
		}
		return this;
	}
	destroy(e) {
		if (this.value?.destroy) try {
			this.value.destroy();
		} catch (t) {
			Ir(e.state, t, "CodeMirror plugin crashed");
		}
	}
	deactivate() {
		this.spec = this.value = null;
	}
}, Hr = /*@__PURE__*/ A.define(), Ur = /*@__PURE__*/ A.define(), Wr = /*@__PURE__*/ A.define(), Gr = /*@__PURE__*/ A.define(), Kr = /*@__PURE__*/ A.define(), qr = /*@__PURE__*/ A.define(), Jr = /*@__PURE__*/ A.define();
function Yr(e, t) {
	let n = e.state.facet(Jr);
	if (!n.length) return n;
	let r = n.map((t) => t instanceof Function ? t(e) : t), i = [];
	return N.spans(r, t.from, t.to, {
		point() {},
		span(e, n, r, a) {
			let o = e - t.from, s = n - t.from, c = i;
			for (let e = r.length - 1; e >= 0; e--, a--) {
				let n = r[e].spec.bidiIsolate, i;
				if (n ??= br(t.text, o, s), a > 0 && c.length && (i = c[c.length - 1]).to == o && i.direction == n) i.to = s, c = i.inner;
				else {
					let e = {
						from: o,
						to: s,
						direction: n,
						inner: []
					};
					c.push(e), c = e.inner;
				}
			}
		}
	}), i;
}
var Xr = /*@__PURE__*/ A.define();
function Zr(e) {
	let t = 0, n = 0, r = 0, i = 0;
	for (let a of e.state.facet(Xr)) {
		let o = a(e);
		o && (o.left != null && (t = Math.max(t, o.left)), o.right != null && (n = Math.max(n, o.right)), o.top != null && (r = Math.max(r, o.top)), o.bottom != null && (i = Math.max(i, o.bottom)));
	}
	return {
		left: t,
		right: n,
		top: r,
		bottom: i
	};
}
var Qr = /*@__PURE__*/ A.define(), $r = class e {
	constructor(e, t, n, r) {
		this.fromA = e, this.toA = t, this.fromB = n, this.toB = r;
	}
	join(t) {
		return new e(Math.min(this.fromA, t.fromA), Math.max(this.toA, t.toA), Math.min(this.fromB, t.fromB), Math.max(this.toB, t.toB));
	}
	addToSet(e) {
		let t = e.length, n = this;
		for (; t > 0; t--) {
			let r = e[t - 1];
			if (!(r.fromA > n.toA)) {
				if (r.toA < n.fromA) break;
				n = n.join(r), e.splice(t - 1, 1);
			}
		}
		return e.splice(t, 0, n), e;
	}
	static extendWithRanges(t, n) {
		if (n.length == 0) return t;
		let r = [];
		for (let i = 0, a = 0, o = 0;;) {
			let s = i < t.length ? t[i].fromB : 1e9, c = a < n.length ? n[a] : 1e9, l = Math.min(s, c);
			if (l == 1e9) break;
			let u = l + o, d = l, f = u;
			for (;;) if (a < n.length && n[a] <= d) {
				let e = n[a + 1];
				a += 2, d = Math.max(d, e);
				for (let e = i; e < t.length && t[e].fromB <= d; e++) o = t[e].toA - t[e].toB;
				f = Math.max(f, e + o);
			} else if (i < t.length && t[i].fromB <= d) {
				let e = t[i++];
				d = Math.max(d, e.toB), f = Math.max(f, e.toA), o = e.toA - e.toB;
			} else break;
			r.push(new e(u, f, l, d));
		}
		return r;
	}
}, ei = class e {
	constructor(e, t, n) {
		this.view = e, this.state = t, this.transactions = n, this.flags = 0, this.startState = e.state, this.changes = Se.empty(this.startState.doc.length);
		for (let e of n) this.changes = this.changes.compose(e.changes);
		let r = [];
		this.changes.iterChangedRanges((e, t, n, i) => r.push(new $r(e, t, n, i))), this.changedRanges = r;
	}
	static create(t, n, r) {
		return new e(t, n, r);
	}
	get viewportChanged() {
		return (this.flags & 4) > 0;
	}
	get viewportMoved() {
		return (this.flags & 8) > 0;
	}
	get heightChanged() {
		return (this.flags & 2) > 0;
	}
	get geometryChanged() {
		return this.docChanged || (this.flags & 18) > 0;
	}
	get focusChanged() {
		return (this.flags & 1) > 0;
	}
	get docChanged() {
		return !this.changes.empty;
	}
	get selectionSet() {
		return this.transactions.some((e) => e.selection);
	}
	get empty() {
		return this.flags == 0 && this.transactions.length == 0;
	}
}, ti = [], R = class {
	constructor(e, t, n = 0) {
		this.dom = e, this.length = t, this.flags = n, this.parent = null, e.cmTile = this;
	}
	get breakAfter() {
		return this.flags & 1;
	}
	get children() {
		return ti;
	}
	isWidget() {
		return !1;
	}
	get isHidden() {
		return !1;
	}
	isComposite() {
		return !1;
	}
	isLine() {
		return !1;
	}
	isText() {
		return !1;
	}
	isBlock() {
		return !1;
	}
	get domAttrs() {
		return null;
	}
	sync(e) {
		if (this.flags |= 2, this.flags & 4) {
			this.flags &= -5;
			let e = this.domAttrs;
			e && mn(this.dom, e);
		}
	}
	toString() {
		return this.constructor.name + (this.children.length ? `(${this.children})` : "") + (this.breakAfter ? "#" : "");
	}
	destroy() {
		this.parent = null;
	}
	setDOM(e) {
		this.dom = e, e.cmTile = this;
	}
	get posAtStart() {
		return this.parent ? this.parent.posBefore(this) : 0;
	}
	get posAtEnd() {
		return this.posAtStart + this.length;
	}
	posBefore(e, t = this.posAtStart) {
		let n = t;
		for (let t of this.children) {
			if (t == e) return n;
			n += t.length + t.breakAfter;
		}
		throw RangeError("Invalid child in posBefore");
	}
	posAfter(e) {
		return this.posBefore(e) + e.length;
	}
	covers(e) {
		return !0;
	}
	coordsIn(e, t, n) {
		return null;
	}
	domPosFor(e, t) {
		let n = jn(this.dom), r = this.length ? e > 0 : t > 0;
		return new $n(this.parent.dom, n + +!!r, e == 0 || e == this.length);
	}
	markDirty(e) {
		this.flags &= -3, e && (this.flags |= 4), this.parent && this.parent.flags & 2 && this.parent.markDirty(!1);
	}
	get overrideDOMText() {
		return null;
	}
	get root() {
		for (let e = this; e; e = e.parent) if (e instanceof ii) return e;
		return null;
	}
	static get(e) {
		return e.cmTile;
	}
}, ni = class extends R {
	constructor(e) {
		super(e, 0), this._children = [];
	}
	isComposite() {
		return !0;
	}
	get children() {
		return this._children;
	}
	get lastChild() {
		return this.children.length ? this.children[this.children.length - 1] : null;
	}
	append(e) {
		this.children.push(e), e.parent = this;
	}
	sync(e) {
		if (this.flags & 2) return;
		super.sync(e);
		let t = this.dom, n = null, r, i = e?.node == t ? e : null, a = 0;
		for (let o of this.children) {
			if (o.sync(e), a += o.length + o.breakAfter, r = n ? n.nextSibling : t.firstChild, i && r != o.dom && (i.written = !0), o.dom.parentNode == t) for (; r && r != o.dom;) r = ri(r);
			else t.insertBefore(o.dom, r);
			n = o.dom;
		}
		for (r = n ? n.nextSibling : t.firstChild, i && r && (i.written = !0); r;) r = ri(r);
		this.length = a;
	}
};
function ri(e) {
	let t = e.nextSibling;
	return e.parentNode.removeChild(e), t;
}
var ii = class extends ni {
	constructor(e, t) {
		super(t), this.view = e;
	}
	owns(e) {
		for (; e; e = e.parent) if (e == this) return !0;
		return !1;
	}
	isBlock() {
		return !0;
	}
	nearest(e) {
		for (;;) {
			if (!e) return null;
			let t = R.get(e);
			if (t && this.owns(t)) return t;
			e = e.parentNode;
		}
	}
	blockTiles(e) {
		for (let t = [], n = this, r = 0, i = 0;;) if (r == n.children.length) {
			if (!t.length) return;
			n = n.parent, n.breakAfter && i++, r = t.pop();
		} else {
			let a = n.children[r++];
			if (a instanceof ai) t.push(r), n = a, r = 0;
			else {
				let t = i + a.length, n = e(a, i);
				if (n !== void 0) return n;
				i = t + a.breakAfter;
			}
		}
	}
	resolveBlock(e, t) {
		let n, r = -1, i, a = -1;
		if (this.blockTiles((o, s) => {
			let c = s + o.length;
			if (e >= s && e <= c) {
				if (o.isWidget() && t >= -1 && t <= 1) {
					if (o.flags & 32) return !0;
					o.flags & 16 && (n = void 0);
				}
				(s < e || e == c && (t < -1 ? o.length : o.covers(1))) && (!n || !o.isWidget() && n.isWidget()) && (n = o, r = e - s), (c > e || e == s && (t > 1 ? o.length : o.covers(-1))) && (!i || !o.isWidget() && i.isWidget()) && (i = o, a = e - s);
			}
		}), !n && !i) throw Error("No tile at position " + e);
		return n && t < 0 || !i ? {
			tile: n,
			offset: r
		} : {
			tile: i,
			offset: a
		};
	}
}, ai = class e extends ni {
	constructor(e, t) {
		super(e), this.wrapper = t;
	}
	isBlock() {
		return !0;
	}
	covers(e) {
		return this.children.length ? e < 0 ? this.children[0].covers(-1) : this.lastChild.covers(1) : !1;
	}
	get domAttrs() {
		return this.wrapper.attributes;
	}
	static of(t, n) {
		let r = new e(n || document.createElement(t.tagName), t);
		return n || (r.flags |= 4), r;
	}
}, oi = class e extends ni {
	constructor(e, t) {
		super(e), this.attrs = t;
	}
	isLine() {
		return !0;
	}
	static start(t, n, r) {
		let i = new e(n || document.createElement("div"), t);
		return (!n || !r) && (i.flags |= 4), i;
	}
	get domAttrs() {
		return this.attrs;
	}
	resolveInline(e, t, n) {
		let r = null, i = -1, a = null, o = -1;
		function s(e, c) {
			for (let l = 0, u = 0; l < e.children.length && u <= c; l++) {
				let d = e.children[l], f = u + d.length;
				f >= c && (d.isComposite() ? s(d, c - u) : (!a || a.isHidden && (t > 0 && !(a.flags & 32) || n && ci(a, d))) && (f > c || d.flags & 32 && t <= 1) ? (a = d, o = c - u) : (u < c || d.flags & 16 && !d.isHidden && t >= -1) && (r = d, i = c - u)), u = f;
			}
		}
		s(this, e);
		let c = (t < 0 ? r : a) || r || a;
		return c ? {
			tile: c,
			offset: c == r ? i : o
		} : null;
	}
	coordsIn(e, t, n) {
		let r = this.resolveInline(e, t, !0);
		return r ? r.tile.coordsIn(Math.max(0, r.offset), t, n) : si(this);
	}
	domIn(e, t) {
		let n = this.resolveInline(e, t);
		if (n) {
			let { tile: e, offset: r } = n;
			if (this.dom.contains(e.dom)) return e.isText() ? new $n(e.dom, Math.min(e.dom.nodeValue.length, r)) : e.domPosFor(r, e.flags & 16 ? 1 : e.flags & 32 ? -1 : t);
			let i = n.tile.parent, a = !1;
			for (let e of i.children) {
				if (a) return new $n(e.dom, 0);
				e == n.tile && (a = !0);
			}
		}
		return new $n(this.dom, 0);
	}
};
function si(e) {
	let t = e.dom.lastChild;
	if (!t) return e.dom.getBoundingClientRect();
	let n = kn(t);
	return n[n.length - 1] || null;
}
function ci(e, t) {
	let n = e.coordsIn(0, 1), r = t.coordsIn(0, 1);
	return n && r && r.top < n.bottom;
}
var li = class e extends ni {
	constructor(e, t) {
		super(e), this.mark = t;
	}
	get domAttrs() {
		return this.mark.attrs;
	}
	static of(t, n) {
		let r = new e(n || document.createElement(t.tagName), t);
		return n || (r.flags |= 4), r;
	}
}, ui = class e extends R {
	constructor(e, t) {
		super(e, t.length), this.text = t;
	}
	sync(e) {
		this.flags & 2 || (super.sync(e), this.dom.nodeValue != this.text && (e && e.node == this.dom && (e.written = !0), this.dom.nodeValue = this.text));
	}
	isText() {
		return !0;
	}
	toString() {
		return JSON.stringify(this.text);
	}
	coordsIn(e, t, n) {
		let r = this.dom.nodeValue.length;
		e > r && (e = r);
		let i = e, a = e, o = 0;
		e == 0 && t < 0 || e == r && t >= 0 ? P.chrome || P.gecko || (e ? (i--, o = 1) : a < r && (a++, o = -1)) : t < 0 ? i-- : a < r && a++;
		let s = Kn(this.dom, i, a).getClientRects();
		if (!s.length) return null;
		let c = s[(o ? o < 0 : t >= 0) ? 0 : s.length - 1];
		return P.safari && !o && c.width == 0 && (c = Array.prototype.find.call(s, (e) => e.width) || c), n == null ? c : Fn(c, (o ? o > 0 : t < 0) == n);
	}
	static of(t, n) {
		let r = new e(n || document.createTextNode(t), t);
		return n || (r.flags |= 2), r;
	}
}, di = class e extends R {
	constructor(e, t, n, r) {
		super(e, t, r), this.widget = n;
	}
	isWidget() {
		return !0;
	}
	get isHidden() {
		return this.widget.isHidden;
	}
	covers(e) {
		return this.flags & 48 ? !1 : (this.flags & (e < 0 ? 64 : 128)) > 0;
	}
	coordsIn(e, t) {
		return this.coordsInWidget(e, t, !1);
	}
	coordsInWidget(e, t, n) {
		let r = this.widget.coordsAt(this.dom, e, t);
		if (r) return r;
		if (n) return Fn(this.dom.getBoundingClientRect(), this.length ? e == 0 : t <= 0);
		{
			let t = this.dom.getClientRects(), n = null;
			if (!t.length) return null;
			let r = this.flags & 16 ? !0 : this.flags & 32 ? !1 : e > 0;
			for (let i = r ? t.length - 1 : 0; n = t[i], !(e > 0 ? i == 0 : i == t.length - 1 || n.top < n.bottom); i += r ? -1 : 1);
			return Fn(n, !r);
		}
	}
	get overrideDOMText() {
		if (!this.length) return T.empty;
		let { root: e } = this;
		if (!e) return T.empty;
		let t = this.posAtStart;
		return e.view.state.doc.slice(t, t + this.length);
	}
	destroy() {
		super.destroy(), this.widget.destroy(this.dom);
	}
	static of(t, n, r, i, a) {
		return a || (a = t.toDOM(n), t.editable || (a.contentEditable = "false")), new e(a, r, t, i);
	}
}, fi = class extends R {
	constructor(e) {
		let t = document.createElement("img");
		t.className = "cm-widgetBuffer", t.setAttribute("aria-hidden", "true"), super(t, 0, e);
	}
	get isHidden() {
		return !0;
	}
	get overrideDOMText() {
		return T.empty;
	}
	coordsIn(e, t, n) {
		let r = this.dom.getBoundingClientRect();
		return n == null ? r : Fn(r, t > 0 == n);
	}
}, pi = class {
	constructor(e) {
		this.index = 0, this.beforeBreak = !1, this.parents = [], this.tile = e;
	}
	advance(e, t, n) {
		let { tile: r, index: i, beforeBreak: a, parents: o } = this;
		for (; e || t > 0;) if (!r.isComposite()) {
			let t = r.length;
			if (i < t && e) {
				let a = Math.min(e, t - i);
				n && n.skip(r, i, i + a), e -= a, i += a;
			}
			if (i == t) a = !!r.breakAfter, {tile: r, index: i} = o.pop(), i++;
			else if (!e) break;
		} else if (a) {
			if (!e) break;
			n && n.break(), e--, a = !1;
		} else if (i == r.children.length) {
			if (!e && !o.length) break;
			n && n.leave(r), a = !!r.breakAfter, {tile: r, index: i} = o.pop(), i++;
		} else {
			let s = r.children[i], c = s.breakAfter;
			(t > 0 ? s.length <= e : s.length < e) && (!n || n.skip(s, 0, s.length) !== !1 || !s.isComposite) ? (a = !!c, i++, e -= s.length) : (o.push({
				tile: r,
				index: i
			}), r = s, i = 0, n && s.isComposite() && n.enter(s));
		}
		return this.tile = r, this.index = i, this.beforeBreak = a, this;
	}
	get root() {
		return this.parents.length ? this.parents[0].tile : this.tile;
	}
}, mi = class {
	constructor(e, t, n, r) {
		this.from = e, this.to = t, this.wrapper = n, this.rank = r;
	}
}, hi = class {
	constructor(e, t, n) {
		this.cache = e, this.root = t, this.blockWrappers = n, this.curLine = null, this.lastBlock = null, this.afterWidget = null, this.pos = 0, this.wrappers = [], this.wrapperPos = 0;
	}
	addText(e, t, n, r) {
		this.flushBuffer();
		let i = this.ensureMarks(t, n), a = i.lastChild;
		if (a && a.isText() && !(a.flags & 8) && a.length + e.length < 512) {
			this.cache.reused.set(a, 2);
			let t = i.children[i.children.length - 1] = new ui(a.dom, a.text + e);
			t.parent = i;
		} else i.append(r || ui.of(e, this.cache.find(ui)?.dom));
		this.pos += e.length, this.afterWidget = null;
	}
	addComposition(e, t) {
		let n = this.curLine;
		n.dom != t.line.dom && (n.setDOM(this.cache.reused.has(t.line) ? Ti(t.line.dom) : t.line.dom), this.cache.reused.set(t.line, 2));
		let r = n;
		for (let e = t.marks.length - 1; e >= 0; e--) {
			let n = t.marks[e], i = r.lastChild;
			if (i instanceof li && i.mark.eq(n.mark)) i.dom != n.dom && i.setDOM(Ti(n.dom)), r = i;
			else {
				if (this.cache.reused.get(n)) {
					let e = R.get(n.dom);
					e && e.setDOM(Ti(n.dom));
				}
				let e = li.of(n.mark, n.dom);
				r.append(e), r = e;
			}
			this.cache.reused.set(n, 2);
		}
		let i = R.get(e.text);
		i && this.cache.reused.set(i, 2);
		let a = new ui(e.text, e.text.nodeValue);
		a.flags |= 8, this.pos = e.range.toB, r.append(a);
	}
	addInlineWidget(e, t, n) {
		let r = this.afterWidget && e.flags & 48 && (this.afterWidget.flags & 48) == (e.flags & 48);
		r || this.flushBuffer();
		let i = this.ensureMarks(t, n);
		!r && !(e.flags & 16) && i.append(this.getBuffer(1)), i.append(e), this.pos += e.length, this.afterWidget = e;
	}
	addMark(e, t, n) {
		this.flushBuffer(), this.ensureMarks(t, n).append(e), this.pos += e.length, this.afterWidget = null;
	}
	addBlockWidget(e) {
		this.getBlockPos().append(e), this.pos += e.length, this.lastBlock = e, this.endLine();
	}
	continueWidget(e) {
		let t = this.afterWidget || this.lastBlock;
		t.length += e, this.pos += e;
	}
	addLineStart(e, t) {
		e ||= Si;
		let n = oi.start(e, t || this.cache.find(oi)?.dom, !!t);
		this.getBlockPos().append(this.lastBlock = this.curLine = n);
	}
	addLine(e) {
		this.getBlockPos().append(e), this.pos += e.length, this.lastBlock = e, this.endLine();
	}
	addBreak() {
		this.lastBlock.flags |= 1, this.endLine(), this.pos++;
	}
	addLineStartIfNotCovered(e) {
		this.blockPosCovered() || this.addLineStart(e);
	}
	ensureLine(e) {
		this.curLine || this.addLineStart(e);
	}
	ensureMarks(e, t) {
		let n = this.curLine;
		for (let r = e.length - 1; r >= 0; r--) {
			let i = e[r], a;
			if (t > 0 && (a = n.lastChild) && a instanceof li && a.mark.eq(i)) n = a, t--;
			else {
				let e = li.of(i, this.cache.find(li, (e) => e.mark.eq(i))?.dom);
				n.append(e), n = e, t = 0;
			}
		}
		return n;
	}
	endLine() {
		if (this.curLine) {
			this.flushBuffer();
			let e = this.curLine.lastChild;
			(!e || !bi(this.curLine, !1) || e.dom.nodeName != "BR" && e.isWidget() && !(P.ios && bi(this.curLine, !0))) && this.curLine.append(this.cache.findWidget(Di, 0, 32) || new di(Di.toDOM(), 0, Di, 32)), this.curLine = this.afterWidget = null;
		}
	}
	updateBlockWrappers() {
		this.wrapperPos > this.pos + 1e4 && (this.blockWrappers.goto(this.pos), this.wrappers.length = 0);
		for (let e = this.wrappers.length - 1; e >= 0; e--) this.wrappers[e].to < this.pos && this.wrappers.splice(e, 1);
		for (let e = this.blockWrappers; e.value && e.from <= this.pos; e.next()) if (e.to >= this.pos) {
			let t = e.rank * 102 + e.value.rank, n = new mi(e.from, e.to, e.value, t), r = this.wrappers.length;
			for (; r > 0 && (this.wrappers[r - 1].rank - n.rank || this.wrappers[r - 1].to - n.to) < 0;) r--;
			this.wrappers.splice(r, 0, n);
		}
		this.wrapperPos = this.pos;
	}
	getBlockPos() {
		this.updateBlockWrappers();
		let e = this.root;
		for (let t of this.wrappers) {
			let n = e.lastChild;
			if (t.from < this.pos && n instanceof ai && n.wrapper.eq(t.wrapper)) e = n;
			else {
				let n = ai.of(t.wrapper, this.cache.find(ai, (e) => e.wrapper.eq(t.wrapper))?.dom);
				e.append(n), e = n;
			}
		}
		return e;
	}
	blockPosCovered() {
		let e = this.lastBlock;
		return e != null && !e.breakAfter && (!e.isWidget() || (e.flags & 160) > 0);
	}
	getBuffer(e) {
		let t = 2 | (e < 0 ? 16 : 32), n = this.cache.find(fi, void 0, 1);
		return n && (n.flags = t), n || new fi(t);
	}
	flushBuffer() {
		this.afterWidget && !(this.afterWidget.flags & 32) && (this.afterWidget.parent.append(this.getBuffer(-1)), this.afterWidget = null);
	}
}, gi = class {
	constructor(e) {
		this.skipCount = 0, this.text = "", this.textOff = 0, this.cursor = e.iter();
	}
	skip(e) {
		this.textOff + e <= this.text.length ? this.textOff += e : (this.skipCount += e - (this.text.length - this.textOff), this.text = "", this.textOff = 0);
	}
	next(e) {
		if (this.textOff == this.text.length) {
			let { value: t, lineBreak: n, done: r } = this.cursor.next(this.skipCount);
			if (this.skipCount = 0, r) throw Error("Ran out of text content when drawing inline views");
			this.text = t;
			let i = this.textOff = Math.min(e, t.length);
			return n ? null : t.slice(0, i);
		}
		let t = Math.min(this.text.length, this.textOff + e), n = this.text.slice(this.textOff, t);
		return this.textOff = t, n;
	}
}, _i = [
	di,
	oi,
	ui,
	li,
	fi,
	ai,
	ii
];
for (let e = 0; e < _i.length; e++) _i[e].bucket = e;
var vi = class {
	constructor(e) {
		this.view = e, this.buckets = _i.map(() => []), this.index = _i.map(() => 0), this.reused = /* @__PURE__ */ new Map();
	}
	add(e) {
		let t = e.constructor.bucket, n = this.buckets[t];
		n.length < 6 ? n.push(e) : n[this.index[t] = (this.index[t] + 1) % 6] = e;
	}
	find(e, t, n = 2) {
		let r = e.bucket, i = this.buckets[r], a = this.index[r];
		for (let e = 0; e < i.length; e++) {
			let o = (e + a) % i.length, s = i[o];
			if ((!t || t(s)) && !this.reused.has(s)) return i.splice(o, 1), o < a && this.index[r]--, this.reused.set(s, n), s;
		}
		return null;
	}
	findWidget(e, t, n) {
		let r = this.buckets[0];
		if (r.length) for (let i = 0, a = 0;; i++) {
			if (i == r.length) {
				if (a) return null;
				a = 1, i = 0;
			}
			let o = r[i];
			if (!this.reused.has(o) && (a == 0 ? o.widget.compare(e) : o.widget.constructor == e.constructor && e.updateDOM(o.dom, this.view, o.widget))) return r.splice(i, 1), i < this.index[0] && this.index[0]--, o.widget == e && o.length == t && (o.flags & 497) == n ? (this.reused.set(o, 1), o) : (this.reused.set(o, 2), new di(o.dom, t, e, o.flags & -498 | n));
		}
	}
	reuse(e) {
		return this.reused.set(e, 1), e;
	}
	maybeReuse(e, t = 2) {
		if (!this.reused.has(e)) return this.reused.set(e, t), e.dom;
	}
	clear() {
		for (let e = 0; e < this.buckets.length; e++) this.buckets[e].length = this.index[e] = 0;
	}
}, yi = class {
	constructor(e, t, n, r, i) {
		this.view = e, this.decorations = r, this.disallowBlockEffectsFor = i, this.openWidget = !1, this.openMarks = 0, this.cache = new vi(e), this.text = new gi(e.state.doc), this.builder = new hi(this.cache, new ii(e, e.contentDOM), N.iter(n)), this.cache.reused.set(t, 2), this.old = new pi(t), this.reuseWalker = {
			skip: (e, t, n) => {
				if (this.cache.add(e), e.isComposite()) return !1;
			},
			enter: (e) => this.cache.add(e),
			leave: () => {},
			break: () => {}
		};
	}
	run(e, t) {
		let n = t && this.getCompositionContext(t.text);
		for (let r = 0, i = 0, a = 0;;) {
			let o = a < e.length ? e[a++] : null, s = o ? o.fromA : this.old.root.length;
			if (s > r) {
				let e = s - r;
				this.preserve(e, !a, !o), r = s, i += e;
			}
			if (!o) break;
			t && o.fromA <= t.range.fromA && o.toA >= t.range.toA ? (this.forward(o.fromA, t.range.fromA, t.range.fromA < t.range.toA ? 1 : -1), this.emit(i, t.range.fromB), this.builder.flushBuffer(), this.cache.clear(), this.builder.addComposition(t, n), this.text.skip(t.range.toB - t.range.fromB), this.forward(t.range.fromA, o.toA), this.emit(t.range.toB, o.toB)) : (this.forward(o.fromA, o.toA), this.emit(i, o.toB)), i = o.toB, r = o.toA;
		}
		return this.builder.curLine && this.builder.endLine(), this.builder.root;
	}
	preserve(e, t, n) {
		let r = wi(this.old), i = this.openMarks;
		this.old.advance(e, n ? 1 : -1, {
			skip: (e, t, n) => {
				if (e.isWidget()) {
					if (this.openWidget) this.builder.continueWidget(n - t);
					else {
						let a = n > 0 || t < e.length ? di.of(e.widget, this.view, n - t, e.flags & 496, this.cache.maybeReuse(e)) : this.cache.reuse(e);
						a.flags & 256 ? (a.flags &= -2, this.builder.addBlockWidget(a)) : (this.builder.ensureLine(null), this.builder.addInlineWidget(a, r, i), i = r.length);
					}
				} else if (e.isText()) this.builder.ensureLine(null), !t && n == e.length && !this.cache.reused.has(e) ? this.builder.addText(e.text, r, i, this.cache.reuse(e)) : (this.cache.add(e), this.builder.addText(e.text.slice(t, n), r, i)), i = r.length;
				else if (e.isLine()) e.flags &= -2, this.cache.reused.set(e, 1), this.builder.addLine(e);
				else if (e instanceof fi) this.cache.add(e);
				else if (e instanceof li) this.builder.ensureLine(null), this.builder.addMark(e, r, i), this.cache.reused.set(e, 1), i = r.length;
				else return !1;
				this.openWidget = !1;
			},
			enter: (e) => {
				e.isLine() ? this.builder.addLineStart(e.attrs, this.cache.maybeReuse(e)) : (this.cache.add(e), e instanceof li && r.unshift(e.mark)), this.openWidget = !1;
			},
			leave: (e) => {
				e.isLine() ? r.length &&= i = 0 : e instanceof li && (r.shift(), i = Math.min(i, r.length));
			},
			break: () => {
				this.builder.addBreak(), this.openWidget = !1;
			}
		}), this.text.skip(e);
	}
	emit(e, t) {
		let n = null, r = this.builder, i = -1, a = N.spans(this.decorations, e, t, {
			point: (e, t, a, o, s, c) => {
				if (a instanceof xn) {
					if (this.disallowBlockEffectsFor[c]) {
						if (a.block) throw RangeError("Block decorations may not be specified via plugins");
						if (t > this.view.state.doc.lineAt(e).to) throw RangeError("Decorations that replace line breaks may not be specified via plugins");
					}
					if (i = o.length, s > o.length) r.continueWidget(t - e);
					else {
						let i = a.widget || (a.block ? Ei.block : Ei.inline), c = xi(a), l = this.cache.findWidget(i, t - e, c) || di.of(i, this.view, t - e, c);
						a.block ? (a.startSide > 0 && r.addLineStartIfNotCovered(n), r.addBlockWidget(l)) : (r.ensureLine(n), r.addInlineWidget(l, o, s));
					}
					n = null;
				} else n = Ci(n, a);
				t > e && this.text.skip(t - e);
			},
			span: (e, t, a, o) => {
				for (let i = e; i < t;) {
					let s = this.text.next(Math.min(512, t - i));
					s == null ? (r.addLineStartIfNotCovered(n), r.addBreak(), i++) : (r.ensureLine(n), r.addText(s, a, i == e ? o : a.length), i += s.length), n = null;
				}
				i = a.length;
			}
		});
		i > -1 && (this.openWidget = a > i), this.openWidget || r.addLineStartIfNotCovered(n), this.openMarks = a;
	}
	forward(e, t, n = 1) {
		t - e <= 10 ? this.old.advance(t - e, n, this.reuseWalker) : (this.old.advance(5, -1, this.reuseWalker), this.old.advance(t - e - 10, -1), this.old.advance(5, n, this.reuseWalker));
	}
	getCompositionContext(e) {
		let t = [], n = null;
		for (let r = e.parentNode;; r = r.parentNode) {
			let e = R.get(r);
			if (r == this.view.contentDOM) break;
			e instanceof li ? t.push(e) : e?.isLine() ? n = e : e instanceof ai || (r.nodeName == "DIV" && !n && r != this.view.contentDOM ? n = new oi(r, Si) : n || t.push(li.of(new yn({
				tagName: r.nodeName.toLowerCase(),
				attributes: gn(r)
			}), r)));
		}
		return {
			line: n,
			marks: t
		};
	}
};
function bi(e, t) {
	let n = (e) => {
		for (let r of e.children) if ((t ? r.isText() : r.length) || n(r)) return !0;
		return !1;
	};
	return n(e);
}
function xi(e) {
	let t = e.isReplace ? (e.startSide < 0 ? 64 : 0) | (e.endSide > 0 ? 128 : 0) : e.startSide > 0 ? 32 : 16;
	return e.block && (t |= 256), t;
}
var Si = { class: "cm-line" };
function Ci(e, t) {
	let n = t.spec.attributes, r = t.spec.class;
	return !n && !r ? e : (e ||= { class: "cm-line" }, n && dn(n, e), r && (e.class += " " + r), e);
}
function wi(e) {
	let t = [];
	for (let n = e.parents.length; n > 1; n--) {
		let r = n == e.parents.length ? e.tile : e.parents[n].tile;
		r instanceof li && t.push(r.mark);
	}
	return t;
}
function Ti(e) {
	let t = R.get(e);
	return t && t.setDOM(e.cloneNode()), e;
}
var Ei = class extends _n {
	constructor(e) {
		super(), this.tag = e;
	}
	eq(e) {
		return e.tag == this.tag;
	}
	toDOM() {
		return document.createElement(this.tag);
	}
	updateDOM(e) {
		return e.nodeName.toLowerCase() == this.tag;
	}
	get isHidden() {
		return !0;
	}
};
Ei.inline = /*@__PURE__*/ new Ei("span"), Ei.block = /*@__PURE__*/ new Ei("div");
var Di = /*@__PURE__*/ new class extends _n {
	toDOM() {
		return document.createElement("br");
	}
	get isHidden() {
		return !0;
	}
	get editable() {
		return !0;
	}
}(), Oi = class {
	constructor(e) {
		this.view = e, this.decorations = [], this.blockWrappers = [], this.dynamicDecorationMap = [!1], this.domChanged = null, this.hasComposition = null, this.editContextFormatting = F.none, this.lastCompositionAfterCursor = !1, this.minWidth = 0, this.minWidthFrom = 0, this.minWidthTo = 0, this.impreciseAnchor = null, this.impreciseHead = null, this.forceSelection = !1, this.lastUpdate = Date.now(), this.updateDeco(), this.tile = new ii(e, e.contentDOM), this.updateInner([new $r(0, 0, 0, e.state.doc.length)], null);
	}
	update(e) {
		let t = e.changedRanges;
		this.minWidth > 0 && t.length && (t.every(({ fromA: e, toA: t }) => t < this.minWidthFrom || e > this.minWidthTo) ? (this.minWidthFrom = e.changes.mapPos(this.minWidthFrom, 1), this.minWidthTo = e.changes.mapPos(this.minWidthTo, 1)) : this.minWidth = this.minWidthFrom = this.minWidthTo = 0), this.updateEditContextFormatting(e);
		let n = -1;
		this.view.inputState.composing >= 0 && !this.view.observer.editContext && (this.domChanged?.newSel ? n = this.domChanged.newSel.head : !zi(e.changes, this.hasComposition) && !e.selectionSet && (n = e.state.selection.main.head));
		let r = n > -1 ? Mi(this.view, e.changes, n) : null;
		if (this.domChanged = null, this.hasComposition) {
			let { from: n, to: r } = this.hasComposition;
			t = new $r(n, r, e.changes.mapPos(n, -1), e.changes.mapPos(r, 1)).addToSet(t.slice());
		}
		this.hasComposition = r ? {
			from: r.range.fromB,
			to: r.range.toB
		} : null, (P.ie || P.chrome) && !r && e && e.state.doc.lines != e.startState.doc.lines && (this.forceSelection = !0);
		let i = this.decorations, a = this.blockWrappers;
		this.updateDeco();
		let o = Fi(i, this.decorations, e.changes);
		o.length && (t = $r.extendWithRanges(t, o));
		let s = Li(a, this.blockWrappers, e.changes);
		return s.length && (t = $r.extendWithRanges(t, s)), r && !t.some((e) => e.fromA <= r.range.fromA && e.toA >= r.range.toA) && (t = r.range.addToSet(t.slice())), this.tile.flags & 2 && t.length == 0 ? !1 : (this.updateInner(t, r), e.transactions.length && (this.lastUpdate = Date.now()), !0);
	}
	updateInner(e, t) {
		this.view.viewState.mustMeasureContent = !0;
		let { observer: n } = this.view;
		n.ignore(() => {
			if (t || e.length) {
				let n = this.tile, r = new yi(this.view, n, this.blockWrappers, this.decorations, this.dynamicDecorationMap);
				t && R.get(t.text) && r.cache.reused.set(R.get(t.text), 2), this.tile = r.run(e, t), ki(n, r.cache.reused);
			}
			this.tile.dom.style.height = this.view.viewState.contentHeight / this.view.scaleY + "px", this.tile.dom.style.flexBasis = this.minWidth ? this.minWidth + "px" : "";
			let r = P.chrome || P.ios ? {
				node: n.selectionRange.focusNode,
				written: !1
			} : void 0;
			this.tile.sync(r), r && (r.written || n.selectionRange.focusNode != r.node || !this.tile.dom.contains(r.node)) && (this.forceSelection = !0), this.tile.dom.style.height = "";
		});
		let r = [];
		if (this.view.viewport.from || this.view.viewport.to < this.view.state.doc.length) for (let e of this.tile.children) e.isWidget() && e.widget instanceof Bi && r.push(e.dom);
		n.updateGaps(r);
	}
	updateEditContextFormatting(e) {
		this.editContextFormatting = this.editContextFormatting.map(e.changes);
		for (let t of e.transactions) for (let e of t.effects) e.is(Fr) && (this.editContextFormatting = e.value);
	}
	updateSelection(e = !1, t = !1) {
		(e || !this.view.observer.selectionRange.focusNode) && this.view.observer.readSelectionRange();
		let { dom: n } = this.tile, r = this.view.root.activeElement, i = r == n, a = !i && !(this.view.state.facet(Lr) || n.tabIndex > -1) && On(n, this.view.observer.selectionRange) && !(r && n.contains(r));
		if (!(i || t || a)) return;
		let o = this.forceSelection;
		this.forceSelection = !1;
		let s = this.view.state.selection.main, c, l;
		if (s.empty ? l = c = this.inlineDOMNearPos(s.anchor, s.assoc || 1) : (l = this.inlineDOMNearPos(s.head, s.head == s.from ? 1 : -1), c = this.inlineDOMNearPos(s.anchor, s.anchor == s.from ? 1 : -1)), P.gecko && s.empty && !this.hasComposition && Ai(c)) {
			let e = document.createTextNode("");
			this.view.observer.ignore(() => c.node.insertBefore(e, c.node.childNodes[c.offset] || null)), c = l = new $n(e, 0), o = !0;
		}
		let u = this.view.observer.selectionRange;
		(o || !u.focusNode || (!An(c.node, c.offset, u.anchorNode, u.anchorOffset) || !An(l.node, l.offset, u.focusNode, u.focusOffset)) && !this.suppressWidgetCursorChange(u, s)) && (this.view.observer.ignore(() => {
			P.android && P.chrome && n.contains(u.focusNode) && Ri(u.focusNode, n) && (n.blur(), n.focus({ preventScroll: !0 }));
			let e = En(this.view.root);
			if (e) {
				if (s.empty) {
					if (P.gecko) {
						let e = Ni(c.node, c.offset);
						if (e && e != 3) {
							let t = (e == 1 ? Zn : Qn)(c.node, c.offset);
							t && (c = new $n(t.node, t.offset));
						}
					}
					e.collapse(c.node, c.offset), s.bidiLevel != null && e.caretBidiLevel !== void 0 && (e.caretBidiLevel = s.bidiLevel);
				} else if (e.extend) {
					e.collapse(c.node, c.offset);
					try {
						e.extend(l.node, l.offset);
					} catch {}
				} else {
					let t = document.createRange();
					s.anchor > s.head && ([c, l] = [l, c]), t.setEnd(l.node, l.offset), t.setStart(c.node, c.offset), e.removeAllRanges(), e.addRange(t);
				}
			}
			a && this.view.root.activeElement == n && (n.blur(), r && r.focus());
		}), this.view.observer.setSelectionRange(c, l)), this.impreciseAnchor = c.precise ? null : new $n(u.anchorNode, u.anchorOffset), this.impreciseHead = l.precise ? null : new $n(u.focusNode, u.focusOffset);
	}
	suppressWidgetCursorChange(e, t) {
		return this.hasComposition && t.empty && An(e.focusNode, e.focusOffset, e.anchorNode, e.anchorOffset) && this.posFromDOM(e.focusNode, e.focusOffset) == t.head;
	}
	enforceCursorAssoc() {
		if (this.hasComposition) return;
		let { view: e } = this, t = e.state.selection.main, n = En(e.root), { anchorNode: r, anchorOffset: i } = e.observer.selectionRange;
		if (!n || !t.empty || !t.assoc || !n.modify) return;
		let a = this.lineAt(t.head, t.assoc);
		if (!a) return;
		let o = a.posAtStart;
		if (t.head == o || t.head == o + a.length) return;
		let s = this.coordsAt(t.head, -1), c = this.coordsAt(t.head, 1);
		if (!s || !c || s.bottom > c.top) return;
		let l = this.domAtPos(t.head + t.assoc, t.assoc);
		n.collapse(l.node, l.offset), n.modify("move", t.assoc < 0 ? "forward" : "backward", "lineboundary"), e.observer.readSelectionRange();
		let u = e.observer.selectionRange;
		e.docView.posFromDOM(u.anchorNode, u.anchorOffset) != t.from && n.collapse(r, i);
	}
	posFromDOM(e, t) {
		let n = this.tile.nearest(e);
		if (!n) return this.tile.dom.compareDocumentPosition(e) & 2 ? 0 : this.view.state.doc.length;
		let r = n.posAtStart;
		if (n.isComposite()) {
			let i;
			if (e == n.dom) i = n.dom.childNodes[t];
			else {
				let r = Pn(e) == 0 ? 0 : t == 0 ? -1 : 1;
				for (;;) {
					let t = e.parentNode;
					if (t == n.dom) break;
					r == 0 && t.firstChild != t.lastChild && (r = e == t.firstChild ? -1 : 1), e = t;
				}
				i = r < 0 ? e : e.nextSibling;
			}
			if (i == n.dom.firstChild) return r;
			for (; i && !R.get(i);) i = i.nextSibling;
			if (!i) return r + n.length;
			for (let e = 0, t = r;; e++) {
				let r = n.children[e];
				if (r.dom == i) return t;
				t += r.length + r.breakAfter;
			}
		} else if (n.isText()) return e == n.dom ? r + t : r + (t ? n.length : 0);
		else return r;
	}
	domAtPos(e, t) {
		let { tile: n, offset: r } = this.tile.resolveBlock(e, t);
		return n.isWidget() ? n.domPosFor(r, t) : n.domIn(r, t);
	}
	inlineDOMNearPos(e, t) {
		let n, r = -1, i = !1, a, o = -1, s = !1;
		return this.tile.blockTiles((t, c) => {
			if (t.isWidget()) {
				if (t.flags & 32 && c >= e) return !0;
				t.flags & 16 && (i = !0);
			} else {
				let l = c + t.length;
				if (c <= e && (n = t, r = e - c, i = l < e), l >= e && !a && (a = t, o = e - c, s = c > e), c > e && a) return !0;
			}
		}), !n && !a ? this.domAtPos(e, t) : (i && a ? n = null : s && n && (a = null), n && t < 0 || !a ? n.domIn(r, t) : a.domIn(o, t));
	}
	coordsAt(e, t, n) {
		let { tile: r, offset: i } = this.tile.resolveBlock(e, t);
		return r.isWidget() ? r.widget instanceof Bi ? null : r.coordsInWidget(i, t, !0) : r.coordsIn(i, t, n);
	}
	lineAt(e, t) {
		let { tile: n } = this.tile.resolveBlock(e, t);
		return n.isLine() ? n : null;
	}
	coordsForChar(e) {
		let { tile: t, offset: n } = this.tile.resolveBlock(e, 1);
		if (!t.isLine()) return null;
		function r(e, t) {
			if (e.isComposite()) for (let n of e.children) {
				if (n.length >= t) {
					let e = r(n, t);
					if (e) return e;
				}
				if (t -= n.length, t < 0) break;
			}
			else if (e.isText() && t < e.length) {
				let n = D(e.text, t);
				if (n == t) return null;
				let r = Kn(e.dom, t, n).getClientRects();
				for (let e = 0; e < r.length; e++) {
					let t = r[e];
					if (e == r.length - 1 || t.top < t.bottom && t.left < t.right) return t;
				}
			}
			return null;
		}
		return r(t, n);
	}
	measureVisibleLineHeights(e) {
		let t = [], { from: n, to: r } = e, i = this.view.contentDOM.clientWidth, a = i > Math.max(this.view.scrollDOM.clientWidth, this.minWidth) + 1, o = -1, s = this.view.textDirection == I.LTR, c = 0, l = (e, u, d) => {
			for (let f = 0; f < e.children.length && !(u > r); f++) {
				let r = e.children[f], p = u + r.length, m = r.dom.getBoundingClientRect(), { height: h } = m;
				if (d && !f && (c += m.top - d.top), r instanceof ai) p > n && l(r, u, m);
				else if (u >= n && (c > 0 && t.push(-c), t.push(h + c), c = 0, a)) {
					let e = r.dom.lastChild, t = e ? kn(e) : [];
					if (t.length) {
						let e = t[t.length - 1], n = s ? e.right - m.left : m.right - e.left;
						n > o && (o = n, this.minWidth = i, this.minWidthFrom = u, this.minWidthTo = p);
					}
				}
				d && f == e.children.length - 1 && (c += d.bottom - m.bottom), u = p + r.breakAfter;
			}
		};
		return l(this.tile, 0, null), t;
	}
	textDirectionAt(e) {
		let { tile: t } = this.tile.resolveBlock(e, 1);
		return getComputedStyle(t.dom).direction == "rtl" ? I.RTL : I.LTR;
	}
	measureTextSize() {
		let e = this.tile.blockTiles((e) => {
			if (e.isLine() && e.children.length && e.length <= 20) {
				let t = 0, n;
				for (let r of e.children) {
					if (!r.isText() || /[^ -~]/.test(r.text)) return;
					let e = kn(r.dom);
					if (e.length != 1) return;
					t += e[0].width, n = e[0].height;
				}
				if (t) return {
					lineHeight: e.dom.getBoundingClientRect().height,
					charWidth: t / e.length,
					textHeight: n
				};
			}
		});
		if (e) return e;
		let t = document.createElement("div"), n, r, i;
		return t.className = "cm-line", t.style.width = "99999px", t.style.position = "absolute", t.textContent = "abc def ghi jkl mno pqr stu", this.view.observer.ignore(() => {
			this.tile.dom.appendChild(t);
			let e = kn(t.firstChild)[0];
			n = t.getBoundingClientRect().height, r = e && e.width ? e.width / 27 : 7, i = e && e.height ? e.height : n, t.remove();
		}), {
			lineHeight: n,
			charWidth: r,
			textHeight: i
		};
	}
	computeBlockGapDeco() {
		let e = [], t = this.view.viewState;
		for (let n = 0, r = 0;; r++) {
			let i = r == t.viewports.length ? null : t.viewports[r], a = i ? i.from - 1 : this.view.state.doc.length;
			if (a > n) {
				let r = (t.lineBlockAt(a).bottom - t.lineBlockAt(n).top) / this.view.scaleY;
				e.push(F.replace({
					widget: new Bi(r),
					block: !0,
					inclusive: !0,
					isBlockGap: !0
				}).range(n, a));
			}
			if (!i) break;
			n = i.to + 1;
		}
		return F.set(e);
	}
	updateDeco() {
		let e = 1, t = this.view.state.facet(Wr).map((t) => (this.dynamicDecorationMap[e++] = typeof t == "function") ? t(this.view) : t), n = !1, r = this.view.state.facet(Kr).map((e, t) => {
			let r = typeof e == "function";
			return r && (n = !0), r ? e(this.view) : e;
		});
		for (r.length && (this.dynamicDecorationMap[e++] = n, t.push(N.join(r))), this.decorations = [
			this.editContextFormatting,
			...t,
			this.computeBlockGapDeco(),
			this.view.viewState.lineGapDeco
		]; e < this.decorations.length;) this.dynamicDecorationMap[e++] = !1;
		this.blockWrappers = this.view.state.facet(Gr).map((e) => typeof e == "function" ? e(this.view) : e);
	}
	scrollIntoView(e) {
		if (e.isSnapshot) {
			let t = this.view.viewState.lineBlockAt(e.range.head);
			this.view.scrollDOM.scrollTop = t.top - e.yMargin, this.view.scrollDOM.scrollLeft = e.xMargin;
			return;
		}
		for (let t of this.view.state.facet(Mr)) try {
			if (t(this.view, e.range, e)) return !0;
		} catch (e) {
			Ir(this.view.state, e, "scroll handler");
		}
		let { range: t } = e, n = this.coordsAt(t.head, t.assoc || (t.head > t.anchor ? -1 : 1)), r;
		if (!n) return;
		!t.empty && (r = this.coordsAt(t.anchor, t.anchor > t.head ? -1 : 1)) && (n = {
			left: Math.min(n.left, r.left),
			top: Math.min(n.top, r.top),
			right: Math.max(n.right, r.right),
			bottom: Math.max(n.bottom, r.bottom)
		});
		let i = Zr(this.view), a = {
			left: n.left - i.left,
			top: n.top - i.top,
			right: n.right + i.right,
			bottom: n.bottom + i.bottom
		}, { offsetWidth: o, offsetHeight: s } = this.view.scrollDOM;
		if (Rn(this.view.scrollDOM, a, t.head < t.anchor ? -1 : 1, e.x, e.y, Math.max(Math.min(e.xMargin, o), -o), Math.max(Math.min(e.yMargin, s), -s), this.view.textDirection == I.LTR), window.visualViewport && window.innerHeight - window.visualViewport.height > 1 && (n.top > window.visualViewport.offsetTop + window.visualViewport.height || n.bottom < window.visualViewport.offsetTop)) {
			let e = this.view.docView.lineAt(t.head, 1);
			if (e) {
				let t = Vn(e.dom);
				e.dom.scrollIntoView({ block: "nearest" }), Hn(t, !1);
			}
		}
	}
	lineHasWidget(e) {
		let t = (e) => e.isWidget() || e.children.some(t);
		return t(this.tile.resolveBlock(e, 1).tile);
	}
	destroy() {
		ki(this.tile);
	}
};
function ki(e, t) {
	let n = t?.get(e);
	if (n != 1) {
		n ?? e.destroy();
		for (let n of e.children) ki(n, t);
	}
}
function Ai(e) {
	return e.node.nodeType == 1 && e.node.firstChild && (e.offset == 0 || e.node.childNodes[e.offset - 1].contentEditable == "false") && (e.offset == e.node.childNodes.length || e.node.childNodes[e.offset].contentEditable == "false");
}
function ji(e, t) {
	let n = e.observer.selectionRange;
	if (!n.focusNode) return null;
	let r = Zn(n.focusNode, n.focusOffset), i = Qn(n.focusNode, n.focusOffset), a = r || i;
	if (i && r && i.node != r.node) {
		let t = R.get(i.node);
		if (!t || t.isText() && t.text != i.node.nodeValue) a = i;
		else if (e.docView.lastCompositionAfterCursor) {
			let e = R.get(r.node);
			!e || e.isText() && e.text != r.node.nodeValue || (a = i);
		}
	}
	if (e.docView.lastCompositionAfterCursor = a != r, !a) return null;
	let o = t - a.offset;
	return {
		from: o,
		to: o + a.node.nodeValue.length,
		node: a.node
	};
}
function Mi(e, t, n) {
	let r = ji(e, n);
	if (!r) return null;
	let { node: i, from: a, to: o } = r, s = i.nodeValue;
	if (/[\n\r]/.test(s) || e.state.doc.sliceString(r.from, r.to) != s) return null;
	let c = t.invertedDesc;
	return {
		range: new $r(c.mapPos(a), c.mapPos(o), a, o),
		text: i
	};
}
function Ni(e, t) {
	return e.nodeType == 1 ? (t && e.childNodes[t - 1].contentEditable == "false" ? 1 : 0) | (t < e.childNodes.length && e.childNodes[t].contentEditable == "false" ? 2 : 0) : 0;
}
var Pi = class {
	constructor() {
		this.changes = [];
	}
	compareRange(e, t) {
		wn(e, t, this.changes);
	}
	comparePoint(e, t) {
		wn(e, t, this.changes);
	}
	boundChange(e) {
		wn(e, e, this.changes);
	}
};
function Fi(e, t, n) {
	let r = new Pi();
	return N.compare(e, t, n, r), r.changes;
}
var Ii = class {
	constructor() {
		this.changes = [];
	}
	compareRange(e, t) {
		wn(e, t, this.changes);
	}
	comparePoint() {}
	boundChange(e) {
		wn(e, e, this.changes);
	}
};
function Li(e, t, n) {
	let r = new Ii();
	return N.compare(e, t, n, r), r.changes;
}
function Ri(e, t) {
	for (let n = e; n && n != t; n = n.assignedSlot || n.parentNode) if (n.nodeType == 1 && n.contentEditable == "false") return !0;
	return !1;
}
function zi(e, t) {
	let n = !1;
	return t && e.iterChangedRanges((e, r) => {
		e < t.to && r > t.from && (n = !0);
	}), n;
}
var Bi = class extends _n {
	constructor(e) {
		super(), this.height = e;
	}
	toDOM() {
		let e = document.createElement("div");
		return e.className = "cm-gap", this.updateDOM(e), e;
	}
	eq(e) {
		return e.height == this.height;
	}
	updateDOM(e) {
		return e.style.height = this.height + "px", !0;
	}
	get editable() {
		return !0;
	}
	get estimatedHeight() {
		return this.height;
	}
	ignoreEvent() {
		return !1;
	}
};
function Vi(e, t, n = 1) {
	let r = e.charCategorizer(t), i = e.doc.lineAt(t), a = t - i.from;
	if (i.length == 0) return k.cursor(t);
	a == 0 ? n = 1 : a == i.length && (n = -1);
	let o = a, s = a;
	n < 0 ? o = D(i.text, a, !1) : s = D(i.text, a);
	let c = r(i.text.slice(o, s));
	for (; o > 0;) {
		let e = D(i.text, o, !1);
		if (r(i.text.slice(e, o)) != c) break;
		o = e;
	}
	for (; s < i.length;) {
		let e = D(i.text, s);
		if (r(i.text.slice(s, e)) != c) break;
		s = e;
	}
	return k.undirectionalRange(o + i.from, s + i.from);
}
function Hi(e, t, n, r, i) {
	let a = Math.round((r - t.left) * e.defaultCharacterWidth);
	if (e.lineWrapping && n.height > e.defaultLineHeight * 1.5) {
		let t = e.viewState.heightOracle.textHeight, r = Math.floor((i - n.top - (e.defaultLineHeight - t) * .5) / t);
		a += r * e.viewState.heightOracle.lineLength;
	}
	let o = e.state.sliceDoc(n.from, n.to);
	return n.from + Rt(o, a, e.state.tabSize);
}
function Ui(e, t, n) {
	let r = e.lineBlockAt(t);
	if (Array.isArray(r.type)) {
		let e;
		for (let i of r.type) {
			if (i.from > t) break;
			if (!(i.to < t)) {
				if (i.from < t && i.to > t) return i;
				(!e || i.type == vn.Text && (e.type != i.type || (n < 0 ? i.from < t : i.to > t))) && (e = i);
			}
		}
		return e || r;
	}
	return r;
}
function Wi(e, t, n, r) {
	let i = Ui(e, t.head, t.assoc || -1), a = !r || i.type != vn.Text || !(e.lineWrapping || i.widgetLineBreaks) ? null : e.coordsAtPos(t.assoc < 0 && t.head > i.from ? t.head - 1 : t.head);
	if (a) {
		let t = e.dom.getBoundingClientRect(), r = e.textDirectionAt(i.from), o = e.posAtCoords({
			x: n == (r == I.LTR) ? t.right - 1 : t.left + 1,
			y: (a.top + a.bottom) / 2
		});
		if (o != null) return k.cursor(o, n ? -1 : 1);
	}
	return k.cursor(n ? i.to : i.from, n ? -1 : 1);
}
function Gi(e, t, n, r) {
	let i = e.state.doc.lineAt(t.head), a = e.bidiSpans(i), o = e.textDirectionAt(i.from);
	for (let s = t, c = null;;) {
		let t = yr(i, a, o, s, n), l = vr;
		if (!t) {
			if (i.number == (n ? e.state.doc.lines : 1)) return s;
			l = "\n", i = e.state.doc.line(i.number + (n ? 1 : -1)), a = e.bidiSpans(i), t = e.visualLineSide(i, !n);
		}
		if (!c) {
			if (!r) return t;
			c = r(l);
		} else if (!c(l)) return s;
		s = t;
	}
}
function Ki(e, t, n) {
	let r = e.state.charCategorizer(t), i = r(n);
	return (e) => {
		let t = r(e);
		return i == pt.Space && (i = t), i == t;
	};
}
function qi(e, t, n, r) {
	let i = t.head, a = n ? 1 : -1;
	if (i == (n ? e.state.doc.length : 0)) return k.cursor(i, t.assoc);
	let o = t.goalColumn, s, c = e.contentDOM.getBoundingClientRect(), l = e.coordsAtPos(i, t.assoc || ((t.empty ? n : t.head == t.from) ? 1 : -1)), u = e.documentTop;
	if (l) o ??= l.left - c.left, s = a < 0 ? l.top : l.bottom;
	else {
		let t = e.viewState.lineBlockAt(i);
		o ??= Math.min(c.right - c.left, e.defaultCharacterWidth * (i - t.from)), s = (a < 0 ? t.top : t.bottom) + u;
	}
	let d = c.left + o, f = e.viewState.heightOracle.textHeight >> 1, p = r ?? f;
	for (let t = 0;; t += f) {
		let r = s + (p + t) * a, i = Qi(e, {
			x: d,
			y: r
		}, !1, a);
		if (n ? r > c.bottom : r < c.top) return k.cursor(i.pos, i.assoc);
		let l = e.coordsAtPos(i.pos, i.assoc), u = l ? (l.top + l.bottom) / 2 : 0;
		if (!l || (n ? u > s : u < s)) return k.cursor(i.pos, i.assoc, void 0, o);
	}
}
function Ji(e, t, n) {
	for (;;) {
		let r = 0;
		for (let i of e) i.between(t - 1, t + 1, (e, i, a) => {
			if (t > e && t < i) {
				let a = r || n || (t - e < i - t ? -1 : 1);
				t = a < 0 ? e : i, r = a;
			}
		});
		if (!r) return t;
	}
}
function Yi(e, t) {
	let n = null;
	for (let r = 0; r < t.ranges.length; r++) {
		let i = t.ranges[r], a = null;
		if (i.empty) {
			let t = Ji(e, i.from, 0);
			t != i.from && (a = k.cursor(t, -1));
		} else {
			let t = Ji(e, i.from, -1), n = Ji(e, i.to, 1);
			(t != i.from || n != i.to) && (a = i.undirectional ? k.undirectionalRange(i.from, i.to) : k.range(i.from == i.anchor ? t : n, i.from == i.head ? t : n));
		}
		a && (n ||= t.ranges.slice(), n[r] = a);
	}
	return n ? k.create(n, t.mainIndex) : t;
}
function Xi(e, t, n) {
	let r = Ji(e.state.facet(qr).map((t) => t(e)), n.from, t.head > n.from ? -1 : 1);
	return r == n.from ? n : k.cursor(r, r < n.from ? 1 : -1);
}
var Zi = class {
	constructor(e, t) {
		this.pos = e, this.assoc = t;
	}
};
function Qi(e, t, n, r) {
	let i = e.contentDOM.getBoundingClientRect(), a = i.top + e.viewState.paddingTop, { x: o, y: s } = t, c = s - a, l;
	for (;;) {
		if (c < 0) return new Zi(0, 1);
		if (c > e.viewState.docHeight) return new Zi(e.state.doc.length, -1);
		if (l = e.elementAtHeight(c), r == null) break;
		if (l.type == vn.Text) {
			if (r < 0 ? l.to < e.viewport.from : l.from > e.viewport.to) break;
			let t = e.docView.coordsAt(r < 0 ? l.from : l.to, r > 0 ? -1 : 1);
			if (t && (r < 0 ? t.top <= c + a : t.bottom >= c + a)) break;
		}
		let t = e.viewState.heightOracle.textHeight / 2;
		c = r > 0 ? l.bottom + t : l.top - t;
	}
	if (e.viewport.from >= l.to || e.viewport.to <= l.from) {
		if (n) return null;
		if (l.type == vn.Text) {
			let t = Hi(e, i, l, o, s);
			return new Zi(t, t == l.from ? 1 : -1);
		}
	}
	if (l.type != vn.Text) return c < (l.top + l.bottom) / 2 ? new Zi(l.from, 1) : new Zi(l.to, -1);
	let u = e.docView.lineAt(l.from, 2);
	return (!u || u.length != l.length) && (u = e.docView.lineAt(l.from, -2)), new $i(e, o, s, e.textDirectionAt(l.from)).scanTile(u, l.from);
}
var $i = class {
	constructor(e, t, n, r) {
		this.view = e, this.x = t, this.y = n, this.baseDir = r, this.line = null, this.spans = null;
	}
	bidiSpansAt(e) {
		return (!this.line || this.line.from > e || this.line.to < e) && (this.line = this.view.state.doc.lineAt(e), this.spans = this.view.bidiSpans(this.line)), this;
	}
	baseDirAt(e, t) {
		let { line: n, spans: r } = this.bidiSpansAt(e);
		return r[lr.find(r, e - n.from, -1, t)].level == this.baseDir;
	}
	dirAt(e, t) {
		let { line: n, spans: r } = this.bidiSpansAt(e);
		return r[lr.find(r, e - n.from, -1, t)].dir;
	}
	bidiIn(e, t) {
		let { spans: n, line: r } = this.bidiSpansAt(e);
		return n.length > 1 || n.length && (n[0].level != this.baseDir || n[0].to + r.from < t);
	}
	scan(e, t, n = !1) {
		let r = 0, i = e.length - 1, a = /* @__PURE__ */ new Set(), o = this.bidiIn(e[0], e[i]), s, c, l = -1, u = 1e9, d;
		search: for (; r < i;) {
			let n = i - r, f = r + i >> 1;
			adjust: if (a.has(f)) {
				for (let e = 1; e < n; e++) {
					let t = f + e;
					if (t >= i && (t -= n), !a.has(t)) {
						f = t;
						break adjust;
					}
				}
				break search;
			}
			a.add(f);
			let p = t(f), m = 0;
			if (p) for (let e = 0; e < p.length; e++) {
				let t = p[e];
				if (!(t.width == 0 && p.length > 1)) {
					if (t.bottom < this.y) (!s || s.bottom < t.bottom) && (s = t), m = 1;
					else if (t.top > this.y) (!c || c.top > t.top) && (c = t), m = -1;
					else {
						let e = t.left > this.x ? this.x - t.left : t.right < this.x ? this.x - t.right : 0, n = Math.abs(e);
						n < u && (l = f, u = n, d = t), e && (m = e < 0 == (this.baseDir == I.LTR) ? -1 : 1);
					}
				}
			}
			m == -1 && (!o || this.baseDirAt(e[f], 1)) ? i = f : m == 1 && (!o || this.baseDirAt(e[f + 1], -1)) && (r = f + 1);
		}
		if (!d) {
			if (!c && !s) return {
				i: 0,
				after: !1
			};
			let n = s && (!c || this.y - s.bottom < c.top - this.y) ? s : c;
			return this.y = (n.top + n.bottom) / 2, this.scan(e, t, !0);
		}
		if (u && !n) {
			let { top: n, bottom: r } = d;
			if (s && s.bottom > (n + n + r) / 3) return this.y = s.bottom - 1, this.scan(e, t, !0);
			if (c && c.top < (n + r + r) / 3) return this.y = c.top + 1, this.scan(e, t, !0);
		}
		let f = (o ? this.dirAt(e[l], 1) : this.baseDir) == I.LTR;
		return {
			i: l,
			after: this.x > (d.left + d.right) / 2 == f
		};
	}
	scanText(e, t) {
		let n = [];
		for (let r = 0; r < e.length; r = D(e.text, r)) n.push(t + r);
		n.push(t + e.length);
		let r = this.scan(n, (r) => {
			let i = n[r] - t, a = n[r + 1] - t;
			return Kn(e.dom, i, a).getClientRects();
		});
		return r.after ? new Zi(n[r.i + 1], -1) : new Zi(n[r.i], 1);
	}
	scanTile(e, t) {
		if (!e.length) return new Zi(t, 1);
		if (e.children.length == 1) {
			let n = e.children[0];
			if (n.isText()) return this.scanText(n, t);
			if (n.isComposite()) return this.scanTile(n, t);
		}
		let n = [t];
		for (let r = 0, i = t; r < e.children.length; r++) n.push(i += e.children[r].length);
		let r = this.scan(n, (t) => {
			let n = e.children[t];
			return n.flags & 48 ? null : (n.dom.nodeType == 1 ? n.dom : Kn(n.dom, 0, n.length)).getClientRects();
		}), i = e.children[r.i], a = n[r.i];
		return i.isText() ? this.scanText(i, a) : i.isComposite() ? this.scanTile(i, a) : r.after ? new Zi(n[r.i + 1], -1) : new Zi(a, 1);
	}
}, ea = "￿", ta = class {
	constructor(e, t) {
		this.points = e, this.view = t, this.text = "", this.lineSeparator = t.state.facet(M.lineSeparator);
	}
	append(e) {
		this.text += e;
	}
	lineBreak() {
		this.text += ea;
	}
	readRange(e, t) {
		if (!e) return this;
		let n = e.parentNode;
		for (let r = e;;) {
			this.findPointBefore(n, r);
			let e = this.text.length;
			this.readNode(r);
			let i = R.get(r), a = r.nextSibling;
			if (a == t) {
				i?.breakAfter && !a && n != this.view.contentDOM && this.lineBreak();
				break;
			}
			let o = R.get(a);
			(i && o ? i.breakAfter : (i ? i.breakAfter : Mn(r)) || Mn(a) && (r.nodeName != "BR" || i?.isWidget()) && this.text.length > e) && !ra(a, t) && this.lineBreak(), r = a;
		}
		return this.findPointBefore(n, t), this;
	}
	readTextNode(e) {
		let t = e.nodeValue;
		for (let n of this.points) n.node == e && (n.pos = this.text.length + Math.min(n.offset, t.length));
		for (let n = 0, r = this.lineSeparator ? null : /\r\n?|\n/g;;) {
			let i = -1, a = 1, o;
			if (this.lineSeparator ? (i = t.indexOf(this.lineSeparator, n), a = this.lineSeparator.length) : (o = r.exec(t)) && (i = o.index, a = o[0].length), this.append(t.slice(n, i < 0 ? t.length : i)), i < 0) break;
			if (this.lineBreak(), a > 1) for (let t of this.points) t.node == e && t.pos > this.text.length && (t.pos -= a - 1);
			n = i + a;
		}
	}
	readNode(e) {
		let t = R.get(e), n = t && t.overrideDOMText;
		if (n != null) {
			this.findPointInside(e, n.length);
			for (let e = n.iter(); !e.next().done;) e.lineBreak ? this.lineBreak() : this.append(e.value);
		} else e.nodeType == 3 ? this.readTextNode(e) : e.nodeName == "BR" ? e.nextSibling && this.lineBreak() : e.nodeType == 1 && this.readRange(e.firstChild, null);
	}
	findPointBefore(e, t) {
		for (let n of this.points) n.node == e && e.childNodes[n.offset] == t && (n.pos = this.text.length);
	}
	findPointInside(e, t) {
		for (let n of this.points) (e.nodeType == 3 ? n.node == e : e.contains(n.node)) && (n.pos = this.text.length + (na(e, n.node, n.offset) ? t : 0));
	}
};
function na(e, t, n) {
	for (;;) {
		if (!t || n < Pn(t)) return !1;
		if (t == e) return !0;
		n = jn(t) + 1, t = t.parentNode;
	}
}
function ra(e, t) {
	let n;
	for (; e != t && e; e = e.nextSibling) {
		let t = R.get(e);
		if (!t?.isWidget()) return !1;
		t && (n ||= []).push(t);
	}
	if (n) {
		for (let e of n) if (e.overrideDOMText?.length) return !1;
	}
	return !0;
}
var ia = class {
	constructor(e, t) {
		this.node = e, this.offset = t, this.pos = -1;
	}
}, aa = class {
	constructor(e, t, n, r) {
		this.typeOver = r, this.bounds = null, this.text = "", this.domChanged = t > -1;
		let { impreciseHead: i, impreciseAnchor: a } = e.docView, o = e.state.selection;
		if (e.state.readOnly && t > -1) this.newSel = null;
		else if (t > -1 && (this.bounds = oa(e.docView.tile, t, n, 0))) {
			let t = i || a ? [] : da(e), n = new ta(t, e);
			n.readRange(this.bounds.startDOM, this.bounds.endDOM), this.text = n.text, this.newSel = fa(t, this.bounds.from);
		} else {
			let t = e.observer.selectionRange, n = i && i.node == t.focusNode && i.offset == t.focusOffset || !Dn(e.contentDOM, t.focusNode) ? o.main.head : e.docView.posFromDOM(t.focusNode, t.focusOffset), r = a && a.node == t.anchorNode && a.offset == t.anchorOffset || !Dn(e.contentDOM, t.anchorNode) ? o.main.anchor : e.docView.posFromDOM(t.anchorNode, t.anchorOffset), s = e.viewport;
			if ((P.ios || P.chrome) && n != r && Math.min(n, r) <= o.main.from && Math.max(n, r) >= o.main.to && (s.from > 0 || s.to < e.state.doc.length)) {
				let t = Math.min(n, r), i = Math.max(n, r), a = s.from - t, o = s.to - i;
				(a == 0 || a == 1 || t == 0) && (o == 0 || o == -1 || i == e.state.doc.length) && (n = 0, r = e.state.doc.length);
			}
			if (e.inputState.composing > -1 && o.ranges.length > 1) this.newSel = o.replaceRange(k.range(r, n));
			else if (e.lineWrapping && r == n && !(o.main.empty && o.main.head == n) && e.inputState.lastTouchTime > Date.now() - 100) {
				let t = e.coordsAtPos(n, -1), r = 0;
				t && (r = e.inputState.lastTouchY <= t.bottom ? -1 : 1), this.newSel = k.create([k.cursor(n, r)]);
			} else this.newSel = k.single(r, n);
		}
	}
};
function oa(e, t, n, r) {
	if (e.isComposite()) {
		let i = -1, a = -1, o = -1, s = -1;
		for (let c = 0, l = r, u = r; c < e.children.length; c++) {
			let r = e.children[c], d = l + r.length;
			if (l < t && d > n) return oa(r, t, n, l);
			if (d >= t && i == -1 && (i = c, a = l), l > n && r.dom.parentNode == e.dom) {
				o = c, s = u;
				break;
			}
			u = d, l = d + r.breakAfter;
		}
		return {
			from: a,
			to: s < 0 ? r + e.length : s,
			startDOM: (i ? e.children[i - 1].dom.nextSibling : null) || e.dom.firstChild,
			endDOM: o < e.children.length && o >= 0 ? e.children[o].dom : null
		};
	}
	return e.isText() ? {
		from: r,
		to: r + e.length,
		startDOM: e.dom,
		endDOM: e.dom.nextSibling
	} : null;
}
function sa(e, t) {
	let n, { newSel: r } = t, { state: i } = e, a = i.selection.main, o = e.inputState.lastKeyTime > Date.now() - 100 ? e.inputState.lastKeyCode : -1;
	if (t.bounds) {
		let { from: e, to: r } = t.bounds, s = a.from, c = null;
		(o === 8 || P.android && t.text.length < r - e) && (s = a.to, c = "end");
		let l = i.doc.sliceString(e, r, ea), u, d;
		!a.empty && a.from >= e && a.to <= r && (t.typeOver || l != t.text) && l.slice(0, a.from - e) == t.text.slice(0, a.from - e) && l.slice(a.to - e) == t.text.slice(u = t.text.length - (l.length - (a.to - e))) ? n = {
			from: a.from,
			to: a.to,
			insert: T.of(t.text.slice(a.from - e, u).split(ea))
		} : (d = ua(l, t.text, s - e, c)) && (P.chrome && o == 13 && d.toB == d.from + 2 && t.text.slice(d.from, d.toB) == "￿￿" && d.toB--, n = {
			from: e + d.from,
			to: e + d.toA,
			insert: T.of(t.text.slice(d.from, d.toB).split(ea))
		});
	} else r && (!e.hasFocus && i.facet(Lr) || pa(r, a)) && (r = null);
	if (!n && !r) return !1;
	if ((P.mac || P.android) && n && n.from == n.to && n.from == a.head - 1 && /^\. ?$/.test(n.insert.toString()) && e.contentDOM.getAttribute("autocorrect") == "off" ? (r && n.insert.length == 2 && (r = k.single(r.main.anchor - 1, r.main.head - 1)), n = {
		from: n.from,
		to: n.to,
		insert: T.of([n.insert.toString().replace(".", " ")])
	}) : i.doc.lineAt(a.from).to < a.to && e.docView.lineHasWidget(a.to) && e.inputState.insertingTextAt > Date.now() - 50 ? n = {
		from: a.from,
		to: a.to,
		insert: i.toText(e.inputState.insertingText)
	} : P.chrome && n && n.from == n.to && n.from == a.head && n.insert.toString() == "\n " && e.lineWrapping && (r &&= k.single(r.main.anchor - 1, r.main.head - 1), n = {
		from: a.from,
		to: a.to,
		insert: T.of([" "])
	}), n) return ca(e, n, r, o);
	if (r && !pa(r, a)) {
		let t = !1, n = "select";
		return e.inputState.lastSelectionTime > Date.now() - 50 && (e.inputState.lastSelectionOrigin == "select" && (t = !0), n = e.inputState.lastSelectionOrigin, n == "select.pointer" && (r = Yi(i.facet(qr).map((t) => t(e)), r))), e.dispatch({
			selection: r,
			scrollIntoView: t,
			userEvent: n
		}), !0;
	}
	return !1;
}
function ca(e, t, n, r = -1) {
	if (P.ios && e.inputState.flushIOSKey(t)) return !0;
	let i = e.state.selection.main;
	if (P.android && (t.to == i.to && (t.from == i.from || t.from == i.from - 1 && e.state.sliceDoc(t.from, i.from) == " ") && t.insert.length == 1 && t.insert.lines == 2 && qn(e.contentDOM, "Enter", 13) || (t.from == i.from - 1 && t.to == i.to && t.insert.length == 0 || r == 8 && t.insert.length < t.to - t.from && t.to > i.head) && qn(e.contentDOM, "Backspace", 8) || t.from == i.from && t.to == i.to + 1 && t.insert.length == 0 && qn(e.contentDOM, "Delete", 46))) return !0;
	let a = t.insert.toString();
	e.inputState.composing >= 0 && e.inputState.composing++;
	let o, s = () => o ||= la(e, t, n);
	return e.state.facet(Er).some((n) => n(e, t.from, t.to, a, s)) || e.dispatch(s()), !0;
}
function la(e, t, n) {
	let r, i = e.state, a = i.selection.main, o = -1;
	if (t.from == t.to && t.from < a.from || t.from > a.to) {
		let n = t.from < a.from ? -1 : 1, r = n < 0 ? a.from : a.to, s = Ji(i.facet(qr).map((t) => t(e)), r, n);
		t.from == s && (o = s);
	}
	if (o > -1) r = {
		changes: t,
		selection: k.cursor(t.from + t.insert.length, -1)
	};
	else if (t.from >= a.from && t.to <= a.to && t.to - t.from >= (a.to - a.from) / 3 && (!n || n.main.empty && n.main.from == t.from + t.insert.length) && e.inputState.composing < 0) {
		let n = a.from < t.from ? i.sliceDoc(a.from, t.from) : "", o = a.to > t.to ? i.sliceDoc(t.to, a.to) : "";
		r = i.replaceSelection(e.state.toText(n + t.insert.sliceString(0, void 0, e.state.lineBreak) + o));
	} else {
		let o = i.changes(t), s = n && n.main.to <= o.newLength ? n.main : void 0;
		if (i.selection.ranges.length > 1 && (e.inputState.composing >= 0 || e.inputState.compositionPendingChange) && t.to <= a.to + 10 && t.to >= a.to - 10) {
			let c = e.state.sliceDoc(t.from, t.to), l, u = n && ji(e, n.main.head);
			if (u) {
				let e = t.insert.length - (t.to - t.from);
				l = {
					from: u.from,
					to: u.to - e
				};
			} else l = e.state.doc.lineAt(a.head);
			let d = a.to - t.to;
			r = i.changeByRange((n) => {
				if (n.from == a.from && n.to == a.to) return {
					changes: o,
					range: s || n.map(o)
				};
				let r = n.to - d, u = r - c.length;
				if (e.state.sliceDoc(u, r) != c || r >= l.from && u <= l.to) return { range: n };
				let f = i.changes({
					from: u,
					to: r,
					insert: t.insert
				}), p = n.to - a.to;
				return {
					changes: f,
					range: s ? k.range(Math.max(0, s.anchor + p), Math.max(0, s.head + p)) : n.map(f)
				};
			});
		} else r = {
			changes: o,
			selection: s && i.selection.replaceRange(s)
		};
	}
	let s = "input.type";
	return (e.composing || e.inputState.compositionPendingChange && e.inputState.compositionEndedAt > Date.now() - 50) && (e.inputState.compositionPendingChange = !1, s += ".compose", e.inputState.compositionFirstChange && (s += ".start", e.inputState.compositionFirstChange = !1)), i.update(r, {
		userEvent: s,
		scrollIntoView: !0
	});
}
function ua(e, t, n, r) {
	let i = Math.min(e.length, t.length), a = 0;
	for (; a < i && e.charCodeAt(a) == t.charCodeAt(a);) a++;
	if (a == i && e.length == t.length) return null;
	let o = e.length, s = t.length;
	for (; o > 0 && s > 0 && e.charCodeAt(o - 1) == t.charCodeAt(s - 1);) o--, s--;
	if (r == "end") {
		let e = Math.max(0, a - Math.min(o, s));
		n -= o + e - a;
	}
	if (o < a && e.length < t.length) {
		let e = n <= a && n >= o ? a - n : 0;
		a -= e, s = a + (s - o), o = a;
	} else if (s < a) {
		let e = n <= a && n >= s ? a - n : 0;
		a -= e, o = a + (o - s), s = a;
	}
	return {
		from: a,
		toA: o,
		toB: s
	};
}
function da(e) {
	let t = [];
	if (e.root.activeElement != e.contentDOM) return t;
	let { anchorNode: n, anchorOffset: r, focusNode: i, focusOffset: a } = e.observer.selectionRange;
	return n && (t.push(new ia(n, r)), (i != n || a != r) && t.push(new ia(i, a))), t;
}
function fa(e, t) {
	if (e.length == 0) return null;
	let n = e[0].pos, r = e.length == 2 ? e[1].pos : n;
	return n > -1 && r > -1 ? k.single(n + t, r + t) : null;
}
function pa(e, t) {
	return t.head == e.main.head && t.anchor == e.main.anchor;
}
var ma = class {
	setSelectionOrigin(e) {
		this.lastSelectionOrigin = e, this.lastSelectionTime = Date.now();
	}
	constructor(e) {
		this.view = e, this.lastKeyCode = 0, this.lastKeyTime = 0, this.touchActive = !1, this.lastTouchTime = 0, this.lastTouchX = 0, this.lastTouchY = 0, this.lastFocusTime = 0, this.lastScrollTop = 0, this.lastScrollLeft = 0, this.lastWheelEvent = 0, this.pendingIOSKey = void 0, this.lastIOSMomentumScroll = 0, this.tabFocusMode = -1, this.lastSelectionOrigin = null, this.lastSelectionTime = 0, this.lastContextMenu = 0, this.scrollHandlers = [], this.handlers = Object.create(null), this.composing = -1, this.compositionFirstChange = null, this.compositionEndedAt = 0, this.compositionPendingKey = !1, this.compositionPendingChange = !1, this.insertingText = "", this.insertingTextAt = 0, this.mouseSelection = null, this.draggedContent = null, this.handleEvent = this.handleEvent.bind(this), this.notifiedFocused = e.hasFocus, P.safari && e.contentDOM.addEventListener("input", () => null), P.gecko && Xa(e.contentDOM.ownerDocument);
	}
	handleEvent(e) {
		Oa(this.view, e) && !this.ignoreDuringComposition(e) && (e.type == "keydown" && this.keydown(e) || (this.view.updateState == 0 ? this.runHandlers(e.type, e) : Promise.resolve().then(() => this.runHandlers(e.type, e))));
	}
	runHandlers(e, t) {
		let n = this.handlers[e];
		if (n) {
			for (let e of n.observers) e(this.view, t);
			for (let e of n.handlers) {
				if (t.defaultPrevented) break;
				if (e(this.view, t)) {
					t.preventDefault();
					break;
				}
			}
		}
	}
	ensureHandlers(e) {
		let t = _a(e), n = this.handlers, r = this.view.contentDOM;
		for (let e in t) if (e != "scroll") {
			let i = !t[e].handlers.length, a = n[e];
			a && i != !a.handlers.length && (r.removeEventListener(e, this.handleEvent), a = null), a || r.addEventListener(e, this.handleEvent, { passive: i });
		}
		for (let e in n) e != "scroll" && !t[e] && r.removeEventListener(e, this.handleEvent);
		this.handlers = t;
	}
	keydown(e) {
		if (this.lastKeyCode = e.keyCode, this.lastKeyTime = Date.now(), e.keyCode == 9 && this.tabFocusMode > -1 && (!this.tabFocusMode || Date.now() <= this.tabFocusMode)) return !0;
		if (this.tabFocusMode > 0 && e.keyCode != 27 && ba.indexOf(e.keyCode) < 0 && (this.tabFocusMode = -1), P.android && P.chrome && !e.synthetic && (e.keyCode == 13 || e.keyCode == 8)) return this.view.observer.delayAndroidKey(e.key, e.keyCode), !0;
		if (P.ios && !e.synthetic && !e.altKey && !e.metaKey && (va.some((t) => t.keyCode == e.keyCode) && !e.ctrlKey || ya.indexOf(e.key) > -1 && e.ctrlKey)) {
			let t = {
				ctrlKey: e.ctrlKey,
				altKey: e.altKey,
				metaKey: e.metaKey,
				shiftKey: e.shiftKey
			};
			return t.shiftKey && P.ios && !/^(off|none)$/.test(this.view.contentDOM.autocapitalize) && ha(this.view.win) && (t.shiftKey = !1), this.pendingIOSKey = {
				key: e.key,
				keyCode: e.keyCode,
				mods: t
			}, setTimeout(() => this.flushIOSKey(), 50), !0;
		}
		return e.keyCode != 229 && this.view.observer.forceFlush(), !1;
	}
	flushIOSKey(e) {
		let t = this.pendingIOSKey;
		return !t || this.view.observer.pendingRecords().length || t.key == "Enter" && e && e.from < e.to && /^\S+$/.test(e.insert.toString()) ? !1 : (this.pendingIOSKey = void 0, qn(this.view.contentDOM, t.key, t.keyCode, t.mods));
	}
	ignoreDuringComposition(e) {
		return !/^key/.test(e.type) || e.synthetic ? !1 : this.composing > 0 ? !0 : P.safari && !P.ios && this.compositionPendingKey && Date.now() - this.compositionEndedAt < 100 ? (this.compositionPendingKey = !1, !0) : !1;
	}
	startMouseSelection(e) {
		this.mouseSelection && this.mouseSelection.destroy(), this.mouseSelection = e;
	}
	update(e) {
		this.view.observer.update(e), this.mouseSelection && this.mouseSelection.update(e), this.draggedContent && e.docChanged && (this.draggedContent = this.draggedContent.map(e.changes)), e.transactions.length && (this.lastKeyCode = this.lastSelectionTime = 0);
	}
	destroy() {
		this.mouseSelection && this.mouseSelection.destroy();
	}
};
function ha(e) {
	return e.visualViewport ? e.visualViewport.height * e.visualViewport.scale / e.document.documentElement.clientHeight < .85 : !1;
}
function ga(e, t) {
	return (n, r) => {
		try {
			return t.call(e, r, n);
		} catch (e) {
			Ir(n.state, e);
		}
	};
}
function _a(e) {
	let t = Object.create(null);
	function n(e) {
		return t[e] || (t[e] = {
			observers: [],
			handlers: []
		});
	}
	for (let t of e) {
		let e = t.spec, r = e && e.plugin.domEventHandlers, i = e && e.plugin.domEventObservers;
		if (r) for (let e in r) {
			let i = r[e];
			i && n(e).handlers.push(ga(t.value, i));
		}
		if (i) for (let e in i) {
			let r = i[e];
			r && n(e).observers.push(ga(t.value, r));
		}
	}
	for (let e in ka) n(e).handlers.push(ka[e]);
	for (let e in z) n(e).observers.push(z[e]);
	return t;
}
var va = [
	{
		key: "Backspace",
		keyCode: 8,
		inputType: "deleteContentBackward"
	},
	{
		key: "Enter",
		keyCode: 13,
		inputType: "insertParagraph"
	},
	{
		key: "Enter",
		keyCode: 13,
		inputType: "insertLineBreak"
	},
	{
		key: "Delete",
		keyCode: 46,
		inputType: "deleteContentForward"
	}
], ya = "dthko", ba = [
	16,
	17,
	18,
	20,
	91,
	92,
	224,
	225
], xa = 6;
function Sa(e) {
	return Math.max(0, e) * .7 + 8;
}
function Ca(e, t) {
	return Math.max(Math.abs(e.clientX - t.clientX), Math.abs(e.clientY - t.clientY));
}
var wa = class {
	constructor(e, t, n, r) {
		this.view = e, this.startEvent = t, this.style = n, this.mustSelect = r, this.scrollSpeed = {
			x: 0,
			y: 0
		}, this.scrolling = -1, this.lastEvent = t, this.scrollParents = zn(e.contentDOM), this.atoms = e.state.facet(qr).map((t) => t(e));
		let i = e.contentDOM.ownerDocument;
		i.addEventListener("mousemove", this.move = this.move.bind(this)), i.addEventListener("mouseup", this.up = this.up.bind(this)), this.extend = t.shiftKey, this.multiple = e.state.facet(M.allowMultipleSelections) && Ta(e, t), this.dragging = Da(e, t) && za(t) == 1 ? null : !1;
	}
	start(e) {
		this.dragging === !1 && this.select(e);
	}
	move(e) {
		if (e.buttons == 0) return this.destroy();
		if (this.dragging || this.dragging == null && Ca(this.startEvent, e) < 10) return;
		this.select(this.lastEvent = e);
		let t = 0, n = 0, r = 0, i = 0, a = this.view.win.innerWidth, o = this.view.win.innerHeight;
		this.scrollParents.x && ({left: r, right: a} = this.scrollParents.x.getBoundingClientRect()), this.scrollParents.y && ({top: i, bottom: o} = this.scrollParents.y.getBoundingClientRect());
		let s = Zr(this.view);
		e.clientX - s.left <= r + xa ? t = -Sa(r - e.clientX) : e.clientX + s.right >= a - xa && (t = Sa(e.clientX - a)), e.clientY - s.top <= i + xa ? n = -Sa(i - e.clientY) : e.clientY + s.bottom >= o - xa && (n = Sa(e.clientY - o)), this.setScrollSpeed(t, n);
	}
	up(e) {
		this.dragging ?? this.select(this.lastEvent), this.dragging || e.preventDefault(), this.destroy();
	}
	destroy() {
		this.setScrollSpeed(0, 0);
		let e = this.view.contentDOM.ownerDocument;
		e.removeEventListener("mousemove", this.move), e.removeEventListener("mouseup", this.up), this.view.inputState.mouseSelection = this.view.inputState.draggedContent = null;
	}
	setScrollSpeed(e, t) {
		this.scrollSpeed = {
			x: e,
			y: t
		}, e || t ? this.scrolling < 0 && (this.scrolling = setInterval(() => this.scroll(), 50)) : this.scrolling > -1 && (clearInterval(this.scrolling), this.scrolling = -1);
	}
	scroll() {
		let { x: e, y: t } = this.scrollSpeed;
		e && this.scrollParents.x && (this.scrollParents.x.scrollLeft += e, e = 0), t && this.scrollParents.y && (this.scrollParents.y.scrollTop += t, t = 0), (e || t) && this.view.win.scrollBy(e, t), this.dragging === !1 && this.select(this.lastEvent);
	}
	select(e) {
		let { view: t } = this, n = Yi(this.atoms, this.style.get(e, this.extend, this.multiple));
		(this.mustSelect || !n.eq(t.state.selection, this.dragging === !1)) && this.view.dispatch({
			selection: n,
			userEvent: "select.pointer"
		}), this.mustSelect = !1;
	}
	update(e) {
		e.transactions.some((e) => e.isUserEvent("input.type")) ? this.destroy() : this.style.update(e) && setTimeout(() => this.select(this.lastEvent), 20);
	}
};
function Ta(e, t) {
	let n = e.state.facet(xr);
	return n.length ? n[0](t) : P.mac ? t.metaKey : t.ctrlKey;
}
function Ea(e, t) {
	let n = e.state.facet(Sr);
	return n.length ? n[0](t) : P.mac ? !t.altKey : !t.ctrlKey;
}
function Da(e, t) {
	let { main: n } = e.state.selection;
	if (n.empty) return !1;
	let r = En(e.root);
	if (!r || r.rangeCount == 0) return !0;
	let i = r.getRangeAt(0).getClientRects();
	for (let e = 0; e < i.length; e++) {
		let n = i[e];
		if (n.left <= t.clientX && n.right >= t.clientX && n.top <= t.clientY && n.bottom >= t.clientY) return !0;
	}
	return !1;
}
function Oa(e, t) {
	if (!t.bubbles) return !0;
	if (t.defaultPrevented) return !1;
	for (let n = t.target, r; n != e.contentDOM; n = n.parentNode) if (!n || n.nodeType == 11 || (r = R.get(n)) && r.isWidget() && !r.isHidden && r.widget.ignoreEvent(t)) return !1;
	return !0;
}
var ka = /*@__PURE__*/ Object.create(null), z = /*@__PURE__*/ Object.create(null), Aa = P.ie && P.ie_version < 15 || P.ios && P.webkit_version < 604;
function ja(e) {
	let t = e.dom.parentNode;
	if (!t) return;
	let n = t.appendChild(document.createElement("textarea"));
	n.style.cssText = "position: fixed; left: -10000px; top: 10px", n.focus(), setTimeout(() => {
		e.focus(), n.remove(), Na(e, n.value);
	}, 50);
}
function Ma(e, t, n) {
	for (let r of e.facet(t)) n = r(n, e);
	return n;
}
function Na(e, t) {
	t = Ma(e.state, Or, t);
	let { state: n } = e, r, i = 1, a = n.toText(t), o = a.lines == n.selection.ranges.length;
	if (Ga != null && n.selection.ranges.every((e) => e.empty) && Ga == a.toString()) {
		let e = -1;
		r = n.changeByRange((r) => {
			let s = n.doc.lineAt(r.from);
			if (s.from == e) return { range: r };
			e = s.from;
			let c = n.toText((o ? a.line(i++).text : t) + n.lineBreak);
			return {
				changes: {
					from: s.from,
					insert: c
				},
				range: k.cursor(r.from + c.length)
			};
		});
	} else r = o ? n.changeByRange((e) => {
		let t = a.line(i++);
		return {
			changes: {
				from: e.from,
				to: e.to,
				insert: t.text
			},
			range: k.cursor(e.from + t.length)
		};
	}) : n.replaceSelection(a);
	e.dispatch(r, {
		userEvent: "input.paste",
		scrollIntoView: !0
	});
}
z.scroll = (e) => {
	let t = e.inputState;
	t.lastScrollTop = e.scrollDOM.scrollTop, t.lastScrollLeft = e.scrollDOM.scrollLeft, P.ios && !t.touchActive && (t.lastIOSMomentumScroll = Date.now());
}, z.wheel = z.mousewheel = (e) => {
	e.inputState.lastWheelEvent = Date.now();
}, ka.keydown = (e, t) => (e.inputState.setSelectionOrigin("select"), t.keyCode == 27 && e.inputState.tabFocusMode != 0 && (e.inputState.tabFocusMode = Date.now() + 2e3), !1), z.touchstart = (e, t) => {
	let n = e.inputState, r = t.targetTouches[0];
	n.touchActive = !0, n.lastTouchTime = Date.now(), r && (n.lastTouchX = r.clientX, n.lastTouchY = r.clientY), n.setSelectionOrigin("select.pointer");
}, z.touchmove = (e) => {
	e.inputState.setSelectionOrigin("select.pointer");
}, z.touchend = (e, t) => {
	e.inputState.touchActive = !1;
}, ka.mousedown = (e, t) => {
	if (e.observer.flush(), e.inputState.lastTouchTime > Date.now() - 2e3) return !1;
	let n = null;
	for (let r of e.state.facet(Cr)) if (n = r(e, t), n) break;
	if (!n && t.button == 0 && (n = Ba(e, t)), n) {
		let r = !e.hasFocus;
		e.inputState.startMouseSelection(new wa(e, t, n, r)), r && e.observer.ignore(() => {
			Wn(e.contentDOM);
			let t = e.root.activeElement;
			t && !t.contains(e.contentDOM) && t.blur();
		});
		let i = e.inputState.mouseSelection;
		if (i) return i.start(t), i.dragging === !1;
	} else e.inputState.setSelectionOrigin("select.pointer");
	return !1;
};
function Pa(e, t, n, r) {
	if (r == 1) return k.cursor(t, n);
	if (r == 2) return Vi(e.state, t, n);
	{
		let r = e.docView.lineAt(t, n), i = e.state.doc.lineAt(r ? r.posAtEnd : t), a = r ? r.posAtStart : i.from, o = r ? r.posAtEnd : i.to;
		return o < e.state.doc.length && o == i.to && o++, k.undirectionalRange(a, o);
	}
}
var Fa = P.ie && P.ie_version <= 11, Ia = null, La = 0, Ra = 0;
function za(e) {
	if (!Fa) return e.detail;
	let t = Ia, n = Ra;
	return Ia = e, Ra = Date.now(), La = !t || n > Date.now() - 400 && Math.abs(t.clientX - e.clientX) < 2 && Math.abs(t.clientY - e.clientY) < 2 ? (La + 1) % 3 : 1;
}
function Ba(e, t) {
	let n = e.posAndSideAtCoords({
		x: t.clientX,
		y: t.clientY
	}, !1), r = za(t), i = e.state.selection;
	return {
		update(e) {
			e.docChanged && (n.pos = e.changes.mapPos(n.pos), i = i.map(e.changes));
		},
		get(t, a, o) {
			let s = e.posAndSideAtCoords({
				x: t.clientX,
				y: t.clientY
			}, !1), c, l = Pa(e, s.pos, s.assoc, r);
			if (n.pos != s.pos && !a) {
				let t = Pa(e, n.pos, n.assoc, r), i = Math.min(t.from, l.from), a = Math.max(t.to, l.to);
				l = i < l.from ? k.range(i, a, l.assoc) : k.range(a, i, l.assoc);
			}
			return a ? i.replaceRange(i.main.extend(l.from, l.to, l.assoc)) : o && r == 1 && i.ranges.length > 1 && (c = Va(i, s.pos)) ? c : o ? i.addRange(l) : k.create([l]);
		}
	};
}
function Va(e, t) {
	for (let n = 0; n < e.ranges.length; n++) {
		let { from: r, to: i } = e.ranges[n];
		if (r <= t && i >= t) return k.create(e.ranges.slice(0, n).concat(e.ranges.slice(n + 1)), e.mainIndex == n ? 0 : e.mainIndex - +(e.mainIndex > n));
	}
	return null;
}
ka.dragstart = (e, t) => {
	let { selection: { main: n } } = e.state;
	if (t.target.draggable) {
		let r = e.docView.tile.nearest(t.target);
		if (r && r.isWidget()) {
			let e = r.posAtStart, t = e + r.length;
			(e >= n.to || t <= n.from) && (n = k.undirectionalRange(e, t));
		}
	}
	let { inputState: r } = e;
	return r.mouseSelection && (r.mouseSelection.dragging = !0), r.draggedContent = n, t.dataTransfer && (t.dataTransfer.setData("Text", Ma(e.state, kr, e.state.sliceDoc(n.from, n.to))), t.dataTransfer.effectAllowed = "copyMove"), !1;
}, ka.dragend = (e) => (e.inputState.draggedContent = null, !1);
function Ha(e, t, n, r) {
	if (n = Ma(e.state, Or, n), !n) return;
	let i = e.posAtCoords({
		x: t.clientX,
		y: t.clientY
	}, !1), { draggedContent: a } = e.inputState, o = r && a && Ea(e, t) ? {
		from: a.from,
		to: a.to
	} : null, s = {
		from: i,
		insert: n
	}, c = e.state.changes(o ? [o, s] : s);
	e.focus(), e.dispatch({
		changes: c,
		selection: {
			anchor: c.mapPos(i, -1),
			head: c.mapPos(i, 1)
		},
		userEvent: o ? "move.drop" : "input.drop"
	}), e.inputState.draggedContent = null;
}
ka.drop = (e, t) => {
	if (!t.dataTransfer) return !1;
	if (e.state.readOnly) return !0;
	let n = t.dataTransfer.files;
	if (n && n.length) {
		let r = Array(n.length), i = 0, a = () => {
			++i == n.length && Ha(e, t, r.filter((e) => e != null).join(e.state.lineBreak), !1);
		};
		for (let e = 0; e < n.length; e++) {
			let t = new FileReader();
			t.onerror = a, t.onload = () => {
				/[\x00-\x08\x0e-\x1f]{2}/.test(t.result) || (r[e] = t.result), a();
			}, t.readAsText(n[e]);
		}
		return !0;
	}
	{
		let n = t.dataTransfer.getData("Text");
		if (n) return Ha(e, t, n, !0), !0;
	}
	return !1;
}, ka.paste = (e, t) => {
	if (e.state.readOnly) return !0;
	e.observer.flush();
	let n = Aa ? null : t.clipboardData;
	return n ? (Na(e, n.getData("text/plain") || n.getData("text/uri-list")), !0) : (ja(e), !1);
};
function Ua(e, t) {
	let n = e.dom.parentNode;
	if (!n) return;
	let r = n.appendChild(document.createElement("textarea"));
	r.style.cssText = "position: fixed; left: -10000px; top: 10px", r.value = t, r.focus(), r.selectionEnd = t.length, r.selectionStart = 0, setTimeout(() => {
		r.remove(), e.focus();
	}, 50);
}
function Wa(e) {
	let t = [], n = [], r = !1;
	for (let r of e.selection.ranges) r.empty || (t.push(e.sliceDoc(r.from, r.to)), n.push(r));
	if (!t.length) {
		let i = -1;
		for (let { from: r } of e.selection.ranges) {
			let a = e.doc.lineAt(r);
			a.number > i && (t.push(a.text), n.push({
				from: a.from,
				to: Math.min(e.doc.length, a.to + 1)
			})), i = a.number;
		}
		r = !0;
	}
	return {
		text: Ma(e, kr, t.join(e.lineBreak)),
		ranges: n,
		linewise: r
	};
}
var Ga = null;
ka.copy = ka.cut = (e, t) => {
	if (!On(e.contentDOM, e.observer.selectionRange)) return !1;
	let { text: n, ranges: r, linewise: i } = Wa(e.state);
	if (!n && !i) return !1;
	Ga = i ? n : null, t.type == "cut" && !e.state.readOnly && e.dispatch({
		changes: r,
		scrollIntoView: !0,
		userEvent: "delete.cut"
	});
	let a = Aa ? null : t.clipboardData;
	return a ? (a.clearData(), a.setData("text/plain", n), !0) : (Ua(e, n), !1);
};
var Ka = /*@__PURE__*/ tt.define();
function qa(e, t) {
	let n = [];
	for (let r of e.facet(Dr)) {
		let i = r(e, t);
		i && n.push(i);
	}
	return n.length ? e.update({
		effects: n,
		annotations: Ka.of(!0)
	}) : null;
}
function Ja(e) {
	setTimeout(() => {
		let t = e.hasFocus;
		if (t != e.inputState.notifiedFocused) {
			let n = qa(e.state, t);
			n ? e.dispatch(n) : e.update([]);
		}
	}, 10);
}
z.focus = (e) => {
	e.inputState.lastFocusTime = Date.now(), !e.scrollDOM.scrollTop && (e.inputState.lastScrollTop || e.inputState.lastScrollLeft) && (e.scrollDOM.scrollTop = e.inputState.lastScrollTop, e.scrollDOM.scrollLeft = e.inputState.lastScrollLeft), Ja(e);
}, z.blur = (e) => {
	e.observer.clearSelectionRange(), Ja(e);
}, z.compositionstart = z.compositionupdate = (e) => {
	e.observer.editContext || (e.inputState.compositionFirstChange ?? (e.inputState.compositionFirstChange = !0), e.inputState.composing < 0 && (e.inputState.composing = 0));
}, z.compositionend = (e) => {
	e.observer.editContext || (e.inputState.composing = -1, e.inputState.compositionEndedAt = Date.now(), e.inputState.compositionPendingKey = !0, e.inputState.compositionPendingChange = e.observer.pendingRecords().length > 0, e.inputState.compositionFirstChange = null, P.chrome && P.android ? e.observer.flushSoon() : e.inputState.compositionPendingChange ? Promise.resolve().then(() => e.observer.flush()) : setTimeout(() => {
		e.inputState.composing < 0 && e.docView.hasComposition && e.update([]);
	}, 50));
}, z.contextmenu = (e) => {
	e.inputState.lastContextMenu = Date.now();
}, ka.beforeinput = (e, t) => {
	if ((t.inputType == "insertText" || t.inputType == "insertCompositionText") && (e.inputState.insertingText = t.data, e.inputState.insertingTextAt = Date.now()), t.inputType == "insertReplacementText" && e.observer.editContext) {
		let n = t.dataTransfer?.getData("text/plain"), r = t.getTargetRanges();
		if (n && r.length) {
			let t = r[0];
			return ca(e, {
				from: e.posAtDOM(t.startContainer, t.startOffset),
				to: e.posAtDOM(t.endContainer, t.endOffset),
				insert: e.state.toText(n)
			}, null), !0;
		}
	}
	let n;
	if (P.chrome && P.android && (n = va.find((e) => e.inputType == t.inputType)) && (e.observer.delayAndroidKey(n.key, n.keyCode), n.key == "Backspace" || n.key == "Delete")) {
		let t = window.visualViewport?.height || 0;
		setTimeout(() => {
			(window.visualViewport?.height || 0) > t + 10 && e.hasFocus && (e.contentDOM.blur(), e.focus());
		}, 100);
	}
	return P.ios && t.inputType == "deleteContentForward" && e.observer.flushSoon(), P.safari && t.inputType == "insertText" && e.inputState.composing >= 0 && setTimeout(() => z.compositionend(e, t), 20), !1;
};
var Ya = /*@__PURE__*/ new Set();
function Xa(e) {
	Ya.has(e) || (Ya.add(e), e.addEventListener("copy", () => {}), e.addEventListener("cut", () => {}));
}
var Za = [
	"pre-wrap",
	"normal",
	"pre-line",
	"break-spaces"
], Qa = !1;
function $a() {
	Qa = !1;
}
var eo = class {
	constructor(e) {
		this.lineWrapping = e, this.doc = T.empty, this.heightSamples = {}, this.lineHeight = 14, this.charWidth = 7, this.textHeight = 14, this.lineLength = 30;
	}
	heightForGap(e, t) {
		let n = this.doc.lineAt(t).number - this.doc.lineAt(e).number + 1;
		return this.lineWrapping && (n += Math.max(0, Math.ceil((t - e - n * this.lineLength * .5) / this.lineLength))), this.lineHeight * n;
	}
	heightForLine(e) {
		return this.lineWrapping ? (1 + Math.max(0, Math.ceil((e - this.lineLength) / Math.max(1, this.lineLength - 5)))) * this.lineHeight : this.lineHeight;
	}
	setDoc(e) {
		return this.doc = e, this;
	}
	mustRefreshForWrapping(e) {
		return Za.indexOf(e) > -1 != this.lineWrapping;
	}
	mustRefreshForHeights(e) {
		let t = !1;
		for (let n = 0; n < e.length; n++) {
			let r = e[n];
			r < 0 ? n++ : this.heightSamples[Math.floor(r * 10)] || (t = !0, this.heightSamples[Math.floor(r * 10)] = !0);
		}
		return t;
	}
	refresh(e, t, n, r, i, a) {
		let o = Za.indexOf(e) > -1, s = Math.abs(t - this.lineHeight) > .3 || this.lineWrapping != o;
		if (this.lineWrapping = o, this.lineHeight = t, this.charWidth = n, this.textHeight = r, this.lineLength = i, s) {
			this.heightSamples = {};
			for (let e = 0; e < a.length; e++) {
				let t = a[e];
				t < 0 ? e++ : this.heightSamples[Math.floor(t * 10)] = !0;
			}
		}
		return s;
	}
}, to = class {
	constructor(e, t) {
		this.from = e, this.heights = t, this.index = 0;
	}
	get more() {
		return this.index < this.heights.length;
	}
}, no = class e {
	constructor(e, t, n, r, i) {
		this.from = e, this.length = t, this.top = n, this.height = r, this._content = i;
	}
	get type() {
		return typeof this._content == "number" ? vn.Text : Array.isArray(this._content) ? this._content : this._content.type;
	}
	get to() {
		return this.from + this.length;
	}
	get bottom() {
		return this.top + this.height;
	}
	get widget() {
		return this._content instanceof xn ? this._content.widget : null;
	}
	get widgetLineBreaks() {
		return typeof this._content == "number" ? this._content : 0;
	}
	join(t) {
		let n = (Array.isArray(this._content) ? this._content : [this]).concat(Array.isArray(t._content) ? t._content : [t]);
		return new e(this.from, this.length + t.length, this.top, this.height + t.height, n);
	}
}, B = /*@__PURE__*/ (function(e) {
	return e[e.ByPos = 0] = "ByPos", e[e.ByHeight = 1] = "ByHeight", e[e.ByPosNoHeight = 2] = "ByPosNoHeight", e;
})(B ||= {}), ro = .001, io = class e {
	constructor(e, t, n = 2) {
		this.length = e, this.height = t, this.flags = n;
	}
	get outdated() {
		return (this.flags & 2) > 0;
	}
	set outdated(e) {
		this.flags = (e ? 2 : 0) | this.flags & -3;
	}
	setHeight(e) {
		this.height != e && (Math.abs(this.height - e) > ro && (Qa = !0), this.height = e);
	}
	replace(t, n, r) {
		return e.of(r);
	}
	decomposeLeft(e, t) {
		t.push(this);
	}
	decomposeRight(e, t) {
		t.push(this);
	}
	applyChanges(e, t, n, r) {
		let i = this, a = n.doc;
		for (let o = r.length - 1; o >= 0; o--) {
			let { fromA: s, toA: c, fromB: l, toB: u } = r[o], d = i.lineAt(s, B.ByPosNoHeight, n.setDoc(t), 0, 0), f = d.to >= c ? d : i.lineAt(c, B.ByPosNoHeight, n, 0, 0);
			for (u += f.to - c, c = f.to; o > 0 && d.from <= r[o - 1].toA;) s = r[o - 1].fromA, l = r[o - 1].fromB, o--, s < d.from && (d = i.lineAt(s, B.ByPosNoHeight, n, 0, 0));
			l += d.from - s, s = d.from;
			let p = mo.build(n.setDoc(a), e, l, u);
			i = ao(i, i.replace(s, c, p));
		}
		return i.updateHeight(n, 0);
	}
	static empty() {
		return new co(0, 0, 0);
	}
	static of(t) {
		if (t.length == 1) return t[0];
		let n = 0, r = t.length, i = 0, a = 0;
		for (;;) if (n == r) {
			if (i > a * 2) {
				let e = t[n - 1];
				e.break ? t.splice(--n, 1, e.left, null, e.right) : t.splice(--n, 1, e.left, e.right), r += 1 + e.break, i -= e.size;
			} else if (a > i * 2) {
				let e = t[r];
				e.break ? t.splice(r, 1, e.left, null, e.right) : t.splice(r, 1, e.left, e.right), r += 2 + e.break, a -= e.size;
			} else break;
		} else if (i < a) {
			let e = t[n++];
			e && (i += e.size);
		} else {
			let e = t[--r];
			e && (a += e.size);
		}
		let o = 0;
		return t[n - 1] == null ? (o = 1, n--) : t[n] ?? (o = 1, r++), new uo(e.of(t.slice(0, n)), o, e.of(t.slice(r)));
	}
};
function ao(e, t) {
	return e == t ? e : (e.constructor != t.constructor && (Qa = !0), t);
}
io.prototype.size = 1;
var oo = /*@__PURE__*/ F.replace({}), so = class extends io {
	constructor(e, t, n) {
		super(e, t), this.deco = n, this.spaceAbove = 0;
	}
	mainBlock(e, t) {
		return new no(t, this.length, e + this.spaceAbove, this.height - this.spaceAbove, this.deco || 0);
	}
	blockAt(e, t, n, r) {
		return this.spaceAbove && e < n + this.spaceAbove ? new no(r, 0, n, this.spaceAbove, oo) : this.mainBlock(n, r);
	}
	lineAt(e, t, n, r, i) {
		let a = this.mainBlock(r, i);
		return this.spaceAbove ? this.blockAt(0, n, r, i).join(a) : a;
	}
	forEachLine(e, t, n, r, i, a) {
		e <= i + this.length && t >= i && a(this.lineAt(0, B.ByPos, n, r, i));
	}
	setMeasuredHeight(e) {
		let t = e.heights[e.index++];
		t < 0 ? (this.spaceAbove = -t, t = e.heights[e.index++]) : this.spaceAbove = 0, this.setHeight(t);
	}
	updateHeight(e, t = 0, n = !1, r) {
		return r && r.from <= t && r.more && this.setMeasuredHeight(r), this.outdated = !1, this;
	}
	toString() {
		return `block(${this.length})`;
	}
}, co = class e extends so {
	constructor(e, t, n) {
		super(e, t, null), this.collapsed = 0, this.widgetHeight = 0, this.breaks = 0, this.spaceAbove = n;
	}
	mainBlock(e, t) {
		return new no(t, this.length, e + this.spaceAbove, this.height - this.spaceAbove, this.breaks);
	}
	replace(t, n, r) {
		let i = r[0];
		return r.length == 1 && (i instanceof e || i instanceof lo && i.flags & 4) && Math.abs(this.length - i.length) < 10 ? (i instanceof lo ? i = new e(i.length, this.height, this.spaceAbove) : i.height = this.height, this.outdated || (i.outdated = !1), i) : io.of(r);
	}
	updateHeight(e, t = 0, n = !1, r) {
		return r && r.from <= t && r.more ? this.setMeasuredHeight(r) : (n || this.outdated) && (this.spaceAbove = 0, this.setHeight(Math.max(this.widgetHeight, e.heightForLine(this.length - this.collapsed)) + this.breaks * e.lineHeight)), this.outdated = !1, this;
	}
	toString() {
		return `line(${this.length}${this.collapsed ? -this.collapsed : ""}${this.widgetHeight ? ":" + this.widgetHeight : ""})`;
	}
}, lo = class e extends io {
	constructor(e) {
		super(e, 0);
	}
	heightMetrics(e, t) {
		let n = e.doc.lineAt(t).number, r = e.doc.lineAt(t + this.length).number, i = r - n + 1, a, o = 0;
		if (e.lineWrapping) {
			let t = Math.min(this.height, e.lineHeight * i);
			a = t / i, this.length > i + 1 && (o = (this.height - t) / (this.length - i - 1));
		} else a = this.height / i;
		return {
			firstLine: n,
			lastLine: r,
			perLine: a,
			perChar: o
		};
	}
	blockAt(e, t, n, r) {
		let { firstLine: i, lastLine: a, perLine: o, perChar: s } = this.heightMetrics(t, r);
		if (t.lineWrapping) {
			let i = r + (e < t.lineHeight ? 0 : Math.round(Math.max(0, Math.min(1, (e - n) / this.height)) * this.length)), a = t.doc.lineAt(i), c = o + a.length * s, l = Math.max(n, e - c / 2);
			return new no(a.from, a.length, l, c, 0);
		}
		{
			let r = Math.max(0, Math.min(a - i, Math.floor((e - n) / o))), { from: s, length: c } = t.doc.line(i + r);
			return new no(s, c, n + o * r, o, 0);
		}
	}
	lineAt(e, t, n, r, i) {
		if (t == B.ByHeight) return this.blockAt(e, n, r, i);
		if (t == B.ByPosNoHeight) {
			let { from: t, to: r } = n.doc.lineAt(e);
			return new no(t, r - t, 0, 0, 0);
		}
		let { firstLine: a, perLine: o, perChar: s } = this.heightMetrics(n, i), c = n.doc.lineAt(e), l = o + c.length * s, u = c.number - a, d = r + o * u + s * (c.from - i - u);
		return new no(c.from, c.length, Math.max(r, Math.min(d, r + this.height - l)), l, 0);
	}
	forEachLine(e, t, n, r, i, a) {
		e = Math.max(e, i), t = Math.min(t, i + this.length);
		let { firstLine: o, perLine: s, perChar: c } = this.heightMetrics(n, i);
		for (let l = e, u = r; l <= t;) {
			let t = n.doc.lineAt(l);
			if (l == e) {
				let n = t.number - o;
				u += s * n + c * (e - i - n);
			}
			let r = s + c * t.length;
			a(new no(t.from, t.length, u, r, 0)), u += r, l = t.to + 1;
		}
	}
	replace(t, n, r) {
		let i = this.length - n;
		if (i > 0) {
			let t = r[r.length - 1];
			t instanceof e ? r[r.length - 1] = new e(t.length + i) : r.push(null, new e(i - 1));
		}
		if (t > 0) {
			let n = r[0];
			n instanceof e ? r[0] = new e(t + n.length) : r.unshift(new e(t - 1), null);
		}
		return io.of(r);
	}
	decomposeLeft(t, n) {
		n.push(new e(t - 1), null);
	}
	decomposeRight(t, n) {
		n.push(null, new e(this.length - t - 1));
	}
	updateHeight(t, n = 0, r = !1, i) {
		let a = n + this.length;
		if (i && i.from <= n + this.length && i.more) {
			let r = [], o = Math.max(n, i.from), s = -1;
			for (i.from > n && r.push(new e(i.from - n - 1).updateHeight(t, n)); o <= a && i.more;) {
				let e = t.doc.lineAt(o).length;
				r.length && r.push(null);
				let n = i.heights[i.index++], a = 0;
				n < 0 && (a = -n, n = i.heights[i.index++]), s == -1 ? s = n : Math.abs(n - s) >= ro && (s = -2);
				let c = new co(e, n, a);
				c.outdated = !1, r.push(c), o += e + 1;
			}
			o <= a && r.push(null, new e(a - o).updateHeight(t, o));
			let c = io.of(r);
			return (s < 0 || Math.abs(c.height - this.height) >= ro || Math.abs(s - this.heightMetrics(t, n).perLine) >= ro) && (Qa = !0), ao(this, c);
		}
		return (r || this.outdated) && (this.setHeight(t.heightForGap(n, n + this.length)), this.outdated = !1), this;
	}
	toString() {
		return `gap(${this.length})`;
	}
}, uo = class extends io {
	constructor(e, t, n) {
		super(e.length + t + n.length, e.height + n.height, t | (e.outdated || n.outdated ? 2 : 0)), this.left = e, this.right = n, this.size = e.size + n.size;
	}
	get break() {
		return this.flags & 1;
	}
	blockAt(e, t, n, r) {
		let i = n + this.left.height;
		return e < i ? this.left.blockAt(e, t, n, r) : this.right.blockAt(e, t, i, r + this.left.length + this.break);
	}
	lineAt(e, t, n, r, i) {
		let a = r + this.left.height, o = i + this.left.length + this.break, s = t == B.ByHeight ? e < a : e < o, c = s ? this.left.lineAt(e, t, n, r, i) : this.right.lineAt(e, t, n, a, o);
		if (this.break || (s ? c.to < o : c.from > o)) return c;
		let l = t == B.ByPosNoHeight ? B.ByPosNoHeight : B.ByPos;
		return s ? c.join(this.right.lineAt(o, l, n, a, o)) : this.left.lineAt(o, l, n, r, i).join(c);
	}
	forEachLine(e, t, n, r, i, a) {
		let o = r + this.left.height, s = i + this.left.length + this.break;
		if (this.break) e < s && this.left.forEachLine(e, t, n, r, i, a), t >= s && this.right.forEachLine(e, t, n, o, s, a);
		else {
			let c = this.lineAt(s, B.ByPos, n, r, i);
			e < c.from && this.left.forEachLine(e, c.from - 1, n, r, i, a), c.to >= e && c.from <= t && a(c), t > c.to && this.right.forEachLine(c.to + 1, t, n, o, s, a);
		}
	}
	replace(e, t, n) {
		let r = this.left.length + this.break;
		if (t < r) return this.balanced(this.left.replace(e, t, n), this.right);
		if (e > this.left.length) return this.balanced(this.left, this.right.replace(e - r, t - r, n));
		let i = [];
		e > 0 && this.decomposeLeft(e, i);
		let a = i.length;
		for (let e of n) i.push(e);
		if (e > 0 && fo(i, a - 1), t < this.length) {
			let e = i.length;
			this.decomposeRight(t, i), fo(i, e);
		}
		return io.of(i);
	}
	decomposeLeft(e, t) {
		let n = this.left.length;
		if (e <= n) return this.left.decomposeLeft(e, t);
		t.push(this.left), this.break && (n++, e >= n && t.push(null)), e > n && this.right.decomposeLeft(e - n, t);
	}
	decomposeRight(e, t) {
		let n = this.left.length, r = n + this.break;
		if (e >= r) return this.right.decomposeRight(e - r, t);
		e < n && this.left.decomposeRight(e, t), this.break && e < r && t.push(null), t.push(this.right);
	}
	balanced(e, t) {
		return e.size > 2 * t.size || t.size > 2 * e.size ? io.of(this.break ? [
			e,
			null,
			t
		] : [e, t]) : (this.left = ao(this.left, e), this.right = ao(this.right, t), this.setHeight(e.height + t.height), this.outdated = e.outdated || t.outdated, this.size = e.size + t.size, this.length = e.length + this.break + t.length, this);
	}
	updateHeight(e, t = 0, n = !1, r) {
		let { left: i, right: a } = this, o = t + i.length + this.break, s = null;
		return r && r.from <= t + i.length && r.more ? s = i = i.updateHeight(e, t, n, r) : i.updateHeight(e, t, n), r && r.from <= o + a.length && r.more ? s = a = a.updateHeight(e, o, n, r) : a.updateHeight(e, o, n), s ? this.balanced(i, a) : (this.height = this.left.height + this.right.height, this.outdated = !1, this);
	}
	toString() {
		return this.left + (this.break ? " " : "-") + this.right;
	}
};
function fo(e, t) {
	let n, r;
	e[t] == null && (n = e[t - 1]) instanceof lo && (r = e[t + 1]) instanceof lo && e.splice(t - 1, 3, new lo(n.length + 1 + r.length));
}
var po = 5, mo = class e {
	constructor(e, t) {
		this.pos = e, this.oracle = t, this.nodes = [], this.lineStart = -1, this.lineEnd = -1, this.covering = null, this.writtenTo = e;
	}
	get isCovered() {
		return this.covering && this.nodes[this.nodes.length - 1] == this.covering;
	}
	span(e, t) {
		if (this.lineStart > -1) {
			let e = Math.min(t, this.lineEnd), n = this.nodes[this.nodes.length - 1];
			n instanceof co ? n.length += e - this.pos : (e > this.pos || !this.isCovered) && this.nodes.push(new co(e - this.pos, -1, 0)), this.writtenTo = e, t > e && (this.nodes.push(null), this.writtenTo++, this.lineStart = -1);
		}
		this.pos = t;
	}
	point(e, t, n) {
		if (e < t || n.heightRelevant) {
			let r = n.widget ? n.widget.estimatedHeight : 0, i = n.widget ? n.widget.lineBreaks : 0;
			r < 0 && (r = this.oracle.lineHeight);
			let a = t - e;
			n.block ? this.addBlock(new so(a, r, n)) : (a || i || r >= po) && this.addLineDeco(r, i, a);
		} else t > e && this.span(e, t);
		this.lineEnd > -1 && this.lineEnd < this.pos && (this.lineEnd = this.oracle.doc.lineAt(this.pos).to);
	}
	enterLine() {
		if (this.lineStart > -1) return;
		let { from: e, to: t } = this.oracle.doc.lineAt(this.pos);
		this.lineStart = e, this.lineEnd = t, this.writtenTo < e && ((this.writtenTo < e - 1 || this.nodes[this.nodes.length - 1] == null) && this.nodes.push(this.blankContent(this.writtenTo, e - 1)), this.nodes.push(null)), this.pos > e && this.nodes.push(new co(this.pos - e, -1, 0)), this.writtenTo = this.pos;
	}
	blankContent(e, t) {
		let n = new lo(t - e);
		return this.oracle.doc.lineAt(e).to == t && (n.flags |= 4), n;
	}
	ensureLine() {
		this.enterLine();
		let e = this.nodes.length ? this.nodes[this.nodes.length - 1] : null;
		if (e instanceof co) return e;
		let t = new co(0, -1, 0);
		return this.nodes.push(t), t;
	}
	addBlock(e) {
		this.enterLine();
		let t = e.deco;
		t && t.startSide > 0 && !this.isCovered && this.ensureLine(), this.nodes.push(e), this.writtenTo = this.pos += e.length, t && t.endSide > 0 && (this.covering = e);
	}
	addLineDeco(e, t, n) {
		let r = this.ensureLine();
		r.length += n, r.collapsed += n, r.widgetHeight = Math.max(r.widgetHeight, e), r.breaks += t, this.writtenTo = this.pos += n;
	}
	finish(e) {
		let t = this.nodes.length == 0 ? null : this.nodes[this.nodes.length - 1];
		this.lineStart > -1 && !(t instanceof co) && !this.isCovered ? this.nodes.push(new co(0, -1, 0)) : (this.writtenTo < this.pos || t == null) && this.nodes.push(this.blankContent(this.writtenTo, this.pos));
		let n = e;
		for (let e of this.nodes) e instanceof co && e.updateHeight(this.oracle, n), n += e ? e.length : 1;
		return this.nodes;
	}
	static build(t, n, r, i) {
		let a = new e(r, t);
		return N.spans(n, r, i, a, 0), a.finish(r);
	}
};
function ho(e, t, n) {
	let r = new go();
	return N.compare(e, t, n, r, 0), r.changes;
}
var go = class {
	constructor() {
		this.changes = [];
	}
	compareRange() {}
	comparePoint(e, t, n, r) {
		(e < t || n && n.heightRelevant || r && r.heightRelevant) && wn(e, t, this.changes, 5);
	}
};
function _o(e, t) {
	let n = e.getBoundingClientRect(), r = e.ownerDocument, i = r.defaultView || window, a = Math.max(0, n.left), o = Math.min(i.innerWidth, n.right), s = Math.max(0, n.top), c = Math.min(i.innerHeight, n.bottom);
	for (let t = e.parentNode; t && t != r.body;) if (t.nodeType == 1) {
		let n = t, r = window.getComputedStyle(n);
		if ((n.scrollHeight > n.clientHeight || n.scrollWidth > n.clientWidth) && r.overflow != "visible") {
			let r = n.getBoundingClientRect();
			a = Math.max(a, r.left), o = Math.min(o, r.right), s = Math.max(s, r.top), c = Math.min(t == e.parentNode ? i.innerHeight : c, r.bottom);
		}
		t = r.position == "absolute" || r.position == "fixed" ? n.offsetParent : n.parentNode;
	} else if (t.nodeType == 11) t = t.host;
	else break;
	return {
		left: a - n.left,
		right: Math.max(a, o) - n.left,
		top: s - (n.top + t),
		bottom: Math.max(s, c) - (n.top + t)
	};
}
function vo(e) {
	let t = e.getBoundingClientRect(), n = e.ownerDocument.defaultView || window;
	return t.left < n.innerWidth && t.right > 0 && t.top < n.innerHeight && t.bottom > 0;
}
function yo(e, t) {
	let n = e.getBoundingClientRect();
	return {
		left: 0,
		right: n.right - n.left,
		top: t,
		bottom: n.bottom - (n.top + t)
	};
}
var bo = class {
	constructor(e, t, n, r) {
		this.from = e, this.to = t, this.size = n, this.displaySize = r;
	}
	static same(e, t) {
		if (e.length != t.length) return !1;
		for (let n = 0; n < e.length; n++) {
			let r = e[n], i = t[n];
			if (r.from != i.from || r.to != i.to || r.size != i.size) return !1;
		}
		return !0;
	}
	draw(e, t) {
		return F.replace({ widget: new xo(this.displaySize * (t ? e.scaleY : e.scaleX), t) }).range(this.from, this.to);
	}
}, xo = class extends _n {
	constructor(e, t) {
		super(), this.size = e, this.vertical = t;
	}
	eq(e) {
		return e.size == this.size && e.vertical == this.vertical;
	}
	toDOM() {
		let e = document.createElement("div");
		return this.vertical ? e.style.height = this.size + "px" : (e.style.width = this.size + "px", e.style.height = "2px", e.style.display = "inline-block"), e;
	}
	get estimatedHeight() {
		return this.vertical ? this.size : -1;
	}
}, So = class {
	constructor(e, t) {
		this.view = e, this.state = t, this.pixelViewport = {
			left: 0,
			right: window.innerWidth,
			top: 0,
			bottom: 0
		}, this.inView = !0, this.paddingTop = 0, this.paddingBottom = 0, this.contentDOMWidth = 0, this.contentDOMHeight = 0, this.editorHeight = 0, this.editorWidth = 0, this.scaleX = 1, this.scaleY = 1, this.scrollOffset = 0, this.scrolledToBottom = !1, this.scrollAnchorPos = 0, this.scrollAnchorHeight = -1, this.scaler = Oo, this.scrollTarget = null, this.printing = !1, this.mustMeasureContent = !0, this.defaultTextDirection = I.LTR, this.visibleRanges = [], this.mustEnforceCursorAssoc = !1;
		let n = t.facet(Ur).some((e) => typeof e != "function" && e.class == "cm-lineWrapping");
		this.heightOracle = new eo(n), this.stateDeco = ko(t), this.heightMap = io.empty().applyChanges(this.stateDeco, T.empty, this.heightOracle.setDoc(t.doc), [new $r(0, 0, 0, t.doc.length)]);
		for (let e = 0; e < 2 && (this.viewport = this.getViewport(0, null), this.updateForViewport()); e++);
		this.updateViewportLines(), this.lineGaps = this.ensureLineGaps([]), this.lineGapDeco = F.set(this.lineGaps.map((e) => e.draw(this, !1))), this.scrollParent = e.scrollDOM, this.computeVisibleRanges();
	}
	updateForViewport() {
		let e = [this.viewport], { main: t } = this.state.selection;
		for (let n = 0; n <= 1; n++) {
			let r = n ? t.head : t.anchor;
			if (!e.some(({ from: e, to: t }) => r >= e && r <= t)) {
				let { from: t, to: n } = this.lineBlockAt(r);
				e.push(new Co(t, n));
			}
		}
		return this.viewports = e.sort((e, t) => e.from - t.from), this.updateScaler();
	}
	updateScaler() {
		let e = this.scaler;
		return this.scaler = this.heightMap.height <= 7e6 ? Oo : new Ao(this.heightOracle, this.heightMap, this.viewports), e.eq(this.scaler) ? 0 : 2;
	}
	updateViewportLines() {
		this.viewportLines = [], this.heightMap.forEachLine(this.viewport.from, this.viewport.to, this.heightOracle.setDoc(this.state.doc), 0, 0, (e) => {
			this.viewportLines.push(jo(e, this.scaler));
		});
	}
	update(e, t = null) {
		this.state = e.state;
		let n = this.stateDeco;
		this.stateDeco = ko(this.state);
		let r = e.changedRanges, i = $r.extendWithRanges(r, ho(n, this.stateDeco, e ? e.changes : Se.empty(this.state.doc.length))), a = this.heightMap.height, o = this.scrolledToBottom ? null : this.scrollAnchorAt(this.scrollOffset);
		$a(), this.heightMap = this.heightMap.applyChanges(this.stateDeco, e.startState.doc, this.heightOracle.setDoc(this.state.doc), i), (this.heightMap.height != a || Qa) && (e.flags |= 2), o ? (this.scrollAnchorPos = e.changes.mapPos(o.from, -1), this.scrollAnchorHeight = o.top) : (this.scrollAnchorPos = -1, this.scrollAnchorHeight = a);
		let s = i.length ? this.mapViewport(this.viewport, e.changes) : this.viewport;
		(t && (t.range.head < s.from || t.range.head > s.to) || !this.viewportIsAppropriate(s)) && (s = this.getViewport(0, t));
		let c = s.from != this.viewport.from || s.to != this.viewport.to;
		this.viewport = s, e.flags |= this.updateForViewport(), (c || !e.changes.empty || e.flags & 2) && this.updateViewportLines(), (this.lineGaps.length || this.viewport.to - this.viewport.from > 4e3) && this.updateLineGaps(this.ensureLineGaps(this.mapLineGaps(this.lineGaps, e.changes))), e.flags |= this.computeVisibleRanges(e.changes), t && (this.scrollTarget = t), !this.mustEnforceCursorAssoc && (e.selectionSet || e.focusChanged) && e.view.lineWrapping && e.state.selection.main.empty && e.state.selection.main.assoc && !e.state.facet(jr) && (this.mustEnforceCursorAssoc = !0);
	}
	measure() {
		let { view: e } = this, t = e.contentDOM, n = window.getComputedStyle(t), r = this.heightOracle, i = n.whiteSpace;
		this.defaultTextDirection = n.direction == "rtl" ? I.RTL : I.LTR;
		let a = this.heightOracle.mustRefreshForWrapping(i) || this.mustMeasureContent === "refresh", o = t.getBoundingClientRect(), s = a || this.mustMeasureContent || this.contentDOMHeight != o.height;
		this.contentDOMHeight = o.height, this.mustMeasureContent = !1;
		let c = 0, l = 0;
		if (o.width && o.height) {
			let { scaleX: e, scaleY: n } = Ln(t, o);
			(e > .005 && Math.abs(this.scaleX - e) > .005 || n > .005 && Math.abs(this.scaleY - n) > .005) && (this.scaleX = e, this.scaleY = n, c |= 16, a = s = !0);
		}
		let u = (parseInt(n.paddingTop) || 0) * this.scaleY, d = (parseInt(n.paddingBottom) || 0) * this.scaleY;
		(this.paddingTop != u || this.paddingBottom != d) && (this.paddingTop = u, this.paddingBottom = d, c |= 18), this.editorWidth != e.scrollDOM.clientWidth && (r.lineWrapping && (s = !0), this.editorWidth = e.scrollDOM.clientWidth, c |= 16);
		let f = zn(this.view.contentDOM, !1).y;
		f != this.scrollParent && (this.scrollParent = f, this.scrollAnchorHeight = -1, this.scrollOffset = 0);
		let p = this.getScrollOffset();
		this.scrollOffset != p && (this.scrollAnchorHeight = -1, this.scrollOffset = p), this.scrolledToBottom = Xn(this.scrollParent || e.win);
		let m = (this.printing ? yo : _o)(t, this.paddingTop), h = m.top - this.pixelViewport.top, g = m.bottom - this.pixelViewport.bottom;
		this.pixelViewport = m;
		let _ = this.pixelViewport.bottom > this.pixelViewport.top && this.pixelViewport.right > this.pixelViewport.left;
		if (_ != this.inView && (this.inView = _, _ && (s = !0)), !this.inView && !this.scrollTarget && !vo(e.dom)) return 0;
		let v = o.width;
		if ((this.contentDOMWidth != v || this.editorHeight != e.scrollDOM.clientHeight) && (this.contentDOMWidth = o.width, this.editorHeight = e.scrollDOM.clientHeight, c |= 16), s) {
			let t = e.docView.measureVisibleLineHeights(this.viewport);
			if (r.mustRefreshForHeights(t) && (a = !0), a || r.lineWrapping && Math.abs(v - this.contentDOMWidth) > r.charWidth) {
				let { lineHeight: n, charWidth: o, textHeight: s } = e.docView.measureTextSize();
				a = n > 0 && r.refresh(i, n, o, s, Math.max(5, v / o), t), a && (e.docView.minWidth = 0, c |= 16);
			}
			h > 0 && g > 0 ? l = Math.max(h, g) : h < 0 && g < 0 && (l = Math.min(h, g)), $a();
			for (let n of this.viewports) {
				let i = n.from == this.viewport.from ? t : e.docView.measureVisibleLineHeights(n);
				this.heightMap = (a ? io.empty().applyChanges(this.stateDeco, T.empty, this.heightOracle, [new $r(0, 0, 0, e.state.doc.length)]) : this.heightMap).updateHeight(r, 0, a, new to(n.from, i));
			}
			Qa && (c |= 2);
		}
		let y = !this.viewportIsAppropriate(this.viewport, l) || this.scrollTarget && (this.scrollTarget.range.head < this.viewport.from || this.scrollTarget.range.head > this.viewport.to);
		return y && (c & 2 && (c |= this.updateScaler()), this.viewport = this.getViewport(l, this.scrollTarget), c |= this.updateForViewport()), (c & 2 || y) && this.updateViewportLines(), (this.lineGaps.length || this.viewport.to - this.viewport.from > 4e3) && this.updateLineGaps(this.ensureLineGaps(a ? [] : this.lineGaps, e)), c |= this.computeVisibleRanges(), this.mustEnforceCursorAssoc && (this.mustEnforceCursorAssoc = !1, e.docView.enforceCursorAssoc()), c;
	}
	get visibleTop() {
		return this.scaler.fromDOM(this.pixelViewport.top);
	}
	get visibleBottom() {
		return this.scaler.fromDOM(this.pixelViewport.bottom);
	}
	getViewport(e, t) {
		let n = .5 - Math.max(-.5, Math.min(.5, e / 1e3 / 2)), r = this.heightMap, i = this.heightOracle, { visibleTop: a, visibleBottom: o } = this, s = new Co(r.lineAt(a - n * 1e3, B.ByHeight, i, 0, 0).from, r.lineAt(o + (1 - n) * 1e3, B.ByHeight, i, 0, 0).to);
		if (t) {
			let { head: e } = t.range;
			if (e < s.from || e > s.to) {
				let n = Math.min(this.editorHeight, this.pixelViewport.bottom - this.pixelViewport.top), a = r.lineAt(e, B.ByPos, i, 0, 0), o;
				o = t.y == "center" ? (a.top + a.bottom) / 2 - n / 2 : t.y == "start" || t.y == "nearest" && e < s.from ? a.top : a.bottom - n, s = new Co(r.lineAt(o - 500, B.ByHeight, i, 0, 0).from, r.lineAt(o + n + 500, B.ByHeight, i, 0, 0).to);
			}
		}
		return s;
	}
	mapViewport(e, t) {
		let n = t.mapPos(e.from, -1), r = t.mapPos(e.to, 1);
		return new Co(this.heightMap.lineAt(n, B.ByPos, this.heightOracle, 0, 0).from, this.heightMap.lineAt(r, B.ByPos, this.heightOracle, 0, 0).to);
	}
	viewportIsAppropriate({ from: e, to: t }, n = 0) {
		if (!this.inView) return !0;
		let { top: r } = this.heightMap.lineAt(e, B.ByPos, this.heightOracle, 0, 0), { bottom: i } = this.heightMap.lineAt(t, B.ByPos, this.heightOracle, 0, 0), { visibleTop: a, visibleBottom: o } = this;
		return (e == 0 || r <= a - Math.max(10, Math.min(-n, 250))) && (t == this.state.doc.length || i >= o + Math.max(10, Math.min(n, 250))) && r > a - 2e3 && i < o + 2e3;
	}
	mapLineGaps(e, t) {
		if (!e.length || t.empty) return e;
		let n = [];
		for (let r of e) t.touchesRange(r.from, r.to) || n.push(new bo(t.mapPos(r.from), t.mapPos(r.to), r.size, r.displaySize));
		return n;
	}
	ensureLineGaps(e, t) {
		let n = this.heightOracle.lineWrapping, r = n ? 1e4 : 2e3, i = r >> 1, a = r << 1;
		if (this.defaultTextDirection != I.LTR && !n) return [];
		let o = [], s = (r, a, c, l) => {
			if (a - r < i) return;
			let u = this.state.selection.main, d = [u.from];
			u.empty || d.push(u.to);
			for (let e of d) if (e > r && e < a) {
				s(r, e - 10, c, l), s(e + 10, a, c, l);
				return;
			}
			let f = Do(e, (e) => e.from >= c.from && e.to <= c.to && Math.abs(e.from - r) < i && Math.abs(e.to - a) < i && !d.some((t) => e.from < t && e.to > t));
			if (!f) {
				if (a < c.to && t && n && t.visibleRanges.some((e) => e.from <= a && e.to >= a)) {
					let e = t.moveToLineBoundary(k.cursor(a), !1, !0).head;
					e > r && (a = e);
				}
				let e = this.gapSize(c, r, a, l);
				f = new bo(r, a, e, n || e < 2e6 ? e : 2e6);
			}
			o.push(f);
		}, c = (t) => {
			if (t.length < a || t.type != vn.Text) return;
			let i = wo(t.from, t.to, this.stateDeco);
			if (i.total < a) return;
			let o = this.scrollTarget ? this.scrollTarget.range.head : null, c, l;
			if (n) {
				let e = r / this.heightOracle.lineLength * this.heightOracle.lineHeight, n, a;
				if (o != null) {
					let r = Eo(i, o), s = ((this.visibleBottom - this.visibleTop) / 2 + e) / t.height;
					n = r - s, a = r + s;
				} else n = (this.visibleTop - t.top - e) / t.height, a = (this.visibleBottom - t.top + e) / t.height;
				c = To(i, n), l = To(i, a);
			} else {
				let n = i.total * this.heightOracle.charWidth, a = r * this.heightOracle.charWidth, s = 0;
				if (n > 2e6) for (let n of e) n.from >= t.from && n.from < t.to && n.size != n.displaySize && n.from * this.heightOracle.charWidth + s < this.pixelViewport.left && (s = n.size - n.displaySize);
				let u = this.pixelViewport.left + s, d = this.pixelViewport.right + s, f, p;
				if (o != null) {
					let e = Eo(i, o), t = ((d - u) / 2 + a) / n;
					f = e - t, p = e + t;
				} else f = (u - a) / n, p = (d + a) / n;
				c = To(i, f), l = To(i, p);
			}
			c > t.from && s(t.from, c, t, i), l < t.to && s(l, t.to, t, i);
		};
		for (let e of this.viewportLines) Array.isArray(e.type) ? e.type.forEach(c) : c(e);
		return o;
	}
	gapSize(e, t, n, r) {
		let i = Eo(r, n) - Eo(r, t);
		return this.heightOracle.lineWrapping ? e.height * i : r.total * this.heightOracle.charWidth * i;
	}
	updateLineGaps(e) {
		bo.same(e, this.lineGaps) || (this.lineGaps = e, this.lineGapDeco = F.set(e.map((e) => e.draw(this, this.heightOracle.lineWrapping))));
	}
	computeVisibleRanges(e) {
		let t = this.stateDeco;
		this.lineGaps.length && (t = t.concat(this.lineGapDeco));
		let n = [];
		N.spans(t, this.viewport.from, this.viewport.to, {
			span(e, t) {
				n.push({
					from: e,
					to: t
				});
			},
			point() {}
		}, 20);
		let r = 0;
		if (n.length != this.visibleRanges.length) r = 12;
		else for (let t = 0; t < n.length && !(r & 8); t++) {
			let i = this.visibleRanges[t], a = n[t];
			(i.from != a.from || i.to != a.to) && (r |= 4, e && e.mapPos(i.from, -1) == a.from && e.mapPos(i.to, 1) == a.to || (r |= 8));
		}
		return this.visibleRanges = n, r;
	}
	lineBlockAt(e) {
		return e >= this.viewport.from && e <= this.viewport.to && this.viewportLines.find((t) => t.from <= e && t.to >= e) || jo(this.heightMap.lineAt(e, B.ByPos, this.heightOracle, 0, 0), this.scaler);
	}
	lineBlockAtHeight(e) {
		return e >= this.viewportLines[0].top && e <= this.viewportLines[this.viewportLines.length - 1].bottom && this.viewportLines.find((t) => t.top <= e && t.bottom >= e) || jo(this.heightMap.lineAt(this.scaler.fromDOM(e), B.ByHeight, this.heightOracle, 0, 0), this.scaler);
	}
	getScrollOffset() {
		return this.scrollParent == this.view.scrollDOM ? this.scrollParent.scrollTop * this.scaleY : (this.scrollParent ? this.scrollParent.getBoundingClientRect().top : 0) - this.view.contentDOM.getBoundingClientRect().top;
	}
	scrollAnchorAt(e) {
		let t = this.lineBlockAtHeight(e + 8);
		return t.from >= this.viewport.from || this.viewportLines[0].top - e > 200 ? t : this.viewportLines[0];
	}
	elementAtHeight(e) {
		return jo(this.heightMap.blockAt(this.scaler.fromDOM(e), this.heightOracle, 0, 0), this.scaler);
	}
	get docHeight() {
		return this.scaler.toDOM(this.heightMap.height);
	}
	get contentHeight() {
		return this.docHeight + this.paddingTop + this.paddingBottom;
	}
}, Co = class {
	constructor(e, t) {
		this.from = e, this.to = t;
	}
};
function wo(e, t, n) {
	let r = [], i = e, a = 0;
	return N.spans(n, e, t, {
		span() {},
		point(e, t) {
			e > i && (r.push({
				from: i,
				to: e
			}), a += e - i), i = t;
		}
	}, 20), i < t && (r.push({
		from: i,
		to: t
	}), a += t - i), {
		total: a,
		ranges: r
	};
}
function To({ total: e, ranges: t }, n) {
	if (n <= 0) return t[0].from;
	if (n >= 1) return t[t.length - 1].to;
	let r = Math.floor(e * n);
	for (let e = 0;; e++) {
		let { from: n, to: i } = t[e], a = i - n;
		if (r <= a) return n + r;
		r -= a;
	}
}
function Eo(e, t) {
	let n = 0;
	for (let { from: r, to: i } of e.ranges) {
		if (t <= i) {
			n += t - r;
			break;
		}
		n += i - r;
	}
	return n / e.total;
}
function Do(e, t) {
	for (let n of e) if (t(n)) return n;
}
var Oo = {
	toDOM(e) {
		return e;
	},
	fromDOM(e) {
		return e;
	},
	scale: 1,
	eq(e) {
		return e == this;
	}
};
function ko(e) {
	let t = e.facet(Wr).filter((e) => typeof e != "function"), n = e.facet(Kr).filter((e) => typeof e != "function");
	return n.length && t.push(N.join(n)), t;
}
var Ao = class e {
	constructor(e, t, n) {
		let r = 0, i = 0, a = 0;
		this.viewports = n.map(({ from: n, to: i }) => {
			let a = t.lineAt(n, B.ByPos, e, 0, 0).top, o = t.lineAt(i, B.ByPos, e, 0, 0).bottom;
			return r += o - a, {
				from: n,
				to: i,
				top: a,
				bottom: o,
				domTop: 0,
				domBottom: 0
			};
		}), this.scale = (7e6 - r) / (t.height - r);
		for (let e of this.viewports) e.domTop = a + (e.top - i) * this.scale, a = e.domBottom = e.domTop + (e.bottom - e.top), i = e.bottom;
	}
	toDOM(e) {
		for (let t = 0, n = 0, r = 0;; t++) {
			let i = t < this.viewports.length ? this.viewports[t] : null;
			if (!i || e < i.top) return r + (e - n) * this.scale;
			if (e <= i.bottom) return i.domTop + (e - i.top);
			n = i.bottom, r = i.domBottom;
		}
	}
	fromDOM(e) {
		for (let t = 0, n = 0, r = 0;; t++) {
			let i = t < this.viewports.length ? this.viewports[t] : null;
			if (!i || e < i.domTop) return n + (e - r) / this.scale;
			if (e <= i.domBottom) return i.top + (e - i.domTop);
			n = i.bottom, r = i.domBottom;
		}
	}
	eq(t) {
		return t instanceof e && this.scale == t.scale && this.viewports.length == t.viewports.length && this.viewports.every((e, n) => e.from == t.viewports[n].from && e.to == t.viewports[n].to);
	}
};
function jo(e, t) {
	if (t.scale == 1) return e;
	let n = t.toDOM(e.top), r = t.toDOM(e.bottom);
	return new no(e.from, e.length, n, r - n, Array.isArray(e._content) ? e._content.map((e) => jo(e, t)) : e._content);
}
var Mo = /*@__PURE__*/ A.define({ combine: (e) => e.join(" ") }), No = /*@__PURE__*/ A.define({ combine: (e) => e.indexOf(!0) > -1 }), Po = /*@__PURE__*/ Ut.newName(), Fo = /*@__PURE__*/ Ut.newName(), Io = /*@__PURE__*/ Ut.newName(), Lo = {
	"&light": "." + Fo,
	"&dark": "." + Io
};
function Ro(e, t, n) {
	return new Ut(t, { finish(t) {
		return /&/.test(t) ? t.replace(/&\w*/, (t) => {
			if (t == "&") return e;
			if (!n || !n[t]) throw RangeError(`Unsupported selector: ${t}`);
			return n[t];
		}) : e + " " + t;
	} });
}
var zo = /*@__PURE__*/ Ro("." + Po, {
	"&": {
		position: "relative !important",
		boxSizing: "border-box",
		"&.cm-focused": { outline: "1px dotted #212121" },
		display: "flex !important",
		flexDirection: "column"
	},
	".cm-scroller": {
		display: "flex !important",
		alignItems: "flex-start !important",
		fontFamily: "monospace",
		lineHeight: 1.4,
		height: "100%",
		overflowX: "auto",
		position: "relative",
		zIndex: 0,
		overflowAnchor: "none"
	},
	".cm-content": {
		margin: 0,
		flexGrow: 2,
		flexShrink: 0,
		display: "block",
		whiteSpace: "pre",
		wordWrap: "normal",
		boxSizing: "border-box",
		minHeight: "100%",
		padding: "4px 0",
		outline: "none",
		"&[contenteditable=true]": { WebkitUserModify: "read-write-plaintext-only" }
	},
	".cm-lineWrapping": {
		whiteSpace_fallback: "pre-wrap",
		whiteSpace: "break-spaces",
		wordBreak: "break-word",
		overflowWrap: "anywhere",
		flexShrink: 1
	},
	"&light .cm-content": { caretColor: "black" },
	"&dark .cm-content": { caretColor: "white" },
	".cm-line": {
		display: "block",
		padding: "0 2px 0 6px"
	},
	".cm-layer": {
		userSelect: "none",
		position: "absolute",
		left: 0,
		top: 0,
		contain: "size style",
		"& > *": { position: "absolute" }
	},
	"&light .cm-selectionBackground": { background: "#d9d9d9" },
	"&dark .cm-selectionBackground": { background: "#222" },
	"&light.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground": { background: "#d7d4f0" },
	"&dark.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground": { background: "#233" },
	".cm-cursorLayer": { pointerEvents: "none" },
	"&.cm-focused > .cm-scroller > .cm-cursorLayer": { animation: "steps(1) cm-blink 1.2s infinite" },
	"@keyframes cm-blink": {
		"0%": {},
		"50%": { opacity: 0 },
		"100%": {}
	},
	"@keyframes cm-blink2": {
		"0%": {},
		"50%": { opacity: 0 },
		"100%": {}
	},
	".cm-cursor, .cm-dropCursor": {
		borderLeft: "1.2px solid black",
		marginLeft: "-0.6px",
		pointerEvents: "none"
	},
	".cm-cursor": { display: "none" },
	"&dark .cm-cursor": { borderLeftColor: "#ddd" },
	".cm-selectionHandle": {
		backgroundColor: "currentColor",
		width: "1.5px"
	},
	".cm-selectionHandle-start::before, .cm-selectionHandle-end::before": {
		content: "\"\"",
		backgroundColor: "inherit",
		borderRadius: "50%",
		width: "8px",
		height: "8px",
		position: "absolute",
		left: "-3.25px"
	},
	".cm-selectionHandle-start::before": { top: "-8px" },
	".cm-selectionHandle-end::before": { bottom: "-8px" },
	".cm-dropCursor": { position: "absolute" },
	"&.cm-focused > .cm-scroller > .cm-cursorLayer .cm-cursor": { display: "block" },
	".cm-iso": { unicodeBidi: "isolate" },
	".cm-announced": {
		position: "fixed",
		top: "-10000px"
	},
	"@media print": { ".cm-announced": { display: "none" } },
	"&light .cm-activeLine": { backgroundColor: "#cceeff44" },
	"&dark .cm-activeLine": { backgroundColor: "#99eeff33" },
	"&light .cm-specialChar": { color: "red" },
	"&dark .cm-specialChar": { color: "#f78" },
	".cm-gutters": {
		flexShrink: 0,
		display: "flex",
		height: "100%",
		boxSizing: "border-box",
		zIndex: 200
	},
	".cm-gutters-before": { insetInlineStart: 0 },
	".cm-gutters-after": { insetInlineEnd: 0 },
	"&light .cm-gutters": {
		backgroundColor: "#f5f5f5",
		color: "#6c6c6c",
		border: "0px solid #ddd",
		"&.cm-gutters-before": { borderRightWidth: "1px" },
		"&.cm-gutters-after": { borderLeftWidth: "1px" }
	},
	"&dark .cm-gutters": {
		backgroundColor: "#333338",
		color: "#ccc"
	},
	".cm-gutter": {
		display: "flex !important",
		flexDirection: "column",
		flexShrink: 0,
		boxSizing: "border-box",
		minHeight: "100%",
		overflow: "hidden"
	},
	".cm-gutterElement": { boxSizing: "border-box" },
	".cm-lineNumbers .cm-gutterElement": {
		padding: "0 3px 0 5px",
		minWidth: "20px",
		textAlign: "right",
		whiteSpace: "nowrap"
	},
	"&light .cm-activeLineGutter": { backgroundColor: "#e2f2ff" },
	"&dark .cm-activeLineGutter": { backgroundColor: "#222227" },
	".cm-panels": {
		boxSizing: "border-box",
		position: "sticky",
		left: 0,
		right: 0,
		zIndex: 300
	},
	"&light .cm-panels": {
		backgroundColor: "#f5f5f5",
		color: "black"
	},
	".cm-panels-top": { top: "0" },
	".cm-panels-bottom": { bottom: "0" },
	"&light .cm-panels-top": { borderBottom: "1px solid #ddd" },
	"&light .cm-panels-bottom": { borderTop: "1px solid #ddd" },
	"&dark .cm-panels": {
		backgroundColor: "#333338",
		color: "white"
	},
	".cm-dialog": {
		padding: "2px 19px 4px 6px",
		position: "relative",
		"& label": { fontSize: "80%" }
	},
	".cm-dialog-close": {
		position: "absolute",
		top: "3px",
		right: "4px",
		backgroundColor: "inherit",
		border: "none",
		font: "inherit",
		fontSize: "14px",
		padding: "0"
	},
	".cm-tab": {
		display: "inline-block",
		overflow: "hidden",
		verticalAlign: "bottom"
	},
	".cm-widgetBuffer": {
		verticalAlign: "text-top",
		height: "1em",
		width: 0,
		display: "inline"
	},
	".cm-placeholder": {
		color: "#888",
		display: "inline-block",
		verticalAlign: "top",
		userSelect: "none"
	},
	".cm-highlightSpace": {
		background: "radial-gradient(circle at 50% 55%, #aaa 20%, transparent 0) no-repeat",
		backgroundSize: ".4em",
		backgroundPosition: "calc(min(50%, 0px)) center"
	},
	".cm-highlightTab": {
		backgroundImage: "url('data:image/svg+xml,<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"20\"><path stroke=\"%23888\" stroke-width=\"1\" fill=\"none\" d=\"M1 10H196L190 5M190 15L196 10M197 4L197 16\"/></svg>')",
		backgroundSize: "auto 100%",
		backgroundPosition: "right 90%",
		backgroundRepeat: "no-repeat"
	},
	".cm-trailingSpace": { backgroundColor: "#ff332255" },
	".cm-button": {
		verticalAlign: "middle",
		color: "inherit",
		fontSize: "70%",
		padding: ".2em 1em",
		borderRadius: "1px"
	},
	"&light .cm-button": {
		backgroundImage: "linear-gradient(#eff1f5, #d9d9df)",
		border: "1px solid #888",
		"&:active": { backgroundImage: "linear-gradient(#b4b4b4, #d0d3d6)" }
	},
	"&dark .cm-button": {
		backgroundImage: "linear-gradient(#393939, #111)",
		border: "1px solid #888",
		"&:active": { backgroundImage: "linear-gradient(#111, #333)" }
	},
	".cm-textfield": {
		verticalAlign: "middle",
		color: "inherit",
		fontSize: "70%",
		border: "1px solid silver",
		padding: ".2em .5em"
	},
	"&light .cm-textfield": { backgroundColor: "white" },
	"&dark .cm-textfield": {
		border: "1px solid #555",
		backgroundColor: "inherit"
	}
}, Lo), Bo = {
	childList: !0,
	characterData: !0,
	subtree: !0,
	attributes: !0,
	characterDataOldValue: !0
}, Vo = P.ie && P.ie_version <= 11, Ho = class {
	constructor(e) {
		this.view = e, this.active = !1, this.editContext = null, this.selectionRange = new Bn(), this.selectionChanged = !1, this.delayedFlush = -1, this.resizeTimeout = -1, this.queue = [], this.delayedAndroidKey = null, this.flushingAndroidKey = -1, this.lastChange = 0, this.scrollTargets = [], this.intersection = null, this.resizeScroll = null, this.intersecting = !1, this.gapIntersection = null, this.gaps = [], this.printQuery = null, this.parentCheck = -1, this.dom = e.contentDOM, this.observer = new MutationObserver((t) => {
			for (let e of t) this.queue.push(e);
			(P.ie && P.ie_version <= 11 || P.ios && e.composing) && t.some((e) => e.type == "childList" && e.removedNodes.length || e.type == "characterData" && e.oldValue.length > e.target.nodeValue.length) ? this.flushSoon() : this.flush();
		}), window.EditContext && P.android && e.constructor.EDIT_CONTEXT !== !1 && !(P.chrome && P.chrome_version < 126) && (this.editContext = new Ko(e), e.state.facet(Lr) && (e.contentDOM.editContext = this.editContext.editContext)), Vo && (this.onCharData = (e) => {
			this.queue.push({
				target: e.target,
				type: "characterData",
				oldValue: e.prevValue
			}), this.flushSoon();
		}), this.onSelectionChange = this.onSelectionChange.bind(this), this.onResize = this.onResize.bind(this), this.onPrint = this.onPrint.bind(this), this.onScroll = this.onScroll.bind(this), window.matchMedia && (this.printQuery = window.matchMedia("print")), typeof ResizeObserver == "function" && (this.resizeScroll = new ResizeObserver(() => {
			this.view.docView?.lastUpdate < Date.now() - 75 && this.onResize();
		}), this.resizeScroll.observe(e.scrollDOM)), this.addWindowListeners(this.win = e.win), this.start(), typeof IntersectionObserver == "function" && (this.intersection = new IntersectionObserver((e) => {
			this.parentCheck < 0 && (this.parentCheck = setTimeout(this.listenForScroll.bind(this), 1e3)), e.length > 0 && e[e.length - 1].intersectionRatio > 0 != this.intersecting && (this.intersecting = !this.intersecting, this.intersecting != this.view.inView && this.onScrollChanged(document.createEvent("Event")));
		}, { threshold: [0, .001] }), this.intersection.observe(this.dom), this.gapIntersection = new IntersectionObserver((e) => {
			e.length > 0 && e[e.length - 1].intersectionRatio > 0 && this.onScrollChanged(document.createEvent("Event"));
		}, {})), this.listenForScroll(), this.readSelectionRange();
	}
	onScrollChanged(e) {
		this.view.inputState.runHandlers("scroll", e), this.intersecting && this.view.measure();
	}
	onScroll(e) {
		this.intersecting && this.flush(!1), this.editContext && this.view.requestMeasure(this.editContext.measureReq), this.onScrollChanged(e);
	}
	onResize() {
		this.resizeTimeout < 0 && (this.resizeTimeout = setTimeout(() => {
			this.resizeTimeout = -1, this.view.requestMeasure();
		}, 50));
	}
	onPrint(e) {
		(e.type != "change" && e.type || e.matches) && (this.view.viewState.printing = !0, this.view.measure(), setTimeout(() => {
			this.view.viewState.printing = !1, this.view.requestMeasure();
		}, 500));
	}
	updateGaps(e) {
		if (this.gapIntersection && (e.length != this.gaps.length || this.gaps.some((t, n) => t != e[n]))) {
			this.gapIntersection.disconnect();
			for (let t of e) this.gapIntersection.observe(t);
			this.gaps = e;
		}
	}
	onSelectionChange(e) {
		let t = this.selectionChanged;
		if (!this.readSelectionRange() || this.delayedAndroidKey) return;
		let { view: n } = this, r = this.selectionRange;
		if (n.state.facet(Lr) ? n.root.activeElement != this.dom : !On(this.dom, r)) return;
		let i = r.anchorNode && n.docView.tile.nearest(r.anchorNode);
		if (i && i.isWidget() && i.widget.ignoreEvent(e)) {
			t || (this.selectionChanged = !1);
			return;
		}
		(P.ie && P.ie_version <= 11 || P.android && P.chrome) && !n.state.selection.main.empty && r.focusNode && An(r.focusNode, r.focusOffset, r.anchorNode, r.anchorOffset) ? this.flushSoon() : this.flush(!1);
	}
	readSelectionRange() {
		let { view: e } = this, t = En(e.root);
		if (!t) return !1;
		let n = P.safari && e.root.nodeType == 11 && e.root.activeElement == this.dom && Go(this.view, t) || t;
		if (!n || this.selectionRange.eq(n)) return !1;
		let r = On(this.dom, n);
		return r && !this.selectionChanged && e.inputState.lastFocusTime > Date.now() - 200 && e.inputState.lastTouchTime < Date.now() - 300 && Yn(this.dom, n) ? (this.view.inputState.lastFocusTime = 0, e.docView.updateSelection(), !1) : (this.selectionRange.setRange(n), r && (this.selectionChanged = !0), !0);
	}
	setSelectionRange(e, t) {
		this.selectionRange.set(e.node, e.offset, t.node, t.offset), this.selectionChanged = !1;
	}
	clearSelectionRange() {
		this.selectionRange.set(null, 0, null, 0);
	}
	listenForScroll() {
		this.parentCheck = -1;
		let e = 0, t = null;
		for (let n = this.dom; n;) if (n.nodeType == 1) !t && e < this.scrollTargets.length && this.scrollTargets[e] == n ? e++ : t ||= this.scrollTargets.slice(0, e), t && t.push(n), n = n.assignedSlot || n.parentNode;
		else if (n.nodeType == 11) n = n.host;
		else break;
		if (e < this.scrollTargets.length && !t && (t = this.scrollTargets.slice(0, e)), t) {
			for (let e of this.scrollTargets) e.removeEventListener("scroll", this.onScroll);
			for (let e of this.scrollTargets = t) e.addEventListener("scroll", this.onScroll);
		}
	}
	ignore(e) {
		if (!this.active) return e();
		try {
			return this.stop(), e();
		} finally {
			this.start(), this.clear();
		}
	}
	start() {
		this.active ||= (this.observer.observe(this.dom, Bo), Vo && this.dom.addEventListener("DOMCharacterDataModified", this.onCharData), !0);
	}
	stop() {
		this.active && (this.active = !1, this.observer.disconnect(), Vo && this.dom.removeEventListener("DOMCharacterDataModified", this.onCharData));
	}
	clear() {
		this.processRecords(), this.queue.length = 0, this.selectionChanged = !1;
	}
	delayAndroidKey(e, t) {
		if (!this.delayedAndroidKey) {
			let e = () => {
				let e = this.delayedAndroidKey;
				e && (this.clearDelayedAndroidKey(), this.view.inputState.lastKeyCode = e.keyCode, this.view.inputState.lastKeyTime = Date.now(), !this.flush() && e.force && qn(this.dom, e.key, e.keyCode));
			};
			this.flushingAndroidKey = this.view.win.requestAnimationFrame(e);
		}
		(!this.delayedAndroidKey || e == "Enter") && (this.delayedAndroidKey = {
			key: e,
			keyCode: t,
			force: this.lastChange < Date.now() - 50 || !!this.delayedAndroidKey?.force
		});
	}
	clearDelayedAndroidKey() {
		this.win.cancelAnimationFrame(this.flushingAndroidKey), this.delayedAndroidKey = null, this.flushingAndroidKey = -1;
	}
	flushSoon() {
		this.delayedFlush < 0 && (this.delayedFlush = this.view.win.requestAnimationFrame(() => {
			this.delayedFlush = -1, this.flush();
		}));
	}
	forceFlush() {
		this.delayedFlush >= 0 && (this.view.win.cancelAnimationFrame(this.delayedFlush), this.delayedFlush = -1), this.flush();
	}
	pendingRecords() {
		for (let e of this.observer.takeRecords()) this.queue.push(e);
		return this.queue;
	}
	processRecords() {
		let e = this.pendingRecords();
		e.length && (this.queue = []);
		let t = -1, n = -1, r = !1;
		for (let i of e) {
			let e = this.readMutation(i);
			e && (e.typeOver && (r = !0), t == -1 ? {from: t, to: n} = e : (t = Math.min(e.from, t), n = Math.max(e.to, n)));
		}
		return {
			from: t,
			to: n,
			typeOver: r
		};
	}
	readChange() {
		let { from: e, to: t, typeOver: n } = this.processRecords(), r = this.selectionChanged && On(this.dom, this.selectionRange);
		if (e < 0 && !r) return null;
		e > -1 && (this.lastChange = Date.now()), this.view.inputState.lastFocusTime = 0, this.selectionChanged = !1;
		let i = new aa(this.view, e, t, n);
		return this.view.docView.domChanged = { newSel: i.newSel ? i.newSel.main : null }, i;
	}
	flush(e = !0) {
		if (this.delayedFlush >= 0 || this.delayedAndroidKey) return !1;
		e && this.readSelectionRange();
		let t = this.readChange();
		if (!t) return this.view.requestMeasure(), !1;
		let n = this.view.state, r = sa(this.view, t);
		return this.view.state == n && (t.domChanged || t.newSel && !pa(this.view.state.selection, t.newSel.main)) && this.view.update([]), r;
	}
	readMutation(e) {
		let t = this.view.docView.tile.nearest(e.target);
		if (!t || t.isWidget()) return null;
		if (t.markDirty(e.type == "attributes"), e.type == "childList") {
			let n = Uo(t, e.previousSibling || e.target.previousSibling, -1), r = Uo(t, e.nextSibling || e.target.nextSibling, 1);
			return {
				from: n ? t.posAfter(n) : t.posAtStart,
				to: r ? t.posBefore(r) : t.posAtEnd,
				typeOver: !1
			};
		}
		return e.type == "characterData" ? {
			from: t.posAtStart,
			to: t.posAtEnd,
			typeOver: e.target.nodeValue == e.oldValue
		} : null;
	}
	setWindow(e) {
		e != this.win && (this.removeWindowListeners(this.win), this.win = e, this.addWindowListeners(this.win));
	}
	addWindowListeners(e) {
		e.addEventListener("resize", this.onResize), this.printQuery ? this.printQuery.addEventListener ? this.printQuery.addEventListener("change", this.onPrint) : this.printQuery.addListener(this.onPrint) : e.addEventListener("beforeprint", this.onPrint), e.addEventListener("scroll", this.onScroll), e.document.addEventListener("selectionchange", this.onSelectionChange);
	}
	removeWindowListeners(e) {
		e.removeEventListener("scroll", this.onScroll), e.removeEventListener("resize", this.onResize), this.printQuery ? this.printQuery.removeEventListener ? this.printQuery.removeEventListener("change", this.onPrint) : this.printQuery.removeListener(this.onPrint) : e.removeEventListener("beforeprint", this.onPrint), e.document.removeEventListener("selectionchange", this.onSelectionChange);
	}
	update(e) {
		this.editContext && (this.editContext.update(e), e.startState.facet(Lr) != e.state.facet(Lr) && (e.view.contentDOM.editContext = e.state.facet(Lr) ? this.editContext.editContext : null));
	}
	destroy() {
		var e, t, n;
		this.stop(), (e = this.intersection) == null || e.disconnect(), (t = this.gapIntersection) == null || t.disconnect(), (n = this.resizeScroll) == null || n.disconnect();
		for (let e of this.scrollTargets) e.removeEventListener("scroll", this.onScroll);
		this.removeWindowListeners(this.win), clearTimeout(this.parentCheck), clearTimeout(this.resizeTimeout), this.win.cancelAnimationFrame(this.delayedFlush), this.win.cancelAnimationFrame(this.flushingAndroidKey), this.editContext && (this.view.contentDOM.editContext = null, this.editContext.destroy());
	}
};
function Uo(e, t, n) {
	for (; t;) {
		let r = R.get(t);
		if (r && r.parent == e) return r;
		let i = t.parentNode;
		t = i == e.dom ? n > 0 ? t.nextSibling : t.previousSibling : i;
	}
	return null;
}
function Wo(e, t) {
	let n = t.startContainer, r = t.startOffset, i = t.endContainer, a = t.endOffset, o = e.docView.domAtPos(e.state.selection.main.anchor, 1);
	return An(o.node, o.offset, i, a) && ([n, r, i, a] = [
		i,
		a,
		n,
		r
	]), {
		anchorNode: n,
		anchorOffset: r,
		focusNode: i,
		focusOffset: a
	};
}
function Go(e, t) {
	if (t.getComposedRanges) {
		let n = t.getComposedRanges(e.root)[0];
		if (n) return Wo(e, n);
	}
	let n = null;
	function r(e) {
		e.preventDefault(), e.stopImmediatePropagation(), n = e.getTargetRanges()[0];
	}
	return e.contentDOM.addEventListener("beforeinput", r, !0), e.dom.ownerDocument.execCommand("indent"), e.contentDOM.removeEventListener("beforeinput", r, !0), n ? Wo(e, n) : null;
}
var Ko = class {
	constructor(e) {
		this.from = 0, this.to = 0, this.pendingContextChange = null, this.handlers = Object.create(null), this.composing = null, this.resetRange(e.state);
		let t = this.editContext = new window.EditContext({
			text: e.state.doc.sliceString(this.from, this.to),
			selectionStart: this.toContextPos(Math.max(this.from, Math.min(this.to, e.state.selection.main.anchor))),
			selectionEnd: this.toContextPos(e.state.selection.main.head)
		});
		this.handlers.textupdate = (n) => {
			let r = e.state.selection.main, { anchor: i, head: a } = r, o = this.toEditorPos(n.updateRangeStart), s = this.toEditorPos(n.updateRangeEnd);
			e.inputState.composing >= 0 && !this.composing && (this.composing = {
				contextBase: n.updateRangeStart,
				editorBase: o,
				drifted: !1
			});
			let c = s - o > n.text.length;
			o == this.from && i < this.from ? o = i : s == this.to && i > this.to && (s = i);
			let l = ua(e.state.sliceDoc(o, s), n.text, (c ? r.from : r.to) - o, c ? "end" : null);
			if (!l) {
				let t = k.single(this.toEditorPos(n.selectionStart), this.toEditorPos(n.selectionEnd));
				pa(t, r) || e.dispatch({
					selection: t,
					userEvent: "select"
				});
				return;
			}
			let u = {
				from: l.from + o,
				to: l.toA + o,
				insert: T.of(n.text.slice(l.from, l.toB).split("\n"))
			};
			if ((P.mac || P.android) && u.from == a - 1 && /^\. ?$/.test(n.text) && e.contentDOM.getAttribute("autocorrect") == "off" && (u = {
				from: o,
				to: s,
				insert: T.of([n.text.replace(".", " ")])
			}), this.pendingContextChange = u, !e.state.readOnly) {
				let t = this.to - this.from + (u.to - u.from + u.insert.length);
				ca(e, u, k.single(this.toEditorPos(n.selectionStart, t), this.toEditorPos(n.selectionEnd, t)));
			}
			this.pendingContextChange && (this.revertPending(e.state), this.setSelection(e.state)), u.from < u.to && !u.insert.length && e.inputState.composing >= 0 && !/[\\p{Alphabetic}\\p{Number}_]/.test(t.text.slice(Math.max(0, n.updateRangeStart - 1), Math.min(t.text.length, n.updateRangeStart + 1))) && this.handlers.compositionend(n);
		}, this.handlers.characterboundsupdate = (n) => {
			let r = [], i = null;
			for (let t = this.toEditorPos(n.rangeStart), a = this.toEditorPos(n.rangeEnd); t < a; t++) {
				let n = e.coordsForChar(t);
				i = n && new DOMRect(n.left, n.top, n.right - n.left, n.bottom - n.top) || i || new DOMRect(), r.push(i);
			}
			t.updateCharacterBounds(n.rangeStart, r);
		}, this.handlers.textformatupdate = (t) => {
			let n = [];
			for (let e of t.getTextFormats()) {
				let t = e.underlineStyle, r = e.underlineThickness;
				if (!/none/i.test(t) && !/none/i.test(r)) {
					let i = this.toEditorPos(e.rangeStart), a = this.toEditorPos(e.rangeEnd);
					if (i < a) {
						let e = `text-decoration: underline ${/^[a-z]/.test(t) ? t + " " : t == "Dashed" ? "dashed " : t == "Squiggle" ? "wavy " : ""}${/thin/i.test(r) ? 1 : 2}px`;
						n.push(F.mark({ attributes: { style: e } }).range(i, a));
					}
				}
			}
			e.dispatch({ effects: Fr.of(F.set(n)) });
		}, this.handlers.compositionstart = () => {
			e.inputState.composing < 0 && (e.inputState.composing = 0, e.inputState.compositionFirstChange = !0);
		}, this.handlers.compositionend = () => {
			if (e.inputState.composing = -1, e.inputState.compositionFirstChange = null, this.composing) {
				let { drifted: t } = this.composing;
				this.composing = null, t && this.reset(e.state);
			}
		};
		for (let e in this.handlers) t.addEventListener(e, this.handlers[e]);
		this.measureReq = { read: (e) => {
			let t = En(e.root);
			t && t.rangeCount && this.editContext.updateSelectionBounds(t.getRangeAt(0).getBoundingClientRect());
		} };
	}
	applyEdits(e) {
		let t = 0, n = !1, r = this.pendingContextChange;
		return e.changes.iterChanges((i, a, o, s, c) => {
			if (n) return;
			let l = c.length - (a - i);
			if (r && a >= r.to) {
				if (r.from == i && r.to == a && r.insert.eq(c)) {
					r = this.pendingContextChange = null, t += l, this.to += l;
					return;
				}
				r = null, this.revertPending(e.state);
			}
			if (i += t, a += t, a <= this.from) this.from += l, this.to += l;
			else if (i < this.to) {
				if (i < this.from || a > this.to || this.to - this.from + c.length > 3e4) {
					n = !0;
					return;
				}
				this.editContext.updateText(this.toContextPos(i), this.toContextPos(a), c.toString()), this.to += l;
			}
			t += l;
		}), r && !n && this.revertPending(e.state), !n;
	}
	update(e) {
		let t = this.pendingContextChange, n = e.startState.selection.main;
		this.composing && (this.composing.drifted || !e.changes.touchesRange(n.from, n.to) && e.transactions.some((e) => !e.isUserEvent("input.type") && e.changes.touchesRange(this.from, this.to))) ? (this.composing.drifted = !0, this.composing.editorBase = e.changes.mapPos(this.composing.editorBase)) : !this.applyEdits(e) || !this.rangeIsValid(e.state) ? (this.pendingContextChange = null, this.reset(e.state)) : (e.docChanged || e.selectionSet || t) && this.setSelection(e.state), (e.geometryChanged || e.docChanged || e.selectionSet) && e.view.requestMeasure(this.measureReq);
	}
	resetRange(e) {
		let { head: t } = e.selection.main;
		this.from = Math.max(0, t - 1e4), this.to = Math.min(e.doc.length, t + 1e4);
	}
	reset(e) {
		this.resetRange(e), this.editContext.updateText(0, this.editContext.text.length, e.doc.sliceString(this.from, this.to)), this.setSelection(e);
	}
	revertPending(e) {
		let t = this.pendingContextChange;
		this.pendingContextChange = null, this.editContext.updateText(this.toContextPos(t.from), this.toContextPos(t.from + t.insert.length), e.doc.sliceString(t.from, t.to));
	}
	setSelection(e) {
		let { main: t } = e.selection, n = this.toContextPos(Math.max(this.from, Math.min(this.to, t.anchor))), r = this.toContextPos(t.head);
		(this.editContext.selectionStart != n || this.editContext.selectionEnd != r) && this.editContext.updateSelection(n, r);
	}
	rangeIsValid(e) {
		let { head: t } = e.selection.main;
		return !(this.from > 0 && t - this.from < 500 || this.to < e.doc.length && this.to - t < 500 || this.to - this.from > 3e4);
	}
	toEditorPos(e, t = this.to - this.from) {
		e = Math.min(e, t);
		let n = this.composing;
		return n && n.drifted ? n.editorBase + (e - n.contextBase) : e + this.from;
	}
	toContextPos(e) {
		let t = this.composing;
		return t && t.drifted ? t.contextBase + (e - t.editorBase) : e - this.from;
	}
	destroy() {
		for (let e in this.handlers) this.editContext.removeEventListener(e, this.handlers[e]);
	}
}, V = class e {
	get state() {
		return this.viewState.state;
	}
	get viewport() {
		return this.viewState.viewport;
	}
	get visibleRanges() {
		return this.viewState.visibleRanges;
	}
	get inView() {
		return this.viewState.inView;
	}
	get composing() {
		return !!this.inputState && this.inputState.composing > 0;
	}
	get compositionStarted() {
		return !!this.inputState && this.inputState.composing >= 0;
	}
	get root() {
		return this._root;
	}
	get win() {
		return this.dom.ownerDocument.defaultView || window;
	}
	constructor(e = {}) {
		this.plugins = [], this.pluginMap = /* @__PURE__ */ new Map(), this.editorAttrs = {}, this.contentAttrs = {}, this.bidiCache = [], this.destroyed = !1, this.updateState = 2, this.measureScheduled = -1, this.measureRequests = [], this.contentDOM = document.createElement("div"), this.scrollDOM = document.createElement("div"), this.scrollDOM.tabIndex = -1, this.scrollDOM.className = "cm-scroller", this.scrollDOM.appendChild(this.contentDOM), this.announceDOM = document.createElement("div"), this.announceDOM.className = "cm-announced", this.announceDOM.setAttribute("aria-live", "polite"), this.dom = document.createElement("div"), this.dom.appendChild(this.announceDOM), this.dom.appendChild(this.scrollDOM), e.parent && e.parent.appendChild(this.dom);
		let { dispatch: t } = e;
		this.dispatchTransactions = e.dispatchTransactions || t && ((e) => e.forEach((e) => t(e, this))) || ((e) => this.update(e)), this.dispatch = this.dispatch.bind(this), this._root = e.root || Jn(e.parent) || document, this.viewState = new So(this, e.state || M.create(e)), e.scrollTo && e.scrollTo.is(Pr) && (this.viewState.scrollTarget = e.scrollTo.value.clip(this.viewState.state)), this.plugins = this.state.facet(zr).map((e) => new Vr(e));
		for (let e of this.plugins) e.update(this);
		this.observer = new Ho(this), this.inputState = new ma(this), this.inputState.ensureHandlers(this.plugins), this.docView = new Oi(this), this.mountStyles(), this.updateAttrs(), this.updateState = 0, this.requestMeasure(), document.fonts?.ready && document.fonts.ready.then(() => {
			this.viewState.mustMeasureContent = "refresh", this.requestMeasure();
		});
	}
	dispatch(...e) {
		let t = e.length == 1 && e[0] instanceof it ? e : e.length == 1 && Array.isArray(e[0]) ? e[0] : [this.state.update(...e)];
		this.dispatchTransactions(t, this);
	}
	update(t) {
		if (this.updateState != 0) throw Error("Calls to EditorView.update are not allowed while an update is in progress");
		let n = !1, r = !1, i, a = this.state;
		for (let e of t) {
			if (e.startState != a) throw RangeError("Trying to update state with a transaction that doesn't start from the previous state.");
			a = e.state;
		}
		if (this.destroyed) {
			this.viewState.state = a;
			return;
		}
		let o = this.hasFocus, s = 0, c = null;
		t.some((e) => e.annotation(Ka)) ? (this.inputState.notifiedFocused = o, s = 1) : o != this.inputState.notifiedFocused && (this.inputState.notifiedFocused = o, c = qa(a, o), c || (s = 1));
		let l = this.observer.delayedAndroidKey, u = null;
		if (l ? (this.observer.clearDelayedAndroidKey(), u = this.observer.readChange(), (u && !this.state.doc.eq(a.doc) || !this.state.selection.eq(a.selection)) && (u = null)) : this.observer.clear(), a.facet(M.phrases) != this.state.facet(M.phrases)) return this.setState(a);
		i = ei.create(this, a, t), i.flags |= s;
		let d = this.viewState.scrollTarget;
		try {
			this.updateState = 2;
			for (let n of t) {
				if (d &&= d.map(n.changes), n.scrollIntoView) {
					let { main: t } = n.state.selection, { x: r, y: i } = this.state.facet(e.cursorScrollMargin);
					d = new Nr(t.empty ? t : k.cursor(t.head, t.head > t.anchor ? -1 : 1), "nearest", "nearest", i, r);
				}
				for (let e of n.effects) e.is(Pr) && (d = e.value.clip(this.state));
			}
			this.viewState.update(i, d), this.bidiCache = Yo.update(this.bidiCache, i.changes), i.empty || (this.updatePlugins(i), this.inputState.update(i)), n = this.docView.update(i), this.state.facet(Qr) != this.styleModules && this.mountStyles(), r = this.updateAttrs(), this.showAnnouncements(t), this.docView.updateSelection(n, t.some((e) => e.isUserEvent("select.pointer")));
		} finally {
			this.updateState = 0;
		}
		if (i.startState.facet(Mo) != i.state.facet(Mo) && (this.viewState.mustMeasureContent = !0), (n || r || d || this.viewState.mustEnforceCursorAssoc || this.viewState.mustMeasureContent) && this.requestMeasure(), n && this.docViewUpdate(), !i.empty) for (let e of this.state.facet(Tr)) try {
			e(i);
		} catch (e) {
			Ir(this.state, e, "update listener");
		}
		(c || u) && Promise.resolve().then(() => {
			c && this.state == c.startState && this.dispatch(c), u && !sa(this, u) && l.force && qn(this.contentDOM, l.key, l.keyCode);
		});
	}
	setState(e) {
		if (this.updateState != 0) throw Error("Calls to EditorView.setState are not allowed while an update is in progress");
		if (this.destroyed) {
			this.viewState.state = e;
			return;
		}
		this.updateState = 2;
		let t = this.hasFocus;
		try {
			for (let e of this.plugins) e.destroy(this);
			this.viewState = new So(this, e), this.plugins = e.facet(zr).map((e) => new Vr(e)), this.pluginMap.clear();
			for (let e of this.plugins) e.update(this);
			this.docView.destroy(), this.docView = new Oi(this), this.inputState.ensureHandlers(this.plugins), this.mountStyles(), this.updateAttrs(), this.bidiCache = [];
		} finally {
			this.updateState = 0;
		}
		t && this.focus(), this.requestMeasure();
	}
	updatePlugins(e) {
		let t = e.startState.facet(zr), n = e.state.facet(zr);
		if (t != n) {
			let r = [];
			for (let i of n) {
				let n = t.indexOf(i);
				if (n < 0) r.push(new Vr(i));
				else {
					let t = this.plugins[n];
					t.mustUpdate = e, r.push(t);
				}
			}
			for (let t of this.plugins) t.mustUpdate != e && t.destroy(this);
			this.plugins = r, this.pluginMap.clear();
		} else for (let t of this.plugins) t.mustUpdate = e;
		for (let e = 0; e < this.plugins.length; e++) this.plugins[e].update(this);
		t != n && this.inputState.ensureHandlers(this.plugins);
	}
	docViewUpdate() {
		for (let e of this.plugins) {
			let t = e.value;
			if (t && t.docViewUpdate) try {
				t.docViewUpdate(this);
			} catch (e) {
				Ir(this.state, e, "doc view update listener");
			}
		}
	}
	measure(e = !0) {
		if (this.destroyed) return;
		if (this.measureScheduled > -1 && this.win.cancelAnimationFrame(this.measureScheduled), this.observer.delayedAndroidKey) {
			this.measureScheduled = -1, this.requestMeasure();
			return;
		}
		this.measureScheduled = 0, e && this.observer.forceFlush();
		let t = null, n = this.viewState.scrollParent, r = this.viewState.getScrollOffset(), { scrollAnchorPos: i, scrollAnchorHeight: a, scaleY: o } = this.viewState;
		Math.abs(r - this.viewState.scrollOffset) > 1 && (a = -1), this.viewState.scrollAnchorHeight = -1;
		try {
			for (let e = 0;; e++) {
				if (a < 0) {
					if (Xn(n || this.win)) i = -1, a = this.viewState.heightMap.height / this.viewState.scaleY;
					else {
						let e = this.viewState.scrollAnchorAt(r);
						i = e.from, a = e.top;
					}
					o = this.viewState.scaleY;
				}
				this.updateState = 1;
				let s = this.viewState.measure();
				if (!s && !this.measureRequests.length && this.viewState.scrollTarget == null) break;
				if (e > 5) {
					console.warn(this.measureRequests.length ? "Measure loop restarted more than 5 times" : "Viewport failed to stabilize");
					break;
				}
				let c = [];
				s & 4 || ([this.measureRequests, c] = [c, this.measureRequests]);
				let l = c.map((e) => {
					try {
						return e.read(this);
					} catch (e) {
						return Ir(this.state, e), Jo;
					}
				}), u = ei.create(this, this.state, []), d = !1;
				u.flags |= s, t ? t.flags |= s : t = u, this.updateState = 2, u.empty || (this.updatePlugins(u), this.inputState.update(u), this.updateAttrs(), d = this.docView.update(u), d && this.docViewUpdate());
				for (let e = 0; e < c.length; e++) if (l[e] != Jo) try {
					let t = c[e];
					t.write && t.write(l[e], this);
				} catch (e) {
					Ir(this.state, e);
				}
				if (d && this.docView.updateSelection(!0), !u.viewportChanged && this.measureRequests.length == 0) {
					if (this.viewState.editorHeight) {
						if (this.viewState.scrollTarget) {
							this.docView.scrollIntoView(this.viewState.scrollTarget), this.viewState.scrollTarget = null, a = -1;
							continue;
						}
						{
							let e = (i < 0 ? this.viewState.heightMap.height : this.viewState.lineBlockAt(i).top) / this.viewState.scaleY - a / o;
							if ((e > 1 || e < -1) && !(P.ios && this.inputState.lastIOSMomentumScroll > Date.now() - 100) && (n == this.scrollDOM || this.hasFocus || Math.max(this.inputState.lastWheelEvent, this.inputState.lastTouchTime) > Date.now() - 100)) {
								r += e, n ? i < 0 ? n.scrollTop = n.scrollHeight : n.scrollTop += e : this.win.scrollBy(0, e), a = -1;
								continue;
							}
						}
					}
					break;
				}
			}
		} finally {
			this.updateState = 0, this.measureScheduled = -1;
		}
		if (t && !t.empty) for (let e of this.state.facet(Tr)) e(t);
	}
	get themeClasses() {
		return Po + " " + (this.state.facet(No) ? Io : Fo) + " " + this.state.facet(Mo);
	}
	updateAttrs() {
		let e = Xo(this, Hr, { class: "cm-editor" + (this.hasFocus ? " cm-focused " : " ") + this.themeClasses }), t = {
			spellcheck: "false",
			autocorrect: "off",
			autocapitalize: "off",
			writingsuggestions: "false",
			translate: "no",
			contenteditable: this.state.facet(Lr) ? "true" : "false",
			class: "cm-content",
			style: `${P.tabSize}: ${this.state.tabSize}`,
			role: "textbox",
			"aria-multiline": "true"
		};
		this.state.readOnly && (t["aria-readonly"] = "true"), Xo(this, Ur, t);
		let n = this.observer.ignore(() => {
			let n = hn(this.contentDOM, this.contentAttrs, t), r = hn(this.dom, this.editorAttrs, e);
			return n || r;
		});
		return this.editorAttrs = e, this.contentAttrs = t, n;
	}
	showAnnouncements(t) {
		let n = !0;
		for (let r of t) for (let t of r.effects) if (t.is(e.announce)) {
			n && (this.announceDOM.textContent = ""), n = !1;
			let e = this.announceDOM.appendChild(document.createElement("div"));
			e.textContent = t.value;
		}
	}
	mountStyles() {
		this.styleModules = this.state.facet(Qr);
		let t = this.state.facet(e.cspNonce);
		Ut.mount(this.root, this.styleModules.concat(zo).reverse(), t ? { nonce: t } : void 0);
	}
	readMeasured() {
		if (this.updateState == 2) throw Error("Reading the editor layout isn't allowed during an update");
		this.updateState == 0 && this.measureScheduled > -1 && this.measure(!1);
	}
	requestMeasure(e) {
		if (this.measureScheduled < 0 && (this.measureScheduled = this.win.requestAnimationFrame(() => this.measure())), e) {
			if (this.measureRequests.indexOf(e) > -1) return;
			if (e.key != null) {
				for (let t = 0; t < this.measureRequests.length; t++) if (this.measureRequests[t].key === e.key) {
					this.measureRequests[t] = e;
					return;
				}
			}
			this.measureRequests.push(e);
		}
	}
	plugin(e) {
		let t = this.pluginMap.get(e);
		return (t === void 0 || t && t.plugin != e) && this.pluginMap.set(e, t = this.plugins.find((t) => t.plugin == e) || null), t && t.update(this).value;
	}
	get documentTop() {
		return this.contentDOM.getBoundingClientRect().top + this.viewState.paddingTop;
	}
	get documentPadding() {
		return {
			top: this.viewState.paddingTop,
			bottom: this.viewState.paddingBottom
		};
	}
	get scaleX() {
		return this.viewState.scaleX;
	}
	get scaleY() {
		return this.viewState.scaleY;
	}
	elementAtHeight(e) {
		return this.readMeasured(), this.viewState.elementAtHeight(e);
	}
	lineBlockAtHeight(e) {
		return this.readMeasured(), this.viewState.lineBlockAtHeight(e);
	}
	get viewportLineBlocks() {
		return this.viewState.viewportLines;
	}
	lineBlockAt(e) {
		return this.viewState.lineBlockAt(e);
	}
	get contentHeight() {
		return this.viewState.contentHeight;
	}
	moveByChar(e, t, n) {
		return Xi(this, e, Gi(this, e, t, n));
	}
	moveByGroup(e, t) {
		return Xi(this, e, Gi(this, e, t, (t) => Ki(this, e.head, t)));
	}
	visualLineSide(e, t) {
		let n = this.bidiSpans(e), r = this.textDirectionAt(e.from), i = n[t ? n.length - 1 : 0];
		return k.cursor(i.side(t, r) + e.from, i.forward(!t, r) ? 1 : -1);
	}
	moveToLineBoundary(e, t, n = !0) {
		return Wi(this, e, t, n);
	}
	moveVertically(e, t, n) {
		return Xi(this, e, qi(this, e, t, n));
	}
	domAtPos(e, t = 1) {
		return this.docView.domAtPos(e, t);
	}
	posAtDOM(e, t = 0) {
		return this.docView.posFromDOM(e, t);
	}
	posAtCoords(e, t = !0) {
		this.readMeasured();
		let n = Qi(this, e, t);
		return n && n.pos;
	}
	posAndSideAtCoords(e, t = !0) {
		return this.readMeasured(), Qi(this, e, t);
	}
	coordsAtPos(e, t = 1) {
		this.readMeasured();
		let n = this.state.doc.lineAt(e), r = this.bidiSpans(n), i = r[lr.find(r, e - n.from, -1, t)];
		return this.docView.coordsAt(e, t, i.dir == I.RTL);
	}
	coordsForChar(e) {
		return this.readMeasured(), this.docView.coordsForChar(e);
	}
	get defaultCharacterWidth() {
		return this.viewState.heightOracle.charWidth;
	}
	get defaultLineHeight() {
		return this.viewState.heightOracle.lineHeight;
	}
	get textDirection() {
		return this.viewState.defaultTextDirection;
	}
	textDirectionAt(e) {
		return !this.state.facet(Ar) || e < this.viewport.from || e > this.viewport.to ? this.textDirection : (this.readMeasured(), this.docView.textDirectionAt(e));
	}
	get lineWrapping() {
		return this.viewState.heightOracle.lineWrapping;
	}
	bidiSpans(e) {
		if (e.length > qo) return _r(e.length);
		let t = this.textDirectionAt(e.from), n;
		for (let r of this.bidiCache) if (r.from == e.from && r.dir == t && (r.fresh || ur(r.isolates, n = Yr(this, e)))) return r.order;
		n ||= Yr(this, e);
		let r = gr(e.text, t, n);
		return this.bidiCache.push(new Yo(e.from, e.to, t, n, !0, r)), r;
	}
	get hasFocus() {
		return (this.dom.ownerDocument.hasFocus() || P.safari && this.inputState?.lastContextMenu > Date.now() - 3e4) && this.root.activeElement == this.contentDOM;
	}
	focus() {
		this.observer.ignore(() => {
			Wn(this.contentDOM), this.docView.updateSelection();
		});
	}
	setRoot(e) {
		this._root != e && (this._root = e, this.observer.setWindow((e.nodeType == 9 ? e : e.ownerDocument).defaultView || window), this.mountStyles());
	}
	destroy() {
		this.root.activeElement == this.contentDOM && this.contentDOM.blur();
		for (let e of this.plugins) e.destroy(this);
		this.plugins = [], this.inputState.destroy(), this.docView.destroy(), this.dom.remove(), this.observer.destroy(), this.measureScheduled > -1 && this.win.cancelAnimationFrame(this.measureScheduled), this.destroyed = !0;
	}
	static scrollIntoView(e, t = {}) {
		return Pr.of(new Nr(typeof e == "number" ? k.cursor(e) : e, t.y ?? "nearest", t.x ?? "nearest", t.yMargin ?? 5, t.xMargin ?? 5));
	}
	scrollSnapshot() {
		let { scrollTop: e, scrollLeft: t } = this.scrollDOM, n = this.viewState.scrollAnchorAt(e);
		return Pr.of(new Nr(k.cursor(n.from), "start", "start", n.top - e, t, !0));
	}
	setTabFocusMode(e) {
		e == null ? this.inputState.tabFocusMode = this.inputState.tabFocusMode < 0 ? 0 : -1 : typeof e == "boolean" ? this.inputState.tabFocusMode = e ? 0 : -1 : this.inputState.tabFocusMode != 0 && (this.inputState.tabFocusMode = Date.now() + e);
	}
	static domEventHandlers(e) {
		return Br.define(() => ({}), { eventHandlers: e });
	}
	static domEventObservers(e) {
		return Br.define(() => ({}), { eventObservers: e });
	}
	static theme(e, t) {
		let n = Ut.newName(), r = [Mo.of(n), Qr.of(Ro(`.${n}`, e))];
		return t && t.dark && r.push(No.of(!0)), r;
	}
	static baseTheme(e) {
		return Be.lowest(Qr.of(Ro("." + Po, e, Lo)));
	}
	static findFromDOM(e) {
		let t = e.querySelector(".cm-content");
		return (t && R.get(t) || R.get(e))?.root?.view || null;
	}
};
V.styleModule = Qr, V.inputHandler = Er, V.clipboardInputFilter = Or, V.clipboardOutputFilter = kr, V.scrollHandler = Mr, V.focusChangeEffect = Dr, V.perLineTextDirection = Ar, V.exceptionSink = wr, V.updateListener = Tr, V.editable = Lr, V.mouseSelectionStyle = Cr, V.dragMovesSelection = Sr, V.clickAddsSelectionRange = xr, V.decorations = Wr, V.blockWrappers = Gr, V.outerDecorations = Kr, V.atomicRanges = qr, V.bidiIsolatedRanges = Jr, V.cursorScrollMargin = /*@__PURE__*/ A.define({ combine: (e) => {
	let t = 5, n = 5;
	for (let r of e) typeof r == "number" ? t = n = r : {x: t, y: n} = r;
	return {
		x: t,
		y: n
	};
} }), V.scrollMargins = Xr, V.darkTheme = No, V.cspNonce = /*@__PURE__*/ A.define({ combine: (e) => e.length ? e[0] : "" }), V.contentAttributes = Ur, V.editorAttributes = Hr, V.lineWrapping = /*@__PURE__*/ V.contentAttributes.of({ class: "cm-lineWrapping" }), V.announce = /*@__PURE__*/ j.define();
var qo = 4096, Jo = {}, Yo = class e {
	constructor(e, t, n, r, i, a) {
		this.from = e, this.to = t, this.dir = n, this.isolates = r, this.fresh = i, this.order = a;
	}
	static update(t, n) {
		if (n.empty && !t.some((e) => e.fresh)) return t;
		let r = [], i = t.length ? t[t.length - 1].dir : I.LTR;
		for (let a = Math.max(0, t.length - 10); a < t.length; a++) {
			let o = t[a];
			o.dir == i && !n.touchesRange(o.from, o.to) && r.push(new e(n.mapPos(o.from, 1), n.mapPos(o.to, -1), o.dir, o.isolates, !1, o.order));
		}
		return r;
	}
};
function Xo(e, t, n) {
	for (let r = e.state.facet(t), i = r.length - 1; i >= 0; i--) {
		let t = r[i], a = typeof t == "function" ? t(e) : t;
		a && dn(a, n);
	}
	return n;
}
var Zo = P.mac ? "mac" : P.windows ? "win" : P.linux ? "linux" : "key";
function Qo(e, t) {
	let n = e.split(/-(?!$)/), r = n[n.length - 1];
	r == "Space" && (r = " ");
	let i, a, o, s;
	for (let e = 0; e < n.length - 1; ++e) {
		let r = n[e];
		if (/^(cmd|meta|m)$/i.test(r)) s = !0;
		else if (/^a(lt)?$/i.test(r)) i = !0;
		else if (/^(c|ctrl|control)$/i.test(r)) a = !0;
		else if (/^s(hift)?$/i.test(r)) o = !0;
		else if (/^mod$/i.test(r)) t == "mac" ? s = !0 : a = !0;
		else throw Error("Unrecognized modifier name: " + r);
	}
	return i && (r = "Alt-" + r), a && (r = "Ctrl-" + r), s && (r = "Meta-" + r), o && (r = "Shift-" + r), r;
}
function $o(e, t, n) {
	return t.altKey && (e = "Alt-" + e), t.ctrlKey && (e = "Ctrl-" + e), t.metaKey && (e = "Meta-" + e), n !== !1 && t.shiftKey && (e = "Shift-" + e), e;
}
var es = /*@__PURE__*/ Be.default(/*@__PURE__*/ V.domEventHandlers({ keydown(e, t) {
	return cs(rs(t.state), e, t, "editor");
} })), ts = /*@__PURE__*/ A.define({ enables: es }), ns = /*@__PURE__*/ new WeakMap();
function rs(e) {
	let t = e.facet(ts), n = ns.get(t);
	return n || ns.set(t, n = os(t.reduce((e, t) => e.concat(t), []))), n;
}
var is = null, as = 4e3;
function os(e, t = Zo) {
	let n = Object.create(null), r = Object.create(null), i = (e, t) => {
		let n = r[e];
		if (n == null) r[e] = t;
		else if (n != t) throw Error("Key binding " + e + " is used both as a regular binding and as a multi-stroke prefix");
	}, a = (e, r, a, o, s) => {
		let c = n[e] || (n[e] = Object.create(null)), l = r.split(/ (?!$)/).map((e) => Qo(e, t));
		for (let t = 1; t < l.length; t++) {
			let n = l.slice(0, t).join(" ");
			i(n, !0), c[n] || (c[n] = {
				preventDefault: !0,
				stopPropagation: !1,
				run: [(t) => {
					let r = is = {
						view: t,
						prefix: n,
						scope: e
					};
					return setTimeout(() => {
						is == r && (is = null);
					}, as), !0;
				}]
			});
		}
		let u = l.join(" ");
		i(u, !1);
		let d = c[u] || (c[u] = {
			preventDefault: !1,
			stopPropagation: !1,
			run: (c._any?.run)?.slice() || []
		});
		a && d.run.push(a), o && (d.preventDefault = !0), s && (d.stopPropagation = !0);
	};
	for (let r of e) {
		let e = r.scope ? r.scope.split(" ") : ["editor"];
		if (r.any) for (let t of e) {
			let e = n[t] || (n[t] = Object.create(null));
			e._any ||= {
				preventDefault: !1,
				stopPropagation: !1,
				run: []
			};
			let { any: i } = r;
			for (let t in e) e[t].run.push((e) => i(e, ss));
		}
		let i = r[t] || r.key;
		if (i) for (let t of e) a(t, i, r.run, r.preventDefault, r.stopPropagation), r.shift && a(t, "Shift-" + i, r.shift, r.preventDefault, r.stopPropagation);
	}
	return n;
}
var ss = null;
function cs(e, t, n, r) {
	ss = t;
	let i = Qt(t), a = ve(ge(i, 0)) == i.length && i != " ", o = "", s = !1, c = !1, l = !1;
	is && is.view == n && is.scope == r && (o = is.prefix + " ", ba.indexOf(t.keyCode) < 0 && (c = !0, is = null));
	let u = /* @__PURE__ */ new Set(), d = (e) => {
		if (e) {
			for (let t of e.run) if (!u.has(t) && (u.add(t), t(n))) return e.stopPropagation && (l = !0), !0;
			e.preventDefault && (e.stopPropagation && (l = !0), c = !0);
		}
		return !1;
	}, f = e[r], p, m;
	return f && (d(f[o + $o(i, t, !a)]) ? s = !0 : a && (t.altKey || t.metaKey || t.ctrlKey) && !(P.windows && t.ctrlKey && t.altKey) && !(P.mac && t.altKey && !(t.ctrlKey || t.metaKey)) && (p = Kt[t.keyCode]) && p != i ? (d(f[o + $o(p, t, !0)]) || t.shiftKey && (m = qt[t.keyCode]) != i && m != p && d(f[o + $o(m, t, !1)])) && (s = !0) : a && t.shiftKey && d(f[o + $o(i, t, !0)]) && (s = !0), !s && d(f._any) && (s = !0)), c && (s = !0), s && l && t.stopPropagation(), ss = null, s;
}
P.gecko && P.gecko_version, /x/.unicode;
var ls = "-10000px", us = class {
	constructor(e, t, n, r) {
		this.facet = t, this.createTooltipView = n, this.removeTooltipView = r, this.input = e.state.facet(t), this.tooltips = this.input.filter((e) => e);
		let i = null;
		this.tooltipViews = this.tooltips.map((e) => i = n(e, i));
	}
	update(e, t) {
		var n;
		let r = e.state.facet(this.facet), i = r.filter((e) => e);
		if (r === this.input) {
			for (let t of this.tooltipViews) t.update && t.update(e);
			return !1;
		}
		let a = [], o = t ? [] : null;
		for (let n = 0; n < i.length; n++) {
			let r = i[n], s = -1;
			if (r) {
				for (let e = 0; e < this.tooltips.length; e++) {
					let t = this.tooltips[e];
					t && t.create == r.create && (s = e);
				}
				if (s < 0) a[n] = this.createTooltipView(r, n ? a[n - 1] : null), o && (o[n] = !!r.above);
				else {
					let r = a[n] = this.tooltipViews[s];
					o && (o[n] = t[s]), r.update && r.update(e);
				}
			}
		}
		for (let e of this.tooltipViews) a.indexOf(e) < 0 && (this.removeTooltipView(e), (n = e.destroy) == null || n.call(e));
		return t && (o.forEach((e, n) => t[n] = e), t.length = o.length), this.input = r, this.tooltips = i, this.tooltipViews = a, !0;
	}
};
function ds(e) {
	let t = e.dom.ownerDocument.documentElement;
	return {
		top: 0,
		left: 0,
		bottom: t.clientHeight,
		right: t.clientWidth
	};
}
var fs = /*@__PURE__*/ A.define({ combine: (e) => ({
	position: P.ios ? "absolute" : e.find((e) => e.position)?.position || "fixed",
	parent: e.find((e) => e.parent)?.parent || null,
	tooltipSpace: e.find((e) => e.tooltipSpace)?.tooltipSpace || ds
}) }), ps = /*@__PURE__*/ new WeakMap(), ms = /*@__PURE__*/ Br.fromClass(class {
	constructor(e) {
		this.view = e, this.above = [], this.inView = !0, this.madeAbsolute = !1, this.lastTransaction = 0, this.measureTimeout = -1;
		let t = e.state.facet(fs);
		this.position = t.position, this.parent = t.parent, this.classes = e.themeClasses, this.createContainer(), this.measureReq = {
			read: this.readMeasure.bind(this),
			write: this.writeMeasure.bind(this),
			key: this
		}, this.resizeObserver = typeof ResizeObserver == "function" ? new ResizeObserver(() => this.measureSoon()) : null, this.manager = new us(e, vs, (e, t) => this.createTooltip(e, t), (e) => {
			this.resizeObserver && this.resizeObserver.unobserve(e.dom), e.dom.remove();
		}), this.above = this.manager.tooltips.map((e) => !!e.above), this.intersectionObserver = typeof IntersectionObserver == "function" ? new IntersectionObserver((e) => {
			Date.now() > this.lastTransaction - 50 && e.length > 0 && e[e.length - 1].intersectionRatio < 1 && this.measureSoon();
		}, { threshold: [1] }) : null, this.observeIntersection(), e.win.addEventListener("resize", this.measureSoon = this.measureSoon.bind(this)), this.maybeMeasure();
	}
	createContainer() {
		this.parent ? (this.container = document.createElement("div"), this.container.style.position = "relative", this.container.className = this.view.themeClasses, this.parent.appendChild(this.container)) : this.container = this.view.dom;
	}
	observeIntersection() {
		if (this.intersectionObserver) {
			this.intersectionObserver.disconnect();
			for (let e of this.manager.tooltipViews) this.intersectionObserver.observe(e.dom);
		}
	}
	measureSoon() {
		this.measureTimeout < 0 && (this.measureTimeout = setTimeout(() => {
			this.measureTimeout = -1, this.maybeMeasure();
		}, 50));
	}
	update(e) {
		e.transactions.length && (this.lastTransaction = Date.now());
		let t = this.manager.update(e, this.above);
		t && this.observeIntersection();
		let n = t || e.geometryChanged, r = e.state.facet(fs);
		if (r.position != this.position && !this.madeAbsolute) {
			this.position = r.position;
			for (let e of this.manager.tooltipViews) e.dom.style.position = this.position;
			n = !0;
		}
		if (r.parent != this.parent) {
			this.parent && this.container.remove(), this.parent = r.parent, this.createContainer();
			for (let e of this.manager.tooltipViews) this.container.appendChild(e.dom);
			n = !0;
		} else this.parent && this.view.themeClasses != this.classes && (this.classes = this.container.className = this.view.themeClasses);
		n && this.maybeMeasure();
	}
	createTooltip(e, t) {
		let n = e.create(this.view), r = t ? t.dom : null;
		if (n.dom.classList.add("cm-tooltip"), e.arrow && !n.dom.querySelector(".cm-tooltip > .cm-tooltip-arrow")) {
			let e = document.createElement("div");
			e.className = "cm-tooltip-arrow", n.dom.appendChild(e);
		}
		return n.dom.style.position = this.position, n.dom.style.top = ls, n.dom.style.left = "0px", this.container.insertBefore(n.dom, r), n.mount && n.mount(this.view), this.resizeObserver && this.resizeObserver.observe(n.dom), n;
	}
	destroy() {
		var e, t, n;
		this.view.win.removeEventListener("resize", this.measureSoon);
		for (let t of this.manager.tooltipViews) t.dom.remove(), (e = t.destroy) == null || e.call(t);
		this.parent && this.container.remove(), (t = this.resizeObserver) == null || t.disconnect(), (n = this.intersectionObserver) == null || n.disconnect(), clearTimeout(this.measureTimeout);
	}
	readMeasure() {
		let e = 1, t = 1, n = !1;
		if (this.position == "fixed" && this.manager.tooltipViews.length) {
			let { dom: e } = this.manager.tooltipViews[0];
			if (P.safari) {
				let t = e.getBoundingClientRect();
				n = Math.abs(t.top + 1e4) > 1 || Math.abs(t.left) > 1;
			} else n = !!e.offsetParent && e.offsetParent != this.container.ownerDocument.body;
		}
		if (n || this.position == "absolute") {
			if (this.parent) {
				let n = this.parent.getBoundingClientRect();
				n.width && n.height && (e = n.width / this.parent.offsetWidth, t = n.height / this.parent.offsetHeight);
			} else ({scaleX: e, scaleY: t} = this.view.viewState);
		}
		let r = this.view.scrollDOM.getBoundingClientRect(), i = Zr(this.view);
		return {
			visible: {
				left: r.left + i.left,
				top: r.top + i.top,
				right: r.right - i.right,
				bottom: r.bottom - i.bottom
			},
			parent: this.parent ? this.container.getBoundingClientRect() : this.view.dom.getBoundingClientRect(),
			pos: this.manager.tooltips.map((e, t) => {
				let n = this.manager.tooltipViews[t];
				return n.getCoords ? n.getCoords(e.pos) : this.view.coordsAtPos(e.pos);
			}),
			size: this.manager.tooltipViews.map(({ dom: e }) => e.getBoundingClientRect()),
			space: this.view.state.facet(fs).tooltipSpace(this.view),
			scaleX: e,
			scaleY: t,
			makeAbsolute: n
		};
	}
	writeMeasure(e) {
		if (e.makeAbsolute) {
			this.madeAbsolute = !0, this.position = "absolute";
			for (let e of this.manager.tooltipViews) e.dom.style.position = "absolute";
		}
		let { visible: t, space: n, scaleX: r, scaleY: i } = e, a = [];
		for (let o = 0; o < this.manager.tooltips.length; o++) {
			let s = this.manager.tooltips[o], c = this.manager.tooltipViews[o], { dom: l } = c, u = e.pos[o], d = e.size[o];
			if (!u || s.clip !== !1 && (u.bottom <= Math.max(t.top, n.top) || u.top >= Math.min(t.bottom, n.bottom) || u.right < Math.max(t.left, n.left) - .1 || u.left > Math.min(t.right, n.right) + .1)) {
				l.style.top = ls;
				continue;
			}
			let f = s.arrow ? c.dom.querySelector(".cm-tooltip-arrow") : null, p = f ? 7 : 0, m = d.right - d.left, h = ps.get(c) ?? d.bottom - d.top, g = c.offset || _s, _ = this.view.textDirection == I.LTR, v = d.width > n.right - n.left ? _ ? n.left : n.right - d.width : _ ? Math.max(n.left, Math.min(u.left - (f ? 14 : 0) + g.x, n.right - m)) : Math.min(Math.max(n.left, u.left - m + (f ? 14 : 0) - g.x), n.right - m), y = this.above[o];
			!s.strictSide && (y ? u.top - h - p - g.y < n.top : u.bottom + h + p + g.y > n.bottom) && y == n.bottom - u.bottom > u.top - n.top && (y = this.above[o] = !y);
			let ee = (y ? u.top - n.top : n.bottom - u.bottom) - p;
			if (ee < h && c.resize !== !1) {
				if (ee < this.view.defaultLineHeight) {
					l.style.top = ls;
					continue;
				}
				ps.set(c, h), l.style.height = (h = ee) / i + "px";
			} else l.style.height && (l.style.height = "");
			let b = y ? u.top - h - p - g.y : u.bottom + p + g.y, x = v + m;
			if (c.overlap !== !0) for (let e of a) e.left < x && e.right > v && e.top < b + h && e.bottom > b && (b = y ? e.top - h - 2 - p : e.bottom + p + 2);
			if (this.position == "absolute" ? (l.style.top = (b - e.parent.top) / i + "px", hs(l, (v - e.parent.left) / r)) : (l.style.top = b / i + "px", hs(l, v / r)), f) {
				let e = u.left + (_ ? g.x : -g.x) - (v + 14 - 7);
				f.style.left = e / r + "px";
			}
			c.overlap !== !0 && a.push({
				left: v,
				top: b,
				right: x,
				bottom: b + h
			}), l.classList.toggle("cm-tooltip-above", y), l.classList.toggle("cm-tooltip-below", !y), c.positioned && c.positioned(e.space);
		}
	}
	maybeMeasure() {
		if (this.manager.tooltips.length && (this.view.inView && this.view.requestMeasure(this.measureReq), this.inView != this.view.inView && (this.inView = this.view.inView, !this.inView))) for (let e of this.manager.tooltipViews) e.dom.style.top = ls;
	}
}, { eventObservers: { scroll() {
	this.maybeMeasure();
} } });
function hs(e, t) {
	let n = parseInt(e.style.left, 10);
	(isNaN(n) || Math.abs(t - n) > 1) && (e.style.left = t + "px");
}
var gs = /*@__PURE__*/ V.baseTheme({
	".cm-tooltip": {
		zIndex: 500,
		boxSizing: "border-box"
	},
	"&light .cm-tooltip": {
		border: "1px solid #bbb",
		backgroundColor: "#f5f5f5"
	},
	"&light .cm-tooltip-section:not(:first-child)": { borderTop: "1px solid #bbb" },
	"&dark .cm-tooltip": {
		backgroundColor: "#333338",
		color: "white"
	},
	".cm-tooltip-arrow": {
		height: "7px",
		width: "14px",
		position: "absolute",
		zIndex: -1,
		overflow: "hidden",
		"&:before, &:after": {
			content: "''",
			position: "absolute",
			width: 0,
			height: 0,
			borderLeft: "7px solid transparent",
			borderRight: "7px solid transparent"
		},
		".cm-tooltip-above &": {
			bottom: "-7px",
			"&:before": { borderTop: "7px solid #bbb" },
			"&:after": {
				borderTop: "7px solid #f5f5f5",
				bottom: "1px"
			}
		},
		".cm-tooltip-below &": {
			top: "-7px",
			"&:before": { borderBottom: "7px solid #bbb" },
			"&:after": {
				borderBottom: "7px solid #f5f5f5",
				top: "1px"
			}
		}
	},
	"&dark .cm-tooltip .cm-tooltip-arrow": {
		"&:before": {
			borderTopColor: "#333338",
			borderBottomColor: "#333338"
		},
		"&:after": {
			borderTopColor: "transparent",
			borderBottomColor: "transparent"
		}
	}
}), _s = {
	x: 0,
	y: 0
}, vs = /*@__PURE__*/ A.define({ enables: [ms, gs] });
function ys(e, t) {
	let n = e.plugin(ms);
	if (!n) return null;
	let r = n.manager.tooltips.indexOf(t);
	return r < 0 ? null : n.manager.tooltipViews[r];
}
var bs = class extends yt {
	compare(e) {
		return this == e || this.constructor == e.constructor && this.eq(e);
	}
	eq(e) {
		return !1;
	}
	destroy(e) {}
};
bs.prototype.elementClass = "", bs.prototype.toDOM = void 0, bs.prototype.mapMode = be.TrackBefore, bs.prototype.startSide = bs.prototype.endSide = -1, bs.prototype.point = !0;
//#endregion
//#region node_modules/@lezer/common/dist/index.js
var xs = 1024, Ss = 0, Cs = class {
	constructor(e, t) {
		this.from = e, this.to = t;
	}
}, H = class {
	constructor(e = {}) {
		this.id = Ss++, this.perNode = !!e.perNode, this.deserialize = e.deserialize || (() => {
			throw Error("This node type doesn't define a deserialize function");
		}), this.combine = e.combine || null;
	}
	add(e) {
		if (this.perNode) throw RangeError("Can't add per-node props to node types");
		return typeof e != "function" && (e = U.match(e)), (t) => {
			let n = e(t);
			return n === void 0 ? null : [this, n];
		};
	}
};
H.closedBy = new H({ deserialize: (e) => e.split(" ") }), H.openedBy = new H({ deserialize: (e) => e.split(" ") }), H.group = new H({ deserialize: (e) => e.split(" ") }), H.isolate = new H({ deserialize: (e) => {
	if (e && e != "rtl" && e != "ltr" && e != "auto") throw RangeError("Invalid value for isolate: " + e);
	return e || "auto";
} }), H.contextHash = new H({ perNode: !0 }), H.lookAhead = new H({ perNode: !0 }), H.mounted = new H({ perNode: !0 });
var ws = class {
	constructor(e, t, n, r = !1) {
		this.tree = e, this.overlay = t, this.parser = n, this.bracketed = r;
	}
	static get(e) {
		return e && e.props && e.props[H.mounted.id];
	}
}, Ts = Object.create(null), U = class e {
	constructor(e, t, n, r = 0) {
		this.name = e, this.props = t, this.id = n, this.flags = r;
	}
	static define(t) {
		let n = t.props && t.props.length ? Object.create(null) : Ts, r = +!!t.top | (t.skipped ? 2 : 0) | (t.error ? 4 : 0) | (t.name == null ? 8 : 0), i = new e(t.name || "", n, t.id, r);
		if (t.props) {
			for (let e of t.props) if (Array.isArray(e) || (e = e(i)), e) {
				if (e[0].perNode) throw RangeError("Can't store a per-node prop on a node type");
				n[e[0].id] = e[1];
			}
		}
		return i;
	}
	prop(e) {
		return this.props[e.id];
	}
	get isTop() {
		return (this.flags & 1) > 0;
	}
	get isSkipped() {
		return (this.flags & 2) > 0;
	}
	get isError() {
		return (this.flags & 4) > 0;
	}
	get isAnonymous() {
		return (this.flags & 8) > 0;
	}
	is(e) {
		if (typeof e == "string") {
			if (this.name == e) return !0;
			let t = this.prop(H.group);
			return t ? t.indexOf(e) > -1 : !1;
		}
		return this.id == e;
	}
	static match(e) {
		let t = Object.create(null);
		for (let n in e) for (let r of n.split(" ")) t[r] = e[n];
		return (e) => {
			for (let n = e.prop(H.group), r = -1; r < (n ? n.length : 0); r++) {
				let i = t[r < 0 ? e.name : n[r]];
				if (i) return i;
			}
		};
	}
};
U.none = new U("", Object.create(null), 0, 8);
var Es = class e {
	constructor(e) {
		this.types = e;
		for (let t = 0; t < e.length; t++) if (e[t].id != t) throw RangeError("Node type ids should correspond to array positions when creating a node set");
	}
	extend(...t) {
		let n = [];
		for (let e of this.types) {
			let r = null;
			for (let n of t) {
				let t = n(e);
				if (t) {
					r ||= Object.assign({}, e.props);
					let n = t[1], i = t[0];
					i.combine && i.id in r && (n = i.combine(r[i.id], n)), r[i.id] = n;
				}
			}
			n.push(r ? new U(e.name, r, e.id, e.flags) : e);
		}
		return new e(n);
	}
}, Ds = /* @__PURE__ */ new WeakMap(), Os = /* @__PURE__ */ new WeakMap(), W;
(function(e) {
	e[e.ExcludeBuffers = 1] = "ExcludeBuffers", e[e.IncludeAnonymous = 2] = "IncludeAnonymous", e[e.IgnoreMounts = 4] = "IgnoreMounts", e[e.IgnoreOverlays = 8] = "IgnoreOverlays", e[e.EnterBracketed = 16] = "EnterBracketed";
})(W ||= {});
var G = class e {
	constructor(e, t, n, r, i) {
		if (this.type = e, this.children = t, this.positions = n, this.length = r, this.props = null, i && i.length) {
			this.props = Object.create(null);
			for (let [e, t] of i) this.props[typeof e == "number" ? e : e.id] = t;
		}
	}
	toString() {
		let e = ws.get(this);
		if (e && !e.overlay) return e.tree.toString();
		let t = "";
		for (let e of this.children) {
			let n = e.toString();
			n && (t && (t += ","), t += n);
		}
		return this.type.name ? (/\W/.test(this.type.name) && !this.type.isError ? JSON.stringify(this.type.name) : this.type.name) + (t.length ? "(" + t + ")" : "") : t;
	}
	cursor(e = 0) {
		return new Hs(this.topNode, e);
	}
	cursorAt(e, t = 0, n = 0) {
		let r = new Hs(Ds.get(this) || this.topNode);
		return r.moveTo(e, t), Ds.set(this, r._tree), r;
	}
	get topNode() {
		return new Ps(this, 0, 0, null);
	}
	resolve(e, t = 0) {
		let n = Ms(Ds.get(this) || this.topNode, e, t, !1);
		return Ds.set(this, n), n;
	}
	resolveInner(e, t = 0) {
		let n = Ms(Os.get(this) || this.topNode, e, t, !0);
		return Os.set(this, n), n;
	}
	resolveStack(e, t = 0) {
		return Vs(this, e, t);
	}
	iterate(e) {
		let { enter: t, leave: n, from: r = 0, to: i = this.length } = e, a = e.mode || 0, o = (a & W.IncludeAnonymous) > 0;
		for (let e = this.cursor(a | W.IncludeAnonymous);;) {
			let a = !1;
			if (e.from <= i && e.to >= r && (!o && e.type.isAnonymous || t(e) !== !1)) {
				if (e.firstChild()) continue;
				a = !0;
			}
			for (; a && n && (o || !e.type.isAnonymous) && n(e), !e.nextSibling();) {
				if (!e.parent()) return;
				a = !0;
			}
		}
	}
	prop(e) {
		return e.perNode ? this.props ? this.props[e.id] : void 0 : this.type.prop(e);
	}
	get propValues() {
		let e = [];
		if (this.props) for (let t in this.props) e.push([+t, this.props[t]]);
		return e;
	}
	balance(t = {}) {
		return this.children.length <= 8 ? this : qs(U.none, this.children, this.positions, 0, this.children.length, 0, this.length, (t, n, r) => new e(this.type, t, n, r, this.propValues), t.makeTree || ((t, n, r) => new e(U.none, t, n, r)));
	}
	static build(e) {
		return Ws(e);
	}
};
G.empty = new G(U.none, [], [], 0);
var ks = class e {
	constructor(e, t) {
		this.buffer = e, this.index = t;
	}
	get id() {
		return this.buffer[this.index - 4];
	}
	get start() {
		return this.buffer[this.index - 3];
	}
	get end() {
		return this.buffer[this.index - 2];
	}
	get size() {
		return this.buffer[this.index - 1];
	}
	get pos() {
		return this.index;
	}
	next() {
		this.index -= 4;
	}
	fork() {
		return new e(this.buffer, this.index);
	}
}, As = class e {
	constructor(e, t, n) {
		this.buffer = e, this.length = t, this.set = n;
	}
	get type() {
		return U.none;
	}
	toString() {
		let e = [];
		for (let t = 0; t < this.buffer.length;) e.push(this.childString(t)), t = this.buffer[t + 3];
		return e.join(",");
	}
	childString(e) {
		let t = this.buffer[e], n = this.buffer[e + 3], r = this.set.types[t], i = r.name;
		if (/\W/.test(i) && !r.isError && (i = JSON.stringify(i)), e += 4, n == e) return i;
		let a = [];
		for (; e < n;) a.push(this.childString(e)), e = this.buffer[e + 3];
		return i + "(" + a.join(",") + ")";
	}
	findChild(e, t, n, r, i) {
		let { buffer: a } = this, o = -1;
		for (let s = e; s != t && !(js(i, r, a[s + 1], a[s + 2]) && (o = s, n > 0)); s = a[s + 3]);
		return o;
	}
	slice(t, n, r) {
		let i = this.buffer, a = new Uint16Array(n - t), o = 0;
		for (let e = t, s = 0; e < n;) {
			a[s++] = i[e++], a[s++] = i[e++] - r;
			let n = a[s++] = i[e++] - r;
			a[s++] = i[e++] - t, o = Math.max(o, n);
		}
		return new e(a, o, this.set);
	}
};
function js(e, t, n, r) {
	switch (e) {
		case -2: return n < t;
		case -1: return r >= t && n < t;
		case 0: return n < t && r > t;
		case 1: return n <= t && r > t;
		case 2: return r > t;
		case 4: return !0;
	}
}
function Ms(e, t, n, r) {
	for (; e.from == e.to || (n < 1 ? e.from >= t : e.from > t) || (n > -1 ? e.to <= t : e.to < t);) {
		let t = !r && e instanceof Ps && e.index < 0 ? null : e.parent;
		if (!t) return e;
		e = t;
	}
	let i = r ? 0 : W.IgnoreOverlays;
	if (r) for (let r = e, a = r.parent; a; r = a, a = r.parent) r instanceof Ps && r.index < 0 && a.enter(t, n, i)?.from != r.from && (e = a);
	for (;;) {
		let r = e.enter(t, n, i);
		if (!r) return e;
		e = r;
	}
}
var Ns = class {
	cursor(e = 0) {
		return new Hs(this, e);
	}
	getChild(e, t = null, n = null) {
		let r = Fs(this, e, t, n);
		return r.length ? r[0] : null;
	}
	getChildren(e, t = null, n = null) {
		return Fs(this, e, t, n);
	}
	resolve(e, t = 0) {
		return Ms(this, e, t, !1);
	}
	resolveInner(e, t = 0) {
		return Ms(this, e, t, !0);
	}
	matchContext(e) {
		return Is(this.parent, e);
	}
	enterUnfinishedNodesBefore(e) {
		let t = this.childBefore(e), n = this;
		for (; t;) {
			let e = t.lastChild;
			if (!e || e.to != t.to) break;
			e.type.isError && e.from == e.to ? (n = t, t = e.prevSibling) : t = e;
		}
		return n;
	}
	get node() {
		return this;
	}
	get next() {
		return this.parent;
	}
}, Ps = class e extends Ns {
	constructor(e, t, n, r) {
		super(), this._tree = e, this.from = t, this.index = n, this._parent = r;
	}
	get type() {
		return this._tree.type;
	}
	get name() {
		return this._tree.type.name;
	}
	get to() {
		return this.from + this._tree.length;
	}
	nextChild(t, n, r, i, a = 0) {
		for (let o = this;;) {
			for (let { children: s, positions: c } = o._tree, l = n > 0 ? s.length : -1; t != l; t += n) {
				let l = s[t], u = c[t] + o.from, d;
				if (a & W.EnterBracketed && l instanceof G && (d = ws.get(l)) && !d.overlay && d.bracketed && r >= u && r <= u + l.length || js(i, r, u, u + l.length)) {
					if (l instanceof As) {
						if (a & W.ExcludeBuffers) continue;
						let e = l.findChild(0, l.buffer.length, n, r - u, i);
						if (e > -1) return new Rs(new Ls(o, l, t, u), null, e);
					} else if (a & W.IncludeAnonymous || !l.type.isAnonymous || Us(l)) {
						let s;
						if (!(a & W.IgnoreMounts) && (s = ws.get(l)) && !s.overlay) return new e(s.tree, u, t, o);
						let c = new e(l, u, t, o);
						return a & W.IncludeAnonymous || !c.type.isAnonymous ? c : c.nextChild(n < 0 ? l.children.length - 1 : 0, n, r, i, a);
					}
				}
			}
			if (a & W.IncludeAnonymous || !o.type.isAnonymous || (t = o.index >= 0 ? o.index + n : n < 0 ? -1 : o._parent._tree.children.length, o = o._parent, !o)) return null;
		}
	}
	get firstChild() {
		return this.nextChild(0, 1, 0, 4);
	}
	get lastChild() {
		return this.nextChild(this._tree.children.length - 1, -1, 0, 4);
	}
	childAfter(e) {
		return this.nextChild(0, 1, e, 2);
	}
	childBefore(e) {
		return this.nextChild(this._tree.children.length - 1, -1, e, -2);
	}
	prop(e) {
		return this._tree.prop(e);
	}
	enter(t, n, r = 0) {
		let i;
		if (!(r & W.IgnoreOverlays) && (i = ws.get(this._tree)) && i.overlay) {
			let a = t - this.from, o = r & W.EnterBracketed && i.bracketed;
			for (let { from: t, to: r } of i.overlay) if ((n > 0 || o ? t <= a : t < a) && (n < 0 || o ? r >= a : r > a)) return new e(i.tree, i.overlay[0].from + this.from, -1, this);
		}
		return this.nextChild(0, 1, t, n, r);
	}
	nextSignificantParent() {
		let e = this;
		for (; e.type.isAnonymous && e._parent;) e = e._parent;
		return e;
	}
	get parent() {
		return this._parent ? this._parent.nextSignificantParent() : null;
	}
	get nextSibling() {
		return this._parent && this.index >= 0 ? this._parent.nextChild(this.index + 1, 1, 0, 4) : null;
	}
	get prevSibling() {
		return this._parent && this.index >= 0 ? this._parent.nextChild(this.index - 1, -1, 0, 4) : null;
	}
	get tree() {
		return this._tree;
	}
	toTree() {
		return this._tree;
	}
	toString() {
		return this._tree.toString();
	}
};
function Fs(e, t, n, r) {
	let i = e.cursor(), a = [];
	if (!i.firstChild()) return a;
	if (n != null) {
		for (let e = !1; !e;) if (e = i.type.is(n), !i.nextSibling()) return a;
	}
	for (;;) {
		if (r != null && i.type.is(r)) return a;
		if (i.type.is(t) && a.push(i.node), !i.nextSibling()) return r == null ? a : [];
	}
}
function Is(e, t, n = t.length - 1) {
	for (let r = e; n >= 0; r = r.parent) {
		if (!r) return !1;
		if (!r.type.isAnonymous) {
			if (t[n] && t[n] != r.name) return !1;
			n--;
		}
	}
	return !0;
}
var Ls = class {
	constructor(e, t, n, r) {
		this.parent = e, this.buffer = t, this.index = n, this.start = r;
	}
}, Rs = class e extends Ns {
	get name() {
		return this.type.name;
	}
	get from() {
		return this.context.start + this.context.buffer.buffer[this.index + 1];
	}
	get to() {
		return this.context.start + this.context.buffer.buffer[this.index + 2];
	}
	constructor(e, t, n) {
		super(), this.context = e, this._parent = t, this.index = n, this.type = e.buffer.set.types[e.buffer.buffer[n]];
	}
	child(t, n, r) {
		let { buffer: i } = this.context, a = i.findChild(this.index + 4, i.buffer[this.index + 3], t, n - this.context.start, r);
		return a < 0 ? null : new e(this.context, this, a);
	}
	get firstChild() {
		return this.child(1, 0, 4);
	}
	get lastChild() {
		return this.child(-1, 0, 4);
	}
	childAfter(e) {
		return this.child(1, e, 2);
	}
	childBefore(e) {
		return this.child(-1, e, -2);
	}
	prop(e) {
		return this.type.prop(e);
	}
	enter(t, n, r = 0) {
		if (r & W.ExcludeBuffers) return null;
		let { buffer: i } = this.context, a = i.findChild(this.index + 4, i.buffer[this.index + 3], n > 0 ? 1 : -1, t - this.context.start, n);
		return a < 0 ? null : new e(this.context, this, a);
	}
	get parent() {
		return this._parent || this.context.parent.nextSignificantParent();
	}
	externalSibling(e) {
		return this._parent ? null : this.context.parent.nextChild(this.context.index + e, e, 0, 4);
	}
	get nextSibling() {
		let { buffer: t } = this.context, n = t.buffer[this.index + 3];
		return n < (this._parent ? t.buffer[this._parent.index + 3] : t.buffer.length) ? new e(this.context, this._parent, n) : this.externalSibling(1);
	}
	get prevSibling() {
		let { buffer: t } = this.context, n = this._parent ? this._parent.index + 4 : 0;
		return this.index == n ? this.externalSibling(-1) : new e(this.context, this._parent, t.findChild(n, this.index, -1, 0, 4));
	}
	get tree() {
		return null;
	}
	toTree() {
		let e = [], t = [], { buffer: n } = this.context, r = this.index + 4, i = n.buffer[this.index + 3];
		if (i > r) {
			let a = n.buffer[this.index + 1];
			e.push(n.slice(r, i, a)), t.push(0);
		}
		return new G(this.type, e, t, this.to - this.from);
	}
	toString() {
		return this.context.buffer.childString(this.index);
	}
};
function zs(e) {
	if (!e.length) return null;
	let t = 0, n = e[0];
	for (let r = 1; r < e.length; r++) {
		let i = e[r];
		(i.from > n.from || i.to < n.to) && (n = i, t = r);
	}
	let r = n instanceof Ps && n.index < 0 ? null : n.parent, i = e.slice();
	return r ? i[t] = r : i.splice(t, 1), new Bs(i, n);
}
var Bs = class {
	constructor(e, t) {
		this.heads = e, this.node = t;
	}
	get next() {
		return zs(this.heads);
	}
};
function Vs(e, t, n) {
	let r = e.resolveInner(t, n), i = null;
	for (let e = r instanceof Ps ? r : r.context.parent; e; e = e.parent) if (e.index < 0) {
		let a = e.parent;
		(i ||= [r]).push(a.resolve(t, n)), e = a;
	} else {
		let a = ws.get(e.tree);
		if (a && a.overlay && a.overlay[0].from <= t && a.overlay[a.overlay.length - 1].to >= t) {
			let o = new Ps(a.tree, a.overlay[0].from + e.from, -1, e);
			(i ||= [r]).push(Ms(o, t, n, !1));
		}
	}
	return i ? zs(i) : r;
}
var Hs = class {
	get name() {
		return this.type.name;
	}
	constructor(e, t = 0) {
		if (this.buffer = null, this.stack = [], this.index = 0, this.bufferNode = null, this.mode = t & ~W.EnterBracketed, e instanceof Ps) this.yieldNode(e);
		else {
			this._tree = e.context.parent, this.buffer = e.context;
			for (let t = e._parent; t; t = t._parent) this.stack.unshift(t.index);
			this.bufferNode = e, this.yieldBuf(e.index);
		}
	}
	yieldNode(e) {
		return e ? (this._tree = e, this.type = e.type, this.from = e.from, this.to = e.to, !0) : !1;
	}
	yieldBuf(e, t) {
		this.index = e;
		let { start: n, buffer: r } = this.buffer;
		return this.type = t || r.set.types[r.buffer[e]], this.from = n + r.buffer[e + 1], this.to = n + r.buffer[e + 2], !0;
	}
	yield(e) {
		return e ? e instanceof Ps ? (this.buffer = null, this.yieldNode(e)) : (this.buffer = e.context, this.yieldBuf(e.index, e.type)) : !1;
	}
	toString() {
		return this.buffer ? this.buffer.buffer.childString(this.index) : this._tree.toString();
	}
	enterChild(e, t, n) {
		if (!this.buffer) return this.yield(this._tree.nextChild(e < 0 ? this._tree._tree.children.length - 1 : 0, e, t, n, this.mode));
		let { buffer: r } = this.buffer, i = r.findChild(this.index + 4, r.buffer[this.index + 3], e, t - this.buffer.start, n);
		return i < 0 ? !1 : (this.stack.push(this.index), this.yieldBuf(i));
	}
	firstChild() {
		return this.enterChild(1, 0, 4);
	}
	lastChild() {
		return this.enterChild(-1, 0, 4);
	}
	childAfter(e) {
		return this.enterChild(1, e, 2);
	}
	childBefore(e) {
		return this.enterChild(-1, e, -2);
	}
	enter(e, t, n = this.mode) {
		return this.buffer ? n & W.ExcludeBuffers ? !1 : this.enterChild(1, e, t) : this.yield(this._tree.enter(e, t, n));
	}
	parent() {
		if (!this.buffer) return this.yieldNode(this.mode & W.IncludeAnonymous ? this._tree._parent : this._tree.parent);
		if (this.stack.length) return this.yieldBuf(this.stack.pop());
		let e = this.mode & W.IncludeAnonymous ? this.buffer.parent : this.buffer.parent.nextSignificantParent();
		return this.buffer = null, this.yieldNode(e);
	}
	sibling(e) {
		if (!this.buffer) return this._tree._parent ? this.yield(this._tree.index < 0 ? null : this._tree._parent.nextChild(this._tree.index + e, e, 0, 4, this.mode)) : !1;
		let { buffer: t } = this.buffer, n = this.stack.length - 1;
		if (e < 0) {
			let e = n < 0 ? 0 : this.stack[n] + 4;
			if (this.index != e) return this.yieldBuf(t.findChild(e, this.index, -1, 0, 4));
		} else {
			let e = t.buffer[this.index + 3];
			if (e < (n < 0 ? t.buffer.length : t.buffer[this.stack[n] + 3])) return this.yieldBuf(e);
		}
		return n < 0 && this.yield(this.buffer.parent.nextChild(this.buffer.index + e, e, 0, 4, this.mode));
	}
	nextSibling() {
		return this.sibling(1);
	}
	prevSibling() {
		return this.sibling(-1);
	}
	atLastNode(e) {
		let t, n, { buffer: r } = this;
		if (r) {
			if (e > 0) {
				if (this.index < r.buffer.buffer.length) return !1;
			} else for (let e = 0; e < this.index; e++) if (r.buffer.buffer[e + 3] < this.index) return !1;
			({index: t, parent: n} = r);
		} else ({index: t, _parent: n} = this._tree);
		for (; n; {index: t, _parent: n} = n) if (t > -1) for (let r = t + e, i = e < 0 ? -1 : n._tree.children.length; r != i; r += e) {
			let e = n._tree.children[r];
			if (this.mode & W.IncludeAnonymous || e instanceof As || !e.type.isAnonymous || Us(e)) return !1;
		}
		return !0;
	}
	move(e, t) {
		if (t && this.enterChild(e, 0, 4)) return !0;
		for (;;) {
			if (this.sibling(e)) return !0;
			if (this.atLastNode(e) || !this.parent()) return !1;
		}
	}
	next(e = !0) {
		return this.move(1, e);
	}
	prev(e = !0) {
		return this.move(-1, e);
	}
	moveTo(e, t = 0) {
		for (; (this.from == this.to || (t < 1 ? this.from >= e : this.from > e) || (t > -1 ? this.to <= e : this.to < e)) && this.parent(););
		for (; this.enterChild(1, e, t););
		return this;
	}
	get node() {
		if (!this.buffer) return this._tree;
		let e = this.bufferNode, t = null, n = 0;
		if (e && e.context == this.buffer) scan: for (let r = this.index, i = this.stack.length; i >= 0;) {
			for (let a = e; a; a = a._parent) if (a.index == r) {
				if (r == this.index) return a;
				t = a, n = i + 1;
				break scan;
			}
			r = this.stack[--i];
		}
		for (let e = n; e < this.stack.length; e++) t = new Rs(this.buffer, t, this.stack[e]);
		return this.bufferNode = new Rs(this.buffer, t, this.index);
	}
	get tree() {
		return this.buffer ? null : this._tree._tree;
	}
	iterate(e, t) {
		for (let n = 0;;) {
			let r = !1;
			if (this.type.isAnonymous || e(this) !== !1) {
				if (this.firstChild()) {
					n++;
					continue;
				}
				this.type.isAnonymous || (r = !0);
			}
			for (;;) {
				if (r && t && t(this), r = this.type.isAnonymous, !n) return;
				if (this.nextSibling()) break;
				this.parent(), n--, r = !0;
			}
		}
	}
	matchContext(e) {
		if (!this.buffer) return Is(this.node.parent, e);
		let { buffer: t } = this.buffer, { types: n } = t.set;
		for (let r = e.length - 1, i = this.stack.length - 1; r >= 0; i--) {
			if (i < 0) return Is(this._tree, e, r);
			let a = n[t.buffer[this.stack[i]]];
			if (!a.isAnonymous) {
				if (e[r] && e[r] != a.name) return !1;
				r--;
			}
		}
		return !0;
	}
};
function Us(e) {
	return e.children.some((e) => e instanceof As || !e.type.isAnonymous || Us(e));
}
function Ws(e) {
	let { buffer: t, nodeSet: n, maxBufferLength: r = xs, reused: i = [], minRepeatType: a = n.types.length } = e, o = Array.isArray(t) ? new ks(t, t.length) : t, s = n.types, c = 0, l = 0;
	function u(e, t, _, v, y, ee) {
		let { id: b, start: x, end: te, size: ne } = o, re = l, S = c;
		if (ne < 0) {
			if (o.next(), ne == -1) {
				let t = i[b];
				_.push(t), v.push(x - e);
				return;
			}
			if (ne == -3) {
				c = b;
				return;
			}
			if (ne == -4) {
				l = b;
				return;
			}
			throw RangeError(`Unrecognized record size: ${ne}`);
		}
		let C = s[b], ie, w, T = x - e;
		if (te - x <= r && (w = h(o.pos - t, y))) {
			let t = new Uint16Array(w.size - w.skip), r = o.pos - w.size, i = t.length;
			for (; o.pos > r;) i = g(w.start, t, i);
			ie = new As(t, te - w.start, n), T = w.start - e;
		} else {
			let e = o.pos - ne;
			o.next();
			let t = [], n = [], i = b >= a ? b : -1, s = 0, c = te;
			for (; o.pos > e;) i >= 0 && o.id == i && o.size >= 0 ? (o.end <= c - r && (p(t, n, x, s, o.end, c, i, re, S), s = t.length, c = o.end), o.next()) : ee > 2500 ? d(x, e, t, n) : u(x, e, t, n, i, ee + 1);
			if (i >= 0 && s > 0 && s < t.length && p(t, n, x, s, x, c, i, re, S), t.reverse(), n.reverse(), i > -1 && s > 0) {
				let e = f(C, S);
				ie = qs(C, t, n, 0, t.length, 0, te - x, e, e);
			} else ie = m(C, t, n, te - x, re - te, S);
		}
		_.push(ie), v.push(T);
	}
	function d(e, t, i, a) {
		let s = [], c = 0, l = -1;
		for (; o.pos > t;) {
			let { id: e, start: t, end: n, size: i } = o;
			if (i > 4) o.next();
			else if (l > -1 && t < l) break;
			else l < 0 && (l = n - r), s.push(e, t, n), c++, o.next();
		}
		if (c) {
			let t = new Uint16Array(c * 4), r = s[s.length - 2];
			for (let e = s.length - 3, n = 0; e >= 0; e -= 3) t[n++] = s[e], t[n++] = s[e + 1] - r, t[n++] = s[e + 2] - r, t[n++] = n;
			i.push(new As(t, s[2] - r, n)), a.push(r - e);
		}
	}
	function f(e, t) {
		return (n, r, i) => {
			let a = 0, o = n.length - 1, s, c;
			if (o >= 0 && (s = n[o]) instanceof G) {
				if (!o && s.type == e && s.length == i) return s;
				(c = s.prop(H.lookAhead)) && (a = r[o] + s.length + c);
			}
			return m(e, n, r, i, a, t);
		};
	}
	function p(e, t, r, i, a, o, s, c, l) {
		let u = [], d = [];
		for (; e.length > i;) u.push(e.pop()), d.push(t.pop() + r - a);
		e.push(m(n.types[s], u, d, o - a, c - o, l)), t.push(a - r);
	}
	function m(e, t, n, r, i, a, o) {
		if (a) {
			let e = [H.contextHash, a];
			o = o ? [e].concat(o) : [e];
		}
		if (i > 25) {
			let e = [H.lookAhead, i];
			o = o ? [e].concat(o) : [e];
		}
		return new G(e, t, n, r, o);
	}
	function h(e, t) {
		let n = o.fork(), i = 0, s = 0, c = 0, l = n.end - r, u = {
			size: 0,
			start: 0,
			skip: 0
		};
		scan: for (let r = n.pos - e; n.pos > r;) {
			let e = n.size;
			if (n.id == t && e >= 0) {
				u.size = i, u.start = s, u.skip = c, c += 4, i += 4, n.next();
				continue;
			}
			let o = n.pos - e;
			if (e < 0 || o < r || n.start < l) break;
			let d = n.id >= a ? 4 : 0, f = n.start;
			for (n.next(); n.pos > o;) {
				if (n.size < 0) {
					if (n.size == -3 || n.size == -4) d += 4;
					else break scan;
				} else n.id >= a && (d += 4);
				n.next();
			}
			s = f, i += e, c += d;
		}
		return (t < 0 || i == e) && (u.size = i, u.start = s, u.skip = c), u.size > 4 ? u : void 0;
	}
	function g(e, t, n) {
		let { id: r, start: i, end: s, size: u } = o;
		if (o.next(), u >= 0 && r < a) {
			let a = n;
			if (u > 4) {
				let r = o.pos - (u - 4);
				for (; o.pos > r;) n = g(e, t, n);
			}
			t[--n] = a, t[--n] = s - e, t[--n] = i - e, t[--n] = r;
		} else u == -3 ? c = r : u == -4 && (l = r);
		return n;
	}
	let _ = [], v = [];
	for (; o.pos > 0;) u(e.start || 0, e.bufferStart || 0, _, v, -1, 0);
	let y = e.length ?? (_.length ? v[0] + _[0].length : 0);
	return new G(s[e.topID], _.reverse(), v.reverse(), y);
}
var Gs = /* @__PURE__ */ new WeakMap();
function Ks(e, t) {
	if (!e.isAnonymous || t instanceof As || t.type != e) return 1;
	let n = Gs.get(t);
	if (n == null) {
		n = 1;
		for (let r of t.children) {
			if (r.type != e || !(r instanceof G)) {
				n = 1;
				break;
			}
			n += Ks(e, r);
		}
		Gs.set(t, n);
	}
	return n;
}
function qs(e, t, n, r, i, a, o, s, c) {
	let l = 0;
	for (let n = r; n < i; n++) l += Ks(e, t[n]);
	let u = Math.ceil(l * 1.5 / 8), d = [], f = [];
	function p(t, n, r, i, o) {
		for (let s = r; s < i;) {
			let r = s, l = n[s], m = Ks(e, t[s]);
			for (s++; s < i; s++) {
				let n = Ks(e, t[s]);
				if (m + n >= u) break;
				m += n;
			}
			if (s == r + 1) {
				if (m > u) {
					let e = t[r];
					p(e.children, e.positions, 0, e.children.length, n[r] + o);
					continue;
				}
				d.push(t[r]);
			} else {
				let i = n[s - 1] + t[s - 1].length - l;
				d.push(qs(e, t, n, r, s, l, i, null, c));
			}
			f.push(l + o - a);
		}
	}
	return p(t, n, r, i, 0), (s || c)(d, f, o);
}
var Js = class {
	constructor() {
		this.map = /* @__PURE__ */ new WeakMap();
	}
	setBuffer(e, t, n) {
		let r = this.map.get(e);
		r || this.map.set(e, r = /* @__PURE__ */ new Map()), r.set(t, n);
	}
	getBuffer(e, t) {
		let n = this.map.get(e);
		return n && n.get(t);
	}
	set(e, t) {
		e instanceof Rs ? this.setBuffer(e.context.buffer, e.index, t) : e instanceof Ps && this.map.set(e.tree, t);
	}
	get(e) {
		return e instanceof Rs ? this.getBuffer(e.context.buffer, e.index) : e instanceof Ps ? this.map.get(e.tree) : void 0;
	}
	cursorSet(e, t) {
		e.buffer ? this.setBuffer(e.buffer.buffer, e.index, t) : this.map.set(e.tree, t);
	}
	cursorGet(e) {
		return e.buffer ? this.getBuffer(e.buffer.buffer, e.index) : this.map.get(e.tree);
	}
}, Ys = class e {
	constructor(e, t, n, r, i = !1, a = !1) {
		this.from = e, this.to = t, this.tree = n, this.offset = r, this.open = !!i | (a ? 2 : 0);
	}
	get openStart() {
		return (this.open & 1) > 0;
	}
	get openEnd() {
		return (this.open & 2) > 0;
	}
	static addTree(t, n = [], r = !1) {
		let i = [new e(0, t.length, t, 0, !1, r)];
		for (let e of n) e.to > t.length && i.push(e);
		return i;
	}
	static applyChanges(t, n, r = 128) {
		if (!n.length) return t;
		let i = [], a = 1, o = t.length ? t[0] : null;
		for (let s = 0, c = 0, l = 0;; s++) {
			let u = s < n.length ? n[s] : null, d = u ? u.fromA : 1e9;
			if (d - c >= r) for (; o && o.from < d;) {
				let n = o;
				if (c >= n.from || d <= n.to || l) {
					let t = Math.max(n.from, c) - l, r = Math.min(n.to, d) - l;
					n = t >= r ? null : new e(t, r, n.tree, n.offset + l, s > 0, !!u);
				}
				if (n && i.push(n), o.to > d) break;
				o = a < t.length ? t[a++] : null;
			}
			if (!u) break;
			c = u.toA, l = u.toA - u.toB;
		}
		return i;
	}
}, Xs = class {
	startParse(e, t, n) {
		return typeof e == "string" && (e = new Zs(e)), n = n ? n.length ? n.map((e) => new Cs(e.from, e.to)) : [new Cs(0, 0)] : [new Cs(0, e.length)], this.createParse(e, t || [], n);
	}
	parse(e, t, n) {
		let r = this.startParse(e, t, n);
		for (;;) {
			let e = r.advance();
			if (e) return e;
		}
	}
}, Zs = class {
	constructor(e) {
		this.string = e;
	}
	get length() {
		return this.string.length;
	}
	chunk(e) {
		return this.string.slice(e);
	}
	get lineChunks() {
		return !1;
	}
	read(e, t) {
		return this.string.slice(e, t);
	}
};
function Qs(e) {
	return (t, n, r, i) => new rc(t, e, n, r, i);
}
var $s = class {
	constructor(e, t, n, r, i, a) {
		this.parser = e, this.parse = t, this.overlay = n, this.bracketed = r, this.target = i, this.from = a;
	}
};
function ec(e) {
	if (!e.length || e.some((e) => e.from >= e.to)) throw RangeError("Invalid inner parse ranges given: " + JSON.stringify(e));
}
var tc = class {
	constructor(e, t, n, r, i, a, o, s) {
		this.parser = e, this.predicate = t, this.mounts = n, this.index = r, this.start = i, this.bracketed = a, this.target = o, this.prev = s, this.depth = 0, this.ranges = [];
	}
}, nc = new H({ perNode: !0 }), rc = class {
	constructor(e, t, n, r, i) {
		this.nest = t, this.input = n, this.fragments = r, this.ranges = i, this.inner = [], this.innerDone = 0, this.baseTree = null, this.stoppedAt = null, this.baseParse = e;
	}
	advance() {
		if (this.baseParse) {
			let e = this.baseParse.advance();
			if (!e) return null;
			if (this.baseParse = null, this.baseTree = e, this.startInner(), this.stoppedAt != null) for (let e of this.inner) e.parse.stopAt(this.stoppedAt);
		}
		if (this.innerDone == this.inner.length) {
			let e = this.baseTree;
			return this.stoppedAt != null && (e = new G(e.type, e.children, e.positions, e.length, e.propValues.concat([[nc, this.stoppedAt]]))), e;
		}
		let e = this.inner[this.innerDone], t = e.parse.advance();
		if (t) {
			this.innerDone++;
			let n = Object.assign(Object.create(null), e.target.props);
			n[H.mounted.id] = new ws(t, e.overlay, e.parser, e.bracketed), e.target.props = n;
		}
		return null;
	}
	get parsedPos() {
		if (this.baseParse) return 0;
		let e = this.input.length;
		for (let t = this.innerDone; t < this.inner.length; t++) this.inner[t].from < e && (e = Math.min(e, this.inner[t].parse.parsedPos));
		return e;
	}
	stopAt(e) {
		if (this.stoppedAt = e, this.baseParse) this.baseParse.stopAt(e);
		else for (let t = this.innerDone; t < this.inner.length; t++) this.inner[t].parse.stopAt(e);
	}
	startInner() {
		let e = new cc(this.fragments), t = null, n = null, r = new Hs(new Ps(this.baseTree, this.ranges[0].from, 0, null), W.IncludeAnonymous | W.IgnoreMounts);
		scan: for (let i, a;;) {
			let o = !0, s;
			if (this.stoppedAt != null && r.from >= this.stoppedAt) o = !1;
			else if (e.hasNode(r)) {
				if (t) {
					let e = t.mounts.find((e) => e.frag.from <= r.from && e.frag.to >= r.to && e.mount.overlay);
					if (e) for (let n of e.mount.overlay) {
						let i = n.from + e.pos, a = n.to + e.pos;
						i >= r.from && a <= r.to && !t.ranges.some((e) => e.from < a && e.to > i) && t.ranges.push({
							from: i,
							to: a
						});
					}
				}
				o = !1;
			} else if (n && (a = ic(n.ranges, r.from, r.to))) o = a != 2;
			else if (!r.type.isAnonymous && (i = this.nest(r, this.input)) && (r.from < r.to || !i.overlay)) {
				r.tree || (oc(r), t && t.depth++, n && n.depth++);
				let a = e.findMounts(r.from, i.parser);
				if (typeof i.overlay == "function") t = new tc(i.parser, i.overlay, a, this.inner.length, r.from, !!i.bracketed, r.tree, t);
				else {
					let e = lc(this.ranges, i.overlay || (r.from < r.to ? [new Cs(r.from, r.to)] : []));
					e.length && ec(e), (e.length || !i.overlay) && this.inner.push(new $s(i.parser, e.length ? i.parser.startParse(this.input, dc(a, e), e) : i.parser.startParse(""), i.overlay ? i.overlay.map((e) => new Cs(e.from - r.from, e.to - r.from)) : null, !!i.bracketed, r.tree, e.length ? e[0].from : r.from)), i.overlay ? e.length && (n = {
						ranges: e,
						depth: 0,
						prev: n
					}) : o = !1;
				}
			} else if (t && (s = t.predicate(r)) && (s === !0 && (s = new Cs(r.from, r.to)), s.from < s.to)) {
				let e = t.ranges.length - 1;
				e >= 0 && t.ranges[e].to == s.from ? t.ranges[e] = {
					from: t.ranges[e].from,
					to: s.to
				} : t.ranges.push(s);
			}
			if (o && r.firstChild()) t && t.depth++, n && n.depth++;
			else for (; !r.nextSibling();) {
				if (!r.parent()) break scan;
				if (t && !--t.depth) {
					let e = lc(this.ranges, t.ranges);
					e.length && (ec(e), this.inner.splice(t.index, 0, new $s(t.parser, t.parser.startParse(this.input, dc(t.mounts, e), e), t.ranges.map((e) => new Cs(e.from - t.start, e.to - t.start)), t.bracketed, t.target, e[0].from))), t = t.prev;
				}
				n && !--n.depth && (n = n.prev);
			}
		}
	}
};
function ic(e, t, n) {
	for (let r of e) {
		if (r.from >= n) break;
		if (r.to > t) return r.from <= t && r.to >= n ? 2 : 1;
	}
	return 0;
}
function ac(e, t, n, r, i, a) {
	if (t < n) {
		let o = e.buffer[t + 1];
		r.push(e.slice(t, n, o)), i.push(o - a);
	}
}
function oc(e) {
	let { node: t } = e, n = [], r = t.context.buffer;
	do
		n.push(e.index), e.parent();
	while (!e.tree);
	let i = e.tree, a = i.children.indexOf(r), o = i.children[a], s = o.buffer, c = [a];
	function l(e, r, i, a, u, d) {
		let f = n[d], p = [], m = [];
		ac(o, e, f, p, m, a);
		let h = s[f + 1], g = s[f + 2];
		c.push(p.length);
		let _ = d ? l(f + 4, s[f + 3], o.set.types[s[f]], h, g - h, d - 1) : t.toTree();
		return p.push(_), m.push(h - a), ac(o, s[f + 3], r, p, m, a), new G(i, p, m, u);
	}
	i.children[a] = l(0, s.length, U.none, 0, o.length, n.length - 1);
	for (let t of c) {
		let n = e.tree.children[t], r = e.tree.positions[t];
		e.yield(new Ps(n, r + e.from, t, e._tree));
	}
}
var sc = class {
	constructor(e, t) {
		this.offset = t, this.done = !1, this.cursor = e.cursor(W.IncludeAnonymous | W.IgnoreMounts);
	}
	moveTo(e) {
		let { cursor: t } = this, n = e - this.offset;
		for (; !this.done && t.from < n;) if (!(t.to >= e && t.enter(n, 1, W.IgnoreOverlays | W.ExcludeBuffers))) {
			if (t.to <= e) t.next(!1) || (this.done = !0);
			else break;
		}
	}
	hasNode(e) {
		if (this.moveTo(e.from), !this.done && this.cursor.from + this.offset == e.from && this.cursor.tree) for (let t = this.cursor.tree;;) {
			if (t == e.tree) return !0;
			if (t.children.length && t.positions[0] == 0 && t.children[0] instanceof G) t = t.children[0];
			else break;
		}
		return !1;
	}
}, cc = class {
	constructor(e) {
		if (this.fragments = e, this.curTo = 0, this.fragI = 0, e.length) {
			let t = this.curFrag = e[0];
			this.curTo = t.tree.prop(nc) ?? t.to, this.inner = new sc(t.tree, -t.offset);
		} else this.curFrag = this.inner = null;
	}
	hasNode(e) {
		for (; this.curFrag && e.from >= this.curTo;) this.nextFrag();
		return this.curFrag && this.curFrag.from <= e.from && this.curTo >= e.to && this.inner.hasNode(e);
	}
	nextFrag() {
		if (this.fragI++, this.fragI == this.fragments.length) this.curFrag = this.inner = null;
		else {
			let e = this.curFrag = this.fragments[this.fragI];
			this.curTo = e.tree.prop(nc) ?? e.to, this.inner = new sc(e.tree, -e.offset);
		}
	}
	findMounts(e, t) {
		let n = [];
		if (this.inner) {
			this.inner.cursor.moveTo(e, 1);
			for (let e = this.inner.cursor.node; e; e = e.parent) {
				let r = e.tree?.prop(H.mounted);
				if (r && r.parser == t) for (let t = this.fragI; t < this.fragments.length; t++) {
					let i = this.fragments[t];
					if (i.from >= e.to) break;
					i.tree == this.curFrag.tree && n.push({
						frag: i,
						pos: e.from - i.offset,
						mount: r
					});
				}
			}
		}
		return n;
	}
};
function lc(e, t) {
	let n = null, r = t;
	for (let i = 1, a = 0; i < e.length; i++) {
		let o = e[i - 1].to, s = e[i].from;
		for (; a < r.length; a++) {
			let e = r[a];
			if (e.from >= s) break;
			e.to <= o || (n || (r = n = t.slice()), e.from < o ? (n[a] = new Cs(e.from, o), e.to > s && n.splice(a + 1, 0, new Cs(s, e.to))) : e.to > s ? n[a--] = new Cs(s, e.to) : n.splice(a--, 1));
		}
	}
	return r;
}
function uc(e, t, n, r) {
	let i = 0, a = 0, o = !1, s = !1, c = -1e9, l = [];
	for (;;) {
		let u = i == e.length ? 1e9 : o ? e[i].to : e[i].from, d = a == t.length ? 1e9 : s ? t[a].to : t[a].from;
		if (o != s) {
			let e = Math.max(c, n), t = Math.min(u, d, r);
			e < t && l.push(new Cs(e, t));
		}
		if (c = Math.min(u, d), c == 1e9) break;
		u == c && (o ? (o = !1, i++) : o = !0), d == c && (s ? (s = !1, a++) : s = !0);
	}
	return l;
}
function dc(e, t) {
	let n = [];
	for (let { pos: r, mount: i, frag: a } of e) {
		let e = r + (i.overlay ? i.overlay[0].from : 0), o = e + i.tree.length, s = Math.max(a.from, e), c = Math.min(a.to, o);
		if (i.overlay) {
			let o = uc(t, i.overlay.map((e) => new Cs(e.from + r, e.to + r)), s, c);
			for (let t = 0, r = s;; t++) {
				let s = t == o.length, l = s ? c : o[t].from;
				if (l > r && n.push(new Ys(r, l, i.tree, -e, a.from >= r || a.openStart, a.to <= l || a.openEnd)), s) break;
				r = o[t].to;
			}
		} else n.push(new Ys(s, c, i.tree, -e, a.from >= e || a.openStart, a.to <= o || a.openEnd));
	}
	return n;
}
//#endregion
//#region node_modules/@lezer/highlight/dist/index.js
var fc = 0, pc = class e {
	constructor(e, t, n, r) {
		this.name = e, this.set = t, this.base = n, this.modified = r, this.id = fc++;
	}
	toString() {
		let { name: e } = this;
		for (let t of this.modified) t.name && (e = `${t.name}(${e})`);
		return e;
	}
	static define(t, n) {
		let r = typeof t == "string" ? t : "?";
		if (t instanceof e && (n = t), n?.base) throw Error("Can not derive from a modified tag");
		let i = new e(r, [], null, []);
		if (i.set.push(i), n) for (let e of n.set) i.set.push(e);
		return i;
	}
	static defineModifier(e) {
		let t = new hc(e);
		return (e) => e.modified.indexOf(t) > -1 ? e : hc.get(e.base || e, e.modified.concat(t).sort((e, t) => e.id - t.id));
	}
}, mc = 0, hc = class e {
	constructor(e) {
		this.name = e, this.instances = [], this.id = mc++;
	}
	static get(t, n) {
		if (!n.length) return t;
		let r = n[0].instances.find((e) => e.base == t && gc(n, e.modified));
		if (r) return r;
		let i = [], a = new pc(t.name, i, t, n);
		for (let e of n) e.instances.push(a);
		let o = _c(n);
		for (let n of t.set) if (!n.modified.length) for (let t of o) i.push(e.get(n, t));
		return a;
	}
};
function gc(e, t) {
	return e.length == t.length && e.every((e, n) => e == t[n]);
}
function _c(e) {
	let t = [[]];
	for (let n = 0; n < e.length; n++) for (let r = 0, i = t.length; r < i; r++) t.push(t[r].concat(e[n]));
	return t.sort((e, t) => t.length - e.length);
}
function vc(e) {
	let t = Object.create(null);
	for (let n in e) {
		let r = e[n];
		Array.isArray(r) || (r = [r]);
		for (let e of n.split(" ")) if (e) {
			let n = [], i = 2, a = e;
			for (let t = 0;;) {
				if (a == "..." && t > 0 && t + 3 == e.length) {
					i = 1;
					break;
				}
				let r = /^"(?:[^"\\]|\\.)*?"|[^\/!]+/.exec(a);
				if (!r) throw RangeError("Invalid path: " + e);
				if (n.push(r[0] == "*" ? "" : r[0][0] == "\"" ? JSON.parse(r[0]) : r[0]), t += r[0].length, t == e.length) break;
				let o = e[t++];
				if (t == e.length && o == "!") {
					i = 0;
					break;
				}
				if (o != "/") throw RangeError("Invalid path: " + e);
				a = e.slice(t);
			}
			let o = n.length - 1, s = n[o];
			if (!s) throw RangeError("Invalid path: " + e);
			t[s] = new bc(r, i, o > 0 ? n.slice(0, o) : null).sort(t[s]);
		}
	}
	return yc.add(t);
}
var yc = new H({ combine(e, t) {
	let n, r, i;
	for (; e || t;) {
		if (!e || t && e.depth >= t.depth ? (i = t, t = t.next) : (i = e, e = e.next), n && n.mode == i.mode && !i.context && !n.context) continue;
		let a = new bc(i.tags, i.mode, i.context);
		n ? n.next = a : r = a, n = a;
	}
	return r;
} }), bc = class {
	constructor(e, t, n, r) {
		this.tags = e, this.mode = t, this.context = n, this.next = r;
	}
	get opaque() {
		return this.mode == 0;
	}
	get inherit() {
		return this.mode == 1;
	}
	sort(e) {
		return !e || e.depth < this.depth ? (this.next = e, this) : (e.next = this.sort(e.next), e);
	}
	get depth() {
		return this.context ? this.context.length : 0;
	}
};
bc.empty = new bc([], 2, null);
function xc(e, t) {
	let n = Object.create(null);
	for (let t of e) if (!Array.isArray(t.tag)) n[t.tag.id] = t.class;
	else for (let e of t.tag) n[e.id] = t.class;
	let { scope: r, all: i = null } = t || {};
	return {
		style: (e) => {
			let t = i;
			for (let r of e) for (let e of r.set) {
				let r = n[e.id];
				if (r) {
					t = t ? t + " " + r : r;
					break;
				}
			}
			return t;
		},
		scope: r
	};
}
function Sc(e, t) {
	let n = null;
	for (let r of e) {
		let e = r.style(t);
		e && (n = n ? n + " " + e : e);
	}
	return n;
}
function Cc(e, t, n, r = 0, i = e.length) {
	let a = new wc(r, Array.isArray(t) ? t : [t], n);
	a.highlightRange(e.cursor(), r, i, "", a.highlighters), a.flush(i);
}
var wc = class {
	constructor(e, t, n) {
		this.at = e, this.highlighters = t, this.span = n, this.class = "";
	}
	startSpan(e, t) {
		t != this.class && (this.flush(e), e > this.at && (this.at = e), this.class = t);
	}
	flush(e) {
		e > this.at && this.class && this.span(this.at, e, this.class);
	}
	highlightRange(e, t, n, r, i) {
		let { type: a, from: o, to: s } = e;
		if (o >= n || s <= t) return;
		a.isTop && (i = this.highlighters.filter((e) => !e.scope || e.scope(a)));
		let c = r, l = Tc(e) || bc.empty, u = Sc(i, l.tags);
		if (u && (c && (c += " "), c += u, l.mode == 1 && (r += (r ? " " : "") + u)), this.startSpan(Math.max(t, o), c), l.opaque) return;
		let d = e.tree && e.tree.prop(H.mounted);
		if (d && d.overlay) {
			let a = e.node.enter(d.overlay[0].from + o, 1), l = this.highlighters.filter((e) => !e.scope || e.scope(d.tree.type)), u = e.firstChild();
			for (let f = 0, p = o;; f++) {
				let m = f < d.overlay.length ? d.overlay[f] : null, h = m ? m.from + o : s, g = Math.max(t, p), _ = Math.min(n, h);
				if (g < _ && u) for (; e.from < _ && (this.highlightRange(e, g, _, r, i), this.startSpan(Math.min(_, e.to), c), !(e.to >= h || !e.nextSibling())););
				if (!m || h > n) break;
				p = m.to + o, p > t && (this.highlightRange(a.cursor(), Math.max(t, m.from + o), Math.min(n, p), "", l), this.startSpan(Math.min(n, p), c));
			}
			u && e.parent();
		} else if (e.firstChild()) {
			d && (r = "");
			do
				if (!(e.to <= t)) {
					if (e.from >= n) break;
					this.highlightRange(e, t, n, r, i), this.startSpan(Math.min(n, e.to), c);
				}
			while (e.nextSibling());
			e.parent();
		}
	}
};
function Tc(e) {
	let t = e.type.prop(yc);
	for (; t && t.context && !e.matchContext(t.context);) t = t.next;
	return t || null;
}
var K = pc.define, Ec = K(), Dc = K(), Oc = K(Dc), kc = K(Dc), Ac = K(), jc = K(Ac), Mc = K(Ac), Nc = K(), Pc = K(Nc), Fc = K(), Ic = K(), Lc = K(), Rc = K(Lc), zc = K(), q = {
	comment: Ec,
	lineComment: K(Ec),
	blockComment: K(Ec),
	docComment: K(Ec),
	name: Dc,
	variableName: K(Dc),
	typeName: Oc,
	tagName: K(Oc),
	propertyName: kc,
	attributeName: K(kc),
	className: K(Dc),
	labelName: K(Dc),
	namespace: K(Dc),
	macroName: K(Dc),
	literal: Ac,
	string: jc,
	docString: K(jc),
	character: K(jc),
	attributeValue: K(jc),
	number: Mc,
	integer: K(Mc),
	float: K(Mc),
	bool: K(Ac),
	regexp: K(Ac),
	escape: K(Ac),
	color: K(Ac),
	url: K(Ac),
	keyword: Fc,
	self: K(Fc),
	null: K(Fc),
	atom: K(Fc),
	unit: K(Fc),
	modifier: K(Fc),
	operatorKeyword: K(Fc),
	controlKeyword: K(Fc),
	definitionKeyword: K(Fc),
	moduleKeyword: K(Fc),
	operator: Ic,
	derefOperator: K(Ic),
	arithmeticOperator: K(Ic),
	logicOperator: K(Ic),
	bitwiseOperator: K(Ic),
	compareOperator: K(Ic),
	updateOperator: K(Ic),
	definitionOperator: K(Ic),
	typeOperator: K(Ic),
	controlOperator: K(Ic),
	punctuation: Lc,
	separator: K(Lc),
	bracket: Rc,
	angleBracket: K(Rc),
	squareBracket: K(Rc),
	paren: K(Rc),
	brace: K(Rc),
	content: Nc,
	heading: Pc,
	heading1: K(Pc),
	heading2: K(Pc),
	heading3: K(Pc),
	heading4: K(Pc),
	heading5: K(Pc),
	heading6: K(Pc),
	contentSeparator: K(Nc),
	list: K(Nc),
	quote: K(Nc),
	emphasis: K(Nc),
	strong: K(Nc),
	link: K(Nc),
	monospace: K(Nc),
	strikethrough: K(Nc),
	inserted: K(),
	deleted: K(),
	changed: K(),
	invalid: K(),
	meta: zc,
	documentMeta: K(zc),
	annotation: K(zc),
	processingInstruction: K(zc),
	definition: pc.defineModifier("definition"),
	constant: pc.defineModifier("constant"),
	function: pc.defineModifier("function"),
	standard: pc.defineModifier("standard"),
	local: pc.defineModifier("local"),
	special: pc.defineModifier("special")
};
for (let e in q) {
	let t = q[e];
	t instanceof pc && (t.name = e);
}
xc([
	{
		tag: q.link,
		class: "tok-link"
	},
	{
		tag: q.heading,
		class: "tok-heading"
	},
	{
		tag: q.emphasis,
		class: "tok-emphasis"
	},
	{
		tag: q.strong,
		class: "tok-strong"
	},
	{
		tag: q.keyword,
		class: "tok-keyword"
	},
	{
		tag: q.atom,
		class: "tok-atom"
	},
	{
		tag: q.bool,
		class: "tok-bool"
	},
	{
		tag: q.url,
		class: "tok-url"
	},
	{
		tag: q.labelName,
		class: "tok-labelName"
	},
	{
		tag: q.inserted,
		class: "tok-inserted"
	},
	{
		tag: q.deleted,
		class: "tok-deleted"
	},
	{
		tag: q.literal,
		class: "tok-literal"
	},
	{
		tag: q.string,
		class: "tok-string"
	},
	{
		tag: q.number,
		class: "tok-number"
	},
	{
		tag: [
			q.regexp,
			q.escape,
			q.special(q.string)
		],
		class: "tok-string2"
	},
	{
		tag: q.variableName,
		class: "tok-variableName"
	},
	{
		tag: q.local(q.variableName),
		class: "tok-variableName tok-local"
	},
	{
		tag: q.definition(q.variableName),
		class: "tok-variableName tok-definition"
	},
	{
		tag: q.special(q.variableName),
		class: "tok-variableName2"
	},
	{
		tag: q.definition(q.propertyName),
		class: "tok-propertyName tok-definition"
	},
	{
		tag: q.typeName,
		class: "tok-typeName"
	},
	{
		tag: q.namespace,
		class: "tok-namespace"
	},
	{
		tag: q.className,
		class: "tok-className"
	},
	{
		tag: q.macroName,
		class: "tok-macroName"
	},
	{
		tag: q.propertyName,
		class: "tok-propertyName"
	},
	{
		tag: q.operator,
		class: "tok-operator"
	},
	{
		tag: q.comment,
		class: "tok-comment"
	},
	{
		tag: q.meta,
		class: "tok-meta"
	},
	{
		tag: q.invalid,
		class: "tok-invalid"
	},
	{
		tag: q.punctuation,
		class: "tok-punctuation"
	}
]);
//#endregion
//#region node_modules/@codemirror/language/dist/index.js
var Bc = /*@__PURE__*/ new H();
function Vc(e) {
	return A.define({ combine: e ? (t) => t.concat(e) : void 0 });
}
var Hc = /*@__PURE__*/ new H(), Uc = class {
	constructor(e, t, n = [], r = "") {
		this.data = e, this.name = r, M.prototype.hasOwnProperty("tree") || Object.defineProperty(M.prototype, "tree", { get() {
			return J(this);
		} }), this.parser = t, this.extension = [tl.of(this), M.languageData.of((e, t, n) => {
			let r = Wc(e, t, n), i = r.type.prop(Bc);
			if (!i) return [];
			let a = e.facet(i), o = r.type.prop(Hc);
			if (o) {
				let i = r.resolve(t - r.from, n);
				for (let t of o) if (t.test(i, e)) {
					let n = e.facet(t.facet);
					return t.type == "replace" ? n : n.concat(a);
				}
			}
			return a;
		})].concat(n);
	}
	isActiveAt(e, t, n = -1) {
		return Wc(e, t, n).type.prop(Bc) == this.data;
	}
	findRegions(e) {
		let t = e.facet(tl);
		if (t?.data == this.data) return [{
			from: 0,
			to: e.doc.length
		}];
		if (!t || !t.allowsNesting) return [];
		let n = [], r = (e, t) => {
			if (e.prop(Bc) == this.data) {
				n.push({
					from: t,
					to: t + e.length
				});
				return;
			}
			let i = e.prop(H.mounted);
			if (i) {
				if (i.tree.prop(Bc) == this.data) {
					if (i.overlay) for (let e of i.overlay) n.push({
						from: e.from + t,
						to: e.to + t
					});
					else n.push({
						from: t,
						to: t + e.length
					});
					return;
				}
				if (i.overlay) {
					let e = n.length;
					if (r(i.tree, i.overlay[0].from + t), n.length > e) return;
				}
			}
			for (let n = 0; n < e.children.length; n++) {
				let i = e.children[n];
				i instanceof G && r(i, e.positions[n] + t);
			}
		};
		return r(J(e), 0), n;
	}
	get allowsNesting() {
		return !0;
	}
};
Uc.setState = /*@__PURE__*/ j.define();
function Wc(e, t, n) {
	let r = e.facet(tl), i = J(e).topNode;
	if (!r || r.allowsNesting) for (let e = i; e; e = e.enter(t, n, W.ExcludeBuffers | W.EnterBracketed)) e.type.isTop && (i = e);
	return i;
}
var Gc = class e extends Uc {
	constructor(e, t, n) {
		super(e, t, [], n), this.parser = t;
	}
	static define(t) {
		let n = Vc(t.languageData);
		return new e(n, t.parser.configure({ props: [Bc.add((e) => e.isTop ? n : void 0)] }), t.name);
	}
	configure(t, n) {
		return new e(this.data, this.parser.configure(t), n || this.name);
	}
	get allowsNesting() {
		return this.parser.hasWrappers();
	}
};
function J(e) {
	let t = e.field(Uc.state, !1);
	return t ? t.tree : G.empty;
}
function Kc(e, t, n = 50) {
	let r = e.field(Uc.state, !1)?.context;
	if (!r) return null;
	let i = r.viewport;
	r.updateViewport({
		from: 0,
		to: t
	});
	let a = r.isDone(t) || r.work(n, t) ? r.tree : null;
	return r.updateViewport(i), a;
}
var qc = class {
	constructor(e) {
		this.doc = e, this.cursorPos = 0, this.string = "", this.cursor = e.iter();
	}
	get length() {
		return this.doc.length;
	}
	syncTo(e) {
		return this.string = this.cursor.next(e - this.cursorPos).value, this.cursorPos = e + this.string.length, this.cursorPos - this.string.length;
	}
	chunk(e) {
		return this.syncTo(e), this.string;
	}
	get lineChunks() {
		return !0;
	}
	read(e, t) {
		let n = this.cursorPos - this.string.length;
		return e < n || t >= this.cursorPos ? this.doc.sliceString(e, t) : this.string.slice(e - n, t - n);
	}
}, Jc = null, Yc = class e {
	constructor(e, t, n = [], r, i, a, o, s) {
		this.parser = e, this.state = t, this.fragments = n, this.tree = r, this.treeLen = i, this.viewport = a, this.skipped = o, this.scheduleOn = s, this.parse = null, this.tempSkipped = [];
	}
	static create(t, n, r) {
		return new e(t, n, [], G.empty, 0, r, [], null);
	}
	startParse() {
		return this.parser.startParse(new qc(this.state.doc), this.fragments);
	}
	work(e, t) {
		return t != null && t >= this.state.doc.length && (t = void 0), this.tree != G.empty && this.isDone(t ?? this.state.doc.length) ? (this.takeTree(), !0) : this.withContext(() => {
			if (typeof e == "number") {
				let t = Date.now() + e;
				e = () => Date.now() > t;
			}
			for (this.parse ||= this.startParse(), t != null && (this.parse.stoppedAt == null || this.parse.stoppedAt > t) && t < this.state.doc.length && this.parse.stopAt(t);;) {
				let n = this.parse.advance();
				if (n) {
					if (this.fragments = this.withoutTempSkipped(Ys.addTree(n, this.fragments, this.parse.stoppedAt != null)), this.treeLen = this.parse.stoppedAt ?? this.state.doc.length, this.tree = n, this.parse = null, this.treeLen < (t ?? this.state.doc.length)) this.parse = this.startParse();
					else return !0;
				}
				if (e()) return !1;
			}
		});
	}
	takeTree() {
		let e, t;
		this.parse && (e = this.parse.parsedPos) >= this.treeLen && ((this.parse.stoppedAt == null || this.parse.stoppedAt > e) && this.parse.stopAt(e), this.withContext(() => {
			for (; !(t = this.parse.advance()););
		}), this.treeLen = e, this.tree = t, this.fragments = this.withoutTempSkipped(Ys.addTree(this.tree, this.fragments, !0)), this.parse = null);
	}
	withContext(e) {
		let t = Jc;
		Jc = this;
		try {
			return e();
		} finally {
			Jc = t;
		}
	}
	withoutTempSkipped(e) {
		for (let t; t = this.tempSkipped.pop();) e = Xc(e, t.from, t.to);
		return e;
	}
	changes(t, n) {
		let { fragments: r, tree: i, treeLen: a, viewport: o, skipped: s } = this;
		if (this.takeTree(), !t.empty) {
			let e = [];
			if (t.iterChangedRanges((t, n, r, i) => e.push({
				fromA: t,
				toA: n,
				fromB: r,
				toB: i
			})), r = Ys.applyChanges(r, e), i = G.empty, a = 0, o = {
				from: t.mapPos(o.from, -1),
				to: t.mapPos(o.to, 1)
			}, this.skipped.length) {
				s = [];
				for (let e of this.skipped) {
					let n = t.mapPos(e.from, 1), r = t.mapPos(e.to, -1);
					n < r && s.push({
						from: n,
						to: r
					});
				}
			}
		}
		return new e(this.parser, n, r, i, a, o, s, this.scheduleOn);
	}
	updateViewport(e) {
		if (this.viewport.from == e.from && this.viewport.to == e.to) return !1;
		this.viewport = e;
		let t = this.skipped.length;
		for (let t = 0; t < this.skipped.length; t++) {
			let { from: n, to: r } = this.skipped[t];
			n < e.to && r > e.from && (this.fragments = Xc(this.fragments, n, r), this.skipped.splice(t--, 1));
		}
		return this.skipped.length >= t ? !1 : (this.reset(), !0);
	}
	reset() {
		this.parse &&= (this.takeTree(), null);
	}
	skipUntilInView(e, t) {
		this.skipped.push({
			from: e,
			to: t
		});
	}
	static getSkippingParser(e) {
		return new class extends Xs {
			createParse(t, n, r) {
				let i = r[0].from, a = r[r.length - 1].to;
				return {
					parsedPos: i,
					advance() {
						let t = Jc;
						if (t) {
							for (let e of r) t.tempSkipped.push(e);
							e && (t.scheduleOn = t.scheduleOn ? Promise.all([t.scheduleOn, e]) : e);
						}
						return this.parsedPos = a, new G(U.none, [], [], a - i);
					},
					stoppedAt: null,
					stopAt() {}
				};
			}
		}();
	}
	isDone(e) {
		e = Math.min(e, this.state.doc.length);
		let t = this.fragments;
		return this.treeLen >= e && t.length && t[0].from == 0 && t[0].to >= e;
	}
	static get() {
		return Jc;
	}
};
function Xc(e, t, n) {
	return Ys.applyChanges(e, [{
		fromA: t,
		toA: n,
		fromB: t,
		toB: n
	}]);
}
var Zc = class e {
	constructor(e) {
		this.context = e, this.tree = e.tree;
	}
	apply(t) {
		if (!t.docChanged && this.tree == this.context.tree) return this;
		let n = this.context.changes(t.changes, t.state), r = this.context.treeLen == t.startState.doc.length ? void 0 : Math.max(t.changes.mapPos(this.context.treeLen), n.viewport.to);
		return n.work(20, r) || n.takeTree(), new e(n);
	}
	static init(t) {
		let n = Math.min(3e3, t.doc.length), r = Yc.create(t.facet(tl).parser, t, {
			from: 0,
			to: n
		});
		return r.work(20, n) || r.takeTree(), new e(r);
	}
};
Uc.state = /*@__PURE__*/ Le.define({
	create: Zc.init,
	update(e, t) {
		for (let e of t.effects) if (e.is(Uc.setState)) return e.value;
		return t.startState.facet(tl) == t.state.facet(tl) ? e.apply(t) : Zc.init(t.state);
	}
});
var Qc = (e) => {
	let t = setTimeout(() => e(), 500);
	return () => clearTimeout(t);
};
typeof requestIdleCallback < "u" && (Qc = (e) => {
	let t = -1, n = setTimeout(() => {
		t = requestIdleCallback(e, { timeout: 400 });
	}, 100);
	return () => t < 0 ? clearTimeout(n) : cancelIdleCallback(t);
});
var $c = typeof navigator < "u" && navigator.scheduling?.isInputPending ? () => navigator.scheduling.isInputPending() : null, el = /*@__PURE__*/ Br.fromClass(class {
	constructor(e) {
		this.view = e, this.working = null, this.workScheduled = 0, this.chunkEnd = -1, this.chunkBudget = -1, this.work = this.work.bind(this), this.scheduleWork();
	}
	update(e) {
		let t = this.view.state.field(Uc.state).context;
		(t.updateViewport(e.view.viewport) || this.view.viewport.to > t.treeLen) && this.scheduleWork(), (e.docChanged || e.selectionSet) && (this.view.hasFocus && (this.chunkBudget += 50), this.scheduleWork()), this.checkAsyncSchedule(t);
	}
	scheduleWork() {
		if (this.working) return;
		let { state: e } = this.view, t = e.field(Uc.state);
		(t.tree != t.context.tree || !t.context.isDone(e.doc.length)) && (this.working = Qc(this.work));
	}
	work(e) {
		this.working = null;
		let t = Date.now();
		if (this.chunkEnd < t && (this.chunkEnd < 0 || this.view.hasFocus) && (this.chunkEnd = t + 3e4, this.chunkBudget = 3e3), this.chunkBudget <= 0) return;
		let { state: n, viewport: { to: r } } = this.view, i = n.field(Uc.state);
		if (i.tree == i.context.tree && i.context.isDone(r + 1e5)) return;
		let a = Date.now() + Math.min(this.chunkBudget, 100, e && !$c ? Math.max(25, e.timeRemaining() - 5) : 1e9), o = i.context.treeLen < r && n.doc.length > r + 1e3, s = i.context.work(() => $c && $c() || Date.now() > a, r + (o ? 0 : 1e5));
		this.chunkBudget -= Date.now() - t, (s || this.chunkBudget <= 0) && (i.context.takeTree(), this.view.dispatch({ effects: Uc.setState.of(new Zc(i.context)) })), this.chunkBudget > 0 && (!s || o) && this.scheduleWork(), this.checkAsyncSchedule(i.context);
	}
	checkAsyncSchedule(e) {
		e.scheduleOn &&= (this.workScheduled++, e.scheduleOn.then(() => this.scheduleWork()).catch((e) => Ir(this.view.state, e)).then(() => this.workScheduled--), null);
	}
	destroy() {
		this.working && this.working();
	}
	isWorking() {
		return !!(this.working || this.workScheduled > 0);
	}
}, { eventHandlers: { focus() {
	this.scheduleWork();
} } }), tl = /*@__PURE__*/ A.define({
	combine(e) {
		return e.length ? e[0] : null;
	},
	enables: (e) => [
		Uc.state,
		el,
		V.contentAttributes.compute([e], (t) => {
			let n = t.facet(e);
			return n && n.name ? { "data-language": n.name } : {};
		})
	]
}), nl = class {
	constructor(e, t = []) {
		this.language = e, this.support = t, this.extension = [e, t];
	}
}, rl = class e {
	constructor(e, t, n, r, i, a = void 0) {
		this.name = e, this.alias = t, this.extensions = n, this.filename = r, this.loadFunc = i, this.support = a, this.loading = null;
	}
	load() {
		return this.loading ||= this.loadFunc().then((e) => this.support = e, (e) => {
			throw this.loading = null, e;
		});
	}
	static of(t) {
		let { load: n, support: r } = t;
		if (!n) {
			if (!r) throw RangeError("Must pass either 'load' or 'support' to LanguageDescription.of");
			n = () => Promise.resolve(r);
		}
		return new e(t.name, (t.alias || []).concat(t.name).map((e) => e.toLowerCase()), t.extensions || [], t.filename, n, r);
	}
	static matchFilename(e, t) {
		for (let n of e) if (n.filename && n.filename.test(t)) return n;
		let n = /\.([^.]+)$/.exec(t);
		if (n) {
			for (let t of e) if (t.extensions.indexOf(n[1]) > -1) return t;
		}
		return null;
	}
	static matchLanguageName(e, t, n = !0) {
		t = t.toLowerCase();
		for (let n of e) if (n.alias.some((e) => e == t)) return n;
		if (n) for (let n of e) for (let e of n.alias) {
			let r = t.indexOf(e);
			if (r > -1 && (e.length > 2 || !/\w/.test(t[r - 1]) && !/\w/.test(t[r + e.length]))) return n;
		}
		return null;
	}
}, il = /*@__PURE__*/ A.define(), al = /*@__PURE__*/ A.define({ combine: (e) => {
	if (!e.length) return "  ";
	let t = e[0];
	if (!t || /\S/.test(t) || Array.from(t).some((e) => e != t[0])) throw Error("Invalid indent unit: " + JSON.stringify(e[0]));
	return t;
} });
function ol(e) {
	let t = e.facet(al);
	return t.charCodeAt(0) == 9 ? e.tabSize * t.length : t.length;
}
function sl(e, t) {
	let n = "", r = e.tabSize, i = e.facet(al)[0];
	if (i == "	") {
		for (; t >= r;) n += "	", t -= r;
		i = " ";
	}
	for (let e = 0; e < t; e++) n += i;
	return n;
}
function cl(e, t) {
	e instanceof M && (e = new ll(e));
	for (let n of e.state.facet(il)) {
		let r = n(e, t);
		if (r !== void 0) return r;
	}
	let n = J(e.state);
	return n.length >= t ? dl(e, n, t) : null;
}
var ll = class {
	constructor(e, t = {}) {
		this.state = e, this.options = t, this.unit = ol(e);
	}
	lineAt(e, t = 1) {
		let n = this.state.doc.lineAt(e), { simulateBreak: r, simulateDoubleBreak: i } = this.options;
		return r != null && r >= n.from && r <= n.to ? i && r == e ? {
			text: "",
			from: e
		} : (t < 0 ? r < e : r <= e) ? {
			text: n.text.slice(r - n.from),
			from: r
		} : {
			text: n.text.slice(0, r - n.from),
			from: n.from
		} : n;
	}
	textAfterPos(e, t = 1) {
		if (this.options.simulateDoubleBreak && e == this.options.simulateBreak) return "";
		let { text: n, from: r } = this.lineAt(e, t);
		return n.slice(e - r, Math.min(n.length, e + 100 - r));
	}
	column(e, t = 1) {
		let { text: n, from: r } = this.lineAt(e, t), i = this.countColumn(n, e - r), a = this.options.overrideIndentation ? this.options.overrideIndentation(r) : -1;
		return a > -1 && (i += a - this.countColumn(n, n.search(/\S|$/))), i;
	}
	countColumn(e, t = e.length) {
		return Lt(e, this.state.tabSize, t);
	}
	lineIndent(e, t = 1) {
		let { text: n, from: r } = this.lineAt(e, t), i = this.options.overrideIndentation;
		if (i) {
			let e = i(r);
			if (e > -1) return e;
		}
		return this.countColumn(n, n.search(/\S|$/));
	}
	get simulatedBreak() {
		return this.options.simulateBreak || null;
	}
}, ul = /*@__PURE__*/ new H();
function dl(e, t, n) {
	let r = t.resolveStack(n), i = t.resolveInner(n, -1).resolve(n, 0).enterUnfinishedNodesBefore(n);
	if (i != r.node) {
		let e = [];
		for (let t = i; t && !(t.from < r.node.from || t.to > r.node.to || t.from == r.node.from && t.type == r.node.type); t = t.parent) e.push(t);
		for (let t = e.length - 1; t >= 0; t--) r = {
			node: e[t],
			next: r
		};
	}
	return fl(r, e, n);
}
function fl(e, t, n) {
	for (let r = e; r; r = r.next) {
		let e = ml(r.node);
		if (e) return e(gl.create(t, n, r));
	}
	return 0;
}
function pl(e) {
	return e.pos == e.options.simulateBreak && e.options.simulateDoubleBreak;
}
function ml(e) {
	let t = e.type.prop(ul);
	if (t) return t;
	let n = e.firstChild, r;
	if (n && (r = n.type.prop(H.closedBy))) {
		let t = e.lastChild, n = t && r.indexOf(t.name) > -1;
		return (e) => bl(e, !0, 1, void 0, n && !pl(e) ? t.from : void 0);
	}
	return e.parent == null ? hl : null;
}
function hl() {
	return 0;
}
var gl = class e extends ll {
	constructor(e, t, n) {
		super(e.state, e.options), this.base = e, this.pos = t, this.context = n;
	}
	get node() {
		return this.context.node;
	}
	static create(t, n, r) {
		return new e(t, n, r);
	}
	get textAfter() {
		return this.textAfterPos(this.pos);
	}
	get baseIndent() {
		return this.baseIndentFor(this.node);
	}
	baseIndentFor(e) {
		let t = this.state.doc.lineAt(e.from);
		for (;;) {
			let n = e.resolve(t.from);
			for (; n.parent && n.parent.from == n.from;) n = n.parent;
			if (_l(n, e)) break;
			t = this.state.doc.lineAt(n.from);
		}
		return this.lineIndent(t.from);
	}
	continue() {
		return fl(this.context.next, this.base, this.pos);
	}
};
function _l(e, t) {
	for (let n = t; n; n = n.parent) if (e == n) return !0;
	return !1;
}
function vl(e) {
	let t = e.node, n = t.childAfter(t.from), r = t.lastChild;
	if (!n) return null;
	let i = e.options.simulateBreak, a = e.state.doc.lineAt(n.from), o = i == null || i <= a.from ? a.to : Math.min(a.to, i);
	for (let e = n.to;;) {
		let i = t.childAfter(e);
		if (!i || i == r) return null;
		if (!i.type.isSkipped) {
			if (i.from >= o) return null;
			let e = /^ */.exec(a.text.slice(n.to - a.from))[0].length;
			return {
				from: n.from,
				to: n.to + e
			};
		}
		e = i.to;
	}
}
function yl({ closing: e, align: t = !0, units: n = 1 }) {
	return (r) => bl(r, t, n, e);
}
function bl(e, t, n, r, i) {
	let a = e.textAfter, o = a.match(/^\s*/)[0].length, s = r && a.slice(o, o + r.length) == r || i == e.pos + o, c = t ? vl(e) : null;
	return c ? s ? e.column(c.from) : e.column(c.to) : e.baseIndent + (s ? 0 : e.unit * n);
}
var xl = (e) => e.baseIndent;
function Sl({ except: e, units: t = 1 } = {}) {
	return (n) => {
		let r = e && e.test(n.textAfter);
		return n.baseIndent + (r ? 0 : t * n.unit);
	};
}
var Cl = /*@__PURE__*/ A.define(), wl = /*@__PURE__*/ new H();
function Tl(e) {
	let t = e.firstChild, n = e.lastChild;
	return t && t.to < n.from ? {
		from: t.to,
		to: n.type.isError ? e.to : n.from
	} : null;
}
var El = class e {
	constructor(e, t) {
		this.specs = e;
		let n;
		function r(e) {
			let t = Ut.newName();
			return (n ||= Object.create(null))["." + t] = e, t;
		}
		let i = typeof t.all == "string" ? t.all : t.all ? r(t.all) : void 0, a = t.scope;
		this.scope = a instanceof Uc ? (e) => e.prop(Bc) == a.data : a ? (e) => e == a : void 0, this.style = xc(e.map((e) => ({
			tag: e.tag,
			class: e.class || r(Object.assign({}, e, { tag: null }))
		})), { all: i }).style, this.module = n ? new Ut(n) : null, this.themeType = t.themeType;
	}
	static define(t, n) {
		return new e(t, n || {});
	}
}, Dl = /*@__PURE__*/ A.define(), Ol = /*@__PURE__*/ A.define({ combine(e) {
	return e.length ? [e[0]] : null;
} });
function kl(e) {
	let t = e.facet(Dl);
	return t.length ? t : e.facet(Ol);
}
function Al(e, t) {
	let n = [Ml], r;
	return e instanceof El && (e.module && n.push(V.styleModule.of(e.module)), r = e.themeType), t?.fallback ? n.push(Ol.of(e)) : r ? n.push(Dl.computeN([V.darkTheme], (t) => t.facet(V.darkTheme) == (r == "dark") ? [e] : [])) : n.push(Dl.of(e)), n;
}
var jl = class {
	constructor(e) {
		this.markCache = Object.create(null), this.tree = J(e.state), this.decorations = this.buildDeco(e, kl(e.state)), this.decoratedTo = e.viewport.to;
	}
	update(e) {
		let t = J(e.state), n = kl(e.state), r = n != kl(e.startState), { viewport: i } = e.view, a = e.changes.mapPos(this.decoratedTo, 1);
		t.length < i.to && !r && t.type == this.tree.type && a >= i.to ? (this.decorations = this.decorations.map(e.changes), this.decoratedTo = a) : (t != this.tree || e.viewportChanged || r) && (this.tree = t, this.decorations = this.buildDeco(e.view, n), this.decoratedTo = i.to);
	}
	buildDeco(e, t) {
		if (!t || !this.tree.length) return F.none;
		let n = new Et();
		for (let { from: r, to: i } of e.visibleRanges) Cc(this.tree, t, (e, t, r) => {
			n.add(e, t, this.markCache[r] || (this.markCache[r] = F.mark({ class: r })));
		}, r, i);
		return n.finish();
	}
}, Ml = /*@__PURE__*/ Be.high(/*@__PURE__*/ Br.fromClass(jl, { decorations: (e) => e.decorations })), Nl = /*@__PURE__*/ El.define([
	{
		tag: q.meta,
		color: "#404740"
	},
	{
		tag: q.link,
		textDecoration: "underline"
	},
	{
		tag: q.heading,
		textDecoration: "underline",
		fontWeight: "bold"
	},
	{
		tag: q.emphasis,
		fontStyle: "italic"
	},
	{
		tag: q.strong,
		fontWeight: "bold"
	},
	{
		tag: q.strikethrough,
		textDecoration: "line-through"
	},
	{
		tag: q.keyword,
		color: "#708"
	},
	{
		tag: [
			q.atom,
			q.bool,
			q.url,
			q.contentSeparator,
			q.labelName
		],
		color: "#219"
	},
	{
		tag: [q.literal, q.inserted],
		color: "#164"
	},
	{
		tag: [q.string, q.deleted],
		color: "#a11"
	},
	{
		tag: [
			q.regexp,
			q.escape,
			/*@__PURE__*/ q.special(q.string)
		],
		color: "#e40"
	},
	{
		tag: /*@__PURE__*/ q.definition(q.variableName),
		color: "#00f"
	},
	{
		tag: /*@__PURE__*/ q.local(q.variableName),
		color: "#30a"
	},
	{
		tag: [q.typeName, q.namespace],
		color: "#085"
	},
	{
		tag: q.className,
		color: "#167"
	},
	{
		tag: [/*@__PURE__*/ q.special(q.variableName), q.macroName],
		color: "#256"
	},
	{
		tag: /*@__PURE__*/ q.definition(q.propertyName),
		color: "#00c"
	},
	{
		tag: q.comment,
		color: "#940"
	},
	{
		tag: q.invalid,
		color: "#f00"
	}
]), Pl = 1e4, Fl = "()[]{}", Il = /*@__PURE__*/ new H();
function Ll(e, t, n) {
	let r = e.prop(t < 0 ? H.openedBy : H.closedBy);
	if (r) return r;
	if (e.name.length == 1) {
		let r = n.indexOf(e.name);
		if (r > -1 && r % 2 == +(t < 0)) return [n[r + t]];
	}
	return null;
}
function Rl(e) {
	let t = e.type.prop(Il);
	return t ? t(e.node) : e;
}
function zl(e, t, n, r = {}) {
	let i = r.maxScanDistance || Pl, a = r.brackets || Fl, o = J(e), s = o.resolveInner(t, n);
	for (let r = s; r; r = r.parent) {
		let i = Ll(r.type, n, a);
		if (i && r.from < r.to) {
			let o = Rl(r);
			if (o && (n > 0 ? t >= o.from && t < o.to : t > o.from && t <= o.to)) return Bl(e, t, n, r, o, i, a);
		}
	}
	return Vl(e, t, n, o, s.type, i, a);
}
function Bl(e, t, n, r, i, a, o) {
	let s = r.parent, c = {
		from: i.from,
		to: i.to
	}, l = 0, u = s?.cursor();
	if (u && (n < 0 ? u.childBefore(r.from) : u.childAfter(r.to))) do
		if (n < 0 ? u.to <= r.from : u.from >= r.to) {
			if (l == 0 && a.indexOf(u.type.name) > -1 && u.from < u.to) {
				let e = Rl(u);
				return {
					start: c,
					end: e ? {
						from: e.from,
						to: e.to
					} : void 0,
					matched: !0
				};
			}
			if (Ll(u.type, n, o)) l++;
			else if (Ll(u.type, -n, o)) {
				if (l == 0) {
					let e = Rl(u);
					return {
						start: c,
						end: e && e.from < e.to ? {
							from: e.from,
							to: e.to
						} : void 0,
						matched: !1
					};
				}
				l--;
			}
		}
	while (n < 0 ? u.prevSibling() : u.nextSibling());
	return {
		start: c,
		matched: !1
	};
}
function Vl(e, t, n, r, i, a, o) {
	if (n < 0 ? !t : t == e.doc.length) return null;
	let s = n < 0 ? e.sliceDoc(t - 1, t) : e.sliceDoc(t, t + 1), c = o.indexOf(s);
	if (c < 0 || c % 2 == 0 != n > 0) return null;
	let l = {
		from: n < 0 ? t - 1 : t,
		to: n > 0 ? t + 1 : t
	}, u = e.doc.iterRange(t, n > 0 ? e.doc.length : 0), d = 0;
	for (let e = 0; !u.next().done && e <= a;) {
		let a = u.value;
		n < 0 && (e += a.length);
		let s = t + e * n;
		for (let e = n > 0 ? 0 : a.length - 1, t = n > 0 ? a.length : -1; e != t; e += n) {
			let t = o.indexOf(a[e]);
			if (!(t < 0 || r.resolveInner(s + e, 1).type != i)) {
				if (t % 2 == 0 == n > 0) d++;
				else if (d == 1) return {
					start: l,
					end: {
						from: s + e,
						to: s + e + 1
					},
					matched: t >> 1 == c >> 1
				};
				else d--;
			}
		}
		n > 0 && (e += a.length);
	}
	return u.done ? {
		start: l,
		matched: !1
	} : null;
}
var Hl = /*@__PURE__*/ Object.create(null), Ul = [U.none], Wl = [], Gl = /*@__PURE__*/ Object.create(null), Kl = /*@__PURE__*/ Object.create(null);
for (let [e, t] of [
	["variable", "variableName"],
	["variable-2", "variableName.special"],
	["string-2", "string.special"],
	["def", "variableName.definition"],
	["tag", "tagName"],
	["attribute", "attributeName"],
	["type", "typeName"],
	["builtin", "variableName.standard"],
	["qualifier", "modifier"],
	["error", "invalid"],
	["header", "heading"],
	["property", "propertyName"]
]) Kl[e] = /*@__PURE__*/ Jl(Hl, t);
function ql(e, t) {
	Wl.indexOf(e) > -1 || (Wl.push(e), console.warn(t));
}
function Jl(e, t) {
	let n = [];
	for (let r of t.split(" ")) {
		let t = [];
		for (let n of r.split(".")) {
			let r = e[n] || q[n];
			r ? typeof r == "function" ? t.length ? t = t.map(r) : ql(n, `Modifier ${n} used at start of tag`) : t.length ? ql(n, `Tag ${n} used as modifier`) : t = Array.isArray(r) ? r : [r] : ql(n, `Unknown highlighting tag ${n}`);
		}
		for (let e of t) n.push(e);
	}
	if (!n.length) return 0;
	let r = t.replace(/ /g, "_"), i = r + " " + n.map((e) => e.id), a = Gl[i];
	if (a) return a.id;
	let o = Gl[i] = U.define({
		id: Ul.length,
		name: r,
		props: [vc({ [r]: n })]
	});
	return Ul.push(o), o.id;
}
I.RTL, I.LTR;
//#endregion
//#region node_modules/@codemirror/commands/dist/index.js
var Yl = (e) => {
	let { state: t } = e, n = t.doc.lineAt(t.selection.main.from), r = eu(e.state, n.from);
	return r.line ? Zl(e) : r.block ? $l(e) : !1;
};
function Xl(e, t) {
	return ({ state: n, dispatch: r }) => {
		if (n.readOnly) return !1;
		let i = e(t, n);
		return i ? (r(n.update(i)), !0) : !1;
	};
}
var Zl = /*@__PURE__*/ Xl(au, 0), Ql = /*@__PURE__*/ Xl(iu, 0), $l = /*@__PURE__*/ Xl((e, t) => iu(e, t, ru(t)), 0);
function eu(e, t) {
	let n = e.languageDataAt("commentTokens", t, 1);
	return n.length ? n[0] : {};
}
var tu = 50;
function nu(e, { open: t, close: n }, r, i) {
	let a = e.sliceDoc(r - tu, r), o = e.sliceDoc(i, i + tu), s = /\s*$/.exec(a)[0].length, c = /^\s*/.exec(o)[0].length, l = a.length - s;
	if (a.slice(l - t.length, l) == t && o.slice(c, c + n.length) == n) return {
		open: {
			pos: r - s,
			margin: s && 1
		},
		close: {
			pos: i + c,
			margin: c && 1
		}
	};
	let u, d;
	i - r <= 100 ? u = d = e.sliceDoc(r, i) : (u = e.sliceDoc(r, r + tu), d = e.sliceDoc(i - tu, i));
	let f = /^\s*/.exec(u)[0].length, p = /\s*$/.exec(d)[0].length, m = d.length - p - n.length;
	return u.slice(f, f + t.length) == t && d.slice(m, m + n.length) == n ? {
		open: {
			pos: r + f + t.length,
			margin: +!!/\s/.test(u.charAt(f + t.length))
		},
		close: {
			pos: i - p - n.length,
			margin: +!!/\s/.test(d.charAt(m - 1))
		}
	} : null;
}
function ru(e) {
	let t = [];
	for (let n of e.selection.ranges) {
		let r = e.doc.lineAt(n.from), i = n.to <= r.to ? r : e.doc.lineAt(n.to);
		i.from > r.from && i.from == n.to && (i = n.to == r.to + 1 ? r : e.doc.lineAt(n.to - 1));
		let a = t.length - 1;
		a >= 0 && t[a].to > r.from ? t[a].to = i.to : t.push({
			from: r.from + /^\s*/.exec(r.text)[0].length,
			to: i.to
		});
	}
	return t;
}
function iu(e, t, n = t.selection.ranges) {
	let r = n.map((e) => eu(t, e.from).block);
	if (!r.every((e) => e)) return null;
	let i = n.map((e, n) => nu(t, r[n], e.from, e.to));
	if (e != 2 && !i.every((e) => e)) return { changes: t.changes(n.map((e, t) => i[t] ? [] : [{
		from: e.from,
		insert: r[t].open + " "
	}, {
		from: e.to,
		insert: " " + r[t].close
	}])) };
	if (e != 1 && i.some((e) => e)) {
		let e = [];
		for (let t = 0, n; t < i.length; t++) if (n = i[t]) {
			let i = r[t], { open: a, close: o } = n;
			e.push({
				from: a.pos - i.open.length,
				to: a.pos + a.margin
			}, {
				from: o.pos - o.margin,
				to: o.pos + i.close.length
			});
		}
		return { changes: e };
	}
	return null;
}
function au(e, t, n = t.selection.ranges) {
	let r = [], i = -1;
	ranges: for (let { from: e, to: a } of n) {
		let n = r.length, o = 1e9, s;
		for (let n = e; n <= a;) {
			let c = t.doc.lineAt(n);
			if (s == null && (s = eu(t, c.from).line, !s)) continue ranges;
			if (c.from > i && (e == a || a > c.from)) {
				i = c.from;
				let e = /^\s*/.exec(c.text)[0].length, t = e == c.length, n = c.text.slice(e, e + s.length) == s ? e : -1;
				e < c.text.length && e < o && (o = e), r.push({
					line: c,
					comment: n,
					token: s,
					indent: e,
					empty: t,
					single: !1
				});
			}
			n = c.to + 1;
		}
		if (o < 1e9) for (let e = n; e < r.length; e++) r[e].indent < r[e].line.text.length && (r[e].indent = o);
		r.length == n + 1 && (r[n].single = !0);
	}
	if (e != 2 && r.some((e) => e.comment < 0 && (!e.empty || e.single))) {
		let e = [];
		for (let { line: t, token: n, indent: i, empty: a, single: o } of r) (o || !a) && e.push({
			from: t.from + i,
			insert: n + " "
		});
		let n = t.changes(e);
		return {
			changes: n,
			selection: t.selection.map(n, 1)
		};
	}
	if (e != 1 && r.some((e) => e.comment >= 0)) {
		let e = [];
		for (let { line: t, comment: n, token: i } of r) if (n >= 0) {
			let r = t.from + n, a = r + i.length;
			t.text[a - t.from] == " " && a++, e.push({
				from: r,
				to: a
			});
		}
		return { changes: e };
	}
	return null;
}
var ou = /*@__PURE__*/ tt.define(), su = /*@__PURE__*/ tt.define(), cu = /*@__PURE__*/ A.define(), lu = /*@__PURE__*/ A.define({ combine(e) {
	return vt(e, {
		minDepth: 100,
		newGroupDelay: 500,
		joinToEvent: (e, t) => t
	}, {
		minDepth: Math.max,
		newGroupDelay: Math.min,
		joinToEvent: (e, t) => (n, r) => e(n, r) || t(n, r)
	});
} }), uu = /*@__PURE__*/ Le.define({
	create() {
		return ku.empty;
	},
	update(e, t) {
		let n = t.state.facet(lu), r = t.annotation(ou);
		if (r) {
			let i = _u.fromTransaction(t, r.selection), a = r.side, o = a == 0 ? e.undone : e.done;
			return o = i ? vu(o, o.length, n.minDepth, i) : wu(o, t.startState.selection), new ku(a == 0 ? r.rest : o, a == 0 ? o : r.rest);
		}
		let i = t.annotation(su);
		if ((i == "full" || i == "before") && (e = e.isolate()), t.annotation(it.addToHistory) === !1) return t.changes.empty ? e : e.addMapping(t.changes.desc);
		let a = _u.fromTransaction(t), o = t.annotation(it.time), s = t.annotation(it.userEvent);
		return a ? e = e.addChanges(a, o, s, n, t) : t.selection && (e = e.addSelection(t.startState.selection, o, s, n.newGroupDelay)), (i == "full" || i == "after") && (e = e.isolate()), e;
	},
	toJSON(e) {
		return {
			done: e.done.map((e) => e.toJSON()),
			undone: e.undone.map((e) => e.toJSON())
		};
	},
	fromJSON(e) {
		return new ku(e.done.map(_u.fromJSON), e.undone.map(_u.fromJSON));
	}
});
function du(e = {}) {
	return [
		uu,
		lu.of(e),
		V.domEventHandlers({ beforeinput(e, t) {
			let n = e.inputType == "historyUndo" ? pu : e.inputType == "historyRedo" ? mu : null;
			return n ? (e.preventDefault(), n(t)) : !1;
		} })
	];
}
function fu(e, t) {
	return function({ state: n, dispatch: r }) {
		if (!t && n.readOnly) return !1;
		let i = n.field(uu, !1);
		if (!i) return !1;
		let a = i.pop(e, n, t);
		return a ? (r(a), !0) : !1;
	};
}
var pu = /*@__PURE__*/ fu(0, !1), mu = /*@__PURE__*/ fu(1, !1), hu = /*@__PURE__*/ fu(0, !0), gu = /*@__PURE__*/ fu(1, !0), _u = class e {
	constructor(e, t, n, r, i) {
		this.changes = e, this.effects = t, this.mapped = n, this.startSelection = r, this.selectionsAfter = i;
	}
	setSelAfter(t) {
		return new e(this.changes, this.effects, this.mapped, this.startSelection, t);
	}
	toJSON() {
		return {
			changes: this.changes?.toJSON(),
			mapped: this.mapped?.toJSON(),
			startSelection: this.startSelection?.toJSON(),
			selectionsAfter: this.selectionsAfter.map((e) => e.toJSON())
		};
	}
	static fromJSON(t) {
		return new e(t.changes && Se.fromJSON(t.changes), [], t.mapped && xe.fromJSON(t.mapped), t.startSelection && k.fromJSON(t.startSelection), t.selectionsAfter.map(k.fromJSON));
	}
	static fromTransaction(t, n) {
		let r = Su;
		for (let e of t.startState.facet(cu)) {
			let n = e(t);
			n.length && (r = r.concat(n));
		}
		return !r.length && t.changes.empty ? null : new e(t.changes.invert(t.startState.doc), r, void 0, n || t.startState.selection, Su);
	}
	static selection(t) {
		return new e(void 0, Su, void 0, void 0, t);
	}
};
function vu(e, t, n, r) {
	let i = t + 1 > n + 20 ? t - n - 1 : 0, a = e.slice(i, t);
	return a.push(r), a;
}
function yu(e, t) {
	let n = [], r = !1;
	return e.iterChangedRanges((e, t) => n.push(e, t)), t.iterChangedRanges((e, t, i, a) => {
		for (let e = 0; e < n.length;) {
			let t = n[e++], o = n[e++];
			a >= t && i <= o && (r = !0);
		}
	}), r;
}
function bu(e, t) {
	return e.ranges.length == t.ranges.length && e.ranges.filter((e, n) => e.empty != t.ranges[n].empty).length === 0;
}
function xu(e, t) {
	return e.length ? t.length ? e.concat(t) : e : t;
}
var Su = [], Cu = 200;
function wu(e, t) {
	if (e.length) {
		let n = e[e.length - 1], r = n.selectionsAfter.slice(Math.max(0, n.selectionsAfter.length - Cu));
		return r.length && r[r.length - 1].eq(t) ? e : (r.push(t), vu(e, e.length - 1, 1e9, n.setSelAfter(r)));
	}
	return [_u.selection([t])];
}
function Tu(e) {
	let t = e[e.length - 1], n = e.slice();
	return n[e.length - 1] = t.setSelAfter(t.selectionsAfter.slice(0, t.selectionsAfter.length - 1)), n;
}
function Eu(e, t) {
	if (!e.length) return e;
	let n = e.length, r = Su;
	for (; n;) {
		let i = Du(e[n - 1], t, r);
		if (i.changes && !i.changes.empty || i.effects.length) {
			let t = e.slice(0, n);
			return t[n - 1] = i, t;
		}
		t = i.mapped, n--, r = i.selectionsAfter;
	}
	return r.length ? [_u.selection(r)] : Su;
}
function Du(e, t, n) {
	let r = xu(e.selectionsAfter.length ? e.selectionsAfter.map((e) => e.map(t)) : Su, n);
	if (!e.changes) return _u.selection(r);
	let i = e.changes.map(t), a = t.mapDesc(e.changes, !0), o = e.mapped ? e.mapped.composeDesc(a) : a;
	return new _u(i, j.mapEffects(e.effects, t), o, e.startSelection.map(a), r);
}
var Ou = /^(input\.type|delete)($|\.)/, ku = class e {
	constructor(e, t, n = 0, r = void 0) {
		this.done = e, this.undone = t, this.prevTime = n, this.prevUserEvent = r;
	}
	isolate() {
		return this.prevTime ? new e(this.done, this.undone) : this;
	}
	addChanges(t, n, r, i, a) {
		let o = this.done, s = o[o.length - 1];
		return o = s && s.changes && !s.changes.empty && t.changes && (!r || Ou.test(r)) && (!s.selectionsAfter.length && n - this.prevTime < i.newGroupDelay && i.joinToEvent(a, yu(s.changes, t.changes)) || r == "input.type.compose") ? vu(o, o.length - 1, i.minDepth, new _u(t.changes.compose(s.changes), xu(j.mapEffects(t.effects, s.changes), s.effects), s.mapped, s.startSelection, Su)) : vu(o, o.length, i.minDepth, t), new e(o, Su, n, r);
	}
	addSelection(t, n, r, i) {
		let a = this.done.length ? this.done[this.done.length - 1].selectionsAfter : Su;
		return a.length > 0 && n - this.prevTime < i && r == this.prevUserEvent && r && /^select($|\.)/.test(r) && bu(a[a.length - 1], t) ? this : new e(wu(this.done, t), this.undone, n, r);
	}
	addMapping(t) {
		return new e(Eu(this.done, t), Eu(this.undone, t), this.prevTime, this.prevUserEvent);
	}
	pop(e, t, n) {
		let r = e == 0 ? this.done : this.undone;
		if (r.length == 0) return null;
		let i = r[r.length - 1], a = i.selectionsAfter[0] || (i.startSelection ? i.startSelection.map(i.changes.invertedDesc, 1) : t.selection);
		if (n && i.selectionsAfter.length) return t.update({
			selection: i.selectionsAfter[i.selectionsAfter.length - 1],
			annotations: ou.of({
				side: e,
				rest: Tu(r),
				selection: a
			}),
			userEvent: e == 0 ? "select.undo" : "select.redo",
			scrollIntoView: !0
		});
		if (i.changes) {
			let n = r.length == 1 ? Su : r.slice(0, r.length - 1);
			return i.mapped && (n = Eu(n, i.mapped)), t.update({
				changes: i.changes,
				selection: i.startSelection,
				effects: i.effects,
				annotations: ou.of({
					side: e,
					rest: n,
					selection: a
				}),
				filter: !1,
				userEvent: e == 0 ? "undo" : "redo",
				scrollIntoView: !0
			});
		}
		return null;
	}
};
ku.empty = /*@__PURE__*/ new ku(Su, Su);
var Au = [
	{
		key: "Mod-z",
		run: pu,
		preventDefault: !0
	},
	{
		key: "Mod-y",
		mac: "Mod-Shift-z",
		run: mu,
		preventDefault: !0
	},
	{
		linux: "Ctrl-Shift-z",
		run: mu,
		preventDefault: !0
	},
	{
		key: "Mod-u",
		run: hu,
		preventDefault: !0
	},
	{
		key: "Alt-u",
		mac: "Mod-Shift-u",
		run: gu,
		preventDefault: !0
	}
];
function ju(e, t) {
	return k.create(e.ranges.map(t), e.mainIndex);
}
function Mu(e, t) {
	return e.update({
		selection: t,
		scrollIntoView: !0,
		userEvent: "select"
	});
}
function Nu({ state: e, dispatch: t }, n) {
	let r = ju(e.selection, n);
	return !r.eq(e.selection, !0) && (t(Mu(e, r)), !0);
}
function Pu(e, t) {
	return k.cursor(t ? e.to : e.from);
}
function Fu(e, t) {
	return Nu(e, (n) => n.empty ? e.moveByChar(n, t) : Pu(n, t));
}
function Y(e) {
	return e.textDirectionAt(e.state.selection.main.head) == I.LTR;
}
var Iu = (e) => Fu(e, !Y(e)), Lu = (e) => Fu(e, Y(e));
function Ru(e, t) {
	return Nu(e, (n) => n.empty ? e.moveByGroup(n, t) : Pu(n, t));
}
var zu = (e) => Ru(e, !Y(e)), Bu = (e) => Ru(e, Y(e));
typeof Intl < "u" && Intl.Segmenter;
function Vu(e, t, n) {
	if (t.type.prop(n)) return !0;
	let r = t.to - t.from;
	return r && (r > 2 || /[^\s,.;:]/.test(e.sliceDoc(t.from, t.to))) || t.firstChild;
}
function Hu(e, t, n) {
	let r = J(e).resolveInner(t.head), i = n ? H.closedBy : H.openedBy;
	for (let a = t.head;;) {
		let t = n ? r.childAfter(a) : r.childBefore(a);
		if (!t) break;
		Vu(e, t, i) ? r = t : a = n ? t.to : t.from;
	}
	let a = r.type.prop(i), o, s;
	return s = a && (o = n ? zl(e, r.from, 1) : zl(e, r.to, -1)) && o.matched ? n ? o.end.to : o.end.from : n ? r.to : r.from, k.cursor(s, n ? -1 : 1);
}
var Uu = (e) => Nu(e, (t) => Hu(e.state, t, !Y(e))), Wu = (e) => Nu(e, (t) => Hu(e.state, t, Y(e)));
function Gu(e, t) {
	return Nu(e, (n) => {
		if (!n.empty) return Pu(n, t);
		let r = e.moveVertically(n, t);
		return r.head == n.head ? e.moveToLineBoundary(n, t) : r;
	});
}
var Ku = (e) => Gu(e, !1), qu = (e) => Gu(e, !0);
function Ju(e) {
	let t = e.scrollDOM.clientHeight < e.scrollDOM.scrollHeight - 2, n = 0, r = 0, i;
	if (t) {
		for (let t of e.state.facet(V.scrollMargins)) {
			let i = t(e);
			i?.top && (n = Math.max(i?.top, n)), i?.bottom && (r = Math.max(i?.bottom, r));
		}
		i = e.scrollDOM.clientHeight - n - r;
	} else i = (e.dom.ownerDocument.defaultView || window).innerHeight;
	return {
		marginTop: n,
		marginBottom: r,
		selfScroll: t,
		height: Math.max(e.defaultLineHeight, i - 5)
	};
}
function Yu(e, t) {
	let n = Ju(e), { state: r } = e, i = ju(r.selection, (r) => r.empty ? e.moveVertically(r, t, n.height) : Pu(r, t));
	if (i.eq(r.selection)) return !1;
	let a;
	if (n.selfScroll) {
		let t = e.coordsAtPos(r.selection.main.head), o = e.scrollDOM.getBoundingClientRect(), s = o.top + n.marginTop, c = o.bottom - n.marginBottom;
		t && t.top > s && t.bottom < c && (a = V.scrollIntoView(i.main.head, {
			y: "start",
			yMargin: t.top - s
		}));
	}
	return e.dispatch(Mu(r, i), { effects: a }), !0;
}
var Xu = (e) => Yu(e, !1), Zu = (e) => Yu(e, !0);
function Qu(e, t, n) {
	let r = e.lineBlockAt(t.head), i = e.moveToLineBoundary(t, n);
	if (i.head == t.head && i.head != (n ? r.to : r.from) && (i = e.moveToLineBoundary(t, n, !1)), !n && i.head == r.from && r.length) {
		let n = /^\s*/.exec(e.state.sliceDoc(r.from, Math.min(r.from + 100, r.to)))[0].length;
		n && t.head != r.from + n && (i = k.cursor(r.from + n));
	}
	return i;
}
var $u = (e) => Nu(e, (t) => Qu(e, t, !0)), ed = (e) => Nu(e, (t) => Qu(e, t, !1)), td = (e) => Nu(e, (t) => Qu(e, t, !Y(e))), nd = (e) => Nu(e, (t) => Qu(e, t, Y(e))), rd = (e) => Nu(e, (t) => k.cursor(e.lineBlockAt(t.head).from, 1)), id = (e) => Nu(e, (t) => k.cursor(e.lineBlockAt(t.head).to, -1));
function ad(e, t, n) {
	let r = !1, i = ju(e.selection, (t) => {
		let i = zl(e, t.head, -1) || zl(e, t.head, 1) || t.head > 0 && zl(e, t.head - 1, 1) || t.head < e.doc.length && zl(e, t.head + 1, -1);
		if (!i || !i.end) return t;
		r = !0;
		let a = i.start.from == t.head ? i.end.to : i.end.from;
		return n ? k.range(t.anchor, a) : k.cursor(a);
	});
	return r ? (t(Mu(e, i)), !0) : !1;
}
var od = ({ state: e, dispatch: t }) => ad(e, t, !1);
function sd(e, t, n) {
	let r = ju(e.state.selection, (e) => {
		e.undirectional && e.head >= e.anchor != t && (e = k.range(e.head, e.anchor));
		let r = n(e);
		return k.range(e.anchor, r.head, r.goalColumn, r.bidiLevel || void 0, r.assoc);
	});
	return !r.eq(e.state.selection) && (e.dispatch(Mu(e.state, r)), !0);
}
function cd(e, t) {
	return sd(e, t, (n) => e.moveByChar(n, t));
}
var ld = (e) => cd(e, !Y(e)), ud = (e) => cd(e, Y(e));
function dd(e, t) {
	return sd(e, t, (n) => e.moveByGroup(n, t));
}
var fd = (e) => dd(e, !Y(e)), pd = (e) => dd(e, Y(e)), md = (e) => {
	let t = !Y(e);
	return sd(e, t, (n) => Hu(e.state, n, t));
}, hd = (e) => {
	let t = Y(e);
	return sd(e, t, (n) => Hu(e.state, n, t));
};
function gd(e, t) {
	return sd(e, t, (n) => e.moveVertically(n, t));
}
var _d = (e) => gd(e, !1), vd = (e) => gd(e, !0);
function yd(e, t) {
	return sd(e, t, (n) => e.moveVertically(n, t, Ju(e).height));
}
var bd = (e) => yd(e, !1), xd = (e) => yd(e, !0), Sd = (e) => sd(e, !0, (t) => Qu(e, t, !0)), Cd = (e) => sd(e, !1, (t) => Qu(e, t, !1)), wd = (e) => {
	let t = !Y(e);
	return sd(e, t, (n) => Qu(e, n, t));
}, Td = (e) => {
	let t = Y(e);
	return sd(e, t, (n) => Qu(e, n, t));
}, Ed = (e) => sd(e, !1, (t) => k.cursor(e.lineBlockAt(t.head).from)), Dd = (e) => sd(e, !0, (t) => k.cursor(e.lineBlockAt(t.head).to)), Od = ({ state: e, dispatch: t }) => (t(Mu(e, { anchor: 0 })), !0), kd = ({ state: e, dispatch: t }) => (t(Mu(e, { anchor: e.doc.length })), !0), Ad = ({ state: e, dispatch: t }) => (t(Mu(e, {
	anchor: e.selection.main.anchor,
	head: 0
})), !0), jd = ({ state: e, dispatch: t }) => (t(Mu(e, {
	anchor: e.selection.main.anchor,
	head: e.doc.length
})), !0), Md = ({ state: e, dispatch: t }) => (t(e.update({
	selection: {
		anchor: 0,
		head: e.doc.length
	},
	userEvent: "select"
})), !0), Nd = ({ state: e, dispatch: t }) => {
	let n = Qd(e).map(({ from: t, to: n }) => k.undirectionalRange(t, Math.min(n + 1, e.doc.length)));
	return t(e.update({
		selection: k.create(n),
		userEvent: "select"
	})), !0;
}, Pd = ({ state: e, dispatch: t }) => {
	let n = ju(e.selection, (t) => {
		let n = J(e), r = n.resolveStack(t.from, 1);
		if (t.empty) {
			let e = n.resolveStack(t.from, -1);
			e.node.from >= r.node.from && e.node.to <= r.node.to && (r = e);
		}
		for (let e = r; e; e = e.next) {
			let { node: n } = e;
			if ((n.from < t.from && n.to >= t.to || n.to > t.to && n.from <= t.from) && e.next) return k.undirectionalRange(n.from, n.to);
		}
		return t;
	});
	return !n.eq(e.selection) && (t(Mu(e, n)), !0);
};
function Fd(e, t) {
	let { state: n } = e, r = n.selection, i = n.selection.ranges.slice();
	for (let r of n.selection.ranges) {
		let a = n.doc.lineAt(r.head);
		if (t ? a.to < e.state.doc.length : a.from > 0) for (let n = r;;) {
			let r = e.moveVertically(n, t);
			if (r.head < a.from || r.head > a.to) {
				i.some((e) => e.head == r.head) || i.push(r);
				break;
			}
			if (r.head == n.head) break;
			n = r;
		}
	}
	return i.length != r.ranges.length && (e.dispatch(Mu(n, k.create(i, i.length - 1))), !0);
}
var Id = (e) => Fd(e, !1), Ld = (e) => Fd(e, !0), Rd = ({ state: e, dispatch: t }) => {
	let n = e.selection, r = null;
	return n.ranges.length > 1 ? r = k.create([n.main]) : n.main.empty || (r = k.create([k.cursor(n.main.head)])), r ? (t(Mu(e, r)), !0) : !1;
};
function zd(e, t) {
	if (e.state.readOnly) return !1;
	let n = "delete.selection", { state: r } = e, i = r.changeByRange((r) => {
		let { from: i, to: a } = r;
		if (i == a) {
			let o = t(r);
			o < i ? (n = "delete.backward", o = Bd(e, o, !1)) : o > i && (n = "delete.forward", o = Bd(e, o, !0)), i = Math.min(i, o), a = Math.max(a, o);
		} else i = Bd(e, i, !1), a = Bd(e, a, !0);
		return i == a ? { range: r } : {
			changes: {
				from: i,
				to: a
			},
			range: k.cursor(i, i < r.head ? -1 : 1)
		};
	});
	return !i.changes.empty && (e.dispatch(r.update(i, {
		scrollIntoView: !0,
		userEvent: n,
		effects: n == "delete.selection" ? V.announce.of(r.phrase("Selection deleted")) : void 0
	})), !0);
}
function Bd(e, t, n) {
	if (e instanceof V) for (let r of e.state.facet(V.atomicRanges).map((t) => t(e))) r.between(t, t, (e, r) => {
		e < t && r > t && (t = n ? r : e);
	});
	return t;
}
var Vd = (e, t, n) => zd(e, (r) => {
	let i = r.from, { state: a } = e, o = a.doc.lineAt(i), s, c;
	if (n && !t && i > o.from && i < o.from + 200 && !/[^ \t]/.test(s = o.text.slice(0, i - o.from))) {
		if (s[s.length - 1] == "	") return i - 1;
		let e = Lt(s, a.tabSize) % ol(a) || ol(a);
		for (let t = 0; t < e && s[s.length - 1 - t] == " "; t++) i--;
		c = i;
	} else c = D(o.text, i - o.from, t, t) + o.from, c == i && o.number != (t ? a.doc.lines : 1) ? c += t ? 1 : -1 : !t && /[\ufe00-\ufe0f]/.test(o.text.slice(c - o.from, i - o.from)) && (c = D(o.text, c - o.from, !1, !1) + o.from);
	return c;
}), Hd = (e) => Vd(e, !1, !0), Ud = (e) => Vd(e, !0, !1), Wd = (e, t) => zd(e, (n) => {
	let r = n.head, { state: i } = e, a = i.doc.lineAt(r), o = i.charCategorizer(r);
	for (let e = null;;) {
		if (r == (t ? a.to : a.from)) {
			r == n.head && a.number != (t ? i.doc.lines : 1) && (r += t ? 1 : -1);
			break;
		}
		let s = D(a.text, r - a.from, t) + a.from, c = a.text.slice(Math.min(r, s) - a.from, Math.max(r, s) - a.from), l = o(c);
		if (e != null && l != e) break;
		(c != " " || r != n.head) && (e = l), r = s;
	}
	return r;
}), Gd = (e) => Wd(e, !1), Kd = (e) => Wd(e, !0), qd = (e) => zd(e, (t) => {
	let n = e.lineBlockAt(t.head).to;
	return t.head < n ? n : Math.min(e.state.doc.length, t.head + 1);
}), Jd = (e) => zd(e, (t) => {
	let n = e.moveToLineBoundary(t, !1).head;
	return t.head > n ? n : Math.max(0, t.head - 1);
}), Yd = (e) => zd(e, (t) => {
	let n = e.moveToLineBoundary(t, !0).head;
	return t.head < n ? n : Math.min(e.state.doc.length, t.head + 1);
}), Xd = ({ state: e, dispatch: t }) => {
	if (e.readOnly) return !1;
	let n = e.changeByRange((e) => ({
		changes: {
			from: e.from,
			to: e.to,
			insert: T.of(["", ""])
		},
		range: k.cursor(e.from)
	}));
	return t(e.update(n, {
		scrollIntoView: !0,
		userEvent: "input"
	})), !0;
}, Zd = ({ state: e, dispatch: t }) => {
	if (e.readOnly) return !1;
	let n = e.changeByRange((t) => {
		if (!t.empty || t.from == 0 || t.from == e.doc.length) return { range: t };
		let n = t.from, r = e.doc.lineAt(n), i = n == r.from ? n - 1 : D(r.text, n - r.from, !1) + r.from, a = n == r.to ? n + 1 : D(r.text, n - r.from, !0) + r.from;
		return {
			changes: {
				from: i,
				to: a,
				insert: e.doc.slice(n, a).append(e.doc.slice(i, n))
			},
			range: k.cursor(a)
		};
	});
	return !n.changes.empty && (t(e.update(n, {
		scrollIntoView: !0,
		userEvent: "move.character"
	})), !0);
};
function Qd(e) {
	let t = [], n = -1;
	for (let r of e.selection.ranges) {
		let i = e.doc.lineAt(r.from), a = e.doc.lineAt(r.to);
		if (!r.empty && r.to == a.from && (a = e.doc.lineAt(r.to - 1)), n >= i.number) {
			let e = t[t.length - 1];
			e.to = a.to, e.ranges.push(r);
		} else t.push({
			from: i.from,
			to: a.to,
			ranges: [r]
		});
		n = a.number + 1;
	}
	return t;
}
function $d(e, t, n) {
	if (e.readOnly) return !1;
	let r = [], i = [];
	for (let t of Qd(e)) {
		if (n ? t.to == e.doc.length : t.from == 0) continue;
		let a = e.doc.lineAt(n ? t.to + 1 : t.from - 1), o = a.length + 1;
		if (n) {
			r.push({
				from: t.to,
				to: a.to
			}, {
				from: t.from,
				insert: a.text + e.lineBreak
			});
			for (let n of t.ranges) i.push(k.range(Math.min(e.doc.length, n.anchor + o), Math.min(e.doc.length, n.head + o)));
		} else {
			r.push({
				from: a.from,
				to: t.from
			}, {
				from: t.to,
				insert: e.lineBreak + a.text
			});
			for (let e of t.ranges) i.push(k.range(e.anchor - o, e.head - o));
		}
	}
	return r.length ? (t(e.update({
		changes: r,
		scrollIntoView: !0,
		selection: k.create(i, e.selection.mainIndex),
		userEvent: "move.line"
	})), !0) : !1;
}
var ef = ({ state: e, dispatch: t }) => $d(e, t, !1), tf = ({ state: e, dispatch: t }) => $d(e, t, !0);
function nf(e, t, n) {
	if (e.readOnly) return !1;
	let r = [];
	for (let t of Qd(e)) n ? r.push({
		from: t.from,
		insert: e.doc.slice(t.from, t.to) + e.lineBreak
	}) : r.push({
		from: t.to,
		insert: e.lineBreak + e.doc.slice(t.from, t.to)
	});
	let i = e.changes(r);
	return t(e.update({
		changes: i,
		selection: e.selection.map(i, n ? 1 : -1),
		scrollIntoView: !0,
		userEvent: "input.copyline"
	})), !0;
}
var rf = ({ state: e, dispatch: t }) => nf(e, t, !1), af = ({ state: e, dispatch: t }) => nf(e, t, !0), of = (e) => {
	if (e.state.readOnly) return !1;
	let { state: t } = e, n = t.changes(Qd(t).map(({ from: e, to: n }) => (e > 0 ? e-- : n < t.doc.length && n++, {
		from: e,
		to: n
	}))), r = ju(t.selection, (t) => {
		let n;
		if (e.lineWrapping) {
			let r = e.lineBlockAt(t.head), i = e.coordsAtPos(t.head, t.assoc || 1);
			i && (n = r.bottom + e.documentTop - i.bottom + e.defaultLineHeight / 2);
		}
		return e.moveVertically(t, !0, n);
	}).map(n);
	return e.dispatch({
		changes: n,
		selection: r,
		scrollIntoView: !0,
		userEvent: "delete.line"
	}), !0;
};
function sf(e, t) {
	if (/\(\)|\[\]|\{\}/.test(e.sliceDoc(t - 1, t + 1))) return {
		from: t,
		to: t
	};
	let n = J(e).resolveInner(t), r = n.childBefore(t), i = n.childAfter(t), a;
	return r && i && r.to <= t && i.from >= t && (a = r.type.prop(H.closedBy)) && a.indexOf(i.name) > -1 && e.doc.lineAt(r.to).from == e.doc.lineAt(i.from).from && !/\S/.test(e.sliceDoc(r.to, i.from)) ? {
		from: r.to,
		to: i.from
	} : null;
}
var cf = /*@__PURE__*/ uf(!1), lf = /*@__PURE__*/ uf(!0);
function uf(e) {
	return ({ state: t, dispatch: n }) => {
		if (t.readOnly) return !1;
		let r = t.changeByRange((n) => {
			let { from: r, to: i } = n, a = t.doc.lineAt(r), o = !e && r == i && sf(t, r);
			e && (r = i = (i <= a.to ? a : t.doc.lineAt(i)).to);
			let s = new ll(t, {
				simulateBreak: r,
				simulateDoubleBreak: !!o
			}), c = cl(s, r);
			for (c ??= Lt(/^\s*/.exec(t.doc.lineAt(r).text)[0], t.tabSize); i < a.to && /\s/.test(a.text[i - a.from]);) i++;
			o ? {from: r, to: i} = o : r > a.from && r < a.from + 100 && !/\S/.test(a.text.slice(0, r)) && (r = a.from);
			let l = ["", sl(t, c)];
			return o && l.push(sl(t, s.lineIndent(a.from, -1))), {
				changes: {
					from: r,
					to: i,
					insert: T.of(l)
				},
				range: k.cursor(r + 1 + l[1].length)
			};
		});
		return n(t.update(r, {
			scrollIntoView: !0,
			userEvent: "input"
		})), !0;
	};
}
function df(e, t) {
	let n = -1;
	return e.changeByRange((r) => {
		let i = [];
		for (let a = r.from; a <= r.to;) {
			let o = e.doc.lineAt(a);
			o.number > n && (r.empty || r.to > o.from) && (t(o, i, r), n = o.number), a = o.to + 1;
		}
		let a = e.changes(i);
		return {
			changes: i,
			range: k.range(a.mapPos(r.anchor, 1), a.mapPos(r.head, 1))
		};
	});
}
var ff = ({ state: e, dispatch: t }) => {
	if (e.readOnly) return !1;
	let n = Object.create(null), r = new ll(e, { overrideIndentation: (e) => n[e] ?? -1 }), i = df(e, (t, i, a) => {
		let o = cl(r, t.from);
		if (o == null) return;
		/\S/.test(t.text) || (o = 0);
		let s = /^\s*/.exec(t.text)[0], c = sl(e, o);
		(s != c || a.from < t.from + s.length) && (n[t.from] = o, i.push({
			from: t.from,
			to: t.from + s.length,
			insert: c
		}));
	});
	return i.changes.empty || t(e.update(i, { userEvent: "indent" })), !0;
}, pf = ({ state: e, dispatch: t }) => !e.readOnly && (t(e.update(df(e, (t, n) => {
	n.push({
		from: t.from,
		insert: e.facet(al)
	});
}), { userEvent: "input.indent" })), !0), mf = ({ state: e, dispatch: t }) => !e.readOnly && (t(e.update(df(e, (t, n) => {
	let r = /^\s*/.exec(t.text)[0];
	if (!r) return;
	let i = Lt(r, e.tabSize), a = 0, o = sl(e, Math.max(0, i - ol(e)));
	for (; a < r.length && a < o.length && r.charCodeAt(a) == o.charCodeAt(a);) a++;
	n.push({
		from: t.from + a,
		to: t.from + r.length,
		insert: o.slice(a)
	});
}), { userEvent: "delete.dedent" })), !0), hf = (e) => (e.setTabFocusMode(), !0), gf = [
	{
		key: "Ctrl-b",
		run: Iu,
		shift: ld,
		preventDefault: !0
	},
	{
		key: "Ctrl-f",
		run: Lu,
		shift: ud
	},
	{
		key: "Ctrl-p",
		run: Ku,
		shift: _d
	},
	{
		key: "Ctrl-n",
		run: qu,
		shift: vd
	},
	{
		key: "Ctrl-a",
		run: rd,
		shift: Ed
	},
	{
		key: "Ctrl-e",
		run: id,
		shift: Dd
	},
	{
		key: "Ctrl-d",
		run: Ud
	},
	{
		key: "Ctrl-h",
		run: Hd
	},
	{
		key: "Ctrl-k",
		run: qd
	},
	{
		key: "Ctrl-Alt-h",
		run: Gd
	},
	{
		key: "Ctrl-o",
		run: Xd
	},
	{
		key: "Ctrl-t",
		run: Zd
	},
	{
		key: "Ctrl-v",
		run: Zu
	}
], _f = /*@__PURE__*/ [
	{
		key: "ArrowLeft",
		run: Iu,
		shift: ld,
		preventDefault: !0
	},
	{
		key: "Mod-ArrowLeft",
		mac: "Alt-ArrowLeft",
		run: zu,
		shift: fd,
		preventDefault: !0
	},
	{
		mac: "Cmd-ArrowLeft",
		run: td,
		shift: wd,
		preventDefault: !0
	},
	{
		key: "ArrowRight",
		run: Lu,
		shift: ud,
		preventDefault: !0
	},
	{
		key: "Mod-ArrowRight",
		mac: "Alt-ArrowRight",
		run: Bu,
		shift: pd,
		preventDefault: !0
	},
	{
		mac: "Cmd-ArrowRight",
		run: nd,
		shift: Td,
		preventDefault: !0
	},
	{
		key: "ArrowUp",
		run: Ku,
		shift: _d,
		preventDefault: !0
	},
	{
		mac: "Cmd-ArrowUp",
		run: Od,
		shift: Ad
	},
	{
		mac: "Ctrl-ArrowUp",
		run: Xu,
		shift: bd
	},
	{
		key: "ArrowDown",
		run: qu,
		shift: vd,
		preventDefault: !0
	},
	{
		mac: "Cmd-ArrowDown",
		run: kd,
		shift: jd
	},
	{
		mac: "Ctrl-ArrowDown",
		run: Zu,
		shift: xd
	},
	{
		key: "PageUp",
		run: Xu,
		shift: bd
	},
	{
		key: "PageDown",
		run: Zu,
		shift: xd
	},
	{
		key: "Home",
		run: ed,
		shift: Cd,
		preventDefault: !0
	},
	{
		key: "Mod-Home",
		run: Od,
		shift: Ad
	},
	{
		key: "End",
		run: $u,
		shift: Sd,
		preventDefault: !0
	},
	{
		key: "Mod-End",
		run: kd,
		shift: jd
	},
	{
		key: "Enter",
		run: cf,
		shift: cf
	},
	{
		key: "Mod-a",
		run: Md
	},
	{
		key: "Backspace",
		run: Hd,
		shift: Hd,
		preventDefault: !0
	},
	{
		key: "Delete",
		run: Ud,
		preventDefault: !0
	},
	{
		key: "Mod-Backspace",
		mac: "Alt-Backspace",
		run: Gd,
		preventDefault: !0
	},
	{
		key: "Mod-Delete",
		mac: "Alt-Delete",
		run: Kd,
		preventDefault: !0
	},
	{
		mac: "Mod-Backspace",
		run: Jd,
		preventDefault: !0
	},
	{
		mac: "Mod-Delete",
		run: Yd,
		preventDefault: !0
	}
].concat(/*@__PURE__*/ gf.map((e) => ({
	mac: e.key,
	run: e.run,
	shift: e.shift
}))), vf = /*@__PURE__*/ [
	{
		key: "Alt-ArrowLeft",
		mac: "Ctrl-ArrowLeft",
		run: Uu,
		shift: md
	},
	{
		key: "Alt-ArrowRight",
		mac: "Ctrl-ArrowRight",
		run: Wu,
		shift: hd
	},
	{
		key: "Alt-ArrowUp",
		run: ef
	},
	{
		key: "Shift-Alt-ArrowUp",
		run: rf
	},
	{
		key: "Alt-ArrowDown",
		run: tf
	},
	{
		key: "Shift-Alt-ArrowDown",
		run: af
	},
	{
		key: "Mod-Alt-ArrowUp",
		run: Id
	},
	{
		key: "Mod-Alt-ArrowDown",
		run: Ld
	},
	{
		key: "Escape",
		run: Rd
	},
	{
		key: "Mod-Enter",
		run: lf
	},
	{
		key: "Alt-l",
		mac: "Ctrl-l",
		run: Nd
	},
	{
		key: "Mod-i",
		run: Pd,
		preventDefault: !0
	},
	{
		key: "Mod-[",
		run: mf
	},
	{
		key: "Mod-]",
		run: pf
	},
	{
		key: "Mod-Alt-\\",
		run: ff
	},
	{
		key: "Shift-Mod-k",
		run: of
	},
	{
		key: "Shift-Mod-\\",
		run: od
	},
	{
		key: "Mod-/",
		run: Yl
	},
	{
		key: "Alt-A",
		mac: "Ctrl-A",
		run: Ql
	},
	{
		key: "Ctrl-m",
		mac: "Shift-Alt-m",
		run: hf
	}
].concat(_f), yf = class {
	constructor(e, t, n, r) {
		this.state = e, this.pos = t, this.explicit = n, this.view = r, this.abortListeners = [], this.abortOnDocChange = !1;
	}
	tokenBefore(e) {
		let t = J(this.state).resolveInner(this.pos, -1);
		for (; t && e.indexOf(t.name) < 0;) t = t.parent;
		return t ? {
			from: t.from,
			to: this.pos,
			text: this.state.sliceDoc(t.from, this.pos),
			type: t.type
		} : null;
	}
	matchBefore(e) {
		let t = this.state.doc.lineAt(this.pos), n = Math.max(t.from, this.pos - 250), r = t.text.slice(n - t.from, this.pos - t.from), i = r.search(Ef(e, !1));
		return i < 0 ? null : {
			from: n + i,
			to: this.pos,
			text: r.slice(i)
		};
	}
	get aborted() {
		return this.abortListeners == null;
	}
	addEventListener(e, t, n) {
		e == "abort" && this.abortListeners && (this.abortListeners.push(t), n && n.onDocChange && (this.abortOnDocChange = !0));
	}
};
function bf(e) {
	let t = Object.keys(e).join(""), n = /\w/.test(t);
	return n && (t = t.replace(/\w/g, "")), `[${n ? "\\w" : ""}${t.replace(/[^\w\s]/g, "\\$&")}]`;
}
function xf(e) {
	let t = Object.create(null), n = Object.create(null);
	for (let { label: r } of e) {
		t[r[0]] = !0;
		for (let e = 1; e < r.length; e++) n[r[e]] = !0;
	}
	let r = bf(t) + bf(n) + "*$";
	return [RegExp("^" + r), new RegExp(r)];
}
function Sf(e) {
	let t = e.map((e) => typeof e == "string" ? { label: e } : e), [n, r] = t.every((e) => /^\w+$/.test(e.label)) ? [/\w*$/, /\w+$/] : xf(t);
	return (e) => {
		let i = e.matchBefore(r);
		return i || e.explicit ? {
			from: i ? i.from : e.pos,
			options: t,
			validFor: n
		} : null;
	};
}
function Cf(e, t) {
	return (n) => {
		for (let t = J(n.state).resolveInner(n.pos, -1); t; t = t.parent) {
			if (e.indexOf(t.name) > -1) return null;
			if (t.type.isTop) break;
		}
		return t(n);
	};
}
var wf = class {
	constructor(e, t, n, r) {
		this.completion = e, this.source = t, this.match = n, this.score = r;
	}
};
function Tf(e) {
	return e.selection.main.from;
}
function Ef(e, t) {
	let { source: n } = e, r = t && n[0] != "^", i = n[n.length - 1] != "$";
	return !r && !i ? e : RegExp(`${r ? "^" : ""}(?:${n})${i ? "$" : ""}`, e.flags ?? (e.ignoreCase ? "i" : ""));
}
var Df = /*@__PURE__*/ tt.define();
function Of(e, t, n, r) {
	let { main: i } = e.selection, a = n - i.from, o = r - i.from;
	return {
		...e.changeByRange((s) => {
			if (s != i && n != r && e.sliceDoc(s.from + a, s.from + o) != e.sliceDoc(n, r)) return { range: s };
			let c = e.toText(t);
			return {
				changes: {
					from: s.from + a,
					to: r == i.from ? s.to : s.from + o,
					insert: c
				},
				range: k.cursor(s.from + a + c.length)
			};
		}),
		scrollIntoView: !0,
		userEvent: "input.complete"
	};
}
var kf = /*@__PURE__*/ new WeakMap();
function Af(e) {
	if (!Array.isArray(e)) return e;
	let t = kf.get(e);
	return t || kf.set(e, t = Sf(e)), t;
}
var jf = /*@__PURE__*/ j.define(), Mf = /*@__PURE__*/ j.define(), Nf = class {
	constructor(e) {
		this.pattern = e, this.chars = [], this.folded = [], this.any = [], this.precise = [], this.byWord = [], this.score = 0, this.matched = [];
		for (let t = 0; t < e.length;) {
			let n = ge(e, t), r = ve(n);
			this.chars.push(n);
			let i = e.slice(t, t + r), a = i.toUpperCase();
			this.folded.push(ge(a == i ? i.toLowerCase() : a, 0)), t += r;
		}
		this.astral = e.length != this.chars.length;
	}
	ret(e, t) {
		return this.score = e, this.matched = t, this;
	}
	match(e) {
		if (this.pattern.length == 0) return this.ret(-100, []);
		if (e.length < this.pattern.length) return null;
		let { chars: t, folded: n, any: r, precise: i, byWord: a } = this;
		if (t.length == 1) {
			let r = ge(e, 0), i = ve(r), a = i == e.length ? 0 : -100;
			if (r != t[0]) {
				if (r == n[0]) a += -200;
				else return null;
			}
			return this.ret(a, [0, i]);
		}
		let o = e.indexOf(this.pattern);
		if (o == 0) return this.ret(e.length == this.pattern.length ? 0 : -100, [0, this.pattern.length]);
		let s = t.length, c = 0;
		if (o < 0) {
			for (let i = 0, a = Math.min(e.length, 200); i < a && c < s;) {
				let a = ge(e, i);
				(a == t[c] || a == n[c]) && (r[c++] = i), i += ve(a);
			}
			if (c < s) return null;
		}
		let l = 0, u = 0, d = !1, f = 0, p = -1, m = -1, h = /[a-z]/.test(e), g = !0;
		for (let r = 0, c = Math.min(e.length, 200), _ = 0; r < c && u < s;) {
			let c = ge(e, r);
			o < 0 && (l < s && c == t[l] && (i[l++] = r), f < s && (c == t[f] || c == n[f] ? (f == 0 && (p = r), m = r + 1, f++) : f = 0));
			let v, y = c < 255 ? c >= 48 && c <= 57 || c >= 97 && c <= 122 ? 2 : +(c >= 65 && c <= 90) : (v = _e(c)) == v.toLowerCase() ? v == v.toUpperCase() ? 0 : 2 : 1;
			(!r || y == 1 && h || _ == 0 && y != 0) && (t[u] == c || n[u] == c && (d = !0) ? a[u++] = r : a.length && (g = !1)), _ = y, r += ve(c);
		}
		return u == s && a[0] == 0 && g ? this.result(-100 + (d ? -200 : 0), a, e) : f == s && p == 0 ? this.ret(-200 - e.length + (m == e.length ? 0 : -100), [0, m]) : o > -1 ? this.ret(-700 - e.length, [o, o + this.pattern.length]) : f == s ? this.ret(-900 - e.length, [p, m]) : u == s ? this.result(-100 + (d ? -200 : 0) + -700 + (g ? 0 : -1100), a, e) : t.length == 2 ? null : this.result((r[0] ? -700 : 0) + -200 + -1100, r, e);
	}
	result(e, t, n) {
		let r = [], i = 0;
		for (let e of t) {
			let t = e + (this.astral ? ve(ge(n, e)) : 1);
			i && r[i - 1] == e ? r[i - 1] = t : (r[i++] = e, r[i++] = t);
		}
		return this.ret(e - n.length, r);
	}
}, Pf = class {
	constructor(e) {
		this.pattern = e, this.matched = [], this.score = 0, this.folded = e.toLowerCase();
	}
	match(e) {
		if (e.length < this.pattern.length) return null;
		let t = e.slice(0, this.pattern.length), n = t == this.pattern ? 0 : t.toLowerCase() == this.folded ? -200 : null;
		return n == null ? null : (this.matched = [0, t.length], this.score = n + (e.length == this.pattern.length ? 0 : -100), this);
	}
}, X = /*@__PURE__*/ A.define({ combine(e) {
	return vt(e, {
		activateOnTyping: !0,
		activateOnCompletion: () => !1,
		activateOnTypingDelay: 100,
		selectOnOpen: !0,
		override: null,
		closeOnBlur: !0,
		maxRenderedOptions: 100,
		defaultKeymap: !0,
		tooltipClass: () => "",
		optionClass: () => "",
		aboveCursor: !1,
		icons: !0,
		addToOptions: [],
		positionInfo: If,
		filterStrict: !1,
		compareCompletions: (e, t) => (e.sortText || e.label).localeCompare(t.sortText || t.label),
		interactionDelay: 75,
		updateSyncTime: 100
	}, {
		defaultKeymap: (e, t) => e && t,
		closeOnBlur: (e, t) => e && t,
		icons: (e, t) => e && t,
		tooltipClass: (e, t) => (n) => Ff(e(n), t(n)),
		optionClass: (e, t) => (n) => Ff(e(n), t(n)),
		addToOptions: (e, t) => e.concat(t),
		filterStrict: (e, t) => e || t
	});
} });
function Ff(e, t) {
	return e ? t ? e + " " + t : e : t;
}
function If(e, t, n, r, i, a) {
	let o = e.textDirection == I.RTL, s = o, c = !1, l = "top", u, d, f = t.left - i.left, p = i.right - t.right, m = r.right - r.left, h = r.bottom - r.top;
	if (s && f < Math.min(m, p) ? s = !1 : !s && p < Math.min(m, f) && (s = !0), m <= (s ? f : p)) u = Math.max(i.top, Math.min(n.top, i.bottom - h)) - t.top, d = Math.min(400, s ? f : p);
	else {
		c = !0, d = Math.min(400, (o ? t.right : i.right - t.left) - 30);
		let e = i.bottom - t.bottom;
		e >= h || e > t.top ? u = n.bottom - t.top : (l = "bottom", u = t.bottom - n.top);
	}
	let g = (t.bottom - t.top) / a.offsetHeight, _ = (t.right - t.left) / a.offsetWidth;
	return {
		style: `${l}: ${u / g}px; max-width: ${d / _}px`,
		class: "cm-completionInfo-" + (c ? o ? "left-narrow" : "right-narrow" : s ? "left" : "right")
	};
}
var Lf = /*@__PURE__*/ j.define();
function Rf(e) {
	let t = e.addToOptions.slice();
	return e.icons && t.push({
		render(e) {
			let t = document.createElement("div");
			return t.classList.add("cm-completionIcon"), e.type && t.classList.add(...e.type.split(/\s+/g).map((e) => "cm-completionIcon-" + e)), t.setAttribute("aria-hidden", "true"), t;
		},
		position: 20
	}), t.push({
		render(e, t, n, r) {
			let i = document.createElement("span");
			i.className = "cm-completionLabel";
			let a = e.displayLabel || e.label, o = 0;
			for (let e = 0; e < r.length;) {
				let t = r[e++], n = r[e++];
				t > o && i.appendChild(document.createTextNode(a.slice(o, t)));
				let s = i.appendChild(document.createElement("span"));
				s.appendChild(document.createTextNode(a.slice(t, n))), s.className = "cm-completionMatchedText", o = n;
			}
			return o < a.length && i.appendChild(document.createTextNode(a.slice(o))), i;
		},
		position: 50
	}, {
		render(e) {
			if (!e.detail) return null;
			let t = document.createElement("span");
			return t.className = "cm-completionDetail", t.textContent = e.detail, t;
		},
		position: 80
	}), t.sort((e, t) => e.position - t.position).map((e) => e.render);
}
function zf(e, t, n) {
	if (e <= n) return {
		from: 0,
		to: e
	};
	if (t < 0 && (t = 0), t <= e >> 1) {
		let e = Math.floor(t / n);
		return {
			from: e * n,
			to: (e + 1) * n
		};
	}
	let r = Math.ceil((e - t) / n);
	return {
		from: e - r * n,
		to: e - (r - 1) * n
	};
}
var Bf = class {
	constructor(e, t, n) {
		this.view = e, this.stateField = t, this.applyCompletion = n, this.info = null, this.infoDestroy = null, this.placeInfoReq = {
			read: () => this.measureInfo(),
			write: (e) => this.placeInfo(e),
			key: this
		}, this.space = null, this.currentClass = "";
		let r = e.state.field(t), { options: i, selected: a } = r.open, o = e.state.facet(X);
		this.optionContent = Rf(o), this.optionClass = o.optionClass, this.tooltipClass = o.tooltipClass, this.range = zf(i.length, a, o.maxRenderedOptions), this.dom = document.createElement("div"), this.dom.className = "cm-tooltip-autocomplete", this.updateTooltipClass(e.state), this.dom.addEventListener("mousedown", (n) => {
			let { options: r } = e.state.field(t).open;
			for (let t = n.target, i; t && t != this.dom; t = t.parentNode) if (t.nodeName == "LI" && (i = /-(\d+)$/.exec(t.id)) && +i[1] < r.length) {
				this.applyCompletion(e, r[+i[1]]), n.preventDefault();
				return;
			}
			if (n.target == this.list) {
				let t = this.list.classList.contains("cm-completionListIncompleteTop") && n.clientY < this.list.firstChild.getBoundingClientRect().top ? this.range.from - 1 : this.list.classList.contains("cm-completionListIncompleteBottom") && n.clientY > this.list.lastChild.getBoundingClientRect().bottom ? this.range.to : null;
				t != null && (e.dispatch({ effects: Lf.of(t) }), n.preventDefault());
			}
		}), this.dom.addEventListener("focusout", (t) => {
			let n = e.state.field(this.stateField, !1);
			n && n.tooltip && e.state.facet(X).closeOnBlur && t.relatedTarget != e.contentDOM && e.dispatch({ effects: Mf.of(null) });
		}), this.showOptions(i, r.id);
	}
	mount() {
		this.updateSel();
	}
	showOptions(e, t) {
		this.list && this.list.remove(), this.list = this.dom.appendChild(this.createListBox(e, t, this.range)), this.list.addEventListener("scroll", () => {
			this.info && this.view.requestMeasure(this.placeInfoReq);
		});
	}
	update(e) {
		let t = e.state.field(this.stateField), n = e.startState.field(this.stateField);
		if (this.updateTooltipClass(e.state), t != n) {
			let { options: r, selected: i, disabled: a } = t.open;
			(!n.open || n.open.options != r) && (this.range = zf(r.length, i, e.state.facet(X).maxRenderedOptions), this.showOptions(r, t.id)), this.updateSel(), a != n.open?.disabled && this.dom.classList.toggle("cm-tooltip-autocomplete-disabled", !!a);
		}
	}
	updateTooltipClass(e) {
		let t = this.tooltipClass(e);
		if (t != this.currentClass) {
			for (let e of this.currentClass.split(" ")) e && this.dom.classList.remove(e);
			for (let e of t.split(" ")) e && this.dom.classList.add(e);
			this.currentClass = t;
		}
	}
	positioned(e) {
		this.space = e, this.info && this.view.requestMeasure(this.placeInfoReq);
	}
	updateSel() {
		let e = this.view.state.field(this.stateField), t = e.open;
		(t.selected > -1 && t.selected < this.range.from || t.selected >= this.range.to) && (this.range = zf(t.options.length, t.selected, this.view.state.facet(X).maxRenderedOptions), this.showOptions(t.options, e.id));
		let n = this.updateSelectedOption(t.selected);
		if (n) {
			this.destroyInfo();
			let { completion: r } = t.options[t.selected], { info: i } = r;
			if (!i) return;
			let a = typeof i == "string" ? document.createTextNode(i) : i(r);
			if (!a) return;
			"then" in a ? a.then((t) => {
				t && this.view.state.field(this.stateField, !1) == e && this.addInfoPane(t, r);
			}).catch((e) => Ir(this.view.state, e, "completion info")) : (this.addInfoPane(a, r), n.setAttribute("aria-describedby", this.info.id));
		}
	}
	addInfoPane(e, t) {
		this.destroyInfo();
		let n = this.info = document.createElement("div");
		if (n.className = "cm-tooltip cm-completionInfo", n.id = "cm-completionInfo-" + Math.floor(Math.random() * 65535).toString(16), e.nodeType != null) n.appendChild(e), this.infoDestroy = null;
		else {
			let { dom: t, destroy: r } = e;
			n.appendChild(t), this.infoDestroy = r || null;
		}
		this.dom.appendChild(n), this.view.requestMeasure(this.placeInfoReq);
	}
	updateSelectedOption(e) {
		let t = null;
		for (let n = this.list.firstChild, r = this.range.from; n; n = n.nextSibling, r++) n.nodeName != "LI" || !n.id ? r-- : r == e ? n.hasAttribute("aria-selected") || (n.setAttribute("aria-selected", "true"), t = n) : n.hasAttribute("aria-selected") && (n.removeAttribute("aria-selected"), n.removeAttribute("aria-describedby"));
		return t && Hf(this.list, t), t;
	}
	measureInfo() {
		let e = this.dom.querySelector("[aria-selected]");
		if (!e || !this.info) return null;
		let t = this.dom.getBoundingClientRect(), n = this.info.getBoundingClientRect(), r = e.getBoundingClientRect(), i = this.space;
		if (!i) {
			let e = this.dom.ownerDocument.documentElement;
			i = {
				left: 0,
				top: 0,
				right: e.clientWidth,
				bottom: e.clientHeight
			};
		}
		return r.top > Math.min(i.bottom, t.bottom) - 10 || r.bottom < Math.max(i.top, t.top) + 10 ? null : this.view.state.facet(X).positionInfo(this.view, t, r, n, i, this.dom);
	}
	placeInfo(e) {
		this.info && (e ? (e.style && (this.info.style.cssText = e.style), this.info.className = "cm-tooltip cm-completionInfo " + (e.class || "")) : this.info.style.cssText = "top: -1e6px");
	}
	createListBox(e, t, n) {
		let r = document.createElement("ul");
		r.id = t, r.setAttribute("role", "listbox"), r.setAttribute("aria-expanded", "true"), r.setAttribute("aria-label", this.view.state.phrase("Completions")), r.addEventListener("mousedown", (e) => {
			e.target == r && e.preventDefault();
		});
		let i = null;
		for (let a = n.from; a < n.to; a++) {
			let { completion: o, match: s } = e[a], { section: c } = o;
			if (c) {
				let e = typeof c == "string" ? c : c.name;
				if (e != i && (a > n.from || n.from == 0)) {
					if (i = e, typeof c != "string" && c.header) r.appendChild(c.header(c));
					else {
						let t = r.appendChild(document.createElement("completion-section"));
						t.textContent = e;
					}
				}
			}
			let l = r.appendChild(document.createElement("li"));
			l.id = t + "-" + a, l.setAttribute("role", "option");
			let u = this.optionClass(o);
			u && (l.className = u);
			for (let e of this.optionContent) {
				let t = e(o, this.view.state, this.view, s);
				t && l.appendChild(t);
			}
		}
		return n.from && r.classList.add("cm-completionListIncompleteTop"), n.to < e.length && r.classList.add("cm-completionListIncompleteBottom"), r;
	}
	destroyInfo() {
		this.info &&= (this.infoDestroy && this.infoDestroy(), this.info.remove(), null);
	}
	destroy() {
		this.destroyInfo();
	}
};
function Vf(e, t) {
	return (n) => new Bf(n, e, t);
}
function Hf(e, t) {
	let n = e.getBoundingClientRect(), r = t.getBoundingClientRect(), i = n.height / e.offsetHeight;
	r.top < n.top ? e.scrollTop -= (n.top - r.top) / i : r.bottom > n.bottom && (e.scrollTop += (r.bottom - n.bottom) / i);
}
function Uf(e) {
	return (e.boost || 0) * 100 + (e.apply ? 10 : 0) + (e.info ? 5 : 0) + +!!e.type;
}
function Wf(e, t) {
	let n = [], r = null, i = null, a = (e) => {
		n.push(e);
		let { section: t } = e.completion;
		if (t) {
			r ||= [];
			let e = typeof t == "string" ? t : t.name;
			r.some((t) => t.name == e) || r.push(typeof t == "string" ? { name: e } : t);
		}
	}, o = t.facet(X);
	for (let r of e) if (r.hasResult()) {
		let e = r.result.getMatch;
		if (r.result.filter === !1) for (let t of r.result.options) a(new wf(t, r.source, e ? e(t) : [], 1e9 - n.length));
		else {
			let n = t.sliceDoc(r.from, r.to), s, c = o.filterStrict ? new Pf(n) : new Nf(n);
			for (let t of r.result.options) if (s = c.match(t.label)) {
				let n = t.displayLabel ? e ? e(t, s.matched) : [] : s.matched, o = s.score + (t.boost || 0);
				if (a(new wf(t, r.source, n, o)), typeof t.section == "object" && t.section.rank === "dynamic") {
					let { name: e } = t.section;
					i ||= Object.create(null), i[e] = Math.max(o, i[e] || -1e9);
				}
			}
		}
	}
	if (r) {
		let e = Object.create(null), t = 0, a = (e, t) => (e.rank === "dynamic" && t.rank === "dynamic" ? i[t.name] - i[e.name] : 0) || (typeof e.rank == "number" ? e.rank : 1e9) - (typeof t.rank == "number" ? t.rank : 1e9) || (e.name < t.name ? -1 : 1);
		for (let n of r.sort(a)) t -= 1e5, e[n.name] = t;
		for (let t of n) {
			let { section: n } = t.completion;
			n && (t.score += e[typeof n == "string" ? n : n.name]);
		}
	}
	let s = [], c = null, l = o.compareCompletions;
	for (let e of n.sort((e, t) => t.score - e.score || l(e.completion, t.completion))) {
		let t = e.completion;
		!c || c.label != t.label || c.detail != t.detail || c.type != null && t.type != null && c.type != t.type || c.apply != t.apply || c.boost != t.boost ? s.push(e) : Uf(e.completion) > Uf(c) && (s[s.length - 1] = e), c = e.completion;
	}
	return s;
}
var Gf = class e {
	constructor(e, t, n, r, i, a) {
		this.options = e, this.attrs = t, this.tooltip = n, this.timestamp = r, this.selected = i, this.disabled = a;
	}
	setSelected(t, n) {
		return t == this.selected || t >= this.options.length ? this : new e(this.options, Xf(n, t), this.tooltip, this.timestamp, t, this.disabled);
	}
	static build(t, n, r, i, a, o) {
		if (i && !o && t.some((e) => e.isPending)) return i.setDisabled();
		let s = Wf(t, n);
		if (!s.length) return i && t.some((e) => e.isPending) ? i.setDisabled() : null;
		let c = n.facet(X).selectOnOpen ? 0 : -1;
		if (i && i.selected != c && i.selected != -1) {
			let e = i.options[i.selected].completion;
			for (let t = 0; t < s.length; t++) if (s[t].completion == e) {
				c = t;
				break;
			}
		}
		return new e(s, Xf(r, c), {
			pos: t.reduce((e, t) => t.hasResult() ? Math.min(e, t.from) : e, 1e8),
			create: ap,
			above: a.aboveCursor
		}, i ? i.timestamp : Date.now(), c, !1);
	}
	map(t) {
		return new e(this.options, this.attrs, {
			...this.tooltip,
			pos: t.mapPos(this.tooltip.pos)
		}, this.timestamp, this.selected, this.disabled);
	}
	setDisabled() {
		return new e(this.options, this.attrs, this.tooltip, this.timestamp, this.selected, !0);
	}
}, Kf = class e {
	constructor(e, t, n) {
		this.active = e, this.id = t, this.open = n;
	}
	static start() {
		return new e(Zf, "cm-ac-" + Math.floor(Math.random() * 2e6).toString(36), null);
	}
	update(t) {
		let { state: n } = t, r = n.facet(X), i = (r.override || n.languageDataAt("autocomplete", Tf(n)).map(Af)).map((e) => (this.active.find((t) => t.source == e) || new $f(e, +!!this.active.some((e) => e.state != 0))).update(t, r));
		i.length == this.active.length && i.every((e, t) => e == this.active[t]) && (i = this.active);
		let a = this.open, o = t.effects.some((e) => e.is(np));
		a && t.docChanged && (a = a.map(t.changes)), t.selection || i.some((e) => e.hasResult() && t.changes.touchesRange(e.from, e.to)) || !qf(i, this.active) || o ? a = Gf.build(i, n, this.id, a, r, o) : a && a.disabled && !i.some((e) => e.isPending) && (a = null), !a && i.every((e) => !e.isPending) && i.some((e) => e.hasResult()) && (i = i.map((e) => e.hasResult() ? new $f(e.source, 0) : e));
		for (let e of t.effects) e.is(Lf) && (a &&= a.setSelected(e.value, this.id));
		return i == this.active && a == this.open ? this : new e(i, this.id, a);
	}
	get tooltip() {
		return this.open ? this.open.tooltip : null;
	}
	get attrs() {
		return this.open ? this.open.attrs : this.active.length ? Jf : Yf;
	}
};
function qf(e, t) {
	if (e == t) return !0;
	for (let n = 0, r = 0;;) {
		for (; n < e.length && !e[n].hasResult();) n++;
		for (; r < t.length && !t[r].hasResult();) r++;
		let i = n == e.length, a = r == t.length;
		if (i || a) return i == a;
		if (e[n++].result != t[r++].result) return !1;
	}
}
var Jf = { "aria-autocomplete": "list" }, Yf = {};
function Xf(e, t) {
	let n = {
		"aria-autocomplete": "list",
		"aria-haspopup": "listbox",
		"aria-controls": e
	};
	return t > -1 && (n["aria-activedescendant"] = e + "-" + t), n;
}
var Zf = [];
function Qf(e, t) {
	if (e.isUserEvent("input.complete")) {
		let n = e.annotation(Df);
		if (n && t.activateOnCompletion(n)) return 12;
	}
	let n = e.isUserEvent("input.type");
	return n && t.activateOnTyping ? 5 : n ? 1 : e.isUserEvent("delete.backward") ? 2 : e.selection ? 8 : e.docChanged ? 16 : 0;
}
var $f = class e {
	constructor(e, t, n = !1) {
		this.source = e, this.state = t, this.explicit = n;
	}
	hasResult() {
		return !1;
	}
	get isPending() {
		return this.state == 1;
	}
	update(t, n) {
		let r = Qf(t, n), i = this;
		(r & 8 || r & 16 && this.touches(t)) && (i = new e(i.source, 0)), r & 4 && i.state == 0 && (i = new e(this.source, 1)), i = i.updateFor(t, r);
		for (let n of t.effects) if (n.is(jf)) i = new e(i.source, 1, n.value);
		else if (n.is(Mf)) i = new e(i.source, 0);
		else if (n.is(np)) for (let e of n.value) e.source == i.source && (i = e);
		return i;
	}
	updateFor(e, t) {
		return this.map(e.changes);
	}
	map(e) {
		return this;
	}
	touches(e) {
		return e.changes.touchesRange(Tf(e.state));
	}
}, ep = class e extends $f {
	constructor(e, t, n, r, i, a) {
		super(e, 3, t), this.limit = n, this.result = r, this.from = i, this.to = a;
	}
	hasResult() {
		return !0;
	}
	updateFor(t, n) {
		if (!(n & 3)) return this.map(t.changes);
		let r = this.result;
		r.map && !t.changes.empty && (r = r.map(r, t.changes));
		let i = t.changes.mapPos(this.from), a = t.changes.mapPos(this.to, 1), o = Tf(t.state);
		if (o > a || !r || n & 2 && (Tf(t.startState) == this.from || o < this.limit)) return new $f(this.source, n & 4 ? 1 : 0);
		let s = t.changes.mapPos(this.limit);
		return tp(r.validFor, t.state, i, a) ? new e(this.source, this.explicit, s, r, i, a) : r.update && (r = r.update(r, i, a, new yf(t.state, o, !1))) ? new e(this.source, this.explicit, s, r, r.from, r.to ?? Tf(t.state)) : new $f(this.source, 1, this.explicit);
	}
	map(t) {
		if (t.empty) return this;
		let n = this.result.map ? this.result.map(this.result, t) : this.result;
		return n ? new e(this.source, this.explicit, t.mapPos(this.limit), n, t.mapPos(this.from), t.mapPos(this.to, 1)) : new $f(this.source, 0);
	}
	touches(e) {
		return e.changes.touchesRange(this.from, this.to);
	}
};
function tp(e, t, n, r) {
	if (!e) return !1;
	let i = t.sliceDoc(n, r);
	return typeof e == "function" ? e(i, n, r, t) : Ef(e, !0).test(i);
}
var np = /*@__PURE__*/ j.define({ map(e, t) {
	return e.map((e) => e.map(t));
} }), rp = /*@__PURE__*/ Le.define({
	create() {
		return Kf.start();
	},
	update(e, t) {
		return e.update(t);
	},
	provide: (e) => [vs.from(e, (e) => e.tooltip), V.contentAttributes.from(e, (e) => e.attrs)]
});
function ip(e, t) {
	let n = t.completion.apply || t.completion.label, r = e.state.field(rp).active.find((e) => e.source == t.source);
	return r instanceof ep && (typeof n == "string" ? e.dispatch({
		...Of(e.state, n, r.from, r.to),
		annotations: Df.of(t.completion)
	}) : n(e, t.completion, r.from, r.to), !0);
}
var ap = /*@__PURE__*/ Vf(rp, ip);
function op(e, t = "option") {
	return (n) => {
		let r = n.state.field(rp, !1);
		if (!r || !r.open || r.open.disabled || Date.now() - r.open.timestamp < n.state.facet(X).interactionDelay) return !1;
		let i = 1, a;
		t == "page" && (a = ys(n, r.open.tooltip)) && (i = Math.max(2, Math.floor(a.dom.offsetHeight / a.dom.querySelector("li").offsetHeight) - 1));
		let { length: o } = r.open.options, s = r.open.selected > -1 ? r.open.selected + i * (e ? 1 : -1) : e ? 0 : o - 1;
		return s < 0 ? s = t == "page" ? 0 : o - 1 : s >= o && (s = t == "page" ? o - 1 : 0), n.dispatch({ effects: Lf.of(s) }), !0;
	};
}
var sp = (e) => {
	let t = e.state.field(rp, !1);
	return e.state.readOnly || !t || !t.open || t.open.selected < 0 || t.open.disabled || Date.now() - t.open.timestamp < e.state.facet(X).interactionDelay ? !1 : ip(e, t.open.options[t.open.selected]);
}, cp = (e) => e.state.field(rp, !1) ? (e.dispatch({ effects: jf.of(!0) }), !0) : !1, lp = (e) => {
	let t = e.state.field(rp, !1);
	return !t || !t.active.some((e) => e.state != 0) ? !1 : (e.dispatch({ effects: Mf.of(null) }), !0);
}, up = class {
	constructor(e, t) {
		this.active = e, this.context = t, this.time = Date.now(), this.updates = [], this.done = void 0;
	}
}, dp = 50, fp = 1e3, pp = /*@__PURE__*/ Br.fromClass(class {
	constructor(e) {
		this.view = e, this.debounceUpdate = -1, this.running = [], this.debounceAccept = -1, this.pendingStart = !1, this.composing = 0;
		for (let t of e.state.field(rp).active) t.isPending && this.startQuery(t);
	}
	update(e) {
		let t = e.state.field(rp), n = e.state.facet(X);
		if (!e.selectionSet && !e.docChanged && e.startState.field(rp) == t) return;
		let r = e.transactions.some((e) => {
			let t = Qf(e, n);
			return t & 8 || (e.selection || e.docChanged) && !(t & 3);
		});
		for (let t = 0; t < this.running.length; t++) {
			let n = this.running[t];
			if (r || n.context.abortOnDocChange && e.docChanged || n.updates.length + e.transactions.length > dp && Date.now() - n.time > fp) {
				for (let e of n.context.abortListeners) try {
					e();
				} catch (e) {
					Ir(this.view.state, e);
				}
				n.context.abortListeners = null, this.running.splice(t--, 1);
			} else n.updates.push(...e.transactions);
		}
		this.debounceUpdate > -1 && clearTimeout(this.debounceUpdate), e.transactions.some((e) => e.effects.some((e) => e.is(jf))) && (this.pendingStart = !0);
		let i = this.pendingStart ? 50 : n.activateOnTypingDelay;
		if (this.debounceUpdate = t.active.some((e) => e.isPending && !this.running.some((t) => t.active.source == e.source)) ? setTimeout(() => this.startUpdate(), i) : -1, this.composing != 0) for (let t of e.transactions) t.isUserEvent("input.type") ? this.composing = 2 : this.composing == 2 && t.selection && (this.composing = 3);
	}
	startUpdate() {
		this.debounceUpdate = -1, this.pendingStart = !1;
		let { state: e } = this.view, t = e.field(rp);
		for (let e of t.active) e.isPending && !this.running.some((t) => t.active.source == e.source) && this.startQuery(e);
		this.running.length && t.open && t.open.disabled && (this.debounceAccept = setTimeout(() => this.accept(), this.view.state.facet(X).updateSyncTime));
	}
	startQuery(e) {
		let { state: t } = this.view, n = new yf(t, Tf(t), e.explicit, this.view), r = new up(e, n);
		this.running.push(r), Promise.resolve(e.source(n)).then((e) => {
			r.context.aborted || (r.done = e || null, this.scheduleAccept());
		}, (e) => {
			this.view.dispatch({ effects: Mf.of(null) }), Ir(this.view.state, e);
		});
	}
	scheduleAccept() {
		this.running.every((e) => e.done !== void 0) ? this.accept() : this.debounceAccept < 0 && (this.debounceAccept = setTimeout(() => this.accept(), this.view.state.facet(X).updateSyncTime));
	}
	accept() {
		this.debounceAccept > -1 && clearTimeout(this.debounceAccept), this.debounceAccept = -1;
		let e = [], t = this.view.state.facet(X), n = this.view.state.field(rp);
		for (let r = 0; r < this.running.length; r++) {
			let i = this.running[r];
			if (i.done === void 0) continue;
			if (this.running.splice(r--, 1), i.done) {
				let n = Tf(i.updates.length ? i.updates[0].startState : this.view.state), r = Math.min(n, i.done.from + +!i.active.explicit), a = new ep(i.active.source, i.active.explicit, r, i.done, i.done.from, i.done.to ?? n);
				for (let e of i.updates) a = a.update(e, t);
				if (a.hasResult()) {
					e.push(a);
					continue;
				}
			}
			let a = n.active.find((e) => e.source == i.active.source);
			if (a && a.isPending) {
				if (i.done == null) {
					let n = new $f(i.active.source, 0);
					for (let e of i.updates) n = n.update(e, t);
					n.isPending || e.push(n);
				} else this.startQuery(a);
			}
		}
		(e.length || n.open && n.open.disabled) && this.view.dispatch({ effects: np.of(e) });
	}
}, { eventHandlers: {
	blur(e) {
		let t = this.view.state.field(rp, !1);
		if (t && t.tooltip && this.view.state.facet(X).closeOnBlur) {
			let n = t.open && ys(this.view, t.open.tooltip);
			(!n || !n.dom.contains(e.relatedTarget)) && setTimeout(() => this.view.dispatch({ effects: Mf.of(null) }), 10);
		}
	},
	compositionstart() {
		this.composing = 1;
	},
	compositionend() {
		this.composing == 3 && setTimeout(() => this.view.dispatch({ effects: jf.of(!1) }), 20), this.composing = 0;
	}
} }), mp = typeof navigator == "object" && /*@__PURE__*/ /Win/.test(navigator.platform), hp = /*@__PURE__*/ Be.highest(/*@__PURE__*/ V.domEventHandlers({ keydown(e, t) {
	let n = t.state.field(rp, !1);
	if (!n || !n.open || n.open.disabled || n.open.selected < 0 || e.key.length > 1 || e.ctrlKey && !(mp && e.altKey) || e.metaKey) return !1;
	let r = n.open.options[n.open.selected], i = n.active.find((e) => e.source == r.source), a = r.completion.commitCharacters || i.result.commitCharacters;
	return a && a.indexOf(e.key) > -1 && ip(t, r), !1;
} })), gp = /*@__PURE__*/ V.baseTheme({
	".cm-tooltip.cm-tooltip-autocomplete": { "& > ul": {
		fontFamily: "monospace",
		whiteSpace: "nowrap",
		overflow: "hidden auto",
		maxWidth_fallback: "700px",
		maxWidth: "min(700px, 95vw)",
		minWidth: "250px",
		maxHeight: "10em",
		height: "100%",
		listStyle: "none",
		margin: 0,
		padding: 0,
		"& > li, & > completion-section": {
			padding: "1px 3px",
			lineHeight: 1.2
		},
		"& > li": {
			overflowX: "hidden",
			textOverflow: "ellipsis",
			cursor: "pointer"
		},
		"& > completion-section": {
			display: "list-item",
			borderBottom: "1px solid silver",
			paddingLeft: "0.5em",
			opacity: .7
		}
	} },
	"&light .cm-tooltip-autocomplete ul li[aria-selected]": {
		background: "#17c",
		color: "white"
	},
	"&light .cm-tooltip-autocomplete-disabled ul li[aria-selected]": { background: "#777" },
	"&dark .cm-tooltip-autocomplete ul li[aria-selected]": {
		background: "#347",
		color: "white"
	},
	"&dark .cm-tooltip-autocomplete-disabled ul li[aria-selected]": { background: "#444" },
	".cm-completionListIncompleteTop:before, .cm-completionListIncompleteBottom:after": {
		content: "\"···\"",
		opacity: .5,
		display: "block",
		textAlign: "center",
		cursor: "pointer"
	},
	".cm-tooltip.cm-completionInfo": {
		position: "absolute",
		padding: "3px 9px",
		width: "max-content",
		maxWidth: "400px",
		boxSizing: "border-box",
		whiteSpace: "pre-line"
	},
	".cm-completionInfo.cm-completionInfo-left": { right: "100%" },
	".cm-completionInfo.cm-completionInfo-right": { left: "100%" },
	".cm-completionInfo.cm-completionInfo-left-narrow": { right: "30px" },
	".cm-completionInfo.cm-completionInfo-right-narrow": { left: "30px" },
	"&light .cm-snippetField": { backgroundColor: "#00000022" },
	"&dark .cm-snippetField": { backgroundColor: "#ffffff22" },
	".cm-snippetFieldPosition": {
		verticalAlign: "text-top",
		width: 0,
		height: "1.15em",
		display: "inline-block",
		margin: "0 -0.7px -.7em",
		borderLeft: "1.4px dotted #888"
	},
	".cm-completionMatchedText": { textDecoration: "underline" },
	".cm-completionDetail": {
		marginLeft: "0.5em",
		fontStyle: "italic"
	},
	".cm-completionIcon": {
		fontSize: "90%",
		width: ".8em",
		display: "inline-block",
		textAlign: "center",
		paddingRight: ".6em",
		opacity: "0.6",
		boxSizing: "content-box"
	},
	".cm-completionIcon-function, .cm-completionIcon-method": { "&:after": { content: "'ƒ'" } },
	".cm-completionIcon-class": { "&:after": { content: "'○'" } },
	".cm-completionIcon-interface": { "&:after": { content: "'◌'" } },
	".cm-completionIcon-variable": { "&:after": { content: "'𝑥'" } },
	".cm-completionIcon-constant": { "&:after": { content: "'𝐶'" } },
	".cm-completionIcon-type": { "&:after": { content: "'𝑡'" } },
	".cm-completionIcon-enum": { "&:after": { content: "'∪'" } },
	".cm-completionIcon-property": { "&:after": { content: "'□'" } },
	".cm-completionIcon-keyword": { "&:after": { content: "'🔑︎'" } },
	".cm-completionIcon-namespace": { "&:after": { content: "'▢'" } },
	".cm-completionIcon-text": { "&:after": {
		content: "'abc'",
		fontSize: "50%",
		verticalAlign: "middle"
	} }
}), _p = class {
	constructor(e, t, n, r) {
		this.field = e, this.line = t, this.from = n, this.to = r;
	}
}, vp = class e {
	constructor(e, t, n) {
		this.field = e, this.from = t, this.to = n;
	}
	map(t) {
		let n = t.mapPos(this.from, -1, be.TrackDel), r = t.mapPos(this.to, 1, be.TrackDel);
		return n == null || r == null ? null : new e(this.field, n, r);
	}
}, yp = class e {
	constructor(e, t) {
		this.lines = e, this.fieldPositions = t;
	}
	instantiate(e, t) {
		let n = [], r = [t], i = e.doc.lineAt(t), a = /^\s*/.exec(i.text)[0];
		for (let i of this.lines) {
			if (n.length) {
				let n = a, o = /^\t*/.exec(i)[0].length;
				for (let t = 0; t < o; t++) n += e.facet(al);
				r.push(t + n.length - o), i = n + i.slice(o);
			}
			n.push(i), t += i.length + 1;
		}
		return {
			text: n,
			ranges: this.fieldPositions.map((e) => new vp(e.field, r[e.line] + e.from, r[e.line] + e.to))
		};
	}
	static parse(t) {
		let n = [], r = [], i = [], a;
		for (let e of t.split(/\r\n?|\n/)) {
			for (; a = /[#$]\{(?:(\d+)(?::([^{}]*))?|((?:\\[{}]|[^{}])*))\}/.exec(e);) {
				let t = a[1] ? +a[1] : null, o = a[2] || a[3] || "", s = -1;
				t === 0 && (t = 1e9);
				let c = o.replace(/\\[{}]/g, (e) => e[1]);
				for (let e = 0; e < n.length; e++) (t == null ? c && n[e].name == c : n[e].seq == t) && (s = e);
				if (s < 0) {
					let e = 0;
					for (; e < n.length && (t == null || n[e].seq != null && n[e].seq < t);) e++;
					n.splice(e, 0, {
						seq: t,
						name: c
					}), s = e;
					for (let e of i) e.field >= s && e.field++;
				}
				for (let e of i) if (e.line == r.length && e.from > a.index) {
					let t = a[2] ? 3 + (a[1] || "").length : 2;
					e.from -= t, e.to -= t;
				}
				i.push(new _p(s, r.length, a.index, a.index + c.length)), e = e.slice(0, a.index) + o + e.slice(a.index + a[0].length);
			}
			e = e.replace(/\\([{}])/g, (e, t, n) => {
				for (let e of i) e.line == r.length && e.from > n && (e.from--, e.to--);
				return t;
			}), r.push(e);
		}
		return new e(r, i);
	}
}, bp = /*@__PURE__*/ F.widget({ widget: /*@__PURE__*/ new class extends _n {
	toDOM() {
		let e = document.createElement("span");
		return e.className = "cm-snippetFieldPosition", e;
	}
	ignoreEvent() {
		return !1;
	}
}() }), xp = /*@__PURE__*/ F.mark({ class: "cm-snippetField" }), Sp = class e {
	constructor(e, t) {
		this.ranges = e, this.active = t, this.deco = F.set(e.map((e) => (e.from == e.to ? bp : xp).range(e.from, e.to)), !0);
	}
	map(t) {
		let n = [];
		for (let e of this.ranges) {
			let r = e.map(t);
			if (!r) return null;
			n.push(r);
		}
		return new e(n, this.active);
	}
	selectionInsideField(e) {
		return e.ranges.every((e) => this.ranges.some((t) => t.field == this.active && t.from <= e.from && t.to >= e.to));
	}
}, Cp = /*@__PURE__*/ j.define({ map(e, t) {
	return e && e.map(t);
} }), wp = /*@__PURE__*/ j.define(), Tp = /*@__PURE__*/ Le.define({
	create() {
		return null;
	},
	update(e, t) {
		for (let n of t.effects) {
			if (n.is(Cp)) return n.value;
			if (n.is(wp) && e) return new Sp(e.ranges, n.value);
		}
		return e && t.docChanged && (e = e.map(t.changes)), e && t.selection && !e.selectionInsideField(t.selection) && (e = null), e;
	},
	provide: (e) => V.decorations.from(e, (e) => e ? e.deco : F.none)
});
function Ep(e, t) {
	return k.create(e.filter((e) => e.field == t).map((e) => k.range(e.from, e.to)));
}
function Dp(e) {
	let t = yp.parse(e);
	return (e, n, r, i) => {
		let { text: a, ranges: o } = t.instantiate(e.state, r), { main: s } = e.state.selection, c = {
			changes: {
				from: r,
				to: i == s.from ? s.to : i,
				insert: T.of(a)
			},
			scrollIntoView: !0,
			annotations: n ? [Df.of(n), it.userEvent.of("input.complete")] : void 0
		};
		if (o.length && (c.selection = Ep(o, 0)), o.some((e) => e.field > 0)) {
			let t = new Sp(o, 0), n = c.effects = [Cp.of(t)];
			e.state.field(Tp, !1) === void 0 && n.push(j.appendConfig.of([
				Tp,
				jp,
				Np,
				gp
			]));
		}
		e.dispatch(e.state.update(c));
	};
}
function Op(e) {
	return ({ state: t, dispatch: n }) => {
		let r = t.field(Tp, !1);
		if (!r || e < 0 && r.active == 0) return !1;
		let i = r.active + e, a = e > 0 && !r.ranges.some((t) => t.field == i + e);
		return n(t.update({
			selection: Ep(r.ranges, i),
			effects: Cp.of(a ? null : new Sp(r.ranges, i)),
			scrollIntoView: !0
		})), !0;
	};
}
var kp = [{
	key: "Tab",
	run: /* @__PURE__ */ Op(1),
	shift: /* @__PURE__ */ Op(-1)
}, {
	key: "Escape",
	run: ({ state: e, dispatch: t }) => e.field(Tp, !1) ? (t(e.update({ effects: Cp.of(null) })), !0) : !1
}], Ap = /*@__PURE__*/ A.define({ combine(e) {
	return e.length ? e[0] : kp;
} }), jp = /*@__PURE__*/ Be.highest(/*@__PURE__*/ ts.compute([Ap], (e) => e.facet(Ap)));
function Mp(e, t) {
	return {
		...t,
		apply: Dp(e)
	};
}
var Np = /*@__PURE__*/ V.domEventHandlers({ mousedown(e, t) {
	let n = t.state.field(Tp, !1), r;
	if (!n || (r = t.posAtCoords({
		x: e.clientX,
		y: e.clientY
	})) == null) return !1;
	let i = n.ranges.find((e) => e.from <= r && e.to >= r);
	return !i || i.field == n.active ? !1 : (t.dispatch({
		selection: Ep(n.ranges, i.field),
		effects: Cp.of(n.ranges.some((e) => e.field > i.field) ? new Sp(n.ranges, i.field) : null),
		scrollIntoView: !0
	}), !0);
} }), Pp = /*@__PURE__*/ new class extends yt {}();
Pp.startSide = 1, Pp.endSide = -1, typeof navigator == "object" && navigator.userAgent;
function Fp(e = {}) {
	return [
		hp,
		rp,
		X.of(e),
		pp,
		Lp,
		gp
	];
}
var Ip = [
	{
		key: "Ctrl-Space",
		run: cp
	},
	{
		mac: "Alt-`",
		run: cp
	},
	{
		mac: "Alt-i",
		run: cp
	},
	{
		key: "Escape",
		run: lp
	},
	{
		key: "ArrowDown",
		run: /*@__PURE__*/ op(!0)
	},
	{
		key: "ArrowUp",
		run: /*@__PURE__*/ op(!1)
	},
	{
		key: "PageDown",
		run: /*@__PURE__*/ op(!0, "page")
	},
	{
		key: "PageUp",
		run: /*@__PURE__*/ op(!1, "page")
	},
	{
		key: "Enter",
		run: sp
	}
], Lp = /*@__PURE__*/ Be.highest(/*@__PURE__*/ ts.computeN([X], (e) => e.facet(X).defaultKeymap ? [Ip] : [])), Rp = class e {
	static create(t, n, r, i, a) {
		let o = i + (i << 8) + t + (n << 4) | 0;
		return new e(t, n, r, o, a, [], []);
	}
	constructor(e, t, n, r, i, a, o) {
		this.type = e, this.value = t, this.from = n, this.hash = r, this.end = i, this.children = a, this.positions = o, this.hashProp = [[H.contextHash, r]];
	}
	addChild(e, t) {
		e.prop(H.contextHash) != this.hash && (e = new G(e.type, e.children, e.positions, e.length, this.hashProp)), this.children.push(e), this.positions.push(t);
	}
	toTree(e, t = this.end) {
		let n = this.children.length - 1;
		return n >= 0 && (t = Math.max(t, this.positions[n] + this.children[n].length + this.from)), new G(e.types[this.type], this.children, this.positions, t - this.from).balance({ makeTree: (e, t, n) => new G(U.none, e, t, n, this.hashProp) });
	}
}, Z;
(function(e) {
	e[e.Document = 1] = "Document", e[e.CodeBlock = 2] = "CodeBlock", e[e.FencedCode = 3] = "FencedCode", e[e.Blockquote = 4] = "Blockquote", e[e.HorizontalRule = 5] = "HorizontalRule", e[e.BulletList = 6] = "BulletList", e[e.OrderedList = 7] = "OrderedList", e[e.ListItem = 8] = "ListItem", e[e.ATXHeading1 = 9] = "ATXHeading1", e[e.ATXHeading2 = 10] = "ATXHeading2", e[e.ATXHeading3 = 11] = "ATXHeading3", e[e.ATXHeading4 = 12] = "ATXHeading4", e[e.ATXHeading5 = 13] = "ATXHeading5", e[e.ATXHeading6 = 14] = "ATXHeading6", e[e.SetextHeading1 = 15] = "SetextHeading1", e[e.SetextHeading2 = 16] = "SetextHeading2", e[e.HTMLBlock = 17] = "HTMLBlock", e[e.LinkReference = 18] = "LinkReference", e[e.Paragraph = 19] = "Paragraph", e[e.CommentBlock = 20] = "CommentBlock", e[e.ProcessingInstructionBlock = 21] = "ProcessingInstructionBlock", e[e.Escape = 22] = "Escape", e[e.Entity = 23] = "Entity", e[e.HardBreak = 24] = "HardBreak", e[e.Emphasis = 25] = "Emphasis", e[e.StrongEmphasis = 26] = "StrongEmphasis", e[e.Link = 27] = "Link", e[e.Image = 28] = "Image", e[e.InlineCode = 29] = "InlineCode", e[e.HTMLTag = 30] = "HTMLTag", e[e.Comment = 31] = "Comment", e[e.ProcessingInstruction = 32] = "ProcessingInstruction", e[e.Autolink = 33] = "Autolink", e[e.HeaderMark = 34] = "HeaderMark", e[e.QuoteMark = 35] = "QuoteMark", e[e.ListMark = 36] = "ListMark", e[e.LinkMark = 37] = "LinkMark", e[e.EmphasisMark = 38] = "EmphasisMark", e[e.CodeMark = 39] = "CodeMark", e[e.CodeText = 40] = "CodeText", e[e.CodeInfo = 41] = "CodeInfo", e[e.LinkTitle = 42] = "LinkTitle", e[e.LinkLabel = 43] = "LinkLabel", e[e.URL = 44] = "URL";
})(Z ||= {});
var zp = class {
	constructor(e, t) {
		this.start = e, this.content = t, this.marks = [], this.parsers = [];
	}
}, Bp = class {
	constructor() {
		this.text = "", this.baseIndent = 0, this.basePos = 0, this.depth = 0, this.markers = [], this.pos = 0, this.indent = 0, this.next = -1;
	}
	forward() {
		this.basePos > this.pos && this.forwardInner();
	}
	forwardInner() {
		let e = this.skipSpace(this.basePos);
		this.indent = this.countIndent(e, this.pos, this.indent), this.pos = e, this.next = e == this.text.length ? -1 : this.text.charCodeAt(e);
	}
	skipSpace(e) {
		return Wp(this.text, e);
	}
	reset(e) {
		for (this.text = e, this.baseIndent = this.basePos = this.pos = this.indent = 0, this.forwardInner(), this.depth = 1; this.markers.length;) this.markers.pop();
	}
	moveBase(e) {
		this.basePos = e, this.baseIndent = this.countIndent(e, this.pos, this.indent);
	}
	moveBaseColumn(e) {
		this.baseIndent = e, this.basePos = this.findColumn(e);
	}
	addMarker(e) {
		this.markers.push(e);
	}
	countIndent(e, t = 0, n = 0) {
		for (let r = t; r < e; r++) n += this.text.charCodeAt(r) == 9 ? 4 - n % 4 : 1;
		return n;
	}
	findColumn(e) {
		let t = 0;
		for (let n = 0; t < this.text.length && n < e; t++) n += this.text.charCodeAt(t) == 9 ? 4 - n % 4 : 1;
		return t;
	}
	scrub() {
		if (!this.baseIndent) return this.text;
		let e = "";
		for (let t = 0; t < this.basePos; t++) e += " ";
		return e + this.text.slice(this.basePos);
	}
};
function Vp(e, t, n) {
	if (n.pos == n.text.length || e != t.block && n.indent >= t.stack[n.depth + 1].value + n.baseIndent) return !0;
	if (n.indent >= n.baseIndent + 4) return !1;
	let r = (e.type == Z.OrderedList ? Zp : Xp)(n, t, !1);
	return r > 0 && (e.type != Z.BulletList || Jp(n, t, !1) < 0) && n.text.charCodeAt(n.pos + r - 1) == e.value;
}
var Hp = {
	[Z.Blockquote](e, t, n) {
		return n.next == 62 && (n.markers.push(Q(Z.QuoteMark, t.lineStart + n.pos, t.lineStart + n.pos + 1)), n.moveBase(n.pos + (Up(n.text.charCodeAt(n.pos + 1)) ? 2 : 1)), e.end = t.lineStart + n.text.length, !0);
	},
	[Z.ListItem](e, t, n) {
		return n.indent < n.baseIndent + e.value && n.next > -1 ? !1 : (n.moveBaseColumn(n.baseIndent + e.value), !0);
	},
	[Z.OrderedList]: Vp,
	[Z.BulletList]: Vp,
	[Z.Document]() {
		return !0;
	}
};
function Up(e) {
	return e == 32 || e == 9 || e == 10 || e == 13;
}
function Wp(e, t = 0) {
	for (; t < e.length && Up(e.charCodeAt(t));) t++;
	return t;
}
function Gp(e, t, n) {
	for (; t > n && Up(e.charCodeAt(t - 1));) t--;
	return t;
}
function Kp(e) {
	if (e.next != 96 && e.next != 126) return -1;
	let t = e.pos + 1;
	for (; t < e.text.length && e.text.charCodeAt(t) == e.next;) t++;
	if (t < e.pos + 3) return -1;
	if (e.next == 96) {
		for (let n = t; n < e.text.length; n++) if (e.text.charCodeAt(n) == 96) return -1;
	}
	return t;
}
function qp(e) {
	return e.next == 62 ? e.text.charCodeAt(e.pos + 1) == 32 ? 2 : 1 : -1;
}
function Jp(e, t, n) {
	if (e.next != 42 && e.next != 45 && e.next != 95) return -1;
	let r = 1;
	for (let t = e.pos + 1; t < e.text.length; t++) {
		let n = e.text.charCodeAt(t);
		if (n == e.next) r++;
		else if (!Up(n)) return -1;
	}
	return n && e.next == 45 && $p(e) > -1 && e.depth == t.stack.length && t.parser.leafBlockParsers.indexOf(dm.SetextHeading) > -1 || r < 3 ? -1 : 1;
}
function Yp(e, t) {
	for (let n = e.stack.length - 1; n >= 0; n--) if (e.stack[n].type == t) return !0;
	return !1;
}
function Xp(e, t, n) {
	return (e.next == 45 || e.next == 43 || e.next == 42) && (e.pos == e.text.length - 1 || Up(e.text.charCodeAt(e.pos + 1))) && (!n || Yp(t, Z.BulletList) || e.skipSpace(e.pos + 2) < e.text.length) ? 1 : -1;
}
function Zp(e, t, n) {
	let r = e.pos, i = e.next;
	for (; i >= 48 && i <= 57;) {
		if (r++, r == e.text.length) return -1;
		i = e.text.charCodeAt(r);
	}
	return r == e.pos || r > e.pos + 9 || i != 46 && i != 41 || r < e.text.length - 1 && !Up(e.text.charCodeAt(r + 1)) || n && !Yp(t, Z.OrderedList) && (e.skipSpace(r + 1) == e.text.length || r > e.pos + 1 || e.next != 49) ? -1 : r + 1 - e.pos;
}
function Qp(e) {
	if (e.next != 35) return -1;
	let t = e.pos + 1;
	for (; t < e.text.length && e.text.charCodeAt(t) == 35;) t++;
	if (t < e.text.length && e.text.charCodeAt(t) != 32) return -1;
	let n = t - e.pos;
	return n > 6 ? -1 : n;
}
function $p(e) {
	if (e.next != 45 && e.next != 61 || e.indent >= e.baseIndent + 4) return -1;
	let t = e.pos + 1;
	for (; t < e.text.length && e.text.charCodeAt(t) == e.next;) t++;
	let n = t;
	for (; t < e.text.length && Up(e.text.charCodeAt(t));) t++;
	return t == e.text.length ? n : -1;
}
var em = /^[ \t]*$/, tm = /-->/, nm = /\?>/, rm = [
	[/^<(?:script|pre|style)(?:\s|>|$)/i, /<\/(?:script|pre|style)>/i],
	[/^\s*<!--/, tm],
	[/^\s*<\?/, nm],
	[/^\s*<![A-Z]/, />/],
	[/^\s*<!\[CDATA\[/, /\]\]>/],
	[/^\s*<\/?(?:address|article|aside|base|basefont|blockquote|body|caption|center|col|colgroup|dd|details|dialog|dir|div|dl|dt|fieldset|figcaption|figure|footer|form|frame|frameset|h1|h2|h3|h4|h5|h6|head|header|hr|html|iframe|legend|li|link|main|menu|menuitem|nav|noframes|ol|optgroup|option|p|param|section|source|summary|table|tbody|td|tfoot|th|thead|title|tr|track|ul)(?:\s|\/?>|$)/i, em],
	[/^\s*(?:<\/[a-z][\w-]*\s*>|<[a-z][\w-]*(\s+[a-z:_][\w-.]*(?:\s*=\s*(?:[^\s"'=<>`]+|'[^']*'|"[^"]*"))?)*\s*>)\s*$/i, em]
];
function im(e, t, n) {
	if (e.next != 60) return -1;
	let r = e.text.slice(e.pos);
	for (let e = 0, t = rm.length - +!!n; e < t; e++) if (rm[e][0].test(r)) return e;
	return -1;
}
function am(e, t) {
	let n = e.countIndent(t, e.pos, e.indent), r = e.skipSpace(t), i = e.countIndent(r, t, n);
	return i >= n + 5 || r == e.text.length ? n + 1 : i;
}
function om(e, t, n) {
	let r = e.length - 1;
	r >= 0 && e[r].to == t && e[r].type == Z.CodeText ? e[r].to = n : e.push(Q(Z.CodeText, t, n));
}
var sm = {
	LinkReference: void 0,
	IndentedCode(e, t) {
		let n = t.baseIndent + 4;
		if (t.indent < n) return !1;
		let r = t.findColumn(n), i = e.lineStart + r, a = e.lineStart + t.text.length, o = [], s = [];
		for (om(o, i, a); e.nextLine() && t.depth >= e.stack.length;) if (t.pos == t.text.length) {
			om(s, e.lineStart - 1, e.lineStart);
			for (let e of t.markers) s.push(e);
		} else if (t.indent < n) break;
		else {
			if (s.length) {
				for (let e of s) e.type == Z.CodeText ? om(o, e.from, e.to) : o.push(e);
				s = [];
			}
			om(o, e.lineStart - 1, e.lineStart);
			for (let e of t.markers) o.push(e);
			a = e.lineStart + t.text.length;
			let n = e.lineStart + t.findColumn(t.baseIndent + 4);
			n < a && om(o, n, a);
		}
		return s.length && (s = s.filter((e) => e.type != Z.CodeText), s.length && (t.markers = s.concat(t.markers))), e.addNode(e.buffer.writeElements(o, -i).finish(Z.CodeBlock, a - i), i), !0;
	},
	FencedCode(e, t) {
		let n = Kp(t);
		if (n < 0) return !1;
		let r = e.lineStart + t.pos, i = t.next, a = n - t.pos, o = t.skipSpace(n), s = Gp(t.text, t.text.length, o), c = [Q(Z.CodeMark, r, r + a)];
		o < s && c.push(Q(Z.CodeInfo, e.lineStart + o, e.lineStart + s));
		for (let n = !0, r = !0, o = !1; e.nextLine() && t.depth >= e.stack.length; n = !1) {
			let s = t.pos;
			if (t.indent - t.baseIndent < 4) for (; s < t.text.length && t.text.charCodeAt(s) == i;) s++;
			if (s - t.pos >= a && t.skipSpace(s) == t.text.length) {
				for (let e of t.markers) c.push(e);
				r && o && om(c, e.lineStart - 1, e.lineStart), c.push(Q(Z.CodeMark, e.lineStart + t.pos, e.lineStart + s)), e.nextLine();
				break;
			}
			{
				o = !0, n || (om(c, e.lineStart - 1, e.lineStart), r = !1);
				for (let e of t.markers) c.push(e);
				let i = e.lineStart + t.basePos, a = e.lineStart + t.text.length;
				i < a && (om(c, i, a), r = !1);
			}
		}
		return e.addNode(e.buffer.writeElements(c, -r).finish(Z.FencedCode, e.prevLineEnd() - r), r), !0;
	},
	Blockquote(e, t) {
		let n = qp(t);
		return n < 0 ? !1 : (e.startContext(Z.Blockquote, t.pos), e.addNode(Z.QuoteMark, e.lineStart + t.pos, e.lineStart + t.pos + 1), t.moveBase(t.pos + n), null);
	},
	HorizontalRule(e, t) {
		if (Jp(t, e, !1) < 0) return !1;
		let n = e.lineStart + t.pos;
		return e.nextLine(), e.addNode(Z.HorizontalRule, n), !0;
	},
	BulletList(e, t) {
		let n = Xp(t, e, !1);
		if (n < 0) return !1;
		e.block.type != Z.BulletList && e.startContext(Z.BulletList, t.basePos, t.next);
		let r = am(t, t.pos + 1);
		return e.startContext(Z.ListItem, t.basePos, r - t.baseIndent), e.addNode(Z.ListMark, e.lineStart + t.pos, e.lineStart + t.pos + n), t.moveBaseColumn(r), null;
	},
	OrderedList(e, t) {
		let n = Zp(t, e, !1);
		if (n < 0) return !1;
		e.block.type != Z.OrderedList && e.startContext(Z.OrderedList, t.basePos, t.text.charCodeAt(t.pos + n - 1));
		let r = am(t, t.pos + n);
		return e.startContext(Z.ListItem, t.basePos, r - t.baseIndent), e.addNode(Z.ListMark, e.lineStart + t.pos, e.lineStart + t.pos + n), t.moveBaseColumn(r), null;
	},
	ATXHeading(e, t) {
		let n = Qp(t);
		if (n < 0) return !1;
		let r = t.pos, i = e.lineStart + r, a = Gp(t.text, t.text.length, r), o = a;
		for (; o > r && t.text.charCodeAt(o - 1) == t.next;) o--;
		(o == a || o == r || !Up(t.text.charCodeAt(o - 1))) && (o = t.text.length);
		let s = e.buffer.write(Z.HeaderMark, 0, n).writeElements(e.parser.parseInline(t.text.slice(r + n + 1, o), i + n + 1), -i);
		o < t.text.length && s.write(Z.HeaderMark, o - r, a - r);
		let c = s.finish(Z.ATXHeading1 - 1 + n, t.text.length - r);
		return e.nextLine(), e.addNode(c, i), !0;
	},
	HTMLBlock(e, t) {
		let n = im(t, e, !1);
		if (n < 0) return !1;
		let r = e.lineStart + t.pos, i = rm[n][1], a = [], o = i != em;
		for (; !i.test(t.text) && e.nextLine();) {
			if (t.depth < e.stack.length) {
				o = !1;
				break;
			}
			for (let e of t.markers) a.push(e);
		}
		o && e.nextLine();
		let s = i == tm ? Z.CommentBlock : i == nm ? Z.ProcessingInstructionBlock : Z.HTMLBlock, c = e.prevLineEnd();
		return e.addNode(e.buffer.writeElements(a, -r).finish(s, c - r), r), !0;
	},
	SetextHeading: void 0
}, cm = class {
	constructor(e) {
		this.stage = 0, this.elts = [], this.pos = 0, this.start = e.start, this.advance(e.content);
	}
	nextLine(e, t, n) {
		if (this.stage == -1) return !1;
		let r = n.content + "\n" + t.scrub(), i = this.advance(r);
		return i > -1 && i < r.length && this.complete(e, n, i);
	}
	finish(e, t) {
		return (this.stage == 2 || this.stage == 3) && Wp(t.content, this.pos) == t.content.length && this.complete(e, t, t.content.length);
	}
	complete(e, t, n) {
		return e.addLeafElement(t, Q(Z.LinkReference, this.start, this.start + n, this.elts)), !0;
	}
	nextStage(e) {
		return e ? (this.pos = e.to - this.start, this.elts.push(e), this.stage++, !0) : (e === !1 && (this.stage = -1), !1);
	}
	advance(e) {
		for (;;) if (this.stage == -1) return -1;
		else if (this.stage == 0) {
			if (!this.nextStage(Im(e, this.pos, this.start, !0))) return -1;
			if (e.charCodeAt(this.pos) != 58) return this.stage = -1;
			this.elts.push(Q(Z.LinkMark, this.pos + this.start, this.pos + this.start + 1)), this.pos++;
		} else if (this.stage == 1) {
			if (!this.nextStage(Pm(e, Wp(e, this.pos), this.start))) return -1;
		} else if (this.stage == 2) {
			let t = Wp(e, this.pos), n = 0;
			if (t > this.pos) {
				let r = Fm(e, t, this.start);
				if (r) {
					let t = lm(e, r.to - this.start);
					t > 0 && (this.nextStage(r), n = t);
				}
			}
			return n ||= lm(e, this.pos), n > 0 && n < e.length ? n : -1;
		} else return lm(e, this.pos);
	}
};
function lm(e, t) {
	for (; t < e.length; t++) {
		let n = e.charCodeAt(t);
		if (n == 10) break;
		if (!Up(n)) return -1;
	}
	return t;
}
var um = class {
	nextLine(e, t, n) {
		let r = t.depth < e.stack.length ? -1 : $p(t), i = t.next;
		if (r < 0) return !1;
		let a = Q(Z.HeaderMark, e.lineStart + t.pos, e.lineStart + r);
		return e.nextLine(), e.addLeafElement(n, Q(i == 61 ? Z.SetextHeading1 : Z.SetextHeading2, n.start, e.prevLineEnd(), [...e.parser.parseInline(n.content, n.start), a])), !0;
	}
	finish() {
		return !1;
	}
}, dm = {
	LinkReference(e, t) {
		return t.content.charCodeAt(0) == 91 ? new cm(t) : null;
	},
	SetextHeading() {
		return new um();
	}
}, fm = [
	(e, t) => Qp(t) >= 0,
	(e, t) => Kp(t) >= 0,
	(e, t) => qp(t) >= 0,
	(e, t) => Xp(t, e, !0) >= 0,
	(e, t) => Zp(t, e, !0) >= 0,
	(e, t) => Jp(t, e, !0) >= 0,
	(e, t) => im(t, e, !0) >= 0
], pm = {
	text: "",
	end: 0
}, mm = class {
	constructor(e, t, n, r) {
		this.parser = e, this.input = t, this.ranges = r, this.line = new Bp(), this.atEnd = !1, this.reusePlaceholders = /* @__PURE__ */ new Map(), this.stoppedAt = null, this.rangeI = 0, this.to = r[r.length - 1].to, this.lineStart = this.absoluteLineStart = this.absoluteLineEnd = r[0].from, this.block = Rp.create(Z.Document, 0, this.lineStart, 0, 0), this.stack = [this.block], this.fragments = n.length ? new Bm(n, t) : null, this.readLine();
	}
	get parsedPos() {
		return this.absoluteLineStart;
	}
	advance() {
		if (this.stoppedAt != null && this.absoluteLineStart > this.stoppedAt) return this.finish();
		let { line: e } = this;
		for (;;) {
			for (let t = 0;;) {
				let n = e.depth < this.stack.length ? this.stack[this.stack.length - 1] : null;
				for (; t < e.markers.length && (!n || e.markers[t].from < n.end);) {
					let n = e.markers[t++];
					this.addNode(n.type, n.from, n.to);
				}
				if (!n) break;
				this.finishContext();
			}
			if (e.pos < e.text.length) break;
			if (!this.nextLine()) return this.finish();
		}
		if (this.fragments && this.reuseFragment(e.basePos)) return null;
		start: for (;;) {
			for (let t of this.parser.blockParsers) if (t) {
				let n = t(this, e);
				if (n != 0) {
					if (n == 1) return null;
					e.forward();
					continue start;
				}
			}
			break;
		}
		if (e.pos == e.text.length) return this.nextLine() ? null : this.finish();
		let t = new zp(this.lineStart + e.pos, e.text.slice(e.pos));
		for (let e of this.parser.leafBlockParsers) if (e) {
			let n = e(this, t);
			n && t.parsers.push(n);
		}
		lines: for (; this.nextLine() && e.pos != e.text.length;) {
			if (e.indent < e.baseIndent + 4) {
				for (let n of this.parser.endLeafBlock) if (n(this, e, t)) break lines;
			}
			for (let n of t.parsers) if (n.nextLine(this, e, t)) return null;
			t.content += "\n" + e.scrub();
			for (let n of e.markers) t.marks.push(n);
		}
		return this.finishLeaf(t), null;
	}
	stopAt(e) {
		if (this.stoppedAt != null && this.stoppedAt < e) throw RangeError("Can't move stoppedAt forward");
		this.stoppedAt = e;
	}
	reuseFragment(e) {
		if (!this.fragments.moveTo(this.absoluteLineStart + e, this.absoluteLineStart) || !this.fragments.matches(this.block.hash)) return !1;
		let t = this.fragments.takeNodes(this);
		return t ? (this.absoluteLineStart += t, this.lineStart = Vm(this.absoluteLineStart, this.ranges), this.moveRangeI(), this.absoluteLineStart < this.to ? (this.lineStart++, this.absoluteLineStart++, this.readLine()) : (this.atEnd = !0, this.readLine()), !0) : !1;
	}
	get depth() {
		return this.stack.length;
	}
	parentType(e = this.depth - 1) {
		return this.parser.nodeSet.types[this.stack[e].type];
	}
	nextLine() {
		return this.lineStart += this.line.text.length, this.absoluteLineEnd >= this.to ? (this.absoluteLineStart = this.absoluteLineEnd, this.atEnd = !0, this.readLine(), !1) : (this.lineStart++, this.absoluteLineStart = this.absoluteLineEnd + 1, this.moveRangeI(), this.readLine(), !0);
	}
	peekLine() {
		return this.scanLine(this.absoluteLineEnd + 1).text;
	}
	moveRangeI() {
		for (; this.rangeI < this.ranges.length - 1 && this.absoluteLineStart >= this.ranges[this.rangeI].to;) this.rangeI++, this.absoluteLineStart = Math.max(this.absoluteLineStart, this.ranges[this.rangeI].from);
	}
	scanLine(e) {
		let t = pm;
		if (t.end = e, e >= this.to) t.text = "";
		else if (t.text = this.lineChunkAt(e), t.end += t.text.length, this.ranges.length > 1) {
			let e = this.absoluteLineStart, n = this.rangeI;
			for (; this.ranges[n].to < t.end;) {
				n++;
				let r = this.ranges[n].from, i = this.lineChunkAt(r);
				t.end = r + i.length, t.text = t.text.slice(0, this.ranges[n - 1].to - e) + i, e = t.end - t.text.length;
			}
		}
		return t;
	}
	readLine() {
		let { line: e } = this, { text: t, end: n } = this.scanLine(this.absoluteLineStart);
		for (this.absoluteLineEnd = n, e.reset(t); e.depth < this.stack.length; e.depth++) {
			let t = this.stack[e.depth], n = this.parser.skipContextMarkup[t.type];
			if (!n) throw Error("Unhandled block context " + Z[t.type]);
			let r = this.line.markers.length;
			if (!n(t, this, e)) {
				this.line.markers.length > r && (t.end = this.line.markers[this.line.markers.length - 1].to), e.forward();
				break;
			}
			e.forward();
		}
	}
	lineChunkAt(e) {
		let t = this.input.chunk(e), n;
		if (this.input.lineChunks) n = t == "\n" ? "" : t;
		else {
			let e = t.indexOf("\n");
			n = e < 0 ? t : t.slice(0, e);
		}
		return e + n.length > this.to ? n.slice(0, this.to - e) : n;
	}
	prevLineEnd() {
		return this.atEnd ? this.lineStart : this.lineStart - 1;
	}
	startContext(e, t, n = 0) {
		this.block = Rp.create(e, n, this.lineStart + t, this.block.hash, this.lineStart + this.line.text.length), this.stack.push(this.block);
	}
	startComposite(e, t, n = 0) {
		this.startContext(this.parser.getNodeType(e), t, n);
	}
	addNode(e, t, n) {
		typeof e == "number" && (e = new G(this.parser.nodeSet.types[e], xm, xm, (n ?? this.prevLineEnd()) - t)), this.block.addChild(e, t - this.block.from);
	}
	addElement(e) {
		this.block.addChild(e.toTree(this.parser.nodeSet), e.from - this.block.from);
	}
	addLeafElement(e, t) {
		this.addNode(this.buffer.writeElements(Rm(t.children, e.marks), -t.from).finish(t.type, t.to - t.from), t.from);
	}
	finishContext() {
		let e = this.stack.pop(), t = this.stack[this.stack.length - 1];
		t.addChild(e.toTree(this.parser.nodeSet), e.from - t.from), this.block = t;
	}
	finish() {
		for (; this.stack.length > 1;) this.finishContext();
		return this.addGaps(this.block.toTree(this.parser.nodeSet, this.lineStart));
	}
	addGaps(e) {
		return this.ranges.length > 1 ? hm(this.ranges, 0, e.topNode, this.ranges[0].from, this.reusePlaceholders) : e;
	}
	finishLeaf(e) {
		for (let t of e.parsers) if (t.finish(this, e)) return;
		let t = Rm(this.parser.parseInline(e.content, e.start), e.marks);
		this.addNode(this.buffer.writeElements(t, -e.start).finish(Z.Paragraph, e.content.length), e.start);
	}
	elt(e, t, n, r) {
		return typeof e == "string" ? Q(this.parser.getNodeType(e), t, n, r) : new wm(e, t);
	}
	get buffer() {
		return new Sm(this.parser.nodeSet);
	}
};
function hm(e, t, n, r, i) {
	let a = e[t].to, o = [], s = [], c = n.from + r;
	function l(n, i) {
		for (; i ? n >= a : n > a;) {
			let i = e[t + 1].from - a;
			r += i, n += i, t++, a = e[t].to;
		}
	}
	for (let u = n.firstChild; u; u = u.nextSibling) {
		l(u.from + r, !0);
		let n = u.from + r, d, f = i.get(u.tree);
		f ? d = f : u.to + r > a ? (d = hm(e, t, u, r, i), l(u.to + r, !1)) : d = u.toTree(), o.push(d), s.push(n - c);
	}
	return l(n.to + r, !1), new G(n.type, o, s, n.to + r - c, n.tree ? n.tree.propValues : void 0);
}
var gm = class e extends Xs {
	constructor(e, t, n, r, i, a, o, s, c) {
		super(), this.nodeSet = e, this.blockParsers = t, this.leafBlockParsers = n, this.blockNames = r, this.endLeafBlock = i, this.skipContextMarkup = a, this.inlineParsers = o, this.inlineNames = s, this.wrappers = c, this.nodeTypes = Object.create(null);
		for (let t of e.types) this.nodeTypes[t.name] = t.id;
	}
	createParse(e, t, n) {
		let r = new mm(this, e, t, n);
		for (let i of this.wrappers) r = i(r, e, t, n);
		return r;
	}
	configure(t) {
		let n = vm(t);
		if (!n) return this;
		let { nodeSet: r, skipContextMarkup: i } = this, a = this.blockParsers.slice(), o = this.leafBlockParsers.slice(), s = this.blockNames.slice(), c = this.inlineParsers.slice(), l = this.inlineNames.slice(), u = this.endLeafBlock.slice(), d = this.wrappers;
		if (_m(n.defineNodes)) {
			i = Object.assign({}, i);
			let e = r.types.slice(), t;
			for (let r of n.defineNodes) {
				let { name: n, block: a, composite: o, style: s } = typeof r == "string" ? { name: r } : r;
				if (e.some((e) => e.name == n)) continue;
				o && (i[e.length] = (e, t, n) => o(t, n, e.value));
				let c = e.length, l = o ? ["Block", "BlockContext"] : a ? c >= Z.ATXHeading1 && c <= Z.SetextHeading2 ? [
					"Block",
					"LeafBlock",
					"Heading"
				] : ["Block", "LeafBlock"] : void 0;
				e.push(U.define({
					id: c,
					name: n,
					props: l && [[H.group, l]]
				})), s && (t ||= {}, Array.isArray(s) || s instanceof pc ? t[n] = s : Object.assign(t, s));
			}
			r = new Es(e), t && (r = r.extend(vc(t)));
		}
		if (_m(n.props) && (r = r.extend(...n.props)), _m(n.remove)) for (let e of n.remove) {
			let t = this.blockNames.indexOf(e), n = this.inlineNames.indexOf(e);
			t > -1 && (a[t] = o[t] = void 0), n > -1 && (c[n] = void 0);
		}
		if (_m(n.parseBlock)) for (let e of n.parseBlock) {
			let t = s.indexOf(e.name);
			if (t > -1) a[t] = e.parse, o[t] = e.leaf;
			else {
				let t = e.before ? ym(s, e.before) : e.after ? ym(s, e.after) + 1 : s.length - 1;
				a.splice(t, 0, e.parse), o.splice(t, 0, e.leaf), s.splice(t, 0, e.name);
			}
			e.endLeaf && u.push(e.endLeaf);
		}
		if (_m(n.parseInline)) for (let e of n.parseInline) {
			let t = l.indexOf(e.name);
			if (t > -1) c[t] = e.parse;
			else {
				let t = e.before ? ym(l, e.before) : e.after ? ym(l, e.after) + 1 : l.length - 1;
				c.splice(t, 0, e.parse), l.splice(t, 0, e.name);
			}
		}
		return n.wrap && (d = d.concat(n.wrap)), new e(r, a, o, s, u, i, c, l, d);
	}
	getNodeType(e) {
		let t = this.nodeTypes[e];
		if (t == null) throw RangeError(`Unknown node type '${e}'`);
		return t;
	}
	parseInline(e, t) {
		let n = new Lm(this, e, t);
		outer: for (let e = t; e < n.end;) {
			let t = n.char(e);
			for (let r of this.inlineParsers) if (r) {
				let i = r(n, t, e);
				if (i >= 0) {
					e = i;
					continue outer;
				}
			}
			e++;
		}
		return n.resolveMarkers(0);
	}
};
function _m(e) {
	return e != null && e.length > 0;
}
function vm(e) {
	if (!Array.isArray(e)) return e;
	if (e.length == 0) return null;
	let t = vm(e[0]);
	if (e.length == 1) return t;
	let n = vm(e.slice(1));
	if (!n || !t) return t || n;
	let r = (e, t) => (e || xm).concat(t || xm), i = t.wrap, a = n.wrap;
	return {
		props: r(t.props, n.props),
		defineNodes: r(t.defineNodes, n.defineNodes),
		parseBlock: r(t.parseBlock, n.parseBlock),
		parseInline: r(t.parseInline, n.parseInline),
		remove: r(t.remove, n.remove),
		wrap: i ? a ? (e, t, n, r) => i(a(e, t, n, r), t, n, r) : i : a
	};
}
function ym(e, t) {
	let n = e.indexOf(t);
	if (n < 0) throw RangeError(`Position specified relative to unknown parser ${t}`);
	return n;
}
var bm = [U.none];
for (let e = 1, t; t = Z[e]; e++) bm[e] = U.define({
	id: e,
	name: t,
	props: e >= Z.Escape ? [] : [[H.group, e in Hp ? ["Block", "BlockContext"] : ["Block", "LeafBlock"]]],
	top: t == "Document"
});
var xm = [], Sm = class {
	constructor(e) {
		this.nodeSet = e, this.content = [], this.nodes = [];
	}
	write(e, t, n, r = 0) {
		return this.content.push(e, t, n, 4 + r * 4), this;
	}
	writeElements(e, t = 0) {
		for (let n of e) n.writeTo(this, t);
		return this;
	}
	finish(e, t) {
		return G.build({
			buffer: this.content,
			nodeSet: this.nodeSet,
			reused: this.nodes,
			topID: e,
			length: t
		});
	}
}, Cm = class {
	constructor(e, t, n, r = xm) {
		this.type = e, this.from = t, this.to = n, this.children = r;
	}
	writeTo(e, t) {
		let n = e.content.length;
		e.writeElements(this.children, t), e.content.push(this.type, this.from + t, this.to + t, e.content.length + 4 - n);
	}
	toTree(e) {
		return new Sm(e).writeElements(this.children, -this.from).finish(this.type, this.to - this.from);
	}
}, wm = class {
	constructor(e, t) {
		this.tree = e, this.from = t;
	}
	get to() {
		return this.from + this.tree.length;
	}
	get type() {
		return this.tree.type.id;
	}
	get children() {
		return xm;
	}
	writeTo(e, t) {
		e.nodes.push(this.tree), e.content.push(e.nodes.length - 1, this.from + t, this.to + t, -1);
	}
	toTree() {
		return this.tree;
	}
};
function Q(e, t, n, r) {
	return new Cm(e, t, n, r);
}
var Tm = {
	resolve: "Emphasis",
	mark: "EmphasisMark"
}, Em = {
	resolve: "Emphasis",
	mark: "EmphasisMark"
}, Dm = {}, Om = {}, km = class {
	constructor(e, t, n, r) {
		this.type = e, this.from = t, this.to = n, this.side = r;
	}
}, Am = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~", jm = /[!"#$%&'()*+,\-.\/:;<=>?@\[\\\]^_`{|}~\xA1\u2010-\u2027]/;
try {
	jm = /* @__PURE__ */ RegExp("[\\p{S}|\\p{P}]", "u");
} catch {}
var Mm = {
	Escape(e, t, n) {
		if (t != 92 || n == e.end - 1) return -1;
		let r = e.char(n + 1);
		for (let t = 0; t < 32; t++) if (Am.charCodeAt(t) == r) return e.append(Q(Z.Escape, n, n + 2));
		return -1;
	},
	Entity(e, t, n) {
		if (t != 38) return -1;
		let r = /^(?:#\d+|#x[a-f\d]+|\w+);/i.exec(e.slice(n + 1, n + 31));
		return r ? e.append(Q(Z.Entity, n, n + 1 + r[0].length)) : -1;
	},
	InlineCode(e, t, n) {
		if (t != 96 || n && e.char(n - 1) == 96) return -1;
		let r = n + 1;
		for (; r < e.end && e.char(r) == 96;) r++;
		let i = r - n, a = 0;
		for (; r < e.end; r++) if (e.char(r) == 96) {
			if (a++, a == i && e.char(r + 1) != 96) return e.append(Q(Z.InlineCode, n, r + 1, [Q(Z.CodeMark, n, n + i), Q(Z.CodeMark, r + 1 - i, r + 1)]));
		} else a = 0;
		return -1;
	},
	HTMLTag(e, t, n) {
		if (t != 60 || n == e.end - 1) return -1;
		let r = e.slice(n + 1, e.end), i = /^(?:[a-z][-\w+.]+:[^\s>]+|[a-z\d.!#$%&'*+/=?^_`{|}~-]+@[a-z\d](?:[a-z\d-]{0,61}[a-z\d])?(?:\.[a-z\d](?:[a-z\d-]{0,61}[a-z\d])?)*)>/i.exec(r);
		if (i) return e.append(Q(Z.Autolink, n, n + 1 + i[0].length, [
			Q(Z.LinkMark, n, n + 1),
			Q(Z.URL, n + 1, n + i[0].length),
			Q(Z.LinkMark, n + i[0].length, n + 1 + i[0].length)
		]));
		let a = /^!--[^>](?:-[^-]|[^-])*?-->/i.exec(r);
		if (a) return e.append(Q(Z.Comment, n, n + 1 + a[0].length));
		let o = /^\?[^]*?\?>/.exec(r);
		if (o) return e.append(Q(Z.ProcessingInstruction, n, n + 1 + o[0].length));
		let s = /^(?:![A-Z][^]*?>|!\[CDATA\[[^]*?\]\]>|\/\s*[a-zA-Z][\w-]*\s*>|\s*[a-zA-Z][\w-]*(\s+[a-zA-Z:_][\w-.:]*(?:\s*=\s*(?:[^\s"'=<>`]+|'[^']*'|"[^"]*"))?)*\s*(\/\s*)?>)/.exec(r);
		return s ? e.append(Q(Z.HTMLTag, n, n + 1 + s[0].length)) : -1;
	},
	Emphasis(e, t, n) {
		if (t != 95 && t != 42) return -1;
		let r = n + 1;
		for (; e.char(r) == t;) r++;
		let i = e.slice(n - 1, n), a = e.slice(r, r + 1), o = jm.test(i), s = jm.test(a), c = /\s|^$/.test(i), l = /\s|^$/.test(a), u = !l && (!s || c || o), d = !c && (!o || l || s), f = u && (t == 42 || !d || o), p = d && (t == 42 || !u || s);
		return e.append(new km(t == 95 ? Tm : Em, n, r, !!f | (p ? 2 : 0)));
	},
	HardBreak(e, t, n) {
		if (t == 92 && e.char(n + 1) == 10) return e.append(Q(Z.HardBreak, n, n + 2));
		if (t == 32) {
			let t = n + 1;
			for (; e.char(t) == 32;) t++;
			if (e.char(t) == 10 && t >= n + 2) return e.append(Q(Z.HardBreak, n, t + 1));
		}
		return -1;
	},
	Link(e, t, n) {
		return t == 91 ? e.append(new km(Dm, n, n + 1, 1)) : -1;
	},
	Image(e, t, n) {
		return t == 33 && e.char(n + 1) == 91 ? e.append(new km(Om, n, n + 2, 1)) : -1;
	},
	LinkEnd(e, t, n) {
		if (t != 93) return -1;
		for (let t = e.parts.length - 1; t >= 0; t--) {
			let r = e.parts[t];
			if (r instanceof km && (r.type == Dm || r.type == Om)) {
				if (!r.side || e.skipSpace(r.to) == n && !/[(\[]/.test(e.slice(n + 1, n + 2))) return e.parts[t] = null, -1;
				let i = e.takeContent(t), a = e.parts[t] = Nm(e, i, r.type == Dm ? Z.Link : Z.Image, r.from, n + 1);
				if (r.type == Dm) for (let n = 0; n < t; n++) {
					let t = e.parts[n];
					t instanceof km && t.type == Dm && (t.side = 0);
				}
				return a.to;
			}
		}
		return -1;
	}
};
function Nm(e, t, n, r, i) {
	let { text: a } = e, o = e.char(i), s = i;
	if (t.unshift(Q(Z.LinkMark, r, r + (n == Z.Image ? 2 : 1))), t.push(Q(Z.LinkMark, i - 1, i)), o == 40) {
		let n = e.skipSpace(i + 1), r = Pm(a, n - e.offset, e.offset), o;
		r && (n = e.skipSpace(r.to), n != r.to && (o = Fm(a, n - e.offset, e.offset), o && (n = e.skipSpace(o.to)))), e.char(n) == 41 && (t.push(Q(Z.LinkMark, i, i + 1)), s = n + 1, r && t.push(r), o && t.push(o), t.push(Q(Z.LinkMark, n, s)));
	} else if (o == 91) {
		let n = Im(a, i - e.offset, e.offset, !1);
		n && (t.push(n), s = n.to);
	}
	return Q(n, r, s, t);
}
function Pm(e, t, n) {
	if (e.charCodeAt(t) == 60) {
		for (let r = t + 1; r < e.length; r++) {
			let i = e.charCodeAt(r);
			if (i == 62) return Q(Z.URL, t + n, r + 1 + n);
			if (i == 60 || i == 10) return !1;
		}
		return null;
	}
	{
		let r = 0, i = t;
		for (let t = !1; i < e.length; i++) {
			let n = e.charCodeAt(i);
			if (Up(n)) break;
			if (t) t = !1;
			else if (n == 40) r++;
			else if (n == 41) {
				if (!r) break;
				r--;
			} else n == 92 && (t = !0);
		}
		return i > t ? Q(Z.URL, t + n, i + n) : i == e.length && null;
	}
}
function Fm(e, t, n) {
	let r = e.charCodeAt(t);
	if (r != 39 && r != 34 && r != 40) return !1;
	let i = r == 40 ? 41 : r;
	for (let r = t + 1, a = !1; r < e.length; r++) {
		let o = e.charCodeAt(r);
		if (a) a = !1;
		else if (o == i) return Q(Z.LinkTitle, t + n, r + 1 + n);
		else o == 92 && (a = !0);
	}
	return null;
}
function Im(e, t, n, r) {
	for (let i = !1, a = t + 1, o = Math.min(e.length, a + 999); a < o; a++) {
		let o = e.charCodeAt(a);
		if (i) i = !1;
		else if (o == 93) return !r && Q(Z.LinkLabel, t + n, a + 1 + n);
		else {
			if (r && !Up(o) && (r = !1), o == 91) return !1;
			o == 92 && (i = !0);
		}
	}
	return null;
}
var Lm = class {
	constructor(e, t, n) {
		this.parser = e, this.text = t, this.offset = n, this.parts = [];
	}
	char(e) {
		return e >= this.end ? -1 : this.text.charCodeAt(e - this.offset);
	}
	get end() {
		return this.offset + this.text.length;
	}
	slice(e, t) {
		return this.text.slice(e - this.offset, t - this.offset);
	}
	append(e) {
		return this.parts.push(e), e.to;
	}
	addDelimiter(e, t, n, r, i) {
		return this.append(new km(e, t, n, !!r | (i ? 2 : 0)));
	}
	get hasOpenLink() {
		for (let e = this.parts.length - 1; e >= 0; e--) {
			let t = this.parts[e];
			if (t instanceof km && (t.type == Dm || t.type == Om)) return !0;
		}
		return !1;
	}
	addElement(e) {
		return this.append(e);
	}
	resolveMarkers(e) {
		for (let t = e; t < this.parts.length; t++) {
			let n = this.parts[t];
			if (!(n instanceof km && n.type.resolve && n.side & 2)) continue;
			let r = n.type == Tm || n.type == Em, i = n.to - n.from, a, o = t - 1;
			for (; o >= e; o--) {
				let e = this.parts[o];
				if (e instanceof km && e.side & 1 && e.type == n.type && !(r && (n.side & 1 || e.side & 2) && (e.to - e.from + i) % 3 == 0 && ((e.to - e.from) % 3 || i % 3))) {
					a = e;
					break;
				}
			}
			if (!a) continue;
			let s = n.type.resolve, c = [], l = a.from, u = n.to;
			if (r) {
				let e = Math.min(2, a.to - a.from, i);
				l = a.to - e, u = n.from + e, s = e == 1 ? "Emphasis" : "StrongEmphasis";
			}
			a.type.mark && c.push(this.elt(a.type.mark, l, a.to));
			for (let e = o + 1; e < t; e++) this.parts[e] instanceof Cm && c.push(this.parts[e]), this.parts[e] = null;
			n.type.mark && c.push(this.elt(n.type.mark, n.from, u));
			let d = this.elt(s, l, u, c);
			this.parts[o] = r && a.from != l ? new km(a.type, a.from, l, a.side) : null, (this.parts[t] = r && n.to != u ? new km(n.type, u, n.to, n.side) : null) ? this.parts.splice(t, 0, d) : this.parts[t] = d;
		}
		let t = [];
		for (let n = e; n < this.parts.length; n++) {
			let e = this.parts[n];
			e instanceof Cm && t.push(e);
		}
		return t;
	}
	findOpeningDelimiter(e) {
		for (let t = this.parts.length - 1; t >= 0; t--) {
			let n = this.parts[t];
			if (n instanceof km && n.type == e && n.side & 1) return t;
		}
		return null;
	}
	takeContent(e) {
		let t = this.resolveMarkers(e);
		return this.parts.length = e, t;
	}
	getDelimiterAt(e) {
		let t = this.parts[e];
		return t instanceof km ? t : null;
	}
	skipSpace(e) {
		return Wp(this.text, e - this.offset) + this.offset;
	}
	elt(e, t, n, r) {
		return typeof e == "string" ? Q(this.parser.getNodeType(e), t, n, r) : new wm(e, t);
	}
};
Lm.linkStart = Dm, Lm.imageStart = Om;
function Rm(e, t) {
	if (!t.length) return e;
	if (!e.length) return t;
	let n = e.slice(), r = 0;
	for (let e of t) {
		for (; r < n.length && n[r].to < e.to;) r++;
		if (r < n.length && n[r].from < e.from) {
			let t = n[r];
			t instanceof Cm && (n[r] = new Cm(t.type, t.from, t.to, Rm(t.children, [e])));
		} else n.splice(r++, 0, e);
	}
	return n;
}
var zm = [
	Z.CodeBlock,
	Z.ListItem,
	Z.OrderedList,
	Z.BulletList
], Bm = class {
	constructor(e, t) {
		this.fragments = e, this.input = t, this.i = 0, this.fragment = null, this.fragmentEnd = -1, this.cursor = null, e.length && (this.fragment = e[this.i++]);
	}
	nextFragment() {
		this.fragment = this.i < this.fragments.length ? this.fragments[this.i++] : null, this.cursor = null, this.fragmentEnd = -1;
	}
	moveTo(e, t) {
		for (; this.fragment && this.fragment.to <= e;) this.nextFragment();
		if (!this.fragment || this.fragment.from > (e ? e - 1 : 0)) return !1;
		if (this.fragmentEnd < 0) {
			let e = this.fragment.to;
			for (; e > 0 && this.input.read(e - 1, e) != "\n";) e--;
			this.fragmentEnd = e ? e - 1 : 0;
		}
		let n = this.cursor;
		n || (n = this.cursor = this.fragment.tree.cursor(), n.firstChild());
		let r = e + this.fragment.offset;
		for (; n.to <= r;) if (!n.parent()) return !1;
		for (;;) {
			if (n.from >= r) return this.fragment.from <= t;
			if (!n.childAfter(r)) return !1;
		}
	}
	matches(e) {
		let t = this.cursor.tree;
		return t && t.prop(H.contextHash) == e;
	}
	takeNodes(e) {
		let t = this.cursor, n = this.fragment.offset, r = this.fragmentEnd - +!!this.fragment.openEnd, i = e.absoluteLineStart, a = i, o = e.block.children.length, s = a, c = o;
		for (;;) {
			if (t.to - n > r) {
				if (t.type.isAnonymous && t.firstChild()) continue;
				break;
			}
			let i = Vm(t.from - n, e.ranges);
			if (t.to - n <= e.ranges[e.rangeI].to) e.addNode(t.tree, i);
			else {
				let n = new G(e.parser.nodeSet.types[Z.Paragraph], [], [], 0, e.block.hashProp);
				e.reusePlaceholders.set(n, t.tree), e.addNode(n, i);
			}
			if (t.type.is("Block") && (zm.indexOf(t.type.id) < 0 ? (a = t.to - n, o = e.block.children.length) : (a = s, o = c), s = t.to - n, c = e.block.children.length), !t.nextSibling()) break;
		}
		for (; e.block.children.length > o;) e.block.children.pop(), e.block.positions.pop();
		return a - i;
	}
};
function Vm(e, t) {
	let n = e;
	for (let r = 1; r < t.length; r++) {
		let i = t[r - 1].to, a = t[r].from;
		i < e && (n -= a - i);
	}
	return n;
}
var Hm = vc({
	"Blockquote/...": q.quote,
	HorizontalRule: q.contentSeparator,
	"ATXHeading1/... SetextHeading1/...": q.heading1,
	"ATXHeading2/... SetextHeading2/...": q.heading2,
	"ATXHeading3/...": q.heading3,
	"ATXHeading4/...": q.heading4,
	"ATXHeading5/...": q.heading5,
	"ATXHeading6/...": q.heading6,
	"Comment CommentBlock": q.comment,
	Escape: q.escape,
	Entity: q.character,
	"Emphasis/...": q.emphasis,
	"StrongEmphasis/...": q.strong,
	"Link/... Image/...": q.link,
	"OrderedList/... BulletList/...": q.list,
	"BlockQuote/...": q.quote,
	"InlineCode CodeText": q.monospace,
	"URL Autolink": q.url,
	"HeaderMark HardBreak QuoteMark ListMark LinkMark EmphasisMark CodeMark": q.processingInstruction,
	"CodeInfo LinkLabel": q.labelName,
	LinkTitle: q.string,
	Paragraph: q.content
}), Um = new gm(new Es(bm).extend(Hm), Object.keys(sm).map((e) => sm[e]), Object.keys(sm).map((e) => dm[e]), Object.keys(sm), fm, Hp, Object.keys(Mm).map((e) => Mm[e]), Object.keys(Mm), []);
function Wm(e, t, n) {
	let r = [];
	for (let i = e.firstChild, a = t;; i = i.nextSibling) {
		let e = i ? i.from : n;
		if (e > a && r.push({
			from: a,
			to: e
		}), !i) break;
		a = i.to;
	}
	return r;
}
function Gm(e) {
	let { codeParser: t, htmlParser: n } = e;
	return { wrap: Qs((e, r) => {
		let i = e.type.id;
		if (t && (i == Z.CodeBlock || i == Z.FencedCode)) {
			let n = "";
			if (i == Z.FencedCode) {
				let t = e.node.getChild(Z.CodeInfo);
				t && (n = r.read(t.from, t.to));
			}
			let a = t(n);
			if (a) return {
				parser: a,
				overlay: (e) => e.type.id == Z.CodeText,
				bracketed: i == Z.FencedCode
			};
		} else if (n && (i == Z.HTMLBlock || i == Z.HTMLTag || i == Z.CommentBlock)) return {
			parser: n,
			overlay: Wm(e.node, e.from, e.to)
		};
		return null;
	}) };
}
var Km = {
	resolve: "Strikethrough",
	mark: "StrikethroughMark"
}, qm = {
	defineNodes: [{
		name: "Strikethrough",
		style: { "Strikethrough/...": q.strikethrough }
	}, {
		name: "StrikethroughMark",
		style: q.processingInstruction
	}],
	parseInline: [{
		name: "Strikethrough",
		parse(e, t, n) {
			if (t != 126 || e.char(n + 1) != 126 || e.char(n + 2) == 126) return -1;
			let r = e.slice(n - 1, n), i = e.slice(n + 2, n + 3), a = /\s|^$/.test(r), o = /\s|^$/.test(i), s = jm.test(r), c = jm.test(i);
			return e.addDelimiter(Km, n, n + 2, !o && (!c || a || s), !a && (!s || o || c));
		},
		after: "Emphasis"
	}]
};
function Jm(e, t, n = 0, r, i = 0) {
	let a = 0, o = !0, s = -1, c = -1, l = !1, u = () => {
		r.push(e.elt("TableCell", i + s, i + c, e.parser.parseInline(t.slice(s, c), i + s)));
	};
	for (let d = n; d < t.length; d++) {
		let n = t.charCodeAt(d);
		n == 124 && !l ? ((!o || s > -1) && a++, o = !1, r && (s > -1 && u(), r.push(e.elt("TableDelimiter", d + i, d + i + 1))), s = c = -1) : (l || n != 32 && n != 9) && (s < 0 && (s = d), c = d + 1), l = !l && n == 92;
	}
	return s > -1 && (a++, r && u()), a;
}
function Ym(e, t) {
	for (let n = t; n < e.length; n++) {
		let t = e.charCodeAt(n);
		if (t == 124) return !0;
		t == 92 && n++;
	}
	return !1;
}
var Xm = /^[>\s]*\|?(\s*:?-+:?\s*\|)+(\s*:?-+:?\s*)?$/, Zm = class {
	constructor() {
		this.rows = null;
	}
	nextLine(e, t, n) {
		if (this.rows == null) {
			this.rows = !1;
			let r;
			if ((t.next == 45 || t.next == 58 || t.next == 124) && Xm.test(r = t.text.slice(t.pos))) {
				let i = [];
				Jm(e, n.content, 0, i, n.start) == Jm(e, r, 0) && (this.rows = [e.elt("TableHeader", n.start, n.start + n.content.length, i), e.elt("TableDelimiter", e.lineStart + t.pos, e.lineStart + t.text.length)]);
			}
		} else if (this.rows) {
			let n = [];
			Jm(e, t.text, t.pos, n, e.lineStart), this.rows.push(e.elt("TableRow", e.lineStart + t.pos, e.lineStart + t.text.length, n));
		}
		return !1;
	}
	finish(e, t) {
		return this.rows ? (e.addLeafElement(t, e.elt("Table", t.start, t.start + t.content.length, this.rows)), !0) : !1;
	}
}, Qm = {
	defineNodes: [
		{
			name: "Table",
			block: !0
		},
		{
			name: "TableHeader",
			style: { "TableHeader/...": q.heading }
		},
		"TableRow",
		{
			name: "TableCell",
			style: q.content
		},
		{
			name: "TableDelimiter",
			style: q.processingInstruction
		}
	],
	parseBlock: [{
		name: "Table",
		leaf(e, t) {
			return Ym(t.content, 0) ? new Zm() : null;
		},
		endLeaf(e, t, n) {
			if (n.parsers.some((e) => e instanceof Zm) || !Ym(t.text, t.basePos)) return !1;
			let r = e.peekLine();
			return Xm.test(r) && Jm(e, t.text, t.basePos) == Jm(e, r, t.basePos);
		},
		before: "SetextHeading"
	}]
}, $m = class {
	nextLine() {
		return !1;
	}
	finish(e, t) {
		return e.addLeafElement(t, e.elt("Task", t.start, t.start + t.content.length, [e.elt("TaskMarker", t.start, t.start + 3), ...e.parser.parseInline(t.content.slice(3), t.start + 3)])), !0;
	}
}, eh = {
	defineNodes: [{
		name: "Task",
		block: !0,
		style: q.list
	}, {
		name: "TaskMarker",
		style: q.atom
	}],
	parseBlock: [{
		name: "TaskList",
		leaf(e, t) {
			return /^\[[ xX]\][ \t]/.test(t.content) && e.parentType().name == "ListItem" ? new $m() : null;
		},
		after: "SetextHeading"
	}]
}, th = /(www\.)|(https?:\/\/)|([\w.+-]{1,100}@)|(mailto:|xmpp:)/gy, nh = /[\w-]+(\.[\w-]+)+(:\d+)?(\/[^\s<]*)?/gy, rh = /[\w-]+\.[\w-]+($|[/:])/, ih = /[\w.+-]+@[\w-]+(\.[\w.-]+)+/gy, ah = /\/[a-zA-Z\d@.]+/gy;
function oh(e, t, n, r) {
	let i = 0;
	for (let a = t; a < n; a++) e[a] == r && i++;
	return i;
}
function sh(e, t) {
	nh.lastIndex = t;
	let n = nh.exec(e);
	if (!n || rh.exec(n[0])[0].indexOf("_") > -1) return -1;
	let r = t + n[0].length;
	for (;;) {
		let n = e[r - 1], i;
		if (/[?!.,:*_~]/.test(n) || n == ")" && oh(e, t, r, ")") > oh(e, t, r, "(")) r--;
		else if (n == ";" && (i = /&(?:#\d+|#x[a-f\d]+|\w+);$/.exec(e.slice(t, r)))) r = t + i.index;
		else break;
	}
	return r;
}
function ch(e, t) {
	ih.lastIndex = t;
	let n = ih.exec(e);
	if (!n) return -1;
	let r = n[0][n[0].length - 1];
	return r == "_" || r == "-" ? -1 : t + n[0].length - +(r == ".");
}
var lh = [
	Qm,
	eh,
	qm,
	{ parseInline: [{
		name: "Autolink",
		parse(e, t, n) {
			let r = n - e.offset;
			if (r && /\w/.test(e.text[r - 1])) return -1;
			th.lastIndex = r;
			let i = th.exec(e.text), a = -1;
			return !i || (i[1] || i[2] ? (a = sh(e.text, r + i[0].length), a > -1 && e.hasOpenLink && (a = r + /([^\[\]]|\[[^\]]*\])*/.exec(e.text.slice(r, a))[0].length)) : i[3] ? a = ch(e.text, r) : (a = ch(e.text, r + i[0].length), a > -1 && i[0] == "xmpp:" && (ah.lastIndex = a, i = ah.exec(e.text), i && (a = i.index + i[0].length))), a < 0) ? -1 : (e.addElement(e.elt("URL", n, a + e.offset)), a + e.offset);
		}
	}] }
];
function uh(e, t, n) {
	return (r, i, a) => {
		if (i != e || r.char(a + 1) == e) return -1;
		let o = [r.elt(n, a, a + 1)];
		for (let i = a + 1; i < r.end; i++) {
			let s = r.char(i);
			if (s == e) return r.addElement(r.elt(t, a, i + 1, o.concat(r.elt(n, i, i + 1))));
			if (s == 92 && o.push(r.elt("Escape", i, i++ + 2)), Up(s)) break;
		}
		return -1;
	};
}
var dh = {
	defineNodes: [{
		name: "Superscript",
		style: q.special(q.content)
	}, {
		name: "SuperscriptMark",
		style: q.processingInstruction
	}],
	parseInline: [{
		name: "Superscript",
		parse: uh(94, "Superscript", "SuperscriptMark")
	}]
}, fh = {
	defineNodes: [{
		name: "Subscript",
		style: q.special(q.content)
	}, {
		name: "SubscriptMark",
		style: q.processingInstruction
	}],
	parseInline: [{
		name: "Subscript",
		parse: uh(126, "Subscript", "SubscriptMark")
	}]
}, ph = {
	defineNodes: [{
		name: "Emoji",
		style: q.character
	}],
	parseInline: [{
		name: "Emoji",
		parse(e, t, n) {
			let r;
			return t != 58 || !(r = /^[a-zA-Z_0-9]+:/.exec(e.slice(n + 1, e.end))) ? -1 : e.addElement(e.elt("Emoji", n, n + 1 + r[0].length));
		}
	}]
}, mh = class e {
	constructor(e, t, n, r, i, a, o, s, c, l = 0, u) {
		this.p = e, this.stack = t, this.state = n, this.reducePos = r, this.pos = i, this.score = a, this.buffer = o, this.bufferBase = s, this.curContext = c, this.lookAhead = l, this.parent = u;
	}
	toString() {
		return `[${this.stack.filter((e, t) => t % 3 == 0).concat(this.state)}]@${this.pos}${this.score ? "!" + this.score : ""}`;
	}
	static start(t, n, r = 0) {
		let i = t.parser.context;
		return new e(t, [], n, r, r, 0, [], 0, i ? new hh(i, i.start) : null, 0, null);
	}
	get context() {
		return this.curContext ? this.curContext.context : null;
	}
	pushState(e, t) {
		this.stack.push(this.state, t, this.bufferBase + this.buffer.length), this.state = e;
	}
	reduce(e) {
		let t = e >> 19, n = e & 65535, { parser: r } = this.p, i = this.reducePos < this.pos - 25 && this.setLookAhead(this.pos), a = r.dynamicPrecedence(n);
		if (a && (this.score += a), t == 0) {
			n < r.minRepeatTerm && this.reducePos < this.pos && (this.reducePos = this.pos), this.pushState(r.getGoto(this.state, n, !0), this.reducePos), n < r.minRepeatTerm && this.storeNode(n, this.reducePos, this.reducePos, i ? 8 : 4, !0), this.reduceContext(n, this.reducePos);
			return;
		}
		let o = this.stack.length - (t - 1) * 3 - (e & 262144 ? 6 : 0), s = o ? this.stack[o - 2] : this.p.ranges[0].from;
		n < r.minRepeatTerm && s == this.reducePos && this.reducePos < this.pos && (this.reducePos = this.pos);
		let c = this.reducePos - s;
		c >= 2e3 && !this.p.parser.nodeSet.types[n]?.isAnonymous && (s == this.p.lastBigReductionStart ? (this.p.bigReductionCount++, this.p.lastBigReductionSize = c) : this.p.lastBigReductionSize < c && (this.p.bigReductionCount = 1, this.p.lastBigReductionStart = s, this.p.lastBigReductionSize = c));
		let l = o ? this.stack[o - 1] : 0, u = this.bufferBase + this.buffer.length - l;
		if (n < r.minRepeatTerm || e & 131072) {
			let e = r.stateFlag(this.state, 1) ? this.pos : this.reducePos;
			this.storeNode(n, s, e, u + 4, !0);
		}
		if (e & 262144) this.state = this.stack[o];
		else {
			let e = this.stack[o - 3];
			this.state = r.getGoto(e, n, !0);
		}
		for (; this.stack.length > o;) this.stack.pop();
		this.reduceContext(n, s);
	}
	storeNode(e, t, n, r = 4, i = !1) {
		if (e == 0 && (!this.stack.length || this.stack[this.stack.length - 1] < this.buffer.length + this.bufferBase)) {
			let e = this.buffer.length;
			if (e > 0 && this.buffer[e - 4] == 0 && this.buffer[e - 1] > -1) {
				if (t == n) return;
				if (this.buffer[e - 2] >= t) {
					this.buffer[e - 2] = n;
					return;
				}
			}
		}
		if (!i || this.pos == n) this.buffer.push(e, t, n, r);
		else {
			let i = this.buffer.length;
			if (i > 0 && (this.buffer[i - 4] != 0 || this.buffer[i - 1] < 0)) {
				let e = !1;
				for (let t = i; t > 0 && this.buffer[t - 2] > n; t -= 4) if (this.buffer[t - 1] >= 0) {
					e = !0;
					break;
				}
				if (e) for (; i > 0 && this.buffer[i - 2] > n;) this.buffer[i] = this.buffer[i - 4], this.buffer[i + 1] = this.buffer[i - 3], this.buffer[i + 2] = this.buffer[i - 2], this.buffer[i + 3] = this.buffer[i - 1], i -= 4, r > 4 && (r -= 4);
			}
			this.buffer[i] = e, this.buffer[i + 1] = t, this.buffer[i + 2] = n, this.buffer[i + 3] = r;
		}
	}
	shift(e, t, n, r) {
		if (e & 131072) this.pushState(e & 65535, this.pos);
		else if (e & 262144) this.pos = r, this.shiftContext(t, n), t <= this.p.parser.maxNode && this.buffer.push(t, n, r, 4);
		else {
			let i = e, { parser: a } = this.p;
			this.pos = r;
			let o = a.stateFlag(i, 1);
			!o && (r > n || t <= a.maxNode) && (this.reducePos = r), this.pushState(i, o ? n : Math.min(n, this.reducePos)), this.shiftContext(t, n), t <= a.maxNode && this.buffer.push(t, n, r, 4);
		}
	}
	apply(e, t, n, r) {
		e & 65536 ? this.reduce(e) : this.shift(e, t, n, r);
	}
	useNode(e, t) {
		let n = this.p.reused.length - 1;
		(n < 0 || this.p.reused[n] != e) && (this.p.reused.push(e), n++);
		let r = this.pos;
		this.reducePos = this.pos = r + e.length, this.pushState(t, r), this.buffer.push(n, r, this.reducePos, -1), this.curContext && this.updateContext(this.curContext.tracker.reuse(this.curContext.context, e, this, this.p.stream.reset(this.pos - e.length)));
	}
	split() {
		let t = this, n = t.buffer.length;
		for (n && t.buffer[n - 4] == 0 && (n -= 4); n > 0 && t.buffer[n - 2] > t.reducePos;) n -= 4;
		let r = t.buffer.slice(n), i = t.bufferBase + n;
		for (; t && i == t.bufferBase;) t = t.parent;
		return new e(this.p, this.stack.slice(), this.state, this.reducePos, this.pos, this.score, r, i, this.curContext, this.lookAhead, t);
	}
	recoverByDelete(e, t) {
		let n = e <= this.p.parser.maxNode;
		n && this.storeNode(e, this.pos, t, 4), this.storeNode(0, this.pos, t, n ? 8 : 4), this.pos = this.reducePos = t, this.score -= 190;
	}
	canShift(e) {
		for (let t = new gh(this);;) {
			let n = this.p.parser.stateSlot(t.state, 4) || this.p.parser.hasAction(t.state, e);
			if (n == 0) return !1;
			if (!(n & 65536)) return !0;
			t.reduce(n);
		}
	}
	recoverByInsert(e) {
		if (this.stack.length >= 300) return [];
		let t = this.p.parser.nextStates(this.state);
		if (t.length > 8 || this.stack.length >= 120) {
			let n = [];
			for (let r = 0, i; r < t.length; r += 2) (i = t[r + 1]) != this.state && this.p.parser.hasAction(i, e) && n.push(t[r], i);
			if (this.stack.length < 120) for (let e = 0; n.length < 8 && e < t.length; e += 2) {
				let r = t[e + 1];
				n.some((e, t) => t & 1 && e == r) || n.push(t[e], r);
			}
			t = n;
		}
		let n = [];
		for (let e = 0; e < t.length && n.length < 4; e += 2) {
			let r = t[e + 1];
			if (r == this.state) continue;
			let i = this.split();
			i.pushState(r, this.pos), i.storeNode(0, i.pos, i.pos, 4, !0), i.shiftContext(t[e], this.pos), i.reducePos = this.pos, i.score -= 200, n.push(i);
		}
		return n;
	}
	forceReduce() {
		let { parser: e } = this.p, t = e.stateSlot(this.state, 5);
		if (!(t & 65536)) return !1;
		if (!e.validAction(this.state, t)) {
			let n = t >> 19, r = t & 65535, i = this.stack.length - n * 3;
			if (i < 0 || e.getGoto(this.stack[i], r, !1) < 0) {
				let e = this.findForcedReduction();
				if (e == null) return !1;
				t = e;
			}
			this.storeNode(0, this.pos, this.pos, 4, !0), this.score -= 100;
		}
		return this.reducePos = this.pos, this.reduce(t), !0;
	}
	findForcedReduction() {
		let { parser: e } = this.p, t = [], n = (r, i) => {
			if (!t.includes(r)) return t.push(r), e.allActions(r, (t) => {
				if (!(t & 393216)) {
					if (t & 65536) {
						let n = (t >> 19) - i;
						if (n > 1) {
							let r = t & 65535, i = this.stack.length - n * 3;
							if (i >= 0 && e.getGoto(this.stack[i], r, !1) >= 0) return n << 19 | 65536 | r;
						}
					} else {
						let e = n(t, i + 1);
						if (e != null) return e;
					}
				}
			});
		};
		return n(this.state, 0);
	}
	forceAll() {
		for (; !this.p.parser.stateFlag(this.state, 2);) if (!this.forceReduce()) {
			this.storeNode(0, this.pos, this.pos, 4, !0);
			break;
		}
		return this;
	}
	get deadEnd() {
		if (this.stack.length != 3) return !1;
		let { parser: e } = this.p;
		return e.data[e.stateSlot(this.state, 1)] == 65535 && !e.stateSlot(this.state, 4);
	}
	restart() {
		this.storeNode(0, this.pos, this.pos, 4, !0), this.state = this.stack[0], this.stack.length = 0;
	}
	sameState(e) {
		if (this.state != e.state || this.stack.length != e.stack.length) return !1;
		for (let t = 0; t < this.stack.length; t += 3) if (this.stack[t] != e.stack[t]) return !1;
		return !0;
	}
	get parser() {
		return this.p.parser;
	}
	dialectEnabled(e) {
		return this.p.parser.dialect.flags[e];
	}
	shiftContext(e, t) {
		this.curContext && this.updateContext(this.curContext.tracker.shift(this.curContext.context, e, this, this.p.stream.reset(t)));
	}
	reduceContext(e, t) {
		this.curContext && this.updateContext(this.curContext.tracker.reduce(this.curContext.context, e, this, this.p.stream.reset(t)));
	}
	emitContext() {
		let e = this.buffer.length - 1;
		(e < 0 || this.buffer[e] != -3) && this.buffer.push(this.curContext.hash, this.pos, this.pos, -3);
	}
	emitLookAhead() {
		let e = this.buffer.length - 1;
		(e < 0 || this.buffer[e] != -4) && this.buffer.push(this.lookAhead, this.pos, this.pos, -4);
	}
	updateContext(e) {
		if (e != this.curContext.context) {
			let t = new hh(this.curContext.tracker, e);
			t.hash != this.curContext.hash && this.emitContext(), this.curContext = t;
		}
	}
	setLookAhead(e) {
		return e <= this.lookAhead ? !1 : (this.emitLookAhead(), this.lookAhead = e, !0);
	}
	close() {
		this.curContext && this.curContext.tracker.strict && this.emitContext(), this.lookAhead > 0 && this.emitLookAhead();
	}
}, hh = class {
	constructor(e, t) {
		this.tracker = e, this.context = t, this.hash = e.strict ? e.hash(t) : 0;
	}
}, gh = class {
	constructor(e) {
		this.start = e, this.state = e.state, this.stack = e.stack, this.base = this.stack.length;
	}
	reduce(e) {
		let t = e & 65535, n = e >> 19;
		n == 0 ? (this.stack == this.start.stack && (this.stack = this.stack.slice()), this.stack.push(this.state, 0, 0), this.base += 3) : this.base -= (n - 1) * 3;
		let r = this.start.p.parser.getGoto(this.stack[this.base - 3], t, !0);
		this.state = r;
	}
}, _h = class e {
	constructor(e, t, n) {
		this.stack = e, this.pos = t, this.index = n, this.buffer = e.buffer, this.index == 0 && this.maybeNext();
	}
	static create(t, n = t.bufferBase + t.buffer.length) {
		return new e(t, n, n - t.bufferBase);
	}
	maybeNext() {
		let e = this.stack.parent;
		e != null && (this.index = this.stack.bufferBase - e.bufferBase, this.stack = e, this.buffer = e.buffer);
	}
	get id() {
		return this.buffer[this.index - 4];
	}
	get start() {
		return this.buffer[this.index - 3];
	}
	get end() {
		return this.buffer[this.index - 2];
	}
	get size() {
		return this.buffer[this.index - 1];
	}
	next() {
		this.index -= 4, this.pos -= 4, this.index == 0 && this.maybeNext();
	}
	fork() {
		return new e(this.stack, this.pos, this.index);
	}
};
function vh(e, t = Uint16Array) {
	if (typeof e != "string") return e;
	let n = null;
	for (let r = 0, i = 0; r < e.length;) {
		let a = 0;
		for (;;) {
			let t = e.charCodeAt(r++), n = !1;
			if (t == 126) {
				a = 65535;
				break;
			}
			t >= 92 && t--, t >= 34 && t--;
			let i = t - 32;
			if (i >= 46 && (i -= 46, n = !0), a += i, n) break;
			a *= 46;
		}
		n ? n[i++] = a : n = new t(a);
	}
	return n;
}
var yh = class {
	constructor() {
		this.start = -1, this.value = -1, this.end = -1, this.extended = -1, this.lookAhead = 0, this.mask = 0, this.context = 0;
	}
}, bh = new yh(), xh = class {
	constructor(e, t) {
		this.input = e, this.ranges = t, this.chunk = "", this.chunkOff = 0, this.chunk2 = "", this.chunk2Pos = 0, this.next = -1, this.token = bh, this.rangeIndex = 0, this.pos = this.chunkPos = t[0].from, this.range = t[0], this.end = t[t.length - 1].to, this.readNext();
	}
	resolveOffset(e, t) {
		let n = this.range, r = this.rangeIndex, i = this.pos + e;
		for (; i < n.from;) {
			if (!r) return null;
			let e = this.ranges[--r];
			i -= n.from - e.to, n = e;
		}
		for (; t < 0 ? i > n.to : i >= n.to;) {
			if (r == this.ranges.length - 1) return null;
			let e = this.ranges[++r];
			i += e.from - n.to, n = e;
		}
		return i;
	}
	clipPos(e) {
		if (e >= this.range.from && e < this.range.to) return e;
		for (let t of this.ranges) if (t.to > e) return Math.max(e, t.from);
		return this.end;
	}
	peek(e) {
		let t = this.chunkOff + e, n, r;
		if (t >= 0 && t < this.chunk.length) n = this.pos + e, r = this.chunk.charCodeAt(t);
		else {
			let t = this.resolveOffset(e, 1);
			if (t == null) return -1;
			if (n = t, n >= this.chunk2Pos && n < this.chunk2Pos + this.chunk2.length) r = this.chunk2.charCodeAt(n - this.chunk2Pos);
			else {
				let e = this.rangeIndex, t = this.range;
				for (; t.to <= n;) t = this.ranges[++e];
				this.chunk2 = this.input.chunk(this.chunk2Pos = n), n + this.chunk2.length > t.to && (this.chunk2 = this.chunk2.slice(0, t.to - n)), r = this.chunk2.charCodeAt(0);
			}
		}
		return n >= this.token.lookAhead && (this.token.lookAhead = n + 1), r;
	}
	acceptToken(e, t = 0) {
		let n = t ? this.resolveOffset(t, -1) : this.pos;
		if (n == null || n < this.token.start) throw RangeError("Token end out of bounds");
		this.token.value = e, this.token.end = n;
	}
	acceptTokenTo(e, t) {
		this.token.value = e, this.token.end = t;
	}
	getChunk() {
		if (this.pos >= this.chunk2Pos && this.pos < this.chunk2Pos + this.chunk2.length) {
			let { chunk: e, chunkPos: t } = this;
			this.chunk = this.chunk2, this.chunkPos = this.chunk2Pos, this.chunk2 = e, this.chunk2Pos = t, this.chunkOff = this.pos - this.chunkPos;
		} else {
			this.chunk2 = this.chunk, this.chunk2Pos = this.chunkPos;
			let e = this.input.chunk(this.pos), t = this.pos + e.length;
			this.chunk = t > this.range.to ? e.slice(0, this.range.to - this.pos) : e, this.chunkPos = this.pos, this.chunkOff = 0;
		}
	}
	readNext() {
		return this.next = this.chunkOff >= this.chunk.length && (this.getChunk(), this.chunkOff == this.chunk.length) ? -1 : this.chunk.charCodeAt(this.chunkOff);
	}
	advance(e = 1) {
		for (this.chunkOff += e; this.pos + e >= this.range.to;) {
			if (this.rangeIndex == this.ranges.length - 1) return this.setDone();
			e -= this.range.to - this.pos, this.range = this.ranges[++this.rangeIndex], this.pos = this.range.from;
		}
		return this.pos += e, this.pos >= this.token.lookAhead && (this.token.lookAhead = this.pos + 1), this.readNext();
	}
	setDone() {
		return this.pos = this.chunkPos = this.end, this.range = this.ranges[this.rangeIndex = this.ranges.length - 1], this.chunk = "", this.next = -1;
	}
	reset(e, t) {
		if (t ? (this.token = t, t.start = e, t.lookAhead = e + 1, t.value = t.extended = -1) : this.token = bh, this.pos != e) {
			if (this.pos = e, e == this.end) return this.setDone(), this;
			for (; e < this.range.from;) this.range = this.ranges[--this.rangeIndex];
			for (; e >= this.range.to;) this.range = this.ranges[++this.rangeIndex];
			e >= this.chunkPos && e < this.chunkPos + this.chunk.length ? this.chunkOff = e - this.chunkPos : (this.chunk = "", this.chunkOff = 0), this.readNext();
		}
		return this;
	}
	read(e, t) {
		if (e >= this.chunkPos && t <= this.chunkPos + this.chunk.length) return this.chunk.slice(e - this.chunkPos, t - this.chunkPos);
		if (e >= this.chunk2Pos && t <= this.chunk2Pos + this.chunk2.length) return this.chunk2.slice(e - this.chunk2Pos, t - this.chunk2Pos);
		if (e >= this.range.from && t <= this.range.to) return this.input.read(e, t);
		let n = "";
		for (let r of this.ranges) {
			if (r.from >= t) break;
			r.to > e && (n += this.input.read(Math.max(r.from, e), Math.min(r.to, t)));
		}
		return n;
	}
}, Sh = class {
	constructor(e, t) {
		this.data = e, this.id = t;
	}
	token(e, t) {
		let { parser: n } = t.p;
		Th(this.data, e, t, this.id, n.data, n.tokenPrecTable);
	}
};
Sh.prototype.contextual = Sh.prototype.fallback = Sh.prototype.extend = !1;
var Ch = class {
	constructor(e, t, n) {
		this.precTable = t, this.elseToken = n, this.data = typeof e == "string" ? vh(e) : e;
	}
	token(e, t) {
		let n = e.pos, r = 0;
		for (;;) {
			let n = e.next < 0, i = e.resolveOffset(1, 1);
			if (Th(this.data, e, t, 0, this.data, this.precTable), e.token.value > -1) break;
			if (this.elseToken == null) return;
			if (n || r++, i == null) break;
			e.reset(i, e.token);
		}
		r && (e.reset(n, e.token), e.acceptToken(this.elseToken, r));
	}
};
Ch.prototype.contextual = Sh.prototype.fallback = Sh.prototype.extend = !1;
var wh = class {
	constructor(e, t = {}) {
		this.token = e, this.contextual = !!t.contextual, this.fallback = !!t.fallback, this.extend = !!t.extend;
	}
};
function Th(e, t, n, r, i, a) {
	let o = 0, s = 1 << r, { dialect: c } = n.p.parser;
	scan: for (; (s & e[o]) != 0;) {
		let n = e[o + 1];
		for (let r = o + 3; r < n; r += 2) if ((e[r + 1] & s) > 0) {
			let n = e[r];
			if (c.allows(n) && (t.token.value == -1 || t.token.value == n || Dh(n, t.token.value, i, a))) {
				t.acceptToken(n);
				break;
			}
		}
		let r = t.next, l = 0, u = e[o + 2];
		if (t.next < 0 && u > l && e[n + u * 3 - 3] == 65535) {
			o = e[n + u * 3 - 1];
			continue scan;
		}
		for (; l < u;) {
			let i = l + u >> 1, a = n + i + (i << 1), s = e[a], c = e[a + 1] || 65536;
			if (r < s) u = i;
			else if (r >= c) l = i + 1;
			else {
				o = e[a + 2], t.advance();
				continue scan;
			}
		}
		break;
	}
}
function Eh(e, t, n) {
	for (let r = t, i; (i = e[r]) != 65535; r++) if (i == n) return r - t;
	return -1;
}
function Dh(e, t, n, r) {
	let i = Eh(n, r, t);
	return i < 0 || Eh(n, r, e) < i;
}
var Oh = typeof process < "u" && process.env && /\bparse\b/.test(process.env.LOG), kh = null;
function Ah(e, t, n) {
	let r = e.cursor(W.IncludeAnonymous);
	for (r.moveTo(t);;) if (!(n < 0 ? r.childBefore(t) : r.childAfter(t))) for (;;) {
		if ((n < 0 ? r.to < t : r.from > t) && !r.type.isError) return n < 0 ? Math.max(0, Math.min(r.to - 1, t - 25)) : Math.min(e.length, Math.max(r.from + 1, t + 25));
		if (n < 0 ? r.prevSibling() : r.nextSibling()) break;
		if (!r.parent()) return n < 0 ? 0 : e.length;
	}
}
var jh = class {
	constructor(e, t) {
		this.fragments = e, this.nodeSet = t, this.i = 0, this.fragment = null, this.safeFrom = -1, this.safeTo = -1, this.trees = [], this.start = [], this.index = [], this.nextFragment();
	}
	nextFragment() {
		let e = this.fragment = this.i == this.fragments.length ? null : this.fragments[this.i++];
		if (e) {
			for (this.safeFrom = e.openStart ? Ah(e.tree, e.from + e.offset, 1) - e.offset : e.from, this.safeTo = e.openEnd ? Ah(e.tree, e.to + e.offset, -1) - e.offset : e.to; this.trees.length;) this.trees.pop(), this.start.pop(), this.index.pop();
			this.trees.push(e.tree), this.start.push(-e.offset), this.index.push(0), this.nextStart = this.safeFrom;
		} else this.nextStart = 1e9;
	}
	nodeAt(e) {
		if (e < this.nextStart) return null;
		for (; this.fragment && this.safeTo <= e;) this.nextFragment();
		if (!this.fragment) return null;
		for (;;) {
			let t = this.trees.length - 1;
			if (t < 0) return this.nextFragment(), null;
			let n = this.trees[t], r = this.index[t];
			if (r == n.children.length) {
				this.trees.pop(), this.start.pop(), this.index.pop();
				continue;
			}
			let i = n.children[r], a = this.start[t] + n.positions[r];
			if (a > e) return this.nextStart = a, null;
			if (i instanceof G) {
				if (a == e) {
					if (a < this.safeFrom) return null;
					let e = a + i.length;
					if (e <= this.safeTo) {
						let t = i.prop(H.lookAhead);
						if (!t || e + t < this.fragment.to) return i;
					}
				}
				this.index[t]++, a + i.length >= Math.max(this.safeFrom, e) && (this.trees.push(i), this.start.push(a), this.index.push(0));
			} else this.index[t]++, this.nextStart = a + i.length;
		}
	}
}, Mh = class {
	constructor(e, t) {
		this.stream = t, this.tokens = [], this.mainToken = null, this.actions = [], this.tokens = e.tokenizers.map((e) => new yh());
	}
	getActions(e) {
		let t = 0, n = null, { parser: r } = e.p, { tokenizers: i } = r, a = r.stateSlot(e.state, 3), o = e.curContext ? e.curContext.hash : 0, s = 0;
		for (let r = 0; r < i.length; r++) {
			if (!(1 << r & a)) continue;
			let c = i[r], l = this.tokens[r];
			if ((!n || c.fallback) && ((c.contextual || l.start != e.pos || l.mask != a || l.context != o) && (this.updateCachedToken(l, c, e), l.mask = a, l.context = o), l.lookAhead > l.end + 25 && (s = Math.max(l.lookAhead, s)), l.value != 0)) {
				let r = t;
				if (l.extended > -1 && (t = this.addActions(e, l.extended, l.end, t)), t = this.addActions(e, l.value, l.end, t), !c.extend && (n = l, t > r)) break;
			}
		}
		for (; this.actions.length > t;) this.actions.pop();
		return s && e.setLookAhead(s), !n && e.pos == this.stream.end && (n = new yh(), n.value = e.p.parser.eofTerm, n.start = n.end = e.pos, t = this.addActions(e, n.value, n.end, t)), this.mainToken = n, this.actions;
	}
	getMainToken(e) {
		if (this.mainToken) return this.mainToken;
		let t = new yh(), { pos: n, p: r } = e;
		return t.start = n, t.end = Math.min(n + 1, r.stream.end), t.value = n == r.stream.end ? r.parser.eofTerm : 0, t;
	}
	updateCachedToken(e, t, n) {
		let r = this.stream.clipPos(n.pos);
		if (t.token(this.stream.reset(r, e), n), e.value > -1) {
			let { parser: t } = n.p;
			for (let r = 0; r < t.specialized.length; r++) if (t.specialized[r] == e.value) {
				let i = t.specializers[r](this.stream.read(e.start, e.end), n);
				if (i >= 0 && n.p.parser.dialect.allows(i >> 1)) {
					i & 1 ? e.extended = i >> 1 : e.value = i >> 1;
					break;
				}
			}
		} else e.value = 0, e.end = this.stream.clipPos(r + 1);
	}
	putAction(e, t, n, r) {
		for (let t = 0; t < r; t += 3) if (this.actions[t] == e) return r;
		return this.actions[r++] = e, this.actions[r++] = t, this.actions[r++] = n, r;
	}
	addActions(e, t, n, r) {
		let { state: i } = e, { parser: a } = e.p, { data: o } = a;
		for (let e = 0; e < 2; e++) for (let s = a.stateSlot(i, e ? 2 : 1);; s += 3) {
			if (o[s] == 65535) {
				if (o[s + 1] == 1) s = zh(o, s + 2);
				else {
					r == 0 && o[s + 1] == 2 && (r = this.putAction(zh(o, s + 2), t, n, r));
					break;
				}
			}
			o[s] == t && (r = this.putAction(zh(o, s + 1), t, n, r));
		}
		return r;
	}
}, Nh = class {
	constructor(e, t, n, r) {
		this.parser = e, this.input = t, this.ranges = r, this.recovering = 0, this.nextStackID = 9812, this.minStackPos = 0, this.reused = [], this.stoppedAt = null, this.lastBigReductionStart = -1, this.lastBigReductionSize = 0, this.bigReductionCount = 0, this.stream = new xh(t, r), this.tokens = new Mh(e, this.stream), this.topTerm = e.top[1];
		let { from: i } = r[0];
		this.stacks = [mh.start(this, e.top[0], i)], this.fragments = n.length && this.stream.end - i > e.bufferLength * 4 ? new jh(n, e.nodeSet) : null;
	}
	get parsedPos() {
		return this.minStackPos;
	}
	advance() {
		let e = this.stacks, t = this.minStackPos, n = this.stacks = [], r, i;
		if (this.bigReductionCount > 300 && e.length == 1) {
			let [t] = e;
			for (; t.forceReduce() && t.stack.length && t.stack[t.stack.length - 2] >= this.lastBigReductionStart;);
			this.bigReductionCount = this.lastBigReductionSize = 0;
		}
		for (let a = 0; a < e.length; a++) {
			let o = e[a];
			for (;;) {
				if (this.tokens.mainToken = null, o.pos > t) n.push(o);
				else if (this.advanceStack(o, n, e)) continue;
				else {
					r || (r = [], i = []), r.push(o);
					let e = this.tokens.getMainToken(o);
					i.push(e.value, e.end);
				}
				break;
			}
		}
		if (!n.length) {
			let e = r && Bh(r);
			if (e) return Oh && console.log("Finish with " + this.stackID(e)), this.stackToTree(e);
			if (this.parser.strict) throw Oh && r && console.log("Stuck with token " + (this.tokens.mainToken ? this.parser.getName(this.tokens.mainToken.value) : "none")), SyntaxError("No parse at " + t);
			this.recovering ||= 5;
		}
		if (this.recovering && r) {
			let e = this.stoppedAt != null && r[0].pos > this.stoppedAt ? r[0] : this.runRecovery(r, i, n);
			if (e) return Oh && console.log("Force-finish " + this.stackID(e)), this.stackToTree(e.forceAll());
		}
		if (this.recovering) {
			let e = this.recovering == 1 ? 1 : this.recovering * 3;
			if (n.length > e) for (n.sort((e, t) => t.score - e.score); n.length > e;) n.pop();
			n.some((e) => e.reducePos > t) && this.recovering--;
		} else if (n.length > 1) {
			outer: for (let e = 0; e < n.length - 1; e++) {
				let t = n[e];
				for (let r = e + 1; r < n.length; r++) {
					let i = n[r];
					if (t.sameState(i) || t.buffer.length > 500 && i.buffer.length > 500) {
						if ((t.score - i.score || t.buffer.length - i.buffer.length) > 0) n.splice(r--, 1);
						else {
							n.splice(e--, 1);
							continue outer;
						}
					}
				}
			}
			n.length > 12 && (n.sort((e, t) => t.score - e.score), n.splice(12, n.length - 12));
		}
		this.minStackPos = n[0].pos;
		for (let e = 1; e < n.length; e++) n[e].pos < this.minStackPos && (this.minStackPos = n[e].pos);
		return null;
	}
	stopAt(e) {
		if (this.stoppedAt != null && this.stoppedAt < e) throw RangeError("Can't move stoppedAt forward");
		this.stoppedAt = e;
	}
	advanceStack(e, t, n) {
		let r = e.pos, { parser: i } = this, a = Oh ? this.stackID(e) + " -> " : "";
		if (this.stoppedAt != null && r > this.stoppedAt) return e.forceReduce() ? e : null;
		if (this.fragments) {
			let t = e.curContext && e.curContext.tracker.strict, n = t ? e.curContext.hash : 0;
			for (let o = this.fragments.nodeAt(r); o;) {
				let r = this.parser.nodeSet.types[o.type.id] == o.type ? i.getGoto(e.state, o.type.id) : -1;
				if (r > -1 && o.length && (!t || (o.prop(H.contextHash) || 0) == n)) return e.useNode(o, r), Oh && console.log(a + this.stackID(e) + ` (via reuse of ${i.getName(o.type.id)})`), !0;
				if (!(o instanceof G) || o.children.length == 0 || o.positions[0] > 0) break;
				let s = o.children[0];
				if (s instanceof G && o.positions[0] == 0) o = s;
				else break;
			}
		}
		let o = i.stateSlot(e.state, 4);
		if (o > 0) return e.reduce(o), Oh && console.log(a + this.stackID(e) + ` (via always-reduce ${i.getName(o & 65535)})`), !0;
		if (e.stack.length >= 8400) for (; e.stack.length > 6e3 && e.forceReduce(););
		let s = this.tokens.getActions(e);
		for (let o = 0; o < s.length;) {
			let c = s[o++], l = s[o++], u = s[o++], d = o == s.length || !n, f = d ? e : e.split(), p = this.tokens.mainToken;
			if (f.apply(c, l, p ? p.start : f.pos, u), Oh && console.log(a + this.stackID(f) + ` (via ${c & 65536 ? `reduce of ${i.getName(c & 65535)}` : "shift"} for ${i.getName(l)} @ ${r}${f == e ? "" : ", split"})`), d) return !0;
			f.pos > r ? t.push(f) : n.push(f);
		}
		return !1;
	}
	advanceFully(e, t) {
		let n = e.pos;
		for (;;) {
			if (!this.advanceStack(e, null, null)) return !1;
			if (e.pos > n) return Ph(e, t), !0;
		}
	}
	runRecovery(e, t, n) {
		let r = null, i = !1;
		for (let a = 0; a < e.length; a++) {
			let o = e[a], s = t[a << 1], c = t[(a << 1) + 1], l = Oh ? this.stackID(o) + " -> " : "";
			if (o.deadEnd && (i || (i = !0, o.restart(), Oh && console.log(l + this.stackID(o) + " (restarted)"), this.advanceFully(o, n)))) continue;
			let u = o.split(), d = l;
			for (let e = 0; e < 10 && u.forceReduce() && (Oh && console.log(d + this.stackID(u) + " (via force-reduce)"), !this.advanceFully(u, n)); e++) Oh && (d = this.stackID(u) + " -> ");
			for (let e of o.recoverByInsert(s)) Oh && console.log(l + this.stackID(e) + " (via recover-insert)"), this.advanceFully(e, n);
			this.stream.end > o.pos ? (c == o.pos && (c++, s = 0), o.recoverByDelete(s, c), Oh && console.log(l + this.stackID(o) + ` (via recover-delete ${this.parser.getName(s)})`), Ph(o, n)) : (!r || r.score < u.score) && (r = u);
		}
		return r;
	}
	stackToTree(e) {
		return e.close(), G.build({
			buffer: _h.create(e),
			nodeSet: this.parser.nodeSet,
			topID: this.topTerm,
			maxBufferLength: this.parser.bufferLength,
			reused: this.reused,
			start: this.ranges[0].from,
			length: e.pos - this.ranges[0].from,
			minRepeatType: this.parser.minRepeatTerm
		});
	}
	stackID(e) {
		let t = (kh ||= /* @__PURE__ */ new WeakMap()).get(e);
		return t || kh.set(e, t = String.fromCodePoint(this.nextStackID++)), t + e;
	}
};
function Ph(e, t) {
	for (let n = 0; n < t.length; n++) {
		let r = t[n];
		if (r.pos == e.pos && r.sameState(e)) {
			t[n].score < e.score && (t[n] = e);
			return;
		}
	}
	t.push(e);
}
var Fh = class {
	constructor(e, t, n) {
		this.source = e, this.flags = t, this.disabled = n;
	}
	allows(e) {
		return !this.disabled || this.disabled[e] == 0;
	}
}, Ih = (e) => e, Lh = class {
	constructor(e) {
		this.start = e.start, this.shift = e.shift || Ih, this.reduce = e.reduce || Ih, this.reuse = e.reuse || Ih, this.hash = e.hash || (() => 0), this.strict = e.strict !== !1;
	}
}, Rh = class e extends Xs {
	constructor(e) {
		if (super(), this.wrappers = [], e.version != 14) throw RangeError(`Parser version (${e.version}) doesn't match runtime version (14)`);
		let t = e.nodeNames.split(" ");
		this.minRepeatTerm = t.length;
		for (let n = 0; n < e.repeatNodeCount; n++) t.push("");
		let n = Object.keys(e.topRules).map((t) => e.topRules[t][1]), r = [];
		for (let e = 0; e < t.length; e++) r.push([]);
		function i(e, t, n) {
			r[e].push([t, t.deserialize(String(n))]);
		}
		if (e.nodeProps) for (let t of e.nodeProps) {
			let e = t[0];
			typeof e == "string" && (e = H[e]);
			for (let n = 1; n < t.length;) {
				let r = t[n++];
				if (r >= 0) i(r, e, t[n++]);
				else {
					let a = t[n + -r];
					for (let o = -r; o > 0; o--) i(t[n++], e, a);
					n++;
				}
			}
		}
		this.nodeSet = new Es(t.map((t, i) => U.define({
			name: i >= this.minRepeatTerm ? void 0 : t,
			id: i,
			props: r[i],
			top: n.indexOf(i) > -1,
			error: i == 0,
			skipped: e.skippedNodes && e.skippedNodes.indexOf(i) > -1
		}))), e.propSources && (this.nodeSet = this.nodeSet.extend(...e.propSources)), this.strict = !1, this.bufferLength = xs;
		let a = vh(e.tokenData);
		this.context = e.context, this.specializerSpecs = e.specialized || [], this.specialized = new Uint16Array(this.specializerSpecs.length);
		for (let e = 0; e < this.specializerSpecs.length; e++) this.specialized[e] = this.specializerSpecs[e].term;
		this.specializers = this.specializerSpecs.map(Vh), this.states = vh(e.states, Uint32Array), this.data = vh(e.stateData), this.goto = vh(e.goto), this.maxTerm = e.maxTerm, this.tokenizers = e.tokenizers.map((e) => typeof e == "number" ? new Sh(a, e) : e), this.topRules = e.topRules, this.dialects = e.dialects || {}, this.dynamicPrecedences = e.dynamicPrecedences || null, this.tokenPrecTable = e.tokenPrec, this.termNames = e.termNames || null, this.maxNode = this.nodeSet.types.length - 1, this.dialect = this.parseDialect(), this.top = this.topRules[Object.keys(this.topRules)[0]];
	}
	createParse(e, t, n) {
		let r = new Nh(this, e, t, n);
		for (let i of this.wrappers) r = i(r, e, t, n);
		return r;
	}
	getGoto(e, t, n = !1) {
		let r = this.goto;
		if (t >= r[0]) return -1;
		for (let i = r[t + 1];;) {
			let t = r[i++], a = t & 1, o = r[i++];
			if (a && n) return o;
			for (let n = i + (t >> 1); i < n; i++) if (r[i] == e) return o;
			if (a) return -1;
		}
	}
	hasAction(e, t) {
		let n = this.data;
		for (let r = 0; r < 2; r++) for (let i = this.stateSlot(e, r ? 2 : 1), a;; i += 3) {
			if ((a = n[i]) == 65535) {
				if (n[i + 1] == 1) a = n[i = zh(n, i + 2)];
				else if (n[i + 1] == 2) return zh(n, i + 2);
				else break;
			}
			if (a == t || a == 0) return zh(n, i + 1);
		}
		return 0;
	}
	stateSlot(e, t) {
		return this.states[e * 6 + t];
	}
	stateFlag(e, t) {
		return (this.stateSlot(e, 0) & t) > 0;
	}
	validAction(e, t) {
		return !!this.allActions(e, (e) => e == t || null);
	}
	allActions(e, t) {
		let n = this.stateSlot(e, 4), r = n ? t(n) : void 0;
		for (let n = this.stateSlot(e, 1); r == null; n += 3) {
			if (this.data[n] == 65535) {
				if (this.data[n + 1] == 1) n = zh(this.data, n + 2);
				else break;
			}
			r = t(zh(this.data, n + 1));
		}
		return r;
	}
	nextStates(e) {
		let t = [];
		for (let n = this.stateSlot(e, 1);; n += 3) {
			if (this.data[n] == 65535) {
				if (this.data[n + 1] == 1) n = zh(this.data, n + 2);
				else break;
			}
			if (!(this.data[n + 2] & 1)) {
				let e = this.data[n + 1];
				t.some((t, n) => n & 1 && t == e) || t.push(this.data[n], e);
			}
		}
		return t;
	}
	configure(t) {
		let n = Object.assign(Object.create(e.prototype), this);
		if (t.props && (n.nodeSet = this.nodeSet.extend(...t.props)), t.top) {
			let e = this.topRules[t.top];
			if (!e) throw RangeError(`Invalid top rule name ${t.top}`);
			n.top = e;
		}
		return t.tokenizers && (n.tokenizers = this.tokenizers.map((e) => {
			let n = t.tokenizers.find((t) => t.from == e);
			return n ? n.to : e;
		})), t.specializers && (n.specializers = this.specializers.slice(), n.specializerSpecs = this.specializerSpecs.map((e, r) => {
			let i = t.specializers.find((t) => t.from == e.external);
			if (!i) return e;
			let a = Object.assign(Object.assign({}, e), { external: i.to });
			return n.specializers[r] = Vh(a), a;
		})), t.contextTracker && (n.context = t.contextTracker), t.dialect && (n.dialect = this.parseDialect(t.dialect)), t.strict != null && (n.strict = t.strict), t.wrap && (n.wrappers = n.wrappers.concat(t.wrap)), t.bufferLength != null && (n.bufferLength = t.bufferLength), n;
	}
	hasWrappers() {
		return this.wrappers.length > 0;
	}
	getName(e) {
		return this.termNames ? this.termNames[e] : String(e <= this.maxNode && this.nodeSet.types[e].name || e);
	}
	get eofTerm() {
		return this.maxNode + 1;
	}
	get topNode() {
		return this.nodeSet.types[this.top[1]];
	}
	dynamicPrecedence(e) {
		let t = this.dynamicPrecedences;
		return t == null ? 0 : t[e] || 0;
	}
	parseDialect(e) {
		let t = Object.keys(this.dialects), n = t.map(() => !1);
		if (e) for (let r of e.split(" ")) {
			let e = t.indexOf(r);
			e >= 0 && (n[e] = !0);
		}
		let r = null;
		for (let e = 0; e < t.length; e++) if (!n[e]) for (let n = this.dialects[t[e]], i; (i = this.data[n++]) != 65535;) (r ||= new Uint8Array(this.maxTerm + 1))[i] = 1;
		return new Fh(e, n, r);
	}
	static deserialize(t) {
		return new e(t);
	}
};
function zh(e, t) {
	return e[t] | e[t + 1] << 16;
}
function Bh(e) {
	let t = null;
	for (let n of e) {
		let e = n.p.stoppedAt;
		(n.pos == n.p.stream.end || e != null && n.pos > e) && n.p.parser.stateFlag(n.state, 2) && (!t || t.score < n.score) && (t = n);
	}
	return t;
}
function Vh(e) {
	if (e.external) {
		let t = +!!e.extend;
		return (n, r) => e.external(n, r) << 1 | t;
	}
	return e.get;
}
//#endregion
//#region node_modules/@lezer/html/dist/index.js
var Hh = 55, Uh = 1, Wh = 56, Gh = 2, Kh = 57, qh = 3, Jh = 4, Yh = 5, Xh = 6, Zh = 7, Qh = 8, $h = 9, eg = 10, tg = 11, ng = 12, rg = 13, ig = 58, ag = 14, og = 15, sg = 59, cg = 21, lg = 23, ug = 24, dg = 25, fg = 27, pg = 28, mg = 29, hg = 32, gg = 35, _g = 37, vg = 38, yg = 0, bg = 1, xg = {
	area: !0,
	base: !0,
	br: !0,
	col: !0,
	command: !0,
	embed: !0,
	frame: !0,
	hr: !0,
	img: !0,
	input: !0,
	keygen: !0,
	link: !0,
	meta: !0,
	param: !0,
	source: !0,
	track: !0,
	wbr: !0,
	menuitem: !0
}, Sg = {
	dd: !0,
	li: !0,
	optgroup: !0,
	option: !0,
	p: !0,
	rp: !0,
	rt: !0,
	tbody: !0,
	td: !0,
	tfoot: !0,
	th: !0,
	tr: !0
}, Cg = {
	dd: {
		dd: !0,
		dt: !0
	},
	dt: {
		dd: !0,
		dt: !0
	},
	li: { li: !0 },
	option: {
		option: !0,
		optgroup: !0
	},
	optgroup: { optgroup: !0 },
	p: {
		address: !0,
		article: !0,
		aside: !0,
		blockquote: !0,
		dir: !0,
		div: !0,
		dl: !0,
		fieldset: !0,
		footer: !0,
		form: !0,
		h1: !0,
		h2: !0,
		h3: !0,
		h4: !0,
		h5: !0,
		h6: !0,
		header: !0,
		hgroup: !0,
		hr: !0,
		menu: !0,
		nav: !0,
		ol: !0,
		p: !0,
		pre: !0,
		section: !0,
		table: !0,
		ul: !0
	},
	rp: {
		rp: !0,
		rt: !0
	},
	rt: {
		rp: !0,
		rt: !0
	},
	tbody: {
		tbody: !0,
		tfoot: !0
	},
	td: {
		td: !0,
		th: !0
	},
	tfoot: { tbody: !0 },
	th: {
		td: !0,
		th: !0
	},
	thead: {
		tbody: !0,
		tfoot: !0
	},
	tr: { tr: !0 }
};
function wg(e) {
	return e == 45 || e == 46 || e == 58 || e >= 65 && e <= 90 || e == 95 || e >= 97 && e <= 122 || e >= 161;
}
var Tg = null, Eg = null, Dg = 0;
function Og(e, t) {
	let n = e.pos + t;
	if (Dg == n && Eg == e) return Tg;
	let r = e.peek(t), i = "";
	for (; wg(r);) i += String.fromCharCode(r), r = e.peek(++t);
	return Eg = e, Dg = n, Tg = i ? i.toLowerCase() : r == Mg || r == Ng ? void 0 : null;
}
var kg = 60, Ag = 62, jg = 47, Mg = 63, Ng = 33, Pg = 45;
function Fg(e, t) {
	this.name = e, this.parent = t;
}
var Ig = [
	Xh,
	eg,
	Zh,
	Qh,
	$h
], Lg = new Lh({
	start: null,
	shift(e, t, n, r) {
		return Ig.indexOf(t) > -1 ? new Fg(Og(r, 1) || "", e) : e;
	},
	reduce(e, t) {
		return t == cg && e ? e.parent : e;
	},
	reuse(e, t, n, r) {
		let i = t.type.id;
		return i == Xh || i == _g ? new Fg(Og(r, 1) || "", e) : e;
	},
	strict: !1
}), Rg = new wh((e, t) => {
	if (e.next != kg) {
		e.next < 0 && t.context && e.acceptToken(ig);
		return;
	}
	e.advance();
	let n = e.next == jg;
	n && e.advance();
	let r = Og(e, 0);
	if (r === void 0) return;
	if (!r) return e.acceptToken(n ? og : ag);
	let i = t.context ? t.context.name : null;
	if (n) {
		if (r == i) return e.acceptToken(tg);
		if (i && Sg[i]) return e.acceptToken(ig, -2);
		if (t.dialectEnabled(yg)) return e.acceptToken(ng);
		for (let e = t.context; e; e = e.parent) if (e.name == r) return;
		e.acceptToken(rg);
	} else {
		if (r == "script") return e.acceptToken(Zh);
		if (r == "style") return e.acceptToken(Qh);
		if (r == "textarea") return e.acceptToken($h);
		if (xg.hasOwnProperty(r)) return e.acceptToken(eg);
		i && Cg[i] && Cg[i][r] ? e.acceptToken(ig, -1) : e.acceptToken(Xh);
	}
}, { contextual: !0 }), zg = new wh((e) => {
	for (let t = 0, n = 0;; n++) {
		if (e.next < 0) {
			n && e.acceptToken(sg);
			break;
		}
		if (e.next == Pg) t++;
		else if (e.next == Ag && t >= 2) {
			n >= 3 && e.acceptToken(sg, -2);
			break;
		} else t = 0;
		e.advance();
	}
});
function Bg(e) {
	for (; e; e = e.parent) if (e.name == "svg" || e.name == "math") return !0;
	return !1;
}
var Vg = new wh((e, t) => {
	if (e.next == jg && e.peek(1) == Ag) {
		let n = t.dialectEnabled(bg) || Bg(t.context);
		e.acceptToken(n ? Yh : Jh, 2);
	} else e.next == Ag && e.acceptToken(Jh, 1);
});
function Hg(e, t, n) {
	let r = 2 + e.length;
	return new wh((i) => {
		for (let a = 0, o = 0, s = 0;; s++) {
			if (i.next < 0) {
				s && i.acceptToken(t);
				break;
			}
			if (a == 0 && i.next == kg || a == 1 && i.next == jg || a >= 2 && a < r && i.next == e.charCodeAt(a - 2)) a++, o++;
			else if (a == r && i.next == Ag) {
				s > o ? i.acceptToken(t, -o) : i.acceptToken(n, -(o - 2));
				break;
			} else if ((i.next == 10 || i.next == 13) && s) {
				i.acceptToken(t, 1);
				break;
			} else a = o = 0;
			i.advance();
		}
	});
}
var Ug = Hg("script", Hh, Uh), Wg = Hg("style", Wh, Gh), Gg = Hg("textarea", Kh, qh), Kg = vc({
	"Text RawText IncompleteTag IncompleteCloseTag": q.content,
	"StartTag StartCloseTag SelfClosingEndTag EndTag": q.angleBracket,
	TagName: q.tagName,
	"MismatchedCloseTag/TagName": [q.tagName, q.invalid],
	AttributeName: q.attributeName,
	"AttributeValue UnquotedAttributeValue": q.attributeValue,
	Is: q.definitionOperator,
	"EntityReference CharacterReference": q.character,
	Comment: q.blockComment,
	ProcessingInst: q.processingInstruction,
	DoctypeDecl: q.documentMeta
}), qg = Rh.deserialize({
	version: 14,
	states: ",xOVO!rOOO!ZQ#tO'#CrO!`Q#tO'#C{O!eQ#tO'#DOO!jQ#tO'#DRO!oQ#tO'#DTO!tOaO'#CqO#PObO'#CqO#[OdO'#CqO$kO!rO'#CqOOO`'#Cq'#CqO$rO$fO'#DUO$zQ#tO'#DWO%PQ#tO'#DXOOO`'#Dl'#DlOOO`'#DZ'#DZQVO!rOOO%UQ&rO,59^O%aQ&rO,59gO%lQ&rO,59jO%wQ&rO,59mO&SQ&rO,59oOOOa'#D_'#D_O&_OaO'#CyO&jOaO,59]OOOb'#D`'#D`O&rObO'#C|O&}ObO,59]OOOd'#Da'#DaO'VOdO'#DPO'bOdO,59]OOO`'#Db'#DbO'jO!rO,59]O'qQ#tO'#DSOOO`,59],59]OOOp'#Dc'#DcO'vO$fO,59pOOO`,59p,59pO(OQ#|O,59rO(TQ#|O,59sOOO`-E7X-E7XO(YQ&rO'#CtOOQW'#D['#D[O(hQ&rO1G.xOOOa1G.x1G.xOOO`1G/Z1G/ZO(sQ&rO1G/ROOOb1G/R1G/RO)OQ&rO1G/UOOOd1G/U1G/UO)ZQ&rO1G/XOOO`1G/X1G/XO)fQ&rO1G/ZOOOa-E7]-E7]O)qQ#tO'#CzOOO`1G.w1G.wOOOb-E7^-E7^O)vQ#tO'#C}OOOd-E7_-E7_O){Q#tO'#DQOOO`-E7`-E7`O*QQ#|O,59nOOOp-E7a-E7aOOO`1G/[1G/[OOO`1G/^1G/^OOO`1G/_1G/_O*VQ,UO,59`OOQW-E7Y-E7YOOOa7+$d7+$dOOO`7+$u7+$uOOOb7+$m7+$mOOOd7+$p7+$pOOO`7+$s7+$sO*bQ#|O,59fO*gQ#|O,59iO*lQ#|O,59lOOO`1G/Y1G/YO*qO7[O'#CwO+SOMhO'#CwOOQW1G.z1G.zOOO`1G/Q1G/QOOO`1G/T1G/TOOO`1G/W1G/WOOOO'#D]'#D]O+eO7[O,59cOOQW,59c,59cOOOO'#D^'#D^O+vOMhO,59cOOOO-E7Z-E7ZOOQW1G.}1G.}OOOO-E7[-E7[",
	stateData: ",c~O!_OS~OUSOVPOWQOXROYTO[]O][O^^O_^Oa^Ob^Oc^Od^Oy^O|_O!eZO~OgaO~OgbO~OgcO~OgdO~OgeO~O!XfOPmP![mP~O!YiOQpP![pP~O!ZlORsP![sP~OUSOVPOWQOXROYTOZqO[]O][O^^O_^Oa^Ob^Oc^Od^Oy^O!eZO~O![rO~P#gO!]sO!fuO~OgvO~OgwO~OS|OT}OiyO~OS!POT}OiyO~OS!ROT}OiyO~OS!TOT}OiyO~OS}OT}OiyO~O!XfOPmX![mX~OP!WO![!XO~O!YiOQpX![pX~OQ!ZO![!XO~O!ZlORsX![sX~OR!]O![!XO~O![!XO~P#gOg!_O~O!]sO!f!aO~OS!bO~OS!cO~Oj!dOShXThXihX~OS!fOT!gOiyO~OS!hOT!gOiyO~OS!iOT!gOiyO~OS!jOT!gOiyO~OS!gOT!gOiyO~Og!kO~Og!lO~Og!mO~OS!nO~Ol!qO!a!oO!c!pO~OS!rO~OS!sO~OS!tO~Ob!uOc!uOd!uO!a!wO!b!uO~Ob!xOc!xOd!xO!c!wO!d!xO~Ob!uOc!uOd!uO!a!{O!b!uO~Ob!xOc!xOd!xO!c!{O!d!xO~OT~cbd!ey|!e~",
	goto: "%q!aPPPPPPPPPPPPPPPPPPPPP!b!hP!nPP!zP!}#Q#T#Z#^#a#g#j#m#s#y!bP!b!bP$P$V$m$s$y%P%V%]%cPPPPPPPP%iX^OX`pXUOX`pezabcde{!O!Q!S!UR!q!dRhUR!XhXVOX`pRkVR!XkXWOX`pRnWR!XnXXOX`pQrXR!XpXYOX`pQ`ORx`Q{aQ!ObQ!QcQ!SdQ!UeZ!e{!O!Q!S!UQ!v!oR!z!vQ!y!pR!|!yQgUR!VgQjVR!YjQmWR![mQpXR!^pQtZR!`tS_O`ToXp",
	nodeNames: "⚠ StartCloseTag StartCloseTag StartCloseTag EndTag SelfClosingEndTag StartTag StartTag StartTag StartTag StartTag StartCloseTag StartCloseTag StartCloseTag IncompleteTag IncompleteCloseTag Document Text EntityReference CharacterReference InvalidEntity Element OpenTag TagName Attribute AttributeName Is AttributeValue UnquotedAttributeValue ScriptText CloseTag OpenTag StyleText CloseTag OpenTag TextareaText CloseTag OpenTag CloseTag SelfClosingTag Comment ProcessingInst MismatchedCloseTag CloseTag DoctypeDecl",
	maxTerm: 68,
	context: Lg,
	nodeProps: [
		[
			"closedBy",
			-10,
			1,
			2,
			3,
			7,
			8,
			9,
			10,
			11,
			12,
			13,
			"EndTag",
			6,
			"EndTag SelfClosingEndTag",
			-4,
			22,
			31,
			34,
			37,
			"CloseTag"
		],
		[
			"openedBy",
			4,
			"StartTag StartCloseTag",
			5,
			"StartTag",
			-4,
			30,
			33,
			36,
			38,
			"OpenTag"
		],
		[
			"group",
			-10,
			14,
			15,
			18,
			19,
			20,
			21,
			40,
			41,
			42,
			43,
			"Entity",
			17,
			"Entity TextContent",
			-3,
			29,
			32,
			35,
			"TextContent Entity"
		],
		[
			"isolate",
			-11,
			22,
			30,
			31,
			33,
			34,
			36,
			37,
			38,
			39,
			42,
			43,
			"ltr",
			-3,
			27,
			28,
			40,
			""
		]
	],
	propSources: [Kg],
	skippedNodes: [0],
	repeatNodeCount: 9,
	tokenData: "!<p!aR!YOX$qXY,QYZ,QZ[$q[]&X]^,Q^p$qpq,Qqr-_rs3_sv-_vw3}wxHYx}-_}!OH{!O!P-_!P!Q$q!Q![-_![!]Mz!]!^-_!^!_!$S!_!`!;x!`!a&X!a!c-_!c!}Mz!}#R-_#R#SMz#S#T1k#T#oMz#o#s-_#s$f$q$f%W-_%W%oMz%o%p-_%p&aMz&a&b-_&b1pMz1p4U-_4U4dMz4d4e-_4e$ISMz$IS$I`-_$I`$IbMz$Ib$Kh-_$Kh%#tMz%#t&/x-_&/x&EtMz&Et&FV-_&FV;'SMz;'S;:j!#|;:j;=`3X<%l?&r-_?&r?AhMz?Ah?BY$q?BY?MnMz?MnO$q!Z$|caPlW!b`!dpOX$qXZ&XZ[$q[^&X^p$qpq&Xqr$qrs&}sv$qvw+Pwx(tx!^$q!^!_*V!_!a&X!a#S$q#S#T&X#T;'S$q;'S;=`+z<%lO$q!R&bXaP!b`!dpOr&Xrs&}sv&Xwx(tx!^&X!^!_*V!_;'S&X;'S;=`*y<%lO&Xq'UVaP!dpOv&}wx'kx!^&}!^!_(V!_;'S&};'S;=`(n<%lO&}P'pTaPOv'kw!^'k!_;'S'k;'S;=`(P<%lO'kP(SP;=`<%l'kp([S!dpOv(Vx;'S(V;'S;=`(h<%lO(Vp(kP;=`<%l(Vq(qP;=`<%l&}a({WaP!b`Or(trs'ksv(tw!^(t!^!_)e!_;'S(t;'S;=`*P<%lO(t`)jT!b`Or)esv)ew;'S)e;'S;=`)y<%lO)e`)|P;=`<%l)ea*SP;=`<%l(t!Q*^V!b`!dpOr*Vrs(Vsv*Vwx)ex;'S*V;'S;=`*s<%lO*V!Q*vP;=`<%l*V!R*|P;=`<%l&XW+UYlWOX+PZ[+P^p+Pqr+Psw+Px!^+P!a#S+P#T;'S+P;'S;=`+t<%lO+PW+wP;=`<%l+P!Z+}P;=`<%l$q!a,]`aP!b`!dp!_^OX&XXY,QYZ,QZ]&X]^,Q^p&Xpq,Qqr&Xrs&}sv&Xwx(tx!^&X!^!_*V!_;'S&X;'S;=`*y<%lO&X!_-ljiSaPlW!b`!dpOX$qXZ&XZ[$q[^&X^p$qpq&Xqr-_rs&}sv-_vw/^wx(tx!P-_!P!Q$q!Q!^-_!^!_*V!_!a&X!a#S-_#S#T1k#T#s-_#s$f$q$f;'S-_;'S;=`3X<%l?Ah-_?Ah?BY$q?BY?Mn-_?MnO$q[/ebiSlWOX+PZ[+P^p+Pqr/^sw/^x!P/^!P!Q+P!Q!^/^!a#S/^#S#T0m#T#s/^#s$f+P$f;'S/^;'S;=`1e<%l?Ah/^?Ah?BY+P?BY?Mn/^?MnO+PS0rXiSqr0msw0mx!P0m!Q!^0m!a#s0m$f;'S0m;'S;=`1_<%l?Ah0m?BY?Mn0mS1bP;=`<%l0m[1hP;=`<%l/^!V1vciSaP!b`!dpOq&Xqr1krs&}sv1kvw0mwx(tx!P1k!P!Q&X!Q!^1k!^!_*V!_!a&X!a#s1k#s$f&X$f;'S1k;'S;=`3R<%l?Ah1k?Ah?BY&X?BY?Mn1k?MnO&X!V3UP;=`<%l1k!_3[P;=`<%l-_!Z3hV!ahaP!dpOv&}wx'kx!^&}!^!_(V!_;'S&};'S;=`(n<%lO&}!_4WiiSlWd!ROX5uXZ7SZ[5u[^7S^p5uqr8trs7Sst>]tw8twx7Sx!P8t!P!Q5u!Q!]8t!]!^/^!^!a7S!a#S8t#S#T;{#T#s8t#s$f5u$f;'S8t;'S;=`>V<%l?Ah8t?Ah?BY5u?BY?Mn8t?MnO5u!Z5zblWOX5uXZ7SZ[5u[^7S^p5uqr5urs7Sst+Ptw5uwx7Sx!]5u!]!^7w!^!a7S!a#S5u#S#T7S#T;'S5u;'S;=`8n<%lO5u!R7VVOp7Sqs7St!]7S!]!^7l!^;'S7S;'S;=`7q<%lO7S!R7qOb!R!R7tP;=`<%l7S!Z8OYlWb!ROX+PZ[+P^p+Pqr+Psw+Px!^+P!a#S+P#T;'S+P;'S;=`+t<%lO+P!Z8qP;=`<%l5u!_8{iiSlWOX5uXZ7SZ[5u[^7S^p5uqr8trs7Sst/^tw8twx7Sx!P8t!P!Q5u!Q!]8t!]!^:j!^!a7S!a#S8t#S#T;{#T#s8t#s$f5u$f;'S8t;'S;=`>V<%l?Ah8t?Ah?BY5u?BY?Mn8t?MnO5u!_:sbiSlWb!ROX+PZ[+P^p+Pqr/^sw/^x!P/^!P!Q+P!Q!^/^!a#S/^#S#T0m#T#s/^#s$f+P$f;'S/^;'S;=`1e<%l?Ah/^?Ah?BY+P?BY?Mn/^?MnO+P!V<QciSOp7Sqr;{rs7Sst0mtw;{wx7Sx!P;{!P!Q7S!Q!];{!]!^=]!^!a7S!a#s;{#s$f7S$f;'S;{;'S;=`>P<%l?Ah;{?Ah?BY7S?BY?Mn;{?MnO7S!V=dXiSb!Rqr0msw0mx!P0m!Q!^0m!a#s0m$f;'S0m;'S;=`1_<%l?Ah0m?BY?Mn0m!V>SP;=`<%l;{!_>YP;=`<%l8t!_>dhiSlWOX@OXZAYZ[@O[^AY^p@OqrBwrsAYswBwwxAYx!PBw!P!Q@O!Q!]Bw!]!^/^!^!aAY!a#SBw#S#TE{#T#sBw#s$f@O$f;'SBw;'S;=`HS<%l?AhBw?Ah?BY@O?BY?MnBw?MnO@O!Z@TalWOX@OXZAYZ[@O[^AY^p@Oqr@OrsAYsw@OwxAYx!]@O!]!^Az!^!aAY!a#S@O#S#TAY#T;'S@O;'S;=`Bq<%lO@O!RA]UOpAYq!]AY!]!^Ao!^;'SAY;'S;=`At<%lOAY!RAtOc!R!RAwP;=`<%lAY!ZBRYlWc!ROX+PZ[+P^p+Pqr+Psw+Px!^+P!a#S+P#T;'S+P;'S;=`+t<%lO+P!ZBtP;=`<%l@O!_COhiSlWOX@OXZAYZ[@O[^AY^p@OqrBwrsAYswBwwxAYx!PBw!P!Q@O!Q!]Bw!]!^Dj!^!aAY!a#SBw#S#TE{#T#sBw#s$f@O$f;'SBw;'S;=`HS<%l?AhBw?Ah?BY@O?BY?MnBw?MnO@O!_DsbiSlWc!ROX+PZ[+P^p+Pqr/^sw/^x!P/^!P!Q+P!Q!^/^!a#S/^#S#T0m#T#s/^#s$f+P$f;'S/^;'S;=`1e<%l?Ah/^?Ah?BY+P?BY?Mn/^?MnO+P!VFQbiSOpAYqrE{rsAYswE{wxAYx!PE{!P!QAY!Q!]E{!]!^GY!^!aAY!a#sE{#s$fAY$f;'SE{;'S;=`G|<%l?AhE{?Ah?BYAY?BY?MnE{?MnOAY!VGaXiSc!Rqr0msw0mx!P0m!Q!^0m!a#s0m$f;'S0m;'S;=`1_<%l?Ah0m?BY?Mn0m!VHPP;=`<%lE{!_HVP;=`<%lBw!ZHcW!cxaP!b`Or(trs'ksv(tw!^(t!^!_)e!_;'S(t;'S;=`*P<%lO(t!aIYliSaPlW!b`!dpOX$qXZ&XZ[$q[^&X^p$qpq&Xqr-_rs&}sv-_vw/^wx(tx}-_}!OKQ!O!P-_!P!Q$q!Q!^-_!^!_*V!_!a&X!a#S-_#S#T1k#T#s-_#s$f$q$f;'S-_;'S;=`3X<%l?Ah-_?Ah?BY$q?BY?Mn-_?MnO$q!aK_kiSaPlW!b`!dpOX$qXZ&XZ[$q[^&X^p$qpq&Xqr-_rs&}sv-_vw/^wx(tx!P-_!P!Q$q!Q!^-_!^!_*V!_!`&X!`!aMS!a#S-_#S#T1k#T#s-_#s$f$q$f;'S-_;'S;=`3X<%l?Ah-_?Ah?BY$q?BY?Mn-_?MnO$q!TM_XaP!b`!dp!fQOr&Xrs&}sv&Xwx(tx!^&X!^!_*V!_;'S&X;'S;=`*y<%lO&X!aNZ!ZiSgQaPlW!b`!dpOX$qXZ&XZ[$q[^&X^p$qpq&Xqr-_rs&}sv-_vw/^wx(tx}-_}!OMz!O!PMz!P!Q$q!Q![Mz![!]Mz!]!^-_!^!_*V!_!a&X!a!c-_!c!}Mz!}#R-_#R#SMz#S#T1k#T#oMz#o#s-_#s$f$q$f$}-_$}%OMz%O%W-_%W%oMz%o%p-_%p&aMz&a&b-_&b1pMz1p4UMz4U4dMz4d4e-_4e$ISMz$IS$I`-_$I`$IbMz$Ib$Je-_$Je$JgMz$Jg$Kh-_$Kh%#tMz%#t&/x-_&/x&EtMz&Et&FV-_&FV;'SMz;'S;:j!#|;:j;=`3X<%l?&r-_?&r?AhMz?Ah?BY$q?BY?MnMz?MnO$q!a!$PP;=`<%lMz!R!$ZY!b`!dpOq*Vqr!$yrs(Vsv*Vwx)ex!a*V!a!b!4t!b;'S*V;'S;=`*s<%lO*V!R!%Q]!b`!dpOr*Vrs(Vsv*Vwx)ex}*V}!O!%y!O!f*V!f!g!']!g#W*V#W#X!0`#X;'S*V;'S;=`*s<%lO*V!R!&QX!b`!dpOr*Vrs(Vsv*Vwx)ex}*V}!O!&m!O;'S*V;'S;=`*s<%lO*V!R!&vV!b`!dp!ePOr*Vrs(Vsv*Vwx)ex;'S*V;'S;=`*s<%lO*V!R!'dX!b`!dpOr*Vrs(Vsv*Vwx)ex!q*V!q!r!(P!r;'S*V;'S;=`*s<%lO*V!R!(WX!b`!dpOr*Vrs(Vsv*Vwx)ex!e*V!e!f!(s!f;'S*V;'S;=`*s<%lO*V!R!(zX!b`!dpOr*Vrs(Vsv*Vwx)ex!v*V!v!w!)g!w;'S*V;'S;=`*s<%lO*V!R!)nX!b`!dpOr*Vrs(Vsv*Vwx)ex!{*V!{!|!*Z!|;'S*V;'S;=`*s<%lO*V!R!*bX!b`!dpOr*Vrs(Vsv*Vwx)ex!r*V!r!s!*}!s;'S*V;'S;=`*s<%lO*V!R!+UX!b`!dpOr*Vrs(Vsv*Vwx)ex!g*V!g!h!+q!h;'S*V;'S;=`*s<%lO*V!R!+xY!b`!dpOr!+qrs!,hsv!+qvw!-Swx!.[x!`!+q!`!a!/j!a;'S!+q;'S;=`!0Y<%lO!+qq!,mV!dpOv!,hvx!-Sx!`!,h!`!a!-q!a;'S!,h;'S;=`!.U<%lO!,hP!-VTO!`!-S!`!a!-f!a;'S!-S;'S;=`!-k<%lO!-SP!-kO|PP!-nP;=`<%l!-Sq!-xS!dp|POv(Vx;'S(V;'S;=`(h<%lO(Vq!.XP;=`<%l!,ha!.aX!b`Or!.[rs!-Ssv!.[vw!-Sw!`!.[!`!a!.|!a;'S!.[;'S;=`!/d<%lO!.[a!/TT!b`|POr)esv)ew;'S)e;'S;=`)y<%lO)ea!/gP;=`<%l!.[!R!/sV!b`!dp|POr*Vrs(Vsv*Vwx)ex;'S*V;'S;=`*s<%lO*V!R!0]P;=`<%l!+q!R!0gX!b`!dpOr*Vrs(Vsv*Vwx)ex#c*V#c#d!1S#d;'S*V;'S;=`*s<%lO*V!R!1ZX!b`!dpOr*Vrs(Vsv*Vwx)ex#V*V#V#W!1v#W;'S*V;'S;=`*s<%lO*V!R!1}X!b`!dpOr*Vrs(Vsv*Vwx)ex#h*V#h#i!2j#i;'S*V;'S;=`*s<%lO*V!R!2qX!b`!dpOr*Vrs(Vsv*Vwx)ex#m*V#m#n!3^#n;'S*V;'S;=`*s<%lO*V!R!3eX!b`!dpOr*Vrs(Vsv*Vwx)ex#d*V#d#e!4Q#e;'S*V;'S;=`*s<%lO*V!R!4XX!b`!dpOr*Vrs(Vsv*Vwx)ex#X*V#X#Y!+q#Y;'S*V;'S;=`*s<%lO*V!R!4{Y!b`!dpOr!4trs!5ksv!4tvw!6Vwx!8]x!a!4t!a!b!:]!b;'S!4t;'S;=`!;r<%lO!4tq!5pV!dpOv!5kvx!6Vx!a!5k!a!b!7W!b;'S!5k;'S;=`!8V<%lO!5kP!6YTO!a!6V!a!b!6i!b;'S!6V;'S;=`!7Q<%lO!6VP!6lTO!`!6V!`!a!6{!a;'S!6V;'S;=`!7Q<%lO!6VP!7QOyPP!7TP;=`<%l!6Vq!7]V!dpOv!5kvx!6Vx!`!5k!`!a!7r!a;'S!5k;'S;=`!8V<%lO!5kq!7yS!dpyPOv(Vx;'S(V;'S;=`(h<%lO(Vq!8YP;=`<%l!5ka!8bX!b`Or!8]rs!6Vsv!8]vw!6Vw!a!8]!a!b!8}!b;'S!8];'S;=`!:V<%lO!8]a!9SX!b`Or!8]rs!6Vsv!8]vw!6Vw!`!8]!`!a!9o!a;'S!8];'S;=`!:V<%lO!8]a!9vT!b`yPOr)esv)ew;'S)e;'S;=`)y<%lO)ea!:YP;=`<%l!8]!R!:dY!b`!dpOr!4trs!5ksv!4tvw!6Vwx!8]x!`!4t!`!a!;S!a;'S!4t;'S;=`!;r<%lO!4t!R!;]V!b`!dpyPOr*Vrs(Vsv*Vwx)ex;'S*V;'S;=`*s<%lO*V!R!;uP;=`<%l!4t!V!<TXjSaP!b`!dpOr&Xrs&}sv&Xwx(tx!^&X!^!_*V!_;'S&X;'S;=`*y<%lO&X",
	tokenizers: [
		Ug,
		Wg,
		Gg,
		Vg,
		Rg,
		zg,
		0,
		1,
		2,
		3,
		4,
		5
	],
	topRules: { Document: [0, 16] },
	dialects: {
		noMatch: 0,
		selfClosing: 515
	},
	tokenPrec: 517
});
function Jg(e, t) {
	let n = Object.create(null);
	for (let r of e.getChildren(ug)) {
		let e = r.getChild(dg), i = r.getChild(fg) || r.getChild(pg);
		e && (n[t.read(e.from, e.to)] = i ? i.type.id == fg ? t.read(i.from + 1, i.to - 1) : t.read(i.from, i.to) : "");
	}
	return n;
}
function Yg(e, t) {
	let n = e.getChild(lg);
	return n ? t.read(n.from, n.to) : " ";
}
function Xg(e, t, n) {
	let r;
	for (let i of n) if (!i.attrs || i.attrs(r ||= Jg(e.node.parent.firstChild, t))) return {
		parser: i.parser,
		bracketed: !0
	};
	return null;
}
function Zg(e = [], t = []) {
	let n = [], r = [], i = [], a = [];
	for (let t of e) (t.tag == "script" ? n : t.tag == "style" ? r : t.tag == "textarea" ? i : a).push(t);
	let o = t.length ? Object.create(null) : null;
	for (let e of t) (o[e.name] || (o[e.name] = [])).push(e);
	return Qs((e, t) => {
		let s = e.type.id;
		if (s == mg) return Xg(e, t, n);
		if (s == hg) return Xg(e, t, r);
		if (s == gg) return Xg(e, t, i);
		if (s == cg && a.length) {
			let n = e.node, r = n.firstChild, i = r && Yg(r, t), o;
			if (i) {
				for (let e of a) if (e.tag == i && (!e.attrs || e.attrs(o ||= Jg(r, t)))) {
					let t = n.lastChild, i = t.type.id == vg ? t.from : n.to;
					if (i > r.to) return {
						parser: e.parser,
						overlay: [{
							from: r.to,
							to: i
						}]
					};
				}
			}
		}
		if (o && s == ug) {
			let n = e.node, r;
			if (r = n.firstChild) {
				let e = o[t.read(r.from, r.to)];
				if (e) for (let r of e) {
					if (r.tagName && r.tagName != Yg(n.parent, t)) continue;
					let e = n.lastChild;
					if (e.type.id == fg) {
						let t = e.from + 1, n = e.lastChild, i = e.to - (n && n.isError ? 0 : 1);
						if (i > t) return {
							parser: r.parser,
							overlay: [{
								from: t,
								to: i
							}],
							bracketed: !0
						};
					} else if (e.type.id == pg) return {
						parser: r.parser,
						overlay: [{
							from: e.from,
							to: e.to
						}]
					};
				}
			}
		}
		return null;
	});
}
//#endregion
//#region node_modules/@lezer/css/dist/index.js
var Qg = 148, $g = 1, e_ = 149, t_ = 150, n_ = 2, r_ = 151, i_ = 3, a_ = 4, o_ = [
	9,
	10,
	11,
	12,
	13,
	32,
	133,
	160,
	5760,
	8192,
	8193,
	8194,
	8195,
	8196,
	8197,
	8198,
	8199,
	8200,
	8201,
	8202,
	8232,
	8233,
	8239,
	8287,
	12288
], s_ = 58, c_ = 40, l_ = 95, u_ = 91, d_ = 45, f_ = 46, p_ = 35, m_ = 37, h_ = 38, g_ = 92, __ = 10, v_ = 42;
function y_(e) {
	return e >= 65 && e <= 90 || e >= 97 && e <= 122 || e >= 161;
}
function b_(e) {
	return e >= 48 && e <= 57;
}
function x_(e) {
	return b_(e) || e >= 97 && e <= 102 || e >= 65 && e <= 70;
}
var S_ = (e, t, n) => (r, i) => {
	for (let a = !1, o = 0, s = 0;; s++) {
		let { next: c } = r;
		if (y_(c) || c == d_ || c == l_ || a && b_(c)) !a && (c != d_ || s > 0) && (a = !0), o === s && c == d_ && o++, r.advance();
		else if (c == g_ && r.peek(1) != __) {
			if (r.advance(), x_(r.next)) {
				do
					r.advance();
				while (x_(r.next));
				r.next == 32 && r.advance();
			} else r.next > -1 && r.advance();
			a = !0;
		} else {
			a && r.acceptToken(o == 2 && i.canShift(n_) ? t : c == c_ ? n : e);
			break;
		}
	}
}, C_ = new wh(S_(e_, n_, t_), { contextual: !0 }), w_ = new wh(S_(r_, i_, a_), { contextual: !0 }), T_ = new wh((e) => {
	if (o_.includes(e.peek(-1))) {
		let { next: t } = e;
		(y_(t) || t == l_ || t == p_ || t == f_ || t == v_ || t == u_ || t == s_ && y_(e.peek(1)) || t == d_ || t == h_) && e.acceptToken(Qg);
	}
}), E_ = new wh((e) => {
	if (!o_.includes(e.peek(-1))) {
		let { next: t } = e;
		if (t == m_ && (e.advance(), e.acceptToken($g)), y_(t)) {
			do
				e.advance();
			while (y_(e.next) || b_(e.next));
			e.acceptToken($g);
		}
	}
}), D_ = vc({
	"AtKeyword import charset namespace keyframes media supports font-feature-values": q.definitionKeyword,
	"from to selector scope MatchFlag": q.keyword,
	NamespaceName: q.namespace,
	KeyframeName: q.labelName,
	KeyframeRangeName: q.operatorKeyword,
	TagName: q.tagName,
	ClassName: q.className,
	PseudoClassName: q.constant(q.className),
	IdName: q.labelName,
	"FeatureName PropertyName": q.propertyName,
	AttributeName: q.attributeName,
	NumberLiteral: q.number,
	KeywordQuery: q.keyword,
	UnaryQueryOp: q.operatorKeyword,
	"CallTag ValueName FontName": q.atom,
	VariableName: q.variableName,
	Callee: q.operatorKeyword,
	Unit: q.unit,
	"UniversalSelector NestingSelector": q.definitionOperator,
	"MatchOp CompareOp": q.compareOperator,
	"ChildOp SiblingOp, LogicOp": q.logicOperator,
	BinOp: q.arithmeticOperator,
	Important: q.modifier,
	Comment: q.blockComment,
	ColorLiteral: q.color,
	"ParenthesizedContent StringLiteral": q.string,
	":": q.punctuation,
	"PseudoOp #": q.derefOperator,
	"; , |": q.separator,
	"( )": q.paren,
	"[ ]": q.squareBracket,
	"{ }": q.brace
}), O_ = {
	__proto__: null,
	lang: 44,
	"nth-child": 44,
	"nth-last-child": 44,
	"nth-of-type": 44,
	"nth-last-of-type": 44,
	dir: 44,
	"host-context": 44,
	if: 90,
	url: 158,
	"url-prefix": 158,
	domain: 158,
	regexp: 158
}, k_ = {
	__proto__: null,
	or: 104,
	and: 104,
	not: 112,
	only: 112,
	layer: 212
}, A_ = {
	__proto__: null,
	selector: 118,
	style: 124,
	layer: 208
}, j_ = {
	__proto__: null,
	"@import": 204,
	"@media": 216,
	"@charset": 220,
	"@namespace": 224,
	"@keyframes": 230,
	"@supports": 242,
	"@scope": 246,
	"@font-feature-values": 252
}, M_ = {
	__proto__: null,
	to: 249
}, N_ = Rh.deserialize({
	version: 14,
	states: "MrQYQdOOO#}QdOOP$UO`OOO%OQaO'#CfOOQP'#Ce'#CeO%VQdO'#CgO%[Q`O'#CgO%aQaO'#FqO&XQdO'#CkO&xQaO'#CcO'SQdO'#CnO'_QdO'#ERO'dQdO'#ETO'oQdO'#E[O'oQdO'#E_OOQP'#Fq'#FqO)RQhO'#FQOOQS'#Fp'#FpOOQS'#FT'#FTQYQdOOO)YQdO'#EeO*iQhO'#EkO)YQdO'#EmO*pQdO'#EoO*{QdO'#ErO)}QhO'#ExO+TQdO'#EzO+`QdO'#E}O+eQaO'#CfO+lQ`O'#EbO+qQ`O'#F}O+|QdO'#F}QOQ`OOP,WO&jO'#CaPOOO)CA`)CA`OOQP'#Ci'#CiOOQP,59R,59RO%VQdO,59ROOQP'#Cm'#CmOOQP,59V,59VO&XQdO,59VO,cQdO,59YO'_QdO,5:mO'dQdO,5:oO'oQdO,5:vO'oQdO,5:xO'oQdO,5:yO'oQdO'#F[O,nQ`O,58}O,vQdO'#EaOOQS,58},58}OOQP'#Cq'#CqOOQO'#EP'#EPOOQP,59Y,59YO,}Q`O,59YO-SQ`O,59YOOQP'#ES'#ESOOQP,5:m,5:mO-XQpO'#EUO-dQdO'#EVO-iQ`O'#EVO-nQpO,5:oO.XQaO,5:vO.oQaO,5:yOOQW'#D^'#D^O/nQhO'#DgO0RQhO,5;lO)}QhO'#DeO0`Q`O'#DnO0eQhO'#D{OOQW'#Fw'#FwOOQS,5;l,5;lO0jQ`O'#DhO0oQ`O'#DkOOQS-E9R-E9ROOQ['#Cv'#CvO0tQdO'#CwO1[QdO'#C}O1rQdO'#DQO2YQ!pO'#DSO4fQ!jO,5;POOQO'#DX'#DXO-SQ`O'#DWO4vQ!nO'#FtO6|Q`O'#DYO7RQ`O'#D|OOQ['#Ft'#FtO7WQhO'#GQO7fQ`O,5;VO7kQ!bO,5;XOOQS'#Eq'#EqO7sQ`O,5;ZO7xQdO,5;ZOOQO'#Et'#EtO8QQ`O,5;^O8VQhO,5;dO'oQdO'#DjOOQS,5;f,5;fO0jQ`O,5;fO8_QdO,5;fOOQS'#Fc'#FcO8gQdO'#FPO7fQ`O,5;iO8oQdO,5:|O9PQdO'#F^O9^Q`O,5<iO9^Q`O,5<iPOOO'#FS'#FSP9iO&jO,58{POOO,58{,58{OOQP1G.m1G.mOOQP1G.q1G.qOOQP1G.t1G.tO,}Q`O1G.tO-SQ`O1G.tOOQP1G0X1G0XO9tQpO1G0ZO9|QaO1G0bO:dQaO1G0dO:zQaO1G0eO;bQaO,5;vOOQO-E9Y-E9YOOQS1G.i1G.iO;lQ`O,5:{O;qQdO'#EQO;xQdO'#CuOOQO'#EX'#EXOOQO,5:q,5:qO-dQdO,5:qOOQP1G0Z1G0ZO)YQdO1G0ZO<PQ!jO'#D^O<_Q!bO,59yO<gQhO,5:ROOQO'#Fx'#FxO<bQ!bO,59}O<oQhO'#FdO)}QhO,59{O)}QhO'#FdO=gQhO1G1WOOQS1G1W1G1WO=qQhO,5:PO>lQhO'#DoOOQW,5:Y,5:YOOQW,5:g,5:gOOQW,5:S,5:SO>vQhO,5:VO?bQ!fO'#FuOOQS'#Fu'#FuOOQS'#FV'#FVO@rQdO,59cOOQ[,59c,59cOAYQdO,59iOOQ[,59i,59iOApQdO,59lOOQ[,59l,59lOOQ[,59n,59nO)YQdO,59pOBWQhO'#EgOOQW'#Eg'#EgOBuQ`O1G0kO4oQhO1G0kOOQ[,59r,59rO)}QhO'#D[OOQ[,59t,59tOBzQ#tO,5:hOCVQhO'#F`OCdQ`O,5<lOOQS1G0q1G0qOOQS1G0s1G0sOOQS1G0u1G0uOCoQ`O1G0uOCtQdO'#EuOOQS1G0x1G0xOOQS1G1O1G1OODPQaO,5:UO7fQ`O1G1QOOQS1G1Q1G1QO0jQ`O1G1QOOQS-E9a-E9aOOQS1G1T1G1TODWQ!fO1G0hODnQ`O'#EdOOQO1G0h1G0hOOQO,5;x,5;xODsQdO,5;xOOQO-E9[-E9[OEQQ`O1G2TPOOO-E9Q-E9QPOOO1G.g1G.gOOQP7+$`7+$`OOQP7+%u7+%uO)YQdO7+%uOOQS1G0g1G0gOE]QaO'#F|OEgQ`O,5:lOElQ!fO'#FUOFjQdO'#FsOFtQ`O,59aOOQO1G0]1G0]OFyQ!bO7+%uO)YQdO1G/eOGUQhO1G/iOOQW1G/m1G/mOOQW1G/g1G/gOGgQhO,5<OOOQW-E9b-E9bOOQS7+&r7+&rOH_QhO'#D^OHmQhO'#F{OHxQ`O'#F{OH}Q`O,5:ZOISQ!bO'#D`O>vQhO'#DmOI_QhO'#DsOIgQhO'#DuOIlQ!jO'#FzOOQO'#Fz'#FzOIwQ`O'#DxOJPQ!bO'#DzOOQO'#Fy'#FyOJUQ`O1G/qOOQS-E9T-E9TOOQ[1G.}1G.}OOQ[1G/T1G/TOOQ[1G/W1G/WOOQ[1G/[1G/[OJZQdO,5;ROOQS7+&V7+&VOJ`Q`O7+&VOJeQhO'#D]OJmQ`O,59vO)}QhO,59vOOQ[1G0S1G0SOJuQ`O1G0SOJzQhO,5;zOOQO-E9^-E9^OOQS7+&a7+&aOKYQbO'#DSOOQO'#Ew'#EwOKhQ`O'#EvOOQO'#Ev'#EvOKsQ`O'#FaOK{QdO,5;aOOQS,5;a,5;aOOQ[1G/p1G/pOOQS7+&l7+&lO7fQ`O7+&lOLWQ!fO'#F]O)YQdO'#F]OM_QdO7+&SOOQO7+&S7+&SOOQO,5;O,5;OOOQO1G1d1G1dOMrQ!bO<<IaOM}QdO'#FZONXQ`O,5<hOOQP1G0W1G0WOOQS-E9S-E9SONaQdO'#FYONkQ`O,5<_OOQ]1G.{1G.{OOQP<<Ia<<IaONsQ`O<<IaONxQdO7+%POOQO'#D`'#D`O! PQ!bO7+%TO! XQhO'#FXO! fQ`O,5<gO)YQdO,5<gOOQW1G/u1G/uO! nQ`O,5:XO>vQhO'#DtOOQO,5:_,5:_O! sQhO,5:aO! {QhO,5:fO)YQdO,5:dOOQW7+%]7+%]OOQO'#Ei'#EiO!!SQ`O1G0mOOQS<<Iq<<IqO)YQdO,59wO!!vQhO1G/bOOQ[1G/b1G/bO!!}Q`O1G/bOOQW-E9U-E9UOOQ[7+%n7+%nOOQO,5;b,5;bOCwQdO'#FbOKsQ`O,5;{OOQS,5;{,5;{OOQS-E9_-E9_OOQS1G0{1G0{OOQS<<JW<<JWO!#VQ!fO,5;wOOQS-E9Z-E9ZOOQO<<In<<InOOQPAN>{AN>{O!$^Q`OAN>{O!$cQaO,5;uOOQO-E9X-E9XO!$mQdO,5;tOOQO-E9W-E9WOOQW<<Hk<<HkOOQW<<Ho<<HoO!$wQhO<<HoO!%YQhO'#D^O!%hQhO,5;sO!%sQ`O,5;sOOQO-E9V-E9VO!%xQdO1G2RO!&SQhO1G/sO!&[Q`O,5:`O>vQhO'#DwOOQO1G/{1G/{O!&aQ!bO1G0QO!&iQdO1G0OOJZQdO'#F_O!&pQ`O7+&XOOQW7+&X7+&XO!&xQ!bO1G/cOOQ[7+$|7+$|O!'TQhO7+$|P!'[Q`O'#FWOOQO,5;|,5;|OOQO-E9`-E9`OOQS1G1g1G1gOOQPG24gG24gO!'aQ`OAN>ZO)YQdO1G1_O!'fQ`O7+'mOOQO1G/z1G/zO!'nQ`O,5:cO!'sQhO7+%lOOQO,5;y,5;yOOQO-E9]-E9]OOQW<<Is<<IsOOQ[<<Hh<<HhPOQW,5;r,5;rOOQWG23uG23uO!'zQdO7+&yOOQO1G/}1G/}OOQO<<IW<<IW",
	stateData: "!(_~O$_OS$`QQ~OWVO^_O`WOcYOdYOl`OmZOp[O#P]O#S^O#YdO#`eO#bfO#dgO#ghO#miO#ojO#rkO$ZRO$fTO~OQmOWVO^_O`WOcYOdYOl`OmZOp[O#P]O#S^O#YdO#`eO#bfO#dgO#ghO#miO#ojO#rkO$ZlO$fTO~O$X$qP~P!jO$`qO~O`YXcYXdYXmYXpYXsYX!eYX#PYX#SYX$YYX$f[X~OgYX~P$ZO$ZsO~O$fuO~O$fuO`$eXc$eXd$eXm$eXp$eXs$eX!e$eX#P$eX#S$eX$Y$eXg$eX~O$ZvO~O`xOcyOdyOmzOp{O#P|O#S!OO$Y}O~Os!RO!e!PO~P&^Of!XO$Z!TO$[!UO~O$Z!YO~OW!^O$Z![O$f!]O~OWVO^_O`WOcYOdYOmZOp[O#P]O#S^O$ZRO$fTO~OS!fOc!gOd!gOh!cOs!RO!Y!eO!]!jO!`!kO$]!bO~On!iO~P(dOQ!uOh!nOp!oOs!pOu!xOw!xO}!vO!q!wO$Z!mO$[!sO$j!qO~OS!fOc!gOd!gOh!cO!Y!eO!]!jO!`!kO$]!bO~Os$tP~P)}Ow!}O!q!wO$Z!|O~Ow#PO$Z#PO~Oh#SOs!RO#p#UO~O$Z#WO~Oc#VX~P$ZOc#ZO~On#[O$X$qXr$qX~O$X$qXr$qX~P!jO$a#_O$b#_O$c#aO~Of#fO$Z!TO$[!UO~Os!RO!e!PO~Or$qP~P!jOh#pO~Oh#qO~Oo!xX!|!xX$f!zX~O$Z#rO~O$f#tO~Oo#uO!|#vO~O`xOcyOdyOmzOp{O~Os#Oa!e#Oa#P#Oa#S#Oa$Y#Oag#Oa~P-vOs#Ra!e#Ra#P#Ra#S#Ra$Y#Rag#Ra~P-vOS!fOc!gOd!gOh!cO!Y!eO!]!jO!`!kO~OR#zOu#zOw#zO$]#wO$j!qO~P/VOn$QO!U#}O!e$OO~P(dOh$SO~O$]$UO~Oh#SO~Oh$WO~O`$YOc$YOg$]Ol$YOm$YOn$YO~P)YO`$YOc$YOl$YOm$YOn$YOo$_O~P)YO`$YOc$YOl$YOm$YOn$YOr$aO~P)YOP$bOSvXcvXdvXhvXnvXyvX!YvX!]vX!`vX#[vX#^vX$]vX!WvXQvX`vXgvXlvXmvXpvXsvXuvXwvX}vX!qvX$ZvX$[vX$jvXovXrvX!evX$XvX$svX!}vX~Oy$cO#[$dO#^$eOn$tP~P)}Oh#qOS$hXc$hXd$hXn$hXy$hX!Y$hX!]$hX!`$hX#[$hX#^$hX$]$hXQ$hX`$hXg$hXl$hXm$hXp$hXs$hXu$hXw$hX}$hX!q$hX$Z$hX$[$hX$j$hXo$hXr$hX!e$hX$X$hX$s$hX!}$hX~Oh$iO~Oh$kO~O!U#}O!e$lOs$tXn$tX~Os!RO~On$oOy$cO~On$pO~Ow$qO!q!wO~Os$rO~Os!RO!U#}O~Os!RO#p$xO~O$Z#WOs#sX~O$s$|On#Ua$X#Uar#Ua~P)YOn$QX$X$QXr$QX~P!jOn#[O$X$qar$qa~O$a#_O$b#_O$c%TO~Oo%VO!|%WO~Os#Oi!e#Oi#P#Oi#S#Oi$Y#Oig#Oi~P-vOs#Qi!e#Qi#P#Qi#S#Qi$Y#Qig#Qi~P-vOs#Ri!e#Ri#P#Ri#S#Ri$Y#Rig#Ri~P-vOs$Oa!e$Oa~P&^Or%XO~Og$pP~P'oOg$gP~P)YOc!SXg!QX!U!QX!W!SX~Oc%aO!W%bO~Og%cO!U#}O~O!U#}OS$WXc$WXd$WXh$WXn$WXs$WX!Y$WX!]$WX!`$WX!e$WX$]$WX~On%gO!e$OO~P(dO!U#}OS!Xac!Xad!Xah!Xan!Xas!Xa!Y!Xa!]!Xa!`!Xa!e!Xa$]!Xag!Xa~O$]%hOg$oP~P/VOR#zOS!fOh%mOu#zOw#zO!Y%nO$]%lO$j!qO~Oy$cOQ$iX`$iXc$iXg$iXh$iXl$iXm$iXn$iXp$iXs$iXu$iXw$iX}$iX!q$iX$Z$iX$[$iX$j$iXo$iXr$iX~O`$YOc$YOg%wOl$YOm$YOn$YO~P)YO`$YOc$YOl$YOm$YOn$YOo%xO~P)YO`$YOc$YOl$YOm$YOn$YOr%yO~P)YOh%{OS#ZXc#ZXd#ZXn#ZX!Y#ZX!]#ZX!`#ZX$]#ZX~On%|O~Og&ROw&SO!r&SO~Os$SX!e$SXn$SX~P)}O!e$lOs$tan$ta~On&VO~Or&^O$Z&XO$j&WO~Og&_O~P&^Oy$cO!e&cO$s$|On#Ui$X#Uir#Ui~P)YO$r&fO~On$Qa$X$Qar$Qa~P!jOn#[O$X$qir$qi~O!e&iOg$pX~P&^Og&kO~Oy$cOQ#xXg#xXh#xXp#xXs#xXu#xXw#xX}#xX!e#xX!q#xX$Z#xX$[#xX$j#xX~O!e&mOg$gX~P)YOg&oO~Oo&pOy$cO!}&qO~OR#zOu#zOw#zO$]&sO$j!qO~O!U#}OS$Wac$Wad$Wah$Wan$Was$Wa!Y$Wa!]$Wa!`$Wa!e$Wa$]$Wa~Oc!dXg!QX!U!QX!e!QX~O!U#}O!e&uOg$oX~Oc&wO~Og&xO~Oc!mXg!mX!W!SX~OS!fOh&zO~O!U&|O~O!U&|O!W&}Og$nX~Oc'OOg!lX~O!W&}O~Og'PO~O$Z'QO~On'SO~Oc'TO!U#}O~Og'VOn'UO~Og'YO~O!U#}Os$Sa!e$San$Sa~OP$bOsvX!evXgvX~O$j&WOs#jX!e#jX~Os!RO!e'[O~Or'`O$Z&XO$j&WO~Oy$cOQ$PXh$PXn$PXp$PXs$PXu$PXw$PX}$PX!e$PX!q$PX$X$PX$Z$PX$[$PX$j$PX$s$PXr$PX~O!e&cO$s$|On#Uq$X#Uqr#Uq~P)YOo'eOy$cO!}'fO~Og#}X!e#}X~P'oO!e&iOg$pa~Og#|X!e#|X~P)YO!e&mOg$ga~Oo'eO~Og'kO~P)YOg'lO!W'mO~O$]'nOg#{X!e#{X~P/VO!e&uOg$oa~Og'sO~OS!fOh'uO~OS!fO~PGUO`'yOg'{O~OS#zac#zad#zah#za!Y#za!]#za!`#za$]#za~Og'}O~P!![Og'}On(OO~Oy$cOQ$Pah$Pan$Pap$Pas$Pau$Paw$Pa}$Pa!e$Pa!q$Pa$X$Pa$Z$Pa$[$Pa$j$Pa$s$Par$Pa~Oo(TO~Og#}a!e#}a~P&^Og#|a!e#|a~P)YOR#zOu#zOw#zO$]&sO$j&WO~Oc!fXg!QX!U!QX!e!QX~O!U#}Og#{a!e#{a~Oc(VO~O!e&uOg$oi~P)YOg!ai!U!ji~Og(XO~O!W(ZOg!ni~Og!li~P)YO`'yOg(^O~Oy$cOg!Pin!Pi~Og(_O~P!![On(`O~Og(aO~O!e&uOg$oq~Og(cO~OS!fO~P!$wOg#{q!e#{q~P)YO$_!r$`$j`$jy#S~",
	goto: "7g$uPPPPP$vP$yP%S%f%S%x&[P%SP&b%SPP&hPPP&n&x&xPPPPP&xPP&xP'hP&xP&x(k&xP)Z)^)d)d)v)dP)dP)dP)d)dP*])dP*i*o+e+hP+k*i+n*i+q+w+z,Q+z)d,WPP,|-S%S-Y%S-`-`-f-jPP%SP%S%SP-p.l.y/Q$yP/ZP/^P$yP$yP$yP/d$yP/g/j/m/t$yP$yPP$yP/y$yP/|0S0c0}1]1c1m1s1y2P2V2a2g2m2s2y3PPPPPPPPPPPP3V3`P4U4X5]P5e6_6t+z7Q7T7WPP7^RrQ_aOPco!R#[%Pq_OP]^co|}!O!P!R#S#[#p%P&iqSOP]^co|}!O!P!R#S#[#p%P&iqUOP]^co|}!O!P!R#S#[#p%P&iQtTR#buQwWR#cxQ!VYR#dyQ#d!XS$h!t!uR%U#f!Z!xdf!n!o!p#Z#q#v$[$^$`$c${%W%]%a&c&d&m&r&w'O'T'i'r'x(V(b!Y!xdf!n!o!p#Z#q#v$[$^$`$c${%W%]%a&c&d&m&r&w'O'T'i'r'x(V(bb#z!c$W%b%m&z&}'m'u(ZU&Z$r&]'[R'Z&Y!Z!tdf!n!o!p#Z#q#v$[$^$`$c${%W%]%a&c&d&m&r&w'O'T'i'r'x(V(bR$j!vQ&P$iR'W&Qq!h`ei!c!d!e!r#}$O$P$S$g$i$l&Q&uQ#x!cW%s$W%m&z'uQ&t%bQ'w&}Q(U'mR(d(ZQ#VjQ$V!jQ$v#UR&a$xX%q$W%m&z'up!h`ei!c!d!e!r#}$O$P$S$g$i$l&Q&uW%p$W%m&z'uQ&{%nQ'v&|Q'w&}R(d(ZR$T!fR%j$SR'p&uR&{%nX%o$W%m&z'uR'v&|X%t$W%m&z'uX%r$W%m&z'u!Y!xdf!n!o!p#Z#q#v$[$^$`$c${%W%]%a&c&d&m&r&w'O'T'i'r'x(V(bQ!}gR$q#OQ!WYR#eyQ#d!WR%U#eQ!ZZR#gzQ!_[R#h{T!^[{Q#s!]R%_#tQ!SXQ!i`Q#TjQ#n!QQ$Q!dQ$n!zQ$t#RQ$w#VQ$z#YQ%g$PQ&`$vQ'^&[Q'a&aR(S']SnP!RQ#^oQ%O#[R&g%PZmPo!R#[%PQ$}#ZQ&e${R'd&dR$g!rQ'R%{R(['yR#OgR#QhR$s#QS&[$r&]R(Q'[V&Y$r&]'[R#YkQ#`qR%S#`QcOSoP!RU!lco%PR%P#[Q%]#q[&l%]&r'i'r'x(bQ&r%aQ'i&mQ'r&wQ'x'OR(b(VQ$[!nQ$^!oQ$`!pV%v$[$^$`Q&Q$iR'X&QQ&v%iS'q&v(WR(W'rQ&n%]R'j&nQ&j%YR'h&jQ!QXR#m!QQ&d${R'c&dQ#]nS%Q#]%RR%R#^Q'z'RR(]'zQ$m!yR&U$mQ&]$rR'_&]Q']&[R(R']Q#XkR$y#XQ$P!dR%f$P_bOPco!R#[%P^XOPco!R#[%PQ!`]Q!a^Q#i|Q#j}Q#k!OQ#l!PQ$u#SQ%Y#pR'g&iR%^#qQ!rdQ!{f[$X!n!o!p$[$^$`Q${#Zh%[#q%]%a&m&r&w'O'i'r'x(V(bQ%`#vQ%z$cS&b${&dQ&h%WQ'b&cR'|'T]$Z!n!o!p$[$^$`Q!d`U!ye!r$gQ#RiQ#y!cS#|!d$PQ$R!eQ%d#}Q%e$OQ%i$SS&O$i&QQ&T$lR'o&uQ#{!cW%s$W%m&z'uQ&t%bQ'w&}Q(U'mR(d(ZQ%u$WQ&y%mQ't&zR(Y'uR%k$SR%Z#pQpPR#o!RQ!zeQ$f!rR%}$g",
	nodeNames: "⚠ Unit VariableName VariableName QueryCallee Comment StyleSheet RuleSet UniversalSelector TagSelector TagName NamespacedTagSelector NamespaceName TagName NestingSelector ClassSelector . ClassName PseudoClassSelector : :: PseudoClassName PseudoClassName ) ( ArgList ValueName ParenthesizedValue AtKeyword # ; ] [ BracketedValue } { BracedValue ColorLiteral NumberLiteral StringLiteral BinaryExpression BinOp CallExpression Callee IfExpression if ArgList IfBranch KeywordQuery FeatureQuery FeatureName BinaryQuery LogicOp ComparisonQuery CompareOp UnaryQuery UnaryQueryOp ParenthesizedQuery SelectorQuery selector ParenthesizedSelector StyleQuery style ParenthesedQuery CallQuery ArgList PropertyName , PropertyName UnaryQuery ParenthesedQuery BinaryQuery ParenthesedQuery ParenthesedQuery StyleFeature PropertyName StyleRange PseudoQuery CallLiteral CallTag ParenthesizedContent PseudoClassName ArgList IdSelector IdName AttributeSelector AttributeName NamespacedAttribute NamespaceName AttributeName MatchOp MatchFlag ChildSelector ChildOp DescendantSelector SiblingSelector SiblingOp Block Declaration PropertyName Important ImportStatement import Layer layer LayerName layer MediaStatement media CharsetStatement charset NamespaceStatement namespace NamespaceName KeyframesStatement keyframes KeyframeName KeyframeList KeyframeSelector KeyframeRangeName SupportsStatement supports ScopeStatement scope to FontFeatureStatement font-feature-values FontName AtRule Styles",
	maxTerm: 174,
	nodeProps: [
		[
			"isolate",
			-2,
			5,
			39,
			""
		],
		[
			"openedBy",
			23,
			"(",
			31,
			"[",
			34,
			"{"
		],
		[
			"closedBy",
			24,
			")",
			32,
			"]",
			35,
			"}"
		]
	],
	propSources: [D_],
	skippedNodes: [
		0,
		5,
		130
	],
	repeatNodeCount: 17,
	tokenData: "K`~R!bOX%ZX^&R^p%Zpq&Rqr)ers)vst+jtu2Xuv%Zvw3Rwx3dxy5Ryz5dz{5i{|6S|}:u}!O;W!O!P;u!P!Q<^!Q![=V![!]>Q!]!^>|!^!_?_!_!`@Z!`!a@n!a!b%Z!b!cAo!c!k%Z!k!lC|!l!u%Z!u!vC|!v!}%Z!}#OD_#O#P%Z#P#QDp#Q#R2X#R#]%Z#]#^ER#^#g%Z#g#hC|#h#o%Z#o#pIf#p#qIw#q#rJ`#r#sJq#s#y%Z#y#z&R#z$f%Z$f$g&R$g#BY%Z#BY#BZ&R#BZ$IS%Z$IS$I_&R$I_$I|%Z$I|$JO&R$JO$JT%Z$JT$JU&R$JU$KV%Z$KV$KW&R$KW&FU%Z&FU&FV&R&FV;'S%Z;'S;=`KY<%lO%Z`%^SOy%jz;'S%j;'S;=`%{<%lO%j`%oS!r`Oy%jz;'S%j;'S;=`%{<%lO%j`&OP;=`<%l%j~&Wh$_~OX%jX^'r^p%jpq'rqy%jz#y%j#y#z'r#z$f%j$f$g'r$g#BY%j#BY#BZ'r#BZ$IS%j$IS$I_'r$I_$I|%j$I|$JO'r$JO$JT%j$JT$JU'r$JU$KV%j$KV$KW'r$KW&FU%j&FU&FV'r&FV;'S%j;'S;=`%{<%lO%j~'yh$_~!r`OX%jX^'r^p%jpq'rqy%jz#y%j#y#z'r#z$f%j$f$g'r$g#BY%j#BY#BZ'r#BZ$IS%j$IS$I_'r$I_$I|%j$I|$JO'r$JO$JT%j$JT$JU'r$JU$KV%j$KV$KW'r$KW&FU%j&FU&FV'r&FV;'S%j;'S;=`%{<%lO%jj)jS$sYOy%jz;'S%j;'S;=`%{<%lO%j~)yWOY)vZr)vrs*cs#O)v#O#P*h#P;'S)v;'S;=`+d<%lO)v~*hOw~~*kRO;'S)v;'S;=`*t;=`O)v~*wXOY)vZr)vrs*cs#O)v#O#P*h#P;'S)v;'S;=`+d;=`<%l)v<%lO)v~+gP;=`<%l)vj+oYmYOy%jz!Q%j!Q![,_![!c%j!c!i,_!i#T%j#T#Z,_#Z;'S%j;'S;=`%{<%lO%jj,dY!r`Oy%jz!Q%j!Q![-S![!c%j!c!i-S!i#T%j#T#Z-S#Z;'S%j;'S;=`%{<%lO%jj-XY!r`Oy%jz!Q%j!Q![-w![!c%j!c!i-w!i#T%j#T#Z-w#Z;'S%j;'S;=`%{<%lO%jj.OYuY!r`Oy%jz!Q%j!Q![.n![!c%j!c!i.n!i#T%j#T#Z.n#Z;'S%j;'S;=`%{<%lO%jj.uYuY!r`Oy%jz!Q%j!Q![/e![!c%j!c!i/e!i#T%j#T#Z/e#Z;'S%j;'S;=`%{<%lO%jj/jY!r`Oy%jz!Q%j!Q![0Y![!c%j!c!i0Y!i#T%j#T#Z0Y#Z;'S%j;'S;=`%{<%lO%jj0aYuY!r`Oy%jz!Q%j!Q![1P![!c%j!c!i1P!i#T%j#T#Z1P#Z;'S%j;'S;=`%{<%lO%jj1UY!r`Oy%jz!Q%j!Q![1t![!c%j!c!i1t!i#T%j#T#Z1t#Z;'S%j;'S;=`%{<%lO%jj1{SuY!r`Oy%jz;'S%j;'S;=`%{<%lO%jd2[UOy%jz!_%j!_!`2n!`;'S%j;'S;=`%{<%lO%jd2uS!|S!r`Oy%jz;'S%j;'S;=`%{<%lO%jb3WS^QOy%jz;'S%j;'S;=`%{<%lO%j~3gWOY3dZw3dwx*cx#O3d#O#P4P#P;'S3d;'S;=`4{<%lO3d~4SRO;'S3d;'S;=`4];=`O3d~4`XOY3dZw3dwx*cx#O3d#O#P4P#P;'S3d;'S;=`4{;=`<%l3d<%lO3d~5OP;=`<%l3dj5WShYOy%jz;'S%j;'S;=`%{<%lO%j~5iOg~n5pUWQyWOy%jz!_%j!_!`2n!`;'S%j;'S;=`%{<%lO%jj6ZWyW#SQOy%jz!O%j!O!P6s!P!Q%j!Q![9x![;'S%j;'S;=`%{<%lO%jj6xU!r`Oy%jz!Q%j!Q![7[![;'S%j;'S;=`%{<%lO%jj7cY!r`$jYOy%jz!Q%j!Q![7[![!g%j!g!h8R!h#X%j#X#Y8R#Y;'S%j;'S;=`%{<%lO%jj8WY!r`Oy%jz{%j{|8v|}%j}!O8v!O!Q%j!Q![9_![;'S%j;'S;=`%{<%lO%jj8{U!r`Oy%jz!Q%j!Q![9_![;'S%j;'S;=`%{<%lO%jj9fU!r`$jYOy%jz!Q%j!Q![9_![;'S%j;'S;=`%{<%lO%jj:P[!r`$jYOy%jz!O%j!O!P7[!P!Q%j!Q![9x![!g%j!g!h8R!h#X%j#X#Y8R#Y;'S%j;'S;=`%{<%lO%jj:zS!eYOy%jz;'S%j;'S;=`%{<%lO%jj;]WyWOy%jz!O%j!O!P6s!P!Q%j!Q![9x![;'S%j;'S;=`%{<%lO%jj;zU`YOy%jz!Q%j!Q![7[![;'S%j;'S;=`%{<%lO%j~<cTyWOy%jz{<r{;'S%j;'S;=`%{<%lO%j~<yS!r`$`~Oy%jz;'S%j;'S;=`%{<%lO%jj=[[$jYOy%jz!O%j!O!P7[!P!Q%j!Q![9x![!g%j!g!h8R!h#X%j#X#Y8R#Y;'S%j;'S;=`%{<%lO%jj>VUcYOy%jz![%j![!]>i!];'S%j;'S;=`%{<%lO%jj>pSdY!r`Oy%jz;'S%j;'S;=`%{<%lO%jj?RSnYOy%jz;'S%j;'S;=`%{<%lO%jh?dU!WWOy%jz!_%j!_!`?v!`;'S%j;'S;=`%{<%lO%jh?}S!WW!r`Oy%jz;'S%j;'S;=`%{<%lO%jl@bS!WW!|SOy%jz;'S%j;'S;=`%{<%lO%jj@uV#PQ!WWOy%jz!_%j!_!`?v!`!aA[!a;'S%j;'S;=`%{<%lO%jbAcS#PQ!r`Oy%jz;'S%j;'S;=`%{<%lO%jjArYOy%jz}%j}!OBb!O!c%j!c!}CP!}#T%j#T#oCP#o;'S%j;'S;=`%{<%lO%jjBgW!r`Oy%jz!c%j!c!}CP!}#T%j#T#oCP#o;'S%j;'S;=`%{<%lO%jjCW[lY!r`Oy%jz}%j}!OCP!O!Q%j!Q![CP![!c%j!c!}CP!}#T%j#T#oCP#o;'S%j;'S;=`%{<%lO%jhDRS!}WOy%jz;'S%j;'S;=`%{<%lO%jjDdSpYOy%jz;'S%j;'S;=`%{<%lO%jnDuSo^Oy%jz;'S%j;'S;=`%{<%lO%jjEWU!}WOy%jz#a%j#a#bEj#b;'S%j;'S;=`%{<%lO%jbEoU!r`Oy%jz#d%j#d#eFR#e;'S%j;'S;=`%{<%lO%jbFWU!r`Oy%jz#c%j#c#dFj#d;'S%j;'S;=`%{<%lO%jbFoU!r`Oy%jz#f%j#f#gGR#g;'S%j;'S;=`%{<%lO%jbGWU!r`Oy%jz#h%j#h#iGj#i;'S%j;'S;=`%{<%lO%jbGoU!r`Oy%jz#T%j#T#UHR#U;'S%j;'S;=`%{<%lO%jbHWU!r`Oy%jz#b%j#b#cHj#c;'S%j;'S;=`%{<%lO%jbHoU!r`Oy%jz#h%j#h#iIR#i;'S%j;'S;=`%{<%lO%jbIYS$rQ!r`Oy%jz;'S%j;'S;=`%{<%lO%jjIkSsYOy%jz;'S%j;'S;=`%{<%lO%jfI|U$fUOy%jz!_%j!_!`2n!`;'S%j;'S;=`%{<%lO%jjJeSrYOy%jz;'S%j;'S;=`%{<%lO%jfJvU#SQOy%jz!_%j!_!`2n!`;'S%j;'S;=`%{<%lO%j`K]P;=`<%l%Z",
	tokenizers: [
		T_,
		E_,
		C_,
		w_,
		1,
		2,
		3,
		4,
		new Ch("m~RRYZ[z{a~~g~aO$b~~dP!P!Qg~lO$c~~", 28, 155)
	],
	topRules: {
		StyleSheet: [0, 6],
		Styles: [1, 129]
	},
	dynamicPrecedences: { 97: 1 },
	specialized: [
		{
			term: 150,
			get: (e) => O_[e] || -1
		},
		{
			term: 151,
			get: (e) => k_[e] || -1
		},
		{
			term: 4,
			get: (e) => A_[e] || -1
		},
		{
			term: 28,
			get: (e) => j_[e] || -1
		},
		{
			term: 149,
			get: (e) => M_[e] || -1
		}
	],
	tokenPrec: 2444
}), P_ = null;
function F_() {
	if (!P_ && typeof document == "object" && document.body) {
		let { style: e } = document.body, t = [], n = /* @__PURE__ */ new Set();
		for (let r in e) r != "cssText" && r != "cssFloat" && typeof e[r] == "string" && (/[A-Z]/.test(r) && (r = r.replace(/[A-Z]/g, (e) => "-" + e.toLowerCase())), n.has(r) || (t.push(r), n.add(r)));
		P_ = t.sort().map((e) => ({
			type: "property",
			label: e,
			apply: e + ": "
		}));
	}
	return P_ || [];
}
var I_ = /*@__PURE__*/ (/* @__PURE__ */ "active.after.any-link.autofill.backdrop.before.checked.cue.default.defined.disabled.empty.enabled.file-selector-button.first.first-child.first-letter.first-line.first-of-type.focus.focus-visible.focus-within.fullscreen.has.host.host-context.hover.in-range.indeterminate.invalid.is.lang.last-child.last-of-type.left.link.marker.modal.not.nth-child.nth-last-child.nth-last-of-type.nth-of-type.only-child.only-of-type.optional.out-of-range.part.placeholder.placeholder-shown.read-only.read-write.required.right.root.scope.selection.slotted.target.target-text.valid.visited.where".split(".")).map((e) => ({
	type: "class",
	label: e
})), L_ = /*@__PURE__*/ (/* @__PURE__ */ "above.absolute.activeborder.additive.activecaption.after-white-space.ahead.alias.all.all-scroll.alphabetic.alternate.always.antialiased.appworkspace.asterisks.attr.auto.auto-flow.avoid.avoid-column.avoid-page.avoid-region.axis-pan.background.backwards.baseline.below.bidi-override.blink.block.block-axis.bold.bolder.border.border-box.both.bottom.break.break-all.break-word.bullets.button.button-bevel.buttonface.buttonhighlight.buttonshadow.buttontext.calc.capitalize.caps-lock-indicator.caption.captiontext.caret.cell.center.checkbox.circle.cjk-decimal.clear.clip.close-quote.col-resize.collapse.color.color-burn.color-dodge.column.column-reverse.compact.condensed.contain.content.contents.content-box.context-menu.continuous.copy.counter.counters.cover.crop.cross.crosshair.currentcolor.cursive.cyclic.darken.dashed.decimal.decimal-leading-zero.default.default-button.dense.destination-atop.destination-in.destination-out.destination-over.difference.disc.discard.disclosure-closed.disclosure-open.document.dot-dash.dot-dot-dash.dotted.double.down.e-resize.ease.ease-in.ease-in-out.ease-out.element.ellipse.ellipsis.embed.end.ethiopic-abegede-gez.ethiopic-halehame-aa-er.ethiopic-halehame-gez.ew-resize.exclusion.expanded.extends.extra-condensed.extra-expanded.fantasy.fast.fill.fill-box.fixed.flat.flex.flex-end.flex-start.footnotes.forwards.from.geometricPrecision.graytext.grid.groove.hand.hard-light.help.hidden.hide.higher.highlight.highlighttext.horizontal.hsl.hsla.hue.icon.ignore.inactiveborder.inactivecaption.inactivecaptiontext.infinite.infobackground.infotext.inherit.initial.inline.inline-axis.inline-block.inline-flex.inline-grid.inline-table.inset.inside.intrinsic.invert.italic.justify.keep-all.landscape.large.larger.left.level.lighter.lighten.line-through.linear.linear-gradient.lines.list-item.listbox.listitem.local.logical.loud.lower.lower-hexadecimal.lower-latin.lower-norwegian.lowercase.ltr.luminosity.manipulation.match.matrix.matrix3d.medium.menu.menutext.message-box.middle.min-intrinsic.mix.monospace.move.multiple.multiple_mask_images.multiply.n-resize.narrower.ne-resize.nesw-resize.no-close-quote.no-drop.no-open-quote.no-repeat.none.normal.not-allowed.nowrap.ns-resize.numbers.numeric.nw-resize.nwse-resize.oblique.opacity.open-quote.optimizeLegibility.optimizeSpeed.outset.outside.outside-shape.overlay.overline.padding.padding-box.painted.page.paused.perspective.pinch-zoom.plus-darker.plus-lighter.pointer.polygon.portrait.pre.pre-line.pre-wrap.preserve-3d.progress.push-button.radial-gradient.radio.read-only.read-write.read-write-plaintext-only.rectangle.region.relative.repeat.repeating-linear-gradient.repeating-radial-gradient.repeat-x.repeat-y.reset.reverse.rgb.rgba.ridge.right.rotate.rotate3d.rotateX.rotateY.rotateZ.round.row.row-resize.row-reverse.rtl.run-in.running.s-resize.sans-serif.saturation.scale.scale3d.scaleX.scaleY.scaleZ.screen.scroll.scrollbar.scroll-position.se-resize.self-start.self-end.semi-condensed.semi-expanded.separate.serif.show.single.skew.skewX.skewY.skip-white-space.slide.slider-horizontal.slider-vertical.sliderthumb-horizontal.sliderthumb-vertical.slow.small.small-caps.small-caption.smaller.soft-light.solid.source-atop.source-in.source-out.source-over.space.space-around.space-between.space-evenly.spell-out.square.start.static.status-bar.stretch.stroke.stroke-box.sub.subpixel-antialiased.svg_masks.super.sw-resize.symbolic.symbols.system-ui.table.table-caption.table-cell.table-column.table-column-group.table-footer-group.table-header-group.table-row.table-row-group.text.text-bottom.text-top.textarea.textfield.thick.thin.threeddarkshadow.threedface.threedhighlight.threedlightshadow.threedshadow.to.top.transform.translate.translate3d.translateX.translateY.translateZ.transparent.ultra-condensed.ultra-expanded.underline.unidirectional-pan.unset.up.upper-latin.uppercase.url.var.vertical.vertical-text.view-box.visible.visibleFill.visiblePainted.visibleStroke.visual.w-resize.wait.wave.wider.window.windowframe.windowtext.words.wrap.wrap-reverse.x-large.x-small.xor.xx-large.xx-small".split(".")).map((e) => ({
	type: "keyword",
	label: e
})).concat(/*@__PURE__*/ (/* @__PURE__ */ "aliceblue.antiquewhite.aqua.aquamarine.azure.beige.bisque.black.blanchedalmond.blue.blueviolet.brown.burlywood.cadetblue.chartreuse.chocolate.coral.cornflowerblue.cornsilk.crimson.cyan.darkblue.darkcyan.darkgoldenrod.darkgray.darkgreen.darkkhaki.darkmagenta.darkolivegreen.darkorange.darkorchid.darkred.darksalmon.darkseagreen.darkslateblue.darkslategray.darkturquoise.darkviolet.deeppink.deepskyblue.dimgray.dodgerblue.firebrick.floralwhite.forestgreen.fuchsia.gainsboro.ghostwhite.gold.goldenrod.gray.grey.green.greenyellow.honeydew.hotpink.indianred.indigo.ivory.khaki.lavender.lavenderblush.lawngreen.lemonchiffon.lightblue.lightcoral.lightcyan.lightgoldenrodyellow.lightgray.lightgreen.lightpink.lightsalmon.lightseagreen.lightskyblue.lightslategray.lightsteelblue.lightyellow.lime.limegreen.linen.magenta.maroon.mediumaquamarine.mediumblue.mediumorchid.mediumpurple.mediumseagreen.mediumslateblue.mediumspringgreen.mediumturquoise.mediumvioletred.midnightblue.mintcream.mistyrose.moccasin.navajowhite.navy.oldlace.olive.olivedrab.orange.orangered.orchid.palegoldenrod.palegreen.paleturquoise.palevioletred.papayawhip.peachpuff.peru.pink.plum.powderblue.purple.rebeccapurple.red.rosybrown.royalblue.saddlebrown.salmon.sandybrown.seagreen.seashell.sienna.silver.skyblue.slateblue.slategray.snow.springgreen.steelblue.tan.teal.thistle.tomato.turquoise.violet.wheat.white.whitesmoke.yellow.yellowgreen".split(".")).map((e) => ({
	type: "constant",
	label: e
}))), R_ = /*@__PURE__*/ (/* @__PURE__ */ "a.abbr.address.article.aside.b.bdi.bdo.blockquote.body.br.button.canvas.caption.cite.code.col.colgroup.dd.del.details.dfn.dialog.div.dl.dt.em.figcaption.figure.footer.form.header.hgroup.h1.h2.h3.h4.h5.h6.hr.html.i.iframe.img.input.ins.kbd.label.legend.li.main.meter.nav.ol.output.p.pre.ruby.section.select.small.source.span.strong.sub.summary.sup.table.tbody.td.template.textarea.tfoot.th.thead.tr.u.ul".split(".")).map((e) => ({
	type: "type",
	label: e
})), z_ = /*@__PURE__*/ [
	"@charset",
	"@color-profile",
	"@container",
	"@counter-style",
	"@font-face",
	"@font-feature-values",
	"@font-palette-values",
	"@import",
	"@keyframes",
	"@layer",
	"@media",
	"@namespace",
	"@page",
	"@position-try",
	"@property",
	"@scope",
	"@starting-style",
	"@supports",
	"@view-transition"
].map((e) => ({
	type: "keyword",
	label: e
})), B_ = /^(\w[\w-]*|-\w[\w-]*|)$/, V_ = /^-(-[\w-]*)?$/;
function H_(e, t) {
	if ((e.name == "(" || e.type.isError) && (e = e.parent || e), e.name != "ArgList") return !1;
	let n = e.parent?.firstChild;
	return n?.name == "Callee" && t.sliceString(n.from, n.to) == "var";
}
var U_ = /*@__PURE__*/ new Js(), W_ = ["Declaration"];
function G_(e) {
	for (let t = e;;) {
		if (t.type.isTop) return t;
		if (!(t = t.parent)) return e;
	}
}
function K_(e, t, n) {
	if (t.to - t.from > 4096) {
		let r = U_.get(t);
		if (r) return r;
		let i = [], a = /* @__PURE__ */ new Set(), o = t.cursor(W.IncludeAnonymous);
		if (o.firstChild()) do
			for (let t of K_(e, o.node, n)) a.has(t.label) || (a.add(t.label), i.push(t));
		while (o.nextSibling());
		return U_.set(t, i), i;
	}
	{
		let r = [], i = /* @__PURE__ */ new Set();
		return t.cursor().iterate((t) => {
			if (n(t) && t.matchContext(W_) && t.node.nextSibling?.name == ":") {
				let n = e.sliceString(t.from, t.to);
				i.has(n) || (i.add(n), r.push({
					label: n,
					type: "variable"
				}));
			}
		}), r;
	}
}
var q_ = /*@__PURE__*/ ((e) => (t) => {
	let { state: n, pos: r } = t, i = J(n).resolveInner(r, -1), a = i.type.isError && i.from == i.to - 1 && n.doc.sliceString(i.from, i.to) == "-";
	if (i.name == "PropertyName" || (a || i.name == "TagName") && /^(Block|Styles)$/.test(i.resolve(i.to).name)) return {
		from: i.from,
		options: F_(),
		validFor: B_
	};
	if (i.name == "ValueName") return {
		from: i.from,
		options: L_,
		validFor: B_
	};
	if (i.name == "PseudoClassName") return {
		from: i.from,
		options: I_,
		validFor: B_
	};
	if (e(i) || (t.explicit || a) && H_(i, n.doc)) return {
		from: e(i) || a ? i.from : r,
		options: K_(n.doc, G_(i), e),
		validFor: V_
	};
	if (i.name == "TagName") {
		for (let { parent: e } = i; e; e = e.parent) if (e.name == "Block") return {
			from: i.from,
			options: F_(),
			validFor: B_
		};
		return {
			from: i.from,
			options: R_,
			validFor: B_
		};
	}
	if (i.name == "AtKeyword") return {
		from: i.from,
		options: z_,
		validFor: B_
	};
	if (!t.explicit) return null;
	let o = i.resolve(r), s = o.childBefore(r);
	return s && s.name == ":" && o.name == "PseudoClassSelector" ? {
		from: r,
		options: I_,
		validFor: B_
	} : s && s.name == ":" && o.name == "Declaration" || o.name == "ArgList" ? {
		from: r,
		options: L_,
		validFor: B_
	} : o.name == "Block" || o.name == "Styles" ? {
		from: r,
		options: F_(),
		validFor: B_
	} : null;
})((e) => e.name == "VariableName"), J_ = /*@__PURE__*/ Gc.define({
	name: "css",
	parser: /*@__PURE__*/ N_.configure({ props: [/*@__PURE__*/ ul.add({ Declaration: /*@__PURE__*/ Sl() }), /*@__PURE__*/ wl.add({ "Block KeyframeList": Tl })] }),
	languageData: {
		commentTokens: { block: {
			open: "/*",
			close: "*/"
		} },
		indentOnInput: /^\s*\}$/,
		wordChars: "-"
	}
});
function Y_() {
	return new nl(J_, J_.data.of({ autocomplete: q_ }));
}
//#endregion
//#region node_modules/@lezer/javascript/dist/index.js
var X_ = 316, Z_ = 317, Q_ = 1, $_ = 2, ev = 3, tv = 4, nv = 318, rv = 320, iv = 321, av = 5, ov = 6, sv = 0, cv = [
	9,
	10,
	11,
	12,
	13,
	32,
	133,
	160,
	5760,
	8192,
	8193,
	8194,
	8195,
	8196,
	8197,
	8198,
	8199,
	8200,
	8201,
	8202,
	8232,
	8233,
	8239,
	8287,
	12288
], lv = 125, uv = 59, dv = 47, fv = 42, pv = 43, mv = 45, hv = 60, gv = 44, _v = 63, vv = 46, yv = 91, bv = new Lh({
	start: !1,
	shift(e, t) {
		return t == av || t == ov || t == rv ? e : t == iv;
	},
	strict: !1
}), xv = new wh((e, t) => {
	let { next: n } = e;
	(n == lv || n == -1 || t.context) && e.acceptToken(nv);
}, {
	contextual: !0,
	fallback: !0
}), Sv = new wh((e, t) => {
	let { next: n } = e, r;
	cv.indexOf(n) > -1 || (n != dv || (r = e.peek(1)) != dv && r != fv) && n != lv && n != uv && n != -1 && !t.context && e.acceptToken(X_);
}, { contextual: !0 }), Cv = new wh((e, t) => {
	e.next == yv && !t.context && e.acceptToken(Z_);
}, { contextual: !0 }), wv = new wh((e, t) => {
	let { next: n } = e;
	if (n == pv || n == mv) {
		if (e.advance(), n == e.next) {
			e.advance();
			let n = !t.context && t.canShift(Q_);
			e.acceptToken(n ? Q_ : $_);
		}
	} else n == _v && e.peek(1) == vv && (e.advance(), e.advance(), (e.next < 48 || e.next > 57) && e.acceptToken(ev));
}, { contextual: !0 });
function Tv(e, t) {
	return e >= 65 && e <= 90 || e >= 97 && e <= 122 || e == 95 || e >= 192 || !t && e >= 48 && e <= 57;
}
var Ev = new wh((e, t) => {
	if (e.next != hv || !t.dialectEnabled(sv) || (e.advance(), e.next == dv)) return;
	let n = 0;
	for (; cv.indexOf(e.next) > -1;) e.advance(), n++;
	if (Tv(e.next, !0)) {
		for (e.advance(), n++; Tv(e.next, !1);) e.advance(), n++;
		for (; cv.indexOf(e.next) > -1;) e.advance(), n++;
		if (e.next == gv) return;
		for (let t = 0;; t++) {
			if (t == 7) {
				if (!Tv(e.next, !0)) return;
				break;
			}
			if (e.next != "extends".charCodeAt(t)) break;
			e.advance(), n++;
		}
	}
	e.acceptToken(tv, -n);
}), Dv = vc({
	"get set async static": q.modifier,
	"for while do if else switch try catch finally return throw break continue default case defer": q.controlKeyword,
	"in of await yield void typeof delete instanceof as satisfies": q.operatorKeyword,
	"let var const using function class extends": q.definitionKeyword,
	"import export from": q.moduleKeyword,
	"with debugger new": q.keyword,
	TemplateString: q.special(q.string),
	super: q.atom,
	BooleanLiteral: q.bool,
	this: q.self,
	null: q.null,
	Star: q.modifier,
	VariableName: q.variableName,
	"CallExpression/VariableName TaggedTemplateExpression/VariableName": q.function(q.variableName),
	VariableDefinition: q.definition(q.variableName),
	Label: q.labelName,
	PropertyName: q.propertyName,
	PrivatePropertyName: q.special(q.propertyName),
	"CallExpression/MemberExpression/PropertyName": q.function(q.propertyName),
	"FunctionDeclaration/VariableDefinition": q.function(q.definition(q.variableName)),
	"ClassDeclaration/VariableDefinition": q.definition(q.className),
	"NewExpression/VariableName": q.className,
	PropertyDefinition: q.definition(q.propertyName),
	PrivatePropertyDefinition: q.definition(q.special(q.propertyName)),
	UpdateOp: q.updateOperator,
	"LineComment Hashbang": q.lineComment,
	BlockComment: q.blockComment,
	Number: q.number,
	String: q.string,
	Escape: q.escape,
	ArithOp: q.arithmeticOperator,
	LogicOp: q.logicOperator,
	BitOp: q.bitwiseOperator,
	CompareOp: q.compareOperator,
	RegExp: q.regexp,
	Equals: q.definitionOperator,
	Arrow: q.function(q.punctuation),
	": Spread": q.punctuation,
	"( )": q.paren,
	"[ ]": q.squareBracket,
	"{ }": q.brace,
	"InterpolationStart InterpolationEnd": q.special(q.brace),
	".": q.derefOperator,
	", ;": q.separator,
	"@": q.meta,
	TypeName: q.typeName,
	TypeDefinition: q.definition(q.typeName),
	"type enum interface implements namespace module declare": q.definitionKeyword,
	"abstract global Privacy readonly override": q.modifier,
	"is keyof unique infer asserts": q.operatorKeyword,
	JSXAttributeValue: q.attributeValue,
	JSXText: q.content,
	"JSXStartTag JSXStartCloseTag JSXSelfCloseEndTag JSXEndTag": q.angleBracket,
	"JSXIdentifier JSXNameSpacedName": q.tagName,
	"JSXAttribute/JSXIdentifier JSXAttribute/JSXNameSpacedName": q.attributeName,
	"JSXBuiltin/JSXIdentifier": q.standard(q.tagName)
}), Ov = {
	__proto__: null,
	export: 20,
	as: 25,
	from: 33,
	default: 36,
	async: 41,
	function: 42,
	in: 52,
	out: 55,
	const: 56,
	extends: 60,
	this: 64,
	true: 72,
	false: 72,
	null: 84,
	void: 88,
	typeof: 92,
	super: 108,
	new: 142,
	delete: 154,
	yield: 163,
	await: 167,
	class: 172,
	public: 235,
	private: 235,
	protected: 235,
	readonly: 237,
	instanceof: 256,
	satisfies: 259,
	import: 292,
	keyof: 349,
	unique: 353,
	infer: 359,
	asserts: 395,
	is: 397,
	abstract: 417,
	implements: 419,
	type: 421,
	let: 424,
	var: 426,
	using: 429,
	interface: 435,
	enum: 439,
	namespace: 445,
	module: 447,
	declare: 451,
	global: 455,
	defer: 471,
	for: 476,
	of: 485,
	while: 488,
	with: 492,
	do: 496,
	if: 500,
	else: 502,
	switch: 506,
	case: 512,
	try: 518,
	catch: 522,
	finally: 526,
	return: 530,
	throw: 534,
	break: 538,
	continue: 542,
	debugger: 546
}, kv = {
	__proto__: null,
	async: 129,
	get: 131,
	set: 133,
	declare: 195,
	public: 197,
	private: 197,
	protected: 197,
	static: 199,
	abstract: 201,
	override: 203,
	readonly: 209,
	accessor: 211,
	new: 401
}, Av = {
	__proto__: null,
	"<": 193
}, jv = Rh.deserialize({
	version: 14,
	states: "$F|Q%TQlOOO%[QlOOO'_QpOOP(lO`OOO*zQ!0MxO'#CiO+RO#tO'#CjO+aO&jO'#CjO+oO#@ItO'#DaO.QQlO'#DgO.bQlO'#DrO%[QlO'#DzO0fQlO'#ESOOQ!0Lf'#E['#E[O1PQ`O'#EXOOQO'#Ep'#EpOOQO'#Il'#IlO1XQ`O'#GsO1dQ`O'#EoO1iQ`O'#EoO3hQ!0MxO'#JrO6[Q!0MxO'#JsO6uQ`O'#F]O6zQ,UO'#FtOOQ!0Lf'#Ff'#FfO7VO7dO'#FfO9XQMhO'#F|O9`Q`O'#F{OOQ!0Lf'#Js'#JsOOQ!0Lb'#Jr'#JrO9eQ`O'#GwOOQ['#K_'#K_O9pQ`O'#IYO9uQ!0LrO'#IZOOQ['#J`'#J`OOQ['#I_'#I_Q`QlOOQ`QlOOO9}Q!L^O'#DvO:UQlO'#EOO:]QlO'#EQO9kQ`O'#GsO:dQMhO'#CoO:rQ`O'#EnO:}Q`O'#EyO;hQMhO'#FeO;xQ`O'#GsOOQO'#K`'#K`O;}Q`O'#K`O<]Q`O'#G{O<]Q`O'#G|O<]Q`O'#HOO9kQ`O'#HRO=SQ`O'#HUO>kQ`O'#CeO>{Q`O'#HcO?TQ`O'#HiO?TQ`O'#HkO`QlO'#HmO?TQ`O'#HoO?TQ`O'#HrO?YQ`O'#HxO?_Q!0LsO'#IOO%[QlO'#IQO?jQ!0LsO'#ISO?uQ!0LsO'#IUO9uQ!0LrO'#IWO@QQ!0MxO'#CiOASQpO'#DlQOQ`OOO%[QlO'#EQOAjQ`O'#ETO:dQMhO'#EnOAuQ`O'#EnOBQQ!bO'#FeOOQ['#Cg'#CgOOQ!0Lb'#Dq'#DqOOQ!0Lb'#Jv'#JvO%[QlO'#JvOOQO'#Jy'#JyOOQO'#Ih'#IhOCQQpO'#EgOOQ!0Lb'#Ef'#EfOOQ!0Lb'#J}'#J}OC|Q!0MSO'#EgODWQpO'#EWOOQO'#Jx'#JxODlQpO'#JyOEyQpO'#EWODWQpO'#EgPFWO&2DjO'#CbPOOO)CD})CD}OOOO'#I`'#I`OFcO#tO,59UOOQ!0Lh,59U,59UOOOO'#Ia'#IaOFqO&jO,59UOGPQ!L^O'#DcOOOO'#Ic'#IcOGWO#@ItO,59{OOQ!0Lf,59{,59{OGfQlO'#IdOGyQ`O'#JtOIxQ!fO'#JtO+}QlO'#JtOJPQ`O,5:ROJgQ`O'#EpOJtQ`O'#KTOKPQ`O'#KSOKPQ`O'#KSOKXQ`O,5;^OK^Q`O'#KROOQ!0Ln,5:^,5:^OKeQlO,5:^OMcQ!0MxO,5:fONSQ`O,5:nONmQ!0LrO'#KQONtQ`O'#KPO9eQ`O'#KPO! YQ`O'#KPO! bQ`O,5;]O! gQ`O'#KPO!#lQ!fO'#JsOOQ!0Lh'#Ci'#CiO%[QlO'#ESO!$[Q!fO,5:sOOQS'#Jz'#JzOOQO-E<j-E<jO9kQ`O,5=_O!$rQ`O,5=_O!$wQlO,5;ZO!&zQMhO'#EkO!(eQ`O,5;ZO!(jQlO'#DyO!(tQpO,5;dO!(|QpO,5;dO%[QlO,5;dOOQ['#FT'#FTOOQ['#FV'#FVO%[QlO,5;eO%[QlO,5;eO%[QlO,5;eO%[QlO,5;eO%[QlO,5;eO%[QlO,5;eO%[QlO,5;eO%[QlO,5;eO%[QlO,5;eO%[QlO,5;eOOQ['#FZ'#FZO!)[QlO,5;tOOQ!0Lf,5;y,5;yOOQ!0Lf,5;z,5;zOOQ!0Lf,5;|,5;|O%[QlO'#IpO!+_Q!0LrO,5<iO%[QlO,5;eO!&zQMhO,5;eO!+|QMhO,5;eO!-nQMhO'#E^O%[QlO,5;wOOQ!0Lf,5;{,5;{O!-uQ,UO'#FjO!.rQ,UO'#KXO!.^Q,UO'#KXO!.yQ,UO'#KXOOQO'#KX'#KXO!/_Q,UO,5<SOOOW,5<`,5<`O!/pQlO'#FvOOOW'#Io'#IoO7VO7dO,5<QO!/wQ,UO'#FxOOQ!0Lf,5<Q,5<QO!0hQ$IUO'#CyOOQ!0Lh'#C}'#C}O!0{O#@ItO'#DRO!1iQMjO,5<eO!1pQ`O,5<hO!3YQ(CWO'#GXO!3jQ`O'#GYO!3oQ`O'#GYO!5_Q(CWO'#G^O!6dQpO'#GbOOQO'#Gn'#GnO!,TQMhO'#GmOOQO'#Gp'#GpO!,TQMhO'#GoO!7VQ$IUO'#JlOOQ!0Lh'#Jl'#JlO!7aQ`O'#JkO!7oQ`O'#JjO!7wQ`O'#CuOOQ!0Lh'#C{'#C{O!8YQ`O'#C}OOQ!0Lh'#DV'#DVOOQ!0Lh'#DX'#DXO!8_Q`O,5<eO1SQ`O'#DZO!,TQMhO'#GPO!,TQMhO'#GRO!8gQ`O'#GTO!8lQ`O'#GUO!3oQ`O'#G[O!,TQMhO'#GaO<]Q`O'#JkO!8qQ`O'#EqO!9`Q`O,5<gOOQ!0Lb'#Cr'#CrO!9hQ`O'#ErO!:bQpO'#EsOOQ!0Lb'#KR'#KRO!:iQ!0LrO'#KaO9uQ!0LrO,5=cO`QlO,5>tOOQ['#Jh'#JhOOQ[,5>u,5>uOOQ[-E<]-E<]O!<hQ!0MxO,5:bO!:]QpO,5:`O!?RQ!0MxO,5:jO%[QlO,5:jO!AiQ!0MxO,5:lOOQO,5@z,5@zO!BYQMhO,5=_O!BhQ!0LrO'#JiO9`Q`O'#JiO!ByQ!0LrO,59ZO!CUQpO,59ZO!C^QMhO,59ZO:dQMhO,59ZO!CiQ`O,5;ZO!CqQ`O'#HbO!DVQ`O'#KdO%[QlO,5;}O!:]QpO,5<PO!D_Q`O,5=zO!DdQ`O,5=zO!DiQ`O,5=zO!DwQ`O,5=zO9uQ!0LrO,5=zO<]Q`O,5=jOOQO'#Cy'#CyO!EOQpO,5=gO!EWQMhO,5=hO!EcQ`O,5=jO!EhQ!bO,5=mO!EpQ`O'#K`O?YQ`O'#HWO9kQ`O'#HYO!EuQ`O'#HYO:dQMhO'#H[O!EzQ`O'#H[OOQ[,5=p,5=pO!FPQ`O'#H]O!FbQ`O'#CoO!FgQ`O,59PO!FqQ`O,59PO!HvQlO,59POOQ[,59P,59PO!IWQ!0LrO,59PO%[QlO,59PO!KcQlO'#HeOOQ['#Hf'#HfOOQ['#Hg'#HgO`QlO,5=}O!KyQ`O,5=}O`QlO,5>TO`QlO,5>VO!LOQ`O,5>XO`QlO,5>ZO!LTQ`O,5>^O!LYQlO,5>dOOQ[,5>j,5>jO%[QlO,5>jO9uQ!0LrO,5>lOOQ[,5>n,5>nO#!dQ`O,5>nOOQ[,5>p,5>pO#!dQ`O,5>pOOQ[,5>r,5>rO##QQpO'#D_O%[QlO'#JvO##sQpO'#JvO##}QpO'#DmO#$`QpO'#DmO#&qQlO'#DmO#&xQ`O'#JuO#'QQ`O,5:WO#'VQ`O'#EtO#'eQ`O'#KUO#'mQ`O,5;_O#'rQpO'#DmO#(PQpO'#EVOOQ!0Lf,5:o,5:oO%[QlO,5:oO#(WQ`O,5:oO?YQ`O,5;YO!CUQpO,5;YO!C^QMhO,5;YO:dQMhO,5;YO#(`Q`O,5@bO#(eQ07dO,5:sOOQO-E<f-E<fO#)kQ!0MSO,5;RODWQpO,5:rO#)uQpO,5:rODWQpO,5;RO!ByQ!0LrO,5:rOOQ!0Lb'#Ej'#EjOOQO,5;R,5;RO%[QlO,5;RO#*SQ!0LrO,5;RO#*_Q!0LrO,5;RO!CUQpO,5:rOOQO,5;X,5;XO#*mQ!0LrO,5;RPOOO'#I^'#I^P#+RO&2DjO,58|POOO,58|,58|OOOO-E<^-E<^OOQ!0Lh1G.p1G.pOOOO-E<_-E<_OOOO,59},59}O#+^Q!bO,59}OOOO-E<a-E<aOOQ!0Lf1G/g1G/gO#+cQ!fO,5?OO+}QlO,5?OOOQO,5?U,5?UO#+mQlO'#IdOOQO-E<b-E<bO#+zQ`O,5@`O#,SQ!fO,5@`O#,ZQ`O,5@nOOQ!0Lf1G/m1G/mO%[QlO,5@oO#,cQ`O'#IjOOQO-E<h-E<hO#,ZQ`O,5@nOOQ!0Lb1G0x1G0xOOQ!0Ln1G/x1G/xOOQ!0Ln1G0Y1G0YO%[QlO,5@lO#,wQ!0LrO,5@lO#-YQ!0LrO,5@lO#-aQ`O,5@kO9eQ`O,5@kO#-iQ`O,5@kO#-wQ`O'#ImO#-aQ`O,5@kOOQ!0Lb1G0w1G0wO!(tQpO,5:uO!)PQpO,5:uOOQS,5:w,5:wO#.iQdO,5:wO#.qQMhO1G2yO9kQ`O1G2yOOQ!0Lf1G0u1G0uO#/PQ!0MxO1G0uO#0UQ!0MvO,5;VOOQ!0Lh'#GW'#GWO#0rQ!0MzO'#JlO!$wQlO1G0uO#2}Q!fO'#JwO%[QlO'#JwO#3XQ`O,5:eOOQ!0Lh'#D_'#D_OOQ!0Lf1G1O1G1OO%[QlO1G1OOOQ!0Lf1G1f1G1fO#3^Q`O1G1OO#5rQ!0MxO1G1PO#5yQ!0MxO1G1PO#8aQ!0MxO1G1PO#8hQ!0MxO1G1PO#;OQ!0MxO1G1PO#=fQ!0MxO1G1PO#=mQ!0MxO1G1PO#=tQ!0MxO1G1PO#@[Q!0MxO1G1PO#@cQ!0MxO1G1PO#BpQ?MtO'#CiO#DkQ?MtO1G1`O#DrQ?MtO'#JsO#EVQ!0MxO,5?[OOQ!0Lb-E<n-E<nO#GdQ!0MxO1G1PO#HaQ!0MzO1G1POOQ!0Lf1G1P1G1PO#IdQMjO'#J|O#InQ`O,5:xO#IsQ!0MxO1G1cO#JgQ,UO,5<WO#JoQ,UO,5<XO#JwQ,UO'#FoO#K`Q`O'#FnOOQO'#KY'#KYOOQO'#In'#InO#KeQ,UO1G1nOOQ!0Lf1G1n1G1nOOOW1G1y1G1yO#KvQ?MtO'#JrO#LQQ`O,5<bO!)[QlO,5<bOOOW-E<m-E<mOOQ!0Lf1G1l1G1lO#LVQpO'#KXOOQ!0Lf,5<d,5<dO#L_QpO,5<dO#LdQMhO'#DTOOOO'#Ib'#IbO#LkO#@ItO,59mOOQ!0Lh,59m,59mO%[QlO1G2PO!8lQ`O'#IrO#LvQ`O,5<zOOQ!0Lh,5<w,5<wO!,TQMhO'#IuO#MdQMjO,5=XO!,TQMhO'#IwO#NVQMjO,5=ZO!&zQMhO,5=]OOQO1G2S1G2SO#NaQ!dO'#CrO#NtQ(CWO'#ErO$ |QpO'#GbO$!dQ!dO,5<sO$!kQ`O'#K[O9eQ`O'#K[O$!yQ`O,5<uO$#aQ!dO'#C{O!,TQMhO,5<tO$#kQ`O'#GZO$$PQ`O,5<tO$$UQ!dO'#GWO$$cQ!dO'#K]O$$mQ`O'#K]O!&zQMhO'#K]O$$rQ`O,5<xO$$wQlO'#JvO$%RQpO'#GcO#$`QpO'#GcO$%dQ`O'#GgO!3oQ`O'#GkO$%iQ!0LrO'#ItO$%tQpO,5<|OOQ!0Lp,5<|,5<|O$%{QpO'#GcO$&YQpO'#GdO$&kQpO'#GdO$&pQMjO,5=XO$'QQMjO,5=ZOOQ!0Lh,5=^,5=^O!,TQMhO,5@VO!,TQMhO,5@VO$'bQ`O'#IyO$'vQ`O,5@UO$(OQ`O,59aOOQ!0Lh,59i,59iO$(TQ`O,5@VO$)TQ$IYO,59uOOQ!0Lh'#Jp'#JpO$)vQMjO,5<kO$*iQMjO,5<mO@zQ`O,5<oOOQ!0Lh,5<p,5<pO$*sQ`O,5<vO$*xQMjO,5<{O$+YQ`O'#KPO!$wQlO1G2RO$+_Q`O1G2RO9eQ`O'#KSO9eQ`O'#EtO%[QlO'#EtO9eQ`O'#I{O$+dQ!0LrO,5@{OOQ[1G2}1G2}OOQ[1G4`1G4`OOQ!0Lf1G/|1G/|OOQ!0Lf1G/z1G/zO$-fQ!0MxO1G0UOOQ[1G2y1G2yO!&zQMhO1G2yO%[QlO1G2yO#.tQ`O1G2yO$/jQMhO'#EkOOQ!0Lb,5@T,5@TO$/wQ!0LrO,5@TOOQ[1G.u1G.uO!ByQ!0LrO1G.uO!CUQpO1G.uO!C^QMhO1G.uO$0YQ`O1G0uO$0_Q`O'#CiO$0jQ`O'#KeO$0rQ`O,5=|O$0wQ`O'#KeO$0|Q`O'#KeO$1[Q`O'#JRO$1jQ`O,5AOO$1rQ!fO1G1iOOQ!0Lf1G1k1G1kO9kQ`O1G3fO@zQ`O1G3fO$1yQ`O1G3fO$2OQ`O1G3fO!DiQ`O1G3fO9uQ!0LrO1G3fOOQ[1G3f1G3fO!EcQ`O1G3UO!&zQMhO1G3RO$2TQ`O1G3ROOQ[1G3S1G3SO!&zQMhO1G3SO$2YQ`O1G3SO$2bQpO'#HQOOQ[1G3U1G3UO!6_QpO'#I}O!EhQ!bO1G3XOOQ[1G3X1G3XOOQ[,5=r,5=rO$2jQMhO,5=tO9kQ`O,5=tO$%dQ`O,5=vO9`Q`O,5=vO!CUQpO,5=vO!C^QMhO,5=vO:dQMhO,5=vO$2xQ`O'#KcO$3TQ`O,5=wOOQ[1G.k1G.kO$3YQ!0LrO1G.kO@zQ`O1G.kO$3eQ`O1G.kO9uQ!0LrO1G.kO$5mQ!fO,5AQO$5zQ`O,5AQO9eQ`O,5AQO$6VQlO,5>PO$6^Q`O,5>POOQ[1G3i1G3iO`QlO1G3iOOQ[1G3o1G3oOOQ[1G3q1G3qO?TQ`O1G3sO$6cQlO1G3uO$:gQlO'#HtOOQ[1G3x1G3xO$:tQ`O'#HzO?YQ`O'#H|OOQ[1G4O1G4OO$:|QlO1G4OO9uQ!0LrO1G4UOOQ[1G4W1G4WOOQ!0Lb'#G_'#G_O9uQ!0LrO1G4YO9uQ!0LrO1G4[O$?TQ`O,5@bO!)[QlO,5;`O9eQ`O,5;`O?YQ`O,5:XO!)[QlO,5:XO!CUQpO,5:XO$?YQ?MtO,5:XOOQO,5;`,5;`O$?dQpO'#IeO$?zQ`O,5@aOOQ!0Lf1G/r1G/rO$@SQpO'#IkO$@^Q`O,5@pOOQ!0Lb1G0y1G0yO#$`QpO,5:XOOQO'#Ig'#IgO$@fQpO,5:qOOQ!0Ln,5:q,5:qO#(ZQ`O1G0ZOOQ!0Lf1G0Z1G0ZO%[QlO1G0ZOOQ!0Lf1G0t1G0tO?YQ`O1G0tO!CUQpO1G0tO!C^QMhO1G0tOOQ!0Lb1G5|1G5|O!ByQ!0LrO1G0^OOQO1G0m1G0mO%[QlO1G0mO$@mQ!0LrO1G0mO$@xQ!0LrO1G0mO!CUQpO1G0^ODWQpO1G0^O$AWQ!0LrO1G0mOOQO1G0^1G0^O$AlQ!0MxO1G0mPOOO-E<[-E<[POOO1G.h1G.hOOOO1G/i1G/iO$AvQ!bO,5<iO$BOQ!fO1G4jOOQO1G4p1G4pO%[QlO,5?OO$BYQ`O1G5zO$BbQ`O1G6YO$BjQ!fO1G6ZO9eQ`O,5?UO$BtQ!0MxO1G6WO%[QlO1G6WO$CUQ!0LrO1G6WO$CgQ`O1G6VO$CgQ`O1G6VO9eQ`O1G6VO$CoQ`O,5?XO9eQ`O,5?XOOQO,5?X,5?XO$DTQ`O,5?XO$+YQ`O,5?XOOQO-E<k-E<kOOQS1G0a1G0aOOQS1G0c1G0cO#.lQ`O1G0cOOQ[7+(e7+(eO!&zQMhO7+(eO%[QlO7+(eO$DcQ`O7+(eO$DnQMhO7+(eO$D|Q!0MzO,5=XO$GXQ!0MzO,5=ZO$IdQ!0MzO,5=XO$KuQ!0MzO,5=ZO$NWQ!0MzO,59uO%!]Q!0MzO,5<kO%$hQ!0MzO,5<mO%&sQ!0MzO,5<{OOQ!0Lf7+&a7+&aO%)UQ!0MxO7+&aO%)xQlO'#IfO%*VQ`O,5@cO%*_Q!fO,5@cOOQ!0Lf1G0P1G0PO%*iQ`O7+&jOOQ!0Lf7+&j7+&jO%*nQ?MtO,5:fO%[QlO7+&zO%*xQ?MtO,5:bO%+VQ?MtO,5:jO%+aQ?MtO,5:lO%+kQMhO'#IiO%+uQ`O,5@hOOQ!0Lh1G0d1G0dOOQO1G1r1G1rOOQO1G1s1G1sO%+}Q!jO,5<ZO!)[QlO,5<YOOQO-E<l-E<lOOQ!0Lf7+'Y7+'YOOOW7+'e7+'eOOOW1G1|1G1|O%,YQ`O1G1|OOQ!0Lf1G2O1G2OOOOO,59o,59oO%,_Q!dO,59oOOOO-E<`-E<`OOQ!0Lh1G/X1G/XO%,fQ!0MxO7+'kOOQ!0Lh,5?^,5?^O%-YQMhO1G2fP%-aQ`O'#IrPOQ!0Lh-E<p-E<pO%-}QMjO,5?aOOQ!0Lh-E<s-E<sO%.pQMjO,5?cOOQ!0Lh-E<u-E<uO%.zQ!dO1G2wO%/RQ!dO'#CrO%/iQMhO'#KSO$$wQlO'#JvOOQ!0Lh1G2_1G2_O%/sQ`O'#IqO%0[Q`O,5@vO%0[Q`O,5@vO%0dQ`O,5@vO%0oQ`O,5@vOOQO1G2a1G2aO%0}QMjO1G2`O$+YQ`O'#K[O!,TQMhO1G2`O%1_Q(CWO'#IsO%1lQ`O,5@wO!&zQMhO,5@wO%1tQ!dO,5@wOOQ!0Lh1G2d1G2dO%4UQ!fO'#CiO%4`Q`O,5=POOQ!0Lb,5<},5<}O%4hQpO,5<}OOQ!0Lb,5=O,5=OOCwQ`O,5<}O%4sQpO,5<}OOQ!0Lb,5=R,5=RO$+YQ`O,5=VOOQO,5?`,5?`OOQO-E<r-E<rOOQ!0Lp1G2h1G2hO#$`QpO,5<}O$$wQlO,5=PO%5RQ`O,5=OO%5^QpO,5=OO!,TQMhO'#IuO%6WQMjO1G2sO!,TQMhO'#IwO%6yQMjO1G2uO%7TQMjO1G5qO%7_QMjO1G5qOOQO,5?e,5?eOOQO-E<w-E<wOOQO1G.{1G.{O!,TQMhO1G5qO!,TQMhO1G5qO!:]QpO,59wO%[QlO,59wOOQ!0Lh,5<j,5<jO%7lQ`O1G2ZO!,TQMhO1G2bO%7qQ!0MxO7+'mOOQ!0Lf7+'m7+'mO!$wQlO7+'mO%8eQ`O,5;`OOQ!0Lb,5?g,5?gOOQ!0Lb-E<y-E<yO%8jQ!dO'#K^O#(ZQ`O7+(eO4UQ!fO7+(eO$DfQ`O7+(eO%8tQ!0MvO'#CiO%9XQ!0MvO,5=SO%9lQ`O,5=SO%9tQ`O,5=SOOQ!0Lb1G5o1G5oOOQ[7+$a7+$aO!ByQ!0LrO7+$aO!CUQpO7+$aO!$wQlO7+&aO%9yQ`O'#JQO%:bQ`O,5APOOQO1G3h1G3hO9kQ`O,5APO%:bQ`O,5APO%:jQ`O,5APOOQO,5?m,5?mOOQO-E=P-E=POOQ!0Lf7+'T7+'TO%:oQ`O7+)QO9uQ!0LrO7+)QO9kQ`O7+)QO@zQ`O7+)QO%:tQ`O7+)QOOQ[7+)Q7+)QOOQ[7+(p7+(pO%:yQ!0MvO7+(mO!&zQMhO7+(mO!E^Q`O7+(nOOQ[7+(n7+(nO!&zQMhO7+(nO%;TQ`O'#KbO%;`Q`O,5=lOOQO,5?i,5?iOOQO-E<{-E<{OOQ[7+(s7+(sO%<rQpO'#HZOOQ[1G3`1G3`O!&zQMhO1G3`O%[QlO1G3`O%<yQ`O1G3`O%=UQMhO1G3`O9uQ!0LrO1G3bO$%dQ`O1G3bO9`Q`O1G3bO!CUQpO1G3bO!C^QMhO1G3bO%=dQ`O'#JPO%=xQ`O,5@}O%>QQpO,5@}OOQ!0Lb1G3c1G3cOOQ[7+$V7+$VO@zQ`O7+$VO9uQ!0LrO7+$VO%>]Q`O7+$VO%[QlO1G6lO%[QlO1G6mO%>bQ!0LrO1G6lO%>lQlO1G3kO%>sQ`O1G3kO%>xQlO1G3kOOQ[7+)T7+)TO9uQ!0LrO7+)_O`QlO7+)aOOQ['#Kh'#KhOOQ['#JS'#JSO%?PQlO,5>`OOQ[,5>`,5>`O%[QlO'#HuO%?^Q`O'#HwOOQ[,5>f,5>fO9eQ`O,5>fOOQ[,5>h,5>hOOQ[7+)j7+)jOOQ[7+)p7+)pOOQ[7+)t7+)tOOQ[7+)v7+)vO%?cQpO1G5|O%?}Q?MtO1G0zO%@XQ`O1G0zOOQO1G/s1G/sO%@dQ?MtO1G/sO?YQ`O1G/sO!)[QlO'#DmOOQO,5?P,5?POOQO-E<c-E<cOOQO,5?V,5?VOOQO-E<i-E<iO!CUQpO1G/sOOQO-E<e-E<eOOQ!0Ln1G0]1G0]OOQ!0Lf7+%u7+%uO#(ZQ`O7+%uOOQ!0Lf7+&`7+&`O?YQ`O7+&`O!CUQpO7+&`OOQO7+%x7+%xO$AlQ!0MxO7+&XOOQO7+&X7+&XO%[QlO7+&XO%@nQ!0LrO7+&XO!ByQ!0LrO7+%xO!CUQpO7+%xO%@yQ!0LrO7+&XO%AXQ!0MxO7++rO%[QlO7++rO%AiQ`O7++qO%AiQ`O7++qOOQO1G4s1G4sO9eQ`O1G4sO%AqQ`O1G4sOOQS7+%}7+%}O#(ZQ`O<<LPO4UQ!fO<<LPO%BPQ`O<<LPOOQ[<<LP<<LPO!&zQMhO<<LPO%[QlO<<LPO%BXQ`O<<LPO%BdQ!0MzO,5?aO%DoQ!0MzO,5?cO%FzQ!0MzO1G2`O%I]Q!0MzO1G2sO%KhQ!0MzO1G2uO%MsQ!fO,5?QO%[QlO,5?QOOQO-E<d-E<dO%M}Q`O1G5}OOQ!0Lf<<JU<<JUO%NVQ?MtO1G0uO&!^Q?MtO1G1PO&!eQ?MtO1G1PO&$fQ?MtO1G1PO&$mQ?MtO1G1PO&&nQ?MtO1G1PO&(oQ?MtO1G1PO&(vQ?MtO1G1PO&(}Q?MtO1G1PO&+OQ?MtO1G1PO&+VQ?MtO1G1PO&+^Q!0MxO<<JfO&-UQ?MtO1G1PO&.RQ?MvO1G1PO&/UQ?MvO'#JlO&1[Q?MtO1G1cO&1iQ?MtO1G0UO&1sQMjO,5?TOOQO-E<g-E<gO!)[QlO'#FqOOQO'#KZ'#KZOOQO1G1u1G1uO&1}Q`O1G1tO&2SQ?MtO,5?[OOOW7+'h7+'hOOOO1G/Z1G/ZO&2^Q!dO1G4xOOQ!0Lh7+(Q7+(QP!&zQMhO,5?^O!,TQMhO7+(cO&2eQ`O,5?]O9eQ`O,5?]O$+YQ`O,5?]OOQO-E<o-E<oO&2sQ`O1G6bO&2sQ`O1G6bO&2{Q`O1G6bO&3WQMjO7+'zO&3hQ!dO,5?_O&3rQ`O,5?_O!&zQMhO,5?_OOQO-E<q-E<qO&3wQ!dO1G6cO&4RQ`O1G6cO&4ZQ`O1G2kO!&zQMhO1G2kOOQ!0Lb1G2i1G2iOOQ!0Lb1G2j1G2jO%4hQpO1G2iO!CUQpO1G2iOCwQ`O1G2iOOQ!0Lb1G2q1G2qO&4`QpO1G2iO&4nQ`O1G2kO$+YQ`O1G2jOCwQ`O1G2jO$$wQlO1G2kO&4vQ`O1G2jO&5jQMjO,5?aOOQ!0Lh-E<t-E<tO&6]QMjO,5?cOOQ!0Lh-E<v-E<vO!,TQMhO7++]O&6gQMjO7++]O&6qQMjO7++]OOQ!0Lh1G/c1G/cO&7OQ`O1G/cOOQ!0Lh7+'u7+'uO&7TQMjO7+'|O&7eQ!0MxO<<KXOOQ!0Lf<<KX<<KXO&8XQ`O1G0zO!&zQMhO'#IzO&8^Q`O,5@xO&:`Q!fO<<LPO!&zQMhO1G2nO&:gQ!0LrO1G2nOOQ[<<G{<<G{O!ByQ!0LrO<<G{O&:xQ!0MxO<<I{OOQ!0Lf<<I{<<I{OOQO,5?l,5?lO&;lQ`O,5?lO&;qQ`O,5?lOOQO-E=O-E=OO&<PQ`O1G6kO&<PQ`O1G6kO9kQ`O1G6kO@zQ`O<<LlOOQ[<<Ll<<LlO&<XQ`O<<LlO9uQ!0LrO<<LlO9kQ`O<<LlOOQ[<<LX<<LXO%:yQ!0MvO<<LXOOQ[<<LY<<LYO!E^Q`O<<LYO&<^QpO'#I|O&<iQ`O,5@|O!)[QlO,5@|OOQ[1G3W1G3WOOQO'#JO'#JOO9uQ!0LrO'#JOO&<qQpO,5=uOOQ[,5=u,5=uO&<xQpO'#EgO&=PQpO'#GeO&=UQ`O7+(zO&=ZQ`O7+(zOOQ[7+(z7+(zO!&zQMhO7+(zO%[QlO7+(zO&=cQ`O7+(zOOQ[7+(|7+(|O9uQ!0LrO7+(|O$%dQ`O7+(|O9`Q`O7+(|O!CUQpO7+(|O&=nQ`O,5?kOOQO-E<}-E<}OOQO'#H^'#H^O&=yQ`O1G6iO9uQ!0LrO<<GqOOQ[<<Gq<<GqO@zQ`O<<GqO&>RQ`O7+,WO&>WQ`O7+,XO%[QlO7+,WO%[QlO7+,XOOQ[7+)V7+)VO&>]Q`O7+)VO&>bQlO7+)VO&>iQ`O7+)VOOQ[<<Ly<<LyOOQ[<<L{<<L{OOQ[-E=Q-E=QOOQ[1G3z1G3zO&>nQ`O,5>aOOQ[,5>c,5>cO&>sQ`O1G4QO9eQ`O7+&fO!)[QlO7+&fOOQO7+%_7+%_O&>xQ?MtO1G6ZO?YQ`O7+%_OOQ!0Lf<<Ia<<IaOOQ!0Lf<<Iz<<IzO?YQ`O<<IzOOQO<<Is<<IsO$AlQ!0MxO<<IsO%[QlO<<IsOOQO<<Id<<IdO!ByQ!0LrO<<IdO&?SQ!0LrO<<IsO&?_Q!0MxO<= ^O&?oQ`O<= ]OOQO7+*_7+*_O9eQ`O7+*_OOQ[ANAkANAkO&?wQ!fOANAkO!&zQMhOANAkO#(ZQ`OANAkO4UQ!fOANAkO&@OQ`OANAkO%[QlOANAkO&@WQ!0MzO7+'zO&BiQ!0MzO,5?aO&DtQ!0MzO,5?cO&GPQ!0MzO7+'|O&IbQ!fO1G4lO&IlQ?MtO7+&aO&KpQ?MvO,5=XO&MwQ?MvO,5=ZO&NXQ?MvO,5=XO&NiQ?MvO,5=ZO&NyQ?MvO,59uO'#PQ?MvO,5<kO'%SQ?MvO,5<mO''hQ?MvO,5<{O')^Q?MtO7+'kO')kQ?MtO7+'mO')xQ`O,5<]OOQO7+'`7+'`OOQ!0Lh7+*d7+*dO')}QMjO<<K}OOQO1G4w1G4wO'*UQ`O1G4wO'*aQ`O1G4wO'*oQ`O7++|O'*oQ`O7++|O!&zQMhO1G4yO'*wQ!dO1G4yO'+RQ`O7++}O'+ZQ`O7+(VO'+fQ!dO7+(VOOQ!0Lb7+(T7+(TOOQ!0Lb7+(U7+(UO!CUQpO7+(TOCwQ`O7+(TO'+pQ`O7+(VO!&zQMhO7+(VO$+YQ`O7+(UO'+uQ`O7+(VOCwQ`O7+(UO'+}QMjO<<NwO!,TQMhO<<NwOOQ!0Lh7+$}7+$}O',XQ!dO,5?fOOQO-E<x-E<xO',cQ!0MvO7+(YO!&zQMhO7+(YOOQ[AN=gAN=gO9kQ`O1G5WOOQO1G5W1G5WO',sQ`O1G5WO',xQ`O7+,VO',xQ`O7+,VO9uQ!0LrOANBWO@zQ`OANBWOOQ[ANBWANBWO'-QQ`OANBWOOQ[ANAsANAsOOQ[ANAtANAtO'-VQ`O,5?hOOQO-E<z-E<zO'-bQ?MtO1G6hOOQO,5?j,5?jOOQO-E<|-E<|OOQ[1G3a1G3aO'-lQ`O,5=POOQ[<<Lf<<LfO!&zQMhO<<LfO&=UQ`O<<LfO'-qQ`O<<LfO%[QlO<<LfOOQ[<<Lh<<LhO9uQ!0LrO<<LhO$%dQ`O<<LhO9`Q`O<<LhO'-yQpO1G5VO'.UQ`O7+,TOOQ[AN=]AN=]O9uQ!0LrOAN=]OOQ[<= r<= rOOQ[<= s<= sO'.^Q`O<= rO'.cQ`O<= sOOQ[<<Lq<<LqO'.hQ`O<<LqO'.mQlO<<LqOOQ[1G3{1G3{O?YQ`O7+)lO'.tQ`O<<JQO'/PQ?MtO<<JQOOQO<<Hy<<HyOOQ!0LfAN?fAN?fOOQOAN?_AN?_O$AlQ!0MxOAN?_OOQOAN?OAN?OO%[QlOAN?_OOQO<<My<<MyOOQ[G27VG27VO!&zQMhOG27VO#(ZQ`OG27VO'/ZQ!fOG27VO4UQ!fOG27VO'/bQ`OG27VO'/jQ?MtO<<JfO'/wQ?MvO1G2`O'1mQ?MvO,5?aO'3pQ?MvO,5?cO'5sQ?MvO1G2sO'7vQ?MvO1G2uO'9yQ?MtO<<KXO':WQ?MtO<<I{OOQO1G1w1G1wO!,TQMhOANAiOOQO7+*c7+*cO':eQ`O7+*cO':pQ`O<= hO':xQ!dO7+*eOOQ!0Lb<<Kq<<KqO$+YQ`O<<KqOCwQ`O<<KqO';SQ`O<<KqO!&zQMhO<<KqOOQ!0Lb<<Ko<<KoO!CUQpO<<KoO';_Q!dO<<KqOOQ!0Lb<<Kp<<KpO';iQ`O<<KqO!&zQMhO<<KqO$+YQ`O<<KpO';nQMjOANDcO';xQ!0MvO<<KtOOQO7+*r7+*rO9kQ`O7+*rO'<YQ`O<= qOOQ[G27rG27rO9uQ!0LrOG27rO@zQ`OG27rO!)[QlO1G5SO'<bQ`O7+,SO'<jQ`O1G2kO&=UQ`OANBQOOQ[ANBQANBQO!&zQMhOANBQO'<oQ`OANBQOOQ[ANBSANBSO9uQ!0LrOANBSO$%dQ`OANBSOOQO'#H_'#H_OOQO7+*q7+*qOOQ[G22wG22wOOQ[ANE^ANE^OOQ[ANE_ANE_OOQ[ANB]ANB]O'<wQ`OANB]OOQ[<<MW<<MWO!)[QlOAN?lOOQOG24yG24yO$AlQ!0MxOG24yO#(ZQ`OLD,qOOQ[LD,qLD,qO!&zQMhOLD,qO'<|Q!fOLD,qO'=TQ?MvO7+'zO'>yQ?MvO,5?aO'@|Q?MvO,5?cO'CPQ?MvO7+'|O'DuQMjOG27TOOQO<<M}<<M}OOQ!0LbANA]ANA]O$+YQ`OANA]OCwQ`OANA]O'EVQ!dOANA]OOQ!0LbANAZANAZO'E^Q`OANA]O!&zQMhOANA]O'EiQ!dOANA]OOQ!0LbANA[ANA[OOQO<<N^<<N^OOQ[LD-^LD-^O9uQ!0LrOLD-^O'EsQ?MtO7+*nOOQO'#Gf'#GfOOQ[G27lG27lO&=UQ`OG27lO!&zQMhOG27lOOQ[G27nG27nO9uQ!0LrOG27nOOQ[G27wG27wO'E}Q?MtOG25WOOQOLD*eLD*eOOQ[!$(!]!$(!]O#(ZQ`O!$(!]O!&zQMhO!$(!]O'FXQ!0MzOG27TOOQ!0LbG26wG26wO$+YQ`OG26wO'HjQ`OG26wOCwQ`OG26wO'HuQ!dOG26wO!&zQMhOG26wOOQ[!$(!x!$(!xOOQ[LD-WLD-WO&=UQ`OLD-WOOQ[LD-YLD-YOOQ[!)9Ew!)9EwO#(ZQ`O!)9EwOOQ!0LbLD,cLD,cO$+YQ`OLD,cOCwQ`OLD,cO'H|Q`OLD,cO'IXQ!dOLD,cOOQ[!$(!r!$(!rOOQ[!.K;c!.K;cO'I`Q?MvOG27TOOQ!0Lb!$( }!$( }O$+YQ`O!$( }OCwQ`O!$( }O'KUQ`O!$( }OOQ!0Lb!)9Ei!)9EiO$+YQ`O!)9EiOCwQ`O!)9EiOOQ!0Lb!.K;T!.K;TO$+YQ`O!.K;TOOQ!0Lb!4/0o!4/0oO!)[QlO'#DzO1PQ`O'#EXO'KaQ!fO'#JrO'KhQ!L^O'#DvO'KoQlO'#EOO'KvQ!fO'#CiO'N^Q!fO'#CiO!)[QlO'#EQO'NnQlO,5;ZO!)[QlO,5;eO!)[QlO,5;eO!)[QlO,5;eO!)[QlO,5;eO!)[QlO,5;eO!)[QlO,5;eO!)[QlO,5;eO!)[QlO,5;eO!)[QlO,5;eO!)[QlO,5;eO!)[QlO'#IpO(!qQ`O,5<iO!)[QlO,5;eO(!yQMhO,5;eO($dQMhO,5;eO!)[QlO,5;wO!&zQMhO'#GmO(!yQMhO'#GmO!&zQMhO'#GoO(!yQMhO'#GoO1SQ`O'#DZO1SQ`O'#DZO!&zQMhO'#GPO(!yQMhO'#GPO!&zQMhO'#GRO(!yQMhO'#GRO!&zQMhO'#GaO(!yQMhO'#GaO!)[QlO,5:jO($kQpO'#D_O($uQpO'#JvO!)[QlO,5@oO'NnQlO1G0uO(%PQ?MtO'#CiO!)[QlO1G2PO!&zQMhO'#IuO(!yQMhO'#IuO!&zQMhO'#IwO(!yQMhO'#IwO(%ZQ!dO'#CrO!&zQMhO,5<tO(!yQMhO,5<tO'NnQlO1G2RO!)[QlO7+&zO!&zQMhO1G2`O(!yQMhO1G2`O!&zQMhO'#IuO(!yQMhO'#IuO!&zQMhO'#IwO(!yQMhO'#IwO!&zQMhO1G2bO(!yQMhO1G2bO'NnQlO7+'mO'NnQlO7+&aO!&zQMhOANAiO(!yQMhOANAiO(%nQ`O'#EoO(%sQ`O'#EoO(%{Q`O'#F]O(&QQ`O'#EyO(&VQ`O'#KTO(&bQ`O'#KRO(&mQ`O,5;ZO(&rQMjO,5<eO(&yQ`O'#GYO('OQ`O'#GYO('TQ`O,5<eO(']Q`O,5<gO('eQ`O,5;ZO('mQ?MtO1G1`O('tQ`O,5<tO('yQ`O,5<tO((OQ`O,5<vO((TQ`O,5<vO((YQ`O1G2RO((_Q`O1G0uO((dQMjO<<K}O((kQMjO<<K}O((rQMhO'#F|O9`Q`O'#F{OAuQ`O'#EnO!)[QlO,5;tO!3oQ`O'#GYO!3oQ`O'#GYO!3oQ`O'#G[O!3oQ`O'#G[O!,TQMhO7+(cO!,TQMhO7+(cO%.zQ!dO1G2wO%.zQ!dO1G2wO!&zQMhO,5=]O!&zQMhO,5=]",
	stateData: "()x~O'|OS'}OSTOS(ORQ~OPYOQYOSfOY!VOaqOdzOeyOl!POpkOrYOskOtkOzkO|YO!OYO!SWO!WkO!XkO!_XO!iuO!lZO!oYO!pYO!qYO!svO!uwO!xxO!|]O$W|O$niO%h}O%j!QO%l!OO%m!OO%n!OO%q!RO%s!SO%v!TO%w!TO%y!UO&W!WO&^!XO&`!YO&b!ZO&d![O&g!]O&m!^O&s!_O&u!`O&w!aO&y!bO&{!cO(TSO(VTO(YUO(aVO(o[O~OWtO~P`OPYOQYOSfOd!jOe!iOpkOrYOskOtkOzkO|YO!OYO!SWO!WkO!XkO!_!eO!iuO!lZO!oYO!pYO!qYO!svO!u!gO!x!hO$W!kO$niO(T!dO(VTO(YUO(aVO(o[O~Oa!wOs!nO!S!oO!b!yO!c!vO!d!vO!|<VO#T!pO#U!pO#V!xO#W!pO#X!pO#[!zO#]!zO(U!lO(VTO(YUO(e!mO(o!sO~O(O!{O~OP]XR]X[]Xa]Xj]Xr]X!Q]X!S]X!]]X!l]X!p]X#R]X#S]X#`]X#kfX#n]X#o]X#p]X#q]X#r]X#s]X#t]X#u]X#v]X#x]X#z]X#{]X$Q]X'z]X(a]X(r]X(y]X(z]X~O!g%RX~P(qO_!}O(V#PO(W!}O(X#PO~O_#QO(X#PO(Y#PO(Z#QO~Ox#SO!U#TO(b#TO(c#VO~OPYOQYOSfOd!jOe!iOpkOrYOskOtkOzkO|YO!OYO!SWO!WkO!XkO!_!eO!iuO!lZO!oYO!pYO!qYO!svO!u!gO!x!hO$W!kO$niO(T<ZO(VTO(YUO(aVO(o[O~O![#ZO!]#WO!Y(hP!Y(vP~P+}O!^#cO~P`OPYOQYOSfOd!jOe!iOrYOskOtkOzkO|YO!OYO!SWO!WkO!XkO!_!eO!iuO!lZO!oYO!pYO!qYO!svO!u!gO!x!hO$W!kO$niO(VTO(YUO(aVO(o[O~Op#mO![#iO!|]O#i#lO#j#iO(T<[O!k(sP~P.iO!l#oO(T#nO~O!x#sO!|]O%h#tO~O#k#uO~O!g#vO#k#uO~OP$[OR#zO[$cOj$ROr$aO!Q#yO!S#{O!]$_O!l#xO!p$[O#R$RO#n$OO#o$PO#p$PO#q$PO#r$QO#s$RO#t$RO#u$bO#v$SO#x$UO#z$WO#{$XO(aVO(r$YO(y#|O(z#}O~Oa(fX'z(fX'w(fX!k(fX!Y(fX!_(fX%i(fX!g(fX~P1qO#S$dO#`$eO$Q$eOP(gXR(gX[(gXj(gXr(gX!Q(gX!S(gX!](gX!l(gX!p(gX#R(gX#n(gX#o(gX#p(gX#q(gX#r(gX#s(gX#t(gX#u(gX#v(gX#x(gX#z(gX#{(gX(a(gX(r(gX(y(gX(z(gX!_(gX%i(gX~Oa(gX'z(gX'w(gX!Y(gX!k(gXv(gX!g(gX~P4UO#`$eO~O$]$hO$_$gO$f$mO~OSfO!_$nO$i$oO$k$qO~Oh%VOj%dOk%dOp%WOr%XOs$tOt$tOz%YO|%ZO!O%]O!S${O!_$|O!i%bO!l$xO#j%cO$W%`O$t%^O$v%_O$y%aO(T$sO(VTO(YUO(a$uO(y$}O(z%POg(^P~Ol%[O~P7eO!l%eO~O!S%hO!_%iO(T%gO~O!g%mO~Oa%nO'z%nO~O!Q%rO~P%[O(U!lO~P%[O%n%vO~P%[Oh%VO!l%eO(T%gO(U!lO~Oe%}O!l%eO(T%gO~Oj$RO~O!_&PO(T%gO(U!lO(VTO(YUO`)WP~O!Q&SO!l&RO%j&VO&T&WO~P;SO!x#sO~O%s&YO!S)SX!_)SX(T)SX~O(T&ZO~Ol!PO!u&`O%j!QO%l!OO%m!OO%n!OO%q!RO%s!SO%v!TO%w!TO~Od&eOe&dO!x&bO%h&cO%{&aO~P<bOd&hOeyOl!PO!_&gO!u&`O!xxO!|]O%h}O%l!OO%m!OO%n!OO%q!RO%s!SO%v!TO%w!TO%y!UO~Ob&kO#`&nO%j&iO(U!lO~P=gO!l&oO!u&sO~O!l#oO~O!_XO~Oa%nO'x&{O'z%nO~Oa%nO'x'OO'z%nO~Oa%nO'x'QO'z%nO~O'w]X!Y]Xv]X!k]X&[]X!_]X%i]X!g]X~P(qO!b'_O!c'WO!d'WO(U!lO(VTO(YUO~Os'UO!S'TO!['XO(e'SO!^(iP!^(xP~P@nOn'bO!_'`O(T%gO~Oe'gO!l%eO(T%gO~O!Q&SO!l&RO~Os!nO!S!oO!|<VO#T!pO#U!pO#W!pO#X!pO(U!lO(VTO(YUO(e!mO(o!sO~O!b'mO!c'lO!d'lO#V!pO#['nO#]'nO~PBYOa%nOh%VO!g#vO!l%eO'z%nO(r'pO~O!p'tO#`'rO~PChOs!nO!S!oO(VTO(YUO(e!mO(o!sO~O!_XOs(mX!S(mX!b(mX!c(mX!d(mX!|(mX#T(mX#U(mX#V(mX#W(mX#X(mX#[(mX#](mX(U(mX(V(mX(Y(mX(e(mX(o(mX~O!c'lO!d'lO(U!lO~PDWO(P'xO(Q'xO(R'zO~O_!}O(V'|O(W!}O(X'|O~O_#QO(X'|O(Y'|O(Z#QO~Ov(OO~P%[Ox#SO!U#TO(b#TO(c(RO~O![(TO!Y'WX!Y'^X!]'WX!]'^X~P+}O!](VO!Y(hX~OP$[OR#zO[$cOj$ROr$aO!Q#yO!S#{O!](VO!l#xO!p$[O#R$RO#n$OO#o$PO#p$PO#q$PO#r$QO#s$RO#t$RO#u$bO#v$SO#x$UO#z$WO#{$XO(aVO(r$YO(y#|O(z#}O~O!Y(hX~PHRO!Y([O~O!Y(uX!](uX!g(uX!k(uX(r(uX~O#`(uX#k#dX!^(uX~PJUO#`(]O!Y(wX!](wX~O!](^O!Y(vX~O!Y(aO~O#`$eO~PJUO!^(bO~P`OR#zO!Q#yO!S#{O!l#xO(aVOP!na[!naj!nar!na!]!na!p!na#R!na#n!na#o!na#p!na#q!na#r!na#s!na#t!na#u!na#v!na#x!na#z!na#{!na(r!na(y!na(z!na~Oa!na'z!na'w!na!Y!na!k!nav!na!_!na%i!na!g!na~PKlO!k(cO~O!g#vO#`(dO(r'pO!](tXa(tX'z(tX~O!k(tX~PNXO!S%hO!_%iO!|]O#i(iO#j(hO(T%gO~O!](jO!k(sX~O!k(lO~O!S%hO!_%iO#j(hO(T%gO~OP(gXR(gX[(gXj(gXr(gX!Q(gX!S(gX!](gX!l(gX!p(gX#R(gX#n(gX#o(gX#p(gX#q(gX#r(gX#s(gX#t(gX#u(gX#v(gX#x(gX#z(gX#{(gX(a(gX(r(gX(y(gX(z(gX~O!g#vO!k(gX~P! uOR(nO!Q(mO!l#xO#S$dO!|!{a!S!{a~O!x!{a%h!{a!_!{a#i!{a#j!{a(T!{a~P!#vO!x(rO~OPYOQYOSfOd!jOe!iOpkOrYOskOtkOzkO|YO!OYO!SWO!WkO!XkO!_XO!iuO!lZO!oYO!pYO!qYO!svO!u!gO!x!hO$W!kO$niO(T!dO(VTO(YUO(aVO(o[O~Oh%VOp%WOr%XOs$tOt$tOz%YO|%ZO!O<sO!S${O!_$|O!i>VO!l$xO#j<yO$W%`O$t<uO$v<wO$y%aO(T(vO(VTO(YUO(a$uO(y$}O(z%PO~O#k(xO~O![(zO!k(kP~P%[O(e(|O(o[O~O!S)OO!l#xO(e(|O(o[O~OP<UOQ<UOSfOd>ROe!iOpkOr<UOskOtkOzkO|<UO!O<UO!SWO!WkO!XkO!_!eO!i<XO!lZO!o<UO!p<UO!q<UO!s<YO!u<]O!x!hO$W!kO$n>PO(T)]O(VTO(YUO(aVO(o[O~O!]$_Oa$qa'z$qa'w$qa!k$qa!Y$qa!_$qa%i$qa!g$qa~Ol)dO~P!&zOh%VOp%WOr%XOs$tOt$tOz%YO|%ZO!O%]O!S${O!_$|O!i%bO!l$xO#j%cO$W%`O$t%^O$v%_O$y%aO(T(vO(VTO(YUO(a$uO(y$}O(z%PO~Og(pP~P!,TO!Q)iO!g)hO!_$^X$Z$^X$]$^X$_$^X$f$^X~O!g)hO!_({X$Z({X$]({X$_({X$f({X~O!Q)iO~P!.^O!Q)iO!_({X$Z({X$]({X$_({X$f({X~O!_)kO$Z)oO$])jO$_)jO$f)pO~O![)sO~P!)[O$]$hO$_$gO$f)wO~On$zX!Q$zX#S$zX'y$zX(y$zX(z$zX~OgmXg$zXnmX!]mX#`mX~P!0SOx)yO(b)zO(c)|O~On*VO!Q*OO'y*PO(y$}O(z%PO~Og)}O~P!1WOg*WO~Oh%VOr%XOs$tOt$tOz%YO|%ZO!O<sO!S*YO!_*ZO!i>VO!l$xO#j<yO$W%`O$t<uO$v<wO$y%aO(VTO(YUO(a$uO(y$}O(z%PO~Op*`O![*^O(T*XO!k)OP~P!1uO#k*aO~O!l*bO~Oh%VOp%WOr%XOs$tOt$tOz%YO|%ZO!O<sO!S${O!_$|O!i>VO!l$xO#j<yO$W%`O$t<uO$v<wO$y%aO(T*dO(VTO(YUO(a$uO(y$}O(z%PO~O![*gO!Y)PP~P!3tOr*sOs!nO!S*iO!b*qO!c*kO!d*kO!l*bO#[*rO%`*mO(U!lO(VTO(YUO(e!mO~O!^*pO~P!5iO#S$dOn(`X!Q(`X'y(`X(y(`X(z(`X!](`X#`(`X~Og(`X$O(`X~P!6kOn*xO#`*wOg(_X!](_X~O!]*yOg(^X~Oj%dOk%dOl%dO(T&ZOg(^P~Os*|O~Og)}O(T&ZO~O!l+SO~O(T(vO~Op+WO!S%hO![#iO!_%iO!|]O#i#lO#j#iO(T%gO!k(sP~O!g#vO#k+XO~O!S%hO![+ZO!](^O!_%iO(T%gO!Y(vP~Os'[O!S+]O![+[O(VTO(YUO(e(|O~O!^(xP~P!9|O!]+^Oa)TX'z)TX~OP$[OR#zO[$cOj$ROr$aO!Q#yO!S#{O!l#xO!p$[O#R$RO#n$OO#o$PO#p$PO#q$PO#r$QO#s$RO#t$RO#u$bO#v$SO#x$UO#z$WO#{$XO(aVO(r$YO(y#|O(z#}O~Oa!ja!]!ja'z!ja'w!ja!Y!ja!k!jav!ja!_!ja%i!ja!g!ja~P!:tOR#zO!Q#yO!S#{O!l#xO(aVOP!ra[!raj!rar!ra!]!ra!p!ra#R!ra#n!ra#o!ra#p!ra#q!ra#r!ra#s!ra#t!ra#u!ra#v!ra#x!ra#z!ra#{!ra(r!ra(y!ra(z!ra~Oa!ra'z!ra'w!ra!Y!ra!k!rav!ra!_!ra%i!ra!g!ra~P!=[OR#zO!Q#yO!S#{O!l#xO(aVOP!ta[!taj!tar!ta!]!ta!p!ta#R!ta#n!ta#o!ta#p!ta#q!ta#r!ta#s!ta#t!ta#u!ta#v!ta#x!ta#z!ta#{!ta(r!ta(y!ta(z!ta~Oa!ta'z!ta'w!ta!Y!ta!k!tav!ta!_!ta%i!ta!g!ta~P!?rOh%VOn+gO!_'`O%i+fO~O!g+iOa(]X!_(]X'z(]X!](]X~Oa%nO!_XO'z%nO~Oh%VO!l%eO~Oh%VO!l%eO(T%gO~O!g#vO#k(xO~Ob+tO%j+uO(T+qO(VTO(YUO!^)XP~O!]+vO`)WX~O[+zO~O`+{O~O!_&PO(T%gO(U!lO`)WP~O%j,OO~P;SOh%VO#`,SO~Oh%VOn,VO!_$|O~O!_,XO~O!Q,ZO!_XO~O%n%vO~O!x,`O~Oe,eO~Ob,fO(T#nO(VTO(YUO!^)VP~Oe%}O~O%j!QO(T&ZO~P=gO[,kO`,jO~OPYOQYOSfOdzOeyOpkOrYOskOtkOzkO|YO!OYO!SWO!WkO!XkO!iuO!lZO!oYO!pYO!qYO!svO!xxO!|]O$niO%h}O(VTO(YUO(aVO(o[O~O!_!eO!u!gO$W!kO(T!dO~P!FyO`,jOa%nO'z%nO~OPYOQYOSfOd!jOe!iOpkOrYOskOtkOzkO|YO!OYO!SWO!WkO!XkO!_!eO!iuO!lZO!oYO!pYO!qYO!svO!x!hO$W!kO$niO(T!dO(VTO(YUO(aVO(o[O~Oa,pOl!OO!uwO%l!OO%m!OO%n!OO~P!IcO!l&oO~O&^,vO~O!_,xO~O&o,zO&q,{OP&laQ&laS&laY&laa&lad&lae&lal&lap&lar&las&lat&laz&la|&la!O&la!S&la!W&la!X&la!_&la!i&la!l&la!o&la!p&la!q&la!s&la!u&la!x&la!|&la$W&la$n&la%h&la%j&la%l&la%m&la%n&la%q&la%s&la%v&la%w&la%y&la&W&la&^&la&`&la&b&la&d&la&g&la&m&la&s&la&u&la&w&la&y&la&{&la'w&la(T&la(V&la(Y&la(a&la(o&la!^&la&e&lab&la&j&la~O(T-QO~Oh!eX!]!RX!^!RX!g!RX!g!eX!l!eX#`!RX~O!]!eX!^!eX~P#!iO!g-VO#`-UOh(jX!]#hX!^#hX!g(jX!l(jX~O!](jX!^(jX~P##[Oh%VO!g-XO!l%eO!]!aX!^!aX~Os!nO!S!oO(VTO(YUO(e!mO~OP<UOQ<UOSfOd>ROe!iOpkOr<UOskOtkOzkO|<UO!O<UO!SWO!WkO!XkO!_!eO!i<XO!lZO!o<UO!p<UO!q<UO!s<YO!u<]O!x!hO$W!kO$n>PO(VTO(YUO(aVO(o[O~O(T=QO~P#$qO!]-]O!^(iX~O!^-_O~O!g-VO#`-UO!]#hX!^#hX~O!]-`O!^(xX~O!^-bO~O!c-cO!d-cO(U!lO~P#$`O!^-fO~P'_On-iO!_'`O~O!Y-nO~Os!{a!b!{a!c!{a!d!{a#T!{a#U!{a#V!{a#W!{a#X!{a#[!{a#]!{a(U!{a(V!{a(Y!{a(e!{a(o!{a~P!#vO!p-sO#`-qO~PChO!c-uO!d-uO(U!lO~PDWOa%nO#`-qO'z%nO~Oa%nO!g#vO#`-qO'z%nO~Oa%nO!g#vO!p-sO#`-qO'z%nO(r'pO~O(P'xO(Q'xO(R-zO~Ov-{O~O!Y'Wa!]'Wa~P!:tO![.PO!Y'WX!]'WX~P%[O!](VO!Y(ha~O!Y(ha~PHRO!](^O!Y(va~O!S%hO![.TO!_%iO(T%gO!Y'^X!]'^X~O#`.VO!](ta!k(taa(ta'z(ta~O!g#vO~P#,wO!](jO!k(sa~O!S%hO!_%iO#j.ZO(T%gO~Op.`O!S%hO![.]O!_%iO!|]O#i._O#j.]O(T%gO!]'aX!k'aX~OR.dO!l#xO~Oh%VOn.gO!_'`O%i.fO~Oa#ci!]#ci'z#ci'w#ci!Y#ci!k#civ#ci!_#ci%i#ci!g#ci~P!:tOn>]O!Q*OO'y*PO(y$}O(z%PO~O#k#_aa#_a#`#_a'z#_a!]#_a!k#_a!_#_a!Y#_a~P#/sO#k(`XP(`XR(`X[(`Xa(`Xj(`Xr(`X!S(`X!l(`X!p(`X#R(`X#n(`X#o(`X#p(`X#q(`X#r(`X#s(`X#t(`X#u(`X#v(`X#x(`X#z(`X#{(`X'z(`X(a(`X(r(`X!k(`X!Y(`X'w(`Xv(`X!_(`X%i(`X!g(`X~P!6kO!].tO!k(kX~P!:tO!k.wO~O!Y.yO~OP$[OR#zO!Q#yO!S#{O!l#xO!p$[O(aVO[#mia#mij#mir#mi!]#mi#R#mi#o#mi#p#mi#q#mi#r#mi#s#mi#t#mi#u#mi#v#mi#x#mi#z#mi#{#mi'z#mi(r#mi(y#mi(z#mi'w#mi!Y#mi!k#miv#mi!_#mi%i#mi!g#mi~O#n#mi~P#3cO#n$OO~P#3cOP$[OR#zOr$aO!Q#yO!S#{O!l#xO!p$[O#n$OO#o$PO#p$PO#q$PO(aVO[#mia#mij#mi!]#mi#R#mi#s#mi#t#mi#u#mi#v#mi#x#mi#z#mi#{#mi'z#mi(r#mi(y#mi(z#mi'w#mi!Y#mi!k#miv#mi!_#mi%i#mi!g#mi~O#r#mi~P#6QO#r$QO~P#6QOP$[OR#zO[$cOj$ROr$aO!Q#yO!S#{O!l#xO!p$[O#R$RO#n$OO#o$PO#p$PO#q$PO#r$QO#s$RO#t$RO#u$bO(aVOa#mi!]#mi#x#mi#z#mi#{#mi'z#mi(r#mi(y#mi(z#mi'w#mi!Y#mi!k#miv#mi!_#mi%i#mi!g#mi~O#v#mi~P#8oOP$[OR#zO[$cOj$ROr$aO!Q#yO!S#{O!l#xO!p$[O#R$RO#n$OO#o$PO#p$PO#q$PO#r$QO#s$RO#t$RO#u$bO#v$SO(aVO(z#}Oa#mi!]#mi#z#mi#{#mi'z#mi(r#mi(y#mi'w#mi!Y#mi!k#miv#mi!_#mi%i#mi!g#mi~O#x$UO~P#;VO#x#mi~P#;VO#v$SO~P#8oOP$[OR#zO[$cOj$ROr$aO!Q#yO!S#{O!l#xO!p$[O#R$RO#n$OO#o$PO#p$PO#q$PO#r$QO#s$RO#t$RO#u$bO#v$SO#x$UO(aVO(y#|O(z#}Oa#mi!]#mi#{#mi'z#mi(r#mi'w#mi!Y#mi!k#miv#mi!_#mi%i#mi!g#mi~O#z#mi~P#={O#z$WO~P#={OP]XR]X[]Xj]Xr]X!Q]X!S]X!l]X!p]X#R]X#S]X#`]X#kfX#n]X#o]X#p]X#q]X#r]X#s]X#t]X#u]X#v]X#x]X#z]X#{]X$Q]X(a]X(r]X(y]X(z]X!]]X!^]X~O$O]X~P#@jOP$[OR#zO[<mOj<bOr<kO!Q#yO!S#{O!l#xO!p$[O#R<bO#n<_O#o<`O#p<`O#q<`O#r<aO#s<bO#t<bO#u<lO#v<cO#x<eO#z<gO#{<hO(aVO(r$YO(y#|O(z#}O~O$O.{O~P#BwO#S$dO#`<nO$Q<nO$O(gX!^(gX~P! uOa'da!]'da'z'da'w'da!k'da!Y'dav'da!_'da%i'da!g'da~P!:tO[#mia#mij#mir#mi!]#mi#R#mi#r#mi#s#mi#t#mi#u#mi#v#mi#x#mi#z#mi#{#mi'z#mi(r#mi'w#mi!Y#mi!k#miv#mi!_#mi%i#mi!g#mi~OP$[OR#zO!Q#yO!S#{O!l#xO!p$[O#n$OO#o$PO#p$PO#q$PO(aVO(y#mi(z#mi~P#EyOn>]O!Q*OO'y*PO(y$}O(z%POP#miR#mi!S#mi!l#mi!p#mi#n#mi#o#mi#p#mi#q#mi(a#mi~P#EyO!]/POg(pX~P!1WOg/RO~Oa$Pi!]$Pi'z$Pi'w$Pi!Y$Pi!k$Piv$Pi!_$Pi%i$Pi!g$Pi~P!:tO$]/SO$_/SO~O$]/TO$_/TO~O!g)hO#`/UO!_$cX$Z$cX$]$cX$_$cX$f$cX~O![/VO~O!_)kO$Z/XO$])jO$_)jO$f/YO~O!]<iO!^(fX~P#BwO!^/ZO~O!g)hO$f({X~O$f/]O~Ov/^O~P!&zOx)yO(b)zO(c/aO~O!S/dO~O(y$}On%aa!Q%aa'y%aa(z%aa!]%aa#`%aa~Og%aa$O%aa~P#L{O(z%POn%ca!Q%ca'y%ca(y%ca!]%ca#`%ca~Og%ca$O%ca~P#MnO!]fX!gfX!kfX!k$zX(rfX~P!0SOp%WO![/mO!](^O(T/lO!Y(vP!Y)PP~P!1uOr*sO!b*qO!c*kO!d*kO!l*bO#[*rO%`*mO(U!lO(VTO(YUO~Os<}O!S/nO![+[O!^*pO(e<|O!^(xP~P$ [O!k/oO~P#/sO!]/pO!g#vO(r'pO!k)OX~O!k/uO~OnoX!QoX'yoX(yoX(zoX~O!g#vO!koX~P$#OOp/wO!S%hO![*^O!_%iO(T%gO!k)OP~O#k/xO~O!Y$zX!]$zX!g%RX~P!0SO!]/yO!Y)PX~P#/sO!g/{O~O!Y/}O~OpkO(T0OO~P.iOh%VOr0TO!g#vO!l%eO(r'pO~O!g+iO~Oa%nO!]0XO'z%nO~O!^0ZO~P!5iO!c0[O!d0[O(U!lO~P#$`Os!nO!S0]O(VTO(YUO(e!mO~O#[0_O~Og%aa!]%aa#`%aa$O%aa~P!1WOg%ca!]%ca#`%ca$O%ca~P!1WOj%dOk%dOl%dO(T&ZOg'mX!]'mX~O!]*yOg(^a~Og0hO~On0jO#`0iOg(_a!](_a~OR0kO!Q0kO!S0lO#S$dOn}a'y}a(y}a(z}a!]}a#`}a~Og}a$O}a~P$(cO!Q*OO'y*POn$sa(y$sa(z$sa!]$sa#`$sa~Og$sa$O$sa~P$)_O!Q*OO'y*POn$ua(y$ua(z$ua!]$ua#`$ua~Og$ua$O$ua~P$*QO#k0oO~Og%Ta!]%Ta#`%Ta$O%Ta~P!1WO!g#vO~O#k0rO~O!]+^Oa)Ta'z)Ta~OR#zO!Q#yO!S#{O!l#xO(aVOP!ri[!rij!rir!ri!]!ri!p!ri#R!ri#n!ri#o!ri#p!ri#q!ri#r!ri#s!ri#t!ri#u!ri#v!ri#x!ri#z!ri#{!ri(r!ri(y!ri(z!ri~Oa!ri'z!ri'w!ri!Y!ri!k!riv!ri!_!ri%i!ri!g!ri~P$+oOh%VOr%XOs$tOt$tOz%YO|%ZO!O<sO!S${O!_$|O!i>VO!l$xO#j<yO$W%`O$t<uO$v<wO$y%aO(VTO(YUO(a$uO(y$}O(z%PO~Op0{O%]0|O(T0zO~P$.VO!g+iOa(]a!_(]a'z(]a!](]a~O#k1SO~O[]X!]fX!^fX~O!]1TO!^)XX~O!^1VO~O[1WO~Ob1YO(T+qO(VTO(YUO~O!_&PO(T%gO`'uX!]'uX~O!]+vO`)Wa~O!k1]O~P!:tO[1`O~O`1aO~O#`1fO~On1iO!_$|O~O(e(|O!^)UP~Oh%VOn1rO!_1oO%i1qO~O[1|O!]1zO!^)VX~O!^1}O~O`2POa%nO'z%nO~O(T#nO(VTO(YUO~O#S$dO#`$eO$Q$eOP(gXR(gX[(gXr(gX!Q(gX!S(gX!](gX!l(gX!p(gX#R(gX#n(gX#o(gX#p(gX#q(gX#r(gX#s(gX#t(gX#u(gX#v(gX#x(gX#z(gX#{(gX(a(gX(r(gX(y(gX(z(gX~Oj2SO&[2TOa(gX~P$3pOj2SO#`$eO&[2TO~Oa2VO~P%[Oa2XO~O&e2[OP&ciQ&ciS&ciY&cia&cid&cie&cil&cip&cir&cis&cit&ciz&ci|&ci!O&ci!S&ci!W&ci!X&ci!_&ci!i&ci!l&ci!o&ci!p&ci!q&ci!s&ci!u&ci!x&ci!|&ci$W&ci$n&ci%h&ci%j&ci%l&ci%m&ci%n&ci%q&ci%s&ci%v&ci%w&ci%y&ci&W&ci&^&ci&`&ci&b&ci&d&ci&g&ci&m&ci&s&ci&u&ci&w&ci&y&ci&{&ci'w&ci(T&ci(V&ci(Y&ci(a&ci(o&ci!^&cib&ci&j&ci~Ob2bO!^2`O&j2aO~P`O!_XO!l2dO~O&q,{OP&liQ&liS&liY&lia&lid&lie&lil&lip&lir&lis&lit&liz&li|&li!O&li!S&li!W&li!X&li!_&li!i&li!l&li!o&li!p&li!q&li!s&li!u&li!x&li!|&li$W&li$n&li%h&li%j&li%l&li%m&li%n&li%q&li%s&li%v&li%w&li%y&li&W&li&^&li&`&li&b&li&d&li&g&li&m&li&s&li&u&li&w&li&y&li&{&li'w&li(T&li(V&li(Y&li(a&li(o&li!^&li&e&lib&li&j&li~O!Y2jO~O!]!aa!^!aa~P#BwOs!nO!S!oO![2pO(e!mO!]'XX!^'XX~P@nO!]-]O!^(ia~O!]'_X!^'_X~P!9|O!]-`O!^(xa~O!^2wO~P'_Oa%nO#`3QO'z%nO~Oa%nO!g#vO#`3QO'z%nO~Oa%nO!g#vO!p3UO#`3QO'z%nO(r'pO~Oa%nO'z%nO~P!:tO!]$_Ov$qa~O!Y'Wi!]'Wi~P!:tO!](VO!Y(hi~O!](^O!Y(vi~O!Y(wi!](wi~P!:tO!](ti!k(tia(ti'z(ti~P!:tO#`3WO!](ti!k(tia(ti'z(ti~O!](jO!k(si~O!S%hO!_%iO!|]O#i3]O#j3[O(T%gO~O!S%hO!_%iO#j3[O(T%gO~On3dO!_'`O%i3cO~Oh%VOn3dO!_'`O%i3cO~O#k%aaP%aaR%aa[%aaa%aaj%aar%aa!S%aa!l%aa!p%aa#R%aa#n%aa#o%aa#p%aa#q%aa#r%aa#s%aa#t%aa#u%aa#v%aa#x%aa#z%aa#{%aa'z%aa(a%aa(r%aa!k%aa!Y%aa'w%aav%aa!_%aa%i%aa!g%aa~P#L{O#k%caP%caR%ca[%caa%caj%car%ca!S%ca!l%ca!p%ca#R%ca#n%ca#o%ca#p%ca#q%ca#r%ca#s%ca#t%ca#u%ca#v%ca#x%ca#z%ca#{%ca'z%ca(a%ca(r%ca!k%ca!Y%ca'w%cav%ca!_%ca%i%ca!g%ca~P#MnO#k%aaP%aaR%aa[%aaa%aaj%aar%aa!S%aa!]%aa!l%aa!p%aa#R%aa#n%aa#o%aa#p%aa#q%aa#r%aa#s%aa#t%aa#u%aa#v%aa#x%aa#z%aa#{%aa'z%aa(a%aa(r%aa!k%aa!Y%aa'w%aa#`%aav%aa!_%aa%i%aa!g%aa~P#/sO#k%caP%caR%ca[%caa%caj%car%ca!S%ca!]%ca!l%ca!p%ca#R%ca#n%ca#o%ca#p%ca#q%ca#r%ca#s%ca#t%ca#u%ca#v%ca#x%ca#z%ca#{%ca'z%ca(a%ca(r%ca!k%ca!Y%ca'w%ca#`%cav%ca!_%ca%i%ca!g%ca~P#/sO#k}aP}a[}aa}aj}ar}a!l}a!p}a#R}a#n}a#o}a#p}a#q}a#r}a#s}a#t}a#u}a#v}a#x}a#z}a#{}a'z}a(a}a(r}a!k}a!Y}a'w}av}a!_}a%i}a!g}a~P$(cO#k$saP$saR$sa[$saa$saj$sar$sa!S$sa!l$sa!p$sa#R$sa#n$sa#o$sa#p$sa#q$sa#r$sa#s$sa#t$sa#u$sa#v$sa#x$sa#z$sa#{$sa'z$sa(a$sa(r$sa!k$sa!Y$sa'w$sav$sa!_$sa%i$sa!g$sa~P$)_O#k$uaP$uaR$ua[$uaa$uaj$uar$ua!S$ua!l$ua!p$ua#R$ua#n$ua#o$ua#p$ua#q$ua#r$ua#s$ua#t$ua#u$ua#v$ua#x$ua#z$ua#{$ua'z$ua(a$ua(r$ua!k$ua!Y$ua'w$uav$ua!_$ua%i$ua!g$ua~P$*QO#k%TaP%TaR%Ta[%Taa%Taj%Tar%Ta!S%Ta!]%Ta!l%Ta!p%Ta#R%Ta#n%Ta#o%Ta#p%Ta#q%Ta#r%Ta#s%Ta#t%Ta#u%Ta#v%Ta#x%Ta#z%Ta#{%Ta'z%Ta(a%Ta(r%Ta!k%Ta!Y%Ta'w%Ta#`%Tav%Ta!_%Ta%i%Ta!g%Ta~P#/sOa#cq!]#cq'z#cq'w#cq!Y#cq!k#cqv#cq!_#cq%i#cq!g#cq~P!:tO![3lO!]'YX!k'YX~P%[O!].tO!k(ka~O!].tO!k(ka~P!:tO!Y3oO~O$O!na!^!na~PKlO$O!ja!]!ja!^!ja~P#BwO$O!ra!^!ra~P!=[O$O!ta!^!ta~P!?rOg']X!]']X~P!,TO!]/POg(pa~OSfO!_4TO$d4UO~O!^4YO~Ov4ZO~P#/sOa$mq!]$mq'z$mq'w$mq!Y$mq!k$mqv$mq!_$mq%i$mq!g$mq~P!:tO!Y4]O~P!&zO!S4^O~O!Q*OO'y*PO(z%POn'ia(y'ia!]'ia#`'ia~Og'ia$O'ia~P%-fO!Q*OO'y*POn'ka(y'ka(z'ka!]'ka#`'ka~Og'ka$O'ka~P%.XO(r$YO~P#/sO!YfX!Y$zX!]fX!]$zX!g%RX#`fX~P!0SOp%WO(T=WO~P!1uOp4bO!S%hO![4aO!_%iO(T%gO!]'eX!k'eX~O!]/pO!k)Oa~O!]/pO!g#vO!k)Oa~O!]/pO!g#vO(r'pO!k)Oa~Og$|i!]$|i#`$|i$O$|i~P!1WO![4jO!Y'gX!]'gX~P!3tO!]/yO!Y)Pa~O!]/yO!Y)Pa~P#/sOP]XR]X[]Xj]Xr]X!Q]X!S]X!Y]X!]]X!l]X!p]X#R]X#S]X#`]X#kfX#n]X#o]X#p]X#q]X#r]X#s]X#t]X#u]X#v]X#x]X#z]X#{]X$Q]X(a]X(r]X(y]X(z]X~Oj%YX!g%YX~P%2OOj4oO!g#vO~Oh%VO!g#vO!l%eO~Oh%VOr4tO!l%eO(r'pO~Or4yO!g#vO(r'pO~Os!nO!S4zO(VTO(YUO(e!mO~O(y$}On%ai!Q%ai'y%ai(z%ai!]%ai#`%ai~Og%ai$O%ai~P%5oO(z%POn%ci!Q%ci'y%ci(y%ci!]%ci#`%ci~Og%ci$O%ci~P%6bOg(_i!](_i~P!1WO#`5QOg(_i!](_i~P!1WO!k5VO~Oa$oq!]$oq'z$oq'w$oq!Y$oq!k$oqv$oq!_$oq%i$oq!g$oq~P!:tO!Y5ZO~O!]5[O!_)QX~P#/sOa$zX!_$zX%^]X'z$zX!]$zX~P!0SO%^5_OaoX!_oX'zoX!]oX~P$#OOp5`O(T#nO~O%^5_O~Ob5fO%j5gO(T+qO(VTO(YUO!]'tX!^'tX~O!]1TO!^)Xa~O[5kO~O`5lO~O[5pO~Oa%nO'z%nO~P#/sO!]5uO#`5wO!^)UX~O!^5xO~Or6OOs!nO!S*iO!b!yO!c!vO!d!vO!|<VO#T!pO#U!pO#V!pO#W!pO#X!pO#[5}O#]!zO(U!lO(VTO(YUO(e!mO(o!sO~O!^5|O~P%;eOn6TO!_1oO%i6SO~Oh%VOn6TO!_1oO%i6SO~Ob6[O(T#nO(VTO(YUO!]'sX!^'sX~O!]1zO!^)Va~O(VTO(YUO(e6^O~O`6bO~Oj6eO&[6fO~PNXO!k6gO~P%[Oa6iO~Oa6iO~P%[Ob2bO!^6nO&j2aO~P`O!g6pO~O!g6rOh(ji!](ji!^(ji!g(ji!l(jir(ji(r(ji~O!]#hi!^#hi~P#BwO#`6sO!]#hi!^#hi~O!]!ai!^!ai~P#BwOa%nO#`6|O'z%nO~Oa%nO!g#vO#`6|O'z%nO~O!](tq!k(tqa(tq'z(tq~P!:tO!](jO!k(sq~O!S%hO!_%iO#j7TO(T%gO~O!_'`O%i7WO~On7[O!_'`O%i7WO~O#k'iaP'iaR'ia['iaa'iaj'iar'ia!S'ia!l'ia!p'ia#R'ia#n'ia#o'ia#p'ia#q'ia#r'ia#s'ia#t'ia#u'ia#v'ia#x'ia#z'ia#{'ia'z'ia(a'ia(r'ia!k'ia!Y'ia'w'iav'ia!_'ia%i'ia!g'ia~P%-fO#k'kaP'kaR'ka['kaa'kaj'kar'ka!S'ka!l'ka!p'ka#R'ka#n'ka#o'ka#p'ka#q'ka#r'ka#s'ka#t'ka#u'ka#v'ka#x'ka#z'ka#{'ka'z'ka(a'ka(r'ka!k'ka!Y'ka'w'kav'ka!_'ka%i'ka!g'ka~P%.XO#k$|iP$|iR$|i[$|ia$|ij$|ir$|i!S$|i!]$|i!l$|i!p$|i#R$|i#n$|i#o$|i#p$|i#q$|i#r$|i#s$|i#t$|i#u$|i#v$|i#x$|i#z$|i#{$|i'z$|i(a$|i(r$|i!k$|i!Y$|i'w$|i#`$|iv$|i!_$|i%i$|i!g$|i~P#/sO#k%aiP%aiR%ai[%aia%aij%air%ai!S%ai!l%ai!p%ai#R%ai#n%ai#o%ai#p%ai#q%ai#r%ai#s%ai#t%ai#u%ai#v%ai#x%ai#z%ai#{%ai'z%ai(a%ai(r%ai!k%ai!Y%ai'w%aiv%ai!_%ai%i%ai!g%ai~P%5oO#k%ciP%ciR%ci[%cia%cij%cir%ci!S%ci!l%ci!p%ci#R%ci#n%ci#o%ci#p%ci#q%ci#r%ci#s%ci#t%ci#u%ci#v%ci#x%ci#z%ci#{%ci'z%ci(a%ci(r%ci!k%ci!Y%ci'w%civ%ci!_%ci%i%ci!g%ci~P%6bO!]'Ya!k'Ya~P!:tO!].tO!k(ki~O$O#ci!]#ci!^#ci~P#BwOP$[OR#zO!Q#yO!S#{O!l#xO!p$[O(aVO[#mij#mir#mi#R#mi#o#mi#p#mi#q#mi#r#mi#s#mi#t#mi#u#mi#v#mi#x#mi#z#mi#{#mi$O#mi(r#mi(y#mi(z#mi!]#mi!^#mi~O#n#mi~P%NdO#n<_O~P%NdOP$[OR#zOr<kO!Q#yO!S#{O!l#xO!p$[O#n<_O#o<`O#p<`O#q<`O(aVO[#mij#mi#R#mi#s#mi#t#mi#u#mi#v#mi#x#mi#z#mi#{#mi$O#mi(r#mi(y#mi(z#mi!]#mi!^#mi~O#r#mi~P&!lO#r<aO~P&!lOP$[OR#zO[<mOj<bOr<kO!Q#yO!S#{O!l#xO!p$[O#R<bO#n<_O#o<`O#p<`O#q<`O#r<aO#s<bO#t<bO#u<lO(aVO#x#mi#z#mi#{#mi$O#mi(r#mi(y#mi(z#mi!]#mi!^#mi~O#v#mi~P&$tOP$[OR#zO[<mOj<bOr<kO!Q#yO!S#{O!l#xO!p$[O#R<bO#n<_O#o<`O#p<`O#q<`O#r<aO#s<bO#t<bO#u<lO#v<cO(aVO(z#}O#z#mi#{#mi$O#mi(r#mi(y#mi!]#mi!^#mi~O#x<eO~P&&uO#x#mi~P&&uO#v<cO~P&$tOP$[OR#zO[<mOj<bOr<kO!Q#yO!S#{O!l#xO!p$[O#R<bO#n<_O#o<`O#p<`O#q<`O#r<aO#s<bO#t<bO#u<lO#v<cO#x<eO(aVO(y#|O(z#}O#{#mi$O#mi(r#mi!]#mi!^#mi~O#z#mi~P&)UO#z<gO~P&)UOa#|y!]#|y'z#|y'w#|y!Y#|y!k#|yv#|y!_#|y%i#|y!g#|y~P!:tO[#mij#mir#mi#R#mi#r#mi#s#mi#t#mi#u#mi#v#mi#x#mi#z#mi#{#mi$O#mi(r#mi!]#mi!^#mi~OP$[OR#zO!Q#yO!S#{O!l#xO!p$[O#n<_O#o<`O#p<`O#q<`O(aVO(y#mi(z#mi~P&,QOn>^O!Q*OO'y*PO(y$}O(z%POP#miR#mi!S#mi!l#mi!p#mi#n#mi#o#mi#p#mi#q#mi(a#mi~P&,QO#S$dOP(`XR(`X[(`Xj(`Xn(`Xr(`X!Q(`X!S(`X!l(`X!p(`X#R(`X#n(`X#o(`X#p(`X#q(`X#r(`X#s(`X#t(`X#u(`X#v(`X#x(`X#z(`X#{(`X$O(`X'y(`X(a(`X(r(`X(y(`X(z(`X!](`X!^(`X~O$O$Pi!]$Pi!^$Pi~P#BwO$O!ri!^!ri~P$+oOg']a!]']a~P!1WO!^7nO~O!]'da!^'da~P#BwO!Y7oO~P#/sO!g#vO(r'pO!]'ea!k'ea~O!]/pO!k)Oi~O!]/pO!g#vO!k)Oi~Og$|q!]$|q#`$|q$O$|q~P!1WO!Y'ga!]'ga~P#/sO!g7vO~O!]/yO!Y)Pi~P#/sO!]/yO!Y)Pi~O!Y7yO~Oh%VOr8OO!l%eO(r'pO~Oj8QO!g#vO~Or8TO!g#vO(r'pO~O!Q*OO'y*PO(z%POn'ja(y'ja!]'ja#`'ja~Og'ja$O'ja~P&5RO!Q*OO'y*POn'la(y'la(z'la!]'la#`'la~Og'la$O'la~P&5tOg(_q!](_q~P!1WO#`8VOg(_q!](_q~P!1WO!Y8WO~Og%Oq!]%Oq#`%Oq$O%Oq~P!1WOa$oy!]$oy'z$oy'w$oy!Y$oy!k$oyv$oy!_$oy%i$oy!g$oy~P!:tO!g6rO~O!]5[O!_)Qa~O!_'`OP$TaR$Ta[$Taj$Tar$Ta!Q$Ta!S$Ta!]$Ta!l$Ta!p$Ta#R$Ta#n$Ta#o$Ta#p$Ta#q$Ta#r$Ta#s$Ta#t$Ta#u$Ta#v$Ta#x$Ta#z$Ta#{$Ta(a$Ta(r$Ta(y$Ta(z$Ta~O%i7WO~P&8fO%^8[Oa%[i!_%[i'z%[i!]%[i~Oa#cy!]#cy'z#cy'w#cy!Y#cy!k#cyv#cy!_#cy%i#cy!g#cy~P!:tO[8^O~Ob8`O(T+qO(VTO(YUO~O!]1TO!^)Xi~O`8dO~O(e(|O!]'pX!^'pX~O!]5uO!^)Ua~O!^8nO~P%;eO(o!sO~P$&YO#[8oO~O!_1oO~O!_1oO%i8qO~On8tO!_1oO%i8qO~O[8yO!]'sa!^'sa~O!]1zO!^)Vi~O!k8}O~O!k9OO~O!k9RO~O!k9RO~P%[Oa9TO~O!g9UO~O!k9VO~O!](wi!^(wi~P#BwOa%nO#`9_O'z%nO~O!](ty!k(tya(ty'z(ty~P!:tO!](jO!k(sy~O%i9bO~P&8fO!_'`O%i9bO~O#k$|qP$|qR$|q[$|qa$|qj$|qr$|q!S$|q!]$|q!l$|q!p$|q#R$|q#n$|q#o$|q#p$|q#q$|q#r$|q#s$|q#t$|q#u$|q#v$|q#x$|q#z$|q#{$|q'z$|q(a$|q(r$|q!k$|q!Y$|q'w$|q#`$|qv$|q!_$|q%i$|q!g$|q~P#/sO#k'jaP'jaR'ja['jaa'jaj'jar'ja!S'ja!l'ja!p'ja#R'ja#n'ja#o'ja#p'ja#q'ja#r'ja#s'ja#t'ja#u'ja#v'ja#x'ja#z'ja#{'ja'z'ja(a'ja(r'ja!k'ja!Y'ja'w'jav'ja!_'ja%i'ja!g'ja~P&5RO#k'laP'laR'la['laa'laj'lar'la!S'la!l'la!p'la#R'la#n'la#o'la#p'la#q'la#r'la#s'la#t'la#u'la#v'la#x'la#z'la#{'la'z'la(a'la(r'la!k'la!Y'la'w'lav'la!_'la%i'la!g'la~P&5tO#k%OqP%OqR%Oq[%Oqa%Oqj%Oqr%Oq!S%Oq!]%Oq!l%Oq!p%Oq#R%Oq#n%Oq#o%Oq#p%Oq#q%Oq#r%Oq#s%Oq#t%Oq#u%Oq#v%Oq#x%Oq#z%Oq#{%Oq'z%Oq(a%Oq(r%Oq!k%Oq!Y%Oq'w%Oq#`%Oqv%Oq!_%Oq%i%Oq!g%Oq~P#/sO!]'Yi!k'Yi~P!:tO$O#cq!]#cq!^#cq~P#BwO(y$}OP%aaR%aa[%aaj%aar%aa!S%aa!l%aa!p%aa#R%aa#n%aa#o%aa#p%aa#q%aa#r%aa#s%aa#t%aa#u%aa#v%aa#x%aa#z%aa#{%aa$O%aa(a%aa(r%aa!]%aa!^%aa~On%aa!Q%aa'y%aa(z%aa~P&IyO(z%POP%caR%ca[%caj%car%ca!S%ca!l%ca!p%ca#R%ca#n%ca#o%ca#p%ca#q%ca#r%ca#s%ca#t%ca#u%ca#v%ca#x%ca#z%ca#{%ca$O%ca(a%ca(r%ca!]%ca!^%ca~On%ca!Q%ca'y%ca(y%ca~P&LQOn>^O!Q*OO'y*PO(z%PO~P&IyOn>^O!Q*OO'y*PO(y$}O~P&LQOR0kO!Q0kO!S0lO#S$dOP}a[}aj}an}ar}a!l}a!p}a#R}a#n}a#o}a#p}a#q}a#r}a#s}a#t}a#u}a#v}a#x}a#z}a#{}a$O}a'y}a(a}a(r}a(y}a(z}a!]}a!^}a~O!Q*OO'y*POP$saR$sa[$saj$san$sar$sa!S$sa!l$sa!p$sa#R$sa#n$sa#o$sa#p$sa#q$sa#r$sa#s$sa#t$sa#u$sa#v$sa#x$sa#z$sa#{$sa$O$sa(a$sa(r$sa(y$sa(z$sa!]$sa!^$sa~O!Q*OO'y*POP$uaR$ua[$uaj$uan$uar$ua!S$ua!l$ua!p$ua#R$ua#n$ua#o$ua#p$ua#q$ua#r$ua#s$ua#t$ua#u$ua#v$ua#x$ua#z$ua#{$ua$O$ua(a$ua(r$ua(y$ua(z$ua!]$ua!^$ua~On>^O!Q*OO'y*PO(y$}O(z%PO~OP%TaR%Ta[%Taj%Tar%Ta!S%Ta!l%Ta!p%Ta#R%Ta#n%Ta#o%Ta#p%Ta#q%Ta#r%Ta#s%Ta#t%Ta#u%Ta#v%Ta#x%Ta#z%Ta#{%Ta$O%Ta(a%Ta(r%Ta!]%Ta!^%Ta~P''VO$O$mq!]$mq!^$mq~P#BwO$O$oq!]$oq!^$oq~P#BwO!^9oO~O$O9pO~P!1WO!g#vO!]'ei!k'ei~O!g#vO(r'pO!]'ei!k'ei~O!]/pO!k)Oq~O!Y'gi!]'gi~P#/sO!]/yO!Y)Pq~Or9wO!g#vO(r'pO~O[9yO!Y9xO~P#/sO!Y9xO~Oj:PO!g#vO~Og(_y!](_y~P!1WO!]'na!_'na~P#/sOa%[q!_%[q'z%[q!]%[q~P#/sO[:UO~O!]1TO!^)Xq~O`:YO~O#`:ZO!]'pa!^'pa~O!]5uO!^)Ui~P#BwO!S:]O~O!_1oO%i:`O~O(VTO(YUO(e:eO~O!]1zO!^)Vq~O!k:hO~O!k:iO~O!k:jO~O!k:jO~P%[O#`:mO!]#hy!^#hy~O!]#hy!^#hy~P#BwO%i:rO~P&8fO!_'`O%i:rO~O$O#|y!]#|y!^#|y~P#BwOP$|iR$|i[$|ij$|ir$|i!S$|i!l$|i!p$|i#R$|i#n$|i#o$|i#p$|i#q$|i#r$|i#s$|i#t$|i#u$|i#v$|i#x$|i#z$|i#{$|i$O$|i(a$|i(r$|i!]$|i!^$|i~P''VO!Q*OO'y*PO(z%POP'iaR'ia['iaj'ian'iar'ia!S'ia!l'ia!p'ia#R'ia#n'ia#o'ia#p'ia#q'ia#r'ia#s'ia#t'ia#u'ia#v'ia#x'ia#z'ia#{'ia$O'ia(a'ia(r'ia(y'ia!]'ia!^'ia~O!Q*OO'y*POP'kaR'ka['kaj'kan'kar'ka!S'ka!l'ka!p'ka#R'ka#n'ka#o'ka#p'ka#q'ka#r'ka#s'ka#t'ka#u'ka#v'ka#x'ka#z'ka#{'ka$O'ka(a'ka(r'ka(y'ka(z'ka!]'ka!^'ka~O(y$}OP%aiR%ai[%aij%ain%air%ai!Q%ai!S%ai!l%ai!p%ai#R%ai#n%ai#o%ai#p%ai#q%ai#r%ai#s%ai#t%ai#u%ai#v%ai#x%ai#z%ai#{%ai$O%ai'y%ai(a%ai(r%ai(z%ai!]%ai!^%ai~O(z%POP%ciR%ci[%cij%cin%cir%ci!Q%ci!S%ci!l%ci!p%ci#R%ci#n%ci#o%ci#p%ci#q%ci#r%ci#s%ci#t%ci#u%ci#v%ci#x%ci#z%ci#{%ci$O%ci'y%ci(a%ci(r%ci(y%ci!]%ci!^%ci~O$O$oy!]$oy!^$oy~P#BwO$O#cy!]#cy!^#cy~P#BwO!g#vO!]'eq!k'eq~O!]/pO!k)Oy~O!Y'gq!]'gq~P#/sOr:|O!g#vO(r'pO~O[;QO!Y;PO~P#/sO!Y;PO~Og(_!R!](_!R~P!1WOa%[y!_%[y'z%[y!]%[y~P#/sO!]1TO!^)Xy~O!]5uO!^)Uq~O(T;XO~O!_1oO%i;[O~O!k;_O~O%i;dO~P&8fOP$|qR$|q[$|qj$|qr$|q!S$|q!l$|q!p$|q#R$|q#n$|q#o$|q#p$|q#q$|q#r$|q#s$|q#t$|q#u$|q#v$|q#x$|q#z$|q#{$|q$O$|q(a$|q(r$|q!]$|q!^$|q~P''VO!Q*OO'y*PO(z%POP'jaR'ja['jaj'jan'jar'ja!S'ja!l'ja!p'ja#R'ja#n'ja#o'ja#p'ja#q'ja#r'ja#s'ja#t'ja#u'ja#v'ja#x'ja#z'ja#{'ja$O'ja(a'ja(r'ja(y'ja!]'ja!^'ja~O!Q*OO'y*POP'laR'la['laj'lan'lar'la!S'la!l'la!p'la#R'la#n'la#o'la#p'la#q'la#r'la#s'la#t'la#u'la#v'la#x'la#z'la#{'la$O'la(a'la(r'la(y'la(z'la!]'la!^'la~OP%OqR%Oq[%Oqj%Oqr%Oq!S%Oq!l%Oq!p%Oq#R%Oq#n%Oq#o%Oq#p%Oq#q%Oq#r%Oq#s%Oq#t%Oq#u%Oq#v%Oq#x%Oq#z%Oq#{%Oq$O%Oq(a%Oq(r%Oq!]%Oq!^%Oq~P''VOg%e!Z!]%e!Z#`%e!Z$O%e!Z~P!1WO!Y;hO~P#/sOr;iO!g#vO(r'pO~O[;kO!Y;hO~P#/sO!]'pq!^'pq~P#BwO!]#h!Z!^#h!Z~P#BwO#k%e!ZP%e!ZR%e!Z[%e!Za%e!Zj%e!Zr%e!Z!S%e!Z!]%e!Z!l%e!Z!p%e!Z#R%e!Z#n%e!Z#o%e!Z#p%e!Z#q%e!Z#r%e!Z#s%e!Z#t%e!Z#u%e!Z#v%e!Z#x%e!Z#z%e!Z#{%e!Z'z%e!Z(a%e!Z(r%e!Z!k%e!Z!Y%e!Z'w%e!Z#`%e!Zv%e!Z!_%e!Z%i%e!Z!g%e!Z~P#/sOr;tO!g#vO(r'pO~O!Y;uO~P#/sOr;|O!g#vO(r'pO~O!Y;}O~P#/sOP%e!ZR%e!Z[%e!Zj%e!Zr%e!Z!S%e!Z!l%e!Z!p%e!Z#R%e!Z#n%e!Z#o%e!Z#p%e!Z#q%e!Z#r%e!Z#s%e!Z#t%e!Z#u%e!Z#v%e!Z#x%e!Z#z%e!Z#{%e!Z$O%e!Z(a%e!Z(r%e!Z!]%e!Z!^%e!Z~P''VOr<QO!g#vO(r'pO~Ov(fX~P1qO!Q%rO~P!)[O(U!lO~P!)[O!YfX!]fX#`fX~P%2OOP]XR]X[]Xj]Xr]X!Q]X!S]X!]]X!]fX!l]X!p]X#R]X#S]X#`]X#`fX#kfX#n]X#o]X#p]X#q]X#r]X#s]X#t]X#u]X#v]X#x]X#z]X#{]X$Q]X(a]X(r]X(y]X(z]X~O!gfX!k]X!kfX(rfX~P'LTOP<UOQ<UOSfOd>ROe!iOpkOr<UOskOtkOzkO|<UO!O<UO!SWO!WkO!XkO!_XO!i<XO!lZO!o<UO!p<UO!q<UO!s<YO!u<]O!x!hO$W!kO$n>PO(T)]O(VTO(YUO(aVO(o[O~O!]<iO!^$qa~Oh%VOp%WOr%XOs$tOt$tOz%YO|%ZO!O<tO!S${O!_$|O!i>WO!l$xO#j<zO$W%`O$t<vO$v<xO$y%aO(T(vO(VTO(YUO(a$uO(y$}O(z%PO~Ol)dO~P(!yOr!eX(r!eX~P#!iOr(jX(r(jX~P##[O!^]X!^fX~P'LTO!YfX!Y$zX!]fX!]$zX#`fX~P!0SO#k<^O~O!g#vO#k<^O~O#`<nO~Oj<bO~O#`=OO!](wX!^(wX~O#`<nO!](uX!^(uX~O#k=PO~Og=RO~P!1WO#k=XO~O#k=YO~Og=RO(T&ZO~O!g#vO#k=ZO~O!g#vO#k=PO~O$O=[O~P#BwO#k=]O~O#k=^O~O#k=cO~O#k=dO~O#k=eO~O#k=fO~O$O=gO~P!1WO$O=hO~P!1WOl=sO~P7eOk#S#T#U#W#X#[#i#j#u$n$t$v$y%]%^%h%i%j%q%s%v%w%y%{~(OT#o!X'|(U#ps#n#qr!Q'}$]'}(T$_(e~",
	goto: "$9Y)]PPPPPP)^PP)aP)rP+W/]PPPP6mPP7TPP=QPPP@tPA^PA^PPPA^PCfPA^PA^PA^PCjPCoPD^PIWPPPI[PPPPI[L_PPPLeMVPI[PI[PP! eI[PPPI[PI[P!#lI[P!'S!(X!(bP!)U!)Y!)U!,gPPPPPPP!-W!(XPP!-h!/YP!2iI[I[!2n!5z!:h!:h!>gPPP!>oI[PPPPPPPPP!BOP!C]PPI[!DnPI[PI[I[I[I[I[PI[!FQP!I[P!LbP!Lf!Lp!Lt!LtP!IXP!Lx!LxP#!OP#!SI[PI[#!Y#%_CjA^PA^PA^A^P#&lA^A^#)OA^#+vA^#.SA^A^#.r#1W#1W#1]#1f#1W#1qPP#1WPA^#2ZA^#6YA^A^6mPPP#:_PPP#:x#:xP#:xP#;`#:xPP#;fP#;]P#;]#;y#;]#<e#<k#<n)aP#<q)aP#<z#<z#<zP)aP)aP)aP)aPP)aP#=Q#=TP#=T)aP#=XP#=[P)aP)aP)aP)aP)aP)a)aPP#=b#=h#=s#=y#>P#>V#>]#>k#>q#>{#?R#?]#?c#?s#?y#@k#@}#AT#AZ#Ai#BO#Cs#DR#DY#Et#FS#Gt#HS#HY#H`#Hf#Hp#Hv#H|#IW#Ij#IpPPPPPPPPPPP#IvPPPPPPP#Jk#Mx$ b$ i$ qPPP$']P$'f$*_$0x$0{$1O$1}$2Q$2X$2aP$2g$2jP$3W$3[$4S$5b$5g$5}PP$6S$6Y$6^$6a$6e$6i$7e$7|$8e$8i$8l$8o$8y$8|$9Q$9UR!|RoqOXst!Z#d%m&r&t&u&w,s,x2[2_Y!vQ'`-e1o5{Q%tvQ%|yQ&T|Q&j!VS'W!e-]Q'f!iS'l!r!yU*k$|*Z*oQ+o%}S+|&V&WQ,d&dQ-c'_Q-m'gQ-u'mQ0[*qQ1b,OQ1y,eR<{<Y%SdOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$_$a$e%m%t&R&k&n&r&t&u&w&{'T'b'r(T(V(](d(x(z)O)}*i+X+],p,s,x-i-q.P.V.t.{/n0]0l0r1S1r2S2T2V2X2[2_2a3Q3W3l4z6T6e6f6i6|8t9T9_S#q]<V!r)_$Z$n'X)s-U-X/V2p4T5w6s:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SU+P%]<s<tQ+t&PQ,f&gQ,m&oQ0x+gQ0}+iQ1Y+uQ2R,kQ3`.gQ5`0|Q5f1TQ6[1zQ7Y3dQ8`5gR9e7['QkOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$Z$_$a$e$n%m%t&R&k&n&o&r&t&u&w&{'T'X'b'r(T(V(](d(x(z)O)s)}*i+X+]+g,p,s,x-U-X-i-q.P.V.g.t.{/V/n0]0l0r1S1r2S2T2V2X2[2_2a2p3Q3W3d3l4T4z5w6T6e6f6i6s6|7[8t9T9_:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>S!S!nQ!r!v!y!z$|'W'_'`'l'm'n*k*o*q*r-]-c-e-u0[0_1o5{5}%[$ti#v$b$c$d$x${%O%Q%^%_%c)y*R*T*V*Y*a*g*w*x+f+i,S,V.f/P/d/m/x/y/{0`0b0i0j0o1f1i1q3c4^4_4j4o5Q5[5_6S7W7v8Q8V8[8q9b9p9y:P:`:r;Q;[;d;k<l<m<o<p<q<r<u<v<w<x<y<z=S=T=U=V=X=Y=]=^=_=`=a=b=c=d=g=h>P>X>Y>]>^Q&X|Q'U!eS'[%i-`Q+t&PQ,P&WQ,f&gQ0n+SQ1Y+uQ1_+{Q2Q,jQ2R,kQ5f1TQ5o1aQ6[1zQ6_1|Q6`2PQ8`5gQ8c5lQ8|6bQ:X8dQ:f8yQ;V:YR<}*ZrnOXst!V!Z#d%m&i&r&t&u&w,s,x2[2_R,h&k&z^OPXYstuvwz!Z!`!g!j!o#S#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$Z$_$a$e$n%m%t&R&k&n&o&r&t&u&w&{'T'b'r(V(](d(x(z)O)s)}*i+X+]+g,p,s,x-U-X-i-q.P.V.g.t.{/V/n0]0l0r1S1r2S2T2V2X2[2_2a2p3Q3W3d3l4T4z5w6T6e6f6i6s6|7[8t9T9_:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>R>S[#]WZ#W#Z'X(T!b%jm#h#i#l$x%e%h(^(h(i(j*Y*^*b+Z+[+^,o-V.T.Z.[.]._/m/p2d3[3]4a6r7TQ%wxQ%{yW&Q|&V&W,OQ&_!TQ'c!hQ'e!iQ(q#sS+n%|%}Q+r&PQ,_&bQ,c&dS-l'f'gQ.i(rQ1R+oQ1X+uQ1Z+vQ1^+zQ1t,`S1x,d,eQ2|-mQ5e1TQ5i1WQ5n1`Q6Z1yQ8_5gQ8b5kQ8f5pQ:T8^R;T:U!U$zi$d%O%Q%^%_%c*R*T*a*w*x/P/x0`0b0i0j0o4_5Q8V9p>P>X>Y!^%yy!i!u%{%|%}'V'e'f'g'k'u*j+n+o-Y-l-m-t0R0U1R2u2|3T4r4s4v7}9{Q+h%wQ,T&[Q,W&]Q,b&dQ.h(qQ1s,_U1w,c,d,eQ3e.iQ6U1tS6Y1x1yQ8x6Z#f>T#v$b$c$x${)y*V*Y*g+f+i,S,V.f/d/m/y/{1f1i1q3c4^4j4o5[5_6S7W7v8Q8[8q9b9y:P:`:r;Q;[;d;k<o<q<u<w<y=S=U=X=]=_=a=c=g>]>^o>U<l<m<p<r<v<x<z=T=V=Y=^=`=b=d=hW%Ti%V*y>PS&[!Q&iQ&]!RQ&^!SU*}%[%d=sR,R&Y%]%Si#v$b$c$d$x${%O%Q%^%_%c)y*R*T*V*Y*a*g*w*x+f+i,S,V.f/P/d/m/x/y/{0`0b0i0j0o1f1i1q3c4^4_4j4o5Q5[5_6S7W7v8Q8V8[8q9b9p9y:P:`:r;Q;[;d;k<l<m<o<p<q<r<u<v<w<x<y<z=S=T=U=V=X=Y=]=^=_=`=a=b=c=d=g=h>P>X>Y>]>^T)z$u){V+P%]<s<tW'[!e%i*Z-`S(}#y#zQ+c%rQ+y&SS.b(m(nQ1j,XQ5T0kR8i5u'QkOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$Z$_$a$e$n%m%t&R&k&n&o&r&t&u&w&{'T'X'b'r(T(V(](d(x(z)O)s)}*i+X+]+g,p,s,x-U-X-i-q.P.V.g.t.{/V/n0]0l0r1S1r2S2T2V2X2[2_2a2p3Q3W3d3l4T4z5w6T6e6f6i6s6|7[8t9T9_:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>S$i$^c#Y#e%q%s%u(S(Y(t(y)R)S)T)U)V)W)X)Y)Z)[)^)`)b)g)q+d+x-Z-x-}.S.U.s.v.z.|.}/O/b0p2k2n3O3V3k3p3q3r3s3t3u3v3w3x3y3z3{3|4P4Q4X5X5c6u6{7Q7a7b7k7l8k9X9]9g9m9n:o;W;`<W=vT#TV#U'RkOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$Z$_$a$e$n%m%t&R&k&n&o&r&t&u&w&{'T'X'b'r(T(V(](d(x(z)O)s)}*i+X+]+g,p,s,x-U-X-i-q.P.V.g.t.{/V/n0]0l0r1S1r2S2T2V2X2[2_2a2p3Q3W3d3l4T4z5w6T6e6f6i6s6|7[8t9T9_:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SQ'Y!eR2q-]!W!nQ!e!r!v!y!z$|'W'_'`'l'm'n*Z*k*o*q*r-]-c-e-u0[0_1o5{5}R1l,ZnqOXst!Z#d%m&r&t&u&w,s,x2[2_Q&y!^Q'v!xS(s#u<^Q+l%zQ,]&_Q,^&aQ-j'dQ-w'oS.r(x=PS0q+X=ZQ1P+mQ1n,[Q2c,zQ2e,{Q2m-WQ2z-kQ2}-oS5Y0r=eQ5a1QS5d1S=fQ6t2oQ6x2{Q6}3SQ8]5bQ9Y6vQ9Z6yQ9^7OR:l9V$d$]c#Y#e%s%u(S(Y(t(y)R)S)T)U)V)W)X)Y)Z)[)^)`)b)g)q+d+x-Z-x-}.S.U.s.v.z.}/O/b0p2k2n3O3V3k3p3q3r3s3t3u3v3w3x3y3z3{3|4P4Q4X5X5c6u6{7Q7a7b7k7l8k9X9]9g9m9n:o;W;`<W=vS(o#p'iQ)P#zS+b%q.|S.c(n(pR3^.d'QkOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$Z$_$a$e$n%m%t&R&k&n&o&r&t&u&w&{'T'X'b'r(T(V(](d(x(z)O)s)}*i+X+]+g,p,s,x-U-X-i-q.P.V.g.t.{/V/n0]0l0r1S1r2S2T2V2X2[2_2a2p3Q3W3d3l4T4z5w6T6e6f6i6s6|7[8t9T9_:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SS#q]<VQ&t!XQ&u!YQ&w![Q&x!]R2Z,vQ'a!hQ+e%wQ-h'cS.e(q+hQ2x-gW3b.h.i0w0yQ6w2yW7U3_3a3e5^U9a7V7X7ZU:q9c9d9fS;b:p:sQ;p;cR;x;qU!wQ'`-eT5y1o5{!Q_OXZ`st!V!Z#d#h%e%m&i&k&r&t&u&w(j,s,x.[2[2_]!pQ!r'`-e1o5{T#q]<V%^{OPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$_$a$e%m%t&R&k&n&o&r&t&u&w&{'T'b'r(T(V(](d(x(z)O)}*i+X+]+g,p,s,x-i-q.P.V.g.t.{/n0]0l0r1S1r2S2T2V2X2[2_2a3Q3W3d3l4z6T6e6f6i6|7[8t9T9_S(}#y#zS.b(m(n!s=l$Z$n'X)s-U-X/V2p4T5w6s:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SU$fd)_,mS(p#p'iU*v%R(w4OU0m+O.n7gQ5^0xQ7V3`Q9d7YR:s9em!tQ!r!v!y!z'`'l'm'n-e-u1o5{5}Q't!uS(f#g2US-s'k'wQ/s*]Q0R*jQ3U-vQ4f/tQ4r0TQ4s0UQ4x0^Q7r4`S7}4t4vS8R4y4{Q9r7sQ9v7yQ9{8OQ:Q8TS:{9w9xS;g:|;PS;s;h;iS;{;t;uS<P;|;}R<S<QQ#wbQ's!uS(e#g2US(g#m+WQ+Y%fQ+j%xQ+p&OU-r'k't'wQ.W(fU/r*]*`/wQ0S*jQ0V*lQ1O+kQ1u,aS3R-s-vQ3Z.`S4e/s/tQ4n0PS4q0R0^Q4u0WQ6W1vQ7P3US7q4`4bQ7u4fU7|4r4x4{Q8P4wQ8v6XS9q7r7sQ9u7yQ9}8RQ:O8SQ:c8wQ:y9rS:z9v9xQ;S:QQ;^:dS;f:{;PS;r;g;hS;z;s;uS<O;{;}Q<R<PQ<T<SQ=o=jQ={=tR=|=uV!wQ'`-e%^aOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$_$a$e%m%t&R&k&n&o&r&t&u&w&{'T'b'r(T(V(](d(x(z)O)}*i+X+]+g,p,s,x-i-q.P.V.g.t.{/n0]0l0r1S1r2S2T2V2X2[2_2a3Q3W3d3l4z6T6e6f6i6|7[8t9T9_S#wz!j!r=i$Z$n'X)s-U-X/V2p4T5w6s:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SR=o>R%^bOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$_$a$e%m%t&R&k&n&o&r&t&u&w&{'T'b'r(T(V(](d(x(z)O)}*i+X+]+g,p,s,x-i-q.P.V.g.t.{/n0]0l0r1S1r2S2T2V2X2[2_2a3Q3W3d3l4z6T6e6f6i6|7[8t9T9_Q%fj!^%xy!i!u%{%|%}'V'e'f'g'k'u*j+n+o-Y-l-m-t0R0U1R2u2|3T4r4s4v7}9{S&Oz!jQ+k%yQ,a&dW1v,b,c,d,eU6X1w1x1yS8w6Y6ZQ:d8x!r=j$Z$n'X)s-U-X/V2p4T5w6s:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SQ=t>QR=u>R%QeOPXYstuvw!Z!`!g!o#S#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$_$a$e%m%t&R&k&n&r&t&u&w&{'T'b'r(V(](d(x(z)O)}*i+X+]+g,p,s,x-i-q.P.V.g.t.{/n0]0l0r1S1r2S2T2V2X2[2_2a3Q3W3d3l4z6T6e6f6i6|7[8t9T9_Y#bWZ#W#Z(T!b%jm#h#i#l$x%e%h(^(h(i(j*Y*^*b+Z+[+^,o-V.T.Z.[.]._/m/p2d3[3]4a6r7TQ,n&o!p=k$Z$n)s-U-X/V2p4T5w6s:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SR=n'XU']!e%i*ZR2s-`%SdOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$_$a$e%m%t&R&k&n&r&t&u&w&{'T'b'r(T(V(](d(x(z)O)}*i+X+],p,s,x-i-q.P.V.t.{/n0]0l0r1S1r2S2T2V2X2[2_2a3Q3W3l4z6T6e6f6i6|8t9T9_!r)_$Z$n'X)s-U-X/V2p4T5w6s:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SQ,m&oQ0x+gQ3`.gQ7Y3dR9e7[!b$Tc#Y%q(S(Y(t(y)Z)[)`)g+x-x-}.S.U.s.v/b0p3O3V3k3{5X5c6{7Q7a9]:o<W!P<d)^)q-Z.|2k2n3p3y3z4P4X6u7b7k7l8k9X9g9m9n;W;`=v!f$Vc#Y%q(S(Y(t(y)W)X)Z)[)`)g+x-x-}.S.U.s.v/b0p3O3V3k3{5X5c6{7Q7a9]:o<W!T<f)^)q-Z.|2k2n3p3v3w3y3z4P4X6u7b7k7l8k9X9g9m9n;W;`=v!^$Zc#Y%q(S(Y(t(y)`)g+x-x-}.S.U.s.v/b0p3O3V3k3{5X5c6{7Q7a9]:o<WQ4_/kz>S)^)q-Z.|2k2n3p4P4X6u7b7k7l8k9X9g9m9n;W;`=vQ>X>ZR>Y>['QkOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$Z$_$a$e$n%m%t&R&k&n&o&r&t&u&w&{'T'X'b'r(T(V(](d(x(z)O)s)}*i+X+]+g,p,s,x-U-X-i-q.P.V.g.t.{/V/n0]0l0r1S1r2S2T2V2X2[2_2a2p3Q3W3d3l4T4z5w6T6e6f6i6s6|7[8t9T9_:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>SS$oh$pR4U/U'XgOPWXYZhstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$Z$_$a$e$n$p%m%t&R&k&n&o&r&t&u&w&{'T'X'b'r(T(V(](d(x(z)O)s)}*i+X+]+g,p,s,x-U-X-i-q.P.V.g.t.{/U/V/n0]0l0r1S1r2S2T2V2X2[2_2a2p3Q3W3d3l4T4z5w6T6e6f6i6s6|7[8t9T9_:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>ST$kf$qQ$ifS)j$l)nR)v$qT$jf$qT)l$l)n'XhOPWXYZhstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$Z$_$a$e$n$p%m%t&R&k&n&o&r&t&u&w&{'T'X'b'r(T(V(](d(x(z)O)s)}*i+X+]+g,p,s,x-U-X-i-q.P.V.g.t.{/U/V/n0]0l0r1S1r2S2T2V2X2[2_2a2p3Q3W3d3l4T4z5w6T6e6f6i6s6|7[8t9T9_:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>ST$oh$pQ$rhR)u$p%^jOPWXYZstuvw!Z!`!g!o#S#W#Z#d#o#u#x#{$O$P$Q$R$S$T$U$V$W$X$_$a$e%m%t&R&k&n&o&r&t&u&w&{'T'b'r(T(V(](d(x(z)O)}*i+X+]+g,p,s,x-i-q.P.V.g.t.{/n0]0l0r1S1r2S2T2V2X2[2_2a3Q3W3d3l4z6T6e6f6i6|7[8t9T9_!s>Q$Z$n'X)s-U-X/V2p4T5w6s:Z:m<U<X<Y<]<^<_<`<a<b<c<d<e<f<g<h<i<k<n<{=O=P=R=Z=[=e=f>S#glOPXZst!Z!`!o#S#d#o#{$n%m&k&n&o&r&t&u&w&{'T'b)O)s*i+]+g,p,s,x-i.g/V/n0]0l1r2S2T2V2X2[2_2a3d4T4z6T6e6f6i7[8t9T!U%Ri$d%O%Q%^%_%c*R*T*a*w*x/P/x0`0b0i0j0o4_5Q8V9p>P>X>Y#f(w#v$b$c$x${)y*V*Y*g+f+i,S,V.f/d/m/y/{1f1i1q3c4^4j4o5[5_6S7W7v8Q8[8q9b9y:P:`:r;Q;[;d;k<o<q<u<w<y=S=U=X=]=_=a=c=g>]>^Q+T%aQ/c*Oo4O<l<m<p<r<v<x<z=T=V=Y=^=`=b=d=h!U$yi$d%O%Q%^%_%c*R*T*a*w*x/P/x0`0b0i0j0o4_5Q8V9p>P>X>YQ*c$zU*l$|*Z*oQ+U%bQ0W*m#f=q#v$b$c$x${)y*V*Y*g+f+i,S,V.f/d/m/y/{1f1i1q3c4^4j4o5[5_6S7W7v8Q8[8q9b9y:P:`:r;Q;[;d;k<o<q<u<w<y=S=U=X=]=_=a=c=g>]>^n=r<l<m<p<r<v<x<z=T=V=Y=^=`=b=d=hQ=w>TQ=x>UQ=y>VR=z>W!U%Ri$d%O%Q%^%_%c*R*T*a*w*x/P/x0`0b0i0j0o4_5Q8V9p>P>X>Y#f(w#v$b$c$x${)y*V*Y*g+f+i,S,V.f/d/m/y/{1f1i1q3c4^4j4o5[5_6S7W7v8Q8[8q9b9y:P:`:r;Q;[;d;k<o<q<u<w<y=S=U=X=]=_=a=c=g>]>^o4O<l<m<p<r<v<x<z=T=V=Y=^=`=b=d=hnoOXst!Z#d%m&r&t&u&w,s,x2[2_S*f${*YQ-R'OQ-S'QR4i/y%[%Si#v$b$c$d$x${%O%Q%^%_%c)y*R*T*V*Y*a*g*w*x+f+i,S,V.f/P/d/m/x/y/{0`0b0i0j0o1f1i1q3c4^4_4j4o5Q5[5_6S7W7v8Q8V8[8q9b9p9y:P:`:r;Q;[;d;k<l<m<o<p<q<r<u<v<w<x<y<z=S=T=U=V=X=Y=]=^=_=`=a=b=c=d=g=h>P>X>Y>]>^Q,U&]Q1h,WQ5s1gR8h5tV*n$|*Z*oU*n$|*Z*oT5z1o5{S0P*i/nQ4w0]T8S4z:]Q+j%xQ0V*lQ1O+kQ1u,aQ6W1vQ8v6XQ:c8wR;^:d!U%Oi$d%O%Q%^%_%c*R*T*a*w*x/P/x0`0b0i0j0o4_5Q8V9p>P>X>Yx*R$v)e*S*u+V/v0d0e4R4g5R5S5W7p8U:R:x=p=}>OS0`*t0a#f<o#v$b$c$x${)y*V*Y*g+f+i,S,V.f/d/m/y/{1f1i1q3c4^4j4o5[5_6S7W7v8Q8[8q9b9y:P:`:r;Q;[;d;k<o<q<u<w<y=S=U=X=]=_=a=c=g>]>^n<p<l<m<p<r<v<x<z=T=V=Y=^=`=b=d=h!d=S(u)c*[*e.j.m.q/_/k/|0v1e3h4[4h4l5r7]7`7w7z8X8Z9t9|:S:};R;e;j;v>Z>[`=T3}7c7f7j9h:t:w;yS=_.l3iT=`7e9k!U%Qi$d%O%Q%^%_%c*R*T*a*w*x/P/x0`0b0i0j0o4_5Q8V9p>P>X>Y|*T$v)e*U*t+V/g/v0d0e4R4g4|5R5S5W7p8U:R:x=p=}>OS0b*u0c#f<q#v$b$c$x${)y*V*Y*g+f+i,S,V.f/d/m/y/{1f1i1q3c4^4j4o5[5_6S7W7v8Q8[8q9b9y:P:`:r;Q;[;d;k<o<q<u<w<y=S=U=X=]=_=a=c=g>]>^n<r<l<m<p<r<v<x<z=T=V=Y=^=`=b=d=h!h=U(u)c*[*e.k.l.q/_/k/|0v1e3f3h4[4h4l5r7]7^7`7w7z8X8Z9t9|:S:};R;e;j;v>Z>[d=V3}7d7e7j9h9i:t:u:w;yS=a.m3jT=b7f9lrnOXst!V!Z#d%m&i&r&t&u&w,s,x2[2_Q&f!UR,p&ornOXst!V!Z#d%m&i&r&t&u&w,s,x2[2_R&f!UQ,Y&^R1d,RsnOXst!V!Z#d%m&i&r&t&u&w,s,x2[2_Q1p,_S6R1s1tU8p6P6Q6US:_8r8sS;Y:^:aQ;m;ZR;w;nQ&m!VR,i&iR6_1|R:f8yW&Q|&V&W,OR1Z+vQ&r!WR,s&sR,y&xT2],x2_R,}&yQ,|&yR2f,}Q'y!{R-y'ySsOtQ#dXT%ps#dQ#OTR'{#OQ#RUR'}#RQ){$uR/`){Q#UVR(Q#UQ#XWU(W#X(X.QQ(X#YR.Q(YQ-^'YR2r-^Q.u(yS3m.u3nR3n.vQ-e'`R2v-eY!rQ'`-e1o5{R'j!rQ/Q)eR4S/QU#_W%h*YU(_#_(`.RQ(`#`R.R(ZQ-a']R2t-at`OXst!V!Z#d%m&i&k&r&t&u&w,s,x2[2_S#hZ%eU#r`#h.[R.[(jQ(k#jQ.X(gW.a(k.X3X7RQ3X.YR7R3YQ)n$lR/W)nQ$phR)t$pQ$`cU)a$`-|<jQ-|<WR<j)qQ/q*]W4c/q4d7t9sU4d/r/s/tS7t4e4fR9s7u$e*Q$v(u)c)e*[*e*t*u+Q+R+V.l.m.o.p.q/_/g/i/k/v/|0d0e0v1e3f3g3h3}4R4[4g4h4l4|5O5R5S5W5r7]7^7_7`7e7f7h7i7j7p7w7z8U8X8Z9h9i9j9t9|:R:S:t:u:v:w:x:};R;e;j;v;y=p=}>O>Z>[Q/z*eU4k/z4m7xQ4m/|R7x4lS*o$|*ZR0Y*ox*S$v)e*t*u+V/v0d0e4R4g5R5S5W7p8U:R:x=p=}>O!d.j(u)c*[*e.l.m.q/_/k/|0v1e3h4[4h4l5r7]7`7w7z8X8Z9t9|:S:};R;e;j;v>Z>[U/h*S.j7ca7c3}7e7f7j9h:t:w;yQ0a*tQ3i.lU4}0a3i9kR9k7e|*U$v)e*t*u+V/g/v0d0e4R4g4|5R5S5W7p8U:R:x=p=}>O!h.k(u)c*[*e.l.m.q/_/k/|0v1e3f3h4[4h4l5r7]7^7`7w7z8X8Z9t9|:S:};R;e;j;v>Z>[U/j*U.k7de7d3}7e7f7j9h9i:t:u:w;yQ0c*uQ3j.mU5P0c3j9lR9l7fQ*z%UR0g*zQ5]0vR8Y5]Q+_%kR0u+_Q5v1jS8j5v:[R:[8kQ,[&_R1m,[Q5{1oR8m5{Q1{,fS6]1{8zR8z6_Q1U+rW5h1U5j8a:VQ5j1XQ8a5iR:V8bQ+w&QR1[+wQ2_,xR6m2_YrOXst#dQ&v!ZQ+a%mQ,r&rQ,t&tQ,u&uQ,w&wQ2Y,sS2],x2_R6l2[Q%opQ&z!_Q&}!aQ'P!bQ'R!cQ'q!uQ+`%lQ+l%zQ,Q&XQ,h&mQ-P&|W-p'k's't'wQ-w'oQ0X*nQ1P+mQ1c,PS2O,i,lQ2g-OQ2h-RQ2i-SQ2}-oW3P-r-s-v-xQ5a1QQ5m1_Q5q1eQ6V1uQ6a2QQ6k2ZU6z3O3R3UQ6}3SQ8]5bQ8e5oQ8g5rQ8l5zQ8u6WQ8{6`S9[6{7PQ9^7OQ:W8cQ:b8vQ:g8|Q:n9]Q;U:XQ;]:cQ;a:oQ;l;VR;o;^Q%zyQ'd!iQ'o!uU+m%{%|%}Q-W'VU-k'e'f'gS-o'k'uQ0Q*jS1Q+n+oQ2o-YS2{-l-mQ3S-tS4p0R0UQ5b1RQ6v2uQ6y2|Q7O3TU7{4r4s4vQ9z7}R;O9{S$wi>PR*{%VU%Ui%V>PR0f*yQ$viS(u#v+iS)c$b$cQ)e$dQ*[$xS*e${*YQ*t%OQ*u%QQ+Q%^Q+R%_Q+V%cQ.l<oQ.m<qQ.o<uQ.p<wQ.q<yQ/_)yQ/g*RQ/i*TQ/k*VQ/v*aS/|*g/mQ0d*wQ0e*xl0v+f,V.f1i1q3c6S7W8q9b:`:r;[;dQ1e,SQ3f=SQ3g=UQ3h=XS3}<l<mQ4R/PS4[/d4^Q4g/xQ4h/yQ4l/{Q4|0`Q5O0bQ5R0iQ5S0jQ5W0oQ5r1fQ7]=]Q7^=_Q7_=aQ7`=cQ7e<pQ7f<rQ7h<vQ7i<xQ7j<zQ7p4_Q7w4jQ7z4oQ8U5QQ8X5[Q8Z5_Q9h=YQ9i=TQ9j=VQ9t7vQ9|8QQ:R8VQ:S8[Q:t=^Q:u=`Q:v=bQ:w=dQ:x9pQ:}9yQ;R:PQ;e=gQ;j;QQ;v;kQ;y=hQ=p>PQ=}>XQ>O>YQ>Z>]R>[>^Q+O%]Q.n<sR7g<tnpOXst!Z#d%m&r&t&u&w,s,x2[2_Q!fPS#fZ#oQ&|!`W'h!o*i0]4zQ(P#SQ)Q#{Q)r$nS,l&k&nQ,q&oQ-O&{S-T'T/nQ-g'bQ.x)OQ/[)sQ0s+]Q0y+gQ2W,pQ2y-iQ3a.gQ4W/VQ5U0lQ6Q1rQ6c2SQ6d2TQ6h2VQ6j2XQ6o2aQ7Z3dQ7m4TQ8s6TQ9P6eQ9Q6fQ9S6iQ9f7[Q:a8tR:k9T#[cOPXZst!Z!`!o#d#o#{%m&k&n&o&r&t&u&w&{'T'b)O*i+]+g,p,s,x-i.g/n0]0l1r2S2T2V2X2[2_2a3d4z6T6e6f6i7[8t9TQ#YWQ#eYQ%quQ%svS%uw!gS(S#W(VQ(Y#ZQ(t#uQ(y#xQ)R$OQ)S$PQ)T$QQ)U$RQ)V$SQ)W$TQ)X$UQ)Y$VQ)Z$WQ)[$XQ)^$ZQ)`$_Q)b$aQ)g$eW)q$n)s/V4TQ+d%tQ+x&RS-Z'X2pQ-x'rS-}(T.PQ.S(]Q.U(dQ.s(xQ.v(zQ.z<UQ.|<XQ.}<YQ/O<]Q/b)}Q0p+XQ2k-UQ2n-XQ3O-qQ3V.VQ3k.tQ3p<^Q3q<_Q3r<`Q3s<aQ3t<bQ3u<cQ3v<dQ3w<eQ3x<fQ3y<gQ3z<hQ3{.{Q3|<kQ4P<nQ4Q<{Q4X<iQ5X0rQ5c1SQ6u=OQ6{3QQ7Q3WQ7a3lQ7b=PQ7k=RQ7l=ZQ8k5wQ9X6sQ9]6|Q9g=[Q9m=eQ9n=fQ:o9_Q;W:ZQ;`:mQ<W#SR=v>SR#[WR'Z!el!tQ!r!v!y!z'`'l'm'n-e-u1o5{5}S'V!e-]U*j$|*Z*oS-Y'W'_S0U*k*qQ0^*rQ2u-cQ4v0[R4{0_R({#xQ!fQT-d'`-e]!qQ!r'`-e1o5{Q#p]R'i<VR)f$dY!uQ'`-e1o5{Q'k!rS'u!v!yS'w!z5}S-t'l'mQ-v'nR3T-uT#kZ%eS#jZ%eS%km,oU(g#h#i#lS.Y(h(iQ.^(jQ0t+^Q3Y.ZU3Z.[.]._S7S3[3]R9`7Td#^W#W#Z%h(T(^*Y+Z.T/mr#gZm#h#i#l%e(h(i(j+^.Z.[.]._3[3]7TS*]$x*bQ/t*^Q2U,oQ2l-VQ4`/pQ6q2dQ7s4aQ9W6rT=m'X+[V#aW%h*YU#`W%h*YS(U#W(^U(Z#Z+Z/mS-['X+[T.O(T.TV'^!e%i*ZQ$lfR)x$qT)m$l)nR4V/UT*_$x*bT*h${*YQ0w+fQ1g,VQ3_.fQ5t1iQ6P1qQ7X3cQ8r6SQ9c7WQ:^8qQ:p9bQ;Z:`Q;c:rQ;n;[R;q;dnqOXst!Z#d%m&r&t&u&w,s,x2[2_Q&l!VR,h&itmOXst!U!V!Z#d%m&i&r&t&u&w,s,x2[2_R,o&oT%lm,oR1k,XR,g&gQ&U|S+}&V&WR1^,OR+s&PT&p!W&sT&q!W&sT2^,x2_",
	nodeNames: "⚠ ArithOp ArithOp ?. JSXStartTag LineComment BlockComment Script Hashbang ExportDeclaration export Star as VariableName String Escape from ; default FunctionDeclaration async function VariableDefinition > < TypeParamList in out const TypeDefinition extends ThisType this LiteralType ArithOp Number BooleanLiteral TemplateType InterpolationEnd Interpolation InterpolationStart NullType null VoidType void TypeofType typeof MemberExpression . PropertyName [ TemplateString Escape Interpolation super RegExp ] ArrayExpression Spread , } { ObjectExpression Property async get set PropertyDefinition Block : NewTarget new NewExpression ) ( ArgList UnaryExpression delete LogicOp BitOp YieldExpression yield AwaitExpression await ParenthesizedExpression ClassExpression class ClassBody MethodDeclaration Decorator @ MemberExpression PrivatePropertyName CallExpression TypeArgList CompareOp < declare Privacy static abstract override PrivatePropertyDefinition PropertyDeclaration readonly accessor Optional TypeAnnotation Equals StaticBlock FunctionExpression ArrowFunction ParamList ParamList ArrayPattern ObjectPattern PatternProperty Privacy readonly Arrow MemberExpression BinaryExpression ArithOp ArithOp ArithOp ArithOp BitOp CompareOp instanceof satisfies CompareOp BitOp BitOp BitOp LogicOp LogicOp ConditionalExpression LogicOp LogicOp AssignmentExpression UpdateOp PostfixExpression CallExpression InstantiationExpression TaggedTemplateExpression DynamicImport import ImportMeta JSXElement JSXSelfCloseEndTag JSXSelfClosingTag JSXIdentifier JSXBuiltin JSXIdentifier JSXNamespacedName JSXMemberExpression JSXSpreadAttribute JSXAttribute JSXAttributeValue JSXEscape JSXEndTag JSXOpenTag JSXFragmentTag JSXText JSXEscape JSXStartCloseTag JSXCloseTag PrefixCast < ArrowFunction TypeParamList SequenceExpression InstantiationExpression KeyofType keyof UniqueType unique ImportType InferredType infer TypeName ParenthesizedType FunctionSignature ParamList NewSignature IndexedType TupleType Label ArrayType ReadonlyType ObjectType MethodType PropertyType IndexSignature PropertyDefinition CallSignature TypePredicate asserts is NewSignature new UnionType LogicOp IntersectionType LogicOp ConditionalType ParameterizedType ClassDeclaration abstract implements type VariableDeclaration let var using TypeAliasDeclaration InterfaceDeclaration interface EnumDeclaration enum EnumBody NamespaceDeclaration namespace module AmbientDeclaration declare GlobalDeclaration global ClassDeclaration ClassBody AmbientFunctionDeclaration ExportGroup VariableName VariableName ImportDeclaration defer ImportGroup ForStatement for ForSpec ForInSpec ForOfSpec of WhileStatement while WithStatement with DoStatement do IfStatement if else SwitchStatement switch SwitchBody CaseLabel case DefaultLabel TryStatement try CatchClause catch FinallyClause finally ReturnStatement return ThrowStatement throw BreakStatement break ContinueStatement continue DebuggerStatement debugger LabeledStatement ExpressionStatement SingleExpression SingleClassItem",
	maxTerm: 380,
	context: bv,
	nodeProps: [
		[
			"isolate",
			-8,
			5,
			6,
			14,
			37,
			39,
			51,
			53,
			55,
			""
		],
		[
			"group",
			-26,
			9,
			17,
			19,
			68,
			207,
			211,
			215,
			216,
			218,
			221,
			224,
			234,
			237,
			243,
			245,
			247,
			249,
			252,
			258,
			264,
			266,
			268,
			270,
			272,
			274,
			275,
			"Statement",
			-34,
			13,
			14,
			32,
			35,
			36,
			42,
			51,
			54,
			55,
			57,
			62,
			70,
			72,
			76,
			80,
			82,
			84,
			85,
			110,
			111,
			120,
			121,
			136,
			139,
			141,
			142,
			143,
			144,
			145,
			147,
			148,
			167,
			169,
			171,
			"Expression",
			-23,
			31,
			33,
			37,
			41,
			43,
			45,
			173,
			175,
			177,
			178,
			180,
			181,
			182,
			184,
			185,
			186,
			188,
			189,
			190,
			201,
			203,
			205,
			206,
			"Type",
			-3,
			88,
			103,
			109,
			"ClassItem"
		],
		[
			"openedBy",
			23,
			"<",
			38,
			"InterpolationStart",
			56,
			"[",
			60,
			"{",
			73,
			"(",
			160,
			"JSXStartCloseTag"
		],
		[
			"closedBy",
			-2,
			24,
			168,
			">",
			40,
			"InterpolationEnd",
			50,
			"]",
			61,
			"}",
			74,
			")",
			165,
			"JSXEndTag"
		]
	],
	propSources: [Dv],
	skippedNodes: [
		0,
		5,
		6,
		278
	],
	repeatNodeCount: 37,
	tokenData: "$Fq07[R!bOX%ZXY+gYZ-yZ[+g[]%Z]^.c^p%Zpq+gqr/mrs3cst:_tuEruvJSvwLkwx! Yxy!'iyz!(sz{!)}{|!,q|}!.O}!O!,q!O!P!/Y!P!Q!9j!Q!R#:O!R![#<_![!]#I_!]!^#Jk!^!_#Ku!_!`$![!`!a$$v!a!b$*T!b!c$,r!c!}Er!}#O$-|#O#P$/W#P#Q$4o#Q#R$5y#R#SEr#S#T$7W#T#o$8b#o#p$<r#p#q$=h#q#r$>x#r#s$@U#s$f%Z$f$g+g$g#BYEr#BY#BZ$A`#BZ$ISEr$IS$I_$A`$I_$I|Er$I|$I}$Dk$I}$JO$Dk$JO$JTEr$JT$JU$A`$JU$KVEr$KV$KW$A`$KW&FUEr&FU&FV$A`&FV;'SEr;'S;=`I|<%l?HTEr?HT?HU$A`?HUOEr(n%d_$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z&j&hT$i&jO!^&c!_#o&c#p;'S&c;'S;=`&w<%lO&c&j&zP;=`<%l&c'|'U]$i&j(Z!bOY&}YZ&cZw&}wx&cx!^&}!^!_'}!_#O&}#O#P&c#P#o&}#o#p'}#p;'S&};'S;=`(l<%lO&}!b(SU(Z!bOY'}Zw'}x#O'}#P;'S'};'S;=`(f<%lO'}!b(iP;=`<%l'}'|(oP;=`<%l&}'[(y]$i&j(WpOY(rYZ&cZr(rrs&cs!^(r!^!_)r!_#O(r#O#P&c#P#o(r#o#p)r#p;'S(r;'S;=`*a<%lO(rp)wU(WpOY)rZr)rs#O)r#P;'S)r;'S;=`*Z<%lO)rp*^P;=`<%l)r'[*dP;=`<%l(r#S*nX(Wp(Z!bOY*gZr*grs'}sw*gwx)rx#O*g#P;'S*g;'S;=`+Z<%lO*g#S+^P;=`<%l*g(n+dP;=`<%l%Z07[+rq$i&j(Wp(Z!b'|0/lOX%ZXY+gYZ&cZ[+g[p%Zpq+gqr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p$f%Z$f$g+g$g#BY%Z#BY#BZ+g#BZ$IS%Z$IS$I_+g$I_$JT%Z$JT$JU+g$JU$KV%Z$KV$KW+g$KW&FU%Z&FU&FV+g&FV;'S%Z;'S;=`+a<%l?HT%Z?HT?HU+g?HUO%Z07[.ST(X#S$i&j'}0/lO!^&c!_#o&c#p;'S&c;'S;=`&w<%lO&c07[.n_$i&j(Wp(Z!b'}0/lOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z)3p/x`$i&j!p),Q(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`0z!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KW1V`#v(Ch$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`2X!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KW2d_#v(Ch$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'At3l_(V':f$i&j(Z!bOY4kYZ5qZr4krs7nsw4kwx5qx!^4k!^!_8p!_#O4k#O#P5q#P#o4k#o#p8p#p;'S4k;'S;=`:X<%lO4k(^4r_$i&j(Z!bOY4kYZ5qZr4krs7nsw4kwx5qx!^4k!^!_8p!_#O4k#O#P5q#P#o4k#o#p8p#p;'S4k;'S;=`:X<%lO4k&z5vX$i&jOr5qrs6cs!^5q!^!_6y!_#o5q#o#p6y#p;'S5q;'S;=`7h<%lO5q&z6jT$d`$i&jO!^&c!_#o&c#p;'S&c;'S;=`&w<%lO&c`6|TOr6yrs7]s;'S6y;'S;=`7b<%lO6y`7bO$d``7eP;=`<%l6y&z7kP;=`<%l5q(^7w]$d`$i&j(Z!bOY&}YZ&cZw&}wx&cx!^&}!^!_'}!_#O&}#O#P&c#P#o&}#o#p'}#p;'S&};'S;=`(l<%lO&}!r8uZ(Z!bOY8pYZ6yZr8prs9hsw8pwx6yx#O8p#O#P6y#P;'S8p;'S;=`:R<%lO8p!r9oU$d`(Z!bOY'}Zw'}x#O'}#P;'S'};'S;=`(f<%lO'}!r:UP;=`<%l8p(^:[P;=`<%l4k%9[:hh$i&j(Wp(Z!bOY%ZYZ&cZq%Zqr<Srs&}st%ZtuCruw%Zwx(rx!^%Z!^!_*g!_!c%Z!c!}Cr!}#O%Z#O#P&c#P#R%Z#R#SCr#S#T%Z#T#oCr#o#p*g#p$g%Z$g;'SCr;'S;=`El<%lOCr(r<__WS$i&j(Wp(Z!bOY<SYZ&cZr<Srs=^sw<Swx@nx!^<S!^!_Bm!_#O<S#O#P>`#P#o<S#o#pBm#p;'S<S;'S;=`Cl<%lO<S(Q=g]WS$i&j(Z!bOY=^YZ&cZw=^wx>`x!^=^!^!_?q!_#O=^#O#P>`#P#o=^#o#p?q#p;'S=^;'S;=`@h<%lO=^&n>gXWS$i&jOY>`YZ&cZ!^>`!^!_?S!_#o>`#o#p?S#p;'S>`;'S;=`?k<%lO>`S?XSWSOY?SZ;'S?S;'S;=`?e<%lO?SS?hP;=`<%l?S&n?nP;=`<%l>`!f?xWWS(Z!bOY?qZw?qwx?Sx#O?q#O#P?S#P;'S?q;'S;=`@b<%lO?q!f@eP;=`<%l?q(Q@kP;=`<%l=^'`@w]WS$i&j(WpOY@nYZ&cZr@nrs>`s!^@n!^!_Ap!_#O@n#O#P>`#P#o@n#o#pAp#p;'S@n;'S;=`Bg<%lO@ntAwWWS(WpOYApZrAprs?Ss#OAp#O#P?S#P;'SAp;'S;=`Ba<%lOAptBdP;=`<%lAp'`BjP;=`<%l@n#WBvYWS(Wp(Z!bOYBmZrBmrs?qswBmwxApx#OBm#O#P?S#P;'SBm;'S;=`Cf<%lOBm#WCiP;=`<%lBm(rCoP;=`<%l<S%9[C}i$i&j(o%1l(Wp(Z!bOY%ZYZ&cZr%Zrs&}st%ZtuCruw%Zwx(rx!Q%Z!Q![Cr![!^%Z!^!_*g!_!c%Z!c!}Cr!}#O%Z#O#P&c#P#R%Z#R#SCr#S#T%Z#T#oCr#o#p*g#p$g%Z$g;'SCr;'S;=`El<%lOCr%9[EoP;=`<%lCr07[FRk$i&j(Wp(Z!b$]#t(T,2j(e$I[OY%ZYZ&cZr%Zrs&}st%ZtuEruw%Zwx(rx}%Z}!OGv!O!Q%Z!Q![Er![!^%Z!^!_*g!_!c%Z!c!}Er!}#O%Z#O#P&c#P#R%Z#R#SEr#S#T%Z#T#oEr#o#p*g#p$g%Z$g;'SEr;'S;=`I|<%lOEr+dHRk$i&j(Wp(Z!b$]#tOY%ZYZ&cZr%Zrs&}st%ZtuGvuw%Zwx(rx}%Z}!OGv!O!Q%Z!Q![Gv![!^%Z!^!_*g!_!c%Z!c!}Gv!}#O%Z#O#P&c#P#R%Z#R#SGv#S#T%Z#T#oGv#o#p*g#p$g%Z$g;'SGv;'S;=`Iv<%lOGv+dIyP;=`<%lGv07[JPP;=`<%lEr(KWJ_`$i&j(Wp(Z!b#p(ChOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KWKl_$i&j$Q(Ch(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z,#xLva(z+JY$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sv%ZvwM{wx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KWNW`$i&j#z(Ch(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'At! c_(Y';W$i&j(WpOY!!bYZ!#hZr!!brs!#hsw!!bwx!$xx!^!!b!^!_!%z!_#O!!b#O#P!#h#P#o!!b#o#p!%z#p;'S!!b;'S;=`!'c<%lO!!b'l!!i_$i&j(WpOY!!bYZ!#hZr!!brs!#hsw!!bwx!$xx!^!!b!^!_!%z!_#O!!b#O#P!#h#P#o!!b#o#p!%z#p;'S!!b;'S;=`!'c<%lO!!b&z!#mX$i&jOw!#hwx6cx!^!#h!^!_!$Y!_#o!#h#o#p!$Y#p;'S!#h;'S;=`!$r<%lO!#h`!$]TOw!$Ywx7]x;'S!$Y;'S;=`!$l<%lO!$Y`!$oP;=`<%l!$Y&z!$uP;=`<%l!#h'l!%R]$d`$i&j(WpOY(rYZ&cZr(rrs&cs!^(r!^!_)r!_#O(r#O#P&c#P#o(r#o#p)r#p;'S(r;'S;=`*a<%lO(r!Q!&PZ(WpOY!%zYZ!$YZr!%zrs!$Ysw!%zwx!&rx#O!%z#O#P!$Y#P;'S!%z;'S;=`!']<%lO!%z!Q!&yU$d`(WpOY)rZr)rs#O)r#P;'S)r;'S;=`*Z<%lO)r!Q!'`P;=`<%l!%z'l!'fP;=`<%l!!b/5|!'t_!l/.^$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z#&U!)O_!k!Lf$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z-!n!*[b$i&j(Wp(Z!b(U%&f#q(ChOY%ZYZ&cZr%Zrs&}sw%Zwx(rxz%Zz{!+d{!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KW!+o`$i&j(Wp(Z!b#n(ChOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z+;x!,|`$i&j(Wp(Z!br+4YOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z,$U!.Z_!]+Jf$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z07[!/ec$i&j(Wp(Z!b!Q.2^OY%ZYZ&cZr%Zrs&}sw%Zwx(rx!O%Z!O!P!0p!P!Q%Z!Q![!3Y![!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z#%|!0ya$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!O%Z!O!P!2O!P!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z#%|!2Z_![!L^$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad!3eg$i&j(Wp(Z!bs'9tOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q![!3Y![!^%Z!^!_*g!_!g%Z!g!h!4|!h#O%Z#O#P&c#P#R%Z#R#S!3Y#S#X%Z#X#Y!4|#Y#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad!5Vg$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx{%Z{|!6n|}%Z}!O!6n!O!Q%Z!Q![!8S![!^%Z!^!_*g!_#O%Z#O#P&c#P#R%Z#R#S!8S#S#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad!6wc$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q![!8S![!^%Z!^!_*g!_#O%Z#O#P&c#P#R%Z#R#S!8S#S#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad!8_c$i&j(Wp(Z!bs'9tOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q![!8S![!^%Z!^!_*g!_#O%Z#O#P&c#P#R%Z#R#S!8S#S#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z07[!9uf$i&j(Wp(Z!b#o(ChOY!;ZYZ&cZr!;Zrs!<nsw!;Zwx!Lcxz!;Zz{#-}{!P!;Z!P!Q#/d!Q!^!;Z!^!_#(i!_!`#7S!`!a#8i!a!}!;Z!}#O#,f#O#P!Dy#P#o!;Z#o#p#(i#p;'S!;Z;'S;=`#-w<%lO!;Z?O!;fb$i&j(Wp(Z!b!X7`OY!;ZYZ&cZr!;Zrs!<nsw!;Zwx!Lcx!P!;Z!P!Q#&`!Q!^!;Z!^!_#(i!_!}!;Z!}#O#,f#O#P!Dy#P#o!;Z#o#p#(i#p;'S!;Z;'S;=`#-w<%lO!;Z>^!<w`$i&j(Z!b!X7`OY!<nYZ&cZw!<nwx!=yx!P!<n!P!Q!Eq!Q!^!<n!^!_!Gr!_!}!<n!}#O!KS#O#P!Dy#P#o!<n#o#p!Gr#p;'S!<n;'S;=`!L]<%lO!<n<z!>Q^$i&j!X7`OY!=yYZ&cZ!P!=y!P!Q!>|!Q!^!=y!^!_!@c!_!}!=y!}#O!CW#O#P!Dy#P#o!=y#o#p!@c#p;'S!=y;'S;=`!Ek<%lO!=y<z!?Td$i&j!X7`O!^&c!_#W&c#W#X!>|#X#Z&c#Z#[!>|#[#]&c#]#^!>|#^#a&c#a#b!>|#b#g&c#g#h!>|#h#i&c#i#j!>|#j#k!>|#k#m&c#m#n!>|#n#o&c#p;'S&c;'S;=`&w<%lO&c7`!@hX!X7`OY!@cZ!P!@c!P!Q!AT!Q!}!@c!}#O!Ar#O#P!Bq#P;'S!@c;'S;=`!CQ<%lO!@c7`!AYW!X7`#W#X!AT#Z#[!AT#]#^!AT#a#b!AT#g#h!AT#i#j!AT#j#k!AT#m#n!AT7`!AuVOY!ArZ#O!Ar#O#P!B[#P#Q!@c#Q;'S!Ar;'S;=`!Bk<%lO!Ar7`!B_SOY!ArZ;'S!Ar;'S;=`!Bk<%lO!Ar7`!BnP;=`<%l!Ar7`!BtSOY!@cZ;'S!@c;'S;=`!CQ<%lO!@c7`!CTP;=`<%l!@c<z!C][$i&jOY!CWYZ&cZ!^!CW!^!_!Ar!_#O!CW#O#P!DR#P#Q!=y#Q#o!CW#o#p!Ar#p;'S!CW;'S;=`!Ds<%lO!CW<z!DWX$i&jOY!CWYZ&cZ!^!CW!^!_!Ar!_#o!CW#o#p!Ar#p;'S!CW;'S;=`!Ds<%lO!CW<z!DvP;=`<%l!CW<z!EOX$i&jOY!=yYZ&cZ!^!=y!^!_!@c!_#o!=y#o#p!@c#p;'S!=y;'S;=`!Ek<%lO!=y<z!EnP;=`<%l!=y>^!Ezl$i&j(Z!b!X7`OY&}YZ&cZw&}wx&cx!^&}!^!_'}!_#O&}#O#P&c#P#W&}#W#X!Eq#X#Z&}#Z#[!Eq#[#]&}#]#^!Eq#^#a&}#a#b!Eq#b#g&}#g#h!Eq#h#i&}#i#j!Eq#j#k!Eq#k#m&}#m#n!Eq#n#o&}#o#p'}#p;'S&};'S;=`(l<%lO&}8r!GyZ(Z!b!X7`OY!GrZw!Grwx!@cx!P!Gr!P!Q!Hl!Q!}!Gr!}#O!JU#O#P!Bq#P;'S!Gr;'S;=`!J|<%lO!Gr8r!Hse(Z!b!X7`OY'}Zw'}x#O'}#P#W'}#W#X!Hl#X#Z'}#Z#[!Hl#[#]'}#]#^!Hl#^#a'}#a#b!Hl#b#g'}#g#h!Hl#h#i'}#i#j!Hl#j#k!Hl#k#m'}#m#n!Hl#n;'S'};'S;=`(f<%lO'}8r!JZX(Z!bOY!JUZw!JUwx!Arx#O!JU#O#P!B[#P#Q!Gr#Q;'S!JU;'S;=`!Jv<%lO!JU8r!JyP;=`<%l!JU8r!KPP;=`<%l!Gr>^!KZ^$i&j(Z!bOY!KSYZ&cZw!KSwx!CWx!^!KS!^!_!JU!_#O!KS#O#P!DR#P#Q!<n#Q#o!KS#o#p!JU#p;'S!KS;'S;=`!LV<%lO!KS>^!LYP;=`<%l!KS>^!L`P;=`<%l!<n=l!Ll`$i&j(Wp!X7`OY!LcYZ&cZr!Lcrs!=ys!P!Lc!P!Q!Mn!Q!^!Lc!^!_# o!_!}!Lc!}#O#%P#O#P!Dy#P#o!Lc#o#p# o#p;'S!Lc;'S;=`#&Y<%lO!Lc=l!Mwl$i&j(Wp!X7`OY(rYZ&cZr(rrs&cs!^(r!^!_)r!_#O(r#O#P&c#P#W(r#W#X!Mn#X#Z(r#Z#[!Mn#[#](r#]#^!Mn#^#a(r#a#b!Mn#b#g(r#g#h!Mn#h#i(r#i#j!Mn#j#k!Mn#k#m(r#m#n!Mn#n#o(r#o#p)r#p;'S(r;'S;=`*a<%lO(r8Q# vZ(Wp!X7`OY# oZr# ors!@cs!P# o!P!Q#!i!Q!}# o!}#O#$R#O#P!Bq#P;'S# o;'S;=`#$y<%lO# o8Q#!pe(Wp!X7`OY)rZr)rs#O)r#P#W)r#W#X#!i#X#Z)r#Z#[#!i#[#])r#]#^#!i#^#a)r#a#b#!i#b#g)r#g#h#!i#h#i)r#i#j#!i#j#k#!i#k#m)r#m#n#!i#n;'S)r;'S;=`*Z<%lO)r8Q#$WX(WpOY#$RZr#$Rrs!Ars#O#$R#O#P!B[#P#Q# o#Q;'S#$R;'S;=`#$s<%lO#$R8Q#$vP;=`<%l#$R8Q#$|P;=`<%l# o=l#%W^$i&j(WpOY#%PYZ&cZr#%Prs!CWs!^#%P!^!_#$R!_#O#%P#O#P!DR#P#Q!Lc#Q#o#%P#o#p#$R#p;'S#%P;'S;=`#&S<%lO#%P=l#&VP;=`<%l#%P=l#&]P;=`<%l!Lc?O#&kn$i&j(Wp(Z!b!X7`OY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#W%Z#W#X#&`#X#Z%Z#Z#[#&`#[#]%Z#]#^#&`#^#a%Z#a#b#&`#b#g%Z#g#h#&`#h#i%Z#i#j#&`#j#k#&`#k#m%Z#m#n#&`#n#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z9d#(r](Wp(Z!b!X7`OY#(iZr#(irs!Grsw#(iwx# ox!P#(i!P!Q#)k!Q!}#(i!}#O#+`#O#P!Bq#P;'S#(i;'S;=`#,`<%lO#(i9d#)th(Wp(Z!b!X7`OY*gZr*grs'}sw*gwx)rx#O*g#P#W*g#W#X#)k#X#Z*g#Z#[#)k#[#]*g#]#^#)k#^#a*g#a#b#)k#b#g*g#g#h#)k#h#i*g#i#j#)k#j#k#)k#k#m*g#m#n#)k#n;'S*g;'S;=`+Z<%lO*g9d#+gZ(Wp(Z!bOY#+`Zr#+`rs!JUsw#+`wx#$Rx#O#+`#O#P!B[#P#Q#(i#Q;'S#+`;'S;=`#,Y<%lO#+`9d#,]P;=`<%l#+`9d#,cP;=`<%l#(i?O#,o`$i&j(Wp(Z!bOY#,fYZ&cZr#,frs!KSsw#,fwx#%Px!^#,f!^!_#+`!_#O#,f#O#P!DR#P#Q!;Z#Q#o#,f#o#p#+`#p;'S#,f;'S;=`#-q<%lO#,f?O#-tP;=`<%l#,f?O#-zP;=`<%l!;Z07[#.[b$i&j(Wp(Z!b(O0/l!X7`OY!;ZYZ&cZr!;Zrs!<nsw!;Zwx!Lcx!P!;Z!P!Q#&`!Q!^!;Z!^!_#(i!_!}!;Z!}#O#,f#O#P!Dy#P#o!;Z#o#p#(i#p;'S!;Z;'S;=`#-w<%lO!;Z07[#/o_$i&j(Wp(Z!bT0/lOY#/dYZ&cZr#/drs#0nsw#/dwx#4Ox!^#/d!^!_#5}!_#O#/d#O#P#1p#P#o#/d#o#p#5}#p;'S#/d;'S;=`#6|<%lO#/d06j#0w]$i&j(Z!bT0/lOY#0nYZ&cZw#0nwx#1px!^#0n!^!_#3R!_#O#0n#O#P#1p#P#o#0n#o#p#3R#p;'S#0n;'S;=`#3x<%lO#0n05W#1wX$i&jT0/lOY#1pYZ&cZ!^#1p!^!_#2d!_#o#1p#o#p#2d#p;'S#1p;'S;=`#2{<%lO#1p0/l#2iST0/lOY#2dZ;'S#2d;'S;=`#2u<%lO#2d0/l#2xP;=`<%l#2d05W#3OP;=`<%l#1p01O#3YW(Z!bT0/lOY#3RZw#3Rwx#2dx#O#3R#O#P#2d#P;'S#3R;'S;=`#3r<%lO#3R01O#3uP;=`<%l#3R06j#3{P;=`<%l#0n05x#4X]$i&j(WpT0/lOY#4OYZ&cZr#4Ors#1ps!^#4O!^!_#5Q!_#O#4O#O#P#1p#P#o#4O#o#p#5Q#p;'S#4O;'S;=`#5w<%lO#4O00^#5XW(WpT0/lOY#5QZr#5Qrs#2ds#O#5Q#O#P#2d#P;'S#5Q;'S;=`#5q<%lO#5Q00^#5tP;=`<%l#5Q05x#5zP;=`<%l#4O01p#6WY(Wp(Z!bT0/lOY#5}Zr#5}rs#3Rsw#5}wx#5Qx#O#5}#O#P#2d#P;'S#5};'S;=`#6v<%lO#5}01p#6yP;=`<%l#5}07[#7PP;=`<%l#/d)3h#7ab$i&j$Q(Ch(Wp(Z!b!X7`OY!;ZYZ&cZr!;Zrs!<nsw!;Zwx!Lcx!P!;Z!P!Q#&`!Q!^!;Z!^!_#(i!_!}!;Z!}#O#,f#O#P!Dy#P#o!;Z#o#p#(i#p;'S!;Z;'S;=`#-w<%lO!;ZAt#8vb$Z#t$i&j(Wp(Z!b!X7`OY!;ZYZ&cZr!;Zrs!<nsw!;Zwx!Lcx!P!;Z!P!Q#&`!Q!^!;Z!^!_#(i!_!}!;Z!}#O#,f#O#P!Dy#P#o!;Z#o#p#(i#p;'S!;Z;'S;=`#-w<%lO!;Z'Ad#:Zp$i&j(Wp(Z!bs'9tOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!O%Z!O!P!3Y!P!Q%Z!Q![#<_![!^%Z!^!_*g!_!g%Z!g!h!4|!h#O%Z#O#P&c#P#R%Z#R#S#<_#S#U%Z#U#V#?i#V#X%Z#X#Y!4|#Y#b%Z#b#c#>_#c#d#Bq#d#l%Z#l#m#Es#m#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad#<jk$i&j(Wp(Z!bs'9tOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!O%Z!O!P!3Y!P!Q%Z!Q![#<_![!^%Z!^!_*g!_!g%Z!g!h!4|!h#O%Z#O#P&c#P#R%Z#R#S#<_#S#X%Z#X#Y!4|#Y#b%Z#b#c#>_#c#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad#>j_$i&j(Wp(Z!bs'9tOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad#?rd$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q!R#AQ!R!S#AQ!S!^%Z!^!_*g!_#O%Z#O#P&c#P#R%Z#R#S#AQ#S#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad#A]f$i&j(Wp(Z!bs'9tOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q!R#AQ!R!S#AQ!S!^%Z!^!_*g!_#O%Z#O#P&c#P#R%Z#R#S#AQ#S#b%Z#b#c#>_#c#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad#Bzc$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q!Y#DV!Y!^%Z!^!_*g!_#O%Z#O#P&c#P#R%Z#R#S#DV#S#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad#Dbe$i&j(Wp(Z!bs'9tOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q!Y#DV!Y!^%Z!^!_*g!_#O%Z#O#P&c#P#R%Z#R#S#DV#S#b%Z#b#c#>_#c#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad#E|g$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q![#Ge![!^%Z!^!_*g!_!c%Z!c!i#Ge!i#O%Z#O#P&c#P#R%Z#R#S#Ge#S#T%Z#T#Z#Ge#Z#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z'Ad#Gpi$i&j(Wp(Z!bs'9tOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!Q%Z!Q![#Ge![!^%Z!^!_*g!_!c%Z!c!i#Ge!i#O%Z#O#P&c#P#R%Z#R#S#Ge#S#T%Z#T#Z#Ge#Z#b%Z#b#c#>_#c#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z*)x#Il_!g$b$i&j$O)Lv(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z)[#Jv_al$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z04f#LS^h#)`#R-<U(Wp(Z!b$n7`OY*gZr*grs'}sw*gwx)rx!P*g!P!Q#MO!Q!^*g!^!_#Mt!_!`$ f!`#O*g#P;'S*g;'S;=`+Z<%lO*g(n#MXX$k&j(Wp(Z!bOY*gZr*grs'}sw*gwx)rx#O*g#P;'S*g;'S;=`+Z<%lO*g(El#M}Z#r(Ch(Wp(Z!bOY*gZr*grs'}sw*gwx)rx!_*g!_!`#Np!`#O*g#P;'S*g;'S;=`+Z<%lO*g(El#NyX$Q(Ch(Wp(Z!bOY*gZr*grs'}sw*gwx)rx#O*g#P;'S*g;'S;=`+Z<%lO*g(El$ oX#s(Ch(Wp(Z!bOY*gZr*grs'}sw*gwx)rx#O*g#P;'S*g;'S;=`+Z<%lO*g*)x$!ga#`*!Y$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`0z!`!a$#l!a#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(K[$#w_#k(Cl$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z*)x$%Vag!*r#s(Ch$f#|$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`$&[!`!a$'f!a#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KW$&g_#s(Ch$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KW$'qa#r(Ch$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`!a$(v!a#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KW$)R`#r(Ch$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(Kd$*`a(r(Ct$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!a%Z!a!b$+e!b#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KW$+p`$i&j#{(Ch(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z%#`$,}_!|$Ip$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z04f$.X_!S0,v$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(n$/]Z$i&jO!^$0O!^!_$0f!_#i$0O#i#j$0k#j#l$0O#l#m$2^#m#o$0O#o#p$0f#p;'S$0O;'S;=`$4i<%lO$0O(n$0VT_#S$i&jO!^&c!_#o&c#p;'S&c;'S;=`&w<%lO&c#S$0kO_#S(n$0p[$i&jO!Q&c!Q![$1f![!^&c!_!c&c!c!i$1f!i#T&c#T#Z$1f#Z#o&c#o#p$3|#p;'S&c;'S;=`&w<%lO&c(n$1kZ$i&jO!Q&c!Q![$2^![!^&c!_!c&c!c!i$2^!i#T&c#T#Z$2^#Z#o&c#p;'S&c;'S;=`&w<%lO&c(n$2cZ$i&jO!Q&c!Q![$3U![!^&c!_!c&c!c!i$3U!i#T&c#T#Z$3U#Z#o&c#p;'S&c;'S;=`&w<%lO&c(n$3ZZ$i&jO!Q&c!Q![$0O![!^&c!_!c&c!c!i$0O!i#T&c#T#Z$0O#Z#o&c#p;'S&c;'S;=`&w<%lO&c#S$4PR!Q![$4Y!c!i$4Y#T#Z$4Y#S$4]S!Q![$4Y!c!i$4Y#T#Z$4Y#q#r$0f(n$4lP;=`<%l$0O#1[$4z_!Y#)l$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z(KW$6U`#x(Ch$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z+;p$7c_$i&j(Wp(Z!b(a+4QOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z07[$8qk$i&j(Wp(Z!b(T,2j$_#t(e$I[OY%ZYZ&cZr%Zrs&}st%Ztu$8buw%Zwx(rx}%Z}!O$:f!O!Q%Z!Q![$8b![!^%Z!^!_*g!_!c%Z!c!}$8b!}#O%Z#O#P&c#P#R%Z#R#S$8b#S#T%Z#T#o$8b#o#p*g#p$g%Z$g;'S$8b;'S;=`$<l<%lO$8b+d$:qk$i&j(Wp(Z!b$_#tOY%ZYZ&cZr%Zrs&}st%Ztu$:fuw%Zwx(rx}%Z}!O$:f!O!Q%Z!Q![$:f![!^%Z!^!_*g!_!c%Z!c!}$:f!}#O%Z#O#P&c#P#R%Z#R#S$:f#S#T%Z#T#o$:f#o#p*g#p$g%Z$g;'S$:f;'S;=`$<f<%lO$:f+d$<iP;=`<%l$:f07[$<oP;=`<%l$8b#Jf$<{X!_#Hb(Wp(Z!bOY*gZr*grs'}sw*gwx)rx#O*g#P;'S*g;'S;=`+Z<%lO*g,#x$=sa(y+JY$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_!`Ka!`#O%Z#O#P&c#P#o%Z#o#p*g#p#q$+e#q;'S%Z;'S;=`+a<%lO%Z)>v$?V_!^(CdvBr$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z?O$@a_!q7`$i&j(Wp(Z!bOY%ZYZ&cZr%Zrs&}sw%Zwx(rx!^%Z!^!_*g!_#O%Z#O#P&c#P#o%Z#o#p*g#p;'S%Z;'S;=`+a<%lO%Z07[$Aq|$i&j(Wp(Z!b'|0/l$]#t(T,2j(e$I[OX%ZXY+gYZ&cZ[+g[p%Zpq+gqr%Zrs&}st%ZtuEruw%Zwx(rx}%Z}!OGv!O!Q%Z!Q![Er![!^%Z!^!_*g!_!c%Z!c!}Er!}#O%Z#O#P&c#P#R%Z#R#SEr#S#T%Z#T#oEr#o#p*g#p$f%Z$f$g+g$g#BYEr#BY#BZ$A`#BZ$ISEr$IS$I_$A`$I_$JTEr$JT$JU$A`$JU$KVEr$KV$KW$A`$KW&FUEr&FU&FV$A`&FV;'SEr;'S;=`I|<%l?HTEr?HT?HU$A`?HUOEr07[$D|k$i&j(Wp(Z!b'}0/l$]#t(T,2j(e$I[OY%ZYZ&cZr%Zrs&}st%ZtuEruw%Zwx(rx}%Z}!OGv!O!Q%Z!Q![Er![!^%Z!^!_*g!_!c%Z!c!}Er!}#O%Z#O#P&c#P#R%Z#R#SEr#S#T%Z#T#oEr#o#p*g#p$g%Z$g;'SEr;'S;=`I|<%lOEr",
	tokenizers: [
		Sv,
		Cv,
		wv,
		Ev,
		2,
		3,
		4,
		5,
		6,
		7,
		8,
		9,
		10,
		11,
		12,
		13,
		14,
		xv,
		new Ch("$S~RRtu[#O#Pg#S#T#|~_P#o#pb~gOx~~jVO#i!P#i#j!U#j#l!P#l#m!q#m;'S!P;'S;=`#v<%lO!P~!UO!U~~!XS!Q![!e!c!i!e#T#Z!e#o#p#Z~!hR!Q![!q!c!i!q#T#Z!q~!tR!Q![!}!c!i!}#T#Z!}~#QR!Q![!P!c!i!P#T#Z!P~#^R!Q![#g!c!i#g#T#Z#g~#jS!Q![#g!c!i#g#T#Z#g#q#r!P~#yP;=`<%l!P~$RO(c~~", 141, 340),
		new Ch("j~RQYZXz{^~^O(Q~~aP!P!Qd~iO(R~~", 25, 323)
	],
	topRules: {
		Script: [0, 7],
		SingleExpression: [1, 276],
		SingleClassItem: [2, 277]
	},
	dialects: {
		jsx: 0,
		ts: 15175
	},
	dynamicPrecedences: {
		80: 1,
		82: 1,
		94: 1,
		169: 1,
		199: 1
	},
	specialized: [
		{
			term: 327,
			get: (e) => Ov[e] || -1
		},
		{
			term: 343,
			get: (e) => kv[e] || -1
		},
		{
			term: 95,
			get: (e) => Av[e] || -1
		}
	],
	tokenPrec: 15201
}), Mv = [
	/*@__PURE__*/ Mp("function ${name}(${params}) {\n	${}\n}", {
		label: "function",
		detail: "definition",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("for (let ${index} = 0; ${index} < ${bound}; ${index}++) {\n	${}\n}", {
		label: "for",
		detail: "loop",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("for (let ${name} of ${collection}) {\n	${}\n}", {
		label: "for",
		detail: "of loop",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("do {\n	${}\n} while (${})", {
		label: "do",
		detail: "loop",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("while (${}) {\n	${}\n}", {
		label: "while",
		detail: "loop",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("try {\n	${}\n} catch (${error}) {\n	${}\n}", {
		label: "try",
		detail: "/ catch block",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("if (${}) {\n	${}\n}", {
		label: "if",
		detail: "block",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("if (${}) {\n	${}\n} else {\n	${}\n}", {
		label: "if",
		detail: "/ else block",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("class ${name} {\n	constructor(${params}) {\n		${}\n	}\n}", {
		label: "class",
		detail: "definition",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("import {${names}} from \"${module}\"\n${}", {
		label: "import",
		detail: "named",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("import ${name} from \"${module}\"\n${}", {
		label: "import",
		detail: "default",
		type: "keyword"
	})
], Nv = /*@__PURE__*/ Mv.concat([
	/*@__PURE__*/ Mp("interface ${name} {\n	${}\n}", {
		label: "interface",
		detail: "definition",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("type ${name} = ${type}", {
		label: "type",
		detail: "definition",
		type: "keyword"
	}),
	/*@__PURE__*/ Mp("enum ${name} {\n	${}\n}", {
		label: "enum",
		detail: "definition",
		type: "keyword"
	})
]), Pv = /*@__PURE__*/ new Js(), Fv = /*@__PURE__*/ new Set([
	"Script",
	"Block",
	"FunctionExpression",
	"FunctionDeclaration",
	"ArrowFunction",
	"MethodDeclaration",
	"ForStatement"
]);
function Iv(e) {
	return (t, n) => {
		let r = t.node.getChild("VariableDefinition");
		return r && n(r, e), !0;
	};
}
var Lv = ["FunctionDeclaration"], Rv = {
	FunctionDeclaration: /*@__PURE__*/ Iv("function"),
	ClassDeclaration: /*@__PURE__*/ Iv("class"),
	ClassExpression: () => !0,
	EnumDeclaration: /*@__PURE__*/ Iv("constant"),
	TypeAliasDeclaration: /*@__PURE__*/ Iv("type"),
	NamespaceDeclaration: /*@__PURE__*/ Iv("namespace"),
	VariableDefinition(e, t) {
		e.matchContext(Lv) || t(e, "variable");
	},
	TypeDefinition(e, t) {
		t(e, "type");
	},
	__proto__: null
};
function zv(e, t) {
	let n = Pv.get(t);
	if (n) return n;
	let r = [], i = !0;
	function a(t, n) {
		let i = e.sliceString(t.from, t.to);
		r.push({
			label: i,
			type: n
		});
	}
	return t.cursor(W.IncludeAnonymous).iterate((t) => {
		if (i) i = !1;
		else if (t.name) {
			let e = Rv[t.name];
			if (e && e(t, a) || Fv.has(t.name)) return !1;
		} else if (t.to - t.from > 8192) {
			for (let n of zv(e, t.node)) r.push(n);
			return !1;
		}
	}), Pv.set(t, r), r;
}
var Bv = /^[\w$\xa1-\uffff][\w$\d\xa1-\uffff]*$/, Vv = [
	"TemplateString",
	"String",
	"RegExp",
	"LineComment",
	"BlockComment",
	"VariableDefinition",
	"TypeDefinition",
	"Label",
	"PropertyDefinition",
	"PropertyName",
	"PrivatePropertyDefinition",
	"PrivatePropertyName",
	"JSXText",
	"JSXAttributeValue",
	"JSXOpenTag",
	"JSXCloseTag",
	"JSXSelfClosingTag",
	".",
	"?."
];
function Hv(e) {
	let t = J(e.state).resolveInner(e.pos, -1);
	if (Vv.indexOf(t.name) > -1) return null;
	let n = t.name == "VariableName" || t.to - t.from < 20 && Bv.test(e.state.sliceDoc(t.from, t.to));
	if (!n && !e.explicit) return null;
	let r = [];
	for (let n = t; n; n = n.parent) Fv.has(n.name) && (r = r.concat(zv(e.state.doc, n)));
	return {
		options: r,
		from: n ? t.from : e.pos,
		validFor: Bv
	};
}
var Uv = /*@__PURE__*/ Gc.define({
	name: "javascript",
	parser: /*@__PURE__*/ jv.configure({ props: [/*@__PURE__*/ ul.add({
		IfStatement: /*@__PURE__*/ Sl({ except: /^\s*({|else\b)/ }),
		TryStatement: /*@__PURE__*/ Sl({ except: /^\s*({|catch\b|finally\b)/ }),
		LabeledStatement: xl,
		SwitchBody: (e) => {
			let t = e.textAfter, n = /^\s*\}/.test(t), r = /^\s*(case|default)\b/.test(t);
			return e.baseIndent + (n ? 0 : r ? 1 : 2) * e.unit;
		},
		Block: /*@__PURE__*/ yl({ closing: "}" }),
		ArrowFunction: (e) => e.baseIndent + e.unit,
		"TemplateString BlockComment": () => null,
		"Statement Property": /*@__PURE__*/ Sl({ except: /^\s*{/ }),
		JSXElement(e) {
			let t = /^\s*<\//.test(e.textAfter);
			return e.lineIndent(e.node.from) + (t ? 0 : e.unit);
		},
		JSXEscape(e) {
			let t = /\s*\}/.test(e.textAfter);
			return e.lineIndent(e.node.from) + (t ? 0 : e.unit);
		},
		"JSXOpenTag JSXSelfClosingTag"(e) {
			return e.column(e.node.from) + e.unit;
		}
	}), /*@__PURE__*/ wl.add({
		"Block ClassBody SwitchBody EnumBody ObjectExpression ArrayExpression ObjectType": Tl,
		BlockComment(e) {
			return {
				from: e.from + 2,
				to: e.to - 2
			};
		},
		JSXElement(e) {
			let t = e.firstChild;
			if (!t || t.name == "JSXSelfClosingTag") return null;
			let n = e.lastChild;
			return {
				from: t.to,
				to: n.type.isError ? e.to : n.from
			};
		},
		"JSXSelfClosingTag JSXOpenTag"(e) {
			let t = e.firstChild?.nextSibling, n = e.lastChild;
			return !t || t.type.isError ? null : {
				from: t.to,
				to: n.type.isError ? e.to : n.from
			};
		}
	})] }),
	languageData: {
		closeBrackets: { brackets: [
			"(",
			"[",
			"{",
			"'",
			"\"",
			"`"
		] },
		commentTokens: {
			line: "//",
			block: {
				open: "/*",
				close: "*/"
			}
		},
		indentOnInput: /^\s*(?:case |default:|\{|\}|<\/)$/,
		wordChars: "$"
	}
}), Wv = {
	test: (e) => /^JSX/.test(e.name),
	facet: /*@__PURE__*/ Vc({ commentTokens: { block: {
		open: "{/*",
		close: "*/}"
	} } })
}, Gv = /*@__PURE__*/ Uv.configure({ dialect: "ts" }, "typescript"), Kv = /*@__PURE__*/ Uv.configure({
	dialect: "jsx",
	props: [/*@__PURE__*/ Hc.add((e) => e.isTop ? [Wv] : void 0)]
}), qv = /*@__PURE__*/ Uv.configure({
	dialect: "jsx ts",
	props: [/*@__PURE__*/ Hc.add((e) => e.isTop ? [Wv] : void 0)]
}, "typescript"), Jv = (e) => ({
	label: e,
	type: "keyword"
}), Yv = /*@__PURE__*/ "break case const continue default delete export extends false finally in instanceof let new return static super switch this throw true typeof var yield".split(" ").map(Jv), Xv = /*@__PURE__*/ Yv.concat(/*@__PURE__*/ [
	"declare",
	"implements",
	"private",
	"protected",
	"public"
].map(Jv));
function Zv(e = {}) {
	let t = e.jsx ? e.typescript ? qv : Kv : e.typescript ? Gv : Uv, n = e.typescript ? Nv.concat(Xv) : Mv.concat(Yv);
	return new nl(t, [
		Uv.data.of({ autocomplete: Cf(Vv, Sf(n)) }),
		Uv.data.of({ autocomplete: Hv }),
		e.jsx ? ty : []
	]);
}
function Qv(e) {
	for (;;) {
		if (e.name == "JSXOpenTag" || e.name == "JSXSelfClosingTag" || e.name == "JSXFragmentTag") return e;
		if (e.name == "JSXEscape" || !e.parent) return null;
		e = e.parent;
	}
}
function $v(e, t, n = e.length) {
	for (let r = t?.firstChild; r; r = r.nextSibling) if (r.name == "JSXIdentifier" || r.name == "JSXBuiltin" || r.name == "JSXNamespacedName" || r.name == "JSXMemberExpression") return e.sliceString(r.from, Math.min(r.to, n));
	return "";
}
var ey = typeof navigator == "object" && /*@__PURE__*/ /Android\b/.test(navigator.userAgent), ty = /*@__PURE__*/ V.inputHandler.of((e, t, n, r, i) => {
	if ((ey ? e.composing : e.compositionStarted) || e.state.readOnly || t != n || r != ">" && r != "/" || !Uv.isActiveAt(e.state, t, -1)) return !1;
	let a = i(), { state: o } = a, s = o.changeByRange((e) => {
		let { head: t } = e, n = J(o).resolveInner(t - 1, -1), i;
		if (n.name == "JSXStartTag" && (n = n.parent), !(o.doc.sliceString(t - 1, t) != r || n.name == "JSXAttributeValue" && n.to > t)) {
			if (r == ">" && n.name == "JSXFragmentTag") return {
				range: e,
				changes: {
					from: t,
					insert: "</>"
				}
			};
			if (r == "/" && n.name == "JSXStartCloseTag") {
				let e = n.parent, r = e.parent;
				if (r && e.from == t - 2 && ((i = $v(o.doc, r.firstChild, t)) || r.firstChild?.name == "JSXFragmentTag")) {
					let e = `${i}>`;
					return {
						range: k.cursor(t + e.length, -1),
						changes: {
							from: t,
							insert: e
						}
					};
				}
			} else if (r == ">") {
				let r = Qv(n);
				if (r && r.name == "JSXOpenTag" && !/^\/?>|^<\//.test(o.doc.sliceString(t, t + 2)) && (i = $v(o.doc, r, t))) return {
					range: e,
					changes: {
						from: t,
						insert: `</${i}>`
					}
				};
			}
		}
		return { range: e };
	});
	return !s.changes.empty && (e.dispatch([a, o.update(s, {
		userEvent: "input.complete",
		scrollIntoView: !0
	})]), !0);
}), ny = [
	"_blank",
	"_self",
	"_top",
	"_parent"
], ry = [
	"ascii",
	"utf-8",
	"utf-16",
	"latin1",
	"latin1"
], iy = [
	"get",
	"post",
	"put",
	"delete"
], ay = [
	"application/x-www-form-urlencoded",
	"multipart/form-data",
	"text/plain"
], oy = ["true", "false"], $ = {}, sy = {
	a: { attrs: {
		href: null,
		ping: null,
		type: null,
		media: null,
		target: ny,
		hreflang: null
	} },
	abbr: $,
	address: $,
	area: { attrs: {
		alt: null,
		coords: null,
		href: null,
		target: null,
		ping: null,
		media: null,
		hreflang: null,
		type: null,
		shape: [
			"default",
			"rect",
			"circle",
			"poly"
		]
	} },
	article: $,
	aside: $,
	audio: { attrs: {
		src: null,
		mediagroup: null,
		crossorigin: ["anonymous", "use-credentials"],
		preload: [
			"none",
			"metadata",
			"auto"
		],
		autoplay: ["autoplay"],
		loop: ["loop"],
		controls: ["controls"]
	} },
	b: $,
	base: { attrs: {
		href: null,
		target: ny
	} },
	bdi: $,
	bdo: $,
	blockquote: { attrs: { cite: null } },
	body: $,
	br: $,
	button: { attrs: {
		form: null,
		formaction: null,
		name: null,
		value: null,
		autofocus: ["autofocus"],
		disabled: ["autofocus"],
		formenctype: ay,
		formmethod: iy,
		formnovalidate: ["novalidate"],
		formtarget: ny,
		type: [
			"submit",
			"reset",
			"button"
		]
	} },
	canvas: { attrs: {
		width: null,
		height: null
	} },
	caption: $,
	center: $,
	cite: $,
	code: $,
	col: { attrs: { span: null } },
	colgroup: { attrs: { span: null } },
	command: { attrs: {
		type: [
			"command",
			"checkbox",
			"radio"
		],
		label: null,
		icon: null,
		radiogroup: null,
		command: null,
		title: null,
		disabled: ["disabled"],
		checked: ["checked"]
	} },
	data: { attrs: { value: null } },
	datagrid: { attrs: {
		disabled: ["disabled"],
		multiple: ["multiple"]
	} },
	datalist: { attrs: { data: null } },
	dd: $,
	del: { attrs: {
		cite: null,
		datetime: null
	} },
	details: { attrs: { open: ["open"] } },
	dfn: $,
	div: $,
	dl: $,
	dt: $,
	em: $,
	embed: { attrs: {
		src: null,
		type: null,
		width: null,
		height: null
	} },
	eventsource: { attrs: { src: null } },
	fieldset: { attrs: {
		disabled: ["disabled"],
		form: null,
		name: null
	} },
	figcaption: $,
	figure: $,
	footer: $,
	form: { attrs: {
		action: null,
		name: null,
		"accept-charset": ry,
		autocomplete: ["on", "off"],
		enctype: ay,
		method: iy,
		novalidate: ["novalidate"],
		target: ny
	} },
	h1: $,
	h2: $,
	h3: $,
	h4: $,
	h5: $,
	h6: $,
	head: { children: [
		"title",
		"base",
		"link",
		"style",
		"meta",
		"script",
		"noscript",
		"command"
	] },
	header: $,
	hgroup: $,
	hr: $,
	html: { attrs: { manifest: null } },
	i: $,
	iframe: { attrs: {
		src: null,
		srcdoc: null,
		name: null,
		width: null,
		height: null,
		sandbox: [
			"allow-top-navigation",
			"allow-same-origin",
			"allow-forms",
			"allow-scripts"
		],
		seamless: ["seamless"]
	} },
	img: { attrs: {
		alt: null,
		src: null,
		ismap: null,
		usemap: null,
		width: null,
		height: null,
		crossorigin: ["anonymous", "use-credentials"]
	} },
	input: { attrs: {
		alt: null,
		dirname: null,
		form: null,
		formaction: null,
		height: null,
		list: null,
		max: null,
		maxlength: null,
		min: null,
		name: null,
		pattern: null,
		placeholder: null,
		size: null,
		src: null,
		step: null,
		value: null,
		width: null,
		accept: [
			"audio/*",
			"video/*",
			"image/*"
		],
		autocomplete: ["on", "off"],
		autofocus: ["autofocus"],
		checked: ["checked"],
		disabled: ["disabled"],
		formenctype: ay,
		formmethod: iy,
		formnovalidate: ["novalidate"],
		formtarget: ny,
		multiple: ["multiple"],
		readonly: ["readonly"],
		required: ["required"],
		type: [
			"hidden",
			"text",
			"search",
			"tel",
			"url",
			"email",
			"password",
			"datetime",
			"date",
			"month",
			"week",
			"time",
			"datetime-local",
			"number",
			"range",
			"color",
			"checkbox",
			"radio",
			"file",
			"submit",
			"image",
			"reset",
			"button"
		]
	} },
	ins: { attrs: {
		cite: null,
		datetime: null
	} },
	kbd: $,
	keygen: { attrs: {
		challenge: null,
		form: null,
		name: null,
		autofocus: ["autofocus"],
		disabled: ["disabled"],
		keytype: ["RSA"]
	} },
	label: { attrs: {
		for: null,
		form: null
	} },
	legend: $,
	li: { attrs: { value: null } },
	link: { attrs: {
		href: null,
		type: null,
		hreflang: null,
		media: null,
		sizes: [
			"all",
			"16x16",
			"16x16 32x32",
			"16x16 32x32 64x64"
		]
	} },
	map: { attrs: { name: null } },
	mark: $,
	menu: { attrs: {
		label: null,
		type: [
			"list",
			"context",
			"toolbar"
		]
	} },
	meta: { attrs: {
		content: null,
		charset: ry,
		name: [
			"viewport",
			"application-name",
			"author",
			"description",
			"generator",
			"keywords"
		],
		"http-equiv": [
			"content-language",
			"content-type",
			"default-style",
			"refresh"
		]
	} },
	meter: { attrs: {
		value: null,
		min: null,
		low: null,
		high: null,
		max: null,
		optimum: null
	} },
	nav: $,
	noscript: $,
	object: { attrs: {
		data: null,
		type: null,
		name: null,
		usemap: null,
		form: null,
		width: null,
		height: null,
		typemustmatch: ["typemustmatch"]
	} },
	ol: {
		attrs: {
			reversed: ["reversed"],
			start: null,
			type: [
				"1",
				"a",
				"A",
				"i",
				"I"
			]
		},
		children: [
			"li",
			"script",
			"template",
			"ul",
			"ol"
		]
	},
	optgroup: { attrs: {
		disabled: ["disabled"],
		label: null
	} },
	option: { attrs: {
		disabled: ["disabled"],
		label: null,
		selected: ["selected"],
		value: null
	} },
	output: { attrs: {
		for: null,
		form: null,
		name: null
	} },
	p: $,
	param: { attrs: {
		name: null,
		value: null
	} },
	pre: $,
	progress: { attrs: {
		value: null,
		max: null
	} },
	q: { attrs: { cite: null } },
	rp: $,
	rt: $,
	ruby: $,
	samp: $,
	script: { attrs: {
		type: ["text/javascript"],
		src: null,
		async: ["async"],
		defer: ["defer"],
		charset: ry
	} },
	section: $,
	select: { attrs: {
		form: null,
		name: null,
		size: null,
		autofocus: ["autofocus"],
		disabled: ["disabled"],
		multiple: ["multiple"]
	} },
	slot: { attrs: { name: null } },
	small: $,
	source: { attrs: {
		src: null,
		type: null,
		media: null
	} },
	span: $,
	strong: $,
	style: { attrs: {
		type: ["text/css"],
		media: null,
		scoped: null
	} },
	sub: $,
	summary: $,
	sup: $,
	table: $,
	tbody: $,
	td: { attrs: {
		colspan: null,
		rowspan: null,
		headers: null
	} },
	template: $,
	textarea: { attrs: {
		dirname: null,
		form: null,
		maxlength: null,
		name: null,
		placeholder: null,
		rows: null,
		cols: null,
		autofocus: ["autofocus"],
		disabled: ["disabled"],
		readonly: ["readonly"],
		required: ["required"],
		wrap: ["soft", "hard"]
	} },
	tfoot: $,
	th: { attrs: {
		colspan: null,
		rowspan: null,
		headers: null,
		scope: [
			"row",
			"col",
			"rowgroup",
			"colgroup"
		]
	} },
	thead: $,
	time: { attrs: { datetime: null } },
	title: $,
	tr: $,
	track: { attrs: {
		src: null,
		label: null,
		default: null,
		kind: [
			"subtitles",
			"captions",
			"descriptions",
			"chapters",
			"metadata"
		],
		srclang: null
	} },
	ul: { children: [
		"li",
		"script",
		"template",
		"ul",
		"ol"
	] },
	var: $,
	video: { attrs: {
		src: null,
		poster: null,
		width: null,
		height: null,
		crossorigin: ["anonymous", "use-credentials"],
		preload: [
			"auto",
			"metadata",
			"none"
		],
		autoplay: ["autoplay"],
		mediagroup: ["movie"],
		muted: ["muted"],
		controls: ["controls"]
	} },
	wbr: $
}, cy = {
	accesskey: null,
	class: null,
	contenteditable: oy,
	contextmenu: null,
	dir: [
		"ltr",
		"rtl",
		"auto"
	],
	draggable: [
		"true",
		"false",
		"auto"
	],
	dropzone: [
		"copy",
		"move",
		"link",
		"string:",
		"file:"
	],
	hidden: ["hidden"],
	id: null,
	inert: ["inert"],
	itemid: null,
	itemprop: null,
	itemref: null,
	itemscope: ["itemscope"],
	itemtype: null,
	lang: [
		"ar",
		"bn",
		"de",
		"en-GB",
		"en-US",
		"es",
		"fr",
		"hi",
		"id",
		"ja",
		"pa",
		"pt",
		"ru",
		"tr",
		"zh"
	],
	spellcheck: oy,
	autocorrect: oy,
	autocapitalize: oy,
	style: null,
	tabindex: null,
	title: null,
	translate: ["yes", "no"],
	rel: [
		"stylesheet",
		"alternate",
		"author",
		"bookmark",
		"help",
		"license",
		"next",
		"nofollow",
		"noreferrer",
		"prefetch",
		"prev",
		"search",
		"tag"
	],
	role: /*@__PURE__*/ "alert application article banner button cell checkbox complementary contentinfo dialog document feed figure form grid gridcell heading img list listbox listitem main navigation region row rowgroup search switch tab table tabpanel textbox timer".split(" "),
	"aria-activedescendant": null,
	"aria-atomic": oy,
	"aria-autocomplete": [
		"inline",
		"list",
		"both",
		"none"
	],
	"aria-busy": oy,
	"aria-checked": [
		"true",
		"false",
		"mixed",
		"undefined"
	],
	"aria-controls": null,
	"aria-describedby": null,
	"aria-disabled": oy,
	"aria-dropeffect": null,
	"aria-expanded": [
		"true",
		"false",
		"undefined"
	],
	"aria-flowto": null,
	"aria-grabbed": [
		"true",
		"false",
		"undefined"
	],
	"aria-haspopup": oy,
	"aria-hidden": oy,
	"aria-invalid": [
		"true",
		"false",
		"grammar",
		"spelling"
	],
	"aria-label": null,
	"aria-labelledby": null,
	"aria-level": null,
	"aria-live": [
		"off",
		"polite",
		"assertive"
	],
	"aria-multiline": oy,
	"aria-multiselectable": oy,
	"aria-owns": null,
	"aria-posinset": null,
	"aria-pressed": [
		"true",
		"false",
		"mixed",
		"undefined"
	],
	"aria-readonly": oy,
	"aria-relevant": null,
	"aria-required": oy,
	"aria-selected": [
		"true",
		"false",
		"undefined"
	],
	"aria-setsize": null,
	"aria-sort": [
		"ascending",
		"descending",
		"none",
		"other"
	],
	"aria-valuemax": null,
	"aria-valuemin": null,
	"aria-valuenow": null,
	"aria-valuetext": null
}, ly = /*@__PURE__*/ "beforeunload copy cut dragstart dragover dragleave dragenter dragend drag paste focus blur change click load mousedown mouseenter mouseleave mouseup keydown keyup resize scroll unload".split(" ").map((e) => "on" + e);
for (let e of ly) cy[e] = null;
var uy = class {
	constructor(e, t) {
		this.tags = {
			...sy,
			...e
		}, this.globalAttrs = {
			...cy,
			...t
		}, this.allTags = Object.keys(this.tags), this.globalAttrNames = Object.keys(this.globalAttrs);
	}
};
uy.default = /*@__PURE__*/ new uy();
function dy(e, t, n = e.length) {
	if (!t) return "";
	let r = t.firstChild, i = r && r.getChild("TagName");
	return i ? e.sliceString(i.from, Math.min(i.to, n)) : "";
}
function fy(e, t = !1) {
	for (; e; e = e.parent) if (e.name == "Element") {
		if (t) t = !1;
		else return e;
	}
	return null;
}
function py(e, t, n) {
	return n.tags[dy(e, fy(t))]?.children || n.allTags;
}
function my(e, t) {
	let n = [];
	for (let r = fy(t); r && !r.type.isTop; r = fy(r.parent)) {
		let i = dy(e, r);
		if (i && r.lastChild.name == "CloseTag") break;
		i && n.indexOf(i) < 0 && (t.name == "EndTag" || t.from >= r.firstChild.to) && n.push(i);
	}
	return n;
}
var hy = /^[:\-\.\w\u00b7-\uffff]*$/;
function gy(e, t, n, r, i) {
	let a = /\s*>/.test(e.sliceDoc(i, i + 5)) ? "" : ">", o = fy(n, n.name == "StartTag" || n.name == "TagName");
	return {
		from: r,
		to: i,
		options: py(e.doc, o, t).map((e) => ({
			label: e,
			type: "type"
		})).concat(my(e.doc, n).map((e, t) => ({
			label: "/" + e,
			apply: "/" + e + a,
			type: "type",
			boost: 99 - t
		}))),
		validFor: /^\/?[:\-\.\w\u00b7-\uffff]*$/
	};
}
function _y(e, t, n, r) {
	let i = /\s*>/.test(e.sliceDoc(r, r + 5)) ? "" : ">";
	return {
		from: n,
		to: r,
		options: my(e.doc, t).map((e, t) => ({
			label: e,
			apply: e + i,
			type: "type",
			boost: 99 - t
		})),
		validFor: hy
	};
}
function vy(e, t, n, r) {
	let i = [], a = 0;
	for (let r of py(e.doc, n, t)) i.push({
		label: "<" + r,
		type: "type"
	});
	for (let t of my(e.doc, n)) i.push({
		label: "</" + t + ">",
		type: "type",
		boost: 99 - a++
	});
	return {
		from: r,
		to: r,
		options: i,
		validFor: /^<\/?[:\-\.\w\u00b7-\uffff]*$/
	};
}
function yy(e, t, n, r, i) {
	let a = fy(n), o = a ? t.tags[dy(e.doc, a)] : null, s = o && o.attrs ? Object.keys(o.attrs) : [];
	return {
		from: r,
		to: i,
		options: (o && o.globalAttrs === !1 ? s : s.length ? s.concat(t.globalAttrNames) : t.globalAttrNames).map((e) => ({
			label: e,
			type: "property"
		})),
		validFor: hy
	};
}
function by(e, t, n, r, i) {
	let a = n.parent?.getChild("AttributeName"), o = [], s;
	if (a) {
		let c = e.sliceDoc(a.from, a.to), l = t.globalAttrs[c];
		if (!l) {
			let r = fy(n), i = r ? t.tags[dy(e.doc, r)] : null;
			l = i?.attrs && i.attrs[c];
		}
		if (l) {
			let t = e.sliceDoc(r, i).toLowerCase(), n = "\"", a = "\"";
			/^['"]/.test(t) ? (s = t[0] == "\"" ? /^[^"]*$/ : /^[^']*$/, n = "", a = e.sliceDoc(i, i + 1) == t[0] ? "" : t[0], t = t.slice(1), r++) : s = /^[^\s<>='"]*$/;
			for (let e of l) o.push({
				label: e,
				apply: n + e + a,
				type: "constant"
			});
		}
	}
	return {
		from: r,
		to: i,
		options: o,
		validFor: s
	};
}
function xy(e, t) {
	let { state: n, pos: r } = t, i = J(n).resolveInner(r, -1), a = i.resolve(r);
	for (let e = r, t; a == i && (t = i.childBefore(e));) {
		let n = t.lastChild;
		if (!n || !n.type.isError || n.from < n.to) break;
		a = i = t, e = n.from;
	}
	return i.name == "TagName" ? i.parent && /CloseTag$/.test(i.parent.name) ? _y(n, i, i.from, r) : gy(n, e, i, i.from, r) : i.name == "StartTag" || i.name == "IncompleteTag" ? gy(n, e, i, r, r) : i.name == "StartCloseTag" || i.name == "IncompleteCloseTag" ? _y(n, i, r, r) : i.name == "OpenTag" || i.name == "SelfClosingTag" || i.name == "AttributeName" ? yy(n, e, i, i.name == "AttributeName" ? i.from : r, r) : i.name == "Is" || i.name == "AttributeValue" || i.name == "UnquotedAttributeValue" ? by(n, e, i, i.name == "Is" ? r : i.from, r) : t.explicit && (a.name == "Element" || a.name == "Text" || a.name == "Document") ? vy(n, e, i, r) : null;
}
function Sy(e) {
	return xy(uy.default, e);
}
function Cy(e) {
	let { extraTags: t, extraGlobalAttributes: n } = e, r = n || t ? new uy(t, n) : uy.default;
	return (e) => xy(r, e);
}
var wy = /*@__PURE__*/ Uv.parser.configure({ top: "SingleExpression" }), Ty = [
	{
		tag: "script",
		attrs: (e) => e.type == "text/typescript" || e.lang == "ts",
		parser: Gv.parser
	},
	{
		tag: "script",
		attrs: (e) => e.type == "text/babel" || e.type == "text/jsx",
		parser: Kv.parser
	},
	{
		tag: "script",
		attrs: (e) => e.type == "text/typescript-jsx",
		parser: qv.parser
	},
	{
		tag: "script",
		attrs(e) {
			return /^(importmap|speculationrules|application\/(.+\+)?json)$/i.test(e.type);
		},
		parser: wy
	},
	{
		tag: "script",
		attrs(e) {
			return !e.type || /^(?:text|application)\/(?:x-)?(?:java|ecma)script$|^module$|^$/i.test(e.type);
		},
		parser: Uv.parser
	},
	{
		tag: "style",
		attrs(e) {
			return (!e.lang || e.lang == "css") && (!e.type || /^(text\/)?(x-)?(stylesheet|css)$/i.test(e.type));
		},
		parser: J_.parser
	}
], Ey = /*@__PURE__*/ [{
	name: "style",
	parser: /*@__PURE__*/ J_.parser.configure({ top: "Styles" })
}].concat(/*@__PURE__*/ ly.map((e) => ({
	name: e,
	parser: Uv.parser
}))), Dy = /*@__PURE__*/ Gc.define({
	name: "html",
	parser: /*@__PURE__*/ qg.configure({ props: [
		/*@__PURE__*/ ul.add({
			Element(e) {
				let t = /^(\s*)(<\/)?/.exec(e.textAfter);
				return e.node.to <= e.pos + t[0].length ? e.continue() : e.lineIndent(e.node.from) + (t[2] ? 0 : e.unit);
			},
			"OpenTag CloseTag SelfClosingTag"(e) {
				return e.column(e.node.from) + e.unit;
			},
			Document(e) {
				if (e.pos + /\s*/.exec(e.textAfter)[0].length < e.node.to) return e.continue();
				let t = null, n;
				for (let n = e.node;;) {
					let e = n.lastChild;
					if (!e || e.name != "Element" || e.to != n.to) break;
					t = n = e;
				}
				return t && !((n = t.lastChild) && (n.name == "CloseTag" || n.name == "SelfClosingTag")) ? e.lineIndent(t.from) + e.unit : null;
			}
		}),
		/*@__PURE__*/ wl.add({ Element(e) {
			let t = e.firstChild, n = e.lastChild;
			return !t || t.name != "OpenTag" ? null : {
				from: t.to,
				to: n.name == "CloseTag" ? n.from : e.to
			};
		} }),
		/*@__PURE__*/ Il.add({ "OpenTag CloseTag": (e) => e.getChild("TagName") })
	] }),
	languageData: {
		commentTokens: { block: {
			open: "<!--",
			close: "-->"
		} },
		indentOnInput: /^\s*<\/\w+\W$/,
		wordChars: "-_"
	}
}), Oy = /*@__PURE__*/ Dy.configure({ wrap: /*@__PURE__*/ Zg(Ty, Ey) });
function ky(e = {}) {
	let t = "", n;
	return e.matchClosingTags === !1 && (t = "noMatch"), e.selfClosingTags === !0 && (t = (t ? t + " " : "") + "selfClosing"), (e.nestedLanguages && e.nestedLanguages.length || e.nestedAttributes && e.nestedAttributes.length) && (n = Zg((e.nestedLanguages || []).concat(Ty), (e.nestedAttributes || []).concat(Ey))), new nl(n ? Dy.configure({
		wrap: n,
		dialect: t
	}) : t ? Oy.configure({ dialect: t }) : Oy, [
		Oy.data.of({ autocomplete: Cy(e) }),
		e.autoCloseTags === !1 ? [] : My,
		Zv().support,
		Y_().support
	]);
}
var Ay = /*@__PURE__*/ new Set(/*@__PURE__*/ "area base br col command embed frame hr img input keygen link meta param source track wbr menuitem".split(" "));
function jy(e, t, n) {
	for (;;) {
		if (t.lastChild?.name != "CloseTag") return !1;
		let r = t.parent;
		if (!r || dy(e, r) != n) return !0;
		t = r;
	}
}
var My = /*@__PURE__*/ V.inputHandler.of((e, t, n, r, i) => {
	if (e.composing || e.state.readOnly || t != n || r != ">" && r != "/" || !Oy.isActiveAt(e.state, t, -1)) return !1;
	let a = i(), { state: o } = a, s = o.changeByRange((e) => {
		let t = o.doc.sliceString(e.from - 1, e.to) == r, { head: n } = e, i = J(o).resolveInner(n, -1), a;
		if (t && r == ">" && i.name == "EndTag") {
			let t = i.parent;
			if ((a = dy(o.doc, t.parent, n)) && !Ay.has(a) && !jy(o.doc, t.parent, a)) return {
				range: e,
				changes: {
					from: n,
					to: n + +(o.doc.sliceString(n, n + 1) === ">"),
					insert: `</${a}>`
				}
			};
		} else if (t && r == "/" && i.name == "IncompleteCloseTag") {
			let e = i.parent;
			if (i.from == n - 2 && e.lastChild?.name != "CloseTag" && (a = dy(o.doc, e, n)) && !Ay.has(a)) {
				let e = n + +(o.doc.sliceString(n, n + 1) === ">"), t = `${a}>`;
				return {
					range: k.cursor(n + t.length, -1),
					changes: {
						from: n,
						to: e,
						insert: t
					}
				};
			}
		}
		return { range: e };
	});
	return !s.changes.empty && (e.dispatch([a, o.update(s, {
		userEvent: "input.complete",
		scrollIntoView: !0
	})]), !0);
}), Ny = /*@__PURE__*/ Vc({ commentTokens: { block: {
	open: "<!--",
	close: "-->"
} } }), Py = /*@__PURE__*/ new H(), Fy = /*@__PURE__*/ Um.configure({ props: [
	/*@__PURE__*/ wl.add((e) => !e.is("Block") || e.is("Document") || Iy(e) != null || Ly(e) ? void 0 : (e, t) => ({
		from: t.doc.lineAt(e.from).to,
		to: e.to
	})),
	/*@__PURE__*/ Py.add(Iy),
	/*@__PURE__*/ ul.add({ Document: () => null }),
	/*@__PURE__*/ Bc.add({ Document: Ny })
] });
function Iy(e) {
	let t = /^(?:ATX|Setext)Heading(\d)$/.exec(e.name);
	return t ? +t[1] : void 0;
}
function Ly(e) {
	return e.name == "OrderedList" || e.name == "BulletList";
}
function Ry(e, t) {
	let n = e;
	for (;;) {
		let e = n.nextSibling, r;
		if (!e || (r = Iy(e.type)) != null && r <= t) break;
		n = e;
	}
	return n.to;
}
var zy = /*@__PURE__*/ Cl.of((e, t, n) => {
	for (let r = J(e).resolveInner(n, -1); r && !(r.from < t); r = r.parent) {
		let e = r.type.prop(Py);
		if (e == null) continue;
		let t = Ry(r, e);
		if (t > n) return {
			from: n,
			to: t
		};
	}
	return null;
});
function By(e) {
	return new Uc(Ny, e, [], "markdown");
}
var Vy = /*@__PURE__*/ By(Fy), Hy = /*@__PURE__*/ By(/* @__PURE__ */ Fy.configure([
	lh,
	fh,
	dh,
	ph,
	{ props: [/*@__PURE__*/ wl.add({ Table: (e, t) => ({
		from: t.doc.lineAt(e.from).to,
		to: e.to
	}) })] }
]));
function Uy(e, t) {
	return (n) => {
		if (n && e) {
			let t = null;
			if (n = /\S*/.exec(n)[0], t = typeof e == "function" ? e(n) : rl.matchLanguageName(e, n, !0), t instanceof rl) return t.support ? t.support.language.parser : Yc.getSkippingParser(t.load());
			if (t) return t.parser;
		}
		return t ? t.parser : null;
	};
}
var Wy = class {
	constructor(e, t, n, r, i, a, o) {
		this.node = e, this.from = t, this.to = n, this.spaceBefore = r, this.spaceAfter = i, this.type = a, this.item = o;
	}
	blank(e, t = !0) {
		let n = this.spaceBefore + (this.node.name == "Blockquote" ? ">" : "");
		if (e != null) {
			for (; n.length < e;) n += " ";
			return n;
		}
		for (let e = this.to - this.from - n.length - this.spaceAfter.length; e > 0; e--) n += " ";
		return n + (t ? this.spaceAfter : "");
	}
	marker(e, t) {
		let n = this.node.name == "OrderedList" ? String(+Ky(this.item, e)[2] + t) : "";
		return this.spaceBefore + n + this.type + this.spaceAfter;
	}
};
function Gy(e, t) {
	let n = [], r = [];
	for (let t = e; t; t = t.parent) {
		if (t.name == "FencedCode") return r;
		(t.name == "ListItem" || t.name == "Blockquote") && n.push(t);
	}
	for (let e = n.length - 1; e >= 0; e--) {
		let i = n[e], a, o = t.lineAt(i.from), s = i.from - o.from;
		if (i.name == "Blockquote" && (a = /^ *>( ?)/.exec(o.text.slice(s)))) r.push(new Wy(i, s, s + a[0].length, "", a[1], ">", null));
		else if (i.name == "ListItem" && i.parent.name == "OrderedList" && (a = /^( *)\d+([.)])( *)/.exec(o.text.slice(s)))) {
			let e = a[3], t = a[0].length;
			e.length >= 4 && (e = e.slice(0, e.length - 4), t -= 4), r.push(new Wy(i.parent, s, s + t, a[1], e, a[2], i));
		} else if (i.name == "ListItem" && i.parent.name == "BulletList" && (a = /^( *)([-+*])( {1,4}\[[ xX]\])?( +)/.exec(o.text.slice(s)))) {
			let e = a[4], t = a[0].length;
			e.length > 4 && (e = e.slice(0, e.length - 4), t -= 4);
			let n = a[2];
			a[3] && (n += a[3].replace(/[xX]/, " ")), r.push(new Wy(i.parent, s, s + t, a[1], e, n, i));
		}
	}
	return r;
}
function Ky(e, t) {
	return /^(\s*)(\d+)(?=[.)])/.exec(t.sliceString(e.from, e.from + 10));
}
function qy(e, t, n, r = 0) {
	for (let i = -1, a = e;;) {
		if (a.name == "ListItem") {
			let e = Ky(a, t), o = +e[2];
			if (i >= 0) {
				if (o != i + 1) return;
				n.push({
					from: a.from + e[1].length,
					to: a.from + e[0].length,
					insert: String(i + 2 + r)
				});
			}
			i = o;
		}
		let e = a.nextSibling;
		if (!e) break;
		a = e;
	}
}
function Jy(e, t) {
	let n = /^[ \t]*/.exec(e)[0].length;
	if (!n || t.facet(al) != "	") return e;
	let r = Lt(e, 4, n), i = "";
	for (let e = r; e > 0;) e >= 4 ? (i += "	", e -= 4) : (i += " ", e--);
	return i + e.slice(n);
}
var Yy = /*@__PURE__*/ ((e = {}) => ({ state: t, dispatch: n }) => {
	let r = J(t), { doc: i } = t, a = null, o = t.changeByRange((n) => {
		if (!n.empty || !Hy.isActiveAt(t, n.from, -1) && !Hy.isActiveAt(t, n.from, 1)) return a = { range: n };
		let o = n.from, s = i.lineAt(o), c = Gy(r.resolveInner(o, -1), i);
		for (; c.length && c[c.length - 1].from > o - s.from;) c.pop();
		if (!c.length) return a = { range: n };
		let l = c[c.length - 1];
		if (l.to - l.spaceAfter.length > o - s.from) return a = { range: n };
		let u = o >= l.to - l.spaceAfter.length && !/\S/.test(s.text.slice(l.to));
		if (l.item && u) {
			if (l.item.from < s.from && !/^[\s>]*$/.test(s.text.slice(0, l.to))) return a = { range: n };
			let r = l.node.firstChild, u = l.node.getChild("ListItem", "ListItem");
			if (r.to >= o || u && u.to < o || s.from > 0 && !/[^\s>]/.test(i.lineAt(s.from - 1).text) || e.nonTightLists === !1) {
				let e = c.length > 1 ? c[c.length - 2] : null, t, n = "";
				e && e.item ? (t = s.from + e.from, n = e.marker(i, 1)) : t = s.from + (e ? e.to : 0);
				let r = [{
					from: t,
					to: o,
					insert: n
				}];
				return l.node.name == "OrderedList" && qy(l.item, i, r, -2), e && e.node.name == "OrderedList" && qy(e.item, i, r), {
					range: k.cursor(t + n.length),
					changes: r
				};
			}
			{
				let e = Qy(c, t, s);
				return {
					range: k.cursor(o + e.length + 1),
					changes: {
						from: s.from,
						insert: e + t.lineBreak
					}
				};
			}
		}
		if (l.node.name == "Blockquote" && u && s.from) {
			let e = i.lineAt(s.from - 1), r = />\s*$/.exec(e.text);
			if (r && r.index == l.from) {
				let i = t.changes([{
					from: e.from + r.index,
					to: e.to
				}, {
					from: s.from + l.from,
					to: s.to
				}]);
				return {
					range: n.map(i),
					changes: i
				};
			}
		}
		let d = [];
		l.node.name == "OrderedList" && qy(l.item, i, d);
		let f = l.item && l.item.from < s.from, p = "";
		if (!f || /^[\s\d.)\-+*>]*/.exec(s.text)[0].length >= l.to) for (let e = 0, t = c.length - 1; e <= t; e++) p += e == t && !f ? c[e].marker(i, 1) : c[e].blank(e < t ? Lt(s.text, 4, c[e + 1].from) - p.length : null);
		let m = o;
		for (; m > s.from && /\s/.test(s.text.charAt(m - s.from - 1));) m--;
		return p = Jy(p, t), Zy(l.node, t.doc) && (p = Qy(c, t, s) + t.lineBreak + p), d.push({
			from: m,
			to: o,
			insert: t.lineBreak + p
		}), {
			range: k.cursor(m + p.length + 1),
			changes: d
		};
	});
	return !a && (n(t.update(o, {
		scrollIntoView: !0,
		userEvent: "input"
	})), !0);
})();
function Xy(e) {
	return e.name == "QuoteMark" || e.name == "ListMark";
}
function Zy(e, t) {
	if (e.name != "OrderedList" && e.name != "BulletList") return !1;
	let n = e.firstChild, r = e.getChild("ListItem", "ListItem");
	if (!r) return !1;
	let i = t.lineAt(n.to), a = t.lineAt(r.from), o = /^[\s>]*$/.test(i.text);
	return i.number + +!o < a.number;
}
function Qy(e, t, n) {
	let r = "";
	for (let t = 0, i = e.length - 2; t <= i; t++) r += e[t].blank(t < i ? Lt(n.text, 4, e[t + 1].from) - r.length : null, t < i);
	return Jy(r, t);
}
function $y(e, t) {
	let n = e.resolveInner(t, -1), r = t;
	Xy(n) && (r = n.from, n = n.parent);
	for (let e; e = n.childBefore(r);) if (Xy(e)) r = e.from;
	else if (e.name == "OrderedList" || e.name == "BulletList") n = e.lastChild, r = n.to;
	else break;
	return n;
}
var eb = [{
	key: "Enter",
	run: Yy
}, {
	key: "Backspace",
	run: ({ state: e, dispatch: t }) => {
		let n = J(e), r = null, i = e.changeByRange((t) => {
			let i = t.from, { doc: a } = e;
			if (t.empty && Hy.isActiveAt(e, t.from)) {
				let t = a.lineAt(i), r = Gy($y(n, i), a);
				if (r.length) {
					let n = r[r.length - 1], a = n.to - n.spaceAfter.length + +!!n.spaceAfter;
					if (i - t.from > a && !/\S/.test(t.text.slice(a, i - t.from))) return {
						range: k.cursor(t.from + a),
						changes: {
							from: t.from + a,
							to: i
						}
					};
					if (i - t.from == a && (n.item && t.from <= n.item.from || /^[\s>]*$/.test(t.text.slice(0, n.to)))) {
						let r = t.from + n.from;
						if (n.item && n.node.from < n.item.from && /\S/.test(t.text.slice(n.from, n.to))) {
							let i = n.blank(Lt(t.text, 4, n.to) - Lt(t.text, 4, n.from));
							return r == t.from && (i = Jy(i, e)), {
								range: k.cursor(r + i.length),
								changes: {
									from: r,
									to: t.from + n.to,
									insert: i
								}
							};
						}
						if (r < i) return {
							range: k.cursor(r),
							changes: {
								from: r,
								to: i
							}
						};
					}
				}
			}
			return r = { range: t };
		});
		return !r && (t(e.update(i, {
			scrollIntoView: !0,
			userEvent: "delete"
		})), !0);
	}
}], tb = /*@__PURE__*/ ky({ matchClosingTags: !1 });
function nb(e = {}) {
	let { codeLanguages: t, defaultCodeLanguage: n, addKeymap: r = !0, base: { parser: i } = Vy, completeHTMLTags: a = !0, pasteURLAsLink: o = !0, htmlTagLanguage: s = tb } = e;
	if (!(i instanceof gm)) throw RangeError("Base parser provided to `markdown` should be a Markdown parser");
	let c = e.extensions ? [e.extensions] : [], l = [s.support, zy], u;
	o && l.push(sb), n instanceof nl ? (l.push(n.support), u = n.language) : n && (u = n);
	let d = t || u ? Uy(t, u) : void 0;
	c.push(Gm({
		codeParser: d,
		htmlParser: s.language.parser
	})), r && l.push(Be.high(ts.of(eb)));
	let f = By(i.configure(c));
	return a && l.push(f.data.of({ autocomplete: rb })), new nl(f, l);
}
function rb(e) {
	let { state: t, pos: n } = e, r = /<[:\-\.\w\u00b7-\uffff]*$/.exec(t.sliceDoc(n - 25, n));
	if (!r) return null;
	let i = J(t).resolveInner(n, -1);
	for (; i && !i.type.isTop;) {
		if (i.name == "CodeBlock" || i.name == "FencedCode" || i.name == "ProcessingInstructionBlock" || i.name == "CommentBlock" || i.name == "Link" || i.name == "Image") return null;
		i = i.parent;
	}
	return {
		from: n - r[0].length,
		to: n,
		options: ab(),
		validFor: /^<[:\-\.\w\u00b7-\uffff]*$/
	};
}
var ib = null;
function ab() {
	if (ib) return ib;
	let e = Sy(new yf(M.create({ extensions: tb }), 0, !0));
	return ib = e ? e.options : [];
}
var ob = /code|horizontalrule|html|link|comment|processing|escape|entity|image|mark|url/i, sb = /*@__PURE__*/ V.domEventHandlers({ paste: (e, t) => {
	let { main: n } = t.state.selection;
	if (n.empty) return !1;
	let r = e.clipboardData?.getData("text/plain");
	if (!r || !/^(https?:\/\/|mailto:|xmpp:|www\.)/.test(r) || (/^www\./.test(r) && (r = "https://" + r), !Hy.isActiveAt(t.state, n.from, 1))) return !1;
	let i = J(t.state), a = !1;
	return i.iterate({
		from: n.from,
		to: n.to,
		enter: (e) => {
			(e.from > n.from || ob.test(e.name)) && (a = !0);
		},
		leave: (e) => {
			e.to < n.to && (a = !0);
		}
	}), !a && (t.dispatch({
		changes: [{
			from: n.from,
			insert: "["
		}, {
			from: n.to,
			insert: `](${r})`
		}],
		userEvent: "input.paste",
		scrollIntoView: !0
	}), !0);
} }), cb = class {
	constructor() {
		this.latest = 0;
	}
	next() {
		return this.latest += 1, this.latest;
	}
	isCurrent(e) {
		return e === this.latest;
	}
}, lb = class {
	constructor(e) {
		this.sequencer = new cb(), this.timer = null, this.renderUrl = e.renderUrl, this.panel = e.panel, this.debounceMs = e.debounceMs ?? 300, this.getHljs = e.getHljs ?? (() => typeof window < "u" ? window.hljs : void 0), this.fetchImpl = e.fetchImpl ?? ((e, t) => fetch(e, t));
	}
	scheduleRender(e) {
		this.renderUrl && (this.timer !== null && clearTimeout(this.timer), this.timer = setTimeout(() => {
			this.timer = null, this.render(e);
		}, this.debounceMs));
	}
	dispose() {
		this.timer !== null && (clearTimeout(this.timer), this.timer = null);
	}
	async render(e) {
		let t = this.renderUrl;
		if (!t) return;
		let n = this.sequencer.next(), r;
		try {
			let n = await this.fetchImpl(t, {
				method: "POST",
				headers: { "Content-Type": "application/json; charset=utf-8" },
				body: JSON.stringify({
					body: e,
					breaks: !0
				})
			});
			if (!n.ok) return;
			r = await n.text();
		} catch {
			return;
		}
		if (!this.sequencer.isCurrent(n)) return;
		this.panel.innerHTML = r;
		let i = this.getHljs();
		i && this.panel.querySelectorAll("pre code").forEach((e) => i.highlightElement(e));
	}
}, ub = [
	{
		name: "+1",
		content: "👍"
	},
	{
		name: "heart",
		content: "❤️️"
	},
	{
		name: "wink",
		content: "😘"
	},
	{
		name: "smile",
		content: "🙂"
	},
	{
		name: "confused",
		content: "😕"
	},
	{
		name: "check",
		content: "✅"
	},
	{
		name: "hooray",
		content: "🎉"
	},
	{
		name: "sad",
		content: "😢"
	},
	{
		name: "-1",
		content: "👎"
	},
	{
		name: "tada",
		content: "🎉"
	},
	{
		name: "x",
		content: "❌"
	},
	{
		name: "o",
		content: "⭕"
	},
	{
		name: "face smile",
		content: "😄"
	},
	{
		name: "face smile kiss",
		content: "😙"
	},
	{
		name: "face kissing",
		content: "😗"
	},
	{
		name: "face astonished",
		content: "😲"
	},
	{
		name: "face angry",
		content: "😠"
	},
	{
		name: "face scream",
		content: "😱"
	},
	{
		name: "face cry",
		content: "😢"
	},
	{
		name: "face neutral",
		content: "😐"
	},
	{
		name: "face heart",
		content: "😍"
	},
	{
		name: "question?",
		content: "❓"
	},
	{
		name: "!",
		content: "❗️"
	},
	{
		name: "bangbang!",
		content: "‼️"
	},
	{
		name: "beer",
		content: "🍺"
	},
	{
		name: "icecream",
		content: "🍦"
	},
	{
		name: "korea",
		content: "🇰🇷"
	},
	{
		name: "us america",
		content: "🇺🇸"
	},
	{
		name: "fr",
		content: "🇫🇷"
	},
	{
		name: "cn china",
		content: "🇨🇳"
	},
	{
		name: "+100",
		content: "💯"
	},
	{
		name: "heavy check",
		content: "✔️"
	},
	{
		name: "+plus",
		content: "➕"
	},
	{
		name: "-minus",
		content: "➖️"
	},
	{
		name: "cactus",
		content: "🌵️"
	},
	{
		name: "animal cat",
		content: "🐈"
	},
	{
		name: "clover",
		content: "🍀"
	},
	{
		name: "v️",
		content: "✌️"
	},
	{
		name: "lock",
		content: "🔒"
	},
	{
		name: "unlock",
		content: "🔓"
	},
	{
		name: "idea bulb",
		content: "💡"
	},
	{
		name: "bomb",
		content: "💣"
	},
	{
		name: "calendar",
		content: "📆"
	},
	{
		name: "date",
		content: "📅"
	},
	{
		name: "chicken",
		content: "🐔"
	},
	{
		name: "mushroom",
		content: "🍄"
	},
	{
		name: "moneybag",
		content: "💰"
	},
	{
		name: "money dollar",
		content: "💵"
	},
	{
		name: "envelope",
		content: "✉️"
	},
	{
		name: "chart upward",
		content: "📈"
	},
	{
		name: "chart downward",
		content: "📉"
	},
	{
		name: "택배 parcel",
		content: "📦"
	},
	{
		name: "박수 clap",
		content: "👏"
	},
	{
		name: "game joker",
		content: "🃏"
	},
	{
		name: "game cards",
		content: "🎴"
	},
	{
		name: "game die",
		content: "🎲"
	},
	{
		name: "tea",
		content: "🍵"
	},
	{
		name: "coffee",
		content: "☕"
	},
	{
		name: "crystal",
		content: "🔮"
	},
	{
		name: "taxi",
		content: "🚕"
	},
	{
		name: "bus",
		content: "🚌"
	},
	{
		name: "train",
		content: "🚋"
	},
	{
		name: "warn",
		content: "⚠️"
	},
	{
		name: "star",
		content: "⭐"
	},
	{
		name: "phone",
		content: "☎️"
	}
];
function db(e, t) {
	if (!t) return e;
	let n = "<li>" + e + "</li>", r = RegExp(">\\s*([^<]*?)(" + t.replace("+", "\\+") + ")([^<]*)\\s*<", "ig"), i = n.replace(r, (e, t, n, r) => "> " + t + "<strong>" + n + "</strong>" + r + " <");
	return i.slice(4, i.length - 5);
}
function fb(e, t) {
	if (!e) return t.slice(0, 10);
	let n = [];
	for (let r of t) {
		let t = String(r.searchText).toLowerCase().indexOf(e.toLowerCase());
		t > -1 && n.push({
			item: r,
			order: t
		});
	}
	return n.sort((e, t) => e.order - t.order), n.slice(0, 10).map((e) => e.item);
}
function pb(e) {
	if (!e) return ub.slice(0, 10);
	let t = e.toLowerCase(), n = [];
	for (let e of ub) {
		let r = e.name.toLowerCase().indexOf(t);
		r > -1 && n.push({
			item: e,
			order: r
		});
	}
	return n.sort((e, t) => e.order - t.order), n.slice(0, 10).map((e) => e.item);
}
function mb(e, t) {
	if (!e) return t.slice();
	let n = t.map((t, n) => {
		let r;
		if (t.issueNo === e) r = 0;
		else {
			let i = t.issueNo.toLowerCase().indexOf(e.toLowerCase());
			r = n + 1 + 10 ** i + (i > -1 ? 0 : 100 ** t.title.toLowerCase().indexOf(e.toLowerCase()));
		}
		return {
			item: t,
			order: r
		};
	});
	return n.sort((e, t) => e.order - t.order), n.map((e) => e.item);
}
function hb(e) {
	return e.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
function gb(e, t) {
	let n = RegExp(hb(t) + "([^\\s]*)$").exec(e);
	if (!n) return null;
	let r = n.index, i = r > 0 ? e[r - 1] : void 0;
	if (i !== void 0 && !/\s/.test(i)) return null;
	let a = n[1] ?? "";
	return a.length > 20 ? null : {
		from: r,
		query: a
	};
}
function _b(e) {
	return typeof e.yonaHtml == "string";
}
function vb(e) {
	return {
		fetchImpl: e.fetchImpl ?? ((e, t) => fetch(e, t)),
		getProgress: e.getProgress ?? (() => typeof window < "u" ? window.NProgress : void 0)
	};
}
async function yb(e, t, n) {
	let r = n.getProgress();
	r?.start();
	try {
		let r = new URLSearchParams({
			query: t,
			mentionType: "user"
		}), i = await n.fetchImpl(`${e}?${r.toString()}`);
		return i.ok ? fb(t, (await i.json()).result ?? []) : [];
	} catch {
		return [];
	} finally {
		r?.done();
	}
}
async function bb(e, t, n) {
	let r = n.getProgress();
	r?.start();
	try {
		let r = new URLSearchParams({
			query: t,
			mentionType: "issue"
		}), i = await n.fetchImpl(`${e}?${r.toString()}`);
		return i.ok ? mb(t, (await i.json()).result ?? []).slice(0, 10) : [];
	} catch {
		return [];
	} finally {
		r?.done();
	}
}
function xb(e) {
	return e.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
function Sb(e, t) {
	let n = `<img style='width:20px;height:20px;' src='${xb(e.image)}'> ${xb(e.name)} <small>${xb(e.loginid)}</small>`;
	return {
		label: e.loginid,
		apply: `@${e.loginid} `,
		yonaHtml: db(n, t)
	};
}
function Cb(e, t) {
	let n = `${xb(e.content)} <small>${xb(e.name)}</small>`;
	return {
		label: e.name,
		apply: `${e.content} `,
		yonaHtml: db(n, t)
	};
}
function wb(e, t) {
	let n = `<small>#${xb(e.issueNo)}</small> ${xb(e.title)}`;
	return {
		label: e.issueNo,
		apply: `#${e.issueNo} `,
		yonaHtml: db(n, t)
	};
}
function Tb(e, t) {
	return (n) => {
		let r = n.state.doc.lineAt(n.pos), i = gb(r.text.slice(0, n.pos - r.from), e);
		return i ? t(i.query, r.from + i.from, n) : null;
	};
}
function Eb() {
	return Tb(":", (e, t) => ({
		from: t,
		options: pb(e).map((t) => Cb(t, e)),
		filter: !1
	}));
}
function Db(e, t) {
	return Tb("@", (n, r, i) => {
		let a = e();
		return a ? new Promise((e) => {
			let o = !1, s = setTimeout(() => {
				o || yb(a, n, t).then((t) => {
					o || e({
						from: r,
						options: t.map((e) => Sb(e, n)),
						filter: !1
					});
				});
			}, 300);
			i.addEventListener("abort", () => {
				o = !0, clearTimeout(s), e(null);
			}, { onDocChange: !0 });
		}) : null;
	});
}
function Ob(e, t) {
	return Tb("#", async (n, r) => {
		let i = e();
		return i ? {
			from: r,
			options: (await bb(i, n, t)).map((e) => wb(e, n)),
			filter: !1
		} : null;
	});
}
function kb(e) {
	let t = vb(e);
	return Fp({
		override: [
			Db(e.getMentionUrl, t),
			Eb(),
			Ob(e.getMentionUrl, t)
		],
		activateOnTypingDelay: 0,
		icons: !1,
		addToOptions: [{
			render: (e) => {
				if (!_b(e)) return null;
				let t = document.createElement("span");
				return t.className = "yona-mention-option", t.innerHTML = e.yonaHtml, t;
			},
			position: 30
		}]
	});
}
//#endregion
//#region src/editor/commands.ts
function Ab(e) {
	let t = e.selection.main;
	return {
		from: t.from,
		to: t.to
	};
}
var jb = {
	bold: "StrongEmphasis",
	italic: "Emphasis"
}, Mb = {
	bold: "**",
	italic: "*"
};
function Nb(e, t, n) {
	let r = (Kc(e, t, 1e3) ?? J(e)).resolveInner(t, 1);
	for (; r; r = r.parent) if (r.type.name === n) return {
		from: r.from,
		to: r.to
	};
	return null;
}
function Pb(e, t, n) {
	let r = t.from + n, i = t.to - n;
	return Math.min(Math.max(e, r), i) - n;
}
function Fb(e, t) {
	let { from: n, to: r } = Ab(e), i = Mb[t], a = i.length, o = Nb(e, n, jb[t]);
	if (o) {
		let t = e.doc.sliceString(o.from + a, o.to - a);
		return {
			changes: {
				from: o.from,
				to: o.to,
				insert: t
			},
			selection: {
				anchor: Pb(n, o, a),
				head: Pb(r, o, a)
			}
		};
	}
	let s = e.sliceDoc(n, r);
	s = t === "bold" ? s.split("**").join("").split("__").join("") : s.split("*").join("").split("_").join("");
	let c = n + a;
	return {
		changes: {
			from: n,
			to: r,
			insert: i + s + i
		},
		selection: {
			anchor: c,
			head: c + s.length
		}
	};
}
var Ib = (e) => Fb(e, "bold"), Lb = (e) => Fb(e, "italic");
function Rb(e) {
	let { from: t, to: n } = Ab(e), r = e.doc.lineAt(t).number, i = e.doc.lineAt(n).number, a = [];
	for (let t = r; t <= i; t++) {
		let n = e.doc.line(t), r = n.text, i = r.match(/[^#]/), o = i ? i.index ?? -1 : -1, s;
		s = o <= 0 ? "# " + r : o === 6 ? r.slice(7) : "#" + r, a.push({
			from: n.from,
			to: n.to,
			insert: s
		});
	}
	return { changes: a };
}
var zb = /^(\s*)>(\s+)/;
function Bb(e) {
	let { from: t, to: n } = Ab(e), r = e.doc.lineAt(t).number, i = e.doc.lineAt(n).number, a = zb.test(e.doc.line(r).text), o = [];
	for (let t = r; t <= i; t++) {
		let n = e.doc.line(t), r = a ? n.text.replace(zb, "$1") : "> " + n.text;
		o.push({
			from: n.from,
			to: n.to,
			insert: r
		});
	}
	return { changes: o };
}
var Vb = {
	checklist: /^(\s*)- \[[ xX]\](\s+)/,
	"unordered-list": /^(\s*)[*\-+](\s+)/,
	"ordered-list": /^(\s*)\d+\.(\s+)/
};
function Hb(e) {
	if (Vb.checklist.test(e)) return "checklist";
	if (Vb["ordered-list"].test(e)) return "ordered-list";
	if (Vb["unordered-list"].test(e)) return "unordered-list";
}
function Ub(e, t, n) {
	return e === "checklist" ? "- [ ] " + t : e === "unordered-list" ? "* " + t : n + ". " + t;
}
function Wb(e, t) {
	let { from: n, to: r } = Ab(e), i = e.doc.lineAt(n).number, a = e.doc.lineAt(r).number, o = Hb(e.doc.line(i).text), s = o === t, c = s ? void 0 : o, l = [], u = 1;
	for (let n = i; n <= a; n++) {
		let r = e.doc.line(n), i = r.text;
		s ? i = i.replace(Vb[t], "$1") : (c && (i = i.replace(Vb[c], "$1")), i = Ub(t, i, u), u += 1), l.push({
			from: r.from,
			to: r.to,
			insert: i
		});
	}
	return { changes: l };
}
var Gb = (e) => Wb(e, "checklist"), Kb = (e) => Wb(e, "unordered-list"), qb = (e) => Wb(e, "ordered-list"), Jb = {
	link: "[",
	image: "!["
};
function Yb(e, t) {
	let { from: n, to: r } = Ab(e), i = Jb[t], a = e.sliceDoc(n, r), o = n + i.length;
	return {
		changes: {
			from: n,
			to: r,
			insert: i + a + "](https://)"
		},
		selection: {
			anchor: o,
			head: o + a.length
		}
	};
}
var Xb = (e) => Yb(e, "link"), Zb = (e) => Yb(e, "image"), Qb = {
	bold: "",
	italic: "",
	quote: "",
	checklist: "",
	unorderedList: "",
	link: "",
	image: "",
	preview: ""
};
function $b() {
	return [
		{
			command: "bold",
			title: "Bold",
			iconChar: Qb.bold,
			run: Ib
		},
		{
			command: "italic",
			title: "Italic",
			iconChar: Qb.italic,
			run: Lb
		},
		"separator",
		{
			command: "heading",
			title: "Heading",
			text: "H",
			run: Rb
		},
		{
			command: "quote",
			title: "Quote",
			iconChar: Qb.quote,
			run: Bb
		},
		"separator",
		{
			command: "checklist",
			title: "Checklist",
			iconChar: Qb.checklist,
			run: Gb
		},
		{
			command: "unordered-list",
			title: "Generic list",
			iconChar: Qb.unorderedList,
			run: Kb
		},
		{
			command: "ordered-list",
			title: "Numbered list",
			text: "1.",
			run: qb
		},
		"separator",
		{
			command: "link",
			title: "Create link",
			iconChar: Qb.link,
			run: Xb
		},
		{
			command: "image",
			title: "Insert image",
			iconChar: Qb.image,
			run: Zb
		},
		"separator",
		{
			command: "preview",
			title: "Toggle preview",
			iconChar: Qb.preview
		}
	];
}
//#endregion
//#region src/editor/YonaMarkdownEditor.vue?vue&type=script&setup=true&lang.ts
var ex = ["name", "data-editor-mode"], tx = { class: "toolbar" }, nx = {
	key: 0,
	class: "separator",
	"aria-hidden": "true"
}, rx = [
	"data-command",
	"data-icon",
	"title",
	"aria-label",
	"aria-pressed",
	"onClick"
], ix = m(/* @__PURE__ */ _(/* @__PURE__ */ l({
	__name: "YonaMarkdownEditor",
	props: {
		name: {
			default: "",
			type: String
		},
		editorMode: {
			default: "",
			type: String
		},
		modelValue: {
			default: "",
			type: String
		},
		renderUrl: {
			default: null,
			type: [String, null]
		},
		mentionUrl: {
			default: null,
			type: [String, null]
		}
	},
	emits: ["update:modelValue"],
	setup(l, { expose: m, emit: _ }) {
		let v = l, y = _, ee = `editor-${n()}`, b = $b(), x = i(null), te = i(null), ne = i(null), re = i(null), S = i(!1), C = null, ie = null, w = null, T = null, E = v.modelValue;
		function ae(e) {
			let t = te.value;
			t && (t.value = e, t.dispatchEvent(new Event("input", {
				bubbles: !0,
				composed: !0
			})), t.dispatchEvent(new KeyboardEvent("keyup", {
				bubbles: !0,
				composed: !0
			})));
		}
		u(() => {
			let e = te.value, t = ne.value, n = re.value;
			if (!e || !t || !n) return;
			e.value = E, e.defaultValue = E, ie = new lb({
				renderUrl: v.renderUrl,
				panel: n
			});
			let r = [
				du(),
				ts.of([...vf, ...Au]),
				nb(),
				Al(Nl, { fallback: !0 }),
				V.lineWrapping,
				V.theme({ "&": {
					fontFamily: "var(--yona-md-font-family, Consolas, Menlo, Monaco, monospace)",
					fontSize: "var(--yona-md-font-size, 13px)"
				} }),
				V.updateListener.of((e) => {
					if (!e.docChanged) return;
					let t = e.state.doc.toString();
					ae(t), y("update:modelValue", t), S.value && ie?.scheduleRender(t);
				})
			];
			if (v.mentionUrl) {
				let e = v.mentionUrl;
				r.push(kb({ getMentionUrl: () => e }));
			}
			C = new V({
				state: M.create({
					doc: E,
					extensions: r
				}),
				parent: t
			}), w = x.value?.closest("form") ?? null, w && (T = () => {
				C && C.dispatch({ changes: {
					from: 0,
					to: C.state.doc.length,
					insert: E
				} });
			}, w.addEventListener("reset", T));
		}), g(() => {
			ie?.dispose(), C?.destroy(), C = null, w && T && w.removeEventListener("reset", T), w = null, T = null;
		}), o(() => v.modelValue, (e) => {
			C && e !== C.state.doc.toString() && C.dispatch({ changes: {
				from: 0,
				to: C.state.doc.length,
				insert: e
			} });
		});
		function oe(e) {
			if (!C || !e.run) return;
			let t = e.run(C.state);
			t && C.dispatch(t), C.focus();
		}
		function se(e) {
			if (e.run) {
				oe(e);
				return;
			}
			S.value = !S.value, S.value && C && ie?.scheduleRender(C.state.doc.toString()), C?.focus();
		}
		function ce(e) {
			return [e.text === void 0 ? `icon icon-${e.command}` : "text-icon", { "is-active": e.command === "preview" && S.value }];
		}
		function le() {
			return C ? C.state.doc.toString() : "";
		}
		function ue(e) {
			C && C.dispatch({ changes: {
				from: 0,
				to: C.state.doc.length,
				insert: e
			} });
		}
		return m({
			getValue: le,
			setValue: ue
		}), (n, i) => (c(), p("div", {
			ref_key: "rootRef",
			ref: x,
			class: "yona-markdown-editor-vue"
		}, [
			f("textarea", {
				ref_key: "textareaRef",
				ref: te,
				name: l.name,
				id: ee,
				"data-editor-mode": l.editorMode,
				markdown: "true",
				style: { display: "none" }
			}, null, 8, ex),
			f("div", tx, [(c(!0), p(d, null, t(a(b), (e, t) => (c(), p(d, { key: t }, [e === "separator" ? (c(), p("span", nx)) : (c(), p("button", {
				key: 1,
				type: "button",
				class: s(["toolbar-button", ce(e)]),
				"data-command": e.command,
				"data-icon": e.iconChar,
				title: e.title,
				"aria-label": e.title,
				"aria-pressed": e.command === "preview" ? S.value : void 0,
				onClick: (t) => se(e)
			}, r(e.text), 11, rx))], 64))), 128))]),
			e(f("div", {
				ref_key: "editorWrapperRef",
				ref: ne,
				class: "editor-wrapper"
			}, null, 512), [[h, !S.value]]),
			e(f("div", {
				ref_key: "previewPanelRef",
				ref: re,
				class: "preview-wrap markdown-wrap"
			}, null, 512), [[h, S.value]])
		], 512));
	}
}), [["styles", [".yona-markdown-editor-vue[data-v-dedf8343]{--yona-md-toolbar-bg:#fafafa;--yona-md-border-color:#00000026;--yona-md-radius:4px;--yona-md-button-radius:3px;--yona-md-button-bg:#fff;--yona-md-button-color:#333;--yona-md-accent-color:#51aacc;--yona-md-accent-text-color:#fff;--yona-md-disabled-opacity:.35;--yona-md-font-family:Consolas, Menlo, Monaco, monospace;--yona-md-font-size:13px;--yona-md-icon-font-family:\"yobicon\";--yona-md-min-height:300px;display:block}.toolbar[data-v-dedf8343]{box-sizing:border-box;background-color:var(--yona-md-toolbar-bg);border:1px solid var(--yona-md-border-color);border-radius:var(--yona-md-radius) var(--yona-md-radius) 0 0;border-bottom:none;flex-wrap:wrap;align-items:center;margin-bottom:4px;padding:6px 8px;display:flex}.toolbar-button[data-v-dedf8343]{box-sizing:border-box;min-width:30px;height:26px;color:var(--yona-md-button-color);background-color:var(--yona-md-button-bg);border:1px solid var(--yona-md-border-color);border-radius:var(--yona-md-button-radius);cursor:pointer;text-align:center;white-space:nowrap;vertical-align:middle;justify-content:center;align-items:center;margin:0 2px 0 0;padding:0 8px;font-size:14px;line-height:24px;transition:all .3s;display:inline-flex;box-shadow:0 1px #0000000d}.toolbar-button[data-v-dedf8343]:hover,.toolbar-button.is-active[data-v-dedf8343]{background-color:var(--yona-md-accent-color);border-color:var(--yona-md-accent-color);color:var(--yona-md-accent-text-color)}.toolbar-button.icon[data-v-dedf8343]:before{font-family:var(--yona-md-icon-font-family);vertical-align:middle;content:attr(data-icon);font-style:normal;font-weight:400}.toolbar-button.text-icon[data-v-dedf8343]{font-family:Arial,sans-serif;font-weight:700}.separator[data-v-dedf8343]{border-left:1px solid var(--yona-md-border-color);align-self:stretch;width:0;min-height:18px;margin:0 6px;display:inline-block}.editor-wrapper[data-v-dedf8343]{box-sizing:border-box;border:1px solid var(--yona-md-border-color);border-radius:0 0 var(--yona-md-radius) var(--yona-md-radius)}.editor-wrapper[data-v-dedf8343]:focus-within{border-color:var(--yona-md-accent-color)}.editor-wrapper[data-v-dedf8343] .cm-editor{min-height:var(--yona-md-min-height)}.preview-wrap[data-v-dedf8343]{box-sizing:border-box;min-height:var(--yona-md-min-height);border:1px solid var(--yona-md-border-color);border-radius:0 0 var(--yona-md-radius) var(--yona-md-radius);-webkit-font-smoothing:antialiased;-webkit-text-size-adjust:100%;font-feature-settings:\"kern\" 1;font-kerning:normal;word-wrap:break-word;padding:10px;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif,Apple Color Emoji,Segoe UI Emoji,Segoe UI Symbol;font-size:1.1em;overflow:auto}.preview-wrap[data-v-dedf8343]>:first-child{margin-top:0!important}.preview-wrap[data-v-dedf8343]>:last-child{margin-bottom:0!important}.preview-wrap[data-v-dedf8343] ul,.preview-wrap[data-v-dedf8343] ol{margin-left:0;padding:0 0 5px 2.5em;font-weight:400}.preview-wrap[data-v-dedf8343] li{margin-bottom:5px;line-height:1.6em}.preview-wrap[data-v-dedf8343] li>ul{margin-bottom:0;padding:5px 0 0 2.5em}.preview-wrap[data-v-dedf8343] li>ul :last-of-type{padding-bottom:0}.preview-wrap[data-v-dedf8343] li>ul pre{padding-bottom:10px!important}.preview-wrap[data-v-dedf8343] li>p{margin-top:8px;margin-bottom:2px}.preview-wrap[data-v-dedf8343] a{color:#4183c4;text-decoration:none}.preview-wrap[data-v-dedf8343] a:hover{color:#4183c4;text-decoration:underline}.preview-wrap[data-v-dedf8343] a:hover span{text-decoration:none}.preview-wrap[data-v-dedf8343] a:active{color:#4183c4;text-decoration:none}.preview-wrap[data-v-dedf8343] h1,.preview-wrap[data-v-dedf8343] h2,.preview-wrap[data-v-dedf8343] h3{margin-bottom:16px;line-height:40px}.preview-wrap[data-v-dedf8343] h1{border-bottom:1px solid #eee;width:95%;padding-bottom:.3em;font-size:2em;font-weight:600}.preview-wrap[data-v-dedf8343] h1 .head-anchor,.preview-wrap[data-v-dedf8343] h2 .head-anchor,.preview-wrap[data-v-dedf8343] h3 .head-anchor,.preview-wrap[data-v-dedf8343] h4 .head-anchor,.preview-wrap[data-v-dedf8343] h5 .head-anchor{opacity:0;margin-left:3px}.preview-wrap[data-v-dedf8343] h1:hover .head-anchor,.preview-wrap[data-v-dedf8343] h2:hover .head-anchor,.preview-wrap[data-v-dedf8343] h3:hover .head-anchor,.preview-wrap[data-v-dedf8343] h4:hover .head-anchor,.preview-wrap[data-v-dedf8343] h5:hover .head-anchor{opacity:1}.preview-wrap[data-v-dedf8343] h2{border-bottom:1px solid #eaecef;width:95%;padding:0 0 .3em;font-size:1.5em;line-height:1.25}.preview-wrap[data-v-dedf8343] h3{margin:1em 0 5px;padding:0;font-size:1.25em}.preview-wrap[data-v-dedf8343] h4{margin-top:1.2em;padding:0;font-size:1.25em}.preview-wrap[data-v-dedf8343] h5{margin-top:20px;font-size:1em}.preview-wrap[data-v-dedf8343] hr{color:#ccc;background-color:#ccc;border:0;height:1px;margin:10px 0}.preview-wrap[data-v-dedf8343] p{margin:0 0 12px;line-height:1.6em}.preview-wrap[data-v-dedf8343] blockquote p{font-size:.9em;font-weight:400}.preview-wrap[data-v-dedf8343] code{border:1px solid #ddd;border-radius:3px;padding:5px 5px 2px;font-family:Consolas,Menlo,Monaco,Ubuntu Mono,source-code-pro,monospace;font-size:13px}.preview-wrap[data-v-dedf8343] code .title{font-size:inherit}.preview-wrap[data-v-dedf8343] blockquote{color:#777;border-left:4px solid #ddd;padding:0 15px}.preview-wrap[data-v-dedf8343] li>img{max-width:80%}.preview-wrap[data-v-dedf8343] p>input[type=checkbox]{vertical-align:text-top}.preview-wrap[data-v-dedf8343] li>input[type=checkbox]{vertical-align:top}.preview-wrap[data-v-dedf8343] img{box-sizing:border-box;border:1px solid #0000001a;max-width:100%;max-height:600px;margin:10px 0;padding:5px}.preview-wrap[data-v-dedf8343]>ul{margin-bottom:16px;line-height:20px;list-style:outside}.preview-wrap[data-v-dedf8343] ul ul,.preview-wrap[data-v-dedf8343] ol ul{list-style:circle}.preview-wrap[data-v-dedf8343] ul ul ul,.preview-wrap[data-v-dedf8343] ol ul ul,.preview-wrap[data-v-dedf8343] ol ol ul,.preview-wrap[data-v-dedf8343] ul ol ul{list-style:square}.preview-wrap[data-v-dedf8343] ol{line-height:1.6em;list-style:decimal}.preview-wrap[data-v-dedf8343] pre{word-break:normal;background-color:#efefef;border:none;margin:10px 0;padding:10px;font-size:1em}.preview-wrap[data-v-dedf8343] pre code{border:none;margin:0;padding:0}.preview-wrap[data-v-dedf8343] table{border-collapse:collapse;margin:15px}.preview-wrap[data-v-dedf8343] table th{background-color:#f7f7f7;border:1px solid #dcddde;min-width:45px;padding:5px}.preview-wrap[data-v-dedf8343] table td{word-break:break-all;border:1px solid #dcddde;padding:5px}.preview-wrap[data-v-dedf8343] .hljs{background:#f0f0f0;padding:.5em;display:block;overflow-x:auto}.preview-wrap[data-v-dedf8343] .hljs,.preview-wrap[data-v-dedf8343] .hljs-subst{color:#444}.preview-wrap[data-v-dedf8343] .hljs-comment{color:#888}.preview-wrap[data-v-dedf8343] .hljs-keyword,.preview-wrap[data-v-dedf8343] .hljs-attribute,.preview-wrap[data-v-dedf8343] .hljs-selector-tag,.preview-wrap[data-v-dedf8343] .hljs-meta-keyword,.preview-wrap[data-v-dedf8343] .hljs-doctag,.preview-wrap[data-v-dedf8343] .hljs-name{font-weight:700}.preview-wrap[data-v-dedf8343] .hljs-type,.preview-wrap[data-v-dedf8343] .hljs-string,.preview-wrap[data-v-dedf8343] .hljs-number,.preview-wrap[data-v-dedf8343] .hljs-selector-id,.preview-wrap[data-v-dedf8343] .hljs-selector-class,.preview-wrap[data-v-dedf8343] .hljs-quote,.preview-wrap[data-v-dedf8343] .hljs-template-tag,.preview-wrap[data-v-dedf8343] .hljs-deletion{color:#800}.preview-wrap[data-v-dedf8343] .hljs-title,.preview-wrap[data-v-dedf8343] .hljs-section{color:#800;font-weight:700}.preview-wrap[data-v-dedf8343] .hljs-regexp,.preview-wrap[data-v-dedf8343] .hljs-symbol,.preview-wrap[data-v-dedf8343] .hljs-variable,.preview-wrap[data-v-dedf8343] .hljs-template-variable,.preview-wrap[data-v-dedf8343] .hljs-link,.preview-wrap[data-v-dedf8343] .hljs-selector-attr,.preview-wrap[data-v-dedf8343] .hljs-selector-pseudo{color:#bc6060}.preview-wrap[data-v-dedf8343] .hljs-literal{color:#78a960}.preview-wrap[data-v-dedf8343] .hljs-built_in,.preview-wrap[data-v-dedf8343] .hljs-bullet,.preview-wrap[data-v-dedf8343] .hljs-code,.preview-wrap[data-v-dedf8343] .hljs-addition{color:#397300}.preview-wrap[data-v-dedf8343] .hljs-meta{color:#1f7199}.preview-wrap[data-v-dedf8343] .hljs-meta-string{color:#4d99bf}.preview-wrap[data-v-dedf8343] .hljs-emphasis{font-style:italic}.preview-wrap[data-v-dedf8343] .hljs-strong{font-weight:700}[data-v-dedf8343] .cm-completionLabel{display:none}[data-v-dedf8343] .yona-mention-option{align-items:center;gap:4px;display:inline-flex}[data-v-dedf8343] .yona-mention-option img{vertical-align:middle;border-radius:3px}[data-v-dedf8343] .yona-mention-option small{opacity:.65;margin-left:4px}[data-v-dedf8343] .yona-mention-option strong{color:var(--yona-md-accent-color,#51aacc);font-weight:700}"]], ["__scopeId", "data-v-dedf8343"]])), ax = 0, ox = class extends ix {
	#e = null;
	#t = (e) => {
		e.target !== this.#e && this.#n();
	};
	connectedCallback() {
		if (super.connectedCallback(), !this.#e) {
			let e = this.getAttribute("name") ?? "", t = this.getAttribute("editor-mode") ?? "", n = document.createElement("textarea");
			n.name = e, n.id = `editor-${e || "field"}-${++ax}`, n.setAttribute("data-editor-mode", t), n.setAttribute("markdown", "true"), n.style.display = "none", n.value = this.getValue(), this.appendChild(n), this.#e = n;
		}
		this.addEventListener("input", this.#t);
	}
	disconnectedCallback() {
		this.removeEventListener("input", this.#t), this.#e &&= (this.removeChild(this.#e), null), super.disconnectedCallback();
	}
	#n() {
		if (!this.#e) return;
		let e = this.getValue();
		this.#e.value = e, this.#e.dispatchEvent(new Event("input", { bubbles: !0 })), this.#e.dispatchEvent(new KeyboardEvent("keyup", { bubbles: !0 }));
	}
	get value() {
		return this.getValue();
	}
	set value(e) {
		this.setValue(e);
	}
};
customElements.define("yona-markdown-editor-vue", ox);
//#endregion
