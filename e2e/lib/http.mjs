// 서버를 HTTP로 부르는 작은 클라이언트이다: 쿠키 보관, 웹 로그인, 기기 인증으로 토큰 받기, 토큰으로 REST 부르기.
import { ADMIN } from './server.mjs';

export class Session {
  constructor(base) { this.base = base; this.cookies = new Map(); this.token = null; }

  #cookieHeader() { return [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '); }

  async #fetch(path, { method = 'GET', headers = {}, body, redirect = 'manual' } = {}) {
    const h = { ...headers };
    if (this.cookies.size) h.cookie = this.#cookieHeader();
    if (this.token && !h.Authorization) h.Authorization = `token ${this.token}`;
    const res = await fetch(this.base + path, { method, headers: h, body, redirect });
    for (const c of res.headers.getSetCookie?.() ?? []) { const [kv] = c.split(';'); const i = kv.indexOf('='); this.cookies.set(kv.slice(0, i), kv.slice(i + 1)); }
    return res;
  }

  /** 웹 로그인(세션 쿠키). */
  async login(loginId, password = ADMIN.password) {
    const res = await this.#fetch('/users/login', { method: 'POST', body: new URLSearchParams({ loginIdOrEmail: loginId, password }) });
    const location = res.headers.get('location') || '';
    if (location.includes('loginform')) throw new Error(`로그인 실패: ${loginId}`);
    return this;
  }

  /** 웹 가입. 이미 있으면 조용히 넘어간다. */
  async signup(loginId, name, email, password = ADMIN.password) {
    await this.#fetch('/users/signup', { method: 'POST', body: new URLSearchParams({ loginId, name, email, password }) });
  }

  /** 기기 인증(RFC 8628)으로 개인용 토큰을 받는다. 웹 세션이 로그인되어 있어야 한다. */
  async deviceToken(scope = 'repo read:org write:org admin:org read:repo_hook write:repo_hook admin:repo_hook', clientName = 'yona-e2e') {
    const issued = await (await fetch(this.base + '/-_-api/v1/oauth/device/code', { method: 'POST', body: new URLSearchParams({ client_name: clientName, scope }) })).json();
    const ok = await this.#fetch('/users/device/approve', { method: 'POST', body: new URLSearchParams({ user_code: issued.user_code }) });
    if (ok.status >= 400) throw new Error('기기 승인 실패: ' + ok.status);
    for (let i = 0; i < 20; i++) {
      const r = await fetch(this.base + '/-_-api/v1/oauth/token', { method: 'POST', body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:device_code', device_code: issued.device_code }) });
      const j = await r.json();
      if (j.access_token) { this.token = j.access_token; return j.access_token; }
      if (j.error === 'slow_down' || j.error === 'authorization_pending') { await new Promise((x) => setTimeout(x, 1200)); continue; }
      throw new Error('토큰 교환 실패: ' + JSON.stringify(j));
    }
    throw new Error('토큰 교환 시간 초과');
  }

  /** 토큰으로 REST(JSON)를 부른다. 실패하면 오류를 던진다(허용 상태는 `ok`). */
  async api(method, path, body, { ok = [200, 201, 204] } = {}) {
    const res = await this.#fetch('/-_-api/v1' + path, { method, headers: body === undefined ? {} : { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await res.text();
    if (!ok.includes(res.status)) throw new Error(`${method} ${path} -> ${res.status} ${text.slice(0, 300)}`);
    try { return text ? JSON.parse(text) : null; } catch { return text; }
  }

  /** 세션 쿠키를 Playwright 컨텍스트에 심을 형태로 돌려준다. */
  playwrightCookies() { return [...this.cookies].map(([name, value]) => ({ name, value, url: this.base })); }
}
