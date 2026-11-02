// X-16과 X-40 조합 확인: Yona가 OIDC 제공자이면서 사내 IdP(Keycloak)로 로그인하는 사이트일 때, 외부 앱(openid-client 라이브러리가 대신한다)의 로그인 요청이
// Yona 로그인 화면의 사내 IdP 단추를 거쳐 이어지고 ID 토큰을 받는지 확인한다. Keycloak 컨테이너(e2e/keycloak/realm-corp.json)를 먼저 띄운다.
import { join } from 'node:path';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
const client = await import(process.env.OPENID_CLIENT || '/tmp/claude-1000/-home-jiho-yona-convert-yona/572b44fd-d452-40b9-ac89-25d34813d6ac/scratchpad/mcpclient/node_modules/openid-client/build/index.js');
const KC = 'http://localhost:28180'; const REALM = 'corp'; const CALLBACK = 'http://localhost:53114/callback';
const state = join(ROOT, '.state', 'ssoprovider'); rmSync(state, { recursive: true, force: true });
const KEY = join(ROOT, '..', 'src', 'test', 'resources', 'oauth2', 'oidc-test-key.pem');
const B = await startServer({ port: 19411, dataDir: state, extraArgs: ['--yona.oauth2.oidc.signing-key-file=' + KEY] });
let status = 'ok'; const fail = (m) => { console.log('FAIL', m); status = 'fail'; }; const ok = (m) => console.log('ok  ', m);
const browser = await chromium.launch(); const mk = async () => (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage();
const shot = async (page, name) => { await page.waitForTimeout(400); await page.screenshot({ path: join(ROOT, 'shots', `sso-provider-${name}.png`), fullPage: true }); };
try {
  const admin = await mk();
  await admin.goto(B.base + '/users/loginform'); await admin.fill('input[name=loginIdOrEmail]', 'admin'); await admin.fill('input[name=password]', ADMIN.password); await admin.click('.login-form-wrap button[type=submit]'); await admin.waitForLoadState('networkidle');
  // 외부 앱 등록(OAuth 앱)
  await admin.goto(B.base + '/user/editform/oauth-apps'); await admin.click('#oauth-app-new'); await admin.waitForSelector('#oauth-app-form');
  await admin.fill('#oauth-name', 'Woodpecker(예시)'); await admin.fill('#oauth-home', 'https://ci.example'); await admin.fill('#oauth-redirects', CALLBACK);
  await admin.check('#oauth-scope-openid'); await admin.check('#oauth-scope-profile'); await admin.check('#oauth-scope-email');
  await admin.click('#oauth-app-form button[type=submit]'); await admin.waitForSelector('#edit-client-id');
  const clientId = (await admin.textContent('#edit-client-id')).trim();
  // 사내 IdP(OIDC) 설정
  await admin.goto(B.base + '/sites/sso?tab=oidc'); await admin.check('#oidc-enabled'); await admin.fill('#oidc-display', '사내 계정'); await admin.fill('#oidc-issuer', `${KC}/realms/${REALM}`);
  await admin.fill('#oidc-client-id', 'yona-oidc'); await admin.fill('#oidc-client-secret', 'oidc-secret-1234'); await admin.click('#sso-oidc-form button[type=submit]'); await admin.waitForLoadState('networkidle');
  // KC의 yona-oidc 클라이언트 리디렉션 주소는 localhost:19410/*이므로 이번 포트(19411)를 추가한다
  const tokenRes = await fetch(`${KC}/realms/master/protocol/openid-connect/token`, { method: 'POST', body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli', username: 'admin', password: 'admin' }) });
  const kcToken = (await tokenRes.json()).access_token; const kc = (path, init = {}) => fetch(`${KC}/admin/realms/${REALM}${path}`, { ...init, headers: { Authorization: `Bearer ${kcToken}`, ...(init.headers || {}) } });
  const list = await (await kc('/clients?clientId=yona-oidc')).json(); const rep = { ...list[0], redirectUris: ['http://localhost:19410/*', 'http://localhost:19411/*'] };
  const upd = await kc(`/clients/${list[0].id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(rep) }); if (upd.status !== 204) throw new Error('KC 클라이언트 수정 실패 ' + upd.status);

  // 외부 앱의 로그인 요청(로그인하지 않은 브라우저)
  const config = await client.discovery(new URL(B.base), clientId, undefined, client.None(), { execute: [client.allowInsecureRequests] });
  const verifier = client.randomPKCECodeVerifier(); const challenge = await client.calculatePKCECodeChallenge(verifier); const nonce = client.randomNonce(); const st = client.randomState();
  const url = client.buildAuthorizationUrl(config, { redirect_uri: CALLBACK, scope: 'openid profile email', code_challenge: challenge, code_challenge_method: 'S256', nonce, state: st });
  const user = await mk(); const callbacks = []; user.on('request', (r) => { if (r.url().startsWith(CALLBACK)) callbacks.push(r.url()); });
  await user.goto(url.href); await user.waitForURL(/\/users\/loginform/); await shot(user, '1-login-page');
  ok('로그인하지 않은 승인 요청은 Yona 로그인 화면으로 간다');
  await user.click('.page.full a.sso-login-btn'); await user.waitForURL(/localhost:28180/);
  await user.fill('#username', 'kim'); await user.fill('#password', 'kimpw1234'); await user.click('#kc-login');
  await user.waitForSelector('#consent-form', { timeout: 20000 }); await shot(user, '2-consent'); ok('사내 IdP 로그인 뒤 승인 화면으로 이어진다');
  await user.click('#consent-allow'); await user.waitForTimeout(1500);
  const cb = callbacks.at(-1); if (!cb) throw new Error('콜백이 없다');
  const tokens = await client.authorizationCodeGrant(config, new URL(cb), { pkceCodeVerifier: verifier, expectedNonce: nonce, expectedState: st }, undefined, { execute: [client.allowInsecureRequests] });
  const claims = tokens.claims(); console.log('id_token claims', JSON.stringify(claims));
  if (claims.email === 'kim@corp.example' && claims.sub) ok('외부 앱이 받은 ID 토큰의 사용자는 사내 IdP로 로그인한 사용자이다'); else fail('클레임이 다르다');
} catch (e) { fail(String(e?.stack || e)); } finally { await browser.close(); await B.stop(); }
console.log('RESULT', status);
